#!/usr/bin/env python3
"""WHAT THE LAKE KEEPS — сценарий для редактора (Word) прямо из данных игры.
  python3 tools/lake/scenario_docx.py [ВЫХОД.docx] [--old ПАПКА_СТАРЫХ_DATA]
Реплики RU + EN, ремарки, титры, выборы с таймером и куда ведут, действия, условия показа. С --old новые и изменённые
места подсвечены жёлтым (сверка с прошлой версией данных)."""
import os, sys, json, datetime
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import story_audit as A
from docx import Document
from docx.shared import Pt, RGBColor, Cm
from docx.enum.text import WD_COLOR_INDEX

RU = {'noah': 'НОЙ', 'iris': 'АЙРИС', 'zoe': 'ЗОЯ', 'danny': 'ДЭННИ', 'eva': 'ЕВА', 'agnes': 'АГНЕС', 'hale': 'ХЕЙЛ',
      'roman': 'РОМАН', 'cop': 'ПОЛИЦЕЙСКИЙ', 'trucker': 'ДАЛЬНОБОЙЩИК', 'radio': 'РАДИО', 'anna': 'НОЙ — ЧУЖИМ ГОЛОСОМ',
      'mom': 'АННА', 'victor': 'ВИКТОР'}
GRAY = RGBColor(0x66, 0x66, 0x66)
BLUE = RGBColor(0x2F, 0x55, 0x8A)


def old_set(d):
    s = set()
    if not d:
        return s
    for n in range(1, 6):
        p = os.path.join(d, 'ch%d.json' % n)
        if not os.path.exists(p):
            continue
        for sid, sc in json.load(open(p))['scenes'].items():
            for b in sc['beats']:
                s.update(sig(sid, b))
    return s


def sig(sid, b):
    out = []
    c = json.dumps(b.get('if'), sort_keys=True)
    if 'say' in b:
        out.append((sid, 'say', b['say'], b['ru'], c))
    for k in ('note', 'text'):
        if isinstance(b.get(k), dict):
            out.append((sid, k, b[k]['ru'], c))
    if 'choice' in b:
        for o in b['choice']['opts']:
            out.append((sid, 'opt', o.get('ru', ''), json.dumps(o.get('if'), sort_keys=True), o.get('go')))
    if 'qte' in b:
        out.append((sid, 'qte', b['qte'], b.get('t'), b.get('n'), c))
    return out


