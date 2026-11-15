package com.yompix.glukhoman.client.ui;

import com.yompix.glukhoman.client.menu.MenuClock;
import com.yompix.glukhoman.client.menu.MenuSound;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.Random;

/**
 * Единый язык интерфейса (этап 5): «плёночный» экран — тёмная подложка, редкие строки помех, уголки кадра, REC;
 * пункты — строки ПРОПИСНЫМИ, при наведении красный ► и тихий щелчок реле, при нажатии — вспышка и сдвиг,
 * неактивный — тусклый с пометкой, появление — по очереди (сдвиг + проявление, 0.25 с, шаг 0.05 с).
 * Цвета и размеры — здесь, все экраны мода берут их отсюда.
 */
public final class Kit {
    private Kit() {}

    public static final int TEXT = 0xF0EAD8, DIM = 0xB8B0A0, OFF = 0x6A6660, RED = 0xE02828, PLATE = 0xC0101012;

    /** Для записи видео-тура: подсветить/«нажать» пункт с этим номером на текущем экране (-1 — нет). */
    public static int forceHover = -1, forcePress = -1;

    public static int alpha(int rgb, float a) {
        return (MathHelper.clamp((int) (a * 255), 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    /** Подложка экрана поверх игры/меню: затемнение, помехи, уголки кадра, REC мигает. */
    public static void backdrop(DrawContext ctx, int w, int h, double t, boolean inGame) {
        ctx.fill(0, 0, w, h, inGame ? 0xB0050506 : 0xFF070709);
        Random r = new Random((long) (t * 12));
        for (int i = 0; i < 10; i++) {
            int y = r.nextInt(Math.max(1, h));
            ctx.fill(0, y, w, y + 1, alpha(0xD0D0E0, 0.05f + r.nextFloat() * 0.06f));
        }
        int m = 14, L = 22, col = 0x90E8E8E8;
        ctx.fill(m, m, m + L, m + 1, col); ctx.fill(m, m, m + 1, m + L, col);
        ctx.fill(w - m - L, m, w - m, m + 1, col); ctx.fill(w - m - 1, m, w - m, m + L, col);
        ctx.fill(m, h - m - 1, m + L, h - m, col); ctx.fill(m, h - m - L, m + 1, h - m, col);
        ctx.fill(w - m - L, h - m - 1, w - m, h - m, col); ctx.fill(w - m - 1, h - m - L, w - m, h - m, col);
        var tr = MinecraftClient.getInstance().textRenderer;
        if ((int) (t * 2) % 2 == 0) ctx.drawTextWithShadow(tr, "●", w - m - 40, m + 6, 0xFFE02424);
        ctx.drawTextWithShadow(tr, inGame ? "PAUSE" : "REC", w - m - 30, m + 6, 0xE0E8E8E8);
    }

    /** Заголовок экрана: крупно, с красной чертой. */
    public static void title(DrawContext ctx, String s, int cx, int y, float a) {
        var tr = MinecraftClient.getInstance().textRenderer;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(cx, y, 0);
        ctx.getMatrices().scale(2f, 2f, 1f);
        ctx.drawCenteredTextWithShadow(tr, s.toUpperCase(), 0, 0, alpha(TEXT, a));
        ctx.getMatrices().pop();
        int lw = tr.getWidth(s.toUpperCase()) + 10;
        ctx.fill(cx - lw, y + 22, cx + lw, y + 23, alpha(RED, a * 0.8f));
    }

    /** Пункт-строка. order — номер для появления по очереди; note — подпись у неактивного. */
    public static class Item extends ButtonWidget {
        final double born = MenuClock.now();
        final int order;
        public String note = "";
        double pressT = -10;
        boolean lastHover;
        public boolean center = true;

        public Item(int x, int y, int w, String label, int order, PressAction onPress) {
            super(x, y, w, 14, Text.literal(label), onPress, DEFAULT_NARRATION_SUPPLIER);
            this.order = order;
        }

        public Item off(String why) {
            active = false;
            note = why;
            return this;
        }

        @Override
        public void onPress() {
            pressT = MenuClock.now();
            MenuSound.play("relay", 0.5f, 0.9f);
            super.onPress();
        }

        public float appear() {
            double u = MathHelper.clamp((MenuClock.now() - born - order * 0.05) / 0.25, 0, 1);
            return (float) (u * u * (3 - 2 * u));
        }

        boolean forced(int which) {
            if (which < 0) return false;
            Screen s = MinecraftClient.getInstance().currentScreen;
            if (s == null) return false;
            int i = 0;
            for (var el : s.children()) {
                if (el instanceof Item it) {
                    if (it == this) return i == which;
                    i++;
                }
            }
            return false;
        }

        @Override
        protected void renderWidget(DrawContext ctx, int mx, int my, float delta) {
            var tr = MinecraftClient.getInstance().textRenderer;
            float a = appear();
            if (a <= 0.01f) return;
            if (forced(forcePress) && MenuClock.now() - pressT > 0.4) pressT = MenuClock.now();
            boolean hv = active && (isHovered() || isFocused() || forced(forceHover));
            if (hv && !lastHover) MenuSound.play("relay", 0.3f, 1.25f);
            lastHover = hv;
            double since = MenuClock.now() - pressT;
            boolean pressed = since < 0.18;
            String label = getMessage().getString().toUpperCase();
            int tw = tr.getWidth(label);
            int slide = (int) ((1 - a) * 10);
            int tx = (center ? getX() + getWidth() / 2 - tw / 2 : getX() + 14) + slide + (hv ? 2 : 0) + (pressed ? 1 : 0);
            int ty = getY() + 3 + (pressed ? 1 : 0);
            if (hv) ctx.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), alpha(0x000000, 0.35f * a));
            if (pressed) ctx.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), alpha(RED, 0.25f * (float) (1 - since / 0.18)));
            if (hv) ctx.drawTextWithShadow(tr, "►", tx - 11, ty, alpha(RED, a));
            int col = !active ? OFF : pressed ? 0xFFFFFF : hv ? TEXT : DIM;
            int room = getWidth() - (center ? 4 : 18);
            if (tw > room) {   // длинная подпись в узкой колонке — ужимаем, а не вылезаем за пункт
                float k = room / (float) tw;
                int x1 = center ? getX() + getWidth() / 2 - room / 2 : getX() + 14;
                ctx.getMatrices().push();
                ctx.getMatrices().translate(x1 + slide + (hv ? 2 : 0), ty + 4 * (1 - k), 0);
                ctx.getMatrices().scale(k, k, 1);
                ctx.drawTextWithShadow(tr, label, 0, 0, alpha(col, a));
                ctx.getMatrices().pop();
            } else ctx.drawTextWithShadow(tr, label, tx, ty, alpha(col, a));
            if (!active && !note.isEmpty())
                ctx.drawTextWithShadow(tr, "— " + note, tx + tw + 6, ty, alpha(0x807870, a));
        }
    }

    /** Ползунок в стиле кита. */
    public static abstract class Slider extends net.minecraft.client.gui.widget.SliderWidget {
        public Slider(int x, int y, int w, String label, double value) {
            super(x, y, w, 16, Text.literal(label), value);
        }

        @Override
        public void renderWidget(DrawContext ctx, int mx, int my, float delta) {
            var tr = MinecraftClient.getInstance().textRenderer;
            boolean hv = isHovered() || isFocused();
            ctx.fill(getX(), getY() + 7, getX() + getWidth(), getY() + 9, 0x60FFFFFF);
            int kx = getX() + (int) (value * (getWidth() - 6));
            ctx.fill(getX(), getY() + 7, kx + 3, getY() + 9, alpha(RED, hv ? 0.95f : 0.7f));
            ctx.fill(kx, getY() + 3, kx + 6, getY() + 13, hv ? 0xFFF0EAD8 : 0xFFB8B0A0);
            ctx.drawTextWithShadow(tr, getMessage(), getX(), getY() - 8, alpha(hv ? TEXT : DIM, 1));
        }
    }
}
