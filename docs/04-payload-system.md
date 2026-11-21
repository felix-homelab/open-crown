# 04 — Payload and exploit system

## 1. Scope

A **payload** is a signed package that gives the app a capability Android will
not grant. Payloads run on the **phone**, for one boot. Nothing in this system
runs on, modifies, or attacks the Watch.

## 2. The contract: uid 0 + domain

The previous revision said "borrow `uid 0` for a session." That is not the
capability the design needs, and the only working Android build of this stack
says so itself: it ships a priv-app, a hidden-API whitelist and SELinux rules,
and states that *"root access alone does not supply this configuration."*

The contract is therefore:

> **A payload yields `uid 0` and an SELinux context that can hold the
> Bluetooth controller, for the life of the boot.**

This is unproven for a stock phone. It is the first experiment (K2a) and the
first kill criterion. See [ADR 0007](adr/0007-uid0-plus-domain.md).

## 3. Payload classes

| Class | Grants | Example | Lifetime |
| --- | --- | --- | --- |
| `payload` | `uid 0` + domain | Device/kernel-matched n-day chain | One boot |
| `agent` | The privileged helper binary | Controller broker + policy + audit | One boot |
| `shim` | A narrow transport API | HAL bridge for a specific family | One boot |
| `diag` | Read-only device report | Kernel, build, capability probe | Unprivileged |

Every run requires `payload` + `agent`. `shim` is optional and
device-specific.

## 4. Supply: public n-days

**We use publicly disclosed vulnerabilities, calibrated per build.** We do not
acquire 0-days and we do not write a fresh chain for every device.

Consequences, stated plainly:

- A supported device is, by definition, a device that has not received the patch
  that fixes the payload. The README says this in its first screen.
- The supported list ages. Every security bulletin can remove an entry.
- The app must never pressure a user to skip updates. It states the trade-off
  once, at setup, and links to the supported list.
- A retired payload is marked retired; the app says so and stops offering it.
- We do not ship a payload for a build we have not run. A port is a new entry
  with its own evidence.

The alternative — buying or developing chains — is out of scope for a
single-maintainer project and would change the legal posture in
[09](09-legal-ethics.md).

## 5. Exploit families

The catalog is organised by family, not by CVE. A family is a reusable technique
with a per-device calibration:

| Family | Sketch | Calibration |
| --- | --- | --- |
| Page-cache write | A kernel path writes attacker-controlled bytes into a file's page cache without write permission; used to patch a root-executed component | Exact kernel build, target file, block offsets, persistence behaviour |
| Futex/UAF | A race gives a controlled write; used to forge a work item that runs a helper with `uid 0` | Struct offsets, symbols, KASLR strategy |
| Future | Anything that yields a reliable `uid 0` + domain | Same contract |

Each family must document its **persistence behaviour**: what survives a reboot,
and why. "Memory-only" is a per-family claim, not a slogan.

## 6. Bundle format

```text
payload.crown
├── manifest.json
├── manifest.sig
├── files/
│   ├── payload.bin
│   └── agent
└── SBOM.json
```

### Manifest

```json
{
  "schema": 1,
  "id": "org.opencrown.payload.example",
  "version": "1.0.0",
  "min_agent": "1.0.0",
  "family": "page-cache",
  "targets": [
    {
      "model": "CPH2653",
      "kernel_build": "6.1.x-android14-...",
      "abi": "arm64-v8a",
      "android": ">=14"
    }
  ],
  "capabilities": ["root", "domain", "controller"],
  "entry": "files/payload.bin",
  "agent": "files/agent",
  "persistence": "boot",
  "reversible": "reboot",
  "artifacts": [
    {"path": "files/payload.bin", "sha256": "...", "size": 1},
    {"path": "files/agent", "sha256": "...", "size": 2}
  ],
  "notes": "Shown at consent time."
}
```

Rules:

- `targets` is exact on model and kernel build. No wildcards.
- `capabilities` is a closed enum; unknown capability = reject.
- `entry` and `agent` must resolve inside `files/`; traversal is rejected.
- Every artifact is hashed; the manifest hash is signed.
- `persistence` must be `boot` in v1. `session` and `explicit` are
  reserved and rejected by the v1 agent.

## 7. Signing and trust

```text
manifest.sig = Ed25519(catalog_key, SHA-256(canonical(manifest.json)))
```

- The app pins one or more catalog public keys.
- The root key is offline; payloads are signed by delegated keys with short
  validity.
- A revocation list ships with the catalog and is cached; a revoked payload
  cannot run even if already downloaded.
- Verification happens in the agent and in the runtime, so a compromised UI
  cannot inject a manifest.
- An append-only transparency log publishes every payload hash and signature.

## 8. Target matching

```text
fingerprint = SHA-256(model || kernel_release || abi || android_sdk || build_id)
```

Matching is exact on `model` and `kernel_build`, range-based on `android`.
A payload that does not match is never executed. A maintainer-only override
exists behind a compile-time flag and is absent from release builds.

The evidence-backed list lives in `catalog/supported-devices.json`, validated by
`catalog/tooling/devices.py` and read by the runtime (`devices` module) and by
the app. A device that is not on the list is `unknown`, which is a normal state
with a clear message, not a crash.

The list gates the UI and consent, not the runtime. The security boundary is the
manifest's exact target match; the list is evidence that we tested that build.
`runtime::prepare` therefore does not consult it, which keeps example bundles
and bench experiments working without pretending they are supported.

## 9. Execution lifecycle

```text
requested → consented → verified → staged → executed → resident → disposed
```

1. **Requested** — the core asks for a capability it lacks.
2. **Consented** — once per boot, with a user gesture. The UI shows id,
   publisher, family, capabilities, persistence, reboot risk, and the fact that
   the app will run a root daemon behind a notification.
