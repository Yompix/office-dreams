package com.yompix.novograd.client.screen;

import com.yompix.novograd.client.NovogradClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import org.lwjgl.glfw.GLFW;

/**
 * Телефон → «Авто» и экраны у меток: штрафы ГИБДД, штрафстоянка, бензин, авиакасса, яхт-клуб, тотализатор, аукцион угнанных машин.
 * Данные приходят с сервера (drv_open / drv_state), деньги проверяет сервер.
 */
public class DriveScreen extends LifeScreen {
    public static NbtCompound state = new NbtCompound();
    /** Торги аукциона: ставки покупателей, показываются по одной. */
    private static NbtCompound auction;
    private static long auctionAt;

    private String tab;
    private final boolean phone;
    private int driver = -1;

    /** Из телефона: вкладки «Штрафы», «Штрафстоянка», «Машина», «Развлечения». */
    public DriveScreen() {
        this("fines", true);
        NbtCompound n = new NbtCompound();
        n.putString("tab", "fines");
        NovogradClient.send("drv_state", n);
    }

    public DriveScreen(String tab, boolean phone) {
        super("Авто");
        this.tab = tab;
        this.phone = phone;
    }

    public static void open(NbtCompound n) {
        state = n;
        mcStatic().setScreen(new DriveScreen(n.getString("open"), false));
    }

    public static void auction(NbtCompound n) {
        auction = n;
        auctionAt = System.currentTimeMillis();
        if (!(mcStatic().currentScreen instanceof DriveScreen)) mcStatic().setScreen(new DriveScreen("auction", false));
        else ((DriveScreen) mcStatic().currentScreen).tab = "auction";
    }

    private static net.minecraft.client.MinecraftClient mcStatic() {
        return net.minecraft.client.MinecraftClient.getInstance();
    }

    @Override
    protected void draw(DrawContext ctx, float delta) {
        int w = Math.min(420, width - 30), h = Math.min(270, height - 30);
        int x = width / 2 - w / 2, y = height / 2 - h / 2;
        int accent = switch (tab) {
            case "air" -> 0xFF4FC3F7;
            case "boat" -> 0xFF26C6DA;
            case "bets" -> 0xFFFFC107;
            case "auction" -> 0xFFFF5252;
            case "impound" -> 0xFFFF9800;
            default -> 0xFF7CFF8A;
        };
        panel(ctx, x, y, w, h, 0xF0101418, 0xF0080A0D);
        String head = switch (tab) {
            case "air" -> "✈ АВИАКАССА";
            case "boat" -> "⚓ ЯХТ-КЛУБ «РИВЬЕРА»";
            case "bets" -> "⚑ ТОТАЛИЗАТОР";
            case "auction" -> "⚒ АУКЦИОН УГНАННЫХ МАШИН";
            case "impound" -> "⚠ ШТРАФСТОЯНКА";
            default -> "АВТО";
        };
        title(ctx, head, x + 10, y + 8, accent);
        textRight(ctx, "$" + state.getInt("money") + (state.getInt("dirty") > 0 ? "  · грязными $" + state.getInt("dirty") : ""), x + w - 10, y + 10, 0xFF7CFF8A);
        int cy = y + 30;
        if (phone) {
            String[][] tabs = {{"fines", "Штрафы"}, {"impound", "Стоянка"}, {"car", "Машина"}, {"fun", "Где что"}};
            for (int i = 0; i < tabs.length; i++) {
                String id = tabs[i][0];
                button(ctx, x + 10 + i * 78, cy, 72, 14, tabs[i][1], tab.equals(id) ? 0xFF2E7D32 : 0xFF37474F, true, () -> tab = id);
            }
            cy += 22;
        }
        switch (tab) {
            case "fines" -> fines(ctx, x, cy, w);
            case "impound" -> impound(ctx, x, cy, w);
            case "car" -> car(ctx, x, cy, w);
            case "fun" -> fun(ctx, x, cy, w);
            case "air" -> air(ctx, x, cy, w);
            case "boat" -> boat(ctx, x, cy, w);
            case "bets" -> bets(ctx, x, cy, w);
            case "auction" -> auctionTab(ctx, x, cy, w);
            default -> {}
        }
        center(ctx, "Esc — закрыть", x + w / 2, y + h - 12, 0xFF607D8B);
    }

