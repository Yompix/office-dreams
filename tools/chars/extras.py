"""Дополнения генератора героев Night Shift (tools/chars/gen.py) — всё кодом из CC0-ассетов MakeHuman и свободного шрифта (OFL),
без чужих готовых моделей. Ключи в описании героя (CHARS в gen.py):
  subdiv  — ["часть имени одежды", ...]: подразделить грубую одежду (у карго MakeHuman 211 вершин — колени были гранёными)
  loose   — {"часть имени": метры}: одежда свободнее — отодвинуть наружу по нормалям (мешковатые карго)
  hide    — ["часть имени", ...]: удалить кожу под этой одеждой (у брюк MakeHuman нет delete_verts — ноги протыкали ткань)
  recolor — {"часть имени картинки": {"rgb": [r, g, b], "gamma": 1, "heather": 0..1}}: перекрасить текстуру целиком по яркости
            (складки остаются), heather — меланж (светлые и тёмные волокна трикотажа)
  emb     — [{"mesh", "img", "text", "font", "at": [x, z], "width", "rgb"}]: «вышивка» надписи на ткани спереди (x, z — мир, м)
  beard   — {"len", "rgb", ...}: борода объёмом — два слоя по коже лица (копия граней тела с весами костей и лицевыми формами,
            отодвинутая наружу), волоски — в своей текстуре по направлению роста; под бородой кожа темнее (щетина)
Порядок: pre(C) — после удаления вспомогательной геометрии (шаг 2 gen.py), post(C) — после имён частей (шаг 4)."""
import bpy, bmesh, os, math, subprocess, tempfile
import numpy as np
from mathutils import Vector
from mathutils.bvhtree import BVHTree
from mathutils.geometry import barycentric_transform

HERE = os.path.dirname(os.path.abspath(__file__))
LUM = np.array([.3, .59, .11], np.float32)
RNG = np.random.default_rng(7)


def _meshes():
    return [o for o in bpy.data.objects if o.type == 'MESH']


def _find(key):
    return next((o for o in _meshes() if key in o.name), None)


def _body():
    from bl_ext.user_default.mpfb.services import ObjectService
    return _find('skin') or next(o for o in _meshes() if ObjectService.get_object_type(o) == 'Basemesh')


def _co(o):
    a = np.empty(len(o.data.vertices) * 3, np.float32); o.data.vertices.foreach_get('co', a); return a.reshape(-1, 3)


def _world(o, local=None):
    M = np.array(o.matrix_world, np.float32); c = _co(o) if local is None else local
    return c @ M[:3, :3].T + M[:3, 3]


def _normals(o):
    o.data.update(); a = np.empty(len(o.data.vertices) * 3, np.float32); o.data.vertices.foreach_get('normal', a); return a.reshape(-1, 3)


def _bvh(o):
    M = o.matrix_world; me = o.data; me.calc_loop_triangles()
    return BVHTree.FromPolygons([M @ v.co for v in me.vertices], [t.vertices[:] for t in me.loop_triangles]), me.loop_triangles


def _img_get(img):
    a = np.empty(img.size[0] * img.size[1] * 4, np.float32); img.pixels.foreach_get(a); return a.reshape(img.size[1], img.size[0], 4)


def _img_set(img, a):
    img.pixels.foreach_set(np.ascontiguousarray(a, np.float32).ravel()); img.update()


def _image(key):
    return next((i for i in bpy.data.images if key in i.name and i.size[0]), None)


def _ss(e0, e1, v):
    t = np.clip((v - e0) / (e1 - e0), 0, 1); return t * t * (3 - 2 * t)


def _blur(a, r):
    """размытие (3 прохода ящиком) — по осям 0 и 1"""
    k = max(1, int(r)); out = a.astype(np.float32)
    for _ in range(3):
        for ax in (0, 1):
            pad = [(0, 0)] * out.ndim; pad[ax] = (k + 1, k)
            c = np.cumsum(np.pad(out, pad, mode='edge'), axis=ax)
            hi = [slice(None)] * out.ndim; lo = [slice(None)] * out.ndim
            hi[ax] = slice(2 * k + 1, None); lo[ax] = slice(None, -2 * k - 1)
            out = (c[tuple(hi)] - c[tuple(lo)]) / (2 * k + 1)
    return out


def _raster(P, V, W, H):
    """треугольники P (n, 3, 2) в пикселях (x вправо, y вверх — как строки img.pixels) со значениями V (n, 3, k) →
       картинка (H, W, k) и маска покрытия"""
    out = np.zeros((H, W, V.shape[2]), np.float32); cov = np.zeros((H, W), bool)
    for t in range(len(P)):
        p = P[t]; lo = np.floor(p.min(0)).astype(int); hi = np.ceil(p.max(0)).astype(int)
        x0, y0 = max(lo[0], 0), max(lo[1], 0); x1, y1 = min(hi[0], W - 1), min(hi[1], H - 1)
        if x1 < x0 or y1 < y0:
            continue
        xs, ys = np.meshgrid(np.arange(x0, x1 + 1) + .5, np.arange(y0, y1 + 1) + .5)
        (ax, ay), (bx, by), (cx, cy) = p
        den = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
        if abs(den) < 1e-12:
            continue
        l1 = ((by - cy) * (xs - cx) + (cx - bx) * (ys - cy)) / den
        l2 = ((cy - ay) * (xs - cx) + (ax - cx) * (ys - cy)) / den
        l3 = 1 - l1 - l2; m = (l1 > -1e-3) & (l2 > -1e-3) & (l3 > -1e-3)
        if not m.any():
            continue
        val = l1[..., None] * V[t, 0] + l2[..., None] * V[t, 1] + l3[..., None] * V[t, 2]
        sub = out[y0:y1 + 1, x0:x1 + 1]; sub[m] = val[m]; cov[y0:y1 + 1, x0:x1 + 1] |= m
    return out, cov


# ---------- до позы покоя: одежда и кожа под ней ----------
def subdiv(keys, levels=1):
    for o in _meshes():
        if not any(k in o.name for k in keys) or o.data.shape_keys:
            continue
        n0 = len(o.data.vertices)
        m = o.modifiers.new('sub', 'SUBSURF'); m.levels = m.render_levels = levels; m.uv_smooth = 'PRESERVE_BOUNDARIES'
        with bpy.context.temp_override(object=o, active_object=o, selected_objects=[o], selected_editable_objects=[o]):
            bpy.ops.object.modifier_move_to_index(modifier='sub', index=0)
            bpy.ops.object.modifier_apply(modifier='sub')
        print('SUBDIV', o.name, n0, '->', len(o.data.vertices))


