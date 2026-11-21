#!/usr/bin/env python3
"""0.8: уникальные сюжетные герои.

1. Восстанавливает описания 56 скинов жителей (16 базовых + 40 случайных c00–c39) так же, как их рисует gen_assets.py
   (тот же порядок и сид), и дописывает в citizen_looks.json одежду (top/shirt/pants) — для проверки похожести.
2. Рисует 16 своих скинов героев сюжета (story_<id>.png): лицо и причёска как у героя раньше, своя одежда и
   «подпись» силуэта (берет, шарф, цепь, плащ, очки-гогглы, серьги, федора…) — этого у прохожих не бывает.
3. Случайные скины, которые совпадают с героем (бывшие скины героев и любые «двойники» по look_distance), рисуются
   заново — пока не станут непохожими. Индексы c00–c39 сохраняются (старые миры не ломаются).

Расстояние похожести — то же, что в Java (life/Looks.java); проверка на 5000 сгенерированных жителях — `./gradlew lookalikeTest`.

  python3 tools/gen_story_looks.py
"""
import io
import json
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_assets as G  # noqa: E402

LOOKS = os.path.join(G.ROOT, 'citizen_looks.json')
STORY_IDS = ['gleb', 'nika', 'shtorm', 'kirpich', 'petrov', 'tema', 'rosa', 'vince', 'marco', 'harris', 'lena', 'crow', 'sofi', 'ray',
             'shakal', 'diaz']
# «подписи» силуэта — части, которых нет у прохожих (HairFeature рисует их только по этим ключам)
SIGNATURE = ('beret', 'scarf', 'chain', 'coat', 'goggles', 'earrings', 'fedora', 'bandana', 'cap_back')


def recover():
    """Описания скинов в том порядке, как их рисует gen_assets.entities() (запись файлов перехвачена)."""
    seen = []
    real_skin, real_save, real_open = G.skin, G.save, getattr(G, 'open', open)

    def rec_skin(v):
        seen.append(dict(v))
        return real_skin(v)

    class Sink(io.StringIO):
        def __exit__(self, *a):
            return False

    def fake_open(path, mode='r', *a, **k):
        if 'w' in mode or 'a' in mode:
            return io.BytesIO() if 'b' in mode else Sink()
        return real_open(path, mode, *a, **k)

    G.skin, G.save, G.open = rec_skin, (lambda *a, **k: None), fake_open
    real_wave_open = G.wave.open
    G.wave.open = lambda *a, **k: _NullWave()
    real_sub = G.subprocess.run
    G.subprocess.run = lambda *a, **k: None
    try:
        G.entities()
    finally:
        G.skin, G.save, G.open = real_skin, real_save, real_open
        G.wave.open = real_wave_open
        G.subprocess.run = real_sub
    # базовые 16 + 40 случайных — последние 56 нарисованных людей до hair.png (в entities() бывают и другие skin()-вызовы)
    return seen[-56:]


class _NullWave:
    def __getattr__(self, k):
        return lambda *a, **kw: None

    def __enter__(self):
        return self

    def __exit__(self, *a):
        return False


def hexd(a, b):
    if a is None or b is None:
        return 255.0 if (a is None) != (b is None) else 0.0
    ca, cb = G.rgb(a), G.rgb(b)
    return math.sqrt(sum((ca[i] - cb[i]) ** 2 for i in range(3)))


def headwear(l):
    for k in ('helmet', 'cap', 'beanie', 'fedora', 'bandana', 'beret', 'cap_back'):
        if k in l:
            return k, l[k]
    return None, None


def facial(l):
    return 'beard' if 'beard' in l else 'mustache' if 'mustache' in l else 'goatee' if 'goatee' in l else ''


