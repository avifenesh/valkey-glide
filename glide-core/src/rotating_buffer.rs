// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
#[allow(unused_imports)]
use bytes::{Bytes, BytesMut};
use integer_encoding::VarInt;
use logger_core::log_error;
use protobuf::Message;
use std::io;

/// An object handling a arranging read buffers, and parsing the data in the buffers into requests.
pub struct RotatingBuffer {
    backing_buffer: BytesMut,
    initial_capacity: usize,
}

impl RotatingBuffer {
    pub fn new(buffer_size: usize) -> Self {
        Self {
            backing_buffer: BytesMut::with_capacity(buffer_size),
            initial_capacity: buffer_size,
        }
    }

    /// Parses the requests in the buffer.
    pub fn get_requests<T: Message>(&mut self) -> io::Result<Vec<T>> {
        let mut results: Vec<T> = vec![];
        // Peek at the buffer to see if we have a full request
        // decode_var returns Some((value, bytes_read)) if successful
        while let Some((request_len, varint_len)) = u32::decode_var(&self.backing_buffer[..]) {
            let total_len = varint_len + request_len as usize;
            if self.backing_buffer.len() >= total_len {
                // Extract the full message including length prefix
                // split_to is O(1) and leaves the remaining bytes in self.backing_buffer
                let message_buf = self.backing_buffer.split_to(total_len).freeze();

                // Parse the message body, skipping the length prefix
                let body = message_buf.slice(varint_len..);
                match T::parse_from_tokio_bytes(&body) {
                    Ok(request) => {
                        results.push(request);
                    }
                    Err(err) => {
                        log_error("parse input", format!("Failed to parse request: {err}"));
                        return Err(err.into());
                    }
                }
            } else {
                // We have the length, but not enough data for the body.
                break;
            }
        }
        Ok(results)
    }

    pub fn current_buffer(&mut self) -> &mut BytesMut {
        // Ensure there is enough space to read more data.
        // split_to advances the read pointer, but BytesMut might not automatically
        // reclaim space at the beginning until we ask for more capacity.
        // We ensure we try to maintain the configured capacity.
        if self.backing_buffer.capacity() - self.backing_buffer.len() < self.initial_capacity / 4 {
            self.backing_buffer.reserve(self.initial_capacity);
        }
        &mut self.backing_buffer
    }
}

#[cfg(test)]
mod tests {
    use std::ptr::from_mut;

    use super::*;
    use crate::command_request::{Command, CommandRequest, RequestType};
    use crate::command_request::{command, command_request};
    use bytes::BufMut;
    use rand::{Rng, distributions::Alphanumeric};
    use rstest::rstest;

    fn write_length(buffer: &mut BytesMut, length: u32) {
        let required_space = u32::required_space(length);
        let new_len = buffer.len() + required_space;
        buffer.resize(new_len, 0_u8);
        u32::encode_var(length, &mut buffer[new_len - required_space..]);
    }

    fn create_command_request(
        callback_index: u32,
        args: Vec<Bytes>,
        request_type: RequestType,
        args_pointer: bool,
    ) -> CommandRequest {
        let mut request = CommandRequest::new();
        request.callback_idx = callback_index;
        let mut command = Command::new();
        command.request_type = request_type.into();
        if args_pointer {
            command.args = Some(command::Args::ArgsVecPointer(
                from_mut(Box::leak(Box::new(args))) as u64,
            ));
        } else {
            let mut args_array = command::ArgsArray::new();
            args_array.args.clone_from(&args);
            command.args = Some(command::Args::ArgsArray(args_array));
        }
        request.command = Some(command_request::Command::SingleCommand(command));
        request
    }

    fn write_message(
        buffer: &mut BytesMut,
        callback_index: u32,
        args: Vec<Bytes>,
        request_type: RequestType,
        args_pointer: bool,
    ) {
        let request = create_command_request(callback_index, args, request_type, args_pointer);
        let message_length = request.compute_size() as usize;
        write_length(buffer, message_length as u32);
        buffer.extend_from_slice(&request.write_to_bytes().unwrap());
    }

    fn write_get(buffer: &mut BytesMut, callback_index: u32, key: &str, args_pointer: bool) {
        write_message(
            buffer,
            callback_index,
            vec![Bytes::from(key.to_string())],
            RequestType::Get,
            args_pointer,
        );
    }

    fn write_set(
        buffer: &mut BytesMut,
        callback_index: u32,
        key: &str,
        value: Bytes,
        args_pointer: bool,
    ) {
        write_message(
            buffer,
            callback_index,
            vec![Bytes::from(key.to_string()), value],
            RequestType::Set,
            args_pointer,
        );
    }

