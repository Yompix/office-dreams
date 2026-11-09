# Режиссура кат-сцен (0.6). Выполняется внутри tools/story.py (exec), после того как собраны все главы.
# Реплики (тексты, id, озвучка) НЕ трогаем — меняем только кадры (shots) и действия актёров (actors).
#
# Каждая сцена — как эпизод сериала / сюжетная миссия GTA V: общий план → действие под реплики →
# крупные планы говорящего и слушающего → финальный «удар». Таймлайн привязан к озвучке:
# L[i] — секунда, когда начинается реплика i (по длине .ogg + пауза 1.2 с), E — когда закончилась последняя.
#
# Новые реплики режиссёра (story/DirectorCues.java): sfx, music, hold, give, door, anim, turn, tp, glass, flash.
# Музыка — треки радио как партитура (клиент: client/story/CutsceneMusic.java, гаснет с концом сцены).

import struct as _st

VOICE_DIR = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'novograd', 'sounds', 'voice')
GAP = 1.25  # CutsceneScreen: следующая реплика — через 1.2 с после конца голоса (+ тик)


def voice_len(vid):
    """Длина озвучки (Ogg Vorbis): последняя granule / частота."""
    p = os.path.join(VOICE_DIR, vid + '.ogg')
    try:
        b = open(p, 'rb').read()
    except OSError:
        return 3.0
    k = b.find(b'\x01vorbis')
    rate = _st.unpack('<I', b[k + 12:k + 16])[0] if k >= 0 else 44100
    j = b.rfind(b'OggS')
    gran = _st.unpack('<q', b[j + 6:j + 14])[0]
    return max(0.5, gran / float(rate or 44100))


def timing(lines_):
    starts, t = [], 0.0
    for ln in lines_:
        starts.append(round(t, 2))
        t += voice_len(ln['voice']) + GAP
    return starts, round(t, 2)


def cuts_of(mid):
    for ch_ in chapters:
        for mi_ in ch_['missions']:
            if mi_['id'] == mid:
                return [x for x in mi_['steps'] if x.get('type') == 'cutscene']
    raise KeyError(mid)


# ---- типовые планы
def CU(who, sec, side=0.6, fov=50, roll=None, shake=None):
    """Крупный план лица: камера перед героем на уровне глаз."""
    return F(who, sec, (-2.3, 1.6, side), shake=shake, fov=fov, fov2=fov - 6, roll=roll)


def LOW(who, sec, side=1.2, roll=None, shake=None):
    """Нижний ракурс спереди: герой «нависает» (злодей, угроза)."""
    return F(who, sec, (-3.0, 0.45, side), shake=shake, fov=62, fov2=56, roll=roll)


def OTS(who, sec, side=1.3, shake=None):
    """Из-за плеча / за спиной идущего героя."""
    return F(who, sec, (3.4, 2.2, side), shake=shake, fov=64, fov2=60)


def CHASE(car, sec, side=2.5, shake=0.35):
    return F(car, sec, (9, 2.6, side), shake=shake)


def HOOD(car, sec, side=0.6):
    """Камера перед капотом смотрит в лобовое стекло: видно, кто в машине."""
    return F(car, sec, (-5.2, 1.7, side), fov=58, fov2=54)


def SIDE(car, sec, side=5.5):
    return F(car, sec, (0.5, 1.5, side), fov=70, shake=0.2)


def direct(mid, k, shots, cues, music=None, grade=None, vol=0.32):
    """Переставить k-ю кат-сцену миссии: shots — кадры, cues(L, E) — действия актёров."""
    st = cuts_of(mid)[k]
    L, E = timing(st['lines'])
    acts = list(cues(L, E))
    if music:
        acts.insert(0, [0, 'music', music, vol])
    total = sum(x['sec'] for x in shots)
    if total < E + 1.0:  # последний кадр держим, пока не договорят
        shots[-1]['sec'] = round(shots[-1]['sec'] + E + 1.0 - total, 2)
    for a in acts:
        a[0] = round(max(0.0, a[0]), 2)
    st['shots'] = shots
    st['actors'] = sorted(acts, key=lambda a: a[0])
    st.pop('grade', None)
    if grade: st['grade'] = grade
    return st


# =====================================================================  АКТ I
# a1m1 — пролог: гонщик рвёт по центру, за ним патруль с сиреной; порт — грузчики «Синих»;
# промзона — Ящеры с битами; финал — Джейк у гаража нервно ходит с телефоном.
direct('a1m1', 0, [
    S('cam_island_far', 'cam_dt_a', 6),
    CHASE('racer', 5, 2.5),
    S('dt_2+0,30,3', 'dt_2+0,16,2.5', 3.5, track='cop'),
    S('port_warehouse+12,8,5', 'port_warehouse+7,5,2.5', 4.5, track='s1'),
    S('gang_hq+10,10,6', 'gang_hq+6,8,2.5', 4.5, track='g1'),
    S('garage_door+10,6,3', 'garage_door+6,4,2', 4, track='gl'),
    CU('gl', 3.5, side=0.8),
], lambda L, E: [
    [0, 'car', 'racer', 'dt_1', 2], [0.2, 'drive', 'racer', ['dt_2', 'dt_3'], 0.95],
    [2.5, 'car', 'cop', 'dt_1', 6], [2.6, 'drive', 'cop', ['dt_2', 'dt_3'], 0.95],
    [6.5, 'sfx', 'cop', 'novograd:siren', 3.0], [9.5, 'sfx', 'cop', 'novograd:siren', 3.0],
    [0, 'spawn', 's1', 'port_warehouse+4,3', 'gang', 'Грузчик'], [0, 'spawn', 's2', 'port_warehouse+-2,3', 'gang', 'Синий'],
    [12, 'walk', 's1', 'port_warehouse+-6,3'], [12.1, 'act', 's1', 'CARRY'], [12.2, 'act', 's2', 'POINT', 's1'],
    [0, 'spawn', 'g1', 'gang_hq+1,4', 'gang', 'Ящер'], [0, 'spawn', 'g2', 'gang_hq+3,5', 'gang', 'Ящер'],
    [16, 'act', 'g1', 'LAUGH', 'g2'], [16.1, 'hold', 'g1', 'novograd:bat'], [16.2, 'act', 'g2', 'SMOKE'],
    [0, 'spawn', 'gl', 'garage_door+2,1', 'c25', 'Джейк'], [20, 'act', 'gl', 'PHONEWALK'], [20.1, 'walk', 'gl', 'garage_door+5,2'],
    [23, 'act', 'gl', 'UPSET'], [25.5, 'turn', 'gl', 'garage_door+12,8'], [26, 'act', 'gl', 'PHONE'],
], music='radio.trassa128_0')

# a1m2 — Сторм влетает на старт с сигналом, хлопает дверью, идёт на Ники; её усмешка; он пинает колесо.
direct('a1m2', 0, [
    S('race_start+22,-5,3', 'race_start+12,-4,2.5', 5.5, track='storm_car'),
    LOW('shtorm', 3),
    CU('nika', 3, side=-0.7),
    S('race_start+5,2,1.2', 'race_start+5.5,2.5,1.2', 4, track='nika', fov=55, fov2=48),
    S('race_start+12,6,4', 'race_start+16,8,6', 3, track='shtorm'),
], lambda L, E: [
    [0, 'spawn', 'nika', 'race_start+0,-4'], [0, 'act', 'nika', 'LEAN'],
    [0, 'spawn', 'f1', 'race_start+-3,-2', 'citizen', 'Фанат'], [0, 'act', 'f1', 'CHEER'],
    [0, 'spawn', 'f2', 'race_start+-2,-6', 'citizen', 'Фанатка'], [0, 'act', 'f2', 'DANCE'],
    [0, 'car', 'storm_car', 'race_start+60,0', 2, 90], [0.2, 'drive', 'storm_car', ['race_start+6,0'], 0.75],
    [3.2, 'sfx', 'storm_car', 'novograd:car_horn', 2.0], [3.3, 'act', 'f1', 'POINT', 'storm_car'],
    [5.0, 'spawn', 'shtorm', 'race_start+6,2'], [5.1, 'sfx', 'shtorm', 'minecraft:block.iron_door.close', 1.2, 0.8],
    [5.3, 'walk', 'shtorm', 'race_start+3,-1'], [5.4, 'act', 'nika', 'none'], [5.5, 'face', 'nika', 'shtorm'],
    [L[1] - 0.2, 'act', 'shtorm', 'ARGUE', 'nika'], [L[1] + 1.8, 'act', 'shtorm', 'POINT', 'nika'],
    [L[1] + 0.5, 'act', 'nika', 'SHRUG', 'shtorm'],
    [L[2], 'walk', 'nika', 'race_start+2,-2'], [L[2] + 1.0, 'act', 'nika', 'LAUGH', 'shtorm'],
    [E - 1.0, 'turn', 'nika', 'race_start+-4,-6'], [E - 0.9, 'walk', 'nika', 'race_start+-3,-6'],
    [E - 0.6, 'act', 'shtorm', 'none'], [E - 0.5, 'walk', 'shtorm', 'race_start+6,1'],
    [E + 0.6, 'anim', 'shtorm', 'kick'], [E + 0.7, 'sfx', 'storm_car', 'minecraft:entity.zombie.attack_iron_door', 0.9, 1.2],
], music='radio.trassa128_1')

# a1m2b — Тейлор выходит из дома, трижды обходит машину, хлопает по капоту, жмёт руку и отсчитывает деньги.
direct('a1m2b', 0, [
    S('cam_suburb', 'suburb_street+10,8,3', 4, track='client'),
    F('client', 4, (4.5, 2.2, 2.2)),
    CU('client', 3),
    S('suburb_street+3,4,2', 'suburb_street+2,3,2', 3.5, track='client'),
], lambda L, E: [
    [0, 'car', 'tcar', 'suburb_street+2,-1', 1, 0],
    [0, 'spawn', 'client', 'suburb_house', 'citizen', 'Мистер Тейлор'], [0, 'door', 'suburb_house', 'open'],
    [0, 'spawn', 'wife', 'suburb_house+2,1', 'citizen', 'Миссис Тейлор'], [0.5, 'act', 'wife', 'WAVE', 'client'],
    [0.4, 'walk', 'client', 'suburb_street+4,2'], [2.6, 'door', 'suburb_house', 'close'],
    [3.6, 'walk', 'client', 'suburb_street+5,-2'], [5.2, 'walk', 'client', 'suburb_street+0,-3'],
    [6.6, 'walk', 'client', 'suburb_street+0,1'],
    [7.6, 'anim', 'client', 'kick'], [7.7, 'sfx', 'tcar', 'minecraft:entity.zombie.attack_wooden_door', 0.6, 1.4],
    [8.2, 'act', 'client', 'LAUGH', 'player'],
    [9.6, 'act', 'client', 'HANDSHAKE', 'player'], [11.0, 'act', 'client', 'TALK', 'player'],
    [11.1, 'hold', 'client', 'novograd:banknote_100'], [11.2, 'sfx', 'client', 'novograd:cash', 0.8],
    [11.5, 'act', 'wife', 'CLAP'],
], music='radio.klassika_1', vol=0.25, grade='film_cine')

