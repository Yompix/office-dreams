#!/usr/bin/env python3
"""0.9.8: банкомат «Сити-Банка» — модель блока (отдельно стоящий с козырьком и настенный в нише фасада), текстуры
16 пикс. на блок и экран с состояниями (приветствие, PIN, сумма, «возьмите деньги», «заберите карту»).
Прорезь карты, клавиатура и лоток стоят там, куда тянется рука в анимации (client/anim/HandsPose: ATM_*).

Запуск из папки novograd: python3 tools/gen_atm.py
"""
import json, os, random
from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'novograd')
TB = os.path.join(ROOT, 'textures', 'block')
TM = os.path.join(ROOT, 'textures', 'misc')
R = random.Random(98)

GRAPH, DARK, EDGE, HI = (42, 46, 53), (27, 30, 35), (60, 66, 75), (78, 86, 97)
CHROME, CHROME_D = (174, 182, 191), (120, 128, 138)
GREEN, GREEN_D, GREEN_L = (31, 164, 90), (20, 112, 60), (98, 229, 126)
WHITE, BLACK = (236, 242, 245), (10, 10, 12)
SCR, SCR_D, SCR_L = (15, 59, 95), (7, 36, 61), (120, 190, 240)


def img(w=16, h=16, c=GRAPH):
    return Image.new('RGBA', (w, h), c + (255,))


def px(im, x, y, c):
    if 0 <= x < im.width and 0 <= y < im.height:
        im.putpixel((x, y), c + (255,) if len(c) == 3 else c)


def rect(im, x0, y0, x1, y1, c):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            px(im, x, y, c)


def noise(im, amt=5):
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = im.getpixel((x, y))
            d = R.randint(-amt, amt)
            im.putpixel((x, y), (max(0, min(255, r + d)), max(0, min(255, g + d)), max(0, min(255, b + d)), a))


def body():
    im = img()
    noise(im, 4)
    rect(im, 0, 0, 15, 0, HI)
    rect(im, 0, 15, 15, 15, DARK)
    rect(im, 0, 0, 0, 15, EDGE)
    rect(im, 15, 0, 15, 15, DARK)
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        px(im, x, y, CHROME_D)
    return im


def front():
    """Нижняя тумба спереди: зелёная полоса бренда, логотип, замок сервисной дверцы, решётка."""
    im = body()
    rect(im, 1, 1, 14, 2, GREEN)
    rect(im, 1, 3, 14, 3, GREEN_D)
    # логотип: зелёный квадрат с белой «колонной» банка
    rect(im, 6, 5, 9, 9, GREEN)
    rect(im, 6, 5, 9, 5, GREEN_L)
    for x in (6, 8):
        rect(im, x + 1 if x == 6 else x, 6, x + 1 if x == 6 else x, 8, WHITE)
    rect(im, 6, 9, 9, 9, WHITE)
    # дверца: шов и замок
    rect(im, 2, 11, 13, 11, DARK)
    px(im, 12, 12, CHROME)
    px(im, 12, 13, CHROME_D)
    for x in range(3, 10, 2):
        rect(im, x, 13, x, 14, DARK)
    return im


def upper():
    """Верх спереди: рамка экрана, глазок камеры, динамик, стрелки к прорезям."""
    im = body()
    rect(im, 2, 1, 13, 1, DARK)
    px(im, 8, 0, (40, 40, 40))
    px(im, 8, 0, (200, 40, 40))  # камера (красный огонёк)
    # рамка экрана (сам экран — отдельный элемент поверх)
    rect(im, 1, 2, 14, 9, DARK)
    rect(im, 1, 2, 14, 2, EDGE)
    # зелёные стрелки к прорези карты (справа от игрока = слева на текстуре севера)
    px(im, 13, 11, GREEN_L)
    px(im, 14, 11, GREEN)
    # динамик
    for x in range(10, 14):
        if x % 2 == 0:
            px(im, x, 14, DARK)
    return im


def keypad():
    """Полка с клавиатурой (вид сверху): 3×3 цифры + ряд «0», справа — красная, жёлтая, зелёная."""
    im = img(16, 16, (34, 38, 44))
    noise(im, 3)
    rect(im, 0, 0, 15, 0, HI)
    # область полки: x 2..14, z 0.5..4 → в текстуре uv [2,0.5,14,4]
    # две строки по три клавиши (с промежутками) + функциональные справа
    for x in (5, 7, 9):
        for z in (1, 3):
            px(im, x, z, CHROME)
            px(im, x, z + 1 if z == 3 else z, CHROME)
    rect(im, 4, 0, 10, 0, (24, 26, 30))
    px(im, 11, 1, (200, 50, 50))
    px(im, 11, 2, (230, 190, 40))
    px(im, 11, 3, (40, 190, 80))
    px(im, 3, 2, (60, 66, 75))  # бесконтактная зона
    px(im, 13, 2, (60, 66, 75))
    return im