def loose(spec):
    """{"часть имени": метры} или {"часть имени": [метры, высота_до, высота_от]} — у пояса не раздувать (выше «высота_от»
       сдвиг ноль, ниже «высота_до» полный; высоты — от таза, м): иначе пояс брюк вылезал сквозь низ рубашки"""
    arm = next(a for a in bpy.data.objects if a.type == 'ARMATURE'); pz = (arm.matrix_world @ arm.data.bones['pelvis'].head_local).z
    for key, d in spec.items():
        o = _find(key)
        if not o:
            continue
        d, z0, z1 = (d, None, None) if not isinstance(d, (list, tuple)) else d
        s = o.matrix_world.to_scale().x; k = np.ones(len(o.data.vertices), np.float32)
        if z0 is not None:
            k = _ss(pz + z1, pz + z0, _world(o)[:, 2])
        co = _co(o) + _normals(o) * (d * k / s)[:, None]; o.data.vertices.foreach_set('co', co.ravel()); o.data.update()
        print('LOOSE', o.name, d)


def hide(keys, reach=.06):
    body = _body(); co = _world(body); M3 = np.array(body.matrix_world, np.float32)[:3, :3]
    n = _normals(body) @ M3.T; n /= np.maximum(np.linalg.norm(n, axis=1, keepdims=True), 1e-9)
    trees = [_bvh(o)[0] for o in (_find(k) for k in keys) if o]
    cov = np.zeros(len(co), bool)
    for i in range(len(co)):
        p, d = Vector(co[i] + n[i] * .001), Vector(n[i])
        cov[i] = any(t.ray_cast(p, d, reach)[0] is not None for t in trees)
    bm = bmesh.new(); bm.from_mesh(body.data); bm.faces.ensure_lookup_table()
    dead = [f for f in bm.faces if all(cov[v.index] for v in f.verts)]
    bmesh.ops.delete(bm, geom=dead, context='FACES_ONLY'); bm.to_mesh(body.data); bm.free(); body.data.update()
    print('HIDE', keys, 'кожа под одеждой: граней', len(dead))


def pre(C):
    if C.get('subdiv'):
        subdiv(C['subdiv'])
    if C.get('loose'):
        loose(C['loose'])
    if C.get('hide'):
        hide(C['hide'])


# ---------- после имён частей: цвет, надписи, борода ----------
def recolor(spec):
    for key, s in spec.items():
        for img in [i for i in bpy.data.images if key in i.name and i.size[0]]:
            a = _img_get(img); c = a[..., :3]; lum = c @ LUM
            ok = lum > .012   # чёрный фон развёртки не трогаем
            base = float(np.median(lum[ok])) if ok.any() else .3
            rel = np.clip(lum / max(base, 1e-4), 0, 4) ** s.get('gamma', 1.0)
            new = rel[..., None] * np.array(s['rgb'], np.float32)
            if s.get('heather'):   # меланж: часть волокон светлее, часть темнее; нить идёт по ряду трикотажа (вдоль строки)
                h = RNG.random(lum.shape, np.float32)
                f = np.where(h > .74, 1.0, np.where(h < .32, -.55, 0.0)).astype(np.float32)
                f = .6 * f + .4 * _blur(f, 1)
                new *= (1 + s['heather'] * f)[..., None]
            c[ok] = np.clip(new, 0, 1)[ok]; _img_set(img, a)
            print('RECOLOR', img.name, s['rgb'], 'base lum', round(base, 3))


def _uv_at(o, tree, lts, org, dirn):
    hit = tree.ray_cast(Vector(org), Vector(dirn))
    if hit[0] is None:
        return None
    loc, _, ti, _ = hit; lt = lts[ti]; M = o.matrix_world
    ps = [M @ o.data.vertices[i].co for i in lt.vertices]; uvl = o.data.uv_layers.active.data
    q = barycentric_transform(loc, *ps, *[uvl[li].uv.to_3d() for li in lt.loops])
    return np.array([q.x, q.y], np.float64)


def _text_mask(font, text, px):
    out = os.path.join(tempfile.mkdtemp(), 'text.png')
    subprocess.run(['python3', os.path.join(HERE, 'text_mask.py'), os.path.join(HERE, font), text, str(px), out], check=True)
    im = bpy.data.images.load(out); a = _img_get(im)[..., 0].copy(); bpy.data.images.remove(im)
    return a   # строки снизу вверх (как img.pixels)


def emb(specs):
    for s in specs:
        o = _find(s['mesh']); img = _image(s['img'])
        if not o or not img:
            print('EMB нет', s['mesh'], s['img']); continue
        tree, lts = _bvh(o); x, z = s['at']; e = .004
        if s.get('ref'):   # высота от кости (ключица): надпись встаёт на грудь при любом росте
            arm = next(a for a in bpy.data.objects if a.type == 'ARMATURE'); z += (arm.matrix_world @ arm.data.bones[s['ref']].head_local).z
        uv0 = _uv_at(o, tree, lts, (x, -2, z), (0, 1, 0))
        ux = _uv_at(o, tree, lts, (x + e, -2, z), (0, 1, 0)); uz = _uv_at(o, tree, lts, (x, -2, z + e), (0, 1, 0))
        if uv0 is None or ux is None or uz is None:
            print('EMB промах', s); continue
        J = np.stack([(ux - uv0) / e, (uz - uv0) / e], 1)   # d(uv)/d(x, z) у груди — плоско на 8 см
        W, H = img.size
        mask = _text_mask(s.get('font', 'fonts/Allura-Regular.ttf'), s['text'], 220)
        th, tw = mask.shape; wm = s['width']; hm = wm * th / tw   # размер надписи в метрах
        corners = [uv0 + J @ np.array([a * wm / 2, b * hm / 2]) for a in (-1, 1) for b in (-1, 1)]
        cu = np.array(corners) * [W, H]; x0, y0 = np.floor(cu.min(0)).astype(int) - 2; x1, y1 = np.ceil(cu.max(0)).astype(int) + 2
        xs, ys = np.meshgrid(np.arange(x0, x1 + 1) + .5, np.arange(y0, y1 + 1) + .5)
        Ji = np.linalg.inv(J)
        q = np.stack([xs / W - uv0[0], ys / H - uv0[1]], -1) @ Ji.T   # → метры (a вдоль надписи, b вверх)
        tx = (q[..., 0] / wm + .5) * (tw - 1); ty = (q[..., 1] / hm + .5) * (th - 1)
        def samp(m, tx, ty):
            ix = np.clip(np.floor(tx).astype(int), 0, tw - 2); iy = np.clip(np.floor(ty).astype(int), 0, th - 2); fx = np.clip(tx - ix, 0, 1); fy = np.clip(ty - iy, 0, 1)
            v = m[iy, ix] * (1 - fx) * (1 - fy) + m[iy, ix + 1] * fx * (1 - fy) + m[iy + 1, ix] * (1 - fx) * fy + m[iy + 1, ix + 1] * fx * fy
            inside = (tx >= 0) & (tx <= tw - 1) & (ty >= 0) & (ty <= th - 1)
            return np.where(inside, v, 0)
        al = samp(mask, tx, ty)
        sh = samp(_blur(mask[..., None], 3)[..., 0], tx - 4, ty + 4)   # тень нити (вышивка выпуклая)
        a = _img_get(img); sub = a[y0:y1 + 1, x0:x1 + 1, :3]
        sub *= (1 - .45 * sh)[..., None]
        thread = np.array(s.get('rgb', (.9, .9, .88)), np.float32) * (.9 + .1 * np.sin(q[..., 0] * 2400 + q[..., 1] * 1600))[..., None]   # гладь: косые стежки
        sub[:] = sub * (1 - al[..., None]) + thread * al[..., None]
        _img_set(img, a)
        nimg = _image(s['img'].replace('Base_Color', 'Normal')) if 'Base_Color' in s['img'] else None
        if nimg and nimg.size[:] == img.size[:]:   # рельеф нити в карте нормалей
            n = _img_get(nimg); gy, gx = np.gradient(_blur(al[..., None], 1)[..., 0])
            ns = n[y0:y1 + 1, x0:x1 + 1]; ns[..., 0] = np.clip(ns[..., 0] - gx * 1.2, 0, 1); ns[..., 1] = np.clip(ns[..., 1] - gy * 1.2, 0, 1); _img_set(nimg, n)
        print('EMB', s['text'], 'uv', uv0.round(4), 'px', (x1 - x0, y1 - y0), 'в', img.name)


