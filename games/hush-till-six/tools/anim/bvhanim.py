"""BVH (мокап CMU и др.) → тот же вид, что FbxSkel: кости, иерархия, покой, локальные повороты/смещения по кадрам (tools/anim/mocap.py)."""
import sys, os, numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from fbxanim import qmul, qinv, qrot, qnorm

def _axq(ax, a):
    q = np.zeros(a.shape + (4,)); q[..., 'XYZ'.index(ax)] = np.sin(a / 2); q[..., 3] = np.cos(a / 2); return q

class BvhSkel:
    def __init__(s, path, fps=30, unit=None):
        tok = open(path).read().split()
        s.name, s.parent, s.offset, s.chans, s.order = {}, {}, {}, {}, []
        i, stack, n = 0, [], 0
        while tok[i] != 'MOTION':
            t = tok[i]
            if t in ('ROOT', 'JOINT'):
                s.name[n] = tok[i + 1]; s.parent[n] = stack[-1] if stack else None; s.order.append(n); cur = n; n += 1; i += 2
            elif t == 'End':   # конечная точка без каналов — пропускаем блок
                depth = 0; i += 2
                while True:
                    if tok[i] == '{': depth += 1
                    elif tok[i] == '}':
                        depth -= 1
                        if depth == 0: i += 1; break
                    i += 1
            elif t == '{': stack.append(cur); i += 1
            elif t == '}': stack.pop(); cur = stack[-1] if stack else None; i += 1
            elif t == 'OFFSET': s.offset[cur] = np.array([float(x) for x in tok[i + 1:i + 4]]); i += 4
            elif t == 'CHANNELS': k = int(tok[i + 1]); s.chans[cur] = tok[i + 2:i + 2 + k]; i += 2 + k
            else: i += 1
        i += 1; nf = int(tok[i + 1]); dtf = float(tok[i + 4]); i += 5
        ncol = sum(len(s.chans.get(j, [])) for j in s.order)
        data = np.array(tok[i:i + nf * ncol], float).reshape(nf, ncol)
        # пересэмпл в fps
        src_t = np.arange(nf) * dtf; s.fps = fps; s.ts = np.arange(0, src_t[-1], 1 / fps); s.F = len(s.ts)
        idx = np.clip(np.searchsorted(src_t, s.ts), 0, nf - 1); s._d = data[idx]
        s.up = 1; s.unit = unit or 1.0
        # «покой»: нулевые повороты, кости по смещениям (мировые повороты в покое — единичные)
        s.bind = {}
        W = {}
        for j in s.order:
            p = s.parent[j]; W[j] = (W[p] if p is not None else np.zeros(3)) + s.offset.get(j, np.zeros(3))
        lift = -min(W[j][1] for j in s.order)   # в покое BVH таз в нуле — поднимаем скелет так, чтобы ступни стояли на полу (высота таза нужна для масштаба)
        for j in s.order:
            M = np.eye(4); M[:3, 3] = (W[j] + [0, lift, 0]); s.bind[j] = M

    def local(s):
        N = len(s.order); T = np.zeros((s.F, N, 3)); R = np.zeros((s.F, N, 4)); R[..., 3] = 1; col = 0
        for n, j in enumerate(s.order):
            ch = s.chans.get(j, []); off = s.offset.get(j, np.zeros(3)); tr = np.repeat(off[None], s.F, 0).copy(); q = R[:, n].copy()
            for c in ch:
                v = s._d[:, col]; col += 1
                if c.endswith('position'): tr[:, 'XYZ'.index(c[0])] = v
                else: q = qmul(q, _axq(c[0], np.radians(v)))   # BVH: каналы по порядку, первый — внешний
            T[:, n] = tr * s.unit; R[:, n] = qnorm(q)
        return T, R

    def world(s, T, R):
        idx = {j: n for n, j in enumerate(s.order)}; WT = np.zeros_like(T); WR = np.zeros_like(R)
        for n, j in enumerate(s.order):
            p = s.parent[j]
            if p is None: WT[..., n, :], WR[..., n, :] = T[..., n, :], R[..., n, :]
            else: k = idx[p]; WR[..., n, :] = qmul(WR[..., k, :], R[..., n, :]); WT[..., n, :] = WT[..., k, :] + qrot(WR[..., k, :], T[..., n, :])
        return WT, WR

if __name__ == '__main__':
    s = BvhSkel(sys.argv[1]); T, R = s.local(); WT, WR = s.world(T, R)
    print('frames', s.F, 'dur', round(s.ts[-1], 2))
    for n, j in enumerate(s.order): print(f'{s.name[j]:16s} parent={s.name.get(s.parent[j], "-"):14s} rest={(s.bind[j][:3, 3]).round(2)} f60={WT[min(60, s.F - 1), n].round(2)}')
