#!/usr/bin/env python3
"""Страница Hush Till Six в Google Play на 13 языках: название, краткое описание (≤ 80 знаков), полное (≤ 4000),
что нового в выпуске. Сюжетные абзацы — из переводов страницы Steam (tools/i18n/<язык>.store.json, совпадают с игрой),
список «что в игре» — под телефон (касание, геймпад, без интернета, графика по силам телефона, без рекламы и сбора данных).
Без Markdown: Play показывает текст как есть.
  python3 tools/android/listing.py  →  tools/android/listing/<язык Play>.txt и tools/android/listing/ALL.md
"""
import json, os, re

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '../..'))
OUT = os.path.join(ROOT, 'tools/android/listing')
TITLE = 'Hush Till Six'                     # название игры всегда по-английски

EN = {
    'short': "A night-shift horror. Don't answer. Don't open. Wait for six.",
    'story': ["Don't answer. Don't open. Wait for six.",
              "You take the night shift at the Horizon business center — an empty office tower that closed a month ago. The guard before you, Frank, sat in that booth for twenty years. A week ago he never handed over his shift: the lamp was on, the tea had gone cold, and Frank was gone. The cameras didn't catch a thing.",
              "The day guard hands you the keys with a shaking hand and tells you to read the logbook before three. On the last page, in Frank's handwriting: DON'T ANSWER TO YOUR NAME. DON'T OPEN THE DOOR TO IT. DON'T GO TO IT YOURSELF — NOT TO THE LIGHT, NOT IN THE ELEVATOR.",
              "At half past three your little sister Lily is standing outside the glass doors. In the rain. Calling your name."],
    'bullets': ["A 2–3 hour interactive horror story with timed choices, four endings and 14 ways to die",
                "Between story scenes you work the shift: watch the CCTV cameras, log anything wrong, run the elevator and the lights — and don't stare at CAM 13",
                "Voiced in English, Russian, Spanish, Portuguese and German; subtitles in 13 languages",
                "Three looks: Retro (low-poly PS1 style), Cinema and Ultra; the game picks what your phone can run",
                "Portrait or landscape, touch or gamepad. Works offline. Headphones recommended",
                "No ads, no accounts, no data collection"],
    'warn': 'Content warning: 18+. Violence, blood and gore, jump scares, flashing lights ("Fewer flashes" option in settings).',
    'notes': 'First test build. Please play through at least one ending and tell us about crashes, slowdowns or anything that looks wrong.',
}
RU = {
    'short': 'Ночная смена и хоррор. Не отвечай. Не открывай. Дождись шести.',
    'story': ['Не отвечай. Не открывай. Дождись шести.',
              'Ты выходишь в ночную смену в бизнес-центр «Горизонт» — пустую башню, закрытую месяц назад. Охранник до тебя, Фрэнк, двадцать лет просидел в этой будке. Неделю назад он не сдал смену: лампа горит, чай остыл, Фрэнка нет. Камеры ничего не записали.',
              'Сменщик отдаёт тебе ключи дрожащей рукой и велит прочитать журнал до трёх. На последней странице — почерком Фрэнка: НЕ ОТЗЫВАЙСЯ НА ИМЯ. НЕ ОТКРЫВАЙ ЕМУ ДВЕРЬ. САМ К НЕМУ НЕ ХОДИ — НИ НА СВЕТ, НИ В ЛИФТ.',
              'В половине четвёртого ночи за стеклянной дверью стоит твоя младшая сестра Лили. Под дождём. И зовёт тебя по имени.'],
    'bullets': ['Интерактивный хоррор на 2–3 часа: выборы на время, четыре концовки и 14 смертей',
                'Между сюжетными сценами — смена: камеры наблюдения, журнал, лифт и свет на этажах; на CAM 13 не засматривайся',
                'Озвучка на русском, английском, испанском, португальском и немецком; субтитры на 13 языках',
                'Три графики: «Ретро» (в духе PS1), «Кино» и «Ультра»; игра сама подбирает, что потянет телефон',
                'Горизонтально или вертикально, касание или геймпад. Работает без интернета. Лучше в наушниках',
                'Без рекламы, без аккаунтов, без сбора данных'],
    'warn': 'Внимание: 18+. Насилие, кровь и жестокость, скримеры, мигающий свет (в настройках есть «Меньше вспышек»).',
    'notes': 'Первая тестовая сборка. Пройдите хотя бы одну концовку и напишите о вылетах, тормозах или обо всём, что выглядит не так.',
}
UK = {
    'short': 'Нічна зміна й хорор. Не озивайся. Не відчиняй. Дочекайся шостої.',
    'story': ['Не озивайся. Не відчиняй. Дочекайся шостої.',
              'Ти виходиш на нічну зміну в бізнес-центр «Горизонт» — порожню вежу, яку закрили місяць тому. Охоронець до тебе, Френк, двадцять років просидів у цій будці. Тиждень тому він не здав зміну: лампа горить, чай вистиг, Френка немає. Камери нічого не записали.',
              'Змінник віддає тобі ключі тремтячою рукою й каже прочитати журнал до третьої. На останній сторінці — почерком Френка: НЕ ОЗИВАЙСЯ НА ІМ’Я. НЕ ВІДЧИНЯЙ ЙОМУ ДВЕРІ. САМ ДО НЬОГО НЕ ХОДИ — НІ НА СВІТЛО, НІ В ЛІФТ.',
              'О пів на четверту ночі за скляними дверима стоїть твоя молодша сестра Лілі. Під дощем. І кличе тебе на ім’я.'],
    'bullets': ['Інтерактивний хорор на 2–3 години: вибори на час, чотири фінали й 14 смертей',
                'Між сюжетними сценами — зміна: камери спостереження, журнал, ліфт і світло на поверхах; на CAM 13 не задивляйся',
                'Озвучення англійською, російською, іспанською, португальською та німецькою; субтитри 13 мовами, зокрема українською',
                'Три графіки: «Ретро» (у дусі PS1), «Кіно» та «Ультра»; гра сама добирає, що потягне телефон',
                'Вертикально чи горизонтально, дотик або геймпад. Працює без інтернету. Краще в навушниках',
                'Без реклами, без акаунтів, без збору даних'],
    'warn': 'Увага: 18+. Насильство, кров і жорстокість, скримери, мигтливе світло (у налаштуваннях є «Менше спалахів»).',
    'notes': 'Перша тестова збірка. Пройдіть хоча б один фінал і напишіть про вильоти, гальмування чи все, що виглядає не так.',
}
# остальные языки: сюжет — из перевода страницы Steam, «что в игре» (пункты 3–6), предупреждение, краткое описание — здесь
MORE = {
    'es': {'short': 'Terror en el turno de noche. No respondas. No abras. Espera a las seis.',
           'bullets': ['Voces en inglés, ruso, español, portugués y alemán; subtítulos en 13 idiomas',
                       'Tres estilos visuales: Retro (low poly al estilo de PS1), Cine y Ultra; el juego elige lo que tu teléfono puede mover',
                       'En vertical u horizontal, con pantalla táctil o mando. Funciona sin conexión. Se recomiendan auriculares',
                       'Sin anuncios, sin cuentas y sin recopilación de datos'],
           'warn': 'Aviso de contenido: 18+. Violencia, sangre y escenas explícitas, sustos repentinos y luces intermitentes (en los ajustes hay una opción «Menos destellos»).',
           'notes': 'Primera versión de prueba. Juega al menos hasta un final y cuéntanos si hay cierres inesperados, tirones o algo que se vea mal.'},
    'pt': {'short': 'Terror no turno da noite. Não responda. Não abra. Espere até as seis.',
           'bullets': ['Dublado em inglês, russo, espanhol, português e alemão; legendas em 13 idiomas',
                       'Três estilos visuais: Retrô (low poly no estilo do PS1), Cinema e Ultra; o jogo escolhe o que o seu celular aguenta',
                       'Na vertical ou na horizontal, com toque ou controle. Funciona offline. Use fones de ouvido',
                       'Sem anúncios, sem contas, sem coleta de dados'],
           'warn': 'Aviso de conteúdo: 18+. Violência explícita, sangue, sustos repentinos e luzes piscando (nas configurações há a opção "Menos flashes").',
           'notes': 'Primeira versão de teste. Jogue pelo menos até um final e conte para a gente sobre travamentos, lentidão ou qualquer coisa estranha.'},
    'de': {'short': 'Horror in der Nachtschicht. Antworte nicht. Mach nicht auf. Warte bis sechs.',
           'bullets': ['Vertonung auf Englisch, Russisch, Spanisch, Portugiesisch und Deutsch; Untertitel in 13 Sprachen',
                       'Drei Grafikstile: Retro (Low-Poly im PS1-Stil), Kino und Ultra; das Spiel wählt, was dein Handy schafft',
                       'Hoch- oder Querformat, Touch oder Gamepad. Funktioniert offline. Kopfhörer empfohlen',
                       'Keine Werbung, keine Konten, keine Datenerfassung'],
           'warn': 'Inhaltswarnung: 18+. Gewalt, Blut und drastische Szenen, Jumpscares, Lichtblitze (in den Einstellungen gibt es „Weniger Blitze“).',
           'notes': 'Erste Testversion. Spiel bitte mindestens bis zu einem Ende und melde Abstürze, Ruckler oder alles, was falsch aussieht.'},
    'fr': {'short': 'Horreur de nuit. Ne réponds pas. N’ouvre pas. Tiens jusqu’à six heures.',
           'bullets': ['Doublage en anglais, russe, espagnol, portugais et allemand ; sous-titres en 13 langues',
                       'Trois styles graphiques : Rétro (low poly façon PS1), Cinéma et Ultra ; le jeu choisit ce que ton téléphone peut faire tourner',
                       'En portrait ou en paysage, au tactile ou à la manette. Fonctionne hors ligne. Casque recommandé',
                       'Pas de pub, pas de compte, aucune collecte de données'],
           'warn': 'Avertissement de contenu : 18+. Violence, sang et scènes gore, jump scares, lumières clignotantes (option « Moins de flashs » dans les paramètres).',
           'notes': 'Première version de test. Joue au moins jusqu’à une fin et signale-nous les plantages, les ralentissements ou tout ce qui semble anormal.'},
    'it': {'short': 'Horror nel turno di notte. Non rispondere. Non aprire. Aspetta le sei.',
           'bullets': ['Doppiato in inglese, russo, spagnolo, portoghese e tedesco; sottotitoli in 13 lingue',
                       'Tre stili grafici: Retro (low poly in stile PS1), Cinema e Ultra; il gioco sceglie cosa può reggere il tuo telefono',
                       'In verticale o in orizzontale, touch o controller. Funziona offline. Consigliate le cuffie',
                       'Niente pubblicità, niente account, nessuna raccolta di dati'],
           'warn': 'Avviso sui contenuti: 18+. Violenza, sangue e scene cruente, jump scare e luci lampeggianti (nelle impostazioni c’è l’opzione «Meno lampi»).',
           'notes': 'Prima versione di prova. Gioca almeno fino a un finale e segnalaci crash, rallentamenti o qualsiasi cosa che non vada.'},
    'pl': {'short': 'Horror na nocnej zmianie. Nie odpowiadaj. Nie otwieraj. Doczekaj do szóstej.',
           'bullets': ['Dubbing po angielsku, rosyjsku, hiszpańsku, portugalsku i niemiecku; napisy w 13 językach',
                       'Trzy style grafiki: Retro (low poly w stylu PS1), Kino i Ultra; gra sama wybiera, co udźwignie twój telefon',
                       'Pionowo lub poziomo, dotyk albo pad. Działa offline. Najlepiej w słuchawkach',
                       'Bez reklam, bez kont, bez zbierania danych'],
           'warn': 'Ostrzeżenie o treści: 18+. Przemoc, krew i drastyczne sceny, jump scare’y, migające światło (w ustawieniach jest opcja „Mniej błysków”).',
           'notes': 'Pierwsza wersja testowa. Przejdź grę przynajmniej do jednego zakończenia i daj znać o awariach, spowolnieniach albo o wszystkim, co wygląda źle.'},
    'tr': {'short': 'Gece vardiyasında korku. Cevap verme. Kapıyı açma. Altıyı bekle.',
           'bullets': ['İngilizce, Rusça, İspanyolca, Portekizce ve Almanca seslendirme; 13 dilde altyazı',
                       'Üç grafik tarzı: Retro (PS1 tarzı low-poly), Sinema ve Ultra; oyun, telefonunun kaldırabileceğini kendisi seçer',
                       'Dikey ya da yatay, dokunmatik ya da oyun kumandası. İnternetsiz çalışır. Kulaklık önerilir',
                       'Reklam yok, hesap yok, veri toplama yok'],
           'warn': 'İçerik uyarısı: 18+. Şiddet, kan ve vahşet, ani korkutmalar, yanıp sönen ışıklar (ayarlarda "Daha az flaş" seçeneği var).',
           'notes': 'İlk test sürümü. Lütfen en az bir sona kadar oyna; çökme, takılma ya da yanlış görünen her şeyi bize bildir.'},
    'ja': {'short': '夜勤ホラー。返事をするな。開けるな。6時まで待て。',
           'bullets': ['英語・ロシア語・スペイン語・ポルトガル語・ドイツ語のボイス、13言語の字幕',
                       '3種類のグラフィック：レトロ（PS1風ローポリ）、シネマ、ウルトラ。スマホの性能に合わせてゲームが自動で選択',
                       '縦画面・横画面、タッチ・ゲームパッドに対応。オフラインで遊べます。ヘッドホン推奨',
                       '広告なし、アカウント不要、データ収集なし'],
           'warn': '内容に関する警告：18歳以上向け。暴力、流血・残酷な描写、ジャンプスケア、点滅する光（設定に「点滅を減らす」があります）。',
           'notes': '最初のテスト版です。少なくとも1つのエンディングまでプレイして、クラッシュ、動作の重さ、おかしな表示があれば教えてください。'},
    'ko': {'short': '야간 근무 호러. 대답하지 마라. 열지 마라. 6시까지 기다려라.',
           'bullets': ['영어·러시아어·스페인어·포르투갈어·독일어 음성, 13개 언어 자막',
                       '그래픽 3종: 레트로(PS1 스타일 로우폴리), 시네마, 울트라 — 휴대폰 성능에 맞춰 게임이 자동으로 선택',
                       '세로·가로 화면, 터치·게임패드 지원. 오프라인 플레이 가능. 헤드폰 권장',
                       '광고 없음, 계정 없음, 데이터 수집 없음'],
           'warn': '콘텐츠 경고: 18+. 폭력, 유혈과 잔혹한 장면, 갑작스러운 점프 스케어, 깜박이는 빛(설정에서 ‘섬광 줄이기’를 켤 수 있음).',
           'notes': '첫 테스트 버전입니다. 엔딩을 하나 이상 플레이하고 강제 종료, 버벅임, 이상하게 보이는 부분이 있으면 알려 주세요.'},
    'zh': {'short': '夜班恐怖游戏。别答应。别开门。等到六点。',
           'bullets': ['英语、俄语、西班牙语、葡萄牙语、德语配音，13 种语言字幕',
                       '三种画面：复古（PS1 风格低多边形）、电影、超高；游戏会根据手机性能自动选择',
                       '竖屏或横屏，触屏或手柄。可离线游玩。建议佩戴耳机',
                       '无广告、无需账号、不收集任何数据'],
           'warn': '内容警告：18+。暴力、血腥与残酷画面，突然惊吓，闪烁灯光（设置中可开启“减少闪光”）。',
           'notes': '首个测试版本。请至少玩到一个结局，如遇闪退、卡顿或显示异常，请告诉我们。'},
}
# язык игры → язык страницы в Play Console (у испанского и китайского — две страницы с одним текстом)
LOCALES = {'en': ['en-US'], 'ru': ['ru-RU'], 'uk': ['uk'], 'es': ['es-ES', 'es-419'], 'pt': ['pt-BR'], 'de': ['de-DE'],
           'fr': ['fr-FR'], 'it': ['it-IT'], 'pl': ['pl-PL'], 'tr': ['tr-TR'], 'ja': ['ja-JP'], 'ko': ['ko-KR'], 'zh': ['zh-CN']}
