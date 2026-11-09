# Процедурная графика LANTERN: свои пиксельные кубы (без ассетов Mojang).
import math, random, sys, os
from PIL import Image, ImageDraw, ImageFilter
OUT = sys.argv[1] if len(sys.argv) > 1 else "assets/img"
os.makedirs(OUT, exist_ok=True)
SS = 4

def tex(kind, seed):
    r = random.Random(seed); t = [[None]*16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            n = r.random()
            if kind == "grass_top":
                base = [(94,168,62),(108,184,70),(84,152,56),(120,196,80)]
                t[y][x] = base[int(n*4)]
            elif kind == "grass_side":
                edge = 3 + (1 if r.random() < .5 else 0) + (1 if (x*7) % 5 == 0 else 0)
                if y < edge: t[y][x] = [(94,168,62),(108,184,70),(84,152,56)][int(n*3)]
                else: t[y][x] = [(134,94,62),(150,106,70),(118,82,54),(160,116,78)][int(n*4)]
            elif kind == "stone":
                t[y][x] = [(112,116,128),(98,102,114),(128,132,144),(84,88,100)][int(n*4)]
            elif kind == "deep":
                t[y][x] = [(52,56,70),(70,74,90),(40,42,54),(88,92,110)][int(n*4)]
            elif kind == "gem":
                t[y][x] = [(112,116,128),(98,102,114),(128,132,144)][int(n*3)]
    if kind == "gem":
        for (gx, gy) in [(3,3),(10,5),(5,10),(11,11)]:
            for dx in range(2):
                for dy in range(2):
                    t[gy+dy][gx+dx] = (90,230,220) if (dx+dy)%2==0 else (40,180,190)
    return t

def shade(c, k): return tuple(max(0, min(255, int(v*k))) for v in c)

def cube(d, ox, oy, s, top, left, right):
    c30, s30 = math.cos(math.pi/6), math.sin(math.pi/6)
    ex = (s*c30, -s*s30); ey = (-s*c30, -s*s30); ez = (0, -s)
    def P(a, b, c):  # a по ex, b по ey, c по ez (0..1)
        return (ox + ex[0]*a + ey[0]*b + ez[0]*c, oy + ex[1]*a + ey[1]*b + ez[1]*c)
    n = 16
    for i in range(n):
        for j in range(n):
            # верх: z=1, a=i, b=j
            a0, a1, b0, b1 = i/n, (i+1)/n, j/n, (j+1)/n
            d.polygon([P(a0,b0,1),P(a1,b0,1),P(a1,b1,1),P(a0,b1,1)], fill=shade(top[j][i], 1.0))
            # правая грань: b=0, a и z
            z0, z1 = 1-(j+1)/n, 1-j/n
            d.polygon([P(a0,0,z0),P(a1,0,z0),P(a1,0,z1),P(a0,0,z1)], fill=shade(right[j][i], 0.78))
            # левая грань: a=0, b и z
            d.polygon([P(0,a0,z0),P(0,a1,z0),P(0,a1,z1),P(0,a0,z1)], fill=shade(left[j][i], 0.6))

def soft_shadow(img, cx, cy, w, h, alpha=110):
    sh = Image.new("RGBA", img.size, (0,0,0,0)); sd = ImageDraw.Draw(sh)
    sd.ellipse([cx-w, cy-h, cx+w, cy+h], fill=(0,0,0,alpha))
    sh = sh.filter(ImageFilter.GaussianBlur(12*SS))
    return Image.alpha_composite(img, sh)

def save(img, name, size):
    img.resize(size, Image.LANCZOS).save(os.path.join(OUT, name))

# --- Java: стопка травяных блоков
W, H = 440*SS, 320*SS
img = Image.new("RGBA", (W, H), (0,0,0,0))
img = soft_shadow(img, W//2, int(H*0.86), 170*SS, 26*SS)
d = ImageDraw.Draw(img)
g_top, g_side = tex("grass_top", 1), tex("grass_side", 2)
cube(d, W*0.36, H*0.90, 92*SS, tex("grass_top",3), tex("grass_side",4), tex("grass_side",5))
cube(d, W*0.64, H*0.86, 80*SS, tex("stone",6), tex("stone",7), tex("stone",8))
cube(d, W*0.50, H*0.60, 86*SS, g_top, g_side, tex("grass_side",9))
save(img, "card_java.png", (440, 320))

# --- Bedrock: тёмный камень + кристаллы + телефон
img = Image.new("RGBA", (W, H), (0,0,0,0))
img = soft_shadow(img, W//2, int(H*0.86), 170*SS, 26*SS)
d = ImageDraw.Draw(img)
cube(d, W*0.40, H*0.90, 92*SS, tex("deep",11), tex("deep",12), tex("deep",13))
cube(d, W*0.40, H*0.90 - 92*SS, 92*SS, tex("gem",14), tex("gem",15), tex("gem",16))
# телефон
px, py, pw, ph = int(W*0.62), int(H*0.30), 120*SS, 210*SS
d.rounded_rectangle([px, py, px+pw, py+ph], radius=20*SS, fill=(28,30,40), outline=(90,96,120), width=4*SS)
d.rounded_rectangle([px+10*SS, py+22*SS, px+pw-10*SS, py+ph-26*SS], radius=8*SS, fill=(60,140,220))
d.ellipse([px+pw-48*SS, py+36*SS, px+pw-24*SS, py+60*SS], fill=(255,226,120))
for cx0, cy0 in [(px+22*SS, py+70*SS), (px+50*SS, py+100*SS)]:
    d.rectangle([cx0, cy0, cx0+36*SS, cy0+10*SS], fill=(235,242,255))
cube(d, px+pw*0.5, py+ph-26*SS-28*SS, 22*SS, tex("grass_top",41), tex("grass_side",42), tex("grass_side",43))
d.rectangle([px+10*SS, py+ph-26*SS-28*SS, px+pw-10*SS, py+ph-26*SS-18*SS], fill=(108,184,70))
d.rectangle([px+10*SS, py+ph-26*SS-18*SS, px+pw-10*SS, py+ph-26*SS], fill=(134,94,62))
d.ellipse([px+pw//2-6*SS, py+ph-18*SS, px+pw//2+6*SS, py+ph-6*SS], fill=(90,96,120))
save(img, "card_bedrock.png", (440, 320))

# --- иконка приложения: светящийся кубический фонарь (LANTERN)
def lantern_tex(face_light):
    t = [[None]*16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            edge = x < 2 or x > 13 or y < 2 or y > 13 or (y in (5,) and 1 < x < 14)
            bar = False
            if edge:
                t[y][x] = (58, 52, 50) if (x + y) % 3 else (74, 66, 60)
            elif bar:
                t[y][x] = (66, 58, 54)
            else:
                d = ((x - 7.5) ** 2 + (y - 8) ** 2) ** 0.5
                k = max(0.0, 1 - d / 9) * face_light
                t[y][x] = (min(255, int(255 * (0.85 + 0.15 * k))), min(255, int(150 + 95 * k)), min(255, int(40 + 140 * k * k)))
    return t
def cap_tex():
    return [[(46, 42, 42) if (x + y) % 4 else (62, 56, 52) for x in range(16)] for y in range(16)]
def draw_lantern(S, scale=1.0, bg=True):
    img = Image.new("RGBA", (S, S), (0,0,0,0)); d = ImageDraw.Draw(img)
    if bg:
        d.rounded_rectangle([int(S*0.03), int(S*0.03), int(S*0.97), int(S*0.97)], radius=int(S*0.22), fill=(18, 19, 28))
    glow = Image.new("RGBA", (S, S), (0,0,0,0)); gd = ImageDraw.Draw(glow)
    cx, cy = S*0.5, S*0.56
    for i in range(30, 0, -1):
        r = S*0.46*i/30
        gd.ellipse([cx-r, cy-r, cx+r, cy+r], fill=(255, 150, 40, int(3 + (30-i)*3.6)))
    glow = glow.filter(ImageFilter.GaussianBlur(S*0.03))
    if bg:
        mask = Image.new("L", (S, S), 0); ImageDraw.Draw(mask).rounded_rectangle([int(S*0.03), int(S*0.03), int(S*0.97), int(S*0.97)], radius=int(S*0.22), fill=255)
        glow.putalpha(Image.composite(glow.split()[3], Image.new("L", (S,S), 0), mask))
    img = Image.alpha_composite(img, glow); d = ImageDraw.Draw(img)
    s = S*0.30*scale
    base_y = cy + S*0.5*s/S*1.0 + s*0.45
    cube(d, cx, base_y, s, lantern_tex(1.0), lantern_tex(0.8), lantern_tex(0.95))
    top_c = base_y - 1.5*s
    c = s*0.42
    co = top_c + 0.5*c
    cube(d, cx, co, c, cap_tex(), cap_tex(), cap_tex())
    cap_top = co - 1.5*c
    hr = c*0.55
    d.arc([cx-hr, cap_top-hr*1.6, cx+hr, cap_top+hr*0.4], 180, 360, fill=(92, 82, 74), width=max(2, int(S*0.02)))
    return img
S = 256*SS
icon = draw_lantern(S)
icon.resize((256,256), Image.LANCZOS).save(os.path.join(OUT, "icon.png"))
icon.resize((256,256), Image.LANCZOS).save(os.path.join(OUT, "..", "..", "icon.png"))
icon.resize((256,256), Image.LANCZOS).save(os.path.join(OUT, "..", "..", "icon.ico"), sizes=[(16,16),(32,32),(48,48),(64,64),(128,128),(256,256)])
big = draw_lantern(512*SS)
big.resize((512,512), Image.LANCZOS).save(os.path.join(OUT, "icon_512.png"))
# Android adaptive: передний план без фона (432x432, сам фонарь в центре 66%), фон — сплошной
fg = draw_lantern(432*SS, 0.8, bg=False)
fg.resize((432,432), Image.LANCZOS).save(os.path.join(OUT, "android_fg.png"))
Image.new("RGBA", (432,432), (18,19,28,255)).save(os.path.join(OUT, "android_bg.png"))
g_top, g_side = tex("grass_top", 1), tex("grass_side", 2)

# --- маленькие кубики для заголовков (32px)
for name, t in [("mini_grass", (g_top, g_side, tex("grass_side",31))), ("mini_stone", (tex("deep",32),tex("deep",33),tex("deep",34)))]:
    S = 96*SS; img = Image.new("RGBA", (S,S), (0,0,0,0)); d = ImageDraw.Draw(img)
    cube(d, S*0.5, S*0.95, 46*SS, *t)
    img.resize((96,96), Image.LANCZOS).save(os.path.join(OUT, name + ".png"))
print("ok")
