package com.yompix.lifecraft.client.screen;

import com.yompix.lifecraft.ModContent;
import com.yompix.lifecraft.client.LifeCraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.MathHelper;

/**
 * Урок у доски: зелёная доска, мел, 5 вопросов по 20 секунд. Таймер-полоска тает,
 * ответ подсвечивается, в конце — оценка «вырастает» на доске.
 */
public class LessonScreen extends LifeScreen {
    private static final int TIME = 20_000;
    private final String subject, level;
    private final String[][] answers;
    private final String[] questions;
    private final int[] wrongHint;
    private final boolean hasBook;
    private final int[] chosen;
    private int index;
    private long questionAt = System.currentTimeMillis();
    private long chosenAt = -1;
    private boolean hintUsed;
    private int hidden = -1;
    private NbtCompound result;
    private long resultAt;
    private boolean sent;

    public LessonScreen(NbtCompound n) {
        super("Урок");
        subject = n.getString("subject");
        level = n.getString("level");
        hasBook = n.getBoolean("hasBook");
        NbtList qs = n.getList("questions", NbtElement.COMPOUND_TYPE);
        questions = new String[qs.size()];
        answers = new String[qs.size()][4];
        wrongHint = new int[qs.size()];
        chosen = new int[qs.size()];
        java.util.Arrays.fill(chosen, -1);
        for (int i = 0; i < qs.size(); i++) {
            NbtCompound q = qs.getCompound(i);
            questions[i] = q.getString("q");
            NbtList a = q.getList("a", NbtElement.STRING_TYPE);
            for (int j = 0; j < 4; j++) answers[i][j] = a.getString(j);
            wrongHint[i] = q.contains("wrong") ? q.getInt("wrong") : -1;
        }
    }

    public void result(NbtCompound n) {
        result = n;
        resultAt = System.currentTimeMillis();
        if (client != null && client.player != null)
            client.player.playSound(n.getInt("grade") >= 4 ? ModContent.LEVEL_UP : n.getInt("grade") == 3 ? ModContent.ANSWER_RIGHT : ModContent.ANSWER_WRONG, 0.8f, 1f);
    }

    private void choose(int a) {
        if (chosenAt >= 0 || index >= questions.length) return;
        chosen[index] = a;
        chosenAt = System.currentTimeMillis();
    }

    private void advance() {
        index++;
        chosenAt = -1;
        hidden = -1;
        questionAt = System.currentTimeMillis();
        if (index >= questions.length && !sent) {
            sent = true;
            NbtCompound n = new NbtCompound();
            n.putIntArray("answers", chosen);
            n.putBoolean("hint", hintUsed);
            LifeCraftClient.send("lesson_answer", n);
        }
    }

