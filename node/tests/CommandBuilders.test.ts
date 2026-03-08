/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 *
 * Unit tests for command builder functions, type conversion utilities,
 * BitField classes, and error hierarchy.
 *
 * These tests verify argument serialization, validation logic, and edge cases
 * in Commands.ts, BaseClient.ts, and Errors.ts without requiring a running server.
 *
 * The native Rust addon is mocked so tests run without compiling native code.
 */

/* eslint-disable @typescript-eslint/no-explicit-any */

// Mock the native addon before any imports that depend on it
jest.mock("../build-ts/native", () => ({
    InitInternalLogger: jest.fn().mockReturnValue(0),
    log: jest.fn(),
    Level: { Error: 0, Warn: 1, Info: 2, Debug: 3, Trace: 4, Off: 5 },
    MAX_REQUEST_ARGS_LEN: 1024 * 1024,
    createLeakedStringVec: jest.fn().mockReturnValue([0, 0]),
    createLeakedString: jest.fn().mockReturnValue([0, 0]),
    createLeakedArray: jest.fn().mockReturnValue([0, 0]),
    createLeakedMap: jest.fn().mockReturnValue([0, 0]),
    createLeakedAttribute: jest.fn().mockReturnValue([0, 0]),
    createLeakedBigint: jest.fn().mockReturnValue([0, 0]),
    createLeakedDouble: jest.fn().mockReturnValue([0, 0]),
    valueFromSplitPointer: jest.fn().mockReturnValue(null),
    valueFromPointer: jest.fn().mockReturnValue(null),
    createOpenTelemetry: jest.fn(),
}));

import { describe, expect, it } from "@jest/globals";
import {
    BitFieldGet,
    BitFieldIncrBy,
    BitFieldOverflow,
    BitFieldSet,
    BitOffset,
    BitOffsetMultiplier,
    BitOverflowControl,
    ClosingError,
    ConfigurationError,
    ConnectionError,
    ConditionalChange,
    convertElementsAndScores,
    convertFieldsAndValuesToHashDataType,
    convertGlideRecord,
    convertGlideRecordToRecord,
    convertKeysAndEntries,
    convertRecordToGlideRecord,
    createGetEx,
    createHSetEx,
    createRestore,
    createSet,
    createZAdd,
    ExecAbortError,
    GlideRecord,
    isGlideRecord,
    parseInfoResponse,
    RequestError,
    SignedEncoding,
    TimeoutError,
    TIMEOUT_ERROR,
    TimeUnit,
    UnsignedEncoding,
    UpdateByScore,
    ValkeyError,
} from "../build-ts";

// ---------------------------------------------------------------------------
// parseInfoResponse
// ---------------------------------------------------------------------------
describe("parseInfoResponse", () => {
    it("parses standard key:value lines", () => {
        const input = "redis_version:7.0.0\r\nused_memory:1000\r\n";
        const result = parseInfoResponse(input);
        expect(result["redis_version"]).toBe("7.0.0");
        expect(result["used_memory"]).toBe("1000");
    });

    it("skips comment lines starting with #", () => {
        const input =
            "# Server\nredis_version:7.0.0\n# Clients\nconnected_clients:5\n";
        const result = parseInfoResponse(input);
        expect(result["redis_version"]).toBe("7.0.0");
        expect(result["connected_clients"]).toBe("5");
        expect(result["# Server"]).toBeUndefined();
        expect(result["# Clients"]).toBeUndefined();
    });

    it("handles empty string input", () => {
        const result = parseInfoResponse("");
        // Empty string splits into [""], an empty key with undefined value
        expect(Object.keys(result).length).toBeLessThanOrEqual(1);
    });

    it("handles values containing colons", () => {
        // INFO output can have values like "executable:/usr/bin/redis-server"
        // split(":") produces ["executable", "/usr/bin/redis-server"], only first 2 captured
        const input = "executable:/usr/bin/redis-server\n";
        const result = parseInfoResponse(input);
        expect(result["executable"]).toBeDefined();
    });

    it("handles lines with only whitespace", () => {
        const input = "key1:value1\n   \nkey2:value2\n";
        const result = parseInfoResponse(input);
        expect(result["key1"]).toBe("value1");
        expect(result["key2"]).toBe("value2");
    });

    it("handles lines without colons", () => {
        const input = "noColonLine\nkey:value\n";
        const result = parseInfoResponse(input);
        expect(result["key"]).toBe("value");
        // Line without colon: split returns single element, value is undefined
        expect(result["noColonLine"]).toBeUndefined();
    });

    it("handles carriage returns in values", () => {
        const input = "key1:value1\r\nkey2:value2\r\n";
        const result = parseInfoResponse(input);
        expect(result["key1"]).toBe("value1");
        expect(result["key2"]).toBe("value2");
    });

    it("parses multiline INFO with multiple sections", () => {
        const input = [
            "# Server",
            "redis_version:7.0.0",
            "redis_mode:standalone",
            "",
            "# Clients",
            "connected_clients:10",
            "blocked_clients:0",
            "",
        ].join("\r\n");
        const result = parseInfoResponse(input);
        expect(result["redis_version"]).toBe("7.0.0");
        expect(result["redis_mode"]).toBe("standalone");
        expect(result["connected_clients"]).toBe("10");
        expect(result["blocked_clients"]).toBe("0");
    });
});

