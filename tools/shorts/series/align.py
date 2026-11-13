#!/usr/bin/env python3
"""Слова реплик со временем (для губ в 3D-сериале): faster-whisper base.en, локально.
  python3 tools/shorts/series/align.py ПАПКА_С_WAV ПАПКА/words.json
→ {"leo1": [[0.12, "The"], [0.31, "card"], …], …} — время начала каждого слова в секундах."""
import json, os, subprocess, sys
import numpy as np
from faster_whisper import WhisperModel
src, out = sys.argv[1], sys.argv[2]
m = WhisperModel('base.en', device='cpu', compute_type='int8')
res = {}
for f in sorted(os.listdir(src)):
    if not f.endswith('.wav'): continue
    pcm = subprocess.run(['ffmpeg', '-loglevel', 'error', '-i', os.path.join(src, f), '-ac', '1', '-ar', '16000', '-f', 'f32le', '-'], capture_output=True, check=True).stdout   # звук сами: у faster-whisper и av здесь разные версии
    segs, _ = m.transcribe(np.frombuffer(pcm, np.float32), language='en', word_timestamps=True, beam_size=5)
    res[f[:-4]] = [[round(w.start, 3), w.word.strip()] for s in segs for w in s.words]
    print(f[:-4], ' '.join(w for _, w in res[f[:-4]]))
json.dump(res, open(out, 'w'), ensure_ascii=False)
