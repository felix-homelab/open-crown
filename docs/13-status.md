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
| Readiness FFI | 4 indexed payloads, 0 ready, all `no_match` with `bundle=none` |
| CVE-2026-20971 UAF/DoS | Public trigger from shell panicked the A52 (`boot_id` changed, `ro.boot.bootreason=kernel_panic`); no root. See [evidence/a52-five-uaf-dos.md](evidence/a52-five-uaf-dos.md). |
| App stability | No `FATAL EXCEPTION`; process alive; [screenshot](evidence/a52-probe.png) |
| Scope | No payload installed/run, no root, no controller, no Watch |

The A52 is an `example` row in `catalog/supported-devices.json` with a note that
no payload exists. No index entry targets `SM-A528B`, so the truthful reason is
`no_match`. The `no_bundle` path is host-tested, not faked on the device by
inventing an A52 payload target.

## Verified on the host

| Area | Evidence |
| --- | --- |
| Docs and ADRs | 14 documents, 16 ADRs, 0 broken links |
| Payload index | `catalog/payload-index.json`, schema, Python validator, 71 published targets across 3 sources plus the external `futex-timer` entry; null `kernel_build` never matches |
| Readiness | `runtime::readiness`; every reason covered, including a Python-signed bundle verified by Rust and a revoked one |
| External payload | `payloads/external/org.opencrown.payload.futex-timer` verifies, is indexed, and is READY on its exact `SM-S9280` DZJ3 target by data-on-disk rules; not run |
| Execution supervisor | `runtime::execution` consent, staged-hash, token/boot-id, timeout and three-attempt budget tests; host-only |
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
- Any payload, exploit chain, controller path, or root acquisition. The
  external `futex-timer` payload has not been run by Open Crown.
- The external payload's Shizuku/shell runner: `libtimer` is started from a
  shell/UserService context upstream; the app is `untrusted_app` and has no
  such bridge yet.
- Consent-gated prepare on a real signed bundle: the A52 is `example`, so the
  gate correctly blocks; only the example bundle and the host-only external
  bundle are exercised on the host.

## Blocked / out of M1

| Work | Needs |
| --- | --- |
| K2a / K2b | A payload for this build; the A52 has none |
| Controller implementation | HCI/HAL behaviour on a target build |
| Protocol specs and vectors | Captures from a Watch |
| Activation decision (ADR 0003) | A Watch and a test account |
| Release signing | The offline key ceremony in ADR 0012; M1 pins the throwaway example key |
| A52 payload | No public per-boot payload for `5.4.254-qgki` / Adreno 642L. DirtyFrag needs 6.5+, GhostLock/SamTimer target 5.10+, CVE-2026-20971 is DoS-only, CVE-2025-21479 is A7xx-only. The CVE-2026-21385 probe ran on the A52 and was negative (no wrap). See [research/a52-payload-search.md](research/a52-payload-search.md) and [evidence/a52-kgsl-probe.md](evidence/a52-kgsl-probe.md). |
| A52 downgrade | Bootloader binary B (11) anti-rollback blocks the pre-2024 builds; the flashable B builds are post-fix. See [research/a52-payload-search.md](research/a52-payload-search.md#can-the-a52-be-downgraded-to-a-vulnerable-build). |
| A52 original chain | Research branch started; the public CVE-2026-20971 harness was confirmed as a stock-kernel DoS (panic/reboot). A controlled primitive needs a `panic_on_oops=0`/KASAN lab kernel, which the locked stock A52 cannot run. See [../research/a52-chain/README.md](../research/a52-chain/README.md). |
| Payload hardware verification | A target device (the A52 is not `SM-S9280` DZJ3) and a privileged runner |
| Public distribution of `payloads/external/` | Licence resolution; the tree is private-use only (ADR 0015) |

## Numbers

```text
rust tests    120
python tests  73
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
python3 catalog/tooling/verify.py payloads/external/org.opencrown.payload.futex-timer
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

The external payload is extracted and the execution contract exists, but two
things are still missing before it can run from the app: a privileged runner
(Shizuku or the payload bootstrap) and a target device. The A52 cannot stand in
for `SM-S9280` DZJ3, and the 2026-11-21 search found no public per-boot chain
for its 5.4.254 kernel (see
[research/a52-payload-search.md](research/a52-payload-search.md)). The next
decision is therefore a port/research effort for the A52, a relaxation of the
no-unlock constraint, or a different payload device. Public release still needs
the licence decision in ADR 0015.
