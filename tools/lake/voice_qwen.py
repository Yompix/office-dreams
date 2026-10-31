#!/usr/bin/env python3
"""Голоса WHAT THE LAKE KEEPS без ElevenLabs: Qwen3-TTS (открытые модели, лицензия Apache-2.0 — коммерческое
использование разрешено), локально на CPU.

Два способа:
  design — новый герой: голос задаётся описанием (INSTRUCT, модель VoiceDesign 1.7B); все реплики героя идут одним
           пакетом, чтобы голос не менялся от реплики к реплике;
  clone  — герой, который уже звучал в прошлых главах (Ной, Айрис, Дэнни): голос клонируется с его готовой реплики
           (модель Base 0.6B, образец — refs/<герой>_<язык>.wav + текст), каждая реплика отдельно, потом склейка
           с паузами — голос тот же, что в главе 1.
После генерации пакет режется на реплики как обычно (voice.py split, BATCH=q): слова со временем, губы, громкость.

  /home/user/tools/qwentts/venv/bin/python tools/lake/voice_qwen.py plan ru|en      → tools/lake/vo_<lang>_q.json
  /home/user/tools/qwentts/venv/bin/python tools/lake/voice_qwen.py gen ru|en N …   → /tmp/lake_vo/q_<lang>_<N>.wav
  /home/user/tools/qwentts/venv/bin/python tools/lake/voice_qwen.py refs ru|en      → tools/lake/refs/<герой>_<lang>.wav
  BATCH=q python3 tools/lake/voice.py split ru|en N /tmp/lake_vo/q_<lang>_<N>.wav    → snd/vo/<lang>/<ключ>.ogg

Ставится один раз: /home/user/tools/qwentts/setup.sh (torch CPU, qwen-tts, модели VoiceDesign 1.7B и Base 0.6B).
В пакет идут только реплики, которых ещё нет в snd/vo/<lang>/timing.json (готовые не трогаем)."""
import json, os, subprocess, sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.insert(0, os.path.join(ROOT, 'tools/lake'))
import voice as V   # noqa: E402  (реплики, ключи, тайминги — общие с ElevenLabs)

INSTRUCT = {
    'noah': 'A 19-year-old young man with a low, slightly hoarse voice. Exhausted, in shock and frightened after something '
            'terrible happened; he speaks quietly and tensely, sometimes his voice trembles. Natural cinematic acting, not theatrical.',
    'iris': 'A 15-year-old teenage girl with a clear, youthful voice. Scared and worn out but trying to sound tough and in control; '
            'quiet, tense, close to tears at times, sharp when she gives orders. Natural cinematic acting, not theatrical.',
    'radio': 'A middle-aged male radio news announcer. Calm, neutral, professional broadcast delivery at an even pace.',
    'roman': 'A 55-year-old man with a deep, gravelly smoker\'s voice. Tired, wary and gruff; speaks slowly and quietly.',
    'danny': 'An 8-year-old boy with a small, high voice. Frightened and lonely, speaks very quietly and hesitantly.',
    # глава 2
    'zoe': 'A 19-year-old young woman with a lively, slightly raspy alto voice. Cocky, sarcastic and quick-witted, joking to hide '
           'fear and exhaustion; fast, casual street talk, sometimes her bravado cracks. Natural cinematic acting, not theatrical.',
    'eva': 'A 27-year-old pregnant woman with a soft, warm, tired voice. Frightened and desperate but polite and gentle; '
           'speaks quietly and a little breathlessly. Natural cinematic acting, not theatrical.',
    'cop': 'A 45-year-old male highway police officer with a firm, flat, authoritative baritone. Polite on the surface but '
           'cold and suspicious; short, even, routine phrases. Natural cinematic acting, not theatrical.',
    'trucker': 'A 55-year-old long-haul trucker with a deep, rough, booming voice. Blunt, gruff and a little mocking; '
               'speaks loudly and lazily. Natural cinematic acting, not theatrical.',
    'hale': 'A 60-year-old man with a soft, calm, velvety voice. Unsettlingly gentle and polite, like a family doctor; '
            'speaks slowly and quietly with a faint smile in his voice. Natural cinematic acting, not theatrical.',
}
CLONE = {'noah', 'iris', 'danny'}   # звучали в главе 1 — клон с готовой реплики (refs/), а не новое описание
LANG = {'ru': 'Russian', 'en': 'English'}
MODEL = 'Qwen/Qwen3-TTS-12Hz-1.7B-VoiceDesign'
MODEL_CLONE = 'Qwen/Qwen3-TTS-12Hz-0.6B-Base'
REFS = os.path.join(ROOT, 'tools/lake/refs')