// ---------------------------------------------------------------------------
// GlideRecord conversions
// ---------------------------------------------------------------------------
describe("GlideRecord conversions", () => {
    describe("convertGlideRecord", () => {
        it("passes through GlideRecord arrays unchanged (same reference)", () => {
            const input: GlideRecord<string> = [
                { key: "a", value: "1" },
                { key: "b", value: "2" },
            ];
            const result = convertGlideRecord(input);
            expect(result).toBe(input);
        });

        it("converts Record to GlideRecord", () => {
            const result = convertGlideRecord({ a: "1", b: "2" });
            expect(result).toEqual([
                { key: "a", value: "1" },
                { key: "b", value: "2" },
            ]);
        });

        it("handles empty Record", () => {
            expect(convertGlideRecord({})).toEqual([]);
        });

        it("handles empty GlideRecord array", () => {
            const input: GlideRecord<string> = [];
            const result = convertGlideRecord(input);
            expect(result).toEqual([]);
            expect(result).toBe(input);
        });

        it("preserves Buffer values in Record conversion", () => {
            const buf = Buffer.from("binary");
            const result = convertGlideRecord({ key1: buf });
            expect(result[0].value).toBe(buf);
        });
    });

    describe("convertGlideRecordToRecord", () => {
        it("converts flat GlideRecord to Record", () => {
            const input: GlideRecord<string> = [
                { key: "a", value: "1" },
                { key: "b", value: "2" },
            ];
            expect(convertGlideRecordToRecord(input)).toEqual({
                a: "1",
                b: "2",
            });
        });

        it("recursively converts nested GlideRecords", () => {
            const input: GlideRecord<unknown> = [
                {
                    key: "outer",
                    value: [
                        { key: "inner1", value: "val1" },
                        { key: "inner2", value: "val2" },
                    ],
                },
            ];
            expect(convertGlideRecordToRecord(input)).toEqual({
                outer: { inner1: "val1", inner2: "val2" },
            });
        });

        it("handles empty GlideRecord", () => {
            expect(convertGlideRecordToRecord([])).toEqual({});
        });

        it("handles GlideRecord with numeric values", () => {
            const input: GlideRecord<number> = [
                { key: "count", value: 42 },
                { key: "zero", value: 0 },
                { key: "negative", value: -1 },
            ];
            expect(convertGlideRecordToRecord(input)).toEqual({
                count: 42,
                zero: 0,
                negative: -1,
            });
        });

        it("handles GlideRecord with null values", () => {
            expect(
                convertGlideRecordToRecord([{ key: "empty", value: null }]),
            ).toEqual({ empty: null });
        });

        it("last key wins on duplicate keys", () => {
            const input: GlideRecord<string> = [
                { key: "dup", value: "first" },
                { key: "dup", value: "second" },
            ];
            expect(convertGlideRecordToRecord(input)["dup"]).toBe("second");
        });

        it("handles deeply nested GlideRecord arrays (isGlideRecordArray)", () => {
            const innerRecords: GlideRecord<string>[] = [
                [{ key: "x", value: "1" }],
                [{ key: "y", value: "2" }],
            ];
            const input: GlideRecord<GlideRecord<string>[]> = [
                { key: "list", value: innerRecords },
            ];
            expect(convertGlideRecordToRecord(input)).toEqual({
                list: [{ x: "1" }, { y: "2" }],
            });
        });
    });

    describe("convertRecordToGlideRecord", () => {
        it("converts Record to GlideRecord", () => {
            expect(convertRecordToGlideRecord({ a: 1, b: 2 })).toEqual([
                { key: "a", value: 1 },
                { key: "b", value: 2 },
            ]);
        });

        it("handles empty Record", () => {
            expect(convertRecordToGlideRecord({})).toEqual([]);
        });

        it("round-trips with convertGlideRecordToRecord for flat data", () => {
            const original = { key1: "val1", key2: "val2" };
            const roundTripped = convertGlideRecordToRecord(
                convertRecordToGlideRecord(original),
            );
            expect(roundTripped).toEqual(original);
        });
    });

    describe("isGlideRecord", () => {
        it("returns true for valid GlideRecord", () => {
            expect(isGlideRecord([{ key: "a", value: "b" }])).toBe(true);
        });

        it("returns false for empty array", () => {
            expect(isGlideRecord([])).toBe(false);
        });

        it("returns false for undefined/null", () => {
            expect(isGlideRecord(undefined)).toBe(false);
            expect(isGlideRecord(null)).toBe(false);
        });

        it("returns false for non-array types", () => {
            expect(isGlideRecord("string")).toBe(false);
            expect(isGlideRecord(42)).toBe(false);
            expect(isGlideRecord({ key: "a", value: "b" })).toBe(false);
        });

        it("returns false for array of primitives", () => {
            expect(isGlideRecord([1, 2, 3])).toBe(false);
            expect(isGlideRecord(["a", "b"])).toBe(false);
        });

        it("returns false for objects missing key or value", () => {
            expect(isGlideRecord([{ name: "a" }])).toBe(false);
            expect(isGlideRecord([{ key: "a" }])).toBe(false);
            expect(isGlideRecord([{ value: "b" }])).toBe(false);
        });

        it("only checks the first element", () => {
            // isGlideRecord only inspects obj[0], so mixed arrays still pass
            const mixed = [{ key: "a", value: "b" }, "not an object"];
            expect(isGlideRecord(mixed)).toBe(true);
        });
    });
});

