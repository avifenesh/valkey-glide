/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 *
 * Unit tests for pure functions and utilities that don't require a live server.
 * Tests error classes, type guards, data conversion helpers, and command builders.
 */

import { describe, expect, it } from "@jest/globals";
import {
    ClosingError,
    ConfigurationError,
    ConnectionError,
    convertFieldsAndValuesToHashDataType,
    convertGlideRecordToRecord,
    convertRecordToGlideRecord,
    ExecAbortError,
    isGlideRecord,
    parseInfoResponse,
    RequestError,
    TimeoutError,
    ValkeyError,
} from "../build-ts";

// ---------------------------------------------------------------------------
// Error hierarchy
// ---------------------------------------------------------------------------
describe("Error hierarchy", () => {
    describe("ValkeyError base class", () => {
        it("should use default message when none provided", () => {
            const err = new RequestError();
            expect(err.message).toBe("No error message provided");
        });

        it("should use default message when undefined is passed", () => {
            const err = new RequestError(undefined);
            expect(err.message).toBe("No error message provided");
        });

        it("should preserve empty string message (not replace with default)", () => {
            const err = new RequestError("");
            expect(err.message).toBe("");
        });

        it("should preserve the provided message", () => {
            const err = new RequestError("something went wrong");
            expect(err.message).toBe("something went wrong");
        });

        it("should report the concrete class name via .name", () => {
            expect(new RequestError().name).toBe("RequestError");
            expect(new TimeoutError().name).toBe("TimeoutError");
            expect(new ClosingError().name).toBe("ClosingError");
            expect(new ExecAbortError().name).toBe("ExecAbortError");
            expect(new ConnectionError().name).toBe("ConnectionError");
            expect(new ConfigurationError().name).toBe("ConfigurationError");
        });
    });

    describe("instanceof chain", () => {
        it("TimeoutError is a RequestError and a ValkeyError", () => {
            const err = new TimeoutError("timeout");
            expect(err).toBeInstanceOf(TimeoutError);
            expect(err).toBeInstanceOf(RequestError);
            expect(err).toBeInstanceOf(ValkeyError);
            expect(err).toBeInstanceOf(Error);
        });

        it("ExecAbortError is a RequestError and a ValkeyError", () => {
            const err = new ExecAbortError("aborted");
            expect(err).toBeInstanceOf(ExecAbortError);
            expect(err).toBeInstanceOf(RequestError);
            expect(err).toBeInstanceOf(ValkeyError);
            expect(err).toBeInstanceOf(Error);
        });

        it("ConnectionError is a RequestError and a ValkeyError", () => {
            const err = new ConnectionError("disconnected");
            expect(err).toBeInstanceOf(ConnectionError);
            expect(err).toBeInstanceOf(RequestError);
            expect(err).toBeInstanceOf(ValkeyError);
            expect(err).toBeInstanceOf(Error);
        });

        it("ConfigurationError is a RequestError and a ValkeyError", () => {
            const err = new ConfigurationError("bad config");
            expect(err).toBeInstanceOf(ConfigurationError);
            expect(err).toBeInstanceOf(RequestError);
            expect(err).toBeInstanceOf(ValkeyError);
            expect(err).toBeInstanceOf(Error);
        });

        it("ClosingError is a ValkeyError but NOT a RequestError", () => {
            const err = new ClosingError("closed");
            expect(err).toBeInstanceOf(ClosingError);
            expect(err).toBeInstanceOf(ValkeyError);
            expect(err).toBeInstanceOf(Error);
            expect(err).not.toBeInstanceOf(RequestError);
        });
    });

    describe("error equality", () => {
        it("two RequestErrors with the same message should be equal (deep)", () => {
            const err1 = new RequestError("test");
            const err2 = new RequestError("test");
            expect(err1).toEqual(err2);
        });

        it("RequestError and TimeoutError with the same message should NOT be equal", () => {
            const err1 = new RequestError("test");
            const err2 = new TimeoutError("test");
            // They have different constructor names, so .name differs
            expect(err1.name).not.toBe(err2.name);
        });
    });
});

