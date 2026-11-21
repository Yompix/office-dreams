#!/usr/bin/env python3
"""0.8: взгляды и жесты во ВСЕХ кат-сценах сюжета → src/main/resources/novograd_cues.json (story/Shots добавляет их к сцене).

Для каждой сцены по репликам и длине озвучки (lipsync.json; нет — по числу букв) строится таймлайн реплик (как на клиенте:
следующая — когда голос кончился + 0.35 с) и ставятся реплики таймлайна:
  * в начале реплики слушатели-герои смотрят на говорящего (`look`), говорящий — на того, к кому обращается
    (предыдущий говоривший герой);
  * поза по смыслу реплики (`gest`): злость, грусть, смех, испуг, «не знаю», кивок, «нет» — тем же словарём, что
    StoryLife.emotion на клиенте (там она — запасной вариант для сцен без этих реплик);
  * в конце сцены взгляды снимаются.
Уже поставленные вручную реплики (novograd_shots.json → cues) не трогаются: для таких сцен ничего не добавляется.

  python3 tools/auto_cues.py
"""
import json
import os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources')
EMO = [
    ('g_scared', ('боюсь', 'страшно', 'помогите', 'не стреляй', 'не надо', 'осторожно', 'бежим', 'спасите', 'о боже')),
    ('g_laugh', ('ха-ха', 'хаха', 'ахах', 'ха!', 'смешно', 'шучу', 'весело', 'умора')),
    ('g_angry', ('убью', 'заткнись', 'чёрт', 'черт', 'проклят', 'ненавиж', 'идиот', 'сволоч', 'урод', 'тварь', 'сдохн', 'достал')),
    ('g_sad', ('жаль', 'прости', 'умер', 'погиб', 'потерял', 'грустн', 'больно', 'одна ', 'один ', 'плакал', 'похорон', 'скучаю')),
    ('g_shrug', ('не знаю', 'кто знает', 'может быть', 'без понятия', 'как знать')),
]


def emotion(text):
    t = text.lower()
    for g, words in EMO:
        if any(w in t for w in words):
            return g
        if g == 'g_angry' and t.count('!') >= 2:
            return g
    if t.startswith('да') or any(w in t for w in ('договорились', 'по рукам', 'хорошо,', 'ладно', 'согласен', 'согласна', 'конечно')):
        return 'g_nod'
    if t.startswith('нет') or any(w in t for w in ('ни за что', 'даже не думай', 'забудь')):
        return 'g_headshake'
    return None


def main():
    story = json.load(open(os.path.join(ROOT, 'novograd_story.json')))
    shots = json.load(open(os.path.join(ROOT, 'novograd_shots.json')))['scenes']
    lips = json.load(open(os.path.join(ROOT, 'assets', 'novograd', 'lipsync.json')))
    chars = set(story['characters'])
    out, n_look, n_gest = {}, 0, 0
    for ci, ch in enumerate(story['chapters']):
        for mi, m in enumerate(ch['missions']):
            for si, st in enumerate(m['steps']):
                if st.get('type') != 'cutscene':
                    continue
                key = f'{ci}:{mi}:{si}'
                if key in shots and shots[key].get('cues'):
                    continue
                actors = {a[2] for a in st.get('actors', []) if isinstance(a, list) and len(a) > 2 and a[1] == 'spawn'}
                heroes = {a for a in actors if a in chars}
                if not heroes:
                    continue
                cues, t, prev = [], 0.0, None
                for ln in st.get('lines', []):
                    who, text = ln.get('who', ''), ln.get('text', '')
                    v = lips.get(ln.get('voice', ''))
                    dur = len(v) / 20.0 if isinstance(v, str) and v else None  # огибающая: 20 значений в секунду
                    if not dur:
                        dur = max(1.2, len(text) * 0.065)
                    has_player = any(x.get('who') == 'player' for x in st.get('lines', []))
                    if who == 'player':  # игрок говорит — герои смотрят на него
                        for h in sorted(heroes):
                            cues.append([round(t + 0.15, 2), 'look', h, 'player'])
                            n_look += 1
                        prev = 'player'
                    if who in heroes:
                        for h in sorted(heroes - {who}):
                            cues.append([round(t + 0.15, 2), 'look', h, who])
                            n_look += 1
                        to = prev if prev and prev != who else ('player' if has_player else None)
                        if to:
                            cues.append([round(t + 0.1, 2), 'look', who, to])
                            n_look += 1
                        g = emotion(text)
                        if g:
                            cues.append([round(t + 0.25, 2), 'gest', who, g, round(min(4.0, max(1.5, dur * 0.8)), 1)])
                            n_gest += 1
                        prev = who
                    t += max(1.2, dur + 0.35)
                total = sum(s.get('sec', 0) for s in st.get('shots', []))
                for h in sorted(heroes):
                    cues.append([round(max(t, total) - 0.3, 2), 'look', h, 'none'])
                if cues:
                    out[key] = {'cues': cues}
    with open(os.path.join(ROOT, 'novograd_cues.json'), 'w') as f:
        json.dump({'scenes': out}, f, ensure_ascii=False, indent=0)
    print(f'сцен с новыми репликами: {len(out)}, взглядов {n_look}, жестов {n_gest} (вручную поставлено: {sum(1 for v in shots.values() if v.get("cues"))})')


if __name__ == '__main__':
    main()
