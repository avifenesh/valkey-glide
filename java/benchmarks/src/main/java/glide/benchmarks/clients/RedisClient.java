/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.benchmarks.clients;

import io.micrometer.core.instrument.MeterRegistry;

/** Common interface for Redis clients used in benchmarking */
public interface RedisClient {
    /**
     * Get the name of the client implementation
     *
     * @return client name
     */
    String getName();

    /**
     * Initialize the client with the given registry for metrics
     *
     * @param registry the metrics registry
     */
    void initialize(MeterRegistry registry);

    /**
     * Get a value for a key
     *
     * @param key the key to retrieve
     * @return the value or null if not found
     */
    String get(String key);

    /**
     * Set a value for a key
     *
     * @param key the key to set
     * @param value the value to set
     * @return true if successful
     */
    boolean set(String key, String value);

    /**
     * Set a value with expiration time in seconds
     *
     * @param key the key to set
     * @param value the value to set
     * @param expireSeconds expiration time in seconds
     * @return true if successful
     */
    boolean setWithExpiry(String key, String value, int expireSeconds);

    /**
     * Delete a key
     *
     * @param key the key to delete
     * @return true if successful
     */
    boolean delete(String key);

    /**
     * Check if a key exists
     *
     * @param key the key to check
     * @return true if exists
     */
    boolean exists(String key);

    /** Reset the client (close and re-initialize connections) */
    void reset();

    /** Close the client and release resources */
    void close();

    /**
     * Get client statistics
     *
     * @return client stats as JSON string
     */
    String getStats();
}
