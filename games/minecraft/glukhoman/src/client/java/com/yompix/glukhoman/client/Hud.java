package com.yompix.glukhoman.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.yompix.glukhoman.Glukhoman;
import com.yompix.glukhoman.story.Texts;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.List;

import static com.yompix.glukhoman.client.GlukhomanClient.*;

/** Экран поверх игры: виньетка и пульс страха, цель, название главы, субтитры, затемнения, стрелка-подсказка, титры. */
public final class Hud {
    private Hud() {}

    static final Identifier VIGNETTE = Glukhoman.id("textures/gui/vignette.png");

    /** Ложный «вылет»: игра будто зависла и упала, потом «перезапускается» — с глючащим логотипом. */
    static void fakeCrash(DrawContext ctx, MinecraftClient c, int w, int h) {
        TextRenderer tr = c.textRenderer;
        int t = crashT;
        if (t < 40) {
            ctx.fill(0, 0, w, h, 0x50000000);
            int bw = 300, bh = 70, x = w / 2 - bw / 2, y = h / 2 - bh / 2;
            ctx.fill(x, y, x + bw, y + bh, 0xFFF0F0F0);
            ctx.fill(x, y, x + bw, y + 14, 0xFF2B5797);
            ctx.drawTextWithShadow(tr, "Глухомань", x + 4, y + 3, 0xFFFFFFFF);
            ctx.drawText(tr, "Программа не отвечает.", x + 10, y + 22, 0xFF000000, false);
            ctx.drawText(tr, "java.lang.IllegalStateException: в мире есть кто-то ещё", x + 10, y + 36, 0xFF600000, false);
            ctx.drawText(tr, "[ Закрыть ]", x + bw - 70, y + 54, 0xFF000000, false);
        } else if (t < 95) {
            ctx.fill(0, 0, w, h, 0xFF000000);
            if ((t / 8) % 2 == 0) ctx.drawTextWithShadow(tr, "_", 8, 8, 0xFFC0C0C0);
            if (t > 60) {
                int gl = (t * 7) % 9 - 4;
                ctx.getMatrices().push();
                ctx.getMatrices().translate(w / 2f + gl, h / 2f, 0);
                ctx.getMatrices().scale(4f, 4f, 1f);
                ctx.drawCenteredTextWithShadow(tr, t % 5 == 0 ? "ОТВЕТЬ МНЕ" : "ГЛУХОМАНЬ", 0, 0, t % 7 == 0 ? 0xFFB02020 : 0xFFD8D0C0);
                ctx.getMatrices().pop();
            }
        } else {
            int a = (int) ((1 - (t - 95) / 35f) * 255);
            ctx.fill(0, 0, w, h, Math.max(0, a) << 24);
        }
    }

