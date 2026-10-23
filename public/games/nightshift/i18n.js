/* «Молчи до шести» (Hush Till Six) — языки. Русский — основной (строки в коде); английский — словарь «русская строка → английская».
   Язык: ?lang=ru|en, затем выбор в настройках (ns1_lang), иначе по языку браузера (русский/украинский/белорусский/казахский → ru, остальные → en).
   Озвученные реплики английской версии — timing_en.json и snd/en/*.mp3. */
(() => {
'use strict';
const q = /[?&]lang=(ru|en)/.exec(location.search);
const LANG = q ? q[1] : (() => { try { const v = JSON.parse(localStorage.getItem('ns1_lang')); if (v === 'ru' || v === 'en') return v; } catch {}
  return /^(ru|uk|be|kk)/i.test(navigator.language || '') ? 'ru' : 'en'; })();
const EN = {
  // сценарий: экранные тексты, выборы, правила, подсказки мини-игр, концовки
  'CAM 04  ХОЛЛ': 'CAM 04  LOBBY', 'CAM 07  ЭТАЖ 7': 'CAM 07  FLOOR 7', 'CAM 01  ВХОД': 'CAM 01  ENTRANCE', 'ВХОД': 'ENTRANCE', 'ХОЛЛ': 'LOBBY', 'ЭТАЖ 7': 'FLOOR 7',
  'НОЧЬ 1': 'NIGHT 1', 'БЦ «ГОРИЗОНТ» · 02:55': 'HORIZON BUSINESS CENTER · 02:55',
  'Нажми, чтобы заступить на смену': 'Tap to start your shift',
  'Сменщик протягивает ключи. Рука дрожит.': 'The other guard holds out the keys. His hand is shaking.',
  'Взять ключи': 'Take the keys', '«А почему втрое?»': '"Why triple pay?"', 'Нажми на листок, чтобы прочитать': 'Tap the sheet to read it',
  '1. В 03:00 НА 7 ЭТАЖЕ\nВКЛЮЧИТСЯ СВЕТ.\nНЕ ПОДНИМАЙСЯ': '1. AT 3:00 A.M. THE LIGHTS\nON FLOOR 7 WILL COME ON.\nDO NOT GO UP',
  '2. ЛИФТ ПРИЕХАЛ САМ —\nНЕ ЗАХОДИ': '2. IF THE ELEVATOR COMES\nON ITS OWN — DON\'T GET IN',
  '3. ПОСЛЕ ПОЛУНОЧИ\nНИКОГО НЕ ВПУСКАЙ.\nДАЖЕ СВОИХ': '3. AFTER MIDNIGHT\nLET NO ONE IN.\nNOT EVEN YOUR OWN',
  '4. КАМЕРУ 13\nНЕ ВКЛЮЧАЙ': '4. DO NOT TURN ON\nCAMERA 13',
  '«ТРЕТЬЕ ПРАВИЛО — ЛОЖЬ.\nВПУСТИ ЕЁ.»  — М.': '"RULE THREE IS A LIE.\nLET HER IN."  — M.',
  'Внизу что-то приписано ручкой': 'Something is scribbled at the bottom',
  'ПЕРЕМОТАЙ ЗАПИСЬ': 'REWIND THE TAPE', 'Тяни ползунок влево. Найди момент, когда он ещё жив.': 'Drag the slider left. Find the moment he was still alive.',
  '03:00:00. Он ещё жив…': '03:00:00. He\'s still alive…',
  'Свет на седьмом горит. Уборщик пропал.': 'The seventh floor is lit. The janitor is gone.',
  'Подняться и найти его': 'Go up and find him', 'Остаться у мониторов': 'Stay at the monitors',
  'Лифт ждёт. Табло: 13.': 'The elevator is waiting. Display: 13.', 'Зайти внутрь': 'Step inside', 'Считать дальше': 'Keep counting',
  'ВЫДЕРНИ ШНУР': 'PULL THE CORD', 'Тяни шнур монитора вниз и держи три секунды.': 'Drag the monitor cord down and hold for three seconds.',
  'КТО ТАМ?': 'WHO\'S THERE?', 'Стекло запотело. Протри его пальцем.': 'The glass is fogged up. Wipe it with your finger.',
  'Катя': 'Katya', 'Не спится. Ты уже на смене? Я дома, позвони утром.': 'Can\'t sleep. Are you on shift already? I\'m home, call me in the morning.',
  'Ключ в замке.': 'The key is in the lock.', 'Повернуть ключ': 'Turn the key', 'Отойти от двери': 'Step away from the door',
  'Лифт едет вниз.': 'The elevator is coming down.', 'В будку, под стойку': 'Into the booth, under the desk', 'К аварийному выходу': 'To the emergency exit',
  'Ты чего не отвечаешь? Перезвони.': 'Why aren\'t you answering? Call me back.',
  'НЕ ДЫШИ': 'DON\'T BREATHE', 'Держи кнопку, пока шаги не уйдут. Отпустишь раньше — услышит.': 'Hold the button until the footsteps leave. Let go too early and it will hear you.',
  'СМЕНА ОКОНЧЕНА': 'SHIFT OVER', '«Ты внутри», — сказал ты ей. И не соврал.': '"You\'re inside," you told her. And you weren\'t lying.',
  'БЕГИ': 'RUN', 'ТЫ ВЫБРАЛСЯ': 'YOU GOT OUT', 'Ей правил никто не давал.': 'Nobody ever gave her the rules.',
  '«ПОМОГИ. МИНУС 2.\nСТУЧИ ТРИЖДЫ»': '"HELP. MINUS 2.\nKNOCK THREE TIMES"',
  'Минус второй. Стучать трижды.': 'Minus two. Knock three times.', 'Спуститься': 'Go down', 'Остаться на посту': 'Stay at your post',
  'ФОНАРИК': 'FLASHLIGHT', 'Веди пальцем, чтобы светить. В конце коридора что-то шуршит.': 'Move your finger to shine the light. Something is rustling at the end of the corridor.',
  'Никого. Дверь серверной — там же, в конце.': 'Nobody. The server room door is down there too, at the end.',
  'СЕРВЕРНАЯ −2': 'SERVER ROOM −2', '«Заходи, открыто».': '"Come in, it\'s open."', 'Толкнуть дверь': 'Push the door', 'Постучать трижды': 'Knock three times',
  'За дверью зашлёпали мокрые шаги. А потом заплакала Катя.': 'Wet footsteps slapped behind the door. Then Katya started to cry.',
  'ДЕРЖИ ДВЕРЬ': 'HOLD THE DOOR', 'Жми чаще. До шести он будет ломиться в дверь.': 'Tap faster. Until six it will keep slamming the door.',
  'ТЫ ДОЖИЛ ДО УТРА': 'YOU SURVIVED THE NIGHT',
  'Михалыч ушёл и не вернулся. Ты вернулся. А тот, у лифта, каждую ночь стоит чуть ближе.': 'Mikhalych left and never came back. You came back. And the one by the elevator stands a little closer every night.',
  'Звонит «Катя».': '"Katya" is calling.', 'Ответить': 'Answer', 'Сбросить': 'Hang up', '5. НЕ ИЩИ МИХАЛЫЧА': '5. DON\'T LOOK FOR MIKHALYCH',
  'ПРАВИЛА СОБЛЮДЕНЫ': 'RULES FOLLOWED', 'Михалыч остался внизу. А правила теперь пишешь ты.': 'Mikhalych stayed down there. And now you write the rules.',
  'ПРАВИЛО 1 НАРУШЕНО': 'RULE 1 BROKEN', 'В 03:00 на седьмом включится свет. Не поднимайся.': 'At 3:00 a.m. the seventh floor lights up. Do not go up.',
  'ПРАВИЛО 2 НАРУШЕНО': 'RULE 2 BROKEN', 'Если лифт приехал сам — не заходи.': 'If the elevator comes on its own — don\'t get in.',
  'Я включил тринадцатую. Он уже смотрел в камеру. Ждал.': 'I turned on thirteen. He was already looking into the camera. Waiting.',
  'ПРАВИЛО 4 НАРУШЕНО': 'RULE 4 BROKEN', 'Камеру 13 не включай.': 'Do not turn on camera 13.',
  'ОНО ТЕБЯ УВИДЕЛО': 'IT SAW YOU', 'Если тринадцатая включилась сама — выдёргивай шнур.': 'If thirteen turns on by itself — pull the cord.',
  'ОНО УСЛЫШАЛО ТЕБЯ': 'IT HEARD YOU', 'Пока шаги рядом — не дыши.': 'While the footsteps are near — don\'t breathe.',
  'ТЕМНОТА ДОГНАЛА': 'THE DARK CAUGHT UP', 'Не оглядывайся. Беги туда, где ещё светло.': 'Don\'t look back. Run where it\'s still light.',
  'ЗА ДВЕРЬЮ БЫЛО ОНО': 'IT WAS BEHIND THE DOOR', 'Он умеет звать. Стучать — нет.': 'It can call your name. It can\'t knock.',
  'ЗАСОВ НЕ ВЫДЕРЖАЛ': 'THE BOLT GAVE WAY', 'Держи дверь до рассвета. Не сбавляй.': 'Hold the door until dawn. Don\'t slow down.',
  'ТЫ ОТВЕТИЛ': 'YOU ANSWERED', 'Ответить — значит впустить.': 'To answer is to let it in.',
  'Пусто. Дождь стеной. На стекле чей-то отпечаток ладони?.. Нет, просто капли.': 'Empty. A wall of rain. A handprint on the glass?.. No, just raindrops.',
  'У лифта кто-то стоит?.. Нет. Показалось.': 'Someone by the elevator?.. No. Just my imagination.',
  'Уборщик домывает седьмой. Хоть кто-то живой в этом здании.': 'The janitor is finishing the seventh floor. At least someone in this building is alive.',
  'Рассвет': 'Dawn', 'Пятое правило': 'Rule Five', 'Внутри': 'Inside', 'Сестра': 'Sister',
  // кто говорит (табличка над репликой)
  'ИТАН': 'ETHAN', 'СМЕНЩИК': 'DAY GUARD', 'ЛИЛИ': 'LILY', 'ФРЭНК': 'FRANK', 'ГАС': 'GUS', 'ты': 'you', 'Лили': 'Lily',
  // интерфейс
  '18+ · жестокость и кровь · лучше в наушниках · в игре есть вспышки света и резкие звуки': '18+ · violence and blood · best with headphones · contains flashing lights and loud sounds',
  'Жестокость, кровь и сцены насилия': 'Violence, blood and gore', 'Лучше в наушниках · в игре есть вспышки света': 'Best with headphones · contains flashing lights',
  'Четыре правила · Одна ночь · Сестра за стеклом': 'Four rules · One night · A sister behind the glass',
  'Загрузка звука…': 'Loading sound…', 'Начать смену': 'Start shift', 'Начать заново': 'Start over', 'Продолжить': 'Continue', 'Концовки': 'Endings', 'КОНЦОВКИ': 'ENDINGS',
  'Настройки': 'Settings', 'НАСТРОЙКИ': 'SETTINGS', 'Назад': 'Back', 'Меню': 'Menu', 'Во весь экран': 'Fullscreen', 'Поддержать игру': 'Support the game', 'СПОНСОРЫ ИГРЫ': 'GAME SPONSORS',
  'Стань первым — твоё имя будет здесь': 'Be the first — your name will be here', 'Сыграть ещё раз': 'Play again', 'Попробовать снова': 'Try again', 'Ты не дожил до утра.': 'You didn\'t make it to morning.',
  'нажми, чтобы закрыть': 'tap to close', 'Правил пока нет.': 'No rules yet.', 'сейчас': 'now', 'СООБЩЕНИЯ': 'MESSAGES', 'Это не я.': 'It\'s not me.', 'Не открывай им.': 'Don\'t open the door to them.', 'Я тебя вижу': 'I see you',
  'мобильный · входящий': 'mobile · incoming', 'СОЕДИНЕНИЕ ПОТЕРЯНО': 'CONNECTION LOST', 'переподключение': 'reconnecting', 'Нужен браузер с WebGL': 'A browser with WebGL is required',
  'ОБХОД КАМЕР': 'CAMERA ROUND', 'Не отзывайся · Не открывай · Дождись шести': 'Don\'t answer · Don\'t open · Wait for six', 'МОЛЧИ ДО ШЕСТИ': 'HUSH TILL SIX', 'БЦ «ГОРИЗОНТ» · 21:00': 'HORIZON BUSINESS CENTER · 21:00', 'Громкость': 'Volume', 'Музыка': 'Music', 'Голоса': 'Voices', 'Звуки': 'Sounds', 'Проверь три камеры. В тринадцатую не смотри.': 'Check three cameras. Don\'t look at thirteen.', '«НЕ СМОТРИ В НЕЁ»': '“DON\'T LOOK AT IT”', 'Продолжить смену': 'Continue the shift',
  'ТОЧНО ВКЛЮЧИТЬ?': 'TURN IT ON?', 'ПРАВИЛО 4': 'RULE 4', 'Тяни вилку вниз, быстро!': 'Drag the plug down, fast!', 'воздух': 'air', 'ДЕРЖИ': 'HOLD',
  'ТИХО…': 'QUIET…', 'Шаги ушли. Можно дышать.': 'The footsteps are gone. You can breathe.', 'ДВЕРЬ': 'DOOR', 'Влево': 'Left', 'Вправо': 'Right', '← ВЛЕВО': '← LEFT', 'ВПРАВО →': 'RIGHT →', 'СЛЕВА': 'LEFT', 'СПРАВА': 'RIGHT',
  '▶ ЗАПИСЬ': '▶ PLAY', '◀◀ ЗАПИСЬ': '◀◀ REWIND', 'НОЧЬ 1 · ': 'NIGHT 1 · ', 'ТЕЛЕФОН': 'PHONE', 'КАМЕРА': 'CAMERA',
  'Экран': 'Screen', 'ГОРИЗОНТАЛЬНО': 'LANDSCAPE', 'ВЕРТИКАЛЬНО': 'PORTRAIT', 'Графика': 'Graphics', 'КИНО': 'CINEMA', 'РЕТРО': 'RETRO', 'Звук': 'Sound', 'Озвучка': 'Voice-over',
  'ВКЛ': 'ON', 'ВЫКЛ': 'OFF', 'СЛАБО': 'LOW', 'ОБЫЧНО': 'NORMAL', 'Плёнка VHS': 'VHS film', 'Меньше вспышек': 'Fewer flashes', 'Реплики сами': 'Auto-advance',
  'Оглядываться мышью': 'Look around with mouse', 'Оглядываться наклоном': 'Look around by tilting', 'Язык': 'Language', 'РУССКИЙ': 'РУССКИЙ', 'ENGLISH': 'ENGLISH',
  '«Кино» — тени, блики, объёмный свет, чёткая картинка (для ПК). «Ретро» — плёнка и пиксели старой приставки, легче для телефона. Без озвучки остаются субтитры.':
    '"Cinema" — shadows, reflections, volumetric light, a sharp image (for PC). "Retro" — the film grain and pixels of an old console, lighter for phones. With voice-over off, subtitles stay on.',
  'Клавиши: F — весь экран, O — повернуть экран, V — озвучка, M — звук.': 'Keys: F — fullscreen, O — rotate screen, V — voice-over, M — sound.',
  'Проверено: {n} из 3': 'Checked: {n} of 3', 'Концовки: {a} из {b} · Смертей найдено: {c} из {d}': 'Endings: {a} of {b} · Deaths found: {c} of {d}', 'Смертей найдено: {c} из {d}': 'Deaths found: {c} of {d}',
  'Ошибка: ': 'Error: ', 'дальше': 'next', 'выбор': 'choice', 'мышь': 'mouse', 'свет и действия': 'light & actions', 'меню': 'menu', 'повернуть экран': 'rotate screen', 'озвучка': 'voice-over', 'Правила': 'Rules',
  // надписи в 3D-мире (текстуры)
  'БЦ  «ГОРИЗОНТ»': 'HORIZON  B.C.', 'ГОРИЗОНТ': 'HORIZON', 'Б И З Н Е С - Ц Е Н Т Р': 'B U S I N E S S   C E N T E R', 'ОХРАНА': 'SECURITY', 'ВЫХОД': 'EXIT', 'ПРАВИЛА': 'RULES',
  'ОТ СЕБЯ': 'PUSH', 'ОСТОРОЖНО': 'CAUTION', 'МОКРЫЙ': 'WET', 'ПОЛ': 'FLOOR', 'СЛАВА': 'SLAVA', 'ПРОПУСК': 'PASS', 'ДОЙЛ': 'DOYLE', 'Фрэнк': 'Frank', 'охрана': 'security', 'КЛИНИНГ': 'CLEANING',
  'СЕРВЕРНАЯ  −2': 'SERVER ROOM  −2', 'ПРАВИЛА СМЕНЫ': 'SHIFT RULES',
  '1. В 03:00 НА 7 ЭТАЖЕ': '1. AT 3:00 ON FLOOR 7', '   ВКЛЮЧИТСЯ СВЕТ.': '   THE LIGHTS COME ON.', '   НЕ ПОДНИМАЙСЯ.': '   DO NOT GO UP.',
  '2. ЛИФТ ПРИЕХАЛ САМ —': '2. ELEVATOR CAME BY', '   НЕ ЗАХОДИ.': '   ITSELF — STAY OUT.', '3. ПОСЛЕ ПОЛУНОЧИ': '3. AFTER MIDNIGHT', '   НИКОГО НЕ ВПУСКАЙ.': '   LET NO ONE IN.',
  '4. КАМЕРУ 13': '4. CAMERA 13 —', '   НЕ ВКЛЮЧАЙ.': '   NEVER TURN ON.', 'Третье правило — ложь.': 'Rule three is a lie.', 'Впусти её.   — М.': 'Let her in.   — M.',
};
const tr = s => LANG === 'en' && EN[s] != null ? EN[s] : s;
// подстановки: tr('Проверено: {n} из 3', { n: 2 })
const trf = (s, v) => tr(s).replace(/\{(\w+)\}/g, (m, k) => v[k] ?? m);
function trStory(S) { if (LANG !== 'en') return;
  (function w(o) { for (const k in o) { const v = o[k]; if (typeof v === 'string') { if (EN[v] != null) o[k] = EN[v]; } else if (v && typeof v === 'object') w(v); } })(S); }
window.NS_LANG = LANG; window.tr = tr; window.trf = trf; window.trStory = trStory;
})();
