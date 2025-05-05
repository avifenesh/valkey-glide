#!/bin/bash

# Colors for terminal output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BOLD='\033[1m'
NC='\033[0m' # No Color

echo -e "${BOLD}Valkey Glide Benchmark Pre-built Docker Build and Run${NC}"
echo "---------------------------------------"

# Step 1: Make sure the native library is properly set up
echo -e "${BOLD}Step 1: Setting up native library...${NC}"
if [ -f "./fix_native_loading.sh" ]; then
    ./fix_native_loading.sh
    if [ $? -ne 0 ]; then
        echo -e "${RED}Native library setup failed. Cannot continue.${NC}"
        exit 1
    fi
else
    echo -e "${RED}Native library setup script not found. Trying to continue...${NC}"
    mkdir -p src/main/resources/glide/benchmarks/libs/native/
    
    # Try to find the library in known locations
    if [ -f "../java/target/release/libglide_rs.so" ]; then
        cp ../java/target/release/libglide_rs.so src/main/resources/glide/benchmarks/libs/native/libglide_rs.so
    else
        echo -e "${RED}Native library not found. This could cause issues later.${NC}"
    fi
fi

# Step 2: Pre-build the JAR file locally
echo -e "${BOLD}Step 2: Pre-building JAR file locally...${NC}"
# Create build directory
mkdir -p build/libs/

# Check if the JAR already exists
if [ -f "build/libs/valkey-glide-benchmark.jar" ] && [ -s "build/libs/valkey-glide-benchmark.jar" ]; then
    echo -e "${YELLOW}JAR file already exists. Using existing file.${NC}"
    echo -e "${YELLOW}Delete build/libs/valkey-glide-benchmark.jar if you want to rebuild.${NC}"
else
    echo -e "${GREEN}Building JAR file with Gradle...${NC}"
    ./gradlew bootJar
    
    if [ $? -ne 0 ] || [ ! -f "build/libs/valkey-glide-benchmark.jar" ]; then
        echo -e "${RED}JAR build failed. Cannot continue.${NC}"
        exit 1
    fi
    
    echo -e "${GREEN}JAR file built successfully.${NC}"
fi

# Step 3: Create a simpler Docker file that uses the pre-built JAR
echo -e "${BOLD}Step 3: Creating simplified Dockerfile for pre-built JAR...${NC}"
cat > Dockerfile.prebuilt << EOF
FROM eclipse-temurin:17-jre

WORKDIR /app

# Copy the pre-built jar file
COPY build/libs/valkey-glide-benchmark.jar app.jar

# Create directory for native libraries
RUN mkdir -p /app/libs/native

# Copy libglide_rs.so to allow runtime loading
COPY src/main/resources/glide/benchmarks/libs/native/libglide_rs.so /app/libs/native/libglide_rs.so

# Set environment variables with enhanced library loading
ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxGCPauseMillis=50 -Djava.security.egd=file:/dev/./urandom -Djava.library.path=/app/libs/native -Dglide.native.library.path=/app/libs/native"

# Set healthcheck
HEALTHCHECK --interval=30s --timeout=3s CMD curl -f http://localhost:8080/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
EOF

# Step 4: Build and run the Docker container with the pre-built JAR
echo -e "${BOLD}Step 4: Building and running Docker container with pre-built JAR...${NC}"

echo -e "${GREEN}Building Docker image from pre-built JAR...${NC}"
docker build -t valkey-glide-benchmark -f Dockerfile.prebuilt .

if [ $? -ne 0 ]; then
    echo -e "${RED}Docker build failed. Please check the error and try again.${NC}"
    exit 1
fi

echo -e "${GREEN}Docker build successful.${NC}"

echo -e "${BOLD}Starting Docker services...${NC}"
# First stop any existing containers to avoid port conflicts
docker compose down
if [ $? -ne 0 ]; then
    echo -e "${YELLOW}Warning: Failed to stop existing services. May see port conflicts.${NC}"
fi

# Start the services using our pre-built image for the benchmark app
BENCHMARK_IMAGE=valkey-glide-benchmark docker compose up -d

if [ $? -ne 0 ]; then
    echo -e "${RED}Failed to start Docker services. Please check the error and try again.${NC}"
    exit 1
fi

echo -e "${GREEN}Docker services started successfully.${NC}"
echo

# Wait for services to be ready
echo -e "${BOLD}Waiting for services to be ready...${NC}"
echo "This may take a few moments..."
sleep 10

# Check if services are running
echo -e "${BOLD}Checking services status:${NC}"
docker compose ps

echo
echo -e "${BOLD}Access the benchmark application:${NC}"
echo "- Benchmark UI: http://localhost:8080"
echo "- Grafana Dashboard: http://localhost:3000"
echo "- Prometheus: http://localhost:9090"
echo
echo -e "${BOLD}To stop the services:${NC}"
echo "docker compose down"
