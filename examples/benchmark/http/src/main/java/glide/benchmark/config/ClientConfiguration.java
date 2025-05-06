package glide.benchmark.config;

import static glide.api.logging.Logger.Level.OFF;
import static glide.api.logging.Logger.log;

import glide.api.GlideClusterClient;
import glide.api.logging.Logger;
import glide.api.models.configuration.GlideClusterClientConfiguration;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.ReadFrom;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.ReadMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.ExecutionException;

/**
 * Configuration class that sets up both Valkey Glide and Redisson clients
 * with equivalent parameters for fair comparison benchmarking.
 */
@Configuration
public class ClientConfiguration {

    // Valkey configuration
    @Value("${benchmark.valkey.host}")
    private String valkeyHost;

    @Value("${benchmark.valkey.port}")
    private int valkeyPort;

    @Value("${benchmark.valkey.use-tls}")
    private boolean valkeyUseTls;

    // Redis configuration
    @Value("${benchmark.redis.host}")
    private String redisHost;

    @Value("${benchmark.redis.port}")
    private int redisPort;

    @Value("${benchmark.redis.use-tls}")
    private boolean redisUseTls;

    // Glide specific configuration
    @Value("${benchmark.glide.client-az}")
    private String glideClientAz;

    @Value("${benchmark.glide.read-from}")
    private String glideReadFrom;

    // Redisson specific configuration
    @Value("${benchmark.redisson.connection-timeout}")
    private int redissonConnectionTimeout;

    @Value("${benchmark.redisson.timeout}")
    private int redissonTimeout;

    @Value("${benchmark.redisson.idle-connection-timeout}")
    private int redissonIdleConnectionTimeout;

    @Value("${benchmark.redisson.master-connections-min-idle-size}")
    private int redissonMasterConnectionsMinIdleSize;

    @Value("${benchmark.redisson.master-connections-pool-size}")
    private int redissonMasterConnectionsPoolSize;

    @Value("${benchmark.redisson.slave-connections-min-idle-size}")
    private int redissonSlaveConnectionsMinIdleSize;

    @Value("${benchmark.redisson.slave-connections-pool-size}")
    private int redissonSlaveConnectionsPoolSize;

    @Value("${benchmark.redisson.retry-attempts}")
    private int redissonRetryAttempts;

    @Value("${benchmark.redisson.retry-interval}")
    private int redissonRetryInterval;

    /**
     * Creates a Glide client for the Valkey server.
     */
    @Bean(name = "valkeyGlideClient")
    @Primary
    public GlideClusterClient valkeyGlideClient() throws ExecutionException, InterruptedException {
        Logger.setLoggerConfig(OFF);
        NodeAddress nodeAddress = NodeAddress.builder()
                .host(valkeyHost)
                .port(valkeyPort)
                .build();

        GlideClusterClientConfiguration.Builder configBuilder =
                GlideClusterClientConfiguration.builder()
                        .address(nodeAddress)
                        .useTLS(valkeyUseTls);

        // Add AZ affinity
        configBuilder.readFrom(ReadFrom.valueOf(glideReadFrom))
                     .clientAZ(glideClientAz);

        GlideClusterClientConfiguration config = configBuilder.build();

        log(INFO, "benchmark", "Creating Glide client for Valkey server: " + valkeyHost + ":" + valkeyPort);
        return GlideClusterClient.createClient(config).get();
    }

    /**
     * Creates a Glide client for the Redis server.
     */
    @Bean(name = "redisGlideClient")
    public GlideClusterClient redisGlideClient() throws ExecutionException, InterruptedException {
        Logger.setLoggerConfig(OFF);
        NodeAddress nodeAddress = NodeAddress.builder()
                .host(redisHost)
                .port(redisPort)
                .build();

        GlideClusterClientConfiguration.Builder configBuilder =
                GlideClusterClientConfiguration.builder()
                        .address(nodeAddress)
                        .useTLS(redisUseTls);

        // Add AZ affinity
        configBuilder.readFrom(ReadFrom.valueOf(glideReadFrom))
                     .clientAZ(glideClientAz);
        
        GlideClusterClientConfiguration config = configBuilder.build();

        log(INFO, "benchmark", "Creating Glide client for Redis server: " + redisHost + ":" + redisPort);
        return GlideClusterClient.createClient(config).get();
    }

    /**
     * Creates a Redisson client for the Valkey server.
     */
    @Bean(name = "valkeyRedissonClient")
    @Primary
    public RedissonClient valkeyRedissonClient() {
        Config config = new Config();
        String redisEndpoint = "rediss://" + valkeyHost + ":" + valkeyPort;

        config.useClusterServers()
                .setKeepAlive(true)
                .addNodeAddress(redisEndpoint)
                .setReadMode(ReadMode.SLAVE)
                .setTimeout(redissonTimeout)
                .setConnectTimeout(redissonConnectionTimeout)
                .setIdleConnectionTimeout(redissonIdleConnectionTimeout)
                .setMasterConnectionMinimumIdleSize(redissonMasterConnectionsMinIdleSize)
                .setMasterConnectionPoolSize(redissonMasterConnectionsPoolSize)
                .setSlaveConnectionMinimumIdleSize(redissonSlaveConnectionsMinIdleSize)
                .setSlaveConnectionPoolSize(redissonSlaveConnectionsPoolSize)
                .setRetryAttempts(redissonRetryAttempts)
                .setRetryInterval(redissonRetryInterval)
                .setSslEnableEndpointIdentification(true)
                .setSslProvider("JDK");

        log(INFO, "benchmark", "Creating Redisson client for Valkey server: " + valkeyHost + ":" + valkeyPort);
        return Redisson.create(config);
    }

    /**
     * Creates a Redisson client for the Redis server.
     */
    @Bean(name = "redisRedissonClient")
    public RedissonClient redisRedissonClient() {
        Config config = new Config();
        String redisEndpoint = "rediss://" + redisHost + ":" + redisPort;

        config.useClusterServers()
                .setKeepAlive(true)
                .addNodeAddress(redisEndpoint)
                .setReadMode(ReadMode.SLAVE)
                .setTimeout(redissonTimeout)
                .setConnectTimeout(redissonConnectionTimeout)
                .setIdleConnectionTimeout(redissonIdleConnectionTimeout)
                .setMasterConnectionMinimumIdleSize(redissonMasterConnectionsMinIdleSize)
                .setMasterConnectionPoolSize(redissonMasterConnectionsPoolSize)
                .setSlaveConnectionMinimumIdleSize(redissonSlaveConnectionsMinIdleSize)
                .setSlaveConnectionPoolSize(redissonSlaveConnectionsPoolSize)
                .setRetryAttempts(redissonRetryAttempts)
                .setRetryInterval(redissonRetryInterval)
                .setSslEnableEndpointIdentification(true)
                .setSslProvider("JDK");

        log(INFO, "benchmark", "Creating Redisson client for Redis server: " + redisHost + ":" + redisPort);
        return Redisson.create(config);
    }
}
