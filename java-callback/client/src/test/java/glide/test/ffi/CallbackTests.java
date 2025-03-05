/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.test.ffi;

import glide.supports.LoadHelper;
import glide.ffi.callback.ValkeyClient;
import glide.ffi.callback.RsLogger;
import glide.ffi.callback.ThreadCallback;
import glide.ffi.callback.ThreadSafeObserver;
import java.util.concurrent.CompletableFuture;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * @author hon_him
 * @since 2025-02-11
 */
@Slf4j
public class CallbackTests {

    @BeforeAll
    static void before() {
        LoadHelper.load("glide_rs_cb");
    }

    @Test
    @SneakyThrows
    void connected() {
        ValkeyClient client = new ValkeyClient("redis://10.37.1.132:6381");
        RsLogger.init();
        CompletableFuture<String> await = new CompletableFuture<>();
        ThreadCallback.connect(client, (ConnectHandler) () -> {
            log.info("connected");
            await.complete("");
        });
        await.get();
        CompletableFuture<String> completableFuture = new CompletableFuture<>();
        ThreadCallback.submit("get hello", client, new ThreadSafeObserver() {
            @Override
            public void onConnected() {

            }

            @Override
            public void onResponse(String s) {
                log.info("in other thread: {}", s);
                assert StringUtils.isNotBlank(s);
                completableFuture.complete(s);
            }

            @Override
            public void onError(String m) {
                System.err.println(m);
                completableFuture.completeExceptionally(new RuntimeException(m));
            }
        });

        String s = completableFuture.get();
        log.info("in main thread: {}", s);
    }
}