# a1m3b — «Неон»: кран над танцполом; вышибала выталкивает пьяного; Роза идёт сквозь толпу с бокалами
# и протягивает один — «угощаю».
direct('a1m3b', 0, [
    S('cam_club', 'club_neon+6,14,6', 5),
    S('club_neon+-3,-2,1.4', 'club_neon+-2,-3,1.4', 3, track='drunk', roll=-8),
    OTS('rosa', 4, side=-1.0),
    CU('rosa', 4.5, side=0.7),
    S('club_neon+8,6,3', 'club_neon+12,9,5', 3, track='rosa'),
], lambda L, E: [
    [0, 'spawn', 'd1', 'club_neon+2,-3', 'citizen'], [0, 'spawn', 'd2', 'club_neon+-2,-4', 'citizen'], [0, 'spawn', 'd3', 'club_neon+0,-6', 'citizen'],
    [0, 'act', 'd1', 'DANCE'], [0, 'act', 'd2', 'DANCE2'], [0, 'act', 'd3', 'DANCE3'],
    [0, 'spawn', 'nika', 'club_neon+1,-1'], [0, 'act', 'nika', 'DANCE'],
    [0, 'spawn', 'bouncer', 'club_neon+3,-6', 'gang', 'Вышибала'], [0, 'spawn', 'drunk', 'club_neon+2,-5', 'citizen', 'Пьяный'],
    [0.3, 'act', 'drunk', 'ARGUE', 'bouncer'], [0.4, 'act', 'bouncer', 'POINT', 'drunk'],
    [5.2, 'anim', 'bouncer', 'kick'], [5.3, 'anim', 'drunk', 'hit'], [5.4, 'sfx', 'drunk', 'minecraft:entity.player.attack.strong', 1.0],
    [5.8, 'walk', 'drunk', 'club_neon+-6,-1'], [6.0, 'act', 'bouncer', 'SHRUG'],
    [0, 'spawn', 'rosa', 'club_neon+4,-8'], [6.8, 'walk', 'rosa', 'club_neon+1,0'], [6.9, 'hold', 'rosa', 'novograd:cola'],
    [L[1] + 0.2, 'act', 'nika', 'WAVE', 'rosa'],
    [L[1] + 1.3, 'act', 'rosa', 'TALK', 'player'], [L[1] + 1.4, 'hold', 'rosa', 'novograd:cola'],
    [E, 'act', 'rosa', 'LAUGH', 'player'], [E + 0.1, 'hold', 'rosa', 'novograd:cola'],
], music='radio.neon88_0', vol=0.38)

# a1m3c — Винс опирается на машину, наезжает, тычет пальцем, ржёт; хлопает дверью и уходит с пробуксовкой.
direct('a1m3c', 0, [
    S('hw_west+16,6,3', 'hw_west+8,-4,2', 3, track='vince'),
    LOW('vince', 3, side=-1.0),
    CU('vince', 2.5),
    S('hw_west+-4,3,1.5', 'hw_west+-3,4,1.5', 2.5, track='vince'),
    F('vince_car', 4, (8, 2.2, -2.5), shake=0.3),
], lambda L, E: [
    [0, 'car', 'vince_car', 'hw_west+3,0', 5, 180], [0, 'spawn', 'vince', 'hw_west+5,-2'], [0, 'act', 'vince', 'LEAN'],
    [0, 'spawn', 'girl', 'hw_west+6,1', 'citizen', 'Подружка Винса'], [0, 'act', 'girl', 'SMOKE'],
    [0.6, 'act', 'vince', 'ARGUE', 'player'], [2.2, 'walk', 'vince', 'hw_west+6,-4'],
    [3.6, 'act', 'vince', 'POINT', 'player'], [5.0, 'act', 'vince', 'LAUGH', 'player'], [5.2, 'act', 'girl', 'LAUGH', 'vince'],
    [E, 'walk', 'vince', 'hw_west+4,-1'], [E + 1.6, 'ride', 'vince', 'vince_car'],
    [E + 1.7, 'sfx', 'vince_car', 'minecraft:block.iron_door.close', 1.2, 0.8], [E + 2.2, 'sfx', 'vince_car', 'novograd:car_start', 1.6],
    [E + 2.6, 'smoke', 'hw_west+3,1'], [E + 2.7, 'drive', 'vince_car', ['hw_west+0,-60'], 0.9], [E + 3.0, 'act', 'girl', 'WAVE', 'vince_car'],
], music='radio.rock_volna_1')

# a1m3d — ночь у гаража: Винс крадётся, оглядывается, возится под машиной (лязг), лает собака — он замирает и удирает.
direct('a1m3d', 0, [
    S('garage_door+12,10,4', 'garage_door+8,6,3', 4, track='vince'),
    OTS('vince', 3.5),
    F('vince', 3, (-2.0, 0.7, 1.3), fov=55, fov2=48),
    S('garage_door+3,3,1.6', 'garage_door+2,2,1.5', 2, track='vince', roll=10),
    S('garage_door+6,-2,2', 'garage_door+8,-4,3', 3.5, track='vince'),
], lambda L, E: [
    [0, 'car', 'jake_car', 'garage_door', 1], [0, 'spawn', 'vince', 'garage_door+14,8'],
    [0.2, 'walk', 'vince', 'garage_door+6,4'], [3.6, 'turn', 'vince', 'garage_door+14,10'],
    [4.4, 'walk', 'vince', 'garage_door+1,1'], [7.0, 'act', 'vince', 'RUMMAGE'], [7.1, 'hold', 'vince', 'minecraft:shears'],
    [7.6, 'sfx', 'jake_car', 'minecraft:block.chain.hit', 1.2], [8.6, 'sfx', 'jake_car', 'minecraft:block.chain.break', 1.2],
    [9.6, 'sfx', 'garage_door+10,-6', 'minecraft:entity.wolf.growl', 2.0], [10.0, 'sfx', 'garage_door+10,-6', 'minecraft:entity.wolf.ambient', 2.0, 0.8],
    [9.9, 'act', 'vince', 'none'], [10.0, 'turn', 'vince', 'garage_door+10,-6'], [10.1, 'act', 'vince', 'UPSET'],
    [11.2, 'smoke', 'garage_door'], [11.4, 'run', 'vince', 'garage_door+30,-20'],
], music='radio.jazz_polnoch_1', grade='film_noir')

# a1m4 — облава: на старте вечеринка; сирены, толпа врассыпную; патрули влетают, копы выскакивают с оружием.
direct('a1m4', 0, [
    S('race_start+6,6,2', 'race_start+8,8,3', 3, track='nika'),
    S('race_start+30,-5,8', 'race_start+18,-4,5', 3.5, track='cop_car1'),
    CU('nika', 2, shake=0.4),
    CHASE('cop_car2', 2.5, 3),
    S('race_start+2,4,0.4', 'race_start+3,5,0.6', 3.5, track='petrov', fov=58, roll=-8, shake=0.3),
], lambda L, E: [
    [0, 'spawn', 'nika', 'race_start+2,-3'], [0, 'act', 'nika', 'DANCE'],
    [0, 'spawn', 'r1', 'race_start+-2,-1', 'citizen', 'Гонщик'], [0, 'act', 'r1', 'CHEER'],
    [0, 'spawn', 'r2', 'race_start+0,-6', 'citizen', 'Фанатка'], [0, 'act', 'r2', 'DANCE2'],
    [0, 'car', 'cop_car1', 'race_start+90,4', 6, 90], [0, 'car', 'cop_car2', 'race_start+110,-4', 6, 90],
    [1.6, 'sfx', 'race_start+20,0', 'novograd:siren', 3.0],
    [2.0, 'drive', 'cop_car1', ['race_start+10,3'], 0.95], [2.4, 'drive', 'cop_car2', ['race_start+14,-4'], 0.95],
    [2.6, 'act', 'nika', 'none'], [2.7, 'turn', 'nika', 'race_start+30,0'], [3.0, 'act', 'nika', 'UPSET'],
    [3.4, 'run', 'r1', 'race_start+-20,-10'], [3.6, 'run', 'r2', 'race_start+-18,8'],
    [6.2, 'run', 'nika', 'race_start+-8,-9'],
    [8.2, 'spawn', 'cop1', 'race_start+8,4', 'police'], [8.2, 'spawn', 'cop2', 'race_start+12,-2', 'police'],
    [8.2, 'spawn', 'petrov', 'race_start+9,1'], [8.3, 'sfx', 'cop1', 'minecraft:block.iron_door.close', 1.2, 0.8],
    [8.6, 'act', 'cop1', 'AIM', 'player'], [8.8, 'act', 'cop2', 'AIM', 'player'], [8.9, 'sfx', 'cop2', 'novograd:gun_slide_back', 1.2],
    [9.2, 'act', 'petrov', 'POINT', 'nika'], [10.5, 'walk', 'petrov', 'race_start+5,0'],
], music='radio.trassa128_2')

# =====================================================================  АКТ II
# a2m1 — двор Ящеров: Джейк под прицелом; Брик выходит, берёт у своего биту, смеётся, пинает Джейка, нависает.
direct('a2m1', 0, [
    S('cam_ind_a', 'gang_hq+14,12,10', 4.5),
    OTS('kirpich', 3.5),
    CU('gleb', 2.5, side=-0.6),
    S('gang_hq+-2,1,0.4', 'gang_hq+-1.5,1.5,0.5', 3.5, track='kirpich', fov=58, roll=-7),
    S('gang_hq+4,8,1.5', 'gang_hq+3,9,1.4', 2, track='gleb', shake=0.6),
    CU('gleb', 2.5, side=0.7, roll=5),
], lambda L, E: [
    [0, 'spawn', 'gleb', 'gang_hq+0,5'], [0, 'act', 'gleb', 'UPSET'], [0, 'spawn', 'kirpich', 'gang_hq+9,0'],
    [0, 'spawn', 'g1', 'gang_hq+-3,7', 'gang'], [0, 'spawn', 'g2', 'gang_hq+3,7', 'gang'],
    [0.5, 'act', 'g1', 'AIM', 'gleb'], [0.5, 'act', 'g2', 'AIM', 'gleb'],
    [0, 'spawn', 'g3', 'gang_hq+3,2', 'gang'], [0, 'act', 'g3', 'SMOKE'],
    [2.0, 'sfx', 'kirpich', 'minecraft:block.iron_door.close', 1.3, 0.7], [2.6, 'walk', 'kirpich', 'gang_hq+3,3'],
    [5.0, 'give', 'g3', 'kirpich', 'novograd:bat'], [5.6, 'walk', 'kirpich', 'gang_hq+1,4'],
    [L[1], 'act', 'kirpich', 'LAUGH', 'gleb'], [L[1] + 0.1, 'hold', 'kirpich', 'novograd:bat'],
    [L[1] + 3.0, 'anim', 'kirpich', 'kick'], [L[1] + 3.2, 'anim', 'gleb', 'hit'],
    [L[1] + 3.3, 'sfx', 'gleb', 'minecraft:entity.player.attack.strong', 1.4],
    [L[1] + 3.6, 'act', 'kirpich', 'ARGUE', 'gleb'], [L[1] + 3.7, 'hold', 'kirpich', 'novograd:bat'], [L[1] + 3.8, 'act', 'g3', 'LAUGH', 'gleb'],
], music='radio.tekhno_podval_1', vol=0.3)

