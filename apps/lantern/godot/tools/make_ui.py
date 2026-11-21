# Графика интерфейса LANTERN 2.0 — фирменный стиль Yompix Games: строгий чёрно-белый, жёсткие пиксельные рамки
# со сдвинутой тенью (как плашки и таймер на заставках канала), без скруглений — «ступенька» в 1 пиксель на углах.
# 9-patch: тот же набор файлов и те же поля, что в 1.7 (UI.glass/solid/halo/primary не меняются), рисуется в 3×.
# Это собственный бренд-стиль владельца: никаких текстур, блоков, шрифтов и цветов Minecraft (docs/LEGAL.md §3).
# python3 tools/make_ui.py [assets/ui]
import sys, os
import numpy as np
from PIL import Image
OUT = sys.argv[1] if len(sys.argv) > 1 else "assets/ui"
os.makedirs(OUT, exist_ok=True)
K = 3            # плотность
M = 20           # поле вокруг панели под тень (dp)
P = 2            # «пиксель» бренда в dp (толщина рамки, шаг ступеньки на углу)


def canvas(w, h):
    return np.zeros((int((h + 2 * M) * K), int((w + 2 * M) * K), 4), np.float32)


def rect(img, x0, y0, x1, y1, rgb, a, notch=True):
    """Прямоугольник в dp (относительно панели) со срезанными «пиксельными» углами; альфа-композит поверх."""
    H, W = img.shape[:2]
    X0, Y0, X1, Y1 = [int(round((v + M) * K)) for v in (x0, y0, x1, y1)]
    n = int(P * K) if notch else 0
    col = np.array(rgb, np.float32)
    for yy in range(max(0, Y0), min(H, Y1)):
        inset = n if n and (yy - Y0 < n or Y1 - 1 - yy < n) else 0
        xa, xb = max(0, X0 + inset), min(W, X1 - inset)
        if xa >= xb:
            continue
        da = img[yy, xa:xb, 3:4]
        oa = a + da * (1 - a)
        img[yy, xa:xb, :3] = (col * a + img[yy, xa:xb, :3] * da * (1 - a)) / np.maximum(oa, 1e-6)
        img[yy, xa:xb, 3:4] = oa


def frame(img, x0, y0, x1, y1, rgb, a, bw=P):
    """Рамка толщиной bw dp; углы срезаны ступенькой."""
    rect(img, x0 + P, y0, x1 - P, y0 + bw, rgb, a, False)
    rect(img, x0 + P, y1 - bw, x1 - P, y1, rgb, a, False)
    rect(img, x0, y0 + P, x0 + bw, y1 - P, rgb, a, False)
    rect(img, x1 - bw, y0 + P, x1, y1 - P, rgb, a, False)


def save(img, name):
    Image.fromarray((np.clip(img, 0, 1) * 255 + 0.5).astype(np.uint8), "RGBA").save(os.path.join(OUT, name), optimize=True)


def panel(r, fill, fill_a, rim_a, shadow_a, off, name, rim=(0.957, 0.957, 0.949), shadow=(0.55, 0.55, 0.55), bw=P):
    w = h = 2 * r + 4
    img = canvas(w, h)
    if shadow_a > 0:
        rect(img, off, off, w + off, h + off, shadow, shadow_a)          # жёсткая сдвинутая тень (без размытия)
    rect(img, 0, 0, w, h, fill, fill_a)
    if rim_a > 0:
        frame(img, 0, 0, w, h, rim, rim_a, bw)
    save(img, name)


# --- поверхности: 3 уровня (карточка · выделенная · плавающая) × 2 «радиуса» (здесь — только размер 9-patch)
for r in (16, 24):
    panel(r, (0.03, 0.03, 0.03), 0.66, 0.22, 0.10, 3, "glass_%d_1.png" % r)
    panel(r, (0.05, 0.05, 0.05), 0.78, 0.42, 0.16, 4, "glass_%d_2.png" % r)
    panel(r, (0.06, 0.06, 0.06), 0.90, 0.75, 0.22, 5, "glass_%d_3.png" % r)
# --- плотные: листы, диалоги, окно поверх игры
panel(24, (0.045, 0.045, 0.045), 1.0, 0.85, 0.30, 6, "solid_24.png")
panel(16, (0.06, 0.06, 0.06), 1.0, 0.80, 0.30, 5, "solid_16.png")


def primary(r, name, pressed=False):
    """Главная кнопка: белая плашка, чёрный текст, жёсткая серая тень. Нажата — плашка «садится» на тень."""
    w = h = 2 * r + 4
    img = canvas(w, h)
    off = 5
    d = 3 if pressed else 0
    rect(img, off, off, w + off, h + off, (0.42, 0.42, 0.42), 1.0)
    rect(img, d, d, w + d, h + d, (0.957, 0.957, 0.949), 1.0)
    save(img, name)


primary(16, "primary_16.png")
primary(16, "primary_16_pressed.png", True)


def halo(r, name):
    """Выделение активного: двойная пиксельная рамка (внутренняя яркая, внешняя — слабая). Центр прозрачный."""
    w = h = 2 * r + 4
    img = canvas(w, h)
    frame(img, -4, -4, w + 4, h + 4, (1, 1, 1), 0.28)
    frame(img, 0, 0, w, h, (1, 1, 1), 0.95)
    save(img, name)


halo(16, "halo_16.png")
halo(24, "halo_24.png")

# --- мягкая точка (свечения) и «луч» блика для кнопок: блик — ступенчатая диагональная полоса
S = 128
y, x = np.mgrid[0:S, 0:S].astype(np.float32)
rr = np.sqrt((x - S / 2 + 0.5) ** 2 + (y - S / 2 + 0.5) ** 2) / (S / 2)
dot = np.zeros((S, S, 4), np.float32); dot[..., :3] = 1; dot[..., 3] = np.clip(1 - rr, 0, 1) ** 2.2
save(dot, "dot.png")
W, H = 96, 32
y, x = np.mgrid[0:H, 0:W].astype(np.float32)
u = (np.floor(x / 4) * 4 + np.floor(y / 4) * 4 * 0.6) / W
sh = np.zeros((H, W, 4), np.float32); sh[..., :3] = 1; sh[..., 3] = (np.abs(u - 0.5) < 0.09) * 0.40
save(sh, "sheen.png")
print("ok:", ", ".join(sorted(f for f in os.listdir(OUT) if f.endswith(".png"))))