NAMES = {'en': 'English (US)', 'ru': 'Русский', 'uk': 'Українська', 'es': 'Español', 'pt': 'Português (Brasil)', 'de': 'Deutsch',
         'fr': 'Français', 'it': 'Italiano', 'pl': 'Polski', 'tr': 'Türkçe', 'ja': '日本語', 'ko': '한국어', 'zh': '中文（简体）'}


def plain(s):
    return re.sub(r'\*+', '', s).strip()


def page(lang):
    if lang in ('en', 'ru', 'uk'):
        d = {'en': EN, 'ru': RU, 'uk': UK}[lang]
        story, bullets, warn = d['story'], d['bullets'], d['warn']
    else:
        st = json.load(open(os.path.join(ROOT, 'tools/i18n/%s.store.json' % lang), encoding='utf-8'))
        p = [plain(x) for x in st['about'].split('\n\n')]
        steam_b = [plain(x.lstrip('-• ').strip()) for x in p[4].split('\n') if x.strip()]
        story = p[:4]
        bullets = steam_b[:2] + MORE[lang]['bullets']
        warn = MORE[lang]['warn']
        d = MORE[lang]
    full = '\n\n'.join(story) + '\n\n' + '\n'.join('• ' + b for b in bullets) + '\n\n' + warn
    return {'title': TITLE, 'short': d['short'], 'full': full, 'notes': d['notes']}


