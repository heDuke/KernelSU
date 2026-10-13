#!/usr/bin/env python3
"""Exercise audit rejection using mutations of the real compiled module."""
import importlib.util
import os
import struct
import tempfile
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("audit", Path(__file__).parents[1] / "audit_canary_lkm.py")
audit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)


@unittest.skipUnless(os.environ.get("KSU_CANARY_AUDIT_INPUTS"), "real artifact fixtures required")
class CandidateAuditTest(unittest.TestCase):
    def setUp(self):
        inputs = Path(os.environ["KSU_CANARY_AUDIT_INPUTS"])
        self.module = Path(os.environ["KSU_CANARY_MODULE_FIXTURE"])
        self.inputs = dict(vmlinux=inputs / "vmlinux", symvers=inputs / "kernel_aarch64_Module.symvers",
                           image=inputs / "Image", config=Path(os.environ["KSU_CANARY_CONFIG_FIXTURE"]))

    def test_real_candidate(self):
        result = audit.audit(self.module, **self.inputs)
        self.assertGreater(result["symbols_resolved"], 0)
        self.assertGreater(result["symbol_versions_checked"], 0)
        self.assertFalse(result["device_tested"])

    def test_wrong_pages_and_crc(self):
        original = self.module.read_bytes()
        elf = audit.Elf(original)
        crc_offset = elf.sections["__versions"][4]
        wrong_crc = bytearray(original)
        wrong_crc[crc_offset] ^= 1
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "module.ko"
            for data in [original.replace(b"ksu_page_shift=12", b"ksu_page_shift=14"), wrong_crc]:
                path.write_bytes(data)
                with self.assertRaises(ValueError):
                    audit.audit(path, **self.inputs)

    def test_truncated_module(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "module.ko"
            path.write_bytes(self.module.read_bytes()[:64])
            with self.assertRaises(ValueError):
                audit.audit(path, **self.inputs)

    def test_missing_import_crc(self):
        data = bytearray(self.module.read_bytes())
        elf = audit.Elf(data)
        section = elf.sections["__versions"]
        records = list(struct.iter_unpack("<Q56s", elf.section_data(section)))
        imports = {name for name, _, index, _ in elf.symbols() if not index}
        removed = next(audit.Elf.string(name, 0) for _, name in records
                       if audit.Elf.string(name, 0) in imports)
        kept = b"".join(struct.pack("<Q56s", crc, name) for crc, name in records
                        if audit.Elf.string(name, 0) != removed)
        data[section[4]:section[4] + len(kept)] = kept
        table = struct.unpack_from("<Q", data, 40)[0]
        struct.pack_into("<Q", data, table + elf.headers.index(section) * 64 + 32, len(kept))
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "module.ko"
            path.write_bytes(data)
            with self.assertRaisesRegex(ValueError, "missing symbol CRC records"):
                audit.audit(path, **self.inputs)


if __name__ == "__main__":
    unittest.main()
