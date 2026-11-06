package com.yompix.novograd.client.screen;

import com.yompix.novograd.client.ArsenalClient;
import com.yompix.novograd.client.NovogradClient;
import com.yompix.novograd.client.ui.Ui;
import com.yompix.novograd.gta.Economy;
import com.yompix.novograd.gta.GunMods;
import com.yompix.novograd.item.GunItem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * «Калибр» → «Мастерская»: слева — стволы из рюкзака, справа — выбранный ствол крупно, обвесы и окраска.
 * Ставит Боб (сервер: GunMods.handle), платят как у оружейника — грязными, потом с карты. Снять обвес — бесплатно.
 */
public class GunTuneScreen extends LifeScreen {
    private int selected = -1, listScroll;
    private final List<Integer> slots = new ArrayList<>();

    public GunTuneScreen() {
        super("Мастерская");
    }

    private void collect() {
        slots.clear();
        var inv = mc().player.getInventory();
        for (int i = 0; i < inv.main.size(); i++) if (inv.getStack(i).getItem() instanceof GunItem) slots.add(i);
        if (selected < 0 || !slots.contains(selected)) {
            int held = inv.selectedSlot;
            selected = slots.contains(held) ? held : slots.isEmpty() ? -1 : slots.get(0);
        }
    }

    @Override
    protected void draw(DrawContext ctx, float delta) {
        if (mc().player == null) return;
        collect();
        float a = openAnim();
        int w = Math.min(340, width - 16), h = Math.min(228, height - 16);
        int x = width / 2 - w / 2, y = (int) (height / 2 - h / 2 + (1 - a) * 24);
        panel(ctx, x, y, w, h, 0xFF2A0A20, 0xFF0A0614);
        title(ctx, "МАСТЕРСКАЯ «КАЛИБРА»", x + 8, y + 8, Ui.PINK);
        var d = NovogradClient.data;
        String money = "$" + d.money + (d.dirty > 0 ? "  · грязными $" + d.dirty : "");
        Ui.small(ctx, textRenderer, money, x + w - 8, y + 30, 0.7f, Ui.MONEY, 2);
        int act = Economy.act(d);
        // ---- список стволов
        int lx = x + 8, ly = y + 30, lw = 104;
        ctx.fill(lx, ly, lx + lw, y + h - 8, 0x60000000);
        if (slots.isEmpty()) {
            wrap(ctx, "В рюкзаке нет оружия. Купи ствол у Боба — и возвращайся.", lx + 4, ly + 6, lw - 8, 0xFFB0A8C0);
            return;
        }
        ctx.enableScissor(lx, ly, lx + lw, y + h - 8);
        int ry = ly + 2 - listScroll;
        var inv = mc().player.getInventory();
        for (int slot : slots) {
            ItemStack s = inv.getStack(slot);
            boolean on = slot == selected, hov = hover(lx, ry, lw, 20);
            ctx.fill(lx + 2, ry, lx + lw - 2, ry + 20, on ? Ui.alpha(Ui.PINK, 0.35f) : hov ? 0x30FFFFFF : 0x18FFFFFF);
            if (on) ctx.fill(lx + 2, ry, lx + 4, ry + 20, Ui.PINK);
            ctx.drawItem(s, lx + 6, ry + 2);
            String n = s.getName().getString();
            Ui.small(ctx, textRenderer, n, lx + 25, ry + 4, Math.min(0.75f, (lw - 30f) / Math.max(1, textRenderer.getWidth(n))), 0xFFFFFFFF, 0);
            String m = GunMods.describe(s);
            Ui.small(ctx, textRenderer, m.isEmpty() ? "без обвеса" : m, lx + 25, ry + 12, Math.min(0.55f, (lw - 30f) / Math.max(1, textRenderer.getWidth(m.isEmpty() ? "без обвеса" : m))), 0xFF9A90B0, 0);
            final int sl = slot;
            area(lx, Math.max(ry, ly), lw, 20, () -> selected = sl);
            ry += 22;
        }
        ctx.disableScissor();
        if (selected < 0) return;
        ItemStack st = inv.getStack(selected);
        if (!(st.getItem() instanceof GunItem g)) return;
        // ---- ствол крупно
        int rx = lx + lw + 8, rw = x + w - 8 - rx;
        ctx.fill(rx, ly, rx + rw, ly + 62, 0x50000000);
        ctx.getMatrices().push();
        ctx.getMatrices().translate(rx + rw / 2f - 32, ly - 2, 0);
        ctx.getMatrices().scale(4f, 4f, 1f);
        ctx.drawItem(st, 0, 0);
        ctx.getMatrices().pop();
        Ui.small(ctx, textRenderer, st.getName().getString().toUpperCase(), rx + 4, ly + 53, 0.7f, Ui.CYAN, 0);
        // ---- обвесы
        int allowed = GunMods.allowed(g.gunId());
        int oy = ly + 66;
        for (int i = 0; i < GunMods.PARTS.length; i++) {
            int part = GunMods.PARTS[i];
            boolean can = (allowed & part) != 0, has = GunMods.has(st, part);
            int c = can ? 0xFFFFFFFF : 0xFF6A6478;
            ctx.drawText(textRenderer, GunMods.PART_NAMES[i], rx + 2, oy + 2, c, false);
            Ui.small(ctx, textRenderer, can ? GunMods.PART_INFO[i] : "не ставится на этот ствол", rx + 2, oy + 12, 0.55f, can ? 0xFFA8A0C0 : 0xFF5A5468, 0);
            final int code = selected * 256 + i;
            if (can) {
                if (has) button(ctx, rx + rw - 62, oy + 2, 60, 14, "Снять", 0xFF6A6478, true, () -> ArsenalClient.send("mod", code));
                else if (act < GunMods.PART_ACT[i]) button(ctx, rx + rw - 62, oy + 2, 60, 14, "акт " + com.yompix.novograd.gta.Armory.roman(GunMods.PART_ACT[i]), 0xFF6A6478, false, () -> {});
                else button(ctx, rx + rw - 62, oy + 2, 60, 14, "$" + GunMods.PART_PRICE[i], Ui.PINK, d.money + d.dirty >= GunMods.PART_PRICE[i], () -> ArsenalClient.send("mod", code));
            }
            if (has) ctx.fill(rx, oy + 1, rx + 1, oy + 19, Ui.MONEY);
            oy += 22;
        }
        // ---- окраска
        ctx.drawText(textRenderer, "Окраска", rx + 2, oy + 3, 0xFFFFFFFF, false);
        int bw = (rw - 4) / GunMods.SKIN_NAMES.length;
        int[] colors = {0xFF8A8598, 0xFF6E7A44, Ui.CYAN, Ui.GOLD};
        for (int k = 0; k < GunMods.SKIN_NAMES.length; k++) {
            boolean cur = GunMods.skin(st) == k, locked = act < GunMods.SKIN_ACT[k];
            String label = cur ? "✔ " + GunMods.SKIN_NAMES[k] : locked ? "акт " + com.yompix.novograd.gta.Armory.roman(GunMods.SKIN_ACT[k]) : GunMods.SKIN_NAMES[k] + (GunMods.SKIN_PRICE[k] > 0 ? " $" + GunMods.SKIN_PRICE[k] : "");
            final int code = selected * 256 + 10 + k;
            button(ctx, rx + 2 + k * bw, oy + 14, bw - 3, 14, label, colors[k], !cur && !locked && d.money + d.dirty >= GunMods.SKIN_PRICE[k], () -> ArsenalClient.send("mod", code));
        }
        Ui.small(ctx, textRenderer, "Боб: «Глушитель — и никто не услышит. Если никто не видит.»", x + w / 2f, y + h - 6, 0.55f, 0xFF8A80A0, 1);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        listScroll = Math.max(0, Math.min(Math.max(0, slots.size() * 22 - 150), listScroll - (int) (v * 14)));
        return true;
    }
}
