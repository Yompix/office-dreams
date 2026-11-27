package com.yompix.novograd.client.city;

import com.yompix.novograd.client.NovogradClient;
import com.yompix.novograd.client.screen.LifeScreen;
import com.yompix.novograd.client.story.ClientStory;
import com.yompix.novograd.client.ui.Ui;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * 0.9.9 «Город»: приложения телефона — «Ночь», «Встречи», «Спорт», «Фотограм», «Уют». Один стиль: заголовок, строки-плашки,
 * кнопки внизу; минимум текста. Данные — от сервера (пакеты night / dating / sport / social / home_life).
 */
public final class CityApps {
    private CityApps() {}

    public static NbtCompound night = new NbtCompound(), dating = new NbtCompound(), sport = new NbtCompound(), social = new NbtCompound(), home = new NbtCompound();

    public static boolean receive(String type, NbtCompound n) {
        switch (type) {
            case "night" -> night = n;
            case "dating" -> dating = n;
            case "sport" -> sport = n;
            case "social" -> social = n;
            case "home_life" -> home = n;
            default -> {
                return false;
            }
        }
        return true;
    }

    static void mark(double x, double z) {
        var pl = net.minecraft.client.MinecraftClient.getInstance().player;
        ClientStory.waypoint = new Vec3d(x, pl == null ? 64 : pl.getY(), z);
    }

    static String dist(double x, double z) {
        var pl = net.minecraft.client.MinecraftClient.getInstance().player;
        return pl == null ? "" : Math.round(Math.hypot(x - pl.getX(), z - pl.getZ())) + " м";
    }

