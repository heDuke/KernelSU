#!/usr/bin/env python3
"""Bind production Manager UI payloads to their checked-out source."""

import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
from zipfile import ZipFile

PACKAGE = "me.weishu.kernelsu.husky.fork"
IDENTITY_ASSET = "assets/husky-build.json"


def write_identity(output, expected_commit):
    def git(*args):
        return subprocess.check_output(["git", *args], text=True).strip()

    commit = git("rev-parse", "HEAD")
    if not re.fullmatch(r"[0-9a-f]{40}", expected_commit) or commit != expected_commit:
        raise ValueError("Checkout does not match the requested production source")
    identity = {"schema_version": 1, "source_commit": commit, "package": PACKAGE,
                "version_code": 30000 + int(git("rev-list", "--count", "HEAD")),
                "version_name": git("describe", "--tags", "--always"), "channel": "production"}
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(identity, indent=2) + "\n", encoding="utf-8")


def verify_source(apk, commit, version_code, version_name):
    with ZipFile(apk) as archive:
        if archive.namelist().count(IDENTITY_ASSET) != 1:
            raise ValueError("APK must contain one production source identity")
        identity = json.loads(archive.read(IDENTITY_ASSET))
    expected = {"schema_version": 1, "source_commit": commit, "package": PACKAGE,
                "version_code": version_code, "version_name": version_name, "channel": "production"}
    if identity != expected:
        raise ValueError("APK build identity differs from the production source or APK version")
    return identity


def manager_payloads(apk):
    with ZipFile(apk) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)):
            raise ValueError("APK contains duplicate ZIP entries")
        selected = sorted(name for name in names if name in ("AndroidManifest.xml", "resources.arsc")
                          or name.startswith(("res/", "assets/")) or re.fullmatch(r"classes(?:\d+)?\.dex", name))
        if not {"AndroidManifest.xml", "resources.arsc", "classes.dex"}.issubset(selected):
            raise ValueError("APK is missing Manager code or resources")
        return [{"name": name, "size": len(data), "sha256": hashlib.sha256(data).hexdigest()}
                for name in selected for data in [archive.read(name)]]


def single_apk(directory):
    candidates = sorted(directory.rglob("*.apk"))
    if len(candidates) != 1:
        raise ValueError("Expected exactly one Manager APK in the current build artifact")
    return candidates[0]


def payload_fingerprint(payloads):
    encoded = json.dumps(payloads, sort_keys=True, separators=(",", ":")).encode()
    return hashlib.sha256(encoded).hexdigest()


def verify_repack(source, result):
    expected = manager_payloads(source)
    if manager_payloads(result) != expected:
        raise ValueError("Repacked APK changed Manager code, resources, manifest or assets")
    return expected


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", type=Path)
    parser.add_argument("--commit", required=True)
    args = parser.parse_args()
    write_identity(args.output, args.commit)
