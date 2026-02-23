## 2025-02-18 - [FFI Null Pointer Checks]
**Vulnerability:** Missing null pointer checks in FFI functions (`store_script`, `create_client`, etc.) in `ffi/src/lib.rs`.
**Learning:** `extern "C"` functions receive raw pointers which can be null. `std::slice::from_raw_parts` and `CStr::from_ptr` cause Undefined Behavior if passed null pointers.
**Prevention:** Always check `is_null()` on raw pointers at the beginning of `extern "C"` functions and return error or safe default.
