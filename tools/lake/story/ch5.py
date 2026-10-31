#!/usr/bin/env python3
"""WHAT THE LAKE KEEPS — глава 5 «Озеро» и пять концовок в данные игры (godot/lake/data/ch5.json) по сценарию newgame/ch5.md.
Тексты RU+EN, выборы на время, действия (qte), флаги, карта главы. Постановка — godot/lake/scripts/chapter5.gd.
Концовки: «Свидетель» (полный и горький), «Тень» (и самый тёмный вариант), «Лёд», «Мама», «Забытьё».
  python3 tools/lake/story/ch5.py"""
import json, os

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '../../../godot/lake/data/ch5.json')


def N(ru, en, dur=2.6, **kw):
    return dict(note={'ru': ru, 'en': en}, dur=dur, **kw)


def S(who, ru, en, **kw):
    return dict(say=who, ru=ru, en=en, **kw)


def T(ru, en, dur=3.0, **kw):
    return dict(text={'ru': ru, 'en': en}, dur=dur, note=True, **kw)


def O(ru, en, go, **kw):
    return dict(ru=ru, en=en, go=go, **kw)


def C(t, opts, shot=None, silent=None):
    c = {'t': t, 'opts': opts + ([{'silent': True, 'go': silent}] if silent else [])}
    if shot:
        c['shot'] = shot
    return {'choice': c}


def I(**cond):
    return {'if': cond}


def SET(cond=None, **flags):           # set с условием: SET({'zoe_taken': '>0'}, hostage=1)
    b = {'set': flags}
    if cond:
        b['if'] = cond
    return b


ZOE = {'zoe': '>0', 'zoe_gone': '==0'}                                   # Зоя с ними
DANNY = {'danny': '>0', 'danny_lost': '==0', 'danny_hale': '==0'}       # Дэнни с ними
AGNES = {'agnes_dead': '==0'}
EVA = {'eva_alive': '>0'}
PULL = {'ru': 'Тяни! Жми E / пробел в ритм', 'en': 'Pull! Press E / Space in rhythm'}

SC = {}

# ---------- «Ранее» ----------
SC['c5_recap'] = {'save': True, 'time': '07:20', 'beats': [
    {'fx': 'black'},
    T('Ранее…', 'Previously…', 1.6),
    T('У поваленной сосны доктор Хейл впервые показал лицо.', 'At the fallen pine, Dr. Hale showed his face for the first time.', 2.8),
    T('Ной вспомнил фары на берегу: «Вы там были. Той ночью».', 'Noah remembered headlights on the shore: “You were there. That night.”', 3.0, **I(hale_knows_mem='>0')),
    T('Хейл забрал мамин дневник.', 'Hale took Mom’s diary.', 2.4, **I(diary_hale='>0')),
    T('«Второй он» убил санитара.', '“The other him” killed the orderly.', 2.4, **I(orderly_dead='>0')),
    T('Зоя — у Хейла.', 'Zoe is with Hale.', 2.2, **I(zoe_taken='>0')),
    T('Дэнни ушёл на голос Хейла.', 'Danny walked toward Hale’s voice.', 2.4, **I(danny_hale='>0')),
    T('Бабушка осталась в лесу.', 'Grandma stayed behind in the forest.', 2.4, **I(agnes_dead='>0')),
    T('Ной нёс бабушку на себе. Она подвернула ногу.', 'Noah carried Grandma on his back. She twisted her ankle.', 2.6, **I(agnes_limp='>0')),
    T('На рассвете — озеро. И две фары на другом берегу.', 'At dawn — the lake. And two headlights on the far shore.', 3.0),
    {'go': 'c5_s1'}]}

# ---------- Сцена 1. «Дача» ----------
SC['c5_s1'] = {'save': True, 'time': '08:10', 'beats': [
    SET({'zoe_taken': '>0'}, hostage=1),
    SET({'zoe_taken': '==0', 'danny_hale': '>0'}, hostage=1),
    SET({'pills': '>0'}, ptotal=1), SET({'pills2': '>0'}, ptotal=1), SET({'pills3': '>0'}, ptotal=1),
    {'fx': 'c5_start'},
    {'fx': 'fade_in', 'shot': 'dacha_front', **N('Глава 5. «Озеро». Дача по окна в снегу. Ставни заколочены.', 'Chapter 5. “The Lake.” The lake house is buried in snow up to the windows. The shutters are boarded up.', 3.6)},
    {'fx': 'key', 'shot': 'porch_step', **N('Ной, не думая, наклоняется к крыльцу и достаёт из-под третьей ступеньки ржавый ключ.', 'Without thinking, Noah bends down and pulls a rusty key from under the third step.', 3.4)},
    S('iris', 'Откуда ты знал?', 'How did you know?', shot='iris_porch'),
    S('noah', '…Не знаю.', '…I don’t know.', shot='noah_porch'),
    {'fx': 'boards', 'shot': 'dacha_window', **N('Ной отдирает доски с окна. В дом входит серый свет.', 'Noah pries the boards off a window. Gray light comes into the house.', 3.0)},
    {'fx': 'dacha_in', 'shot': 'dacha_room', **N('Внутри всё промёрзло. На вешалке у двери — мамин плащ: одиннадцать лет его никто не трогал.', 'Inside, everything is frozen. On the hook by the door — Mom’s raincoat. No one has touched it in eleven years.', 3.8)},
    N('На стене — детский рисунок фломастером: трое человечков и озеро.', 'On the wall — a child’s marker drawing: three stick figures and a lake.', 3.0, fx='wall_drawing', shot='wall_drawing'),
    {'fx': 'stove5', 'shot': 'stove5', **N('Бабушка растапливает печь старыми дровами с веранды.', 'Grandma lights the stove with old wood from the porch.', 3.0, **I(agnes_dead='==0'))},
    {'fx': 'stove5', 'shot': 'stove5', **N('Ной растапливает печь старыми дровами с веранды.', 'Noah lights the stove with old wood from the porch.', 3.0, **I(agnes_dead='>0'))},
    N('Ева греет малышку у печи.', 'Eva warms the baby by the stove.', 2.4, shot='dacha_room', **I(eva_alive='>0')),
    S('agnes', 'На лёд у мостков не ходите. Там ключи бьют — лёд и в лютый мороз не держит.', 'Stay off the ice by the dock. There’s springs under there — it won’t hold, not even in a hard freeze.', shot='agnes_window', fx='agnes_up', **I(agnes_dead='==0')),
    N('У мостков на льду — тёмное пятно.', 'By the dock, there’s a dark patch on the ice.', 2.4, shot='dark_ice', **I(agnes_dead='>0')),
    S('iris', 'Там лёд тёмный. Не ходи туда.', 'The ice is dark over there. Don’t go near it.', shot='iris_room', **I(agnes_dead='>0')),
    {'fx': 'coat_touch', 'shot': 'coat'},
    S('iris', 'Она мне тут косы заплетала. Я думала, это сон.', 'She used to braid my hair here. I thought I’d dreamed that.', shot='iris_coat'),
    {'go': 'c5_s1_hub'}]}

SC['c5_s1_hub'] = {'beats': [
    {'fx': 'hub_in'},
    N('Печь растапливается. Можно поговорить — с кем захочешь.', 'The stove is warming up. You can talk to whoever you want.', 2.6, shot='dacha_room', **I(hub_seen='==0')),
    SET(None, hub_seen=1),
    C(20, [O('Айрис', 'Iris', 'c5_t_iris', **I(t_iris='==0')),
           O('Зоя', 'Zoe', 'c5_t_zoe', **{'if': {'zoe': '>0', 'zoe_gone': '==0', 't_zoe': '==0'}}),
           O('Бабушка', 'Grandma', 'c5_t_agnes', **{'if': {'agnes_dead': '==0', 't_agnes': '==0'}}),
           O('Дэнни', 'Danny', 'c5_t_danny', **{'if': {'danny': '>0', 'danny_lost': '==0', 'danny_hale': '==0', 't_danny': '==0'}}),
           O('Ева', 'Eva', 'c5_t_eva', **{'if': {'eva_alive': '>0', 't_eva': '==0'}}),
           O('Пора к сараю', 'Time to go to the boathouse', 'c5_s1_pills')],
      shot='noah_room', silent='c5_s1_pills')]}

SC['c5_t_iris'] = {'beats': [
    {'set': {'t_iris': 1, 'trust': 1}, 'toast': True},
    {'fx': 'iris_frost', 'shot': 'iris_window', **N('Айрис рисует пальцем на заиндевевшем стекле мостки.', 'Iris draws the dock with her finger on the frosted glass.', 2.8)},
    S('noah', 'Ты помнишь, как тут было?', 'Do you remember what it was like here?', shot='noah_room'),
    S('iris', 'Мне было четыре. Помню мамины руки. И что всё пахло озером.', 'I was four. I remember Mom’s hands. And everything smelled like the lake.', shot='iris_window'),
    {'go': 'c5_s1_hub'}]}

SC['c5_t_zoe'] = {'beats': [
    {'set': {'t_zoe': 1, 'zoe_hook': 1}},
    {'fx': 'zoe_porch', 'shot': 'zoe_porch', **N('Зоя курит на крыльце и вертит в руках багор из сарая.', 'Zoe smokes on the porch, turning a boat hook from the boathouse over in her hands.', 3.0)},
    S('zoe', 'Это что, гарпун? Красиво тут. Жутко, но красиво.', 'What is this, a harpoon? It’s pretty here. Creepy, but pretty.', shot='zoe_porch'),
    S('noah', 'Я тут последний раз был в восемь.', 'Last time I was here, I was eight.', shot='noah_porch'),
    S('zoe', 'А мне в восемь подарили хомяка. Он сдох. Прости. Когда нервничаю, несу чушь.', 'When I was eight, I got a hamster. It died. Sorry. I talk crap when I’m nervous.', shot='zoe_porch'),
    {'if': {'romance': '>0'}, 'else': 'c5_s1_hub'},
    {'set': {'romance': 1}},
    {'fx': 'zoe_hand', 'shot': 'zoe_porch', **N('Она берёт его за руку.', 'She takes his hand.', 2.0)},
    S('zoe', 'Ты только возвращайся, ладно? Ну, оттуда, куда ты уходишь.', 'Just come back, okay? From wherever it is you go.', shot='zoe_porch'),
    {'go': 'c5_s1_hub'}]}

SC['c5_t_agnes'] = {'beats': [
    {'set': {'t_agnes': 1}},
    S('agnes', 'Счастливая она тут была. Летом-то. Пока этот не нагрянет.', 'She was happy here. Summers. Till that man came rolling in.', shot='agnes_stove5'),
    S('noah', 'Ты знала? Что это он её?', 'You knew? That it was him?', shot='noah_room'),
    N('Бабушка долго молчит.', 'Grandma is silent for a long time.', 2.2, shot='agnes_stove5'),
    S('agnes', 'Знала. И молчала. Одиннадцать лет молчала, милок.', 'I knew. And I kept my mouth shut. Eleven years, sonny.', shot='agnes_stove5'),
    {'go': 'c5_s1_hub'}]}

