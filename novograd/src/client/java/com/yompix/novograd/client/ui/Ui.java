package com.yompix.novograd.client.ui;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.yompix.novograd.Novograd;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * Общий стиль «Тёмные улицы»: почти чёрные полупрозрачные панели, косые плашки, жирные заголовки с обводкой,
 * неон (розовый, бирюзовый), золото для денег и титулов, зерно плёнки и полосы «кино».
 */
public final class Ui {
    private Ui() {}

    public static final int PINK = 0xFFFF2E88, CYAN = 0xFF28E6FF, GOLD = 0xFFFFC428, MONEY = 0xFF62E57E, RED = 0xFFFF3B4A,
            WHITE = 0xFFFFFFFF, DIM = 0xFFA9A3BC, PANEL = 0xE00B0812, PANEL_LIGHT = 0xE0181222, INK = 0xFF07050C;

    public static final Identifier LOGO = Novograd.id("textures/gui/ui/logo.png"), SUBTITLE = Novograd.id("textures/gui/ui/subtitle.png"),
            PASSED = Novograd.id("textures/gui/ui/passed.png"), PAUSE = Novograd.id("textures/gui/ui/pause.png"),
            SETTINGS = Novograd.id("textures/gui/ui/settings.png"), SKYLINE = Novograd.id("textures/gui/ui/skyline.png"),
            SKYLINE_FG = Novograd.id("textures/gui/ui/skyline_fg.png"), GRAIN = Novograd.id("textures/gui/ui/grain.png"),
            STAR = Novograd.id("textures/gui/ui/star.png"), STAR_EMPTY = Novograd.id("textures/gui/ui/star_empty.png");
    public static final int[] LOGO_SIZE = {1077, 230}, SUBTITLE_SIZE = {876, 130}, PASSED_SIZE = {1185, 167}, PAUSE_SIZE = {501, 144},
            SETTINGS_SIZE = {896, 173}, SKYLINE_SIZE = {1280, 720}, FG_SIZE = {1024, 256}, PANEL_SIZE = {640, 360};

    public static Identifier panel(int i) {
        return Novograd.id("textures/gui/ui/panel" + (i % 4 + 1) + ".png");
    }

    public static int alpha(int color, float a) {
        int base = color >>> 24;
        return (int) (base * MathHelper.clamp(a, 0, 1)) << 24 | (color & 0xFFFFFF);
    }

    public static float now() {
        return (System.currentTimeMillis() % 1_000_000L) / 1000f;
    }

    public static float easeOut(float k) {
        k = MathHelper.clamp(k, 0, 1);
        return 1 - (1 - k) * (1 - k) * (1 - k);
    }

    /** Плавно ведёт значение к цели (для анимаций наведения). */
    public static float approach(float cur, float target, float speed) {
        return cur + (target - cur) * MathHelper.clamp(speed, 0, 1);
    }

    // ------------------------------------------------------------------ текст
    /** Текст с чёрной обводкой по 8 сторонам — читается на любом фоне. */
    public static void outlined(DrawContext ctx, TextRenderer tr, String s, int x, int y, int color) {
        int a = color >>> 24;
        if (a < 8) return;
        int ink = (int) (a * 0.9f) << 24 | 0x07050C;
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                if (dx != 0 || dy != 0) ctx.drawText(tr, s, x + dx, y + dy, ink, false);
        ctx.drawText(tr, s, x + 1, y + 2, ink, false);
        ctx.drawText(tr, s, x, y, color, false);
    }

    /** Крупный текст: масштаб, обводка; align: 0 — слева, 1 — по центру, 2 — справа. */
    public static void big(DrawContext ctx, TextRenderer tr, String s, float x, float y, float scale, int color, int align) {
        var m = ctx.getMatrices();
        m.push();
        m.translate(x, y, 0);
        m.scale(scale, scale, 1);
        int w = tr.getWidth(s);
        int ox = align == 1 ? -w / 2 : align == 2 ? -w : 0;
        outlined(ctx, tr, s, ox, 0, color);
        m.pop();
    }

