package glide.benchmark.controller;

import glide.api.GlideClusterClient;
import glide.api.models.GlideString;
import glide.benchmark.service.DataPopulationService;
import glide.benchmark.util.DataGenerationUtil;

import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutionException;

/**
 * Controller exposing HTTP endpoints for benchmarking Valkey Glide and Redisson clients.
 * Implements the 80/20 GET/SET workload pattern with title metadata key patterns.
 */
@RestController
@RequestMapping("/benchmark")
public class BenchmarkController {

    private static final Logger logger = LoggerFactory.getLogger(BenchmarkController.class);
    private static final Random random = new Random();
    
    @Value("${benchmark.key-space-size}")
    private int keySpaceSize;
    
    @Value("${benchmark.get-existing-ratio}")
    private double getExistingRatio;
    
    @Value("${benchmark.get-nonexisting-set-ratio}")
    private double getNonExistingSetRatio;
    
    private final MeterRegistry meterRegistry;
    private final DataGenerationUtil dataGenerationUtil;
    private final DataPopulationService dataPopulationService;
    
    @Autowired
    @Qualifier("valkeyGlideClient")
    private GlideClusterClient valkeyGlideClient;
    
    @Autowired
    @Qualifier("redisGlideClient")
    private GlideClusterClient redisGlideClient;
    
    @Autowired
    @Qualifier("valkeyRedissonClient")
    private RedissonClient valkeyRedissonClient;
    
    @Autowired
    @Qualifier("redisRedissonClient")
    private RedissonClient redisRedissonClient;
    
    public BenchmarkController(MeterRegistry meterRegistry, 
                             DataGenerationUtil dataGenerationUtil,
                             DataPopulationService dataPopulationService) {
        this.meterRegistry = meterRegistry;
        this.dataGenerationUtil = dataGenerationUtil;
        this.dataPopulationService = dataPopulationService;
    }
    