# a2m2b — после перестрелки: дым, горящая тачка, на земле «Синий»; Марко спокойно закуривает, смотрит в камеру и уезжает.
direct('a2m2b', 0, [
    S('cam_chop', 'chop_shop+10,10,4', 3, track='marco'),
    LOW('marco', 3, side=-1.0),
    S('chop_shop+-2,-3,1.6', 'chop_shop+-1,-4,1.6', 2.5, track='marco'),
    CHASE('marco_car', 4.5, 3, shake=0.4),
], lambda L, E: [
    [0, 'smoke', 'chop_shop+-3,2'], [0, 'boom', 'chop_shop+-8,6'], [3.0, 'smoke', 'chop_shop+-8,6'],
    [0, 'spawn', 'body', 'chop_shop+-1,3', 'gang', 'Синий'], [0.1, 'act', 'body', 'SLEEP'],
    [0, 'car', 'marco_car', 'chop_shop+6,4', 4, 0], [0, 'spawn', 'marco', 'chop_shop+2,-1'],
    [0.3, 'walk', 'marco', 'chop_shop+4,1'], [2.6, 'act', 'marco', 'SMOKE'],
    [4.4, 'face', 'marco', 'player'], [5.6, 'act', 'marco', 'LAUGH', 'player'],
    [6.6, 'walk', 'marco', 'chop_shop+5,3'], [7.6, 'ride', 'marco', 'marco_car'],
    [7.7, 'sfx', 'marco_car', 'minecraft:block.iron_door.close', 1.2, 0.8], [7.9, 'sfx', 'marco_car', 'novograd:car_start', 1.5],
    [8.1, 'drive', 'marco_car', ['chop_shop+6,60', 'hw_south'], 0.95], [8.2, 'smoke', 'chop_shop+6,4'],
], music='radio.rock_volna_2')

# a2m3c (1) — пляж на закате: Марко опирается на свою машину, охрана идёт «обыскать» гостя; Марко подходит и протягивает руку.
direct('a2m3c', 0, [
    S('cam_beach', 'beach_lot+12,6,4', 3.5, track='marco'),
    OTS('b1', 2.5),
    F('marco', 3, (-2.6, 1.6, 0.8), fov=55, fov2=50),
    S('beach_lot+6,3,2', 'beach_lot+5,2,2', 3, track='marco'),
], lambda L, E: [
    [0, 'car', 'mcar', 'beach_lot+10,-8', 4, 0],
    [0, 'spawn', 'marco', 'beach_lot+8,-5'], [0, 'act', 'marco', 'LEAN'],
    [0, 'spawn', 'b1', 'beach_lot+10,-4', 'gang', 'Синий'], [0, 'spawn', 'b2', 'beach_lot+12,-4', 'gang', 'Синий'],
    [0.4, 'act', 'marco', 'TALK', 'player'], [0.5, 'walk', 'b1', 'beach_lot+2,2'], [0.7, 'act', 'b2', 'SMOKE'],
    [3.4, 'act', 'b1', 'POINT', 'player'], [4.6, 'act', 'b1', 'SHRUG', 'marco'],
    [5.2, 'walk', 'marco', 'beach_lot+3,0'], [6.0, 'walk', 'b1', 'beach_lot+7,-3'],
    [8.3, 'act', 'marco', 'HANDSHAKE', 'player'],
], music='radio.jazz_polnoch_0', vol=0.28)

# a2m3c (2) — засада: визг шин, машина Ящеров, перестрелка, Марко бежит к машине и уходит;
# из машины выходит сам Брик — «Говорил с Марко?»
direct('a2m3c', 1, [
    S('beach_lot+12,8,3', 'beach_lot+9,5,2', 2.5, track='mc'),
    S('beach_lot+16,8,3', 'beach_lot+10,4,2', 2.5, track='l_car', shake=0.5),
    S('beach_lot+-4,6,2', 'beach_lot+-3,4,2', 2.5, track='b1', shake=0.8, roll=10),
    CHASE('marco_car', 2.5, 2, shake=0.5),
    F('brik', 3, (-3.4, 1.5, 1.1), fov=60),
    CU('brik', 3),
], lambda L, E: [
    [0, 'spawn', 'mc', 'beach_lot+3,0', 'c33', 'Марко «Синий»'], [0, 'act', 'mc', 'TALK', 'player'],
    [0, 'spawn', 'b1', 'beach_lot+6,-4', 'gang', 'Синий'], [0, 'spawn', 'b2', 'beach_lot+8,2', 'gang', 'Синий'],
    [0, 'car', 'marco_car', 'beach_lot+10,-8', 4, 0],
    [0, 'car', 'l_car', 'beach_lot+0,70', 3, 180], [0, 'spawn', 'brik', 'beach_lot+0,70', 'c39', 'Брик'], [0.05, 'ride', 'brik', 'l_car'],
    [0.6, 'drive', 'l_car', ['beach_lot+0,12'], 0.95], [1.2, 'sfx', 'l_car', 'novograd:car_horn', 2.5],
    [1.6, 'act', 'mc', 'none'], [1.7, 'turn', 'mc', 'beach_lot+0,30'], [1.8, 'act', 'b1', 'AIM', 'l_car'],
    [3.4, 'spawn', 'l1', 'beach_lot+-2,10', 'gang', 'Ящер'], [3.4, 'spawn', 'l2', 'beach_lot+3,11', 'gang', 'Ящер'],
    [3.8, 'shoot', 'l1', 'b1', 5, 0], [4.2, 'shoot', 'l2', 'b2', 5, 0], [4.0, 'shoot', 'b1', 'l1', 3, 0], [4.6, 'glass', 'l_car'],
    [4.3, 'run', 'mc', 'beach_lot+9,-6'], [5.8, 'die', 'b1'], [6.6, 'die', 'b2'],
    [6.4, 'ride', 'mc', 'marco_car'], [6.6, 'sfx', 'marco_car', 'novograd:car_start', 1.6],
    [6.8, 'drive', 'marco_car', ['beach_lot+10,-90'], 0.95], [7.0, 'smoke', 'beach_lot+10,-8'],
    [L[1] - 0.3, 'exit', 'brik'], [L[1] - 0.2, 'sfx', 'l_car', 'minecraft:block.iron_door.close', 1.3, 0.8],
    [L[1], 'walk', 'brik', 'beach_lot+2,4'], [L[1] + 2.6, 'act', 'brik', 'ARGUE', 'player'],
    [L[1] + 3.0, 'anim', 'l1', 'kick'], [L[1] + 3.6, 'act', 'l2', 'LAUGH', 'brik'],
    [E - 0.6, 'act', 'brik', 'LAUGH', 'player'],
], music='radio.trassa128_3', grade='film_sunset')

# a2m4 — инкассаторы грузят мешки, захлопывают двери, хлопок по крыше — и фургон уходит; через улицу Ящер звонит Брику.
direct('a2m4', 0, [
    S('cam_casino', 'casino_dt+14,12,5', 3, track='van'),
    F('gd1', 2.5, (-2.6, 1.3, 1.0), fov=58),
    OTS('sp', 3),
    F('van', 5, (10, 3, -3)),
], lambda L, E: [
    [0, 'car', 'van', 'casino_dt', 4, 0],
    [0, 'spawn', 'gd1', 'casino_dt+-3,-4', 'police', 'Инкассатор'], [0, 'spawn', 'gd2', 'casino_dt+-2,2', 'police', 'Инкассатор'],
    [0.2, 'walk', 'gd1', 'casino_dt+-1.5,-1'], [0.3, 'act', 'gd1', 'CARRY'], [0.4, 'act', 'gd2', 'AIM', 'casino_dt+-8,4'],
    [3.0, 'act', 'gd1', 'none'], [3.2, 'sfx', 'van', 'minecraft:block.iron_door.close', 1.6, 0.7],
    [3.8, 'anim', 'gd1', 'highfive'], [3.9, 'sfx', 'van', 'minecraft:entity.zombie.attack_iron_door', 0.6, 1.3],
    [0, 'spawn', 'sp', 'casino_dt+10,8', 'gang', 'Ящер'], [0.1, 'act', 'sp', 'PHONE'], [0.2, 'turn', 'sp', 'casino_dt'],
    [5.0, 'drive', 'van', ['casino_dt+0,60', 'dt_3'], 0.7], [5.5, 'sfx', 'van', 'novograd:car_start', 1.4],
    [6.0, 'walk', 'sp', 'casino_dt+8,14'],
], music='radio.jazz_polnoch_1')

# =====================================================================  АКТ III
# a3m1 — участок ночью: Харрис с кофе швыряет Питерсону папку и уходит; Питерсон смотрит на камеру задержанного.
direct('a3m1', 0, [
    S('police_station+8,8,3', 'police_station+5,5,2', 3.5, track='petrov'),
    F('harris', 3, (-2.6, 1.6, 0.8), fov=58),
    S('police_station+4,2,1.6', 'police_station+3.5,2.5,1.6', 3, track='harris'),
    OTS('harris', 2.5),
    CU('petrov', 3.5),
], lambda L, E: [
    [0, 'spawn', 'petrov', 'police_station+10,6'], [0.2, 'walk', 'petrov', 'police_station+1,1'],
    [0, 'spawn', 'harris', 'police_station+-4,2'], [0.1, 'hold', 'harris', 'novograd:cola'],
    [0, 'spawn', 'cop1', 'police_station+-6,-2', 'police'], [0, 'act', 'cop1', 'TYPE'],
    [0, 'spawn', 'cop2', 'police_station+4,-3', 'police'], [0, 'act', 'cop2', 'PHONE'],
    [3.2, 'walk', 'harris', 'police_station+-1,1.5'], [3.3, 'hold', 'harris', 'novograd:cola'],
    [L[1], 'act', 'harris', 'ARGUE', 'petrov'], [L[1] + 0.1, 'hold', 'harris', 'minecraft:paper'],
    [L[1] + 2.2, 'give', 'harris', 'petrov', 'minecraft:paper'], [L[1] + 2.3, 'sfx', 'petrov', 'minecraft:item.book.put', 1.2],
    [L[1] + 3.2, 'walk', 'harris', 'police_station+-9,2'], [L[1] + 3.4, 'act', 'cop2', 'SHRUG', 'harris'],
    [L[2] - 0.6, 'turn', 'petrov', 'police_station+-6,-4'], [L[2], 'act', 'petrov', 'TALK', 'player'],
    [L[2] + 0.1, 'hold', 'petrov', 'minecraft:paper'],
], music='radio.nochnoy_port_0', vol=0.28, grade='film_noir')

