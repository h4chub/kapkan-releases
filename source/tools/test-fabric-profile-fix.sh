#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${KAPKAN_JDK:?Set KAPKAN_JDK to a JDK 21 installation}"
classpath="build/testclasses:app/KapKan-Launcher.jar"
"$KAPKAN_JDK/bin/java" -cp "$classpath" gg.kapkan.release.FabricPreflightTest
"$KAPKAN_JDK/bin/java" -cp "$classpath" gg.kapkan.release.FabricFlowTest
"$KAPKAN_JDK/bin/java" -Dkapkan.test.restoredJson=true -cp "$classpath" gg.kapkan.release.FabricFlowTest
"$KAPKAN_JDK/bin/java" -Dkapkan.test.linkedCache=true -cp "$classpath" gg.kapkan.release.FabricFlowTest
