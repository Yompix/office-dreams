"""Герой из готовой 3D-фигуры (сгенерированной по картинке: Tripo / Meshy / Hunyuan 3D) → модель игры со скелетом,
   мимикой и губами. Фигура даёт внешность (тело, одежда, волосы, борода — как на картинке владельца), наш человек MakeHuman
   (tools/chars/gen.py, тот же рост и поза «Т») — кости, веса и лицевые формы:
     1) фигуру ставим по росту и центру на нашего человека (поза «Т» у обоих), подгоняем ICP по точкам;
     2) веса костей — с ближайшей точки нашего тела и одежды (перенос данных), модификатор Armature;
     3) голову фигуры заменяем нашей: голова MakeHuman (глаза, веки, рот, язык, зубы, 52 формы ARKit и визем) натягивается на
        поверхность головы фигуры (кроме глаз и рта), цвета головы фигуры переносятся в текстуру кожи; голова фигуры
        срезается по вороту;
     4) экспорт glb с теми же именами частей (skin, eyes, teeth, tongue, lashes; одежда фигуры — cloth_scan).
   blender -b ПЕРС.blend --python tools/chars/fromscan.py -- ФИГУРА.glb ВЫХОД.glb
   ПЕРС.blend — сборка gen.py (тот же персонаж, рядом с его .glb), до экспорта: кости, формы, одежда MakeHuman."""
import bpy, bmesh, sys, os, math
import numpy as np
from mathutils import Vector, Matrix
from mathutils.bvhtree import BVHTree
from mathutils.kdtree import KDTree

args = sys.argv[sys.argv.index('--') + 1:]
scan_path, out = args[0], args[1]
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import extras   # noqa: E402  (рисование в numpy, проекции, сглаживание)

arm = next(o for o in bpy.data.objects if o.type == 'ARMATURE')
ours = [o for o in bpy.data.objects if o.type == 'MESH']
skin = bpy.data.objects['skin']


def world_co(o):
    return extras._world(o)


# ---------- 1) фигура: импорт, рост, центр, ICP ----------
before = set(bpy.data.objects)
bpy.ops.import_scene.gltf(filepath=scan_path)
new = [o for o in bpy.data.objects if o not in before]
new_names = [o.name for o in new]
shapes = {pb.custom_shape for o in new if o.type == 'ARMATURE' for pb in o.pose.bones if pb.custom_shape}   # кружки костей импортёра glTF
scan_meshes = [o for o in new if o.type == 'MESH' and o not in shapes and not o.name.startswith('Icosphere')]
for o in new:   # снять родителей и анимации: нужна просто сетка в мире
    if o.type == 'MESH':
        mw = o.matrix_world.copy(); o.parent = None; o.matrix_world = mw
for o in scan_meshes:   # у сгенерированной фигуры форм нет; у подставной для проверки — снимаем
    if o.data.shape_keys:
        o.shape_key_clear()
    for m in list(o.modifiers):
        o.modifiers.remove(m)
    o.vertex_groups.clear()
with bpy.context.temp_override(selected_editable_objects=scan_meshes, selected_objects=scan_meshes, active_object=scan_meshes[0], object=scan_meshes[0]):
    bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
    if len(scan_meshes) > 1:
        bpy.ops.object.join()
scan = scan_meshes[0]; scan.name = 'cloth_scan'; scan.data.name = 'cloth_scan'
for nm in new_names:   # кости и пустышки фигуры не нужны
    o = bpy.data.objects.get(nm)
    if o is not None and o != scan:
        bpy.data.objects.remove(o, do_unlink=True)