// ---------------------------------------------------------------------------
// convertFieldsAndValuesToHashDataType
// ---------------------------------------------------------------------------
describe("convertFieldsAndValuesToHashDataType", () => {
    it("passes through HashDataType arrays unchanged", () => {
        const input = [
            { field: "f1", value: "v1" },
            { field: "f2", value: "v2" },
        ];
        expect(convertFieldsAndValuesToHashDataType(input)).toBe(input);
    });

    it("converts Record to HashDataType", () => {
        expect(
            convertFieldsAndValuesToHashDataType({ name: "Alice", age: "30" }),
        ).toEqual([
            { field: "name", value: "Alice" },
            { field: "age", value: "30" },
        ]);
    });

    it("handles empty Record", () => {
        expect(convertFieldsAndValuesToHashDataType({})).toEqual([]);
    });

    it("handles empty HashDataType array", () => {
        expect(convertFieldsAndValuesToHashDataType([])).toEqual([]);
    });

    it("preserves Buffer values", () => {
        const buf = Buffer.from("binary-value");
        const result = convertFieldsAndValuesToHashDataType({ key: buf });
        expect(result[0].value).toBe(buf);
    });
});

// ---------------------------------------------------------------------------
// convertKeysAndEntries
// ---------------------------------------------------------------------------
describe("convertKeysAndEntries", () => {
    it("passes through GlideRecord arrays unchanged", () => {
        const input: GlideRecord<string> = [{ key: "k1", value: "v1" }];
        expect(convertKeysAndEntries(input)).toBe(input);
    });

    it("converts Record to GlideRecord", () => {
        expect(convertKeysAndEntries({ a: "1", b: "2" })).toEqual([
            { key: "a", value: "1" },
            { key: "b", value: "2" },
        ]);
    });

    it("handles empty Record", () => {
        expect(convertKeysAndEntries({})).toEqual([]);
    });

    it("handles empty GlideRecord array", () => {
        const input: GlideRecord<string> = [];
        expect(convertKeysAndEntries(input)).toBe(input);
    });
});

