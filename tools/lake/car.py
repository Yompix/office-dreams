"""Машина отца (WHAT THE LAKE KEEPS): старый седан 90-х целиком — кузов, стёкла, колёса, фары и салон — кодом в Blender.
Готовых CC0-машин нет (Poly Haven — без машин, у остальных — игрушечный low-poly), поэтому строим сами.
Кузов — «лофт» по поперечным сечениям от бампера до бампера (стёкла — зоны того же сечения), подразделение с острыми
кромками по линии окон и краю крыши, арки колёс — вырезом; швы дверей — тёмными полосками по поверхности.
Салон — торпедо (сечение по ширине), козырёк приборов, бардачок (отдельно, петля снизу), руль (отдельно, в плоскости XY,
ось Z — к груди водителя), кресла с боковой поддержкой, задний диван, обивка дверей, потолок, стойки, ковёр с тоннелем,
консоль с рычагом, зеркало, козырьки. Размеры — под посадку героев в godot/lake/scripts/world/car.gd.
Оси Blender: X — вправо (водитель слева), Y — вперёд, Z — вверх, земля Z = 0 (в Godot вперёд станет −Z).
  /home/user/tools/blender45/blender -b --factory-startup --python tools/lake/car.py -- godot/lake/assets/models/sedan.glb
"""
import bpy, bmesh, math, os, sys
from mathutils import Vector, Matrix
from mathutils.bvhtree import BVHTree

OUT = sys.argv[sys.argv.index('--') + 1]
HERE = os.path.dirname(os.path.abspath(__file__))
TEX = os.path.join(HERE, '../../godot/lake/assets/ph')
sys.path.insert(0, HERE)
from garments import _image   # картинка ткани с оттенком (копия пикселей)

bpy.ops.wm.read_factory_settings(use_empty=True)
SC = bpy.context.scene


# ---------- материалы ----------
def mat(name, col, rough=0.5, metal=0.0, alpha=1.0, emit=None, cull=False, coat=0.0, tex=None, tint=None, tile=1.0, nor=True):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    bs = nt.nodes['Principled BSDF']
    bs.inputs['Base Color'].default_value = (*col, 1.0)
    bs.inputs['Roughness'].default_value = rough
    bs.inputs['Metallic'].default_value = metal
    if alpha < 1.0:
        bs.inputs['Alpha'].default_value = alpha
        for attr, val in (('surface_render_method', 'BLENDED'), ('blend_method', 'BLEND')):
            try:
                setattr(m, attr, val)
            except Exception:
                pass
    if emit:
        bs.inputs['Emission Color'].default_value = (*emit[0], 1.0)
        bs.inputs['Emission Strength'].default_value = emit[1]
    if coat:
        bs.inputs['Coat Weight'].default_value = coat
        bs.inputs['Coat Roughness'].default_value = 0.04
    m.use_backface_culling = cull
    if tex:
        base = os.path.join(TEX, tex, tex)
        tc = nt.nodes.new('ShaderNodeTexCoord')
        mp = nt.nodes.new('ShaderNodeMapping')
        mp.inputs['Scale'].default_value = (tile, tile, tile)
        nt.links.new(tc.outputs['UV'], mp.inputs['Vector'])
        d = nt.nodes.new('ShaderNodeTexImage')
        d.image = _image(base + '_diff_1k.jpg', tint, name + '_diffuse')
        nt.links.new(mp.outputs['Vector'], d.inputs['Vector'])
        nt.links.new(d.outputs['Color'], bs.inputs['Base Color'])
        if nor and os.path.exists(base + '_nor_gl_1k.jpg'):
            n = nt.nodes.new('ShaderNodeTexImage')
            n.image = bpy.data.images.load(base + '_nor_gl_1k.jpg', check_existing=True)
            n.image.colorspace_settings.name = 'Non-Color'
            nt.links.new(mp.outputs['Vector'], n.inputs['Vector'])
            nm = nt.nodes.new('ShaderNodeNormalMap')
            nt.links.new(n.outputs['Color'], nm.inputs['Color'])
            nt.links.new(nm.outputs['Normal'], bs.inputs['Normal'])
    return m


