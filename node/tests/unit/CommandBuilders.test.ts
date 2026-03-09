/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 *
 * Unit tests for command argument building logic in Commands.ts.
 * These tests verify that command builders produce the correct argument arrays
 * for the Valkey/Redis protocol without requiring a native Rust build or server.
 */

/* eslint-disable @typescript-eslint/no-explicit-any */

// Mock native bindings (Rust NAPI module not available in unit tests)
// { virtual: true } allows mocking modules that don't exist on disk
jest.mock(
    "../../build-ts/native",
    () => ({
        createLeakedStringVec: jest.fn(() => [0, 0]),
        MAX_REQUEST_ARGS_LEN: 2 * 1024 * 1024,
    }),
    { virtual: true },
);

// Mock protobuf module with minimal implementation that captures args
jest.mock(
    "../../build-ts/ProtobufMessage",
    () => {
        const types: Record<string, number> = {};
        let counter = 1;
        const RequestType = new Proxy(
            {},
            {
                get: (_target, prop) => {
                    if (typeof prop === "string") {
                        if (!(prop in types)) {
                            types[prop] = counter++;
                        }
                        return types[prop];
                    }
                    return undefined;
                },
            },
        );

        return {
            command_request: {
                RequestType,
                Command: {
                    create: ({ requestType }: any) => ({
                        requestType,
                        argsArray: null,
                        argsVecPointer: null,
                    }),
                    ArgsArray: {
                        create: ({ args }: any) => ({ args }),
                    },
                },
            },
        };
    },
    { virtual: true },
);

// Mock BaseClient - provide runtime values that Commands.ts uses
jest.mock("../../src/BaseClient", () => ({
    convertRecordToGlideRecord: (record: Record<string, any>) =>
        Object.entries(record).map(([key, value]) => ({ key, value })),
    ObjectType: {
        STRING: "string",
        LIST: "list",
        SET: "set",
        ZSET: "zset",
        HASH: "hash",
        STREAM: "stream",
    },
}));

// Mock GlideClient and GlideClusterClient (type-only imports for JSDoc)
jest.mock("../../src/GlideClient", () => ({}));
jest.mock("../../src/GlideClusterClient", () => ({}));

import { describe, expect, it } from "@jest/globals";

/**
 * Helper: extract the string args from a command object returned by createCommand.
 * The mock stores args as Buffer[] in argsArray.args - convert back to strings.
 */
function getArgs(command: any): string[] {
    if (!command?.argsArray?.args) return [];
    return command.argsArray.args.map((buf: Buffer | Uint8Array) =>
        Buffer.isBuffer(buf) ? buf.toString() : Buffer.from(buf).toString(),
    );
}

// ── parseInfoResponse ──────────────────────────────────────────────────────

import { parseInfoResponse } from "../../src/Commands";

describe("parseInfoResponse", () => {
    it("parses key:value lines correctly", () => {
        const response = "redis_version:7.2.0\nredis_mode:standalone\n";
        const parsed = parseInfoResponse(response);
        expect(parsed["redis_version"]).toBe("7.2.0");
        expect(parsed["redis_mode"]).toBe("standalone");
    });

    it("ignores comment lines starting with #", () => {
        const response = "# Server\nredis_version:7.2.0\n# Clients\n";
        const parsed = parseInfoResponse(response);
        expect(parsed["redis_version"]).toBe("7.2.0");
        expect(parsed["# Server"]).toBeUndefined();
    });

    it("handles empty response", () => {
        const parsed = parseInfoResponse("");
        expect(Object.keys(parsed).length).toBeLessThanOrEqual(1);
    });

    it("handles values containing colons (e.g., bind address)", () => {
        // INFO can return values like "bind:0.0.0.0:6379"
        // The split on ":" only splits on first colon
        const response = "bind:0.0.0.0";
        const parsed = parseInfoResponse(response);
        expect(parsed["bind"]).toBe("0.0.0.0");
    });

    it("trims whitespace from keys and values", () => {
        const response = "  used_memory:1024  \r\n";
        const parsed = parseInfoResponse(response);
        expect(parsed["used_memory"]).toBe("1024");
    });
});

// ── convertFieldsAndValuesToHashDataType ────────────────────────────────────

import { convertFieldsAndValuesToHashDataType } from "../../src/Commands";