    @Override
    protected void draw(DrawContext ctx, float delta) {
        long now = System.currentTimeMillis();
        if (index < questions.length) {
            if (chosenAt < 0 && now - questionAt > TIME) choose(-1);
            if (chosenAt >= 0 && now - chosenAt > 450) advance();
        }
        float a = openAnim();
        int w = Math.min(320, width - 20), h = Math.min(210, height - 20);
        int x = width / 2 - w / 2, y = (int) (height / 2 - h / 2 + (1 - a) * 30);
        // доска в деревянной раме
        panel(ctx, x - 6, y - 6, w + 12, h + 12, 0xFF8A6440, 0xFF5E4026);
        ctx.fillGradient(x, y, x + w, y + h, 0xFF2F6140, 0xFF244E33);
        for (int i = 0; i < 6; i++) ctx.fill(x + 10 + i * 47, y + h - 14 + (i % 2), x + 30 + i * 47, y + h - 13 + (i % 2), 0x22FFFFFF);
        ctx.fill(x + 20, y + h + 1, x + 60, y + h + 4, 0xFFEFEFEF);
        center(ctx, subject + " · " + level, x + w / 2, y + 8, 0xFFFFF4B0);
        if (result != null) {
            drawResult(ctx, x, y, w, h);
            return;
        }
        if (index >= questions.length) {
            center(ctx, "Учитель проверяет…", x + w / 2, y + h / 2, 0xFFE8F0E8);
            return;
        }
        // прогресс по вопросам
        for (int i = 0; i < questions.length; i++) {
            int c = i < index ? 0xFFE8F0E8 : i == index ? 0xFFFFD36E : 0x55FFFFFF;
            ctx.fill(x + w / 2 - questions.length * 7 + i * 14, y + 22, x + w / 2 - questions.length * 7 + i * 14 + 10, y + 25, c);
        }
        // вопрос «пишется мелом»: буквы появляются по одной
        String q = questions[index];
        int shown = (int) MathHelper.clamp((now - questionAt) / 18, 0, q.length());
        int qh = 0;
        var lines = textRenderer.wrapLines(net.minecraft.text.Text.literal(q.substring(0, shown)), (int) ((w - 30) / 1.4f));
        for (int i = 0; i < lines.size(); i++) {
            ctx.getMatrices().push();
            ctx.getMatrices().translate(x + w / 2f, y + 36 + i * 15, 0);
            ctx.getMatrices().scale(1.4f, 1.4f, 1);
            ctx.drawText(textRenderer, lines.get(i), -textRenderer.getWidth(lines.get(i)) / 2, 0, 0xFFF4F7F4, true);
            ctx.getMatrices().pop();
            qh = (i + 1) * 15;
        }
        // таймер
        float left = 1 - MathHelper.clamp((now - questionAt) / (float) TIME, 0, 1);
        int tc = left > 0.5f ? 0xFF7BE07B : left > 0.25f ? 0xFFFFD36E : 0xFFFF6B6B;
        ctx.fill(x + 20, y + 44 + qh, x + w - 20, y + 47 + qh, 0x33000000);
        ctx.fill(x + 20, y + 44 + qh, x + 20 + (int) ((w - 40) * left), y + 47 + qh, tc);
        // ответы 2×2
        int bw = (w - 50) / 2, bh = 26, by = y + h - 76;
        for (int i = 0; i < 4; i++) {
            final int ans = i;
            int bx = x + 20 + (i % 2) * (bw + 10), byy = by + (i / 2) * (bh + 8);
            if (i == hidden) {
                ctx.fill(bx, byy, bx + bw, byy + bh, 0x22000000);
                continue;
            }
            int color = 0xFF3D7A52;
            if (chosenAt >= 0 && chosen[index] == i) color = 0xFFFFB84D;
            float pop = Math.min(1, (now - questionAt) / 200f - i * 0.15f);
            if (pop <= 0) continue;
            button(ctx, bx, byy, bw, bh, "ABCD".charAt(i) + ")  " + answers[index][i], color, chosenAt < 0, () -> choose(ans));
        }
        if (hasBook && !hintUsed && wrongHint[index] >= 0)
            button(ctx, x + w - 92, y + 6, 84, 12, "Учебник", 0xFF2C6ECB, chosenAt < 0, () -> {
                hintUsed = true;
                hidden = wrongHint[index];
            });
    }

    private void drawResult(DrawContext ctx, int x, int y, int w, int h) {
        int grade = result.getInt("grade");
        float t = MathHelper.clamp((System.currentTimeMillis() - resultAt) / 500f, 0, 1);
        float s = 1 + (float) Math.sin(t * Math.PI) * 0.6f;
        int c = grade == 5 ? 0xFF7BE07B : grade == 4 ? 0xFFB8E986 : grade == 3 ? 0xFFFFD36E : 0xFFFF6B6B;
        // оценка в кружке, как красной ручкой в дневнике
        int cx = x + w / 2, cy = y + 62;
        for (int r = 22; r >= 20; r--)
            for (int i = 0; i < 48; i++) {
                double ang = i / 48.0 * Math.PI * 2;
                int px = cx + (int) (Math.cos(ang) * r * s * t), py = cy + (int) (Math.sin(ang) * r * s * t);
                ctx.fill(px, py, px + 2, py + 2, c);
            }
        bigText(ctx, String.valueOf(grade), cx, cy - (int) (10 * s), 3f * s, c);
        String[] words = {"", "", "Двойка… попробуй ещё", "Тройка — зачёт", "Хорошо!", "Отлично!"};
        center(ctx, words[grade] + "  (" + result.getInt("right") + " из " + questions.length + ")", cx, y + 96, 0xFFF4F7F4);
        NbtList corr = result.getList("correct", NbtElement.STRING_TYPE);
        for (int i = 0; i < corr.size() && i < questions.length; i++) {
            boolean ok = chosen[i] >= 0 && answers[i][chosen[i]].equals(corr.getString(i));
            String line = (ok ? "✔ " : "✖ ") + trim(questions[i], 28) + " → " + corr.getString(i);
            ctx.drawText(textRenderer, line, x + 16, y + 112 + i * 11, ok ? 0xFFB8F0B8 : 0xFFFFB0B0, false);
        }
        button(ctx, x + w / 2 - 40, y + h - 22, 80, 16, "Закрыть", 0xFF3D7A52, true, this::close);
    }

    private static String trim(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n - 1) + "…";
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (result == null && index < questions.length && key >= 49 && key <= 52) {
            choose(key - 49);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return result != null || index >= questions.length;
    }
}
