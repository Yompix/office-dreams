"""Своя одежда героев WHAT THE LAKE KEEPS — шьётся по телу в Blender (tools/chars/gen.py зовёт build() после позы покоя).
В CC0-пакетах MakeHuman почти нет зимней одежды (одна куртка — у Итана из Hush Till Six), поэтому куртки, худи с капюшоном,
жилет и шапку делаем сами: участок тела по весам костей → копия, отодвинутая наружу и сглаженная (ткань не повторяет мышцы),
с толщиной; веса костей те же, что у тела, — одежда двигается вместе с ним; кожа под одеждой удаляется (не протыкает).
Ткань — текстуры Poly Haven (CC0): цвет × оттенок, нормали, шероховатость.
Виды (C['garments'] в tools/lake/chars.json):
  jacket — торс и рукава до запястий, воротник; off — насколько велика (у Ноя отцовская — не по размеру), hem — низ от таза
  vest   — то же без рукавов
  hood   — капюшон на голове с вырезом для лица (с jacket — худи)
  beanie — шапка от макушки до бровей, с отворотом
"""
import bpy, bmesh, os
from mathutils import Vector

TEX = os.path.join(os.path.dirname(os.path.abspath(__file__)), '../../godot/lake/assets/ph')
TORSO = ['spine_01', 'spine_02', 'spine_03', 'clavicle_l', 'clavicle_r', 'pelvis']
ARMS = ['upperarm_l', 'upperarm_r', 'lowerarm_l', 'lowerarm_r']
HANDS = ['hand_l', 'hand_r'] + [f'{f}_0{i}_{s}' for f in ('index', 'middle', 'pinky', 'ring', 'thumb') for i in (1, 2, 3) for s in ('l', 'r')]
HEAD = ['head', 'neck_01']


def _otype(o):
    from bl_ext.user_default.mpfb.services import ObjectService
    return ObjectService.get_object_type(o)


def _body():
    return next(o for o in bpy.data.objects if o.type == 'MESH' and _otype(o) == 'Basemesh')


def _arm():
    return next(o for o in bpy.data.objects if o.type == 'ARMATURE')


def _w(o, names):
    idx = {o.vertex_groups[n].index for n in names if n in o.vertex_groups}
    out = [0.0] * len(o.data.vertices)
    for v in o.data.vertices:
        for g in v.groups:
            if g.group in idx:
                out[v.index] += g.weight
    return out


def _bone_z(name):
    a = _arm()
    return (a.matrix_world @ a.data.bones[name].head_local).z


def _brow_z():
    br = [o for o in bpy.data.objects if o.type == 'MESH' and _otype(o) == 'Eyebrows']
    if not br:
        return _bone_z('head') + 0.11
    zs = [(br[0].matrix_world @ v.co).z for v in br[0].data.vertices]
    return min(zs)


def _image(path, tint, name):
    src = bpy.data.images.load(path, check_existing=True)
    if not tint:
        return src
    img = src.copy()
    img.name = name
    px = list(img.pixels)
    for i in range(0, len(px), 4):
        px[i] *= tint[0]
        px[i + 1] *= tint[1]
        px[i + 2] *= tint[2]
    img.pixels[:] = px
    img.update()
    img.pack()
    return img


def _material(name, tex, tint, tile, rough_k=1.0):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    bs = nt.nodes['Principled BSDF']
    base = os.path.join(TEX, tex, tex)
    tc = nt.nodes.new('ShaderNodeTexCoord')
    mp = nt.nodes.new('ShaderNodeMapping')
    mp.inputs['Scale'].default_value = (tile, tile, tile)
    nt.links.new(tc.outputs['UV'], mp.inputs['Vector'])
    d = nt.nodes.new('ShaderNodeTexImage')
    d.image = _image(base + '_diff_1k.jpg', tint, name + '_diffuse')
    nt.links.new(mp.outputs['Vector'], d.inputs['Vector'])
    nt.links.new(d.outputs['Color'], bs.inputs['Base Color'])
    n = nt.nodes.new('ShaderNodeTexImage')
    n.image = bpy.data.images.load(base + '_nor_gl_1k.jpg', check_existing=True)
    n.image.colorspace_settings.name = 'Non-Color'
    nt.links.new(mp.outputs['Vector'], n.inputs['Vector'])
    nm = nt.nodes.new('ShaderNodeNormalMap')
    nt.links.new(n.outputs['Color'], nm.inputs['Color'])
    nt.links.new(nm.outputs['Normal'], bs.inputs['Normal'])
    r = nt.nodes.new('ShaderNodeTexImage')
    r.image = bpy.data.images.load(base + '_arm_1k.jpg', check_existing=True)
    r.image.colorspace_settings.name = 'Non-Color'
    nt.links.new(mp.outputs['Vector'], r.inputs['Vector'])
    sep = nt.nodes.new('ShaderNodeSeparateColor')
    nt.links.new(r.outputs['Color'], sep.inputs['Color'])
    nt.links.new(sep.outputs['Green'], bs.inputs['Roughness'])
    nt.links.new(sep.outputs['Blue'], bs.inputs['Metallic'])
    return m


