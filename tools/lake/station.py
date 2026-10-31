"""Лесная заправка «24» (WHAT THE LAKE KEEPS, глава 1, сцены 5–6) — постройки кодом в Blender:
навес на четырёх столбах с лампами снизу и «24» на фризе, два островка с колонками (табло, пистолеты, шланги, столбики),
магазин с витриной и стеклянной дверью (на петле — её откроют и разобьют), крыльцо-ступенька (на нём сидит Дэнни),
внутри — плитка, лампы, прилавок, стеллажи с товаром, холодильник с напитками, полка за кассиром; вывеска «24» на столбе;
сугробы по краям площадки. Реквизит (касса, телевизор, канистры, шины…) — модели Poly Haven, ставит игра.
Координаты пишем как в Godot (x — от дороги вправо, y — вверх, z — вдоль дороги), G() переводит в оси Blender.
  /home/user/tools/blender45/blender -b --factory-startup --python tools/lake/station.py -- godot/lake/assets/models/station.glb
"""
import bpy, bmesh, math, os, random, sys
from mathutils import Vector
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bl
from bl import M, mat

OUT = sys.argv[sys.argv.index('--') + 1]
bpy.ops.wm.read_factory_settings(use_empty=True)
random.seed(24)
FONT = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'

M.update({
    'white': mat('white', (0.72, 0.72, 0.7), 0.5),
    'red': mat('red', (0.42, 0.025, 0.025), 0.45),
    'yellow': mat('yellow', (0.75, 0.55, 0.04), 0.5),
    'steel': mat('steel', (0.35, 0.36, 0.37), 0.4, 0.8),
    'dark': mat('dark', (0.03, 0.03, 0.032), 0.5),
    'under': mat('under', (0.5, 0.5, 0.5), 0.6, 0.5, tex='metal_plate', tint=(0.55, 0.55, 0.55), tile=0.5),
    'concrete': mat('concrete', (0.4, 0.4, 0.4), 0.85, tex='concrete_floor_02', tint=(0.8, 0.8, 0.78), tile=0.4, arm=True),
    'plaster': mat('plaster', (0.6, 0.58, 0.55), 0.9, tex='painted_plaster_wall', tint=(0.78, 0.74, 0.66), tile=0.35),
    'plaster_in': mat('plaster_in', (0.7, 0.7, 0.68), 0.9, tex='painted_plaster_wall', tint=(0.86, 0.86, 0.82), tile=0.5),
    'tiles': mat('tiles', (0.6, 0.6, 0.6), 0.4, tex='floor_tiles_06', tint=(0.85, 0.85, 0.82), tile=0.5, arm=True),
    'iron': mat('iron', (0.4, 0.4, 0.4), 0.6, 0.6, tex='corrugated_iron', tint=(0.5, 0.52, 0.55), tile=0.5),
    'glass': mat('glass', (0.1, 0.12, 0.13), 0.03, 0.0, alpha=0.2),
    'lamp': mat('lamp', (0.95, 0.95, 0.92), 0.3, emit=((1.0, 0.98, 0.92), 6.0)),
    'tube': mat('tube', (0.95, 0.95, 0.92), 0.3, emit=((0.92, 0.97, 1.0), 5.0)),
    'neon': mat('neon', (0.6, 0.05, 0.03), 0.3, emit=((1.0, 0.16, 0.06), 9.0)),
    'signface': mat('signface', (0.85, 0.85, 0.8), 0.4, emit=((1.0, 0.96, 0.88), 0.75)),
    'signred': mat('signred', (0.45, 0.02, 0.015), 0.4, emit=((0.55, 0.03, 0.015), 0.6)),   # цифры на подсвеченном поле
    'display': mat('display', (0.05, 0.12, 0.05), 0.2, emit=((1.0, 0.62, 0.15), 2.0)),
    'fridge': mat('fridge', (0.7, 0.8, 0.85), 0.1, emit=((0.75, 0.88, 1.0), 1.4)),
    'counter': mat('counter', (0.2, 0.14, 0.09), 0.45),
    'top': mat('top', (0.5, 0.48, 0.45), 0.3),
    'snow': mat('snow', (0.78, 0.8, 0.85), 0.75),
    'hose': mat('hose', (0.015, 0.015, 0.015), 0.6),
    'ice': mat('ice', (0.1, 0.35, 0.75), 0.4),
})