S = world_co(scan); B = np.concatenate([world_co(o) for o in ours if o.name in ('skin',) or o.name.startswith('cloth_')])
# рост и центр: по рамкам (фигура генератора бывает в любой единице и с центром где угодно)
sh, bh = np.ptp(S[:, 2]), np.ptp(B[:, 2])
k = bh / sh; S = (S - [S[:, 0].mean(), S[:, 1].mean(), S[:, 2].min()]) * k + [B[:, 0].mean(), B[:, 1].mean(), B[:, 2].min()]
# лицом в −Y, как MakeHuman: если глубина фигуры спереди «тяжелее» сзади — ок; иначе разворот на 180°
def front_heavy(P):
    top = P[P[:, 2] > np.percentile(P[:, 2], 85)]   # голова: нос впереди
    return np.percentile(top[:, 1], 3) - top[:, 1].mean()
if front_heavy(S) > 0:
    c = S.mean(0); S = (S - c) * [-1, -1, 1] + c
# ICP: точечный (поворот вокруг Z и сдвиг; масштаб уже по росту), по 4000 точек
rng = np.random.default_rng(1)
Bs = B[rng.choice(len(B), min(6000, len(B)), replace=False)]; kd = KDTree(len(Bs))
for i, p in enumerate(Bs):
    kd.insert(p, i)
kd.balance()
for it in range(30):
    Ss = S[rng.choice(len(S), min(4000, len(S)), replace=False)]
    nn = np.array([kd.find(Vector(p))[0][:] for p in Ss])
    ms, mn = Ss.mean(0), nn.mean(0); a, b = Ss - ms, nn - mn
    H = a[:, :2].T @ b[:, :2]; U, _, Vt = np.linalg.svd(H); R2 = Vt.T @ U.T
    if np.linalg.det(R2) < 0:
        Vt[1] *= -1; R2 = Vt.T @ U.T
    R = np.eye(3); R[:2, :2] = R2
    sc = 1.0   # масштаб — по росту (выше); с подбором масштаба ICP «сжимает» фигуру к ближайшим точкам
    S = ((S - ms) @ R.T) * sc + mn
print('SCAN вершин', len(S), 'масштаб', round(k, 4), 'ICP средн. отклонение мм', round(float(np.linalg.norm(nn - (((Ss - ms) @ R.T) * sc + mn), axis=1).mean()) * 1000, 1))
mi = np.array(scan.matrix_world.inverted())
scan.data.vertices.foreach_set('co', (S @ mi[:3, :3].T + mi[:3, 3]).astype(np.float32).ravel()); scan.data.update()

# ---------- 2) веса костей с нашего тела и одежды ----------
src = [o for o in ours if o.name == 'skin' or o.name.startswith('cloth_')]
with bpy.context.temp_override(selected_editable_objects=src, selected_objects=src, active_object=src[0], object=src[0]):
    pass
proxy = None
cop = [o.copy() for o in src]
for c, o in zip(cop, src):
    c.data = o.data.copy(); bpy.context.scene.collection.objects.link(c)
    for m in list(c.modifiers):
        c.modifiers.remove(m)
    if c.data.shape_keys:
        c.shape_key_clear()
with bpy.context.temp_override(selected_editable_objects=cop, selected_objects=cop, active_object=cop[0], object=cop[0]):
    bpy.ops.object.join()
proxy = cop[0]; proxy.name = 'weights_src'
for g in proxy.vertex_groups:
    if g.name not in scan.vertex_groups:
        scan.vertex_groups.new(name=g.name)
dt = scan.modifiers.new('wt', 'DATA_TRANSFER'); dt.object = proxy; dt.use_vert_data = True; dt.data_types_verts = {'VGROUP_WEIGHTS'}
dt.vert_mapping = 'POLYINTERP_NEAREST'; dt.layers_vgroup_select_src = 'ALL'; dt.layers_vgroup_select_dst = 'NAME'
with bpy.context.temp_override(object=scan, active_object=scan, selected_objects=[scan], selected_editable_objects=[scan]):
    bpy.ops.object.modifier_apply(modifier='wt')
