#!/usr/bin/env python3
"""WHAT THE LAKE KEEPS — прохождения по данным игры, точно по правилам game.gd (без «любых» чисел, как в story_audit).
Каждый тест — политика выборов. Симулятор даёт: последовательность --pick для автопрогона Godot, путь по сценам,
выборы (текстом), итоговые флаги и концовку. Потом tools/lake/story_tests.py прогоняет тот же маршрут в Godot и
сравнивает путь, концовку и флаги.
  python3 tools/lake/story_sim.py list          тесты
  python3 tools/lake/story_sim.py run ИМЯ       один тест (путь, выборы, флаги)
  python3 tools/lake/story_sim.py all [файл]    все тесты → JSON (по умолчанию /tmp/lake_sim.json)"""
import json, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import story_audit as A

ENDS = ['mama', 'shadow', 'ice', 'witness']   # порядок как в game.gd::_the_end; иначе «Забытьё»


def simulate(data, chapters, rules, miss=(), missin=(), default=0):
    """rules: {сцена: правило} — правило: номер видимого варианта, 'silent', подстрока русского текста варианта
    или список таких правил (по очереди, для сцен, куда возвращаются: хаб на даче)."""
    sc = data['scenes']
    f = dict(data['flags'])
    sid = data['start']
    picks, path, said, used = [], [], [], {}
    ending = None
    guard = 0
    while sid:
        guard += 1
        if guard > 2000:
            raise RuntimeError('зациклилось у ' + sid)
        path.append(sid)
        nxt = None
        for b in sc[sid]['beats']:
            if 'if' in b and not A.cond_ok(b['if'], f):
                if b.get('else'):
                    nxt = b['else']
                    break
                continue
            if 'set' in b:
                for k, v in b['set'].items():
                    f[k] = f.get(k, 0) + int(v)
            if 'choice' in b:
                opts = [o for o in b['choice']['opts'] if 'if' not in o or A.cond_ok(o['if'], f)]
                r = rules.get(sid, default)
                if isinstance(r, list):
                    n = used.get(sid, 0)
                    used[sid] = n + 1
                    r = r[n] if n < len(r) else r[-1]
                if r == 'silent':
                    idx = [i for i, o in enumerate(opts) if o.get('silent')][0]
                elif isinstance(r, int):
                    idx = min(r, len(opts) - 1)
                else:
                    m = []
                    for alt in r.split('|'):   # «А|Б» — первый подходящий из вариантов
                        m = [i for i, o in enumerate(opts) if not o.get('silent') and alt in o.get('ru', '')]
                        if m:
                            break
                    if not m:
                        raise RuntimeError('%s: нет варианта «%s» среди %s' % (sid, r, [o.get('ru', 'молчание') for o in opts]))
                    idx = m[0]
                picks.append(idx)
                o = opts[idx]
                said.append((sid, 'молчание' if o.get('silent') else o['ru']))
                nxt = o.get('go')
                break
            if 'peek' in b:
                nxt = b['no'] if 'peek' in miss else b['yes']
                break
            if 'qte' in b:
                failed = b['qte'] in miss or sid in missin
                if failed and b['qte'] == 'brake':
                    f['lamp'] = 1   # game.gd::_deer_hit
                if failed and b.get('fail'):
                    nxt = b['fail']
                    break
            if b.get('go'):
                nxt = b['go']
                break
            if b.get('end'):
                nxt = A.chapter_next(chapters, int(b['end']))
                if not nxt:
                    ending = 'oblivion'
                    for e in ENDS:
                        if float(f.get('end_' + e, 0)) > 0:
                            ending = e
                            break
                break
        sid = nxt
    return {'picks': picks, 'path': path, 'choices': said, 'flags': {k: v for k, v in f.items() if v}, 'ending': ending}


