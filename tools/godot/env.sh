#!/usr/bin/env bash
# Godot в облаке: редактор 4.7.2 в /home/user/tools/godot, программная графика (Mesa: lavapipe для Forward+, llvmpipe для сайта).
# Если Godot нет: tools/godot/install.sh
export GODOT="${GODOT:-/home/user/tools/godot/godot}"
export VK_DRIVER_FILES=/usr/share/vulkan/icd.d/lvp_icd.json VK_ICD_FILENAMES=/usr/share/vulkan/icd.d/lvp_icd.json
# gd_pc  — как на ПК (Forward+: живой свет, объёмный туман);  gd_web — как на сайте (Compatibility)
gd_pc()  { xvfb-run -a -s "-screen 0 1920x1080x24" "$GODOT" --rendering-driver vulkan --rendering-method forward_plus "$@"; }
gd_web() { LIBGL_ALWAYS_SOFTWARE=1 xvfb-run -a -s "-screen 0 1920x1080x24" "$GODOT" --rendering-driver opengl3 --rendering-method gl_compatibility "$@"; }
