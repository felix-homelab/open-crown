# 01 — Feasibility

This document separates what is proven, what is plausible, and what is not
going to happen. It is the document that decides whether to spend a year, so it
cites rather than asserts.

## 1. The core question

Can an Android phone own an Apple Watch without an iPhone?

**For the protocol: yes — it has been done and published.** Fresh pairing,
activation, and encrypted reconnect are demonstrated on Linux and Windows with
native HCI, and on Android with Magisk. The Watch does not require an iPhone to
be the peer.

**For the product: unknown.** The published Android path requires Magisk plus a
priv-app, a hidden-API whitelist and SELinux rules. Its own installation notes
say: *"Root access alone does not supply this configuration."* Open Crown's
whole reason to exist is to replace that configuration with a per-boot payload.
That is unproven.

So the project is one claim plus a lot of inherited work:

1. **Protocol** — already reconstructed in public. Our job is verification and a
   clean reimplementation.
2. **Privilege** — the open question. `uid 0` for a session is not the same as
   a process that can hold the controller.
3. **Product** — inherited from the prior work once (1) and (2) hold.

## 2. What the prior art establishes

Reference: [apple_watch_connector](https://github.com/LordixDemon/apple_watch_connector)
(Java, Flutter, Rust; README snapshot October 2026; no licence file).

| Claim | Evidence in the prior work | Caveat |
| --- | --- | --- |
| Discovery and advertisement decode | Android and desktop | Single Watch family |
| Pairing (six-digit PIN, optical code) | Fresh pairing verified on Linux and Windows | One watchOS build |
| Device activation and a visible face | Hardware journal and optical-pairing report | "Activation" may not imply an Apple ID session |
| Encrypted reconnect without re-pairing | Same-pair reconnect verified | Same hardware only |
| Faces, settings, notifications, Wi-Fi | Implemented on Android | Desktop service coverage partial |
| Android operation | Magisk module + priv-app + hidden-API whitelist + SELinux rules | **Rooted device, not stock** |
| Hardware | Apple Watch Ultra 2 (Watch7,5), watchOS 26.2 (23S303), OnePlus CPH2653 | One data point |

This is an existence proof, not a support matrix. It proves the protocol is
reachable from a non-Apple host and that the Watch accepts one. It does **not**
prove that Open Crown's privilege model works, and it does not prove activation
in the sense that matters for an Apple ID account session.

## 3. Layer-by-layer verdict

| Layer | Verdict | Why |
| --- | --- | --- |
| BLE advertising / discovery | **Proven, no privilege** | Public BLE scanning sees the Watch. |
| Classic L2CAP / ERTM transport | **Proven with privilege** | Needs controller ownership; demonstrated by prior art. |
| Privilege without Magisk | **Unproven — the project's risk** | `uid 0` plus an SELinux domain must be shown under enforcing policy. |
| Pairing bootstrap | **Proven, large effort to reimplement** | Documented by prior art; still the biggest code surface. |
| Bonding / long-term keys | **Proven, delicate** | Keys live in the controller; provisioning across boots is the hard part. |
| Activation (device) | **Proven** | Prior art activates a factory-reset Watch. |
| Activation (Apple ID session) | **Unknown** | Whether the phone or the Watch holds the session is undecided. |
| Control channel (IDS) | **Proven, ongoing** | Each service needs its own spec and tests. |
| Setup completion | **Proven, fiddly** | Ordering and observer branches matter more than any message. |
| Faces / settings | **Proven, incremental** | Per-feature work; readback required. |
| Notifications | **Proven** | Requires an Android listener and the bulletin path. |
| Health sync | **Partially proven** | Encrypted ingress works; completeness is open-ended. |
| Wi-Fi relay | **Partially proven** | Delivery is shown; the Watch joining the network is a separate observation. |
| Calls / SMS relay | **Unlikely v1** | Coupled to telephony and Apple services. |
| Watch app install | **Not feasible** | Requires Apple code signing. |
| watchOS jailbreak | **Does not exist publicly** | Out of scope. |
| Activation Lock bypass | **Refused** | Illegal in many jurisdictions and enables theft. |

## 4. The privilege model

The phone keeps its bootloader locked and its system partition untouched. It
does **not** stay unprivileged while the app is in use.

```text
app launch
  → detect device + kernel build
  → match a signed payload
  → run payload (in-app) → uid 0
  → payload also establishes an SELinux context that can hold the controller
  → start the resident agent, verify the one-time token
  → agent owns the controller, runs the session, stays behind a foreground service
  → watch unpaired or app stopped → agent exits
  → reboot → everything gone
```

Consequences the design must accept:

- **`uid 0` is necessary, not sufficient.** The payload contract is
  "uid 0 + domain". The SELinux step is the unproven part, and it is the first
  experiment.
- **The agent is a daemon while a watch is paired.** Notifications require a
  live link. "Session" means one boot, not one UI visit.
- **No payload, no pairing.** An unsupported device falls back to
  bring-your-own-root or discovery-only mode, with a clear explanation.
- **Exploit reliability is a product feature.** A 60%-reliable payload is a 60%
  reliable app. The UI shows attempts, retries, and failure reasons.
- **Vendor patches retire devices.** The support list shrinks, and the README
  says so.
- **A failed run must not brick the phone.** Payloads are memory-only where the
  family allows it, with preflight checks and a documented recovery path.

## 5. What "exploit" means here

The catalog contains privilege-acquisition packages for the **phone**:

1. **Payloads** — device- and kernel-matched chains that yield `uid 0` and a
   usable SELinux context.
2. **Agent binary** — the resident helper the payload bootstraps; owns the radio
   and nothing else.
3. **Optional shim** — a narrow bridge for device families where a socket is not
   enough.

Nothing in the catalog targets the Watch. There is no watch-side payload, no
firmware image, and no Apple binary in this project.

## 6. The genuinely unknown things

| Unknown | Why it matters | How we resolve it |
| --- | --- | --- |
| Can a payload hold the controller under enforcing SELinux? | Decides whether a stock-phone product exists | Phase 0 / K2a, with temporary root as the harness |
| Who holds the Apple ID session? | Decides the phone's role and the credential design | Trace the prior art's `activationResponse` path; then a test account |
| How long does an n-day survive? | Determines the supported list and the update warning | Track vendor bulletins per family; field data |
| Does the Watch accept a non-Apple host for every service? | Decides feature tiers | Per-feature hardware tests |
| Will users accept "updates may break it"? | Decides whether the product has an audience | Ask, then measure attrition |

## 7. Feature tiers (v1.0 commitment)

| Tier | Features | Commitment |
| --- | --- | --- |
| T0 | Pair, activate, visible face, reconnect | Must ship, gated by K2a and the activation decision |
| T1 | Notifications, faces, settings | Must ship after T0 |
| T2 | Health ingress, Wi-Fi relay | Must ship after T1 |
| T3 | Calls / SMS relay | Best effort; may slip indefinitely |

## 8. Kill criteria

Stop or re-scope if any of these becomes true:

1. **K2a fails:** no acceptable path to the controller without Magisk's priv-app
   and policy configuration. Fall back to a rooted-device companion.
2. **Activation is Apple-device-only:** no relay path exists and the Watch
   cannot hold the session itself.
3. **No payload survives a release cycle:** no n-day can be maintained for one
   Android release cycle on the target family.
4. **A watchOS release makes the protocol opaque** to capture and observation.
5. **Apple ID sign-in cannot be done safely** — lockout or attestation makes it
   untenable — and the product has no credential-free tier.
6. **Legal review concludes** the work cannot be distributed in the target
   jurisdictions, or the accepted risk is not survivable.

## 9. Effort shape

Honest order of magnitude: **thirty to fifty part-time weeks**, or one to two
calendar years once hardware waits and review cycles are included — before the
feature tiers are complete. The rewrite alone is three to six months before the
protocol work starts, and that is a deliberate cost, not an accident.

| Track | Share | Note |
| --- | --- | --- |
| Protocol verification and specs | 25% | Against the prior art's map, not from zero |
| Companion core (pairing, setup, IDS) | 30% | The largest code surface |
| Privilege runtime, payloads, agent | 20% | The riskiest and least plannable |
| Features and UI | 15% | Faces, notifications, health, Wi-Fi |
| Governance, signing, release | 10% | Two-reviewer policy, SBOM, transparency |

The first milestone worth celebrating is **K2a**: a payload holds the controller
under enforcing SELinux. Everything before it is preparation; everything after
it is a product.