def G(x, y, z):
    return Vector((x, -z, y))


def gb(name, size, pos, m, bevel=0.01, roty=0.0, uv=0.0, seg=2):
    """брусок в осях Godot: size (x, y, z), pos (x, y, z), поворот вокруг вертикали"""
    return bl.rbox(name, (size[0], size[2], size[1]), G(*pos), m, bevel, seg, rot=(0, 0, roty), uv=uv)


def gcyl(name, r, h, pos, m, seg=20, r2=None):
    return bl.cyl(name, r, h, G(*pos), m, axis='Z', seg=seg, r2=r2)


def hose(name, pts, r=0.016):
    cu = bpy.data.curves.new(name, 'CURVE')
    cu.dimensions = '3D'
    cu.bevel_depth = r
    cu.bevel_resolution = 3
    sp = cu.splines.new('NURBS')
    sp.points.add(len(pts) - 1)
    for p, q in zip(sp.points, pts):
        v = G(*q)
        p.co = (v.x, v.y, v.z, 1.0)
    sp.use_endpoint_u = True
    sp.order_u = 3
    o = bl.obj(name, cu)
    bl.select_only(o)
    bpy.ops.object.convert(target='MESH')
    o = bpy.context.view_layer.objects.active
    o.data.materials.append(M['hose'])
    return o


def letters(name, body, size, pos, face, m='neon', depth=0.05):
    """надпись стоймя лицом к +X (face=1) или −X (face=−1) в осях Godot"""
    o = bl.text_mesh(name, body, size, m, depth, FONT)
    o.rotation_euler = (math.radians(90), 0, math.radians(90 * face))
    o.location = G(*pos)
    return o


# ---------- навес ----------
CX, CZ = 14.0, -5.0
gb('canopy_roof', (9.0, 0.14, 13.0), (CX, 4.82, CZ), 'under', 0.0, uv=0.5)
for sz in (-1, 1):
    gb('fascia', (9.3, 0.75, 0.16), (CX, 4.7, CZ + sz * 6.58), 'white', 0.02)
    gb('fascia_stripe', (9.32, 0.12, 0.17), (CX, 4.42, CZ + sz * 6.58), 'red', 0.0)
for sx in (-1, 1):
    gb('fascia', (0.16, 0.75, 13.32), (CX + sx * 4.58, 4.7, CZ), 'white', 0.02)
    gb('fascia_stripe', (0.17, 0.12, 13.34), (CX + sx * 4.58, 4.42, CZ), 'red', 0.0)
letters('canopy_24', '24', 0.62, (CX - 4.68, 4.72, CZ), -1)
letters('canopy_24b', '24', 0.62, (CX + 4.68, 4.72, CZ), 1)
for x in (12.0, 16.0):
    for z in (-9.0, -5.0, -1.0):
        gb('canopy_lamp', (1.3, 0.05, 0.42), (x, 4.73, z), 'lamp', 0.01)
for x in (12.0, 16.0):
    for z in (-8.7, -1.3):
        gb('column', (0.32, 4.75, 0.32), (x, 2.37, z), 'white', 0.03)
        gb('column_base', (0.36, 0.45, 0.36), (x, 0.4, z), 'red', 0.02)

# ---------- островки и колонки ----------
for x in (12.0, 16.0):
    gb('island', (1.3, 0.18, 8.2), (x, 0.09, -5.0), 'concrete', 0.06, uv=1.0)
    for z in (-9.25, -0.75):
        gcyl('bollard', 0.085, 0.95, (x, 0.18 + 0.475, z), 'yellow', 16)
    b = 0.18
    gb('pump_base', (0.62, 0.12, 1.08), (x, b + 0.06, -5.0), 'dark', 0.02)
    gb('pump', (0.56, 1.45, 1.0), (x, b + 0.12 + 0.725, -5.0), 'white', 0.03)
    gb('pump_head', (0.6, 0.36, 1.06), (x, b + 1.57 + 0.18, -5.0), 'red', 0.03)
    for s in (-1, 1):
        gb('pump_display', (0.02, 0.2, 0.52), (x + s * 0.285, b + 1.25, -5.0), 'display', 0.004)
        gb('pump_panel', (0.015, 0.34, 0.86), (x + s * 0.284, b + 0.62, -5.0), 'dark', 0.004)
        for dz in (-0.3, 0.3):
            gb('holster', (0.08, 0.2, 0.11), (x + s * 0.31, b + 1.0, -5.0 + dz), 'dark', 0.01)
            gb('nozzle', (0.06, 0.24, 0.05), (x + s * 0.34, b + 0.98, -5.0 + dz), 'dark', 0.01, roty=0)
            hose('hose', [(x + s * 0.27, b + 1.5, -5.0 + dz * 1.3), (x + s * 0.55, b + 1.1, -5.0 + dz * 1.6),
                          (x + s * 0.62, b + 0.25, -5.0 + dz * 1.5), (x + s * 0.45, b + 0.4, -5.0 + dz * 1.2), (x + s * 0.35, b + 0.9, -5.0 + dz)])

