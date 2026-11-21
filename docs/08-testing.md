# 08 — Testing

## 1. Philosophy

A feature is done when there is a **capture**, a **vector**, a **test**, and a
**hardware note**. Compiling is not done. A passing mock is not done. "It worked
once" is a lead, not a result.

Two subsystems are unusually risky — borrowed privilege and Apple ID handling —
so their tests are first-class, and the privilege test runs before any product
code exists.

## 2. Test levels

| Level | What | Where | Gate |
| --- | --- | --- | --- |
| L0 | Unit tests, parsers, state machines | host (`cargo test`) | every PR |
| L1 | Golden vectors: bytes in → bytes out | host | every PR |
| L2 | Integration with `MockTransport` | host + emulator | every PR |
| L3 | Hardware-in-the-loop: real phone + real Watch | bench | nightly / release |
| L4 | Field: clean install, factory reset, reboot, week-long use | owner | release candidate |

L1 is the backbone: every protocol spec has at least one vector from a capture.

## 3. Hardware matrix

At kickoff: one Watch, one rooted phone, one sacrificial phone, one Linux host
with a USB HCI dongle, and one iPhone as a reference stack.

| Slot | Device | Purpose |
| --- | --- | --- |
| W1 | Apple Watch Ultra 2 (Watch7,5), watchOS 26.2 (23S303) | All pairing/setup tests |
| P1 | OnePlus CPH2653, stock (payload only; no harness root) | Privilege and transport |
| P2 | Sacrificial phone, same family | Brick drill, panic tests |
| L1 | Linux host + USB BR/EDR dongle | Protocol work without Android |
| I1 | iPhone | Reference capture, activation comparison |

The matrix grows only when a feature needs a second data point. The support list
is evidence, not intention, and it names models and OS builds — not "Android 12+".

## 4. Privilege tests

These protect the user's phone.

| Test | Pass condition |
| --- | --- |
| **K2a — own the controller** | Under enforcing SELinux, the probe binds `HCI_CHANNEL_USER`, issues a command and reads the command complete; the exact context is recorded |
| K2a negative | A run that does not obtain the context is denied; the avc line is recorded as the denial |
| Clean reboot | After a session and reboot: no agent, no payload, no modified partition, no changed policy |
| Panic recovery | A forced panic reboots to a working system; the app reports the failed run and does not auto-retry |
| Retry budget | Three attempts in one boot; a fourth is refused |
| Target mismatch | A payload for another build is rejected before execution |
| Signature failure | A tampered manifest or artifact is rejected by runtime and agent |
| Agent orphan | Killing the app terminates the agent within the grace period |
| Agent persistence | The agent survives the UI being backgrounded, and dies at reboot |
| Preflight | Low battery / low storage aborts before the payload runs |
| Data integrity | User data survives a session; no corruption after 20 runs |
| Brick drill | On the sacrificial phone, a deliberately bad payload is recovered by reboot; no bootloader unlock |

The brick drill is mandatory before any public release.

## 5. Authentication tests

The shape depends on ADR 0003.

| Test | Pass condition |
| --- | --- |
| Relay mode | Owner input transits the app, is never persisted, and is zeroized after forwarding |
| Session mode | SRP vectors match the chosen implementation; 2FA accepted once; token in Keystore |
| Password lifetime | No copy in logs, crash dumps, or backups |
| Token storage | Token in Keystore, not in SQLite or preferences |
| Token expiry | Expired token forces re-auth, not a silent failure |
| Lockout | Repeated failures surface a clear message and stop retrying |
| Network | Only pinned Apple endpoints and the catalog are contacted |

## 6. Protocol acceptance per feature

```text
Acceptance
  Given a factory-reset Watch on <watchOS>, when <action>, then <observable>.
  Evidence: capture <hash>, vector <path>, hardware note <date>.
  Known limits: <what is not covered>.
```

A feature without an acceptance note cannot be marked done.

## 7. Regression and fuzzing

- **Parser fuzzing** (`cargo-fuzz`) for advertisements, control frames,
  notification payloads, face resources.
- **State-machine property tests** (`proptest`): no transition skips a
  checkpoint or replays a non-idempotent step.
- **Capture replay** in CI: a corpus of real captures parses without panic and
  produces the expected actions.
- **Nightly HIL** on the bench: pair, reboot, reconnect, sync a notification.

## 8. Evidence format

Every hardware test produces a note in `docs/evidence/`:

```text
date, operator, phone build, watch model, watchOS, payload id
what was done, what was observed, what was captured
result: pass / fail / partial
attachments: capture hash, screenshots, logs (redacted)
```

The note is committed with the PR that claims the feature. A claim without a note
is reverted.

## 9. Release gates

- All L0–L2 green.
- L3 green on the matrix for the features claimed, including K2a.
- Exploit safety suite green, including the brick drill.
- Auth suite green for the chosen shape.
- Provenance audit sign-off.
- SBOM and reproducible build verified.
- Recovery documentation published.
