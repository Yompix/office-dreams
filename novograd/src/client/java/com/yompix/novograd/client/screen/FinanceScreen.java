package com.yompix.novograd.client.screen;

import com.yompix.novograd.client.NovogradClient;
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
        String[][] tabs = {{"money", "Деньги"}, {"biz", "Бизнесы"}, {"stocks", "Биржа"}};
        for (int i = 0; i < tabs.length; i++) {
            String id = tabs[i][0];
            button(ctx, x + 120 + i * 80, y + 6, 74, 14, tabs[i][1], tab.equals(id) ? 0xFF2E7D32 : 0xFF37474F, true, () -> tab = id);
        }
        int cy = y + 30;
        int money = state.getInt("money"), dirty = state.getInt("dirty");
        switch (tab) {
            case "money" -> {
                bigText(ctx, "$" + money, x + w / 2, cy + 10, 2.5f, 0xFF7CFF8A);
                center(ctx, "на карте — чистые, тратятся везде и не пропадают", x + w / 2, cy + 40, 0xFFB0BEC5);
                bigText(ctx, "$" + dirty, x + w / 2, cy + 62, 2f, 0xFFFF5252);
                center(ctx, "грязные наличные — с ограблений, налётов и инкассаторов", x + w / 2, cy + 86, 0xFFB0BEC5);
                int ty = cy + 104;
                ty = wrap(ctx, "• Грязными платят у оружейника «Калибр» (чёрный рынок).", x + 14, ty, w - 28, 0xFFE0E0E0);
                ty = wrap(ctx, "• Отмыть: каждый твой бизнес отмывает часть каждый день (85% на карту); Рэй на пристани Ривьеры — всё сразу, но берёт 30%.", x + 14, ty + 2, w - 28, 0xFFE0E0E0);
                ty = wrap(ctx, "• Риск: погиб — теряешь половину грязных, задержан — полиция забирает все.", x + 14, ty + 2, w - 28, 0xFFE0E0E0);
                wrap(ctx, "• Сейфы бизнесов копят выручку до трёх дней — заезжай и забирай (золотая метка у бизнеса).", x + 14, ty + 2, w - 28, 0xFFE0E0E0);
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
