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
docs/           # Documentation (MkDocs)
```

## Architecture: Language Bindings to Core

| Language     | Mechanism | Native Library              | Communication      |
|--------------|-----------|-----------------------------|--------------------|
| Python Async | PyO3      | valkey-glide (python/glide-async/) | Unix socket IPC    |
| Python Sync  | CFFI      | glide-ffi (ffi/)            | FFI calls          |
| Java         | JNI       | glide-rs (java/)            | JNI calls          |
| Go           | CGO       | glide-ffi (ffi/)            | FFI calls          |
| Node.js      | NAPI v2   | rust-client (node/rust-client/)  | Unix socket IPC    |

```
Socket IPC path:  Language wrapper → protobuf → socket_listener → glide-core → Valkey/Redis
FFI path:         Language wrapper → C FFI call → glide-core → Valkey/Redis
```

## New Command Implementation Workflow

When adding a new Valkey/Redis command, touch these files in order:

<command-workflow>
  <step order="1" name="Protobuf definition">
    <file>glide-core/src/protobuf/command_request.proto</file>
    <action>Add RequestType enum variant with next available ID in the correct category group (Bitmap=1xx, Cluster=2xx, Connection=3xx, String=4xx, Generic=5xx, Set=6xx, Hash=7xx, List=8xx, SortedSet=9xx, Stream=10xx, etc.)</action>
  </step>
  <step order="2" name="Rust request type">
    <file>glide-core/src/request_type.rs</file>
    <action>Add enum variant, implement get_command() to return the Redis command (Cmd), and map from protobuf type</action>
  </step>
  <step order="3" name="Language bindings" note="for each target language">
    <java>
      <interface>java/client/src/main/java/glide/api/commands/{Category}BaseCommands.java</interface>
      <impl>java/client/src/main/java/glide/api/BaseClient.java</impl>
      <unit-test>java/client/src/test/java/glide/api/GlideClientTest.java</unit-test>
      <integ-test>java/integTest/src/test/java/glide/SharedCommandTests.java</integ-test>
      <note>Add Javadoc with @see link to valkey.io docs, @apiNote for cluster behavior</note>
    </java>
    <python>
      <async-commands>python/glide-async/python/glide/async_commands/core.py (or standalone_commands.py / cluster_commands.py)</async-commands>
      <shared-options>python/glide-shared/glide_shared/commands/ (for option types)</shared-options>
      <sync-commands>python/glide-sync/glide_sync/sync_commands/ (if sync client needed)</sync-commands>
      <integ-test>python/tests/</integ-test>
      <note>Commands use self._execute_command(RequestType.XXX, args). Add docstrings with examples.</note>
    </python>
    <node>
      <factory>node/src/Commands.ts (add createXxx() factory function)</factory>
      <client>node/src/BaseClient.ts (add method calling the factory)</client>
      <integ-test>node/tests/</integ-test>
      <note>Large args (>4KB) use createLeakedStringVec() for pointer passing</note>
    </node>
    <go>
      <interface>go/internal/interfaces/{category}_commands.go</interface>
      <impl>go/base_client.go</impl>
      <integ-test>go/integTest/shared_commands_test.go</integ-test>
      <note>Go uses FFI via C bindings in go/callbacks.go</note>
    </go>
  </step>
  <step order="4" name="Jedis compatibility (if applicable)">
    <file>java/jedis-compatibility/src/main/java/redis/clients/jedis/Jedis.java</file>
    <note>Only if the command exists in the Jedis API surface</note>
  </step>
  <step order="5" name="Batch/Transaction support">
    <action>Add command to batch/transaction classes in each language if the command supports pipelining</action>
  </step>
</command-workflow>

## Error Hierarchy (consistent across all languages)

```
GlideError / ValkeyError (base)
├── ClosingError          — client closed, no longer usable
└── RequestError          — error during command execution
    ├── TimeoutError      — request timed out
    ├── ExecAbortError    — transaction aborted
    ├── ConnectionError   — connection lost (may auto-reconnect)
    └── ConfigurationError — invalid configuration