    /** Мелкий текст (масштаб < 1) без обводки, с тенью. */
    public static void small(DrawContext ctx, TextRenderer tr, String s, float x, float y, float scale, int color, int align) {
        var m = ctx.getMatrices();
        m.push();
        m.translate(x, y, 0);
        m.scale(scale, scale, 1);
        int w = tr.getWidth(s);
        boolean light = ((color >> 16 & 255) + (color >> 8 & 255) + (color & 255)) > 200;
        ctx.drawText(tr, s, align == 1 ? -w / 2 : align == 2 ? -w : 0, 0, color, light);
        m.pop();
    }

    /** Заголовок с разрядкой букв (для «ЗАДАНИЯ», «ГАРАЖ»…). */
    public static int spaced(DrawContext ctx, TextRenderer tr, String s, int x, int y, int color, int gap) {
        int cx = x;
        for (int i = 0; i < s.length(); i++) {
            String ch = String.valueOf(s.charAt(i));
            ctx.drawText(tr, ch, cx, y, color, true);
            cx += tr.getWidth(ch) + gap;
        }
        return cx - x;
    }

    // ------------------------------------------------------------------ фигуры
    /** Косая плашка (параллелограмм): верх сдвинут вправо на skew. */
    public static void slant(DrawContext ctx, int x, int y, int w, int h, int skew, int color) {
        if (h <= 0 || w <= 0) return;
        for (int r = 0; r < h; r++) {
            int off = skew * (h - 1 - r) / Math.max(1, h - 1);
            ctx.fill(x + off, y + r, x + off + w, y + r + 1, color);
        }
    }

    /** Тёмная панель с цветной полоской сверху. */
    public static void panel(DrawContext ctx, int x, int y, int w, int h, int accent) {
        ctx.fill(x + 3, y + 3, x + w + 3, y + h + 3, 0x50000000);
        ctx.fill(x, y, x + w, y + h, PANEL);
        ctx.fill(x, y, x + w, y + 2, accent);
        ctx.fill(x, y + h - 1, x + w, y + h, 0x30FFFFFF);
    }

    /** Заголовок окна: косая цветная плашка с крупным текстом. */
    public static void header(DrawContext ctx, TextRenderer tr, String title, int x, int y, int accent) {
        String t = title.toUpperCase();
        int w = (int) (tr.getWidth(t) * 2f) + 26;
        slant(ctx, x, y, w, 24, 8, accent);
        slant(ctx, x + w + 4, y, 6, 24, 8, alpha(accent, 0.6f));
        slant(ctx, x + w + 14, y, 3, 24, 8, alpha(accent, 0.3f));
        big(ctx, tr, t, x + 10, y + 5, 2f, WHITE, 0);
    }