SC['c5_t_danny'] = {'beats': [
    {'set': {'t_danny': 1, 'danny_rope': 1}},
    {'fx': 'danny_rope', 'shot': 'danny_room', **N('Дэнни не выпускает из рук моток верёвки.', 'Danny won’t let go of a coil of rope.', 2.6)},
    S('danny', 'В сарае нашёл. Вдруг пригодится. Ты нас не бросишь?', 'Found it in the boathouse. Just in case. You won’t leave us, right?', shot='danny_room'),
    C(8, [O('«Не брошу»', '“I won’t”', 'c5_t_danny_yes'),
          O('«Не знаю. Но я постараюсь»', '“I don’t know. But I’ll try”', 'c5_t_danny_try')], shot='noah_room', silent='c5_s1_hub')]}

SC['c5_t_danny_yes'] = {'beats': [
    {'set': {'promise5': 1}},
    S('noah', 'Не брошу.', 'I won’t.', shot='noah_room'),
    N('Дэнни кивает. Он это запомнит.', 'Danny nods. He’ll remember that.', 2.2, shot='danny_room'),
    {'go': 'c5_s1_hub'}]}

SC['c5_t_danny_try'] = {'beats': [
    S('noah', 'Не знаю. Но я постараюсь.', 'I don’t know. But I’ll try.', shot='noah_room'),
    N('Дэнни долго смотрит на него. Честно — это тоже ответ.', 'Danny looks at him for a long time. Honest is an answer too.', 2.6, shot='danny_room'),
    {'go': 'c5_s1_hub'}]}

SC['c5_t_eva'] = {'beats': [
    {'set': {'t_eva': 1}},
    S('eva', 'Прости, я тебе даже спасибо не сказала. Ты нас… Ну, ты понял.', 'Sorry, I never even said thank you. You… well, you know.', shot='eva_room'),
    S('noah', 'Это бабушка. И Айрис.', 'That was Grandma. And Iris.', shot='noah_room'),
    S('eva', 'И ты. Не спорь.', 'And you. Don’t argue.', shot='eva_room'),
    {'go': 'c5_s1_hub'}]}

# таблетки — последний раз
SC['c5_s1_pills'] = {'beats': [
    {'fx': 'head_ring', 'shot': 'noah_room', **N('Ной встаёт, чтобы идти к сараю, — и голову будто сжимает обручем. В ушах звенит.', 'Noah gets up to go to the boathouse — and it’s like a band tightens around his head. His ears are ringing.', 3.4)},
    {'if': {'pills_pocket': '>0'}, 'else': 'c5_s1_pills2'},
    {'fx': 'bottle5', 'shot': 'bottle5'},
    C(8, [O('Принять', 'Take one', 'c5_s1_take'),
          O('Бросить в печь', 'Throw them in the stove', 'c5_s1_burn')], shot='noah_room', silent='c5_s1_keep')]}

SC['c5_s1_pills2'] = {'beats': [
    {'if': {'pills_iris': '>0'}, 'else': 'c5_s1_go'},
    N('Пузырёк — у Айрис. Она смотрит на брата.', 'The bottle is with Iris. She watches her brother.', 2.6, shot='iris_room'),
    C(8, [O('Попросить таблетку', 'Ask her for a pill', 'c5_s1_ask'),
          O('Не просить', 'Don’t ask', 'c5_s1_keep')], shot='noah_room', silent='c5_s1_keep')]}

SC['c5_s1_ask'] = {'beats': [
    S('noah', 'Айрис… дай мне одну.', 'Iris… give me one.', shot='noah_room'),
    {'if': {'trust': '>=3'}, 'else': 'c5_s1_iris_gives'},
    {'set': {'iris_refused': 1}},
    {'fx': 'iris_burns', 'shot': 'stove5', **N('Айрис бросает пузырёк в печь.', 'Iris throws the bottle into the stove.', 2.4)},
    S('iris', 'Нет. Не сегодня. Пожалуйста, вспомни.', 'No. Not today. Please — remember.', shot='iris_room'),
    {'go': 'c5_s1_go'}]}

SC['c5_s1_iris_gives'] = {'beats': [
    N('Айрис молча отдаёт пузырёк и отворачивается.', 'Iris silently hands him the bottle and turns away.', 2.6, shot='iris_room'),
    {'go': 'c5_s1_take'}]}

SC['c5_s1_take'] = {'beats': [
    {'set': {'pills5': 1, 'ptotal': 1}},
    {'fx': 'take5', 'shot': 'noah_room', **N('Ной глотает таблетку. Звон стихает — а вместе с ним и всё остальное.', 'Noah swallows a pill. The ringing fades — and everything else fades with it.', 3.2)},
    {'go': 'c5_s1_go'}]}

SC['c5_s1_burn'] = {'beats': [
    {'set': {'pills_burned5': 1, 'trust': 1}, 'toast': True},
    {'fx': 'burn5', 'shot': 'stove5', **N('Пузырёк летит в печь. Пластик съёживается в огне.', 'The bottle goes into the stove. The plastic shrivels in the fire.', 2.8)},
    {'go': 'c5_s1_go'}]}

SC['c5_s1_keep'] = {'beats': [
    N('Ной ничего не принимает. Звон остаётся.', 'Noah takes nothing. The ringing stays.', 2.4, shot='noah_room'),
    {'go': 'c5_s1_go'}]}

SC['c5_s1_go'] = {'beats': [
    N('Уходя к сараю, Айрис отдаёт малышку тем, кто остаётся в доме.', 'On her way out to the boathouse, Iris hands the baby to whoever stays in the house.', 2.8, shot='dacha_room', **{'if': {'baby': '>0'}}),
    {'if': {'pills5': '>0'}, 'else': 'c5_s2'},
    {'fx': 'gap_ice', 'shot': 'gap_view', **N('Ной заходит в сарай и смотрит в щель. Там просто лёд.', 'Noah goes into the boathouse and looks through the gap. There’s only ice.', 3.0)},
    S('noah', 'Прости. Там ничего нет.', 'I’m sorry. There’s nothing there.', shot='noah_shed'),
    N('Айрис плачет беззвучно.', 'Iris cries without a sound.', 2.4, shot='iris_shed'),
    {'go': 'c5_s3'}]}

# ---------- Сцена 2. «Сарай» (воспоминание) ----------
SC['c5_s2'] = {'save': True, 'beats': [
    {'fx': 'kneel_gap', 'shot': 'noah_gap', **N('В сарае пахнет смолой и старой лодкой. Ной встаёт на колени у щели.', 'The boathouse smells of tar and old boat. Noah kneels by the gap.', 3.0)},
    {'fx': 'summer_night'},
    {'fx': 'fade_in', 'shot': 'memory_dock', **N('А там — уже лето. Тёплая ночь, лягушки. На мостках — фонарь, у ног — сумки.', 'And there — it’s summer. A warm night, frogs. A lantern on the dock, bags at her feet.', 3.6)},
    {'fx': 'mom_hum', 'shot': 'mom', **N('Мама сидит рядом и тихо напевает ту самую колыбельную.', 'Mom sits beside him, softly humming that same lullaby.', 3.0)},
    S('mom', 'Скоро приедет дядя Саймон, и поедем к бабушке. Только тихо, Айрис спит.', 'Uncle Simon will be here soon, and then we’re going to Grandma’s. Shh, Iris is sleeping.', shot='mom'),
    {'fx': 'road_lights', 'shot': 'memory_road', **N('На дороге — фары. Мама улыбается — и улыбка гаснет: это не та машина.', 'Headlights on the road. Mom smiles — and the smile dies: it’s the wrong car.', 3.2)},
    S('mom', 'Ной, в сарай. Быстро. Сиди тихо, как мышка, что бы ни было.', 'Noah, into the boathouse. Quick. Quiet as a mouse, no matter what.', shot='mom'),
    {'fx': 'hum_stop'},
    {'fx': 'victor_walk', 'shot': 'memory_dock', **N('По мосткам идёт отец. Пьяный, но шагает ровно — как человек, который уже всё решил.', 'Dad walks down the dock. Drunk, but his steps are steady — like a man who has already decided.', 3.4)},
    {'fx': 'kick_bag', **S('victor', 'Куда собралась? Саймон всё рассказал. Сам позвонил. Друг всё-таки.', 'Going somewhere? Simon told me everything. Called me himself. Good friend, Simon.', shot='victor')},
    S('mom', '…Он же обещал.', '…He promised.', shot='mom'),
    {'fx': 'grab_hair', 'shot': 'memory_dock', **N('Отец хватает её за волосы и тащит к краю мостков.', 'Dad grabs her by the hair and drags her to the edge of the dock.', 2.8)},
    {'if': {'memory': '>=3'}, 'else': 'c5_s2_look6'},
    C(10, [O('Смотреть на маму', 'Look at Mom', 'c5_s2_mom'),
           O('Смотреть на отца', 'Look at Dad', 'c5_s2_dad')], shot='gap_close', silent='c5_s2_shut')]}

SC['c5_s2_look6'] = {'beats': [
    C(6, [O('Смотреть на маму', 'Look at Mom', 'c5_s2_mom'),
          O('Смотреть на отца', 'Look at Dad', 'c5_s2_dad')], shot='gap_close', silent='c5_s2_shut')]}

SC['c5_s2_mom'] = {'beats': [
    {'set': {'look_mom': 1}},
    {'fx': 'mom_turns', 'shot': 'mom_face', **N('Она поворачивает голову к сараю. В свете фонаря видно губы: «Не смотри, малыш».', 'She turns her head toward the boathouse. In the lantern light he can read her lips: “Don’t look, baby.”', 3.8)},
    {'fx': 'drown', 'shot': 'memory_dock', **N('Отец тащит её в воду.', 'Dad drags her into the water.', 2.4)},
    {'go': 'c5_s2_after'}]}

SC['c5_s2_dad'] = {'beats': [
    {'set': {'look_dad': 1}},
    {'fx': 'drown', 'shot': 'victor_face', **N('Лицо отца в свете фонаря спокойное. Он держит её под водой и смотрит на дом. Долго.', 'In the lantern light, Dad’s face is calm. He holds her under the water and looks at the house. For a long time.', 4.0)},
    {'go': 'c5_s2_after'}]}

SC['c5_s2_shut'] = {'beats': [
    {'set': {'eyes_shut': 1}},
    {'fx': 'eyes_shut', **N('Мальчик зажмуривается. Только звуки: всплеск, плеск… тишина.', 'The boy squeezes his eyes shut. Only sounds: a splash, splashing… silence.', 3.6)},
    N('Потом снова лягушки.', 'Then the frogs again.', 2.2),
    {'go': 'c5_s2_after'}]}

