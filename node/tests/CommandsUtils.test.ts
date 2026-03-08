/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import { describe, expect, it } from "@jest/globals";
import { parseInfoResponse, RequestError } from "../build-ts";

describe("parseInfoResponse", () => {
    it("should parse standard key:value lines", () => {
        const response = "used_memory:1024\nused_memory_human:1K";
        const result = parseInfoResponse(response);
        expect(result["used_memory"]).toBe("1024");
        expect(result["used_memory_human"]).toBe("1K");
    });

    it("should skip comment lines starting with #", () => {
        const response =
            "# Server\nredis_version:7.0.0\n# Clients\nconnected_clients:1";
        const result = parseInfoResponse(response);
        expect(result["redis_version"]).toBe("7.0.0");
        expect(result["connected_clients"]).toBe("1");
        expect(result["# Server"]).toBeUndefined();
    });

    it("should skip empty lines", () => {
        const response = "key1:value1\n\n\nkey2:value2";
        const result = parseInfoResponse(response);
        expect(result["key1"]).toBe("value1");
        expect(result["key2"]).toBe("value2");
        expect(result[""]).toBeUndefined();
    });

    it("should skip lines without colons", () => {
        const response = "key1:value1\nmalformed_line\nkey2:value2";
        const result = parseInfoResponse(response);
        expect(result["key1"]).toBe("value1");
        expect(result["key2"]).toBe("value2");
        expect(result["malformed_line"]).toBeUndefined();
    });

    it("should preserve colons in values", () => {
        const response =
            "os:Linux 5.4.0:x86_64\nconfig_file:/etc/redis/redis.conf";
        const result = parseInfoResponse(response);
        expect(result["os"]).toBe("Linux 5.4.0:x86_64");
        expect(result["config_file"]).toBe("/etc/redis/redis.conf");
    });

    it("should return empty record for empty string", () => {
        const result = parseInfoResponse("");
        expect(Object.keys(result).length).toBe(0);
    });

    it("should handle response with only comments and empty lines", () => {
        const response = "# Server\n# Clients\n\n";
        const result = parseInfoResponse(response);
        expect(Object.keys(result).length).toBe(0);
    });
});

describe("RequestError usage in command validation", () => {
    it("RequestError should be an instance of Error", () => {
        const error = new RequestError("test message");
        expect(error).toBeInstanceOf(Error);
        expect(error.message).toBe("test message");
    });
});
