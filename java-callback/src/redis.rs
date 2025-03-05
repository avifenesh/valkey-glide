use anyhow::{Context, Result};
use redis::{AsyncCommands, Client, Cmd, Connection, GlideConnectionOptions, Value};
use redis::aio::MultiplexedConnection;

#[macro_export]
macro_rules! str_cmd {
    ($cmd:expr) => {{
        let mut command = Cmd::new();
        let parts: Vec<String> = split_args($cmd);
        for arg in &parts {
            command.arg(arg);
        }
        command
    }};
}

pub fn create_client_from_url(url: &str) -> Result<Client> {
    Client::open(url).context("Failed to create redis client")
}

pub async fn query(client: &Client, cmd: impl Into<String>) -> Result<String> {
    let mut connection = client.get_multiplexed_async_connection(GlideConnectionOptions::default()).await?;
    let value: String = str_cmd!(cmd).query_async(&mut connection).await?;
    Ok(value)
}

pub fn do_query(con: &mut Connection, cmd: impl Into<String>) -> Result<String> {
    let value: Value = str_cmd!(cmd).query(con)?;
    value_to_string(value)
}

pub async fn do_query_async(con: &mut MultiplexedConnection, cmd: impl Into<String>) -> Result<String> {
    let value: Value = str_cmd!(cmd).query_async(con).await?;
    value_to_string(value)
}

fn value_to_string(value: Value) -> Result<String> {
    let s = match value {
        Value::Nil => "Nil".to_string(),
        Value::Int(i) => i.to_string(),
        Value::BulkString(bs) => String::from_utf8(bs)?,
        Value::Array(arr) => format!("Array[{}]", arr.len()),
        Value::SimpleString(ss) => ss,
        Value::Okay => "Okay".to_string(),
        Value::Map(m) => format!("Map[{}]", m.len()),
        Value::Attribute { .. } => "Attributes".to_string(),
        Value::Set(s) => format!("Set[{}]", s.len()),
        Value::Double(d) => d.to_string(),
        Value::Boolean(b) => b.to_string(),
        Value::VerbatimString { text, .. } => text,
        Value::BigNumber(n) => n.to_string(),
        Value::Push { .. } => "Push".to_string(),
    };
    Ok(s)
}

pub fn split_args(cmd: impl Into<String>) -> Vec<String> {
    let cmd = cmd.into();

    let mut parts: Vec<String> = Vec::new();
    let mut current = String::new();
    let mut in_quotes = false;
    let mut quote_char = '\0';

    for c in cmd.chars() {
        if in_quotes {
            if c == quote_char {
                in_quotes = false;
                parts.push(current.clone());
                current.clear();
            } else {
                current.push(c);
            }
        } else {
            if c.is_whitespace() {
                if !current.is_empty() {
                    parts.push(current.clone());
                    current.clear();
                }
            } else if c == '\'' || c == '"' || c == '`' {
                in_quotes = true;
                quote_char = c;
            } else {
                current.push(c);
            }
        }
    }

    if !current.is_empty() {
        parts.push(current);
    }
    parts
}
