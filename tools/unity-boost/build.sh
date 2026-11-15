#!/usr/bin/env bash
# Unity Boost RU — сборка офлайн-архива для владельца.
#
#   bash tools/unity-boost/build.sh            # всё: скачать, собрать dll, сложить архив
#   WORK=/tmp/ub OUT=/tmp/UnityBoostRU.zip bash tools/unity-boost/build.sh
#
# Что делает:
#   1) скачивает закреплённые версии BepInEx 5 / BepInEx 6 IL2CPP / XUnity.AutoTranslator (проверка sha256);
#   2) ставит .NET SDK 8 в $DOTNET_DIR, если его нет (dotnet-install.sh);
#   3) собирает UnityBoost.dll в двух вариантах (BepInEx 5 / net35 и BepInEx 6 IL2CPP / net6);
#   4) складывает архив: installer + payload/ + licenses/ + README_RU.txt.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORK="${WORK:-$HERE/build}"
OUT="${OUT:-$WORK/UnityBoostRU.zip}"
DOTNET_DIR="${DOTNET_DIR:-/home/user/tools/dotnet}"
export NUGET_PACKAGES="${NUGET_PACKAGES:-$WORK/nuget}"
export DOTNET_CLI_TELEMETRY_OPTOUT=1 DOTNET_NOLOGO=1 DOTNET_SKIP_FIRST_TIME_EXPERIENCE=1

# ---- закреплённые версии (менять вместе с sha256) ----
BEP5_VER=5.4.23.6
BEP6_BUILD=788
BEP6_VER="6.0.0-be.788+5b766a3"
XU_VER=5.6.3.1
GH=https://github.com
declare -A URL SHA
URL[bep5_x64]="$GH/BepInEx/BepInEx/releases/download/v$BEP5_VER/BepInEx_win_x64_$BEP5_VER.zip"
SHA[bep5_x64]=6c57a714a892efb4e9e1fef63d608a3391f30ec984031c465404e4ee381a0755
URL[bep5_x86]="$GH/BepInEx/BepInEx/releases/download/v$BEP5_VER/BepInEx_win_x86_$BEP5_VER.zip"
SHA[bep5_x86]=a4024dafaca5d3784097e34b3c2921415030c0b461e52f30053ed8914be9622d
URL[bep6_x64]="https://builds.bepinex.dev/projects/bepinex_be/$BEP6_BUILD/BepInEx-Unity.IL2CPP-win-x64-${BEP6_VER/+/%2B}.zip"
SHA[bep6_x64]=f4cc496bd098a0df4164b81e3737297707f13a47c2478dba2f60eefab784817a
URL[bep6_x86]="https://builds.bepinex.dev/projects/bepinex_be/$BEP6_BUILD/BepInEx-Unity.IL2CPP-win-x86-${BEP6_VER/+/%2B}.zip"
SHA[bep6_x86]=d5954a5993ec39cd1133603d85bff93875d30b6411b712cc13dcf03c8e08a4d3
URL[xu_bep5]="$GH/bbepis/XUnity.AutoTranslator/releases/download/v$XU_VER/XUnity.AutoTranslator-BepInEx-$XU_VER.zip"
SHA[xu_bep5]=6c3ff03aafe9708f25c2115c0c04c255b6a9d88e716896ae7bacb590a8777609
URL[xu_il2cpp]="$GH/bbepis/XUnity.AutoTranslator/releases/download/v$XU_VER/XUnity.AutoTranslator-BepInEx-IL2CPP-$XU_VER.zip"
SHA[xu_il2cpp]=f1d4d3151a061edfcda815e4472fe1f1de8756bdc23708c9b5ebb9bfe736fb1f
# только для компиляции (в архив не идут)
URL[ref_unity]="https://nuget.bepinex.dev/v3/package/unityengine/5.6.1/unityengine.5.6.1.nupkg"
SHA[ref_unity]=350ab9017bfece555e9479949cff68a50aaca0d86075f426ae0acb132d057096
URL[ref_il2cpp]="https://nuget.bepinex.dev/v3/package/il2cppinterop.referencelibs/1.0.0/il2cppinterop.referencelibs.1.0.0.nupkg"
SHA[ref_il2cpp]=cf268c538460180e540fe89fe4cfbfb7af38515f9d63b37f31bfa4b5e73bc899

mkdir -p "$WORK/dl"
fetch() {
  local key="$1" f="$WORK/dl/$1.zip"
  if [[ ! -f "$f" ]] || ! echo "${SHA[$key]}  $f" | sha256sum -c --quiet - 2>/dev/null; then
    echo "скачиваю $key"
    curl -fsSL --retry 3 -o "$f.part" "${URL[$key]}"
    mv "$f.part" "$f"
  fi
  echo "${SHA[$key]}  $f" | sha256sum -c --quiet - || { echo "sha256 не совпал: $key"; exit 1; }
}
for k in "${!URL[@]}"; do fetch "$k"; done

