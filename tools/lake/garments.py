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
"""
import bpy, bmesh, os, math, bisect
from mathutils import Vector

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
    px = list(img.pixels)
    for i in range(0, len(px), 4):
        px[i] *= tint[0]
        px[i + 1] *= tint[1]
        px[i + 2] *= tint[2]
    img.pixels[:] = px
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


def _shell(body, name, keep, offset, thick, smooth=3, center=None, back=0.0, drape=None, nsmooth=4):
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
    for v in bm.verts:
        v[src] = v.index
    bmesh.ops.delete(bm, geom=[v for v in bm.verts if not keep[v.index]], context='VERTS')
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
    orig = {v.index: (v.co.copy(), nrm[v]) for v in bm.verts}
    need = {}
    for v in bm.verts:
        extra = 0.0
        if c_loc is not None:   # капюшон: к затылку и макушке ткань отходит дальше (мешком), у лица — ближе
            d = (v.co - c_loc)
            dl = max(d.length, 1e-6)
            extra = back * (max(0.0, d.y / dl) * 0.9 + max(0.0, d.z / dl) * 0.5)
        v.co += nrm[v] * (offset + extra)
        need[v.index] = (offset + extra) * 0.85
    if drape:
        _drape(bm, body.matrix_world, src, *drape)
    _flatten_edges(bm)
    for _ in range(smooth):
        bmesh.ops.smooth_vert(bm, verts=bm.verts, factor=0.5, use_axis_x=True, use_axis_y=True, use_axis_z=True)
        # сглаживание тянет ткань внутрь во впадинах (грудина) — не ближе 85 % отступа к телу, иначе рубашка проступает
        for v in bm.verts:
            p0, n0 = orig[v.index]
            h = (v.co - p0).dot(n0)
            if h < need[v.index]:
                v.co += n0 * (need[v.index] - h)
    _flatten_edges(bm)
    for f in bm.faces:   # у части граней тела свой номер материала (соски, пупок) — на одежде без них были белые пятна
        f.material_index = 0
    bm.to_mesh(me)
    bm.free()
    ob.data.materials.clear()
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
            hem = hip - min(g.get('hem', 0.03), 0.035)
            keep = []
            for i in range(len(co)):
                arm = w_arms[i]
                ok = skin[i] and (w_torso[i] + arm) > 0.5 and w_hands[i] < 0.2 and w_head[i] < 0.45 and co[i].z > hem
                if kind == 'vest' and arm > 0.45:
                    ok = False
                if kind == 'jacket' and co[i].z > neck + 0.03 and w_head[i] > 0.2:   # воротник — чуть выше основания шеи
                    ok = False
                keep.append(ok)
            # воротник: немного шеи у основания
            for i in range(len(co)):
                if skin[i] and g.get('collar', True) and w_head[i] > 0.3 and neck - 0.02 < co[i].z < neck + 0.06 and w_headonly[i] < 0.3:
                    keep[i] = True
            ob = _shell(body, name, keep, g.get('off', 0.018), g.get('thick', 0.005), smooth=g.get('smooth', 6), drape=(w_torso, w_arms, w_head, g.get('slope', 0.12), g.get('band', 0.5)), nsmooth=8)
            hide |= _interior(body, keep, 1)
            covers.append((hem, kind == 'jacket', g.get('collar', True)))
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
        elif kind == 'beanie':
            cut = brow - g.get('low', 0.0)
            keep = [skin[i] and w_headonly[i] > 0.5 and co[i].z > cut for i in range(len(co))]
            ob = _shell(body, name, keep, g.get('off', 0.014), g.get('thick', 0.006), smooth=4)
            band = [skin[i] and w_headonly[i] > 0.5 and cut < co[i].z < cut + 0.038 for i in range(len(co))]
            ob2 = _shell(body, name + '_cuff', band, g.get('off', 0.014) + 0.008, 0.006, smooth=4)
            ob2.data.materials.append(_material(name + '_cuff_m', g['tex'], g.get('tint'), g.get('tile', 6.0), g.get('rough')))
        else:
            continue
        ob.data.materials.append(_material(name + '_m', g['tex'], g.get('tint'), g.get('tile', 6.0), g.get('rough')))
        print('GARMENT', name, kind, len(ob.data.vertices))
    _hide_skin(body, hide)
    _hide_under(body, covers, w_torso, w_arms, w_head, w_headonly, neck)


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
        for v in o.data.vertices:
            p = o.matrix_world @ v.co
            _, idx, dist = kd.find(p)
            for hem, sleeves, collar in covers:
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