    private void textRight(DrawContext ctx, String s, int rx, int y, int color) {
        ctx.drawText(textRenderer, s, rx - textRenderer.getWidth(s), y, color, true);
    }

    // ------------------------------------------------------------------ штрафы
    private void fines(DrawContext ctx, int x, int cy, int w) {
        NbtList l = state.getList("fines", NbtElement.COMPOUND_TYPE);
        int total = state.getInt("finesTotal");
        long day = state.getLong("day");
        if (l.isEmpty()) {
            bigText(ctx, "Штрафов нет", x + w / 2, cy + 20, 2f, 0xFF7CFF8A);
            center(ctx, "Камеры на трассах: 60 км/ч (в городе на магистралях — 50).", x + w / 2, cy + 50, 0xFFB0BEC5);
            center(ctx, "Не оплатишь штраф за 2 игровых дня — полиция объявит розыск.", x + w / 2, cy + 62, 0xFFB0BEC5);
            return;
        }
        for (int i = 0; i < Math.min(l.size(), 8); i++) {
            NbtCompound f = l.getCompound(l.size() - 1 - i);
            int ry = cy + i * 18;
            boolean late = day - f.getLong("day") >= 2;
            ctx.fill(x + 10, ry, x + w - 10, ry + 15, late ? 0x60501010 : 0x40202830);
            ctx.drawText(textRenderer, "◉ " + f.getString("where"), x + 16, ry + 4, 0xFFFFFFFF, false);
            textRight(ctx, (late ? "ПРОСРОЧЕН · " : "день " + f.getLong("day") + " · ") + "$" + f.getInt("amt"), x + w - 16, ry + 4, late ? 0xFFFF6E6E : 0xFFFFD54F);
        }
        if (l.size() > 8) center(ctx, "…и ещё " + (l.size() - 8), x + w / 2, cy + 8 * 18 + 2, 0xFF90A4AE);
        int by = cy + Math.min(l.size(), 8) * 18 + 16;
        button(ctx, x + w / 2 - 80, by, 160, 18, "Оплатить всё: $" + total, 0xFF2E7D32, state.getInt("money") >= total, () -> NovogradClient.send("drv_payfines"));
    }

    // ------------------------------------------------------------------ штрафстоянка
    private void impound(DrawContext ctx, int x, int cy, int w) {
        NbtList l = state.getList("impound", NbtElement.COMPOUND_TYPE);
        int fee = state.getInt("impoundFee");
        if (l.isEmpty()) {
            bigText(ctx, "Твоих машин тут нет", x + w / 2, cy + 20, 1.6f, 0xFFFFB74D);
            wrap(ctx, "Если бросить свою машину посреди дороги, через 5 минут её увезёт эвакуатор. Забрать — здесь, у полицейского участка, за $" + fee + ".", x + 20, cy + 50, w - 40, 0xFFB0BEC5);
            return;
        }
        for (int i = 0; i < Math.min(l.size(), 8); i++) {
            NbtCompound c = l.getCompound(i);
            int ry = cy + i * 22;
            ctx.fill(x + 10, ry, x + w - 10, ry + 19, 0x40202830);
            ctx.drawText(textRenderer, "▶ " + c.getString("name"), x + 16, ry + 6, 0xFFFFFFFF, false);
            final int idx = i;
            if (!phone) button(ctx, x + w - 130, ry + 2, 114, 15, "Забрать $" + fee, 0xFFF9A825, state.getInt("money") >= fee, () -> {
                NbtCompound n = new NbtCompound();
                n.putInt("i", idx);
                NovogradClient.send("drv_impound", n);
            });
        }
        if (phone) center(ctx, "Забрать — на штрафстоянке у полицейского участка (значок ⚠ на карте)", x + w / 2, cy + Math.min(l.size(), 8) * 22 + 8, 0xFFB0BEC5);
    }

