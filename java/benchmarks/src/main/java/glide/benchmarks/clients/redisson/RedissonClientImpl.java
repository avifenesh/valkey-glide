/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.benchmarks.clients.redisson;

import com.google.gson.Gson;
import glide.benchmarks.clients.ValkeyClient;
import glide.benchmarks.config.ConnectionSettings;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.redisson.config.ReadMode;
import org.redisson.connection.balancer.RoundRobinLoadBalancer;

/** Redisson client implementation for benchmarking */
public class RedissonClientImpl implements ValkeyClient {
    private static final String CLIENT_NAME = "Redisson";
    private static final String REDISSON_ENDPOINT_TEMPLATE = "rediss://%s:%s";

    private RedissonClient redisson;
    private ConnectionSettings settings;
    private MeterRegistry meterRegistry;

    // Metrics
    private Timer getTimer;
    private Timer setTimer;
    private Timer deleteTimer;
    private Timer existsTimer;
    private final AtomicLong hitCount = new AtomicLong(0);
    private final AtomicLong missCount = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);
    private final AtomicLong writeCount = new AtomicLong(0);

    public RedissonClientImpl(ConnectionSettings settings) {
        this.settings = settings;
    }

    @Override
    public void initialize(MeterRegistry registry) {
        this.meterRegistry = registry;
        initializeMetrics();
        initializeClient();
    }

    private void initializeMetrics() {
        getTimer = meterRegistry.timer("valkey.get", "client", CLIENT_NAME);
        setTimer = meterRegistry.timer("valkey.set", "client", CLIENT_NAME);
        deleteTimer = meterRegistry.timer("valkey.delete", "client", CLIENT_NAME);
        existsTimer = meterRegistry.timer("valkey.exists", "client", CLIENT_NAME);

        meterRegistry.gauge("valkey.hits", hitCount);
        meterRegistry.gauge("valkey.misses", missCount);
        meterRegistry.gauge("valkey.errors", errorCount);
        meterRegistry.gauge("valkey.writes", writeCount);
    }

    private void initializeClient() {
        try {
            Config config = new Config();

            // Format endpoint for ElastiCache cluster
            String endpoint =
                    String.format(REDISSON_ENDPOINT_TEMPLATE, settings.getHost(), settings.getPort());

            // Configure for ElastiCache Cluster mode
            config
                    .useClusterServers()
                    .setLoadBalancer(new RoundRobinLoadBalancer())
                    .setScanInterval(2000)
                    .setCheckSlotsCoverage(true)
                    .setClientName("redisson-benchmark")
                    .setKeepAlive(true)
                    .setTcpNoDelay(true)
                    .addNodeAddress(endpoint)
                    .setReadMode(ReadMode.SLAVE)
                    .setTimeout(settings.getTimeout())
                    .setConnectTimeout(settings.getConnectTimeout())
                    .setIdleConnectionTimeout(settings.getIdleConnectTimeout())
                    .setMasterConnectionMinimumIdleSize(settings.getMasterConnectionsMinIdleSize())
                    .setMasterConnectionPoolSize(settings.getMasterConnectionsPoolSize())
                    .setSlaveConnectionMinimumIdleSize(settings.getSlaveConnectionsMinIdleSize())
                    .setSlaveConnectionPoolSize(settings.getSlaveConnectionsPoolSize())
                    .setRetryAttempts(settings.getNumRetries())
                    .setRetryInterval(settings.getRetryInterval())
                    .setSubscriptionsPerConnection(5) // AWS recommendation for ElastiCache
                    .setFailedSlaveCheckInterval(180000) // AWS recommendation for ElastiCache
                    .setFailedSlaveReconnectionInterval(3000) // AWS recommendation for ElastiCache
                    .setSslEnableEndpointIdentification(false); // Required for ElastiCache SSL

            // Use String codec for string data - equivalent to what we use with Glide
            config.setCodec(new StringCodec());

            // Set thread configuration for optimal performance
            config.setThreads(Runtime.getRuntime().availableProcessors() * 2);
            config.setNettyThreads(Runtime.getRuntime().availableProcessors() * 2);

            redisson = Redisson.create(config);
        } catch (Exception e) {
            errorCount.incrementAndGet();
            throw new RuntimeException("Failed to initialize Redisson client", e);
        }
    }

    @Override
    public String getName() {
        return CLIENT_NAME;
    }

    @Override
    public String get(String key) {
        try {
            return getTimer.record(
                    () -> {
                        String value = redisson.getBucket(key).get();
                        if (value != null) {
                            hitCount.incrementAndGet();
                        } else {
                            missCount.incrementAndGet();
                        }
                        return value;
                    });
        } catch (Exception e) {
            errorCount.incrementAndGet();
            return null;
        }
    }

    @Override
    public boolean set(String key, String value) {
        try {
            return setTimer.record(
                    () -> {
                        redisson.getBucket(key).set(value);
                        writeCount.incrementAndGet();
                        return true;
                    });
        } catch (Exception e) {
            errorCount.incrementAndGet();
            return false;
        }
    }

    @Override
    public boolean setWithExpiry(String key, String value, int expireSeconds) {
        try {
            return setTimer.record(
                    () -> {
                        redisson.getBucket(key).set(value, expireSeconds, TimeUnit.SECONDS);
                        writeCount.incrementAndGet();
                        return true;
                    });
        } catch (Exception e) {
            errorCount.incrementAndGet();
            return false;
        }
    }

    @Override
    public boolean delete(String key) {
        try {
            return deleteTimer.record(() -> redisson.getBucket(key).delete());
        } catch (Exception e) {
            errorCount.incrementAndGet();
            return false;
        }
    }

    @Override
    public boolean exists(String key) {
        try {
            return existsTimer.record(() -> redisson.getBucket(key).isExists());
        } catch (Exception e) {
            errorCount.incrementAndGet();
            return false;
        }
    }

    @Override
    public void reset() {
        close();
        initializeClient();
    }

    @Override
    public void close() {
        if (redisson != null && !redisson.isShutdown()) {
            try {
                redisson.shutdown();
            } catch (Exception e) {
                errorCount.incrementAndGet();
            }
        }
    }

    @Override
    public String getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("client", CLIENT_NAME);
        stats.put("hitCount", hitCount.get());
        stats.put("missCount", missCount.get());
        stats.put("errorCount", errorCount.get());
        stats.put("writeCount", writeCount.get());

        if (getTimer != null) {
            stats.put("getAvgMs", getTimer.mean(TimeUnit.MILLISECONDS));
            stats.put("getMaxMs", getTimer.max(TimeUnit.MILLISECONDS));
            stats.put("getCount", getTimer.count());
        }

        if (setTimer != null) {
            stats.put("setAvgMs", setTimer.mean(TimeUnit.MILLISECONDS));
            stats.put("setMaxMs", setTimer.max(TimeUnit.MILLISECONDS));
            stats.put("setCount", setTimer.count());
        }

        return new Gson().toJson(stats);
    }
}
