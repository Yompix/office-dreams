# Hush Till Six в Steam — что делать по шагам

Здесь всё, чтобы выпустить игру в Steam. Часть уже сделана (сборки, достижения, геймпад, картинки, тексты), часть — только вы сами: аккаунт, оплата, загрузка.

> **Пароль от Steam и коды Steam Guard никому не давайте — ни Claude, ни «помощникам».** Claude они не нужны. Нужны только номера приложения и депо (App ID, Depot ID) — они не секретные, их можно прислать в чат.

## Что уже готово

- **Сборки для Steam:** папка `steam-windows` (Windows) и `steam-linux` (Linux/SteamOS) — содержимое двух депо. Собирает Claude: `node tools/desktop/build.js win linux --steam --out ПАПКА`.
- **Достижения** — 21 штука (таблица ниже). Игра открывает их сама, когда игрок дошёл до концовки или нашёл смерть. Без Steam игра работает так же, просто без достижений.
- **Геймпад и Steam Deck:** Xbox, PlayStation, Steam Deck — всё меню, выборы, смена, мини-игры, пауза. Текст читается на экране Deck 1280×800.
- **Сохранения в облаке (Steam Cloud):** игра пишет копию сохранений в файл `cloud/save.json` — Steam переносит его на другой компьютер (настройка — шаг 3).
- **Картинки** для страницы и библиотеки — нарисованы движком игры, без ИИ (список в шаге 4).
- **Тексты** страницы на английском и русском (шаг 3).

## Шаг 1. Аккаунт Steamworks (делаете вы)

1. Откройте https://partner.steamgames.com → **Join Steamworks** (Присоединиться) и войдите своим аккаунтом Steam. Steam Guard в аккаунте должен быть включён.
2. Заполните анкету: имя или название (как вы оформлены: физлицо, ИП, компания), адрес, **банковские реквизиты** для выплат, **налоговую анкету** (для физлиц не из США — форма W-8BEN, заполняется прямо на сайте, по шагам), подтверждение личности.
3. Оплатите **Steam Direct — $100 за игру.** Деньги вернутся с продаж, когда игра заработает $1000.
4. После оплаты Steam создаст приложение и даст **App ID** (число, например 3456780). Выпустить игру можно не раньше чем через 30 дней после оплаты, а страница «Скоро выйдет» должна провисеть минимум 2 недели.

## Шаг 2. Депо и запуск (Steamworks → ваше приложение)

1. **SteamPipe → Depots:** создайте два депо — «Hush Till Six Windows» (OS: Windows) и «Hush Till Six Linux» (OS: Linux + SteamOS). Запишите их номера (обычно App ID + 1 и + 2).
2. **Installation → General → Launch Options** — два варианта запуска:
   - Executable: `Hush Till Six.exe`, Operating System: **Windows**.
   - Executable: `hush-till-six`, Arguments: `--no-sandbox`, Operating System: **Linux + SteamOS** (без песочницы Chromium: в контейнере Steam её нет, а игра показывает только свои файлы; то же делает `start.sh` в сборке).
3. **Installation → Linux Runtime:** «Steam Linux Runtime 3.0 (sniper)».
4. После любых изменений: **Publish → Prepare for Publishing → Publish to Steam**.

## Шаг 3. Страница в магазине (Store Page Admin)

**Название:** Hush Till Six (на всех языках одинаково).

**Короткое описание (EN):** A night-shift horror story. Watch the cameras, follow the rules, and whatever calls your name — don't answer. Four endings, fourteen ways to die. Wait for six.

**Короткое описание (RU):** Хоррор про ночную смену. Смотри камеры, соблюдай правила и не отзывайся, если тебя зовут по имени. Четыре концовки, четырнадцать смертей. Дождись шести.

**Об игре (EN):**

> **Don't answer. Don't open. Wait for six.**
>
> You take the night shift at the Horizon business center — an empty office tower that closed a month ago. The guard before you, Frank, sat in that booth for twenty years. A week ago he never handed over his shift: the lamp was on, the tea had gone cold, and Frank was gone. The cameras didn't catch a thing.
>
> The day guard hands you the keys with a shaking hand and tells you to read the logbook before three. On the last page, in Frank's handwriting: *DON'T ANSWER TO YOUR NAME. DON'T OPEN THE DOOR TO IT. DON'T GO TO IT YOURSELF — NOT TO THE LIGHT, NOT IN THE ELEVATOR.*
>
> At half past three your little sister Lily is standing outside the glass doors. In the rain. Calling your name.
>
> - A 2–3 hour interactive horror story with timed choices, four endings and fourteen ways to die
> - Between story scenes you work the shift: watch the CCTV cameras, log anything wrong, run the elevator and the lights — and don't stare at CAM 13
> - Fully voiced in English, Russian, Spanish, Brazilian Portuguese and German; Ukrainian subtitles
> - Three looks: Ultra (native resolution up to 4K), Cinema and Retro (PS1-style); the game picks one for your graphics card
> - Full controller support, Steam Deck friendly. Headphones recommended
> - Original music composed in code
>
> **Content warning:** 18+. Violence, blood and gore, jump scares, flashing lights.

**Об игре (RU):**

