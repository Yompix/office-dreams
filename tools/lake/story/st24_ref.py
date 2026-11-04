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


REFUEL = [   # эпизод 1, шаг 7: камера ¾ сзади-сбоку у бака, в глубине — ступенька с детьми
    {'ref': 'refuel', 'place': {'who': 'noah', 'at': [13.0, 0.0, -3.3], 'face': 90, 'clip': 'Idle'},
     'cam': [[12.35, 1.62, -1.55], [13.55, 1.0, -3.6], 46], 'fx': 'nozzle_take', 'dur': 0.9},
    {'ref': 'refuel', 'qte': 'hold', 't': 7.0, 'what': 'fuel',
     'hint': L('Заправить — держите [E]', 'Fill up — hold [E]')},
    {'ref': 'refuel', 'fx': 'nozzle_back', 'dur': 1.0},
]
ICE = {'ref': 'ice', 'qte': 'ice', 'what': 'ice', 'hint': L('Разбить лёд — [E]', 'Break the ice — [E]')}


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
    open(DATA, 'w', encoding='utf-8').write(json.dumps(j, ensure_ascii=False, indent=1) + '\n')
    print('ch1.json: st24 — заправка удержанием, лёд в ведре действием')


if __name__ == '__main__':
    main()
