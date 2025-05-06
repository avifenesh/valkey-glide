/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.examples;

import static glide.api.logging.Logger.Level.ERROR;
import static glide.api.logging.Logger.Level.INFO;
import static glide.api.logging.Logger.Level.WARN;
import static glide.api.logging.Logger.log;
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

public class ClusterExample {

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

    // ElastiCache endpoint configuration
    private static final String VALKEY_HOST = "clustercfg.testingprimevalkey.ey5v7d.use2.cache.amazonaws.com";
    private static final int VALKEY_PORT = 6379;
    private static final boolean VALKEY_USE_TLS = true;

    /**
     * Creates and returns a <code>GlideClusterClient</code> instance.
     *
     * <p>This function initializes a <code>GlideClusterClient</code> with the provided list of nodes.
     * The list may contain the address of one or more cluster nodes, and the client will
     * automatically discover all nodes in the cluster.
     *
     * @return A <code>GlideClusterClient</code> connected to the discovered nodes.
     * @throws CancellationException if the operation is cancelled.
     * @throws ExecutionException if the client fails due to execution errors.
     * @throws InterruptedException if the operation is interrupted.
     */
    public static GlideClusterClient createClient(List<NodeAddress> nodeList)
            throws CancellationException, ExecutionException, InterruptedException {
        // Check `GlideClusterClientConfiguration` for additional options.
        GlideClusterClientConfiguration config =
                GlideClusterClientConfiguration.builder()
                        .addresses(nodeList)
                        // Enable TLS for ElastiCache
                        .useTLS(VALKEY_USE_TLS)
                        .build();

        GlideClusterClient client = GlideClusterClient.createClient(config).get();
        return client;
    }