def _shell(body, name, keep, offset, thick, smooth=3, center=None, back=0.0):
    me = body.data.copy()
    me.name = name
    ob = bpy.data.objects.new(name, me)
    bpy.context.scene.collection.objects.link(ob)
    ob.parent = body.parent
    ob.matrix_world = body.matrix_world.copy()
    if not ob.vertex_groups:   # имена групп костей — как у тела
        for g in body.vertex_groups:
            ob.vertex_groups.new(name=g.name)
    if me.shape_keys:
        ob.shape_key_clear()
    bm = bmesh.new()
    bm.from_mesh(me)
    bm.verts.ensure_lookup_table()
    bmesh.ops.delete(bm, geom=[v for v in bm.verts if not keep[v.index]], context='VERTS')
    bm.normal_update()
    inv = body.matrix_world.inverted()
    c_loc = inv @ center if center is not None else None
    orig = {v.index: (v.co.copy(), v.normal.copy()) for v in bm.verts}
    need = {}
    for v in bm.verts:
        extra = 0.0
        if c_loc is not None:   # капюшон: к затылку и макушке ткань отходит дальше (мешком), у лица — ближе
            d = (v.co - c_loc)
            dl = max(d.length, 1e-6)
            extra = back * (max(0.0, d.y / dl) * 0.9 + max(0.0, d.z / dl) * 0.5)
        v.co += v.normal * (offset + extra)
        need[v.index] = (offset + extra) * 0.85
    for _ in range(smooth):
        bmesh.ops.smooth_vert(bm, verts=bm.verts, factor=0.5, use_axis_x=True, use_axis_y=True, use_axis_z=True)
        # сглаживание тянет ткань внутрь во впадинах (грудина) — не ближе 85 % отступа к телу, иначе рубашка проступает
        for v in bm.verts:
            p0, n0 = orig[v.index]
            h = (v.co - p0).dot(n0)
            if h < need[v.index]:
                v.co += n0 * (need[v.index] - h)
    for f in bm.faces:   # у части граней тела свой номер материала (соски, пупок) — на одежде без них были белые пятна
        f.material_index = 0
    bm.to_mesh(me)
    bm.free()
    ob.data.materials.clear()
    bpy.context.view_layer.objects.active = ob
    for o in bpy.context.selected_objects:
        o.select_set(False)
    ob.select_set(True)
    sol = ob.modifiers.new('solid', 'SOLIDIFY')
    sol.thickness = thick
    sol.offset = 1.0
    sol.use_rim = True
    bpy.ops.object.modifier_apply(modifier='solid')
    mod = ob.modifiers.new('arm', 'ARMATURE')
    mod.object = _arm()
    return ob


def _interior(body, keep, rings=2):
    """вершины участка, отстоящие от его края на rings рёбер, — под одеждой (их кожу можно удалить)"""
    me = body.data
    inside = set(i for i, k in enumerate(keep) if k)
    nb = [[] for _ in me.vertices]
    for e in me.edges:
        a, b = e.vertices
        nb[a].append(b)
        nb[b].append(a)
    for _ in range(rings):
        edge = {i for i in inside if any(j not in inside for j in nb[i])}
        inside -= edge
    return inside


def _hide_skin(body, verts):
    if not verts:
        return
    bm = bmesh.new()
    bm.from_mesh(body.data)
    bm.verts.ensure_lookup_table()
    bmesh.ops.delete(bm, geom=[f for f in bm.faces if all(v.index in verts for v in f.verts)], context='FACES_ONLY')
    bm.to_mesh(body.data)
    bm.free()


