#!/usr/bin/env python3
"""0.9.8: «скольжение ног» — насколько стопа, которая стоит на земле, едет по земле (м/с и % от скорости тела).

  • игрок (ванильная модель + client/anim/PlayerAnim): нога — маятник, фаза идёт по пройденному пути (limbAnimator);
    «до» — ванильный размах 1.4·s, «после» — размах из формулы шага (PlayerAnim.gaitAmp).
  • жители (GeckoLib, клипы walk/run из animations/citizen.animation.json): стопа по костям бедро+голень; темп клипа —
    скорость/опора (CitizenEntity: «до» 0.075 и 0.17 блока за тик; «после» — откалибровано здесь же).

Запуск: python3 tools/gait_metrics.py [--json out.json]
"""
import json, math, os, sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'novograd')


# ---------------------------------------------------------------- игрок
def player_amp(v, s, new):
    """Размах ноги (рад) при скорости v (блок/тик), limbDistance s."""
    if not new:
        return 1.4 * s
    L = 0.75
    w = 0.6662 * max(s, 1e-3)
    k = v * math.pi / (2 * L * w)
    return min(1.4 * s, math.asin(max(0.0, min(1.0, k))))


STANCE = 0.62


def gait_angle(ph, A):
    """PlayerAnim.gait: опора (62 % цикла) — нога идёт назад равномерно (+A → −A), перенос — плавно вперёд."""
    u = (ph / (2 * math.pi)) % 1.0
    if u < STANCE:
        return A - 2 * A * (u / STANCE)
    k = (u - STANCE) / (1 - STANCE)
    return -A + 2 * A * (0.5 - 0.5 * math.cos(math.pi * k))


def gait_amp(v, s):
    L = 0.75
    w = 0.6662 * max(s, 1e-3)
    T = 2 * math.pi / w
    return min(1.2, v * STANCE * T / (2 * L))


def player_slip(v, new, ticks=400, dt=0.05):
    s = min(v * 4, 1.0)
    L = 0.75
    pos = 0.0
    slips, n = 0.0, 0
    A = gait_amp(v, s) if new else player_amp(v, s, False)
    prev = None
    for i in range(int(ticks / dt)):
        pos += s * dt
        ph = 0.6662 * pos
        if new:
            th, thL = gait_angle(ph, A), gait_angle(ph + math.pi, A)
        else:
            th = A * math.cos(ph)
            thL = -th
        # стопы (z вперёд относительно бедра, y вниз)
        zr, yr = L * math.sin(th), L * math.cos(th)
        zl, yl = L * math.sin(thL), L * math.cos(thL)
        if prev is not None:
            dzr = (zr - prev[0]) / dt
            dzl = (zl - prev[1]) / dt
            # опорная — та, что едет назад (при двойной опоре — обе)
            for dz in (dzr, dzl):
                if dz < 0:
                    slips += abs(v + dz)
                    n += 1
        prev = (zr, zl)
    return slips / max(1, n) * 20, v * 20  # м/с, скорость тела м/с


# ---------------------------------------------------------------- жители
def interp(track, t):
    keys = sorted((float(k), v) for k, v in track.items())
    if t <= keys[0][0]:
        return keys[0][1]
    for (t0, a), (t1, b) in zip(keys, keys[1:]):
        if t0 <= t <= t1:
            k = (t - t0) / max(1e-9, t1 - t0)
            return [a[i] + (b[i] - a[i]) * k for i in range(3)]
    return keys[-1][1]


def foot(clip, side, t):
    b = clip['bones']
    rx_leg = math.radians(interp(b[side + '_leg']['rotation'], t)[0])
    rx_shin = math.radians(interp(b[side + '_shin']['rotation'], t)[0])
    root_y = interp(b['root']['position'], t)[1] if 'root' in b and 'position' in b['root'] else 0
    # бедро на 12 пикс., колено −6, стопа −6 (поворот в плоскости y–z)
    ky, kz = -6 * math.cos(rx_leg), -6 * math.sin(rx_leg)
    a2 = rx_leg + rx_shin
    fy, fz = ky - 6 * math.cos(a2), kz - 6 * math.sin(a2)
    return 12 + root_y + fy, fz


def clip_stance_speed(clip, steps=2000):
    """Средняя скорость опорной стопы (пикс./с) при темпе 1.0."""
    T = float(clip.get('animation_length', 1.0))
    dt = T / steps
    sp, n = 0.0, 0
    for i in range(steps):
        t = i * dt
        ry, rz = foot(clip, 'right', t)
        ly, lz = foot(clip, 'left', t)
        ry2, rz2 = foot(clip, 'right', t + dt)
        ly2, lz2 = foot(clip, 'left', t + dt)
        if ry <= ly:
            sp += abs(rz2 - rz) / dt
        else:
            sp += abs(lz2 - lz) / dt
        n += 1
    return sp / n


def citizen_slip(stance_px, v, ref, lo, hi):
    """v — блок/тик; темп клипа = clamp(v/ref, lo, hi)."""
    f = max(lo, min(hi, v / ref))
    foot_ms = stance_px * f / 16
    return abs(foot_ms - v * 20), v * 20


def main():
    out = {}
    print('ИГРОК (ванильные ноги / новая формула), скольжение опорной стопы:')
    rows = []
    for name, v in (('присед', 0.065), ('шаг', 0.10), ('ходьба', 0.2165), ('спринт', 0.2806)):
        a, vb = player_slip(v, False)
        b, _ = player_slip(v, True)
        rows.append((name, vb, a, b))
        print(f'  {name:8s} {vb:4.1f} м/с: до {a:4.2f} м/с ({a / vb * 100:3.0f}%) → после {b:4.2f} м/с ({b / vb * 100:3.0f}%)')
    out['player'] = rows
    anim = json.load(open(os.path.join(ROOT, 'animations', 'citizen.animation.json'), encoding='utf-8'))['animations']
    print('ЖИТЕЛИ (клипы walk/run), скольжение опорной стопы:')
    res = {}
    for clip, ref_old, lo, hi, speeds in (('walk', 0.075, 0.6, 1.6, (0.05, 0.075, 0.1, 0.12)), ('run', 0.17, 0.8, 1.5, (0.14, 0.18, 0.22, 0.26))):
        st = clip_stance_speed(anim['animation.citizen.' + clip])
        ref_new = st / 16 / 20  # блок/тик при темпе 1.0
        res[clip] = ref_new
        print(f'  {clip}: опорная стопа {st / 16:4.2f} блока/с при темпе 1.0 → опора {ref_new:.4f} блока/тик (было {ref_old})')
        for v in speeds:
            a, vb = citizen_slip(st, v, ref_old, lo, hi)
            b, _ = citizen_slip(st, v, ref_new, 0.45, 2.2)
            print(f'     {vb:4.1f} м/с: до {a:4.2f} м/с ({a / vb * 100:3.0f}%) → после {b:4.2f} м/с ({b / vb * 100:3.0f}%)')
            out.setdefault('citizen', []).append((clip, vb, a, b))
    out['ref'] = res
    if '--json' in sys.argv:
        json.dump(out, open(sys.argv[sys.argv.index('--json') + 1], 'w'), ensure_ascii=False, indent=1)


if __name__ == '__main__':
    main()
