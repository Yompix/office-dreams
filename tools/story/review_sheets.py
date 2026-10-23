# Контактные листы для покадровой проверки: 6 кадров сцены (по 3 снимка: начало, середина, конец) на лист.
#   python3 tools/story/review_sheets.py ПАПКА   (ПАПКА/review.json и снимки ПАПКА/rev/ — см. review_spec.js) → ПАПКА/sheets/
import json, os, sys
from PIL import Image, ImageDraw
D = sys.argv[1]; R = json.load(open(os.path.join(D, 'review.json'))); REV = os.path.join(D, 'rev'); OUT = os.path.join(D, 'sheets'); os.makedirs(OUT, exist_ok=True)
W, H = 180, 320; per = 6
def frame(img, t):
    p = os.path.join(REV, '%s_%.2f.png' % (img, t))
    if not os.path.exists(p): return None
    im = Image.open(p); return im.resize((W, H) if im.height > im.width else (W, W * 9 // 16))
n = 0
for s in range(0, len(R), per):
    sheet = Image.new('RGB', (W * 9, (H + 16) * 2), (20, 20, 20)); d = ImageDraw.Draw(sheet)
    for j, o in enumerate(R[s:s + per]):
        cx, cy = (j % 3) * W * 3, (j // 3) * (H + 16)
        d.text((cx + 4, cy + 2), '%d %s[%d] %s' % (s + j, o['scene'], o['i'], o['img']), fill=(255, 220, 0))
        for k, t in enumerate(o['times']):
            im = frame(o['img'], t)
            if im: sheet.paste(im, (cx + k * W, cy + 16))
    sheet.save(os.path.join(OUT, 'sheet_%02d.png' % (s // per))); n += 1
print(n, 'листов в', OUT)
