#!/usr/bin/env python3
"""Голоса серии: tools/shorts/series/<серия>_lines.json → ПАПКА/<ключ>.wav
  /home/user/tools/qwentts/venv/bin/python tools/shorts/series/voices.py ep1_lines.json ОБРАЗЕЦ_ВЛАДЕЛЬЦА.wav ПАПКА
Герой с "clone": "owner" говорит голосом владельца (он разрешил). Остальным голос придумывается по описанию (Qwen3-TTS VoiceDesign):
одна образцовая фраза → ПАПКА/refs/<герой>.wav, потом все реплики героя копируются с этого образца — голос одинаковый во всей серии.
Чужие голоса (актёров, из ElevenLabs) сюда не подставлять. Готовые файлы пропускает."""
import json, os, sys
import torch, soundfile as sf, numpy as np
from qwen_tts import Qwen3TTSModel
spec, owner, out = sys.argv[1], sys.argv[2], sys.argv[3]
S = json.load(open(spec if os.path.exists(spec) else os.path.join(os.path.dirname(os.path.abspath(__file__)), spec), encoding='utf8'))
os.makedirs(os.path.join(out, 'refs'), exist_ok=True)
load = lambda n: Qwen3TTSModel.from_pretrained(n, device_map='cpu', dtype=torch.float32, attn_implementation='sdpa')
need = [w for w, v in S['voices'].items() if 'design' in v and not os.path.exists(os.path.join(out, 'refs', w + '.wav'))]
if need:
    m = load('Qwen/Qwen3-TTS-12Hz-1.7B-VoiceDesign')
    for w in need:
        v = S['voices'][w]; wavs, sr = m.generate_voice_design(text=v['ref'], language='English', instruct=v['design'], max_new_tokens=1024)
        sf.write(os.path.join(out, 'refs', w + '.wav'), np.asarray(wavs[0], dtype=np.float32), sr); print('образец', w, round(len(wavs[0]) / sr, 2), flush=True)
    del m
m = load('Qwen/Qwen3-TTS-12Hz-0.6B-Base'); P = {}
for k, w, t in S['lines']:
    f = os.path.join(out, k + '.wav')
    if os.path.exists(f): continue
    if w not in P: P[w] = m.create_voice_clone_prompt(ref_audio=owner if S['voices'][w].get('clone') == 'owner' else os.path.join(out, 'refs', w + '.wav'), x_vector_only_mode=True)
    wavs, sr = m.generate_voice_clone(text=t, language='English', voice_clone_prompt=P[w], max_new_tokens=1024)
    sf.write(f, np.asarray(wavs[0], dtype=np.float32), sr); print(k, w, round(len(wavs[0]) / sr, 2), flush=True)
