# Catalog

The signed, versioned collection of payloads the app can run. The rules are in
[04 — Payload system](../docs/04-payload-system.md); this file describes the
directory.

```text
catalog/
├── schema/                 payload-manifest and supported-devices schemas
├── manifests/              one <id>/<version>.json per payload, with .sig
├── examples/               fixture bundles, including a signed example
├── tooling/                signing, revocation, transparency, device list
└── supported-devices.json  the evidence-backed device list
```

## Rules

- A manifest is exact-target: model + kernel build, no wildcards.
- Capabilities are a closed enum; unknown capability is rejected.
- `entry` and `agent` resolve inside `files/`; traversal is rejected.
- Every artifact carries a SHA-256; the manifest hash is signed.
- `persistence` is `boot` and `reversible` is `reboot` in v1.
- Verification happens in the runtime and in the agent, not only in the app.

## Status

Schema, signing, revocation, the transparency log and the device list are
implemented. There is no real payload: `catalog/examples/signed/` is dummy
bytes signed with a throwaway key, and it exists to prove the Python signer and
the Rust verifier agree. The catalog ships when the supply and review gates in
[ADR 0010](../docs/adr/0010-ship-order.md) pass.
