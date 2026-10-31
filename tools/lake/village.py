"""Деревня у леса (WHAT THE LAKE KEEPS, глава 3) — постройка кодом в Blender: улица вдоль Z, дома из досок с двускатными
крышами под снегом, заборы, колодец, магазин (вывеску пишет игра; окно — отдельным предметом shop_window, его можно разбить),
таксофон, сарай с поленницей и колодой, последний дом у леса. Дом бабушки Агнес — с синими ставнями и крыльцом, внутри
одна комната: русская печь с устьем (огонь ставит игра), кровать за печью, стол и лавки у окна, сундук, полка с приёмником,
фотографии на стене, пучки трав под балкой, лаз в подпол. Координаты — как в Godot (x — поперёк улицы, y — вверх, z — вдоль),
G() переводит в оси Blender. Узел начала — середина улицы у въезда.
  /home/user/tools/blender45/blender -b --factory-startup --python tools/lake/village.py -- godot/lake/assets/models/village.glb
"""
import bpy, math, os, random, sys
from mathutils import Vector
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bl
from bl import M, mat

OUT = sys.argv[sys.argv.index('--') + 1]
bpy.ops.wm.read_factory_settings(use_empty=True)
random.seed(5)

M.update({
    'siding': mat('siding', (0.4, 0.28, 0.17), 0.85, tex='weathered_plank_siding', tint=(0.72, 0.56, 0.4), tile=0.4),
    'siding_gray': mat('siding_gray', (0.35, 0.32, 0.3), 0.85, tex='weathered_plank_siding', tint=(0.55, 0.52, 0.5), tile=0.4),
    'panel': mat('panel', (0.4, 0.26, 0.15), 0.6, tex='wooden_panels', tint=(0.8, 0.6, 0.42), tile=0.5),
    'floor': mat('floor', (0.35, 0.24, 0.15), 0.6, tex='old_wood_floor', tint=(0.8, 0.66, 0.5), tile=0.5, arm=True),
    'plaster': mat('plaster', (0.86, 0.84, 0.78), 0.9, tex='painted_plaster_wall', tint=(1.0, 0.97, 0.9), tile=0.6),
    'plaster_in': mat('plaster_in', (0.78, 0.72, 0.6), 0.9, tex='painted_plaster_wall', tint=(0.9, 0.84, 0.7), tile=0.5),
    'table': mat('table', (0.4, 0.28, 0.18), 0.45, tex='wood_table_worn', tint=(0.85, 0.7, 0.55), tile=0.8),
    'roof': mat('roof', (0.25, 0.28, 0.3), 0.6, 0.6, tex='corrugated_iron', tint=(0.42, 0.45, 0.47), tile=0.5),
    'snow': mat('snow', (0.82, 0.84, 0.89), 0.75),
    'frame': mat('frame', (0.86, 0.85, 0.8), 0.6),                    # белые наличники
    'blue': mat('blue', (0.12, 0.3, 0.62), 0.55),                     # синие ставни
    'green': mat('green', (0.16, 0.3, 0.22), 0.6),
    'brown': mat('brown', (0.25, 0.15, 0.08), 0.65),
    'dark': mat('dark', (0.03, 0.03, 0.032), 0.6),
    'soot': mat('soot', (0.02, 0.018, 0.016), 0.95),
    'glass': mat('glass', (0.1, 0.12, 0.13), 0.05, 0.0, alpha=0.25),
    'glass_lit': mat('glass_lit', (0.9, 0.7, 0.4), 0.3, emit=((1.0, 0.72, 0.4), 1.6)),
    'log': mat('log', (0.36, 0.24, 0.14), 0.85, tex='old_wood_floor', tint=(0.62, 0.46, 0.32), tile=0.8),
    'cut': mat('cut', (0.62, 0.48, 0.3), 0.8),
    'steel': mat('steel', (0.35, 0.36, 0.37), 0.4, 0.8),
    'phone': mat('phone', (0.55, 0.08, 0.06), 0.4, coat=0.3),
    'sheet': mat('sheet', (0.86, 0.85, 0.8), 0.9),
    'blanket': mat('blanket', (0.42, 0.12, 0.1), 0.85, tex='wool_boucle', tint=(0.8, 0.3, 0.25), tile=2.0),
    'pic': mat('pic', (0.6, 0.55, 0.45), 0.7),
    'herb': mat('herb', (0.32, 0.36, 0.18), 0.9),
    'radio': mat('radio', (0.28, 0.17, 0.09), 0.5, coat=0.2),
    'cloth': mat('cloth', (0.75, 0.72, 0.62), 0.9),
})


