"""Персонажи Night Shift из MakeHuman/MPFB2 (всё CC0) → glb с костями (имена как у UE/Quaternius) и лицевыми формами
   (52 ARKit face units + 15 визем). Запуск: blender -b --python tools/chars/gen.py -- <name> <out.glb>
   Описание персонажей — CHARS ниже. Нужен MPFB2 и пакеты: system assets, faceunits01, visemes02, skins01, hair01, shirts01, pants01, suits01, hats01."""
import bpy, sys, os, json, bmesh
from bl_ext.user_default.mpfb.services import HumanService, FaceService, ObjectService
name, out = sys.argv[sys.argv.index('--') + 1:][:2]
R = lambda a, c, af: {'asian': a, 'caucasian': c, 'african': af}
CHARS = {
  # Итан, 23 — главный герой, ночной охранник: пришёл с улицы в дождь — стёганая куртка (тёмная серо-зелёная), рубашка, джинсы, ботинки
  'ethan': dict(phenotype=dict(gender=1, age=.42, muscle=.55, weight=.45, proportions=.6, height=.61, cupsize=.5, firmness=.5, race=R(.05, .9, .05)),
                skin='young_caucasian_male/young_caucasian_male.mhmat', hair='short02/short02.mhclo', brows='eyebrow001/eyebrow001.mhclo',
                clothes=['male_casualsuit05/male_casualsuit05.mhclo', 'shoes03/shoes03.mhclo'], retint={'casualsuit05': (.16, .18, .15)}),
  # Фрэнк, 64 — прошлый ночной охранник, двадцать лет в здании
  'frank': dict(phenotype=dict(gender=1, age=.93, muscle=.4, weight=.62, proportions=.5, height=.5, cupsize=.5, firmness=.4, race=R(0, 1, 0)),
                skin='old_caucasian_male/old_caucasian_male.mhmat', hair='short04/short04.mhclo', brows='eyebrow007/eyebrow007.mhclo',
                clothes=['namuhekam_male_polo_shirt/namuhekam_male_polo_shirt.mhclo', 'toigo_wool_pants/toigo_wool_pants.mhclo', 'shoes04/shoes04.mhclo']),
  # Гас, 66 — ночной уборщик, комбинезон
  'gus': dict(phenotype=dict(gender=1, age=.95, muscle=.45, weight=.55, proportions=.45, height=.42, cupsize=.5, firmness=.4, race=R(0, .1, .9)),
              skin='old_african_male/old_african_male.mhmat', hair='short01/short01.mhclo', brows='eyebrow010/eyebrow010.mhclo',
              clothes=['male_worksuit01/male_worksuit01.mhclo', 'shoes02/shoes02.mhclo']),
  # дневной охранник (сменщик), 45: куртку надел заранее — тёмно-синяя стёганая куртка (кепка — в игре)
  'dayguard': dict(phenotype=dict(gender=1, age=.7, muscle=.5, weight=.7, proportions=.5, height=.48, cupsize=.5, firmness=.5, race=R(.1, .85, .05)),
                   skin='middleage_caucasian_male/middleage_caucasian_male.mhmat', hair='culturalibre_hair_02/culturalibre_hair_02.mhclo', brows='eyebrow003/eyebrow003.mhclo',
                   clothes=['male_casualsuit05/male_casualsuit05.mhclo', 'shoes04/shoes04.mhclo'], retint={'casualsuit05': (.07, .085, .13)}),
  # Лили, 20 — сестра Итана: жёлтая куртка (свитер, перекрашивается в игре), джинсы, кеды
  'lily': dict(phenotype=dict(gender=0, age=.38, muscle=.4, weight=.4, proportions=.65, height=.6, cupsize=.45, firmness=.6, race=R(.05, .9, .05)),
               skin='young_caucasian_female/young_caucasian_female.mhmat', hair='long01/long01.mhclo', brows='eyebrow011/eyebrow011.mhclo',
               clothes=['toigo_fisherman_sweater/toigo_fisherman_sweater.mhclo', 'cortu_cargo_pants/cortu_cargo_pants.mhclo', 'shoes06/shoes06.mhclo']),
  # Жилец: очень высокий и худой, без одежды, мокрые чёрные волосы (цвет кожи и глаза — в игре)
  'tenant': dict(phenotype=dict(gender=1, age=.75, muscle=0, weight=0, proportions=0, height=1, cupsize=0, firmness=0, race=R(0, 1, 0)),
                 skin='old_caucasian_male/old_caucasian_male.mhmat', hair='long01/long01.mhclo', brows='eyebrow012/eyebrow012.mhclo', clothes=[]),
}
# герои других игр (WHAT THE LAKE KEEPS — tools/lake/chars.json): тот же генератор, свой список
if os.environ.get('CHARS_JSON'):
    CHARS.update(json.load(open(os.environ['CHARS_JSON'], encoding='utf-8')))