    // ------------------------------------------------------------------ машина и бензин
    private void car(DrawContext ctx, int x, int cy, int w) {
        if (state.contains("fuel")) {
            int f = state.getInt("fuel");
            bigText(ctx, "⛽ " + f + "%", x + w / 2, cy + 6, 2f, f > 25 ? 0xFF7CFF8A : 0xFFFF6E6E);
        } else bigText(ctx, "Ты не за рулём", x + w / 2, cy + 6, 1.6f, 0xFFB0BEC5);
        int ty = cy + 36;
        ty += wrap(ctx, "• Ближайшая заправка: " + state.getString("station") + ". Заедь под навес к колонкам и встань — бак наполнится ($50 за полный).", x + 16, ty, w - 32, 0xFFE0E0E0) + 3;
        ty += wrap(ctx, "• Полного бака хватает на 15–20 минут езды. Пустой бак — мотор глохнет, машина катится и встаёт.", x + 16, ty, w - 32, 0xFFE0E0E0) + 3;
        ty += wrap(ctx, "• Кассира на заправке можно ограбить, как продавца в магазине (целься в него).", x + 16, ty, w - 32, 0xFFE0E0E0) + 3;
        ty += wrap(ctx, "• Дождь — шины держат хуже: тормози раньше. Туман — включи фары (L). Песок и трава замедляют; багги там как дома.", x + 16, ty, w - 32, 0xFFE0E0E0) + 3;
        wrap(ctx, "• Сегодня на аукционе угнанных машин в цене: " + state.getString("hot") + ".", x + 16, ty, w - 32, 0xFFFF8A80);
    }

    private void fun(DrawContext ctx, int x, int cy, int w) {
        String[] lines = {
                "✈ Авиакассы: аэропорт Новограда, гидроаэродром на пляже Ривьеры, полоса в Пустоши — быстрые перелёты.",
                "⚓ Яхт-клуб на пристани Ривьеры: гонки катеров с призами, прокат и покупка катера.",
                "⚑ Тотализатор у старта северного кольца: ставь на уличных гонщиков.",
                "⚒ Аукцион угнанных машин — у разборки в промзоне. Деньги грязные, а кто-то может настучать.",
                "⚠ Штрафстоянка — у полицейского участка.",
                "☀ В Пустоши и горах у дорог стоят багги — для бездорожья.",
        };
        int ty = cy + 4;
        for (String s : lines) ty += wrap(ctx, s, x + 16, ty, w - 32, 0xFFE0E0E0) + 5;
    }

    // ------------------------------------------------------------------ авиакасса
    private void air(DrawContext ctx, int x, int cy, int w) {
        String here = state.getString("here");
        NbtList ports = state.getList("ports", NbtElement.COMPOUND_TYPE);
        center(ctx, "«Новоград-Авиа» · куда летим?", x + w / 2, cy, 0xFFB3E5FC);
        int i = 0;
        for (NbtElement e : ports) {
            NbtCompound c = (NbtCompound) e;
            if (c.getString("id").equals(here)) continue;
            int ry = cy + 18 + i++ * 34;
            ctx.fill(x + 20, ry, x + w - 20, ry + 28, 0x40203040);
            ctx.drawText(textRenderer, "✈ " + c.getString("name"), x + 28, ry + 4, 0xFFFFFFFF, true);
            ctx.drawText(textRenderer, "перелёт ~1 минута игрового времени", x + 28, ry + 16, 0xFF90A4AE, false);
            String id = c.getString("id");
            button(ctx, x + w - 130, ry + 6, 100, 16, "Билет $" + c.getInt("price"), 0xFF0288D1, state.getInt("money") >= c.getInt("price") && state.getInt("stars") == 0, () -> {
                NbtCompound n = new NbtCompound();
                n.putString("to", id);
                NovogradClient.send("drv_fly", n);
                close();
            });
        }
        if (state.getInt("stars") > 0) center(ctx, "С розыском на борт не пускают", x + w / 2, cy + 18 + i * 34 + 4, 0xFFFF6E6E);
    }