// ---------------------------------------------------------------------------
// convertElementsAndScores
// ---------------------------------------------------------------------------
describe("convertElementsAndScores", () => {
    it("passes through SortedSetDataType arrays (same reference)", () => {
        const input = [
            { element: "a", score: 1.0 },
            { element: "b", score: 2.5 },
        ];
        expect(convertElementsAndScores(input)).toBe(input);
    });

    it("converts Record to SortedSetDataType", () => {
        expect(
            convertElementsAndScores({ member1: 1.0, member2: 2.5 }),
        ).toEqual([
            { element: "member1", score: 1.0 },
            { element: "member2", score: 2.5 },
        ]);
    });

    it("handles empty Record", () => {
        expect(convertElementsAndScores({})).toEqual([]);
    });

    it("handles negative, zero, and Infinity scores", () => {
        const result = convertElementsAndScores({
            neg: -1.5,
            zero: 0,
            inf: Infinity,
        });
        expect(result).toHaveLength(3);
        expect(result.find((e) => e.element === "neg")?.score).toBe(-1.5);
        expect(result.find((e) => e.element === "zero")?.score).toBe(0);
        expect(result.find((e) => e.element === "inf")?.score).toBe(Infinity);
    });
});

// ---------------------------------------------------------------------------
// BitField classes
// ---------------------------------------------------------------------------
describe("BitField classes", () => {
    describe("SignedEncoding", () => {
        it("produces correct encoding strings", () => {
            expect(new SignedEncoding(8).toArg()).toBe("i8");
            expect(new SignedEncoding(16).toArg()).toBe("i16");
            expect(new SignedEncoding(64).toArg()).toBe("i64");
        });

        it("handles boundary values", () => {
            expect(new SignedEncoding(1).toArg()).toBe("i1");
            expect(new SignedEncoding(0).toArg()).toBe("i0");
        });
    });

    describe("UnsignedEncoding", () => {
        it("produces correct encoding strings", () => {
            expect(new UnsignedEncoding(8).toArg()).toBe("u8");
            expect(new UnsignedEncoding(32).toArg()).toBe("u32");
            expect(new UnsignedEncoding(63).toArg()).toBe("u63");
        });
    });

    describe("BitOffset", () => {
        it("produces correct offset strings", () => {
            expect(new BitOffset(0).toArg()).toBe("0");
            expect(new BitOffset(7).toArg()).toBe("7");
            expect(new BitOffset(100).toArg()).toBe("100");
        });
    });

    describe("BitOffsetMultiplier", () => {
        it("produces correct # prefixed offset strings", () => {
            expect(new BitOffsetMultiplier(0).toArg()).toBe("#0");
            expect(new BitOffsetMultiplier(1).toArg()).toBe("#1");
            expect(new BitOffsetMultiplier(10).toArg()).toBe("#10");
        });
    });

    describe("BitFieldGet", () => {
        it("produces correct GET subcommand args", () => {
            const cmd = new BitFieldGet(
                new UnsignedEncoding(8),
                new BitOffset(0),
            );
            expect(cmd.toArgs()).toEqual(["GET", "u8", "0"]);
        });

        it("works with multiplier offset and signed encoding", () => {
            const cmd = new BitFieldGet(
                new SignedEncoding(16),
                new BitOffsetMultiplier(2),
            );
            expect(cmd.toArgs()).toEqual(["GET", "i16", "#2"]);
        });
    });

    describe("BitFieldSet", () => {
        it("produces correct SET subcommand args", () => {
            const cmd = new BitFieldSet(
                new UnsignedEncoding(8),
                new BitOffset(0),
                255,
            );
            expect(cmd.toArgs()).toEqual(["SET", "u8", "0", "255"]);
        });

        it("handles negative values", () => {
            const cmd = new BitFieldSet(
                new SignedEncoding(8),
                new BitOffset(0),
                -128,
            );
            expect(cmd.toArgs()).toEqual(["SET", "i8", "0", "-128"]);
        });

        it("handles zero value", () => {
            const cmd = new BitFieldSet(
                new UnsignedEncoding(4),
                new BitOffset(0),
                0,
            );
            expect(cmd.toArgs()).toEqual(["SET", "u4", "0", "0"]);
        });
    });

    describe("BitFieldIncrBy", () => {
        it("produces correct INCRBY subcommand args", () => {
            const cmd = new BitFieldIncrBy(
                new SignedEncoding(8),
                new BitOffset(0),
                10,
            );
            expect(cmd.toArgs()).toEqual(["INCRBY", "i8", "0", "10"]);
        });

        it("handles negative increments", () => {
            const cmd = new BitFieldIncrBy(
                new UnsignedEncoding(8),
                new BitOffsetMultiplier(1),
                -5,
            );
            expect(cmd.toArgs()).toEqual(["INCRBY", "u8", "#1", "-5"]);
        });
    });

    describe("BitFieldOverflow", () => {
        it.each([
            [BitOverflowControl.WRAP, "WRAP"],
            [BitOverflowControl.SAT, "SAT"],
            [BitOverflowControl.FAIL, "FAIL"],
        ])("produces correct OVERFLOW %s args", (control, expected) => {
            expect(new BitFieldOverflow(control).toArgs()).toEqual([
                "OVERFLOW",
                expected,
            ]);
        });
    });
});