# a3m2b — переулок: Харрис курит, курьер оглядывается, рукопожатие, конверт; Харрис пересчитывает и смеётся —
# вспышка фотоаппарата! Харрис резко оборачивается, курьер бежит.
direct('a3m2b', 0, [
    S('dt_alley+16,7,3', 'dt_alley+13,5,2.5', 3.5, track='harris', fov=34, fov2=30),
    OTS('courier', 3),
    S('dt_alley+4,-5,2', 'dt_alley+3,-4,1.8', 3, track='courier'),
    CU('harris', 3),
    S('dt_alley+2,-3,0.4', 'dt_alley+2.2,-3.2,0.4', 3, track='harris', fov=60, fov2=36, roll=-9),
], lambda L, E: [
    [0, 'spawn', 'harris', 'dt_alley+-6,-2'], [0, 'act', 'harris', 'SMOKE'],
    [0, 'spawn', 'courier', 'dt_alley+-14,0', 'gang', 'Курьер Ящеров'], [0.3, 'walk', 'courier', 'dt_alley+-9,-1'],
    [2.8, 'turn', 'courier', 'dt_alley+4,-3'], [3.5, 'walk', 'courier', 'dt_alley+-7,-1'],
    [5.0, 'act', 'courier', 'HANDSHAKE', 'harris'], [5.0, 'act', 'harris', 'HANDSHAKE', 'courier'],
    [6.4, 'give', 'courier', 'harris', 'novograd:banknote_100'],
    [L[1], 'act', 'harris', 'LAUGH', 'courier'], [L[1] + 0.1, 'hold', 'harris', 'novograd:banknote_100'],
    [L[1] + 0.2, 'sfx', 'harris', 'novograd:cash', 0.9],
    [E - 1.0, 'flash', 'dt_alley+2,-3'], [E - 0.95, 'sfx', 'dt_alley+2,-3', 'minecraft:block.lever.click', 1.6, 1.6],
    [E - 0.6, 'act', 'harris', 'POINT', 'player'], [E - 0.5, 'act', 'courier', 'UPSET'],
    [E + 0.4, 'run', 'courier', 'dt_alley+-24,0'], [E + 1.4, 'walk', 'harris', 'dt_alley+-2,-2'],
], music='radio.jazz_polnoch_1', grade='film_noir')

# a3m3b — похищение: Лена выходит с телефоном, рядом тормозит машина, двое бегут к ней, телефон летит на асфальт,
# прохожий удирает, драка, её заталкивают в машину — и с пробуксовкой прочь.
direct('a3m3b', 0, [
    S('cam_club', 'club_neon+8,10,4', 2.5, track='lena'),
    F('k1', 2.5, (3, 1.8, 1.0), shake=0.4),
    CU('lena', 2.5, shake=0.4),
    S('club_neon+4,4,2', 'club_neon+3,3,2', 2.5, track='lena', shake=0.6, roll=-9),
    CHASE('kid_car', 4.5, 2.5, shake=0.4),
], lambda L, E: [
    [0, 'spawn', 'lena', 'club_neon+0,2'], [0, 'act', 'lena', 'PHONE'],
    [0, 'spawn', 'by', 'club_neon+-3,3', 'citizen', 'Прохожий'], [0, 'act', 'by', 'BAGS'],
    [0, 'car', 'kid_car', 'club_neon+8,6', 3, 90], [0.8, 'sfx', 'kid_car', 'novograd:car_start', 1.8],
    [1.0, 'spawn', 'k1', 'club_neon+10,4', 'gang', 'Ящер'], [1.0, 'spawn', 'k2', 'club_neon+10,8', 'gang', 'Ящер'],
    [1.0, 'sfx', 'kid_car', 'minecraft:block.iron_door.open', 1.2, 0.8],
    [1.2, 'run', 'k1', 'club_neon+1,2'], [1.4, 'run', 'k2', 'club_neon+0,3'],
    [2.6, 'turn', 'lena', 'club_neon+8,6'], [3.0, 'act', 'lena', 'UPSET'], [3.1, 'sfx', 'lena', 'minecraft:entity.item.break', 1.0],
    [3.4, 'act', 'by', 'UPSET'], [3.9, 'run', 'by', 'club_neon+-16,6'],
    [4.4, 'fight', 'k1', 'lena', 30], [4.6, 'anim', 'k2', 'drag'], [5.2, 'anim', 'lena', 'hit'],
    [L[1] + 0.9, 'ride', 'k2', 'kid_car'], [L[1] + 1.0, 'ride', 'lena', 'kid_car'], [L[1] + 1.1, 'ride', 'k1', 'kid_car'],
    [L[1] + 1.2, 'sfx', 'kid_car', 'minecraft:block.iron_door.close', 1.6, 0.7],
    [L[1] + 1.6, 'drive', 'kid_car', ['dt_1', 'dt_2'], 0.95], [L[1] + 1.8, 'smoke', 'club_neon+8,6'],
], music='radio.trassa128_0')

# a3m3c — арест: Харрис тянется к кобуре — два ствола в лицо; руки вверх; наручники; «Не в этот раз» — и его уводят.
direct('a3m3c', 0, [
    S('police_station+10,6,3', 'police_station+7,4,2', 3, track='harris'),
    CU('harris', 1.8, shake=0.3),
    S('police_station+-1,-2,1.6', 'police_station+-1.5,-1.5,1.6', 2.2, track='harris', roll=8),
    S('police_station+3,-3,2', 'police_station+2,-2,1.8', 3, track='harris'),
    CU('petrov', 2.5),
    F('harris', 3, (3.4, 2.0, 1.3)),
], lambda L, E: [
    [0, 'spawn', 'harris', 'police_station+-6,4'], [0, 'act', 'harris', 'PHONE'], [0, 'spawn', 'petrov', 'police_station+3,2'],
    [0, 'spawn', 'c1', 'police_station+6,-3', 'police'], [0, 'spawn', 'c2', 'police_station+7,3', 'police'],
    [0.5, 'walk', 'petrov', 'police_station+-3,3'], [0.8, 'walk', 'c1', 'police_station+-4,1'], [1.0, 'walk', 'c2', 'police_station+-4,6'],
    [2.4, 'act', 'harris', 'none'], [2.5, 'face', 'harris', 'petrov'], [3.0, 'hold', 'harris', 'novograd:pistol'],
    [3.3, 'act', 'c1', 'AIM', 'harris'], [3.4, 'act', 'c2', 'AIM', 'harris'], [3.5, 'sfx', 'c1', 'novograd:gun_slide_back', 1.4],
    [4.3, 'act', 'harris', 'SHRUG'],
    [L[1], 'act', 'harris', 'ARGUE', 'petrov'], [L[1] + 1.4, 'walk', 'c1', 'police_station+-5,3'],
    [L[1] + 2.6, 'anim', 'c1', 'drag'], [L[1] + 2.7, 'sfx', 'harris', 'minecraft:block.chain.place', 1.5],
    [L[2] - 0.3, 'act', 'petrov', 'POINT', 'harris'], [L[2] + 0.2, 'act', 'harris', 'UPSET'],
    [E + 0.2, 'walk', 'harris', 'police_station+10,6'], [E + 0.3, 'walk', 'c1', 'police_station+9,5'], [E + 0.6, 'act', 'c2', 'none'],
], music='radio.tekhno_podval_2', vol=0.28, grade='film_noir')

# a3m4 финал — Брик рвётся из рук копов, удар, его запихивают в машину; гараж: Джейк чинит, Ники смеётся;
# машина с Бриком уходит по полосе; облёт острова.
direct('a3m4', 0, [
    S('airfield_hangar+14,8,3', 'airfield_hangar+9,5,2', 2.5, track='kirpich'),
    CU('kirpich', 2, shake=0.5),
    S('garage_door+10,6,3', 'garage_door+7,4,2', 6, track='gl'),
    CHASE('cop_car', 3.5, 2.5, shake=0.2),
    S('cam_air_a', 'cam_island_far', 8),
    S('cam_dt_a', 'cam_dt_b', 6),
], lambda L, E: [
    [0, 'spawn', 'kirpich', 'airfield_hangar+2,4'], [0, 'act', 'kirpich', 'UPSET'],
    [0, 'spawn', 'c1', 'airfield_hangar+4,3', 'police'], [0, 'spawn', 'c2', 'airfield_hangar+0,3', 'police'],
    [0, 'car', 'cop_car', 'airfield_hangar+6,12', 6, 0], [0.4, 'act', 'c2', 'AIM', 'kirpich'],
    [1.0, 'fight', 'kirpich', 'c1', 30], [2.4, 'anim', 'c1', 'kick'], [2.5, 'anim', 'kirpich', 'hit'],
    [2.6, 'sfx', 'kirpich', 'minecraft:entity.player.attack.strong', 1.4],
    [3.0, 'walk', 'kirpich', 'airfield_hangar+5,11'], [3.1, 'walk', 'c1', 'airfield_hangar+7,10'],
    [5.2, 'ride', 'kirpich', 'cop_car'], [5.4, 'sfx', 'cop_car', 'minecraft:block.iron_door.close', 1.4, 0.8],
    [5.8, 'drive', 'cop_car', ['airfield_hangar+6,60', 'airfield'], 0.6], [6.0, 'sfx', 'cop_car', 'novograd:siren', 2.0],
    [0, 'car', 'gcar', 'garage_door', 1], [0, 'spawn', 'gl', 'garage_door+2,1', 'c25', 'Джейк'], [0, 'act', 'gl', 'HAMMER'],
    [0, 'spawn', 'nk', 'garage_door+4,2', 'c20', 'Ники «Нитро»'], [0, 'act', 'nk', 'LEAN'],
    [6.0, 'sfx', 'gcar', 'minecraft:block.anvil.use', 0.6, 1.4], [7.5, 'act', 'nk', 'LAUGH', 'gl'],
    [8.6, 'act', 'gl', 'LAUGH', 'nk'], [10.0, 'act', 'gl', 'HAMMER'],
], music='radio.klassika_3', vol=0.3, grade='film_sunset')

# =====================================================================  АКТ IV
# a4m1 (1) — мост: машины идут в обе стороны; Стрип; у «Золотой короны» богач швыряет деньги, швейцар подбирает.
direct('a4m1', 0, [
    S('cam_bridge', 'bridge_n+30,0,12', 5.5),
    S('bridge_n+-20,6,3', 'bridge_n+-40,8,4', 3.5, track='tc1'),
    S('cam_riv_far', 'cam_riv_strip', 5.5),
    S('riv_casino+12,8,4', 'riv_casino+6,5,2.5', 4, track='vip'),
], lambda L, E: [
    [0, 'car', 'tc1', 'bridge_n+40,2', 2, 90], [0.2, 'drive', 'tc1', ['bridge_n+-160,2'], 0.8],
    [0, 'car', 'tc2', 'bridge_n+-70,-2', 5, 270], [0.4, 'drive', 'tc2', ['bridge_n+120,-2'], 0.7],
    [0, 'spawn', 'vip', 'riv_casino+3,3', 'citizen', 'Игрок'], [0, 'act', 'vip', 'CHEER'],
    [0, 'spawn', 'gf', 'riv_casino+4,4', 'citizen', 'Спутница'], [0, 'act', 'gf', 'DANCE'],
    [0, 'spawn', 'valet', 'riv_casino+0,1', 'citizen', 'Швейцар'],
    [14.6, 'give', 'vip', 'valet', 'novograd:banknote_100'], [14.8, 'sfx', 'valet', 'novograd:cash', 1.0],
    [15.6, 'act', 'valet', 'CLAP'], [16.0, 'act', 'vip', 'LAUGH', 'valet'],
], music='radio.palma_fm_1', vol=0.3)