M = {
    'paint': mat('paint', (0.055, 0.075, 0.1), 0.32, 0.55, coat=0.8, cull=True),       # тёмно-синий металлик, лак
    'glass': mat('glass', (0.08, 0.1, 0.11), 0.03, 0.0, alpha=0.18),
    'chrome': mat('chrome', (0.8, 0.8, 0.82), 0.12, 1.0),
    'trim': mat('trim', (0.025, 0.025, 0.027), 0.55),                                   # чёрный пластик снаружи
    'seam': mat('seam', (0.004, 0.004, 0.005), 0.8),
    'tire': mat('tire', (0.022, 0.022, 0.022), 0.85),
    'rim': mat('rim', (0.45, 0.46, 0.48), 0.35, 0.85),
    'lamp': mat('lamp', (0.85, 0.85, 0.8), 0.05, 0.0, emit=((1.0, 0.95, 0.82), 0.0)),   # стекло фар: свет включает игра
    'tail': mat('tail', (0.35, 0.01, 0.01), 0.15, 0.0, emit=((1.0, 0.05, 0.03), 0.0)),
    'plate': mat('plate', (0.75, 0.75, 0.72), 0.5),
    'liner': mat('liner', (0.012, 0.012, 0.012), 0.95),
    # салон
    'dash': mat('dash', (0.05, 0.048, 0.046), 0.62),
    'dash2': mat('dash2', (0.075, 0.072, 0.068), 0.7),
    'gauge': mat('gauge', (0.01, 0.01, 0.01), 0.3),
    'seat': mat('seat', (0.3, 0.3, 0.3), 0.95, tex='poly_wool_herringbone', tint=(0.32, 0.3, 0.28), tile=4.0),
    'door': mat('door', (0.085, 0.08, 0.075), 0.75),
    'roof': mat('roof', (0.3, 0.29, 0.27), 0.95, tex='wool_boucle', tint=(0.3, 0.29, 0.28), tile=9.0),
    'trim_in': mat('trim_in', (0.11, 0.105, 0.1), 0.7),                                # пластик стоек
    'carpet': mat('carpet', (0.035, 0.034, 0.033), 1.0, tex='wool_boucle', tint=(0.09, 0.088, 0.085), tile=10.0),
    'wheelgrip': mat('wheelgrip', (0.03, 0.028, 0.026), 0.55),
    'mirror': mat('mirror', (0.9, 0.9, 0.92), 0.02, 1.0),
}


def obj(name, me):
    o = bpy.data.objects.new(name, me)
    SC.collection.objects.link(o)
    return o


def apply_mods(o):
    bpy.context.view_layer.objects.active = o
    for x in bpy.context.selected_objects:
        x.select_set(False)
    o.select_set(True)
    for md in list(o.modifiers):
        bpy.ops.object.modifier_apply(modifier=md.name)


def smooth(o, angle=40):
    bpy.context.view_layer.objects.active = o
    for x in bpy.context.selected_objects:
        x.select_set(False)
    o.select_set(True)
    bpy.ops.object.shade_smooth_by_angle(angle=math.radians(angle))


def box_uv(o, scale=1.0):
    """развёртка «коробкой»: по главной оси нормали грани (ткань на креслах, потолке, ковре)"""
    me = o.data
    if not me.uv_layers:
        me.uv_layers.new(name='UVMap')
    uv = me.uv_layers.active.data
    for p in me.polygons:
        n = p.normal
        ax = max(range(3), key=lambda i: abs(n[i]))
        for li in p.loop_indices:
            co = o.matrix_world @ me.vertices[me.loops[li].vertex_index].co
            u, v = [(co.y, co.z), (co.x, co.z), (co.x, co.y)][ax]
            uv[li].uv = (u * scale, v * scale)


def rbox(name, size, loc, m, bevel=0.02, seg=3, rot=(0, 0, 0), uv=0.0):
    """скруглённый брусок (литой пластик, подушки)"""
    bm = bmesh.new()
    bmesh.ops.create_cube(bm, size=1.0)
    for v in bm.verts:
        v.co = Vector((v.co.x * size[0], v.co.y * size[1], v.co.z * size[2]))
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    o = obj(name, me)
    o.location = loc
    o.rotation_euler = [math.radians(a) for a in rot]
    me.materials.append(M[m])
    if bevel > 0:
        b = o.modifiers.new('bev', 'BEVEL')
        b.width = min(bevel, min(size) * 0.45)
        b.segments = seg
        b.limit_method = 'NONE'
    apply_mods(o)
    smooth(o, 60)
    if uv:
        box_uv(o, uv)
    return o


def beam(name, p0, p1, w, h, m, up=Vector((0, 0, 1)), bevel=0.01):
    """брусок от точки к точке (стойки салона)"""
    p0, p1 = Vector(p0), Vector(p1)
    d = p1 - p0
    o = rbox(name, (w, d.length, h), (0, 0, 0), m, bevel)
    y = d.normalized()
    x = y.cross(up).normalized()
    z = x.cross(y)
    o.matrix_world = Matrix.Translation((p0 + p1) * 0.5) @ Matrix((x, y, z)).transposed().to_4x4()
    return o


