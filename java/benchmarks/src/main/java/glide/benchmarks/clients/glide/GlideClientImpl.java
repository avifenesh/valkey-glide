/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.benchmarks.clients.glide;

import com.google.gson.Gson;
import glide.benchmarks.clients.ValkeyClient;
import glide.benchmarks.config.ConnectionSettings;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import glide.api.GlideClient;
import glide.api.GlideClusterClient;
import glide.api.BaseClient;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.configuration.GlideClusterClientConfiguration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ExecutionException;

/** Valkey GLIDE client implementation for benchmarking */
public class GlideClientImpl implements ValkeyClient {
    private static final String CLIENT_NAME = "ValkeyGlide";

    private BaseClient glide;
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
            if (settings.isClusterMode()) {
                // Create cluster client configuration
                GlideClusterClientConfiguration config = GlideClusterClientConfiguration.builder()
                        .address(
                                NodeAddress.builder()
                                        .host(settings.getHost())
                                        .port(settings.getPort())
                                        .build())
                        .useTLS(settings.isUseSsl())
                        .build();

                // Create the client asynchronously
                try {
                    glide = GlideClusterClient.createClient(config).get(10, TimeUnit.SECONDS);
                } catch (InterruptedException | ExecutionException | java.util.concurrent.TimeoutException e) {
                    throw new RuntimeException("Failed to create cluster client", e);
                }
            } else {
                // Create standalone client configuration
                GlideClientConfiguration config = GlideClientConfiguration.builder()
                        .address(
                                NodeAddress.builder()
                                        .host(settings.getHost())
                                        .port(settings.getPort())
                                        .build())
                        .useTLS(settings.isUseSsl())
                        .build();

                // Create the client asynchronously
                try {
                    glide = GlideClient.createClient(config).get(10, TimeUnit.SECONDS);
                } catch (InterruptedException | ExecutionException | java.util.concurrent.TimeoutException e) {
                    throw new RuntimeException("Failed to create client", e);
                }
            }
            
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
                        CompletableFuture<String> future = glide.get(key);
                        try {
                            String result = future.get();
                            if (result != null) {
                                hitCount.incrementAndGet();
                                return result;
                            } else {
                                missCount.incrementAndGet();
                                return null;
                            }
                        } catch (Exception e) {
                            errorCount.incrementAndGet();
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
                try {
                    CompletableFuture<List<String>> future = glide.mget(keyArray);
                    List<String> values = future.get();
                    
                    // Process results
                    for (int i = 0; i < keys.size(); i++) {
                        String value = values.get(i);
                        if (value != null) {
                            result.put(keys.get(i), value);
                            hitCount.incrementAndGet();
                        } else {
                            missCount.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
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
                        try {
                            glide.set(key, value).get();
                            writeCount.incrementAndGet();
                            return true;
                        } catch (Exception e) {
                            errorCount.incrementAndGet();
                            return false;
                        }
                    });
        } catch (Exception e) {
            errorCount.incrementAndGet();
            return false;
        }
    }
    
    /**
     * Optimized bulk set operation for better performance
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
                try {
                    // Process all key-value pairs individually
                    CompletableFuture<?>[] futures = new CompletableFuture[keyValueMap.size()];
                    int i = 0;
                    for (Map.Entry<String, String> entry : keyValueMap.entrySet()) {
                        futures[i++] = glide.set(entry.getKey(), entry.getValue());
                    }
                    CompletableFuture.allOf(futures).get();
                    writeCount.addAndGet(keyValueMap.size());
                    return true;
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    return false;
                }
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
                        try {
                            glide.setex(key, expireSeconds, value).get();
                            writeCount.incrementAndGet();
                            return true;
                        } catch (Exception e) {
                            errorCount.incrementAndGet();
                            return false;
                        }
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
                        try {
                            long result = glide.del(key).get();
                            return result > 0;
                        } catch (Exception e) {
                            errorCount.incrementAndGet();
                            return false;
                        }
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
                        try {
                            long result = glide.exists(key).get();
                            return result > 0;
                        } catch (Exception e) {
                            errorCount.incrementAndGet();
                            return false;
                        }
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

        return new Gson().toJson(stats);
    }
}
