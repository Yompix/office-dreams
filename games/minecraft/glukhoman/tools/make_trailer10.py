#!/usr/bin/env python3
"""Трейлер 1.0 «Порожняк» (YouTube, ~90 с, 16:9, VHS) и три вертикальных ролика 9:16 (крючок в первую секунду, титры
вшиты). Только кадры игры 1.0 (клипы Autotest trailer: clips/*.mp4 + журналы событий) и наши звуки. Концовки не показываются.

  /tmp/claude-0/ttsenv/bin/python tools/make_trailer10.py [main|shorts|all]
"""
import os, sys
import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import make_trailer3 as t3  # noqa: E402
from make_trailer3 import S, place, loop, hp, SR, FONT, FONTB, text, build, lufs  # noqa: E402

OUT = '/tmp/claude-0/glukhoman-trailer3'
CLIPS = f'{OUT}/clips'
t3.CLIPS = CLIPS
LINK = 'office-dreams-production.up.railway.app/lantern/download'


def events(clip):
    p = f'{CLIPS}/{clip}.events.tsv'
    out = []
    if os.path.exists(p):
        for row in open(p, encoding='utf-8'):
            q = row.rstrip('\n').split('\t')
            if q[1] == 'SND':
                out.append((float(q[0]), q[2], float(q[3]), float(q[4])))
    return out


def sound_for(edl_offsets):
    """edl_offsets: [(clip, начало в клипе, длина)] — звук сцены по журналам + ветер, плёнка, акценты."""
    def f(times, total):
        a = np.zeros(int(total * SR) + SR)
        a += loop(hp(S('wind'), 120), len(a) / SR) * 0.10
        for (s_, t0, t1), (clip, a_, d) in zip(times, edl_offsets):
            if s_ in ('black', 'tape', 'play'):
                if s_ == 'tape':
                    t3.tapefx(a, t0)
                continue
            for t, name, vol, dist in events(clip):
                if a_ <= t < a_ + d:
                    try:
                        place(a, S(name), t0 + t - a_, vol / (1 + dist / 7) * (1.4 if name == 'knock' else 1.0))
                    except FileNotFoundError:
                        pass
            if clip == 'c4_face':
                place(a, S('stinger'), t0 + max(0.1, 9.0 - a_), 0.9)
            if clip == 'pr_train':
                place(a, S('whistle'), t0 + 0.5, 0.7)
        return a
    return f


MAIN = [('play', 0, 2.0), ('pr_train', 2.6, 6.5), ('tape', 0, 0.12), ('c1_handover', 11.6, 5.5), ('tape', 0, 0.1),
        ('demo_watcher', 3.0, 6.0), ('demo_watcher', 9.0, 2.6), ('tape', 0, 0.12), ('demo_ch2', 2.5, 7.0), ('c2_pass', 4.0, 6.0),
        ('tape', 0, 0.1), ('pr_board', 7.0, 6.0), ('demo_ch3', 0.5, 7.0), ('demo_ch3', 8.6, 3.4), ('tape', 0, 0.12),
        ('c1_handover', 24.4, 6.0), ('c4_rollcall', 11.0, 5.0), ('demo_moments', 16.0, 5.5), ('demo_ch5', 0.5, 5.5),
        ('demo_watcher', 21.0, 5.0), ('c4_face', 6.0, 5.6), ('black', 0, 1.0), ('black', 0, 8.0)]