def cyl(name, r, depth, loc, m, axis='X', seg=32, bevel=0.0):
    bm = bmesh.new()
    bmesh.ops.create_cone(bm, cap_ends=True, segments=seg, radius1=r, radius2=r, depth=depth)
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    o = obj(name, me)
    if axis == 'X':
        o.rotation_euler = (0, math.radians(90), 0)
    elif axis == 'Y':
        o.rotation_euler = (math.radians(90), 0, 0)
    o.location = loc
    me.materials.append(M[m])
    if bevel:
        b = o.modifiers.new('bev', 'BEVEL')
        b.width = bevel
        b.segments = 4
    apply_mods(o)
    smooth(o, 50)
    return o


def join(name, parts):
    for x in bpy.context.selected_objects:
        x.select_set(False)
    for p in parts:
        p.select_set(True)
    bpy.context.view_layer.objects.active = parts[0]
    bpy.ops.object.join()
    o = parts[0]
    o.name = name
    o.data.name = name
    return o


def origin_to(o, p):
    """точка отсчёта предмета (петля бардачка, центр колеса); поворот предмета сохраняется"""
    p = Vector(p)
    o.data.transform(Matrix.Translation(o.matrix_world.to_3x3().inverted() @ (o.location - p)))
    o.location = p


def apply_rot(o):
    for x in bpy.context.selected_objects:
        x.select_set(False)
    o.select_set(True)
    bpy.context.view_layer.objects.active = o
    bpy.ops.object.transform_apply(location=False, rotation=True, scale=True)


# ---------- кузов: сечения ----------
#   y      низ   полуширина  окна(пояс)  крыша/капот: полуширина, высота   (y — вперёд)
SECT = [
    (2.86, 0.31, 0.78, 0.56, 0.66, 0.60),
    (2.80, 0.25, 0.855, 0.70, 0.78, 0.74),
    (2.68, 0.22, 0.875, 0.79, 0.81, 0.825),
    (2.20, 0.21, 0.885, 0.835, 0.825, 0.86),
    (1.60, 0.21, 0.89, 0.87, 0.83, 0.89),
    (0.95, 0.21, 0.89, 0.905, 0.825, 0.92),
    (0.645, 0.21, 0.89, 0.93, 0.82, 0.935),     # низ лобового
    (0.38, 0.21, 0.89, 0.94, 0.72, 1.25),
    (0.115, 0.21, 0.89, 0.95, 0.66, 1.565),     # верх лобового
    (-0.40, 0.21, 0.89, 0.96, 0.67, 1.585),     # стойка B
    (-0.52, 0.21, 0.89, 0.96, 0.67, 1.585),
    (-1.18, 0.21, 0.89, 0.97, 0.66, 1.555),     # верх заднего стекла
    (-1.45, 0.21, 0.89, 0.98, 0.69, 1.30),
    (-1.70, 0.22, 0.89, 0.99, 0.80, 1.005),     # низ заднего стекла — багажник
    (-2.00, 0.23, 0.885, 0.99, 0.82, 0.995),
    (-2.13, 0.26, 0.87, 0.95, 0.80, 0.965),
    (-2.21, 0.32, 0.79, 0.80, 0.70, 0.82),
]
CABIN = (-1.18, 0.645)


def ring(y, zb, w, zbelt, wr, zr):
    gh = zr - zbelt > 0.03                    # есть «теплица» (стёкла) над поясом
    p = [(0.0, zb), (w - 0.06, zb), (w, zb + 0.1), (w + 0.008, zbelt - 0.14), (w - 0.012, zbelt)]
    if gh:
        p += [(wr + 0.035, zr - 0.05), (wr - 0.035, zr), (wr * 0.5, zr + 0.016)]
    else:
        p += [(w - 0.035, zbelt + 0.01), (w - 0.09, zr), (w * 0.5, zr + 0.012)]
    top = (0.0, zr + (0.02 if gh else 0.014))
    right = [Vector((x, y, z)) for x, z in p[1:]]
    left = [Vector((-v.x, y, v.z)) for v in reversed(right)]
    return [Vector((0, y, p[0][1]))] + right + [Vector((0, y, top[1]))] + left


