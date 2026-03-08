/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 *
 * Unit tests for pure functions and command builders that don't require
 * a running Valkey server. Covers conversion utilities, parsing logic,
 * validation error paths, and error class hierarchy.
 */

import { describe, expect, it } from "@jest/globals";
import {
    ClosingError,
    ConfigurationError,
    ConnectionError,
    convertFieldsAndValuesToHashDataType,
    convertGlideRecord,
    convertGlideRecordToRecord,
    convertKeysAndEntries,
    convertRecordToGlideRecord,
    ExecAbortError,
    isGlideRecord,
    parseInfoResponse,
    RequestError,
    TimeoutError,
    TIMEOUT_ERROR,
    ValkeyError,
} from "../build-ts";
import type {
    GlideRecord,
    GlideString,
    HashDataType,
} from "../build-ts";

// ─────────────────────────────────────────────────────────────────────────────
// parseInfoResponse
// ─────────────────────────────────────────────────────────────────────────────
describe("parseInfoResponse", () => {
    it("should parse standard key:value lines", () => {
        const input = "redis_version:7.2.0\r\nused_memory:1024\r\n";
        const result = parseInfoResponse(input);
        expect(result["redis_version"]).toBe("7.2.0");
        expect(result["used_memory"]).toBe("1024");
    });

    it("should skip comment lines starting with #", () => {
        const input = "# Server\nredis_version:7.2.0\n# Clients\nconnected_clients:5\n";
        const result = parseInfoResponse(input);
        expect(result["redis_version"]).toBe("7.2.0");
        expect(result["connected_clients"]).toBe("5");
        expect(result["# Server"]).toBeUndefined();
        expect(result["# Clients"]).toBeUndefined();
    });

    it("should handle empty input", () => {
        const result = parseInfoResponse("");
        // Empty string produces one empty-string key with undefined value
        expect(Object.keys(result).length).toBeLessThanOrEqual(1);
    });

    it("should handle values containing colons", () => {
        // INFO response values like config_file can contain colons in paths
        const input = "config_file:/etc/valkey/valkey.conf\n";
        const result = parseInfoResponse(input);
        // split(":", 2) only splits on first colon - but the implementation uses
        // destructuring which takes only the first two parts
        expect(result["config_file"]).toBe("/etc/valkey/valkey.conf");
    });

    it("should handle lines with only whitespace after trimming", () => {
        const input = "redis_version:7.2.0\n   \nused_memory:1024\n";
        const result = parseInfoResponse(input);
        expect(result["redis_version"]).toBe("7.2.0");
        expect(result["used_memory"]).toBe("1024");
    });

    it("should handle Windows-style line endings (CRLF)", () => {
        const input = "redis_version:7.2.0\r\nused_memory:1024\r\n";
        const result = parseInfoResponse(input);
        expect(result["redis_version"]).toBe("7.2.0");
        expect(result["used_memory"]).toBe("1024");
    });

    it("should handle a single line without trailing newline", () => {
        const result = parseInfoResponse("redis_version:7.2.0");
        expect(result["redis_version"]).toBe("7.2.0");
    });

    it("should handle lines with no value after colon", () => {
        const result = parseInfoResponse("empty_key:\n");
        expect(result["empty_key"]).toBe("");
    });
});

// ─────────────────────────────────────────────────────────────────────────────
// isGlideRecord
// ─────────────────────────────────────────────────────────────────────────────
describe("isGlideRecord", () => {
    it("should return true for a valid GlideRecord", () => {
        const record: GlideRecord<string> = [
            { key: "a", value: "1" },
            { key: "b", value: "2" },
        ];
        expect(isGlideRecord(record)).toBe(true);
    });

    it("should return false for an empty array", () => {
        expect(isGlideRecord([])).toBe(false);
    });

    it("should return false for null", () => {
        expect(isGlideRecord(null)).toBe(false);
    });

    it("should return false for undefined", () => {
        expect(isGlideRecord(undefined)).toBe(false);
    });

    it("should return false for a plain object", () => {
        expect(isGlideRecord({ key: "a", value: "1" })).toBe(false);
    });

    it("should return false for an array of strings", () => {
        expect(isGlideRecord(["a", "b", "c"])).toBe(false);
    });

    it("should return false for an array of objects without key/value", () => {
        expect(isGlideRecord([{ name: "a" }])).toBe(false);
    });

    it("should return false for an array with only 'key' but no 'value'", () => {
        expect(isGlideRecord([{ key: "a" }])).toBe(false);
    });

    it("should return false for an array with only 'value' but no 'key'", () => {
        expect(isGlideRecord([{ value: "a" }])).toBe(false);
    });

    it("should return true for single-element GlideRecord", () => {
        expect(isGlideRecord([{ key: "a", value: "1" }])).toBe(true);
    });

    it("should return true even with extra properties on elements", () => {
        expect(isGlideRecord([{ key: "a", value: "1", extra: true }])).toBe(true);
    });

    it("should return false for a number", () => {
        expect(isGlideRecord(42)).toBe(false);
    });

    it("should return false for a boolean", () => {
        expect(isGlideRecord(true)).toBe(false);
    });
});