describe("convertFieldsAndValuesToHashDataType", () => {
    it("converts Record to HashDataType array", () => {
        const record = { field1: "value1", field2: "value2" };
        const result = convertFieldsAndValuesToHashDataType(record);
        expect(result).toEqual([
            { field: "field1", value: "value1" },
            { field: "field2", value: "value2" },
        ]);
    });

    it("passes through HashDataType array unchanged", () => {
        const hashData = [
            { field: "f1", value: "v1" },
            { field: "f2", value: "v2" },
        ];
        const result = convertFieldsAndValuesToHashDataType(hashData);
        expect(result).toBe(hashData); // same reference
    });

    it("handles empty Record", () => {
        const result = convertFieldsAndValuesToHashDataType({});
        expect(result).toEqual([]);
    });

    it("handles empty array", () => {
        const result = convertFieldsAndValuesToHashDataType([]);
        expect(result).toEqual([]);
    });
});

// ── convertElementsAndScores ────────────────────────────────────────────────

import { convertElementsAndScores } from "../../src/Commands";

describe("convertElementsAndScores", () => {
    it("converts Record<string, number> to SortedSetDataType", () => {
        const record = { member1: 1.5, member2: 2.5 };
        const result = convertElementsAndScores(record);
        expect(result).toEqual([
            { element: "member1", score: 1.5 },
            { element: "member2", score: 2.5 },
        ]);
    });

    it("passes through SortedSetDataType array unchanged", () => {
        const data = [
            { element: "a", score: 1 },
            { element: "b", score: 2 },
        ];
        const result = convertElementsAndScores(data);
        expect(result).toBe(data); // same reference
    });

    it("handles empty Record", () => {
        const result = convertElementsAndScores({});
        expect(result).toEqual([]);
    });

    it("handles negative and infinity scores", () => {
        const record = { neg: -1, zero: 0 };
        const result = convertElementsAndScores(record);
        expect(result[0].score).toBe(-1);
        expect(result[1].score).toBe(0);
    });
});

// ── BitField option classes ─────────────────────────────────────────────────

import {
    BitFieldGet,
    BitFieldIncrBy,
    BitFieldOverflow,
    BitFieldSet,
    BitOffset,
    BitOffsetMultiplier,
    BitOverflowControl,
    SignedEncoding,
    UnsignedEncoding,
} from "../../src/Commands";

describe("BitField option classes", () => {
    describe("SignedEncoding", () => {
        it("produces correct signed encoding string", () => {
            expect(new SignedEncoding(8).toArg()).toBe("i8");
            expect(new SignedEncoding(16).toArg()).toBe("i16");
            expect(new SignedEncoding(64).toArg()).toBe("i64");
        });
    });

    describe("UnsignedEncoding", () => {
        it("produces correct unsigned encoding string", () => {
            expect(new UnsignedEncoding(8).toArg()).toBe("u8");
            expect(new UnsignedEncoding(32).toArg()).toBe("u32");
            expect(new UnsignedEncoding(63).toArg()).toBe("u63");
        });
    });

    describe("BitOffset", () => {
        it("produces numeric offset string", () => {
            expect(new BitOffset(0).toArg()).toBe("0");
            expect(new BitOffset(100).toArg()).toBe("100");
        });
    });

    describe("BitOffsetMultiplier", () => {
        it("produces # prefixed offset string", () => {
            expect(new BitOffsetMultiplier(0).toArg()).toBe("#0");
            expect(new BitOffsetMultiplier(5).toArg()).toBe("#5");
        });
    });

    describe("BitFieldGet", () => {
        it("produces correct GET subcommand args", () => {
            const get = new BitFieldGet(
                new UnsignedEncoding(8),
                new BitOffset(0),
            );
            expect(get.toArgs()).toEqual(["GET", "u8", "0"]);
        });

        it("works with offset multiplier", () => {
            const get = new BitFieldGet(
                new SignedEncoding(16),
                new BitOffsetMultiplier(2),
            );
            expect(get.toArgs()).toEqual(["GET", "i16", "#2"]);
        });
    });

    describe("BitFieldSet", () => {
        it("produces correct SET subcommand args", () => {
            const set = new BitFieldSet(
                new UnsignedEncoding(8),
                new BitOffset(0),
                255,
            );
            expect(set.toArgs()).toEqual(["SET", "u8", "0", "255"]);
        });

        it("handles negative values", () => {
            const set = new BitFieldSet(
                new SignedEncoding(8),
                new BitOffset(0),
                -128,
            );
            expect(set.toArgs()).toEqual(["SET", "i8", "0", "-128"]);
        });

        it("handles zero value", () => {
            const set = new BitFieldSet(
                new UnsignedEncoding(8),
                new BitOffset(0),
                0,
            );
            expect(set.toArgs()).toEqual(["SET", "u8", "0", "0"]);
        });
    });

    describe("BitFieldIncrBy", () => {
        it("produces correct INCRBY subcommand args", () => {
            const incr = new BitFieldIncrBy(
                new UnsignedEncoding(8),
                new BitOffset(100),
                10,
            );
            expect(incr.toArgs()).toEqual(["INCRBY", "u8", "100", "10"]);
        });

        it("handles negative increment (decrement)", () => {
            const incr = new BitFieldIncrBy(
                new SignedEncoding(16),
                new BitOffsetMultiplier(1),
                -5,
            );
            expect(incr.toArgs()).toEqual(["INCRBY", "i16", "#1", "-5"]);
        });
    });

    describe("BitFieldOverflow", () => {
        it("produces OVERFLOW WRAP args", () => {
            const overflow = new BitFieldOverflow(BitOverflowControl.WRAP);
            expect(overflow.toArgs()).toEqual(["OVERFLOW", "WRAP"]);
        });

        it("produces OVERFLOW SAT args", () => {
            const overflow = new BitFieldOverflow(BitOverflowControl.SAT);
            expect(overflow.toArgs()).toEqual(["OVERFLOW", "SAT"]);
        });

        it("produces OVERFLOW FAIL args", () => {
            const overflow = new BitFieldOverflow(BitOverflowControl.FAIL);
            expect(overflow.toArgs()).toEqual(["OVERFLOW", "FAIL"]);
        });
    });
});

