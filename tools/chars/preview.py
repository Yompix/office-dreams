"""Лист модели героя в Blender (Cycles): рост спереди, сбоку, сзади (орто) и голова спереди, в три четверти, в профиль.
   Сравнивать с референсом владельца. Открываем готовую модель (.glb — в ней перекрашенные текстуры и волоски бороды;
   в .blend рядом картинки остаются исходными):
   blender -b --factory-startup --python tools/chars/preview.py -- ПАПКА [samples] МОДЕЛЬ.glb
   Кадры: ПАПКА/body_front.png, body_side.png, body_back.png, head_front.png, head_34.png, head_side.png"""
import bpy, sys, os, math
from mathutils import Vector

args = sys.argv[sys.argv.index('--') + 1:]
out = args[0]; spp = int(args[1]) if len(args) > 1 else 48
only_faces = '--only-faces' in args; args = [a for a in args if a != '--only-faces']
os.makedirs(out, exist_ok=True)
if len(args) > 2:
    for o in list(bpy.data.objects):
        bpy.data.objects.remove(o, do_unlink=True)
    bpy.ops.import_scene.gltf(filepath=args[2])
    for m in bpy.data.materials:   # срез альфы (волосы, ресницы, борода) — как в игре, без полупрозрачной каши
        if m.node_tree and any(n.type == 'TEX_IMAGE' for n in m.node_tree.nodes):
            m.blend_method = 'CLIP' if m.blend_method != 'OPAQUE' else 'OPAQUE'
sc = bpy.context.scene
sc.render.engine = 'CYCLES'; sc.cycles.device = 'CPU'; sc.cycles.samples = spp; sc.cycles.use_denoising = True
sc.cycles.max_bounces = 6; sc.cycles.transparent_max_bounces = 24   # слои бороды и волос — много прозрачных пересечений
sc.render.film_transparent = False; sc.view_settings.view_transform = 'AgX'; sc.view_settings.look = 'AgX - Medium High Contrast'
w = bpy.data.worlds.new('w') if not sc.world else sc.world; sc.world = w; w.use_nodes = True
bg = w.node_tree.nodes.get('Background'); bg.inputs[0].default_value = (.16, .17, .19, 1); bg.inputs[1].default_value = .35
meshes = [o for o in bpy.data.objects if o.type == 'MESH']
skin = bpy.data.objects.get('skin')
zs = [(o.matrix_world @ v.co).z for o in meshes for v in list(o.data.vertices)[::7]]
H = max(zs); eyes = bpy.data.objects.get('eyes')
ez = sum((eyes.matrix_world @ v.co).z for v in eyes.data.vertices) / len(eyes.data.vertices) if eyes else H - .1
ey = sum((eyes.matrix_world @ v.co).y for v in eyes.data.vertices) / len(eyes.data.vertices) if eyes else 0


def light(name, loc, energy, size, color=(1, 1, 1)):
    ld = bpy.data.lights.new(name, 'AREA'); ld.energy = energy; ld.size = size; ld.color = color
    ob = bpy.data.objects.new(name, ld); sc.collection.objects.link(ob); ob.location = loc
    d = Vector((0, 0, ez - .4)) - Vector(loc); ob.rotation_euler = d.to_track_quat('-Z', 'Y').to_euler()
    return ob


light('key', (-1.6, -2.2, ez + .8), 420, 1.6, (1, .97, .92))
light('fill', (1.9, -1.8, ez - .1), 140, 2.2, (.92, .96, 1))
light('rim', (.6, 2.4, ez + .9), 300, 1.2, (1, 1, 1))
cam_d = bpy.data.cameras.new('cam'); cam = bpy.data.objects.new('cam', cam_d); sc.collection.objects.link(cam); sc.camera = cam


def shot(name, loc, target, ortho=None, lens=85, res=(640, 640)):
    sc.render.resolution_x, sc.render.resolution_y = res
    cam.location = loc; cam.rotation_euler = (Vector(target) - Vector(loc)).to_track_quat('-Z', 'Y').to_euler()
    if ortho:
        cam_d.type = 'ORTHO'; cam_d.ortho_scale = ortho
    else:
        cam_d.type = 'PERSP'; cam_d.lens = lens
    sc.render.filepath = os.path.join(out, name + '.png'); bpy.ops.render.render(write_still=True); print('SHOT', sc.render.filepath)


mid = H / 2 + .02
if not only_faces:
  shot('body_front', (0, -6, mid), (0, 0, mid), ortho=H * 1.12, res=(520, 700))
  shot('body_side', (6, 0, mid), (0, 0, mid), ortho=H * 1.12, res=(520, 700))
  shot('body_back', (0, 6, mid), (0, 0, mid), ortho=H * 1.12, res=(520, 700))
hz = ez - .045
if not only_faces:
  shot('head_front', (0, ey - 1.15, hz), (0, ey, hz), lens=90)
  shot('head_34', (.78, ey - .85, hz + .02), (0, ey + .03, hz), lens=90)
  shot('head_side', (1.15, ey + .05, hz), (0, ey + .05, hz), lens=90)
# выражения (как ряд лиц на референсе): лицевые формы ARKit на всех частях головы — борода, брови, ресницы, зубы
FACES = {'neutral': {}, 'angry': {'browDownLeft': 1, 'browDownRight': 1, 'noseSneerLeft': .7, 'noseSneerRight': .7, 'mouthFrownLeft': .6,
         'mouthFrownRight': .6, 'mouthUpperUpLeft': .5, 'mouthUpperUpRight': .5, 'eyeSquintLeft': .5, 'eyeSquintRight': .5, 'jawOpen': .08},
         'surprised': {'browInnerUp': 1, 'browOuterUpLeft': 1, 'browOuterUpRight': 1, 'eyeWideLeft': 1, 'eyeWideRight': 1, 'jawOpen': .45, 'mouthFunnel': .25}}
if '--faces' in args or only_faces:
    for fname, kv in FACES.items():
        for o in bpy.data.objects:
            if o.type == 'MESH' and o.data.shape_keys:
                for kb in o.data.shape_keys.key_blocks[1:]:
                    kb.value = kv.get(kb.name, 0)
        shot('face_' + fname, (0, ey - 1.0, hz - .01), (0, ey, hz - .01), lens=100, res=(480, 480))
