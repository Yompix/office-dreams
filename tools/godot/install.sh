#!/usr/bin/env bash
# Ставит Godot 4.7.2 (редактор + шаблоны экспорта Windows/Web/Linux) и программный Vulkan для проверки картинки в облаке.
set -e
V=4.7.2-stable; D=/home/user/tools/godot; T=~/.local/share/godot/export_templates/4.7.2.stable
mkdir -p "$D" "$T"; cd "$D"
[ -x "Godot_v${V}_linux.x86_64" ] || { curl -sSL -o g.zip "https://github.com/godotengine/godot/releases/download/$V/Godot_v${V}_linux.x86_64.zip"; unzip -o -q g.zip; rm g.zip; }
ln -sf "$D/Godot_v${V}_linux.x86_64" "$D/godot"
if [ ! -f "$T/version.txt" ]; then
  curl -sSL -o t.tpz "https://github.com/godotengine/godot/releases/download/$V/Godot_v${V}_export_templates.tpz"
  unzip -o -q -j t.tpz templates/version.txt templates/web_release.zip templates/web_debug.zip templates/web_nothreads_release.zip templates/web_nothreads_debug.zip templates/windows_release_x86_64.exe templates/windows_release_x86_64_console.exe templates/windows_debug_x86_64.exe templates/windows_debug_x86_64_console.exe templates/linux_release.x86_64 templates/linux_debug.x86_64 -d "$T"
  rm t.tpz
fi
[ -f /usr/share/vulkan/icd.d/lvp_icd.json ] || apt-get install -y -q mesa-vulkan-drivers
"$D/godot" --version