    // ------------------------------------------------------------------ яхт-клуб
    private void boat(DrawContext ctx, int x, int cy, int w) {
        int lw = w / 2 - 20;
        button(ctx, x + 14, cy, lw, 20, "Гонка катеров · взнос $" + state.getInt("fee"), 0xFF00838F, state.getInt("money") >= state.getInt("fee"), () -> boatAct("race"));
        button(ctx, x + 14, cy + 26, lw, 20, "Прокат катера 10 мин · $" + state.getInt("rent"), 0xFF1565C0, state.getInt("money") >= state.getInt("rent"), () -> boatAct("rent"));
        button(ctx, x + 14, cy + 52, lw, 20, "Купить катер · $" + state.getInt("buy"), 0xFF6A1B9A, state.getInt("money") >= state.getInt("buy") && state.getInt("act") >= 1, () -> boatAct("buy"));
        wrap(ctx, "Пройди все буйки (столбы света) вдоль берега и вернись к пристани за 4 минуты. Обгони капитанов клуба — приз $300 / $700 / $1500.", x + 14, cy + 80, lw, 0xFFB0BEC5);
        int bx = x + w / 2 + 6;
        ctx.drawText(textRenderer, "РЕКОРДЫ КЛУБА", bx, cy + 4, 0xFF80DEEA, true);
        NbtList b = state.getList("board", NbtElement.COMPOUND_TYPE);
        java.util.List<NbtCompound> rows = new java.util.ArrayList<>();
        for (NbtElement e : b) rows.add((NbtCompound) e);
        rows.sort(java.util.Comparator.comparingInt(c -> c.getInt("t")));
        for (int i = 0; i < Math.min(rows.size(), 8); i++) {
            NbtCompound c = rows.get(i);
            int ry = cy + 18 + i * 13;
            int col = c.getBoolean("me") ? 0xFFFFEB3B : 0xFFFFFFFF;
            ctx.drawText(textRenderer, (i + 1) + ". " + c.getString("name"), bx, ry, col, false);
            textRight(ctx, time(c.getInt("t")), x + w - 16, ry, col);
        }
    }

    private static String time(int t) {
        int s = t / 20;
        return s / 60 + ":" + String.format("%02d", s % 60) + "." + (t % 20) / 2;
    }

    private void boatAct(String what) {
        NbtCompound n = new NbtCompound();
        n.putString("what", what);
        NovogradClient.send("drv_boat", n);
        close();
    }

    // ------------------------------------------------------------------ тотализатор
    private void bets(DrawContext ctx, int x, int cy, int w) {
        NbtList l = state.getList("drivers", NbtElement.COMPOUND_TYPE);
        int stage = state.getInt("stage");
        boolean mine = state.contains("myBet");
        center(ctx, stage == 0 ? (mine ? "Ставка принята — старт скоро" : "Выбери гонщика и ставку · заезд по северному и восточному кольцу")
                : "Заезд идёт — ставки закрыты", x + w / 2, cy, stage == 0 ? 0xFFFFE082 : 0xFFFF8A65);
        for (int i = 0; i < l.size(); i++) {
            NbtCompound c = l.getCompound(i);
            int ry = cy + 14 + i * 30;
            boolean sel = driver == i || mine && state.getInt("myDriver") == i;
            ctx.fill(x + 14, ry, x + w - 14, ry + 26, sel ? 0x70806010 : 0x40202830);
            ctx.drawText(textRenderer, c.getString("name"), x + 22, ry + 4, 0xFFFFFFFF, true);
            ctx.drawText(textRenderer, c.getString("car"), x + 22, ry + 15, 0xFF90A4AE, false);
            Ui_big(ctx, "×" + c.getDouble("odds"), x + w - 110, ry + 7, 0xFFFFD54F);
            final int idx = i;
            if (stage == 0 && !mine) area(x + 14, ry, w - 28, 26, () -> driver = idx);
        }
        int by = cy + 14 + l.size() * 30 + 6;
        if (stage == 0 && !mine) {
            NbtList bets = state.getList("bets", NbtElement.COMPOUND_TYPE);
            int bw = 70;
            int bx0 = x + w / 2 - (bets.size() * (bw + 6)) / 2;
            for (int i = 0; i < bets.size(); i++) {
                int v = bets.getCompound(i).getInt("v");
                button(ctx, bx0 + i * (bw + 6), by, bw, 18, "$" + v, 0xFFF9A825, driver >= 0 && state.getInt("money") >= v, () -> {
                    NbtCompound n = new NbtCompound();
                    n.putInt("driver", driver);
                    n.putInt("amount", v);
                    NovogradClient.send("drv_bet", n);
                });
            }
            if (driver < 0) center(ctx, "Нажми на гонщика", x + w / 2, by + 24, 0xFF90A4AE);
        } else if (mine) center(ctx, "Твоя ставка: $" + state.getInt("myBet") + " · итоги придут СМС", x + w / 2, by + 4, 0xFFFFE082);
    }