C = CHARS[name]
for o in list(bpy.data.objects): bpy.data.objects.remove(o, do_unlink=True)
info = HumanService._create_default_human_info_dict()
info.update(name=name, phenotype=C['phenotype'], rig='game_engine', eyes='high-poly/high-poly.mhclo', eyebrows=C['brows'],
            eyelashes='eyelashes01/eyelashes01.mhclo', teeth='teeth_base/teeth_base.mhclo', tongue='tongue01/tongue01.mhclo',
            hair=C.get('hair', ''), clothes=C['clothes'], skin_mhmat=C['skin'], skin_material_type='MAKESKIN', eyes_material_type='MAKESKIN')
# формы лица и тела MakeHuman (своё лицо у героя, живот беременной) — в описании человека: MPFB ставит их до примерки волос
# и одежды (поставленные после — причёска садилась мимо изменённой головы)
info['targets'] = [{'target': k, 'value': v} for k, v in C.get('targets', {}).items()]
st = HumanService.get_default_deserialization_settings(); st.update(subdiv_levels=0, scale=.1)
body = HumanService.deserialize_from_dict(info, st)
FaceService.load_targets(body, load_microsoft_visemes=False, load_meta_visemes=True, load_arkit_faceunits=True)
FaceService.interpolate_targets(body)
import numpy as np
from bl_ext.user_default.mpfb.services import faceservice as FS
# лицевые формы, которые игра использует (остальные ARKit-формы не нужны и только раздувают файл)
FACE = set(FS.META_VISEMES) | {'eyeBlinkLeft', 'eyeBlinkRight', 'eyeLookDownLeft', 'eyeLookDownRight', 'eyeLookInLeft', 'eyeLookInRight',
  'eyeLookOutLeft', 'eyeLookOutRight', 'eyeLookUpLeft', 'eyeLookUpRight', 'eyeSquintLeft', 'eyeSquintRight', 'eyeWideLeft', 'eyeWideRight',
  'browDownLeft', 'browDownRight', 'browInnerUp', 'browOuterUpLeft', 'browOuterUpRight', 'cheekSquintLeft', 'cheekSquintRight',
  'noseSneerLeft', 'noseSneerRight', 'jawOpen', 'mouthClose', 'mouthFunnel', 'mouthPucker', 'mouthSmileLeft', 'mouthSmileRight',
  'mouthFrownLeft', 'mouthFrownRight', 'mouthStretchLeft', 'mouthStretchRight', 'mouthUpperUpLeft', 'mouthUpperUpRight',
  'mouthLowerDownLeft', 'mouthLowerDownRight', 'mouthPressLeft', 'mouthPressRight'}
def co(kb): a = np.empty(len(kb.data) * 3, np.float32); kb.data.foreach_get('co', a); return a
for o in [o for o in bpy.data.objects if o.type == 'MESH']:
    me = o.data
    # 1) запекаем формы телосложения в базу, оставляем только лицевые
    if me.shape_keys:
        kbs = me.shape_keys.key_blocks; base = co(kbs[0]); add = np.zeros_like(base)
        for kb in kbs[1:]:
            if kb.name not in FACE and kb.value: add += kb.value * (co(kb) - base)
        for kb in kbs: kb.data.foreach_set('co', co(kb) + add)
        me.vertices.foreach_set('co', co(kbs[0]))
        for kb in list(kbs[1:]):
            if kb.name not in FACE: o.shape_key_remove(kb)
        for kb in me.shape_keys.key_blocks: kb.value = 0
        # формы, которые этот меш почти не двигают, выкидываем (меньше файл)
        b0 = co(me.shape_keys.key_blocks[0])
        for kb in list(me.shape_keys.key_blocks[1:]):
            if np.abs(co(kb) - b0).max() < 1e-4: o.shape_key_remove(kb)
        if len(me.shape_keys.key_blocks) == 1: o.shape_key_remove(me.shape_keys.key_blocks[0])
