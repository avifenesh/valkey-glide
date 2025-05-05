#!/bin/bash

# Colors for terminal output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BOLD='\033[1m'
NC='\033[0m' # No Color

echo -e "${BOLD}Valkey GLIDE Native Library Verification${NC}"
echo "---------------------------------------"

# Check if the expected library exists
EXPECTED_LIB_PATH="/app/libs/native/libvalkey_glide.so"
if [ ! -f "$EXPECTED_LIB_PATH" ]; then
    echo -e "${RED}ERROR: Native library not found at $EXPECTED_LIB_PATH${NC}"
    echo "The native library is required for Valkey GLIDE to function correctly."
    exit 1
fi

echo -e "${GREEN}Found native library at $EXPECTED_LIB_PATH${NC}"
echo -e "File details:"

# Show file info
ls -la "$EXPECTED_LIB_PATH"

# Check file permissions
if [ ! -r "$EXPECTED_LIB_PATH" ]; then
    echo -e "${RED}ERROR: Native library is not readable${NC}"
    exit 1
fi

# Check if the library is properly formatted
if file "$EXPECTED_LIB_PATH" | grep -q "shared object"; then
    echo -e "${GREEN}Library is a valid shared object${NC}"
else
    echo -e "${RED}ERROR: Library does not appear to be a valid shared object${NC}"
    echo "File type details: $(file $EXPECTED_LIB_PATH)"
    exit 1
fi

# Verify JVM can access the library path
echo -e "${BOLD}Verifying JVM library path...${NC}"

# Check if java is installed
if ! command -v java &> /dev/null; then
    echo -e "${RED}ERROR: java command not found, cannot verify JVM library path${NC}"
    exit 1
fi

# Print Java library path
echo -e "Current Java library path setting:"
java -XshowSettings:properties -version 2>&1 | grep 'java.library.path'

echo -e "${BOLD}Library verification successful.${NC}"
echo -e "${GREEN}Native library is present and appears valid.${NC}"

exit 0
