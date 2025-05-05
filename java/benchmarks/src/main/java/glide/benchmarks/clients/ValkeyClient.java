/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.benchmarks.clients;

import io.micrometer.core.instrument.MeterRegistry;

/** Common interface for Valkey client implementations */
public interface ValkeyClient {

    /**
     * Get the name of the client implementation
     *
     * @return client name
     */
    String getName();

    /**
     * Initialize the client with metrics registry
     *
     * @param registry metrics registry for collecting metrics
     */
    void initialize(MeterRegistry registry);

    /**
     * Get a value for a key
     *
     * @param key key to get value for
     * @return value or null if not found
     */
    String get(String key);

    /**
     * Set a value for a key
     *
     * @param key key to set
     * @param value value to set
     * @return true if successful
     */
    boolean set(String key, String value);

    /**
     * Set a value with expiry time
     *
     * @param key key to set
     * @param value value to set
     * @param expireSeconds expiry time in seconds
     * @return true if successful
     */
    boolean setWithExpiry(String key, String value, int expireSeconds);

    /**
     * Check if a key exists
     *
     * @param key key to check
     * @return true if key exists
     */
    boolean exists(String key);

    /**
     * Delete a key
     *
     * @param key key to delete
     * @return true if key was deleted
     */
    boolean delete(String key);

    /** Reset the client connection */
    void reset();

    /** Close the client connection */
    void close();

    /**
     * Get client statistics as JSON string
     *
     * @return JSON string with statistics
     */
    String getStats();
}
