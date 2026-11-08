# Shorts «Голосовые от мамы» (7 ноя): своя страшная история, не связанная с играми.
# Видео: Mixkit (Mixkit Free License — можно на YouTube и в коммерции, без указания автора) + свой экран телефона и «глазок».
# Голоса: ElevenLabs v4 (аккаунт владельца): рассказчик BrokenFutureNarrator, мама Tatyana Voloshina.
#   python3 tools/stories/mom_story.py CLIPS_DIR VO_DIR OUT.mp4
# CLIPS_DIR: 33676 (похороны), 48733 (кровать), 402 (силуэт), 1986 (коридор), 31380 (замок), 20843 (глаз)
# VO_DIR: nar1.mp3 (рассказчик), mom1.mp3 (мама)
import math, os, random, subprocess, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter

CL, VO, OUT = sys.argv[1], sys.argv[2], sys.argv[3]
W, H, FPS, SR = 1080, 1920, 30, 48000
ROOT = os.path.dirname(os.path.abspath(__file__)); SND = os.path.join(ROOT, '../../public/games/nightshift/snd')
TMP = os.path.dirname(os.path.abspath(OUT))
FD = '/usr/share/fonts/opentype/inter/'
def F(sz, w='SemiBold'): return ImageFont.truetype(FD + f'Inter-{w}.otf', sz)
def ease(x): x = max(0., min(1., x)); return x * x * (3 - 2 * x)
rnd = random.Random(7)

# ------------------------------------------------------------ голос: (кто, начало в файле, конец, фраза для субтитров)
NAR = {'n1': (0.0, 1.94, 'Мама умерла сорок дней назад.'), 'n2': (2.80, 4.96, 'Вчера она прислала мне голосовое.'),
       'n3': (6.24, 11.28, 'Я решил, что кто-то взломал её номер. Заблокировал. Лёг спать.'),
       'n4': (12.54, 14.68, 'В 3:12 в дверь позвонили.'), 'n5': (15.82, 17.18, 'Я посмотрел в глазок.'),
       'n6': (17.62, 18.74, 'На площадке никого.')}
MOM = {'m1': (0.0, 3.54, 'Сынок… Не открывай дверь. Это не я.'), 'm2': (4.46, 8.28, 'Сынок, открой. Это я. Я замёрзла.'),
       'm3': (9.34, 12.30, 'Не открывай. Он уже научился говорить как я.'), 'm4': (13.30, 17.12, 'Открой! Открой! ОТКРОЙ!'),
       'm5': (17.88, 20.08, 'Поздно. Он уже внутри.')}
# ------------------------------------------------------------ язык: STORY_LANG=en — английские голоса, субтитры и экран телефона
LANG = os.environ.get('STORY_LANG', 'ru')
EN = {'пятница, 7 ноября': 'Friday, November 7', 'сейчас': 'now', 'Голосовое сообщение (0:04)': 'Voice message (0:04)',
      'Мама ♥': 'Mom ♥', 'была в сети 40 дней назад': 'last seen 40 days ago', '26 сентября': 'September 26',
      'Сынок, ты поел?': 'Did you eat, sweetie?', 'Да, мам': 'Yeah, mom', 'Купи хлеба по дороге': 'Grab some bread on the way',
      'Позвони мне завтра. Люблю': 'Call me tomorrow. Love you', 'Сегодня': 'Today', 'Вы заблокировали этот контакт': 'You blocked this contact',
      'Разблокировать': 'Unblock', 'Больше страшных историй': 'More scary stories',
      'там же — моя хоррор-игра Hush Till Six': 'also there: my horror game Hush Till Six', 'и новые проекты': 'and new projects'}
def tr(s): return EN.get(s, s) if LANG == 'en' else s
if LANG == 'en':
    NAR = {'n1': (0.0, 1.94, 'Mom died forty days ago.'), 'n2': (3.04, 5.68, 'Yesterday, she sent me a voice message.'),
           'n3': (6.94, 11.10, 'I figured someone hacked her number. I blocked it. Went to bed.'),
           'n4': (12.34, 14.50, 'At 3:12, the doorbell rang.'), 'n5': (15.58, 16.92, 'I looked through the peephole.'),
           'n6': (18.00, 18.74, 'No one was there.')}
    MOM = {'m1': (0.0, 3.62, "Sweetheart… Don't open the door. It's not me."), 'm2': (4.66, 8.90, "Sweetheart, open up. It's me. I'm so cold."),
           'm3': (9.70, 12.38, "Don't open it. He's learned to sound like me."), 'm4': (13.12, 17.32, 'Open up! Open up! OPEN UP!'),
           'm5': (18.14, 20.82, "Too late. He's already inside.")}
