#!/bin/bash
set -e

echo "============================================"
echo "TLS Connection Latency Comparison"
echo "============================================"
echo ""
echo "VALKEY_HOST: ${VALKEY_HOST:-not set}"
echo "VALKEY_PORT: ${VALKEY_PORT:-6379}"
echo "VALKEY_PASSWORD: ${VALKEY_PASSWORD:+[SET]}"
echo ""

if [ -z "$VALKEY_HOST" ] || [ -z "$VALKEY_PASSWORD" ]; then
  echo "ERROR: Please set VALKEY_HOST and VALKEY_PASSWORD environment variables"
  echo "Example:"
  echo "  export VALKEY_HOST=clustercfg.test.eurjab.use1.cache.amazonaws.com"
  echo "  export VALKEY_PASSWORD=your_password"
  exit 1
fi

echo "Testing NPM package (@valkey/valkey-glide latest from npm)..."
echo "Single run tests:"
cd /app/test-npm
for i in {1..3}; do
  echo -n "  Run $i: "
  node index.mjs
done

echo ""
echo "Parallel run test (3x25 = 75 connections):"
echo -n "  Average: "
./run.sh
echo " ms"
echo ""

echo "============================================"
echo ""

echo "Testing LOCAL build (PR branch with fix)..."
echo "Single run tests:"
cd /app/test-local
for i in {1..3}; do
  echo -n "  Run $i: "
  node index.mjs
done

echo ""
echo "Parallel run test (3x25 = 75 connections):"
echo -n "  Average: "
./run.sh
echo " ms"
echo ""

echo "============================================"
echo "Comparison complete!"
echo "============================================"
