// «Hush Till Six» — монтаж трейлеров. node tools/trailer/edl.js yt|tt ru|en > timeline.json
// Кадры — только из движка игры (World.draw), голоса — snd/*.mp3 и snd/en/*.mp3, музыка — tools/trailer/audio.py (код).
// Сегмент: { shot, t0 — секунда анимации кадра в начале сегмента, cam [cx, cy, z0, z1] (доли кадра 9:16, как cams в story.js),
//   cctv — подпись камеры (HUD), fx — static|dark|rain|flicker|lamp, props, sh — props странностей смены (камеры cam01…),
//   vo — реплики [ключ, секунда от начала сегмента], sfx — [звук, секунда, {gain, rate, cut, lp, pan}], card — надписи поверх }
const fs = require('fs'), path = require('path');
const ROOT = path.join(__dirname, '../../public/games/nightshift');
const TIM = { ru: JSON.parse(fs.readFileSync(path.join(ROOT, 'timing.json'))), en: JSON.parse(fs.readFileSync(path.join(ROOT, 'timing_en.json'))) };
global.window = global; require(path.join(ROOT, 'story.js')); const STORY = global.STORY;
const FPS = 24;

const RATE = { fake: .95, phonefake: .96 };   // «чужой» голос в игре играет чуть медленнее (engine.js voiceFx)
const fxOf = (k) => (TIM.ru[k] || {}).fx || '';
const vdur = (k, L) => { const m = TIM[L][k] || TIM.ru[k]; if (!m) throw new Error('нет реплики ' + k); return m.dur / (RATE[fxOf(k)] || 1); };
const beat = (ref) => { const [s, i] = ref.split(':'), sc = STORY.scenes[s]; if (!sc) throw new Error('нет сцены ' + s); const b = sc.beats[+i]; if (!b) throw new Error('нет кадра ' + ref); return { b, props: Object.assign({}, sc.props || {}, b.props || {}) }; };
// секунда слова в реплике: первое слово, подходящее под рег. выражение (после n-го совпадения)
const wordAt = (k, L, re, n = 0) => { const w = (TIM[L][k] || {}).w || []; let c = 0; for (const [t, s] of w) if (re.test(s)) { if (c++ === n) return t / (RATE[fxOf(k)] || 1); } throw new Error(`слово ${re} не найдено в ${k}/${L}`); };

const T = (ru, en) => ({ ru, en });   // текст на двух языках
// финалы для вспышек «14 способов умереть»: кадр, секунда, камера наблюдения?, кадрирование 16:9
const DEATHS = [['hush_face', 1.2], ['k4_owl', 3.2, 1, [.46, .6, 1.4, 1.5]], ['stare_lens', 3.55, 1], ['fmom_turn', 2.2], ['k2_last', 1.6], ['k1_face', 2.4, 0, [.48, .4, 1.35, 1.45]], ['corridor_monster', 1.5], ['k8_ceiling', 3.1]];
const RULE2 = { shot: 'entrance_cam', t0: 1.5, cam: [.5, .45, 1.15, 1.25], camL: [.5, .42, 1.25, 1.4], cctv: T('CAM 01  ВХОД', 'CAM 01  ENTRANCE'), clock: '03:31' };   // «никому не открывай»: Лили под дождём у входа, камера наблюдения

