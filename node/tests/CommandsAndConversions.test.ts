/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

/**
 * Unit tests for pure-logic functions in Commands.ts and BaseClient.ts.
 * These tests do NOT require a running Valkey/Redis server.
 */
import { describe, expect, it } from "@jest/globals";
import {
    BitmapIndexType,
    ClosingError,
    ConfigurationError,
    ConnectionError,
    convertFieldsAndValuesToHashDataType,
    convertGlideRecord,
    convertGlideRecordToRecord,
    convertKeysAndEntries,
    convertRecordToGlideRecord,
    ExecAbortError,
    GeoUnit,
    GlideRecord,
    isGlideRecord,
    parseInfoResponse,
    RequestError,
    SortOrder,
    TimeoutError,
    TimeUnit,
    ValkeyError,
} from "../build-ts";

// ---------------------------------------------------------------------------
// parseInfoResponse
// ---------------------------------------------------------------------------
describe("parseInfoResponse", () => {
    it("parses standard key:value lines", () => {
        const raw = "redis_version:7.0.0\nredis_mode:standalone\n";
        const result = parseInfoResponse(raw);
        expect(result["redis_version"]).toBe("7.0.0");
        expect(result["redis_mode"]).toBe("standalone");
    });

    it("skips comment lines starting with #", () => {
        const raw =
            "# Server\nredis_version:7.0.0\n# Clients\nconnected_clients:1\n";
        const result = parseInfoResponse(raw);
        expect(result["redis_version"]).toBe("7.0.0");
        expect(result["connected_clients"]).toBe("1");
        // Comments should NOT appear as keys
        expect(result["# Server"]).toBeUndefined();
        expect(result["# Clients"]).toBeUndefined();
    });

    it("handles empty response", () => {
        const result = parseInfoResponse("");
        // Empty string splits into [""], trim produces "", split(":") produces ["",""],
        // so we get key="" with value=""
        expect(Object.keys(result).length).toBeLessThanOrEqual(1);
    });

    it("handles lines with colons in the value", () => {
        // Values like "tcp://localhost:6379" have colons.
        // Current implementation: destructuring [key, value] = line.split(":")
        // gives key="bind", value="tcp" (value truncated at second colon).
        const raw = "bind:tcp://localhost:6379\n";
        const result = parseInfoResponse(raw);
        expect(result["bind"]).toBe("tcp");
    });

    it("handles \\r\\n line endings", () => {
        const raw = "key1:val1\r\nkey2:val2\r\n";
        const result = parseInfoResponse(raw);
        expect(result["key1"]).toBe("val1");
        // With \r\n, the trim() handles the trailing \r
        expect(result["key2"]).toBe("val2");
    });

    it("handles lines without colons", () => {
        const raw = "no_colon_here\nkey:val\n";
        const result = parseInfoResponse(raw);
        expect(result["no_colon_here"]).toBeUndefined();
        expect(result["key"]).toBe("val");
    });

    it("handles numeric values", () => {
        const raw =
            "used_memory:1048576\nused_cpu_sys:1.234567\nkeyspace_hits:0\n";
        const result = parseInfoResponse(raw);
        expect(result["used_memory"]).toBe("1048576");
        expect(result["used_cpu_sys"]).toBe("1.234567");
        expect(result["keyspace_hits"]).toBe("0");
    });

    it("handles empty value after colon", () => {
        const raw = "empty_key:\n";
        const result = parseInfoResponse(raw);
        expect(result["empty_key"]).toBe("");
    });

    it("handles real-world INFO output structure", () => {
        const raw = [
            "# Server",
            "redis_version:7.2.4",
            "redis_git_sha1:00000000",
            "redis_git_dirty:0",
            "redis_build_id:abc123",
            "",
            "# Clients",
            "connected_clients:10",
            "blocked_clients:0",
            "",
        ].join("\n");
        const result = parseInfoResponse(raw);
        expect(result["redis_version"]).toBe("7.2.4");
        expect(result["redis_git_dirty"]).toBe("0");
        expect(result["connected_clients"]).toBe("10");
        expect(result["blocked_clients"]).toBe("0");
    });
});

