package com.yompix.novograd.client.screen;

import com.yompix.novograd.client.NovogradClient;
import com.yompix.novograd.client.ui.Ui;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import org.lwjgl.glfw.GLFW;

/**
 * Телефон → «Финансы»: карта и грязные наличные, бизнесы (сейфы, отмывка, улучшения) и биржа острова.
 */
public class FinanceScreen extends LifeScreen {
    public static NbtCompound state = new NbtCompound();
    private String tab = "money";
    private int qty = 10;

    public FinanceScreen() {
        super("Финансы");
        NovogradClient.send("eco_state");
    }

    @Override
    protected void draw(DrawContext ctx, float delta) {
        int w = Math.min(460, width - 30), h = Math.min(300, height - 30);
        int x = width / 2 - w / 2, y = height / 2 - h / 2;
        panel(ctx, x, y, w, h, 0xF0101418, 0xF0080A0D);
        title(ctx, "ФИНАНСЫ", x + 10, y + 8, 0xFF7CFF8A);
        String[][] tabs = {{"money", "Деньги"}, {"market", "Рынок"}, {"biz", "Бизнесы"}, {"stocks", "Биржа"}};
        for (int i = 0; i < tabs.length; i++) {
            String id = tabs[i][0];
            button(ctx, x + 100 + i * 66, y + 6, 62, 14, tabs[i][1], tab.equals(id) ? 0xFF2E7D32 : 0xFF37474F, true, () -> tab = id);
        }
        int cy = y + 30;
        int money = state.getInt("money"), dirty = state.getInt("dirty");
        switch (tab) {
            case "money" -> {
                // 0.9.6: карта · наличные · грязные, цены и проценты — цифрами
                int cash = state.getInt("cash");
                int cw = (w - 20) / 3;
                String[][] cols = {{"$" + money, "КАРТА"}, {"$" + cash, "НАЛИЧНЫЕ"}, {"$" + dirty, "ГРЯЗНЫЕ"}};
                int[] colors = {0xFF7CFF8A, 0xFFFFE08A, 0xFFFF5252};
                for (int i = 0; i < 3; i++) {
                    int cx = x + 10 + cw * i + cw / 2;
                    bigText(ctx, cols[i][0], cx, cy + 8, 1.8f, colors[i]);
                    center(ctx, cols[i][1], cx, cy + 30, 0xFFB0BEC5);
                }
                double cpi = state.contains("cpi") ? state.getDouble("cpi") : 1;
                String[][] rows = {
                        {"Цены", String.format(java.util.Locale.ROOT, "×%.3f", cpi)},
                        {"Инфляция", String.format(java.util.Locale.ROOT, "%+.1f%% / мес.", state.getDouble("inflMonth"))},
                        {"Вклад (остаток на карте)", String.format(java.util.Locale.ROOT, "+%.1f%% / мес.", state.getDouble("saveRate"))},
                        {"Долг на карте", String.format(java.util.Locale.ROOT, "−%.0f%% / мес.", state.getDouble("loanRate"))},
                        {"Счета вчера", "−$" + state.getInt("bills")},
                        {"Проценты вчера", (state.getInt("interest") >= 0 ? "+$" : "−$") + Math.abs(state.getInt("interest"))}};
                int ty = cy + 46;
                for (String[] r : rows) {
                    ctx.drawText(textRenderer, r[0], x + 16, ty, 0xFFE0E0E0, false);
                    ctx.drawText(textRenderer, r[1], x + w - 16 - textRenderer.getWidth(r[1]), ty, 0xFFFFFFFF, true);
                    ty += 12;
                }
                // график индекса цен
                var hist = state.getList("cpiHistory", NbtElement.DOUBLE_TYPE);
                int gx = x + 16, gw = w - 32, gy = ty + 6, gh = Math.max(16, y + h - gy - 10);
                ctx.fill(gx, gy, gx + gw, gy + gh, 0x40000000);
                if (hist.size() > 1) {
                    double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
                    for (int i = 0; i < hist.size(); i++) {
                        lo = Math.min(lo, hist.getDouble(i));
                        hi = Math.max(hi, hist.getDouble(i));
                    }
                    if (hi - lo < 1e-4) hi = lo + 1e-4;
                    for (int i = 0; i < hist.size(); i++) {
                        int px = gx + i * (gw - 2) / (hist.size() - 1);
                        int py = gy + gh - 2 - (int) ((hist.getDouble(i) - lo) / (hi - lo) * (gh - 4));
                        ctx.fill(px, py, px + 2, py + 2, 0xFFFFC428);
                    }
                }
                ctx.drawText(textRenderer, "ЦЕНЫ", gx + 3, gy + 3, 0x90FFFFFF, false);
            }
            case "market" -> {
                // 0.9.7: рынок — ставка ЦБ, безработица, зарплаты, новость дня, цены по категориям, график
                var mk = state.getCompound("macro");
                String ev = mk.getString("eventTitle");
                String[][] rows = {
                        {"Инфляция", String.format(java.util.Locale.ROOT, "%+.2f%% / мес.", mk.getDouble("infl"))},
                        {"Ставка центробанка", String.format(java.util.Locale.ROOT, "%.2f%% / мес.", mk.getDouble("rate"))},
                        {"Безработица", String.format(java.util.Locale.ROOT, "%.1f%%", mk.getDouble("unemp"))},
                        {"Зарплаты", String.format(java.util.Locale.ROOT, "×%.2f", mk.contains("wage") ? mk.getDouble("wage") : 1)}};
                int ty = cy;
                for (String[] r : rows) {
                    ctx.drawText(textRenderer, r[0], x + 16, ty, 0xFFE0E0E0, false);
                    ctx.drawText(textRenderer, r[1], x + w / 2 - 10 - textRenderer.getWidth(r[1]), ty, 0xFFFFFFFF, true);
                    ty += 12;
                }
                if (!ev.isEmpty()) wrap(ctx, "Новость: " + ev, x + 16, ty + 2, w / 2 - 26, 0xFFFFC428);
                // цены по категориям: полоска от ×0,75 до ×1,4, отметка ×1
                var cats = mk.getList("cats", NbtElement.COMPOUND_TYPE);
                int bx = x + w / 2 + 6, by = cy, bw = w / 2 - 22;
                for (int i = 0; i < cats.size(); i++) {
                    var c = cats.getCompound(i);
                    double m = c.getDouble("mul");
                    int ry = by + i * 11;
                    ctx.drawText(textRenderer, c.getString("name"), bx, ry, 0xFFCFD8DC, false);
                    int sx = bx + 56, sw = bw - 92;
                    ctx.fill(sx, ry + 3, sx + sw, ry + 5, 0x40FFFFFF);
                    int one = sx + (int) (sw * (1 - 0.75) / 0.65), at = sx + (int) (sw * Math.max(0, Math.min(1, (m - 0.75) / 0.65)));
                    ctx.fill(Math.min(one, at), ry + 2, Math.max(one, at) + 1, ry + 6, m >= 1 ? 0xFFFF7043 : 0xFF66BB6A);
                    ctx.fill(one, ry + 1, one + 1, ry + 7, 0xFFFFFFFF);
                    String v = String.format(java.util.Locale.ROOT, "×%.2f", m);
                    ctx.drawText(textRenderer, v, bx + bw - textRenderer.getWidth(v), ry, 0xFFFFFFFF, false);
                }
                // график: инфляция, ставка, безработица по дням
                var hist = mk.getList("hist", NbtElement.COMPOUND_TYPE);
                int gx = x + 16, gw = w - 32, gy = Math.max(ty + 26, by + cats.size() * 11 + 6), gh = Math.max(16, y + h - gy - 10);
                ctx.fill(gx, gy, gx + gw, gy + gh, 0x40000000);
                String[] keys = {"infl", "rate", "unemp"};
                int[] colors = {0xFFFFC428, 0xFF4FC3F7, 0xFFEF5350};
                for (int k = 0; k < 3 && hist.size() > 1; k++) {
                    double hi = k == 2 ? 15 : 10;
                    for (int i = 0; i < hist.size(); i++) {
                        int px = gx + i * (gw - 2) / (hist.size() - 1);
                        int py = gy + gh - 2 - (int) (Math.max(0, Math.min(hi, hist.getCompound(i).getDouble(keys[k]))) / hi * (gh - 4));
                        ctx.fill(px, py, px + 2, py + 2, colors[k]);
                    }
                }
                Ui.small(ctx, textRenderer, "инфляция · ставка · безработица (по дням)", gx + 3, gy + 3, 0.7f, 0x90FFFFFF, 0);
            }
            case "biz" -> {
                var list = state.getList("biz", NbtElement.COMPOUND_TYPE);
                for (int i = 0; i < list.size(); i++) {
                    NbtCompound b = list.getCompound(i);
                    int ry = cy + i * 40;
                    ctx.fill(x + 10, ry, x + w - 10, ry + 36, b.getBoolean("own") ? 0x6020502A : 0x40202830);
                    ctx.drawText(textRenderer, b.getString("name"), x + 16, ry + 4, 0xFFFFFFFF, true);
                    if (b.getBoolean("own")) {
                        ctx.drawText(textRenderer, "ур." + (b.getInt("level") + 1) + " · +$" + b.getInt("income") + "/день · сейф $" + b.getInt("safe") + " · отмывает до $" + b.getInt("wash") + "/день",
                                x + 16, ry + 18, 0xFFA5D6A7, false);
                        int up = b.getInt("upgrade");
                        final int idx = i;
                        if (up > 0) button(ctx, x + w - 128, ry + 8, 110, 18, "Улучшить $" + up, 0xFFF9A825, money >= up, () -> {
                            NbtCompound n = new NbtCompound();
                            n.putInt("i", idx);
                            NovogradClient.send("biz_upgrade", n);
                        });
                        else ctx.drawText(textRenderer, "максимум", x + w - 70, ry + 14, 0xFFFFD54F, false);
                    } else {
                        ctx.drawText(textRenderer, "продаётся $" + b.getInt("price") + " · доход $" + b.getInt("income") + "/день — купить на месте (золотая метка)", x + 16, ry + 18, 0xFF90A4AE, false);
                    }
                }
            }
            default -> {
                var list = state.getList("stocks", NbtElement.COMPOUND_TYPE);
                ctx.drawText(textRenderer, "Покупка/продажа: по " + qty + " шт. (колесо — изменить). Твои дела двигают цены!", x + 14, cy - 2, 0xFFB0BEC5, false);
                for (int i = 0; i < list.size(); i++) {
                    NbtCompound s = list.getCompound(i);
                    int ry = cy + 12 + i * 40;
                    ctx.fill(x + 10, ry, x + w - 10, ry + 36, 0x40202830);
                    double price = s.getDouble("price");
                    var hist = s.getList("h", NbtElement.DOUBLE_TYPE);
                    double first = hist.isEmpty() ? price : hist.getDouble(0);
                    double ch = (price - first) / first * 100;
                    ctx.drawText(textRenderer, s.getString("t") + "  " + s.getString("name"), x + 16, ry + 4, 0xFFFFFFFF, true);
                    ctx.drawText(textRenderer, String.format("$%.2f  %s%.1f%%  · у тебя %d", price, ch >= 0 ? "+" : "", ch, s.getInt("own")), x + 16, ry + 18, ch >= 0 ? 0xFF7CFF8A : 0xFFFF5252, false);
                    // мини-график за последние двое суток
                    int gx = x + 200, gw = 110, gy = ry + 4, gh = 28;
                    if (hist.size() > 1) {
                        double lo = Double.MAX_VALUE, hi = 0;
                        for (int k = 0; k < hist.size(); k++) { lo = Math.min(lo, hist.getDouble(k)); hi = Math.max(hi, hist.getDouble(k)); }
                        double span = Math.max(0.01, hi - lo);
                        for (int k = 0; k < hist.size(); k++) {
                            int px = gx + k * gw / Math.max(1, hist.size() - 1);
                            int py = gy + gh - (int) ((hist.getDouble(k) - lo) / span * gh);
                            ctx.fill(px, py, px + 2, py + 2, ch >= 0 ? 0xFF7CFF8A : 0xFFFF5252);
                        }
                    }
                    String t = s.getString("t");
                    button(ctx, x + w - 128, ry + 8, 54, 18, "Купить", 0xFF2E7D32, money >= price * qty, () -> trade(t, qty));
                    button(ctx, x + w - 68, ry + 8, 54, 18, "Продать", 0xFFC62828, s.getInt("own") > 0, () -> trade(t, -qty));
                }
            }
        }
        center(ctx, "Esc — закрыть", x + w / 2, y + h - 12, 0xFF607D8B);
    }

    private void trade(String t, int q) {
        NbtCompound n = new NbtCompound();
        n.putString("t", t);
        n.putInt("qty", q);
        NovogradClient.send("stock_trade", n);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hx, double vy) {
        int[] steps = {1, 5, 10, 50, 100};
        int i = 0;
        while (i < steps.length - 1 && steps[i] < qty) i++;
        i = Math.max(0, Math.min(steps.length - 1, i + (vy > 0 ? 1 : -1)));
        qty = steps[i];
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
