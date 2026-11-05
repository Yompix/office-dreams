package com.yompix.lifecraft.client.story;

import com.yompix.lifecraft.LifeCraft;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;

/**
 * Заставка студии при запуске игры: пиксели слетаются в эмблему Yompix Games, по буквам вспыхивает название,
 * звучит джингл и голос «Йомпикс Геймс представляет», затем — LIFECRAFT над силуэтом города.
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
    private static final long TOTAL = 8600;

    private final Screen next;
    private final long start = System.currentTimeMillis();
    private final float[][] from;
    private boolean voice, jingle;

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
        MinecraftClient.getInstance().getSoundManager().play(new PositionedSoundInstance(LifeCraft.id(id), SoundCategory.MASTER, 1f, 1f,
                Random.create(), false, 0, SoundInstance.AttenuationType.NONE, 0, 0, 0, true));
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        long t = System.currentTimeMillis() - start;
        if (!jingle) {
            jingle = true;
            play("yompix_jingle");
        }
        if (!voice && t > 1500) {
            voice = true;
            play("voice.yompix");
        }
        if (t > TOTAL) {
            close();
            return;
        }
        ctx.fill(0, 0, width, height, 0xFF05040A);
        // мягкое свечение позади эмблемы
        float glow = MathHelper.clamp((t - 600) / 900f, 0, 1) * (t < 4300 ? 1 : MathHelper.clamp((4800 - t) / 500f, 0, 1));
        for (int i = 6; i > 0; i--) {
            int rad = 40 + i * 12;
            int a = (int) (glow * 14 * (7 - i));
            ctx.fill(width / 2 - rad, height / 2 - 30 - rad, width / 2 + rad, height / 2 - 30 + rad, a << 24 | 0x6A3DFF);
        }
        if (t < 4800) studio(ctx, t);
        else title(ctx, t - 4800);
        float fadeIn = MathHelper.clamp(t / 400f, 0, 1), fadeOut = MathHelper.clamp((TOTAL - t) / 600f, 0, 1);
        float dark = 1 - Math.min(fadeIn, fadeOut);
        if (dark > 0) ctx.fill(0, 0, width, height, (int) (dark * 255) << 24);
        ctx.drawText(textRenderer, "Нажми, чтобы пропустить", width - textRenderer.getWidth("Нажми, чтобы пропустить") - 6, height - 12, 0x33FFFFFF, false);
    }

    private void studio(DrawContext ctx, long t) {
        int px = 7, cols = LOGO[0].length(), rows = LOGO.length;
        int ox = width / 2 - cols * px / 2, oy = height / 2 - 30 - rows * px / 2;
        int i = 0;
        for (int y = 0; y < rows; y++)
            for (int x = 0; x < cols; x++) {
                if (LOGO[y].charAt(x) != '1') continue;
                float k = MathHelper.clamp((t - 150 - i * 18) / 650f, 0, 1);
                k = 1 - (1 - k) * (1 - k) * (1 - k);
                float fx = ox + x * px + from[i][0] * (1 - k), fy = oy + y * px + from[i][1] * (1 - k);
                float hue = (x + y) / (float) (cols + rows);
                int c = lerpColor(0xFF8A5CFF, 0xFF2EE6D6, hue);
                if (t > 1300 && t < 1700) {
                    float flash = 1 - Math.abs(t - 1500) / 200f;
                    c = lerpColor(c, 0xFFFFFFFF, flash * 0.7f);
                }
                ctx.fill((int) fx, (int) fy, (int) fx + px - 1, (int) fy + px - 1, c);
                i++;
            }
        // блик пробегает по эмблеме
        if (t > 1400 && t < 2200) {
            int sx = ox - 20 + (int) ((t - 1400) / 800f * (cols * px + 40));
            ctx.fill(sx, oy, sx + 6, oy + rows * px, 0x55FFFFFF);
        }
        // название по буквам
        String name = "YOMPIX";
        float scale = 3f;
        int tw = (int) (textRenderer.getWidth(name) * scale);
        int letters = (int) MathHelper.clamp((t - 1400) / 90, 0, name.length());
        ctx.getMatrices().push();
        ctx.getMatrices().translate(width / 2f - tw / 2f, height / 2f + 26, 0);
        ctx.getMatrices().scale(scale, scale, 1);
        ctx.drawText(textRenderer, name.substring(0, letters), 0, 0, 0xFFFFFFFF, true);
        ctx.getMatrices().pop();
        float games = MathHelper.clamp((t - 2100) / 600f, 0, 1);
        if (games > 0) {
            String g = "G  A  M  E  S";
            ctx.drawText(textRenderer, g, width / 2 - textRenderer.getWidth(g) / 2, height / 2 + 56, (int) (games * 255) << 24 | 0x2EE6D6, false);
        }
        float pres = MathHelper.clamp((t - 3000) / 500f, 0, 1);
        if (pres > 0) {
            String p = "представляет";
            ctx.drawText(textRenderer, p, width / 2 - textRenderer.getWidth(p) / 2, height / 2 + 74, (int) (pres * 200) << 24 | 0xBBBBBB, false);
        }
    }

    private void title(DrawContext ctx, long t) {
        // силуэт города поднимается снизу, окна загораются
        Random r = Random.create(3);
        float rise = MathHelper.clamp(t / 1200f, 0, 1);
        int x = 0;
        while (x < width) {
            int w = 18 + r.nextInt(30), h = 30 + r.nextInt(90);
            int top = height - (int) (h * rise);
            ctx.fill(x, top, x + w - 2, height, 0xFF141225);
            for (int wy = top + 6; wy < height - 4; wy += 9)
                for (int wx = x + 4; wx < x + w - 6; wx += 7)
                    if (r.nextFloat() < 0.4f && t > 600 + r.nextInt(1500)) ctx.fill(wx, wy, wx + 3, wy + 4, 0xFFFFD166);
            x += w;
        }
        float k = MathHelper.clamp((t - 300) / 900f, 0, 1);
        float scale = 4.5f + (1 - k) * 3;
        String s = "LIFECRAFT";
        ctx.getMatrices().push();
        ctx.getMatrices().translate(width / 2f, height / 2f - 30, 0);
        ctx.getMatrices().scale(scale, scale, 1);
        int a = (int) (k * 255) << 24;
        ctx.drawText(textRenderer, s, -textRenderer.getWidth(s) / 2 + 1, 1, a | 0x6A3DFF, false);
        ctx.drawText(textRenderer, s, -textRenderer.getWidth(s) / 2, 0, a | 0xFFFFFF, false);
        ctx.getMatrices().pop();
        float sub = MathHelper.clamp((t - 1300) / 600f, 0, 1);
        String st = "жизнь, которую строишь ты";
        ctx.drawText(textRenderer, st, width / 2 - textRenderer.getWidth(st) / 2, height / 2 + 10, (int) (sub * 255) << 24 | 0xFFC107, true);
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