def look_distance(a, b):
    """Расстояние «на глаз» между двумя образами (без «подписей» героя). < 2.5 — двойник. Та же формула — в Looks.java."""
    d = 0.0
    if a.get('style') != b.get('style'):
        d += 1.5
    d += min(1.0, hexd(a.get('hair'), b.get('hair')) / 120)
    d += 0.5 * min(1.0, hexd(a.get('skin'), b.get('skin')) / 80)
    ha, ca = headwear(a)
    hb, cb = headwear(b)
    if ha != hb:
        d += 1.0
    elif ha:
        d += 0.5 * min(1.0, hexd(ca, cb) / 150)
    if a.get('glasses') != b.get('glasses'):
        d += 0.5
    if facial(a) != facial(b):
        d += 0.5
    if a.get('top') != b.get('top'):
        d += 0.5
    d += min(1.0, hexd(a.get('shirt'), b.get('shirt')) / 150)
    d += 0.5 * min(1.0, hexd(a.get('pants'), b.get('pants')) / 150)
    if bool(a.get('girl')) != bool(b.get('girl')):
        d += 2.0
    return d


def same_combo(a, b):
    """Та же причёска того же цвета (это узнаётся первым)."""
    return a.get('style') == b.get('style') and hexd(a.get('hair'), b.get('hair')) < 40


def base_of(l):
    return {k: v for k, v in l.items() if k not in SIGNATURE}


def look_full(v):
    out = G.look(v)
    for k in ('top', 'shirt', 'pants'):
        if k in v:
            out[k] = v[k]
    if v.get('dress') and 'top' in out:
        out['top'] = 'dress'
    for k in SIGNATURE:
        if v.get(k):
            out[k] = v[k]
    if v.get('cap_back'):
        out.pop('cap', None)
    return out


