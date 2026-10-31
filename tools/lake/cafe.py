"""Кафе для дальнобойщиков «У трёх сосен» (WHAT THE LAKE KEEPS, глава 2, сцены 4–6) — постройка кодом в Blender:
деревянный дом с двускатной крышей под снегом, крыльцо с навесом и ступенями, большие окна, вывеска (надпись ставит
игра — на языке игры); внутри — столы-кабинки у окон, стойка с табуретами, полки и кофемашина за стойкой, окно раздачи
на кухню, вешалка у двери, лампы над столами, туалет в углу (раковина, зеркало, кабинка); задняя дверь через кухню;
парковка со снегом и сугробами. Фуры, машины, телевизор, ёлки и люди — ставит игра.
Координаты — как в Godot (x — от дороги вправо, y — вверх, z — вдоль дороги), G() переводит в оси Blender.
  /home/user/tools/blender45/blender -b --factory-startup --python tools/lake/cafe.py -- godot/lake/assets/models/cafe.glb
"""
import bpy, bmesh, math, os, random, sys
from mathutils import Vector
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bl
from bl import M, mat

OUT = sys.argv[sys.argv.index('--') + 1]
bpy.ops.wm.read_factory_settings(use_empty=True)
random.seed(3)

M.update({
    'siding': mat('siding', (0.45, 0.3, 0.18), 0.85, tex='weathered_plank_siding', tint=(0.8, 0.6, 0.42), tile=0.4),
    'panel': mat('panel', (0.4, 0.26, 0.15), 0.6, tex='wooden_panels', tint=(0.85, 0.62, 0.42), tile=0.5),
    'floor': mat('floor', (0.35, 0.24, 0.15), 0.6, tex='old_wood_floor', tint=(0.85, 0.7, 0.55), tile=0.5, arm=True),
    'table': mat('table', (0.4, 0.28, 0.18), 0.45, tex='wood_table_worn', tint=(0.9, 0.75, 0.6), tile=0.8),
    'counter': mat('counter', (0.3, 0.2, 0.12), 0.5, tex='kitchen_wood', tint=(0.8, 0.65, 0.5), tile=0.7),
    'plaster_in': mat('plaster_in', (0.78, 0.72, 0.6), 0.9, tex='painted_plaster_wall', tint=(0.95, 0.86, 0.7), tile=0.5),
    'tiles': mat('tiles', (0.7, 0.7, 0.68), 0.35, tex='floor_tiles_06', tint=(0.9, 0.9, 0.88), tile=0.6, arm=True),
    'concrete': mat('concrete', (0.4, 0.4, 0.4), 0.85, tex='concrete_floor_02', tint=(0.75, 0.75, 0.73), tile=0.4, arm=True),
    'roof': mat('roof', (0.25, 0.28, 0.3), 0.6, 0.6, tex='corrugated_iron', tint=(0.45, 0.48, 0.5), tile=0.5),
    'snow': mat('snow', (0.8, 0.82, 0.87), 0.75),
    'frame': mat('frame', (0.2, 0.12, 0.07), 0.6),
    'glass': mat('glass', (0.12, 0.13, 0.12), 0.04, 0.0, alpha=0.18),
    'seat': mat('seat', (0.32, 0.05, 0.04), 0.45, coat=0.3),          # красный кожзам кабинок
    'chrome': mat('chrome', (0.6, 0.6, 0.62), 0.2, 0.9),
    'dark': mat('dark', (0.03, 0.03, 0.032), 0.5),
    'lamp': mat('lamp', (1.0, 0.9, 0.7), 0.3, emit=((1.0, 0.78, 0.48), 5.0)),
    'shade': mat('shade', (0.08, 0.2, 0.12), 0.4, 0.3),
    'sign': mat('sign', (0.25, 0.15, 0.08), 0.7),
    'white': mat('white', (0.82, 0.82, 0.8), 0.25),
    'mirror': mat('mirror', (0.7, 0.72, 0.75), 0.02, 1.0),
    'steel': mat('steel', (0.45, 0.46, 0.47), 0.3, 0.85),
    'menu': mat('menu', (0.9, 0.85, 0.7), 0.6, emit=((1.0, 0.92, 0.75), 0.4)),
    'pic': mat('pic', (0.15, 0.22, 0.16), 0.7),
})


def G(x, y, z):
    return Vector((x, -z, y))


