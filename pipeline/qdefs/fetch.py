"""Download the free signature feeds that don't need special tools.

ClamAV databases are fetched separately with the official `cvdupdate` tool,
which respects ClamAV's mirror rules (see the workflow).
"""

from __future__ import annotations

import argparse
import os
import sys
import urllib.request
from pathlib import Path

MVT_INDEX = "https://raw.githubusercontent.com/mvt-project/mvt-indicators/main/indicators.yaml"
MB_FULL_CSV = "https://bazaar.abuse.ch/export/csv/full/"
USER_AGENT = "Qastellan-definitions/1 (+https://github.com/aadvark/Qastellan)"


def download(url: str, dest: Path, headers: dict[str, str] | None = None) -> bool:
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT, **(headers or {})})
    try:
        with urllib.request.urlopen(req, timeout=300) as resp, dest.open("wb") as fh:
            while chunk := resp.read(1 << 20):
                fh.write(chunk)
        return True
    except Exception as exc:
        print(f"warning: could not download {url}: {exc}", file=sys.stderr)
        dest.unlink(missing_ok=True)
        return False


def stix_urls(index_yaml: str) -> list[tuple[str, str]]:
    """(file name, URL) for each STIX2 file listed in MVT's indicators.yaml."""
    import yaml

    doc = yaml.safe_load(index_yaml) or {}
    urls: list[tuple[str, str]] = []
    for i, entry in enumerate(doc.get("indicators") or []):
        url = None
        if entry.get("type") == "github" and entry.get("github"):
            g = entry["github"]
            url = f"https://raw.githubusercontent.com/{g['owner']}/{g['repo']}/{g['branch']}/{g['path']}"
        elif entry.get("download_url"):
            url = entry["download_url"]
        if url and url.endswith(".stix2"):
            stem = Path(url).stem
            urls.append((f"{i:03d}-{stem}.stix2", url))
    return urls


def main(argv: list[str] | None = None) -> None:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--dest", required=True)
    args = p.parse_args(argv)
    dest = Path(args.dest)
    (dest / "stix").mkdir(parents=True, exist_ok=True)

    # MalwareBazaar: an Auth-Key is free from abuse.ch; kept in a repo secret.
    mb_headers = {"Auth-Key": os.environ["MB_AUTH_KEY"]} if os.environ.get("MB_AUTH_KEY") else {}
    download(MB_FULL_CSV, dest / "malwarebazaar_full.csv.zip", mb_headers)

    index = dest / "mvt_indicators.yaml"
    if download(MVT_INDEX, index):
        for name, url in stix_urls(index.read_text(encoding="utf-8")):
            download(url, dest / "stix" / name)


if __name__ == "__main__":
    main()