// ---------------------------------------------------------------------------
// Error class hierarchy
// ---------------------------------------------------------------------------
describe("Error class hierarchy", () => {
    it("all error types are instanceof Error and ValkeyError", () => {
        const errors = [
            new ClosingError("test"),
            new RequestError("test"),
            new TimeoutError("test"),
            new ExecAbortError("test"),
            new ConnectionError("test"),
            new ConfigurationError("test"),
        ];

        for (const err of errors) {
            expect(err).toBeInstanceOf(Error);
            expect(err).toBeInstanceOf(ValkeyError);
        }
    });

    it("RequestError subclasses are instanceof RequestError", () => {
        expect(new TimeoutError("t")).toBeInstanceOf(RequestError);
        expect(new ExecAbortError("t")).toBeInstanceOf(RequestError);
        expect(new ConnectionError("t")).toBeInstanceOf(RequestError);
        expect(new ConfigurationError("t")).toBeInstanceOf(RequestError);
    });

    it("ClosingError is NOT instanceof RequestError", () => {
        expect(new ClosingError("close")).not.toBeInstanceOf(RequestError);
    });

    it("provides default message when none given", () => {
        expect(new ClosingError().message).toBe("No error message provided");
    });

    it("preserves custom message", () => {
        expect(new RequestError("custom msg").message).toBe("custom msg");
    });

    it("error name matches class name", () => {
        const cases: [ValkeyError, string][] = [
            [new ClosingError("x"), "ClosingError"],
            [new RequestError("x"), "RequestError"],
            [new TimeoutError("x"), "TimeoutError"],
            [new ExecAbortError("x"), "ExecAbortError"],
            [new ConnectionError("x"), "ConnectionError"],
            [new ConfigurationError("x"), "ConfigurationError"],
        ];

        for (const [err, name] of cases) {
            expect(err.name).toBe(name);
        }
    });

    it("TIMEOUT_ERROR singleton is a TimeoutError", () => {
        expect(TIMEOUT_ERROR).toBeInstanceOf(TimeoutError);
        expect(TIMEOUT_ERROR.message).toBe("Operation timed out");
    });
});

