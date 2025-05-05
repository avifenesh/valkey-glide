/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.benchmarks.clients.glide;

import com.google.gson.Gson;
import glide.benchmarks.clients.ValkeyClient;
import glide.benchmarks.config.ConnectionSettings;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.valkey.GlideClusterClient;
import io.valkey.GlideGenericClient;
import io.valkey.GlideOptions;
import io.valkey.Opt;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Valkey GLIDE client implementation for benchmarking */
public class GlideClientImpl implements ValkeyClient {
    private static final String CLIENT_NAME = "ValkeyGlide";

    private GlideGenericClient glide;
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

    public GlideClientImpl(ConnectionSettings settings) {
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

    /**
     * Initialize the client with performance-optimized settings
     */
    private void initializeClient() {
        try {
            // Create cluster client with optimized configuration
            GlideClusterClient.Builder builder =
                    GlideClusterClient.builder()
                            .addNode(settings.getHost(), settings.getPort())
                            .useSSL(settings.isUseSsl())
                            .connectTimeout(settings.getConnectTimeout())
                            .commandTimeout(settings.getTimeout())
                            // Optimize connection pool for benchmark workloads
                            .maxConnections(Math.max(settings.getMasterConnectionsPoolSize(), 32))
                            .minConnections(Math.max(settings.getMasterConnectionsMinIdleSize(), 16));

            // Configure client options for best performance
            GlideOptions options = GlideOptions.builder()
                    .autoReconnect(true)
                    .tcpNoDelay(true)  // Important for latency-sensitive operations
                    .tcpKeepAlive(true)
                    .readFrom(io.valkey.ReadFrom.MASTER_PREFERRED) // Use replicas when possible for reads
                    .retryAttempts(settings.getRetryAttempts())
                    .retryInterval(settings.getRetryInterval())
                    .tcpReceiveBufferSize(1024 * 1024) // 1MB buffer for high throughput
                    .tcpSendBufferSize(1024 * 1024)
                    .build();

            builder.options(options);

            // Disable logging for performance
            builder.logLevel(null);

            glide = builder.build();
            
            // Pre-warm connection pool
            warmupConnectionPool();
            
        } catch (Exception e) {
            errorCount.incrementAndGet();
            throw new RuntimeException("Failed to initialize Valkey GLIDE client", e);
        }
    }
    
    /**
     * Pre-warm the connection pool to avoid connection initialization overhead during benchmarks
     */
    private void warmupConnectionPool() {
        // Execute a few commands to establish connections
        try {
            CompletableFuture<?>[] futures = new CompletableFuture[10];
            for (int i = 0; i < 10; i++) {
                final int idx = i;
                futures[i] = CompletableFuture.runAsync(() -> {
                    try {
                        String key = "warmup:" + idx;
                        glide.set(key, "warmup-value");
                        glide.get(key);
                        glide.del(key);
                    } catch (Exception e) {
                        // Ignore exceptions during warmup
                    }
                });
            }
            // Wait for all warmup operations to complete
            CompletableFuture.allOf(futures).join();
        } catch (Exception e) {
            // Ignore exceptions during warmup
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
                        // Using direct get command for best performance
                        Opt<String> result = glide.get(key);
                        if (result.hasValue()) {
                            hitCount.incrementAndGet();
                            return result.getValue();
                        } else {
                            missCount.incrementAndGet();
                            return null;
                        }
                    });
        } catch (Exception e) {
            errorCount.incrementAndGet();
            return null;
        }
    }
    
    /**
     * Optimized bulk get operation - useful for database population
     * and batch processing scenarios
     * 
     * @param keys List of keys to fetch
     * @return Map of key-value pairs for found keys
     */
    public Map<String, String> mget(List<String> keys) {
        try {
            Map<String, String> result = new HashMap<>();
            
            getTimer.record(() -> {
                // Convert list to array for Glide API
                String[] keyArray = keys.toArray(new String[0]);
                
                // Execute multi-get operation
                List<Opt<String>> values = glide.mget(keyArray);
                
                // Process results
                for (int i = 0; i < keys.size(); i++) {
                    Opt<String> optValue = values.get(i);
                    if (optValue.hasValue()) {
                        result.put(keys.get(i), optValue.getValue());
                        hitCount.incrementAndGet();
                    } else {
                        missCount.incrementAndGet();
                    }
                }
            });
            
            return result;
        } catch (Exception e) {
            errorCount.incrementAndGet();
            return Map.of();
        }
    }

    @Override
    public boolean set(String key, String value) {
        try {
            return setTimer.record(
                    () -> {
                        // Using direct set command for best performance
                        glide.set(key, value);
                        writeCount.incrementAndGet();
                        return true;
                    });
        } catch (Exception e) {
            errorCount.incrementAndGet();
            return false;
        }
    }
    
    /**
     * Optimized bulk set operation using pipelining for better performance
     * 
     * @param keyValueMap Map of key-value pairs to set
     * @return true if operation was successful
     */
    public boolean mset(Map<String, String> keyValueMap) {
        if (keyValueMap == null || keyValueMap.isEmpty()) {
            return true;
        }
        
        try {
            return setTimer.record(() -> {
                // Execute bulk set operation
                glide.mset(keyValueMap);
                writeCount.addAndGet(keyValueMap.size());
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
                        // Using setex for atomic operation
                        glide.setex(key, expireSeconds, value);
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
            return deleteTimer.record(
                    () -> {
                        long result = glide.del(key);
                        return result > 0;
                    });
        } catch (Exception e) {
            errorCount.incrementAndGet();
            return false;
        }
    }

    @Override
    public boolean exists(String key) {
        try {
            return existsTimer.record(
                    () -> {
                        long result = glide.exists(key);
                        return result > 0;
                    });
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
        if (glide != null) {
            try {
                glide.close();
            } catch (Exception e) {
                errorCount.incrementAndGet();
            }
        }
    }

    /**
     * Execute a pipeline of operations for better performance
     * 
     * @param callback Lambda that executes operations in a pipeline
     */
    public void pipeline(Runnable callback) {
        try {
            // Execute operations in a pipeline for better performance
            glide.withPipeline(pipeline -> {
                try {
                    callback.run();
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                }
            });
        } catch (Exception e) {
            errorCount.incrementAndGet();
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
            stats.put("getTotalMs", getTimer.totalTime(TimeUnit.MILLISECONDS));
            stats.put("getCount", getTimer.count());
            stats.put("getP95Ms", getTimer.percentile(0.95, TimeUnit.MILLISECONDS));
            stats.put("getP99Ms", getTimer.percentile(0.99, TimeUnit.MILLISECONDS));
        }

        if (setTimer != null) {
            stats.put("setAvgMs", setTimer.mean(TimeUnit.MILLISECONDS));
            stats.put("setMaxMs", setTimer.max(TimeUnit.MILLISECONDS));
            stats.put("setTotalMs", setTimer.totalTime(TimeUnit.MILLISECONDS));
            stats.put("setCount", setTimer.count());
            stats.put("setP95Ms", setTimer.percentile(0.95, TimeUnit.MILLISECONDS));
            stats.put("setP99Ms", setTimer.percentile(0.99, TimeUnit.MILLISECONDS));
        }

        // Add connection pool metrics
        try {
            if (glide instanceof GlideClusterClient) {
                // Could add cluster-specific metrics here in the future
            }
        } catch (Exception e) {
            // Ignore any exceptions during stats collection
        }

        return new Gson().toJson(stats);
    }
}
