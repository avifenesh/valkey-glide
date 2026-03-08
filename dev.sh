#!/usr/bin/env bash
# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
#
# dev.sh - Unified developer toolkit for Valkey GLIDE
#
# Provides a single entry point for common cross-language operations:
#   ./dev.sh status       - Show repo build/dependency status
#   ./dev.sh check        - Pre-push validation (format + lint + build)
#   ./dev.sh fmt [lang]   - Format code (all or specific language)
#   ./dev.sh lint [lang]  - Lint code (all or specific language)
#   ./dev.sh build [lang] - Build (all or specific language)
#   ./dev.sh test [lang]  - Run tests (all or specific language)
#   ./dev.sh setup        - Verify development environment
#   ./dev.sh changed      - Show which languages have changes vs main

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
BOLD='\033[1m'
RESET='\033[0m'

LANGUAGES=(rust java python node go)

info()  { echo -e "${BLUE}[info]${RESET} $*"; }
ok()    { echo -e "${GREEN}[ok]${RESET} $*"; }
warn()  { echo -e "${YELLOW}[warn]${RESET} $*"; }
err()   { echo -e "${RED}[error]${RESET} $*"; }
header(){ echo -e "\n${BOLD}$*${RESET}"; }

# ── Helpers ──────────────────────────────────────────────────────────

check_tool() {
    local tool=$1
    if command -v "$tool" &>/dev/null; then
        ok "$tool $(command -v "$tool")"
        return 0
    else
        err "$tool not found"
        return 1
    fi
}

# Detect which languages have file changes compared to main branch
detect_changed_languages() {
    local base="${1:-main}"
    local changed=()
    local files
    files=$(git diff --name-only "$base"...HEAD 2>/dev/null || git diff --name-only HEAD~1 2>/dev/null || echo "")

    if [[ -z "$files" ]]; then
        echo "${LANGUAGES[@]}"
        return
    fi

    if echo "$files" | grep -qE '^(glide-core/|logger_core/|ffi/|Cargo)'; then
        changed+=(rust)
    fi
    if echo "$files" | grep -qE '^java/'; then
        changed+=(java)
    fi
    if echo "$files" | grep -qE '^python/'; then
        changed+=(python)
    fi
    if echo "$files" | grep -qE '^node/'; then
        changed+=(node)
    fi
    if echo "$files" | grep -qE '^go/'; then
        changed+=(go)
    fi

    if [[ ${#changed[@]} -eq 0 ]]; then
        echo "none"
    else
        echo "${changed[@]}"
    fi
}

# ── Commands ─────────────────────────────────────────────────────────

cmd_status() {
    header "Repository Status"
    echo -e "Branch: $(git branch --show-current)"
    echo -e "Commit: $(git log --oneline -1)"
    echo ""

    header "Language Toolchain Versions"
    echo -n "  Rust:   "; rustc --version 2>/dev/null || echo "not installed"
    echo -n "  Cargo:  "; cargo --version 2>/dev/null || echo "not installed"
    echo -n "  Java:   "; java --version 2>/dev/null | head -1 || echo "not installed"
    echo -n "  Gradle: "; cd java 2>/dev/null && ./gradlew --version 2>/dev/null | grep "^Gradle " || echo "not available"; cd "$ROOT_DIR"
    echo -n "  Python: "; python3 --version 2>/dev/null || echo "not installed"
    echo -n "  Node:   "; node --version 2>/dev/null || echo "not installed"
    echo -n "  npm:    "; npm --version 2>/dev/null || echo "not installed"
    echo -n "  Go:     "; go version 2>/dev/null || echo "not installed"
    echo ""

    header "Changed Languages (vs main)"
    local changed
    changed=$(detect_changed_languages)
    if [[ "$changed" == "none" ]]; then
        info "No language-specific changes detected"
    else
        for lang in $changed; do
            echo -e "  ${GREEN}*${RESET} $lang"
        done
    fi
}

cmd_setup() {
    header "Checking Development Environment"
    local missing=0

    info "Required tools:"
    check_tool rustc     || ((missing++))
    check_tool cargo     || ((missing++))
    check_tool protoc    || ((missing++))

    echo ""
    info "Language-specific tools:"
    check_tool java      || ((missing++))
    check_tool python3   || ((missing++))
    check_tool node      || ((missing++))
    check_tool npm       || ((missing++))
    check_tool go        || ((missing++))

    echo ""
    info "Optional tools:"
    check_tool cargo-clippy || warn "cargo clippy not available (install via rustup component add clippy)"
    check_tool cargo-fmt    || warn "cargo fmt not available (install via rustup component add rustfmt)"
    check_tool valkey-server || {
        check_tool redis-server || warn "No valkey-server or redis-server found (needed for integration tests)"
    }

    echo ""
    if [[ $missing -gt 0 ]]; then
        err "$missing required tool(s) missing"
        return 1
    else
        ok "All required tools present"
    fi
}

cmd_fmt() {
    local lang="${1:-all}"

    case "$lang" in
        rust|all-rust)
            header "Formatting Rust"
            cargo fmt --all
            ok "Rust formatted"
            ;;&
        java|all-java)
            header "Formatting Java"
            cd java && ./gradlew :spotlessApply && cd "$ROOT_DIR"
            ok "Java formatted"
            ;;&
        python|all-python)
            header "Formatting Python"
            cd python && python3 dev.py lint && cd "$ROOT_DIR"
            ok "Python formatted"
            ;;&
        node|all-node)
            header "Formatting Node.js"
            cd node && npx prettier --write src/ tests/ && cd "$ROOT_DIR"
            ok "Node.js formatted"
            ;;&
        go|all-go)
            header "Formatting Go"
            cd go && make format && cd "$ROOT_DIR"
            ok "Go formatted"
            ;;&
        all)
            # The case fall-through above handles 'all' for each language
            ;;
        rust|java|python|node|go)
            ;; # already handled
        *)
            err "Unknown language: $lang (valid: rust, java, python, node, go, all)"
            return 1
            ;;
    esac
}