// ---------------------------------------------------------------------------
// isGlideRecord
// ---------------------------------------------------------------------------
describe("isGlideRecord", () => {
    it("returns true for valid GlideRecord", () => {
        expect(isGlideRecord([{ key: "a", value: 1 }])).toBe(true);
    });

    it("returns true for multi-element GlideRecord", () => {
        expect(
            isGlideRecord([
                { key: "a", value: 1 },
                { key: "b", value: 2 },
            ]),
        ).toBe(true);
    });

    it("returns false for empty array", () => {
        expect(isGlideRecord([])).toBe(false);
    });

    it("returns false for undefined", () => {
        expect(isGlideRecord(undefined)).toBe(false);
    });

    it("returns false for null", () => {
        expect(isGlideRecord(null)).toBe(false);
    });

    it("returns false for non-array objects", () => {
        expect(isGlideRecord({ key: "a", value: 1 })).toBe(false);
    });

    it("returns false for arrays of primitives", () => {
        expect(isGlideRecord([1, 2, 3])).toBe(false);
        expect(isGlideRecord(["a", "b"])).toBe(false);
    });

    it("returns false for arrays of objects without key/value", () => {
        expect(isGlideRecord([{ name: "a", data: 1 }])).toBe(false);
    });

    it("returns false for arrays with only 'key' (no 'value')", () => {
        expect(isGlideRecord([{ key: "a" }])).toBe(false);
    });

    it("returns false for arrays with only 'value' (no 'key')", () => {
        expect(isGlideRecord([{ value: 1 }])).toBe(false);
    });

    it("returns true even with extra properties (duck typing)", () => {
        expect(isGlideRecord([{ key: "a", value: 1, extra: true }])).toBe(true);
    });

    it("returns false for string", () => {
        expect(isGlideRecord("hello")).toBe(false);
    });

    it("returns false for number", () => {
        expect(isGlideRecord(42)).toBe(false);
    });

    it("checks only the first element", () => {
        // Second element is malformed but isGlideRecord only checks first
        const malformed: GlideRecord<unknown> = [
            { key: "a", value: 1 },
            { key: "b", value: undefined },
        ];
        // Add a non-conforming object to test duck typing on first element
        (malformed as unknown[]).push({ bad: true });
        expect(isGlideRecord(malformed)).toBe(true);
    });
});

// ---------------------------------------------------------------------------
// convertGlideRecord
// ---------------------------------------------------------------------------
describe("convertGlideRecord", () => {
    it("passes through an existing GlideRecord unchanged", () => {
        const input: GlideRecord<string> = [{ key: "a", value: "1" }];
        const result = convertGlideRecord(input);
        expect(result).toBe(input); // same reference
    });

    it("converts a plain Record to GlideRecord", () => {
        const input = { foo: "bar", baz: "qux" };
        const result = convertGlideRecord(input);
        expect(result).toEqual([
            { key: "foo", value: "bar" },
            { key: "baz", value: "qux" },
        ]);
    });

    it("handles empty Record", () => {
        const result = convertGlideRecord({});
        expect(result).toEqual([]);
    });

    it("handles empty GlideRecord array", () => {
        const input: GlideRecord<string> = [];
        const result = convertGlideRecord(input);
        expect(result).toEqual([]);
        expect(result).toBe(input);
    });

    it("handles Record with Buffer values", () => {
        const buf = Buffer.from("hello");
        const result = convertGlideRecord({ mykey: buf });
        expect(result).toEqual([{ key: "mykey", value: buf }]);
    });
});