# 1б) своя одежда (WHAT THE LAKE KEEPS): куртки, худи с капюшоном, жилет, шапка — оболочкой по телу (tools/lake/garments.py).
#     Шьётся до масок: тело под одеждой MakeHuman ещё целое (иначе у куртки нет туловища)
if C.get('garments'):
    sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '../lake'))
    import garments
    garments.build(C['garments'])
for o in [o for o in bpy.data.objects if o.type == 'MESH']:
    me = o.data
    # 2) маски (вспомогательная геометрия MakeHuman и тело под одеждой) превращаем в настоящее удаление вершин
    kill = set()
    for m in [m for m in o.modifiers if m.type == 'MASK']:
        g = o.vertex_groups.get(m.vertex_group)
        ing = set(v.index for v in me.vertices if g and any(x.group == g.index and x.weight > 0 for x in v.groups))
        kill |= ing if m.invert_vertex_group else set(range(len(me.vertices))) - ing
        o.modifiers.remove(m)
    if kill:
        bm = bmesh.new(); bm.from_mesh(me); bm.verts.ensure_lookup_table()
        bmesh.ops.delete(bm, geom=[bm.verts[i] for i in kill], context='VERTS'); bm.to_mesh(me); bm.free()
    for m in [m for m in o.modifiers if m.type not in ('ARMATURE',)]: o.modifiers.remove(m)
    print('MESH', o.name, len(me.vertices), [k.name for k in me.shape_keys.key_blocks][:3] if me.shape_keys else 0, len(me.shape_keys.key_blocks) if me.shape_keys else 0)
# 3) поза покоя как у анимаций игры (модель Quaternius, «Т»): поворачиваем кости MPFB вдоль костей Quaternius,
#    запекаем позу в сетку (вместе с лицевыми формами) и делаем её позой покоя — тогда клипы Quaternius встают без перекосов
import struct, mathutils
QG = os.path.join(os.path.dirname(os.path.abspath(__file__)), '../../public/games/nightshift/models/male.glb')
d = open(QG, 'rb').read(); l = struct.unpack('<I', d[12:16])[0]; J = json.loads(d[20:20 + l]); N = J['nodes']
par = {c: i for i, n in enumerate(N) for c in n.get('children', [])}
def gm(i):
    n = N[i]; m = mathutils.Quaternion([n.get('rotation', [0, 0, 0, 1])[k] for k in (3, 0, 1, 2)]).to_matrix().to_4x4()
    m.translation = n.get('translation', [0, 0, 0]); return (gm(par[i]) if i in par else mathutils.Matrix()) @ m
QP = {N[i]['name'].replace('Head', 'head'): gm(i).translation for i in range(len(N)) if 'mesh' not in N[i]}
QP = {k: mathutils.Vector((v.x, -v.z, v.y)) for k, v in QP.items()}   # glTF (y вверх) → Blender (z вверх)
arm = [o for o in bpy.data.objects if o.type == 'ARMATURE'][0]
bpy.context.view_layer.objects.active = arm; bpy.ops.object.mode_set(mode='POSE')
NEXT = {'hand_l': 'middle_01_l', 'hand_r': 'middle_01_r', 'foot_l': 'ball_l', 'foot_r': 'ball_r', 'spine_03': 'neck_01', 'neck_01': 'head', 'clavicle_l': 'upperarm_l', 'clavicle_r': 'upperarm_r'}
def walk(b):
    yield b
    for c in b.children: yield from walk(c)
for pb in walk(arm.pose.bones[0]):
    nx = NEXT.get(pb.name) or next((c.name for c in pb.children if c.name in QP), None)
    if pb.name in ('Root', 'pelvis') or pb.name not in QP or not nx or nx not in QP or nx not in arm.pose.bones: continue
    bpy.context.view_layer.update()
    h = arm.matrix_world @ pb.head; cur = (arm.matrix_world @ arm.pose.bones[nx].head) - h; want = QP[nx] - QP[pb.name]
    if cur.length < 1e-5 or want.length < 1e-5: continue
    R = cur.normalized().rotation_difference(want.normalized()).to_matrix().to_4x4()
    pb.matrix = mathutils.Matrix.Translation(pb.head) @ R @ mathutils.Matrix.Translation(-pb.head) @ pb.matrix
