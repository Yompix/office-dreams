#!/usr/bin/env python3
"""WHAT THE LAKE KEEPS — весь сценарий из данных игры (godot/lake/data/ch1..ch5.json) читаемым текстом для редактора:
сцена за сценой, ремарки курсивом, реплики «ИМЯ: текст», выборы списком с переходами, условия словами.
Новое (прогулки, разговоры в дороге) помечено «НОВОЕ», места для будущих дополнений — «ПЛАН».
  python3 tools/lake/scenario_md.py ПАПКА        → ПАПКА/ch1.md … ch5.md (markdown для документа редактора)"""
import json, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import story_audit as A

# флаги — словами (условия «если …»); остальные — как в данных
FLAG = {
    'storm_slip': 'машину занесло задом в сугроб', 'storm_tool': 'Ной взял монтировку', 'frost': 'Ной отморозил пальцы',
    'storm_radio': 'поймали сводку', 'porch': 'Ной вышел к Зое на крыльцо', 'coffee_zoe': 'Ной принёс Зое кофе',
    'cafe_tv': 'Ной выключил телевизор', 'cafe_paid': 'Ной заплатил в кафе',
    'trust': 'доверие Айрис', 'shadow': '«Тень»', 'memory': 'память', 'pills': 'таблетка (гл. 1)', 'late': 'задержались с оленем',
    'lamp': 'разбита фара', 'deer': 'олень', 'lapse1': 'был провал на заправке', 'danny': 'Дэнни с ними', 'danny_left': 'Дэнни остался на заправке',
    'roman_ally': 'Роман помог', 'roman_hurt': 'Роман ранен', 'roman_dead': 'Роман погиб', 'police': 'полиция знает машину',
    'police_late': 'полиция узнает позже', 'threw': 'пузырёк выброшен', 'asked': 'Ной спрашивал, что было дома', 'drawing1': 'видел рисунок дома',
    'mirror': 'смотрел в зеркало', 'diary': 'Айрис показала дневник', 'ditch': 'кювет в начале', 'washed': 'отмыл руки', 'danny_name': 'спросил имя Дэнни',
    'danny_hands': 'Дэнни заметил кровь', 'danny_bye': 'Дэнни спросил «Вы уедете?»', 'choco': 'есть шоколадка', 'choco_given': 'отдал шоколадку',
    'blanket': 'взял одеяло', 'blanket_given': 'отдал одеяло', 'phoned': 'звонил домой',
    'zoe': 'Зоя с ними', 'zoe_trust': 'доверие Зои', 'eva': 'Ева с ними', 'eva_dead': 'Ева погибла', 'eva_found': 'Еву нашли в кювете',
    'agnes_dead': 'Агнес погибла', 'baby': 'малышка жива', 'danny_lost': 'Дэнни ушёл', 'danny_hale': 'Дэнни у Хейла', 'zoe_taken': 'Зою забрал Хейл',
    'romance': 'близость с Зоей', 'proof': 'улики', 'hard': 'жёсткость',
    # смысл флагов — по godot/lake/docs/FLAG_AUDIT.md
    'agnes_knows_hale': 'Агнес прочла дневник', 'agnes_limp': 'Агнес хромает', 'agnes_sings': 'Агнес поёт на берегу',
    'baby_along': 'малышка у Айрис на руках', 'belief_doctor': 'Ной решил: это болезнь', 'belief_healer': 'Ной поверил: мама с ним',
    'birth_time': 'Ной сразу сказал про роды', 'bitter': 'доказательств мало', 'cop_saw': 'полицейский видел Ноя',
    'danny_against': 'Ной просил Дэнни молчать', 'danny_fear': 'Дэнни боится Ноя', 'danny_for': 'Дэнни за Ноя',
    'danny_here': 'Дэнни на даче', 'danny_rope': 'у Дэнни верёвка', 'danny_truth': 'Ной велел Дэнни говорить правду',
    'diary_burned': 'Хейл сжёг страницы дневника', 'diary_hale': 'дневник у Хейла', 'drawings_peeked': 'Ной тайком смотрел рисунки',
    'end_ice': 'маршрут «Лёд»', 'end_mama': 'маршрут «Мама»', 'end_oblivion': 'маршрут «Забытьё»', 'end_shadow': 'маршрут «Тень»',
    'end_witness': 'маршрут «Свидетель»', 'ev_agnes': 'доказательство: Агнес', 'ev_danny': 'доказательство: Дэнни',
    'ev_diary': 'доказательство: дневник', 'ev_orderly': 'доказательство: санитар', 'ev_zoe': 'доказательство: Зоя',
    'eva_alive': 'Ева жива', 'eva_alone': 'Еву оставили у кафе', 'eva_cold': 'Ева замёрзла в пути', 'exposed': 'правда о Хейле доказана',
    'eyes_shut': 'мальчик зажмурился', 'fled': 'сбежали с поста', 'hale_brave': 'Ной вышел к Хейлу с топором',
    'hale_drowned': 'Хейл утонул', 'hale_killed': '«второй он» убил Хейла', 'hale_knows_mem': 'Ной вспомнил седан на берегу',
    'hale_let_go': 'Ной не стал спасать Хейла', 'hale_lost': 'ушли к соседке', 'hale_saved': 'Ной спас Хейла',
    'heard_hale': 'Ной слышал Хейла у кафе', 'heard_song': 'колыбельная по рации', 'help': 'на льду помогают',
    'hostage': 'у Хейла заложник', 'hub_seen': 'подсказка у печи уже была', 'hurt4': 'у Ноя разбиты руки (гл. 4)',
    'iris_lost': 'Айрис ушла под лёд', 'jealous': 'Айрис ревнует к Зое', 'lapse_shop': 'провал у магазина', 'light': 'есть свет',
    'look_dad': 'смотрел на отца', 'look_mom': 'смотрел на маму', 'night_lapse': 'ночной провал с топором',
    'noah_drowned': 'Ной утонул', 'note_pocket': 'у Ноя записка Айрис', 'orderly_dead': 'санитар погиб',
    'pills2': 'таблетка (гл. 2)', 'pills3': 'таблетка (гл. 3)', 'pills5': 'таблетка (гл. 5)', 'pills_burned': 'таблетки сожжены',
    'pills_flushed': 'таблетки смыты', 'pills_iris': 'пузырёк у Айрис', 'pills_old': 'старый пузырёк у Ноя',
    'pills_pocket': 'новый пузырёк у Ноя', 'promise': 'Ной обещал Дэнни «никого не трону»', 'promise5': 'Ной обещал Дэнни «не брошу»',
    'ptotal': 'таблеток выпито', 'remembered': 'Ной вспомнил ту ночь', 'silent_ice': 'Ной молчал на льду',
    'sitter_danny': 'малышка с Дэнни', 'sitter_zoe': 'малышка с Зоей', 't_agnes': 'говорил с Агнес', 't_danny': 'говорил с Дэнни',
    't_eva': 'говорил с Евой', 't_iris': 'говорил с Айрис', 't_zoe': 'говорил с Зоей', 'van_late': 'буханка опоздала',
    'weak': 'Ной ослаб от таблеток', 'zoe_alive5': 'Зою отогрели', 'zoe_believed': 'Ной поверил Зое', 'zoe_died': 'Зоя погибла',
    'zoe_doubted': 'Ной усомнился в Зое', 'zoe_front': 'Зоя села вперёд', 'zoe_gone': 'Зои больше нет с ними', 'zoe_here': 'Зоя на даче',
    'zoe_hook': 'у Зои багор', 'zoe_passed': 'проехали мимо Зои', 'zoe_safe': 'Зою выпустили из седана',
    'zoe_unasked': 'Ной не спросил про браслет', 'zoe_wary': 'Ной спросил про браслет',
}
DEER = {1: 'успел затормозить', 2: 'добил оленя', 3: 'уехал', 4: 'сидел с оленем', 5: 'замер'}

