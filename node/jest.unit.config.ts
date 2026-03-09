/**
 * Jest configuration for pure unit tests that do not require
 * the native Rust build or a running Valkey/Redis server.
 *
 * Run with: npx jest --config jest.unit.config.ts
 */
import type { Config } from "jest";

const config: Config = {
    preset: "ts-jest",
    transform: {
        "^.+\\.(ts|tsx)$": [
            "ts-jest",
            {
                // Skip type-checking for unit tests since mocked modules
                // lack type declarations (native Rust build not required)
                diagnostics: false,
            },
        ],
    },
    transformIgnorePatterns: [
        "node_modules/",
        "\\.(js|jsx)$",
        "<rootDir>/build-ts/",
    ],
    testEnvironment: "node",
    testRegex: "/tests/unit/.*\\.(test|spec)?\\.(ts|tsx)$",
    moduleFileExtensions: ["ts", "js", "json", "node"],
    modulePathIgnorePatterns: ["rust-client/", "build-js/"],
    // No setupFilesAfterEnv - unit tests don't need server setup or Logger
};

export default config;