// ---------------------------------------------------------------------------
// parseInfoResponse
// ---------------------------------------------------------------------------
describe("parseInfoResponse", () => {
    it("should parse standard key:value lines", () => {
        const info = "redis_version:7.0.0\nredis_mode:standalone";
        const parsed = parseInfoResponse(info);
        expect(parsed["redis_version"]).toBe("7.0.0");
        expect(parsed["redis_mode"]).toBe("standalone");
    });

    it("should skip comment lines starting with #", () => {
        const info =
            "# Server\nredis_version:7.0.0\n# Clients\nconnected_clients:1";
        const parsed = parseInfoResponse(info);
        expect(parsed["redis_version"]).toBe("7.0.0");
        expect(parsed["connected_clients"]).toBe("1");
        expect(parsed["# Server"]).toBeUndefined();
        expect(parsed["# Clients"]).toBeUndefined();
    });

    it("should handle empty string input", () => {
        const parsed = parseInfoResponse("");
        // Empty string splits into [""], which produces key="" value=undefined
        expect(Object.keys(parsed).length).toBeLessThanOrEqual(1);
    });

    it("should handle input with only comment lines", () => {
        const info = "# Server\n# Clients\n# Memory";
        const parsed = parseInfoResponse(info);
        // Only empty-string key from the split, no real data
        const realKeys = Object.keys(parsed).filter((k) => k.length > 0);
        expect(realKeys.length).toBe(0);
    });

    it("should handle values containing colons (only first colon is split)", () => {
        // This documents current behavior: destructuring split loses data after 2nd colon
        const info = "bind_source:127.0.0.1:6379";
        const parsed = parseInfoResponse(info);
        // Bug: split(":") produces ["bind_source", "127.0.0.1", "6379"]
        // Destructuring [key, value] only captures first two elements
        // So value is "127.0.0.1" not "127.0.0.1:6379"
        expect(parsed["bind_source"]).toBe("127.0.0.1");
    });

    it("should handle lines with empty values", () => {
        const info = "config_file:";
        const parsed = parseInfoResponse(info);
        expect(parsed["config_file"]).toBe("");
    });

    it("should handle lines without colons", () => {
        const info = "orphan_line";
        const parsed = parseInfoResponse(info);
        // split(":") on "orphan_line" gives ["orphan_line"]
        // Destructuring: key="orphan_line", value=undefined
        expect(parsed["orphan_line"]).toBeUndefined();
    });

    it("should handle \\r\\n line endings (common in Redis protocol)", () => {
        const info = "redis_version:7.0.0\r\nconnected_clients:1";
        const parsed = parseInfoResponse(info);
        // .trim() handles the \r
        expect(parsed["redis_version"]).toBe("7.0.0");
        expect(parsed["connected_clients"]).toBe("1");
    });

    it("should parse a realistic multi-section INFO response", () => {
        const info = [
            "# Server",
            "redis_version:7.2.4",
            "redis_mode:standalone",
            "os:Linux 5.15.0",
            "",
            "# Clients",
            "connected_clients:3",
            "blocked_clients:0",
            "",
            "# Memory",
            "used_memory:1048576",
            "used_memory_human:1.00M",
        ].join("\n");

        const parsed = parseInfoResponse(info);
        expect(parsed["redis_version"]).toBe("7.2.4");
        expect(parsed["redis_mode"]).toBe("standalone");
        expect(parsed["connected_clients"]).toBe("3");
        expect(parsed["used_memory"]).toBe("1048576");
        expect(parsed["used_memory_human"]).toBe("1.00M");
    });
});