# ---- ссылки для компиляции ----
R="$WORK/refs"; rm -rf "$R"; mkdir -p "$R/bep5" "$R/bep6"
unzip -qjo "$WORK/dl/bep5_x64.zip" BepInEx/core/BepInEx.dll -d "$R/bep5"
unzip -qjo "$WORK/dl/ref_unity.zip" lib/net35/UnityEngine.dll -d "$R/bep5"
unzip -qjo "$WORK/dl/bep6_x64.zip" BepInEx/core/BepInEx.Core.dll BepInEx/core/BepInEx.Unity.IL2CPP.dll \
  BepInEx/core/BepInEx.Unity.Common.dll BepInEx/core/Il2CppInterop.Runtime.dll BepInEx/core/Il2CppInterop.Common.dll -d "$R/bep6"
unzip -qjo "$WORK/dl/ref_il2cpp.zip" lib/net6.0/Il2Cppmscorlib.dll lib/net6.0/UnityEngine.CoreModule-nstrip.dll -d "$R/bep6"
mv "$R/bep6/UnityEngine.CoreModule-nstrip.dll" "$R/bep6/UnityEngine.CoreModule.dll"

# ---- .NET SDK ----
if [[ ! -x "$DOTNET_DIR/dotnet" ]]; then
  echo "ставлю .NET SDK 8 в $DOTNET_DIR"
  mkdir -p "$DOTNET_DIR"
  curl -fsSL -o "$WORK/dotnet-install.sh" https://dot.net/v1/dotnet-install.sh
  bash "$WORK/dotnet-install.sh" --channel 8.0 --install-dir "$DOTNET_DIR" --no-path
fi
DOTNET="$DOTNET_DIR/dotnet"

build() {
  local proj="$1" out="$2"
  "$DOTNET" build "$HERE/src/$proj" -c Release -nologo -v:minimal \
    -p:RefsDir="$R" -p:BaseIntermediateOutputPath="$WORK/obj/$(basename "$proj" .csproj)/" \
    -p:OutputPath="$out/" -p:TreatWarningsAsErrors=false
}
build BepInEx5/UnityBoost.BepInEx5.csproj "$WORK/bin/bep5"
build IL2CPP/UnityBoost.IL2CPP.csproj "$WORK/bin/il2cpp"

# ---- сборка архива ----
STAGE="$WORK/stage/UnityBoostRU"; rm -rf "$WORK/stage"; mkdir -p "$STAGE/payload"
P="$STAGE/payload"
for k in bep5_x64 bep5_x86 bep6_x64 bep6_x86 xu_bep5 xu_il2cpp; do cp "$WORK/dl/$k.zip" "$P/$k.zip"; done
mkdir -p "$P/unityboost/bep5" "$P/unityboost/il2cpp"
cp "$WORK/bin/bep5/UnityBoost.dll" "$P/unityboost/bep5/"
cp "$WORK/bin/il2cpp/UnityBoost.dll" "$P/unityboost/il2cpp/"
cp "$HERE/config/"* "$P/"
cat > "$P/versions.txt" <<EOF
UnityBoost 1.0.0
BepInEx (Mono)   $BEP5_VER
BepInEx (IL2CPP) $BEP6_VER
XUnity.AutoTranslator $XU_VER
EOF
cp "$HERE/installer/install.ps1" "$STAGE/install.ps1"
cp "$HERE/installer/"*.bat "$STAGE/"
cp "$HERE/README_RU.txt" "$STAGE/README_RU.txt"
mkdir -p "$STAGE/licenses"; cp "$HERE/licenses/"* "$STAGE/licenses/"

# Windows-файлы: .bat и .txt с CRLF (внутри .bat только латиница — кодовая страница консоли не важна).
for f in "$STAGE"/*.bat "$STAGE"/README_RU.txt "$STAGE/payload/"*.ini "$STAGE/payload/"*.cfg "$STAGE/payload/versions.txt"; do
  [[ -f "$f" ]] && sed -i 's/\r$//; s/$/\r/' "$f"
done
# install.ps1 — UTF-8 с BOM (иначе Windows PowerShell 5.1 ломает кириллицу)
sed -i 's/\r$//; s/$/\r/' "$STAGE/install.ps1"
if [[ "$(head -c3 "$STAGE/install.ps1" | od -An -tx1 | tr -d " \n")" != "efbbbf" ]]; then
  { printf '\xef\xbb\xbf'; cat "$STAGE/install.ps1"; } > "$STAGE/install.ps1.tmp" && mv "$STAGE/install.ps1.tmp" "$STAGE/install.ps1"
fi

# README — UTF-8 с BOM, чтобы старый Блокнот не показал кракозябры
{ printf '\xef\xbb\xbf'; cat "$STAGE/README_RU.txt"; } > "$STAGE/README.tmp" && mv "$STAGE/README.tmp" "$STAGE/README_RU.txt"

rm -f "$OUT"
( cd "$WORK/stage" && python3 -I - "$OUT" <<'PY'
import os, sys, zipfile
out = sys.argv[1]
with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
    for root, dirs, files in os.walk("UnityBoostRU"):
        dirs.sort()
        for n in sorted(files):
            p = os.path.join(root, n)
            # имена в UTF-8 (флаг 0x800 ставится автоматически для не-ASCII)
            z.write(p, p)
PY
)
ls -la "$OUT"
echo "готово: $OUT"