# Общая основа «мирного» прохождения: Ной не выбирает насилие, бережёт доверие Айрис, всех берёт с собой.
PEACE = {
    's1': 'silent', 's2': 'Выключить радио', 's4': 'Принять таблетку', 's5_calm': 'Помогите сестре',
    's6': 'Поехали с нами', 's7_iris': 'Я не знаю, что со мной',
    'c2_s1': 'Отвернуться', 'c2_s2': 'Остановиться', 'c2_s2_stop': 'Садись назад', 'c2_s2_ride': 'silent',
    'c2_s3': 'Говорить буду я', 'c2_s4_wc': 'Не принимать', 'c2_s4_porch': 'Выйти к ней', 'c2_s4_z': 'У меня — минуты',
    'c2_s5': 'Уехать сейчас', 'c2_s6': 'Взять Еву',
    'c3_s2': 'Тут женщина рожает', 'c3_s3_dark': 'Пусть сходит Зоя|Никто — свечи', 'c3_s4_belief': 'Значит… мама со мной?',
    'c3_s4_pills': 'принять таблетку на ночь', 'c3_s6': 'Спрятать всех в подпол',
    'c4_s1': 'Отдать Айрис', 'c4_s2_zoe': 'Я ей верю', 'c4_s3_talk': 'Прости меня', 'c4_s4': 'Если спросят',
    'c4_s5': 'Что случилось с моей мамой?', 'c4_s6_zoe': 'Броситься на', 'c4_s6_agnes': 'Вернуться за ней',
    'c5_s1_hub': 0, 'c5_t_danny': 'Не брошу', 'c5_s1_pills2': 'Не просить', 'c5_s1_pills': 'Бросить в печь',
    'c5_s1_pills_old': 'Бросить в печь', 'c5_s2': 'Смотреть на отца', 'c5_s2_look6': 'Смотреть на отца',
    'c5_s3_iris': 'Я всё помню', 'c5_s3_shed': 'Я всё помню', 'c5_s4': 'Айрис', 'c5_s4_iris': 'Протянуть руку Хейлу',
    'c5_s4_alone': 'Протянуть руку Хейлу',
}
# «Жестокое»: каждый раз самый тёмный вариант
DARK = {
    's1': 'Мы возвращаемся', 's2': 'Дослушать', 's3_hit': 'Добить монтировкой', 's4': 'Не принимать',
    's6': 'Мы не можем его взять', 's6_b': 'Нет. Он остаётся', 's7_iris': 'Всё будет хорошо',
    'c2_s1': 'Смотреть дальше', 'c2_s2': 'Проехать мимо', 'c2_s3': 'Говорить буду я', 'c2_s4_zoe': 'Промолчать',
    'c2_s4_wc': 'Смыть всё', 'c2_s5': 'Подойти к машине', 'c2_s6': 'Взять Еву',
    'c3_s2': 'Ты — наша бабушка?', 'c3_s3_dark': 'Пойду я', 'c3_s4_belief': 'Это болезнь', 'c3_s4_brew': 'Не пить',
    'c3_s6': 'Выйти к машине', 'c4_s1': 'Сунуть пузырёк', 'c4_s3_sleep': 'Тихо посмотреть', 'c4_s5': 'Напасть',
    'c4_s6_agnes': 'Уйти', 'c5_s1_hub': 'Пора к сараю', 'c5_s1_pills': 'silent', 'c5_s2': 'Смотреть на отца',
    'c5_s2_look6': 'Смотреть на отца', 'c5_s3_iris': '[улыбнуться]',
}


def T(base, **over):
    r = dict(base)
    r.update(over)
    return r