# где встанут дополнения из плана (вкладка «Дополнения», раздел «Главы 2–5»): перед этой сценой
PLAN = {
    'c3_s3_noah': 'ПЛАН: путь за бензином к Петру — прогулкой по деревне в буране (забор на ощупь, тёмный магазин, сарай с канистрами); провал и ветки — как сейчас. Около 6 мин.',
    'c3_s4': 'ПЛАН: прогулка по дому Агнес — фотографии молодой Анны, её комната, приёмник на полке. Около 8 мин.',
    'c4_s1_van': 'ПЛАН: буханка — до «толкают всем миром» собрать в сарае свечи, аккумулятор с седана, бензин. Около 8 мин.',
    'c4_s3': 'ПЛАН: прогулка у остановки, пока буханка остывает — расписание, следы, тот же плакат «Тихих сосен». Около 4 мин.',
    'c4_s6': 'ПЛАН: изба — игрок сам прячется от луча фонарика и укачивает малышку; исходы — «тишина» или «плач», как сейчас. Около 6 мин.',
    'c4_s7': 'ПЛАН: переход по льду на рассвете — тёмный лёд трещит, дорогу выбирает игрок (готовит тёмный лёд финала). Около 6 мин.',
    'c5_s1': 'ПЛАН: прогулка по даче до разговоров у печи — мамины вещи, ключ под третьей ступенькой, сарай издалека. Около 10 мин.',
}
PLAN_ROAD = 'ПЛАН: разговор в дороге (две темы из нескольких, как в главе 1) — где именно, подскажет редактор.'
ROAD_AFTER = {'c2_s2_ride', 'c4_s2'}
ROAD_BEFORE = set()   # глава 2: разговор в дороге — в сцене «Буран» (c2_storm_wait)


