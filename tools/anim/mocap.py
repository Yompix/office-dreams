"""Мокап → клипы под скелет models/male.glb → public/games/nightshift/models/anims2.glb (повороты int16, прореженные ключи).

  python3 -I tools/anim/mocap.py ПАПКА_С_FBX tools/anim/mocap_spec.json public/games/nightshift/models/anims2.glb

Источники (скачать в одну папку; сырые FBX в репозиторий и на сайт НЕ класть — в игру идут только перенесённые клипы):
  Rokoko, бесплатные наборы с сайта (мокап всего тела с пальцами; «можно использовать в любом проекте, включая коммерческий»):
    https://media.rokoko.com/EVERYDAY-IDLES-MOCAP.zip        — Idle_* (разговор, болтовня, спор, оглядывается, смотрит)
    https://media.rokoko.com/WALK-RUN-CYCLES-MOCAP.zip       — ходьба и бег
    TV-набор (Google Drive, *_ue.fbx): SittingTalking_Gossip, TalkingHead_Intv, OnCellPhone, SitDown_UseLaptop
    263-набор (Google Drive, по одному файлу через Range): Sitting_Idle01/02_HUMANIK_769
  Тексты лицензий и ссылки — public/games/nightshift/licenses/Rokoko-free-mocap.txt, CREDITS.txt.
Перенос: мировой поворот кости цели = поворот источника × (покой источника)⁻¹ × (выравнивание направления кости)⁻¹ × покой цели;
скелеты Mixamo, UE-манекен и HumanIK приводятся к именам Mixamo (canon). Медленный разворот корпуса убирается (stab),
смещение таза — в пределах клипа (inplace), конец петли плавно переходит в начало (loop)."""
import sys, os, json, numpy as np
HERE = os.path.dirname(os.path.abspath(__file__)); sys.path.insert(0, HERE)
from gltfio import Skel
from fbxanim import FbxSkel, qmul, qinv, qrot, qnorm, mat_to_q
MALE = os.path.join(os.path.dirname(os.path.dirname(HERE)), 'public/games/nightshift/models/male.glb')

MAP_MIXAMO = {'Hips': 'pelvis', 'Spine': 'spine_01', 'Spine1': 'spine_02', 'Spine2': 'spine_03', 'Neck': 'neck_01', 'Head': 'Head'}
for S, s in (('Left', 'l'), ('Right', 'r')):
    MAP_MIXAMO.update({f'{S}Shoulder': f'clavicle_{s}', f'{S}Arm': f'upperarm_{s}', f'{S}ForeArm': f'lowerarm_{s}', f'{S}Hand': f'hand_{s}',
                       f'{S}UpLeg': f'thigh_{s}', f'{S}Leg': f'calf_{s}', f'{S}Foot': f'foot_{s}', f'{S}ToeBase': f'ball_{s}'})
    for F, f in (('Thumb', 'thumb'), ('Index', 'index'), ('Middle', 'middle'), ('Ring', 'ring'), ('Pinky', 'pinky')):
        for j in (1, 2, 3): MAP_MIXAMO[f'{S}Hand{F}{j}'] = f'{f}_0{j}_{s}'
# к какой кости смотрит кость (направление в покое) — у цели
TCHILD = {'pelvis': 'spine_01', 'spine_01': 'spine_02', 'spine_02': 'spine_03', 'spine_03': 'neck_01', 'neck_01': 'Head'}
for s in 'lr':
    TCHILD.update({f'clavicle_{s}': f'upperarm_{s}', f'upperarm_{s}': f'lowerarm_{s}', f'lowerarm_{s}': f'hand_{s}', f'hand_{s}': f'middle_01_{s}',
                   f'thigh_{s}': f'calf_{s}', f'calf_{s}': f'foot_{s}', f'foot_{s}': f'ball_{s}', f'ball_{s}': f'ball_leaf_{s}'})
    for f in ('thumb', 'index', 'middle', 'ring', 'pinky'):
        TCHILD[f'{f}_01_{s}'] = f'{f}_02_{s}'; TCHILD[f'{f}_02_{s}'] = f'{f}_03_{s}'; TCHILD[f'{f}_03_{s}'] = f'{f}_04_leaf_{s}'

