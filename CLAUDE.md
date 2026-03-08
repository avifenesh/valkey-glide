# CLAUDE.md

Valkey GLIDE is an official open-source client library for Valkey and Redis OSS. It uses a core driver written in Rust with language-specific wrappers for Python, Java, Node.js, and Go, and other languages in separate repositories.

## Hard Constraints (non-negotiable)
- NO task completion without tests covering it and passing
- NO PR creation without all subagent feedback addressed
- NEVER assume - always verify with tests and benchmarks
- NEVER ignore bugs, even out of scope - open an issue

## Rules
### Always
- Direct and concise, no compliments or apologies
- Ask if unsure, stop and reassess if looping
- Fetch web resources fresh, don't rely on cached data
- If asked to do X a certain way, do it that way. Disagree? Raise it, but don't change without approval

### When Writing
- Commit frequently with meaningful messages - git is our diary
- Focus on one language + its core bindings. Core changes = consider all bindings
- AI agent files: context-efficient, use XML for structured data in Markdown
- Keep PRs small and focused, split if too large

### Before Task Completion
- Tests cover it and pass
- Linter/formatter ran for changed languages

### Before Push
- `git pull --rebase upstream main`, resolve conflicts
- Exception: if on a feature branch rebasing onto another feature branch, rebase onto that branch instead
- Run tests for relevant scope

### Before PR Creation/Update
- Run subagents in parallel, address all feedback:
    - performance analysis
    - code quality, style, best practices
    - test coverage
    - documentation clarity
    - security vulnerabilities and edge cases

## What the user cares about (all equally important):
- Performance - low latency, high throughput
- Reliability - robust error handling, edge cases
- Usability - clear APIs, good documentation, best DX
- Maintainability - clean code, modular design, tests, simplicity
- Correctness - verify with tests and benchmarks, not assumptions

---

## Commit & PR Requirements

