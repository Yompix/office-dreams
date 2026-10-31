"""Буханка деда (WHAT THE LAKE KEEPS, глава 4): старый полноприводный фургон «кабина над мотором» с круглыми фарами —
кузов-«батон» со скруглёнными рёбрами, окнами по бокам и двустворчатой задней дверью; салон: два передних сиденья,
кожух мотора между ними, торпедо, почти лежачий «автобусный» руль, лавки вдоль бортов сзади, плафон.
Кузов — брусок со скруглением рёбер → проёмы окон и арки колёс вычитанием → толщина внутрь (изнутри — светлая краска).
Координаты в скрипте — как в Godot (x — вправо, y — вверх, нос — к −Z); G() переводит в оси Blender.
Детали по именам для игры: `body` (краска), `glass`, `lamp_l/r` (фары), `tail_l/r` (стопы), `wheel_*` (колёса — центр
в точке отсчёта, крутятся), `steer` (руль — точка отсчёта в центре), `mirror_l/r` (корпуса боковых зеркал).
  /home/user/tools/blender45/blender -b --factory-startup --python tools/lake/van.py -- godot/lake/assets/models/van.glb
"""
import bpy, bmesh, math, os, sys
from mathutils import Vector, Matrix
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bl
from bl import M, mat

OUT = sys.argv[sys.argv.index('--') + 1]
bpy.ops.wm.read_factory_settings(use_empty=True)

M.update({
    'paint': mat('paint', (0.25, 0.3, 0.2), 0.6),             # выцветший защитный
    'inner': mat('inner', (0.6, 0.6, 0.53), 0.8),              # изнутри борта крашены светлее
    'seat': mat('seat', (0.11, 0.09, 0.08), 0.45),            # дерматин
    'cover': mat('cover', (0.2, 0.17, 0.14), 0.55),           # кожух мотора — обит дерматином
    'rubber': mat('rubber', (0.035, 0.035, 0.04), 0.92),
    'dash': mat('dash', (0.07, 0.07, 0.075), 0.6),
    'gauge': mat('gauge', (0.85, 0.82, 0.7), 0.4),
    'chrome': mat('chrome', (0.72, 0.72, 0.74), 0.25, 0.9),
    'steel': mat('steel', (0.05, 0.05, 0.055), 0.55, 0.4),
    'glass': mat('glass', (0.05, 0.06, 0.07), 0.05, 0.0, alpha=0.28),
    'lamp': mat('lamp', (0.9, 0.9, 0.85), 0.2),
    'tail': mat('tail', (0.32, 0.01, 0.01), 0.3),
    'amber': mat('amber', (0.75, 0.38, 0.03), 0.3),
    'tyre': mat('tyre', (0.025, 0.025, 0.025), 0.9),
    'rim': mat('rim', (0.22, 0.27, 0.18), 0.5, 0.3),
    'seam': mat('seam', (0.02, 0.022, 0.02), 0.7),
    'mirror': mat('mirror', (0.03, 0.03, 0.035), 0.4),
    'bulb': mat('bulb', (0.95, 0.9, 0.75), 0.3, emit=((1.0, 0.92, 0.75), 0.6)),
})

L, W = 4.36, 1.94          # длина, ширина
Y0, Y1 = 0.46, 2.06        # низ кузова, крыша
ZF, ZR = -L / 2, L / 2     # нос, корма


def G(x, y, z):
    return Vector((x, -z, y))


def gb(name, size, pos, m, bevel=0.01, seg=2):
    return bl.rbox(name, (size[0], size[2], size[1]), G(*pos), m, bevel, seg)


def cutter(name, c, size, r=0.04):
    """брусок-«вырез» с центром c и размером size (оси Godot)"""
    return bl.rbox(name, (size[0], size[2], size[1]), G(*c), M['seam'], r, 3)


# ---------- кузов-«батон» ----------
bm = bmesh.new()
bmesh.ops.create_cube(bm, size=1.0)
for v in bm.verts:
    v.co = G(v.co.x * W, Y0 + (v.co.z + 0.5) * (Y1 - Y0), v.co.y * L)
