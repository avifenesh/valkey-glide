#!/bin/bash
# Generate self-signed certificates for Valkey TLS
set -e

CERT_DIR="$(dirname "$0")/certs"
mkdir -p "$CERT_DIR"
cd "$CERT_DIR"

# Generate CA key and certificate
openssl genrsa -out ca.key 4096
openssl req -x509 -new -nodes -sha256 -key ca.key -days 365 -out ca.crt \
    -subj "/CN=Valkey-Test-CA"

# Generate server key and certificate
openssl genrsa -out server.key 2048
openssl req -new -sha256 -key server.key -out server.csr \
    -subj "/CN=valkey"

cat > server.ext << EOF
authorityKeyIdentifier=keyid,issuer
basicConstraints=CA:FALSE
keyUsage = digitalSignature, nonRepudiation, keyEncipherment, dataEncipherment
subjectAltName = @alt_names

[alt_names]
DNS.1 = valkey
DNS.2 = localhost
IP.1 = 127.0.0.1
EOF

openssl x509 -req -in server.csr -CA ca.crt -CAkey ca.key -CAcreateserial \
    -out server.crt -days 365 -sha256 -extfile server.ext

rm -f server.csr server.ext

chmod 644 *.crt *.key

echo "Certificates generated in $CERT_DIR"
