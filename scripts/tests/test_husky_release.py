import importlib.util
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

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


if __name__ == "__main__":
    unittest.main()
