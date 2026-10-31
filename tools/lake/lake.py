"""Озеро (WHAT THE LAKE KEEPS, главы 4–5): берег замёрзшего озера — старая дача (ставни заколочены, внутри печь, стол,
кровать, мамин плащ на вешалке), лодочный сарай из досок со щелью на уровне глаз восьмилетнего (смотрит на мостки),
мостки на сваях с крюком для фонаря, перевёрнутая лодка у сарая, поленница. Лёд, берег и лес ставит игра (scripts/world/lake.gd).
Координаты — как в Godot (x — к озеру, y — вверх, z — вдоль берега); кромка воды — x = -8; G() переводит в оси Blender.
Снег — отдельными предметами `*snow*` (летом, в воспоминании, игра их прячет).
  /home/user/tools/blender45/blender -b --factory-startup --python tools/lake/lake.py -- godot/lake/assets/models/lake.glb
"""
import bpy, math, os, random, sys
from mathutils import Vector
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bl
from bl import M, mat

OUT = sys.argv[sys.argv.index('--') + 1]
bpy.ops.wm.read_factory_settings(use_empty=True)
random.seed(11)

M.update({
    'siding': mat('siding', (0.36, 0.27, 0.19), 0.85, tex='weathered_plank_siding', tint=(0.6, 0.5, 0.4), tile=0.4),
    'plank': mat('plank', (0.34, 0.27, 0.2), 0.9, tex='weathered_plank_siding', tint=(0.5, 0.44, 0.38), tile=0.6),
    'floor': mat('floor', (0.35, 0.24, 0.15), 0.6, tex='old_wood_floor', tint=(0.75, 0.62, 0.48), tile=0.5, arm=True),
    'deck': mat('deck', (0.3, 0.26, 0.22), 0.9, tex='old_wood_floor', tint=(0.55, 0.5, 0.45), tile=0.6),
    'panel': mat('panel', (0.4, 0.26, 0.15), 0.6, tex='wooden_panels', tint=(0.78, 0.6, 0.44), tile=0.5),
    'plaster_in': mat('plaster_in', (0.78, 0.72, 0.6), 0.9, tex='painted_plaster_wall', tint=(0.88, 0.82, 0.7), tile=0.5),
    'roof': mat('roof', (0.25, 0.28, 0.3), 0.6, 0.6, tex='corrugated_iron', tint=(0.38, 0.4, 0.42), tile=0.5),
    'snow': mat('snow', (0.82, 0.84, 0.89), 0.75),
    'frame': mat('frame', (0.78, 0.76, 0.7), 0.7),
    'board': mat('board', (0.33, 0.26, 0.19), 0.9, tex='old_wood_floor', tint=(0.55, 0.47, 0.38), tile=0.8),   # доски на окнах
    'dark': mat('dark', (0.02, 0.02, 0.022), 0.7),
    'glass': mat('glass', (0.1, 0.12, 0.13), 0.05, 0.0, alpha=0.3),
    'log': mat('log', (0.36, 0.24, 0.14), 0.85, tex='old_wood_floor', tint=(0.62, 0.46, 0.32), tile=0.8),
    'cut': mat('cut', (0.62, 0.48, 0.3), 0.8),
    'steel': mat('steel', (0.3, 0.3, 0.31), 0.5, 0.7),
    'rust': mat('rust', (0.32, 0.14, 0.07), 0.8, 0.3),
    'boat': mat('boat', (0.18, 0.3, 0.33), 0.7),
    'brick': mat('brick', (0.55, 0.32, 0.24), 0.9, tex='painted_plaster_wall', tint=(0.7, 0.42, 0.32), tile=0.8),
    'soot': mat('soot', (0.02, 0.018, 0.016), 0.95),
    'cloth': mat('cloth', (0.62, 0.6, 0.52), 0.9),
    'blanket': mat('blanket', (0.3, 0.32, 0.4), 0.85, tex='wool_boucle', tint=(0.5, 0.55, 0.7), tile=2.0),
    'coat': mat('coat', (0.55, 0.42, 0.18), 0.6),            # мамин жёлтый плащ
    'rope': mat('rope', (0.55, 0.47, 0.32), 0.9),
})


