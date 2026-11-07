# Страшная история «Девятый этаж» для Shorts (7 ноя): настоящие видеоклипы с Mixkit (бесплатная лицензия Mixkit Free License —
# можно в коммерческих роликах и на YouTube, без указания автора), свои подписи, звук, в конце — реклама Hush Till Six.
#   python3 tools/stories/stock_story.py CLIPS_DIR AD_CLIP.mp4 OUT.mp4
# CLIPS_DIR: 99834.mp4 (дождь на стекле), 3978.mp4 (ночь за окном), 100565.mp4 (фигура в окне), 3924.mp4 (тень руки машет),
#            45575.mp4 (молния над городом), 100563.mp4 (лицо прижато к стеклу), 41760.mp4 (лицо — скример)
import math, os, random, subprocess, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFont

CL, AD, OUT = sys.argv[1], sys.argv[2], sys.argv[3]
W, H, FPS = 1080, 1920, 30
ROOT = os.path.dirname(os.path.abspath(__file__)); SND = os.path.join(ROOT, '../../public/games/nightshift/snd')
TMP = os.path.dirname(os.path.abspath(OUT))
BONE, GOLD, RED = (236, 232, 220), (242, 194, 27), (232, 38, 46)
rnd = random.Random(3)

def ttf(name):   # шрифты UI (Oswald) — из woff в ttf
    p = os.path.join(TMP, name + '.ttf')
    if not os.path.exists(p):
        from fontTools.ttLib import TTFont
        f = TTFont(os.path.join(ROOT, '../../public/games/nightshift/fonts', name + '.woff')); f.flavor = None; f.save(p)
    return p
F_UI, F_LOGO = ttf('ns-ui'), ttf('ns-logo')
def font(sz, logo=False): return ImageFont.truetype(F_LOGO if logo else F_UI, sz)
def ease(x): x = max(0., min(1., x)); return x * x * (3 - 2 * x)

# ---------------------------------------------------------------- монтаж: (клип, с какой секунды, длительность, подписи [(с, по, текст)], обработка)
SEG = [
    ('99834', 2.0, 3.4, [(0, 3.4, 'КАЖДУЮ НОЧЬ РОВНО В 3:00 НАЧИНАЛСЯ ДОЖДЬ')], 'cold'),
    ('3978', 1.0, 3.6, [(0, 3.6, 'И ЗА ОКНОМ КТО-ТО ХОДИЛ ПО ДВОРУ')], 'cold'),
    ('3924', 2.0, 4.0, [(0, 4.0, 'А НА СТЕНЕ ПОЯВЛЯЛАСЬ ТЕНЬ. ОНА МАХАЛА МНЕ')], 'mono'),
    ('3978', 6.0, 2.8, [(0, 1.5, 'ВЧЕРА Я ПОМАХАЛ В ОТВЕТ'), (1.5, 2.8, 'И ЛАМПА ПОГАСЛА')], 'off'),
    ('45575', 1.2, 2.2, [(0, 2.2, 'ВСПЫШКА МОЛНИИ...')], 'storm'),
    ('100565', 1.0, 4.4, [(0, 2.2, 'ТЕНЬ БЫЛА НЕ МОЯ'), (2.2, 4.4, 'ОНО СТОЯЛО ЗА МОИМ ОКНОМ')], 'cold'),
    ('100563', 0.0, 4.4, [(0, 4.4, 'И ТЕПЕРЬ ОНО МАШЕТ МНЕ КАЖДУЮ НОЧЬ')], 'face'),
    ('41760', 3.0, 1.2, [], 'scare'),
]
CARDS = [('q', 2.4), ('ad', 3.8), ('logo', 3.8), ('cta', 3.0)]