def G(x, y, z):
    return Vector((x, -z, y))


def gb(name, size, pos, m, bevel=0.01, roty=0.0, uv=0.0, seg=2):
    return bl.rbox(name, (size[0], size[2], size[1]), G(*pos), m, bevel, seg, rot=(0, 0, roty), uv=uv)


def gcyl(name, r, h, pos, m, seg=16, r2=None, axis='Z'):
    return bl.cyl(name, r, h, G(*pos), m, axis=axis, seg=seg, r2=r2)


class H:
    """Дом: местные координаты (фасад к +X), поворот 0 — дом слева от улицы (фасад к улице), 180 — справа."""
    def __init__(self, cx, cz, rot):
        self.cx, self.cz, self.rot = cx, cz, rot

    def p(self, x, y, z):
        if self.rot == 180:
            x, z = -x, -z
        return (self.cx + x, y, self.cz + z)

    def s(self, sx, sy, sz):
        return (sx, sy, sz)

    def box(self, name, size, pos, m, bevel=0.01, uv=0.0):
        return gb(name, size, self.p(*pos), m, bevel, uv=uv)


def house(name, cx, cz, rot, w=7.0, d=6.0, h=2.7, base=0.5, wall='siding', shutters='blue', lit=False, door=True, inside=False):
    """w — вдоль улицы (z), d — вглубь (x); фасад (x = +d/2) — к улице."""
    o = H(cx, cz, rot)
    t = 0.2
    hx, hz = d / 2, w / 2
    o.box(name + '_base', (d + 0.2, base, w + 0.2), (0, base / 2, 0), 'log', 0.03, uv=0.8)
    # стены (с окнами на фасаде — проёмы делим на простенки)
    o.box(name + '_wall_back', (t, h, w), (-hx + t / 2, base + h / 2, 0), wall, 0.0, uv=1.0)
    for sz in (-1, 1):
        o.box(name + '_wall_side', (d, h, t), (0, base + h / 2, sz * (hz - t / 2)), wall, 0.0, uv=1.0)
    # фасад: окна 1.0×1.1 на высоте 0.9; дверь (если есть) — посередине
    wins = [-2.2, 2.2] if door else [-2.0, 0.0, 2.0]
    wy0, wh, ww = base + 0.9, 1.1, 1.0
    cuts = sorted([(z - ww / 2, z + ww / 2, 'w') for z in wins] + ([(-0.5, 0.5, 'd')] if door else []))
    zs = [-hz] + [c for a, b, _ in cuts for c in (a, b)] + [hz]
    for i in range(0, len(zs), 2):
        a, b = zs[i], zs[i + 1]
        if b - a > 0.01:
            o.box(name + '_wall_front', (t, h, b - a), (hx - t / 2, base + h / 2, (a + b) / 2), wall, 0.0, uv=1.0)
    for a, b, kind in cuts:
        zc = (a + b) / 2
        if kind == 'w':
            o.box(name + '_wall_front', (t, wy0 - base, ww), (hx - t / 2, base + (wy0 - base) / 2, zc), wall, 0.0, uv=1.0)
            o.box(name + '_wall_front', (t, base + h - wy0 - wh, ww), (hx - t / 2, wy0 + wh + (base + h - wy0 - wh) / 2, zc), wall, 0.0, uv=1.0)
            o.box(name + '_window' if not lit else name + '_window_lit', (0.03, wh, ww), (hx - 0.08, wy0 + wh / 2, zc), 'glass_lit' if lit else 'glass', 0.0)
            o.box(name + '_frame', (0.06, 0.08, ww + 0.2), (hx + 0.02, wy0 + wh + 0.06, zc), 'frame', 0.01)
            o.box(name + '_frame', (0.06, 0.06, ww + 0.16), (hx + 0.02, wy0 - 0.04, zc), 'frame', 0.01)
            o.box(name + '_frame', (0.05, wh, 0.05), (hx - 0.06, wy0 + wh / 2, zc), 'frame', 0.005)
            o.box(name + '_frame', (0.05, 0.05, ww), (hx - 0.06, wy0 + wh * 0.62, zc), 'frame', 0.005)
            for sd in (-1, 1):   # ставни
                o.box(name + '_shutter', (0.05, wh + 0.04, ww / 2), (hx + 0.06, wy0 + wh / 2, zc + sd * (ww * 0.75)), shutters, 0.01)
        else:
            o.box(name + '_wall_front', (t, base + h - 2.6, b - a), (hx - t / 2, 2.6 + (base + h - 2.6) / 2, zc), wall, 0.0, uv=1.0)
            o.box(name + '_door', (0.07, 2.05, 0.96), (hx - 0.05, base + 1.03, zc), 'brown', 0.01)
            o.box(name + '_frame', (0.08, 0.1, 1.2), (hx + 0.02, base + 2.12, zc), 'frame', 0.01)
            for k in range(2):   # ступени крыльца
                o.box(name + '_step', (0.32, base / 2 * (2 - k), 1.6), (hx + 0.18 + 0.32 * k, base / 4 * (2 - k), zc), 'floor', 0.01)
            o.box(name + '_porch', (1.0, 0.08, 2.0), (hx + 0.5, base - 0.04, zc), 'floor', 0.01)
            o.box(name + '_canopy', (1.2, 0.08, 2.2), (hx + 0.55, base + 2.45, zc), 'roof', 0.0)
            o.box(name + '_canopy_snow', (1.1, 0.1, 2.1), (hx + 0.55, base + 2.53, zc), 'snow', 0.04)
    # крыша: конёк вдоль улицы (по z), скаты к фасаду и к задней стене
    ang = math.atan2(1.5, d / 2)
    rw = math.hypot(1.5, d / 2) + 0.45
    for sx in (-1, 1):
        cxr = sx * (d / 4 + 0.1)
        r = gb(name + '_roof', (rw, 0.08, w + 0.7), o.p(cxr, base + h + 0.75, 0), 'roof', 0.0, uv=0.5)
        r.rotation_euler[1] = (sx if o.rot == 0 else -sx) * ang
        sn = gb(name + '_roof_snow', (rw - 0.1, 0.18, w + 0.6), o.p(cxr, base + h + 0.88, 0), 'snow', 0.07)
        sn.rotation_euler[1] = (sx if o.rot == 0 else -sx) * ang
    for sz in (-1, 1):   # фронтоны
        o.box(name + '_gable', (d * 0.62, 1.0, t), (0, base + h + 0.5, sz * (hz - t / 2)), wall, 0.0, uv=1.0)
        o.box(name + '_gable', (d * 0.3, 0.5, t), (0, base + h + 1.2, sz * (hz - t / 2)), wall, 0.0, uv=1.0)
    gb(name + '_chimney', (0.5, 1.4, 0.5), o.p(-0.8, base + h + 1.3, 1.6), 'plaster', 0.02)
    # сугробы у стен
    for sz in (-1, 1):
        gb('drift', (d + 0.6, 0.4, 0.9), o.p(0, 0.15, sz * (hz + 0.35)), 'snow', 0.18)
    if inside:
        o.box(name + '_floor_in', (d - 2 * t, 0.04, w - 2 * t), (0, base + 0.02, 0), 'floor', 0.0, uv=0.5)
        o.box(name + '_ceiling_in', (d - 2 * t, 0.06, w - 2 * t), (0, base + h - 0.03, 0), 'panel', 0.0, uv=0.5)
        o.box(name + '_beam', (d - 2 * t, 0.16, 0.18), (0, base + h - 0.14, 0.4), 'log', 0.02)
    return o


