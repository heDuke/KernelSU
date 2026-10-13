#!/usr/bin/env python3
"""Offline gates for the pinned husky Canary 4K engineering candidate."""
import argparse
import hashlib
import json
import mmap
import struct
from pathlib import Path

RELEASE = "6.1.172-android14-11-g3fc92ed75e44-ab16169542"
IMAGE_SHA256 = "e75276559886e2aba2c7d3d9d82aa0c835f4cf4b9566d179eaea9846294710ea"
VMLINUX_SHA256 = "cb179992de8e54de5d17587dfbd661b9ee8218d6f0b2a64ab4ac78addc5f89b4"
SYMVERS_SHA256 = "8ddcd0842039fdcb0d5421c2bf1b01d1065c0894b7487a4686d9d1b3f9d3aa71"
CONFIG_SHA256 = "b4ba7450f12f2b6cb04f1b19b312ac448213854f7868baa7c4fa975194a8a343"


def digest(path):
    with open(path, "rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def require(condition, message):
    if not condition:
        raise ValueError(message)


class Elf:
    def __init__(self, data):
        self.data = data
        require(len(data) >= 64 and data[:7] == b"\x7fELF\x02\x01\x01", "invalid ELF64 LE header")
        require(struct.unpack_from("<H", data, 18)[0] == 183, "not ARM64")
        offset = struct.unpack_from("<Q", data, 40)[0]
        stride, count, names = struct.unpack_from("<HHH", data, 58)
        require(stride == 64 and count > 0 and names < count, "invalid section table")
        self.headers = [struct.unpack("<IIQQQQIIQQ", self.range(offset + i * 64, 64)) for i in range(count)]
        strings = self.section_data(self.headers[names])
        self.sections = {}
        for header in self.headers:
            name = self.string(strings, header[0])
            if name:
                require(name not in self.sections, "duplicate section: " + name)
                self.sections[name] = header

    def range(self, offset, size):
        require(offset <= len(self.data) and size <= len(self.data) - offset, "ELF bounds exceeded")
        return self.data[offset:offset + size]

    def section_data(self, header):
        return self.range(header[4], header[5])

    @staticmethod
    def string(table, offset):
        require(offset < len(table), "string offset out of bounds")
        end = table.find(b"\0", offset)
        require(end >= 0, "unterminated ELF string")
        return table[offset:end].decode("ascii")

    def symbols(self):
        header = self.sections[".symtab"]
        require(header[9] == 24 and header[5] % 24 == 0 and header[6] < len(self.headers), "invalid symbol table")
        strings = self.section_data(self.headers[header[6]])
        return [(self.string(strings, name), info, index, value)
                for name, info, _, index, value, _ in struct.iter_unpack("<IBBHQQ", self.section_data(header)) if name]


def audit(module, vmlinux, symvers, image, config):
    for path, expected in [(vmlinux, VMLINUX_SHA256), (symvers, SYMVERS_SHA256),
                           (image, IMAGE_SHA256), (config, CONFIG_SHA256)]:
        require(digest(path) == expected, "target input digest mismatch: " + str(path))
    ko = Elf(Path(module).read_bytes())
    require(struct.unpack_from("<H", ko.data, 16)[0] == 1, "module is not ET_REL")
    info = {}
    for entry in ko.section_data(ko.sections[".modinfo"]).split(b"\0"):
        if entry and b"=" in entry:
            key, value = entry.decode("ascii").split("=", 1)
            if key in ("vermagic", "ksu_kmi", "ksu_page_shift", "ksu_kernel_release"):
                require(key not in info, "duplicate module identity")
                info[key] = value
    require(info.get("vermagic") == RELEASE + " SMP preempt mod_unload modversions aarch64", "vermagic mismatch")
    require(info.get("ksu_kernel_release") == RELEASE and info.get("ksu_page_shift") == "12"
            and info.get("ksu_kmi") == "android14-6.1", "target metadata mismatch")
    require(".BTF" in ko.sections and ko.sections[".BTF"][5] > 0, "module BTF absent")
    symbols = ko.symbols()
    with open(vmlinux, "rb") as stream, mmap.mmap(stream.fileno(), 0, access=mmap.ACCESS_READ) as data:
        kernel = Elf(data)
        definitions = {}
        for name, _, index, value in kernel.symbols():
            if index:
                normalized = name.split("$", 1)[0].split(".llvm.", 1)[0]
                definitions.setdefault(normalized, set()).add(value)
        unresolved = [name for name, _, index, _ in symbols if not index]
        require(all(name in definitions and len(definitions[name]) == 1 for name in unresolved),
                "missing/ambiguous kernel symbols: " + ", ".join(name for name in unresolved if name not in definitions or len(definitions[name]) != 1))
        cfi = [(name, value) for name, _, index, value in symbols if index and name.startswith("__kcfi_typeid_") and name in definitions]
        require(all(definitions[name] == {value} for name, value in cfi), "CFI type ID mismatch")
    expected_versions = {}
    for line in Path(symvers).read_text().splitlines():
        fields = line.split()
        if len(fields) >= 2:
            expected_versions[fields[1]] = int(fields[0], 16)
    version_data = ko.section_data(ko.sections["__versions"])
    require(version_data and len(version_data) % 64 == 0, "missing/malformed symbol versions")
    versions = {}
    for crc, raw_name in struct.iter_unpack("<Q56s", version_data):
        name = Elf.string(raw_name, 0)
        require(name not in versions and expected_versions.get(name) == crc, "symbol CRC mismatch: " + name)
        versions[name] = crc
    require("module_layout" in versions, "module_layout version absent")
    missing = (set(unresolved) & expected_versions.keys()) - versions.keys()
    require(not missing, "missing symbol CRC records: " + ", ".join(sorted(missing)))
    return {"support_state": "offline_checked_candidate", "device_tested": False,
            "build_id": "ZP11.260918.007", "kernel_release": RELEASE, "page_size_bytes": 4096,
            "module_sha256": digest(module), "symbols_resolved": len(unresolved),
            "symbol_versions_checked": len(versions), "cfi_ids_compared": len(cfi)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("module", "vmlinux", "symvers", "image", "config"):
        parser.add_argument("--" + name, required=True, type=Path)
    args = parser.parse_args()
    try:
        print(json.dumps(audit(**vars(args)), indent=2))
    except (ValueError, KeyError, UnicodeError, struct.error) as error:
        parser.exit(1, "Audit failed: " + str(error) + "\n")


if __name__ == "__main__":
    main()
