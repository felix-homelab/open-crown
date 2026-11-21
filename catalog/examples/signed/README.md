# Signed example bundle

Structural example only: dummy files, no payload chain. It exists so the Python
signer and the Rust verifier are proven to agree.

- manifest.json is signed by catalog/tooling/sign.py.
- manifest.sig is the raw 64-byte Ed25519 signature over SHA-256 of the
  canonical manifest.
- public_key.hex is the public half of a throwaway key. The private half was
  discarded when the bundle was generated.

This key is not a catalog root and must never be trusted for a release. Used by
runtime/tests/signed_example.rs and by CI.