def G(x, y, z):
    return Vector((x, -z, y))


def gb(name, size, pos, m, bevel=0.01, roty=0.0, uv=0.0, seg=2):
    return bl.rbox(name, (size[0], size[2], size[1]), G(*pos), m, bevel, seg, rot=(0, 0, roty), uv=uv)


def gcyl(name, r, h, pos, m, seg=16, r2=None, axis='Z'):
    return bl.cyl(name, r, h, G(*pos), m, axis=axis, seg=seg, r2=r2)


# ---------- дача: фасад (крыльцо) — к озеру (+X); пол — y 0.6 ----------
DX, DZ = -20.0, 0.0          # середина дачи
DW, DD, DH, B = 7.0, 6.0, 2.6, 0.6
T = 0.2
hx, hz = DD / 2, DW / 2


def d(x, y, z):
    return (DX + x, y, DZ + z)


def dbox(name, size, pos, m, bevel=0.01, uv=0.0):
    return gb(name, size, d(*pos), m, bevel, uv=uv)


dbox('dacha_base', (DD + 0.2, B, DW + 0.2), (0, B / 2, 0), 'log', 0.03, uv=0.8)
dbox('dacha_wall_back', (T, DH, DW), (-hx + T / 2, B + DH / 2, 0), 'siding', 0.0, uv=1.0)
for sz in (-1, 1):   # боковые стены: по окну
    zc = sz * (hz - T / 2)
    dbox('dacha_wall_side', (DD, 0.9, T), (0, B + 0.45, zc), 'siding', 0.0, uv=1.0)
    dbox('dacha_wall_side', (DD, DH - 2.0, T), (0, B + 2.0 + (DH - 2.0) / 2, zc), 'siding', 0.0, uv=1.0)
    dbox('dacha_wall_side', ((DD - 1.0) / 2, 1.1, T), (-(DD + 1.0) / 4, B + 1.45, zc), 'siding', 0.0, uv=1.0)
    dbox('dacha_wall_side', ((DD - 1.0) / 2, 1.1, T), ((DD + 1.0) / 4, B + 1.45, zc), 'siding', 0.0, uv=1.0)
    dbox('dacha_window', (1.0, 1.1, 0.03), (0, B + 1.45, zc), 'glass', 0.0)
    for k in range(3):   # заколочено досками крест-накрест и поперёк
        b = dbox('dacha_board', (1.3, 0.14, 0.04), (0, B + 1.1 + k * 0.36, sz * (hz + 0.03)), 'board', 0.005)
        b.rotation_euler[1] = math.radians((-1) ** k * 6)
# фасад: два окна (заколочены) и дверь посередине
for zc in (-2.0, 2.0):
    dbox('dacha_wall_front', (T, 0.9, 1.0), (hx - T / 2, B + 0.45, zc), 'siding', 0.0, uv=1.0)
    dbox('dacha_wall_front', (T, DH - 2.0, 1.0), (hx - T / 2, B + 2.0 + (DH - 2.0) / 2, zc), 'siding', 0.0, uv=1.0)
    dbox('dacha_window', (0.03, 1.1, 1.0), (hx - 0.08, B + 1.45, zc), 'glass', 0.0)
    for k in range(3):
        b = dbox('dacha_board', (0.04, 0.14, 1.3), (hx + 0.03, B + 1.1 + k * 0.36, zc), 'board', 0.005)
        b.rotation_euler[0] = math.radians((-1) ** k * 6)
for (a, b) in ((-hz, -2.5), (-1.5, -0.5), (0.5, 1.5), (2.5, hz)):
    dbox('dacha_wall_front', (T, DH, b - a), (hx - T / 2, B + DH / 2, (a + b) / 2), 'siding', 0.0, uv=1.0)