def _head_frame(body):
    E = _world(_find('eyes')); O = (E[E[:, 0] > 0].mean(0) + E[E[:, 0] < 0].mean(0)) / 2
    T = _world(_find('teeth')); mz = T[:, 2].min() + .55 * np.ptp(T[:, 2]) - O[2]
    rel = _world(body) - O; mid = np.abs(rel[:, 0]) < .006
    nose = rel[mid & (rel[:, 2] < -.01) & (rel[:, 2] > -.07)]; tip = nose[nose[:, 1].argmin()]
    chin = rel[mid & (rel[:, 2] < mz - .02) & (rel[:, 2] > -.16)]; chin_f = chin[chin[:, 1].argmin()]
    band = rel[(rel[:, 2] > -.035) & (rel[:, 2] < .01) & (rel[:, 1] > .04) & (rel[:, 1] < .16)]   # ухо: дальше всех вбок на высоте глаз
    rim = band[np.abs(band[:, 0]).argmax()]; ear_d = float(rim[1]) - .022   # перед уха (козелок) — на ~2 см ближе к лицу, чем край
    L = dict(O=O, mz=mz, tip=tip, chin=chin_f, ear_d=ear_d)
    print('HEAD', {k: (np.round(v, 4).tolist() if hasattr(v, 'shape') else round(v, 4)) for k, v in L.items()})
    return rel, L


def beard_weights(rel, L, s):
    """вес бороды по вершинам кожи (0..1) и длина волоса (м). Границы — как у ухоженной полной бороды: щёчная линия от
       бакенбарда (у уха, до волос) вниз к краю усов, усы от носа до губы, губы открыты, снизу — линия под челюстью"""
    x, z, d = np.abs(rel[:, 0]), rel[:, 2], rel[:, 1]
    phi = np.degrees(np.arctan2(x, -(d - s.get('axis', .06))))   # 0 — спереди, 90 — сбоку (у уха)
    zsn = L['tip'][2] - s.get('sn', .012)   # под носом
    PH = [0, 16, 23, 34, 55, 75, 85, 91, 97]
    top = np.interp(phi, PH, [zsn - .002, zsn - .003, zsn - .014] + s.get('cheek', [-.058, -.047, -.034, -.019, .0, .014]))   # щёчная линия → бакенбард до волос
    bot = np.interp(phi, [0, 40, 70, 90, 100], s.get('neck', [-.128, -.124, -.112, -.096, -.09]))
    w = _ss(top + .002, top - .006, z) * _ss(bot - .004, bot + .004, z)
    w *= _ss(L['ear_d'] + .004, L['ear_d'] - .006, d) * _ss(100, 92, phi)
    zm = L['mz'] + s.get('mz', 0); lw, lu, ld = s.get('lips', (.025, .0068, .0088))
    r = np.sqrt((x / lw) ** 2 + ((z - zm) / np.where(z > zm, lu, ld)) ** 2)
    w *= np.where(d < .01, _ss(.98, 1.12, r), 1)   # губы без бороды (вырез точно по губам — шире был светлый ободок кожи)
    w[(z > .03) | (z < -.2)] = 0
    chin = _ss(L['chin'][2] + .025, L['chin'][2] - .005, z) * _ss(55, 25, phi)
    ln = s.get('len', .008) * (1 + .5 * chin) * (1 - .5 * _ss(60, 92, phi)) * np.where((z > zm) & (phi < 26), .85, 1)
    return w, ln


