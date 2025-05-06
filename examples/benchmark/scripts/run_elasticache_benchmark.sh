#!/bin/bash

# Colors for terminal output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BOLD='\033[1m'
NC='\033[0m' # No Color

# Define project paths
BENCHMARK_ROOT="/home/fedora/valkey-glide/examples/benchmark"
STANDALONE_PATH="${BENCHMARK_ROOT}/standalone"
BENCHMARK_JAVA_FILE="${STANDALONE_PATH}/src/main/java/glide/benchmark/ElastiCacheBenchmarkTest.java"

# Parse command line arguments
SERVER_TYPE="valkey"
HELP_REQUESTED=false

while [[ "$#" -gt 0 ]]; do
    case $1 in
        -s|--server)
            if [[ "$2" == "valkey" || "$2" == "redis" ]]; then
                SERVER_TYPE="$2"
                shift
            else
                echo -e "${RED}Invalid server type. Use 'valkey' or 'redis'.${NC}"
                exit 1
            fi
            ;;
        -h|--help)
            HELP_REQUESTED=true
            ;;
        *)
            echo -e "${RED}Unknown parameter: $1${NC}"
            HELP_REQUESTED=true
            ;;
    esac
    shift
done

if [ "$HELP_REQUESTED" = true ]; then
    echo -e "${BOLD}Valkey GLIDE ElastiCache Benchmark Test${NC}"
    echo "Usage: ./scripts/run_elasticache_benchmark.sh [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  -s, --server [valkey|redis]   Specify server type (default: valkey)"
    echo "  -h, --help                    Show this help message"
    exit 0
fi

echo -e "${BOLD}Valkey GLIDE ElastiCache Benchmark Test - 80/20 Workload Pattern${NC}"
echo -e "Server Type: ${GREEN}${SERVER_TYPE^^}${NC}"
echo "---------------------------------------"

# First check if Java client has been built
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
fi

# Create Gradle project structure in standalone directory if it doesn't exist
if [ ! -f "${STANDALONE_PATH}/build.gradle" ]; then
    echo -e "${YELLOW}Setting up Gradle project for standalone benchmark...${NC}"
    
    mkdir -p "${STANDALONE_PATH}/gradle/wrapper"
    
    # Create build.gradle file
    cat > "${STANDALONE_PATH}/build.gradle" << EOL
plugins {
    id 'java'
    id 'application'
}

repositories {
    mavenCentral()
}

dependencies {
    // Valkey Glide Java client
    implementation files('${GLIDE_JAR_PATH}/glide-1.0.0-SNAPSHOT.jar')
    
    // Add any additional dependencies needed for the benchmark
}

application {
    mainClass = 'glide.benchmark.ElastiCacheBenchmarkTest'
}

sourceSets {
    main {
        java {
            srcDirs = ['src/main/java']
        }
    }
}

run {
    systemProperty 'java.library.path', '${NATIVE_LIB_PATH}'
    jvmArgs = ['-Xms1g', '-Xmx2g']
    args = ['${SERVER_TYPE}']
}
EOL

    # Copy gradle wrapper files if they exist in examples/java
    if [ -f "/home/fedora/valkey-glide/examples/java/gradlew" ]; then
        cp /home/fedora/valkey-glide/examples/java/gradlew "${STANDALONE_PATH}/"
        cp -r /home/fedora/valkey-glide/examples/java/gradle/* "${STANDALONE_PATH}/gradle/"
        chmod +x "${STANDALONE_PATH}/gradlew"
    else
        echo -e "${YELLOW}Gradle wrapper not found. Using system gradle.${NC}"
    fi
fi

# Ensure the benchmark file exists in the examples directory
if [ ! -f "${BENCHMARK_JAVA_FILE}" ]; then
    echo -e "${RED}Benchmark file not found at ${BENCHMARK_JAVA_FILE}${NC}"
    echo -e "${RED}Please ensure the file exists before running the benchmark.${NC}"
    exit 1
fi

# Compile using the standalone project
echo -e "${YELLOW}Compiling benchmark...${NC}"
cd "${STANDALONE_PATH}"

# Use gradlew if it exists, otherwise use system gradle
if [ -f "gradlew" ]; then
    ./gradlew compileJava
else
    gradle compileJava
fi

if [ $? -ne 0 ]; then
    echo -e "${RED}Compilation failed. Cannot continue.${NC}"
    exit 1
fi

# Run the benchmark
echo -e "${BOLD}Running 80/20 Workload Benchmark against ${SERVER_TYPE^^}...${NC}"
echo -e "${YELLOW}This will populate test data, run GET/SET operations, and measure latency${NC}"
echo -e "${YELLOW}The test uses the TITLE{id}:{locale}:{attribute} key pattern${NC}"

if [ -f "gradlew" ]; then
    ./gradlew run --args="${SERVER_TYPE}"
else
    gradle run --args="${SERVER_TYPE}"
fi

# Check result status
if [ $? -eq 0 ]; then
    echo -e "${GREEN}ElastiCache benchmark executed successfully${NC}"
    echo -e "${BOLD}To compare with the other server type, run:${NC}"
    if [ "$SERVER_TYPE" = "valkey" ]; then
        echo -e "./scripts/run_elasticache_benchmark.sh --server redis"
    else
        echo -e "./scripts/run_elasticache_benchmark.sh --server valkey"
    fi
else
    echo -e "${RED}ElastiCache benchmark failed with status code $?${NC}"
fi
