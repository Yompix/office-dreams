"""Чтение glTF/GLB без зависимостей (numpy): узлы, иерархия, мировые матрицы, клипы. Для перенацеливания анимаций."""
import json, struct, numpy as np

CT = {5120: np.int8, 5121: np.uint8, 5122: np.int16, 5123: np.uint16, 5125: np.uint32, 5126: np.float32}
NC = {'SCALAR': 1, 'VEC2': 2, 'VEC3': 3, 'VEC4': 4, 'MAT4': 16}

def load(path):
    b = open(path, 'rb').read()
    n = struct.unpack('<I', b[12:16])[0]
    j = json.loads(b[20:20 + n])
    rest = b[20 + n:]
    bin_ = rest[8:8 + struct.unpack('<I', rest[0:4])[0]] if rest else b''
    return j, bin_

def accessor(j, bin_, i):
    a = j['accessors'][i]; bv = j['bufferViews'][a['bufferView']]
    dt = CT[a['componentType']]; nc = NC[a['type']]
    off = bv.get('byteOffset', 0) + a.get('byteOffset', 0)
    stride = bv.get('byteStride', 0); isz = np.dtype(dt).itemsize * nc
    if stride and stride != isz:
        raw = np.frombuffer(bin_, np.uint8, count=stride * a['count'], offset=off).reshape(a['count'], stride)[:, :isz]
        arr = np.frombuffer(raw.tobytes(), dt).reshape(a['count'], nc)
    else:
        arr = np.frombuffer(bin_, dt, count=a['count'] * nc, offset=off).reshape(a['count'], nc)
    arr = arr.astype(np.float64)
    if a.get('normalized'):
        arr = {np.int8: lambda x: np.maximum(x / 127, -1), np.uint8: lambda x: x / 255, np.int16: lambda x: np.maximum(x / 32767, -1), np.uint16: lambda x: x / 65535}[dt](arr)
    return arr

# кватернионы [x, y, z, w]
def qmul(a, b):
    ax, ay, az, aw = np.moveaxis(a, -1, 0); bx, by, bz, bw = np.moveaxis(b, -1, 0)
    return np.stack([aw * bx + ax * bw + ay * bz - az * by, aw * by - ax * bz + ay * bw + az * bx,
                     aw * bz + ax * by - ay * bx + az * bw, aw * bw - ax * bx - ay * by - az * bz], -1)
def qinv(q): return q * np.array([-1, -1, -1, 1])
def qrot(q, v):
    u = q[..., :3]; w = q[..., 3:]
    t = 2 * np.cross(u, v)
    return v + w * t + np.cross(u, t)
def qnorm(q): return q / np.linalg.norm(q, axis=-1, keepdims=True)

class Skel:
    def __init__(self, path):
        self.j, self.bin = load(path)
        self.nodes = self.j['nodes']
        self.name = [n.get('name', f'n{i}') for i, n in enumerate(self.nodes)]
        self.idx = {k: i for i, k in enumerate(self.name)}
        self.parent = [-1] * len(self.nodes)
        for i, n in enumerate(self.nodes):
            for c in n.get('children', []): self.parent[c] = i
        self.T = np.array([n.get('translation', [0, 0, 0]) for n in self.nodes], float)
        self.R = np.array([n.get('rotation', [0, 0, 0, 1]) for n in self.nodes], float)
        self.S = np.array([n.get('scale', [1, 1, 1]) for n in self.nodes], float)
        order, seen = [], set()
        def visit(i):
            if i in seen: return
            if self.parent[i] >= 0: visit(self.parent[i])
            seen.add(i); order.append(i)
        for i in range(len(self.nodes)): visit(i)
        self.order = order
        self.clips = {a['name']: a for a in self.j.get('animations', [])}

    def world(self, T=None, R=None, S=None):
        """мировые (позиция, поворот, масштаб) для локальных T,R,S формы [..., N, k]"""
        T = self.T if T is None else T; R = self.R if R is None else R; S = self.S if S is None else S
        WT = np.zeros_like(T); WR = np.zeros_like(R); WS = np.ones_like(S)
        for i in self.order:
            p = self.parent[i]
            if p < 0: WT[..., i, :], WR[..., i, :], WS[..., i, :] = T[..., i, :], R[..., i, :], S[..., i, :]
            else:
                WR[..., i, :] = qmul(WR[..., p, :], R[..., i, :])
                WT[..., i, :] = WT[..., p, :] + qrot(WR[..., p, :], WS[..., p, :] * T[..., i, :])
                WS[..., i, :] = WS[..., p, :] * S[..., i, :]
        return WT, WR, WS

    def sample(self, clip, fps=30):
        """локальные T,R,S всех узлов по кадрам клипа: [F, N, k]"""
        a = self.clips[clip]
        tmax = 0
        chans = []
        for c in a['channels']:
            s = a['samplers'][c['sampler']]
            ti = accessor(self.j, self.bin, s['input'])[:, 0]; vo = accessor(self.j, self.bin, s['output'])
            tmax = max(tmax, ti[-1]); chans.append((c['target'].get('node'), c['target']['path'], ti, vo, s.get('interpolation', 'LINEAR')))
        F = max(2, int(round(tmax * fps)) + 1)
        ts = np.linspace(0, tmax, F)
        T = np.repeat(self.T[None], F, 0); R = np.repeat(self.R[None], F, 0); S = np.repeat(self.S[None], F, 0)
        for node, path, ti, vo, interp in chans:
            if node is None or path not in ('translation', 'rotation', 'scale'): continue
            if interp == 'CUBICSPLINE': vo = vo.reshape(len(ti), 3, -1)[:, 1]
            out = np.zeros((F, vo.shape[1]))
            k = np.clip(np.searchsorted(ti, ts, side='right') - 1, 0, len(ti) - 1); k2 = np.minimum(k + 1, len(ti) - 1)
            dt = np.where(ti[k2] > ti[k], ti[k2] - ti[k], 1); f = np.clip((ts - ti[k]) / dt, 0, 1)[:, None]
            if interp == 'STEP': f = f * 0
            if path == 'rotation':
                a0, a1 = vo[k], vo[k2].copy(); neg = (a0 * a1).sum(1) < 0; a1[neg] *= -1
                out = qnorm(a0 * (1 - f) + a1 * f)   # nlerp на 30 к/с ≈ slerp
                R[:, node] = out
            else:
                out = vo[k] * (1 - f) + vo[k2] * f
                (T if path == 'translation' else S)[:, node] = out
        return ts, T, R, S

