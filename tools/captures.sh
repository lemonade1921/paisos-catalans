#!/usr/bin/env bash
# Fa les captures de pantalla del README en un emulador (flux «Captures de pantalla»).
set -euo pipefail

PKG=cat.paisoscatalans
APK=app/build/outputs/apk/debug/app-debug.apk
OUT=docs/screenshots
mkdir -p "$OUT"

adb install -r "$APK"

# Barra d'estat neta
adb shell settings put global sysui_demo_allowed 1 || true
adb shell am broadcast -a com.android.systemui.demo -e command enter || true
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1200 || true
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false || true
adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 -e mobile hide || true
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false || true

# 1. Benvinguda (primer arrencada, sense dades)
adb shell am start -W -n "$PKG/.MainActivity"
sleep 8
adb exec-out screencap -p > "$OUT/benvinguda.png"
adb shell am force-stop "$PKG"

# 2. Dades de demostració dins de l'emmagatzematge privat de l'app
adb push demo-estat.json /data/local/tmp/estat.json
adb shell "cat /data/local/tmp/estat.json | run-as $PKG sh -c 'mkdir -p files && cat > files/estat.json'"

captura() {
  adb shell am start -n "$PKG/.MainActivity" --es obre "$1"
  sleep "${2:-5}"
  adb exec-out screencap -p > "$OUT/$1.png"
}

adb shell am start -W -n "$PKG/.MainActivity" --es obre mapa
sleep 10
adb exec-out screencap -p > "$OUT/mapa.png"
captura progres
captura joc
captura perfil
captura ajustos
captura resum 7
captura compartir 7
adb shell input keyevent KEYCODE_BACK || true

ls -la "$OUT"
