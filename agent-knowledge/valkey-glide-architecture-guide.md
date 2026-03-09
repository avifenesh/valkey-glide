# Valkey GLIDE Architecture & Developer Guide

A comprehensive reference for contributors and AI agents working on the Valkey GLIDE codebase. Covers architecture, patterns, build system, debugging, and contribution workflow.

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Architecture](#2-architecture)
3. [Core Subsystems](#3-core-subsystems)
4. [Language Bindings](#4-language-bindings)
5. [Command Lifecycle](#5-command-lifecycle)
6. [Key Design Decisions](#6-key-design-decisions)
7. [Adding a New Command](#7-adding-a-new-command)
8. [Build & Test Reference](#8-build--test-reference)
9. [Debugging Guide](#9-debugging-guide)
10. [Performance Tuning](#10-performance-tuning)
11. [Contribution Workflow](#11-contribution-workflow)
12. [Key Files Quick Reference](#12-key-files-quick-reference)

---

## 1. Project Overview

Valkey GLIDE (General Language Independent Driver for the Enterprise) is an official open-source client library for Valkey and Redis OSS. It provides a high-performance Rust core (`glide-core`) with language-specific wrappers for **Python** (async + sync), **Java**, **Node.js**, and **Go**.

### Supported Engine Versions

| Engine | 6.2 | 7.0 | 7.1 | 7.2 | 8.0 | 8.1 |
|--------|-----|-----|-----|-----|-----|-----|
| Valkey | -   | -   | -   | Y   | Y   | Y   |
| Redis  | Y   | Y   | Y   | Y   | -   | -   |

### Repository Structure

```
valkey-glide/
  glide-core/           # Rust core driver (connection, protocol, clustering)
    redis-rs/           # Forked redis-rs with custom cluster/pubsub extensions
    telemetry/          # OpenTelemetry integration crate
    src/
      client/           # Client, connection management, value conversion
      pubsub/           # PubSub synchronizer (observer pattern)
      protobuf/         # .proto schemas for IPC
      iam/              # AWS IAM authentication (SigV4)
  ffi/                  # C FFI layer for Go and Python sync
  java/                 # Java client (JNI bridge + Gradle project)
    jedis-compatibility/# Drop-in Jedis replacement
  python/               # Python client (async via PyO3, sync via CFFI)
    glide-async/        # PyO3 binding (socket IPC)
    glide-sync/         # CFFI binding (FFI)
    glide-shared/       # Shared command definitions
  node/                 # Node.js client (NAPI v2)
    rust-client/        # NAPI Rust binding
  go/                   # Go client (CGO/FFI)
  logger_core/          # Rust logging infrastructure (tracing-based)
  utils/                # Test utilities, cluster management scripts
  benchmarks/           # Cross-language performance benchmarks
  examples/             # Usage examples per language
  docs/                 # Documentation (MkDocs)
```

---

## 2. Architecture

### High-Level Data Flow

```
Language Wrapper (Python / Java / Node.js / Go)
    |
    v
[Socket IPC or FFI boundary]
    |
    v
glide-core (Rust)
    |
    v
redis-rs (forked, at glide-core/redis-rs/)
    |
    v
Valkey / Redis Server
```

### Two Communication Paths

| Path | Languages | Mechanism | Serialization | Key Trait |
|------|-----------|-----------|---------------|-----------|
| **Socket IPC** | Python async, Node.js | Unix Domain Socket | Protobuf (varint-prefixed) | Integrates with language async event loops |
| **FFI** | Python sync, Go, Java | Direct C function calls | C structs / Protobuf bytes | Lower latency, callback-based |

#### Socket IPC Path
1. Language wrapper serializes `CommandRequest` protobuf
2. Writes varint-length-prefixed bytes to Unix Domain Socket
3. `socket_listener.rs` reads into `RotatingBuffer`, parses protobuf
4. Routes to `Client::send_command()` with `Cmd` and optional `RoutingInfo`
5. Response serialized as protobuf, written back to socket

#### FFI Path
1. Language calls `command(client_ptr, request_bytes, len)` via C FFI
2. Rust `ffi/src/lib.rs` decodes request, dispatches to core client
3. Result returned as `CommandResponse` C struct (tagged union)
4. Caller copies data and calls `free_command_response()`

### Client Architecture

```rust
enum ClientWrapper {
    Standalone(StandaloneClient),   // Single server or replica set
    Cluster { client: ClusterConnection },  // Cluster mode
    Lazy(Box<LazyClient>),          // Deferred connection (first command triggers connect)
}
```

The `Client` struct wraps this in `Arc<RwLock<ClientWrapper>>` for thread-safe access. Key fields:
- `request_timeout: Duration` (default 250ms)
- `inflight_requests_allowed: Arc<AtomicIsize>` (backpressure, default 1000)
- `compression_manager: Option<Arc<CompressionManager>>` (transparent Zstd/LZ4)
- `iam_token_manager: Option<Arc<IAMTokenManager>>` (AWS IAM auth)
- `pubsub_synchronizer: Arc<dyn PubSubSynchronizer>` (subscription state)

---

## 3. Core Subsystems

### 3.1 Connection Management

#### Standalone Mode
- `StandaloneClient` maintains `Vec<ReconnectingConnection>` (primary + replicas)
- Primary detected via `INFO REPLICATION` during setup
- Read routing strategies: `Primary`, `PreferReplica` (round-robin), `AZAffinity`, `AZAffinityReplicasAndPrimary`
- Periodic connection check every 3 seconds

#### `ReconnectingConnection`
State machine: `Connected` -> `Reconnecting` -> `Connected` (also `InitializedDisconnected`)
- `ManualResetEvent` signals when connection is available
- Background task handles reconnection with infinite backoff (never gives up)
- PING verification after reconnect
- Connection state restored: database ID (SELECT), password (AUTH), protocol (HELLO), client name

#### Cluster Mode
- Uses forked redis-rs `ClusterConnection` (async)
- `SlotMap`: BTreeMap keyed by end-slot for O(log n) lookup
- CRC16 XMODEM modulo 16384, with hashtag extraction (`{tag}key`)
- MOVED/ASK redirect handling with automatic retry
- Topology refresh every 60 seconds with exponential backoff
- DNS lookups parallelized during slot refresh
- Multi-slot commands split by slot using `MultiSlotArgPattern`

#### Response Aggregation Policies
| Policy | Used By | Behavior |
|--------|---------|----------|
| `AllSucceeded` | MSET | All nodes must succeed |
| `Sum` | DEL, EXISTS | Sum numeric results |
| `CombineArrays` | MGET, KEYS | Merge arrays |
| `CombineMaps` | PUBSUB NUMSUB | Merge maps |
| `Min` | WAIT | Minimum value |
| `FirstSucceededNonEmpty` | RANDOMKEY | First non-empty result |

### 3.2 PubSub Synchronizer

**File**: `glide-core/src/pubsub/synchronizer.rs`

Implements an **observer pattern with eventual consistency**:

- `desired_subscriptions` - what the user wants
- `current_subscriptions_by_address` - what's actually active per node
- Background reconciliation task runs every 3 seconds (configurable)
- Uses `Weak<TokioRwLock<ClientWrapper>>` to avoid reference cycles

**Reconciliation algorithm**:
1. Process pending unsubscribes from slot migrations
2. Compute diff between desired and actual subscriptions
3. Subscribe to missing, unsubscribe from unwanted
4. Sharded unsubscribes grouped by slot for correct routing

**Command interception** (pre-execution):
- **Lazy (non-blocking)**: SUBSCRIBE/UNSUBSCRIBE variants update desired state; reconciliation task handles actual server commands
- **Blocking**: Updates desired state, then waits for sync with timeout
- **GET_SUBSCRIPTIONS**: Returns both desired and actual subscription state

**Subscription kinds**: Exact, Pattern, Sharded (cluster only)

### 3.3 Compression

**File**: `glide-core/src/compression.rs`

Transparent compression for SET/GET commands:
- Header: 3-byte magic (`0x00 0x01 0x02`) + 1-byte version + 1-byte backend ID
- Backends: **Zstd** (ID 0x01, default level 3) and **LZ4** (ID 0x02, default level 0)
- Minimum size: 64 bytes (configurable, absolute minimum 6 = header + 1)
- Graceful fallback: if compression fails or doesn't reduce size, uses original
- Supported in all languages (Go added in v2.3 via #5359)

### 3.4 IAM Authentication

**File**: `glide-core/src/iam/mod.rs`

AWS SigV4 presigned tokens for ElastiCache/MemoryDB:
- 15-minute token TTL
- Auto-refresh with configurable interval (default 5 minutes)
- Exponential backoff for token generation failures (8 attempts max)
- Reference cycle fix (#5431): uses weak references in callback

### 3.5 Value Conversion

**File**: `glide-core/src/client/value_conversion.rs`

`ExpectedReturnType` enum provides rich type conversion from RESP values:
- Handles RESP2 vs RESP3 normalization (RESP2 returns flat arrays; RESP3 returns native maps)
- Scores: RESP2 returns BulkString ("1.5"), RESP3 returns Double
- Specialized types: `ZRankReturnType`, `FTSearchReturnType`, `GeoSearchReturnType`, etc.

### 3.6 Telemetry (OpenTelemetry)

**Crate**: `glide-core/telemetry/`

Metrics tracked:
- `glide.timeout_errors`, `glide.retry_attempts`, `glide.moved_errors`
- `glide.subscription_out_of_sync_count`, `glide.subscription_last_sync_timestamp`
- Compression stats: values compressed/decompressed, bytes saved
- Total connections, total clients

Signal export: gRPC, HTTP/protobuf, HTTP/JSON, or file. Default flush 5000ms, trace sampling 1%.
Cross-boundary span propagation via `root_span_ptr` in protobuf.

### 3.7 Runtime Model

| Path | Runtime | Threads |
|------|---------|---------|
| Socket IPC | `LocalPoolHandle` | One per CPU core, `spawn_pinned()` for thread-affinity |
| FFI | Single-threaded Tokio | Dedicated `glide-runtime-thread`, `OnceCell` init |

Socket listener runs three concurrent tasks per connection: reader, writer, push manager.

---

## 4. Language Bindings

### 4.1 Python Async (PyO3 + Socket IPC)

**Entry**: `python/glide-async/src/lib.rs` -> `python/glide-async/python/glide/glide_client.py`

- `start_socket_listener_external(init_callback)` returns socket path
- Commands serialized as protobuf, written to Unix socket
- Response contains `RespPointer` (raw pointer to `Box<redis::Value>`)
- Python calls `value_from_pointer(ptr)` via PyO3 -> `Box::from_raw()` -> Python object
- Memory: Rust leaks `Box<Value>` via `Box::leak()`, Python must consume pointer immediately
- Uses `anyio` for runtime-agnostic async (asyncio, trio)

### 4.2 Python Sync (CFFI/FFI)

**Entry**: `python/glide-sync/glide_sync/_glide_ffi.py`

- CFFI creates Python bindings to Rust `extern "C"` functions
- Returns `CommandResponse` C struct with discriminant union
- Python copies immediately, then calls `free_command_response(ptr)`
- Fork safety: `os.register_at_fork(after_in_child=self._create_core_client)`

### 4.3 Java (JNI)

**Entry**: `java/src/lib.rs` -> `java/client/src/main/java/glide/api/BaseClient.java`

- JNI `create_client()` returns handle_id (u64) indexing into `DashMap<u64, GlideClient>`
- `execute_command()` spawns async Tokio task, calls Java callback via JNI on completion
- Java `CompletableFuture` stored by callback_id
- `RegistryMethodCache` caches JNI class/method lookups with classloader-safe fallback
- Handle must be explicitly closed via `close_client(handle)` to free Rust resources

**Historical issues**: Lambda 16KB hang (#5301), classloader JNI caching (#5029), protobuf shading (#5031)

### 4.4 Node.js (NAPI v2)

**Entry**: `node/rust-client/src/lib.rs` -> `node/src/BaseClient.ts`

- `StartSocketConnection()` starts socket listener, returns Promise with socket path
- TypeScript creates `net.Socket`, writes protobuf commands
- NAPI deferred pattern: `env.create_deferred()` -> resolve on Tokio task completion
- Per-client Tokio runtime for isolation
- Uses jemalloc allocator (non-MSVC)

### 4.5 Go (CGO/FFI)

**Entry**: `go/base_client.go`, `go/callbacks.go`

- **Baton pattern**: Go channel pointer passed as `usize` through Rust
- `command()` returns immediately; Rust callback fires -> Go `//export successCallback` sends to channel
- Go copies data from C pointers immediately in callback; Rust frees after callback returns
- PubSub callback spawns goroutine to avoid blocking Rust executor
- Platform-specific LDFLAGS via build tags; pre-built Rust binaries in `go/rustbin/<target>/`

### 4.6 FFI Layer (Shared C Interface)

**File**: `ffi/src/lib.rs`

Key functions: `create_client`, `close_client`, `command`, `store_script`, `free_command_response`, `free_command_result`

`CommandResponse` is a C repr struct with:
- `ResponseType` discriminant (Null, Int, Float, String, Array, Map, Set, Error, etc.)
- Nested pointers for arrays/maps (recursive)
- `string_value` + `string_value_len` for binary-safe strings
- Caller must free via `free_command_response()`

### 4.7 Jedis Compatibility Layer

**Path**: `java/jedis-compatibility/`

Drop-in replacement for Jedis clients (`Jedis`, `JedisPooled`, `JedisCluster`, `UnifiedJedis`).
- Wraps GLIDE APIs in `redis.clients.jedis` package namespace
- Auto-detects standalone vs cluster from configuration
- `ConfigurationMapper` translates Jedis config to GLIDE config
- Published as `io.valkey:valkey-glide-jedis-compatibility`

### Error Propagation

All paths follow the same pattern:
1. Rust `RedisError` -> `RequestErrorType` enum (Unspecified/ExecAbort/Timeout/Disconnect)
2. Error type + message string passed through FFI/socket/JNI
3. Language maps enum to native exception class
4. Raises/rejects to caller

### Memory Ownership Summary

| Binding | Client Lifetime | Response Ownership | Value Transfer |
|---------|----------------|-------------------|----------------|
| Python Async | Rust heap via socket_listener | Pointer leaked, Python Box::from_raw | Move (pointer) |
| Python Sync | FFI handle | Tree malloc'd by Rust | Copy then free |
| Java | DashMap by handle_id | CommandResponse via callback | Copy in JNI |
| Node.js | NAPI struct | Protobuf bytes on socket | Copy from buffer |
| Go | FFI handle (void*) | Callback pointer | Copy in callback |

---

## 5. Command Lifecycle

### Full Flow (Socket IPC Path)

1. Language wrapper serializes `CommandRequest` protobuf
2. Writes varint-length-prefixed bytes to Unix socket
3. `socket_listener.rs` reads into `RotatingBuffer`
4. Parses protobuf `CommandRequest` messages
5. Extracts `RequestType`, args, routing info
6. Calls `Client::send_command()` with `Cmd` and optional `RoutingInfo`
7. Client resolves lazy init if needed (double-checked locking)
8. Intercepts pubsub commands via synchronizer (pre-execution)
9. Applies request timeout (extended +0.5s for blocking commands like BLPOP)
10. Routes to standalone or cluster client
11. Applies post-command interception (SELECT, AUTH, HELLO, CLIENT SETNAME)
12. Applies decompression if compression manager is configured
13. Converts value to expected type via `ExpectedReturnType`
14. Serializes `Response` protobuf back to socket

### Command Interception

**Pre-execution** (PubSub): All subscribe/unsubscribe commands intercepted before routing. Updates desired subscription state; actual server commands handled by reconciliation task.

**Post-execution** (State tracking): SELECT, AUTH, HELLO, CLIENT SETNAME results captured and stored for connection state restoration on reconnect.

---

## 6. Key Design Decisions

### Why Rust Core + Language Wrappers?
- Single implementation of complex logic (clustering, routing, reconnection)
- Performance-critical path in compiled language
- Each language gets native-feeling API via its best FFI mechanism
- Bug fixes in core automatically benefit all languages

### Why Two IPC Paths (Socket vs FFI)?
- **Socket IPC** integrates cleanly with Python async / Node.js event loops without blocking
- **FFI** provides lower overhead for languages that can handle callbacks (Java JNI, Go CGO)
- Python sync uses FFI because synchronous blocking is natural for FFI calls

### Why Fork redis-rs?
- Custom cluster extensions (multi-slot commands, response aggregation)
- PubSub auto-reconnection and sharded PubSub support
- AZ-aware routing
- Topology refresh with slot migration handling
- Custom MOVED/ASK handling with SlotMap updates

### Eventual Consistency for PubSub
- Decouples user intent from server execution
- Handles topology changes, slot migrations, node failures gracefully
- 3-second reconciliation interval balances responsiveness with overhead
- Lazy (non-blocking) mode lets user continue without waiting for subscription confirmation

### Backpressure via Inflight Limit
- `AtomicIsize` counter limits concurrent in-flight requests (default 1000)
- Based on Little's Law: 50K req/s * 1ms = 50, with 20x buffer
- Returns error to client immediately when exceeded (fail-fast)
- Python with PubSub: lowered to 250

### Lazy Client Pattern
- `lazy_connect: true` defers connection until first command
- Useful for dependency injection, testing, and reducing startup time
- Double-checked locking: read lock first, then write lock to initialize

---

## 7. Adding a New Command

Step-by-step process for adding a command across all layers:

### 1. Rust Core
- Add variant to `RequestType` enum in `glide-core/src/request_type.rs`
- Add protobuf mapping in `command_request.proto`
- Add `Cmd` construction in `to_cmd()` match arm
- Add compression behavior in `compression_behavior()` if applicable
- Add expected return type in `value_conversion.rs` if needed

### 2. Each Language Wrapper
- **Python**: Add to command interfaces in `python/glide-shared/glide_shared/commands/`
- **Java**: Add to command interface in `java/client/src/main/java/glide/api/commands/`
- **Node.js**: Add to `node/src/Commands.ts` and `node/src/BaseClient.ts`
- **Go**: Add to interfaces in `go/internal/interfaces/`

### 3. Tests
- Add integration tests in each language's test directory
- Cover standalone and cluster modes
- Test edge cases (empty args, large values, error responses)

### Cross-Language Consistency
- All wrappers share the same protobuf schema
- Command names and argument order must match across wrappers
- Default values (timeouts, etc.) must be synchronized

---

## 8. Build & Test Reference

### Prerequisites
- Rust toolchain (edition 2024)
- `valkey-server` or `redis-server` in PATH for integration tests
- Language-specific: Python 3.12-3.14, Java 21 + Gradle, Node.js 20+, Go

### Build Commands

```bash
# Full builds via Makefile
make all              # Build all language bindings
make java             # Java release build
make python           # Python async + sync (release)
make node             # Node.js release build
make go               # Go build
make clean            # Remove .build/ directory

# Individual components
cargo build -p glide-core --features socket-layer   # Rust core
cargo build -p glide-ffi --release                   # FFI library
cd java && ./gradlew :client:buildAllRelease         # Java
cd python && python3 dev.py build --mode release     # Python
cd node && npm i && npm run build:release             # Node.js
make -C go build                                      # Go
```

### Test Commands

```bash
# Integration tests (require running server)
make java-test        # Java integration tests
make python-test      # Python tests
make node-test        # Node.js tests
make go-test          # Go tests

# Unit tests
cargo test -p glide-core                            # Core unit tests
cargo test -p glide-core --features socket-layer    # Core + socket tests
cargo test -p glide-ffi                             # FFI tests

# Specialized
cd java && ./gradlew pubsubTest                     # PubSub only
cd java && ./gradlew modulesTest                    # Server module tests
make -C go pubsub-test                              # Go PubSub
make -C go opentelemetry-test                       # Go OTEL
```

### Linting

```bash
make java-lint          # spotlessApply
make python-lint        # via dev.py lint
make node-lint          # eslint
make go-lint            # golangci-lint
make prettier-check     # Prettier for JS/TS files
cargo clippy            # Rust linting
cargo fmt               # Rust formatting
```

### Test Infrastructure

**Cluster Manager** (`utils/cluster_manager.py`): ~4000-line Python script used by all languages.
```bash
cluster_manager.py start [--cluster-mode] [-n SHARDS] [-r REPLICAS] [--tls]
cluster_manager.py stop --cluster-folder /path
```

**Test frameworks**:
| Language | Framework | Notes |
|----------|-----------|-------|
| Rust | rstest, criterion, iai-callgrind | serial_test for exclusive |
| Python | pytest | asyncio/uvloop/trio support |
| Java | JUnit 5 + Gradle | JaCoCo coverage, 2GB heap |
| Node.js | Jest | SharedTests.ts for cross-client |
| Go | go test + testify | HTML reports via go-test-report |

**Memory safety**: MIRI tests at `ffi/miri-tests/`; Go uses Address Sanitizer on Linux.

### FFI Release Profile
Aggressive optimizations in `ffi/Cargo.toml`:
- `opt-level = 3`, `lto = "fat"`, `codegen-units = 1`
- `strip = "symbols"`, `panic = "abort"` (prevents unwinding across FFI)

---

## 9. Debugging Guide

### Logging
- Log levels: trace(4), debug(3), info(2), warn(1), error(0), off(5)
- Uses `tracing` crate, hourly rotating file appender in `glide-logs/` or `$GLIDE_LOG_DIR`
- `RUST_LOG` env var overrides default level
- Key categories: `"reconnect"`, `"connection creation"`, `"send_command"`, `"pubsub_synchronizer"`, `"parse input"`

### Operational Checklists

**When connections fail**:
1. Check logs for error type (timeout vs disconnect vs auth)
2. Timeout: increase `connection_timeout` (default 2000ms)
3. Auth: verify credentials, check for NOAUTH/WRONGPASS in logs
4. TLS: cert validity, mode match (NoTls/InsecureTls/SecureTls)
5. AWS ElastiCache: check for empty hostname issue (#5373)

**When commands hang**:
1. Check inflight limit (1000 default, 250 for Python+pubsub)
2. Blocking commands: timeout extension (+0.5s) within overall timeout?
3. Socket listener: is `UnixStreamListener::next_values()` reading?
4. Topology refresh vs connection validation race (#5308)
5. Java Lambda: response > 16KB? (#5301)

**When PubSub drifts**:
1. Use GET_SUBSCRIPTIONS to compare desired vs actual
2. Check `subscription_out_of_sync_count` metric
3. Verify reconciliation task running (log category "pubsub_synchronizer")
4. Check for topology changes causing slot migrations
5. Verify `pubsub_reconciliation_interval` is reasonable

**When tests are flaky**:
1. Replace sleep-based waits with polling + retry loops
2. Add finally-blocks for resource cleanup
3. Verify server health post-test (destructive tests)
4. Increase connection timeout in CI (Java: 10000ms)
5. ScriptKill: use connection timeout + cleanup (#5418)

### Common Issues

| Issue | Root Cause | Fix |
|-------|-----------|-----|
| Empty hostname in cluster | AWS ElastiCache returns `hostname: ""` | Falls back to IP (#5373) |
| IAM token manager leak | Reference cycle in callback | Break with weak refs (#5431) |
| Java Lambda 16KB hang | Direct-buffer path issue | Preserve scalar types (#5301) |
| JNI classloader failure | Cached methods invalid across classloaders | Fallback env (#5029) |
| Protobuf version conflict | Java dependency shading missing | Restore shading (#5031) |
| Logger panic on duplicate init | `init()` called twice | Use `try_init()` (#5129) |
| Topology refresh race | Concurrent with connection validation | Made mutually exclusive (#5308) |

### Error Hierarchies

**Python**: `GlideError` -> `ClosingError`, `RequestError` -> `TimeoutError`, `ExecAbortError`, `ConnectionError`, `ConfigurationError`

**Node.js**: `ValkeyError` -> `ClosingError`, `RequestError` -> `TimeoutError`, `ExecAbortError`, `ConnectionError`, `ConfigurationError`

**Java FFI**: `FFIError` with `Jni`, `Utf8`, `Logger`, `OpenTelemetry` variants

---

## 10. Performance Tuning

### Defaults That Matter

| Parameter | Default | Notes |
|-----------|---------|-------|
| Request timeout | 250ms | May be too short for complex operations |
| Connection timeout | 2000ms | Java tests use 10000ms |
| Inflight request limit | 1000 | 250 for Python+pubsub |
| Periodic connection check | 3 seconds | State check, not PING |
| Topology refresh | 60 seconds | With exponential backoff |
| Blocking command timeout extension | +0.5 seconds | Added to command's own timeout |
| TCP_NODELAY | enabled | Since v2.2.5 (#5100) |
| PubSub reconciliation | 3 seconds | Configurable |
| Compression min size | 64 bytes | Below this, overhead exceeds savings |

### Compression Trade-offs
- **Zstd** (default level 3): Better compression ratio, slightly higher CPU
- **LZ4** (default level 0): Faster, lower ratio
- Only SET/GET supported; other commands pass through uncompressed
- Level ranges: Zstd -131072 to 22, LZ4 -128 to 12

### Retry Strategy Defaults
- Formula: `factor * base^attempt` (100ms, 200ms, 400ms, 800ms, 1600ms)
- `factor`: 100ms, `exponent_base`: 2, `number_of_retries`: 5, `jitter_percent`: 20%
- Permanent errors skip retry: AuthenticationFailed, InvalidClientConfig, RESP3NotSupported, NOAUTH, WRONGPASS

---

## 11. Contribution Workflow

### Requirements
- **DCO Signoff**: `git commit -s -m "message"` (all commits)
- **Conventional Commits**: `<type>(<scope>): <description>`
  - Types: feat, fix, docs, style, refactor, test, chore
  - Scopes: java, python, node, go, core, ffi
- **GPG/SSH signed commits** (show as "Verified" on GitHub)

### PR Process
1. Fork and branch from latest `main`
2. Keep PRs focused on a single issue
3. Fill out PR template (Summary, Issue link, Testing, etc.)
4. All CI checks must pass before review
5. Feature branch -> main: **squash and merge**
6. Release branch -> main: **merge commit**

### Quality Gates
- Build passes: `make all`
- Lint passes: `make *-lint`
- Tests pass: `make *-test`
- No generated outputs committed
- DCO signoff present
- Conventional commit format
- Cross-language API consistency maintained

### Feature Flags (Rust)
- `proto` - Enables protobuf support
- `socket-layer` - Enables Unix socket IPC (depends on `proto`)
- `standalone_heartbeat` - PING heartbeat for standalone
- `mock-pubsub` - Mock pubsub for testing
- `iam_tests` - IAM integration tests
- Socket-layer NOT enabled by default; FFI users use `proto` only

---

## 12. Key Files Quick Reference

### Core Rust

| File | Purpose |
|------|---------|
| `glide-core/src/client/mod.rs` | Main Client struct, send_command, lazy init |
| `glide-core/src/client/types.rs` | Connection config types, protobuf conversion |
| `glide-core/src/client/reconnecting_connection.rs` | Auto-reconnect with backoff |
| `glide-core/src/client/standalone_client.rs` | Standalone mode with replica routing |
| `glide-core/src/client/value_conversion.rs` | RESP value type conversion |
| `glide-core/src/socket_listener.rs` | Unix socket IPC server |
| `glide-core/src/rotating_buffer.rs` | Varint-prefixed protobuf parser |
| `glide-core/src/request_type.rs` | Command enum (500+ commands) |
| `glide-core/src/compression.rs` | Transparent Zstd/LZ4 compression |
| `glide-core/src/pubsub/synchronizer.rs` | PubSub reconciliation engine |
| `glide-core/src/iam/mod.rs` | AWS IAM token management |
| `glide-core/src/scripts_container.rs` | Global Lua script cache |

### Forked redis-rs

| File | Purpose |
|------|---------|
| `glide-core/redis-rs/redis/src/cluster_routing.rs` | Slot calculation, routing rules |
| `glide-core/redis-rs/redis/src/cluster_slotmap.rs` | SlotMap with round-robin replica selection |
| `glide-core/redis-rs/redis/src/cluster_async/mod.rs` | Async cluster with MOVED/ASK |

### Language Wrappers

| File | Purpose |
|------|---------|
| `python/glide-async/src/lib.rs` | PyO3 bindings |
| `python/glide-sync/glide_sync/_glide_ffi.py` | CFFI bindings |
| `java/src/lib.rs` | JNI bridge, RegistryMethodCache |
| `node/rust-client/src/lib.rs` | NAPI v2 bindings |
| `go/base_client.go` | Go client implementation |
| `go/callbacks.go` | CGO callback handling |
| `ffi/src/lib.rs` | C FFI for Go/Python sync |

### Configuration & Contributing

| File | Purpose |
|------|---------|
| `AGENTS.md` | AI agent build/test instructions |
| `CLAUDE.md` | Claude-specific project context |
| `CONTRIBUTING.md` | Contribution guidelines |
| `SUBMITTING_PRS.md` | PR workflow |
| `REVIEWING_PRS.md` | PR review process |
| `CHANGELOG.md` | Version history |
| `Makefile` | Top-level build orchestration |

---

## Appendix: Rust Patterns Used

### Async Patterns
- `tokio::task::spawn` for background reconnection (detached lifecycle)
- `Box::pin(async move { ... })` for `RedisFuture` return type
- `Arc<RwLock<ClientWrapper>>` for thread-safe mutable client access
- `Weak<RwLock<ClientWrapper>>` for pubsub synchronizer (avoids reference cycles)
- `futures::stream::buffer_unordered` for concurrent connection setup
- `Arc::downgrade()` in spawned tasks to allow cleanup when owner drops

### FFI Memory Patterns
- `Box::into_raw` / `Box::from_raw` for heap objects crossing FFI boundary
- `ManuallyDrop` to prevent double-free across boundaries
- `CommandResponse` C repr struct with explicit pointer fields
- Caller responsible for freeing via `free_command_response`
- `ScriptHashBuffer` with manual memory management

### Global Containers
Two `Lazy<Mutex<HashMap>>` globals:
1. **Scripts container**: SHA1-keyed, reference-counted Lua scripts
2. **Cluster scan container**: nanoid-keyed ScanStateRC cursors

Both exist to prevent Rust GC from cleaning up objects referenced by language wrappers.

### Security
- Socket file: `<tempdir>/glide-socket-<uuid>` with 0o600 permissions
- Socket directory: 0o700 (owner only)
- Passwords sanitized as `[sanitized]` in debug logs
- TLS via rustls with aws-lc-rs backend (FIPS-aligned)
- mTLS supported (client_cert + client_key) since v2.3
- TCP_NODELAY enabled by default since v2.2.5
