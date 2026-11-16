#!/usr/bin/env python3
"""Создаёт короткие файлы программ (*.vsh/*.fsh) для каждого измерения: сам код лежит в shaders/program/*.glsl.
Запуск: python3 tools/gen_shader_stubs.py"""
import os, shutil
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'shaders', 'ShaderLite', 'shaders')
P = {
    'shadow': ('shadow', []),
    'gbuffers_terrain': ('gbuffers_solid', ['TERRAIN']),
    'gbuffers_entities': ('gbuffers_solid', ['ENTITY']),
    'gbuffers_block': ('gbuffers_solid', []),
    'gbuffers_hand': ('gbuffers_solid', []),
    'gbuffers_spidereyes': ('gbuffers_forward', ['UNLIT 2.0']),
    'gbuffers_armor_glint': ('gbuffers_forward', ['UNLIT 1.0']),
    'gbuffers_damagedblock': ('gbuffers_forward', ['RAW']),
    'gbuffers_water': ('gbuffers_water', []),
    'gbuffers_hand_water': ('gbuffers_forward', []),
    'gbuffers_textured': ('gbuffers_forward', []),
    'gbuffers_textured_lit': ('gbuffers_forward', []),
    'gbuffers_weather': ('gbuffers_forward', ['WEATHER']),
    'gbuffers_clouds': ('gbuffers_forward', ['CLOUDS']),
    'gbuffers_basic': ('gbuffers_forward', ['NOTEX', 'UNLIT 1.0']),
    'gbuffers_line': ('gbuffers_forward', ['NOTEX', 'UNLIT 1.0']),
    'gbuffers_beaconbeam': ('gbuffers_forward', ['UNLIT 2.0']),
    'gbuffers_skybasic': ('gbuffers_sky', []),
    'gbuffers_skytextured': ('gbuffers_sky', ['SKYTEXTURED']),
    'deferred': ('deferred', []),
    'deferred1': ('deferred1', []),
    'composite': ('bloom', ['BLOOM_PASS 0']),
    'composite1': ('bloom', ['BLOOM_PASS 1']),
    'composite2': ('bloom', ['BLOOM_PASS 2']),
    'final': ('final', []),
}
DIMS = {'': [], 'world-1': ['NETHER'], 'world1': ['END']}
for d, ddefs in DIMS.items():
    out = os.path.join(ROOT, d) if d else ROOT
    if d: shutil.rmtree(out, ignore_errors=True)
    os.makedirs(out, exist_ok=True)
    for name, (src, defs) in P.items():
        if name == 'shadow' and d: continue          # в Незере и Энде теней от солнца нет — проход не нужен
        for st in ('vsh', 'fsh'):
            lines = ['#version 120', f'#define {st.upper()}'] + [f'#define {x}' for x in ddefs + defs]
            lines.append(f'#include "/program/{src}.glsl"')
            open(os.path.join(out, f'{name}.{st}'), 'w').write('\n'.join(lines) + '\n')
print('ok')