bpy.data.objects.remove(proxy, do_unlink=True)
scan.parent = arm; scan.matrix_parent_inverse = arm.matrix_world.inverted()
am = scan.modifiers.new('arm', 'ARMATURE'); am.object = arm
print('SCAN веса костей:', len(scan.vertex_groups), 'групп')

# ---------- 3) голова: наша (мимика, глаза, рот) по форме и цветам головы фигуры; голова фигуры срезается по вороту ----------
E = world_co(bpy.data.objects['eyes']); O = (E[E[:, 0] > 0].mean(0) + E[E[:, 0] < 0].mean(0)) / 2
neck_z = (arm.matrix_world @ arm.data.bones['neck_01'].head_local).z
cut_z = neck_z + float(os.environ.get('SCAN_CUT', '.02'))   # чуть выше основания шеи: ворот фигуры остаётся, шея и голова — наши
sc_tree = BVHTree.FromObject(scan, bpy.context.evaluated_depsgraph_get())
scan.data.calc_loop_triangles()
co = world_co(skin); M3 = np.array(skin.matrix_world, np.float32)[:3, :3]
n = extras._normals(skin) @ M3.T; n /= np.maximum(np.linalg.norm(n, axis=1, keepdims=True), 1e-9)
rel = co - O; x, z, d = np.abs(rel[:, 0]), rel[:, 2], rel[:, 1]
head = co[:, 2] > cut_z - .01
T = world_co(bpy.data.objects['teeth']); mz = T[:, 2].min() + .55 * np.ptp(T[:, 2]) - O[2]
face = extras._ss(.045, .03, x) * extras._ss(.025, .012, z) * extras._ss(mz - .022, mz - .01, z) * extras._ss(.015, -.005, d)   # глаза, нос, рот — наши
disp = np.zeros_like(co); hit_w = np.zeros(len(co), np.float32)
for i in np.nonzero(head)[0]:
    p = Vector(co[i]); nv = Vector(n[i]); best = None
    for sgn in (1, -1):
        h = sc_tree.ray_cast(p + nv * sgn * 1e-4, nv * sgn, .05)
        if h[0] is not None and (best is None or h[3] < best[1]):
            best = (h[0], h[3])
    if best is None:
        h = sc_tree.find_nearest(p, .05)
        if h[0] is not None:
            best = (h[0], h[3])
    if best is not None:
        disp[i] = np.array(best[0]) - co[i]; hit_w[i] = 1
w = hit_w * (1 - face) * extras._ss(cut_z - .01, cut_z + .03, co[:, 2])
ed = np.array([e.vertices[:] for e in skin.data.edges], np.int64); a, b = ed[:, 0], ed[:, 1]
deg = np.bincount(a, minlength=len(co)) + np.bincount(b, minlength=len(co))
tgt = disp * w[:, None]; dsp = tgt.copy(); pin = (~head) * 50 + face * 50
for _ in range(120):
    nb = np.zeros_like(dsp); np.add.at(nb, a, dsp[b]); np.add.at(nb, b, dsp[a]); nb /= np.maximum(deg, 1)[:, None]
    dsp = (w[:, None] * 3 * tgt + nb) / (w * 3 + pin + 1)[:, None]
off = dsp @ np.linalg.inv(M3).T
if skin.data.shape_keys:
    for kb in skin.data.shape_keys.key_blocks:
        q = np.empty(len(kb.data) * 3, np.float32); kb.data.foreach_get('co', q); kb.data.foreach_set('co', (q.reshape(-1, 3) + off).ravel())
skin.data.vertices.foreach_set('co', (extras._co(skin) + off).ravel()); skin.data.update()
print('SCAN голова натянута: вершин', int((w > .5).sum()), 'среднее смещение мм', round(float(np.linalg.norm(dsp[head], axis=1).mean() * 1000), 1))
# цвета головы и шеи фигуры → текстура кожи (ближайшая точка поверхности фигуры; у каждого треугольника — картинка его материала)
simg = next((nd.image for m in skin.data.materials if m and m.use_nodes for nd in m.node_tree.nodes if nd.type == 'TEX_IMAGE' and nd.image and 'diffuse' in nd.image.name.lower()), None)
mat_img = []
for m in scan.data.materials:
    im = next((nd.image for nd in (m.node_tree.nodes if m and m.use_nodes else []) if nd.type == 'TEX_IMAGE' and nd.image), None)
    mat_img.append(extras._img_get(im)[..., :3] if im else None)