    /**
     * Executes the main logic of the application, performing basic operations such as SET, GET, PING,
     * and INFO REPLICATION using the provided <code>GlideClusterClient</code>.
     *
     * @param client An instance of <code>GlideClusterClient</code>.
     * @throws ExecutionException if an execution error occurs during operations.
     * @throws InterruptedException if the operation is interrupted.
     */
    public static void appLogic(GlideClusterClient client)
            throws ExecutionException, InterruptedException {
        
    // Benchmark configuration - adjusted to be more efficient while still meaningful
    final int NUM_OPERATIONS = 1000; // Reduced from 10000 to run faster
    final int[] VALUE_SIZES = {100, 1000}; // Small and medium values only
    final int WARMUP_OPS = 100; // Reduced warmup
        
        // Perform initial connectivity check
        CompletableFuture<String> pingResponse = client.ping();
        log(INFO, "app", "Initial PING response: " + pingResponse.get());
        
        // Warmup
        log(INFO, "app", "Performing warmup with " + WARMUP_OPS + " operations...");
        String warmupValue = generateRandomValue(1000);
        for (int i = 0; i < WARMUP_OPS; i++) {
            String key = "warmup:key:" + i;
            client.set(key, warmupValue).get();
        }
        log(INFO, "app", "Warmup completed");
        
        // Run comprehensive benchmarks for different value sizes
        log(INFO, "app", "==== STARTING FULL BENCHMARK SUITE ====");
        log(INFO, "app", "Operations per test: " + NUM_OPERATIONS);
        
        // ==== SET BENCHMARKS ====
        log(INFO, "app", "\n==== SET BENCHMARKS ====");
        for (int valueSize : VALUE_SIZES) {
            String testValue = generateRandomValue(valueSize);
            log(INFO, "app", "Running SET benchmark with value size: " + valueSize + " bytes");
            
            Instant setStart = Instant.now();
            for (int i = 0; i < NUM_OPERATIONS; i++) {
                String key = "benchmark:set:" + valueSize + ":" + i;
                client.set(key, testValue).get();
            }
            Instant setEnd = Instant.now();
            double setDurationSeconds = Duration.between(setStart, setEnd).toMillis() / 1000.0;
            double setOpsPerSecond = NUM_OPERATIONS / setDurationSeconds;
            double throughputMbps = (NUM_OPERATIONS * valueSize) / (setDurationSeconds * 1024 * 1024); 
            
            log(INFO, "app", String.format("SET %d bytes: %.2f seconds, %.2f ops/sec, %.2f MB/sec", 
                    valueSize, setDurationSeconds, setOpsPerSecond, throughputMbps));
        }
        
        // ==== GET BENCHMARKS ====
        log(INFO, "app", "\n==== GET BENCHMARKS ====");
        for (int valueSize : VALUE_SIZES) {
            log(INFO, "app", "Running GET benchmark with value size: " + valueSize + " bytes");
            
            Instant getStart = Instant.now();
            for (int i = 0; i < NUM_OPERATIONS; i++) {
                String key = "benchmark:set:" + valueSize + ":" + i;
                client.get(key).get();
            }
            Instant getEnd = Instant.now();
            double getDurationSeconds = Duration.between(getStart, getEnd).toMillis() / 1000.0;
            double getOpsPerSecond = NUM_OPERATIONS / getDurationSeconds;
            double throughputMbps = (NUM_OPERATIONS * valueSize) / (getDurationSeconds * 1024 * 1024);
            
            log(INFO, "app", String.format("GET %d bytes: %.2f seconds, %.2f ops/sec, %.2f MB/sec", 
                    valueSize, getDurationSeconds, getOpsPerSecond, throughputMbps));
        }
        
        // ==== HASH BENCHMARKS ====
        log(INFO, "app", "\n==== HASH BENCHMARKS ====");
        int hashFieldCount = 10;
        String hashValue = generateRandomValue(1000);
        
        // HSET benchmark
        log(INFO, "app", "Running HSET benchmark with " + hashFieldCount + " fields per hash");
        Instant hsetStart = Instant.now();
        for (int i = 0; i < NUM_OPERATIONS; i++) {
            String hashKey = "benchmark:hash:" + i;
            // Create a map for the hash fields
            java.util.Map<String, String> hashMap = new java.util.HashMap<>();
            for (int f = 0; f < hashFieldCount; f++) {
                hashMap.put("field" + f, hashValue);
            }
            client.hset(hashKey, hashMap).get();
        }
        Instant hsetEnd = Instant.now();
        double hsetDurationSeconds = Duration.between(hsetStart, hsetEnd).toMillis() / 1000.0;
        double hsetOpsPerSecond = (NUM_OPERATIONS * hashFieldCount) / hsetDurationSeconds;
        
        log(INFO, "app", String.format("HSET: %.2f seconds, %.2f ops/sec", 
                hsetDurationSeconds, hsetOpsPerSecond));
        
        // HGET benchmark
        log(INFO, "app", "Running HGET benchmark");
        Instant hgetStart = Instant.now();
        for (int i = 0; i < NUM_OPERATIONS; i++) {
            String hashKey = "benchmark:hash:" + i;
            for (int f = 0; f < hashFieldCount; f++) {
                client.hget(hashKey, "field" + f).get();
                // Note: hget with individual field and key is valid, no change needed
            }
        }
        Instant hgetEnd = Instant.now();
        double hgetDurationSeconds = Duration.between(hgetStart, hgetEnd).toMillis() / 1000.0;
        double hgetOpsPerSecond = (NUM_OPERATIONS * hashFieldCount) / hgetDurationSeconds;
        
        log(INFO, "app", String.format("HGET: %.2f seconds, %.2f ops/sec", 
                hgetDurationSeconds, hgetOpsPerSecond));
                
        // ==== LIST BENCHMARKS ====
        log(INFO, "app", "\n==== LIST BENCHMARKS ====");
        int listItemCount = 100;
        String listValue = generateRandomValue(100);
        
        // LPUSH benchmark
        log(INFO, "app", "Running LPUSH benchmark with " + listItemCount + " items per list");
        Instant lpushStart = Instant.now();
        for (int i = 0; i < NUM_OPERATIONS/10; i++) {
            String listKey = "benchmark:list:" + i;
            for (int j = 0; j < listItemCount; j++) {
                client.lpush(listKey, new String[]{listValue}).get();
            }
        }
        Instant lpushEnd = Instant.now();
        double lpushDurationSeconds = Duration.between(lpushStart, lpushEnd).toMillis() / 1000.0;
        double lpushOpsPerSecond = ((NUM_OPERATIONS/10) * listItemCount) / lpushDurationSeconds;
        
        log(INFO, "app", String.format("LPUSH: %.2f seconds, %.2f ops/sec", 
                lpushDurationSeconds, lpushOpsPerSecond));
                
        // Get cluster info at the end
        try {
            ClusterValue<String> infoResponse = client.info(new Section[] {Section.CLUSTER}, ALL_NODES).get();
            log(INFO, "app", "Cluster configuration confirmed: " + infoResponse.getMultiValue().size() + " nodes total");
        } catch (Exception e) {
            log(WARN, "app", "Could not retrieve cluster info: " + e.getMessage());
        }
    }

