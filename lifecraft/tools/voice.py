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
for line in lines:
    vid = line['id']
    dst = os.path.join(OUT, vid + '.ogg')
    if only and vid not in only:
        continue
    if os.path.exists(dst) and not force and not only:
        continue
    sp, pitch = speaker_and_pitch(line)
    best = (-1, None)
    with tempfile.TemporaryDirectory() as tmp:
        for attempt in range(4):
            wav = os.path.join(tmp, f'{attempt}.wav')
            tts.tts_to_file(text=line['text'], speaker=sp, language='ru', file_path=wav)
            heard = ''.join(s.text for s in asr.transcribe(pcm16k(wav), language='ru')[0])
            score = difflib.SequenceMatcher(None, norm(heard), norm(line['text'])).ratio()
            if score > best[0]:
                best = (score, wav)
            if score >= 0.88:
                break
        score, wav = best
        k = 2 ** (pitch / 12)
        af = f'asetrate=24000*{k:.4f},aresample=24000,atempo={1 / k:.4f},' if pitch else ''
        af += 'silenceremove=start_periods=1:start_threshold=-45dB,loudnorm=I=-18:TP=-2'
        subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', wav, '-af', af, '-ac', '1', '-ar', '24000', '-c:a', 'libvorbis', '-q:a', '5', dst], check=True)
    done += 1
    print(f'{vid:12s} {sp:18s} {score:.2f}  {line["text"][:60]}', flush=True)
print('готово:', done)
