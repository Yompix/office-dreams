/* «Ночная смена» — сценарий интерактивного кино (финальная версия).
   Сцена = список кадров (beat) + что дальше (then).
   beat: img — кадр 3D-мира (scenes.js), cams — планы [cx, cy, зум от, зум до] (меняются по очереди как монтажные склейки),
         fx — 'cctv' | 'static' | 'rain' | 'flicker' | 'lamp' | 'dark' | 'dawn', label — надпись камеры,
         say — реплики (файлы из snd/, субтитры из timing.json), text — текст без голоса (печатается), who — кто говорит,
         sfx — [[звук, секунда]], pad — пауза после, scare — секунда скримера,
         creep — здесь можно на миг показать Жильца (случайный тихий испуг, решает движок).
   then: goto | choice | camgame | mg ('door' | 'hold' | 'run' | 'cord' | 'wipe' | 'torch' | 'tape') | ending | over.
   Кто есть кто: Тёмыч (ты), Сменщик, Катя (сестра), Михалыч (прошлый ночной охранник), Жилец (???) — живёт на 13-м этаже,
   которого в здании нет. Правила честные: их писали охранники, которые дожили. Записку «впусти её» подделал Жилец. */
window.STORY = {
  start: 'intro',
  scenes: {
    /* ================= ОБЩАЯ ЧАСТЬ ================= */
    intro: {
      music: 'drone', time: '02:55',
      beats: [
        { img: 'hall_cam', fx: 'cctv', label: 'CAM 04  ХОЛЛ', cams: [[.5, .5, 1.0, 1.06], [.44, .46, 2.6, 3.4]], say: ['l01'], pad: .6,
          chapter: ['НОЧЬ 1', 'БЦ «ГОРИЗОНТ» · 02:55'], wait: 'Нажми, чтобы заступить на смену' },
        { img: 'exterior', fx: 'rain', cams: [[.5, .4, 1.0, 1.08], [.52, .68, 1.7, 1.9]], say: ['l02'], sfx: [['boom', 0]], pad: .4, creep: true },
        { img: 'exterior', fx: 'rain', cams: [[.52, .6, 1.5, 1.6]], say: ['intro_2'], pad: .3 },
        { img: 'guard', cams: [[.5, .5, 1.0, 1.06], [.51, .43, 1.6, 1.8], [.5, .55, 1.1, 1.2]], say: ['l03', 'ch_guard'], sfx: [['boom', 0]], pad: .3 }
      ],
      then: { choice: 'Сменщик протягивает ключи. Рука дрожит.', timeout: 10, def: 0,
              opts: [{ t: 'Взять ключи', go: 'keys_a' }, { t: '«А почему втрое?»', go: 'keys_b' }] }
    },
    keys_a: {
      music: 'drone', time: '02:57',
      beats: [
        { img: 'guard_keys', cams: [[.5, .55, 1.2, 1.35]], say: ['keys_a_0'], sfx: [['click', .2]], pad: .3 },
        { img: 'guard', cams: [[.51, .43, 1.5, 1.6]], say: ['keys_go'], pad: .2 },
        { img: 'smenshik_leave', cams: [[.5, .5, 1.0, 1.1]], say: ['l04'], sfx: [['steps', .3]], pad: .5 }
      ],
      then: { goto: 'note' }
    },
    keys_b: {
      music: 'drone', time: '02:57',
      beats: [
        { img: 'guard', cams: [[.51, .43, 1.5, 1.7]], say: ['keys_b_0'], pad: .2 },
        { img: 'guard', cams: [[.51, .42, 1.8, 2.0]], say: ['keys_b_1'], pad: .3 },
        { img: 'guard_keys', cams: [[.5, .5, 1.3, 1.45]], say: ['keys_b_2'], sfx: [['click', .3]], pad: .3 },
        { img: 'guard', cams: [[.51, .43, 1.6, 1.7]], say: ['keys_go'], pad: .2 },
        { img: 'smenshik_leave', cams: [[.5, .5, 1.0, 1.1]], say: ['l04'], sfx: [['steps', .3]], pad: .5 }
      ],
      then: { goto: 'note' }
    },
    note: {
      music: 'drone', time: '02:59',
      beats: [
        { img: 'desk_note', fx: 'lamp', cams: [[.5, .5, 1.0, 1.1]], say: ['note_0'], sfx: [['boom', 0]], pad: .3, wait: 'Нажми на листок, чтобы прочитать' },
        { img: 'desk_note', fx: 'lamp', cams: [[.5, .55, 1.4, 1.6]], say: ['l05'], rule: '1. В 03:00 НА 7 ЭТАЖЕ\nВКЛЮЧИТСЯ СВЕТ.\nНЕ ПОДНИМАЙСЯ', pad: .4 },
        { img: 'note', cams: [[.5, .5, 1.0, 1.08], [.5, .42, 1.3, 1.4]], say: ['l06'], rule: '2. ЛИФТ ПРИЕХАЛ САМ —\nНЕ ЗАХОДИ', pad: .4 },
        { img: 'note', cams: [[.5, .55, 1.3, 1.45]], say: ['l07'], rule: '3. ПОСЛЕ ПОЛУНОЧИ\nНИКОГО НЕ ВПУСКАЙ.\nДАЖЕ СВОИХ', pad: .4 },
        { img: 'desk_note', fx: 'lamp', cams: [[.55, .6, 1.5, 1.7]], say: ['l08'], rule: '4. КАМЕРУ 13\nНЕ ВКЛЮЧАЙ', pad: .5 },
        { img: 'note', cams: [[.5, .75, 1.4, 1.7]], say: ['l09'], rule: '«ТРЕТЬЕ ПРАВИЛО — ЛОЖЬ.\nВПУСТИ ЕЁ.»  — М.', sfx: [['boom', 0]], pad: .6, wait: 'Внизу что-то приписано ручкой' },
        { img: 'monitors', fx: 'flicker', cams: [[.5, .5, 1.0, 1.1], [.5, .45, 1.4, 1.6]], say: ['l10'], sfx: [['metal_groan', 4.2]], pad: .6, creep: true },
        { img: 'note', cams: [[.52, .78, 1.7, 2.0]], say: ['note_7'], pad: .3 },
        { img: 'note', cams: [[.52, .78, 2.0, 2.2]], say: ['note_8'], sfx: [['boom', 0]], heart: true, pad: .6 }
      ],
      then: { camgame: 'floor7' }
    },
    // 02:59 уборщик домывает седьмой (его видно ещё в обходе камер); в 03:00 свет гаснет, из 707 тянутся пальцы, его утаскивают;
    // свет «включается» — то самое правило 1. Тело потом найдётся на CAM 07 (ветка Б) или прямо под ногами (если подняться)
    floor7: {
      music: 'drone', time: '02:59', sec: 50,
      beats: [
        { img: 'cleaner_cam', fx: 'cctv', label: 'CAM 07  ЭТАЖ 7', cams: [[.5, .5, 1.0, 1.06], [.47, .55, 1.7, 1.85]], say: ['fl7_0'], pad: .3 },
        { img: 'cleaner_close', cams: [[.5, .52, 1.0, 1.08]], say: ['cleaner_hum'], sfx: [['steps', 1.1, { cut: .4, gain: .25, lp: 900 }]], pad: 1.1 },
        { img: 'cleaner_stop', time: '03:00', cams: [[.5, .48, 1.0, 1.12]], say: ['floor7_0'], sfx: [['hum_stop', .2, { gain: .5 }], ['fluor_flicker', .9, { off: 1.4, cut: .5, gain: .3 }], ['fluor_flicker', 1.9, { off: 1.6, cut: .4, gain: .25 }]], pad: 2.4 },
        { img: 'cleaner_fingers', cams: [[.5, .5, 1.0, 1.15]], hush: true, sfx: [['metal_groan', .2, { cut: 1.6, gain: .25, lp: 1200 }], ['cleaner_cry_a', 1.25, { gain: .75, rev: .3 }]], text: '', pad: 2.4 },
        { img: 'cleaner_grab', fx: 'cctv', label: 'CAM 07  ЭТАЖ 7', cams: [[.5, .5, 1.0, 1.04], [.47, .57, 1.7, 1.9], [.47, .55, 1.9, 2.15]], scare: 1.02, hit: [], text: '',
          sfx: [['door_slams', .98, { off: .5, cut: .5, gain: .9 }], ['cleaner_cry_b', 1.0, { gain: .9, rev: .25 }], ['eat_wet', 1.75, { cut: .6, gain: .5 }], ['cleaner_cry_a', 2.0, { gain: .55, rev: .4, lp: 1400 }], ['eat_wet', 2.2, { gain: .85 }], ['neck_crack', 2.75, { gain: .8 }], ['eat_wet', 3.3, { gain: .8 }], ['eat_wet', 4.1, { gain: .7 }], ['door', 5.0, { cut: .5, gain: .5, lp: 900 }]], pad: 5.6 },
        { img: 'cleaner_gone', fx: 'cctv', label: 'CAM 07  ЭТАЖ 7', cams: [[.5, .5, 1.0, 1.08], [.45, .56, 1.7, 1.9]], say: ['fl7_2'], heart: true, pad: .6, rule: '1. В 03:00 НА 7 ЭТАЖЕ\nВКЛЮЧИТСЯ СВЕТ.\nНЕ ПОДНИМАЙСЯ' }
      ],
      then: { mg: 'tape', title: 'ПЕРЕМОТАЙ ЗАПИСЬ', hint: 'Тяни ползунок влево. Найди, где он ещё живой.', found: '03:00:00. Он ещё живой…',
              img: 'cleaner_grab', label: 'CAM 07  ЭТАЖ 7', cam: [.47, .54, 1.45, 1.45], len: 5.6, goal: .7, stop: 2.7, clock0: [3, 0, 0],
              sfx: [['door_slams', .98, { off: .5, cut: .5, gain: .7, lp: 2200 }], ['cleaner_cry_b', 1.0, { gain: .7, rev: .25, lp: 2200 }], ['eat_wet', 2.2, { gain: .7, lp: 2200 }], ['static', 2.6, { gain: .6, cut: .5 }]], win: 'floor7_ch' }
    },
    floor7_ch: {
      beats: [],
      then: { choice: 'Свет на седьмом горит. Уборщик пропал.', timeout: 9, def: 1,
              opts: [{ t: 'Подняться и найти его', go: 'over_floor7' }, { t: 'Остаться у мониторов', go: 'lift' }] }
    },
    // повтор после смерти на седьмом — без долгой сцены с уборщиком
    floor7_re: {
      music: 'drone', time: '03:00',
      beats: [{ img: 'cleaner_gone', fx: 'cctv', label: 'CAM 07  ЭТАЖ 7', cams: [[.5, .5, 1.0, 1.08]], say: ['fl7_2'], heart: true, pad: .4 }],
      then: { choice: 'Свет на седьмом горит. Уборщик пропал.', timeout: 9, def: 1,
              opts: [{ t: 'Подняться и найти его', go: 'over_floor7' }, { t: 'Остаться у мониторов', go: 'lift' }] }
    },
    lift: {
      music: 'drone', time: '03:04',
      beats: [
        { img: 'corridor_lit_after', fx: 'cctv', label: 'CAM 07  ЭТАЖ 7', cams: [[.5, .5, 1.0, 1.12]], say: ['l11'], hush: true, pad: .3, creep: true },
        { img: 'elevator_open', cams: [[.5, .5, 1.0, 1.1], [.5, .55, 1.6, 1.9], [.5, .7, 1.3, 1.4]], say: ['l12'], sfx: [['ding', .8]], pad: .2 },
        { img: 'elevator_13', cams: [[.5, .3, 1.4, 1.8]], say: ['lift_2'], sfx: [['tick', .5], ['tick', 1.5], ['tick', 2.5]], heart: true, pad: .4 }
      ],
      then: { choice: 'Лифт ждёт. Табло: 13.', timeout: 8, def: 1,
              opts: [{ t: 'Зайти внутрь', go: 'over_lift' }, { t: 'Считать дальше', go: 'cam13' }] }
    },
    cam13: {
      music: 'tension', time: '03:17',
      beats: [
        { img: 'elevator_open', fx: 'dark', cams: [[.5, .55, 1.3, 1.2]], say: ['cam13_0'], sfx: [['ding', 2.4]], pad: .4 },
        { img: 'cam13_turn', fx: 'cctv', label: 'CAM 13', cams: [[.5, .45, 1.0, 1.05]], say: ['l13'], scareGap: true, sfx: [['static', 0], ['neck_crack', 2.7]], pad: .2 },
        { img: 'cam13_turn', fx: 'cctv', label: 'CAM 13', cams: [[.54, .34, 2.2, 2.6]], say: ['ch_monster'], pad: .3 }
      ],
      then: { mg: 'cord', title: 'ВЫДЕРНИ ШНУР', hint: 'Тяни шнур монитора вниз. Три секунды.', dur: 3, win: 'knock', lose: 'over_cord' }
    },
    knock: {
      music: 'tension', time: '03:30',
      beats: [
        { img: 'booth_dark', cams: [[.5, .5, 1.0, 1.1]], say: ['knock_0'], sfx: [['click', 0]], pad: .4, creep: true },
        { img: 'desk_note', fx: 'lamp', cams: [[.5, .55, 1.2, 1.4]], say: ['knock_r'], rule: '3. ПОСЛЕ ПОЛУНОЧИ\nНИКОГО НЕ ВПУСКАЙ.\nДАЖЕ СВОИХ', pad: .5 },
        { img: 'booth_dark', cams: [[.5, .5, 1.0, 1.12]], say: ['knock_n1'], hush: true, sfx: [['steps_barefoot', 6.2]], pad: 1.4 },
      ],
      then: { mg: 'wipe', title: 'КТО ТАМ?', hint: 'Стекло запотело. Протри его пальцем.', img: 'katya_slam', cam: [.5, .47, 1.25, 1.25], win: 'knock2' }
    },
    knock2: {
      beats: [
        { img: 'door_katya', fx: 'rain', cams: [[.5, .5, 1.0, 1.08], [.51, .46, 1.8, 2.0]], say: ['l14'], sfx: [['knock', 1.0]], pad: .3 },
        { img: 'katya_face', fx: 'rain', cams: [[.5, .45, 1.0, 1.1]], say: ['ch_katya'], sfx: [['glass_tap', .1]], pad: .4 },
        { img: 'door_katya', fx: 'rain', cams: [[.51, .46, 1.4, 1.6]], say: ['l15'], pad: .4 },
        { img: 'door_katya', fx: 'rain', cams: [[.5, .5, 1.2, 1.3]], say: ['knock_4'], pad: .3 },
        { img: 'entrance_empty', fx: 'static', label: 'CAM 01  ВХОД', cams: [[.5, .5, 1.0, 1.1]], say: ['l16'], pad: .2 },
        { img: 'entrance_empty', fx: 'static', label: 'CAM 01  ВХОД', cams: [[.6, .4, 1.6, 1.7]], say: ['ch_whisper'], pad: .6 },
        { img: 'katya_face', fx: 'rain', cams: [[.5, .46, 1.15, 1.3]], sms: { from: 'Катя', text: 'Не спится. Ты уже на смене? Я дома, позвони утром.', at: .3, dur: 4.6 }, say: ['knock_sms'], pad: .8 },
        { img: 'katya_face', fx: 'rain', cams: [[.5, .5, 1.3, 1.5]], say: ['knock_7'], pad: .3 },
        { img: 'katya_face', fx: 'rain', cams: [[.5, .44, 1.8, 2.1]], say: ['knock_8'], heart: true, pad: .5 },
        { img: 'katya_face', fx: 'rain', cams: [[.5, .5, 1.0, 1.1], [.5, .42, 1.4, 1.6]], say: ['l17'], heart: true, pad: .3 }
      ],
      then: { choice: 'Ключ в замке.',
              opts: [{ t: 'Повернуть ключ', go: 'open' }, { t: 'Отойти от двери', go: 'stay' }] }
    },

    /* ================= ВЕТКА А: ВПУСТИЛ ================= */
    open: {
      music: 'tension', time: '03:31',
      beats: [
        { img: 'door_katya', fx: 'rain', cams: [[.5, .5, 1.1, 1.3]], say: ['open_0'], sfx: [['door', .2]], pad: .4 },
        { img: 'katya_inside', cams: [[.5, .45, 1.0, 1.15]], say: ['open_1'], pad: .3 },
        { img: 'katya_inside', cams: [[.5, .42, 1.2, 1.3]], say: ['open_2'], pad: .3 },
        { img: 'katya_inside_still', cams: [[.5, .42, 1.0, 1.1]], say: ['open_3'], sfx: [['boom', 0]], pad: .5 },
        { img: 'katya_inside_still', cams: [[.5, .4, 1.3, 1.45]], say: ['open_4'], pad: .4 },
        { img: null, phone: 'call', say: ['open_5'], sfx: [['ring', 0], ['buzz', 0]], pad: .4 },
        { img: null, phone: 'call', say: ['open_6'], sfx: [['ring', 0]], heart: true, pad: .5 },
        { img: 'hall_cam_empty', fx: 'cctv', label: 'CAM 04  ХОЛЛ', cams: [[.5, .5, 1.0, 1.1]], say: ['open_7'], pad: .2, creep: true },
        { img: 'elevator_floors', cams: [[.5, .4, 1.1, 1.4]], floors: true, say: ['open_8'], sfx: [['ding', 3.6]], pad: .4 },
        { img: 'corridor_katya', fx: 'cctv', label: 'CAM 07  ЭТАЖ 7', cams: [[.49, .49, 1.0, 1.3]], say: ['open_9'], pad: .6 },
        { img: 'corridor_face', fx: 'cctv', label: 'CAM 07  ЭТАЖ 7', cams: [[.5, .45, 1.0, 1.15]], say: ['open_10'], scare: .3, hit: ['jumpscare'], pad: .8 },
        { img: 'note', cams: [[.52, .78, 1.6, 1.9]], say: ['open_11'], rule: '«ТРЕТЬЕ ПРАВИЛО — ЛОЖЬ.\nВПУСТИ ЕЁ.»  — М.', pad: .3 },
        { img: 'note', cams: [[.5, .6, 1.2, 1.3]], say: ['open_12'], heart: true, pad: .4 },
        { img: 'monitors', fx: 'flicker', cams: [[.5, .5, 1.1, 1.3]], say: ['open_13'], sfx: [['static', 0]], heart: true, pad: .3, creep: true }
      ],
      then: { choice: 'Лифт едет вниз.', timeout: 8, def: 0,
              opts: [{ t: 'В будку, под стойку', go: 'booth' }, { t: 'К аварийному выходу', go: 'exit' }] }
    },
    booth: {
      music: 'tension', time: '03:38',
      beats: [
        { img: 'monitors', fx: 'flicker', cams: [[.5, .5, 1.0, 1.1]], say: ['booth_0'], sfx: [['click', .4]], pad: .3 },
        { img: 'booth_dark', fx: 'dark', cams: [[.5, .5, 1.0, 1.2]], say: ['booth_1'], sfx: [['ding', .1], ['steps', 1.0]], pad: .2 },
        { img: 'booth_dark', fx: 'dark', cams: [[.5, .5, 1.2, 1.25]], sms: { from: 'Катя', text: 'Ты чего не отвечаешь? Перезвони.', at: .2, dur: 3 }, say: ['booth_sms'], hush: true, pad: .5 }
      ],
      then: { goto: 'booth_hold' }
    },
    booth_hold: {
      music: 'tension', time: '03:39',
      beats: [
        { img: 'booth_dark', fx: 'dark', cams: [[.5, .5, 1.2, 1.3]], say: ['booth_hold_0'], sfx: [['steps', .4]], pad: .2 }
      ],
      then: { mg: 'hold', title: 'НЕ ДЫШИ', hint: 'Держи кнопку, пока шаги не уйдут. Отпустишь раньше — услышит.', dur: 9, win: 'booth_win', lose: 'over_hold' }
    },
    booth_win: {
      music: 'drone', time: '06:00',
      beats: [
        { img: 'booth_dark', cams: [[.5, .5, 1.2, 1.1]], say: ['booth_win_0'], pad: .4, creep: true },
        { img: 'guard_dawn_fear', cams: [[.5, .5, 1.0, 1.08]], say: ['booth_win_1'], sfx: [['door', 0], ['steps', .6]], pad: .3 },
        { img: 'guard_dawn_fear', cams: [[.51, .43, 1.6, 1.8]], say: ['booth_win_2'], pad: .3 },
        { img: 'guard_dawn_fear', cams: [[.51, .42, 1.9, 2.1]], say: ['booth_win_3'], pad: .5 },
        { img: 'booth_reflect', cams: [[.5, .5, 1.0, 1.06], [.5, .45, 1.5, 1.7]], say: ['booth_win_4'], scare: 4.2, pad: 1.0 }
      ],
      then: { ending: 'inside', title: 'СМЕНА ОКОНЧЕНА', sub: '«Ты внутри», — сказал ты ей. И не соврал.' }
    },
    exit: {
      music: 'tension', time: '03:38',
      beats: [
        { img: 'corridor_dark', cams: [[.5, .5, 1.0, 1.2]], say: ['exit_0'], sfx: [['steps', .2]], pad: .2 },
        { img: 'corridor_off', cams: [[.5, .5, 1.0, 1.3]], say: ['exit_1'], sfx: [['click', .5], ['click', 1.0], ['click', 1.4], ['click', 1.7]], pad: .1 }
      ],
      then: { mg: 'run', title: 'БЕГИ', n: 6, win: 'sister', lose: 'over_run' }
    },
    sister: {
      music: 'drone', time: '03:44',
      beats: [
        { img: 'exterior_alone', fx: 'rain', cams: [[.5, .5, 1.0, 1.1]], say: ['sister_0'], sfx: [['door', 0], ['boom', .4]], pad: .4 },
        { img: 'exterior', fx: 'rain', cams: [[.5, .35, 1.2, 1.35]], say: ['sister_1'], pad: .4, creep: true },
        { img: null, phone: 'call', say: ['sister_2'], sfx: [['ring', 0], ['buzz', 0]], pad: .3 },
        { img: null, phone: 'call2', say: ['sister_3'], pad: .3 },
        { img: null, phone: 'call2', say: ['sister_4'], heart: true, pad: .6 },
        { img: 'exterior_alone', fx: 'rain', cams: [[.5, .42, 1.4, 1.7]], say: ['sister_5'], pad: .4 },
        { img: null, phone: 'call2', say: ['sister_6'], sfx: [['door', 1.6]], pad: 1.2 }
      ],
      then: { ending: 'sister', title: 'ТЫ ВЫБРАЛСЯ', sub: 'Ей правил никто не давал.' }
    },

    /* ================= ВЕТКА Б: НЕ ВПУСТИЛ ================= */
    stay: {
      music: 'tension', time: '03:31',
      beats: [
        { img: 'katya_smile', fx: 'rain', cams: [[.5, .45, 1.0, 1.15]], say: ['stay_0'], sfx: [['boom', 0]], pad: .4 },
        { img: 'katya_smile', fx: 'rain', cams: [[.5, .5, 1.4, 1.6]], say: ['stay_1'], pad: .4 },
        { img: 'door_smile', fx: 'rain', cams: [[.5, .5, 1.0, 1.2]], say: ['stay_2'], sfx: [['knock', .3], ['knock', 1.1], ['knock', 1.8], ['knock', 2.4]], pad: .3 },
        { img: 'door_smile', fx: 'rain', cams: [[.51, .46, 1.6, 1.8]], say: ['stay_3'], pad: .4 },
        { img: 'door_back', fx: 'dark', cams: [[.5, .5, 1.2, 1.0]], say: ['stay_4'], pad: .6 },
        { img: 'door_back', fx: 'dark', cams: [[.5, .5, 1.0, 1.12]], say: ['stay_n1'], pad: .6 },
        { img: null, phone: 'call2', say: ['stay_5'], sfx: [['ring', 0]], pad: .3 },
        { img: null, phone: 'call2', say: ['stay_6'], pad: .3 },
        { img: null, phone: 'call2', say: ['stay_7'], pad: .2 },
        { img: null, phone: 'call2', say: ['stay_8'], pad: .3 },
        { img: null, phone: 'call2', say: ['stay_9'], pad: .4 },
        { img: 'monitors', fx: 'flicker', cams: [[.5, .5, 1.0, 1.15]], say: ['stay_10'], pad: .5, creep: true }
      ],
      then: { goto: 'cam13b' }
    },
    cam13b: {
      music: 'tension', time: '04:10',
      beats: [
        { img: 'monitors', fx: 'flicker', cams: [[.5, .45, 1.2, 1.4]], say: ['cam13b_0'], sfx: [['static', 0]], pad: .3 },
        { img: 'cam7_eat', fx: 'cctv', label: 'CAM 07  ЭТАЖ 7', cams: [[.5, .6, 1.0, 1.3]], say: ['eat_1'], sfx: [['eat_wet', .6]], pad: .3 },
        { img: 'cam7_eat', fx: 'cctv', label: 'CAM 07  ЭТАЖ 7', cams: [[.5, .62, 1.6, 2.3]], say: ['eat_2'], sfx: [['eat_wet', .2], ['neck_crack', 3.6]], pad: 1.4 },
        { img: 'cam13_mih', fx: 'cctv', label: 'CAM 13', cams: [[.5, .5, 1.0, 1.1]], say: ['cam13b_1'], pad: .3 },
        { img: 'cam13_mih', fx: 'cctv', label: 'CAM 13', cams: [[.5, .45, 1.5, 1.7]], say: ['cam13b_2'],
          rule: '«ПОМОГИ. МИНУС 2.\nСТУЧИ ТРИЖДЫ»', pad: .6 },
        { img: 'cam13_mih_monster', fx: 'cctv', label: 'CAM 13', cams: [[.6, .4, 1.2, 2.0]], say: ['cam13b_3'], scare: 3.4, hit: ['glass_slam'], pad: .8 }
      ],
      then: { choice: 'Минус второй. Стучать трижды.', timeout: 12, def: 1,
              opts: [{ t: 'Спуститься', go: 'down' }, { t: 'Остаться на посту', go: 'rules' }] }
    },
    down: {
      music: 'tension', time: '04:14',
      beats: [
        { img: 'corridor_dark', fx: 'dark', cams: [[.5, .5, 1.0, 1.3]], say: ['down_0'], sfx: [['steps', .2], ['steps', 1.8]], pad: .2 },
        { img: 'corridor_dark', cams: [[.5, .5, 1.3, 1.5]], say: ['down_1'], sfx: [['click', .8]], pad: .3 }
      ],
      then: { mg: 'torch', title: 'ФОНАРИК', hint: 'Веди пальцем — свети. В конце коридора что-то шуршит.', hint2: 'Никого. Дверь серверной — там же, в конце.', found: 'СЕРВЕРНАЯ −2',
              img: 'torch_corr_mon', img2: 'torch_corr', img3: 'torch_corr_near', mon: [.5, .47], goal: [.5, .455], near: [.5, .4], win: 'basement' }
    },
    basement: {
      music: 'tension', time: '04:16',
      beats: [
        { img: 'basement_door', fx: 'flicker', cams: [[.5, .5, 1.0, 1.15]], say: ['basement_0'], pad: .3 },
        { img: 'basement_door', cams: [[.5, .45, 1.3, 1.45]], say: ['basement_1'], pad: .4 },
        { img: 'basement_door', cams: [[.5, .5, 1.5, 1.6]], say: ['basement_2'], heart: true, pad: .3 },
        { img: 'basement_door', cams: [[.5, .5, 1.6, 1.8]], say: ['basement_3'], heart: true, pad: .4 }
      ],
      then: { choice: '«Заходи, открыто».', timeout: 10, def: 1,
              opts: [{ t: 'Толкнуть дверь', go: 'over_voice' }, { t: 'Постучать трижды', go: 'knock3' }] }
    },
    knock3: {
      music: 'tension', time: '04:17',
      beats: [
        { img: 'basement_door', cams: [[.5, .5, 1.2, 1.3]], say: ['knock3_0'], sfx: [['knock', .5], ['knock', 1.2], ['knock', 1.9]], pad: .4 },
        { img: 'basement_door', fx: 'dark', cams: [[.5, .5, 1.3, 1.2]], say: ['knock3_1'], sfx: [['door', .9]], pad: .3 },
        { img: 'server_mih_stand', cams: [[.5, .45, 1.0, 1.15]], say: ['knock3_2'], pad: .2 },
        { img: 'server_mih_stand', cams: [[.5, .45, 1.2, 1.3]], say: ['knock3_3'], pad: .2 },
        { img: 'server_mih_stand', cams: [[.5, .4, 1.4, 1.6]], say: ['knock3_4'], pad: .4, creep: true },
        { img: 'server_mih_close', cams: [[.5, .45, 1.0, 1.1]], say: ['knock3_5'], pad: .3 },
        { img: 'server_mih_close', cams: [[.5, .42, 1.2, 1.3]], say: ['knock3_6'], pad: .3 },
        { img: 'server_mih_close', cams: [[.5, .4, 1.3, 1.4]], say: ['knock3_7'], pad: .3 },
        { img: 'server_mih', cams: [[.5, .5, 1.0, 1.1]], say: ['knock3_8'], pad: .3 },
        { img: 'server_door', cams: [[.6, .5, 1.0, 1.2]], text: 'За дверью зашлёпали мокрые шаги. А потом заплакала Катя.', say: ['ch_katya'], sfx: [['steps_barefoot', 0]], pad: .2 }
      ],
      then: { goto: 'hold_door' }
    },
    hold_door: {
      music: 'tension', time: '04:20',
      beats: [
        { img: 'server_door', fx: 'flicker', cams: [[.65, .5, 1.2, 1.4]], say: ['hold_door_0'], sfx: [['door_slams', .1]], pad: .1 }
      ],
      then: { mg: 'door', title: 'ДЕРЖИ ДВЕРЬ', hint: 'Жми часто. До шести он будет ломиться.', dur: 13, win: 'dawn', lose: 'over_door' }
    },
    dawn: {
      music: 'drone', time: '06:00',
      beats: [
        { img: 'server_door', cams: [[.5, .5, 1.1, 1.0]], say: ['dawn_0'], pad: .5 },
        { img: 'server_mih_stand', cams: [[.5, .45, 1.0, 1.15]], say: ['dawn_1'], pad: .3 },
        { img: 'exterior_two', fx: 'rain', cams: [[.5, .5, 1.0, 1.1]], say: ['dawn_2'], sfx: [['door', 0]], pad: .4 },
        { img: 'exterior_two', fx: 'rain', cams: [[.55, .5, 1.4, 1.6]], say: ['dawn_3'], pad: .3 },
        { img: 'exterior_two', fx: 'rain', cams: [[.6, .5, 1.7, 1.9]], say: ['dawn_4'], pad: .3 },
        { img: 'exterior_two', fx: 'rain', cams: [[.62, .48, 2.0, 2.2]], say: ['dawn_5'], pad: .8 },
        { img: 'exterior_two', fx: 'rain', cams: [[.5, .5, 1.0, 1.12]], say: ['dawn_n1'], pad: .6 },
        { img: 'hall_cam', fx: 'cctv', label: 'CAM 04  ХОЛЛ', cams: [[.5, .5, 1.0, 1.06], [.44, .46, 2.6, 3.4]], say: ['l01'], pad: 1.0 }
      ],
      then: { ending: 'dawn', title: 'ТЫ ДОЖИЛ ДО УТРА', sub: 'Михалыч ушёл и не вернулся. Ты — вернулся. А у лифта каждую ночь стоят чуть ближе.' }
    },
    rules: {
      music: 'drone', time: '04:12',
      beats: [
        { img: 'monitors', fx: 'flicker', cams: [[.5, .5, 1.0, 1.1]], say: ['rules_0'], pad: .3 },
        { img: 'booth_dark', cams: [[.5, .5, 1.0, 1.15]], say: ['rules_1'], pad: .3, creep: true },
        { img: 'monitors', cams: [[.5, .5, 1.2, 1.3]], say: ['rules_2'], pad: .4 }
      ],
      then: { goto: 'call540' }
    },
    call540: {
      music: 'tension', time: '05:40',
      beats: [
        { img: null, phone: 'call', say: ['call540_0'], sfx: [['ring', 0], ['buzz', 0]], pad: .3 },
        { img: null, phone: 'call', say: ['call540_1'], sfx: [['ring', 0]], heart: true, pad: .3 }
      ],
      then: { choice: 'Звонит «Катя».', timeout: 8, def: 1, opts: [{ t: 'Ответить', go: 'over_answer' }, { t: 'Сбросить', go: 'hangup' }] }
    },
    hangup: {
      music: 'drone', time: '06:00',
      beats: [
        { img: 'monitors', cams: [[.5, .5, 1.0, 1.1]], say: ['hangup_0'], sfx: [['buzz', .4], ['buzz', 1.6]], pad: .3, creep: true },
        { img: 'hall_dawn', cams: [[.5, .5, 1.0, 1.08]], say: ['hangup_1'], sfx: [['steps', .3]], pad: .3 },
        { img: 'desk_note', cams: [[.5, .5, 1.0, 1.2]], say: ['hangup_2'], pad: .3 },
        { img: 'note', cams: [[.5, .55, 1.0, 1.3]], say: ['hangup_3'], rule: '5. НЕ ИЩИ МИХАЛЫЧА', pad: .5 },
        { img: 'note', cams: [[.5, .55, 1.3, 1.5]], say: ['hangup_4'], pad: 1.0 }
      ],
      then: { ending: 'rules', title: 'ПРАВИЛА СОБЛЮДЕНЫ', sub: 'Михалыч остался внизу. А правила теперь пишешь ты.' }
    },

    /* ================= СМЕРТИ ================= */
    over_floor7: {
      music: 'tension', time: '03:01',
      beats: [
        { img: 'corridor_lit_after', cams: [[.5, .5, 1.0, 1.15]], say: ['over_floor7_0'], sfx: [['steps', .2]], pad: .2 },
        { img: 'eat_close', cams: [[.5, .55, 1.0, 1.15]], say: ['eat_3'], sfx: [['eat_wet', 0], ['neck_crack', 2.1]], pad: .8 },
        { img: 'corridor_off', cams: [[.5, .5, 1.0, 1.3]], say: ['over_floor7_1'], sfx: [['click', .5], ['click', 1.0], ['click', 1.4], ['click', 1.7]], pad: .1 },
        { img: 'lunge_corridor', fx: 'lunge', scare: 1.35, text: '', pad: 3.0 }
      ],
      then: { over: 'ПРАВИЛО 1 НАРУШЕНО', sub: 'В 03:00 на седьмом включится свет. Не поднимайся.', retry: 'floor7_re' }
    },
    over_lift: {
      music: 'tension', time: '03:05',
      beats: [
        { img: 'elevator_inside', cams: [[.5, .45, 1.0, 1.2]], say: ['over_lift_0'], sfx: [['ding', 2.2]], pad: .2 },
        { img: 'elevator_inside', cams: [[.5, .3, 1.3, 1.6]], say: ['over_lift_1'], pad: .1 },
        { img: 'lunge_lift', fx: 'lunge', scare: 1.4, text: '', pad: 3.0 }
      ],
      then: { over: 'ПРАВИЛО 2 НАРУШЕНО', sub: 'Если лифт приехал сам — не заходи.', retry: 'lift' }
    },
    over_cam13: {
      music: 'tension',
      beats: [
        { img: 'cam13_monster', fx: 'cctv', label: 'CAM 13', cams: [[.54, .34, 1.0, 1.8]], text: 'Я включил тринадцатую. Он уже смотрел в камеру. Ждал.', say: ['ch_monster'], pad: .3 },
        { img: 'lunge_booth', fx: 'lunge', scare: 1.3, text: '', pad: 3.0 }
      ],
      then: { over: 'ПРАВИЛО 4 НАРУШЕНО', sub: 'Камеру 13 не включай.', retry: 'intro_cams' }
    },
    over_cord: {
      music: 'tension', time: '03:17',
      beats: [
        { img: 'cam13_monster', fx: 'cctv', label: 'CAM 13', cams: [[.54, .34, 2.0, 2.4]], say: ['over_cord_0'], pad: .2 },
        { img: 'booth_dark', fx: 'dark', cams: [[.5, .5, 1.0, 1.2]], say: ['over_cord_1'], pad: .1 },
        { img: 'lunge_booth', fx: 'lunge', scare: 1.3, text: '', pad: 3.0 }
      ],
      then: { over: 'ОНО ТЕБЯ УВИДЕЛО', sub: 'Если тринадцатая включилась сама — выдёргивай шнур.', retry: 'cam13' }
    },
    over_hold: {
      music: 'tension',
      beats: [{ img: 'booth_dark', fx: 'dark', cams: [[.5, .5, 1.0, 1.1]], say: ['ch_monster'], pad: .1 }, { img: 'lunge_booth', fx: 'lunge', scare: 1.3, text: '', pad: 3.0 }],
      then: { over: 'ОНО УСЛЫШАЛО ТЕБЯ', sub: 'Пока шаги рядом — не дыши.', retry: 'booth_hold' }
    },
    over_run: {
      music: 'tension',
      beats: [{ img: 'lunge_back', fx: 'lunge', scare: 1.2, text: '', pad: 3.0 }],
      then: { over: 'ТЕМНОТА ДОГНАЛА', sub: 'Не оглядывайся. Беги туда, где ещё светло.', retry: 'exit' }
    },
    over_voice: {
      music: 'tension', time: '04:17',
      beats: [
        { img: 'basement_door', cams: [[.5, .5, 1.2, 1.4]], say: ['over_voice_0'], sfx: [['door', .2]], pad: .2 },
        { img: 'server_door', cams: [[.4, .5, 1.0, 1.2]], say: ['over_voice_1'], pad: .2 },
        { img: 'server_door', fx: 'dark', cams: [[.4, .5, 1.2, 1.3]], say: ['over_voice_2'], pad: .1 },
        { img: 'lunge_server', fx: 'lunge', scare: 1.4, text: '', pad: 3.0 }
      ],
      then: { over: 'ЗА ДВЕРЬЮ БЫЛО ОНО', sub: 'Он умеет звать. Стучать — нет.', retry: 'basement' }
    },
    over_door: {
      music: 'tension',
      beats: [
        { img: 'server_door', fx: 'dark', cams: [[.65, .5, 1.2, 1.5]], say: ['over_door_0'], sfx: [['door_slams', 0], ['door', 1.2]], pad: .1 },
        { img: 'lunge_server', fx: 'lunge', scare: 1.4, text: '', pad: 3.0 }
      ],
      then: { over: 'ЗАСОВ НЕ ВЫДЕРЖАЛ', sub: 'Держи дверь до рассвета. Не сбавляй.', retry: 'hold_door' }
    },
    over_answer: {
      music: 'tension', time: '05:41',
      beats: [
        { img: null, phone: 'call2', say: ['over_answer_0'], pad: .2 },
        { img: 'hall_cam_katya', fx: 'cctv', label: 'CAM 04  ХОЛЛ', cams: [[.5, .5, 1.0, 1.1], [.47, .47, 2.2, 2.6]], say: ['over_answer_1'], scare: 3.6, hit: ['jumpscare'], pad: 1.4 }
      ],
      then: { over: 'ТЫ ОТВЕТИЛ', sub: 'Ответить — значит впустить.', retry: 'call540' }
    },
    // повтор обхода камер после смерти на CAM 13
    intro_cams: { time: '02:59', beats: [], then: { camgame: 'floor7' } }
  },
  // мини-игра «обход камер»: посмотреть 3 камеры, 13-ю не трогать
  cams: [
    { id: 'CAM 01', name: 'ВХОД', img: 'entrance_empty', c: [.5, .5, 1.0], note: 'Пусто. Дождь стеной. На стекле — чей-то отпечаток ладони?.. Нет, капли.' },
    { id: 'CAM 04', name: 'ХОЛЛ', img: 'hall_cam', c: [.44, .46, 2.4], note: 'У лифта кто-то стоит?.. Нет. Показалось.', creep: true },
    { id: 'CAM 07', name: 'ЭТАЖ 7', img: 'cleaner_cam', c: [.5, .5, 1.0], note: 'Уборщик домывает седьмой. Хоть кто-то живой в здании.' },
    { id: 'CAM 13', name: '', img: null, forbidden: true }
  ],
  endings: { dawn: 'Рассвет', rules: 'Пятое правило', inside: 'Внутри', sister: 'Сестра' },
  overs: ['ПРАВИЛО 1 НАРУШЕНО', 'ПРАВИЛО 2 НАРУШЕНО', 'ПРАВИЛО 4 НАРУШЕНО', 'ОНО ТЕБЯ УВИДЕЛО', 'ОНО УСЛЫШАЛО ТЕБЯ', 'ТЕМНОТА ДОГНАЛА', 'ЗА ДВЕРЬЮ БЫЛО ОНО', 'ТЫ ОТВЕТИЛ', 'ЗАСОВ НЕ ВЫДЕРЖАЛ']
};
