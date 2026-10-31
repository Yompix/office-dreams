"""Общие кирпичики для моделей WHAT THE LAKE KEEPS в Blender (машина — car.py, заправка — station.py):
материалы (цвет, шероховатость, металл, прозрачность, свечение, лак, ткань Poly Haven с оттенком), скруглённые бруски,
цилиндры, брусок между двумя точками, объединение, точка отсчёта, развёртка «коробкой».
Материалы скрипт кладёт в M: bl.M['paint'] = bl.mat(...), потом rbox(..., 'paint').
"""
import bpy, bmesh, math, os, sys
from mathutils import Vector, Matrix

HERE = os.path.dirname(os.path.abspath(__file__))
TEX = os.path.join(HERE, '../../godot/lake/assets/ph')
sys.path.insert(0, HERE)
from garments import _image   # картинка ткани с оттенком (копия пикселей)

M = {}


def mat(name, col, rough=0.5, metal=0.0, alpha=1.0, emit=None, cull=False, coat=0.0, tex=None, tint=None, tile=1.0, nor=True, arm=False):
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
        if arm and os.path.exists(base + '_arm_1k.jpg'):   # шероховатость из текстуры (бетон, металл)
            r = nt.nodes.new('ShaderNodeTexImage')
            r.image = bpy.data.images.load(base + '_arm_1k.jpg', check_existing=True)
            r.image.colorspace_settings.name = 'Non-Color'
            nt.links.new(mp.outputs['Vector'], r.inputs['Vector'])
            sep = nt.nodes.new('ShaderNodeSeparateColor')
            nt.links.new(r.outputs['Color'], sep.inputs['Color'])
            nt.links.new(sep.outputs['Green'], bs.inputs['Roughness'])
    return m


def obj(name, me):
    o = bpy.data.objects.new(name, me)
    bpy.context.scene.collection.objects.link(o)
    return o


def select_only(o):
    for x in bpy.context.selected_objects:
        x.select_set(False)
    o.select_set(True)
    bpy.context.view_layer.objects.active = o


def apply_mods(o):
    select_only(o)
    for md in list(o.modifiers):
        bpy.ops.object.modifier_apply(modifier=md.name)


def smooth(o, angle=40):
    select_only(o)
    bpy.ops.object.shade_smooth_by_angle(angle=math.radians(angle))


def box_uv(o, scale=1.0):
    """развёртка «коробкой»: по главной оси нормали грани (ткань, штукатурка, бетон, плитка)"""
    me = o.data
    if not me.uv_layers:
        me.uv_layers.new(name='UVMap')
    uv = me.uv_layers.active.data
    for p in me.polygons:
        n = o.matrix_world.to_3x3() @ p.normal
        ax = max(range(3), key=lambda i: abs(n[i]))
        for li in p.loop_indices:
            co = o.matrix_world @ me.vertices[me.loops[li].vertex_index].co
            u, v = [(co.y, co.z), (co.x, co.z), (co.x, co.y)][ax]
            uv[li].uv = (u * scale, v * scale)


def rbox(name, size, loc, m, bevel=0.02, seg=3, rot=(0, 0, 0), uv=0.0):
    """скруглённый брусок (литой пластик, подушки, плиты); size и loc — в осях Blender"""
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
    me.materials.append(M[m] if isinstance(m, str) else m)
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
    """брусок от точки к точке"""
    p0, p1 = Vector(p0), Vector(p1)
    d = p1 - p0
    o = rbox(name, (w, d.length, h), (0, 0, 0), m, bevel)
    y = d.normalized()
    x = y.cross(up).normalized()
    z = x.cross(y)
    o.matrix_world = Matrix.Translation((p0 + p1) * 0.5) @ Matrix((x, y, z)).transposed().to_4x4()
    return o


def cyl(name, r, depth, loc, m, axis='X', seg=32, bevel=0.0, r2=None, caps=True):
    bm = bmesh.new()
    bmesh.ops.create_cone(bm, cap_ends=caps, segments=seg, radius1=r, radius2=r if r2 is None else r2, depth=depth)
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    o = obj(name, me)
    if axis == 'X':
        o.rotation_euler = (0, math.radians(90), 0)
    elif axis == 'Y':
        o.rotation_euler = (math.radians(90), 0, 0)
    o.location = loc
    me.materials.append(M[m] if isinstance(m, str) else m)
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
    """точка отсчёта предмета (петля, центр колеса); поворот предмета сохраняется"""
    p = Vector(p)
    o.data.transform(Matrix.Translation(o.matrix_world.to_3x3().inverted() @ (o.location - p)))
    o.location = p


def apply_rot(o):
    select_only(o)
    bpy.ops.object.transform_apply(location=False, rotation=True, scale=True)


def text_mesh(name, body, size, m, extrude=0.02, font=None):
    """объёмная надпись (буквы вывески): кривая-текст → сетка; лежит в плоскости XY, лицом к +Z"""
    cu = bpy.data.curves.new(name, 'FONT')
    cu.body = body
    cu.size = size
    cu.extrude = extrude
    cu.align_x = 'CENTER'
    cu.align_y = 'CENTER'
    if font and os.path.exists(font):
        cu.font = bpy.data.fonts.load(font)
    o = obj(name, cu)
    select_only(o)
    bpy.ops.object.convert(target='MESH')
    o = bpy.context.view_layer.objects.active
    o.data.materials.clear()
    o.data.materials.append(M[m] if isinstance(m, str) else m)
    return o


def export(path):
    for x in bpy.context.selected_objects:
        x.select_set(False)
    for o in bpy.data.objects:
        if o.type == 'MESH':
            o.select_set(True)
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    bpy.ops.export_scene.gltf(filepath=os.path.abspath(path), export_format='GLB', use_selection=True, export_apply=True, export_yup=True)
    print('EXPORT', path, len([o for o in bpy.data.objects if o.type == 'MESH']), 'objects',
          sum(len(o.data.polygons) for o in bpy.data.objects if o.type == 'MESH'), 'faces')
