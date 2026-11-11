# Процедурная графика LANTERN: иконка (гладкий фонарь со своим силуэтом) и графика для Google Play.
# Никаких блоков, пиксельных текстур и других элементов, похожих на Minecraft (см. docs/LEGAL.md).
# python3 tools/make_art.py [assets/img] [папка для Play (feature graphic 1024x500, иконка 512)]
import math, sys, os
from PIL import Image, ImageDraw, ImageFilter, ImageFont
OUT = sys.argv[1] if len(sys.argv) > 1 else "assets/img"
PLAY = sys.argv[2] if len(sys.argv) > 2 else ""
os.makedirs(OUT, exist_ok=True)
SS = 4
BG = (18, 19, 28)
METAL = (52, 46, 44)
METAL_HI = (86, 76, 70)


def radial(size, cx, cy, r, color, alpha):
    g = Image.new("RGBA", size, (0, 0, 0, 0)); d = ImageDraw.Draw(g)
    for i in range(40, 0, -1):
        rr = r * i / 40
        d.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], fill=color + (int(alpha * (1 - i / 40) ** 1.6),))
    return g.filter(ImageFilter.GaussianBlur(r * 0.08))


def lantern(img, cx, cy, h):
    """Гладкий фонарь высотой h с центром (cx, cy): ручка, крышка, стекло со светом и пламенем, основание."""
    d = ImageDraw.Draw(img)
    w = h * 0.56
    top = cy - h / 2
    # ручка
    hr = w * 0.26
    d.arc([cx - hr, top, cx + hr, top + hr * 1.7], 180, 360, fill=METAL_HI, width=max(2, int(h * 0.035)))
    # крышка — трапеция и обод
    cap_t, cap_b = top + hr * 0.85, top + h * 0.24
    d.polygon([(cx - w * 0.22, cap_t), (cx + w * 0.22, cap_t), (cx + w * 0.56, cap_b), (cx - w * 0.56, cap_b)], fill=METAL)
    d.rounded_rectangle([cx - w * 0.6, cap_b - h * 0.02, cx + w * 0.6, cap_b + h * 0.05], radius=int(h * 0.025), fill=METAL_HI)
    # стекло: тёплый градиент сверху вниз
    gt, gb = cap_b + h * 0.05, top + h * 0.86
    gx0, gx1 = cx - w * 0.46, cx + w * 0.46
    glass = Image.new("RGBA", img.size, (0, 0, 0, 0)); gd = ImageDraw.Draw(glass)
    steps = 60
    for i in range(steps):
        k = i / (steps - 1)
        y0 = gt + (gb - gt) * i / steps
        gd.rectangle([gx0, y0, gx1, y0 + (gb - gt) / steps + 1], fill=(255, int(214 - 70 * k), int(120 - 90 * k), 255))
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([gx0, gt, gx1, gb], radius=int(w * 0.16), fill=255)
    img.paste(glass, (0, 0), mask)
    d = ImageDraw.Draw(img)
    # пламя — капля с острым язычком
    fh = (gb - gt) * 0.56
    fcx, fcy = cx, gt + (gb - gt) * 0.58
    pts = []
    for a in range(0, 361, 4):
        t = math.radians(a)
        r = fh * 0.30
        x = fcx + r * math.sin(t)
        y = fcy + r * math.cos(t) * 0.9
        if 110 < a < 250:
            k = (1 - abs(a - 180) / 70) ** 1.3
            y -= fh * 0.45 * k
            x = fcx + (x - fcx) * (1 - 0.8 * k)
        pts.append((x, y))
    d.polygon(pts, fill=(255, 250, 232))
    # обвод стекла и перемычка
    lw = max(2, int(h * 0.03))
    d.rounded_rectangle([gx0, gt, gx1, gb], radius=int(w * 0.16), outline=METAL, width=lw)
    # основание
    d.rounded_rectangle([cx - w * 0.58, gb - h * 0.01, cx + w * 0.58, top + h * 0.95], radius=int(h * 0.03), fill=METAL_HI)
    d.rounded_rectangle([cx - w * 0.48, top + h * 0.95, cx + w * 0.48, top + h], radius=int(h * 0.02), fill=METAL)


def icon(S, scale=1.0, bg=True):
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    if bg:
        ImageDraw.Draw(img).rounded_rectangle([int(S * .03), int(S * .03), int(S * .97), int(S * .97)], radius=int(S * .22), fill=BG)
    glow = radial((S, S), S * .5, S * .55, S * .46 * scale, (255, 150, 40), 170)
    if bg:
        m = Image.new("L", (S, S), 0); ImageDraw.Draw(m).rounded_rectangle([int(S * .03), int(S * .03), int(S * .97), int(S * .97)], radius=int(S * .22), fill=255)
        glow.putalpha(Image.composite(glow.split()[3], Image.new("L", (S, S), 0), m))
    img = Image.alpha_composite(img, glow)
    lantern(img, S * .5, S * .52, S * .66 * scale)
    return img


def down(img, size):
    return img.resize(size, Image.LANCZOS)


big = icon(512 * SS)
down(big, (512, 512)).save(os.path.join(OUT, "icon_512.png"))
small = down(big, (256, 256))
small.save(os.path.join(OUT, "icon.png"))
small.save(os.path.join(OUT, "..", "..", "icon.png"))
small.save(os.path.join(OUT, "..", "..", "icon.ico"), sizes=[(16, 16), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
# Android adaptive: передний план без фона (фонарь в безопасной зоне 66%), фон — сплошной
down(icon(432 * SS, 0.72, bg=False), (432, 432)).save(os.path.join(OUT, "android_fg.png"))
Image.new("RGBA", (432, 432), BG + (255,)).save(os.path.join(OUT, "android_bg.png"))

if PLAY:
    os.makedirs(PLAY, exist_ok=True)
    # Play: иконка 512x512 без прозрачности по краям (Play сам скругляет)
    full = Image.new("RGBA", (512 * SS, 512 * SS), BG + (255,))
    full = Image.alpha_composite(full, radial(full.size, 256 * SS, 280 * SS, 236 * SS, (255, 150, 40), 170))
    lantern(full, 256 * SS, 266 * SS, 338 * SS)
    down(full.convert("RGB"), (512, 512)).save(os.path.join(PLAY, "icon_512.png"))
    # Feature graphic 1024x500: фонарь слева, название и строка справа (без слова Minecraft и чужих логотипов)
    W, H = 1024 * 2, 500 * 2
    f = Image.new("RGBA", (W, H), BG + (255,))
    f = Image.alpha_composite(f, radial((W, H), W * .24, H * .55, H * .75, (255, 140, 30), 150))
    lantern(f, W * .24, H * .53, H * .72)
    d = ImageDraw.Draw(f)
    fonts = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "assets", "fonts")
    big_f = ImageFont.truetype(os.path.join(fonts, "Montserrat-ExtraBold.ttf"), 176)
    sub_f = ImageFont.truetype(os.path.join(fonts, "Montserrat-Bold.ttf"), 60)
    d.text((W * .44, H * .26), "LANTERN", font=big_f, fill=(242, 243, 246))
    d.text((W * .445, H * .56), "Играйте с друзьями", font=sub_f, fill=(255, 182, 72))
    d.text((W * .445, H * .67), "по коду комнаты", font=sub_f, fill=(164, 170, 184))
    down(f.convert("RGB"), (1024, 500)).save(os.path.join(PLAY, "feature_1024x500.png"))
print("ok")