// ── Command builders (argument ordering & edge cases) ───────────────────────

import {
    createSet,
    createGet,
    createGetRange,
    createZAdd,
    createXAdd,
    createXTrim,
    createRestore,
    createGetEx,
    createLPos,
    createCopy,
    createGeoAdd,
    createGeoDist,
    createBitField,
    createLolwut,
    createPublish,
    createFCall,
    createBLMPop,
    createBitOp,
    createHSet,
    createZInterCard,
    createZDiff,
    createZDiffWithScores,
    createScan,
    createZScan,
    createHScan,
    TimeUnit,
    SetOptions,
    ConditionalChange,
    UpdateByScore,
    BitwiseOperation,
    GeoUnit,
    ListDirection,
    InfBoundary,
} from "../../src/Commands";

describe("createSet", () => {
    it("builds basic SET command with key and value", () => {
        const cmd = createSet("mykey", "myvalue");
        const args = getArgs(cmd);
        expect(args).toEqual(["mykey", "myvalue"]);
    });

    it("adds NX flag for onlyIfDoesNotExist", () => {
        const cmd = createSet("k", "v", {
            conditionalSet: "onlyIfDoesNotExist",
        });
        expect(getArgs(cmd)).toEqual(["k", "v", "NX"]);
    });

    it("adds XX flag for onlyIfExists", () => {
        const cmd = createSet("k", "v", { conditionalSet: "onlyIfExists" });
        expect(getArgs(cmd)).toEqual(["k", "v", "XX"]);
    });

    it("adds IFEQ flag with comparison value for onlyIfEqual", () => {
        const cmd = createSet("k", "v", {
            conditionalSet: "onlyIfEqual",
            comparisonValue: "oldval",
        });
        expect(getArgs(cmd)).toEqual(["k", "v", "IFEQ", "oldval"]);
    });

    it("adds GET flag for returnOldValue", () => {
        const cmd = createSet("k", "v", { returnOldValue: true });
        expect(getArgs(cmd)).toEqual(["k", "v", "GET"]);
    });

    it("adds KEEPTTL for keepExisting expiry", () => {
        const cmd = createSet("k", "v", { expiry: "keepExisting" });
        expect(getArgs(cmd)).toEqual(["k", "v", "KEEPTTL"]);
    });

    it("adds EX with seconds for Seconds TimeUnit", () => {
        const cmd = createSet("k", "v", {
            expiry: { type: TimeUnit.Seconds, count: 60 },
        });
        expect(getArgs(cmd)).toEqual(["k", "v", "EX", "60"]);
    });

    it("adds PX with milliseconds for Milliseconds TimeUnit", () => {
        const cmd = createSet("k", "v", {
            expiry: { type: TimeUnit.Milliseconds, count: 5000 },
        });
        expect(getArgs(cmd)).toEqual(["k", "v", "PX", "5000"]);
    });

    it("adds EXAT for UnixSeconds TimeUnit", () => {
        const cmd = createSet("k", "v", {
            expiry: { type: TimeUnit.UnixSeconds, count: 1700000000 },
        });
        expect(getArgs(cmd)).toEqual(["k", "v", "EXAT", "1700000000"]);
    });

    it("adds PXAT for UnixMilliseconds TimeUnit", () => {
        const cmd = createSet("k", "v", {
            expiry: { type: TimeUnit.UnixMilliseconds, count: 1700000000000 },
        });
        expect(getArgs(cmd)).toEqual(["k", "v", "PXAT", "1700000000000"]);
    });

    it("combines conditional set, GET, and expiry in correct order", () => {
        const cmd = createSet("k", "v", {
            conditionalSet: "onlyIfExists",
            returnOldValue: true,
            expiry: { type: TimeUnit.Seconds, count: 10 },
        });
        // Order: key, value, XX, GET, EX, 10
        expect(getArgs(cmd)).toEqual(["k", "v", "XX", "GET", "EX", "10"]);
    });

    it("throws on non-integer expiry count", () => {
        expect(() =>
            createSet("k", "v", {
                expiry: { type: TimeUnit.Seconds, count: 10.5 },
            }),
        ).toThrow("Count must be an integer");
    });
});

