"""ECDSA P-256 signatures for the definitions manifest.

The app verifies the manifest with the public key built into it, then checks
each downloaded file against the SHA-256 listed in the manifest. ECDSA P-256 is
used because every Android version can verify it without extra libraries.
"""

from __future__ import annotations

import base64

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec


def generate_keypair() -> tuple[bytes, bytes]:
    """Return (private key PEM, public key DER as base64)."""
    key = ec.generate_private_key(ec.SECP256R1())
    private_pem = key.private_bytes(
        serialization.Encoding.PEM,
        serialization.PrivateFormat.PKCS8,
        serialization.NoEncryption(),
    )
    public_der = key.public_key().public_bytes(
        serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo
    )
    return private_pem, base64.b64encode(public_der)


def sign(data: bytes, private_pem: bytes) -> bytes:
    key = serialization.load_pem_private_key(private_pem, password=None)
    if not isinstance(key, ec.EllipticCurvePrivateKey):
        raise ValueError("signing key must be an EC P-256 key")
    return key.sign(data, ec.ECDSA(hashes.SHA256()))


def verify(data: bytes, signature: bytes, public_der_b64: bytes) -> bool:
    key = serialization.load_der_public_key(base64.b64decode(public_der_b64))
    try:
        key.verify(signature, data, ec.ECDSA(hashes.SHA256()))  # type: ignore[union-attr]
        return True
    except Exception:
        return False
