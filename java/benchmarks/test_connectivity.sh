#!/bin/bash

# Colors for terminal output
GREEN='\033[0;32m'
RED='\033[0;31m'
BOLD='\033[1m'
NC='\033[0m' # No Color

echo -e "${BOLD}Valkey Glide Benchmark Connectivity Test${NC}"
echo "-------------------------------------"
echo -e "${BOLD}This script will test connectivity to both Valkey and Redis endpoints${NC}"
echo

# Build Glide in release mode for optimal performance
echo -e "${BOLD}Building Glide java bindings...${NC}"
cd ../
cargo build --release
if [ $? -ne 0 ]; then
    echo -e "${RED}Glide java bindings build failed. Please check the error and try again.${NC}"
    exit 1
fi
echo -e "${GREEN}Glide java bindings build successful.${NC}"
echo -e "${GREEN}Glide core build successful.${NC}"
echo

# Move back to the benchmarks directory
cd benchmarks

# Verify gradle wrapper exists
if [ ! -f "./gradlew" ]; then
    echo -e "${RED}Gradle wrapper not found. Creating symbolic link to parent gradle wrapper...${NC}"
    ln -s ../gradlew ./gradlew
    ln -s ../gradle ./gradle
    chmod +x ./gradlew
fi

# Run the connectivity test
echo -e "${BOLD}Running connectivity test...${NC}"
./gradlew bootRun --args='--spring.profiles.active=connectivity-test'

echo
if [ $? -eq 0 ]; then
    echo -e "${GREEN}Connectivity test completed successfully.${NC}"
else
    echo -e "${RED}Connectivity test failed. Please check the logs for more details.${NC}"
fi

echo
echo -e "${BOLD}To run the full benchmark:${NC}"
echo "1. Run the benchmark locally: ./run_local.sh"
echo "2. Access the benchmark UI: http://localhost:8080"
echo "3. Access Prometheus metrics: http://localhost:8080/actuator/prometheus"
