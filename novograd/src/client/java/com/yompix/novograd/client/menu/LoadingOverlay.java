package com.yompix.novograd.client.menu;

import com.yompix.novograd.client.ui.Ui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

/** Экран загрузки мира: комикс-арт Новограда, логотип, бегущая неоновая полоса и советы — как загрузочный экран криминальной игры. */
public final class LoadingOverlay {
    private LoadingOverlay() {}

    private static final String[] TIPS = {"Светофоры переключаются…", "Таксисты выезжают на линию…", "Неон на фасадах прогревается…",
            "Полиция заступает на ночную смену…", "Чёрные Ящеры делят район…", "В гараже прогревают моторы…"};

    public static void draw(DrawContext ctx, int width, int height, String what) {
        var tr = MinecraftClient.getInstance().textRenderer;
        float t = (System.currentTimeMillis() % 1_000_000L) / 1000f;
        ctx.fill(0, 0, width, height, 0xFF05030A);
        // большой арт: панель сменяется каждые 5 с и медленно наезжает
        int i = (int) (t / 5) % 4;
        float k = (t % 5) / 5f;
        Ui.cover(ctx, Ui.panel(i), 0, 0, width, height, Ui.PANEL_SIZE[0], Ui.PANEL_SIZE[1], 1.05f + k * 0.06f, 0.3f + k * 0.4f, 0.55f);
        ctx.fillGradient(0, 0, width, height, 0x60000000, 0xE0000000);
        Ui.letterbox(ctx, width, height, 1);
        // логотип снизу слева, как на загрузочных экранах
        float lw = Math.min(width * 0.45f, 240);
        int lx = 24, ly = height - (int) (height * 0.1f) - 70;
        int lh = Ui.logo(ctx, lx, ly, lw, 1);
        Ui.subtitle(ctx, lx + 8, ly + lh - 2, lw * 0.55f, 1);
        // справа: что грузится и полоса
        String w = what.toUpperCase() + "...".substring(0, (int) (t * 2) % 4);
        int rx = width - 24;
        int by = height - (int) (height * 0.1f) - 26;
        Ui.big(ctx, tr, w, rx - tr.getWidth("...") * 1.3f, by - 16, 1.3f, Ui.WHITE, 2);
        int bw = Math.min(160, width / 3), bx = rx - bw;
        ctx.fill(bx, by, rx, by + 3, 0x40FFFFFF);
        int seg = (int) ((t * 110) % (bw + 50)) - 50;
        ctx.fill(bx + Math.max(0, seg), by, bx + MathHelper.clamp(seg + 50, 0, bw), by + 3, Ui.PINK);
        String tip = TIPS[(int) (t / 3) % TIPS.length];
        ctx.drawText(tr, tip, rx - tr.getWidth(tip), by + 8, 0xAAFFFFFF, false);
        ctx.drawText(tr, "YOMPIX GAMES", width - tr.getWidth("YOMPIX GAMES") - 8, (int) (height * 0.1f) - 12, 0x88FFFFFF, false);
        Ui.grain(ctx, width, height, 0.3f);
    }
}
