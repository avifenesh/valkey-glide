# Valkey Glide vs. Redisson Benchmark

This benchmark compares the performance of Valkey Glide Java client with Redisson client against Valkey/Redis endpoints.

## Key Performance Differences

- **Valkey Glide**: Uses a singleton thread-safe multiplexer architecture for optimal performance
- **Redisson**: Uses traditional connection pooling approach

## Prerequisites

- Java 11+
- Rust/Cargo (for building Valkey Glide native core)
- Access to Valkey/Redis endpoints

## Quick Start

Three helper scripts have been provided to assist with running the benchmarks:

### 1. Test Connectivity

To verify connectivity to Valkey/Redis endpoints:

```bash
./test_connectivity.sh
```

This will:

- Build Glide native core in release mode
- Run a connectivity test against configured endpoints
- Report the results

### 2. Populate Database for Benchmarks

To populate the database with test data:

```bash
./populate_database.sh --client glide --keys 500000 --threads 16
```

Options:

- `--client`: Which client to use for population (`glide` or `redisson`)
- `--keys`: Number of keys to populate
- `--threads`: Number of threads to use
- `--batch-size`: Batch size for operations
- `--word-count`: Word count for generated values

This will:

- Build and set up the native library
- Populate the database with random key-value pairs
- Use optimized batch operations for improved performance

### 3. Run Full Benchmark Suite

To run the benchmark suite locally (recommended method):

```bash
./run_local.sh
```

Options:

- `--profile=<name>`: Specify a Spring profile to activate (default: default)
- `--additional-args="<args>"`: Pass additional arguments to the benchmark app

This will:

- Build the native library if needed
- Configure the benchmarking application
- Run all benchmark tests
- Expose metrics endpoints

## Benchmark Configuration

The configuration is handled through environment variables in the `.env` file:

- Connection settings for Valkey/Redis endpoints
- Connection pool settings for both clients (for fair comparison)
- Benchmark settings (key count, threads, etc.)

## Manual Setup

If you prefer to run specific benchmark components manually:

1. Build Valkey Glide core:

   ```bash
   cd ../
   cargo build --release
   ```

2. Run the benchmark app:

   ```bash
   cd benchmarks
   ./gradlew bootRun
   ```

## Performance Optimizations

Several optimizations have been implemented to improve benchmark performance:

### Glide Client Architecture

- **Singleton Thread-Safe Multiplexer**: More efficient than traditional connection pooling
- Advanced metrics collection including P95/P99 latency tracking
- TCP settings optimized for high-throughput workloads

### Redisson Client Configuration

- Using the same configuration as the customer's environment for fair comparison
- Connection pooling parameters matched to customer settings

## Available Endpoints

When running, the following endpoints are available:

- <http://localhost:8080/api/v1/glide/key/{key}> - GET operation with Glide
- <http://localhost:8080/api/v1/redisson/key/{key}> - GET operation with Redisson
- <http://localhost:8080/api/v1/{client}/key/{key}> (POST) - SET operation
- <http://localhost:8080/api/v1/{client}/status> - Client stats
- <http://localhost:8080/actuator/prometheus> - Prometheus metrics

## Troubleshooting

- **Native Library Issues**: Run the `fix_native_loading.sh` script to resolve issues with native library loading.
- **Connectivity Issues**: Use the connectivity test to verify you can reach endpoints.
- **Build Issues**: Ensure Java 11 is being used (not Java 17) as the benchmark is built for Java 11 compatibility