    static NbtCompound send(String type, Object... kv) {
        NbtCompound n = new NbtCompound();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            if (kv[i + 1] instanceof Integer v) n.putInt((String) kv[i], v);
            else if (kv[i + 1] instanceof Boolean v) n.putBoolean((String) kv[i], v);
            else n.putString((String) kv[i], String.valueOf(kv[i + 1]));
        }
        NovogradClient.send(type, n);
        return n;
    }

    /** Общая основа: заголовок цветом приложения, строки-плашки. */
    abstract static class App extends LifeScreen {
        final int accent;
        final String head;

        App(String title, int accent) {
            super(title);
            this.accent = accent;
            this.head = title.toUpperCase();
        }

        int top() {
            return 8 + (int) ((1 - openAnim()) * -16);
        }

        @Override
        protected void draw(DrawContext ctx, float delta) {
            title(ctx, head, 12, top(), accent);
            body(ctx, top() + 30, width - 24);
            center(ctx, "Esc — назад", width / 2, height - 12, 0xFF607D8B);
        }

        abstract void body(DrawContext ctx, int y, int w);

        void row(DrawContext ctx, int y, int w, String left, String right, int rc, boolean on) {
            Ui.slant(ctx, 12, y, w, 14, 3, on ? Ui.alpha(accent, 0.85f) : 0x90161615);
            ctx.drawText(textRenderer, left, 18, y + 3, Ui.WHITE, on);
            if (!right.isEmpty()) Ui.small(ctx, textRenderer, right, 12 + w - 6, y + 4, 0.8f, rc, 2);
        }
    }

    // ====================================================================== «Ночь»
    public static class Night extends App {
        public Night() {
            super("Ночь", 0xFFAB47BC);
            NovogradClient.send("night_state");
        }

        @Override
        void body(DrawContext ctx, int y, int w) {
            NbtCompound s = night;
            // карточка концерта
            Ui.slant(ctx, 12, y, w, 34, 4, s.getBoolean("now") ? 0xE07B1FA2 : 0xC0261A30);
            Ui.small(ctx, textRenderer, s.getBoolean("now") ? "СЕЙЧАС · КОНЦЕРТ" : "СЕГОДНЯ 19:00 · КОНЦЕРТ", 20, y + 4, 0.8f, Ui.GOLD, 0);
            ctx.drawText(textRenderer, s.getString("band") + " — " + s.getString("venue"), 20, y + 17, Ui.WHITE, true);
            if (s.contains("x")) button(ctx, 12 + w - 70, y + 9, 62, 16, "НА КАРТУ", accent, true, () -> {
                mark(s.getDouble("x"), s.getDouble("z"));
                close();
            });
            y += 42;
            Ui.small(ctx, textRenderer, "МЕСТА", 12, y, 0.8f, 0xFF90A4AE, 0);
            y += 10;
            NbtList pl = s.getList("places", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < pl.size() && y < height - 90; i++) {
                NbtCompound c = pl.getCompound(i);
                row(ctx, y, w, c.getString("name") + "  §7" + c.getString("what"), dist(c.getDouble("x"), c.getDouble("z")), 0xFFB9B7B1, false);
                area(12, y, w, 14, () -> {
                    mark(c.getDouble("x"), c.getDouble("z"));
                    close();
                });
                y += 16;
            }
            y += 4;
            Ui.small(ctx, textRenderer, "ИГРЫ НА ДЕНЬГИ · подойди к метке", 12, y, 0.8f, 0xFF90A4AE, 0);
            y += 10;
            NbtList gl = s.getList("games", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < gl.size() && y < height - 26; i++) {
                NbtCompound c = gl.getCompound(i);
                row(ctx, y, w, c.getString("title") + " · $" + c.getInt("bet"), dist(c.getDouble("x"), c.getDouble("z")), 0xFFB9B7B1, false);
                area(12, y, w, 14, () -> {
                    mark(c.getDouble("x"), c.getDouble("z"));
                    close();
                });
                y += 16;
            }
        }
    }

    // ====================================================================== «Встречи»
    public static class Dating extends App {
        private int sel = -1;

        public Dating() {
            super("Встречи", 0xFFE91E63);
            NovogradClient.send("date_state");
        }

        @Override
        void body(DrawContext ctx, int y, int w) {
            NbtList l = dating.getList("list", NbtElement.COMPOUND_TYPE);
            int active = dating.contains("active") ? dating.getInt("active") : -1;
            if (l.isEmpty()) {
                center(ctx, "Пока никого", width / 2, y + 16, 0xFFB9B7B1);
                Ui.small(ctx, textRenderer, "Shift + ПКМ пустой рукой по прохожему — познакомиться", width / 2, y + 32, 0.8f, 0xFF90A4AE, 1);
                return;
            }
            for (int i = 0; i < l.size(); i++) {
                NbtCompound e = l.getCompound(i);
                int rel = e.getInt("rel");
                boolean on = i == sel;
                Ui.slant(ctx, 12, y, w, 24, 3, on ? 0xE0880E4F : 0x90161615);
                ctx.drawText(textRenderer, e.getString("name") + (i == active ? "  §d♥ свидание" : ""), 18, y + 3, Ui.WHITE, on);
                Ui.small(ctx, textRenderer, e.getString("job") + " · любит " + e.getString("likes") + " · " + com.yompix.novograd.life.Dating.level(rel), 18, y + 14, 0.75f, 0xFFF8BBD0, 0);
                // сердечки симпатии
                int hx = 12 + w - 108;
                for (int k = 0; k < 10; k++) ctx.fill(hx + k * 10, y + 8, hx + k * 10 + 7, y + 15, rel >= (k + 1) * 10 ? 0xFFE91E63 : rel > k * 10 ? 0xFFF48FB1 : 0x40FFFFFF);
                final int idx = i;
                area(12, y, w, 24, () -> sel = idx);
                y += 27;
            }
            if (sel < 0 || sel >= l.size()) return;
            NbtCompound e = l.getCompound(sel);
            int by = height - 66, bw = (w - 9) / 4;
            button(ctx, 12, by, bw, 16, "ПОЗВОНИТЬ", 0xFF3FB8B0, true, () -> send("date_call", "i", sel));
            boolean perk = e.getInt("rel") >= 60 && e.getLong("perk") != dating.getLong("day");
            button(ctx, 15 + bw, by, bw, 16, "ПОПРОСИТЬ: " + com.yompix.novograd.life.Dating.perkName(e.getString("job")).toUpperCase(), 0xFFFFA000, perk,
                    () -> send("date_perk", "i", sel));
            button(ctx, 18 + 2 * bw, by, bw, 16, "ЗАБЫТЬ", 0xFF546E7A, true, () -> {
                send("date_forget", "i", sel);
                sel = -1;
            });
            Ui.small(ctx, textRenderer, "СВИДАНИЕ:", 12, by + 22, 0.8f, 0xFF90A4AE, 0);
            String[] places = com.yompix.novograd.life.Dating.PLACE_NAMES;
            int pw = (w - 4 * (places.length - 1)) / places.length;
            for (int k = 0; k < places.length; k++) {
                int kk = k;
                button(ctx, 12 + k * (pw + 4), by + 32, pw, 16, places[k], 0xFFE91E63, active < 0, () -> send("date_ask", "i", sel, "place", kk));
            }
        }
    }

    // ====================================================================== «Спорт»
    public static class Sport extends App {
        private int route = 0, fee = 100, duelGame = 0, duelBet = 100;
        static final String[] DUEL_GAMES = {"billiards", "bowling", "hoops", "golf"}, DUEL_TITLES = {"Бильярд", "Боулинг", "Баскетбол", "Гольф"};

        public Sport() {
            super("Спорт", 0xFFFF7043);
            NovogradClient.send("sport_state");
        }

        @Override
        void body(DrawContext ctx, int y, int w) {
            NbtList ev = sport.getList("events", NbtElement.COMPOUND_TYPE);
            boolean in = false;
            // 0.9.10: входящий вызов от игрока
            if (sport.contains("ask")) {
                NbtCompound a = sport.getCompound("ask");
                row(ctx, y, w, "⚔ " + a.getString("from") + " · " + a.getString("game"), a.getInt("bet") > 0 ? "$" + a.getInt("bet") : "на интерес", Ui.GOLD, true);
                y += 16;
                int hw = (w - 4) / 2;
                button(ctx, 12, y, hw, 16, "ПРИНЯТЬ", 0xFF43A047, true, () -> {
                    send("duel_accept");
                    sport.remove("ask");
                });
                button(ctx, 16 + hw, y, hw, 16, "НЕТ", 0xFF546E7A, true, () -> {
                    send("duel_decline");
                    sport.remove("ask");
                });
                y += 24;
            }
            Ui.small(ctx, textRenderer, "ЗАЕЗДЫ", 12, y, 0.8f, 0xFF90A4AE, 0);
            y += 10;
            if (ev.isEmpty()) {
                Ui.small(ctx, textRenderer, "Никто не зовёт — создай свой", 18, y + 2, 0.8f, 0xFFB9B7B1, 0);
                y += 16;
            }
            for (int i = 0; i < ev.size(); i++) {
                NbtCompound c = ev.getCompound(i);
                in |= c.getBoolean("mine");
                String st = switch (c.getInt("stage")) {
                    case 0 -> "набор";
                    case 1 -> "отсчёт";
                    case 2 -> "едут";
                    default -> "итоги";
                };
                row(ctx, y, w, c.getString("name") + (c.getInt("fee") > 0 ? " · $" + c.getInt("fee") : "") + "  §7" + c.getString("who"), st + " · " + c.getInt("n"), 0xFFFFCC80, c.getBoolean("mine"));
                int id = c.getInt("id");
                if (!c.getBoolean("mine") && c.getInt("stage") == 0) area(12, y, w, 14, () -> send("sport_join", "id", id));
                y += 16;
            }
            y += 6;
            if (in) {
                button(ctx, 12, y, 110, 16, "ВЫЙТИ", 0xFF546E7A, true, () -> send("sport_leave"));
                for (int i = 0; i < ev.size(); i++) if (ev.getCompound(i).getBoolean("host") && ev.getCompound(i).getInt("stage") == 0)
                    button(ctx, 128, y, 110, 16, "СТАРТ", 0xFF43A047, true, () -> send("sport_start"));
                y += 24;
            } else {
                Ui.small(ctx, textRenderer, "НОВЫЙ ЗАЕЗД", 12, y, 0.8f, 0xFF90A4AE, 0);
                y += 10;
                NbtList routes = sport.getList("routes", NbtElement.STRING_TYPE);
                int rw = (w - 8) / Math.max(1, routes.size());
                for (int k = 0; k < routes.size(); k++) {
                    int kk = k;
                    button(ctx, 12 + k * (rw + 4), y, rw, 16, routes.getString(k), route == k ? accent : 0xFF37474F, true, () -> route = kk);
                }
                y += 20;
                int[] fees = {0, 50, 100, 500, 1000};
                int fw = (w - 16) / fees.length;
                for (int k = 0; k < fees.length; k++) {
                    int fv = fees[k];
                    button(ctx, 12 + k * (fw + 4), y, fw, 16, fv == 0 ? "без взноса" : "$" + fv, fee == fv ? accent : 0xFF37474F, true, () -> fee = fv);
                }
                y += 20;
                button(ctx, 12, y, w, 18, "СОЗДАТЬ · СТАРТ ЧЕРЕЗ 60 С (НУЖНА МАШИНА У СТАРТА)", 0xFF43A047, true, () -> send("sport_create", "route", route, "fee", fee));
                y += 26;
            }
            // 0.9.10: вызов игроку — та же партия у обоих, ставка в залог
            NbtList pls = sport.getList("players", NbtElement.STRING_TYPE);
            Ui.small(ctx, textRenderer, "ВЫЗОВ ИГРОКУ" + (pls.isEmpty() ? " · в сети никого" : " · вживую: Shift+ПКМ по игроку"), 12, y, 0.8f, 0xFF90A4AE, 0);
            y += 10;
            if (!pls.isEmpty() && !sport.getBoolean("inDuel")) {
                int gw = (w - 12) / 4;
                for (int k = 0; k < 4; k++) {
                    int kk = k;
                    button(ctx, 12 + k * (gw + 4), y, gw, 14, DUEL_TITLES[k], duelGame == k ? accent : 0xFF37474F, true, () -> duelGame = kk);
                }
                y += 18;
                int[] bets = {0, 50, 100, 500, 1000};
                int bw2 = (w - 16) / bets.length;
                for (int k = 0; k < bets.length; k++) {
                    int bv = bets[k];
                    button(ctx, 12 + k * (bw2 + 4), y, bw2, 14, bv == 0 ? "на интерес" : "$" + bv, duelBet == bv ? accent : 0xFF37474F, true, () -> duelBet = bv);
                }
                y += 18;
                int cols = Math.max(1, w / 140), cw = (w - (cols - 1) * 4) / cols;
                for (int k = 0; k < pls.size() && k < cols * 2; k++) {
                    String who = pls.getString(k);
                    button(ctx, 12 + (k % cols) * (cw + 4), y + (k / cols) * 18, cw, 16, "⚔ " + who, 0xFFE64A19, true,
                            () -> send("duel_ask", "to", who, "game", DUEL_GAMES[duelGame], "bet", duelBet));
                }
                y += 18 * Math.min(2, (pls.size() + cols - 1) / cols) + 6;
            } else y += 6;
            Ui.small(ctx, textRenderer, "ПЛОЩАДКИ", 12, y, 0.8f, 0xFF90A4AE, 0);
            y += 10;
            NbtList gl = sport.getList("games", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < gl.size() && y < height - 26; i++) {
                NbtCompound c = gl.getCompound(i);
                String g = c.getString("game");
                if (!g.equals("hoops") && !g.equals("golf") && !g.equals("bowling")) continue;
                row(ctx, y, w, c.getString("title") + " · $" + c.getInt("bet"), dist(c.getDouble("x"), c.getDouble("z")), 0xFFB9B7B1, false);
                area(12, y, w, 14, () -> {
                    mark(c.getDouble("x"), c.getDouble("z"));
                    close();
                });
                y += 16;
            }
        }
    }

    // ====================================================================== «Фотограм»
    public static class Social extends App {
        private int scroll;

        public Social() {
            super("Фотограм", 0xFFEC407A);
            NovogradClient.send("social_feed");
        }

        @Override
        void body(DrawContext ctx, int y, int w) {
            NbtCompound s = social;
            // профиль
            Ui.slant(ctx, 12, y, w, 30, 4, 0xC0301020);
            Ui.big(ctx, textRenderer, String.valueOf(s.getInt("followers")), 20, y + 6, 1.4f, Ui.WHITE, 0);
            Ui.small(ctx, textRenderer, "подписчиков · " + s.getString("rank"), 20, y + 20, 0.75f, 0xFFF8BBD0, 0);
            Ui.small(ctx, textRenderer, "слава " + s.getInt("fame") + " · постов " + s.getInt("posts"), 12 + w - 8, y + 6, 0.8f, 0xFFB9B7B1, 2);
            Ui.small(ctx, textRenderer, "Камера в телефоне — свой пост", 12 + w - 8, y + 18, 0.75f, 0xFF90A4AE, 2);
            y += 38;
            NbtList feed = s.getList("feed", NbtElement.COMPOUND_TYPE);
            String me = mc().player == null ? "" : mc().player.getName().getString();
            int start = MathHelper.clamp(scroll, 0, Math.max(0, feed.size() - 1));
            // 0.9.10: карточки с картинкой — снимок камеры или карточка по теме
            int cols = w >= 520 ? 2 : 1, cw = (w - (cols - 1) * 6) / cols, ph = 54, pw = 96, ch = ph + 8;
            for (int i = start, k = 0; i < feed.size() && y + ch < height - 18; i++, k++) {
                NbtCompound c = feed.getCompound(i);
                boolean mine = c.getString("p").equals(me);
                int cx = 12 + (k % cols) * (cw + 6);
                Ui.slant(ctx, cx, y, cw, ch, 3, mine ? 0xB0880E4F : 0x90161615);
                FeedImages.draw(ctx, c, cx + 5, y + 4, pw, ph);
                int tx = cx + pw + 11, tw = cw - pw - 18;
                ctx.drawText(textRenderer, c.getString("a"), tx, y + 6, 0xFFF48FB1, false);
                // текст поста — до трёх строк
                var lines = textRenderer.wrapLines(net.minecraft.text.Text.literal(c.getString("t")), tw);
                for (int li = 0; li < Math.min(3, lines.size()); li++) ctx.drawText(textRenderer, lines.get(li), tx, y + 18 + li * 10, Ui.WHITE, false);
                Ui.small(ctx, textRenderer, "♥ " + c.getInt("l") + " · день " + c.getLong("d"), cx + cw - 8, y + ch - 10, 0.75f, 0xFFB9B7B1, 2);
                if (k % cols == cols - 1) y += ch + 4;
            }
        }

        @Override
        public boolean mouseScrolled(double mx, double my, double hx, double vy) {
            scroll = Math.max(0, scroll + (vy > 0 ? -1 : 1));
            return true;
        }
    }

    // ====================================================================== «Уют»
    public static class Home extends App {
        public Home() {
            super("Уют", 0xFFA0785A);
            NovogradClient.send("home_state");
        }

        @Override
        void body(DrawContext ctx, int y, int w) {
            NbtCompound s = home;
            String here = s.getString("here");
            Ui.small(ctx, textRenderer, s.getInt("homes") == 0 ? "Жилья нет — телефон → «Дом»" : here.isEmpty() ? "Ты не дома: мебель ставится только дома" : "Дома: " + here + " · мебели " + s.getInt("placed"),
                    12, y, 0.85f, here.isEmpty() ? Ui.GOLD : 0xFFA5D6A7, 0);
            y += 12;
            int[] pr = s.getIntArray("prices");
            int bw = (w - 8) / 3;
            boolean partyOn = s.getBoolean("party");
            button(ctx, 12, y, bw, 18, partyOn ? "ВЕЧЕРИНКА ИДЁТ" : "ВЕЧЕРИНКА $" + (pr.length > 0 ? pr[0] : 300), 0xFFAB47BC, !here.isEmpty() && !partyOn, () -> send("home_party"));
            String dog = s.getString("dog"), cat = s.getString("cat");
            button(ctx, 16 + bw, y, bw, 18, dog.isEmpty() ? "СОБАКА $" + (pr.length > 1 ? pr[1] : 400) : "СОБАКА: " + dog.toUpperCase(), 0xFF8D6E63, dog.isEmpty(), () -> send("home_pet", "kind", "dog"));
            button(ctx, 20 + 2 * bw, y, bw, 18, cat.isEmpty() ? "КОШКА $" + (pr.length > 2 ? pr[2] : 250) : "КОШКА: " + cat.toUpperCase(), 0xFF8D6E63, cat.isEmpty(), () -> send("home_pet", "kind", "cat"));
            y += 22;
            if (!dog.isEmpty() || !cat.isEmpty()) {
                button(ctx, 12, y, bw, 14, "ПИТОМЕЦ — КО МНЕ", 0xFF5D4037, true, () -> send("home_petcall", "home", false));
                button(ctx, 16 + bw, y, bw, 14, "ПИТОМЕЦ — ДОМОЙ", 0xFF5D4037, true, () -> send("home_petcall", "home", true));
                y += 18;
            }
            Ui.small(ctx, textRenderer, "МЕБЕЛЬ · ПКМ — поставить, Z/X — повернуть, удар — убрать", 12, y + 2, 0.8f, 0xFF90A4AE, 0);
            y += 12;
            NbtList cat2 = s.getList("catalog", NbtElement.COMPOUND_TYPE);
            int cols = Math.max(2, w / 120), cw = (w - (cols - 1) * 4) / cols;
            for (int i = 0; i < cat2.size(); i++) {
                NbtCompound c = cat2.getCompound(i);
                int cx = 12 + (i % cols) * (cw + 4), cy = y + (i / cols) * 22;
                if (cy > height - 30) break;
                int idx = i;
                // плитка: иконка предмета (объёмная модель) + название и цена
                button(ctx, cx, cy, cw, 20, "", 0xFF6D4C41, s.getInt("homes") > 0, () -> send("home_buy", "i", idx));
                var item = net.minecraft.registry.Registries.ITEM.get(net.minecraft.util.Identifier.of(c.getString("id")));
                ctx.drawItem(new net.minecraft.item.ItemStack(item), cx + 4, cy + 2);
                Ui.small(ctx, textRenderer, c.getString("name"), cx + 23, cy + 3, 0.8f, 0xFFFFFFFF, 0);
                Ui.small(ctx, textRenderer, "$" + c.getInt("price"), cx + 23, cy + 12, 0.75f, 0xFFFFD54F, 0);
            }
        }
    }
}
