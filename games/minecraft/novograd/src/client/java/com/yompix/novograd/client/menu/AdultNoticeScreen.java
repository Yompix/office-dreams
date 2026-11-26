package com.yompix.novograd.client.menu;

import com.yompix.novograd.client.NovogradConfig;
import com.yompix.novograd.client.screen.LifeScreen;
import com.yompix.novograd.client.ui.Ui;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

/** 0.9.6: при первом запуске — «Мод для взрослых 18+» и кнопка «OK» (показывается один раз на установку). */
public class AdultNoticeScreen extends LifeScreen {
    private final Screen next;

    public AdultNoticeScreen(Screen next) {
        super("18+");
        this.next = next;
    }

    @Override
    protected void draw(DrawContext ctx, float delta) {
        float a = openAnim();
        int cx = width / 2, cy = height / 2;
        Ui.big(ctx, textRenderer, "18+", cx, cy - 46, 4f, Ui.alpha(Ui.PINK, a), 1);
        Ui.big(ctx, textRenderer, "Мод для взрослых 18+", cx, cy + 2, 1.4f, Ui.alpha(Ui.WHITE, a), 1);
        Ui.small(ctx, textRenderer, "Насилие, преступность, азартные игры", cx, cy + 22, 0.85f, Ui.alpha(0xFFB9B7B1, a), 1);
        button(ctx, cx - 40, cy + 40, 80, 18, "OK", Ui.PINK, true, this::ok);
    }

    private void ok() {
        NovogradConfig.get().adultOk = true;
        NovogradConfig.save();
        if (client != null) client.setScreen(next);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE) {
            ok();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }
}