3. **Verified** — signature, revocation, target, agent version.
4. **Staged** — files in a private dir, hashes re-checked.
5. **Executed** — the payload yields `uid 0` and a domain; the agent starts with
   a one-time token over a Unix socket.
6. **Resident** — the agent holds the controller behind a foreground service and
   records every privileged action.
7. **Disposed** — at unpair, app stop, or reboot. The agent exits; files are
   deleted; the checkpoint is marked stale.

Automatic reconnect does **not** re-run the payload: the agent is already
resident. If the agent is gone, re-raising it requires a new gesture. This
resolves the old contradiction between "re-run automatically" and "no silent
execution."

## 10. Safety requirements

- **Memory-only where the family allows it.** Each family documents its
  persistence behaviour; no partition or boot-image writes.
- **Visible execution.** A foreground service notification with a stop action.
  The agent is never silent.
- **Preflight:** battery above 30%, free space above 200 MB, exact target match,
  no other privileged session active.
- **Watchdog:** the agent self-terminates if the app disappears beyond the grace
  period; borrowed privilege does not outlive the agent.
- **Crash recovery:** a panic reboots the phone; the next start reports the
  unclean run without auto-retrying.
- **Retry budget:** three attempts per boot, then cooldown. No boot loops.
- **Audit log:** append-only, local, exportable.

## 11. Threat model

| Threat | Impact | Mitigation |
| --- | --- | --- |
| Malicious payload in catalog | Root compromise | Offline signing, transparency log, review, reproducible builds |
| Compromised catalog key | Same | Rotation, revocation, threshold signing |
| Compromised app process | Capability abuse | Agent validates UID + signature + token + envelope; policy limits operations |
| Long-lived root agent | Persistent attack surface | Small binary, seccomp, no network, foreground visibility, watchdog |
| Public n-day known to attackers | Anyone can root that build | Payloads are not secrets; we do not pretend the exploit is confidential |
| Replay of an old payload | Downgrade | Version monotonicity + revocation |
| Exploit instability | Panic / data loss | Per-family persistence analysis, preflight, watchdog, retry budget |
| Supply chain | Backdoored binary | Reproducible builds, SBOM, pinned toolchain |

## 12. What the catalog will never contain

- An Activation Lock, iCloud Lock, or pairing-lock bypass.
- A watchOS exploit, jailbreak, or firmware image.
- A payload that hides from the user, persists silently, or disables logging.
- A payload whose only purpose is to defeat another person's device.
- A generic "root any phone" payload with no target list.
- A payload that gives the user a persistent shell.

## 13. Governance

| Action | Requirement |
| --- | --- |
| Add a payload | Exact-target evidence, reproducible build, source published |
| Grant root | Same, plus a second reviewer where available |
| Ship without a second reviewer | Allowed, but labelled `unreviewed` in the catalog and release notes |
| Revoke | Any maintainer, immediate |
| Change signing keys | Documented ceremony, overlap window |
| Retire | Announced in the catalog and in the app |

## 14. Bring-your-own-root

A user who already has root can point the agent at it and skip the catalog. The
agent runs under that root, and the app records the provenance as
`user-supplied`. This is the fallback for unsupported devices and the
recommended path for developers. It is also the first thing to ship — see
[ADR 0010](adr/0010-ship-order.md).

## 15. What this is not

It is not a hidden capability. The payload binary is in the APK, the signature is
published, and anyone who wants the artifact can extract it. The project accepts
that and documents it in [09 — Legal and ethics](09-legal-ethics.md). Claiming
otherwise would be a lie that a reviewer would catch in five minutes.

## 16. Readiness (M1)

An indexed payload can be listed before it is built. `catalog/payload-index.json`
is source-level data: each payload names the upstream source and the device
targets that source publishes. It is not a signed bundle and grants nothing.
See [ADR 0014](adr/0014-payload-index-and-readiness.md).

`runtime::readiness` answers one question without running anything:

> Given this device, this index, this bundle directory, these pinned keys and
> this revocation list, would the payload be READY?

It reads the manifest, verifies the signature with the pinned catalog key,
checks every artifact's size and SHA-256, checks revocation, and compares
`min_agent`. It never stages a file, mints a token, spawns a process, or asks for
root. The first failing gate is the single reported reason: `no_match`,
`not_available`, `no_bundle`, `invalid_manifest`, `bad_signature`,
`artifact_mismatch`, `revoked` or `agent_too_old`; `ready` means every gate
passed.

Exactness is not relaxed for a payload whose upstream source publishes only a
kernel version. `kernel_build` is then `null`, and the target can never match.
The alternative — guessing the rest of `uname -r` — would defeat the matcher.

The M1 A52 (`SM-A528B`, `5.4.254-qgki-…`) is an `example` device in the
supported-device list and has no indexed target. The probe therefore reports
"recognised, nothing ready"; no payload is READY on it.

## 17. External payloads (private development)

The supplied upstream payloads are not standalone bundles. They are
quarantined unchanged under `payloads/external/` with a `PROVENANCE.md`, the
upstream licence and a descriptive Open Crown catalog name. They are not
MIT-licensed, and they are not Open Crown-authored code. See
[ADR 0015](adr/0015-external-payload-quarantine.md).

The first integrated artifact is `futex-timer`, the SamTimer DZJ3 payload for
CVE-2026-98260. It is signed, indexed and READY on its exact target by the
data-on-disk rules, but it has **not** been run or hardware-verified by Open
Crown, and it needs a Shizuku/shell runner that the app does not have. The A52
is not a target. Execution is a consent-gated launch contract
([ADR 0016](adr/0016-consent-gated-execution.md)), not a root escalation.