def flag_txt(k, e):
    e = str(e).strip()
    op = next((o for o in A.OPS if e.startswith(o)), '==')
    n = e[len(op):] if e.startswith(op) else e
    name = FLAG.get(k, k)
    if k == 'deer' and op == '==' and n.isdigit():
        return 'олень: ' + DEER.get(int(n), n)
    if k in A.COUNTERS:   # счётчики — всегда с числом: «доверие Айрис > 0», а не «доверие Айрис»
        if (op == '>=' and n == '1'):
            op, n = '>', '0'
        if (op == '<' and n == '1'):
            op, n = '<=', '0'
    elif (op == '>' and n == '0') or (op == '>=' and n == '1'):
        return name
    elif (op == '==' and n == '0') or (op == '<' and n == '1'):
        return 'НЕ ' + name
    return '%s %s %s' % (name, {'>=': '≥', '<=': '≤', '!=': '≠', '==': '='}.get(op, op), n)


def cond(c):
    c = dict(c)
    for who, gone, word in (('zoe', 'zoe_gone', 'Зоя всё ещё с ними'), ('danny', 'danny_lost', 'Дэнни всё ещё с ними')):
        if flag_txt(who, c.get(who, '')) == FLAG[who] and flag_txt(gone, c.get(gone, '')) == 'НЕ ' + FLAG[gone]:
            c = {(word if k == who else k): v for k, v in c.items() if k != gone}   # «был с ними» и «не ушёл» = «всё ещё с ними»
    return '[если ' + ', '.join(k if k[0] >= 'А' else flag_txt(k, v) for k, v in c.items()) + ']'


def sets(s):
    out = []
    for k, v in s.items():
        if k in ('trust', 'shadow', 'memory', 'zoe_trust', 'proof', 'romance'):
            out.append('%s %+d' % (FLAG.get(k, k), int(v)))
    return ('_(' + ', '.join(out) + ')_') if out else ''


def name(data, b):
    n = b.get('name') or data['names'].get(b['say'], b['say'])
    return A.txt(n) if isinstance(n, dict) else str(n)


def beats(data, bs, nested=False):
    """Шаги сцены → блоки markdown (абзац или список). nested — строки темы разговора: одной строкой каждая."""
    out = []
    for b in bs:
        pre = (cond(b['if']) + ' ') if 'if' in b else ''
        tail = ' (иначе → `%s`)' % b['else'] if b.get('else') else ''
        line = ''
        if 'say' in b:
            line = '**%s:** %s' % (name(data, b), b.get('ru', ''))
        elif isinstance(b.get('note'), dict):
            line = '*%s*' % b['note']['ru']
        elif isinstance(b.get('text'), dict):
            line = '**[титр]** %s' % b['text']['ru']
        if 'toast' in b:
            line = (line + ' ' if line else '') + '«Айрис это запомнит»'
        st = sets(b['set']) if 'set' in b else ''
        if st:
            line = (line + ' ' if line else '') + st
        if b.get('go'):
            line = (line + ' ' if line else '') + '→ `%s`' % b['go']
        if not line and tail and 'if' in b and not any(k in b for k in ('choice', 'ask', 'explore', 'peek', 'qte', 'end')):
            line = 'дальше по сцене'   # развилка: условие выполнено — сцена идёт дальше, иначе переход
        if line or tail:
            out.append(pre + line + tail)
        if 'explore' in b:
            d = data.get('explore', {}).get(b['explore'], {})
            pts = [A.txt(s['label']) + ((' ' + cond(s['if'])) if 'if' in s else '') for s in d.get('spots', [])]
            out.append('**НОВОЕ — прогулка: игрок сам ведёт Ноя.** Точки: %s. Выход: «%s». Полный текст — во вкладке «Дополнения».'
                       % ('; '.join(pts), A.txt(d.get('exit', {}).get('label', {}))))
        if 'choice' in b:
            c = b['choice']
            items = []
            k = 0
            for o in c['opts']:
                oc = (' ' + cond(o['if'])) if 'if' in o else ''
                if o.get('silent'):
                    items.append('- **—** промолчать%s → `%s`' % (oc, o.get('go')))
                else:
                    k += 1
                    items.append('- **%d** %s%s → `%s`' % (k, o['ru'], oc, o.get('go')))
            out.append('**Выбор (%g с):**' % float(c.get('t', 6)))
            out.append('\n'.join(items))
        if 'ask' in b:
            q = b['ask']
            mx = (', успевают %s' % q['max']) if 'max' in q else ''
            out.append('**НОВОЕ — разговор, темы на выбор без таймера%s:**' % mx)
            items = []
            for o in q['opts']:
                oc = (' ' + cond(o['if'])) if 'if' in o else ''
                head = '**%s**%s%s' % (o['ru'], ' (закончить)' if o.get('leave') else '', oc)
                sub = beats(data, o.get('beats', []), True)
                items.append('- ' + '  \n'.join([head] + sub))
            out.append('\n'.join(items))
            out.extend(beats(data, q.get('end', []), nested))
        if 'peek' in b:
            out.append('**Взгляд в зеркало (%g с):** посмотрел → `%s`, нет → `%s`' % (float(b.get('t', 5)), b['yes'], b['no']))
        if 'qte' in b:
            out.append('**Действие:** %s%s' % (A.txt(b.get('hint')) or b['qte'], (' (не успел → `%s`)' % b['fail']) if b.get('fail') else ''))
        if b.get('end'):
            out.append('**Конец главы %s.**' % (1 if b['end'] is True else b['end']))
    return out