TESTS = {
    '01_peace_witness': dict(title='Мирное прохождение, высокое доверие Айрис, доказательств достаточно → «Свидетель» с судом', rules=PEACE),
    '02_dark_shadow': dict(title='Жестокое прохождение, максимальная «Тень» → «Тень», Айрис спасена', rules=DARK, miss=['brake']),
    '02b_dark_shadow_iris_lost': dict(title='То же, Ной не успел к Айрис → «Тень», Айрис подо льдом', rules=DARK, miss=['brake'], missin=['c5_s4_shadow']),
    '03_no_pills': dict(title='Без единой таблетки', rules=T(PEACE, s4='Выбросить в окно', c2_s4_wc='Смыть всё', c3_s4_brew='Выпить',
                                                          c4_s1='Забросить в сугроб', c4_s6_zoe='Бежать с детьми')),
    '04_pills': dict(title='С таблетками везде → «Забытьё»', rules=T(PEACE, c2_s4_wc='Принять таблетку', c4_s1='Сунуть пузырёк',
                                                                   c5_s1_pills='Принять', c5_s3_iris='Ладно. Поехали')),
    '05_no_zoe': dict(title='Без Зои (проехал мимо, в кафе не позвал)', rules=T(PEACE, c2_s2='Проехать мимо', c2_s4_zoe='Промолчать',
                                                                              c3_s3_dark='Пойду я')),
    '06_no_danny': dict(title='Без Дэнни (оставлен на заправке)', rules=T(PEACE, s6='Мы не можем его взять', s6_b='Нет. Он остаётся')),
    '07_eva_dies': dict(title='Ева умирает родами', rules=PEACE, missin=['c3_s3_birth']),
    '08_eva_saved': dict(title='Ева жива (Еву не взяли у кафе — нашли в кювете)', rules=T(PEACE, c2_s6='Не брать', c3_s1_evacar='Бежать за помощью')),
    '09_agnes_dies': dict(title='Агнес остаётся в лесу', rules=T(PEACE, c4_s6_agnes='Уйти')),
    '10_agnes_saved': dict(title='Агнес спасена (на руках из леса)', rules=PEACE),
    '11_zoe_taken': dict(title='Зою похищает Хейл (ночь у кафе), освобождение на берегу', rules=T(PEACE, c2_s5='Остаться')),
    '12_danny_lost': dict(title='Дэнни уходит в буран (Ной соврал ему в кафе)', rules=T(PEACE, s4='Не принимать', c2_s4_danny='Нет',
                                                                                      c3_s4_pills='Не отдавать и не пить', c3_s5_danny='Иди. Я тебя не держу',
                                                                                      c4_s6_zoe='Бежать с детьми')),
    '13a_end_witness': dict(title='Концовка «Свидетель»', rules=PEACE),
    '13b_end_shadow': dict(title='Концовка «Тень»', rules=DARK, miss=['brake']),
    '13c_end_ice': dict(title='Концовка «Лёд» (Ной ныряет за Айрис)', rules=T(PEACE, c5_s4='Нырнуть')),
    '13d_end_mama': dict(title='Концовка «Мама» (колыбельная Агнес, «слушать»)', rules=T(PEACE, c5_s3_iris='[слушать]', c5_mama='Протянуть руку')),
    '13e_end_oblivion': dict(title='Концовка «Забытьё» (поехать с Хейлом)', rules=T(PEACE, c5_s3_iris='Ладно. Поехали')),
    '14_all_lost_hostages': dict(title='Комбинация: Ева умерла, Агнес погибла, Зоя и Дэнни у Хейла — малышка с Айрис в сарае',
                                 rules=T(PEACE, s4='Не принимать', c2_s4_danny='Нет', c2_s5='Остаться', c3_s4_pills='принять таблетку на ночь',
                                         c4_s6_agnes='Уйти', c4_s6_danny='Отпустить'), missin=['c3_s3_birth']),
    '15_sitter_zoe_no_hook': dict(title='Комбинация: Ева и Агнес погибли, Зоя сидит с малышкой — багра на льду нет',
                                  rules=T(PEACE, c4_s6_agnes='Уйти', c5_s1_go='Зоя, побудь с ней'), missin=['c3_s3_birth']),
    '16_ice_hale_saved': dict(title='Лёд: Ной вытащил Хейла, Айрис соскользнула, Ной ныряет за ней', rules=T(PEACE, c5_s4='Хейла'),
                              missin=['c5_s4_hale']),
    '17_witness_bitter': dict(title='«Свидетель» без суда: дневник у Хейла, свидетелей мало', rules=T(DARK, c5_s3_iris='Я всё помню', c5_s4='Айрис',
                                                                                                c5_s4_iris='Не двигаться', c4_s5='Отдать ему дневник',
                                                                                                c4_s5_diary_no='Отобрать силой')),
    # финальная доработка: новые развилки (кювет, пост с полицией, кто сидит с малышкой, песня Зои из кафе)
    '18_ditch_police_obey': dict(title='Кювет в начале, Ной заплатил и уехал (кассир звонит в полицию), на посту — выйти из машины',
                                 rules=T(PEACE, s5_calm='Не надо. Я заплачу', c2_s3_order='Подчиниться'), miss=['steer']),
    '19_police_flee': dict(title='Пост с полицией: «Газ в пол»', rules=T(PEACE, s5_calm='Не надо. Я заплачу', c2_s3_order='Газ в пол')),
    '20_police_frozen': dict(title='Пост с полицией: Ной замер (молчание)', rules=T(PEACE, s5_calm='Не надо. Я заплачу', c2_s3_order='silent')),
    '21_police_zoe': dict(title='Пост с полицией: говорит Зоя', rules=T(PEACE, s5_calm='Не надо. Я заплачу', c2_s3='Дать говорить Зое')),
    '22_along_shadow': dict(title='Комбинация: Ева умерла, Агнес погибла, Зои и Дэнни нет — малышка с Айрис в сарае, «Тень»',
                            rules=T(DARK, c5_s3_shed='[улыбнуться]'), miss=['brake'], missin=['c3_s3_birth']),
    '23_zoe_diner_taken': dict(title='Зою подобрали в кафе, ночью её забрал Хейл — на берегу «Пой. Что угодно»',
                               rules=T(PEACE, c2_s2='Проехать мимо', c2_s4_zoe='Поехали с нами', c2_s5='Остаться',
                                       c5_shore_orderly='Что угодно')),
    '24_sitter_danny_carry': dict(title='Еву нашли в кювете и несли на руках; Ева и Агнес погибли — с малышкой остаётся Дэнни',
                                  rules=T(PEACE, c2_s6='Не брать', c3_s1_evacar='Нести её', c4_s6_agnes='Уйти',
                                          c5_s1_go='Дэнни, ты за старшего'), missin=['c3_s3_birth']),
    # остальные исходы на льду — чтобы каждая строка таблицы ENDINGS_AUDIT.md была пройдена в Godot
    '25_ice_iris_fail': dict(title='Лёд: Ной схватил Айрис, но не вытянул — кромка ломается, он выталкивает её и уходит под лёд',
                             rules=T(PEACE, c5_s4='Айрис'), missin=['c5_s4_iris']),
    '26_both_saved': dict(title='«Свидетель»: Ной сначала вытащил Хейла, потом Айрис — спасены оба', rules=T(PEACE, c5_s4='Хейла')),
    '27_mama_still': dict(title='«Мама»: Ной не протянул руку — Хейла забрало озеро', rules=T(PEACE, c5_s3_iris='[слушать]', c5_mama='Не двигаться')),
    '28_witness_let_go': dict(title='«Свидетель»: Айрис спасена, Ной не стал спасать Хейла («Я-то знаю»)',
                              rules=T(PEACE, c5_s4_iris='Не двигаться')),
}