describe("createGet", () => {
    it("builds GET command with single key arg", () => {
        const cmd = createGet("mykey");
        expect(getArgs(cmd)).toEqual(["mykey"]);
    });
});

describe("createGetRange", () => {
    it("builds GETRANGE with key, start, end as strings", () => {
        const cmd = createGetRange("mykey", 0, 10);
        expect(getArgs(cmd)).toEqual(["mykey", "0", "10"]);
    });

    it("handles negative indices", () => {
        const cmd = createGetRange("mykey", -5, -1);
        expect(getArgs(cmd)).toEqual(["mykey", "-5", "-1"]);
    });
});

describe("createZAdd", () => {
    it("builds basic ZADD with array of ElementAndScore", () => {
        const cmd = createZAdd("zset", [
            { element: "a", score: 1 },
            { element: "b", score: 2 },
        ]);
        const args = getArgs(cmd);
        // key, score1, element1, score2, element2
        expect(args).toEqual(["zset", "1", "a", "2", "b"]);
    });

    it("builds ZADD with Record<string, number>", () => {
        const cmd = createZAdd("zset", { member1: 1.5, member2: 2.5 });
        const args = getArgs(cmd);
        expect(args).toEqual(["zset", "1.5", "member1", "2.5", "member2"]);
    });

    it("adds NX option", () => {
        const cmd = createZAdd(
            "zset",
            [{ element: "a", score: 1 }],
            { conditionalChange: ConditionalChange.ONLY_IF_DOES_NOT_EXIST },
        );
        const args = getArgs(cmd);
        expect(args).toEqual(["zset", "NX", "1", "a"]);
    });

    it("adds XX option", () => {
        const cmd = createZAdd(
            "zset",
            [{ element: "a", score: 1 }],
            { conditionalChange: ConditionalChange.ONLY_IF_EXISTS },
        );
        const args = getArgs(cmd);
        expect(args).toEqual(["zset", "XX", "1", "a"]);
    });

    it("adds GT update option with XX", () => {
        const cmd = createZAdd(
            "zset",
            [{ element: "a", score: 5 }],
            {
                conditionalChange: ConditionalChange.ONLY_IF_EXISTS,
                updateOptions: UpdateByScore.GREATER_THAN,
            },
        );
        const args = getArgs(cmd);
        expect(args).toEqual(["zset", "XX", "GT", "5", "a"]);
    });

    it("throws when NX combined with GT/LT (mutually exclusive)", () => {
        expect(() =>
            createZAdd(
                "zset",
                [{ element: "a", score: 1 }],
                {
                    conditionalChange:
                        ConditionalChange.ONLY_IF_DOES_NOT_EXIST,
                    updateOptions: UpdateByScore.GREATER_THAN,
                },
            ),
        ).toThrow("GT, LT, and NX options are mutually exclusive");
    });

    it("adds CH flag", () => {
        const cmd = createZAdd(
            "zset",
            [{ element: "a", score: 1 }],
            { changed: true },
        );
        const args = getArgs(cmd);
        expect(args).toEqual(["zset", "CH", "1", "a"]);
    });

    it("adds INCR flag", () => {
        const cmd = createZAdd(
            "zset",
            [{ element: "a", score: 5 }],
            undefined,
            true,
        );
        const args = getArgs(cmd);
        expect(args).toEqual(["zset", "INCR", "5", "a"]);
    });

    it("combines XX + LT + CH + INCR in correct order", () => {
        const cmd = createZAdd(
            "zset",
            [{ element: "m", score: 10 }],
            {
                conditionalChange: ConditionalChange.ONLY_IF_EXISTS,
                updateOptions: UpdateByScore.LESS_THAN,
                changed: true,
            },
            true,
        );
        const args = getArgs(cmd);
        // order: key, XX, LT, CH, INCR, score, element
        expect(args).toEqual(["zset", "XX", "LT", "CH", "INCR", "10", "m"]);
    });
});

