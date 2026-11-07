package com.yompix.novograd.client.screen;

import com.yompix.novograd.client.NovogradClient;
import com.yompix.novograd.client.OutdoorClient;
import com.yompix.novograd.client.ui.Ui;
import com.yompix.novograd.gta.Fishing;
import com.yompix.novograd.gta.Hunting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * Телефон → «Природа»: календарь острова (время года, праздники), охота (лицензия, где можно, цены скупщика),
 * рыбалка (рекорды, турнир на озере Ясное). У меток лавок — то же окно с кнопками «купить» и «продать всё».
 */
public class OutdoorScreen extends LifeScreen {
    private String tab;
    private final String shop;

    public OutdoorScreen(String shop) {
        super("Природа");
        this.shop = shop == null ? "" : shop;
        tab = this.shop.equals("hunt") ? "hunt" : this.shop.equals("fish") ? "fish" : "calendar";
        NovogradClient.send("out_state", new NbtCompound());
    }

    private static final int GREEN = 0xFF7CFF8A;

    @Override
    protected void draw(DrawContext ctx, float delta) {
        float a = openAnim();
        int w = Math.min(440, width - 24), h = Math.min(280, height - 24);
        int x = width / 2 - w / 2, y = height / 2 - h / 2 + (int) ((1 - a) * 16);
        int accent = tab.equals("hunt") ? 0xFFFF9F1C : tab.equals("fish") ? 0xFF26C6DA : 0xFF8EC5FF;
        Ui.panel(ctx, x, y, w, h, Ui.alpha(accent, a));
        String head = shop.equals("hunt") ? "ОХОТНИЧИЙ ДОМИК «БЕРЛОГА»" : shop.equals("fish") ? "РЫБНАЯ ЛАВКА" : "ПРИРОДА";
        Ui.big(ctx, textRenderer, head, x + 10, y + 8, 1.4f, accent, 0);
        NbtCompound st = OutdoorClient.state;
        String money = "$" + (st.contains("money") ? st.getInt("money") : NovogradClient.data.money);
        Ui.small(ctx, textRenderer, money, x + w - 10, y + 11, 1f, GREEN, 2);
        int cy = y + 28;
        String[][] tabs = {{"calendar", "Календарь"}, {"hunt", "Охота"}, {"fish", "Рыбалка"}};
        for (int i = 0; i < tabs.length; i++) {
            String id = tabs[i][0];
            button(ctx, x + 10 + i * 84, cy, 78, 14, tabs[i][1], tab.equals(id) ? accentOf(id) : 0xFF37474F, true, () -> tab = id);
        }
        cy += 22;
        switch (tab) {
            case "hunt" -> hunt(ctx, x, cy, w, st.getCompound("hunt"));
            case "fish" -> fish(ctx, x, cy, w, st.getCompound("fish"));
            default -> calendar(ctx, x, cy, w);
        }
        center(ctx, "Esc — закрыть", x + w / 2, y + h - 12, 0xFF607D8B);
    }

    private static int accentOf(String tab) {
        return tab.equals("hunt") ? 0xFFB86A10 : tab.equals("fish") ? 0xFF1A8C9A : 0xFF3A6EA8;
    }

    // ------------------------------------------------------------------ календарь
    private void calendar(DrawContext ctx, int x, int cy, int w) {
        NbtCompound s = OutdoorClient.season;
        if (s.isEmpty()) {
            center(ctx, "Календарь острова загружается…", x + w / 2, cy + 20, 0xFFB0BEC5);
            return;
        }
        String[] names = {"SUMMER", "AUTUMN", "WINTER", "SPRING"};
        String[] titles = {"Лето", "Осень", "Зима", "Весна"};
        int[] colors = {0xFFFFC428, 0xFFFF8A1E, 0xFF8EC5FF, 0xFF7CFF8A};
        int cw = (w - 20 - 18) / 4;
        for (int i = 0; i < 4; i++) {
            boolean now = names[i].equals(s.getString("season"));
            int bx = x + 10 + i * (cw + 6);
            ctx.fill(bx, cy, bx + cw, cy + 34, now ? Ui.alpha(colors[i], 0.35f) : 0x40202830);
            if (now) ctx.fill(bx, cy, bx + cw, cy + 2, colors[i]);
            Ui.big(ctx, textRenderer, titles[i], bx + cw / 2f, cy + 8, now ? 1.3f : 1f, now ? 0xFFFFFFFF : 0xFF8A8598, 1);
            if (now) Ui.small(ctx, textRenderer, "день " + (s.getInt("dayIn") + 1) + " из " + s.getInt("len"), bx + cw / 2f, cy + 24, 0.75f, colors[i], 1);
        }
        int ty = cy + 44;
        ctx.drawText(textRenderer, "Сегодня: день " + s.getLong("day") + (s.getBoolean("real") ? " · время года по настоящему календарю" : " · сезон длится " + s.getInt("len") + " дн."), x + 12, ty, 0xFFE6E2F0, false);
        ty += 14;
        String hol = s.getString("holiday");
        if (!hol.equals("NONE")) {
            Ui.big(ctx, textRenderer, "🎉 " + s.getString("holidayTitle"), x + 12, ty, 1.3f, hol.equals("HALLOWEEN") ? 0xFFFF8A1E : 0xFFFF3B4A, 0);
            ty += 16;
            String what = switch (hol) {
                case "NEW_YEAR" -> "Ёлка на площади делового центра · подарки спрятаны по городу (ПКМ — открыть) · салют в полночь";
                case "HALLOWEEN" -> "Тыквы на улицах · ночью на холмах (север, у леса) — «Дом с привидениями»: задание «Ночь страха»";
                default -> "Концерт и ярмарка на площади делового центра · подойди к сцене — премия от мэрии · салют в 21:00";
            };
            ty += wrap(ctx, what, x + 12, ty, w - 24, 0xFFFFE0B0) + 4;
        } else {
            ctx.drawText(textRenderer, "Ближайший праздник: " + s.getString("next"), x + 12, ty, 0xFFFFE0B0, false);
            ty += 14;
        }
        String season = s.getString("season");
        String tip = switch (season) {
            case "AUTUMN" -> "Осень: листва желтеет, чаще дожди — на дороге скользко.";
            case "WINTER" -> "Зима: снегопады, трава в инее. Конец осени — Хэллоуин, середина зимы — Новый год.";
            case "SPRING" -> "Весна: сезон охоты на оленя закрыт. Свежая зелень, редкие дожди.";
            default -> "Лето: солнце, пляжи. В середине лета — День города.";
        };
        wrap(ctx, tip, x + 12, ty + 4, w - 24, 0xFFB0BEC5);
    }