# ---------- улица ----------
for z0, z1 in ((20, -100),):
    pass
for sx in (-1, 1):   # заборы вдоль улицы (с разрывами у калиток)
    for zc in range(15, -96, -1):
        if (sx < 0 and -48 < zc < -42) or (sx > 0 and -31 < zc < -25) or abs(zc) % 20 == 3:
            continue
        gb('picket', (0.04, 1.1 + random.uniform(-0.05, 0.05), 0.09), (sx * 6.4, 0.55, zc + 0.5), 'siding_gray', 0.005)
    gb('rail', (0.04, 0.07, 110), (sx * 6.42, 0.85, -40), 'siding_gray', 0.0)
    gb('rail', (0.04, 0.07, 110), (sx * 6.42, 0.35, -40), 'siding_gray', 0.0)
    gb('bank', (1.2, 0.5, 112), (sx * 5.2, 0.12, -40), 'snow', 0.2)
for zp in (-15, -55, -92):   # столбы с фонарями
    gcyl('pole', 0.11, 6.5, (4.6, 3.25, zp), 'log', 10)
    gb('lamp_arm', (1.1, 0.08, 0.08), (4.1, 6.2, zp), 'steel', 0.01)
    gb('lamp_head', (0.4, 0.12, 0.25), (3.55, 6.1, zp), 'steel', 0.03)