VO_NAR, VO_MOM = ('en_nar1', 'en_mom2') if LANG == 'en' else ('nar1', 'mom1')
# реплика: (ключ, время в ролике, обработка: room / phone / door)
LINES = [('n1', .5, 'room'), ('n2', 3.5, 'room'), ('m1', 8.2, 'phone'), ('n3', 13.0, 'room'), ('n4', 19.4, 'room'),
         ('n5', 23.9, 'room'), ('n6', 26.6, 'room'), ('m2', 29.2, 'door'), ('m3', 35.6, 'phone'), ('m4', 41.2, 'door'),
         ('m5', 49.6, 'phone')]

# ------------------------------------------------------------ кадры: (вид, начало, конец, параметры)
SHOTS = [('clip', 0.0, 3.2, dict(c='33676', t0=1.5, cx=.5, g='cold')),
         ('lock', 3.2, 6.9, dict(time='03:07', note=True)),
         ('chat', 6.9, 12.8, dict(state=1, play=8.0, dur=3.6)),
         ('clip', 12.8, 19.0, dict(c='48733', t0=25.0, cx=.68, g='night')),
         ('lock', 19.0, 23.5, dict(time='03:12', note=False, dim=True)),
         ('clip', 23.5, 26.3, dict(c='402', t0=0.5, cx=.6, g='mono')),
         ('peep', 26.3, 34.6, dict(c='1986', t0=0.0)),
         ('chat', 34.6, 40.6, dict(state=2, play=35.4, dur=3.0)),
         ('clip', 40.6, 46.2, dict(c='31380', t0=0.2, cx=.47, g='lock')),
         ('black', 46.2, 46.9, {}),
         ('eye', 46.9, 48.7, dict(c='20843', t0=1.0)),
         ('chat', 48.7, 53.4, dict(state=3, play=49.4, dur=2.3, off=52.8)),
         ('end', 53.4, 60.0, {})]
T_END = SHOTS[-1][2]

