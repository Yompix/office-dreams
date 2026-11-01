"""Своя одежда героев WHAT THE LAKE KEEPS — шьётся по телу в Blender (tools/chars/gen.py зовёт build() после позы покоя).
В CC0-пакетах MakeHuman почти нет зимней одежды (одна куртка — у Итана из Hush Till Six), поэтому куртки, худи с капюшоном,
жилет и шапку делаем сами: участок тела по весам костей → копия, отодвинутая наружу и сглаженная (ткань не повторяет мышцы),
с толщиной; веса костей те же, что у тела, — одежда двигается вместе с ним; кожа под одеждой удаляется (не протыкает).
Ткань — текстуры Poly Haven (CC0): цвет × оттенок, нормали, шероховатость.
Виды (C['garments'] в tools/lake/chars.json):
  jacket — торс и рукава до запястий, воротник; off — насколько велика (у Ноя отцовская — не по размеру), hem — низ от таза
  vest   — то же без рукавов
  hood   — капюшон на голове с вырезом для лица (с jacket — худи); down: true — опущенный: мешком на спине, валиком вокруг шеи
  beanie — шапка от макушки до бровей, с отворотом
  scarf  — шарф: валик вокруг шеи поверх воротника и конец на груди
"""
import bpy, bmesh, os, math, bisect
from mathutils import Vector
from mathutils.bvhtree import BVHTree
from mathutils.kdtree import KDTree

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
    import numpy as np
    a = np.array(img.pixels[:], dtype=np.float32).reshape(-1, 4)
    lum = a[:, :3] @ np.array([0.3, 0.59, 0.11], np.float32)
    g = lum / max(float(lum.mean()), 1e-3) * 0.78   # фактура без своего цвета (зелёная плащёвка, оранжевое сукно), яркость — к 0,78
    a[:, 0] = np.clip(g * tint[0], 0, 1)
    a[:, 1] = np.clip(g * tint[1], 0, 1)
    a[:, 2] = np.clip(g * tint[2], 0, 1)
    img.pixels[:] = a.ravel()
    img.update()
    img.pack()
    return img


def _material(name, tex, tint, tile, rough=None):
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
    if rough is None:   # шероховатость — из текстуры (кожа); ткань — ровно матовая
        nt.links.new(sep.outputs['Green'], bs.inputs['Roughness'])
    else:
        bs.inputs['Roughness'].default_value = rough
    bs.inputs['Metallic'].default_value = 0.0   # ткань не металл (в синем канале у части текстур Poly Haven — мусор)
    return m


def _drape(bm, M, src, w_torso, w_arms, w_head, slope=0.12, band=0.5):
    """ткань висит, а не облегает: вокруг оси торса радиус берётся по самым выступающим местам рядом (грудь, лопатки,
    живот), ниже них ткань падает почти отвесно (спереди и сзади — круче, по бокам — ближе к телу, чтобы не лезть в руки);
    ямки (между грудными, вдоль позвоночника, под грудью, талия) заполняются — фигура под одеждой не читается"""
    Mi = M.inverted()
    vs = []
    for v in bm.verts:
        k = w_torso[v[src]] - w_arms[v[src]] - 2.0 * w_head[v[src]]   # воротник и шея — как были
        if k > 0.0:
            vs.append((v, M @ v.co, min(1.0, k * 2.0)))
    if len(vs) < 50:
        return
    DZ, NA = 0.015, 48
    sl = {}
    for v, p, k in vs:
        if k >= 1.0:
            sl.setdefault(math.floor(p.z / DZ), []).append(p)
    ks = sorted(sl)
    cen = {}
    for zb in ks:   # ось торса: центр среза на каждой высоте, сглаженный по ±4.5 см
        acc = [q for j in range(zb - 3, zb + 4) for q in sl.get(j, [])]
        cen[zb] = (sum(q.x for q in acc) / len(acc), sum(q.y for q in acc) / len(acc))

    def axis(z):
        zb = math.floor(z / DZ)
        i = min(max(bisect.bisect_left(ks, zb), 0), len(ks) - 1)
        return cen[ks[i]]
    pol = []
    R = {}
    for v, p, k in vs:
        cx, cy = axis(p.z)
        dx, dy = p.x - cx, p.y - cy
        r = math.hypot(dx, dy)
        if r < 1e-4:
            continue
        fa = (math.atan2(dy, dx) + math.pi) / (2 * math.pi) * NA
        fz = p.z / DZ
        pol.append((v, p, k, cx, cy, dx / r, dy / r, r, fa, fz))
        key = (int(fa) % NA, math.floor(fz))
        R[key] = max(R.get(key, 0.0), r)
    z0 = min(t[1] for t in R)
    z1 = max(t[1] for t in R)
    th = [((a + 0.5) / NA * 2 * math.pi - math.pi) for a in range(NA)]
    for zb in range(z0, z1 + 1):   # каждый срез — выпуклый: ткань перекидывается мостом через ложбинки (между грудными, позвоночник)
        pts = sorted((R[(a, zb)] * math.cos(th[a]), R[(a, zb)] * math.sin(th[a])) for a in range(NA) if R.get((a, zb), 0.0) > 0.0)
        if len(pts) < 3:
            continue
        cr = lambda o, p, q: (p[0] - o[0]) * (q[1] - o[1]) - (p[1] - o[1]) * (q[0] - o[0])
        lo, up = [], []
        for q in pts:
            while len(lo) >= 2 and cr(lo[-2], lo[-1], q) <= 0:
                lo.pop()
            lo.append(q)
        for q in reversed(pts):
            while len(up) >= 2 and cr(up[-2], up[-1], q) <= 0:
                up.pop()
            up.append(q)
        hull = lo[:-1] + up[:-1]
        for a in range(NA):
            if R.get((a, zb), 0.0) <= 0.0:
                continue
            ux, uy = math.cos(th[a]), math.sin(th[a])
            best = 0.0
            for i in range(len(hull)):
                p, q = hull[i], hull[(i + 1) % len(hull)]
                ex, ey = q[0] - p[0], q[1] - p[1]
                den = ux * ey - uy * ex
                if abs(den) < 1e-9:
                    continue
                t = (p[0] * ey - p[1] * ex) / den
                sp = (p[0] * uy - p[1] * ux) / den
                if -1e-6 <= sp <= 1.0 + 1e-6 and t > best:
                    best = t
            R[(a, zb)] = max(R[(a, zb)], best)
    side = [abs(math.cos((a + 0.5) / NA * 2 * math.pi - math.pi)) for a in range(NA)]   # 1 — бок, 0 — грудь/спина
    D = {}
    for a in range(NA):   # «закрытие»: максимум по соседним углам и высотам (по бокам — уже окно)
        wa = 1
        for zb in range(z0, z1 + 1):
            D[(a, zb)] = max((R.get(((a + i) % NA, zb + j), 0.0) for i in range(-wa, wa + 1) for j in range(0, 3)), default=0.0)
    for a in range(NA):   # спереди и сзади ткань натянута прямо между выступами (ключицы → грудь → живот): выпуклая оболочка
        wh = 1.0 - min(1.0, max(0.0, (side[a] - 0.4) / 0.4))
        if wh <= 0.0:
            continue
        pts = [(zb, D[(a, zb)]) for zb in range(z0, z1 + 1) if D[(a, zb)] > 0.0]
        hull = []
        for q in pts:
            while len(hull) >= 2 and (hull[-1][0] - hull[-2][0]) * (q[1] - hull[-2][1]) - (hull[-1][1] - hull[-2][1]) * (q[0] - hull[-2][0]) >= 0:
                hull.pop()
            hull.append(q)
        for i in range(len(hull) - 1):
            (za, ra), (zc, rc) = hull[i], hull[i + 1]
            for zb in range(za, zc + 1):
                h = ra + (rc - ra) * (zb - za) / max(1, zc - za)
                if D[(a, zb)] > 0.0 and h > D[(a, zb)]:
                    D[(a, zb)] += (h - D[(a, zb)]) * wh
    for a in range(NA):   # ткань падает сверху вниз не круче slope (по бокам — круче, ближе к телу)
        sk = slope + 0.65 * side[a]
        cur = 0.0
        for zb in range(z1, z0 - 1, -1):
            cur = max(D[(a, zb)], cur - sk * DZ)
            D[(a, zb)] = cur
    B = {}
    for a in range(NA):
        for zb in range(z0, z1 + 1):
            acc = [D[((a + i) % NA, zb + j)] for i in (-1, 0, 1) for j in (-1, 0, 1) if z0 <= zb + j <= z1]
            B[(a, zb)] = sum(acc) / len(acc)

    def at(fa, fz):   # двулинейно по углу и высоте
        a0 = math.floor(fa - 0.5)
        ta = fa - 0.5 - a0
        zb = min(max(math.floor(fz - 0.5), z0), z1 - 1)
        tz = min(max(fz - 0.5 - zb, 0.0), 1.0)
        g = lambda a, z: B[(a % NA, z)]
        return (g(a0, zb) * (1 - ta) + g(a0 + 1, zb) * ta) * (1 - tz) + (g(a0, zb + 1) * (1 - ta) + g(a0 + 1, zb + 1) * ta) * tz
    zlo = min(t[9] for t in pol) * DZ
    for v, p, k, cx, cy, ux, uy, r, fa, fz in pol:
        rt = at(fa, fz)
        hb = max(0.0, 1.0 - (p.z - zlo) / 0.06)   # низ (резинка худи, пояс куртки) — ближе к телу
        rt = rt + (r - rt) * band * hb * hb
        if rt > r:
            nr = r + (rt - r) * k
            v.co = Mi @ Vector((cx + ux * nr, cy + uy * nr, p.z))


