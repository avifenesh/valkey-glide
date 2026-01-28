# TLS Connection Latency Reproduction

This reproduction environment demonstrates the ~3x TLS connection latency regression caused by aws-lc-rs v1.15.0+ CPU jitter entropy feature.

## What's In This Branch

This branch **reproduces the issue** WITHOUT the fix:
- ✅ aws-lc-rs >= 1.15.0 explicitly required (jitter entropy ENABLED)
- ❌ NO `.cargo/config.toml` fix
- ✅ tls-rustls-insecure feature enabled for testing

## Running on Amazon Linux 2023 (x86_64 EC2)

### Prerequisites
```bash
sudo dnf install -y nodejs gcc gcc-c++ cmake git unzip tar xz openssl-devel clang
```

### Install protoc
```bash
curl -LO https://github.com/protocolbuffers/protobuf/releases/download/v3.20.3/protoc-3.20.3-linux-x86_64.zip
sudo unzip protoc-3.20.3-linux-x86_64.zip -d /usr/local
rm protoc-3.20.3-linux-x86_64.zip
```

### Install Rust
```bash
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y
source "$HOME/.cargo/env"
```

### Clone and Build
```bash
git clone https://github.com/avifenesh/valkey-glide.git
cd valkey-glide
git checkout reproduce/jitter-entropy-issue

cd node
npm install
cd rust-client
npm install
cd ..

# Build the node client
export BUILD_MODE=release
npm run prereq
npm run build-protobuf
cd rust-client
npm run build:release
cd ..
npm run build:ts
```

### Create Test Directory
```bash
cd ~
mkdir jitter-test
cd jitter-test
npm init -y
npm install ~/valkey-glide/node
```

### Create Test Script
Create `index.mjs`:
```javascript
import { log } from 'node:console';
import { hrtime } from 'node:process';
import { GlideClusterClient } from '@valkey/valkey-glide';

const start = hrtime.bigint();

const client = await GlideClusterClient.createClient({
  addresses: [{
    host: 'clustercfg.test.eurjab.use1.cache.amazonaws.com',
    port: 6379,
  }],
  advancedConfiguration: {
    connectionTimeout: 10000,
  },
  credentials: {
    password: 'YOUR_PASSWORD_HERE',
  },
  useTLS: true,
});

const end = hrtime.bigint();
log(Number(end - start) / 1e6);
client.close();
```

### Run Tests

Single connection tests:
```bash
node index.mjs  # Run 3 times
```

Parallel test (75 connections):
```bash
cat > run.sh << 'EOF'
#!/bin/bash
rm -f results.txt
for i in {1..3}; do
  for j in {1..25}; do
    node index.mjs >> results.txt &
  done
  wait
done
awk '{total+=$1; count+=1} END {printf "%.3f\n", total/count}' results.txt
EOF

chmod +x run.sh
./run.sh
```

## Expected Results (Reproducing Issue)

Since this branch does **NOT** have the fix:
- **Single connections**: ~200-300ms each
- **Parallel average (75 connections)**: ~600-700ms

## Comparing With Fix

To see the improvement, switch to the fix branch:
```bash
cd ~/valkey-glide
git checkout fix/disable-jitter-entropy  # This has the .cargo/config.toml fix
# Rebuild following same steps above
```

With the fix:
- **Single connections**: ~80-100ms each
- **Parallel average**: ~150-200ms

**Performance improvement**: ~3x faster TLS connections
