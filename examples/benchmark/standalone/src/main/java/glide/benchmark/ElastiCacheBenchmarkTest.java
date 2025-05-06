package glide.benchmark;

import static glide.api.logging.Logger.Level.ERROR;
import static glide.api.logging.Logger.Level.INFO;
import static glide.api.logging.Logger.Level.WARN;
import static glide.api.logging.Logger.log;
import static glide.api.models.GlideString.gs;
import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleMultiNodeRoute.ALL_NODES;

import glide.api.GlideClusterClient;
import glide.api.logging.Logger;
import glide.api.models.ClusterValue;
import glide.api.models.GlideString;
import glide.api.models.commands.InfoOptions.Section;
import glide.api.models.configuration.GlideClusterClientConfiguration;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.ReadFrom;
import glide.api.models.exceptions.ClosingException;
import glide.api.models.exceptions.ConnectionException;
import glide.api.models.exceptions.TimeoutException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Benchmark test for Valkey Glide against ElastiCache.
 * Implements the 80/20 GET/SET workload pattern with title metadata key patterns.
 */
public class ElastiCacheBenchmarkTest {

    // Server endpoints
    private static final String VALKEY_HOST = "clustercfg.testingprimevalkey.ey5v7d.use2.cache.amazonaws.com";
    private static final String REDIS_HOST = "clustercfg.primetestinredis.ey5v7d.use2.cache.amazonaws.com";
    private static final int PORT = 6379;
    private static final boolean USE_TLS = true;
    private static final String CLIENT_AZ = "us-east-2b"; // Client availability zone for AZ affinity
    
    // Benchmark configuration
    private static final int NUM_OPERATIONS = 10000; // Number of operations to perform
    private static final int KEY_SPACE_SIZE = 5000; // Using smaller subset for this test
    private static final int PARAGRAPH_SIZE_MIN = 200;
    private static final int PARAGRAPH_SIZE_MAX = 800;
    private static final double GET_EXISTING_RATIO = 0.8; // 80% GET existing keys
    private static final double GET_NONEXISTING_SET_RATIO = 0.2; // 20% GET non-existing + SET
    
    // Key pattern configuration
    private static final String[] LOCALES = {"en_US", "es_ES", "fr_FR", "de_DE", "ja_JP"};
    private static final String[] ATTRIBUTES = {
        "document",          // paragraph-sized value
        "is_kids_targeted",  // single-word value
        "initial_xray_data", // paragraph-sized value
        "xray_metadata_payload" // paragraph-sized value
    };
    
    private static final Random random = new Random();
    
    /**
     * Creates and returns a configured Valkey Glide client for the given server type.
     * 
     * @param serverType "valkey" for Valkey server or "redis" for Redis server
     * @return Configured GlideClusterClient
     */
    public static GlideClusterClient createClient(String serverType)
            throws CancellationException, ExecutionException, InterruptedException {
        
        String host = serverType.equals("valkey") ? VALKEY_HOST : REDIS_HOST;
        
        // Configure for ElastiCache cluster
        NodeAddress nodeAddress = NodeAddress.builder()
            .host(host)
            .port(PORT)
            .build();

        // Create base configuration
        GlideClusterClientConfiguration config;
        
        // Add AZ affinity if using Valkey
        if (serverType.equals("valkey")) {
            config = GlideClusterClientConfiguration.builder()
                    .address(nodeAddress)
                    .useTLS(USE_TLS)
                    .readFrom(ReadFrom.AZ_AFFINITY)
                    .clientAZ(CLIENT_AZ)
                    .build();
        } else {
            config = GlideClusterClientConfiguration.builder()
                    .address(nodeAddress)
                    .useTLS(USE_TLS)
                    .build();
        }

        log(INFO, "benchmark", "Connecting to " + host + ":" + PORT + " with TLS: " + USE_TLS);
        GlideClusterClient client = GlideClusterClient.createClient(config).get();
        
        return client;
    }

    /**
     * Generates a random string of the specified size.
     */
    private static String generateRandomValue(int size) {
        StringBuilder sb = new StringBuilder(size);
        for (int i = 0; i < size; i++) {
            sb.append((char) ('a' + (random.nextInt(26))));
        }
        return sb.toString();
    }
    
