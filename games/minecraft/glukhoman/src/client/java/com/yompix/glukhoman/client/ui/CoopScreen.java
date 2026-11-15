package com.yompix.glukhoman.client.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;

/**
 * «Вместе» (этап 5): позвать друзей — открыть свою смену по сети (после загрузки мира — сразу, с лобби);
 * подключиться — список серверов и смен по локальной сети (ванильный, с поиском LAN) или прямой адрес.
 */
public class CoopScreen extends KitScreen {
    /** Открыть мир по сети, как только он загрузится (из главного меню). */
    public static boolean hostOnJoin;

    public CoopScreen(Screen parent) {
        super("Вместе", parent, false);
        sub = "До 4 сторожей на одной станции";
    }

    @Override
    protected int rows() {
        return 5;
    }

    @Override
    protected void build() {
        var c = client;
        item("Позвать друзей (новая смена)", b -> {
            hostOnJoin = true;
            com.yompix.glukhoman.client.menu.WorldStart.newShift(c, this);
        });
        var cont = item("Позвать друзей (продолжить)", b -> {
            hostOnJoin = true;
            com.yompix.glukhoman.client.menu.WorldStart.continueShift(c, this);
        });
        if (!com.yompix.glukhoman.client.menu.WorldStart.hasSave(c)) cont.off("нет сохранения");
        item("Подключиться к другу", b -> c.setScreen(new MultiplayerScreen(this)));
        gap();
        item("Назад", b -> close());
    }

    /** Открыть уже идущую смену по сети (из паузы или после загрузки). */
    public static void openLan(MinecraftClient c, Screen back) {
        var s = c.getServer();
        if (s == null) return;
        if (!s.isRemote()) {
            int port = net.minecraft.util.NetworkUtils.findLocalPort();
            boolean ok = s.openToLan(net.minecraft.world.GameMode.ADVENTURE, false, port);
            Notify.show(ok ? "Смена открыта: порт " + port : "Не удалось открыть смену по сети");
        }
        c.setScreen(new LobbyScreen(back));
    }

    @SuppressWarnings("unused")
    static ServerInfo direct(String addr) {
        return ServerAddress.isValid(addr) ? new ServerInfo("Смена", addr, ServerInfo.ServerType.OTHER) : null;
    }
}
