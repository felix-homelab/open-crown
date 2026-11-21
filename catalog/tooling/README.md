# Catalog tooling

| Tool | Status | Purpose |
| --- | --- | --- |
| [sign.py](sign.py) | implemented | Ed25519 keygen, sign, verify |
| [revocation.py](revocation.py) | implemented | build and check the revocation list |
| [transparency.py](transparency.py) | implemented | append-only hash-chained log of digests |

## sign.py

    python3 catalog/tooling/sign.py keygen --out-dir keys/
    python3 catalog/tooling/sign.py sign --manifest m.json --key keys/private.hex --out manifest.sig
    python3 catalog/tooling/sign.py verify --manifest m.json --sig manifest.sig --pubkey keys/public.hex

The signature covers SHA-256 of the canonical manifest, the same rule the Rust
runtime verifies. `catalog/examples/signed/` is a committed end-to-end check:
Python signs it, Rust verifies and stages it in `runtime/tests/signed_example.rs`.

## revocation.py

    python3 catalog/tooling/revocation.py add --list revoked.json --id org.example --version 1.0.0 --reason "panics"
    python3 catalog/tooling/revocation.py check --list revoked.json --id org.example --version 1.0.0

The runtime takes the file as JSON and refuses a revoked id+version before
staging.

## transparency.py

    python3 catalog/tooling/transparency.py append --log transparency.jsonl --digest <64 hex>
    python3 catalog/tooling/transparency.py verify --log transparency.jsonl

Each entry commits to its predecessor, so an edit breaks every later hash.

The example key is a throwaway. The real catalog key is generated offline and
never committed.
