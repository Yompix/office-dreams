package com.yompix.novograd.client.city;

import com.yompix.novograd.client.NovogradClient;
import com.yompix.novograd.client.screen.LifeScreen;
import com.yompix.novograd.client.ui.Ui;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.NbtCompound;

/** 0.9.6: телефон → «Тайник»: уровень тайника, охрана, риск кражи, деньги и вещи (класть и брать — только дома). */
public class StashScreen extends LifeScreen {
    public static NbtCompound state = new NbtCompound();
    static final String[] TIERS = {"Нет", "За картиной", "Под полом", "Сейф в стене"};

    public StashScreen() {
        super("Тайник");
        NovogradClient.send("stash_state", new NbtCompound());
    }

    private static void send(String act, int amount) {
        NbtCompound n = new NbtCompound();
        n.putString("act", act);
        n.putInt("amount", amount);
        NovogradClient.send("stash", n);
    }

    @Override
    protected void draw(DrawContext ctx, float delta) {
        float an = openAnim();
        int top = 8 + (int) ((1 - an) * -16);
        title(ctx, "ТАЙНИК", 12, top, 0xFFA0785A);
        int tier = state.getInt("tier");
        boolean home = state.getBoolean("home");
        int[] pr = state.getIntArray("prices");
        int x = 12, w = width - 24, y = top + 30;
        Ui.panel(ctx, x, y, w, 40, 0xFFA0785A);
        Ui.big(ctx, textRenderer, TIERS[Math.min(3, tier)].toUpperCase(), x + 8, y + 6, 1.3f, Ui.WHITE, 0);
        Ui.small(ctx, textRenderer, "риск кражи " + state.getInt("risk") + "% в день", x + 8, y + 24, 0.8f, state.getInt("risk") > 8 ? Ui.RED : Ui.MONEY, 0);
        if (tier < 3 && pr.length >= 3) button(ctx, x + w - 128, y + 10, 120, 18, "УЛУЧШИТЬ $" + pr[tier], Ui.GOLD, home, () -> send("upgrade", 0));
        y += 46;
        // охрана
        String[] sec = {"alarm", "dog", "cams"}, names = {"СИГНАЛИЗАЦИЯ", "СОБАКА", "КАМЕРЫ"};
        int bw = (w - 8) / 3;
        for (int i = 0; i < 3; i++) {
            boolean have = state.getBoolean(sec[i]);
            final String id = sec[i];
            button(ctx, x + i * (bw + 4), y, bw, 16, have ? names[i] + " ✔" : names[i] + (pr.length > 3 + i ? " $" + pr[3 + i] : ""), have ? 0xFF2E7D32 : 0xFF3F7FC0, !have, () -> send(id, 0));
        }
        y += 24;
        // деньги
        var d = NovogradClient.data;
        String[][] rows = {{"cash", "НАЛИЧНЫЕ", "$" + state.getInt("cash"), "$" + d.cash}, {"dirty", "ГРЯЗНЫЕ", "$" + state.getInt("dirty"), "$" + d.dirty}};
        for (String[] r : rows) {
            ctx.drawText(textRenderer, r[1], x + 4, y + 4, Ui.WHITE, true);
            Ui.small(ctx, textRenderer, "в тайнике " + r[2] + " · при себе " + r[3], x + 90, y + 5, 0.8f, 0xFFB9B7B1, 0);
            button(ctx, x + w - 150, y, 72, 16, "ПОЛОЖИТЬ", 0xFFA0785A, home && tier > 0, () -> send(r[0] + "_in", 1_000_000));
            button(ctx, x + w - 74, y, 72, 16, "ВЗЯТЬ", 0xFF6E6D69, home, () -> send(r[0] + "_out", 1_000_000));
            y += 20;
        }
        button(ctx, x, y + 4, w, 18, "ВЕЩИ (" + state.getInt("itemCount") + ")", 0xFFA0785A, home && tier > 0, () -> send("items", 0));
        if (!home) Ui.small(ctx, textRenderer, "Класть и брать — дома", width / 2f, y + 28, 0.85f, Ui.GOLD, 1);
        ctx.fill(0, height - 18, width, height, 0xE0040404);
        String back = "‹ НАЗАД";
        int bbw = textRenderer.getWidth(back) + 16;
        Ui.slant(ctx, width - bbw - 8, height - 16, bbw, 14, 4, hover(width - bbw - 8, height - 17, bbw, 17) ? Ui.PINK : 0x60FFFFFF);
        ctx.drawText(textRenderer, back, width - bbw, height - 13, 0xFFFFFFFF, false);
        area(width - bbw - 8, height - 17, bbw, 17, this::close);
    }
}
