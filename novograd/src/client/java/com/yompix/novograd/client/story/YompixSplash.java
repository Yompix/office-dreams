package com.yompix.novograd.client.story;

import com.yompix.novograd.Novograd;
import com.yompix.novograd.client.ui.Ui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.Identifier;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.GlStateManager;

/**
 * Заставка студии при запуске игры: пиксели слетаются в эмблему Yompix Games, по буквам вспыхивает название,
 * звучит джингл и голос «Йомпикс Геймс представляет», затем — НОВОГРАД над силуэтом города.
 */
public class YompixSplash extends Screen {
    private static boolean shown;
    // эмблема: пиксельная «Y» внутри квадрата-геймпада (1 — пиксель)
    private static final String[] LOGO = {
            "0111111111110",
            "1100000000011",
            "1010000000101",
            "1001000001001",
            "1000100010001",
            "1000011100001",
            "1000001000001",
            "1000001000001",
            "1000001000001",
            "1100001000011",
            "0111111111110"};
    private static final long TOTAL = 11400 + 3920;

    private final Screen next;
    private long start = System.currentTimeMillis();
    private final float[][] from;
    private boolean voice, jingle;
    private long lastShot = -1;

    public YompixSplash(Screen next) {
        super(Text.literal("Yompix Games"));
        this.next = next;
        Random r = Random.create(7);
        int n = 0;
        for (String row : LOGO) for (char c : row.toCharArray()) if (c == '1') n++;
        from = new float[n][2];
        for (float[] f : from) {
            double a = r.nextDouble() * Math.PI * 2;
            f[0] = (float) Math.cos(a) * (300 + r.nextInt(200));
            f[1] = (float) Math.sin(a) * (300 + r.nextInt(200));
        }
    }

    public static boolean shown() {
        return shown;
    }

    public static void markShown() {
        shown = true;
    }

    private static void play(String id) {
        MinecraftClient.getInstance().getSoundManager().play(new PositionedSoundInstance(Novograd.id(id), SoundCategory.MASTER, 1f, 1f,
                Random.create(), false, 0, SoundInstance.AttenuationType.NONE, 0, 0, 0, true));
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // ждём, пока уйдёт экран загрузки игры: заставка начинается с чистого чёрного кадра
        if (MinecraftClient.getInstance().getOverlay() != null) {
            start = System.currentTimeMillis();
            ctx.fill(0, 0, width, height, 0xFF05040A);
            return;
        }
        long t = System.currentTimeMillis() - start;
        if (!jingle) {
            jingle = true;
            play("yompix_intro");
        }
        if (!voice && t > 2700) {
            voice = true;
            play("voice.yompix");
        }
        if (t > TOTAL) {
            close();
            return;
        }
        // проверка заставки: кадры в run/screenshots (-Dnovograd.introshots=true)
        if (Boolean.getBoolean("novograd.introshots") && t / 350 != lastShot) {
            lastShot = t / 350;
            long n = lastShot;
            MinecraftClient mc = MinecraftClient.getInstance();
            mc.send(() -> net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(mc.runDirectory, String.format("intro_%02d.png", n), mc.getFramebuffer(), m -> {}));
            if (t > TOTAL - 400) mc.send(mc::scheduleStop);
        }
        ctx.fill(0, 0, width, height, 0xFF05040A);
        // виньетка: края темнее
        for (int i = 0; i < 6; i++) {
            int a = 18 + i * 6, inset = i * Math.min(width, height) / 30;
            ctx.fill(0, 0, width, inset, a << 24);
            ctx.fill(0, height - inset, width, height, a << 24);
        }
        if (t < 4800) studio(ctx, t);
        else title(ctx, t - 4800);
        float fadeIn = MathHelper.clamp(t / 400f, 0, 1), fadeOut = MathHelper.clamp((TOTAL - t) / 600f, 0, 1);
        float dark = 1 - Math.min(fadeIn, fadeOut);
        if (dark > 0) ctx.fill(0, 0, width, height, (int) (dark * 255) << 24);
        ctx.drawText(textRenderer, "Нажми, чтобы пропустить", width - textRenderer.getWidth("Нажми, чтобы пропустить") - 6, height - 12, 0x33FFFFFF, false);
    }