// ─────────────────────────────────────────────────────────────────────────────
// convertGlideRecord
// ─────────────────────────────────────────────────────────────────────────────
describe("convertGlideRecord", () => {
    it("should pass through a GlideRecord array unchanged", () => {
        const input: GlideRecord<GlideString> = [
            { key: "a", value: "1" },
            { key: "b", value: "2" },
        ];
        const result = convertGlideRecord(input);
        expect(result).toBe(input); // same reference
    });

    it("should convert a plain Record to GlideRecord", () => {
        const input = { foo: "bar", baz: "qux" };
        const result = convertGlideRecord(input);
        expect(result).toEqual([
            { key: "foo", value: "bar" },
            { key: "baz", value: "qux" },
        ]);
    });

    it("should handle an empty Record", () => {
        const result = convertGlideRecord({});
        expect(result).toEqual([]);
    });

    it("should handle an empty GlideRecord array", () => {
        const input: GlideRecord<GlideString> = [];
        const result = convertGlideRecord(input);
        expect(result).toBe(input);
        expect(result).toEqual([]);
    });

    it("should handle Buffer values in Record", () => {
        const buf = Buffer.from("hello");
        const input = { key1: buf };
        const result = convertGlideRecord(input);
        expect(result).toEqual([{ key: "key1", value: buf }]);
    });
});

// ─────────────────────────────────────────────────────────────────────────────
// convertRecordToGlideRecord
// ─────────────────────────────────────────────────────────────────────────────
describe("convertRecordToGlideRecord", () => {
    it("should convert a Record to GlideRecord", () => {
        const input = { a: 1, b: 2 };
        const result = convertRecordToGlideRecord(input);
        expect(result).toEqual([
            { key: "a", value: 1 },
            { key: "b", value: 2 },
        ]);
    });

    it("should handle an empty Record", () => {
        const result = convertRecordToGlideRecord({});
        expect(result).toEqual([]);
    });

    it("should handle nested objects as values", () => {
        const input = { a: { nested: true }, b: [1, 2, 3] };
        const result = convertRecordToGlideRecord(input);
        expect(result).toEqual([
            { key: "a", value: { nested: true } },
            { key: "b", value: [1, 2, 3] },
        ]);
    });

    it("should handle null/undefined values", () => {
        const input: Record<string, string | null> = { a: null, b: "ok" };
        const result = convertRecordToGlideRecord(input);
        expect(result).toEqual([
            { key: "a", value: null },
            { key: "b", value: "ok" },
        ]);
    });
});