# другие скелеты → имена Mixamo: UE-манекен (Rokoko *_ue.fbx) и HumanIK (Character1_…)
UE2MX = {'pelvis': 'Hips', 'spine_01': 'Spine', 'spine_02': 'Spine1', 'spine_03': 'Spine2', 'neck_01': 'Neck', 'head': 'Head'}
for S, s_ in (('Left', 'l'), ('Right', 'r')):
    UE2MX.update({f'clavicle_{s_}': f'{S}Shoulder', f'upperarm_{s_}': f'{S}Arm', f'lowerarm_{s_}': f'{S}ForeArm', f'hand_{s_}': f'{S}Hand',
                  f'thigh_{s_}': f'{S}UpLeg', f'calf_{s_}': f'{S}Leg', f'foot_{s_}': f'{S}Foot', f'ball_{s_}': f'{S}ToeBase'})
    for F, f in (('Thumb', 'thumb'), ('Index', 'index'), ('Middle', 'middle'), ('Ring', 'ring'), ('Pinky', 'pinky')):
        for j in (1, 2, 3): UE2MX[f'{f}_0{j}_{s_}'] = f'{S}Hand{F}{j}'
def canon(src):
    for i, n in list(src.name.items()):
        n2 = n[len('Character1_'):] if n.startswith('Character1_') else n
        src.name[i] = UE2MX.get(n2, n2)
    return src

def rot_between(a, b):
    a = a / np.linalg.norm(a); b = b / np.linalg.norm(b); c = np.cross(a, b); d = float(np.dot(a, b))
    if d < -0.999999:
        ax = np.cross(a, [1, 0, 0]);
        if np.linalg.norm(ax) < 1e-6: ax = np.cross(a, [0, 1, 0])
        ax /= np.linalg.norm(ax); return np.array([ax[0], ax[1], ax[2], 0.0])
    q = np.array([c[0], c[1], c[2], 1 + d]); return q / np.linalg.norm(q)

