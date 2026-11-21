# 13 — Status

As of 2026-11-21 (M1). This page separates what a device has verified, what the
host has verified, and what remains untested. **A52-verified is not
payload-verified.**

## Verified on the A52

The M1 test device is a stock Samsung Galaxy A52s 5G, `SM-A528B` (`a52sxq`,
serial `R5CR916B48N`), build `UP1A.231005.007` / `A528BXXSBGYI3`, kernel
`5.4.254-qgki-28575149-abA528BXXSBGYI3`, SDK 34, arm64-v8a. Full note and
hashes: [evidence/a52-m1.md](evidence/a52-m1.md).

| Area | Observed |
| --- | --- |
| JNA + native loading | `libjnidispatch.so` and `libopencrown.so` loaded in the app process; no `UnsatisfiedLinkError` |
| Probe FFI | Real model, kernel release, ABI, SDK, build id and fingerprint shown |
| Supported-list status | `example`, never `supported` |
| Readiness FFI | 3 indexed payloads, 0 ready, all `no_match` with `bundle=none` |
| App stability | No `FATAL EXCEPTION`; process alive; [screenshot](evidence/a52-probe.png) |
| Scope | No payload installed/run, no root, no controller, no Watch |

The A52 is an `example` row in `catalog/supported-devices.json` with a note that
no payload exists. No index entry targets `SM-A528B`, so the truthful reason is
`no_match`. The `no_bundle` path is host-tested, not faked on the device by
inventing an A52 payload target.

## Verified on the host

| Area | Evidence |
| --- | --- |
| Docs and ADRs | 14 documents, 14 ADRs, 0 broken links |
| Payload index | `catalog/payload-index.json`, schema, Python validator, 70 published targets across 3 sources; null `kernel_build` never matches |
| Readiness | `runtime::readiness`; every reason covered, including a Python-signed bundle verified by Rust and a revoked one |
| Capture tooling | btsnoop, pcap, pcapng parsers; fixture model |
| Spec tooling | spec-lint, vector-gen, fixture-diff |
| Catalog trust | Ed25519 sign/verify, bundle verify, revocation builder, hash-chained transparency log |
| Device list | schema, validator, runtime parser, FFI, app rendering, A52 example |
| Runtime | manifest validation, canonical digest, signature, revocation, staging, token and boot id |
| FFI | protocol revision, error type, prepare, inspect, probe, readiness, device queries; bindings regenerated and diffed in CI |
| Agent | protobuf envelope v1, capability policy, broker dispatch, framed Unix socket with peer-UID check, audit log |
| App | probe screen, payload list, devices, bundle scanner, consent gate; 25 JVM tests |
| CI | rust (fmt, clippy, test, bindings diff, cargo-deny), python (tests, payload index, bundle verify, device list, links), android (NDK, arm64 lib, APK, unit tests, report) |

## Implemented but not verified on a device

- The agent socket under Android's SELinux policy and app UID.
- The K2a probe (`tools/hci-echo`): it compiles for arm64 but has never run.
- Any payload, exploit chain, controller path, or root acquisition.
- Consent-gated prepare on a real signed bundle: the A52 is `example`, so the
  gate correctly blocks; only the example bundle is exercised on the host.

## Blocked / out of M1

| Work | Needs |
| --- | --- |
| K2a / K2b | A payload for this build; the A52 has none |
| Controller implementation | HCI/HAL behaviour on a target build |
| Protocol specs and vectors | Captures from a Watch |
| Activation decision (ADR 0003) | A Watch and a test account |
| Release signing | The offline key ceremony in ADR 0012; M1 pins the throwaway example key |
| Payload verification | A built, signed, exact-target bundle; never inferred from A52 app testing |

## Numbers

```text
rust tests    112
python tests  69
jvm tests     25
apk           26 MB, arm64-v8a, libopencrown.so + libjnidispatch.so
supply chain  advisories ok, bans ok, licenses ok, sources ok
docs          0 broken links
```

## Reproduce

```sh
tools/dev/bootstrap-toolchain.sh
tools/build-android-lib.sh          # needs ANDROID_NDK_HOME
cargo fmt --all -- --check
cargo clippy --workspace --all-targets -- -D warnings
cargo test --workspace
python3 -m venv /tmp/oc-venv && /tmp/oc-venv/bin/pip install -r tools/requirements.txt
PYTHONPATH=tools/lib /tmp/oc-venv/bin/python -m unittest discover -s tools/tests
python3 catalog/tooling/payload_index.py validate --index catalog/payload-index.json
python3 catalog/tooling/devices.py validate --list catalog/supported-devices.json
python3 catalog/tooling/verify.py catalog/examples/signed
python3 tools/link-check/cli.py .
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Device verification:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n org.opencrown.app/.MainActivity
adb logcat -d | grep -E 'OpenCrown|AndroidRuntime|FATAL|UnsatisfiedLinkError'
adb exec-out screencap -p > docs/evidence/a52-probe.png
```

## Next

Phase 0 remains: run the first payload on a stock phone, record stage A (uid 0)
and stage B (controller bind), and fill `docs/evidence/phase0-k2a.md`. M1 did
not start that work and must not be read as evidence for it.