// ─────────────────────────────────────────────────────────────────────────────
// convertGlideRecordToRecord
// ─────────────────────────────────────────────────────────────────────────────
describe("convertGlideRecordToRecord", () => {
    it("should convert a flat GlideRecord to Record", () => {
        const input: GlideRecord<string> = [
            { key: "a", value: "1" },
            { key: "b", value: "2" },
        ];
        const result = convertGlideRecordToRecord(input);
        expect(result).toEqual({ a: "1", b: "2" });
    });

    it("should handle an empty GlideRecord", () => {
        const result = convertGlideRecordToRecord([]);
        expect(result).toEqual({});
    });

    it("should recursively convert nested GlideRecords", () => {
        const input: GlideRecord<unknown> = [
            {
                key: "outer",
                value: [
                    { key: "inner1", value: "val1" },
                    { key: "inner2", value: "val2" },
                ],
            },
        ];
        const result = convertGlideRecordToRecord(input);
        expect(result).toEqual({
            outer: { inner1: "val1", inner2: "val2" },
        });
    });

    it("should handle arrays of GlideRecords", () => {
        const inner1: GlideRecord<string> = [{ key: "k1", value: "v1" }];
        const inner2: GlideRecord<string> = [{ key: "k2", value: "v2" }];
        const input: GlideRecord<GlideRecord<string>[]> = [
            { key: "list", value: [inner1, inner2] },
        ];
        const result = convertGlideRecordToRecord(input);
        expect(result).toEqual({
            list: [{ k1: "v1" }, { k2: "v2" }],
        });
    });

    it("should handle non-GlideRecord array values without conversion", () => {
        const input: GlideRecord<number[]> = [
            { key: "nums", value: [1, 2, 3] },
        ];
        const result = convertGlideRecordToRecord(input);
        expect(result).toEqual({ nums: [1, 2, 3] });
    });

    it("should handle null values", () => {
        const input: GlideRecord<null> = [{ key: "nilkey", value: null }];
        const result = convertGlideRecordToRecord(input);
        expect(result).toEqual({ nilkey: null });
    });

    it("should handle duplicate keys by using last occurrence", () => {
        const input: GlideRecord<string> = [
            { key: "dup", value: "first" },
            { key: "dup", value: "second" },
        ];
        const result = convertGlideRecordToRecord(input);
        expect(result["dup"]).toBe("second");
    });
});

// ─────────────────────────────────────────────────────────────────────────────
// Round-trip: Record -> GlideRecord -> Record
// ─────────────────────────────────────────────────────────────────────────────
describe("Record <-> GlideRecord round-trip", () => {
    it("should preserve data through Record -> GlideRecord -> Record conversion", () => {
        const original = { x: "1", y: "2", z: "3" };
        const glideRecord = convertRecordToGlideRecord(original);
        const roundTripped = convertGlideRecordToRecord(glideRecord);
        expect(roundTripped).toEqual(original);
    });

    it("should handle convertGlideRecord from Record then back via convertGlideRecordToRecord", () => {
        const original: Record<string, string> = { alpha: "a", beta: "b" };
        const glideRecord = convertGlideRecord(original);
        const back = convertGlideRecordToRecord(glideRecord);
        expect(back).toEqual(original);
    });
});

// ─────────────────────────────────────────────────────────────────────────────
// convertFieldsAndValuesToHashDataType
// ─────────────────────────────────────────────────────────────────────────────
describe("convertFieldsAndValuesToHashDataType", () => {
    it("should pass through HashDataType array unchanged", () => {
        const input: HashDataType = [
            { field: "f1", value: "v1" },
            { field: "f2", value: "v2" },
        ];
        const result = convertFieldsAndValuesToHashDataType(input);
        expect(result).toBe(input); // same reference
    });

    it("should convert a plain Record to HashDataType", () => {
        const input = { name: "Alice", age: "30" };
        const result = convertFieldsAndValuesToHashDataType(input);
        expect(result).toEqual([
            { field: "name", value: "Alice" },
            { field: "age", value: "30" },
        ]);
    });

    it("should handle an empty Record", () => {
        const result = convertFieldsAndValuesToHashDataType({});
        expect(result).toEqual([]);
    });

    it("should handle an empty HashDataType array", () => {
        const input: HashDataType = [];
        const result = convertFieldsAndValuesToHashDataType(input);
        expect(result).toBe(input);
        expect(result).toEqual([]);
    });

    it("should handle Buffer values in Record", () => {
        const buf = Buffer.from("binary");
        const input: Record<string, GlideString> = { binfield: buf };
        const result = convertFieldsAndValuesToHashDataType(input);
        expect(result).toEqual([{ field: "binfield", value: buf }]);
    });
});

// ─────────────────────────────────────────────────────────────────────────────
// convertKeysAndEntries
// ─────────────────────────────────────────────────────────────────────────────
describe("convertKeysAndEntries", () => {
    it("should pass through a GlideRecord array unchanged", () => {
        const input: GlideRecord<string> = [
            { key: "k1", value: "v1" },
        ];
        const result = convertKeysAndEntries(input);
        expect(result).toBe(input);
    });

    it("should convert a plain Record to GlideRecord", () => {
        const input = { stream1: "0-0", stream2: "0-1" };
        const result = convertKeysAndEntries(input);
        expect(result).toEqual([
            { key: "stream1", value: "0-0" },
            { key: "stream2", value: "0-1" },
        ]);
    });

    it("should handle an empty Record", () => {
        const result = convertKeysAndEntries({});
        expect(result).toEqual([]);
    });
});

