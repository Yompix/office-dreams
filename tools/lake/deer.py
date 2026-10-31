"""Олень для сцены 3 (WHAT THE LAKE KEEPS): модель из Figma Weave (Hunyuan 3D 2.1 по картинке — tools/lake/src/deer_hunyuan.glb,
картинка-образец — deer_ref.jpg) → скелет и веса в Blender → godot/lake/assets/models/deer.glb. Движения (стоит, дышит,
смотрит, идёт, лежит раненый) — в игре кодом (godot/lake/scripts/world/deer.gd), здесь только кости.
Ось: голова к −Y Blender (в Godot олень смотрит по +Z, как герои), копыта на земле, высота в холке ≈ 1 м.
Ноги находим сами: нижние вершины делятся на 4 группы (k-средних по x, y), у каждой — копыто, колено, верх.
Веса — по расстоянию до костей своей зоны: нога слушается только своих костей, рога и голова — головы.
  /home/user/tools/blender45/blender -b --factory-startup --python tools/lake/deer.py -- godot/lake/assets/models/deer.glb
"""
import bpy, bmesh, math, os, sys
import numpy as np
from mathutils import Matrix, Vector

OUT = sys.argv[sys.argv.index('--') + 1]
SRC = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'src/deer_hunyuan.glb')
SCALE = 0.8
bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.gltf(filepath=SRC)
o = [x for x in bpy.data.objects if x.type == 'MESH'][0]
for x in list(bpy.data.objects):
    if x is not o and x.type != 'MESH':
        bpy.data.objects.remove(x)
o.parent = None
# поворот (голова → −Y), масштаб, на землю — прямо в вершины
M = Matrix.Scale(SCALE, 4) @ Matrix.Rotation(math.radians(90), 4, 'Z') @ o.matrix_world
o.data.transform(M)
o.matrix_world = Matrix.Identity(4)
P = np.array([tuple(v.co) for v in o.data.vertices])
zmin = P[:, 2].min()
o.data.transform(Matrix.Translation((0, 0, -zmin)))
P[:, 2] -= zmin
o.name = o.data.name = 'deer'
H = P[:, 2].max()
y0, y1 = P[:, 1].min(), P[:, 1].max()
back = np.median([P[(P[:, 1] > a) & (P[:, 1] < a + 0.05), 2].max() for a in np.linspace(-0.2, 0.6, 9) * SCALE / 0.8])
print('DEER H %.2f length %.2f back %.2f' % (H, y1 - y0, back))

# ---- ноги: 4 группы k-средних по (x, y) среди вершин ниже 40 % высоты спины
low_i = np.where(P[:, 2] < 0.4 * back)[0]
L = P[low_i][:, :2]
rng = np.random.default_rng(1)
cy = (L[:, 1].min() + L[:, 1].max()) / 2
C = np.array([[L[:, 0].min() * 0.6, L[:, 1].min() + 0.05], [L[:, 0].max() * 0.6, L[:, 1].min() + 0.05],
              [L[:, 0].min() * 0.6, L[:, 1].max() - 0.05], [L[:, 0].max() * 0.6, L[:, 1].max() - 0.05]])
for _ in range(30):
    d = ((L[:, None, :] - C[None, :, :]) ** 2).sum(2)
    lab = d.argmin(1)
    for k in range(4):
        if (lab == k).any():
            C[k] = L[lab == k].mean(0)
legs = []
for k in range(4):
    g = P[low_i[lab == k]]
    hoof = g[g[:, 2] < g[:, 2].min() + 0.03].mean(0)
    mid = g[(g[:, 2] > 0.25 * back) & (g[:, 2] < 0.38 * back)]
    mid = mid.mean(0) if len(mid) else hoof + np.array([0, 0, 0.3 * back])
    legs.append((hoof, mid, C[k]))
# имена: перед/зад по y (голова к −y), лево/право по x (x < 0 — левая сторона оленя? олень смотрит в −y: его левая — +x)
order = sorted(range(4), key=lambda k: legs[k][0][1])
front, rear = order[:2], order[2:]
names = {}
for grp, pre in ((front, 'f'), (rear, 'b')):
    a, b = grp
    left, right = (a, b) if legs[a][0][0] > legs[b][0][0] else (b, a)
    names[left] = pre + 'l'
    names[right] = pre + 'r'
for k in range(4):
    print('LEG', names[k], 'hoof', legs[k][0].round(3), 'mid', legs[k][1].round(3))

# ---- кости
bpy.ops.object.armature_add(enter_editmode=True, location=(0, 0, 0))
arm = bpy.context.object
arm.name = arm.data.name = 'deer_rig'
eb = arm.data.edit_bones
eb.remove(eb[0])
def bone(name, h, t, parent=None, connect=False):
    b = eb.new(name)
    b.head = Vector(h)
    b.tail = Vector(t)
    b.roll = 0.0
    if parent:
        b.parent = eb[parent]
        b.use_connect = connect
    return b