def body():
    rings = [ring(*s) for s in SECT]
    n = len(rings[0])                          # 16: 0 низ, 1..7 справа, 8 верх, 9..15 слева
    verts = [v for r in rings for v in r]
    faces, fm = [], []
    for i in range(len(rings) - 1):
        y0, y1 = SECT[i][0], SECT[i + 1][0]
        ym = (y0 + y1) * 0.5
        for j in range(n):
            a, b = i * n + j, i * n + (j + 1) % n
            faces.append((a, b, (i + 1) * n + (j + 1) % n, (i + 1) * n + j))
            seg = j if j < 8 else 15 - j       # 0..7 — та же зона справа и слева
            m = 0
            side_win = seg == 4 and CABIN[0] < ym < 0.60 and not (-0.52 < ym < -0.40)
            front_win = seg in (6, 7) and 0.115 < ym < 0.645
            rear_win = seg in (6, 7) and -1.70 < ym < -1.18
            if side_win or front_win or rear_win:
                m = 1
            fm.append(m)
    faces.append(tuple(range(n)))                                   # торцы
    fm.append(0)
    last = (len(rings) - 1) * n
    faces.append(tuple(range(last, last + n)))
    fm.append(0)
    me = bpy.data.meshes.new('body')
    me.from_pydata([tuple(v) for v in verts], [], faces)
    me.update()
    for p, m in zip(me.polygons, fm):
        p.material_index = m
    me.materials.append(M['paint'])
    me.materials.append(M['glass'])
    o = obj('body', me)
    bm = bmesh.new()
    bm.from_mesh(me)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    bm.to_mesh(me)
    bm.free()
    # острые кромки: линия окон, край крыши/капота, нижний край кузова
    cr = me.attributes.new('crease_edge', 'FLOAT', 'EDGE')
    sharp = {4: 0.75, 12: 0.75, 6: 0.55, 10: 0.55, 2: 0.4, 14: 0.4}   # справа k, слева 16 − k
    for e in me.edges:
        a, b = e.vertices
        if abs(a - b) == n:                  # продольное ребро
            j = a % n
            if j in sharp:
                cr.data[e.index].value = sharp[j]
        elif a // n in (0, len(rings) - 1) and b // n == a // n:
            cr.data[e.index].value = 0.6     # торцы бамперов
    s = o.modifiers.new('sub', 'SUBSURF')
    s.levels = 2
    s.render_levels = 2
    apply_mods(o)
    # арки колёс
    cutters = []
    for x in (-1, 1):
        for y in (1.92, -0.98):
            c = cyl('cut', 0.375, 0.5, (x * 0.9, y, 0.33), 'trim', seg=48)
            cutters.append(c)
    for c in cutters:
        b = o.modifiers.new('arch', 'BOOLEAN')
        b.operation = 'DIFFERENCE'
        b.object = c
        b.solver = 'EXACT'
        apply_mods(o)
    for c in cutters:
        bpy.data.objects.remove(c)
    smooth(o, 38)
    # стёкла — отдельный предмет
    for x in bpy.context.selected_objects:
        x.select_set(False)
    o.select_set(True)
    bpy.context.view_layer.objects.active = o
    bpy.ops.object.mode_set(mode='EDIT')
    bpy.ops.mesh.select_all(action='SELECT')
    bpy.ops.mesh.separate(type='MATERIAL')
    bpy.ops.object.mode_set(mode='OBJECT')
    for x in bpy.context.selected_objects:
        if x.data.materials and x.data.materials[0] and len(x.data.polygons):
            used = {p.material_index for p in x.data.polygons}
            if used == {1}:
                x.name = 'glass'
                x.data.name = 'glass'
    return bpy.data.objects['body']