    /**
     * Health check endpoint.
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Benchmark service is healthy");
    }
    
    /**
     * Endpoint to populate data in Valkey/Redis databases.
     */
    @PostMapping("/populate")
    public ResponseEntity<Map<String, Object>> populateData(
            @RequestParam(defaultValue = "valkey") String serverType,
            @RequestParam(defaultValue = "glide") String clientType,
            @RequestParam(defaultValue = "100") int batchSize,
            @RequestParam(defaultValue = "4") int threads) {
        
        Map<String, Object> result = new HashMap<>();
        result.put("serverType", serverType);
        result.put("clientType", clientType);
        result.put("keySpaceSize", keySpaceSize);
        
        try {
            int keysPopulated;
            
            if ("glide".equalsIgnoreCase(clientType)) {
                if ("valkey".equalsIgnoreCase(serverType)) {
                    keysPopulated = dataPopulationService.populateValkeyWithGlide(batchSize, threads);
                } else {
                    keysPopulated = dataPopulationService.populateRedisWithGlide(batchSize, threads);
                }
            } else {
                if ("valkey".equalsIgnoreCase(serverType)) {
                    keysPopulated = dataPopulationService.populateValkeyWithRedisson(batchSize, threads);
                } else {
                    keysPopulated = dataPopulationService.populateRedisWithRedisson(batchSize, threads);
                }
            }
            
            result.put("status", "success");
            result.put("keysPopulated", keysPopulated);
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("Error populating data: {}", e.getMessage(), e);
            result.put("status", "error");
            result.put("message", e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }
    
    /**
     * Verify data population by checking a sample of keys.
     */
    @GetMapping("/verify")
    public ResponseEntity<Map<String, Object>> verifyData(
            @RequestParam(defaultValue = "valkey") String serverType,
            @RequestParam(defaultValue = "1000") int sampleSize) {
        
        Map<String, Object> result = new HashMap<>();
        result.put("serverType", serverType);
        result.put("sampleSize", sampleSize);
        
        try {
            GlideClusterClient client = "valkey".equalsIgnoreCase(serverType) ? 
                    valkeyGlideClient : redisGlideClient;
            
            double hitRate = dataPopulationService.verifyDataWithGlide(client, sampleSize);
            
            result.put("status", "success");
            result.put("hitRate", hitRate);
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("Error verifying data: {}", e.getMessage(), e);
            result.put("status", "error");
            result.put("message", e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }
    
    /**
     * Main endpoint for Glide client benchmarking with 80/20 GET/SET workload pattern.
     * This endpoint is called by the oha tool.
     */
    @GetMapping("/glide/{serverType}/{key}")
    public ResponseEntity<String> benchmarkGlide(
            @PathVariable String serverType,
            @PathVariable String key) {
        
        // Select the appropriate Glide client based on serverType
        GlideClusterClient client = "valkey".equalsIgnoreCase(serverType) ? 
                valkeyGlideClient : redisGlideClient;
        
        String clientName = "glide_" + serverType.toLowerCase();
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            // Introduce the 80/20 GET/SET pattern based on random sampling
            double rand = random.nextDouble();
            boolean isExistingKey = rand < getExistingRatio;
            
            // For existing keys (80% case), use the provided key
            // For non-existing keys (20% case), modify the key to ensure it doesn't exist
            String actualKey = isExistingKey ? key : "nonexisting_" + key;
            
            // Execute GET operation
            String result = client.get(GlideString.gs(actualKey)).get().toString();
            sample.stop(meterRegistry.timer("benchmark.get", "client", clientName, "exists", String.valueOf(result != null)));
            
            // For non-existing keys (20% case), perform SET operation
            if (result == null && !isExistingKey) {
                Timer.Sample setSample = Timer.start(meterRegistry);
                String attribute = actualKey.contains(":") ? 
                        dataGenerationUtil.extractAttribute(actualKey) : "document";
                String value = dataGenerationUtil.generateValueForAttribute(attribute);
                
                client.set(GlideString.gs(actualKey), GlideString.gs(value)).get();
                setSample.stop(meterRegistry.timer("benchmark.set", "client", clientName));
                
                return ResponseEntity.ok("Key not found, value set: " + value.substring(0, Math.min(20, value.length())) + "...");
            }
            
            // Return the result
            return ResponseEntity.ok(result == null ? 
                    "Key not found" : 
                    result.substring(0, Math.min(20, result.length())) + "...");
            
        } catch (ExecutionException | InterruptedException e) {
            logger.error("Error in Glide benchmark: {}", e.getMessage(), e);
            sample.stop(meterRegistry.timer("benchmark.error", "client", clientName));
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }
    
    /**
     * Main endpoint for Redisson client benchmarking with 80/20 GET/SET workload pattern.
     * This endpoint is called by the oha tool.
     */
    @GetMapping("/redisson/{serverType}/{key}")
    public ResponseEntity<String> benchmarkRedisson(
            @PathVariable String serverType,
            @PathVariable String key) {
        
        // Select the appropriate Redisson client based on serverType
        RedissonClient client = "valkey".equalsIgnoreCase(serverType) ? 
                valkeyRedissonClient : redisRedissonClient;
        
        String clientName = "redisson_" + serverType.toLowerCase();
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            // Introduce the 80/20 GET/SET pattern based on random sampling
            double rand = random.nextDouble();
            boolean isExistingKey = rand < getExistingRatio;
            
            // For existing keys (80% case), use the provided key
            // For non-existing keys (20% case), modify the key to ensure it doesn't exist
            String actualKey = isExistingKey ? key : "nonexisting_" + key;
            
            // Execute GET operation
            RBucket<String> bucket = client.getBucket(actualKey);
            String result = bucket.get();
            sample.stop(meterRegistry.timer("benchmark.get", "client", clientName, "exists", String.valueOf(result != null)));
            
            // For non-existing keys (20% case), perform SET operation
            if (result == null && !isExistingKey) {
                Timer.Sample setSample = Timer.start(meterRegistry);
                String attribute = actualKey.contains(":") ? 
                        dataGenerationUtil.extractAttribute(actualKey) : "document";
                String value = dataGenerationUtil.generateValueForAttribute(attribute);
                
                bucket.set(value);
                setSample.stop(meterRegistry.timer("benchmark.set", "client", clientName));
                
                return ResponseEntity.ok("Key not found, value set: " + value.substring(0, Math.min(20, value.length())) + "...");
            }
            
            // Return the result
            return ResponseEntity.ok(result == null ? 
                    "Key not found" : 
                    result.substring(0, Math.min(20, result.length())) + "...");
            
        } catch (Exception e) {
            logger.error("Error in Redisson benchmark: {}", e.getMessage(), e);
            sample.stop(meterRegistry.timer("benchmark.error", "client", clientName));
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }
    
    /**
     * Get status of the benchmark service, including configuration.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        
        // Server endpoints
        Map<String, Object> endpoints = new HashMap<>();
        endpoints.put("valkey", valkeyGlideClient.getAddresses().toString());
        endpoints.put("redis", redisGlideClient.getAddresses().toString());
        
        // Workload configuration
        Map<String, Object> workloadConfig = new HashMap<>();
        workloadConfig.put("keySpaceSize", keySpaceSize);
        workloadConfig.put("getExistingRatio", getExistingRatio);
        workloadConfig.put("getNonExistingSetRatio", getNonExistingSetRatio);
        
        // oha commands for reference
        Map<String, String> ohaCommands = new HashMap<>();
        ohaCommands.put("glide_valkey", "oha -n 10000 -c 50 http://localhost:8080/benchmark/glide/valkey/TITLE{1..1000}:en_US:document");
        ohaCommands.put("glide_redis", "oha -n 10000 -c 50 http://localhost:8080/benchmark/glide/redis/TITLE{1..1000}:en_US:document");
        ohaCommands.put("redisson_valkey", "oha -n 10000 -c 50 http://localhost:8080/benchmark/redisson/valkey/TITLE{1..1000}:en_US:document");
        ohaCommands.put("redisson_redis", "oha -n 10000 -c 50 http://localhost:8080/benchmark/redisson/redis/TITLE{1..1000}:en_US:document");
        
        status.put("endpoints", endpoints);
        status.put("workloadConfiguration", workloadConfig);
        status.put("ohaCommands", ohaCommands);
        
        return ResponseEntity.ok(status);
    }
}
