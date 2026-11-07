#!/usr/bin/env python3
"""Звуки звонков героев (story/Calls): мелодия входящего звонка и щелчок соединения.
Запуск из папки novograd: python3 tools/gen_calls_sfx.py (нужен ffmpeg). Пишет sounds/phone_ring.ogg, phone_pickup.ogg и записи в sounds.json;
заодно регистрирует в sounds.json новые озвученные реплики (voice.<id>), если .ogg уже есть."""
import json, os, subprocess, tempfile, wave
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
SND = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'novograd', 'sounds')
SR = 44100


def marimba(f, dur):
    t = np.arange(int(SR * dur)) / SR
    env = np.exp(-t * 7) * np.minimum(1, t * 400)
    return env * (np.sin(2 * np.pi * f * t) + 0.35 * np.sin(2 * np.pi * f * 4 * t) * np.exp(-t * 20) + 0.15 * np.sin(2 * np.pi * f * 2 * t))


def save(name, x):
    x = x / max(1e-6, np.abs(x).max()) * 0.8
    with tempfile.TemporaryDirectory() as tmp:
        w = os.path.join(tmp, 'a.wav')
        with wave.open(w, 'wb') as f:
            f.setnchannels(1)
            f.setsampwidth(2)
            f.setframerate(SR)
            f.writeframes((x * 32767).astype(np.int16).tobytes())
        subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', w, '-c:a', 'libvorbis', '-q:a', '5', os.path.join(SND, name + '.ogg')], check=True)


# мелодия звонка: короткое арпеджио «маримбы», 1,6 с (клиент повторяет её, пока телефон звонит)
notes = [(659.3, 0.0), (784.0, 0.13), (987.8, 0.26), (1318.5, 0.39), (987.8, 0.65), (1318.5, 0.78)]
ring = np.zeros(int(SR * 1.6))
for f, at in notes:
    s = marimba(f, 0.6)
    i = int(at * SR)
    ring[i:i + len(s)] += s[:len(ring) - i]
save('phone_ring', ring)
# соединение: два коротких тона
t = np.arange(int(SR * 0.09)) / SR
beep = np.sin(2 * np.pi * 1400 * t) * np.minimum(1, (0.09 - t) * 200)
save('phone_pickup', np.concatenate([beep, np.zeros(int(SR * 0.05)), beep * 0.8]))

p = os.path.join(SND, '..', 'sounds.json')
j = json.load(open(p, encoding='utf-8'))
for k in ('phone_ring', 'phone_pickup'):
    j[k] = {'sounds': [{'name': 'novograd:' + k, 'stream': False}], 'subtitle': 'subtitles.novograd.' + k}
# озвученные реплики (tools/voice.py): voice.<id> для каждого готового .ogg — добавляем недостающие, остальное не трогаем
for line in json.load(open(os.path.join(HERE, 'voice_lines.json'), encoding='utf-8')):
    k = 'voice.' + line['id']
    if k not in j and os.path.exists(os.path.join(SND, 'voice', line['id'] + '.ogg')):
        j[k] = {'sounds': [{'name': 'novograd:voice/' + line['id'], 'stream': True}]}
json.dump(j, open(p, 'w', encoding='utf-8'), ensure_ascii=False, indent=2)
print('ok')