# ---------- дома ----------
house('h1', -11.0, -6.0, 0, shutters='green', lit=True, door=False)
house('h2', 11.0, -9.0, 180, wall='siding_gray', shutters='green', door=False)
AG = house('agnes', -11.0, -45.0, 0, w=7.6, d=6.6, shutters='blue', door=True, inside=True)
house('h4', 11.0, -50.0, 180, shutters='brown', door=False)
house('h5', -11.0, -68.0, 0, wall='siding_gray', shutters='green', door=False)
house('h6', 10.0, -88.0, 180, w=6.0, d=5.5, shutters='brown', door=False)   # последний дом, за ним лес и озеро

# ---------- магазин ----------
S = H(11.0, -28.0, 180)
S.box('shop_base', (5.4, 0.35, 6.4), (0, 0.17, 0), 'concrete' if 'concrete' in M else 'log', 0.02, uv=0.6)
for sz in (-1, 1):
    S.box('shop_wall', (5.2, 3.0, 0.2), (0, 1.85, sz * 3.0), 'plaster', 0.0, uv=0.7)
S.box('shop_wall', (0.2, 3.0, 6.2), (-2.5, 1.85, 0), 'plaster', 0.0, uv=0.7)
S.box('shop_wall', (0.2, 0.7, 6.2), (2.5, 3.0, 0), 'plaster', 0.0, uv=0.7)
S.box('shop_wall', (0.2, 0.9, 6.2), (2.5, 0.8, 0), 'plaster', 0.0, uv=0.7)
S.box('shop_wall', (0.2, 1.4, 1.6), (2.5, 2.0, 2.2), 'plaster', 0.0, uv=0.7)
S.box('shop_window', (0.03, 1.35, 2.6), (2.52, 1.95, -0.9), 'glass', 0.0)
S.box('shop_door', (0.07, 2.1, 1.0), (2.52, 1.4, 2.2), 'brown', 0.01)
S.box('shop_roof', (6.0, 0.15, 7.0), (0, 3.45, 0), 'roof', 0.0, uv=0.5)
S.box('shop_roof_snow', (5.8, 0.2, 6.8), (0, 3.6, 0), 'snow', 0.08)
S.box('shop_sign', (0.08, 0.6, 3.6), (2.65, 3.0, -0.6), 'green', 0.02)
S.box('payphone', (0.32, 0.6, 0.45), (2.72, 1.55, 3.45 if False else 3.35), 'phone', 0.03)
S.box('payphone_hood', (0.45, 0.08, 0.6), (2.75, 1.95, 3.35), 'steel', 0.02)
S.box('shop_steps', (0.5, 0.18, 1.4), (2.85, 0.09, 2.2), 'floor', 0.01)

# ---------- колодец, сарай, поленница ----------
gcyl('well', 0.6, 0.9, (-5.5, 0.45, -40.0), 'log', 14)
gcyl('well_hole', 0.45, 0.02, (-5.5, 0.91, -40.0), 'soot', 14)
for sz in (-1, 1):
    gb('well_post', (0.1, 1.9, 0.1), (-5.5, 0.95, -40.0 + sz * 0.62), 'log', 0.01)
gb('well_roof', (1.3, 0.06, 1.6), (-5.5, 2.0, -40.0), 'roof', 0.0)
gb('well_roof_snow', (1.2, 0.1, 1.5), (-5.5, 2.07, -40.0), 'snow', 0.04)
gcyl('well_drum', 0.1, 1.3, (-5.5, 1.55, -40.0), 'log', 10, axis='Y')
gb('shed', (3.0, 2.3, 3.4), (-18.5, 1.15, -52.0), 'siding_gray', 0.0, uv=1.0)
gb('shed_door', (0.06, 1.9, 0.9), (-16.98, 0.95, -52.0), 'brown', 0.01)
gb('shed_roof', (3.6, 0.1, 4.0), (-18.5, 2.4, -52.0), 'roof', 0.0)
gb('shed_roof_snow', (3.4, 0.15, 3.8), (-18.5, 2.52, -52.0), 'snow', 0.06)
for row in range(4):   # поленница вдоль стены сарая
    for k in range(9):
        gcyl('logs', 0.11, 0.5, (-16.7 - 0.0, 0.12 + row * 0.21, -54.2 + k * 0.23 + (0.1 if row % 2 else 0.0)), 'log', 8, axis='X')