> **Не отвечай. Не открывай. Дождись шести.**
>
> Ты выходишь в ночную смену в бизнес-центр «Горизонт» — пустую башню, закрытую месяц назад. Охранник до тебя, Фрэнк, двадцать лет просидел в этой будке. Неделю назад он не сдал смену: лампа горит, чай остыл, Фрэнка нет. Камеры ничего не записали.
>
> Сменщик отдаёт тебе ключи дрожащей рукой и велит прочитать журнал до трёх. На последней странице — почерком Фрэнка: *НЕ ОТЗЫВАЙСЯ НА ИМЯ. НЕ ОТКРЫВАЙ ЕМУ ДВЕРЬ. САМ К НЕМУ НЕ ХОДИ — НИ НА СВЕТ, НИ В ЛИФТ.*
>
> В половине четвёртого за стеклянной дверью стоит твоя младшая сестра Лили. Под дождём. И зовёт тебя по имени.
>
> - Интерактивный хоррор на 2–3 часа: выборы на время, четыре концовки и четырнадцать смертей
> - Между сюжетными сценами — смена: камеры наблюдения, журнал, лифт и свет на этажах; на CAM 13 не засматривайся
> - Полная озвучка на русском, английском, испанском, португальском (Бразилия) и немецком; субтитры на украинском
> - Три вида графики: «Ультра» (родное разрешение до 4K), «Кино» и «Ретро» (в духе PS1); игра сама выбирает под видеокарту
> - Полная поддержка геймпада и Steam Deck. Лучше в наушниках
> - Своя музыка, написанная кодом
>
> **Внимание:** 18+. Насилие, кровь, скримеры, мигающий свет.

**Страница магазина на других языках** (Store Page Admin → выбрать язык; тексты делали переводчики, `tools/i18n/<язык>.store.json`):

**Español — короткое описание:** Una historia de terror en el turno de noche. Vigila las cámaras, cumple las reglas y, si algo te llama por tu nombre, no respondas. Cuatro finales, catorce formas de morir. Espera a las seis.

**Español — об игре:**

> **No respondas. No abras. Espera a las seis.**
>
> Aceptas el turno de noche en el centro de negocios Horizon, una torre de oficinas vacía que cerró hace un mes. El vigilante anterior, Frank, pasó veinte años en esa caseta. Hace una semana no entregó el turno: la lámpara seguía encendida, el té se había enfriado y Frank no estaba. Las cámaras no grabaron nada.
>
> El vigilante de día te entrega las llaves con mano temblorosa y te pide que leas la bitácora antes de las tres. En la última página, con la letra de Frank: *NO RESPONDAS A TU NOMBRE. NO LE ABRAS LA PUERTA. NO TE ACERQUES TÚ — NI A LA LUZ, NI AL ASCENSOR.*
>
> A las tres y media de la madrugada, tu hermana menor, Lily, está al otro lado de las puertas de vidrio. Bajo la lluvia. Llamándote por tu nombre.
>
> - Una historia interactiva de terror de 2–3 horas, con decisiones contrarreloj, cuatro finales y catorce formas de morir
> - Entre escena y escena cubres el turno: vigila las cámaras de seguridad, anota todo lo que no cuadre, controla el ascensor y las luces… y no te quedes mirando la CAM 13
> - Voces completas en inglés, ruso y español, además de portugués de Brasil y alemán. Subtítulos en ucraniano
> - Tres estilos visuales: Ultra (resolución nativa hasta 4K), Cine y Retro (al estilo de PS1); el juego elige según tu tarjeta gráfica
> - Compatibilidad total con gamepad y Steam Deck. Se recomiendan auriculares
> - Música original escrita en código
>
> **Aviso de contenido:** 18+. Violencia, sangre y escenas explícitas, sustos repentinos y luces intermitentes.

**Português (Brasil) — короткое описание:** Uma história de terror no turno da noite. Vigie as câmeras, siga as regras e, seja lá o que chamar o seu nome, não responda. Quatro finais, quatorze jeitos de morrer. Espere até as seis.

**Português (Brasil) — об игре:**

> **Não responda. Não abra. Espere até as seis.**
>
> Você assume o turno da noite no centro empresarial Horizon — uma torre de escritórios vazia, fechada há um mês. O vigia antes de você, Frank, passou vinte anos naquela guarita. Uma semana atrás, ele não entregou o turno: a luminária acesa, o chá frio, e nada do Frank. As câmeras não gravaram nada.
>
> O vigia do dia te entrega as chaves com a mão tremendo e manda você ler o diário antes das três. Na última página, com a letra do Frank: *NÃO RESPONDA SE CHAMAREM SEU NOME. NÃO ABRA A PORTA PRA AQUILO. NÃO VÁ ATÉ AQUILO — NEM PRA LUZ, NEM PRO ELEVADOR.*
>
> Às três e meia da madrugada, sua irmã caçula, Lily, está do outro lado da porta de vidro. Na chuva. Chamando o seu nome.
>
> - Uma história interativa de terror de 2 a 3 horas, com escolhas contra o relógio, quatro finais e quatorze jeitos de morrer
> - Entre as cenas da história, você cumpre o turno: vigia as câmeras de segurança, anota tudo o que estiver errado, controla o elevador e as luzes — e não fica olhando para a CAM 13
> - Totalmente dublado em português do Brasil, inglês e russo — e também em espanhol e alemão. Legendas em ucraniano
> - Três visuais: Ultra (resolução nativa até 4K), Cinema e Retrô (no estilo do PS1); o jogo escolhe conforme a sua placa de vídeo
> - Suporte completo a controle, ótimo no Steam Deck. Recomendamos jogar com fones de ouvido
> - Trilha sonora original composta em código
>
> **Aviso de conteúdo:** 18+. Violência explícita, sangue, sustos repentinos e luzes piscando.