SC['c5_s2_after'] = {'beats': [
    {'fx': 'after_drown', 'shot': 'memory_dock', **N('Вода успокаивается. Фонарь качается. Отец сидит на досках, мокрый по пояс, и не двигается.', 'The water goes still. The lantern sways. Dad sits on the boards, soaked to the waist, not moving.', 3.6)},
    {'fx': 'young_hale', 'shot': 'memory_road', **N('Много позже на берегу загораются другие фары. Хейл — моложе, тот же шарф.', 'Much later, other headlights come on by the shore. Hale — younger, the same scarf.', 3.4)},
    S('victor', 'Она сама. Слышишь? Сама. Поскользнулась.', 'She fell. You hear me? She slipped. All on her own.', shot='victor'),
    N('Хейл долго смотрит на воду.', 'Hale stares at the water for a long time.', 2.4, shot='young_hale'),
    S('hale', 'Иди в дом, Вить. Переоденься. Я всё сделаю.', 'Go inside, Vic. Get changed. I’ll take care of it.', shot='young_hale'),
    {'fx': 'shed_door', 'shot': 'hale_door', **N('Хейл открывает дверь сарая и видит мальчика. Глаза у мальчика пустые, как дырки на рисунках Айрис.', 'Hale opens the boathouse door and sees the boy. The boy’s eyes are empty, like the holes in Iris’s drawings.', 3.8)},
    {'fx': 'hale_crouch', **S('hale', 'Ной… Ты спал. Слышишь меня? Ты спал. Тебе это приснилось.', 'Noah… You were asleep. Do you hear me? You were asleep. You dreamed it.', shot='young_hale_low')},
    {'fx': 'first_pill', **S('hale', 'Выпей. К утру всё пройдёт.', 'Here, take this. It’ll all be gone by morning.', shot='young_hale_low')},
    {'fx': 'fade_out', 'dur': 1.2, **N('Первая таблетка. Сарай темнеет.', 'The first pill. The boathouse goes dark.', 2.6)},
    {'fx': 'winter_back'},
    {'fx': 'fade_in', 'shot': 'noah_gap', **N('Снова зима. Ной стоит на коленях у щели. Сзади — Айрис.', 'Winter again. Noah is on his knees by the gap. Iris is behind him.', 3.0)},
    S('iris', 'Ты вспомнил?', 'You remember?', shot='iris_shed'),
    S('noah', 'Вспомнил.', 'I remember.', shot='noah_shed'),
    {'set': {'remembered': 1, 'memory': 1}},
    {'fx': 'iris_lean', 'shot': 'shed_two', **N('Айрис утыкается лбом ему в плечо — будто наконец донесла то, что несла одна всю дорогу.', 'Iris presses her forehead to his shoulder — as if she’d finally delivered what she’d carried alone the whole way.', 3.8)},
    {'if': {'agnes_dead': '==0', 'belief_healer': '>0'}, 'else': 'c5_s3'},
    {'fx': 'agnes_door5', 'shot': 'agnes_shed5'},
    S('agnes', 'Тогда она с тобой.', 'Then she’s with you.', shot='agnes_shed5'),
    {'go': 'c5_s3'}]}

# ---------- Сцена 3. «Хейл» ----------
SC['c5_s3'] = {'save': True, 'time': '09:20', 'beats': [
    SET({'agnes_dead': '==0', 'remembered': '>0', 'belief_healer': '>0'}, agnes_sings=1),
    SET({'agnes_dead': '==0', 'remembered': '>0', 'belief_healer': '==0', 'belief_doctor': '==0', 'look_mom': '>0'}, agnes_sings=1),
    {'fx': 'sedan_arrives', 'shot': 'sedan_shore', **N('С дороги спускается чёрный седан и встаёт у сарая. Мотор работает.', 'The black sedan comes down from the road and stops by the boathouse. The engine keeps running.', 3.2)},
    N('Первым выходит санитар и открывает заднюю дверь.', 'The orderly gets out first and opens the back door.', 2.6, shot='sedan_shore', **I(orderly_dead='==0')),
    N('Зоя — сонная, в чужом пуховике. Санитар держит её под локоть.', 'Zoe — drowsy, in a stranger’s down jacket. The orderly holds her by the elbow.', 3.0, shot='sedan_shore', **{'if': {'zoe_taken': '>0', 'orderly_dead': '==0'}}),
    N('Дэнни молча держится за его рукав.', 'Danny silently holds on to his sleeve.', 2.4, shot='sedan_shore', **{'if': {'danny_hale': '>0', 'orderly_dead': '==0'}}),
    N('Хейл за рулём сам. На заднем сиденье — заложники. Двери на детском замке.', 'Hale is driving himself. In the back seat — the hostages. Child locks on the doors.', 3.0, shot='sedan_shore', **{'if': {'orderly_dead': '>0', 'hostage': '>0'}}),
    {'fx': 'noah_ice', 'shot': 'ice_wide', **N('Ной выходит на мостки и спускается на лёд — подальше от дома, где младенец.', 'Noah walks out onto the dock and down onto the ice — away from the house with the baby.', 3.2)},
    {'fx': 'hale_ice', 'shot': 'ice_wide', **N('Хейл идёт к мосткам той же тропой, что одиннадцать лет назад. И выходит за ним на лёд.', 'Hale walks to the dock by the same path as eleven years ago. And follows him onto the ice.', 3.4)},
    N('Они останавливаются у края тёмного пятна.', 'They stop at the edge of the dark patch.', 2.4, shot='ice_two'),
    S('hale', 'Ты был в сарае. Что бы ты там ни увидел, Ной, — это сон. Мы это сто раз проходили.', 'You were in the boathouse. Whatever you saw in there, Noah, it was a dream. We’ve been through this a hundred times.', shot='hale_ice', **I(remembered='>0')),
    S('hale', 'Как голова? Выпил таблетку? Умница. Видишь — сразу легче.', 'How’s the head? Took your pill? Good boy. See? Better already.', shot='hale_ice', **I(pills5='>0')),
    {'if': {'diary_hale': '>0'}, 'else': 'c5_s3_iris'},
    {'fx': 'tear_pages', 'shot': 'hale_ice', **N('Хейл достаёт мамин дневник и вырывает несколько страниц — те, где про него.', 'Hale takes out Mom’s diary and tears out a few pages — the ones about him.', 3.2)},
    {'fx': 'lighter', **S('hale', 'Вот она, ваша правда. Бумага.', 'Here’s your truth. Paper.', shot='hale_ice')},
    {'set': {'diary_burned': 1}},
    {'fx': 'burn_pages', 'shot': 'pages_fire', **N('Айрис кричит. Страницы горят до конца; пепел уносит ветер.', 'Iris screams. The pages burn to nothing; the wind carries off the ash.', 3.0)},
    {'fx': 'diary_throw', **S('hale', 'Забирай. Там про погоду и рецепты.', 'Go on, take it. It’s all weather and recipes.', shot='hale_ice')},
    {'go': 'c5_s3_iris'}]}

SC['c5_s3_iris'] = {'beats': [
    {'fx': 'iris_ice', 'shot': 'ice_wide', **N('Айрис выходит по мосткам на лёд — следом за братом.', 'Iris walks down the dock onto the ice — after her brother.', 2.8)},
    S('iris', 'Ты мне тогда сказал: не ходи к озеру одна. А я не одна.', 'That night you told me not to go to the lake alone. Well, I’m not alone.', shot='iris_ice', **I(night_lapse='>0')),
    S('hale', 'Айрис, детка, отойди от него. Ты же видела, на что он способен.', 'Iris, sweetheart, step away from him. You’ve seen what he can do.', shot='hale_ice'),
    S('iris', 'Видела. А про тебя — читала. В мамином дневнике.', 'I have. And I’ve read about you. In Mom’s diary.', shot='iris_ice'),
    S('hale', 'Посмотри на берег, Ной. Зоя приняла много лекарства. На морозе это опасно, ей нужен врач. Поедешь со мной — и все разъедутся по домам.', 'Look at the shore, Noah. Zoe’s had a lot of medication. In this cold, that’s dangerous — she needs a doctor. Come with me, and everyone goes home.', shot='hale_ice', **I(zoe_taken='>0')),
    S('hale', 'Посмотри на берег. Дэнни совсем замёрз. Поедешь со мной — отвезу его к маме.', 'Look at the shore. Danny’s frozen stiff. Come with me, and I’ll take him to his mom.', shot='hale_ice', **{'if': {'zoe_taken': '==0', 'danny_hale': '>0'}}),
    {'fx': 'agnes_sings', 'shot': 'agnes_shore', **N('С берега бабушка начинает петь колыбельную — сначала тихо, потом громче. Хейл морщится.', 'From the shore, Grandma starts singing the lullaby — softly at first, then louder. Hale winces.', 3.6, **I(agnes_sings='>0'))},
    C(10, [O('«Отпусти их. Тогда поеду»', '“Let them go. Then I’ll come”', 'c5_oblivion', **{'if': {'hostage': '>0', 'remembered': '>0'}}),
           O('«Отпустите их. Тогда поеду»', '“Let them go. Then I’ll come”', 'c5_oblivion', **{'if': {'hostage': '>0', 'remembered': '==0'}}),
           O('«Ладно. Поехали»', '“Fine. Let’s go”', 'c5_oblivion', **I(hostage='==0')),
           O('«Я всё помню»', '“I remember everything”', 'c5_s3_remember', **I(remembered='>0')),
           O('«Я не поеду»', '“I’m not going”', 'c5_s3_wont', **I(remembered='==0')),
           O('[улыбнуться]', '[smile]', 'c5_s3_smile', **{'if': {'shadow': '>=2', 'pills5': '==0'}, 'shadow': True}),
           O('[слушать]', '[listen]', 'c5_s3_listen', **I(agnes_sings='>0'))],
      shot='noah_ice', silent='c5_s3_silent')]}

SC['c5_s3_silent'] = {'beats': [
    {'set': {'silent_ice': 1}},
    {'fx': 'hale_elbow5', 'shot': 'ice_two', **N('Ной молчит. Хейл подходит и берёт его под локоть.', 'Noah says nothing. Hale walks over and takes his elbow.', 2.8)},
    S('hale', 'Вот и умница.', 'There’s a good boy.', shot='hale_ice'),
    {'go': 'c5_oblivion'}]}

