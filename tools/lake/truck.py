"""Фура (WHAT THE LAKE KEEPS, глава 2): седельный тягач «кабина над мотором» и тентованный полуприцеп — кодом в Blender.
Стоят у кафе «У трёх сосен», одна складывается поперёк трассы в буран. Нос тягача — к −Z (как у машины героев),
сцепка — в начале координат, полуприцеп отдельным объектом `trailer` (игра может повернуть его — «сложилась»).
Детали по именам для игры: `cab` (краска, игра перекрашивает), `glass`, `lamp*` (фары), `tail*` (стопы),
`marker*` (оранжевые габариты), `wheel*`.
  /home/user/tools/blender45/blender -b --factory-startup --python tools/lake/truck.py -- godot/lake/assets/models/truck.glb
"""
import bpy, math, os, sys
from mathutils import Vector
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bl
from bl import M, mat

OUT = sys.argv[sys.argv.index('--') + 1]
bpy.ops.wm.read_factory_settings(use_empty=True)

M.update({
    'cab': mat('cab', (0.55, 0.06, 0.04), 0.35, coat=0.6),
    'chassis': mat('chassis', (0.03, 0.03, 0.035), 0.6, 0.4),
    'steel': mat('steel', (0.45, 0.46, 0.47), 0.35, 0.85),
    'tarp': mat('tarp', (0.16, 0.22, 0.3), 0.85),
    'tarp_band': mat('tarp_band', (0.75, 0.75, 0.72), 0.7),
    'tyre': mat('tyre', (0.02, 0.02, 0.02), 0.9),
    'rim': mat('rim', (0.5, 0.5, 0.5), 0.4, 0.7),
    'glass': mat('glass', (0.04, 0.05, 0.06), 0.05, 0.0, alpha=0.55),
    'lamp': mat('lamp', (0.9, 0.9, 0.85), 0.2),
    'tail': mat('tail', (0.3, 0.01, 0.01), 0.3),
    'marker': mat('marker', (0.6, 0.3, 0.02), 0.3),
    'dark': mat('dark', (0.02, 0.02, 0.022), 0.5),
    'mud': mat('mud', (0.22, 0.2, 0.18), 0.95),
})


def G(x, y, z):
    return Vector((x, -z, y))


def gb(name, size, pos, m, bevel=0.01, seg=2):
    return bl.rbox(name, (size[0], size[2], size[1]), G(*pos), m, bevel, seg)


def wheel(name, x, z, r=0.52, w=0.32, twin=False):
    parts = []
    for k in ([0.0, 0.34] if twin else [0.0]):
        xx = x + (k if x > 0 else -k)
        parts.append(bl.cyl(name + '_tyre', r, w, G(xx, r, z), M['tyre'], axis='X', seg=28, bevel=0.04))
        parts.append(bl.cyl(name + '_rim', r * 0.55, w + 0.02, G(xx, r, z), M['rim'], axis='X', seg=20))
    return bl.join(name, parts)


# ---------- тягач (сцепка в z=0, нос к −Z) ----------
CAB_Z0, CAB_Z1 = -4.6, -2.45                     # кабина: передняя стенка … задняя
gb('chassis_frame', (0.9, 0.28, 6.6), (0.0, 0.95, -1.8), 'chassis', 0.02)
cab = [gb('cab_box', (2.45, 2.05, CAB_Z1 - CAB_Z0), (0.0, 1.35 + 1.02, (CAB_Z0 + CAB_Z1) / 2), 'cab', 0.08, 3),
       gb('cab_roof', (2.4, 0.55, CAB_Z1 - CAB_Z0 - 0.2), (0.0, 3.68, (CAB_Z0 + CAB_Z1) / 2 + 0.08), 'cab', 0.12, 3),
       gb('cab_bumper_skirt', (2.5, 0.55, 0.35), (0.0, 1.05, CAB_Z0 + 0.12), 'cab', 0.05)]
bl.join('cab', cab)
gb('grille', (1.7, 0.75, 0.04), (0.0, 1.75, CAB_Z0 - 0.01), 'dark', 0.01)
for i in range(5):
    gb('grille_bar', (1.68, 0.035, 0.05), (0.0, 1.5 + i * 0.13, CAB_Z0 - 0.025), 'steel', 0.005)
