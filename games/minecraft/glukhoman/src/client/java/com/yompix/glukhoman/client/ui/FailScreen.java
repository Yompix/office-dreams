package com.yompix.glukhoman.client.ui;

import com.yompix.glukhoman.client.menu.MenuClock;
import net.minecraft.client.gui.DrawContext;

/**
 * Экран провала (этап 5) вместо ванильного «Вы погибли»: в «Порожняке» смерть — «плёнка оборвалась» (Night.grab).
 * Кнопки появляются через 1.5 с (как у ванильной — чтобы не нажать случайно); «Перемотать» — возрождение.
 */
public class FailScreen extends KitScreen {
    public FailScreen() {
        super("Плёнка оборвалась", null, true);
        sub = "Последняя запись осталась на перроне.";
    }

    @Override
    protected int rows() {
        return 2;
    }

    @Override
    protected void build() {
        var c = client;
        item("Перемотать", b -> {
            if (c.player != null) c.player.requestRespawn();
            c.setScreen(null);
        });
        item("Выйти в меню", b -> c.setScreen(new ConfirmScreen(this, true, "Выйти в меню?", "Смена сохранится.", "Выйти", "Остаться",
                () -> PauseScreen.leave(c))));
        for (var el : children()) if (el instanceof Kit.Item it) it.active = false;
    }

    @Override
    protected void extra(DrawContext ctx, int mx, int my, double t) {
        if (t > 1.5) for (var el : children()) if (el instanceof Kit.Item it && !it.active && it.note.isEmpty()) it.active = true;
        // «обрыв ленты»: полосы шума тают за 1 с
        double k = Math.max(0, 1 - t);
        if (k > 0) for (int i = 0; i < 40 * k; i++) {
            int y = (int) ((MenuClock.now() * 977 + i * 131) % height);
            ctx.fill(0, y, width, y + 2, Kit.alpha(0xFFFFFF, (float) (0.25 * k)));
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
