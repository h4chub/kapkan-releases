#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
: "${KAPKAN_JDK:?Set KAPKAN_JDK to a JDK 21 installation}"
: "${KAPKAN_CLIENT_JDK:?Set KAPKAN_CLIENT_JDK to a JDK 25 installation}"
command -v go >/dev/null
command -v python3 >/dev/null
export GOFLAGS="${GOFLAGS:-} -buildvcs=false"
# Build companion targets first using companion/build.py and JDK 25.
KAPKAN_JDK="$KAPKAN_CLIENT_JDK" python3 companion/build.py
for version in 26.1 26.1.1 26.1.2; do
  KAPKAN_JDK="$KAPKAN_CLIENT_JDK" python3 companion/test.py --minecraft "$version"
done
python3 companion/check_compatibility.py
mkdir -p companion/build/layout-tests
"$KAPKAN_CLIENT_JDK/bin/javac" --release 25 -cp companion/build/26.1.2/classes -d companion/build/layout-tests companion/qa/MenuLayoutTest.java
"$KAPKAN_CLIENT_JDK/bin/java" -cp companion/build/26.1.2/classes:companion/build/layout-tests MenuLayoutTest
python3 tools/build-desktop.py
bash tools/test-fabric-profile-fix.sh
for test in gg.kapkan.v23.ReleaseSelfTest gg.kapkan.release.FirstInstallTest gg.kapkan.release.DownloadRecoveryTest gg.kapkan.release.ModProfilesTest gg.kapkan.release.WindowsFabricClasspathTest gg.kapkan.release.HardeningSelfTest gg.kapkan.release.LauncherMaintenanceTest gg.kapkan.release.PerformanceIntegrationTest; do
  "$KAPKAN_JDK/bin/java" -Djava.awt.headless=true -cp build/testclasses:app/KapKan-Launcher.jar "$test"
done
"$KAPKAN_JDK/bin/java" -cp build/testclasses:app/KapKan-Launcher.jar gg.kapkan.release.CompanionIntegrationTest app/KapKan-1.0.7-mc26.1.2-fabric.jar app/KapKan-ClientCore-1.21.11-Beta1.jar app/KapKan-Presence-26.1.2-H2.jar app/KapKan-Presence-26.1.2-Build6.jar app/KapKan-Presence-26.1.2.jar app
python3 packaging/windows_icon.py
for arch in arm64 amd64; do
  (cd native && GOTOOLCHAIN=local CGO_ENABLED=0 GOOS=darwin GOARCH="$arch" go build -trimpath -ldflags='-s -w' -o "../build/KapKan-darwin-$arch" ./cmd/launcher)
done
(cd native && GOTOOLCHAIN=local CGO_ENABLED=0 GOOS=windows GOARCH=amd64 go build -trimpath -ldflags='-s -w -H=windowsgui' -o ../build/KapKan.exe ./cmd/launcher)
python3 packaging/make_packages.py
(cd native && KAPKAN_TEST_PAYLOAD="$PWD/cmd/installer/payload.zip" GOTOOLCHAIN=local go test ./... -count=1)
(cd native && GOTOOLCHAIN=local CGO_ENABLED=0 GOOS=windows GOARCH=amd64 go build -trimpath -ldflags='-s -w -H=windowsgui' -o ../dist/KapKan-1.0.7-Setup.exe ./cmd/installer)
(cd packaging && python3 -m unittest test_macho_layout test_macos_microphone -v)
python3 packaging/audit.py
printf '\nArtifacts built in dist/. Run tools/run-mod-ui-qa.py with Xvfb for UI verification. Native device acceptance is still required.\n'
