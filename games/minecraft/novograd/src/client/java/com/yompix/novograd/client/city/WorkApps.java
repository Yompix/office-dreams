package com.yompix.novograd.client.city;

import com.yompix.novograd.client.NovogradClient;
import com.yompix.novograd.client.ui.Ui;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

/**
 * 0.9.10 «Работа»: приложения телефона — «Карьера» (профессии и звания), «Криминал» (авторитет, угон на заказ,
 * банда), «Служба» (полиция, пожарные, скорая — по желанию), «Бизнес» (своё кафе, салон, клуб). Стиль — как у
 * CityApps: заголовок, плашки, кнопки; данные — пакеты career / crime / duty / biz.
 */
public final class WorkApps {
    private WorkApps() {}

    public static NbtCompound career = new NbtCompound(), crime = new NbtCompound(), duty = new NbtCompound(), biz = new NbtCompound(), voice = new NbtCompound();

    public static boolean receive(String type, NbtCompound n) {
        switch (type) {
            case "career" -> career = n;
            case "crime" -> crime = n;
            case "duty" -> duty = n;
            case "biz" -> biz = n;
            case "voice" -> voice = n;
            default -> {
                return false;
            }
        }
        return true;
    }

    static void bar(DrawContext ctx, int x, int y, int w, int xp, int from, int next, int color) {
        float k = next <= from ? 1 : (xp - from) / (float) (next - from);
        Ui.bar(ctx, x, y, w, 3, Math.max(0, Math.min(1, k)), color);
    }

    // ====================================================================== «Карьера»
    public static class Career extends CityApps.App {
        public Career() {
            super("Карьера", 0xFF5A6ACF);
            NovogradClient.send("career_state");
        }

        @Override
        void body(DrawContext ctx, int y, int w) {
            NbtList l = career.getList("list", NbtElement.COMPOUND_TYPE);
            int on = career.contains("on") ? career.getInt("on") : -1;
            for (int i = 0; i < l.size(); i++) {
                NbtCompound c = l.getCompound(i);
                boolean me = i == on;
                Ui.slant(ctx, 12, y, w, 24, 3, me ? 0xE03949AB : 0x90161615);
                ctx.drawText(textRenderer, c.getString("name") + "  §7" + c.getString("rank"), 18, y + 3, Ui.WHITE, me);
                Ui.small(ctx, textRenderer, "оплата ×" + String.format("%.1f", c.getFloat("mul")) + "  ·  " + c.getInt("xp") + " опыта", 18, y + 14, 0.75f, 0xFFC5CAE9, 0);
                bar(ctx, 12 + w - 150, y + 16, 90, c.getInt("xp"), c.getInt("from"), c.getInt("next"), 0xFF7986CB);
                int idx = i;
                if (on < 0) button(ctx, 12 + w - 54, y + 4, 48, 16, "СМЕНА", 0xFF43A047, true, () -> CityApps.send("career_start", "i", idx));
                else if (me) button(ctx, 12 + w - 54, y + 4, 48, 16, "КОНЕЦ", 0xFFC62828, true, () -> CityApps.send("career_stop"));
                y += 27;
            }
            if (on >= 0) Ui.small(ctx, textRenderer, "Задание на радаре · осталось " + career.getLong("left") + " с", 12, y + 4, 0.85f, Ui.GOLD, 0);
        }
    }

    // ====================================================================== «Криминал»
    public static class Crime extends CityApps.App {
        private String gangName = "";
        private net.minecraft.client.gui.widget.TextFieldWidget field;

        public Crime() {
            super("Криминал", 0xFF8E1B3A);
            NovogradClient.send("crime_state");
        }

        @Override
        protected void init() {
            super.init();
            field = new net.minecraft.client.gui.widget.TextFieldWidget(textRenderer, 12, 0, 140, 14, net.minecraft.text.Text.literal("Название"));
            field.setMaxLength(16);
            addDrawableChild(field);
        }