def gb(name, size, pos, m, bevel=0.01, roty=0.0, uv=0.0, seg=2):
    return bl.rbox(name, (size[0], size[2], size[1]), G(*pos), m, bevel, seg, rot=(0, 0, roty), uv=uv)


def gcyl(name, r, h, pos, m, seg=20, r2=None):
    return bl.cyl(name, r, h, G(*pos), m, axis='Z', seg=seg, r2=r2)


X0, X1, Z0, Z1, H, F = 22.0, 34.0, -15.0, 1.0, 3.2, 0.45     # фасад к дороге (x = X0), пол зала — F
DZ0, DZ1 = -7.6, -6.4                                          # входная дверь
ZC = (Z0 + Z1) / 2

# ---------- дом ----------
gb('slab', (X1 - X0, F, Z1 - Z0), ((X0 + X1) / 2, F / 2, ZC), 'concrete', 0.02, uv=1.0)
gb('floor', (X1 - X0 - 0.4, 0.012, Z1 - Z0 - 0.4), ((X0 + X1) / 2, F + 0.006, ZC), 'floor', 0.0, uv=1.0)
gb('wall_back', (0.22, H, Z1 - Z0), (X1 - 0.11, F + H / 2, ZC), 'siding', 0.0, uv=1.0)
for z in (Z0 + 0.11, Z1 - 0.11):
    gb('wall_side', (X1 - X0, H, 0.22), ((X0 + X1) / 2, F + H / 2, z), 'siding', 0.0, uv=1.0)
for z in (Z0 + 0.11, Z1 - 0.11):   # фронтоны
    bm = bmesh.new()
    pts = [G(X0, F + H, z), G(X1, F + H, z), G((X0 + X1) / 2, F + H + 1.7, z)]
    vs = [bm.verts.new(p) for p in pts]
    bm.faces.new(vs)
    me = bpy.data.meshes.new('gable')
    bm.to_mesh(me)
    bm.free()
    o = bl.obj('gable', me)
    me.materials.append(M['siding'])
    sol = o.modifiers.new('sol', 'SOLIDIFY')
    sol.thickness = 0.22
    bl.apply_mods(o)
    bl.box_uv(o, 1.0)
# крыша двускатная, железо под снегом
for side in (-1, 1):
    ang = math.atan2(1.7, (X1 - X0) / 2)
    w = math.hypot(1.7, (X1 - X0) / 2) + 0.6
    cx = (X0 + X1) / 2 + side * ((X1 - X0) / 4 + 0.12)
    o = gb('roof', (w, 0.08, Z1 - Z0 + 0.8), (cx, F + H + 0.85, ZC), 'roof', 0.0, uv=0.5)
    o.rotation_euler[1] = side * ang
    s = gb('roof_snow', (w - 0.1, 0.16, Z1 - Z0 + 0.7), (cx + side * 0.0, F + H + 0.97, ZC), 'snow', 0.06)
    s.rotation_euler[1] = side * ang
# фасад: низ, простенки, окна, верх; дверь на петле
gb('front_low', (0.22, 0.85, Z1 - Z0), (X0 + 0.11, F + 0.425, ZC), 'siding', 0.0, uv=1.0)
gb('front_head', (0.22, H - 2.35, Z1 - Z0), (X0 + 0.11, F + 2.35 + (H - 2.35) / 2, ZC), 'siding', 0.0, uv=1.0)
posts = [Z0 + 0.22, -12.4, -9.8, DZ0 - 0.1, DZ1 + 0.1, -3.8, -1.2, Z1 - 0.22]
for z in posts:
    gb('pier', (0.22, 1.5, 0.4 if z not in (Z0 + 0.22, Z1 - 0.22) else 0.22), (X0 + 0.11, F + 0.85 + 0.75, z), 'siding', 0.0, uv=1.0)
for a, b in zip(posts[:-1], posts[1:]):
    if a > DZ0 - 0.2 and b < DZ1 + 0.2:
        continue
    zc, wz = (a + b) / 2, b - a - 0.4
    gb('window', (0.03, 1.42, wz), (X0 + 0.12, F + 0.85 + 0.75, zc), 'glass', 0.0)
    gb('window_sill', (0.4, 0.06, wz + 0.1), (X0 + 0.05, F + 0.85, zc), 'frame', 0.01)
    gb('window_bar', (0.06, 0.05, wz), (X0 + 0.12, F + 0.85 + 0.75, zc), 'frame', 0.005)
    gb('window_mull', (0.06, 1.42, 0.05), (X0 + 0.12, F + 0.85 + 0.75, zc), 'frame', 0.005)
