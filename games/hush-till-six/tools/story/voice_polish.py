# HTS: «студийная» обработка своих записей (владелец — Итан, актриса — Лили) перед voice_uk.py, чтобы они звучали
# в одном ряду с остальными голосами игры: шумодав, срез гула, эквалайзер под тембр английской записи того же героя
# (средний спектр речи «как у эталона», ±8 дБ), де-эссер, мягкая компрессия, лёгкий «воздух» комнаты.
# Эхо помещения, рацию, телефон и стекло игра накладывает сама (engine.js voiceFx) — здесь только чистая запись.
#   python3 tools/story/voice_polish.py <папка с N.wav/mp3> <карта.json {"N": "ключ"}> <папка на выход>
# потом: python3 tools/story/voice_uk.py <папка на выход> <карта.json>
import json, os, subprocess, sys
import numpy as np
from scipy.signal import welch, fftconvolve

SRC, MAPF, OUT = sys.argv[1], sys.argv[2], sys.argv[3]
G = os.path.join(os.path.dirname(os.path.abspath(__file__)), '../../web')
SR = 48000
MAP = json.load(open(MAPF)); os.makedirs(OUT, exist_ok=True)

def rd(p):
    raw = subprocess.run(['ffmpeg', '-v', 'error', '-i', p, '-ac', '1', '-ar', str(SR), '-f', 'f32le', '-'], capture_output=True, check=True).stdout
    return np.frombuffer(raw, np.float32).copy()
def src(n):
    for e in ('wav', 'mp3', 'm4a', 'ogg'):
        if os.path.exists(f'{SRC}/{n}.{e}'): return f'{SRC}/{n}.{e}'
def speech(a):   # только кадры с речью (по громкости) — иначе тишина и шум портят средний спектр
    f = 1024; n = len(a) // f; e = np.sqrt((a[:n * f].reshape(n, f) ** 2).mean(1)) + 1e-9
    keep = e > e.max() * 10 ** (-30 / 20)
    return np.concatenate([a[i * f:(i + 1) * f] for i in range(n) if keep[i]]) if keep.any() else a
def ltas(chunks):
    a = np.concatenate([speech(c) for c in chunks]); f, p = welch(a, SR, nperseg=4096)
    return f, 10 * np.log10(p + 1e-14)
def pre(p):   # шумодав и срез гула до анализа и обработки
    out = subprocess.run(['ffmpeg', '-v', 'error', '-i', p, '-ac', '1', '-ar', str(SR),
                          '-af', 'highpass=f=75,afftdn=nr=14:nf=-48:tn=1,lowpass=f=15500', '-f', 'f32le', '-'], capture_output=True, check=True).stdout
    return np.frombuffer(out, np.float32).copy()

keys = {n: k for n, k in MAP.items() if src(n)}
own = {n: pre(src(n)) for n in keys}
refs = [rd(f'{G}/snd/en/{k}.mp3') for k in keys.values() if os.path.exists(f'{G}/snd/en/{k}.mp3')]
f, Lo = ltas(list(own.values())); _, Lr = ltas(refs)
# поправка: эталон − своя запись, сглаженная по третям октавы, только 90 Гц…12 кГц, 60 % разницы, не больше ±6 дБ; общий уровень — не трогаем
d = Lr - Lo; band = (f > 90) & (f < 12000); d -= np.median(d[band]); d[~band] = 0
sm = np.array([d[(f > x / 2 ** (1 / 6)) & (f < x * 2 ** (1 / 6))].mean() if x > 0 and ((f > x / 2 ** (1 / 6)) & (f < x * 2 ** (1 / 6))).any() else 0 for x in f])
sm = np.clip(sm * .6, -6, 6); sm[~band] *= 0   # 60 % поправки: ближе к эталону, но голос остаётся своим
taps = 2049; H = 10 ** (np.interp(np.fft.rfftfreq(taps * 2, 1 / SR), f, sm) / 20)
h = np.fft.irfft(H); h = np.roll(h, taps)[:2 * taps - 1] * np.hanning(2 * taps - 1)
print('эквалайзер (дБ):', ' '.join(f'{int(x)}:{sm[np.argmin(abs(f - x))]:+.1f}' for x in (100, 200, 400, 800, 1600, 3200, 6400, 10000)))
for n, k in keys.items():
    a = fftconvolve(own[n], h, 'same').astype(np.float32)
    raw = os.path.join(OUT, f'_{n}.f32')
    a.tofile(raw)
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-f', 'f32le', '-ar', str(SR), '-ac', '1', '-i', raw, '-af',
                    'deesser=i=0.4:m=0.5:f=0.5,'
                    'acompressor=threshold=-22dB:ratio=3:attack=6:release=90:makeup=2,'
                    'aecho=0.9:0.85:11|23:0.06|0.04,'                     # «воздух» маленькой комнаты, почти неслышный
                    'alimiter=limit=0.89:level=false', os.path.join(OUT, f'{n}.wav')], check=True)
    os.remove(raw)
    print(n, k, 'ok')