HIPL, HIPR = 'LeftUpLeg', 'RightUpLeg'
def retarget(src, t0=None, t1=None, fps=30, loop=False, inplace=True, stab=0, pelvis=None):
    tgt = Skel(MALE); WTt, WRt, _ = tgt.world()
    T, R = src.local(); WTs, WRs = src.world(T, R)
    names = [src.name[i] for i in src.order]; sidx = {n: k for k, n in enumerate(names)}
    # покой источника: мировые повороты/позиции из BindPose (если есть) — иначе кадр 0
    bindR = np.array([mat_to_q(src.bind[i]) if i in src.bind else WRs[0, k] for k, i in enumerate(src.order)])
    bindT = np.array([(src.bind[i][:3, 3] * src.unit) if i in src.bind else WTs[0, k] for k, i in enumerate(src.order)])
    # отрезок клипа
    ts = src.ts; a = 0 if t0 is None else int(np.searchsorted(ts, t0)); b = len(ts) if t1 is None else int(np.searchsorted(ts, t1))
    WRs, WTs = WRs[a:b], WTs[a:b]; F = len(WRs); ts2 = np.arange(F) / src.fps
    if stab:   # медленный разворот корпуса убираем (персонаж смотрит туда, куда его поставил кадр), быстрые повороты — оставляем
        il, ir = sidx[HIPL], sidx[HIPR]; v = WTs[:, il] - WTs[:, ir]; yaw = np.unwrap(np.arctan2(-v[:, 2], v[:, 0]))
        n = max(1, int(stab * src.fps)); pad = np.pad(yaw, (n, n), mode='edge'); ker = np.ones(2 * n + 1) / (2 * n + 1); ys = np.convolve(pad, ker, mode='valid')
        qy = np.zeros((F, 4)); qy[:, 1] = np.sin(-ys / 2); qy[:, 3] = np.cos(-ys / 2)
        WRs = qmul(np.repeat(qy[:, None], WRs.shape[1], 1), WRs)
        c = WTs[:, sidx['Hips']].mean(0); c[1] = 0
        WTs = qrot(np.repeat(qy[:, None], WTs.shape[1], 1), WTs - c) + c
    # обратная проверка: дети источника для направлений
    schild = {}
    for k, i in enumerate(src.order):
        p = src.parent[i]
        if p is not None: schild.setdefault(src.name[p], []).append(src.name[i])
    def sdir(n):
        kids = schild.get(n, [])
        pref = [c for c in kids if c in MAP_MIXAMO and MAP_MIXAMO.get(c, '').startswith(('spine', 'neck', 'Head', 'upperarm', 'lowerarm', 'hand', 'calf', 'foot', 'ball', 'middle_01'))]
        c = (pref or [k for k in kids if k in MAP_MIXAMO] or [None])[0]
        if c is None:   # кончик пальца/носок: продолжаем направление от родителя
            p = src.parent[src.order[sidx[n]]]; return bindT[sidx[n]] - bindT[sidx[src.name[p]]]
        return bindT[sidx[c]] - bindT[sidx[n]]
    out = {}
    WRo = np.repeat(WRt[None], F, 0)
    for sn, tn in MAP_MIXAMO.items():
        if sn not in sidx or tn not in tgt.idx: continue
        k, j = sidx[sn], tgt.idx[tn]
        tc = TCHILD.get(tn)
        A = rot_between(sdir(sn), (WTt[tgt.idx[tc]] - WTt[j]) if tc in tgt.idx else sdir(sn)) if tn != 'Head' else np.array([0, 0, 0, 1.0])
        WRo[:, j] = qnorm(qmul(qmul(qmul(WRs[:, k], qinv(bindR[k])[None]), qinv(A)[None]), WRt[j][None]))
    # локальные
    tracks = {}
    bones = [n for n in tgt.name if n not in ('Eyebrows', 'Eyes', 'SuperHero_Male', 'Armature', 'root') and not n.endswith(('_leaf_l', '_leaf_r'))]
    for n in bones:
        j = tgt.idx[n]; p = tgt.parent[j]
        loc = qnorm(qmul(qinv(WRo[:, p]), WRo[:, j]))
        for f in range(1, F):
            if (loc[f] * loc[f - 1]).sum() < 0: loc[f] *= -1
        tracks[j] = {'rotation': loc}
    # таз: позиция источника → масштаб по высоте таза, в осях родителя таза (root повёрнут на −90° по X)
    kh = sidx['Hips']; P = WTs[:, kh].copy(); sc = WTt[tgt.idx['pelvis'], 1] / bindT[kh, 1]
    if inplace:
        lin = np.linspace(0, 1, F)[:, None]
        for ax in (0, 2): P[:, ax] -= P[0, ax] + (P[-1, ax] - P[0, ax]) * lin[:, 0] if loop else P[0, ax]
        P[:, [0, 2]] -= P[:, [0, 2]].mean(0)
    P = P * sc; P[:, 0] += WTt[tgt.idx['pelvis'], 0]; P[:, 2] += WTt[tgt.idx['pelvis'], 2]
    if pelvis is not None: P += np.asarray(pelvis, float) - P.mean(0)   # сидя: таз туда же, где у Sitting_Idle_Loop (стул в сценах стоит под ним)
    jr = tgt.idx['root']; Pl = qrot(qinv(WRt[jr])[None], P - WTt[jr][None])
    tracks[tgt.idx['pelvis']]['translation'] = Pl
    if loop: loopify(tracks, F, src.fps)
    return tgt, set(bones), ts2, tracks

