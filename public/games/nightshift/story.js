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
          chapter: ['МОЛЧИ ДО ШЕСТИ', 'БЦ «ГОРИЗОНТ» · 21:00'], wait: 'Нажми, чтобы заступить на смену' },
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
      beats: [{ img: 'pro_offer', cams: [[.5, .5, 1.1, 1.15], [.48, .44, 1.3, 1.36]], say: ['p10', 'p11', 'p12'], pad: .4 }],
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
      then: { shift: 'S1', fresh: true }
    },

    /* ================= ЖУРНАЛ, 02:50 ================= */
    journal: {
      music: 'drone', time: '02:50',
      beats: [
        { img: 'jr_desk', fx: 'lamp', cams: [[.5, .5, 1.0, 1.06]], say: ['j01'], pad: .3, chapter: ['02:50', 'ЖУРНАЛ ФРЭНКА'] },
        { img: 'jr_desk', fx: 'lamp', cams: [[.5, .55, 1.15, 1.22]], say: ['j02'], pad: .3 },
        { img: 'jr_p12', fx: 'lamp', cams: [[.5, .5, 1.0, 1.05]], say: ['j03'], pad: .3 },
        { img: 'jr_p15', fx: 'lamp', cams: [[.5, .5, 1.0, 1.05]], say: ['j04'], pad: .3 },
        { img: 'jr_p18', fx: 'lamp', cams: [[.5, .5, 1.0, 1.05]], say: ['j05'], pad: .3 },
        { img: 'jr_p20', fx: 'lamp', cams: [[.5, .5, 1.0, 1.08]], say: ['j06'], rule: L1, pad: .6 },
        { img: 'jr_p21', fx: 'lamp', cams: [[.5, .5, 1.0, 1.06]], say: ['j07'], rule: 'МАМА ПОЗВАЛА. Я ОТВЕТИЛ.\nСОЛЬ ДЕРЖИТ. ИДУ ВНИЗ.', pad: .5 },
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
        { img: 'f7_cam_after', fx: 'cctv', label: 'CAM 07  ЭТАЖ 7', cams: [[.5, .5, 1.0, 1.08]], say: ['f06'], sfx: [['static', 0, { gain: .6, cut: 1.4 }]], heart: true, pad: .6 }
      ],
      then: { mg: 'tape', title: 'ПЕРЕМОТАЙ ЗАПИСЬ', hint: 'Тяни ползунок влево. Найди, где он ещё здесь.', found: '03:00:00. Гас стоит у двери 707…',
              img: 'f7_stop', label: 'CAM 07  ЭТАЖ 7', cam: [.5, .5, 1.25, 1.25], len: 4.6, goal: .8, stop: 3.4, clock0: [3, 0, 0], win: 'floor7b' }
    },
    floor7b: {
      music: 'tension', time: '03:02',
      beats: [
        { img: 'f7_stop', fx: 'cctv', label: 'CAM 07  ЭТАЖ 7', cams: [[.5, .5, 1.2, 1.3]], say: ['f07'], pad: .4 },
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
      then: { mg: 'wipe', title: 'КТО ТАМ?', hint: 'Стекло запотело. Протри его пальцем.', img: 'gl_face', cam: [.5, .5, 1.0, 1.0], win: 'glass2' }
    },
    glass2: {
      music: 'tension', time: '03:30',
      beats: [
        { img: 'gl_wide', fx: 'rain', cams: [[.5, .5, 1.0, 1.06]], say: ['g04'], pad: .3 },
        { img: 'gl_face', fx: 'rain', cams: [[.5, .5, 1.0, 1.06]], say: ['g05'], pad: .3 },
        { img: 'gl_wide', fx: 'rain', cams: [[.5, .5, 1.06, 1.1]], say: ['g06'], pad: .3 },
        { img: 'gl_face', fx: 'rain', cams: [[.5, .5, 1.06, 1.12]], say: ['g07'], pad: .3 },
        { img: 'entrance_empty', fx: 'static', label: 'CAM 01  ВХОД', cams: [[.5, .5, 1.0, 1.08]], say: ['g08'], pad: .4 },
        { img: null, phone: { kind: 'lock', time: '03:31', notes: [{ from: 'Лили', text: 'Не спится :( Как первая смена? Позвони утром', at: .5, ago: 'минуту назад' }] }, say: ['g09'], sfx: [['buzz', .5]], pad: .5 },
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
        { img: null, phone: { kind: 'call', name: 'Лили' }, say: ['a06'], sfx: [['ring', 0], ['buzz', 0]], pad: .3 },
        { img: 'a_beside', cams: [[.5, .5, 1.0, 1.06]], say: ['a07'], sfx: [['ring', 0, { gain: .8 }]], pad: .5 },
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
      then: { mg: 'hold', title: 'НЕ ДЫШИ', hint: 'Оно ищет на звук. Держи палец, пока шаги рядом.', dur: 11, win: 'hide2', lose: 'over_k5' }
    },
    hide2: {
      music: 'drone', time: '05:59',
      beats: [
        { img: 'h_crouch', cams: [[.5, .5, 1.0, 1.05]], say: ['h05'], pad: .4 },
        { img: 'h_face', cams: [[.5, .5, 1.05, 1.12]], say: ['h06'], sfx: [['ch_whisper', .2, { bus: 'voice', gain: .6 }]], pad: .6 },
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
        { img: null, phone: { kind: 'call', name: 'Лили' }, say: ['x05'], sfx: [['ring', 0], ['buzz', 0]], pad: .3 },
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
        { img: 'b_smile', fx: 'rain', cams: [[.5, .48, 1.0, 1.12]], say: ['b02'], pad: .5 },
        { img: 'gl_scratch', fx: 'rain', cams: [[.5, .5, 1.0, 1.06]], say: ['b03', 'b04'], sfx: [['glass_scratch', .3, { gain: .8 }]], pad: .5 },
        { img: 'b_back', fx: 'rain', cams: [[.5, .5, 1.0, 1.05]], say: ['b05'], pad: .6 },
        { img: 'b_print', fx: 'rain', cams: [[.5, .5, 1.0, 1.1]], say: ['b06'], pad: .6 },
        { img: null, phone: { kind: 'out', name: 'Лили' }, say: ['b07'], sfx: [['ringback', .2], ['ringback', 2.2], ['ringback', 4.2]], pad: .3 },
        { img: null, phone: { kind: 'talk', name: 'Лили', t0: 0 }, say: ['b08', 'b09', 'b10', 'b11'], pad: .4 },
        { img: 'e_face', cams: [[.5, .5, 1.0, 1.06]], say: ['b12'], pad: .5 }
      ],
      then: { goto: 'server' }
    },
    server: {
      music: 'tension', time: '04:10',
      beats: [
        { img: 'monitors', fx: 'flicker', cams: [[.5, .5, 1.0, 1.1]], say: ['s01'], sfx: [['static', .2, { gain: .4 }]], pad: .3 },
        { img: 'c13_gus', fx: 'cctv', label: 'CAM 13', cams: [[.5, .5, 1.0, 1.08]], say: ['s02', 's03'], sfx: [['eat_wet', .8, { gain: .4 }]], pad: .2 },
        { img: 'c13_gus_up', fx: 'cctv', label: 'CAM 13', cams: [[.5, .45, 1.3, 1.8]], say: ['s04'], sfx: [['neck_crack', 1.4]], pad: .1 },
        { img: 'booth_dark', fx: 'dark', cams: [[.5, .5, 1.0, 1.0]], text: '', pad: 1.4 },
        { img: 'srv_cam', fx: 'cctv', label: 'CAM −2  СЕРВЕРНАЯ', cams: [[.5, .5, 1.0, 1.06]], say: ['s05'], pad: .3 },
        { img: 'srv_cam', fx: 'cctv', label: 'CAM −2  СЕРВЕРНАЯ', cams: [[.58, .58, 1.5, 1.7]], say: ['s06'], pad: .3 },
        { img: 'srv_cam', fx: 'cctv', label: 'CAM −2  СЕРВЕРНАЯ', cams: [[.5, .5, 1.1, 1.14]], say: ['s07'], pad: .3 },
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
        { img: 'srv_in', cams: [[.5, .5, 1.0, 1.05]], say: ['d11', 'd12'], pad: .3 },
        { img: 'srv_in', cams: [[.52, .45, 1.2, 1.26]], say: ['d13'], pad: .4 },
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
      then: { shift: 'S6' }
    },
    dawn: {
      music: 'drone', time: '06:00',
      beats: [
        { img: 'srv_in', cams: [[.5, .5, 1.0, 1.04]], say: ['d20', 'd21'], pad: .4 },
        { img: 'st_up', fx: 'cctv', label: 'CAM 04  ЛЕСТНИЦА', cams: [[.5, .5, 1.0, 1.06]], say: ['d22'], pad: .4 },
        { img: 'x_two', fx: 'rain', cams: [[.5, .5, 1.0, 1.06]], say: ['d23'], sfx: [['tram', .8, { gain: .4 }]], pad: .4 },
        { img: 'x_two', fx: 'rain', cams: [[.52, .45, 1.2, 1.25]], say: ['d24'], pad: .3 },
        { img: 'x_no', fx: 'rain', cams: [[.5, .45, 1.0, 1.06]], say: ['d25'], pad: .3 },
        { img: 'x_two', fx: 'rain', cams: [[.5, .45, 1.25, 1.3]], say: ['d26'], pad: .4 },
        { img: 'x_smoke', fx: 'rain', cams: [[.5, .45, 1.0, 1.06]], say: ['d27'], sfx: [['lighter', .3]], pad: 1.2 },
        { img: 'x_smoke', fx: 'rain', cams: [[.5, .42, 1.1, 1.16]], say: ['d28', 'd29'], pad: .4 },
        { img: 'x_two', fx: 'rain', cams: [[.5, .5, 1.0, 1.04]], say: ['d30'], pad: .6 },
        { img: 'hall_cam', fx: 'cctv', label: 'CAM 02  ХОЛЛ  03:00', cams: [[.5, .5, 1.0, 1.04], [.36, .46, 2.2, 2.6]], say: ['d31'], pad: 1.6 }
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
        { img: null, phone: { kind: 'call', name: 'Лили' }, say: ['w04'], sfx: [['ring', 0], ['buzz', 0]], pad: .2 },
        { img: null, phone: { kind: 'call', name: 'Лили' }, say: ['w05'], sfx: [['ring', 0]], heart: true, pad: .3 }
      ],
      then: { choice: 'Звонит «Лили».', timeout: 8, def: 1, opts: [{ t: 'Ответить', go: 'over_k9' }, { t: 'Сбросить', go: 'morning' }] }
    },
    morning: {
      music: 'drone', time: '06:00',
      beats: [
        { img: 'monitors', cams: [[.5, .5, 1.0, 1.08]], say: ['w06'], sfx: [['buzz', .4], ['buzz', 1.8]], pad: .4 },
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
        { img: 'c13_face', fx: 'cctv', label: 'CAM 13', cams: [[.5, .45, 1.0, 1.6]], say: ['k1a'], pad: .2 },
        { img: 'c13_face', fx: 'cctv', label: 'CAM 13', cams: [[.5, .42, 1.8, 2.2]], say: ['k1b'], pad: .1 },
        { img: 'lunge_booth', fx: 'lunge', scare: 1.3, text: '', pad: 3.0 }
      ],
      then: { over: 'ТЫ ПОСМОТРЕЛ ЕМУ В ЛИЦО', sub: 'Не смотри в тринадцатую камеру.', retry: '@save' }
    },
    over_k2: {
      music: 'tension', time: '03:03',
      beats: [
        { img: 'corridor_lit_after', cams: [[.5, .5, 1.0, 1.12]], say: ['k2a'], sfx: [['steps', .2]], pad: .2 },
        { img: 'eat_close', cams: [[.5, .55, 1.0, 1.12]], say: ['k2b'], sfx: [['eat_wet', 0]], pad: .4 },
        { img: 'corridor_off', cams: [[.5, .5, 1.0, 1.25]], say: ['k2c'], sfx: [['click', .5], ['click', 1.0], ['click', 1.4], ['click', 1.7]], pad: .1 },
        { img: 'lunge_corridor', fx: 'lunge', scare: 1.35, text: '', pad: 3.0 }
      ],
      then: { over: 'ТЫ ПОШЁЛ НА СВЕТ', sub: 'Сам к нему не ходи — ни на свет, ни в лифт.', retry: 'floor7b' }
    },
    over_k3: {
      music: 'tension', time: '03:06',
      beats: [
        { img: 'booth_radio', cams: [[.5, .5, 1.1, 1.2]], say: ['k3a', 'k3b'], pad: .1 },
        { img: 'lunge_booth', fx: 'lunge', scare: 1.3, text: '', pad: 3.0 }
      ],
      then: { over: 'ТЫ ОТОЗВАЛСЯ', sub: 'Гас не знал твоего имени. А оно знает.', retry: 'radio' }
    },
    over_k4: {
      music: 'tension', time: '03:18',
      beats: [
        { img: 'c13_face', fx: 'cctv', label: 'CAM 13', cams: [[.5, .45, 1.4, 2.0]], say: ['k4a', 'k4b'], pad: .1 },
        { img: 'lunge_booth', fx: 'lunge', scare: 1.3, text: '', pad: 3.0 }
      ],
      then: { over: 'ОНО ОБЕРНУЛОСЬ', sub: 'Тринадцатая включилась сама — выдёргивай шнур, не смотри.', retry: 'cam13' }
    },
    over_k5: {
      music: 'tension',
      beats: [
        { img: 'h_face', fx: 'dark', cams: [[.5, .5, 1.1, 1.2]], say: ['k5a'], pad: .1 },
        { img: 'lunge_booth', fx: 'lunge', scare: 1.2, text: '', pad: 3.0 }
      ],
      then: { over: 'ОНО УСЛЫШАЛО', sub: 'Оно ищет на звук. Пока шаги рядом — не дыши.', retry: 'hide' }
    },
    over_k6: {
      music: 'tension',
      beats: [{ img: 'lunge_back', fx: 'lunge', scare: 1.2, text: '', pad: 3.0 }],
      then: { over: 'ТЕМНОТА ДОГНАЛА', sub: 'Остановишься — догонит.', retry: 'run' }
    },
    over_k7: {
      music: 'tension', time: '04:18',
      beats: [
        { img: 'bs_open', cams: [[.5, .5, 1.0, 1.06]], say: ['k7a'], sfx: [['door_metal', .1]], pad: .2 },
        { img: 'bs_no', cams: [[.5, .45, 1.0, 1.12]], say: ['k7b'], pad: .2 },
        { img: 'bs_no', fx: 'dark', cams: [[.5, .45, 1.12, 1.2]], say: ['k7c'], sfx: [['breath_close', .4]], pad: .1 },
        { img: 'lunge_back', fx: 'lunge', scare: 1.3, text: '', pad: 3.0 }
      ],
      then: { over: 'ТЫ ПОШЁЛ НА ЗОВ', sub: 'Оно умеет звать. Стучать — нет.', retry: 'basement' }
    },
    over_k8: {
      music: 'tension', time: '05:20',
      beats: [
        { img: 'srv_crawl', cams: [[.5, .5, 1.0, 1.08]], say: ['k8a'], pad: .4 },
        { img: 'srv_dark', cams: [[.5, .5, 1.1, 1.2]], say: ['k8b', 'k8c'], pad: .1 },
        { img: 'lunge_server', fx: 'lunge', scare: 1.4, text: '', pad: 3.0 }
      ],
      then: { over: 'ФРЭНК УШЁЛ НА ГОЛОС', sub: 'Держи его, пока голос не стихнет.', retry: 'knock3' }
    },
    over_k9: {
      music: 'tension', time: '05:41',
      beats: [
        { img: null, phone: { kind: 'talk', name: 'Лили', t0: 0 }, say: ['k9a'], pad: .2 },
        { img: 'hall_cam_katya', fx: 'cctv', label: 'CAM 02  ХОЛЛ', cams: [[.5, .5, 1.0, 1.1], [.47, .47, 2.2, 2.6]], say: ['k9b'], scare: 3.4, hit: ['jumpscare'], pad: 1.4 }
      ],
      then: { over: 'ТЫ ОТВЕТИЛ', sub: 'Свои до утра подождут.', retry: 'call540' }
    },
    /* ================= ОБХОДЫ И СМЕНА ================= */
    round1: {
      music: 'drone', time: '22:00',
      beats: [
        { img: 'st_down', cams: [[.5, .5, 1.0, 1.04]], say: ['n02'], sfx: [['steps', .2, { gain: .6 }]], pad: .3, chapter: ['22:00', 'ОБХОД'] },
        { img: 'bs_walk', cams: [[.5, .5, 1.0, 1.06]], say: ['n03'], pad: .3 },
        { img: 'bs_door', cams: [[.5, .5, 1.0, 1.08]], say: ['n04'], sfx: [['door_metal', .2, { gain: .35, cut: .6 }]], pad: .3 },
        { img: 'bs_door', cams: [[.5, .55, 1.1, 1.14]], say: ['n05'], pad: .6 }
      ],
      then: { shift: 'S2' }
    },
    round2: {
      music: 'drone', time: '01:00',
      beats: [
        { img: 'r2_meet', cams: [[.5, .5, 1.0, 1.05]], say: ['n14'], pad: .3, chapter: ['01:00', 'СЕДЬМОЙ ЭТАЖ'] },
        { img: 'r2_apple', cams: [[.5, .5, 1.0, 1.06]], say: ['n16', 'n17'], pad: .3 },
        { img: 'r2_talk', cams: [[.5, .5, 1.0, 1.05]], say: ['n15'], pad: .8 }
      ],
      then: { shift: 'S4' }
    },
    sh_S1: { beats: [], then: { shift: 'S1' } }, sh_S2: { beats: [], then: { shift: 'S2' } }, sh_S3: { beats: [], then: { shift: 'S3' } }, sh_S4: { beats: [], then: { shift: 'S4' } }, sh_S5: { beats: [], then: { shift: 'S5' } }, sh_S6: { beats: [], then: { shift: 'S6' } },
    over_door: {
      music: 'tension',
      beats: [
        { img: 'entrance_cam', fx: 'cctv', label: 'CAM 01  ВХОД', cams: [[.5, .5, 1.0, 1.1]], say: ['n30'], sfx: [['bolt', .1, { gain: .8 }], ['door', .8, { gain: .6 }], ['rain_burst', .9, { gain: .5 }]], pad: .3 },
        { img: 'lunge_booth', fx: 'lunge', scare: 1.3, text: '', pad: 3.0 }
      ],
      then: { over: 'ТЫ ОТКРЫЛ ЕЙ', sub: 'Лили спала в трёхстах километрах отсюда. Посторонних не впускать.', retry: '@save' }
    },
    over_come: {
      music: 'tension',
      beats: [
        { img: 'hall_cam_nearer', fx: 'cctv', label: 'CAM 02  ХОЛЛ', cams: [[.5, .5, 1.0, 1.1]], say: ['n21'], sfx: [['steps_barefoot', .3, { gain: .8 }]], pad: .2 },
        { img: 'lunge_booth', fx: 'lunge', scare: 1.3, text: '', pad: 3.0 }
      ],
      then: { over: 'ОНО ПРИШЛО', sub: 'Три странности за час остались без внимания. Смотри камеры, записывай, выключай лишний свет.', retry: '@save' }
    },
    over_stare: {
      music: 'tension',
      beats: [
        { img: 'c13_face', fx: 'static', label: 'CAM', cams: [[.5, .45, 1.3, 1.9]], say: ['n22'], pad: .1 },
        { img: 'lunge_booth', fx: 'lunge', scare: 1.3, text: '', pad: 3.0 }
      ],
      then: { over: 'ТЫ ЗАСМОТРЕЛСЯ', sub: 'Там, где помехи, кто-то стоит. Не смотри — запиши и переключи.', retry: '@save' }
    },
    over_phone: {
      music: 'tension',
      beats: [
        { img: null, phone: { kind: 'talk', name: 'Внутренний', t0: 4 }, say: ['n23'], pad: .3 },
        { img: 'hall_cam_katya', fx: 'cctv', label: 'CAM 02  ХОЛЛ', cams: [[.5, .5, 1.0, 1.1], [.47, .47, 2.2, 2.6]], scare: 2.2, hit: ['jumpscare'], text: '', pad: 1.4 }
      ],
      then: { over: 'ТЫ НАЗВАЛСЯ', sub: 'Оно знает твоё имя. Ему нужно только, чтобы ты ответил.', retry: '@save' }
    }
  },
  /* смена: часы ночи — камеры, пульт, журнал (engine.js, shiftGame) */
  shift: {
    memo: ['ПАМЯТКА ОХРАННИКУ БЦ «ГОРИЗОНТ»', '22:00 — запереть вход, лифт — в ночной режим (СТОП).', 'Каждый час — осмотр по камерам. Замечания — в журнал (кнопка «Записать»).',
      'Свет на пустых этажах — выключать с пульта.', 'Посторонних не впускать.', 'Внутренний телефон — для связи с охраной соседних зданий.'],
    frank: ['(на обороте обложки, карандашом) Помехи на камере — не засматривайся. Запиши и переключи.', '03.10  Обход. Лампа на 4-м мигает.', '05.10  Обход. Кошка у баков.', '08.10  Обход. Без происшествий.',
      '10.10  Обход. Без происшествий.', '12.10  Лифт ночью ездит сам. Техник: «датчик».', '15.10  Гаса зовут с 7-го. По имени. Голосом Марты. Марта три года как умерла.',
      '18.10  На пульте CAM 13. Такой камеры нет. Не смотри.', '20.10  НЕ ОТЗЫВАЙСЯ НА ИМЯ. НЕ ОТКРЫВАЙ ЕМУ ДВЕРЬ. САМ К НЕМУ НЕ ХОДИ — НИ НА СВЕТ, НИ В ЛИФТ.', '21.10  Мама позвала. Я ответил. Соль держит. Иду вниз.'],
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
      lift: { cam: '03', kind: 'Лифт', props: { lift: '9', liftOpen: 1 }, win: 16, say: 'n13', sfx: ['ding', { gain: .5, lp: 1400 }] },
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
      st04: { cam: '04', kind: 'Помехи', box: [.42, .3, .13, .42], stare: true, win: 12, say: 'n12' },
      st10: { cam: '10', kind: 'Помехи', box: [.47, .36, .08, .27], stare: true, win: 12 },
      st_m2: { cam: 'm2', kind: 'Помехи', box: [.46, .3, .09, .38], stare: true, win: 12 },
      st02: { cam: '02', kind: 'Помехи', box: [.17, .35, .07, .2], stare: true, win: 12 },
      st03: { cam: '03', kind: 'Помехи', box: [.6, .3, .1, .45], stare: true, win: 12 },
      st07: { cam: '07', kind: 'Помехи', box: [.45, .32, .08, .32], stare: true, win: 12 },
      st01: { cam: '01', kind: 'Помехи', box: [.52, .12, .07, .3], stare: true, win: 12 }
    },
    segs: {
      S1: { from: '21:05', to: '22:00', cam: '02', cams: ['01', '02', '03', '04', '07', '10', 'm2'], hint: 'Камеры — внизу. Странное — «Записать». Свет, вход и лифт — на пульте.',
        events: [{ at: '21:14', an: 'light10' }, { at: '21:28', an: 'cat' }, { at: '21:46', task: { text: 'К 22:00 запереть вход и остановить лифт', need: ['door', 'lift'], by: '22:00', say: 'n01' } }],
        rnd: { pool: ['door10', 'palmMove', 'chair10', 'clockSpin', 'light7', 'snd07'], n: 3 }, next: 'round1' },
      S2: { from: '22:20', to: '23:59', cam: '02', cams: ['01', '02', '03', '04', '07', '10', 'm2'],
        events: [{ at: '22:25', state: { gus: 1 } }, { at: '22:26', an: 'gus7' }, { at: '22:40', radio: ['f01', 'f02'] }, { at: '23:05', an: 'lift' }, { at: '23:30', radio: ['n08', 'n09', 'n10', 'n11'] }],
        rnd: { pool: ['door10', 'light10', 'cat', 'door01', 'streetOff', 'lift13', 'clockSpin', 'palmMove', 'chair10', 'snd10', 'snd04', 'snd03', 'hallDark'], n: 6 }, next: 'sh_S3' },
      S3: { from: '00:00', to: '00:59', cam: '02', cams: ['01', '02', '03', '04', '07', '10', 'm2'],
        events: [{ at: '00:12', an: 'st04' }, { at: '00:30', sms: { from: 'Лили', text: 'Ты как там? Не страшно одному? 😄', reply: ['Нормально. Спи давай.', 'Если честно — жутковато.'], back: 'Держись! Утром позвоню' } }, { at: '00:45', an: 'palm' }],
        rnd: { pool: ['door10', 'light10', 'lift', 'st_m2', 'lily01', 'snd_m2', 'sdoor', 'mOff', 'stDark', 'snd01', 'whisp02', 'door01', 'st03'], n: 5 }, next: 'round2' },
      S4: { from: '01:20', to: '02:50', cam: '02', cams: ['01', '02', '03', '04', '07', '10', 'm2'],
        events: [{ at: '01:40', call: { say: 'n20', ask: '«Итан?.. Это ты?»', yes: '— Да. Это я.', hang: 'Молча положить трубку', die: 'over_phone', after: 'n24' } },
          { at: '02:00', c13: true, say: 'n19' }, { at: '02:12', an: 'st10' }, { at: '02:30', say: 'n18' }],
        rnd: { pool: ['door10', 'lift', 'st_m2', 'st02', 'st07', 'st01', 'frank02', 'guard04', 'snd_m2', 'sdoor', 'mOff', 'hallDark', 'door01', 'snd01', 'stDark', 'lily01', 'st03', 'whisp02'], n: 7 }, next: 'journal' },
      // ветка «остаться в будке»: самый трудный час. Рация голосом Гаса, стук снизу, «Лили» у входа, свет гаснет сверху вниз
      S5: { from: '04:20', to: '05:35', cam: '02', cams: ['01', '02', '03', '04', '07', '10', 'm2'],
        events: [{ at: '04:21', say: 'n25' }, { at: '04:30', an: 'knock3x' },
          { at: '04:42', call: { title: 'Рация. Канал уборщика', sub: 'Сквозь шипение — голос Гаса.', take: 'Ответить', no: 'Не отвечать', radio: 1, say: 'n26', ask: '«Итан… Спустись ко мне»', yes: '— Гас! Держись, я сейчас поднимусь!', hang: 'Выключить рацию', die: 'over_k3', after: 'n27' } },
          { at: '04:58', sms: { from: 'Лили', text: 'Итан, я внизу у входа. Открой, пожалуйста, я промокла насквозь' } }, { at: '04:59', an: 'lilyDoor' },
          { at: '05:20', say: 'n31', state: { blackout: 1 } }],
        rnd: { pool: ['st_m2', 'st02', 'st07', 'st01', 'st03', 'st10', 'frank02', 'guard04', 'snd_m2', 'sdoor', 'snd03', 'snd04', 'whisp02', 'lift13', 'clockSpin', 'chair10', 'light10'], n: 8 }, next: 'stay2' },
      // ветка «к Фрэнку»: ночь в серверной у регистратора. Фрэнк подсказывает; голос матери возвращается — держи его
      S6: { from: '04:30', to: '05:58', cam: 'm2', cams: ['01', '02', '03', '04', '07', '10', 'm2'],
        events: [{ at: '04:31', say: 'n33' }, { at: '04:40', say: 'n34' }, { at: '04:52', say: 'n35' },
          { at: '05:06', hold: { say: 'n37', dur: 7, die: 'over_k8' } }, { at: '05:34', hold: { say: 'n37', dur: 9, die: 'over_k8' } }, { at: '05:40', say: 'n36' }],
        rnd: { pool: ['st_m2', 'st02', 'st07', 'st01', 'st03', 'st10', 'frank02', 'guard04', 'lily01', 'snd_m2', 'snd01', 'whisp02', 'lift', 'lift13', 'hallDark', 'stDark', 'door10', 'palmMove'], n: 8 }, next: 'dawn' }
    }
  },
  endings: { inside: 'Внутри', sister: 'Сестра', dawn: 'Рассвет', newentry: 'Новая запись' },
  overs: ['ТЫ ПОСМОТРЕЛ ЕМУ В ЛИЦО', 'ТЫ ПОШЁЛ НА СВЕТ', 'ТЫ ОТОЗВАЛСЯ', 'ОНО ОБЕРНУЛОСЬ', 'ОНО УСЛЫШАЛО', 'ТЕМНОТА ДОГНАЛА', 'ТЫ ПОШЁЛ НА ЗОВ', 'ФРЭНК УШЁЛ НА ГОЛОС', 'ТЫ ОТВЕТИЛ',
    'ОНО ПРИШЛО', 'ТЫ ЗАСМОТРЕЛСЯ', 'ТЫ НАЗВАЛСЯ', 'ТЫ ОТКРЫЛ ЕЙ']
};
