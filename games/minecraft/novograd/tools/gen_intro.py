"""Заставка Yompix Games: эмблема, надписи, блики (PNG) и звук «вжух — удар — аккорд» (OGG)."""
import math, os, subprocess, wave
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'novograd')
GUI = os.path.join(ROOT, 'textures', 'gui', 'yompix')
os.makedirs(GUI, exist_ok=True)
BOLD = '/usr/share/fonts/opentype/inter/InterDisplay-Bold.otf'
PURPLE, CYAN, PINK = (138, 92, 255), (46, 230, 214), (255, 92, 170)


def grad(w, h, c1, c2, diag=True):
    y, x = np.mgrid[0:h, 0:w]
    k = ((x / w + y / h) / 2) if diag else y / h
    arr = np.zeros((h, w, 4), np.uint8)
    for i in range(3):
        arr[..., i] = (c1[i] * (1 - k) + c2[i] * k).astype(np.uint8)
    arr[..., 3] = 255
    return Image.fromarray(arr, 'RGBA')


def logo(size=512):
    """Геймпад-квадрат со скруглением и стилизованной Y; градиент фиолетовый→бирюзовый, внутреннее свечение."""
    s = size
    mask = Image.new('L', (s, s), 0)
    d = ImageDraw.Draw(mask)
    pad, r, th = 40, 96, 34
    d.rounded_rectangle([pad, pad, s - pad, s - pad], r, fill=255)
    d.rounded_rectangle([pad + th, pad + th, s - pad - th, s - pad - th], r - th, fill=0)
    # Y: две руки и ножка
    cx, cy = s // 2, s // 2 + 10
    w = 46
    d.line([(pad + 105, pad + 95), (cx, cy)], fill=255, width=w)
    d.line([(s - pad - 105, pad + 95), (cx, cy)], fill=255, width=w)
    d.line([(cx, cy), (cx, s - pad - 85)], fill=255, width=w)
    d.ellipse([cx - w // 2, cy - w // 2, cx + w // 2, cy + w // 2], fill=255)
    for (x, y) in [(pad + 105, pad + 95), (s - pad - 105, pad + 95), (cx, s - pad - 85)]:
        d.ellipse([x - w // 2, y - w // 2, x + w // 2, y + w // 2], fill=255)
    # кнопки геймпада по углам
    for (x, y, c) in [(pad + 70, s - pad - 70, PINK), (s - pad - 70, s - pad - 70, CYAN)]:
        d.ellipse([x - 16, y - 16, x + 16, y + 16], fill=255)
    body = grad(s, s, PURPLE, CYAN)
    out = Image.new('RGBA', (s, s), (0, 0, 0, 0))
    out.paste(body, (0, 0), mask)
    # блик сверху
    hl = Image.new('L', (s, s), 0)
    ImageDraw.Draw(hl).ellipse([pad - 60, pad - 200, s - pad + 60, s // 2 - 20], fill=90)
    hl = Image.fromarray(np.minimum(np.array(hl), np.array(mask)))
    white = Image.new('RGBA', (s, s), (255, 255, 255, 255))
    out = Image.composite(Image.blend(out, white, 0.35), out, hl)
    out.save(os.path.join(GUI, 'logo.png'))
    # свечение: размытая маска, отдельной текстурой (рисуется аддитивно)
    glow = mask.filter(ImageFilter.GaussianBlur(26))
    g = grad(s, s, PURPLE, CYAN)
    g.putalpha(glow)
    g.save(os.path.join(GUI, 'logo_glow.png'))


def word(text, name, size, color=(255, 255, 255), spacing=0):
    f = ImageFont.truetype(BOLD, size)
    widths = [f.getbbox(ch)[2] for ch in text]
    w = sum(widths) + spacing * (len(text) - 1) + 40
    h = int(size * 1.35)
    img = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    x = 20
    for ch, cw in zip(text, widths):
        d.text((x, size * 0.1), ch, font=f, fill=color + (255,))
        x += cw + spacing
    img.save(os.path.join(GUI, name + '.png'))
    print(name, img.size, 'letters', [20 + sum(widths[:i]) + spacing * i for i in range(len(text) + 1)])


def flare():
    s = 256
    y, x = np.mgrid[0:s, 0:s]
    r = np.sqrt((x - s / 2) ** 2 + (y - s / 2) ** 2) / (s / 2)
    a = np.clip(1 - r, 0, 1) ** 2.2
    img = np.zeros((s, s, 4), np.uint8)
    img[..., :3] = 255
    img[..., 3] = (a * 255).astype(np.uint8)
    Image.fromarray(img, 'RGBA').save(os.path.join(GUI, 'flare.png'))
    # кольцо ударной волны
    ring = np.clip(1 - np.abs(r - 0.85) / 0.08, 0, 1) ** 1.5
    img[..., 3] = (ring * 255).astype(np.uint8)
    Image.fromarray(img, 'RGBA').save(os.path.join(GUI, 'ring.png'))
    # горизонтальный световой штрих
    w, h = 512, 32
    y, x = np.mgrid[0:h, 0:w]
    a = np.clip(1 - np.abs(y - h / 2) / (h / 2), 0, 1) ** 3 * np.clip(1 - np.abs(x - w / 2) / (w / 2), 0, 1) ** 0.8
    img = np.zeros((h, w, 4), np.uint8)
    img[..., :3] = 255
    img[..., 3] = (a * 255).astype(np.uint8)
    Image.fromarray(img, 'RGBA').save(os.path.join(GUI, 'streak.png'))


def sound():
    sr = 44100
    T = 6.0
    t = np.arange(int(sr * T)) / sr
    out = np.zeros_like(t)
    rng = np.random.default_rng(3)
    # 1) вжух: шум, фильтр открывается, громкость растёт до удара в 1.45 с
    n = rng.standard_normal(len(t))
    riser = np.zeros_like(t)
    idx = t < 1.45
    k = t[idx] / 1.45
    # простой резонансный фильтр с растущей частотой
    y1 = y2 = 0.0
    seg = np.zeros(idx.sum())
    for i, (x, kk) in enumerate(zip(n[idx], k)):
        f = 200 + 5000 * kk ** 2
        a = math.exp(-2 * math.pi * f / sr)
        y1 = (1 - a) * x + a * y1
        seg[i] = y1
    riser[idx] = seg * (k ** 2.5) * 3.0
    riser[idx] += np.sin(2 * np.pi * (80 + 600 * k ** 3) * t[idx]) * 0.15 * k ** 2
    out += riser
    # 2) удар: низкий «бум» с падающей частотой + щелчок
    hit = 1.45
    m = t >= hit
    tt = t[m] - hit
    boom = np.sin(2 * np.pi * (35 + 90 * np.exp(-tt * 18)) * tt) * np.exp(-tt * 2.8) * 1.1
    click = rng.standard_normal(m.sum()) * np.exp(-tt * 60) * 0.5
    out[m] += boom + click
    # 3) аккорд-«мерцание» (до-мажор с септимой) с медленным затуханием и вибрато
    for f, g in [(261.6, 0.18), (329.6, 0.14), (392.0, 0.14), (493.9, 0.10), (523.3, 0.12), (1046.5, 0.05)]:
        vib = 1 + 0.003 * np.sin(2 * np.pi * 5 * tt)
        tone = np.sin(2 * np.pi * f * vib * tt) + 0.3 * np.sin(2 * np.pi * 2 * f * tt)
        env = (1 - np.exp(-tt * 30)) * np.exp(-tt * 0.7)
        out[m] += tone * env * g
    # 4) блестящие «искры» — высокие пинги
    for i in range(14):
        st = hit + 0.1 + i * 0.13
        mm = t >= st
        q = t[mm] - st
        f = [2093, 2637, 3136, 2349][i % 4]
        out[mm] += np.sin(2 * np.pi * f * q) * np.exp(-q * 9) * 0.05
    out = np.tanh(out * 1.3) * 0.85
    # стерео: лёгкая задержка правого канала для объёма
    d = int(sr * 0.012)
    right = np.concatenate([np.zeros(d), out[:-d]])
    st = np.stack([out, right * 0.95], axis=1)
    pcm = (st * 32767).astype(np.int16)
    tmp = '/tmp/yompix_intro.wav'
    with wave.open(tmp, 'wb') as w:
        w.setnchannels(2); w.setsampwidth(2); w.setframerate(sr); w.writeframes(pcm.tobytes())
    dst = os.path.join(ROOT, 'sounds', 'yompix_intro.ogg')
    # моно для Minecraft (стерео-звуки не позиционируются, но для интерфейса годится и так)
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', tmp, '-ac', '1', '-c:a', 'libvorbis', '-q:a', '6', dst], check=True)
    print('звук', dst)


logo()
word('YOMPIX', 'word_yompix', 120, spacing=6)
word('GAMES', 'word_games', 48, color=(46, 230, 214), spacing=30)
flare()
sound()
