// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use bytes::BytesMut;
use logger_core::{log_info, log_warn};
use once_cell::sync::Lazy;
use sha1_smol::Sha1;
use std::cell::Cell;
use std::collections::HashMap;
use std::sync::{Arc, Mutex};

const LOCK_ERR: &str = "Failed to acquire the scripts container lock";

/// A script entry stored in the global container.
///
/// `ScriptEntry` holds the compiled script bytes and a reference count
/// to track how many times the script has been added via `add_script`.
struct ScriptEntry {
    script: Arc<BytesMut>,
    ref_count: Cell<u32>,
}

static CONTAINER: Lazy<Mutex<HashMap<String, ScriptEntry>>> =
    Lazy::new(|| Mutex::new(HashMap::new()));

pub fn add_script(script: &[u8]) -> String {
    let mut hash = Sha1::new();
    hash.update(script);
    let hash = hash.digest().to_string();
    log_info(
        "script lifetime",
        format!("Added script with hash: `{hash}`"),
    );

    let mut container = CONTAINER.lock().expect(LOCK_ERR);
    let entry = container
        .entry(hash.clone())
        .or_insert_with(|| ScriptEntry {
            script: Arc::new(BytesMut::from(script)),
            ref_count: Cell::new(0),
        });
    let new_count = entry.ref_count.get() + 1;
    entry.ref_count.set(new_count);
    log_info(
        "script_lifetime",
        format!("Added script with hash: `{hash}`, ref_count = {new_count}"),
    );
    hash
}

pub fn get_script(hash: &str) -> Option<Arc<BytesMut>> {
    CONTAINER
        .lock()
        .expect(LOCK_ERR)
        .get(hash)
        .map(|entry| entry.script.clone())
}

pub fn remove_script(hash: &str) {
    let mut container = CONTAINER.lock().expect(LOCK_ERR);
    if let Some(entry) = container.get(hash) {
        let new_count = entry.ref_count.get() - 1;
        entry.ref_count.set(new_count);

        if new_count == 0 {
            container.remove(hash);
            log_info(
                "script_lifetime",
                format!("Removed script with hash `{hash}` (ref_count reached 0)."),
            );
        } else {
            log_info(
                "script_lifetime",
                format!("Decremented ref_count for script `{hash}`: new ref_count = {new_count}."),
            );
        }
    } else {
        log_warn(
            "script_lifetime",
            format!("Attempted to remove non-existent script with hash `{hash}`."),
        );
    }
}

#[cfg(test)]
mod script_tests {
    use super::*;

    #[test]
    fn test_add_and_get_script() {
        let script = b"print('Hello, World!')";
        let hash = add_script(script);

        let retrieved = get_script(&hash);
        assert!(retrieved.is_some());
        assert_eq!(&retrieved.unwrap()[..], script);
    }

    #[test]
    fn test_reference_counting_and_removal() {
        let script_1 = b"print('ref count test')";
        let script_2 = b"print('ref count test')";
        let hash = add_script(script_1);
        let hash_2 = add_script(script_2); // Increase ref count to 2
        assert_eq!(hash, hash_2);

        // First removal should decrement but not remove
        remove_script(&hash);
        assert!(get_script(&hash).is_some());

        // Second removal should remove the script
        remove_script(&hash);
        assert!(get_script(&hash).is_none());
    }

    #[test]
    fn test_remove_non_existent_script() {
        let fake_hash = "nonexistenthash";
        remove_script(fake_hash); // Should not panic
    }

    #[test]
    fn test_same_content_produces_same_hash() {
        let script_a = b"return 1";
        let script_b = b"return 1";
        let hash_a = add_script(script_a);
        let hash_b = add_script(script_b);
        assert_eq!(hash_a, hash_b);
        // Cleanup
        remove_script(&hash_a);
        remove_script(&hash_b);
    }

    #[test]
    fn test_different_content_produces_different_hash() {
        let script_a = b"return 'unique_a_12345'";
        let script_b = b"return 'unique_b_67890'";
        let hash_a = add_script(script_a);
        let hash_b = add_script(script_b);
        assert_ne!(hash_a, hash_b);
        // Cleanup
        remove_script(&hash_a);
        remove_script(&hash_b);
    }

    #[test]
    fn test_get_non_existent_script_returns_none() {
        assert!(get_script("does_not_exist_at_all").is_none());
    }

    #[test]
    fn test_empty_script() {
        let script = b"";
        let hash = add_script(script);
        let retrieved = get_script(&hash);
        assert!(retrieved.is_some());
        assert_eq!(&retrieved.unwrap()[..], b"");
        remove_script(&hash);
    }

    #[test]
    fn test_large_script() {
        let script = vec![b'x'; 100_000];
        let hash = add_script(&script);
        let retrieved = get_script(&hash);
        assert!(retrieved.is_some());
        assert_eq!(retrieved.unwrap().len(), 100_000);
        remove_script(&hash);
    }

    #[test]
    fn test_binary_script_content() {
        let script: Vec<u8> = (0..=255).collect();
        let hash = add_script(&script);
        let retrieved = get_script(&hash);
        assert!(retrieved.is_some());
        assert_eq!(&retrieved.unwrap()[..], &script[..]);
        remove_script(&hash);
    }

    #[test]
    fn test_high_ref_count() {
        let script = b"return 'high_ref_count_test'";
        let hash = add_script(script);
        // Add 99 more refs (total 100)
        for _ in 0..99 {
            let h = add_script(script);
            assert_eq!(h, hash);
        }
        // Remove 99 refs - script should still exist
        for _ in 0..99 {
            remove_script(&hash);
            assert!(get_script(&hash).is_some());
        }
        // Remove last ref
        remove_script(&hash);
        assert!(get_script(&hash).is_none());
    }

    #[test]
    fn test_add_after_full_removal() {
        let script = b"return 'readd_test'";
        let hash = add_script(script);
        remove_script(&hash);
        assert!(get_script(&hash).is_none());

        // Re-add the same script
        let hash2 = add_script(script);
        assert_eq!(hash, hash2);
        assert!(get_script(&hash2).is_some());
        assert_eq!(&get_script(&hash2).unwrap()[..], script);
        remove_script(&hash2);
    }
}