// ─────────────────────────────────────────────────────────────────────────────
// convertElementsAndScores
// ─────────────────────────────────────────────────────────────────────────────
describe("convertElementsAndScores", () => {
    it("should pass through SortedSetDataType array unchanged", () => {
        const input: SortedSetDataType = [
            { element: "a", score: 1.0 },
            { element: "b", score: 2.5 },
        ];
        const result = convertElementsAndScores(input);
        expect(result).toBe(input); // same reference
    });

    it("should convert a Record<string, number> to SortedSetDataType", () => {
        const input = { alice: 100, bob: 200 };
        const result = convertElementsAndScores(input);
        expect(result).toEqual([
            { element: "alice", score: 100 },
            { element: "bob", score: 200 },
        ]);
    });

    it("should handle an empty Record", () => {
        const result = convertElementsAndScores({});
        expect(result).toEqual([]);
    });

    it("should handle an empty SortedSetDataType array", () => {
        const input: SortedSetDataType = [];
        const result = convertElementsAndScores(input);
        expect(result).toBe(input);
        expect(result).toEqual([]);
    });

    it("should handle fractional scores", () => {
        const input = { member: 3.14159 };
        const result = convertElementsAndScores(input);
        expect(result[0].score).toBeCloseTo(3.14159);
    });

    it("should handle negative scores", () => {
        const input = { negative: -42.5 };
        const result = convertElementsAndScores(input);
        expect(result[0].score).toBe(-42.5);
    });

    it("should handle zero score", () => {
        const input = { zero: 0 };
        const result = convertElementsAndScores(input);
        expect(result[0].score).toBe(0);
    });
});

// ─────────────────────────────────────────────────────────────────────────────
// Error class hierarchy
// ─────────────────────────────────────────────────────────────────────────────
describe("Error class hierarchy", () => {
    it("ValkeyError subclasses should be instanceof Error", () => {
        expect(new RequestError("test")).toBeInstanceOf(Error);
        expect(new ClosingError("test")).toBeInstanceOf(Error);
        expect(new TimeoutError("test")).toBeInstanceOf(Error);
        expect(new ExecAbortError("test")).toBeInstanceOf(Error);
        expect(new ConnectionError("test")).toBeInstanceOf(Error);
        expect(new ConfigurationError("test")).toBeInstanceOf(Error);
    });

    it("all error types should be instanceof ValkeyError", () => {
        expect(new RequestError("test")).toBeInstanceOf(ValkeyError);
        expect(new ClosingError("test")).toBeInstanceOf(ValkeyError);
        expect(new TimeoutError("test")).toBeInstanceOf(ValkeyError);
        expect(new ExecAbortError("test")).toBeInstanceOf(ValkeyError);
        expect(new ConnectionError("test")).toBeInstanceOf(ValkeyError);
        expect(new ConfigurationError("test")).toBeInstanceOf(ValkeyError);
    });

    it("TimeoutError should be instanceof RequestError", () => {
        const err = new TimeoutError("timed out");
        expect(err).toBeInstanceOf(RequestError);
        expect(err).toBeInstanceOf(ValkeyError);
    });

    it("ExecAbortError should be instanceof RequestError", () => {
        const err = new ExecAbortError("aborted");
        expect(err).toBeInstanceOf(RequestError);
    });

    it("ConnectionError should be instanceof RequestError", () => {
        const err = new ConnectionError("disconnected");
        expect(err).toBeInstanceOf(RequestError);
    });

    it("ConfigurationError should be instanceof RequestError", () => {
        const err = new ConfigurationError("bad config");
        expect(err).toBeInstanceOf(RequestError);
    });

    it("ClosingError should NOT be instanceof RequestError", () => {
        const err = new ClosingError("closed");
        expect(err).not.toBeInstanceOf(RequestError);
    });

    it("error name should match class name", () => {
        expect(new RequestError("x").name).toBe("RequestError");
        expect(new ClosingError("x").name).toBe("ClosingError");
        expect(new TimeoutError("x").name).toBe("TimeoutError");
        expect(new ExecAbortError("x").name).toBe("ExecAbortError");
        expect(new ConnectionError("x").name).toBe("ConnectionError");
        expect(new ConfigurationError("x").name).toBe("ConfigurationError");
    });

    it("should use default message when none provided", () => {
        const err = new RequestError();
        expect(err.message).toBe("No error message provided");
    });

    it("should preserve the provided message", () => {
        const msg = "Something went wrong with the request";
        const err = new RequestError(msg);
        expect(err.message).toBe(msg);
    });

    it("TIMEOUT_ERROR singleton should be a TimeoutError", () => {
        expect(TIMEOUT_ERROR).toBeInstanceOf(TimeoutError);
        expect(TIMEOUT_ERROR).toBeInstanceOf(RequestError);
        expect(TIMEOUT_ERROR.message).toBe("Operation timed out");
    });

    it("errors should be catchable by parent type in try/catch", () => {
        const throwTimeout = () => {
            throw new TimeoutError("timeout");
        };

        try {
            throwTimeout();
        } catch (e) {
            expect(e).toBeInstanceOf(RequestError);
            expect(e).toBeInstanceOf(ValkeyError);
            expect(e).toBeInstanceOf(Error);
        }
    });

    it("RequestError equality based on message", () => {
        const err1 = new RequestError("same message");
        const err2 = new RequestError("same message");
        // Different instances
        expect(err1).not.toBe(err2);
        // But equal by Jest's toEqual (same message, same type)
        expect(err1).toEqual(err2);
    });
});