def hair(s):
    """волосам объём (зачёс назад с объёмом сверху: сдвиг от центра головы, больше всего — надо лбом и на макушке) и
       кожа головы под волосами в цвет волос (между прядями не светит лысина)"""
    o = _find('hair'); body = _body()
    if not o:
        return
    rel, L = _head_frame(body); O = L['O']; c = np.array([0, .035, .015], np.float32)
    co = _world(o) - O; top = co[:, 2].max(); v = co - c; r = np.linalg.norm(v, axis=1, keepdims=True); u = v / np.maximum(r, 1e-6)
    hgt = _ss(.0, top - .01, co[:, 2]); front = _ss(.03, -.06, co[:, 1]) * _ss(-.01, .05, co[:, 2]); side = _ss(.045, .075, np.abs(co[:, 0]))
    back = _ss(.06, .14, co[:, 1]) * _ss(-.06, .02, co[:, 2])   # затылок: объём назад (зачёс)
    vol = s.get('volume', {}); amt = hgt * vol.get('top', .01) + front * vol.get('front', .012) - side * hgt * vol.get('side', .0) + back * vol.get('back', .0)
    off = u * amt[:, None]; s3 = o.matrix_world.to_scale().x; M3 = np.array(o.matrix_world.inverted(), np.float32)[:3, :3]
    offl = off @ M3.T
    if o.data.shape_keys:
        for kb in o.data.shape_keys.key_blocks:
            a = np.empty(len(kb.data) * 3, np.float32); kb.data.foreach_get('co', a); kb.data.foreach_set('co', (a.reshape(-1, 3) + offl).ravel())
    o.data.vertices.foreach_set('co', (_co(o) + offl).ravel()); o.data.update()
    print('HAIR объём, макс см', round(float(amt.max()) * 100, 2))
    if s.get('lift', {}).get('fringe'):   # чёлка выше (Айрис, решение редактора 6 ноя: «приподнять на 1–2 см, сохранив причёску»)
        _fringe_lift(o, body, O, float(s['lift']['fringe']))
    if s.get('scalp'):   # кожа под волосами — в цвет волос (луч от кожи наружу упирается в волосы)
        bco = _world(body); M3b = np.array(body.matrix_world, np.float32)[:3, :3]; n = _normals(body) @ M3b.T; n /= np.maximum(np.linalg.norm(n, axis=1, keepdims=True), 1e-9)
        tree = _bvh(o)[0]; near = (bco[:, 2] > O[2] - .06)
        hit = np.zeros(len(bco), np.float32)
        for i in np.nonzero(near)[0]:
            h = tree.ray_cast(Vector(bco[i] + n[i] * .0005), Vector(n[i]), .05)
            hit[i] = 1.0 if h[0] is not None else 0.0
        me = body.data; me.calc_loop_triangles(); uvl = me.uv_layers.active
        luv = np.empty(len(me.loops) * 2, np.float32); uvl.data.foreach_get('uv', luv); luv = luv.reshape(-1, 2)
        lts = [t for t in me.loop_triangles if hit[list(t.vertices)].max() > 0]
        simg = next((nd.image for m in me.materials if m and m.use_nodes for nd in m.node_tree.nodes if nd.type == 'TEX_IMAGE' and nd.image and 'diffuse' in nd.image.name.lower()), None)
        if simg and lts:
            W, H = simg.size; tl = np.array([t.loops[:] for t in lts]); tv = np.array([t.vertices[:] for t in lts])
            m, cov = _raster(luv[tl] * [W, H], hit[tv][..., None], W, H); m = _blur(m, 3)[..., 0]
            a = _img_get(simg); col = np.array(s['scalp'], np.float32); nz = .85 + .3 * RNG.random(m.shape, np.float32)
            a[..., :3] = a[..., :3] * (1 - m[..., None] * .9) + (col * nz[..., None]) * (m[..., None] * .9); _img_set(simg, a)
            print('HAIR кожа под волосами: вершин', int(hit.sum()))


def _fringe_lift(o, body, O, lift):
    """чёлка выше на lift (м): пряди надо лбом (спереди, по центру, ниже линии роста) — вверх и чуть вперёд, кончики на всю
       величину, к линии роста волос сдвиг сходит на нет (корни на месте — та же причёска); виски, макушка, затылок не
       двигаются; где прядь подошла ко лбу ближе 3 мм — отводится от кожи по нормали (не проходит сквозь лоб)"""
    co = _world(o) - O                                     # оси головы: x — вбок, y — назад (лицо в −Y), z — вверх, 0 — между глаз
    w = _ss(.0, -.012, co[:, 1]) * _ss(.085, .04, co[:, 2]) * _ss(.08, .06, np.abs(co[:, 0]))   # чёлка висит в 1–2 см перед лбом (лоб — 2 см перед глазами)
    new = co.copy()
    new[:, 2] += lift * w
    new[:, 1] -= lift * .2 * w                              # лоб уходит назад кверху: прядь идёт вверх и чуть вперёд
    tree = _bvh(body)[0]; pushed = 0
    for i in np.nonzero(w > 1e-3)[0]:
        p = Vector((new[i] + O).tolist())
        loc, nrm, _, _ = tree.find_nearest(p)
        if loc is None:
            continue
        gap = (p - loc).dot(nrm)
        if gap < .003:
            p = p + nrm * (.003 - gap); new[i] = np.array(p, np.float32) - O; pushed += 1
    M3 = np.array(o.matrix_world.inverted(), np.float32)[:3, :3]
    offl = (new - co) @ M3.T
    if o.data.shape_keys:
        for kb in o.data.shape_keys.key_blocks:
            a = np.empty(len(kb.data) * 3, np.float32); kb.data.foreach_get('co', a); kb.data.foreach_set('co', (a.reshape(-1, 3) + offl).ravel())
    o.data.vertices.foreach_set('co', (_co(o) + offl).ravel()); o.data.update()
    print('HAIR чёлка выше: вершин', int((w > 1e-3).sum()), 'макс см', round(float(lift * w.max()) * 100, 2), 'отведено от лба', pushed)


