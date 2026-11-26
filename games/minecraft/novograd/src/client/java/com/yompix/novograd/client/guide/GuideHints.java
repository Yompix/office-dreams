package com.yompix.novograd.client.guide;

import com.yompix.novograd.client.ui.Quiet;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

/** 0.9.6: подсказка один раз за мир, когда игрок впервые подходит к месту, где есть чем заняться (маленькая, слева сверху). */
public final class GuideHints {
    private GuideHints() {}

    private static final String[][] SPOTS = {
            {"shop_clothes", "Шмот-Базар: одежда. Внутри — редактор внешности"}, {"shop_boutique", "«Бархат»: дорогая одежда"},
            {"shop_barber", "Цирюльня: стрижка, цвет, борода"}, {"shop_gym", "Зал: сила и выносливость, вес видно на теле"},
            {"casino_dt", "Казино: 11 игр"}, {"riv_casino", "«Золотая корона»: казино Ривьеры"}, {"bank_tower", "Банк: карта, наличные, вклад"},
            {"chop_shop", "Разборка: сдать угнанную машину"}, {"race_start", "Старт гонки: встань на метку в машине"},
            {"club_neon", "«Неон»: вечером — диджей и танцы"}, {"metro_dt", "Метро: билет $5 в автомате"}, {"cafe_dt", "Кафе: ПКМ по стойке — меню"},
            {"market_suburb", "Супермаркет: ПКМ по полке — купить"}, {"riv_marina", "Яхт-клуб: катера и гонки"}, {"airfield", "Аэродром: самолёт и вертолёт"},
    };

    public static void init() {
        if (System.getProperty("novograd.autotest") != null) return;
        ClientTickEvents.END_CLIENT_TICK.register(c -> {
            if (c.player == null || c.world == null || c.player.age % 20 != 0 || c.currentScreen != null) return;
            for (String[] s : SPOTS) {
                double[] v = com.yompix.novograd.world.Island.place(s[0]);
                if (v == null) continue;
                double dx = c.player.getX() - v[0], dz = c.player.getZ() - v[2];
                if (dx * dx + dz * dz < 18 * 18) {
                    Quiet.hint("spot:" + s[0], s[1]);
                    return;
                }
            }
            if (c.player.age == 20 * 60) Quiet.hint("guide", "Телефон (G) → «Гид»: что можно делать");
        });
    }
}
