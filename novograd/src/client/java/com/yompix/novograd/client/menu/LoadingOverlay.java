package com.yompix.novograd.client.menu;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

/** Экран загрузки мира в стиле Novograd: фон города, логотип, «загружаем Новоград…» и советы. */
public final class LoadingOverlay {
    private LoadingOverlay() {}

    private static final String[] TIPS = {"Мама уже готовит завтрак…", "Светофоры переключаются…", "Учительница проверяет тетради…",
            "Таксисты выезжают на линию…", "Неон на фасадах прогревается…", "Тёма придумывает новый план…"};

    public static void draw(DrawContext ctx, int width, int height, String what) {
        var tr = MinecraftClient.getInstance().textRenderer;
        LifeMenuScreen.drawBackground(ctx, width, height);
        ctx.fill(0, 0, width, height, 0xB0000000);
        float t = (System.currentTimeMillis() % 100000) / 1000f;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(width / 2f, height / 2f - 40, 0);
        ctx.getMatrices().scale(3.5f, 3.5f, 1);
        ctx.drawText(tr, "НОВОГРАД", -tr.getWidth("НОВОГРАД") / 2, 0, 0xFFFFFFFF, true);
        ctx.getMatrices().pop();
        String w = what + "...".substring(0, (int) (t * 2) % 4);
        ctx.drawText(tr, w, width / 2 - tr.getWidth(what) / 2, height / 2 + 4, 0xFFFFC107, true);
        // бегущая неоновая полоска
        int bw = 160, bx = width / 2 - bw / 2, by = height / 2 + 20;
        ctx.fill(bx, by, bx + bw, by + 3, 0x40FFFFFF);
        int seg = (int) ((t * 90) % (bw + 40)) - 40;
        ctx.fill(bx + Math.max(0, seg), by, bx + MathHelper.clamp(seg + 40, 0, bw), by + 3, 0xFFFF2FB4);
        String tip = TIPS[(int) (t / 3) % TIPS.length];
        ctx.drawText(tr, tip, width / 2 - tr.getWidth(tip) / 2, height / 2 + 34, 0xAAFFFFFF, false);
        ctx.drawText(tr, "Yompix Games", width / 2 - tr.getWidth("Yompix Games") / 2, height - 16, 0xFF8A5CFF, false);
    }
}
