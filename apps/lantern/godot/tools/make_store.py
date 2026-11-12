# Графика LANTERN 1.7 для Google Play в стиле приложения («ночной лагерь, освещённый фонарём»):
#   icon_512.png, feature_1024x500.png, phone_1..6_*.png (1080×1920, подпись сверху), sheet-before-after.png
# Свои гладкие силуэты и градиенты — никаких блоков, чужих логотипов и слова Minecraft на картинках (docs/LEGAL.md).
# python3 tools/make_store.py <папка Play> <папка скриншотов 1080×2400 (tests/design_shots.sh)> [папка «до» для листа до/после]
import sys, os, math
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from make_art import lantern, radial, down

PLAY = sys.argv[1]
SHOTS = sys.argv[2] if len(sys.argv) > 2 else ""
BEFORE = sys.argv[3] if len(sys.argv) > 3 else ""
os.makedirs(PLAY, exist_ok=True)
FONTS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "assets", "fonts")
F = lambda name, size: ImageFont.truetype(os.path.join(FONTS, name), size)
AMBER = (255, 182, 72)


def sky(W, H, seed=3, stars=140, lamp=(0.08, 0.0), aurora=0.30):
    """Ночное небо приложения: индиго → почти чёрное, лента сияния, звёзды, тёплый свет фонаря."""
    y, x = np.mgrid[0:H, 0:W].astype(np.float32)
    u, v = x / W, y / H
    top, mid, bot = np.array([23, 27, 64.0]), np.array([10, 12, 29.0]), np.array([5, 5, 11.0])
    t1 = np.clip(v / 0.5, 0, 1) ** 1.0
    t2 = np.clip((v - 0.42) / 0.58, 0, 1)
    c = top + (mid - top) * (t1 * t1 * (3 - 2 * t1))[..., None]
    c = c + (bot - c) * (t2 * t2 * (3 - 2 * t2))[..., None]
    xs = u * max(W / H, 0.5) * 2.0
    by = aurora + 0.055 * np.sin(xs * 2.1 + 1.0) + 0.03 * np.sin(xs * 4.7 - 2.0)
    d = v - by
    band = np.exp(-d * d * np.where(d < 0, 70.0, 22.0)) * (0.55 + 0.45 * np.sin(xs * 3.3 + 0.7 + np.sin(xs * 1.3) * 1.5))
    mixk = (0.5 + 0.5 * np.sin(xs * 1.7 + 0.4))[..., None]
    aur = np.array([51, 199, 168.0]) * (1 - mixk) + np.array([133, 97, 255.0]) * mixk
    c += aur * band[..., None] * 0.17
    lx, ly = (u - lamp[0]) * max(W / H, 0.6), (v - lamp[1])
    l2 = lx * lx + ly * ly
    c += np.array(AMBER, np.float32) * (np.exp(-l2 * 5.5) * 0.25 + np.exp(-np.sqrt(l2) * 2.4) * 0.05)[..., None]
    rng = np.random.default_rng(seed)
    img = Image.fromarray(np.clip(c + rng.normal(0, 0.6, c.shape), 0, 255).astype(np.uint8), "RGB").convert("RGBA")
    dr = ImageDraw.Draw(img, "RGBA")
    for _ in range(stars):
        sx, sy = rng.random() * W, rng.random() ** 1.8 * H * 0.6
        r = rng.random() * 1.6 + 0.6
        dr.ellipse([sx - r, sy - r, sx + r, sy + r], fill=(235, 238, 255, int(60 + rng.random() * 150)))
    return img