def parts():
    """Детали: 0–3 прорезь карты (чёрная + зелёный огонёк), 4–7 лоток (хромированная губа),
    8–11 прорезь чека, 12–15 световая панель под козырьком."""
    im = img()
    rect(im, 0, 0, 15, 3, BLACK)
    rect(im, 0, 3, 15, 3, GREEN_L)
    rect(im, 0, 0, 15, 0, CHROME_D)
    rect(im, 0, 4, 15, 7, CHROME)
    rect(im, 1, 5, 14, 6, BLACK)
    rect(im, 0, 4, 15, 4, (215, 222, 228))
    rect(im, 0, 8, 15, 11, (50, 54, 60))
    rect(im, 2, 9, 13, 10, BLACK)
    rect(im, 4, 10, 11, 10, WHITE)
    rect(im, 0, 12, 15, 15, WHITE)
    rect(im, 0, 15, 15, 15, (200, 220, 230))
    return im


def hood():
    """Козырёк: спереди зелёная полоса с белой надписью (пиксельные «буквы» СИТИ БАНК), сверху — графит."""
    im = body()
    rect(im, 0, 0, 15, 2, GREEN)
    rect(im, 0, 3, 15, 3, GREEN_D)
    # «СИТИ БАНК» в 2 пикс. высотой — штрихи букв
    marks = [1, 3, 4, 6, 8, 9, 11, 12, 14]
    for x in marks:
        px(im, x, 1, WHITE)
    rect(im, 0, 0, 0, 2, WHITE)  # логотип-колонна слева
    return im


# ---------------------------------------------------------------- экран: заставка (анимированная текстура блока)
def screen_frames():
    frames = []
    for k in range(4):
        im = img(16, 16, SCR)
        for y in range(16):
            for x in range(16):
                t = y / 15
                c = tuple(int(SCR[i] * (1 - t) + SCR_D[i] * t) for i in range(3))
                px(im, x, y, c)
        # логотип
        rect(im, 6, 3, 9, 6, GREEN)
        rect(im, 7, 4, 7, 5, WHITE)
        rect(im, 8, 4, 8, 5, WHITE)
        rect(im, 6, 6, 9, 6, WHITE)
        if k < 3:
            # карта и стрелка вправо (мигает)
            rect(im, 4, 9, 9, 12, (255, 193, 7))
            rect(im, 5, 10, 6, 10, (176, 136, 0))
            if k != 1:
                rect(im, 11, 10, 12, 11, SCR_L)
                px(im, 13, 10, SCR_L)
                px(im, 13, 11, SCR_L)
        else:
            # реклама кредита: зелёная плашка и «%»
            rect(im, 2, 9, 13, 13, GREEN_D)
            rect(im, 2, 9, 13, 9, GREEN)
            px(im, 5, 10, WHITE); px(im, 7, 12, WHITE); px(im, 6, 11, WHITE); px(im, 5, 12, WHITE); px(im, 7, 10, WHITE)
            rect(im, 9, 11, 12, 11, WHITE)
        frames.append(im)
    sheet = Image.new('RGBA', (16, 64))
    for i, f in enumerate(frames):
        sheet.paste(f, (0, i * 16))
    return sheet


