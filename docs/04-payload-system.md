# 04 — Payload and exploit system

## 1. Scope

A **payload** is a signed package that gives the app a capability stock Android
will not grant. Payloads run on the **phone**, for one session. Nothing in this
system runs on, modifies, or attacks the Watch.

The model is **zero-install privilege**:

- The phone is stock between sessions: no permanent root, no Magisk module, no
  boot-image change.
- When the app needs the radio, it runs a device-matched exploit, borrows root,
  starts the agent, and uses the radio.
- When the session ends — or the phone reboots — the privilege is gone.

This is the same shape as community per-boot root tools, but the payload does
not exist to give the user root. It exists to let Open Crown own the Bluetooth
controller. The user never gets a shell unless they ask for one.

## 2. Payload classes

| Class | Grants | Example | Lifetime |
| --- | --- | --- | --- |
| `exploit` | uid 0 for the session | Device/kernel-matched chain (page-cache, futex/UAF, future family) | One session |
| `agent` | The root helper binary | HCI broker + policy + audit | One session |
| `shim` | A narrow transport API | HAL bridge or socket shim for a specific device family | One session |
| `diag` | Read-only device report | Kernel, build, capability probe | Runs unprivileged |

Every run requires `exploit` + `agent`. `shim` is optional and device-specific.

## 3. Exploit families

The catalog is organised by family, not by CVE. A family is a reusable technique
with a per-device calibration:

| Family | Sketch | Calibration |
| --- | --- | --- |
| Page-cache write | A kernel path writes attacker-controlled bytes into a file's page cache without write permission; the app patches a root-executed component | Exact kernel build, target file, block offsets |
| Futex/UAF | A race in a kernel object gives a controlled write; used to forge a work item that runs a helper as root | Struct offsets, symbol addresses, KASLR strategy |
| Future | Anything that yields a reliable uid 0 | Same contract |

The catalog never ships a payload that has not been run on the target build. A
"port" is a new entry with its own evidence.

## 4. Bundle format

```text
payload.crown
├── manifest.json
├── manifest.sig
├── files/
│   ├── exploit.bin
│   └── agent
└── SBOM.json
```

### Manifest

```json
{
  "schema": 1,
  "id": "org.opencrown.payload.example-e1s",
  "version": "1.0.0",
  "min_agent": "1.0.0",
  "family": "page-cache",
  "targets": [
    {
      "model": "SM-S921B",
      "kernel_build": "6.1.162-android14-11-34343818",
      "abi": "arm64-v8a",
      "android": ">=14"
    }
  ],
  "capabilities": ["root", "hci"],
  "entry": "files/exploit.bin",
  "agent": "files/agent",
  "persistence": "ephemeral",
  "reversible": true,
  "artifacts": [
    {"path": "files/exploit.bin", "sha256": "…", "size": 123456},
    {"path": "files/agent", "sha256": "…", "size": 654321}
  ],
  "notes": "Shown at consent time."
}
```

Rules:

- `targets` is exact on model and kernel build. No wildcards.
- `capabilities` is a closed enum; unknown capability = reject.
- `entry` and `agent` must resolve inside `files/`; traversal is rejected.
- Every artifact is hashed; the manifest hash is signed.
- `persistence` must be `ephemeral` for v1. `session` and `explicit` are
  reserved and rejected by the v1 agent.

## 5. Signing and trust

```text
manifest.sig = Ed25519(catalog_key, SHA-256(canonical(manifest.json)))
```

- The app pins one or more catalog public keys.
- The root key is offline; payloads are signed by delegated keys with short
  validity.
- A revocation list ships with the catalog and is cached; a revoked payload
  cannot run even if already downloaded.
- Verification happens in the **agent** and in the runtime, so a compromised UI
  cannot bypass it.
- An append-only transparency log publishes every payload hash and signature.

## 6. Target matching and support

```text
fingerprint = SHA-256(model || kernel_release || abi || android_sdk || build_id)
```

Matching is exact on `model` and `kernel_build`, range-based on `android`. A
payload that does not match is never executed. A maintainer-only override
exists behind a compile-time flag and is absent from release builds.

Support policy:

- A payload is **supported** while it has a passing hardware test on the current
  app release.
- When the vendor patches the bug, the payload is marked **retired**; the app
  says so and points at the device matrix.
- The catalog may carry multiple families for one device if more than one
  works; the runtime picks the highest-reliability entry.

## 7. Execution lifecycle

```text
requested → consented → verified → staged → executed → observed → disposed
```

1. **Requested** — the core asks for a capability the session lacks.
2. **Consented** — the UI shows id, publisher, family, capabilities,
   persistence, expected reboot risk, and a plain-language explanation.
3. **Verified** — signature, revocation, target, agent version.
4. **Staged** — files in a private dir, hashes re-checked.
5. **Executed** — the exploit borrows root and starts the agent with a one-time
   token over a Unix socket.
6. **Observed** — the agent records the capability handshake and the HCI
   operations in the audit log.
7. **Disposed** — the agent exits; the exploit's loaded state is released; files
   are deleted; the checkpoint is marked stale.

## 8. Safety requirements

- **Memory-only by default.** No partition writes, no boot image, no persistent
  module.
- **No silent execution.** Every run has a user gesture and a visible ongoing
  notification.
- **Preflight:** battery above 30%, free space above 200 MB, exact target match,
  no other session active.
- **Watchdog:** the agent self-terminates if the app disappears; the exploit's
  borrowed root does not outlive the agent.
- **Crash recovery:** a panic reboots the phone; the app detects an unclean run
  on next start and reports it without auto-retrying.
- **Retry budget:** three attempts per boot, then cooldown; no boot loops.
- **Audit log:** append-only, local, exportable.

## 9. Threat model

| Threat | Impact | Mitigation |
| --- | --- | --- |
| Malicious payload in catalog | Root compromise | Offline signing, transparency log, two reviewers, reproducible builds |
| Compromised catalog key | Same | Rotation, revocation, threshold signing for `exploit` |
| Compromised UI process | Capability abuse | Agent checks caller UID + signature + one-time token |
| Replay of an old payload | Downgrade | Version monotonicity + revocation |
| Exploit instability | Brick / panic | Memory-only, preflight, watchdog, retry budget, recovery docs |
| Payload escape | Kernel compromise | Minimal agent, seccomp, no ambient capabilities |
| Supply chain | Backdoored binary | Reproducible builds, SBOM, pinned toolchain |

## 10. What the catalog will never contain

- An Activation Lock, iCloud Lock, or pairing-lock bypass.
- A watchOS exploit, jailbreak, or firmware image.
- A payload that hides from the user, persists silently, or disables security
  logging.
- A payload whose only purpose is to defeat another person's device.
- A generic "root any phone" payload with no target list.
- A payload that gives the user a persistent shell.

## 11. Governance

| Action | Requirement |
| --- | --- |
| Add a payload | Two reviewers, reproducible build, published source |
| Grant `root` | Same + offline signature + 7-day comment period |
| Revoke | Any maintainer, immediate |
| Change signing keys | Documented ceremony, overlap window |
| Retire | Announce in the catalog and the app |

## 12. Bring-your-own-root

A user who already has a root solution can point the agent at it and skip the
catalog entirely. The agent then runs under that root, and the app records the
provenance as `user-supplied`. This is the escape hatch for unsupported devices
and the recommended path for developers.
