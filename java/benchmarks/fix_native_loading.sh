#!/bin/bash

# Colors for terminal output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BOLD='\033[1m'
NC='\033[0m' # No Color

echo -e "${BOLD}Fix Native Library Loading for Docker${NC}"
echo "---------------------------------------"

# Source directory setup
NATIVE_LIB_SRC="../../java/target/release/libglide_rs.so"
NATIVE_LIB_DST="src/main/resources/glide/benchmarks/libs/native/libglide_rs.so"
NATIVE_LIB_DST_DIR="src/main/resources/glide/benchmarks/libs/native"

# Create destination directory if it doesn't exist
mkdir -p "$NATIVE_LIB_DST_DIR"
if [ $? -ne 0 ]; then
    echo -e "${RED}Failed to create destination directory.${NC}"
    exit 1
fi

# Check if the source library exists
if [ ! -f "$NATIVE_LIB_SRC" ]; then
    echo -e "${YELLOW}Warning: Source library not found at $NATIVE_LIB_SRC${NC}"
    echo -e "${YELLOW}Looking for alternative sources...${NC}"
    
    # Check in alternative locations
    ALT_LOCATIONS=(
        "../../java/target/debug/libglide_rs.so"
        "../../java/build/libs/release/libglide_rs.so"
        "../client/build/libs/native/libglide_rs.so"
        "../../target/release/libglide_rs.so"
    )
    
    FOUND=0
    for ALT_LOC in "${ALT_LOCATIONS[@]}"; do
        if [ -f "$ALT_LOC" ]; then
            echo -e "${GREEN}Found library at $ALT_LOC${NC}"
            NATIVE_LIB_SRC=$ALT_LOC
            FOUND=1
            break
        fi
    done
    
    if [ $FOUND -eq 0 ]; then
        echo -e "${RED}Could not find native library in any known location.${NC}"
        echo -e "${YELLOW}You need to build the Glide native library first.${NC}"
        
        read -p "Do you want to try building the Glide native library now? (y/n) " -n 1 -r
        echo
        
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            echo -e "${BOLD}Attempting to build Glide native library...${NC}"
            (cd ../../java && ./gradlew buildRust)
            
            if [ -f "$NATIVE_LIB_SRC" ]; then
                echo -e "${GREEN}Successfully built native library!${NC}"
            else
                echo -e "${RED}Failed to build native library.${NC}"
                echo -e "${YELLOW}You may need to build it manually or fix build issues.${NC}"
                exit 1
            fi
        else
            echo -e "${YELLOW}Please build the native library manually and try again.${NC}"
            exit 1
        fi
    fi
fi

# Copy the library
echo -e "${BOLD}Copying native library from $NATIVE_LIB_SRC to $NATIVE_LIB_DST${NC}"
cp -f "$NATIVE_LIB_SRC" "$NATIVE_LIB_DST"

if [ $? -ne 0 ]; then
    echo -e "${RED}Failed to copy native library.${NC}"
    exit 1
else
    echo -e "${GREEN}Successfully copied native library.${NC}"
fi

# Add custom class loader configuration to ensure library is properly loaded in Docker
CONFIG_FILE="src/main/resources/application.properties"

echo -e "${BOLD}Adding custom native library configuration to $CONFIG_FILE${NC}"

# Check if file exists
if [ ! -f "$CONFIG_FILE" ]; then
    echo -e "${RED}Configuration file $CONFIG_FILE does not exist.${NC}"
    exit 1
fi

# Check if already configured
if grep -q "native.library.path" "$CONFIG_FILE"; then
    echo -e "${YELLOW}Native library configuration already exists in $CONFIG_FILE${NC}"
else
    echo "" >> "$CONFIG_FILE"
    echo "# Native library loading configuration" >> "$CONFIG_FILE"
    echo "native.library.path=/app/libs/native" >> "$CONFIG_FILE"
    echo -e "${GREEN}Added native library configuration.${NC}"
fi

echo -e "${BOLD}Native library setup completed successfully.${NC}"
echo -e "${YELLOW}Note: When running in Docker, the library path is set to /app/libs/native${NC}"
echo -e "${YELLOW}      This is mapped in the Dockerfile.${NC}"