All commits **must** include:
1. **DCO signoff**: `git commit -s -m "feat(scope): message"` (adds `Signed-off-by:` line)
2. **Cryptographic signature**: `git commit -S -s -m "feat(scope): message"` (shows "Verified" on GitHub)
3. **Conventional Commits format**: `<type>(<scope>): <description>`
   - Types: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`
   - Scopes: `core`, `java`, `python`, `node`, `go`, `ffi`, `jedis-compatibility`
   - Example: `feat(java): implement CLUSTER SCAN command`

Missing DCO signoff or signature will fail CI checks.

---

## Build & Test Quick Reference

<build-commands>
  <language name="rust-core">
    <build>cd glide-core &amp;&amp; cargo build --release</build>
    <test>cd glide-core &amp;&amp; cargo test</test>
    <lint>cd glide-core &amp;&amp; cargo clippy --all-features --all-targets -- -D warnings</lint>
    <format>cd glide-core &amp;&amp; cargo fmt --all</format>
  </language>
  <language name="java">
    <build>cd java &amp;&amp; ./gradlew :client:buildAllRelease</build>
    <test>cd java &amp;&amp; ./gradlew :integTest:test</test>
    <unit-test>cd java &amp;&amp; ./gradlew :client:test</unit-test>
    <lint>cd java &amp;&amp; ./gradlew :spotlessApply</lint>
    <check>cd java &amp;&amp; ./gradlew :spotlessCheck</check>
  </language>
  <language name="python">
    <build>cd python &amp;&amp; python3 dev.py build --mode release</build>
    <test>cd python &amp;&amp; python3 dev.py test</test>
    <lint>cd python &amp;&amp; python3 dev.py lint</lint>
    <protobuf>cd python &amp;&amp; python3 dev.py protobuf</protobuf>
  </language>
  <language name="node">
    <build>cd node &amp;&amp; npm install &amp;&amp; npm run build:release</build>
    <test>cd node &amp;&amp; npm test</test>
    <lint>cd node &amp;&amp; npm run lint:fix</lint>
    <protobuf>cd node &amp;&amp; npm run build-protobuf</protobuf>
  </language>
  <language name="go">
    <build>cd go &amp;&amp; make build</build>
    <test>cd go &amp;&amp; make integ-test</test>
    <unit-test>cd go &amp;&amp; make unit-test</unit-test>
    <lint>cd go &amp;&amp; make lint</lint>
    <format>cd go &amp;&amp; make format</format>
    <protobuf>cd go &amp;&amp; make generate-protobuf</protobuf>
  </language>
  <language name="top-level">
    <build-all>make all</build-all>
    <check-server>which valkey-server || which redis-server</check-server>
    <note>All integration tests require a running Valkey/Redis server. Use utils/cluster_manager.py to start one.</note>
  </language>
</build-commands>

---

## Project Structure

```
glide-core/     # Rust core driver - handles connection, protocol, clustering
python/         # Python client wrapper (async via PyO3, sync via CFFI)
java/           # Java client wrapper (JNI) + Jedis compatibility layer
node/           # Node.js client wrapper (NAPI v2)
go/             # Go client wrapper (CGO/FFI)
ffi/            # Foreign function interface for Go and Python sync
logger_core/    # Rust logging infrastructure
utils/          # Test utilities and cluster management scripts
benchmarks/     # Performance benchmarks
examples/       # Usage examples for each language
docs/           # Documentation
```

## Architecture: Language Bindings to Core

| Language     | Mechanism | Native Library              | Communication      |
|--------------|-----------|-----------------------------|--------------------|
| Python Async | PyO3      | valkey-glide (python/glide-async/) | Unix socket IPC    |
| Python Sync  | CFFI      | glide-ffi (ffi/)            | FFI calls          |
| Java         | JNI       | glide-rs (java/)            | JNI calls          |
| Go           | CGO       | glide-ffi (ffi/)            | FFI calls          |
| Node.js      | NAPI v2   | rust-client (node/rust-client/)  | Unix socket IPC    |

Socket IPC wrappers → `socket_listener` → `glide-core` → Valkey/Redis
FFI wrappers → `glide-core` → Valkey/Redis

---

## Adding a New Command (Cookbook)

This is the most common task. Follow these steps in order:

### Step 1: Protobuf Definition
**File:** `glide-core/src/protobuf/command_request.proto`
- Add enum variant to `RequestType` with a unique numeric ID
- IDs are grouped by category in 100s: bitmap=100s, cluster=200s, connection=300s, generic=400s, hash=500s, hyper=600s, list=700s, pubsub=800s, scripting=900s, server=1000s, set=1100s, sorted-set=1200s, stream=1300s, string=1400s, transaction=1500s

### Step 2: Rust RequestType Enum
**File:** `glide-core/src/request_type.rs`
- Add matching `#[repr(C)]` enum variant with same numeric ID
- Add `get_command()` match arm mapping to the actual Redis/Valkey command string

### Step 3: Core Client Logic (if needed)
**File:** `glide-core/src/client/mod.rs`
- Most commands use the default dispatch path and need no changes here
- Only add handling if the command needs special routing, multi-slot logic, or response transformation

### Step 4: Response Conversion (if needed)
**File:** `glide-core/src/client/value_conversion.rs`
- Add conversion logic if the command returns a non-standard type that needs coercion

### Step 5: Language Wrapper Implementation
Implement in the target language wrapper. Each language follows the same pattern: define the method on the client class, construct the protobuf request, and return the typed response.