def _tailor(bm, M, src, co, w_torso, w_arms, w_head, w_hands, arm, ease=0.035, ease_top=0.03, ease_cuff=0.018, box=3.2, slope=0.06):
    """крой вместо копии тела: корпус — короб (сечение — скруглённый прямоугольник по самым выступающим местам туловища
    с запасом ease, ниже груди ткань падает почти отвесно — фигура под курткой не читается: ни груди, ни пупка);
    рукава — трубы вокруг костей рук, шире у плеча (ease_top), уже у манжеты (ease_cuff). Топология и веса — от тела."""
    Mi = M.inverted()
    DZ = 0.015
    # ось туловища и габариты по высоте — только по туловищу (руки и голова не в счёт)
    tv = [co[i] for i in range(len(co)) if w_torso[i] > 0.6 and w_arms[i] < 0.2 and w_head[i] < 0.3]
    if len(tv) < 100:
        return
    sl = {}
    for p in tv:
        sl.setdefault(math.floor(p.z / DZ), []).append(p)
    ks = sorted(sl)
    cen = {}
    for zb in ks:
        acc = [q for j in range(zb - 3, zb + 4) for q in sl.get(j, [])]
        cen[zb] = (sum(q.x for q in acc) / len(acc), sum(q.y for q in acc) / len(acc))
    ext = {}
    for zb in ks:
        cx, cy = cen[zb]
        acc = [q for j in range(zb - 1, zb + 2) for q in sl.get(j, [])]
        ext[zb] = [max([q.x - cx for q in acc] + [0.02]), max([cx - q.x for q in acc] + [0.02]),
                   max([q.y - cy for q in acc] + [0.02]), max([cy - q.y for q in acc] + [0.02])]
    for d in range(4):   # ткань падает сверху вниз не круче slope: ниже груди и лопаток — почти отвесно
        cur = 0.0
        for zb in reversed(ks):
            cur = max(ext[zb][d], cur - slope * DZ)
            ext[zb][d] = cur
    sm = {}
    for zb in ks:   # плавно по высоте
        acc = [ext[j] for j in range(zb - 2, zb + 3) if j in ext]
        sm[zb] = [sum(a[d] for a in acc) / len(acc) for d in range(4)]

    def near(z):
        zb = math.floor(z / DZ)
        i = min(max(bisect.bisect_left(ks, zb), 0), len(ks) - 1)
        return ks[i]
    # кости рук: плечо → локоть → запястье (поза MPFB по умолчанию)
    segs = {}
    for sd in ('l', 'r'):
        b = [arm.matrix_world @ arm.data.bones[n].head_local for n in ('upperarm_' + sd, 'lowerarm_' + sd, 'hand_' + sd)]
        segs[sd] = b
    def foot(p, sd):
        a, b, c = segs[sd]
        best = None
        for (u, v, t0, t1) in ((a, b, 0.0, 0.5), (b, c, 0.5, 1.0)):
            e = v - u
            t = max(0.0, min(1.0, (p - u).dot(e) / max(e.length_squared, 1e-9)))
            f = u + e * t
            dd = (p - f).length
            if best is None or dd < best[0]:
                best = (dd, f, t0 + (t1 - t0) * t, e.normalized())
        return best
    # толщина руки тела по длине (в долях от плеча до запястья)
    prof = {'l': [0.0] * 21, 'r': [0.0] * 21}
    for i in range(len(co)):
        if w_arms[i] > 0.5 and w_hands[i] < 0.1:
            sd = 'l' if co[i].x > 0 else 'r'
            dd, f, t, e = foot(co[i], sd)
            k = int(round(t * 20))
            prof[sd][k] = max(prof[sd][k], dd)
    for sd in prof:
        pr = prof[sd]
        for k in range(21):   # пустые и слишком тонкие места — по соседям
            if pr[k] < 0.02:
                nb = [pr[j] for j in range(max(0, k - 3), min(21, k + 4)) if pr[j] >= 0.02]
                pr[k] = max(nb) if nb else 0.045
        prof[sd] = [max(pr[max(0, k - 1):k + 2]) for k in range(21)]
    for v in bm.verts:
        i = v[src]
        p = M @ v.co
        wt = max(0.0, min(1.0, (w_torso[i] - w_arms[i]) * 2.0)) * max(0.0, 1.0 - 2.5 * w_head[i])
        ws = max(0.0, min(1.0, (w_arms[i] - w_torso[i]) * 2.0 + w_hands[i]))
        tgt = p.copy()
        acc = 0.0
        if wt > 0.0:
            zb = near(p.z)
            cx, cy = cen[zb]
            a = sm[zb]
            dx, dy = p.x - cx, p.y - cy
            r0 = math.hypot(dx, dy)
            if r0 > 1e-5:
                ux, uy = dx / r0, dy / r0
                ax = (a[0] if ux > 0 else a[1]) + ease
                by = (a[2] if uy > 0 else a[3]) + ease
                r = (abs(ux / ax) ** box + abs(uy / by) ** box) ** (-1.0 / box)
                r = max(r, r0)   # только наружу: внутрь короба ткань не уходит
                tgt = tgt + (Vector((cx + ux * r, cy + uy * r, p.z)) - p) * wt
                acc += wt
        if ws > 0.0 and acc < 1.0:
            sd = 'l' if p.x > 0 else 'r'
            dd, f, t, e = foot(p, sd)
            if dd > 1e-5:
                k = min(20, max(0, int(round(t * 20))))
                r = prof[sd][k] + ease_top * (1.0 - t) + ease_cuff * t
                r = max(r, dd)
                d = (p - f) / dd
                w = min(ws, 1.0 - acc)
                tgt = tgt + ((f + d * r) - p) * w
        v.co = Mi @ tgt