        @Override
        void body(DrawContext ctx, int y, int w) {
            NbtCompound c = crime;
            Ui.slant(ctx, 12, y, w, 28, 4, 0xC0300A14);
            Ui.big(ctx, textRenderer, c.getString("rank").toUpperCase(), 20, y + 5, 1.2f, Ui.WHITE, 0);
            Ui.small(ctx, textRenderer, "авторитет " + c.getInt("xp") + " · добыча +" + c.getInt("r") * 5 + "%", 12 + w - 8, y + 5, 0.8f, 0xFFFF8A80, 2);
            bar(ctx, 20, y + 22, w - 16, c.getInt("xp"), c.getInt("from"), c.getInt("next"), 0xFFE53935);
            y += 36;
            // угон на заказ
            if (c.contains("order"))
                Ui.small(ctx, textRenderer, "ЗАКАЗ: " + c.getString("order") + " · до $" + c.getInt("orderPay") + " · " + c.getLong("orderLeft") + " с · разборка на радаре", 12, y + 4, 0.85f, Ui.GOLD, 0);
            else button(ctx, 12, y, 160, 16, "ВЗЯТЬ ЗАКАЗ НА УГОН", 0xFFB71C1C, true, () -> CityApps.send("crime_order"));
            button(ctx, 12 + 166, y, 160, 16, "КОНТРАБАНДА И ДЕЛА", 0xFF5D4037, true, () -> mc().setScreen(new com.yompix.novograd.client.screen.DarkJobsScreen()));
            y += 24;
            Ui.small(ctx, textRenderer, "НАЛЁТ ВДВОЁМ: напарник рядом со стволом — касса +60 %, пополам (банда — ещё +20 %)", 12, y, 0.75f, 0xFF90A4AE, 0);
            y += 14;
            // банда
            if (c.contains("gang")) {
                Ui.small(ctx, textRenderer, "БАНДА «" + c.getString("gang") + "» · авторитет " + c.getInt("gangRep"), 12, y, 0.9f, 0xFFFF8A80, 0);
                y += 12;
                NbtList m = c.getList("members", NbtElement.STRING_TYPE);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < m.size(); i++) sb.append(i > 0 ? ", " : "").append(m.getString(i));
                Ui.small(ctx, textRenderer, sb.toString(), 12, y, 0.8f, 0xFFB9B7B1, 0);
                y += 12;
                if (c.getBoolean("leader")) {
                    NbtList on = c.getList("online", NbtElement.STRING_TYPE);
                    for (int i = 0; i < on.size() && i < 4; i++) {
                        String nm = on.getString(i);
                        button(ctx, 12 + i * 92, y, 88, 14, "ЗВАТЬ " + nm, 0xFF6D1B2A, true, () -> CityApps.send("crime_invite", "who", nm));
                    }
                    y += 18;
                }
                button(ctx, 12, y, 100, 14, "ВЫЙТИ", 0xFF546E7A, true, () -> CityApps.send("crime_leave"));
                field.visible = false;
                y += 20;
            } else {
                field.visible = true;
                field.setY(y);
                button(ctx, 160, y, 150, 14, "ОСНОВАТЬ БАНДУ $2000", 0xFFB71C1C, field.getText().trim().length() >= 3, () -> CityApps.send("crime_gang", "name", field.getText()));
                if (c.contains("invite")) button(ctx, 316, y, 150, 14, "ВСТУПИТЬ В «" + c.getString("invite") + "»", 0xFF43A047, true, () -> CityApps.send("crime_accept"));
                y += 22;
            }
            NbtList top = c.getList("top", NbtElement.COMPOUND_TYPE);
            if (!top.isEmpty()) {
                Ui.small(ctx, textRenderer, "БАНДЫ ГОРОДА", 12, y, 0.8f, 0xFF90A4AE, 0);
                y += 10;
                for (int i = 0; i < top.size(); i++) {
                    NbtCompound g = top.getCompound(i);
                    row(ctx, y, w, (i + 1) + ". «" + g.getString("name") + "»", g.getInt("n") + " чел. · авторитет " + g.getInt("rep"), 0xFFFF8A80, false);
                    y += 16;
                }
            }
        }
    }

    // ====================================================================== «Служба»
    public static class Duty extends CityApps.App {
        public Duty() {
            super("Служба", 0xFF3F7FC0);
            NovogradClient.send("duty_state");
        }

        @Override
        void body(DrawContext ctx, int y, int w) {
            NbtCompound d = duty;
            int role = d.contains("role") ? d.getInt("role") : -1;
            String[] names = com.yompix.novograd.gta.Duty.NAMES;
            String[] what = {"в розыске: " + d.getInt("wanted") + " · задержать — стой рядом 3 с", "пожаров: " + d.getInt("fires") + " · брандспойт в пожарной машине (ПКМ)",
                    "раненых: " + d.getInt("injured") + " · вылечить — стой рядом 3 с"};
            int[] colors = {0xE01565C0, 0xE0C62828, 0xE02E7D32};
            for (int i = 0; i < 3; i++) {
                boolean me = role == i;
                Ui.slant(ctx, 12, y, w, 26, 3, me ? colors[i] : 0x90161615);
                ctx.drawText(textRenderer, names[i] + (me ? "  · НА СМЕНЕ" : ""), 18, y + 3, Ui.WHITE, me);
                Ui.small(ctx, textRenderer, what[i], 18, y + 15, 0.75f, 0xFFCFD8DC, 0);
                int idx = i;
                boolean can = role < 0 && d.getInt("stars") == 0 && d.getLong("cooldown") == 0;
                if (role < 0) button(ctx, 12 + w - 74, y + 5, 68, 16, "НА СМЕНУ", 0xFF43A047, can, () -> CityApps.send("duty_on", "role", idx));
                else if (me) button(ctx, 12 + w - 74, y + 5, 68, 16, "СДАТЬ", 0xFFC62828, true, () -> CityApps.send("duty_off"));
                y += 29;
            }
            y += 4;
            String rule = d.getInt("stars") > 0 ? "С розыском на службу не берут" : d.getLong("cooldown") > 0 ? "На смену — через " + d.getLong("cooldown") + " с" : "";
            if (!rule.isEmpty()) Ui.small(ctx, textRenderer, rule, 12, y, 0.85f, Ui.GOLD, 0);
            y += 14;
            Ui.small(ctx, textRenderer, "Правила: только по желанию · на смене не ранишь игроков без розыска · сам нарушил — смена снята", 12, y, 0.75f, 0xFF90A4AE, 0);
            Ui.small(ctx, textRenderer, "одного и того же задержать — не чаще раза в 10 минут", 12, y + 10, 0.75f, 0xFF90A4AE, 0);
        }
    }

    // ====================================================================== «Бизнес»
    public static class Biz extends CityApps.App {
        private int openType = 0;

        public Biz() {
            super("Бизнес", 0xFF2E7D32);
            NovogradClient.send("biz_state");
        }

        @Override
        void body(DrawContext ctx, int y, int w) {
            NbtCompound b = biz;
            String[] types = com.yompix.novograd.gta.Business.TYPE_NAMES;
            if (b.contains("mine")) {
                NbtCompound m = b.getCompound("mine");
                int t = m.getInt("type");
                Ui.slant(ctx, 12, y, w, 30, 4, 0xC01B5E20);
                Ui.big(ctx, textRenderer, "«" + m.getString("name") + "»", 20, y + 4, 1.2f, Ui.WHITE, 0);
                Ui.small(ctx, textRenderer, types[t].toLowerCase() + " · репутация " + m.getInt("rep") + " · касса $" + m.getInt("cash"), 20, y + 19, 0.8f, 0xFFA5D6A7, 0);
                Ui.small(ctx, textRenderer, "вчера: " + m.getInt("g") + " гостей · $" + m.getInt("rv") + " − $" + m.getInt("cs"), 12 + w - 8, y + 5, 0.8f, Ui.WHITE, 2);
                Ui.small(ctx, textRenderer, "запас " + m.getInt("stock"), 12 + w - 8, y + 18, 0.85f, m.getInt("stock") < 20 ? Ui.RED : Ui.GOLD, 2);
                y += 36;
                // цены
                Ui.small(ctx, textRenderer, "ЦЕНЫ", 12, y + 3, 0.8f, 0xFF90A4AE, 0);
                for (int i = 0; i < 3; i++) {
                    int iv = i;
                    button(ctx, 60 + i * 84, y, 80, 14, com.yompix.novograd.gta.Business.PRICE_NAMES[i], m.getInt("price") == i ? accent : 0xFF37474F, true, () -> CityApps.send("biz_price", "v", iv));
                }
                y += 18;
                // персонал
                int[] staff = m.getIntArray("staff");
                for (int i = 0; i < 3; i++) {
                    int iv = i;
                    String role = com.yompix.novograd.gta.Business.STAFF[t][i];
                    ctx.drawText(textRenderer, role + ": " + (staff.length > i ? staff[i] : 0), 14, y + 3, Ui.WHITE, false);
                    button(ctx, 120, y, 20, 14, "−", 0xFF546E7A, true, () -> CityApps.send("biz_staff", "i", iv, "d", -1));
                    button(ctx, 144, y, 20, 14, "+", 0xFF43A047, true, () -> CityApps.send("biz_staff", "i", iv, "d", 1));
                    y += 16;
                }
                y += 4;
                int unit = b.getIntArray("unit").length > t ? b.getIntArray("unit")[t] : 10;
                int lot = t == 1 ? 2 : 100;
                button(ctx, 12, y, 140, 16, "ЗАПАС +" + lot + " · $" + lot * unit, 0xFFF9A825, true, () -> CityApps.send("biz_buy", "units", lot));
                button(ctx, 156, y, 160, 16, "СВОЙ РЕЙС (−50 %)", 0xFF6D4C41, true, () -> CityApps.send("biz_run"));
                button(ctx, 320, y, 110, 16, "КАССУ НА КАРТУ", 0xFF2E7D32, m.getInt("cash") > 0, () -> CityApps.send("biz_cash"));
                y += 22;
            } else {
                Ui.small(ctx, textRenderer, "ОТКРЫТЬ СВОЁ ДЕЛО", 12, y, 0.85f, 0xFF90A4AE, 0);
                y += 12;
                int[] open = b.getIntArray("open");
                for (int i = 0; i < 3; i++) {
                    int iv = i;
                    button(ctx, 12 + i * 120, y, 116, 16, types[i] + (open.length > i ? " $" + open[i] : ""), openType == i ? accent : 0xFF37474F, true, () -> openType = iv);
                }
                y += 20;
                NbtList free = b.getList("free", NbtElement.COMPOUND_TYPE);
                for (int i = 0; i < free.size(); i++) {
                    NbtCompound f = free.getCompound(i);
                    if (f.getInt("type") != openType) continue;
                    String pl = f.getString("place");
                    row(ctx, y, w, place(pl), "открыть", Ui.MONEY, false);
                    area(12, y, w, 14, () -> CityApps.send("biz_open", "type", openType, "place", pl, "name", ""));
                    y += 16;
                }
                y += 6;
            }
            NbtList all = b.getList("all", NbtElement.COMPOUND_TYPE);
            if (!all.isEmpty()) {
                Ui.small(ctx, textRenderer, "РЕЙТИНГ ЗАВЕДЕНИЙ (выручка вчера)", 12, y, 0.8f, 0xFF90A4AE, 0);
                y += 10;
                for (int i = 0; i < all.size() && y < height - 26; i++) {
                    NbtCompound x = all.getCompound(i);
                    row(ctx, y, w, (i + 1) + ". «" + x.getString("name") + "» · " + types[x.getInt("type")].toLowerCase() + " · " + x.getString("ownerName"), "$" + x.getInt("rv"), Ui.MONEY, false);
                    y += 16;
                }
            }
        }

        static String place(String id) {
            return switch (id) {
                case "cafe_dt" -> "Центр, у площади";
                case "cafe_suburb" -> "Пригород";
                case "cafe_riviera" -> "Ривьера";
                case "cafe_mirage" -> "«Мираж»";
                case "industry_street" -> "Промзона";
                case "suburb_street" -> "Пригород, улица";
                case "desert_road" -> "Трасса в пустыню";
                case "club_neon" -> "У «Неона»";
                case "beach_bar" -> "Пляж";
                case "riv_strip" -> "Стрип Ривьеры";
                default -> id;
            };
        }
    }

    // ====================================================================== «Рация» (голос: Simple Voice Chat)
    public static class VoiceApp extends CityApps.App {
        public VoiceApp() {
            super("Рация", 0xFF26A69A);
            NovogradClient.send("voice_state");
        }

        @Override
        void body(DrawContext ctx, int y, int w) {
            NbtCompound v = voice;
            boolean mod = com.yompix.novograd.client.VoiceKeys.installed();
            if (!mod || !v.getBoolean("server")) {
                row(ctx, y, w, "Голос выключен", !mod ? "нет мода Simple Voice Chat" : "на сервере нет мода голоса", 0xFFFF8A65, false);
                y += 16;
                Ui.small(ctx, textRenderer, "Установщик Новограда ставит его сам — переустанови или положи в mods", 12, y + 2, 0.8f, 0xFF90A4AE, 0);
                return;
            }
            String now = v.getString("now");
            row(ctx, y, w, "Сейчас: " + (now.isEmpty() ? "голос рядом" : now), v.getBoolean("jail") ? "в тюрьме — тишина" : "говорить — " + com.yompix.novograd.client.VoiceKeys.talkKey(), 0xFF80CBC4, true);
            y += 22;
            // каналы
            String ch = v.getString("ch");
            int bw = (w - 8) / 3;
            button(ctx, 12, y, bw, 16, "РЯДОМ", ch.equals("near") && now.isEmpty() ? accent : 0xFF37474F, true, () -> CityApps.send("voice_channel", "ch", "near"));
            button(ctx, 16 + bw, y, bw, 16, "СЛУЖБА", ch.equals("duty") || now.startsWith("Рация") ? accent : 0xFF37474F, v.getBoolean("duty"), () -> CityApps.send("voice_channel", "ch", "duty"));
            String gang = v.getString("gang");
            button(ctx, 20 + 2 * bw, y, bw, 16, gang.isEmpty() ? "БАНДА" : "БАНДА " + gang.toUpperCase(), ch.equals("gang") ? accent : 0xFF37474F, !gang.isEmpty(), () -> CityApps.send("voice_channel", "ch", "gang"));
            y += 26;
            // звонок
            Ui.small(ctx, textRenderer, "ЗВОНОК", 12, y, 0.8f, 0xFF90A4AE, 0);
            y += 10;
            String ring = v.getString("ring"), call = v.getString("call");
            int hw = (w - 4) / 2;
            if (!ring.isEmpty()) {
                row(ctx, y, w, "☎ Звонит " + ring, "", 0, true);
                y += 16;
                button(ctx, 12, y, hw, 16, "ОТВЕТИТЬ", 0xFF43A047, true, () -> CityApps.send("voice_answer"));
                button(ctx, 16 + hw, y, hw, 16, "СБРОС", 0xFF546E7A, true, () -> CityApps.send("voice_hangup"));
                y += 22;
            } else if (!call.isEmpty() || v.getBoolean("dialing")) {
                row(ctx, y, w, call.isEmpty() ? "☎ Звоним…" : "☎ На связи: " + call, "", 0, true);
                y += 16;
                button(ctx, 12, y, w, 16, "ПОЛОЖИТЬ ТРУБКУ", 0xFFE53935, true, () -> CityApps.send("voice_hangup"));
                y += 22;
            } else {
                NbtList pl = v.getList("players", NbtElement.STRING_TYPE);
                if (pl.isEmpty()) {
                    Ui.small(ctx, textRenderer, "В сети никого", 18, y + 2, 0.8f, 0xFFB9B7B1, 0);
                    y += 14;
                }
                int cols = Math.max(1, w / 140), cw = (w - (cols - 1) * 4) / cols;
                for (int k = 0; k < pl.size() && k < cols * 3; k++) {
                    String who = pl.getString(k);
                    button(ctx, 12 + (k % cols) * (cw + 4), y + (k / cols) * 18, cw, 16, "☎ " + who, 0xFF00897B, true, () -> CityApps.send("voice_call", "to", who));
                }
                y += 18 * Math.min(3, (pl.size() + cols - 1) / cols) + 6;
            }
            button(ctx, 12, y, w, 16, "НАСТРОЙКИ ГОЛОСА (МИКРОФОН, ГРОМКОСТЬ)", 0xFF37474F, true, com.yompix.novograd.client.VoiceKeys::openSettings);
        }
    }
}