cmd_lint() {
    local lang="${1:-all}"
    local failed=0

    case "$lang" in
        rust)
            header "Linting Rust"
            cargo clippy --all-targets -- -D warnings || ((failed++))
            cargo fmt --all -- --check || ((failed++))
            ;;
        java)
            header "Linting Java"
            cd java && ./gradlew :spotlessCheck && cd "$ROOT_DIR" || ((failed++))
            ;;
        python)
            header "Linting Python"
            cd python && python3 dev.py lint && cd "$ROOT_DIR" || ((failed++))
            ;;
        node)
            header "Linting Node.js"
            cd node && npx eslint . && cd "$ROOT_DIR" || ((failed++))
            ;;
        go)
            header "Linting Go"
            cd go && make lint && cd "$ROOT_DIR" || ((failed++))
            ;;
        all)
            for l in "${LANGUAGES[@]}"; do
                cmd_lint "$l" || ((failed++))
            done
            ;;
        *)
            err "Unknown language: $lang (valid: rust, java, python, node, go, all)"
            return 1
            ;;
    esac

    if [[ $failed -gt 0 ]]; then
        err "$failed linter(s) failed"
        return 1
    fi
    ok "Lint passed"
}

cmd_build() {
    local lang="${1:-all}"
    local failed=0

    case "$lang" in
        rust)
            header "Building Rust core"
            cargo build --release
            ;;
        java)
            header "Building Java"
            make java || ((failed++))
            ;;
        python)
            header "Building Python"
            make python || ((failed++))
            ;;
        node)
            header "Building Node.js"
            make node || ((failed++))
            ;;
        go)
            header "Building Go"
            make go || ((failed++))
            ;;
        all)
            for l in "${LANGUAGES[@]}"; do
                cmd_build "$l" || ((failed++))
            done
            ;;
        *)
            err "Unknown language: $lang (valid: rust, java, python, node, go, all)"
            return 1
            ;;
    esac

    if [[ $failed -gt 0 ]]; then
        err "$failed build(s) failed"
        return 1
    fi
    ok "Build passed"
}

cmd_test() {
    local lang="${1:-all}"
    local failed=0

    case "$lang" in
        rust)
            header "Testing Rust core"
            cd glide-core && cargo test && cd "$ROOT_DIR" || ((failed++))
            ;;
        java)
            header "Testing Java"
            make java-test || ((failed++))
            ;;
        python)
            header "Testing Python"
            make python-test || ((failed++))
            ;;
        node)
            header "Testing Node.js"
            make node-test || ((failed++))
            ;;
        go)
            header "Testing Go"
            cd go && make unit-test && cd "$ROOT_DIR" || ((failed++))
            ;;
        all)
            for l in "${LANGUAGES[@]}"; do
                cmd_test "$l" || ((failed++))
            done
            ;;
        *)
            err "Unknown language: $lang (valid: rust, java, python, node, go, all)"
            return 1
            ;;
    esac

    if [[ $failed -gt 0 ]]; then
        err "$failed test suite(s) failed"
        return 1
    fi
    ok "Tests passed"
}