// ------- монтаж -------
// правила: голос сменщика по телефону режется по словам — между правилами паузы, на каждое правило свой кадр и надпись
function rules(S, L, P) {
  const k = 'dg08', re = L === 'ru' ? [/^Сиди$/, /^дверь$/, /^на$/] : [/^Sit$/, /^don't$/, /^don't$/], n = L === 'ru' ? [0, 0, 0] : [0, 1, 2];
  const r = re.map((x, i) => wordAt(k, L, x, n[i]) - .06), end = vdur(k, L), hold = P.hold;
  S.push({ id: 'call', shot: 'booth_call', from: 'dg_it:1', t0: 3, cam: P.callCam, vo: [[k, P.lead, { to: r[0] }]], tail: .25 });
  S.push({ id: 'rule1', shot: 'monitors', from: 'journal:10', t0: 1, cam: [.5, .5, 1.0, 1.06], fx: 'flicker', vo: [[k, .15, { from: r[0], to: r[1] }]], tail: hold, card: { kind: 'rule', n: 1, at: 0, lines: [T('СИДИ ДО ШЕСТИ', 'SIT TIGHT TILL SIX')] }, nosub: 1,
    sfx: [['tick', .05, { gain: .35 }]] });
  S.push(Object.assign({ id: 'rule2', vo: [[k, .15, { from: r[1], to: r[2] }]], tail: hold, card: { kind: 'rule', n: 2, at: 0, lines: [T('НИКОМУ НЕ ОТКРЫВАЙ', "DON'T OPEN THE DOOR")] }, nosub: 1 }, P.rule2));
  S.push({ id: 'rule3', shot: 'c13_back', from: 'cam13:1', t0: 1, cam: [.47, .42, 1.9, 2.2], cctv: T('CAM 13', 'CAM 13'), clock: '03:13', vo: [[k, .15, { from: r[2] }]], tail: hold + .15, card: { kind: 'rule', n: 3, at: 0, lines: [T('НЕ ОТЗЫВАЙСЯ НА ИМЯ', "DON'T ANSWER TO YOUR NAME")] }, nosub: 1,
    sfx: [['static', .15 + end - r[2] + hold - .25, { gain: .5, cut: .4 }]] });
}
// YouTube 16:9, ~90 с: тихая завязка → правила → всё идёт не так → смерти → тишина и бросок → название → «Это я» → где играть
function youtube(L) {
  const S = [];
  S.push({ id: 'open', black: 1.4, sfx: [['rain', 0, { gain: .3, fadeIn: 1.4, cut: 11 }]] });
  S.push({ id: 'ext', shot: 'pro_ext', from: 'intro:2', t0: 0, cam: [.5, .45, 1.0, 1.1], fx: 'rain', vo: [['p01', .35]], tail: .7, fadeIn: .8 });
  S.push({ id: 'hall', shot: 'pro_hall', from: 'intro:0', t0: 1, cam: [.5, .5, 1.02, 1.1], cctv: T('CAM 02  ХОЛЛ', 'CAM 02  LOBBY'), clock: '22:04', dur: 3.4,
    card: { kind: 'place', at: .3, dur: 2.9, lines: [T('БИЗНЕС-ЦЕНТР «ГОРИЗОНТ»', 'HORIZON BUSINESS CENTER'), T('НОЧНАЯ СМЕНА · 22:00 — 06:00', 'NIGHT SHIFT · 10 PM — 6 AM')] } });
  S.push({ id: 'guard', shot: 'pro_two', from: 'intro:5', t0: 6, cam: [.48, .45, 1.2, 1.3], vo: [['p09', .2]], tail: .3 });
  S.push({ id: 'booth', shot: 'fb_view', from: 'booth:0', t0: 2, cam: [.5, .5, 1.0, 1.1], dur: 2.6, sfx: [['tick', .2, { gain: .35 }], ['tick', 1.2, { gain: .35 }], ['tick', 2.2, { gain: .35 }]] });
  rules(S, L, { callCam: [.5, .48, 1.1, 1.16], lead: .25, hold: .55, rule2: RULE2 });
  // всё идёт не так: музыка набирает, склейки чаще
  S.push({ id: 'lift', shot: 'elevator_13', from: 'lift:1', t0: 0, cam: [.5, .32, 1.25, 1.55], camL: [.5, .46, 1.15, 1.85], vo: [['e02', .2]], tail: .35, sfx: [['ding', .05, { gain: .3 }]], mark: 'build' });
  S.push({ id: 'gus', shot: 'f7_radio', from: 'floor7:3', t0: 3.2, cam: [.5, .5, 1.04, 1.12], camL: [.5, .4, 1.0, 1.06], vo: [['f05', .15]], tail: .25, sfx: [['static', 0, { gain: .2, cut: .35 }]] });
  S.push({ id: 'gone', shot: 'f7_cam_after', from: 'floor7:5', t0: 1, cam: [.5, .5, 1.0, 1.06], cctv: T('CAM 07  ЭТАЖ 7', 'CAM 07  FLOOR 7'), clock: '03:00', fx: 'static', dur: 1.7, sfx: [['static', 0, { gain: .6, cut: .9 }]] });
  S.push({ id: 'lily', shot: 'gl_face', from: 'glass2:1', t0: 1, cam: [.5, .47, 1.06, 1.16], fx: 'rain', vo: [['g05', .15]], tail: .3, sfx: [['glass_hit', .3, { gain: .3 }], ['glass_hit', 1.05, { gain: .28 }]] });
  S.push({ id: 'lilycam', shot: 'entrance_empty', from: 'glass2:4', t0: 1, cam: [.5, .5, 1.0, 1.08], cctv: T('CAM 01  ВХОД', 'CAM 01  ENTRANCE'), clock: '03:31', fx: 'static', vo: [['g08', .1]], tail: .2 });
  S.push({ id: 'frank', shot: 'srv_in', from: 'knock3:5', t0: 2, cam: [.52, .45, 1.2, 1.28], camL: [.58, .45, 1.45, 1.55], vo: [['n34', .1]], tail: .25 });
  S.push({ id: 'face', shot: 'c13_face', from: 'over_stare:0', t0: 2.5, cam: [[.45, 1.5, -3.3], -.06, 1.5, 1.9], cctv: T('CAM', 'CAM'), clock: '04:44', fx: 'static', dur: 1.3, sfx: [['static', 0, { gain: .7, cut: 1.3 }], ['neck_crack', .55, { gain: .6 }]] });
  S.push({ id: 'mom', shot: 'srv_dark', from: 'knock3:6', t0: 1.5, cam: [.5, .5, 1.04, 1.14], vo: [['d18', .1]], tail: .2 });
  S.push({ id: 'hide', shot: 'h_crouch', from: 'hide:2', t0: 2, cam: [.5, .5, 1.02, 1.08], vo: [['n42', .55, { gain: 1.3 }]], tail: 1.3, sfx: [['steps_barefoot', 0, { gain: .2, cut: .55 }], ['breath_close', 2.3, { gain: .2 }]] });
  // смерти: надпись и вспышки финалов
  const D = DEATHS;
  S.push({ id: 'ends', black: 1.05, card: { kind: 'big', at: .1, dur: .9, lines: [T('4 КОНЦОВКИ', '4 ENDINGS')] }, sfx: [['boom', .05, { gain: .7 }]], mark: 'deaths' });
  D.forEach(([shot, t0, cc, camL], i) => S.push({ id: 'd' + i, shot, t0, cam: [.5, .47, 1.05, 1.12], camL, cctv: cc ? T('CAM ' + (shot === 'stare_lens' ? '07' : '13'), 'CAM ' + (shot === 'stare_lens' ? '07' : '13')) : null, clock: '04:1' + i, dur: .42, flash: .5, sfx: [[['glass_slam', 'neck_crack', 'boom', 'skin_stretch'][i % 4], 0, { gain: .55 }]] }));
  S.push({ id: 'ways', black: 1.4, card: { kind: 'big', at: 0, dur: 1.35, lines: [T('14 СПОСОБОВ УМЕРЕТЬ', '14 WAYS TO DIE')] }, sfx: [['boom', 0, { gain: .8 }], ['tinnitus', .1, { gain: .25, cut: 1.3 }]] });
  // тишина: улыбка, шёпот, бросок
  S.push({ id: 'smile', shot: 'b_smile', from: 'branch_b:2', t0: .4, cam: [.5, .47, 1.0, 1.2], fx: 'rain', vo: [['b02', .3]], tail: .35, sfx: [['skin_stretch', 1.9, { gain: .45 }], ['drone_dread', 0, { gain: .3 }]], mark: 'silence' });
  S.push({ id: 'gap', black: .55 });
  S.push({ id: 'there', shot: 'h_face_open', from: 'over_k5:0', t0: 1, cam: [.5, .5, 1.1, 1.2], fx: 'dark', vo: [['k5a', .2]], tail: .15 });
  S.push({ id: 'lunge', shot: 'lunge_booth', t0: .55, cam: [.5, .5, 1, 1], scareAt: .75, dur: 1.6, sfx: [['jumpscare', .75, { gain: 1 }], ['scare', .75, { gain: .8 }]], mark: 'scare' });
  S.push({ id: 'cut', black: .9, sfx: [['tinnitus', 0, { gain: .3, cut: 2.5 }]] });
  // название
  S.push({ id: 'title', black: 4.2, card: { kind: 'logo', at: .1, dur: 4.1, tag: T('Не отвечай. Не открывай. Дождись шести.', "Don't answer. Don't open. Wait for six.") }, sfx: [['boom', .1, { gain: .9 }], ['sub_presence', .1, { gain: .5 }]], mark: 'title' });
  // «Это я»: звонок в темноте, отвечает его голос — не его
  S.push({ id: 'ring', black: 2.5, sfx: [['buzz', .1, { gain: .45 }], ['buzz', 1.1, { gain: .45 }]], mark: 'stinger' });
  S.push({ id: 'call2', black: 0, vo: [['n20', 0]], tail: .9, card: { kind: 'phone', at: 0, name: T('Лили', 'Lily') } });
  S.push({ id: 'me', black: 0, vo: [['n23m', 0]], tail: .35, card: { kind: 'phone', at: 0, name: T('Лили', 'Lily') } });
  S.push({ id: 'dbl', shot: 'dbl_cam', from: 'over_phone:2', t0: 2.2, cam: [.72, .55, 2.3, 2.6], cctv: T('CAM 02  ХОЛЛ', 'CAM 02  LOBBY'), clock: '05:59', dur: 1.5, sfx: [['phone_crackle', 0, { gain: .5 }], ['static', 1.1, { gain: .7, cut: .4 }]] });
  // где играть
  S.push({ id: 'end', black: 6.0, card: { kind: 'end', at: 0, dur: 6.0 }, sfx: [['knock', 3.6, { gain: .7 }], ['knock', 4.05, { gain: .7 }], ['knock', 4.5, { gain: .7 }]], mark: 'end' });
  return S;
}