def hills(img, base, amp, color, seed, kind="hills"):
    """Гладкий силуэт: холмы или горы (как обложки игр в приложении)."""
    W, H = img.size
    rng = np.random.default_rng(seed)
    p = rng.random(6) * 6.28
    pts = []
    for xi in range(0, W + 8, 8):
        x = xi / H * 2.2
        if kind == "mountains":
            tri = lambda z: abs((z % 1.0) - 0.5) * 2
            h = (tri(x * 0.45 + p[0]) * 0.8 + tri(x * 1.1 + p[1]) * 0.35 + tri(x * 2.7 + p[2]) * 0.1 - 0.55) * 1.2
        else:
            h = math.sin(x * 1.3 + p[0]) * 0.55 + math.sin(x * 2.9 + p[1]) * 0.3 + math.sin(x * 6.1 + p[2]) * 0.15
        pts.append((xi, base - h * amp))
    ImageDraw.Draw(img, "RGBA").polygon(pts + [(W, H), (0, H)], fill=color)


def glow_text(img, xy, text, font, fill, glow=None, blur=18):
    if glow:
        g = Image.new("RGBA", img.size, (0, 0, 0, 0))
        ImageDraw.Draw(g).text(xy, text, font=font, fill=glow)
        img.alpha_composite(g.filter(ImageFilter.GaussianBlur(blur)))
    ImageDraw.Draw(img).text(xy, text, font=font, fill=fill)


# ------------------------------------------------------------------ иконка 512 (без прозрачности: Play скругляет сам)
SS = 3
ic = sky(512 * SS, 512 * SS, seed=5, stars=60, lamp=(0.5, 0.55), aurora=0.2)
hills(ic, 430 * SS, 40 * SS, (20, 22, 52, 255), 11)
hills(ic, 470 * SS, 30 * SS, (9, 10, 24, 255), 12)
ic = Image.alpha_composite(ic, radial(ic.size, 256 * SS, 272 * SS, 236 * SS, (255, 150, 40), 185))
lantern(ic, 256 * SS, 262 * SS, 330 * SS)
down(ic.convert("RGB"), (512, 512)).save(os.path.join(PLAY, "icon_512.png"))

# ------------------------------------------------------------------ feature graphic 1024×500
W, H = 2048, 1000
f = sky(W, H, seed=9, stars=220, lamp=(0.22, 0.5), aurora=0.24)
hills(f, H * 0.70, H * 0.16, (48, 52, 120, 255), 21, "mountains")
hills(f, H * 0.82, H * 0.10, (26, 29, 72, 255), 22)
hills(f, H * 0.93, H * 0.08, (10, 11, 28, 255), 23)
f = Image.alpha_composite(f, radial((W, H), W * .22, H * .55, H * .70, (255, 140, 30), 165))
lantern(f, W * .22, H * .52, H * .66)
glow_text(f, (W * .42, H * .20), "LANTERN", F("Montserrat-ExtraBold.ttf", 190), (245, 243, 238), (255, 170, 70, 90), 30)
glow_text(f, (W * .425, H * .50), "Нажал — и ты в игре", F("Montserrat-ExtraBold.ttf", 78), AMBER)
glow_text(f, (W * .425, H * .625), "с друзьями по одной ссылке", F("Montserrat-Bold.ttf", 58), (182, 184, 204))
down(f.convert("RGB"), (1024, 500)).save(os.path.join(PLAY, "feature_1024x500.png"))


def rounded(im, r):
    m = Image.new("L", im.size, 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, im.size[0] - 1, im.size[1] - 1], radius=r, fill=255)
    out = im.convert("RGBA")
    out.putalpha(m)
    return out


