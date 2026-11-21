//! The quarantined external payload is integrated, signed and READY on its
//! exact target. This is data-on-disk verification, not a hardware run.

use std::fs;
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicUsize, Ordering};

use opencrown_runtime::index::PayloadIndex;
use opencrown_runtime::matcher::DeviceFingerprint;
use opencrown_runtime::readiness::{Reason, evaluate};
use opencrown_runtime::revocation::RevocationList;

static COUNTER: AtomicUsize = AtomicUsize::new(0);

fn temp(tag: &str) -> PathBuf {
    let mut path = std::env::temp_dir();
    path.push(format!(
        "opencrown-external-{tag}-{}-{}",
        std::process::id(),
        COUNTER.fetch_add(1, Ordering::SeqCst)
    ));
    path
}

fn copy_bundle(source: &Path, root: &Path, id: &str) {
    let destination = root.join(id);
    fs::create_dir_all(destination.join("files")).unwrap();
    for name in ["manifest.json", "manifest.sig", "files/libtimer"] {
        fs::copy(source.join(name), destination.join(name)).unwrap();
    }
}

#[test]
fn futex_timer_is_ready_on_its_exact_target() {
    let repo = Path::new(env!("CARGO_MANIFEST_DIR")).join("..");
    let bundle = repo.join("payloads/external/org.opencrown.payload.futex-timer");
    let index =
        PayloadIndex::parse(&fs::read(repo.join("catalog/payload-index.json")).unwrap()).unwrap();
    let key = opencrown_runtime::hex::decode(
        fs::read_to_string(bundle.join("public_key.hex"))
            .unwrap()
            .trim(),
    )
    .unwrap();

    let root = temp("ready");
    copy_bundle(&bundle, &root, "org.opencrown.payload.futex-timer");
    let device = DeviceFingerprint {
        model: "SM-S9280".into(),
        kernel_release: "6.1.145-android14-11-3254743-abS9280ZCS6DZJ3".into(),
        abi: "arm64-v8a".into(),
        android_sdk: 35,
        build_id: "UP1A.231005.007".into(),
    };
    let results = evaluate(
        &index,
        &root,
        &device,
        "0.1.0",
        &[key],
        &RevocationList::empty(),
    )
    .unwrap();
    let result = results
        .iter()
        .find(|result| result.id == "org.opencrown.payload.futex-timer")
        .unwrap();
    assert_eq!(result.reason, Reason::Ready, "{}", result.detail);
    assert_eq!(result.bundle_version.as_deref(), Some("1.0.0"));
    let _ = fs::remove_dir_all(root);
}

#[test]
fn futex_timer_does_not_match_the_a52() {
    let repo = Path::new(env!("CARGO_MANIFEST_DIR")).join("..");
    let index =
        PayloadIndex::parse(&fs::read(repo.join("catalog/payload-index.json")).unwrap()).unwrap();
    let device = DeviceFingerprint {
        model: "SM-A528B".into(),
        kernel_release: "5.4.254-qgki-28575149-abA528BXXSBGYI3".into(),
        abi: "arm64-v8a".into(),
        android_sdk: 34,
        build_id: "UP1A.231005.007".into(),
    };
    let result = index
        .payloads()
        .iter()
        .find(|payload| payload.id == "org.opencrown.payload.futex-timer")
        .unwrap();
    assert!(result.matching_target(&device).is_none());
}