bmesh.ops.recalc_face_normals(bm, faces=bm.faces)   # перевод осей отражает куб — нормали снова наружу (иначе вычитание съест всё)
bmesh.ops.bevel(bm, geom=list(bm.edges), offset=0.24, segments=6, profile=0.5, affect='EDGES', clamp_overlap=True)
for v in bm.verts:   # лобовое — с завалом назад, низ носа — чуть вперёд
    g = Vector((v.co.x, v.co.z, -v.co.y))     # назад в оси Godot
    if g.z < ZF + 0.5 and g.y > 1.32:
        g.z += (g.y - 1.32) * 0.22
    v.co = G(g.x, g.y, g.z)
me = bpy.data.meshes.new('body')
bm.to_mesh(me)
bm.free()
body = bl.obj('body', me)
me.materials.append(M['paint'])
me.materials.append(M['inner'])

cuts = []
WY0, WY1 = 1.36, 1.86
WYC, WYH = (WY0 + WY1) / 2, WY1 - WY0
cuts.append(cutter('c_ws_l', (-0.45, 1.61, ZF), (0.78, 0.54, 0.8)))          # лобовое — два стекла
cuts.append(cutter('c_ws_r', (0.45, 1.61, ZF), (0.78, 0.54, 0.8)))
SIDE = ((-0.78, -0.14), (0.02, 0.66), (0.82, 1.46))
for sx in (-1, 1):
    cuts.append(cutter('c_door', (sx * 1.0, WYC, -1.4), (0.6, WYH, 0.64)))     # окна передних дверей
    for (z0, z1) in SIDE:
        cuts.append(cutter('c_side', (sx * 1.0, WYC + 0.01, (z0 + z1) / 2), (0.6, WYH - 0.06, z1 - z0)))
    cuts.append(cutter('c_rear', (sx * 0.42, 1.65, ZR), (0.54, 0.42, 0.8)))   # задние двери — по окну
for zc in (-1.15, 1.15):   # арки колёс
    a = bl.cyl('c_arch', 0.47, 2.6, G(0.0, 0.36, zc), M['seam'], axis='X', seg=40)
    cuts.append(a)
cut = bl.join('cutters', cuts)
sol = body.modifiers.new('thick', 'SOLIDIFY')   # сначала — тонкая стенка (изнутри своя краска), потом — проёмы насквозь
sol.thickness = 0.035
sol.offset = -1.0
sol.use_rim = True
sol.material_offset = 1
bl.apply_mods(body)
md = body.modifiers.new('holes', 'BOOLEAN')
md.operation = 'DIFFERENCE'
md.solver = 'EXACT'
md.material_mode = 'TRANSFER'   # торцы проёмов — тёмные, как резиновый уплотнитель
md.object = cut
bl.apply_mods(body)
bpy.data.objects.remove(cut, do_unlink=True)
bl.smooth(body, 35)

# ---------- стёкла ----------
gl = []
gl.append(gb('g_ws_l', (0.8, 0.56, 0.012), (-0.45, 1.61, ZF + 0.035 + (1.61 - 1.32) * 0.22), 'glass', 0.0))
gl.append(gb('g_ws_r', (0.8, 0.56, 0.012), (0.45, 1.61, ZF + 0.035 + (1.61 - 1.32) * 0.22), 'glass', 0.0))
for o in gl:
    o.rotation_euler = (math.radians(12.4), 0, 0)
for sx in (-1, 1):
    gl.append(gb('g_door', (0.012, 0.52, 0.66), (sx * 0.955, (WY0 + WY1) / 2, -1.4), 'glass', 0.0))
    for (z0, z1) in ((-0.78, -0.14), (0.02, 0.66), (0.82, 1.46)):
        gl.append(gb('g_side', (0.012, 0.44, z1 - z0), (sx * 0.955, (WY0 + WY1) / 2 + 0.03, (z0 + z1) / 2), 'glass', 0.0))
    gl.append(gb('g_rear', (0.56, 0.44, 0.012), (sx * 0.42, 1.65, ZR - 0.02), 'glass', 0.0))
bl.join('glass', gl)

# ---------- швы дверей, ручки, водостоки ----------
seams = []
for sx in (-1, 1):
    x = sx * (W / 2 + 0.002)
    for (z, y0, y1) in ((-1.86, 0.62, 1.9), (-0.98, 0.62, 1.9)):        # передние двери
        seams.append(gb('seam', (0.006, y1 - y0, 0.014), (x, (y0 + y1) / 2, z), 'seam', 0.0))
    seams.append(gb('seam', (0.006, 0.014, 0.88), (x, 0.62, -1.42), 'seam', 0.0))
    seams.append(gb('handle', (0.03, 0.035, 0.16), (sx * (W / 2 + 0.012), 1.2, -1.2), 'chrome', 0.008))
    seams.append(gb('gutter', (0.03, 0.03, 3.6), (sx * (W / 2 - 0.05), 1.98, 0.15), 'paint', 0.01))