**Deutsch — короткое описание:** Eine Horrorgeschichte über eine Nachtschicht. Behalte die Kameras im Blick, halte dich an die Regeln — und was auch immer deinen Namen ruft: Antworte nicht. Vier Enden, vierzehn Arten zu sterben. Warte bis sechs.

**Deutsch — об игре:**

> **Antworte nicht. Mach nicht auf. Warte bis sechs.**
>
> Du trittst die Nachtschicht im Bürozentrum Horizon an — einem leeren Büroturm, der vor einem Monat geschlossen hat. Dein Vorgänger Frank saß zwanzig Jahre in dieser Kabine. Vor einer Woche gab es keine Übergabe: Die Lampe brannte, der Tee war kalt, und Frank war verschwunden. Die Kameras haben nichts aufgezeichnet.
>
> Der Wachmann der Tagschicht gibt dir mit zitternder Hand die Schlüssel und sagt, du sollst das Wachbuch vor drei lesen. Auf der letzten Seite, in Franks Handschrift: *ANTWORTE NICHT AUF DEINEN NAMEN. MACH IHM NICHT AUF. GEH NICHT SELBST ZU IHM — NICHT INS LICHT, NICHT IN DEN AUFZUG.*
>
> Um halb vier Uhr nachts steht deine kleine Schwester Lily vor der Glastür. Im Regen. Und ruft deinen Namen.
>
> - Interaktive Horrorgeschichte für 2–3 Stunden: Entscheidungen unter Zeitdruck, vier Enden und vierzehn Arten zu sterben
> - Zwischen den Story-Szenen arbeitest du die Schicht: Überwachungskameras im Blick behalten, Auffälliges ins Wachbuch eintragen, Aufzug und Licht steuern — und starr nicht zu lange auf CAM 13
> - Komplett vertont auf Englisch, Russisch und Deutsch, außerdem auf Spanisch und brasilianischem Portugiesisch; Untertitel auf Ukrainisch
> - Drei Grafikstile: Ultra (native Auflösung bis 4K), Kino und Retro (im Stil der PS1); das Spiel wählt passend zur Grafikkarte
> - Volle Controller-Unterstützung, Steam-Deck-freundlich. Kopfhörer empfohlen
> - Eigene Musik, im Code komponiert
>
> **Inhaltswarnung:** 18+. Gewalt, Blut und drastische Szenen, Jumpscares, Lichtblitze.

**Italiano — короткое описание:** Una storia horror nel turno di notte. Controlla le telecamere, segui le regole e, qualunque cosa ti chiami per nome, non rispondere. Quattro finali, quattordici modi di morire. Aspetta le sei.

**Italiano — об игре:**

> **Non rispondere. Non aprire. Aspetta le sei.**
>
> Prendi servizio di notte al centro d'affari Horizon, una torre di uffici vuota, chiusa da un mese. La guardia prima di te, Frank, ha trascorso vent'anni in quella guardiola. Una settimana fa non ha passato le consegne: la lampada era accesa, il tè si era raffreddato e Frank era sparito. Le telecamere non hanno registrato niente.
>
> La guardia diurna ti porge le chiavi con la mano che trema e ti dice di leggere il registro prima delle tre. Sull'ultima pagina, con la scrittura di Frank: *NON RISPONDERE AL TUO NOME. NON APRIRE LA PORTA A QUELLA COSA. NON ANDARCI TU STESSO — NÉ VERSO LA LUCE, NÉ IN ASCENSORE.*
>
> Alle tre e mezza di notte tua sorella minore, Lily, è fuori dalle porte a vetri. Sotto la pioggia. Ti chiama per nome.
>
> - Una storia horror interattiva di 2–3 ore, con scelte a tempo, quattro finali e quattordici modi di morire
> - Tra una scena e l'altra fai il tuo turno: sorveglia le telecamere a circuito chiuso, annota tutto ciò che non va, gestisci ascensore e luci… e non fissare la CAM 13
> - Doppiaggio completo in inglese, russo, spagnolo, portoghese brasiliano e tedesco; interfaccia e sottotitoli in italiano e ucraino
> - Tre stili grafici: Ultra (risoluzione nativa fino a 4K), Cinema e Retro (in stile PS1); il gioco sceglie in base alla tua scheda grafica
> - Supporto completo per controller, adatto a Steam Deck. Si consigliano le cuffie
> - Musica originale, composta direttamente nel codice
>
> **Avviso sui contenuti:** 18+. Violenza, sangue e scene cruente, jump scare e luci lampeggianti.

**Türkçe — короткое описание:** Bir gece vardiyası korku hikâyesi. Kameraları izle, kurallara uy ve adınla seslenen her ne olursa olsun cevap verme. Dört son, ölmenin on dört yolu. Altıyı bekle.

**Türkçe — об игре:**

