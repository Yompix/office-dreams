#!/bin/bash
# Сборка APK «Night Shift» без Android Studio (пакеты apt: aapt apksigner zipalign android-sdk-platform-23 dalvik-exchange).
# Запуск: tools/games-apk/build.sh <keystore> <пароль> -> public/games/nightshift.apk
set -e
cd "$(dirname "$0")"; ROOT=../..; KS=$1; PW=$2
JAR=/usr/lib/android-sdk/platforms/android-23/android.jar; B=$(mktemp -d)
mkdir -p $B/res/drawable $B/assets $B/obj
cp -r $ROOT/public/games/nightshift $B/assets/
python3 -c "from PIL import Image; Image.open('$ROOT/public/games/nightshift/icon.png').resize((192,192), Image.LANCZOS).save('$B/res/drawable/icon.png')"
# иконки лаунчера по плотностям (res/drawable-*dpi/icon.png, делает ~/gamegen/brand.js --write)
[ -d res ] && cp -r res/. $B/res/
javac -nowarn -source 8 -target 8 -bootclasspath $JAR -d $B/obj src/com/officedreams/nightshift/Main.java 2>&1 | grep -v warning || true
dalvik-exchange --dex --output=$B/classes.dex $B/obj
aapt package -f -M AndroidManifest.xml -S $B/res -A $B/assets -I $JAR -F $B/u.apk -0 mp3 -0 webp
(cd $B && aapt add u.apk classes.dex >/dev/null)
zipalign -f 4 $B/u.apk $B/a.apk
apksigner sign --ks "$KS" --ks-pass pass:"$PW" --out $ROOT/public/games/nightshift.apk $B/a.apk
rm -rf $B; ls -la $ROOT/public/games/nightshift.apk
