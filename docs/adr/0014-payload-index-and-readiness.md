# ADR 0014 — Payload index v1 and the readiness engine

**Status:** Accepted
**Date:** 2026-11-21

## Context

M1 needs to answer one question for a list of payloads: *would this payload be
ready on this device, without running anything?* The catalog already has a
signed bundle manifest, a matcher, a revocation list and a stager. None of those
can answer the question before a bundle exists, and the catalog deliberately
lists payloads that have not been built yet.

The supplied M1 payload list is three source repositories, not three signed
bundles:

- DirtyFrag (CVE-2026-43284, page-cache)
- Root My Galaxy (CVE-2026-43499, futex/UAF)
- Root My S24 (CVE-2026-43499 / CVE-2026-98260, futex/UAF)

Each repository covers several devices, and most publish a kernel *version*
(`6.1.145`) rather than the exact `uname -r` release (`6.1.145-android14-11-…`)
that the runtime matches on. Flattening each device into an entry with invented
suffixes would create false exact targets. Dropping the devices would discard
the supplied data.

## Decision

### Index shape

`catalog/payload-index.json` is **source-level**. One entry names one upstream
payload, and its `targets` array carries the published per-device data:

```json
{
  "id": "org.opencrown.payload.example",
  "family": "futex-uaf",
  "status": "available",
  "source": "example",
  "targets": [
    {
      "model": "SM-A536E",
      "kernel_build": "5.10.237-android12-9-31999025-abA536EXXSNGZG3",
      "kernel_version": "5.10.237",
      "build": "A536EXXSNGZG3",
      "android": ">=31",
      "abi": "arm64-v8a"
    }
  ]
}
```

The index is **not** a bundle manifest and grants nothing. It is data used to
list and explain, exactly as ADR 0004 separates the catalog from the app.

### Exactness and missing data

- `kernel_build` is the exact string matched against the device's
  `uname -r`. It is `null` when the upstream source publishes only a kernel
  version. A null target can never match and can never be READY. The published
  `kernel_version` is preserved verbatim; it is not expanded into a guessed
  release.
- A source with no device matrix at all (DirtyFrag gates on kernel/KMI) has an
  empty `targets` array. It is listed, and it reports `no_match`.
- `status` is `available`, `pending` or `retired`, copying the source's own
  claim in `source_status`; we do not upgrade a source claim. A target may
  override the payload status when one row of a source is still in progress.
- No entry is synthesized for the M1 A52. None of the supplied sources targets
  SM-A528B, and a fake target would make the index say something false. The A52
  is added to `catalog/supported-devices.json` as `example`; the probe therefore
  says "recognised, no payload", while every indexed payload says `no_match`.

### Readiness contract

`runtime::readiness` is a pure, read-only evaluator. It takes the index, a
bundle root, the device facts, the app's agent version, pinned public keys and a
revocation list. It never stages files, mints a token, spawns a process, or
requests root.

The first failing gate wins, in this order:

1. `no_match` — no target matches model, exact kernel build, ABI and SDK.
2. `not_available` — a target matches but its effective index status is
   `pending` or `retired`. This reason is added by this ADR; the M1 brief's list
   did not include it, and silently reporting `no_match` or `no_bundle` would
   hide a deliberate decision.
3. `no_bundle` — no directory at `<bundles>/<id>`.
4. `invalid_manifest` — the manifest is missing, unreadable, invalid, or its id
   or family disagrees with the index.
5. `no_match` — the bundle manifest is valid but does not target this device.
6. `revoked` — the manifest id + version is on the revocation list.
7. `bad_signature` — no pinned key verifies `manifest.sig`.
8. `artifact_mismatch` — an artifact is missing, has the wrong size, or has the
   wrong SHA-256.
9. `agent_too_old` — `manifest.min_agent` is greater than the app's agent
   version.
10. `ready` — all of the above hold.

`ready` is a statement about data on disk. It is not a statement that the
payload has ever been run, that the exploit works, or that the bundle is
supported. On the M1 A52, no payload is READY.

### Pinned key

The app pins catalog keys in `catalog/catalog-keys.json`, copied into assets at
build time, and passes the hex keys to the FFI. `prepare` no longer trusts a
`public_key.hex` inside the bundle it is verifying. M1 pins only the throwaway
example key because ADR 0012's offline ceremony has not happened; that key is
labelled as such and cannot be mistaken for a release root.

## Consequences

**Positive:** the supplied sources are represented without guessed kernels;
readiness is testable on the host and cannot execute anything; pending data is
visible instead of disappearing; the app can distinguish "unknown device" from
"recognised device with no matching payload".

**Negative:** most Root My Galaxy targets cannot become READY until an exact
`uname -r` is recorded for that build. That is the correct state, not a bug: the
project has not run those payloads and does not know the string. DirtyFrag is
listed but never matches, because its source declares no model target.

## Alternatives considered

- **One flat entry per device with a guessed kernel suffix** — rejected: it
  invents exact targets, which is the one thing the matcher must not do.
- **Exclude sources without exact targets** — rejected: the user supplied them
  and "listed but not ready" is a valid, useful state.
- **Make readiness call `prepare` and catch errors** — rejected: `prepare`
  stages artifacts and mints a token, both forbidden for a probe.