SC['c5_s3_remember'] = {'beats': [
    {'set': {'said_remember': 1}},
    S('noah', 'Мама ждала тебя той ночью. С сумками. Ты обещал её увезти. А приехал он.', 'Mom was waiting for you that night. Bags packed. You promised to get her out. And he showed up instead.', shot='noah_ice'),
    S('noah', 'Она до конца смотрела на сарай. Чтобы я не вышел.', 'She kept looking at the boathouse, right to the end. So I wouldn’t come out.', shot='noah_ice', **I(look_mom='>0')),
    S('noah', 'Он держал её под водой. Долго. А ты написал: «несчастный случай».', 'He held her under. For a long time. And you wrote “accidental drowning.”', shot='noah_ice', **I(look_dad='>0')),
    S('noah', 'Я не смотрел. Но я всё слышал.', 'I didn’t look. But I heard everything.', shot='noah_ice', **I(eyes_shut='>0')),
    S('noah', 'А потом ты открыл сарай и сказал, что мне это приснилось.', 'And then you opened the boathouse and told me I’d dreamed it.', shot='noah_ice'),
    {'fx': 'hale_breaks', 'shot': 'hale_ice', **N('У Хейла впервые срывается голос.', 'For the first time, Hale’s voice breaks.', 2.2)},
    S('hale', 'Я… я только сказал ему. Я испугался, Ной. Он бы меня… Я не думал, что он её…', 'I… I only told him. I was scared, Noah. He would have… I never thought he’d…', shot='hale_ice'),
    S('hale', 'Тебе было восемь. Ты кричал по ночам. Я тебе помог.', 'You were eight. You screamed every night. I helped you.', shot='hale_ice'),
    S('noah', 'Ты помог себе.', 'You helped yourself.', shot='noah_ice'),
    {'fx': 'hale_grabs', 'shot': 'ice_two', **N('Хейл берёт Айрис за руку — крепко, как врач берёт буйного пациента.', 'Hale takes Iris by the arm — firmly, the way a doctor takes hold of a violent patient.', 3.2)},
    S('hale', 'Айрис поедет со мной. Ей тоже нужна помощь.', 'Iris is coming with me. She needs help too.', shot='hale_ice'),
    {'go': 'c5_s3_onice'}]}

SC['c5_s3_wont'] = {'beats': [
    {'set': {'said_wont': 1}},
    S('noah', 'Я не знаю, что тогда было. Но Айрис я вам не отдам.', 'I don’t know what happened back then. But I’m not giving you Iris.', shot='noah_ice'),
    {'fx': 'hale_grabs', 'shot': 'ice_two', **N('Хейл вздыхает и берёт Айрис за руку.', 'Hale sighs and takes Iris by the arm.', 2.6)},
    S('hale', 'Жаль. Тогда Айрис поедет со мной. Ей тоже нужна помощь.', 'That’s a shame. Then Iris is coming with me. She needs help too.', shot='hale_ice'),
    {'go': 'c5_s3_onice'}]}

SC['c5_s3_onice'] = {'beats': [
    {'fx': 'iris_back', 'shot': 'ice_two', **N('Айрис вырывается и отступает — на тёмный лёд. Хейл шагает за ней.', 'Iris tears free and backs away — onto the dark ice. Hale steps after her.', 3.0)},
    {'go': 'c5_s4'}]}

SC['c5_s3_smile'] = {'beats': [
    {'set': {'smiled': 1}},
    {'fx': 'smile', 'shot': 'noah_smile', **N('Звук уходит. Ной улыбается — и это не его улыбка.', 'The sound drains away. Noah smiles — and it isn’t his smile.', 3.0)},
    {'fx': 'hale_backs', 'shot': 'ice_two', **N('Он идёт к Хейлу. Хейл пятится.', 'He walks toward Hale. Hale backs away.', 2.4)},
    S('hale', 'Ной. Ной, стой. Посмотри на меня. Ты же меня знаешь.', 'Noah. Noah, stop. Look at me. You know me.', shot='hale_ice'),
    {'fx': 'iris_sleeve', **S('iris', 'Ной, не надо!', 'Noah, don’t!', shot='iris_ice')},
    {'fx': 'iris_falls', 'shot': 'ice_two', **N('«Второй он» стряхивает её, как ветку. Айрис падает на тёмный лёд; Хейл отступает туда же.', '“The other him” shakes her off like a twig. Iris falls onto the dark ice; Hale backs onto it too.', 3.4)},
    S('agnes', 'Не Аня это! Не она!', 'That’s not Anna! That’s not her!', shot='agnes_shore', fx='agnes_out', **I(agnes_dead='==0')),
    {'go': 'c5_s4_shadow'}]}

SC['c5_s3_listen'] = {'beats': [
    {'set': {'listened': 1}},
    {'fx': 'eyes_close', 'shot': 'noah_ice', **N('Ной закрывает глаза и говорит — тихо, чужим женским голосом.', 'Noah closes his eyes and speaks — softly, in a woman’s voice that isn’t his.', 3.0)},
    S('anna', 'Здравствуй, Саймон. Ты же обещал.', 'Hello, Simon. You promised.', shot='noah_ice', lips='noah'),
    {'fx': 'hale_pales', 'shot': 'hale_ice', **N('Хейл белеет и пятится — прямо на тёмный лёд.', 'Hale goes white and backs away — straight onto the dark ice.', 2.8)},
    {'go': 'c5_mama'}]}

# ---------- Сцена 4. «Трещина» ----------
SC['c5_s4'] = {'save': True, 'beats': [
    SET({'ptotal': '>=3'}, weak=1), SET({'ptotal': '<3', 'pills5': '>0'}, weak=1),
    SET({'zoe_hook': '>0'}, help=1), SET({'zoe_hook': '==0', 'danny_rope': '>0'}, help=1),
    {'fx': 'crack', 'shot': 'ice_two', **N('Тёмный лёд у мостков проваливается — под Айрис и под Хейлом.', 'The dark ice by the dock gives way — under Iris and under Hale.', 2.8)},
    {'fx': 'in_water', 'shot': 'hole', **N('Оба в чёрной воде. Хейл хватается за Айрис: утопающий хватается за всё, что рядом.', 'Both are in the black water. Hale grabs Iris: a drowning man grabs whatever is near.', 3.2)},
    S('agnes', 'Ложись! На брюхо ложись, милок!', 'Get down! Flat on your belly, sonny!', shot='agnes_shore', fx='agnes_out', **I(agnes_dead='==0')),
    {'fx': 'crawl', 'shot': 'crawl', **N('Ной падает на лёд и ползёт к краю.', 'Noah drops onto the ice and crawls to the edge.', 2.6)},
    N('На вытянутую руку — запястье Айрис и воротник Хейла. Схватить можно только одного.', 'Within arm’s reach — Iris’s wrist and Hale’s collar. He can only grab one.', 3.4, shot='hole_hands'),
    C(6, [O('Айрис', 'Iris', 'c5_s4_iris'),
          O('Хейла', 'Hale', 'c5_s4_hale'),
          O('Нырнуть', 'Dive in', 'c5_s4_dive')], shot='noah_crawl', silent='c5_s4_iris')]}

SC['c5_s4_iris'] = {'beats': [
    {'fx': 'grab_iris', 'shot': 'hole_hands', **N('Ной хватает её за запястье. Хейл держит её за куртку и тянет вниз.', 'Noah grabs her wrist. Hale has her by the jacket and is dragging her down.', 3.0)},
    S('noah', 'Куртку! Снимай куртку!', 'Your jacket! Get it off!', shot='noah_crawl'),
    {'qte': 'press', 't': 7.0, 'n': 12, 'hint': PULL, 'fail': 'c5_s4_iris_fail', 'if': {'weak': '==0', 'help': '>0'}},
    {'qte': 'press', 't': 7.0, 'n': 15, 'hint': PULL, 'fail': 'c5_s4_iris_fail', 'if': {'weak': '==0', 'help': '==0'}},
    {'qte': 'press', 't': 7.0, 'n': 17, 'hint': PULL, 'fail': 'c5_s4_iris_fail', 'if': {'weak': '>0', 'help': '>0'}},
    {'qte': 'press', 't': 7.0, 'n': 20, 'hint': PULL, 'fail': 'c5_s4_iris_fail', 'if': {'weak': '>0', 'help': '==0'}},
    {'set': {'iris_saved': 1}},
    {'fx': 'iris_out', 'shot': 'hole', **N('Айрис выскальзывает из куртки — Ной вытаскивает её на лёд. У Хейла в руках пустая куртка; он цепляется за кромку.', 'Iris slips out of the jacket — Noah hauls her onto the ice. Hale is left holding an empty jacket; he clings to the edge.', 3.8)},
    C(5, [O('Протянуть руку Хейлу', 'Reach out to Hale', 'c5_s4_reach'),
          O('Не двигаться', 'Don’t move', 'c5_s4_still')], shot='noah_crawl', silent='c5_s4_still')]}

SC['c5_s4_reach'] = {'beats': [
    {'fx': 'reach_hale', 'shot': 'hole_hands', **N('Ной протягивает руку.', 'Noah reaches out.', 2.0)},
    N('Зоя подаёт багор.', 'Zoe holds out the boat hook.', 2.0, shot='hole', **I(zoe_hook='>0')),
    N('Дэнни бросает верёвку.', 'Danny throws the rope.', 2.0, shot='hole', **I(danny_rope='>0')),
    {'qte': 'mash', 't': 6.0, 'n': 9, 'hint': {'ru': 'Вытаскивай! Быстро жми E / пробел', 'en': 'Haul him out! Mash E / Space'}, 'fail': 'c5_s4_hale_gone', 'if': {'help': '>0'}},
    {'qte': 'mash', 't': 6.0, 'n': 13, 'hint': {'ru': 'Вытаскивай! Быстро жми E / пробел', 'en': 'Haul him out! Mash E / Space'}, 'fail': 'c5_s4_hale_gone', 'if': {'help': '==0'}},
    {'set': {'hale_saved': 1}},
    {'fx': 'hale_out', 'shot': 'hole', **N('Вместе они вытаскивают Хейла на лёд. Синий от холода, он хватает ртом воздух.', 'Together they drag Hale onto the ice. Blue with cold, he gasps for air.', 3.2)},
    {'set': {'end_witness': 1}},
    {'go': 'c5_shore'}]}

SC['c5_s4_still'] = {'beats': [
    {'fx': 'hale_slips', 'shot': 'hole', **N('Ной не двигается. Пальцы Хейла соскальзывают с кромки. Озеро забирает его.', 'Noah doesn’t move. Hale’s fingers slide off the edge. The lake takes him.', 3.2)},
    {'set': {'hale_drowned': 1, 'end_witness': 1}},
    {'go': 'c5_shore'}]}

SC['c5_s4_hale_gone'] = {'beats': [
    {'fx': 'hale_slips', 'shot': 'hole', **N('Не хватает сил. Рука Хейла выскальзывает. Озеро забирает его.', 'Not enough strength. Hale’s hand slips free. The lake takes him.', 3.0)},
    {'set': {'hale_drowned': 1, 'end_witness': 1}},
    {'go': 'c5_shore'}]}