    private void Ui_big(DrawContext ctx, String s, int x, int y, int color) {
        var m = ctx.getMatrices();
        m.push();
        m.translate(x, y, 0);
        m.scale(1.5f, 1.5f, 1);
        ctx.drawText(textRenderer, s, 0, 0, color, true);
        m.pop();
    }

    // ------------------------------------------------------------------ аукцион
    private void auctionTab(DrawContext ctx, int x, int cy, int w) {
        if (auction != null) {
            NbtList bids = auction.getList("bids", NbtElement.COMPOUND_TYPE);
            float t = (System.currentTimeMillis() - auctionAt) / 1000f;
            int shown = Math.min(bids.size(), (int) (t / 1.6f) + 1);
            center(ctx, "Лот: " + auction.getString("car"), x + w / 2, cy, 0xFFFFFFFF);
            for (int i = 0; i < shown; i++) {
                NbtCompound b = bids.getCompound(i);
                int ry = cy + 16 + i * 16;
                boolean last = i == shown - 1;
                ctx.fill(x + 40, ry, x + w - 40, ry + 13, last ? 0x60803030 : 0x30202830);
                ctx.drawText(textRenderer, "• " + b.getString("who"), x + 48, ry + 3, 0xFFFFFFFF, false);
                textRight(ctx, "$" + b.getInt("v"), x + w - 48, ry + 3, last ? 0xFFFFEB3B : 0xFFB0BEC5);
            }
            if (shown >= bids.size() && t > bids.size() * 1.6f + 0.6f) {
                float k = Math.min(1, (t - bids.size() * 1.6f - 0.6f) * 2);
                bigText(ctx, "ПРОДАНО! $" + auction.getInt("final"), x + w / 2, cy + 20 + bids.size() * 16 + 10, 1.5f + k * 0.5f, 0xFF7CFF8A);
            } else if ((int) (t * 3) % 2 == 0) center(ctx, "…кто больше?", x + w / 2, cy + 20 + shown * 16, 0xFFFF8A80);
            return;
        }
        center(ctx, "Сегодня в цене: " + state.getString("hot") + " (+50%)", x + w / 2, cy, 0xFFFF8A80);
        if (state.contains("cd")) {
            center(ctx, "Скупщик: «Не так часто. Приезжай через " + state.getInt("cd") + " с.»", x + w / 2, cy + 30, 0xFFFFB74D);
            return;
        }
        if (!state.contains("car")) {
            bigText(ctx, "Пригони чужую машину в ворота", x + w / 2, cy + 26, 1.3f, 0xFFFFFFFF);
            wrap(ctx, "Скупщики «из своих» торгуются за каждую угнанную машину. Редкие (суперкар, классика, лоурайдер, лимузин) — дороже, целые — дороже. Платят грязными. "
                    + "Чем ценнее машина, тем выше шанс, что кто-то настучит копам.", x + 30, cy + 52, w - 60, 0xFFB0BEC5);
            return;
        }
        bigText(ctx, state.getString("car"), x + w / 2, cy + 22, 1.6f, 0xFFFFFFFF);
        center(ctx, "состояние " + state.getInt("hp") + "% · оценка ~$" + state.getInt("est") + " · риск наводки " + state.getInt("risk") + "%", x + w / 2, cy + 46, 0xFFB0BEC5);
        if (state.getBoolean("ok"))
            button(ctx, x + w / 2 - 90, cy + 66, 180, 20, "Выставить на торги", 0xFFC62828, true, () -> NovogradClient.send("drv_sell"));
        else center(ctx, "Скупщик: «Эту не возьмём — своя или засвеченная»", x + w / 2, cy + 70, 0xFFFF6E6E);
    }

    @Override
    public void tick() {
        // торги закончились — через пару секунд закрываем
        if (auction != null && (System.currentTimeMillis() - auctionAt) / 1000f > auction.getList("bids", NbtElement.COMPOUND_TYPE).size() * 1.6f + 4) {
            auction = null;
            close();
        }
    }

    @Override
    public void close() {
        auction = null;
        super.close();
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
