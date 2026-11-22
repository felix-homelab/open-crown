# 13 — Status

As of 2026-11-22 (private research build).

## Verified on the A52

The test device is a stock Samsung Galaxy A52s 5G, `SM-A528B` (`a52sxq`),
build `UP1A.231005.007` / `A528BXXSBGYI3`, kernel
`5.4.254-qgki-28575149-abA528BXXSBGYI3`, SDK 34, arm64-v8a.

| Area | Observed |
| --- | --- |
| M1 probe and readiness | App loads the native and JNA libraries, reports the real device, and lists no ready catalog payload |
| In-tree A52 chain | App auto-runs the page-cache patch on first launch per boot, reports 576/576 bytes verified, and shows Root reached once the init relay marker appears |
| Temporary root over ADB | `uid=0(root) gid=0(root) ... context=u:r:init:s0`; relay is not reachable from `untrusted_app` |
| Reboot | Relay and page-cache patch disappear; shell returns to `uid=2000(shell)` |
| Scope | No bootloader unlock, no data wipe, no persistent boot-image change |

The app cannot consume the relay itself: Android excludes `untrusted_app` from
that Unix socket. The operator triggers init and uses the relay over ADB; the app cannot write the property itself. The
only chain in the tree is `app/src/main/cpp/a52root/`.

## Verified on the host

| Area | Evidence |
| --- | --- |
| Docs and ADRs | `tools/link-check/cli.py .` passes |
| App chain build | `liba52root.so` and the 576-byte A52 payload are packaged; JVM tests pass |
| Payload index | `catalog/payload-index.json` is empty; no external target is indexed |
| Readiness and execution | Host tests cover readiness reasons, signed example bundles, and the consent-gated supervisor |
| Catalog trust | Ed25519 sign/verify, bundle verify, revocation builder, hash-chained transparency log |
| Device list | schema, validator, runtime parser, FFI, app rendering, A52 example |

## Blocked / out of scope

| Work | Needs |
| --- | --- |
| App-side root consumption | An SELinux-accessible broker; the app is `untrusted_app` |
| K2a / controller ownership | A payload that yields the SELinux domain needed for the Bluetooth controller |
| Protocol and activation | A Watch and a test account |
| Public release | Licence and signing decisions for the in-tree chain |

## Reproduce

```sh
tools/dev/bootstrap-toolchain.sh
tools/build-android-lib.sh
cargo fmt --all -- --check
cargo clippy --workspace --all-targets -- -D warnings
cargo test --workspace
python3 tools/link-check/cli.py .
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Device verification:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n org.opencrown.app/.MainActivity
# Tap "Run chain", then trigger init from another shell:
adb shell setprop debug.dirtyinit 42
adb logcat -d | grep -E 'OpenCrown|AndroidRuntime|FATAL|UnsatisfiedLinkError'
```

The resulting relay is an init-domain command interface, not a general-purpose
root shell. It is temporary and ends at reboot.

## Next

Decide whether the app should gain an SELinux-accessible broker for the relay,
or whether the chain stays an ADB-operated research feature. The A52 chain does
not satisfy K2a yet; controller ownership and the Watch protocol remain
unimplemented.
