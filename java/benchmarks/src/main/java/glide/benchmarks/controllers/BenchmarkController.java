/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.benchmarks.controllers;

import glide.benchmarks.clients.ValkeyClient;
import glide.benchmarks.utils.TextGenerator;
import io.micrometer.core.annotation.Timed;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Controller for Valkey client benchmark operations */
@RestController
@RequestMapping("/api/v1")
@Slf4j
public class BenchmarkController {

    @Autowired
    @Qualifier("glideClient")
    private ValkeyClient glideClient;

    @Autowired
    @Qualifier("redissonClient")
    private ValkeyClient redissonClient;

    /**
     * Get value for a key using GLIDE client
     *
     * @param key the key to retrieve
     * @return response with value
     */
    @GetMapping("/glide/key/{key}")
    @Timed(
            value = "endpoint.glide.get",
            percentiles = {0.5, 0.9, 0.95, 0.99})
    public ResponseEntity<Map<String, Object>> getGlideValue(@PathVariable String key) {
        Map<String, Object> response = new HashMap<>();
        try {
            String value = glideClient.get(key);
            response.put("key", key);
            response.put("value", value);
            response.put("found", value != null);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            // Minimal logging for performance
            response.put("error", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Get value for a key using Redisson client
     *
     * @param key the key to retrieve
     * @return response with value
     */
    @GetMapping("/redisson/key/{key}")
    @Timed(
            value = "endpoint.redisson.get",
            percentiles = {0.5, 0.9, 0.95, 0.99})
    public ResponseEntity<Map<String, Object>> getRedissonValue(@PathVariable String key) {
        Map<String, Object> response = new HashMap<>();
        try {
            String value = redissonClient.get(key);
            response.put("key", key);
            response.put("value", value);
            response.put("found", value != null);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            // Minimal logging for performance
            response.put("error", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Get value for a non-existing key and set with GLIDE client
     *
     * @param key the key to get and set
     * @return response with result
     */
    @GetMapping("/glide/key/missing/{key}")
    @Timed(
            value = "endpoint.glide.get.missing",
            percentiles = {0.5, 0.9, 0.95, 0.99})
    public ResponseEntity<Map<String, Object>> getGlideMissingAndSet(@PathVariable String key) {
        Map<String, Object> response = new HashMap<>();
        try {
            String value = glideClient.get(key);
            boolean keyExists = value != null;
            response.put("key", key);
            response.put("found", keyExists);

            // If key doesn't exist, set it with random value
            if (!keyExists) {
                String newValue = TextGenerator.generateLoremIpsum(20);
                boolean setResult = glideClient.set(key, newValue);
                response.put("valueSet", setResult);
                response.put("newValue", newValue);
            } else {
                response.put("value", value);
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            // Minimal logging for performance
            response.put("error", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Get value for a non-existing key and set with Redisson client
     *
     * @param key the key to get and set
     * @return response with result
     */
    @GetMapping("/redisson/key/missing/{key}")
    @Timed(
            value = "endpoint.redisson.get.missing",
            percentiles = {0.5, 0.9, 0.95, 0.99})
    public ResponseEntity<Map<String, Object>> getRedissonMissingAndSet(@PathVariable String key) {
        Map<String, Object> response = new HashMap<>();
        try {
            String value = redissonClient.get(key);
            boolean keyExists = value != null;
            response.put("key", key);
            response.put("found", keyExists);

            // If key doesn't exist, set it with random value
            if (!keyExists) {
                String newValue = TextGenerator.generateLoremIpsum(20);
                boolean setResult = redissonClient.set(key, newValue);
                response.put("valueSet", setResult);
                response.put("newValue", newValue);
            } else {
                response.put("value", value);
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            // Minimal logging for performance
            response.put("error", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Set a value with GLIDE client
     *
     * @param key the key to set
     * @param requestBody body containing value to set
     * @return response with result
     */
    @PostMapping("/glide/key/{key}")
    @Timed(
            value = "endpoint.glide.set",
            percentiles = {0.5, 0.9, 0.95, 0.99})
    public ResponseEntity<Map<String, Object>> setGlideValue(
            @PathVariable String key, @RequestBody(required = false) Map<String, String> requestBody) {

        Map<String, Object> response = new HashMap<>();
        try {
            String value =
                    (requestBody != null && requestBody.containsKey("value"))
                            ? requestBody.get("value")
                            : TextGenerator.generateLoremIpsum(20);

            boolean result = glideClient.set(key, value);
            response.put("key", key);
            response.put("success", result);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            // Minimal logging for performance
            response.put("error", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Set a value with Redisson client
     *
     * @param key the key to set
     * @param requestBody body containing value to set
     * @return response with result
     */
    @PostMapping("/redisson/key/{key}")
    @Timed(
            value = "endpoint.redisson.set",
            percentiles = {0.5, 0.9, 0.95, 0.99})
    public ResponseEntity<Map<String, Object>> setRedissonValue(
            @PathVariable String key, @RequestBody(required = false) Map<String, String> requestBody) {

        Map<String, Object> response = new HashMap<>();
        try {
            String value =
                    (requestBody != null && requestBody.containsKey("value"))
                            ? requestBody.get("value")
                            : TextGenerator.generateLoremIpsum(20);

            boolean result = redissonClient.set(key, value);
            response.put("key", key);
            response.put("success", result);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            // Minimal logging for performance
            response.put("error", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Get client status for GLIDE
     *
     * @return status response
     */
    @GetMapping("/glide/status")
    public ResponseEntity<Map<String, Object>> getGlideStatus() {
        Map<String, Object> response = new HashMap<>();
        response.put("client", "Valkey GLIDE");
        response.put("status", "active");
        response.put("stats", glideClient.getStats());
        return ResponseEntity.ok(response);
    }

    /**
     * Get client status for Redisson
     *
     * @return status response
     */
    @GetMapping("/redisson/status")
    public ResponseEntity<Map<String, Object>> getRedissonStatus() {
        Map<String, Object> response = new HashMap<>();
        response.put("client", "Redisson");
        response.put("status", "active");
        response.put("stats", redissonClient.getStats());
        return ResponseEntity.ok(response);
    }

    /**
     * Reset both client connections
     *
     * @return reset result
     */
    @PostMapping("/reset")
    public ResponseEntity<Map<String, Object>> resetClients() {
        Map<String, Object> response = new HashMap<>();
        try {
            glideClient.reset();
            redissonClient.reset();
            response.put("glide", "reset");
            response.put("redisson", "reset");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            // Minimal logging for performance
            response.put("error", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
}