if simg and any(t is not None for t in mat_img):
    from mathutils.geometry import barycentric_transform
    tree2, lts2 = extras._bvh(scan); suv = scan.data.uv_layers.active.data; Mw = scan.matrix_world
    co2 = world_co(skin); me = skin.data; me.calc_loop_triangles(); uvl = me.uv_layers.active
    luv = np.empty(len(me.loops) * 2, np.float32); uvl.data.foreach_get('uv', luv); luv = luv.reshape(-1, 2)
    hd = co2[:, 2] > neck_z - .045   # голова и шея до ворота
    lts = [t for t in me.loop_triangles if hd[list(t.vertices)].all()]
    tl = np.array([t.loops[:] for t in lts]); tv = np.array([t.vertices[:] for t in lts])
    W, H = simg.size; A, cov = extras._raster(luv[tl] * [W, H], co2[tv], W, H)
    col = np.zeros((H, W, 3), np.float32); got = np.zeros((H, W), bool)
    ys, xs = np.nonzero(cov)
    for y, x_ in zip(ys, xs):
        h = tree2.find_nearest(Vector(A[y, x_]), .03)
        if h[0] is None:
            continue
        lt = lts2[h[2]]; TA = mat_img[scan.data.polygons[lt.polygon_index].material_index] if mat_img else None
        if TA is None:
            continue
        ps = [Mw @ scan.data.vertices[i].co for i in lt.vertices]
        q = barycentric_transform(h[0], *ps, *[suv[li].uv.to_3d() for li in lt.loops])
        th, tw = TA.shape[:2]; u, v = (q.x % 1) * (tw - 1), (q.y % 1) * (th - 1)
        i0_, j0_ = int(v), int(u); fy, fx = v - i0_, u - j0_; i1_, j1_ = min(i0_ + 1, th - 1), min(j0_ + 1, tw - 1)
        col[y, x_] = TA[i0_, j0_] * (1 - fx) * (1 - fy) + TA[i0_, j1_] * fx * (1 - fy) + TA[i1_, j0_] * (1 - fx) * fy + TA[i1_, j1_] * fx * fy
        got[y, x_] = True
    # глаза: нарисованные глаза фигуры на наши веки не переносим (у нас свои глаза и веки) — там тон кожи вокруг
    Ev = world_co(bpy.data.objects['eyes']); ecs = [Ev[Ev[:, 0] > 0].mean(0), Ev[Ev[:, 0] < 0].mean(0)]
    eye = np.zeros(cov.shape, np.float32)
    for ec in ecs:
        eye = np.maximum(eye, extras._ss(.024, .016, np.linalg.norm(A - ec, axis=-1)))
    ring = cov & got & (eye < .05) & (np.linalg.norm(A - ecs[0], axis=-1) < .04)
    tone = col[ring].mean(0) if ring.any() else None
    m = extras._blur(got.astype(np.float32)[..., None], 3)[..., 0] * got * (1 - eye)
    a_ = extras._img_get(simg); base = a_[..., :3].copy()
    if tone is not None:   # веки — свой рисунок, но в тон кожи фигуры
        orig_tone = base[ring].mean(0); k = np.clip(tone / np.maximum(orig_tone, 1e-4), .5, 1.6)
        base = base * (1 - eye[..., None]) + np.clip(base * k, 0, 1) * eye[..., None]
    a_[..., :3] = base * (1 - m[..., None]) + col * m[..., None]
    # кисти — в тон рук фигуры у запястья (свои кисти MakeHuman остаются: пальцы для анимаций)
    wr = [np.array(arm.matrix_world @ arm.data.bones['hand_' + s_].head_local) for s_ in ('l', 'r')]
    fore = [h for h in (tree2.find_nearest(Vector(w_ + np.array([-.06 * np.sign(w_[0]), 0, .01])), .08) for w_ in wr) if h[0] is not None]
    if fore:
        fc = []
        for h in fore:
            lt = lts2[h[2]]; TA = mat_img[scan.data.polygons[lt.polygon_index].material_index]
            if TA is None:
                continue
            ps = [Mw @ scan.data.vertices[i].co for i in lt.vertices]; q = barycentric_transform(h[0], *ps, *[suv[li].uv.to_3d() for li in lt.loops])
            fc.append(TA[int((q.y % 1) * (TA.shape[0] - 1)), int((q.x % 1) * (TA.shape[1] - 1))])
        if fc:
            hands_t = [t for t in me.loop_triangles if (np.abs(co2[list(t.vertices), 0]) > min(abs(wr[0][0]), abs(wr[1][0])) - .03).all()]
            if hands_t:
                Ah, covh = extras._raster(luv[np.array([t.loops[:] for t in hands_t])] * [W, H], np.ones((len(hands_t), 3, 1), np.float32), W, H)
                k = np.clip(np.mean(fc, 0) / np.maximum(a_[covh, :3].mean(0), 1e-4), .5, 1.6)
                a_[covh, :3] = np.clip(a_[covh, :3] * k, 0, 1); print('SCAN кисти в тон рук фигуры ×', np.round(k, 3))
    extras._img_set(simg, a_)
    print('SCAN цвета головы и шеи перенесены в', simg.name, 'текселей', int(got.sum()))
