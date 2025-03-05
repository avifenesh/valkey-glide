package glide.test.ffi;

import glide.ffi.callback.ThreadSafeObserver;

/**
 * @author hon_him
 * @since 2025-02-11
 */

public interface ResponseHandler extends ThreadSafeObserver {
    @Override
    default void onConnected() {

    }

    @Override
    default void onError(String m) {

    }
}