for (z, y0, y1) in ((-0.9, 0.62, 1.9), (0.3, 0.62, 1.9)):                 # боковая дверь справа
    seams.append(gb('seam', (0.006, y1 - y0, 0.014), (W / 2 + 0.002, (y0 + y1) / 2, z), 'seam', 0.0))
seams.append(gb('handle', (0.03, 0.035, 0.16), (W / 2 + 0.012, 1.2, 0.15), 'chrome', 0.008))
seams.append(gb('seam', (0.014, 1.3, 0.006), (0.0, 1.25, ZR + 0.002), 'seam', 0.0))   # стык задних створок
seams.append(gb('handle', (0.16, 0.035, 0.03), (0.2, 1.2, ZR + 0.012), 'chrome', 0.008))
bl.join('trim', seams)

# ---------- нос: фары, решётка, поворотники, бампер ----------
for sx, nm in ((-1, 'lamp_l'), (1, 'lamp_r')):
    bl.cyl(nm, 0.085, 0.05, G(sx * 0.66, 0.88, ZF - 0.015), M['lamp'], axis='Y', seg=32)
    bl.cyl('lamp_rim', 0.1, 0.04, G(sx * 0.66, 0.88, ZF - 0.005), M['chrome'], axis='Y', seg=32)
    gb('amber', (0.1, 0.05, 0.03), (sx * 0.66, 1.04, ZF - 0.005), 'amber', 0.01)
    gb(nm.replace('lamp', 'tail'), (0.09, 0.16, 0.03), (sx * 0.8, 0.86, ZR + 0.01), 'tail', 0.01)
gb('grille', (0.84, 0.3, 0.02), (0.0, 0.88, ZF - 0.006), 'steel', 0.01)
for i in range(9):
    gb('grille_bar', (0.03, 0.28, 0.025), (-0.36 + i * 0.09, 0.88, ZF - 0.014), 'paint', 0.005)
gb('bumper_f', (2.02, 0.13, 0.12), (0.0, 0.52, ZF - 0.07), 'steel', 0.02)
gb('bumper_r', (2.02, 0.13, 0.12), (0.0, 0.52, ZR + 0.07), 'steel', 0.02)
for sx, nm in ((-1, 'mirror_l'), (1, 'mirror_r')):   # боковые зеркала на кронштейнах
    bl.beam('mirror_arm', G(sx * 0.97, 1.4, -1.86), G(sx * 1.13, 1.52, -1.9), 0.02, 0.02, M['steel'])
    gb(nm, (0.035, 0.24, 0.16), (sx * 1.15, 1.6, -1.9), 'mirror', 0.015)

# ---------- колёса, рама ----------
for nm, x, z in (('wheel_fl', -0.74, -1.15), ('wheel_fr', 0.74, -1.15), ('wheel_rl', -0.74, 1.15), ('wheel_rr', 0.74, 1.15)):
    parts = [bl.cyl(nm + '_t', 0.38, 0.24, G(x, 0.38, z), M['tyre'], axis='X', seg=32, bevel=0.05),
             bl.cyl(nm + '_r', 0.2, 0.25, G(x, 0.38, z), M['rim'], axis='X', seg=20),
             bl.cyl(nm + '_h', 0.07, 0.27, G(x, 0.38, z), M['chrome'], axis='X', seg=12)]
    w = bl.join(nm, parts)
    bl.origin_to(w, G(x, 0.38, z))
gb('frame', (1.1, 0.16, 3.8), (0.0, 0.4, 0.0), 'steel', 0.02)
for z in (-1.15, 1.15):
    bl.cyl('axle', 0.06, 1.3, G(0.0, 0.38, z), M['steel'], axis='X', seg=12)

# ---------- салон ----------
gb('floor', (1.8, 0.05, 4.0), (0.0, 0.535, 0.05), 'rubber', 0.01)
for sx in (-1, 1):   # кожухи колёс внутри (арки вырезаны в стенке насквозь)
    for zc in (-1.15, 1.15):
        gb('housing', (0.34, 0.36, 1.02), (sx * 0.79, 0.69, zc), 'inner', 0.1, 3)
