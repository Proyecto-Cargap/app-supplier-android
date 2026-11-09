#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
. tool/environment.sh
# Requiere un emulador iniciado. Android Studio Device Manager permite seleccionarlo.
./gradlew :app:connectedStagingDebugAndroidTest :data:connectedDebugAndroidTest