gb('bumper', (2.5, 0.32, 0.3), (0.0, 0.62, CAB_Z0 - 0.1), 'chassis', 0.04)
gb('glass', (2.2, 0.95, 0.03), (0.0, 2.75, CAB_Z0 - 0.005), 'glass', 0.0)
for s in (-1, 1):
    gb('glass', (0.03, 0.7, 0.9), (s * 1.225, 2.75, CAB_Z0 + 0.6), 'glass', 0.0)
    gb('lamp_%s' % ('l' if s < 0 else 'r'), (0.42, 0.2, 0.05), (s * 0.95, 0.95, CAB_Z0 - 0.26), 'lamp', 0.02)
    gb('marker_cab', (0.25, 0.06, 0.06), (s * 0.95, 3.98, CAB_Z0 + 0.2), 'marker', 0.01)
    gb('mirror_arm', (0.45, 0.04, 0.04), (s * 1.42, 3.05, CAB_Z0 + 0.25), 'chassis', 0.005)
    gb('mirror', (0.06, 0.55, 0.25), (s * 1.62, 2.85, CAB_Z0 + 0.25), 'chassis', 0.02)
    gb('step', (0.3, 0.05, 0.55), (s * 1.1, 0.75, CAB_Z0 + 0.45), 'steel', 0.01)
    gb('fender', (0.4, 0.12, 1.25), (s * 1.08, 1.12, -3.7), 'chassis', 0.03)
    gb('tank', (0.55, 0.62, 1.4), (s * 0.95, 0.9, -1.55), 'steel', 0.05)
gb('fifth_wheel', (1.0, 0.1, 1.0), (0.0, 1.15, -0.1), 'chassis', 0.02)
for s in (-1, 1):
    wheel('wheel_f', s * 1.05, -3.7)
    wheel('wheel_r1', s * 0.92, -0.65, twin=True)
    wheel('wheel_r2', s * 0.92, 0.6, twin=True)

# ---------- полуприцеп (тент) ----------
tr = []
lights = []                                      # стопы и габариты — отдельными предметами (игра их зажигает)
L, W, H0, H1 = 13.6, 2.5, 1.3, 4.0               # длина, ширина, пол и крыша
TZ0 = -1.0                                       # передняя стенка (над сцепкой)
tr.append(gb('trailer_floor', (W, 0.25, L), (0.0, H0, TZ0 + L / 2), 'chassis', 0.02))
tr.append(gb('trailer_tarp', (W, H1 - H0 - 0.25, L), (0.0, (H0 + H1) / 2 + 0.12, TZ0 + L / 2), 'tarp', 0.05, 3))
for z in (TZ0 + 0.05, TZ0 + L - 0.05):
    tr.append(gb('trailer_frame', (W + 0.04, H1 - H0, 0.1), (0.0, (H0 + H1) / 2, z), 'steel', 0.01))
for s in (-1, 1):
    tr.append(gb('trailer_band', (0.02, 0.12, L - 0.2), (s * (W / 2 + 0.01), H1 - 0.25, TZ0 + L / 2), 'tarp_band', 0.0))
    tr.append(gb('trailer_skirt', (0.04, 0.6, 7.0), (s * (W / 2 - 0.1), H0 - 0.4, TZ0 + 4.5), 'chassis', 0.01))
    for k in range(3):
        tr.append(wheel('trailer_wheel', s * 0.92, TZ0 + L - 3.2 + k * 1.3, twin=True))
    lights.append(gb('tail_%s' % ('l' if s < 0 else 'r'), (0.32, 0.14, 0.05), (s * 0.95, H0 - 0.25, TZ0 + L + 0.02), 'tail', 0.01))
    for k in range(5):
        lights.append(gb('marker_side', (0.05, 0.08, 0.14), (s * (W / 2 + 0.03), H0 - 0.05, TZ0 + 1.0 + k * 2.8), 'marker', 0.005))
tr.append(gb('trailer_bumper', (2.3, 0.15, 0.15), (0.0, H0 - 0.55, TZ0 + L + 0.1), 'chassis', 0.01))
tr.append(gb('trailer_legs', (1.8, 0.9, 0.12), (0.0, H0 - 0.6, TZ0 + 1.9), 'chassis', 0.01))
tr.append(gb('trailer_mud', (W, 0.5, L * 0.6), (0.0, H0 + 0.25, TZ0 + L * 0.65), 'mud', 0.0))
trailer = bl.join('trailer', [o for o in tr if o])
bl.origin_to(trailer, G(0.0, 0.0, 0.0))            # поворачивается вокруг сцепки
for o in lights:
    o.parent = trailer
bl.export(OUT)
