.PHONY: all java java-test python python-test node node-test check-valkey-server go go-test \
	prettier-check prettier-fix rust rust-test rust-lint rust-fmt lint fmt info

BLUE=\033[34m
YELLOW=\033[33m
GREEN=\033[32m
RED=\033[31m
CYAN=\033[36m
BOLD=\033[1m
RESET=\033[0m
ROOT_DIR=$(shell pwd)
PYENV_DIR=$(shell pwd)/python/.env
PY_PATH=$(shell find python/.env -name "site-packages"|xargs readlink -f)
PY_GLIDE_PATH=$(shell pwd)/python/python/

all: java java-test python python-test node node-test go go-test python-lint java-lint

##
## Java targets
##
java:
	@echo "$(GREEN)Building for Java (release)$(RESET)"
	@cd java && ./gradlew :client:buildAllRelease

java-lint:
	@echo "$(GREEN)Running spotlessApply$(RESET)"
	@cd java && ./gradlew :spotlessApply

java-test: check-valkey-server
	@echo "$(GREEN)Running integration tests$(RESET)"
	@cd java && ./gradlew :integTest:test

##
## Python targets
##
python:
	@echo "$(GREEN)Building Python async + sync clients (release mode)$(RESET)"
	@cd python && python3 dev.py build --mode release

python-lint:
	@echo "$(GREEN)Running linters via dev.py$(RESET)"
	@cd python && python3 dev.py lint

python-test: check-valkey-server
	@echo "$(GREEN)Running Python tests$(RESET)"
	@cd python && python3 dev.py test

##
## NodeJS targets
##
node: .build/node_deps
	@echo "$(GREEN)Building for NodeJS (release)...$(RESET)"
	@cd node && npm run build:release

.build/node_deps:
	@echo "$(GREEN)Installing NodeJS dependencies...$(RESET)"
	@cd node && npm i
	@cd node/rust-client && npm i
	@mkdir -p .build/ && touch .build/node_deps

node-test: .build/node_deps check-valkey-server
	@echo "$(GREEN)Running tests for NodeJS$(RESET)"
	@cd node && npm run build
	cd node && npm test

node-lint: .build/node_deps
	@echo "$(GREEN)Running linters for NodeJS$(RESET)"
	@cd node && npx run lint:fix

##
## Prettier targets
##
prettier-check:
	@echo "$(GREEN)Checking formatting with Prettier$(RESET)"
	@npx prettier --check .github/
	@for folder in node benchmarks/node benchmarks/utilities; do \
		npx prettier --check $$folder; \
	done

prettier-fix:
	@echo "$(GREEN)Fixing formatting with Prettier$(RESET)"
	@npx prettier --write .github/
	@for folder in node benchmarks/node benchmarks/utilities; do \
		npx prettier --write $$folder; \
	done

##
## Go targets
##


go: .build/go_deps
	$(MAKE) -C go build

go-test: .build/go_deps
	$(MAKE) -C go test

go-lint: .build/go_deps
	$(MAKE) -C go lint

.build/go_deps:
	@echo "$(GREEN)Installing GO dependencies...$(RESET)"
	$(MAKE) -C go install-build-tools install-dev-tools
	@mkdir -p .build/ && touch .build/go_deps

##
## Rust targets
##
rust: ## Build glide-core and FFI (release)
	@echo "$(GREEN)Building Rust core + FFI (release)$(RESET)"
	@cd glide-core && cargo build --release
	@cd ffi && cargo build --release

rust-test: ## Run Rust unit tests
	@echo "$(GREEN)Running Rust tests$(RESET)"
	@cd glide-core && cargo test
	@cd ffi && cargo test

rust-lint: ## Run clippy on Rust code
	@echo "$(GREEN)Running clippy$(RESET)"
	@cd glide-core && cargo clippy -- -D warnings
	@cd ffi && cargo clippy -- -D warnings

rust-fmt: ## Format Rust code
	@echo "$(GREEN)Formatting Rust code$(RESET)"
	@cd glide-core && cargo fmt
	@cd ffi && cargo fmt
	@cd logger_core && cargo fmt

rust-fmt-check: ## Check Rust formatting without changes
	@echo "$(GREEN)Checking Rust formatting$(RESET)"
	@cd glide-core && cargo fmt -- --check
	@cd ffi && cargo fmt -- --check
	@cd logger_core && cargo fmt -- --check

##
## Cross-language targets
##
lint: rust-lint python-lint java-lint node-lint go-lint ## Run linters for all languages
	@echo "$(GREEN)All linters passed$(RESET)"

fmt: rust-fmt prettier-fix ## Auto-format Rust + JS/TS files
	@echo "$(GREEN)All formatters applied$(RESET)"

##
## Common targets
##
check-valkey-server:
	which valkey-server || which redis-server

clean: ## Remove build caches
	rm -fr .build/

info: ## Show development environment info
	@echo "$(BOLD)$(CYAN)Valkey GLIDE Development Environment$(RESET)"
	@echo ""
	@echo "$(BOLD)Toolchain versions:$(RESET)"
	@printf "  Rust:    " && (rustc --version 2>/dev/null || echo "$(RED)not installed$(RESET)")
	@printf "  Cargo:   " && (cargo --version 2>/dev/null || echo "$(RED)not installed$(RESET)")
	@printf "  Java:    " && (java --version 2>/dev/null | head -1 || echo "$(RED)not installed$(RESET)")
	@printf "  Gradle:  " && (cd java && ./gradlew --version 2>/dev/null | grep "Gradle " || echo "$(RED)not available$(RESET)")
	@printf "  Python:  " && (python3 --version 2>/dev/null || echo "$(RED)not installed$(RESET)")
	@printf "  Node.js: " && (node --version 2>/dev/null || echo "$(RED)not installed$(RESET)")
	@printf "  npm:     " && (npm --version 2>/dev/null || echo "$(RED)not installed$(RESET)")
	@printf "  Go:      " && (go version 2>/dev/null || echo "$(RED)not installed$(RESET)")
	@printf "  Protoc:  " && (protoc --version 2>/dev/null || echo "$(RED)not installed$(RESET)")
	@echo ""
	@printf "  Server:  " && (valkey-server --version 2>/dev/null || redis-server --version 2>/dev/null || echo "$(RED)no valkey-server or redis-server found$(RESET)")
	@echo ""
	@echo "$(BOLD)Key paths:$(RESET)"
	@echo "  Root:    $(ROOT_DIR)"
	@echo "  Core:    $(ROOT_DIR)/glide-core"
	@echo "  FFI:     $(ROOT_DIR)/ffi"

help: ## Show this help
	@echo "$(BOLD)$(CYAN)Valkey GLIDE - Makefile Targets$(RESET)"
	@echo ""
	@echo "$(BOLD)Build targets:$(RESET)"
	@echo "  $(GREEN)all$(RESET)             Build and test all languages"
	@echo "  $(GREEN)rust$(RESET)            Build Rust core + FFI (release)"
	@echo "  $(GREEN)java$(RESET)            Build Java client (release)"
	@echo "  $(GREEN)python$(RESET)          Build Python async + sync clients (release)"
	@echo "  $(GREEN)node$(RESET)            Build Node.js client (release)"
	@echo "  $(GREEN)go$(RESET)              Build Go client"
	@echo ""
	@echo "$(BOLD)Test targets:$(RESET)"
	@echo "  $(GREEN)rust-test$(RESET)       Run Rust unit tests"
	@echo "  $(GREEN)java-test$(RESET)       Run Java integration tests"
	@echo "  $(GREEN)python-test$(RESET)     Run Python tests"
	@echo "  $(GREEN)node-test$(RESET)       Run Node.js tests"
	@echo "  $(GREEN)go-test$(RESET)         Run Go tests"
	@echo ""
	@echo "$(BOLD)Lint & format targets:$(RESET)"
	@echo "  $(GREEN)lint$(RESET)            Run linters for all languages"
	@echo "  $(GREEN)fmt$(RESET)             Auto-format Rust + JS/TS"
	@echo "  $(GREEN)rust-lint$(RESET)       Run clippy on Rust code"
	@echo "  $(GREEN)rust-fmt$(RESET)        Format Rust code"
	@echo "  $(GREEN)rust-fmt-check$(RESET)  Check Rust formatting (no changes)"
	@echo "  $(GREEN)java-lint$(RESET)       Run Java spotlessApply"
	@echo "  $(GREEN)python-lint$(RESET)     Run Python linters"
	@echo "  $(GREEN)node-lint$(RESET)       Run Node.js linters"
	@echo "  $(GREEN)go-lint$(RESET)         Run Go linters"
	@echo "  $(GREEN)prettier-check$(RESET)  Check JS/TS formatting"
	@echo "  $(GREEN)prettier-fix$(RESET)    Fix JS/TS formatting"
	@echo ""
	@echo "$(BOLD)Utility targets:$(RESET)"
	@echo "  $(GREEN)info$(RESET)            Show development environment info"
	@echo "  $(GREEN)clean$(RESET)           Remove build caches"
	@echo "  $(GREEN)help$(RESET)            Show this help"