# ---------- магазин ----------
X0, X1, Z0, Z1, H, F = 21.0, 31.0, -12.0, 1.0, 3.6, 0.38   # F — пол магазина и крыльцо (на нём сидит Дэнни)
gb('shop_slab', (X1 - X0, F, Z1 - Z0), ((X0 + X1) / 2, F / 2, (Z0 + Z1) / 2), 'concrete', 0.02, uv=1.0)
gb('shop_floor', (X1 - X0 - 0.5, 0.012, Z1 - Z0 - 0.5), ((X0 + X1) / 2, F + 0.006, (Z0 + Z1) / 2), 'tiles', 0.0, uv=1.0)
gb('wall_back', (0.25, H, Z1 - Z0), (X1 - 0.125, F + H / 2, (Z0 + Z1) / 2), 'plaster', 0.0, uv=1.0)
for z in (Z0 + 0.125, Z1 - 0.125):
    gb('wall_side', (X1 - X0, H, 0.25), ((X0 + X1) / 2, F + H / 2, z), 'plaster', 0.0, uv=1.0)
# внутренняя отделка (светлее) — стены изнутри
gb('wall_back_in', (0.02, H - 0.3, Z1 - Z0 - 0.5), (X1 - 0.26, F + (H - 0.3) / 2, (Z0 + Z1) / 2), 'plaster_in', 0.0, uv=1.0)
for z, s in ((Z0 + 0.26, 1), (Z1 - 0.26, -1)):
    gb('wall_side_in', (X1 - X0 - 0.5, H - 0.3, 0.02), ((X0 + X1) / 2, F + (H - 0.3) / 2, z), 'plaster_in', 0.0, uv=1.0)
gb('ceiling', (X1 - X0 - 0.5, 0.04, Z1 - Z0 - 0.5), ((X0 + X1) / 2, F + H - 0.3, (Z0 + Z1) / 2), 'plaster_in', 0.0, uv=1.0)
gb('roof', (X1 - X0 + 0.4, 0.28, Z1 - Z0 + 0.4), ((X0 + X1) / 2, F + H + 0.14, (Z0 + Z1) / 2), 'concrete', 0.02, uv=0.5)
# витрина: низ стены, стойки, стёкла, верх с вывеской; дверь на петле
DZ0, DZ1 = -2.55, -1.45
gb('front_low', (0.25, 0.7, Z1 - Z0), (X0 + 0.125, F + 0.35, (Z0 + Z1) / 2), 'plaster', 0.0, uv=1.0)
gb('front_head', (0.25, H - 2.75, Z1 - Z0), (X0 + 0.125, F + 2.75 + (H - 2.75) / 2, (Z0 + Z1) / 2), 'plaster', 0.0, uv=1.0)
gb('shop_band', (0.06, 0.62, 7.0), (X0 - 0.03, F + 3.15, -5.5), 'signface', 0.01)
posts = [Z0 + 0.25, -9.6, -7.2, -4.8, DZ0 - 0.06, DZ1 + 0.06, -0.2, Z1 - 0.25]
for z in posts:
    gb('mullion', (0.14, 2.05, 0.12), (X0 + 0.125, F + 0.7 + 1.025, z), 'dark', 0.01)
