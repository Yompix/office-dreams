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
MODELS = ['dead_tree_trunk', 'modular_electricity_poles',
          # заправка «24»: касса, телевизор с новостями, полки, продукты, коробки, канистры, шины, знак «мокрый пол»; блокнот Айрис
          'CashRegister_01', 'Television_01', 'Shelf_01', 'long_life_food', 'cardboard_box_01', 'metal_jerrycan', 'old_tyre',
          'WetFloorSign_01', 'binder_notebook', 'stationery_supplies']
TREE_TEX = ['fir_tree_01', 'pine_tree_01']   # у этих моделей сетки — фотосканы по 0.5–1 ГБ: берём только текстуры хвои и коры, ели строим кодом
TEXTURES = ['asphalt_snow', 'snow_02',
            # ткани для одежды героев, которую генератор шьёт сам (tools/lake/garments.py)
            'brown_leather', 'knitted_fleece', 'wool_boucle', 'cotton_jersey', 'poly_wool_herringbone', 'denim_fabric',
            # заправка: стены, бетон, плитка в магазине, металл навеса
            'painted_plaster_wall', 'concrete_floor_02', 'floor_tiles_06', 'corrugated_iron', 'metal_plate',
            # кафе «У трёх сосен» (глава 2): пол, обшивка снаружи и внутри, столы, стойка
            'old_wood_floor', 'weathered_plank_siding', 'wooden_panels', 'wood_table_worn', 'kitchen_wood']
HDRIS = ['narrow_moonlit_road']
TWIG_2K = {'fir_tree_01': ['twig_diff', 'twig_alpha', 'twig_nor_gl']}   # ели карточками: атлас лап с прозрачностью (road.gd)


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
    for m in TREE_TEX:
        g = files(m)['gltf']['1k']['gltf']
        for rel, f in g.get('include', {}).items():
            if rel.endswith('.jpg') or rel.endswith('.png'):
                curl(f['url'], os.path.join(OUT, 'ph', m, rel))
        print('текстуры дерева', m)
    for m, kinds in TWIG_2K.items():
        for k in kinds:
            curl('https://dl.polyhaven.org/file/ph-assets/Models/jpg/2k/%s/%s_%s_2k.jpg' % (m, m, k), os.path.join(OUT, 'ph', m, 'textures', '%s_%s_2k.jpg' % (m, k)))
        print('лапы ели 2k', m)
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
        fp.write('Poly Haven assets — CC0 1.0 (public domain), https://polyhaven.com/license\n' + '\n'.join(MODELS + TREE_TEX + TEXTURES + HDRIS) + '\n')
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
    subprocess.run([sys.executable, os.path.join(ROOT, 'tools/lake/drawings.py')], check=True)   # рисунки Айрис и фото на стене


if __name__ == '__main__':
    main()
