package com.yompix.glukhoman.client.menu;

import com.yompix.glukhoman.story.Texts;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Титры: медленно ползут вверх на чёрном. Любая клавиша — назад. */
public class CreditsMenuScreen extends Screen {
    final Screen parent;
    final double t0 = MenuClock.now();

    public CreditsMenuScreen(Screen parent) {
        super(Text.literal("Титры"));
        this.parent = parent;
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        ctx.fill(0, 0, width, height, 0xFF050506);
        var lines = Texts.get().credits();
        int y0 = height - (int) ((MenuClock.now() - t0) * 18);
        for (int i = 0; i < lines.size(); i++) {
            int y = y0 + i * 16;
            if (y > -10 && y < height) ctx.drawCenteredTextWithShadow(textRenderer, lines.get(i), width / 2, y, i == 0 ? 0xFFE8D9A8 : 0xFFC8C8C8);
        }
        if (y0 + lines.size() * 16 < -20) close();
        ctx.drawTextWithShadow(textRenderer, "Esc — назад", 8, height - 14, 0x60A0A0A0);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        close();
        return true;
    }

    @Override
    public boolean mouseClicked(double x, double y, int b) {
        close();
        return true;
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
    }
}
