#!/usr/bin/env python3
"""Плавность камеры кат-сцены по покадровому логу (время — часы кадра «clk», если есть в логе, иначе время сцены) (*.cam.tsv, пишет CutsceneScreen при записи или -Dnovograd.camcheck).

Считает по кадрам видео (30 к/с, шаг dt = 1/30 с — так видит зритель):
  * рывок (jerk, 3-я производная позиции), блоков/с³: RMS, p99, максимум;
  * угловая скорость (по кватерниону курс+наклон[+крен]), °/с, и её изменение за кадр (угловое ускорение), °/с²: p99, максимум;
  * разрывы: кадры, где скорость или угловая скорость меняется скачком (|Δv| > 1.5 блока/с за кадр и в 4 раза выше
    соседей, или |Δω| > 25 °/с за кадр и в 4 раза выше соседей), а также остановки «стоп-кадром» посреди движения.
Склейки (cut=1 или смена плана) исключаются: окно разностей, задевающее склейку, не считается.

  python3 tools/cam_metrics.py file.cam.tsv [...] [--json]
"""
import json
import math
import sys


def quat(yaw, pitch, roll=0.0):
    # камера Minecraft: курс вокруг Y (знак как у игры), наклон вокруг X, крен вокруг Z
    y, p, r = math.radians(-yaw) / 2, math.radians(pitch) / 2, math.radians(roll) / 2
    qy = (math.cos(y), 0.0, math.sin(y), 0.0)
    qp = (math.cos(p), math.sin(p), 0.0, 0.0)
    qr = (math.cos(r), 0.0, 0.0, math.sin(r))
    return qmul(qmul(qy, qp), qr)


def qmul(a, b):
    w1, x1, y1, z1 = a
    w2, x2, y2, z2 = b
    return (w1 * w2 - x1 * x2 - y1 * y2 - z1 * z2, w1 * x2 + x1 * w2 + y1 * z2 - z1 * y2,
            w1 * y2 - x1 * z2 + y1 * w2 + z1 * x2, w1 * z2 + x1 * y2 - y1 * x2 + z1 * w2)


def qang(a, b):
    d = abs(sum(i * j for i, j in zip(a, b)))
    return math.degrees(2 * math.acos(min(1.0, d)))


def pct(v, q):
    if not v:
        return 0.0
    s = sorted(v)
    return s[min(len(s) - 1, int(q * (len(s) - 1) + 0.5))]


def load(path):
    rows = []
    with open(path) as f:
        head = f.readline().rstrip('\n').split('\t')
        for ln in f:
            c = ln.rstrip('\n').split('\t')
            if len(c) < len(head):
                continue
            r = dict(zip(head, c))
            clk = r.get('clk')
            rows.append(dict(t=float(clk) if clk not in (None, '') else float(r['t']), scene_t=float(r['t']), shot=int(r['shot']), cut=int(r['cut']), x=float(r['x']), y=float(r['y']), z=float(r['z']),
                             yaw=float(r['yaw']), pitch=float(r['pitch']), roll=float(r.get('roll', 0) or 0)))
    return rows


