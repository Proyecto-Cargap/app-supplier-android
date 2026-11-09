#!/bin/sh
# Selecciona herramientas existentes sin modificar la configuración global.
if [ -z "${JAVA_HOME:-}" ] && [ -x '/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/java' ]; then
    JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'; export JAVA_HOME
fi
if [ -z "${ANDROID_HOME:-}" ] && [ -d "$HOME/Library/Android/sdk" ]; then
    ANDROID_HOME="$HOME/Library/Android/sdk"; export ANDROID_HOME
fi
