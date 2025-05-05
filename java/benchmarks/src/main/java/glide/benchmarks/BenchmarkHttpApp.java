/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.benchmarks;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application for the Valkey GLIDE HTTP benchmarking server. This application compares the
 * performance of Valkey GLIDE with Redisson.
 */
@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = "glide.benchmarks")
public class BenchmarkHttpApp {

    public static void main(String[] args) {
        SpringApplication.run(BenchmarkHttpApp.class, args);
    }
}