# ------------------------------------------------------------------ герои: лицо и причёска как раньше, своя одежда и подпись
def story_designs():
    return {
        # гонщик: чёрная гоночная куртка с красным, кепка козырьком назад, цепь
        'gleb': dict(skin='c99067', hair='4e342e', style='slick', top='jacket', shirt='212121', inner='b71c1c', pants='1c1c1c', shoes='e53935',
                     cap_back='b71c1c', chain='d4af37', scar=True),
        # Ники «Нитро»: белый хвост, гоночные очки на лбу, оранжевый комбинезон
        'nika': dict(skin='8d5a3b', hair='e0e0e0', style='ponytail', top='jacket', shirt='ff6d00', inner='212121', pants='ff6d00', shoes='212121',
                     slim=True, girl=True, blush=True, goggles='ff6d00'),
        # Сторм: ирокез, тёмные очки, кожаная куртка
        'shtorm': dict(skin='e2b48e', hair='6b3d1f', style='mohawk', top='jacket', shirt='3e2723', inner='9e9e9e', pants='0d47a1', shoes='111111',
                       glasses='shades', chain='c0c0c0'),
        # Брик: бандана, борода, майка-рабочка, крупнее всех
        'kirpich': dict(skin='d8a982', hair='bf360c', style='buzz', top='tshirt', shirt='5d4037', pants='33691e', shoes='4e342e', beard='bf360c',
                        bandana='2e7d32'),
        # Лейтенант Питерсон: штатский — бежевый плащ, федора, усы
        'petrov': dict(skin='d8a982', hair='9e9e9e', style='slick', top='suit', shirt='8d6e63', inner='eceff1', pants='4e342e', shoes='3e2723',
                       mustache='9e9e9e', fedora='6d4c41', band='212121', coat='a1887f', tie='1565c0', badge=True),
        # Томми: синие волосы, вязаная шапка и длинный шарф, худи
        'tema': dict(skin='efc6a4', hair='1e88e5', style='slick', top='hoodie', shirt='7cb342', pants='37474f', shoes='fafafa', beard='1e88e5',
                     beanie='ffb300', scarf='ffb300'),
        # Роза: афро, большие серьги-кольца, красное платье
        'rosa': dict(skin='c99067', hair='3e2723', style='afro', top='tshirt', shirt='c62828', pants='c62828', shoes='212121', slim=True, girl=True,
                     blush=True, dress=True, glasses='frame', earrings='d4af37'),
        # Винс: зачёс, золотые очки и цепь, белый костюм
        'vince': dict(skin='6b4429', hair='f5f5f5', style='slick', top='suit', shirt='f5f5f5', inner='212121', pants='f5f5f5', shoes='212121',
                      glasses='gold', chain='d4af37', goatee='f5f5f5'),
        # Марко «Синий»: синяя бандана банды, борода, клетчатая рубашка
        'marco': dict(skin='efc6a4', hair='6b3d1f', style='curly', top='hoodie', shirt='1565c0', inner='e3f2fd', pants='263238', shoes='1565c0',
                      beard='6b3d1f', bandana='0d47a1'),
        # Шеф Харрис: белая рубашка начальника, фуражка, седые усы, тёмно-синее пальто
        'harris': dict(skin='e0b08a', hair='bdbdbd', style='short', top='jacket', shirt='eceff1', inner='0d1b3e', pants='0d1b3e', shoes='111111',
                       mustache='bdbdbd', cap='0d1b3e', badge=True, tie='0d1b3e', coat='1a2540'),
        # Лена Вуд: фиолетовые волосы, берет журналистки, очки, жёлтый плащ
        'lena': dict(skin='6b4429', hair='8e24aa', style='long', top='jacket', shirt='fbc02d', inner='fff8e1', pants='37474f', shoes='5d4037',
                     slim=True, girl=True, blush=True, glasses='frame', beret='4a148c', coat='fbc02d'),
        # Виктор Кроу: лысый, чёрный длинный плащ, белый шарф, федора с красной лентой
        'crow': dict(skin='8d5a3b', hair='a0662e', style='bald', top='suit', shirt='111111', inner='b71c1c', pants='111111', shoes='050505',
                     glasses='frame', fedora='111111', band='b71c1c', coat='1a1a1a', scarf='f5f5f5'),
        # Софи: длинные светлые волосы, красный берет, кремовое пальто, серьги
        'sofi': dict(skin='c99067', hair='f2d16b', style='long', top='sweater', shirt='fff3e0', pants='212121', shoes='b71c1c', slim=True, girl=True,
                     blush=True, beret='c62828', earrings='e0e0e0', coat='efebe9'),
        # Рэй: светлые кудри, бородка, авиаторы, гавайская рубашка
        'ray': dict(skin='efc6a4', hair='f2d16b', style='curly', top='tshirt', shirt='00acc1', pants='f5f5dc', shoes='8d6e63', glasses='aviator',
                    goatee='c8a040', chain='c0c0c0'),
        # Шакал: седые кудри, борода, шрам, военная куртка
        'shakal': dict(skin='c99067', hair='e0e0e0', style='curly', top='jacket', shirt='556b2f', inner='3e2723', pants='3e3e2e', shoes='3e2723',
                       beard='e0e0e0', scar=True, earrings='d4af37'),
        # Сержант Диас: хвост, тёмно-синяя форма, авиаторы, синий берет
        'diaz': dict(skin='8d5a3b', hair='8e24aa', style='ponytail', top='jacket', shirt='0d1b3e', inner='90caf9', pants='0d1b3e', shoes='111111',
                     slim=True, girl=True, glasses='aviator', beret='0d1b3e', badge=True),
    }


