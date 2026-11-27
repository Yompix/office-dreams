package com.yompix.novograd.client.style;

import com.mojang.blaze3d.systems.RenderSystem;
import com.yompix.novograd.Novograd;
import com.yompix.novograd.life.Appearance;
import com.yompix.novograd.life.StyleCatalog;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 0.9.6: скин 128×128 (раскладка скина игрока 64×64, вдвое подробнее — модели берут UV в долях, так что и игрок, и
 * житель на GeckoLib читают его как обычный) из внешности: кожа, лицо (глаза с радужкой, ресницы, брови, губы,
 * веснушки, родинки, шрамы, макияж, пирсинг), тату, ногти; слоями — низ, обувь, верх (узор, заправка, закатанные
 * рукава), куртка поверх (распахнута/застёгнута), часы; потёртость/грязь — поверх одежды.
 *
 * <p>Одинаковая внешность ({@link Appearance#key}) — одна текстура; кэш до {@link #MAX} текстур, новых — не больше
 * {@link #PER_FRAME} за кадр (остальные ждут кадр-другой с обычным скином), старые освобождаются.
 */
public final class SkinComposer {
    private SkinComposer() {}

    public static final int MAX = 384, PER_FRAME = 3;
    private static final Map<String, Identifier> CACHE = new LinkedHashMap<>(64, 0.75f, true);
    private static int counter, frameBudget = PER_FRAME;
    private static long frameMark = -1;
    /** Для замера: сколько собрано и сколько времени ушло. */
    public static int built;
    public static long builtNanos;

    /** Текстура для внешности (только на потоке отрисовки; иначе или сверх лимита кадра — null: пока обычный скин). */
    public static Identifier texture(Appearance a) {
        return texture(a.key(), a);
    }

    public static Identifier texture(String key, Appearance a) {
        Identifier id = CACHE.get(key);
        if (id != null) return id;
        if (!RenderSystem.isOnRenderThread()) return null;
        long frame = System.nanoTime() / 16_000_000L;
        if (frame != frameMark) {
            frameMark = frame;
            frameBudget = PER_FRAME;
        }
        if (frameBudget <= 0) return null;
        frameBudget--;
        long t0 = System.nanoTime();
        id = Novograd.id("dynskin/" + (counter++));
        MinecraftClient.getInstance().getTextureManager().registerTexture(id, new NativeImageBackedTexture(compose(a)));
        builtNanos += System.nanoTime() - t0;
        built++;
        CACHE.put(key, id);
        if (CACHE.size() > MAX) {
            var it = CACHE.entrySet().iterator();
            Identifier old = it.next().getValue();
            it.remove();
            MinecraftClient.getInstance().getTextureManager().destroyTexture(old);
        }
        return id;
    }

    /** Уже собранная текстура (без сборки). */
    public static Identifier peek(String key) {
        return CACHE.get(key);
    }

    public static int cached() {
        return CACHE.size();
    }

    // ================================================================== холст
    static final int R = 2, SIZE = 64 * R;

    record Rg(int x, int y, int w, int h) {
        int hw() {
            return w * R;
        }

        int hh() {
            return h * R;
        }
    }

    /** Грани коробки в раскладке скина (в координатах 64×64). */
    record Bx(Rg top, Rg bottom, Rg right, Rg front, Rg left, Rg back) {
        Rg[] sides() {
            return new Rg[]{right, front, left, back};
        }

        Rg[] all() {
            return new Rg[]{top, bottom, right, front, left, back};
        }
    }

    /** Цвет пикселя по глобальным координатам 128×128 (ткань, узор, кожа). */
    interface Paint {
        int at(int gx, int gy);
    }

    static final class P {
        final NativeImage img = new NativeImage(SIZE, SIZE, true);
        /** 1 — одежда (на неё ложится грязь), 2 — кожа. */
        final byte[] mask = new byte[SIZE * SIZE];

        P() {
            img.fillRect(0, 0, SIZE, SIZE, 0);
        }

        void set(int gx, int gy, int argb, int m) {
            if (gx < 0 || gy < 0 || gx >= SIZE || gy >= SIZE) return;
            int a = argb >>> 24, r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
            img.setColor(gx, gy, a << 24 | b << 16 | g << 8 | r);
            mask[gy * SIZE + gx] = (byte) (a == 0 ? 0 : m);
        }

        int get(int gx, int gy) {
            int c = img.getColor(gx, gy);
            return (c & 0xFF000000) | (c & 255) << 16 | (c & 0xFF00) | ((c >> 16) & 255);
        }

        static Bx regions(int u, int v, int w, int h, int d) {
            return new Bx(new Rg(u + d, v, w, d), new Rg(u + d + w, v, w, d), new Rg(u, v + d, d, h), new Rg(u + d, v + d, w, h),
                    new Rg(u + d + w, v + d, d, h), new Rg(u + d + w + d, v + d, w, h));
        }

        /** Прямоугольник в пикселях высокого разрешения внутри грани (включительно). */
        void h(Rg r, int x0, int y0, int x1, int y1, Paint p, int m) {
            for (int y = Math.max(0, y0); y <= Math.min(y1, r.hh() - 1); y++)
                for (int x = Math.max(0, x0); x <= Math.min(x1, r.hw() - 1); x++) {
                    int gx = r.x * R + x, gy = r.y * R + y;
                    set(gx, gy, p.at(gx, gy), m);
                }
        }

        void all(Rg r, Paint p, int m) {
            h(r, 0, 0, r.hw() - 1, r.hh() - 1, p, m);
        }

        /** Точный пиксель без «шума». */
        void d(Rg r, int x, int y, int col) {
            if (x >= 0 && y >= 0 && x < r.hw() && y < r.hh()) set(r.x * R + x, r.y * R + y, col, mask[(r.y * R + y) * SIZE + r.x * R + x]);
        }

        void dm(Rg r, int x, int y, int col, int m) {
            if (x >= 0 && y >= 0 && x < r.hw() && y < r.hh()) set(r.x * R + x, r.y * R + y, col, m);
        }

        int at(Rg r, int x, int y) {
            return get(r.x * R + Math.min(x, r.hw() - 1), r.y * R + Math.min(y, r.hh() - 1));
        }

        /** Ряды y0..y1 (высокое разрешение) на всех четырёх боках. */
        void rows(Bx b, int y0, int y1, Paint p, int m) {
            for (Rg r : b.sides()) h(r, 0, y0, r.hw() - 1, y1, p, m);
        }

        void box(Bx b, Paint p, int m) {
            for (Rg r : b.all()) all(r, p, m);
        }

        void clear(Rg r, int x0, int y0, int x1, int y1) {
            h(r, x0, y0, x1, y1, (x, y) -> 0, 0);
        }
    }

    // ================================================================== цвета
    static int hash(int x, int y, int seed) {
        int h = x * 374761393 + y * 668265263 + seed * 1274126177;
        h = (h ^ (h >>> 13)) * 1274126177;
        return (h ^ (h >>> 16)) & 0x7FFFFFFF;
    }

    static int shade(int c, float k) {
        int a = c >>> 24;
        int r = Math.min(255, Math.max(0, (int) (((c >> 16) & 255) * k))), g = Math.min(255, Math.max(0, (int) (((c >> 8) & 255) * k))),
                b = Math.min(255, Math.max(0, (int) ((c & 255) * k)));
        return a << 24 | r << 16 | g << 8 | b;
    }

    static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t), g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t),
                bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }

    static int op(int rgb) {
        return 0xFF000000 | rgb;
    }

    static int lum(int c) {
        return (((c >> 16) & 255) * 3 + ((c >> 8) & 255) * 6 + (c & 255)) / 10;
    }

    /** Контрастная отделка: на светлом — темнее, на тёмном — светлее. */
    static int trim(int c, float k) {
        return lum(c) > 140 ? shade(c, 1 - k) : mix(c, 0xFFFFFFFF, k);
    }

    /** Ткань: едва заметный шум нитей. */
    static Paint cloth(int c) {
        return (x, y) -> shade(c, 1 + ((hash(x, y, 3) % 9) - 4) / 220f);
    }

    /** Кожа: мягкий шум без «пикселей». */
    static Paint skinP(int c) {
        return (x, y) -> shade(c, 1 + ((hash(x >> 1, y >> 1, 9) % 5) - 2) / 260f);
    }

    static Paint flat(int c) {
        return (x, y) -> c;
    }

    /** Узор верха: полоска, клетка, камуфляж, принт. */
    static Paint pattern(int c, int kind) {
        return switch (kind) {
            case 1 -> (x, y) -> ((y >> 1) % 3 == 0) ? trim(c, 0.28f) : shade(c, 1 + ((hash(x, y, 3) % 7) - 3) / 240f);
            case 2 -> (x, y) -> {
                boolean vx = (x >> 1) % 4 == 0, hy = (y >> 1) % 4 == 0;
                return vx && hy ? shade(c, 0.55f) : vx || hy ? shade(c, 0.78f) : shade(c, 1 + ((hash(x, y, 3) % 7) - 3) / 240f);
            };
            case 3 -> (x, y) -> {
                int h = hash(x / 5, y / 4, 7) % 6;
                return h == 0 ? shade(c, 0.62f) : h == 1 ? mix(c, 0xFF3A2A18, 0.45f) : h == 2 ? shade(c, 1.25f) : c;
            };
            case 4 -> (x, y) -> {
                int h = hash(x, y, 21) % 37;
                return h == 0 || h == 9 ? trim(c, 0.55f) : h == 17 ? 0xFFF2C230 : h == 25 ? mix(c, 0xFFE0407A, 0.7f) : shade(c, 1 + ((hash(x, y, 3) % 7) - 3) / 240f);
            };
            default -> cloth(c);
        };
    }

    // ================================================================== сборка
    /** Части тела на холсте. */
    static final class Body {
        Bx head, hat, body, bodyO;
        Bx[] arms, armsO, legs, legsO;
        int aw;
    }

    public static NativeImage compose(Appearance a) {
        P p = new P();
        Body b = new Body();
        b.aw = a.slim ? 3 : 4;
        b.head = P.regions(0, 0, 8, 8, 8);
        b.hat = P.regions(32, 0, 8, 8, 8);
        b.body = P.regions(16, 16, 8, 12, 4);
        b.bodyO = P.regions(16, 32, 8, 12, 4);
        b.arms = new Bx[]{P.regions(40, 16, b.aw, 12, 4), P.regions(32, 48, b.aw, 12, 4)};
        b.armsO = new Bx[]{P.regions(40, 32, b.aw, 12, 4), P.regions(48, 48, b.aw, 12, 4)};
        b.legs = new Bx[]{P.regions(0, 16, 4, 12, 4), P.regions(16, 48, 4, 12, 4)};
        b.legsO = new Bx[]{P.regions(0, 32, 4, 12, 4), P.regions(0, 48, 4, 12, 4)};
        int S = op(StyleCatalog.tone(a.skin));
        int H = op(StyleCatalog.hairColor(a.hairColor));
        // кожа везде
        Paint sk = skinP(S);
        p.box(b.head, sk, 2);
        p.box(b.body, sk, 2);
        for (Bx x : b.arms) p.box(x, sk, 2);
        for (Bx x : b.legs) p.box(x, sk, 2);
        // лёгкая тень: шея под подбородком, колени, локти
        p.h(b.body.front, 5, 0, 10, 0, flat(shade(S, 0.9f)), 2);
        for (Bx l : b.legs) p.rows(l, 11, 11, (x, y) -> shade(S, 0.96f), 2);
        face(p, b, a, S, H);
        tattoos(p, b, a);
        nails(p, b, a, S);
        String top = Appearance.id(a.top), pants = Appearance.id(a.pants), shoes = Appearance.id(a.shoes), outer = Appearance.id(a.outer);
        int tc = color(a.top, 0xF2F2F2), pc = color(a.pants, 0x2F4A7A), sc = color(a.shoes, 0xF0F0F0);
        if (top.equals("coverall")) {
            pants = "work_pants";
            pc = tc;
        }
        boolean bareLegs = StyleCatalog.dress(top) || top.equals("swimsuit");
        if (!bareLegs) pants(p, b, pants, pc, S);
        else bareLegs(p, b, top, tc, S);
        shoes(p, b, shoes, sc, S);
        top(p, b, a, top, tc, S);
        if (!outer.isEmpty() && !top.equals("swimsuit")) outer(p, b, a, outer, color(a.outer, 0x161616), top);
        wrist(p, b, a);
        if (a.slim) waist(p, b);
        grime(p, a.grime);
        hair(p, b, a, S, H);
        return p.img;
    }

    private static int color(String ref, int def) {
        var w = StyleCatalog.wear(Appearance.id(ref));
        return op(w == null ? def : w.color(Appearance.colorIndex(ref)));
    }

    // ================================================================== лицо
    private static void face(P p, Body b, Appearance a, int S, int H) {
        Rg F = b.head.front;
        boolean dark = a.skin >= 6;
        // ---- скулы, нос, подбородок
        p.h(F, 0, 12, 0, 15, flat(shade(S, 0.95f)), 2);
        p.h(F, 15, 12, 15, 15, flat(shade(S, 0.95f)), 2);
        p.h(F, 3, 15, 12, 15, flat(shade(S, 0.94f)), 2);
        p.d(F, 7, 10, shade(S, 0.93f));
        p.d(F, 8, 10, shade(S, 0.95f));
        p.d(F, 6, 11, shade(S, 0.84f));
        p.d(F, 9, 11, shade(S, 0.84f));
        p.d(F, 7, 11, shade(S, 1.03f));
        p.d(F, 8, 11, shade(S, 1.01f));
        // ---- брови
        // брови — натуральные: у крашеных (неон, вишня, бирюза) и седых — тёмно-русые/свои
        boolean dyed = a.hairColor >= 7 && a.hairColor <= 10 || a.hairColor >= 15;
        int browBase = a.hair.equals("bald") || dyed ? mix(S, 0xFF2A1A10, 0.6f) : H;
        int brow = shade(browBase, lum(browBase) > 170 ? 0.72f : 0.78f);
        int[] lb, rb;
        switch (a.brows) {
            case 0 -> { lb = new int[]{3, 6, 5, 6}; rb = new int[]{10, 6, 12, 6}; }
            case 2 -> { lb = new int[]{2, 5, 5, 6}; rb = new int[]{10, 5, 13, 6}; }
            case 3 -> { lb = new int[]{2, 6, 5, 6}; rb = new int[]{10, 6, 13, 6}; }
            case 4 -> { lb = new int[]{1, 5, 6, 6}; rb = new int[]{9, 5, 14, 6}; }
            default -> { lb = new int[]{2, 6, 5, 6}; rb = new int[]{10, 6, 13, 6}; }
        }
        p.h(F, lb[0], lb[1], lb[2], lb[3], flat(brow), 2);
        p.h(F, rb[0], rb[1], rb[2], rb[3], flat(brow), 2);
        if (a.brows == 3) { // дугой: середина выше
            p.d(F, 3, 5, brow);
            p.d(F, 4, 5, brow);
            p.d(F, 11, 5, brow);
            p.d(F, 12, 5, brow);
            p.d(F, 2, 6, mix(brow, S, 0.5f));
            p.d(F, 13, 6, mix(brow, S, 0.5f));
        }
        if (a.brows == 1) {
            p.d(F, 5, 6, mix(brow, S, 0.35f));
            p.d(F, 10, 6, mix(brow, S, 0.35f));
        }
        // ---- глаза: белок, радужка (2×2) с тёмным зрачком и бликом, складка века
        int iris = op(StyleCatalog.EYE_COLORS.get(a.eyeColor).color());
        int white = mix(0xFFF6F2EC, S, 0.12f), whiteLow = mix(white, S, 0.3f);
        int crease = shade(S, dark ? 0.8f : 0.86f);
        for (int side = 0; side < 2; side++) {
            int x0 = side == 0 ? 2 : 10, ix = side == 0 ? 3 : 11; // ix — левый столбец радужки
            p.h(F, x0, 7, x0 + 3, 7, flat(crease), 2);
            switch (a.eyes) {
                case 2 -> { // узкие: одна строка
                    p.d(F, x0, 8, whiteLow);
                    p.d(F, x0 + 3, 8, whiteLow);
                    p.d(F, ix, 8, shade(iris, 0.6f));
                    p.d(F, ix + 1, 8, shade(iris, 0.85f));
                    p.h(F, x0, 9, x0 + 3, 9, flat(shade(S, 0.9f)), 2);
                }
                case 1 -> { // миндаль: внешний уголок вниз уже
                    p.d(F, x0, 8, white);
                    p.d(F, x0 + 3, 8, white);
                    iris(p, F, ix, iris);
                    p.d(F, side == 0 ? x0 : x0 + 3, 9, shade(S, 0.9f));
                    p.d(F, side == 0 ? x0 + 3 : x0, 9, whiteLow);
                }
                case 3 -> { // большие: шире на пиксель наружу
                    int out = side == 0 ? x0 - 1 : x0 + 4;
                    p.d(F, out, 8, white);
                    p.d(F, out, 9, whiteLow);
                    p.d(F, x0, 8, white);
                    p.d(F, x0 + 3, 8, white);
                    p.d(F, x0, 9, whiteLow);
                    p.d(F, x0 + 3, 9, whiteLow);
                    iris(p, F, ix, iris);
                }
                default -> {
                    p.d(F, x0, 8, white);
                    p.d(F, x0 + 3, 8, white);
                    p.d(F, x0, 9, whiteLow);
                    p.d(F, x0 + 3, 9, whiteLow);
                    iris(p, F, ix, iris);
                }
            }
            int outer = side == 0 ? x0 - 1 : x0 + 4;
            if (a.lashes > 0 || a.liner > 0) {
                int lash = a.liner > 0 ? 0xFF0E0A0A : 0xFF241818;
                p.h(F, x0, 7, x0 + 3, 7, flat(lash), 2);
                p.d(F, outer, 7, a.liner > 0 ? lash : mix(lash, S, 0.4f));
                if (a.liner > 0) p.d(F, outer, 6, mix(lash, S, 0.3f));
            }
            if (a.bags > 0) p.h(F, x0, 10, x0 + 3, 10, flat(mix(shade(S, 0.86f), 0xFF5A4A6A, 0.12f)), 2);
        }
        // ---- губы
        int lipNat = dark ? mix(S, 0xFF5A2A26, 0.45f) : mix(S, 0xFFC0606A, 0.42f);
        int lip = a.lipstick > 0 ? op(StyleCatalog.LIP_COLORS.get(a.lipstick).color()) : lipNat;
        int line = shade(mix(lip, S, 0.3f), 0.62f);
        switch (a.mouth) {
            case 1 -> {
                p.h(F, 5, 12, 10, 12, flat(shade(lip, 0.9f)), 2);
                p.h(F, 6, 13, 9, 13, flat(lip), 2);
                p.h(F, 6, 12, 9, 12, flat(mix(line, lip, 0.4f)), 2);
                p.d(F, 7, 13, shade(lip, 1.12f));
            }
            case 2 -> p.h(F, 6, 12, 9, 12, flat(mix(line, lip, 0.35f)), 2);
            case 3 -> {
                p.h(F, 6, 12, 9, 12, flat(line), 2);
                p.d(F, 5, 11, line);
                p.d(F, 10, 11, line);
                p.h(F, 6, 13, 9, 13, flat(mix(lip, S, 0.3f)), 2);
                p.d(F, 4, 11, shade(S, 0.94f));
                p.d(F, 11, 11, shade(S, 0.94f));
            }
            default -> {
                p.h(F, 6, 12, 9, 12, flat(line), 2);
                p.h(F, 6, 13, 9, 13, flat(mix(lip, S, a.lipstick > 0 ? 0f : 0.25f)), 2);
                if (a.lipstick > 0) p.h(F, 6, 12, 9, 12, flat(shade(lip, 0.8f)), 2);
            }
        }
        // ---- румянец, веснушки, родинки, шрамы
        if (a.blush > 0) {
            int bl = mix(S, 0xFFE06070, dark ? 0.18f : 0.28f);
            for (int x : new int[]{2, 3, 4, 11, 12, 13}) p.d(F, x, 11, bl);
        }
        if (a.freckles > 0) {
            int fr = mix(S, 0xFF7A4024, 0.35f);
            int[][] dots = {{3, 10}, {5, 10}, {4, 11}, {2, 11}, {6, 10}, {9, 10}, {10, 10}, {11, 11}, {12, 10}, {13, 11}, {7, 9}, {8, 9}};
            for (int[] d : dots) p.d(F, d[0], d[1], fr);
        }
        int moleC = mix(S, 0xFF2A140C, 0.7f);
        if (a.mole == 1) p.d(F, 12, 11, moleC);
        if (a.mole == 2) p.d(F, 10, 11, moleC);
        int scarC = mix(S, 0xFFF0C8C0, 0.55f);
        switch (a.scar) {
            case 1 -> { p.d(F, 12, 5, scarC); p.d(F, 11, 6, scarC); p.d(F, 12, 6, scarC); }
            case 2 -> { p.d(F, 2, 10, scarC); p.d(F, 3, 11, scarC); p.d(F, 4, 12, scarC); }
            case 3 -> { p.d(F, 8, 11, scarC); p.d(F, 8, 12, mix(scarC, lip, 0.4f)); p.d(F, 8, 13, scarC); }
            default -> {}
        }
        int ink = mix(S, 0xFF1A2030, 0.75f);
        switch (a.faceTat) {
            case 1 -> { p.d(F, 13, 9, ink); p.d(F, 12, 10, ink); p.d(F, 13, 10, ink); p.d(F, 14, 10, ink); p.d(F, 13, 11, ink); }
            case 2 -> { p.d(F, 12, 10, 0xFFA01A2A); p.d(F, 14, 10, 0xFFA01A2A); p.d(F, 13, 11, 0xFFA01A2A); p.d(F, 13, 10, 0xFFA01A2A); }
            case 3 -> { p.d(F, 13, 10, ink); p.d(F, 13, 12, ink); p.d(F, 14, 11, ink); }
            default -> {}
        }
        // ---- пирсинг: гвоздики в ушах (на боках головы), нос
        if (a.earrings > 0) {
            int m = op(StyleCatalog.EARRINGS.get(a.earrings).color());
            p.d(b.head.right, 8, 11, m);
            p.d(b.head.left, 7, 11, m);
        }
        if (a.nose == 1) p.d(F, 9, 10, 0xFFE8E8E8);
        if (a.nose == 2) { p.d(F, 9, 11, 0xFFC8C8C8); p.d(F, 10, 11, 0xFFA8A8A8); }
        // ---- уши: чуть темнее в середине боков
        p.h(b.head.right, 7, 8, 9, 11, flat(shade(S, 0.92f)), 2);
        p.h(b.head.left, 6, 8, 8, 11, flat(shade(S, 0.92f)), 2);
        if (a.earrings > 0) {
            int m = op(StyleCatalog.EARRINGS.get(a.earrings).color());
            p.d(b.head.right, 8, 12, m);
            p.d(b.head.left, 7, 12, m);
        }
        // ---- щетина и борода (объёмные усы и борода — HairFeature)
        int B = shade(H, 0.95f);
        switch (a.beard) {
            case "stubble" -> {
                int st = mix(S, H, 0.32f);
                for (int y = 11; y <= 15; y++)
                    for (int x = 0; x < 16; x++) {
                        if (y <= 13 && x >= 4 && x <= 11 && !(y == 11 && (x < 6 || x > 9))) continue;
                        if (y == 11 && x > 5 && x < 10) {
                            if ((x & 1) == 0) p.d(F, x, y, st);
                            continue;
                        }
                        if (hash(x, y, 5) % 3 != 0) p.d(F, x, y, st);
                    }
                p.h(b.head.right, 0, 12, 15, 15, (x, y) -> hash(x, y, 6) % 3 == 0 ? S : st, 2);
                p.h(b.head.left, 0, 12, 15, 15, (x, y) -> hash(x, y, 6) % 3 == 0 ? S : st, 2);
                p.h(b.head.bottom, 0, 0, 15, 15, flat(st), 2);
            }
            case "mustache" -> p.h(F, 5, 11, 10, 11, flat(B), 2);
            case "goatee" -> {
                p.h(F, 5, 11, 10, 11, flat(B), 2);
                p.h(F, 6, 14, 9, 15, flat(B), 2);
            }
            case "chin", "beard", "full" -> {
                p.h(F, 0, 13, 5, 15, flat(B), 2);
                p.h(F, 10, 13, 15, 15, flat(B), 2);
                p.h(F, 4, 14, 11, 15, flat(B), 2);
                if (!a.beard.equals("chin")) {
                    p.h(F, 0, 10, 1, 15, flat(B), 2);
                    p.h(F, 14, 10, 15, 15, flat(B), 2);
                }
                if (a.beard.equals("full")) p.h(F, 5, 11, 10, 11, flat(B), 2);
                p.h(b.head.right, 0, 11, 15, 15, flat(B), 2);
                p.h(b.head.left, 0, 11, 15, 15, flat(B), 2);
                p.h(b.head.bottom, 0, 0, 15, 15, flat(B), 2);
                // губы видны
                p.h(F, 6, 12, 9, 12, flat(line), 2);
                p.h(F, 6, 13, 9, 13, flat(lip), 2);
            }
            default -> {}
        }
    }

    private static void iris(P p, Rg F, int ix, int iris) {
        p.d(F, ix, 8, shade(iris, 0.45f));
        p.d(F, ix + 1, 8, mix(iris, 0xFFFFFFFF, 0.35f));
        p.d(F, ix, 9, iris);
        p.d(F, ix + 1, 9, shade(iris, 0.8f));
    }

    // ================================================================== волосы на голове (под объёмной причёской)
    private static void hair(P p, Body b, Appearance a, int S, int H) {
        String st = a.hair;
        if (st.equals("bald")) {
            p.h(b.head.top, 4, 4, 11, 11, flat(shade(S, 1.05f)), 2);
            return;
        }
        int hl = a.highlight >= 0 ? op(StyleCatalog.hairColor(a.highlight)) : 0;
        Paint strands = (x, y) -> {
            if (hl != 0 && (x % 5) == 1) return shade(hl, 1 + ((hash(x, y >> 2, 4) % 5) - 2) / 40f);
            return shade(H, 1 + ((x * 7 + (y >> 2) * 3) % 5 - 2) * 0.045f);
        };
        boolean shaved = st.equals("buzz") || st.equals("crew") || st.equals("cornrows");
        int fuzz = mix(S, H, st.equals("crew") ? 0.7f : 0.55f);
        Paint shave = (x, y) -> hash(x, y, 2) % 4 == 0 ? mix(S, H, 0.35f) : fuzz;
        Rg F = b.head.front;
        if (shaved) {
            p.all(b.head.top, st.equals("cornrows") ? (x, y) -> (x >> 1) % 2 == 0 ? shade(H, 0.9f) : mix(S, H, 0.4f) : shave, 2);
            p.h(F, 0, 0, 15, 1, shave, 2);
            p.h(b.head.right, 0, 0, 15, 5, shave, 2);
            p.h(b.head.left, 0, 0, 15, 5, shave, 2);
            p.h(b.head.back, 0, 0, 15, 9, st.equals("cornrows") ? (x, y) -> (x >> 1) % 2 == 0 ? shade(H, 0.9f) : mix(S, H, 0.4f) : shave, 2);
            return;
        }
        boolean sides = st.equals("undercut") || st.equals("mohawk");
        p.all(b.head.top, strands, 2);
        if (sides) {
            p.h(b.head.right, 0, 0, 15, 5, shave, 2);
            p.h(b.head.left, 0, 0, 15, 5, shave, 2);
            p.h(b.head.back, 0, 0, 15, 9, shave, 2);
            p.h(F, 0, 0, 15, 0, st.equals("mohawk") ? shave : strands, 2);
            if (st.equals("mohawk")) p.h(b.head.top, 5, 0, 10, 15, strands, 2);
            return;
        }
        boolean longHair = switch (st) {
            case "long", "wavy", "bob", "ponytail", "high_pony", "braids", "bun", "dreads", "afro", "curly" -> true;
            default -> false;
        };
        int fringe = !a.fringe || !StyleCatalog.hasFringe(st) ? 1 : switch (st) {
            case "bob", "pixie", "long", "wavy", "braids" -> 4;
            default -> 3;
        };
        if (st.equals("afro") || st.equals("curly")) fringe = 3;
        p.h(F, 0, 0, 15, fringe - 1, strands, 2);
        if (fringe >= 3 && !st.equals("side_part")) {
            // неровный край чёлки
            for (int x = 0; x < 16; x += 3) p.d(F, x, fringe, shade(H, 0.85f));
        }
        if (st.equals("side_part")) {
            p.h(F, 0, 0, 10, 2, strands, 2);
            p.d(F, 11, 0, shade(S, 0.95f));
        }
        int side = longHair ? 13 : 5;
        p.h(b.head.right, 0, 0, 15, side, strands, 2);
        p.h(b.head.left, 0, 0, 15, side, strands, 2);
        if (!longHair) {
            // виски / бакенбарды у мужских стрижек
            p.h(b.head.right, 12, 6, 15, 9, strands, 2);
            p.h(b.head.left, 0, 6, 3, 9, strands, 2);
        }
        p.h(b.head.back, 0, 0, 15, longHair ? 15 : 10, strands, 2);
        if (longHair && !st.equals("ponytail") && !st.equals("high_pony") && !st.equals("bun")) {
            p.h(F, 0, 0, 0, 12, strands, 2);
            p.h(F, 15, 0, 15, 12, strands, 2);
        }
    }

    // ================================================================== тату и ногти
    private static void tattoos(P p, Body b, Appearance a) {
        int ink = 0xFF2A3044;
        for (int slot = 0; slot < 2; slot++) {
            int t = a.tats[slot];
            if (t == 0) continue;
            Bx arm = b.arms[slot == 0 ? 1 : 0];
            Rg out = slot == 0 ? arm.left : arm.right;
            switch (t) {
                case 1 -> { // узор-браслет на плече
                    for (Rg r : arm.sides())
                        for (int x = 0; x < r.hw(); x++) {
                            int y = 6 + ((x / 2) % 2 == 0 ? 0 : 1);
                            p.d(r, x, y, ink);
                            p.d(r, x, y + 2, ink);
                        }
                }
                case 2 -> { // роза
                    int red = 0xFFA01A2A;
                    p.d(out, 3, 5, red);
                    p.d(out, 4, 5, shade(red, 0.7f));
                    p.d(out, 3, 6, shade(red, 0.8f));
                    p.d(out, 4, 6, red);
                    p.d(out, 5, 5, red);
                    p.d(out, 4, 4, red);
                    p.d(out, 4, 7, 0xFF2E5E2A);
                    p.d(out, 4, 8, 0xFF2E5E2A);
                    p.d(out, 5, 8, 0xFF3A7A34);
                }
                case 3 -> { // якорь
                    for (int y = 3; y <= 9; y++) p.d(out, 4, y, ink);
                    p.d(out, 3, 4, ink);
                    p.d(out, 5, 4, ink);
                    p.d(out, 2, 8, ink);
                    p.d(out, 6, 8, ink);
                    p.d(out, 3, 9, ink);
                    p.d(out, 5, 9, ink);
                }
                case 4 -> { // «рукав» — плечо и предплечье
                    for (Rg r : arm.sides())
                        for (int y = 2; y < 20; y++)
                            for (int x = 0; x < r.hw(); x++) {
                                int h = hash(r.x * 2 + x, r.y * 2 + y, 31) % 7;
                                if (h < 2) p.d(r, x, y, mix(p.at(r, x, y), ink, h == 0 ? 0.85f : 0.55f));
                            }
                }
                case 5 -> { // надпись на предплечье
                    Rg f = arm.front;
                    for (int x = 1; x < f.hw() - 1; x++) if (x % 3 != 2) p.d(f, x, 15, ink);
                    for (int x = 1; x < f.hw() - 1; x += 2) p.d(f, x, 16, ink);
                }
                default -> { // звёзды
                    int[][] s = {{2, 4}, {5, 7}, {3, 11}};
                    for (int[] q : s) {
                        p.d(out, q[0], q[1], ink);
                        p.d(out, q[0] - 1, q[1], ink);
                        p.d(out, q[0] + 1, q[1], ink);
                        p.d(out, q[0], q[1] - 1, ink);
                        p.d(out, q[0], q[1] + 1, ink);
                    }
                }
            }
        }
        if (a.tats[2] > 0) { // шея: сбоку головы снизу и верх корпуса сзади
            int t = a.tats[2];
            Rg r = b.head.left;
            int col = t == 2 ? 0xFFA01A2A : ink;
            p.d(r, 6, 14, col);
            p.d(r, 7, 15, col);
            p.d(r, 8, 14, col);
            p.d(r, 7, 13, t == 4 ? ink : col);
            Rg bk = b.body.back;
            for (int x = 5; x <= 10; x++) if (t == 1 || t == 4 || x % 2 == 0) p.d(bk, x, 1, ink);
        }
        if (a.tats[3] > 0) { // кисть правой руки
            Rg f = b.arms[0].front;
            p.d(f, 1, 21, ink);
            p.d(f, f.hw() - 2, 21, ink);
            p.d(f, f.hw() / 2, 20, ink);
        }
    }

    private static void nails(P p, Body b, Appearance a, int S) {
        if (a.nails <= 0) return;
        int n = op(StyleCatalog.NAIL_COLORS.get(a.nails).color());
        for (Bx arm : b.arms) {
            for (int x = 0; x < arm.front.hw(); x += 2) p.d(arm.front, x, 23, n);
            p.all(arm.bottom, (x, y) -> (x + y) % 2 == 0 ? n : shade(S, 0.95f), 2);
        }
    }

    // ================================================================== ноги без брюк (платье, купальник)
    private static void bareLegs(P p, Body b, String top, int C, int S) {
        if (top.equals("swimsuit")) for (Bx l : b.legs) p.rows(l, 0, 1, cloth(C), 1);
        else for (Bx l : b.legs) {
            int len = top.equals("dress") ? 19 : top.equals("office_dress") ? 11 : 8;
            p.rows(l, 0, len, cloth(C), 1);
            p.all(l.top, cloth(C), 1);
        }
    }

    // ================================================================== низ
    private static void pants(P p, Body b, String kind, int C, int S) {
        Rg f = b.body.front;
        int belt = 0xFF2A1A10, buckle = 0xFFC0C0C0;
        boolean waistBand = true;
        Paint base = cloth(C);
        boolean denim = kind.equals("jeans") || kind.equals("skinny") || kind.equals("ripped") || kind.equals("denim_shorts");
        if (denim) base = (x, y) -> shade(C, ((x + y) % 3 == 0 ? 1.08f : 0.98f) + ((hash(x, y, 4) % 5) - 2) / 200f);
        int len = switch (kind) {
            case "shorts", "swim_trunks" -> 10;
            case "denim_shorts" -> 7;
            case "skirt_pleat" -> 8;
            case "skirt" -> 12;
            case "skirt_long" -> 21;
            default -> 23;
        };
        for (Bx l : b.legs) {
            p.rows(l, 0, len, base, 1);
            p.all(l.top, base, 1);
            if (len >= 23) p.all(l.bottom, base, 1);
        }
        Paint fb = base;
        switch (kind) {
            case "jeans", "skinny", "ripped" -> {
                for (Bx l : b.legs) {
                    p.h(l.right, 0, 0, 0, 22, flat(shade(C, 1.2f)), 1);
                    p.h(l.left, 7, 0, 7, 22, flat(shade(C, 1.2f)), 1);
                    if (!kind.equals("skinny")) p.rows(l, 22, 23, flat(shade(C, 0.86f)), 1);
                    p.h(l.front, 0, 0, 7, 1, flat(shade(C, 0.88f)), 1);
                }
                if (kind.equals("ripped")) for (Bx l : b.legs) {
                    p.h(l.front, 2, 10, 5, 11, skinP(S), 2);
                    p.d(l.front, 2, 10, 0xFFE8E8E8);
                    p.d(l.front, 4, 11, 0xFFE8E8E8);
                    p.d(l.front, 1, 5, 0xFFE0E0E0);
                    p.d(l.front, 5, 16, 0xFFE0E0E0);
                }
            }
            case "sweats", "joggers" -> {
                for (Bx l : b.legs) {
                    if (kind.equals("sweats")) {
                        p.h(l.right, 2, 0, 3, 20, flat(0xFFF2F2F2), 1);
                        p.h(l.left, 4, 0, 5, 20, flat(0xFFF2F2F2), 1);
                    } else p.rows(l, 19, 21, flat(shade(C, 0.82f)), 1);
                    p.rows(l, 15, 15, flat(shade(C, 0.92f)), 1);
                }
                belt = shade(C, 1.12f);
                buckle = 0xFFF2F2F2;
            }
            case "shorts", "denim_shorts", "swim_trunks" -> {
                for (Bx l : b.legs) {
                    p.rows(l, len, len, flat(kind.equals("denim_shorts") ? 0xFFE8E8E8 : shade(C, 0.8f)), 1);
                    if (kind.equals("swim_trunks")) {
                        p.h(l.right, 1, 0, 2, len, flat(0xFFF2F2F2), 1);
                        p.h(l.left, 5, 0, 6, len, flat(0xFFF2F2F2), 1);
                    }
                }
                if (kind.equals("swim_trunks")) {
                    waistBand = false;
                    p.d(f, 7, 21, 0xFFF2F2F2);
                    p.d(f, 8, 22, 0xFFF2F2F2);
                }
            }
            case "cargo", "work_pants" -> {
                for (Bx l : b.legs) {
                    Rg o = l == b.legs[0] ? l.right : l.left;
                    p.h(o, 1, 9, 6, 14, flat(shade(C, 0.86f)), 1);
                    p.h(o, 1, 9, 6, 9, flat(shade(C, 0.7f)), 1);
                    if (kind.equals("work_pants")) {
                        p.h(l.front, 0, 10, 7, 13, flat(shade(C, 0.72f)), 1);
                        p.rows(l, 17, 17, flat(0xFFD8D8D8), 1);
                    }
                }
            }
            case "slacks", "chinos" -> {
                for (Bx l : b.legs) {
                    p.h(l.front, 3, 0, 3, 22, flat(shade(C, 0.84f)), 1);
                    p.h(l.back, 4, 0, 4, 22, flat(shade(C, 0.87f)), 1);
                }
                belt = kind.equals("slacks") ? 0xFF111111 : 0xFF4A2A18;
                buckle = kind.equals("slacks") ? 0xFFD4AF37 : 0xFFC0C0C0;
            }
            case "leather_pants" -> {
                for (Bx l : b.legs) for (Rg r : l.sides()) for (int y = 0; y < 22; y++) if (hash(r.x, r.y + y, 3) % 4 == 0) p.d(r, 2, y, shade(C, 2.4f) | 0xFF000000);
                belt = 0xFF0A0A0A;
            }
            case "leggings" -> {
                for (Bx l : b.legs) for (Rg r : l.sides()) p.h(r, 2, 0, 3, 22, flat(shade(C, 1.25f)), 1);
                waistBand = false;
            }
            case "skirt", "skirt_pleat", "skirt_long" -> {
                for (Bx l : b.legs) {
                    if (kind.equals("skirt_pleat")) for (Rg r : l.sides()) for (int x = 0; x < r.hw(); x += 2) p.h(r, x, 0, x, len, flat(shade(C, 0.84f)), 1);
                    p.rows(l, len, len, flat(shade(C, 0.8f)), 1);
                }
                waistBand = false;
            }
            default -> {}
        }
        // пояс брюк на корпусе (ряды 20–23)
        for (Rg r : b.body.sides()) p.h(r, 0, 20, r.hw() - 1, 23, fb, 1);
        p.all(b.body.bottom, fb, 1);
        if (waistBand) {
            for (Rg r : b.body.sides()) p.h(r, 0, 20, r.hw() - 1, 21, flat(belt), 1);
            p.h(f, 7, 20, 8, 21, flat(buckle), 1);
            p.d(f, 7, 20, shade(buckle, 1.2f));
        } else for (Rg r : b.body.sides()) p.h(r, 0, 20, r.hw() - 1, 20, flat(shade(C, 0.85f)), 1);
        if (kind.equals("sweats") || kind.equals("joggers")) {
            p.d(f, 7, 22, 0xFFF2F2F2);
            p.d(f, 8, 23, 0xFFF2F2F2);
        }
    }

    // ================================================================== обувь
    private static void shoes(P p, Body b, String kind, int C, int S) {
        int white = 0xFFF4F4F4, sole = 0xFF2A2420;
        for (int i = 0; i < 2; i++) {
            Bx l = b.legs[i], o = b.legsO[i];
            Paint c = cloth(C);
            switch (kind) {
                case "canvas" -> {
                    p.rows(l, 17, 21, c, 1);
                    p.rows(l, 22, 23, flat(white), 1);
                    p.h(l.front, 0, 19, 7, 21, flat(white), 1);
                    for (int y = 16; y <= 18; y += 2) p.h(l.front, 3, y, 4, y, flat(white), 1);
                    p.all(l.bottom, flat(white), 1);
                }
                case "high_tops" -> {
                    p.rows(l, 12, 21, c, 1);
                    p.rows(l, 22, 23, flat(white), 1);
                    p.rows(l, 12, 12, flat(trim(C, 0.3f)), 1);
                    for (int y = 13; y <= 19; y += 2) p.h(l.front, 3, y, 4, y, flat(white), 1);
                    Rg out = i == 0 ? l.right : l.left;
                    p.h(out, 2, 15, 5, 17, flat(white), 1);
                    p.all(l.bottom, flat(white), 1);
                }
                case "slides", "sandals" -> {
                    p.rows(l, 18, 22, skinP(S), 2);
                    if (kind.equals("slides")) {
                        p.h(l.front, 0, 19, 7, 20, c, 1);
                        p.h(l.right, 3, 19, 7, 20, c, 1);
                        p.h(l.left, 0, 19, 4, 20, c, 1);
                    } else {
                        p.rows(l, 19, 19, c, 1);
                        p.h(l.front, 3, 20, 4, 21, c, 1);
                        p.h(l.back, 0, 21, 7, 21, c, 1);
                    }
                    p.rows(l, 23, 23, flat(shade(C, 0.7f)), 1);
                    p.all(l.bottom, flat(shade(C, 0.7f)), 1);
                    p.h(l.front, 0, 22, 7, 22, flat(shade(S, 0.92f)), 2);
                }
                case "loafers", "oxfords" -> {
                    p.rows(l, 19, 21, c, 1);
                    p.rows(l, 22, 23, flat(shade(C, 0.55f)), 1);
                    p.d(l.front, 2, 20, mix(C, white, 0.6f));
                    if (kind.equals("oxfords")) {
                        p.h(l.front, 3, 19, 4, 19, flat(shade(C, 0.7f)), 1);
                        p.h(l.front, 0, 21, 7, 21, flat(shade(C, 0.8f)), 1);
                    } else p.h(l.front, 2, 19, 5, 19, flat(shade(C, 0.75f)), 1);
                    p.all(l.bottom, flat(shade(C, 0.5f)), 1);
                    if (lum(C) > 200) p.rows(l, 23, 23, flat(0xFF2A2A2A), 1);
                }
                case "boots", "work_boots", "biker_boots", "winter_boots" -> {
                    int topRow = switch (kind) {
                        case "biker_boots" -> 10;
                        case "winter_boots" -> 13;
                        default -> 14;
                    };
                    p.rows(l, topRow, 21, c, 1);
                    p.rows(l, 22, 23, flat(kind.equals("work_boots") ? 0xFF2A1A10 : sole), 1);
                    p.all(l.bottom, flat(sole), 1);
                    switch (kind) {
                        case "boots" -> {
                            p.h(l.right, 3, 14, 4, 19, flat(shade(C, 0.6f)), 1);
                            p.h(l.left, 3, 14, 4, 19, flat(shade(C, 0.6f)), 1);
                        }
                        case "work_boots" -> {
                            for (int y = 15; y <= 20; y += 2) p.h(l.front, 2, y, 5, y, flat(0xFFE8D8B8), 1);
                            p.h(l.front, 0, 21, 7, 21, flat(shade(C, 0.7f)), 1);
                        }
                        case "biker_boots" -> {
                            Rg out = i == 0 ? l.right : l.left;
                            p.h(out, 1, 14, 6, 14, flat(0xFF3A3A3A), 1);
                            p.h(out, 3, 13, 4, 15, flat(0xFFC0C0C0), 1);
                        }
                        default -> {
                            p.rows(l, 13, 14, (x, y) -> hash(x, y, 8) % 3 == 0 ? 0xFFD8CFC0 : 0xFFEDE6DA, 1);
                            for (Rg r : o.sides()) p.h(r, 0, 13, r.hw() - 1, 14, (x, y) -> hash(x, y, 8) % 3 == 0 ? 0xFFD8CFC0 : 0xFFEDE6DA, 1);
                        }
                    }
                    for (Rg r : o.sides()) p.h(r, 0, 20, r.hw() - 1, 21, c, 1);
                }
                case "heels", "flats" -> {
                    p.rows(l, 17, 20, skinP(S), 2);
                    p.h(l.front, 0, kind.equals("heels") ? 21 : 20, 7, 22, c, 1);
                    p.h(l.right, 0, 21, 7, 22, c, 1);
                    p.h(l.left, 0, 21, 7, 22, c, 1);
                    p.h(l.back, 0, 20, 7, 23, c, 1);
                    if (kind.equals("heels")) p.h(l.back, 2, 21, 5, 23, flat(shade(C, 0.7f)), 1);
                    p.rows(l, 23, 23, flat(shade(C, 0.6f)), 1);
                    p.all(l.bottom, flat(shade(C, 0.6f)), 1);
                }
                case "lux_sneakers" -> {
                    p.rows(l, 18, 21, c, 1);
                    p.h(l.right, 0, 20, 7, 20, flat(0xFFD4AF37), 1);
                    p.h(l.left, 0, 20, 7, 20, flat(0xFFD4AF37), 1);
                    p.h(l.front, 2, 18, 5, 18, flat(0xFFD4AF37), 1);
                    int s = lum(C) < 60 ? 0xFFD4AF37 : white;
                    p.rows(l, 22, 23, flat(s), 1);
                    p.all(l.bottom, flat(s), 1);
                }
                case "runners" -> {
                    p.rows(l, 18, 21, c, 1);
                    int acc = trim(C, 0.5f);
                    Rg out = i == 0 ? l.right : l.left;
                    for (int x = 1; x < 7; x++) p.d(out, x, 21 - x / 3, acc);
                    p.rows(l, 22, 23, flat(mix(acc, white, 0.5f)), 1);
                    p.all(l.bottom, flat(white), 1);
                }
                default -> { // кроссовки
                    p.rows(l, 18, 21, c, 1);
                    int accent = lum(C) > 200 ? 0xFF1E50B4 : white;
                    Rg out = i == 0 ? l.right : l.left;
                    p.h(out, 2, 20, 5, 20, flat(accent), 1);
                    p.d(out, 1, 19, accent);
                    p.h(l.front, 2, 18, 5, 18, flat(white), 1);
                    p.rows(l, 22, 23, flat(white), 1);
                    p.all(l.bottom, flat(white), 1);
                }
            }
        }
    }

    // ================================================================== верх
    private static int sleeveLen(String kind, boolean rolled) {
        int s = switch (kind) {
            case "tank", "jersey", "sundress", "swimsuit", "work_vest" -> 0;
            case "tee", "tee_print", "polo", "bowling", "hawaii", "dress", "office_dress", "tunic" -> 1;
            default -> 2;
        };
        if (kind.equals("work_vest")) s = 1;
        return s == 2 && rolled && !kind.equals("suit") && !kind.equals("coverall") ? 3 : s;
    }

    /** Узор возможен (у клетчатой и гавайской свой). */
    private static boolean patternable(String kind) {
        return switch (kind) {
            case "flannel", "hawaii", "suit", "work_vest", "coverall", "swimsuit", "jersey", "tracksuit" -> false;
            default -> true;
        };
    }

    private static void top(P p, Body b, Appearance a, String kind, int C, int S) {
        Rg F = b.body.front, BK = b.body.back;
        int white = 0xFFF2F2F2;
        Paint main = a.pattern > 0 && patternable(kind) ? pattern(C, a.pattern) : cloth(C);
        if (kind.equals("flannel")) main = pattern(C, 2);
        if (kind.equals("hawaii")) main = (x, y) -> {
            int h = hash(x, y, 11) % 29;
            return h == 0 || h == 5 ? 0xFFFFF4E8 : h == 9 ? 0xFFFFD54F : h == 13 || h == 17 ? 0xFF1F5E2A : h == 21 ? 0xFFE0407A : shade(C, 1 + ((hash(x, y, 3) % 7) - 3) / 240f);
        };
        if (kind.equals("sweater") || kind.equals("turtleneck") || kind.equals("cardigan") || kind.equals("vest_knit")) {
            Paint pm = main;
            main = (x, y) -> shade(pm.at(x, y), (x % 2 == 0) ? 0.95f : 1.02f);
        }
        boolean dress = StyleCatalog.dress(kind);
        boolean longBody = dress || kind.equals("tunic") || kind.equals("swimsuit") || kind.equals("coverall") || !a.tuck && !kind.equals("suit") && !kind.equals("vest_knit");
        int bottom = longBody ? 23 : 19;
        // ---- корпус
        for (Rg r : b.body.sides()) p.h(r, 0, 0, r.hw() - 1, bottom, main, 1);
        p.all(b.body.top, main, 1);
        if (longBody) p.all(b.body.bottom, main, 1);
        if (!longBody) for (Rg r : b.body.sides()) p.h(r, 0, 19, r.hw() - 1, 19, flat(shade(C, 0.86f)), 1); // складка у ремня
        else if (!dress && !kind.equals("swimsuit") && !kind.equals("coverall")) for (Rg r : b.body.sides()) p.h(r, 0, 23, r.hw() - 1, 23, flat(shade(C, 0.84f)), 1);
        // ---- рукава
        int sl = sleeveLen(kind, a.rolled);
        Paint sleeve = main;
        if (kind.equals("bowling")) sleeve = cloth(trim(C, 0.35f));
        for (Bx arm : b.arms) {
            switch (sl) {
                case 0 -> {}
                case 1 -> {
                    p.rows(arm, 0, 7, sleeve, 1);
                    p.rows(arm, 7, 7, flat(shade(C, 0.86f)), 1);
                    p.all(arm.top, sleeve, 1);
                }
                case 3 -> {
                    p.rows(arm, 0, 11, sleeve, 1);
                    p.rows(arm, 10, 12, flat(shade(C, 1.08f)), 1);
                    p.rows(arm, 12, 12, flat(shade(C, 0.8f)), 1);
                    p.all(arm.top, sleeve, 1);
                }
                default -> {
                    p.rows(arm, 0, 21, sleeve, 1);
                    p.rows(arm, 20, 21, flat(shade(C, 0.84f)), 1);
                    p.all(arm.top, sleeve, 1);
                }
            }
        }
        // ---- ворот и детали
        Paint skin = skinP(S);
        switch (kind) {
            case "tank", "jersey" -> {
                p.h(F, 4, 0, 11, 3, skin, 2);
                p.h(F, 5, 4, 10, 4, skin, 2);
                p.h(BK, 4, 0, 11, 1, skin, 2);
                p.h(b.body.top, 0, 0, 15, 7, skin, 2);
                p.h(b.body.top, 0, 0, 2, 7, main, 1);
                p.h(b.body.top, 13, 0, 15, 7, main, 1);
                p.h(F, 0, 0, 1, 3, skin, 2);
                p.h(F, 14, 0, 15, 3, skin, 2);
                for (Rg r : new Rg[]{b.body.right, b.body.left}) p.h(r, 0, 0, r.hw() - 1, 5, skin, 2);
                if (kind.equals("jersey")) {
                    int num = trim(C, 0.8f);
                    // номер «7» спереди и сзади
                    for (Rg r : new Rg[]{F, BK}) {
                        p.h(r, 5, 7, 10, 8, flat(num), 1);
                        for (int y = 9; y <= 16; y++) p.d(r, 10 - (y - 9) / 2, y, num);
                        for (int y = 9; y <= 16; y++) p.d(r, 9 - (y - 9) / 2, y, num);
                    }
                    for (Rg r : new Rg[]{b.body.right, b.body.left}) p.h(r, 3, 6, 4, 23, flat(num), 1);
                }
            }
            case "tee", "tee_print", "longsleeve", "sweatshirt", "tunic" -> {
                p.h(F, 5, 0, 10, 0, skin, 2);
                p.h(F, 6, 1, 9, 1, skin, 2);
                p.h(F, 4, 0, 11, 0, flat(shade(C, 0.86f)), 1);
                p.h(F, 5, 0, 10, 0, skin, 2);
                p.h(F, 5, 1, 5, 1, flat(shade(C, 0.86f)), 1);
                p.h(F, 10, 1, 10, 1, flat(shade(C, 0.86f)), 1);
                if (kind.equals("tee_print")) {
                    // абстрактный принт без логотипов: закат над волной
                    int sun = lum(C) > 150 ? 0xFFE0407A : 0xFFFFC428, wave = lum(C) > 150 ? 0xFF1E50B4 : 0xFF28E6FF;
                    for (int y = 6; y <= 10; y++) for (int x = 5; x <= 10; x++) {
                        int dx = 2 * x - 15, dy = 2 * y - 21;
                        if (dx * dx + dy * dy <= 30) p.dm(F, x, y, mix(sun, 0xFFFF6A2A, (y - 6) / 6f), 1);
                    }
                    for (int x = 3; x <= 12; x++) p.dm(F, x, 11 + ((x / 2) % 2), wave, 1);
                    for (int x = 4; x <= 11; x++) p.dm(F, x, 13, shade(wave, 0.7f), 1);
                }
                if (kind.equals("sweatshirt")) {
                    p.rows(b.body, 21, 23, flat(shade(C, 0.88f)), 1);
                    for (Bx arm : b.arms) p.rows(arm, 19, 21, flat(shade(C, 0.86f)), 1);
                }
                if (kind.equals("tunic")) {
                    int em = trim(C, 0.5f);
                    for (int x = 4; x <= 11; x++) p.d(F, x, 2, x % 2 == 0 ? em : 0xFFF2C230);
                    for (int y = 2; y <= 6; y++) p.d(F, 7, y, em);
                }
            }
            case "polo" -> {
                p.h(F, 3, 0, 12, 1, flat(shade(C, 1.12f)), 1);
                p.h(F, 6, 1, 9, 6, flat(shade(C, 0.92f)), 1);
                p.d(F, 7, 3, white);
                p.d(F, 7, 5, white);
                p.h(BK, 0, 0, 15, 1, flat(shade(C, 1.12f)), 1);
            }
            case "shirt", "flannel", "bowling", "hawaii", "blouse", "coverall" -> {
                boolean camp = kind.equals("bowling") || kind.equals("hawaii");
                int coll = kind.equals("flannel") ? shade(C, 1.15f) : trim(C, 0.12f);
                // воротник и планка с пуговицами
                p.h(F, 4, 0, 6, 2, flat(coll), 1);
                p.h(F, 9, 0, 11, 2, flat(coll), 1);
                p.h(F, 7, 0, 8, camp || kind.equals("blouse") ? 3 : 1, skin, 2);
                if (!camp) {
                    p.h(F, 7, 2, 7, bottom, flat(shade(C, 0.88f)), 1);
                    for (int y = 4; y <= bottom - 1; y += 4) p.d(F, 8, y, kind.equals("coverall") ? 0xFFC0C0C0 : trim(C, 0.35f));
                } else for (int y = 5; y <= bottom - 1; y += 5) p.d(F, 8, y, trim(C, 0.4f));
                p.h(BK, 0, 0, 15, 1, flat(coll), 1);
                if (kind.equals("shirt") || kind.equals("coverall")) p.h(F, 10, 5, 13, 8, flat(shade(C, 0.92f)), 1);
                if (kind.equals("bowling")) for (Rg r : new Rg[]{F, BK}) {
                    p.h(r, 2, 0, 4, bottom, cloth(trim(C, 0.35f)), 1);
                    p.h(r, 11, 0, 13, bottom, cloth(trim(C, 0.35f)), 1);
                }
                if (kind.equals("blouse")) {
                    p.h(F, 6, 3, 9, 3, flat(shade(C, 0.9f)), 1);
                    for (int y = 6; y <= bottom; y += 4) p.d(F, 4, y, shade(C, 0.93f));
                }
                if (kind.equals("coverall")) {
                    p.h(F, 2, 5, 5, 8, flat(shade(C, 0.88f)), 1);
                    p.h(BK, 2, 4, 13, 9, flat(shade(C, 0.95f)), 1);
                }
            }
            case "hoodie" -> {
                p.h(F, 2, 12, 13, 19, flat(shade(C, 0.88f)), 1);
                p.h(F, 2, 12, 13, 12, flat(shade(C, 0.78f)), 1);
                p.h(F, 5, 0, 10, 1, flat(shade(C, 0.74f)), 1);
                p.h(F, 6, 2, 6, 8, flat(white), 1);
                p.h(F, 9, 2, 9, 8, flat(white), 1);
                p.h(BK, 2, 0, 13, 5, flat(shade(C, 0.84f)), 1);
                p.h(BK, 3, 6, 12, 6, flat(shade(C, 0.76f)), 1);
                p.h(b.body.top, 2, 0, 13, 7, flat(shade(C, 0.82f)), 1);
                p.rows(b.body, 21, 23, flat(shade(C, 0.88f)), 1);
            }
            case "sweater", "cardigan" -> {
                p.h(F, 5, 0, 10, 0, skin, 2);
                p.h(F, 4, 0, 11, 1, flat(shade(C, 0.86f)), 1);
                p.h(F, 5, 0, 10, 0, skin, 2);
                p.rows(b.body, 21, 23, flat(shade(C, 0.86f)), 1);
                for (Bx arm : b.arms) if (sl == 2) p.rows(arm, 19, 21, flat(shade(C, 0.84f)), 1);
                if (kind.equals("cardigan")) {
                    int inner = lum(C) > 150 ? 0xFF1E1E1E : 0xFFF2F2F2;
                    if (a.open) p.h(F, 6, 0, 9, bottom, cloth(inner), 1);
                    else {
                        p.h(F, 6, 0, 9, 5, cloth(inner), 1);
                        for (int y = 7; y <= bottom - 2; y += 4) p.d(F, 7, y, trim(C, 0.4f));
                    }
                    p.h(F, 5, 0, 5, bottom, flat(shade(C, 0.82f)), 1);
                    p.h(F, 10, 0, 10, bottom, flat(shade(C, 0.82f)), 1);
                }
            }
            case "turtleneck" -> {
                p.h(F, 0, 0, 15, 2, flat(shade(C, 0.92f)), 1);
                p.h(BK, 0, 0, 15, 2, flat(shade(C, 0.92f)), 1);
                // ворот поднимается на шею (низ головы)
                p.all(b.head.bottom, cloth(shade(C, 0.95f)), 1);
                p.h(b.head.front, 0, 15, 15, 15, flat(shade(C, 0.95f)), 1);
                p.h(b.head.back, 0, 15, 15, 15, flat(shade(C, 0.95f)), 1);
                p.h(b.head.right, 0, 15, 15, 15, flat(shade(C, 0.95f)), 1);
                p.h(b.head.left, 0, 15, 15, 15, flat(shade(C, 0.95f)), 1);
            }
            case "tracksuit" -> {
                p.h(b.arms[0].right, 1, 0, 1, 19, flat(white), 1);
                p.h(b.arms[0].right, 4, 0, 4, 19, flat(white), 1);
                p.h(b.arms[1].left, 1, 0, 1, 19, flat(white), 1);
                p.h(b.arms[1].left, 4, 0, 4, 19, flat(white), 1);
                p.h(b.body.right, 3, 0, 3, 21, flat(white), 1);
                p.h(b.body.left, 4, 0, 4, 21, flat(white), 1);
                p.h(F, 7, 0, 7, 21, flat(0xFFD0D0D0), 1);
                p.h(F, 8, 0, 8, 21, flat(shade(C, 0.7f)), 1);
                p.h(F, 2, 0, 13, 1, flat(shade(C, 0.75f)), 1);
                p.rows(b.body, 21, 23, flat(shade(C, 0.8f)), 1);
            }
            case "sundress", "swimsuit" -> {
                // бретели: плечи открыты, вырез — скромный
                p.h(b.body.top, 0, 0, 15, 7, skin, 2);
                p.h(b.body.top, 3, 0, 4, 7, main, 1);
                p.h(b.body.top, 11, 0, 12, 7, main, 1);
                p.h(F, 0, 0, 15, 2, skin, 2);
                p.h(F, 3, 0, 4, 2, main, 1);
                p.h(F, 11, 0, 12, 2, main, 1);
                p.h(F, 5, 3, 10, 3, flat(shade(C, 0.85f)), 1);
                p.h(BK, 0, 0, 15, 3, skin, 2);
                p.h(BK, 3, 0, 4, 3, main, 1);
                p.h(BK, 11, 0, 12, 3, main, 1);
                for (Rg r : new Rg[]{b.body.right, b.body.left}) p.h(r, 0, 0, r.hw() - 1, 3, skin, 2);
                if (kind.equals("sundress")) for (Rg r : b.body.sides()) p.h(r, 0, 13, r.hw() - 1, 13, flat(shade(C, 0.82f)), 1);
                else {
                    for (Rg r : new Rg[]{b.body.right, b.body.left}) p.h(r, 3, 4, 4, 23, flat(trim(C, 0.5f)), 1);
                    p.rows(b.body, 22, 23, flat(shade(C, 0.9f)), 1);
                }
            }
            case "dress", "office_dress" -> {
                p.h(F, 5, 0, 10, 1, skin, 2);
                p.h(F, 6, 2, 9, 2, skin, 2);
                for (Rg r : b.body.sides()) p.h(r, 0, 13, r.hw() - 1, 13, flat(kind.equals("dress") ? trim(C, 0.4f) : shade(C, 0.82f)), 1);
                if (kind.equals("office_dress")) p.h(F, 7, 14, 8, 23, flat(shade(C, 0.9f)), 1);
            }
            case "suit", "vest_knit" -> {
                int shirt = kind.equals("suit") && lum(C) > 200 ? 0xFF1E1E2A : 0xFFF6F6F6;
                int tie = kind.equals("suit") ? (lum(C) > 200 ? 0xFFEDEDED : 0xFF8B1A1A) : 0;
                for (Rg r : b.body.sides()) p.h(r, 0, 0, r.hw() - 1, bottom, cloth(shirt), 1);
                p.all(b.body.top, cloth(shirt), 1);
                for (Bx arm : b.arms) {
                    if (sl == 3) {
                        p.rows(arm, 0, 11, cloth(shirt), 1);
                        p.rows(arm, 10, 12, flat(shade(shirt, 0.94f)), 1);
                    } else p.rows(arm, 0, 21, cloth(shirt), 1);
                    p.all(arm.top, cloth(shirt), 1);
                }
                // жилет
                Paint vest = kind.equals("suit") ? cloth(shade(C, 0.9f)) : main;
                p.h(F, 2, 5, 13, 21, vest, 1);
                for (int y = 0; y < 6; y++) {
                    p.h(F, 2, y + 4, 6 - y, y + 4, vest, 1);
                    p.h(F, 9 + y, y + 4, 13, y + 4, vest, 1);
                }
                p.h(BK, 0, 4, 15, 21, vest, 1);
                for (Rg r : new Rg[]{b.body.right, b.body.left}) p.h(r, 0, 5, r.hw() - 1, 21, vest, 1);
                if (tie != 0) {
                    p.h(F, 7, 1, 8, 15, flat(tie), 1);
                    p.h(F, 7, 1, 8, 1, flat(shade(tie, 0.8f)), 1);
                } else {
                    p.h(F, 5, 0, 6, 2, flat(0xFFE8E8E8), 1);
                    p.h(F, 9, 0, 10, 2, flat(0xFFE8E8E8), 1);
                }
                for (int y = 12; y <= 20; y += 4) p.d(F, 7, y, shade(C, 0.6f));
                if (kind.equals("suit")) jacket(p, b, a, C, 23, true, 21, "suit");
            }
            case "work_vest" -> {
                // под жилетом — серая футболка
                int tee = 0xFF8A8A8A;
                for (Rg r : b.body.sides()) p.h(r, 0, 0, r.hw() - 1, bottom, cloth(tee), 1);
                for (Bx arm : b.arms) p.rows(arm, 0, 7, cloth(tee), 1);
                p.h(F, 5, 0, 10, 1, skin, 2);
                for (Rg r : b.body.sides()) p.h(r, 0, 2, r.hw() - 1, 21, main, 1);
                p.h(F, 6, 0, 9, 21, cloth(tee), 1);
                p.h(F, 7, 3, 8, 21, flat(shade(C, 0.8f)), 1);
                for (int y : new int[]{10, 11, 16, 17}) p.rows(b.body, y, y, flat(0xFFD8D8D8), 1);
                p.h(F, 6, 0, 9, 21, cloth(tee), 1);
            }
            default -> {}
        }
        if (sl == 0 || sl == 1) for (Bx arm : b.arms) p.all(arm.bottom, skin, 2);
    }

    /** Женская фигура: лёгкая тень по бокам талии (без «объёма»). */
    private static void waist(P p, Body b) {
        Rg F = b.body.front, BK = b.body.back;
        for (int y = 12; y <= 18; y++) {
            for (Rg r : new Rg[]{F, BK}) {
                p.d(r, 0, y, shade(p.at(r, 0, y), 0.9f));
                p.d(r, r.hw() - 1, y, shade(p.at(r, r.hw() - 1, y), 0.9f));
            }
        }
    }

    // ================================================================== куртка поверх
    private static void outer(P p, Body b, Appearance a, String kind, int C, String top) {
        boolean open = a.open;
        int bottom = switch (kind) {
            case "coat", "trench", "parka" -> 23;
            case "bomber", "varsity", "denim", "leather", "fleece" -> 21;
            default -> 23;
        };
        int cuff = kind.equals("biker_vest") ? -1 : 21;
        if (kind.equals("puffer") || kind.equals("parka")) open = false;
        jacket(p, b, a, C, bottom, open, cuff, kind);
        Rg o = b.bodyO.front;
        int white = 0xFFF2F2F2;
        switch (kind) {
            case "denim" -> {
                for (int x = 0; x < 16; x++) if (x < 5 || x > 10) p.d(o, x, 6, shade(C, 1.25f));
                p.h(o, 2, 7, 4, 9, flat(shade(C, 0.88f)), 1);
                p.h(o, 11, 7, 13, 9, flat(shade(C, 0.88f)), 1);
                p.d(o, 3, 8, 0xFFC08A40);
                p.d(o, 12, 8, 0xFFC08A40);
                p.h(o, 0, 0, 4, 1, flat(shade(C, 1.15f)), 1);
                p.h(o, 11, 0, 15, 1, flat(shade(C, 1.15f)), 1);
                for (Bx arm : b.armsO) p.rows(arm, 19, 21, flat(shade(C, 1.12f)), 1);
            }
            case "leather" -> {
                for (Bx bx : new Bx[]{b.bodyO, b.armsO[0], b.armsO[1]})
                    for (Rg r : bx.sides())
                        for (int y = 0; y < 21; y++) if (hash(r.x, r.y + y, 5) % 6 == 0) p.d(r, (y * 3) % r.hw(), y, shade(C, 2.6f) | 0xFF000000);
                if (open) {
                    p.h(o, 3, 0, 5, 6, flat(shade(C, 1.8f)), 1);
                    p.h(o, 10, 0, 12, 6, flat(shade(C, 1.8f)), 1);
                } else for (int y = 0; y <= 20; y++) p.d(o, 11 - y / 3, y, 0xFFC0C0C0);
                p.d(o, 2, 12, 0xFFC0C0C0);
                p.rows(b.bodyO, 20, 21, flat(shade(C, 0.8f)), 1);
            }
            case "bomber", "varsity" -> {
                int rib = kind.equals("varsity") ? white : shade(C, 0.7f);
                p.rows(b.bodyO, 20, 21, flat(rib), 1);
                if (kind.equals("varsity")) p.rows(b.bodyO, 21, 21, flat(shade(C, 0.8f)), 1);
                p.h(o, 3, 0, 12, 1, flat(rib), 1);
                for (Bx arm : b.armsO) p.rows(arm, 18, 21, flat(rib), 1);
                if (kind.equals("varsity")) {
                    for (Bx arm : b.armsO) p.rows(arm, 0, 17, cloth(0xFFEDE6D6), 1);
                    if (!open) for (int y = 3; y <= 18; y += 3) p.d(o, 7, y, white);
                    // нашивка-круг без букв
                    p.h(o, 2, 4, 4, 6, flat(white), 1);
                    p.d(o, 3, 5, shade(C, 1.2f));
                } else if (!open) p.h(o, 7, 0, 7, 20, flat(0xFFC0C0C0), 1);
            }
            case "blazer" -> {
                if (open) {
                    p.h(o, 4, 0, 5, 9, flat(shade(C, 0.82f)), 1);
                    p.h(o, 10, 0, 11, 9, flat(shade(C, 0.82f)), 1);
                } else {
                    for (int y = 0; y < 9; y++) { p.d(o, 4 + y / 2, y, shade(C, 0.8f)); p.d(o, 11 - y / 2, y, shade(C, 0.8f)); }
                    p.d(o, 7, 12, shade(C, 0.55f));
                    p.d(o, 7, 16, shade(C, 0.55f));
                }
                p.h(o, 2, 6, 3, 6, flat(0xFFFF2E88), 1);
                p.h(o, 10, 14, 13, 14, flat(shade(C, 0.75f)), 1);
                p.h(o, 2, 14, 5, 14, flat(shade(C, 0.75f)), 1);
            }
            case "coat", "trench" -> {
                p.h(o, 1, 0, 4, 5, flat(shade(C, 1.12f)), 1);
                p.h(o, 11, 0, 14, 5, flat(shade(C, 1.12f)), 1);
                if (!open) for (int y = 8; y <= 20; y += 4) {
                    p.d(o, 5, y, 0xFF1A1A1A);
                    p.d(o, 10, y, 0xFF1A1A1A);
                }
                if (kind.equals("trench")) {
                    p.rows(b.bodyO, 15, 16, flat(shade(C, 0.8f)), 1);
                    p.h(o, 7, 15, 8, 16, flat(0xFFC0A060), 1);
                    for (Bx arm : b.armsO) p.all(arm.top, flat(shade(C, 0.88f)), 1);
                }
                // полы на бёдрах
                for (Bx l : b.legsO) for (Rg r : l.sides()) p.h(r, 0, 0, r.hw() - 1, kind.equals("coat") ? 11 : 13, cloth(C), 1);
                p.h(b.legsO[0].front, 7, 0, 7, 13, flat(shade(C, 0.75f)), 1);
                p.h(b.legsO[1].front, 0, 0, 0, 13, flat(shade(C, 0.75f)), 1);
            }
            case "puffer" -> {
                for (Bx bx : new Bx[]{b.bodyO, b.armsO[0], b.armsO[1]})
                    for (Rg r : bx.sides()) for (int y = 3; y < 22; y += 4) p.h(r, 0, y, r.hw() - 1, y, flat(shade(C, 0.72f)), 1);
                for (Bx bx : new Bx[]{b.bodyO, b.armsO[0], b.armsO[1]})
                    for (Rg r : bx.sides()) for (int y = 1; y < 22; y += 4) p.h(r, 0, y, r.hw() - 1, y, flat(shade(C, 1.2f)), 1);
                p.h(o, 7, 0, 7, 22, flat(0xFFB0B0B0), 1);
                p.h(o, 2, 0, 13, 1, flat(shade(C, 0.85f)), 1);
            }
            case "parka" -> {
                Paint fur = (x, y) -> hash(x, y, 8) % 3 == 0 ? 0xFFB8A890 : 0xFFD8CCB4;
                p.h(o, 2, 0, 13, 1, fur, 1);
                p.h(b.bodyO.back, 1, 0, 14, 5, flat(shade(C, 0.88f)), 1);
                p.h(b.bodyO.back, 1, 0, 14, 0, fur, 1);
                p.h(o, 7, 2, 8, 22, flat(shade(C, 0.75f)), 1);
                p.h(o, 1, 12, 5, 16, flat(shade(C, 0.88f)), 1);
                p.h(o, 10, 12, 14, 16, flat(shade(C, 0.88f)), 1);
                p.all(b.bodyO.top, fur, 1);
            }
            case "windbreaker" -> {
                int band = trim(C, 0.6f);
                p.rows(b.bodyO, 6, 8, flat(band), 1);
                for (Bx arm : b.armsO) p.rows(arm, 6, 8, flat(band), 1);
                if (!open) p.h(o, 7, 0, 7, 22, flat(0xFFC0C0C0), 1);
            }
            case "biker_vest" -> {
                Rg bk = b.bodyO.back;
                for (int y = 5; y <= 13; y++) for (int x = 4; x <= 11; x++) {
                    int dx = 2 * x - 15, dy = 2 * y - 18;
                    int d = dx * dx + dy * dy;
                    if (d <= 64 && d >= 30) p.d(bk, x, y, 0xFFC8B070);
                    else if (d < 30) p.d(bk, x, y, 0xFF7A1F2B);
                }
                for (int y = 1; y <= 19; y += 3) { p.d(o, 3, y, 0xFFC0C0C0); p.d(o, 12, y, 0xFFC0C0C0); }
            }
            case "fleece" -> {
                for (Bx bx : new Bx[]{b.bodyO, b.armsO[0], b.armsO[1]})
                    for (Rg r : bx.sides()) for (int y = 0; y < 22; y++) for (int x = 0; x < r.hw(); x++) if (hash(r.x * 2 + x, r.y * 2 + y, 12) % 5 == 0) p.d(r, x, y, shade(C, 1.1f));
                if (!open) p.h(o, 7, 0, 7, 10, flat(0xFF9A9A9A), 1);
                p.h(o, 4, 0, 11, 1, flat(shade(C, 0.85f)), 1);
            }
            case "work_jacket" -> {
                p.h(o, 2, 0, 13, 1, flat(0xFF4A3420), 1);
                p.h(o, 2, 6, 5, 10, flat(shade(C, 0.86f)), 1);
                p.h(o, 10, 6, 13, 10, flat(shade(C, 0.86f)), 1);
                if (!open) for (int y = 4; y <= 20; y += 4) p.d(o, 7, y, 0xFF2A2A2A);
            }
            default -> {}
        }
    }

    /**
     * Куртка на внешнем слое: корпус до ряда bottom (высокое разрешение), распахнута — середина пустая (видно верх),
     * рукава до ряда cuff (−1 — без рукавов).
     */
    private static void jacket(P p, Body b, Appearance a, int C, int bottom, boolean open, int cuff, String kind) {
        Paint c = cloth(C);
        for (Rg r : b.bodyO.sides()) p.h(r, 0, 0, r.hw() - 1, bottom, c, 1);
        p.all(b.bodyO.top, c, 1);
        if (open) {
            Rg f = b.bodyO.front;
            int half = kind.equals("suit") ? 2 : 3;
            for (int y = 0; y <= bottom; y++) {
                int w = y < 10 && (kind.equals("suit") || kind.equals("blazer")) ? half + Math.max(0, 3 - y / 3) : half;
                p.clear(f, 8 - w, y, 7 + w, y);
            }
        }
        for (Bx arm : b.armsO) {
            if (cuff < 0) {
                p.box(arm, (x, y) -> 0, 0);
                continue;
            }
            int end = a.rolled && !kind.equals("suit") && !kind.equals("puffer") ? 12 : cuff;
            for (Rg r : arm.sides()) p.h(r, 0, 0, r.hw() - 1, end, c, 1);
            p.all(arm.top, c, 1);
            if (end == 12) p.rows(arm, 10, 12, flat(shade(C, 1.12f)), 1);
        }
        if (cuff < 0) for (Rg r : new Rg[]{b.bodyO.right, b.bodyO.left}) p.h(r, 0, 0, r.hw() - 1, 3, (x, y) -> 0, 0);
    }

    // ================================================================== часы и браслеты
    private static void wrist(P p, Body b, Appearance a) {
        if (a.wrist.isEmpty()) return;
        String k = Appearance.id(a.wrist);
        int c = color(a.wrist, 0x161616);
        Bx arm = b.arms[1];
        if (k.equals("bracelet")) {
            for (Rg r : arm.sides()) for (int x = 0; x < r.hw(); x++) {
                p.d(r, x, 19, x % 2 == 0 ? c : 0xFFE8E8E8);
                p.d(r, x, 20, x % 2 == 1 ? shade(c, 0.8f) : 0xFFD4AF37);
            }
            return;
        }
        for (Rg r : arm.sides()) p.h(r, 0, 19, r.hw() - 1, 20, flat(c), 1);
        Rg face = arm.left;
        int fc = k.equals("smartwatch") ? 0xFF101820 : k.equals("gold_watch") ? 0xFFF4E8C0 : 0xFFE8E8E8;
        p.h(face, 2, 18, 5, 21, flat(k.equals("gold_watch") ? 0xFFD4AF37 : shade(c, 1.4f)), 1);
        p.h(face, 3, 19, 4, 20, flat(fc), 1);
        if (k.equals("smartwatch")) p.d(face, 3, 19, 0xFF28E6FF);
    }

    // ================================================================== потёртость и грязь
    private static void grime(P p, int level) {
        if (level <= 0) return;
        int chance = level == 1 ? 3 : level == 2 ? 8 : 15;
        for (int y = 0; y < SIZE; y++)
            for (int x = 0; x < SIZE; x++) {
                if (p.mask[y * SIZE + x] != 1) continue;
                int h = hash(x / (level >= 2 ? 2 : 1), y / (level >= 2 ? 2 : 1), 77) % 100;
                if (h >= chance) continue;
                int c = p.get(x, y);
                int g = h % 3 == 0 ? mix(c, 0xFF5A4630, 0.35f + level * 0.1f) : level == 1 ? mix(c, 0xFFFFFFFF, 0.12f) : shade(c, 0.8f - level * 0.05f);
                p.set(x, y, g, 1);
            }
    }
}