// ─────────────────────────────────────────────────────────────────────────────
// Command builder validation (error paths)
//
// These tests verify that command builders throw errors for invalid inputs.
// They import the create* functions and test the validation logic without
// needing a server connection. The functions produce protobuf Command objects,
// so we can inspect the args to verify correctness.
// ─────────────────────────────────────────────────────────────────────────────
import {
    convertElementsAndScores,
    createGetEx,
    createHGetEx,
    createHSetEx,
    createLCS,
    createRestore,
    createSet,
    createZAdd,
    TimeUnit,
    ConditionalChange,
    UpdateByScore,
} from "../build-ts";
import type { SortedSetDataType } from "../build-ts";

describe("createSet validation", () => {
    it("should throw on non-integer expiry count", () => {
        expect(() =>
            createSet("key", "val", {
                expiry: { type: TimeUnit.Seconds, count: 1.5 },
            }),
        ).toThrow("Count must be an integer");
    });

    it("should throw on NaN expiry count", () => {
        expect(() =>
            createSet("key", "val", {
                expiry: { type: TimeUnit.Seconds, count: NaN },
            }),
        ).toThrow("Count must be an integer");
    });

    it("should throw on Infinity expiry count", () => {
        expect(() =>
            createSet("key", "val", {
                expiry: { type: TimeUnit.Seconds, count: Infinity },
            }),
        ).toThrow("Count must be an integer");
    });

    it("should not throw on valid integer expiry", () => {
        expect(() =>
            createSet("key", "val", {
                expiry: { type: TimeUnit.Seconds, count: 60 },
            }),
        ).not.toThrow();
    });

    it("should not throw on keepExisting expiry", () => {
        expect(() =>
            createSet("key", "val", {
                expiry: "keepExisting",
            }),
        ).not.toThrow();
    });

    it("should not throw with no options", () => {
        expect(() => createSet("key", "val")).not.toThrow();
    });

    it("should produce correct args for onlyIfExists", () => {
        const cmd = createSet("k", "v", { conditionalSet: "onlyIfExists" });
        const args = cmd.argsArray?.args?.map((a) => a.toString()) ?? [];
        expect(args).toContain("XX");
    });

    it("should produce correct args for onlyIfDoesNotExist", () => {
        const cmd = createSet("k", "v", {
            conditionalSet: "onlyIfDoesNotExist",
        });
        const args = cmd.argsArray?.args?.map((a) => a.toString()) ?? [];
        expect(args).toContain("NX");
    });

    it("should produce correct args for returnOldValue", () => {
        const cmd = createSet("k", "v", { returnOldValue: true });
        const args = cmd.argsArray?.args?.map((a) => a.toString()) ?? [];
        expect(args).toContain("GET");
    });

    it("should produce correct args for keepExisting expiry", () => {
        const cmd = createSet("k", "v", { expiry: "keepExisting" });
        const args = cmd.argsArray?.args?.map((a) => a.toString()) ?? [];
        expect(args).toContain("KEEPTTL");
    });

    it("should produce correct args for EX expiry", () => {
        const cmd = createSet("k", "v", {
            expiry: { type: TimeUnit.Seconds, count: 10 },
        });
        const args = cmd.argsArray?.args?.map((a) => a.toString()) ?? [];
        expect(args).toContain("EX");
        expect(args).toContain("10");
    });

    it("should produce correct args for combined options", () => {
        const cmd = createSet("k", "v", {
            conditionalSet: "onlyIfExists",
            returnOldValue: true,
            expiry: { type: TimeUnit.Milliseconds, count: 5000 },
        });
        const args = cmd.argsArray?.args?.map((a) => a.toString()) ?? [];
        expect(args).toEqual(["k", "v", "XX", "GET", "PX", "5000"]);
    });
});