def vf(kind, src_w, src_h, dur):
    """кадр 9:16 из клипа: вырезать середину по высоте, медленный наезд, цветокоррекция"""
    if src_w / src_h > W / H:   # горизонтальный: по высоте, середина
        ch = src_h; cw = int(ch * W / H) // 2 * 2
    else:
        cw = src_w; ch = int(cw * H / W) // 2 * 2
    z = f"scale={int(W*1.12)}:{int(H*1.12)}:flags=lanczos,crop={W}:{H}:'(in_w-{W})/2+{int(W*.06)}*sin(t/{dur}*1.57)':'(in_h-{H})/2-{int(H*.04)}*t/{dur}'"
    base = f"crop={cw}:{ch},{z}"
    g = {'cold': "eq=saturation=0.35:brightness=-0.04:contrast=1.12,colorbalance=bs=0.10:bm=0.06:rs=-0.05",
         'mono': "hue=s=0,eq=brightness=-0.18:contrast=1.5,colorbalance=bs=0.12:bm=0.08",
         'storm': "eq=saturation=0.3:gamma=2.2:brightness=0.05:contrast=1.1,colorbalance=bs=0.12:bm=0.08",
         'face': "eq=saturation=0.3:gamma=1.5:brightness=0.03:contrast=1.15,colorbalance=bs=0.08:bm=0.05",
         'off': "eq=saturation=0.3:brightness=-0.05,colorbalance=bs=0.1",
         'scare': "eq=saturation=0.6:contrast=1.3"}[kind]
    return f"{base},{g},fps={FPS}"

def frames(path, t0, dur, filt):
    p = subprocess.Popen(['ffmpeg', '-v', 'error', '-ss', str(t0), '-t', str(dur), '-i', path, '-vf', filt, '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-'], stdout=subprocess.PIPE)
    n = int(round(dur * FPS))
    for i in range(n):
        b = p.stdout.read(W * H * 3)
        if len(b) < W * H * 3: b = last if i else bytes(W * H * 3)
        last = b; yield np.frombuffer(b, np.uint8).reshape(H, W, 3)
    p.stdout.close(); p.wait()

def vignette():
    y, x = np.mgrid[0:H, 0:W]; d = np.sqrt(((x - W / 2) / (W * .65)) ** 2 + ((y - H / 2) / (H * .62)) ** 2)
    return np.clip(1.12 - d * .7, .3, 1.0)[..., None].astype(np.float32)