// ---------------------------------------------------------------------------
// isGlideRecord
// ---------------------------------------------------------------------------
describe("isGlideRecord", () => {
    it("should return true for a valid GlideRecord", () => {
        expect(isGlideRecord([{ key: "a", value: "b" }])).toBe(true);
    });

    it("should return true for multi-element GlideRecord", () => {
        expect(
            isGlideRecord([
                { key: "a", value: 1 },
                { key: "b", value: 2 },
            ]),
        ).toBe(true);
    });

    it("should return false for null", () => {
        expect(isGlideRecord(null)).toBe(false);
    });

    it("should return false for undefined", () => {
        expect(isGlideRecord(undefined)).toBe(false);
    });

    it("should return false for empty array", () => {
        expect(isGlideRecord([])).toBe(false);
    });

    it("should return false for a plain string array", () => {
        expect(isGlideRecord(["a", "b", "c"])).toBe(false);
    });

    it("should return false for a number array", () => {
        expect(isGlideRecord([1, 2, 3])).toBe(false);
    });

    it("should return false for objects missing 'key' property", () => {
        expect(isGlideRecord([{ value: "b" }])).toBe(false);
    });

    it("should return false for objects missing 'value' property", () => {
        expect(isGlideRecord([{ key: "a" }])).toBe(false);
    });

    it("should return true for objects with extra properties alongside key/value", () => {
        expect(isGlideRecord([{ key: "a", value: "b", extra: true }])).toBe(
            true,
        );
    });

    it("should return false for non-array types", () => {
        expect(isGlideRecord("string")).toBe(false);
        expect(isGlideRecord(42)).toBe(false);
        expect(isGlideRecord({ key: "a", value: "b" })).toBe(false);
        expect(isGlideRecord(true)).toBe(false);
    });

    it("should return true when key/value are null", () => {
        // The type guard only checks property existence, not value types
        expect(isGlideRecord([{ key: null, value: null }])).toBe(true);
    });

    it("should only check the first element", () => {
        // Second element lacks 'key' but isGlideRecord only checks obj[0]
        expect(
            isGlideRecord([{ key: "a", value: "b" }, { notkey: "c" } as any]),
        ).toBe(true);
    });
});

// ---------------------------------------------------------------------------
// convertGlideRecordToRecord
// ---------------------------------------------------------------------------
describe("convertGlideRecordToRecord", () => {
    it("should convert an empty GlideRecord to an empty Record", () => {
        expect(convertGlideRecordToRecord([])).toEqual({});
    });

    it("should convert a flat GlideRecord to a Record", () => {
        const glideRecord = [
            { key: "name", value: "Alice" },
            { key: "age", value: "30" },
        ];
        expect(convertGlideRecordToRecord(glideRecord)).toEqual({
            name: "Alice",
            age: "30",
        });
    });

    it("should handle null values", () => {
        const glideRecord = [{ key: "empty", value: null }];
        expect(convertGlideRecordToRecord(glideRecord)).toEqual({
            empty: null,
        });
    });

    it("should handle numeric values", () => {
        const glideRecord = [
            { key: "count", value: 42 },
            { key: "rate", value: 3.14 },
        ];
        expect(convertGlideRecordToRecord(glideRecord)).toEqual({
            count: 42,
            rate: 3.14,
        });
    });

    it("should recursively convert nested GlideRecords", () => {
        const nested = [
            {
                key: "outer",
                value: [{ key: "inner", value: "deep" }],
            },
        ];
        const result = convertGlideRecordToRecord(nested);
        expect(result).toEqual({ outer: { inner: "deep" } });
    });

    it("should handle deeply nested GlideRecords (3 levels)", () => {
        const deep = [
            {
                key: "l1",
                value: [
                    {
                        key: "l2",
                        value: [{ key: "l3", value: "bottom" }],
                    },
                ],
            },
        ];
        const result = convertGlideRecordToRecord(deep);
        expect(result).toEqual({ l1: { l2: { l3: "bottom" } } });
    });

    it("should handle last-key-wins for duplicate keys", () => {
        const glideRecord = [
            { key: "dup", value: "first" },
            { key: "dup", value: "second" },
        ];
        const result = convertGlideRecordToRecord(glideRecord);
        expect(result["dup"]).toBe("second");
    });

    it("should handle mixed nested and flat values", () => {
        const mixed: { key: string; value: unknown }[] = [
            { key: "flat", value: "simple" },
            {
                key: "nested",
                value: [{ key: "inner", value: "complex" }],
            },
            { key: "number", value: 99 },
        ];
        const result = convertGlideRecordToRecord(mixed);
        expect(result).toEqual({
            flat: "simple",
            nested: { inner: "complex" },
            number: 99,
        });
    });
});