    fn assert_request(
        request: &CommandRequest,
        expected_type: RequestType,
        expected_index: u32,
        expected_args: Vec<Bytes>,
        args_pointer: bool,
    ) {
        assert_eq!(request.callback_idx, expected_index);
        let Some(command_request::Command::SingleCommand(ref command)) = request.command else {
            panic!("expected single command");
        };
        assert_eq!(command.request_type, expected_type.into());
        let args: Vec<Bytes> = if args_pointer {
            *unsafe { Box::from_raw(command.args_vec_pointer() as *mut Vec<Bytes>) }
        } else {
            command.args_array().args.to_vec()
        };
        assert_eq!(args, expected_args);
    }

    fn generate_random_string(length: usize) -> String {
        rand::thread_rng()
            .sample_iter(&Alphanumeric)
            .take(length)
            .map(char::from)
            .collect()
    }

    #[rstest]
    fn get_right_sized_buffer() {
        let mut rotating_buffer = RotatingBuffer::new(128);
        assert!(rotating_buffer.current_buffer().capacity() >= 128);
        assert_eq!(rotating_buffer.current_buffer().len(), 0);
    }

    #[rstest]
    fn get_requests(#[values(false, true)] args_pointer: bool) {
        const BUFFER_SIZE: usize = 50;
        let mut rotating_buffer = RotatingBuffer::new(BUFFER_SIZE);
        write_get(rotating_buffer.current_buffer(), 100, "key", args_pointer);
        write_set(
            rotating_buffer.current_buffer(),
            5,
            "key",
            "value".into(),
            args_pointer,
        );
        let requests = rotating_buffer.get_requests().unwrap();
        assert_eq!(requests.len(), 2);
        assert_request(
            &requests[0],
            RequestType::Get,
            100,
            vec!["key".into()],
            args_pointer,
        );
        assert_request(
            &requests[1],
            RequestType::Set,
            5,
            vec!["key".into(), "value".into()],
            args_pointer,
        );
    }

    #[rstest]
    fn repeating_requests_from_same_buffer(#[values(false, true)] args_pointer: bool) {
        const BUFFER_SIZE: usize = 50;
        let mut rotating_buffer = RotatingBuffer::new(BUFFER_SIZE);
        write_get(rotating_buffer.current_buffer(), 100, "key", args_pointer);
        let requests = rotating_buffer.get_requests().unwrap();
        assert_request(
            &requests[0],
            RequestType::Get,
            100,
            vec!["key".into()],
            args_pointer,
        );
        write_set(
            rotating_buffer.current_buffer(),
            5,
            "key",
            "value".into(),
            args_pointer,
        );
        let requests = rotating_buffer.get_requests().unwrap();
        assert_eq!(requests.len(), 1);
        assert_request(
            &requests[0],
            RequestType::Set,
            5,
            vec!["key".into(), "value".into()],
            args_pointer,
        );
    }

    #[rstest]
    fn next_write_doesnt_affect_values() {
        const BUFFER_SIZE: u32 = 16;
        let mut rotating_buffer = RotatingBuffer::new(BUFFER_SIZE as usize);
        write_get(rotating_buffer.current_buffer(), 100, "key", false);

        let requests = rotating_buffer.get_requests().unwrap();
        assert_eq!(requests.len(), 1);
        assert_request(
            &requests[0],
            RequestType::Get,
            100,
            vec!["key".into()],
            false,
        );

        while rotating_buffer.backing_buffer.len() < rotating_buffer.backing_buffer.capacity() {
            rotating_buffer.backing_buffer.put_u8(0_u8);
        }
        assert_request(
            &requests[0],
            RequestType::Get,
            100,
            vec!["key".into()],
            false,
        );
    }

    #[rstest]
    fn copy_full_message_and_a_second_length_with_partial_message_to_next_buffer(
        #[values(false, true)] args_pointer: bool,
    ) {
        const NUM_OF_MESSAGE_BYTES: usize = 2;
        let mut rotating_buffer = RotatingBuffer::new(24);
        write_get(rotating_buffer.current_buffer(), 100, "key1", args_pointer);

        let mut second_request_bytes = BytesMut::new();
        write_get(&mut second_request_bytes, 101, "key2", args_pointer);
        let buffer = rotating_buffer.current_buffer();
        buffer.extend_from_slice(&second_request_bytes[..NUM_OF_MESSAGE_BYTES]);
        let requests = rotating_buffer.get_requests().unwrap();
        assert_eq!(requests.len(), 1);
        assert_request(
            &requests[0],
            RequestType::Get,
            100,
            vec!["key1".into()],
            args_pointer,
        );
        let buffer = rotating_buffer.current_buffer();
        assert_eq!(buffer.len(), NUM_OF_MESSAGE_BYTES);
        buffer.extend_from_slice(&second_request_bytes[NUM_OF_MESSAGE_BYTES..]);
        let requests = rotating_buffer.get_requests().unwrap();
        assert_eq!(requests.len(), 1);
        assert_request(
            &requests[0],
            RequestType::Get,
            101,
            vec!["key2".into()],
            args_pointer,
        );
    }