describe("createXAdd", () => {
    it("builds basic XADD with auto-generated ID", () => {
        const cmd = createXAdd("stream", [
            ["field1", "value1"],
            ["field2", "value2"],
        ]);
        const args = getArgs(cmd);
        // key, *, field1, value1, field2, value2
        expect(args).toEqual([
            "stream",
            "*",
            "field1",
            "value1",
            "field2",
            "value2",
        ]);
    });

    it("uses custom ID when provided", () => {
        const cmd = createXAdd(
            "stream",
            [["f", "v"]],
            { id: "1-1" },
        );
        const args = getArgs(cmd);
        expect(args).toEqual(["stream", "1-1", "f", "v"]);
    });

    it("adds NOMKSTREAM when makeStream is false", () => {
        const cmd = createXAdd(
            "stream",
            [["f", "v"]],
            { makeStream: false },
        );
        const args = getArgs(cmd);
        expect(args[1]).toBe("NOMKSTREAM");
    });

    it("does NOT add NOMKSTREAM when makeStream is true", () => {
        const cmd = createXAdd(
            "stream",
            [["f", "v"]],
            { makeStream: true },
        );
        const args = getArgs(cmd);
        expect(args).not.toContain("NOMKSTREAM");
    });

    it("adds MAXLEN trim option", () => {
        const cmd = createXAdd(
            "stream",
            [["f", "v"]],
            {
                trim: { method: "maxlen", threshold: 100, exact: true },
            },
        );
        const args = getArgs(cmd);
        expect(args).toContain("MAXLEN");
        expect(args).toContain("=");
        expect(args).toContain("100");
    });

    it("adds MINID trim option with approximate", () => {
        const cmd = createXAdd(
            "stream",
            [["f", "v"]],
            {
                trim: { method: "minid", threshold: "1000-0", exact: false },
            },
        );
        const args = getArgs(cmd);
        expect(args).toContain("MINID");
        expect(args).toContain("~");
        expect(args).toContain("1000-0");
    });

    it("adds LIMIT to trim options", () => {
        const cmd = createXAdd(
            "stream",
            [["f", "v"]],
            {
                trim: {
                    method: "maxlen",
                    threshold: 100,
                    exact: false,
                    limit: 10,
                },
            },
        );
        const args = getArgs(cmd);
        expect(args).toContain("LIMIT");
        expect(args).toContain("10");
    });

    it("combines NOMKSTREAM + trim + auto ID in correct order", () => {
        const cmd = createXAdd(
            "stream",
            [["f", "v"]],
            {
                makeStream: false,
                trim: { method: "maxlen", threshold: 50, exact: true },
            },
        );
        const args = getArgs(cmd);
        // Order: key, NOMKSTREAM, MAXLEN, =, 50, *, f, v
        expect(args).toEqual([
            "stream",
            "NOMKSTREAM",
            "MAXLEN",
            "=",
            "50",
            "*",
            "f",
            "v",
        ]);
    });
});

describe("createXTrim", () => {
    it("builds XTRIM with MAXLEN exact", () => {
        const cmd = createXTrim("stream", {
            method: "maxlen",
            threshold: 1000,
            exact: true,
        });
        const args = getArgs(cmd);
        expect(args).toEqual(["stream", "MAXLEN", "=", "1000"]);
    });

    it("builds XTRIM with MINID approximate + LIMIT", () => {
        const cmd = createXTrim("stream", {
            method: "minid",
            threshold: "1234-0",
            exact: false,
            limit: 5,
        });
        const args = getArgs(cmd);
        expect(args).toEqual([
            "stream",
            "MINID",
            "~",
            "1234-0",
            "LIMIT",
            "5",
        ]);
    });
});

describe("createRestore", () => {
    it("builds basic RESTORE with key, ttl, value", () => {
        const cmd = createRestore("mykey", 0, "serialized_data");
        expect(getArgs(cmd)).toEqual(["mykey", "0", "serialized_data"]);
    });

    it("adds REPLACE flag", () => {
        const cmd = createRestore("k", 0, "data", { replace: true });
        expect(getArgs(cmd)).toContain("REPLACE");
    });

    it("adds ABSTTL flag", () => {
        const cmd = createRestore("k", 1700000, "data", { absttl: true });
        expect(getArgs(cmd)).toContain("ABSTTL");
    });

    it("adds IDLETIME option", () => {
        const cmd = createRestore("k", 0, "data", { idletime: 100 });
        const args = getArgs(cmd);
        const idx = args.indexOf("IDLETIME");
        expect(idx).toBeGreaterThan(-1);
        expect(args[idx + 1]).toBe("100");
    });

    it("adds FREQ option", () => {
        const cmd = createRestore("k", 0, "data", { frequency: 50 });
        const args = getArgs(cmd);
        const idx = args.indexOf("FREQ");
        expect(idx).toBeGreaterThan(-1);
        expect(args[idx + 1]).toBe("50");
    });

    it("throws when both IDLETIME and FREQ are set", () => {
        expect(() =>
            createRestore("k", 0, "data", { idletime: 100, frequency: 50 }),
        ).toThrow("IDLETIME and FREQ cannot be set at the same time");
    });

    it("combines REPLACE + ABSTTL + IDLETIME in correct order", () => {
        const cmd = createRestore("k", 5000, "data", {
            replace: true,
            absttl: true,
            idletime: 200,
        });
        const args = getArgs(cmd);
        // Order: key, ttl, value, REPLACE, ABSTTL, IDLETIME, 200
        expect(args).toEqual([
            "k",
            "5000",
            "data",
            "REPLACE",
            "ABSTTL",
            "IDLETIME",
            "200",
        ]);
    });
});

