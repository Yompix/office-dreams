"""Звуки WHAT THE LAKE KEEPS, которые сделаны кодом (numpy), а не в ElevenLabs:
  underwater — пролог: глухой подводный гул и пузыри (10 с, петля);
  ui_click   — щелчок выбора и кнопок меню (ElevenLabs дал почти тишину);
  siren      — глава 2, бегство с поста: вой сирены за машиной — громко, потом глуше и тише (отстали в снегу);
  knock      — глава 3: стук костяшками в деревянную дверь (один удар; три — игра ставит с паузами);
  baby_cry   — глава 3: плач новорождённой (тонкий голос с дрожью, вдох-всхлип, «уа-а»);
  phone_ring — глава 4: старый кнопочный телефон Агнес звонит (две трели);
  steam      — глава 4: пар из-под капота закипевшей буханки (шипение, петля);
  frogs      — глава 5: летняя ночь у озера, лягушки (хор с перекличкой, петля);
  ice_crack  — глава 5: лёд трещит и проваливается (низкий гул, щелчки, хруст);
  pump_run   — глава 1, заправка «24»: насос колонки качает — гул мотора, шум топлива в горловине, тиканье счётчика
               (9 с; играет, пока игрок держит кнопку, отпустил — стоп);
  car_arrive, engine_off, belt_click — «24», вступление: машина подкатывает к колонке, ключ — мотор глохнет, ремень;
  phone_pickup, dial_tone, phone_dial — «24», прилавок: трубку сняли, гудок, набор номера;
  breath_ragged, groan — «24», провал: рваное дыхание Ноя, стон Романа за стеклом.
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


def pump_run():
    n = int(9.0 * SR)
    t = np.arange(n) / SR
    spin = np.clip(t / 0.18, 0, 1)                                                   # мотор разгоняется
    f0 = 98.0 * (0.92 + 0.08 * spin) * (1 + 0.004 * np.sin(2 * np.pi * 0.7 * t))
    ph = 2 * np.pi * np.cumsum(f0) / SR
    motor = (np.sin(ph) + 0.5 * np.sin(2 * ph) + 0.25 * np.sin(3 * ph) + 0.12 * np.sin(5 * ph)) * spin
    motor *= 1 + 0.08 * np.sin(2 * np.pi * 7.5 * t)                                  # пульсация поршня
    flow = lowpass(rng.normal(0, 1, n), 700.0, 2)                                    # бензин шумит в горловине
    gurgle = 1 + 0.5 * lowpass(rng.normal(0, 1, n), 3.0, 1) * 18                     # неровно, с «бульками»
    flow *= np.clip(gurgle, 0.3, 2.0) * np.clip((t - 0.25) / 0.4, 0, 1)
    tick = np.zeros(n)                                                               # счётчик: колёсики цифр
    for k in range(int(9.0 * 9)):
        i = int((0.3 + k / 9.0) * SR)
        if i + 400 < n:
            tt = np.arange(400) / SR
            tick[i:i + 400] += np.sin(2 * np.pi * 2400 * tt) * np.exp(-tt / 0.002) * (0.6 if k % 10 else 1.0)
    x = 0.55 * motor + 0.9 * flow + 0.18 * tick
    x *= np.clip((9.0 - t) / 0.3, 0, 1)                                              # без щелчка в конце
    save('pump_run', x, 0.7)


def car_arrive():
    """Глава 1, заправка «24», вступление: машина сбрасывает ход и подкатывает к колонке — мотор на сбросе газа,
    шины по укатанному снегу (хруст стихает к остановке), под конец — холостые обороты. 6,6 с."""
    dur = 6.6
    n = int(dur * SR)
    t = np.arange(n) / SR
    k = np.clip(t / 6.0, 0, 1)
    v = (1 - k) ** 1.0                                                               # скорость: от ~45 км/ч к нулю
    rpm = 0.55 + 0.45 * v                                                            # обороты падают к холостым
    f0 = 38.0 * rpm * (1 + 0.006 * np.sin(2 * np.pi * 0.9 * t))
    ph = 2 * np.pi * np.cumsum(f0) / SR
    eng = np.sin(ph) + 0.6 * np.sin(2 * ph) + 0.35 * np.sin(3 * ph) + 0.15 * np.sin(4.5 * ph)
    eng = lowpass(eng, 420.0, 2) * (0.7 + 0.3 * v)
    crunch = lowpass(rng.normal(0, 1, n), 1500.0, 1) - lowpass(rng.normal(0, 1, n), 120.0, 1) * 0.5
    grit = np.clip(lowpass(rng.normal(0, 1, n), 25.0, 1) * 9, 0.2, 1.6)              # неровный хруст наста
    crunch *= grit * v ** 0.7
    hiss = lowpass(rng.normal(0, 1, n), 3000.0, 1) * 0.25 * v                        # снежная пыль из-под колёс
    x = 0.6 * eng + 0.5 * crunch + hiss
    x *= np.clip(t / 0.6, 0, 1) * np.clip((dur - t) / 0.4, 0, 1)                     # издалека — нарастает
    save('car_arrive', x, 0.7)


def engine_off():
    """Ключ на себя: щелчок замка зажигания, мотор глохнет с вздохом, остывающий двигатель тикает. 2,4 с."""
    dur = 2.4
    n = int(dur * SR)
    t = np.arange(n) / SR
    click = np.zeros(n)
    for c0, f, a in ((0.02, 3200, 0.8), (0.07, 2100, 0.5)):                         # ключ: два щелчка замка
        i = int(c0 * SR)
        tt = np.arange(500) / SR
        click[i:i + 500] += a * np.sin(2 * np.pi * f * tt) * np.exp(-tt / 0.003)
    dying = np.clip((0.85 - t) / 0.7, 0, 1)                                          # мотор глохнет за 0,7 с
    f0 = 36.0 * (0.4 + 0.6 * dying)
    ph = 2 * np.pi * np.cumsum(f0) / SR
    eng = (np.sin(ph) + 0.5 * np.sin(2 * ph) + 0.3 * np.sin(3 * ph)) * dying ** 1.5
    shudder = np.exp(-((t - 0.8) / 0.05) ** 2) * np.sin(2 * np.pi * 22 * t)          # последний толчок кузова
    tick = np.zeros(n)
    for c0 in (1.25, 1.75, 2.1):                                                     # остывающий металл
        i = int(c0 * SR)
        tt = np.arange(300) / SR
        if i + 300 < n:
            tick[i:i + 300] += np.sin(2 * np.pi * 5200 * tt) * np.exp(-tt / 0.0015)
    x = 0.5 * click + 0.7 * lowpass(eng, 380.0, 2) + 0.4 * shudder + 0.12 * tick
    save('engine_off', x, 0.7)


def belt_click():
    """Ремень безопасности: щелчок пряжки и шорох ленты, уезжающей вверх. 1,0 с."""
    dur = 1.0
    n = int(dur * SR)
    t = np.arange(n) / SR
    click = np.zeros(n)
    i = int(0.02 * SR)
    tt = np.arange(700) / SR
    click[i:i + 700] += np.sin(2 * np.pi * 2600 * tt) * np.exp(-tt / 0.004) + 0.5 * np.sin(2 * np.pi * 4100 * tt) * np.exp(-tt / 0.002)
    slide = lowpass(rng.normal(0, 1, n), 2500.0, 1) - lowpass(rng.normal(0, 1, n), 400.0, 1)
    slide *= np.clip((t - 0.08) / 0.05, 0, 1) * np.clip((0.75 - t) / 0.3, 0, 1)
    x = 0.8 * click + 0.25 * slide
    save('belt_click', x, 0.6)


def phone_pickup():
    """Трубку сняли с рычагов (или положили): пластиковый стук и щелчок рычага. 0,4 с."""
    dur = 0.4
    n = int(dur * SR)
    t = np.arange(n) / SR
    x = np.zeros(n)
    for c0, f, a, d in ((0.0, 900, 0.7, 0.012), (0.03, 2400, 0.5, 0.004), (0.11, 1800, 0.35, 0.003)):
        i = int(c0 * SR)
        tt = np.arange(n - i) / SR
        x[i:] += a * np.sin(2 * np.pi * f * tt) * np.exp(-tt / d)
    x += 0.15 * lowpass(rng.normal(0, 1, n), 2000.0, 1) * np.exp(-t / 0.03)
    save('phone_pickup', x, 0.6)


def dial_tone():
    """Гудок в трубке (425 Гц, ровный), слышен из трубки у Романа — тихо, с хрипом линии. 3 с."""
    dur = 3.0
    n = int(dur * SR)
    t = np.arange(n) / SR
    x = np.sin(2 * np.pi * 425 * t) * (0.9 + 0.1 * np.sin(2 * np.pi * 0.7 * t))
    x = lowpass(x + 0.08 * rng.normal(0, 1, n), 1800.0, 1)
    x *= np.clip(t / 0.05, 0, 1) * np.clip((dur - t) / 0.2, 0, 1)
    save('dial_tone', x, 0.5)


def phone_dial():
    """Набор номера на кнопочном аппарате: короткие щелчки кнопок с тонами (10 цифр, неровный темп). 2 с."""
    dur = 2.0
    n = int(dur * SR)
    x = np.zeros(n)
    tones = [(697, 1209), (770, 1336), (852, 1477), (941, 1336), (697, 1336), (770, 1209), (852, 1336), (697, 1477), (941, 1209), (770, 1477)]
    c = 0.05
    for k, (fa, fb) in enumerate(tones):
        i = int(c * SR)
        m = int(0.09 * SR)
        if i + m >= n:
            break
        tt = np.arange(m) / SR
        x[i:i + m] += 0.3 * (np.sin(2 * np.pi * fa * tt) + np.sin(2 * np.pi * fb * tt)) * np.clip((0.09 - tt) / 0.01, 0, 1)
        x[i:i + 300] += 0.6 * np.sin(2 * np.pi * 3000 * tt[:300]) * np.exp(-tt[:300] / 0.002)   # кнопка
        c += 0.13 + 0.05 * ((k * 7) % 3)
    save('phone_dial', x, 0.55)


def breath_ragged():
    """Частое рваное дыхание после провала: короткие вдохи сквозь зубы и выдохи паром. 3 с."""
    dur = 3.0
    n = int(dur * SR)
    t = np.arange(n) / SR
    x = np.zeros(n)
    c = 0.05
    k = 0
    while c < dur - 0.3:
        ln = 0.22 if k % 2 == 0 else 0.3          # вдох короче выдоха
        i0, i1 = int(c * SR), int((c + ln) * SR)
        m = i1 - i0
        env = np.sin(np.linspace(0, np.pi, m)) ** (1.5 if k % 2 == 0 else 0.8)
        noise = rng.normal(0, 1, m)
        band = lowpass(noise, 2600.0 if k % 2 == 0 else 1300.0, 1) - lowpass(noise, 350.0, 1)
        x[i0:i1] += band * env * (0.8 if k % 2 == 0 else 0.6)
        c += ln + (0.05 if k % 2 == 0 else 0.12)
        k += 1
    save('breath_ragged', x, 0.6)


def groan():
    """Роман на полу за стеклом стонет: низкий голос с «о», высота падает, глухо (фильтр стекла). 3,2 с."""
    dur = 3.2
    n = int(dur * SR)
    t = np.arange(n) / SR
    x = np.zeros(n)
    for c0, ln, f0 in ((0.1, 1.1, 118.0), (1.6, 1.3, 108.0)):
        i0 = int(c0 * SR)
        m = int(ln * SR)
        tt = np.arange(m) / SR
        f = f0 * (1.0 - 0.18 * tt / ln) * (1 + 0.012 * np.sin(2 * np.pi * 5.5 * tt))   # падает, дрожит
        ph = 2 * np.pi * np.cumsum(f) / SR
        glot = sum(np.sin(h * ph) / h ** 1.2 for h in range(1, 14))                 # голосовой источник
        voc = np.zeros(m)
        for fm, bw, g in ((480.0, 90.0, 1.0), (820.0, 110.0, 0.55), (2400.0, 160.0, 0.12)):   # гласная «о»
            r = np.exp(-np.pi * bw / SR)
            th = 2 * np.pi * fm / SR
            y1 = y2 = 0.0
            out = np.zeros(m)
            for i in range(m):
                y = glot[i] + 2 * r * np.cos(th) * y1 - r * r * y2
                out[i] = y
                y2, y1 = y1, y
            voc += g * out / np.max(np.abs(out) + 1e-9)
        env = np.sin(np.linspace(0, np.pi, m)) ** 0.7
        x[i0:i0 + m] += voc * env + 0.15 * lowpass(rng.normal(0, 1, m), 1500.0, 1) * env
    x = lowpass(x, 900.0, 2)   # сквозь стекло витрины
    save('groan', x, 0.5)


if __name__ == '__main__':
    import sys
    for name in (sys.argv[1:] or ['underwater', 'ui_click', 'siren', 'knock', 'baby_cry', 'phone_ring', 'steam', 'frogs', 'ice_crack']):
        globals()[name]()
