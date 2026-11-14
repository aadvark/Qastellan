"""Reading signature sources and writing Qastellan's compact definition files.

Hash table file format ("QHS1"), all integers big-endian:

    magic      4 bytes   b"QHS1"
    count      uint32    number of records
    records    count x 12 bytes, sorted ascending by prefix:
                 prefix   uint64   first 8 bytes of the hash
                 name_id  uint32   index into names.txt (0-based line number)

Only the first 8 bytes of each hash are kept. With a few million entries the
chance of an innocent file matching by accident is about 1 in 10^12, and it
cuts the download to a quarter of full SHA-256 hashes.
"""

from __future__ import annotations

import io
import re
import struct
import tarfile
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, Iterator

MAGIC = b"QHS1"
RECORD = struct.Struct(">QI")
HEADER = struct.Struct(">4sI")

HEX = re.compile(r"^[0-9a-fA-F]+$")


@dataclass(frozen=True)
class Signature:
    """One known-bad hash, as found in a source."""

    algo: str  # "md5", "sha1" or "sha256"
    digest: str  # lowercase hex
    name: str  # detection name shown to the user
    source: str


def algo_for(digest: str) -> str | None:
    return {32: "md5", 40: "sha1", 64: "sha256"}.get(len(digest))


def make_signature(digest: str, name: str, source: str) -> Signature | None:
    digest = digest.strip().lower()
    if not HEX.match(digest):
        return None
    algo = algo_for(digest)
    if algo is None:
        return None
    return Signature(algo, digest, name.strip() or "Unknown", source)


# --------------------------------------------------------------------------- ClamAV


def _clamav_members(path: Path) -> Iterator[tuple[str, bytes]]:
    """Yield (member name, bytes) from a ClamAV .cvd or .cld database."""
    data = path.read_bytes()
    if not data.startswith(b"ClamAV-VDB:"):
        raise ValueError(f"{path} is not a ClamAV database")
    body = data[512:]  # fixed 512-byte text header
    with tarfile.open(fileobj=io.BytesIO(body), mode="r:*") as tar:
        for member in tar.getmembers():
            if not member.isfile():
                continue
            fh = tar.extractfile(member)
            if fh is not None:
                yield member.name, fh.read()


def read_clamav(path: Path, name_prefixes: tuple[str, ...] = ("Andr.",)) -> Iterator[Signature]:
    """Hash signatures for Android threats from a ClamAV database.

    .hdb lines are  MD5:size:name
    .hsb lines are  SHA1-or-SHA256:size:name[:min_flevel]
    """
    for member, raw in _clamav_members(path):
        if not member.endswith((".hdb", ".hsb")):
            continue
        for line in raw.decode("utf-8", "replace").splitlines():
            parts = line.split(":")
            if len(parts) < 3:
                continue
            digest, _size, name = parts[0], parts[1], parts[2]
            if name_prefixes and not name.startswith(name_prefixes):
                continue
            sig = make_signature(digest, name, "clamav")
            if sig:
                yield sig


# --------------------------------------------------------------------------- MalwareBazaar


def read_malwarebazaar_csv(lines: Iterable[str]) -> Iterator[Signature]:
    """Rows from MalwareBazaar's CSV export, keeping Android packages only.

    Columns (quoted, comma separated): first_seen, sha256, md5, sha1, reporter,
    file_name, file_type_guess, mime_type, signature, clamav, vtpercent,
    imphash, ssdeep, tlsh. Comment lines start with '#'.
    """
    import csv

    rows = csv.reader(line for line in lines if line.strip() and not line.startswith("#"))
    for row in rows:
        if len(row) < 9:
            continue
        cells = [c.strip().strip('"').strip() for c in row]
        file_type = cells[6].lower()
        if file_type != "apk":
            continue
        family = cells[8] if cells[8] and cells[8] != "n/a" else "Generic"
        name = f"Android.{family}"
        for digest in (cells[1], cells[2]):
            sig = make_signature(digest, name, "malwarebazaar")
            if sig:
                yield sig


def read_malwarebazaar_api(payload: dict) -> Iterator[Signature]:
    """Entries from the MalwareBazaar API (query=get_file_type, file_type=apk)."""
    for item in payload.get("data") or []:
        family = item.get("signature") or "Generic"
        name = f"Android.{family}"
        for key in ("sha256_hash", "md5_hash"):
            sig = make_signature(item.get(key) or "", name, "malwarebazaar")
            if sig:
                yield sig


# --------------------------------------------------------------------------- STIX2 (MVT / Amnesty)