def seams(o):
    """швы дверей и капота — тонкие тёмные полоски по поверхности кузова"""
    dg = bpy.context.evaluated_depsgraph_get()
    bvh = BVHTree.FromObject(o, dg)
    verts, faces = [], []

    def strip(pts, width=0.0035):
        for i in range(len(pts) - 1):
            (p0, n0), (p1, n1) = pts[i], pts[i + 1]
            t = (p1 - p0).normalized()
            s0 = n0.cross(t).normalized() * width * 0.5
            s1 = n1.cross(t).normalized() * width * 0.5
            k = len(verts)
            verts.extend([p0 - s0 + n0 * 0.0012, p0 + s0 + n0 * 0.0012, p1 + s1 + n1 * 0.0012, p1 - s1 + n1 * 0.0012])
            faces.append((k, k + 1, k + 2, k + 3))

    def hit_side(y, z, sx):
        r = bvh.ray_cast(Vector((sx * 1.5, y, z)), Vector((-sx, 0, 0)))
        return (r[0], r[1]) if r[0] else None

    def hit_top(x, y):
        r = bvh.ray_cast(Vector((x, y, 2.5)), Vector((0, 0, -1)))
        return (r[0], r[1]) if r[0] else None
    for sx in (-1, 1):
        for y, z1 in ((0.62, 0.9), (-0.46, 0.93), (-1.3, 0.93)):   # двери: передний край, между дверями, задний край
            pts = [hit_side(y, z, sx) for z in [0.3 + k * 0.025 for k in range(int((z1 - 0.3) / 0.025) + 1)]]
            strip([p for p in pts if p])
        pts = [hit_side(y, 0.3, sx) for y in [0.62 - k * 0.05 for k in range(39)]]   # низ дверей
        strip([p for p in pts if p])
    for y in (0.96, -1.72):                                         # задний край капота, передний край крышки багажника
        pts = [hit_top(x, y) for x in [-0.82 + k * 0.04 for k in range(42)]]
        strip([p for p in pts if p])
    for sx in (-1, 1):                                              # бока капота и багажника
        for ys in ([0.96 + k * 0.08 for k in range(22)], [-1.72 - k * 0.04 for k in range(11)]):
            pts = [hit_top(sx * 0.8, y) for y in ys]
            strip([p for p in pts if p])
    me = bpy.data.meshes.new('seams')
    me.from_pydata([tuple(v) for v in verts], [], faces)
    me.materials.append(M['seam'])
    return obj('seams', me)


def exterior():
    b = body()
    seams(b)
    dg = bpy.context.evaluated_depsgraph_get()
    bvh = BVHTree.FromObject(b, dg)

    def front(x, z, sgn=1):   # где поверхность кузова спереди (sgn=1) или сзади (−1) на высоте z
        r = bvh.ray_cast(Vector((x, sgn * 4.0, z)), Vector((0, -sgn, 0)))
        return r[0].y if r[0] else sgn * 2.8

    def side(y, z, sx):
        r = bvh.ray_cast(Vector((sx * 1.5, y, z)), Vector((-sx, 0, 0)))
        return r[0].x if r[0] else sx * 0.89
    # бамперы, решётка, фары, фонари, номера, зеркала, ручки — по поверхности кузова; подкрылки, днище
    rbox('bumper_f', (1.74, 0.16, 0.2), (0, front(0, 0.38) + 0.03, 0.38), 'trim', 0.05)
    rbox('bumper_r', (1.74, 0.14, 0.2), (0, front(0, 0.42, -1) - 0.03, 0.42), 'trim', 0.05)
    yf = front(0, 0.665)
    rbox('grille', (0.74, 0.04, 0.13), (0, yf + 0.004, 0.665), 'trim', 0.01)
    rbox('grille_frame', (0.78, 0.03, 0.155), (0, yf, 0.665), 'chrome', 0.008)
    for x in (-1, 1):
        yl = front(x * 0.62, 0.665)
        rbox('lamp', (0.3, 0.04, 0.13), (x * 0.62, yl + 0.006, 0.665), 'lamp', 0.012)
        rbox('lamp_rim', (0.32, 0.03, 0.15), (x * 0.62, yl, 0.665), 'chrome', 0.008)
        rbox('tail', (0.36, 0.04, 0.16), (x * 0.64, front(x * 0.64, 0.8, -1) - 0.006, 0.80), 'tail', 0.015)
        xs = side(0.56, 1.0, x)
        rbox('mirror_out', (0.07, 0.15, 0.1), (xs + x * 0.07, 0.56, 1.0), 'trim', 0.025)
        rbox('mirror_stem', (0.06, 0.05, 0.03), (xs + x * 0.025, 0.58, 0.97), 'trim', 0.01)
        for y in (0.08, -0.95):
            rbox('handle', (0.02, 0.13, 0.03), (side(y, 0.84, x) + x * 0.006, y, 0.84), 'chrome', 0.008)
    rbox('plate_f', (0.52, 0.012, 0.11), (0, front(0, 0.41) + 0.012 + 0.03, 0.41), 'plate', 0.004)
    rbox('plate_r', (0.52, 0.012, 0.11), (0, front(0, 0.66, -1) - 0.008, 0.66), 'plate', 0.004, rot=(-8, 0, 0))
    rbox('underbody', (1.66, 4.9, 0.02), (0, 0.32, 0.215), 'liner', 0.0)
    for x in (-1, 1):
        for y in (1.92, -0.98):
            bm = bmesh.new()
            bmesh.ops.create_cone(bm, cap_ends=False, segments=40, radius1=0.37, radius2=0.37, depth=0.28)
            me = bpy.data.meshes.new('liner')
            bm.to_mesh(me)
            bm.free()
            o = obj('liner', me)
            o.rotation_euler = (0, math.radians(90), 0)
            o.location = (x * 0.75, y, 0.33)
            me.materials.append(M['liner'])
            # колесо: шина со скруглёнными боками, обод, колпак
            t = cyl('tire', 0.325, 0.2, (0, 0, 0), 'tire', seg=40, bevel=0.06)
            r = cyl('rim', 0.2, 0.205, (0, 0, 0), 'rim', seg=32)
            cap = cyl('cap', 0.17, 0.02, (x * 0.098, 0, 0), 'chrome', seg=32, bevel=0.006)
            w = join('wheel_%s%s' % ('f' if y > 0 else 'r', 'l' if x < 0 else 'r'), [t, r, cap])
            apply_rot(w)                     # ось колеса — X предмета: игра крутит его вокруг X
            w.location = (x * 0.76, y, 0.325)


