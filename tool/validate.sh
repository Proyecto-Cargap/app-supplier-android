#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
. tool/environment.sh
python3 tool/check-architecture.py
./gradlew :app:testStagingDebugUnitTest :data:testDebugUnitTest :app:lintStagingDebug :app:assembleStagingDebug :app:assembleProductionRelease
