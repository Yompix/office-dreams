"""0.9.7: тепловая карта жителей из лога автотеста spawnmap (-Dnovograd.autotest=spawnmap).
python3 tools/heatmap.py до.log после.log out.png — слева «до» (-Dnovograd.crowdcap=false), справа «после».
Клетка 16×16 блоков; цвет — сколько людей в клетке (в среднем по замерам); подпись — максимум в клетке."""
import re
import sys
from collections import Counter

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np


def load(path):
    cells, samples, worst = Counter(), 0, 0
    for line in open(path, encoding="utf-8", errors="replace"):
        m = re.search(r"\[spawnmap\] (\S+) игрок .* макс_в_клетке (\d+) точки (.*)$", line)
        if not m:
            continue
        samples += 1
        worst = max(worst, int(m.group(2)))
        for pt in m.group(3).split(";"):
            if "," in pt:
                x, z = pt.split(",")
                cells[(int(x) >> 4, int(z) >> 4)] += 1
    return cells, samples, worst


runs = [load(sys.argv[1]), load(sys.argv[2])]
allk = [k for c, _, _ in runs for k in c]
x0, x1 = min(k[0] for k in allk), max(k[0] for k in allk)
z0, z1 = min(k[1] for k in allk), max(k[1] for k in allk)
fig, axs = plt.subplots(1, 2, figsize=(15, 7))
vmax = 0
grids = []
for cells, samples, worst in runs:
    g = np.zeros((z1 - z0 + 1, x1 - x0 + 1))
    for (x, z), n in cells.items():
        g[z - z0, x - x0] = n / max(1, samples / 10)  # люди в клетке за замер в районе (по 4 замера на район)
    grids.append(g)
    vmax = max(vmax, g.max())
for ax, g, (cells, samples, worst), title in zip(axs, grids, runs, ["До (0.9.6: без норм)", "После (0.9.7: нормы по районам)"]):
    im = ax.imshow(np.ma.masked_where(g == 0, g), cmap="magma_r", vmin=0, vmax=vmax, origin="lower",
                   extent=[x0 * 16, (x1 + 1) * 16, z0 * 16, (z1 + 1) * 16])
    ax.set_title(f"{title}\nлюдей замечено {sum(cells.values())}, макс. в клетке 16×16 за замер: {worst}", fontsize=11)
    ax.set_xlabel("x, блоки")
    ax.set_ylabel("z, блоки")
    ax.set_facecolor("#eef1f4")
fig.colorbar(im, ax=axs, shrink=0.8, label="людей в клетке (в среднем)")
fig.suptitle("Новоград 0.9.7 — где стоят жители (10 районов, по 4 замера)", fontsize=13)
fig.savefig(sys.argv[3], dpi=100)
print("ok", sys.argv[3])