// ---------------------------------------------------------------------------
// convertGlideRecordToRecord
// ---------------------------------------------------------------------------
describe("convertGlideRecordToRecord", () => {
    it("converts flat GlideRecord to Record", () => {
        const input: GlideRecord<string> = [
            { key: "a", value: "1" },
            { key: "b", value: "2" },
        ];
        expect(convertGlideRecordToRecord(input)).toEqual({ a: "1", b: "2" });
    });

    it("handles empty GlideRecord", () => {
        expect(convertGlideRecordToRecord([])).toEqual({});
    });

    it("recursively converts nested GlideRecords", () => {
        const input: GlideRecord<GlideRecord<string>> = [
            {
                key: "outer",
                value: [{ key: "inner", value: "deep" }],
            },
        ];
        const result = convertGlideRecordToRecord(input);
        expect(result).toEqual({ outer: { inner: "deep" } });
    });

    it("recursively converts arrays of GlideRecords", () => {
        const input: GlideRecord<GlideRecord<string>[]> = [
            {
                key: "groups",
                value: [
                    [{ key: "name", value: "g1" }],
                    [{ key: "name", value: "g2" }],
                ],
            },
        ];
        const result = convertGlideRecordToRecord(input);
        expect(result).toEqual({
            groups: [{ name: "g1" }, { name: "g2" }],
        });
    });

    it("preserves non-GlideRecord array values", () => {
        const input: GlideRecord<string[]> = [
            { key: "tags", value: ["tag1", "tag2", "tag3"] },
        ];
        const result = convertGlideRecordToRecord(input);
        expect(result).toEqual({ tags: ["tag1", "tag2", "tag3"] });
    });

    it("preserves null values", () => {
        const input: GlideRecord<null> = [{ key: "nothing", value: null }];
        const result = convertGlideRecordToRecord(input);
        expect(result).toEqual({ nothing: null });
    });

    it("preserves number values", () => {
        const input: GlideRecord<number> = [
            { key: "count", value: 42 },
            { key: "score", value: -1.5 },
        ];
        const result = convertGlideRecordToRecord(input);
        expect(result).toEqual({ count: 42, score: -1.5 });
    });

    it("handles duplicate keys (last one wins)", () => {
        const input: GlideRecord<string> = [
            { key: "dup", value: "first" },
            { key: "dup", value: "second" },
        ];
        const result = convertGlideRecordToRecord(input);
        expect(result["dup"]).toBe("second");
    });

    it("handles Buffer keys by coercing to string", () => {
        const input = [
            { key: Buffer.from("bufkey"), value: "val" },
        ] as GlideRecord<string>;
        const result = convertGlideRecordToRecord(input);
        // Buffer.toString() coercion depends on how JS handles it as object key
        expect(Object.keys(result).length).toBe(1);
    });
});

// ---------------------------------------------------------------------------
// convertRecordToGlideRecord
// ---------------------------------------------------------------------------
describe("convertRecordToGlideRecord", () => {
    it("converts Record to GlideRecord", () => {
        const result = convertRecordToGlideRecord({ x: 1, y: 2 });
        expect(result).toEqual([
            { key: "x", value: 1 },
            { key: "y", value: 2 },
        ]);
    });

    it("handles empty Record", () => {
        expect(convertRecordToGlideRecord({})).toEqual([]);
    });

    it("preserves complex values", () => {
        const nested = { a: [1, 2, 3] };
        const result = convertRecordToGlideRecord(nested);
        expect(result).toEqual([{ key: "a", value: [1, 2, 3] }]);
    });

    it("roundtrips with convertGlideRecordToRecord for flat data", () => {
        const original = { alpha: "A", beta: "B", gamma: "C" };
        const glideRec = convertRecordToGlideRecord(original);
        const backToRecord = convertGlideRecordToRecord(glideRec);
        expect(backToRecord).toEqual(original);
    });
});

