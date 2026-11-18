"""Open Crown research tooling.

Pure-Python core paths with no third-party dependencies. Signature
verification uses cryptography when it is installed and says so when it is not.
"""

__version__ = "0.1.0"

from . import capture, fixturediff, fixtures, manifest, speclint, vectorgren

__all__ = ["capture", "fixturediff", "fixtures", "manifest", "speclint", "vectorgren"]