# ---------- салон ----------
def dash():
    prof = [(0.70, 0.90), (0.6, 0.935), (0.5, 0.947), (0.44, 0.935), (0.405, 0.86), (0.385, 0.72), (0.4, 0.58),
            (0.47, 0.47), (0.6, 0.42), (0.72, 0.5), (0.74, 0.86)]
    xs = [-0.8, -0.6, -0.15, 0.15, 0.6, 0.8]
    verts, faces = [], []
    n = len(prof)
    for x in xs:
        verts += [(x, y, z) for y, z in prof]
    for i in range(len(xs) - 1):
        for j in range(n):
            faces.append((i * n + j, i * n + (j + 1) % n, (i + 1) * n + (j + 1) % n, (i + 1) * n + j))
    faces.append(tuple(range(n))[::-1])
    faces.append(tuple(range((len(xs) - 1) * n, len(xs) * n)))
    me = bpy.data.meshes.new('dash')
    me.from_pydata(verts, [], faces)
    me.materials.append(M['dash'])
    o = obj('dash', me)
    bm = bmesh.new()
    bm.from_mesh(me)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    bm.to_mesh(me)
    bm.free()
    b = o.modifiers.new('bev', 'BEVEL')
    b.width = 0.025
    b.segments = 3
    b.limit_method = 'ANGLE'
    apply_mods(o)
    smooth(o, 35)
    # козырёк приборов и щиток, центральная панель с магнитолой, решётки обдува
    rbox('binnacle', (0.46, 0.13, 0.085), (-0.36, 0.47, 0.975), 'dash', 0.03)
    rbox('cluster', (0.42, 0.012, 0.13), (-0.36, 0.415, 0.88), 'gauge', 0.006, rot=(-18, 0, 0))
    rbox('stack', (0.26, 0.03, 0.26), (0, 0.39, 0.7), 'dash2', 0.012, rot=(-8, 0, 0))
    rbox('radio', (0.19, 0.02, 0.055), (0, 0.375, 0.76), 'gauge', 0.004, rot=(-8, 0, 0))
    for x in (-0.6, -0.08, 0.08, 0.6):
        rbox('vent', (0.11, 0.02, 0.05), (x, 0.405, 0.85 if abs(x) > 0.3 else 0.835), 'gauge', 0.006)
    g = rbox('glovebox', (0.46, 0.025, 0.17), (0.38, 0.372, 0.68), 'dash2', 0.012, rot=(-6, 0, 0))
    origin_to(g, (0.38, 0.375, 0.6))
    rbox('glove_handle', (0.08, 0.012, 0.02), (0.38, 0.358, 0.74), 'chrome', 0.004)


