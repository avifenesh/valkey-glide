/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.benchmarks.config;

import glide.benchmarks.clients.RedisClient;
import glide.benchmarks.clients.glide.GlideClientImpl;
import glide.benchmarks.clients.redisson.RedissonClientImpl;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/** Configuration for Redis clients used in benchmarking */
@Configuration
public class RedisClientConfiguration {

    @Value("${redis.host:localhost}")
    private String redisHost;

    @Value("${redis.port:6379}")
    private int redisPort;

    @Value("${redis.use-ssl:false}")
    private boolean useSSL;

    @Value("${redis.cluster-mode:true}")
    private boolean clusterMode;

    @Value("${redis.timeout:1000}")
    private int timeout;

    @Value("${redis.connect-timeout:10000}")
    private int connectTimeout;

    @Value("${redis.idle-connect-timeout:1000}")
    private int idleConnectTimeout;

    @Value("${redis.master-connections-min-idle:10}")
    private int masterConnectionsMinIdle;

    @Value("${redis.master-connections-pool-size:24}")
    private int masterConnectionsPoolSize;

    @Value("${redis.slave-connections-min-idle:10}")
    private int slaveConnectionsMinIdle;

    @Value("${redis.slave-connections-pool-size:24}")
    private int slaveConnectionsPoolSize;

    @Value("${redis.retry-attempts:3}")
    private int numRetries;

    @Value("${redis.retry-interval:1000}")
    private int retryInterval;

    /**
     * Create connection settings from application properties
     *
     * @return connection settings
     */
    @Bean
    public ConnectionSettings connectionSettings() {
        return ConnectionSettings.builder()
                .host(redisHost)
                .port(redisPort)
                .useSsl(useSSL)
                .clusterMode(clusterMode)
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
     * @param settings connection settings
     * @return configured GLIDE client
     */
    @Bean
    @Primary
    public RedisClient glideClient(MeterRegistry meterRegistry, ConnectionSettings settings) {
        GlideClientImpl client = new GlideClientImpl(settings);
        client.initialize(meterRegistry);
        return client;
    }

    /**
     * Create Redisson client
     *
     * @param meterRegistry metrics registry
     * @param settings connection settings
     * @return configured Redisson client
     */
    @Bean
    public RedisClient redissonClient(MeterRegistry meterRegistry, ConnectionSettings settings) {
        RedissonClientImpl client = new RedissonClientImpl(settings);
        client.initialize(meterRegistry);
        return client;
    }
}
