package com.yompix.novograd.client.screen;

import com.yompix.novograd.client.NovogradClient;
import com.yompix.novograd.client.ui.Ui;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

/**
 * 0.9.8: «Банк» → «Кредит». Слева — кредитный рейтинг (шкала 300–850) и история платежей, справа — предложение
 * (сумма колесом/кнопками, срок 4/8/12 платежей, платёж и переплата) или текущий кредит (остаток, следующий платёж,
 * «Внести», «Погасить»). Всё решает сервер (gta/Loans); здесь — только показ и просьбы.
 */
public class LoanScreen extends LifeScreen {
    public static NbtCompound state = new NbtCompound();
    private int amount = -1, term = 8;
    private float shownScore = 300;

    public LoanScreen() {
        super("Кредит");
        NovogradClient.send("loan_state");
    }

    private NbtCompound loan() {
        return NovogradClient.data == null ? new NbtCompound() : NovogradClient.data.loan;
    }

    private boolean active() {
        NbtCompound l = loan();
        return l.getInt("left") > 0 || l.getInt("principal") > 0 || l.getInt("overdue") > 0;
    }

    @Override
    protected void draw(DrawContext ctx, float delta) {
        int w = Math.min(440, width - 30), h = Math.min(250, height - 30);
        int x = width / 2 - w / 2, y = height / 2 - h / 2 + (int) ((1 - openAnim()) * 20);
        panel(ctx, x, y, w, h, 0xF0101418, 0xF0080A0D);
        title(ctx, "КРЕДИТ", x + 10, y + 8, 0xFF7CFF8A);
        int score = state.contains("score") ? state.getInt("score") : (loan().contains("score") ? loan().getInt("score") : 620);
        shownScore = Ui.approach(shownScore, score, 12);
        // ---------------- рейтинг: дуга из делений
        int gx = x + 18, gy = y + 34, gw = w / 2 - 34;
        int cx = gx + gw / 2, cy = gy + 72, r = Math.min(64, gw / 2);
        int segs = 40;
        float k = (shownScore - 300) / 550f;
        for (int i = 0; i < segs; i++) {
            float a = MathHelper.PI + MathHelper.PI * i / (segs - 1);
            float f = i / (float) (segs - 1);
            int col = f < 0.36f ? 0xFFE5383B : f < 0.64f ? 0xFFFFC428 : f < 0.8f ? 0xFF8BD450 : 0xFF62E57E;
            if (f > k) col = 0x40FFFFFF;
            int px = cx + (int) (MathHelper.cos(a) * r), py = cy + (int) (MathHelper.sin(a) * r);
            ctx.fill(px - 2, py - 2, px + 2, py + 2, col);
        }
        bigText(ctx, String.valueOf(Math.round(shownScore)), cx, cy - 26, 2.2f, 0xFFFFFFFF);
        center(ctx, state.getString("band").isEmpty() ? "рейтинг" : state.getString("band"), cx, cy - 2, 0xFFB0BEC5);
        // история: 12 последних платежей
        String hist = loan().getString("hist");
        int hx = cx - 12 * 9 / 2, hy = cy + 14;
        for (int i = 0; i < 12; i++) {
            int col = 0x30FFFFFF;
            int j = i - (12 - hist.length());
            if (j >= 0) col = hist.charAt(j) == '1' ? 0xFF62E57E : 0xFFE5383B;
            ctx.fill(hx + i * 9, hy, hx + i * 9 + 6, hy + 6, col);
        }
        Ui.small(ctx, textRenderer, "платежи", cx, hy + 10, 0.75f, 0x90FFFFFF, 1);
        String inc = "доход $" + state.getInt("income") + "/день";
        Ui.small(ctx, textRenderer, inc, cx, hy + 22, 0.8f, 0xFFCFD8DC, 1);
        // ---------------- справа: предложение или текущий кредит
        int rx = x + w / 2 + 6, rw = w / 2 - 18, ry = y + 34;
        ctx.fill(rx - 6, ry - 4, rx - 5, y + h - 18, 0x30FFFFFF);
        if (active()) current(ctx, rx, ry, rw);
        else offer(ctx, rx, ry, rw);
        center(ctx, "Esc — закрыть", x + w / 2, y + h - 12, 0xFF607D8B);
    }