def main():
    args = [a for a in sys.argv[1:]]
    old = None
    if '--old' in args:
        old = args[args.index('--old') + 1]
        del args[args.index('--old'):args.index('--old') + 2]
    out = args[0] if args else os.path.join(A.ROOT, 'out/WhatTheLakeKeeps_scenario_editor_v2.docx')
    olds = old_set(old)
    data, chapters, owner, _ = A.load()
    doc = Document()
    st = doc.styles['Normal']
    st.font.name = 'Arial'
    st.font.size = Pt(10.5)
    for s in doc.sections:
        s.left_margin = s.right_margin = Cm(2.0)
    doc.add_heading('WHAT THE LAKE KEEPS', 0)
    doc.add_paragraph('Сценарий интерактивного фильма — версия для редактора (после редактуры диалогов и веток, %s).'
                      % datetime.date.today().strftime('%d.%m.%Y'))
    doc.add_paragraph('Собрано из данных игры (godot/lake/data/ch1–ch5.json): это ровно то, что идёт в игре. '
                      'Реплика — имя и текст по-русски, ниже английский. [ЕСЛИ …] — условие показа (флаги). '
                      'ВЫБОР — варианты с таймером и сценой, куда ведут; [молчание] — что будет, если не выбрать. '
                      'ДЕЙСТВИЕ — что жмёт игрок; «провал →» — куда при неудаче.'
                      + (' Жёлтым — новое или изменённое при редактуре (подробно — docs/DIALOGUE_REVISIONS.md).' if olds else ''))
    cur = 0
    nlines = 0
    for sid, sc in data['scenes'].items():
        ch = owner[sid]
        if ch != cur:
            cur = ch
            t = chapters[ch].get('title') or {}
            doc.add_page_break()
            doc.add_heading('Глава %d. %s' % (ch, (t.get('ru') if isinstance(t, dict) else '') or ''), 1)
        h = doc.add_heading(sid + ('  ·  %s' % sc['time'] if sc.get('time') else '') + ('  ·  сохранение' if sc.get('save') else ''), 3)
        for b in sc['beats']:
            new = olds and any(x not in olds for x in sig(sid, b))
            cond = ('  [ЕСЛИ %s]' % A.cond_s(b['if'])) if b.get('if') else ''
            els = ('  иначе → %s' % b['else']) if b.get('else') else ''

            def para():
                p = doc.add_paragraph()
                p.paragraph_format.space_after = Pt(2)
                return p

            def mark(run):
                if new:
                    run.font.highlight_color = WD_COLOR_INDEX.YELLOW
                return run
            if 'say' in b:
                nlines += 1
                p = para()
                nm = b.get('name')
                nm = (nm.get('ru') if isinstance(nm, dict) else nm) or RU.get(b['say'], b['say'].upper())
                r = mark(p.add_run(nm + ': '))
                r.bold = True
                mark(p.add_run(b['ru']))
                if cond:
                    rr = p.add_run(cond)
                    rr.font.size = Pt(8.5)
                    rr.font.color.rgb = BLUE
                p2 = para()
                r2 = p2.add_run('    ' + b['en'])
                r2.italic = True
                r2.font.color.rgb = GRAY
                r2.font.size = Pt(9.5)
            for k, lab in (('note', ''), ('text', 'ТИТР: ')):
                if isinstance(b.get(k), dict):
                    p = para()
                    r = mark(p.add_run(lab + b[k]['ru']))
                    r.italic = (k == 'note')
                    if cond:
                        rr = p.add_run(cond)
                        rr.font.size = Pt(8.5)
                        rr.font.color.rgb = BLUE
                    p2 = para()
                    r2 = p2.add_run('    ' + lab + b[k]['en'])
                    r2.italic = True
                    r2.font.color.rgb = GRAY
                    r2.font.size = Pt(9)
            if 'choice' in b:
                c = b['choice']
                p = para()
                r = p.add_run('ВЫБОР (%s с):' % c.get('t', 6))
                r.bold = True
                for j, o in enumerate(c['opts']):
                    q = para()
                    q.paragraph_format.left_indent = Cm(0.8)
                    oc = ('  [ЕСЛИ %s]' % A.cond_s(o['if'])) if o.get('if') else ''
                    on = olds and (sid, 'opt', o.get('ru', ''), json.dumps(o.get('if'), sort_keys=True), o.get('go')) not in olds
                    if o.get('silent'):
                        rr = q.add_run('[молчание] → %s' % o.get('go'))
                    else:
                        rr = q.add_run('%d. %s / %s%s → %s' % (j + 1, o['ru'], o['en'], oc, o.get('go')))
                    if on:
                        rr.font.highlight_color = WD_COLOR_INDEX.YELLOW
            if 'peek' in b:
                para().add_run('ВЗГЛЯД (%s с): посмотрел → %s, нет → %s' % (b.get('t', 5), b['yes'], b['no'])).bold = True
            if 'qte' in b:
                h2 = b.get('hint') or {}
                r = mark(para().add_run('ДЕЙСТВИЕ (%s, %s с%s): «%s»%s%s' % (b['qte'], b.get('t'), (', нажатий %s' % b['n']) if 'n' in b else '',
                                                                         h2.get('ru', ''), (' провал → %s' % b['fail']) if b.get('fail') else '', cond)))
                r.bold = True
            if 'set' in b and not any(k in b for k in ('say', 'note', 'text')):
                p = para()
                rr = p.add_run('флаги: ' + ', '.join('%s %+d' % kv for kv in b['set'].items()) + cond)
                rr.font.size = Pt(8)
                rr.font.color.rgb = GRAY
            if b.get('go') or els:
                p = para()
                rr = p.add_run(('→ %s' % b['go'] if b.get('go') else '') + (cond if not any(k in b for k in ('say', 'note', 'text', 'qte')) else '') + els)
                rr.font.color.rgb = BLUE
            if b.get('end'):
                nx = A.chapter_next(chapters, int(b['end']))
                para().add_run('КОНЕЦ ГЛАВЫ %s%s' % (b['end'], (' → %s' % nx) if nx else ' · КОНЕЦ ИГРЫ')).bold = True
    os.makedirs(os.path.dirname(out), exist_ok=True)
    doc.save(out)
    print('сценарий: %s (%d сцен, %d реплик)' % (out, len(data['scenes']), nlines))


if __name__ == '__main__':
    main()
