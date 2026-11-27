#!/usr/bin/env python3
"""0.9.8 «Руки»: звуки взаимодействий — карта в банкомат, клавиши, счётчик купюр, монетка, банка, пистолет колонки,
ремень, зажигалка, дверь машины, свист биты и ножа… Всё синтезируется здесь (numpy), без сэмплов; моно, короткие,
ogg q3 — весь набор меньше 300 КБ (Full-архив должен остаться < 100 МБ).

Запуск из папки novograd: python3 tools/gen_hands_sfx.py  (пишет sounds/hands/*.ogg и записи «hands.*» в sounds.json)
"""
import json, os, subprocess, wave
import numpy as np

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'novograd')
SND = os.path.join(ROOT, 'sounds', 'hands')
SR = 44100
RNG = np.random.default_rng(98)


def write(name, sig, q=3):
    sig = np.asarray(sig, float)
    sig = sig / (np.max(np.abs(sig)) + 1e-9) * 0.92
    fade = min(len(sig), int(SR * 0.006))
    sig[-fade:] *= np.linspace(1, 0, fade)
    os.makedirs(SND, exist_ok=True)
    tmp = os.path.join('/tmp', f'nh_{name}.wav')
    with wave.open(tmp, 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes((np.clip(sig, -1, 1) * 32767).astype(np.int16).tobytes())
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', tmp, '-c:a', 'libvorbis', '-q:a', str(q), os.path.join(SND, name + '.ogg')], check=True)
    os.remove(tmp)


def T(sec):
    return np.arange(int(SR * sec)) / SR


def lp(x, k):
    if k <= 1:
        return x
    ker = np.ones(int(k)) / int(k)
    return np.convolve(np.convolve(x, ker, mode='same'), ker, mode='same')


def hp(x, k):
    return x - lp(x, k)


def noise(sec):
    return RNG.standard_normal(len(T(sec)))


def env(t, a, d):
    return np.minimum(1, t / max(a, 1e-4)) * np.exp(-np.maximum(t - a, 0) * d)


def mix(*xs):
    n = max(len(x) for x in xs)
    out = np.zeros(n)
    for x in xs:
        out[:len(x)] += x
    return out


def place(total, at, x):
    out = np.zeros(int(SR * total))
    i = int(SR * at)
    n = min(len(x), len(out) - i)
    if n > 0:
        out[i:i + n] += x[:n]
    return out


def click(sec=0.03, f=3000, d=180, k=6):
    t = T(sec)
    return hp(noise(sec), k) * env(t, 0.0005, d) * 0.6 + np.sin(2 * np.pi * f * t) * env(t, 0.0003, d * 1.3) * 0.5


def tone(sec, f, d=6, a=0.004):
    t = T(sec)
    return np.sin(2 * np.pi * f * t) * env(t, a, d)


def thump(sec=0.25, f0=120, f1=45, d=18):
    t = T(sec)
    f = f1 + (f0 - f1) * np.exp(-t * 25)
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * env(t, 0.002, d)


def slide(sec, k_lo=8, k_hi=60, amp_d=3):
    t = T(sec)
    s = lp(hp(noise(sec), k_lo), 2) * (0.4 + 0.6 * np.sin(np.pi * t / sec))
    return s * np.exp(-t * amp_d * 0.2)


def rustle(sec, density=30):
    t = T(sec)
    out = lp(hp(noise(sec), 5), 3) * 0.15
    for _ in range(int(density * sec)):
        at = RNG.uniform(0, sec * 0.9)
        out += place(sec, at, hp(noise(0.03), 4) * env(T(0.03), 0.001, 90) * RNG.uniform(0.3, 1))
    return out * np.sin(np.pi * t / sec) ** 0.5


def sounds():
    s = {}
    # банкомат
    s['card_in'] = mix(slide(0.32, 6) * 0.5, place(0.32, 0.26, click(0.05, 1800, 120)) * 1.2)
    s['card_out'] = place(0.35, 0.0, click(0.04, 1500, 150)) + place(0.35, 0.04, slide(0.3, 6) * 0.5)
    s['key'] = place(0.16, 0, click(0.02, 2500, 300) * 0.7) + place(0.16, 0.01, tone(0.14, 1250, 30, 0.002) * 0.5)
    t = T(1.0)
    whirr = (np.sin(2 * np.pi * 95 * t) * 0.3 + lp(noise(1.0), 6) * 0.35) * np.minimum(1, t / 0.08) * np.minimum(1, (1.0 - t) / 0.1)
    for i in range(9):
        whirr += place(1.0, 0.12 + i * 0.085, hp(noise(0.025), 3) * env(T(0.025), 0.001, 120) * 0.7)
    s['atm_whirr'] = whirr
    s['cash'] = rustle(0.45, 40)
    # автомат, ларёк, касса
    t = T(0.6)
    s['coin'] = place(0.6, 0, mix(tone(0.3, 3400, 14) * 0.6, tone(0.3, 5100, 20) * 0.3, click(0.02, 4000, 200))) + \
        sum(place(0.6, 0.18 + i * 0.07 * (1 - i * 0.15), tone(0.08, 2600 - i * 200, 40) * (0.5 - i * 0.1)) for i in range(4))
    s['button'] = mix(click(0.06, 900, 90), place(0.06, 0.012, click(0.04, 600, 120) * 0.6))
    s['can_drop'] = place(0.7, 0, thump(0.35, 180, 60, 14)) + place(0.7, 0.0, hp(noise(0.2), 3) * env(T(0.2), 0.001, 25) * 0.6) + \
        place(0.7, 0.22, thump(0.25, 140, 70, 20) * 0.5) + place(0.7, 0.36, tone(0.25, 700, 18) * 0.25)
    t = T(0.55)
    s['can_open'] = mix(place(0.55, 0, click(0.03, 1500, 150) * 1.2), place(0.55, 0.02, hp(noise(0.5), 2) * env(T(0.5), 0.004, 7) * 0.7))
    t = T(0.32)
    s['gulp'] = mix(np.sin(2 * np.pi * np.cumsum(220 + 300 * np.exp(-t * 18)) / SR) * env(t, 0.01, 14) * 0.8, lp(noise(0.32), 9) * env(t, 0.005, 20) * 0.3)
    s['crunch'] = sum(place(0.4, RNG.uniform(0, 0.25), hp(noise(0.05), 2) * env(T(0.05), 0.001, 70)) for _ in range(9))
    s['bag_tear'] = rustle(0.5, 70) * 0.7 + place(0.5, 0.2, hp(noise(0.25), 2) * env(T(0.25), 0.01, 10) * 0.6)
    s['wrapper'] = rustle(0.6, 55)
    s['scan'] = tone(0.18, 2300, 3, 0.002)
    s['tap'] = place(0.35, 0, tone(0.11, 1900, 4, 0.002)) + place(0.35, 0.14, tone(0.16, 2400, 4, 0.002))
    t = T(0.9)
    s['receipt'] = (np.sign(np.sin(2 * np.pi * 180 * t)) * 0.12 + hp(noise(0.9), 3) * 0.2) * np.minimum(1, t / 0.02) * np.minimum(1, (0.9 - t) / 0.05)
    s['bite'] = mix(hp(noise(0.12), 3) * env(T(0.12), 0.002, 40), place(0.12, 0.02, lp(noise(0.1), 10) * env(T(0.1), 0.004, 30) * 0.6))
    # дверь, колонка, ремонт, сейф
    s['door_handle'] = place(0.5, 0, click(0.05, 1100, 80)) + place(0.5, 0.08, click(0.06, 800, 60) * 0.7) + \
        place(0.5, 0.18, (np.sin(2 * np.pi * np.cumsum(300 + 120 * np.sin(T(0.3) * 9)) / SR) * env(T(0.3), 0.05, 5) * 0.15))
    s['nozzle'] = place(0.4, 0, mix(click(0.06, 1700, 60), thump(0.15, 300, 160, 30) * 0.5)) + place(0.4, 0.09, tone(0.25, 900, 15) * 0.25)
    t = T(2.4)
    flow = lp(noise(2.4), 4) * 0.5 + lp(noise(2.4), 25) * 0.6
    gurg = np.sin(2 * np.pi * np.cumsum(140 + 60 * np.sin(t * 23) + 40 * np.sin(t * 7.3)) / SR) * 0.15
    s['fuel_flow'] = (flow + gurg) * np.minimum(1, t / 0.15) * np.minimum(1, (2.4 - t) / 0.15)
    s['ratchet'] = sum(place(0.5, i * 0.045, click(0.03, 3200, 220) * (0.8 if i % 2 else 0.5)) for i in range(10))
    s['dial'] = click(0.03, 4200, 260) * 0.8
    s['safe_open'] = place(0.9, 0, mix(thump(0.3, 160, 50, 12) * 0.9, click(0.05, 900, 60))) + \
        place(0.9, 0.15, np.sin(2 * np.pi * np.cumsum(180 + 90 * np.sin(T(0.7) * 5)) / SR) * env(T(0.7), 0.1, 3) * 0.25)
    # машина
    s['car_door_open'] = place(0.6, 0, click(0.05, 1200, 90)) + place(0.6, 0.05, thump(0.15, 200, 120, 30) * 0.4) + \
        place(0.6, 0.1, lp(noise(0.45), 4) * env(T(0.45), 0.08, 5) * 0.2)
    s['car_door_close'] = mix(thump(0.5, 140, 45, 10), place(0.5, 0, hp(noise(0.1), 3) * env(T(0.1), 0.001, 50) * 0.5) + place(0.5, 0.01, click(0.04, 900, 90) * 0.6))
    s['belt'] = place(0.45, 0, slide(0.25, 5) * 0.4) + place(0.45, 0.28, mix(click(0.05, 2600, 140) * 1.2, click(0.04, 1300, 160) * 0.6))
    # курение и питьё
    s['lighter'] = mix(place(0.7, 0, hp(noise(0.06), 2) * env(T(0.06), 0.001, 60)), place(0.7, 0.05, lp(noise(0.6), 3) * env(T(0.6), 0.05, 4) * 0.35))
    t = T(1.1)
    s['exhale'] = lp(hp(noise(1.1), 6), 4) * np.sin(np.pi * t / 1.1) ** 1.5 * 0.6
    s['bottle'] = place(0.4, 0, mix(tone(0.3, 520, 10) * 0.5, tone(0.3, 1040, 16) * 0.2, click(0.02, 3000, 200) * 0.5))
    # оружие: кобура, свист
    s['holster'] = mix(slide(0.3, 3) * 0.6, place(0.3, 0.22, click(0.04, 700, 90) * 0.5))
    for name, sec, k, amp in (('whoosh_bat', 0.4, 18, 1.0), ('whoosh_knife', 0.22, 4, 0.8), ('whoosh_fist', 0.2, 10, 0.7)):
        t = T(sec)
        s[name] = lp(noise(sec), k) * np.sin(np.pi * t / sec) ** 2 * amp
    return s


def main():
    snd = sounds()
    for name, sig in snd.items():
        write(name, sig)
    path = os.path.join(ROOT, 'sounds.json')
    with open(path, encoding='utf-8') as f:
        js = json.load(f)
    for name in snd:
        js['hands.' + name] = {'sounds': [{'name': 'novograd:hands/' + name}], 'subtitle': 'subtitles.novograd.hands'}
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(js, f, ensure_ascii=False, indent=2)
        f.write('\n')
    total = sum(os.path.getsize(os.path.join(SND, n + '.ogg')) for n in snd)
    print(f'{len(snd)} звуков, {total // 1024} КБ')


if __name__ == '__main__':
    main()
