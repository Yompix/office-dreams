#!/usr/bin/env python3
"""Сравнение света «было / стало»: кадры старой игры (old_shot.js) и Godot (bake_test) рядом, средняя яркость каждого.

  python3 tools/godot/compare_light.py ПАПКА кадр[:секунда] …   (кадры уже сняты: ПАПКА/old_<кадр>.png и ПАПКА/new_<кадр>.png)
  → ПАПКА/compare.png (сетка «было | стало») и таблица: яркость было, стало, отношение — чтобы подобрать свет мест.
"""
import sys, os
from PIL import Image, ImageDraw, ImageStat


def lum(im):
    g = im.convert('L')
    return ImageStat.Stat(g).mean[0]


def main():
    d = sys.argv[1]
    ids = [a.split(':')[0] for a in sys.argv[2:]]
    rows, W, H = [], 480, 270
    print('%-22s %7s %7s %6s' % ('кадр', 'было', 'стало', 'k'))
    for i in ids:
        a, b = os.path.join(d, 'old_%s.png' % i), os.path.join(d, 'new_%s.png' % i)
        if not (os.path.exists(a) and os.path.exists(b)):
            print('%-22s нет снимков' % i)
            continue
        A, B = Image.open(a).convert('RGB').resize((W, H)), Image.open(b).convert('RGB').resize((W, H))
        la, lb = lum(A), lum(B)
        print('%-22s %7.1f %7.1f %6.2f' % (i, la, lb, la / max(lb, 0.1)))
        rows.append((i, A, B))
    out = Image.new('RGB', (W * 2, H * len(rows)))
    dr = ImageDraw.Draw(out)
    for r, (i, A, B) in enumerate(rows):
        out.paste(A, (0, r * H))
        out.paste(B, (W, r * H))
        dr.text((6, r * H + 4), i + ' (было)', fill=(255, 255, 0))
        dr.text((W + 6, r * H + 4), 'стало', fill=(255, 255, 0))
    out.save(os.path.join(d, 'compare.png'))


if __name__ == '__main__':
    main()
