#!/usr/bin/env python3
"""0.8: слои-жесты для живых героев (руки/голова/грудь поверх тела) — в citizen.animation.json.

* Из готовых занятий (мокап/ручные клипы) берутся только кости рук, головы и груди: курит, смотрит в телефон, на часы,
  потягивается, думает, пожимает плечами, грустит, злится, хлопает, радуется — ноги остаются от ходьбы/стойки.
* Новые короткие жесты по ключам: испуг, шаг назад (руки перед собой), поправить воротник, руки на груди, кивок,
  «нет» головой, поправить волосы, фейспалм, отдать честь, помахать (короткий).

Повторный запуск заменяет те же ключи. python3 tools/gen_emotes.py
"""
import json
import os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'novograd', 'animations',
                    'citizen.animation.json')
UPPER = ('chest', 'head', 'right_arm', 'right_forearm', 'left_arm', 'left_forearm')
DERIVED = {  # новый жест ← занятие, (петля), множитель амплитуды груди
    'g_smoke': ('smoke', True), 'g_phonecheck': ('phone', True), 'g_watch': ('watch', False), 'g_stretch': ('stretch', False),
    'g_think': ('think', True), 'g_shrug': ('shrug', False), 'g_sad': ('upset', True), 'g_angry': ('argue', True), 'g_clap': ('clap', True),
    'g_cheer': ('cheer', True),
}


def keys(*frames):
    """frames: (t, {кость: [x, y, z]}) → каналы Bedrock."""
    bones = {}
    for t, pose in frames:
        for b, r in pose.items():
            bones.setdefault(b, {'rotation': {}})['rotation'][f'{t:g}'] = r
    return bones


REST = [0, 0, 0]


def arms(r, rf, l, lf):
    return {'right_arm': r, 'right_forearm': rf, 'left_arm': l, 'left_forearm': lf}


