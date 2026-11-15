#!/usr/bin/env python3
"""Главное меню и заставка 0.4.2: логотип-наклейка кассеты, логотип экрана загрузки, силуэт для редкого сбоя,
панорама меню (6 граней из снимков нашей карты) и кадры заставки (из клипов автотеста -Pautotest=assets).

  /tmp/claude-0/ttsenv/bin/python tools/gen_menu.py logos            — логотипы и силуэт
  /tmp/claude-0/ttsenv/bin/python tools/gen_menu.py pano <папка>     — грани панорамы из run/client/screenshots/pano_*.png
  /tmp/claude-0/ttsenv/bin/python tools/gen_menu.py intro <папка>    — кадры заставки из intro_*.mp4
"""
import json, os, random, subprocess, sys, tempfile
from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
A = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets')
GUI = os.path.join(A, 'glukhoman', 'textures', 'gui')
BOLD = '/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf'
MONO = '/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf'
rnd = random.Random(1996)


def logos():
    os.makedirs(GUI, exist_ok=True)
    # наклейка кассеты: кремовая полоса с бледными линейками, название как от руки по трафарету, номер, красные полосы
    W, H = 1024, 256
    im = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle([0, 0, W - 1, H - 1], 18, fill=(20, 20, 22, 235))
    d.rounded_rectangle([22, 22, W - 23, H - 23], 10, fill=(214, 204, 178, 255))
    for y in range(60, H - 30, 26):
        d.line([40, y, W - 40, y], fill=(170, 160, 140, 255), width=1)
    d.rectangle([22, H - 62, W - 23, H - 54], fill=(170, 40, 34, 255))
    d.rectangle([22, H - 50, W - 23, H - 46], fill=(170, 40, 34, 255))
    f = ImageFont.truetype(BOLD, 118)
    txt = 'ГЛУХОМАНЬ'
    tw = d.textlength(txt, font=f)
    x0 = (W - tw) / 2
    for i, ch in enumerate(txt):   # каждая буква чуть гуляет — трафарет, выбитый вручную
        cw = d.textlength(ch, font=f)
        d.text((x0, 52 + rnd.randint(-4, 4)), ch, font=f, fill=(28, 26, 30, 255))
        x0 += cw
    # потёртости трафарета
    for _ in range(900):
        x, y = rnd.randint(40, W - 40), rnd.randint(50, 180)
        if im.getpixel((x, y))[0] < 60:
            d.point((x, y), fill=(214, 204, 178, 255))
    fs = ImageFont.truetype(MONO, 26)
    d.text((44, 26), 'E-180   ·   ПОРОЖНЯК   ·   14.10.96   ·   НЕ СДАВАТЬ', font=fs, fill=(90, 70, 60, 255))
    d.text((W - 160, H - 44), 'SP  №1', font=fs, fill=(230, 220, 200, 255))
    im = im.filter(ImageFilter.GaussianBlur(0.4))
    im.save(os.path.join(GUI, 'logo_label.png'))
    # экран загрузки: белый логотип на прозрачном (экран сам рисует его на тёмном фоне).
    # Текстура квадратная: верхняя половина — левая часть логотипа, нижняя — правая (так её режет экран загрузки).
    L = Image.new('RGBA', (1024, 256), (0, 0, 0, 0))
    dl = ImageDraw.Draw(L)
    f2 = ImageFont.truetype(BOLD, 150)
    tw = dl.textlength(txt, font=f2)
    dl.text(((1024 - tw) / 2, 40), txt, font=f2, fill=(255, 255, 255, 255))
    sq = Image.new('RGBA', (512, 512), (0, 0, 0, 0))
    sq.paste(L.crop((0, 0, 512, 256)), (0, 0))
    sq.paste(L.crop((512, 0, 1024, 256)), (0, 256))
    sq.save(os.path.join(GUI, 'splash_logo.png'))
    # силуэт Обходчика для редкого сбоя меню: высокий, узкий, голова набок, пряди, без деталей
    S = Image.new('RGBA', (160, 480), (0, 0, 0, 0))
    ds = ImageDraw.Draw(S)
    c = (6, 6, 8, 250)
    ds.polygon([(62, 120), (98, 120), (106, 300), (96, 470), (86, 470), (80, 320), (74, 470), (64, 470), (54, 300)], fill=c)   # тужурка, ноги
    ds.polygon([(62, 124), (48, 160), (40, 300), (46, 304), (58, 170)], fill=c)       # руки длинные
    ds.polygon([(98, 124), (112, 160), (122, 302), (116, 306), (102, 170)], fill=c)
    ds.ellipse([66, 64, 98, 112], fill=c)                                             # голова, набок
    for i in range(9):
        x = 66 + i * 4
        ds.line([x, 70, x + rnd.randint(-4, 4), 120 + rnd.randint(0, 30)], fill=c, width=2)  # пряди
    S = S.rotate(-7, resample=Image.BICUBIC, center=(80, 120)).filter(ImageFilter.GaussianBlur(1.2))
    S.save(os.path.join(GUI, 'silhouette.png'))
    print('ok logos')


def pano(src):
    out = os.path.join(A, 'minecraft', 'textures', 'gui', 'title', 'background')
    os.makedirs(out, exist_ok=True)
    for i in range(6):
        im = Image.open(os.path.join(src, f'pano_{i}.png')).convert('RGB')
        w, h = im.size
        face = im.crop(((w - h) // 2, 0, (w - h) // 2 + h, h)).resize((512, 512), Image.LANCZOS)
        face.save(os.path.join(out, f'panorama_{i}.png'), optimize=True)
    print('ok pano')


def intro(src):
    out = os.path.join(GUI, 'intro')
    os.makedirs(out, exist_ok=True)
    segs = [('a', 'intro_in_ir.mp4', 0.3, 1.4, 12), ('b', 'intro_t3_board.mp4', 0.0, 1.2, 12), ('c', 'intro_t3_train.mp4', 2.6, 1.6, 12),
            ('d', 'intro_t3_rewind.mp4', 4.9, 0.1, 30)]
    meta = []
    with tempfile.TemporaryDirectory(dir='/tmp/claude-0') as t:
        for p, clip, ss, dur, fps in segs:
            subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-ss', str(ss), '-t', str(dur), '-i', os.path.join(src, clip), '-vf',
                            f'fps={fps},scale=384:216,eq=gamma=1.35', os.path.join(t, f'{p}_%02d.png')], check=True)
            fs = sorted(x for x in os.listdir(t) if x.startswith(p + '_'))
            for i, fn in enumerate(fs):
                im = Image.open(os.path.join(t, fn)).convert('RGB').quantize(colors=64, method=Image.MEDIANCUT, dither=Image.NONE)
                im.save(os.path.join(out, f'{p}_{i - 1:02d}.png' if False else f'{p}_{i:02d}.png'), optimize=True)
            meta.append([p, len(fs)])
    json.dump({'fps': 12, 'segments': meta}, open(os.path.join(out, 'intro.json'), 'w'))
    size = sum(os.path.getsize(os.path.join(out, f)) for f in os.listdir(out))
    print('ok intro', meta, round(size / 1e6, 2), 'MB')


if __name__ == '__main__':
    what = sys.argv[1]
    if what == 'logos':
        logos()
    elif what == 'pano':
        pano(sys.argv[2])
    elif what == 'intro':
        intro(sys.argv[2])
