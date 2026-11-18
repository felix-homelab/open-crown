# Open Crown

**A clean-room Android companion for Apple Watch, with a per-boot privilege layer.**

Open Crown lets an Android phone discover, pair with, activate, and operate an
Apple Watch without an iPhone in the loop. The phone stays stock: no bootloader
unlock, no permanent root, no custom boot image. When the app needs the radio,
it runs a signed, device-matched **exploit payload** that borrows root for one
session and gives it back at reboot.

> **Status: planning.** This repository contains design documents only. There is
> no application code, no payload, and no working pairing path.

---

## The one-paragraph version

An Apple Watch speaks a proprietary, encrypted protocol family to its iPhone.
Android's public Bluetooth APIs cannot open the channels that protocol needs,
so the phone has to own the Bluetooth controller at the HCI level. That needs
elevated privileges, which stock Android will not grant. Open Crown therefore
has three parts: a **protocol companion** (Kotlin UI + Rust core), a **per-boot
exploit runtime** that acquires root through signed, device-specific payloads,
and a **privileged agent** that holds the radio for the session. The Watch
itself is never attacked.

---

## What Open Crown is

- A native Android app (Kotlin + Jetpack Compose) with a Rust protocol core.
- A **zero-install privilege model**: the phone remains unrooted between
  sessions; root exists only while the app is actively using it.
- A signed catalog of device-matched exploit payloads (page-cache, futex,
  and future families) plus the agent binary they bootstrap.
- In-app Apple ID sign-in for activation, with tokens only — the password is
  never stored.
- A clean-room implementation: new code and assets, with prior art used for
  ideas and principles only.

## What Open Crown is not

- **Not a watch jailbreak.** No public watchOS jailbreak exists and this project
  does not pretend to have one. No code runs on the Watch.
- **Not an activation-lock bypass.** Open Crown only works with a Watch the
  user already owns and can activate with their own Apple ID. Bypassing
  Activation Lock, iCloud Lock, or a previous owner's pairing is out of scope
  and will not be implemented.
- **Not a stolen-device tool.** Ownership attestation is a design requirement,
  not a disclaimer.
- **Not a permanent root.** There is no persistent su, no Magisk module in the
  critical path, and no boot-image modification.
- **Not an iPhone replacement for day one.** Pairing, activation, and reconnect
  are staged goals with hard gates.

---

## Why this is hard (read this first)

| Layer | Reality |
| --- | --- |
| Bluetooth | The Watch uses Classic L2CAP/ERTM channels Android does not expose to ordinary apps. The app must own the controller (HCI) or the HAL. |
| Privilege | Owning the controller needs uid 0. Stock Android will not give it. The app must borrow it per boot through a kernel exploit. |
| Pairing | Apple's pairing bootstrap, SMP bonding, and long-term key exchange are undocumented. They must be specified from black-box captures. |
| Activation | Activation is an Apple service tied to an Apple ID. The app relays it; it never forges it. |
| Services | Control, setup, sync, faces, notifications, health, and call relay are separate protocols with their own state machines. |
| Drift | A watchOS or Android update can invalidate an exploit or a protocol assumption overnight. |

The exploit layer solves the **second** row, on the user's own phone, for the
duration of a session. It does not solve, and is not intended to solve, any of
the others.

---

## Feature scope for v1.0

| Tier | Feature | Status |
| --- | --- | --- |
| T0 | Pair, activate, visible face, reconnect | Core |
| T1 | Notifications (Android apps → Watch) | Core |
| T1 | Watch faces: inventory, select, safe mutate | Core |
| T1 | Settings: wrist, 24-hour, invert | Core |
| T2 | Health data ingress (local only) | Core |
| T2 | Wi-Fi credential relay | Core |
| T3 | Calls / SMS relay | Stretch, last, may slip |

T0–T2 are the v1.0 commitment. T3 is explicitly allowed to miss v1.0 without
blocking the release.

---

## Docs

Read in this order:

1. [Charter](docs/00-charter.md) — why the project exists, what "done" means.
2. [Feasibility](docs/01-feasibility.md) — the honest engineering assessment.
3. [Architecture](docs/02-architecture.md) — components, boundaries, data flow.
4. [Clean room](docs/03-clean-room.md) — how we stay legally and technically clean.
5. [Payload system](docs/04-payload-system.md) — the exploit/payload catalog.
6. [Stack](docs/05-stack.md) — language and framework decisions.
7. [Roadmap](docs/06-roadmap.md) — phases, gates, kill criteria.
8. [Protocol research](docs/07-protocol-research.md) — what to reverse engineer and how.
9. [Testing](docs/08-testing.md) — evidence, hardware matrix, acceptance.
10. [Legal and ethics](docs/09-legal-ethics.md) — the lines we do not cross.
11. [Risks](docs/10-risks.md) — what can kill the project.
12. [Glossary](docs/11-glossary.md) — terms and abbreviations.

Decisions are recorded as ADRs in [`docs/adr/`](docs/adr/).

---

## Planned repository layout

```text
open-crown/
├── app/                  Android application (Kotlin, Compose)
├── core/                 Rust protocol core (no OS I/O)
├── ffi/                  UniFFI bindings and generated Kotlin
├── agent/                Privileged helper (HCI broker, payload host)
├── runtime/              Exploit runtime and payload loader (native)
├── catalog/              Payload catalog schema, manifests, tooling
├── protocol/             Clean-room protocol specs and golden vectors
├── docs/                 This documentation set
└── tools/                Capture analysis, profile builders, release tooling
```

The layout is deliberately different from any prior project: Android-native UI,
Rust core, a separate privileged agent, and a first-class signed payload
catalog with its own runtime.

---

## License

Not chosen yet. Until a license is selected, all rights are reserved. The
clean-room policy in [docs/03-clean-room.md](docs/03-clean-room.md) applies to
every contribution.