<command-impl-files>
  <language name="java">
    <interface>java/client/src/main/java/glide/api/commands/ (add to relevant *BaseCommands interface)</interface>
    <client>java/client/src/main/java/glide/api/BaseClient.java (implement method)</client>
    <standalone>java/client/src/main/java/glide/api/GlideClient.java (standalone-only methods)</standalone>
    <cluster>java/client/src/main/java/glide/api/GlideClusterClient.java (cluster-only methods)</cluster>
    <batch>java/client/src/main/java/glide/api/models/BaseBatch.java (add batch support)</batch>
    <unit-test>java/client/src/test/java/glide/ (GlideClientTest / GlideClusterClientTest)</unit-test>
    <integ-test>java/integTest/src/test/java/glide/ (add to relevant test suite)</integ-test>
  </language>
  <language name="python">
    <commands>python/glide-shared/glide_shared/commands/ (add to relevant mixin)</commands>
    <async-client>python/glide-async/python/glide/glide_client.py</async-client>
    <sync-commands>python/glide-sync/glide_sync/sync_commands/ (sync version)</sync-commands>
    <tests>python/tests/ (add to relevant test file)</tests>
  </language>
  <language name="node">
    <commands>node/src/Commands.ts (add command builder function)</commands>
    <client>node/src/BaseClient.ts (add client method)</client>
    <tests>node/tests/ (add to relevant test file)</tests>
  </language>
  <language name="go">
    <client>go/base_client.go (implement method on baseClient)</client>
    <standalone>go/glide_client.go (standalone-only methods)</standalone>
    <cluster>go/glide_cluster_client.go (cluster-only methods)</cluster>
    <tests>go/integTest/ (add to relevant test suite)</tests>
  </language>
</command-impl-files>

### Step 6: Regenerate Protobuf
After changing `.proto` files, regenerate for each language you're touching:
- **Java**: Gradle picks up changes automatically on build
- **Python**: `cd python && python3 dev.py protobuf`
- **Node**: `cd node && npm run build-protobuf`
- **Go**: `cd go && make generate-protobuf`

### Step 7: Tests
- Add unit tests for argument construction
- Add integration tests that exercise the command against a live server
- Test both standalone and cluster clients
- Include edge cases: empty args, wrong types, server version gating

---

## Cross-Language Change Checklist

When modifying `glide-core/` or `ffi/`, changes propagate to all languages. Use this checklist:

- [ ] Protobuf schema changes regenerated for all affected languages
- [ ] `request_type.rs` enum and `get_command()` match arm both updated
- [ ] Socket IPC path tested (Python async, Node.js)
- [ ] FFI path tested (Python sync, Go, Java)
- [ ] Error type changes reflected in all language wrappers
- [ ] Build order respected: Rust core → protobuf regen → language build → tests

---

## Error Flow

```
Valkey/Redis → redis-rs → RedisError
  → glide-core/src/errors.rs (RequestErrorType: Unspecified|ExecAbort|Timeout|Disconnect)
    → Socket IPC: error string via protobuf response.proto
      → Python async: GlideError subclasses
      → Node.js: Error objects with type/message
    → FFI: CommandResponse with error pointer
      → Java: JNI exception bridge (java/src/errors.rs)
      → Go: standard error interface
      → Python sync: CFFI error string
```

---

## Key Constants & Defaults

| Constant | Value | File |
|----------|-------|------|
| Response timeout | 250ms | `glide-core/src/client/mod.rs` |
| Connection timeout | 2000ms | `glide-core/src/client/mod.rs` |
| Max retries | 3 | `glide-core/src/client/mod.rs` |
| Max inflight requests | 1000 | `glide-core/src/client/mod.rs` |
| Heartbeat interval | 1s | `glide-core/src/client/mod.rs` |
| Connection check interval | 3s | `glide-core/src/client/mod.rs` |

---

## Context Retrieval

