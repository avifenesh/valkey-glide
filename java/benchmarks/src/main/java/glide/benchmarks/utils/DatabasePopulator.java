/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.benchmarks.utils;

import glide.benchmarks.clients.ValkeyClient;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Utility for populating Valkey database with test data */
@Component
@Slf4j
public class DatabasePopulator {

    @Value("${benchmark.populate.keys:500000}")
    private int totalKeys;

    @Value("${benchmark.populate.threads:16}")
    private int numThreads;

    @Value("${benchmark.populate.batch-size:1000}")
    private int batchSize;

    @Value("${benchmark.populate.word-count:30}")
    private int wordCount;

    @Value("${benchmark.populate.enabled:false}")
    private boolean populateEnabled;

    @Value("${benchmark.populate.client:glide}")
    private String clientToUse;

    /**
     * Create a CommandLineRunner bean that runs database population at startup when profile is set
     *
     * @param applicationContext Spring application context
     * @return CommandLineRunner
     */
    @Bean
    @Profile("populate")
    public CommandLineRunner populateDatabase(ApplicationContext applicationContext) {
        return args -> {
            if (populateEnabled) {
                ValkeyClient client;
                if ("redisson".equalsIgnoreCase(clientToUse)) {
                    client = applicationContext.getBean("redissonClient", ValkeyClient.class);
                    System.out.println("Using Redisson client for database population");
                } else {
                    client = applicationContext.getBean("glideClient", ValkeyClient.class);
                    System.out.println("Using Glide client for database population");
                }

                populateKeys(client);

                // Exit after population is complete when running with populate profile
                System.exit(0);
            }
        };
    }

    /**
     * Populate database with random keys and values
     *
     * @param client Valkey client to use
     */
    public void populateKeys(ValkeyClient client) {
        System.out.println(
                "Starting database population with "
                        + totalKeys
                        + " keys using "
                        + numThreads
                        + " threads");

        long startTime = System.currentTimeMillis();

        // Generate unique movie keys
        Set<String> uniqueKeys = generateUniqueKeys(totalKeys);
        System.out.println("Generated " + uniqueKeys.size() + " unique keys");

        // Distribute keys across threads
        String[] keysArray = uniqueKeys.toArray(new String[0]);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        AtomicInteger lastReportedCount = new AtomicInteger(0);

        // Use a more efficient approach with CompletableFuture for better concurrency
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            CompletableFuture<Void> future =
                    CompletableFuture.runAsync(
                            () -> {
                                int keysPerThread = keysArray.length / numThreads;
                                int startIdx = threadId * keysPerThread;
                                int endIdx =
                                        (threadId == numThreads - 1)
                                                ? keysArray.length
                                                : (threadId + 1) * keysPerThread;

                                try {
                                    // Process in batches for better performance
                                    for (int i = startIdx; i < endIdx; i += batchSize) {
                                        int batchEnd = Math.min(i + batchSize, endIdx);

                                        for (int j = i; j < batchEnd; j++) {
                                            String key = keysArray[j];
                                            String value = TextGenerator.generateLoremIpsum(wordCount);

                                            try {
                                                boolean success = client.set(key, value);
                                                if (success) {
                                                    int count = successCount.incrementAndGet();
                                                    // Report progress every 10,000 keys
                                                    if (count % 10000 == 0 && count > lastReportedCount.get()) {
                                                        lastReportedCount.set(count);
                                                        double percentage = count * 100.0 / totalKeys;
                                                        System.out.println(
                                                                String.format(
                                                                        "Progress: %d keys inserted (%.2f%%)", count, percentage));
                                                    }
                                                } else {
                                                    errorCount.incrementAndGet();
                                                }
                                            } catch (Exception e) {
                                                errorCount.incrementAndGet();
                                                // Minimal logging to improve performance
                                                if (errorCount.get() % 1000 == 0) {
                                                    System.err.println("Error setting keys: " + e.getMessage());
                                                }
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                    System.err.println("Thread " + threadId + " error: " + e.getMessage());
                                }
                            },
                            executor);

            futures.add(future);
        }

        // Wait for all threads to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        executor.shutdown();

        long endTime = System.currentTimeMillis();
        double durationSeconds = (endTime - startTime) / 1000.0;

        System.out.println("Database population completed:");
        System.out.println("Total keys: " + totalKeys);
        System.out.println("Successful: " + successCount.get());
        System.out.println("Failed: " + errorCount.get());
        System.out.println(String.format("Duration: %.2f seconds", durationSeconds));
        System.out.println(
                String.format("Rate: %.2f keys/second", successCount.get() / durationSeconds));
    }

    /**
     * Generate a set of unique keys for testing
     *
     * @param count number of keys to generate
     * @return set of unique keys
     */
    private Set<String> generateUniqueKeys(int count) {
        Set<String> keys = new HashSet<>(count);
        int attempts = 0;
        int maxAttempts = count * 2;

        while (keys.size() < count && attempts < maxAttempts) {
            keys.add(TextGenerator.generateMovieKey());
            attempts++;
        }

        if (keys.size() < count) {
            System.out.println(
                    "WARNING: Could only generate "
                            + keys.size()
                            + " unique keys after "
                            + maxAttempts
                            + " attempts");
        }

        return keys;
    }
}