# ---------------------------------------------------------------- экран: состояния (рисует клиент поверх во время действия)
def states():
    """18×12 на кадр, 32 пикс./блок: 0 «вставьте карту», 1–5 PIN (0–4 точки и «ок»), 6 сумма, 7 обработка,
    8 «возьмите деньги», 9 «заберите карту»."""
    W, H, N = 18, 12, 10
    sheet = Image.new('RGBA', (W, H * N))

    def base():
        im = img(W, H, SCR)
        for y in range(H):
            for x in range(W):
                t = y / (H - 1)
                px(im, x, y, tuple(int(SCR[i] * (1 - t) + SCR_D[i] * t) for i in range(3)))
        rect(im, 0, 0, W - 1, 1, GREEN)
        px(im, 1, 0, WHITE)
        return im

    for n in range(N):
        im = base()
        if n == 0:
            rect(im, 6, 4, 11, 7, (255, 193, 7))
            rect(im, 7, 5, 8, 5, (176, 136, 0))
            rect(im, 13, 5, 15, 6, SCR_L)
            px(im, 16, 5, SCR_L); px(im, 16, 6, SCR_L)
        elif 1 <= n <= 5:
            # поле PIN: 4 ячейки, заполнено n−1
            for i in range(4):
                x0 = 3 + i * 3
                rect(im, x0, 5, x0 + 1, 7, SCR_D)
                if i < n - 1:
                    rect(im, x0, 6, x0 + 1, 6, WHITE)
            if n == 5:
                rect(im, 7, 9, 10, 10, GREEN_L)
        elif n == 6:
            for i in range(3):
                for j in range(2):
                    rect(im, 2 + i * 5, 3 + j * 4, 5 + i * 5, 5 + j * 4, (30, 136, 229))
                    px(im, 3 + i * 5, 4 + j * 4, WHITE)
        elif n == 7:
            for i, (x, y) in enumerate(((8, 4), (10, 5), (10, 7), (8, 8), (6, 7), (6, 5))):
                px(im, x, y, tuple(min(255, 80 + i * 30) for _ in range(3)))
        elif n == 8:
            for i in range(3):
                rect(im, 4 + i * 2, 3 + i, 11 + i * 2, 6 + i, (95, 184, 107))
                rect(im, 4 + i * 2, 3 + i, 11 + i * 2, 3 + i, (150, 220, 160))
            rect(im, 8, 9, 9, 10, GREEN_L)
        elif n == 9:
            rect(im, 4, 3, 11, 7, (255, 193, 7))
            rect(im, 5, 4, 6, 4, (176, 136, 0))
            rect(im, 13, 4, 15, 5, SCR_L)
            px(im, 12, 4, SCR_L); px(im, 12, 5, SCR_L)
        sheet.paste(im, (0, n * H))
    return sheet


# ---------------------------------------------------------------- модели
def face(tex, uv=None, cull=None, shade=True):
    f = {'texture': tex}
    if uv:
        f['uv'] = uv
    return f


def el(fr, to, faces, shade=True):
    for k, f in faces.items():
        if 'uv' not in f:
            f['uv'] = auto(fr, to, k)
    e = {'from': fr, 'to': to, 'faces': faces}
    if not shade:
        e['shade'] = False
    return e


def fit(a0, a1):
    """Отрезок → в пределы 0..16 (элементы выше блока: иначе UV уходит за спрайт в атласе)."""
    if a1 > 16:
        a0, a1 = a0 - (a1 - 16), 16
    if a0 < 0:
        a0, a1 = 0, a1 - a0
    return a0, min(16, a1)


def auto(fr, to, side):
    x0, y0, z0 = fr
    x1, y1, z1 = to
    if side in ('north', 'south'):
        u0, u1 = fit(16 - x1, 16 - x0) if side == 'north' else fit(x0, x1)
        v0, v1 = fit(16 - y1, 16 - y0)
    elif side in ('east', 'west'):
        u0, u1 = fit(16 - z1, 16 - z0) if side == 'east' else fit(z0, z1)
        v0, v1 = fit(16 - y1, 16 - y0)
    else:
        u0, u1 = fit(x0, x1)
        v0, v1 = fit(z0, z1)
    return [round(u0, 3), round(v0, 3), round(u1, 3), round(v1, 3)]


def all6(tex, north=None, up=None, down=None):
    return {'north': face(north or tex), 'south': face(tex), 'east': face(tex), 'west': face(tex), 'up': face(up or tex), 'down': face(down or tex)}


