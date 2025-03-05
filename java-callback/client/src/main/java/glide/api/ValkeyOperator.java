/**·Copyright·Valkey·GLIDE·Project·Contributors·-·SPDX·Identifier:·Apache-2.0·*/
package glide.api;

import glide.ffi.callback.RsLogger;
import glide.ffi.callback.ThreadCallback;
import glide.ffi.callback.ThreadSafeObserver;
import glide.ffi.callback.ValkeyClient;
import glide.supports.LoadHelper;
import java.util.concurrent.CompletableFuture;

/**
 * @author hon_him
 * @since 2025-02-12
 */
public class ValkeyOperator implements AutoCloseable {

    private static volatile boolean LIBRARY_LOADED = false;

    private final ValkeyClient valkeyClient;

    private ValkeyOperator(ValkeyClient valkeyClient) {
        this.valkeyClient = valkeyClient;
    }

    public static ValkeyOperator fromUrl(String url) {
        tryLoadLibrary();
        ValkeyClient client = new ValkeyClient(url);
        return new ValkeyOperator(client);
    }

    public CompletableFuture<Void> start() {
        CompletableFuture<String> async = new CompletableFuture<>();
        ThreadCallback.connect(valkeyClient, new DefaultHandler(async));
        return async.thenAccept(s -> {});
    }

    public CompletableFuture<String> set(CharSequence key, CharSequence val) {
        String cmd = String.format("SET %s %s", key, val);
        CompletableFuture<String> async = new CompletableFuture<>();
        ThreadCallback.submit(cmd, valkeyClient, new DefaultHandler(async));
        return async;
    }

    public CompletableFuture<String> get(CharSequence key) {
        String cmd = String.format("GET %s", key);
        CompletableFuture<String> async = new CompletableFuture<>();
        ThreadCallback.submit(cmd, valkeyClient, new DefaultHandler(async));
        return async;
    }

    @Override
    public void close() throws Exception {
        // TODO
    }

    private static synchronized void tryLoadLibrary() {
        if (!LIBRARY_LOADED) {
            LoadHelper.load("glide_rs_cb");
            LIBRARY_LOADED = true;
            RsLogger.init();
        }
    }

    private static class DefaultHandler implements ThreadSafeObserver {

        private final CompletableFuture<String> async;

        private DefaultHandler(CompletableFuture<String> async) {
            this.async = async;
        }

        @Override
        public void onConnected() {
            async.complete(null);
        }

        @Override
        public void onResponse(String s) {
            async.complete(s);
        }

        @Override
        public void onError(String m) {
            async.completeExceptionally(new FFIException(m));
        }
    }

    public static class FFIException extends RuntimeException {
        public FFIException() {}

        public FFIException(String message) {
            super(message);
        }

        public FFIException(String message, Throwable cause) {
            super(message, cause);
        }

        public FFIException(Throwable cause) {
            super(cause);
        }

        public FFIException(
                String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
            super(message, cause, enableSuppression, writableStackTrace);
        }
    }
}