gb('door_head', (0.22, 0.95, DZ1 - DZ0 + 0.2), (X0 + 0.11, F + 2.25 + 0.475, (DZ0 + DZ1) / 2), 'siding', 0.0)
door = gb('cafe_door', (0.06, 2.2, DZ1 - DZ0 - 0.04), (X0 + 0.11, F + 1.1, (DZ0 + DZ1) / 2), 'frame', 0.01)
gb('cafe_door_glass', (0.07, 0.9, 0.7), (X0 + 0.11, F + 1.5, (DZ0 + DZ1) / 2), 'glass', 0.0)
bl.origin_to(door, G(X0 + 0.11, F, DZ0))
# крыльцо, навес на столбах, ступени, вывеска над навесом (надпись — игра)
PX0 = X0 - 2.2
gb('porch', (2.2, F, 6.0), (X0 - 1.1, F / 2, -7.0), 'floor', 0.02, uv=1.0)
gb('porch_snow', (2.0, 0.04, 0.6), (X0 - 1.0, F + 0.02, -9.6), 'snow', 0.02)
for k in range(2):
    gb('step', (0.35, F / 2 * (2 - k), 2.0), (PX0 - 0.18 - 0.35 * k, F / 4 * (2 - k), -7.0), 'floor', 0.01)
for z in (-9.8, -4.2):
    gb('porch_post', (0.16, 2.7, 0.16), (PX0 + 0.12, F + 1.35, z), 'frame', 0.01)
gb('porch_roof', (2.6, 0.1, 6.4), (X0 - 1.15, F + 2.75, -7.0), 'roof', 0.0, uv=0.5)
gb('porch_roof_snow', (2.5, 0.14, 6.3), (X0 - 1.15, F + 2.86, -7.0), 'snow', 0.05)
gb('porch_rail', (0.06, 0.06, 2.0), (PX0 + 0.12, F + 0.9, -9.0), 'frame', 0.005)
gb('porch_rail', (0.06, 0.06, 2.0), (PX0 + 0.12, F + 0.9, -5.0), 'frame', 0.005)
gb('sign_board', (0.12, 0.9, 5.2), (X0 - 0.12, F + 3.45, -7.0), 'sign', 0.02)
gb('sign_lamp', (0.5, 0.06, 0.12), (X0 - 0.45, F + 4.0, -7.0), 'lamp', 0.01)
gb('porch_lamp', (0.14, 0.2, 0.14), (X0 - 0.1, F + 2.3, -8.1), 'lamp', 0.02)

# ---------- зал ----------
gb('ceiling', (X1 - X0 - 0.44, 0.04, Z1 - Z0 - 0.44), ((X0 + X1) / 2, F + H - 0.02, ZC), 'plaster_in', 0.0, uv=1.0)
for z, s in ((Z0 + 0.23, 1), (Z1 - 0.23, -1)):
    gb('wall_in_side', (X1 - X0 - 0.44, H - 1.1, 0.02), ((X0 + X1) / 2, F + 1.1 + (H - 1.1) / 2, z), 'plaster_in', 0.0, uv=1.0)
    gb('wainscot', (X1 - X0 - 0.44, 1.1, 0.03), ((X0 + X1) / 2, F + 0.55, z), 'panel', 0.0, uv=1.0)
gb('wall_in_back', (0.02, H - 1.1, Z1 - Z0 - 0.44), (X1 - 0.23, F + 1.1 + (H - 1.1) / 2, ZC), 'plaster_in', 0.0, uv=1.0)
gb('wainscot_back', (0.03, 1.1, Z1 - Z0 - 0.44), (X1 - 0.23, F + 0.55, ZC), 'panel', 0.0, uv=1.0)
gb('wainscot_front', (0.03, 0.85, Z1 - Z0 - 0.44), (X0 + 0.23, F + 0.425, ZC), 'panel', 0.0, uv=1.0)
# столы-кабинки у окон: стол поперёк, по бокам — диваны с высокой спинкой
BOOTHS = [-13.4, -11.1, -2.6, -0.3]
for zc in BOOTHS:
    gb('table', (1.3, 0.05, 0.75), (23.05, F + 0.76, zc), 'table', 0.01)
    gcyl('table_leg', 0.05, 0.72, (23.35, F + 0.37, zc), 'chrome', 12)
    for s in (-1, 1):
        z = zc + s * 0.78
        gb('bench_seat', (1.35, 0.14, 0.5), (23.08, F + 0.42, z), 'seat', 0.04, seg=3)
        gb('bench_base', (1.3, 0.35, 0.45), (23.08, F + 0.17, z), 'frame', 0.01)
        gb('bench_back', (1.35, 0.7, 0.14), (23.08, F + 0.85, z + s * 0.27), 'seat', 0.05, seg=3)
    gb('napkins', (0.1, 0.12, 0.08), (22.58, F + 0.84, zc - 0.2), 'white', 0.01)
    gcyl('ketchup', 0.03, 0.18, (22.6, F + 0.87, zc + 0.15), 'seat', 10)
    # лампа над столом
    gcyl('pend_cord', 0.006, 0.9, (23.05, F + H - 0.47, zc), 'dark', 6)
    gcyl('pend_shade', 0.22, 0.2, (23.05, F + H - 1.02, zc), 'shade', 18, r2=0.06)
    gb('pend_bulb', (0.12, 0.08, 0.12), (23.05, F + H - 1.12, zc), 'lamp', 0.04)
