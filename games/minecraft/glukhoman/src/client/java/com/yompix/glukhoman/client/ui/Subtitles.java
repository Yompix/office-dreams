package com.yompix.glukhoman.client.ui;

import com.yompix.glukhoman.client.GConfig;
import com.yompix.glukhoman.client.menu.MenuClock;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Субтитры (этап 4). Очередь без наложений: следующая реплика того же говорящего ждёт конца предыдущей, разные
 * говорящие — стопкой (не больше двух на экране, остальные ждут). Время показа — от длины текста (и не меньше
 * длины голоса). Имя говорящего — своим цветом и жирным, текст — светлым; перенос по ширине экрана; размер,
 * положение, фон, стиль и контраст — из настроек. Работает при любом разрешении и масштабе интерфейса: всё
 * считается от ширины/высоты экрана в «интерфейсных» пикселях и масштаба субтитров, строка не шире 80 % экрана.
 */
public final class Subtitles {
    private Subtitles() {}

    public static final class Line {
        public final String key, who, text;
        public double start, dur;

        Line(String key, String who, String text, double start, double dur) {
            this.key = key;
            this.who = who;
            this.text = text;
            this.start = start;
            this.dur = dur;
        }

        public double end() {
            return start + dur;
        }
    }

    static final List<Line> LINES = new ArrayList<>();
    public static final int MAX_VISIBLE = 2;

    static final Map<String, Integer> COLORS = Map.of("zakhar", 0xFFD7B072, "zakhar_r", 0xFFD7B072, "vika", 0xFF9CC8EC, "gena", 0xFF9FD48F,
            "ls", 0xFFE0A8A8, "petrovich", 0xFFE6A46A, "zina", 0xFFD8A6D4, "polya", 0xFFC8C8C8, "me", 0xFFA8DCC4, "lyokha", 0xFFA8DCC4);

    public static double now() {
        return MenuClock.now();
    }

    /** Сколько держать строку на экране, с: от числа символов, но не меньше 1.6 с и не меньше голоса. */
    public static double readTime(String text, double voice) {
        return Math.max(Math.max(1.6, 0.6 + 0.062 * text.length()), voice);
    }

    /** Добавить реплику. key — ключ говорящего (цвет и перевод имени), who — имя как пришло, voice — длина голоса, с. */
    public static void push(String key, String who, String text, double voice) {
        if (!GConfig.get().subtitles || text == null || text.isBlank()) return;
        double t = now();
        LINES.removeIf(l -> l.end() < t);
        double start = t;
        for (Line l : LINES) if (l.key.equals(key) && l.end() + 0.08 > start) start = l.end() + 0.08;   // свой — после себя
        // не больше двух одновременно: ждём, пока освободится место
        while (true) {
            final double s = start;
            List<Line> at = LINES.stream().filter(l -> l.start <= s + 1e-6 && l.end() > s).toList();
            if (at.size() < MAX_VISIBLE) break;
            start = at.stream().mapToDouble(Line::end).min().orElse(start) + 0.05;
        }
        LINES.add(new Line(key == null ? "" : key, who == null ? "" : who, text, start, readTime(text, voice)));
    }

    public static void clear() {
        LINES.clear();
    }

    public static List<Line> visible(double t) {
        List<Line> v = new ArrayList<>();
        for (Line l : LINES) if (t >= l.start && t < l.end()) v.add(l);
        return v;
    }

    public static float scale() {
        return GConfig.get().subSize / 100f;
    }

    static int textColor() {
        GConfig c = GConfig.get();
        return c.subContrast ? 0xFFFFFFFF : c.subStyle == 0 ? 0xFFE8DDB8 : 0xFFEDEDED;
    }

    static int bgAlpha() {
        GConfig c = GConfig.get();
        return c.subContrast ? Math.max(210, c.subBg * 255 / 100) : c.subBg * 255 / 100;
    }

    /** Строка с именем: «Имя:» цветом и жирным + текст. */
    static Text compose(Line l) {
        MutableText out = Text.empty();
        if (!l.who.isEmpty()) {
            int col = COLORS.getOrDefault(l.key, 0xFFE6E6E6);
            out.append(Text.literal(l.who + ": ").setStyle(Style.EMPTY.withBold(true).withColor(col & 0xFFFFFF)));
        }
        out.append(Text.literal(l.text).setStyle(Style.EMPTY.withColor(textColor() & 0xFFFFFF)));
        return out;
    }

    /** Прямоугольник строки на экране (в интерфейсных пикселях) — для отрисовки и автотеста. */
    public record Box(int x, int y, int w, int h, OrderedText text, float alpha) {}

    /**
     * Раскладка: снизу вверх, над нижним отступом {@code bottom}. Ширина строки ≤ 80 % экрана (и ≤ 420 при масштабе 1),
     * если экран совсем узкий — масштаб уменьшается, чтобы влезло.
     */
    public static List<Box> layout(TextRenderer tr, int w, int h, int bottom, double t) {
        List<Box> out = new ArrayList<>();
        List<Line> vis = visible(t);
        if (vis.isEmpty()) return out;
        float s = scale();
        int maxW = (int) Math.min(w * 0.8f / s, 420);
        if (maxW < 120) maxW = 120;
        int lineH = (int) Math.ceil(11 * s);
        int y = h - bottom - (GConfig.get().subPos == 1 ? h / 6 : 0);
        for (int i = vis.size() - 1; i >= 0; i--) {
            Line l = vis.get(i);
            float a = (float) MathHelper.clamp(Math.min((t - l.start) / 0.18, (l.end() - t) / 0.25), 0, 1);
            List<OrderedText> rows = tr.wrapLines(compose(l), maxW);
            for (int k = rows.size() - 1; k >= 0; k--) {
                int lw = (int) Math.ceil(tr.getWidth(rows.get(k)) * s);
                y -= lineH;
                out.add(new Box(w / 2 - lw / 2, y, lw, lineH, rows.get(k), a));
            }
            y -= (int) (4 * s);
        }
        return out;
    }

    public static void render(DrawContext ctx, TextRenderer tr, int w, int h, int bottom) {
        double t = now();
        LINES.removeIf(l -> l.end() < t - 1);
        float s = scale();
        int bg = bgAlpha();
        for (Box b : layout(tr, w, h, bottom, t)) {
            int a = (int) (b.alpha() * 255);
            if (a <= 3) continue;
            if (bg > 0) ctx.fill(b.x() - 4, b.y() - 1, b.x() + b.w() + 4, b.y() + b.h(), ((bg * a / 255) << 24));
            ctx.getMatrices().push();
            ctx.getMatrices().translate(b.x(), b.y() + 1, 0);
            ctx.getMatrices().scale(s, s, 1);
            ctx.drawTextWithShadow(tr, b.text(), 0, 0, (Math.max(4, a) << 24) | 0xFFFFFF);
            ctx.getMatrices().pop();
        }
    }
}
