package com.yompix.novograd.client.screen;

import com.yompix.novograd.client.ArmoryClient;
import com.yompix.novograd.client.ArsenalClient;
import com.yompix.novograd.client.NovogradClient;
import com.yompix.novograd.client.ui.Ui;
import com.yompix.novograd.gta.Bodyguards;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

/**
 * Телефон → «Охрана»: агентство «Щит». Нанять до трёх телохранителей (оплата каждые сутки), уволить,
 * «ждать здесь / за мной» (то же — ПКМ по своему охраннику). Открывается с акта III.
 */
public class BodyguardScreen extends LifeScreen {
    public BodyguardScreen() {
        super("Охрана");
        ArsenalClient.send("guard_info", 0);
    }

    public void refresh() {}

    @Override
    protected void draw(DrawContext ctx, float delta) {
        float a = openAnim();
        int w = Math.min(300, width - 16), h = Math.min(232, height - 8);
        int x = width / 2 - w / 2, y = (int) (height / 2 - h / 2 + (1 - a) * 24);
        panel(ctx, x, y, w, h, 0xFF06303A, 0xFF040A10);
        title(ctx, "АГЕНТСТВО «ЩИТ»", x + 8, y + 8, Ui.CYAN);
        var d = NovogradClient.data;
        Ui.small(ctx, textRenderer, "$" + d.money + (d.dirty > 0 ? " · грязными $" + d.dirty : ""), x + w - 8, y + h - 9, 0.7f, Ui.MONEY, 2);
        NbtCompound n = ArmoryClient.guards;
        int act = n.contains("act") ? n.getInt("act") : com.yompix.novograd.gta.Economy.act(d);
        int yy = y + 30;
        if (act < Bodyguards.ACT) {
            wrap(ctx, "«Щит» работает только с теми, у кого есть имя в городе. Позвоните, когда о вас заговорят — с акта III.",
                    x + 12, yy + 8, w - 24, 0xFFB8C8D0);
            return;
        }
        yy += wrap(ctx, "Ходят за вами, садятся в машину, отвечают огнём. Плата — каждые сутки (сначала наличными).",
                x + 10, yy, w - 20, 0xFF9FB4C0) + 4;
        NbtList list = n.getList("list", NbtElement.COMPOUND_TYPE);
        Ui.small(ctx, textRenderer, "ВАША ОХРАНА  " + list.size() + " / " + Bodyguards.MAX, x + 10, yy, 0.7f, Ui.CYAN, 0);
        yy += 9;
        boolean stay = false;
        for (int i = 0; i < list.size(); i++) {
            NbtCompound g = list.getCompound(i);
            int tier = g.getInt("tier");
            stay = g.getBoolean("stay");
            ctx.fill(x + 8, yy, x + w - 8, yy + 18, 0x40FFFFFF);
            ctx.drawText(textRenderer, g.getString("name"), x + 12, yy + 2, 0xFFFFFFFF, false);
            Ui.small(ctx, textRenderer, Bodyguards.TIER_NAMES[tier] + " · $" + Bodyguards.TIER_FEE[tier] + "/сутки" + (stay ? " · ждёт" : ""), x + 12, yy + 11, 0.55f, 0xFF9FB4C0, 0);
            Ui.bar(ctx, x + w - 128, yy + 7, 50, 4, g.getFloat("hp"), Ui.MONEY);
            final int idx = i;
            button(ctx, x + w - 72, yy + 2, 60, 14, "Уволить", 0xFF8A3040, true, () -> ArsenalClient.send("guard_fire", idx));
            yy += 20;
        }
        if (list.isEmpty()) {
            Ui.small(ctx, textRenderer, "никого — вы один", x + 12, yy + 2, 0.65f, 0xFF6A7A88, 0);
            yy += 14;
        } else {
            button(ctx, x + 8, yy, 120, 14, stay ? "За мной" : "Ждать здесь", Ui.CYAN, true, () -> ArsenalClient.send("guard_stay", 0));
            yy += 18;
        }
        yy += 4;
        Ui.small(ctx, textRenderer, "НАНЯТЬ", x + 10, yy, 0.7f, Ui.CYAN, 0);
        yy += 9;
        for (int t = 0; t < 3; t++) {
            ctx.fill(x + 8, yy, x + w - 8, yy + 18, 0x28FFFFFF);
            ctx.drawText(textRenderer, Bodyguards.TIER_NAMES[t], x + 12, yy + 2, 0xFFFFFFFF, false);
            Ui.small(ctx, textRenderer, Bodyguards.TIER_INFO[t], x + 12, yy + 11, 0.55f, 0xFF9FB4C0, 0);
            final int tier = t;
            boolean ok = list.size() < Bodyguards.MAX && d.money + d.dirty >= Bodyguards.TIER_FEE[t];
            button(ctx, x + w - 92, yy + 2, 80, 14, "$" + Bodyguards.TIER_FEE[t] + "/сутки", Ui.MONEY, ok, () -> ArsenalClient.send("guard_hire", tier));
            yy += 20;
        }
    }
}
