#!/bin/bash
# Сборка мода без Gradle/Loom (экономит ~1 ГБ диска): javac против Minecraft с именами yarn,
# потом tiny-remapper переводит jar в имена intermediary (как у всех Fabric-модов).
# Использование: tools/build_mod.sh <папка_исходников> <выходной.jar> <версия>
# Нужна подготовленная папка $SL_CACHE (см. tools/prepare_build.sh).
set -e
SRC=$(cd "$1" && pwd); OUT=$2; VER=${3:-1.0.0}
C=${SL_CACHE:?SL_CACHE}
T=$(mktemp -d); trap "rm -rf $T" EXIT
CP=$C/tools/mc-named.jar:$(ls $C/tools/named-*.jar | paste -sd:):$(cat $C/mc/classpath.txt)
mkdir -p $T/classes
javac -nowarn --release 21 -encoding UTF-8 -proc:none -cp "$CP" -d $T/classes $(find $SRC/src/main/java -name '*.java')
cp -r $SRC/src/main/resources/. $T/classes/
sed -i "s/\${version}/$VER/" $T/classes/fabric.mod.json
(cd $T/classes && jar cf $T/named.jar .)
java -jar $C/tools/tiny-remapper-0.11.1-fat.jar $T/named.jar $T/out.jar $C/tools/mappings/mappings.tiny named intermediary \
  $C/tools/mc-named.jar $(ls $C/tools/named-*.jar) 2>&1 | grep -v JAVA_TOOL_OPTIONS | grep -v '^\[INFO\]' || true
cp $T/out.jar "$OUT"
echo "built $OUT ($(stat -c %s "$OUT") bytes)"
