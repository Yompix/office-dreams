#!/usr/bin/env python3
"""0.9.6: лист «24 жителя из генератора» из снимков автотеста lookgrid (run/screenshots/show/lookgrid_*.png, lookface_*.png).

  python3 tools/contact_sheet.py run/screenshots/show ВЫХОД.jpg
"""
import sys
from PIL import Image, ImageDraw, ImageFont

src, out = sys.argv[1], sys.argv[2]
rows = []
for i in range(3):
    im = Image.open(f"{src}/lookgrid_{i}.png").convert("RGB")
    w, h = im.size
    rows.append(im.crop((int(w * 0.07), int(h * 0.30), int(w * 0.93), int(h * 0.70))))
cw, ch = rows[0].size
sheet = Image.new("RGB", (cw, ch * 3 + 40), (24, 22, 28))
for i, r in enumerate(rows):
    sheet.paste(r, (0, 40 + ch * i))
d = ImageDraw.Draw(sheet)
font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 18)
d.text((12, 10), "НОВОГРАД 0.9.6 — 24 жителя из генератора: мужчины и женщины, 10 тонов кожи, разные районы", fill=(255, 196, 40), font=font)
sheet.save(out, quality=92)
print(out, sheet.size)
