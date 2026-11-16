package com.yompix.shaderlite;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

import java.util.List;

/** Простой экран настроек (открывается из Mod Menu: Моды → ShaderLite → кнопка настроек). */
public final class ConfigScreen extends Screen {
    private final Screen parent;

    public ConfigScreen(Screen parent) {
        super(Text.translatable("shaderlite_adaptive.title"));
        this.parent = parent;
    }

    @Override protected void init() {
        Config c = AdaptiveQuality.CONFIG;
        int x = width / 2 - 155, w = 310, y = height / 6, h = 20, gap = 24;
        addDrawableChild(CyclingButtonWidget.onOffBuilder(c.enabled)
                .build(x, y, w, h, Text.translatable("shaderlite_adaptive.enabled"), (b, v) -> c.enabled = v));
        y += gap;
        addDrawableChild(CyclingButtonWidget.<Integer>builder(v -> Text.literal(v + " FPS"))
                .values(List.of(30, 40, 45, 50, 60, 75, 90, 120)).initially(nearest(c.targetFps, List.of(30, 40, 45, 50, 60, 75, 90, 120)))
                .build(x, y, w, h, Text.translatable("shaderlite_adaptive.target"), (b, v) -> c.targetFps = v));
        y += gap;
        addDrawableChild(CyclingButtonWidget.<Integer>builder(v -> Text.translatable("shaderlite_adaptive.chunks", v))
                .values(List.of(2, 3, 4, 5, 6, 8, 10, 12)).initially(nearest(c.minViewDistance, List.of(2, 3, 4, 5, 6, 8, 10, 12)))
                .build(x, y, w, h, Text.translatable("shaderlite_adaptive.minview"), (b, v) -> c.minViewDistance = v));
        y += gap;
        addDrawableChild(CyclingButtonWidget.onOffBuilder(c.adjustClouds)
                .build(x, y, 152, h, Text.translatable("shaderlite_adaptive.clouds"), (b, v) -> c.adjustClouds = v));
        addDrawableChild(CyclingButtonWidget.onOffBuilder(c.adjustParticles)
                .build(x + 158, y, 152, h, Text.translatable("shaderlite_adaptive.particles"), (b, v) -> c.adjustParticles = v));
        y += gap;
        addDrawableChild(CyclingButtonWidget.onOffBuilder(c.adjustEntityDistance)
                .build(x, y, 152, h, Text.translatable("shaderlite_adaptive.entities"), (b, v) -> c.adjustEntityDistance = v));
        addDrawableChild(CyclingButtonWidget.onOffBuilder(c.showMessages)
                .build(x + 158, y, 152, h, Text.translatable("shaderlite_adaptive.messages"), (b, v) -> c.showMessages = v));
        y += gap * 2;
        addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, b -> close()).dimensions(width / 2 - 100, y, 200, h).build());
    }

    private static int nearest(int v, List<Integer> list) {
        int best = list.get(0);
        for (int x : list) if (Math.abs(x - v) < Math.abs(best - v)) best = x;
        return best;
    }

    @Override public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, 15, 0xFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("shaderlite_adaptive.hint"), width / 2, height / 6 - 14, 0xA0A0A0);
    }

    @Override public void close() {
        AdaptiveQuality.CONFIG.save();
        AdaptiveQuality.onConfigChanged();
        client.setScreen(parent);
    }
}
