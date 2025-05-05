/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.benchmarks.utils;

import glide.benchmarks.config.ConnectionSettings;
import glide.benchmarks.clients.ValkeyClient;
import glide.benchmarks.clients.glide.GlideClientImpl;
import glide.benchmarks.clients.redisson.RedissonClientImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Connectivity test utility for Valkey and Redis endpoints
 */
@Component
@Profile("connectivity-test")
public class ConnectivityTest {
    private static final Logger log = LoggerFactory.getLogger(ConnectivityTest.class);
    
    @Autowired
    private ConnectionSettings valkeySettings;

    @Autowired
    private ConnectionSettings redisSettings;

    /**
     * Command line runner bean to execute the connectivity test
     * @return CommandLineRunner instance
     */
    @Bean
    public CommandLineRunner testConnectivity() {
        return args -> {
            log.info("Starting connectivity tests...");
            
            // Test Glide connection to Valkey
            log.info("Testing Glide connection to Valkey endpoint: {}", valkeySettings.getHost());
            ValkeyClient glideClient = new GlideClientImpl(valkeySettings);
            try {
                glideClient.initialize(null);  // No metrics needed for connectivity test
                String testKey = "connectivity:test:key";
                String testValue = "connectivity_test_" + System.currentTimeMillis();
                
                boolean setResult = glideClient.set(testKey, testValue);
                log.info("Glide SET operation result: {}", setResult);
                
                String getValue = glideClient.get(testKey);
                log.info("Glide GET operation result - match: {}", testValue.equals(getValue));
                
                boolean deleteResult = glideClient.delete(testKey);
                log.info("Glide DELETE operation result: {}", deleteResult);
                
                log.info("Valkey connectivity test with Glide: SUCCESS");
            } catch (Exception e) {
                log.error("Valkey connectivity test with Glide failed", e);
            } finally {
                glideClient.close();
            }
            
            // Test Redisson connection to Redis
            log.info("Testing Redisson connection to Redis endpoint: {}", redisSettings.getHost());
            ValkeyClient redissonClient = new RedissonClientImpl(redisSettings);
            try {
                redissonClient.initialize(null);  // No metrics needed for connectivity test
                String testKey = "connectivity:test:key";
                String testValue = "connectivity_test_" + System.currentTimeMillis();
                
                boolean setResult = redissonClient.set(testKey, testValue);
                log.info("Redisson SET operation result: {}", setResult);
                
                String getValue = redissonClient.get(testKey);
                log.info("Redisson GET operation result - match: {}", testValue.equals(getValue));
                
                boolean deleteResult = redissonClient.delete(testKey);
                log.info("Redisson DELETE operation result: {}", deleteResult);
                
                log.info("Redis connectivity test with Redisson: SUCCESS");
            } catch (Exception e) {
                log.error("Redis connectivity test with Redisson failed", e);
            } finally {
                redissonClient.close();
            }
            
            log.info("Connectivity tests completed");
        };
    }
}
