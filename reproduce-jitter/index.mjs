import { log } from 'node:console';
import { hrtime } from 'node:process';
import { readFileSync } from 'node:fs';

import { GlideClient } from '@valkey/valkey-glide';

const host = process.env.VALKEY_HOST || 'localhost';
const port = parseInt(process.env.VALKEY_PORT || '6380', 10);
const caCertPath = process.env.CA_CERT_PATH || '/certs/ca.crt';

// Read CA certificate
let rootCertificates;
try {
  rootCertificates = readFileSync(caCertPath);
} catch (e) {
  // If no CA cert, try without it
}

const start = hrtime.bigint();

const clientConfig = {
  addresses: [
    {
      host,
      port,
    },
  ],
  advancedConfiguration: {
    connectionTimeout: 10000,
  },
  useTLS: true,
};

// Add CA cert if available
if (rootCertificates) {
  clientConfig.tlsAdvancedConfiguration = {
    rootCertificates,
  };
}

const client = await GlideClient.createClient(clientConfig);

const end = hrtime.bigint();

log(Number(end - start) / 1e6);

client.close();
