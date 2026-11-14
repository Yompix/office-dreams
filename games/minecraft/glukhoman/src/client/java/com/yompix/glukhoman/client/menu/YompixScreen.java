package com.yompix.glukhoman.client.menu;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.Random;

/**
 * Заставка студии Yompix Games (этап 6) — перед заставкой «Порожняка», один раз за запуск игры.
 * Эмблема — та же, что в Новограде (пиксельная «Y» в рамке-геймпаде, {@code novograd/.../YompixSplash}), но по-нашему:
 * пиксели собираются из помех плёнки, с дрожью строк; название печатается по букве, звук — щелчок реле, гул и «тиньк»
 * плёнки (tools/gen_v2.py → yompix.ogg). 4.6 с; любая клавиша или клик — сразу к заставке игры; если что-то не так
 * (окно свёрнуто, время «прыгнуло») — не дольше 8 с по настенным часам, дальше идём сами. Без текстур — рисуется
 * прямоугольниками, поэтому не может «не загрузиться».
 */
public class YompixScreen extends Screen {
    public static boolean shown;
    static final String[] LOGO = {
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
    public static final double LEN = 4.6;

    final Screen next;
    double t0 = -1;
    final long wall0 = System.nanoTime();
    boolean left, sound;
    final float[][] from;

    public YompixScreen(Screen next) {
        super(Text.literal("Yompix Games"));
        this.next = next;
        shown = true;
        Random r = new Random(1996);
        int n = 0;
        for (String row : LOGO) for (char ch : row.toCharArray()) if (ch == '1') n++;
        from = new float[n][3];
        for (float[] f : from) {
            f[0] = (r.nextFloat() - 0.5f) * 2.2f;   // откуда летит (доли ширины)
            f[1] = (r.nextFloat() - 0.5f) * 1.6f;
            f[2] = r.nextFloat() * 0.6f;            // задержка
        }
    }

    double time() {
        if (t0 < 0) t0 = MenuClock.now();
        return MenuClock.now() - t0;
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        double t = time();
        if (!sound && t >= 0.05) {
            sound = true;
            MenuSound.play("yompix", 0.9f, 1f);
        }
        int w = width, h = height;
        ctx.fill(0, 0, w, h, 0xFF050506);
        double fade = MathHelper.clamp(Math.min(t / 0.35, (LEN - t) / 0.6), 0, 1);
        // помехи плёнки: редкие светлые строки, сильнее в начале
        Random r = new Random((long) (t * 30));
        int lines = (int) (6 + 30 * Math.max(0, 1 - t / 1.6));
        for (int i = 0; i < lines; i++) {
            int y = r.nextInt(h);
            int a = (int) ((10 + r.nextInt(30)) * fade);
            ctx.fill(0, y, w, y + 1, (a << 24) | 0xC8C8D0);
        }
        // эмблема: каждый пиксель летит из помех на своё место (сглаженно), строки дрожат по горизонтали
        int px = Math.max(3, h / 34);
        int lw = LOGO[0].length() * px, lh = LOGO.length * px;
        int x0 = w / 2 - lw / 2, y0 = h / 2 - lh / 2 - h / 12;
        int i = 0;
        double glow = Math.max(0, Math.sin(MathHelper.clamp((t - 1.5) / 0.8, 0, 1) * Math.PI));
        for (int ry = 0; ry < LOGO.length; ry++) {
            double jit = (r.nextFloat() - 0.5) * px * 2.5 * Math.max(0, 1 - t / 1.4) + (t > 3.1 && t < 3.3 ? (ry % 3 - 1) * px * 0.8 : 0);
            for (int rx = 0; rx < LOGO[ry].length(); rx++) {
                if (LOGO[ry].charAt(rx) != '1') continue;
                float[] f = from[i++];
                double u = MathHelper.clamp((t - 0.15 - f[2]) / 0.9, 0, 1);
                u = u * u * u * (u * (u * 6 - 15) + 10);
                double x = MathHelper.lerp(u, w / 2.0 + f[0] * w / 2, x0 + rx * px) + jit;
                double y = MathHelper.lerp(u, h / 2.0 + f[1] * h / 2, y0 + ry * px);
                int a = (int) (255 * fade * Math.min(1, (t - f[2]) / 0.3 + 0.05));
                if (a <= 0) continue;
                int col = u >= 1 ? 0xE6E1D2 : 0x9AA0A8;
                ctx.fill((int) x, (int) y, (int) x + px - 1, (int) y + px - 1, (MathHelper.clamp(a, 0, 255) << 24) | col);
                if (glow > 0) ctx.fill((int) x - 1, (int) y - 1, (int) x + px, (int) y + px, ((int) (40 * glow * fade) << 24) | 0xD04040);
            }
        }
        // название по букве + «GAMES» мельче
        String word = "YOMPIX";
        int shown = (int) MathHelper.clamp((t - 1.6) / 0.11, 0, word.length());
        if (shown > 0) {
            float s = Math.max(2f, h / 120f);
            ctx.getMatrices().push();
            ctx.getMatrices().translate(w / 2f, y0 + lh + px * 1.6f, 0);
            ctx.getMatrices().scale(s, s, 1);
            String part = word.substring(0, shown);
            int a = (int) (255 * fade);
            ctx.drawCenteredTextWithShadow(textRenderer, part + (shown < word.length() && (int) (t * 8) % 2 == 0 ? "_" : ""), 0, 0, (Math.max(4, a) << 24) | 0xE6E1D2);
            ctx.getMatrices().pop();
            if (t > 2.4) {
                float s2 = s * 0.55f;
                int a2 = (int) (255 * fade * MathHelper.clamp((t - 2.4) / 0.4, 0, 1));
                ctx.getMatrices().push();
                ctx.getMatrices().translate(w / 2f, y0 + lh + px * 1.6f + 12 * s, 0);
                ctx.getMatrices().scale(s2, s2, 1);
                ctx.drawCenteredTextWithShadow(textRenderer, "G  A  M  E  S", 0, 0, (Math.max(4, a2) << 24) | 0xB04848);
                ctx.getMatrices().pop();
            }
        }
        // «срыв строк» на 3.1 с — и затемнение
        if (t > 3.1 && t < 3.3) {
            int y = (int) (h * (t - 3.1) / 0.2);
            ctx.fill(0, y, w, y + px, 0x60FFFFFF);
        }
        double wall = (System.nanoTime() - wall0) / 1e9;
        if (t >= LEN || (MenuClock.recFrame < 0 && wall > LEN + 3.5)) leave();
    }

    void leave() {
        if (left) return;
        left = true;
        MinecraftClient.getInstance().setScreen(next);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        leave();
        return true;
    }

    @Override
    public boolean mouseClicked(double x, double y, int b) {
        leave();
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