def main():
    data = json.load(open(ROOT))
    an = data['animations']
    for new, (src, loop) in DERIVED.items():
        a = an['animation.citizen.' + src]
        b = {k: v for k, v in a['bones'].items() if k in UPPER}
        # «петля» занятия с поворотом на 360° (кодировка некоторых клипов) — нормализуем вручную не нужно: GeckoLib это ест
        an['animation.citizen.' + new] = {'loop': loop, 'animation_length': a['animation_length'], 'bones': b}
    T = 1  # для читаемости
    hand = {
        'g_scared': (False, 2.2, keys(
            (0, {**arms([-20, 10, 10], [-40, 0, 0], [-20, -10, -10], [-40, 0, 0]), 'chest': REST, 'head': REST}),
            (0.25, {**arms([-62, 22, 8], [-105, 0, 0], [-62, -22, -8], [-105, 0, 0]), 'chest': [-10, 0, 0], 'head': [-8, 0, 0]}),
            (0.35, {**arms([-60, 20, 10], [-100, 0, 0], [-60, -20, -10], [-100, 0, 0])}),
            (0.45, {**arms([-63, 23, 7], [-106, 0, 0], [-63, -23, -7], [-106, 0, 0])}),
            (1.7, {**arms([-61, 21, 9], [-102, 0, 0], [-61, -21, -9], [-102, 0, 0]), 'chest': [-9, 0, 0], 'head': [-6, 4, 0]}),
            (2.2, {**arms([-15, 5, 5], [-30, 0, 0], [-15, -5, -5], [-30, 0, 0]), 'chest': REST, 'head': REST}))),
        'g_stepback': (False, 1.4, keys(
            (0, {**arms(REST, REST, REST, REST), 'chest': REST, 'head': REST}),
            (0.3, {**arms([-72, 14, 12], [-55, 0, 0], [-72, -14, -12], [-55, 0, 0]), 'chest': [-14, 0, 0], 'head': [-10, 0, 0]}),
            (1.0, {**arms([-70, 12, 12], [-58, 0, 0], [-70, -12, -12], [-58, 0, 0]), 'chest': [-12, 0, 0], 'head': [-8, 0, 0]}),
            (1.4, {**arms([-20, 0, 5], [-20, 0, 0], [-20, 0, -5], [-20, 0, 0]), 'chest': [-3, 0, 0], 'head': REST}))),
        'g_adjust': (False, 1.7, keys(
            (0, {**arms(REST, REST, REST, REST), 'head': REST}),
            (0.35, {**arms([-62, -28, 0], [-122, 0, 0], [-62, 28, 0], [-122, 0, 0]), 'head': [12, 0, 0]}),
            (0.6, {**arms([-58, -26, 0], [-128, 0, 0], [-58, 26, 0], [-128, 0, 0]), 'head': [8, 0, -3]}),
            (0.85, {**arms([-64, -30, 0], [-118, 0, 0], [-64, 30, 0], [-118, 0, 0]), 'head': [10, 0, 3]}),
            (1.7, {**arms([-5, 0, 3], [-10, 0, 0], [-5, 0, -3], [-10, 0, 0]), 'head': REST}))),
        'g_crossarms': (True, 4.0, keys(
            (0, {**arms([-42, -36, 0], [-112, 0, 0], [-46, 38, 0], [-108, 0, 0]), 'head': [2, 0, 0], 'chest': [-2, 0, 0]}),
            (2.0, {**arms([-44, -34, 0], [-114, 0, 0], [-45, 37, 0], [-110, 0, 0]), 'head': [4, 6, 2], 'chest': [-3, 0, 0]}),
            (4.0, {**arms([-42, -36, 0], [-112, 0, 0], [-46, 38, 0], [-108, 0, 0]), 'head': [2, 0, 0], 'chest': [-2, 0, 0]}))),
        'g_nod': (False, 0.9, keys((0, {'head': REST}), (0.2, {'head': [14, 0, 0]}), (0.4, {'head': [-2, 0, 0]}), (0.6, {'head': [10, 0, 0]}),
                                   (0.9, {'head': REST}))),
        'g_headshake': (False, 1.1, keys((0, {'head': REST}), (0.2, {'head': [3, 18, 0]}), (0.45, {'head': [3, -18, 0]}),
                                         (0.7, {'head': [3, 14, 0]}), (0.9, {'head': [2, -6, 0]}), (1.1, {'head': REST}))),
        'g_hair': (False, 1.8, keys(
            (0, {**arms(REST, REST, REST, REST), 'head': REST}),
            (0.45, {'right_arm': [-152, -10, 22], 'right_forearm': [-88, 0, 0], 'head': [4, 0, -6]}),
            (0.9, {'right_arm': [-158, -8, 18], 'right_forearm': [-96, 0, 0], 'head': [-2, 0, -4]}),
            (1.8, {'right_arm': [-5, 0, 3], 'right_forearm': [-8, 0, 0], 'head': REST}))),
        'g_facepalm': (False, 2.0, keys(
            (0, {**arms(REST, REST, REST, REST), 'head': REST, 'chest': REST}),
            (0.35, {'right_arm': [-92, -34, 0], 'right_forearm': [-112, 0, 0], 'head': [22, 0, 0], 'chest': [6, 0, 0]}),
            (1.5, {'right_arm': [-95, -32, 0], 'right_forearm': [-115, 0, 0], 'head': [24, 4, 0], 'chest': [7, 0, 0]}),
            (2.0, {'right_arm': [-10, 0, 3], 'right_forearm': [-10, 0, 0], 'head': REST, 'chest': REST}))),
        'g_salute': (False, 1.8, keys(
            (0, {'right_arm': REST, 'right_forearm': REST, 'head': REST, 'chest': REST}),
            (0.3, {'right_arm': [-118, -48, 12], 'right_forearm': [-105, 0, 0], 'head': [-3, 0, 0], 'chest': [-3, 0, 0]}),
            (1.4, {'right_arm': [-120, -46, 12], 'right_forearm': [-108, 0, 0], 'head': [-3, 0, 0], 'chest': [-3, 0, 0]}),
            (1.8, {'right_arm': [-8, 0, 3], 'right_forearm': [-8, 0, 0], 'head': REST, 'chest': REST}))),
        'g_wave2': (False, 1.6, keys(
            (0, {'right_arm': REST, 'right_forearm': REST}),
            (0.25, {'right_arm': [-150, 0, 28], 'right_forearm': [-20, 0, 0]}),
            (0.5, {'right_arm': [-150, 0, 12], 'right_forearm': [-25, 0, 0]}),
            (0.75, {'right_arm': [-150, 0, 32], 'right_forearm': [-20, 0, 0]}),
            (1.0, {'right_arm': [-150, 0, 14], 'right_forearm': [-25, 0, 0]}),
            (1.6, {'right_arm': [-6, 0, 3], 'right_forearm': [-6, 0, 0]}))),
    }
    for k, (loop, length, bones) in hand.items():
        an['animation.citizen.' + k] = {'loop': loop, 'animation_length': length, 'bones': bones}
    with open(ROOT, 'w') as f:
        json.dump(data, f)
    print('жестов:', len(DERIVED) + len(hand))


if __name__ == '__main__':
    main()
