# Valkey GLIDE - Comprehensive Architecture & Developer Guide

> **Purpose**: A deep-reference guide for developers and AI agents working on the Valkey GLIDE codebase. Covers architecture decisions, design patterns, command flow, cross-language conventions, and operational knowledge.
>
> **Scope**: glide-core (Rust), FFI layer, and wrappers for Python (async/sync), Java (JNI + Jedis compat), Node.js (NAPI), Go (CGO).
>
> **Version**: v2.3 (development) | Engines: Valkey 7.2/8.0/8.1, Redis 6.2/7.0/7.1/7.2

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Core Design Decisions](#2-core-design-decisions)
3. [Command Lifecycle](#3-command-lifecycle)
4. [Connection Management](#4-connection-management)
5. [Cluster Routing & Slot System](#5-cluster-routing--slot-system)
6. [PubSub Synchronizer](#6-pubsub-synchronizer)
7. [Compression System](#7-compression-system)
8. [Language Binding Patterns](#8-language-binding-patterns)
9. [Error Handling & Resilience](#9-error-handling--resilience)
10. [Security Model](#10-security-model)
11. [Telemetry & Observability](#11-telemetry--observability)
12. [Testing Patterns](#12-testing-patterns)
13. [Adding a New Command](#13-adding-a-new-command)
14. [Build System & CI](#14-build-system--ci)
15. [Performance Engineering](#15-performance-engineering)
16. [Contribution Workflow](#16-contribution-workflow)
17. [Key Files Quick Reference](#17-key-files-quick-reference)
18. [Common Pitfalls & Debugging](#18-common-pitfalls--debugging)

---

## 1. Architecture Overview

### Layered Design

```
Language Wrapper (Python/Java/Node/Go)
    |
    v
[Socket IPC / FFI boundary]
    |
    v
glide-core (Rust)       <-- All business logic lives here
    |
    v
redis-rs (forked)       <-- Custom cluster, AZ, pubsub extensions
    |
    v
Valkey / Redis Server
```

**Key Principle**: All complex logic (routing, retry, compression, pubsub sync, connection management) lives in the Rust core. Language wrappers are thin shims that handle serialization and expose idiomatic APIs. This means a bug fix or feature in `glide-core` benefits all languages simultaneously.

### Two IPC Models

| Model | Languages | Mechanism | Serialization | Key Trait |
|-------|-----------|-----------|---------------|-----------|
| **Socket IPC** | Python async, Node.js | Unix Domain Socket | Protobuf (varint-prefixed) | Integrates with async event loops |
| **FFI/JNI** | Java, Go, Python sync | Direct C calls / JNI | C structs / protobuf bytes | Zero-copy where possible, callback-based |

**Why two models?** Socket IPC naturally integrates with Python's asyncio and Node.js's event loop without blocking. FFI gives Java/Go lower overhead and simpler lifecycle management. Python sync uses FFI because there's no event loop to integrate with.

### Forked redis-rs

The project maintains a fork at `glide-core/redis-rs/` with significant custom extensions:
- **AZ-aware routing**: `availability_zone` field on connections, `ReadFromReplicaStrategy` variants for AZ affinity
- **Sharded PubSub**: Full SSUBSCRIBE/SUNSUBSCRIBE support
- **Multi-slot command splitting**: `MultiSlotArgPattern` for commands spanning multiple slots
- **Response aggregation policies**: `AllSucceeded`, `Sum`, `CombineArrays`, `CombineMaps`, `Min`, `FirstSucceededNonEmpty`
- **Topology management**: Mutually exclusive refresh/validation, parallelized DNS lookups, empty hostname handling
- **Connection state tracking**: Database ID, auth credentials, protocol version preserved across reconnects

---

## 2. Core Design Decisions

### Why Rust Core?
1. **Memory safety without GC** - Critical for long-lived server connections
2. **Performance** - Zero-cost abstractions, no GC pauses
3. **Shared logic** - One implementation, N language wrappers
4. **Fearless concurrency** - Compiler enforces thread safety at FFI boundaries

### Why Protobuf for Socket IPC?
- Efficient binary serialization (smaller than JSON, faster than text protocols)
- Schema evolution support (new fields don't break old clients)
- Code generation for multiple languages
- Varint length-prefixed framing for efficient streaming

### Why Not Protobuf for FFI?
- FFI path uses C structs (`CommandResponse`) directly to avoid serialization overhead
- `CommandResponse` is a tagged union with explicit pointer fields
- Caller responsible for freeing via `free_command_response()` to maintain deterministic memory management

### Lazy Client Pattern
Clients can be created with `lazy_connect: true`:
1. Initial `Client::new()` stores config in `ClientWrapper::Lazy(LazyClient)`
2. First command triggers `get_or_initialize_client()` with double-checked locking
3. Read lock checks if initialized, write lock creates the real client
4. After initialization, waits for initial PubSub sync before returning
5. **Critical**: Write guard must be dropped before waiting for PubSub sync to avoid deadlock

### Backpressure (Little's Law)
- `inflight_requests_allowed: AtomicIsize` limits concurrent requests
- Default: **1000** (based on 50K req/s at 1ms latency with 20x buffer)
- Python with PubSub: lowered to **250**
- Returns error immediately if limit exceeded (fail-fast, not queue-and-wait)

---

## 3. Command Lifecycle

### Full Flow (Socket IPC path)

```
1. [Wrapper] Serialize CommandRequest protobuf
2. [Wrapper] Write varint-length-prefixed bytes to Unix socket
3. [socket_listener.rs] RotatingBuffer reads from socket
4. [socket_listener.rs] Parse varint prefix, extract protobuf
5. [socket_listener.rs] Decode RequestType, args, routing
6. [Client::send_command()] Called with Cmd + optional RoutingInfo

   6a. [Lazy init] get_or_initialize_client() if Lazy
   6b. [PubSub intercept] Check if command should be intercepted
   6c. [Timeout calc] get_request_timeout() with blocking command extension
   6d. [Routing] Route to standalone or cluster client
       - Cluster: Rewrite Random -> RandomPrimary for write commands
       - Standalone: Select primary or replica based on ReadFrom strategy
   6e. [Value conversion] convert_to_expected_type() normalizes RESP2/RESP3
   6f. [Decompression] If compression manager configured, decompress GET responses
   6g. [Post-command intercept] Track SELECT/AUTH/HELLO/CLIENT SETNAME state

7. [socket_listener.rs] Serialize Response protobuf back to socket
8. [Wrapper] Read response, resolve Promise/Future
```

### Timeout Handling (Nuanced)

| Command Type | Timeout Calculation |
|-------------|-------------------|
| Regular commands | `request_timeout` (default 250ms) |
| BLPOP, BRPOP, BLMOVE, BZPOPMAX, BZPOPMIN, BRPOPLPUSH | `last_arg_seconds + 0.5s` |
| BLMPOP, BZMPOP | `arg[1]_seconds + 0.5s` |
| XREAD, XREADGROUP | `BLOCK_subcommand_ms + 0.5s` |
| WAIT | `arg[2]_ms + 0.5s` |
| Zero timeout (blocking forever) | No timeout applied |

The 0.5s extension (`BLOCKING_CMD_TIMEOUT_EXTENSION`) ensures the client doesn't timeout before the server responds to a blocking command.

### Post-Command Interception

After successful execution, state-changing commands are intercepted to preserve connection state for reconnection:

| Command | What's Stored | Purpose |
|---------|--------------|---------|
| `SELECT db` | database_id | Restore DB on reconnect |
| `AUTH [user] pass` | username, password | Re-authenticate on reconnect |
| `HELLO ver [AUTH user pass] [SETNAME name]` | protocol, auth, client_name | Restore protocol + auth + name |
| `CLIENT SETNAME name` | client_name | Restore client name on reconnect |

### PubSub Command Interception (Pre-execution)

All PubSub commands are intercepted **before** routing to the server:

| Mode | Commands | Behavior |
|------|----------|----------|
| **Lazy (non-blocking)** | SUBSCRIBE, PSUBSCRIBE, SSUBSCRIBE, UNSUBSCRIBE, etc. | Update desired state, return immediately. Reconciliation task handles actual execution. |
| **Blocking** | *_BLOCKING variants | Update desired state, then wait for sync with deadline |
| **Query** | GET_SUBSCRIPTIONS | Return both desired and actual subscription state |

---

## 4. Connection Management

### Standalone Mode: ReconnectingConnection

State machine with automatic recovery:

```
InitializedDisconnected ──connect──> Connected
         ▲                              │
         │                              │ error
         │                              ▼
         └────────── Reconnecting ──────┘
                    (infinite backoff)
```

- **PING verification** after reconnect before marking as connected
- **Connection state restoration**: SELECT, AUTH, HELLO, CLIENT SETNAME replayed
- **ManualResetEvent** signals when connection is available
- Background task handles reconnection with infinite backoff (never gives up until client dropped)
- **Periodic check every 3 seconds** (not PING, just state check)

### Read Routing Strategies (Standalone)

| Strategy | Behavior |
|----------|----------|
| `Primary` | All reads go to primary |
| `PreferReplica` | Round-robin across replicas (fallback to primary) |
| `AZAffinity` | Prefer same availability zone replicas |
| `AZAffinityReplicasAndPrimary` | Same-AZ replicas > primary > any replica |

### Cluster Mode

- Uses redis-rs `ClusterConnection` (async) with custom extensions
- **Topology refresh**: Every 60s (default), exponential backoff (500ms base, 1.5x, 3 retries)
- **MOVED handling**: Updates SlotMap primary/replica roles, retries to new node
- **ASK handling**: Sends `ASKING` command then retries (transient redirect during resharding)
- **Topology refresh vs connection validation**: Mutually exclusive (#5308) to prevent race conditions
- **DNS lookups parallelized** during slot refresh (#5281)

---

## 5. Cluster Routing & Slot System

### Slot Calculation
- CRC16 XMODEM modulo 16384
- **Hashtag extraction**: `{tag}key` uses only `tag` for slot calculation
- `SlotMap`: BTreeMap keyed by end-slot for O(log n) lookup

### Multi-Slot Command Splitting

Commands spanning multiple slots are split using `MultiSlotArgPattern`:

| Pattern | Commands | Splitting Logic |
|---------|----------|----------------|
| `KeysOnly` | DEL, EXISTS, UNLINK | Each key routed independently |
| `KeyValuePairs` | MSET | Adjacent key-value pairs grouped by slot |
| `KeysAndLastArg` | MGET variant | Keys grouped by slot, last arg replicated |
| `KeyWithTwoArgTriples` | Specialized | Triplets of (key, arg1, arg2) grouped |

### Response Aggregation

| Policy | Commands | Logic |
|--------|----------|-------|
| `AllSucceeded` | MSET | All must return OK |
| `Sum` | DEL, EXISTS | Sum of integer responses |
| `CombineArrays` | MGET, KEYS | Concatenate array responses |
| `CombineMaps` | PUBSUB NUMSUB | Merge map responses |
| `Min` | WAIT | Minimum of integer responses |
| `FirstSucceededNonEmpty` | RANDOMKEY | First non-nil response |

### Cluster Scan

Cluster SCAN uses `ScanStateRC` (Arc-wrapped scan state) stored in a global container with nanoid keys:
1. Client performs scan, gets cursor + results
2. If cursor not finished, store `ScanStateRC` in global container, return nanoid key
3. Wrapper holds nanoid key with drop function that removes from container
4. When key dropped, no references remain, Rust GC cleans `ScanState`

---

## 6. PubSub Synchronizer

### Observer Pattern with Eventual Consistency

The `GlidePubSubSynchronizer` decouples user intent from server execution:

```
User calls SUBSCRIBE("channel")
    │
    ▼
Update desired_subscriptions  ───> Return immediately (non-blocking)
    │
    ▼ (background, every 3s)
Reconciliation Task
    │
    ├── Compute diff: desired vs actual
    ├── Subscribe to missing channels
    ├── Unsubscribe from unwanted channels
    └── Update current_subscriptions_by_address
```

### Key Design Choices

1. **Weak references**: `Weak<TokioRwLock<ClientWrapper>>` avoids reference cycles - reconciliation task exits when client drops
2. **Per-address tracking**: `current_subscriptions_by_address` enables handling topology changes (slot migrations move subscriptions between nodes)
3. **Pending unsubscribes**: When slots migrate, old-node subscriptions are queued for cleanup
4. **Configurable interval**: Default 3s, exposed as `pubsub_reconciliation_interval`
5. **Telemetry**: `subscription_out_of_sync_count`, `subscription_last_sync_timestamp`

### Subscription Kinds

| Mode | Kinds | Note |
|------|-------|------|
| Cluster | Exact, Pattern, Sharded | Sharded PubSub uses slot-based routing |
| Standalone | Exact, Pattern | No sharded support |

Static slices (`CLUSTER_SUBSCRIPTION_KINDS`, `STANDALONE_SUBSCRIPTION_KINDS`) for zero-allocation iteration.

---

## 7. Compression System

### Wire Format

```
Byte 0-2: Magic header (0x00, 0x01, 0x02)
Byte 3:   Version (0x01)
Byte 4:   Backend ID (0x01=Zstd, 0x02=LZ4)
Byte 5+:  Compressed payload
```

### Behavior

- **Compression**: Applied to SET commands (value at index 1)
- **Decompression**: Applied to GET responses
- **Min threshold**: 64 bytes default (configurable, absolute minimum 6 = header + 1 byte)
- **Graceful fallback**: If compression fails or doesn't reduce size, uses original
- **Cross-backend decompression**: Static backends (OnceLock) allow Zstd-compressed data to be read even if client now uses LZ4
- **Telemetry**: Values compressed/decompressed, bytes saved, skipped count

### Backend Defaults

| Backend | Default Level | Range | Characteristic |
|---------|--------------|-------|----------------|
| Zstd | 3 | -131072 to 22 | Higher compression ratio |
| LZ4 | 0 | -128 to 12 | Faster, lower ratio |

---

## 8. Language Binding Patterns

### Python Async (PyO3 + Socket IPC)

**Key files**: `python/glide-async/src/lib.rs`, `python/glide-async/python/glide/glide_client.py`

**Flow**:
1. PyO3 `start_socket_listener_external(init_callback)` starts Rust socket listener
2. Callback returns socket path; Python creates socket connection
3. Commands serialized as protobuf via `_available_futures: Dict[int, Future]` keyed by callback_idx
4. Response contains `RespPointer` (raw ptr to `Box<Value>` on Rust heap)
5. Python calls `value_from_pointer(ptr)` PyO3 function to consume and convert

**Memory**: Rust leaks `Box<Value>` via `Box::leak()`, gives Python the pointer as u64. Python must consume immediately.

**Async model**: Uses `anyio` for runtime-agnostic async (asyncio, trio, uvloop).

### Python Sync (CFFI/FFI)

**Key files**: `python/glide-sync/glide_sync/_glide_ffi.py`, `ffi/src/lib.rs`

**Flow**: CFFI bindings → C FFI functions → Tokio `block_on()` → `CommandResponse` struct returned

**Fork safety**: `os.register_at_fork(after_in_child=self._create_core_client)` recreates Rust client after `fork()`.

### Java (JNI)

**Key files**: `java/src/lib.rs`, `java/src/jni_client.rs`, `java/client/src/main/java/glide/api/BaseClient.java`

**Flow**:
1. JNI `create_client()` returns `handle_id` (u64) indexing into `DashMap<u64, GlideClient>`
2. `execute_command(handle_id, protobuf_bytes, callback_id)` spawns Tokio task
3. On completion, Rust calls Java callback via JNI `call_void_method(callback_id, result)`
4. Java `CompletableFuture` completed when callback fires

**Method caching**: `RegistryMethodCache` with `OnceLock<Mutex>`, double-checked locking, classloader-safe fallback.

**Jedis compatibility**: Drop-in replacement layer at `java/jedis-compatibility/` wrapping GLIDE in `redis.clients.jedis` package API.

### Node.js (NAPI v2)

**Key files**: `node/rust-client/src/lib.rs`, `node/src/BaseClient.ts`

**Flow**: Similar to Python async but with NAPI deferred pattern and per-client Tokio runtime.

**Performance**: Uses jemalloc allocator (non-MSVC) for better performance.

### Go (CGO/FFI)

**Key files**: `go/base_client.go`, `go/callbacks.go`, `ffi/src/lib.rs`

**Flow**:
1. `create_client()` returns opaque `void*`
2. `command(client_ptr, request_bytes, callback_id)` - Rust returns immediately
3. Go creates `chan payload`, passes channel pointer as callback baton
4. Rust callback fires → Go `//export successCallback` sends to channel
5. Goroutine unblocks from `<-channel`

**Baton pattern**: Channel pointer disguised as `usize` passed through Rust and back. Go copies data immediately in callback, Rust frees after callback returns.

### Memory Ownership Summary

| Binding | Client Lifetime | Response Ownership | Value Transfer |
|---------|----------------|-------------------|----------------|
| Python Async | Rust heap via socket_listener | Pointer leaked, Python Box::from_raw | Move (pointer transfer) |
| Python Sync | FFI handle | Tree malloc'd by Rust | Copy then free |
| Java | DashMap by handle_id | Callback pointer | Copy in JNI callback |
| Node.js | NAPI struct | Protobuf bytes on socket | Copy from socket buffer |
| Go | FFI handle (void*) | Callback pointer | Copy in callback, Rust frees after |

---

## 9. Error Handling & Resilience

### Error Classification

```rust
enum RequestErrorType {
    Unspecified = 0,  // General errors
    ExecAbort = 1,    // Transaction EXEC failed
    Timeout = 2,      // Request timed out
    Disconnect = 3,   // Connection lost (triggers reconnect)
}
```

### Error Propagation Across Boundaries

```
Rust RedisError
    → RequestErrorType enum + message string
    → Passed through FFI/socket/JNI
    → Language maps to native exception:
        Python: GlideError → ClosingError, RequestError → TimeoutError, ExecAbortError, ConnectionError
        Node.js: ValkeyError → ClosingError, RequestError → TimeoutError, ExecAbortError
        Java: FFIError with Jni/Utf8/Logger/OpenTelemetry variants → Exception/RuntimeException
        Jedis compat: JedisException → JedisConnectionException, JedisDataException
```

### Retry Strategy

- **Formula**: `factor * base^attempt` (default: 100ms, 200ms, 400ms, 800ms, 1600ms)
- **Defaults**: factor=100ms, exponent_base=2, number_of_retries=5, jitter_percent=20%
- **Permanent errors** (skip retry): AuthenticationFailed, InvalidClientConfig, RESP3NotSupported, NOAUTH, WRONGPASS
- **Reconnection**: Infinite backoff (never gives up until client dropped)
- **PING verification**: After reconnect, before marking as Connected

### RESP2 vs RESP3 Normalization

The value conversion system normalizes both RESP versions:
- **Maps**: RESP2 returns flat arrays `[k1,v1,k2,v2]`, RESP3 returns native Map
- **Scores**: RESP2 returns BulkString ("1.5"), RESP3 returns Double
- **Booleans/Sets**: RESP3 has native types; RESP2 uses Int/Array equivalents
- `convert_to_expected_type()` normalizes both into consistent Value types for wrappers

---

## 10. Security Model

### Socket Security
- Socket file permissions: `0o600` (owner read/write only)
- Socket directory: `0o700` (owner only)
- Socket path: `<tempdir>/glide-socket-<uuid>` (UUID prevents predictability)
- TOCTOU vulnerability mitigated via restricted directory creation before socket

### TLS/mTLS
- `rustls` with `aws-lc-rs` backend (FIPS-aligned)
- Three modes: `NoTls`, `InsecureTls` (no cert verification), `SecureTls`
- mTLS supported (client_cert + client_key) since v2.3
- `TCP_NODELAY` enabled by default since v2.2.5 (#5100)

### Authentication
- Password-based AUTH (single password or username+password)
- IAM authentication for AWS ElastiCache/MemoryDB:
  - SigV4 presigned token (15-minute TTL)
  - Auto-refresh (default 5 minutes, configurable 1s to 12h)
  - Exponential backoff for token failures (8 attempts max, 100ms initial, 3s cap)
  - Weak references in callback to prevent reference cycles (#5431)
- Passwords sanitized as `[sanitized]` in debug logs

---

## 11. Telemetry & Observability

### OpenTelemetry Integration

**Metrics tracked**:
- `glide.timeout_errors` - timeout count
- `glide.retry_attempts` - retry count
- `glide.moved_errors` - MOVED redirect count
- `glide.subscription_out_of_sync_count` - PubSub drift
- `glide.subscription_last_sync_timestamp` - last PubSub sync
- Compression: values compressed/decompressed, bytes saved, skipped
- Total connections, total clients

**Export**: gRPC, HTTP/protobuf, HTTP/JSON, or File
**Config via env vars**: `OTEL_EXPORTER_OTLP_PROTOCOL`, `OTEL_EXPORTER_OTLP_TRACES_PROTOCOL`
**Defaults**: flush interval 5000ms, trace sampling 1%
**Cross-boundary spans**: `root_span_ptr` passed through protobuf

### Logging

- `logger_core` crate with `tracing` backend
- Macros: `log_trace`, `log_debug`, `log_info`, `log_warn`, `log_error`
- Hourly rotating file appender in `glide-logs/` or `$GLIDE_LOG_DIR`
- Key categories: "reconnect", "connection creation", "send_command", "parse input", "pubsub_synchronizer"
- Logger uses `try_init()` for graceful fallback on duplicate initialization (#5129)

---

## 12. Testing Patterns

### Test Infrastructure

| Language | Framework | Key Notes |
|----------|-----------|-----------|
| Rust | rstest, criterion, iai-callgrind | `serial_test` for exclusive access, `ctor` for init |
| Python | pytest | asyncio/uvloop/trio via `--async-backend` |
| Java | JUnit 5 + Gradle | JaCoCo coverage, 2GB heap, 10s connection timeout |
| Node.js | Jest | `SharedTests.ts` for cross-client test sharing |
| Go | go test + testify | Address Sanitizer on Linux, HTML reports |

### Cluster Manager (`utils/cluster_manager.py`)

~4000-line Python script used by ALL languages:
```bash
cluster_manager.py start [--cluster-mode] [-n SHARDS] [-r REPLICAS] [--tls]
cluster_manager.py stop --cluster-folder /path
```

### Test Configurations by Language
- **Java**: 5 clusters in parallel (standalone, standalone-TLS, cluster, cluster-TLS, cluster-AZ with 4 replicas)
- **Python**: 4 clusters (standalone + cluster x TLS/non-TLS)
- **Go**: Address Sanitizer enabled (`-fsanitize=address`)
- **Rust**: Lazy-initialized shared servers reused across tests

### Preventing Flaky Tests

Lessons learned from multiple flaky test fixes:
1. **Replace sleep with polling+retry** loops (ScriptKill: #5423, #5338, #5418)
2. **Add finally-blocks** for resource cleanup (Java: #5348)
3. **Stabilize startup timing** (Node.js TLS: #5392)
4. **Use connection timeout** in cleanup code (ScriptKill: #5418)
5. **Post-test health checks** to verify server state
6. **Increase connection timeouts in CI** (Java: 10000ms vs default 2000ms)

### Memory Safety Testing
- **Rust FFI**: MIRI tests at `ffi/miri-tests/`
- **Go**: Address Sanitizer on Linux (disabled for Alpine/musl)

---

## 13. Adding a New Command

### Step-by-Step Workflow

**1. Core Rust (glide-core)**

a. Add variant to `RequestType` enum in `glide-core/src/request_type.rs`:
```rust
// Follow the numeric category convention:
// Bitmap: 100s, Cluster: 200s, Connection: 300s, Generic: 400s,
// Geo: 500s, Hash: 600s, HyperLogLog: 700s, List: 800s, etc.
MyNewCommand = 1234,
```

b. Add `to_cmd()` implementation in the same file:
```rust
RequestType::MyNewCommand => cmd("MYNEWCOMMAND"),
```

c. If the command has compression behavior, add it to `compression_behavior()`.

d. If the command needs special return type handling, add it to `expected_type_for_cmd()` in `value_conversion.rs`.

**2. Protobuf (if using socket IPC)**

Add the request type mapping in `glide-core/src/protobuf/command_request.proto`.

**3. Language Wrappers (for each language)**

| Language | Interface File | Implementation |
|----------|---------------|----------------|
| Python | `python/glide-shared/glide_shared/commands/` | Command class/method |
| Java | `java/client/src/main/java/glide/api/commands/` | Interface + BaseClient impl |
| Node.js | `node/src/Commands.ts` + `node/src/BaseClient.ts` | TypeScript method |
| Go | `go/internal/interfaces/` | Interface + base_client impl |

**4. Tests (for each language)**

- Integration tests against real server
- Unit tests for argument construction
- Cross-client test coverage (Node.js `SharedTests.ts` pattern)

**5. Cross-Language Consistency**

- Command names and argument order must match across all wrappers
- Default values synchronized (documented in `types.rs` comments)
- RESP2/RESP3 handling tested in both protocols

---

## 14. Build System & CI

### Build Commands

```bash
# Full builds
make all                    # All languages
make java / python / node / go  # Individual

# Rust core
cargo build -p glide-core --features socket-layer
cargo build -p glide-ffi --release

# Per-language
cd java && ./gradlew :client:buildAllRelease
cd python && python3 dev.py build --mode release
cd node && npm i && npm run build:release
cd go && make install-build-tools && make build
```

### FFI Release Profile (Aggressive Optimization)

```toml
[profile.release]
opt-level = 3
lto = "fat"           # Maximum link-time optimization
codegen-units = 1     # Maximum optimization
strip = "symbols"
panic = "abort"       # No unwinding across FFI
```

### CI Workflows

| Workflow | Trigger | Purpose |
|----------|---------|---------|
| `java.yml` | PR / push | Java tests + lint |
| `python.yml` | PR / push | Python tests + lint |
| `node.yml` | PR / push | Node.js tests + lint |
| `go.yml` | PR / push | Go tests + lint |
| `rust.yml` | PR / push | Core Rust tests |
| `redis-rs.yml` | PR / push | Fork-specific tests |
| `codeql.yml` | scheduled | Security analysis (pinned to 2.23.9) |
| `semgrep.yml` | PR | Static analysis |
| `*-cd.yml` | release | Continuous deployment |
| `ort.yml` | scheduled | Open source license compliance |

### Cross-Compilation
- Go uses zigbuild for cross-compilation on Linux
- Pre-built Rust binaries in `go/rustbin/<target>/`
- Targets: macOS (aarch64/x86_64), Linux glibc (aarch64/x86_64), Alpine musl (aarch64/x86_64)

---

## 15. Performance Engineering

### Defaults That Matter

| Parameter | Default | Rationale |
|-----------|---------|-----------|
| Request timeout | 250ms | Balanced for typical operations |
| Connection timeout | 2000ms | Java tests use 10000ms |
| Inflight request limit | 1000 | Little's Law: 50K req/s * 1ms = 50, with 20x buffer |
| Periodic connection check | 3s | Balance between validation and overhead |
| Topology refresh | 60s | Cluster stability vs freshness |
| Blocking cmd timeout extension | 0.5s | Prevent premature timeout |
| TCP_NODELAY | enabled | Reduce latency (since v2.2.5) |
| PubSub reconciliation | 3s | Eventual consistency interval |

### Runtime Models

**Socket IPC**: `LocalPoolHandle` with one thread per CPU core. `spawn_pinned()` ensures tasks stay on the same thread (no context switching). Three concurrent tasks per connection: reader, writer, push manager.

**FFI**: Single-threaded Tokio runtime in dedicated thread (`glide-runtime-thread`). Initialized once via `OnceCell`. Stays alive for application lifetime. Shutdown via `Notify` signal.

### Benchmarks
- `glide-core/benches/rotating_buffer_benchmark` - buffer parsing
- `glide-core/benches/connections_benchmark` - connection management
- `glide-core/benches/memory_benchmark` - memory usage
- `benchmarks/` - cross-language benchmarks

---

## 16. Contribution Workflow

### Requirements
1. **DCO Signoff**: `Signed-off-by: Name <email>` on all commits
2. **Conventional Commits**: `feat(scope): description`, `fix(scope): description`
3. **GPG/SSH signing**: Commits must show as "Verified" on GitHub
4. **Tests**: Must pass for affected languages
5. **Lint**: Language-specific linters must pass

### Issue Workflow
- Check for `good first issue` label or triaged issues (no `Untriaged user issue` label)
- Comment intent before starting work
- Open an issue for significant changes before coding

### PR Process
1. Fork, branch from `main`
2. Focused changes (no unrelated formatting)
3. Tests pass locally
4. DCO + conventional commit format
5. CI passes
6. Stay involved in review conversation

---

## 17. Key Files Quick Reference

### Core
| File | What |
|------|------|
| `glide-core/src/client/mod.rs` | Client struct, send_command, lazy init |
| `glide-core/src/socket_listener.rs` | Unix socket IPC server |
| `glide-core/src/request_type.rs` | 500+ command enum with Cmd construction |
| `glide-core/src/client/value_conversion.rs` | RESP2/RESP3 normalization |
| `glide-core/src/client/reconnecting_connection.rs` | Auto-reconnect with backoff |
| `glide-core/src/client/standalone_client.rs` | Standalone mode + replica routing |
| `glide-core/src/pubsub/synchronizer.rs` | PubSub reconciliation engine |
| `glide-core/src/compression.rs` | Zstd/LZ4 transparent compression |
| `glide-core/src/iam/mod.rs` | AWS IAM token management |
| `glide-core/src/errors.rs` | Error classification |

### redis-rs Fork
| File | What |
|------|------|
| `glide-core/redis-rs/redis/src/cluster_routing.rs` | Route/slot calc, response policies |
| `glide-core/redis-rs/redis/src/cluster_slotmap.rs` | SlotMap, round-robin replicas |
| `glide-core/redis-rs/redis/src/cluster_async/mod.rs` | Async cluster, MOVED/ASK |

### Language Bindings
| Language | Rust Bridge | Client |
|----------|------------|--------|
| Python async | `python/glide-async/src/lib.rs` | `python/glide-async/python/glide/glide_client.py` |
| Python sync | `ffi/src/lib.rs` | `python/glide-sync/glide_sync/glide_client.py` |
| Java | `java/src/lib.rs` | `java/client/src/main/java/glide/api/BaseClient.java` |
| Node.js | `node/rust-client/src/lib.rs` | `node/src/BaseClient.ts` |
| Go | `ffi/src/lib.rs` | `go/base_client.go` |

### Configuration
| File | What |
|------|------|
| `AGENTS.md` | AI agent instructions |
| `CLAUDE.md` | Claude-specific context |
| `CONTRIBUTING.md` | Contribution guidelines |
| `deny.toml` | Cargo deny (license/advisory) |
| `Makefile` | Top-level build orchestration |

---

## 18. Common Pitfalls & Debugging

### When Connection Fails
1. Check error type (timeout vs disconnect vs auth)
2. Timeout → increase `connection_timeout` (default 2000ms)
3. Auth → verify credentials, check for NOAUTH/WRONGPASS
4. TLS → cert validity, mode match, mTLS config
5. AWS ElastiCache → check for empty hostname (#5373)

### When Commands Hang
1. Check inflight limit (1000 default)
2. Blocking commands → verify timeout extension
3. Topology refresh race → #5308 (made mutually exclusive)
4. Java Lambda → response > 16KB (#5301, fixed)

### When PubSub Drifts
1. `GET_SUBSCRIPTIONS` → compare desired vs actual
2. Check `subscription_out_of_sync_count` metric
3. Verify reconciliation task running (log: "pubsub_synchronizer")
4. Check for topology changes causing slot migrations

### When Tests Are Flaky
1. Replace sleep with polling+retry
2. Add finally-blocks for cleanup
3. Increase connection timeouts in CI
4. Post-test health checks
5. Use `serial_test` for tests needing exclusive access

### Historical Bugs (Architectural Lessons)
| Bug | Root Cause | Fix | Lesson |
|-----|-----------|-----|--------|
| IAM reference cycle (#5431) | Strong ref in callback | Weak references | Always use Weak for background tasks |
| Java Lambda 16KB (#5301) | Direct-buffer overflow | Scalar type preservation | Test with large payloads |
| Topology vs validation race (#5308) | Concurrent access | Mutual exclusion | Shared state needs coordination |
| Empty hostname (#5373) | AWS returns `""` | Fallback to IP | Handle cloud-provider quirks |
| JNI classloader (#5029) | Cross-classloader caching | Fallback env | Don't assume single classloader |
| Protobuf shading (#5031) | Version conflict | Relocation | Shade transitive deps |
| Logger panic (#5129) | Duplicate init | `try_init()` | Graceful init for libraries |

---

## Appendix: Feature Flags

| Flag | Effect | Default |
|------|--------|---------|
| `proto` | Enables protobuf support | Off |
| `socket-layer` | Unix socket IPC (implies `proto`) | Off |
| `standalone_heartbeat` | PING heartbeat for standalone | Off |
| `mock-pubsub` | Mock PubSub for testing | Off |
| `iam_tests` | IAM integration tests | Off |

**Note**: Socket-layer is NOT enabled by default; FFI users (Go, Python sync) use `proto` only. Tests always enable `socket-layer` via dev-dependencies.
