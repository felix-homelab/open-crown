# Catalog tooling

| Tool | Status | Purpose |
| --- | --- | --- |
| [sign.py](sign.py) | implemented | Ed25519 keygen, sign, verify |
| verify | implemented | `sign.py verify` covers it |
| revocation | runtime-side | the runtime reads a revocations file; a builder is next |
| transparency log | not started | append-only record of payload hashes |

## sign.py

    python3 catalog/tooling/sign.py keygen --out-dir keys/
    python3 catalog/tooling/sign.py sign --manifest m.json --key keys/private.hex --out manifest.sig
    python3 catalog/tooling/sign.py verify --manifest m.json --sig manifest.sig --pubkey keys/public.hex

The signature covers SHA-256 of the canonical manifest, the same rule the Rust
runtime verifies. `catalog/examples/signed/` is a committed end-to-end check:
Python signs it, Rust verifies and stages it in `runtime/tests/signed_example.rs`.

The example key is a throwaway. The real catalog key is generated offline and
never committed.
