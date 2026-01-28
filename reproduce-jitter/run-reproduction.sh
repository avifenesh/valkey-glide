#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

echo "Step 1: Generating TLS certificates..."
./generate-certs.sh

echo ""
echo "Step 2: Building and running containers..."
docker compose up --build

echo ""
echo "Cleaning up..."
docker compose down
