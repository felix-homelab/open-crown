# 01 — Feasibility

This document is deliberately uncomfortable. It separates what is proven, what
is plausible, and what is not going to happen. The project should be killed or
re-scoped if the assumptions in §8 stop holding.

## 1. The core question

Can an Android phone own an Apple Watch without an iPhone?

**Yes — but only by borrowing root, per boot, from the phone's own kernel.**

The Watch's protocol runs over Classic Bluetooth L2CAP channels with ERTM, plus
a security handshake Android's public API does not expose. A normal app cannot
open those channels, set the link policy, or see raw HCI traffic. The phone has
to take control of the controller, and stock Android will not grant that to an
app.

So the project is a **three-body problem**:

1. **Protocol** — re-implement Apple's pairing and services (hard, long).
2. **Privilege** — borrow root for a session on a stock phone (hard, brittle).
3. **Product** — make the result usable by a human (medium).

The exploit layer exists for (2). It does not help with (1), and it is the part
most likely to break on a random Tuesday.

## 2. Layer-by-layer verdict

| Layer | Verdict | Why |
| --- | --- | --- |
| BLE advertising / discovery | **Feasible today, no root** | Public BLE scanning sees the Watch's setup advertisement. |
| Classic L2CAP / ERTM transport | **Feasible with per-boot root** | Needs HCI-level control. The exploit borrows uid 0 long enough to open it. |
| Privilege acquisition | **Feasible but brittle** | A kernel exploit matched to the exact device and kernel build. DirtyFrag-style page-cache chains and futex/UAF chains are the known families. Patched by vendors at will. |
| Pairing bootstrap | **Feasible, large effort** | Undocumented; must be specified from captures. Field reports show it is reachable from a privileged host. |
| Bonding / long-term keys | **Feasible, delicate** | Must survive reboots and reconnects; key storage is the hard part. |
| Activation | **Feasible as a relay** | Apple service bound to an Apple ID. The app signs in and relays; it never forges. |
| Control channel (IDS) | **Feasible, ongoing** | A family of request/response services; each needs its own spec and tests. |
| Setup completion | **Feasible, fiddly** | Ordering and observer branches matter more than any single message. |
| Faces / settings | **Feasible, incremental** | Per-feature work; readback required. |
| Notifications | **Feasible** | Requires an Android notification listener and the Watch bulletin path. |
| Health sync | **Partially feasible** | Encrypted ingress can be implemented; completeness is open-ended. |
| Wi-Fi relay | **Feasible for supported networks** | Narrow, explicit network support. |
| Calls / SMS relay | **Unlikely v1, possible later** | Tightly coupled to telephony and Apple services; high effort, low certainty. |
| Watch app install | **Not feasible** | Requires Apple code signing. Permanently out of scope. |
| watchOS jailbreak | **Does not exist publicly** | No known public chain. Out of scope. |
| Activation Lock bypass | **Refused** | Illegal in many jurisdictions and enables theft. Not implemented. |

## 3. The privilege model

The phone stays **stock**: locked bootloader, no permanent root, no Magisk
module required, no boot-image modification.

```text
app launch
  → detect device + kernel build
  → match a signed payload
  → run exploit (in-app) → borrow uid 0
  → bootstrap the Crown Agent (root, session-scoped)
  → agent owns HCI / HAL, runs the session
  → session ends, agent exits, payload unloaded
  → reboot (voluntary or not) → stock again
```

Consequences the design must accept:

- **Re-bootstrap every boot.** The app cannot rely on a daemon surviving reboot.
- **No payload, no pairing.** An unsupported device falls back to discovery-only
  mode with a clear explanation — never a silent broken state.
- **Exploit reliability is a product feature.** A 60%-reliable exploit is a 60%
  reliable app. The UI must expose attempts, retries, and failure reasons.
- **A failed run must not brick the phone.** Payloads are memory-only where
  possible, with preflight checks and a documented recovery path.
- **Vendor patches break devices.** The catalog needs a deprecation policy and a
  "your build is not supported" message that is honest.

## 4. Why "exploits" are host-side only

The catalog contains privilege-acquisition packages for the **phone**:

1. **Exploit payloads** — device- and kernel-matched chains that yield uid 0 for
   one session.
2. **Agent binary** — the small root helper the exploit bootstraps; owns the
   radio and nothing else.
3. **Optional HAL shim** — a narrow bridge for devices where a socket is not
   enough.

Nothing in the catalog targets the Watch. There is no watch-side payload, no
firmware image, and no Apple binary in this project.

## 5. What has been shown to work (field evidence)

Public research and community reports establish that a privileged host can:

- discover a Watch in setup mode and decode its advertisement;
- open the pairing bootstrap and complete a PIN or optical handshake;
- activate the Watch against Apple's service with the owner's Apple ID;
- complete setup and reach a normal face;
- reconnect over an encrypted link and exchange control traffic.

They also establish that ephemeral root via kernel exploits is practical on
Samsung-class devices, and that a KernelSU module can be late-loaded without a
bootloader unlock. Open Crown does not depend on KernelSU, but the mechanism
(borrow root, run a helper, unload) is proven.

These are existence proofs, not a supported matrix.

## 6. The genuinely unknown things

| Unknown | Why it matters | How we resolve it |
| --- | --- | --- |
| Whether a non-exploit transport exists | Would remove the whole payload layer | Spike: attempt L2CAP CoC / SDP tricks on stock Android; expect failure; document. |
| How much of pairing is Apple-server-dependent | Decides offline behaviour and activation flow | Capture a full pairing; classify each message local vs server. |
| Apple ID sign-in from a non-Apple client | Core of activation; SRP + 2FA must work | Prototype SRP + 2FA against a test account; measure lockout risk. |
| Exploit reliability across builds | Determines the user experience | Field data per payload: success rate, panic rate, required retries. |
| Protocol stability across watchOS | Maintenance cost | Versioned spec with per-release deltas from day one. |

## 7. Feature tiers (v1.0 commitment)

| Tier | Features | Commitment |
| --- | --- | --- |
| T0 | Pair, activate, visible face, reconnect | Must ship |
| T1 | Notifications, faces, settings | Must ship |
| T2 | Health ingress, Wi-Fi relay | Must ship |
| T3 | Calls / SMS relay | Best effort; may slip to v1.1 |

T3 is explicitly allowed to miss v1.0. It is sequenced last and has its own
kill gate.

## 8. Kill criteria

Stop or re-scope if any of these becomes true:

1. Pairing needs a secret that cannot be obtained without breaking Apple's
   device security.
2. Activation cannot be completed for an owner's own Watch without an iPhone
   present, and no relay path exists.
3. No exploit payload can be maintained for the target device family for more
   than one Android release cycle.
4. A watchOS release makes the protocol opaque to black-box capture.
5. Apple ID sign-in cannot be done safely (account lockout, mandatory
   attestation, or terms that make it untenable).
6. Legal review concludes the work cannot be distributed in the target
   jurisdictions.

## 9. Effort shape

This is a multi-month, part-time project:

- 15% capture tooling and protocol specs
- 20% exploit runtime, payload catalog, agent
- 30% pairing / security / transport
- 20% setup, activation, state machines
- 15% features, UI, hardening

The first milestone worth celebrating is **P3: pair and activate a
factory-reset Watch on a stock phone with a borrowed-root session**. Everything
before it is preparation; everything after it is expansion.
