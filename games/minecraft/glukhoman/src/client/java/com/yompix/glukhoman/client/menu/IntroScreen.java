package com.yompix.glukhoman.client.menu;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yompix.glukhoman.Glukhoman;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Заставка, как «своя» у больших игр: темнота → щелчок кассеты и мотор → синий экран «▶ PLAY» и трекинг →
 * вспышки записи (перрон в ИК, табло, огни поезда в тумане, фигура на три кадра) → гудок → логотип на сбое плёнки →
 * «Порожняк» → «Не связано с Mojang». Пропуск — любая клавиша или клик. Полностью — один раз за запуск игры,
 * потом — двухсекундная версия.
 */
public class IntroScreen extends Screen {
    public static boolean playedThisSession;
    final Screen next;
    final boolean full;
    double t0 = -1;
    final Random rnd = Random.create();
    final List<Identifier[]> segs = new ArrayList<>();
    int fps = 12;
    final java.util.Set<String> fired = new java.util.HashSet<>();
    static final Identifier LOGO = Glukhoman.id("textures/gui/logo_label.png");

    public static final double FULL = 13.5, SHORT = 2.2;

    public IntroScreen(Screen next) {
        super(Text.literal("Глухомань"));
        this.next = next;
        this.full = !playedThisSession;
        playedThisSession = true;
        try (var in = IntroScreen.class.getResourceAsStream("/assets/glukhoman/textures/gui/intro/intro.json")) {
            JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            fps = o.get("fps").getAsInt();
            for (var e : o.getAsJsonArray("segments")) {
                JsonArray a = e.getAsJsonArray();
                String p = a.get(0).getAsString();
                int n = a.get(1).getAsInt();
                Identifier[] f = new Identifier[n];
                for (int i = 0; i < n; i++) f[i] = Glukhoman.id(String.format("textures/gui/intro/%s_%02d.png", p, i));
                segs.add(f);
            }
        } catch (Exception e) {
            Glukhoman.LOG.warn("Заставка: нет кадров", e);
        }
    }

    double time() {
        if (t0 < 0) t0 = MenuClock.now();
        return MenuClock.now() - t0;
    }

    boolean once(String k, double t, double at) {
        return t >= at && fired.add(k);
    }

    public double length() {
        return full ? FULL : SHORT;
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        double t = time();
        int w = width, h = height;
        ctx.fill(0, 0, w, h, 0xFF000000);
        if (!full) {
            renderShort(ctx, t, w, h);
            if (t > SHORT) done();
            return;
        }
        // --- звук
        if (once("ins", t, 0.15)) MenuSound.play("tape_click", 1f, 0.8f);
        if (once("mot", t, 0.55)) MenuSound.play("cam_motor", 0.6f, 0.9f);
        if (once("hiss", t, 1.0)) MenuSound.play("radio_static", 0.35f, 0.6f);
        if (once("relay", t, 3.8)) MenuSound.play("relay", 0.8f, 1f);
        if (once("bell", t, 5.0)) MenuSound.play("crossing_bell", 0.45f, 1f);
        if (once("hit", t, 6.6)) MenuSound.play("stinger_soft", 0.7f, 0.8f);
        if (once("whistle", t, 7.0)) MenuSound.play("whistle", 0.9f, 1f);
        if (once("title", t, 7.6)) MenuSound.play("title_hit", 0.8f, 0.9f);
        // --- картинка
        if (t < 1.0) {
            // темнота, кассета входит
        } else if (t < 2.4) {
            ctx.fill(0, 0, w, h, 0xFF1A2690);
            trackingNoise(ctx, w, h, t, 0.6f);
            if ((int) (t * 3) % 2 == 0 || t > 1.4) {
                ctx.getMatrices().push();
                ctx.getMatrices().scale(3, 3, 1);
                ctx.drawTextWithShadow(textRenderer, "▶ PLAY", 12, 10, 0xFFEFEFEF);
                ctx.getMatrices().pop();
            }
        } else if (t < 6.7) {
            double[] start = {2.4, 3.8, 5.0, 6.6};
            int si = t < 3.8 ? 0 : t < 5.0 ? 1 : t < 6.6 ? 2 : 3;
            if (si < segs.size()) {
                Identifier[] f = segs.get(si);
                int fi = si == 3 ? Math.min(f.length - 1, (int) ((t - start[si]) * 30)) : Math.min(f.length - 1, (int) ((t - start[si]) * fps));
                ctx.drawTexture(f[Math.max(0, fi)], 0, 0, 0, 0, w, h, w, h);
            }
            trackingNoise(ctx, w, h, t, si == 3 ? 1f : 0.35f);
            if (si < 3 && (int) (t * 2) % 2 == 0) ctx.drawTextWithShadow(textRenderer, "● REC", w - 50, 10, 0xFFE02424);
            ctx.drawTextWithShadow(textRenderer, "14.10.1996  03:1" + (int) (4 + (t - 2.4) / 2), w - 118, h - 18, 0xD0E0E0E0);
        } else if (t < 7.4) {
            trackingNoise(ctx, w, h, t, 1f);
        } else {
            double u = t - 7.4;
            float a = (float) Math.min(1, u / 0.4) * (float) Math.min(1, (FULL - t) / 1.0);
            logo(ctx, w, h, a, u < 1.2 ? 1 - u / 1.2 : (rnd.nextFloat() < 0.04 ? 0.6 : 0.05));
            if (t > 9.0) ctx.drawCenteredTextWithShadow(textRenderer, "П О Р О Ж Н Я К", w / 2, h / 2 + 46, alpha(0xC04040, a * (float) Math.min(1, (t - 9.0) / 0.6)));
            if (t > 10.0) ctx.drawCenteredTextWithShadow(textRenderer, "Не связано с Mojang", w / 2, h - 20, alpha(0x808080, a * (float) Math.min(1, (t - 10) / 0.6)));
        }
        if (t > FULL) done();
    }

