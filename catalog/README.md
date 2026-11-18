# Catalog

The signed, versioned collection of payloads the app can run. The rules are in
[04 — Payload system](../docs/04-payload-system.md); this file describes the
directory.

```text
catalog/
├── schema/                 payload-manifest.schema.json (the contract)
├── manifests/              one <id>/<version>.json per payload, with .sig
├── examples/               fixture bundles for tests, never signed for release
└── tooling/                signing, verification, transparency-log client
```

## Rules

- A manifest is exact-target: model + kernel build, no wildcards.
- Capabilities are a closed enum; unknown capability is rejected.
- `entry` and `agent` resolve inside `files/`; traversal is rejected.
- Every artifact carries a SHA-256; the manifest hash is signed.
- `persistence` is `boot` and `reversible` is `reboot` in v1.
- Verification happens in the runtime and in the agent, not only in the app.

## Status

Schema only. No manifest is signed and no tooling exists yet. Signing,
revocation and the transparency log land with Phase 4/8
([roadmap](../docs/06-roadmap.md)).
