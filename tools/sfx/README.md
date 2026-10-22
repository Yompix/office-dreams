# Звуки «Night Shift»

Все звуки игры (`public/games/nightshift/snd/<ключ>.mp3`, кроме реплик) генерируются в ElevenLabs Sound Effects
(`eleven_text_to_sound_v2`) **на платном тарифе владельца** — тогда права на них у владельца и их можно продавать.
Описания и длительности — `sfx.json` (ключ → [описание, секунды]).

Порядок для каждого звука: генерация (1 вариант, ~50 кредитов) → скачать mp3 → привести к формату игры:

    ffmpeg -y -i скачанный.mp3 -ac 1 -ar 44100 -b:a 96k public/games/nightshift/snd/<ключ>.mp3

Длинные фоновые петли (`drone`, `tension`, `room_tone`, `rain`) — стерео 128k, края петли сглажены:

    ffmpeg -y -i скачанный.mp3 -ar 44100 -b:a 128k -af "afade=t=in:d=0.3,areverse,afade=t=in:d=0.3,areverse" snd/<ключ>.mp3

Нельзя: звуки из интернета, бесплатный тариф ElevenLabs (там лицензия некоммерческая), edge-tts.
Каждый новый звук — в `sfx.json` и (если сторонний) в `public/games/nightshift/CREDITS.txt`.
