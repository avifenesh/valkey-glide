package glide.test.ffi;

import glide.ffi.callback.ThreadSafeObserver;

/**
 * @author hon_him
 * @since 2025-02-11
 */

public interface ConnectHandler extends ThreadSafeObserver {
    @Override
    default void onResponse(String s) {

    }

    @Override
    default void onError(String m) {

    }
}
