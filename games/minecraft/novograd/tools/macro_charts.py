"""0.9.7: графики рынка из build/macro.csv (./gradlew macroTest) — 90 дней трёх сценариев.
python3 tools/macro_charts.py build/macro.csv out.png"""
import csv
import sys

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt

rows = list(csv.DictReader(open(sys.argv[1], encoding="utf-8")))
scen = []
for r in rows:
    if r["scenario"] not in scen:
        scen.append(r["scenario"])
cols = ["#2a78d6", "#1aa37a", "#e0622f"]
panels = [("cpi", "Индекс цен"), ("infl", "Инфляция, %/мес"), ("rate", "Ставка ЦБ, %/мес"), ("unemp", "Безработица, %"),
          ("wage", "Индекс зарплат"), ("fuel", "Бензин, ×цены"), ("property", "Жильё, ×цены"), ("weapons", "Оружие, ×цены")]
fig, axs = plt.subplots(2, 4, figsize=(16, 7.5))
for ax, (k, title) in zip(axs.flat, panels):
    for i, s in enumerate(scen):
        xs = [int(r["day"]) for r in rows if r["scenario"] == s]
        ys = [float(r[k]) for r in rows if r["scenario"] == s]
        ax.plot(xs, ys, color=cols[i % 3], lw=1.6, label=s)
    ax.set_title(title, fontsize=11)
    ax.grid(alpha=0.25)
    ax.spines[["top", "right"]].set_visible(False)
axs.flat[0].legend(frameon=False, fontsize=9)
fig.suptitle("Новоград 0.9.7 — рынок: 90 игровых дней (без игроков / обычный / «бешеный» игрок)", fontsize=13)
fig.tight_layout()
fig.savefig(sys.argv[2], dpi=110)
print("ok", sys.argv[2])
