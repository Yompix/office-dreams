"""Оформление «Тёмные улицы»: логотип, ночной город на закате, комикс-панели, зерно, звёзды розыска,
плюс замена ванильных кнопок/ползунков/панорамы, чтобы и меню Майнкрафта были в нашем стиле.

Запуск: python3 tools/gen_ui.py  (PIL + numpy). Всё рисуется с нуля, чужих картинок нет."""
import math, os, random
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont, ImageChops

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets')
UI = os.path.join(RES, 'novograd', 'textures', 'gui', 'ui')
MENU = os.path.join(RES, 'novograd', 'textures', 'gui', 'menu')
VAN = os.path.join(RES, 'minecraft', 'textures', 'gui')
os.makedirs(UI, exist_ok=True)
BLACK_IT = '/usr/share/fonts/opentype/inter/InterDisplay-BlackItalic.otf'
BLACK = '/usr/share/fonts/opentype/inter/InterDisplay-Black.otf'

PINK, CYAN, GOLD, PURPLE = (255, 46, 136), (40, 230, 255), (255, 196, 40), (110, 40, 190)


def vgrad(w, h, stops):
    """Вертикальный градиент по списку (позиция 0..1, цвет)."""
    arr = np.zeros((h, w, 4), np.float32)
    ys = np.linspace(0, 1, h)
    for c in range(3):
        col = np.interp(ys, [s[0] for s in stops], [s[1][c] for s in stops])
        arr[:, :, c] = col[:, None]
    arr[..., 3] = 255
    return Image.fromarray(arr.astype(np.uint8), 'RGBA')


