"""WHAT THE LAKE KEEPS, глава 1 — эталонная сцена заправки «24» по согласованному постановочному листу
(godot/lake/docs/staging/ST24_STAGING_SHEET.md, ответы редактора — docs/audit/EDITOR_DECISIONS_2026-11-04.md).
Правит godot/lake/data/ch1.json на месте; повторный запуск ничего не дублирует. Запускать ПОСЛЕДНИМ — после
ch1_walk.py, gestures.py и fixes_mp.py (они пишут прогулку st24 целиком):
    python3 tools/lake/story/st24_ref.py
Что делает (журнал — docs/staging/ST24_IMPLEMENTATION.md):
  * заправка — первое действие игрока (эпизод 1, шаг 7): пистолет в руку, «держать» до отсечки (табло бежит, отпустил —
    стоит, провала нет), пистолет обратно; подписи «Щелчок. Колонка встаёт…» и «Гул в голове не стихает» убраны;
  * ведро (эпизод 2): лёд — действие в два нажатия (трещины → пластины; 3 с без нажатия — Ной разбивает сам),
    подпись «Ной разбивает лёд…» убрана — её заменило действие.
"""
import json, os

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
DATA = os.path.join(ROOT, 'godot/lake/data/ch1.json')


def L(ru, en):
    return {'ru': ru, 'en': en}


REFUEL = [   # эпизод 1, шаг 7: Ной у горловины (лючок над левым задним колесом), камера от заднего угла машины —
    # рука с пистолетом в горловине, шланг от колонки, табло колонки за спиной Ноя
    {'ref': 'refuel', 'place': {'who': 'noah', 'at': [12.9, 0.0, -3.45], 'face': 90, 'clip': 'Idle'},
     'cam': [[13.95, 1.45, -1.35], [13.05, 1.0, -3.6], 44], 'fx': 'nozzle_take', 'dur': 1.0},
    {'ref': 'refuel', 'qte': 'hold', 't': 7.0, 'what': 'fuel',
     'hint': L('Заправить — держите [E]', 'Fill up — hold [E]')},
    {'ref': 'refuel', 'cam': [[13.05, 1.47, -4.62], [12.3, 1.42, -5.0], 28], 'dur': 1.4},   # отсечка: табло встало
    {'ref': 'refuel', 'fx': 'nozzle_back', 'dur': 1.0},
]
ICE = {'ref': 'ice', 'qte': 'ice', 'what': 'ice', 'hint': L('Разбить лёд — [E]', 'Break the ice — [E]')}
BUCKET_CAM = [[12.98, 1.0, -8.1], [12.45, 0.42, -8.56], 36]   # крупно: ведро, корка льда, руки Ноя
HANDS_CAM = [[12.3, 1.45, -7.7], [13.0, 1.15, -8.5], 38]       # спереди-сбоку: Ной встал и смотрит на ладони


def main():
    j = json.load(open(DATA, encoding='utf-8'))
    w = j['explore']['st24']
    # --- заправка — первое действие игрока; подписи-описания убраны ---
    intro = [b for b in w['intro'] if b.get('ref') != 'refuel'
             and (b.get('note') or {}).get('ru') not in ('Щелчок. Колонка встаёт: полный бак.', 'Гул в голове не стихает.')]
    w['intro'] = REFUEL + intro
    # --- ведро: лёд — действием ---
    sp = next(s for s in w['spots'] if s['id'] == 'bucket')
    bt = sp['beats']
    if not any(b.get('qte') == 'ice' for b in bt):   # уже сделано — не трогать
        for i, b in enumerate(bt):
            if (b.get('note') or {}).get('ru', '').startswith('Ной разбивает лёд'):
                crouch = {'anim': b.get('anim', {'who': 'noah', 'clip': 'Crouch_Idle'}), 'dur': 0.7, 'ref': 'ice'}
                plunge = {'fx': b.get('fx', 'snd:splash:-14'), 'dur': 1.2, 'ref': 'ice'}   # руки в воду
                bt[i:i + 1] = [crouch, ICE, plunge]
                break
        else:
            raise SystemExit('st24/bucket: не нашёл шаг «Ной разбивает лёд»')
    # постановка у ведра: крупно ведро (синяя вода, тонкий лёд — видно, подпись не нужна), рука бьёт по корке на каждом
    # нажатии, обе руки — в воду, вода розовеет (подпись не нужна); ладони — потом, с подписью, пока нет крупного плана рук
    for b in bt:
        nt = (b.get('note') or {}).get('ru', '')
        if nt.startswith('Ведро со щёткой для стёкол'):
            b.pop('note')
            b.update({'ref': 'ice', 'cam': BUCKET_CAM, 'dur': 1.6})
        elif b.get('ref') == 'ice' and b.get('anim'):
            b['fx'] = 'ice_ready'
        elif b.get('qte') == 'ice':
            b['cam'] = BUCKET_CAM
        elif b.get('ref') == 'ice' and b.get('fx') in ('snd:splash:-14', 'hands_in_water'):
            b.update({'fx': 'hands_in_water', 'cam': BUCKET_CAM, 'dur': 1.4})
        elif nt == 'Вода розовеет.':
            b.pop('note')
            b.update({'ref': 'ice', 'cam': BUCKET_CAM, 'dur': 2.2})
        elif nt.startswith('Ладони чистые'):
            b['fx'] = 'hands_out'
            b['cam'] = HANDS_CAM
    # Айрис на крыльце (s5): таз в 8 см от края, как у Дэнни (game.gd DANNY_SEAT) — раньше сидела в 30 см от края:
    # колени над бетоном, голени и стопы в ступеньке (владелец, кадр 4 ноя: «ноги в текстурах застревают»)
    for b in j['scenes']['s5']['beats']:
        if (b.get('sit') or {}).get('who') == 'iris':
            b['place']['at'] = [20.08, 0.38, -4.05]
            b['sit']['at'] = [19.98, 0.48, -4.05]
    open(DATA, 'w', encoding='utf-8').write(json.dumps(j, ensure_ascii=False, indent=1) + '\n')
    print('ch1.json: st24 — заправка удержанием, лёд в ведре действием')


if __name__ == '__main__':
    main()
