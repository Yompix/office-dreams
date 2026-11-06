package com.yompix.novograd.client;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/** Значки мест на карте и радаре, как в GTA: символ на цветном квадратике по типу места. */
public final class PlaceIcons {
    private PlaceIcons() {}

    /** {символ, цвет} по имени места; null — не показывать значком. */
    public static String[] of(String id) {
        String s = id.toLowerCase();
        if (s.startsWith("pulse_maps")) return new String[]{"?", "FF8F00"}; // ларёк «Карты коллекций»
        if (s.startsWith("pulse_fight")) return new String[]{"✊", "8E1B1B"}; // подпольные бои «Подвал»
        if (s.startsWith("realty_garage")) return new String[]{"P", "1E88E5"}; // свой гараж (world/Garage)
        if (s.startsWith("realty_own")) return new String[]{"⌂", "2E7D32"}; // своё жильё
        if (s.startsWith("realty")) return new String[]{"⌂", "EF6C00"}; // продаётся / сдаётся
        if (s.contains("bank") || s.contains("atm")) return new String[]{"$", "2E7D32"};
        if (s.contains("gun")) return new String[]{"⚔", "C62828"};
        if (s.contains("garage")) return new String[]{"🔧", "F9A825"};
        if (s.contains("casino")) return new String[]{"♣", "AD1457"};
        if (s.contains("hospital")) return new String[]{"+", "E53935"};
        if (s.contains("police")) return new String[]{"★", "1565C0"};
        if (s.contains("shop_clothes") || s.contains("boutique")) return new String[]{"T", "8E24AA"};
        if (s.contains("barber")) return new String[]{"✂", "6D4C41"};
        if (s.contains("gym")) return new String[]{"Ж", "455A64"};
        if (s.contains("bar") || s.contains("club")) return new String[]{"♪", "D81B60"};
        if (s.contains("pizza") || s.contains("burger")) return new String[]{"Е", "EF6C00"};
        if (s.contains("shop")) return new String[]{"М", "00897B"};
        if (s.contains("dealer")) return new String[]{"А", "3949AB"};
        if (s.contains("airfield")) return new String[]{"✈", "546E7A"};
        if (s.contains("marina") || s.contains("dock")) return new String[]{"⚓", "0277BD"};
        return null;
    }

    public static void draw(DrawContext ctx, TextRenderer tr, String[] ic, int x, int y) {
        int col = 0xFF000000 | Integer.parseInt(ic[1], 16);
        ctx.fill(x - 5, y - 5, x + 6, y + 6, 0xFF000000);
        ctx.fill(x - 4, y - 4, x + 5, y + 5, col);
        ctx.drawText(tr, ic[0], x - tr.getWidth(ic[0]) / 2 + 1, y - 3, 0xFFFFFFFF, false);
    }
}