def title_text(text, path, size, stops, squash=0.86, stroke=None, shadow=10, track=0, font=BLACK_IT):
    """Толстая надпись: градиентная заливка, чёрная обводка, тень со сдвигом."""
    f = ImageFont.truetype(font, size)
    stroke = stroke or max(4, size // 11)
    # ширина с трекингом
    ws = [f.getbbox(ch)[2] for ch in text]
    total = sum(ws) + track * (len(text) - 1)
    W, H = int(total + stroke * 4 + shadow * 2 + size * 0.3), int(size * 1.35 + stroke * 4 + shadow)
    mask = Image.new('L', (W, H), 0)
    outline = Image.new('L', (W, H), 0)
    d, do = ImageDraw.Draw(mask), ImageDraw.Draw(outline)
    x, y = stroke * 2, stroke
    for ch, w in zip(text, ws):
        do.text((x, y), ch, font=f, fill=255, stroke_width=stroke, stroke_fill=255)
        d.text((x, y), ch, font=f, fill=255)
        x += w + track
    fill = vgrad(W, H, stops)
    out = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    sh = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    sh.putalpha(outline.filter(ImageFilter.GaussianBlur(2)).point(lambda v: int(v * 0.85)))
    out.alpha_composite(sh, (shadow, shadow))
    blk = Image.new('RGBA', (W, H), (8, 6, 14, 255))
    blk.putalpha(outline)
    out.alpha_composite(blk)
    fill.putalpha(mask)
    out.alpha_composite(fill)
    # блик по верхней кромке букв
    hl = Image.new('RGBA', (W, H), (255, 255, 255, 0))
    hm = ImageChops.subtract(mask, ImageChops.offset(mask, 0, max(2, size // 22)))
    hl.putalpha(hm.point(lambda v: int(v * 0.55)))
    out.alpha_composite(hl)
    bbox = out.getbbox()
    out = out.crop(bbox)
    if squash != 1:
        out = out.resize((int(out.width * squash), out.height), Image.LANCZOS)
    out.save(path)
    print(path, out.size)
    return out


def sky_color(e):
    """Цвет неба по высоте над горизонтом (e: 0 — горизонт, 1 — зенит)."""
    stops = [(0.0, (255, 150, 60)), (0.08, (255, 80, 110)), (0.22, (190, 40, 140)), (0.45, (70, 20, 110)), (0.75, (18, 10, 45)), (1.0, (6, 4, 18))]
    return [np.interp(e, [s[0] for s in stops], [s[1][c] for s in stops]) for c in range(3)]


def buildings(rng, W, base, hmin, hmax, wmin, wmax, color, win_p, win_cols, layer_seed, period=None):
    """Список зданий слева направо: (x0, x1, top, antenna, color, windows)."""
    out, x = [], 0
    while x < W:
        w = rng.randint(wmin, wmax)
        h = rng.randint(hmin, hmax)
        if rng.random() < 0.12: h = int(h * 1.6)
        out.append((x, x + w, base - h, rng.random() < 0.25, color, rng.random()))
        x += w + rng.randint(0, 3)
    return out


def draw_city(img, base, layers, win=True, seed=1):
    d = ImageDraw.Draw(img)
    W = img.width
    rng = random.Random(seed)
    for li, (hmin, hmax, wmin, wmax, col, wp) in enumerate(layers):
        for (x0, x1, top, ant, c, k) in buildings(rng, W, base, hmin, hmax, wmin, wmax, col, wp, None, li):
            d.rectangle([x0, top, x1, base + 400], fill=c)
            if ant:
                d.line([(x0 + (x1 - x0) // 2, top), (x0 + (x1 - x0) // 2, top - (hmax // 5))], fill=c, width=2)
                d.rectangle([x0 + (x1 - x0) // 2 - 1, top - hmax // 5 - 2, x0 + (x1 - x0) // 2 + 1, top - hmax // 5], fill=(255, 60, 80))
            elif rng.random() < 0.3:
                # ступенчатая крыша
                inset = (x1 - x0) // 4
                d.rectangle([x0 + inset, top - hmax // 10, x1 - inset, top], fill=c)
            if win and wp > 0:
                for wy in range(top + 6, base - 4, 7):
                    for wx in range(x0 + 4, x1 - 4, 6):
                        if rng.random() < wp:
                            wc = rng.choice([(255, 210, 120), (255, 230, 170), (120, 230, 255), (255, 120, 200)])
                            d.rectangle([wx, wy, wx + 2, wy + 3], fill=wc)
            # неоновая вывеска
            if win and rng.random() < 0.12 and x1 - x0 > 26:
                nc = rng.choice([PINK, CYAN, GOLD])
                sy = top + rng.randint(10, 40)
                d.rectangle([x0 + 4, sy, x1 - 4, sy + 5], fill=nc)


def palm(d, x, base, h, col, rng):
    """Силуэт пальмы."""
    pts = []
    for i in range(20):
        t = i / 19
        pts.append((x + math.sin(t * 1.6) * h * 0.18, base - t * h))
    d.line(pts, fill=col, width=max(3, h // 22))
    tx, ty = pts[-1]
    for a in range(9):
        ang = a / 9 * math.pi * 2
        ln = h * (0.35 + rng.random() * 0.15)
        p = []
        for j in range(12):
            t = j / 11
            p.append((tx + math.cos(ang) * ln * t, ty + math.sin(ang) * ln * t * 0.5 + (t * t) * ln * 0.45))
        d.line(p, fill=col, width=max(2, h // 30))


def skyline(W=1280, H=720, path=None):
    """Закат над Новоградом: градиентное небо, полосатое солнце, три слоя зданий, пальмы, вода с отражением."""
    horizon = int(H * 0.72)
    sky = Image.new('RGBA', (W, H))
    arr = np.zeros((H, W, 4), np.uint8)
    for y in range(H):
        e = max(0.0, (horizon - y) / horizon)
        c = sky_color(e)
        arr[y, :, :3] = c
    arr[..., 3] = 255
    sky = Image.fromarray(arr, 'RGBA')
    d = ImageDraw.Draw(sky)
    rng = random.Random(4)
    for i in range(140):
        x, y = rng.randrange(W), rng.randrange(int(H * 0.4))
        b = rng.randint(120, 255)
        d.point((x, y), fill=(b, b, b))
    # солнце с полосами
    sun = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    sd = ImageDraw.Draw(sun)
    cx, cy, r = int(W * 0.62), int(horizon - H * 0.12), int(H * 0.22)
    for yy in range(cy - r, cy + r):
        k = (yy - (cy - r)) / (2 * r)
        col = (255, int(220 - 150 * k), int(90 + 60 * k))
        half = int(math.sqrt(max(0, r * r - (yy - cy) ** 2)))
        if yy > cy - r * 0.1 and ((yy - cy) // max(3, int(r * 0.09))) % 2 == 1 and yy < horizon:
            continue
        sd.line([(cx - half, yy), (cx + half, yy)], fill=col + (255,))
    glow = sun.filter(ImageFilter.GaussianBlur(40))
    sky.alpha_composite(glow)
    sky.alpha_composite(sun)
    city = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    draw_city(city, horizon, [(40, 160, 30, 60, (60, 22, 90, 255), 0.0)], win=False, seed=3)
    draw_city(city, horizon, [(60, 260, 26, 70, (30, 12, 52, 255), 0.18)], seed=5)
    sky.alpha_composite(city)
    # вода с отражением
    refl = sky.crop((0, horizon - (H - horizon), W, horizon)).transpose(Image.FLIP_TOP_BOTTOM)
    ra = np.array(refl).astype(np.float32)
    for y in range(ra.shape[0]):
        shift = int(math.sin(y * 0.9) * 6 * (y / ra.shape[0] + 0.2))
        ra[y] = np.roll(ra[y], shift, axis=0)
        ra[y, :, :3] *= 0.55 - 0.35 * y / ra.shape[0]
    sky.alpha_composite(Image.fromarray(ra.clip(0, 255).astype(np.uint8), 'RGBA'), (0, horizon))
    d = ImageDraw.Draw(sky)
    d.line([(0, horizon), (W, horizon)], fill=(255, 120, 160), width=1)
    # передний план: набережная и пальмы
    fg = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    fd = ImageDraw.Draw(fg)
    fd.rectangle([0, H - 26, W, H], fill=(8, 4, 16, 255))
    for x, h in ((40, 300), (130, 230), (W - 200, 280), (W - 90, 340)):
        palm(fd, x, H - 20, h, (8, 4, 16, 255), rng)
    for x in range(0, W, 90):
        fd.rectangle([x + 40, H - 120, x + 43, H - 26], fill=(8, 4, 16, 255))
        fd.ellipse([x + 35, H - 128, x + 48, H - 118], fill=(255, 200, 120, 255))
    sky.alpha_composite(fg)
    sky.convert('RGB').save(path or os.path.join(UI, 'skyline.png'))
    print('skyline')


def skyline_fg(W=1024, H=256):
    """Чёрный силуэт города на прозрачном фоне (низ экранов)."""
    img = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    draw_city(img, H, [(60, 200, 22, 60, (14, 8, 26, 230), 0.0)], win=False, seed=9)
    draw_city(img, H, [(30, 130, 18, 50, (6, 3, 12, 255), 0.10)], seed=12)
    img.save(os.path.join(UI, 'skyline_fg.png'))


def grain(n=128):
    rng = np.random.default_rng(1)
    v = rng.integers(0, 256, (n, n)).astype(np.uint8)
    a = (rng.random((n, n)) ** 2 * 45).astype(np.uint8)
    img = np.stack([v, v, v, a], -1)
    Image.fromarray(img, 'RGBA').save(os.path.join(UI, 'grain.png'))


def halftone_panel(src, dst, dark, light, accent):
    """Кадр города → комикс-панель: дуотон, точечный растр, чёрная рамка."""
    im = Image.open(src).convert('L')
    im = im.crop((60, 70, im.width - 60, im.height - 30)).resize((640, 360), Image.LANCZOS)  # без надписей HUD сверху
    im = Image.eval(im, lambda v: int(min(255, max(0, (v - 20) * 1.35))))
    g = np.array(im).astype(np.float32) / 255
    out = np.zeros((360, 640, 3), np.float32)
    for c in range(3):
        out[..., c] = dark[c] + (light[c] - dark[c]) * g
    base = Image.fromarray(out.astype(np.uint8), 'RGB')
    # растр: кружки размером от темноты
    dots = Image.new('RGBA', (640, 360), (0, 0, 0, 0))
    dd = ImageDraw.Draw(dots)
    step = 6
    for y in range(0, 360, step):
        for x in range((y // step % 2) * step // 2, 640, step):
            v = g[min(359, y), min(639, x)]
            r = (1 - v) * step * 0.55
            if r > 0.6:
                dd.ellipse([x - r, y - r, x + r, y + r], fill=dark + (110,))
    base = base.convert('RGBA')
    base.alpha_composite(dots)
    # засвет акцентом по диагонали
    ov = Image.new('RGBA', (640, 360), (0, 0, 0, 0))
    od = ImageDraw.Draw(ov)
    od.polygon([(380, 0), (640, 0), (640, 360), (520, 360)], fill=accent + (50,))
    base.alpha_composite(ov.filter(ImageFilter.GaussianBlur(30)))
    d = ImageDraw.Draw(base)
    d.rectangle([0, 0, 639, 359], outline=(0, 0, 0, 255), width=6)
    base.convert('RGB').save(dst)


def star(path, filled):
    s = 64
    img = Image.new('RGBA', (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    pts = []
    for i in range(10):
        a = -math.pi / 2 + i * math.pi / 5
        r = 29 if i % 2 == 0 else 12.5
        pts.append((32 + math.cos(a) * r, 33 + math.sin(a) * r))
    d.polygon(pts, fill=(0, 0, 0, 255))
    d.line(pts + [pts[0]], fill=(0, 0, 0, 255), width=7, joint='curve')
    inner = []
    for i in range(10):
        a = -math.pi / 2 + i * math.pi / 5
        r = 23 if i % 2 == 0 else 9.5
        inner.append((32 + math.cos(a) * r, 33 + math.sin(a) * r))
    d.polygon(inner, fill=(255, 255, 255, 255) if filled else (255, 255, 255, 60))
    img.resize((32, 32), Image.LANCZOS).save(path)


# ------------------------------------------------------------------ ванильные виджеты в нашем стиле
def widget(path, w, h, bg, border, accent=None, top=None):
    img = Image.new('RGBA', (w, h), bg)
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, w - 1, h - 1], outline=border)
    if top: d.line([(1, 1), (w - 2, 1)], fill=top)
    if accent:
        d.rectangle([0, 0, 1, h - 1], fill=accent)
        d.line([(0, h - 1), (w - 1, h - 1)], fill=accent)
    img.save(path)


def vanilla():
    wd = os.path.join(VAN, 'sprites', 'widget')
    os.makedirs(wd, exist_ok=True)
    meta = '{\n    "gui": {\n        "scaling": {\n            "type": "nine_slice",\n            "width": %d,\n            "height": %d,\n            "border": %s\n        }\n    }\n}\n'
    for name, args in {
        'button': ((14, 10, 22, 225), (255, 255, 255, 46), None, (255, 255, 255, 18)),
        'button_highlighted': ((70, 14, 48, 235), (255, 46, 136, 255), (255, 46, 136, 255), (255, 120, 180, 90)),
        'button_disabled': ((10, 8, 14, 170), (255, 255, 255, 20), None, None),
        'slider': ((14, 10, 22, 225), (255, 255, 255, 46), None, None),
        'slider_highlighted': ((24, 12, 34, 235), (40, 230, 255, 200), None, None),
    }.items():
        widget(os.path.join(wd, name + '.png'), 200, 20, *args)
        with open(os.path.join(wd, name + '.png.mcmeta'), 'w') as f:
            f.write(meta % (200, 20, '3'))
    for name, col in (('slider_handle', (255, 46, 136, 255)), ('slider_handle_highlighted', (40, 230, 255, 255))):
        img = Image.new('RGBA', (8, 20), col)
        d = ImageDraw.Draw(img)
        d.rectangle([0, 0, 7, 19], outline=(0, 0, 0, 255))
        d.line([(1, 1), (6, 1)], fill=(255, 255, 255, 150))
        img.save(os.path.join(wd, name + '.png'))
        with open(os.path.join(wd, name + '.png.mcmeta'), 'w') as f:
            f.write(meta % (8, 20, '{\n                "left": 2,\n                "top": 2,\n                "right": 2,\n                "bottom": 3\n            }'))
    # фон списков и разделители
    for name, a in (('menu_background', 200), ('inworld_menu_background', 150), ('menu_list_background', 210), ('inworld_menu_list_background', 160)):
        rng = np.random.default_rng(len(name))
        base = np.zeros((16, 16, 4), np.uint8)
        base[..., 0], base[..., 1], base[..., 2] = 14, 9, 22
        n = rng.integers(0, 6, (16, 16))
        for c in range(3): base[..., c] += n.astype(np.uint8)
        base[..., 3] = a
        Image.fromarray(base, 'RGBA').save(os.path.join(VAN, name + '.png'))
    for name in ('header_separator', 'footer_separator', 'inworld_header_separator', 'inworld_footer_separator'):
        img = Image.new('RGBA', (32, 2), (0, 0, 0, 0))
        d = ImageDraw.Draw(img)
        d.line([(0, 0), (31, 0)], fill=PINK + (255,) if 'header' in name else CYAN + (220,))
        d.line([(0, 1), (31, 1)], fill=(0, 0, 0, 160))
        img.save(os.path.join(VAN, name + '.png'))


def panorama():
    """Панорама ванильных меню: равнопромежуточная картинка ночного города → 6 граней куба."""
    EW, EH = 2048, 1024
    eq = Image.new('RGBA', (EW, EH))
    arr = np.zeros((EH, EW, 4), np.uint8)
    for y in range(EH):
        elev = (0.5 - y / EH) * math.pi  # +пи/2 вверху
        e = max(0.0, elev / (math.pi / 2))
        arr[y, :, :3] = sky_color(e) if elev >= 0 else (10, 6, 20)
    arr[..., 3] = 255
    eq = Image.fromarray(arr, 'RGBA')
    city = Image.new('RGBA', (EW, EH), (0, 0, 0, 0))
    draw_city(city, EH // 2, [(30, 120, 20, 50, (58, 22, 88, 255), 0.0)], win=False, seed=21)
    draw_city(city, EH // 2, [(20, 90, 16, 44, (24, 10, 44, 255), 0.22)], seed=22)
    eq.alpha_composite(city)
    # «зашить» шов по долготе: последние 60 px смешиваем с началом
    e = np.array(eq).astype(np.float32)
    n = 60
    for i in range(n):
        k = i / n
        e[:, EW - n + i] = e[:, EW - n + i] * (1 - k) + e[:, i] * k
    eqa = e.astype(np.uint8)
    S = 512
    dst = os.path.join(VAN, 'title', 'background')
    os.makedirs(dst, exist_ok=True)
    ys, xs = np.mgrid[0:S, 0:S]
    u = (xs + 0.5) / S * 2 - 1
    v = (ys + 0.5) / S * 2 - 1
    for face in range(6):
        if face < 4:
            yaw = face * math.pi / 2
            dx, dy, dz = u, -v, np.ones_like(u)
            # поворот вокруг вертикали: смотрим вперёд, вправо — рост азимута
            x = dx * math.cos(yaw) + dz * math.sin(yaw)
            z = -dx * math.sin(yaw) + dz * math.cos(yaw)
            y = dy
        elif face == 4:
            x, y, z = u, np.ones_like(u), v
        else:
            x, y, z = u, -np.ones_like(u), -v
        az = np.arctan2(x, z)
        el = np.arctan2(y, np.sqrt(x * x + z * z))
        px = ((az / (2 * math.pi)) % 1.0 * EW).astype(int) % EW
        py = np.clip(((0.5 - el / math.pi) * EH).astype(int), 0, EH - 1)
        Image.fromarray(eqa[py, px], 'RGBA').convert('RGB').save(os.path.join(dst, 'panorama_%d.png' % face))


# ------------------------------------------------------------------ иконки телефона: тёмная плитка + неоновый значок
APP_COLORS = {'bank': (70, 230, 120), 'school': (255, 170, 60), 'jobs': (120, 140, 255), 'messages': (70, 230, 150), 'map': (60, 190, 255),
              'shop': (255, 100, 90), 'casino': (255, 60, 120), 'music': (255, 70, 140), 'camera': (200, 200, 220), 'weather': (110, 200, 255),
              'story': (255, 196, 40), 'trophy': (255, 210, 80), 'home': (230, 160, 110), 'taxi': (255, 215, 0), 'friends': (180, 140, 255),
              'missions': (255, 60, 70), 'style': (255, 90, 170), 'contacts': (90, 230, 160), 'cnr': (80, 150, 255)}


def glyph(d, app, c, S):
    k = S / 64
    def P(*pts): return [(x * k, y * k) for x, y in pts]
    w = max(2, int(5 * k))
    if app == 'bank':
        f = ImageFont.truetype(BLACK, int(40 * k))
        d.text((32 * k, 33 * k), '$', font=f, fill=c, anchor='mm')
    elif app == 'school':
        d.polygon(P((14, 20), (32, 26), (50, 20), (50, 46), (32, 52), (14, 46)), outline=c, width=w)
        d.line(P((32, 26), (32, 52)), fill=c, width=w)
    elif app == 'jobs':
        d.rounded_rectangle(P((12, 24), (52, 50)), radius=5 * k, outline=c, width=w)
        d.rectangle(P((24, 16), (40, 24)), outline=c, width=w)
        d.line(P((12, 35), (52, 35)), fill=c, width=w)
    elif app == 'messages':
        d.rounded_rectangle(P((10, 14), (54, 44)), radius=10 * k, fill=c)
        d.polygon(P((18, 40), (16, 54), (30, 42)), fill=c)
    elif app == 'map':
        d.ellipse(P((18, 10), (46, 38)), fill=c)
        d.polygon(P((20, 30), (44, 30), (32, 54)), fill=c)
        d.ellipse(P((26, 18), (38, 30)), fill=(14, 10, 22))
    elif app == 'shop':
        d.polygon(P((14, 24), (50, 24), (46, 52), (18, 52)), fill=c)
        d.arc(P((22, 10), (42, 34)), 180, 360, fill=c, width=w)
    elif app == 'casino':
        d.polygon(P((32, 10), (52, 32), (32, 54), (12, 32)), fill=c)
        d.polygon(P((32, 20), (42, 32), (32, 44), (22, 32)), fill=(14, 10, 22))
    elif app == 'music':
        d.ellipse(P((12, 38), (28, 52)), fill=c)
        d.ellipse(P((36, 34), (52, 48)), fill=c)
        d.line(P((26, 44), (26, 14)), fill=c, width=w)
        d.line(P((50, 40), (50, 10)), fill=c, width=w)
        d.polygon(P((24, 12), (52, 8), (52, 18), (24, 22)), fill=c)
    elif app == 'camera':
        d.rounded_rectangle(P((8, 20), (56, 52)), radius=6 * k, fill=c)
        d.rectangle(P((22, 14), (38, 20)), fill=c)
        d.ellipse(P((22, 26), (42, 46)), fill=(14, 10, 22))
        d.ellipse(P((27, 31), (37, 41)), fill=c)
    elif app == 'weather':
        d.ellipse(P((10, 10), (34, 34)), fill=(255, 200, 70))
        d.ellipse(P((20, 30), (40, 50)), fill=c)
        d.ellipse(P((32, 24), (54, 46)), fill=c)
        d.rounded_rectangle(P((16, 38), (56, 52)), radius=7 * k, fill=c)
    elif app == 'story':
        pts = []
        for i in range(10):
            a = -math.pi / 2 + i * math.pi / 5
            r = 22 if i % 2 == 0 else 9
            pts.append((32 + math.cos(a) * r, 33 + math.sin(a) * r))
        d.polygon(P(*pts), fill=c)
    elif app == 'trophy':
        d.polygon(P((16, 12), (48, 12), (44, 32), (20, 32)), fill=c)
        d.ellipse(P((18, 18), (46, 40)), fill=c)
        d.arc(P((6, 12), (24, 32)), 90, 270, fill=c, width=w)
        d.arc(P((40, 12), (58, 32)), 270, 90, fill=c, width=w)
        d.rectangle(P((29, 38), (35, 46)), fill=c)
        d.rectangle(P((20, 46), (44, 52)), fill=c)
    elif app == 'home':
        d.polygon(P((32, 10), (56, 32), (8, 32)), fill=c)
        d.rectangle(P((14, 30), (50, 54)), fill=c)
        d.rectangle(P((27, 38), (37, 54)), fill=(14, 10, 22))
    elif app == 'taxi':
        d.rounded_rectangle(P((8, 28), (56, 46)), radius=5 * k, fill=c)
        d.polygon(P((16, 28), (22, 16), (42, 16), (48, 28)), fill=c)
        d.rectangle(P((26, 8), (38, 14)), fill=c)
        d.ellipse(P((12, 40), (24, 52)), fill=(14, 10, 22))
        d.ellipse(P((40, 40), (52, 52)), fill=(14, 10, 22))
        d.polygon(P((22, 19), (30, 19), (30, 27), (19, 27)), fill=(14, 10, 22))
        d.polygon(P((34, 19), (42, 19), (45, 27), (34, 27)), fill=(14, 10, 22))
    elif app == 'friends':
        d.ellipse(P((10, 14), (28, 32)), fill=c)
        d.pieslice(P((4, 34), (34, 64)), 180, 360, fill=c)
        d.ellipse(P((34, 12), (54, 32)), fill=c)
        d.pieslice(P((28, 34), (60, 66)), 180, 360, fill=c)
    elif app == 'style':
        # вешалка с футболкой
        d.arc(P((26, 6), (38, 18)), 180, 30, fill=c, width=w)
        d.line(P((32, 18), (32, 22)), fill=c, width=w)
        d.line(P((32, 22), (8, 34), (56, 34), (32, 22)), fill=c, width=w)
        d.polygon(P((18, 34), (46, 34), (52, 40), (46, 44), (44, 56), (20, 56), (18, 44), (12, 40)), fill=c)
    elif app == 'contacts':
        # трубка старого телефона и волны звонка (звонки героев, story/Calls)
        d.arc(P((10, 10), (54, 54)), 100, 170, fill=c, width=int(9 * k))
        d.ellipse(P((8, 8), (22, 22)), fill=c)
        d.ellipse(P((42, 42), (56, 56)), fill=c)
        d.arc(P((30, 8), (56, 34)), 270, 360, fill=c, width=w)
        d.arc(P((36, 2), (62, 28)), 270, 360, fill=c, width=w)
    elif app == 'cnr':
        # полицейская звезда-значок и маска вора («Полицейские и воры»)
        import math as _m
        pts = [(32 + (26 if i % 2 == 0 else 11) * _m.cos(_m.pi / 2 + i * _m.pi / 5), 30 - (26 if i % 2 == 0 else 11) * _m.sin(_m.pi / 2 + i * _m.pi / 5)) for i in range(10)]
        d.polygon(P(*pts), fill=c)
        d.rounded_rectangle(P((14, 48), (50, 60)), radius=int(5 * k), fill=(255, 60, 80, 255))
        d.ellipse(P((20, 51), (28, 57)), fill=(10, 7, 16, 255))
        d.ellipse(P((36, 51), (44, 57)), fill=(10, 7, 16, 255))
    elif app == 'missions':
        d.ellipse(P((12, 12), (52, 52)), outline=c, width=w)
        d.ellipse(P((24, 24), (40, 40)), outline=c, width=w)
        for a, b in (((32, 4), (32, 18)), ((32, 46), (32, 60)), ((4, 32), (18, 32)), ((46, 32), (60, 32))):
            d.line(P(a, b), fill=c, width=w)


def app_icons():
    out = os.path.join(UI, 'app')
    os.makedirs(out, exist_ok=True)
    S = 128
    for app, col in APP_COLORS.items():
        tile = Image.new('RGBA', (S, S), (0, 0, 0, 0))
        m = Image.new('L', (S, S), 0)
        ImageDraw.Draw(m).rounded_rectangle([2, 2, S - 3, S - 3], radius=30, fill=255)
        bg = vgrad(S, S, [(0, tuple(int(v * 0.32 + 22) for v in col)), (1, (10, 7, 16))])
        tile.paste(bg, (0, 0), m)
        g = Image.new('RGBA', (S, S), (0, 0, 0, 0))
        glyph(ImageDraw.Draw(g), app, col + (255,), S)
        # отступ, чтобы значок был по центру плитки, и свечение
        g = g.resize((int(S * 0.72), int(S * 0.72)), Image.LANCZOS)
        gl = Image.new('RGBA', (S, S), (0, 0, 0, 0))
        gl.alpha_composite(g, (int(S * 0.14), int(S * 0.14)))
        glow = gl.filter(ImageFilter.GaussianBlur(7))
        tile.alpha_composite(glow)
        tile.alpha_composite(gl)
        bd = ImageDraw.Draw(tile)
        bd.rounded_rectangle([2, 2, S - 3, S - 3], radius=30, outline=col + (150,), width=3)
        bd.arc([4, 4, 60, 60], 180, 270, fill=(255, 255, 255, 70), width=3)
        tile.resize((64, 64), Image.LANCZOS).save(os.path.join(out, app + '.png'))


if __name__ == '__main__':
    title_text('НОВОГРАД', os.path.join(UI, 'logo.png'), 200,
               [(0.0, (255, 236, 150)), (0.42, (255, 170, 40)), (0.62, (255, 60, 130)), (1.0, (190, 20, 120))], track=4)
    title_text('ТЁМНЫЕ УЛИЦЫ', os.path.join(UI, 'subtitle.png'), 90,
               [(0.0, (230, 255, 255)), (1.0, (40, 220, 255))], squash=0.92, track=10)
    title_text('МИССИЯ ПРОЙДЕНА', os.path.join(UI, 'passed.png'), 120,
               [(0.0, (255, 245, 190)), (0.5, (255, 200, 50)), (1.0, (205, 120, 10))], track=2)
    title_text('ПАУЗА', os.path.join(UI, 'pause.png'), 140,
               [(0.0, (255, 255, 255)), (1.0, (200, 205, 230))], track=6)
    title_text('НАСТРОЙКИ', os.path.join(UI, 'settings.png'), 140,
               [(0.0, (255, 255, 255)), (1.0, (200, 205, 230))], track=6)
    skyline()
    skyline_fg()
    grain()
    for i, (dk, lt, ac) in enumerate([((30, 6, 40), (255, 120, 190), PINK), ((6, 20, 44), (110, 230, 255), CYAN),
                                      ((40, 10, 6), (255, 190, 90), GOLD), ((16, 6, 40), (190, 140, 255), PURPLE)]):
        if os.path.exists(os.path.join(UI, '..', 'keyart', 'night.png')):
            continue  # с 0.3.2 панели — настоящие кадры острова (textures/gui/keyart)
        halftone_panel(os.path.join(MENU, 'bg%d.png' % (i + 1)), os.path.join(UI, 'panel%d.png' % (i + 1)), dk, lt, ac)
    star(os.path.join(UI, 'star.png'), True)
    star(os.path.join(UI, 'star_empty.png'), False)
    vanilla()
    panorama()
    app_icons()
