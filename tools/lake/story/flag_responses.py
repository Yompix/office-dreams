"""WHAT THE LAKE KEEPS — отклики на «молчаливые» решения игрока (docs/FLAG_RESPONSES_DRAFT.md, таблица «Решения игрока —
предлагаемые отклики», утверждено 8 ноя). 20 решений: 14 реплик, 5 строк эпилога (карточки «Что стало с каждым»,
c5_cards), 1 деталь в кадре (одеяло на Дэнни в главе 2 — ремаркой: в постановке пропа нет).
Владелец 8 ноя: новые реплики — только текстом, без озвучки (ключа "vo" нет; голоса в snd/vo для них нет —
game.gd показывает реплику по времени чтения).

Каждый вставленный шаг помечен "resp": "<флаг>"; повторный запуск сначала убирает старые вставки и ставит заново —
ничего не дублируется. Если генераторы глав (chN.py, chN_walk.py, gestures.py, fixes_mp.py) пересобрали данные —
запустить снова:
    python3 tools/lake/story/flag_responses.py
Условия: кроме флага решения — кто жив и рядом (как в tools/lake/story_audit.py, present()).
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))


def SAY(who, ru, en, cond):
    return {'say': who, 'ru': ru, 'en': en, 'if': cond}


def NOTE(ru, en, cond, dur=3.0):
    return {'note': {'ru': ru, 'en': en}, 'dur': dur, 'if': cond}


def CARD(ru, en, cond, dur=3.2):   # строка эпилога — как остальные карточки c5_cards
    return {'text': {'ru': ru, 'en': en}, 'dur': dur, 'note': True, 'if': cond}


# (флаг, глава, где: id сцены или ('explore', прогулка, точка), начало русского текста шага-якоря, 'after'|'before', шаги)
RULES = [
    # ---- глава 2
    ('blanket_given', 2, 'c2_s1', 'Дэнни спит сзади. Рация — в кулаке.', 'after',
     [NOTE('Он завернулся в одеяло из багажника — то самое, с заправки.',
           'He’s wrapped in the blanket from the trunk — the one from the gas station.',
           {'blanket_given': '>0', 'danny': '>0'}, 2.8)]),
    ('stepped_out', 2, 'c2_s4', 'Тёплый жёлтый свет, запах жареного', 'after',
     [SAY('iris', 'Ты бы правда сдался.', 'You really would’ve given yourself up.', {'stepped_out': '>0'})]),
    # ---- глава 3
    ('storm_radio', 3, 'c3_s2', 'Дом с синими ставнями.', 'after',
     [SAY('iris', 'По радио нас ищут. Машину надо спрятать.', 'They’re looking for us on the radio. We have to hide the car.',
          {'storm_radio': '>0', 'police': '>0'})]),
    ('iris_alone', 3, 'c3_s3_alive', 'Ева кивает.', 'after',
     [SAY('eva', 'Твоя сестра всю дорогу меня грела.', 'Your sister kept me warm the whole way.',
          {'iris_alone': '>0', 'eva_dead': '==0'})]),
    ('iris_saw', 3, 'c3_s4', 'Айрис клюёт носом на лавке', 'after',
     [SAY('iris', 'Я видела, как ты к нему подошёл. Что он сказал?', 'I saw you go to him. What did he say?', {'iris_saw': '>0'}),
      SAY('noah', 'Спросил, есть ли таблетки.', 'Asked if I still had pills.', {'iris_saw': '>0'})]),
    ('agnes_knows', 3, 'c3_s4', 'Озеро своих не отпускает, милок.', 'before',
     [SAY('agnes', 'Не помнишь — не стыдно. Стыдно — не хотеть помнить.', 'Not remembering is no shame. Not wanting to is.',
          {'agnes_knows': '>0'})]),
    # ---- глава 4
    ('pills_kept', 4, 'c4_s1', 'В кармане у Ноя — ещё и запасной пузырёк', 'after',
     [SAY('iris', 'Ты их так и не отдал.', 'You never gave them up.', {'pills_kept': '>0', 'pills_burned': '==0'})]),
    ('brew', 4, 'c4_s1_van', 'Лет восемь не заводилась.', 'before',
     [SAY('agnes', 'Отвар своё сделал. Теперь будешь помнить.', 'The brew did its work. Now you’ll remember.',
          {'brew': '>0', 'agnes_dead': '==0'})]),
    ('drawing1', 4, 'c4_s3_pages', 'Нож. Отец на полу.', 'after',
     [SAY('noah', 'Красное окно. Я его уже видел — в машине.', 'The red window. I saw it already — in the car.',
          {'drawing1': '>0', 'drawings_shown': '>0'})]),
    ('pills_left', 4, 'c4_s5', 'Ной. Ну наконец-то.', 'after',
     [SAY('hale', 'Ты оставил мой подарок в машине. Зря.', 'You left my gift in the car. A mistake.', {'pills_left': '>0'})]),
    ('zoe_trust', 4, 'c4_s6_zoe_lost', None, 'start',   # до флагов «Зою увели»: пока она ещё рядом
     [SAY('zoe', 'Ной! Ты мне верил — найди меня!', 'Noah! You believed me — find me!',
          {'zoe_trust': '>=3', 'zoe': '>0', 'zoe_gone': '==0'})]),
    ('agnes_warm', 4, 'c4_s6_agnes_lost', None, 'start',   # до agnes_dead: последние слова
     [SAY('agnes', 'Идите, внуки. Идите.', 'Go, children. Go.', {'agnes_warm': '>0', 'agnes_dead': '==0'})]),
    # ---- глава 5
    ('pine_freeze', 5, 'c5_s1', 'Она мне тут косы заплетала.', 'after',
     [SAY('iris', 'Ты тогда стоял, как будто он тебя выключил.', 'You just stood there, like he’d switched you off.',
          {'pine_freeze': '>0'})]),
    ('iris_fought', 5, 'c5_s1', 'Она мне тут косы заплетала.', 'after',
     [SAY('noah', 'Ты тогда шприц у него выбила. Я так и не сказал спасибо.',
          'You knocked that syringe out of his hand. I never said thank you.', {'iris_fought': '>0'})]),
    ('belief_none', 5, 'c5_s2_after', 'Вспомнил.', 'after',
     [SAY('iris', 'Теперь знаешь?', 'Do you know now?', {'belief_none': '>0'})]),
    ('pine_diary', 5, ('explore', 'c5h', 'tinbox'), 'Айрис закрывает глаза и беззвучно шевелит губами', 'after',
     [SAY('iris', 'Код был в дневнике. Я его помню.', 'The code was in the diary. I remember it.',
          {'pine_diary': '>0', 'diary_hale': '>0', 'c5_box': '==0'})]),
    # эпилог: карточки «Что стало с каждым»
    ('phoned', 5, 'c5_cards', 'Ной — в «Тихих соснах».', 'after',
     [CARD('В распечатке звонков той ночи — один вызов с таксофона «24». В пустой дом.',
           'The phone records for that night show one call from the “24” payphone. To an empty house.', {'phoned': '>0'})]),
    ('iris_saved', 5, 'c5_cards', 'Через три года ей восемнадцать.', 'after',
     [CARD('Айрис потом часто говорила, кто её вытащил. Ни разу не сказала — как.',
           'Iris often told people who pulled her out. Never how.', {'iris_saved': '>0', 'iris_lost': '==0'})]),
    ('danny_stayed', 5, 'c5_cards', 'Дэнни больше никогда не верит взрослым на слово.', 'after_last',
     [CARD('Дэнни не отпускал руку Айрис до самой полиции.', 'Danny didn’t let go of Iris’s hand until the police came.',
           {'danny_stayed': '>0', 'danny': '>0', 'danny_lost': '==0', 'danny_hale': '==0', 'iris_lost': '==0'})]),
    ('cafe_paid', 5, 'c5_cards', 'Кассир Роман на суде сказал', 'before',
     [CARD('Официантка Нина потом сказала: парень за всё заплатил. Даже за чай девочке.',
           'Nina the waitress said later: the boy paid for everything. Even the girl’s tea.', {'cafe_paid': '>0'})]),
]


def _ru(b):
    for k in ('note', 'text'):
        if isinstance(b.get(k), dict):
            return str(b[k].get('ru', ''))
    return str(b.get('ru', ''))


def strip(x):
    """Убрать прошлые вставки (шаги с "resp") — во всём дереве главы."""
    if isinstance(x, list):
        x[:] = [v for v in x if not (isinstance(v, dict) and 'resp' in v)]
        for v in x:
            strip(v)
    elif isinstance(x, dict):
        for v in x.values():
            strip(v)


def beats_of(j, where):
    if isinstance(where, tuple):
        _, walk, spot = where
        for s in j['explore'][walk]['spots']:
            if s['id'] == spot:
                return s['beats']
        raise SystemExit('нет точки %s/%s' % (walk, spot))
    return j['scenes'][where]['beats']


def _alts(anchor):
    """Якорь и его вид после редактуры script_edits.json (правки могут идти до или после этого скрипта)."""
    try:
        edits = json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'script_edits.json'), encoding='utf-8'))
    except OSError:
        edits = []
    return [anchor] + [e['new_ru'] for e in edits if e['old_ru'].startswith(anchor)]


def main():
    total = 0
    for ch in range(1, 6):
        rules = [r for r in RULES if r[1] == ch]
        path = os.path.join(ROOT, 'godot/lake/data/ch%d.json' % ch)
        j = json.load(open(path, encoding='utf-8'))
        strip(j)
        for flag, _, where, anchor, pos, new in rules:
            beats = beats_of(j, where)
            new = [dict(b, resp=flag) for b in new]
            if pos == 'start':
                i = 0
            else:
                alts = _alts(anchor)
                hits = [k for k, b in enumerate(beats) if 'resp' not in b and any(_ru(b).startswith(a) for a in alts)]
                if not hits:
                    raise SystemExit('ch%d %s: нет шага «%s»' % (ch, where, anchor))
                if pos == 'before':
                    i = hits[0]
                else:
                    i = (hits[-1] if pos == 'after_last' else hits[0]) + 1
                    while i < len(beats) and beats[i].get('resp') and pos != 'before':   # после уже вставленных у того же якоря
                        i += 1
            beats[i:i] = new
            total += len(new)
        s = json.dumps(j, ensure_ascii=False, indent=1) + '\n'
        open(path, 'w', encoding='utf-8').write(s)
    print('откликов вставлено: %d шагов (%d решений)' % (total, len(RULES)))


if __name__ == '__main__':
    main()