    // ------------------------------------------------------------------ новая анимация студии
    private static final Identifier LOGO_T = Novograd.id("textures/gui/yompix/logo.png"), GLOW_T = Novograd.id("textures/gui/yompix/logo_glow.png"),
            FLARE_T = Novograd.id("textures/gui/yompix/flare.png"), RING_T = Novograd.id("textures/gui/yompix/ring.png"),
            STREAK_T = Novograd.id("textures/gui/yompix/streak.png"), WORD_T = Novograd.id("textures/gui/yompix/word_yompix.png"),
            GAMES_T = Novograd.id("textures/gui/yompix/word_games.png");
    private static final int[] LETTERS = {20, 109, 205, 318, 400, 437, 527};
    private static final long HIT = 1450;

    /** Картинка по центру (cx, cy) нужного размера с цветом/прозрачностью; additive — светится. */
    private void tex(DrawContext ctx, Identifier id, int tw, int th, float cx, float cy, float w, float h, float r, float g, float b, float a, boolean additive) {
        if (a <= 0.003f) return;
        RenderSystem.enableBlend();
        if (additive) RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
        else RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(r, g, b, Math.min(1, a));
        var m = ctx.getMatrices();
        m.push();
        m.translate(cx, cy, 0);
        m.scale(w / tw, h / th, 1);
        ctx.drawTexture(id, -tw / 2, -th / 2, 0, 0, tw, th, tw, th);
        m.pop();
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.defaultBlendFunc();
    }

    private static float clamp01(float v) {
        return MathHelper.clamp(v, 0, 1);
    }

    private static float easeOut(float k) {
        k = clamp01(k);
        return 1 - (1 - k) * (1 - k) * (1 - k);
    }

    /** Упругое появление: перелетает и пружинит. */
    private static float elastic(float k) {
        k = clamp01(k);
        return k >= 1 ? 1 : (float) (1 - Math.pow(2, -10 * k) * Math.cos(k * 18));
    }