STIX_SHA256 = re.compile(r"file:hashes\.(?:'SHA-256'|sha256|\"SHA-256\")\s*=\s*'([0-9a-fA-F]{64})'")
STIX_APP_ID = re.compile(r"app:id\s*=\s*'([A-Za-z0-9_.]+)'")


def read_stix(bundle: dict, default_name: str) -> tuple[list[Signature], list[tuple[str, str]]]:
    """Return (hash signatures, (package name, threat name)) from a STIX2 bundle."""
    objects = bundle.get("objects") or []
    malware_names = {o["id"]: o.get("name", default_name) for o in objects if o.get("type") == "malware"}
    indicator_to_malware: dict[str, str] = {}
    for o in objects:
        if o.get("type") == "relationship" and o.get("relationship_type") == "indicates":
            indicator_to_malware[o.get("source_ref", "")] = malware_names.get(o.get("target_ref", ""), default_name)

    hashes: list[Signature] = []
    packages: list[tuple[str, str]] = []
    for o in objects:
        if o.get("type") != "indicator":
            continue
        pattern = o.get("pattern", "")
        name = "Spyware." + indicator_to_malware.get(o.get("id", ""), default_name).replace(" ", "")
        for digest in STIX_SHA256.findall(pattern):
            sig = make_signature(digest, name, "mvt")
            if sig:
                hashes.append(sig)
        for pkg in STIX_APP_ID.findall(pattern):
            packages.append((pkg, name))
    return hashes, packages


# --------------------------------------------------------------------------- writing


def prefix_of(digest: str) -> int:
    return int(digest[:16], 16)


def build_tables(signatures: Iterable[Signature]) -> tuple[dict[str, list[tuple[int, int]]], list[str]]:
    """Group signatures by algorithm into sorted (prefix, name_id) tables.

    Duplicate prefixes keep the first name seen, so sources listed first win.
    """
    names: list[str] = []
    name_ids: dict[str, int] = {}
    tables: dict[str, dict[int, int]] = {"md5": {}, "sha1": {}, "sha256": {}}
    for sig in signatures:
        if sig.name not in name_ids:
            name_ids[sig.name] = len(names)
            names.append(sig.name)
        tables[sig.algo].setdefault(prefix_of(sig.digest), name_ids[sig.name])
    return {algo: sorted(t.items()) for algo, t in tables.items()}, names


def write_table(path: Path, records: list[tuple[int, int]]) -> None:
    with path.open("wb") as fh:
        fh.write(HEADER.pack(MAGIC, len(records)))
        for prefix, name_id in records:
            fh.write(RECORD.pack(prefix, name_id))


def read_table(path: Path) -> list[tuple[int, int]]:
    data = path.read_bytes()
    magic, count = HEADER.unpack_from(data, 0)
    if magic != MAGIC:
        raise ValueError("not a QHS1 file")
    return [RECORD.unpack_from(data, HEADER.size + i * RECORD.size) for i in range(count)]


def lookup(records: list[tuple[int, int]], digest: str) -> int | None:
    """Binary search, mirroring what the app does on the phone."""
    target = prefix_of(digest.lower())
    lo, hi = 0, len(records) - 1
    while lo <= hi:
        mid = (lo + hi) // 2
        value = records[mid][0]
        if value == target:
            return records[mid][1]
        if value < target:
            lo = mid + 1
        else:
            hi = mid - 1
    return None


# --------------------------------------------------------------------------- Exodus Privacy trackers

_REGEX_LEFTOVER = re.compile(r"[\\^$*+?()\[\]{}]")


def read_exodus_trackers(payload: dict) -> list[tuple[str, str, list[str]]]:
    """(name, category, class-name prefixes) from Exodus Privacy's tracker API.

    Code signatures are regular expressions over Java class names, nearly all
    plain prefixes such as "com.adform.sdk.". Those are kept (escaped dots
    unescaped); anything still containing regex syntax is skipped rather than
    risking false matches on the phone.
    """
    out: list[tuple[str, str, list[str]]] = []
    trackers = (payload.get("trackers") or {}).values()
    for t in trackers:
        name = (t.get("name") or "").strip().replace("\t", " ")
        sig = (t.get("code_signature") or "").strip()
        if not name or not sig:
            continue
        prefixes = []
        for part in sig.split("|"):
            part = part.strip().replace("\\.", ".")
            if len(part) < 4 or _REGEX_LEFTOVER.search(part):
                continue
            prefixes.append(part)
        if prefixes:
            cats = t.get("categories") or []
            out.append((name, (cats[0] if cats else "Tracker").replace("\t", " "), sorted(set(prefixes))))
    return sorted(out, key=lambda x: x[0].lower())