def steer():
    """руль в плоскости XY у начала координат (ось Z — к груди); игра ставит его в руки водителя"""
    # обод — тор
    parts = []
    tor = bpy.data.meshes.new('rim')
    bm = bmesh.new()
    R, r, U, V = 0.178, 0.0155, 48, 12
    vs = []
    for i in range(U):
        a = 2 * math.pi * i / U
        for j in range(V):
            b = 2 * math.pi * j / V
            vs.append(bm.verts.new(((R + r * math.cos(b)) * math.cos(a), (R + r * math.cos(b)) * math.sin(a), r * math.sin(b))))
    for i in range(U):
        for j in range(V):
            bm.faces.new((vs[i * V + j], vs[((i + 1) % U) * V + j], vs[((i + 1) % U) * V + (j + 1) % V], vs[i * V + (j + 1) % V]))
    bm.to_mesh(tor)
    bm.free()
    t = obj('rim', tor)
    tor.materials.append(M['wheelgrip'])
    smooth(t, 80)
    parts.append(t)
    hub = cyl('hub', 0.06, 0.05, (0, 0, -0.035), 'dash', axis='Z', seg=24, bevel=0.012)
    parts.append(hub)
    for a in (90, 210, 330):
        ar = math.radians(a)
        p0 = Vector((math.cos(ar) * 0.05, math.sin(ar) * 0.05, -0.03))
        p1 = Vector((math.cos(ar) * 0.17, math.sin(ar) * 0.17, 0.0))
        parts.append(beam('spoke', p0, p1, 0.04, 0.012, 'dash', up=Vector((0, 0, 1)), bevel=0.004))
    s = join('steer', parts)
    apply_rot(s)
    origin_to(s, (0, 0, 0))


def seats():
    for sx in (-1, 1):
        x = sx * 0.36
        p = [rbox('cushion', (0.5, 0.52, 0.12), (x, -0.33, 0.405), 'seat', 0.045, 4, rot=(6, 0, 0), uv=3.0),
             rbox('pad', (0.36, 0.46, 0.06), (x, -0.33, 0.47), 'seat', 0.025, 3, rot=(6, 0, 0), uv=3.0)]
        for bx in (-1, 1):
            p.append(rbox('bolster', (0.08, 0.46, 0.1), (x + bx * 0.215, -0.34, 0.49), 'seat', 0.035, 3, rot=(6, 0, bx * 4), uv=3.0))
        p.append(rbox('back', (0.5, 0.13, 0.64), (x, -0.66, 0.82), 'seat', 0.05, 4, rot=(14, 0, 0), uv=3.0))
        p.append(rbox('backpad', (0.36, 0.06, 0.56), (x, -0.6, 0.83), 'seat', 0.025, 3, rot=(14, 0, 0), uv=3.0))
        for bx in (-1, 1):
            p.append(rbox('bolster', (0.08, 0.12, 0.5), (x + bx * 0.215, -0.6, 0.8), 'seat', 0.035, 3, rot=(14, 0, -bx * 8), uv=3.0))
        p.append(rbox('headrest', (0.27, 0.1, 0.19), (x, -0.8, 1.29), 'seat', 0.045, 4, rot=(10, 0, 0), uv=3.0))
        for bx in (-1, 1):
            p.append(cyl('post', 0.006, 0.12, (x + bx * 0.07, -0.78, 1.16), 'chrome', axis='Z', seg=8))
        p.append(rbox('rail', (0.42, 0.5, 0.04), (x, -0.33, 0.2), 'trim', 0.01))
        join('seat_' + ('l' if sx < 0 else 'r'), p)
    p = [rbox('rcushion', (1.42, 0.52, 0.14), (0, -1.3, 0.37), 'seat', 0.05, 4, rot=(5, 0, 0), uv=3.0),
         rbox('rback', (1.42, 0.13, 0.6), (0, -1.62, 0.76), 'seat', 0.05, 4, rot=(20, 0, 0), uv=3.0)]
    for bx in (-1, 1):   # строчка: три места на диване
        p.append(rbox('rseam', (0.01, 0.5, 0.01), (bx * 0.24, -1.29, 0.443), 'trim', 0.0, rot=(5, 0, 0)))
        p.append(rbox('rseam', (0.01, 0.012, 0.52), (bx * 0.24, -1.56, 0.77), 'trim', 0.0, rot=(20, 0, 0)))
    join('seat_rear', p)