    /**
     * Видоискатель: мигающий красный ● REC, батарея (садится — мигает), кассета и номер сюжета, ИК, уголки кадра,
     * дата 14.10.1996 и время по сюжетным часам. При пересмотре кассеты — ▶ PLAY и счётчик ленты вместо REC.
     */
    public static void rec(DrawContext ctx, TextRenderer tr, int w, int h, long time) {
        MinecraftClient c = MinecraftClient.getInstance();
        long tod = (c.world.getTimeOfDay() + 6000) % 24000;
        int hh = (int) (tod / 1000), mm = (int) (tod % 1000 * 60 / 1000), ss = (int) (time / 20 % 60);
        int m = 14, L = 16, col = 0xC0E8E8E8;
        ctx.getMatrices().push();
        if (!Cine.active) ctx.getMatrices().translate(HandCam.hudX, HandCam.hudY, 0);   // рамка качается вместе с камерой
        // уголки кадра
        ctx.fill(m, m, m + L, m + 1, col); ctx.fill(m, m, m + 1, m + L, col);
        ctx.fill(w - m - L, m, w - m, m + 1, col); ctx.fill(w - m - 1, m, w - m, m + L, col);
        ctx.fill(m, h - m - 1, m + L, h - m, col); ctx.fill(m, h - m - L, m + 1, h - m, col);
        ctx.fill(w - m - L, h - m - 1, w - m, h - m, col); ctx.fill(w - m - 1, h - m - L, w - m, h - m, col);
        if (Cine.active && Cine.replay) {
            ctx.drawTextWithShadow(tr, "▶ PLAY", m + 8, m + 6, 0xFFE8E8E8);
            ctx.drawTextWithShadow(tr, String.format("0:%02d:%02d", (int) (Cine.time() / 60) + 41, (int) Cine.time() % 60), w - m - 60, m + 6, 0xD0E0E0E0);
        } else {
            if ((time / 12) % 2 == 0) ctx.drawTextWithShadow(tr, "●", w - m - 52, m + 6, 0xFFE02424);
            ctx.drawTextWithShadow(tr, "REC", w - m - 40, m + 6, 0xE8E8E8E8);
            // батарея: корпус, контакт, 4 деления; меньше четверти — мигает
            float bat = Cine.active ? 0.62f : Viewfinder.battery();
            int bx = m + 8, by = m + 7;
            boolean low = bat < 0.25f;
            if (!low || (time / 8) % 2 == 0) {
                int bc = low ? 0xFFE04040 : 0xE0E8E8E8;
                ctx.fill(bx, by, bx + 20, by + 1, bc); ctx.fill(bx, by + 8, bx + 20, by + 9, bc);
                ctx.fill(bx, by, bx + 1, by + 9, bc); ctx.fill(bx + 19, by, bx + 20, by + 9, bc);
                ctx.fill(bx + 20, by + 3, bx + 22, by + 6, bc);
                int bars = (int) Math.ceil(bat * 4);
                for (int i = 0; i < bars; i++) ctx.fill(bx + 2 + i * 4, by + 2, bx + 5 + i * 4, by + 7, bc);
            }
            if (Viewfinder.ir && !Cine.active) ctx.drawTextWithShadow(tr, "ИК", bx + 28, by + 1, 0xFF9CE89C);
            if (!tape.isEmpty() && !Cine.active) ctx.drawTextWithShadow(tr, "▭ " + tape, m + 8, m + 20, 0xB0D8D8D0);
        }
        ctx.drawTextWithShadow(tr, String.format("14.10.1996  %02d:%02d:%02d", hh, mm, ss), w - m - 118, h - m - 14, 0xD0D8D8D0);
        ctx.drawTextWithShadow(tr, "SP", m + 8, h - m - 14, 0xA0D8D8D0);
        ctx.getMatrices().pop();
    }

    public static void render(DrawContext ctx, RenderTickCounter counter) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (!inWorld || c.player == null) return;
        if (Cine.active) {   // в кат-сцене — только рамка видоискателя, если сцена «в камере»
            if (Cine.vf) rec(ctx, c.textRenderer, ctx.getScaledWindowWidth(), ctx.getScaledWindowHeight(), c.world.getTime());
            return;
        }
        if (c.options.hudHidden) return;
        int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
        if (crashT >= 0) {
            fakeCrash(ctx, c, w, h);
            return;
        }
        TextRenderer tr = c.textRenderer;
        long time = c.world.getTime();
        float pt = counter.getTickDelta(true);

        // виньетка: всегда немного, сильнее при низком рассудке; на пульсе краснеет
        float fear = 1 - shownSanity / 100f;
        float a = 0.35f + fear * 0.6f;
        float red = 0;
        if (sanity < 40 && GConfig.get().heartbeat && !GConfig.get().noFlashing) {
            int period = 12 + (int) (sanity * 0.6f);
            red = (float) Math.max(0, Math.cos((time % period + pt) / period * Math.PI * 2)) * (40 - sanity) / 40f * 0.5f;
        }
        RenderSystem.enableBlend();
        ctx.setShaderColor(1f, 1f - red, 1f - red, Math.min(1f, a));
        ctx.drawTexture(VIGNETTE, 0, 0, w, h, 0, 0, 256, 256, 256, 256);
        ctx.setShaderColor(1f, 1f, 1f, 1f);
        if (joltT > 0) ctx.fill(0, 0, w, h, ((int) (joltT / 8f * 120) << 24) | 0xE8E8E8);

        if (Viewfinder.view()) rec(ctx, tr, w, h, time);