# a4m1 (2) — Ники в «Молнии»: рёв мотора, дым из-под колёс, рывок по мосту.
direct('a4m1', 1, [
    HOOD('nika_car', 2.5),
    CHASE('nika_car', 4.5, 3),
    S('bridge_n+-60,10,4', 'bridge_n+-90,8,3', 4, track='nika_car'),
], lambda L, E: [
    [0, 'car', 'nika_car', 'bridge_n+-6,2', 2, 90], [0, 'spawn', 'nk', 'bridge_n+-6,2', 'c20', 'Ники «Нитро»'], [0.05, 'ride', 'nk', 'nika_car'],
    [0.4, 'sfx', 'nika_car', 'novograd:car_start', 1.8], [1.2, 'smoke', 'bridge_n+-5,2'], [1.4, 'sfx', 'nika_car', 'novograd:car_horn', 1.5],
    [1.8, 'drive', 'nika_car', ['bridge_n+-200,2', 'riv_ring_e+0,-444'], 0.95],
], music='radio.trassa128_1')

# a4m2 — Кроу с охраной идёт ко входу, суёт швейцару купюру; игрок за столом Софи вскакивает и уходит;
# Кроу отчитывает её, хлопает ладонью — она вздрагивает; уходит, смеясь; Софи остаётся одна.
direct('a4m2', 0, [
    S('riv_casino+-6,10,4', 'riv_casino+-4,4,2.5', 3.5, track='crow'),
    LOW('crow', 3, side=-1.2),
    S('riv_casino+3,4,2', 'riv_casino+2,3,1.8', 3, track='sofi'),
    S('riv_casino+-2,1,1.6', 'riv_casino+-1.6,1.5,1.6', 3, track='sofi', fov=48, roll=6),
    OTS('crow', 2.5),
    S('riv_casino+-3,6,1.6', 'riv_casino+-2.5,5.5,1.6', 3.5, track='sofi', fov=46),
], lambda L, E: [
    [0, 'spawn', 'crow', 'riv_casino+1,-12'], [0, 'spawn', 'g1', 'riv_casino+3,-13', 'gang', 'Охрана Кроу'], [0, 'spawn', 'g2', 'riv_casino+0,-14', 'gang', 'Охрана Кроу'],
    [0, 'spawn', 'sofi', 'riv_casino+0,4'], [0, 'spawn', 'gm', 'riv_casino+0,6', 'citizen', 'Игрок'], [0.2, 'act', 'sofi', 'TALK', 'gm'], [0.2, 'act', 'gm', 'LAUGH', 'sofi'],
    [0, 'spawn', 'valet', 'riv_casino+3,-4', 'citizen', 'Швейцар'],
    [0.3, 'walk', 'crow', 'riv_casino+1,-5'], [0.5, 'walk', 'g1', 'riv_casino+3,-6'], [0.5, 'walk', 'g2', 'riv_casino+0,-7'],
    [3.0, 'give', 'crow', 'valet', 'novograd:banknote_100'], [3.6, 'walk', 'crow', 'riv_casino+1,1'], [3.8, 'act', 'valet', 'WAVE', 'crow'],
    [4.0, 'walk', 'g1', 'riv_casino+3,-1'], [4.0, 'walk', 'g2', 'riv_casino+-1,-1'],
    [5.0, 'act', 'gm', 'UPSET'], [5.4, 'walk', 'gm', 'riv_casino+-6,9'],
    [L[1], 'act', 'crow', 'ARGUE', 'sofi'], [L[1], 'act', 'sofi', 'UPSET'],
    [L[1] + 2.6, 'anim', 'crow', 'highfive'], [L[1] + 2.8, 'anim', 'sofi', 'hit'],
    [L[1] + 2.9, 'sfx', 'sofi', 'minecraft:entity.zombie.attack_wooden_door', 0.9, 1.3],
    [L[1] + 5.0, 'act', 'crow', 'LAUGH', 'sofi'], [L[1] + 5.1, 'act', 'g1', 'SMOKE'],
    [L[2] + 0.6, 'walk', 'crow', 'riv_casino+1,-18'], [L[2] + 0.9, 'walk', 'g1', 'riv_casino+3,-17'], [L[2] + 1.0, 'walk', 'g2', 'riv_casino+0,-19'],
    [E + 0.4, 'act', 'sofi', 'THINK'],
], music='radio.jazz_polnoch_1')

# a4m3 — Стрип перед гонкой: толпа, флаг; Шакал подходит, тычет пальцем, ржёт, садится в машину и газует.
direct('a4m3', 0, [
    S('riv_strip+16,0,4', 'riv_strip+8,0,3', 3, track='shakal'),
    F('shakal', 3, (-2.6, 1.6, 0.8), fov=58),
    LOW('shakal', 2),
    S('riv_strip+3,10,1.6', 'riv_strip+2,11,1.6', 2, track='fl'),
    HOOD('sh_car', 3),
], lambda L, E: [
    [0, 'spawn', 'shakal', 'riv_strip+10,6'], [0, 'car', 'sh_car', 'riv_strip+4,3', 3, 270],
    [0, 'spawn', 'fl', 'riv_strip+0,8', 'citizen', 'Стартёрша'], [0, 'act', 'fl', 'CHEER'],
    [0, 'spawn', 'cr1', 'riv_strip+-2,9', 'citizen'], [0, 'act', 'cr1', 'DANCE2'],
    [0, 'spawn', 'cr2', 'riv_strip+2,10', 'citizen'], [0, 'act', 'cr2', 'CLAP'],
    [0.2, 'walk', 'shakal', 'riv_strip+5,5'], [2.6, 'act', 'shakal', 'POINT', 'player'], [4.2, 'act', 'shakal', 'LAUGH', 'player'],
    [E - 0.2, 'walk', 'shakal', 'riv_strip+4,4'], [E + 1.2, 'ride', 'shakal', 'sh_car'],
    [E + 1.3, 'sfx', 'sh_car', 'minecraft:block.iron_door.close', 1.2, 0.8], [E + 1.8, 'sfx', 'sh_car', 'novograd:car_start', 1.8],
    [E + 2.2, 'smoke', 'riv_strip+4,3'], [E + 2.4, 'act', 'fl', 'WAVE'], [E + 2.6, 'sfx', 'sh_car', 'novograd:car_horn', 1.4],
], music='radio.rock_volna_0')

# a4m9 — служебный вход: охранник курит; Софи заговаривает, суёт ему колу — он уходит; она открывает дверь и машет.
direct('a4m9', 0, [
    S('riv_casino+-5,8,3', 'riv_casino+-3,5,2', 3, track='sofi'),
    F('gd', 2.5, (-2.4, 1.6, 0.7), fov=55),
    OTS('sofi', 2.5, side=-1.2),
    CU('sofi', 3),
], lambda L, E: [
    [0, 'spawn', 'sofi', 'riv_casino+1,3'], [0, 'spawn', 'gd', 'riv_casino+3,-3', 'gang', 'Охрана Кроу'], [0, 'act', 'gd', 'SMOKE'],
    [0.4, 'walk', 'sofi', 'riv_casino+2,-1'], [2.2, 'act', 'sofi', 'LAUGH', 'gd'], [2.3, 'act', 'gd', 'LAUGH', 'sofi'],
    [3.0, 'give', 'sofi', 'gd', 'novograd:cola'], [4.0, 'walk', 'gd', 'riv_casino+-6,-12'],
    [5.6, 'door', 'riv_casino+3,-4', 'open'], [6.0, 'act', 'sofi', 'WAVE', 'player'], [7.0, 'smoke', 'riv_casino+3,-4'],
    [9.0, 'walk', 'sofi', 'riv_casino+3,-5'],
], music='radio.jazz_polnoch_1')

# a4m10 — доки: Кроу держит Софи под стволом, охранник целится; монолог; Софи вырывается и кричит «Беги!»,
# очередь под ноги игроку, её запихивают в машину.
direct('a4m10', 0, [
    S('riv_docks+14,8,3', 'riv_docks+8,5,2', 4, track='crow'),
    LOW('crow', 3, side=-1.0),
    S('riv_docks+3,3,2', 'riv_docks+2,2,1.8', 4, track='sofi', shake=0.4),
    CU('sofi', 3, roll=-6, shake=0.3),
    S('riv_docks+6,-1,1.2', 'riv_docks+5,0,1.2', 2.5, track='g1', shake=0.8, roll=9),
    CHASE('crow_car', 4, 2.5, shake=0.4),
], lambda L, E: [
    [0, 'spawn', 'crow', 'riv_docks+10,-6'], [0, 'spawn', 'sofi', 'riv_docks+9,-5'], [0, 'act', 'sofi', 'UPSET'],
    [0, 'spawn', 'g1', 'riv_docks+12,-3', 'gang', 'Охрана Кроу'], [0.3, 'act', 'g1', 'AIM', 'player'],
    [0, 'car', 'crow_car', 'riv_docks+14,-8', 3, 0], [0.4, 'act', 'crow', 'AIM', 'player'],
    [2.2, 'anim', 'crow', 'drag'], [2.4, 'anim', 'sofi', 'hit'],
    [L[1], 'act', 'crow', 'ARGUE', 'player'], [L[1] + 0.1, 'hold', 'crow', 'novograd:pistol'],
    [L[1] + 3.0, 'walk', 'crow', 'riv_docks+11,-7'],
    [L[2], 'fight', 'sofi', 'crow', 24], [L[2] + 0.3, 'anim', 'sofi', 'kick'],
    [L[2] + 1.2, 'shoot', 'g1', 'riv_docks+3,3', 4, 0],
    [E + 0.3, 'ride', 'crow', 'crow_car'], [E + 0.4, 'ride', 'sofi', 'crow_car'], [E + 0.6, 'ride', 'g1', 'crow_car'],
    [E + 0.7, 'sfx', 'crow_car', 'minecraft:block.iron_door.close', 1.6, 0.7],
    [E + 1.0, 'drive', 'crow_car', ['riv_ring_e+0,-200', 'bridge_n'], 0.95], [E + 1.2, 'smoke', 'riv_docks+14,-8'],
], music='radio.tekhno_podval_1')

# =====================================================================  АКТ V
# a5m1 — горы: облёт; у седана Кроу звонит, охранник курит; далёкая сирена — Кроу бросает телефон и прыгает в машину.
direct('a5m1', 0, [
    S('cam_mtn', 'mtn_road+20,0,30', 6),
    S('mtn_road+10,-34,3', 'mtn_road+7,-37,2', 3, track='cr'),
    CU('cr', 2.5),
    S('mtn_road+12,-30,4', 'mtn_road+16,-26,6', 3.5, track='crow_car'),
], lambda L, E: [
    [0, 'car', 'crow_car', 'mtn_road+0,-40', 3, 180],
    [0, 'spawn', 'cr', 'mtn_road+2,-38', 'c37', 'Виктор Кроу'], [0, 'act', 'cr', 'PHONE'],
    [0, 'spawn', 'gd', 'mtn_road+-2,-37', 'gang', 'Охрана Кроу'], [0, 'act', 'gd', 'SMOKE'],
    [8.6, 'sfx', 'mtn_road+0,-10', 'novograd:siren', 3.0], [8.8, 'act', 'cr', 'none'], [8.9, 'turn', 'cr', 'mtn_road+0,0'],
    [9.4, 'act', 'cr', 'UPSET'], [9.6, 'act', 'gd', 'AIM', 'mtn_road+0,0'],
    [11.0, 'walk', 'cr', 'mtn_road+1,-40'], [12.0, 'ride', 'cr', 'crow_car'], [12.2, 'ride', 'gd', 'crow_car'],
    [12.4, 'sfx', 'crow_car', 'minecraft:block.iron_door.close', 1.4, 0.8],
], music='radio.klassika_0', vol=0.3)