def cabin():
    # обивка дверей: панель, подлокотник, ручка, верх двери; спереди и сзади — одной панелью на сторону
    for sx in (-1, 1):
        p = [rbox('card', (0.04, 2.18, 0.72), (sx * 0.775, -0.47, 0.58), 'door', 0.012),
             rbox('sill', (0.17, 2.18, 0.04), (sx * 0.8, -0.47, 0.945), 'dash', 0.015)]   # до стекла: снаружи не видно щели
        for y in (-0.06, -1.02):
            p.append(rbox('armrest', (0.07, 0.42, 0.055), (sx * 0.735, y, 0.64), 'dash2', 0.02))
            p.append(rbox('pull', (0.025, 0.1, 0.03), (sx * 0.752, y + 0.3, 0.78), 'chrome', 0.008))
            p.append(rbox('pocket', (0.05, 0.4, 0.12), (sx * 0.745, y + 0.05, 0.33), 'dash', 0.015))
        p.append(rbox('kick', (0.05, 0.06, 0.6), (sx * 0.77, 0.6, 0.6), 'dash', 0.01))
        join('door_' + ('l' if sx < 0 else 'r'), p)
    # потолок и стойки
    rbox('headliner', (1.36, 1.3, 0.025), (0, -0.53, 1.535), 'roof', 0.01, uv=2.0)
    for sx in (-1, 1):
        rbox('roofrail', (0.06, 1.3, 0.05), (sx * 0.69, -0.53, 1.505), 'roof', 0.02, rot=(0, sx * 30, 0), uv=2.0)
        beam('pillar_a', (sx * 0.8, 0.64, 0.95), (sx * 0.665, 0.12, 1.53), 0.07, 0.035, 'trim_in', up=Vector((-sx, 0, 0.3)).normalized())
        rbox('pillar_b', (0.05, 0.13, 0.6), (sx * 0.755, -0.46, 1.24), 'trim_in', 0.015, rot=(0, -sx * 7, 0))
        beam('pillar_c', (sx * 0.78, -1.62, 0.99), (sx * 0.67, -1.17, 1.53), 0.09, 0.05, 'trim_in', up=Vector((-sx, 0, 0.3)).normalized())
        rbox('visor', (0.34, 0.16, 0.016), (sx * 0.36, 0.03, 1.512), 'roof', 0.006, uv=2.0)
    rbox('shelf', (1.36, 0.28, 0.025), (0, -1.78, 0.985), 'dash', 0.008)
    # пол: ковёр, тоннель, щиток ног; консоль, рычаг, ручник
    rbox('carpet', (1.54, 2.35, 0.02), (0, -0.5, 0.14), 'carpet', 0.0, uv=1.0)
    rbox('tunnel', (0.28, 1.6, 0.13), (0, -0.25, 0.2), 'carpet', 0.05, uv=1.0)
    rbox('toeboard', (1.5, 0.03, 0.34), (0, 0.66, 0.3), 'carpet', 0.0, rot=(-30, 0, 0), uv=1.0)
    rbox('console', (0.24, 0.62, 0.16), (0, 0.02, 0.33), 'dash2', 0.03)
    rbox('console_lid', (0.22, 0.32, 0.07), (0, -0.48, 0.44), 'dash', 0.03)
    rbox('gearboot', (0.1, 0.1, 0.04), (0, 0.08, 0.425), 'dash', 0.03)
    cyl('gearstick', 0.009, 0.17, (0, 0.06, 0.51), 'dash', axis='Z', seg=10)
    bm = bmesh.new()
    bmesh.ops.create_uvsphere(bm, u_segments=16, v_segments=10, radius=0.026)
    me = bpy.data.meshes.new('knob')
    bm.to_mesh(me)
    bm.free()
    k = obj('knob', me)
    k.location = (0, 0.05, 0.6)
    me.materials.append(M['dash'])
    smooth(k, 80)
    beam('handbrake', (0, -0.18, 0.42), (0, -0.4, 0.5), 0.035, 0.03, 'dash', up=Vector((1, 0, 0)), bevel=0.012)
    # зеркало заднего вида
    rbox('mirror_in', (0.25, 0.04, 0.07), (0, 0.09, 1.445), 'dash', 0.015, rot=(-6, 0, 3))
    rbox('mirror_glass', (0.23, 0.004, 0.055), (0, 0.068, 1.445), 'mirror', 0.003, rot=(-6, 0, 3))
    cyl('mirror_stem', 0.01, 0.08, (0, 0.11, 1.5), 'dash', axis='Z', seg=8)


exterior()
dash()
steer()
seats()
cabin()
# в Godot: машина — «как есть», предметы по именам (body, glass, steer, glovebox, wheel_*, lamp, tail …)
for x in bpy.context.selected_objects:
    x.select_set(False)
for o in bpy.data.objects:
    if o.type == 'MESH':
        o.select_set(True)
os.makedirs(os.path.dirname(os.path.abspath(OUT)), exist_ok=True)
bpy.ops.export_scene.gltf(filepath=os.path.abspath(OUT), export_format='GLB', use_selection=True, export_apply=True, export_yup=True)
print('CAR', OUT, len([o for o in bpy.data.objects if o.type == 'MESH']), 'objects',
      sum(len(o.data.polygons) for o in bpy.data.objects if o.type == 'MESH'), 'faces')