cx = float(np.median(P[(P[:, 2] > 0.6 * back) & (P[:, 2] < back) & (P[:, 1] > y0 + 0.4), 0]))   # ось тела (модель сдвинута вбок)
fy = float(np.mean([legs[k][0][1] for k in front]))
by_ = float(np.mean([legs[k][0][1] for k in rear]))
zb = back * 0.8
bone('root', (cx, (fy + by_) / 2, 0), (cx, (fy + by_) / 2 - 0.2, 0))
bone('hips', (cx, by_ - 0.05, zb), (cx, (fy + by_) / 2, zb + 0.02), 'root')
bone('spine', (cx, (fy + by_) / 2, zb + 0.02), (cx, fy + 0.03, zb + 0.04), 'hips', True)
bone('chest', (cx, fy + 0.03, zb + 0.04), (cx, fy - 0.12, zb + 0.12), 'spine', True)
# шея и голова: голова — у переднего края, по медиане высот там
hp = P[(P[:, 1] < y0 + 0.28 * SCALE / 0.8)]
head_z = float(np.percentile(hp[:, 2], 45))
hx = float(np.median(hp[hp[:, 2] < head_z + 0.1, 0]))
nose = (hx, y0 + 0.02, head_z - 0.06)
hb = (hx, y0 + 0.3 * SCALE / 0.8, head_z + 0.06)
bone('neck', (0, fy - 0.12, zb + 0.12), ((fy - 0.12 + hb[1]) / 2 * 1.0, (fy - 0.12 + hb[1]) / 2, (zb + 0.12 + hb[2]) / 2 + 0.03), 'chest', True)
eb['neck'].head = Vector((cx, fy - 0.12, zb + 0.12))
eb['neck'].tail = Vector(((cx + hx) / 2, (fy - 0.12 + hb[1]) / 2, (zb + 0.12 + hb[2]) / 2 + 0.03))
bone('neck2', tuple(eb['neck'].tail), hb, 'neck', True)
bone('head', hb, nose, 'neck2', True)
bone('tail', (cx, y1 - 0.08, zb + 0.06), (cx, y1 + 0.02, zb - 0.08), 'hips')
for k in range(4):
    n = names[k]
    hoof, mid, c = legs[k]
    top = (c[0], c[1] + (0.04 if n[0] == 'b' else 0.0), zb - 0.05)
    knee = (mid[0], mid[1], mid[2] + 0.06)
    fet = (hoof[0], hoof[1] - 0.01, 0.1 * back / 1.0)
    bone(n + '_up', top, knee, 'chest' if n[0] == 'f' else 'hips')
    bone(n + '_low', knee, fet, n + '_up', True)
    bone(n + '_foot', fet, (hoof[0], hoof[1] - 0.04, 0.005), n + '_low', True)
bpy.ops.object.mode_set(mode='OBJECT')

# ---- веса: зона вершины → свои кости, вес по расстоянию до отрезка кости (1/d^4, две ближние)
bones = {b.name: (np.array(b.head_local), np.array(b.tail_local)) for b in arm.data.bones}
def segd(p, a, b):
    ab = b - a
    t = np.clip(((p - a) @ ab) / max(ab @ ab, 1e-9), 0, 1)
    return np.linalg.norm(p - (a + np.outer(t, ab)), axis=1)
body = ['hips', 'spine', 'chest', 'neck', 'neck2', 'head', 'tail']
D_body = np.stack([segd(P, *bones[b]) for b in body], 1)
leg_of = np.full(len(P), -1)
legC = np.array([legs[k][2] for k in range(4)])
dxy = ((P[:, None, :2] - legC[None, :, :]) ** 2).sum(2) ** 0.5
near = dxy.argmin(1)
legzone = (P[:, 2] < zb - 0.02) & (dxy.min(1) < 0.16)
leg_of[legzone] = near[legzone]
W = {}
for b in body + [names[k] + s for k in range(4) for s in ('_up', '_low', '_foot')]:
    W[b] = np.zeros(len(P))
inv = 1.0 / np.maximum(D_body, 0.01) ** 4
# голова и рога — жёстко головой
head_zone = (P[:, 1] < hb[1] + 0.02) & (P[:, 2] > hb[2] - 0.12)
for i in range(len(P)):
    if head_zone[i]:
        W['head'][i] = 1.0
        continue
    if leg_of[i] >= 0:
        n = names[leg_of[i]]
        cand = [n + '_up', n + '_low', n + '_foot']
        d = np.array([segd(P[i:i + 1], *bones[c])[0] for c in cand])
        if P[i, 2] > zb - 0.18:                      # верх ноги мягко переходит в корпус
            cand.append('chest' if n[0] == 'f' else 'hips')
            d = np.append(d, segd(P[i:i + 1], *bones[cand[-1]])[0])
        w = 1.0 / np.maximum(d, 0.01) ** 4
    else:
        cand = body
        w = inv[i].copy()
    top2 = np.argsort(-w)[:2]
    s = w[top2].sum()
    for j in top2:
        W[cand[j]][i] = w[j] / s
for b, w in W.items():
    vg = o.vertex_groups.new(name=b)
    idx = np.where(w > 0.001)[0]
    for i in idx:
        vg.add([int(i)], float(w[i]), 'REPLACE')
mod = o.modifiers.new('arm', 'ARMATURE')
mod.object = arm
o.parent = arm
bpy.context.view_layer.objects.active = o
bpy.ops.object.shade_smooth()
for x in bpy.context.selected_objects:
    x.select_set(False)
o.select_set(True)
arm.select_set(True)
os.makedirs(os.path.dirname(os.path.abspath(OUT)), exist_ok=True)
bpy.ops.export_scene.gltf(filepath=os.path.abspath(OUT), export_format='GLB', use_selection=True, export_apply=False,
                          export_skins=True, export_animations=False, export_yup=True)
print('DEER axis x %.3f head x %.3f' % (cx, hx))
print('DEER EXPORT', OUT, len(P), 'verts', len(arm.data.bones), 'bones')
