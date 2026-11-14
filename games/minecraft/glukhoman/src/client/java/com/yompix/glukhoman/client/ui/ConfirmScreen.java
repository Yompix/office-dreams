package com.yompix.glukhoman.client.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

/** Подтверждение «да/нет» (этап 5). По умолчанию выделено «нет»; Esc — «нет». */
public class ConfirmScreen extends KitScreen {
    final String yes, no;
    final Runnable onYes;

    public ConfirmScreen(Screen parent, boolean inGame, String question, String detail, String yes, String no, Runnable onYes) {
        super(question, parent, inGame);
        this.sub = detail;
        this.yes = yes;
        this.no = no;
        this.onYes = onYes;
    }

    @Override
    protected int rows() {
        return 2;
    }

    @Override
    protected void build() {
        var n = item(no, b -> close());
        item(yes, b -> onYes.run());
        setInitialFocus(n);
    }
}
