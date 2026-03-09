# AGENTS: Unified Context for Agentic Tools

This file provides AI agents with the minimum but sufficient context to work productively in the Valkey GLIDE mono-repository. It covers build commands, contribution requirements, command implementation patterns, and essential guardrails for maintaining code quality across multiple language bindings.

## Repository Overview

This is the Valkey GLIDE mono-repository containing a Rust core (`glide-core`) and FFI layer used to build first-class Valkey/Redis clients with multi-language bindings. The repository implements the General Language Independent Driver for the Enterprise (GLIDE) for Valkey and Redis OSS.

**Primary Languages Present:** Rust, Java, Python, Node.js/TypeScript, Go

**Key Components:**
- `glide-core/` - Core Rust implementation with async client logic
- `ffi/` - Foreign Function Interface layer for language interoperability
- `java/` - Java client bindings with Gradle build system + Jedis compatibility layer
- `python/` - Python async (PyO3) and sync (CFFI) client bindings
- `node/` - Node.js/TypeScript client bindings with NAPI v2
- `go/` - Go client bindings via CGO/FFI
- `logger_core/` - Shared logging infrastructure
- `utils/` - Shared utilities and cluster management tools
- `benchmarks/` - Performance benchmarks across languages
- `examples/` - Usage examples for each language binding
- `docs/` - Documentation and MkDocs configuration

## Architecture Quick Facts

**Core Implementation:** Rust (`glide-core`) with two IPC paths to language adapters:
- **Socket IPC** (Python async, Node.js): Protobuf messages over Unix domain socket
- **FFI** (Java JNI, Go CGO, Python sync CFFI): Direct C function calls with `CommandResponse` struct

**Design Constraints:** Async-first APIs, cluster-aware routing, batching support, cross-AZ affinity
**Key Features:** Multi-slot command handling, PubSub auto-reconnection, cluster scan, OpenTelemetry integration, transparent compression (Zstd/LZ4), IAM authentication

**Supported Engine Versions:**
| Engine Type | 6.2 | 7.0 | 7.1 | 7.2 | 8.0 | 8.1 |
|-------------|-----|-----|-----|-----|-----|-----|
| Valkey      | -   | -   | -   | ✓   | ✓   | ✓   |
| Redis       | ✓   | ✓   | ✓   | ✓   | -   | -   |

## Build and Test Rules (Agents)

### Preferred (Make Targets)
```bash
# Build all language bindings
make all

# Individual language builds
make java          # Build Java client (release mode)
make python        # Build Python async + sync clients (release mode)
make node          # Build Node.js client (release mode)
make go            # Build Go client

# Testing (requires running Valkey/Redis server)
make java-test     # Run Java integration tests
make python-test   # Run Python tests
make node-test     # Run Node.js tests
make go-test       # Run Go tests

# Linting
make java-lint     # Run Java spotlessApply
make python-lint   # Run Python linters via dev.py
make node-lint     # Run Node.js linters
make go-lint       # Run Go linters

# Formatting
make prettier-check  # Check Prettier formatting
make prettier-fix    # Fix Prettier formatting

# Utilities
make clean         # Remove .build/ directory
make help          # List available targets
```

### Raw Equivalents Per Stack

**Rust (glide-core):**
```bash
cd glide-core
cargo build --release
cargo test
cargo bench
cargo clippy
cargo fmt
```

**Java:**
```bash
cd java
./gradlew :client:buildAllRelease       # Build
./gradlew :client:test                   # Unit tests
./gradlew :integTest:test                # Integration tests
./gradlew :integTest:pubsubTest          # PubSub tests
./gradlew :integTest:test --tests 'methodName' --rerun  # Single test
./gradlew :spotlessApply                 # Lint/format
```

**Python:**
```bash
cd python
python3 dev.py build --mode release
python3 dev.py test
python3 dev.py lint
```

**Node.js/TypeScript:**
```bash
cd node
npm install
npm run build:release
npm test
npx eslint --fix .
```

**Go:**
```bash
cd go
make build
make test
make lint
```

**Benchmarks:**
```bash
# Rust benchmarks
cd glide-core && cargo bench

# Cross-language benchmarks
cd benchmarks && ./install_and_test.sh
```

## Command Implementation Pattern

The most common development task is adding new Valkey/Redis commands. The flow:

1. **Protobuf** (`glide-core/src/protobuf/command_request.proto`): Add `RequestType` enum variant
2. **Rust core** (`glide-core/src/request_type.rs`): Add enum variant + `get_command()` mapping
3. **Language bindings**: Add interface method + implementation + tests in each language:
   - **Java**: Interface in `java/client/src/main/java/glide/api/commands/`, impl in `BaseClient.java`
   - **Python**: Method in `python/glide-async/python/glide/async_commands/core.py`
   - **Node.js**: Factory in `node/src/Commands.ts`, method in `node/src/BaseClient.ts`
   - **Go**: Interface in `go/internal/interfaces/`, impl in `go/base_client.go`
4. **Jedis compatibility** (if applicable): `java/jedis-compatibility/src/main/java/redis/clients/jedis/Jedis.java`
5. **Tests**: Unit + integration tests in each language, covering standalone + cluster modes

**Key rule:** Core/protobuf changes affect ALL language bindings. Single-language changes need only that language's tests.

## Contribution Requirements