def reroll_random(i, rng, story_looks):
    """Новый случайный горожанин с индексом i (пол прежний), непохожий ни на одного героя."""
    girl = i % 2 == 0
    skins = ['f6d2b8', 'efc6a4', 'e2b48e', 'd8a982', 'c99067', 'a8714a', '8d5a3b', '6b4429']
    hairs = ['1a1a1a', '3e2723', '6b3d1f', 'a0662e', 'd2691e', 'f2d16b', 'e0e0e0', '5d4037', '424242']
    clothes = ['e53935', '1e88e5', '43a047', 'fdd835', '8e24aa', 'fb8c00', '00897b', '546e7a', 'f06292', '3949ab', 'ffffff', '212121', '6d4c41',
               '26c6da', '9ccc65', 'ff7043']
    pants = ['1a237e', '263238', '37474f', '4e342e', '212121', '5d4037', '1565c0', '6a1b9a', '455a64']
    for _ in range(500):
        v = dict(skin=rng.choice(skins), hair=rng.choice(hairs), shirt=rng.choice(clothes), pants=rng.choice(pants),
                 shoes=rng.choice(['fafafa', '212121', '5d4037', 'e53935', '1e88e5']),
                 top=rng.choice(['tshirt', 'tshirt', 'hoodie', 'jacket', 'sweater'] + (['dress'] if girl else ['suit'])), inner=rng.choice(clothes),
                 style=rng.choice(['long', 'ponytail', 'bob', 'curly', 'bob'] if girl else ['short', 'buzz', 'curly', 'slick', 'bald', 'short']),
                 slim=girl)
        if girl:
            v['blush'] = True
            v['girl'] = True
            if rng.random() < 0.4:
                v['bow'] = rng.choice(['ff4f9a', 'ffffff', '7e57c2', '29b6f6'])
        if rng.random() < 0.2:
            v['glasses'] = rng.choice([True, 'shades'])
        if not girl and rng.random() < 0.25:
            v['beard'] = v['hair']
        if rng.random() < 0.2:
            v['cap'] = rng.choice(clothes)
        if v['top'] == 'dress':
            v['dress'] = True
            v['top'] = 'tshirt'
        lf = look_full(v)
        if all(look_distance(lf, base_of(s)) >= 3.0 and not same_combo(lf, s) for s in story_looks):
            return v
    raise SystemExit(f'не нашёл непохожего жителя для c{i:02d}')


def main():
    old = json.load(open(LOOKS))[:56]
    vs = recover()
    assert len(vs) == 56, len(vs)
    for i, (v, l) in enumerate(zip(vs, old)):
        if G.look(v) != l:
            raise SystemExit(f'скин {i}: описание не совпало с citizen_looks.json — gen_assets.py поменялся?\n{G.look(v)}\n{l}')
    designs = story_designs()
    story = [look_full(designs[k]) for k in STORY_IDS]
    for k, l in zip(STORY_IDS, story):
        l['story'] = k
    base = [look_full(v) for v in vs]
    # бывшие скины героев (c13, c15, …) и все «двойники» — рисуем заново
    chars = json.load(open(os.path.join(G.ROOT, '..', '..', 'novograd_story.json')))['characters']
    former = {16 + int(c['skin'][1:]) for c in chars.values() if c.get('skin') and str(c['skin']).startswith('c')}
    rng = random.Random(808)
    redrawn = []
    for i in range(56):
        lf = base[i]
        dup = any(look_distance(lf, base_of(s)) < 3.0 or same_combo(lf, s) for s in story)
        if i >= 16 and (i in former or dup):
            v = reroll_random(i - 16, rng, story)
            vs[i] = v
            base[i] = look_full(v)
            G.save(G.skin(v), 'entity', 'citizen', f'c{i - 16:02d}.png')
            redrawn.append(f'c{i - 16:02d}')
        elif i < 16 and dup:
            print(f'внимание: базовый скин {i} похож на героя — проверь вручную')
    for k in STORY_IDS:
        G.save(G.skin(designs[k]), 'entity', 'citizen', f'story_{k}.png')
    with open(LOOKS, 'w') as f:
        json.dump(base + story, f)
    worst = min((look_distance(b, base_of(s)), j, s['story']) for j, b in enumerate(base) for s in story)
    print(f'перерисовано {len(redrawn)}: {" ".join(redrawn)}')
    print(f'героев {len(story)}; ближайший житель к герою: скин {worst[1]} ↔ {worst[2]}, расстояние {worst[0]:.2f} (двойник < 2.5)')


if __name__ == '__main__':
    main()
