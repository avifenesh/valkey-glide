#!/bin/bash

# Colors for terminal output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BOLD='\033[1m'
NC='\033[0m' # No Color

echo -e "${BOLD}Valkey GLIDE vs Redisson Benchmark Server${NC}"
echo "---------------------------------------"

# Define project paths
BENCHMARK_ROOT="/home/fedora/valkey-glide/examples/benchmark"
HTTP_PATH="${BENCHMARK_ROOT}/http"

# Check for Java client build
GLIDE_JAR_PATH="/home/fedora/valkey-glide/java/client/build/libs"
if [ ! -d "$GLIDE_JAR_PATH" ] || [ -z "$(ls -A $GLIDE_JAR_PATH)" ]; then
    echo -e "${YELLOW}Valkey GLIDE client libraries not found, attempting to build...${NC}"
    
    # Check if the native library exists
    NATIVE_LIB_PATH="/home/fedora/valkey-glide/java/target/release/libglide_rs.so"
    if [ ! -f "$NATIVE_LIB_PATH" ]; then
        echo -e "${YELLOW}Native library not found at $NATIVE_LIB_PATH${NC}"
        echo -e "${BOLD}Building Valkey GLIDE Rust core...${NC}"
        
        cd /home/fedora/valkey-glide/java
        cargo build --release
        
        if [ $? -ne 0 ]; then
            echo -e "${RED}Valkey GLIDE Rust core build failed. Cannot continue.${NC}"
            exit 1
        fi
        echo -e "${GREEN}Valkey GLIDE Rust core built successfully.${NC}"
    else
        echo -e "${GREEN}Found Rust native library at $NATIVE_LIB_PATH${NC}"
    fi
    
    # Build the Java client
    echo -e "${YELLOW}Building Valkey GLIDE Java client...${NC}"
    cd /home/fedora/valkey-glide/java
    ./gradlew clean build -x test
    
    if [ $? -ne 0 ]; then
        echo -e "${RED}Valkey GLIDE Java client build failed. Cannot continue.${NC}"
        exit 1
    fi
    echo -e "${GREEN}Valkey GLIDE Java client built successfully.${NC}"
fi

# Check if we need to move HTTP project files
if [ ! -d "$HTTP_PATH" ]; then
    echo -e "${YELLOW}Creating HTTP benchmark project structure...${NC}"
    mkdir -p "$HTTP_PATH"
    
    # Move files from benchmark root to HTTP path
    if [ -f "${BENCHMARK_ROOT}/build.gradle" ]; then
        mv "${BENCHMARK_ROOT}/build.gradle" "${HTTP_PATH}/"
    fi
    
    if [ -d "${BENCHMARK_ROOT}/src" ]; then
        mv "${BENCHMARK_ROOT}/src" "${HTTP_PATH}/"
    fi
    
    # Copy README if it exists
    if [ -f "${BENCHMARK_ROOT}/README.md" ]; then
        cp "${BENCHMARK_ROOT}/README.md" "${HTTP_PATH}/README.md"
    fi
fi

# Change to HTTP benchmark directory
cd "${HTTP_PATH}"

# Check if Gradle wrapper exists
if [ ! -f "./gradlew" ]; then
    echo -e "${YELLOW}Gradle wrapper not found, creating...${NC}"
    gradle wrapper
fi

# Build and run the benchmark server
echo -e "${BOLD}Building and running benchmark server...${NC}"
./gradlew bootRun --args='--server.port=8080' &
SERVER_PID=$!

# Function to clean up when script is terminated
cleanup() {
    echo -e "${YELLOW}Shutting down benchmark server...${NC}"
    kill -TERM $SERVER_PID
    exit 0
}

# Register cleanup function for script termination
trap cleanup INT TERM

# Wait a moment for the server to start
echo -e "${YELLOW}Waiting for server to start...${NC}"
sleep 5

echo -e "${BOLD}Benchmark server is running!${NC}"
echo -e "${GREEN}-------------------------------------------------------------${NC}"
echo -e "${BOLD}Available endpoints:${NC}"
echo -e "- Health check: ${GREEN}http://localhost:8080/benchmark/health${NC}"
echo -e "- Status check: ${GREEN}http://localhost:8080/benchmark/status${NC}"
echo -e ""
echo -e "${BOLD}Data Population:${NC}"
echo -e "- Populate Valkey with Glide: ${GREEN}curl -X POST http://localhost:8080/benchmark/populate?serverType=valkey&clientType=glide${NC}"
echo -e "- Populate Redis with Glide: ${GREEN}curl -X POST http://localhost:8080/benchmark/populate?serverType=redis&clientType=glide${NC}"
echo -e "- Verify population: ${GREEN}curl http://localhost:8080/benchmark/verify?serverType=valkey${NC}"
echo -e ""
echo -e "${BOLD}Benchmarking with oha tool:${NC}"
echo -e "1. Glide client against Valkey:"
echo -e "   ${GREEN}oha -n 10000 -c 50 http://localhost:8080/benchmark/glide/valkey/TITLE{1..1000}:en_US:document${NC}"
echo -e ""
echo -e "2. Glide client against Redis:"
echo -e "   ${GREEN}oha -n 10000 -c 50 http://localhost:8080/benchmark/glide/redis/TITLE{1..1000}:en_US:document${NC}"
echo -e ""
echo -e "3. Redisson client against Valkey:"
echo -e "   ${GREEN}oha -n 10000 -c 50 http://localhost:8080/benchmark/redisson/valkey/TITLE{1..1000}:en_US:document${NC}"
echo -e ""
echo -e "4. Redisson client against Redis:"
echo -e "   ${GREEN}oha -n 10000 -c 50 http://localhost:8080/benchmark/redisson/redis/TITLE{1..1000}:en_US:document${NC}"
echo -e "${GREEN}-------------------------------------------------------------${NC}"
echo -e "Press Ctrl+C to stop the server"

# Keep script running until user interrupts
wait $SERVER_PID
