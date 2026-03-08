# AGENTS: Unified Context for Agentic Tools

This file provides AI agents with the minimum but sufficient context to work productively in the Valkey GLIDE mono-repository. It covers build commands, contribution requirements, and essential guardrails for maintaining code quality across multiple language bindings.

## Repository Overview

This is the Valkey GLIDE mono-repository containing a Rust core (`glide-core`) and FFI layer used to build first-class Valkey/Redis clients with multi-language bindings. The repository implements the General Language Independent Driver for the Enterprise (GLIDE) for Valkey and Redis OSS.

**Primary Languages Present:** Rust, Java, Python, Node.js/TypeScript, Go

**Key Components:**
- `glide-core/` - Core Rust implementation with async client logic
- `ffi/` - Foreign Function Interface layer for language interoperability
- `java/` - Java client bindings with Gradle build system
- `python/` - Python async/sync client bindings
- `node/` - Node.js/TypeScript client bindings with npm
- `go/` - Go client bindings
- `logger_core/` - Shared logging infrastructure
- `utils/` - Shared utilities and cluster management tools
- `benchmarks/` - Performance benchmarks across languages
- `examples/` - Usage examples for each language binding
- `docs/` - Documentation and MkDocs configuration

**Critical Files (start here for common tasks):**
- `glide-core/src/request_type.rs` - All 600+ command type mappings (enum + Redis command strings)
- `glide-core/src/client/mod.rs` - Main Client struct, command routing, lazy init
- `glide-core/src/client/value_conversion.rs` - Response type coercion logic
- `glide-core/src/errors.rs` - Error types that propagate to all languages
- `glide-core/src/protobuf/command_request.proto` - Protobuf command definitions
- `glide-core/src/socket_listener.rs` - Unix socket IPC for Python async / Node.js
- `ffi/src/lib.rs` - C FFI for Go / Python sync (CommandResponse struct)

## Architecture Quick Facts

**Core Implementation:** Rust (`glide-core`) with FFI exposure to language adapters
**Design Constraints:** Async-first APIs, cluster-aware routing, batching support, cross-AZ affinity
**Key Features:** Multi-slot command handling, PubSub auto-reconnection, cluster scan, OpenTelemetry integration

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

# Testing
make java-test     # Run Java integration tests
make python-test   # Run Python tests
make node-test     # Run Node.js tests
make go-test       # Run Go tests

# Linting
make java-lint     # Run Java spotlessApply
make python-lint   # Run Python linters via dev.py
make node-lint     # Run Node.js linters
make go-lint       # Run Go linters

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
./gradlew :client:buildAllRelease
./gradlew :integTest:test
./gradlew :spotlessApply
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
npx run lint:fix
```

**Go:**
```bash
cd go
make build
make test
make lint
go build ./...
go test ./...
```

**Benchmarks:**
```bash
# Rust benchmarks
cd glide-core && cargo bench

# Cross-language benchmarks
cd benchmarks && ./install_and_test.sh
```

**Test Results:** Stored in language-specific directories (`target/`, `build/`, `node_modules/`, etc.)

## Contribution Requirements

### Developer Certificate of Origin (DCO) Signoff REQUIRED

All commits must include a `Signed-off-by` line:

```bash
# Add signoff to new commits
git commit -s -m "feat: add new feature"

# Configure automatic signoff
git config --global format.signOff true

# Add signoff to existing commit
git commit --amend --signoff --no-edit

# Add signoff to multiple commits
git rebase -i HEAD~n --signoff
```

**Required format:** `Signed-off-by: Your Name <your.email@example.com>`

### Conventional Commits

Use conventional commit format for all commit messages:

```
<type>(<scope>): <description>

[optional body]