def phone_shot(src, caption, sub, out, seed):
    """1080×1920: подпись сверху, под ней экран приложения в скруглённой рамке (низ уходит за край)."""
    W, H = 1080, 1920
    bg = sky(W, H, seed=seed, stars=120, lamp=(0.05, 0.02), aurora=0.20)
    d = ImageDraw.Draw(bg)
    big = F("Montserrat-ExtraBold.ttf", 84)
    y = 96
    for line in caption.split("\n"):
        w = d.textlength(line, font=big)
        glow_text(bg, ((W - w) / 2, y), line, big, (245, 243, 238), (255, 170, 70, 70), 26)
        y += 98
    if sub:
        sf = F("Montserrat-Medium.ttf", 40)
        w = d.textlength(sub, font=sf)
        d.text(((W - w) / 2, y + 8), sub, font=sf, fill=(182, 184, 204))
        y += 64
    shot = Image.open(src).convert("RGB")
    sw = 900
    sh = int(shot.height * sw / shot.width)
    shot = rounded(shot.resize((sw, sh), Image.LANCZOS), 64)
    x0, y0 = (W - sw) // 2, y + 56
    sh_l = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(sh_l).rounded_rectangle([x0 - 6, y0 + 18, x0 + sw + 6, y0 + sh + 40], radius=70, fill=(0, 0, 0, 170))
    bg.alpha_composite(sh_l.filter(ImageFilter.GaussianBlur(34)))
    gl = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(gl).rounded_rectangle([x0 - 3, y0 - 3, x0 + sw + 3, y0 + sh + 3], radius=67, outline=(255, 200, 120, 150), width=6)
    bg.alpha_composite(gl.filter(ImageFilter.GaussianBlur(10)))
    bg.alpha_composite(shot, (x0, y0))
    ImageDraw.Draw(bg).rounded_rectangle([x0, y0, x0 + sw - 1, y0 + sh - 1], radius=64, outline=(255, 255, 255, 70), width=2)
    bg.convert("RGB").save(out)


if SHOTS:
    items = [
        ("10_home", "Нажал — и ты в игре", "Игры друзей и открытые миры", "phone_1_games.png"),
        ("24b_join_auto_open", "LANTERN сам\nоткроет игру", "Одна ссылка — и вы в мире друга", "phone_2_join.png"),
        ("12c_create", "Своя игра\nза секунду", "Код для друзей придёт сам", "phone_3_create.png"),
        ("31_friends", "Друзья рядом", "Кто играет — видно сразу", "phone_4_friends.png"),
        ("32_chat", "Чат без лишнего", "Позвать в мир — одно нажатие", "phone_5_chat.png"),
        ("43_shop", "Свой фонарь", "Пламя и рамки за игру с друзьями", "phone_6_shop.png"),
    ]
    for i, (name, cap, sub, out) in enumerate(items):
        p = os.path.join(SHOTS, name + ".png")
        if os.path.exists(p):
            phone_shot(p, cap, sub, os.path.join(PLAY, out), 30 + i)
        else:
            print("нет скриншота", p)

# ------------------------------------------------------------------ лист «до / после»
if BEFORE and SHOTS:
    names = ["10_home", "24_join_step3_in_world", "31_friends", "54_more_sheet"]
    cw, ch, pad = 432, 960, 28
    Wd = pad + len(names) * (cw + pad)
    Hd = 150 + 2 * (ch + 70) + pad
    sheet = sky(Wd, Hd, seed=4, stars=0, lamp=(0.03, 0.0), aurora=-1.0)
    d = ImageDraw.Draw(sheet)
    d.text((pad, 34), "LANTERN: было 1.6 → стало 1.7", font=F("Montserrat-ExtraBold.ttf", 54), fill=(245, 243, 238))
    for r, (folder, title, col) in enumerate([(BEFORE, "БЫЛО · 1.6", (182, 184, 204)), (SHOTS, "СТАЛО · 1.7", AMBER)]):
        y0 = 150 + r * (ch + 70)
        d.text((pad, y0 - 4), title, font=F("Montserrat-Bold.ttf", 30), fill=col)
        for i, n in enumerate(names):
            p = os.path.join(folder, n + ".png")
            if not os.path.exists(p):
                continue
            im = rounded(Image.open(p).convert("RGB").resize((cw, ch), Image.LANCZOS), 30)
            sheet.alpha_composite(im, (pad + i * (cw + pad), y0 + 44))
    sheet.convert("RGB").save(os.path.join(os.path.dirname(os.path.abspath(PLAY)), "sheet-before-after.png"))
print("ok:", ", ".join(sorted(os.listdir(PLAY))))