for sx in (-1, 1):   # передние сиденья
    x = sx * 0.5
    gb('seat_base', (0.38, 0.4, 0.38), (x, 0.76, -1.22), 'steel', 0.02)
    gb('seat', (0.5, 0.13, 0.5), (x, 1.0, -1.22), 'seat', 0.05, 3)
    b = gb('seat_back', (0.5, 0.6, 0.11), (x, 1.36, -0.94), 'seat', 0.05, 3)
    b.rotation_euler.x += math.radians(-10)
gb('engine_cover', (0.56, 0.44, 0.95), (0.0, 0.78, -1.5), 'cover', 0.08, 3)                 # мотор — между сиденьями
gb('dash', (1.86, 0.24, 0.24), (0.0, 1.2, ZF + 0.3), 'dash', 0.04, 3)
gb('dash_top', (1.86, 0.04, 0.34), (0.0, 1.33, ZF + 0.27), 'dash', 0.02)
gb('cluster', (0.36, 0.16, 0.03), (-0.5, 1.24, ZF + 0.43), 'seam', 0.01)
for k in range(3):
    bl.cyl('gauge', 0.042, 0.01, G(-0.62 + k * 0.12, 1.24, ZF + 0.448), M['gauge'], axis='Y', seg=20)
st = [bl.cyl('steer_rim', 0.2, 0.03, G(0, 0, 0), M['dash'], axis='Z', seg=40, caps=False)]
bm = bmesh.new()   # обод — тор
bmesh.ops.create_circle(bm, segments=48, radius=0.2)
me2 = bpy.data.meshes.new('rim_ring')
bm.to_mesh(me2)
bm.free()
bpy.data.objects.remove(st[0], do_unlink=True)
ring = bl.obj('steer', me2)
ring.data.materials.append(M['dash'])
sk = ring.modifiers.new('skin', 'SKIN')
for v in ring.data.skin_vertices[0].data:
    v.radius = (0.016, 0.016)
ring.modifiers.new('sub', 'SUBSURF').levels = 1
bl.apply_mods(ring)
spokes = [bl.beam('spoke', G(0, 0, 0), G(0.19 * math.cos(a), 0.19 * math.sin(a), 0.0), 0.04, 0.012, M['dash']) for a in (math.radians(-90), math.radians(30), math.radians(150))]
hub = bl.cyl('hub', 0.05, 0.05, G(0, 0, 0), M['dash'], axis='Y', seg=20)
wheel = bl.join('steer', [ring] + spokes + [hub])
# руль почти лежит: плоскость колеса наклонена на 35° к горизонту, низ — к водителю
wheel.matrix_world = Matrix.Translation(G(-0.5, 1.33, -1.66)) @ Matrix.Rotation(math.radians(-55), 4, 'X')
bl.apply_rot(wheel)
bl.origin_to(wheel, G(-0.5, 1.33, -1.66))
bl.beam('column', G(-0.5, 1.3, -1.7), G(-0.5, 0.75, -2.02), 0.05, 0.05, M['steel'])
bl.beam('gear', G(-0.12, 0.9, -1.12), G(-0.16, 1.18, -1.2), 0.02, 0.02, M['steel'])
bl.cyl('gear_knob', 0.03, 0.05, G(-0.16, 1.2, -1.2), M['dash'], axis='Z', seg=12)
for sx, z0, z1 in ((-1, -0.78, 1.86), (1, 0.32, 1.86)):   # лавки вдоль бортов (справа — с проходом к боковой двери)
    x = sx * 0.7
    gb('bench_base', (0.36, 0.4, z1 - z0), (x, 0.76, (z0 + z1) / 2), 'steel', 0.02)
    gb('bench', (0.44, 0.12, z1 - z0), (x, 0.99, (z0 + z1) / 2), 'seat', 0.04, 3)
    b = gb('bench_back', (0.08, 0.46, z1 - z0), (sx * 0.9, 1.33, (z0 + z1) / 2), 'seat', 0.03, 3)
    b.rotation_euler.y += math.radians(8 * sx)
gb('dome', (0.24, 0.04, 0.12), (0.0, 2.0, 0.4), 'bulb', 0.015)
gb('rear_shelf', (1.6, 0.03, 0.3), (0.0, 1.62, ZR - 0.22), 'steel', 0.01)

bl.export(OUT)
