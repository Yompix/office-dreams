#!/usr/bin/env python3
"""Звуки оружия: выстрелы (резкий щелчок + «тело» + механика + эхо-хвост) и детали перезарядки,
хитмаркер, звон гильз. Всё синтезируется здесь (numpy), без сэмплов.

Запуск из папки novograd: python3 tools/gen_gun_sfx.py
(gen_gta.py для выстрелов тоже берёт функции отсюда, чтобы перегенерация не вернула старые звуки.)
"""
import json, os, subprocess, wave
import numpy as np

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'novograd')
SND = os.path.join(ROOT, 'sounds')
SR = 44100


def write(name, sig):
    sig = np.asarray(sig, float)
    peak = np.max(np.abs(sig)) + 1e-9
    sig = sig / peak * 0.95
    tmp = os.path.join('/tmp', f'ng_{name}.wav')
    with wave.open(tmp, 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes((np.clip(sig, -1, 1) * 32767).astype(np.int16).tobytes())
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', tmp, '-c:a', 'libvorbis', '-q:a', '6', os.path.join(SND, name + '.ogg')], check=True)
    os.remove(tmp)


def lp(x, k):
    """Простой фильтр низких частот (скользящее среднее k отсчётов, дважды)."""
    if k <= 1:
        return x
    ker = np.ones(int(k)) / int(k)
    return np.convolve(np.convolve(x, ker, mode='same'), ker, mode='same')


def hp(x, k):
    return x - lp(x, k)


def env(t, a, d):
    """Атака a с, затухание d (1/с)."""
    return np.minimum(1, t / max(a, 1e-4)) * np.exp(-np.maximum(t - a, 0) * d)


def shot(T, seed, crack=1.0, body=1.0, body_lp=30, body_decay=22, thump_f=(160, 48), thump=0.9,
         mech=0.25, tail=0.5, tail_decay=3.5, refl=((0.09, 0.35), (0.21, 0.22), (0.38, 0.12))):
    rng = np.random.default_rng(seed)
    t = np.arange(int(SR * T)) / SR
    n = rng.standard_normal(len(t))
    # 1) резкий фронт: широкополосный щелчок 2–4 мс
    snap = hp(n, 4) * env(t, 0.0002, 900) * 2.2 * crack
    snap += np.sin(2 * np.pi * 3200 * t) * env(t, 0.0001, 600) * 0.6 * crack
    # 2) тело: «бум» — низкий шум + свип тона вниз
    f = thump_f[1] + (thump_f[0] - thump_f[1]) * np.exp(-t * 35)
    ph = 2 * np.pi * np.cumsum(f) / SR
    boom = lp(n, body_lp) * 6 * env(t, 0.001, body_decay) * body + np.sin(ph) * env(t, 0.0015, body_decay * 0.8) * thump
    mid = lp(hp(n, 30), 3) * env(t, 0.001, body_decay * 1.8) * 1.2 * body
    # 3) механика затвора: короткий металлический щелчок через ~35 мс
    k = np.clip(t - 0.035, 0, None)
    mech_s = (t > 0.035) * (np.sin(2 * np.pi * 2400 * k) * 0.5 + np.sin(2 * np.pi * 4100 * k) * 0.3 + hp(n, 3) * 0.4) * np.exp(-k * 120) * mech
    dry = snap + boom + mid + mech_s
    # 4) хвост: отражения от домов + рассеянный гул
    wet = np.zeros_like(dry)
    for dly, g in refl:
        i = int(dly * SR)
        wet[i:] += lp(dry[:-i], 6) * g
    rumble = lp(n, 160) * 14 * env(t, 0.02, tail_decay) * tail + lp(hp(n, 60), 8) * 2 * env(t, 0.01, tail_decay * 1.6) * tail
    s = dry + wet + rumble
    s *= np.minimum(1, (T - t) / 0.05)
    return np.tanh(s * 1.4)


def click(t, at, f1, f2, decay, rng, amp=1.0):
    k = np.clip(t - at, 0, None)
    on = (t >= at).astype(float)
    n = rng.standard_normal(len(t))
    return on * amp * (np.sin(2 * np.pi * f1 * k) * 0.45 + np.sin(2 * np.pi * f2 * k) * 0.3 + hp(n, 6) * 0.5) * np.exp(-k * decay)


def scrape(t, a, b, rng, amp=0.2, k=3):
    n = hp(rng.standard_normal(len(t)), k)
    e = ((t > a) & (t < b)) * np.sin(np.clip((t - a) / (b - a), 0, 1) * np.pi)
    return n * e * amp


def part(T, events, seed, scrapes=()):
    rng = np.random.default_rng(seed)
    t = np.arange(int(SR * T)) / SR
    s = np.zeros_like(t)
    for at, f1, f2, dec, amp in events:
        s += click(t, at, f1, f2, dec, rng, amp)
    for a, b, amp in scrapes:
        s += scrape(t, a, b, rng, amp)
    return np.tanh(s * 1.3)


def pistol_shot():
    return shot(1.1, 101, crack=1.0, body=0.8, body_lp=22, body_decay=30, thump_f=(220, 70), thump=0.6, mech=0.3, tail=0.45, tail_decay=4)


def smg_shot():
    return shot(0.55, 102, crack=0.9, body=0.6, body_lp=18, body_decay=42, thump_f=(240, 90), thump=0.45, mech=0.35, tail=0.35, tail_decay=6,
                refl=((0.07, 0.3), (0.16, 0.18)))


def rifle_shot():
    return shot(0.9, 103, crack=1.3, body=1.0, body_lp=26, body_decay=26, thump_f=(180, 55), thump=0.8, mech=0.3, tail=0.55, tail_decay=4.5)


def shotgun_shot():
    s = shot(1.4, 104, crack=0.9, body=1.6, body_lp=45, body_decay=14, thump_f=(130, 38), thump=1.2, mech=0.0, tail=0.8, tail_decay=3)
    t = np.arange(len(s)) / SR
    rng = np.random.default_rng(5)
    # помпа: назад (0.32 с) и вперёд (0.46 с) — синхронно с анимацией в GunAnim
    pump = click(t, 0.32, 1300, 2500, 55, rng, 0.9) + scrape(t, 0.30, 0.36, rng, 0.25) + click(t, 0.46, 1100, 2300, 50, rng, 1.0) + scrape(t, 0.42, 0.47, rng, 0.2)
    return s + pump * 0.35


def sniper_shot():
    s = shot(2.2, 105, crack=1.6, body=1.2, body_lp=34, body_decay=18, thump_f=(150, 40), thump=1.1, mech=0.0, tail=0.9, tail_decay=2.2,
             refl=((0.14, 0.4), (0.33, 0.28), (0.61, 0.18), (0.95, 0.1)))
    t = np.arange(len(s)) / SR
    rng = np.random.default_rng(6)
    # затвор: вверх 0.55, назад 0.68, вперёд 0.86, вниз 0.98 (как в GunAnim)
    bolt = (click(t, 0.55, 1900, 3300, 70, rng, 0.7) + click(t, 0.68, 1400, 2600, 55, rng, 0.9) + scrape(t, 0.6, 0.68, rng, 0.2)
            + click(t, 0.86, 1500, 2700, 60, rng, 1.0) + scrape(t, 0.78, 0.86, rng, 0.18) + click(t, 0.98, 2100, 3600, 80, rng, 0.8))
    return s + bolt * 0.3


def main():
    write('gunshot', pistol_shot())
    write('smg_shot', smg_shot())
    write('rifle_shot', rifle_shot())
    write('shotgun_shot', shotgun_shot())
    write('sniper_shot', sniper_shot())
    parts = {
        'gun_mag_out': part(0.35, [(0.02, 1500, 2700, 60, 0.8), (0.1, 900, 1700, 40, 0.4)], 1, [(0.03, 0.12, 0.25)]),
        'gun_mag_in': part(0.35, [(0.02, 1100, 2200, 45, 0.7), (0.07, 1800, 3200, 70, 1.0)], 2, [(0.0, 0.06, 0.2)]),
        'gun_slide_back': part(0.25, [(0.01, 1700, 3000, 70, 0.6), (0.1, 2000, 3400, 75, 0.7)], 3, [(0.02, 0.1, 0.3)]),
        'gun_slide_release': part(0.25, [(0.01, 2300, 4000, 85, 1.0), (0.025, 1600, 2900, 70, 0.6)], 4),
        'gun_bolt_back': part(0.3, [(0.01, 1300, 2500, 55, 0.6), (0.12, 1600, 2800, 60, 0.8)], 5, [(0.02, 0.12, 0.3)]),
        'gun_bolt_fwd': part(0.3, [(0.01, 1800, 3200, 70, 1.0), (0.03, 1200, 2300, 50, 0.6)], 6),
        'gun_shell_in': part(0.3, [(0.03, 1300, 2600, 60, 0.8), (0.06, 900, 1700, 40, 0.4)], 7, [(0.0, 0.05, 0.2)]),
        'gun_pump': part(0.45, [(0.03, 1300, 2500, 55, 0.9), (0.2, 1100, 2300, 50, 1.0)], 8, [(0.0, 0.05, 0.3), (0.15, 0.21, 0.25)]),
        'gun_handling': part(0.5, [(0.05, 700, 1300, 30, 0.25), (0.3, 1800, 3000, 70, 0.4)], 9, [(0.0, 0.3, 0.12)]),
    }
    for k, v in parts.items():
        write(k, v)
    # хитмаркер: сухой «тк», убийство — два тона
    t = np.arange(int(SR * 0.12)) / SR
    rng = np.random.default_rng(12)
    write('hit_marker', np.sin(2 * np.pi * 2600 * t) * env(t, 0.0005, 70) * 0.7 + hp(rng.standard_normal(len(t)), 3) * env(t, 0.0003, 260) * 0.6)
    t = np.arange(int(SR * 0.25)) / SR
    write('hit_kill', np.sin(2 * np.pi * 1900 * t) * env(t, 0.0005, 30) * 0.6 + np.sin(2 * np.pi * 2850 * t) * env(np.clip(t - 0.06, 0, None), 0.0005, 30) * (t > 0.06) * 0.6)
    # гильзы: звон латуни (несколько вариантов), дробь — глухо
    for i in range(3):
        t = np.arange(int(SR * 0.4)) / SR
        rng = np.random.default_rng(40 + i)
        f = [5200 + i * 700, 7900 + i * 500, 11300 - i * 400]
        s = sum(np.sin(2 * np.pi * fi * t + rng.uniform(0, 6)) * np.exp(-t * (14 + j * 9)) * (0.6 / (j + 1)) for j, fi in enumerate(f))
        s += hp(rng.standard_normal(len(t)), 2) * env(t, 0.0002, 400) * 0.4
        s += (t > 0.11) * np.roll(s, int(0.11 * SR)) * 0.35
        write(f'casing_{i}', s)
    t = np.arange(int(SR * 0.25)) / SR
    rng = np.random.default_rng(50)
    write('casing_shell', np.sin(2 * np.pi * 900 * t) * np.exp(-t * 40) * 0.6 + lp(rng.standard_normal(len(t)), 4) * env(t, 0.0005, 60) * 1.2)
    # sounds.json
    sj = os.path.join(ROOT, 'sounds.json')
    with open(sj) as f:
        d = json.load(f)
    for k in list(parts) + ['hit_marker', 'hit_kill', 'casing_shell']:
        d[k] = {'sounds': [{'name': 'novograd:' + k}]}
    d['casing'] = {'sounds': [{'name': f'novograd:casing_{i}'} for i in range(3)]}
    with open(sj, 'w') as f:
        json.dump(d, f, indent=2, ensure_ascii=False)
    print('gun sfx ok')


# ---------------------------------------------------------------- новые стволы и глушитель (python3 tools/gen_gun_sfx.py new)
def suppressed(seed, low=1.0):
    """Выстрел с глушителем: глухой «пфт» + щелчок затвора, без хвоста эха."""
    rng = np.random.default_rng(seed)
    T = 0.45
    t = np.arange(int(SR * T)) / SR
    n = rng.standard_normal(len(t))
    puff = lp(n, 10 + 6 * low) * 5 * env(t, 0.002, 60) + lp(hp(n, 12), 3) * env(t, 0.001, 140) * 0.8
    k = np.clip(t - 0.03, 0, None)
    mech = (t > 0.03) * (np.sin(2 * np.pi * 2600 * k) * 0.5 + hp(n, 3) * 0.5) * np.exp(-k * 150) * 0.6
    return np.tanh((puff + mech) * 1.3) * 0.7


def new_sounds():
    out = {
        'revolver_shot': shot(1.5, 201, crack=1.2, body=1.3, body_lp=30, body_decay=20, thump_f=(170, 50), thump=1.0, mech=0.1, tail=0.7, tail_decay=3,
                              refl=((0.1, 0.4), (0.24, 0.25), (0.45, 0.14))),
        'eagle_shot': shot(1.6, 202, crack=1.4, body=1.4, body_lp=32, body_decay=17, thump_f=(150, 45), thump=1.1, mech=0.35, tail=0.75, tail_decay=2.8,
                           refl=((0.11, 0.4), (0.27, 0.26), (0.5, 0.15))),
        'micro_shot': shot(0.4, 203, crack=0.8, body=0.5, body_lp=14, body_decay=55, thump_f=(280, 110), thump=0.35, mech=0.4, tail=0.25, tail_decay=8,
                           refl=((0.06, 0.25), (0.13, 0.15))),
        'saiga_shot': shot(1.2, 204, crack=1.0, body=1.5, body_lp=42, body_decay=16, thump_f=(140, 42), thump=1.1, mech=0.45, tail=0.7, tail_decay=3.5),
        'carbine_shot': shot(0.8, 205, crack=1.4, body=0.85, body_lp=22, body_decay=30, thump_f=(200, 70), thump=0.7, mech=0.3, tail=0.5, tail_decay=5),
        'lmg_shot': shot(0.75, 206, crack=1.3, body=1.1, body_lp=30, body_decay=26, thump_f=(160, 50), thump=0.9, mech=0.45, tail=0.55, tail_decay=5),
        'silenced_shot': suppressed(207, 1.0),
        'silenced_rifle': suppressed(208, 0.5),
    }
    # гранатомёт: хлопок вышибного заряда + шипение маршевого двигателя
    rng = np.random.default_rng(209)
    T = 1.8
    t = np.arange(int(SR * T)) / SR
    n = rng.standard_normal(len(t))
    boom = shot(T, 210, crack=0.8, body=1.6, body_lp=50, body_decay=12, thump_f=(110, 35), thump=1.2, mech=0.0, tail=0.9, tail_decay=2.4)
    hiss = hp(n, 3) * env(np.clip(t - 0.12, 0, None), 0.05, 1.6) * (t > 0.12) * 0.8
    out['rpg_shot'] = np.tanh(boom + hiss)
    # перезарядки: барабан (открыть, 6 патронов, закрыть), лента пулемёта, граната в трубу
    out['revolver_reload'] = part(2.6, [(0.15, 1500, 2700, 60, 0.9)] + [(0.6 + 0.22 * i, 1300, 2600, 70, 0.6) for i in range(6)]
                                  + [(2.15, 1900, 3300, 70, 1.0)], 211, [(0.1, 0.2, 0.2), (2.05, 2.15, 0.25)])
    out['lmg_reload'] = part(5.0, [(0.3, 1300, 2500, 55, 0.9), (0.8, 1000, 1900, 40, 0.7), (2.4, 1200, 2300, 45, 0.9), (3.6, 1700, 3000, 65, 1.0),
                                   (4.2, 1400, 2600, 55, 0.9), (4.5, 1800, 3200, 70, 1.0)], 212, [(0.9, 1.6, 0.3), (2.6, 3.4, 0.35)])
    out['rpg_reload'] = part(3.2, [(0.3, 700, 1300, 30, 0.5), (2.0, 900, 1600, 35, 0.9), (2.3, 1500, 2700, 60, 1.0)], 213, [(1.2, 2.0, 0.25)])
    for k, v in out.items():
        write(k, v)
    sj = os.path.join(ROOT, 'sounds.json')
    with open(sj) as f:
        d = json.load(f)
    for k in out:
        d[k] = {'sounds': [{'name': 'novograd:' + k}]}
    with open(sj, 'w') as f:
        f.write(json.dumps(d, indent=2, ensure_ascii=False))
    print('new gun sfx ok')


if __name__ == '__main__':
    import sys
    if sys.argv[1:] == ['new']:
        new_sounds()
    else:
        main()