// TikTok 9:16, ~40 с: крючок сразу — «Итан, открой!» и правило; дальше быстрее
function tiktok(L) {
  const S = [];
  S.push({ id: 'lily', shot: 'gl_face', from: 'glass2:1', t0: 1, cam: [.5, .5, 1.1, 1.2], fx: 'rain', vo: [['g05', .1]], tail: .25, card: { kind: 'hook', at: 0, dur: 99, lines: [T('ЕСЛИ НОЧЬЮ ТЕБЯ ЗОВУТ ПО ИМЕНИ…', 'IF SOMEONE CALLS YOUR NAME AT NIGHT…')] },
    sfx: [['rain', 0, { gain: .3, cut: 7.5 }], ['glass_hit', .25, { gain: .3 }], ['glass_hit', .95, { gain: .28 }]] });
  S.push({ id: 'far', shot: 'gl_wide', from: 'glass2:2', t0: 1, cam: [.5, .5, 1.04, 1.1], fx: 'rain', vo: [['g06', .05]], tail: .25, card: { kind: 'hook', at: 0, dur: 99, lines: [T('ЕСЛИ НОЧЬЮ ТЕБЯ ЗОВУТ ПО ИМЕНИ…', 'IF SOMEONE CALLS YOUR NAME AT NIGHT…'), T('НЕ ОТЗЫВАЙСЯ.', "DON'T ANSWER.")] } });
  rules(S, L, { callCam: [.5, .48, 1.12, 1.18], lead: .15, hold: .45, rule2: RULE2 });
  S.push({ id: 'gus', shot: 'f7_radio', from: 'floor7:3', t0: 3.2, cam: [.5, .42, 1.0, 1.06], vo: [['f05', .1]], tail: .2, sfx: [['static', 0, { gain: .2, cut: .35 }]], mark: 'build' });
  S.push({ id: 'gone', shot: 'f7_cam_after', from: 'floor7:5', t0: 1, cam: [.5, .5, 1.0, 1.06], cctv: T('CAM 07  ЭТАЖ 7', 'CAM 07  FLOOR 7'), clock: '03:00', fx: 'static', dur: 1.1, sfx: [['static', 0, { gain: .6, cut: .7 }]] });
  S.push({ id: 'frank', shot: 'srv_in', from: 'knock3:5', t0: 2, cam: [.58, .45, 1.45, 1.55], vo: [['n34', .05]], tail: .2 });
  S.push({ id: 'face', shot: 'c13_face', from: 'over_stare:0', t0: 2.5, cam: [[.45, 1.5, -3.3], -.06, 1.5, 1.9], cctv: T('CAM', 'CAM'), clock: '04:44', fx: 'static', dur: 1.0, sfx: [['static', 0, { gain: .7, cut: 1 }], ['neck_crack', .45, { gain: .6 }]] });
  S.push({ id: 'hide', shot: 'h_crouch', from: 'hide:2', t0: 2, cam: [.5, .5, 1.04, 1.1], vo: [['n42', .4, { gain: 1.3 }]], tail: .9, sfx: [['steps_barefoot', 0, { gain: .2, cut: .45 }], ['breath_close', 1.9, { gain: .2 }]] });
  const D = DEATHS.slice(0, 6);
  S.push({ id: 'ends', black: .5, sfx: [['boom', .02, { gain: .7 }]], mark: 'deaths' });
  D.forEach(([shot, t0, cc, camL], i) => S.push({ id: 'd' + i, shot, t0, cam: [.5, .47, 1.05, 1.12], camL, cctv: cc ? T('CAM ' + (shot === 'stare_lens' ? '07' : '13'), 'CAM ' + (shot === 'stare_lens' ? '07' : '13')) : null, clock: '04:1' + i, dur: .4, flash: .5,
    card: i === 0 ? { kind: 'big', at: 0, dur: 2.4, lines: [T('14 СПОСОБОВ УМЕРЕТЬ', '14 WAYS TO DIE')], keep: 1 } : null, sfx: [[['glass_slam', 'neck_crack', 'boom', 'skin_stretch'][i % 4], 0, { gain: .55 }]] }));
  S.push({ id: 'smile', shot: 'b_smile', from: 'branch_b:2', t0: .4, cam: [.5, .47, 1.0, 1.22], fx: 'rain', vo: [['b02', .25]], tail: .3, sfx: [['skin_stretch', 1.8, { gain: .45 }], ['drone_dread', 0, { gain: .3 }]], mark: 'silence' });
  S.push({ id: 'gap', black: .4 });
  S.push({ id: 'there', shot: 'h_face_open', from: 'over_k5:0', t0: 1, cam: [.5, .5, 1.1, 1.2], fx: 'dark', vo: [['k5a', .15]], tail: .1 });
  S.push({ id: 'lunge', shot: 'lunge_booth', t0: .55, cam: [.5, .5, 1, 1], scareAt: .75, dur: 1.6, sfx: [['jumpscare', .75, { gain: 1 }], ['scare', .75, { gain: .8 }]], mark: 'scare' });
  S.push({ id: 'cut', black: .5, sfx: [['tinnitus', 0, { gain: .3, cut: 2 }]] });
  S.push({ id: 'end', black: 5.6, card: { kind: 'end', at: 0, dur: 5.6, logo: 1 }, sfx: [['boom', 0, { gain: .9 }], ['knock', 3.7, { gain: .7 }], ['knock', 4.15, { gain: .7 }], ['knock', 4.6, { gain: .7 }]], mark: 'end' });
  return S;
}

