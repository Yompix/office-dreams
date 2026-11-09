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
 * Скин 64×64 (раскладка скина игрока) из внешности: кожа и лицо, причёска на голове, верх, низ, обувь.
 * Пиксель-арт по мотивам tools/gen_assets.py skin(); куртки, пиджаки и пальто — на внешнем слое (объёмнее).
 * Одинаковая внешность — одна текстура; старые текстуры освобождаются.
 */
public final class SkinComposer {
    private SkinComposer() {}

    private static final Map<String, Identifier> CACHE = new LinkedHashMap<>(32, 0.75f, true);
    private static int counter;

    /** Текстура для внешности (только на потоке отрисовки; иначе null — будет обычный скин). */
    public static Identifier texture(Appearance a) {
        String key = a.key();
        Identifier id = CACHE.get(key);
        if (id != null) return id;
        if (!RenderSystem.isOnRenderThread()) return null;
        id = Novograd.id("dynskin/" + (counter++));
        MinecraftClient.getInstance().getTextureManager().registerTexture(id, new NativeImageBackedTexture(compose(a)));
        CACHE.put(key, id);
        if (CACHE.size() > 48) {
            var it = CACHE.entrySet().iterator();
            Identifier old = it.next().getValue();
            it.remove();
            MinecraftClient.getInstance().getTextureManager().destroyTexture(old);
        }
        return id;
    }

    // ------------------------------------------------------------------ рисование
    private record Rg(int x, int y, int w, int h) {}

    /** Грани коробки в раскладке скина. */
    private record Bx(Rg top, Rg bottom, Rg right, Rg front, Rg left, Rg back) {
        Rg[] sides() {
            return new Rg[]{right, front, left, back};
        }
    }

    private static final class P {
        final NativeImage img = new NativeImage(64, 64, true);

        P() {
            img.fillRect(0, 0, 64, 64, 0);
        }

        void px(int x, int y, int argb) {
            if (x < 0 || y < 0 || x >= 64 || y >= 64) return;
            int a = argb >>> 24, r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
            img.setColor(x, y, a << 24 | b << 16 | g << 8 | r);
        }

        void clear(int x, int y) {
            img.setColor(x, y, 0);
        }

        static Bx regions(int u, int v, int w, int h, int d) {
            return new Bx(new Rg(u + d, v, w, d), new Rg(u + d + w, v, w, d), new Rg(u, v + d, d, h), new Rg(u + d, v + d, w, h),
                    new Rg(u + d + w, v + d, d, h), new Rg(u + d + w + d, v + d, w, h));
        }

        /** Коробка, залитая цветом с лёгким «шумом» ткани. */
        Bx box(int u, int v, int w, int h, int d, int col) {
            Bx b = regions(u, v, w, h, d);
            for (Rg r : new Rg[]{b.top, b.bottom, b.right, b.front, b.left, b.back}) fill(r, 0, 0, r.w - 1, r.h - 1, col);
            return b;
        }

        void fill(Rg r, int x0, int y0, int x1, int y1, int col) {
            for (int y = Math.max(0, y0); y <= Math.min(y1, r.h - 1); y++)
                for (int x = Math.max(0, x0); x <= Math.min(x1, r.w - 1); x++) {
                    int gx = r.x + x, gy = r.y + y;
                    px(gx, gy, (col >>> 24) == 0 ? col : jitter(col, gx, gy));
                }
        }

        void dot(Rg r, int x, int y, int col) {
            if (x >= 0 && y >= 0 && x < r.w && y < r.h) px(r.x + x, r.y + y, col);
        }

        /** Строки y0..y1 на всех четырёх боках конечности/корпуса. */
        void rows(Bx b, int y0, int y1, int col) {
            for (Rg r : b.sides()) fill(r, 0, y0, r.w - 1, y1, col);
        }

        void clearBox(Bx b) {
            for (Rg r : new Rg[]{b.top, b.bottom, b.right, b.front, b.left, b.back})
                for (int y = 0; y < r.h; y++) for (int x = 0; x < r.w; x++) clear(r.x + x, r.y + y);
        }
    }