describe("createGetEx validation", () => {
    it("should throw on non-integer duration", () => {
        expect(() =>
            createGetEx("key", { type: TimeUnit.Seconds, duration: 1.5 }),
        ).toThrow("Count must be an integer");
    });

    it("should throw on NaN duration", () => {
        expect(() =>
            createGetEx("key", { type: TimeUnit.Seconds, duration: NaN }),
        ).toThrow("Count must be an integer");
    });

    it("should not throw on persist option", () => {
        expect(() => createGetEx("key", "persist")).not.toThrow();
    });

    it("should produce correct args for persist", () => {
        const cmd = createGetEx("key", "persist");
        const args = cmd.argsArray?.args?.map((a) => a.toString()) ?? [];
        expect(args).toEqual(["key", "PERSIST"]);
    });

    it("should produce correct args for EX duration", () => {
        const cmd = createGetEx("key", {
            type: TimeUnit.Seconds,
            duration: 100,
        });
        const args = cmd.argsArray?.args?.map((a) => a.toString()) ?? [];
        expect(args).toEqual(["key", "EX", "100"]);
    });

    it("should produce correct args with no options", () => {
        const cmd = createGetEx("key");
        const args = cmd.argsArray?.args?.map((a) => a.toString()) ?? [];
        expect(args).toEqual(["key"]);
    });
});

describe("createZAdd validation", () => {
    it("should throw when NX and GT are both specified", () => {
        expect(() =>
            createZAdd(
                "key",
                [{ element: "a", score: 1 }],
                {
                    conditionalChange: ConditionalChange.ONLY_IF_DOES_NOT_EXIST,
                    updateOptions: UpdateByScore.GREATER_THAN,
                },
            ),
        ).toThrow("GT, LT, and NX options are mutually exclusive");
    });

    it("should throw when NX and LT are both specified", () => {
        expect(() =>
            createZAdd(
                "key",
                [{ element: "a", score: 1 }],
                {
                    conditionalChange: ConditionalChange.ONLY_IF_DOES_NOT_EXIST,
                    updateOptions: UpdateByScore.LESS_THAN,
                },
            ),
        ).toThrow("GT, LT, and NX options are mutually exclusive");
    });

    it("should not throw for NX without updateOptions", () => {
        expect(() =>
            createZAdd("key", [{ element: "a", score: 1 }], {
                conditionalChange: ConditionalChange.ONLY_IF_DOES_NOT_EXIST,
            }),
        ).not.toThrow();
    });

    it("should not throw for XX with GT", () => {
        expect(() =>
            createZAdd("key", [{ element: "a", score: 1 }], {
                conditionalChange: ConditionalChange.ONLY_IF_EXISTS,
                updateOptions: UpdateByScore.GREATER_THAN,
            }),
        ).not.toThrow();
    });

    it("should handle array-style members", () => {
        const cmd = createZAdd("zset", [
            { element: "a", score: 1.5 },
            { element: "b", score: 2.5 },
        ]);
        const args = cmd.argsArray?.args?.map((a) => a.toString()) ?? [];
        // key, score1, element1, score2, element2
        expect(args).toEqual(["zset", "1.5", "a", "2.5", "b"]);
    });

    it("should handle Record-style members", () => {
        const cmd = createZAdd("zset", { member1: 10, member2: 20 });
        const args = cmd.argsArray?.args?.map((a) => a.toString()) ?? [];
        expect(args).toContain("zset");
        expect(args).toContain("10");
        expect(args).toContain("member1");
        expect(args).toContain("20");
        expect(args).toContain("member2");
    });

    it("should include CH flag when changed is true", () => {
        const cmd = createZAdd(
            "zset",
            [{ element: "a", score: 1 }],
            { changed: true },
        );
        const args = cmd.argsArray?.args?.map((a) => a.toString()) ?? [];
        expect(args).toContain("CH");
    });

    it("should include INCR flag when incr is true", () => {
        const cmd = createZAdd(
            "zset",
            [{ element: "a", score: 1 }],
            undefined,
            true,
        );
        const args = cmd.argsArray?.args?.map((a) => a.toString()) ?? [];
        expect(args).toContain("INCR");
    });

    it("should handle +inf and -inf scores", () => {
        const cmd = createZAdd("zset", [
            { element: "low", score: "-inf" },
            { element: "high", score: "+inf" },
        ]);
        const args = cmd.argsArray?.args?.map((a) => a.toString()) ?? [];
        expect(args).toContain("-inf");
        expect(args).toContain("+inf");
    });
});

