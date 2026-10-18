/* «Ночная смена» — сценарий интерактивного кино.
   Сцена = список кадров (beat) + что дальше (then).
   beat: img — кадр 3D-мира (scenes.js), cams — планы [cx, cy, зум от, зум до] (меняются по очереди как монтажные склейки),
         fx — 'cctv' | 'static' | 'rain' | 'flicker' | 'lamp' | 'dark', label — надпись камеры,
         say — реплики (файлы из snd/, субтитры из timing.json), text — текст без голоса (печатается),
         sfx — [[звук, секунда]], pad — пауза после, scare — секунда скримера. */
window.STORY = {
  start: 'intro',
  scenes: {
    intro: {
      music: 'drone', time: '02:55',
      beats: [
        { img: 'hall_cam', fx: 'cctv', label: 'CAM 04  ХОЛЛ', cams: [[.5, .5, 1.0, 1.06], [.44, .46, 2.6, 3.4]], say: ['l01'], pad: .6,
          chapter: ['НОЧЬ 1', 'ПОСТ ОХРАНЫ · 02:55'], wait: 'Нажми, чтобы заступить на смену' },
        { img: 'exterior', fx: 'rain', cams: [[.5, .4, 1.0, 1.08], [.52, .68, 1.7, 1.9]], say: ['l02'], sfx: [['boom', 0]], pad: .4 },
        { img: 'guard', cams: [[.5, .5, 1.0, 1.06], [.51, .43, 1.6, 1.8], [.5, .55, 1.1, 1.2]], say: ['l03', 'ch_guard'], sfx: [['boom', 0]], pad: .3 }
      ],
      then: { choice: 'Сменщик протягивает ключи.', timeout: 10, def: 0,
              opts: [{ t: 'Молча взять ключи', go: 'keys_a' }, { t: 'Спросить, почему платят втрое', go: 'keys_b' }] }
    },
    keys_a: {
      music: 'drone', time: '02:58',
      beats: [
        { img: 'guard_keys', cams: [[.5, .55, 1.2, 1.35]], text: 'Я взял ключи. Он даже не попрощался.', sfx: [['click', .2]], pad: .3 },
        { img: 'smenshik_leave', cams: [[.5, .5, 1.0, 1.1]], say: ['l04'], sfx: [['steps', .3]], pad: .5 }
      ],
      then: { goto: 'note' }
    },
    keys_b: {
      music: 'drone', time: '02:58',
      beats: [
        { img: 'guard', cams: [[.51, .43, 1.5, 1.7]], who: 'СМЕНЩИК', text: '— Не спрашивай. Просто доживи до шести.', pad: .4 },
        { img: 'smenshik_leave', cams: [[.5, .5, 1.0, 1.1]], say: ['l04'], sfx: [['steps', .3]], pad: .5 }
      ],
      then: { goto: 'note' }
    },
    note: {
      music: 'drone', time: '02:59',
      beats: [
        { img: 'desk_note', fx: 'lamp', cams: [[.5, .5, 1.0, 1.1], [.5, .55, 1.4, 1.6]], say: ['l05'], sfx: [['boom', 0]], rule: '1. В 03:00 НА 7 ЭТАЖЕ\nВКЛЮЧИТСЯ СВЕТ.\nНЕ ПОДНИМАЙСЯ', pad: .4, wait: 'Нажми на листок, чтобы прочитать' },
        { img: 'note', cams: [[.5, .5, 1.0, 1.08], [.5, .42, 1.3, 1.4]], say: ['l06'], rule: '2. ЛИФТ ПРИЕХАЛ САМ —\nНЕ ЗАХОДИ', pad: .4 },
        { img: 'note', cams: [[.5, .55, 1.3, 1.45]], say: ['l07'], rule: '3. ПОСЛЕ ПОЛУНОЧИ\nНИКОГО НЕ ВПУСКАЙ', pad: .4 },
        { img: 'desk_note', fx: 'lamp', cams: [[.55, .6, 1.5, 1.7]], say: ['l08'], rule: '4. КАМЕРУ 13\nНЕ ВКЛЮЧАЙ', pad: .5 },
        { img: 'note', cams: [[.5, .75, 1.4, 1.7]], say: ['l09'], rule: '«ТРЕТЬЕ ПРАВИЛО — ЛОЖЬ.\nВПУСТИ ЕЁ.»  — М.', sfx: [['boom', 0]], pad: .6, wait: 'Внизу что-то приписано ручкой' },
        { img: 'monitors', fx: 'flicker', cams: [[.5, .5, 1.0, 1.1], [.5, .45, 1.4, 1.6]], say: ['l10'], pad: .6 }
      ],
      then: { camgame: 'floor7' }
    },
    floor7: {
      music: 'drone', time: '03:00',
      beats: [
        { img: 'corridor_cam', fx: 'cctv', label: 'CAM 07  ЭТАЖ 7', cams: [[.5, .5, 1.0, 1.1], [.51, .49, 1.8, 2.2]], say: ['l11'], sfx: [['click', 1.2], ['click', 2.3], ['click', 3.4], ['click', 4.5]], pad: .2 }
      ],
      then: { choice: 'На седьмом горит свет. Что делаешь?', timeout: 9, def: 1,
              opts: [{ t: 'Подняться и проверить', go: 'over_floor7' }, { t: 'Сидеть и смотреть', go: 'lift' }] }
    },
    lift: {
      music: 'drone', time: '03:04',
      beats: [
        { img: 'elevator_open', cams: [[.5, .5, 1.0, 1.1], [.5, .55, 1.6, 1.9], [.5, .7, 1.3, 1.4]], say: ['l12'], sfx: [['ding', .8]], pad: .2 }
      ],
      then: { choice: 'Лифт стоит открытый. Пустой.', timeout: 8, def: 1,
              opts: [{ t: 'Зайти внутрь', go: 'over_lift' }, { t: 'Считать секунды', go: 'cam13' }] }
    },
    cam13: {
      music: 'tension', time: '03:17',
      beats: [
        { img: 'cam13_monster', fx: 'cctv', label: 'CAM 13', cams: [[.5, .45, 1.0, 1.05]], say: ['l13'], scareGap: true, pad: .2 },
        { img: 'cam13_monster', fx: 'cctv', label: 'CAM 13', cams: [[.54, .34, 2.2, 2.6]], say: ['ch_monster'], pad: .8 },
        { img: 'door_katya', fx: 'rain', cams: [[.5, .5, 1.0, 1.08], [.51, .46, 1.8, 2.0]], say: ['l14'], sfx: [['knock', 1.0]], pad: .3 },
        { img: 'katya_face', fx: 'rain', cams: [[.5, .45, 1.0, 1.1]], say: ['ch_katya'], sfx: [['knock', .2]], pad: .4 },
        { img: 'door_katya', fx: 'rain', cams: [[.51, .46, 1.4, 1.6]], say: ['l15'], pad: .4 },
        { img: 'entrance_empty', fx: 'static', label: 'CAM 01  ВХОД', cams: [[.5, .5, 1.0, 1.1]], say: ['l16'], pad: .2 },
        { img: 'entrance_empty', fx: 'static', label: 'CAM 01  ВХОД', cams: [[.6, .4, 1.6, 1.7]], say: ['ch_whisper'], pad: .6 },
        { img: 'katya_face', fx: 'rain', cams: [[.5, .5, 1.0, 1.1], [.5, .42, 1.4, 1.6]], say: ['l17'], heart: true, pad: .3 }
      ],
      then: { choice: 'Кому верить?', timeout: 12, def: null,
              opts: [{ t: 'Открыть дверь', go: 'open' }, { t: 'Не открывать', go: 'stay' }] }
    },
    open: {
      music: 'tension', time: '03:31',
      beats: [
        { img: 'door_katya', fx: 'rain', cams: [[.5, .5, 1.1, 1.3]], text: 'Я открыл.', sfx: [['door', 0]], pad: .4 },
        { img: 'katya_inside', cams: [[.5, .45, 1.0, 1.15]], text: 'Катя вцепилась в меня. Ледяная. Мокрая. Я сказал: всё, ты в безопасности.', pad: .3 },
        { img: 'katya_inside_still', cams: [[.5, .42, 1.0, 1.1]], text: 'Она перестала плакать. Сразу. Будто выключили.', sfx: [['boom', 0]], pad: .5 },
        { img: null, phone: 'call', text: 'И тут у меня зазвонил телефон. На экране — Катя.', sfx: [['ring', 0], ['buzz', 0]], pad: .6 },
        { img: 'hall_cam_empty', fx: 'cctv', label: 'CAM 04  ХОЛЛ', cams: [[.5, .5, 1.0, 1.1]], text: 'Я поднял глаза. В холле никого.', pad: .2 },
        { img: 'elevator_floors', cams: [[.5, .4, 1.1, 1.4]], floors: true, text: 'А лифт уже ехал наверх. Третий. Пятый. Седьмой.', sfx: [['ding', 3.6]], pad: .4 },
        { img: 'corridor_katya', fx: 'cctv', label: 'CAM 07  ЭТАЖ 7', cams: [[.49, .49, 1.0, 1.3]], text: 'На камере седьмого этажа она обернулась.', pad: .6 },
        { img: 'corridor_face', fx: 'cctv', label: 'CAM 07  ЭТАЖ 7', cams: [[.5, .45, 1.0, 1.15]], text: 'Это было уже не её лицо.', scare: .3, pad: .8 },
        { img: 'desk_note', cams: [[.5, .5, 1.1, 1.3]], text: '«Молодец. Теперь она внутри. Поднимайся на седьмой, я помогу. М.»', rule: '«ТЕПЕРЬ ОНА ВНУТРИ.\nПОДНИМАЙСЯ НА СЕДЬМОЙ.»  — М.', pad: .6 }
      ],
      then: { choice: 'Михалыч зовёт на седьмой. Правило — не подниматься.', timeout: 12, def: 1,
              opts: [{ t: 'Подняться на седьмой', go: 'up7' }, { t: 'Запереться в будке до шести', go: 'booth' }] }
    },
    stay: {
      music: 'tension', time: '03:31',
      beats: [
        { img: 'katya_smile', fx: 'rain', cams: [[.5, .45, 1.0, 1.15]], text: 'Я не открыл. Она перестала плакать. И улыбнулась. Слишком широко.', sfx: [['boom', 0]], pad: .4 },
        { img: 'door_smile', fx: 'rain', cams: [[.5, .5, 1.0, 1.2]], text: 'Она стучала всё сильнее. А на холодном стекле от её дыхания не оставалось пара.', sfx: [['knock', .3], ['knock', 2.4]], pad: .3 },
        { img: 'door_back', fx: 'dark', cams: [[.5, .5, 1.2, 1.0]], text: 'Потом она отошла. Спиной вперёд. Не отводя от меня глаз. Пока темнота её не съела.', pad: .6 },
        { img: null, phone: 'msg', text: 'Через минуту пришло сообщение. С её номера.', sfx: [['buzz', 0]], pad: .4 },
        { img: null, phone: 'call2', text: 'Я позвонил. Катя была дома. Она спала. Правила работают.', sfx: [['ring', 0]], pad: .6 },
        { img: 'monitors', fx: 'flicker', cams: [[.5, .45, 1.2, 1.4]], text: 'И тогда снова включилась тринадцатая камера.', sfx: [['static', 0]], pad: .3 },
        { img: 'cam13_mih', fx: 'cctv', label: 'CAM 13', cams: [[.5, .5, 1.0, 1.1]], text: 'На ней был Михалыч. Живой. В руках листок.', rule: '«ПРАВИЛА ПИШЕТ ОНО»', pad: .6 },
        { img: 'cam13_mih_monster', fx: 'cctv', label: 'CAM 13', cams: [[.6, .4, 1.2, 2.0]], text: 'А за его спиной стоял он.', scare: .4, pad: .8 }
      ],
      then: { choice: 'Михалыч жив. Но за ним стоит оно.', timeout: 12, def: 1,
              opts: [{ t: 'Спуститься к Михалычу', go: 'down13' }, { t: 'Слушаться правил', go: 'rules' }] }
    },
    over_floor7: {
      music: 'tension', time: '03:01',
      beats: [
        { img: 'corridor_lit', cams: [[.5, .5, 1.0, 1.15]], text: 'Я поднялся. Лифт открылся на седьмом.', sfx: [['ding', .2]], pad: .2 },
        { img: 'corridor_off', cams: [[.5, .5, 1.0, 1.3]], text: 'Лампы гасли одна за другой. Ко мне.', sfx: [['click', .5], ['click', 1.0], ['click', 1.4], ['click', 1.7]], pad: .1 },
        { img: 'monster_close', cams: [[.5, .45, 1.0, 1.15]], scare: 0, text: '', pad: 1.6 }
      ],
      then: { over: 'ПРАВИЛО 1 НАРУШЕНО', sub: 'Не поднимайся на седьмой.', retry: 'floor7' }
    },
    over_lift: {
      music: 'tension', time: '03:05',
      beats: [
        { img: 'elevator_inside', cams: [[.5, .45, 1.0, 1.2]], text: 'Я зашёл. Двери закрылись. Кнопок не было. Только цифра 13.', sfx: [['ding', 2.2]], pad: .2 },
        { img: 'monster_close', cams: [[.5, .45, 1.0, 1.15]], scare: 0, text: '', pad: 1.6 }
      ],
      then: { over: 'ПРАВИЛО 2 НАРУШЕНО', sub: 'Если лифт приехал сам — не заходи.', retry: 'lift' }
    },
    over_cam13: {
      music: 'tension',
      beats: [
        { img: 'cam13_monster', fx: 'cctv', label: 'CAM 13', cams: [[.54, .34, 1.0, 1.8]], text: 'Ты включил тринадцатую. Оно увидело тебя.', say: ['ch_monster'], pad: .3 },
        { img: 'monster_close', cams: [[.5, .45, 1.0, 1.15]], scare: 0, text: '', pad: 1.6 }
      ],
      then: { over: 'ПРАВИЛО 4 НАРУШЕНО', sub: 'Камеру 13 не включай.', retry: 'intro_cams' }
    },
    // ---------- ветка «открыть» → седьмой этаж
    up7: {
      music: 'tension', time: '03:40',
      beats: [
        { img: 'corridor_dark', cams: [[.5, .5, 1.0, 1.2]], text: 'Лифт я не тронул. Пошёл по лестнице. Седьмой этаж.', sfx: [['steps', .2], ['steps', 1.6]], pad: .3 },
        { img: 'corridor_713', cams: [[.5, .5, 1.0, 1.1], [.5, .55, 1.6, 1.9]], text: 'Свет горел только у одной двери. Кабинет 713.', sfx: [['click', 1.0]], pad: .3 },
        { img: 'door713', fx: 'flicker', cams: [[.5, .5, 1.0, 1.15]], text: 'Из-за двери кто-то шептал моё имя. Голосом Михалыча.', say: ['ch_whisper'], pad: .4 }
      ],
      then: { choice: 'Кто-то шепчет за дверью 713.', timeout: 10, def: 0,
              opts: [{ t: 'Открыть дверь', go: 'room713' }, { t: 'Позвать: «Михалыч?»', go: 'over_call' }] }
    },
    room713: {
      music: 'tension', time: '03:52',
      beats: [
        { img: 'server_door', cams: [[.5, .55, 1.0, 1.15]], text: 'Внутри серверная. На полу — круг из соли. В кругу сидел Михалыч. Живой.', sfx: [['door', 0]], pad: .3 },
        { img: 'server_mih_close', cams: [[.5, .45, 1.0, 1.1]], who: 'МИХАЛЫЧ', text: '— Она идёт за тобой, — прошептал он. — Держи дверь до рассвета. Что бы она ни говорила.', pad: .3 },
        { img: 'corridor_dark', cams: [[.5, .5, 1.0, 1.2]], text: 'В коридоре зацокали мокрые шаги.', say: ['ch_katya'], sfx: [['knock', 1.0]], pad: .2 }
      ],
      then: { mg: 'door', title: 'ДЕРЖИ ДВЕРЬ', hint: 'Жми часто, не давай ей открыть', dur: 13, win: 'dawn_a', lose: 'over_door' }
    },
    dawn_a: {
      music: 'drone', time: '06:00',
      beats: [
        { img: 'exterior_dawn', cams: [[.5, .4, 1.0, 1.1]], text: 'В шесть утра стук прекратился. Над городом поднималось солнце.', pad: .4 },
        { img: 'server_mih_stand', fx: 'dawn', cams: [[.5, .45, 1.0, 1.15]], text: 'Михалыч вышел из круга. Пожал мне руку. И сказал: «Спасибо. Теперь твоя смена».', pad: .5 },
        { img: 'hall_cam_nearer', fx: 'cctv', label: 'CAM 04  ХОЛЛ', cams: [[.5, .5, 1.0, 1.0], [.54, .51, 2.4, 2.8]], text: 'Следующей ночью я посмотрел на камеру в холле. Он стоял чуть ближе.', scare: 5.2, pad: 1.0 }
      ],
      then: { ending: 'dawn', title: 'ТЫ ДОЖИЛ ДО УТРА', sub: 'Михалыч свободен. А ночная смена теперь твоя. Навсегда.' }
    },
    booth: {
      music: 'tension', time: '03:40',
      beats: [
        { img: 'monitors', fx: 'flicker', cams: [[.5, .5, 1.0, 1.1], [.5, .45, 1.4, 1.6]], text: 'Я запер будку и выключил свет. До шести — два с половиной часа.', sfx: [['click', .4]], pad: .3 },
        { img: 'booth_dark', fx: 'dark', cams: [[.5, .5, 1.0, 1.2]], text: 'Шаги. Она ходила по холлу. Совсем рядом. И звала меня.', sfx: [['steps', .5]], say: ['ch_whisper'], pad: .2 }
      ],
      then: { mg: 'hold', title: 'НЕ ДЫШИ', hint: 'Держи кнопку, пока шаги не уйдут. Отпустишь раньше — услышит.', dur: 9, win: 'booth_win', lose: 'over_hold' }
    },
    booth_win: {
      music: 'drone', time: '06:00',
      beats: [
        { img: 'booth_dark', cams: [[.5, .5, 1.2, 1.1]], text: 'Шаги стихли у самой двери. Я не дышал, пока не рассвело.', pad: .4 },
        { img: 'guard_dawn', cams: [[.5, .5, 1.0, 1.08], [.51, .43, 1.7, 1.9]], text: 'В шесть пришёл сменщик. Посмотрел на меня и улыбнулся. Слишком широко.', sfx: [['steps', .2]], pad: .3 },
        { img: 'note', cams: [[.5, .55, 1.0, 1.3]], text: 'Уходя, я оставил записку для следующего охранника. Своим почерком.', rule: '«ТРЕТЬЕ ПРАВИЛО — ЛОЖЬ.\nВПУСТИ ЕЁ.»', pad: 1.0 }
      ],
      then: { ending: 'cycle', title: 'ТЫ ДОЖИЛ ДО УТРА', sub: 'Но записку «впусти её» теперь пишешь ты. Круг замкнулся.' }
    },
    // ---------- ветка «не открывать» → Михалыч
    down13: {
      music: 'tension', time: '03:40',
      beats: [
        { img: 'corridor_dark', fx: 'dark', cams: [[.5, .5, 1.0, 1.3]], text: 'Лифт я не тронул. По лестнице вниз. Минус первый. Минус второй.', sfx: [['steps', .2], ['steps', 1.8]], pad: .2 },
        { img: 'server_mih_stand', cams: [[.5, .5, 1.0, 1.15]], text: 'Комната тринадцатой камеры. Михалыч схватил меня за руку.', sfx: [['door', 0]], pad: .2 },
        { img: 'server_mih_stand', fx: 'flicker', cams: [[.5, .4, 1.3, 1.5]], who: 'МИХАЛЫЧ', text: '— Бежим, пока свет горит! Где гаснет — туда нельзя!', pad: .2 },
        { img: 'cam13_monster', fx: 'dark', cams: [[.54, .34, 1.6, 2.0]], say: ['ch_monster'], pad: .2 }
      ],
      then: { mg: 'run', title: 'БЕГИ', n: 6, win: 'escape', lose: 'over_run' }
    },
    escape: {
      music: 'drone', time: '06:00',
      beats: [
        { img: 'exterior_two', cams: [[.5, .5, 1.0, 1.1], [.5, .55, 1.3, 1.5]], text: 'Мы выбежали под дождь ровно в шесть. Солнце. Живые.', sfx: [['door', 0]], pad: .4 },
        { img: 'exterior_two', cams: [[.55, .5, 1.5, 1.7]], text: 'Михалыч закурил.', pad: .3 },
        { img: 'exterior_two', cams: [[.6, .5, 1.7, 1.9]], who: 'МИХАЛЫЧ', text: '— Завтра ты выйдешь снова. Иначе оно придёт к Кате.', pad: 1.0 }
      ],
      then: { ending: 'two', title: 'ВЫ ВЫБРАЛИСЬ', sub: 'Михалыч спасён. Но теперь вы оба привязаны к этому зданию.' }
    },
    rules: {
      music: 'drone', time: '05:40',
      beats: [
        { img: 'monitors', fx: 'flicker', cams: [[.5, .5, 1.0, 1.1]], text: 'Я решил: правила — единственное, что держит меня живым.', pad: .3 },
        { img: null, phone: 'call', text: 'В 5:40 снова позвонила Катя.', sfx: [['ring', 0], ['buzz', 0]], pad: .3 }
      ],
      then: { choice: 'Звонит Катя.', timeout: 8, def: 1, opts: [{ t: 'Ответить', go: 'over_answer' }, { t: 'Сбросить', go: 'hangup' }] }
    },
    hangup: {
      music: 'drone', time: '06:00',
      beats: [
        { img: 'hall_dawn', cams: [[.5, .5, 1.0, 1.08]], text: 'Я сбросил. В шесть пришёл сменщик. Ночь закончилась.', pad: .3 },
        { img: 'note', cams: [[.5, .55, 1.0, 1.3]], text: 'На столе лежал листок. Пятое правило. Моим почерком. Я его не писал.', rule: '5. НЕ ВЕРЬ МИХАЛЫЧУ', pad: 1.0 }
      ],
      then: { ending: 'rules', title: 'ТЫ ДОЖИЛ ДО УТРА', sub: 'Правил стало пять. Кто их пишет — ты или оно?' }
    },
    // ---------- проигрыши второго акта
    over_call: {
      music: 'tension', time: '03:44',
      beats: [
        { img: 'door713', cams: [[.5, .5, 1.1, 1.2]], text: '— Михалыч?', pad: .2 },
        { img: 'door713', fx: 'dark', cams: [[.5, .5, 1.2, 1.3]], text: 'Шёпот за дверью стих. А потом голос раздался прямо у меня за спиной.', pad: .1 },
        { img: 'corridor_monster', cams: [[.5, .45, 1.0, 1.15]], say: ['ch_monster'], pad: .1 },
        { img: 'monster_close', cams: [[.5, .45, 1.0, 1.15]], scare: 0, text: '', pad: 1.6 }
      ],
      then: { over: 'ОН СТОЯЛ ЗА СПИНОЙ', sub: 'Не зови его по имени.', retry: 'up7' }
    },
    over_door: { music: 'tension', beats: [{ img: 'corridor_face', cams: [[.5, .45, 1.1, 1.3]], scare: .2, text: '', pad: 1.8 }],
      then: { over: 'ДВЕРЬ НЕ ВЫДЕРЖАЛА', sub: 'Она была сильнее, чем казалась.', retry: 'room713' } },
    over_hold: { music: 'tension', beats: [{ img: 'monster_booth', cams: [[.5, .45, 1.0, 1.2]], scare: .1, text: '', say: ['ch_monster'], pad: .8 }],
      then: { over: 'ОНА УСЛЫШАЛА ТЕБЯ', sub: 'Иногда надо просто не дышать.', retry: 'booth' } },
    over_run: { music: 'tension', beats: [{ img: 'corridor_monster', cams: [[.5, .45, 1.0, 1.2]], scare: .1, text: '', pad: 1.8 }],
      then: { over: 'ТЕМНОТА ДОГНАЛА', sub: 'Где гаснет свет — туда нельзя.', retry: 'down13' } },
    over_answer: {
      music: 'tension', time: '05:41',
      beats: [
        { img: null, phone: 'call2', text: '', say: ['ch_whisper'], pad: .2 },
        { img: 'hall_cam_katya', fx: 'cctv', label: 'CAM 04  ХОЛЛ', cams: [[.5, .5, 1.0, 1.1], [.47, .47, 2.2, 2.6]], text: 'Голос был её. Но звучал он из холла.', scare: 2.6, pad: 1.4 }
      ],
      then: { over: 'ТЫ ОТВЕТИЛ ЕЙ', sub: 'Некоторые звонки лучше не брать.', retry: 'rules' }
    },
    intro_cams: { time: '02:59', beats: [], then: { camgame: 'floor7' } }
  },
  // мини-игра «обход камер»: посмотреть 3 камеры, 13-ю не трогать
  cams: [
    { id: 'CAM 01', name: 'ВХОД', img: 'entrance_empty', c: [.5, .5, 1.0], note: 'Пусто. Дождь.' },
    { id: 'CAM 04', name: 'ХОЛЛ', img: 'hall_cam', c: [.44, .46, 2.4], note: 'У лифта кто-то стоит?..', creep: true },
    { id: 'CAM 07', name: 'ЭТАЖ 7', img: 'corridor_dark', c: [.5, .5, 1.0], note: 'Темно. Пока.' },
    { id: 'CAM 13', name: '', img: null, forbidden: true }
  ],
  endings: { dawn: 'Рассвет', cycle: 'Записка', two: 'Двое', rules: 'Пятое правило' },
  overs: ['ПРАВИЛО 1 НАРУШЕНО', 'ПРАВИЛО 2 НАРУШЕНО', 'ПРАВИЛО 4 НАРУШЕНО', 'ОН СТОЯЛ ЗА СПИНОЙ', 'ДВЕРЬ НЕ ВЫДЕРЖАЛА', 'ОНА УСЛЫШАЛА ТЕБЯ', 'ТЕМНОТА ДОГНАЛА', 'ТЫ ОТВЕТИЛ ЕЙ']
};
