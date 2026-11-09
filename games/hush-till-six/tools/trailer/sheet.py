# Лист проверки монтажа: python3 tools/trailer/sheet.py CACHE/preview → CACHE/preview/sheet.jpg (по 3 кадра на сегмент: начало, середина, конец)
import json, os, sys
from PIL import Image, ImageDraw
D = sys.argv[1]; L = json.load(open(os.path.join(D, 'list.json')))
segs = []
for x in L:
    if not segs or segs[-1][0] != x['id']: segs.append((x['id'], x['shot'], []))
    segs[-1][2].append(x)
im0 = Image.open(L[0]['f']); w, h = im0.size; k = 300 / max(w, h); tw, th = int(w * k), int(h * k)
cols = 2 if w > h else 3; cw = tw * 3 + 12; rows = (len(segs) + cols - 1) // cols
sheet = Image.new('RGB', (cw * cols, rows * (th + 22)), (16, 16, 16)); d = ImageDraw.Draw(sheet)
for i, (sid, shot, fr) in enumerate(segs):
    x0, y0 = (i % cols) * cw, (i // cols) * (th + 22)
    d.text((x0 + 4, y0 + 4), f"{sid}  {shot}  {fr[0]['t']}s", fill=(240, 200, 40))
    for j, f in enumerate(fr): sheet.paste(Image.open(f['f']).resize((tw, th)), (x0 + j * tw, y0 + 20))
sheet.save(os.path.join(D, 'sheet.jpg'), quality=88); print(os.path.join(D, 'sheet.jpg'), sheet.size)
