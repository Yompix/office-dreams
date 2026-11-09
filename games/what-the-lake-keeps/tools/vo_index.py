#!/usr/bin/env python3
"""WHAT THE LAKE KEEPS — какой текст озвучен каким файлом (godot/data/vo_lines.json).
Игра ищет голос реплики по ТЕКСТУ (сцена + кто + текст, потом кто + текст), а не по номеру реплики в сцене:
вставили реплику — остальные не съезжают; поменяли текст — старый голос не играет (только субтитр), пока не переозвучат.
  python3 tools/vo_index.py freeze     записать текущие тексты для всех ключей из snd/vo/<lang>/timing.json
                                            (только когда данные и озвучка совпадают — например, сразу после озвучки)
  python3 tools/vo_index.py add KEY    дописать/обновить один ключ по текущему тексту (после переозвучки реплики)
  python3 tools/vo_index.py check      какие реплики сейчас без голоса (новые или изменённые) — список для переозвучки"""
import json, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import story_audit as A

OUT = os.path.join(A.LAKE, 'data/vo_lines.json')


def lines(data):
    for sid, sc in data['scenes'].items():
        for b in sc['beats']:
            if 'say' in b:
                yield sid, b


def timing(lang):
    p = os.path.join(A.LAKE, 'snd/vo/%s/timing.json' % lang)
    return json.load(open(p)) if os.path.exists(p) else {}


def main():
    data, _, _, _ = A.load()
    cmd = sys.argv[1] if len(sys.argv) > 1 else 'check'
    idx = json.load(open(OUT)) if os.path.exists(OUT) else {}
    if cmd == 'freeze':
        idx = {}
        for lang in ('ru', 'en'):
            t = timing(lang)
            for sid, b in lines(data):
                if b['_vo'] in t:
                    idx.setdefault(b['_vo'], {'scene': sid, 'who': b['say']})[lang] = b[lang]
    elif cmd == 'add':
        key = sys.argv[2]
        keys = A.vo_keys(data)
        for (sid, i), k in keys.items():
            if k == key:
                b = data['scenes'][sid]['beats'][i]
                idx[key] = {'scene': sid, 'who': b['say'], 'ru': b['ru'], 'en': b['en']}
    if cmd in ('freeze', 'add'):
        json.dump(idx, open(OUT, 'w'), ensure_ascii=False, indent=0, sort_keys=True)
        print('записано %d ключей → %s' % (len(idx), OUT))
        return 0
    by = {lang: {} for lang in ('ru', 'en')}
    for k, v in idx.items():
        for lang in ('ru', 'en'):
            if lang in v:
                by[lang].setdefault((v['scene'], v['who'], v[lang]), k)
                by[lang].setdefault(('', v['who'], v[lang]), k)
    miss = {'ru': [], 'en': []}
    for sid, b in lines(data):
        for lang in ('ru', 'en'):
            if (sid, b['say'], b[lang]) not in by[lang] and ('', b['say'], b[lang]) not in by[lang]:
                miss[lang].append((sid, b['say'], b[lang]))
    for lang in ('ru', 'en'):
        print('%s: без голоса %d реплик' % (lang, len(miss[lang])))
        for sid, who, t in miss[lang]:
            print('   %-22s %-6s %s' % (sid, who, t))
    return 0


if __name__ == '__main__':
    sys.exit(main())