def main():
    os.makedirs(OUT, exist_ok=True)
    md = ['# Hush Till Six — страница в Google Play на 13 языках', '',
          'Play Console → Grow users → Store presence → Main store listing. Английский — основной; остальные: Add translations → '
          'язык → вставить три поля. Название везде одно: **Hush Till Six**. Что нового — при создании выпуска (Release notes).', '']
    bad = []
    for lang in LOCALES:
        p = page(lang)
        if len(p['title']) > 30 or len(p['short']) > 80 or len(p['full']) > 4000 or len(p['notes']) > 500:
            bad.append((lang, len(p['short']), len(p['full'])))
        txt = 'App name:\n%s\n\nShort description (%d/80):\n%s\n\nFull description (%d/4000):\n%s\n\nRelease notes (%d/500):\n%s\n' % (
            p['title'], len(p['short']), p['short'], len(p['full']), p['full'], len(p['notes']), p['notes'])
        for loc in LOCALES[lang]:
            open(os.path.join(OUT, loc + '.txt'), 'w', encoding='utf-8').write(txt)
        md += ['## %s — %s' % (NAMES[lang], ', '.join(LOCALES[lang])), '', '**Краткое описание** (%d из 80):' % len(p['short']), '',
               '```', p['short'], '```', '', '**Полное описание** (%d из 4000):' % len(p['full']), '', '```', p['full'], '```', '',
               '**Что нового** (%d из 500):' % len(p['notes']), '', '```', p['notes'], '```', '']
    open(os.path.join(OUT, 'ALL.md'), 'w', encoding='utf-8').write('\n'.join(md))
    print('языков', len(LOCALES), 'страниц', sum(len(v) for v in LOCALES.values()), 'превышения' if bad else 'все в пределах', bad)


if __name__ == '__main__':
    main()
