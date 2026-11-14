import io
import json
import tarfile
import zipfile
from pathlib import Path

import pytest

from qdefs import build, formats, signing

ROOT = Path(__file__).resolve().parents[1]
EICAR_SHA256 = "275a021bbfb6489e54d471899f7db9d1663fc695ec2fe2a2c4538aabf651fd0f"
EICAR_MD5 = "44d88612fea8a8f36de82e1278abb02f"


def make_cvd(path: Path, members: dict[str, str]) -> None:
    buf = io.BytesIO()
    with tarfile.open(fileobj=buf, mode="w:gz") as tar:
        for name, text in members.items():
            data = text.encode()
            info = tarfile.TarInfo(name)
            info.size = len(data)
            tar.addfile(info, io.BytesIO(data))
    header = b"ClamAV-VDB:test".ljust(512, b" ")
    path.write_bytes(header + buf.getvalue())


@pytest.fixture()
def sources(tmp_path: Path) -> Path:
    make_cvd(
        tmp_path / "main.cvd",
        {
            "main.hdb": "\n".join(
                [
                    "aa" * 16 + ":1234:Andr.Trojan.Agent-1",
                    "bb" * 16 + ":99:Win.Trojan.NotAndroid-2",
                    "not-a-hash:1:Andr.Broken",
                ]
            ),
            "main.hsb": "cc" * 32 + ":555:Andr.Spyware.Thing-3:73",
            "main.ndb": "ignored",
        },
    )
    csv_text = "\n".join(
        [
            "# MalwareBazaar export",
            '"2026-11-01 10:00:00", "' + "dd" * 32 + '", "' + "ee" * 16 + '", "' + "ff" * 20
            + '", "x", "a.apk", "apk", "application/zip", "Anatsa", "", "", "", "", ""',
            '"2026-11-01 10:00:00", "' + "11" * 32 + '", "' + "22" * 16 + '", "' + "33" * 20
            + '", "x", "a.exe", "exe", "application/x-dosexec", "AgentTesla", "", "", "", "", ""',
        ]
    )
    with zipfile.ZipFile(tmp_path / "full.csv.zip", "w") as zf:
        zf.writestr("full.csv", csv_text)
    stix = {
        "type": "bundle",
        "objects": [
            {"type": "malware", "id": "malware--1", "name": "Predator"},
            {"type": "indicator", "id": "indicator--1", "pattern": "[app:id = 'com.evil.spy']"},
            {"type": "indicator", "id": "indicator--2", "pattern": "[file:hashes.sha256 = '" + "44" * 32 + "']"},
            {"type": "relationship", "relationship_type": "indicates", "source_ref": "indicator--1", "target_ref": "malware--1"},
            {"type": "relationship", "relationship_type": "indicates", "source_ref": "indicator--2", "target_ref": "malware--1"},
        ],
    }
    (tmp_path / "stix").mkdir()
    (tmp_path / "stix" / "001-predator.stix2").write_text(json.dumps(stix))
    return tmp_path


def run_build(sources: Path, out: Path, *extra: str) -> dict:
    args = [
        "--out", str(out), "--version", "2026.11.15",
        "--clamav", str(sources / "main.cvd"),
        "--mb-csv", str(sources / "full.csv.zip"),
        "--stix-dir", str(sources / "stix"),
        "--extra", str(ROOT / "extra_signatures.txt"),
        *extra,
    ]
    ns = build.parser().parse_args(args)
    return build.build(ns)


def names(out: Path) -> list[str]:
    return (out / "names.txt").read_text().splitlines()


