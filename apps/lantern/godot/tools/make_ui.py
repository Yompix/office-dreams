# Графика интерфейса LANTERN 1.7 («ночной лагерь, освещённый фонарём»): 9-patch «стекло», кнопка-градиент, свечения.
# Всё процедурное, гладкое (никаких блоков и пикселей — docs/LEGAL.md). Рисуется в 3× и подключается с size override,
# поэтому на любом экране углы чёткие. Цвет акцента накладывается в приложении (modulate), здесь — нейтральные тона.
# python3 tools/make_ui.py [assets/ui]
import sys, os, math
import numpy as np
from PIL import Image
OUT = sys.argv[1] if len(sys.argv) > 1 else "assets/ui"
os.makedirs(OUT, exist_ok=True)
K = 3            # плотность
M = 20           # поле вокруг панели под тень (dp)


def sdf_round(w, h, r, k=K, m=M, dy=0.0):
    """Расстояние до скруглённого прямоугольника (в dp) для картинки (w+2m)x(h+2m) dp в k×."""
    W, H = int((w + 2 * m) * k), int((h + 2 * m) * k)
    y, x = np.mgrid[0:H, 0:W].astype(np.float32)
    x = (x + 0.5) / k - m - w / 2
    y = (y + 0.5) / k - m - h / 2 - dy
    qx = np.abs(x) - (w / 2 - r)
    qy = np.abs(y) - (h / 2 - r)
    d = np.sqrt(np.maximum(qx, 0) ** 2 + np.maximum(qy, 0) ** 2) + np.minimum(np.maximum(qx, qy), 0) - r
    return d, x, y


def over(dst, src):
    """Наложение RGBA (float 0..1), обычный альфа-композит."""
    a = src[..., 3:4] + dst[..., 3:4] * (1 - src[..., 3:4])
    rgb = (src[..., :3] * src[..., 3:4] + dst[..., :3] * dst[..., 3:4] * (1 - src[..., 3:4])) / np.maximum(a, 1e-6)
    return np.concatenate([rgb, a], axis=-1)


def layer(shape, rgb, alpha):
    out = np.zeros(shape + (4,), np.float32)
    out[..., 0], out[..., 1], out[..., 2] = rgb
    out[..., 3] = np.clip(alpha, 0, 1)
    return out


def save(img, name):
    Image.fromarray((np.clip(img, 0, 1) * 255 + 0.5).astype(np.uint8), "RGBA").save(os.path.join(OUT, name), optimize=True)


def glass(r, fill_top, fill_bot, rim_top, rim_bot, shadow, name, fill_rgb=(0.80, 0.83, 1.0), blur=14.0, off=7.0, hi=0.10):
    w = h = 2 * r + 4
    d, x, y = sdf_round(w, h, r)
    ds, _, _ = sdf_round(w, h, r, dy=off)
    t = np.clip((y + h / 2) / h, 0, 1)                      # 0 сверху → 1 снизу
    inside = np.clip(0.5 - d * K, 0, 1)
    img = layer(d.shape, (0, 0, 0), shadow * np.exp(-np.maximum(ds, 0) / blur * 2.2) * (1 - inside))   # мягкая тень
    img = over(img, layer(d.shape, fill_rgb, (fill_top + (fill_bot - fill_top) * t) * inside))          # полупрозрачная заливка
    glow = np.exp(-np.maximum(-d - 0.5, 0) / 9.0) * np.clip(1 - t * 2.2, 0, 1)                          # внутренний блик сверху
    img = over(img, layer(d.shape, (1, 1, 1), hi * glow * inside))
    rim = np.clip(1 - np.abs(d + 0.5) * 1.0, 0, 1)                                                     # кромка 1 dp: светлее сверху
    img = over(img, layer(d.shape, (1, 1, 1), (rim_top + (rim_bot - rim_top) * t ** 0.7) * rim))
    save(img, name)


