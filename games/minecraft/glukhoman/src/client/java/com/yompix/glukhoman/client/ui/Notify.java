package com.yompix.glukhoman.client.ui;

import com.yompix.glukhoman.client.menu.MenuClock;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

/** Уведомления (этап 5): плашка справа сверху, выезжает, держится 3.5 с, не больше трёх; повтор того же — не множится. */
public final class Notify {
    private Notify() {}

    record N(String text, double t0) {}

    static final List<N> LIST = new ArrayList<>();

    public static void show(String text) {
        double t = MenuClock.now();
        LIST.removeIf(n -> n.text.equals(text) || t - n.t0 > 4);
        LIST.add(new N(text, t));
        while (LIST.size() > 3) LIST.remove(0);
    }

    public static void render(DrawContext ctx, TextRenderer tr, int w, int h) {
        double t = MenuClock.now();
        LIST.removeIf(n -> t - n.t0 > 4);
        int y = 36;
        for (N n : LIST) {
            double u = t - n.t0;
            float a = (float) Math.min(1, Math.min(u / 0.2, (4 - u) / 0.4));
            int tw = Math.min(tr.getWidth(n.text), w / 2);
            int x = w - 20 - tw - (int) ((1 - Math.min(1, u / 0.2)) * -30);
            ctx.fill(x - 8, y - 4, w - 12, y + 12, Kit.alpha(0x0A0A0C, 0.8f * a));
            ctx.fill(x - 8, y - 4, x - 6, y + 12, Kit.alpha(Kit.RED, a));
            ctx.drawTextWithShadow(tr, tr.trimToWidth(n.text, w / 2), x, y, Kit.alpha(Kit.TEXT, a));
            y += 20;
        }
    }
}
