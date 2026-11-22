# Catalog

The signed, versioned collection of payloads the app can run. The rules are in
[04 — Payload system](../docs/04-payload-system.md); this file describes the
directory.

```text
catalog/
├── schema/                 payload-manifest, payload-index and device schemas
├── manifests/              one <id>/<version>.json per payload, with .sig
├── examples/               fixture bundles, including a signed example
├── tooling/                signing, revocation, transparency, index, device list
├── payload-index.json      source-level payloads and their published targets
├── catalog-keys.json       the app's pinned catalog keys (test key in M1)
├── revocations.json        the shipped revocation list
└── supported-devices.json  the evidence-backed device list
```

External payload artifacts live outside `catalog/`, under
part of the MIT tree (ADR 0015).

## Rules

- A manifest is exact-target: model + kernel build, no wildcards.
- Capabilities are a closed enum; unknown capability is rejected.
- `entry` and `agent` resolve inside `files/`; traversal is rejected.
- Every artifact carries a SHA-256; the manifest hash is signed.
- `persistence` is `boot` and `reversible` is `reboot` in v1.
- Verification happens in the runtime and in the agent, not only in the app.

## Status

Schema, signing, revocation, the transparency log, the device list and the
source-level payload index are implemented. The index lists the M1 sources
(DirtyFrag, Root My Galaxy, Root My S24) without inventing exact kernels; a
target whose upstream only publishes a kernel version has `kernel_build: null`
and can never be READY (ADR 0014).

`catalog/examples/signed/` is dummy bytes signed with a throwaway key; it
exists to prove the Python signer and the Rust verifier agree. The first real
pinned in `catalog-keys.json`; the offline key ceremony in ADR 0012 has not
happened. The catalog ships when the supply and review gates in
[ADR 0010](../docs/adr/0010-ship-order.md) pass.
