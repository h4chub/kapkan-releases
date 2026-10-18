#!/bin/bash
set -Eeuo pipefail
cd "$(dirname "$0")"
if [ "$(uname -s)" != Darwin ]; then
  echo "Run this command on macOS. It packages the prebuilt app; no compiling is needed."
  exit 1
fi
APP="$PWD/KapKan.app"
OUT_DIR="$PWD/dist"
mkdir -p "$OUT_DIR"
LOG="$OUT_DIR/Make-DMG.log"
exec > >(/usr/bin/tee -a "$LOG") 2>&1
STAGE=""
TEMP_OUT="$OUT_DIR/KapKan-macOS-building.dmg"
cleanup() {
  if [ -n "$STAGE" ] && [ -d "$STAGE" ]; then /bin/rm -rf "$STAGE"; fi
}
failure() {
  code=$?
  trap - ERR
  /bin/rm -f "$TEMP_OUT"
  printf '\nDMG WAS NOT CREATED. Packaging stopped with exit code %s.\n' "$code"
  printf 'Your original KapKan.app and any previous completed DMG were not changed.\nLog: %s\n' "$LOG"
  if [ -t 0 ]; then read -r -p "Press Enter to close." _ || true; fi
  exit "$code"
}
trap cleanup EXIT
trap failure ERR
if [ ! -f "$APP/Contents/Info.plist" ] || [ ! -f "$APP/Contents/MacOS/KapKan" ]; then
  echo "Keep Make-DMG.command beside the complete KapKan.app from the same 1.0.7 package."
  false
fi
STAGE="$(mktemp -d "${TMPDIR:-/tmp}/KapKan-dmg.XXXXXX")"
printf '\nPreparing KapKan 1.0.7 disk image...\n'
/usr/bin/ditto "$APP" "$STAGE/KapKan.app"
EXE="$STAGE/KapKan.app/Contents/MacOS/KapKan"
/bin/chmod 755 "$EXE"
/usr/bin/plutil -lint "$STAGE/KapKan.app/Contents/Info.plist"
ENTITLEMENTS="$STAGE/KapKan.app/Contents/Resources/KapKan.entitlements"
/usr/bin/plutil -lint "$ENTITLEMENTS"
# A purpose string enables macOS's consent dialog when a game voice mod opens
# audio input. This entitlement also permits that operation in signed builds
# using the hardened runtime; macOS still requires the player's consent.
if [ "$(/usr/libexec/PlistBuddy -c 'Print :com.apple.security.device.audio-input' "$ENTITLEMENTS")" != true ]; then
  echo "The app is missing its microphone signing declaration. Use a complete KapKan package."
  false
fi
if ! /usr/libexec/PlistBuddy -c 'Print :NSMicrophoneUsageDescription' "$STAGE/KapKan.app/Contents/Info.plist" >/dev/null; then
  echo "The app is missing its microphone usage description. Use a complete KapKan package."
  false
fi
# The release packer now ends the universal file exactly at its final slice.
# On Macs with lipo available, also canonicalize through Apple's own tool.
# This modifies the temporary copy only, not the original application.
LIPO=""
if /usr/bin/xcode-select -p >/dev/null 2>&1; then LIPO="$(command -v lipo || true)"; fi
if [ -n "$LIPO" ] && "$LIPO" -archs "$EXE" >/dev/null 2>&1; then
  "$LIPO" "$EXE" -thin x86_64 -output "$STAGE/KapKan-x86_64"
  "$LIPO" "$EXE" -thin arm64 -output "$STAGE/KapKan-arm64"
  "$LIPO" -create "$STAGE/KapKan-x86_64" "$STAGE/KapKan-arm64" -output "$STAGE/KapKan-universal"
  /bin/mv "$STAGE/KapKan-universal" "$EXE"
  /bin/rm "$STAGE/KapKan-x86_64" "$STAGE/KapKan-arm64"
  /bin/chmod 755 "$EXE"
fi
if [ -n "${KAPKAN_NOTARY_PROFILE:-}" ] && [ -z "${KAPKAN_SIGN_IDENTITY:-}" ]; then
  echo "Notarization requires your Developer ID identity. An ad-hoc signature cannot be notarized."
  false
fi
if [ -n "${KAPKAN_SIGN_IDENTITY:-}" ]; then
  /usr/bin/codesign --force --sign "$KAPKAN_SIGN_IDENTITY" --options runtime --entitlements "$ENTITLEMENTS" --timestamp "$STAGE/KapKan.app"
else
  /usr/bin/codesign --force --sign - --entitlements "$ENTITLEMENTS" --timestamp=none "$STAGE/KapKan.app"
fi
# Never remove this check to make a broken executable look successful.
/usr/bin/codesign --verify --deep --strict --verbose=2 "$STAGE/KapKan.app"
/usr/bin/codesign --display --entitlements :- "$STAGE/KapKan.app" > "$STAGE/signed-entitlements.plist"
if [ "$(/usr/libexec/PlistBuddy -c 'Print :com.apple.security.device.audio-input' "$STAGE/signed-entitlements.plist")" != true ]; then
  echo "The app signature did not retain its microphone declaration. Packaging stopped."
  false
fi
/bin/rm "$STAGE/signed-entitlements.plist"
echo "Application code-signature validation passed."
/bin/ln -s /Applications "$STAGE/Applications"
cat > "$STAGE/READ-ME.txt" <<'TXT'
KapKan 1.0.7
Drag KapKan.app into Applications, then open it.
First launch prepares the matching Java runtime and requires internet.
Minecraft data in ~/.kapkan is preserved. This package does not publish updates.
Native device acceptance tests and public-release signing may still be pending.
An ad-hoc signature is NOT Apple Developer ID notarization.
TXT
/usr/bin/hdiutil create -volname "KapKan" -srcfolder "$STAGE" -fs HFS+ -format UDZO -ov "$TEMP_OUT"
/usr/bin/hdiutil verify "$TEMP_OUT"
if [ -n "${KAPKAN_NOTARY_PROFILE:-}" ]; then
  /usr/bin/codesign --force --sign "$KAPKAN_SIGN_IDENTITY" --timestamp "$TEMP_OUT"
  /usr/bin/xcrun notarytool submit "$TEMP_OUT" --keychain-profile "$KAPKAN_NOTARY_PROFILE" --wait
  /usr/bin/xcrun stapler staple "$TEMP_OUT"
  /usr/bin/xcrun stapler validate "$TEMP_OUT"
fi
FINAL="$OUT_DIR/KapKan-macOS-1.0.7.dmg"
if [ -f "$FINAL" ]; then
  /bin/mv "$FINAL" "$OUT_DIR/KapKan-macOS-previous-$(date +%Y%m%d-%H%M%S).dmg"
fi
/bin/mv "$TEMP_OUT" "$FINAL"
(cd "$OUT_DIR" && /usr/bin/shasum -a 256 "KapKan-macOS-1.0.7.dmg" > "KapKan-macOS-1.0.7.dmg.sha256")
printf '\nCREATED AND VERIFIED: %s\n' "$FINAL"
if [ -z "${KAPKAN_NOTARY_PROFILE:-}" ]; then echo "Not Developer-ID notarized. No website/GitHub release was published."; fi
/usr/bin/open -R "$FINAL"
echo "Mods, worlds, controls and the installed launcher were not modified."
