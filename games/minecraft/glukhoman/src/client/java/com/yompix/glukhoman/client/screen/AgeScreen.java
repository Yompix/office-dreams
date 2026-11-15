package com.yompix.glukhoman.client.screen;

import com.yompix.glukhoman.client.GConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Предупреждение при первом запуске: 18+, насилие, кровь, расчленёнка. */
public class AgeScreen extends Screen {
    private final Screen parent;

    public AgeScreen(Screen parent) {
        super(Text.literal("18+"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addDrawableChild(ButtonWidget.builder(Text.literal("Мне есть 18, продолжить"), b -> {
            GConfig.get().ageAck = true;
            GConfig.get().save();
            client.setScreen(parent);
        }).dimensions(width / 2 - 100, height / 2 + 40, 200, 20).build());
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float d) {
        ctx.fill(0, 0, width, height, 0xFF080606);
        ctx.getMatrices().push();
        ctx.getMatrices().translate(width / 2f, height / 2f - 60, 0);
        ctx.getMatrices().scale(4f, 4f, 1f);
        ctx.drawCenteredTextWithShadow(textRenderer, "18+", 0, 0, 0xFFC02020);
        ctx.getMatrices().pop();
        ctx.drawCenteredTextWithShadow(textRenderer, "«Глухомань» — хоррор для взрослых: насилие, кровь, расчленёнка, скримеры.", width / 2, height / 2 - 14, 0xFFD0D0D0);
        ctx.drawCenteredTextWithShadow(textRenderer, "Жестокость можно уменьшить: пауза — «Глухомань» — «Жестокость».", width / 2, height / 2 + 2, 0xFF909090);
        ctx.drawCenteredTextWithShadow(textRenderer, "Не связано с Mojang и Microsoft.", width / 2, height - 16, 0xFF555555);
        super.render(ctx, mx, my, d);
    }

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float d) {
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
