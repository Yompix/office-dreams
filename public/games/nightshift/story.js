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
      then: { goto: 'journal' }
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
      then: { camgame: 'floor7' }
    },

    /* ================= СЕДЬМОЙ ЭТАЖ, 02:59 ================= */
    floor7: {
      music: 'drone', time: '02:59',
      beats: [
        { img: 'f7_cam', fx: 'cctv', label: 'CAM 07  ЭТАЖ 7', cams: [[.5, .5, 1.0, 1.05]], say: ['f01'], pad: .3 },
        { img: 'booth_radio', cams: [[.5, .5, 1.0, 1.06]], say: ['f02'], sfx: [['click', .3, { gain: .5 }]], pad: .3 },
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
              calls: ['d18', 'd19'], dur: 12, safe: 'ШЕСТЬ УТРА', safeHint: 'Голос оборвался на полуслове.', win: 'dawn', lose: 'over_k8' }
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
        { img: 'booth_dark', cams: [[.5, .5, 1.0, 1.06]], say: ['w01'], pad: .4 },
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
      then: { over: 'ТЫ ПОСМОТРЕЛ ЕМУ В ЛИЦО', sub: 'Не смотри в тринадцатую камеру.', retry: 'cams_re' }
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
    // повтор обхода камер после смерти на CAM 13
    cams_re: { time: '02:58', beats: [], then: { camgame: 'floor7' } }
  },
  // мини-игра «обход камер» (02:58): посмотреть 3 камеры, 13-ю не трогать (журнал, 18.10)
  cams: [
    { id: 'CAM 01', name: 'ВХОД', img: 'entrance_empty', c: [.5, .5, 1.0], say: 'c01' },
    { id: 'CAM 02', name: 'ХОЛЛ', img: 'hall_cam_empty', c: [.44, .46, 2.4], say: 'c02' },
    { id: 'CAM 07', name: 'ЭТАЖ 7', img: 'f7_cam', c: [.5, .5, 1.0], say: 'c03' },
    { id: 'CAM 13', name: '', img: null, forbidden: true, over: 'over_k1' }
  ],
  endings: { inside: 'Внутри', sister: 'Сестра', dawn: 'Рассвет', newentry: 'Новая запись' },
  overs: ['ТЫ ПОСМОТРЕЛ ЕМУ В ЛИЦО', 'ТЫ ПОШЁЛ НА СВЕТ', 'ТЫ ОТОЗВАЛСЯ', 'ОНО ОБЕРНУЛОСЬ', 'ОНО УСЛЫШАЛО', 'ТЕМНОТА ДОГНАЛА', 'ТЫ ПОШЁЛ НА ЗОВ', 'ФРЭНК УШЁЛ НА ГОЛОС', 'ТЫ ОТВЕТИЛ']
};