def metrics(rows, fps=30.0, by_time=True):
    """by_time: производные по времени сцены t (повторы кадра с тем же t — запись не успела — выбрасываются);
    иначе — по кадрам видео с шагом 1/fps."""
    stalls = 0
    if by_time:
        kept = []
        for r in rows:
            if kept and r['t'] <= kept[-1]['t'] + 1e-6 and not r['cut']:
                stalls += 1
                continue
            if kept and r['cut'] == 0 and r['shot'] == kept[-1]['shot'] and r['t'] - kept[-1]['t'] > 0.2:
                stalls += 1  # пропуск времени при записи — считаем как склейку, но отмечаем
                r = dict(r, cut=1)
            kept.append(r)
        rows = kept
    dt = 1.0 / fps
    n = len(rows)
    # граница: кадр i начинает новый план (склейка между i-1 и i)
    brk = [i > 0 and (rows[i]['cut'] or rows[i]['shot'] != rows[i - 1]['shot']) for i in range(n)]
    cuts = sum(brk)

    def clean(i0, i1):  # окно кадров [i0, i1] без склеек внутри
        return i0 >= 0 and i1 < n and not any(brk[k] for k in range(i0 + 1, i1 + 1))

    P = [(r['x'], r['y'], r['z']) for r in rows]
    Q = [quat(r['yaw'], r['pitch'], r['roll']) for r in rows]
    vel = [None] * n
    om = [None] * n
    T = [r['t'] if by_time else i * dt for i, r in enumerate(rows)]
    acc = [None] * n
    for i in range(1, n):
        if clean(i - 1, i):
            h = max(1e-4, T[i] - T[i - 1])
            vel[i] = tuple((P[i][k] - P[i - 1][k]) / h for k in range(3))
            om[i] = qang(Q[i - 1], Q[i]) / h
    for i in range(2, n):
        if vel[i] is not None and vel[i - 1] is not None:
            h = max(1e-4, (T[i] - T[i - 2]) / 2)
            acc[i] = tuple((vel[i][k] - vel[i - 1][k]) / h for k in range(3))
    jerk, dv, dw, alpha = [], [], [], []
    for i in range(3, n):
        if acc[i] is not None and acc[i - 1] is not None:
            h = max(1e-4, (T[i] - T[i - 2]) / 2)
            jerk.append(math.sqrt(sum(((acc[i][k] - acc[i - 1][k]) / h) ** 2 for k in range(3))))
    for i in range(2, n):
        if vel[i] is not None and vel[i - 1] is not None:
            dv.append((i, math.sqrt(sum((vel[i][k] - vel[i - 1][k]) ** 2 for k in range(3)))))
            dw.append((i, abs(om[i] - om[i - 1])))
            alpha.append(abs(om[i] - om[i - 1]) / max(1e-4, (T[i] - T[i - 2]) / 2))
    # разрывы: скачок за кадр, заметно выше соседей (иначе это плавный разгон, а не рывок)
    disc = []
    for arr, lim in ((dv, 1.5), (dw, 25.0)):
        vals = dict(arr)
        for i, v in arr:
            if v <= lim:
                continue
            nb = [vals[k] for k in range(i - 4, i + 5) if k != i and k in vals]
            base = sorted(nb)[len(nb) // 2] if nb else 0.0
            if v > 4 * max(base, lim / 4):
                disc.append(i)
    # стоп-кадр: камера двигалась, замерла ровно на 1–3 кадра и поехала дальше
    for i in range(2, n - 3):
        if vel[i] is None or vel[i - 1] is None:
            continue
        sp = lambda k: math.sqrt(sum(c * c for c in vel[k])) if vel[k] is not None else None
        if sp(i - 1) and sp(i - 1) > 1.0 and sp(i) < 0.05 * sp(i - 1):
            nxt = [sp(k) for k in range(i + 1, i + 4) if vel[k] is not None]
            if nxt and max(nxt) > 0.5 * sp(i - 1):
                disc.append(i)
    disc = sorted(set(disc))
    speeds = [math.sqrt(sum(c * c for c in v)) for v in vel if v is not None]
    oms = [w for w in om if w is not None]
    return dict(frames=n, rec_stalls=stalls, cuts=cuts, discontinuities=len(disc), disc_frames=disc[:20],
                jerk_rms=round(math.sqrt(sum(j * j for j in jerk) / max(1, len(jerk))), 2), jerk_p99=round(pct(jerk, 0.99), 2),
                jerk_max=round(max(jerk) if jerk else 0, 2),
                speed_max=round(max(speeds) if speeds else 0, 2), omega_max=round(max(oms) if oms else 0, 1),
                omega_p99=round(pct(oms, 0.99), 1),
                ang_acc_p99=round(pct(alpha, 0.99), 1), ang_acc_max=round(max(alpha) if alpha else 0, 1),
                max_domega_frame=round(max((v for _, v in dw), default=0), 2))


def main(argv):
    as_json = '--json' in argv
    files = [a for a in argv if not a.startswith('--')]
    out = {}
    for f in files:
        out[f] = metrics(load(f))
    if as_json:
        print(json.dumps(out, ensure_ascii=False, indent=1))
        return
    print(f"{'файл':42} {'кадр':>5} {'скл':>4} {'разр':>4} {'jerkRMS':>8} {'jerk99':>8} {'jerkMax':>9} {'ω99°/с':>7} {'α99°/с²':>8} {'αmax':>8}")
    for f, m in out.items():
        name = f.rsplit('/', 1)[-1][:42]
        print(f"{name:42} {m['frames']:5d} {m['cuts']:4d} {m['discontinuities']:4d} {m['jerk_rms']:8.1f} {m['jerk_p99']:8.1f} {m['jerk_max']:9.1f} "
              f"{m['omega_p99']:7.1f} {m['ang_acc_p99']:8.1f} {m['ang_acc_max']:8.1f}")


if __name__ == '__main__':
    main(sys.argv[1:])
