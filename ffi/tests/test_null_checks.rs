use glide_core::request_type::RequestType;
use glide_ffi::*;
use std::ffi::{c_char, c_void, CString};

#[test]
fn test_store_script_null() {
    let res = unsafe { store_script(std::ptr::null(), 0) };
    assert!(res.is_null());
}

#[test]
fn test_create_client_null() {
    let client_type = Box::into_raw(Box::new(ClientType::SyncClient));
    let res = unsafe { create_client(std::ptr::null(), 0, client_type, std::mem::transmute(0usize)) };
    assert!(!res.is_null());
    let response = unsafe { res.as_ref() }.expect("response is null");
    assert!(response.conn_ptr.is_null());
    assert!(!response.connection_error_message.is_null());

    let err_msg = unsafe { std::ffi::CStr::from_ptr(response.connection_error_message).to_str().unwrap() };
    assert!(err_msg.contains("Connection request bytes pointer is null"));

    unsafe { free_connection_response(res as *mut ConnectionResponse) };
    unsafe { let _ = Box::from_raw(client_type); }
}

#[test]
fn test_invoke_script_null_client() {
    let res = unsafe {
        invoke_script(
            std::ptr::null(),
            0,
            std::ptr::null(),
            0,
            std::ptr::null(),
            std::ptr::null(),
            0,
            std::ptr::null(),
            std::ptr::null(),
            std::ptr::null(),
            0
        )
    };
    assert!(!res.is_null());
    let result = unsafe { res.as_ref() }.expect("result is null");
    assert!(result.response.is_null());
    assert!(!result.command_error.is_null());

    let error = unsafe { result.command_error.as_ref() }.expect("command_error is null");
    let err_msg = unsafe { std::ffi::CStr::from_ptr(error.command_error_message).to_str().unwrap() };
    assert!(err_msg.contains("Client adapter pointer is null"));

    unsafe { free_command_result(res) };
}

#[test]
fn test_command_null_client() {
    let res = unsafe {
        command(
            std::ptr::null(),
            0,
            RequestType::Ping,
            0,
            std::ptr::null(),
            std::ptr::null(),
            std::ptr::null(),
            0,
            0
        )
    };
    assert!(!res.is_null());
    let result = unsafe { res.as_ref() }.expect("result is null");
    assert!(result.response.is_null());
    assert!(!result.command_error.is_null());

    let error = unsafe { result.command_error.as_ref() }.expect("command_error is null");
    let err_msg = unsafe { std::ffi::CStr::from_ptr(error.command_error_message).to_str().unwrap() };
    assert!(err_msg.contains("Client adapter pointer is null"));

    unsafe { free_command_result(res) };
}

#[test]
fn test_request_cluster_scan_null_client() {
    let res = unsafe {
        request_cluster_scan(
            std::ptr::null(),
            0,
            std::ptr::null(),
            0,
            std::ptr::null(),
            std::ptr::null(),
        )
    };
    assert!(!res.is_null());
    let result = unsafe { res.as_ref() }.expect("result is null");
    assert!(result.response.is_null());
    assert!(!result.command_error.is_null());

    let error = unsafe { result.command_error.as_ref() }.expect("command_error is null");
    let err_msg = unsafe { std::ffi::CStr::from_ptr(error.command_error_message).to_str().unwrap() };
    assert!(err_msg.contains("Client adapter pointer is null"));

    unsafe { free_command_result(res) };
}

#[test]
fn test_update_connection_password_null_client() {
    let res = unsafe {
        update_connection_password(
            std::ptr::null(),
            0,
            std::ptr::null(),
            false
        )
    };
    assert!(!res.is_null());
    let result = unsafe { res.as_ref() }.expect("result is null");
    assert!(result.response.is_null());
    assert!(!result.command_error.is_null());

    let error = unsafe { result.command_error.as_ref() }.expect("command_error is null");
    let err_msg = unsafe { std::ffi::CStr::from_ptr(error.command_error_message).to_str().unwrap() };
    assert!(err_msg.contains("Client adapter pointer is null"));

    unsafe { free_command_result(res) };
}

#[test]
fn test_refresh_iam_token_null_client() {
    let res = unsafe {
        refresh_iam_token(
            std::ptr::null(),
            0,
        )
    };
    assert!(!res.is_null());
    let result = unsafe { res.as_ref() }.expect("result is null");
    assert!(result.response.is_null());
    assert!(!result.command_error.is_null());

    let error = unsafe { result.command_error.as_ref() }.expect("command_error is null");
    let err_msg = unsafe { std::ffi::CStr::from_ptr(error.command_error_message).to_str().unwrap() };
    assert!(err_msg.contains("Client adapter pointer is null"));

    unsafe { free_command_result(res) };
}

#[test]
fn test_batch_null_client() {
    let res = unsafe {
        batch(
            std::ptr::null(),
            0,
            std::ptr::null(),
            false,
            std::ptr::null(),
            0
        )
    };
    assert!(!res.is_null());
    let result = unsafe { res.as_ref() }.expect("result is null");
    assert!(result.response.is_null());
    assert!(!result.command_error.is_null());

    let error = unsafe { result.command_error.as_ref() }.expect("command_error is null");
    let err_msg = unsafe { std::ffi::CStr::from_ptr(error.command_error_message).to_str().unwrap() };
    assert!(err_msg.contains("Client adapter pointer is null"));

    unsafe { free_command_result(res) };
}
