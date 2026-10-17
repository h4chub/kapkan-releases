"""Regression coverage for the missing macOS voice chat permission metadata."""
import copy
import plistlib
import re
import shutil
import struct
import subprocess
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

from macos_permissions import (
    APP_IDENTIFIER,
    AUDIO_INPUT_ENTITLEMENT,
    MICROPHONE_PURPOSE,
    validate_microphone_support,
)


class MacMicrophonePackageTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory()
        cls.root = Path(cls.temp.name)
        source = Path(__file__).resolve().parents[1]
        for name in ("packaging", "build", "app", "native/cmd/installer"):
            (cls.root / name).mkdir(parents=True)
        for name in ("make_packages.py", "macho_layout.py", "macos_permissions.py",
                     "KapKan.entitlements", "Make-DMG.command"):
            shutil.copy2(source / "packaging" / name, cls.root / "packaging" / name)
        # Resource contents are immaterial here; exercise the production packer
        # against its actual resource names and inspect its final archive.
        for resource in (source / "app").iterdir():
            if resource.is_file():
                (cls.root / "app" / resource.name).write_bytes(b"fixture resource")
        for name in re.findall(r"'(KapKan[^']+\.(?:jar|png|icns|ico))'",
                               (source / "packaging/make_packages.py").read_text()):
            (cls.root / "app" / name).write_bytes(b"fixture resource")
        shutil.copytree(source / "performance", cls.root / "performance")
        for name in ("README.md", "QA-REPORT.md", "BUILD-INFO.json",
                     "RELEASE-GATE.json", "GO-LICENSE.txt"):
            (cls.root / name).write_text("fixture\n")
        for arch, cpu in (("amd64", 0x01000007), ("arm64", 0x0100000c)):
            data = struct.pack("<8I", 0xfeedfacf, cpu,
                               3 if arch == "amd64" else 0, 2, 1, 72, 0, 0)
            data += struct.pack("<II16sQQQQiiII", 0x19, 72, b"__LINKEDIT",
                                0, 8, 104, 8, 1, 1, 0, 0) + b"PAYLOAD!"
            (cls.root / "build" / ("KapKan-darwin-" + arch)).write_bytes(data)
        (cls.root / "build" / "KapKan.exe").write_bytes(b"fixture executable")
        subprocess.run([sys.executable, str(cls.root / "packaging/make_packages.py"), "--unsigned-macos"],
                       check=True, capture_output=True, text=True)
        archive = next((cls.root / "dist").glob("KapKan-macOS-*.zip"))
        with zipfile.ZipFile(archive) as packaged:
            cls.info = plistlib.loads(packaged.read("KapKan.app/Contents/Info.plist"))
            cls.entitlements = plistlib.loads(packaged.read(
                "KapKan.app/Contents/Resources/KapKan.entitlements"))
            cls.performance = {name.removeprefix("KapKan.app/Contents/Resources/performance/"):
                               packaged.read(name) for name in packaged.namelist()
                               if name.startswith("KapKan.app/Contents/Resources/performance/")}

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    def test_archive_can_request_microphone_consent_with_existing_identity(self):
        validate_microphone_support(self.info, self.entitlements)
        self.assertEqual(self.info["CFBundleIdentifier"], APP_IDENTIFIER)
        self.assertEqual(self.info["NSMicrophoneUsageDescription"], MICROPHONE_PURPOSE)
        self.assertEqual(self.entitlements, {AUDIO_INPUT_ENTITLEMENT: True})
        self.assertNotIn("NSCameraUsageDescription", self.info)

    def test_missing_usage_description_from_105_is_rejected(self):
        broken = copy.copy(self.info)
        del broken["NSMicrophoneUsageDescription"]
        with self.assertRaisesRegex(ValueError, "usage description"):
            validate_microphone_support(broken, self.entitlements)

    def test_missing_hardened_runtime_entitlement_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "audio-input"):
            validate_microphone_support(self.info, {})

    def test_identity_change_cannot_discard_existing_player_privacy_choice(self):
        broken = {**self.info, "CFBundleIdentifier": "gg.kapkan.launcher.new"}
        with self.assertRaisesRegex(ValueError, "identity"):
            validate_microphone_support(broken, self.entitlements)

    def test_performance_binaries_sources_and_licenses_travel_together(self):
        original = {file.relative_to(self.root / "performance").as_posix(): file.read_bytes()
                    for file in (self.root / "performance").rglob("*") if file.is_file()}
        self.assertEqual(self.performance, original)
        self.assertIn("manifest.json", self.performance)
        self.assertTrue(any(name.startswith("sources/") for name in self.performance))
        self.assertTrue(any(name.startswith("licenses/") for name in self.performance))


if __name__ == "__main__":
    unittest.main(verbosity=2)
