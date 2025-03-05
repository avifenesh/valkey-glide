package glide.benchmarks.clients.callback;

import glide.api.ValkeyOperator;
import glide.benchmarks.clients.AsyncClient;
import glide.benchmarks.utils.ConnectionSettings;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * @author hon_him
 * @since 2025-02-12
 */
public class ValkeyAsyncClient implements AsyncClient<String> {

    private ValkeyOperator valkeyOperator;

    @Override
    public Future<String> asyncSet(String key, String value) {
        return valkeyOperator.set(key, value);
    }

    @Override
    public Future<String> asyncGet(String key) {
        return valkeyOperator.get(key);
    }

    @Override
    public void connectToValkey(ConnectionSettings connectionSettings) {
        String url =
                String.format(
                        "%s://%s:%d",
                        connectionSettings.useSsl ? "rediss" : "redis",
                        connectionSettings.host,
                        connectionSettings.port);
        valkeyOperator = ValkeyOperator.fromUrl(url);
        CompletableFuture<Void> start = valkeyOperator.start();
        try {
            start.get(1, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getName() {
        return "glide-callback";
    }
}