def _extend(bm, M, co, w_legs_l, w_legs_r, drop, flare=0.06, ease=0.03, step=0.025, arms_w=None, thigh=0.3):
    """подол ниже бёдер: нижний край куртки (самое низкое замкнутое кольцо) продолжается вниз на drop — кольцами,
    снаружи от ног (по габаритам тела на каждой высоте + ease), чуть расширяясь (flare); веса нового низа — от таза
    к бёдрам своей стороны (ткань висит от бёдер, при шаге идёт за ногой лишь отчасти)"""
    if drop <= 0.0:
        return
    # слой метки — до сбора вершин: новый слой в BMesh делает прежние ссылки Python на вершины недействительными
    ext = bm.verts.layers.float.get('lake_ext') or bm.verts.layers.float.new('lake_ext')   # глубина ниже подола, 0…1
    Mi = M.inverted()
    bnd = [e for e in bm.edges if e.is_boundary]
    loops, seen = [], set()
    for e0 in bnd:
        if e0 in seen:
            continue
        loop, stack = set(), [e0]
        while stack:
            e = stack.pop()
            if e in seen:
                continue
            seen.add(e)
            for v in e.verts:
                loop.add(v)
                for e2 in v.link_edges:
                    if e2.is_boundary and e2 not in seen:
                        stack.append(e2)
        loops.append(loop)
    if not loops:
        return
    hem = min(loops, key=lambda L: sum((M @ v.co).z for v in L) / len(L))
    pts = [M @ v.co for v in hem]
    cx = sum(p.x for p in pts) / len(pts)
    cy = sum(p.y for p in pts) / len(pts)
    z0 = sum(p.z for p in pts) / len(pts)
    # габарит ног и таза по углу на каждой высоте ниже подола
    NA = 36
    legs = [co[i] for i in range(len(co)) if co[i].z < z0 + 0.02 and co[i].z > z0 - drop - 0.05 and arms_w[i] < 0.05]
    def body_r(z, ang):
        best = 0.0
        for p in legs:
            if abs(p.z - z) < 0.02:
                a = math.atan2(p.y - cy, p.x - cx)
                if abs((a - ang + math.pi) % (2 * math.pi) - math.pi) < math.pi / NA * 1.5:
                    best = max(best, math.hypot(p.x - cx, p.y - cy))
        return best
    dl = bm.verts.layers.deform.verify()
    uvl = bm.loops.layers.uv.active
    n = max(1, int(math.ceil(drop / step)))
    edges = [e for e in bm.edges if e.is_boundary and e.verts[0] in hem and e.verts[1] in hem]
    cur = edges
    gl, gr = w_legs_l, w_legs_r   # индексы групп бёдер
    down = {}   # (грань, вершина) → (uv в этом углу, направление «вниз» в развёртке на метр)
    if uvl:
        for e in edges:
            for f in e.link_faces:
                for lp in f.loops:
                    v = lp.vert
                    if v not in e.verts:
                        continue
                    up = [l2 for l2 in (lp.link_loop_next, lp.link_loop_prev) if l2.vert not in e.verts]
                    if not up:
                        continue
                    a = up[0]
                    dw = (M @ v.co - M @ a.vert.co).length
                    d = (lp[uvl].uv - a[uvl].uv) / max(dw, 1e-4)
                    down[(e, v)] = (lp[uvl].uv.copy(), d)
    for k in range(1, n + 1):
        res = bmesh.ops.extrude_edge_only(bm, edges=cur)
        nv = [g for g in res['geom'] if isinstance(g, bmesh.types.BMVert)]
        ne = [g for g in res['geom'] if isinstance(g, bmesh.types.BMEdge) and all(v in nv for v in g.verts)]
        nf = [g for g in res['geom'] if isinstance(g, bmesh.types.BMFace)]
        t = k / n
        for v in nv:
            v[ext] = t
        for v in nv:
            p = M @ v.co
            a = math.atan2(p.y - cy, p.x - cx)
            r0 = math.hypot(p.x - cx, p.y - cy)
            z = z0 - drop * t
            r = max(r0 * (1.0 + flare * step / max(drop, 1e-3)), body_r(z, a) + ease)
            v.co = Mi @ Vector((cx + math.cos(a) * r, cy + math.sin(a) * r, z))
            side = gl if p.x > cx else gr
            mid = max(0.0, 1.0 - abs(p.x - cx) / 0.06)   # спереди и сзади посередине — поровну от обеих ног
            w = v[dl]
            keep = {g: wt * (1.0 - thigh * t) for g, wt in w.items()}
            w.clear()
            for g, wt in keep.items():
                w[g] = wt
            if mid > 0.0:
                w[gl] = w.get(gl, 0.0) + thigh * t * 0.5 * mid
                w[gr] = w.get(gr, 0.0) + thigh * t * 0.5 * mid
            w[side] = w.get(side, 0.0) + thigh * t * (1.0 - mid)
        if uvl:   # развёртка: каждая новая грань продолжает ткань грани над ней вниз (без полос растянутой текстуры)
            nxt = {}
            for f in nf:
                top = [e for e in f.edges if e in cur]
                if not top:
                    continue
                et = top[0]
                bot = [e for e in f.edges if e in ne]
                for lp in f.loops:
                    v = lp.vert
                    if v in et.verts:
                        key = (et, v)
                        if key in down:
                            lp[uvl].uv = down[key][0]
                for lp in f.loops:
                    v = lp.vert
                    if v in et.verts:
                        continue
                    above = [l2.vert for l2 in (lp.link_loop_next, lp.link_loop_prev) if l2.vert in et.verts]
                    if not above or (et, above[0]) not in down:
                        continue
                    uv0, d = down[(et, above[0])]
                    lp[uvl].uv = uv0 + d * (M @ v.co - M @ above[0].co).length
                    if bot:
                        nxt[(bot[0], v)] = (lp[uvl].uv.copy(), d)
            down = nxt
        cur = ne
    print('EXTEND', drop, n, 'колец')


def _loops(bm):
    """замкнутые края сетки: список (вершины, рёбра)"""
    seen, out = set(), []
    for e0 in [e for e in bm.edges if e.is_boundary]:
        if e0 in seen:
            continue
        vs, es, stack = set(), [], [e0]
        while stack:
            e = stack.pop()
            if e in seen:
                continue
            seen.add(e)
            es.append(e)
            for v in e.verts:
                vs.add(v)
                for e2 in v.link_edges:
                    if e2.is_boundary and e2 not in seen:
                        stack.append(e2)
        out.append((vs, es))
    return out


def _collar(bm, M, kind='turn', stand=0.03, fall=0.05, opening=0.014):
    """воротник из выреза горловины: стойка вверх (stand) и, у отложного (turn), отворот наружу и вниз на плечи (fall);
    спереди разрез под молнию или пуговицы (opening). Веса отворота — с шеи на грудь (не крутится вместе с головой)."""
    dl = bm.verts.layers.deform.verify()   # слой — до сбора вершин (новый слой обновляет ссылки на вершины)
    lps = _loops(bm)
    if not lps:
        return
    Mi = M.inverted()
    top_vs, top_es = max(lps, key=lambda L: sum((M @ v.co).z for v in L[0]) / len(L[0]))
    pts = [M @ v.co for v in top_vs]
    c = sum(pts, Vector()) / len(pts)
    def front(v):
        p = M @ v.co
        return p.y < c.y and abs(p.x - c.x) < opening
    sel = [e for e in top_es if not (front(e.verts[0]) and front(e.verts[1]))]
    def shift(v, k, gneck, gchest):   # доля веса шеи — на грудь
        w = v[dl]
        if gneck in w:
            m = w[gneck] * k
            w[gneck] = w[gneck] - m
            w[gchest] = w.get(gchest, 0.0) + m
    res = bmesh.ops.extrude_edge_only(bm, edges=sel)
    v1 = [g for g in res['geom'] if isinstance(g, bmesh.types.BMVert)]
    e1 = [g for g in res['geom'] if isinstance(g, bmesh.types.BMEdge) and all(v in v1 for v in g.verts)]
    for v in v1:
        p = M @ v.co
        rad = Vector((p.x - c.x, p.y - c.y, 0.0))
        rad = rad.normalized() if rad.length > 1e-6 else Vector((0, 0, 0))
        v.co = Mi @ (p + Vector((0, 0, stand)) - rad * 0.004)   # стойка — вверх, чуть к шее
    if kind == 'turn':
        res2 = bmesh.ops.extrude_edge_only(bm, edges=e1)
        v2 = [g for g in res2['geom'] if isinstance(g, bmesh.types.BMVert)]
        for v in v2:
            p = M @ v.co
            rad = Vector((p.x - c.x, p.y - c.y, 0.0))
            rad = rad.normalized() if rad.length > 1e-6 else Vector((0, 0, 0))
            v.co = Mi @ (p + rad * fall * 0.85 - Vector((0, 0, fall * 0.95)))   # отворот ложится на плечи и грудь
        return v1, v2, dl
    return v1, [], dl


def _world_bvh(ob):
    """дерево поиска по поверхности объекта в мировых координатах (после всех правок сетки)"""
    mw = ob.matrix_world
    vs = [mw @ v.co for v in ob.data.vertices]
    ps = [list(p.vertices) for p in ob.data.polygons]
    return BVHTree.FromPolygons(vs, ps), vs


def _weights_from(dst, src, kd, src_vs):
    """веса костей новой детали — от ближайшей вершины ткани (молния, карманы двигаются вместе с полой)"""
    for g in src.vertex_groups:
        if g.name not in dst.vertex_groups:
            dst.vertex_groups.new(name=g.name)
    gi = {g.index: g.name for g in src.vertex_groups}
    mw = dst.matrix_world
    for v in dst.data.vertices:
        _, j, _ = kd.find(mw @ v.co)
        for ge in src.data.vertices[j].groups:
            dst.vertex_groups[gi[ge.group]].add([v.index], ge.weight, 'REPLACE')


