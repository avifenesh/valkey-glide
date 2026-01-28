import { log } from 'node:console';
import { hrtime } from 'node:process';

import { GlideClusterClient } from '@valkey/valkey-glide';

const host = process.env.VALKEY_HOST || 'clustercfg.test.eurjab.use1.cache.amazonaws.com';
const port = parseInt(process.env.VALKEY_PORT || '6379', 10);
const password = process.env.VALKEY_PASSWORD || '';

const start = hrtime.bigint();

const client = await GlideClusterClient.createClient({
  addresses: [
    {
      host,
      port,
    },
  ],
  advancedConfiguration: {
    connectionTimeout: 10000,
  },
  credentials: {
    password,
  },
  useTLS: true,
});

const end = hrtime.bigint();

log(Number(end - start) / 1e6);

client.close();
