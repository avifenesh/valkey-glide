# Valkey Glide vs. Redisson Benchmark

This benchmark compares the performance of Valkey Glide Java client with Redisson client against AWS ElastiCache endpoints.

## Prerequisites

- Java 17+
- Docker and Docker Compose
- Rust/Cargo (for building Valkey Glide)
- Access to AWS ElastiCache endpoints

## Quick Start

Two helper scripts have been provided to assist with running the benchmarks:

### 1. Test Connectivity

To verify connectivity to both AWS ElastiCache endpoints:

```bash
./test_connectivity.sh
```

This will:

- Build Glide in release mode
- Run a connectivity test against both Valkey and Redis ElastiCache endpoints
- Report the results

### 2. Run Full Benchmark Suite

To build and run the full benchmark suite with Docker:

```bash
./build_and_run_docker.sh
```

This will:

- Build Glide in release mode
- Copy necessary native libraries
- Build the Docker image
- Start all required services (benchmark app, Prometheus, Grafana)
- Provide access URLs for the running services

## Benchmark Configuration

The configuration is handled through environment variables in the `.env` file:

- `VALKEY_HOST` - Valkey AWS ElastiCache endpoint
- `REDIS_HOST` - Redis AWS ElastiCache endpoint
- Connection pool settings for both clients
- Benchmark settings (key count, threads, etc.)

## Manual Setup

If you prefer to run the benchmark manually:

1. Build Valkey Glide core:

   ```bash
   cd ../../
   cargo build --release
   ```

2. Copy the native library:

   ```bash
   mkdir -p java/benchmarks/src/main/resources/glide/benchmarks/libs/native/
   cp target/release/libvalkey_glide.so java/benchmarks/src/main/resources/glide/benchmarks/libs/native/
   ```

3. Run the benchmark app:

   ```bash
   cd java/benchmarks
   ./gradlew bootRun
   ```

## Docker Setup

The Docker setup includes:

- Benchmark application (Spring Boot)
- Prometheus for metrics collection
- Grafana for visualization

To manually work with Docker:

- Build: `docker-compose build`
- Start: `docker-compose up -d`
- Stop: `docker-compose down`

## Available Endpoints

When running, the following endpoints are available:

- <http://localhost:8080/api/v1/glide/key/{key}> - GET operation with Glide
- <http://localhost:8080/api/v1/redisson/key/{key}> - GET operation with Redisson
- <http://localhost:8080/api/v1/{client}/key/{key}> (POST) - SET operation
- <http://localhost:8080/api/v1/{client}/status> - Client stats
- <http://localhost:8080/actuator/prometheus> - Prometheus metrics

## Metrics and Visualization

- Prometheus: <http://localhost:9090>
- Grafana: <http://localhost:3000>

## Troubleshooting

- **Build Issues**: The Dockerfile is set up to use the local Gradle configuration. If you encounter build issues, verify that the Gradle wrapper is properly set up.
- **Connectivity Issues**: Use the connectivity test to verify you can reach both ElastiCache endpoints.
- **Docker Problems**: Check Docker logs with `docker-compose logs`.
