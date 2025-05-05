FROM eclipse-temurin:17-jdk AS builder

# Set working directory
WORKDIR /app

# Copy files needed for gradle build
COPY settings.gradle .
COPY build.gradle .
COPY gradlew .
COPY gradle gradle
COPY src src

# Make gradlew executable
RUN chmod +x ./gradlew

# Build the application
RUN ./gradlew bootJar --no-daemon

# Create a smaller runtime image
FROM eclipse-temurin:17-jre

WORKDIR /app

# Copy the built jar file
COPY --from=builder /app/build/libs/valkey-glide-benchmark.jar app.jar

# Create directory for native libraries
RUN mkdir -p /app/libs/native

# Copy libvalkey_glide.so to allow runtime loading
COPY --from=builder /app/build/classes/java/main/glide/benchmarks/libs/native/libvalkey_glide.so /app/libs/native/libvalkey_glide.so

# Add a healthcheck script
COPY --from=builder /app/src/main/java/glide/benchmarks/utils/verify_native_libs.sh /app/verify_native_libs.sh
RUN chmod +x /app/verify_native_libs.sh

# Set environment variables with enhanced library loading
ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxGCPauseMillis=50 -Djava.security.egd=file:/dev/./urandom -Djava.library.path=/app/libs/native -Dglide.native.library.path=/app/libs/native"

# Set healthcheck
HEALTHCHECK --interval=30s --timeout=3s CMD curl -f http://localhost:8080/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