    private void offer(DrawContext ctx, int rx, int ry, int rw) {
        int limit = state.getInt("limit");
        if (amount < 0) amount = Math.min(limit, Math.max(100, limit / 2 / 50 * 50));
        amount = MathHelper.clamp(amount, Math.min(100, limit), limit);
        if (limit < 100) {
            wrap(ctx, state.contains("limit") ? "Сейчас не одобрим: подними доход или рейтинг." : "Связь с банком…", rx, ry + 30, rw, 0xFFB0BEC5);
            return;
        }
        Ui.small(ctx, textRenderer, "СУММА · до $" + limit, rx, ry, 0.8f, 0xFF90A4AE, 0);
        bigText(ctx, "$" + amount, rx + rw / 2, ry + 12, 2f, 0xFF7CFF8A);
        // ползунок
        int sy = ry + 36;
        ctx.fill(rx, sy, rx + rw, sy + 3, 0x40FFFFFF);
        int kx = rx + (int) (rw * (amount - 100f) / Math.max(1, limit - 100));
        ctx.fill(rx, sy, kx, sy + 3, 0xFF62E57E);
        ctx.fill(kx - 2, sy - 3, kx + 2, sy + 6, 0xFFFFFFFF);
        area(rx, sy - 5, rw, 13, () -> {
            float f = MathHelper.clamp((mouseX - rx) / (float) rw, 0, 1);
            amount = Math.round((100 + f * (limit - 100)) / 50f) * 50;
        });
        // срок
        Ui.small(ctx, textRenderer, "СРОК · платёж раз в " + state.getInt("period") + " дн.", rx, sy + 12, 0.8f, 0xFF90A4AE, 0);
        int[] terms = {4, 8, 12};
        int bw = (rw - 8) / 3;
        for (int i = 0; i < 3; i++) {
            int tv = terms[i];
            button(ctx, rx + i * (bw + 4), sy + 22, bw, 16, tv + " плат.", term == tv ? 0xFF2E7D32 : 0xFF37474F, true, () -> term = tv);
        }
        double r = state.getDouble("periodRate");
        int pay = r <= 1e-9 ? amount / term : (int) Math.ceil(amount * r / (1 - Math.pow(1 + r, -term)));
        int over = pay * term - amount;
        String[][] rows = {{"Ставка", String.format(java.util.Locale.ROOT, "%.1f%% / мес.", state.getDouble("rateMonth"))},
                {"Платёж", "$" + pay}, {"Переплата", "$" + over}};
        int ty = sy + 46;
        for (String[] row : rows) {
            ctx.drawText(textRenderer, row[0], rx, ty, 0xFFCFD8DC, false);
            ctx.drawText(textRenderer, row[1], rx + rw - textRenderer.getWidth(row[1]), ty, 0xFFFFFFFF, true);
            ty += 12;
        }
        button(ctx, rx, ty + 6, rw, 20, "Взять $" + amount, 0xFF2FA84F, true, () -> {
            NbtCompound n = new NbtCompound();
            n.putInt("amount", amount);
            n.putInt("term", term);
            NovogradClient.send("loan_take", n);
            amount = -1;
        });
    }

    private void current(DrawContext ctx, int rx, int ry, int rw) {
        NbtCompound l = loan();
        int total = Math.max(1, l.getInt("total")), principal = l.getInt("principal"), overdue = l.getInt("overdue");
        Ui.small(ctx, textRenderer, "ОСТАЛОСЬ", rx, ry, 0.8f, 0xFF90A4AE, 0);
        bigText(ctx, "$" + (principal + overdue), rx + rw / 2, ry + 12, 2f, overdue > 0 ? 0xFFFF6B6B : 0xFFFFFFFF);
        Ui.bar(ctx, rx, ry + 36, rw, 4, 1 - principal / (float) total, 0xFF62E57E);
        long days = l.getLong("next") - state.getLong("day");
        String[][] rows = {{"Платёж", "$" + l.getInt("pay")}, {"Следующий", days <= 0 ? "сегодня утром" : "через " + days + " дн."},
                {"Платежей", String.valueOf(l.getInt("left"))}, {"Просрочка", overdue > 0 ? "$" + overdue : "нет"}};
        int ty = ry + 48;
        for (String[] row : rows) {
            ctx.drawText(textRenderer, row[0], rx, ty, 0xFFCFD8DC, false);
            ctx.drawText(textRenderer, row[1], rx + rw - textRenderer.getWidth(row[1]), ty, row[0].equals("Просрочка") && overdue > 0 ? 0xFFFF6B6B : 0xFFFFFFFF, true);
            ty += 12;
        }
        if (l.getInt("stage") >= 2) wrap(ctx, l.getInt("stage") >= 3 ? "Последнее предупреждение: дальше — изъятие." : "Долгом занимаются коллекторы.", rx, ty + 2, rw, 0xFFFF8A65);
        int by = ty + 22;
        int bw = (rw - 4) / 2;
        button(ctx, rx, by, bw, 18, overdue > 0 ? "Погасить долг" : "Внести $" + l.getInt("pay"), 0xFF2E7D32, true, () -> pay(false));
        button(ctx, rx + bw + 4, by, bw, 18, "Закрыть $" + (principal + overdue), 0xFFF9A825, true, () -> pay(true));
    }

    private void pay(boolean all) {
        NbtCompound n = new NbtCompound();
        n.putBoolean("all", all);
        NovogradClient.send("loan_pay", n);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hx, double vy) {
        int limit = state.getInt("limit");
        int step = limit > 20000 ? 1000 : limit > 4000 ? 250 : 50;
        amount = MathHelper.clamp(amount + (vy > 0 ? step : -step), 100, Math.max(100, limit));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }
}
