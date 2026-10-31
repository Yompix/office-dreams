"""Звуки WHAT THE LAKE KEEPS, которые сделаны кодом (numpy), а не в ElevenLabs:
  underwater — пролог: глухой подводный гул и пузыри (10 с, петля);
  ui_click   — щелчок выбора и кнопок меню (ElevenLabs дал почти тишину);
  siren      — глава 2, бегство с поста: вой сирены за машиной — громко, потом глуше и тише (отстали в снегу);
  knock      — глава 3: стук костяшками в деревянную дверь (один удар; три — игра ставит с паузами);
  baby_cry   — глава 3: плач новорождённой (тонкий голос с дрожью, вдох-всхлип, «уа-а»);
  phone_ring — глава 4: старый кнопочный телефон Агнес звонит (две трели);
  steam      — глава 4: пар из-под капота закипевшей буханки (шипение, петля);
  frogs      — глава 5: летняя ночь у озера, лягушки (хор с перекличкой, петля);
  ice_crack  — глава 5: лёд трещит и проваливается (низкий гул, щелчки, хруст).
  python3 tools/lake/synth.py   → godot/lake/snd/<имя>.ogg"""
import os, subprocess, wave
import numpy as np

SR = 44100
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '../../godot/lake/snd')
rng = np.random.default_rng(3)


def lowpass(x, fc, passes=2):
    a = np.exp(-2 * np.pi * fc / SR)
    for _ in range(passes):
        out = np.empty_like(x)
        acc = 0.0
        for i in range(len(x)):
            acc = a * acc + (1 - a) * x[i]
            out[i] = acc
        x = out
    return x


