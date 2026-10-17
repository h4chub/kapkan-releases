"""Microphone declarations for the responsible KapKan.app bundle.

These declarations let macOS ask the player when a voice mod first opens an
audio input. They do not grant access or change any existing privacy decision.
The native launcher must remain the game's responsible application: start
Java directly and wait for it, rather than detaching it through a shell/open.
"""
import os
import plistlib
import subprocess
import sys
from pathlib import Path

MICROPHONE_PURPOSE = (
    "Minecraft voice chat mods use your microphone so you can talk to other players."
)
AUDIO_INPUT_ENTITLEMENT = "com.apple.security.device.audio-input"
APP_IDENTIFIER = "gg.kapkan.launcher"


def validate_microphone_support(info: dict, entitlements: dict) -> None:
    """Reject a package that cannot present KapKan's microphone consent dialog."""
    if info.get("CFBundleIdentifier") != APP_IDENTIFIER:
        raise ValueError("KapKan's existing macOS application identity must be preserved")
    if info.get("CFBundleExecutable") != "KapKan":
        raise ValueError("Microphone declarations must belong to the native KapKan entry point")
    if info.get("NSMicrophoneUsageDescription") != MICROPHONE_PURPOSE:
        raise ValueError("KapKan.app is missing its microphone usage description")
    if entitlements != {AUDIO_INPUT_ENTITLEMENT: True}:
        raise ValueError("KapKan signing must declare only the required audio-input entitlement")


def verify_signed_app(app: Path) -> None:
    """Verify Apple's signature seal and the actual embedded entitlement."""
    if sys.platform != "darwin":
        raise RuntimeError("macOS signature verification must run on macOS")
    subprocess.run(["/usr/bin/codesign", "--verify", "--deep", "--strict",
                    "--verbose=2", str(app)], check=True)
    signature = subprocess.run(["/usr/bin/codesign", "--display", "--entitlements",
                                ":-", str(app)], check=True, capture_output=True)
    info = plistlib.loads((app / "Contents/Info.plist").read_bytes())
    validate_microphone_support(info, plistlib.loads(signature.stdout))


def sign_app(app: Path) -> None:
    """Attach the capability to the app we own, leaving downloaded Java untouched."""
    if sys.platform != "darwin":
        raise RuntimeError("Sign macOS packages on a Mac; use --unsigned-macos only for development")
    identity = os.environ.get("KAPKAN_SIGN_IDENTITY", "").strip()
    command = ["/usr/bin/codesign", "--force", "--sign", identity or "-",
               "--entitlements", str(app / "Contents/Resources/KapKan.entitlements")]
    command += ["--options", "runtime", "--timestamp"] if identity else ["--timestamp=none"]
    subprocess.run(command + [str(app)], check=True)
    verify_signed_app(app)