    /** Полоска прогресса. */
    public static void bar(DrawContext ctx, int x, int y, int w, int h, float k, int color) {
        ctx.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xC0000000);
        ctx.fill(x, y, x + w, y + h, alpha(color, 0.25f));
        ctx.fill(x, y, x + (int) (w * MathHelper.clamp(k, 0, 1)), y + h, color);
    }

    // ------------------------------------------------------------------ картинки
    public static void tex(DrawContext ctx, Identifier id, float x, float y, float w, float h, int tw, int th, float alpha) {
        tint(ctx, id, x, y, w, h, tw, th, 1, 1, 1, alpha);
    }

    /** Картинка с подкраской цветом (r, g, b). */
    public static void tint(DrawContext ctx, Identifier id, float x, float y, float w, float h, int tw, int th, float r, float g, float b, float alpha) {
        if (alpha <= 0.003f) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        ctx.setShaderColor(r, g, b, Math.min(1, alpha));
        var m = ctx.getMatrices();
        m.push();
        m.translate(x, y, 0);
        m.scale(w / tw, h / th, 1);
        ctx.drawTexture(id, 0, 0, 0, 0, tw, th, tw, th);
        m.pop();
        ctx.setShaderColor(1, 1, 1, 1);
    }

    /** Картинка с подкраской (r,g,b) и сложением света — для свечений. */
    public static void glow(DrawContext ctx, Identifier id, float x, float y, float w, float h, int tw, int th, float r, float g, float b, float alpha) {
        if (alpha <= 0.003f) return;
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
        ctx.setShaderColor(r, g, b, Math.min(1, alpha));
        var m = ctx.getMatrices();
        m.push();
        m.translate(x, y, 0);
        m.scale(w / tw, h / th, 1);
        ctx.drawTexture(id, 0, 0, 0, 0, tw, th, tw, th);
        m.pop();
        ctx.setShaderColor(1, 1, 1, 1);
        RenderSystem.defaultBlendFunc();
    }

    /** Картинка «обложкой»: заполняет прямоугольник, лишнее обрезается; pan — сдвиг 0..1. */
    public static void cover(DrawContext ctx, Identifier id, int x, int y, int w, int h, int tw, int th, float zoom, float pan, float alpha) {
        float s = Math.max(w / (float) tw, h / (float) th) * zoom;
        float dw = tw * s, dh = th * s;
        ctx.enableScissor(x, y, x + w, y + h);
        tex(ctx, id, x + (w - dw) * pan, y + (h - dh) / 2, dw, dh, tw, th, alpha);
        ctx.disableScissor();
    }

    /**
     * Комикс-панель: картинка 640×360 обрезается под пропорции w×h (pan 0..1 — какую часть показать),
     * белая рамка, тень; рисуется с поворотом rot вокруг центра (cx, cy).
     */
    public static void comic(DrawContext ctx, Identifier id, float cx, float cy, float w, float h, float rot, float scale, float alpha, float pan) {
        if (alpha <= 0.003f || w < 2 || h < 2) return;
        var m = ctx.getMatrices();
        m.push();
        m.translate(cx, cy, 0);
        m.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(rot));
        m.scale(scale, scale, 1);
        int iw = (int) w, ih = (int) h;
        ctx.fill(-iw / 2 + 6, -ih / 2 + 7, iw / 2 + 6, ih / 2 + 7, (int) (alpha * 160) << 24);
        ctx.fill(-iw / 2 - 3, -ih / 2 - 3, iw / 2 + 3, ih / 2 + 3, alpha(0xFFF4EEE0, alpha));
        ctx.fill(-iw / 2 - 1, -ih / 2 - 1, iw / 2 + 1, ih / 2 + 1, alpha(0xFF000000, alpha));
        float aspect = w / h;
        int rw = 640, rh = 360;
        if (aspect < 640f / 360f) rw = (int) (360 * aspect);
        else rh = (int) (640 / aspect);
        float u = (640 - rw) * pan, v = (360 - rh) / 2f;
        RenderSystem.enableBlend();
        ctx.setShaderColor(1, 1, 1, alpha);
        ctx.drawTexture(id, -iw / 2, -ih / 2, iw, ih, u, v, rw, rh, 640, 360);
        ctx.setShaderColor(1, 1, 1, 1);
        m.pop();
    }

    /** Зерно плёнки поверх кадра. */
    public static void grain(DrawContext ctx, int w, int h, float alpha) {
        if (alpha <= 0) return;
        long f = System.currentTimeMillis() / 50;
        int ox = (int) (f * 37 % 128), oy = (int) (f * 71 % 128);
        RenderSystem.enableBlend();
        ctx.setShaderColor(1, 1, 1, alpha);
        for (int y = -oy; y < h; y += 128)
            for (int x = -ox; x < w; x += 128) ctx.drawTexture(GRAIN, x, y, 0, 0, 128, 128, 128, 128);
        ctx.setShaderColor(1, 1, 1, 1);
    }

    /** Виньетка: края кадра темнее. */
    public static void vignette(DrawContext ctx, int w, int h, float k) {
        int a = (int) (170 * k);
        ctx.fillGradient(0, 0, w, h / 4, a << 24, 0);
        ctx.fillGradient(0, h - h / 3, w, h, 0, a << 24);
        for (int i = 0; i < 12; i++) {
            int band = w / 40;
            int al = (int) (a * (1 - i / 12f) * 0.6f) << 24;
            ctx.fill(i * band, 0, (i + 1) * band, h, al);
            ctx.fill(w - (i + 1) * band, 0, w - i * band, h, al);
        }
    }

    /** Полосы «кино» сверху и снизу. */
    public static void letterbox(DrawContext ctx, int w, int h, float k) {
        int bar = (int) (h * 0.1f * k);
        if (bar <= 0) return;
        ctx.fill(0, 0, w, bar, 0xFF000000);
        ctx.fill(0, h - bar, w, h, 0xFF000000);
    }

    /** Логотип «НОВОГРАД» нужной ширины; возвращает высоту. */
    public static int logo(DrawContext ctx, float x, float y, float width, float alpha) {
        float h = width * LOGO_SIZE[1] / LOGO_SIZE[0];
        tex(ctx, LOGO, x, y, width, h, LOGO_SIZE[0], LOGO_SIZE[1], alpha);
        return (int) h;
    }

    /** «ТЁМНЫЕ УЛИЦЫ» на косой тёмной плашке. */
    public static int subtitle(DrawContext ctx, float x, float y, float width, float alpha) {
        float h = width * SUBTITLE_SIZE[1] / SUBTITLE_SIZE[0];
        slant(ctx, (int) x - 6, (int) (y + h * 0.12f), (int) (width + 12), (int) (h * 0.8f), (int) (h * 0.3f), alpha(0xE0101018, alpha));
        tex(ctx, SUBTITLE, x, y, width, h, SUBTITLE_SIZE[0], SUBTITLE_SIZE[1], alpha);
        return (int) h;
    }

    // ------------------------------------------------------------------ пункты меню
    /**
     * Пункт меню в духе криминальной игры: слева направо, при наведении — косая розовая плашка выезжает,
     * текст сдвигается и белеет. hov — 0..1 (анимация наведения).
     */
    public static void menuItem(DrawContext ctx, TextRenderer tr, int x, int y, int w, int h, String label, String sub, float hov, float appear, boolean enabled) {
        menuItem(ctx, tr, x, y, w, h, label, sub, hov, appear, enabled, 1.5f);
    }

    public static void menuItem(DrawContext ctx, TextRenderer tr, int x, int y, int w, int h, String label, String sub, float hov, float appear, boolean enabled, float s) {
        int a = (int) (appear * 255);
        if (a < 6) return;
        int slide = (int) ((1 - appear) * -40);
        x += slide;
        if (hov > 0.01f) {
            int sw = (int) (w * Ui.easeOut(hov));
            slant(ctx, x - 4, y, sw, h, 6, alpha(0xF0FF2E88, appear));
            ctx.fill(x - 4, y + h, x - 4 + sw, y + h + 1, alpha(0xFF28E6FF, appear * hov));
        }
        ctx.fill(x - 10, y + 2, x - 8, y + h - 2, alpha(hov > 0.5f ? CYAN : 0x40FFFFFF, appear));
        int tx = x + 6 + (int) (6 * hov);
        int col = !enabled ? 0xFF6E6880 : hov > 0.5f ? WHITE : 0xFFE6E2F0;
        int ty = sub == null || sub.isEmpty() ? y + (int) ((h - 8 * s) / 2) : y + 2;
        big(ctx, tr, label.toUpperCase(), tx, ty, s, alpha(col, appear), 0);
        if (sub != null && !sub.isEmpty())
            small(ctx, tr, sub, tx + 1, y + 4 + (int) (9 * s), 0.8f, alpha(hov > 0.5f ? 0xFFFFE1EE : 0xFF9C96B0, appear), 0);
    }

    public static MinecraftClient mc() {
        return MinecraftClient.getInstance();
    }
}
