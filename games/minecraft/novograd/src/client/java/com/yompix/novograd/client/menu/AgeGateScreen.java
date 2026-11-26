package com.yompix.novograd.client.menu;

import com.yompix.novograd.client.NovogradConfig;
import com.yompix.novograd.client.screen.LifeScreen;
import com.yompix.novograd.client.ui.Ui;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.glfw.GLFW;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.Period;

/**
 * 0.9.7: «Подтверди возраст» — при первом запуске (один раз на установку) вместо окна 0.9.6 «Мод для взрослых».
 * Дата рождения ДД.ММ.ГГГГ (только цифры, точки ставятся сами). Храним только «18+ да/нет» и дату проверки.
 * Не 18+ — мод работает, взрослый контент выключен насовсем (см. {@link NovogradConfig#adultContent}).
 */
public class AgeGateScreen extends LifeScreen {
    private final Screen next;
    private final StringBuilder digits = new StringBuilder();
    private String error = "";

    public AgeGateScreen(Screen next) {
        super("Подтверди возраст");
        this.next = next;
    }

    /** Возраст в полных годах на дату today; −1 — такой даты нет, она в будущем или раньше 1900 года. */
    public static int age(int day, int month, int year, LocalDate today) {
        try {
            LocalDate b = LocalDate.of(year, month, day);
            if (year < 1900 || b.isAfter(today)) return -1;
            return Period.between(b, today).getYears();
        } catch (DateTimeException e) {
            return -1;
        }
    }

    /** Ввод цифрами «ДДММГГГГ» → возраст (−1 — неверно). */
    public static int age(String eight, LocalDate today) {
        if (eight.length() != 8 || !eight.chars().allMatch(Character::isDigit)) return -1;
        return age(Integer.parseInt(eight.substring(0, 2)), Integer.parseInt(eight.substring(2, 4)), Integer.parseInt(eight.substring(4)), today);
    }

    private String part(int from, int to, String mask) {
        StringBuilder b = new StringBuilder();
        for (int i = from; i < to; i++) b.append(i < digits.length() ? digits.charAt(i) : mask.charAt(i - from));
        return b.toString();
    }

    @Override
    protected void draw(DrawContext ctx, float delta) {
        float a = openAnim();
        int cx = width / 2, cy = height / 2;
        Ui.big(ctx, textRenderer, "18+", cx, cy - 74, 3f, Ui.alpha(Ui.PINK, a), 1);
        Ui.big(ctx, textRenderer, "Подтверди возраст", cx, cy - 38, 1.4f, Ui.alpha(Ui.WHITE, a), 1);
        Ui.small(ctx, textRenderer, "Часть контента — только для взрослых. Дату не храним.", cx, cy - 20, 0.85f, Ui.alpha(0xFFB9B7B1, a), 1);
        // три поля: ДД . ММ . ГГГГ
        String[] parts = {part(0, 2, "ДД"), part(2, 4, "ММ"), part(4, 8, "ГГГГ")};
        int[] ws = {30, 30, 52};
        int total = ws[0] + ws[1] + ws[2] + 2 * 12, x = cx - total / 2, y = cy - 4;
        int filled = digits.length();
        int[] ends = {2, 4, 8};
        for (int i = 0; i < 3; i++) {
            boolean active = filled < ends[i] && (i == 0 || filled >= ends[i - 1]);
            ctx.fill(x, y, x + ws[i], y + 20, 0xC0101012);
            ctx.fill(x, y + 19, x + ws[i], y + 20, active ? Ui.PINK : 0x60FFFFFF);
            boolean empty = filled <= (i == 0 ? 0 : ends[i - 1]);
            Ui.big(ctx, textRenderer, parts[i], x + ws[i] / 2f, y + 6, 1f, empty ? 0x70FFFFFF : Ui.WHITE, 1);
            x += ws[i];
            if (i < 2) {
                Ui.big(ctx, textRenderer, ".", x + 6, y + 6, 1f, 0xB0FFFFFF, 1);
                x += 12;
            }
        }
        if (!error.isEmpty()) Ui.small(ctx, textRenderer, error, cx, y + 26, 0.8f, Ui.RED, 1);
        int by = cy + 38;
        button(ctx, cx - 92, by, 88, 18, "ПОДТВЕРДИТЬ", Ui.PINK, digits.length() == 8, this::confirm);
        button(ctx, cx + 4, by, 88, 18, "ВЫЙТИ", 0xFF8A8985, true, this::quit);
    }

    private void confirm() {
        int age = age(digits.toString(), LocalDate.now());
        if (age < 0) {
            error = "Такой даты нет — проверь";
            return;
        }
        NovogradConfig.setAge(age >= 18);
        if (client != null) client.setScreen(next);
    }

    private void quit() {
        if (client != null) client.scheduleStop();
    }

    @Override
    public boolean charTyped(char ch, int mods) {
        if (Character.isDigit(ch) && digits.length() < 8) {
            digits.append(ch);
            error = "";
            return true;
        }
        return super.charTyped(ch, mods);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_BACKSPACE && digits.length() > 0) {
            digits.setLength(digits.length() - 1);
            error = "";
            return true;
        }
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && digits.length() == 8) {
            confirm();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    /** Для uiaudit: показать с введённой датой. */
    public AgeGateScreen typed(String s) {
        digits.setLength(0);
        digits.append(s, 0, Math.min(8, s.length()));
        return this;
    }
}