bpy.context.view_layer.update(); bpy.ops.object.mode_set(mode='OBJECT')
dg = bpy.context.evaluated_depsgraph_get()
def evco(o):
    dg.update(); e = o.evaluated_get(dg); a = np.empty(len(e.data.vertices) * 3, np.float32); e.data.vertices.foreach_get('co', a); return a
for o in [o for o in bpy.data.objects if o.type == 'MESH']:
    kbs = o.data.shape_keys.key_blocks if o.data.shape_keys else []
    for kb in kbs: kb.value = 0
    base = evco(o); sk = {}
    for kb in list(kbs)[1:]: kb.value = 1; sk[kb.name] = evco(o); kb.value = 0
    o.data.vertices.foreach_set('co', base)
    if kbs: kbs[0].data.foreach_set('co', base)
    for kb in list(kbs)[1:]: kb.data.foreach_set('co', sk[kb.name])
    o.data.update()
bpy.context.view_layer.objects.active = arm; bpy.ops.object.mode_set(mode='POSE'); bpy.ops.pose.armature_apply(selected=False); bpy.ops.object.mode_set(mode='OBJECT')

# 4) прозрачность только там, где она нужна (брови, ресницы, волосы — по альфа-порогу), иначе глаза и зубы «пропадают» при сортировке
for o in [o for o in bpy.data.objects if o.type == 'MESH']:
    cut = any(k in o.name for k in ('eyebrow', 'eyelash')) or o.name.split('.', 1)[-1] == C.get('hair', '-').split('/')[0]
    for sl in o.material_slots:
        if sl.material: sl.material.blend_method = 'CLIP' if cut else 'OPAQUE'; sl.material.alpha_threshold = .4
# имена по типу (игра узнаёт части тела по ним): skin, eyes, brows, lashes, teeth, tongue, hair, cloth_<вещь>
KIND = {'Basemesh': 'skin', 'Eyes': 'eyes', 'Eyebrows': 'brows', 'Eyelashes': 'lashes', 'Teeth': 'teeth', 'Tongue': 'tongue', 'Hair': 'hair'}
for o in [o for o in bpy.data.objects if o.type == 'MESH']:
    t = ObjectService.get_object_type(o); k = KIND.get(t) or 'cloth_' + o.name.split('.', 1)[-1]
    o.name = k; o.data.name = k
    print('PART', t, '->', k)
# 4б) перекраска одежды: красятся только малонасыщенные пиксели (ткань куртки), джинсы и полоски рубашки остаются своими
for key, rgb in C.get('retint', {}).items():
    for img in bpy.data.images:
        if key not in img.name or 'diffuse' not in img.name.lower() or not img.size[0]: continue
        a = np.empty(len(img.pixels), np.float32); img.pixels.foreach_get(a); a = a.reshape(-1, 4); c = a[:, :3]
        mx, mn = c.max(1), c.min(1); sat = (mx - mn) / np.maximum(mx, 1e-4); lum = c @ np.array([.3, .59, .11], np.float32)
        m = np.clip((.22 - sat) / .1, 0, 1) * np.clip((mx - .05) / .05, 0, 1)
        tgt = np.array(rgb, np.float32); tl = tgt @ np.array([.3, .59, .11], np.float32); base = float(np.median(lum[m > .9])) if (m > .9).any() else .3
        new = lum[:, None] / base * tgt[None, :]
        c[:] = c * (1 - m[:, None]) + np.clip(new, 0, 1) * m[:, None]; img.pixels.foreach_set(a.ravel()); img.update()
        print('RETINT', img.name, rgb, 'base lum', round(base, 3), 'share', round(float(m.mean()), 3))
# 5) текстуры поменьше: кожа 2048, остальное 1024
for img in bpy.data.images:
    if not img.size[0]: continue
    lim = 2048 if 'skin' in img.filepath.lower() or 'diffuse' in img.name.lower() and 'male' in img.name.lower() else 1024
    if max(img.size) > lim: img.scale(lim, lim); print('IMG', img.name, lim)
os.makedirs(os.path.dirname(out), exist_ok=True)
bpy.ops.wm.save_as_mainfile(filepath=out.replace('.glb', '.blend'))
bpy.ops.export_scene.gltf(filepath=out, export_format='GLB', export_skins=True, export_morph=True, export_morph_normal=False,
                          export_animations=False, export_apply=False, export_image_format='WEBP', export_image_quality=82, export_yup=True)
print('DONE', out, os.path.getsize(out))
