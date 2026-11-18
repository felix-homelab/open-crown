# Catalog tooling

Three tools, none implemented yet:

1. **`sign`** — canonicalise a manifest, sign it with a delegated key, write
   `manifest.sig`.
2. **`verify`** — re-run the same checks the runtime and agent perform, so a
   release can be audited without a phone.
3. **`log-client`** — publish and query the append-only transparency log.

They are small enough to be one crate; it lands in Phase 4 so the runtime and
the release process share a single verifier.