<context-sources>
  <language name="python-async">
    <triggers>python async, PyO3, glide-async, socket IPC python</triggers>
    <start-with>python/glide-async/src/lib.rs</start-with>
    <depends-on>core</depends-on>
    <entry>python/glide-async/Cargo.toml</entry>
    <bindings>python/glide-async/src/lib.rs</bindings>
    <client>python/glide-async/python/glide/glide_client.py</client>
    <commands>python/glide-shared/glide_shared/commands/</commands>
    <tests>python/tests/</tests>
    <dev-cli>python/dev.py</dev-cli>
  </language>
  <language name="python-sync">
    <triggers>python sync, CFFI, glide-sync, synchronous python</triggers>
    <start-with>python/glide-sync/glide_sync/_glide_ffi.py</start-with>
    <depends-on>ffi</depends-on>
    <entry>python/glide-sync/setup.py</entry>
    <bindings>python/glide-sync/glide_sync/_glide_ffi.py</bindings>
    <client>python/glide-sync/glide_sync/glide_client.py</client>
    <commands>python/glide-sync/glide_sync/sync_commands/</commands>
    <tests>python/tests/</tests>
    <dev-cli>python/dev.py</dev-cli>
  </language>
  <language name="java">
    <triggers>java, JNI, glide-rs, Java client</triggers>
    <start-with>java/src/lib.rs</start-with>
    <depends-on>core</depends-on>
    <entry>java/Cargo.toml</entry>
    <bindings>java/src/lib.rs</bindings>
    <client>java/client/src/main/java/glide/api/BaseClient.java</client>
    <commands>java/client/src/main/java/glide/api/commands/</commands>
    <batch>java/client/src/main/java/glide/api/models/BaseBatch.java</batch>
    <errors>java/src/errors.rs</errors>
    <tests>java/client/src/test/java/glide/</tests>
    <integ-tests>java/integTest/src/test/java/glide/</integ-tests>
    <jedis-compat>java/jedis-compatibility/</jedis-compat>
  </language>
  <language name="node">
    <triggers>node, nodejs, NAPI, typescript, rust-client node</triggers>
    <start-with>node/rust-client/src/lib.rs</start-with>
    <depends-on>core</depends-on>
    <entry>node/rust-client/Cargo.toml</entry>
    <bindings>node/rust-client/src/lib.rs</bindings>
    <client>node/src/BaseClient.ts</client>
    <commands>node/src/Commands.ts</commands>
    <tests>node/tests/</tests>
  </language>
  <language name="go">
    <triggers>go, golang, CGO, go client</triggers>
    <start-with>go/base_client.go</start-with>
    <depends-on>ffi</depends-on>
    <entry>go/Makefile</entry>
    <bindings>go/callbacks.go</bindings>
    <client>go/base_client.go</client>
    <standalone>go/glide_client.go</standalone>
    <cluster>go/glide_cluster_client.go</cluster>
    <tests>go/integTest/</tests>
  </language>
  <language name="core">
    <triggers>glide-core, rust core, socket listener, protocol, clustering</triggers>
    <start-with>glide-core/src/client/mod.rs</start-with>
    <entry>glide-core/Cargo.toml</entry>
    <client>glide-core/src/client/mod.rs</client>
    <request-types>glide-core/src/request_type.rs</request-types>
    <value-conversion>glide-core/src/client/value_conversion.rs</value-conversion>
    <errors>glide-core/src/errors.rs</errors>
    <socket>glide-core/src/socket_listener.rs</socket>
    <protobuf>glide-core/src/protobuf/</protobuf>
    <reconnection>glide-core/src/client/reconnecting_connection.rs</reconnection>
    <standalone>glide-core/src/client/standalone_client.rs</standalone>
    <compression>glide-core/src/compression.rs</compression>
    <tests>glide-core/tests/</tests>
  </language>
  <language name="ffi">
    <triggers>ffi, foreign function interface, C bindings, libglide</triggers>
    <start-with>ffi/src/lib.rs</start-with>
    <entry>ffi/Cargo.toml</entry>
    <bindings>ffi/src/lib.rs</bindings>
  </language>
</context-sources>

## Common Pitfalls

- Forgetting `--features "proto,socket-layer"` when building glide-core standalone (socket listener won't compile)
- Running integration tests without a Valkey/Redis server running (tests hang or timeout)
- Not regenerating protobuf after `.proto` changes (build succeeds but new commands are missing)
- Editing Java commands without checking the Jedis compatibility interfaces need updating too
- Forgetting license headers in new Go files: `// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0`
- Missing DCO signoff on commits (CI will reject the PR)
- Not respecting build order: Rust core must build before language wrappers