# стойка вдоль задней стены, табуреты
CX = 31.0
gb('counter', (0.75, 1.0, 9.0), (CX, F + 0.5, -7.5), 'counter', 0.02, uv=1.0)
gb('counter_top', (0.95, 0.05, 9.2), (CX - 0.05, F + 1.025, -7.5), 'table', 0.01, uv=1.0)
gb('counter_kick', (0.77, 0.12, 9.0), (CX, F + 0.06, -7.5), 'dark', 0.0)
for k in range(6):
    z = -11.4 + k * 1.5
    gcyl('stool_post', 0.035, 0.68, (CX - 0.9, F + 0.34, z), 'chrome', 10)
    gcyl('stool_seat', 0.2, 0.08, (CX - 0.9, F + 0.72, z), 'seat', 18)
    gcyl('stool_foot', 0.17, 0.03, (CX - 0.9, F + 0.015, z), 'chrome', 16)
# за стойкой: полки с банками, кофемашина, кассовый ящик, меню на стене, окно раздачи
gb('backbar', (0.45, 0.95, 7.0), (X1 - 0.5, F + 0.475, -7.5), 'counter', 0.01)
gb('backbar_top', (0.5, 0.04, 7.1), (X1 - 0.5, F + 0.97, -7.5), 'steel', 0.005)
gb('coffee_machine', (0.4, 0.55, 0.6), (X1 - 0.55, F + 1.27, -9.6), 'steel', 0.03)
gb('coffee_pot', (0.15, 0.2, 0.15), (X1 - 0.75, F + 1.09, -9.3), 'dark', 0.03)
for k in range(3):
    gb('shelf', (0.25, 0.03, 3.0), (X1 - 0.38, F + 1.55 + k * 0.38, -5.6), 'counter', 0.005)
    zz = -6.95
    while zz < -4.25:
        w = random.uniform(0.08, 0.16)
        c = random.choice([(0.6, 0.15, 0.05), (0.2, 0.35, 0.15), (0.75, 0.65, 0.3), (0.5, 0.1, 0.1), (0.85, 0.85, 0.8)])
        key = 'jar%d%d%d' % tuple(int(v * 10) for v in c)
        pm = M.get(key) or M.setdefault(key, mat(key, c, 0.4))
        gb('jars', (0.14, random.uniform(0.14, 0.26), w), (X1 - 0.38, F + 1.57 + k * 0.38 + 0.09, zz + w / 2), pm, 0.01)
        zz += w + 0.025
gb('menu_board', (0.04, 0.9, 1.8), (X1 - 0.26, F + 2.35, -10.0), 'menu', 0.01)
gb('pass_window', (0.24, 0.7, 1.6), (X1 - 0.11, F + 1.45, -2.4), 'dark', 0.0)
gb('pass_shelf', (0.5, 0.04, 1.7), (X1 - 0.3, F + 1.1, -2.4), 'steel', 0.005)
# вешалка у двери, картины с соснами, ёлочный освежитель на стойке не нужен — тихо
gb('coat_rack', (0.06, 0.06, 1.4), (X0 + 0.3, F + 1.8, -8.6), 'frame', 0.005)
for k in range(4):
    gb('coat_hook', (0.12, 0.03, 0.03), (X0 + 0.36, F + 1.75, -9.15 + k * 0.36), 'chrome', 0.005)