dbox('dacha_wall_front', (T, DH - 2.1, 1.0), (hx - T / 2, B + 2.1 + (DH - 2.1) / 2, 0), 'siding', 0.0, uv=1.0)
dbox('dacha_door', (0.07, 2.05, 0.94), (hx - 0.05, B + 1.03, 0), 'plank', 0.01)
dbox('dacha_handle', (0.05, 0.04, 0.14), (hx + 0.01, B + 1.0, 0.32), 'rust', 0.01)
dbox('dacha_porch', (1.6, 0.1, 3.0), (hx + 0.8, B - 0.05, 0), 'deck', 0.01, uv=0.6)
for k in range(3):   # ступени; под третьей — ключ (глава 5)
    dbox('dacha_step', (0.3, (B - 0.1) * (3 - k) / 3, 1.6), (hx + 1.75 + 0.3 * k, (B - 0.1) * (3 - k) / 6, 0), 'deck', 0.01)
for sz in (-1, 1):
    gcyl('dacha_post', 0.06, 2.4, d(hx + 1.5, B + 1.2, sz * 1.4), 'log', 10)
dbox('dacha_canopy', (1.9, 0.08, 3.4), (hx + 0.85, B + 2.45, 0), 'roof', 0.0)
dbox('dacha_canopy_snow', (1.8, 0.14, 3.3), (hx + 0.85, B + 2.55, 0), 'snow', 0.05)
ang = math.atan2(1.5, DD / 2)
rw = math.hypot(1.5, DD / 2) + 0.45
for sx in (-1, 1):   # крыша: конёк вдоль берега
    r = dbox('dacha_roof', (rw, 0.08, DW + 0.7), (sx * (DD / 4 + 0.1), B + DH + 0.75, 0), 'roof', 0.0, uv=0.5)
    r.rotation_euler[1] = sx * ang
    s = dbox('dacha_roof_snow', (rw - 0.1, 0.2, DW + 0.6), (sx * (DD / 4 + 0.1), B + DH + 0.9, 0), 'snow', 0.08)
    s.rotation_euler[1] = sx * ang
for sz in (-1, 1):
    dbox('dacha_gable', (DD * 0.62, 1.0, T), (0, B + DH + 0.5, sz * (hz - T / 2)), 'siding', 0.0, uv=1.0)
    dbox('dacha_gable', (DD * 0.3, 0.5, T), (0, B + DH + 1.2, sz * (hz - T / 2)), 'siding', 0.0, uv=1.0)
dbox('dacha_chimney', (0.5, 1.5, 0.5), (-1.0, B + DH + 1.3, -1.6), 'brick', 0.02)
for sz in (-1, 1):   # сугробы «по окна»
    dbox('dacha_drift_snow', (DD + 1.0, 0.9, 1.4), (0, 0.35, sz * (hz + 0.5)), 'snow', 0.4)
dbox('dacha_drift_snow', (1.4, 0.8, DW + 1.0), (-hx - 0.5, 0.3, 0), 'snow', 0.4)
for zc in (-2.6, 2.6):
    dbox('dacha_drift_snow', (1.2, 0.7, 1.6), (hx + 0.7, 0.28, zc), 'snow', 0.35)
# внутри: пол, потолок, печь, стол, лавка, кровать, вешалка с маминым плащом, полка
dbox('dacha_floor_in', (DD - 2 * T, 0.04, DW - 2 * T), (0, B + 0.02, 0), 'floor', 0.0, uv=0.5)
dbox('dacha_ceiling_in', (DD - 2 * T, 0.06, DW - 2 * T), (0, B + DH - 0.03, 0), 'panel', 0.0, uv=0.5)
dbox('dacha_stove', (1.2, 1.6, 1.2), (-1.9, B + 0.8, -2.2), 'brick', 0.04, uv=0.6)
dbox('dacha_stove_door', (0.04, 0.3, 0.36), (-1.28, B + 0.55, -2.2), 'soot', 0.01)
dbox('dacha_table', (0.9, 0.05, 1.4), (1.3, B + 0.76, 1.6), 'floor', 0.01)
for xz in ((0.95, 1.05), (1.65, 1.05), (0.95, 2.15), (1.65, 2.15)):
    dbox('dacha_leg', (0.05, 0.74, 0.05), (xz[0], B + 0.37, xz[1]), 'floor', 0.005)