    void renderShort(DrawContext ctx, double t, int w, int h) {
        if (once("s_click", t, 0.0)) MenuSound.play("tape_click", 0.8f, 0.9f);
        if (t < 0.6) {
            ctx.fill(0, 0, w, h, 0xFF1A2690);
            trackingNoise(ctx, w, h, t, 0.6f);
            ctx.getMatrices().push();
            ctx.getMatrices().scale(3, 3, 1);
            ctx.drawTextWithShadow(textRenderer, "▶ PLAY", 12, 10, 0xFFEFEFEF);
            ctx.getMatrices().pop();
        } else {
            float a = (float) Math.min(1, (t - 0.6) / 0.3) * (float) Math.min(1, (SHORT - t) / 0.5);
            logo(ctx, w, h, a, t < 1.0 ? 0.7 : 0.05);
        }
    }

    static int alpha(int rgb, float a) {
        return ((int) (Math.max(0, Math.min(1, a)) * 255) << 24) | rgb;
    }

    /** Логотип-наклейка кассеты на сбое: смещённые цветные копии, срыв строк. */
    void logo(DrawContext ctx, int w, int h, float a, double glitch) {
        int lw = Math.min(w - 40, 420), lh = lw / 4, x = (w - lw) / 2, y = h / 2 - lh / 2 - 10;
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        int off = (int) (glitch * 8 * (rnd.nextFloat() - 0.5f) * 2);
        if (glitch > 0.1) {
            ctx.setShaderColor(1f, 0.2f, 0.2f, a * 0.5f);
            ctx.drawTexture(LOGO, x + off + 3, y, 0, 0, lw, lh, lw, lh);
            ctx.setShaderColor(0.2f, 0.6f, 1f, a * 0.5f);
            ctx.drawTexture(LOGO, x - off - 3, y + 1, 0, 0, lw, lh, lw, lh);
        }
        ctx.setShaderColor(1f, 1f, 1f, a);
        ctx.drawTexture(LOGO, x + off / 2, y, 0, 0, lw, lh, lw, lh);
        ctx.setShaderColor(1f, 1f, 1f, 1f);
        if (glitch > 0.3) for (int i = 0; i < 3; i++) {
            int yy = y + rnd.nextInt(Math.max(1, lh));
            ctx.fill(x, yy, x + lw, yy + 2, alpha(0xD0D0D0, a * 0.4f));
        }
    }

    /** Полосы трекинга и «снег»: мягкие, шумные, больше внизу кадра. */
    void trackingNoise(DrawContext ctx, int w, int h, double t, float k) {
        int bandY = (int) (h * (0.62 + 0.3 * ((t * 0.37) % 1)));
        for (int i = 0; i < 40 * k; i++) {
            int yy = bandY + rnd.nextInt(18) - 9, xx = rnd.nextInt(w), len = 10 + rnd.nextInt(80);
            ctx.fill(xx, yy, Math.min(w, xx + len), yy + 1, alpha(0xE0E0F0, 0.25f + rnd.nextFloat() * 0.4f));
        }
        for (int i = 0; i < 60 * k; i++) {
            int yy = h - 1 - rnd.nextInt(Math.max(1, h / 30)), xx = rnd.nextInt(w);
            ctx.fill(xx, yy, Math.min(w, xx + 20 + rnd.nextInt(60)), yy + 1, alpha(0xC0C0C0, 0.3f));
        }
    }

    void done() {
        MinecraftClient.getInstance().setScreen(next);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        done();
        return true;
    }

    @Override
    public boolean mouseClicked(double x, double y, int b) {
        done();
        return true;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
    }
}
