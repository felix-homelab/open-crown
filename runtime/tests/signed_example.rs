//! Cross-language check: a bundle signed by catalog/tooling/sign.py must
//! verify and stage in the Rust runtime.

use std::path::{Path, PathBuf};

use opencrown_runtime::matcher::DeviceFingerprint;
use opencrown_runtime::{hex, manifest, matcher, stage};

fn bundle() -> PathBuf {
    Path::new(env!("CARGO_MANIFEST_DIR")).join("../catalog/examples/signed")
}

fn device() -> DeviceFingerprint {
    DeviceFingerprint {
        model: "EXAMPLE-1".into(),
        kernel_release: "example-6.1.0".into(),
        abi: "arm64-v8a".into(),
        android_sdk: 36,
        build_id: "EXAMPLE".into(),
    }
}

#[test]
fn python_signature_verifies_and_stages_in_rust() {
    let bundle = bundle();
    let bytes = std::fs::read(bundle.join("manifest.json")).unwrap();
    let manifest = manifest::Manifest::parse(&bytes).unwrap();

    let signature = std::fs::read(bundle.join("manifest.sig")).unwrap();
    let public_hex = std::fs::read_to_string(bundle.join("public_key.hex")).unwrap();
    let public = hex::decode(public_hex.trim()).expect("public key is hex");
    manifest::verify_signature(&manifest, &signature, &public).unwrap();

    assert!(matcher::select(&manifest, &device()).is_ok());

    let work = std::env::temp_dir().join(format!("opencrown-signed-{}", std::process::id()));
    let staged = stage::stage(&bundle, &manifest, &work).unwrap();
    assert_eq!(
        std::fs::read(&staged.entry).unwrap(),
        b"opencrown example payload\n"
    );
    assert_eq!(
        std::fs::read(&staged.agent).unwrap(),
        b"#!/bin/sh\nexit 0\n"
    );
    let _ = std::fs::remove_dir_all(&work);
}

#[test]
fn tampering_breaks_verification() {
    let bundle = bundle();
    let bytes = std::fs::read(bundle.join("manifest.json")).unwrap();
    let mut value: serde_json::Value = serde_json::from_slice(&bytes).unwrap();
    value["version"] = serde_json::Value::String("9.9.9".into());
    let tampered = serde_json::to_vec(&value).unwrap();
    let manifest = manifest::Manifest::parse(&tampered).unwrap();

    let signature = std::fs::read(bundle.join("manifest.sig")).unwrap();
    let public_hex = std::fs::read_to_string(bundle.join("public_key.hex")).unwrap();
    let public = hex::decode(public_hex.trim()).unwrap();
    assert!(manifest::verify_signature(&manifest, &signature, &public).is_err());
}