    /**
     * Executes the application logic with exception handling.
     *
     * @throws ExecutionException if an execution error occurs during operations.
     */
    private static void execAppLogic() throws ExecutionException {

        // Use ElastiCache endpoint
        log(INFO, "app", "Connecting to ElastiCache endpoint: " + VALKEY_HOST + ":" + VALKEY_PORT + 
                  ", TLS: " + VALKEY_USE_TLS);
                  
        // Define list of nodes
        List<NodeAddress> nodeList =
                Collections.singletonList(NodeAddress.builder().host(VALKEY_HOST).port(VALKEY_PORT).build());

        while (true) {
            try (GlideClusterClient client = createClient(nodeList)) {
                log(INFO, "app", "Successfully connected to ElastiCache cluster");
                appLogic(client);
                return;
            } catch (CancellationException e) {
                log(ERROR, "glide", "Request cancelled: " + e.getMessage());
                throw e;
            } catch (InterruptedException e) {
                log(ERROR, "glide", "Client interrupted: " + e.getMessage());
                Thread.currentThread().interrupt(); // Restore interrupt status
                throw new CancellationException("Client was interrupted.");
            } catch (ExecutionException e) {
                // All Glide errors will be handled as ExecutionException
                if (e.getCause() instanceof ClosingException) {
                    // If the error message contains "NOAUTH", raise the exception
                    // because it indicates a critical authentication issue.
                    if (e.getMessage().contains("NOAUTH")) {
                        log(ERROR, "glide", "Authentication error encountered: " + e.getMessage());
                        throw e;
                    } else {
                        log(WARN, "glide", "Client has closed and needs to be re-created: " + e.getMessage());
                    }
                } else if (e.getCause() instanceof ConnectionException) {
                    // The client wasn't able to reestablish the connection within the given retries
                    log(ERROR, "glide", "Connection error encountered: " + e.getMessage());
                    throw e;
                } else if (e.getCause() instanceof TimeoutException) {
                    // A request timed out. You may choose to retry the execution based on your application's
                    // logic
                    log(ERROR, "glide", "Timeout encountered: " + e.getMessage());
                    throw e;
                } else {
                    log(ERROR, "glide", "Execution error encountered: " + e.getCause());
                    throw e;
                }
            }
        }
    }

    /**
     * The entry point of the cluster example. This method sets up the logger configuration and
     * executes the main application logic.
     *
     * @param args Command-line arguments passed to the application.
     * @throws ExecutionException if an error occurs during execution of the application logic.
     */
    public static void main(String[] args) throws ExecutionException {
        // In this example, we will utilize the client's logger for all log messages
        Logger.setLoggerConfig(INFO);
        
        log(INFO, "app", "Starting ElastiCache benchmark test");
        
        // Run the benchmark
        execAppLogic();
        
        log(INFO, "app", "ElastiCache benchmark test completed");
    }
}
