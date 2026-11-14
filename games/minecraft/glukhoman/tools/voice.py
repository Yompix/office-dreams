#!/usr/bin/env python3
"""Озвучка реплик «Глухомани» нейросетью XTTS-v2 (локально, лицензия Coqui CPML — только некоммерческое использование).
Реплики берутся из src/main/resources/glukhoman_story.json (раздел lines). Каждую переслушивает Whisper; плохо слышно — ещё дубль.
Потом звук обрабатывается под источник: радио (полоса + треск), плёнка (шипение + плавание), Хор (несколько голосов
со сдвигом тона и эхом), шёпот (случайная фаза — голос без связок).

Запуск: COQUI_TOS_AGREED=1 /tmp/claude-0/ttsenv/bin/python tools/voice.py [--only id1,id2] [--force]
"""
import difflib, json, os, re, subprocess, sys, tempfile
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, '..', 'src', 'main', 'resources')
OUT = os.path.join(RES, 'assets', 'glukhoman', 'sounds', 'voice')
os.makedirs(OUT, exist_ok=True)
story = json.load(open(os.path.join(RES, 'glukhoman_story.json'), encoding='utf-8'))
LINES = story['lines']

SPEAKER = {'radio': 'Damien Black', 'anna_radio': 'Ana Florence', 'choir_anna': 'Ana Florence', 'choir': 'Viktor Menelaos',
           'shumov': 'Viktor Menelaos', 'echo': 'Craig Gutsy', 'whisper': 'Ana Florence'}
SR = 24000

only = set(sys.argv[sys.argv.index('--only') + 1].split(',')) if '--only' in sys.argv else None
force = '--force' in sys.argv

from TTS.api import TTS
from faster_whisper import WhisperModel
import torch
torch.set_num_threads(int(os.environ.get('THREADS', '3')))
tts = TTS('tts_models/multilingual/multi-dataset/xtts_v2')
asr = WhisperModel('small', device='cpu', compute_type='int8')


def norm(s):
    return re.sub(r'[^а-яa-z0-9 ]+', '', s.lower().replace('ё', 'е')).strip()


def load(f, sr=SR):
    raw = subprocess.run(['ffmpeg', '-loglevel', 'error', '-i', f, '-f', 's16le', '-ac', '1', '-ar', str(sr), '-'], capture_output=True).stdout
    return np.frombuffer(raw, np.int16).astype(np.float32) / 32768


def save(x, dst):
    x = np.clip(x / max(1e-6, np.abs(x).max()) * 0.9, -1, 1)
    with tempfile.NamedTemporaryFile(suffix='.raw') as t:
        t.write((x * 32767).astype(np.int16).tobytes()); t.flush()
        subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-f', 's16le', '-ar', str(SR), '-ac', '1', '-i', t.name,
                        '-c:a', 'libvorbis', '-q:a', '4', dst], check=True)


def say(text, speaker, tmp, tag):
    best = (-1, None)
    for attempt in range(3):
        wav = os.path.join(tmp, f'{tag}{attempt}.wav')
        tts.tts_to_file(text=text, speaker=speaker, language='ru', file_path=wav, temperature=0.55, top_p=0.8, top_k=40,
                        repetition_penalty=6.0)
        segs = list(asr.transcribe(load(wav, 16000), language='ru')[0])
        sim = difflib.SequenceMatcher(None, norm(''.join(s.text for s in segs)), norm(text)).ratio()
        if sim > best[0]:
            best = (sim, wav)
        if sim >= 0.9:
            break
    clean = os.path.join(tmp, tag + 'c.wav')
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', best[1], '-af',
                    'highpass=f=70,silenceremove=start_periods=1:start_threshold=-45dB,areverse,silenceremove=start_periods=1:start_threshold=-45dB,areverse',
                    '-ar', str(SR), '-ac', '1', clean], check=True)
    return load(clean), best[0]


