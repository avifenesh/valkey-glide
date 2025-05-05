/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.benchmarks.config;

import glide.benchmarks.clients.ValkeyClient;
import glide.benchmarks.clients.glide.GlideClientImpl;
import glide.benchmarks.clients.redisson.RedissonClientImpl;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/** Configuration for Valkey client benchmarking */
@Configuration
public class ValkeyClientConfiguration {

    // Valkey cluster endpoint for the Valkey GLIDE client
    private static final String VALKEY_CLUSTER_ENDPOINT =
            "clustercfg.testingprimevalkey.ey5v7d.use2.cache.amazonaws.com";

    // Redis cluster endpoint for the Redisson client
    private static final String REDIS_CLUSTER_ENDPOINT =
            "clustercfg.primetestinredis.ey5v7d.use2.cache.amazonaws.com";

    private static final int CLUSTER_PORT = 6379;
    private static final boolean USE_SSL = true;

    @Value("${valkey.timeout:1000}")
    private int timeout;

    @Value("${valkey.connect-timeout:10000}")
    private int connectTimeout;

    @Value("${valkey.idle-connect-timeout:1000}")
    private int idleConnectTimeout;

    @Value("${valkey.master-connections-min-idle:10}")
    private int masterConnectionsMinIdle;

    @Value("${valkey.master-connections-pool-size:24}")
    private int masterConnectionsPoolSize;

    @Value("${valkey.slave-connections-min-idle:10}")
    private int slaveConnectionsMinIdle;

    @Value("${valkey.slave-connections-pool-size:24}")
    private int slaveConnectionsPoolSize;

    @Value("${valkey.retry-attempts:3}")
    private int numRetries;

    @Value("${valkey.retry-interval:1000}")
    private int retryInterval;

    /**
     * Create connection settings for Valkey cluster
     *
     * @return connection settings
     */
    @Bean(name = "valkeyConnectionSettings")
    public ConnectionSettings valkeyConnectionSettings() {
        return ConnectionSettings.builder()
                .host(VALKEY_CLUSTER_ENDPOINT)
                .port(CLUSTER_PORT)
                .useSsl(USE_SSL)
                .clusterMode(true)
                .timeout(timeout)
                .connectTimeout(connectTimeout)
                .idleConnectTimeout(idleConnectTimeout)
                .masterConnectionsMinIdleSize(masterConnectionsMinIdle)
                .masterConnectionsPoolSize(masterConnectionsPoolSize)
                .slaveConnectionsMinIdleSize(slaveConnectionsMinIdle)
                .slaveConnectionsPoolSize(slaveConnectionsPoolSize)
                .numRetries(numRetries)
                .retryInterval(retryInterval)
                .localCacheTtlSeconds(30)
                .remoteDefaultCacheTtlHours(12)
                .negativeRemoteCacheTtlHours(1)
                .cdsExceptionCacheTtlMinutes(15)
                .build();
    }

    /**
     * Create connection settings for Redis cluster
     *
     * @return connection settings
     */
    @Bean(name = "redisConnectionSettings")
    public ConnectionSettings redisConnectionSettings() {
        return ConnectionSettings.builder()
                .host(REDIS_CLUSTER_ENDPOINT)
                .port(CLUSTER_PORT)
                .useSsl(USE_SSL)
                .clusterMode(true)
                .timeout(timeout)
                .connectTimeout(connectTimeout)
                .idleConnectTimeout(idleConnectTimeout)
                .masterConnectionsMinIdleSize(masterConnectionsMinIdle)
                .masterConnectionsPoolSize(masterConnectionsPoolSize)
                .slaveConnectionsMinIdleSize(slaveConnectionsMinIdle)
                .slaveConnectionsPoolSize(slaveConnectionsPoolSize)
                .numRetries(numRetries)
                .retryInterval(retryInterval)
                .localCacheTtlSeconds(30)
                .remoteDefaultCacheTtlHours(12)
                .negativeRemoteCacheTtlHours(1)
                .cdsExceptionCacheTtlMinutes(15)
                .build();
    }

    /**
     * Create Valkey GLIDE client
     *
     * @param meterRegistry metrics registry
     * @param settings connection settings for Valkey
     * @return configured GLIDE client
     */
    @Bean
    @Primary
    public ValkeyClient glideClient(
            MeterRegistry meterRegistry,
            @org.springframework.beans.factory.annotation.Qualifier("valkeyConnectionSettings")
                    ConnectionSettings settings) {
        GlideClientImpl client = new GlideClientImpl(settings);
        client.initialize(meterRegistry);
        return client;
    }

    /**
     * Create Redisson client
     *
     * @param meterRegistry metrics registry
     * @param settings connection settings for Redis
     * @return configured Redisson client
     */
    @Bean
    public ValkeyClient redissonClient(
            MeterRegistry meterRegistry,
            @org.springframework.beans.factory.annotation.Qualifier("redisConnectionSettings")
                    ConnectionSettings settings) {
        RedissonClientImpl client = new RedissonClientImpl(settings);
        client.initialize(meterRegistry);
        return client;
    }
}
