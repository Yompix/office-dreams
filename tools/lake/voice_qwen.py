#!/usr/bin/env python3
"""Голоса WHAT THE LAKE KEEPS без ElevenLabs: Qwen3-TTS VoiceDesign (открытая модель, лицензия Apache-2.0 —
коммерческое использование разрешено), локально на CPU. Голос героя задаётся описанием (INSTRUCT); чтобы голос
не менялся от реплики к реплике, все реплики героя идут одним пакетом, потом режутся по словам (voice.py split).

  /home/user/tools/qwentts/venv/bin/python tools/lake/voice_qwen.py plan ru|en      → tools/lake/vo_<lang>_q.json
  /home/user/tools/qwentts/venv/bin/python tools/lake/voice_qwen.py gen ru|en N     → /tmp/lake_vo/q_<lang>_<N>.wav
  python3 tools/lake/voice.py split …  (BATCH=q)                                    → snd/vo/<lang>/<ключ>.ogg

Ставится один раз: /home/user/tools/qwentts/setup.sh (torch CPU, qwen-tts, модель Qwen/Qwen3-TTS-12Hz-1.7B-VoiceDesign).
В пакет идут только реплики, которых ещё нет в snd/vo/<lang>/timing.json (готовые от ElevenLabs не трогаем)."""
import json, os, sys

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
}
LANG = {'ru': 'Russian', 'en': 'English'}
MODEL = 'Qwen/Qwen3-TTS-12Hz-1.7B-VoiceDesign'


def plan(lang):
    tim = json.load(open(V.tfile(lang))) if os.path.exists(V.tfile(lang)) else {}
    by = {}
    for k, L in V.LINES.items():
        if k not in tim:
            by.setdefault(L['who'], []).append(k)
    out = [{'who': who, 'keys': keys, 'prompt': '\n'.join(V.say_text(V.LINES[k][lang]) for k in keys)} for who, keys in by.items()]
    json.dump(out, open(os.path.join(ROOT, f'tools/lake/vo_{lang}_q.json'), 'w'), ensure_ascii=False, indent=1)
    for i, b in enumerate(out):
        print(i, b['who'], len(b['keys']), 'реплик')


_model = None


def gen(lang, idx):
    global _model
    import soundfile as sf
    import torch
    from qwen_tts import Qwen3TTSModel
    B = json.load(open(os.path.join(ROOT, f'tools/lake/vo_{lang}_q.json')))[idx]
    torch.manual_seed(7)
    torch.set_num_threads(os.cpu_count() or 4)
    if _model is None:
        _model = Qwen3TTSModel.from_pretrained(MODEL, device_map='cpu', dtype=torch.float32, attn_implementation='sdpa')
    wavs, sr = _model.generate_voice_design(text=B['prompt'], language=LANG[lang], instruct=INSTRUCT[B['who']],
                                            max_new_tokens=4096)
    os.makedirs('/tmp/lake_vo', exist_ok=True)
    out = f'/tmp/lake_vo/q_{lang}_{idx}.wav'
    sf.write(out, wavs[0], sr)
    print('ok', out, round(len(wavs[0]) / sr, 1), 'с', flush=True)


if __name__ == '__main__':
    cmd, lang = sys.argv[1], sys.argv[2]
    if cmd == 'plan':
        plan(lang)
    elif cmd == 'gen':   # gen ru 0 1 2 — несколько пакетов за один запуск (модель грузится один раз)
        for a in sys.argv[3:]:
            gen(lang, int(a))
    elif cmd == 'all':   # all ru,en — все пакеты обоих языков
        for lg in lang.split(','):
            for i in range(len(json.load(open(os.path.join(ROOT, f'tools/lake/vo_{lg}_q.json'))))):
                gen(lg, i)
