mod async_callback;
pub mod jni_c_header;
mod redis;

use std::future::Future;
pub use crate::async_callback::*;
use once_cell::sync::Lazy;
use std::sync::RwLock;
use tokio::runtime::Runtime;
use tokio::task::JoinHandle;

static RUNTIME: Lazy<RwLock<Runtime>> = Lazy::new(|| {
    let runtime = tokio::runtime::Builder::new_multi_thread()
        .enable_all()
        .thread_name("rs4java")
        .build()
        .expect("Failed to create tokio runtime");
    RwLock::new(runtime)
});

fn spawn<F>(future: F) -> JoinHandle<F::Output>
where
    F: Future + Send + 'static,
    F::Output: Send + 'static,
{
    RUNTIME.read().expect("Failed to get tokio runtime")
        .spawn(future)
}