def beard(s):
    body = _body(); rel, L = _head_frame(body); w, ln = beard_weights(rel, L, s)
    me = body.data; uvl = me.uv_layers.active
    keep_f = np.array([w[list(p.vertices)].max() > .02 for p in me.polygons])
    print('BEARD граней', int(keep_f.sum()), 'вершин с весом', int((w > .02).sum()))
    # --- кожа под бородой: темнее, щетина (в развёртке кожи) ---
    simg = next((n.image for m in me.materials if m and m.use_nodes for n in m.node_tree.nodes if n.type == 'TEX_IMAGE' and n.image and 'diffuse' in n.image.name.lower()), None)
    me.calc_loop_triangles(); lts = [t for t in me.loop_triangles if keep_f[t.polygon_index]]
    luv = np.empty(len(me.loops) * 2, np.float32); uvl.data.foreach_get('uv', luv); luv = luv.reshape(-1, 2)
    tri_l = np.array([t.loops[:] for t in lts]); tri_v = np.array([t.vertices[:] for t in lts])
    if simg:
        W, H = simg.size; m, cov = _raster(luv[tri_l] * [W, H], w[tri_v][..., None], W, H)
        m = _blur(m, 2)[..., 0]; a = _img_get(simg); nz = RNG.random(m.shape, np.float32)
        k = (m * (.55 + .45 * nz))[..., None] * s.get('shadow', .55)
        a[..., :3] = a[..., :3] * (1 - k) + a[..., :3] * np.array(s.get('shadow_rgb', (.42, .36, .33)), np.float32) * k
        _img_set(simg, a); print('BEARD тень на коже', simg.name)
    # --- атлас бороды: своя развёртка — сфера вокруг точки за ртом (низ лица, подбородок и низ челюсти без швов и почти без
    #     растяжения; в развёртке кожи кусок шеи лежит в другом острове — атлас выходил пустым и мелким) ---
    cen = np.array([0, s.get('cd', .05), L['mz'] + s.get('cz', .005)], np.float32); r = rel - cen
    R = s.get('radius', .085); buv = np.stack([np.arctan2(r[:, 0], -r[:, 1]), np.arctan2(r[:, 2], np.hypot(r[:, 0], r[:, 1]))], 1) * R
    used = np.unique(tri_v); u0, u1 = buv[used].min(0), buv[used].max(0); span = u1 - u0
    res = s.get('res', 1024)
    if span[0] >= span[1]:
        AW = res; AH = int(2 ** round(math.log2(max(64, res * span[1] / span[0]))))
    else:
        AH = res; AW = int(2 ** round(math.log2(max(64, res * span[0] / span[1]))))
    sc = np.array([AW / span[0], AH / span[1]]); P = (buv[tri_v] - u0) * sc
    print('BEARD атлас', AW, 'x', AH, 'мм на пиксель', np.round(span / [AW, AH] * 1000, 3))
    # направление роста волос в развёртке: «вниз по коже» (и к шее под челюстью), масштаб пикселей на метр
    co = _world(body); g = np.array([0, .35, -1], np.float32); g /= np.linalg.norm(g); DV = np.zeros((len(lts), 3, 3), np.float32)
    for i in range(len(lts)):
        p = co[tri_v[i]]; t = P[i]; dP = np.stack([p[1] - p[0], p[2] - p[0]], 1); dT = np.stack([t[1] - t[0], t[2] - t[0]], 1)
        nrm = np.cross(dP[:, 0], dP[:, 1]); nn = np.linalg.norm(nrm)
        if nn < 1e-12 or abs(np.linalg.det(dT)) < 1e-9:
            continue
        nrm /= nn; gd = g - nrm * (g @ nrm); gl = np.linalg.norm(gd)
        if gl < 1e-6:
            continue
        gd /= gl; Jinv = dT @ np.linalg.pinv(dP)   # 3D → пиксели атласа
        v = Jinv @ gd; DV[i, :] = [v[0], v[1], 0]
    vals = np.concatenate([w[tri_v][..., None], ln[tri_v][..., None], DV[..., :2]], -1)
    A, cov = _raster(P, vals, AW, AH)
    Wm, Lm, Dx, Dy = A[..., 0], A[..., 1], A[..., 2], A[..., 3]
    # волоски: семена по плотности, отрезок по направлению роста, лёгкий изгиб; альфа — «высота» волоска
    alpha = np.where(cov, np.clip((Wm - .3) / .35, 0, 1) * s.get('core', .5), 0).astype(np.float32)
    rgb0 = np.array(s.get('rgb', (.3, .2, .12)), np.float32)
    col = np.zeros((AH, AW, 3), np.float32) + rgb0 * s.get('base', .7)
    n_seed = int(AW * AH * s.get('density', .05)); sx = RNG.random(n_seed) * AW; sy = RNG.random(n_seed) * AH
    ix, iy = sx.astype(int), sy.astype(int); ok = cov[iy, ix] & (RNG.random(n_seed) < Wm[iy, ix] ** .7)
    sx, sy, ix, iy = sx[ok], sy[ok], ix[ok], iy[ok]
    dx, dy = Dx[iy, ix], Dy[iy, ix]; dl = np.hypot(dx, dy) + 1e-9; ux, uy = dx / dl, dy / dl
    lpx = Lm[iy, ix] * dl * RNG.uniform(.55, 1.15, len(sx))
    ang = RNG.normal(0, .22, len(sx)); ca, sa = np.cos(ang), np.sin(ang); ux, uy = ux * ca - uy * sa, ux * sa + uy * ca
    curl = RNG.normal(0, .012, len(sx))
    K = int(min(160, max(8, np.percentile(lpx, 99) * 2)))
    tt = np.linspace(0, 1, K, dtype=np.float32)[None, :]
    bend = curl[:, None] * (tt * lpx[:, None]) ** 2 * .5
    px = sx[:, None] + ux[:, None] * tt * lpx[:, None] - uy[:, None] * bend; py = sy[:, None] + uy[:, None] * tt * lpx[:, None] + ux[:, None] * bend
    ha = RNG.uniform(.42, 1.0, len(sx)).astype(np.float32)[:, None] * (1 - .35 * tt)   # волосок к кончику тоньше
    tone = RNG.choice([.55, .8, 1.0, 1.0, 1.15, 1.35], len(sx)).astype(np.float32)[:, None, None]
    shade = (tone * (.85 + .3 * tt[..., None])) * rgb0[None, None, :]
    pxi, pyi = np.clip(px.astype(int), 0, AW - 1), np.clip(py.astype(int), 0, AH - 1)
    inside = cov[pyi, pxi]
    np.maximum.at(alpha, (pyi[inside], pxi[inside]), np.broadcast_to(ha, px.shape)[inside])
    col[pyi[inside], pxi[inside]] = np.broadcast_to(shade, px.shape + (3,))[inside]
    print('BEARD волосков', len(sx), 'атлас', AW, 'x', AH, 'длина px (медиана)', round(float(np.median(lpx)), 1))
    img = bpy.data.images.new('beard_strands', AW, AH, alpha=True); img.alpha_mode = 'STRAIGHT'
    _img_set(img, np.concatenate([np.clip(col, 0, 1), alpha[..., None]], -1))
    mat = bpy.data.materials.new('beard'); mat.use_nodes = True; nt = mat.node_tree; bs = nt.nodes['Principled BSDF']
    tx = nt.nodes.new('ShaderNodeTexImage'); tx.image = img
    nt.links.new(tx.outputs['Color'], bs.inputs['Base Color']); nt.links.new(tx.outputs['Alpha'], bs.inputs['Alpha'])
    bs.inputs['Roughness'].default_value = .75; mat.blend_method = 'CLIP'; mat.alpha_threshold = .3
    # --- два слоя: копия тела, отодвинутая наружу (лицевые формы и веса костей — те же), лишние грани удаляются ---
    M3 = np.array(body.matrix_world, np.float32)[:3, :3]; sc_w = body.matrix_world.to_scale().x
    nrm = _normals(body); fw = _ss(0, .6, w)
    for name, f in (('beard', s.get('inner', .42)), ('beard2', s.get('outer', .95))):
        ob = body.copy(); ob.data = body.data.copy(); bpy.context.scene.collection.objects.link(ob)
        ob.name = name; ob.data.name = name
        off = nrm * (ln * f * fw / sc_w)[:, None]
        if ob.data.shape_keys:
            for kb in ob.data.shape_keys.key_blocks:
                a = np.empty(len(kb.data) * 3, np.float32); kb.data.foreach_get('co', a); kb.data.foreach_set('co', (a.reshape(-1, 3) + off).ravel())
        ob.data.vertices.foreach_set('co', (_co(ob) + off).ravel())
        src = ob.data.attributes.new('src_v', 'INT', 'POINT'); src.data.foreach_set('value', np.arange(len(ob.data.vertices), dtype=np.int32))
        bm = bmesh.new(); bm.from_mesh(ob.data); bm.faces.ensure_lookup_table()
        bmesh.ops.delete(bm, geom=[f2 for f2 in bm.faces if not keep_f[f2.index]], context='FACES')
        bm.to_mesh(ob.data); bm.free()
        sv = np.empty(len(ob.data.vertices), np.int32); ob.data.attributes['src_v'].data.foreach_get('value', sv); ob.data.attributes.remove(ob.data.attributes['src_v'])
        ob_uv = buv[sv]
        uv = ob.data.uv_layers.active; lv = np.empty(len(ob.data.loops), np.int32); ob.data.loops.foreach_get('vertex_index', lv)
        uv.data.foreach_set('uv', ((ob_uv[lv] - u0) / span).ravel())
        ob.data.materials.clear(); ob.data.materials.append(mat); ob.data.update()
        print('BEARD слой', name, len(ob.data.vertices), 'вершин')


