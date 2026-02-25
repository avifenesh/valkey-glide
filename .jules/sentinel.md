## 2026-02-25 - [Secure Unix Domain Socket Creation]
**Vulnerability:** IPC Unix domain socket was created directly in `/tmp` (or `AppData`) with a potential window where default permissions (umask) applied before restricting them, and risk of name collision/hijacking.
**Learning:** `UnixListener::bind` creates the file. Permissions set afterwards leave a TOCTOU window. Also, clean up logic can be dangerous if it recursively deletes directories without validating the path structure.
**Prevention:** Create a directory with `0700` permissions *first*, then place the socket inside it. Validate paths before recursive deletion. Use `DirBuilder` for atomic directory creation with permissions.
