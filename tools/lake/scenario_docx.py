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
CH_NAME = {1: 'Кровь на руках'}   # у главы 1 названия в данных нет (титр рисует game.gd)
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
        out.append((sid, 'say', b['say'], b['ru'], b.get('en', ''), c))
    for k in ('note', 'text'):
        if isinstance(b.get(k), dict):
            out.append((sid, k, b[k]['ru'], b[k].get('en', ''), c))
    if 'choice' in b:
        for o in b['choice']['opts']:
            out.append((sid, 'opt', o.get('ru', ''), o.get('en', ''), json.dumps(o.get('if'), sort_keys=True), o.get('go')))
    if 'qte' in b:
        out.append((sid, 'qte', b['qte'], b.get('t'), b.get('n'), c))
    return out


def render(doc, sid, beats, data, chapters, olds, depth, cnt):
    """Шаги сцены по порядку; внутри прогулки (точки осмотра, выход) и разговора с темами — с отступом."""
    cnt.setdefault('nested', 0)
    for b in beats:
        new = depth == 0 and olds and any(x not in olds for x in sig(sid, b))
        cond = ('  [ЕСЛИ %s]' % A.cond_s(b['if'])) if b.get('if') else ''
        els = ('  иначе → %s' % b['else']) if b.get('else') else ''

        def para(extra=0.0):
            p = doc.add_paragraph()
            p.paragraph_format.space_after = Pt(2)
            if depth or extra:
                p.paragraph_format.left_indent = Cm(0.8 * depth + extra)
            return p

        def mark(run):
            if new:
                run.font.highlight_color = WD_COLOR_INDEX.YELLOW
            return run

        def small(p, txt, color=BLUE, size=8.5):
            rr = p.add_run(txt)
            rr.font.size = Pt(size)
            rr.font.color.rgb = color
            return rr
        if 'say' in b:
            cnt['lines'] += 1
            if depth:
                cnt['nested'] += 1
            p = para()
            nm = b.get('name')
            nm = (nm.get('ru') if isinstance(nm, dict) else nm) or RU.get(b['say'], b['say'].upper())
            r = mark(p.add_run(nm + ': '))
            r.bold = True
            mark(p.add_run(b['ru']))
            if cond:
                small(p, cond)
            r2 = para().add_run('    ' + b['en'])
            r2.italic = True
            r2.font.color.rgb = GRAY
            r2.font.size = Pt(9.5)
        for k, lab in (('note', ''), ('text', 'ТИТР: ')):
            if isinstance(b.get(k), dict):
                p = para()
                r = mark(p.add_run(lab + b[k]['ru']))
                r.italic = (k == 'note')
                if cond:
                    small(p, cond)
                r2 = para().add_run('    ' + lab + b[k]['en'])
                r2.italic = True
                r2.font.color.rgb = GRAY
                r2.font.size = Pt(9)
        if 'choice' in b:
            c = b['choice']
            para().add_run('ВЫБОР (%s с):' % c.get('t', 6)).bold = True
            for j, o in enumerate(c['opts']):
                q = para(0.8)
                oc = ('  [ЕСЛИ %s]' % A.cond_s(o['if'])) if o.get('if') else ''
                on = olds and (sid, 'opt', o.get('ru', ''), o.get('en', ''), json.dumps(o.get('if'), sort_keys=True), o.get('go')) not in olds
                if o.get('silent'):
                    rr = q.add_run('[молчание] → %s' % o.get('go'))
                else:
                    rr = q.add_run('%d. %s / %s%s → %s' % (j + 1, o['ru'], o['en'], oc, o.get('go')))
                if on:
                    rr.font.highlight_color = WD_COLOR_INDEX.YELLOW
        if 'ask' in b:
            q = b['ask']
            opts = q.get('opts', [])
            mx = q.get('max')
            p = para()
            p.add_run('РАЗГОВОР — темы на выбор%s, без таймера — ждёт игрока%s:' % (
                (', можно выбрать %s' % mx) if mx else '', cond)).bold = True
            for o in opts:
                oc = ('  [ЕСЛИ %s]' % A.cond_s(o['if'])) if o.get('if') else ''
                t = para(0.8)
                rr = t.add_run(('Закончить: ' if o.get('leave') else 'Тема: ') + '%s / %s' % (o.get('ru', ''), o.get('en', '')))
                rr.bold = not o.get('leave')
                if oc:
                    small(t, oc)
                render(doc, sid, o.get('beats', []), data, chapters, olds, depth + 2, cnt)
        if 'explore' in b:
            e = data.get('explore', {}).get(b['explore'], {})
            p = para()
            p.add_run('ПРОГУЛКА «%s» — игрок ведёт %s сам, точки осмотра в любом порядке%s:' % (
                b['explore'], RU.get(e.get('who', 'noah'), 'Ноя').capitalize() if e.get('who', 'noah') != 'noah' else 'Ноя', cond)).bold = True
            if e.get('intro'):
                render(doc, sid, e['intro'], data, chapters, olds, depth + 1, cnt)
            for sp in e.get('spots', []):
                lab = sp.get('label') or {}
                t = para(0.8)
                t.add_run('• %s / %s' % (lab.get('ru', sp.get('id')), lab.get('en', ''))).bold = True
                extra = []
                if sp.get('if'):
                    extra.append('ЕСЛИ %s' % A.cond_s(sp['if']))
                if sp.get('once') is False:
                    extra.append('можно подходить снова')
                if sp.get('after'):
                    extra.append('появляется через %s с' % sp['after'])
                if extra:
                    small(t, '  [%s]' % '; '.join(extra))
                render(doc, sid, sp.get('beats', []), data, chapters, olds, depth + 2, cnt)
                if sp.get('ask'):
                    render(doc, sid, [{'ask': sp['ask']}], data, chapters, olds, depth + 2, cnt)
            for z in e.get('zones', []):
                t = para(0.8)
                t.add_run('• Место на пути%s' % (('  [ЕСЛИ %s]' % A.cond_s(z['if'])) if z.get('if') else '')).bold = True
                render(doc, sid, z.get('beats', []), data, chapters, olds, depth + 2, cnt)
            nd = e.get('nudge')
            if isinstance(nd, dict) and nd.get('beats'):
                t = para(0.8)
                t.add_run('• Подсказка, если долго стоять (%s с)' % nd.get('after', '')).bold = True
                render(doc, sid, nd['beats'], data, chapters, olds, depth + 2, cnt)
            for dr in e.get('doors', []):   # двери, которые герой открывает сам и заходит внутрь (4 ноя)
                lab = dr.get('label') or {}
                t = para(0.8)
                t.add_run('• %s / %s' % (lab.get('ru', dr.get('id')), lab.get('en', ''))).bold = True
                small(t, '  [дверь: створка открывается, можно зайти внутрь]')
            ex = e.get('exit') or {}
            if ex:
                t = para(0.8)
                t.add_run('• Выход: %s / %s' % ((ex.get('label') or {}).get('ru', ''), (ex.get('label') or {}).get('en', ''))).bold = True
                if ex.get('need'):
                    small(t, '  [только когда открыта дверь «%s»]' % ex['need'])
                render(doc, sid, ex.get('beats', []), data, chapters, olds, depth + 2, cnt)
        if 'peek' in b:
            para().add_run('ВЗГЛЯД (%s с): посмотрел → %s, нет → %s' % (b.get('t', 5), b['yes'], b['no'])).bold = True
        if 'qte' in b:
            h2 = b.get('hint') or {}
            r = mark(para().add_run('ДЕЙСТВИЕ (%s, %s с%s): «%s»%s%s' % (b['qte'], b.get('t'), (', нажатий %s' % b['n']) if 'n' in b else '',
                                                                     h2.get('ru', ''), (' провал → %s' % b['fail']) if b.get('fail') else '', cond)))
            r.bold = True
        if 'set' in b and not any(k in b for k in ('say', 'note', 'text')):
            small(para(), 'флаги: ' + ', '.join('%s %+d' % kv for kv in b['set'].items()) + cond, GRAY, 8)
        if b.get('go') or els:
            rr = para().add_run(('→ %s' % b['go'] if b.get('go') else '') + (cond if not any(k in b for k in ('say', 'note', 'text', 'qte')) else '') + els)
            rr.font.color.rgb = BLUE
        if b.get('end'):
            nx = A.chapter_next(chapters, int(b['end']))
            para().add_run('КОНЕЦ ГЛАВЫ %s%s' % (b['end'], (' → %s' % nx) if nx else ' · КОНЕЦ ИГРЫ')).bold = True