VIG = vignette()
GRAIN = [np.random.default_rng(i).normal(0, 7, (H // 2, W // 2, 1)).astype(np.float32).repeat(2, 0).repeat(2, 1) for i in range(5)]

def caption(img, text, a):
    if a <= 0 or not text: return img
    d = ImageDraw.Draw(img); f = font(70); words = text.split(); lines, cur = [], ''
    for w in words:
        if f.getlength((cur + ' ' + w).strip()) > 900: lines.append(cur); cur = w
        else: cur = (cur + ' ' + w).strip()
    lines.append(cur); y = 330
    for l in lines:
        d.text((W / 2, y), l, font=f, fill=tuple(int(c * a) for c in BONE), anchor='mm', stroke_width=7, stroke_fill=(0, 0, 0)); y += 88
    return img

def big(lines, t, sz=118):
    im = Image.new('RGB', (W, H)); d = ImageDraw.Draw(im); a = ease(t / .25); f = font(sz); y = H / 2 - (len(lines) - 1) * sz * .56
    for l in lines: d.text((W / 2, y), l, font=f, fill=tuple(int(c * a) for c in BONE), anchor='mm'); y += sz * 1.12
    return im

def logo(t):
    im = Image.new('RGB', (W, H)); d = ImageDraw.Draw(im); a = ease(t / .4); gl = rnd.random() < .07 and t > .6
    d.text((W / 2 + (10 if gl else 0), 720), 'HUSH', font=font(250, True), fill=tuple(int(c * a) for c in BONE), anchor='mm')
    d.text((W / 2, 925), 'TILL SIX', font=font(155, True), fill=tuple(int(c * a) for c in GOLD), anchor='mm')
    b = ease((t - .6) / .4)
    for i, (l, sz, c) in enumerate([('ХОРРОР ПРО ОДНУ НОЧНУЮ СМЕНУ', 54, BONE), ('ИГРАЙ БЕСПЛАТНО В БРАУЗЕРЕ · ITCH.IO', 46, GOLD), ('СКОРО В STEAM И GOOGLE PLAY', 40, (175, 172, 165))]):
        d.text((W / 2, 1140 + i * 82), l, font=font(sz), fill=tuple(int(v * b) for v in c), anchor='mm')
    return im

def cta(t):
    im = Image.new('RGB', (W, H)); d = ImageDraw.Draw(im); a = ease(t / .3); p = 1 + .03 * math.sin(t * 6)
    d.text((W / 2, 760), 'ПОДПИШИСЬ', font=font(int(135 * p)), fill=tuple(int(c * a) for c in RED), anchor='mm')
    d.text((W / 2, 905), 'YOUTUBE + TELEGRAM', font=font(66), fill=tuple(int(c * a) for c in BONE), anchor='mm')
    d.text((W / 2, 1015), '@YOMPIXGAMES', font=font(80), fill=tuple(int(c * a) for c in GOLD), anchor='mm')
    d.text((W / 2, 1115), 'НОВАЯ СТРАШНАЯ ИСТОРИЯ КАЖДУЮ НЕДЕЛЮ', font=font(42), fill=tuple(int(c * a * .85) for c in BONE), anchor='mm')
    return im

def main():
    enc = subprocess.Popen(['ffmpeg', '-v', 'error', '-y', '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-s', f'{W}x{H}', '-r', str(FPS), '-i', '-',
                            '-c:v', 'libx264', '-preset', 'medium', '-crf', '22', '-pix_fmt', 'yuv420p', os.path.join(TMP, 'v.mp4')], stdin=subprocess.PIPE)
    f = 0; marks = {}; T = 0.0
    for clip, t0, dur, caps, kind in SEG:
        path = os.path.join(CL, clip + '.mp4')
        sw, sh = map(int, subprocess.run(['ffprobe', '-v', 'error', '-select_streams', 'v', '-show_entries', 'stream=width,height', '-of', 'csv=p=0', path], capture_output=True, text=True).stdout.split(','))
        marks[clip + '@' + str(t0)] = T
        for i, fr in enumerate(frames(path, t0, dur, vf(kind, sw, sh, dur))):
            t = i / FPS; a = fr.astype(np.float32)
            if kind == 'off' and t > 1.5: a *= max(0.0, 1 - (t - 1.5) / .12)
            if kind == 'storm' and .7 < t < .95: a = a * .5 + 160 * (1 - abs(t - .8) / .15)   # вспышка   # окно гаснет — резко в темноту
            if kind == 'scare':
                sx, sy = rnd.randint(-28, 28), rnd.randint(-28, 28); a = np.roll(a, (sy, sx), (0, 1)) * (1.35 if t < .15 else 1.0)
                if t > .8: a *= max(0, 1 - (t - .8) / .35)
            a = a * VIG + GRAIN[f % 5]
            im = Image.fromarray(np.clip(a, 0, 255).astype(np.uint8))
            for c0, c1, txt in caps:
                if c0 <= t < c1: im = caption(im, txt, ease((t - c0) / .3) * ease((c1 - t) / .25))
            enc.stdin.write(im.tobytes()); f += 1
        T += dur
    ad = list(frames(AD, 20.0, 3.8, f"scale={W}:{H},fps={FPS}"))
    for cid, dur in CARDS:
        marks[cid] = T
        for i in range(int(round(dur * FPS))):
            t = i / FPS
            if cid == 'q': im = big(['А ТЫ БЫ', 'ПОМАХАЛ', 'В ОТВЕТ?'], t)
            elif cid == 'ad':
                im = Image.fromarray((ad[min(i, len(ad) - 1)].astype(np.float32) * .8).astype(np.uint8)); im = caption(im, 'ЛЮБИШЬ, КОГДА ПО-НАСТОЯЩЕМУ СТРАШНО? СЫГРАЙ', ease(t / .3))
            elif cid == 'logo': im = logo(t)
            else: im = cta(t)
            enc.stdin.write(im.tobytes()); f += 1
        T += dur
    enc.stdin.close(); enc.wait()
    audio(T, marks)
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', os.path.join(TMP, 'v.mp4'), '-i', os.path.join(TMP, 'a.wav'), '-map', '0:v', '-map', '1:a', '-c:v', 'copy', '-c:a', 'aac', '-b:a', '192k',
                    '-shortest', '-movflags', '+faststart', OUT], check=True)
    os.remove(os.path.join(TMP, 'v.mp4')); os.remove(os.path.join(TMP, 'a.wav'))
    print(OUT, round(T, 2), 'с')

SR = 48000
def snd(name, path=None, t0=0, dur=None):
    cmd = ['ffmpeg', '-v', 'error', '-ss', str(t0)] + (['-t', str(dur)] if dur else []) + ['-i', path or os.path.join(SND, name + '.mp3'), '-ac', '2', '-ar', str(SR), '-f', 'f32le', '-']
    return np.frombuffer(subprocess.run(cmd, capture_output=True).stdout, np.float32).reshape(-1, 2)

def audio(T, M):
    mix = np.zeros((int((T + 1) * SR), 2), np.float32)
    def put(x, at, g=1.0, fade_in=0.0, fade_out=0.0, until=None):
        x = x.copy() * g
        if until: x = x[:int((until - at) * SR)]
        n = len(x)
        if fade_in: k = min(n, int(fade_in * SR)); x[:k] *= np.linspace(0, 1, k)[:, None]
        if fade_out: k = min(n, int(fade_out * SR)); x[-k:] *= np.linspace(1, 0, k)[:, None]
        s = int(at * SR); e = min(len(mix), s + n); mix[s:e] += x[:e - s]
    story_end = M['q']
    rain = snd('rain'); r = np.tile(rain, (int(story_end * SR / len(rain)) + 2, 1))
    put(r, 0, .5, 1.0, 1.0, until=story_end - 0.0)
    dr = snd('drone_dread'); put(np.tile(dr, (int(story_end * SR / len(dr)) + 2, 1)), 0, .45, 2.0, 1.0, until=story_end)
    hb = snd('heart'); t = M['3924@2.0']; k = 0
    while t < M['3978@6.0']: put(hb, t, .5 + .05 * k, until=t + .9); t += max(.55, 1.05 - .08 * k); k += 1
    put(snd('click'), M['3978@6.0'] + 1.5, .8)                                          # окно гаснет
    put(snd('boom'), M['45575@1.2'] + .75, .6)                                           # гром на вспышке
    for i, dt in enumerate([.4, .75, 1.1, 2.0, 2.3]): put(snd('glass_tap'), M['100565@1.0'] + dt, .7)   # стук в стекло
    put(snd('glass_scratch'), M['100563@0.0'] + .3, .6)
    put(snd('whispers'), M['100563@0.0'] + 1.5, .5, until=M['41760@3.0'])
    put(snd('jumpscare'), M['41760@3.0'], 1.0); put(snd('scare'), M['41760@3.0'], .7)
    put(snd('tinnitus'), M['q'], .25, until=M['q'] + 2.3, fade_out=.6)
    put(snd(None, AD, 20.0, 3.8), M['ad'], .9, .1, .3)                                   # звук фрагмента игры
    put(snd('boom'), M['logo'], .7); put(snd('knock'), M['cta'] + .6, .5); put(snd('knock'), M['cta'] + 1.0, .5)
    pk = np.abs(mix).max(); mix = mix / max(pk, 1e-6) * .89
    raw = os.path.join(TMP, 'a_raw.wav')
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-f', 'f32le', '-ar', str(SR), '-ac', '2', '-i', '-', raw], input=mix.tobytes(), check=True)
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', raw, '-af', 'loudnorm=I=-14:TP=-1.5:LRA=11', '-ar', str(SR), os.path.join(TMP, 'a.wav')], check=True)
    os.remove(raw)

if __name__ == '__main__':
    main()