// ---------------------------------------------------------------------------
// convertFieldsAndValuesToHashDataType
// ---------------------------------------------------------------------------
describe("convertFieldsAndValuesToHashDataType", () => {
    it("converts Record to HashDataType", () => {
        const result = convertFieldsAndValuesToHashDataType({
            field1: "val1",
            field2: "val2",
        });
        expect(result).toEqual([
            { field: "field1", value: "val1" },
            { field: "field2", value: "val2" },
        ]);
    });

    it("passes through existing HashDataType", () => {
        const input = [
            { field: "f1", value: "v1" },
            { field: "f2", value: "v2" },
        ];
        const result = convertFieldsAndValuesToHashDataType(input);
        expect(result).toBe(input); // same reference
    });

    it("handles empty Record", () => {
        expect(convertFieldsAndValuesToHashDataType({})).toEqual([]);
    });

    it("handles empty HashDataType array", () => {
        const input: { field: string; value: string }[] = [];
        const result = convertFieldsAndValuesToHashDataType(input);
        expect(result).toBe(input);
    });

    it("handles Buffer values in Record", () => {
        const buf = Buffer.from("binary");
        const result = convertFieldsAndValuesToHashDataType({ myfield: buf });
        expect(result).toEqual([{ field: "myfield", value: buf }]);
    });
});

// ---------------------------------------------------------------------------
// convertKeysAndEntries
// ---------------------------------------------------------------------------
describe("convertKeysAndEntries", () => {
    it("converts Record to GlideRecord", () => {
        const result = convertKeysAndEntries({
            "stream-1": "0-0",
            "stream-2": "$",
        });
        expect(result).toEqual([
            { key: "stream-1", value: "0-0" },
            { key: "stream-2", value: "$" },
        ]);
    });

    it("passes through existing GlideRecord", () => {
        const input: GlideRecord<string> = [{ key: "stream-1", value: "0-0" }];
        const result = convertKeysAndEntries(input);
        expect(result).toBe(input);
    });

    it("handles empty Record", () => {
        expect(convertKeysAndEntries({})).toEqual([]);
    });

    it("handles empty GlideRecord array", () => {
        const input: GlideRecord<string> = [];
        const result = convertKeysAndEntries(input);
        expect(result).toBe(input);
    });
});

// ---------------------------------------------------------------------------
// Enum values - verify correctness
// ---------------------------------------------------------------------------
describe("Enum values", () => {
    describe("GeoUnit", () => {
        it("has correct wire values", () => {
            expect(GeoUnit.METERS).toBe("m");
            expect(GeoUnit.KILOMETERS).toBe("km");
            expect(GeoUnit.MILES).toBe("mi");
            expect(GeoUnit.FEET).toBe("ft");
        });
    });

    describe("BitmapIndexType", () => {
        it("has correct wire values", () => {
            expect(BitmapIndexType.BYTE).toBe("BYTE");
            expect(BitmapIndexType.BIT).toBe("BIT");
        });
    });

    describe("SortOrder", () => {
        it("has correct wire values", () => {
            expect(SortOrder.ASC).toBe("ASC");
            expect(SortOrder.DESC).toBe("DESC");
        });
    });

    describe("TimeUnit", () => {
        it("maps to correct Valkey commands", () => {
            expect(TimeUnit.Seconds).toBe("EX");
            expect(TimeUnit.Milliseconds).toBe("PX");
            expect(TimeUnit.UnixSeconds).toBe("EXAT");
            expect(TimeUnit.UnixMilliseconds).toBe("PXAT");
        });
    });
});