# a5m2 — блокпост: копы целятся, седан Кроу прорывается сквозь огонь, звон стекла — и вниз по серпантину.
direct('a5m2', 0, [
    S('mtn_road+6,-46,0.3', 'mtn_road+5,-48,0.5', 2.5, track='crow_car', fov=60),
    S('mtn_road+-8,-52,2', 'mtn_road+-7,-50,2', 2.5, track='cp1', shake=0.5, roll=-8),
    CHASE('crow_car', 4.5, 3, shake=0.4),
    SIDE('crow_car', 3),
], lambda L, E: [
    [0, 'spawn', 'cp1', 'mtn_road+5,-50', 'police'], [0, 'spawn', 'cp2', 'mtn_road+-5,-52', 'police'],
    [0.3, 'act', 'cp1', 'AIM', 'mtn_road+0,-30'], [0.3, 'act', 'cp2', 'AIM', 'mtn_road+0,-30'],
    [0, 'car', 'crow_car', 'mtn_road+0,-30', 3, 180], [0.6, 'sfx', 'crow_car', 'novograd:car_start', 1.8],
    [0.8, 'drive', 'crow_car', ['outer_nw+600,0', 'outer_nw'], 0.95],
    [1.6, 'shoot', 'cp1', 'crow_car', 5, 0], [1.9, 'shoot', 'cp2', 'crow_car', 4, 0], [2.6, 'glass', 'crow_car'],
    [3.2, 'sfx', 'crow_car', 'minecraft:entity.zombie.attack_iron_door', 1.0, 0.8],
], music='radio.trassa128_0')

# a5m3 — пустошь: наёмники таскают ящики из фургона, патруль с оружием, один курит у фургона.
direct('a5m3', 0, [
    S('cam_desert', 'desert_mesa+20,10,10', 4.5),
    S('desert_mesa+26,6,3', 'desert_mesa+20,5,2', 3.5, track='m1'),
    OTS('m2', 3),
    S('desert_mesa+14,-6,1.2', 'desert_mesa+16,-8,1.4', 2.5, track='m3', fov=58),
], lambda L, E: [
    [0, 'car', 'trk', 'desert_mesa+16,0', 4, 90],
    [0, 'spawn', 'm1', 'desert_mesa+14,2', 'gang', 'Наёмник'], [0.2, 'walk', 'm1', 'desert_mesa+6,2'], [0.3, 'act', 'm1', 'CARRY'],
    [0, 'spawn', 'm2', 'desert_mesa+10,-4', 'gang', 'Наёмник'], [0.4, 'walk', 'm2', 'desert_mesa+2,-6'],
    [0, 'spawn', 'm3', 'desert_mesa+15,-3', 'gang', 'Наёмник'], [0, 'act', 'm3', 'SMOKE'],
    [5.0, 'walk', 'm1', 'desert_mesa+14,2'], [5.1, 'act', 'm1', 'CARRY'],
    [8.0, 'act', 'm2', 'AIM', 'desert_mesa+30,-6'], [9.5, 'act', 'm3', 'LAUGH', 'm1'],
], music='radio.country_road_1', vol=0.3)

# a5m7 (1) — ангар: гудят моторы самолёта, наёмники грузят мешки; Кроу оборачивается, говорит, затворы щёлкают,
# он поворачивается спиной и уходит к трапу.
direct('a5m7', 0, [
    S('airfield_hangar+20,10,4', 'airfield_hangar+12,6,3', 3.5, track='crow'),
    LOW('crow', 3, side=-1.0),
    CU('crow', 2.5),
    F('m1', 2, (-2.3, 1.6, 0.6), fov=52, shake=0.2),
    F('crow', 2.5, (3.5, 1.9, 1.4)),
], lambda L, E: [
    [0, 'spawn', 'crow', 'airfield_hangar+2,4'], [0, 'act', 'crow', 'PHONE'],
    [0, 'spawn', 'm1', 'airfield_hangar+5,3', 'gang', 'Наёмник'], [0, 'spawn', 'm2', 'airfield_hangar+-2,3', 'gang', 'Наёмник'],
    [0, 'spawn', 'm3', 'airfield_hangar+8,6', 'gang', 'Наёмник'], [0.2, 'walk', 'm3', 'airfield_hangar+-4,8'], [0.3, 'act', 'm3', 'CARRY'],
    [0.2, 'sfx', 'airfield_hangar+-6,10', 'novograd:plane_engine', 2.5],
    [0.5, 'act', 'm1', 'AIM', 'player'], [0.5, 'act', 'm2', 'AIM', 'player'],
    [0.6, 'act', 'crow', 'none'], [0.7, 'face', 'crow', 'player'], [1.2, 'act', 'crow', 'ARGUE', 'player'],
    [3.6, 'walk', 'crow', 'airfield_hangar+3,2'],
    [L[1], 'act', 'crow', 'POINT', 'player'], [L[1] + 0.4, 'sfx', 'm1', 'novograd:gun_slide_back', 1.4],
    [L[1] + 0.7, 'sfx', 'm2', 'novograd:gun_slide_back', 1.4, 0.9],
    [L[1] + 1.6, 'walk', 'crow', 'airfield_hangar+-6,10'], [L[1] + 2.2, 'sfx', 'airfield_hangar+-6,10', 'novograd:plane_engine', 2.5],
], music='radio.tekhno_podval_1')

# a5m7 (2) — финал: Кроу вырывается, Питерсон указывает на машину, его увозят; бар Софи на Стрипе; облёт Ривьеры.
direct('a5m7', 1, [
    S('airfield_hangar+14,8,3', 'airfield_hangar+9,5,2', 3, track='crow'),
    CU('crow', 2, shake=0.5),
    CHASE('cop_car', 3, 2.5, shake=0.2),
    S('riv_strip+12,4,3', 'riv_strip+8,1,2', 6, track='sf'),
    CU('sf', 3),
    S('cam_riv_far', 'cam_riv_strip', 8),
], lambda L, E: [
    [0, 'spawn', 'crow', 'airfield_hangar+2,4'], [0, 'act', 'crow', 'UPSET'], [0, 'spawn', 'petrov', 'airfield_hangar+5,6'],
    [0, 'spawn', 'c1', 'airfield_hangar+4,3', 'police'], [0, 'car', 'cop_car', 'airfield_hangar+6,12', 6, 0],
    [0.8, 'fight', 'crow', 'c1', 24], [2.0, 'anim', 'c1', 'kick'], [2.1, 'anim', 'crow', 'hit'],
    [2.2, 'sfx', 'crow', 'minecraft:entity.player.attack.strong', 1.4], [2.4, 'act', 'petrov', 'POINT', 'cop_car'],
    [3.0, 'walk', 'crow', 'airfield_hangar+5,11'], [3.1, 'walk', 'c1', 'airfield_hangar+7,10'],
    [5.0, 'ride', 'crow', 'cop_car'], [5.2, 'sfx', 'cop_car', 'minecraft:block.iron_door.close', 1.4, 0.8],
    [5.5, 'drive', 'cop_car', ['airfield_hangar+6,60', 'airfield'], 0.6], [5.7, 'sfx', 'cop_car', 'novograd:siren', 2.0],
    [0, 'spawn', 'sf', 'riv_strip+6,-2', 'c26', 'Софи'], [0, 'act', 'sf', 'TALK', 'cu1'],
    [0, 'spawn', 'cu1', 'riv_strip+7,0', 'citizen', 'Гость'], [0, 'act', 'cu1', 'DRINK'],
    [0, 'spawn', 'cu2', 'riv_strip+4,0', 'citizen', 'Гостья'], [0, 'act', 'cu2', 'DANCE'],
    [9.5, 'give', 'sf', 'cu1', 'novograd:cola'], [10.5, 'act', 'sf', 'LAUGH', 'cu1'], [12.0, 'act', 'cu1', 'CHEER'],
], music='radio.klassika_3', vol=0.3)

# =====================================================================  АКТ VI
# a6m1 — участок утром: копы болтают, Питерсону суют кофе, по его «хлопайте» — аплодисменты; Диас входит, скептично
# жмёт плечами и тычет в новичка.
direct('a6m1', 0, [
    S('police_station+14,8,3', 'police_station+8,5,2', 4, track='petrov'),
    OTS('diaz', 3.5),
    S('police_station+-1,-5,2', 'police_station+0,-6,2.2', 3.5, track='c1'),
    CU('petrov', 3),
    CU('diaz', 4, side=-0.7),
    S('police_station+8,6,4', 'police_station+12,9,6', 3, track='diaz'),
], lambda L, E: [
    [0, 'spawn', 'petrov', 'police_station+-2,2'], [0, 'spawn', 'diaz', 'police_station+8,-3'],
    [0, 'spawn', 'c1', 'police_station+-4,-2', 'police'], [0, 'spawn', 'c2', 'police_station+-5,1', 'police'], [0, 'spawn', 'c3', 'police_station+-4,4', 'police'],
    [0.5, 'act', 'c1', 'LAUGH', 'c2'], [0.5, 'act', 'c2', 'TALK', 'c1'], [0.5, 'act', 'petrov', 'TALK', 'c2'],
    [2.6, 'give', 'c3', 'petrov', 'novograd:cola'],
    [4.0, 'walk', 'diaz', 'police_station+2,0'],
    [L[1], 'act', 'petrov', 'POINT', 'player'], [L[1] + 2.0, 'act', 'c1', 'CLAP'], [L[1] + 2.1, 'act', 'c2', 'CLAP'], [L[1] + 2.2, 'act', 'c3', 'CLAP'],
    [L[1] + 3.5, 'face', 'petrov', 'player'], [L[1] + 3.6, 'act', 'petrov', 'LAUGH', 'player'],
    [L[2], 'act', 'diaz', 'SHRUG', 'petrov'], [L[2] + 2.2, 'act', 'diaz', 'POINT', 'player'],
    [E - 0.8, 'act', 'c1', 'LAUGH', 'diaz'], [E - 0.5, 'act', 'diaz', 'LAUGH', 'player'],
], music='radio.klassika_1', vol=0.25, grade='film_cine')

# a6m2 — патруль: Диас за рулём, мимо проносится «Ракета» с сигналом; сирена, дым из-под колёс — погоня.
direct('a6m2', 0, [
    HOOD('pcar', 2.5),
    S('dt_3+18,6,3', 'dt_3+10,4,2', 2.5, track='speeder'),
    CHASE('speeder', 3, 2.5, shake=0.4),
    F('pcar', 3.5, (8, 2.5, -2.5), shake=0.4),
], lambda L, E: [
    [0, 'car', 'pcar', 'dt_3+5,10', 6, 180], [0, 'spawn', 'dz', 'dt_3+5,10', 'c24', 'Сержант Диас'], [0.05, 'ride', 'dz', 'pcar'],
    [0, 'car', 'speeder', 'dt_3+0,90', 2, 180], [0.6, 'drive', 'speeder', ['dt_3+0,-40', 'dt_4'], 0.95],
    [2.0, 'sfx', 'speeder', 'novograd:car_horn', 2.5],
    [4.4, 'sfx', 'pcar', 'novograd:siren', 3.0], [4.8, 'smoke', 'dt_3+5,10'],
    [5.0, 'drive', 'pcar', ['dt_3+0,-40', 'dt_4'], 0.95],
], music='radio.trassa128_2')

