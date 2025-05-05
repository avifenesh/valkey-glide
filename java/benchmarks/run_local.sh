#!/bin/bash

# Colors for terminal output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BOLD='\033[1m'
NC='\033[0m' # No Color

echo -e "${BOLD}Valkey Glide Benchmark - Local Execution${NC}"
echo "---------------------------------------"

# Process arguments
while [[ "$#" -gt 0 ]]; do
  case $1 in
    --help)
      echo -e "${BOLD}Usage:${NC}"
      echo "  ./run_local.sh [options]"
      echo ""
      echo "Options:"
      echo "  --help              Show this help message"
      echo "  --profile <name>    Spring profile to activate (default: default)"
      echo "  --additional-args   Additional arguments to pass to Spring Boot"
      echo ""
      echo "Examples:"
      echo "  ./run_local.sh"
      echo "  ./run_local.sh --profile=metrics"
      echo "  ./run_local.sh --additional-args=\"--server.port=8081\""
      exit 0
      ;;
    --profile=*)
      PROFILE="${1#*=}"
      shift
      ;;
    --additional-args=*)
      ADDITIONAL_ARGS="${1#*=}"
      shift
      ;;
    *)
      echo -e "${RED}Unknown parameter: $1${NC}"
      echo "Use --help to see available options"
      exit 1
      ;;
  esac
done

# Set default values
PROFILE=${PROFILE:-default}
ARGS="--spring.profiles.active=${PROFILE}"

if [ ! -z "$ADDITIONAL_ARGS" ]; then
  ARGS="${ARGS} ${ADDITIONAL_ARGS}"
fi

# Check if the native library has been built
NATIVE_LIB_PATH="/home/fedora/valkey-glide/java/target/release/libglide_rs.so"

if [ ! -f "$NATIVE_LIB_PATH" ]; then
  echo -e "${YELLOW}Native library not found at $NATIVE_LIB_PATH${NC}"
  echo -e "${BOLD}Building Valkey Glide core...${NC}"
  
  cd ../
  cargo build --release
  
  if [ $? -ne 0 ]; then
    echo -e "${RED}Valkey Glide core build failed. Cannot continue.${NC}"
    exit 1
  fi
  
  echo -e "${GREEN}Valkey Glide core built successfully.${NC}"
  cd benchmarks/
else
  echo -e "${GREEN}Native library already exists at $NATIVE_LIB_PATH${NC}"
fi

# Verify gradle wrapper exists
if [ ! -f "./gradlew" ]; then
  echo -e "${YELLOW}Gradle wrapper not found. Creating symbolic link to parent gradle wrapper...${NC}"
  ln -s ../gradlew ./gradlew
  ln -s ../gradle ./gradle
  chmod +x ./gradlew
fi

# Run the application
echo -e "${BOLD}Running Valkey Glide benchmark locally with profile: ${PROFILE}${NC}"
echo -e "${BOLD}Arguments: ${ARGS}${NC}"

./gradlew bootRun --args="${ARGS}" \
  -Djava.library.path=/home/fedora/valkey-glide/java/target/release \
  -Dbenchmark.skip.dependency.check=true

STATUS=$?

if [ $STATUS -eq 0 ]; then
  echo -e "${GREEN}Benchmark application completed successfully!${NC}"
else
  echo -e "${RED}Benchmark application failed with status code $STATUS${NC}"
  exit $STATUS
fi
