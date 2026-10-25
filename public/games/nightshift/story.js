/* «Молчи до шести» — сценарий интерактивного кино (v3, 22 окт). Реплики: tools/story/lines.py → snd/*.mp3 + timing.json.
   Сцена = список кадров (beat) + что дальше (then).
   beat: img — кадр 3D-мира (scenes.js), cams — планы [cx, cy, зум от, зум до] (по очереди, как монтажные склейки),
         fx — 'cctv' | 'static' | 'rain' | 'flicker' | 'lamp' | 'dark' | 'dawn' | 'lunge', label — надпись камеры,
         say — реплики (ключи lines.py), text — текст без голоса, sfx — [[звук, секунда, {…}]], pad — пауза после,
         phone — экран телефона ({ kind: 'lock' | 'call' | 'out' | 'talk', … }), rule — правило на экране, time — часы.
   then: goto | choice | camgame | mg ('hold' | 'run' | 'cord' | 'wipe' | 'torch' | 'tape' | 'door') | ending | over.
   Правила «оно» (lines.py): зовёт по имени голосами близких; не подойдёт, пока сам не позовёшь (отзовёшься, откроешь, пойдёшь на зов);
   к отозвавшемуся приходит сквозь любые двери — спасает только круг соли; ищет на звук; кто увидел лицо — ответит; никогда не стучит;
   в шесть засыпает. Настоящие камеры показывают его только помехами; тринадцатая — его. */
