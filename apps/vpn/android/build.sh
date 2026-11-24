#!/usr/bin/env bash
# YURK for Android: APK (install from the bot) + AAB (Google Play, needs an Organization developer account).
#   bash build.sh OUT_DIR
# Needs: JDK 17+, Android SDK (ANDROID_HOME, platform 35, build-tools 35), Gradle 8.9+ (`gradle` in PATH or /opt/gradle).
# Release key: YURK_KEYSTORE + YURK_KS_PASS; if not set, ~/.android-upload/yurk-upload.jks is used (created once).
# KEEP THAT KEY: without it no update of the app can be installed over the old one.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="${1:?out dir}"; mkdir -p "$OUT"
export ANDROID_HOME="${ANDROID_HOME:-/root/android-sdk}"
GRADLE="$(command -v gradle || echo /opt/gradle/bin/gradle)"

# 1. Xray core for Android (AndroidLibXrayLite, LGPL-3.0)
AAR="$HERE/app/libs/libv2ray.aar"
[ -s "$AAR" ] || curl -fL --retry 3 -o "$AAR" https://github.com/2dust/AndroidLibXrayLite/releases/latest/download/libv2ray.aar

# 2. the interface = the Mini App page, without the Telegram script (the app has its own bridge)
grep -v 'telegram-web-app.js' "$HERE/../panel/public/app.html" > "$HERE/app/src/main/assets/app.html"

# 3. release key
if [ -z "${YURK_KEYSTORE:-}" ]; then
  K="$HOME/.android-upload"; mkdir -p "$K"; chmod 700 "$K"
  export YURK_KEYSTORE="$K/yurk-upload.jks"
  if [ ! -f "$YURK_KEYSTORE" ]; then
    head -c 18 /dev/urandom | base64 | tr -d '/+=' > "$K/yurk-upload.pass"
    keytool -genkeypair -keystore "$YURK_KEYSTORE" -alias yurk -keyalg RSA -keysize 3072 -validity 10000 \
      -storepass "$(cat "$K/yurk-upload.pass")" -keypass "$(cat "$K/yurk-upload.pass")" -dname "CN=YURK VPN, O=Yompix" >/dev/null
    echo "NEW release key: $YURK_KEYSTORE (password in yurk-upload.pass) — back it up!"
  fi
  export YURK_KS_PASS="$(cat "$K/yurk-upload.pass")"
fi

# 4. build
cd "$HERE"
"$GRADLE" --no-daemon -q assembleRelease bundleRelease
V=$(grep -m1 "def vName" app/build.gradle | sed "s/.*'\(.*\)'.*/\1/")
cp app/build/outputs/apk/release/app-release.apk "$OUT/YURK-$V.apk"
cp app/build/outputs/bundle/release/app-release.aab "$OUT/YURK-$V.aab"
ls -la "$OUT"/YURK-"$V".*
