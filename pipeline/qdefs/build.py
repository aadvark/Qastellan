"""Build Qastellan's definition files from downloaded sources.

Every source is optional, so a feed that is down for a day does not stop the
build; the manifest records which sources made it in.

Example:
    python -m qdefs.build --out out --version 2026.11.15 \
        --clamav sources/main.cvd --clamav sources/daily.cvd \
        --mb-csv sources/full.csv.zip --stix-dir sources/stix \
        --extra extra_signatures.txt
"""

from __future__ import annotations

import argparse
import hashlib
import io
import json
import os
import sys
import zipfile
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable, Iterator

from . import formats
from .signing import sign

TABLE_FILES = {"md5": "md5.qhs", "sha1": "sha1.qhs", "sha256": "sha256.qhs"}


def _csv_lines(path: Path) -> Iterator[str]:
    if path.suffix == ".zip":
        with zipfile.ZipFile(path) as zf:
            for member in zf.namelist():
                with zf.open(member) as fh:
                    yield from io.TextIOWrapper(fh, encoding="utf-8", errors="replace")
    else:
        with path.open(encoding="utf-8", errors="replace") as fh:
            yield from fh


def read_extra(path: Path) -> Iterator[formats.Signature]:
    """Our own signatures: one 'hash name' pair per line, '#' for comments."""
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        digest, _, name = line.partition(" ")
        sig = formats.make_signature(digest, name, "qastellan")
        if sig:
            yield sig


def collect(args: argparse.Namespace) -> tuple[list[formats.Signature], list[tuple[str, str]], dict[str, int]]:
    signatures: list[formats.Signature] = []
    packages: list[tuple[str, str]] = []
    counts: dict[str, int] = {}

    def add(source: str, items: Iterable[formats.Signature]) -> None:
        before = len(signatures)
        try:
            signatures.extend(items)
        except Exception as exc:  # one broken feed must not stop the build
            print(f"warning: skipped {source}: {exc}", file=sys.stderr)
            del signatures[before:]
            return
        counts[source] = counts.get(source, 0) + len(signatures) - before

    # Order matters: when two sources share a hash, the first name wins.
    if args.extra:
        add("qastellan", read_extra(Path(args.extra)))
    for stix_file in sorted(Path(args.stix_dir).glob("*.stix2")) if args.stix_dir else []:
        try:
            hashes, pkgs = formats.read_stix(json.loads(stix_file.read_text(encoding="utf-8")), stix_file.stem)
        except Exception as exc:
            print(f"warning: skipped {stix_file.name}: {exc}", file=sys.stderr)
            continue
        add("mvt", hashes)
        packages.extend(pkgs)
    for cvd in args.clamav or []:
        add("clamav", formats.read_clamav(Path(cvd)))
    if args.mb_csv:
        add("malwarebazaar", formats.read_malwarebazaar_csv(_csv_lines(Path(args.mb_csv))))
    if args.mb_api_json:
        add("malwarebazaar", formats.read_malwarebazaar_api(json.loads(Path(args.mb_api_json).read_text())))
    return signatures, packages, counts


def build(args: argparse.Namespace) -> dict:
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)

    signatures, packages, counts = collect(args)
    tables, names = formats.build_tables(signatures)

    files: dict[str, dict] = {}
    for algo, records in tables.items():
        path = out / TABLE_FILES[algo]
        formats.write_table(path, records)
        files[path.name] = {"records": len(records)}

    (out / "names.txt").write_text("\n".join(names) + "\n", encoding="utf-8")
    files["names.txt"] = {"records": len(names)}

    unique_pkgs = sorted(dict(packages).items())
    (out / "packages.txt").write_text("".join(f"{p}\t{n}\n" for p, n in unique_pkgs), encoding="utf-8")
    files["packages.txt"] = {"records": len(unique_pkgs)}

    for name, meta in files.items():
        data = (out / name).read_bytes()
        meta["sha256"] = hashlib.sha256(data).hexdigest()
        meta["size"] = len(data)

    manifest = {
        "format": 1,
        "version": args.version,
        "created": datetime.now(timezone.utc).replace(microsecond=0).isoformat(),
        "sources": counts,
        "files": files,
    }
    manifest_bytes = json.dumps(manifest, indent=2, sort_keys=True).encode("utf-8")
    (out / "manifest.json").write_bytes(manifest_bytes)

    key = os.environ.get(args.key_env, "").encode("utf-8")
    if key:
        (out / "manifest.sig").write_bytes(sign(manifest_bytes, key))
    elif not args.allow_unsigned:
        raise SystemExit(f"signing key missing: set ${args.key_env} or pass --allow-unsigned for local tests")
    return manifest


def parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--out", required=True)
    p.add_argument("--version", default=datetime.now(timezone.utc).strftime("%Y.%m.%d"))
    p.add_argument("--clamav", action="append", help="ClamAV .cvd/.cld file (repeatable)")
    p.add_argument("--mb-csv", help="MalwareBazaar CSV export (.csv or .zip)")
    p.add_argument("--mb-api-json", help="Saved MalwareBazaar API response")
    p.add_argument("--stix-dir", help="Folder of .stix2 indicator files")
    p.add_argument("--extra", help="Qastellan's own signatures file")
    p.add_argument("--key-env", default="QASTELLAN_SIGNING_KEY")
    p.add_argument("--allow-unsigned", action="store_true")
    return p


def main(argv: list[str] | None = None) -> None:
    manifest = build(parser().parse_args(argv))
    print(json.dumps({k: manifest[k] for k in ("version", "sources")}, indent=2))


if __name__ == "__main__":
    main()