# a6m3 — Сити-Банк: грабители бегут ко входу, охранник падает, звон стекла и тревога; Лена ведёт прямой эфир,
# оператор щёлкает вспышкой; бандит целится в неё — оператор пригибается, Лена говорит дальше.
direct('a6m3', 0, [
    S('bank_tower+22,10,4', 'bank_tower+12,6,3', 3, track='r1'),
    F('r1', 2.5, (3, 1.8, 1.0), shake=0.4),
    S('bank_tower+3,1,0.6', 'bank_tower+2,1,0.8', 2.5, track='gu', shake=0.6, roll=8),
    CU('lena', 4, side=0.5),
    S('bank_tower+11,12.5,1.6', 'bank_tower+11,13,1.6', 3.5, track='lena', fov=50),
], lambda L, E: [
    [0, 'spawn', 'r1', 'bank_tower+14,-3', 'gang', 'Грабитель в маске'], [0, 'spawn', 'r2', 'bank_tower+16,-2', 'gang', 'Грабитель в маске'],
    [0, 'spawn', 'r3', 'bank_tower+12,-4', 'gang', 'Грабитель в маске'],
    [0, 'spawn', 'gu', 'bank_tower+0,-3', 'police', 'Охранник банка'],
    [0.2, 'run', 'r1', 'bank_tower+1,-3'], [0.4, 'run', 'r2', 'bank_tower+3,-2'], [0.6, 'run', 'r3', 'bank_tower+-1,-4'],
    [3.4, 'fight', 'r1', 'gu', 20], [4.6, 'act', 'gu', 'SLEEP'], [4.8, 'glass', 'bank_tower+1,-5'],
    [5.0, 'sfx', 'bank_tower', 'minecraft:block.bell.use', 2.5], [5.6, 'sfx', 'bank_tower', 'minecraft:block.bell.use', 2.5],
    [5.4, 'shoot', 'r2', 'bank_tower+0,9', 3, 0],
    [0, 'spawn', 'lena', 'bank_tower+8,11'], [0, 'spawn', 'cm', 'bank_tower+10,12', 'citizen', 'Оператор'], [0.2, 'face', 'cm', 'lena'],
    [0.3, 'act', 'lena', 'TALK', 'cm'], [3.0, 'flash', 'cm'], [6.0, 'flash', 'cm'],
    [6.4, 'act', 'r1', 'AIM', 'lena'],
    [L[1], 'act', 'lena', 'TALK', 'cm'], [L[1] + 1.4, 'act', 'cm', 'UPSET'], [L[1] + 2.0, 'run', 'r3', 'bank_tower+-6,-4'],
    [L[1] + 3.2, 'flash', 'cm'], [L[1] + 3.3, 'sfx', 'cm', 'minecraft:block.lever.click', 1.4, 1.6],
], music='radio.trassa128_3', vol=0.28)

# a6m4 — слежка через длиннофокусный объектив: Марко ходит с телефоном, рукопожатие, посылка;
# «Шеф…» — Марко вдруг смотрит прямо в объектив.
direct('a6m4', 0, [
    S('dt_alley+16,7,3', 'dt_alley+13,5,2.5', 4, track='marco', fov=32, fov2=28),
    S('dt_alley+3,-5,2', 'dt_alley+2,-4,1.8', 3.5, track='courier'),
    CU('marco', 3.5),
    S('dt_alley+16,7,3', 'dt_alley+15,6,2.8', 3.5, track='marco', fov=34, fov2=20),
    OTS('courier', 2.5),
], lambda L, E: [
    [0, 'spawn', 'marco', 'dt_alley+-6,-2'], [0, 'act', 'marco', 'PHONE'],
    [0, 'spawn', 'courier', 'dt_alley+-14,0', 'gang', 'Курьер «Синих»'], [0.3, 'walk', 'courier', 'dt_alley+-7,-1'],
    [3.0, 'act', 'courier', 'HANDSHAKE', 'marco'], [3.0, 'act', 'marco', 'HANDSHAKE', 'courier'],
    [4.4, 'give', 'courier', 'marco', 'novograd:parcel'], [5.4, 'act', 'courier', 'POINT', 'dt_alley+-20,0'],
    [L[1], 'act', 'marco', 'PHONE'], [L[1] + 1.2, 'walk', 'courier', 'dt_alley+-20,0'],
    [L[1] + 3.6, 'act', 'marco', 'none'], [L[1] + 3.7, 'turn', 'marco', 'dt_alley+16,7'], [L[1] + 4.2, 'act', 'marco', 'THINK'],
    [E + 0.5, 'walk', 'marco', 'dt_alley+-12,-2'],
], music='radio.jazz_polnoch_1', grade='film_noir')

# a6m5 нет кат-сцены. a6m6 — побег: конвой тормозит перед фургоном, наёмники открывают огонь, конвоир падает,
# двери фургона распахиваются, Харрис разминается, берёт пистолет — «я вернусь за своим креслом» — и уезжает.
direct('a6m6', 0, [
    S('hw_south+20,10,4', 'hw_south+10,6,3', 3, track='convoy'),
    CHASE('convoy', 3, 2.5),
    S('hw_south+-4,4,1', 'hw_south+-3,5,1.2', 2, track='g1', shake=0.7, roll=8),
    CU('harris', 3),
    OTS('harris', 2),
    CHASE('blocker', 3.5, 2.5, shake=0.4),
], lambda L, E: [
    [0, 'car', 'convoy', 'hw_south+0,-80', 6, 0], [0.1, 'drive', 'convoy', ['hw_south+0,-4'], 0.7],
    [0, 'car', 'blocker', 'hw_south+6,6', 4, 90], [3.6, 'sfx', 'convoy', 'novograd:car_horn', 2.5],
    [5.0, 'spawn', 'g1', 'hw_south+5,3', 'gang', 'Наёмник'], [5.0, 'spawn', 'g2', 'hw_south+8,1', 'gang', 'Наёмник'],
    [5.1, 'spawn', 'cop', 'hw_south+-2,-2', 'police'],
    [5.3, 'shoot', 'g1', 'cop', 4, 0], [5.6, 'shoot', 'g2', 'convoy', 4, 0], [6.0, 'glass', 'convoy'], [6.6, 'die', 'cop'],
    [L[1] - 0.4, 'sfx', 'convoy', 'minecraft:block.iron_door.open', 1.6, 0.7],
    [L[1] - 0.2, 'spawn', 'harris', 'hw_south+-1,-1'], [L[1], 'act', 'harris', 'STRETCH'],
    [L[1] + 1.0, 'give', 'g1', 'harris', 'novograd:pistol'],
    [L[1] + 1.6, 'act', 'harris', 'LAUGH', 'player'], [L[1] + 1.7, 'hold', 'harris', 'novograd:pistol'],
    [E + 0.2, 'walk', 'harris', 'hw_south+6,4'], [E + 1.6, 'ride', 'harris', 'blocker'], [E + 1.7, 'ride', 'g1', 'blocker'],
    [E + 1.9, 'sfx', 'blocker', 'novograd:car_start', 1.6], [E + 2.2, 'drive', 'blocker', ['hw_sw', 'port_gate'], 0.95],
    [E + 2.3, 'smoke', 'hw_south+6,6'],
], music='radio.tekhno_podval_1')

# a6m7 — казино: Софи с подносом «спотыкается» и обливает Ленни, тот вскакивает, охранник тычет пальцем;
# пока она извиняется — карта уже у неё. Диас у фургона на связи.
direct('a6m7', 0, [
    S('riv_casino+-5,6,3', 'riv_casino+-3,3,2', 3, track='lenny'),
    F('sofi', 2.5, (3, 1.7, -1.3)),
    CU('lenny', 2, shake=0.3),
    S('riv_casino+1,1,1.4', 'riv_casino+0.5,0.5,1.4', 2.5, track='sofi'),
    CU('dz', 3.5),
    S('riv_casino+4,3,2', 'riv_casino+7,5,3', 2, track='sofi'),
], lambda L, E: [
    [0, 'spawn', 'lenny', 'riv_casino+1,-5', 'citizen', 'Ленни'], [0, 'act', 'lenny', 'DRINK'],
    [0, 'spawn', 'g1', 'riv_casino+3,-7', 'gang', 'Охрана Ленни'], [0, 'act', 'g1', 'LEAN'],
    [0, 'spawn', 'sofi', 'riv_casino+1,3'], [0.4, 'walk', 'sofi', 'riv_casino+1,-3'], [0.5, 'hold', 'sofi', 'novograd:cola'],
    [3.0, 'anim', 'sofi', 'hit'], [3.1, 'sfx', 'lenny', 'minecraft:entity.generic.splash', 1.2], [3.2, 'act', 'lenny', 'UPSET'],
    [3.6, 'act', 'g1', 'POINT', 'sofi'], [4.0, 'act', 'sofi', 'LAUGH', 'lenny'],
    [5.2, 'give', 'lenny', 'sofi', 'novograd:bank_card'],
    [0, 'car', 'van', 'riv_casino+-5,9', 4, 90], [0, 'spawn', 'dz', 'riv_casino+-3,10', 'c24', 'Сержант Диас'], [0, 'act', 'dz', 'PHONE'],
    [L[1] + 2.0, 'walk', 'sofi', 'riv_casino+1,4'], [L[1] + 2.2, 'act', 'lenny', 'ARGUE', 'sofi'],
], music='radio.jazz_polnoch_3', vol=0.3)

# a6m8 — колонна спецназа пылит по пустоши, тормозит; бойцы выпрыгивают, Диас командует «Пошли!»,
# бегут к складу; на крыше наёмник — короткая перестрелка.
direct('a6m8', 0, [
    S('desert_mesa+40,14,8', 'desert_mesa+26,10,6', 3.5, track='swat1'),
    CHASE('swat2', 3, 3, shake=0.5),
    CU('diaz', 2, shake=0.3),
    F('s1', 3, (3, 1.8, 1.0), shake=0.5),
    S('desert_mesa+10,6,3', 'desert_mesa+8,4,2', 3, track='m1', roll=-8, shake=0.4),
], lambda L, E: [
    [0, 'car', 'swat1', 'desert_mesa+80,0', 17, 90], [0, 'car', 'swat2', 'desert_mesa+95,6', 17, 90],
    [0.1, 'drive', 'swat1', ['desert_mesa+16,0'], 0.9], [0.4, 'drive', 'swat2', ['desert_mesa+20,6'], 0.9],
    [1.5, 'smoke', 'desert_mesa+60,2'], [3.0, 'smoke', 'desert_mesa+40,2'], [4.5, 'smoke', 'desert_mesa+25,2'],
    [6.2, 'sfx', 'swat1', 'minecraft:block.iron_door.open', 1.6, 0.7],
    [6.3, 'spawn', 's1', 'desert_mesa+14,2', 'police', 'Спецназ'], [6.3, 'spawn', 's2', 'desert_mesa+15,-2', 'police', 'Спецназ'],
    [6.3, 'spawn', 's3', 'desert_mesa+17,4', 'police', 'Спецназ'], [6.3, 'spawn', 'diaz', 'desert_mesa+16,4'],
    [L[1] - 0.2, 'act', 'diaz', 'POINT', 'desert_mesa'],
    [L[1] + 1.0, 'run', 's1', 'desert_mesa+4,2'], [L[1] + 1.1, 'run', 's2', 'desert_mesa+5,-2'], [L[1] + 1.2, 'run', 's3', 'desert_mesa+6,4'],
    [L[1] + 1.4, 'run', 'diaz', 'desert_mesa+7,1'],
    [0, 'spawn', 'm1', 'desert_mesa+-2,0', 'gang', 'Наёмник'], [L[1] + 3.0, 'act', 'm1', 'AIM', 's1'],
    [L[1] + 3.6, 'shoot', 'm1', 's1', 3, 0], [L[1] + 4.0, 'shoot', 's2', 'm1', 4, 0], [L[1] + 5.2, 'die', 'm1'],
], music='radio.trassa128_3')