def main():
    args = [a for a in sys.argv[1:]]
    old = None
    if '--old' in args:
        old = args[args.index('--old') + 1]
        del args[args.index('--old'):args.index('--old') + 2]
    out = args[0] if args else os.path.join(A.ROOT, 'out/FINAL_SCENARIO.docx')
    olds = old_set(old)
    data, chapters, owner, _ = A.load()
    doc = Document()
    st = doc.styles['Normal']
    st.font.name = 'Arial'
    st.font.size = Pt(10.5)
    for s in doc.sections:
        s.left_margin = s.right_margin = Cm(2.0)
    doc.add_heading('WHAT THE LAKE KEEPS', 0)
    doc.add_paragraph('Сценарий интерактивного фильма — версия для редактора (собрано %s).'
                      % datetime.date.today().strftime('%d.%m.%Y'))
    doc.add_paragraph('Собрано из данных игры (godot/lake/data/ch1–ch5.json): это ровно то, что идёт в игре. '
                      'Реплика — имя и текст по-русски, ниже английский. [ЕСЛИ …] — условие показа (флаги). '
                      'ВЫБОР — варианты с таймером и сценой, куда ведут; [молчание] — что будет, если не выбрать. '
                      'ДЕЙСТВИЕ — что жмёт игрок; «провал →» — куда при неудаче. ПРОГУЛКА — Ной ходит сам, точки осмотра '
                      '(• название) — в любом порядке, под каждой — что там происходит; РАЗГОВОР — темы на выбор.'
                      + (' Жёлтым — новое или изменённое по сравнению с прошлой версией, по-русски или по-английски '
                         '(подробно — godot/lake/docs/CHANGELOG.md, DIALOGUE_REVISIONS.md, LOCALIZATION_AUDIT.md).' if olds else ''))
    cur = 0
    cnt = {'lines': 0}
    for sid, sc in data['scenes'].items():
        ch = owner[sid]
        if ch != cur:
            cur = ch
            t = chapters[ch].get('title') or {}
            doc.add_page_break()
            name = ((t.get('ru') if isinstance(t, dict) else '') or '').split('·')[-1].strip() or CH_NAME.get(ch, '')
            doc.add_heading('Глава %d · %s' % (ch, name[:1].upper() + name[1:].lower()), 1)   # «ГЛАВА 2 · ПОПУТЧИЦА» → «Глава 2 · Попутчица»
        doc.add_heading(sid + ('  ·  %s' % sc['time'] if sc.get('time') else '') + ('  ·  сохранение' if sc.get('save') else ''), 3)
        render(doc, sid, sc['beats'], data, chapters, olds, 0, cnt)
    os.makedirs(os.path.dirname(out), exist_ok=True)
    doc.save(out)
    print('сценарий: %s (%d сцен, %d реплик, из них в прогулках и разговорах с темами — %d)' % (out, len(data['scenes']), cnt['lines'], cnt['nested']))


if __name__ == '__main__':
    main()
