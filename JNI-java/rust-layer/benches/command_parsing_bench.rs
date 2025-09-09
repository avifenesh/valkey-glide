use criterion::{Criterion, black_box, criterion_group, criterion_main};

fn parse_simple_split(command: &str) -> (&str, Option<&str>) {
    let ct = command.trim();
    let mut parts = ct.splitn(2, char::is_whitespace);
    let primary = parts.next().unwrap_or(ct);
    let secondary = parts.next();
    (primary, secondary)
}

fn parse_with_find(command: &str) -> (&str, Option<&str>) {
    let ct = command.trim();
    match ct.find(char::is_whitespace) {
        Some(pos) => {
            let (first, rest) = ct.split_at(pos);
            (first, Some(rest.trim()))
        }
        None => (ct, None),
    }
}

fn parse_minimal(command: &str) -> (&str, Option<&str>) {
    let mut parts = command.splitn(2, ' ');
    let primary = parts.next().unwrap_or(command);
    let secondary = parts.next();
    (primary, secondary)
}

const KNOWN_MULTI_WORD_PREFIXES: &[&str] = &[
    "CONFIG", "CLIENT", "SCRIPT", "FUNCTION", "PUBSUB", "OBJECT", "XGROUP",
];

fn parse_with_prefix_check(command: &str) -> (&str, Option<&str>) {
    let ct = command.trim();

    // Fast path: check if it could be multi-word
    let needs_split = KNOWN_MULTI_WORD_PREFIXES
        .iter()
        .any(|prefix| ct.starts_with(prefix) && ct.len() > prefix.len());

    if needs_split {
        let mut parts = ct.splitn(2, char::is_whitespace);
        let primary = parts.next().unwrap_or(ct);
        let secondary = parts.next();
        (primary, secondary)
    } else {
        (ct, None)
    }
}

fn benchmark_command_parsing(c: &mut Criterion) {
    let single_word_commands = vec![
        "GET", "SET", "HGET", "LPUSH", "ZADD", "SADD", "INCR", "DECR", "EXPIRE", "TTL", "PING",
        "EXISTS", "DEL", "MGET", "MSET",
    ];

    let multi_word_commands = vec![
        "CONFIG GET",
        "SCRIPT EXISTS",
        "FUNCTION LIST",
        "CLIENT ID",
        "PUBSUB CHANNELS",
        "OBJECT ENCODING",
        "XGROUP SETID",
    ];

    c.bench_function("parse_simple_split_single", |b| {
        b.iter(|| {
            for cmd in &single_word_commands {
                black_box(parse_simple_split(cmd));
            }
        })
    });

    c.bench_function("parse_with_find_single", |b| {
        b.iter(|| {
            for cmd in &single_word_commands {
                black_box(parse_with_find(cmd));
            }
        })
    });

    c.bench_function("parse_minimal_single", |b| {
        b.iter(|| {
            for cmd in &single_word_commands {
                black_box(parse_minimal(cmd));
            }
        })
    });

    c.bench_function("parse_with_prefix_check_single", |b| {
        b.iter(|| {
            for cmd in &single_word_commands {
                black_box(parse_with_prefix_check(cmd));
            }
        })
    });

    c.bench_function("parse_simple_split_multi", |b| {
        b.iter(|| {
            for cmd in &multi_word_commands {
                black_box(parse_simple_split(cmd));
            }
        })
    });

    c.bench_function("parse_with_find_multi", |b| {
        b.iter(|| {
            for cmd in &multi_word_commands {
                black_box(parse_with_find(cmd));
            }
        })
    });
}

criterion_group!(benches, benchmark_command_parsing);
criterion_main!(benches);
