package com.yompix.glukhoman.client.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

/** Лобби смены (этап 5): кто подключён, адрес для друзей, «Начать» — закрыть и играть. Список живой. */
public class LobbyScreen extends KitScreen {
    public LobbyScreen(Screen parent) {
        super("Смена", parent, true);
    }

    @Override
    protected int rows() {
        return 8;
    }

    @Override
    protected void build() {
        colY += 64;
        item("Начать", b -> client.setScreen(null));
        item("Назад", b -> close());
    }

    @Override
    protected void extra(DrawContext ctx, int mx, int my, double t) {
        var nh = client.getNetworkHandler();
        int y = colY0() - 8;
        var s = client.getServer();
        String addr = s != null && s.isRemote() ? "Друзьям: ваш IP в локальной сети, порт " + s.getServerPort() : s != null ? "Смена только ваша — откройте её по сети из паузы" : "Вы в чужой смене";
        ctx.drawCenteredTextWithShadow(textRenderer, addr, width / 2, y, Kit.alpha(0xA8A090, 1));
        y += 16;
        if (nh == null) return;
        int n = 0;
        for (var e : nh.getPlayerList()) {
            String name = e.getProfile().getName();
            ctx.drawCenteredTextWithShadow(textRenderer, "● " + name + (n == 0 && s != null ? "  (хозяин)" : ""), width / 2, y, Kit.alpha(n == 0 ? Kit.TEXT : Kit.DIM, 1));
            y += 11;
            if (++n >= 4) break;
        }
        if (n < 2) ctx.drawCenteredTextWithShadow(textRenderer, "Ждём остальных" + ".".repeat(1 + (int) (t * 2) % 3), width / 2, y + 2, Kit.alpha(0x807870, 1));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