    private static int jitter(int c, int x, int y) {
        int h = (x * 131 + y * 7 + x * y * 17) * 1103515245 + 12345;
        float k = 1 + (((h >>> 16) % 13) - 6) / 255f;
        return shade(c, k);
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

    private static int opaque(int rgb) {
        return 0xFF000000 | rgb;
    }

    private static int hash(int x, int y, int seed) {
        int h = x * 374761393 + y * 668265263 + seed * 2147483647;
        h = (h ^ (h >>> 13)) * 1274126177;
        return (h ^ (h >>> 16)) & 0x7FFFFFFF;
    }

    // ------------------------------------------------------------------ сборка
    public static NativeImage compose(Appearance a) {
        P p = new P();
        int S = opaque(StyleCatalog.SKIN_TONES[Math.floorMod(a.skin, StyleCatalog.SKIN_TONES.length)]);
        int H = opaque(StyleCatalog.hairColor(a.hairColor));
        int aw = a.slim ? 3 : 4;
        head(p, a, S, H);
        StyleCatalog.Wear top = StyleCatalog.wear(Appearance.id(a.top)), pants = StyleCatalog.wear(Appearance.id(a.pants)), shoes = StyleCatalog.wear(Appearance.id(a.shoes));
        if (top == null) top = StyleCatalog.wear("tee");
        if (pants == null) pants = StyleCatalog.wear("jeans");
        if (shoes == null) shoes = StyleCatalog.wear("sneakers");
        int tc = opaque(top.color(Appearance.colorIndex(a.top))), pc = opaque(pants.color(Appearance.colorIndex(a.pants))),
                sc = opaque(shoes.color(Appearance.colorIndex(a.shoes)));
        // ноги — сначала брюки, потом обувь
        Bx[] legs = {p.box(0, 16, 4, 12, 4, pc), p.box(16, 48, 4, 12, 4, pc)};
        Bx[] legsO = {P.regions(0, 32, 4, 12, 4), P.regions(0, 48, 4, 12, 4)};
        Bx body = p.box(16, 16, 8, 12, 4, tc);
        Bx bodyO = P.regions(16, 32, 8, 12, 4);
        Bx[] arms = {p.box(40, 16, aw, 12, 4, tc), p.box(32, 48, aw, 12, 4, tc)};
        Bx[] armsO = {P.regions(40, 32, aw, 12, 4), P.regions(48, 48, aw, 12, 4)};
        pants(p, pants.id(), pc, S, legs, body);
        shoes(p, shoes.id(), sc, S, legs, legsO);
        top(p, top.id(), tc, S, body, bodyO, arms, armsO, legsO);
        return p.img;
    }

    // ------------------------------------------------------------------ голова
    private static void head(P p, Appearance a, int S, int H) {
        Bx r = p.box(0, 0, 8, 8, 8, S);
        Rg F = r.front;
        String style = a.hair;
        boolean bald = style.equals("bald");
        if (!bald) {
            int fringe = switch (style) {
                case "buzz", "mohawk" -> 0;
                case "curly", "bob", "afro" -> 2;
                default -> 1;
            };
            int side = switch (style) {
                case "buzz", "mohawk" -> 1;
                case "curly", "ponytail", "afro" -> 3;
                case "long" -> 7;
                case "bob" -> 5;
                default -> 2;
            };
            int hairTop = style.equals("buzz") || style.equals("mohawk") ? mix(S, H, 0.55f) : H;
            p.fill(r.top, 0, 0, 7, 7, hairTop);
            if (style.equals("mohawk")) p.fill(r.top, 3, 0, 4, 7, H);
            p.fill(F, 0, 0, 7, fringe - 1, H);
            if (style.equals("buzz") || style.equals("mohawk")) p.fill(F, 0, 0, 7, 0, mix(S, H, 0.55f));
            p.fill(r.right, 0, 0, 7, side - 1, style.equals("buzz") || style.equals("mohawk") ? mix(S, H, 0.5f) : H);
            p.fill(r.left, 0, 0, 7, side - 1, style.equals("buzz") || style.equals("mohawk") ? mix(S, H, 0.5f) : H);
            boolean longBack = style.equals("long") || style.equals("bob") || style.equals("ponytail");
            p.fill(r.back, 0, 0, 7, longBack ? 7 : side, style.equals("buzz") || style.equals("mohawk") ? mix(S, H, 0.5f) : H);
            if (style.equals("long") || style.equals("bob")) {
                for (int y = 2; y <= (style.equals("long") ? 5 : 3); y++) {
                    p.dot(F, 0, y, H);
                    p.dot(F, 7, y, H);
                }
            }
            if (style.equals("curly") || style.equals("afro")) for (int x = 0; x < 8; x += 2) p.dot(F, x, 2, shade(H, 0.8f));
            if (style.equals("slick")) for (int x = 2; x <= 7; x++) p.dot(F, x, x < 5 ? 0 : 1, shade(H, 1.35f));
        } else p.fill(r.top, 2, 2, 5, 5, shade(S, 1.05f));
        // лицо: брови, глаза, нос, рот
        int brow = shade(bald ? S : H, 0.7f);
        p.dot(F, 1, 3, brow);
        p.dot(F, 2, 3, brow);
        p.dot(F, 5, 3, brow);
        p.dot(F, 6, 3, brow);
        int eye = a.skin >= 4 ? 0xFF1A1210 : 0xFF3B4A6A;
        p.dot(F, 1, 4, 0xFFFFFFFF);
        p.dot(F, 6, 4, 0xFFFFFFFF);
        p.dot(F, 2, 4, eye);
        p.dot(F, 5, 4, eye);
        p.dot(F, 3, 5, shade(S, 0.88f));
        p.dot(F, 4, 5, shade(S, 0.88f));
        int lips = a.slim ? 0xFFD6455A : 0xFFA0524A;
        p.dot(F, 3, 6, lips);
        p.dot(F, 4, 6, lips);
        if (a.slim) {
            p.dot(F, 1, 3, 0xFF1A1010);
            p.dot(F, 6, 3, 0xFF1A1010);
        }
        // щетина / усы / эспаньолка / борода (объёмные усы и борода дорисует HairFeature)
        int B = shade(H, 0.95f);
        switch (a.beard) {
            case "stubble" -> {
                int st = mix(S, H, 0.38f);
                for (int y = 5; y <= 7; y++)
                    for (int x = 0; x < 8; x++) {
                        if (y == 5 && x > 1 && x < 6) continue;
                        if (y == 6 && (x == 3 || x == 4)) continue;
                        if (((x + y) & 1) == 0 || y == 7) p.dot(F, x, y, st);
                    }
                p.fill(r.right, 0, 6, 7, 7, st);
                p.fill(r.left, 0, 6, 7, 7, st);
            }
            case "mustache" -> {
                for (int x = 2; x <= 5; x++) p.dot(F, x, 5, B);
            }
            case "goatee" -> {
                p.dot(F, 3, 7, B);
                p.dot(F, 4, 7, B);
                for (int x = 2; x <= 5; x++) p.dot(F, x, 5, B);
            }
            case "beard", "full" -> {
                p.fill(F, 0, 6, 7, 7, B);
                p.dot(F, 0, 5, B);
                p.dot(F, 7, 5, B);
                p.dot(F, 2, 6, 0xFF5A2A22);
                p.dot(F, 3, 6, 0xFF8E3F36);
                p.dot(F, 4, 6, 0xFF8E3F36);
                p.dot(F, 5, 6, 0xFF5A2A22);
                if (a.beard.equals("full")) for (int x = 2; x <= 5; x++) p.dot(F, x, 5, B);
                p.fill(r.right, 0, 6, 7, 7, B);
                p.fill(r.left, 0, 6, 7, 7, B);
            }
            default -> {}
        }
        // слой головы (32,0): хвост сзади
        if (style.equals("ponytail")) {
            Bx hat = P.regions(32, 0, 8, 8, 8);
            p.fill(hat.back, 3, 1, 4, 7, shade(H, 0.9f));
        }
    }

    // ------------------------------------------------------------------ брюки
    private static void pants(P p, String kind, int C, int S, Bx[] legs, Bx body) {
        Rg f = body.front, bk = body.back;
        int belt = 0xFF2A1A10, buckle = 0xFFC0C0C0;
        switch (kind) {
            case "sweats" -> {
                for (Bx l : legs) {
                    p.rows(l, 9, 9, shade(C, 0.8f));
                    p.fill(l.right, 1, 0, 1, 8, 0xFFF2F2F2);
                    p.fill(l.left, 2, 0, 2, 8, 0xFFF2F2F2);
                }
                belt = shade(C, 1.15f);
                buckle = 0xFFF2F2F2;
            }
            case "shorts" -> {
                for (Bx l : legs) {
                    p.rows(l, 5, 5, shade(C, 0.82f));
                    p.rows(l, 6, 11, S);
                    p.fill(l.bottom, 0, 0, 3, 3, S);
                }
            }
            case "cargo" -> {
                int[] camo = {shade(C, 0.7f), shade(C, 1.25f), mix(C, 0xFF3A2A18, 0.5f)};
                for (Bx l : legs) {
                    for (Rg r : l.sides())
                        for (int y = 0; y < 10; y++)
                            for (int x = 0; x < r.w; x++) {
                                int h = hash((r.x + x) / 2, (r.y + y) / 2, 7) % 5;
                                if (h < 3) p.dot(r, x, y, camo[h]);
                            }
                    p.fill(l.right, 0, 4, 3, 4, shade(C, 0.6f));
                    p.fill(l.right, 0, 4, 0, 7, shade(C, 0.6f));
                    p.fill(l.left, 0, 4, 3, 4, shade(C, 0.6f));
                    p.fill(l.left, 3, 4, 3, 7, shade(C, 0.6f));
                }
            }
            case "slacks" -> {
                for (Bx l : legs) {
                    p.fill(l.front, 1, 0, 1, 9, shade(C, 0.82f));
                    p.fill(l.back, 2, 0, 2, 9, shade(C, 0.85f));
                }
                belt = 0xFF111111;
                buckle = 0xFFD4AF37;
            }
            case "leather_pants" -> {
                for (Bx l : legs) for (Rg r : l.sides()) for (int y = 0; y < 10; y++) if (hash(r.x, r.y + y, 3) % 4 == 0) p.dot(r, 1, y, shade(C, 2.2f));
                belt = 0xFF0A0A0A;
                buckle = 0xFFC0C0C0;
            }
            default -> { // джинсы
                for (Bx l : legs) {
                    p.fill(l.right, 0, 0, 0, 9, shade(C, 1.18f));
                    p.fill(l.left, 3, 0, 3, 9, shade(C, 1.18f));
                    p.fill(l.front, 0, 0, 3, 0, shade(C, 0.85f));
                    p.rows(l, 9, 9, shade(C, 0.9f));
                }
            }
        }
        // ремень и верх брюк на корпусе (ряды 10–11)
        p.fill(f, 0, 10, 7, 11, C);
        p.fill(bk, 0, 10, 7, 11, C);
        p.fill(body.right, 0, 10, 3, 11, C);
        p.fill(body.left, 0, 10, 3, 11, C);
        p.fill(f, 0, 10, 7, 10, belt);
        p.fill(bk, 0, 10, 7, 10, belt);
        p.fill(body.right, 0, 10, 3, 10, belt);
        p.fill(body.left, 0, 10, 3, 10, belt);
        p.dot(f, 3, 10, buckle);
        p.dot(f, 4, 10, buckle);
        p.fill(body.bottom, 0, 0, 7, 3, C);
        if (kind.equals("sweats")) {
            p.dot(f, 3, 11, 0xFFF2F2F2);
            p.dot(f, 4, 11, 0xFFF2F2F2);
        }
    }

    // ------------------------------------------------------------------ обувь
    private static void shoes(P p, String kind, int C, int S, Bx[] legs, Bx[] legsO) {
        int white = 0xFFF4F4F4;
        for (int i = 0; i < 2; i++) {
            Bx l = legs[i], o = legsO[i];
            switch (kind) {
                case "canvas" -> {
                    p.rows(l, 8, 10, C);
                    p.rows(l, 11, 11, white);
                    p.fill(l.front, 0, 10, 3, 11, white);
                    p.dot(l.front, 1, 8, white);
                    p.dot(l.front, 2, 9, white);
                    p.fill(l.bottom, 0, 0, 3, 3, white);
                    p.rows(o, 10, 11, 0);
                    p.fill(o.front, 0, 11, 3, 11, white);
                }
                case "slides" -> {
                    p.rows(l, 9, 10, S);
                    p.fill(l.front, 0, 10, 3, 10, C);
                    p.fill(l.right, 0, 10, 3, 10, C);
                    p.fill(l.left, 0, 10, 3, 10, C);
                    p.rows(l, 11, 11, shade(C, 0.7f));
                    p.fill(l.bottom, 0, 0, 3, 3, shade(C, 0.7f));
                }
                case "loafers" -> {
                    p.rows(l, 10, 10, C);
                    p.rows(l, 11, 11, shade(C, 0.55f));
                    p.dot(l.front, 1, 10, shade(C, 1.9f) | 0xFF000000);
                    p.fill(l.bottom, 0, 0, 3, 3, shade(C, 0.5f));
                    if (C == opaque(0xF2F2F2)) p.rows(l, 11, 11, 0xFF2A2A2A);
                }
                case "boots" -> {
                    p.rows(l, 7, 10, C);
                    p.fill(l.right, 1, 7, 2, 9, shade(C, 0.6f));
                    p.fill(l.left, 1, 7, 2, 9, shade(C, 0.6f));
                    p.rows(l, 11, 11, 0xFF1A1410);
                    p.fill(l.bottom, 0, 0, 3, 3, 0xFF1A1410);
                    for (Rg r : o.sides()) p.fill(r, 0, 10, r.w - 1, 11, shade(C, 0.95f));
                    p.fill(o.front, 0, 11, 3, 11, 0xFF1A1410);
                }
                case "lux_sneakers" -> {
                    p.rows(l, 9, 10, C);
                    p.fill(l.right, 0, 10, 3, 10, 0xFFD4AF37);
                    p.fill(l.left, 0, 10, 3, 10, 0xFFD4AF37);
                    p.dot(l.front, 1, 9, 0xFFD4AF37);
                    p.dot(l.front, 2, 9, 0xFFD4AF37);
                    int sole = C == opaque(0x141414) ? 0xFFD4AF37 : white;
                    p.rows(l, 11, 11, sole);
                    p.fill(l.bottom, 0, 0, 3, 3, sole);
                    for (Rg r : o.sides()) p.fill(r, 0, 10, r.w - 1, 10, C);
                    for (Rg r : o.sides()) p.fill(r, 0, 11, r.w - 1, 11, sole);
                }
                default -> { // кроссовки
                    p.rows(l, 9, 10, C);
                    int accent = C == opaque(0xF0F0F0) ? 0xFF1E50B4 : white;
                    p.fill(l.right, 1, 10, 2, 10, accent);
                    p.fill(l.left, 1, 10, 2, 10, accent);
                    p.dot(l.front, 1, 9, white);
                    p.dot(l.front, 2, 9, white);
                    p.rows(l, 11, 11, white);
                    p.fill(l.bottom, 0, 0, 3, 3, white);
                    for (Rg r : o.sides()) p.fill(r, 0, 10, r.w - 1, 10, C);
                    for (Rg r : o.sides()) p.fill(r, 0, 11, r.w - 1, 11, white);
                }
            }
        }
    }

    // ------------------------------------------------------------------ верх
    private static void top(P p, String kind, int C, int S, Bx body, Bx bodyO, Bx[] arms, Bx[] armsO, Bx[] legsO) {
        Rg F = body.front;
        int white = 0xFFF2F2F2;
        // по умолчанию — рукава до локтя, ниже кожа
        java.util.function.IntConsumer sleeves = len -> {
            for (Bx a : arms) {
                p.rows(a, len, 11, S);
                p.fill(a.bottom, 0, 0, 3, 3, S);
            }
        };
        switch (kind) {
            case "tank" -> {
                sleeves.accept(0);
                for (Bx a : arms) p.fill(a.top, 0, 0, 3, 3, S);
                p.fill(F, 2, 0, 5, 1, S);
                p.fill(body.back, 2, 0, 5, 0, S);
                p.fill(body.top, 0, 0, 7, 3, S);
                p.fill(body.top, 0, 0, 1, 3, C);
                p.fill(body.top, 6, 0, 7, 3, C);
            }
            case "tee", "tee_print" -> {
                sleeves.accept(4);
                for (Bx a : arms) p.rows(a, 3, 3, shade(C, 0.9f));
                p.fill(F, 2, 0, 5, 0, shade(S, 0.95f));
                if (kind.equals("tee_print")) {
                    int ink = C == opaque(0xE0407A) ? 0xFF28E6FF : 0xFFFF2E88;
                    int ink2 = C == opaque(0xF2F2F2) ? 0xFF161616 : 0xFFFFC428;
                    // неоновая «Н» и полоса-горизонт
                    for (int y = 3; y <= 7; y++) {
                        p.dot(F, 2, y, ink);
                        p.dot(F, 5, y, ink);
                    }
                    p.dot(F, 3, 5, ink);
                    p.dot(F, 4, 5, ink);
                    p.fill(F, 1, 8, 6, 8, ink2);
                }
            }
            case "polo" -> {
                sleeves.accept(4);
                for (Bx a : arms) p.rows(a, 3, 3, shade(C, 0.85f));
                p.fill(F, 1, 0, 6, 0, shade(C, 1.15f));
                p.fill(F, 3, 1, 4, 3, shade(C, 0.9f));
                p.dot(F, 3, 1, white);
                p.dot(F, 3, 3, white);
                p.fill(body.back, 0, 0, 7, 0, shade(C, 1.15f));
            }
            case "hawaii" -> {
                sleeves.accept(4);
                for (Bx b : new Bx[]{body, arms[0], arms[1]})
                    for (Rg r : b.sides())
                        for (int y = 0; y < (b == body ? 10 : 4); y++)
                            for (int x = 0; x < r.w; x++) {
                                int h = hash(r.x + x, r.y + y, 11) % 23;
                                if (h == 0 || h == 5) p.dot(r, x, y, 0xFFFFF4E8);
                                else if (h == 9) p.dot(r, x, y, 0xFFFFD54F);
                                else if (h == 13 || h == 17) p.dot(r, x, y, 0xFF1F5E2A);
                            }
                p.fill(F, 3, 0, 4, 1, S);
                p.fill(F, 2, 0, 2, 0, shade(C, 1.2f));
                p.fill(F, 5, 0, 5, 0, shade(C, 1.2f));
            }
            case "flannel" -> {
                sleeves.accept(10);
                for (Bx b : new Bx[]{body, arms[0], arms[1]})
                    for (Rg r : b.sides())
                        for (int y = 0; y < (b == body ? 10 : 10); y++)
                            for (int x = 0; x < r.w; x++) {
                                int gx = r.x + x, gy = r.y + y;
                                boolean vx = gx % 3 == 0, hy = gy % 3 == 0;
                                if (vx && hy) p.dot(r, x, y, shade(C, 0.45f));
                                else if (vx || hy) p.dot(r, x, y, shade(C, 0.7f));
                                else if (gx % 3 == 1 && gy % 3 == 1) p.dot(r, x, y, shade(C, 1.3f));
                            }
                p.fill(F, 3, 0, 3, 9, shade(C, 0.6f));
                p.fill(F, 2, 0, 5, 0, shade(C, 1.2f));
            }
            case "hoodie" -> {
                sleeves.accept(10);
                for (Bx a : arms) p.rows(a, 9, 9, shade(C, 0.85f));
                p.fill(F, 1, 6, 6, 9, shade(C, 0.86f));
                p.fill(F, 1, 6, 6, 6, shade(C, 0.75f));
                p.fill(F, 3, 1, 3, 4, white);
                p.fill(F, 4, 1, 4, 4, white);
                p.fill(body.back, 1, 0, 6, 2, shade(C, 0.82f));
                p.fill(F, 2, 0, 5, 0, shade(C, 0.75f));
                p.fill(body.top, 1, 0, 6, 3, shade(C, 0.82f));
            }
            case "tracksuit" -> {
                sleeves.accept(10);
                for (Bx a : arms) p.rows(a, 9, 9, shade(C, 0.8f));
                p.fill(arms[0].right, 0, 0, 0, 8, white);
                p.fill(arms[0].right, 2, 0, 2, 8, white);
                p.fill(arms[1].left, 1, 0, 1, 8, white);
                p.fill(arms[1].left, 3, 0, 3, 8, white);
                p.fill(body.right, 1, 0, 1, 9, white);
                p.fill(body.left, 2, 0, 2, 9, white);
                p.fill(F, 3, 0, 3, 9, 0xFFD0D0D0);
                p.fill(F, 4, 0, 4, 9, shade(C, 0.7f));
                p.fill(F, 1, 0, 6, 0, shade(C, 0.75f));
                p.fill(F, 0, 9, 7, 9, shade(C, 0.8f));
                p.fill(body.back, 0, 0, 7, 0, shade(C, 0.75f));
            }
            case "denim", "leather", "bomber" -> {
                // под курткой — футболка
                int inner = kind.equals("leather") ? 0xFFEDEDED : kind.equals("denim") ? 0xFFF4F4F4 : 0xFF1E1E1E;
                p.fill(F, 0, 0, 7, 9, inner);
                for (Bx a : arms) p.rows(a, 0, 9, inner);
                sleeves.accept(10);
                jacket(p, C, bodyO, armsO, kind.equals("bomber") ? 9 : 10, kind.equals("bomber") ? 0 : 2, 9);
                Rg o = bodyO.front;
                if (kind.equals("denim")) {
                    for (int x = 0; x < 8; x++) if (x < 3 || x > 4) p.dot(o, x, 3, shade(C, 1.25f));
                    p.dot(o, 1, 2, 0xFFC08A40);
                    p.dot(o, 6, 2, 0xFFC08A40);
                    for (Rg r : armsO[0].sides()) p.fill(r, 0, 9, r.w - 1, 9, shade(C, 1.2f));
                    for (Rg r : armsO[1].sides()) p.fill(r, 0, 9, r.w - 1, 9, shade(C, 1.2f));
                } else if (kind.equals("leather")) {
                    for (Bx b : new Bx[]{bodyO, armsO[0], armsO[1]})
                        for (Rg r : b.sides())
                            for (int y = 0; y < 10; y++)
                                if (hash(r.x, r.y + y, 5) % 5 == 0) p.dot(r, (y * 3) % r.w, y, shade(C, 2.6f) | 0xFF000000);
                    p.fill(o, 2, 0, 2, 3, shade(C, 1.8f));
                    p.fill(o, 5, 0, 5, 3, shade(C, 1.8f));
                    p.dot(o, 1, 6, 0xFFC0C0C0);
                } else {
                    p.fill(o, 3, 0, 4, 9, C);
                    p.fill(o, 3, 0, 3, 9, 0xFFC0C0C0);
                    p.fill(o, 0, 9, 7, 9, shade(C, 0.7f));
                    p.fill(o, 1, 0, 6, 0, shade(C, 0.7f));
                    for (Rg r : armsO[0].sides()) p.fill(r, 0, 8, r.w - 1, 8, shade(C, 0.7f));
                    for (Rg r : armsO[1].sides()) p.fill(r, 0, 8, r.w - 1, 8, shade(C, 0.7f));
                    p.fill(bodyO.back, 0, 9, 7, 9, shade(C, 0.7f));
                }
            }
            case "blazer" -> {
                boolean light = ((C >> 16 & 255) + (C >> 8 & 255) + (C & 255)) > 450;
                int inner = light ? 0xFF1E1E1E : 0xFFF2F2F2;
                p.fill(F, 0, 0, 7, 9, inner);
                for (Bx a : arms) p.rows(a, 0, 10, light ? C : inner);
                sleeves.accept(11);
                jacket(p, C, bodyO, armsO, 11, 2, 10);
                Rg o = bodyO.front;
                p.fill(o, 2, 0, 2, 4, shade(C, 0.84f));
                p.fill(o, 5, 0, 5, 4, shade(C, 0.84f));
                p.dot(o, 1, 3, 0xFFFF2E88);
                p.dot(o, 6, 7, shade(C, 0.6f));
            }
            case "suit" -> {
                int shirt = C == opaque(0xEDEDED) ? 0xFF1E1E2A : 0xFFF6F6F6;
                int tie = C == opaque(0xEDEDED) ? 0xFFEDEDED : 0xFF8B1A1A;
                p.fill(F, 0, 0, 7, 9, shirt);
                for (Bx a : arms) p.rows(a, 0, 10, shirt);
                sleeves.accept(11);
                // жилет
                p.fill(F, 1, 3, 6, 9, shade(C, 0.9f));
                p.fill(F, 3, 1, 4, 8, tie);
                p.dot(F, 3, 1, shade(tie, 0.8f));
                p.dot(F, 4, 1, shade(tie, 0.8f));
                p.fill(F, 0, 0, 7, 0, shirt);
                jacket(p, C, bodyO, armsO, 11, 2, 9);
                Rg o = bodyO.front;
                for (int y = 0; y <= 5; y++) {
                    p.dot(o, 2, y, y < 4 ? shade(C, 0.8f) : C);
                    p.dot(o, 5, y, y < 4 ? shade(C, 0.8f) : C);
                }
                for (int y = 6; y <= 10; y++) {
                    p.dot(o, 3, y, C);
                    p.dot(o, 4, y, C);
                }
                p.dot(o, 3, 7, shade(C, 0.55f));
                p.dot(o, 1, 3, 0xFFF6F6F6);
                for (Bx a : armsO) for (Rg r : a.sides()) p.fill(r, 0, 9, r.w - 1, 9, 0);
            }
            case "coat" -> {
                int inner = shade(C, 0.45f);
                p.fill(F, 0, 0, 7, 9, inner);
                for (Bx a : arms) p.rows(a, 0, 10, inner);
                sleeves.accept(11);
                jacket(p, C, bodyO, armsO, 12, 1, 10);
                Rg o = bodyO.front;
                p.fill(o, 3, 0, 4, 11, C);
                p.fill(o, 4, 2, 4, 11, shade(C, 0.75f));
                p.fill(o, 1, 0, 2, 3, shade(C, 1.15f));
                p.fill(o, 5, 0, 6, 3, shade(C, 1.15f));
                for (int y = 5; y <= 10; y += 2) p.dot(o, 3, y, 0xFF1A1A1A);
                p.fill(o, 0, 8, 7, 8, shade(C, 0.8f));
                // полы пальто на бёдрах
                for (Bx l : legsO) for (Rg r : l.sides()) p.fill(r, 0, 0, r.w - 1, 5, C);
                p.fill(legsO[0].front, 3, 0, 3, 5, shade(C, 0.75f));
                p.fill(legsO[1].front, 0, 0, 0, 5, shade(C, 0.75f));
            }
            default -> sleeves.accept(4);
        }
    }

    /**
     * Куртка на внешнем слое: корпус до ряда bottom, распахнута посередине на ширину open,
     * рукава до ряда cuff (ниже — рубашка/кисть с основного слоя).
     */
    private static void jacket(P p, int C, Bx bodyO, Bx[] armsO, int bottom, int open, int cuff) {
        for (Rg r : bodyO.sides()) p.fill(r, 0, 0, r.w - 1, bottom - 1, C);
        p.fill(bodyO.top, 0, 0, 7, 3, C);
        if (open > 0) {
            Rg f = bodyO.front;
            int x0 = 4 - open / 2 - (open % 2), x1 = 3 + open / 2;
            for (int y = 0; y < bottom; y++) for (int x = x0; x <= x1; x++) p.clear(f.x + x, f.y + y);
        }
        for (Bx a : armsO) {
            for (Rg r : a.sides()) p.fill(r, 0, 0, r.w - 1, cuff, C);
            p.fill(a.top, 0, 0, a.top.w - 1, 3, C);
        }
    }
}