SC['c5_s4_iris_fail'] = {'beats': [
    {'fx': 'edge_breaks', 'shot': 'hole', **N('Кромка ломается и под Ноем. Он в воде — и из последних сил выталкивает Айрис на лёд.', 'The edge breaks under Noah too. He’s in the water — and with the last of his strength he shoves Iris up onto the ice.', 3.6)},
    {'fx': 'noah_under', 'shot': 'hole', **N('Хейл вцепляется в него. Оба уходят под лёд.', 'Hale latches on to him. They both go under the ice.', 3.0)},
    {'set': {'iris_saved': 1, 'hale_drowned': 1, 'noah_drowned': 1, 'end_ice': 1}},
    {'go': 'c5_ice'}]}

SC['c5_s4_hale'] = {'beats': [
    {'fx': 'grab_hale', 'shot': 'hole_hands', **N('Ной хватает Хейла за воротник. Хейл отпускает Айрис и вцепляется в Ноя обеими руками.', 'Noah grabs Hale by the collar. Hale lets go of Iris and clamps onto Noah with both hands.', 3.4)},
    S('noah', 'Он за всё ответит.', 'He’s going to answer for this.', shot='noah_crawl'),
    S('noah', 'Ложись грудью на лёд! Ногами работай!', 'Get your chest on the ice! Kick with your legs!', shot='noah_crawl'),
    {'qte': 'press', 't': 7.0, 'n': 14, 'hint': PULL, 'fail': 'c5_s4_hale_fail', 'if': {'weak': '==0', 'help': '>0'}},
    {'qte': 'press', 't': 7.0, 'n': 17, 'hint': PULL, 'fail': 'c5_s4_hale_fail', 'if': {'weak': '==0', 'help': '==0'}},
    {'qte': 'press', 't': 7.0, 'n': 19, 'hint': PULL, 'fail': 'c5_s4_hale_fail', 'if': {'weak': '>0', 'help': '>0'}},
    {'qte': 'press', 't': 7.0, 'n': 22, 'hint': PULL, 'fail': 'c5_s4_hale_fail', 'if': {'weak': '>0', 'help': '==0'}},
    {'set': {'hale_saved': 1, 'iris_saved': 1, 'end_witness': 1}},
    {'fx': 'both_out', 'shot': 'hole', **N('Оба на льду: Хейл — синий, Айрис — мокрая, дрожит.', 'Both are on the ice: Hale blue with cold, Iris soaked and shaking.', 3.0)},
    {'go': 'c5_shore'}]}

SC['c5_s4_hale_fail'] = {'beats': [
    {'fx': 'iris_slips', 'shot': 'hole', **N('Пальцы Айрис соскальзывают с кромки. Ной ныряет за ней.', 'Iris’s fingers slip off the edge. Noah dives after her.', 3.0)},
    {'fx': 'noah_under', 'shot': 'hole', **N('Снизу он выталкивает её на лёд — и не всплывает.', 'From below, he pushes her up onto the ice — and doesn’t come back up.', 3.2)},
    {'set': {'hale_saved': 1, 'iris_saved': 1, 'noah_drowned': 1, 'end_ice': 1}},
    {'go': 'c5_ice'}]}

SC['c5_s4_dive'] = {'beats': [
    {'fx': 'dive', 'shot': 'hole', **N('Ной соскальзывает в воду, отрывает руки Хейла от Айрис и выталкивает её на лёд.', 'Noah slides into the water, tears Hale’s hands off Iris and pushes her up onto the ice.', 3.4)},
    {'fx': 'noah_under', 'shot': 'hole', **N('Хейл вцепляется в Ноя. Оба уходят под лёд.', 'Hale latches on to Noah. They both go under the ice.', 3.0)},
    {'set': {'iris_saved': 1, 'hale_drowned': 1, 'noah_drowned': 1, 'end_ice': 1}},
    {'go': 'c5_ice'}]}

SC['c5_s4_shadow'] = {'beats': [
    {'fx': 'crack', 'shot': 'hole', **N('Лёд проваливается. Хейл уцепился за кромку, Айрис бьётся в воде чуть дальше.', 'The ice gives way. Hale has caught the edge; Iris is thrashing in the water a little farther out.', 3.4)},
    {'fx': 'boot', 'shot': 'boot', **N('«Второй он» не ложится на лёд. Он подходит к Хейлу и наступает ботинком ему на пальцы.', '“The other him” doesn’t lie down on the ice. He walks up to Hale and steps on his fingers.', 3.6)},
    S('hale', 'Ной… Ной, пожалуйста…', 'Noah… Noah, please…', shot='hale_water'),
    {'fx': 'hale_slips', 'shot': 'hole', **N('Пальцы Хейла соскальзывают. Он уходит под лёд.', 'Hale’s fingers slip. He goes under the ice.', 2.8)},
    {'set': {'hale_killed': 1}},
    {'fx': 'iris_sinking', 'shot': 'hole', **N('Айрис уходит под воду — и снова выныривает. Всё слабее.', 'Iris goes under — and comes up again. Weaker each time.', 3.0)},
    {'qte': 'press', 't': 4.0, 'n': 4, 'hint': {'ru': '«Айрис!» — жми E / пробел', 'en': '“Iris!” — press E / Space'}, 'fail': 'c5_shadow_dark'},
    {'fx': 'wake_up', 'shot': 'crawl', **N('Ной приходит в себя. Падает на лёд и вытаскивает сестру.', 'Noah comes back to himself. He drops onto the ice and pulls his sister out.', 3.2)},
    {'set': {'iris_saved': 1, 'end_shadow': 1}},
    {'go': 'c5_shore'}]}

# ---------- «Мама» ----------
SC['c5_mama'] = {'save': True, 'beats': [
    {'fx': 'ice_takes', 'shot': 'ice_two', **N('Хейл стоит на тёмном льду и смотрит на Ноя, как на привидение. Лёд принимает его — тихо, без всплеска.', 'Hale stands on the dark ice staring at Noah like he’s a ghost. The ice takes him — quietly, without a splash.', 3.8)},
    {'fx': 'hand_edge', 'shot': 'hole', **N('Из воды на кромку ложится рука.', 'A hand comes up out of the water onto the edge.', 2.4)},
    S('hale', 'Ной… Руку…', 'Noah… Your hand…', shot='hale_water'),
    {'fx': 'eyes_clear', 'shot': 'noah_ice', **N('Ной открывает глаза. Они ясные.', 'Noah opens his eyes. They’re clear.', 2.4)},
    C(5, [O('Протянуть руку', 'Reach out', 'c5_mama_reach'),
          O('Не двигаться', 'Don’t move', 'c5_mama_still')], shot='noah_ice', silent='c5_mama_still')]}

SC['c5_mama_reach'] = {'beats': [
    {'set': {'hale_saved': 1, 'end_mama': 1}},
    {'fx': 'hale_out', 'shot': 'hole', **N('Ной вытаскивает Хейла. Бабушка перестаёт петь.', 'Noah pulls Hale out. Grandma stops singing.', 2.8)},
    S('agnes', 'Отпустила.', 'She’s let go.', shot='agnes_shore'),
    {'go': 'c5_shore'}]}

SC['c5_mama_still'] = {'beats': [
    {'set': {'hale_drowned': 1, 'end_mama': 1}},
    {'fx': 'hale_slips', 'shot': 'hole', **N('Рука соскальзывает с кромки. Бабушка перестаёт петь.', 'The hand slides off the edge. Grandma stops singing.', 2.8)},
    S('agnes', 'Своё забрала. Теперь отпустит.', 'She took what was hers. Now she’ll let go.', shot='agnes_shore'),
    {'go': 'c5_shore'}]}

# ---------- Сцена 5. «Берег» ----------
SC['c5_shore'] = {'save': True, 'time': '10:30', 'beats': [
    {'fx': 'shore'},
    {'if': {'hostage': '>0'}, 'else': 'c5_patrol'},
    {'if': {'orderly_dead': '>0'}, 'else': 'c5_shore_orderly'},
    {'fx': 'unlock', 'shot': 'sedan_shore', **N('Седан стоит у сарая с работающим мотором. Ной открывает задние двери снаружи. В машине тепло.', 'The sedan sits by the boathouse with its engine running. Noah opens the back doors from outside. It’s warm inside.', 3.6)},
    N('Зоя сонная, но живая.', 'Zoe is drowsy, but alive.', 2.4, shot='zoe_car', **I(zoe_taken='>0')),
    {'set': {'zoe_safe': 1}, 'if': {'zoe_taken': '>0'}},
    N('Дэнни бежит к Айрис.', 'Danny runs to Iris.', 2.2, shot='sedan_shore', **I(danny_hale='>0')),
    {'go': 'c5_patrol'}]}

SC['c5_shore_orderly'] = {'beats': [
    {'fx': 'orderly_flees', 'shot': 'sedan_shore', **N('Санитар бросает заложников у сарая и уезжает на седане.', 'The orderly abandons the hostages by the boathouse and drives off in the sedan.', 3.0)},
    N('Дэнни бежит к Айрис.', 'Danny runs to Iris.', 2.2, shot='sedan_shore', **I(danny_hale='>0')),
    {'if': {'zoe_taken': '>0'}, 'else': 'c5_patrol'},
    {'fx': 'zoe_falls', 'shot': 'zoe_snow', **N('Зоя падает в снег и не встаёт: лекарство и мороз.', 'Zoe collapses in the snow and doesn’t get up: the medication and the cold.', 3.0)},
    {'if': {'noah_drowned': '==0'}, 'else': 'c5_zoe_iris'},
    {'fx': 'rub_hands', 'shot': 'zoe_snow', **N('Растирать руки, трясти, тащить к дому — и всё время говорить с ней.', 'Rub her hands, shake her, drag her to the house — and keep talking to her the whole time.', 3.0)},
    S('zoe', 'Сегодня четверг?..', 'Is it Thursday…?', shot='zoe_face'),
    C(8, [O('«Пой. Ту, что в машине пела»', '“Sing. The one you sang in the car”', 'c5_zoe_sing', **I(zoe='>0')),
          O('«Я тебе верю»', '“I believe you”', 'c5_zoe_believe', **I(zoe_believed='>0')),
          O('«Зоя. Это я»', '“Zoe. It’s me”', 'c5_zoe_me', **I(romance='>0')),
          O('«Не спи! Слышишь? Не спи!»', '“Stay awake! You hear me? Stay awake!”', 'c5_zoe_try')], shot='noah_snow', silent='c5_zoe_try')]}

SC['c5_zoe_sing'] = {'beats': [
    S('noah', 'Нет. Сегодня ты не спишь. Пой. Ту, что в машине пела.', 'No. No sleeping today. Sing. The one you sang in the car.', shot='noah_snow'),
    N('Она поёт — тихо и фальшиво.', 'She sings — softly and off-key.', 2.6, fx='zoe_sing', shot='zoe_face'),
    {'go': 'c5_zoe_wake'}]}