# голову фигуры срезаем по вороту (выше — наша голова), кисти — по запястью (наши кисти: пальцы держат ключи и рацию)
wx = {s_: abs((arm.matrix_world @ arm.data.bones['hand_' + s_].head_local).x) for s_ in ('l', 'r')}
bm = bmesh.new(); bm.from_mesh(scan.data); Mw = scan.matrix_world
def scan_cut(f):
    ps = [Mw @ v.co for v in f.verts]
    return all(p.z > cut_z for p in ps) or all(abs(p.x) > wx['l' if p.x > 0 else 'r'] - .005 for p in ps)
bmesh.ops.delete(bm, geom=[f for f in bm.faces if scan_cut(f)], context='FACES'); bm.to_mesh(scan.data); bm.free()
# наша одежда и волосы MakeHuman больше не нужны: одежда — фигуры; кожа под ней — тоже (кроме головы, шеи и кистей рук)
for o in list(bpy.data.objects):
    if o.type == 'MESH' and (o.name.startswith('cloth_') and o != scan or o.name in ('hair', 'brows', 'beard', 'beard2')):
        bpy.data.objects.remove(o, do_unlink=True)
bm = bmesh.new(); bm.from_mesh(skin.data); co3 = world_co(skin)
hand = ((co3[:, 0] > wx['l'] - .03) | (co3[:, 0] < -(wx['r'] - .03)))   # кисти (поза «Т»: руки вдоль x)
keep = (co3[:, 2] > neck_z - .03) | hand
bm.verts.ensure_lookup_table()
bmesh.ops.delete(bm, geom=[f for f in bm.faces if not any(keep[v.index] for v in f.verts)], context='FACES'); bm.to_mesh(skin.data); bm.free()
print('SCAN кожа: оставлены голова, шея и кисти')
bpy.ops.export_scene.gltf(filepath=out, export_format='GLB', export_skins=True, export_morph=True, export_morph_normal=False,
                          export_animations=False, export_apply=False, export_image_format='WEBP', export_image_quality=85, export_yup=True)
print('DONE', out, os.path.getsize(out))
