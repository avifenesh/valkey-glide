#!/bin/bash

# Colors for terminal output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BOLD='\033[1m'
NC='\033[0m' # No Color

echo -e "${BOLD}Valkey Glide Benchmark Database Population${NC}"
echo "---------------------------------------"

# Check if there are any arguments
if [ "$#" -gt 0 ]; then
  if [ "$1" == "--help" ]; then
    echo -e "${BOLD}Usage:${NC}"
    echo "  ./populate_database.sh [options]"
    echo ""
    echo "Options:"
    echo "  --client <glide|redisson>  Specify the client to use for populating (default: glide)"
    echo "  --keys <number>            Number of keys to populate (default: 500000)"
    echo "  --threads <number>         Number of threads to use (default: 16)"
    echo "  --batch-size <number>      Batch size for operations (default: 1000)"
    echo "  --word-count <number>      Word count for generated values (default: 30)"
    echo "  --help                     Show this help message"
    exit 0
  fi
fi

# Process arguments
CLIENT="glide"
KEYS=500000
THREADS=16
BATCH_SIZE=1000
WORD_COUNT=30

while [[ "$#" -gt 0 ]]; do
  case $1 in
    --client) CLIENT="$2"; shift ;;
    --keys) KEYS="$2"; shift ;;
    --threads) THREADS="$2"; shift ;;
    --batch-size) BATCH_SIZE="$2"; shift ;;
    --word-count) WORD_COUNT="$2"; shift ;;
    *) echo "Unknown parameter: $1"; exit 1 ;;
  esac
  shift
done

echo -e "${BOLD}Configuration:${NC}"
echo "- Client: $CLIENT"
echo "- Keys: $KEYS"
echo "- Threads: $THREADS"
echo "- Batch size: $BATCH_SIZE"
echo "- Word count: $WORD_COUNT"
echo ""

# Try local execution first (more reliable than Docker for dependencies)
echo -e "${BOLD}Starting database population using local execution...${NC}"

# Prepare the native library
if [ ! -f "src/main/resources/glide/benchmarks/libs/native/libglide_rs.so" ]; then
  echo -e "${YELLOW}Setting up native library...${NC}"
  if [ -f "./fix_native_loading.sh" ]; then
    ./fix_native_loading.sh
  else
    echo -e "${RED}Native library setup script not found. Database population may fail.${NC}"
  fi
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
fi

# Run the Java application directly with Gradle
echo -e "${BOLD}Running database population with Gradle...${NC}"
./gradlew bootRun --args="--spring.profiles.active=populate" \
  -Djava.library.path=/home/fedora/valkey-glide/java/target/release \
  -Dbenchmark.populate.enabled=true \
  -Dbenchmark.populate.client=$CLIENT \
  -Dbenchmark.populate.keys=$KEYS \
  -Dbenchmark.populate.threads=$THREADS \
  -Dbenchmark.populate.batch-size=$BATCH_SIZE \
  -Dbenchmark.populate.word-count=$WORD_COUNT

STATUS=$?

if [ $STATUS -eq 0 ]; then
  echo -e "${GREEN}Database population completed successfully!${NC}"
else
  echo -e "${RED}Database population failed with status code $STATUS${NC}"
  exit $STATUS
fi

echo -e "${YELLOW}TIP: To run benchmarks against the populated database, use:${NC}"
echo "  ./run_local.sh"
