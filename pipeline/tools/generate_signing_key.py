"""Create the definitions signing key pair (run once, on your own computer).

    python pipeline/tools/generate_signing_key.py

- Paste the PRIVATE key into the GitHub repo secret QASTELLAN_SIGNING_KEY
  (Settings > Secrets and variables > Actions). Never commit it.
- Put the PUBLIC key line into app/src/main/res/values/definitions_key.xml.
"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from qdefs.signing import generate_keypair  # noqa: E402

private_pem, public_b64 = generate_keypair()
print("=== PRIVATE KEY (GitHub secret QASTELLAN_SIGNING_KEY) ===")
print(private_pem.decode())
print("=== PUBLIC KEY (app definitions_key.xml) ===")
print(public_b64.decode())
