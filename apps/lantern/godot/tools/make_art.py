# Процедурная графика «Играем вместе»: свои пиксельные кубы (без ассетов Mojang).
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

# --- иконка приложения
S = 256*SS
img = Image.new("RGBA", (S, S), (0,0,0,0)); d = ImageDraw.Draw(img)
d.rounded_rectangle([8*SS, 8*SS, S-8*SS, S-8*SS], radius=56*SS, fill=(22,26,36))
d.rounded_rectangle([8*SS, 8*SS, S-8*SS, S-8*SS], radius=56*SS, outline=(76,201,110), width=6*SS)
cube(d, S*0.5, S*0.86, 104*SS, g_top, g_side, tex("grass_side",21))
img.resize((256,256), Image.LANCZOS).save(os.path.join(OUT, "icon.png"))
img.resize((256,256), Image.LANCZOS).save(os.path.join(OUT, "..", "..", "icon.png"))
img.resize((256,256), Image.LANCZOS).save(os.path.join(OUT, "..", "..", "icon.ico"), sizes=[(16,16),(32,32),(48,48),(64,64),(128,128),(256,256)])

# --- маленькие кубики для заголовков (32px)
for name, t in [("mini_grass", (g_top, g_side, tex("grass_side",31))), ("mini_stone", (tex("deep",32),tex("deep",33),tex("deep",34)))]:
    S = 96*SS; img = Image.new("RGBA", (S,S), (0,0,0,0)); d = ImageDraw.Draw(img)
    cube(d, S*0.5, S*0.95, 46*SS, *t)
    img.resize((96,96), Image.LANCZOS).save(os.path.join(OUT, name + ".png"))
print("ok")
