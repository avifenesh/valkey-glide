/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */

import static glide.api.logging.Logger.Level.ERROR;
import static glide.api.logging.Logger.Level.INFO;
import static glide.api.logging.Logger.Level.WARN;
import static glide.api.logging.Logger.log;
import static glide.api.models.GlideString.gs;
import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleMultiNodeRoute.ALL_NODES;

import glide.api.GlideClusterClient;
import glide.api.logging.Logger;
import glide.api.models.ClusterValue;
import glide.api.models.commands.InfoOptions.Section;
import glide.api.models.configuration.GlideClusterClientConfiguration;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.exceptions.ClosingException;
import glide.api.models.exceptions.ConnectionException;
import glide.api.models.exceptions.TimeoutException;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public class ElastiCacheBenchmarkTest {

    private static final String VALKEY_HOST = "clustercfg.testingprimevalkey.ey5v7d.use2.cache.amazonaws.com";
    private static final int VALKEY_PORT = 6379;
    private static final boolean VALKEY_USE_TLS = true;
    
    private static final int NUM_OPERATIONS = 1000; // Number of operations to perform
    private static final int VALUE_SIZE = 1000; // Size of value in bytes

    /**
     * Creates and returns a configured Valkey Cluster Client to connect to ElastiCache.
     */
    public static GlideClusterClient createClient()
            throws CancellationException, ExecutionException, InterruptedException {
        // Configure for ElastiCache cluster
        NodeAddress nodeAddress = NodeAddress.builder()
            .host(VALKEY_HOST)
            .port(VALKEY_PORT)
            .build();

        GlideClusterClientConfiguration config =
                GlideClusterClientConfiguration.builder()
                        .address(nodeAddress)
                        .useTLS(VALKEY_USE_TLS)
                        .build();

        log(INFO, "benchmark", "Connecting to " + VALKEY_HOST + ":" + VALKEY_PORT + " with TLS: " + VALKEY_USE_TLS);
        GlideClusterClient client = GlideClusterClient.createClient(config).get();
        return client;
    }

    /**
     * Generates a random string of the specified size.
     */
    private static String generateRandomValue(int size) {
        StringBuilder sb = new StringBuilder(size);
        for (int i = 0; i < size; i++) {
            sb.append((char) ('a' + (Math.random() * 26)));
        }
        return sb.toString();
    }

    /**
     * Performs the benchmark test.
     */
    public static void runBenchmark(GlideClusterClient client) 
            throws ExecutionException, InterruptedException {
        
        String testValue = generateRandomValue(VALUE_SIZE);
        
        log(INFO, "benchmark", "Starting SET benchmark with " + NUM_OPERATIONS + " operations, value size: " + VALUE_SIZE + " bytes");
        
        // Perform SET operations
        Instant setStart = Instant.now();
        for (int i = 0; i < NUM_OPERATIONS; i++) {
            String key = "benchmark:key:" + UUID.randomUUID().toString();
            client.set(gs(key), gs(testValue)).get(); // Using get() to ensure operation completes
        }
        Instant setEnd = Instant.now();
        double setDurationSeconds = Duration.between(setStart, setEnd).toMillis() / 1000.0;
        double setOpsPerSecond = NUM_OPERATIONS / setDurationSeconds;
        
        log(INFO, "benchmark", String.format("SET benchmark completed in %.2f seconds, %.2f ops/second", 
                setDurationSeconds, setOpsPerSecond));

        // Run PING benchmark
        log(INFO, "benchmark", "Starting PING benchmark with " + NUM_OPERATIONS + " operations");
        
        Instant pingStart = Instant.now();
        for (int i = 0; i < NUM_OPERATIONS; i++) {
            client.ping().get(); // Using get() to ensure operation completes
        }
        Instant pingEnd = Instant.now();
        double pingDurationSeconds = Duration.between(pingStart, pingEnd).toMillis() / 1000.0;
        double pingOpsPerSecond = NUM_OPERATIONS / pingDurationSeconds;
        
        log(INFO, "benchmark", String.format("PING benchmark completed in %.2f seconds, %.2f ops/second", 
                pingDurationSeconds, pingOpsPerSecond));

        // Display cluster info
        try {
            ClusterValue<String> infoResponse = client.info(new Section[] {Section.CLUSTER}, ALL_NODES).get();
            log(INFO, "benchmark", "Cluster info: " + infoResponse.getMultiValue());
        } catch (Exception e) {
            log(WARN, "benchmark", "Could not retrieve cluster info: " + e.getMessage());
        }
    }

    /**
     * Main entry point with exception handling.
     */
    public static void main(String[] args) {
        Logger.setLoggerConfig(INFO);
        
        try (GlideClusterClient client = createClient()) {
            log(INFO, "benchmark", "Connected to cluster successfully");
            
            // Run the benchmark
            runBenchmark(client);
            
            log(INFO, "benchmark", "Benchmark completed successfully");
        } catch (CancellationException e) {
            log(ERROR, "glide", "Request cancelled: " + e.getMessage());
        } catch (InterruptedException e) {
            log(ERROR, "glide", "Client interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            // All Glide errors will be handled as ExecutionException
            if (e.getCause() instanceof ClosingException) {
                if (e.getMessage().contains("NOAUTH")) {
                    log(ERROR, "glide", "Authentication error encountered: " + e.getMessage());
                } else {
                    log(WARN, "glide", "Client has closed: " + e.getMessage());
                }
            } else if (e.getCause() instanceof ConnectionException) {
                log(ERROR, "glide", "Connection error encountered: " + e.getMessage());
            } else if (e.getCause() instanceof TimeoutException) {
                log(ERROR, "glide", "Timeout encountered: " + e.getMessage());
            } else {
                log(ERROR, "glide", "Execution error encountered: " + e.getCause());
            }
        } catch (Exception e) {
            log(ERROR, "glide", "Unexpected error: " + e.getMessage());
        }
    }
}
