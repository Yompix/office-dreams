package com.yompix.glukhoman.client.ui;

import com.yompix.glukhoman.client.menu.MenuClock;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Основа экранов мода (этап 5): подложка, заголовок, колонка пунктов, уведомления поверх, Esc — назад. */
public abstract class KitScreen extends Screen {
    protected final Screen parent;
    protected final boolean inGame;
    protected final double t0 = MenuClock.now();
    protected int order, colY, colW = 220;
    protected String sub = "";
    protected boolean showTitle = true;

    protected KitScreen(String title, Screen parent, boolean inGame) {
        super(Text.literal(title));
        this.parent = parent;
        this.inGame = inGame;
    }

    @Override
    protected void init() {
        order = 0;
        colY = Math.max(56, height / 2 - rows() * 9 - 10);
        build();
    }

    /** Сколько строк в колонке (для центрирования по высоте). */
    protected int rows() {
        return 6;
    }

    protected abstract void build();

    protected Kit.Item item(String label, ButtonWidget.PressAction a) {
        Kit.Item it = new Kit.Item(width / 2 - colW / 2, colY, colW, label, order++, a);
        colY += 18;
        return addDrawableChild(it);
    }

    protected void gap() {
        colY += 8;
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        double t = MenuClock.now() - t0;
        float a = (float) Math.min(1, t / 0.25);
        Kit.backdrop(ctx, width, height, MenuClock.now(), inGame);
        if (showTitle) Kit.title(ctx, getTitle().getString(), width / 2, Math.max(14, colY0() - 46), a);
        if (!sub.isEmpty()) ctx.drawCenteredTextWithShadow(textRenderer, sub, width / 2, Math.max(14, colY0() - 46) + 28, Kit.alpha(0xA8A090, a));
        for (var d : children()) if (d instanceof net.minecraft.client.gui.Drawable dr) dr.render(ctx, mx, my, delta);
        extra(ctx, mx, my, t);
        Notify.render(ctx, textRenderer, width, height);
    }

    protected int colY0() {
        return Math.max(56, height / 2 - rows() * 9 - 10);
    }

    protected void extra(DrawContext ctx, int mx, int my, double t) {
    }

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return inGame;
    }
}