def loopify(tracks, F, fps, sec=.6):
    """последние sec секунд плавно переходят в первый кадр — петля без скачка"""
    n = min(F // 3, int(sec * fps))
    w = (np.linspace(0, 1, n) ** 2 * (3 - 2 * np.linspace(0, 1, n)))[:, None]
    for tr in tracks.values():
        if 'rotation' in tr:
            q = tr['rotation']; q0 = q[0].copy()
            seg = q[F - n:].copy(); s = np.sign((seg * q0).sum(1, keepdims=True)); s[s == 0] = 1
            tr['rotation'][F - n:] = qnorm(seg * (1 - w) + (q0 * s) * w)
        if 'translation' in tr:
            v = tr['translation']; v[F - n:] = v[F - n:] * (1 - w) + v[0] * w

def reduce_keys(ts, v, tol):
    """жадное прореживание: оставляем ключ, если линейная интерполяция соседей ошибается > tol"""
    keep = [0]; i = 0; F = len(ts)
    while i < F - 1:
        j = i + 2
        while j < F:
            seg = v[i:j + 1]; f = ((ts[i:j + 1] - ts[i]) / (ts[j] - ts[i]))[:, None]
            if np.abs(seg - (v[i] * (1 - f) + v[j] * f)).max() > tol: break
            j += 1
        i = j - 1; keep.append(i)
    return np.array(sorted(set(keep)))

def save(path, tgt, keep, clips, tolR=.0025, tolT=.002):
    import struct
    order = [i for i in tgt.order if tgt.name[i] in keep or tgt.name[i] in ('Armature', 'root')]
    new = {o: n for n, o in enumerate(order)}
    nodes = []
    for o in order:
        nd = {'name': tgt.name[o]}; ch = [new[c] for c in tgt.nodes[o].get('children', []) if c in new]
        if ch: nd['children'] = ch
        if np.abs(tgt.T[o]).sum() > 0: nd['translation'] = [float(x) for x in tgt.T[o]]
        if np.abs(tgt.R[o] - [0, 0, 0, 1]).sum() > 1e-9: nd['rotation'] = [float(x) for x in tgt.R[o]]
        nodes.append(nd)
    roots = [new[o] for o in order if tgt.parent[o] not in new]
    buf = bytearray(); views = []; accs = []
    def add(arr, typ, ctype=5126, norm=False, mm=False):
        while len(buf) % 4: buf.append(0)
        views.append({'buffer': 0, 'byteOffset': len(buf), 'byteLength': arr.nbytes}); buf.extend(arr.tobytes())
        a = {'bufferView': len(views) - 1, 'componentType': ctype, 'count': int(arr.shape[0]), 'type': typ}
        if norm: a['normalized'] = True
        if mm: a['min'] = [float(arr.min())]; a['max'] = [float(arr.max())]
        accs.append(a); return len(accs) - 1
    anims = []; tot = 0
    for name, (ts, tracks) in clips.items():
        samplers, channels = [], []; tcache = {}
        for nd, paths in tracks.items():
            if nd not in new: continue
            for p, v in paths.items():
                kk = reduce_keys(ts, v, tolR if p == 'rotation' else tolT)
                if len(kk) <= 2 and np.abs(v - v[0]).max() < (tolR if p == 'rotation' else tolT): kk = np.array([0])   # не меняется — один ключ
                key = tuple(kk.tolist())
                if key not in tcache: tcache[key] = add(np.ascontiguousarray(ts[kk, None], np.float32), 'SCALAR', mm=True)
                if p == 'rotation': out = add(np.ascontiguousarray(np.round(np.clip(v[kk], -1, 1) * 32767), np.int16), 'VEC4', 5122, True)
                else: out = add(np.ascontiguousarray(v[kk], np.float32), 'VEC3')
                samplers.append({'input': tcache[key], 'output': out, 'interpolation': 'LINEAR'}); channels.append({'sampler': len(samplers) - 1, 'target': {'node': new[nd], 'path': p}})
                tot += len(kk)
        anims.append({'name': name, 'samplers': samplers, 'channels': channels})
    while len(buf) % 4: buf.append(0)
    j = {'asset': {'version': '2.0', 'generator': 'office-dreams tools/anim/mocap'}, 'scene': 0, 'scenes': [{'nodes': roots}], 'nodes': nodes,
         'animations': anims, 'accessors': accs, 'bufferViews': views, 'buffers': [{'byteLength': len(buf)}]}
    js = json.dumps(j, separators=(',', ':')).encode()
    while len(js) % 4: js += b' '
    out = struct.pack('<III', 0x46546C67, 2, 12 + 8 + len(js) + 8 + len(buf)) + struct.pack('<II', len(js), 0x4E4F534A) + js + struct.pack('<II', len(buf), 0x004E4942) + bytes(buf)
    open(path, 'wb').write(out); return len(out), tot

if __name__ == '__main__':
    srcdir, spec, outp = sys.argv[1], json.load(open(sys.argv[2])), sys.argv[3]; clips = {}; tgt = keep = None
    for it in spec:
        src = canon(FbxSkel(os.path.join(srcdir, it['src']), it.get('fps', 30)))
        tgt, keep, ts, tracks = retarget(src, it.get('t0'), it.get('t1'), loop=it.get('loop', False), inplace=it.get('inplace', True), stab=it.get('stab', 0), pelvis=it.get('pelvis'))
        clips[it['name']] = (ts, tracks); print(f"{it['name']:24s} {len(ts):4d} кадров {ts[-1]:6.2f} с")
    n, k = save(outp, tgt, keep, clips); print('→', outp, n // 1024, 'КБ, ключей', k)