def chapter(data, owner, n):
    out = []
    for sid, sc in data['scenes'].items():
        if owner[sid] != n:
            continue
        if sid in PLAN:
            out.append('> ' + PLAN[sid])
            out.append('')
        if sid in ROAD_BEFORE:
            out.append('> ' + PLAN_ROAD)
            out.append('')
        tags = [t for t in (('сохранение' if sc.get('save') else ''), sc.get('time', '')) if t]
        out.append('### `%s`%s' % (sid, (' · ' + ', '.join(tags)) if tags else ''))
        out.append('')
        body = beats(data, sc['beats'])
        for blk in (body if body else ['_(без слов — постановка)_']):
            out.append(blk)
            out.append('')
        if sid in ROAD_AFTER:
            out.append('> ' + PLAN_ROAD)
            out.append('')
    return '\n'.join(out)


def walk_md(data, ex_id):
    """Прогулка целиком: вступление, точки (подпись, условие, шаги, разговор), зоны, «поторопить», выход."""
    d = data.get('explore', {}).get(ex_id, {})
    out = []
    intro = beats(data, d.get('setup', []) + d.get('intro', []))
    if intro:
        out.append('**В начале:**')
        out.append('\n'.join('- ' + x for x in intro))
    for s in d.get('spots', []) + ([dict(d['exit'], exit=True)] if d.get('exit') else []):
        head = '#### %s%s%s' % ('Выход: ' if s.get('exit') else '', A.txt(s['label']), (' ' + cond(s['if'])) if 'if' in s else '')
        out.append(head)
        body = beats(data, s.get('beats', []))
        if s.get('ask'):
            body += beats(data, [{'ask': s['ask']}])
        body += beats(data, s.get('after', []))
        out.append('\n\n'.join(x for x in body if x) if body else '_(без слов — постановка)_')
    for z in d.get('zones', []):
        out.append('**Если отойти далеко%s:** %s' % ((' ' + cond(z['if'])) if 'if' in z else '', ' '.join(beats(data, z.get('beats', []), True))))
    if d.get('nudge'):
        out.append('**Если тянуть время (%d с):** %s' % (int(d['nudge'].get('after', 180)), ' '.join(beats(data, d['nudge'].get('beats', []), True))))
    return '\n\n'.join(out).replace('**НОВОЕ — разговор, темы на выбор без таймера', '**Разговор, темы на выбор без таймера')


def main():
    if len(sys.argv) > 2 and sys.argv[1] == '--walk':   # одна прогулка целиком: scenario_md.py --walk c3p
        data, _, _, _ = A.load()
        print(walk_md(data, sys.argv[2]))
        return
    dst = sys.argv[1] if len(sys.argv) > 1 else '.'
    os.makedirs(dst, exist_ok=True)
    data, chapters, owner, _ = A.load()
    for n in range(1, 6):
        md = chapter(data, owner, n)
        open(os.path.join(dst, 'ch%d.md' % n), 'w', encoding='utf-8').write(md)
        print('гл.%d: %d сцен, %d знаков' % (n, sum(1 for s in owner.values() if s == n), len(md)))


if __name__ == '__main__':
    main()
