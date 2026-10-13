#!/usr/bin/env python3
"""Fail closed before publishing the production-signed HuskySU artifacts."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
from zipfile import ZipFile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from repack_apk import find_android_tool  # noqa: E402

PACKAGE = "me.weishu.kernelsu.husky.fork"
TRUSTED_CERTIFICATE = "b37ef4db671d6d1dd44d1b42efdd63effece3265649734a9d665ff5939c59553"
MODULES = ("android14-6.1_kernelsu.ko", "android16-6.12_kernelsu.ko")


def run_tool(name, *arguments):
    tool = find_android_tool(name)
    if tool is None:
        raise ValueError(f"Required Android tool missing: {name}")
    return subprocess.run(
        [str(tool), *map(str, arguments)], check=True, capture_output=True, text=True
    ).stdout


def verify_identity(signing, badging, certificate, version_code):
    if certificate != TRUSTED_CERTIFICATE:
        raise ValueError("Production keystore no longer matches the trusted certificate")
    signers = re.findall(r"Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)", signing)
    if [digest.lower() for digest in signers] != [certificate]:
        raise ValueError("APK signer does not match the sole production certificate")
    match = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']*)'", badging, re.M)
    if match is None or match.group(1) != PACKAGE or int(match.group(2)) != version_code:
        raise ValueError("APK package or version does not match the release source")
    if "application-debuggable" in badging:
        raise ValueError("Production APK is debuggable")
    return {"package": match.group(1), "version_code": int(match.group(2)),
            "version_name": match.group(3), "certificate_sha256": certificate}


def check_elf(data, machine, module=False):
    if (len(data) < 64 or data[:6] != b"\x7fELF\x02\x01"
            or int.from_bytes(data[18:20], "little") != machine
            or (module and int.from_bytes(data[16:18], "little") != 1)):
        raise ValueError("Unexpected ELF architecture or module type")


def describe(name, data):
    return {"name": name, "size": len(data), "sha256": hashlib.sha256(data).hexdigest()}


def verify_release(directory, certificate, version_code, commit):
    if re.fullmatch(r"[0-9a-f]{40}", commit) is None:
        raise ValueError("Release must identify a full source commit")
    apk = directory / "HuskySU.apk"
    metadata = verify_identity(
        run_tool("apksigner", "verify", "--verbose", "--print-certs", apk),
        run_tool("aapt2", "dump", "badging", apk), certificate, version_code,
    )
    native = []
    with ZipFile(apk) as archive:
        for arch, machine in (("arm64-v8a", 183), ("x86_64", 62)):
            name = f"lib/{arch}/libksud.so"
            data = archive.read(name)
            check_elf(data, machine)
            native.append(describe(name, data))
    assets = [describe(apk.name, apk.read_bytes())]
    for name in MODULES:
        data = (directory / name).read_bytes()
        check_elf(data, 183, module=True)
        assets.append(describe(name, data))
    manifest = {
        "schema_version": 1, "source_commit": commit, "manager": metadata,
        "native_payloads": native, "assets": assets,
        "module_scope": "Generic KMI builds; exact target validation required. Not the pinned ZP11.260918.007 / 6.1.172 Canary candidate.",
        "experimental_load_enabled": False,
        "validation_limits": ["Canary private ABI/CFI", "cold boot and recovery campaign", "full device visual acceptance"],
    }
    manifest_path = directory / "manifest.json"
    manifest_path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    checksums = assets + [describe(manifest_path.name, manifest_path.read_bytes())]
    (directory / "SHA256SUMS").write_text(
        "".join(f"{asset['sha256']}  {asset['name']}\n" for asset in checksums), encoding="ascii"
    )
    print(json.dumps({"source_commit": commit, "manager": metadata, "assets": assets}, indent=2))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    args = parser.parse_args()
    verify_release(args.directory, os.environ["EXPECTED_CERTIFICATE"],
                   int(os.environ["EXPECTED_VERSION_CODE"]), os.environ["GITHUB_SHA"])


if __name__ == "__main__":
    main()
