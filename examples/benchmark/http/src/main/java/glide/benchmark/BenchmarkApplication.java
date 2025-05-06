package glide.benchmark;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Main entry point for the Valkey Glide benchmark application.
 * This Spring Boot application provides HTTP endpoints for comparing
 * Valkey Glide and Redisson performance using the oha tool.
 */
@SpringBootApplication
@EnableAsync
public class BenchmarkApplication {

    public static void main(String[] args) {
        SpringApplication.run(BenchmarkApplication.class, args);
    }
}