dbox('dacha_bench', (0.35, 0.05, 1.6), (2.4, B + 0.45, 1.6), 'floor', 0.01)
dbox('dacha_bed', (0.95, 0.45, 2.0), (-2.2, B + 0.23, 1.8), 'floor', 0.02)
dbox('dacha_mattress', (0.9, 0.12, 1.95), (-2.2, B + 0.51, 1.8), 'blanket', 0.04)
dbox('dacha_hanger', (0.05, 0.05, 0.6), (2.75, B + 1.85, -2.6), 'log', 0.005)
dbox('dacha_raincoat', (0.12, 1.0, 0.45), (2.7, B + 1.35, -2.6), 'coat', 0.05)
dbox('dacha_shelf', (0.25, 0.04, 1.2), (-2.75, B + 1.6, 0.3), 'floor', 0.005)

# ---------- лодочный сарай у воды: дверь к озеру (+X), щель — на стене к мосткам (−Z) ----------
SX, SZ = -10.0, 13.0
SW, SD, SHT = 4.2, 3.4, 2.4      # вдоль берега, вглубь, высота
gb('boathouse_floor', (SD, 0.08, SW), (SX, 0.24, SZ), 'deck', 0.01, uv=0.6)
for k in range(int(SD / 0.22)):   # задняя стена (−Z, к мосткам): доски вертикально; одна щель — на уровне глаз ребёнка
    x = SX - SD / 2 + 0.11 + k * 0.22
    gap = abs(x - (SX + 0.4)) < 0.12
    if gap:
        gb('boathouse_plank', (0.2, 0.75, 0.04), (x, 0.28 + 0.375, SZ - SW / 2), 'plank', 0.005)
        gb('boathouse_plank', (0.2, SHT - 1.12, 0.04), (x, 1.12 + (SHT - 1.12) / 2, SZ - SW / 2), 'plank', 0.005)
        gb('boathouse_plank_half', (0.11, 0.1, 0.04), (x - 0.045, 1.08, SZ - SW / 2), 'plank', 0.003)
    else:
        gb('boathouse_plank', (0.205, SHT, 0.04), (x, 0.28 + SHT / 2 - 0.14, SZ - SW / 2), 'plank', 0.005)
for k in range(int(SD / 0.22)):   # стена к берегу (+Z)
    x = SX - SD / 2 + 0.11 + k * 0.22
    gb('boathouse_plank', (0.205, SHT, 0.04), (x, 0.28 + SHT / 2 - 0.14, SZ + SW / 2), 'plank', 0.005)
for k in range(int(SW / 0.22)):   # задняя (−X, к лесу)
    z = SZ - SW / 2 + 0.11 + k * 0.22
    gb('boathouse_plank', (0.04, SHT, 0.205), (SX - SD / 2, 0.28 + SHT / 2 - 0.14, z), 'plank', 0.005)
for z0, z1 in ((SZ - SW / 2, SZ - 0.75), (SZ + 0.75, SZ + SW / 2)):   # к озеру: по краям доски, посередине — дверь
    n = int((z1 - z0) / 0.22)
    for k in range(n):
        z = z0 + 0.11 + k * 0.22
        gb('boathouse_plank', (0.04, SHT, 0.205), (SX + SD / 2, 0.28 + SHT / 2 - 0.14, z), 'plank', 0.005)
gb('boathouse_door', (0.05, 2.0, 1.4), (SX + SD / 2 + 0.03, 1.25, SZ), 'plank', 0.01)
gb('boathouse_lintel', (0.06, 0.2, 1.6), (SX + SD / 2, 2.3, SZ), 'log', 0.01)
for sx in (-1, 1):
    r = gb('boathouse_roof', (SD * 0.62, 0.06, SW + 0.5), (SX + sx * SD / 4, SHT + 0.42, SZ), 'roof', 0.0, uv=0.5)
    r.rotation_euler[1] = sx * math.radians(24)
    s = gb('boathouse_roof_snow', (SD * 0.6, 0.15, SW + 0.4), (SX + sx * SD / 4, SHT + 0.52, SZ), 'snow', 0.06)
    s.rotation_euler[1] = sx * math.radians(24)