def build(specs):
    body = _body()
    me = body.data
    w_torso = _w(body, TORSO)
    w_arms = _w(body, ARMS)
    w_hands = _w(body, HANDS)
    w_head = _w(body, HEAD)
    w_headonly = _w(body, ['head'])
    co = [body.matrix_world @ v.co for v in me.vertices]
    # только сама кожа: служебная геометрия MakeHuman (сферы глаз, «колготки», «юбка», кубики суставов) в выкройку не идёт —
    # иначе на одежде шнуры, а перед глазами капюшона — чёрные диски
    helper = [g.name for g in body.vertex_groups if g.name.startswith('helper') or g.name in ('HelperGeometry', 'JointCubes') or g.name.startswith('joint-')]
    skin = [w <= 0.0 for w in _w(body, helper)]
    hip = _bone_z('pelvis')
    neck = _bone_z('neck_01')
    brow = _brow_z()
    center = Vector((0.0, 0.0, 0.0))
    hv = [co[i] for i in range(len(co)) if w_headonly[i] > 0.6]
    for p in hv:
        center += p
    center /= max(1, len(hv))
    hide = set()
    covers = []   # (подол, есть ли рукава): рубашка MakeHuman под нашей курткой удаляется целиком — она висит свободнее куртки
    for g in specs:
        kind = g['kind']
        name = g.get('name', kind)
        if kind in ('jacket', 'vest'):
            hem = hip - min(g.get('hem', 0.03), 0.035)
            keep = []
            for i in range(len(co)):
                arm = w_arms[i]
                ok = skin[i] and (w_torso[i] + arm) > 0.5 and w_hands[i] < 0.2 and w_head[i] < 0.45 and co[i].z > hem
                if kind == 'vest' and arm > 0.45:
                    ok = False
                if kind == 'jacket' and co[i].z > neck + 0.03 and w_head[i] > 0.2:   # воротник — чуть выше основания шеи
                    ok = False
                keep.append(ok)
            # воротник: немного шеи у основания
            for i in range(len(co)):
                if skin[i] and g.get('collar', True) and w_head[i] > 0.3 and neck - 0.02 < co[i].z < neck + 0.06 and w_headonly[i] < 0.3:
                    keep[i] = True
            ob = _shell(body, name, keep, g.get('off', 0.018), g.get('thick', 0.005))
            hide |= _interior(body, keep, 1)
            covers.append((hem, kind == 'jacket'))
        elif kind == 'hood':
            keep = []
            for i in range(len(co)):
                d = co[i] - center
                fwd = -d.y / max(d.length, 1e-6)   # лицо MakeHuman смотрит в −Y Blender
                face = fwd > 0.12 and co[i].z < brow + 0.05   # вырез для лица — шире, капюшон не «шлем»
                keep.append(skin[i] and w_head[i] > 0.5 and co[i].z > neck - 0.04 and not face)
            ob = _shell(body, name, keep, g.get('off', 0.035), g.get('thick', 0.006), smooth=5, center=center, back=g.get('back', 0.05))
        elif kind == 'beanie':
            cut = brow - g.get('low', 0.0)
            keep = [skin[i] and w_headonly[i] > 0.5 and co[i].z > cut for i in range(len(co))]
            ob = _shell(body, name, keep, g.get('off', 0.014), g.get('thick', 0.006), smooth=4)
            band = [skin[i] and w_headonly[i] > 0.5 and cut < co[i].z < cut + 0.038 for i in range(len(co))]
            ob2 = _shell(body, name + '_cuff', band, g.get('off', 0.014) + 0.008, 0.006, smooth=4)
            ob2.data.materials.append(_material(name + '_cuff_m', g['tex'], g.get('tint'), g.get('tile', 6.0)))
        else:
            continue
        ob.data.materials.append(_material(name + '_m', g['tex'], g.get('tint'), g.get('tile', 6.0)))
        print('GARMENT', name, kind, len(ob.data.vertices))
    _hide_skin(body, hide)
    _hide_under(body, covers, w_torso, w_arms)


def _hide_under(body, covers, w_torso, w_arms):
    """у одежды MakeHuman (рубашка, футболка) удаляется всё, что под нашей курткой: выше подола, над туловищем (и руками,
    если у куртки рукава) — на груди и животе рубашка висит дальше от тела, чем куртка, и прорывалась бы сквозь неё"""
    if not covers:
        return
    from mathutils.kdtree import KDTree
    me = body.data
    kd = KDTree(len(me.vertices))
    for v in me.vertices:
        kd.insert(body.matrix_world @ v.co, v.index)
    kd.balance()
    for o in [o for o in bpy.data.objects if o.type == 'MESH' and o is not body and _otype(o) == 'Clothes']:
        under = set()
        for v in o.data.vertices:
            p = o.matrix_world @ v.co
            _, idx, dist = kd.find(p)
            for hem, sleeves in covers:
                if p.z > hem + 0.012 and (w_torso[idx] > 0.4 or (sleeves and w_arms[idx] > 0.4)):
                    under.add(v.index)
                    break
        if not under:
            continue
        bm = bmesh.new()
        bm.from_mesh(o.data)
        bm.verts.ensure_lookup_table()
        bmesh.ops.delete(bm, geom=[f for f in bm.faces if all(v.index in under for v in f.verts)], context='FACES_ONLY')
        bm.to_mesh(o.data)
        bm.free()
        print('UNDER', o.name, len(under))