# ------------------------------------------------------------ общее: виньетка, зерно, субтитры
yy, xx = np.mgrid[0:H, 0:W]
VIG = np.clip(1.15 - np.sqrt(((xx - W / 2) / (W * .62)) ** 2 + ((yy - H / 2) / (H * .6)) ** 2) * .75, .25, 1)[..., None].astype(np.float32)
GRAIN = [np.random.default_rng(i).normal(0, 6, (H // 2, W // 2, 1)).astype(np.float32).repeat(2, 0).repeat(2, 1) for i in range(6)]

def subs(img, t):
    for k, at, fx in LINES:
        a, b, txt = (NAR.get(k) or MOM[k]); d = b - a
        if not (at - .05 <= t < at + d + .5): continue
        al = ease((t - at + .05) / .2) * ease((at + d + .5 - t) / .25)
        mom = k[0] == 'm'
        f = F(58 if not mom else 60, 'SemiBold'); words = ((('«' + txt + '»') if LANG != 'en' else ('“' + txt + '”')) if mom else txt).split(); lines, cur = [], ''
        for w in words:
            if f.getlength((cur + ' ' + w).strip()) > 900: lines.append(cur); cur = w
            else: cur = (cur + ' ' + w).strip()
        lines.append(cur)
        y = 1430 - (len(lines) - 1) * 40; col = (255, 205, 190) if mom else (240, 238, 232)
        bw = max(f.getlength(l) for l in lines) + 70
        ov = Image.new('RGBA', (W, H)); do = ImageDraw.Draw(ov)
        do.rounded_rectangle((W / 2 - bw / 2, y - 55, W / 2 + bw / 2, y + (len(lines) - 1) * 80 + 55), 26, fill=(0, 0, 0, int(170 * al)))
        img = Image.alpha_composite(img.convert('RGBA'), ov).convert('RGB'); d0 = ImageDraw.Draw(img)
        for l in lines:
            d0.text((W / 2, y), l, font=f, fill=tuple(int(c * al) for c in col), anchor='mm', stroke_width=3, stroke_fill=(0, 0, 0)); y += 80
    return img

def finish(a, n, t):
    a = a * VIG + GRAIN[n % 6]
    return subs(Image.fromarray(np.clip(a, 0, 255).astype(np.uint8)), t)

# ------------------------------------------------------------ видео из клипа
GR = {'cold': "eq=saturation=0.35:contrast=1.12:brightness=-0.03,colorbalance=bs=0.10:bm=0.05:rs=-0.04",
      'night': "eq=saturation=0.45:contrast=1.1:brightness=-0.03:gamma=1.0,colorbalance=bs=0.05:rs=-0.03",
      'mono': "hue=s=0,eq=contrast=1.25:brightness=-0.04,colorbalance=bs=0.08:bm=0.04",
      'lock': "eq=saturation=0.25:contrast=1.15:brightness=0.0:gamma=1.45,colorbalance=bs=0.08:gs=-0.03"}

def probe(p): return list(map(int, subprocess.run(['ffprobe', '-v', 'error', '-select_streams', 'v', '-show_entries', 'stream=width,height', '-of', 'csv=p=0', p], capture_output=True, text=True).stdout.split(',')))

def reader(path, t0, dur, vf, w, h):
    p = subprocess.Popen(['ffmpeg', '-v', 'error', '-ss', str(t0), '-t', str(dur + .5), '-i', path, '-vf', vf, '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-'], stdout=subprocess.PIPE)
    last = np.zeros((h, w, 3), np.uint8)
    while True:
        b = p.stdout.read(w * h * 3)
        if len(b) == w * h * 3: last = np.frombuffer(b, np.uint8).reshape(h, w, 3)
        yield last

def clip_gen(o, dur):
    path = os.path.join(CL, o['c'] + '.mp4'); sw, sh = probe(path)
    cw = int(sh * W / H) // 2 * 2; x0 = int(max(0, min(sw - cw, sw * o['cx'] - cw / 2)))
    vf = f"crop={cw}:{sh}:{x0}:0,scale={int(W*1.1)}:{int(H*1.1)}:flags=lanczos,crop={W}:{H}:'(in_w-{W})/2':'(in_h-{H})/2-{int(H*.03)}*t/{dur}',{GR[o['g']]},fps={FPS}"
    return reader(path, o['t0'], dur, vf, W, H)

# ------------------------------------------------------------ глазок: «рыбий глаз» в круге
R_OUT = 470
def peep_map(n):
    y, x = np.mgrid[0:H, 0:W]; dx, dy = (x - W / 2) / R_OUT, (y - H / 2 + 60) / R_OUT
    r = np.sqrt(dx * dx + dy * dy); rs = np.sin(np.clip(r, 0, 1) * 1.25) / math.sin(1.25)   # бочка
    k = np.where(r > 0, rs / np.maximum(r, 1e-6), 1)
    sx = np.clip((dx * k * .5 + .5) * (n - 1), 0, n - 1).astype(np.int32); sy = np.clip((dy * k * .5 + .5) * (n - 1), 0, n - 1).astype(np.int32)
    ring = np.clip((1.0 - r) * 14, 0, 1) * np.clip(1.05 - r ** 4 * .9, 0, 1)
    return sy, sx, ring[..., None].astype(np.float32)
PM = peep_map(900)

def peep_gen(o, dur, gray=True, zoom=1.0):
    path = os.path.join(CL, o['c'] + '.mp4'); sw, sh = probe(path); s = int(sh / zoom) // 2 * 2
    vf = f"crop={s}:{s}:{(sw-s)//2}:{(sh-s)//2},scale=900:900,{'hue=s=0,' if gray else ''}eq=contrast=1.2:brightness=-0.02,fps={FPS}"
    for fr in reader(path, o['t0'], dur, vf, 900, 900):
        yield fr[PM[0], PM[1]].astype(np.float32) * PM[2]

# ------------------------------------------------------------ телефон: экран блокировки и чат
PH_W, PH_H = 860, 1760
def rr(d, box, r, fill): d.rounded_rectangle(box, r, fill=fill)
WAVE = [0.25 + 0.75 * abs(math.sin(i * .9) * math.cos(i * .37)) * (0.6 + 0.4 * rnd.random()) for i in range(34)]

def screen_lock(o, t):
    im = Image.new('RGB', (PH_W, PH_H)); d = ImageDraw.Draw(im)
    for y in range(PH_H):                      # тёмные обои
        c = int(14 + 18 * (y / PH_H)); d.line([(0, y), (PH_W, y)], fill=(c // 2, c // 2 + 2, c + 8))
    d.text((PH_W / 2, 330), o['time'], font=F(190, 'Bold'), fill=(232, 234, 240), anchor='mm')
    d.text((PH_W / 2, 470), tr('пятница, 7 ноября'), font=F(44, 'Regular'), fill=(190, 194, 205), anchor='mm')
    if o.get('note') and t > .35:
        a = ease((t - .35) / .3); y0 = 640 - 40 * (1 - a)
        card = Image.new('RGBA', (PH_W, PH_H)); dc = ImageDraw.Draw(card)
        dc.rounded_rectangle((40, y0, PH_W - 40, y0 + 190), 38, fill=(60, 62, 72, int(225 * a)))
        dc.ellipse((70, y0 + 40, 170, y0 + 140), fill=(120, 96, 110, int(255 * a)))
        dc.text((120, y0 + 90), 'М', font=F(52, 'Bold'), fill=(255, 255, 255, int(255 * a)), anchor='mm')
        dc.text((200, y0 + 62), tr('Мама ♥'), font=F(44, 'Bold'), fill=(245, 245, 248, int(255 * a)), anchor='lm')
        dc.text((PH_W - 80, y0 + 62), tr('сейчас'), font=F(34, 'Regular'), fill=(180, 182, 190, int(255 * a)), anchor='rm')
        dc.text((200, y0 + 125), tr('Голосовое сообщение (0:04)'), font=F(40, 'Regular'), fill=(215, 216, 222, int(255 * a)), anchor='lm')
        im.paste(card, (0, 0), card)
    d.rounded_rectangle((PH_W / 2 - 140, PH_H - 40, PH_W / 2 + 140, PH_H - 28), 6, fill=(200, 200, 205))
    return im

def bubble_voice(d, y, prog, playing, dur_txt, time_txt):
    rr(d, (30, y, 620, y + 150), 34, (38, 40, 46))
    cx, cy = 100, y + 75; d.ellipse((cx - 46, cy - 46, cx + 46, cy + 46), fill=(92, 142, 210))
    if playing: d.rectangle((cx - 16, cy - 20, cx - 6, cy + 20), fill='white'); d.rectangle((cx + 6, cy - 20, cx + 16, cy + 20), fill='white')
    else: d.polygon([(cx - 12, cy - 22), (cx - 12, cy + 22), (cx + 22, cy)], fill='white')
    for i, h in enumerate(WAVE):
        x = 170 + i * 12; hh = 8 + h * 46; on = i / len(WAVE) < prog
        d.rounded_rectangle((x, y + 62 - hh / 2, x + 6, y + 62 + hh / 2), 3, fill=(120, 170, 235) if on else (110, 112, 120))
    d.text((170, y + 118), dur_txt, font=F(30, 'Regular'), fill=(150, 154, 165), anchor='lm')
    d.text((600, y + 118), time_txt, font=F(28, 'Regular'), fill=(150, 154, 165), anchor='rm')

def screen_chat(o, t, T):
    im = Image.new('RGB', (PH_W, PH_H), (14, 16, 20)); d = ImageDraw.Draw(im)
    y = 250 - (330 if o['state'] >= 2 else 0) - (175 if o['state'] == 3 else 0)   # новые сообщения выше субтитров
    def chip(txt):
        nonlocal y; w = F(30, 'SemiBold').getlength(txt) + 50
        rr(d, (PH_W / 2 - w / 2, y, PH_W / 2 + w / 2, y + 56), 28, (34, 36, 42)); d.text((PH_W / 2, y + 28), txt, font=F(30, 'SemiBold'), fill=(170, 172, 180), anchor='mm'); y += 90
    def msg(txt, tm, out=False):
        nonlocal y; f = F(40, 'Regular'); w = min(600, f.getlength(txt) + 150)
        x0 = PH_W - 30 - w if out else 30
        rr(d, (x0, y, x0 + w, y + 110), 32, (43, 82, 120) if out else (38, 40, 46))
        d.text((x0 + 32, y + 50), txt, font=f, fill=(236, 238, 242), anchor='lm')
        d.text((x0 + w - 24, y + 88), tm, font=F(26, 'Regular'), fill=(150, 160, 175), anchor='rm'); y += 135
    chip(tr('26 сентября')); msg(tr('Сынок, ты поел?'), '19:02'); msg(tr('Да, мам'), '19:40', True)
    msg(tr('Купи хлеба по дороге'), '19:41'); msg(tr('Позвони мне завтра. Люблю'), '21:15')
    chip(tr('Сегодня'))
    p = max(0, min(1, (T - o['play']) / o['dur'])) if T >= o['play'] else 0
    playing = o['play'] <= T < o['play'] + o['dur']
    if o['state'] == 1:
        bubble_voice(d, y, p, playing, f"0:0{int(p*4)}" if playing else '0:04', '03:07'); y += 175
    else:
        bubble_voice(d, y, 1, False, '0:04', '03:07'); y += 175
        if o['state'] >= 2:
            ban = tr('Вы заблокировали этот контакт'); chip(ban)
            if o['state'] == 2: bubble_voice(d, y, p, playing, '0:03', '03:19'); y += 175
            else:
                bubble_voice(d, y, 1, False, '0:03', '03:19'); y += 175
                bubble_voice(d, y, p, playing, '0:02', '03:26'); y += 175
    d.rectangle((0, 0, PH_W, 190), fill=(26, 28, 34))
    d.text((40, 120), '‹', font=F(80, 'Regular'), fill=(110, 160, 230), anchor='lm')
    d.ellipse((100, 75, 190, 165), fill=(120, 96, 110)); d.text((145, 120), 'М', font=F(46, 'Bold'), fill='white', anchor='mm')
    d.text((215, 98), tr('Мама ♥'), font=F(46, 'Bold'), fill=(240, 240, 245), anchor='lm')
    d.text((215, 148), tr('была в сети 40 дней назад'), font=F(32, 'Regular'), fill=(140, 144, 155), anchor='lm')
    d.rectangle((0, PH_H - 130, PH_W, PH_H), fill=(26, 28, 34))
    d.text((PH_W / 2, PH_H - 65), tr('Разблокировать'), font=F(40, 'SemiBold'), fill=(232, 90, 90) if o['state'] >= 2 else (110, 160, 230), anchor='mm')
    return im

def phone_frame(scr, t, n, glow=1.0, off=0.0):
    """экран в тёмной комнате: телефон в руке, лёгкое покачивание, свечение"""
    can = Image.new('RGB', (W, H), (0, 0, 0))
    s = scr.resize((int(PH_W * .98), int(PH_H * .98)), Image.LANCZOS)
    body = Image.new('RGBA', (s.width + 36, s.height + 36), (0, 0, 0, 0)); db = ImageDraw.Draw(body)
    db.rounded_rectangle((0, 0, body.width - 1, body.height - 1), 70, fill=(10, 10, 12, 255))
    mask = Image.new('L', s.size, 0); ImageDraw.Draw(mask).rounded_rectangle((0, 0, s.width - 1, s.height - 1), 54, fill=255)
    body.paste(s, (18, 18), mask)
    ang = .8 * math.sin(t * .7); dx = 8 * math.sin(t * .9 + n * .0); dy = 10 * math.sin(t * .6)
    body = body.rotate(ang, resample=Image.BICUBIC, expand=True)
    can.paste(body, (int((W - body.width) / 2 + dx), int((H - body.height) / 2 + dy)), body)
    a = np.asarray(can).astype(np.float32) * glow
    blur = np.asarray(can.resize((W // 8, H // 8)).filter(ImageFilter.GaussianBlur(10)).resize((W, H))).astype(np.float32)
    a = a + blur * .35 * glow
    if off: a *= max(0, 1 - off)
    return a

# ------------------------------------------------------------ финальная карточка
def end_card(t):
    im = Image.new('RGB', (W, H)); d = ImageDraw.Draw(im)
    a = ease(t / .6); b = ease((t - .9) / .6); c = ease((t - 1.8) / .6)
    d.text((W / 2, 800), tr('Больше страшных историй'), font=F(64, 'SemiBold'), fill=tuple(int(v * a) for v in (235, 233, 228)), anchor='mm')
    d.text((W / 2, 905), '@yompixgames', font=F(92, 'Bold'), fill=tuple(int(v * b) for v in (232, 60, 60)), anchor='mm')
    d.text((W / 2, 1000), 'YouTube  ·  Telegram', font=F(46, 'Regular'), fill=tuple(int(v * b) for v in (200, 198, 192)), anchor='mm')
    d.text((W / 2, 1180), tr('там же — моя хоррор-игра Hush Till Six'), font=F(44, 'Regular'), fill=tuple(int(v * c * .85) for v in (200, 198, 192)), anchor='mm')
    d.text((W / 2, 1245), tr('и новые проекты'), font=F(44, 'Regular'), fill=tuple(int(v * c * .85) for v in (200, 198, 192)), anchor='mm')
    return np.asarray(im).astype(np.float32)

# ------------------------------------------------------------ рендер
def render():
    enc = subprocess.Popen(['ffmpeg', '-v', 'error', '-y', '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-s', f'{W}x{H}', '-r', str(FPS), '-i', '-',
                            '-c:v', 'libx264', '-preset', 'medium', '-crf', '23', '-pix_fmt', 'yuv420p', os.path.join(TMP, 'v.mp4')], stdin=subprocess.PIPE)
    n = 0
    for kind, a0, a1, o in SHOTS:
        dur = a1 - a0; N = int(round(a1 * FPS)) - int(round(a0 * FPS))
        gen = clip_gen(o, dur) if kind == 'clip' else peep_gen(o, dur) if kind == 'peep' else peep_gen(o, dur, True, 1.6) if kind == 'eye' else None
        for i in range(N):
            t = i / FPS; T = a0 + t
            if kind == 'clip':
                a = next(gen).astype(np.float32)
                if o['c'] == '31380': a *= 1 + .25 * (rnd.random() < .08)   # мигает свет на площадке
            elif kind == 'peep':
                fl = .75 + .25 * (math.sin(T * 23) > .7) * rnd.random()                # мигающая лампа на площадке
                a = next(gen) * fl * ease(t / .4) * 1.25
            elif kind == 'eye':
                a = next(gen) * 1.25; sx, sy = rnd.randint(-22, 22), rnd.randint(-22, 22); a = np.roll(a, (sy, sx), (0, 1))
                if t < .12: a = a * .4 + 120
                if t > 1.3: a *= max(0, 1 - (t - 1.3) / .4)
            elif kind == 'lock':
                g = ease(t / .25) * (.55 if o.get('dim') else 1.0); a = phone_frame(screen_lock(o, t), t, n, g)
            elif kind == 'chat':
                off = 0
                if o.get('off') and T > o['off']: off = min(1, (T - o['off']) / .15) if rnd.random() > .3 else 1
                a = phone_frame(screen_chat(o, t, T), t, n, ease(t / .2), off)
            elif kind == 'black': a = np.zeros((H, W, 3), np.float32)
            else: a = end_card(t)
            if kind != 'end' and t > dur - .12 and kind in ('clip',): a *= max(0, (dur - t) / .12)
            enc.stdin.write(finish(a, n, T).tobytes()); n += 1
    enc.stdin.close(); enc.wait()

# ------------------------------------------------------------ звук
def load(path, t0=0, dur=None, af=None):
    cmd = ['ffmpeg', '-v', 'error', '-ss', str(t0)] + (['-t', str(dur)] if dur else []) + ['-i', path] + (['-af', af] if af else []) + ['-ac', '2', '-ar', str(SR), '-f', 'f32le', '-']
    return np.frombuffer(subprocess.run(cmd, capture_output=True).stdout, np.float32).reshape(-1, 2)
def sfx(name): return load(os.path.join(SND, name + '.mp3'))
FX = {'room': "aecho=0.8:0.6:35|60:0.12|0.08,acompressor=threshold=-20dB:ratio=3",
      'phone': "highpass=f=450,lowpass=f=3200,acrusher=bits=10:mix=0.25,volume=1.3,aecho=0.8:0.5:12:0.15",
      'door': "lowpass=f=1100,highpass=f=120,aecho=0.8:0.7:45|90:0.35|0.25,volume=0.9"}

def tone(freqs, dur, decay=3.0, vol=.3):
    t = np.arange(int(dur * SR)) / SR; s = sum(np.sin(2 * np.pi * f * t) / (k + 1) for k, f in enumerate(freqs)) * np.exp(-t * decay) * vol
    return np.stack([s, s], 1).astype(np.float32)
def vibro(dur=.9):
    t = np.arange(int(dur * SR)) / SR; env = ((t % .45) < .3).astype(np.float32)
    s = np.sign(np.sin(2 * np.pi * 160 * t)) * .12 * env * (0.6 + 0.4 * np.sin(2 * np.pi * 7 * t))
    s = np.convolve(s, np.ones(40) / 40, 'same'); return np.stack([s, s], 1).astype(np.float32)
def bell():   # дверной звонок «динь-дон»
    return np.concatenate([tone([659, 1318, 1977], .55, 2.5, .35), tone([523, 1046, 1569], 1.4, 2.0, .35)])

def audio():
    mix = np.zeros((int((T_END + 1) * SR), 2), np.float32)
    def put(x, at, g=1., fi=0., fo=0., until=None):
        x = x * g
        if until: x = x[:max(0, int((until - at) * SR))]
        if not len(x): return
        if fi: k = min(len(x), int(fi * SR)); x[:k] *= np.linspace(0, 1, k)[:, None]
        if fo: k = min(len(x), int(fo * SR)); x[-k:] *= np.linspace(1, 0, k)[:, None]
        s = int(at * SR); e = min(len(mix), s + len(x)); mix[s:e] += x[:e - s]
    def loop(x, a, b, g, fi=1., fo=1.):
        put(np.tile(x, (int((b - a) * SR / len(x)) + 2, 1)), a, g, fi, fo, until=b)
    # голоса
    for k, at, fx in LINES:
        src, (a, b, _) = (VO_NAR, NAR[k]) if k in NAR else (VO_MOM, MOM[k])
        put(load(os.path.join(VO, src + '.mp3'), max(0, a - .05), b - a + .35, FX[fx]), at - .05, 1.0, .02, .2)
    # атмосфера
    loop(sfx('rain'), 0, 3.4, .35, .3, .4)
    loop(sfx('drone_dread'), 0, 53.4, .28, 2, 1.5)
    put(vibro(), 3.5, .9); put(tone([880, 1320], .5, 6, .18), 3.55)                     # голосовое пришло
    put(sfx('click'), 7.95, .4)                                                          # нажал «плей»
    put(bell(), 21.8, .8); put(bell()[::1], 23.1, .8)                                    # звонок в дверь
    put(sfx('heart'), 26.4, .35); put(sfx('heart'), 30.0, .4)
    put(vibro(), 34.8, .9); put(tone([880, 1320], .5, 6, .18), 34.85)                    # телефон загорелся сам
    for dt in (0.0, .35, .7, 1.6, 1.9, 2.25, 3.2, 3.5, 3.85):                            # удары в дверь под «открой»
        put(sfx('knock'), 41.0 + dt, .7)
    for dt in (4.2, 4.6, 5.0): put(sfx('click'), 41.0 + dt, .9)                         # замок проворачивается
    put(sfx('jumpscare'), 46.9, 1.0); put(sfx('scare'), 46.9, .6)
    put(vibro(.45), 49.2, .7); put(sfx('tinnitus'), 48.7, .18, .1, 1.2, until=53.4)
    put(sfx('boom'), 53.4, .45)
    pk = np.abs(mix).max(); mix = mix / max(pk, 1e-6) * .9
    raw = os.path.join(TMP, 'a_raw.wav')
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-f', 'f32le', '-ar', str(SR), '-ac', '2', '-i', '-', raw], input=mix.tobytes(), check=True)
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', raw, '-af', 'loudnorm=I=-14:TP=-1.5:LRA=11', '-ar', str(SR), os.path.join(TMP, 'a.wav')], check=True)
    os.remove(raw)

if __name__ == '__main__':
    render(); audio()
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', os.path.join(TMP, 'v.mp4'), '-i', os.path.join(TMP, 'a.wav'), '-map', '0:v', '-map', '1:a',
                    '-c:v', 'copy', '-c:a', 'aac', '-b:a', '192k', '-shortest', '-movflags', '+faststart', OUT], check=True)
    if os.environ.get('AUDIO_OUT'):
        subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', os.path.join(TMP, 'a.wav'), '-c:a', 'aac', '-b:a', '192k', os.environ['AUDIO_OUT']], check=True)
    os.remove(os.path.join(TMP, 'v.mp4')); os.remove(os.path.join(TMP, 'a.wav'))
    print(OUT, T_END, 'с')