    // ------------------------------------------------------------------ охота
    private void hunt(DrawContext ctx, int x, int cy, int w, NbtCompound n) {
        boolean lic = n.getBoolean("license");
        ctx.fill(x + 10, cy, x + w - 10, cy + 30, lic ? 0x402E7D32 : 0x40501010);
        Ui.big(ctx, textRenderer, lic ? "Лицензия есть" : "Лицензии нет", x + 16, cy + 4, 1.2f, lic ? GREEN : 0xFFFF6B6B, 0);
        Ui.small(ctx, textRenderer, "Здесь: " + n.getString("region"), x + 16, cy + 19, 0.75f, 0xFFE0E0E8, 0);
        Ui.small(ctx, textRenderer, "добыто " + n.getInt("hunted") + (n.getInt("poached") > 0 ? " · браконьерство " + n.getInt("poached") : ""), x + w - 16, cy + 6, 0.75f, 0xFFB0BEC5, 2);
        int ty = cy + 36;
        boolean at = shop.equals("hunt") && n.getBoolean("lodgeNear");
        if (at) {
            int bw = (w - 20 - 18) / 4;
            button(ctx, x + 10, ty, bw, 16, lic ? "Лицензия ✓" : "Лицензия $" + Hunting.LICENSE_PRICE, 0xFF2E7D32, !lic, () -> buy("license"));
            button(ctx, x + 16 + bw, ty, bw, 16, "Винтовка $" + Hunting.RIFLE_PRICE, 0xFFB86A10, lic, () -> buy("rifle"));
            button(ctx, x + 22 + bw * 2, ty, bw, 16, "Патроны ×20 $" + Hunting.AMMO_PRICE, 0xFF5D4037, true, () -> buy("ammo"));
            button(ctx, x + 28 + bw * 3, ty, bw, 16, "Удочка $" + Hunting.ROD_PRICE, 0xFF1A8C9A, true, () -> buy("rod"));
            ty += 22;
            int value = 0;
            if (mc().player != null)
                for (int i = 0; i < mc().player.getInventory().size(); i++) {
                    ItemStack s = mc().player.getInventory().getStack(i);
                    value += Hunting.PRICES.getOrDefault(s.getItem(), 0) * s.getCount();
                }
            String sell = value <= 0 ? "Продать добычу — пусто" : lic ? "Продать всё: $" + value : "Продать вполцены наличкой: $" + value / 2;
            button(ctx, x + 10, ty, w - 20, 16, sell, lic ? 0xFF2E7D32 : 0xFF8E1B3A, value > 0, () -> {
                NbtCompound c = new NbtCompound();
                c.putString("shop", "hunt");
                NovogradClient.send("out_sell", c);
            });
            ty += 22;
        } else {
            ty += wrap(ctx, "Лицензия, охотничья винтовка с оптикой и скупщик шкур — в домике «Берлога» у деревни Сосновка (≈ "
                    + n.getInt("lx") + ", " + n.getInt("lz") + "), на западной окраине.", x + 12, ty, w - 24, 0xFFFFE0B0) + 4;
        }
        // цены
        NbtList prices = n.getList("prices", NbtElement.COMPOUND_TYPE);
        int col = 0;
        for (int i = 0; i < prices.size(); i++) {
            NbtCompound p = prices.getCompound(i);
            var item = Registries.ITEM.get(Identifier.of(p.getString("item")));
            int px = x + 12 + col * ((w - 24) / 2), py = ty + (i / 2) * 18;
            ctx.drawItem(new ItemStack(item), px, py);
            ctx.drawText(textRenderer, item.getName().getString(), px + 19, py + 4, 0xFFE6E2F0, false);
            String pr = "$" + p.getInt("price");
            ctx.drawText(textRenderer, pr, px + (w - 24) / 2 - 10 - textRenderer.getWidth(pr), py + 4, GREEN, false);
            col = 1 - col;
        }
        ty += (prices.size() + 1) / 2 * 18 + 4;
        wrap(ctx, "Где: леса и поля, Седые горы (козлы, волки), Пустошь (зайцы, лисы). Заповедник у озера Ясное и Ривьера — под охраной. "
                + "Без лицензии, в заповеднике или на оленя весной — браконьерство: увидят полиция или егерь — звезда.", x + 12, ty, w - 24, 0xFF90A4AE);
    }