def save_anim_glb(path, sk, keep, clips):
    """GLB только со скелетом (узлы sk из keep, их покой) и клипами: clips = {имя: (times[F], {узел: {'rotation': [F,4], 'translation': [F,3]}})}"""
    keep = [i for i in sk.order if sk.name[i] in keep]
    new = {o: n for n, o in enumerate(keep)}
    nodes = []
    for o in keep:
        n = {'name': sk.name[o]}
        ch = [new[c] for c in sk.nodes[o].get('children', []) if c in new]
        if ch: n['children'] = ch
        if np.abs(sk.T[o]).sum() > 0: n['translation'] = [float(x) for x in sk.T[o]]
        if np.abs(sk.R[o] - [0, 0, 0, 1]).sum() > 1e-9: n['rotation'] = [float(x) for x in sk.R[o]]
        if np.abs(sk.S[o] - 1).sum() > 1e-9: n['scale'] = [float(x) for x in sk.S[o]]
        nodes.append(n)
    roots = [new[o] for o in keep if sk.parent[o] not in new]
    buf = bytearray(); views = []; accs = []
    def add(arr, typ, mm=False):
        arr = np.ascontiguousarray(arr, np.float32)
        while len(buf) % 4: buf.append(0)
        views.append({'buffer': 0, 'byteOffset': len(buf), 'byteLength': arr.nbytes}); buf.extend(arr.tobytes())
        a = {'bufferView': len(views) - 1, 'componentType': 5126, 'count': int(arr.shape[0]), 'type': typ}
        if mm: a['min'] = [float(arr.min())]; a['max'] = [float(arr.max())]
        accs.append(a); return len(accs) - 1
    anims = []
    for name, (ts, tracks) in clips.items():
        ti = add(ts[:, None], 'SCALAR', True); samplers = []; channels = []
        for nd, paths in tracks.items():
            if nd not in new: continue
            for p, v in paths.items():
                samplers.append({'input': ti, 'output': add(v, 'VEC4' if p == 'rotation' else 'VEC3'), 'interpolation': 'LINEAR'})
                channels.append({'sampler': len(samplers) - 1, 'target': {'node': new[nd], 'path': p}})
        anims.append({'name': name, 'samplers': samplers, 'channels': channels})
    while len(buf) % 4: buf.append(0)
    j = {'asset': {'version': '2.0', 'generator': 'office-dreams tools/anim'}, 'scene': 0, 'scenes': [{'nodes': roots}], 'nodes': nodes,
         'animations': anims, 'accessors': accs, 'bufferViews': views, 'buffers': [{'byteLength': len(buf)}]}
    js = json.dumps(j, separators=(',', ':')).encode()
    while len(js) % 4: js += b' '
    out = struct.pack('<III', 0x46546C67, 2, 12 + 8 + len(js) + 8 + len(buf)) + struct.pack('<II', len(js), 0x4E4F534A) + js + struct.pack('<II', len(buf), 0x004E4942) + bytes(buf)
    open(path, 'wb').write(out)
