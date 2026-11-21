# Catalog tooling

| Tool | Status | Purpose |
| --- | --- | --- |
| [sign.py](sign.py) | implemented | Ed25519 keygen, sign, verify |
| [verify.py](verify.py) | implemented | verify a whole bundle: shape, signature, artifacts, revocation |
| [revocation.py](revocation.py) | implemented | build and check the revocation list |
| [transparency.py](transparency.py) | implemented | append-only hash-chained log of digests |
| [devices.py](devices.py) | implemented | validate the supported-device list |

## sign.py

    python3 catalog/tooling/sign.py keygen --out-dir keys/
    python3 catalog/tooling/sign.py sign --manifest m.json --key keys/private.hex --out manifest.sig
    python3 catalog/tooling/sign.py verify --manifest m.json --sig manifest.sig --pubkey keys/public.hex

The signature covers SHA-256 of the canonical manifest, the same rule the Rust
runtime verifies.

## verify.py

    python3 catalog/tooling/verify.py catalog/examples/signed
    python3 catalog/tooling/verify.py --manifest m.json

The bundle form checks the manifest rules, the signature, every artifact's size
and SHA-256, and an optional revocation list. It mirrors what the runtime does
before staging.

## revocation.py

    python3 catalog/tooling/revocation.py add --list revoked.json --id org.example --version 1.0.0 --reason "panics"
    python3 catalog/tooling/revocation.py check --list revoked.json --id org.example --version 1.0.0

## transparency.py

    python3 catalog/tooling/transparency.py append --log transparency.jsonl --digest <64 hex>
    python3 catalog/tooling/transparency.py verify --log transparency.jsonl

## devices.py

    python3 catalog/tooling/devices.py validate --list catalog/supported-devices.json
    python3 catalog/tooling/devices.py check --list catalog/supported-devices.json --model EXAMPLE-1 --kernel example-6.1.0

The example key is a throwaway. The real catalog key is generated offline and
never committed.
