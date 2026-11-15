package com.yompix.glukhoman.client.screen;

import com.yompix.glukhoman.Glukhoman;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.math.random.Random;

import java.util.List;

/** Пересмотр «звуковой» записи на маленьком экранчике камеры: синий экран, PLAY, строки по очереди, шум плёнки. */
public class PlaybackScreen extends Screen {
    final String title;
    final List<String> lines;
    int t;
    final Random rnd = Random.create();

    public PlaybackScreen(String title, List<String> lines) {
        super(Text.literal("Пересмотр"));
        this.title = title;
        this.lines = lines;
    }

    @Override
    public void tick() {
        t++;
        if (t == 2) {
            var e = Registries.SOUND_EVENT.get(Glukhoman.id("radio_static"));
            if (e != null) MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(e, 1f, 0.35f));
        }
        if (t > 60 + lines.size() * 70 + 60) close();
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        ctx.fill(0, 0, width, height, 0xFF0A0C10);
        int bw = Math.min(420, width - 40), bh = Math.min(240, height - 40), x0 = width / 2 - bw / 2, y0 = height / 2 - bh / 2;
        ctx.fill(x0, y0, x0 + bw, y0 + bh, t < 30 ? 0xFF1A2A9A : 0xFF101418);
        for (int i = 0; i < 70; i++) {   // зерно и полосы
            int yy = y0 + rnd.nextInt(bh), xx = x0 + rnd.nextInt(bw);
            ctx.fill(xx, yy, Math.min(x0 + bw, xx + 6 + rnd.nextInt(30)), yy + 1, 0x30FFFFFF);
        }
        int band = y0 + bh - 10 - (t * 3) % 40;
        ctx.fill(x0, band, x0 + bw, band + 3, 0x40C8C8D8);
        ctx.drawTextWithShadow(textRenderer, "▶ PLAY", x0 + 10, y0 + 8, 0xFFE8E8E8);
        ctx.drawTextWithShadow(textRenderer, title, x0 + 10, y0 + 22, 0xFFB8B8B8);
        int shown = Math.max(0, (t - 40) / 70 + 1);
        int y = y0 + 48;
        for (int i = 0; i < Math.min(shown, lines.size()); i++) {
            for (var l : textRenderer.wrapLines(Text.literal(lines.get(i)), bw - 24)) {
                ctx.drawTextWithShadow(textRenderer, l, x0 + 12, y, lines.get(i).contains("[тишина") ? 0xFFD07070 : 0xFFE0E0E0);
                y += 11;
            }
            y += 4;
        }
        ctx.drawTextWithShadow(textRenderer, "Esc — стоп", x0 + bw - 60, y0 + bh - 12, 0x80A0A0A0);
    }

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