# a6m9 — Коул копается в шкафчике, видит нас, бросает всё и бежит; хлопает дверь, Диас выскакивает следом и целится —
# поздно: машина Коула с визгом уходит.
direct('a6m9', 0, [
    S('police_station+10,6,3', 'police_station+7,4,2', 2, track='cole'),
    CU('cole', 1.6, shake=0.4),
    F('cole', 2, (3, 1.8, 1.0), shake=0.4),
    CU('dz', 2.2, shake=0.3),
    CHASE('cole_car', 4, 2.5, shake=0.4),
], lambda L, E: [
    [0, 'spawn', 'cole', 'police_station+2,-1', 'police', 'Сержант Коул'], [0, 'act', 'cole', 'RUMMAGE'],
    [0, 'car', 'cole_car', 'police_station+10,6', 6, 90],
    [0, 'spawn', 'dz', 'police_station+-3,1', 'c24', 'Сержант Диас'],
    [0.9, 'act', 'cole', 'none'], [1.0, 'face', 'cole', 'player'], [1.2, 'act', 'cole', 'UPSET'],
    [2.0, 'run', 'cole', 'police_station+9,5'], [2.2, 'sfx', 'police_station+6,3', 'minecraft:block.wooden_door.open', 1.4],
    [2.3, 'door', 'police_station+6,3', 'open'], [2.4, 'run', 'dz', 'police_station+6,3'],
    [4.2, 'ride', 'cole', 'cole_car'], [4.3, 'sfx', 'cole_car', 'minecraft:block.iron_door.close', 1.4, 0.8],
    [4.6, 'smoke', 'police_station+10,6'], [4.8, 'drive', 'cole_car', ['dt_5', 'dt_4'], 0.95], [4.9, 'sfx', 'cole_car', 'novograd:car_start', 1.6],
    [5.2, 'act', 'dz', 'AIM', 'cole_car'],
], music='radio.trassa128_0')

# a6m10 (1) — «Неон»: Харрис прикрывается Леной, ствол у её виска, наёмники целятся и подходят; Лена брыкается.
direct('a6m10', 0, [
    S('club_neon+10,5,3', 'club_neon+7,4,2', 2.5, track='harris'),
    LOW('harris', 2.5, side=-0.9),
    CU('lena', 2.5, roll=8, shake=0.3),
    OTS('g1', 2),
    S('club_neon+3,3,1.8', 'club_neon+2,2,1.7', 3, track='harris', shake=0.3),
], lambda L, E: [
    [0, 'spawn', 'harris', 'club_neon+0,-3'], [0, 'spawn', 'lena', 'club_neon+1,-3'], [0, 'act', 'lena', 'UPSET'],
    [0, 'spawn', 'g1', 'club_neon+3,-6', 'gang', 'Наёмник'], [0, 'spawn', 'g2', 'club_neon+-3,-5', 'gang', 'Наёмник'],
    [0.3, 'act', 'harris', 'AIM', 'player'], [0.5, 'act', 'g1', 'AIM', 'player'], [0.5, 'act', 'g2', 'AIM', 'player'],
    [1.4, 'anim', 'harris', 'drag'], [1.6, 'anim', 'lena', 'hit'],
    [L[1], 'anim', 'lena', 'kick'], [L[1] + 0.4, 'act', 'harris', 'ARGUE', 'lena'], [L[1] + 0.5, 'hold', 'harris', 'novograd:pistol'],
    [L[1] + 1.4, 'walk', 'g1', 'club_neon+2,-2'], [L[1] + 1.6, 'walk', 'g2', 'club_neon+-2,-1'],
    [L[1] + 2.4, 'sfx', 'g1', 'novograd:gun_slide_back', 1.4],
    [E + 0.2, 'walk', 'harris', 'club_neon+-1,-7'], [E + 0.3, 'walk', 'lena', 'club_neon+0,-7'],
], music='radio.tekhno_podval_2', vol=0.3)

# a6m10 (2) — чёрный ход: дверь, Харрис тащит Лену к машине, она отбивается — удар; в машину и по газам.
direct('a6m10', 1, [
    S('club_neon+12,8,3', 'club_neon+8,5,2', 2.5, track='harris'),
    F('harris', 2, (-2.6, 1.6, 0.7), fov=56, shake=0.3),
    CU('lena', 1.6, shake=0.5, roll=-8),
    CHASE('h_car', 4.5, 2.5, shake=0.4),
], lambda L, E: [
    [0, 'car', 'h_car', 'club_neon+8,6', 3, 90],
    [0, 'spawn', 'harris', 'club_neon+3,1'], [0, 'spawn', 'lena', 'club_neon+3.5,0'], [0, 'door', 'club_neon+3,0', 'open'],
    [0.3, 'walk', 'harris', 'club_neon+7,5'], [0.4, 'walk', 'lena', 'club_neon+7.5,4.5'],
    [2.6, 'fight', 'lena', 'harris', 20], [3.6, 'anim', 'harris', 'kick'], [3.7, 'anim', 'lena', 'hit'],
    [3.8, 'sfx', 'lena', 'minecraft:entity.player.attack.strong', 1.3],
    [4.4, 'ride', 'harris', 'h_car'], [4.5, 'ride', 'lena', 'h_car'], [4.6, 'sfx', 'h_car', 'minecraft:block.iron_door.close', 1.5, 0.7],
    [5.0, 'drive', 'h_car', ['dt_1', 'dt_2'], 0.95], [5.2, 'smoke', 'club_neon+8,6'],
], music='radio.trassa128_1')

# a6m11 нет кат-сцены. a6m12 (1) — кран у причала: гудит мотор катера, Харрис курит под стволами наёмников,
# бросает окурок, достаёт пистолет — «Убейте его» — и первая очередь летит в нас.
direct('a6m12', 0, [
    S('port_crane+18,8,4', 'port_crane+11,5,3', 3.5, track='harris'),
    OTS('harris', 2.5),
    LOW('harris', 3.5, side=-1.0),
    F('m1', 2, (-2.3, 1.6, 0.6), fov=52),
    S('port_crane+12,1,1.5', 'port_crane+10,1,1.5', 3, track='m1', shake=0.7, roll=-9),
], lambda L, E: [
    [0, 'spawn', 'harris', 'port_crane+2,3'], [0, 'act', 'harris', 'SMOKE'],
    [0, 'spawn', 'm1', 'port_crane+5,2', 'gang', 'Наёмник'], [0, 'spawn', 'm2', 'port_crane+-2,2', 'gang', 'Наёмник'],
    [0, 'spawn', 'm3', 'port_crane+0,7', 'gang', 'Наёмник'], [0.3, 'walk', 'm3', 'port_crane+6,7'],
    [0.2, 'sfx', 'port_crane+-6,0', 'novograd:boat_engine', 2.5],
    [0.5, 'act', 'm1', 'AIM', 'player'], [0.5, 'act', 'm2', 'AIM', 'player'],
    [1.4, 'act', 'harris', 'ARGUE', 'player'],
    [L[1] + 0.4, 'act', 'harris', 'AIM', 'player'], [L[1] + 0.5, 'sfx', 'harris', 'novograd:gun_slide_back', 1.4],
    [E - 0.5, 'sfx', 'm1', 'novograd:gun_slide_back', 1.4, 0.9], [E - 0.2, 'walk', 'harris', 'port_crane+-6,4'],
    [E + 0.6, 'shoot', 'm1', 'port_crane+12,0', 4, 0], [E + 0.8, 'shoot', 'm2', 'port_crane+12,2', 4, 0],
], music='radio.tekhno_podval_1')

# a6m12 (2) — финал: Харриса в наручниках сажают в машину; участок: аплодисменты, рукопожатие Питерсона,
# вспышка Лены; Диас с рукой на перевязи вручает «штраф за парковку».
direct('a6m12', 1, [
    S('port_crane+14,8,3', 'port_crane+9,5,2', 3, track='harris'),
    CHASE('cop_car', 3.5, 2.5, shake=0.2),
    S('police_station+12,6,3', 'police_station+7,4,2', 4, track='petrov'),
    CU('lena', 2),
    CU('petrov', 3),
    CU('diaz', 4, side=-0.6),
    S('police_station+8,6,4', 'police_station+12,9,6', 3, track='diaz'),
    S('cam_dt_a', 'cam_dt_b', 5),
], lambda L, E: [
    [0, 'spawn', 'harris', 'port_crane+2,4'], [0, 'act', 'harris', 'UPSET'], [0, 'spawn', 'c1', 'port_crane+4,3', 'police'],
    [0, 'car', 'cop_car', 'port_crane+6,12', 6, 0], [0.5, 'walk', 'harris', 'port_crane+5,11'], [0.6, 'walk', 'c1', 'port_crane+7,10'],
    [2.6, 'anim', 'c1', 'drag'], [3.6, 'ride', 'harris', 'cop_car'], [3.7, 'sfx', 'cop_car', 'minecraft:block.iron_door.close', 1.4, 0.8],
    [4.0, 'drive', 'cop_car', ['port_gate'], 0.6], [4.2, 'sfx', 'cop_car', 'novograd:siren', 2.0],
    [0, 'spawn', 'petrov', 'police_station+-1,1'], [0, 'spawn', 'diaz', 'police_station+6,-3'], [0, 'spawn', 'lena', 'police_station+4,3'],
    [0, 'spawn', 'c2', 'police_station+-4,-2', 'police'], [0, 'spawn', 'c3', 'police_station+-4,3', 'police'],
    [L[1] - 0.6, 'act', 'c2', 'CLAP'], [L[1] - 0.5, 'act', 'c3', 'CHEER', 'player'],
    [L[1] + 0.2, 'act', 'petrov', 'HANDSHAKE', 'player'], [L[1] + 1.8, 'flash', 'lena'],
    [L[1] + 1.9, 'sfx', 'lena', 'minecraft:block.lever.click', 1.4, 1.6], [L[1] + 2.4, 'act', 'lena', 'LAUGH', 'player'],
    [L[1] + 4.2, 'act', 'petrov', 'LAUGH', 'player'],
    [L[2] - 0.8, 'walk', 'diaz', 'police_station+2,0'], [L[2] + 1.6, 'act', 'diaz', 'POINT', 'player'],
    [L[2] + 1.7, 'hold', 'diaz', 'minecraft:paper'], [L[2] + 3.5, 'act', 'c2', 'LAUGH', 'diaz'], [L[2] + 4.0, 'act', 'diaz', 'SHRUG', 'player'],
], music='radio.klassika_3', vol=0.3)