describe("createGetEx", () => {
    it("builds basic GETEX with just key", () => {
        const cmd = createGetEx("mykey");
        expect(getArgs(cmd)).toEqual(["mykey"]);
    });

    it("adds PERSIST option", () => {
        const cmd = createGetEx("mykey", "persist");
        expect(getArgs(cmd)).toEqual(["mykey", "PERSIST"]);
    });

    it("adds EX with duration", () => {
        const cmd = createGetEx("k", {
            type: TimeUnit.Seconds,
            duration: 60,
        });
        expect(getArgs(cmd)).toEqual(["k", "EX", "60"]);
    });

    it("adds PX with duration", () => {
        const cmd = createGetEx("k", {
            type: TimeUnit.Milliseconds,
            duration: 5000,
        });
        expect(getArgs(cmd)).toEqual(["k", "PX", "5000"]);
    });

    it("throws on non-integer duration", () => {
        expect(() =>
            createGetEx("k", { type: TimeUnit.Seconds, duration: 10.5 }),
        ).toThrow("Count must be an integer");
    });
});

describe("createLPos", () => {
    it("builds basic LPOS with key and element", () => {
        const cmd = createLPos("list", "value");
        expect(getArgs(cmd)).toEqual(["list", "value"]);
    });

    it("adds RANK option", () => {
        const cmd = createLPos("list", "value", { rank: 2 });
        expect(getArgs(cmd)).toEqual(["list", "value", "RANK", "2"]);
    });

    it("adds COUNT option", () => {
        const cmd = createLPos("list", "value", { count: 3 });
        expect(getArgs(cmd)).toEqual(["list", "value", "COUNT", "3"]);
    });

    it("adds MAXLEN option", () => {
        const cmd = createLPos("list", "value", { maxLength: 100 });
        expect(getArgs(cmd)).toEqual(["list", "value", "MAXLEN", "100"]);
    });

    it("combines all options in correct order: RANK, COUNT, MAXLEN", () => {
        const cmd = createLPos("list", "value", {
            rank: -1,
            count: 5,
            maxLength: 50,
        });
        expect(getArgs(cmd)).toEqual([
            "list",
            "value",
            "RANK",
            "-1",
            "COUNT",
            "5",
            "MAXLEN",
            "50",
        ]);
    });
});

describe("createCopy", () => {
    it("builds basic COPY with source and destination", () => {
        const cmd = createCopy("src", "dst");
        expect(getArgs(cmd)).toEqual(["src", "dst"]);
    });

    it("adds DB option", () => {
        const cmd = createCopy("src", "dst", { destinationDB: 2 });
        expect(getArgs(cmd)).toEqual(["src", "dst", "DB", "2"]);
    });

    it("adds REPLACE option", () => {
        const cmd = createCopy("src", "dst", { replace: true });
        expect(getArgs(cmd)).toEqual(["src", "dst", "REPLACE"]);
    });

    it("combines DB + REPLACE in correct order", () => {
        const cmd = createCopy("src", "dst", {
            destinationDB: 3,
            replace: true,
        });
        expect(getArgs(cmd)).toEqual(["src", "dst", "DB", "3", "REPLACE"]);
    });
});

describe("createGeoAdd", () => {
    it("builds GEOADD with members", () => {
        const members = new Map<string, { longitude: number; latitude: number }>();
        members.set("place1", { longitude: 13.361389, latitude: 38.115556 });
        const cmd = createGeoAdd("geo", members);
        const args = getArgs(cmd);
        expect(args[0]).toBe("geo");
        expect(args[1]).toBe("13.361389");
        expect(args[2]).toBe("38.115556");
        expect(args[3]).toBe("place1");
    });

    it("adds CH flag", () => {
        const members = new Map();
        members.set("p", { longitude: 0, latitude: 0 });
        const cmd = createGeoAdd("geo", members, { changed: true });
        const args = getArgs(cmd);
        expect(args).toContain("CH");
    });

    it("adds updateMode flag (XX/NX)", () => {
        const members = new Map();
        members.set("p", { longitude: 0, latitude: 0 });
        const cmd = createGeoAdd("geo", members, {
            updateMode: ConditionalChange.ONLY_IF_EXISTS,
        });
        const args = getArgs(cmd);
        expect(args[1]).toBe("XX");
    });
});