SC['c5_zoe_believe'] = {'beats': [
    S('noah', 'Я тебе верю. Слышишь? Верю.', 'I believe you. You hear me? I believe you.', shot='noah_snow'),
    S('zoe', 'Ну хоть кто-то…', 'At least somebody does…', shot='zoe_face'),
    {'go': 'c5_zoe_wake'}]}

SC['c5_zoe_me'] = {'beats': [
    S('noah', 'Зоя. Это я.', 'Zoe. It’s me.', shot='noah_snow'),
    N('Её пальцы сжимают его руку.', 'Her fingers squeeze his hand.', 2.4, shot='zoe_face'),
    {'go': 'c5_zoe_wake'}]}

SC['c5_zoe_try'] = {'beats': [
    S('noah', 'Не спи! Слышишь? Не спи!', 'Stay awake! You hear me? Stay awake!', shot='noah_snow'),
    {'qte': 'mash', 't': 6.0, 'n': 16, 'hint': {'ru': 'Растирай руки — быстро жми E / пробел', 'en': 'Rub her hands — mash E / Space'}, 'fail': 'c5_zoe_dies'},
    {'go': 'c5_zoe_wake'}]}

SC['c5_zoe_iris'] = {'beats': [
    {'fx': 'rub_hands', 'shot': 'zoe_snow', **N('Ноя нет. Зою растирает Айрис — мокрая, сама вся дрожит.', 'Noah is gone. Iris rubs Zoe’s hands — soaked, shaking herself.', 3.0)},
    S('iris', 'Зоя, не спи! Пожалуйста… Я сегодня больше никого не отдам.', 'Zoe, don’t fall asleep! Please… I’m not losing anyone else today.', shot='iris_snow'),
    {'qte': 'mash', 't': 6.0, 'n': 14, 'hint': {'ru': 'Растирай руки — быстро жми E / пробел', 'en': 'Rub her hands — mash E / Space'}, 'fail': 'c5_zoe_dies'},
    {'go': 'c5_zoe_wake'}]}

SC['c5_zoe_wake'] = {'beats': [
    {'set': {'zoe_alive5': 1}},
    {'fx': 'zoe_eyes', 'shot': 'zoe_face', **N('Она открывает глаза.', 'She opens her eyes.', 2.0)},
    S('zoe', 'Ой. Это ты… А я думала — опять белая комната.', 'Oh. It’s you… I thought it was the white room again.', shot='zoe_face'),
    {'go': 'c5_patrol'}]}

SC['c5_zoe_dies'] = {'beats': [
    {'set': {'zoe_died': 1}},
    {'fx': 'zoe_still', 'shot': 'zoe_face', **N('Зоя засыпает в снегу. И больше не просыпается.', 'Zoe falls asleep in the snow. And never wakes up.', 3.2)},
    {'go': 'c5_patrol'}]}

SC['c5_patrol'] = {'beats': [
    {'fx': 'patrol', 'shot': 'patrol', **N('К полудню на берег выезжает патруль: утром у поваленной сосны нашли брошенную буханку, а оттуда одна дорога — к озеру.', 'By noon a police patrol drives down to the shore: that morning they found the abandoned van at the fallen pine, and from there only one road leads — to the lake.', 4.2)},
    N('Санитар далеко не уехал: на дороге его остановил тот же патруль. Он всё рассказал сам.', 'The orderly didn’t get far: the same patrol stopped him on the road. He told them everything himself.', 3.6, shot='patrol', **I(orderly_dead='==0')),
    {'if': {'end_ice': '>0'}, 'else': 'c5_route2'},
    {'go': 'c5_ice_epi'}]}

SC['c5_route2'] = {'beats': [
    {'if': {'end_shadow': '>0'}, 'else': 'c5_route3'},
    {'go': 'c5_shadow'}]}

SC['c5_route3'] = {'beats': [
    {'if': {'end_mama': '>0'}, 'else': 'c5_witness'},
    {'go': 'c5_mama_epi'}]}

# ---------- доказательства (для «Свидетеля», «Лёда», «Мамы») ----------
PROOF = [
    SET({'diary_burned': '==0'}, proof=1),
    SET({'zoe': '>0', 'zoe_gone': '==0'}, proof=1),
    SET({'zoe_alive5': '>0'}, proof=1), SET({'zoe_safe': '>0'}, proof=1),
    SET({'agnes_dead': '==0'}, proof=1),
    SET({'orderly_dead': '==0'}, proof=1),
    SET({'danny_hale': '>0'}, proof=1),
    SET({'look_dad': '>0'}, proof=1),
    SET({'remembered': '>0', 'proof': '>=2'}, exposed=1),
]

# ---------- «Свидетель» ----------
SC['c5_witness'] = {'save': True, 'beats': PROOF + [
    {'fx': 'hands_out', 'shot': 'patrol', **N('Ной сам идёт навстречу патрулю и протягивает руки.', 'Noah walks toward the patrol himself and holds out his hands.', 3.0)},
    {'fx': 'hale_stove', 'shot': 'stove5', **N('Хейла, синего от холода, сажают у печки. Из сухого остался только мамин плащ.', 'Hale, blue with cold, is sat down by the stove. The only dry thing left is Mom’s raincoat.', 3.6, **I(hale_saved='>0'))},
    N('Бабушка молча накидывает плащ Хейлу на плечи.', 'Grandma silently drapes the raincoat over Hale’s shoulders.', 3.0, shot='stove5', **{'if': {'hale_saved': '>0', 'agnes_dead': '==0'}}),
    {'fx': 'fade_out', 'dur': 1.4},
    {'if': {'exposed': '>0'}, 'else': 'c5_witness_bitter'},
    T('Хейла судят. «Тихие сосны» закрывают.', 'Hale stands trial. Quiet Pines is shut down.', 3.2, **I(hale_saved='>0')),
    T('Хейл утонул. Но правду о нём узнали все. «Тихие сосны» закрывают.', 'Hale drowned. But everyone learned the truth about him. Quiet Pines is shut down.', 3.4, **I(hale_saved='==0')),
    {'go': 'c5_visit'}]}

SC['c5_witness_bitter'] = {'beats': [
    {'set': {'bitter': 1}},
    T('Слово Ноя против слова врача. Доказательств мало.', 'Noah’s word against a doctor’s. Not enough proof.', 3.2),
    {'go': 'c5_visit'}]}

SC['c5_visit'] = {'beats': [
    {'fx': 'visit_room'},
    {'fx': 'fade_in', 'shot': 'visit', **N('Комната для свиданий. Айрис садится напротив.', 'A prison visiting room. Iris sits down across from him.', 3.0)},
    S('iris', 'Три года. Судья сказал, ты меня защищал.', 'Three years. The judge said you were protecting me.', shot='iris_visit', **{'if': {'roman_dead': '==0', 'orderly_dead': '==0'}}),
    {'if': {'roman_dead': '==0', 'orderly_dead': '==0'}, 'else': 'c5_visit8'},
    {'go': 'c5_visit2'}]}

SC['c5_visit8'] = {'beats': [
    S('iris', 'Восемь лет. Судья сказал: за папу — ты меня защищал. А остальное…', 'Eight years. The judge said that with Dad, you were protecting me. But the rest…', shot='iris_visit'),
    {'go': 'c5_visit2'}]}

SC['c5_visit2'] = {'beats': [
    {'if': {'bitter': '>0'}, 'else': 'c5_visit_full'},
    {'fx': 'drawing_car', 'shot': 'visit_table', **N('Она кладёт на стол рисунок. На рисунке — чёрная машина.', 'She puts a drawing on the table. In the drawing — the black car.', 3.0)},
    S('iris', 'Его отпустили. Ничего. Я вырасту.', 'They let him go. It’s okay. I won’t be a kid forever.', shot='iris_visit', **I(hale_saved='>0')),
    S('iris', 'Все думают, он просто утонул. Пусть думают. Я-то знаю.', 'Everyone thinks he just drowned. Let them. I know.', shot='iris_visit', **I(hale_saved='==0')),
    {'go': 'c5_cards'}]}

SC['c5_visit_full'] = {'beats': [
    {'if': {'trust': '>=2'}, 'else': 'c5_visit_cold'},
    S('iris', 'Я буду приходить. Каждую неделю.', 'I’ll visit. Every week.', shot='iris_visit'),
    {'fx': 'drawing_dock', 'shot': 'visit_table', **N('Она кладёт на стол рисунок: они вдвоём на мостках, лето.', 'She puts a drawing on the table: the two of them on the dock, summer.', 3.0)},
    S('noah', 'Ты нарисовала нас?', 'You drew us?', shot='noah_visit'),
    S('iris', 'Я нарисовала, как будет.', 'I drew how it’s going to be.', shot='iris_visit'),
    N('Рядом с Айрис сидит Зоя — и Айрис впервые не отодвигается.', 'Zoe sits next to Iris — and for the first time, Iris doesn’t move away.', 3.0, shot='visit', **{'if': {'romance': '>0', 'zoe_died': '==0', 'zoe_taken': '==0'}}),
    {'go': 'c5_cards'}]}

SC['c5_visit_cold'] = {'beats': [
    S('iris', 'Я пока не могу. Но я ещё приду.', 'I can’t. Not yet. But I’ll come again.', shot='iris_visit'),
    {'go': 'c5_cards'}]}

# ---------- «Тень» ----------
SC['c5_shadow'] = {'save': True, 'beats': [
    {'fx': 'iris_crawls', 'shot': 'iris_crawl', **N('Айрис лежит на льду, мокрая, и смотрит на брата. Потом отползает от него — как от чужого.', 'Iris lies on the ice, soaked, staring at her brother. Then she crawls away from him — like from a stranger.', 3.6)},
    S('iris', 'Где он? Где мой брат?', 'Where is he? Where’s my brother?', shot='iris_crawl'),
    N('Приезжает патруль. Ной не сопротивляется. Айрис уезжает вместе со всеми и ни разу не оборачивается.', 'The patrol arrives. Noah doesn’t resist. Iris leaves with everyone else and never once looks back.', 3.8, shot='patrol'),
    {'fx': 'fade_out', 'dur': 1.4},
    {'fx': 'cell'},
    {'fx': 'fade_in', 'shot': 'cell', **N('Камера. Вместо зеркала — полированная сталь. Ной умывается и поднимает голову.', 'A cell. Instead of a mirror — polished steel. Noah washes his face and looks up.', 3.4)},
    {'fx': 'steel_smile', 'shot': 'steel', **N('Отражение улыбается чуть раньше, чем он.', 'His reflection smiles a moment before he does.', 3.4)},
    {'set': {'end_shadow': 1}},
    {'go': 'c5_cards'}]}

