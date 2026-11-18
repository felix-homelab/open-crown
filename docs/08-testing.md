# 08 — Testing

## 1. Philosophy

A feature is done when there is a **capture**, a **vector**, a **test**, and a
**hardware note**. Compiling is not done. A passing mock is not done. "It worked
once" is a lead, not a result.

The project has two unusually risky subsystems — borrowed root and Apple ID
authentication — so their tests are first-class, not afterthoughts.

## 2. Test levels

| Level | What | Where | Gate |
| --- | --- | --- | --- |
| L0 | Unit tests, parsers, state machines | host (`cargo test`) | every PR |
| L1 | Golden vectors: bytes in → bytes out | host | every PR |
| L2 | Integration with `MockTransport` (recorded traces) | host + emulator | every PR |
| L3 | Hardware-in-the-loop: real phone + real Watch | bench | nightly / release |
| L4 | Field: clean install, factory reset, reboot, week-long use | owner | release candidate |

L1 is the backbone: every protocol spec must have at least one vector, and the
vector must come from a capture.

## 3. Hardware matrix

At kickoff: one Watch, one Android phone, one iPhone (for activation research
and as a reference stack).

| Slot | Device | Purpose |
| --- | --- | --- |
| W1 | Primary Watch | All pairing/setup tests |
| P1 | Primary phone | Exploit + transport |
| P2 | Sacrificial phone (recommended) | Brick drill, panic tests |
| I1 | iPhone | Reference capture, activation comparison |

The matrix grows only when a feature needs a second data point. Do not claim
support for a device that has never been tested; the support list is evidence,
not intention.

## 4. Exploit and privilege tests

These are the tests that protect the user's phone.

| Test | Pass condition |
| --- | --- |
| Clean reboot | After a session and reboot: no `su`, no agent process, no loaded module, no modified partition |
| Panic recovery | A forced panic reboots to a working system; the app reports the failed run and does not auto-retry |
| Retry budget | Four attempts in one boot are refused; cooldown observed |
| Target mismatch | A payload for another build is rejected before execution |
| Signature failure | A tampered manifest or artifact is rejected by the runtime and the agent |
| Agent orphan | Killing the app terminates the agent within 5 s |
| Preflight | Low battery / low storage aborts before the exploit runs |
| Data integrity | User data and app data survive a session; no corruption after 20 runs |
| Brick drill | On the sacrificial phone, a deliberately bad payload is recovered by reboot; no bootloader unlock needed |

The brick drill is mandatory before any public release.

## 5. Authentication tests

| Test | Pass condition |
| --- | --- |
| SRP vectors | Implementation matches published SRP-6a test vectors |
| Password lifetime | Password buffer is zeroized; no copy in logs, crash dumps, or backups |
| 2FA | Code accepted once, never logged, expires correctly |
| Token storage | Token in Keystore, not in SQLite or preferences |
| Token expiry | Expired token forces re-auth, not a silent failure |
| Lockout | Repeated failures surface a clear message and stop retrying |
| Network | Only pinned Apple endpoints and the catalog are contacted |

## 6. Protocol acceptance per feature

Each feature has an acceptance note in `protocol/<area>/<feature>.md`:

```text
Acceptance
  Given a factory-reset Watch on <watchOS>, when <action>, then <observable>.
  Evidence: capture <hash>, vector <path>, hardware note <date>.
  Known limits: <what is not covered>.
```

A feature without an acceptance note cannot be marked done in the roadmap.

## 7. Regression and fuzzing

- **Parser fuzzing** (`cargo-fuzz`) for every untrusted input: advertisements,
  control frames, notification payloads, face resources.
- **State-machine property tests** (`proptest`): no transition may skip a
  checkpoint or replay a non-idempotent step.
- **Capture replay** in CI: a corpus of real captures must parse without panic
  and produce the expected actions.
- **Nightly HIL** on the bench: pair, reboot, reconnect, sync a notification.

## 8. Evidence format

Every hardware test produces a note in `docs/evidence/`:

```text
date, operator, phone build, watch model, watchOS, payload id
what was done, what was observed, what was captured
result: pass / fail / partial
attachments: capture hash, screenshots, logs (redacted)
```

The note is committed with the PR that claims the feature. A claim without a
note is reverted.

## 9. Release gates

- All L0–L2 green.
- L3 green on the matrix for the features claimed.
- Exploit safety suite green, including the brick drill.
- Auth suite green.
- Clean-room audit sign-off.
- SBOM and reproducible build verified.
- Recovery documentation published.
