# Non-UTF8 Test Resolution

## Background

Several tests in SharedCommandTests.java are marked as `@Disabled` with the note "Test expectations outdated after non-UTF8 fixes - needs decision on correct behavior". These tests were disabled during the implementation of proper binary data support in the JNI bridge.

## Current Status (2025-09-01)

### Fixed Issues

1. **UTF-8 Validation for String Commands**
   - Added `extractAndValidateStringResponse()` utility method in BaseClient
   - Properly validates UTF-8 using CharsetDecoder with REPORT action
   - Detects invalid UTF-8 characters (0x80-0x9F range) in strings
   - Throws RuntimeException for invalid UTF-8 as expected by tests

2. **bitop Test Now Passing**
   - The bitop test in SharedCommandTests that creates invalid UTF-8 (0x9E) now correctly throws ExecutionException
   - This addresses issue https://github.com/valkey-io/valkey-glide/issues/1447

### Disabled Tests Requiring Decision

The following tests remain disabled and need architectural decisions:

1. **non_UTF8_GlideString_map** - Tests storing non-UTF8 bytes in hash maps
2. **non_UTF8_GlideString_map_with_double** - Tests non-UTF8 keys with double values
3. **non_UTF8_GlideString_nested_array** - Tests nested arrays with non-UTF8 data
4. **non_UTF8_GlideString_map_with_geospatial** - Tests geospatial data with non-UTF8 keys
5. **non_UTF8_GlideString_map_of_arrays** - Tests arrays containing non-UTF8 data

## Key Design Decisions Needed

### 1. GlideString Behavior

- **Current**: GlideString is designed to handle binary data that may not be valid UTF-8
- **Question**: Should `GlideString.toString()` throw an exception or return a placeholder message for non-UTF8 data?
- **Test Expectation**: `"Value not convertible to string: byte[] 13"`

### 2. Cross-Type Operations

- **Issue**: Tests expect exceptions when mixing String and GlideString operations on non-UTF8 data
- **Example**: `client.hget(hashKey.toString(), stringField)` should throw ExecutionException for non-UTF8 values
- **Current Fix**: Our UTF-8 validation correctly throws exceptions for String operations

### 3. Response Format Consistency

- **Cluster vs Standalone**: Different response formats need consistent handling
- **Solution Implemented**: `extractAndValidateStringResponse()` handles both modes

## Recommendations

1. **Enable bitop test validation** - Remove the TODO comment since it's now fixed
2. **Review GlideString.toString()** behavior for non-UTF8 data
3. **Update disabled tests** based on architectural decisions about binary data handling
4. **Document binary data policy** clearly for API users

## Implementation Notes

### Utility Methods Created

```java
// In BaseClient.java
protected String extractAndValidateStringResponse(Object result)
protected Long extractLongResponse(Object result)
protected CompletableFuture<Object> executeBinaryCommand(String commandType, GlideString... args)
protected CompletableFuture<Object> executeBinaryCommandMixed(String commandType, Object... args)
```

These utilities ensure consistent handling across all commands and reduce code duplication.