```

<error-files>
  <rust>glide-core/src/errors.rs</rust>
  <protobuf>glide-core/src/protobuf/response.proto (RequestErrorType enum)</protobuf>
  <java>java/client/src/main/java/glide/api/models/exceptions/</java>
  <python>python/glide-shared/glide_shared/exceptions.py</python>
  <node>node/src/Errors.ts</node>
  <go>go/errors.go</go>
</error-files>

## Testing Quick Reference

<testing>
  <language name="java">
    <unit>cd java && ./gradlew :client:test</unit>
    <integration>cd java && ./gradlew :integTest:test</integration>
    <single>cd java && ./gradlew :integTest:test --tests 'testMethodName' --rerun</single>
    <pubsub>cd java && ./gradlew :integTest:pubsubTest</pubsub>
    <lint>cd java && ./gradlew :spotlessApply</lint>
    <prereq>Requires running Valkey/Redis server (check with: which valkey-server || which redis-server)</prereq>
  </language>
  <language name="python">
    <build>cd python && python3 dev.py build --mode release</build>
    <test>cd python && python3 dev.py test</test>
    <lint>cd python && python3 dev.py lint</lint>
  </language>
  <language name="node">
    <build>cd node && npm install && npm run build:release</build>
    <test>cd node && npm test</test>
    <lint>cd node && npx eslint --fix .</lint>
  </language>
  <language name="go">
    <build>cd go && make build</build>
    <test>cd go && make test</test>
    <lint>cd go && make lint</lint>
  </language>
  <language name="rust-core">
    <build>cd glide-core && cargo build --release</build>
    <test>cd glide-core && cargo test</test>
    <lint>cd glide-core && cargo clippy && cargo fmt --check</lint>
    <bench>cd glide-core && cargo bench</bench>
  </language>
  <language name="make-targets">
    <all>make all</all>
    <note>Use make targets for CI-equivalent builds: make java, make python, make node, make go</note>
  </language>
</testing>

## Key Subsystems

<subsystems>
  <subsystem name="PubSub Synchronizer">
    <file>glide-core/src/pubsub/synchronizer.rs</file>
    <desc>Observer pattern with eventual consistency. Reconciles desired vs actual subscriptions every 3s. Handles topology changes, slot migrations, lazy/blocking subscribe modes. Uses Weak refs to avoid reference cycles.</desc>
  </subsystem>
  <subsystem name="Compression">
    <file>glide-core/src/compression.rs</file>
    <desc>Transparent Zstd/LZ4 compression with 5-byte header (magic + version + backend ID). Minimum 64 bytes threshold. Supported in all language bindings.</desc>
  </subsystem>
  <subsystem name="IAM Auth">
    <file>glide-core/src/iam/</file>
    <desc>AWS SigV4 token generation for ElastiCache/MemoryDB. Auto-refresh with exponential backoff. Uses Weak refs to avoid reference cycles.</desc>
  </subsystem>
  <subsystem name="Socket Listener">
    <file>glide-core/src/socket_listener.rs</file>
    <desc>Unix domain socket IPC for Python async and Node.js. Protobuf message framing with rotating buffer.</desc>
  </subsystem>
  <subsystem name="Reconnection">
    <file>glide-core/src/client/reconnecting_connection.rs</file>
    <desc>Auto-reconnect with configurable exponential backoff. Inflight request limit: 1000 default.</desc>
  </subsystem>
  <subsystem name="Cluster Scan">
    <file>glide-core/src/cluster_scan_container.rs</file>
    <desc>Cursor management for distributed SCAN across cluster nodes.</desc>
  </subsystem>
  <subsystem name="Jedis Compatibility">
    <dir>java/jedis-compatibility/</dir>
    <desc>Drop-in Jedis API replacement wrapping GLIDE. Covers UnifiedJedis, Jedis, JedisPooled, JedisCluster. Maven: io.valkey:valkey-glide-jedis-compatibility.</desc>
  </subsystem>
  <subsystem name="OpenTelemetry">
    <dir>glide-core/telemetry/</dir>
    <desc>Metrics for timeouts, retries, MOVED errors, PubSub sync events. Supports gRPC/HTTP/file export. Span propagation via root_span_ptr in protobuf.</desc>
  </subsystem>
</subsystems>

## Common Pitfalls

<pitfalls>
  <pitfall name="Cross-language consistency">When adding a command to one language, check if it should be added to all others. Core (Rust/protobuf) changes always affect all bindings.</pitfall>
  <pitfall name="Flaky tests">Never use raw sleep() in tests. Use polling + retry with timeout. Always clean up resources in finally/defer blocks.</pitfall>
  <pitfall name="DCO signoff">All commits require DCO signoff: `git commit -s`. Missing signoff = CI failure. To fix: `git commit --amend --signoff --no-edit`.</pitfall>
  <pitfall name="Commit signing">All commits must be cryptographically signed (GPG or SSH) to show as "Verified" on GitHub.</pitfall>
  <pitfall name="Batch/Transaction">Commands in batch/transaction contexts may have different return types than standalone execution. Test both paths.</pitfall>
  <pitfall name="Cluster vs Standalone">Some commands behave differently in cluster mode (multi-slot, routing). Always test both modes. Use @apiNote in Java / docstring notes in Python to document cluster-specific behavior.</pitfall>
  <pitfall name="Large payloads">Node.js: args >4KB need createLeakedStringVec(). Java: JNI has 16KB Lambda limit for inline responses — large arrays use direct-buffer path.</pitfall>
  <pitfall name="Reference cycles">Async subsystems (PubSub synchronizer, IAM token manager) must use Weak refs in callbacks to avoid preventing client drop/cleanup.</pitfall>
</pitfalls>

## Context Retrieval

<context-sources>
  <language name="python-async">
    <triggers>python async, PyO3, glide-async, socket IPC python</triggers>
    <start-with>python/glide-async/src/lib.rs</start-with>
    <depends-on>core</depends-on>
    <entry>python/glide-async/Cargo.toml</entry>
    <bindings>python/glide-async/src/lib.rs</bindings>
    <client>python/glide-async/python/glide/glide_client.py</client>
    <commands>python/glide-async/python/glide/async_commands/core.py</commands>
    <standalone-commands>python/glide-async/python/glide/async_commands/standalone_commands.py</standalone-commands>
    <cluster-commands>python/glide-async/python/glide/async_commands/cluster_commands.py</cluster-commands>
    <shared-options>python/glide-shared/glide_shared/commands/</shared-options>
    <errors>python/glide-shared/glide_shared/exceptions.py</errors>
    <tests>python/tests/</tests>
  </language>
  <language name="python-sync">
    <triggers>python sync, CFFI, glide-sync, synchronous python</triggers>
    <start-with>python/glide-sync/glide_sync/_glide_ffi.py</start-with>
    <depends-on>ffi</depends-on>
    <entry>python/glide-sync/setup.py</entry>
    <bindings>python/glide-sync/glide_sync/_glide_ffi.py</bindings>
    <client>python/glide-sync/glide_sync/glide_client.py</client>
    <commands>python/glide-sync/glide_sync/sync_commands/</commands>
    <errors>python/glide-shared/glide_shared/exceptions.py</errors>
    <tests>python/tests/</tests>
  </language>
  <language name="java">
    <triggers>java, JNI, glide-rs, Java client</triggers>
    <start-with>java/src/lib.rs</start-with>
    <depends-on>core</depends-on>
    <entry>java/Cargo.toml</entry>
    <bindings>java/src/lib.rs</bindings>
    <clients>java/client/src/main/java/glide/api/BaseClient.java, GlideClient.java, GlideClusterClient.java</clients>
    <commands>java/client/src/main/java/glide/api/commands/</commands>
    <errors>java/client/src/main/java/glide/api/models/exceptions/</errors>
    <unit-tests>java/client/src/test/java/glide/api/</unit-tests>
    <integ-tests>java/integTest/src/test/java/glide/</integ-tests>
    <jedis-compat>java/jedis-compatibility/</jedis-compat>
    <tests>java/client/src/test/java/glide/</tests>
  </language>
  <language name="node">
    <triggers>node, nodejs, NAPI, typescript, rust-client node</triggers>
    <start-with>node/rust-client/src/lib.rs</start-with>
    <depends-on>core</depends-on>
    <entry>node/rust-client/Cargo.toml</entry>
    <bindings>node/rust-client/src/lib.rs</bindings>
    <client>node/src/BaseClient.ts</client>
    <commands>node/src/Commands.ts</commands>
    <errors>node/src/Errors.ts</errors>
    <tests>node/tests/</tests>
  </language>
  <language name="go">
    <triggers>go, golang, CGO, go client</triggers>
    <start-with>go/base_client.go</start-with>
    <depends-on>ffi</depends-on>
    <entry>go/Makefile</entry>
    <bindings>go/callbacks.go</bindings>
    <client>go/base_client.go</client>
    <commands>go/internal/interfaces/</commands>
    <errors>go/errors.go</errors>
    <tests>go/integTest/</tests>
  </language>
  <language name="core">
    <triggers>glide-core, rust core, socket listener, protocol, clustering</triggers>
    <start-with>glide-core/src/client/mod.rs</start-with>
    <entry>glide-core/Cargo.toml</entry>
    <client>glide-core/src/client/mod.rs</client>
    <request-types>glide-core/src/request_type.rs</request-types>
    <socket>glide-core/src/socket_listener.rs</socket>
    <errors>glide-core/src/errors.rs</errors>
    <reconnection>glide-core/src/client/reconnecting_connection.rs</reconnection>
    <protobuf>glide-core/src/protobuf/</protobuf>
    <compression>glide-core/src/compression.rs</compression>
    <pubsub>glide-core/src/pubsub/synchronizer.rs</pubsub>
    <tests>glide-core/tests/</tests>
  </language>
  <language name="ffi">
    <triggers>ffi, foreign function interface, C bindings, libglide</triggers>
    <start-with>ffi/src/lib.rs</start-with>
    <entry>ffi/Cargo.toml</entry>
    <bindings>ffi/src/lib.rs</bindings>
  </language>
</context-sources>

## Protobuf Wire Protocol

Three proto files define the client-core communication:

| Proto File | Purpose | Key Types |
|---|---|---|
| `command_request.proto` | Command encoding | `RequestType` enum (200+ commands), `Command`, routing info |
| `connection_request.proto` | Client configuration | `ConnectionRequest`, `ReadFrom`, `TlsMode`, `CompressionConfig`, `IamCredentials` |
| `response.proto` | Response handling | `Response`, `RequestError`, `RequestErrorType`, `ConstantResponse` |

**RequestType ID ranges:** Bitmap=1xx, Cluster=2xx, Connection=3xx, String=4xx, Generic=5xx, Set=6xx, Hash=7xx, List=8xx, SortedSet=9xx, Stream=10xx, HyperLogLog=11xx, Geo=12xx, Scripting=13xx, Server=14xx, PubSub=15xx.
