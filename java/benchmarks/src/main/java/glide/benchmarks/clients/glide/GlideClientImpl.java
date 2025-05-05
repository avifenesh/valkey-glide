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
import java.util.Map;
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

    private void initializeClient() {
        try {
            // Create cluster client with optimized configuration
            GlideClusterClient.Builder builder =
                    GlideClusterClient.builder()
                            .addNode(settings.getHost(), settings.getPort())
                            .useSSL(settings.isUseSsl())
                            .connectTimeout(settings.getConnectTimeout())
                            .commandTimeout(settings.getTimeout())
                            .maxConnections(settings.getMasterConnectionsPoolSize())
                            .minConnections(settings.getMasterConnectionsMinIdleSize());

            // Configure client options for best performance
            GlideOptions options =
                    GlideOptions.builder().autoReconnect(true).tcpNoDelay(true).tcpKeepAlive(true).build();

            builder.options(options);

            // Disable logging for performance
            builder.logLevel(null);

            glide = builder.build();
        } catch (Exception e) {
            errorCount.incrementAndGet();
            throw new RuntimeException("Failed to initialize Valkey GLIDE client", e);
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