def swap(spec):
    """{"часть имени картинки": "файл MPFB (от data/)"}: другая картинка того же ассета (цвет глаз: brown_eye → bluegreen_eye)"""
    from bl_ext.user_default.mpfb.services import LocationService
    for key, rel in spec.items():
        img = _image(key); path = os.path.join(LocationService.get_user_data(os.path.dirname(rel).split('/')[0]), *rel.split('/')[1:])
        if img and os.path.exists(path):
            img.filepath = path; img.reload(); print('SWAP', key, '->', rel)
        else:
            print('SWAP нет', key, path)


def _sim2d(src, dst):
    """подобие на плоскости (масштаб, поворот, сдвиг) по парам точек (Умеяма): dst ≈ s·R·src + t"""
    src, dst = np.asarray(src, np.float64), np.asarray(dst, np.float64); ms, md = src.mean(0), dst.mean(0); a, b = src - ms, dst - md
    U, S, Vt = np.linalg.svd(b.T @ a / len(src)); D = np.diag([1, np.sign(np.linalg.det(U @ Vt))]); R = U @ D @ Vt
    sc = np.trace(np.diag(S) @ D) / (a ** 2).sum(1).mean(); return sc * R, md - sc * R @ ms


def _ref_views(s, body, L, co):
    """общие для fit/photo: ориентиры нашей головы и подобие «наша камера → пиксели картинки» для каждого вида"""
    O = L['O']; rel = co - O
    E = _world(_find('eyes')); el = E[E[:, 0] > 0].mean(0); er = E[E[:, 0] < 0].mean(0)
    mid = np.abs(rel[:, 0]) < .006; lipf = rel[mid & (np.abs(rel[:, 2] - L['mz']) < .004)]; lip = lipf[lipf[:, 1].argmin()] + O
    tip = L['tip'] + O; mouth = np.array([0, lip[1], O[2] + L['mz']])
    out = []
    for v in s['views']:
        cam = v['cam']
        def proj(P, cam=cam):
            if cam == 'front':
                return np.stack([P[..., 0], -P[..., 2]], -1), P[..., 1]
            if cam == 'right':
                return np.stack([-P[..., 1], -P[..., 2]], -1), P[..., 0]
            return np.stack([P[..., 1], -P[..., 2]], -1), -P[..., 0]
        axes = {'front': (np.array([1., 0, 0]), np.array([0, 0, -1.])), 'right': (np.array([0, -1., 0]), np.array([0, 0, -1.])),
                'left': (np.array([0, 1., 0]), np.array([0, 0, -1.]))}[cam]
        lm3 = {'eye_r': er, 'eye_l': el, 'nose': tip, 'mouth': mouth, 'eye': er if cam == 'right' else el}
        out.append(dict(v=v, proj=proj, axes=axes, lm3=lm3))
    return out


def _fit_sim(V, d, w):
    v = V['v']; names = list(v['lm']); src = np.array([V['proj'](V['lm3'][k])[0] for k in names]); dst = np.array([v['lm'][k] for k in names], np.float64)
    if v['cam'] == 'left':
        dst[:, 0] = w - 1 - dst[:, 0]
    return _sim2d(src, dst)