### Developer Certificate of Origin (DCO) Signoff REQUIRED

All commits must include a `Signed-off-by` line:

```bash
# Add signoff to new commits
git commit -s -m "feat: add new feature"

# Add signoff to existing commit
git commit --amend --signoff --no-edit
```

**Required format:** `Signed-off-by: Your Name <your.email@example.com>`

### Commit Signing REQUIRED

All commits must be cryptographically signed (GPG or SSH) to show as "Verified" on GitHub:

```bash
git commit -S -s -m "feat(java): add new command"
```

### Conventional Commits

```
<type>(<scope>): <description>
```

**Types:** `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`
**Scopes:** `java`, `python`, `node`, `go`, `core`, `ffi`
**Example:** `feat(java): add cluster scan support for Java client`

## Guardrails & Policies

### Generated Outputs (Never Commit)
- `target/` - Rust build artifacts
- `node_modules/` - Node.js dependencies
- `.build/` - Make build cache
- `debug/` - Debug builds
- `generated/` - Generated protobuf files
- `benchmarks/results/` - Benchmark output
- `python/.env*` - Python virtual environments
- `*.class` - Java compiled files

### Cross-Language Changes
- Follow semantic versioning for breaking changes
- Test changes across affected language bindings
- Verify API consistency: same command name, same parameter order, same return type semantics

### Security & Code Quality
- Never commit secrets, credentials, or API keys
- Follow SECURITY.md for vulnerability reporting
- Run lint/format targets before committing
- Maintain compatibility with supported engine versions
- Do not modify vendored or third-party code

## Error Handling

All language bindings share a consistent error hierarchy:

| Error Type | When | Retryable? |
|---|---|---|
| `ClosingError` | Client closed, no longer usable | No |
| `RequestError` | General command execution failure | Depends |
| `TimeoutError` | Request exceeded timeout | Yes |
| `ExecAbortError` | Transaction was aborted | Depends |
| `ConnectionError` | Connection lost (auto-reconnect may recover) | Yes |
| `ConfigurationError` | Invalid client configuration | No |

Error files per language:
- **Rust**: `glide-core/src/errors.rs`
- **Proto**: `glide-core/src/protobuf/response.proto`
- **Java**: `java/client/src/main/java/glide/api/models/exceptions/`
- **Python**: `python/glide-shared/glide_shared/exceptions.py`
- **Node.js**: `node/src/Errors.ts`
- **Go**: `go/errors.go`

## Debugging Quick Reference

| Symptom | Likely Cause | Where to Look |
|---|---|---|
| PubSub messages not received | Subscription drift after topology change | `glide-core/src/pubsub/synchronizer.rs`, check `subscription_out_of_sync_count` metric |
| Connection hangs | Inflight limit (1000) reached or topology refresh race | `glide-core/src/client/reconnecting_connection.rs` |
| Java large response corruption | 16KB JNI Lambda limit | `java/src/lib.rs` — uses direct-buffer path for large arrays |
| Empty hostname in cluster | AWS ElastiCache returning `hostname: ""` | Falls back to IP address automatically |
| Memory leak in async subsystem | Reference cycle in callbacks | Use `Weak` refs in PubSub synchronizer and IAM token manager |
| Flaky tests | Using `sleep()` instead of polling | Replace with polling + retry + timeout, add `finally`/`defer` cleanup |

## Quality Gates (Agent Checklist)

- [ ] Build passes for changed languages
- [ ] Lint passes: `make *-lint` targets succeed
- [ ] Tests pass: unit + integration for changed scope
- [ ] No generated outputs committed (check `.gitignore`)
- [ ] DCO signoff present: `git log --format="%B" -n 1 | grep "Signed-off-by"`
- [ ] Commit is cryptographically signed
- [ ] Conventional commit format used
- [ ] Cross-language API consistency maintained (if core change)
- [ ] Both standalone and cluster modes tested (if adding command)
- [ ] Batch/Transaction support added (if command is pipelineable)

## Quick Facts for Reasoners

**Engines Supported:** Valkey 7.2, 8.0, 8.1 | Redis 6.2, 7.0, 7.1, 7.2
**Key Features:** AZ Affinity, PubSub auto-reconnection, sharded PubSub, cluster-aware multi-key commands, cluster scan, batching (pipeline/transaction), OpenTelemetry, compression (Zstd/LZ4), IAM auth
**Architecture:** Rust core with FFI bindings, async-first design, cluster and standalone support
**Performance:** Optimized for high throughput and low latency with connection pooling

## If You Need More

- **Getting Started:** [README.md](./README.md)
- **Contributing:** [CONTRIBUTING.md](./CONTRIBUTING.md)
- **Submitting PRs:** [SUBMITTING_PRS.md](./SUBMITTING_PRS.md)
- **Reviewing PRs:** [REVIEWING_PRS.md](./REVIEWING_PRS.md)
- **Security:** [SECURITY.md](./SECURITY.md)
- **Documentation:** [docs/README.md](./docs/README.md)
- **Examples:** [examples/](./examples/)
- **Language-Specific Guides:**
  - [Java Developer Guide](./java/DEVELOPER.md)
  - [Python Developer Guide](./python/DEVELOPER.md)
  - [Node.js Developer Guide](./node/DEVELOPER.md)
  - [Go Developer Guide](./go/DEVELOPER.md)