def plan(lang):
    tim = json.load(open(V.tfile(lang))) if os.path.exists(V.tfile(lang)) else {}
    by = {}
    for k, L in V.LINES.items():
        if k not in tim:
            by.setdefault(L['who'], []).append(k)
    out = [{'who': who, 'mode': 'clone' if who in CLONE else 'design', 'keys': keys,
            'prompt': '\n'.join(V.say_text(V.LINES[k][lang]) for k in keys)} for who, keys in by.items()]
    json.dump(out, open(os.path.join(ROOT, f'tools/lake/vo_{lang}_q.json'), 'w'), ensure_ascii=False, indent=1)
    for i, b in enumerate(out):
        print(i, b['who'], b['mode'], len(b['keys']), 'реплик')


REF_KEYS = {   # образец для клона: спокойные реплики главы 1 подряд (крик и шёпот дают крикливый или шепчущий клон)
    'noah': ['s5_pay_1', 's6_a2_1'], 'iris': ['s6_a_2', 's2_c_2'], 'danny': ['s5_2']}


def refs(lang):
    """Образец голоса для клона: реплики REF_KEYS из готовой озвучки, склеенные с паузой, и их текст."""
    import numpy as np
    import soundfile as sf
    os.makedirs(REFS, exist_ok=True)
    for who in sorted(CLONE):
        parts, text = [], []
        for k in REF_KEYS[who]:
            pcm = subprocess.run(['ffmpeg', '-nostdin', '-v', 'error', '-i', os.path.join(V.sdir(lang), k + '.ogg'), '-ac', '1', '-ar', '24000',
                                  '-f', 'f32le', '-'], capture_output=True, check=True).stdout
            parts += [np.frombuffer(pcm, np.float32), np.zeros(int(24000 * 0.35), np.float32)]
            text.append(V.say_text(V.LINES[k][lang]))
        out = os.path.join(REFS, f'{who}_{lang}.wav')
        wav = np.concatenate(parts[:-1])
        sf.write(out, wav, 24000)
        json.dump({'keys': REF_KEYS[who], 'text': ' '.join(text)}, open(out[:-4] + '.json', 'w'), ensure_ascii=False)
        print(who, lang, round(len(wav) / 24000, 1), 'с |', ' '.join(text)[:90])


_model = {}


def _load(name):
    import torch
    from qwen_tts import Qwen3TTSModel
    if name not in _model:
        _model.clear()   # одна модель в памяти (на CPU их две не помещаются рядом с игрой)
        torch.set_num_threads(os.cpu_count() or 4)
        _model[name] = Qwen3TTSModel.from_pretrained(name, device_map='cpu', dtype=torch.float32, attn_implementation='sdpa')
    return _model[name]


def gen(lang, idx):
    import numpy as np
    import soundfile as sf
    import torch
    B = json.load(open(os.path.join(ROOT, f'tools/lake/vo_{lang}_q.json')))[idx]
    os.makedirs('/tmp/lake_vo', exist_ok=True)
    out = f'/tmp/lake_vo/q_{lang}_{idx}.wav'
    torch.manual_seed(7)
    if B.get('mode') == 'clone':
        m = _load(MODEL_CLONE)
        ref = json.load(open(os.path.join(REFS, f"{B['who']}_{lang}.json")))
        prompt = m.create_voice_clone_prompt(ref_audio=os.path.join(REFS, f"{B['who']}_{lang}.wav"), ref_text=ref['text'])
        parts, sr = [], 24000
        for k in B['keys']:
            torch.manual_seed(7)
            wavs, sr = m.generate_voice_clone(text=V.say_text(V.LINES[k][lang]), language=LANG[lang], voice_clone_prompt=prompt,
                                              max_new_tokens=1024)
            parts += [wavs[0], np.zeros(int(sr * 0.7), dtype=np.float32)]
            print(' ', k, round(len(wavs[0]) / sr, 1), 'с', flush=True)
        wav = np.concatenate(parts)
    else:
        m = _load(MODEL)
        wavs, sr = m.generate_voice_design(text=B['prompt'], language=LANG[lang], instruct=INSTRUCT[B['who']], max_new_tokens=4096)
        wav = wavs[0]
    sf.write(out, wav, sr)
    print('ok', out, B['who'], round(len(wav) / sr, 1), 'с', flush=True)


if __name__ == '__main__':
    cmd, lang = sys.argv[1], sys.argv[2]
    if cmd == 'plan':
        plan(lang)
    elif cmd == 'refs':
        refs(lang)
    elif cmd == 'gen':   # gen ru 0 1 2 — несколько пакетов за один запуск (модель грузится один раз)
        for a in sys.argv[3:]:
            gen(lang, int(a))
    elif cmd == 'all':   # all ru,en — все пакеты обоих языков (сначала описания, потом клоны — модель меняется один раз)
        for lg in lang.split(','):
            bs = json.load(open(os.path.join(ROOT, f'tools/lake/vo_{lg}_q.json')))
            for i in sorted(range(len(bs)), key=lambda i: bs[i].get('mode') == 'clone'):
                gen(lg, i)
