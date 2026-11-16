#!/bin/bash
# Собирает ZIP шейдерпака: tools/pack_shader.sh <выходной.zip>
set -e
D=$(cd "$(dirname "$0")/.." && pwd)
python3 "$D/tools/gen_shader_stubs.py" >/dev/null
OUT=$(readlink -f "$1"); rm -f "$OUT"
(cd "$D/shaders/ShaderLite" && zip -q -r -X "$OUT" shaders LICENSE.txt README.txt 2>/dev/null || (cd "$D/shaders/ShaderLite" && zip -q -r -X "$OUT" shaders))
echo "packed $OUT ($(stat -c %s "$OUT") bytes)"
