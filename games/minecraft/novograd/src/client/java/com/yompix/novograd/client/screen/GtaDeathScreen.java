package com.yompix.novograd.client.screen;

import com.yompix.novograd.client.ui.Ui;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

/**
 * Экран смерти как в GTA: тёмный кадр, полоса через экран и крупное «ПОТРАЧЕНО». Кнопки и их логика — ванильные
 * (возрождение — сервер Новограда везёт в больницу и выставляет счёт, Wanted.respawned).
 */
public class GtaDeathScreen extends DeathScreen {
    @Nullable private final Text msg;
    private int ticks;

    public GtaDeathScreen(@Nullable Text message, boolean hardcore) {
        super(message, hardcore);
        this.msg = message;
    }

    @Override
    public void tick() {
        super.tick();
        ticks++;
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        float k = MathHelper.clamp((ticks + delta) / 30f, 0, 1);
        ctx.fillGradient(0, 0, width, height, Ui.alpha(0xC0101010, k), Ui.alpha(0xE0050505, k));
        int band = height / 4 - 34; // кнопки ванильного экрана — с height/4 + 72, надписи держим выше них
        ctx.fill(0, band, width, band + 52, Ui.alpha(0xB0000000, k));
        ctx.fill(0, band, width, band + 1, Ui.alpha(Ui.PINK, k));
        ctx.fill(0, band + 51, width, band + 52, Ui.alpha(Ui.PINK, k));
        float s = 4.2f - 0.6f * Ui.easeOut(k);
        Ui.big(ctx, textRenderer, "ПОТРАЧЕНО", width / 2f, band + 12, s, Ui.alpha(0xFFD42A3A, k), 1);
        if (msg != null) Ui.small(ctx, textRenderer, msg.getString(), width / 2f, band + 60, 1f, Ui.alpha(0xFFE0E0E0, k), 1);
        Ui.small(ctx, textRenderer, "Половина грязных денег пропадёт. Врачи выставят счёт.", width / 2f, band + 74, 0.8f, Ui.alpha(Ui.DIM, k), 1);
        for (var c : children()) if (c instanceof Drawable d) d.render(ctx, mouseX, mouseY, delta);
    }
}
