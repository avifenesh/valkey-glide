#!/bin/bash

# Colors for terminal output
GREEN='\033[0;32m'
RED='\033[0;31m'
BOLD='\033[1m'
NC='\033[0m' # No Color

echo -e "${BOLD}Valkey Glide Benchmark Docker Build and Run${NC}"
echo "---------------------------------------"

# Skip core and binding builds since they're already built
echo -e "${BOLD}Using pre-built Glide core and Java bindings...${NC}"
echo -e "${GREEN}Core and bindings already built.${NC}"

# We're already in java/benchmarks directory, ensure native library location exists
echo -e "${BOLD}Setting up native library for Docker...${NC}"
# Use the new script to handle native library loading
if [ -f "./fix_native_loading.sh" ]; then
    echo -e "${BOLD}Running fix_native_loading.sh to handle library setup...${NC}"
    ./fix_native_loading.sh
    if [ $? -ne 0 ]; then
        echo -e "${RED}Native library setup failed. Cannot continue.${NC}"
        exit 1
    fi
else
    echo -e "${RED}fix_native_loading.sh script not found. Falling back to basic setup...${NC}"
    
    mkdir -p src/main/resources/glide/benchmarks/libs/native/
    
    # Check if we need to copy the library
    if [ ! -f "src/main/resources/glide/benchmarks/libs/native/libvalkey_glide.so" ]; then
        echo -e "${BOLD}Copying native library...${NC}"
        # Copy from Java target if available
        if [ -f "../../java/target/release/libglide_rs.so" ]; then
            cp ../../java/target/release/libglide_rs.so src/main/resources/glide/benchmarks/libs/native/libvalkey_glide.so
        else
            echo -e "${RED}Native library not found at ../../java/target/release/libglide_rs.so${NC}"
            # Check if it's already in the current directory structure
            if [ -f "src/main/resources/glide/benchmarks/libs/native/libvalkey_glide.so" ]; then
                echo -e "${GREEN}Library already exists in destination.${NC}"
            else
                echo -e "${RED}No native library found. Cannot continue.${NC}"
                exit 1
            fi
        fi
    fi
fi
echo -e "${GREEN}Native library setup complete.${NC}"
echo

# Verify gradle wrapper exists
if [ ! -f "./gradlew" ]; then
    echo -e "${RED}Gradle wrapper not found. Creating symbolic link to parent gradle wrapper...${NC}"
    ln -s ../gradlew ./gradlew
    ln -s ../gradle ./gradle
    chmod +x ./gradlew
fi

# Build the Docker image
echo -e "${BOLD}Building Docker image...${NC}"
docker compose build
if [ $? -ne 0 ]; then
    echo -e "${RED}Docker build failed. Please check the error and try again.${NC}"
    exit 1
fi
echo -e "${GREEN}Docker build successful.${NC}"
echo

# Start the services
echo -e "${BOLD}Starting Docker services...${NC}"
docker compose up -d
if [ $? -ne 0 ]; then
    echo -e "${RED}Failed to start Docker services. Please check the error and try again.${NC}"
    exit 1
fi
echo -e "${GREEN}Docker services started successfully.${NC}"
echo

# Wait for services to be ready
echo -e "${BOLD}Waiting for services to be ready...${NC}"
echo "This may take a few moments..."
sleep 10

# Check if services are running
echo -e "${BOLD}Checking services status:${NC}"
docker compose ps

echo
echo -e "${BOLD}Access the benchmark application:${NC}"
echo "- Benchmark UI: http://localhost:8080"
echo "- Grafana Dashboard: http://localhost:3000"
echo "- Prometheus: http://localhost:9090"
echo
echo -e "${BOLD}To stop the services:${NC}"
echo "docker compose down"
