"""Скелет и анимация из двоичного FBX: кости, иерархия, покой (BindPose), кривые → локальные повороты/смещения по кадрам."""
import sys, os, numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from fbxread import read, props70
TICK = 46186158000

def euler_q(e_deg, order=0):
    """FBX eEulerXYZ (0): поворот X, затем Y, затем Z (матрица Rz·Ry·Rx). Кватернионы [x,y,z,w]."""
    e = np.radians(np.asarray(e_deg, float))
    def ax(i, a):
        q = np.zeros(a.shape + (4,)); q[..., i] = np.sin(a / 2); q[..., 3] = np.cos(a / 2); return q
    qx, qy, qz = ax(0, e[..., 0]), ax(1, e[..., 1]), ax(2, e[..., 2])
    seq = {0: 'xyz', 1: 'xzy', 2: 'yzx', 3: 'yxz', 4: 'zxy', 5: 'zyx'}[order]
    m = {'x': qx, 'y': qy, 'z': qz}
    q = m[seq[0]]
    for c in seq[1:]: q = qmul(m[c], q)   # следующий поворот — слева
    return q

def qmul(a, b):
    ax, ay, az, aw = np.moveaxis(a, -1, 0); bx, by, bz, bw = np.moveaxis(b, -1, 0)
    return np.stack([aw * bx + ax * bw + ay * bz - az * by, aw * by - ax * bz + ay * bw + az * bx,
                     aw * bz + ax * by - ay * bx + az * bw, aw * bw - ax * bx - ay * by - az * bz], -1)
def qinv(q): return q * np.array([-1, -1, -1, 1])
def qrot(q, v):
    u = q[..., :3]; w = q[..., 3:]; t = 2 * np.cross(u, v); return v + w * t + np.cross(u, t)
def qnorm(q): return q / np.linalg.norm(q, axis=-1, keepdims=True)

def mat_to_q(M):
    M = np.asarray(M, float); m = M[:3, :3] / np.linalg.norm(M[:3, :3], axis=0)
    t = m.trace()
    if t > 0: s = np.sqrt(t + 1) * 2; return qnorm(np.array([(m[2, 1] - m[1, 2]) / s, (m[0, 2] - m[2, 0]) / s, (m[1, 0] - m[0, 1]) / s, .25 * s]))
    i = int(np.argmax([m[0, 0], m[1, 1], m[2, 2]]))
    if i == 0: s = np.sqrt(1 + m[0, 0] - m[1, 1] - m[2, 2]) * 2; return qnorm(np.array([.25 * s, (m[0, 1] + m[1, 0]) / s, (m[0, 2] + m[2, 0]) / s, (m[2, 1] - m[1, 2]) / s]))
    if i == 1: s = np.sqrt(1 + m[1, 1] - m[0, 0] - m[2, 2]) * 2; return qnorm(np.array([(m[0, 1] + m[1, 0]) / s, .25 * s, (m[1, 2] + m[2, 1]) / s, (m[0, 2] - m[2, 0]) / s]))
    s = np.sqrt(1 + m[2, 2] - m[0, 0] - m[1, 1]) * 2; return qnorm(np.array([(m[0, 2] + m[2, 0]) / s, (m[1, 2] + m[2, 1]) / s, .25 * s, (m[1, 0] - m[0, 1]) / s]))

