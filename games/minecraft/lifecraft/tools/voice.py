#!/usr/bin/env python3
"""Озвучка всех реплик сюжета нейросетью XTTS-v2 (бесплатно, локально, лицензия Coqui CPML — только некоммерческое использование).
Каждую реплику переслушивает Whisper; если слова распознаются плохо — озвучиваем заново.
Детские голоса (главы 1–2) — тот же диктор, но выше тоном.
Запуск: COQUI_TOS_AGREED=1 <venv>/bin/python tools/voice.py [--only id1,id2] [--force]
"""
import difflib, json, os, re, subprocess, sys, tempfile
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'lifecraft', 'sounds', 'voice')
os.makedirs(OUT, exist_ok=True)

story = json.load(open(os.path.join(HERE, '..', 'src', 'main', 'resources', 'lifecraft_story.json'), encoding='utf-8'))
lines = json.load(open(os.path.join(HERE, 'voice_lines.json'), encoding='utf-8'))
lines.append({'id': 'yompix', 'who': 'narrator', 'text': 'Йомпикс Геймс представляет.'})
CHARS = story['characters']
AMBIENT = {'amb_kid': ['Gracie Wise', 'Andrew Chipper', 'Lilya Stainthorpe', 'Kumar Dahl', 'Tammy Grit'],
           'amb_adult': ['Abrahan Mack', 'Tammie Ema', 'Baldur Sanjin', 'Nova Hogarth', 'Luis Moray'],
           'amb_old': ['Brenda Stern', 'Wulf Carlevaro', 'Uta Obando', 'Filip Traverse']}

only = None
force = '--force' in sys.argv
if '--only' in sys.argv:
    only = set(sys.argv[sys.argv.index('--only') + 1].split(','))

from TTS.api import TTS
from faster_whisper import WhisperModel

tts = TTS('tts_models/multilingual/multi-dataset/xtts_v2')
asr = WhisperModel('small', device='cpu', compute_type='int8')


def norm(s):
    s = s.lower().replace('ё', 'е')
    return re.sub(r'[^а-яa-z0-9 ]+', '', s).strip()


def pcm16k(f):
    raw = subprocess.run(['ffmpeg', '-loglevel', 'error', '-i', f, '-f', 's16le', '-ac', '1', '-ar', '16000', '-'], capture_output=True).stdout
    return np.frombuffer(raw, np.int16).astype(np.float32) / 32768


def speaker_and_pitch(line):
    who, vid = line['who'], line['id']
    if 'speaker' in line:
        return line['speaker'], line.get('pitch', 0.0)
    if who in AMBIENT:
        n = int(vid.rsplit('_', 1)[1])
        sp = AMBIENT[who][n % len(AMBIENT[who])]
        return sp, 4.0 if who == 'amb_kid' else 0.0
    ch = CHARS[who]
    pitch = 0.0
    if ch.get('kid'):
        if vid.startswith(('c1', 'c2')):
            pitch = 4.0
        elif vid.startswith('c3'):
            pitch = 1.0
    return ch['voice'], pitch


done = 0
import torch
torch.set_num_threads(int(os.environ.get('THREADS', '2')))
part, parts = map(int, os.environ.get('WORKER', '0/1').split('/'))
for li, line in enumerate(lines):
    if li % parts != part:
        continue
    vid = line['id']
    dst = os.path.join(OUT, vid + '.ogg')
    if only and vid not in only:
        continue
    older = float(os.environ.get('FORCE_OLDER', '0'))
    if os.path.exists(dst) and not force and not only and os.path.getmtime(dst) >= older:
        continue
    sp, pitch = speaker_and_pitch(line)
    best = (-1, None, 0)
    with tempfile.TemporaryDirectory() as tmp:
        # минимум 3 дубля, максимум 8; берём самый чистый: точность слов + уверенность распознавания каждого слова
        for attempt in range(5):
            wav = os.path.join(tmp, f'{attempt}.wav')
            tts.tts_to_file(text=line['text'], speaker=sp, language='ru', file_path=wav,
                            temperature=0.55, top_p=0.8, top_k=40, repetition_penalty=6.0, length_penalty=1.0)
            segs = list(asr.transcribe(pcm16k(wav), language='ru', word_timestamps=True)[0])
            heard = ''.join(s_.text for s_ in segs)
            words = [w for s_ in segs for w in (s_.words or [])]
            minp = min((w.probability for w in words), default=0)
            sim = difflib.SequenceMatcher(None, norm(heard), norm(line['text'])).ratio()
            dur = len(pcm16k(wav)) / 16000
            cps = len(line['text']) / max(dur, 0.1)
            dur_ok = 7 <= cps <= 22
            score = sim * 0.7 + minp * 0.3 - (0 if dur_ok else 0.2)
            if score > best[0]:
                best = (score, wav, sim)
            if sim >= 0.94 and minp >= 0.4 and dur_ok:
                break
        score, wav, sim = best
        af = f'rubberband=pitch={2 ** (pitch / 12):.4f}:formant=preserved,' if pitch else ''
        af += 'highpass=f=70,silenceremove=start_periods=1:start_threshold=-45dB,areverse,silenceremove=start_periods=1:start_threshold=-45dB,areverse,loudnorm=I=-18:TP=-2'
        subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', wav, '-af', af, '-ac', '1', '-ar', '24000', '-c:a', 'libvorbis', '-q:a', '6', dst], check=True)
    done += 1
    print(f'{vid:12s} {sp:18s} {score:.2f} {sim:.2f}  {line["text"][:60]}', flush=True)
print('готово:', done)