    #[rstest]
    fn copy_partial_length_to_buffer(#[values(false, true)] args_pointer: bool) {
        const NUM_OF_LENGTH_BYTES: usize = 1;
        const KEY_LENGTH: usize = 10000;
        let mut rotating_buffer = RotatingBuffer::new(24);
        let buffer = rotating_buffer.current_buffer();
        let key = generate_random_string(KEY_LENGTH);
        let mut request_bytes = BytesMut::new();
        write_get(&mut request_bytes, 100, key.as_str(), args_pointer);

        let required_varint_length = u32::required_space(KEY_LENGTH as u32);
        assert!(required_varint_length > 1); // so we could split the write of the varint
        buffer.extend_from_slice(&request_bytes[..NUM_OF_LENGTH_BYTES]);
        let requests = rotating_buffer.get_requests::<CommandRequest>().unwrap();
        assert_eq!(requests.len(), 0);
        let buffer = rotating_buffer.current_buffer();
        buffer.extend_from_slice(&request_bytes[NUM_OF_LENGTH_BYTES..]);
        let requests = rotating_buffer.get_requests().unwrap();
        assert_eq!(requests.len(), 1);
        assert_request(
            &requests[0],
            RequestType::Get,
            100,
            vec![key.into()],
            args_pointer,
        );
    }

    #[rstest]
    fn copy_partial_length_to_buffer_after_a_full_message(
        #[values(false, true)] args_pointer: bool,
    ) {
        const NUM_OF_LENGTH_BYTES: usize = 1;
        const KEY_LENGTH: usize = 10000;
        let mut rotating_buffer = RotatingBuffer::new(24);
        let key2 = generate_random_string(KEY_LENGTH);
        let required_varint_length = u32::required_space(KEY_LENGTH as u32);
        assert!(required_varint_length > 1); // so we could split the write of the varint
        write_get(rotating_buffer.current_buffer(), 100, "key1", args_pointer);
        let mut request_bytes = BytesMut::new();
        write_get(&mut request_bytes, 101, key2.as_str(), args_pointer);

        let buffer = rotating_buffer.current_buffer();
        buffer.extend_from_slice(&request_bytes[..NUM_OF_LENGTH_BYTES]);
        let requests = rotating_buffer.get_requests().unwrap();
        assert_eq!(requests.len(), 1);
        assert_request(
            &requests[0],
            RequestType::Get,
            100,
            vec!["key1".into()],
            args_pointer,
        );
        let buffer = rotating_buffer.current_buffer();
        assert_eq!(buffer.len(), NUM_OF_LENGTH_BYTES);
        buffer.extend_from_slice(&request_bytes[NUM_OF_LENGTH_BYTES..]);
        let requests = rotating_buffer.get_requests().unwrap();
        assert_eq!(requests.len(), 1);
        assert_request(
            &requests[0],
            RequestType::Get,
            101,
            vec![key2.into()],
            args_pointer,
        );
    }

    #[rstest]
    fn refill_buffer_after_consuming(#[values(false)] args_pointer: bool) {
        // This test verifies that we can fill the buffer, consume it (via split_to),
        // and then continue writing to it (requiring BytesMut to reclaim/reallocate).
        const BUFFER_SIZE: usize = 128;
        let mut rotating_buffer = RotatingBuffer::new(BUFFER_SIZE);

        // Write enough data to fill/mostly fill the buffer
        let key = generate_random_string(60);
        write_get(rotating_buffer.current_buffer(), 1, &key, args_pointer);

        let requests = rotating_buffer.get_requests::<CommandRequest>().unwrap();
        assert_eq!(requests.len(), 1);

        // At this point, the buffer should be logically empty (len=0),
        // but the underlying storage might be at the end.
        // writing again should trigger reserve/compact/alloc inside current_buffer/reserve

        let key2 = generate_random_string(60);
        write_get(rotating_buffer.current_buffer(), 2, &key2, args_pointer);

        let requests2 = rotating_buffer.get_requests::<CommandRequest>().unwrap();
        assert_eq!(requests2.len(), 1);
        assert_request(
            &requests2[0],
            RequestType::Get,
            2,
            vec![key2.into()],
            args_pointer,
        );
    }
}