SC['c5_shadow_dark'] = {'save': True, 'beats': [
    {'set': {'iris_lost': 1, 'end_shadow_dark': 1, 'end_shadow': 1}},
    {'fx': 'iris_gone', 'shot': 'hole', **N('«Второй он» смотрит, как Айрис уходит под лёд.', '“The other him” watches Iris slip under the ice.', 3.2)},
    N('Ной сидит у полыньи, пока его не поднимает патруль.', 'Noah sits by the hole in the ice until the patrol lifts him to his feet.', 3.2, shot='hole_sit'),
    {'fx': 'notebook_ice', 'shot': 'notebook_ice', **N('На льду — блокнот Айрис. Ветер листает страницы. Последний рисунок — они вдвоём на мостках, лето.', 'Iris’s notebook lies on the ice. The wind turns the pages. The last drawing — the two of them on the dock, summer.', 4.0)},
    {'fx': 'water_smile', 'shot': 'water_face', **N('Полынью затягивает тонким льдом. В чёрной воде его отражение улыбается. Он — нет.', 'Thin ice closes over the hole. In the black water, his reflection is smiling. He isn’t.', 4.0)},
    {'go': 'c5_cards'}]}

# ---------- «Лёд» ----------
SC['c5_ice'] = {'save': True, 'beats': [
    {'fx': 'iris_screams', 'shot': 'iris_crawl', **N('Айрис лежит у края полыньи и кричит его имя.', 'Iris lies at the edge of the hole and screams his name.', 3.0)},
    N('Тишина.', 'Silence.', 2.4, shot='hole'),
    N('Потом она встаёт — потому что на берегу кому-то нужна помощь.', 'Then she gets up — because someone on the shore needs help.', 3.0, shot='iris_crawl'),
    {'go': 'c5_shore'}]}

SC['c5_ice_epi'] = {'beats': PROOF + [
    {'fx': 'fade_out', 'dur': 1.4},
    {'fx': 'spring'},
    {'fx': 'fade_in', 'shot': 'spring_dock', **N('Весна. Лёд сошёл. Айрис на мостках опускает в воду рисунок: Ной и мама держатся за руки под водой.', 'Spring. The ice is gone. On the dock, Iris lowers a drawing into the water: Noah and Mom holding hands under the water.', 4.4)},
    S('agnes', 'Озеро своих не отпускает. Теперь они там вдвоём.', 'The lake keeps its own. Now they’re together down there.', shot='agnes_spring', **I(agnes_dead='==0')),
    S('iris', 'Ты теперь с мамой. А я тут. Буду жить за двоих.', 'You’re with Mom now. And I’m still here. I’ll live for both of us.', shot='iris_spring', **I(agnes_dead='>0')),
    T('Правду о Хейле суду рассказала Айрис.', 'Iris told the court the truth about Hale.', 3.0, **I(exposed='>0')),
    {'go': 'c5_cards'}]}

# ---------- «Мама» — эпилог ----------
SC['c5_mama_epi'] = {'beats': PROOF + [
    {'fx': 'hands_out', 'shot': 'patrol', **N('Ной сам идёт к патрулю.', 'Noah walks to the patrol himself.', 2.6)},
    T('Хейла судят.', 'Hale stands trial.', 2.6, **{'if': {'hale_saved': '>0', 'exposed': '>0'}}),
    {'fx': 'fade_out', 'dur': 1.4},
    {'fx': 'summer_day'},
    {'fx': 'fade_in', 'shot': 'summer_dock', **N('Лето. Ной вернулся. Мостки. Айрис — постарше, с блокнотом.', 'Summer. Noah is back. The dock. Iris — a little older, with her notebook.', 3.6)},
    N('Провалов больше нет: отражение в воде моргает вместе с ним.', 'No more blackouts: his reflection in the water blinks when he does.', 3.0, shot='water_face'),
    S('iris', 'Это была она?', 'Was it her?', shot='iris_summer'),
    {'fx': 'own_smile', 'shot': 'noah_summer', **N('Ной молча улыбается — своей улыбкой.', 'Noah smiles without a word — his own smile.', 3.2)},
    {'go': 'c5_cards'}]}

# ---------- «Забытьё» ----------
SC['c5_oblivion'] = {'save': True, 'beats': [
    {'set': {'end_oblivion': 1}},
    S('hale', 'Отведите её в дом, ей нужно в тепло.', 'Take her inside, she needs to get warm.', shot='hale_ice', **I(zoe_taken='>0')),
    S('hale', 'Отведите мальчика в дом, он замёрз.', 'Take the boy inside, he’s freezing.', shot='hale_ice', **{'if': {'zoe_taken': '==0', 'danny_hale': '>0'}}),
    N('Зою уводят к печке.', 'They lead Zoe in to the stove.', 2.2, shot='sedan_shore', **I(zoe_taken='>0')),
    N('Дэнни бежит к Айрис.', 'Danny runs to Iris.', 2.2, shot='sedan_shore', **I(danny_hale='>0')),
    {'if': {'remembered': '>0'}, 'else': 'c5_obl_forgot'},
    {'fx': 'walk_car', 'shot': 'ice_wide', **N('Ной идёт к машине сам.', 'Noah walks to the car on his own.', 2.4, **I(silent_ice='==0'))},
    {'fx': 'walk_car', 'shot': 'ice_wide', **N('Хейл ведёт его под локоть, и Ной идёт, как во сне.', 'Hale leads him by the elbow, and Noah walks as if in a dream.', 2.8, **I(silent_ice='>0'))},
    {'if': {'trust': '>=3'}, 'else': 'c5_obl_still'},
    {'fx': 'sign', 'shot': 'mirror_sign', **N('Айрис бежит по мосткам и держит перед собой раскрытый блокнот. Крупными буквами.', 'Iris runs down the dock holding her notebook open in front of her. In big letters.', 3.6)},
    {'go': 'c5_obl_car'}]}

SC['c5_obl_still'] = {'beats': [
    {'fx': 'iris_dock', 'shot': 'mirror_dock', **N('Айрис стоит на мостках и не двигается. В зеркале она становится всё меньше.', 'Iris stands on the dock and doesn’t move. In the mirror she grows smaller and smaller.', 3.6)},
    {'go': 'c5_obl_car'}]}

SC['c5_obl_forgot'] = {'beats': [
    {'fx': 'walk_car', 'shot': 'ice_wide', **N('Ной садится в машину спокойно, как в автобус.', 'Noah gets into the car calmly, like getting on a bus.', 2.6)},
    N('Айрис молча суёт ему в карман сложенный листок.', 'Iris silently slips a folded piece of paper into his pocket.', 2.8, fx='pocket_note', shot='ice_two', **I(trust='>=2')),
    {'set': {'note_pocket': 1}, 'if': {'trust': '>=2'}},
    {'fx': 'empty_dock', 'shot': 'mirror_dock', **N('В зеркале — пустые мостки и мамин плащ в окне дачи.', 'In the mirror — the empty dock, and Mom’s raincoat in the lake house window.', 3.4)},
    {'go': 'c5_obl_car'}]}

SC['c5_obl_car'] = {'beats': [
    {'fx': 'injection', **S('hale', 'Поспи, Ной.', 'Get some sleep, Noah.', shot='car_hale')},
    {'fx': 'fade_out', 'dur': 1.4},
    T('На дороге седан останавливает патруль. Хейл показывает бумаги: «Мой пациент».', 'A patrol stops the sedan on the road. Hale shows them papers: “My patient.”', 3.6),
    T('Суд признаёт Ноя невменяемым. Принудительное лечение. Лечащий врач — доктор Хейл.', 'The court finds Noah not responsible. Compulsory treatment. Attending physician: Dr. Hale.', 3.8),
    {'fx': 'white_room'},
    {'fx': 'fade_in', 'shot': 'white_room', **N('Белая комната. Ной рисует на листке мостки и фонарь — и не понимает, что рисует.', 'A white room. Noah draws a dock and a lantern on a sheet of paper — and doesn’t understand what he’s drawing.', 3.8)},
    N('В руке — листок Айрис: они вдвоём на мостках. Он не узнаёт ни её, ни себя.', 'In his hand — Iris’s note: the two of them on the dock. He recognizes neither her nor himself.', 3.6, shot='white_room', **I(note_pocket='>0')),
    {'fx': 'hale_takes', **S('hale', 'Опять этот сон? Ничего. Мы с ним справимся.', 'That dream again? Don’t worry. We’ll take care of it.', shot='hale_white')},
    {'go': 'c5_cards'}]}