        // цель
        if (chapter > 0 && !objective.isEmpty() && creditsT < 0) {
            long since = time - objectiveChanged;
            int col = since < 80 ? 0xFFE8D9A8 : 0xB0A0A0A0;
            String head = title;
            int oy = Viewfinder.view() ? 44 : 8;
            ctx.drawTextWithShadow(tr, head, 8, oy, 0x80909090);
            for (var ln : tr.wrapLines(net.minecraft.text.Text.literal("▸ " + objective), Math.min(300, w / 2))) {
                ctx.drawTextWithShadow(tr, ln, 8, oy + 12, col);
                oy += 10;
            }
            if (lightOn) ctx.drawTextWithShadow(tr, "◉", w - 14, 8, 0xFFE0D070);
        }

        // название главы
        if (cardT > 0) {
            float t = 160 - cardT + pt;
            float alpha = Math.min(1, Math.min(t / 30f, (cardT - pt) / 40f));
            int al = Math.max(4, (int) (alpha * 255)) << 24;
            ctx.fill(0, h / 2 - 46, w, h / 2 + 40, ((int) (alpha * 140)) << 24);
            ctx.drawCenteredTextWithShadow(tr, cardA, w / 2, h / 2 - 36, al | 0x9A9A9A);
            ctx.getMatrices().push();
            ctx.getMatrices().translate(w / 2f, h / 2f - 18, 0);
            ctx.getMatrices().scale(3f, 3f, 1f);
            ctx.drawCenteredTextWithShadow(tr, cardB, 0, 0, al | 0xE6E0D0);
            ctx.getMatrices().pop();
            ctx.drawCenteredTextWithShadow(tr, cardC, w / 2, h / 2 + 20, al | 0xB0A898);
        }

        // субтитры
        int y = h - 72;
        for (int i = SUBS.size() - 1; i >= 0; i--) {
            String[] s = SUBS.get(i);
            String line = (s[0].isEmpty() ? "" : s[0] + ": ") + s[1];
            List<net.minecraft.text.OrderedText> lines = tr.wrapLines(net.minecraft.text.Text.literal(line), Math.min(360, w - 40));
            for (int k = lines.size() - 1; k >= 0; k--) {
                int lw = tr.getWidth(lines.get(k));
                ctx.fill(w / 2 - lw / 2 - 3, y - 2, w / 2 + lw / 2 + 3, y + 9, 0x99000000);
                ctx.drawTextWithShadow(tr, lines.get(k), w / 2 - lw / 2, y, s[0].startsWith("Голос") || s[0].equals("???") ? 0xFFD8B0B0 : 0xFFE0E0E0);
                y -= 12;
            }
            y -= 2;
        }

        // стрелка к цели
        if (hintT > 0 && hint != null) {
            Vec3d to = hint.subtract(c.player.getPos());
            double ang = Math.toDegrees(Math.atan2(-to.x, to.z)) - c.player.getYaw();
            ang = MathHelper.wrapDegrees(ang);
            String[] arrows = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
            String ar = arrows[(int) Math.floorMod(Math.round(ang / 45.0), 8)];
            int dist = (int) to.horizontalLength();
            ctx.getMatrices().push();
            ctx.getMatrices().translate(w / 2f, h / 2f - 40, 0);
            ctx.getMatrices().scale(2f, 2f, 1f);
            ctx.drawCenteredTextWithShadow(tr, ar, 0, 0, 0xFFE8D9A8);
            ctx.getMatrices().pop();
            ctx.drawCenteredTextWithShadow(tr, dist + " м", w / 2, h / 2 - 20, 0xFFB0A890);
        }

        // затемнение / белая вспышка
        if (fadeT > 0) {
            float k = fadeT > fadeTotal / 2 ? (fadeTotal - fadeT) / (fadeTotal / 2f) : fadeT / (fadeTotal / 2f);
            k = Math.min(1, k * 1.4f);
            ctx.fill(0, 0, w, h, ((int) (k * 255) << 24) | (fadeColor & 0xFFFFFF));
        }

        Jumpscare.render(ctx, w, h);

        // титры
        if (creditsT >= 0) {
            ctx.fill(0, 0, w, h, Math.min(230, creditsT * 3) << 24);
            List<String> lines = Texts.get().credits();
            int y0 = h - (int) ((creditsT + pt) * 0.5f);
            for (int i = 0; i < lines.size(); i++) {
                int ly = y0 + i * 16;
                if (ly < -10 || ly > h) continue;
                ctx.drawCenteredTextWithShadow(tr, lines.get(i), w / 2, ly, i == 0 ? 0xFFE8D9A8 : 0xFFC8C8C8);
            }
        }
    }
}
