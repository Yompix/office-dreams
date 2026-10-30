#!/usr/bin/env python3
"""Собирает newgame/GAME_PROMPT.md — всю игру одним файлом для ИИ-ведущего.

Состав: prompt_head.md (как вести игру, старт, «не выбрал», действия) + разделы DESIGN.md
(мир, правда, герои, устройство, механики, концовки, флаги) + сценарии ch1–ch5.md без английских строк.
Запуск после любых правок глав: python3 newgame/build_prompt.py
"""
import re
from pathlib import Path

D = Path(__file__).resolve().parent
DESIGN_SECTIONS = {'3', '4', '5', '6', '7', '9', '10'}


def design_part():
    parts = re.split(r'(?m)^(?=## \d+\. )', (D / 'DESIGN.md').read_text(encoding='utf-8'))
    out = []
    for p in parts:
        m = re.match(r'## (\d+)\. ', p)
        if not m or m.group(1) not in DESIGN_SECTIONS:
            continue
        p = re.sub(r'(?m)^## \d+\. ', '## ', p)
        p = re.sub(r'(?m)^### \d+\.\d+ ', '### ', p)
        p = p.replace('## Правда (спойлер всей игры)', '## Правда (секрет: игроку не раскрывать, пока не откроется в сцене)')
        p = p.replace('свои выборы рядом с процентами других игроков', 'свои выборы по главам')
        p = '\n'.join(l for l in p.split('\n') if 'Свободное хождение' not in l)
        p = re.sub(r'\n---\s*$', '\n', p.rstrip() + '\n')
        out.append(p.rstrip() + '\n')
    return '\n'.join(out)


def chapter(n):
    s = (D / f'ch{n}.md').read_text(encoding='utf-8')
    s = re.sub(r'(?m)^(?:Сценарий «как фильм» — \*\*утверждён владельцем 30 окт\*\* \(«[^»]*»\)\.'
               r'|Черновик сценария «как фильм», 30 окт\. Владелец читает и правит\.)\s*', '', s)
    lines = []
    for l in s.split('\n'):
        if re.match(r'^\s*\*[A-Z][A-Z]+:.*\*\s*$', l):  # английская строка под русской
            continue
        if ('в английской версии' in l or l.startswith('Английский текст карточек')
                or l.startswith('Под каждой русской репликой') or l.startswith('Остальные языки —')):
            continue
        l = re.sub(r' \*[A-Z]+: [^*]*\*', '', l)
        if l.startswith('#'):
            l = '#' + l.replace('# WHAT THE LAKE KEEPS — ', '# ', 1)
        lines.append(l)
    return re.sub(r'\n{3,}', '\n\n', '\n'.join(lines)).strip() + '\n'


head = (D / 'prompt_head.md').read_text(encoding='utf-8')
full = (head.rstrip() + '\n\n' + design_part() + '\n---\n\n# Сценарии\n\n'
        + '\n---\n\n'.join(chapter(n) for n in range(1, 6)))
(D / 'GAME_PROMPT.md').write_text(full, encoding='utf-8')
print('GAME_PROMPT.md:', len(full), 'знаков,', full.count('\n'), 'строк')