gcyl('block', 0.3, 0.55, (-15.6, 0.27, -55.2), 'cut', 14)

# ---------- дом Агнес изнутри (местные координаты дома: фасад к +X; пол — y 0.5) ----------
B = 0.5
AG.box('stove', (1.7, 1.9, 1.9), (-1.95, B + 0.95, -2.35), 'plaster', 0.06, uv=0.6)
AG.box('stove_top', (1.8, 0.12, 2.0), (-1.95, B + 1.95, -2.35), 'plaster', 0.03)
AG.box('stove_pipe', (0.55, 0.9, 0.55), (-2.3, B + 2.45, -2.6), 'plaster', 0.02)
AG.box('stove_mouth', (0.08, 0.55, 0.6), (-1.08, B + 0.95, -2.35), 'soot', 0.04)          # устье — к комнате
AG.box('stove_shelf', (0.25, 0.08, 1.9), (-1.0, B + 0.6, -2.35), 'plaster', 0.02)
AG.box('bed', (1.9, 0.45, 0.95), (-2.2, B + 0.23, 1.3), 'brown', 0.03)                     # кровать у задней стены
AG.box('bed_sheet', (1.85, 0.12, 0.92), (-2.2, B + 0.5, 1.3), 'sheet', 0.04)
AG.box('bed_blanket', (1.2, 0.08, 0.96), (-1.95, B + 0.58, 1.3), 'blanket', 0.03)
AG.box('bed_pillow', (0.4, 0.14, 0.7), (-2.95, B + 0.62, 1.3), 'sheet', 0.06)
AG.box('table', (1.0, 0.06, 1.5), (2.2, B + 0.76, 1.6), 'table', 0.01)
for sx, sz in ((1.8, 1.0), (2.6, 1.0), (1.8, 2.2), (2.6, 2.2)):
    AG.box('table_leg', (0.06, 0.74, 0.06), (sx, B + 0.37, sz), 'table', 0.005)
AG.box('table_cloth', (0.9, 0.012, 1.3), (2.2, B + 0.795, 1.6), 'cloth', 0.0)
AG.box('bench', (0.35, 0.06, 1.8), (2.95, B + 0.45, 1.6), 'table', 0.01)                  # лавка у окна
AG.box('bench2', (1.6, 0.06, 0.35), (2.0, B + 0.45, 2.9), 'table', 0.01)
for sx, sz in ((2.95, 0.8), (2.95, 2.4)):
    AG.box('bench_leg', (0.3, 0.44, 0.05), (sx, B + 0.22, sz), 'table', 0.005)
AG.box('chest', (0.9, 0.55, 0.5), (0.6, B + 0.28, 3.4), 'brown', 0.03)
AG.box('chest_band', (0.92, 0.04, 0.52), (0.6, B + 0.42, 3.4), 'steel', 0.005)
AG.box('shelf', (0.25, 0.04, 1.2), (-3.0, B + 1.6, 0.0), 'table', 0.005)
AG.box('radio', (0.22, 0.28, 0.42), (-2.98, B + 1.76, 0.1), 'radio', 0.03)
AG.box('radio_dial', (0.01, 0.1, 0.22), (-2.86, B + 1.8, 0.1), 'pic', 0.0)
for k, (z, y) in enumerate(((-0.9, 1.75), (-0.45, 1.85), (0.0, 1.7))):   # фотографии Анны — над кроватью, на задней стене
    AG.box('photo_frame', (0.03, 0.34, 0.26), (-3.08, B + y, 1.0 + z), 'brown', 0.005)
    AG.box('photo', (0.01, 0.27, 0.19), (-3.06, B + y, 1.0 + z), 'pic', 0.0)
for k in range(6):   # пучки трав под балкой
    z = -1.5 + k * 0.6
    AG.box('herb', (0.12, 0.45, 0.12), (-0.4 + (k % 2) * 0.5, B + 2.2, z + 0.4), 'herb', 0.05)
AG.box('hatch', (0.8, 0.02, 0.8), (-0.2, B + 0.045, -1.0), 'floor', 0.0)                 # лаз в подпол
AG.box('hatch_ring', (0.12, 0.02, 0.03), (-0.2, B + 0.06, -0.7), 'steel', 0.0)
AG.box('lamp_bulb', (0.08, 0.1, 0.08), (0.2, B + 2.25, 0.4), 'glass_lit', 0.02)

bl.export(OUT)
print('DONE', OUT)