gb('picture', (0.03, 0.55, 0.8), (X1 - 0.25, F + 2.1, 0.0), 'pic', 0.01)        # картины с соснами
for z in (Z0 + 0.25, Z1 - 0.25):
    gb('picture', (0.8, 0.55, 0.03), (26.5, F + 2.1, z), 'pic', 0.01)
# лампы под потолком над стойкой
for z in (-11.0, -7.5, -4.0):
    gb('ceiling_lamp', (0.5, 0.06, 0.5), (CX - 0.2, F + H - 0.05, z), 'lamp', 0.02)
gb('ceiling_lamp', (0.5, 0.06, 0.5), (27.0, F + H - 0.05, -7.0), 'lamp', 0.02)

# ---------- туалет в углу (z от Z0 до −12,6, x от 29,4): перегородка с дверью, раковина, зеркало, кабинка ----------
TZ, TX = -12.6, 29.4
gb('wc_wall', (X1 - TX, H, 0.12), ((TX + X1) / 2, F + H / 2, TZ), 'plaster_in', 0.0, uv=1.0)
gb('wc_wall_side', (0.12, H, TZ - Z0), (TX, F + H / 2, (Z0 + TZ) / 2), 'plaster_in', 0.0, uv=1.0)
gb('wc_floor', (X1 - TX - 0.3, 0.014, TZ - Z0 - 0.3), ((TX + X1) / 2, F + 0.014, (Z0 + TZ) / 2), 'tiles', 0.0, uv=1.0)
gb('wc_door', (0.05, 2.05, 0.8), (TX, F + 1.03, -13.5), 'frame', 0.01)
gb('sink', (0.45, 0.16, 0.55), (X1 - 0.5, F + 0.86, -13.8), 'white', 0.05, seg=3)
gb('sink_bowl', (0.32, 0.03, 0.4), (X1 - 0.5, F + 0.95, -13.8), 'steel', 0.01)
gcyl('sink_tap', 0.02, 0.2, (X1 - 0.33, F + 1.05, -13.8), 'chrome', 10)
gb('sink_mirror', (0.02, 0.75, 0.6), (X1 - 0.26, F + 1.6, -13.8), 'mirror', 0.005)
gb('mirror_frame', (0.015, 0.8, 0.65), (X1 - 0.255, F + 1.6, -13.8), 'frame', 0.005)
gb('stall', (1.3, 2.0, 0.05), (31.2, F + 1.05, -14.0), 'white', 0.005)
gb('wc_lamp', (0.4, 0.05, 0.4), (32.0, F + H - 0.05, -13.8), 'lamp', 0.02)
# задняя дверь (кухня → двор)
gb('back_door', (0.06, 2.1, 0.9), (X1 - 0.01, F + 1.05, -1.0), 'frame', 0.01)
gb('back_step', (0.9, F, 1.2), (X1 + 0.45, F / 2, -1.0), 'concrete', 0.02)

# ---------- парковка и сугробы ----------
gb('lot', (34.0, 0.04, 70.0), (23.0, 0.02, -10.0), 'concrete', 0.0, uv=0.6)
for (x, z, sx, sz) in [(7.5, -40.0, 5.0, 2.2), (20.0, -42.0, 9.0, 2.6), (38.0, -30.0, 2.2, 8.0), (38.5, -6.0, 2.5, 10.0),
                       (37.0, 14.0, 2.4, 5.0), (24.0, 22.0, 9.0, 2.2), (9.0, 22.5, 5.0, 2.0), (21.0, 2.6, 1.0, 0.8), (21.2, -16.4, 1.2, 1.0)]:
    bm = bmesh.new()
    bmesh.ops.create_uvsphere(bm, u_segments=20, v_segments=10, radius=1.0)
    for v in bm.verts:
        n = math.sin(v.co.x * 3.1 + x) * math.cos(v.co.y * 2.7 + z) * 0.12
        v.co = Vector((v.co.x * sx * (1 + n), v.co.y * sz * (1 + n), max(v.co.z, -0.1) * random.uniform(0.7, 1.2)))
    me = bpy.data.meshes.new('snowbank')
    bm.to_mesh(me)
    bm.free()
    o = bl.obj('snowbank', me)
    o.location = G(x, 0.0, z)
    me.materials.append(M['snow'])
    bl.smooth(o, 80)

for pre in ('jars',):
    parts = [o for o in bpy.data.objects if o.type == 'MESH' and o.name.startswith(pre)]
    if parts:
        bl.join(pre + '_all', parts)
bl.export(OUT)
