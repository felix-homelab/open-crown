# 13 — Status

As of 2026-11-21. This page separates what exists and is verified from what is
implemented but unverified, and what is blocked on hardware or information.

## Implemented and verified on the host

| Area | Evidence |
| --- | --- |
| Docs and ADRs | 13 documents, 13 ADRs, link check clean |
| Capture tooling | btsnoop, pcap, pcapng parsers; fixture model; 59 Python tests |
| Spec tooling | spec-lint, vector-gen, fixture-diff |
| Catalog trust | Ed25519 sign/verify, bundle verify, revocation builder, hash-chained transparency log |
| Device list | schema, validator, runtime parser, FFI, app rendering |
| Runtime | manifest validation, canonical digest, signature, revocation, staging, token and boot id; cross-language test (Python signs, Rust verifies) |
| FFI | protocol revision, error type, `prepare_payload`, `inspect_bundle`, device queries; bindings diffed in CI |
| Agent | protobuf envelope v1, capability policy, broker dispatch, framed Unix socket with peer-UID check, audit log and file, runnable CLI with a stub controller |
| App | devices, bundle scanner, consent gate, prepare call; 14 JVM tests |
| CI | rust (fmt, clippy, test, bindings diff, cargo-deny), python (tests, bundle verify, device list, links), android (NDK probe, arm64 library, APK, unit tests, APK report) |

## Implemented but unverified on a device

- JNA loading `libjnidispatch.so` and `libopencrown.so` on Android.
- The K2a probe (`tools/hci-echo`): it compiles for arm64 but has never run.
- The agent socket under Android's SELinux policy and app UID.
- Compose rendering of the device, bundle and consent screens.

## Blocked

| Work | Needs |
| --- | --- |
| K2a / K2b | The phone and the first payload |
| Controller implementation | HCI/HAL behaviour on the target build |
| Protocol specs and vectors | Captures from a Watch |
| Activation decision (ADR 0003) | A Watch and a test account |
| Release signing | The offline key ceremony in ADR 0012 |

## Numbers

```text
rust tests    89
python tests  59
jvm tests     14
apk           25 MB, arm64-v8a, libopencrown.so + libjnidispatch.so
supply chain  advisories ok, bans ok, licenses ok, sources ok
docs          0 broken links
```

## Reproduce

```sh
tools/dev/bootstrap-toolchain.sh
tools/build-android-lib.sh          # needs ANDROID_NDK_HOME
cargo test --workspace
PYTHONPATH=tools/lib python3 -m unittest discover -s tools/tests
./gradlew :app:assembleDebug :app:testDebugUnitTest
python3 tools/link-check/cli.py .
python3 catalog/tooling/verify.py catalog/examples/signed
```

## Next

Phase 0: run the first payload on the stock phone, record stage A (uid 0) and
stage B (controller bind), fill `docs/evidence/phase0-k2a.md`.
