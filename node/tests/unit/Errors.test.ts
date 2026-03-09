/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import { describe, expect, it } from "@jest/globals";
import {
    ClosingError,
    ConfigurationError,
    ConnectionError,
    ExecAbortError,
    RequestError,
    TIMEOUT_ERROR,
    TimeoutError,
    ValkeyError,
} from "../../src/Errors";

describe("ValkeyError hierarchy", () => {
    describe("ValkeyError base class", () => {
        it("should use custom message when provided", () => {
            // ValkeyError is abstract, test through concrete subclass
            const err = new ClosingError("custom message");
            expect(err.message).toBe("custom message");
        });

        it("should use default message when no message provided", () => {
            const err = new ClosingError();
            expect(err.message).toBe("No error message provided");
        });

        it("should use default message when undefined is passed", () => {
            const err = new ClosingError(undefined);
            expect(err.message).toBe("No error message provided");
        });

        it("should return class name from name getter, not 'Error'", () => {
            const err = new ClosingError("test");
            expect(err.name).toBe("ClosingError");
            // Should NOT be "Error" (which is the default Error.name)
            expect(err.name).not.toBe("Error");
        });

        it("should extend Error", () => {
            const err = new ClosingError("test");
            expect(err).toBeInstanceOf(Error);
        });
    });

    describe("ClosingError", () => {
        it("should be instance of ValkeyError", () => {
            const err = new ClosingError("connection closed");
            expect(err).toBeInstanceOf(ValkeyError);
        });

        it("should NOT be instance of RequestError", () => {
            const err = new ClosingError("connection closed");
            expect(err).not.toBeInstanceOf(RequestError);
        });

        it("should have correct name", () => {
            expect(new ClosingError().name).toBe("ClosingError");
        });
    });

    describe("RequestError", () => {
        it("should be instance of ValkeyError", () => {
            const err = new RequestError("bad request");
            expect(err).toBeInstanceOf(ValkeyError);
        });

        it("should NOT be instance of ClosingError", () => {
            const err = new RequestError("bad request");
            expect(err).not.toBeInstanceOf(ClosingError);
        });

        it("should have correct name", () => {
            expect(new RequestError().name).toBe("RequestError");
        });
    });

    describe("TimeoutError", () => {
        it("should be instance of RequestError", () => {
            const err = new TimeoutError("timed out");
            expect(err).toBeInstanceOf(RequestError);
        });

        it("should be instance of ValkeyError", () => {
            const err = new TimeoutError("timed out");
            expect(err).toBeInstanceOf(ValkeyError);
        });

        it("should have correct name", () => {
            expect(new TimeoutError().name).toBe("TimeoutError");
        });
    });

    describe("ExecAbortError", () => {
        it("should be instance of RequestError", () => {
            const err = new ExecAbortError("transaction aborted");
            expect(err).toBeInstanceOf(RequestError);
        });

        it("should be instance of ValkeyError", () => {
            const err = new ExecAbortError("transaction aborted");
            expect(err).toBeInstanceOf(ValkeyError);
        });

        it("should have correct name", () => {
            expect(new ExecAbortError().name).toBe("ExecAbortError");
        });
    });

    describe("ConnectionError", () => {
        it("should be instance of RequestError", () => {
            const err = new ConnectionError("disconnected");
            expect(err).toBeInstanceOf(RequestError);
        });

        it("should be instance of ValkeyError", () => {
            const err = new ConnectionError();
            expect(err).toBeInstanceOf(ValkeyError);
        });

        it("should NOT be instance of ClosingError", () => {
            const err = new ConnectionError();
            expect(err).not.toBeInstanceOf(ClosingError);
        });

        it("should have correct name", () => {
            expect(new ConnectionError().name).toBe("ConnectionError");
        });
    });

    describe("ConfigurationError", () => {
        it("should be instance of RequestError", () => {
            const err = new ConfigurationError("bad config");
            expect(err).toBeInstanceOf(RequestError);
        });

        it("should be instance of ValkeyError", () => {
            const err = new ConfigurationError("bad config");
            expect(err).toBeInstanceOf(ValkeyError);
        });

        it("should have correct name", () => {
            expect(new ConfigurationError().name).toBe("ConfigurationError");
        });
    });

    describe("TIMEOUT_ERROR singleton", () => {
        it("should be a TimeoutError instance", () => {
            expect(TIMEOUT_ERROR).toBeInstanceOf(TimeoutError);
        });

        it("should be a RequestError instance", () => {
            expect(TIMEOUT_ERROR).toBeInstanceOf(RequestError);
        });

        it("should have the message 'Operation timed out'", () => {
            expect(TIMEOUT_ERROR.message).toBe("Operation timed out");
        });

        it("should have name 'TimeoutError'", () => {
            expect(TIMEOUT_ERROR.name).toBe("TimeoutError");
        });
    });

    describe("error catching patterns", () => {
        it("catch ValkeyError catches all glide errors", () => {
            const errors = [
                new ClosingError("a"),
                new RequestError("b"),
                new TimeoutError("c"),
                new ExecAbortError("d"),
                new ConnectionError("e"),
                new ConfigurationError("f"),
            ];

            for (const err of errors) {
                expect(err).toBeInstanceOf(ValkeyError);
            }
        });

        it("catch RequestError catches all request-level errors but not ClosingError", () => {
            expect(new TimeoutError()).toBeInstanceOf(RequestError);
            expect(new ExecAbortError()).toBeInstanceOf(RequestError);
            expect(new ConnectionError()).toBeInstanceOf(RequestError);
            expect(new ConfigurationError()).toBeInstanceOf(RequestError);
            // ClosingError is NOT a RequestError
            expect(new ClosingError()).not.toBeInstanceOf(RequestError);
        });

        it("each error type has a distinct name for error discrimination", () => {
            const names = new Set([
                new ClosingError().name,
                new RequestError().name,
                new TimeoutError().name,
                new ExecAbortError().name,
                new ConnectionError().name,
                new ConfigurationError().name,
            ]);
            // All names should be unique
            expect(names.size).toBe(6);
        });

        it("errors work correctly with try/catch instanceof pattern", () => {
            // Simulate the common pattern: catch specific errors before general ones
            const timeoutErr = new TimeoutError("op timed out");

            let caught = "";

            try {
                throw timeoutErr;
            } catch (e) {
                if (e instanceof TimeoutError) {
                    caught = "timeout";
                } else if (e instanceof RequestError) {
                    caught = "request";
                } else if (e instanceof ValkeyError) {
                    caught = "valkey";
                }
            }

            expect(caught).toBe("timeout");
        });

        it("empty string message is preserved, not replaced with default", () => {
            const err = new RequestError("");
            // Empty string is falsy but still a string - it should be preserved
            // because the constructor uses ?? (nullish coalescing), not || (logical or)
            expect(err.message).toBe("");
        });
    });
});