def fit(s):
    """Форма головы по силуэтам референса (анфас и профили): кромка силуэта нашей головы подтягивается к кромке на картинке,
       остальное — плавно следом (смещения сглаживаются по сетке). Середина лица (глаза, нос, рот) и уши не двигаются —
       мимика и губы остаются наши. Волосы и борода с картинки становятся объёмом самой головы (сетку волос MakeHuman не берём)."""
    d = os.environ.get(s.get('env', 'DAYGUARD_REF'), '')
    if not d or not os.path.isdir(d):
        print('FIT нет папки референса'); return
    body = _body(); me = body.data; rel, L = _head_frame(body); O = L['O']; co = _world(body); rel = co - O
    M3 = np.array(body.matrix_world, np.float32)[:3, :3]; n = _normals(body) @ M3.T; n /= np.maximum(np.linalg.norm(n, axis=1, keepdims=True), 1e-9)
    head = rel[:, 2] > s.get('below', -.15)
    x, z, dd = np.abs(rel[:, 0]), rel[:, 2], rel[:, 1]
    # не трогать: середину лица (глаза, нос, губы — впадины, которых силуэт не видит) и уши (тонкие)
    face = _ss(.05, .035, x) * _ss(.03, .015, z) * _ss(L['mz'] - .03, L['mz'] - .015, z) * _ss(.02, .0, dd)
    ear = _ss(.058, .066, x) * _ss(L['ear_d'] - .012, L['ear_d'] - .002, dd) * _ss(-.06, -.045, z) * _ss(.025, .01, z)
    pin = np.clip(face + ear, 0, 1)
    tgt = np.zeros_like(co); tw = np.zeros(len(co), np.float32)
    for V in _ref_views(s, body, L, co):
        v = V['v']
        im = bpy.data.images.load(os.path.join(d, v['sil'])); S = _img_get(im)[::-1, :, 0] > .5; bpy.data.images.remove(im)
        h, w = S.shape
        if v['cam'] == 'left':
            S = S[:, ::-1]
        Rm, t = _fit_sim(V, d, w); sc = float(np.sqrt(abs(np.linalg.det(Rm))))
        xy, _ = V['proj'](co); uv = xy @ Rm.T + t
        a1, a2 = V['axes']; view = np.cross(a1, a2); view /= np.linalg.norm(view)
        nu, nv = n @ a1, n @ a2; nl = np.hypot(nu, nv) + 1e-9
        rim = np.clip(1 - np.abs(n @ view), 0, 1) ** s.get('rim_power', 6) * head * (1 - pin)
        dimg = np.stack([nu / nl, nv / nl], -1) @ Rm.T; dimg /= np.linalg.norm(dimg, axis=-1, keepdims=True) + 1e-9
        ks = np.arange(-int(.035 * sc), int(.035 * sc) + 1)   # до 3,5 см в обе стороны
        P = uv[:, None, :] + dimg[:, None, :] * ks[None, :, None]
        ix = np.clip(P[..., 0].astype(int), 0, w - 1); iy = np.clip(P[..., 1].astype(int), 0, h - 1)
        ins = S[iy, ix]; c0 = ins[:, len(ks) // 2]
        chg = ins != c0[:, None]
        kk = np.where(chg, np.abs(ks)[None, :], 10 ** 6); j = kk.argmin(1); found = chg[np.arange(len(co)), j]
        dist_px = ks[j].astype(np.float32)   # знак: + наружу (по нормали)
        dist_m = np.where(found, dist_px / sc, 0)
        dir3 = (nu / nl)[:, None] * a1[None, :] + (nv / nl)[:, None] * a2[None, :]
        wv = rim * found * v.get('weight', 1.0)
        tgt += dir3 * (dist_m * wv)[:, None]; tw += wv
        print('FIT вид', v['sil'], v['cam'], 'кромочных вершин', int((wv > .3).sum()), 'среднее смещение мм', round(float(np.abs(dist_m[wv > .3]).mean() * 1000) if (wv > .3).any() else 0, 1))
    tgt = np.where(tw[:, None] > 1e-6, tgt / np.maximum(tw, 1e-6)[:, None], 0)
    # сглаживание: кромки тянут к цели, прибитые — к нулю, остальные — к среднему соседей
    ed = np.array([e.vertices[:] for e in me.edges], np.int64); a, b = ed[:, 0], ed[:, 1]
    deg = np.bincount(a, minlength=len(co)) + np.bincount(b, minlength=len(co))
    disp = tgt.copy(); dw = np.clip(tw, 0, 1) * 4; pw = pin * 50 + (~head) * 50
    for _ in range(s.get('iters', 160)):
        nb = np.zeros_like(disp); np.add.at(nb, a, disp[b]); np.add.at(nb, b, disp[a])
        nb /= np.maximum(deg, 1)[:, None]
        disp = (dw[:, None] * tgt + nb) / (dw + pw + 1)[:, None]
    disp *= _ss(s.get('below', -.15), s.get('below', -.15) + .03, rel[:, 2])[:, None]
    off = disp @ np.linalg.inv(M3).T
    if me.shape_keys:
        for kb in me.shape_keys.key_blocks:
            q = np.empty(len(kb.data) * 3, np.float32); kb.data.foreach_get('co', q); kb.data.foreach_set('co', (q.reshape(-1, 3) + off).ravel())
    me.vertices.foreach_set('co', (_co(body) + off).ravel()); me.update()
    m = np.linalg.norm(disp, axis=1); print('FIT смещение головы: среднее мм', round(float(m[head].mean() * 1000), 1), 'макс мм', round(float(m.max() * 1000), 1))


def photo(s):
    """Лицо с картинки владельца (референс): анфас и профиль проецируются на кожу головы; от картинки — цвет и крупные черты
       (брови, губы, тени, тон кожи), от исходной кожи — мелкие поры (смешивание по частотам). Картинки референса в
       репозиторий не кладём (это спонсор, репозиторий открытый): папка — в переменной окружения s['env'] (DAYGUARD_REF)."""
    d = os.environ.get(s.get('env', 'DAYGUARD_REF'), '')
    if not d or not os.path.isdir(d):
        print('PHOTO нет папки референса', s.get('env', 'DAYGUARD_REF'), '— лицо без проекции'); return
    body = _body(); me = body.data; rel, L = _head_frame(body); O = L['O']; co = _world(body)
    M3 = np.array(body.matrix_world, np.float32)[:3, :3]; n = _normals(body) @ M3.T; n /= np.maximum(np.linalg.norm(n, axis=1, keepdims=True), 1e-9)
    E = _world(_find('eyes')); el = E[E[:, 0] > 0].mean(0); er = E[E[:, 0] < 0].mean(0)
    mid = np.abs(rel[:, 0]) < .006; lipf = rel[mid & (np.abs(rel[:, 2] - L['mz']) < .004)]; lip = lipf[lipf[:, 1].argmin()] + O
    tip = L['tip'] + O; mouth = np.array([0, lip[1], O[2] + L['mz']])
    simg = next((nd.image for m in me.materials if m and m.use_nodes for nd in m.node_tree.nodes if nd.type == 'TEX_IMAGE' and nd.image and 'diffuse' in nd.image.name.lower()), None)
    W, H = simg.size; me.calc_loop_triangles(); uvl = me.uv_layers.active
    luv = np.empty(len(me.loops) * 2, np.float32); uvl.data.foreach_get('uv', luv); luv = luv.reshape(-1, 2)
    head = co[:, 2] > O[2] - s.get('below', .24)
    lts = [t for t in me.loop_triangles if head[list(t.vertices)].all()]
    tl = np.array([t.loops[:] for t in lts]); tv = np.array([t.vertices[:] for t in lts])
    A, cov = _raster(luv[tl] * [W, H], np.concatenate([co[tv], n[tv]], -1), W, H)
    Pos, Nrm = A[..., :3], A[..., 3:]; Nrm /= np.maximum(np.linalg.norm(Nrm, axis=-1, keepdims=True), 1e-9)
    acc = np.zeros((H, W, 3), np.float32); ws = np.zeros((H, W), np.float32)
    for v in s['views']:
        im = bpy.data.images.load(os.path.join(d, v['img'])); R = _img_get(im)[::-1, :, :3].copy(); bpy.data.images.remove(im)   # строки сверху вниз, как в замерах
        h, w = R.shape[:2]; mirror = v['cam'] == 'left'
        if mirror:
            R = R[:, ::-1]
        def proj(P):   # мир → плоскость камеры (метры): анфас — x вправо, z вверх; профиль справа — −y вправо, z вверх; слева — зеркально
            if v['cam'] == 'front':
                return np.stack([P[..., 0], -P[..., 2]], -1), P[..., 1]
            if v['cam'] == 'right':   # камера справа от героя (−x): лицо (−y) — вправо кадра
                return np.stack([-P[..., 1], -P[..., 2]], -1), P[..., 0]
            return np.stack([P[..., 1], -P[..., 2]], -1), -P[..., 0]   # слева (+x), картинка профиля справа — зеркально
        lm3 = {'eye_r': er, 'eye_l': el, 'nose': tip, 'mouth': mouth, 'eye': er if v['cam'] == 'right' else el}
        names = [k for k in v['lm']]; src = np.array([proj(lm3[k])[0] for k in names]); dst = np.array([v['lm'][k] for k in names], np.float64)
        if mirror:
            dst[:, 0] = w - 1 - dst[:, 0]
        Rm, t = _sim2d(src, dst)
        xy, dep = proj(Pos); uv = xy @ Rm.T + t
        # видимость: буфер глубины по треугольникам кожи головы в этой камере
        txy, tdep = proj(co[tv]); tuv = txy @ Rm.T + t
        zb = np.full((h, w), np.inf, np.float32)
        for i in range(len(tv)):
            p = tuv[i]; lo = np.floor(p.min(0)).astype(int); hi = np.ceil(p.max(0)).astype(int)
            x0, y0, x1, y1 = max(lo[0], 0), max(lo[1], 0), min(hi[0], w - 1), min(hi[1], h - 1)
            if x1 < x0 or y1 < y0:
                continue
            xs, ys = np.meshgrid(np.arange(x0, x1 + 1) + .5, np.arange(y0, y1 + 1) + .5); (ax, ay), (bx, by), (cx, cy) = p
            den = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
            if abs(den) < 1e-9:
                continue
            l1 = ((by - cy) * (xs - cx) + (cx - bx) * (ys - cy)) / den; l2 = ((cy - ay) * (xs - cx) + (ax - cx) * (ys - cy)) / den; l3 = 1 - l1 - l2
            m = (l1 > -1e-3) & (l2 > -1e-3) & (l3 > -1e-3); z = l1 * tdep[i, 0] + l2 * tdep[i, 1] + l3 * tdep[i, 2]
            sub = zb[y0:y1 + 1, x0:x1 + 1]; sub[m] = np.minimum(sub[m], z[m])
        ix = np.clip(uv[..., 0].astype(int), 0, w - 1); iy = np.clip(uv[..., 1].astype(int), 0, h - 1)
        inside = cov & (uv[..., 0] >= 0) & (uv[..., 0] < w) & (uv[..., 1] >= 0) & (uv[..., 1] < h)
        vis = inside & (dep <= zb[iy, ix] + .003)
        facing = {'front': -Nrm[..., 1], 'right': -Nrm[..., 0], 'left': Nrm[..., 0]}[v['cam']]
        wt = np.clip(facing, 0, 1) ** s.get('power', 3) * vis * v.get('weight', 1.0)
        wt *= _ss(v.get('zmin', -1.0), v.get('zmin', -1.0) + .012, Pos[..., 2] - O[2])   # ниже — на картинке уже ворот поло
        for (ex, ey, rx, ry) in v.get('holes', []):   # глаза на картинке (белок, радужка) — не на веки
            if mirror:
                ex = w - 1 - ex
            wt *= np.clip(((uv[..., 0] - ex) / rx) ** 2 + ((uv[..., 1] - ey) / ry) ** 2, 0, 1) ** 2
        fx = np.clip(uv[..., 0] - ix, 0, 1)[..., None]; fy = np.clip(uv[..., 1] - iy, 0, 1)[..., None]; ix1 = np.clip(ix + 1, 0, w - 1); iy1 = np.clip(iy + 1, 0, h - 1)
        c = R[iy, ix] * (1 - fx) * (1 - fy) + R[iy, ix1] * fx * (1 - fy) + R[iy1, ix] * (1 - fx) * fy + R[iy1, ix1] * fx * fy
        acc += c * wt[..., None]; ws += wt
        print('PHOTO вид', v['img'], v['cam'], 'видно текселей', int((wt > .05).sum()), 'масштаб', round(float(np.sqrt(abs(np.linalg.det(Rm)))), 1))
    a = _img_get(simg); orig = a[..., :3].copy()
    proj_c = acc / np.maximum(ws, 1e-6)[..., None]
    m = np.clip(ws / s.get('full', .35), 0, 1); m = _blur(m[..., None], 6)[..., 0] * (ws > .002)
    if s.get('hair_rgb'):   # макушка и затылок (их не видно ни спереди, ни в профиль) — цвет волос с картинки, с прядями
        R3 = Pos - O; zone = (_ss(-.06, -.04, R3[..., 2]) * _ss(L['ear_d'] - .03, L['ear_d'], R3[..., 1]) + _ss(.07, .095, R3[..., 2])).clip(0, 1) * cov
        zone = _blur(zone[..., None].astype(np.float32), 4)[..., 0]
        k = zone * (1 - m); nz = .82 + .36 * _blur(RNG.random(m.shape, np.float32)[..., None], 1)[..., 0]
        fill = np.array(s['hair_rgb'], np.float32)[None, None, :] * nz[..., None]
        proj_c = proj_c * (1 - k[..., None]) + fill * k[..., None]; m = np.maximum(m, zone)
    r = s.get('radius', 5)
    lp = _blur(proj_c * m[..., None], r) / np.maximum(_blur(m[..., None], r), 1e-4)
    hp = orig - _blur(orig, r)
    new = np.clip(lp + hp * s.get('detail', .8), 0, 1)
    # тон остального тела — к тону лица с картинки (руки и шея того же цвета, что лицо)
    sel = (m > .8) & (lp @ LUM > .4) & (lp[..., 0] > lp[..., 2] * 1.1)   # только кожа (без волос и бороды с картинки)
    k = (lp[sel].mean(0) / np.maximum(orig[sel].mean(0), 1e-4)) if sel.any() else np.ones(3)
    k = np.clip(k, .6, 1.3); body_tone = np.clip(orig * k, 0, 1)
    a[..., :3] = body_tone * (1 - m[..., None]) + new * m[..., None]
    _img_set(simg, a); print('PHOTO кожа:', simg.name, 'доля головы', round(float(m.mean()), 3), 'тон тела ×', np.round(k, 3))
    # цвет бороды и волос — с картинки (пятна на подбородке и на макушке анфаса)
    return dict(beard=v.get('beard_rgb'), hair=None)


def post(C):
    if C.get('swap'):
        swap(C['swap'])
    if C.get('recolor'):
        recolor(C['recolor'])
    if C.get('emb'):
        emb(C['emb'])
    if C.get('hair_fx'):
        hair(C['hair_fx'])
    if C.get('beard'):
        beard(C['beard'])
    if C.get('fit'):
        fit(C['fit'])
    if C.get('photo'):
        photo(C['photo'])