cmd_check() {
    header "Pre-push Check"
    local lang="${1:-}"
    local targets
    local failed=0

    if [[ -n "$lang" ]]; then
        targets=("$lang")
    else
        # Auto-detect changed languages
        local changed
        changed=$(detect_changed_languages)
        if [[ "$changed" == "none" ]]; then
            info "No language changes detected, checking Rust core only"
            targets=(rust)
        else
            IFS=' ' read -ra targets <<< "$changed"
        fi
    fi

    info "Checking: ${targets[*]}"
    echo ""

    for t in "${targets[@]}"; do
        cmd_lint "$t" || ((failed++))
    done

    # Always check Rust core builds since all languages depend on it
    if [[ ! " ${targets[*]} " =~ " rust " ]]; then
        header "Building Rust core (dependency)"
        cargo check --release || ((failed++))
    fi

    for t in "${targets[@]}"; do
        cmd_build "$t" || ((failed++))
    done

    echo ""
    if [[ $failed -gt 0 ]]; then
        err "Pre-push check failed ($failed failure(s))"
        return 1
    fi
    ok "Pre-push check passed - ready to push"
}

cmd_changed() {
    header "Changed Languages (vs main)"
    local changed
    changed=$(detect_changed_languages)
    if [[ "$changed" == "none" ]]; then
        info "No language-specific changes detected"
    else
        for lang in $changed; do
            echo -e "  ${GREEN}*${RESET} $lang"
            # Show changed files for this language
            local pattern
            case "$lang" in
                rust)   pattern='^(glide-core/|logger_core/|ffi/|Cargo)' ;;
                java)   pattern='^java/' ;;
                python) pattern='^python/' ;;
                node)   pattern='^node/' ;;
                go)     pattern='^go/' ;;
            esac
            git diff --name-only main...HEAD 2>/dev/null | grep -E "$pattern" | sed 's/^/      /' || true
        done
    fi
}

cmd_help() {
    echo -e "${BOLD}Valkey GLIDE Developer Toolkit${RESET}"
    echo ""
    echo "Usage: ./dev.sh <command> [language]"
    echo ""
    echo "Commands:"
    echo "  status        Show repo and toolchain status"
    echo "  setup         Verify development environment prerequisites"
    echo "  check [lang]  Pre-push validation (lint + build for changed languages)"
    echo "  fmt   [lang]  Format code (default: all)"
    echo "  lint  [lang]  Lint code (default: all)"
    echo "  build [lang]  Build (default: all)"
    echo "  test  [lang]  Run tests (default: all)"
    echo "  changed       Show which languages have changes vs main"
    echo "  help          Show this help message"
    echo ""
    echo "Languages: rust, java, python, node, go, all"
    echo ""
    echo "Examples:"
    echo "  ./dev.sh status          # Show toolchain versions and changed files"
    echo "  ./dev.sh check           # Auto-detect changes and validate"
    echo "  ./dev.sh check rust      # Validate only Rust changes"
    echo "  ./dev.sh fmt java        # Format Java code"
    echo "  ./dev.sh lint python     # Lint Python code"
    echo "  ./dev.sh build node      # Build Node.js client"
    echo "  ./dev.sh test go         # Run Go unit tests"
}

# ── Main ─────────────────────────────────────────────────────────────

command="${1:-help}"
shift || true

case "$command" in
    status)  cmd_status "$@" ;;
    setup)   cmd_setup "$@" ;;
    check)   cmd_check "$@" ;;
    fmt)     cmd_fmt "$@" ;;
    lint)    cmd_lint "$@" ;;
    build)   cmd_build "$@" ;;
    test)    cmd_test "$@" ;;
    changed) cmd_changed "$@" ;;
    help|-h|--help) cmd_help ;;
    *)
        err "Unknown command: $command"
        cmd_help
        exit 1
        ;;
esac
