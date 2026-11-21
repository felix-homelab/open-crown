#!/usr/bin/env python3
"""Sign and verify payload manifests with Ed25519.

The signature covers SHA-256 of the canonical manifest, matching the runtime
(docs/04-payload-system.md section 7). The private key is a raw 32-byte seed in
hex; it is never read from the repository.
"""

import argparse
import os
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "tools" / "lib"))

from opencrown_tools.manifest import load, signing_input, validate


def _crypto():
    try:
        from cryptography.exceptions import InvalidSignature
        from cryptography.hazmat.primitives.asymmetric.ed25519 import (
            Ed25519PrivateKey,
            Ed25519PublicKey,
        )
    except ImportError as exc:  # pragma: no cover - depends on the environment
        raise SystemExit(
            "cryptography is required: pip install -r tools/requirements.txt"
        ) from exc
    return Ed25519PrivateKey, Ed25519PublicKey, InvalidSignature


def generate_keypair() -> tuple[str, str]:
    """Return (private seed hex, public key hex)."""
    Ed25519PrivateKey, _, _ = _crypto()
    key = Ed25519PrivateKey.generate()
    private_hex = key.private_bytes_raw().hex()
    public_hex = key.public_key().public_bytes_raw().hex()
    return private_hex, public_hex


def sign_manifest(manifest: dict, private_hex: str) -> bytes:
    Ed25519PrivateKey, _, _ = _crypto()
    issues = validate(manifest)
    if issues:
        raise ValueError("; ".join(issues))
    key = Ed25519PrivateKey.from_private_bytes(bytes.fromhex(private_hex))
    return key.sign(signing_input(manifest))


def verify_manifest(manifest: dict, signature: bytes, public_hex: str) -> None:
    _, Ed25519PublicKey, InvalidSignature = _crypto()
    issues = validate(manifest)
    if issues:
        raise ValueError("; ".join(issues))
    key = Ed25519PublicKey.from_public_bytes(bytes.fromhex(public_hex))
    try:
        key.verify(signature, signing_input(manifest))
    except InvalidSignature as exc:
        raise ValueError("signature does not cover this manifest") from exc


def _cmd_keygen(args: argparse.Namespace) -> int:
    private_hex, public_hex = generate_keypair()
    out = Path(args.out_dir)
    out.mkdir(parents=True, exist_ok=True)
    private_path = out / "private.hex"
    public_path = out / "public.hex"
    private_path.write_text(private_hex + "\n", encoding="utf-8")
    os.chmod(private_path, 0o600)
    public_path.write_text(public_hex + "\n", encoding="utf-8")
    print(f"private: {private_path}")
    print(f"public:  {public_path}")
    print("Keep the private key offline. It is the catalog root.")
    return 0


def _cmd_sign(args: argparse.Namespace) -> int:
    manifest = load(args.manifest)
    private_hex = Path(args.key).read_text(encoding="utf-8").strip()
    signature = sign_manifest(manifest, private_hex)
    Path(args.out).write_bytes(signature)
    print(f"signed {manifest.get('id')} {manifest.get('version')} -> {args.out}")
    return 0


def _cmd_verify(args: argparse.Namespace) -> int:
    manifest = load(args.manifest)
    signature = Path(args.sig).read_bytes()
    public_hex = Path(args.pubkey).read_text(encoding="utf-8").strip()
    try:
        verify_manifest(manifest, signature, public_hex)
    except ValueError as exc:
        print(f"invalid: {exc}")
        return 1
    print("signature valid")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)

    keygen = sub.add_parser("keygen", help="create a catalog key pair")
    keygen.add_argument("--out-dir", required=True)
    keygen.set_defaults(func=_cmd_keygen)

    sign = sub.add_parser("sign", help="sign a manifest")
    sign.add_argument("--manifest", required=True)
    sign.add_argument("--key", required=True, help="private seed, hex")
    sign.add_argument("--out", required=True)
    sign.set_defaults(func=_cmd_sign)

    verify = sub.add_parser("verify", help="verify a manifest signature")
    verify.add_argument("--manifest", required=True)
    verify.add_argument("--sig", required=True)
    verify.add_argument("--pubkey", required=True)
    verify.set_defaults(func=_cmd_verify)

    args = parser.parse_args()
    return args.func(args)


if __name__ == "__main__":
    raise SystemExit(main())