    private void studio(DrawContext ctx, long t) {
        float cx = width / 2f, cy = height / 2f - 8;
        float L = Math.min(width, height) * 0.44f; // размер эмблемы
        // тряска камеры при ударе
        float shake = t > HIT ? (float) Math.exp(-(t - HIT) / 140.0) * 7 : 0;
        Random sr = Random.create(t / 30);
        cx += (sr.nextFloat() - 0.5f) * shake;
        cy += (sr.nextFloat() - 0.5f) * shake;
        // уход: всё приближается и тает
        float out = clamp01((t - 4150) / 650f);
        float zoom = 1 + out * out * 0.6f, fade = 1 - out;

        // 1) искры летят по спирали к центру
        Random r = Random.create(11);
        for (int i = 0; i < 70; i++) {
            float delay = r.nextFloat() * 700, speed = 0.8f + r.nextFloat() * 0.6f;
            float ang0 = r.nextFloat() * 6.283f, dist0 = Math.max(width, height) * (0.6f + r.nextFloat() * 0.4f);
            float k = clamp01((t - delay) / ((HIT - delay) * speed));
            if (t > HIT + 50 || k <= 0) continue;
            float e = k * k;
            float ang = ang0 + e * 2.4f, dist = dist0 * (1 - e);
            float x = cx + (float) Math.cos(ang) * dist, y = cy + (float) Math.sin(ang) * dist * 0.7f;
            float hue = i % 3 == 0 ? 1 : 0;
            float sz = 6 + r.nextFloat() * 10;
            tex(ctx, FLARE_T, 256, 256, x, y, sz, sz, hue > 0 ? 1f : 0.55f, hue > 0 ? 0.4f : 0.9f, 1f, 0.9f * k, true);
        }
        // 2) световой штрих нарастает к удару
        float pre = clamp01((t - 700) / (HIT - 700f));
        if (t < HIT + 600) {
            float post = t > HIT ? clamp01(1 - (t - HIT) / 600f) : 1;
            tex(ctx, STREAK_T, 512, 32, cx, cy, width * (0.2f + pre * 1.3f), 10 + pre * 30, 0.7f, 0.6f, 1f, pre * post, true);
        }
        if (t < HIT) return;
        float dt = t - HIT;

        // 3) вспышка и ударная волна
        float ringK = clamp01(dt / 750f);
        tex(ctx, RING_T, 256, 256, cx, cy, L * (0.4f + ringK * 3.2f), L * (0.4f + ringK * 3.2f), 0.6f, 0.9f, 1f, (1 - ringK) * 0.9f, true);
        float ring2 = clamp01((dt - 120) / 900f);
        tex(ctx, RING_T, 256, 256, cx, cy, L * (0.3f + ring2 * 2.2f), L * (0.3f + ring2 * 2.2f), 1f, 0.5f, 0.9f, (1 - ring2) * 0.6f, true);

        // 4) эмблема: упругое появление, переворот, потом уезжает влево к надписи
        float s = elastic(dt / 900f);
        float flip = (float) Math.abs(Math.cos(clamp01(1 - dt / 380f) * Math.PI / 2));
        float slide = easeOut((dt - 900) / 700f);
        float lx = cx - slide * L * 0.76f;
        float size = L * s * zoom * (1 - slide * 0.25f);
        float pulse = 0.75f + 0.25f * (float) Math.sin(dt / 260.0);
        tex(ctx, GLOW_T, 512, 512, lx, cy, size * 1.25f * flip, size * 1.25f, 1, 1, 1, pulse * fade, true);
        boolean glitch = dt < 260 || (t > 3950 && t < 4250);
        if (glitch) {
            // глитч: эмблема режется на полосы, полосы скачут в стороны
            Random g = Random.create(t / 45);
            int strips = 9;
            for (int i = 0; i < strips; i++) {
                float off = (g.nextFloat() - 0.5f) * size * 0.18f;
                ctx.enableScissor(0, (int) (cy - size / 2 + i * size / strips), width, (int) (cy - size / 2 + (i + 1) * size / strips) + 1);
                tex(ctx, LOGO_T, 512, 512, lx + off, cy, size * flip, size, 1, 1, 1, fade, false);
                ctx.disableScissor();
            }
        } else tex(ctx, LOGO_T, 512, 512, lx, cy, size * flip, size, 1, 1, 1, fade, false);
        // хроматическая аберрация после удара: красный и синий «двойники» расходятся и сходятся
        float ab = (float) Math.exp(-dt / 220.0) * 14 + (t > 3950 && t < 4250 ? 6 : 0);
        if (ab > 0.4f) {
            tex(ctx, LOGO_T, 512, 512, lx + ab, cy, size * flip, size, 1, 0.1f, 0.2f, 0.55f * fade, true);
            tex(ctx, LOGO_T, 512, 512, lx - ab, cy, size * flip, size, 0.1f, 0.4f, 1, 0.55f * fade, true);
        }
        // блик скользит по эмблеме
        float shine = clamp01((dt - 550) / 650f);
        if (shine > 0 && shine < 1) {
            ctx.enableScissor((int) (lx - size / 2), (int) (cy - size / 2), (int) (lx + size / 2), (int) (cy + size / 2));
            var m = ctx.getMatrices();
            m.push();
            m.translate(lx - size + shine * size * 2, cy, 0);
            m.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(70));
            tex(ctx, STREAK_T, 512, 32, 0, 0, size * 1.6f, size * 0.22f, 1, 1, 1, 0.8f, true);
            m.pop();
            ctx.disableScissor();
        }
        // искры кружат вокруг эмблемы
        Random o = Random.create(5);
        for (int i = 0; i < 26; i++) {
            float rad = size * (0.62f + o.nextFloat() * 0.35f), sp = 0.4f + o.nextFloat() * 0.8f;
            float ang = o.nextFloat() * 6.283f + dt / 1000f * sp;
            float sz = 3 + o.nextFloat() * 6;
            float a = clamp01(dt / 400f) * (0.5f + 0.5f * (float) Math.sin(dt / 180.0 + i)) * fade;
            tex(ctx, FLARE_T, 256, 256, lx + (float) Math.cos(ang) * rad, cy + (float) Math.sin(ang) * rad * 0.9f, sz, sz, 0.6f, 0.9f, 1f, a, true);
        }

