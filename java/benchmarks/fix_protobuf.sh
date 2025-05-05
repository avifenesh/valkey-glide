#!/bin/bash

# Colors for terminal output
GREEN='\033[0;32m'
RED='\033[0;31m'
BOLD='\033[1m'
NC='\033[0m' # No Color

echo -e "${BOLD}Valkey Glide Benchmark - Protobuf Fixer${NC}"
echo "-----------------------------------"
echo -e "${BOLD}This script will install protobuf v29.0 from source${NC}"
echo

# Check current protobuf version
current_version=$(protoc --version | awk '{print $2}')
echo -e "Current protobuf version: ${BOLD}$current_version${NC}"
echo -e "Required version: ${BOLD}29.0+${NC}"

if [[ $current_version == 29.* ]] || [[ $current_version == 3[0-9].* ]]; then
    echo -e "${GREEN}Protobuf version is already compatible. No action needed.${NC}"
    exit 0
fi

echo
echo -e "${BOLD}Installing dependencies...${NC}"
sudo dnf install -y autoconf automake libtool curl make g++ unzip

# Set up a temporary directory for the build
cd /tmp
rm -rf protobuf-29.0 protobuf-29.0.tar.gz
echo -e "${BOLD}Downloading protobuf v29.0...${NC}"
curl -OL https://github.com/protocolbuffers/protobuf/releases/download/v29.0/protobuf-29.0.tar.gz

echo -e "${BOLD}Extracting archive...${NC}"
tar -xzf protobuf-29.0.tar.gz
cd protobuf-29.0

echo -e "${BOLD}Creating build directory...${NC}"
mkdir -p cmake/build
cd cmake/build

echo -e "${BOLD}Configuring build...${NC}"
cmake -DCMAKE_CXX_STANDARD=11 -Dprotobuf_BUILD_TESTS=OFF ..

echo -e "${BOLD}Building protobuf (this may take a few minutes)...${NC}"
make -j$(nproc)

echo -e "${BOLD}Installing protobuf...${NC}"
sudo make install
sudo ldconfig

# Verify installation
echo
echo -e "${BOLD}Verifying installation...${NC}"
new_version=$(protoc --version | awk '{print $2}')
echo -e "New protobuf version: ${BOLD}$new_version${NC}"

if [[ $new_version == 29.* ]] || [[ $new_version == 3[0-9].* ]]; then
    echo -e "${GREEN}Installation successful! Protobuf has been updated to the required version.${NC}"
else
    echo -e "${RED}Installation may have failed. The protobuf version does not appear to be updated.${NC}"
    echo -e "You may need to manually update PATH to include the newly installed protoc."
    exit 1
fi

echo
echo -e "${BOLD}You can now proceed with building and running the benchmark:${NC}"
echo "1. Test connectivity: ./test_connectivity.sh"
echo "2. Run benchmark with Docker: ./build_and_run_docker.sh"
