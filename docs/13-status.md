# 13 — Status

As of 2026-11-22 (private research build).

This revision replaces the single-screen probe shell with an observation-first
companion shell. It does **not** claim that pairing works. The trusted status
is the [feature registry](cleanroom/FEATURE_REGISTRY.md): 226 rows, 3 verified,
24 built-but-unverified, 32 blocked, 167 not built. The three verified rows are
narrow and do not imply pairing: unprivileged Apple-advertisement observation,
the Kotlin↔Rust UniFFI seam, and local notification ingress. No watch-scoped
capability is verified.

## What is now real

| Area | State | Evidence |
| --- | --- | --- |
| Compose design system | Light/dark tokens, type ramp, grouped lists, controls, sheets, original line icons, reduced motion, haptics | `app/src/main/kotlin/org/opencrown/app/ui/`; [light and 1.3x text evidence](evidence/a52-design-system.md) |
| App shell | Welcome, Home, Discovery, Pairing, Setup, Faces, Notifications, Health, Apps, Settings, Accessibility, Diagnostics, Evidence, About | `CrownApp.kt`, `ui/screens/` |
| Observation model | Every watch value is `Observation<T>` with origin, verification, time, pair/epoch and unavailable reason | `model/Observation.kt`, unit tests |
| Rust core seam | Generated UniFFI binding, arm64 library packaged with JNA; Diagnostics renders protocol revision and core rules from Rust | [evidence](evidence/a52-uniffi-core.md) |
| Notification ingress | Android listener captures sanitised, memory-only notification content; no delivery exists | [evidence](evidence/a52-notification-ingress.md) |
| Notification claims | Revision-bound actions/replies, tombstones and no-retry outcomes in the Rust core | `core/src/notifications.rs`, host tests |
| Write confirmation | ACK is not applied; only a matching epoch/target/value observation is | `core/src/write.rs`, host tests |
| Pairing rules | PIN/optical exclusivity, bounded PIN attempts, downgrade/MAC refusal, bond ordering, replacement safety | `core/src/pairing.rs`, host tests |
| Recovery drill | App reinstall and reboot tested; onboarding reset and core load verified | [evidence](evidence/a52-recovery-reinstall.md) |
| Health mirror | Bounded transactional mirror with tombstones and no zero substitution | `core/src/health.rs`, host tests |
| Wi-Fi guards | One archive per setup generation, expiry/retry bounds, ACK is delivery not join | `core/src/wifi.rs`, host tests |
| Preferences and pigments | Unknown-never-false, baseline-bound read-back, complete pigment sets | `core/src/settings.rs`, host tests |
| Minified release | R8 build keeps UniFFI/JNA; installed debug-signed on the A52 and core loads | [evidence](evidence/a52-release-build.md) |
| Secret scan | Tracked files scanned for private keys, provider tokens and signing material; CI enforced | tools/cleanroom/secret-scan.py |
| Feature registry | 226 rows generated from one source, with blockers and evidence | `tools/feature-registry/build.py`, `docs/cleanroom/feature-registry.json` |
| BLE discovery | Real unprivileged BLE scan for Apple manufacturer advertisements, RSSI and seen count; advertisements are not treated as watches. Physically exercised on the A52; the five observed advertisements were all refused as selectable | `data/BleDiscoveryRepository.kt`; [evidence](evidence/a52-discovery.md) |
| Diagnostics | Real device facts, app SELinux context, Bluetooth permission state, payload profile, relay marker, broker reachability, pair/link state | `data/DeviceProbe.kt`, Diagnostics screen |
| Sealed pair store | AES-GCM through Android Keystore, corrupt envelope treated as absent | `data/PairRecordStore.kt` |
| Discovery rules | Bounded inventory, opaque tokens, TTL, stale-token rejection, no metadata means no selection. FE25 decode remains UNKNOWN. Rust owns the inventory; the Android scanner renders it | `core/src/discovery/mod.rs`; [wiring evidence](evidence/a52-rust-discovery.md) |
| IDS rules | Route catalogue with class checks, epoch-scoped correlation, receipt versus effect, 15/30/60 s reconnect backoff | `core/src/ids/mod.rs` |
| Provisioning rules | Ordered checkpoints, locale hard stop, activation/sync gating, strict versus owner-confirmed completion | `core/src/setup/mod.rs` |
| Face delta rules | Complete baseline, operation validation, staged transaction, read-back-only APPLIED, timeout/disconnect UNKNOWN | `core/src/faces/mod.rs` |
| Consent-gated chain | The in-tree page-cache chain is kept, but only runs from an explicit Diagnostics consent sheet; it is no longer an automatic boot side effect | `data/ChainController.kt` |
| Clean-room governance | Lanes, quarantine, blocker catalog, inventory, specs, boundary guard | `docs/cleanroom/`, `tools/cleanroom/check-boundary.py` |

