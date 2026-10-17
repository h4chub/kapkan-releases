#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
: "${KAPKAN_JDK:?Set KAPKAN_JDK to a JDK 21 installation}"
: "${KAPKAN_CLIENT_JDK:?Set KAPKAN_CLIENT_JDK to a JDK 25 installation}"
command -v go >/dev/null
export GOFLAGS="${GOFLAGS:-} -buildvcs=false"
KAPKAN_JDK="$KAPKAN_CLIENT_JDK" python3 companion/build.py
python3 tools/build-desktop.py
"$KAPKAN_JDK/bin/java" -cp build/testclasses:app/KapKan-Launcher.jar gg.kapkan.release.WindowsFabricClasspathTest
"$KAPKAN_JDK/bin/java" -cp build/testclasses:app/KapKan-Launcher.jar gg.kapkan.release.FabricFlowTest
python3 packaging/windows_icon.py
(cd native && GOTOOLCHAIN=local CGO_ENABLED=0 GOOS=windows GOARCH=amd64 go build -trimpath -ldflags='-s -w -H=windowsgui' -o ../build/KapKan.exe ./cmd/launcher)
python3 packaging/make_packages.py --windows-only
(cd native && KAPKAN_TEST_PAYLOAD="$PWD/cmd/installer/payload.zip" GOTOOLCHAIN=local go test ./... -count=1 -v)
(cd native && GOTOOLCHAIN=local CGO_ENABLED=0 GOOS=windows GOARCH=amd64 go build -trimpath -ldflags='-s -w -H=windowsgui' -o ../dist/KapKan-1.0.6-Setup.exe ./cmd/installer)
python3 packaging/audit.py --windows-only
