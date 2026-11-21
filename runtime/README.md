# runtime

The payload runtime: everything around the chain, none of the chain. It
implements [02 — Architecture](../docs/02-architecture.md) section 2.2 and
[04 — Payload system](../docs/04-payload-system.md) sections 6–9.

## Flow

    prepare(bundle, work, device, signature, public_key, revocations)
      -> Manifest::parse        serde, deny_unknown_fields, rule checks
      -> revocation check       a revoked id+version never runs
      -> matcher::select        exact model + kernel build, Android range
      -> verify_signature       Ed25519 over SHA-256 of canonical JSON
      -> stage::stage           re-hash each artifact, 0600, private dir
      -> launch::new_token      32 random bytes, hex
      -> Prepared { manifest, staged, token }

The caller launches the staged entry or agent and must present the token in
CROWN_TOKEN. The privilege-escalation chain itself is supplied by the
maintainer; it is not in this crate.

## Invariants

- Unknown fields, capabilities and families are rejected.
- entry and agent must be declared artifacts.
- No path may escape files/, and traversal is rejected.
- Staged files are re-hashed, not trusted because the manifest was valid.
- The signature covers the canonical form, not the raw bytes.
- Staged files are 0600 inside a 0700 directory on Unix.

## Not done

- JNI or UniFFI exposure to the app.
- Revocation-list lookup and the transparency log.
- The payload chain.
