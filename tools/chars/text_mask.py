#!/usr/bin/env python3
"""Маска надписи для «вышивки» (tools/chars/extras.py): текст шрифтом → PNG в оттенках серого (белое — нить).
   python3 tools/chars/text_mask.py ШРИФТ.ttf "Текст" ВЫСОТА_PX ВЫХОД.png
   Отдельным скриптом, потому что в Python Blender нет Pillow."""
import sys
from PIL import Image, ImageDraw, ImageFont

font, text, px, out = sys.argv[1], sys.argv[2], int(sys.argv[3]), sys.argv[4]
ft = ImageFont.truetype(font, px)
x0, y0, x1, y1 = ft.getbbox(text)
pad = px // 6
im = Image.new('L', (x1 - x0 + 2 * pad, y1 - y0 + 2 * pad), 0)
ImageDraw.Draw(im).text((pad - x0, pad - y0), text, font=ft, fill=255)
im.save(out)
print('TEXT', out, im.size)
