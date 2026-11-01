#!/usr/bin/env python3
"""WHAT THE LAKE KEEPS — таблица присутствия и переходов для docs/BRANCH_CONTINUITY.md.
  python3 tools/lake/story_audit.py explore          (сначала: проход по всем веткам → /tmp/lake_explore.pkl)
  python3 tools/lake/story_tables.py [файл.pkl]      → godot/lake/docs/BRANCH_CONTINUITY.md (таблица в конце файла)
Кто рядом — по проходу всех веток: «да» (всегда), «нет» (никогда), «±» (зависит от прошлых выборов)."""
import os, sys, pickle, re
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import story_audit as A

DOC = os.path.join(A.LAKE, 'docs/BRANCH_CONTINUITY.md')
MARK = '<!-- ТАБЛИЦА: собирается tools/lake/story_tables.py, руками не править -->'
WHO = [('zoe', 'Зоя'), ('danny', 'Дэнни'), ('eva', 'Ева'), ('agnes', 'Агнес')]
RU = {'noah': 'Ной', 'iris': 'Айрис', 'zoe': 'Зоя', 'danny': 'Дэнни', 'eva': 'Ева', 'agnes': 'Агнес', 'hale': 'Хейл',
      'roman': 'Роман', 'cop': 'Полицейский', 'trucker': 'Дальнобойщик', 'radio': 'Радио', 'anna': 'Ной (голос Анны)',
      'mom': 'Анна', 'victor': 'Виктор'}
# что важного узнают в сцене (коротко; полностью — CHARACTER_KNOWLEDGE.md)
LEARN = {
    's2': 'Ной: отец мёртв, его ищут', 's4': 'Ной: таблетки от Д-ра С. Хейла', 's6': 'Айрис, Ной: имя Дэнни',
    's7_m': 'Ной: чёрный седан у заправки', 's8_diary': 'Ной: «Ной всё видел» (дневник)',
    'c2_s2_q': 'Ной: браслет из санатория', 'c2_s5': 'Ной: седан Хейла; имя из дневника',
    'c2_s5_walk': 'Ной: голос Хейла', 'c2_s6_road': 'Ной: есть бабушка',
    'c3_s1_evacar': 'все: как Ева попала в кювет', 'c3_s2_gran': 'Ной: Агнес — бабушка',
    'c3_s4': 'Ной: мама уходила, Агнес послала её к доктору; Агнес — что было дома (от Айрис)',
    'c3_s4_diary': 'Агнес: Хейл знал о ночи побега', 'c3_s5_lapse': 'Айрис, Агнес: «второй» говорит маминым голосом',
    'c4_s2_zoe': 'Ной, Айрис: «Тихие сосны»', 'c4_s2_hale': 'все: Зоя у Хейла', 'c4_s3_pages': 'Ной: что было дома',
    'c4_s4': 'Ной: что было на заправке', 'c4_s5_talk': 'Ной: Хейл был на берегу той ночью (память ≥ 3)',
    'c4_s6_agnes': 'Хейл: Агнес знает (если читала дневник)', 'c5_t_agnes': 'Ной: Агнес знала и молчала',
    'c5_s2': 'Ной: вся правда о той ночи', 'c5_s2_after': 'Айрис: брат вспомнил',
    'c5_s3_remember': 'Хейл: Ной помнит', 'c5_s3_listen': 'Хейл: «Здравствуй, Саймон»',
}


def esc(s):
    return str(s).replace('|', '／').replace('\n', ' ')


def main():
    pk = sys.argv[1] if len(sys.argv) > 1 else '/tmp/lake_explore.pkl'
    r = pickle.load(open(pk, 'rb'))
    data, chapters, owner, _ = A.load()
    sc = data['scenes']
    inc, _, _ = A.graph(data, chapters, owner)
    pres = r['presence_at']
    rows = []
    for sid, s in sc.items():
        ins = []
        for src, how in inc.get(sid, []):
            if how == 'конец главы':
                ins.append('%s (конец главы)' % src)
                continue
            m = re.match(r'(\D+?)#(\d+)', how)
            kind = m.group(1) if m else how
            bi = int(m.group(2)) if m else -1
            b = sc[src]['beats'][bi] if 0 <= bi < len(sc[src]['beats']) else {}
            cond = ''
            if kind == 'выбор':
                j = int(how.split('.')[-1])
                o = b['choice']['opts'][j]
                cond = '«%s»' % o.get('ru', '')[:40] + (' если %s' % A.cond_s(o['if']) if o.get('if') else '')
            elif kind == 'молчание':
                cond = 'молчание'
            elif kind == 'else':
                cond = 'иначе (не %s)' % A.cond_s(b.get('if', {}))
            elif kind.startswith('qte'):
                cond = 'провал действия'
            elif kind.startswith('peek'):
                cond = kind
            elif b.get('if'):
                cond = 'если %s' % A.cond_s(b['if'])
            ins.append('%s%s' % (src, (': ' + cond) if cond else ''))
        if sid == data['start']:
            ins.insert(0, 'начало игры')
        p = dict(pres.get(sid, {}))
        if sid == 'c3_recap' or sid.startswith('c3_s1'):   # до встречи с Агнес (модель присутствия уточнена после прохода)
            p['agnes'] = {False}
        who = []
        for k, nm in WHO:
            v = p.get(k)
            if not v:
                continue
            who.append('%s %s' % (nm, 'да' if v == {True} else ('нет' if v == {False} else '±')))
        speak = {}
        for b in s['beats']:
            if 'say' in b:
                speak[b['say']] = speak.get(b['say'], 0) + 1
        dlg = ', '.join('%s %d' % (RU.get(k, k), n) for k, n in speak.items()) or '—'
        outs = []
        for to, how in A.edges(s):
            m = re.match(r'(\D+?)#', how)
            outs.append('%s (%s)' % (to, m.group(1) if m else how))
        for b in s['beats']:
            if b.get('end'):
                nx = A.chapter_next(chapters, int(b['end']))
                outs.append(('%s (конец главы %s)' % (nx, b['end'])) if nx else 'конец игры')
        rows.append('| %s | %s | %s | %s | %s | %s |' % (sid, esc('; '.join(ins) or '—'), esc(', '.join(who) or 'Ной, Айрис'),
                                                          esc(LEARN.get(sid, '—')), esc(dlg), esc('; '.join(dict.fromkeys(outs)) or '—')))
    head = open(DOC).read().split(MARK)[0] if os.path.exists(DOC) else '# BRANCH_CONTINUITY\n\n'
    table = ['', MARK, '', '## Все сцены: условия, кто рядом, реплики, переходы', '',
             'Проход по веткам: %d состояний, концовки (число разных путей): %s. «Кто рядом»: да — всегда, нет — никогда, '
             '± — зависит от прошлых выборов. Ной и Айрис рядом всегда (кроме конца «Льда» и «Тени»: см. сцены).' %
             (r['states'], ', '.join('%s %d' % kv for kv in sorted(r['endings'].items()))), '',
             '| ID сцены | Условия появления (откуда) | Кто рядом | Что узнают | Диалог (реплик) | Куда дальше |',
             '|---|---|---|---|---|---|'] + rows
    open(DOC, 'w').write(head.rstrip() + '\n' + '\n'.join(table) + '\n')
    print('записано: %s (%d сцен)' % (DOC, len(rows)))


if __name__ == '__main__':
    main()