for sz in (-1, 1):
    gb('boathouse_gable', (SD * 0.7, 0.7, 0.04), (SX, SHT + 0.25, SZ + sz * SW / 2), 'plank', 0.0)
for xz in ((SX - SD / 2, SZ - SW / 2), (SX + SD / 2, SZ - SW / 2), (SX - SD / 2, SZ + SW / 2), (SX + SD / 2, SZ + SW / 2)):
    gcyl('boathouse_post', 0.08, SHT + 0.3, (xz[0], (SHT + 0.3) / 2, xz[1]), 'log', 10)
gb('boathouse_drift_snow', (SD + 0.6, 0.5, 0.8), (SX, 0.2, SZ + SW / 2 + 0.4), 'snow', 0.2)
gb('boathouse_drift_snow', (0.8, 0.45, SW + 0.4), (SX - SD / 2 - 0.4, 0.18, SZ), 'snow', 0.2)
# внутри сарая: вёсла, сети, канистра, верстак
for k in range(2):
    o = bl.beam('boathouse_oar', G(SX - SD / 2 + 0.15, 0.4, SZ + 1.2 + k * 0.25), G(SX - SD / 2 + 0.2, 2.0, SZ + 1.3 + k * 0.25), 0.04, 0.03, M['log'])
gb('boathouse_bench', (0.6, 0.05, 1.6), (SX - SD / 2 + 0.35, 0.95, SZ - 0.6), 'floor', 0.01)
gb('boathouse_net', (0.08, 0.9, 1.2), (SX - SD / 2 + 0.08, 1.4, SZ - 1.0), 'rope', 0.03)

# ---------- мостки: от кромки — в озеро; сваи, настил, крюк для фонаря ----------
MZ = 7.0
ML = 13.0
for k in range(int(ML / 0.2)):
    x = -9.0 + k * 0.2
    gb('dock_board', (0.18, 0.05, 1.3), (x, 0.42 + random.uniform(-0.01, 0.01), MZ), 'deck', 0.004)
for k in range(5):
    x = -8.5 + k * 3.0
    for sz in (-1, 1):
        gcyl('dock_pile', 0.08, 1.5, (x, -0.3, MZ + sz * 0.6), 'log', 10)
gb('dock_beam', (ML, 0.1, 0.1), (-9.0 + ML / 2, 0.35, MZ - 0.6), 'log', 0.01)
gb('dock_beam', (ML, 0.1, 0.1), (-9.0 + ML / 2, 0.35, MZ + 0.6), 'log', 0.01)
gcyl('dock_lamp_post', 0.06, 1.3, (-8.6 + ML, 1.05, MZ + 0.58), 'log', 10)
gb('dock_hook', (0.18, 0.025, 0.025), (-8.52 + ML, 1.62, MZ + 0.58), 'rust', 0.004)
gb('dock_snow', (ML, 0.08, 1.2), (-9.0 + ML / 2, 0.49, MZ), 'snow', 0.03)

# ---------- лодка вверх дном, поленница ----------
hull = gcyl('boat_snow_hull', 0.55, 3.6, (-6.5, 0.3, 17.6), 'boat', 20, axis='X')
hull.scale = (1.0, 0.55, 0.42)
gb('boat_snow', (2.6, 0.08, 0.5), (-6.5, 0.56, 17.6), 'snow', 0.03)
for row in range(3):
    for k in range(10):
        gcyl('logs', 0.11, 0.5, (-23.6, 0.12 + row * 0.21, -2.0 + k * 0.23 + (0.1 if row % 2 else 0.0)), 'log', 8, axis='X')
gb('logs_snow', (0.6, 0.1, 2.5), (-23.6, 0.78, -0.95), 'snow', 0.04)

bl.export(OUT)