// ------- сборка: время каждого сегмента, реплики, субтитры, надписи -------
function build(fmt, L) {
  const segs = (fmt === 'yt' ? youtube : tiktok)(L), out = { fmt, lang: L, fps: FPS, W: fmt === 'yt' ? 1920 : 1080, H: fmt === 'yt' ? 1080 : 1920, segs: [], vo: [], sfx: [], subs: [], cards: [], marks: {} };
  let t = 0;
  for (const s of segs) {
    const o = Object.assign({}, s), src = s.from ? beat(s.from) : null;
    if (src) { o.props = Object.assign({}, src.props, s.props || {}); if (!o.fx && src.b.fx && src.b.fx !== 'cctv') o.fx = src.b.fx; }
    if (s.sh) o.props = Object.assign({}, o.props || {}, { sh: s.sh });
    let dur = s.dur ?? s.black ?? 0;
    const cut = (k, o = {}) => ((o.to ?? TIM[L][k].dur) - (o.from || 0)) / (RATE[fxOf(k)] || 1);   // длина куска реплики
    for (const [k, at, vo] of s.vo || []) dur = Math.max(dur, s.dur != null ? 0 : at + cut(k, vo) + (s.tail || 0));
    if (s.dur != null) dur = s.dur;
    o.start = +t.toFixed(4); o.dur = +dur.toFixed(4); delete o.from; delete o.vo; delete o.card; delete o.sfx;
    o.n = Math.round((t + dur) * FPS) - Math.round(t * FPS); o.f0 = Math.round(t * FPS);   // кадры сегмента: [f0, f0 + n)
    if (o.cctv) o.cctv = o.cctv[L];
    for (const [k, at, opt] of s.vo || []) {
      const m = TIM[L][k] || TIM.ru[k], fx = fxOf(k), who = (TIM.ru[k] || {}).who || 'ИТАН';
      const o2 = opt || {}, from = o2.from || 0;
      out.vo.push({ k, at: +(t + at).toFixed(4), file: (L === 'en' ? 'snd/en/' : 'snd/') + k + '.mp3', fx, rate: RATE[fx] || 1, g: m.g || 1, dur: cut(k, o2), from, to: o2.to ?? null, who, aloud: who !== 'ИТАН' || (m.t || '').trim().startsWith('—'), seg: s.id, ...o2 });
      if (!s.nosub) out.subs.push(...subsOf(k, L, t + at - from / (RATE[fx] || 1), o2.to != null ? o2.to / (RATE[fx] || 1) : null).filter(x => x.end > t + at + .05));
    }
    for (const [n, at, opt] of s.sfx || []) out.sfx.push({ n, at: +(t + at).toFixed(4), ...(opt || {}) });
    if (s.card) { const c = Object.assign({}, s.card); c.at = +(t + (c.at || 0)).toFixed(4); if (c.dur == null || c.dur > 90) c.dur = dur - (s.card.at || 0); if (c.lines) c.lines = c.lines.map(x => x[L]); if (c.tag) c.tag = c.tag[L]; if (c.name) c.name = c.name[L]; c.seg = s.id; out.cards.push(c); }
    if (s.mark) out.marks[s.mark] = +t.toFixed(4);
    out.segs.push(o); t += dur;
  }
  // крючок TikTok: одна надпись на два сегмента — склеиваем, чтобы не мигала
  out.cards = out.cards.reduce((a, c) => { const p = a[a.length - 1]; if (p && p.kind === 'hook' && c.kind === 'hook') { p.dur = c.at + c.dur - p.at; p.lines2 = c.lines; p.at2 = c.at; return a; } a.push(c); return a; }, []);
  out.dur = +t.toFixed(4); out.frames = Math.round(t * FPS);
  return out;
}