const L1 = 'НЕ ОТЗЫВАЙСЯ НА ИМЯ.\nНЕ ОТКРЫВАЙ ЕМУ ДВЕРЬ.\nСАМ К НЕМУ НЕ ХОДИ —\nНИ НА СВЕТ, НИ В ЛИФТ.';
window.STORY = {
  start: 'intro',
  scenes: {
    /* ================= ПРОЛОГ, 21:00 ================= */
    intro: {
      music: 'drone', time: '21:00',
      beats: [
        { img: 'pro_hall', fx: 'cctv', label: 'CAM 02  ХОЛЛ', cams: [[.5, .5, 1.0, 1.05]], say: ['p01'], pad: .6,
          chapter: ['HUSH TILL SIX', 'БЦ «ГОРИЗОНТ» · 21:00'], wait: 'Нажми, чтобы заступить на смену' },
        { img: 'pro_hall', fx: 'cctv', label: 'CAM 02  ХОЛЛ', cams: [[.47, .5, 1.3, 1.4]], say: ['p02'], pad: .4 },
        { img: 'pro_ext', fx: 'rain', cams: [[.5, .45, 1.0, 1.08]], say: ['p03', 'p04'], sfx: [['steps', 1.0, { gain: .4 }], ['door', 9.6, { gain: .5 }]], pad: .6 },
        { img: null, phone: { kind: 'lock', time: '21:03', notes: [{ from: 'Лили', text: 'Удачи на первой смене! Не засни там :)', at: 1.4 }] }, say: ['p05'], sfx: [['buzz', 1.4]], pad: .5 },
        { img: 'pro_enter', cams: [[.5, .5, 1.0, 1.06]], say: ['p06'], pad: .3 },
        { img: 'pro_two', cams: [[.5, .5, 1.0, 1.05], [.48, .45, 1.25, 1.3]], say: ['p07', 'p08', 'p09'], pad: .3 },
        { img: 'pro_offer', cams: [[.5, .5, 1.05, 1.12]], pad: 1.4 }
      ],
      then: { choice: 'Сменщик протягивает связку. Рука дрожит.', timeout: 12, def: 0,
              opts: [{ t: 'Взять ключи', go: 'keys_take' }, { t: '«Как это — не сдал?»', go: 'keys_ask' }] }
    },
    keys_ask: {
      music: 'drone', time: '21:04',
      beats: [{ img: 'pro_offer', cams: [[.5, .5, 1.1, 1.15], [.48, .44, 1.3, 1.36]], say: ['p10', 'p11', 'p12'], set: 'asked', pad: .4, react: { 'СМЕНЩИК': ['no', ['p12', '^(камеры|cameras|камери|cámaras|câmeras|kameras)$', .6], 2.4] } }],   // «камеры ничего не записали» — качает головой (от слова: в английской озвучке оно на 2,5 с позже)
      then: { goto: 'keys_take' }
    },
    keys_take: {
      music: 'drone', time: '21:05',
      beats: [
        { img: 'pro_take', cams: [[.5, .5, 1.05, 1.1]], sfx: [['keys', .9, { gain: .7 }]], pad: 2.0 },
        { img: 'pro_palm', cams: [[.5, .5, 1.0, 1.06]], say: ['p13'], pad: .3 },
        { img: 'pro_point', cams: [[.5, .5, 1.0, 1.05]], say: ['p14'], pad: .4 },
        { img: 'pro_knock', cams: [[.5, .5, 1.0, 1.04]], say: ['p15'], sfx: [['knock', .55, { gain: .6 }], ['knock', .82, { gain: .6 }], ['knock', 1.09, { gain: .6 }]], pad: .6 }
      ],
      then: { goto: 'booth' }
    },

    /* ================= БУДКА ФРЭНКА, 21:06 — осмотр ================= */
    // explore: точки на предметах кадра; каждая — короткая сцена и обратно (then.back). Что нашёл — флаги: дальше откроются вопросы и сцены
    booth: {
      music: 'drone', time: '21:06',
      beats: [{ img: 'fb_view', cams: [[.5, .5, 1.0, 1.05]], say: ['fb01'], if: 'asked', pad: .4, chapter: ['21:06', 'БУДКА ФРЭНКА'] },   // «всё, как сказал сменщик» — если спрашивал
        { img: 'fb_view', cams: [[.5, .5, 1.0, 1.05]], say: ['fb00'], if: '!asked', pad: .4, chapter: ['21:06', 'БУДКА ФРЭНКА'] }],
      then: { explore: { img: 'fb_view', cams: [[.5, .54, 1.05, 1.07]], hint: 'Осмотри будку. Нажми на то, что хочешь проверить.',
        spots: [{ t: 'Куртка Фрэнка', go: 'fb_jacket', at: [6.33, .92, -.74], set: 'salt' }, { t: 'Фото под стеклом', go: 'fb_photo', at: [4.78, .81, -.33], set: 'photo' },
          { t: 'Ящик стола', go: 'fb_drawer', at: [4.78, .7, -.47], set: 'fphone' }, { t: 'Журнал', go: 'fb_log', at: [5.35, .84, -.27] }],
        leave: { t: 'Сесть за мониторы', go: 'fb_go' } } }
    },
    fb_jacket: { music: 'drone', beats: [{ img: 'fb_jacket', cams: [[.5, .5, 1.0, 1.06], [.5, .5, 1.0, 1.05, 'fb_salt']], cutAt: ['^(горсть|handful|жменя|puñado|punhado|handvoll)'], say: ['fb02'], pad: .5 }], then: { back: 'booth' } },
    fb_photo: { music: 'drone', beats: [{ img: 'ny_photo', fx: 'photo', photo: { smudge: [.55, 1.5, -7.6], back: 'Марта.\nСедьмой этаж.\nНовый год', backCam: 2 },
      cams: [[.5, .5, 1.0, 1.03], [[.55, 1.5, -7.6], 0, 2.6, 2.8], [.5, .5, 1.0, 1.02]], cutAt: ['^(лицо|face|обличчя|cara|rosto|gesicht)', '^(оборот|back|зворот|detrás|verso|rückseite)'], say: ['fb03'], pad: .8 }], then: { back: 'booth' } },
    fb_drawer: { music: 'drone', beats: [{ img: 'fb_phone', cams: [[.5, .5, 1.0, 1.06]], say: ['fb04'], sfx: [['click', .2, { gain: .5 }], ['buzz', 4.4, { gain: .5 }]], pad: .5 }], then: { back: 'booth' } },
    fb_log: { music: 'drone', beats: [{ img: 'fb_log', cams: [[.5, .5, 1.0, 1.05]], say: ['fb05'], pad: .5 }], then: { back: 'booth' } },
    fb_go: { music: 'drone', beats: [], then: { shift: 'S1', fresh: true } },

    /* ================= ЗВОНОК ДНЕВНОГО, 22:00 — разговор ================= */
    // talk: темы по номерам; тема ведёт в свою сцену и обратно. Новые вопросы открываются по ответам (if: 'v:сцена')
    dg_call: {
      music: 'drone', time: '22:00',
      beats: [
        { img: null, phone: { kind: 'call', name: '+1 (914) 207-3318' }, say: ['dg01'], sfx: [['ring_mob', 0], ['buzz', 0]], pad: .4, chapter: ['22:00', 'ЗВОНОК'] },
        { img: 'booth_call', cams: [[.5, .5, 1.0, 1.05]], say: ['dg02'], pad: .3 }
      ],
      then: { talk: { img: 'booth_call', cams: [[.5, .5, 1.05, 1.08]], title: 'Дневной охранник', topics: [
        { t: 'Что на самом деле случилось с Фрэнком?', go: 'dg_frank' }, { t: 'Зачем ты стучал в стекло?', go: 'dg_knock' },
        { t: '«Оно» — это кто?', go: 'dg_it', if: 'v:dg_knock' }, { t: 'В здании ещё кто-нибудь есть?', go: 'dg_gus' }],
        bye: { t: '«Ладно. До утра»', go: 'dg_bye' } } }
    },
    dg_frank: { music: 'drone', beats: [{ img: 'booth_call', cams: [[.5, .5, 1.05, 1.1]], say: ['dg03'], pad: .2 }, { img: 'booth_call', cams: [[.5, .48, 1.1, 1.16]], say: ['dg04'], pad: .4 }], then: { back: 'dg_call' } },
    dg_knock: { music: 'drone', beats: [{ img: 'booth_call', cams: [[.5, .5, 1.05, 1.1]], say: ['dg05'], pad: .2 }, { img: 'booth_call', cams: [[.5, .48, 1.1, 1.16]], say: ['dg06'], pad: .4 }], then: { back: 'dg_call' } },
    dg_it: { music: 'drone', beats: [{ img: 'booth_call', cams: [[.5, .5, 1.05, 1.1]], say: ['dg07'], pad: .2 }, { img: 'booth_call', cams: [[.5, .48, 1.1, 1.16]], say: ['dg08'], pad: .4 }], then: { back: 'dg_call' } },
    dg_gus: { music: 'drone', beats: [{ img: 'booth_call', cams: [[.5, .5, 1.05, 1.1]], say: ['dg09'], pad: .2 }, { img: 'booth_call', cams: [[.5, .48, 1.1, 1.16]], say: ['dg10'], pad: .4 }], then: { back: 'dg_call' } },
    dg_bye: { music: 'drone', beats: [{ img: 'booth_call', cams: [[.5, .5, 1.05, 1.1]], say: ['dg11'], pad: .2 }, { img: 'booth_call', cams: [[.5, .48, 1.1, 1.14]], say: ['dg12'], pad: .8 }], then: { goto: 'round1' } },

    /* ================= ЖУРНАЛ, 02:50 ================= */
    journal: {
      music: 'drone', time: '02:50',
      beats: [
        { img: 'jr_desk', fx: 'lamp', cams: [[.5, .5, 1.0, 1.06]], say: ['j01'], set: 'jread', pad: .3, chapter: ['02:50', 'ЖУРНАЛ ФРЭНКА'] },
        { img: 'jr_desk', fx: 'lamp', cams: [[.5, .55, 1.15, 1.22]], say: ['j02'], pad: .3 },
        { img: 'jr_p12', fx: 'lamp', cams: [[.5, .5, 1.0, 1.05]], say: ['j03'], pad: .3 },
        { img: 'jr_p15', fx: 'lamp', cams: [[.5, .5, 1.0, 1.05]], say: ['j04'], pad: .3 },
        { img: 'jr_p18', fx: 'lamp', cams: [[.5, .5, 1.0, 1.05]], say: ['j05'], pad: .3 },
        { img: 'jr_p20', fx: 'lamp', cams: [[.5, .5, 1.0, 1.08]], say: ['j06'], rule: L1, pad: .6 },
        { img: 'jr_p21', fx: 'lamp', cams: [[.5, .5, 1.0, 1.06]], say: ['j07'], rule: 'МАМА ПОЗВАЛА. Я ОТВЕТИЛ.\nСОЛЬ ДЕРЖИТ. ИДУ ВНИЗ.', pad: .5 },
        { img: 'jr_p21', fx: 'lamp', cams: [[.5, .5, 1.06, 1.1]], say: ['al02'], if: 'fpcall', pad: .4 },
        { img: 'jr_p21', fx: 'lamp', cams: [[.5, .5, 1.06, 1.1]], say: ['al03'], if: 'salt', pad: .4 },
        { img: 'jr_desk', fx: 'lamp', cams: [[.5, .5, 1.1, 1.14]], say: ['j08'], pad: .3 },
        { img: 'monitors', fx: 'flicker', cams: [[.5, .5, 1.0, 1.08]], say: ['j09'], sfx: [['fluor_flicker', .4, { gain: .3 }]], pad: .6 }
      ],
      then: { goto: 'floor7' }
    },

    /* ================= СЕДЬМОЙ ЭТАЖ, 02:59 ================= */
    floor7: {
      music: 'drone', time: '02:59',
      beats: [
        { img: 'f7_cam', fx: 'cctv', label: 'CAM 07  ЭТАЖ 7', cams: [[.5, .5, 1.0, 1.05]], sfx: [['mop_swish', .5, { gain: .3 }]], text: '', pad: 2.2 },
        { img: 'f7_close', cams: [[.5, .52, 1.0, 1.08]], sfx: [['mop_swish', .35, { gain: .55 }], ['mop_swish', 1.2, { gain: .5, rate: .92 }], ['mop_swish', 2.1, { gain: .55, rate: 1.05 }]], text: '', pad: 2.6 },
        { img: 'f7_stop', time: '03:00', cams: [[.5, .5, 1.0, 1.1]], say: ['f03'], sfx: [['hum_stop', .2, { gain: .5 }]], pad: .4 },
        { img: 'f7_radio', cams: [[.5, .5, 1.0, 1.08]], say: ['f04', 'f05'], sfx: [['static', 0, { gain: .3, cut: 1.2 }]], pad: .3 },
        { img: 'f7_radio', cams: [[.5, .5, 1.08, 1.12]], say: ['al01'], if: 'warned', pad: .3 },
        { img: 'f7_cam_after', fx: 'cctv', label: 'CAM 07  ЭТАЖ 7', cams: [[.5, .5, 1.0, 1.08]], say: ['f06'], sfx: [['static', 0, { gain: .6, cut: 1.4 }]], heart: true, pad: .6 }
      ],
      then: { mg: 'tape', title: 'ПЕРЕМОТАЙ ЗАПИСЬ', hint: 'Тяни ползунок влево. Найди, где он ещё здесь.', found: '03:00:00. Гас стоит у двери 707…',
              img: 'f7_stop', label: 'CAM 07  ЭТАЖ 7', cam: [.5, .5, 1.25, 1.25], len: 4.6, goal: .8, stop: 3.4, clock0: [3, 0, 0], win: 'floor7b' }
    },
    floor7b: {
      music: 'tension', time: '03:02',
      beats: [
        { img: 'f7_stop', fx: 'cctv', label: 'CAM 07  ЭТАЖ 7', hud: { date: '29.10.2026', at: '03:00', tag: '▶ ЗАПИСЬ' }, cams: [[.5, .5, 1.2, 1.3]], say: ['f07'], pad: .4 },   // «пересмотрел запись»: на экране — запись 03:00, а не живое 03:02
        { img: null, phone: { kind: 'talk', name: 'Полиция', t0: 3 }, say: ['f08'], pad: .4 }
      ],
      then: { choice: 'На седьмом горит свет. Гаса нет.', timeout: 10, def: 1,
              opts: [{ t: 'Подняться на седьмой', go: 'over_k2' }, { t: 'Остаться в будке', go: 'radio' }] }
    },

    /* ================= РАЦИЯ, 03:05 ================= */
    radio: {
      music: 'drone', time: '03:05',
      beats: [
        { img: 'booth_dark', cams: [[.5, .5, 1.0, 1.06]], say: ['r01'], pad: .3 },
        { img: 'f7_cam_after', fx: 'cctv', label: 'CAM 07  ЭТАЖ 7', cams: [[.5, .5, 1.0, 1.06]], say: ['r02'], pad: .5 },
        { img: 'booth_radio', cams: [[.5, .5, 1.1, 1.16]], say: ['r03'], sfx: [['static', 0, { gain: .35, cut: .8 }]], heart: true, pad: .4 }
      ],
      then: { choice: 'Рация зовёт тебя по имени.', timeout: 9, def: 1,
              opts: [{ t: 'Ответить: «Гас, держись!»', go: 'over_k3' }, { t: 'Выключить рацию', go: 'radio_off' }] }
    },
    radio_off: {
      music: 'drone', time: '03:06',
      beats: [{ img: 'booth_radio', cams: [[.5, .5, 1.16, 1.2]], say: ['r04'], sfx: [['click', .1]], pad: .8 }],
      then: { goto: 'lift' }
    },

    /* ================= ЛИФТ, 03:12 ================= */
    lift: {
      music: 'tension', time: '03:12',
      beats: [
        { img: 'elevator_open', cams: [[.5, .5, 1.0, 1.08]], say: ['e01'], sfx: [['ding', .6]], pad: .3 },
        { img: 'elevator_13', cams: [[.5, .3, 1.3, 1.6]], say: ['e02'], heart: true, pad: .4 },
        { img: 'elevator_open', cams: [[.5, .55, 1.2, 1.1]], say: ['e03'], sfx: [['door', 1.2, { gain: .5 }]], pad: .5 }
      ],
      then: { goto: 'cam13' }
    },

    /* ================= ТРИНАДЦАТАЯ, 03:17 ================= */
    cam13: {
      music: 'tension', time: '03:17',
      beats: [
        { img: 'monitors', fx: 'flicker', cams: [[.5, .5, 1.0, 1.1]], say: ['m01'], sfx: [['static', .3, { gain: .4 }]], pad: .3 },
        { img: 'c13_back', fx: 'cctv', label: 'CAM 13', cams: [[.5, .5, 1.0, 1.12]], say: ['m02'], heart: true, pad: .2 }
      ],
      then: { mg: 'cord', title: 'ВЫДЕРНИ ШНУР', hint: 'Оно оборачивается. Тяни вилку вниз!', img: 'c13_turn', cam: [.5, .5, 1.0, 1.6], dur: 3.2, win: 'glass', lose: 'over_k4' }
    },

    /* ================= СТЕКЛО, 03:30 ================= */
    glass: {
      music: 'drone', time: '03:20',
      beats: [
        { img: 'booth_dark', fx: 'dark', cams: [[.5, .5, 1.0, 1.08]], say: ['g01'], sfx: [['click', 0]], pad: .4 },
        { img: 'booth_dark', cams: [[.5, .5, 1.08, 1.14]], say: ['g02'], pad: .5 },
        { img: 'booth_dark', time: '03:30', cams: [[.5, .5, 1.14, 1.2]], say: ['g03'], sfx: [['ch_whisper', 1.6, { bus: 'voice', gain: .5, lp: 900 }]], heart: true, pad: .3 }
      ],
      then: { mg: 'wipe', title: 'КТО ТАМ?', hint: 'Стекло запотело. Протри его пальцем.', img: 'gl_none', slam: 'gl_slam', cam: [.5, .5, 1.0, 1.0], win: 'glass2' }
    },
    glass2: {
      music: 'tension', time: '03:30',
      beats: [
        { img: 'gl_wide', fx: 'rain', cams: [[.5, .5, 1.0, 1.06]], say: ['g04'], pad: .3 },
        { img: 'gl_face', fx: 'rain', cams: [[.5, .5, 1.0, 1.06]], say: ['g05'], pad: .3 },
        { img: 'gl_wide', fx: 'rain', cams: [[.5, .5, 1.06, 1.1]], say: ['g06'], pad: .3 },
        { img: 'gl_face', fx: 'rain', cams: [[.5, .5, 1.06, 1.12]], say: ['g07'], pad: .3 },
        { img: 'entrance_empty', fx: 'static', label: 'CAM 01  ВХОД', cams: [[.5, .5, 1.0, 1.08]], say: ['g08'], pad: .4, noise: { at: [.62, 1.05, 6.32], w: .95, h: 2.15 } },   // там, где она стояла, — помехи
        { img: null, phone: { kind: 'lock', time: '03:31', notes: [{ from: 'Лили', text: 'Всё ещё не сплю :( Ты как там? Утром позвони!', at: .5, ago: 'минуту назад' }] }, say: ['g09'], sfx: [['buzz', .5]], pad: .5 },
        { img: 'gl_face', fx: 'rain', cams: [[.5, .5, 1.1, 1.18]], say: ['g10'], pad: .3 },
        { img: 'gl_look', fx: 'rain', cams: [[.5, .5, 1.0, 1.06]], say: ['g11'], sfx: [['glass_hit', 1.8, { gain: .9 }], ['glass_hit', 2.1, { gain: .8 }], ['glass_hit', 3.1, { gain: .9 }], ['glass_hit', 3.4, { gain: .8 }]], heart: true, pad: 1.6 }
      ],
      then: { choice: '«Итан, открой!»', timeout: 12, def: 2,
              opts: [{ t: 'Открыть', go: 'branch_a' }, { t: 'Написать на стекле', go: 'glass_write' }, { t: 'Отойти от двери', go: 'branch_b' }] }
    },
    glass_write: {
      music: 'tension', time: '03:31',
      beats: [
        { img: 'gl_write', fx: 'rain', cams: [[.5, .5, 1.0, 1.06]], say: ['g12'], pad: 1.4 },
        { img: 'gl_face', fx: 'rain', cams: [[.5, .5, 1.14, 1.2]], say: ['g13'], pad: .4 },
        { img: 'gl_wide', fx: 'rain', cams: [[.5, .5, 1.0, 1.05]], say: ['g14'], heart: true, pad: .4 }
      ],
      then: { choice: 'Оно повторяет твоё имя.', timeout: 10, def: 1, opts: [{ t: 'Открыть', go: 'branch_a' }, { t: 'Отойти от двери', go: 'branch_b' }] }
    },

    /* ================= ВЕТКА А: открыл, 03:31 ================= */
    branch_a: {
      music: 'tension', time: '03:31',
      beats: [
        { img: 'a_key', cams: [[.5, .5, 1.0, 1.06]], say: ['a01'], sfx: [['click', 1.2, { gain: .8 }]], pad: .4 },
        { img: 'a_hug', cams: [[.5, .5, 1.0, 1.05]], say: ['a02'], sfx: [['door', .55, { gain: .6 }], ['rain_burst', .7, { gain: .5 }]], pad: .3 },
        { img: 'a_hug_side', cams: [[.5, .5, 1.0, 1.04]], say: ['a03'], pad: .3 },
        { img: 'a_hug_face', cams: [[.5, .5, 1.0, 1.06]], say: ['a04'], hush: true, pad: .4 },
        { img: 'a_hug_face', cams: [[.5, .5, 1.06, 1.12]], say: ['a05'], pad: .4 },
        { img: null, phone: { kind: 'call', name: 'Лили' }, say: ['a06'], sfx: [['ring_mob', 0], ['buzz', 0]], pad: .3 },
        { img: 'a_beside', cams: [[.5, .5, 1.0, 1.06]], say: ['a07'], sfx: [['ring_mob', 0, { gain: .8 }]], pad: .5 },
        { img: 'a_steps', cams: [[.5, .5, 1.0, 1.08]], say: ['a08'], pad: .4 },
        { img: 'a_floors', cams: [[.5, .5, 1.0, 1.1]], say: ['a09'], sfx: [['tick', .9], ['tick', 1.8], ['tick', 2.7]], pad: .5 },
        { img: 'c13_lily', fx: 'cctv', label: 'CAM 13', cams: [[.5, .5, 1.0, 1.15]], say: ['a10'], pad: .3 },
        { img: 'c13_lily_face', fx: 'cctv', label: 'CAM 13', cams: [[.5, .45, 1.2, 1.5]], say: ['a11'], sfx: [['neck_crack', .4]], pad: .4 },
        { img: 'e_face', cams: [[.5, .5, 1.0, 1.08]], say: ['a12'], pad: .4 },
        { img: 'a_floors_down', cams: [[.5, .5, 1.0, 1.08]], say: ['a13'], sfx: [['tick', .7], ['tick', 1.4], ['tick', 2.1]], heart: true, pad: .3 }
      ],
      then: { choice: 'Лифт едет вниз.', timeout: 8, def: 0, opts: [{ t: 'Спрятаться в будке', go: 'hide' }, { t: 'Бежать к аварийному выходу', go: 'run' }] }
    },
    hide: {
      music: 'tension', time: '03:40',
      beats: [
        { img: 'h_lock', cams: [[.5, .5, 1.0, 1.06]], say: ['h01'], sfx: [['click', 1.1]], pad: .2 },
        { img: 'h_lamp', cams: [[.5, .5, 1.0, 1.06]], sfx: [['click', .75]], text: '', pad: 1.3 },
        { img: 'h_crouch', cams: [[.5, .5, 1.0, 1.06]], say: ['h02'], sfx: [['ding', .2, { gain: .5 }], ['steps_barefoot', 2.2, { gain: .6 }]], pad: .3 },
        { img: 'h_pocket', cams: [[.5, .5, 1.0, 1.08]], say: ['h03'], sfx: [['buzz', .1]], pad: .3 },
        { img: 'h_face', cams: [[.5, .5, 1.0, 1.06]], say: ['h04'], heart: true, pad: .3 }
      ],
      then: { mg: 'hold', title: 'НЕ ДЫШИ', hint: 'Оно ищет на звук. Держи палец, пока шаги рядом.', dur: 11, win: 'hide_wait', lose: 'over_k5' }
    },
    hide_wait: {   // шаги ушли наверх — до шести почти два часа под стойкой, у мониторов (час смены A1)
      music: 'drone', time: '03:58',
      beats: [{ img: 'h_crouch', cams: [[.5, .5, 1.0, 1.05]], say: ['n38'], pad: .4 }],
      then: { shift: 'A1' }
    },
    hide2: {
      music: 'drone', time: '05:59',
      beats: [
        { img: 'h_crouch', cams: [[.5, .5, 1.0, 1.05]], say: ['h05'], pad: .4 },
        { img: 'h_face_open', cams: [[.5, .5, 1.05, 1.12]], say: ['h06'], sfx: [['ch_whisper', .2, { bus: 'voice', gain: .6 }]], pad: .6 },
        { img: 'h_guard', fx: 'dawn', time: '06:00', cams: [[.5, .5, 1.0, 1.05]], say: ['h07', 'h08'], sfx: [['door', .2]], pad: .3 },
        { img: 'h_guard_back', fx: 'dawn', cams: [[.5, .5, 1.0, 1.05]], say: ['h09'], pad: .4 },
        { img: 'h_reflect', fx: 'dawn', cams: [[.5, .5, 1.0, 1.1]], say: ['h10'], pad: 1.4 }
      ],
      then: { ending: 'inside', title: 'ВНУТРИ', sub: '«Ты со мной», — сказал ты ей. И не соврал.' }
    },
    run: {
      music: 'tension', time: '03:40',
      beats: [{ img: 'x_run', cams: [[.5, .5, 1.0, 1.04]], say: ['x01', 'x02'], sfx: [['steps', 0, { gain: .8 }]], pad: .1 }],
      then: { mg: 'run', title: 'БЕГИ', n: 5, win: 'run2', lose: 'over_k6' }
    },
    run2: {
      music: 'drone', time: '03:44',
      beats: [
        { img: 'x_out', fx: 'rain', cams: [[.5, .5, 1.0, 1.06]], say: ['x03'], sfx: [['door', 0], ['rain_burst', .1]], pad: .4 },
        { img: 'x_building', fx: 'rain', cams: [[.5, .45, 1.0, 1.12]], say: ['x04'], pad: .6 },
        { img: null, phone: { kind: 'call', name: 'Лили' }, say: ['x05'], sfx: [['ring_mob', 0], ['buzz', 0]], pad: .3 },
        { img: 'x_rain', fx: 'rain', cams: [[.5, .5, 1.0, 1.06]], say: ['x06'], pad: .3 },
        { img: 'x_rain', fx: 'rain', cams: [[.5, .45, 1.1, 1.16]], say: ['x07'], pad: .4 },
        { img: null, phone: { kind: 'talk', name: 'Лили', t0: 24 }, say: ['x08'], heart: true, pad: 1.4 }
      ],
      then: { ending: 'sister', title: 'СЕСТРА', sub: 'Оно пошло туда, где его ещё не ждут. Твоим голосом.' }
    },

    /* ================= ВЕТКА Б: не открыл, 03:32 ================= */
    branch_b: {
      music: 'tension', time: '03:32',
      beats: [
        { img: 'gl_wide', fx: 'rain', cams: [[.5, .5, 1.0, 1.04]], say: ['g15'], pad: .4 },
        { img: 'gl_face', fx: 'rain', cams: [[.5, .5, 1.1, 1.14]], say: ['b01'], hush: true, pad: .4 },
        { img: 'b_smile', fx: 'rain', cams: [[.5, .47, 1.0, 1.2]], say: ['b02'], sfx: [['skin_stretch', 1.0, { gain: .75 }], ['skin_stretch', 2.2, { gain: .9, rate: .85 }], ['drone_dread', .3, { gain: .5 }]], pad: .8 },   // улыбка ползёт со скрипом кожи
        { img: 'gl_scratch', fx: 'rain', cams: [[.5, .5, 1.0, 1.06]], say: ['b03', 'b04'], sfx: [['glass_scratch', .3, { gain: .8 }]], pad: .5 },
        { img: 'b_back', fx: 'rain', cams: [[.5, .5, 1.0, 1.05]], say: ['b05'], pad: .6 },
        { img: 'b_print', fx: 'rain', cams: [[.5, .5, 1.0, 1.1]], say: ['b06'], pad: .6 },
        { img: null, phone: { kind: 'out', name: 'Лили' }, say: ['b07'], sfx: [['ringback', .2], ['ringback', 6.2]], pad: .3 },
        { img: null, phone: { kind: 'talk', name: 'Лили', t0: 0 }, say: ['b08', 'b09', 'b10', 'b11'], pad: .4 },
        { img: 'e_hands', cams: [[.5, .5, 1.0, 1.06]], say: ['b12'], pad: .5 }   // «Руки тряслись» — видно, как дрожат ладони
      ],
      then: { goto: 'server' }
    },
    server: {
      music: 'tension', time: '04:10',
      beats: [
        { img: 'monitors', fx: 'flicker', cams: [[.5, .5, 1.0, 1.1]], say: ['s01'], sfx: [['static', .2, { gain: .4 }]], pad: .3 },
        // «тринадцатая» под потолком у 707: сначала общий план (Гас в дверях, над ним — оно), потом камера сама наезжает: оно рвёт и ест, хрустят кости
        { img: 'c13_gus', fx: 'cctv', label: 'CAM 13', cams: [[[-.62, .45, -12.05], 0, 1.0, 1.15], [[-.7, .5, -12.2], 0, 1.65, 1.85]], say: ['s02', 's03'],
          sfx: [['eat_wet', .5, { gain: .55 }], ['neck_crack', 3.35, { gain: .55 }], ['eat_wet', 6.0, { gain: .65 }], ['neck_crack', 8.15, { gain: .7 }]], pad: .3 },
        // перестаёт есть и поднимает голову к объективу: лицо в крови, глаза загораются в ИК-подсветке камеры
        { img: 'c13_gus_up', fx: 'cctv', label: 'CAM 13', cams: [[[-.7, .95, -12.45], 0, 1.6, 2.4]], say: ['s04'], sfx: [['neck_crack', 1.3], ['sub_presence', 1.8, { gain: .8 }], ['static', 2.2, { gain: .5, cut: .45 }]], pad: .5 },
        { img: 'booth_dark', fx: 'dark', cams: [[.5, .5, 1.0, 1.0]], text: '', pad: 1.4 },
        { img: 'srv_cam', fx: 'cctv', label: 'CAM −2  СЕРВЕРНАЯ', cams: [[.5, .5, 1.0, 1.06]], say: ['s05'], pad: .3 },
        { img: 'srv_cam', fx: 'cctv', label: 'CAM −2  СЕРВЕРНАЯ', cams: [[.58, .58, 1.5, 1.7]], say: ['s06'], pad: .3 },
        { img: 'srv_cam', fx: 'cctv', label: 'CAM −2  СЕРВЕРНАЯ', cams: [[.5, .5, 1.1, 1.14]], say: ['s07'], pad: .3 },
        { img: 'srv_cam', fx: 'cctv', label: 'CAM −2  СЕРВЕРНАЯ', cams: [[.5, .5, 1.14, 1.18]], say: ['al04'], if: 'heard', pad: .3 },
        { img: 'srv_sheet', fx: 'cctv', label: 'CAM −2  СЕРВЕРНАЯ', cams: [[.58, .55, 1.6, 1.9]], say: ['s08'], rule: 'НЕ ЗОВИ МЕНЯ.\nСТУЧИ ТРИ РАЗА.', pad: .6 },
        { img: 'srv_look', fx: 'cctv', label: 'CAM −2  СЕРВЕРНАЯ', cams: [[.55, .55, 1.3, 1.4]], say: ['s09'], heart: true, pad: .5 }
      ],
      then: { choice: 'Фрэнк жив. Он внизу.', timeout: 12, def: 0, opts: [{ t: 'Спуститься к нему', go: 'down' }, { t: 'Остаться в будке', go: 'stay' }] }
    },
    down: {
      music: 'drone', time: '04:14',
      beats: [{ img: 'st_down', cams: [[.5, .5, 1.0, 1.04]], say: ['d01'], sfx: [['steps', .2, { gain: .6 }]], pad: .2 }],
      then: { mg: 'torch', title: 'ПОДВАЛ', hint: 'Темно. Веди фонарём, найди дверь серверной.', img: 'bs_dark', goal: [.5, .52], found: 'Железная дверь. Под ней — свет.', win: 'basement' }
    },
    basement: {
      music: 'tension', time: '04:17',
      beats: [
        { img: 'bs_walk', cams: [[.5, .5, 1.0, 1.06]], say: ['d02'], sfx: [['salt_crunch', .4, { gain: .6 }], ['salt_crunch', 1.4, { gain: .5 }]], pad: .3 },
        { img: 'bs_door', cams: [[.5, .5, 1.0, 1.08]], say: ['d03'], pad: .4 },
        { img: 'bs_door', cams: [[.5, .5, 1.1, 1.16]], say: ['d04', 'd05'], hush: true, pad: .4 },
        { img: 'bs_door', cams: [[.5, .55, 1.16, 1.22]], say: ['d06'], rule: 'НЕ ЗОВИ МЕНЯ.\nСТУЧИ ТРИ РАЗА.', heart: true, pad: .4 }
      ],
      then: { choice: '«Заходи, сынок. Открыто».', timeout: 10, def: 1, opts: [{ t: 'Открыть дверь', go: 'over_k7' }, { t: 'Постучать три раза', go: 'knock3' }] }
    },
    knock3: {
      music: 'drone', time: '04:18',
      beats: [
        { img: 'bs_knock', cams: [[.5, .5, 1.0, 1.04]], say: ['d07'], sfx: [['knock_metal', .55], ['knock_metal', .82], ['knock_metal', 1.09]], pad: .4 },
        { img: 'bs_door', cams: [[.5, .5, 1.1, 1.14]], say: ['d08'], sfx: [['bolt', 1.5, { gain: .9 }]], pad: .2 },
        { img: 'bs_open', cams: [[.5, .5, 1.0, 1.06]], say: ['d09'], sfx: [['door_metal', .1, { gain: .7 }]], pad: .3 },
        { img: 'bs_frank', cams: [[.5, .5, 1.0, 1.06]], say: ['d10'], pad: .3 },
        { img: 'srv_in', cams: [[.5, .5, 1.0, 1.05]], say: ['d11', 'd12'], pad: .3, react: { 'ФРЭНК': ['no+sad', ['d12', '^(седьмую|can.t|сьому|séptima|sétima|siebte)$', .52], 99] } },   // «седьмую одному не выдержать» — качает головой (к слову, на любом языке), лицо грустное до конца кадра
        { img: 'srv_in', cams: [[.52, .45, 1.2, 1.26]], say: ['d13', 'd13b'], pad: .4, react: { 'ФРЭНК': ['grim', 0, 99] } },   // d13b — почему соль: для него это стена, вот оно и зовёт, ждёт, пока выйдешь или ответишь
        { img: 'srv_dark', cams: [[.5, .5, 1.0, 1.1]], say: ['d14', 'd15'], hush: true, pad: .3 },
        { img: 'srv_reach', cams: [[.5, .5, 1.0, 1.06]], say: ['d16', 'd17'], heart: true, pad: .2 },
        { img: 'srv_hold', cams: [[.5, .5, 1.0, 1.04]], text: '', pad: 1.0 }
      ],
      then: { mg: 'hold', title: 'ДЕРЖИ ФРЭНКА', hint: 'Мать зовёт его из темноты. Не отпускай, пока голос не стихнет.', meter: 'силы', btn: 'ДЕРЖАТЬ', btnOn: 'ДЕРЖУ',
              calls: ['d18', 'd19'], dur: 12, safe: '04:30', safeHint: 'Голос стих. Ненадолго.', win: 'srv_wait', lose: 'over_k8' }
    },
    srv_wait: {
      music: 'drone', time: '04:30',
      beats: [{ img: 'srv_in', cams: [[.5, .5, 1.0, 1.05]], say: ['n32'], pad: .4 }],
      then: { explore: { img: 'srv_room', cams: [[.5, .5, 1.0, 1.03]], hint: 'До шести ещё полтора часа. Осмотрись.',
        spots: [{ t: 'Круг соли', go: 'sv_salt', at: [1.0, .02, 2.15] }, { t: 'Кулер и сухари', go: 'sv_cooler', at: [.35, .9, 3.05] }, { t: 'Листы на стойке', go: 'sv_sheets', at: [3.0, 1.2, 2.2] }],
        leave: { t: 'Сесть рядом с Фрэнком', go: 'srv_talk' } } }
    },
    sv_salt: { music: 'drone', beats: [{ img: 'srv_room', cams: [[[1.0, .02, 2.15], -.04, 1.0, 1.5]], say: ['sv01'], pad: .4 }], then: { back: 'srv_wait' } },
    sv_cooler: { music: 'drone', beats: [{ img: 'srv_room', cams: [[[.35, .9, 3.05], 0, 1.2, 1.5]], say: ['sv02'], pad: .4 }], then: { back: 'srv_wait' } },
    sv_sheets: { music: 'drone', beats: [{ img: 'srv_sheets', cams: [[.5, .5, 1.0, 1.06]], say: ['sv03'], pad: .5 }], then: { back: 'srv_wait' } },
    srv_talk: { music: 'drone', beats: [], then: { talk: { img: 'srv_in', cams: [[.5, .5, 1.0, 1.02]], title: 'Фрэнк',
      topics: [{ t: 'Это правда была она?', go: 'sv_mom' }, { t: 'Гас сказал, его тоже зовут', go: 'sv_gus', if: 'v:gs_calls' }, { t: 'Почему соль?', go: 'sv_why' }],
      bye: { t: 'Посидеть с ним до шести', go: 'srv_bye' } } } },
    sv_mom: { music: 'drone', beats: [{ img: 'srv_in', cams: [[.5, .5, 1.0, 1.04]], say: ['sv05', 'sv06'], pad: .4, react: { 'ФРЭНК': ['no+sad', ['sv06', '^(это|it.s|це|eso|não|das)$', .88], 99] } }], then: { back: 'srv_talk' } },   // «это не она» — качает головой на этих словах
    sv_gus: { music: 'drone', beats: [{ img: 'srv_in', cams: [[.5, .5, 1.0, 1.04]], say: ['sv07', 'sv08'], pad: .4, react: { 'ФРЭНК': ['yes+sad', ['sv08', '^(знаю|know|sé|sei|weiß)$', .02], 99] } }], then: { back: 'srv_talk' } },   // «знаю» — кивает на слове
    sv_why: { music: 'drone', beats: [{ img: 'srv_in', cams: [[.5, .5, 1.0, 1.04]], say: ['sv09', 'sv10'], pad: .4 }], then: { back: 'srv_talk' } },
    srv_bye: { music: 'drone', beats: [{ img: 'srv_in', cams: [[.5, .5, 1.0, 1.04]], say: ['sv11', 'sv12'], pad: .6, react: { 'ФРЭНК': ['yes+grim', 2.3, 99] } }], then: { shift: 'S6' } },
    dawn: {
      music: 'drone', time: '06:00',
      beats: [
        { img: 'srv_in', cams: [[.5, .5, 1.0, 1.04]], say: ['d20', 'd21'], pad: .4, react: { 'ФРЭНК': ['yes+calm', 3.6, 99] } },   // «всё, до ночи оно спит» — кивает
        { img: 'st_up', fx: 'cctv', label: 'CAM 04  ЛЕСТНИЦА', cams: [[.5, .5, 1.0, 1.06]], say: ['d22'], pad: .4 },
        { img: 'x_two', fx: 'rain', cams: [[.5, .5, 1.0, 1.06]], say: ['d23'], sfx: [['tram', .8, { gain: .4 }]], pad: .4 },
        { img: 'x_two', fx: 'rain', cams: [[.52, .45, 1.2, 1.25]], say: ['d24'], pad: .3, react: { 'ФРЭНК': ['worry', 0, 99] } },
        { img: 'x_no', fx: 'rain', cams: [[.5, .45, 1.0, 1.06]], say: ['d25'], pad: .3, react: { 'ИТАН': ['no+grim', .3, 9] } },   // «Я покачал головой» — головой, лицо тяжёлое
        { img: 'x_two', fx: 'rain', cams: [[.5, .45, 1.25, 1.3]], say: ['d26'], pad: .4, react: { 'ФРЭНК': ['sad', 0, 99] } },
        { img: 'x_smoke', fx: 'rain', cams: [[.5, .45, 1.0, 1.06]], say: ['d27'], sfx: [['lighter', .3]], pad: 1.2 },
        { img: 'x_smoke', fx: 'rain', cams: [[.5, .42, 1.1, 1.16]], say: ['d28', 'd29'], pad: .4, react: { 'ФРЭНК': ['sad', 0, 99] } },
        { img: 'x_two', fx: 'rain', cams: [[.5, .5, 1.0, 1.04]], say: ['d30'], props: { cig: 1 }, pad: .6 },
        { img: 'hall_cam', fx: 'cctv', label: 'CAM 02  ХОЛЛ', hud: { date: '05.11.2026', at: '03:00' }, cams: [[.5, .5, 1.0, 1.04], [.36, .46, 2.2, 2.6]], say: ['d31'], pad: 1.6 }   // «теперь каждую ночь в три»: запись другой ночи
      ],
      then: { ending: 'dawn', title: 'РАССВЕТ', sub: 'Ты ни разу не ответил. Пока.' }
    },
    stay: {
      music: 'drone', time: '04:15',
      beats: [
        { img: 'booth_dark', cams: [[.5, .5, 1.0, 1.06]], say: ['w01'], pad: .4 }
      ],
      then: { shift: 'S5' }   // самый трудный час: он знает, что ты наверху один
    },
    stay2: {
      music: 'drone', time: '05:35',
      beats: [
        { img: 'monitors', cams: [[.5, .5, 1.0, 1.08]], say: ['w02'], pad: .4 },
        { img: 'booth_dark', cams: [[.5, .5, 1.08, 1.14]], say: ['w03'], pad: .6 }
      ],
      then: { goto: 'call540' }
    },
    call540: {
      music: 'tension', time: '05:40',
      beats: [
        { img: null, phone: { kind: 'call', name: 'Лили' }, say: ['w04'], sfx: [['ring_mob', 0], ['buzz', 0]], pad: .2 },
        { img: null, phone: { kind: 'call', name: 'Лили' }, say: ['w05'], sfx: [['ring_mob', 0]], heart: true, pad: .3 }
      ],
      then: { choice: 'Звонит «Лили».', timeout: 8, def: 1, opts: [{ t: 'Ответить', go: 'over_k9' }, { t: 'Сбросить', go: 'morning' }] }
    },
    morning: {
      music: 'drone', time: '06:00',
      beats: [
        { img: 'monitors', cams: [[.5, .5, 1.0, 1.08]], say: ['w06'], sfx: [['buzz', .4], ['buzz', 1.8]], pad: .4 },
        { img: 'monitors', fx: 'dawn', cams: [[.5, .5, 1.08, 1.1]], text: '', sfx: [['knock', .4], ['knock', .67], ['knock', .94]], pad: 2.2 },   // три стука в стекло входа, как в 21:05: «это я»
        { img: 'hall_dawn', fx: 'dawn', cams: [[.5, .5, 1.0, 1.06]], say: ['w07', 'w08'], sfx: [['door', .2]], pad: .4 },
        { img: 'bs_after', cams: [[.5, .5, 1.0, 1.1]], say: ['w09'], pad: .8 },
        { img: 'jr_write', fx: 'lamp', cams: [[.5, .5, 1.0, 1.08]], say: ['w10'], sfx: [['pen', .6]], pad: 1.2 }
      ],
      then: { ending: 'newentry', title: 'ТЫ НЕ СПУСТИЛСЯ', sub: 'Ты дожил до утра. Фрэнк — нет.' }
    },

    /* ================= СМЕРТИ ================= */
    over_k1: {
      music: 'tension',
      beats: [
        // оно стоит посреди коридора лицом к объективу и не шевелится; наезд — на его лицо (точка мира), лицо выше субтитров
        { img: 'c13_face', fx: 'cctv', label: 'CAM 13', cams: [[[.3, 2.12, -4.6], .09, 1.0, 1.35]], say: ['k1a'], pad: .2 },
        { img: 'c13_name', fx: 'cctv', label: 'CAM 13', cams: [[[.3, 2.12, -4.6], .07, 1.7, 2.15]], say: ['k1b'], pad: .1 },   // губы складывают «И-тан»
        // свой финал: крупно — лицо Итана в свете помех, губы сами отвечают; сигнал рвётся — и на тринадцатой, где стояло оно, стоит Итан (серый, глаза чёрные) и смотрит в объектив
        { img: 'k1_face', cams: [[.5, .44, 1.0, 1.05]], sfx: [['static', 0, { gain: .22, cut: 1.45, once: 1 }], ['tinnitus', .4, { gain: .35 }], ['static', 1.42, { gain: .85, cut: .4, once: 1 }]], snow: [[1.45, 1.8]], text: '', pad: 1.65 },
        { img: 'k1_taken', fx: 'cctv', label: 'CAM 13', hush: 'soft', cams: [[[.3, 1.62, -4.6], .03, 1.75, 2.15]], snow: [[0, .3], [2.75, 3.05]], black: 3.05,
          sfx: [['static', 0, { gain: .7, cut: .3, once: 1 }], ['sub_presence', .5, { gain: .55, cut: 2.3 }], ['static', 2.75, { gain: .95, cut: .3, once: 1 }]], text: '', pad: 3.6 }
      ],
      then: { over: 'ТЫ ПОСМОТРЕЛ ЕМУ В ЛИЦО', sub: 'Не смотри в тринадцатую камеру.', retry: '@save' }
    },
    over_k2: {
      music: 'tension', time: '03:03',
      beats: [
        { img: 'corridor_lit_after', cams: [[.5, .5, 1.0, 1.12]], say: ['k2a'], sfx: [['steps', .2]], pad: .2 },
        { img: 'eat_close', cams: [[.5, .55, 1.0, 1.12]], say: ['k2b'], sfx: [['eat_wet', 0]], pad: .4 },
        { img: 'corridor_off', cams: [[.5, .5, 1.0, 1.25]], say: ['k2c'], sfx: [['click', .5], ['click', 1.0], ['click', 1.4], ['click', 1.7]], pad: .1 },
        // свой финал: горит одна лампа — над Итаном; впереди темнота. Лампа моргает — на краю света уже стоит оно, лицо под потолком; рывками складывается к нему, лицо к лицу. Лампа гаснет
        { img: 'k2_last', hush: 'soft', cams: [[.5, .5, 1.0, 1.0]], black: 4.0,
          sfx: [['fluor_flicker', 1.1, { gain: .55, cut: .4 }], ['skin_stretch', 2.95, { gain: .45 }], ['breath_close', 3.65, { gain: .85 }], ['hum_stop', 3.98, { gain: .85 }]], text: '', pad: 4.6 }
      ],
      then: { over: 'ТЫ ПОШЁЛ НА СВЕТ', sub: 'Сам к нему не ходи — ни на свет, ни в лифт.', retry: 'floor7b' }
    },
    over_k3: {
      music: 'tension',   // без своего времени: сюда же ведёт рация голосом Гаса в 04:42 (час S5) — часы не прыгают назад на 03:06
      beats: [
        { img: 'k3_answer', cams: [[.5, .5, 1.1, 1.2]], say: ['k3a', 'k3b'], pad: .1 },
        // свой финал: склейка на правое стекло будки — вплотную за ним, согнувшись, стоит оно с рацией Гаса у рта; опускает её — губы ещё шевелятся; лампа гаснет
        { img: 'k3_radio', hush: 'soft', cams: [[.5, .46, 1.0, 1.06]], black: 3.0,
          sfx: [['phone_crackle', 0, { gain: .55 }], ['breath_close', .55, { gain: .45 }], ['whispers', 1.95, { gain: .35 }], ['hum_stop', 2.97, { gain: .8 }], ['static', 3.0, { gain: .3, cut: .9, once: 1 }]], text: '', pad: 3.65 }
      ],
      then: { over: 'ТЫ ОТОЗВАЛСЯ', sub: 'Гас не знал твоего имени. А оно знает.', retry: '@save' }   // снова — с того же места: выбор у рации (03:05) или час S5
    },
    over_k4: {
      music: 'tension', time: '03:18',
      beats: [
        // мини-игра кончилась тем, что он повернулся к объективу: стоит в дальнем конце лицом к нам — медленный цифровой наезд, пока Итан рассказывает
        { img: 'k4_far', fx: 'cctv', label: 'CAM 13', cams: [[[0, 2.12, -13], .06, 1.2, 2.0]], say: ['k4a', 'k4b'], pad: .1 },
        // свой финал: рывок помех — он ближе; тело рывками отворачивается, а голова остаётся лицом к объективу (хруст); идёт к камере спиной вперёд — помехи, обрыв
        { img: 'k4_owl', fx: 'cctv', label: 'CAM 13', hush: 'soft', cams: [[[0, 2.0, -7.2], .06, 1.8, 1.85], [.5, .46, 1.0, 1.0]], snow: [[0, .28], [3.55, 3.85]], black: 3.85,
          sfx: [['static', 0, { gain: .7, cut: .28, once: 1 }], ['skin_stretch', .6, { gain: .55 }], ['neck_crack', 1.1, { gain: .8 }], ['neck_crack', 1.36, { gain: .65 }], ['steps_barefoot', 2.05, { gain: .75, rate: 1.25 }], ['static', 3.55, { gain: .95, cut: .3, once: 1 }]], text: '', pad: 4.4 }
      ],
      then: { over: 'ОНО ОБЕРНУЛОСЬ', sub: 'Тринадцатая включилась сама — выдёргивай шнур, не смотри.', retry: 'cam13' }
    },
    over_k5: {
      music: 'tension',
      beats: [
        { img: 'h_face_open', fx: 'dark', cams: [[.5, .5, 1.1, 1.2]], say: ['k5a'], pad: .1 },
        // свой финал: из-под стола — в просвет опускается её лицо, голова набок дальше человеческого; рука выстреливает к нему — обрыв, в темноте слышно, как его волокут
        { img: 'k5_drag', hush: 'soft', cams: [[.5, .47, 1.0, 1.04]], black: 1.83,
          sfx: [['skin_stretch', .55, { gain: .35 }], ['breath_close', 1.05, { gain: .6 }], ['boom', 1.83, { gain: .7 }], ['glass_scratch', 1.95, { gain: .5, rate: .5 }]], text: '', pad: 3.0 }
      ],
      then: { over: 'ОНО УСЛЫШАЛО', sub: 'Оно ищет на звук. Пока шаги рядом — не дыши.', retry: 'hide' }
    },
    over_k5b: {   // то же в часе A1 (шаги у будки, отпустил «НЕ ДЫШИ»): заново — с начала часа
      music: 'tension',
      beats: [
        { img: 'h_face_open', fx: 'dark', cams: [[.5, .5, 1.1, 1.2]], say: ['k5a'], pad: .1 },
        // свой финал: из-под стола — в просвет опускается её лицо, голова набок дальше человеческого; рука выстреливает к нему — обрыв, в темноте слышно, как его волокут
        { img: 'k5_drag', hush: 'soft', cams: [[.5, .47, 1.0, 1.04]], black: 1.83,
          sfx: [['skin_stretch', .55, { gain: .35 }], ['breath_close', 1.05, { gain: .6 }], ['boom', 1.83, { gain: .7 }], ['glass_scratch', 1.95, { gain: .5, rate: .5 }]], text: '', pad: 3.0 }
      ],
      then: { over: 'ОНО УСЛЫШАЛО', sub: 'Оно ищет на звук. Пока шаги рядом — не дыши.', retry: '@save' }
    },
    over_k6: {
      music: 'tension',
      beats: [   // свой финал: упал — камера на полу; лампы гаснут к нему по одной; в темноте босые шаги, последняя лампа моргнула — ступни уже перед лицом; оно присаживается — сверху в кадр опускается лицо
        { img: 'k6_fall', cams: [[.5, .5, 1.0, 1.0]], black: 3.8,
          sfx: [['boom', 0, { gain: .55 }], ['tinnitus', .1, { gain: .45 }], ['hum_stop', .35, { gain: .35 }], ['hum_stop', 1.05, { gain: .45 }], ['hum_stop', 1.75, { gain: .55 }], ['hum_stop', 2.05, { gain: .6 }], ['steps_barefoot', 1.95, { gain: .85, cut: .5 }], ['skin_stretch', 2.85, { gain: .4 }], ['breath_close', 3.35, { gain: .9 }], ['hum_stop', 3.78, { gain: .8 }]], text: '', pad: 4.4 }],
      then: { over: 'ТЕМНОТА ДОГНАЛА', sub: 'Остановишься — догонит.', retry: 'run' }
    },
    over_k7: {
      music: 'tension', time: '04:18',
      beats: [
        { img: 'bs_open', cams: [[.5, .5, 1.0, 1.06]], say: ['k7a'], sfx: [['door_metal', .1]], pad: .2 },
        { img: 'bs_no', cams: [[.5, .45, 1.0, 1.12]], say: ['k7b'], pad: .2 },
        { img: 'bs_no', fx: 'dark', cams: [[.5, .45, 1.12, 1.2]], say: ['k7c'], sfx: [['breath_close', .4]], pad: .1 },
        // свой финал: Итан оборачивается — в луче фонаря оно вплотную, наклонилось к лицу; пауза; рот раскрывается шире человеческого, подаётся вперёд — позади хлопает дверь серверной, обрыв
        { img: 'k7_behind', hush: 'soft', cams: [[.5, .4, 1.0, 1.3]], black: 2.84,   // наезд на лицо: грудь уходит из кадра
          sfx: [['heart_held', .1, { gain: .6, cut: 1.4 }], ['skin_stretch', 1.8, { gain: .55 }], ['breath_close', 2.35, { gain: .8 }], ['door_slams', 2.84, { gain: 1 }]], text: '', pad: 3.55 }
      ],
      then: { over: 'ТЫ ПОШЁЛ НА ЗОВ', sub: 'Оно умеет звать. Стучать — нет.', retry: 'basement' }
    },
    over_k8: {
      music: 'tension',   // без своего времени: держал Фрэнка в 04:18 (knock3) или в часе S6 (05:06, 05:34) — часы не прыгают на 05:20
      beats: [
        { img: 'srv_crawl', cams: [[.5, .5, 1.0, 1.08]], say: ['k8a'], pad: .4 },
        { img: 'srv_dark', cams: [[.5, .5, 1.1, 1.2]], say: ['k8b', 'k8c'], pad: .1 },
        // свой финал: луч фонаря поднимается — на потолке над кругом, вниз головой, висит оно; голос Фрэнка — из его рта; голова ложится набок — и оно срывается вниз, на Итана: обрыв
        { img: 'k8_ceiling', hush: 'soft', cams: [[.5, .5, 1.0, 1.06]], say: ['k8b'], sayAt: 1.7, black: 3.5,
          sfx: [['salt_crunch', .2, { gain: .45 }], ['skin_stretch', 2.95, { gain: .5 }], ['boom', 3.48, { gain: .75 }], ['breath_close', 3.5, { gain: .7 }]], pad: 1.25 }
      ],
      then: { over: 'ФРЭНК УШЁЛ НА ГОЛОС', sub: 'Держи его, пока голос не стихнет.', retry: '@save' }   // снова — с того же места: «держи Фрэнка» в серверной или час S6 (а не с 04:18)
    },
    over_k9: {
      music: 'tension', time: '05:41',
      beats: [
        { img: null, phone: { kind: 'talk', name: 'Лили', t0: 0 }, say: ['k9a'], pad: .2 },
        { img: 'hall_cam_katya', fx: 'cctv', label: 'CAM 02  ХОЛЛ', cams: [[.5, .5, 1.0, 1.1], [.47, .47, 1.6, 1.9]], say: ['k9b'], pad: .3 },
        // свой финал: тёмное стекло будки, никого — тишина — удар: ладонь и лицо у стекла; дышит на стекло; ладонь сползает, оставляя мазок; лампа гаснет
        { img: 'k9_glass', hush: 'soft', scare: 1.25, hit: ['glass_slam'], cams: [[.5, .46, 1.0, 1.04]], black: 3.45,
          sfx: [['breath_close', 1.95, { gain: .6 }], ['glass_scratch', 2.3, { gain: .6 }], ['hum_stop', 3.43, { gain: .75 }]], text: '', pad: 4.0 }
      ],
      then: { over: 'ТЫ ОТВЕТИЛ', sub: 'Свои до утра подождут.', retry: 'call540' }
    },
    /* ================= ОБХОДЫ И СМЕНА ================= */
    round1: {
      music: 'drone', time: '22:05',
      beats: [
        { img: 'st_down', cams: [[.5, .5, 1.0, 1.04]], say: ['n02'], sfx: [['steps', .2, { gain: .6 }]], pad: .3, chapter: ['22:05', 'ОБХОД'] },
        { img: 'bs_walk', cams: [[.5, .5, 1.0, 1.06]], say: ['n03'], pad: .3 }
      ],
      then: { explore: { img: 'bm_view', cams: [[.5, .5, 1.0, 1.03]], hint: 'Дверь серверной. Что проверить?',
        spots: [{ t: 'Ручка', go: 'bm_handle', at: [-.38, 1.02, -13.93] }, { t: 'Постучать', go: 'bm_knock', at: [-.02, 1.42, -13.95], set: 'heard' },
          { t: 'Щель под дверью', go: 'bm_under', at: [.05, .06, -13.9] }, { t: 'Позвать', go: 'bm_call', at: [.3, 1.8, -13.95], if: 'v:bm_knock' }],
        leave: { t: 'Подняться наверх', go: 'bm_up' } } }
    },
    bm_handle: { music: 'drone', beats: [{ img: 'bs_door', cams: [[.42, .55, 1.1, 1.18]], say: ['n04'], sfx: [['door_metal', .2, { gain: .35, cut: .6 }]], pad: .4 }], then: { back: 'round1' } },
    bm_knock: { music: 'drone', beats: [{ img: 'bm_knock', cams: [[.5, .5, 1.0, 1.04]], say: ['bm01'], heart: true, pad: .6,   // постучал «тук-тук»; изнутри — три медленных удара (знак Фрэнка; открывает он только на три)
      sfx: [['knock_metal', .55], ['knock_metal', .82], ['knock_metal', 3.3, { gain: .6, lp: 700 }], ['knock_metal', 4.15, { gain: .6, lp: 700 }], ['knock_metal', 5.0, { gain: .6, lp: 700 }]] }], then: { back: 'round1' } },
    bm_under: { music: 'drone', beats: [{ img: 'bm_under', cams: [[.5, .5, 1.0, 1.06]], say: ['bm02'], pad: .5 }], then: { back: 'round1' } },
    bm_call: { music: 'drone', beats: [{ img: 'bs_door', cams: [[.5, .5, 1.05, 1.1]], say: ['bm03'], pad: .3 }, { img: 'bm_slit', cams: [[.5, .55, 1.0, 1.05]], say: ['bm04'], heart: true, pad: .5 }], then: { back: 'round1' } },
    bm_up: { music: 'drone', time: '22:15', beats: [
      { img: 'bm_back', cams: [[.5, .5, 1.0, 1.04]], say: ['bm05'], if: 'heard', sfx: [['steps', .2, { gain: .5 }]], pad: .5 },
      { img: 'bm_back', cams: [[.5, .5, 1.0, 1.04]], say: ['n05'], if: '!heard', sfx: [['steps', .2, { gain: .5 }]], pad: .5 }], then: { shift: 'S2' } },
    round2: {
      music: 'drone', time: '01:00',
      beats: [
        { img: 'r2_meet', cams: [[.5, .5, 1.0, 1.05]], say: ['n14'], pad: .3, chapter: ['01:00', 'СЕДЬМОЙ ЭТАЖ'] },
        { img: 'r2_apple', cams: [[.5, .5, 1.0, 1.06]], text: '', pad: 2.6 }
      ],
      then: { talk: { img: 'gs_ask', cams: [[.5, .5, 1.0, 1.03]], title: 'Гас', topics: [
        { t: 'Спасибо. А вы давно тут?', go: 'gs_long', if: '!v:gs_wife' },
        { t: 'Женщина на фото у Фрэнка — это Марта?', go: 'gs_photo', if: 'photo' },
        { t: 'Ваша жена тоже здесь работала?', go: 'gs_wife', if: ['!photo', '!v:gs_long', 'v:dg_gus'] },
        { t: 'Фрэнк что-нибудь говорил перед тем, как пропасть?', go: 'gs_frank' },
        { t: 'Кто вас зовёт?', go: 'gs_calls', if: 'v:gs_frank' },
        { t: '«Что бы она ни говорила — не отвечайте»', go: 'gs_warn', if: 'v:gs_calls', set: 'warned' }],
        bye: { t: 'Пора в будку', go: 'gs_bye' } } }
    },
    gs_long: { music: 'drone', beats: [{ img: 'gs_ask', cams: [[.5, .5, 1.0, 1.04]], say: ['n16'], pad: .2 }, { img: 'gs_sad', cams: [[.5, .5, 1.0, 1.05]], say: ['n17'], pad: .4 }], then: { back: 'round2' } },
    gs_photo: { music: 'drone', beats: [{ img: 'gs_ask', cams: [[.5, .5, 1.0, 1.04]], say: ['gs01'], pad: .2 }, { img: 'gs_sad', cams: [[.5, .5, 1.0, 1.06]], say: ['gs02'], pad: .5 }], then: { back: 'round2' } },
    gs_wife: { music: 'drone', beats: [{ img: 'gs_ask', cams: [[.5, .5, 1.0, 1.04]], say: ['gs03'], pad: .2 }, { img: 'gs_sad', cams: [[.5, .5, 1.0, 1.05]], say: ['gs04'], pad: .4 }], then: { back: 'round2' } },
    gs_frank: { music: 'drone', beats: [{ img: 'gs_ask', cams: [[.5, .5, 1.0, 1.04]], say: ['gs05'], pad: .2 }, { img: 'gs_ans', cams: [[.5, .5, 1.0, 1.05]], say: ['gs06'], pad: .4, react: { 'ГАС': ['yes', ['gs06', '^(сказал|told|сказав|dije|disse|gesagt)$', .36], 2.0] } }], then: { back: 'round2' } },   // «Я сказал — зовут» — кивает
    gs_calls: { music: 'drone', beats: [{ img: 'gs_ask', cams: [[.5, .5, 1.0, 1.04]], say: ['gs07'], pad: .2 }, { img: 'gs_707', cams: [[.5, .5, 1.0, 1.05]], say: ['gs08'], heart: true, pad: .5, react: { 'ГАС': ['no', 5.0, 8.2] } }], then: { back: 'round2' } },
    gs_warn: { music: 'drone', beats: [{ img: 'gs_ask', cams: [[.5, .5, 1.0, 1.05]], say: ['gs09'], pad: .2 }, { img: 'gs_sad', cams: [[.5, .5, 1.0, 1.06]], say: ['gs10'], pad: .5, react: { 'ГАС': ['warm', 4.45, 99] } }], then: { back: 'round2' } },
    gs_bye: { music: 'drone', beats: [{ img: 'r2_talk', cams: [[.5, .5, 1.0, 1.05]], say: ['n15'], pad: .8, react: { 'ГАС': ['no+warm', 3.4, 99] } }], then: { goto: 'ly_call' } },

    /* ================= ЗВОНОК ЛИЛИ, 01:15 — разговор ================= */
    ly_call: {
      music: 'drone', time: '01:15',
      beats: [
        { img: null, phone: { kind: 'call', name: 'Лили' }, say: ['ly01'], sfx: [['ring_mob', 0], ['buzz', 0]], pad: .4 },
        { img: 'booth_call', cams: [[.5, .5, 1.0, 1.05]], say: ['ly02'], pad: .3 }
      ],
      then: { talk: { img: 'booth_call', cams: [[.5, .5, 1.05, 1.08]], title: 'Лили', topics: [
        { t: 'Как там Бублик?', go: 'ly_dog' }, { t: 'Ты сейчас в общаге?', go: 'ly_dorm' }, { t: 'Тут… странно', go: 'ly_weird' }],
        bye: { t: '«Спи давай. Всё сдашь»', go: 'ly_bye' } } }
    },
    ly_dog: { music: 'drone', beats: [{ img: 'booth_call', cams: [[.5, .5, 1.05, 1.1]], say: ['ly03'], pad: .2 }, { img: 'booth_call', cams: [[.5, .48, 1.1, 1.16]], say: ['ly04'], pad: .4 }], then: { back: 'ly_call' } },
    ly_dorm: { music: 'drone', beats: [{ img: 'booth_call', cams: [[.5, .5, 1.05, 1.1]], say: ['ly05'], pad: .2 }, { img: 'booth_call', cams: [[.5, .48, 1.1, 1.16]], say: ['ly06'], pad: .4 }], then: { back: 'ly_call' } },
    ly_weird: { music: 'drone', beats: [{ img: 'booth_call', cams: [[.5, .5, 1.05, 1.1]], say: ['ly07'], pad: .2 }, { img: 'booth_call', cams: [[.5, .48, 1.1, 1.16]], say: ['ly08'], pad: .4 }], then: { back: 'ly_call' } },
    ly_bye: { music: 'drone', beats: [{ img: 'booth_call', cams: [[.5, .5, 1.05, 1.1]], say: ['ly09'], pad: .2 }, { img: 'booth_call', cams: [[.5, .48, 1.1, 1.14]], say: ['ly10'], pad: .8 }], then: { shift: 'S4' } },
    sh_S1: { beats: [], then: { shift: 'S1' } }, sh_S2: { beats: [], then: { shift: 'S2' } }, sh_S3: { beats: [], then: { shift: 'S3' } }, sh_S4: { beats: [], then: { shift: 'S4' } }, sh_S5: { beats: [], then: { shift: 'S5' } }, sh_S6: { beats: [], then: { shift: 'S6' } }, sh_A1: { beats: [], then: { shift: 'A1' } },
    over_fmom: {
      music: 'tension',
      beats: [
        { img: null, phone: { kind: 'talk', name: 'Номер не определён', t0: 1 }, say: ['fp03'], pad: .2 },
        // свой финал: дыхание из трубки — уже за спиной; Итан медленно оборачивается — в свете экрана телефона лицо вплотную; держит взгляд; экран гаснет
        { img: 'fmom_turn', hush: 'soft', cams: [[.5, .5, 1.0, 1.0]], black: 2.92,
          sfx: [['breath_close', .15, { gain: .45, pan: -.3 }], ['heart_held', .45, { gain: .7, cut: 1.5 }], ['breath_close', 2.35, { gain: 1 }]], text: '', pad: 3.55 }
      ],
      then: { over: 'ТЫ ВЗЯЛ ТРУБКУ', sub: 'Звонили не тебе. Но ответил — ты.', retry: '@save' }
    },
    over_door: {
      music: 'tension',
      beats: [
        // замок щёлкнул, створка ушла наружу, она шагнула в дверь, обернулась к объективу и медленно улыбнулась — шире, чем можно
        { img: 'door_in', fx: 'cctv', label: 'CAM 01  ВХОД', cams: [[.5, .5, 1.0, 1.1]], say: ['n30'], sfx: [['bolt', .1, { gain: .8 }], ['door', .3, { gain: .6 }], ['rain_burst', .4, { gain: .5 }], ['steps', 1.1, { gain: .35, cut: 1.2 }]], pad: .3 },
        // свой финал (из будки): стоит посреди холла и улыбается; голова ложится набок до хруста; свет гаснет от входа к будке — с каждым разом она ближе; лицо за стеклом будки — темнота
        { img: 'door_tilt', hush: 'soft', cams: [[.5, .47, 1.0, 1.0]], black: 3.6,
          sfx: [['rain', 0, { gain: .2, cut: 2.2 }], ['neck_crack', 1.22, { gain: .8 }], ['neck_crack', 1.52, { gain: .55 }], ['hum_stop', 2.05, { gain: .55 }], ['hum_stop', 2.58, { gain: .65 }], ['hum_stop', 3.08, { gain: .75 }], ['breath_close', 3.3, { gain: .8 }]], text: '', pad: 4.2 }
      ],
      then: { over: 'ТЫ ОТКРЫЛ ЕЙ', sub: 'Лили спала в трёхстах километрах отсюда. Посторонних не впускать.', retry: '@save' }
    },
    over_come: {
      music: 'tension',
      beats: [
        // «Шаги. Уже не на камерах — в холле»: камеры на пульте гаснут одна за другой (было: в этот момент его как раз показывала камера холла), лампа будки захлёбывается
        { img: 'come_mon', cams: [[.5, .5, 1.0, 1.06]], say: ['n21'], sfx: [['steps_barefoot', .3, { gain: .55, pan: -.4 }], ['static', .55, { gain: .45, cut: .3, once: 1 }], ['static', 1.2, { gain: .45, cut: .3, once: 1 }], ['static', 1.85, { gain: .5, cut: .3, once: 1 }],
          ['fluor_flicker', 2.15, { gain: .6 }], ['hum_stop', 2.7, { gain: .8 }], ['steps_barefoot', 2.9, { gain: .75, pan: .55 }]], pad: .55 },
        // свой финал: дверь будки изнутри — три стука, ручка медленно идёт вниз, дверь приоткрывается, в щель по кромке заходят длинные пальцы — темнота
        { img: 'come_door', hush: 'soft', cams: [[.5, .5, 1.0, 1.06]], black: 4.45, sfx: [['knock', .35], ['knock', 1.05], ['knock', 1.75], ['metal_groan', 2.85, { gain: .6 }], ['skin_stretch', 3.35, { gain: .55 }], ['breath_close', 3.95, { gain: .8 }]], text: '', pad: 5.0 }
      ],
      then: { over: 'ОНО ПРИШЛО', sub: 'Три странности за час остались без внимания. Смотри камеры, записывай, выключай лишний свет.', retry: '@save' }
    },
    over_stare: {
      music: 'room_tone',   // почти тишина: так «тссс» слышно у самого уха
      beats: [
        { img: 'c13_face', fx: 'static', label: 'CAM', cams: [[[.3, 2.12, -4.6], .07, 1.6, 2.4]], say: ['n22'], pad: .1 },   // помехи складываются в лицо: наезд на его лицо (точка мира), лицо — выше субтитров
        // не бросок в камеру, а «тсс»: монитор сам переключается на камеру, о которой Итан не знал, — в будке за его спиной стоит Жилец и смотрит в объектив
        { img: 'hush_cam', fx: 'cctv', label: 'CAM 00  БУДКА', cams: [[[5.22, 1.5, -1.0], 0, 1.0, 1.22]], snow: [[0, .25]], sfx: [['static', 0, { gain: .55, cut: .25, once: 1 }], ['heart_held', .35, { gain: .8 }]], text: '', pad: 2.7 },
        // спереди, с места мониторов: наклоняется к уху, палец к губам — «тссс» — и гаснет свет
        { img: 'hush_face', cams: [[.5, .5, 1.0, 1.07]], black: 2.62, sfx: [['hush', 1.3, { gain: 1 }], ['hum_stop', 2.2, { gain: .85 }], ['breath_close', 2.6, { gain: .7 }]], text: '', pad: 3.25 }
      ],
      then: { over: 'ТЫ ЗАСМОТРЕЛСЯ', sub: 'Там, где помехи, кто-то стоит. Не смотри — запиши и переключи.', retry: '@save' }
    },
    over_stare_b: {   // второй финал «засмотрелся» (по очереди с «тсс», engine.js DEATH_ALT): он идёт к объективу рывками между помехами и закрывает камеру ладонью
      music: 'room_tone',
      beats: [
        { img: 'c13_face', fx: 'static', label: 'CAM', cams: [[[.3, 2.12, -4.6], .07, 1.6, 2.4]], say: ['n22'], pad: .1 },
        { img: 'stare_lens', fx: 'cctv', label: 'CAM 07  ЭТАЖ 7', hush: 'soft', cams: [[.5, .5, 1.0, 1.0]], snow: [[.62, .78], [1.22, 1.38], [1.77, 1.93], [2.27, 2.45]], black: 3.3,
          sfx: [['static', .62, { gain: .55, cut: .18, once: 1 }], ['static', 1.22, { gain: .6, cut: .18, once: 1 }], ['static', 1.77, { gain: .65, cut: .18, once: 1 }], ['static', 2.27, { gain: .75, cut: .2, once: 1 }],
            ['skin_stretch', 2.6, { gain: .45 }], ['breath_close', 2.95, { gain: .85 }]], text: '', pad: 3.9 }
      ],
      then: { over: 'ТЫ ЗАСМОТРЕЛСЯ', sub: 'Там, где помехи, кто-то стоит. Не смотри — запиши и переключи.', retry: '@save' }
    },
    over_phone: {
      music: 'tension',
      beats: [
        { img: null, phone: { kind: 'talk', name: 'Внутренний', t0: 4 }, say: ['n23'], pad: .3 },
        // свой финал: в трубке твой же голос повторяет «Да. Это я.» — а на камере холла у стекла будки стоит второй Итан с телефоном у уха и поворачивает голову к объективу
        { img: null, phone: { kind: 'talk', name: 'Внутренний', t0: 6 }, say: ['n23m'], pad: .5 },
        // камера холла с цифровым наездом: у окна будки стоит второй Итан с телефоном у уха — напротив настоящего; голова поворачивается к объективу дальше, чем может человек
        { img: 'dbl_cam', fx: 'cctv', label: 'CAM 02  ХОЛЛ', hush: 'soft', cams: [[[5.0, 1.45, .4], .02, 1.9, 2.05], [[5.12, 1.68, 1.0], .07, 3.3, 3.6]], snow: [[3.3, 3.6]], black: 3.6,
          sfx: [['phone_crackle', .1, { gain: .5 }], ['skin_stretch', 1.75, { gain: .55 }], ['tinnitus', 2.5, { gain: .4 }], ['static', 3.3, { gain: .9, cut: .3, once: 1 }]], text: '', pad: 4.15 }
      ],
      then: { over: 'ТЫ НАЗВАЛСЯ', sub: 'Оно знает твоё имя. Ему нужно только, чтобы ты ответил.', retry: '@save' }
    },
    // ---- те же смерти в часы, когда Итана нет в будке (continuity.js; выбор — S.shift.hourDeath, engine.js shiftDie) ----
    // S6: Итан всю ночь в серверной, на корточках в круге соли рядом с Фрэнком — вместо будки камера серверной (CAM −2)
    over_k1_srv: {
      music: 'tension',
      beats: [
        { img: 'c13_face', fx: 'cctv', label: 'CAM 13', cams: [[[.3, 2.12, -4.6], .09, 1.0, 1.35]], say: ['k1a'], pad: .2 },
        { img: 'c13_name', fx: 'cctv', label: 'CAM 13', cams: [[[.3, 2.12, -4.6], .07, 1.7, 2.15]], say: ['k1b'], pad: .1 },
        // на камере серверной Итан поднимает лицо к объективу, губы сами отвечают; сигнал рвётся — на тринадцатой, где стояло оно, стоит он
        { img: 'k1_srv', fx: 'cctv', label: 'CAM −2  ПОДВАЛ', cams: [[[2.28, .85, 2.19], .02, 1.6, 1.75]], sfx: [['static', 0, { gain: .22, cut: 1.45, once: 1 }], ['tinnitus', .4, { gain: .35 }], ['static', 1.42, { gain: .85, cut: .4, once: 1 }]], snow: [[1.45, 1.8]], text: '', pad: 1.65 },
        { img: 'k1_taken', fx: 'cctv', label: 'CAM 13', hush: 'soft', cams: [[[.3, 1.62, -4.6], .03, 1.75, 2.15]], snow: [[0, .3], [2.75, 3.05]], black: 3.05,
          sfx: [['static', 0, { gain: .7, cut: .3, once: 1 }], ['sub_presence', .5, { gain: .55, cut: 2.3 }], ['static', 2.75, { gain: .95, cut: .3, once: 1 }]], text: '', pad: 3.6 }
      ],
      then: { over: 'ТЫ ПОСМОТРЕЛ ЕМУ В ЛИЦО', sub: 'Не смотри в тринадцатую камеру.', retry: '@save' }
    },
    over_come_srv: {
      music: 'tension',
      beats: [
        // камера серверной рвётся помехами одна вспышка за другой — «уже не на камерах»
        { img: 'come_srv_a', fx: 'cctv', label: 'CAM −2  ПОДВАЛ', cams: [[.5, .5, 1.0, 1.06]], say: ['n21'], snow: [[.55, .7], [1.2, 1.35], [1.85, 2.05], [2.5, 2.8]],
          sfx: [['steps_barefoot', .3, { gain: .55, pan: -.4 }], ['static', .55, { gain: .45, cut: .15, once: 1 }], ['static', 1.2, { gain: .45, cut: .15, once: 1 }], ['static', 1.85, { gain: .5, cut: .2, once: 1 }], ['static', 2.5, { gain: .55, cut: .3, once: 1 }]], pad: .55 },
        // свой финал: картинка вернулась — свечи погасли, за спиной у Итана, у самой соли, стоит оно и смотрит в объектив; наклоняется через черту — темнота
        { img: 'come_srv_b', fx: 'cctv', label: 'CAM −2  ПОДВАЛ', hush: 'soft', cams: [[[2.3, 1.2, 1.9], .04, 1.4, 1.6]], snow: [[0, .3], [2.35, 2.6]], black: 2.6,
          sfx: [['static', 0, { gain: .6, cut: .3, once: 1 }], ['sub_presence', .4, { gain: .6, cut: 2 }], ['skin_stretch', 1.55, { gain: .5 }], ['static', 2.35, { gain: .9, cut: .25, once: 1 }]], text: '', pad: 3.2 }
      ],
      then: { over: 'ОНО ПРИШЛО', sub: 'Три странности за час остались без внимания. Смотри камеры, записывай, выключай лишний свет.', retry: '@save' }
    },
    over_stare_srv: {
      music: 'room_tone',
      beats: [
        { img: 'c13_face', fx: 'static', label: 'CAM', cams: [[[.3, 2.12, -4.6], .07, 1.6, 2.4]], say: ['n22'], pad: .1 },
        // регистратор сам переключается на камеру серверной: оно стоит за спиной у Итана, наклоняется к его уху и подносит палец к губам, глядя в объектив — «тссс» — камера гаснет
        { img: 'hush_srv', fx: 'cctv', label: 'CAM −2  ПОДВАЛ', cams: [[[2.45, 1.45, 1.75], .04, 2.1, 2.5]], snow: [[0, .25]], black: 2.65,   // цифровой наезд: палец у губ и лицо Итана — крупно
          sfx: [['static', 0, { gain: .55, cut: .25, once: 1 }], ['heart_held', .35, { gain: .7 }], ['hush', 1.45, { gain: 1 }], ['static', 2.6, { gain: .7, cut: .2, once: 1 }]], text: '', pad: 3.3 }
      ],
      then: { over: 'ТЫ ЗАСМОТРЕЛСЯ', sub: 'Там, где помехи, кто-то стоит. Не смотри — запиши и переключи.', retry: '@save' }
    },
    // A1: Итан прячется под стойкой (не за столом): крупно его лицо под столешницей; на CAM 00 оно над столом тянется к нему; на камере холла — только второй
    over_k1_hide: {
      music: 'tension',
      beats: [
        { img: 'c13_face', fx: 'cctv', label: 'CAM 13', cams: [[[.3, 2.12, -4.6], .09, 1.0, 1.35]], say: ['k1a'], pad: .2 },
        { img: 'c13_name', fx: 'cctv', label: 'CAM 13', cams: [[[.3, 2.12, -4.6], .07, 1.7, 2.15]], say: ['k1b'], pad: .1 },
        { img: 'k1_hide', cams: [[.5, .46, 1.0, 1.05]], sfx: [['static', 0, { gain: .22, cut: 1.45, once: 1 }], ['tinnitus', .4, { gain: .35 }], ['static', 1.42, { gain: .85, cut: .4, once: 1 }]], snow: [[1.45, 1.8]], text: '', pad: 1.65 },
        { img: 'k1_taken', fx: 'cctv', label: 'CAM 13', hush: 'soft', cams: [[[.3, 1.62, -4.6], .03, 1.75, 2.15]], snow: [[0, .3], [2.75, 3.05]], black: 3.05,
          sfx: [['static', 0, { gain: .7, cut: .3, once: 1 }], ['sub_presence', .5, { gain: .55, cut: 2.3 }], ['static', 2.75, { gain: .95, cut: .3, once: 1 }]], text: '', pad: 3.6 }
      ],
      then: { over: 'ТЫ ПОСМОТРЕЛ ЕМУ В ЛИЦО', sub: 'Не смотри в тринадцатую камеру.', retry: '@save' }
    },
    over_stare_hide: {
      music: 'room_tone',
      beats: [
        { img: 'c13_face', fx: 'static', label: 'CAM', cams: [[[.3, 2.12, -4.6], .07, 1.6, 2.4]], say: ['n22'], pad: .1 },
        { img: 'hush_hide', fx: 'cctv', label: 'CAM 00  БУДКА', cams: [[[5.75, 1.4, -.75], .04, 1.3, 1.5]], snow: [[0, .25]], black: 2.65,
          sfx: [['static', 0, { gain: .55, cut: .25, once: 1 }], ['heart_held', .35, { gain: .7 }], ['hush', 1.45, { gain: 1 }], ['static', 2.6, { gain: .7, cut: .2, once: 1 }]], text: '', pad: 3.3 }
      ],
      then: { over: 'ТЫ ЗАСМОТРЕЛСЯ', sub: 'Там, где помехи, кто-то стоит. Не смотри — запиши и переключи.', retry: '@save' }
    },
    over_phone_hide: {
      music: 'tension',
      beats: [
        { img: null, phone: { kind: 'talk', name: 'Внутренний', t0: 4 }, say: ['n23'], pad: .3 },
        { img: null, phone: { kind: 'talk', name: 'Внутренний', t0: 6 }, say: ['n23m'], pad: .5 },
        { img: 'dbl_cam_hide', fx: 'cctv', label: 'CAM 02  ХОЛЛ', hush: 'soft', cams: [[[5.0, 1.45, .4], .02, 1.9, 2.05], [[5.12, 1.68, 1.0], .07, 3.3, 3.6]], snow: [[3.3, 3.6]], black: 3.6,
          sfx: [['phone_crackle', .1, { gain: .5 }], ['skin_stretch', 1.75, { gain: .55 }], ['tinnitus', 2.5, { gain: .4 }], ['static', 3.3, { gain: .9, cut: .3, once: 1 }]], text: '', pad: 4.15 }
      ],
      then: { over: 'ТЫ НАЗВАЛСЯ', sub: 'Оно знает твоё имя. Ему нужно только, чтобы ты ответил.', retry: '@save' }
    }
  },
  /* смена: часы ночи — камеры, пульт, журнал (engine.js, shiftGame) */
  shift: {
    // смерть в часы, когда Итана нет в будке: S6 — он в серверной с Фрэнком, A1 — под стойкой (engine.js shiftDie, tools/story/continuity.js)
    hourDeath: { S6: { over_k1: 'over_k1_srv', over_come: 'over_come_srv', over_stare: 'over_stare_srv' }, A1: { over_phone: 'over_phone_hide', over_k1: 'over_k1_hide', over_stare: 'over_stare_hide' } },
    memo: ['ПАМЯТКА ОХРАННИКУ БЦ «ГОРИЗОНТ»', '22:00 — запереть вход, лифт — в ночной режим (СТОП).', 'Каждый час — осмотр по камерам. Замечания — в журнал (кнопка «Записать»).',
      'Свет на пустых этажах — выключать с пульта.', 'Посторонних не впускать.', 'Внутренний телефон — для связи с охраной соседних зданий.'],
    frank: ['(на обороте обложки, карандашом) Помехи на камере — не засматривайся. Запиши и переключи.', '03.10  Обход. Лампа на 4-м мигает.', '05.10  Обход. Кошка у баков.', '08.10  Обход. Без происшествий.',
      '10.10  Обход. Без происшествий.', '12.10  Лифт ночью ездит сам. Техник: «датчик».', '15.10  Гаса зовут с 7-го. По имени. Голосом Марты. Марта три года как умерла.',
      '18.10  На пульте CAM 13. Такой камеры нет. Не смотри.', '20.10  НЕ ОТЗЫВАЙСЯ НА ИМЯ. НЕ ОТКРЫВАЙ ЕМУ ДВЕРЬ. САМ К НЕМУ НЕ ХОДИ — НИ НА СВЕТ, НИ В ЛИФТ.', '21.10  Мама позвала. Я ответил. Соль держит. Иду вниз.'],
    // до 02:50 (журнал перевязан, «До трёх. Весь.») — только его обычные записи с пульта; остальное — после сцены journal (флаг jread)
    frankPre: ['03.10  Обход. Лампа на 4-м мигает.', '05.10  Обход. Кошка у баков.', '08.10  Обход. Без происшествий.', '10.10  Обход. Без происшествий.', '12.10  Лифт ночью ездит сам. Техник: «датчик».',
      '(Дальше Фрэнк писал от руки — в журнале на столе. Журнал перевязан бечёвкой, на обложке: «До трёх. Весь.»)'],
    // текст реплик смены, пока нет озвучки (потом — из timing.json)
    lines: { n01: 'Двадцать два ноль-ноль. Вход заперт, лифт — в ночном режиме. Двенадцать этажей и я.', n02: 'Обход. Лестница, этажи, подвал. Фонарь, ключи — и никого.',
      n03: 'У серверной пахло воском. Из-под двери тянулась тёплая полоска света.', n04: 'Заперто. Ключа от серверной в связке не было.', n05: 'Наверное, аварийная лампа. Я пошёл наверх.',
      n06: 'Кошка у баков. Фрэнк писал про неё двадцать лет. Запишу и я.', n07: 'На десятом кто-то оставил свет. Выключу с пульта.',
      n08: '— Охрана, это опять Гас. Чайник у тебя в будке есть? Фрэнк мне всегда оставлял.', n09: '— Есть. Спускайся.', n10: '— Не, я уж тут, наверху. Тут тихо. Хорошо слышно…',
      n11: 'Что ему там слышно, я не спросил.', n12: 'Помехи. Только в углу, у двери. Как будто там кто-то стоит.', n13: 'Лифт поехал сам. На пульте — никого. Наверное, датчик.',
      n14: '— А, новенький. Держи яблоко, с дачи.', n15: '— Здание старое, всякое мерещится. Ты главное — в голову не бери.', n16: '— Спасибо. А вы давно тут?',
      n17: '— Тридцать лет. Жена тут работала, на седьмом. Теперь вот я мою.', n18: 'Половина третьего. Журнал всё ещё лежал закрытым.',
      n19: 'На пульте появилась ещё одна кнопка. Тринадцать. Минуту назад её не было.', n20: '— Итан?.. Это ты?', n21: 'Шаги. Уже не на камерах — в холле.',
      n22: 'Помехи сложились в лицо. Оно смотрело прямо в объектив.', n23: '— Да. Это я.', n24: 'Трубку я положил молча.',
      n25: 'Четыре двадцать. Фрэнк внизу, в круге. А я здесь. До шести почти два часа.', n26: '— Итан… Спустись ко мне. Тут так хорошо слышно…', n27: 'Гаса больше нет. Я видел это на тринадцатой.',
      n28: 'Снизу постучали. Три раза. Как просил Фрэнк. Только просил он об этом меня.', n29: 'У входа стояла Лили. Под дождём, без зонта. Лили спала в трёхстах километрах отсюда.',
      n30: 'Я открыл. Она вошла и улыбнулась. Слишком широко.', n31: 'Свет начал гаснуть. Этаж за этажом. Сверху вниз. Ко мне.',
      n32: 'Голос стих. Фрэнк тяжело дышал. В серверной стоял регистратор: все камеры на одном мониторе.', n33: '— Смотри камеры. Всё, что не так, — записывай. Я двадцать лет так утра дожидался.',
      n34: '— В помехи долго не смотри. Оно оттуда смотрит.', n35: '— Если я пойду на голос — держи. Что бы я ни говорил.', n36: '— Без двадцати шесть. Держимся, парень.', n37: '— Фрэнки… Открой маме… Замёрзла я тут…',
      // обучение: диспетчер Вера
      t01: "— Охрана «Горизонта», это пульт «Щит», диспетчер Вера. Первая смена? Давай быстро покажу, что у тебя на мониторах.",
      t02: "— Внизу — камеры здания. Нажми любую. Например, третью — лифты.",
      t03: "— Твоя работа — замечать, что не так. Видишь табло лифта? Тринадцатый этаж. А в здании их двенадцать.",
      t04: "— Нажми «Записать» и выбери, что это. Сейчас — «Лифт».",
      t05: "— Вот так. Записал — и оно пропало. Пропустишь три за час — будут проблемы.",
      t06: "— Слева вверху — микрофон камеры. Прыгают столбики — значит, там звук. Его тоже записывай.",
      t07: "— «Пульт» — это свет на этажах, вход и лифт. К десяти вход запри, лифт останови.",
      t08: "— В «Журнале» — памятка и записи прежнего охранника. Почитай, пока тихо.",
      t09: "— И ещё. Если на камере помехи — не засматривайся. Записал — и сразу переключай. Всё, удачной смены." },
    who: { n08: 'ГАС', n10: 'ГАС', n14: 'ГАС', n15: 'ГАС', n17: 'ГАС', n20: 'ЛИЛИ', n26: 'ГАС', n33: 'ФРЭНК', n34: 'ФРЭНК', n35: 'ФРЭНК', n36: 'ФРЭНК', n37: 'ГОЛОС', t01: 'ВЕРА', t02: 'ВЕРА', t03: 'ВЕРА', t04: 'ВЕРА', t05: 'ВЕРА', t06: 'ВЕРА', t07: 'ВЕРА', t08: 'ВЕРА', t09: 'ВЕРА' },
    // странности: камера, что видно (props → scenes.js) или слышно (snd), что сделать (fix: запись по виду — по умолчанию, свет/вход с пульта), окно в игровых минутах.
    // need: locked — только если вход заперт; nogus — только когда Гаса нет на 7-м. Помехи (box, stare) — это «он»: записать и сразу переключить.
    an: {
      light10: { cam: '10', kind: 'Свет', light: 10, fix: 'light:10', win: 25, say: 'n07' },
      light7: { cam: '07', kind: 'Свет', light: 7, fix: 'light:7', win: 25, need: 'nogus' },
      door10: { cam: '10', kind: 'Дверь', props: { door10: 1 }, win: 22 },
      door7: { cam: '07', kind: 'Дверь', props: { door7: 1 }, win: 22, need: 'nogus' },
      door01: { cam: '01', kind: 'Дверь', props: { door01: 1 }, fix: 'door', unlock: 1, need: 'locked', win: 20, notice: 'Пульт пискнул: «ВХОД — ОТКРЫТ».' },
      cat: { cam: '01', kinds: ['Другое', 'Кто-то'], props: { cat: 1 }, win: 25, say: 'n06' },
      streetOff: { cam: '01', kind: 'Свет', props: { streetOff: 1 }, win: 22 },
      wet: { cam: '02', kinds: ['Кто-то', 'Другое'], props: { wet: 1 }, win: 22 },
      hallDark: { cam: '02', kind: 'Свет', props: { hallDark: 1 }, win: 20 },
      lift: { cam: '03', kind: 'Лифт', props: { lift: '1', liftOpen: 1 }, win: 16, say: 'n13', sfx: ['ding', { gain: .5, lp: 1400 }] },   // лифт сам приехал вниз: табло «1», двери открыты, кабина светится, внутри никого
      lift13: { cam: '03', kind: 'Лифт', props: { lift13: 1 }, win: 20 },
      clockSpin: { cam: '03', kind: 'Другое', props: { clockSpin: 1 }, win: 20 },
      palmMove: { cam: '03', kind: 'Другое', props: { palmMove: 1 }, win: 25 },
      stDark: { cam: '04', kind: 'Свет', props: { stDark: 1 }, win: 20 },
      chair10: { cam: '10', kind: 'Другое', props: { chair10: 1 }, win: 22 },
      bucket7: { cam: '07', kind: 'Другое', props: { bucket7: 1 }, win: 30, need: 'nogus' },
      mOff: { cam: 'm2', kind: 'Свет', props: { mOff: 1 }, win: 20 },
      sdoor: { cam: 'm2', kind: 'Дверь', props: { sdoor: 1 }, win: 18 },
      // звуки: камера слышит — столбики MIC прыгают
      snd01: { cam: '01', kind: 'Звук', snd: ['glass_scratch', { gain: .5, lp: 2400 }, 7], win: 18 },
      whisp02: { cam: '02', kind: 'Звук', snd: ['whispers', { gain: .45, cut: 3, lp: 2200 }, 7], win: 18 },
      snd03: { cam: '03', kind: 'Звук', snd: ['metal_groan', { gain: .5, lp: 1500 }, 8], win: 18 },
      snd04: { cam: '04', kind: 'Звук', snd: ['steps_barefoot', { gain: .55, cut: 2.4, lp: 1600 }, 6], win: 18 },
      snd07: { cam: '07', kind: 'Звук', snd: ['cleaner_hum', { gain: .4, lp: 1800 }, 6], win: 20, need: 'nogus' },
      snd10: { cam: '10', kind: 'Звук', snd: ['ring', { gain: .35, lp: 1700 }, 3.6], win: 18 },
      snd_m2: { cam: 'm2', kind: 'Звук', snd: ['knock_metal', { gain: .6, lp: 1600 }, 5], win: 18 },
      knock3x: { cam: 'm2', kind: 'Звук', snd: ['knock_metal', { gain: .7, lp: 1700 }, 6, 3], win: 20, say: 'n28' },   // три удара, как просил Фрэнк
      lilyDoor: { cam: '01', kind: 'Кто-то', props: { lilyDoor: 1 }, win: 26, say: 'n29', trap: 'door', die: 'over_door' },   // открыть вход, пока она там, — смерть
      // кто-то стоит и смотрит (это не они)
      lily01: { cam: '01', kind: 'Кто-то', props: { lily01: 1 }, win: 16 },
      frank02: { cam: '02', kind: 'Кто-то', props: { frank02: 1 }, win: 16 },
      guard04: { cam: '04', kind: 'Кто-то', props: { guard04: 1 }, win: 16 },
      // сюжет
      gus7: { cam: '07', fix: 'none', win: 70, say: 'c03' },
      palm: { cam: '02', fix: 'none', props: { palm: 1 }, win: 25, say: 'c02' },
      // его помехи
      // Помехи: в ряби стоит Жилец (at — x, пол, z), пока смотришь — рывками подходит к камере до near (steps — свои 4 точки, как на лестнице; pose3 — поза у объектива вместо «тянется»); box — запасной прямоугольник
      st04: { cam: '04', kind: 'Помехи', kinds: ['Помехи', 'Кто-то'], box: [.42, .3, .13, .42], at: [-.6, 4.5, -5.5], steps: [[-.6, 4.5, -5.5], [-.1, 4.5, -5.25], [.42, 4.5, -5.05], [.8, 4.5, -4.86]], pose3: 'monster', stare: true, win: 12, say: 'n12' },
      st10: { cam: '10', kind: 'Помехи', kinds: ['Помехи', 'Кто-то'], box: [.47, .36, .08, .27], at: [-.2, 0, -9.2], near: [.15, 0, -3.3], stare: true, win: 12 },
      st_m2: { cam: 'm2', kind: 'Помехи', kinds: ['Помехи', 'Кто-то'], box: [.46, .3, .09, .38], at: [0, 0, -9.4], near: [.12, 0, -3.1], stare: true, win: 12 },
      st02: { cam: '02', kind: 'Помехи', kinds: ['Помехи', 'Кто-то'], box: [.17, .35, .07, .2], at: [-3.3, 0, -5.1], steps: [[-3.3, 0, -5.1], [-1.2, 0, -2.4], [2.2, 0, -.6], [4.4, 0, 3.4]], stare: true, win: 12 },
      st03: { cam: '03', kind: 'Помехи', kinds: ['Помехи', 'Кто-то'], box: [.6, .3, .1, .45], at: [-.55, 0, -4.4], near: [-.15, 0, -2.75], stare: true, win: 12 },
      st07: { cam: '07', kind: 'Помехи', kinds: ['Помехи', 'Кто-то'], box: [.45, .32, .08, .32], at: [-.2, 0, -9.6], near: [.15, 0, -3.1], stare: true, win: 12 },
      st01: { cam: '01', kind: 'Помехи', kinds: ['Помехи', 'Кто-то'], box: [.52, .12, .07, .3], at: [4.0, 0, 9.6], near: [1.0, 0, 8.75], stare: true, win: 12 }
    },
    segs: {
      S1: { from: '21:10', to: '22:00', spm: 3.6, cam: '02', cams: ['01', '02', '03', '04', '07', '10', 'm2'], hint: 'Камеры — внизу. Странное — «Записать». Свет, вход и лифт — на пульте.',
        events: [{ at: '21:14', an: 'light10' }, { at: '21:28', an: 'cat' }, { at: '21:46', task: { text: 'К 22:00 запереть вход и остановить лифт', need: ['door', 'lift'], by: '22:00', say: 'n01' } }],
        rnd: { pool: ['door10', 'palmMove', 'chair10', 'clockSpin', 'light7', 'snd07'], n: 2 }, next: 'dg_call' },
      S2: { st: { gus: 0, gusGone: 0, blackout: 0 }, from: '22:20', to: '23:59', spm: 2.2, cam: '02', cams: ['01', '02', '03', '04', '07', '10', 'm2'],
        events: [{ at: '22:25', state: { gus: 1 } }, { at: '22:26', an: 'gus7' }, { at: '22:40', radio: ['f01', 'f02'] }, { at: '23:05', an: 'lift' }, { at: '23:30', radio: ['n08', 'n09', 'n10', 'n11'] }],
        rnd: { pool: ['door10', 'light10', 'cat', 'door01', 'streetOff', 'lift13', 'clockSpin', 'palmMove', 'chair10', 'snd10', 'snd04', 'snd03', 'hallDark'], n: 4 }, next: 'sh_S3' },
      S3: { st: { gus: 1, gusGone: 0, blackout: 0 }, from: '00:00', to: '00:59', spm: 3.3, cam: '02', cams: ['01', '02', '03', '04', '07', '10', 'm2'],
        events: [{ at: '00:12', an: 'st04' }, { at: '00:45', an: 'palm' }],
        rnd: { pool: ['door10', 'light10', 'lift', 'st_m2', 'lily01', 'snd_m2', 'sdoor', 'mOff', 'stDark', 'snd01', 'whisp02', 'door01', 'st03'], n: 3 }, next: 'round2' },
      S4: { st: { gus: 1, gusGone: 0, blackout: 0 }, from: '01:20', to: '02:50', spm: 2.4, cam: '02', cams: ['01', '02', '03', '04', '07', '10', 'm2'],
        events: [{ at: '01:40', call: { say: 'n20', ask: '«Итан?.. Это ты?»', yes: '— Да. Это я.', hang: 'Молча положить трубку', die: 'over_phone', after: 'n24' } },
          { at: '02:00', c13: true, say: 'n19' }, { at: '02:12', an: 'st10' },
          // телефон Фрэнка (если поставил на зарядку): звонит с неизвестного номера, пропущенные, сообщение «Фрэнки, сынок…»; ответить — смерть
          { at: '02:16', if: 'fphone', sfx: ['buzz', { gain: .6 }] }, { at: '02:17', if: 'fphone', sfx: ['ring', { gain: .45, lp: 2000 }] }, { at: '02:18', if: 'fphone', sfx: ['ring', { gain: .45, lp: 2000 }] },
          { at: '02:19', if: 'fphone', sms: { from: 'Телефон Фрэнка · 11 пропущенных', text: 'Номер не определён' } },
          { at: '02:20', if: 'fphone', sms: { from: 'Телефон Фрэнка · Номер не определён', text: 'Фрэнки, сынок, спустись ко мне. Я внизу', dur: 6 }, say: 'fp01', set: 'fpcall' },
          { at: '02:25', if: 'fphone', call: { title: 'Телефон Фрэнка', sub: 'На зарядке. Номер не определён.', take: 'Ответить: «Алло?»', no: 'Сбросить', takeDie: 1, die: 'over_fmom', noSay: 'fp02' } },
          { at: '02:31', say: 'n18' }],
        rnd: { pool: ['door10', 'lift', 'st_m2', 'st02', 'st07', 'st01', 'frank02', 'guard04', 'snd_m2', 'sdoor', 'mOff', 'hallDark', 'door01', 'snd01', 'stDark', 'lily01', 'st03', 'whisp02'], n: 5 }, next: 'journal' },
      // st — что видно на камерах с начала часа: с 22:25 до трёх Гас моет седьмой (S3, S4 — и после «Продолжить»), после 03:00 его нет — брошены ведро и швабра;
      // blackout: 0 — свет гаснет только в своём часе: повтор часа после смерти и «Вернуться к выбору» начинаются со светом
      // ветка «остаться в будке»: самый трудный час. Рация голосом Гаса, стук снизу, «Лили» у входа, свет гаснет сверху вниз
      S5: { st: { gus: 0, gusGone: 1, blackout: 0 }, from: '04:20', to: '05:35', spm: 4, cam: '02', cams: ['01', '02', '03', '04', '07', '10', 'm2'],
        events: [{ at: '04:21', say: 'n25' }, { at: '04:30', an: 'knock3x' },
          { at: '04:42', call: { title: 'Рация. Канал уборщика', sub: 'Сквозь шипение — голос Гаса.', take: 'Ответить', no: 'Не отвечать', radio: 1, say: 'n26', ask: '«Итан… Спустись ко мне»', yes: '— Гас! Держись, я сейчас поднимусь!', hang: 'Выключить рацию', die: 'over_k3', after: 'n27' } },
          { at: '04:58', sms: { from: 'Лили', text: 'Итан, я внизу у входа. Открой, пожалуйста, я промокла насквозь' } }, { at: '04:59', an: 'lilyDoor' },
          { at: '05:20', say: 'n31', state: { blackout: 1 } }],
        rnd: { pool: ['st_m2', 'st02', 'st07', 'st01', 'st03', 'st10', 'frank02', 'guard04', 'snd_m2', 'sdoor', 'snd03', 'snd04', 'whisp02', 'lift13', 'clockSpin', 'chair10', 'light10'], n: 6 }, next: 'stay2' },
      // ветка «к Фрэнку»: ночь в серверной у регистратора. Фрэнк подсказывает; голос матери возвращается — держи его
      S6: { st: { gus: 0, gusGone: 1, blackout: 0 }, from: '04:30', to: '05:58', spm: 4, cam: 'm2', cams: ['01', '02', '03', '04', '07', '10', 'm2'],
        events: [{ at: '04:31', say: 'n33' }, { at: '04:40', say: 'n34' }, { at: '04:52', say: 'n35' },
          { at: '05:06', hold: { say: 'n37', dur: 7, die: 'over_k8' } }, { at: '05:34', hold: { say: 'n37', dur: 9, die: 'over_k8' } }, { at: '05:40', say: 'n36' }],
        rnd: { pool: ['st_m2', 'st02', 'st07', 'st01', 'st03', 'st10', 'frank02', 'guard04', 'lily01', 'snd_m2', 'snd01', 'whisp02', 'lift', 'lift13', 'hallDark', 'stDark', 'door10', 'palmMove'], n: 6 }, next: 'dawn' },
      // ветка «открыл → спрятался в будке»: «Лили» внутри, ходит по этажам, включает свет и ищет. Шаги у будки — не дыши; внутренний — не снимай
      A1: { st: { gus: 0, gusGone: 1, blackout: 0 }, from: '04:00', to: '05:45', spm: 3.6, cam: '02', cams: ['01', '02', '03', '04', '07', '10', 'm2'], hint: 'Ты прячешься. Смотри камеры и записывай. Шаги у будки — «НЕ ДЫШИ».',
        events: [{ at: '04:08', an: 'light7' }, { at: '04:16', an: 'light10', say: 'n40' },   // «на седьмом загорелся свет, потом на десятом» — когда загорелся и десятый (было в 04:10, а десятый — в 04:16; занята камера — реплика ждёт вместе со светом)
          { at: '04:26', hold: { say: 'n39', dur: 7, die: 'over_k5b', title: 'НЕ ДЫШИ', text: 'Шаги у самой будки. Держи кнопку, пока она не уйдёт.', btn: 'ЗАТАИТЬСЯ', btnOn: 'НЕ ДЫШУ', done: 'Шаги удаляются. Пока.', sfx: ['steps_barefoot', { gain: .6 }] } },
          { at: '04:40', sms: { from: 'Лили', text: 'Я на седьмом. Тут светло и тепло. Поднимись ко мне?' } },
          { at: '04:52', say: 'n41' }, { at: '04:52', call: { say: 'n20', ask: '«Итан?.. Это ты?»', yes: '— Да. Это я.', hang: 'Молча положить трубку', die: 'over_phone', after: 'n24' } },
          { at: '05:12', say: 'n31', state: { blackout: 1 } },
          { at: '05:24', hold: { say: 'n42', dur: 9, die: 'over_k5b', title: 'НЕ ДЫШИ', text: 'Она дышит за стеклом будки. Не отпускай.', btn: 'ЗАТАИТЬСЯ', btnOn: 'НЕ ДЫШУ', done: 'Шаги удаляются. Пока.', sfx: ['steps_barefoot', { gain: .8 }] } },
          { at: '05:40', say: 'n43' }],
        rnd: { pool: ['st02', 'st01', 'st03', 'st07', 'st10', 'st_m2', 'hallDark', 'stDark', 'lift', 'lift13', 'door7', 'door10', 'palmMove', 'chair10', 'clockSpin', 'snd03', 'snd04', 'whisp02', 'sdoor', 'mOff'], n: 6 }, next: 'hide2' }
    }
  },
  endings: { inside: 'Внутри', sister: 'Сестра', dawn: 'Рассвет', newentry: 'Новая запись' },
  overs: ['ТЫ ПОСМОТРЕЛ ЕМУ В ЛИЦО', 'ТЫ ПОШЁЛ НА СВЕТ', 'ТЫ ОТОЗВАЛСЯ', 'ОНО ОБЕРНУЛОСЬ', 'ОНО УСЛЫШАЛО', 'ТЕМНОТА ДОГНАЛА', 'ТЫ ПОШЁЛ НА ЗОВ', 'ФРЭНК УШЁЛ НА ГОЛОС', 'ТЫ ОТВЕТИЛ',
    'ОНО ПРИШЛО', 'ТЫ ЗАСМОТРЕЛСЯ', 'ТЫ НАЗВАЛСЯ', 'ТЫ ОТКРЫЛ ЕЙ', 'ТЫ ВЗЯЛ ТРУБКУ']
};
