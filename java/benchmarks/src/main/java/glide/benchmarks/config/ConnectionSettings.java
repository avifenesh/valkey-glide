/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.benchmarks.config;

import lombok.Builder;
import lombok.Data;

/** Enhanced connection settings for Redis/Valkey clients */
@Data
@Builder
public class ConnectionSettings {
    // Basic connection settings
    private String host;
    private int port;
    private boolean useSsl;
    private boolean clusterMode;

    // Timeout settings
    private int timeout; // in ms
    private int connectTimeout; // in ms
    private int idleConnectTimeout; // in ms
    private int retryInterval; // in ms
    private int numRetries;

    // Connection pool settings
    private int masterConnectionsMinIdleSize;
    private int masterConnectionsPoolSize;
    private int slaveConnectionsMinIdleSize;
    private int slaveConnectionsPoolSize;

    // Cache TTL settings
    private int localCacheTtlSeconds;
    private int remoteDefaultCacheTtlHours;
    private int negativeRemoteCacheTtlHours;
    private int cdsExceptionCacheTtlMinutes;

    // Construct endpoint URL for Redis/Valkey connections
    public String getEndpointUrl() {
        String protocol = useSsl ? "rediss" : "redis";
        return protocol + "://" + host + ":" + port;
    }

    /**
     * Create default connection settings based on standard parameters
     *
     * @return default connection settings instance
     */
    public static ConnectionSettings createDefault() {
        return ConnectionSettings.builder()
                .host("localhost")
                .port(6379)
                .useSsl(false)
                .clusterMode(false)
                .timeout(1000)
                .connectTimeout(10000)
                .idleConnectTimeout(1000)
                .masterConnectionsMinIdleSize(10)
                .masterConnectionsPoolSize(24)
                .slaveConnectionsMinIdleSize(10)
                .slaveConnectionsPoolSize(24)
                .numRetries(3)
                .retryInterval(1000)
                .localCacheTtlSeconds(30)
                .remoteDefaultCacheTtlHours(12)
                .negativeRemoteCacheTtlHours(1)
                .cdsExceptionCacheTtlMinutes(15)
                .build();
    }
}