    /**
     * Generates a random paragraph-sized value.
     */
    private static String generateParagraphValue() {
        int size = PARAGRAPH_SIZE_MIN + random.nextInt(PARAGRAPH_SIZE_MAX - PARAGRAPH_SIZE_MIN);
        return generateRandomValue(size);
    }
    
    /**
     * Generates a key using the TITLE{id}:{locale}:{attribute} pattern.
     */
    private static String generateKey(int titleId, String locale, String attribute) {
        return String.format("TITLE%d:%s:%s", titleId, locale, attribute);
    }
    
    /**
     * Generates the appropriate value based on attribute type.
     */
    private static String generateValueForAttribute(String attribute) {
        if (attribute.equals("is_kids_targeted")) {
            return random.nextBoolean() ? "true" : "false";
        } else {
            return generateParagraphValue();
        }
    }

    /**
     * Populates the test data set with the specified key pattern and value distribution.
     */
    public static void populateDataSet(GlideClusterClient client, int keySpaceSize) 
            throws ExecutionException, InterruptedException {
        
        log(INFO, "benchmark", "Populating data set with " + keySpaceSize + " keys...");
        
        int titleCount = keySpaceSize / (LOCALES.length * ATTRIBUTES.length);
        if (titleCount < 1) titleCount = 1;
        
        int totalKeys = 0;
        Instant startTime = Instant.now();
        
        for (int titleId = 1; titleId <= titleCount; titleId++) {
            for (String locale : LOCALES) {
                for (String attribute : ATTRIBUTES) {
                    String key = generateKey(titleId, locale, attribute);
                    String value = generateValueForAttribute(attribute);
                    
                    client.set(gs(key), gs(value)).get();
                    totalKeys++;
                }
            }
            
            if (titleId % 100 == 0 || titleId == titleCount) {
                log(INFO, "benchmark", String.format("Populated %d/%d titles (%d keys)", 
                        titleId, titleCount, totalKeys));
            }
        }
        
        Instant endTime = Instant.now();
        double durationSeconds = Duration.between(startTime, endTime).toMillis() / 1000.0;
        log(INFO, "benchmark", String.format("Data population completed in %.2f seconds (%d keys)", 
                durationSeconds, totalKeys));
    }
    
    /**
     * Generates a list of keys for benchmark operations.
     */
    private static List<String> generateKeyList(int size) {
        List<String> keys = new ArrayList<>(size);
        int titleCount = KEY_SPACE_SIZE / (LOCALES.length * ATTRIBUTES.length);
        if (titleCount < 1) titleCount = 1;
        
        for (int i = 0; i < size; i++) {
            int titleId = random.nextInt(titleCount) + 1;
            String locale = LOCALES[random.nextInt(LOCALES.length)];
            String attribute = ATTRIBUTES[random.nextInt(ATTRIBUTES.length)];
            keys.add(generateKey(titleId, locale, attribute));
        }
        
        return keys;
    }
    
    /**
     * Generates non-existing keys by using titleId outside the populated range.
     */
    private static List<String> generateNonExistingKeyList(int size) {
        List<String> keys = new ArrayList<>(size);
        int titleCount = KEY_SPACE_SIZE / (LOCALES.length * ATTRIBUTES.length);
        if (titleCount < 1) titleCount = 1;
        
        for (int i = 0; i < size; i++) {
            int titleId = titleCount + random.nextInt(10000) + 1; // Outside populated range
            String locale = LOCALES[random.nextInt(LOCALES.length)];
            String attribute = ATTRIBUTES[random.nextInt(ATTRIBUTES.length)];
            keys.add(generateKey(titleId, locale, attribute));
        }
        
        return keys;
    }