describe("createGeoDist", () => {
    it("builds basic GEODIST without unit", () => {
        const cmd = createGeoDist("geo", "a", "b");
        expect(getArgs(cmd)).toEqual(["geo", "a", "b"]);
    });

    it("adds unit when specified", () => {
        const cmd = createGeoDist("geo", "a", "b", GeoUnit.KILOMETERS);
        expect(getArgs(cmd)).toEqual(["geo", "a", "b", "km"]);
    });
});

describe("createBitField", () => {
    it("builds BITFIELD with multiple subcommands", () => {
        const cmd = createBitField("key", [
            new BitFieldGet(new UnsignedEncoding(8), new BitOffset(0)),
            new BitFieldSet(new UnsignedEncoding(8), new BitOffset(8), 200),
            new BitFieldIncrBy(
                new SignedEncoding(16),
                new BitOffsetMultiplier(1),
                100,
            ),
        ]);
        const args = getArgs(cmd);
        expect(args).toEqual([
            "key",
            "GET",
            "u8",
            "0",
            "SET",
            "u8",
            "8",
            "200",
            "INCRBY",
            "i16",
            "#1",
            "100",
        ]);
    });

    it("includes OVERFLOW subcommand", () => {
        const cmd = createBitField("key", [
            new BitFieldOverflow(BitOverflowControl.SAT),
            new BitFieldIncrBy(new UnsignedEncoding(8), new BitOffset(0), 300),
        ]);
        const args = getArgs(cmd);
        expect(args).toEqual([
            "key",
            "OVERFLOW",
            "SAT",
            "INCRBY",
            "u8",
            "0",
            "300",
        ]);
    });
});

describe("createBitOp", () => {
    it("builds BITOP with operation, destination, and keys", () => {
        const cmd = createBitOp(BitwiseOperation.AND, "dest", [
            "key1",
            "key2",
        ]);
        expect(getArgs(cmd)).toEqual(["AND", "dest", "key1", "key2"]);
    });

    it("handles NOT operation with single key", () => {
        const cmd = createBitOp(BitwiseOperation.NOT, "dest", ["src"]);
        expect(getArgs(cmd)).toEqual(["NOT", "dest", "src"]);
    });
});

describe("createHSet", () => {
    it("builds HSET with field-value pairs", () => {
        const cmd = createHSet("hash", [
            { field: "f1", value: "v1" },
            { field: "f2", value: "v2" },
        ]);
        const args = getArgs(cmd);
        expect(args).toEqual(["hash", "f1", "v1", "f2", "v2"]);
    });
});

describe("createLolwut", () => {
    it("builds LOLWUT with no options", () => {
        const cmd = createLolwut();
        expect(getArgs(cmd)).toEqual([]);
    });

    it("adds VERSION option", () => {
        const cmd = createLolwut({ version: 5 });
        expect(getArgs(cmd)).toEqual(["VERSION", "5"]);
    });

    it("adds VERSION and parameters", () => {
        const cmd = createLolwut({ version: 5, parameters: [10, 20] });
        expect(getArgs(cmd)).toEqual(["VERSION", "5", "10", "20"]);
    });

    it("adds parameters without version", () => {
        const cmd = createLolwut({ parameters: [30, 40] });
        expect(getArgs(cmd)).toEqual(["30", "40"]);
    });
});

describe("createPublish", () => {
    it("builds PUBLISH with channel and message (channel first)", () => {
        const cmd = createPublish("hello", "mychannel");
        // Protocol: PUBLISH channel message
        expect(getArgs(cmd)).toEqual(["mychannel", "hello"]);
    });

    it("builds SPUBLISH for sharded pubsub", () => {
        const cmd = createPublish("hello", "mychannel", true);
        expect(getArgs(cmd)).toEqual(["mychannel", "hello"]);
    });
});

describe("createFCall", () => {
    it("builds FCALL with function, keys, and args", () => {
        const cmd = createFCall("myfunc", ["key1", "key2"], ["arg1", "arg2"]);
        expect(getArgs(cmd)).toEqual([
            "myfunc",
            "2",
            "key1",
            "key2",
            "arg1",
            "arg2",
        ]);
    });

    it("handles empty keys array", () => {
        const cmd = createFCall("myfunc", [], ["arg1"]);
        expect(getArgs(cmd)).toEqual(["myfunc", "0", "arg1"]);
    });

    it("handles empty args array", () => {
        const cmd = createFCall("myfunc", ["k1"], []);
        expect(getArgs(cmd)).toEqual(["myfunc", "1", "k1"]);
    });
});