def save(name, x, peak=0.8):
    x = x / max(1e-9, np.max(np.abs(x))) * peak
    wav = '/tmp/lake_%s.wav' % name
    with wave.open(wav, 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes((x * 32767).astype('<i2').tobytes())
    subprocess.run(['ffmpeg', '-nostdin', '-v', 'error', '-y', '-i', wav, '-c:a', 'libvorbis', '-q:a', '4',
                    os.path.join(OUT, name + '.ogg')], check=True)
    print(name)


def underwater():
    T = 10.0
    n = int(SR * T)
    t = np.arange(n) / SR
    rumble = lowpass(rng.normal(0, 1, n), 90.0) * 6.0 * (0.7 + 0.3 * np.sin(2 * np.pi * 0.1 * t))
    rumble += 0.25 * np.sin(2 * np.pi * 38 * t) * (0.5 + 0.5 * np.sin(2 * np.pi * 0.2 * t))
    bub = np.zeros(n)
    for _ in range(70):                    # пузыри: короткие «блупы» с растущим тоном
        s = rng.uniform(0, T - 0.2); d = rng.uniform(0.03, 0.09); f0 = rng.uniform(300, 900)
        i0 = int(s * SR); m = int(d * SR); tt = np.arange(m) / SR
        bub[i0:i0 + m] += rng.uniform(0.05, 0.18) * np.sin(2 * np.pi * (f0 * tt + 2500 * tt * tt)) * np.exp(-tt / (d * 0.4))
    x = rumble + lowpass(bub, 1400.0) * 2.5
    k = int(0.5 * SR)                      # стык петли: конец плавно переходит в начало
    x[:k] = x[:k] * np.linspace(0, 1, k) + x[-k:] * np.linspace(1, 0, k)
    save('underwater', x[:-k])


def ui_click():
    n = int(0.12 * SR)
    t = np.arange(n) / SR
    x = 0.6 * np.sin(2 * np.pi * 1450 * t) * np.exp(-t / 0.007)
    x += 0.35 * np.sin(2 * np.pi * 420 * t) * np.exp(-t / 0.018)
    x += 0.25 * lowpass(rng.normal(0, 1, n), 3000.0, 1) * np.exp(-t / 0.002)
    x[:20] *= np.linspace(0, 1, 20)
    save('ui_click', x, 0.7)


def siren():
    T = 7.0
    n = int(SR * T)
    t = np.arange(n) / SR
    f = 720 + 640 * (0.5 - 0.5 * np.cos(2 * np.pi * t / 3.1))   # «вой»: вверх-вниз за 3,1 с
    ph = 2 * np.pi * np.cumsum(f) / SR
    tone = np.sin(ph) + 0.45 * np.sin(2 * ph) + 0.3 * np.sin(3 * ph) + 0.12 * np.sin(5 * ph)
    tone = np.tanh(1.6 * tone)                                    # рупор: жёстко, с призвуком
    far = lowpass(tone, 700.0, 2)                                 # издалека — глухо
    k = np.clip((t - 1.4) / 3.6, 0.0, 1.0)                        # отстают: ближний звук переходит в дальний
    x = tone * (1 - k) + far * k * 1.6
    x *= np.minimum(1.0, t / 0.25) * np.exp(-np.maximum(0.0, t - 1.6) / 1.9)
    for d, g in ((0.11, 0.35), (0.27, 0.22), (0.46, 0.12)):       # эхо от леса вдоль дороги
        i = int(d * SR)
        x[i:] += g * x[:-i]
    fo = int(0.6 * SR)
    x[-fo:] *= np.linspace(1, 0, fo) ** 2
    save('siren', x, 0.75)


def knock():
    n = int(0.35 * SR)
    t = np.arange(n) / SR
    body = np.sin(2 * np.pi * 145 * t) * np.exp(-t / 0.05) + 0.6 * np.sin(2 * np.pi * 310 * t) * np.exp(-t / 0.025)
    hit = lowpass(rng.normal(0, 1, n), 1800.0, 1) * np.exp(-t / 0.006) * 1.5
    x = body + hit
    for d, g in ((0.018, 0.35), (0.041, 0.18)):   # гулкая дверь
        i = int(d * SR)
        x[i:] += g * x[:-i]
    save('knock', x, 0.8)


def baby_cry():
    T = 4.2
    n = int(SR * T)
    t = np.arange(n) / SR
    x = np.zeros(n)
    for t0, d in ((0.15, 1.1), (1.55, 1.25), (3.05, 0.95)):   # три «уа-а» с всхлипами между
        m = (t >= t0) & (t < t0 + d)
        tt = t[m] - t0
        f = 420 + 90 * np.sin(np.pi * tt / d) + 25 * np.sin(2 * np.pi * 7.5 * tt) + 40 * (tt < 0.08) * (0.08 - tt) / 0.08
        ph = 2 * np.pi * np.cumsum(f) / SR
        env = np.minimum(1, tt / 0.06) * np.minimum(1, (d - tt) / 0.18) * (0.75 + 0.25 * np.sin(2 * np.pi * 3.1 * tt))
        v = sum((0.9 ** h) * np.sin(h * ph) for h in range(1, 9))
        vow = 0.55 + 0.45 * np.minimum(1, tt / 0.25)                   # «у» → «а»: верх раскрывается
        x[m] += env * (v * vow + 0.15 * np.sin(ph))
        s0 = int((t0 + d + 0.05) * SR)
        sob = lowpass(rng.normal(0, 1, int(0.22 * SR)), 2500.0, 1) * np.exp(-np.arange(int(0.22 * SR)) / (0.06 * SR)) * 0.25
        x[s0:s0 + len(sob)] += sob[:max(0, min(len(sob), n - s0))]
    x = lowpass(x, 5200.0, 1)
    save('baby_cry', x, 0.7)


def phone_ring():
    T = 2.6
    n = int(SR * T)
    t = np.arange(n) / SR
    x = np.zeros(n)
    for t0 in (0.0, 1.3):   # трель: два тона попеременно 25 раз в секунду
        m = (t >= t0) & (t < t0 + 0.9)
        tt = t[m] - t0
        f = np.where((tt * 25).astype(int) % 2 == 0, 1100.0, 1350.0)
        x[m] = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.minimum(1, tt / 0.01) * np.minimum(1, (0.9 - tt) / 0.03)
    x = lowpass(x, 3200.0, 1)
    save('phone_ring', x, 0.6)


def steam():
    T = 6.0
    n = int(SR * T)
    x = rng.normal(0, 1, n)
    hi = x - lowpass(x, 2500.0, 1)                       # шипение — верх шума
    t = np.arange(n) / SR
    env = 0.75 + 0.25 * np.sin(2 * np.pi * 0.7 * t) + 0.1 * np.sin(2 * np.pi * 2.3 * t)
    bub = np.zeros(n)
    for k in range(40):                                    # бульканье в радиаторе
        i = int(rng.uniform(0, T - 0.1) * SR)
        m = int(0.06 * SR)
        tt = np.arange(m) / SR
        bub[i:i + m] += np.sin(2 * np.pi * rng.uniform(180, 320) * tt) * np.exp(-tt / 0.015) * 0.5
    x = hi * env + bub
    fade = int(0.3 * SR)
    x[:fade] *= np.linspace(0, 1, fade)                     # петля без щелчка
    x[-fade:] *= np.linspace(1, 0, fade)
    save('steam', x, 0.5)


def frogs():
    T = 12.0
    n = int(SR * T)
    x = np.zeros(n)
    for voice, (f0, rate, gain) in enumerate(((520, 2.6, 1.0), (380, 1.8, 0.8), (760, 3.4, 0.5), (300, 1.1, 0.7))):
        tcur = rng.uniform(0, 0.8)
        while tcur < T - 0.5:
            dur = rng.uniform(0.12, 0.3)
            m = int(dur * SR)
            tt = np.arange(m) / SR
            f = f0 * (1 + 0.08 * np.sin(2 * np.pi * 9 * tt))
            pulse = (np.sin(2 * np.pi * rate * 12 * tt) > 0).astype(float) * 0.6 + 0.4   # «кваканье» — частые импульсы
            c = np.sin(2 * np.pi * np.cumsum(f) / SR) * pulse * np.sin(np.pi * tt / dur) ** 0.5
            c += 0.3 * np.sin(2 * np.pi * np.cumsum(f * 2) / SR) * pulse * np.sin(np.pi * tt / dur)
            i = int(tcur * SR)
            x[i:i + m] += c[:max(0, min(m, n - i))] * gain * rng.uniform(0.5, 1.0)
            tcur += dur + rng.exponential(0.6 / (voice + 1)) + 0.15
    x = lowpass(x, 2600.0, 1) + 0.04 * lowpass(rng.normal(0, 1, n), 400.0, 1)   # и ночной фон
    fade = int(0.4 * SR)
    x[:fade] *= np.linspace(0, 1, fade)
    x[-fade:] *= np.linspace(1, 0, fade)
    save('frogs', x, 0.6)


def ice_crack():
    T = 3.2
    n = int(SR * T)
    t = np.arange(n) / SR
    x = np.zeros(n)
    boom = np.sin(2 * np.pi * (70 - 25 * t) * t) * np.exp(-t / 0.9) * 0.8          # гул по льду
    x += boom
    for k in range(26):                                                           # щелчки трещин — гуще к концу
        t0 = (k / 26) ** 0.7 * 2.2 + rng.uniform(0, 0.05)
        i = int(t0 * SR)
        m = int(0.05 * SR)
        tt = np.arange(m) / SR
        x[i:i + m] += rng.normal(0, 1, m) * np.exp(-tt / 0.004) * rng.uniform(0.4, 1.0)
        x[i:i + m] += np.sin(2 * np.pi * rng.uniform(900, 2400) * tt) * np.exp(-tt / 0.01) * 0.4
    i = int(2.3 * SR)                                                             # провал: хруст и всплеск
    m = n - i
    tt = np.arange(m) / SR
    x[i:] += lowpass(rng.normal(0, 1, m), 1500.0, 1) * np.exp(-tt / 0.35) * 1.4
    save('ice_crack', x, 0.85)


if __name__ == '__main__':
    import sys
    for name in (sys.argv[1:] or ['underwater', 'ui_click', 'siren', 'knock', 'baby_cry', 'phone_ring', 'steam', 'frogs', 'ice_crack']):
        globals()[name]()
