import importlib.util
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


if __name__ == "__main__":
    unittest.main()
