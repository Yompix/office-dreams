# Звуки: источники и лицензии

Настоящие записи — **BigSoundBank.com** (Joseph Sardin). Лицензия сайта: «как CC0» — можно использовать бесплатно, в том числе
в играх и видео, без обязательного указания авторства (автор просит по желанию: «Additional sounds: Joseph SARDIN - BigSoundBank.com»).
Страница лицензии: https://bigsoundbank.com/licence.html. Файлы: https://bigsoundbank.com/UPLOAD/ogg/<номер>.ogg

Все записи обработаны (обрезка, эквалайзер, смена тона, эхо, хорус, слои с нашими синтезированными звуками) — `tools/gen_real_sounds.py`.
Сырьё лежит в `tools/sound_src/` и в git не попадает.

| № | Название | Где используется |
|---|---|---|
| 0904 | Wind in the Trees | amb.forest, wind (трейлер) |
| 0555 | Forest at Night After Rain | amb.forest, room (трейлер) |
| 1764 | Tawny Owl #2 | amb.forest |
| 0155 | Whistling of the Wind #2 | wind_gust |
| 0208 | Footstep in the Snow #1 | steps_near, step (трейлер) |
| 3363 | Steps in the snow #3 | steps_far |
| 1301 | Steps on the Twigs | steps_far |
| 1299 | Broken twigs #1 | branch_snap, swish (трейлер) |
| 3211 | Creaking Door #8 | door_creak, creak (трейлер) |
| 0518 | Floating Floor Squeaks | pole_step |
| 0320 | Knock on a Glass Door #2 | knock, tap (трейлер) |
| 2195 | Man Breathing | breath |
| 1103 | Breathless Man | breath (трейлер) |
| 2107 | Zombie #2 | listener_scream, choir_roar, choir_hum |
| 0486 | Grunts | choir_roar |
| 2593 | Gore (pepper) #1 | choir_hurt (расправа) |
| 1409 | Bones cracking #3 | choir_hurt |
| 0596 | Bones cracking #1 | jumpscare, hit (трейлер) |
| 0303 | Grinding Metal Gate #1 | jumpscare, hit (трейлер) |
| 0312 | Crackling Radio #1 | radio_static |
| 0218 | Heart Beat | heartbeat |
| 1662 | Howling of a Girl #1 | distant_scream |

Остальные звуки (щелчки Слухача, гул ламп, генератор, музыка трейлеров, дроны) синтезированы кодом — `tools/gen_sounds.py`, `tools/make_trailer.py`, `tools/make_proof.py`.
0.4 «Порожняк»: переездный звонок, мотор камеры, реле табло, громкоговоритель, перемотка, мотовоз, стук на стыках — синтез,
`tools/gen_v2.py`. Трейлер и вертикальные ролики 0.4 (`tools/make_trailer3.py`) собраны только из этих звуков и записей BigSoundBank, без музыки.
Озвучки в 0.4 нет (реплики — субтитрами). Старые голоса XTTS-v2 (некоммерческая лицензия CPML) удалены из мода и в роликах не используются.
Splice — не использован (нет подписки, ничего не скачано).

Благодарность: Additional sounds: Joseph SARDIN - BigSoundBank.com
