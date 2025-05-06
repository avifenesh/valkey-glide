#!/bin/bash

# Colors for terminal output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BOLD='\033[1m'
NC='\033[0m' # No Color

echo -e "${BOLD}Valkey GLIDE ElastiCache Benchmark Test${NC}"
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
    
    # Use example project as a basis for compilation
    echo -e "${YELLOW}Compiling ElastiCacheBenchmarkTest.java against examples project...${NC}"
    cd /home/fedora/valkey-glide/examples/java
fi

# Create temporary directory for our compiled benchmark
TEMP_DIR="/tmp/glide-benchmark"
mkdir -p $TEMP_DIR

# Copy our benchmark file to examples
cp /home/fedora/valkey-glide/ElastiCacheBenchmarkTest.java /home/fedora/valkey-glide/examples/java/src/main/java/glide/examples/

# Compile using the examples project and run
cd /home/fedora/valkey-glide/examples/java
./gradlew compileJava

# Create a run task for our benchmark
echo "task runElastiCacheBenchmark(type: JavaExec) {" > elastic-benchmark-task.gradle
echo "    mainClass = 'glide.examples.ElastiCacheBenchmarkTest'" >> elastic-benchmark-task.gradle
echo "    classpath = sourceSets.main.runtimeClasspath" >> elastic-benchmark-task.gradle
echo "    jvmArgs = ['-Djava.library.path=/home/fedora/valkey-glide/java/target/release']" >> elastic-benchmark-task.gradle
echo "}" >> elastic-benchmark-task.gradle

# Apply the custom task
echo "apply from: 'elastic-benchmark-task.gradle'" >> build.gradle

# Run the benchmark
echo -e "${BOLD}Running ElastiCache benchmark...${NC}"
./gradlew runElastiCacheBenchmark

# Check result status
if [ $? -eq 0 ]; then
    echo -e "${GREEN}ElastiCache benchmark executed successfully${NC}"
else
    echo -e "${RED}ElastiCache benchmark failed with status code $?${NC}"
fi