def test_android_hashes_found_and_others_dropped(sources: Path, tmp_path: Path) -> None:
    out = tmp_path / "out"
    manifest = run_build(sources, out, "--allow-unsigned")
    md5 = formats.read_table(out / "md5.qhs")
    sha256 = formats.read_table(out / "sha256.qhs")
    n = names(out)

    assert n[formats.lookup(md5, "aa" * 16)] == "Andr.Trojan.Agent-1"
    assert formats.lookup(md5, "bb" * 16) is None  # Windows signature filtered out
    assert n[formats.lookup(sha256, "cc" * 32)] == "Andr.Spyware.Thing-3"
    assert n[formats.lookup(sha256, "dd" * 32)] == "Android.Anatsa"
    assert formats.lookup(sha256, "11" * 32) is None  # .exe sample filtered out
    assert n[formats.lookup(sha256, "44" * 32)] == "Spyware.Predator"
    assert n[formats.lookup(sha256, EICAR_SHA256)] == "Qastellan.Test.EICAR"
    assert n[formats.lookup(md5, EICAR_MD5)] == "Qastellan.Test.EICAR"
    assert (out / "packages.txt").read_text() == "com.evil.spy\tSpyware.Predator\n"
    assert manifest["sources"] == {"qastellan": 2, "mvt": 1, "clamav": 2, "malwarebazaar": 2}


def test_tables_are_sorted_and_unique(sources: Path, tmp_path: Path) -> None:
    out = tmp_path / "out"
    run_build(sources, out, "--allow-unsigned")
    for name in ("md5.qhs", "sha1.qhs", "sha256.qhs"):
        prefixes = [p for p, _ in formats.read_table(out / name)]
        assert prefixes == sorted(set(prefixes))


def test_manifest_lists_file_hashes_and_is_signed(sources: Path, tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> None:
    private_pem, public_b64 = signing.generate_keypair()
    monkeypatch.setenv("QASTELLAN_SIGNING_KEY", private_pem.decode())
    out = tmp_path / "out"
    manifest = run_build(sources, out)

    raw = (out / "manifest.json").read_bytes()
    assert signing.verify(raw, (out / "manifest.sig").read_bytes(), public_b64)
    assert not signing.verify(raw + b" ", (out / "manifest.sig").read_bytes(), public_b64)
    import hashlib

    for name, meta in manifest["files"].items():
        assert hashlib.sha256((out / name).read_bytes()).hexdigest() == meta["sha256"]


def test_refuses_to_publish_unsigned(sources: Path, tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.delenv("QASTELLAN_SIGNING_KEY", raising=False)
    with pytest.raises(SystemExit):
        run_build(sources, tmp_path / "out")


def test_broken_source_is_skipped(sources: Path, tmp_path: Path) -> None:
    (sources / "main.cvd").write_bytes(b"garbage")
    manifest = run_build(sources, tmp_path / "out", "--allow-unsigned")
    assert "clamav" not in manifest["sources"]
    assert manifest["sources"]["malwarebazaar"] == 2


def test_mvt_index_parsing() -> None:
    from qdefs.fetch import stix_urls

    yaml_text = """
indicators:
  - type: github
    name: Example
    github: {owner: AmnestyTech, repo: investigations, branch: master, path: 2021-07-18_nso/pegasus.stix2}
  - type: other
    download_url: https://example.org/x.txt
"""
    assert stix_urls(yaml_text) == [
        ("000-pegasus.stix2", "https://raw.githubusercontent.com/AmnestyTech/investigations/master/2021-07-18_nso/pegasus.stix2")
    ]


def test_exodus_trackers_become_prefix_lists(tmp_path: Path, sources: Path) -> None:
    payload = {"trackers": {
        "1": {"name": "Adform", "code_signature": "com.adform.sdk.", "categories": ["Advertisement"]},
        "2": {"name": "Odd", "code_signature": "^com\\.odd\\.(a|b)$", "categories": []},
        "3": {"name": "Adjoe", "code_signature": "io.adjoe.sdk.|io.adjoe.protection.", "categories": ["Advertisement"]},
        "4": {"name": "Empty", "code_signature": "", "categories": []},
    }}
    (sources / "trackers.json").write_text(json.dumps(payload))
    out = tmp_path / "out"
    manifest = run_build(sources, out, "--allow-unsigned", "--trackers", str(sources / "trackers.json"))
    lines = (out / "trackers.txt").read_text().splitlines()
    assert lines == [
        "Adform\tAdvertisement\tcom.adform.sdk.",
        "Adjoe\tAdvertisement\tio.adjoe.protection.|io.adjoe.sdk.",
    ]
    assert manifest["sources"]["exodus"] == 2
    assert "trackers.txt" in manifest["files"]