# --- стекло: 3 уровня высоты × 2 радиуса
for r in (16, 24):
    glass(r, 0.070, 0.040, 0.20, 0.05, 0.26, "glass_%d_1.png" % r, blur=10.0, off=5.0, hi=0.05)
    glass(r, 0.105, 0.060, 0.28, 0.07, 0.36, "glass_%d_2.png" % r, blur=14.0, off=7.0, hi=0.08)
    glass(r, 0.150, 0.085, 0.36, 0.10, 0.46, "glass_%d_3.png" % r, blur=17.0, off=9.0, hi=0.11)
# --- плотное тёмное стекло для листов, всплывашек и окна поверх игры (под ним контент — нужна читаемость)
glass(24, 1.0, 1.0, 0.30, 0.08, 0.55, "solid_24.png", fill_rgb=(0.085, 0.095, 0.185), blur=18.0, off=8.0, hi=0.06)
glass(16, 1.0, 1.0, 0.30, 0.08, 0.50, "solid_16.png", fill_rgb=(0.105, 0.115, 0.215), blur=14.0, off=6.0, hi=0.06)


def primary(r, name, pressed=False):
    """Главная кнопка: градиент (в приложении умножается на акцент: слева светлее, справа теплее), блик сверху, тёплое свечение."""
    w = h = 2 * r + 4
    d, x, y = sdf_round(w, h, r)
    ds, _, _ = sdf_round(w, h, r, dy=5.0)
    t = np.clip((y + h / 2) / h, 0, 1)
    u = np.clip((x + w / 2) / w, 0, 1)
    inside = np.clip(0.5 - d * K, 0, 1)
    img = layer(d.shape, (1.0, 0.72, 0.42), (0.20 if pressed else 0.38) * np.exp(-np.maximum(ds, 0) / 11.0 * 2.2) * (1 - inside))
    rgb = np.stack([np.ones_like(u), 1.0 - 0.20 * u - 0.06 * t, 1.0 - 0.34 * u - 0.10 * t], axis=-1) * (0.90 if pressed else 1.0)
    body = np.concatenate([rgb, inside[..., None]], axis=-1)
    img = over(img, body)
    glow = np.clip(1 - t * 2.4, 0, 1) * inside
    img = over(img, layer(d.shape, (1, 1, 1), (0.10 if pressed else 0.26) * glow))
    rim = np.clip(1 - np.abs(d + 0.5), 0, 1)
    img = over(img, layer(d.shape, (1, 1, 1), (0.55 - 0.45 * t) * rim))
    save(img, name)


primary(16, "primary_16.png")
primary(16, "primary_16_pressed.png", True)


def halo(r, name):
    """Свечение вокруг активного элемента (центр прозрачный) + тонкая светящаяся кромка. Красится акцентом."""
    w = h = 2 * r + 4
    d, x, y = sdf_round(w, h, r)
    out = np.exp(-np.maximum(d, 0) / 7.0) * np.clip(d * K + 0.5, 0, 1) * 0.55
    rim = np.clip(1 - np.abs(d + 0.75) / 1.25, 0, 1) * 0.95
    save(layer(d.shape, (1, 1, 1), np.maximum(out, rim)), name)


halo(16, "halo_16.png")
halo(24, "halo_24.png")

# --- мягкая точка (светлячки, ореолы) и «луч» блика для кнопок
S = 128
y, x = np.mgrid[0:S, 0:S].astype(np.float32)
rr = np.sqrt((x - S / 2 + 0.5) ** 2 + (y - S / 2 + 0.5) ** 2) / (S / 2)
save(layer((S, S), (1, 1, 1), np.clip(1 - rr, 0, 1) ** 2.2), "dot.png")
W, H = 96, 32
y, x = np.mgrid[0:H, 0:W].astype(np.float32)
u = (x + (y - H / 2) * 0.6) / W
save(layer((H, W), (1, 1, 1), np.exp(-((u - 0.5) / 0.13) ** 2) * 0.55), "sheen.png")
print("ok:", ", ".join(sorted(os.listdir(OUT))))
