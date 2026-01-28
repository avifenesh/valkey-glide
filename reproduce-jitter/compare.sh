#!/bin/bash
set -e

echo "============================================"
echo "TLS Connection Latency Comparison"
echo "============================================"
echo ""

echo "Testing NPM package (@valkey/valkey-glide latest)..."
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
echo ""

echo "============================================"
echo "Comparison complete!"
echo "============================================"