def main_texts(times):
    T = {s_ + str(i): (t0, t1) for i, (s_, t0, t1) in enumerate(times)}
    seg = lambda i: (times[i][1], times[i][2])
    f = []
    f.append(text(0.2, 1.9, 'КАССЕТА №1 · 14.10.1996', 54, '(h/2)-40', font=t3.MONO if hasattr(t3, 'MONO') else FONT))
    a, b = seg(1)
    f.append(text(a + 0.5, b - 0.3, 'Ветка 41. Рельсы давно сняли.', 50, 'h-190', box=True))
    a, b = seg(3)
    f.append(text(a + 0.2, b - 0.2, 'Тебя наняли снимать её каждую ночь', 50, 'h-190', box=True))
    a, b = seg(5)
    f.append(text(a + 0.2, b, 'Глазами он есть. В объективе — нет.', 50, 'h-190', box=True))
    a, b = seg(6)
    f.append(text(a + 0.1, b, 'Пока снимаешь — он не двигается', 50, 'h-190', box=True))
    a, b = seg(8)
    f.append(text(a + 0.3, b - 0.3, 'Сорок имён. Видно только в ИК', 50, 'h-190', box=True))
    a, b = seg(9)
    f.append(text(a + 0.2, b, 'В 3:14 приходит поезд, которого нет', 50, 'h-190', box=True))
    a, b = seg(11)
    f.append(text(a + 0.2, b, 'Кого снимешь — того он и заберёт', 50, 'h-190', box=True))
    a, b = seg(12)
    f.append(text(a + 0.2, b - 0.2, 'Перегон вслепую. Стук на стыках — сзади', 48, 'h-190', box=True))
    a, b = seg(16)
    f.append(text(a + 0.2, b, 'Перекличка: вас на одного больше', 50, 'h-190', box=True))
    a, b = seg(17)
    f.append(text(a + 0.2, b, 'Одному или вдвоём, втроём, вчетвером', 46, 'h-190', box=True))
    a, b = seg(21)
    f.append(text(a, b, ' ', 10, 'h-100'))
    a, b = seg(22)
    f.append(text(a + 0.1, b, 'ПОРОЖНЯК', 150, '(h/2)-160', font=FONTB))
    f.append(text(a + 0.8, b, 'хоррор-мод · пять глав · три концовки', 46, '(h/2)+20'))
    f.append(text(a + 1.6, b, 'Скачать: ' + LINK, 40, '(h/2)+110'))
    f.append(text(a + 1.6, b, 'Не связано с Mojang', 34, 'h-120', color='white@0.75'))
    return f


SHORTS = {
    'A': ([('c4_face', 8.6, 3.0), ('tape', 0, 0.1), ('c4_face', 0.5, 6.0), ('c1_handover', 30.0, 3.4), ('black', 0, 5.0)],
          'ПЕРЕМОТАЙ. ЧЬЁ ЭТО ЛИЦО?', ['Камера лежала в дежурке всю ночь', 'Табло: «станция занята»', 'А в кадре — он…', 'и лицо у него — твоё']),
    'B': ([('demo_watcher', 9.0, 2.0), ('demo_watcher', 3.0, 6.0), ('demo_watcher', 21.0, 6.0), ('black', 0, 5.0)],
          'НЕ ОПУСКАЙ КАМЕРУ', ['Глазами он есть. В объективе — нет', 'Снимаешь — он замирает', 'Опустил камеру на секунду…', '…и он уже рядом']),
    'C': ([('c2_pass', 6.0, 3.0), ('demo_ch2', 22.0, 9.0), ('c2_pass', 10.0, 5.0), ('black', 0, 5.0)],
          'ОНА ЖЕ СПИТ ДОМА', ['Поезд встал дверью напротив', 'В видоискателе на лавке — Зина', 'Она машет. Она спит дома.', 'В последнем окне — место «40»']),
}


def short_texts(hook, caps):
    def f(times):
        out = [text(0, 1.6, hook, 60 if len(hook) > 18 else 72, '260', font=FONTB, box=True)]
        n = len(times)
        for i, c in enumerate(caps):
            if i + 0 >= n:
                break
            s_, a, b = times[min(i, n - 2)]
            a = max(a, 1.6) if i == 0 else a
            out.append(text(a + 0.2 + (i % 2) * 1.2, b - 0.1, c, 58, 'h-520', box=True))
        a, b = times[-1][1], times[-1][2]
        out.append(text(a + 0.1, b, 'ПОРОЖНЯК', 140, '(h/2)-200', font=FONTB))
        out.append(text(a + 0.6, b, 'хоррор-мод · ссылка в профиле', 50, '(h/2)'))
        out.append(text(a + 0.6, b, 'Не связано с Mojang', 40, 'h-300', color='white@0.75'))
        return out
    return f


if __name__ == '__main__':
    what = sys.argv[1] if len(sys.argv) > 1 else 'all'
    if what in ('main', 'all'):
        build(MAIN, main_texts, sound_for(MAIN), f'{OUT}/trailer_1.0.mp4', crf=23)
        print('trailer_1.0.mp4', lufs(f'{OUT}/trailer_1.0.mp4'))
    if what in ('shorts', 'all'):
        for k, (edl, hook, caps) in SHORTS.items():
            out = f'{OUT}/short_{k}.mp4'
            build(edl, short_texts(hook, caps), sound_for(edl), out, W=1080, H=1920, vertical=True, crf=24)
            print(out, lufs(out))
