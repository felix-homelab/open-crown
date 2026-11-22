# Phase 0 baseline audit

Date: 2026-11-22
Auditor: lead (Lane C functions are not yet independently staffed)
Baseline commit: `148adf6` plus the Phase 0 working tree described below.

This is the honest starting point. It records what exists, what was observed,
and what remains unknown before any product claim is made.

## 1. Repository inventory

| Area | State at baseline | Consequence |
| --- | --- | --- |
| `app/` | Kotlin + Compose shell, one success screen, in-tree page-cache chain, payload manifest matcher | Replaced by the Phase 1 shell; the chain stays quarantined |
| `core/` | Rust crate with `discovery`, `faces`, `health`, `ids`, `notifications`, `setup`, `telephony`, `wifi` modules, most 5-line stubs | Protocol implementation is not started |
| `runtime/` | Payload manifest, matcher, staging, readiness, revocation, device list; host tests | Reusable foundation, host-tested only |
| `agent/` | Broker skeleton, IPC envelope, policy, audit; host tests | Not running on a device |
| `ffi/` | UniFFI bindings, 601 lines, generated bindings checked in | Not wired to the new app yet |
| `payloads/` | One in-tree A52 page-cache relay package | Quarantined; provenance review open |
| `docs/` | Charter, architecture, payload system, roadmap, ADRs, status, evidence | Still describes the old milestone; new clean-room docs added |
| `reference/` | Present locally, gitignored, no build integration | Quarantined reference for Lane A/C only |

No code under `app/`, `core/`, `agent/`, `ffi/`, or `runtime/` was copied
from the reference in this phase. The new Kotlin files were written from the
state-model and design requirements.

## 2. Physical hardware

| Item | Value | How verified |
| --- | --- | --- |
| Phone | Samsung SM-A528B (`a52sxq`) | `adb shell getprop ro.product.model` / `ro.product.device` |
| Android | 14 / SDK 34, build `UP1A.231005.007` / `A528BXXSBGYI3` | `getprop`, `dumpsys` |
| Kernel | `5.4.254-qgki-28575149-abA528BXXSBGYI3` | `uname -a` |
| SELinux | Enforcing | `getenforce` |
| Shell context | `u:r:shell:s0`, uid 2000 | `id` |
| Bootloader / root | Not unlocked; no Magisk; no persistent root | Existing evidence, re-checked state |
| Apple Watch | **Not present** | No watch is available to pair or observe |

The device serial is deliberately not recorded anywhere in the repository. The
screenshot evidence in `docs/evidence/` is app UI, not a secret.

**Finding P0-HW-1:** no Apple Watch is available on the bench. This blocks
physical verification of every watch-scoped feature. It does not block building
the app, the registry, or the transport code.

## 3. Privilege chain

Observed on boot 67 and again on boot 68:

- The app auto-runs the page-cache chain on first launch per boot and reports
  `576/576 bytes match` / `PAGE CACHE MODIFIED`.
- `@dfi_init` appears in `/proc/net/unix` as a listening abstract socket;
  `/dev/.dfi` exists but is not readable by `shell` (permission denied).
- The app shows "Root reached" when the marker exists. This means *the chain
  ran*, not *the app can use privilege*.

New observation from the Phase 2 broker investigation (2026-11-22):

- After the relay binds and listens it never returns to its init caller. It
  occupies an init thread in `accept()` indefinitely.
- Shortly after, `logcat` returns `Connection refused` (logd unavailable) and
  the phone watchdog-resets. On boot 67 the reset happened within roughly two
  minutes of the relay becoming active; the exact trigger (watchdog vs another
  init dependency) is UNKNOWN.
- The shell-domain `setprop ctl.start zzz` hangs once the relay is active,
  because the property thread is the hijacked init thread.

**Finding P0-REL-1:** the current relay is not a viable production broker base.
It destabilises the phone by consuming an init thread for the rest of the boot.
Any product design that relies on this relay staying resident is wrong. A
replacement must either return to init, spawn a separate process, or be limited
to a bounded command exchange.