def main():
    data, chapters, _, _ = A.load()
    cmd = sys.argv[1] if len(sys.argv) > 1 else 'list'
    if cmd == 'list':
        for k, t in TESTS.items():
            print('%-28s %s' % (k, t['title']))
        return 0
    names = list(TESTS) if cmd == 'all' else [sys.argv[2]]
    out = {}
    for k in names:
        t = TESTS[k]
        try:
            r = simulate(data, chapters, t['rules'], t.get('miss', ()), t.get('missin', ()))
        except RuntimeError as e:
            raise SystemExit('тест %s: %s' % (k, e))
        r.update(title=t['title'], miss=t.get('miss', []), missin=t.get('missin', []))
        out[k] = r
        if cmd == 'run':
            print(t['title'])
            print('концовка:', r['ending'], '| сцен:', len(r['path']), '| выборов:', len(r['picks']))
            print('--pick', ','.join(map(str, r['picks'])))
            for sid, c in r['choices']:
                print('  %-18s %s' % (sid, c))
            print('флаги:', json.dumps(r['flags'], ensure_ascii=False))
    if cmd == 'all':
        p = sys.argv[2] if len(sys.argv) > 2 else '/tmp/lake_sim.json'
        json.dump(out, open(p, 'w'), ensure_ascii=False, indent=1)
        for k, r in out.items():
            print('%-28s %-9s сцен %3d выборов %2d' % (k, r['ending'], len(r['path']), len(r['picks'])))
    return 0


if __name__ == '__main__':
    sys.exit(main())