class FbxSkel:
    def __init__(s, path, fps=30):
        r = read(path); objs = r.find('Objects'); con = r.find('Connections')
        gs = props70(r.find('GlobalSettings')); s.unit = float((gs.get('UnitScaleFactor') or [1])[0]) / 100.0   # → метры
        s.up = (gs.get('UpAxis') or [1])[0]
        models, curves, cnodes = {}, {}, {}
        for o in objs.kids:
            if o.name == 'Model': models[o.props[0]] = o
            elif o.name == 'AnimationCurve': curves[o.props[0]] = o
            elif o.name == 'AnimationCurveNode': cnodes[o.props[0]] = o
        parent, cn_of_model, curve_of_cn = {}, {}, {}
        for c in con.all('C'):
            kind, a, b = c.props[0], c.props[1], c.props[2]
            if kind == 'OO' and a in models and b in models: parent[a] = b
            elif kind == 'OP' and a in cnodes and b in models: cn_of_model[(b, c.props[3])] = a
            elif kind == 'OP' and a in curves and b in cnodes: curve_of_cn[(b, c.props[3])] = a
        bones = [i for i, m in models.items() if m.props[2] in ('LimbNode', 'Root', 'Limb', 'Null')]
        s.name = {i: models[i].props[1].split('\x00')[0].split(':')[-1] for i in bones}
        s.parent = {i: (parent.get(i) if parent.get(i) in s.name else None) for i in bones}
        # порядок: родитель раньше ребёнка
        order, seen = [], set()
        def visit(i):
            if i in seen: return
            if s.parent[i] is not None: visit(s.parent[i])
            seen.add(i); order.append(i)
        for i in bones: visit(i)
        s.order = order
        s.P = {i: props70(models[i]) for i in bones}
        # кривые → время
        tmax = 0; s.anim = {}
        for i in bones:
            for prop in ('Lcl Translation', 'Lcl Rotation', 'Lcl Scaling'):
                cn = cn_of_model.get((i, prop))
                if cn is None: continue
                chans = []
                for k, ax in enumerate('XYZ'):
                    cv = curve_of_cn.get((cn, 'd|' + ax))
                    if cv is None: chans.append(None); continue
                    kt = curves[cv].find('KeyTime').props[0].astype(np.float64) / TICK; kv = curves[cv].find('KeyValueFloat').props[0].astype(np.float64)
                    chans.append((kt, kv)); tmax = max(tmax, kt[-1])
                s.anim[(i, prop)] = chans
        s.fps = fps; F = max(2, int(round(tmax * fps)) + 1); s.ts = np.linspace(0, tmax, F); s.F = F
        # покой: BindPose (мировые матрицы)
        s.bind = {}
        for o in objs.kids:
            if o.name == 'Pose':
                for pn in o.all('PoseNode'):
                    nid = pn.find('Node').props[0]; M = np.array(pn.find('Matrix').props[0], float).reshape(4, 4).T   # FBX хранит по столбцам
                    s.bind[nid] = M

    def vec(s, i, prop, default):
        v = s.P[i].get(prop)
        return np.array([float(x) for x in v[:3]], float) if v else np.array(default, float)

    def local(s):
        """локальные T [F,N,3] (метры) и R [F,N,4] по кадрам, порядок s.order"""
        N = len(s.order); T = np.zeros((s.F, N, 3)); R = np.zeros((s.F, N, 4)); R[..., 3] = 1
        for n, i in enumerate(s.order):
            t0 = s.vec(i, 'Lcl Translation', [0, 0, 0]); r0 = s.vec(i, 'Lcl Rotation', [0, 0, 0])
            pre = s.vec(i, 'PreRotation', [0, 0, 0]); post = s.vec(i, 'PostRotation', [0, 0, 0])
            ro = int((s.P[i].get('RotationOrder') or [0])[0])
            tr = np.repeat(t0[None], s.F, 0); rot = np.repeat(r0[None], s.F, 0)
            for prop, arr in (('Lcl Translation', tr), ('Lcl Rotation', rot)):
                ch = s.anim.get((i, prop))
                if not ch: continue
                for k, c in enumerate(ch):
                    if c is None: continue
                    kt, kv = c; arr[:, k] = np.interp(s.ts, kt, kv)
            q = euler_q(rot, ro)
            if np.abs(pre).sum() > 1e-6: q = qmul(euler_q(pre, 0)[None].repeat(s.F, 0), q)
            if np.abs(post).sum() > 1e-6: q = qmul(q, qinv(euler_q(post, 0))[None].repeat(s.F, 0))
            T[:, n] = tr * s.unit; R[:, n] = qnorm(q)
        return T, R

    def world(s, T, R):
        N = len(s.order); idx = {i: n for n, i in enumerate(s.order)}
        WT = np.zeros_like(T); WR = np.zeros_like(R)
        for n, i in enumerate(s.order):
            p = s.parent[i]
            if p is None: WT[..., n, :], WR[..., n, :] = T[..., n, :], R[..., n, :]
            else:
                k = idx[p]; WR[..., n, :] = qmul(WR[..., k, :], R[..., n, :]); WT[..., n, :] = WT[..., k, :] + qrot(WR[..., k, :], T[..., n, :])
        return WT, WR

if __name__ == '__main__':
    s = FbxSkel(sys.argv[1])
    print('unit', s.unit, 'up', s.up, 'frames', s.F, 'dur', round(s.ts[-1], 2))
    T, R = s.local(); WT, WR = s.world(T, R)
    names = [s.name[i] for i in s.order]
    for n, i in enumerate(s.order):
        b = s.bind.get(i); bp = (b[:3, 3] * s.unit).round(3) if b is not None else None
        print(f'{names[n]:22s} parent={s.name.get(s.parent[i], "-"):16s} f0={WT[0, n].round(3)} bind={bp}')