def model(wall):
    T = {'body': 'novograd:block/atm_body', 'front': 'novograd:block/atm_front', 'upper': 'novograd:block/atm_upper', 'keypad': 'novograd:block/atm_keypad',
         'parts': 'novograd:block/atm_parts', 'hood': 'novograd:block/atm_hood', 'screen': 'novograd:block/atm_screen', 'particle': 'novograd:block/atm_upper'}
    if wall:
        T['stone'] = 'minecraft:block/polished_andesite'
    els = []
    back = 16
    # тумба и цоколь
    els.append(el([1, 0, 4], [15, 16, back], all6('#body', north='#front')))
    els.append(el([0.5, 0, 3.5], [15.5, 1, back], all6('#body')))
    # полка с клавиатурой
    els.append(el([2, 15.5, 0], [14, 17, 4], {'north': face('#body', [2, 0, 14, 1.5]), 'east': face('#body'), 'west': face('#body'),
                                                  'up': face('#keypad', [2, 0, 14, 4]), 'down': face('#body')}))
    # верх: корпус, экран, прорези
    els.append(el([1, 17, 4], [15, 28, back], all6('#body', north='#upper')))
    els.append(el([3.5, 20.5, 3.9], [12.5, 26.5, 4], {'north': face('#screen', [0, 2.5, 16, 13.2])}, shade=False))
    els.append(el([3, 19.2, 3.4], [6.5, 20, 4], {'north': face('#parts', [0, 0, 16, 3.5]), 'up': face('#parts', [0, 0, 16, 1]), 'down': face('#parts', [0, 0, 16, 1]),
                                                  'east': face('#parts', [0, 0, 1, 1]), 'west': face('#parts', [0, 0, 1, 1])}))
    els.append(el([3, 18.1, 3.6], [6.5, 18.6, 4], {'north': face('#parts', [0, 8, 16, 11])}))
    els.append(el([7.5, 18.1, 3.4], [13, 19.6, 4], {'north': face('#parts', [0, 4, 16, 8]), 'up': face('#parts', [0, 4, 16, 5]), 'down': face('#parts', [0, 4, 16, 5]),
                                                     'east': face('#parts', [0, 4, 1, 5]), 'west': face('#parts', [0, 4, 1, 5])}))
    if not wall:
        # козырёк с подсветкой снизу и шторки приватности
        els.append(el([0, 28, 0], [16, 30, 16], {'north': face('#hood', [0, 0, 16, 2]), 'south': face('#body'), 'east': face('#body', [0, 0, 16, 2]),
                                                  'west': face('#body', [0, 0, 16, 2]), 'up': face('#body'), 'down': face('#parts', [0, 12, 16, 16])}, shade=True))
        els.append(el([0, 17, 1.5], [0.6, 28, 16], all6('#body')))
        els.append(el([15.4, 17, 1.5], [16, 28, 16], all6('#body')))
    else:
        # ниша в фасаде: каменная рамка, световая вывеска сверху
        els.append(el([0, 0, 3], [1, 30, 4], all6('#stone')))
        els.append(el([15, 0, 3], [16, 30, 4], all6('#stone')))
        els.append(el([0, 28, 1], [16, 30, 4], {'north': face('#hood', [0, 0, 16, 2]), 'down': face('#parts', [0, 12, 16, 16]), 'up': face('#stone'),
                                                 'east': face('#stone'), 'west': face('#stone')}))
    return {'parent': 'block/block', 'textures': T, 'elements': els,
            'display': {'gui': {'rotation': [30, 225, 0], 'translation': [0, -2, 0], 'scale': [0.5, 0.5, 0.5]},
                        'fixed': {'scale': [0.5, 0.5, 0.5]}, 'ground': {'scale': [0.3, 0.3, 0.3]}}}


def blockstate(name):
    v = {}
    for f, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
        d = {'model': f'novograd:block/{name}'}
        if y:
            d['y'] = y
        v[f'facing={f}'] = d
    return {'variants': v}


def main():
    os.makedirs(TB, exist_ok=True)
    os.makedirs(TM, exist_ok=True)
    body().save(os.path.join(TB, 'atm_body.png'))
    front().save(os.path.join(TB, 'atm_front.png'))
    upper().save(os.path.join(TB, 'atm_upper.png'))
    keypad().save(os.path.join(TB, 'atm_keypad.png'))
    parts().save(os.path.join(TB, 'atm_parts.png'))
    hood().save(os.path.join(TB, 'atm_hood.png'))
    screen_frames().save(os.path.join(TB, 'atm_screen.png'))
    with open(os.path.join(TB, 'atm_screen.png.mcmeta'), 'w') as f:
        json.dump({'animation': {'frametime': 30, 'frames': [0, 1, 0, 1, 0, 1, 3, 3]}}, f)
    states().save(os.path.join(TM, 'atm_states.png'))
    old = os.path.join(TB, 'atm_side.png')
    if os.path.exists(old):
        os.remove(old)
    for name, wall in (('atm', False), ('atm_wall', True)):
        with open(os.path.join(ROOT, 'models', 'block', name + '.json'), 'w') as f:
            json.dump(model(wall), f, indent=1)
        with open(os.path.join(ROOT, 'blockstates', name + '.json'), 'w') as f:
            json.dump(blockstate(name), f, indent=1)
        with open(os.path.join(ROOT, 'models', 'item', name + '.json'), 'w') as f:
            json.dump({'parent': f'novograd:block/{name}'}, f)
    print('банкомат: модели atm, atm_wall; текстуры и экран готовы')


if __name__ == '__main__':
    main()