## Broker / K2a: measured blocker

The Phase 2 probe produced a definitive result on the A52:

| Test | Domain | Result |
| --- | --- | --- |
| Connect to `@dfi_init` | `u:r:shell:s0`, uid 2000 | **OK**; relay executes `getuid` and returns 0 |
| Read `/proc/1/attr/current` through relay | init | `u:r:init:s0` |
| Connect to `@dfi_init` | `u:r:runas_app:s0` (app-UID approximation) | **EACCES (errno 13)**, three times |
| Same socket absent | shell | `ECONNREFUSED`, so denial and absence are distinguishable |

The socket's peer label was measured with `SO_PEERSEC`: `u:r:init:s0`. The app
runs as `u:r:untrusted_app:s0:c68,c257,c512,c768`; its own Diagnostics probe
against the live socket returns **EACCES/EPERM**, while the shell probe returns
uid 0. The denial is an LSM `connectto` refusal, not a filesystem permission.
The screenshot and UI dump are in
[docs/evidence/a52-broker-untrusted-app.md](evidence/a52-broker-untrusted-app.md).

A file-queue broker was also tested: the relay's openat on a 0644 file owned
by the app returned EACCES, so app-data files are not a channel either. See
[a52-file-broker-probe.md](evidence/a52-file-broker-probe.md).

Two further findings make the current relay unusable as a production base:

1. It is an **unauthenticated raw-syscall RPC**. After two guards it forks a
   child per connection, executes `svc #0` with six caller-controlled arguments
   and writes the result back. Anyone allowed to connect owns arbitrary syscalls
   as uid 0 in init.
2. It **never returns to init**. After bind/listen it loops in `accept()`,
   permanently consuming the init thread that logged. The property service and
   logd then stop responding, and the phone watchdog-resets within minutes.

There is also no unprivileged trigger: `setprop ctl.start zzz` and
`debug.dirtyinit` are denied to `untrusted_app`. The chain activates on the next
init `LOG()` after the patch, which is why launching the old app appeared to arm
it without ADB.

**K2a is blocked.** The current chain cannot become the shipped broker. See
`docs/evidence/broker-probe-a52.md` and
[BLOCKERS.md](cleanroom/BLOCKERS.md#b-app-agent-broker).

## What is still not built

- No transport implementation beyond unprivileged BLE scanning: no ACL, L2CAP,
  SMP, uIKE, NetworkRelay, IDS or NWSC.
- No HCI/controller ownership, no resident agent on device.
- No pairing, activation, setup, face inventory, notification session, health
  session or watch app management.
- No protocol frames or vectors beyond the Lane A specs, which still mark many
  fields UNKNOWN.

## Blocked rows (32)

The registry names each one. The major families:

- **Apple account / iCloud**: Apple ID, iCloud sync, Find My network, iMessage,
  FaceTime, Siri, Walkie-Talkie, continuity unlock, 2FA, Health iCloud.
- **Secure element / payments**: Apple Pay, Wallet, transit, secure-element
  provisioning.
- **Apple-controlled supply**: Activation Lock, cellular provisioning, App Store
  purchases and DRM, watchOS updates, third-party watch app signing.
- **Platform**: HCI ownership (K2a) and the app-to-agent broker.
- **Assets and evidence**: Apple-rendered face assets, optical-pairing firmware,
  the reference evidence gap, completion policy.

## Hardware

- Phone: SM-A528B (`a52sxq`), Android 14 / SDK 34, kernel
  `5.4.254-qgki-2517...`, SELinux enforcing. The exact kernel string and the
  device serial are kept out of this document.
- Apple Watch: **not present**. No row can be physically verified yet.

## Checks

```sh
# Clean-checkout verification on 2026-11-22: with reference/ moved aside,
# Gradle assemble/unit tests and the Cargo workspace tests passed.
python3 tools/feature-registry/build.py --check
python3 tools/cleanroom/check-boundary.py
JAVA_HOME=.toolchain/jdk/Contents/Home GRADLE_USER_HOME=.gradle-home \
  ./gradlew :app:testDebugUnitTest :app:assembleDebug
cargo test --workspace
python3 tools/link-check/cli.py .
```

## Next

1. Install and exercise the new shell on the phone; record the real
   `untrusted_app` broker result in Diagnostics.
2. Decide whether K2a is a hard stop (rooted-device prerequisite) or whether a
   different privilege path is worth a time-boxed experiment.
3. Acquire a physical Watch or record that the physical gate cannot be met.
4. Begin Lane B protocol implementation only from the Lane A specs, starting
   with discovery decode and framing tests.