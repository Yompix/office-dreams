"""Граффити для коллекции тегов (gta/Collectibles): теги банды (rival_*) и свои теги игрока (tag_*), 128×128 RGBA.
Запуск: python3 tools/gen_graffiti.py  → src/main/resources/assets/novograd/textures/misc/graffiti/"""
import os, random
from PIL import Image, ImageDraw, ImageFont, ImageFilter

OUT = os.path.join(os.path.dirname(__file__), "..", "src/main/resources/assets/novograd/textures/misc/graffiti")
os.makedirs(OUT, exist_ok=True)
BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"


def spray(img, color, n, rnd, box):
    d = ImageDraw.Draw(img)
    x0, y0, x1, y1 = box
    for _ in range(n):
        x, y = rnd.uniform(x0, x1), rnd.uniform(y0, y1)
        r = rnd.choice([0.6, 0.8, 1.2])
        d.ellipse([x - r, y - r, x + r, y + r], fill=color + (rnd.randint(60, 160),))


def drips(img, color, rnd, xs, y, maxlen):
    d = ImageDraw.Draw(img)
    for x in xs:
        ln = rnd.randint(maxlen // 3, maxlen)
        w = rnd.choice([2, 2, 3])
        d.line([x, y, x, y + ln], fill=color + (230,), width=w)
        d.ellipse([x - w, y + ln - w, x + w, y + ln + w // 2 + 1], fill=color + (230,))


def tag(text, size, fill, outline, shine, seed, rot, drip_col=None, under=None):
    rnd = random.Random(seed)
    big = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    f = ImageFont.truetype(BOLD, size)
    d = ImageDraw.Draw(big)
    bb = d.textbbox((0, 0), text, font=f, stroke_width=10)
    tw, th = bb[2] - bb[0], bb[3] - bb[1]
    x, y = (256 - tw) / 2 - bb[0], (256 - th) / 2 - bb[1]
    if under:  # подложка-облако
        for _ in range(26):
            cx, cy = rnd.uniform(40, 216), rnd.uniform(80, 176)
            r = rnd.uniform(26, 46)
            d.ellipse([cx - r, cy - r * 0.7, cx + r, cy + r * 0.7], fill=under + (255,))
    d.text((x, y), text, font=f, fill=outline + (255,), stroke_width=10, stroke_fill=outline + (255,))
    # заливка с градиентом
    mask = Image.new("L", (256, 256), 0)
    ImageDraw.Draw(mask).text((x, y), text, font=f, fill=255, stroke_width=3, stroke_fill=255)
    grad = Image.new("RGBA", (256, 256))
    gd = ImageDraw.Draw(grad)
    for yy in range(256):
        k = yy / 255
        c = tuple(int(fill[0][i] * (1 - k) + fill[1][i] * k) for i in range(3))
        gd.line([0, yy, 255, yy], fill=c + (255,))
    big.paste(grad, (0, 0), mask)
    # блик
    hl = Image.new("L", (256, 256), 0)
    ImageDraw.Draw(hl).text((x - 3, y - 4), text, font=f, fill=110)
    hl = Image.composite(hl, Image.new("L", (256, 256), 0), mask.point(lambda v: 255 if v > 0 else 0))
    big.paste(Image.new("RGBA", (256, 256), shine + (255,)), (0, 0), hl.filter(ImageFilter.GaussianBlur(2)).point(lambda v: min(v, 120)))
    if drip_col:
        drips(big, drip_col, rnd, [rnd.randint(60, 196) for _ in range(5)], int(y + bb[3] - 14), 50)
    spray(big, outline, 260, rnd, (20, 50, 236, 206))
    big = big.rotate(rot, resample=Image.BICUBIC)
    return big.resize((128, 128), Image.LANCZOS)


def lizard(seed):
    """Знак «Чёрных Ящеров»: ящерица и надпись — красным по чёрному, с подтёками."""
    rnd = random.Random(seed)
    img = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    red, black = (200, 24, 30), (14, 14, 16)
    pts = [(60, 150), (90, 120), (128, 112), (170, 118), (200, 100), (222, 92)]
    d.line(pts, fill=black + (255,), width=22, joint="curve")
    d.line(pts, fill=red + (255,), width=12, joint="curve")
    for (a, b) in [((95, 118), (80, 90)), ((100, 125), (85, 150)), ((160, 117), (175, 90)), ((165, 120), (180, 148))]:
        d.line([a, b], fill=black + (255,), width=10)
        d.line([a, b], fill=red + (255,), width=5)
    d.ellipse([200, 80, 236, 106], fill=black + (255,))
    d.ellipse([204, 84, 232, 102], fill=red + (255,))
    f = ImageFont.truetype(BOLD, 40)
    d.text((44, 168), "ЯЩЕРЫ", font=f, fill=red + (255,), stroke_width=5, stroke_fill=black + (255,))
    drips(img, red, rnd, [70, 110, 150, 190], 205, 40)
    spray(img, black, 200, rnd, (30, 60, 236, 220))
    return img.rotate(rnd.uniform(-8, 8), resample=Image.BICUBIC).resize((128, 128), Image.LANCZOS)


rivals = [
    lizard(1),
    tag("Ч.Я.", 92, ((220, 30, 30), (120, 0, 0)), (10, 10, 10), (255, 140, 140), 2, -6, drip_col=(170, 0, 0)),
    tag("ЯЩЕР", 70, ((60, 60, 60), (10, 10, 10)), (190, 20, 20), (150, 150, 150), 3, 5, drip_col=(190, 20, 20)),
]
mine = [
    tag("НОВ", 96, ((255, 70, 170), (140, 40, 255)), (20, 10, 40), (255, 220, 255), 11, -7, drip_col=(255, 70, 170), under=(255, 214, 0)),
    tag("Y!", 120, ((60, 230, 255), (20, 90, 255)), (8, 16, 40), (220, 255, 255), 12, 8, drip_col=(40, 160, 255)),
    tag("NVG", 90, ((140, 255, 60), (0, 150, 60)), (10, 30, 10), (240, 255, 200), 13, -4, under=(30, 30, 30)),
    tag("ЯЮ", 104, ((255, 210, 0), (255, 90, 0)), (40, 10, 0), (255, 255, 200), 14, 6, drip_col=(255, 120, 0)),
    tag("24", 120, ((255, 255, 255), (170, 200, 255)), (200, 20, 60), (255, 255, 255), 15, -9, drip_col=(200, 20, 60), under=(20, 20, 60)),
    tag("BOSS", 72, ((255, 120, 60), (220, 20, 120)), (20, 0, 30), (255, 230, 200), 16, 3, under=(40, 220, 200)),
]
for i, im in enumerate(rivals):
    im.save(os.path.join(OUT, f"rival_{i}.png"))
for i, im in enumerate(mine):
    im.save(os.path.join(OUT, f"tag_{i}.png"))
print("ok", len(rivals), len(mine))

# значок приложения «Коллекции» в телефоне: баллончик с краской на тёмной плашке
ICON = os.path.join(os.path.dirname(__file__), "..", "src/main/resources/assets/novograd/textures/gui/ui/app/collect.png")
S = 256
ic = Image.new("RGBA", (S, S), (0, 0, 0, 0))
d = ImageDraw.Draw(ic)
for y in range(S):  # тёмный градиент
    k = y / S
    c = (int(56 - 30 * k), int(24 - 12 * k), int(52 - 26 * k), 255)
    d.line([(0, y), (S, y)], fill=c)
mask = Image.new("L", (S, S), 0)
ImageDraw.Draw(mask).rounded_rectangle([8, 8, S - 8, S - 8], 46, fill=255)
icon = Image.new("RGBA", (S, S), (0, 0, 0, 0))
icon.paste(ic, (0, 0), mask)
d = ImageDraw.Draw(icon)
d.rounded_rectangle([8, 8, S - 8, S - 8], 46, outline=(255, 79, 184, 255), width=6)
pink = (255, 79, 184, 255)
d.rounded_rectangle([92, 92, 160, 206], 14, fill=pink)          # баллон
d.rectangle([92, 120, 160, 132], fill=(255, 190, 230, 255))     # полоска
d.rounded_rectangle([108, 66, 144, 96], 6, fill=(220, 220, 230, 255))  # колпачок
d.rectangle([120, 52, 132, 68], fill=(200, 200, 210, 255))      # распылитель
rnd = random.Random(5)
for _ in range(60):  # облачко краски
    x, y = rnd.uniform(150, 214), rnd.uniform(30, 80)
    r = rnd.uniform(2, 6)
    d.ellipse([x - r, y - r, x + r, y + r], fill=rnd.choice([(63, 216, 255, 230), (255, 214, 0, 230), (124, 255, 74, 230)]))
icon.resize((64, 64), Image.LANCZOS).save(ICON)
print("icon ok")
