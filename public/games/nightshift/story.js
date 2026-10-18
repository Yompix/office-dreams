/* «Ночная смена» — сценарий интерактивного кино (финальная версия).
   Сцена = список кадров (beat) + что дальше (then).
   beat: img — кадр 3D-мира (scenes.js), cams — планы [cx, cy, зум от, зум до] (меняются по очереди как монтажные склейки),
         fx — 'cctv' | 'static' | 'rain' | 'flicker' | 'lamp' | 'dark' | 'dawn', label — надпись камеры,
         say — реплики (файлы из snd/, субтитры из timing.json), text — текст без голоса (печатается), who — кто говорит,
         sfx — [[звук, секунда]], pad — пауза после, scare — секунда скримера,
         creep — здесь можно на миг показать Жильца (случайный тихий испуг, решает движок).
   then: goto | choice | camgame | mg ('door' | 'hold' | 'run' | 'cord') | ending | over.
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
        { img: 'exterior', fx: 'rain', cams: [[.52, .6, 1.5, 1.6]], text: 'Деньги нужны были на мамино лечение. Так что я бы не спросил, даже если б знал.', pad: .3 },
        { img: 'guard', cams: [[.5, .5, 1.0, 1.06], [.51, .43, 1.6, 1.8], [.5, .55, 1.1, 1.2]], say: ['l03', 'ch_guard'], sfx: [['boom', 0]], pad: .3 }
      ],
      then: { choice: 'Сменщик протягивает ключи. Рука у него дрожит.', timeout: 10, def: 0,
              opts: [{ t: 'Молча взять ключи', go: 'keys_a' }, { t: 'Спросить, почему платят втрое', go: 'keys_b' }] }
    },
    keys_a: {
      music: 'drone', time: '02:57',
      beats: [
        { img: 'guard_keys', cams: [[.5, .55, 1.2, 1.35]], text: 'Я взял ключи. Тёплые, мокрые. Он сжимал их в кулаке всю дорогу.', sfx: [['click', .2]], pad: .3 },
        { img: 'smenshik_leave', cams: [[.5, .5, 1.0, 1.1]], say: ['l04'], sfx: [['steps', .3]], pad: .5 }
      ],
      then: { goto: 'note' }
    },
    keys_b: {
      music: 'drone', time: '02:57',
      beats: [
        { img: 'guard', cams: [[.51, .43, 1.5, 1.7]], text: '— А почему втрое-то?', pad: .2 },
        { img: 'guard', cams: [[.51, .42, 1.8, 2.0]], who: 'СМЕНЩИК', text: '— Прошлый тоже спрашивал. Михалыч.', pad: .3 },
        { img: 'guard_keys', cams: [[.5, .5, 1.3, 1.45]], who: 'СМЕНЩИК', text: '— Месяц назад пропал. Прямо на смене. Куртка на стуле осталась.', sfx: [['click', .3]], pad: .3 },
        { img: 'smenshik_leave', cams: [[.5, .5, 1.0, 1.1]], say: ['l04'], sfx: [['steps', .3]], pad: .5 }
      ],
      then: { goto: 'note' }
    },
    note: {
      music: 'drone', time: '02:59',
      beats: [
        { img: 'desk_note', fx: 'lamp', cams: [[.5, .5, 1.0, 1.1]], text: 'Листок в клетку. Четыре правила. Четыре разных почерка.', sfx: [['boom', 0]], pad: .3, wait: 'Нажми на листок, чтобы прочитать' },
        { img: 'desk_note', fx: 'lamp', cams: [[.5, .55, 1.4, 1.6]], say: ['l05'], rule: '1. В 03:00 НА 7 ЭТАЖЕ\nВКЛЮЧИТСЯ СВЕТ.\nНЕ ПОДНИМАЙСЯ', pad: .4 },
        { img: 'note', cams: [[.5, .5, 1.0, 1.08], [.5, .42, 1.3, 1.4]], say: ['l06'], rule: '2. ЛИФТ ПРИЕХАЛ САМ —\nНЕ ЗАХОДИ', pad: .4 },
        { img: 'note', cams: [[.5, .55, 1.3, 1.45]], say: ['l07'], rule: '3. ПОСЛЕ ПОЛУНОЧИ\nНИКОГО НЕ ВПУСКАЙ', pad: .4 },
        { img: 'desk_note', fx: 'lamp', cams: [[.55, .6, 1.5, 1.7]], say: ['l08'], rule: '4. КАМЕРУ 13\nНЕ ВКЛЮЧАЙ', pad: .5 },
        { img: 'note', cams: [[.5, .75, 1.4, 1.7]], say: ['l09'], rule: '«ТРЕТЬЕ ПРАВИЛО — ЛОЖЬ.\nВПУСТИ ЕЁ.»  — М.', sfx: [['boom', 0]], pad: .6, wait: 'Внизу что-то приписано ручкой' },
        { img: 'monitors', fx: 'flicker', cams: [[.5, .5, 1.0, 1.1], [.5, .45, 1.4, 1.6]], say: ['l10'], pad: .4, creep: true },
        { img: 'note', cams: [[.52, .78, 1.7, 2.0]], text: 'Я провёл пальцем по подписи. Чернила размазались. Синяя полоса на коже.', pad: .3 },
        { img: 'note', cams: [[.52, .78, 2.0, 2.2]], text: 'Месяц назад пропал. А чернила ещё мокрые.', sfx: [['boom', 0]], heart: true, pad: .6 }
      ],
      then: { camgame: 'floor7' }
    },
    floor7: {
      music: 'drone', time: '03:00',
      beats: [
        { img: 'corridor_cam', fx: 'cctv', label: 'CAM 07  ЭТАЖ 7', cams: [[.5, .5, 1.0, 1.1], [.51, .49, 1.8, 2.2]], text: 'Три ноль-ноль.',
          sfx: [['click', 1.2], ['click', 2.3], ['click', 3.4], ['click', 4.5]], pad: 2.6 }
      ],
      then: { choice: 'На седьмом зажёгся свет. Лампа за лампой.', timeout: 9, def: 1,
              opts: [{ t: 'Подняться и проверить', go: 'over_floor7' }, { t: 'Сидеть и смотреть', go: 'lift' }] }
    },
    lift: {
      music: 'drone', time: '03:04',
      beats: [
        { img: 'corridor_lit', fx: 'cctv', label: 'CAM 07  ЭТАЖ 7', cams: [[.5, .5, 1.0, 1.12]], say: ['l11'], pad: .3, creep: true },
        { img: 'elevator_open', cams: [[.5, .5, 1.0, 1.1], [.5, .55, 1.6, 1.9], [.5, .7, 1.3, 1.4]], say: ['l12'], sfx: [['ding', .8]], pad: .2 },
        { img: 'elevator_13', cams: [[.5, .3, 1.4, 1.8]], text: 'На табло над дверями горело: 13. В «Горизонте» двенадцать этажей.', sfx: [['tick', .5], ['tick', 1.5], ['tick', 2.5]], heart: true, pad: .4 }
      ],
      then: { choice: 'Лифт ждёт. Табло: 13.', timeout: 8, def: 1,
              opts: [{ t: 'Зайти внутрь', go: 'over_lift' }, { t: 'Считать дальше', go: 'cam13' }] }
    },
    cam13: {
      music: 'tension', time: '03:17',
      beats: [
        { img: 'elevator_open', fx: 'dark', cams: [[.5, .55, 1.3, 1.2]], text: 'Пятьдесят девять. Шестьдесят. Двери закрылись. Лифт ушёл наверх. Туда, где этажей нет.', sfx: [['ding', 2.4]], pad: .4 },
        { img: 'cam13_turn', fx: 'cctv', label: 'CAM 13', cams: [[.5, .45, 1.0, 1.05]], say: ['l13'], scareGap: true, sfx: [['static', 0]], pad: .2 },
        { img: 'cam13_turn', fx: 'cctv', label: 'CAM 13', cams: [[.54, .34, 2.2, 2.6]], say: ['ch_monster'], pad: .3 }
      ],
      then: { mg: 'cord', title: 'ВЫДЕРНИ ШНУР', hint: 'Тяни шнур монитора вниз. Три секунды.', dur: 3, win: 'knock', lose: 'over_cord' }
    },
    knock: {
      music: 'tension', time: '03:30',
      beats: [
        { img: 'booth_dark', cams: [[.5, .5, 1.0, 1.1]], text: 'Я выдернул шнур. Экран погас. Минут десять я просто сидел и слушал свой пульс.', sfx: [['click', 0]], pad: .4, creep: true },
        { img: 'door_katya', fx: 'rain', cams: [[.5, .5, 1.0, 1.08], [.51, .46, 1.8, 2.0]], say: ['l14'], sfx: [['knock', 1.0]], pad: .3 },
        { img: 'katya_face', fx: 'rain', cams: [[.5, .45, 1.0, 1.1]], say: ['ch_katya'], sfx: [['knock', .2]], pad: .4 },
        { img: 'door_katya', fx: 'rain', cams: [[.51, .46, 1.4, 1.6]], say: ['l15'], pad: .4 },
        { img: 'door_katya', fx: 'rain', cams: [[.5, .5, 1.2, 1.3]], text: 'Жёлтый дождевик. Тот самый, что я подарил ей на двадцатилетие.', pad: .3 },
        { img: 'entrance_empty', fx: 'static', label: 'CAM 01  ВХОД', cams: [[.5, .5, 1.0, 1.1]], say: ['l16'], pad: .2 },
        { img: 'entrance_empty', fx: 'static', label: 'CAM 01  ВХОД', cams: [[.6, .4, 1.6, 1.7]], say: ['ch_whisper'], pad: .6 },
        { img: 'katya_face', fx: 'rain', cams: [[.5, .5, 1.3, 1.5]], text: 'Она всхлипывала прямо в стекло. А стекло оставалось чистым. Ни пятнышка пара.', pad: .3 },
        { img: 'katya_face', fx: 'rain', cams: [[.5, .44, 1.8, 2.1]], text: 'И она ни разу не моргнула.', heart: true, pad: .5 },
        { img: 'katya_face', fx: 'rain', cams: [[.5, .5, 1.0, 1.1], [.5, .42, 1.4, 1.6]], say: ['l17'], heart: true, pad: .3 }
      ],
      then: { choice: 'Сестра плачет за стеклом.',
              opts: [{ t: 'Открыть дверь', go: 'open' }, { t: 'Не открывать', go: 'stay' }] }
    },

    /* ================= ВЕТКА А: ВПУСТИЛ ================= */
    open: {
      music: 'tension', time: '03:31',
      beats: [
        { img: 'door_katya', fx: 'rain', cams: [[.5, .5, 1.1, 1.3]], text: 'Я повернул ключ.', sfx: [['door', .2]], pad: .4 },
        { img: 'katya_inside', cams: [[.5, .45, 1.0, 1.15]], text: 'Она кинулась мне на шею. Ледяная. Мокрая насквозь.', pad: .3 },
        { img: 'katya_inside', cams: [[.5, .42, 1.2, 1.3]], text: '— Всё, всё. Ты внутри. Ты в безопасности.', pad: .3 },
        { img: 'katya_inside_still', cams: [[.5, .42, 1.0, 1.1]], text: 'Она перестала плакать. Сразу. Как будто кто-то выключил звук.', sfx: [['boom', 0]], pad: .5 },
        { img: 'katya_inside_still', cams: [[.5, .4, 1.3, 1.45]], text: 'От неё пахло не дождём. Подвалом. Сырой землёй.', pad: .4 },
        { img: null, phone: 'call', text: 'В кармане зазвонил телефон. На экране: «Катя».', sfx: [['ring', 0], ['buzz', 0]], pad: .4 },
        { img: null, phone: 'call', text: 'Катя звонила мне. А Катя только что держала меня за шею.', sfx: [['ring', 0]], heart: true, pad: .5 },
        { img: 'hall_cam_empty', fx: 'cctv', label: 'CAM 04  ХОЛЛ', cams: [[.5, .5, 1.0, 1.1]], text: 'Я поднял глаза. Холл был пуст. Только мокрые следы к лифту.', pad: .2, creep: true },
        { img: 'elevator_floors', cams: [[.5, .4, 1.1, 1.4]], floors: true, text: 'Лифт ехал наверх. Третий. Пятый. Седьмой.', sfx: [['ding', 3.6]], pad: .4 },
        { img: 'corridor_katya', fx: 'cctv', label: 'CAM 07  ЭТАЖ 7', cams: [[.49, .49, 1.0, 1.3]], text: 'Камера седьмого. В конце коридора — жёлтый дождевик. Она медленно обернулась.', pad: .6 },
        { img: 'corridor_face', fx: 'cctv', label: 'CAM 07  ЭТАЖ 7', cams: [[.5, .45, 1.0, 1.15]], text: 'Лицо было чужое. И оно улыбалось в камеру.', scare: .3, pad: .8 },
        { img: 'note', cams: [[.52, .78, 1.6, 1.9]], text: 'Мокрые чернила. Михалыч пропал месяц назад. Записку писал не он.', rule: '«ТРЕТЬЕ ПРАВИЛО — ЛОЖЬ.\nВПУСТИ ЕЁ.»  — М.', pad: .3 },
        { img: 'note', cams: [[.5, .6, 1.2, 1.3]], text: 'Оно подделало его почерк. А дверь я открыл ему сам.', heart: true, pad: .4 },
        { img: 'monitors', fx: 'flicker', cams: [[.5, .5, 1.1, 1.3]], text: 'Загудел лифт. Седьмой. Шестой. Пятый. Оно спускалось.', sfx: [['static', 0]], heart: true, pad: .3, creep: true }
      ],
      then: { choice: 'Оно едет вниз. Куда ты?', timeout: 8, def: 0,
              opts: [{ t: 'Запереться в будке', go: 'booth' }, { t: 'Бежать к аварийному выходу', go: 'exit' }] }
    },
    booth: {
      music: 'tension', time: '03:38',
      beats: [
        { img: 'monitors', fx: 'flicker', cams: [[.5, .5, 1.0, 1.1]], text: 'Я запер будку. Выключил лампу. Сполз на пол под стойку.', sfx: [['click', .4]], pad: .3 },
        { img: 'booth_dark', fx: 'dark', cams: [[.5, .5, 1.0, 1.2]], text: 'Дзынь. Двери лифта. Шаги. Босые, мокрые. Ближе.', sfx: [['ding', .1], ['steps', 1.0]], pad: .2 }
      ],
      then: { goto: 'booth_hold' }
    },
    booth_hold: {
      music: 'tension', time: '03:39',
      beats: [
        { img: 'booth_dark', fx: 'dark', cams: [[.5, .5, 1.2, 1.3]], who: 'КАТЯ?', text: '— Тёмыч… Я знаю, что ты тут. Ты же сам меня впустил.', sfx: [['steps', .4]], pad: .2 }
      ],
      then: { mg: 'hold', title: 'НЕ ДЫШИ', hint: 'Держи кнопку, пока шаги не уйдут. Отпустишь раньше — услышит.', dur: 9, win: 'booth_win', lose: 'over_hold' }
    },
    booth_win: {
      music: 'drone', time: '06:00',
      beats: [
        { img: 'booth_dark', cams: [[.5, .5, 1.2, 1.1]], text: 'Шаги кружили вокруг будки до рассвета. Потом стихли.', pad: .4, creep: true },
        { img: 'guard_dawn_fear', cams: [[.5, .5, 1.0, 1.08]], text: 'В шесть хлопнула входная дверь. Пришёл сменщик.', sfx: [['door', 0], ['steps', .6]], pad: .3 },
        { img: 'guard_dawn_fear', cams: [[.51, .43, 1.6, 1.8]], text: 'Он посмотрел на меня. И попятился. Медленно. Не поворачиваясь спиной.', pad: .3 },
        { img: 'guard_dawn_fear', cams: [[.51, .42, 1.9, 2.1]], who: 'СМЕНЩИК', text: '— Ты… ты чего так улыбаешься?', pad: .5 },
        { img: 'booth_reflect', cams: [[.5, .5, 1.0, 1.06], [.5, .45, 1.5, 1.7]], text: 'Я глянул в стекло будки. Я улыбался. Шире, чем умею.', scare: 4.2, pad: 1.0 }
      ],
      then: { ending: 'inside', title: 'ТЫ ДОЖИЛ ДО УТРА', sub: 'Ты сам сказал ему: «Ты внутри». Оно поверило. И ушло домой вместе с тобой.' }
    },
    exit: {
      music: 'tension', time: '03:38',
      beats: [
        { img: 'corridor_dark', cams: [[.5, .5, 1.0, 1.2]], text: 'Аварийный выход — в конце служебного коридора. Сорок шагов.', sfx: [['steps', .2]], pad: .2 },
        { img: 'corridor_off', cams: [[.5, .5, 1.0, 1.3]], text: 'Лампы начали гаснуть. По одной. Там, где гаснет, — там оно.', sfx: [['click', .5], ['click', 1.0], ['click', 1.4], ['click', 1.7]], pad: .1 }
      ],
      then: { mg: 'run', title: 'БЕГИ', n: 6, win: 'sister', lose: 'over_run' }
    },
    sister: {
      music: 'drone', time: '03:44',
      beats: [
        { img: 'exterior_alone', fx: 'rain', cams: [[.5, .5, 1.0, 1.1]], text: 'Я вылетел под дождь. Бежал, пока не кончился воздух.', sfx: [['door', 0], ['boom', .4]], pad: .4 },
        { img: 'exterior', fx: 'rain', cams: [[.5, .35, 1.2, 1.35]], text: '«Горизонт» стоял тёмный. Только на седьмом горело одно окно.', pad: .4, creep: true },
        { img: null, phone: 'call', text: 'Зазвонил телефон. Катя.', sfx: [['ring', 0], ['buzz', 0]], pad: .3 },
        { img: null, phone: 'call2', who: 'КАТЯ', text: '— Тёмыч? Ты чего так рано? Четыре утра.', pad: .3 },
        { img: null, phone: 'call2', who: 'КАТЯ', text: '— Ты же только что постучал ко мне в дверь.', heart: true, pad: .6 },
        { img: 'exterior_alone', fx: 'rain', cams: [[.5, .42, 1.4, 1.7]], text: 'Я стоял под дождём. В другом городе. За триста километров от её двери.', pad: .4 },
        { img: null, phone: 'call2', who: 'КАТЯ', text: '— Ты чего молчишь? Заходи. Я уже открыла.', sfx: [['door', 1.6]], pad: 1.2 }
      ],
      then: { ending: 'sister', title: 'ТЫ ВЫБРАЛСЯ', sub: 'Ты сбежал. Оно — тоже. Только не наружу, а к ней.' }
    },

    /* ================= ВЕТКА Б: НЕ ВПУСТИЛ ================= */
    stay: {
      music: 'tension', time: '03:31',
      beats: [
        { img: 'katya_smile', fx: 'rain', cams: [[.5, .45, 1.0, 1.15]], text: 'Я не открыл. Она перестала плакать. Сразу.', sfx: [['boom', 0]], pad: .4 },
        { img: 'katya_smile', fx: 'rain', cams: [[.5, .5, 1.4, 1.6]], text: 'И улыбнулась. Слишком широко. У Кати никогда не было столько зубов.', pad: .4 },
        { img: 'door_smile', fx: 'rain', cams: [[.5, .5, 1.0, 1.2]], text: 'Она стучала. Сильнее. Ещё сильнее. Стекло дрожало в раме.', sfx: [['knock', .3], ['knock', 1.1], ['knock', 1.8], ['knock', 2.4]], pad: .3 },
        { img: 'door_smile', fx: 'rain', cams: [[.51, .46, 1.6, 1.8]], who: 'КАТЯ?', text: '— Ничего. Я подожду. Я умею ждать.', pad: .4 },
        { img: 'door_back', fx: 'dark', cams: [[.5, .5, 1.2, 1.0]], text: 'Потом она пошла назад. Спиной вперёд. Не отводя от меня глаз.', pad: .6 },
        { img: null, phone: 'call2', text: 'Я набрал Катю. Гудок. Второй. Третий.', sfx: [['ring', 0]], pad: .3 },
        { img: null, phone: 'call2', who: 'КАТЯ', text: '— Алло… Тёмыч? Ты время видел?', pad: .3 },
        { img: null, phone: 'call2', text: '— Ты где?', pad: .2 },
        { img: null, phone: 'call2', who: 'КАТЯ', text: '— Дома. Сплю. Что случилось?', pad: .3 },
        { img: null, phone: 'call2', text: '— Ничего. Спи. И никому не открывай. Даже мне.', pad: .4 },
        { img: 'monitors', fx: 'flicker', cams: [[.5, .5, 1.0, 1.15]], text: 'Правила не врали. Врала только записка.', pad: .5, creep: true }
      ],
      then: { goto: 'cam13b' }
    },
    cam13b: {
      music: 'tension', time: '04:10',
      beats: [
        { img: 'monitors', fx: 'flicker', cams: [[.5, .45, 1.2, 1.4]], text: 'Шнур я выдернул ещё в три. Тринадцатая всё равно включилась.', sfx: [['static', 0]], pad: .3 },
        { img: 'cam13_mih', fx: 'cctv', label: 'CAM 13', cams: [[.5, .5, 1.0, 1.1]], text: 'Серверная. На полу — круг из соли. В круге сидел человек.', pad: .3 },
        { img: 'cam13_mih', fx: 'cctv', label: 'CAM 13', cams: [[.5, .45, 1.5, 1.7]], text: 'Михалыч. Живой. Худой, заросший. Он поднял к камере листок.',
          rule: '«ПОМОГИ. МИНУС 2.\nСТУЧИ ТРИЖДЫ»', pad: .6 },
        { img: 'cam13_mih_monster', fx: 'cctv', label: 'CAM 13', cams: [[.6, .4, 1.2, 2.0]], text: 'А за кругом, в темноте, кто-то стоял. И смотрел не на него. На меня.', scare: 3.4, pad: .8 }
      ],
      then: { choice: 'Михалыч жив. Минус второй этаж.', timeout: 12, def: 1,
              opts: [{ t: 'Спуститься к Михалычу', go: 'down' }, { t: 'Сидеть и соблюдать правила', go: 'rules' }] }
    },
    down: {
      music: 'tension', time: '04:14',
      beats: [
        { img: 'corridor_dark', fx: 'dark', cams: [[.5, .5, 1.0, 1.3]], text: 'Лифт я не тронул. Лестница. Минус первый. Минус второй.', sfx: [['steps', .2], ['steps', 1.8]], pad: .2 },
        { img: 'corridor_dark', cams: [[.5, .5, 1.3, 1.5]], text: 'Пахло сыростью и свечным воском. Фонарик мигал.', sfx: [['click', .8]], pad: .3, creep: true }
      ],
      then: { goto: 'basement' }
    },
    basement: {
      music: 'tension', time: '04:16',
      beats: [
        { img: 'basement_door', fx: 'flicker', cams: [[.5, .5, 1.0, 1.15]], text: 'Железная дверь. «Серверная». Под дверью — полоска тёплого света.', pad: .3 },
        { img: 'basement_door', cams: [[.5, .45, 1.3, 1.45]], who: 'МИХАЛЫЧ', text: '— Тёмыч? Ты? Заходи, сынок, открыто.', pad: .4 },
        { img: 'basement_door', cams: [[.5, .5, 1.5, 1.6]], text: 'Голос был его. Хриплый, добрый. Только мы с ним ни разу не виделись.', heart: true, pad: .3 },
        { img: 'basement_door', cams: [[.5, .5, 1.6, 1.8]], text: 'Откуда он знает, как меня зовут? На листке было: стучи трижды.', heart: true, pad: .4 }
      ],
      then: { choice: '«Заходи, открыто».', timeout: 10, def: 1,
              opts: [{ t: 'Войти на голос', go: 'over_voice' }, { t: 'Постучать трижды', go: 'knock3' }] }
    },
    knock3: {
      music: 'tension', time: '04:17',
      beats: [
        { img: 'basement_door', cams: [[.5, .5, 1.2, 1.3]], text: 'Я постучал. Раз. Два. Три.', sfx: [['knock', .5], ['knock', 1.2], ['knock', 1.9]], pad: .4 },
        { img: 'basement_door', fx: 'dark', cams: [[.5, .5, 1.3, 1.2]], text: 'Тишина. Потом лязгнул засов. Изнутри.', sfx: [['door', .9]], pad: .3 },
        { img: 'server_mih_stand', cams: [[.5, .45, 1.0, 1.15]], who: 'МИХАЛЫЧ', text: '— Быстро. Закрывай. Ты слышал за дверью мой голос?', pad: .2 },
        { img: 'server_mih_stand', cams: [[.5, .45, 1.2, 1.3]], text: '— Слышал.', pad: .2 },
        { img: 'server_mih_stand', cams: [[.5, .4, 1.4, 1.6]], who: 'МИХАЛЫЧ', text: '— Я месяц рта не раскрывал. Это был он.', pad: .4, creep: true },
        { img: 'server_mih_close', cams: [[.5, .45, 1.0, 1.1]], who: 'МИХАЛЫЧ', text: '— Жилец. С тринадцатого этажа. Которого в здании нет.', pad: .3 },
        { img: 'server_mih_close', cams: [[.5, .42, 1.2, 1.3]], who: 'МИХАЛЫЧ', text: '— Голоса ворует. Почерк. Войти может, только если впустишь. Или ответишь.', pad: .3 },
        { img: 'server_mih_close', cams: [[.5, .4, 1.3, 1.4]], who: 'МИХАЛЫЧ', text: '— «Впусти её» — это он писал. Моей рукой. Я бы такого не написал.', pad: .3 },
        { img: 'server_mih', cams: [[.5, .5, 1.0, 1.1]], who: 'МИХАЛЫЧ', text: '— Соль держит его до рассвета. Но круг на одного. Тебе — дверь.', pad: .3 },
        { img: 'server_door', cams: [[.6, .5, 1.0, 1.2]], text: 'За дверью зашлёпали мокрые шаги. А потом заплакала Катя.', say: ['ch_katya'], pad: .2 }
      ],
      then: { goto: 'hold_door' }
    },
    hold_door: {
      music: 'tension', time: '04:20',
      beats: [
        { img: 'server_door', fx: 'flicker', cams: [[.65, .5, 1.2, 1.4]], text: 'Удар. Ещё удар. Дверь выгнулась внутрь.', sfx: [['knock', .2], ['knock', .9]], pad: .1 }
      ],
      then: { mg: 'door', title: 'ДЕРЖИ ДВЕРЬ', hint: 'Жми часто. До шести он будет ломиться.', dur: 13, win: 'dawn', lose: 'over_door' }
    },
    dawn: {
      music: 'drone', time: '06:00',
      beats: [
        { img: 'server_door', cams: [[.5, .5, 1.1, 1.0]], text: 'В шесть удары прекратились. Как отрезало.', pad: .5 },
        { img: 'server_mih_stand', cams: [[.5, .45, 1.0, 1.15]], text: 'Михалыч вышел из круга. Ноги его не держали. Я подставил плечо.', pad: .3 },
        { img: 'exterior_two', fx: 'rain', cams: [[.5, .5, 1.0, 1.1]], text: 'Мы вышли под дождь. Небо было серое. Утро.', sfx: [['door', 0]], pad: .4 },
        { img: 'exterior_two', fx: 'rain', cams: [[.55, .5, 1.4, 1.6]], text: 'Михалыч долго хлопал по карманам. Нашёл мятую сигарету. Закурил.', pad: .3 },
        { img: 'exterior_two', fx: 'rain', cams: [[.6, .5, 1.7, 1.9]], who: 'МИХАЛЫЧ', text: '— Спасибо, сынок. Месяц я неба не видел.', pad: .3 },
        { img: 'exterior_two', fx: 'rain', cams: [[.62, .48, 2.0, 2.2]], who: 'МИХАЛЫЧ', text: '— Но слушай. Сегодня ночью оно будет ждать уже тебя.', pad: .8 },
        { img: 'hall_cam', fx: 'cctv', label: 'CAM 04  ХОЛЛ', cams: [[.5, .5, 1.0, 1.06], [.44, .46, 2.6, 3.4]], say: ['l01'], pad: 1.0 }
      ],
      then: { ending: 'dawn', title: 'ТЫ ДОЖИЛ ДО УТРА', sub: 'Михалыч свободен. А у лифта теперь ждут тебя. Каждую ночь — чуть ближе.' }
    },
    rules: {
      music: 'drone', time: '04:12',
      beats: [
        { img: 'monitors', fx: 'flicker', cams: [[.5, .5, 1.0, 1.1]], text: 'Я остался. Правила держат меня живым. Значит, держусь за правила.', pad: .3 },
        { img: 'booth_dark', cams: [[.5, .5, 1.0, 1.15]], text: 'Полтора часа я смотрел в мониторы. Тринадцатая больше не включалась.', pad: .3, creep: true },
        { img: 'monitors', cams: [[.5, .5, 1.2, 1.3]], text: 'Михалыч сидел там, внизу. Я старался об этом не думать.', pad: .4 }
      ],
      then: { goto: 'call540' }
    },
    call540: {
      music: 'tension', time: '05:40',
      beats: [
        { img: null, phone: 'call', text: 'В 05:40 зазвонил телефон. «Катя».', sfx: [['ring', 0], ['buzz', 0]], pad: .3 },
        { img: null, phone: 'call', text: 'Катя спит. Я сам её разбудил два часа назад.', sfx: [['ring', 0]], heart: true, pad: .3 }
      ],
      then: { choice: 'Звонит «Катя».', timeout: 8, def: 1, opts: [{ t: 'Ответить', go: 'over_answer' }, { t: 'Сбросить', go: 'hangup' }] }
    },
    hangup: {
      music: 'drone', time: '06:00',
      beats: [
        { img: 'monitors', cams: [[.5, .5, 1.0, 1.1]], text: 'Я сбросил. Он позвонил снова. И снова. Потом затих.', sfx: [['buzz', .4], ['buzz', 1.6]], pad: .3, creep: true },
        { img: 'hall_dawn', cams: [[.5, .5, 1.0, 1.08]], text: 'В шесть пришёл сменщик. Посмотрел на меня. Ничего не спросил.', sfx: [['steps', .3]], pad: .3 },
        { img: 'desk_note', cams: [[.5, .5, 1.0, 1.2]], text: 'Я забрал куртку. Под листком с правилами лежал ещё один.', pad: .3 },
        { img: 'note', cams: [[.5, .55, 1.0, 1.3]], text: 'Пятое правило. Мой почерк. Я не помню, когда это написал.', rule: '5. НЕ ИЩИ МИХАЛЫЧА', pad: .5 },
        { img: 'note', cams: [[.5, .55, 1.3, 1.5]], text: 'Но я знал: это правда. Правила пишут те, кто дожил.', pad: 1.0 }
      ],
      then: { ending: 'rules', title: 'ТЫ ДОЖИЛ ДО УТРА', sub: 'Михалыч остался внизу. А правила теперь пишешь ты.' }
    },

    /* ================= СМЕРТИ ================= */
    over_floor7: {
      music: 'tension', time: '03:01',
      beats: [
        { img: 'corridor_lit', cams: [[.5, .5, 1.0, 1.15]], text: 'Я поднялся пешком. Седьмой. Коридор залит светом. Пусто.', sfx: [['steps', .2]], pad: .2 },
        { img: 'corridor_off', cams: [[.5, .5, 1.0, 1.3]], text: 'Лампы гасли одна за другой. От дальней стены. Ко мне.', sfx: [['click', .5], ['click', 1.0], ['click', 1.4], ['click', 1.7]], pad: .1 },
        { img: 'monster_close', cams: [[.5, .45, 1.0, 1.15]], scare: 0, text: '', pad: 1.6 }
      ],
      then: { over: 'ПРАВИЛО 1 НАРУШЕНО', sub: 'В 03:00 на седьмом включится свет. Не поднимайся.', retry: 'floor7' }
    },
    over_lift: {
      music: 'tension', time: '03:05',
      beats: [
        { img: 'elevator_inside', cams: [[.5, .45, 1.0, 1.2]], text: 'Я шагнул внутрь. Двери закрылись. Кнопок не было. Только табло: 13.', sfx: [['ding', 2.2]], pad: .2 },
        { img: 'elevator_inside', cams: [[.5, .3, 1.3, 1.6]], text: 'Лифт поехал вверх. Мимо двенадцатого. Выше.', pad: .1 },
        { img: 'monster_close', cams: [[.5, .45, 1.0, 1.15]], scare: 0, text: '', pad: 1.6 }
      ],
      then: { over: 'ПРАВИЛО 2 НАРУШЕНО', sub: 'Если лифт приехал сам — не заходи.', retry: 'lift' }
    },
    over_cam13: {
      music: 'tension',
      beats: [
        { img: 'cam13_monster', fx: 'cctv', label: 'CAM 13', cams: [[.54, .34, 1.0, 1.8]], text: 'Я включил тринадцатую. Он уже смотрел в камеру. Ждал.', say: ['ch_monster'], pad: .3 },
        { img: 'monster_close', cams: [[.5, .45, 1.0, 1.15]], scare: 0, text: '', pad: 1.6 }
      ],
      then: { over: 'ПРАВИЛО 4 НАРУШЕНО', sub: 'Камеру 13 не включай.', retry: 'intro_cams' }
    },
    over_cord: {
      music: 'tension', time: '03:17',
      beats: [
        { img: 'cam13_monster', fx: 'cctv', label: 'CAM 13', cams: [[.54, .34, 2.0, 2.4]], text: 'Я не успел. А потом в кадре стало пусто.', pad: .2 },
        { img: 'booth_dark', fx: 'dark', cams: [[.5, .5, 1.0, 1.2]], text: 'Лампа в будке погасла. Кто-то дышал мне в затылок.', pad: .1 },
        { img: 'monster_booth', cams: [[.5, .45, 1.0, 1.2]], scare: 0, text: '', pad: 1.6 }
      ],
      then: { over: 'ОНО ТЕБЯ УВИДЕЛО', sub: 'Если тринадцатая включилась сама — выдёргивай шнур.', retry: 'cam13' }
    },
    over_hold: {
      music: 'tension',
      beats: [{ img: 'monster_booth', cams: [[.5, .45, 1.0, 1.2]], scare: .1, text: '', say: ['ch_monster'], pad: .8 }],
      then: { over: 'ОНО УСЛЫШАЛО ТЕБЯ', sub: 'Иногда надо просто не дышать.', retry: 'booth_hold' }
    },
    over_run: {
      music: 'tension',
      beats: [{ img: 'corridor_monster', cams: [[.5, .45, 1.0, 1.2]], scare: .1, text: '', pad: 1.8 }],
      then: { over: 'ТЕМНОТА ДОГНАЛА', sub: 'Где гаснет свет — туда нельзя.', retry: 'exit' }
    },
    over_voice: {
      music: 'tension', time: '04:17',
      beats: [
        { img: 'basement_door', cams: [[.5, .5, 1.2, 1.4]], text: 'Я толкнул дверь. Не заперто.', sfx: [['door', .2]], pad: .2 },
        { img: 'server_door', cams: [[.4, .5, 1.0, 1.2]], text: 'Михалыч сидел в круге. Смотрел на меня и мотал головой: нет, нет, нет.', pad: .2 },
        { img: 'server_door', fx: 'dark', cams: [[.4, .5, 1.2, 1.3]], who: '???', text: '— Вот и пришёл, сынок.', pad: .1 },
        { img: 'monster_close', cams: [[.5, .45, 1.0, 1.15]], scare: 0, text: '', pad: 1.6 }
      ],
      then: { over: 'ЗА ДВЕРЬЮ БЫЛО ОНО', sub: 'Голосу верить нельзя. Стучи трижды.', retry: 'basement' }
    },
    over_door: {
      music: 'tension',
      beats: [
        { img: 'server_door', fx: 'dark', cams: [[.65, .5, 1.2, 1.5]], text: 'Засов вылетел вместе с шурупами.', sfx: [['door', 0]], pad: .1 },
        { img: 'monster_close', cams: [[.5, .45, 1.0, 1.2]], scare: 0, text: '', pad: 1.6 }
      ],
      then: { over: 'ЗА ДВЕРЬЮ БЫЛО ОНО', sub: 'Держи дверь до рассвета. Не сбавляй.', retry: 'hold_door' }
    },
    over_answer: {
      music: 'tension', time: '05:41',
      beats: [
        { img: null, phone: 'call2', who: 'КАТЯ?', text: '— Тёмыч… А почему ты к нему не спустился?', pad: .2 },
        { img: 'hall_cam_katya', fx: 'cctv', label: 'CAM 04  ХОЛЛ', cams: [[.5, .5, 1.0, 1.1], [.47, .47, 2.2, 2.6]], text: 'Голос был её. Только шёл он не из трубки. Из холла.', scare: 3.6, pad: 1.4 }
      ],
      then: { over: 'ТЫ ОТВЕТИЛ', sub: 'Ответить — значит впустить.', retry: 'call540' }
    },
    // повтор обхода камер после смерти на CAM 13
    intro_cams: { time: '02:59', beats: [], then: { camgame: 'floor7' } }
  },
  // мини-игра «обход камер»: посмотреть 3 камеры, 13-ю не трогать
  cams: [
    { id: 'CAM 01', name: 'ВХОД', img: 'entrance_empty', c: [.5, .5, 1.0], note: 'Пусто. Дождь стеной.' },
    { id: 'CAM 04', name: 'ХОЛЛ', img: 'hall_cam', c: [.44, .46, 2.4], note: 'У лифта кто-то стоит?.. Нет. Показалось.', creep: true },
    { id: 'CAM 07', name: 'ЭТАЖ 7', img: 'corridor_dark', c: [.5, .5, 1.0], note: 'Темно. Пока.' },
    { id: 'CAM 13', name: '', img: null, forbidden: true }
  ],
  endings: { dawn: 'Рассвет', rules: 'Пятое правило', inside: 'Внутри', sister: 'Сестра' },
  overs: ['ПРАВИЛО 1 НАРУШЕНО', 'ПРАВИЛО 2 НАРУШЕНО', 'ПРАВИЛО 4 НАРУШЕНО', 'ОНО ТЕБЯ УВИДЕЛО', 'ОНО УСЛЫШАЛО ТЕБЯ', 'ТЕМНОТА ДОГНАЛА', 'ЗА ДВЕРЬЮ БЫЛО ОНО', 'ТЫ ОТВЕТИЛ']
};