    /**
     * Runs the 80/20 GET/SET workload pattern benchmark.
     */
    public static void runWorkloadBenchmark(GlideClusterClient client) 
            throws ExecutionException, InterruptedException {
        
        // Generate key lists for benchmark
        int existingKeysCount = (int) (NUM_OPERATIONS * GET_EXISTING_RATIO);
        int nonExistingKeysCount = (int) (NUM_OPERATIONS * GET_NONEXISTING_SET_RATIO);
        
        List<String> existingKeys = generateKeyList(existingKeysCount);
        List<String> nonExistingKeys = generateNonExistingKeyList(nonExistingKeysCount);
        
        log(INFO, "benchmark", String.format("Starting 80/20 workload benchmark with %d operations (%d existing GET, %d non-existing GET+SET)",
                NUM_OPERATIONS, existingKeysCount, nonExistingKeysCount));
        
        // Execute workload benchmark
        Instant startTime = Instant.now();
        int hits = 0;
        int misses = 0;
        
        // First perform GETs on existing keys (80%)
        for (String key : existingKeys) {
            GlideString result = client.get(gs(key)).get();
            if (result != null) {
                hits++;
            } else {
                misses++;
            }
        }
        
        // Then perform GET+SET on non-existing keys (20%)
        for (String key : nonExistingKeys) {
            GlideString result = client.get(gs(key)).get();
            
            if (result != null) {
                hits++;
            } else {
                misses++;
                // Key doesn't exist, so set it
                String attribute = key.substring(key.lastIndexOf(":") + 1);
                String value = generateValueForAttribute(attribute);
                client.set(gs(key), gs(value)).get();
            }
        }
        
        Instant endTime = Instant.now();
        double durationSeconds = Duration.between(startTime, endTime).toMillis() / 1000.0;
        double opsPerSecond = NUM_OPERATIONS / durationSeconds;
        
        log(INFO, "benchmark", String.format("80/20 workload completed in %.2f seconds, %.2f ops/second", 
                durationSeconds, opsPerSecond));
        log(INFO, "benchmark", String.format("Cache hits: %d, misses: %d, hit rate: %.2f%%", 
                hits, misses, (double)hits/NUM_OPERATIONS*100));
    }
    
    /**
     * Runs a separate test to measure P99 latency under increasing load.
     */
    public static void runLatencyTest(GlideClusterClient client) 
            throws ExecutionException, InterruptedException {
        
        log(INFO, "benchmark", "Starting latency test...");
        
        int[] batchSizes = {10, 100, 1000};
        List<String> keys = generateKeyList(1000); // Reuse a set of keys
        
        for (int batchSize : batchSizes) {
            List<Long> latencies = new ArrayList<>();
            
            for (int i = 0; i < 100; i++) {
                String key = keys.get(random.nextInt(keys.size()));
                
                Instant start = Instant.now();
                
                // Execute batch of operations
                for (int j = 0; j < batchSize; j++) {
                    client.get(gs(key)).get();
                }
                
                Instant end = Instant.now();
                long latencyMs = Duration.between(start, end).toMillis();
                latencies.add(latencyMs);
            }
            
            // Sort latencies to calculate percentiles
            Collections.sort(latencies);
            
            double p50 = latencies.get(latencies.size() / 2);
            double p90 = latencies.get((int)(latencies.size() * 0.9));
            double p99 = latencies.get((int)(latencies.size() * 0.99));
            
            log(INFO, "benchmark", String.format("Batch size %d - P50: %.1f ms, P90: %.1f ms, P99: %.1f ms", 
                    batchSize, p50, p90, p99));
        }
    }

    /**
     * Display cluster information.
     */
    public static void displayClusterInfo(GlideClusterClient client) {
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
        
        String serverType = "valkey"; // Default to valkey
        if (args.length > 0 && args[0].equalsIgnoreCase("redis")) {
            serverType = "redis";
        }
        
        log(INFO, "benchmark", "Starting benchmark against " + serverType.toUpperCase() + " server");
        
        try (GlideClusterClient client = createClient(serverType)) {
            log(INFO, "benchmark", "Connected to cluster successfully");
            
            // Display cluster info
            displayClusterInfo(client);
            
            // Populate test data
            populateDataSet(client, KEY_SPACE_SIZE);
            
            // Run the workload benchmark
            runWorkloadBenchmark(client);
            
            // Run latency test
            runLatencyTest(client);
            
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