    private void buy(String what) {
        NbtCompound c = new NbtCompound();
        c.putString("item", what);
        NovogradClient.send("out_buy", c);
    }

    // ------------------------------------------------------------------ рыбалка
    private void fish(DrawContext ctx, int x, int cy, int w, NbtCompound n) {
        ctx.drawText(textRenderer, "Поймано рыб: " + n.getInt("caught") + (n.getInt("wins") > 0 ? " · побед в турнирах: " + n.getInt("wins") : ""), x + 12, cy, 0xFFE6E2F0, false);
        int ty = cy + 14;
        boolean at = shop.equals("fish") && n.getBoolean("fishNear") || shop.equals("hunt");
        if (shop.equals("fish") && n.getBoolean("fishNear")) {
            int value = 0;
            if (mc().player != null)
                for (int i = 0; i < mc().player.getInventory().size(); i++) value += Fishing.price(mc().player.getInventory().getStack(i));
            int bw = (w - 26) / 2;
            button(ctx, x + 10, ty, bw, 16, "Удочка $" + Hunting.ROD_PRICE, 0xFF1A8C9A, true, () -> buy("rod"));
            button(ctx, x + 16 + bw, ty, bw, 16, value > 0 ? "Продать улов: $" + value : "Улова нет", 0xFF2E7D32, value > 0, () -> {
                NbtCompound c = new NbtCompound();
                c.putString("shop", "fish");
                NovogradClient.send("out_sell", c);
            });
            ty += 22;
        } else if (!at) {
            ty += wrap(ctx, "Удочка — в рыбных лавках: порт, клуб рыбаков на озере Ясное, марина Ривьеры (и в «Берлоге»). Забрасывай с пирса: в озере — карась, окунь, щука, сом; в море — скумбрия, лосось, тунец. Изредка — золотая рыбка!",
                    x + 12, ty, w - 24, 0xFFFFE0B0) + 4;
        }
        // рекорды
        NbtList rec = n.getList("records", NbtElement.COMPOUND_TYPE);
        int half = (w - 24) / 2;
        for (int i = 0; i < rec.size(); i++) {
            NbtCompound r = rec.getCompound(i);
            int px = x + 12 + (i % 2) * half, py = ty + (i / 2) * 11;
            int g = r.getInt("g");
            ctx.drawText(textRenderer, r.getString("name"), px, py, g > 0 ? 0xFFE6E2F0 : 0xFF6E6880, false);
            String v = g > 0 ? OutdoorClient.kg(g) : "—";
            ctx.drawText(textRenderer, v, px + half - 12 - textRenderer.getWidth(v), py, g > 0 ? 0xFF26C6DA : 0xFF6E6880, false);
        }
        ty += (rec.size() + 1) / 2 * 11 + 6;
        // турнир
        boolean on = n.getBoolean("on");
        Ui.big(ctx, textRenderer, on ? "ТУРНИР ИДЁТ" : "ТУРНИР РЫБАКОВ", x + 12, ty, 1.1f, on ? Ui.GOLD : 0xFF26C6DA, 0);
        Ui.small(ctx, textRenderer, on ? "до 11:00 · общий вес улова на Ясном" : "каждый 3-й день, 6:00–11:00 · следующий: день " + n.getLong("nextTour"),
                x + w - 12, ty + 2, 0.75f, 0xFFB0BEC5, 2);
        ty += 14;
        NbtList rows = n.getList("rows", NbtElement.COMPOUND_TYPE);
        if (on)
            for (int i = 0; i < Math.min(5, rows.size()); i++) {
                NbtCompound r = rows.getCompound(i);
                int c = r.getBoolean("me") ? Ui.GOLD : 0xFFE6E2F0;
                ctx.drawText(textRenderer, (i + 1) + ". " + r.getString("name"), x + 16, ty + i * 10, c, false);
                String v = OutdoorClient.kg(r.getInt("g"));
                ctx.drawText(textRenderer, v, x + w / 2 - textRenderer.getWidth(v), ty + i * 10, c, false);
            }
        else wrap(ctx, "Призы: $1500 · $800 · $400. Лови на озере — вес засчитывается сам.", x + 12, ty, w - 24, 0xFF90A4AE);
    }
}
