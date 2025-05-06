# Valkey Glide Benchmark Suite

This benchmark suite compares the performance of Valkey Glide against Redisson client libraries when connecting to both Valkey 8 and Redis 6.2 servers under high load conditions.

## Overview

The benchmark suite consists of two separate benchmarking approaches:

1. **Standalone Benchmark**: A Java application that directly benchmarks the Valkey Glide client against ElastiCache (Valkey/Redis)
2. **HTTP Benchmark Server**: A Spring Boot application that exposes HTTP endpoints for benchmarking with the `oha` tool

Both benchmarks implement the same workload pattern:

- 80% GET operations for existing keys
- 20% GET operations for non-existing keys followed by SET operations
- Title metadata key pattern: `TITLE{id}:{locale}:{attribute}`
- Mixed value sizes (75% paragraph-sized, 25% single-word)

## Directory Structure

```
/examples/benchmark/
├── README.md                 # This file - main documentation
├── scripts/                  # Benchmark script collection
│   ├── run_elasticache_benchmark.sh    # Run standalone benchmark
│   └── run_benchmark_server.sh         # Run HTTP benchmark server
├── standalone/               # Standalone benchmark implementation
│   └── src/main/java/glide/benchmark/
│       └── ElastiCacheBenchmarkTest.java
└── http/                     # HTTP benchmark server implementation
    ├── build.gradle
    └── src/
        └── main/
            ├── java/glide/benchmark/   # Server code
            └── resources/              # Config files
```

## Running the Standalone Benchmark

The standalone benchmark directly measures the performance of the Valkey Glide client against either Valkey or Redis servers, without any HTTP overhead. It provides detailed metrics on throughput and latency.

```bash
# Navigate to benchmark directory
cd /examples/benchmark

# Run against Valkey server (default)
./scripts/run_elasticache_benchmark.sh

# Run against Redis server
./scripts/run_elasticache_benchmark.sh --server redis

# View help
./scripts/run_elasticache_benchmark.sh --help
```

The standalone benchmark performs the following steps:

1. Populates the database with test data using the TITLE key pattern
2. Runs the 80/20 GET/SET workload pattern
3. Measures P50/P90/P99 latency under different load levels

## Running the HTTP Benchmark Server

The HTTP benchmark provides a Spring Boot application with endpoints for client comparison using the `oha` load testing tool. This allows benchmarking both Valkey Glide and Redisson clients against both Valkey and Redis servers.

```bash
# Navigate to benchmark directory
cd /examples/benchmark

# Start the HTTP benchmark server
./scripts/run_benchmark_server.sh
```

Once the server is running, you can:

1. **Check server status**:  
   `curl http://localhost:8080/benchmark/status`

2. **Populate test data**:  

   ```bash
   # Populate Valkey with Glide client
   curl -X POST http://localhost:8080/benchmark/populate?serverType=valkey&clientType=glide
   
   # Populate Redis with Glide client
   curl -X POST http://localhost:8080/benchmark/populate?serverType=redis&clientType=glide
   ```

3. **Run benchmarks with oha**:

   ```bash
   # Glide client against Valkey
   oha -n 10000 -c 50 http://localhost:8080/benchmark/glide/valkey/TITLE{1..1000}:en_US:document
   
   # Glide client against Redis
   oha -n 10000 -c 50 http://localhost:8080/benchmark/glide/redis/TITLE{1..1000}:en_US:document
   
   # Redisson client against Valkey
   oha -n 10000 -c 50 http://localhost:8080/benchmark/redisson/valkey/TITLE{1..1000}:en_US:document
   
   # Redisson client against Redis
   oha -n 10000 -c 50 http://localhost:8080/benchmark/redisson/redis/TITLE{1..1000}:en_US:document
   ```

## Benchmark Methodology

### Key Pattern

All benchmarks use the following key pattern:

```
TITLE{id}:{locale}:{attribute}
```

Where:

- `{id}` is a numeric identifier (1 to N)
- `{locale}` is one of: "en_US", "es_ES", "fr_FR", "de_DE", "ja_JP"
- `{attribute}` is one of:
  - "document" (paragraph-sized value)
  - "is_kids_targeted" (single-word value)
  - "initial_xray_data" (paragraph-sized value)
  - "xray_metadata_payload" (paragraph-sized value)

### Workload Pattern

The benchmarks implement an 80/20 workload pattern:

- 80% GET operations for existing keys
- 20% GET operations for non-existing keys, followed by SET operations to populate them

This pattern mimics a realistic cache usage pattern where most operations are reads of existing data, with occasional writes for missing data.

### Performance Metrics

The benchmarks collect and report the following metrics:

1. **Throughput**
   - Operations per second

2. **Latency**
   - P50 (median) latency
   - P90 latency
   - P99 latency

3. **Cache Hit/Miss Rates**
   - Percentage of GET operations that resulted in hits vs. misses

## Client Features Compared

The benchmarks compare how each client handles:

1. Cluster topology discovery and updates
2. AZ-aware routing (Valkey Glide feature)
3. Connection pooling vs. multiplexing performance
4. Read replica utilization
5. Pipelining efficiency