> **Cevap verme. Kapıyı açma. Altıyı bekle.**
>
> Horizon İş Merkezi'nde gece vardiyasını devralıyorsun — bir ay önce kapanmış, bomboş bir ofis kulesi. Senden önceki görevli Frank, yirmi yıl boyunca o güvenlik kulübesinde oturdu. Bir hafta önce vardiyasını devretmedi: lamba yanıyordu, çay soğumuştu, Frank ise ortada yoktu. Kameralar hiçbir şey kaydetmemişti.
>
> Gündüz nöbetçisi anahtarları sana titreyen eliyle uzatıyor ve defteri saat üçten önce okumanı söylüyor. Son sayfada, Frank'in el yazısıyla: *ADINA CEVAP VERME. ONA KAPIYI AÇMA. ONA KENDİ AYAĞINLA GİTME — NE IŞIĞA, NE ASANSÖRE.*
>
> Üç buçukta küçük kız kardeşin Lily cam kapının dışında duruyor. Yağmurun altında. Ve adınla sesleniyor.
>
> - 2–3 saatlik interaktif korku hikâyesi: süreli seçimler, dört son, ölmenin on dört yolu
> - Hikâye sahneleri arasında vardiyayı sen yürütüyorsun: güvenlik kameralarını izle, ters giden her şeyi deftere kaydet, asansörü ve ışıkları yönet — ve CAM 13'e dik dik bakma
> - İngilizce, Rusça, İspanyolca, Brezilya Portekizcesi ve Almanca tam seslendirme; Türkçe ve Ukraynaca altyazı ve arayüz
> - Üç görsel stil: Ultra (4K'ya kadar yerel çözünürlük), Sinema ve Retro (PS1 tarzı); oyun, ekran kartına göre birini kendisi seçer
> - Tam denetleyici (gamepad) desteği, Steam Deck ile uyumlu. Kulaklık önerilir
> - Kodla bestelenmiş özgün müzik
>
> **İçerik uyarısı:** 18+. Şiddet, kan ve vahşet, ani korkutmalar, yanıp sönen ışıklar.

**Polski — короткое описание:** Horror o nocnej zmianie. Obserwuj kamery, przestrzegaj zasad, a gdy coś zawoła cię po imieniu — nie odpowiadaj. Cztery zakończenia, czternaście sposobów, by umrzeć. Doczekaj do szóstej.

**Polski — об игре:**

> **Nie odpowiadaj. Nie otwieraj. Doczekaj do szóstej.**
>
> Bierzesz nocną zmianę w Centrum Biznesowym Horizon — pustym biurowcu, który zamknięto miesiąc temu. Ochroniarz przed tobą, Frank, przesiedział w tej budce dwadzieścia lat. Tydzień temu nie zdał zmiany: lampa się paliła, herbata wystygła, a Franka nie było. Kamery nic nie nagrały.
>
> Dzienny ochroniarz podaje ci klucze drżącą ręką i każe przeczytać dziennik do trzeciej. Na ostatniej stronie, pismem Franka: *NIE ODPOWIADAJ, GDY WOŁAJĄ CIĘ PO IMIENIU. NIE OTWIERAJ MU DRZWI. SAM DO NIEGO NIE CHODŹ — ANI DO ŚWIATŁA, ANI DO WINDY.*
>
> O wpół do czwartej za szklanymi drzwiami stoi twoja młodsza siostra Lily. W deszczu. I woła cię po imieniu.
>
> - Interaktywna opowieść grozy na 2–3 godziny: wybory na czas, cztery zakończenia i czternaście sposobów, by umrzeć
> - Między scenami fabularnymi pracujesz na zmianie: obserwujesz kamery monitoringu, zapisujesz w dzienniku wszystko, co nie tak, obsługujesz windę i światło na piętrach — i nie wpatrujesz się w CAM 13
> - Pełna oprawa głosowa po angielsku, rosyjsku, hiszpańsku, portugalsku (Brazylia) i niemiecku; polskie napisy i interfejs
> - Trzy style grafiki: Ultra (natywna rozdzielczość do 4K), Kino i Retro (w stylu PS1); gra sama dobiera styl do twojej karty graficznej
> - Pełna obsługa kontrolera, dobrze działa na Steam Deck. Zalecane słuchawki
> - Oryginalna muzyka skomponowana w kodzie
>
> **Ostrzeżenie o treści:** 18+. Przemoc, krew i drastyczne sceny, jump scare'y, migające światło.

**简体中文 — короткое описание:** 一个关于夜班的恐怖故事。盯紧摄像头，遵守规则，无论什么东西在叫你的名字，都别答应。四个结局，十四种死法。等到六点。

**简体中文 — об игре:**

> **别答应。别开门。等到六点。**
>
> 你接下了地平线商务中心的夜班——一座空荡荡的办公大楼，一个月前就已关闭。你的前任弗兰克，在那个岗亭里坐了二十年。一周前，他没有来交班：灯亮着，茶凉透了，弗兰克不见了。摄像头什么也没拍到。
>
> 白班保安用发抖的手把钥匙交给你，叮嘱你三点之前读完值班日志。最后一页上，是弗兰克的笔迹：*有人叫你名字，别答应。别给它开门。别自己去找它——别走向亮光，别进电梯。*
>
> 凌晨三点半，你的妹妹莉莉站在玻璃门外。淋着雨。叫着你的名字。
>
> - 2–3 小时的互动恐怖故事：限时抉择、四个结局、十四种死法
> - 剧情场景之间，你要亲自值班：盯着监控摄像头，记录异常，操作电梯和各层灯光——还有，别盯着 CAM 13 看
> - 完整配音：英语、俄语、西班牙语、巴西葡萄牙语和德语；界面与字幕提供简体中文（另有乌克兰语字幕）
> - 三种画面风格：超高画质（原生分辨率，最高 4K）、电影画质和复古画质（PS1 风格）；游戏会根据你的显卡自动选择
> - 完整支持手柄，适配 Steam Deck。建议佩戴耳机
> - 原创音乐，全部由代码谱写
>
> **内容警告：** 18+。暴力、血腥与残酷画面，突然惊吓，闪烁灯光。

**Français — короткое описание:** Une histoire d’horreur en service de nuit. Surveille les caméras, respecte les règles et, si quelque chose t’appelle par ton nom, ne réponds pas. Quatre fins, quatorze façons de mourir. Tiens jusqu’à six heures.

**Français — об игре:**

> **Ne réponds pas. N’ouvre pas. Tiens jusqu’à six heures.**
>
> Tu prends le service de nuit au centre d’affaires Horizon, une tour de bureaux vide, fermée il y a un mois. Le gardien avant toi, Frank, a passé vingt ans dans cette loge. Il y a une semaine, il n’a pas passé le relais : la lampe était allumée, le thé froid, et Frank avait disparu. Les caméras n’ont rien enregistré.
>
> Le gardien de jour te tend les clés d’une main tremblante et te dit de lire le journal avant trois heures. Sur la dernière page, de l’écriture de Frank : *NE RÉPONDS PAS À TON NOM. NE LUI OUVRE PAS LA PORTE. NE VA PAS À SA RENCONTRE — NI VERS LA LUMIÈRE, NI DANS L’ASCENSEUR.*
>
> À trois heures et demie, ta petite sœur Lily se tient derrière la porte vitrée. Sous la pluie. Elle t’appelle par ton nom.
>
> - Une histoire d’horreur interactive de 2 à 3 heures : choix chronométrés, quatre fins et quatorze façons de mourir
> - Entre les scènes de l’histoire, tu assures ton service : surveille les caméras, note tout ce qui cloche, gère l’ascenseur et les lumières… et ne fixe pas la CAM 13
> - Doublage complet en anglais, russe, espagnol, portugais brésilien et allemand ; interface et sous-titres en français et en ukrainien
> - Trois rendus visuels : Ultra (résolution native jusqu’en 4K), Cinéma et Rétro (style PS1) ; le jeu choisit selon ta carte graphique
> - Manette entièrement prise en charge, compatible Steam Deck. Casque recommandé
> - Musique originale écrite en code
>
> **Avertissement de contenu :** 18+. Violence, sang et scènes gore, jump scares, lumières clignotantes.

**日本語 — короткое описание:** 夜勤の警備員を描くホラー。カメラを見張り、ルールを守り、名前を呼ぶ何かには返事をするな。エンディング4つ、死に方14通り。6時まで耐えろ。

**日本語 — об игре:**

> **返事をするな。開けるな。6時まで待て。**
>
> あなたは、ホライゾン・ビジネスセンターの夜勤につく。一か月前に閉鎖された、無人のオフィスビルだ。前任の警備員フランクは、二十年間あの守衛室に座り続けた。一週間前、彼は引き継ぎに現れなかった。ランプはついたまま、お茶は冷め、フランクの姿だけが消えていた。カメラには何も映っていなかった。
>
> 日勤の警備員は震える手で鍵を渡し、3時までに日誌を読んでおけと告げる。最後のページには、フランクの筆跡でこう書かれている。*名前を呼ばれても返事をするな。それにはドアを開けるな。自分から近づくな――明かりにも、エレベーターにも。*
>
> 午前3時半。ガラスのドアの向こうに、妹のリリーが立っている。雨の中で、あなたの名前を呼びながら。
>
> - 2〜3時間のインタラクティブ・ホラー。制限時間つきの選択、4つのエンディング、14通りの死に方
> - ストーリーの合間には、実際に勤務をこなす。監視カメラを見張り、異常を日誌に記録し、エレベーターと照明を操作する――そして、CAM 13を見つめすぎるな
> - 英語、ロシア語、スペイン語、ブラジル・ポルトガル語、ドイツ語のフルボイス。インターフェースと字幕は日本語に対応
> - 3種類のグラフィック：ウルトラ（最大4Kのネイティブ解像度）、シネマ、レトロ（PS1風）。ゲームがグラフィックボードに合わせて自動で選択
> - コントローラー完全対応、Steam Deckにも最適。ヘッドホン推奨
> - 音楽はすべてオリジナル（コードで作曲）
>
> **内容に関する警告：** 18歳以上向け。暴力、流血・残酷な描写、ジャンプスケア、点滅する光。

**한국어 — короткое описание:** 야간 근무 호러 스토리. 카메라를 지켜보고 규칙을 따르라. 이름을 부르는 것이 있어도 대답하지 마라. 엔딩 4개, 죽음 14가지. 6시까지 기다려라.

**한국어 — об игре:**

> **대답하지 마라. 열지 마라. 6시까지 기다려라.**
>
> 당신은 한 달 전 문을 닫은 텅 빈 오피스 타워, 호라이즌 비즈니스 센터의 야간 근무를 맡는다. 앞서 근무한 경비원 프랭크는 20년 동안 그 경비실을 지켰다. 일주일 전, 그는 근무를 인계하지 않았다. 스탠드는 켜져 있었고, 차는 식어 있었고, 프랭크는 사라지고 없었다. 카메라에는 아무것도 찍히지 않았다.
>
> 주간 경비원은 떨리는 손으로 열쇠를 건네며 3시 전에 일지를 읽으라고 말한다. 마지막 장에는 프랭크의 필체로 이렇게 적혀 있다. *이름을 불러도 대답하지 마라. 그것에게 문을 열어 주지 마라. 제 발로 그것에게 가지 마라 — 빛 쪽으로도, 엘리베이터 안으로도.*
>
> 새벽 3시 반, 유리문 밖에 어린 여동생 릴리가 서 있다. 빗속에서. 당신의 이름을 부르며.
>
> - 2~3시간 분량의 인터랙티브 호러 스토리: 제한 시간이 있는 선택, 엔딩 4개, 죽음 14가지
> - 스토리 장면 사이사이에는 근무를 선다. CCTV 카메라를 감시하고, 이상한 점은 모두 일지에 기록하고, 엘리베이터와 조명을 다룬다 — 그리고 CAM 13은 오래 쳐다보지 마라
> - 영어, 러시아어, 스페인어, 브라질 포르투갈어, 독일어 풀 보이스. 한국어 인터페이스와 자막
> - 세 가지 화면 스타일: 울트라(최대 4K 네이티브 해상도), 시네마, 레트로(PS1 스타일). 그래픽 카드에 맞춰 게임이 알아서 고른다
> - 게임패드 완벽 지원, 스팀 덱 대응. 헤드폰 착용 권장
> - 코드로 작곡한 오리지널 음악
>
> **콘텐츠 경고:** 18+. 폭력, 유혈과 잔혹한 장면, 갑작스러운 점프 스케어, 깜박이는 빛.

**Языки:** English, Русский, Español (España/Latinoamérica), Português (Brasil), Deutsch — интерфейс, озвучка, субтитры; Français, Italiano, Polski, Türkçe, Українська, 简体中文, 日本語, 한국어 — интерфейс и субтитры (в Steamworks → Supported Languages отметить для них Interface и Subtitles).

**Жанры:** Adventure, Indie. **Метки (tags):** Horror, Psychological Horror, Interactive Fiction, Multiple Endings, Choices Matter, Story Rich, Atmospheric, Singleplayer, Mystery, Dark, Gore, Short, 3D.

**Системные требования (Windows):**
- Минимум: Windows 10 64-bit, процессор 2 ядра 2 ГГц, 4 ГБ памяти, видеокарта с WebGL 2: Intel HD 520 / UHD 620, GeForce GTX 750, Radeon R7 — графика «Ретро» (игра включит её сама), 1 ГБ на диске.
- Рекомендуется: Windows 10/11 64-bit, 4 ядра, 8 ГБ памяти, GeForce GTX 1050 / Radeon RX 560 / Intel Iris Xe и новее — графика «Кино».
- Для «Ультра»: GeForce RTX 2060 / Radeon RX 5700 и новее — родное разрешение экрана до 4K, тени и отражения чётче.
- Steam Deck: «Кино», 1280×720 (если кадры тяжёлые — разрешение снижается само, картинка подрезкивается). Видеокарту игра определяет при первом запуске; выбрать вручную — Настройки → Графика.
- Linux / SteamOS: 64-bit, остальное так же.

**Анкета о содержании (Mature Content Survey) — 18+:**
- Frequent Violence or Gore — **да** (кровь, смерти героя, монстр).
- General Mature Content — **да**.
- Nudity / Sexual Content — **нет**.
- Описание (EN): *Graphic violence and blood, death scenes, jump scares and flashing lights.* (RU): *Жестокость и кровь, сцены смерти, скримеры и мигающий свет.*

**Геймпад:** Store Page Admin → «Controller support»: **Full Controller Support**, отметить Xbox и PlayStation. Steamworks → **Steam Input**: «Default configuration» — **Gamepad** (стандартная раскладка), Steam Input API не нужен.

**Steam Cloud** (Steamworks → Application → Steam Cloud):
- Byte quota per user: `1048576`, Number of files per user: `10`.
- **Auto-Cloud**, Root Paths: Root `WinAppDataRoaming`, Subdirectory `Hush Till Six/cloud`, Pattern `*.json`, OS: Windows.
- Root Overrides: Original Root `WinAppDataRoaming` → New Root `LinuxXdgConfigHome`, OS: Linux (путь тот же: `Hush Till Six/cloud`).
- Steam Deck с Windows-сборкой (Proton) берёт путь Windows — отдельно ничего не нужно.

## Шаг 4. Картинки (Store Page Admin → Graphical Assets и Library Assets)

Все картинки нарисованы движком игры (кадры «Кино») и шрифтом игры — ИИ не участвовал. Пересобрать: `node tools/desktop/steam/kit.js ПАПКА frames shots`, затем `python3 tools/desktop/steam/kit.py ПАПКА`.

| Файл | Размер | Куда |
|---|---|---|
| `store/header_capsule.jpg` | 920×430 | Header Capsule |
| `store/small_capsule.jpg` | 462×174 | Small Capsule |
| `store/main_capsule.jpg` | 1232×706 | Main Capsule |
| `store/vertical_capsule.jpg` | 748×896 | Vertical Capsule |
| `library/library_capsule.jpg` | 600×900 | Library Capsule |
| `library/library_hero.jpg` | 3840×1240 | Library Hero (без надписей) |
| `library/library_logo.png` | прозрачный | Library Logo (поставить слева внизу) |
| `screenshots/*_en.png`, `*_ru.png` | 1920×1080 | Screenshots (минимум 5; английские — для всех, русские — для русской страницы) |
| `achievements/*.jpg` | 256×256 | иконки достижений (`*_locked.jpg` — «не получено») |
| `icons/community_icon.jpg` | 184×184 | Community Icon |
| `icons/client_icon.ico` | 16/32 | Client Icon |

Трейлеры у вас уже есть (YouTube 16:9, RU/EN) — их можно загрузить в Store Page Admin → Trailers. В финальной карточке трейлера стоит «Скоро в Steam» — когда будет ссылка, Claude пересоберёт.

## Шаг 5. Достижения (Steamworks → Stats & Achievements → Achievements)

Добавьте 21 достижение **с такими API Name** (их ждёт игра, менять нельзя). «Скрытое» — описание видно только после получения (чтобы не было спойлеров). Описание смерти — та же подсказка, что игра пишет на экране смерти.

| API Name | Название RU / EN | Описание RU / EN | Скрытое |
|---|---|---|---|
| `END_INSIDE` | Внутри / Inside | Концовка «Внутри». / Reach the ending “Inside”. | да |
| `END_SISTER` | Сестра / Sister | Концовка «Сестра». / Reach the ending “Sister”. | да |
| `END_DAWN` | Рассвет / Dawn | Концовка «Рассвет». / Reach the ending “Dawn”. | да |
| `END_NEWENTRY` | Новая запись / New Entry | Концовка «Новая запись». / Reach the ending “New Entry”. | да |
| `DEATH_01` | Ты посмотрел ему в лицо / You Looked It in the Face | Не смотри в тринадцатую камеру. / Don't look at camera thirteen. | да |
| `DEATH_02` | Ты пошёл на свет / You Went to the Light | Сам к нему не ходи — ни на свет, ни в лифт. / Don't go to it yourself — not to the light, not in the elevator. | да |
| `DEATH_03` | Ты отозвался / You Answered the Radio | Гас не знал твоего имени. А оно знает. / Gus didn't know your name. It does. | да |
| `DEATH_04` | Оно обернулось / It Turned Around | Тринадцатая включилась сама — выдёргивай шнур, не смотри. / Thirteen turned on by itself — pull the cord, don't look. | да |
| `DEATH_05` | Оно услышало / It Heard You | Оно ищет на звук. Пока шаги рядом — не дыши. / It hunts by sound. While the footsteps are near — don't breathe. | да |
| `DEATH_06` | Темнота догнала / The Dark Caught Up | Остановишься — догонит. / Stop, and it catches you. | да |
| `DEATH_07` | Ты пошёл на зов / You Followed the Call | Оно умеет звать. Стучать — нет. / It knows how to call. Not how to knock. | да |
| `DEATH_08` | Фрэнк ушёл на голос / Frank Went to the Voice | Держи его, пока голос не стихнет. / Hold him until the voice fades. | да |
| `DEATH_09` | Ты ответил / You Answered the Call | Свои до утра подождут. / Your own can wait till morning. | да |
| `DEATH_10` | Оно пришло / It Came | Три странности за час остались без внимания. / Three anomalies in an hour went unnoticed. | да |
| `DEATH_11` | Ты засмотрелся / You Stared Too Long | Там, где помехи, кто-то стоит. / Someone is standing where the static is. | да |
| `DEATH_12` | Ты назвался / You Said Your Name | Оно знает твоё имя. Ему нужно только, чтобы ты ответил. / It knows your name. All it needs is for you to answer. | да |
| `DEATH_13` | Ты открыл ей / You Let Her In | Посторонних не впускать. / No unauthorized visitors. | да |
| `DEATH_14` | Ты взял трубку / You Picked Up | Звонили не тебе. Но ответил — ты. / The call wasn't for you. But you're the one who answered. | да |
| `ALL_ENDINGS` | Все концовки / Every Ending | Открой все четыре концовки. / Unlock all four endings. | нет |
| `ALL_DEATHS` | Четырнадцать способов умереть / Fourteen Ways to Die | Найди все 14 смертей. / Find all 14 deaths. | нет |
| `NIGHT_DONE` | Конец смены / End of Shift | Доживи до утра — до любой концовки. / Make it through the night to any ending. | нет |

Иконки: `achievements/<API Name>.jpg` (получено) и `achievements/<API Name>_locked.jpg` (не получено). После добавления — **Publish**.

Номер смерти — её место в списке смертей игры (`S.overs` в `story.js`); если список изменится, таблицу и `ACH` в `tools/desktop/app/main.js` нужно поправить вместе.

## Шаг 6. Загрузить сборку (делаете вы, со своего компьютера)

1. Скачайте **Steamworks SDK** (partner.steamgames.com → SDK → Download) и распакуйте.
2. В папке `sdk\tools\ContentBuilder\`:
   - в `scripts\` положите `app_build.vdf`, `depot_build_windows.vdf`, `depot_build_linux.vdf` из этой папки и замените в них номера `1234560` (App ID), `1234561` (депо Windows), `1234562` (депо Linux) на свои — или пришлите номера Claude, он пришлёт готовые файлы;
   - в `content\windows\` — всё содержимое папки `steam-windows`, в `content\linux\` — содержимое `steam-linux`.
3. Запустите `ContentBuilder\builder\steamcmd.exe`. В его окне наберите `login ВАШ_ЛОГИН` — пароль и код Steam Guard вводите **сами** в этом окне. Потом: `run_app_build ..\scripts\app_build.vdf`, дождитесь «Success», затем `quit`.
   (Можно и без команд: в SDK есть **SteamPipe GUI** — `sdk\tools\SteamPipeGUI.zip`: указать App ID, депо, папки и нажать Upload.)
4. Steamworks → **SteamPipe → Builds**: появится новая сборка. Сначала поставьте её на ветку `beta` или проверьте как разработчик: в своей библиотеке Steam установите игру и проверьте — запуск, звук, геймпад, достижение (умереть один раз — должно всплыть уведомление), Shift+Tab (оверлей), выход и повторный вход (сохранения).
5. Всё хорошо — **Set build live** на ветке `default`.

Для проверки у себя без загрузки: положите рядом с `Hush Till Six.exe` файл `steam_appid.txt` с вашим App ID, запустите Steam, затем игру. В папке `%APPDATA%\Hush Till Six` появится `steam.log` — там написано, подключился ли Steam. Перед загрузкой этот файл не нужен (сборщик его и так пропустит).

## Шаг 7. Steam Deck

Steam проверяет игры на Deck сам (Steamworks → **Steam Deck Compatibility** → можно запросить проверку). Что проверяют и что у нас:
- **Управление:** всё работает с геймпада — меню, выборы, смена (LB/RB — камеры, X — записать, Y — пульт, View — журнал), мини-игры, пауза (Start). На экране геймпада подсказки и рамка выбора.
- **Экран:** 1280×800 — текст читается, интерфейс помещается (проверено снимками).
- **Без лишнего:** нет лаунчера, сразу игра на весь экран; клавиатура не нужна.
- **Система:** Windows-сборка идёт через Proton, есть и родная Linux-сборка.

Если на Deck будет тормозить «Кино» — игра сама предложит «Ретро».

## Шаг 8. Цена и выпуск

- **Цена:** на itch.io игра стоит $3. В Steam для истории на 2–3 часа обычно ставят **$3.99–4.99**; региональные цены Steam подставит сам (для России, Украины, Турции и т. д. дешевле). На первую неделю — скидка 10–20 % (Steamworks → Discounts).
- **Порядок:** заполнить страницу → отправить её на проверку (2–5 рабочих дней) → **Coming Soon** (минимум 2 недели, собирает «желаемое») → загрузить сборку и отправить на проверку → в день выхода нажать **Release**.
- Можно сделать **бесплатную демо-версию** в Steam (отдельное приложение «Hush Till Six Demo», та же логика, что на itch: до 03:30) — скажите Claude, он соберёт.

## Что прислать Claude

- App ID и номера двух депо (не секрет) — Claude впишет их в `*.vdf` и поменяет в трейлере «Скоро в Steam» на ссылку.
- Решения: цена, дата выхода, нужна ли демо-версия.
- **Не присылать:** пароль, коды Steam Guard, данные банка и налоговые формы — это только вы и только на сайте Steam.

## Для Claude: сборка и проверка

```
cd tools/desktop && npm ci
node build.js win --out OUT            # OUT/Hush Till Six/ + HushTillSix-Windows-v1.0.0.zip + «Hush Till Six Setup 1.0.0.exe» (itch.io и сайт)
node build.js linux --out OUT          # OUT/hush-till-six-linux/ + HushTillSix-Linux-v1.0.0.zip
node build.js win linux --steam --out OUT   # OUT/steam-windows, OUT/steam-linux — содержимое депо Steam (со steamworks.js)
node build.js linux --test --steam --out T && xvfb-run -a -s "-screen 0 1920x1200x24" node check.js T/steam-linux SHOTS
```

- Steam включается, только если игру запустил Steam (переменная `SteamAppId`) или рядом с .exe лежит `steam_appid.txt`; иначе — обычная игра. Ошибка подключения — в `steam.log` профиля.
- Достижения: игра (`engine.js`) зовёт `DESK.ach('END_…' / 'DEATH_NN' / 'NIGHT_DONE')`; «все концовки» и «все смерти» считает `main.js` по своей копии `achievements.json` (полученные без Steam досылаются при первом запуске со Steam).
- Сохранения: `preload.js` копирует `ns1_*` из localStorage в `cloud/save.json` (раз в 15 с и при выходе); при запуске файл новее своей последней записи — берётся он (пришёл через Steam Cloud).
