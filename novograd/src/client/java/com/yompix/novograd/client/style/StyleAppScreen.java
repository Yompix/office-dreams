package com.yompix.novograd.client.style;

import com.yompix.novograd.client.NovogradClient;
import com.yompix.novograd.client.screen.LifeScreen;
import com.yompix.novograd.client.ui.Ui;
import com.yompix.novograd.life.LifeData;
import com.yompix.novograd.life.StyleCatalog;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.NbtCompound;

/** Телефон → «Стиль»: герой, сила и выносливость из зала, гардероб и где что купить. */
public class StyleAppScreen extends LifeScreen {
    private boolean locked;
    private long lockedAt;
    private float yaw = 200;

    public StyleAppScreen(boolean locked) {
        super("Стиль");
        if (locked) locked();
    }

    public void locked() {
        locked = true;
        lockedAt = System.currentTimeMillis();
    }

    @Override
    protected void draw(DrawContext ctx, float delta) {
        float a = openAnim();
        LifeData d = NovogradClient.data;
        int top = 10 + (int) ((1 - a) * -20);
        title(ctx, "Стиль", 12, top, Ui.PINK);
        Ui.small(ctx, textRenderer, "внешность · гардероб · спортзал", 14, top + 28, 0.8f, 0xFFD8D2EA, 0);
        // герой
        int sw = (int) (width * 0.32f), sy = top + 40, sh = height - sy - 24;
        ctx.fill(10, sy, 10 + sw, sy + sh, 0x90060410);
        ctx.fill(10, sy, 10 + sw, sy + 1, Ui.alpha(Ui.PINK, 0.6f));
        yaw += 0.4f;
        ctx.enableScissor(10, sy, 10 + sw, sy + sh);
        StyleShopScreen.hero(ctx, 10 + sw / 2f, sy + sh - 14, Math.min(sh * 0.4f, sw * 0.7f), yaw);
        ctx.disableScissor();
        // навыки
        int px = 20 + sw, pw = width - px - 10, py = sy;
        Ui.panel(ctx, px, py, pw, 92, Ui.alpha(Ui.GOLD, a));
        Ui.big(ctx, textRenderer, "СПОРТЗАЛ", px + 8, py + 7, 1.2f, Ui.GOLD, 0);
        Ui.small(ctx, textRenderer, StyleCatalog.GYM_NAME + " · до +" + StyleCatalog.GYM_DAILY + " в день к каждому навыку", px + 8 + textRenderer.getWidth("СПОРТЗАЛ") * 1.2f + 8, py + 9, 0.75f, 0xFFB8B2CC, 0);
        boolean today = d.gymDay == (mc().world == null ? -1 : mc().world.getTimeOfDay() / 24000L + 1);
        stat(ctx, px + 8, py + 26, pw - 16, "СИЛА", d.strength, today ? d.gymStrToday : 0, Ui.RED,
                "Удар кулаком и битой +" + String.format("%.1f", d.strength * 0.03) + " · плечи шире");
        stat(ctx, px + 8, py + 58, pw - 16, "ВЫНОСЛИВОСТЬ", d.stamina, today ? d.gymStaToday : 0, Ui.CYAN,
                "Бег +" + Math.round(d.stamina * 0.12) + "% · падение без урона +" + String.format("%.1f", d.stamina * 0.03) + " бл.");
        // гардероб
        int gy = py + 100;
        Ui.panel(ctx, px, gy, pw, height - gy - 24, Ui.alpha(Ui.PINK, a));
        Ui.big(ctx, textRenderer, "ГАРДЕРОБ", px + 8, gy + 7, 1.2f, Ui.WHITE, 0);
        Ui.small(ctx, textRenderer, "вещей: " + d.wardrobe.size(), px + pw - 8, gy + 9, 0.8f, 0xFFB8B2CC, 2);
        button(ctx, px + 8, gy + 24, Math.min(160, pw - 16), 18, "ОТКРЫТЬ ГАРДЕРОБ", Ui.PINK, true, () -> StyleClient.send("wardrobe", new NbtCompound()));
        int ty = gy + 48;
        if (locked && System.currentTimeMillis() - lockedAt < 8000) {
            ty += wrap(ctx, "Переодеться можно в своём убежище или в магазине одежды. Убежища продаются по городу — ищи дверь с табличкой «продаётся».",
                    px + 8, ty, pw - 16, 0xFFFFC428) + 4;
        }
        String[] where = {"👕 " + StyleCatalog.CHEAP_NAME + " — уличная одежда, дёшево", "💎 " + StyleCatalog.LUX_NAME + " — люкс для тех, кто поднялся",
                "✂ " + StyleCatalog.BARBER_NAME + " — стрижки, цвет, бороды", "💪 " + StyleCatalog.GYM_NAME + " — сила и выносливость"};
        for (String s : where) {
            if (ty > height - 34) break;
            Ui.small(ctx, textRenderer, s, px + 8, ty, 0.8f, 0xFFDCD8E8, 0);
            ty += 10;
        }
        // низ
        ctx.fill(0, height - 18, width, height, 0xE0050309);
        ctx.fill(0, height - 19, width, height - 18, Ui.alpha(Ui.PINK, 0.8f));
        Ui.small(ctx, textRenderer, "Одежду покупают в магазинах, переодеваются бесплатно — дома и в магазине", 10, height - 13, 0.8f, 0xB0FFFFFF, 0);
        String back = "‹ НАЗАД";
        int bw = textRenderer.getWidth(back) + 16;
        Ui.slant(ctx, width - bw - 8, height - 16, bw, 14, 4, hover(width - bw - 8, height - 17, bw, 17) ? Ui.PINK : 0x60FFFFFF);
        ctx.drawText(textRenderer, back, width - bw, height - 13, 0xFFFFFFFF, false);
        area(width - bw - 8, height - 17, bw, 17, this::close);
    }

    private void stat(DrawContext ctx, int x, int y, int w, String name, int value, int today, int color, String effect) {
        ctx.drawText(textRenderer, name, x, y, Ui.WHITE, true);
        String v = value + " / " + StyleCatalog.STAT_MAX;
        ctx.drawText(textRenderer, v, x + w - textRenderer.getWidth(v), y, color, true);
        Ui.bar(ctx, x, y + 11, w, 5, value / (float) StyleCatalog.STAT_MAX, color);
        // засечки дневного лимита
        Ui.small(ctx, textRenderer, effect, x, y + 19, 0.72f, 0xFFB8B2CC, 0);
        String t = "сегодня +" + today + "/" + StyleCatalog.GYM_DAILY;
        Ui.small(ctx, textRenderer, t, x + w, y + 19, 0.72f, today >= StyleCatalog.GYM_DAILY ? Ui.GOLD : 0xFFB8B2CC, 2);
    }
}