for a, b in zip(posts[:-1], posts[1:]):
    if a > DZ0 - 0.1 and b < DZ1 + 0.1:
        continue                                                  # дверной проём
    gb('window', (0.03, 2.05, b - a - 0.12), (X0 + 0.125, F + 0.7 + 1.025, (a + b) / 2), 'glass', 0.0)
gb('door_head', (0.25, 0.55, DZ1 - DZ0 + 0.12), (X0 + 0.125, F + 2.2 + 0.275, (DZ0 + DZ1) / 2), 'plaster', 0.0)
d = [gb('door_frame', (0.06, 2.2, 0.06), (X0 + 0.125, F + 1.1, DZ0 + 0.03), 'dark', 0.01),
     gb('door_frame', (0.06, 2.2, 0.06), (X0 + 0.125, F + 1.1, DZ1 - 0.03), 'dark', 0.01),
     gb('door_frame', (0.06, 0.08, DZ1 - DZ0), (X0 + 0.125, F + 2.16, (DZ0 + DZ1) / 2), 'dark', 0.01),
     gb('door_frame', (0.06, 0.16, DZ1 - DZ0), (X0 + 0.125, F + 0.08, (DZ0 + DZ1) / 2), 'dark', 0.01),
     gb('door_bar', (0.05, 0.04, DZ1 - DZ0 - 0.2), (X0 + 0.07, F + 1.05, (DZ0 + DZ1) / 2), 'steel', 0.01)]
door = bl.join('shop_door', d)
glass = gb('shop_door_glass', (0.025, 1.92, DZ1 - DZ0 - 0.12), (X0 + 0.125, F + 1.12, (DZ0 + DZ1) / 2), 'glass', 0.0)
bl.origin_to(door, G(X0 + 0.125, F, DZ0))                        # петля — у левого края проёма
bl.origin_to(glass, G(X0 + 0.125, F, DZ0))
# крыльцо-ступенька вдоль витрины
gb('porch', (1.1, F, 10.0), (X0 - 0.55, F / 2, -5.0), 'concrete', 0.03, uv=1.0)
# лампы под потолком
for x in (23.6, 27.2):
    for z in (-9.5, -5.5, -1.5):
        gb('tube_lamp', (0.3, 0.07, 1.5), (x, F + H - 0.36, z), 'tube', 0.01)
# прилавок, касса стоит на нём (игра), полка с товаром за кассиром
gb('counter', (2.8, 1.0, 0.72), (26.4, F + 0.5, -8.4), 'counter', 0.02)
gb('counter_top', (2.9, 0.05, 0.82), (26.4, F + 1.025, -8.4), 'top', 0.01)
gb('counter_kick', (2.82, 0.12, 0.74), (26.4, F + 0.06, -8.38), 'dark', 0.0)
gb('backshelf', (3.2, 1.6, 0.36), (26.4, F + 1.2, -11.55), 'counter', 0.01)
for i in range(4):
    for j in range(14):
        c = random.choice([(0.6, 0.1, 0.08), (0.12, 0.25, 0.55), (0.8, 0.75, 0.6), (0.1, 0.4, 0.15), (0.75, 0.6, 0.1), (0.85, 0.85, 0.85)])
        pm = M.get('p%d_%d_%d' % tuple(int(v * 10) for v in c)) or M.setdefault('p%d_%d_%d' % tuple(int(v * 10) for v in c), mat('prod', c, 0.5))
        gb('prod', (0.18, 0.24, 0.12), (25.0 + j * 0.2, F + 0.62 + i * 0.36, -11.4), pm, 0.005)
gb('tv_shelf', (0.9, 0.05, 0.5), (26.4, F + 2.12, -11.62), 'steel', 0.005)
# стеллажи посреди зала: основание, спинка, четыре полки, товар
for x in (23.4, 25.6):
    gb('gondola_base', (0.9, 0.15, 4.2), (x, F + 0.075, -3.6), 'dark', 0.01)
    gb('gondola_back', (0.05, 1.55, 4.2), (x, F + 0.8, -3.6), 'white', 0.005)
    for k in range(4):
        y = F + 0.25 + k * 0.38
        gb('gondola_shelf', (0.88, 0.025, 4.2), (x, y, -3.6), 'white', 0.004)
        for side in (-1, 1):
            z = -5.55
            while z < -1.7:
                w = random.uniform(0.08, 0.22)
                h = random.uniform(0.12, 0.3)
                c = random.choice([(0.6, 0.1, 0.08), (0.12, 0.25, 0.55), (0.8, 0.75, 0.6), (0.1, 0.4, 0.15), (0.75, 0.6, 0.1),
                                   (0.85, 0.85, 0.85), (0.3, 0.15, 0.05), (0.9, 0.4, 0.1)])
                key = 'g%d_%d_%d' % tuple(int(v * 10) for v in c)
                pm = M.get(key) or M.setdefault(key, mat('prod', c, 0.55))
                if random.random() < 0.88:
                    gb('goods', (0.26, h, w), (x + side * 0.27, y + 0.013 + h / 2, z + w / 2), pm, 0.004)
                z += w + 0.02
