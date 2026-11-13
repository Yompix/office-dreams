#!/usr/bin/env python3
"""Дубляж роликов голосом владельца (его запись; разрешил 12 ноя): tools/shorts/dub_lines.json → ПАПКА/<язык>/<ключ>.wav.
  /home/user/tools/qwentts/venv/bin/python tools/shorts/dub.py ОБРАЗЕЦ.wav ПАПКА ru ja de …   (Qwen3-TTS Base, CPU: ~3–4 мин на реплику; готовые пропускает)
«Hush. Till six.» (b6) — название игры, во всех языках берётся английская запись."""
import json, os, sys, shutil
import torch, soundfile as sf, numpy as np
from qwen_tts import Qwen3TTSModel
ref, out, langs = sys.argv[1], sys.argv[2], sys.argv[3:]
L = json.load(open(os.environ.get('DUB_LINES') or os.path.join(os.path.dirname(os.path.abspath(__file__)), 'dub_lines.json'), encoding='utf8'))   # DUB_LINES — свой файл реплик (другой ролик)
m = Qwen3TTSModel.from_pretrained('Qwen/Qwen3-TTS-12Hz-0.6B-Base', device_map='cpu', dtype=torch.float32, attn_implementation='sdpa')
prompt = m.create_voice_clone_prompt(ref_audio=ref, x_vector_only_mode=True)
for lg in langs:
    d = os.path.join(out, lg); os.makedirs(d, exist_ok=True)
    for k, t in L[lg].items():
        f = os.path.join(d, k + '.wav')
        if k == 'lang' or os.path.exists(f): continue
        wavs, sr = m.generate_voice_clone(text=t, language=L[lg]['lang'], voice_clone_prompt=prompt, max_new_tokens=1024)
        sf.write(f, np.asarray(wavs[0], dtype=np.float32), sr); print(lg, k, round(len(wavs[0]) / sr, 2), flush=True)
    en = os.path.join(out, 'en', 'b6.wav')
    if lg != 'en' and 'b6' in L['en'] and os.path.exists(en) and not os.path.exists(os.path.join(d, 'b6.wav')): shutil.copy(en, os.path.join(d, 'b6.wav'))