# ---------- что стало с каждым ----------
SC['c5_cards'] = {'beats': [
    {'fx': 'fade_out', 'dur': 1.2},
    {'fx': 'black'},
    T('Что стало с каждым', 'What became of everyone', 2.2),
    # Ной
    T('Ной — в тюрьме. Срок — три года.', 'Noah — in prison. Three years.', 3.0, **{'if': {'end_witness': '>0', 'roman_dead': '==0', 'orderly_dead': '==0'}}),
    T('Ной — в тюрьме. Восемь лет.', 'Noah — in prison. Eight years.', 3.0, **{'if': {'end_witness': '>0', 'roman_dead': '>0'}}),
    T('Ной — в тюрьме. Восемь лет.', 'Noah — in prison. Eight years.', 3.0, **{'if': {'end_witness': '>0', 'roman_dead': '==0', 'orderly_dead': '>0'}}),
    T('Ной — подо льдом.', 'Noah — under the ice.', 2.6, **I(end_ice='>0')),
    T('Ной — в тюрьме. На суде он не сказал ни слова.', 'Noah — in prison. He didn’t say a word at his trial.', 3.0, **I(end_shadow='>0')),
    T('Ной — дома. Провалов больше нет.', 'Noah — home. No more blackouts.', 2.8, **I(end_mama='>0')),
    T('Ной — в «Тихих соснах».', 'Noah — at Quiet Pines.', 2.6, **I(end_oblivion='>0')),
    # Айрис
    T('Айрис — подо льдом.', 'Iris — under the ice.', 2.6, **I(iris_lost='>0')),
    T('Айрис живёт у бабушки.', 'Iris lives with Grandma.', 2.6, **{'if': {'iris_lost': '==0', 'agnes_dead': '==0'}}),
    T('Айрис живёт у Евы, на севере.', 'Iris lives with Eva, up north.', 2.6, **{'if': {'iris_lost': '==0', 'agnes_dead': '>0', 'eva_alive': '>0'}}),
    T('Айрис — в детдоме. Сбылось то, чего она боялась.', 'Iris — in a children’s home. Just what she was afraid of.', 3.0, **{'if': {'iris_lost': '==0', 'agnes_dead': '>0', 'eva_alive': '==0'}}),
    T('Через три года ей восемнадцать. Она считает дни.', 'She turns eighteen in three years. She’s counting the days.', 3.0, **I(end_oblivion='>0')),
    # Зоя
    T('Зоя дала показания. «Тихие сосны» закрыли.', 'Zoe testified. Quiet Pines was shut down.', 3.0, **{'if': {'zoe': '>0', 'zoe_died': '==0', 'exposed': '>0', 'end_oblivion': '==0'}}),
    T('Зоя жива. Но ей никто не поверил.', 'Zoe is alive. But no one believed her.', 3.0, **{'if': {'zoe': '>0', 'zoe_died': '==0', 'exposed': '==0', 'end_oblivion': '==0'}}),
    T('Зоя уснула в снегу у сарая.', 'Zoe fell asleep in the snow by the boathouse.', 2.8, **I(zoe_died='>0')),
    T('Зою через месяц мать снова отвезла в «Тихие сосны». Ей кажется, что сегодня четверг.', 'A month later, her mother took Zoe back to Quiet Pines. She thinks it’s Thursday.', 3.6, **{'if': {'zoe': '>0', 'end_oblivion': '>0'}}),
    T('Она ждёт его.', 'She’s waiting for him.', 2.4, **{'if': {'romance': '>0', 'zoe_died': '==0', 'end_witness': '>0'}}),
    T('Она дождалась.', 'She waited for him.', 2.4, **{'if': {'romance': '>0', 'zoe_died': '==0', 'end_mama': '>0'}}),
    # Ева
    T('Ева назвала дочь Анной, к мужу не вернулась и живёт у сестры на севере.', 'Eva named her daughter Anna, never went back to her husband, and lives with her sister up north.', 3.6, **I(eva_alive='>0')),
    T('Ева погибла в буран. Анну забрала её сестра.', 'Eva died in the storm. Her sister took Anna.', 3.0, **I(eva_dead='>0')),
    # Дэнни
    T('За Дэнни приехала мама — трезвая. На прощание он отдал Айрис свою рацию.', 'Danny’s mom came for him — sober. As a goodbye, he gave Iris his walkie-talkie.', 3.6, **{'if': {'danny': '>0', 'danny_lost': '==0', 'danny_hale': '==0'}}),
    T('На суде Дэнни сказал про Ноя: «Он не хотел».', 'At the trial, Danny said of Noah: “He didn’t mean to.”', 3.0, **I(danny_for='>0')),
    T('На суде Дэнни сказал: «Он просил никому не говорить».', 'At the trial, Danny said: “He told me not to tell anyone.”', 3.0, **I(danny_against='>0')),
    T('Дэнни рассказал полиции, как доктор выманил его в лесу.', 'Danny told the police how the doctor lured him out in the woods.', 3.0, **{'if': {'danny_hale': '>0', 'end_oblivion': '==0'}}),
    T('Дэнни рассказал, как доктор выманил его в лесу. Ему никто не поверил.', 'Danny told how the doctor lured him out in the woods. No one believed him.', 3.2, **{'if': {'danny_hale': '>0', 'end_oblivion': '>0'}}),
    T('Дэнни так и не нашли.', 'Danny was never found.', 2.6, **I(danny_lost='>0')),
    T('Дэнни больше никогда не верит взрослым на слово.', 'Danny never takes a grown-up at their word again.', 3.0, **{'if': {'promise5': '>0', 'end_oblivion': '>0'}}),
    T('Дэнни больше никогда не верит взрослым на слово.', 'Danny never takes a grown-up at their word again.', 3.0, **{'if': {'promise5': '>0', 'end_shadow': '>0'}}),
    # Агнес
    T('Агнес ждёт внука.', 'Agnes is waiting for her grandson.', 2.6, **{'if': {'agnes_dead': '==0', 'end_ice': '==0'}}),
    T('Агнес погибла в лесу.', 'Agnes died in the forest.', 2.6, **I(agnes_dead='>0')),
    # Роман, санитар, Хейл
    T('Кассир Роман на суде сказал: «Он просил помочь сестре».', 'Roman the cashier told the court: “He asked me to help his sister.”', 3.2, **I(roman_ally='>0')),
    T('Кассир Роман выжил и на суде узнал Ноя.', 'Roman the cashier survived and recognized Noah in court.', 3.0, **I(roman_hurt='>0')),
    T('Кассир Роман погиб.', 'Roman the cashier died.', 2.4, **I(roman_dead='>0')),
    T('Санитара взяли на дороге. Он дал показания против Хейла.', 'The orderly was caught on the road. He testified against Hale.', 3.2, **{'if': {'orderly_dead': '==0', 'end_oblivion': '==0'}}),
    T('Хейл — под судом.', 'Hale — on trial.', 2.4, **{'if': {'hale_saved': '>0', 'exposed': '>0'}}),
    T('Хейла отпустили за недостатком улик.', 'Hale was released for lack of evidence.', 2.8, **{'if': {'hale_saved': '>0', 'exposed': '==0'}}),
    T('Хейл утонул. «Несчастный случай».', 'Hale drowned. “An accident.”', 2.8, **I(hale_drowned='>0')),
    T('Хейла убил «второй Ной».', '“The other Noah” killed Hale.', 2.6, **I(hale_killed='>0')),
    T('Хейл лечит Ноя.', 'Hale is treating Noah.', 2.4, **I(end_oblivion='>0')),
    {'end': 5}]}

DATA = {
    'chapter': 5, 'start': 'c5_recap', 'ready': True,    # True — когда постановка главы готова (chapter5.gd)
    'title': {'ru': 'ГЛАВА 5 · ОЗЕРО', 'en': 'CHAPTER 5 · THE LAKE'},
    'names': {'mom': {'ru': 'АННА', 'en': 'ANNA'}, 'victor': {'ru': 'ВИКТОР', 'en': 'VICTOR'}},
    'flags': {k: 0 for k in ['hostage', 'ptotal', 'hub_seen', 't_iris', 't_zoe', 't_agnes', 't_danny', 't_eva', 'zoe_hook', 'danny_rope',
                             'promise5', 'iris_refused', 'pills5', 'pills_burned5', 'look_mom', 'look_dad', 'eyes_shut', 'remembered',
                             'agnes_sings', 'diary_burned', 'silent_ice', 'said_remember', 'said_wont', 'smiled', 'listened', 'weak', 'help',
                             'iris_saved', 'hale_saved', 'hale_drowned', 'hale_killed', 'noah_drowned', 'iris_lost', 'zoe_safe',
                             'zoe_alive5', 'zoe_died', 'proof', 'exposed', 'bitter', 'note_pocket',
                             'end_witness', 'end_shadow', 'end_shadow_dark', 'end_ice', 'end_mama', 'end_oblivion']},
    'map': [
        {'key': 'pills', 'ru': 'Таблетки', 'en': 'The pills', 'opts': [
            {'id': 'took', 'ru': 'принял', 'en': 'took one', 'if': {'pills5': '>0'}},
            {'id': 'burned', 'ru': 'в печь', 'en': 'into the stove', 'if': {'pills_burned5': '>0'}},
            {'id': 'refused', 'ru': 'Айрис не дала', 'en': 'Iris said no', 'if': {'iris_refused': '>0'}},
            {'id': 'none', 'ru': 'обошёлся', 'en': 'went without', 'if': {}}]},
        {'key': 'memory', 'ru': 'Сарай', 'en': 'The boathouse', 'opts': [
            {'id': 'mom', 'ru': 'смотрел на маму', 'en': 'watched Mom', 'if': {'look_mom': '>0'}},
            {'id': 'dad', 'ru': 'смотрел на отца', 'en': 'watched Dad', 'if': {'look_dad': '>0'}},
            {'id': 'shut', 'ru': 'зажмурился', 'en': 'shut his eyes', 'if': {'eyes_shut': '>0'}},
            {'id': 'ice', 'ru': 'там просто лёд', 'en': 'only ice', 'if': {}}]},
        {'key': 'ice', 'ru': 'На льду', 'en': 'On the ice', 'opts': [
            {'id': 'listen', 'ru': 'слушал', 'en': 'listened', 'if': {'listened': '>0'}},
            {'id': 'smile', 'ru': 'улыбнулся', 'en': 'smiled', 'if': {'smiled': '>0'}},
            {'id': 'remember', 'ru': '«Я всё помню»', 'en': '“I remember”', 'if': {'said_remember': '>0'}},
            {'id': 'wont', 'ru': '«Я не поеду»', 'en': '“I’m not going”', 'if': {'said_wont': '>0'}},
            {'id': 'go', 'ru': 'поехал с Хейлом', 'en': 'went with Hale', 'if': {}}]},
        {'key': 'hale', 'ru': 'Хейл', 'en': 'Hale', 'opts': [
            {'id': 'saved', 'ru': 'вытащил', 'en': 'pulled out', 'if': {'hale_saved': '>0'}},
            {'id': 'drowned', 'ru': 'утонул', 'en': 'drowned', 'if': {'hale_drowned': '>0'}},
            {'id': 'killed', 'ru': 'убит', 'en': 'killed', 'if': {'hale_killed': '>0'}},
            {'id': 'won', 'ru': 'увёз Ноя', 'en': 'took Noah', 'if': {}}]},
        {'key': 'zoe', 'ru': 'Зоя', 'en': 'Zoe', 'opts': [
            {'id': 'died', 'ru': 'уснула в снегу', 'en': 'fell asleep in the snow', 'if': {'zoe_died': '>0'}},
            {'id': 'woke', 'ru': 'не дал уснуть', 'en': 'kept her awake', 'if': {'zoe_alive5': '>0'}},
            {'id': 'with', 'ru': 'жива', 'en': 'alive', 'if': {'zoe': '>0'}},
            {'id': 'none', 'ru': 'не с ними', 'en': 'not with them', 'if': {}}]},
        {'key': 'end', 'ru': 'Концовка', 'en': 'Ending', 'opts': [
            {'id': 'mama', 'ru': '«Мама»', 'en': '“Mother”', 'if': {'end_mama': '>0'}},
            {'id': 'shadow', 'ru': '«Тень»', 'en': '“Shadow”', 'if': {'end_shadow': '>0'}},
            {'id': 'ice', 'ru': '«Лёд»', 'en': '“Ice”', 'if': {'end_ice': '>0'}},
            {'id': 'witness', 'ru': '«Свидетель»', 'en': '“Witness”', 'if': {'end_witness': '>0'}},
            {'id': 'oblivion', 'ru': '«Забытьё»', 'en': '“Oblivion”', 'if': {}}]},
    ],
    'scenes': SC,
}

if __name__ == '__main__':
    json.dump(DATA, open(OUT, 'w'), ensure_ascii=False, indent=1)
    open(OUT, 'a').write('\n')
    says = sum(1 for s in SC.values() for b in s['beats'] if 'say' in b)
    print('ch5.json:', len(SC), 'сцен,', says, 'реплик')