describe("createRestore validation", () => {
    it("should throw when both IDLETIME and FREQ are specified", () => {
        expect(() =>
            createRestore("key", 0, "serialized", {
                idletime: 100,
                frequency: 5,
            }),
        ).toThrow("both IDLETIME and FREQ cannot be set at the same time");
    });

    it("should not throw with only IDLETIME", () => {
        expect(() =>
            createRestore("key", 0, "serialized", { idletime: 100 }),
        ).not.toThrow();
    });

    it("should not throw with only FREQ", () => {
        expect(() =>
            createRestore("key", 0, "serialized", { frequency: 5 }),
        ).not.toThrow();
    });

    it("should produce correct args with REPLACE and ABSTTL", () => {
        const cmd = createRestore("key", 1000, "data", {
            replace: true,
            absttl: true,
        });
        const args = cmd.argsArray?.args?.map((a) => a.toString()) ?? [];
        expect(args).toContain("REPLACE");
        expect(args).toContain("ABSTTL");
    });

    it("should produce correct args with IDLETIME", () => {
        const cmd = createRestore("key", 0, "data", { idletime: 500 });
        const args = cmd.argsArray?.args?.map((a) => a.toString()) ?? [];
        expect(args).toContain("IDLETIME");
        expect(args).toContain("500");
    });

    it("should produce correct args with FREQ", () => {
        const cmd = createRestore("key", 0, "data", { frequency: 42 });
        const args = cmd.argsArray?.args?.map((a) => a.toString()) ?? [];
        expect(args).toContain("FREQ");
        expect(args).toContain("42");
    });

    it("should produce correct args with no options", () => {
        const cmd = createRestore("key", 5000, "data");
        const args = cmd.argsArray?.args?.map((a) => a.toString()) ?? [];
        expect(args).toEqual(["key", "5000", "data"]);
    });

    it("should handle zero values for IDLETIME", () => {
        const cmd = createRestore("key", 0, "data", { idletime: 0 });
        const args = cmd.argsArray?.args?.map((a) => a.toString()) ?? [];
        expect(args).toContain("IDLETIME");
        expect(args).toContain("0");
    });

    it("should handle zero values for FREQ", () => {
        const cmd = createRestore("key", 0, "data", { frequency: 0 });
        const args = cmd.argsArray?.args?.map((a) => a.toString()) ?? [];
        expect(args).toContain("FREQ");
        expect(args).toContain("0");
    });
});

describe("createLCS options", () => {
    it("should produce basic args with no options", () => {
        const cmd = createLCS("key1", "key2");
        const args = cmd.argsArray?.args?.map((a) => a.toString()) ?? [];
        expect(args).toEqual(["key1", "key2"]);
    });

    it("should include LEN when len option is true", () => {
        const cmd = createLCS("key1", "key2", { len: true });
        const args = cmd.argsArray?.args?.map((a) => a.toString()) ?? [];
        expect(args).toEqual(["key1", "key2", "LEN"]);
    });

    it("should include IDX when idx option is provided", () => {
        const cmd = createLCS("key1", "key2", { idx: {} });
        const args = cmd.argsArray?.args?.map((a) => a.toString()) ?? [];
        expect(args).toEqual(["key1", "key2", "IDX"]);
    });

    it("should include WITHMATCHLEN when specified", () => {
        const cmd = createLCS("key1", "key2", {
            idx: { withMatchLen: true },
        });
        const args = cmd.argsArray?.args?.map((a) => a.toString()) ?? [];
        expect(args).toContain("IDX");
        expect(args).toContain("WITHMATCHLEN");
    });

    it("should include MINMATCHLEN when specified", () => {
        const cmd = createLCS("key1", "key2", {
            idx: { minMatchLen: 5 },
        });
        const args = cmd.argsArray?.args?.map((a) => a.toString()) ?? [];
        expect(args).toContain("IDX");
        expect(args).toContain("MINMATCHLEN");
        expect(args).toContain("5");
    });

    it("should include both WITHMATCHLEN and MINMATCHLEN", () => {
        const cmd = createLCS("key1", "key2", {
            idx: { withMatchLen: true, minMatchLen: 3 },
        });
        const args = cmd.argsArray?.args?.map((a) => a.toString()) ?? [];
        expect(args).toEqual([
            "key1",
            "key2",
            "IDX",
            "WITHMATCHLEN",
            "MINMATCHLEN",
            "3",
        ]);
    });

    it("should prefer LEN over IDX when both are set", () => {
        // The implementation uses if/else: len takes priority
        const cmd = createLCS("key1", "key2", {
            len: true,
            idx: { withMatchLen: true },
        });
        const args = cmd.argsArray?.args?.map((a) => a.toString()) ?? [];
        expect(args).toContain("LEN");
        expect(args).not.toContain("IDX");
    });
});