// ---------------------------------------------------------------------------
// Command builder argument validation
// ---------------------------------------------------------------------------
describe("Command builder argument validation", () => {
    describe("createSet", () => {
        it("throws on non-integer expiry count", () => {
            expect(() =>
                createSet("key", "value", {
                    expiry: { type: TimeUnit.Seconds, count: 1.5 },
                }),
            ).toThrow("Count must be an integer");
        });

        it("throws on NaN expiry count", () => {
            expect(() =>
                createSet("key", "value", {
                    expiry: { type: TimeUnit.Seconds, count: NaN },
                }),
            ).toThrow("Count must be an integer");
        });

        it("throws on Infinity expiry count", () => {
            expect(() =>
                createSet("key", "value", {
                    expiry: { type: TimeUnit.Seconds, count: Infinity },
                }),
            ).toThrow("Count must be an integer");
        });

        it("does not throw on valid integer expiry", () => {
            expect(() =>
                createSet("key", "value", {
                    expiry: { type: TimeUnit.Seconds, count: 60 },
                }),
            ).not.toThrow();
        });

        it("does not throw on keepExisting expiry", () => {
            expect(() =>
                createSet("key", "value", { expiry: "keepExisting" }),
            ).not.toThrow();
        });

        it("does not throw with no options", () => {
            expect(() => createSet("key", "value")).not.toThrow();
        });

        it("does not throw with negative integer expiry (server validates)", () => {
            expect(() =>
                createSet("key", "value", {
                    expiry: { type: TimeUnit.Seconds, count: -1 },
                }),
            ).not.toThrow();
        });

        it("does not throw with zero expiry count", () => {
            expect(() =>
                createSet("key", "value", {
                    expiry: { type: TimeUnit.Seconds, count: 0 },
                }),
            ).not.toThrow();
        });

        it("accepts all TimeUnit variants", () => {
            for (const unit of [
                TimeUnit.Seconds,
                TimeUnit.Milliseconds,
                TimeUnit.UnixSeconds,
                TimeUnit.UnixMilliseconds,
            ]) {
                expect(() =>
                    createSet("key", "value", {
                        expiry: { type: unit, count: 100 },
                    }),
                ).not.toThrow();
            }
        });
    });

    describe("createGetEx", () => {
        it("throws on non-integer duration", () => {
            expect(() =>
                createGetEx("key", {
                    type: TimeUnit.Seconds,
                    duration: 1.5,
                }),
            ).toThrow("Count must be an integer");
        });

        it("does not throw on persist option", () => {
            expect(() => createGetEx("key", "persist")).not.toThrow();
        });

        it("does not throw with no options", () => {
            expect(() => createGetEx("key")).not.toThrow();
        });

        it("does not throw on valid integer duration", () => {
            expect(() =>
                createGetEx("key", {
                    type: TimeUnit.Milliseconds,
                    duration: 5000,
                }),
            ).not.toThrow();
        });
    });

    describe("createRestore", () => {
        it("throws when both IDLETIME and FREQ are set", () => {
            expect(() =>
                createRestore("key", 0, "serialized", {
                    idletime: 100,
                    frequency: 50,
                }),
            ).toThrow("both IDLETIME and FREQ cannot be set");
        });

        it("does not throw with only IDLETIME", () => {
            expect(() =>
                createRestore("key", 0, "serialized", { idletime: 100 }),
            ).not.toThrow();
        });

        it("does not throw with only FREQ", () => {
            expect(() =>
                createRestore("key", 0, "serialized", { frequency: 50 }),
            ).not.toThrow();
        });

        it("does not throw with no options", () => {
            expect(() => createRestore("key", 0, "serialized")).not.toThrow();
        });

        it("does not throw with replace + absttl options", () => {
            expect(() =>
                createRestore("key", 0, "serialized", {
                    replace: true,
                    absttl: true,
                }),
            ).not.toThrow();
        });
    });

    describe("createZAdd", () => {
        it("throws when NX is combined with GT updateOptions", () => {
            expect(() =>
                createZAdd(
                    "key",
                    [{ element: "a", score: 1 }],
                    {
                        conditionalChange:
                            ConditionalChange.ONLY_IF_DOES_NOT_EXIST,
                        updateOptions: UpdateByScore.GREATER_THAN,
                    },
                ),
            ).toThrow("GT, LT, and NX options are mutually exclusive");
        });

        it("throws when NX is combined with LT updateOptions", () => {
            expect(() =>
                createZAdd(
                    "key",
                    [{ element: "a", score: 1 }],
                    {
                        conditionalChange:
                            ConditionalChange.ONLY_IF_DOES_NOT_EXIST,
                        updateOptions: UpdateByScore.LESS_THAN,
                    },
                ),
            ).toThrow("GT, LT, and NX options are mutually exclusive");
        });

        it("does not throw with NX alone", () => {
            expect(() =>
                createZAdd("key", [{ element: "a", score: 1 }], {
                    conditionalChange:
                        ConditionalChange.ONLY_IF_DOES_NOT_EXIST,
                }),
            ).not.toThrow();
        });

        it("does not throw with XX + GT", () => {
            expect(() =>
                createZAdd("key", [{ element: "a", score: 1 }], {
                    conditionalChange: ConditionalChange.ONLY_IF_EXISTS,
                    updateOptions: UpdateByScore.GREATER_THAN,
                }),
            ).not.toThrow();
        });

        it("does not throw with Record input", () => {
            expect(() =>
                createZAdd("key", { member1: 1.0, member2: 2.5 }),
            ).not.toThrow();
        });

        it("does not throw with empty members array", () => {
            expect(() => createZAdd("key", [])).not.toThrow();
        });
    });

    describe("createHSetEx", () => {
        it("throws on non-integer expiry count", () => {
            expect(() =>
                createHSetEx("key", [{ field: "f1", value: "v1" }], {
                    expiry: { type: TimeUnit.Seconds, count: 2.5 },
                }),
            ).toThrow("Count must be an integer");
        });

        it("does not throw with KEEPTTL", () => {
            expect(() =>
                createHSetEx("key", [{ field: "f1", value: "v1" }], {
                    expiry: "KEEPTTL",
                }),
            ).not.toThrow();
        });

        it("does not throw with valid integer expiry", () => {
            expect(() =>
                createHSetEx("key", [{ field: "f1", value: "v1" }], {
                    expiry: { type: TimeUnit.Seconds, count: 60 },
                }),
            ).not.toThrow();
        });

        it("does not throw with empty field map", () => {
            expect(() => createHSetEx("key", [])).not.toThrow();
        });
    });
});
