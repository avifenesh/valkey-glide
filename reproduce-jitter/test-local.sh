#!/bin/bash
echo '============================================'
echo 'TLS Connection Latency Test (Local Valkey)'
echo '============================================'
echo ''

cd /app/test-local

cat > index.mjs << 'EOF'
import { log } from 'node:console';
import { hrtime } from 'node:process';
import { GlideClient } from '@valkey/valkey-glide';

const host = process.env.VALKEY_HOST || 'localhost';
const port = parseInt(process.env.VALKEY_PORT || '6379', 10);

const start = hrtime.bigint();

const client = await GlideClient.createClient({
  addresses: [{ host, port }],
  advancedConfiguration: { connectionTimeout: 10000 },
  useTLS: true,
  tlsAdvancedConfiguration: {
    insecure: true, // Skip cert validation for self-signed certs
  },
});

const end = hrtime.bigint();
log(Number(end - start) / 1e6);
client.close();
EOF

echo 'Testing LOCAL build (with jitter entropy issue)...'
echo 'Single run tests:'
for i in 1 2 3; do
  echo -n "  Run $i: "
  node index.mjs
done

echo ''
echo 'Parallel test (3x25 = 75 connections):'
rm -f results.txt
for i in 1 2 3; do
  for j in $(seq 1 25); do
    node index.mjs >> results.txt &
  done
  wait
done
echo -n '  Average: '
awk '{total+=$1; count+=1} END {printf "%.3f ms\n", total/count}' results.txt

echo ''
echo '============================================'
