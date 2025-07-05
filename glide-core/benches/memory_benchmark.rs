// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use glide_core::{
    client::Client,
    connection_request::{ConnectionRequest, NodeAddress, TlsMode},
};
use criterion::{criterion_group, criterion_main, Criterion};
use redis::{Value, cmd};
use std::hint::black_box;
use tokio::runtime::Builder;

fn create_connection_request() -> ConnectionRequest {
    let host = "localhost";
    let mut request = ConnectionRequest::new();
    request.tls_mode = TlsMode::NoTls.into();
    let mut address_info = NodeAddress::new();
    address_info.host = host.into();
    address_info.port = 6379;
    request.addresses.push(address_info);
    request
}

fn runner<Fut>(f: impl FnOnce(Client) -> Fut)
where
    Fut: futures::Future<Output = ()>,
{
    let runtime = Builder::new_current_thread().enable_all().build().unwrap();
    runtime.block_on(async {
        let client = Client::new(create_connection_request().into(), None)
            .await
            .unwrap();
        f(client).await;
    });
}

fn just_setup(c: &mut Criterion) {
    c.bench_function("just_setup", |b| {
        b.iter(|| {
            runner(|_| async {});
        });
    });
}

fn send_message(c: &mut Criterion) {
    c.bench_function("send_message", |b| {
        b.iter(|| {
            runner(|mut client| async move {
                client
                    .send_command(&black_box(cmd("PING")), None)
                    .await
                    .unwrap();
            });
        });
    });
}

fn send_and_receive_messages(c: &mut Criterion) {
    c.bench_function("send_and_receive_messages", |b| {
        b.iter(|| {
            runner(|mut client| async move {
                let mut command = cmd("SET");
                command.arg("foo").arg("bar");
                client
                    .send_command(&black_box(command), None)
                    .await
                    .unwrap();
                let mut command = cmd("SET");
                command.arg("baz").arg("foo");
                client
                    .send_command(&black_box(command), None)
                    .await
                    .unwrap();
                let mut command = cmd("MGET");
                command.arg("baz").arg("foo");
                let result = client
                    .send_command(&black_box(command), None)
                    .await
                    .unwrap();
                assert!(
                    result
                        == Value::Array(vec![
                            Value::BulkString(b"foo".to_vec()),
                            Value::BulkString(b"bar".to_vec())
                        ])
                )
            });
        });
    });
}

fn lots_of_messages(c: &mut Criterion) {
    c.bench_function("lots_of_messages", |b| {
        b.iter(|| {
            runner(|mut client| async move {
                for _ in 0..1000 {
                    let mut command = cmd("SET");
                    command.arg("foo").arg("bar");
                    client
                        .send_command(&black_box(command), None)
                        .await
                        .unwrap();
                    let mut command = cmd("SET");
                    command.arg("baz").arg("foo");
                    client
                        .send_command(&black_box(command), None)
                        .await
                        .unwrap();
                    let mut command = cmd("MGET");
                    command.arg("baz").arg("foo");
                    let result = client
                        .send_command(&black_box(command), None)
                        .await
                        .unwrap();
                    assert!(
                        result
                            == Value::Array(vec![
                                Value::BulkString(b"foo".to_vec()),
                                Value::BulkString(b"bar".to_vec())
                            ])
                    )
                }
            });
        });
    });
}

criterion_group!(cluster, just_setup, send_message, send_and_receive_messages, lots_of_messages);
criterion_main!(cluster);