[optional footer(s)]
```

**Common types:** `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`

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
- Language-specific build directories per `.gitignore`

### Cross-Language Changes
- Follow semantic versioning for breaking changes
- Test changes across affected language bindings

### Security & Code Quality
- Never commit secrets, credentials, or API keys
- Follow SECURITY.md for vulnerability reporting
- Run lint/format targets before committing
- Maintain compatibility with supported engine versions
- Do not modify vendored or third-party code

## Project Structure (Essential)

```
valkey-glide/
├── glide-core/          # Core Rust implementation
├── ffi/                 # Foreign Function Interface layer
├── java/                # Java client bindings (Gradle)
├── python/              # Python async/sync bindings
├── node/                # Node.js/TypeScript bindings (npm)
├── go/                  # Go client bindings
├── logger_core/         # Shared logging infrastructure
├── utils/               # Cluster management and utilities
├── benchmarks/          # Performance benchmarks
├── examples/            # Usage examples per language
├── docs/                # Documentation (MkDocs)
├── .github/workflows/   # CI/CD pipelines
└── Makefile            # Top-level build orchestration
```

## Adding a New Command (Cross-Language Workflow)

The most common task. Changes flow top-down through these layers:

```
1. glide-core/src/protobuf/command_request.proto  → Add RequestType enum value (unique ID by category)
2. glide-core/src/request_type.rs                 → Add #[repr(C)] enum + get_command() match arm
3. glide-core/src/client/mod.rs                   → Only if special routing/response handling needed
4. glide-core/src/client/value_conversion.rs       → Only if non-standard response type conversion
5. Language wrapper (client method + command builder + tests)
6. Regenerate protobuf for each affected language
```

**RequestType ID ranges:** bitmap=100s, cluster=200s, connection=300s, generic=400s, hash=500s, hyper=600s, list=700s, pubsub=800s, scripting=900s, server=1000s, set=1100s, sorted-set=1200s, stream=1300s, string=1400s, transaction=1500s

**Protobuf regeneration after .proto changes:**
- Java: automatic on `./gradlew build`
- Python: `cd python && python3 dev.py protobuf`
- Node: `cd node && npm run build-protobuf`
- Go: `cd go && make generate-protobuf`

## Cross-Language Change Propagation

When modifying `glide-core/` or `ffi/`, changes affect all bindings:

| Change Type | Affects | Action Required |
|-------------|---------|-----------------|
| `.proto` schema | All languages | Regenerate protobuf, update all wrappers |
| `request_type.rs` | All languages | Ensure enum IDs match proto, update FFI |
| `errors.rs` | All languages | Update error handling in each wrapper |
| `socket_listener.rs` | Python async, Node.js | Test socket IPC path |
| `ffi/src/lib.rs` | Go, Python sync, Java | Test FFI/JNI/CGO path |
| `value_conversion.rs` | All languages | Verify response types in each wrapper |

**Build order (must be respected):**
1. Rust core (`glide-core/`, `ffi/`)
2. Protobuf regeneration
3. Language-specific build
4. Tests

## Error Flow Architecture

```
Valkey/Redis server error
  → redis-rs (forked at glide-core/redis-rs/)
    → glide-core/src/errors.rs → RequestErrorType {Unspecified, ExecAbort, Timeout, Disconnect}
      → Socket IPC path (response.proto): error string in protobuf response
        → Python async: GlideError subclasses
        → Node.js: Error objects with type + message
      → FFI path: CommandResponse with error pointer
        → Java: JNI exception (java/src/errors.rs bridge)
        → Go: standard error interface
        → Python sync: CFFI error string
```

## Quality Gates (Agent Checklist)

- [ ] Build passes: `make all` succeeds
- [ ] Lint passes: `make *-lint` targets succeed
- [ ] Tests pass: `make *-test` targets succeed
- [ ] No generated outputs committed (check `.gitignore`)
- [ ] DCO signoff present: `git log --format="%B" -n 1 | grep "Signed-off-by"`
- [ ] Commit cryptographically signed (shows "Verified" on GitHub)
- [ ] Conventional commit format used
- [ ] Cross-language API consistency maintained
- [ ] Protobuf regenerated for all affected languages after .proto changes
- [ ] Security scan passes (no secrets committed)

## Quick Facts for Reasoners

**Engines Supported:** Valkey 7.2, 8.0, 8.1, 9.0+ | Redis 6.2, 7.0, 7.1, 7.2
**Key Features:** AZ Affinity, PubSub auto-reconnection, sharded PubSub, cluster-aware multi-key commands, cluster scan, batching (pipeline/transaction), OpenTelemetry integration
**Architecture:** Rust core with FFI bindings, async-first design, cluster and standalone support
**Performance:** Optimized for high throughput and low latency with connection pooling

## If You Need More

- **Getting Started:** [README.md](./README.md)
- **Contributing:** [CONTRIBUTING.md](./CONTRIBUTING.md)
- **Security:** [SECURITY.md](./SECURITY.md)
- **Documentation:** [docs/README.md](./docs/README.md)
- **Examples:** [examples/](./examples/)
- **Language-Specific Guides:**
  - [Java Developer Guide](./java/DEVELOPER.md)
  - [Python Developer Guide](./python/DEVELOPER.md)
  - [Node.js Developer Guide](./node/DEVELOPER.md)
  - [Go Developer Guide](./go/DEVELOPER.md)