def shift(x, semis, tmp, tag):
    a, b = os.path.join(tmp, tag + 'i.wav'), os.path.join(tmp, tag + 'o.wav')
    import wave
    with wave.open(a, 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes((np.clip(x, -1, 1) * 32767).astype(np.int16).tobytes())
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', a, '-af', f'rubberband=pitch={2 ** (semis / 12):.4f}', b], check=True)
    return load(b)


def band(x, lo, hi):
    from scipy.signal import butter, sosfilt
    return sosfilt(butter(4, [lo, hi], btype='band', fs=SR, output='sos'), x)


def echo(x, delays=(0.09, 0.21, 0.37), g=0.35):
    out = np.concatenate([x, np.zeros(int(SR * 0.6))])
    for i, d in enumerate(delays):
        k = int(d * SR)
        out[k:k + len(x)] += x * g * (0.7 ** i)
    return out


def static(n, amount, rng):
    s = rng.standard_normal(n) * amount
    crackle = (rng.random(n) < 0.002) * rng.standard_normal(n) * amount * 6
    return band(s + crackle, 300, 4000)


def whisper(x):
    from scipy.signal import stft, istft
    f, t, Z = stft(x, SR, nperseg=512)
    ph = np.exp(1j * np.random.default_rng(3).uniform(0, 2 * np.pi, Z.shape))
    _, y = istft(np.abs(Z) * ph, SR, nperseg=512)
    return band(y, 600, 7000)


def process(lid, who, x, tmp):
    rng = np.random.default_rng(abs(hash(lid)) % 2 ** 32)
    pad = np.zeros(int(SR * 0.25))
    if who in ('radio', 'anna_radio', 'echo'):
        y = np.tanh(band(x, 350, 3200) * 3) * 0.5
        y = np.concatenate([pad, y, pad])
        amt = 0.05 if who == 'radio' else 0.09
        fade = 1 - 0.3 * (np.sin(2 * np.pi * 0.4 * np.arange(len(y)) / SR) > 0.9)  # эфир «проваливается»
        y = y * fade + static(len(y), amt, rng)
        return y
    if who == 'shumov':
        n = np.arange(len(x))
        wow = (np.sin(2 * np.pi * 0.7 * n / SR) * 0.0025 * SR).astype(int)
        idx = np.clip(n + wow, 0, len(x) - 1)
        y = band(x[idx], 180, 5500)
        y = np.concatenate([pad, y, pad])
        return y + rng.standard_normal(len(y)) * 0.012
    if who in ('choir_anna', 'choir'):
        lo = shift(x, -4, tmp, lid + 'a')
        hi = shift(x, 3, tmp, lid + 'b')
        n = max(len(x), len(lo), len(hi)) + int(SR * 0.1)
        y = np.zeros(n)
        y[:len(x)] += x * (1.0 if who == 'choir_anna' else 0.6)
        k = int(SR * 0.035)
        y[k:k + len(lo)] += lo * (0.45 if who == 'choir_anna' else 1.0)
        k = int(SR * 0.07)
        y[k:k + len(hi)] += hi * 0.3
        return echo(y, g=0.3)
    if who == 'whisper':
        return echo(whisper(x), (0.12, 0.3), 0.25)
    return x


done = 0
with tempfile.TemporaryDirectory() as tmp:
    for lid, line in LINES.items():
        if only and lid not in only:
            continue
        dst = os.path.join(OUT, lid + '.ogg')
        if os.path.exists(dst) and not force and not only:
            continue
        who = line['who']
        x, sim = say(line['text'], SPEAKER[who], tmp, lid)
        if who == 'choir':  # Хор: мужской и женский голос вместе
            x2, _ = say(line['text'], 'Ana Florence', tmp, lid + 'f')
            n = max(len(x), len(x2)); a = np.zeros(n); a[:len(x)] += x; a[:len(x2)] += x2 * 0.8; x = a
        save(process(lid, who, x, tmp), dst)
        done += 1
        print(f'{lid:12s} {who:11s} {sim:.2f}  {line["text"][:60]}', flush=True)
print('готово:', done)