// ---------------------------------------------------------------------------
// Error class hierarchy
// ---------------------------------------------------------------------------
describe("Error classes", () => {
    it("RequestError extends ValkeyError", () => {
        const err = new RequestError("test");
        expect(err).toBeInstanceOf(ValkeyError);
        expect(err).toBeInstanceOf(RequestError);
        expect(err.message).toBe("test");
        expect(err.name).toBe("RequestError");
    });

    it("TimeoutError extends RequestError", () => {
        const err = new TimeoutError("timed out");
        expect(err).toBeInstanceOf(RequestError);
        expect(err).toBeInstanceOf(ValkeyError);
        expect(err.name).toBe("TimeoutError");
    });

    it("ExecAbortError extends RequestError", () => {
        const err = new ExecAbortError("aborted");
        expect(err).toBeInstanceOf(RequestError);
        expect(err.name).toBe("ExecAbortError");
    });

    it("ConnectionError extends RequestError", () => {
        const err = new ConnectionError("disconnected");
        expect(err).toBeInstanceOf(RequestError);
        expect(err.name).toBe("ConnectionError");
    });

    it("ConfigurationError extends RequestError", () => {
        const err = new ConfigurationError("bad config");
        expect(err).toBeInstanceOf(RequestError);
        expect(err.name).toBe("ConfigurationError");
    });

    it("ClosingError extends ValkeyError but not RequestError", () => {
        const err = new ClosingError("closing");
        expect(err).toBeInstanceOf(ValkeyError);
        expect(err).not.toBeInstanceOf(RequestError);
        expect(err.name).toBe("ClosingError");
    });

    it("ValkeyError provides default message when none given", () => {
        const err = new RequestError();
        expect(err.message).toBe("No error message provided");
    });

    it("error classes are distinguishable in catch blocks", () => {
        const errors = [
            new TimeoutError("t"),
            new ConnectionError("c"),
            new ExecAbortError("e"),
            new ConfigurationError("cfg"),
        ];

        for (const err of errors) {
            expect(err).toBeInstanceOf(RequestError);
        }

        expect(errors[0]).toBeInstanceOf(TimeoutError);
        expect(errors[0]).not.toBeInstanceOf(ConnectionError);
        expect(errors[1]).toBeInstanceOf(ConnectionError);
        expect(errors[1]).not.toBeInstanceOf(TimeoutError);
    });
});

// ---------------------------------------------------------------------------
// Conversion edge cases - roundtrip integrity
// ---------------------------------------------------------------------------
describe("Conversion roundtrip integrity", () => {
    it("convertGlideRecord -> convertGlideRecordToRecord roundtrip for Record input", () => {
        const original = { a: "1", b: "2", c: "3" };
        const asGlide = convertGlideRecord(original);
        const backToRecord = convertGlideRecordToRecord(asGlide);
        expect(backToRecord).toEqual(original);
    });

    it("convertRecordToGlideRecord produces valid GlideRecord", () => {
        const record = { key1: "val1" };
        const glideRec = convertRecordToGlideRecord(record);
        expect(isGlideRecord(glideRec)).toBe(true);
    });

    it("empty conversions are consistent", () => {
        expect(convertGlideRecordToRecord([])).toEqual({});
        expect(convertRecordToGlideRecord({})).toEqual([]);
        expect(isGlideRecord([])).toBe(false);
        expect(isGlideRecord(convertRecordToGlideRecord({}))).toBe(false);
    });

    it("handles deeply nested GlideRecords (3 levels)", () => {
        const input: GlideRecord<GlideRecord<GlideRecord<string>>> = [
            {
                key: "l1",
                value: [
                    {
                        key: "l2",
                        value: [{ key: "l3", value: "leaf" }],
                    },
                ],
            },
        ];
        const result = convertGlideRecordToRecord(input);
        expect(result).toEqual({ l1: { l2: { l3: "leaf" } } });
    });

    it("preserves mixed value types in GlideRecord", () => {
        const input: GlideRecord<string | number | boolean | null | number[]> =
            [
                { key: "str", value: "hello" },
                { key: "num", value: 42 },
                { key: "bool", value: true },
                { key: "nil", value: null },
                { key: "arr", value: [1, 2, 3] },
            ];
        const result = convertGlideRecordToRecord(input);
        expect(result).toEqual({
            str: "hello",
            num: 42,
            bool: true,
            nil: null,
            arr: [1, 2, 3],
        });
    });
});
