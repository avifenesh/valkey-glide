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

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
  echo -e "${RED}Docker is not running. Please start Docker and try again.${NC}"
  exit 1
fi

# Override environment variables to enable database population
echo -e "${BOLD}Starting database population...${NC}"
docker compose run \
  -e SPRING_PROFILES_ACTIVE=populate \
  -e BENCHMARK_POPULATE_ENABLED=true \
  -e BENCHMARK_POPULATE_CLIENT=$CLIENT \
  -e BENCHMARK_POPULATE_KEYS=$KEYS \
  -e BENCHMARK_POPULATE_THREADS=$THREADS \
  -e BENCHMARK_POPULATE_BATCH_SIZE=$BATCH_SIZE \
  -e BENCHMARK_POPULATE_WORD_COUNT=$WORD_COUNT \
  benchmark-app

STATUS=$?

if [ $STATUS -eq 0 ]; then
  echo -e "${GREEN}Database population completed successfully!${NC}"
else
  echo -e "${RED}Database population failed with status code $STATUS${NC}"
  exit $STATUS
fi

echo -e "${YELLOW}TIP: To run benchmarks against the populated database, use:${NC}"
echo "  docker compose up -d"
