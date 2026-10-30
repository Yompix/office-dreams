#!/usr/bin/env python3
"""Свободные ассеты WHAT THE LAKE KEEPS (CC0, Poly Haven — polyhaven.com/license) в godot/lake/assets/ph/ (в git не идут).
Модели — glTF 1k с текстурами, текстуры — 1k (цвет, нормали GL, шероховатость/AO), небо — HDRI 1k.
Герои и клипы движений (MakeHuman CC0, Quaternius CC0, мокап Rokoko) — жёсткие ссылки из public/games/nightshift/models,
как у Hush Till Six (tools/godot/sync_assets.sh), без meshopt — через tools/godot/unpack_glb.mjs.
  python3 tools/lake/fetch_assets.py
"""
import json, os, subprocess, sys
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '../..'))
OUT = os.path.join(ROOT, 'godot/lake/assets')
MODELS = ['fir_tree_01', 'pine_tree_01', 'fir_sapling_medium', 'dead_tree_trunk', 'modular_electricity_poles']
TEXTURES = ['asphalt_snow', 'snow_02']
HDRIS = ['narrow_moonlit_road']


def curl(url, dst):
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    if os.path.exists(dst) and os.path.getsize(dst) > 0:
        return
    subprocess.run(['curl', '-sSfL', '--retry', '3', '-o', dst, url], check=True)


def files(asset):
    return json.loads(subprocess.run(['curl', '-sSfL', 'https://api.polyhaven.com/files/' + asset], check=True, capture_output=True).stdout)


def main():
    for m in MODELS:
        g = files(m)['gltf']['1k']['gltf']
        d = os.path.join(OUT, 'ph', m)
        curl(g['url'], os.path.join(d, m + '.gltf'))
        for rel, f in g.get('include', {}).items():
            curl(f['url'], os.path.join(d, rel))
        print('модель', m)
    for t in TEXTURES:
        f = files(t)
        d = os.path.join(OUT, 'ph', t)
        for kind, key in [('Diffuse', 'diff'), ('nor_gl', 'nor_gl'), ('arm', 'arm')]:
            if kind in f and '1k' in f[kind]:
                ent = f[kind]['1k'].get('jpg') or f[kind]['1k'].get('png')
                curl(ent['url'], os.path.join(d, '%s_%s_1k.jpg' % (t, key)))
        print('текстура', t)
    for h in HDRIS:
        f = files(h)['hdri']['1k']['hdr']
        curl(f['url'], os.path.join(OUT, 'ph', h + '_1k.hdr'))
        print('небо', h)
    with open(os.path.join(OUT, 'ph', 'LICENSE.txt'), 'w') as fp:
        fp.write('Poly Haven assets — CC0 1.0 (public domain), https://polyhaven.com/license\n' + '\n'.join(MODELS + TEXTURES + HDRIS) + '\n')
    # герои и движения — из Hush Till Six (CC0 / свободный мокап), жёсткие ссылки
    src = os.path.join(ROOT, 'public/games/nightshift/models')
    dst = os.path.join(OUT, 'models')
    os.makedirs(dst, exist_ok=True)
    subprocess.run(['node', os.path.join(ROOT, 'tools/godot/unpack_glb.mjs'), src, dst], check=True)
    for n in os.listdir(src):
        if n.startswith('LICENSE'):
            p = os.path.join(dst, n)
            if not os.path.exists(p):
                os.link(os.path.join(src, n), p)
    print('герои:', sorted(n for n in os.listdir(dst) if n.endswith('.glb')))


if __name__ == '__main__':
    main()
