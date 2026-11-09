#!/usr/bin/env python3
"""Проверка помещений: твёрдое на высоте тела (mask_<id>.png, по прозрачности) внутри области, где может быть тело героя
(прямоугольники ходьбы минус препятствия). Печатает скопления с координатами (м) и размером; рисует chk_<id>.png.
python3 plan_check.py dir id [door_open]"""
import json, sys
from PIL import Image, ImageDraw
import numpy as np
d, pid = sys.argv[1], sys.argv[2]
door_open = len(sys.argv) > 3
info = json.load(open(f'{d}/plan_{pid}.json'))
N, ext, cx, cz = info['N'], info['ext'], info['cx'], info['cz']
m = np.array(Image.open(f'{d}/mask_{pid}.png').convert('RGBA'))[:, :, 3] > 40
def to_px(x, z):
    return (x - cx) / ext * N + N / 2, (z - cz) / ext * N + N / 2
def to_w(u, v):
    return (u - N / 2) / N * ext + cx, (v - N / 2) / N * ext + cz
walk = list(info['walk'])
block = list(info['block'])
if door_open:
    for dr in info['doors']:
        ub = [tuple(u[:4]) for u in dr['unblock']]
        block = [b for b in block if tuple(b[:4]) not in ub] + dr['block']
        walk += dr.get('walk', [])
body = Image.new('L', (N, N), 0)
dr_ = ImageDraw.Draw(body)
for w in walk:
    a, b = to_px(w[0], w[1]), to_px(w[2], w[3])
    dr_.rectangle([a, b], fill=255)
for bl in block:
    a, b = to_px(bl[0], bl[1]), to_px(bl[2], bl[3])
    dr_.rectangle([a, b], fill=0)
bm = np.array(body) > 0
# допуск: 3 см внутрь (краевые пиксели и тонкие таблички на стенах не считаем)
tol = max(1, int(round(0.03 / ext * N)))
from scipy import ndimage
bm_in = ndimage.binary_erosion(bm, iterations=tol)
bad = m & bm_in
lab, n = ndimage.label(bad)
out = []
for i in range(1, n + 1):
    ys, xs = np.where(lab == i)
    area = len(xs) * (ext / N) ** 2
    if area < 0.002:   # меньше 20 см² — шум
        continue
    x0, z0 = to_w(xs.min(), ys.min()); x1, z1 = to_w(xs.max(), ys.max())
    out.append((area, x0, z0, x1, z1))
out.sort(reverse=True)
print(f'{pid}{" (дверь открыта)" if door_open else ""}: срез {info.get("slice")}, скоплений {len(out)}')
for a, x0, z0, x1, z1 in out[:25]:
    print(f'  {a*10000:7.0f} см²  x {x0:.2f}…{x1:.2f}  z {z0:.2f}…{z1:.2f}')
vis = Image.open(f'{d}/plan_{pid}.png').convert('RGB')
ov = Image.new('RGBA', vis.size, (0, 0, 0, 0))
o = ImageDraw.Draw(ov)
o.bitmap((0, 0), Image.fromarray((bm.astype(np.uint8) * 255)), fill=(0, 255, 80, 45))
o.bitmap((0, 0), Image.fromarray((bad.astype(np.uint8) * 255)), fill=(255, 0, 255, 255))
vis = Image.alpha_composite(vis.convert('RGBA'), ov).convert('RGB')
vis.save(f'{d}/chk_{pid}{"_open" if door_open else ""}.jpg', quality=85)
