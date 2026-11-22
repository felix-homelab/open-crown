# Open Crown

**An Android companion for Apple Watch, built on a per-boot privilege model.**

Open Crown lets an Android phone discover, pair with, activate, and operate an
Apple Watch without an iPhone in the loop. The bootloader stays locked and the
system partition stays untouched: when the app needs the Bluetooth controller it
borrows root for the life of the boot, and a reboot takes it back.

> **Status: private research, Phase 1 shell.** The app now has an
> observation-first Apple-style Compose shell, a 266-row capability registry and
> real device diagnostics. The Watch protocol and pairing path are not
> implemented. The A52s page-cache chain is kept but only runs from an explicit
> Diagnostics consent sheet; it produces a temporary init-domain relay that the
> app itself cannot connect to. **K2a is measured blocked on the A52**: the real
> `untrusted_app` domain is refused with EACCES while the shell domain connects.
> See [the evidence](docs/evidence/a52-broker-untrusted-app.md) and
> [13 — Status](docs/13-status.md).

> **What it needs today.** A phone you can prepare: already rooted, or on a build
> with a usable n-day. It is not a universal companion. The supported list is
> short, evidence-backed, and may lose entries when a vendor patches. See
> [04 — Payload system](docs/04-payload-system.md).

---

## The one-paragraph version

The Watch speaks a proprietary, encrypted protocol over Classic Bluetooth, and
Android's public APIs cannot open the channels it needs. Owning those channels
means owning the Bluetooth controller, and on a modern enforcing device owning
the controller means more than `uid 0`: it needs an SELinux domain and a
platform configuration that stock Android will not hand an ordinary app. Open
Crown therefore has three parts: a **protocol companion** (Kotlin UI + Rust
core), a **per-boot payload runtime** that acquires privilege through signed,
device-matched n-day chains, and a **resident agent** that holds the radio while
a watch is paired. The Watch itself is never attacked.

## The one claim the project rests on

Everything else here is inherited work. The new claim is:

> **K2a — a per-boot payload can take the Bluetooth controller under enforcing
> SELinux, without Magisk's priv-app, hidden-API whitelist, or a persistent
> module.**

It is **not proven**, and the current A52 chain does not satisfy it. The chain
yields init-domain uid 0, but the app's real `untrusted_app` domain is denied a
socket connection, and the relay is an unauthenticated raw-syscall RPC that
consumes an init thread until the phone watchdog-resets. Until a different
privilege path passes, every "stock phone" sentence in these documents is a
hypothesis. Evidence:
[broker-probe-a52](docs/evidence/broker-probe-a52.md),
[a52-broker-untrusted-app](docs/evidence/a52-broker-untrusted-app.md).

## What Open Crown is

- A native Android app (Kotlin + Jetpack Compose) with a Rust protocol core.
- **Borrowed privilege, one boot at a time:** the in-tree chain is consent-gated
  and quarantined; a resident agent does not exist yet. Nothing survives the
  reboot.
- A **resident agent** behind a visible foreground service for as long as a
  watch is paired. It is a root daemon while the phone is up; we say so.
- A signed catalog of device-matched payloads and the agent they bootstrap.
- A clean-room protocol implementation with published specs and vectors.
- A licence that lets people audit and rebuild it: MIT.

## What Open Crown is not

- **Not a watch jailbreak.** No code runs on the Watch and no firmware is touched.
- **Not an Activation Lock bypass.** Locked devices are out of scope and stay out.
- **Not an iPhone replacement.** No App Store, no Apple Pay, no cellular
  provisioning, no watchOS updates.
- **Not universal.** One model and one watchOS version at a time, with evidence.
- **Not a silent root.** The agent is visible, the payload is signed, and a
  reboot is an unconditional recovery path.
- **Not a general-purpose rooting tool** for arbitrary phones — but the payload
  artifact is extractable from the APK, and [09](docs/09-legal-ethics.md) says so
  instead of pretending otherwise.

## Why this is hard