## 4. Broker and transport

- The app cannot consume the init-domain relay socket. This is now measured
  from the app's **real** domain, not an approximation: the process runs as
  `u:r:untrusted_app:s0:c68,c257,c512,c768`, the relay socket is live, and the
  app's connect is denied with EACCES/EPERM. The shell domain, by contrast,
  connects and gets uid 0. See
  [a52-broker-untrusted-app.md](../../evidence/a52-broker-untrusted-app.md).
- The app does not yet start, authenticate, or talk to an agent.
- No HCI user channel bind has been attempted under the new build.
- K2a (uid 0 + controller-capable domain) remains unproven.
- The relay is an unauthenticated raw-syscall RPC and never returns to init;
  it kills the property/log thread and the phone watchdog-resets.

**Finding P0-BRK-1 (closed as blocked):** there is no app-domain path to the
relay. The app cannot ship a manual-ADB trigger, and the relay is not a viable
broker base even if a policy change allowed the connect. K2a is recorded as a
blocker, not deferred.

## 5. Reference and licences

- Reference materialised from the existing local copy; no network fetch.
- No top-level licence file in the reference. Its only NOTICE covers AOSP
  Apache-2.0 AIDL declarations, not the rest of the tree.
- The reference contains Apple-origin watch-face containers, pigment swatches,
  optical-pairing imagery, app icons, and Flutter/iOS artwork. None is copied.
- The reference's own README admits incomplete parity; see
  [inventory/reference-features.md](../inventory/reference-features.md).

**Finding P0-PROV-1:** the in-tree page-cache chain has no copyright or licence
statement and its header describes a named "DFI v3" chain. It predates the
clean-room process. It is quarantined payload code; its provenance is open and
must be closed before a public release.

## 6. Feature registry baseline

Generated by `tools/feature-registry/build.py`:

| Status | Count |
| --- | ---: |
| verified | 0 |
| implemented_unverified | 8 |
| blocked | 29 |
| out_of_scope | 0 |
| unimplemented | 174 |
| **total** | **211** |

No row is verified. The eight implemented rows are host-tested foundations
(payload verification, readiness, matching, IPC, audit) plus the observation
model and registry themselves. The 29 blocked rows name an Apple-service or
architecture blocker in [BLOCKERS.md](../BLOCKERS.md); they are not hiding
unimplemented work.

## 7. Design and UI

- The old single-screen UI is replaced by an observation-first shell.
- The design system is original: no Apple font, logo, SF Symbol, watch-face
  artwork, or screenshot is included.
- The digital-crown mark is retained as the brand mark.
- Dynamic Type, dark mode, reduced motion, and content descriptions are
  requirements, not polish.

## 8. Checks run at baseline

```sh
python3 tools/feature-registry/build.py --check   # 211 rows, schema valid
JAVA_HOME=.toolchain/jdk/Contents/Home ./gradlew :app:testDebugUnitTest
cargo test --workspace                             # baseline has existing tests
python3 tools/link-check/cli.py .
```

The Gradle build requires the bundled JDK 21 and the in-repo Gradle home; the
CI workflow already uses Temurin 21, so this is a local-only invocation detail.

## 9. Open findings

| Id | Finding | Owner | Gate |
| --- | --- | --- | --- |
| P0-HW-1 | No physical Watch for verification | product owner | Phase 3 physical gate |
| P0-REL-1 | Relay hijacks init and destabilises the phone | Phase 2 | before any product use |
| P0-BRK-1 | No app-to-agent broker; manual ADB only | Phase 2 | Gate G0/K2a |
| P0-PROV-1 | Payload chain provenance unreviewed | Lane C | before release |
| P0-TR-1 | No HCI/controller ownership proven | Phase 2 | Gate G0/K2a |
| P0-PROT-1 | No protocol implementation beyond stubs | Phase 3 | pairing gate |

## 10. What this baseline does not claim

It does not claim that pairing is close, that the relay is stable, that the
reference's feature list is achievable, or that any Apple-service row can be
enabled later. It claims only what the table above records.