# холодильник с напитками у задней стены
gb('fridge_body', (0.75, 2.15, 4.2), (X1 - 0.65, F + 1.075, -4.5), 'dark', 0.02)
gb('fridge_glow', (0.02, 1.85, 3.9), (X1 - 1.03, F + 1.1, -4.5), 'fridge', 0.0)
for z in (-6.45, -5.15, -3.85, -2.55):
    gb('fridge_frame', (0.04, 1.9, 0.05), (X1 - 1.05, F + 1.1, z), 'steel', 0.005)
gb('fridge_top', (0.8, 0.3, 4.25), (X1 - 0.65, F + 2.3, -4.5), 'red', 0.01)

# ---------- вывеска «24» у дороги ----------
SX, SZ = 5.6, 6.0
gcyl('sign_pole', 0.13, 7.4, (SX, 3.7, SZ), 'steel', 16)
gb('sign_box', (0.42, 2.0, 2.5), (SX, 6.4, SZ), 'white', 0.03)
for s in (-1, 1):
    gb('sign_face', (0.02, 1.8, 2.3), (SX + s * 0.215, 6.4, SZ), 'signface', 0.005)
    letters('sign_24', '24', 1.9, (SX + s * 0.25, 6.4, SZ), s, 'signred')
gb('sign_cap', (0.5, 0.12, 2.6), (SX, 7.45, SZ), 'red', 0.02)

# ---------- мелочи у магазина: ледник, урна, автомат воздуха ----------
gb('ice_chest', (0.62, 0.95, 1.4), (20.55, F + 0.475, 0.0), 'white', 0.03)
letters('ice_word', 'ICE', 0.28, (20.23, F + 0.6, 0.0), -1, 'ice', 0.01)
gcyl('trash', 0.26, 0.85, (16.0, 0.18 + 0.425, -0.35), 'dark', 18)
gb('air_pump', (0.45, 1.25, 0.5), (21.0 - 0.5, 0.625, 2.2), 'red', 0.03)

# ---------- сугробы по краям площадки ----------
for (x, z, sx, sz) in [(7.5, -24.0, 5.0, 2.2), (19.0, -25.0, 7.0, 2.6), (33.0, -18.0, 2.2, 6.0), (34.0, -2.0, 2.5, 8.0),
                       (33.0, 12.0, 2.4, 5.0), (24.0, 15.0, 7.0, 2.2), (9.0, 15.5, 5.0, 2.0), (6.6, -12.0, 1.4, 4.0)]:
    bm = bmesh.new()
    bmesh.ops.create_uvsphere(bm, u_segments=20, v_segments=10, radius=1.0)
    for v in bm.verts:
        n = math.sin(v.co.x * 3.1 + x) * math.cos(v.co.y * 2.7 + z) * 0.12
        v.co = Vector((v.co.x * sx * (1 + n), v.co.y * sz * (1 + n), max(v.co.z, -0.1) * random.uniform(0.7, 1.1)))
    me = bpy.data.meshes.new('snowbank')
    bm.to_mesh(me)
    bm.free()
    o = bl.obj('snowbank', me)
    o.location = G(x, 0.0, z)
    me.materials.append(M['snow'])
    bl.smooth(o, 80)

# мелкий товар — одним предметом на вид (меньше вызовов отрисовки)
for pre in ('goods', 'prod'):
    parts = [o for o in bpy.data.objects if o.type == 'MESH' and o.name.startswith(pre)]
    if parts:
        bl.join(pre + '_all', parts)
bl.export(OUT)