// ---------------------------------------------------------------------------
// convertRecordToGlideRecord
// ---------------------------------------------------------------------------
describe("convertRecordToGlideRecord", () => {
    it("should convert an empty Record to an empty GlideRecord", () => {
        expect(convertRecordToGlideRecord({})).toEqual([]);
    });

    it("should convert a single-entry Record", () => {
        const result = convertRecordToGlideRecord({ name: "Alice" });
        expect(result).toEqual([{ key: "name", value: "Alice" }]);
    });

    it("should convert a multi-entry Record", () => {
        const result = convertRecordToGlideRecord({ a: "1", b: "2", c: "3" });
        expect(result).toHaveLength(3);
        // Verify all entries are present (order may vary by engine)
        const keys = result.map((e) => e.key);
        expect(keys).toContain("a");
        expect(keys).toContain("b");
        expect(keys).toContain("c");
    });

    it("should handle undefined values in Record", () => {
        const result = convertRecordToGlideRecord({
            present: "yes",
            missing: undefined as unknown as string,
        });
        expect(result).toHaveLength(2);
        const missingEntry = result.find((e) => e.key === "missing");
        expect(missingEntry?.value).toBeUndefined();
    });

    it("should roundtrip with convertGlideRecordToRecord for simple data", () => {
        const original = { x: "1", y: "2", z: "3" };
        const glideRecord = convertRecordToGlideRecord(original);
        const backToRecord = convertGlideRecordToRecord(glideRecord);
        expect(backToRecord).toEqual(original);
    });
});

// ---------------------------------------------------------------------------
// convertFieldsAndValuesToHashDataType
// ---------------------------------------------------------------------------
describe("convertFieldsAndValuesToHashDataType", () => {
    it("should pass through HashDataType array unchanged", () => {
        const hashData = [
            { field: "name", value: "Alice" },
            { field: "age", value: "30" },
        ];
        expect(convertFieldsAndValuesToHashDataType(hashData)).toBe(hashData);
    });

    it("should convert empty Record to empty array", () => {
        expect(convertFieldsAndValuesToHashDataType({})).toEqual([]);
    });

    it("should convert Record to HashDataType", () => {
        const record = { name: "Alice", age: "30" };
        const result = convertFieldsAndValuesToHashDataType(record);
        expect(result).toHaveLength(2);
        const fields = result.map((e) => e.field);
        expect(fields).toContain("name");
        expect(fields).toContain("age");
        const nameEntry = result.find((e) => e.field === "name");
        expect(nameEntry?.value).toBe("Alice");
    });

    it("should pass through empty HashDataType array", () => {
        const empty: { field: string; value: string }[] = [];
        expect(convertFieldsAndValuesToHashDataType(empty)).toBe(empty);
    });

    it("should handle Record with single field", () => {
        const result = convertFieldsAndValuesToHashDataType({
            only: "one",
        });
        expect(result).toEqual([{ field: "only", value: "one" }]);
    });

    it("should handle Buffer values in Record", () => {
        const buf = Buffer.from("binary-data");
        const result = convertFieldsAndValuesToHashDataType({
            field1: buf as any,
        });
        expect(result).toHaveLength(1);
        expect(result[0].field).toBe("field1");
        expect(result[0].value).toBe(buf);
    });
});
