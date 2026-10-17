import importlib.util
import os
from pathlib import Path
import tempfile
import unittest
import json
from zipfile import ZipFile
import warnings
from unittest.mock import patch
from scripts import manager_build_identity as identity

spec = importlib.util.spec_from_file_location("release", Path(__file__).parents[1] / "verify_husky_release.py")
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


class ProductionIdentityTest(unittest.TestCase):
    def setUp(self):
        self.signing = [release.TRUSTED_CERTIFICATE]
        self.badging = f"package: name='{release.PACKAGE}' versionCode='32638' versionName='test'\n"

    def test_trusted_identity(self):
        result = release.verify_identity(self.signing, self.badging, release.TRUSTED_CERTIFICATE, 32638)
        self.assertEqual(result["package"], release.PACKAGE)

    def test_reject_identity_changes(self):
        cases = [
            (self.signing, self.badging, "0" * 64, 32638),
            (["0" * 64], self.badging, release.TRUSTED_CERTIFICATE, 32638),
            (self.signing * 2, self.badging, release.TRUSTED_CERTIFICATE, 32638),
            ([], self.badging, release.TRUSTED_CERTIFICATE, 32638),
            (self.signing, self.badging.replace(release.PACKAGE, "me.weishu.kernelsu.pr"), release.TRUSTED_CERTIFICATE, 32638),
            (self.signing, self.badging, release.TRUSTED_CERTIFICATE, 32637),
            (self.signing, self.badging + "application-debuggable\n", release.TRUSTED_CERTIFICATE, 32638),
        ]
        for args in cases:
            with self.subTest(args=args), self.assertRaises(ValueError):
                release.verify_identity(*args)

    def test_verification_failure_writes_no_manifest(self):
        with tempfile.TemporaryDirectory() as path:
            directory = Path(path)
            with patch.object(release, "find_android_tool", return_value=None):
                with self.assertRaises(ValueError):
                    release.verify_release(directory, release.TRUSTED_CERTIFICATE, 32638, "a" * 40)
            self.assertEqual(list(directory.iterdir()), [])

    def test_actual_module_target_and_inconsistent_vermagic(self):
        module = Path(os.environ.get("KSU_RELEASE_MODULE_FIXTURE", "dist-husky/android14-6.1_kernelsu.ko"))
        if not module.is_file():
            self.skipTest("built release module fixture required")
        data = module.read_bytes()
        target = release.module_target("android14-6.1_kernelsu.ko", data)
        self.assertEqual(target["kmi"], "android14-6.1")
        self.assertIn(target["page_size"], (4096, 16384, 65536))
        original = ("vermagic=" + target["kernel_release"]).encode()
        mutated = data.replace(original, original[:-1] + (b"x" if original[-1:] != b"x" else b"y"), 1)
        with self.assertRaises(ValueError):
            release.module_target("android14-6.1_kernelsu.ko", mutated)


class ManagerContentTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)
        self.source = {"schema_version": 1, "source_commit": "a" * 40, "package": identity.PACKAGE,
                       "version_code": 32650, "version_name": "fixture", "channel": "production"}
        self.payloads = {"AndroidManifest.xml": b"manifest", "resources.arsc": b"resources",
                         "classes.dex": b"code", "res/drawable/icon.xml": b"icon",
                         identity.IDENTITY_ASSET: json.dumps(self.source).encode()}

    def apk(self, name, payloads=None, native=b"old native"):
        path = self.directory / name
        with ZipFile(path, "w") as archive:
            for key, data in (self.payloads if payloads is None else payloads).items():
                archive.writestr(key, data)
            archive.writestr("lib/arm64-v8a/libksud.so", native)
        return path

    def test_native_replacement_keeps_manager_payloads(self):
        source = self.apk("source.apk")
        result = self.apk("result.apk", native=b"new native")
        self.assertEqual(len(identity.verify_repack(source, result)), len(self.payloads))
        self.assertEqual(identity.verify_source(result, "a" * 40, 32650, "fixture"), self.source)

    def test_reject_changed_manager_code_resources_and_assets(self):
        source = self.apk("source.apk")
        for name in self.payloads:
            with self.subTest(name=name):
                changed = self.payloads | {name: b"stale build content"}
                with self.assertRaises(ValueError):
                    identity.verify_repack(source, self.apk("result.apk", changed))

    def test_reject_wrong_source_version_or_channel(self):
        for key, value in {"source_commit": "b" * 40, "version_code": 32638,
                           "version_name": "old", "package": "me.weishu.kernelsu.pr",
                           "channel": "test"}.items():
            with self.subTest(key=key), self.assertRaises(ValueError):
                contents = self.payloads | {identity.IDENTITY_ASSET: json.dumps(self.source | {key: value}).encode()}
                identity.verify_source(self.apk("wrong.apk", contents), "a" * 40, 32650, "fixture")
        without_identity = {k: v for k, v in self.payloads.items() if k != identity.IDENTITY_ASSET}
        with self.assertRaises(ValueError):
            identity.verify_source(self.apk("missing.apk", without_identity), "a" * 40, 32650, "fixture")

    def test_reject_missing_code_duplicate_entries_or_ambiguous_apks(self):
        with self.assertRaises(ValueError):
            identity.single_apk(self.directory)
        source = self.apk("source.apk")
        self.assertEqual(identity.single_apk(self.directory), source)
        missing = {k: v for k, v in self.payloads.items() if k != "classes.dex"}
        with self.assertRaises(ValueError):
            identity.manager_payloads(self.apk("missing.apk", missing))
        with self.assertRaises(ValueError):
            identity.single_apk(self.directory)
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", UserWarning)
            with ZipFile(source, "a") as archive:
                archive.writestr("classes.dex", b"duplicate")
        with self.assertRaises(ValueError):
            identity.manager_payloads(source)

    def test_source_writer_requires_requested_checkout(self):
        output = self.directory / "identity.json"
        with patch.object(identity.subprocess, "check_output", return_value="a" * 40):
            with self.assertRaises(ValueError):
                identity.write_identity(output, "b" * 40)
        self.assertFalse(output.exists())
        with patch.object(identity.subprocess, "check_output", side_effect=["a" * 40, "2650", "fixture"]):
            identity.write_identity(output, "a" * 40)
        self.assertEqual(json.loads(output.read_text()), self.source)


if __name__ == "__main__":
    unittest.main()