describe("createHSetEx validation", () => {
    it("should throw on non-integer expiry count", () => {
        expect(() =>
            createHSetEx(
                "key",
                [{ field: "f1", value: "v1" }],
                { expiry: { type: TimeUnit.Seconds, count: 1.5 } },
            ),
        ).toThrow("Count must be an integer");
    });

    it("should not throw with KEEPTTL expiry", () => {
        expect(() =>
            createHSetEx(
                "key",
                [{ field: "f1", value: "v1" }],
                { expiry: "KEEPTTL" },
            ),
        ).not.toThrow();
    });

    it("should produce correct args with KEEPTTL", () => {
        const cmd = createHSetEx(
            "key",
            [{ field: "f1", value: "v1" }],
            { expiry: "KEEPTTL" },
        );
        const args = cmd.argsArray?.args?.map((a) => a.toString()) ?? [];
        expect(args).toContain("KEEPTTL");
    });

    it("should produce correct args with field-value pairs", () => {
        const cmd = createHSetEx(
            "myhash",
            [
                { field: "name", value: "Alice" },
                { field: "age", value: "30" },
            ],
            { expiry: { type: TimeUnit.Seconds, count: 60 } },
        );
        const args = cmd.argsArray?.args?.map((a) => a.toString()) ?? [];
        expect(args[0]).toBe("myhash");
        expect(args).toContain("EX");
        expect(args).toContain("60");
        expect(args).toContain("FIELDS");
        expect(args).toContain("2");
        expect(args).toContain("name");
        expect(args).toContain("Alice");
    });

    it("should handle empty field-value list", () => {
        const cmd = createHSetEx("key", []);
        const args = cmd.argsArray?.args?.map((a) => a.toString()) ?? [];
        // Should NOT include FIELDS when list is empty
        expect(args).not.toContain("FIELDS");
    });
});

describe("createHGetEx validation", () => {
    it("should throw on non-integer expiry count", () => {
        expect(() =>
            createHGetEx(
                "key",
                ["field1"],
                { expiry: { type: TimeUnit.Seconds, count: 2.7 } },
            ),
        ).toThrow("Count must be an integer");
    });

    it("should not throw with PERSIST expiry", () => {
        expect(() =>
            createHGetEx("key", ["field1"], { expiry: "PERSIST" }),
        ).not.toThrow();
    });

    it("should produce correct args with PERSIST", () => {
        const cmd = createHGetEx("key", ["f1", "f2"], { expiry: "PERSIST" });
        const args = cmd.argsArray?.args?.map((a) => a.toString()) ?? [];
        expect(args).toContain("PERSIST");
        expect(args).toContain("FIELDS");
        expect(args).toContain("2");
    });

    it("should produce correct args with expiry type and fields", () => {
        const cmd = createHGetEx(
            "myhash",
            ["name", "age", "email"],
            { expiry: { type: TimeUnit.Milliseconds, count: 5000 } },
        );
        const args = cmd.argsArray?.args?.map((a) => a.toString()) ?? [];
        expect(args[0]).toBe("myhash");
        expect(args).toContain("PX");
        expect(args).toContain("5000");
        expect(args).toContain("FIELDS");
        expect(args).toContain("3");
    });

    it("should produce correct args with no expiry options", () => {
        const cmd = createHGetEx("key", ["f1"]);
        const args = cmd.argsArray?.args?.map((a) => a.toString()) ?? [];
        expect(args).toEqual(["key", "FIELDS", "1", "f1"]);
    });
});