        // 5) YOMPIX — буквы выпрыгивают по очереди, GAMES раздвигается
        float wordH = L * 0.42f * zoom, wordW = wordH * 541 / 162f;
        float wx = cx + L * 0.435f * zoom, wy = cy - L * 0.07f;
        for (int i = 0; i < 6; i++) {
            float k = easeOut((dt - 1050 - i * 75) / 420f);
            if (k <= 0) continue;
            int u0 = LETTERS[i], u1 = LETTERS[i + 1];
            float lw = (u1 - u0) * wordW / 541f;
            float x = wx - wordW / 2 + (u0 + (u1 - u0) / 2f) * wordW / 541f;
            float y = wy + (1 - k) * wordH * 0.6f;
            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(1, 1, 1, k * fade);
            var m = ctx.getMatrices();
            m.push();
            m.translate(x, y, 0);
            float sc = wordH / 162f;
            m.scale(sc, sc, 1);
            ctx.drawTexture(WORD_T, -(u1 - u0) / 2, -81, u0, 0, u1 - u0, 162, 541, 162);
            m.pop();
            RenderSystem.setShaderColor(1, 1, 1, 1);
            if (k < 1) tex(ctx, FLARE_T, 256, 256, x, y, lw * 1.4f * (1 - k), lw * 1.4f * (1 - k), 0.7f, 0.8f, 1f, 1 - k, true);
        }
        float gk = easeOut((dt - 1650) / 700f);
        if (gk > 0) {
            float gh = wordH * 0.36f, gw = gh * 332 / 64f * (1.5f - 0.5f * gk);
            tex(ctx, GAMES_T, 332, 64, wx, wy + wordH * 0.62f, gw, gh, 1, 1, 1, gk * fade, false);
            float line = easeOut((dt - 1500) / 600f);
            ctx.fill((int) (wx - wordW / 2 * line), (int) (wy + wordH * 0.43f), (int) (wx + wordW / 2 * line), (int) (wy + wordH * 0.43f) + 1,
                    (int) (200 * fade) << 24 | 0x2EE6D6);
        }
        float pres = clamp01((dt - 2100) / 500f) * fade;
        if (pres > 0) {
            String p = "п р е д с т а в л я е т";
            ctx.drawText(textRenderer, p, (int) (cx - textRenderer.getWidth(p) / 2f), (int) (cy + L * 0.62f), (int) (pres * 200) << 24 | 0xBBBBBB, false);
        }
        // белая вспышка в момент удара
        float flash = (float) Math.exp(-dt / 160.0);
        if (flash > 0.02f) ctx.fill(0, 0, width, height, (int) (flash * 230) << 24 | 0xFFFFFF);
    }

    /**
     * Вторая часть: «загрузочный арт» — комикс-панели с ночным городом врезаются одна за другой, затем
     * закат над Новоградом и удар логотипа «НОВОГРАД» с подзаголовком «ТЁМНЫЕ УЛИЦЫ». Плёночное зерно, полосы кино.
     */
    /** Монтаж настоящих кадров острова (после комикс-панелей, до логотипа): наезд камеры, жёсткие склейки, подписи районов. */
    private static final long MONT_AT = 1750, MONT_LEN = 3920, MONT_SHOT = 560;
    private static final String[][] MONT = {{"bridge", "МОСТ ЧЕРЕЗ ПРОЛИВ"}, {"night", "ДЕЛОВОЙ ЦЕНТР"}, {"chase", "СЕВЕРНАЯ ТРАССА"},
            {"port", "ПОРТ НОВОГРАДА"}, {"strip", "РИВЬЕРА"}, {"casino", "СТРИП"}, {"desert", "ПУСТОШЬ"}};

    private void montage(DrawContext ctx, long t) {
        int i = (int) Math.min(MONT.length - 1, t / MONT_SHOT);
        float k = (t - i * MONT_SHOT) / (float) MONT_SHOT;
        Identifier id = Novograd.id("textures/gui/keyart/" + MONT[i][0] + ".png");
        float zoom = 1.16f - 0.12f * k;                       // медленный наезд
        float pan = i % 2 == 0 ? 0.35f + 0.3f * k : 0.65f - 0.3f * k; // и проезд в сторону
        ctx.fill(0, 0, width, height, 0xFF000000);
        Ui.cover(ctx, id, 0, 0, width, height, 800, 450, zoom, pan, 1);
        // подпись района: розовая плашка слева внизу
        float in = Ui.easeOut(Math.min(1, k * 4));
        int bx = (int) (24 - (1 - in) * 120), by = (int) (height * 0.78f);
        String cap = MONT[i][1];
        int cw = textRenderer.getWidth(cap) * 2 + 18;
        ctx.fill(bx, by, bx + cw, by + 24, 0xE8FF2E88);
        ctx.getMatrices().push();
        ctx.getMatrices().translate(bx + 9, by + 5, 0);
        ctx.getMatrices().scale(2f, 2f, 1);
        ctx.drawText(textRenderer, cap, 0, 0, 0xFFFFFFFF, true);
        ctx.getMatrices().pop();
        // белая вспышка на склейке
        float since = (t - i * MONT_SHOT);
        if (since < 90) ctx.fill(0, 0, width, height, (int) ((1 - since / 90f) * 170) << 24 | 0xFFFFFF);
        Ui.vignette(ctx, width, height, 0.8f);
        Ui.letterbox(ctx, width, height, 1);
        Ui.grain(ctx, width, height, 0.35f);
    }

    private void title(DrawContext ctx, long t) {
        if (t >= MONT_AT && t < MONT_AT + MONT_LEN) {
            montage(ctx, t - MONT_AT);
            return;
        }
        boolean afterMontage = t >= MONT_AT + MONT_LEN;
        if (afterMontage) t -= MONT_LEN;
        float w = width, h = height;
        // 1) комикс-панели: три кадра с ударом и вспышкой
        float[][] P = {{0.28f, 0.50f, 0.48f, 0.70f, -3f, -1}, {0.73f, 0.31f, 0.44f, 0.36f, 2.5f, 1}, {0.74f, 0.70f, 0.40f, 0.34f, -1.5f, 1}};
        long[] at = {0, 380, 760};
        float shake = 0;
        float panelsOut = Ui.easeOut((t - 1750) / 450f);
        if (panelsOut < 1 && !afterMontage) {
            // за панелями — тёмный растр-фон
            ctx.fill(0, 0, width, height, 0xFF0A0612);
            for (int i = 0; i < 3; i++) {
                float k = (t - at[i]) / 240f;
                if (k <= 0) continue;
                float e = Ui.easeOut(k);
                float sc = 1.35f - 0.35f * e;
                float side = P[i][5];
                float px = w * P[i][0] + (1 - e) * side * w * 0.5f + panelsOut * side * w * 0.8f;
                float py = h * P[i][1] + MathHelper.sin(t / 900f + i) * 2;
                float rot = P[i][4] + (1 - e) * side * 8;
                Ui.comic(ctx, Ui.panel(i == 0 ? 0 : i == 1 ? 1 : 2), px, py, w * P[i][2], h * P[i][3], rot, sc, Math.min(1, k * 2) * (1 - panelsOut),
                        0.2f + 0.6f * clamp01(t / 2400f));
                float since = t - at[i] - 200;
                if (since > 0 && since < 300) shake = Math.max(shake, (float) Math.exp(-since / 90.0) * 5);
            }
            // вспышки удара
            for (long a0 : at) {
                float since = t - a0 - 200;
                if (since > 0 && since < 260) ctx.fill(0, 0, width, height, (int) (Math.exp(-since / 70.0) * 150) << 24 | 0xFFFFFF);
            }
        }
        // 2) закат над городом: медленный наезд
        float sky = Ui.easeOut((t - 1650) / 700f);
        if (sky > 0) {
            float zoom = 1.18f - 0.08f * clamp01((t - 1650) / 4500f);
            Ui.cover(ctx, Ui.SKYLINE, 0, 0, width, height, Ui.SKYLINE_SIZE[0], Ui.SKYLINE_SIZE[1], zoom, 0.55f, sky);
            ctx.fillGradient(0, 0, width, height, (int) (sky * 40) << 24, (int) (sky * 120) << 24);
        }
        // 3) удар логотипа
        long hit = 2150;
        float lk = (t - hit) / 260f;
        if (lk > 0) {
            float e = Ui.easeOut(lk);
            float lw = Math.min(w * 0.7f, 420);
            float scale = 2.6f - 1.6f * e + (lk > 1 ? 0.03f * clamp01((t - hit - 260) / 4000f) : 0);
            float sw = lw * scale, sh = sw * Ui.LOGO_SIZE[1] / Ui.LOGO_SIZE[0];
            float since = t - hit - 240;
            if (since > 0) shake = Math.max(shake, (float) Math.exp(-since / 120.0) * 8);
            Random sr = Random.create(t / 30);
            float ox = (sr.nextFloat() - 0.5f) * shake, oy = (sr.nextFloat() - 0.5f) * shake;
            float lx = w / 2 - sw / 2 + ox, ly = h * 0.42f - sh / 2 + oy;
            // красный и бирюзовый «двойники» расходятся после удара
            float ab = since > 0 ? (float) Math.exp(-since / 200.0) * 10 : 0;
            if (ab > 0.4f) {
                Ui.tint(ctx, Ui.LOGO, lx + ab, ly, sw, sh, Ui.LOGO_SIZE[0], Ui.LOGO_SIZE[1], 1f, 0.15f, 0.45f, 0.6f);
                Ui.tint(ctx, Ui.LOGO, lx - ab, ly, sw, sh, Ui.LOGO_SIZE[0], Ui.LOGO_SIZE[1], 0.1f, 0.9f, 1f, 0.6f);
            }
            Ui.tex(ctx, Ui.LOGO, lx, ly, sw, sh, Ui.LOGO_SIZE[0], Ui.LOGO_SIZE[1], Math.min(1, lk * 3));
            if (since > 0 && since < 300) ctx.fill(0, 0, width, height, (int) (Math.exp(-since / 80.0) * 200) << 24 | 0xFFFFFF);
            // подзаголовок выезжает справа
            float sk = Ui.easeOut((t - hit - 500) / 450f);
            if (sk > 0) {
                float subW = lw * 0.58f;
                Ui.subtitle(ctx, w / 2 - subW / 2 + (1 - sk) * w * 0.6f + ox, ly + sh * 0.92f, subW, sk);
            }
            // мелкая подпись студии внизу
            float ck = clamp01((t - hit - 1200) / 600f);
            if (ck > 0) {
                String c = "YOMPIX GAMES";
                int cw = c.length() * 8;
                Ui.spaced(ctx, textRenderer, c, (int) (w / 2 - cw / 2f), (int) (h * 0.9f) - 14, (int) (ck * 200) << 24 | 0xFFFFFF, 2);
            }
        }
        // кино: полосы, зерно, виньетка
        Ui.vignette(ctx, width, height, 0.7f);
        Ui.letterbox(ctx, width, height, 1);
        Ui.grain(ctx, width, height, 0.3f);
    }

    private static int lerpColor(int a, int b, float k) {
        int r = (int) MathHelper.lerp(k, (a >> 16) & 255, (b >> 16) & 255), g = (int) MathHelper.lerp(k, (a >> 8) & 255, (b >> 8) & 255),
                bl = (int) MathHelper.lerp(k, a & 255, b & 255);
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        close();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        close();
        return true;
    }

    @Override
    public void close() {
        shown = true;
        MinecraftClient.getInstance().getSoundManager().stopAll();
        MinecraftClient.getInstance().setScreen(next);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