// субтитры реплики: по предложениям, время — по словам из timing.json
function subsOf(k, L, t0, until) {
  const m = TIM[L][k] || TIM.ru[k], rate = RATE[fxOf(k)] || 1, w = m.w || [], text = m.t.replace(/^\s*—\s*/, '').trim();
  const parts = text.match(/[^.!?…]+(?:[.!?…]+["»”]?|$)/g).map(s => s.trim()).filter(Boolean);
  const chunks = []; for (const p of parts) { const prev = chunks[chunks.length - 1]; if (prev && (prev.length + p.length < 44 || p.length < 12)) chunks[chunks.length - 1] = prev + ' ' + p; else chunks.push(p); }
  const words = s => s.split(/\s+/).filter(x => /[\p{L}\p{N}]/u.test(x)).length;
  let wi = 0; const res = [];
  chunks.forEach((c, i) => { const st = w[wi] ? w[wi][0] / rate : (m.dur / rate) * i / chunks.length; wi += words(c); res.push({ at: +(t0 + Math.max(0, st - .05)).toFixed(4), text: c }); });
  const end = t0 + m.dur / rate + .25;
  res.forEach((r, i) => { r.end = +(i + 1 < res.length ? res[i + 1].at : end).toFixed(4); if (until != null) r.end = Math.min(r.end, +(t0 + until + .3).toFixed(4)); r.dur = +(r.end - r.at).toFixed(4); });
  for (let i = res.length - 1; i > 0; i--) if (res[i].dur < .95 && res[i - 1].text.length + res[i].text.length < 90) { res[i - 1].text += ' ' + res[i].text; res[i - 1].end = res[i].end; res[i - 1].dur = +(res[i - 1].end - res[i - 1].at).toFixed(4); res.splice(i, 1); }   // короткий хвост — к предыдущей строке
  return res.filter(r => r.dur > .25 && (until == null || r.at < t0 + until - .1));
}

module.exports = { build, FPS };
if (require.main === module) { const [fmt = 'yt', L = 'ru'] = process.argv.slice(2); process.stdout.write(JSON.stringify(build(fmt, L), null, 1)); }