| Layer | Reality |
| --- | --- |
| Bluetooth | The Watch uses Classic L2CAP/ERTM, which Android does not expose to apps. You own the controller or you do nothing. |
| Privilege | `uid 0` alone is not enough on Android 12+. The only working Android build of this stack ships a priv-app, a hidden-API whitelist and SELinux rules. |
| Protocol | Already mapped by a public prior project; the work is verification and reimplementation, not discovery. |
| Activation | Device activation is demonstrated by that project; whether an Apple ID session can live on the phone is undecided. |
| Supply | Supported devices are the ones that stopped receiving patches. That trade-off is in the README on purpose. |

## Scope for v1.0

| Tier | Feature | Status |
| --- | --- | --- |
| T0 | Pair, activate, visible face, reconnect | Gated by K2a and the activation decision |
| T1 | Notifications, faces, settings | After T0 |
| T2 | Health ingress, Wi-Fi relay | After T1 |
| T3 | Calls / SMS relay | Best effort, may never ship |

The ship order is in [ADR 0010](docs/adr/0010-ship-order.md): research first, a
rooted-device companion next, the catalog when the supply and review gates pass.

## Prior art and provenance

The protocol layer was reconstructed in public by
[apple_watch_connector](https://github.com/LordixDemon/apple_watch_connector)
(Java, Flutter, Rust; Android via Magisk, Linux and Windows via native HCI).
Open Crown is an independent reimplementation: ideas and observations are used,
code is not. The relationship, the licence position, and what "clean room" means
here are in [03 — Provenance](docs/03-clean-room.md) and
[docs/provenance.md](docs/provenance.md).

Naming it is not a courtesy. A design document that benchmarks against an
unnamed project cannot be audited.

## Docs

Read in this order:

1. [Charter](docs/00-charter.md) — why the project exists, what "done" means.
2. [Feasibility](docs/01-feasibility.md) — what the evidence proves, and what it does not.
3. [Architecture](docs/02-architecture.md) — components, boundaries, the two transport shapes.
4. [Provenance](docs/03-clean-room.md) — prior art, clean room, licences.
5. [Payload system](docs/04-payload-system.md) — privilege, supply, resident agent.
6. [Stack](docs/05-stack.md) — language and framework decisions, including why a rewrite.
7. [Roadmap](docs/06-roadmap.md) — K2a first, gates, kill criteria.
8. [Protocol research](docs/07-protocol-research.md) — what is already mapped, what we verify.
9. [Testing](docs/08-testing.md) — evidence, hardware matrix, privilege gates.
10. [Legal and ethics](docs/09-legal-ethics.md) — accepted risk, stated plainly.
11. [Risks](docs/10-risks.md) — what can kill the project.
12. [Glossary](docs/11-glossary.md) — terms and the real protocol vocabulary.
13. [Decisions](docs/12-decisions.md) — the decision log this revision came from.
14. [Status](docs/13-status.md) — what exists, what is verified, what is blocked.

Decisions are recorded as ADRs in [`docs/adr/`](docs/adr/).

---

## Building

The toolchain lives inside the repository so the build is self-contained and
reproducible:

    tools/dev/bootstrap-toolchain.sh     # JDK 21, Gradle 8.14.3, SDK 36, Rust
    ./gradlew :app:assembleDebug :app:testDebugUnitTest
    cargo test --workspace               # Rust core
    PYTHONPATH=tools/lib python3 -m unittest discover -s tools/tests
    tools/ffi-generate.sh                # regenerate Kotlin bindings

The debug APK is verified: package org.opencrown.app, minSdk 33, targetSdk 36,
signed with the debug certificate. It contains a skeleton screen and nothing
else; there is no protocol logic and no payload.

## Repository layout (skeleton)

```text
open-crown/
├── app/                  Android application (Kotlin, Compose, foreground service)
├── core/                 Rust protocol core (no OS I/O)
├── ffi/                  UniFFI bindings and generated Kotlin
├── agent/                Resident privileged helper (controller, policy, audit)
├── runtime/              Payload runtime and payload loader (native)
├── catalog/              Payload schema, manifests, signing tooling
├── protocol/             Specs and golden vectors
├── docs/                 This documentation set
└── tools/                Capture analysis, profile builders, release tooling
```

## Licence

MIT — see [LICENSE](LICENSE). Third-party components keep their own licences;
the release SBOM records them.