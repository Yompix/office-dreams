# chroma-key a sprite sheet on flat green and split it into separate sprites (connected blobs)
import sys
from PIL import Image
import numpy as np
from collections import deque
src, outprefix = sys.argv[1], sys.argv[2]
names = sys.argv[3].split(',') if len(sys.argv) > 3 else None
im = Image.open(src).convert('RGB')
a = np.asarray(im).astype(np.int16)
r, g, b = a[..., 0], a[..., 1], a[..., 2]
# green-screen: strongly green pixels are background; soften the edge (despill)
bg = (g > 120) & (g > r * 1.35) & (g > b * 1.35)
alpha = np.where(bg, 0, 255).astype(np.uint8)
# despill: pull residual green towards the average of r and b on kept pixels
rgb = a.copy()
spill = (~bg) & (g > (r + b) / 2 + 18)
rgb[..., 1] = np.where(spill, ((r + b) / 2 + 18).astype(np.int16), g)
H, W = alpha.shape
# connected components on the alpha mask (8-neighbour), merge tiny bits into the nearest big blob later
lab = np.zeros((H, W), np.int32); n = 0; boxes = []
mask = alpha > 0
for y in range(H):
    for x in range(W):
        if mask[y, x] and not lab[y, x]:
            n += 1; q = deque([(y, x)]); lab[y, x] = n; x0 = x1 = x; y0 = y1 = y; cnt = 0
            while q:
                cy, cx = q.popleft(); cnt += 1
                x0 = min(x0, cx); x1 = max(x1, cx); y0 = min(y0, cy); y1 = max(y1, cy)
                for dy in (-1, 0, 1):
                    for dx in (-1, 0, 1):
                        ny, nx = cy + dy, cx + dx
                        if 0 <= ny < H and 0 <= nx < W and mask[ny, nx] and not lab[ny, nx]:
                            lab[ny, nx] = n; q.append((ny, nx))
            boxes.append([x0, y0, x1, y1, cnt])
big = [b for b in boxes if b[4] > 2000]
big.sort(key=lambda b: (b[0]))
# grow each big box to swallow small nearby blobs (hair strands, chair wheels)
for b in boxes:
    if b[4] > 2000: continue
    cx, cy = (b[0] + b[2]) / 2, (b[1] + b[3]) / 2
    best = min(big, key=lambda B: max(B[0] - cx, 0, cx - B[2]) + max(B[1] - cy, 0, cy - B[3]))
    if max(best[0] - cx, 0, cx - best[2]) + max(best[1] - cy, 0, cy - best[3]) < 25:
        best[0] = min(best[0], b[0]); best[1] = min(best[1], b[1]); best[2] = max(best[2], b[2]); best[3] = max(best[3], b[3])
out = Image.fromarray(np.dstack([np.clip(rgb, 0, 255).astype(np.uint8), alpha]), 'RGBA')
for i, b in enumerate(big):
    pad = 2
    crop = out.crop((max(0, b[0] - pad), max(0, b[1] - pad), min(W, b[2] + pad + 1), min(H, b[3] + pad + 1)))
    nm = names[i] if names and i < len(names) else str(i)
    crop.save(f'{outprefix}{nm}.png')
    print(nm, crop.size)
print('sprites', len(big))