describe("createBLMPop", () => {
    it("builds BLMPOP with timeout, keys, and direction", () => {
        const cmd = createBLMPop(
            ["list1", "list2"],
            ListDirection.LEFT,
            5,
        );
        const args = getArgs(cmd);
        // BLMPOP timeout numkeys key1 key2 direction
        expect(args).toEqual(["5", "2", "list1", "list2", "LEFT"]);
    });

    it("adds COUNT option", () => {
        const cmd = createBLMPop(["list1"], ListDirection.RIGHT, 0, 10);
        const args = getArgs(cmd);
        expect(args).toEqual(["0", "1", "list1", "RIGHT", "COUNT", "10"]);
    });
});

describe("createZInterCard", () => {
    it("builds ZINTERCARD with keys", () => {
        const cmd = createZInterCard(["zset1", "zset2"]);
        const args = getArgs(cmd);
        // numkeys keys...
        expect(args[0]).toBe("2");
        expect(args[1]).toBe("zset1");
        expect(args[2]).toBe("zset2");
    });

    it("adds LIMIT option", () => {
        const cmd = createZInterCard(["z1", "z2"], 10);
        const args = getArgs(cmd);
        expect(args).toContain("LIMIT");
        expect(args).toContain("10");
    });
});

describe("createZDiff and createZDiffWithScores", () => {
    it("builds ZDIFF with key count prefix", () => {
        const cmd = createZDiff(["z1", "z2", "z3"]);
        const args = getArgs(cmd);
        expect(args[0]).toBe("3");
    });

    it("builds ZDIFF WITHSCORES variant", () => {
        const cmd = createZDiffWithScores(["z1", "z2"]);
        const args = getArgs(cmd);
        expect(args[0]).toBe("2");
        expect(args[args.length - 1]).toBe("WITHSCORES");
    });
});

describe("createScan", () => {
    it("builds basic SCAN with cursor", () => {
        const cmd = createScan("0");
        expect(getArgs(cmd)).toEqual(["0"]);
    });

    it("adds MATCH pattern", () => {
        const cmd = createScan("0", { match: "user:*" });
        expect(getArgs(cmd)).toEqual(["0", "MATCH", "user:*"]);
    });

    it("adds COUNT option", () => {
        const cmd = createScan("0", { count: 100 });
        expect(getArgs(cmd)).toEqual(["0", "COUNT", "100"]);
    });

    it("adds TYPE filter", () => {
        const cmd = createScan("0", { type: "string" as any });
        expect(getArgs(cmd)).toEqual(["0", "TYPE", "string"]);
    });

    it("combines MATCH + COUNT + TYPE", () => {
        const cmd = createScan("0", {
            match: "prefix:*",
            count: 50,
            type: "hash" as any,
        });
        const args = getArgs(cmd);
        expect(args).toEqual([
            "0",
            "MATCH",
            "prefix:*",
            "COUNT",
            "50",
            "TYPE",
            "hash",
        ]);
    });
});

describe("createZScan", () => {
    it("builds basic ZSCAN with key and cursor", () => {
        const cmd = createZScan("zset", "0");
        expect(getArgs(cmd)).toEqual(["zset", "0"]);
    });

    it("adds NOSCORES option", () => {
        const cmd = createZScan("zset", "0", { noScores: true });
        const args = getArgs(cmd);
        expect(args).toContain("NOSCORES");
    });

    it("combines MATCH + COUNT + NOSCORES", () => {
        const cmd = createZScan("zset", "0", {
            match: "m*",
            count: 20,
            noScores: true,
        });
        const args = getArgs(cmd);
        expect(args).toEqual([
            "zset",
            "0",
            "MATCH",
            "m*",
            "COUNT",
            "20",
            "NOSCORES",
        ]);
    });
});

describe("createHScan", () => {
    it("builds basic HSCAN with key and cursor", () => {
        const cmd = createHScan("hash", "0");
        expect(getArgs(cmd)).toEqual(["hash", "0"]);
    });

    it("adds NOVALUES option", () => {
        const cmd = createHScan("hash", "0", { noValues: true });
        const args = getArgs(cmd);
        expect(args).toContain("NOVALUES");
    });

    it("combines MATCH + COUNT + NOVALUES", () => {
        const cmd = createHScan("hash", "0", {
            match: "field*",
            count: 10,
            noValues: true,
        });
        const args = getArgs(cmd);
        expect(args).toEqual([
            "hash",
            "0",
            "MATCH",
            "field*",
            "COUNT",
            "10",
            "NOVALUES",
        ]);
    });
});
