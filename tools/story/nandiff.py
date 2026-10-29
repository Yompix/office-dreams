# сравнение: где в «как на видеокарте» (hook) пиксель почернел, а в обычном был светлым → NaN. python3 nandiff.py OUT
import sys, json, os
from PIL import Image, ImageChops
import numpy as np
out = sys.argv[1]; ids = json.load(open(out + '/ids.json')); rep = []
os.makedirs(out + '/diff', exist_ok=True)
for i in ids:
    a, b = out + '/plain/' + i + '.png', out + '/hook/' + i + '.png'
    if not (os.path.exists(a) and os.path.exists(b)): continue
    A = np.asarray(Image.open(a).convert('RGB')).astype(np.float32) / 255; B = np.asarray(Image.open(b).convert('RGB')).astype(np.float32) / 255
    la = A @ [.299, .587, .114]; lb = B @ [.299, .587, .114]
    bad = (la > .06) & (lb < .02)
    diff = np.abs(A - B).max(axis=2) > .08
    n, nd = int(bad.sum()), int(diff.sum())
    if n > 8 or nd > 200:
        rep.append((i, n, nd))
        v = (B * 255).astype(np.uint8).copy(); v[bad] = [255, 0, 255]; v[diff & ~bad] = (v[diff & ~bad] * .5 + np.array([0, 255, 255]) * .5).astype(np.uint8)
        Image.fromarray(np.concatenate([(A * 255).astype(np.uint8), v], axis=1)).save(out + '/diff/' + i + '.jpg', quality=85)
rep.sort(key=lambda x: -x[1])
for r in rep: print(r)
print('shots with NaN-black pixels:', sum(1 for r in rep if r[1] > 8), 'of', len(ids))