def _uv_density(ob):
    """сколько единиц развёртки на метр ткани (медиана по рёбрам граней): карманы и планки той же ткани кладутся
    с тем же масштабом — иначе текстура на них сжата в разы (рябь, «пиксели», серое пятно вместо меланжа)"""
    me = ob.data
    uv = me.uv_layers.active
    if not uv:
        return None
    mw = ob.matrix_world
    rs = []
    for poly in me.polygons[:4000]:
        li = list(poly.loop_indices)
        for a, b in zip(li, li[1:] + li[:1]):
            pa, pb = mw @ me.vertices[me.loops[a].vertex_index].co, mw @ me.vertices[me.loops[b].vertex_index].co
            d = (pa - pb).length
            if d > 1e-4:
                rs.append((uv.data[a].uv - uv.data[b].uv).length / d)
    if not rs:
        return None
    rs.sort()
    return rs[len(rs) // 2]


def _strip(target, name, path, width, lift, mat, segs_v=None, uv_tile=1.0, uvpm=None):
    """лента по поверхности ткани (молния, планка): path — точки (мир) по средней линии, ширина width, приподнята на lift"""
    bvh, tvs = _world_bvh(target)
    kd = KDTree(len(tvs))
    for i, p in enumerate(tvs):
        kd.insert(p, i)
    kd.balance()
    left, right, cen = [], [], []
    for k, p in enumerate(path):
        hit = bvh.find_nearest(p)
        if hit[0] is None:
            continue
        q, n = hit[0], hit[1].normalized()
        t = (path[min(k + 1, len(path) - 1)] - path[max(k - 1, 0)])
        t = (t - n * t.dot(n)).normalized()
        side = t.cross(n).normalized()
        cen.append((q + n * lift, side, n))
    me = bpy.data.meshes.new(name)
    vs, faces, uvs = [], [], []
    for k, (q, side, n) in enumerate(cen):
        vs += [q - side * width * 0.5, q + side * width * 0.5]
    for k in range(len(cen) - 1):
        a = 2 * k
        faces.append((a, a + 1, a + 3, a + 2))
    me.from_pydata(vs, [], faces)
    me.update()
    for poly in me.polygons:   # гладкое затенение (from_pydata даёт плоские грани — «кубики» света на ткани)
        poly.use_smooth = True
    uvl = me.uv_layers.new(name='UVMap')
    acc = [0.0]
    for k in range(1, len(cen)):
        acc.append(acc[-1] + (cen[k][0] - cen[k - 1][0]).length)
    for poly in me.polygons:
        for li in poly.loop_indices:
            vi = me.loops[li].vertex_index
            k, s2 = vi // 2, vi % 2
            if uvpm:   # ткань куртки — в масштабе самой куртки
                uvl.data[li].uv = (float(s2) * width * uvpm, acc[k] * uvpm)
            else:
                uvl.data[li].uv = (float(s2), acc[k] / max(width, 1e-4) * uv_tile)
    ob = bpy.data.objects.new(name, me)
    bpy.context.scene.collection.objects.link(ob)
    ob.parent = target.parent
    ob.matrix_world = target.matrix_world.copy()
    mi = ob.matrix_world.inverted()
    for v in me.vertices:
        v.co = mi @ v.co
    me.materials.append(mat)
    _weights_from(ob, target, kd, tvs)
    sol = ob.modifiers.new('solid', 'SOLIDIFY')
    sol.thickness = 0.0025
    sol.offset = 1.0
    sol.use_rim = True
    bpy.context.view_layer.objects.active = ob
    bpy.ops.object.modifier_apply(modifier='solid')
    ob.modifiers.new('arm', 'ARMATURE').object = _arm()
    return ob


def _patch(target, name, center, up, size, lift, mat, rot=0.0, n_u=6, n_v=4, uvpm=None):
    """заплатка по поверхности (карман, клапан, нашивка): центр (мир) у ткани, up — куда «верх» заплатки, size — (ш, в)"""
    bvh, tvs = _world_bvh(target)
    kd = KDTree(len(tvs))
    for i, p in enumerate(tvs):
        kd.insert(p, i)
    kd.balance()
    if center.y < -0.5:   # «спереди»: точка на ткани — лучом из-за спины зрителя (MakeHuman смотрит в −Y), а не ближайшая
        hit = bvh.ray_cast(Vector((center.x, -2.0, center.z)), Vector((0.0, 1.0, 0.0)))
    else:
        hit = bvh.find_nearest(center)
    if hit[0] is None:
        return None
    q0, n0 = hit[0], hit[1].normalized()
    u = (up - n0 * up.dot(n0)).normalized()
    r = u.cross(n0).normalized()
    ca, sa = math.cos(rot), math.sin(rot)
    u, r = u * ca + r * sa, r * ca - u * sa
    vs, faces, uvc = [], [], []
    for j in range(n_v + 1):
        for i in range(n_u + 1):
            a = (i / n_u - 0.5) * size[0]
            b = (j / n_v - 0.5) * size[1]
            p = q0 + r * a + u * b
            h = bvh.find_nearest(p)
            q, n = (h[0], h[1].normalized()) if h[0] is not None else (p, n0)
            vs.append(q + n * lift)
            uvc.append(((i / n_u) * size[0] * uvpm, (j / n_v) * size[1] * uvpm) if uvpm else (i / n_u, j / n_v))
    for j in range(n_v):
        for i in range(n_u):
            a = j * (n_u + 1) + i
            faces.append((a, a + 1, a + n_u + 2, a + n_u + 1))
    me = bpy.data.meshes.new(name)
    me.from_pydata(vs, [], faces)
    me.update()
    for poly in me.polygons:   # гладкое затенение (from_pydata даёт плоские грани — «кубики» света на ткани)
        poly.use_smooth = True
    uvl = me.uv_layers.new(name='UVMap')
    for poly in me.polygons:
        for li in poly.loop_indices:
            uvl.data[li].uv = uvc[me.loops[li].vertex_index]
    ob = bpy.data.objects.new(name, me)
    bpy.context.scene.collection.objects.link(ob)
    ob.parent = target.parent
    ob.matrix_world = target.matrix_world.copy()
    mi = ob.matrix_world.inverted()
    for v in me.vertices:
        v.co = mi @ v.co
    me.materials.append(mat)
    _weights_from(ob, target, kd, tvs)
    sol = ob.modifiers.new('solid', 'SOLIDIFY')
    sol.thickness = 0.003
    sol.offset = 1.0
    sol.use_rim = True
    bpy.context.view_layer.objects.active = ob
    bpy.ops.object.modifier_apply(modifier='solid')
    ob.modifiers.new('arm', 'ARMATURE').object = _arm()
    return ob


def _zip_material(name):
    """молния: тёмная тесьма, светлые металлические зубцы посередине (картинка — кодом)"""
    W, H = 32, 64
    img = bpy.data.images.new(name + '_img', W, H)
    px = []
    for y in range(H):
        for x in range(W):
            tooth = 0.36 < x / W < 0.64 and ((y // 4) % 2 == 0) == (x < W // 2)
            c = (0.55, 0.55, 0.57) if tooth else (0.05, 0.05, 0.055)
            px += [c[0], c[1], c[2], 1.0]
    img.pixels[:] = px
    img.pack()
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    bs = nt.nodes['Principled BSDF']
    t = nt.nodes.new('ShaderNodeTexImage')
    t.image = img
    t.interpolation = 'Closest'
    nt.links.new(t.outputs['Color'], bs.inputs['Base Color'])
    bs.inputs['Roughness'].default_value = 0.4
    bs.inputs['Metallic'].default_value = 0.35
    return m


def _front_path(ob, z_top, z_bot, step=0.01, dx=0.0):
    """средняя линия полы спереди (MakeHuman смотрит в −Y): точки на внешней поверхности ткани сверху вниз"""
    bvh, vs = _world_bvh(ob)
    near = [p for p in vs if abs(p.x) < 0.3]
    cx = sorted(p.x for p in near)[len(near) // 2] + dx if near else dx
    pts, z = [], z_top
    while z >= z_bot:
        hit = bvh.ray_cast(Vector((cx, -2.0, z)), Vector((0.0, 1.0, 0.0)))
        if hit[0] is not None:
            pts.append(hit[0])
        z -= step
    return pts, cx


def _trims(ob, g, name, neck_z, hem_z):
    """детали поверх ткани по описанию героя (g['trims']): zip — молния с бегунком, buttons — планка с пуговицами,
    pockets — welt (прорезные), flap (накладные с клапаном), kangaroo (карман худи); patches — нашивки"""
    tr = g.get('trims') or {}
    if not tr:
        return
    mat = ob.data.materials[0] if ob.data.materials else None
    uvpm = _uv_density(ob)
    z_top = neck_z - 0.025
    z_bot = hem_z - g.get('drop', 0.0) + 0.012
    if tr.get('zip'):
        path, cx = _front_path(ob, z_top, z_bot)
        if len(path) > 4:
            zm = _zip_material(name + '_zip_m')
            _strip(ob, name + '_zip', path, 0.022, 0.0012, zm, uv_tile=0.5)
            _patch(ob, name + '_pull', path[2] + Vector((0.0, -0.01, -0.02)), Vector((0, 0, 1)), (0.013, 0.032), 0.004, zm)
            print('TRIM zip', len(path))
    if tr.get('buttons'):
        path, cx = _front_path(ob, z_top - 0.02, z_bot, dx=0.012)
        if len(path) > 4 and mat:
            _strip(ob, name + '_placket', path, 0.045, 0.002, mat, uvpm=uvpm)
            bm = _button_material(name + '_button_m', tr.get('button_color', [0.05, 0.035, 0.025]))
            n = int(tr['buttons'])
            for k in range(n):
                q = path[int((k + 0.5) / n * (len(path) - 1))]
                _patch(ob, name + '_button%d' % k, q + Vector((0.0, -0.02, 0.0)), Vector((0, 0, 1)), (0.024, 0.024), 0.006, bm, n_u=3, n_v=3)
            print('TRIM buttons', n)
    pk = tr.get('pockets')
    if pk and mat:
        _, cx = _front_path(ob, z_top, z_top - 0.02)
        for sd in (-1.0, 1.0):
            if pk == 'welt':
                z = hem_z + 0.13
                _patch(ob, '%s_pocket%s' % (name, 'L' if sd > 0 else 'R'), Vector((cx + sd * 0.11, -1.0, z)), Vector((0, 0, 1)), (0.15, 0.02), 0.003, mat, rot=sd * 0.55, n_u=8, n_v=2, uvpm=uvpm)
            elif pk == 'flap':
                z = hem_z - g.get('drop', 0.0) + 0.16
                _patch(ob, '%s_pocket%s' % (name, 'L' if sd > 0 else 'R'), Vector((cx + sd * 0.12, -1.0, z)), Vector((0, 0, 1)), (0.17, 0.18), 0.003, mat, n_u=8, n_v=8, uvpm=uvpm)
                _patch(ob, '%s_flap%s' % (name, 'L' if sd > 0 else 'R'), Vector((cx + sd * 0.12, -1.0, z + 0.095)), Vector((0, 0, 1)), (0.18, 0.06), 0.007, mat, n_u=8, n_v=3, uvpm=uvpm)
        if pk == 'kangaroo':
            z = hem_z + 0.13
            _patch(ob, name + '_pocket', Vector((cx, -1.0, z)), Vector((0, 0, 1)), (0.30, 0.17), 0.003, mat, n_u=12, n_v=6, uvpm=uvpm)
        print('TRIM pockets', pk)
    for k, ptc in enumerate(tr.get('patches', [])):   # нашивки: [x, z, ш, в, цвет] спереди или "arm_l"
        img = _emblem(name + '_emb%d' % k, ptc.get('style', 'star'), ptc.get('color', [0.8, 0.2, 0.2]))
        em = bpy.data.materials.new(name + '_emb%d_m' % k)
        em.use_nodes = True
        e = em.node_tree.nodes['Principled BSDF']
        t = em.node_tree.nodes.new('ShaderNodeTexImage')
        t.image = img
        em.node_tree.links.new(t.outputs['Color'], e.inputs['Base Color'])
        e.inputs['Roughness'].default_value = 0.85
        a = _arm()
        if ptc.get('at') in ('arm_l', 'arm_r'):
            sd = ptc['at'][-1]
            h = a.matrix_world @ a.data.bones['upperarm_' + sd].head_local
            t2 = a.matrix_world @ a.data.bones['lowerarm_' + sd].head_local
            c = h.lerp(t2, 0.45)
            out = Vector((1.0 if sd == 'l' else -1.0, 0.0, 0.0))
            _patch(ob, '%s_patch%d' % (name, k), c + out * 0.12, (t2 - h).normalized() * -1.0, (ptc.get('w', 0.06), ptc.get('h', 0.06)), 0.002, em, n_u=4, n_v=4)
        else:
            _patch(ob, '%s_patch%d' % (name, k), Vector((ptc.get('x', 0.1), -1.0, neck_z - ptc.get('dz', 0.14))), Vector((0, 0, 1)), (ptc.get('w', 0.06), ptc.get('h', 0.05)), 0.002, em, n_u=4, n_v=4)
        print('TRIM patch', k)


def _button_material(name, color):
    """пуговица: круглая (вне круга — прозрачно), выпуклый ободок и четыре дырочки; блик пластика"""
    N = 64
    img = bpy.data.images.new(name + '_img', N, N, alpha=True)
    px = []
    for y in range(N):
        for x in range(N):
            u, v = (x + 0.5) / N * 2 - 1, (y + 0.5) / N * 2 - 1
            r = math.hypot(u, v)
            if r > 0.96:
                px += [0.0, 0.0, 0.0, 0.0]
                continue
            k = 1.0
            if r > 0.78:   # ободок — светлее по верхней кромке
                k = 1.35 + 0.35 * (-v)
            elif r < 0.62:
                k = 0.85
            for hx, hy in ((-0.22, -0.22), (0.22, -0.22), (-0.22, 0.22), (0.22, 0.22)):
                if math.hypot(u - hx, v - hy) < 0.11:
                    k = 0.25
            px += [min(1.0, color[0] * k), min(1.0, color[1] * k), min(1.0, color[2] * k), 1.0]
    img.pixels = px
    img.pack()
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    b = m.node_tree.nodes['Principled BSDF']
    t = m.node_tree.nodes.new('ShaderNodeTexImage')
    t.image = img
    m.node_tree.links.new(t.outputs['Color'], b.inputs['Base Color'])
    cut = m.node_tree.nodes.new('ShaderNodeMath')   # «больше 0,5» перед альфой — glTF выгружает вырезкой (MASK)
    cut.operation = 'GREATER_THAN'
    cut.inputs[1].default_value = 0.5
    m.node_tree.links.new(t.outputs['Alpha'], cut.inputs[0])
    m.node_tree.links.new(cut.outputs[0], b.inputs['Alpha'])
    b.inputs['Roughness'].default_value = 0.32
    return m


def _emblem(name, style, color):
    """нашивка: круг/звезда/полоса на ткани (картинка кодом, 128×128)"""
    N = 128
    img = bpy.data.images.new(name, N, N)
    px = []
    cr, cg, cb = color
    for y in range(N):
        for x in range(N):
            u, v = (x + 0.5) / N * 2 - 1, (y + 0.5) / N * 2 - 1
            r = math.hypot(u, v)
            base = (0.12, 0.12, 0.13)
            c = base
            if style == 'star':
                ang = math.atan2(v, u)
                rr = 0.55 + 0.25 * math.cos(5 * ang)
                if r < rr * 0.9:
                    c = (cr, cg, cb)
                if 0.86 < r < 0.95:
                    c = (0.85, 0.82, 0.7)
            elif style == 'stripes':
                c = (cr, cg, cb) if int((u + v + 2) * 4) % 2 == 0 else (0.9, 0.88, 0.8)
            else:
                c = (cr, cg, cb) if r < 0.75 else ((0.85, 0.82, 0.7) if r < 0.92 else base)
            px += [c[0], c[1], c[2], 1.0]
    img.pixels[:] = px
    img.pack()
    return img


def _flatten_edges(bm):
    """края одежды (манжеты, подол, воротник) — ровным кольцом: каждое — в свою плоскость (а не лесенкой по граням тела)"""
    import numpy as np
    bnd = {v for e in bm.edges if e.is_boundary for v in e.verts}
    seen = set()
    for v0 in bnd:
        if v0 in seen:
            continue
        loop, stack = [], [v0]
        seen.add(v0)
        while stack:
            v = stack.pop()
            loop.append(v)
            for e in v.link_edges:
                if e.is_boundary:
                    o = e.other_vert(v)
                    if o not in seen:
                        seen.add(o)
                        stack.append(o)
        if len(loop) < 8:
            continue
        P = np.array([tuple(v.co) for v in loop])
        c = P.mean(axis=0)
        w, U = np.linalg.eigh(np.cov((P - c).T))
        if math.sqrt(max(w[0], 0.0)) > 0.012:   # край и так не плоский (капюшон на спине) — не трогаем
            continue
        n = U[:, 0]
        for v, q in zip(loop, P):
            d = float(np.dot(q - c, n))
            v.co -= Vector(tuple(n * d))


# Ткань по физике (Blender Cloth): масса, жёсткость на растяжение/сжатие/сдвиг/изгиб, сопротивление воздуха.
# Кожа — тяжёлая и жёсткая на изгиб (крупные мягкие складки), трикотаж — лёгкий и мягкий (мелкие складки), пуховик — объёмный.
FABRIC = {
    'leather': dict(mass=0.45, tension=60, compression=60, shear=25, bending=40.0, air=1.5),
    'wool':    dict(mass=0.38, tension=25, compression=25, shear=12, bending=4.0, air=1.5),
    'cotton':  dict(mass=0.20, tension=15, compression=15, shear=6, bending=0.6, air=1.0),
    'canvas':  dict(mass=0.32, tension=28, compression=28, shear=12, bending=2.5, air=1.0),
    'puffer':  dict(mass=0.30, tension=20, compression=20, shear=8, bending=7.0, air=2.0),
    'knit':    dict(mass=0.30, tension=10, compression=10, shear=4, bending=0.4, air=1.0),
    'light':   dict(mass=0.15, tension=10, compression=10, shear=4, bending=0.15, air=1.0),
}


def _proxy(body):
    """гладкое тело для столкновений с тканью: без сосков, пупка и рёбер (иначе ткань собирается на них точечными
    защипами); чуть раздуто, чтобы сглаживание не сделало его тоньше настоящего"""
    dg = bpy.context.evaluated_depsgraph_get()
    me = bpy.data.meshes.new_from_object(body.evaluated_get(dg))
    ob = bpy.data.objects.new('lake_proxy', me)
    bpy.context.scene.collection.objects.link(ob)
    ob.matrix_world = body.matrix_world.copy()
    bm = bmesh.new()
    bm.from_mesh(me)
    for _ in range(12):
        bmesh.ops.smooth_vert(bm, verts=bm.verts, factor=0.5, use_axis_x=True, use_axis_y=True, use_axis_z=True)
    bm.normal_update()
    for v in bm.verts:
        v.co += v.normal * 0.004
    bm.to_mesh(me)
    bm.free()
    return ob


def _inner(ob, inset):
    """внутренняя форма одежды для столкновений: её же крой, сдвинутый внутрь на inset — ткань провисает под весом,
    но ложится на гладкую форму кроя, а не на грудь, пупок и рёбра тела"""
    me = ob.data.copy()
    o2 = bpy.data.objects.new('lake_inner', me)
    bpy.context.scene.collection.objects.link(o2)
    o2.matrix_world = ob.matrix_world.copy()
    for g in list(o2.vertex_groups):
        o2.vertex_groups.remove(g)
    bm = bmesh.new()
    bm.from_mesh(me)
    bm.normal_update()
    for _ in range(3):
        bmesh.ops.smooth_vert(bm, verts=bm.verts, factor=0.5, use_axis_x=True, use_axis_y=True, use_axis_z=True)
    bm.normal_update()
    for v in bm.verts:
        v.co -= v.normal * inset
    bm.to_mesh(me)
    bm.free()
    return o2


def _simulate(ob, body, pin, fabric='cotton', frames=48, grow=None, grow_max=0.0, self_col=True, inset=0.0):
    """ткань оседает под своим весом на теле (столкновение с кожей, закреплена там, где pin > 0) — складки и провисание
    настоящие, а не сглаженная копия тела; результат запекается в сетку (кости и веса не меняются)"""
    scn = bpy.context.scene
    prx = _inner(ob, inset) if inset > 0.0 else _proxy(body)
    col = prx.modifiers.new('lake_col', 'COLLISION')
    prx.collision.thickness_outer = 0.006
    prx.collision.thickness_inner = 0.02
    prx.collision.cloth_friction = 1.0
    prx.collision.damping = 0.3
    vg = ob.vertex_groups.get('lake_pin') or ob.vertex_groups.new(name='lake_pin')
    for i, w in enumerate(pin):
        if w > 0.0:
            vg.add([i], min(1.0, w), 'REPLACE')
    gg = None
    if grow and grow_max > 0.0:   # запас ткани: в этих местах ткань длиннее, чем нужно (рукава, низ) — собирается в складки
        gg = ob.vertex_groups.new(name='lake_grow')
        for i, w in enumerate(grow):
            if w > 0.0:
                gg.add([i], min(1.0, w), 'REPLACE')
    md = ob.modifiers.new('lake_cloth', 'CLOTH')
    st, p = md.settings, FABRIC[fabric]
    if gg:
        st.vertex_group_shrink = 'lake_grow'
        st.shrink_min = 0.0
        st.shrink_max = -grow_max
    st.quality = 8
    st.mass = p['mass']
    st.air_damping = p['air']
    st.tension_stiffness = p['tension']
    st.compression_stiffness = p['compression']
    st.shear_stiffness = p['shear']
    st.bending_stiffness = p['bending']
    st.vertex_group_mass = 'lake_pin'
    st.pin_stiffness = 1.0
    cs = md.collision_settings
    cs.collision_quality = 4
    cs.distance_min = 0.005
    cs.use_self_collision = self_col   # складки не проходят друг сквозь друга
    cs.self_distance_min = 0.003
    md.point_cache.frame_start = 1
    md.point_cache.frame_end = frames
    old = (scn.frame_start, scn.frame_end, scn.frame_current)
    scn.frame_start, scn.frame_end = 1, frames
    for f in range(1, frames + 1):
        scn.frame_set(f)
    dg = bpy.context.evaluated_depsgraph_get()
    ev = ob.evaluated_get(dg)
    co = [v.co.copy() for v in ev.data.vertices]
    ob.modifiers.remove(md)
    pme = prx.data
    bpy.data.objects.remove(prx, do_unlink=True)
    bpy.data.meshes.remove(pme)
    ob.vertex_groups.remove(vg)
    if gg:
        ob.vertex_groups.remove(gg)
    scn.frame_start, scn.frame_end = old[0], old[1]
    scn.frame_set(old[2])
    me = ob.data
    moved = 0.0
    if os.environ.get('LAKE_SIMDBG'):   # отладка: где ткань ушла дальше всего (мир, до → после)
        mw = ob.matrix_world
        top = sorted(range(len(co)), key=lambda i: -(me.vertices[i].co - co[i]).length)[:12]
        far = sum(1 for v, c in zip(me.vertices, co) if (v.co - c).length > 0.08)
        print('SIMDBG', ob.name, 'сдвиг > 8 см:', far, 'из', len(co))
        for i in top:
            a, b = mw @ me.vertices[i].co, mw @ co[i]
            print('SIMDBG  #%d  (%.3f %.3f %.3f) → (%.3f %.3f %.3f)  pin %.2f' % (i, a.x, a.y, a.z, b.x, b.y, b.z, pin[i]))
    for v, c in zip(me.vertices, co):
        moved = max(moved, (v.co - c).length)
        v.co = c
    me.update()
    print('SIM', ob.name, fabric, frames, 'кадров, наибольший сдвиг %.3f м' % moved)


def _islands_clean(bm, share=0.02):
    """вершины без граней и мелкие оторванные кусочки (несколько граней тела, попавших в выкройку) — прочь:
    в симуляции ткани им не за что держаться, и они падают сквозь тело"""
    loose = [v for v in bm.verts if not v.link_faces]
    if loose:
        bmesh.ops.delete(bm, geom=loose, context='VERTS')
    seen, isl = set(), []
    for v0 in bm.verts:
        if v0 in seen:
            continue
        part, stack = [], [v0]
        seen.add(v0)
        while stack:
            v = stack.pop()
            part.append(v)
            for e in v.link_edges:
                o = e.other_vert(v)
                if o not in seen:
                    seen.add(o)
                    stack.append(o)
        isl.append(part)
    total = max(1, len(bm.verts))
    small = [v for part in isl if len(part) < share * total for v in part]
    if small:
        bmesh.ops.delete(bm, geom=small, context='VERTS')
    if loose or small:
        print('CLEAN', len(loose), 'одиночных вершин,', len(small), 'вершин в мелких кусочках')


def _quilt(ob, pitch, depth):
    """простёжка пуховика: ткань втягивается по швам через pitch метров, между швами — выпуклые валики;
    на туловище швы горизонтальные, на рукавах — вокруг руки (по длине кости от плеча)"""
    me = ob.data
    mw = ob.matrix_world
    mi = mw.inverted()
    a = _arm()
    axes = {}
    for sd in ('l', 'r'):
        b0, b1 = a.data.bones.get('upperarm_' + sd), a.data.bones.get('hand_' + sd)
        if b0 and b1:
            h, t = a.matrix_world @ b0.head_local, a.matrix_world @ b1.head_local
            axes[sd] = (h, (t - h).normalized())
    gi = {g.name: g.index for g in ob.vertex_groups}
    bm = bmesh.new()
    bm.from_mesh(me)
    dl = bm.verts.layers.deform.verify()
    bm.normal_update()
    moves = []
    for v in bm.verts:
        w = v[dl]
        wl = w.get(gi.get('upperarm_l', -1), 0.0) + w.get(gi.get('lowerarm_l', -1), 0.0)
        wr = w.get(gi.get('upperarm_r', -1), 0.0) + w.get(gi.get('lowerarm_r', -1), 0.0)
        p = mw @ v.co
        sd = 'l' if wl >= wr else 'r'
        if max(wl, wr) > 0.5 and sd in axes:
            h, ax = axes[sd]
            par = (p - h).dot(ax)
        else:
            par = p.z
        ph = (par / pitch) % 1.0
        bump = math.sin(math.pi * ph) ** 0.6   # 0 на шве, 1 посередине валика
        nw = (mw.to_3x3() @ v.normal).normalized()
        moves.append((v, mi @ (p + nw * depth * (bump - 1.0))))
    for v, c in moves:
        v.co = c
    bm.to_mesh(me)
    bm.free()
    me.update()
    print('QUILT', pitch, depth)


def _shell(body, name, keep, offset, thick, smooth=3, center=None, back=0.0, drape=None, nsmooth=4, sim=None, pin_fn=None, grow_fn=None, grow_max=0.0, tailor=None, drop=0.0, self_col=True, neck=None, inset=0.0, frames=48, quilt=None):
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
    src = bm.verts.layers.int.new('src')   # номер вершины тела — для весов костей после удаления лишнего
    bm.verts.layers.float.new('lake_ext')   # глубина подола ниже бёдер (_extend); все слои — до словарей по вершинам
    bm.verts.layers.deform.verify()
    for v in bm.verts:
        v[src] = v.index
    bmesh.ops.delete(bm, geom=[v for v in bm.verts if not keep[v.index]], context='VERTS')
    _islands_clean(bm)
    bm.normal_update()
    nrm = {v: v.normal.copy() for v in bm.verts}
    for _ in range(nsmooth):   # нормали сглажены: мелкие формы тела (соски, пупок, рёбра) не дают на ткани бугорков и складок
        nn = {}
        for v in bm.verts:
            acc = nrm[v].copy()
            for e in v.link_edges:
                acc += nrm[e.other_vert(v)]
            nn[v] = acc.normalized() if acc.length > 1e-8 else nrm[v]
        nrm = nn
    inv = body.matrix_world.inverted()
    c_loc = inv @ center if center is not None else None
    orig = {v: (v.co.copy(), nrm[v]) for v in bm.verts}
    need = {}
    for v in bm.verts:
        extra = 0.0
        if c_loc is not None:   # капюшон: к затылку и макушке ткань отходит дальше (мешком), у лица — ближе
            d = (v.co - c_loc)
            dl = max(d.length, 1e-6)
            extra = back * (max(0.0, d.y / dl) * 0.9 + max(0.0, d.z / dl) * 0.5)
        v.co += nrm[v] * (offset + extra)
        need[v] = (offset + extra) * 0.85
    if tailor:   # крой коробом и трубами (tailor — словарь: co, веса, кости, запасы)
        _tailor(bm, body.matrix_world, src, **tailor)
        if drop > 0.0:
            vgs = ob.vertex_groups
            nb = len(bm.verts)
            _extend(bm, body.matrix_world, tailor['co'], vgs['thigh_l'].index, vgs['thigh_r'].index, drop, arms_w=[a + h for a, h in zip(tailor['w_arms'], tailor['w_hands'])])
            if os.environ.get('LAKE_SIMDBG'):
                print('SHELLDBG', name, 'до подола', nb, 'после', len(bm.verts))
    elif drape:
        _drape(bm, body.matrix_world, src, *drape)
    _flatten_edges(bm)
    for _ in range(smooth):
        bmesh.ops.smooth_vert(bm, verts=bm.verts, factor=0.5, use_axis_x=True, use_axis_y=True, use_axis_z=True)
        # сглаживание тянет ткань внутрь во впадинах (грудина) — не ближе 85 % отступа к телу, иначе рубашка проступает
        for v in bm.verts:
            if v not in orig:   # новые вершины (подол ниже бёдер) — без ограничения
                continue
            p0, n0 = orig[v]
            h = (v.co - p0).dot(n0)
            if h < need[v]:
                v.co += n0 * (need[v] - h)
    _flatten_edges(bm)
    for f in bm.faces:   # у части граней тела свой номер материала (соски, пупок) — на одежде без них были белые пятна
        f.material_index = 0
        f.smooth = True     # и все гладкие: новые грани подола выходят плоскими — свет ложится «кубиками»
    nv0 = len(bm.verts)
    bm.to_mesh(me)
    bm.free()
    if os.environ.get('LAKE_SIMDBG'):
        print('SHELLDBG', name, 'bmesh', nv0, 'mesh', len(me.vertices), 'faces', len(me.polygons))
    ob.data.materials.clear()
    if sim and pin_fn:   # ткань по физике — до толщины (симулируется одна поверхность)
        srcl = me.attributes.get('src')
        mw = ob.matrix_world
        idx = [srcl.data[i].value if srcl else i for i in range(len(me.vertices))]
        pin = [pin_fn(idx[i], mw @ v.co) for i, v in enumerate(me.vertices)]
        exl = me.attributes.get('lake_ext')
        if exl:   # подол ниже бёдер держит форму выкройки (свободным он поднимался и мялся о коллайдер), у пояса — мягче
            for i in range(len(pin)):
                t = exl.data[i].value
                if t > 0.0:
                    pin[i] = max(pin[i], min(1.0, 0.55 + t * 1.5))
        grow = [grow_fn(idx[i], mw @ v.co) for i, v in enumerate(me.vertices)] if grow_fn else None
        _simulate(ob, body, pin, sim, frames=frames, grow=grow, grow_max=grow_max, self_col=self_col, inset=inset)
    if sim and pin_fn:   # подол и манжеты после ткани — снова ровными кольцами (кожа и плотная ткань не волнятся по краю)
        bm = bmesh.new()
        bm.from_mesh(me)
        _flatten_edges(bm)
        bm.to_mesh(me)
        bm.free()
    if quilt:   # простёжка — после ткани по физике и до воротника и толщины
        _quilt(ob, quilt[0], quilt[1])
    if neck in ('turn', 'stand'):   # воротник — после ткани по физике (сама она его не мнёт), до толщины
        bm = bmesh.new()
        bm.from_mesh(me)
        r = _collar(bm, ob.matrix_world, neck)
        for f in bm.faces:
            f.smooth = True
        if r:
            v1, v2, dl = r
            gn = ob.vertex_groups.get('neck_01')
            gc = ob.vertex_groups.get('spine_03')
            if gn and gc:
                for v in v1:
                    w = v[dl]
                    if gn.index in w:
                        m = w[gn.index] * 0.5
                        w[gn.index] -= m
                        w[gc.index] = w.get(gc.index, 0.0) + m
                for v in v2:
                    w = v[dl]
                    if gn.index in w:
                        m = w[gn.index]
                        w[gn.index] = 0.0
                        w[gc.index] = w.get(gc.index, 0.0) + m
        bm.to_mesh(me)
        bm.free()
        me.update()
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
    w_lower = _w(body, ['lowerarm_l', 'lowerarm_r'])
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
            hem = hip - min(g.get('hem', 0.03), 0.09)
            keep = []
            for i in range(len(co)):
                arm = w_arms[i]
                ok = skin[i] and (w_torso[i] + arm) > 0.5 and w_hands[i] < 0.2 and w_head[i] < 0.45 and co[i].z > hem
                if kind == 'vest' and arm > 0.45:
                    ok = False
                if kind == 'jacket' and co[i].z > neck + 0.03 and w_head[i] > 0.2:   # воротник — чуть выше основания шеи
                    ok = False
                if g.get('open') and abs(co[i].x) < g['open'] and co[i].y < -0.02 and co[i].z < neck + 0.04:   # расстёгнута: полоса спереди (лицо — в −Y)
                    ok = False
                keep.append(ok)
            # воротник: немного шеи у основания
            for i in range(len(co)):
                if skin[i] and g.get('collar', True) and w_head[i] > 0.3 and neck - 0.02 < co[i].z < neck + 0.06 and w_headonly[i] < 0.3:
                    keep[i] = not (g.get('open') and abs(co[i].x) < g['open'] and co[i].y < -0.02)
            band_z = hem + 0.025   # пояс: нижние 2,5 см куртки держатся на бёдрах (резинка, пояс), ткань над ним напускается
            def pin_top(i, p, _neck=neck, _band=band_z, _belt=g.get('belt', True)):   # плечи и ворот держат куртку; манжеты и пояс — на месте
                fall = g.get('pin_fall', 0.08)
                top = 1.0 if p.z >= _neck - 0.06 else max(0.0, 1.0 - (_neck - 0.06 - p.z) / fall) ** 2
                cuff = 1.0 if w_hands[i] > 0.02 else 0.0
                belt = 1.0 if _belt and p.z < _band and w_arms[i] < 0.3 else 0.0
                return max(top, cuff, belt, 1.0 if w_head[i] > 0.15 else 0.0)
            def grow_mid(i, p, _band=band_z):   # запас ткани: предплечья — гармошка у манжет; полоса над поясом — напуск
                over = 0.5 * max(0.0, 1.0 - abs(p.z - (_band + 0.05)) / 0.05) if g.get('belt', True) else 0.0
                return max(w_lower[i], over * w_torso[i])
            tl = None
            if g.get('tailor'):
                tc = dict(g['tailor'])
                tl = dict(co=co, w_torso=w_torso, w_arms=w_arms, w_head=w_head, w_hands=w_hands, arm=_arm(), **tc)
            ob = _shell(body, name, keep, g.get('off', 0.018), g.get('thick', 0.005), smooth=g.get('smooth', 6), drape=(w_torso, w_arms, w_head, g.get('slope', 0.12), g.get('band', 0.5)), nsmooth=8,
                        sim=g.get('sim'), pin_fn=pin_top, grow_fn=grow_mid, grow_max=g.get('grow', 0.1), tailor=tl, drop=g.get('drop', 0.0), self_col=g.get('self_col', False),
                        neck=g.get('neck'), inset=g.get('inset', 0.0), frames=g.get('frames', 48), quilt=g.get('quilt'))
            ob.data.materials.append(_material(name + '_m', g['tex'], g.get('tint'), g.get('tile', 6.0), g.get('rough')))
            _trims(ob, g, name, neck, hem)
            print('GARMENT', name, kind, len(ob.data.vertices))
            hide |= _interior(body, keep, 1)
            covers.append((hem, kind == 'jacket', g.get('collar', True)))
            continue
        elif kind == 'hood' and g.get('down'):
            a = _arm()
            nb = a.matrix_world @ a.data.bones['neck_01'].head_local   # основание шеи
            keep = []
            for i in range(len(co)):
                d = co[i] - nb
                ring = -0.03 < d.z < 0.045 and math.hypot(d.x, d.y) < 0.12          # валик вокруг шеи
                pouch = d.y > -0.01 and -0.19 < d.z < 0.045 and abs(d.x) < 0.12     # мешок капюшона на спине (лицо — в −Y)
                keep.append(skin[i] and (w_torso[i] + w_head[i]) > 0.5 and w_headonly[i] < 0.25 and w_arms[i] < 0.3 and (ring or pouch))
            ob = _shell(body, name, keep, g.get('off', 0.034), g.get('thick', 0.007), smooth=8, center=nb + Vector((0.0, -0.03, -0.1)), back=g.get('back', 0.03))
        elif kind == 'hood':
            keep = []
            for i in range(len(co)):
                d = co[i] - center
                fwd = -d.y / max(d.length, 1e-6)   # лицо MakeHuman смотрит в −Y Blender
                face = fwd > 0.12 and co[i].z < brow + 0.05   # вырез для лица — шире, капюшон не «шлем»
                keep.append(skin[i] and w_head[i] > 0.5 and co[i].z > neck - 0.04 and not face)
            ob = _shell(body, name, keep, g.get('off', 0.035), g.get('thick', 0.006), smooth=5, center=center, back=g.get('back', 0.05))
        elif kind == 'scarf':   # шарф: плотный валик вокруг шеи поверх воротника, один конец — на груди
            a = _arm()
            nb = a.matrix_world @ a.data.bones['neck_01'].head_local
            keep = []
            for i in range(len(co)):
                d = co[i] - nb
                ring = -0.025 < d.z < 0.06 and math.hypot(d.x, d.y) < 0.12
                tail = d.y < -0.02 and -0.24 < d.z < -0.01 and 0.01 < d.x < 0.075   # конец свисает спереди (лицо — в −Y)
                keep.append(skin[i] and (w_torso[i] + w_head[i]) > 0.5 and w_headonly[i] < 0.3 and w_arms[i] < 0.3 and (ring or tail))
            ob = _shell(body, name, keep, g.get('off', 0.05), g.get('thick', 0.01), smooth=7, center=nb + Vector((0.0, 0.0, -0.06)))
        elif kind == 'beanie':
            cut = brow - g.get('low', 0.0)
            keep = [skin[i] and w_headonly[i] > 0.5 and co[i].z > cut for i in range(len(co))]
            ob = _shell(body, name, keep, g.get('off', 0.014), g.get('thick', 0.006), smooth=6, center=center, back=g.get('slouch', 0.02))
            band = [skin[i] and w_headonly[i] > 0.5 and cut < co[i].z < cut + 0.045 for i in range(len(co))]   # отворот — плотно, не «поля»
            ob2 = _shell(body, name + '_cuff', band, g.get('off', 0.014) + 0.004, 0.007, smooth=5)
            ob2.data.materials.append(_material(name + '_cuff_m', g['tex'], g.get('tint'), g.get('tile', 6.0), g.get('rough')))
        else:
            continue
        ob.data.materials.append(_material(name + '_m', g['tex'], g.get('tint'), g.get('tile', 6.0), g.get('rough')))
        print('GARMENT', name, kind, len(ob.data.vertices))
    _hide_skin(body, hide)
    _hide_under(body, covers, w_torso, w_arms, w_head, w_headonly, neck)


def _light_faces(o):
    """вершины одежды MakeHuman, у которых по текстуре светлая малонасыщенная ткань (рубашка, футболка), а не джинсы"""
    out = set()
    me = o.data
    img = None
    for sl in o.material_slots:
        if sl.material and sl.material.use_nodes:
            for n in sl.material.node_tree.nodes:
                if n.type == 'TEX_IMAGE' and n.image and 'diffuse' in n.image.name.lower():
                    img = n.image
    if img is None or not img.size[0] or not me.uv_layers:
        return out
    W, H = img.size
    px = img.pixels[:]
    uvl = me.uv_layers.active.data
    for poly in me.polygons:
        for li in poly.loop_indices:
            u, v = uvl[li].uv
            x = min(W - 1, max(0, int(u % 1.0 * W)))
            y = min(H - 1, max(0, int(v % 1.0 * H)))
            k = (y * W + x) * 4
            r, g, b = px[k], px[k + 1], px[k + 2]
            mx, mn = max(r, g, b), min(r, g, b)
            if mx > 0.45 and (mx - mn) / max(mx, 1e-4) < 0.25:
                out.add(me.loops[li].vertex_index)
    return out


def _hide_under(body, covers, w_torso, w_arms, w_head, w_headonly, neck):
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
        light = _light_faces(o)   # грани рубашки (светлая, малонасыщенная ткань), а не джинсов
        for v in o.data.vertices:
            p = o.matrix_world @ v.co
            _, idx, dist = kd.find(p)
            for hem, sleeves, collar in covers:
                if v.index in light and hem - 0.10 < p.z <= hem + 0.012 and w_torso[idx] > 0.3:   # край рубашки под подолом — заправлен
                    under.add(v.index)
                    break
                if p.z > hem + 0.012 and (w_torso[idx] > 0.4 or (sleeves and w_arms[idx] > 0.4)) or \
                        collar and w_head[idx] > 0.3 and w_headonly[idx] < 0.3 and p.z < neck + 0.05:   # ворот футболки под воротником
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
