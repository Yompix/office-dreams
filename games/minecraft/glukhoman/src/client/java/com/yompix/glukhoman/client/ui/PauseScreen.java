package com.yompix.glukhoman.client.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.MessageScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.text.Text;

/** Пауза (этап 5) вместо ванильной: продолжить, настройки мода и игры, пригласить друзей, кто в смене, выйти (с подтверждением). */
public class PauseScreen extends KitScreen {
    public PauseScreen() {
        super("Пауза", null, true);
    }

    @Override
    protected int rows() {
        return 7;
    }

    @Override
    protected void build() {
        var c = client;
        item("Продолжить", b -> c.setScreen(null));
        item("Настройки", b -> c.setScreen(new com.yompix.glukhoman.client.screen.SettingsScreen(this)));
        item("Настройки игры (управление, звук)", b -> c.setScreen(new net.minecraft.client.gui.screen.option.OptionsScreen(this, c.options)));
        boolean sp = c.isIntegratedServerRunning();
        boolean lan = sp && c.getServer() != null && c.getServer().isRemote();
        var host = item(lan ? "Смена открыта для друзей" : "Позвать друзей (по сети)", b -> CoopScreen.openLan(c, this));
        if (!sp) host.off("только у хозяина");
        else if (lan) host.off("порт " + c.getServer().getServerPort());
        item("Кто в смене", b -> c.setScreen(new LobbyScreen(this)));
        gap();
        item("Выйти в меню", b -> c.setScreen(new ConfirmScreen(this, true, "Выйти в меню?",
                sp ? "Смена сохранится. Друзей отключит." : "Вы отключитесь от смены.", "Выйти", "Остаться", () -> leave(c))));
    }

    @Override
    public void close() {
        client.setScreen(null);
    }

    /** Выход в главное меню — как в ванильной паузе (сохранить, отключиться, меню). */
    public static void leave(MinecraftClient c) {
        boolean sp = c.isInSingleplayer();
        if (c.world != null) c.world.disconnect();
        if (sp) c.disconnect(new MessageScreen(Text.translatable("menu.savingLevel")));
        else c.disconnect();
        c.setScreen(sp ? new TitleScreen() : new MultiplayerScreen(new TitleScreen()));
    }
}
