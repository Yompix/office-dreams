package com.yompix.novograd.client.screen;

import com.yompix.novograd.client.NovogradClient;
import com.yompix.novograd.client.UnderworldClient;
import com.yompix.novograd.client.ui.Ui;
import com.yompix.novograd.gta.BigHeists;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

/**
 * Телефон → «Дела»: тёмная сторона Новограда (gta/Underworld).
 *  • «Ограбления» — доска планирования больших дел: разведка, машина, подход, снаряжение, команда, старт;
 *  • «Заказы» — заказные убийства с анонимного номера;
 *  • «Контрабанда» — партии Рыбака: катер из порта или самолёт с аэродрома.
 */
public class DarkJobsScreen extends LifeScreen {
    private static final String[] TABS = {"Ограбления", "Заказы", "Контрабанда"};
    private static final int[] TAB_COLORS = {0xFFFFC428, 0xFFFF3B4A, 0xFF28E6FF};
    private static int tab, heist;

    public DarkJobsScreen() {
        this("");
    }

    public DarkJobsScreen(String open) {
        super("Дела");
        switch (open) {
            case "heist" -> tab = 0;
            case "contract" -> tab = 1;
            case "smuggle" -> tab = 2;
            default -> {}
        }
        NovogradClient.send("uw_state");
    }

    private static void bh(String id, String a, int v) {
        NbtCompound n = new NbtCompound();
        n.putString("h", id);
        n.putString("a", a);
        n.putInt("v", v);
        UnderworldClient.send("uw_bh", n);
    }

    private static void act(String type, String a, int v) {
        NbtCompound n = new NbtCompound();
        n.putString("a", a);
        n.putInt("v", v);
        UnderworldClient.send(type, n);
    }

    @Override
    protected void draw(DrawContext ctx, float delta) {
        float an = openAnim();
        int w = Math.min(410, width - 12), h = Math.min(236, height - 6);
        int x = width / 2 - w / 2, y = (int) (height / 2 - h / 2 + (1 - an) * 24);
        panel(ctx, x, y, w, h, 0xFF2A0610, 0xFF05030A);
        title(ctx, "ТЁМНЫЕ ДЕЛА", x + 8, y + 7, Ui.RED);
        var d = NovogradClient.data;
        Ui.small(ctx, textRenderer, "на карте $" + d.money + " · грязными $" + d.dirty, x + w - 8, y + 10, 0.7f, Ui.MONEY, 2);
        int tw = (w - 16 - 8) / 3;
        for (int i = 0; i < 3; i++) {
            final int k = i;
            button(ctx, x + 8 + i * (tw + 4), y + 24, tw, 13, (tab == i ? "▶ " : "") + TABS[i], tab == i ? TAB_COLORS[i] : 0xFF4A3A55, true, () -> tab = k);
        }
        NbtCompound s = UnderworldClient.state;
        int act = s.contains("act") ? s.getInt("act") : com.yompix.novograd.gta.Economy.act(d);
        int cy = y + 42;
        switch (tab) {
            case 0 -> heists(ctx, s, act, x, cy, w, y + h - cy - 6);
            case 1 -> contracts(ctx, s.getCompound("contracts"), act, x, cy, w);
            default -> smuggle(ctx, s.getCompound("smuggle"), act, x, cy, w);
        }
    }

    // ---------------------------------------------------------------- ограбления
    private void heists(DrawContext ctx, NbtCompound s, int act, int x, int y, int w, int h) {
        if (act < BigHeists.ACT) {
            wrap(ctx, "Седой: «Большие дела — не для новичков. Когда о тебе заговорит весь город (акт IV), приходи. "
                    + "Три цели: порт, Сити-Банк и казино „Корона“. Доска планирования будет у тебя дома.»", x + 14, y + 10, w - 28, 0xFFB8A8B8);
            return;
        }
        NbtList list = s.getList("heists", NbtElement.COMPOUND_TYPE);
        if (list.isEmpty()) {
            Ui.small(ctx, textRenderer, "загрузка…", x + w / 2f, y + 20, 0.8f, 0xFF8A8598, 1);
            return;
        }
        heist = Math.min(heist, list.size() - 1);
        boolean board = s.getBoolean("board");
        // слева — три цели
        for (int i = 0; i < list.size(); i++) {
            NbtCompound c = list.getCompound(i);
            final int k = i;
            boolean sel = heist == i;
            String mark = c.getBoolean("running") ? " ●" : c.getBoolean("ready") ? " ✔" : "";
            button(ctx, x + 8, y + i * 17, 84, 14, c.getString("name").replace("Казино ", "") + mark, sel ? Ui.GOLD : 0xFF5A4630, true, () -> heist = k);
        }
        NbtCompound c = list.getCompound(heist);
        String id = c.getString("id");
        int iy = y + 54;
        Ui.small(ctx, textRenderer, "Добыча $" + c.getInt("min") / 1000 + "–" + c.getInt("max") / 1000 + " тыс.", x + 10, iy, 0.6f, Ui.MONEY, 0);
        Ui.small(ctx, textRenderer, "Охрана " + c.getInt("guards") + " · камер " + c.getInt("cams"), x + 10, iy + 8, 0.6f, 0xFFB8A8B8, 0);
        if (c.getInt("done") > 0) Ui.small(ctx, textRenderer, "Взято раз: " + c.getInt("done"), x + 10, iy + 16, 0.6f, 0xFFB8A8B8, 0);
        if (c.getInt("cd") > 0) Ui.small(ctx, textRenderer, "Шум уляжется: " + c.getInt("cd") + " дн.", x + 10, iy + 24, 0.6f, Ui.RED, 0);
        wrap(ctx, board ? "§aТы у доски планирования" : "§7Доска — у тебя дома или в убежище. Подготовку можно и отсюда, старт — только у доски",
                x + 10, iy + 36, 84, 0xFFB8A8B8);
        // справа — доска
        int bx = x + 100, bw = w - 108;
        ctx.fill(bx - 4, y - 2, bx + bw + 2, y + h, 0x30FFC428);
        int ry = y;
        String prep = c.getString("prep");
        boolean running = c.getBoolean("running");
        boolean loud = c.getInt("loud") == 1;
        ry = step(ctx, bx, ry, bw, "1. Разведка", "сфотографируй охрану (G → Камера)", c.getInt("scout") == 1,
                prep.equals("scout") ? "идёт…" : "Начать", !running && !prep.equals("scout"), () -> bh(id, "scout", 0));
        ry = step(ctx, bx, ry, bw, "2. Машина для отхода", "угони и отгони на точку", c.getInt("car") == 1,
                prep.equals("car") ? "идёт…" : "Угнать", !running && !prep.equals("car"), () -> bh(id, "car", 0));
        ctx.drawText(textRenderer, "3. Подход", bx, ry + 3, 0xFFFFFFFF, false);
        int ab = (bw - 70) / 2;
        button(ctx, bx + 66, ry, ab, 13, "ТИХО", !loud ? Ui.CYAN : 0xFF30485A, !running, () -> bh(id, "approach", 0));
        button(ctx, bx + 70 + ab, ry, ab, 13, "ГРОМКО", loud ? Ui.RED : 0xFF5A3038, !running, () -> bh(id, "approach", 1));
        ry += 16;
        boolean gear = c.getInt(loud ? "gearL" : "gearS") == 1;
        ry = step(ctx, bx, ry, bw, "4. Снаряжение", loud ? "взрывчатка, бронежилет, гранаты" : "форма охраны, ствол с глушителем", gear,
                "$" + BigHeists.GEAR_PRICE, !running, () -> bh(id, "gear", 0));
        Ui.small(ctx, textRenderer, "КОМАНДА — лучше профи: дороже доля, выше шанс", bx, ry + 1, 0.6f, Ui.GOLD, 0);
        ry += 8;
        int cut = 0;
        int lw = 46, tbw = (bw - lw - 6) / 3;
        for (int r = 0; r < 3; r++) {
            int sel = c.getInt(BigHeists.ROLES[r]) - 1;
            ctx.drawText(textRenderer, BigHeists.ROLE_NAMES[r], bx, ry + 3, sel >= 0 ? 0xFFFFFFFF : 0xFFFF8A8A, false);
            for (int t = 0; t < 3; t++) {
                final int role = r, tier = t;
                button(ctx, bx + lw + t * (tbw + 3), ry, tbw, 12, BigHeists.CREW_SHORT[r][t] + " " + BigHeists.CUT[t] + "%",
                        sel == t ? new int[]{0xFF62E57E, Ui.CYAN, Ui.PINK}[r] : 0xFF3A3448, !running, () -> bh(id, BigHeists.ROLES[role], tier));
            }
            String info = sel >= 0 ? BigHeists.CREW[r][sel] + ": " + BigHeists.CREW_INFO[r][sel] : "не выбран";
            Ui.small(ctx, textRenderer, info, bx + lw, ry + 13, 0.55f, 0xFFB8A8B8, 0);
            if (sel >= 0) cut += BigHeists.CUT[sel];
            ry += 20;
        }
        int my = (c.getInt("min") + c.getInt("max")) / 2 * (100 - cut) / 100;
        Ui.small(ctx, textRenderer, "Доля команды " + cut + "% · тебе ≈ $" + my / 1000 + " тыс. (грязными)", bx, ry + 1, 0.6f, Ui.MONEY, 0);
        ry += 9;
        if (running) {
            button(ctx, bx, ry, bw / 2 - 2, 14, "Идёт дело…", Ui.GOLD, false, () -> {});
            button(ctx, bx + bw / 2 + 2, ry, bw / 2 - 2, 14, "Отменить", 0xFF8A3040, true, () -> bh(id, "cancel", 0));
        } else {
            boolean ok = c.getBoolean("ready") && board && c.getInt("cd") == 0;
            button(ctx, bx, ry, prep.isEmpty() ? bw : bw / 2 - 2, 14, "НАЧАТЬ ОГРАБЛЕНИЕ", Ui.RED, ok, () -> {
                bh(id, "start", 0);
                close();
            });
            if (!prep.isEmpty()) button(ctx, bx + bw / 2 + 2, ry, bw / 2 - 2, 14, "Бросить подготовку", 0xFF8A3040, true, () -> bh(id, "cancel", 0));
        }
    }

    private int step(DrawContext ctx, int x, int y, int w, String name, String sub, boolean done, String btn, boolean enabled, Runnable go) {
        ctx.drawText(textRenderer, (done ? "§a✔ " : "§7○ ") + name, x, y, 0xFFFFFFFF, false);
        Ui.small(ctx, textRenderer, sub, x + 10, y + 9, 0.55f, 0xFF9A90A8, 0);
        if (!done) button(ctx, x + w - 62, y, 62, 13, btn, Ui.GOLD, enabled, () -> {
            go.run();
            if (!btn.startsWith("$")) close();
        });
        else Ui.small(ctx, textRenderer, "готово", x + w - 4, y + 3, 0.7f, Ui.MONEY, 2);
        return y + 17;
    }

    // ---------------------------------------------------------------- заказы
    private void contracts(DrawContext ctx, NbtCompound s, int act, int x, int y, int w) {
        if (act < 1) {
            wrap(ctx, "Неизвестные номера тебе пока не пишут. Пройди немного сюжета (акт II).", x + 14, y + 10, w - 28, 0xFFB8A8B8);
            return;
        }
        Ui.small(ctx, textRenderer, "Выполнено заказов: " + s.getInt("done"), x + w - 10, y - 1, 0.6f, 0xFF9A90A8, 2);
        if (s.contains("active")) {
            NbtCompound c = s.getCompound("active");
            int cy = card(ctx, x + 8, y + 6, w - 16, c, Ui.RED, true);
            Ui.small(ctx, textRenderer, "Сейчас цель: " + c.getString("now") + " · осталось " + c.getInt("left") / 60 + " мин", x + 14, cy, 0.7f, Ui.GOLD, 0);
            button(ctx, x + w - 98, cy + 10, 88, 14, "Отказаться", 0xFF8A3040, true, () -> act("uw_ct", "cancel", 0));
            wrap(ctx, "§7Метка на радаре ведёт к цели. Бонусы: способ — как просили; без свидетелей — никто из прохожих не видел; без розыска — через 3 секунды после дела нет звёзд.",
                    x + 14, cy + 30, w - 28, 0xFF9A90A8);
            return;
        }
        NbtList l = s.getList("offers", NbtElement.COMPOUND_TYPE);
        if (l.isEmpty()) {
            wrap(ctx, "Пока тихо. Заказы приходят СМС с неизвестного номера.", x + 14, y + 10, w - 28, 0xFFB8A8B8);
            button(ctx, x + w / 2 - 60, y + 34, 120, 14, "Ждать звонка", Ui.RED, true, () -> act("uw_ct", "refresh", 0));
            return;
        }
        int cy = y + 4;
        for (int i = 0; i < l.size(); i++) {
            final int k = i;
            NbtCompound c = l.getCompound(i);
            int top = cy;
            cy = card(ctx, x + 8, cy, w - 16, c, 0xFF8A2030, false);
            button(ctx, x + w - 70, top + 2, 60, 14, "Взять", Ui.RED, true, () -> {
                act("uw_ct", "take", k);
                close();
            });
            cy += 4;
        }
    }

    private int card(DrawContext ctx, int x, int y, int w, NbtCompound c, int color, boolean big) {
        ctx.fill(x, y, x + w, y + (big ? 44 : 50), 0x30FFFFFF);
        ctx.fill(x, y, x + 2, y + (big ? 44 : 50), color);
        ctx.drawText(textRenderer, "§c" + c.getString("title") + " §f" + c.getString("name"), x + 6, y + 3, 0xFFFFFFFF, false);
        Ui.small(ctx, textRenderer, c.getString("why"), x + 6, y + 13, 0.6f, 0xFFD0C0C8, 0);
        Ui.small(ctx, textRenderer, "Днём: " + c.getString("day") + " · ночью: " + c.getString("night") + (c.getInt("guards") > 0 ? " · охрана " + c.getInt("guards") : ""),
                x + 6, y + 21, 0.6f, 0xFFB8A8B8, 0);
        Ui.small(ctx, textRenderer, "Способ: " + c.getString("method") + (c.getInt("bonus") > 0 ? " (+" + c.getInt("bonus") + "%: " + c.getString("hint") + ")" : ""),
                x + 6, y + 29, 0.6f, Ui.GOLD, 0);
        Ui.small(ctx, textRenderer, "$" + c.getInt("pay") + " · " + c.getInt("min") + " мин · от: " + c.getString("client"), x + 6, y + (big ? 37 : 39), 0.65f, Ui.MONEY, 0);
        return y + (big ? 48 : 50);
    }

    // ---------------------------------------------------------------- контрабанда
    private void smuggle(DrawContext ctx, NbtCompound s, int act, int x, int y, int w) {
        if (act < 1) {
            wrap(ctx, "Рыбак с незнакомыми не работает. Пройди немного сюжета (акт II).", x + 14, y + 10, w - 28, 0xFFB8A8B8);
            return;
        }
        Ui.small(ctx, textRenderer, "Доставлено партий: " + s.getInt("done"), x + w - 10, y - 1, 0.6f, 0xFF9A90A8, 2);
        if (s.contains("active")) {
            NbtCompound c = s.getCompound("active");
            int cy = cargo(ctx, x + 8, y + 6, w - 16, c);
            String[] st = {"забери транспорт с грузом", "садись в транспорт", "вези груз покупателю"};
            Ui.small(ctx, textRenderer, "Сейчас: " + st[Math.min(2, c.getInt("stage"))] + " · осталось " + c.getInt("left") / 60 + " мин", x + 14, cy, 0.7f, Ui.CYAN, 0);
            button(ctx, x + w - 98, cy + 10, 88, 14, "Бросить груз", 0xFF8A3040, true, () -> act("uw_sm", "cancel", 0));
            wrap(ctx, "§7Катер: доведи до метки на воде. Самолёт: пролети над меткой ниже 40 м — груз сбросится. Береговая охрана: катера со стрелками и вертолёт. Без хвоста — +20%.",
                    x + 14, cy + 30, w - 28, 0xFF9A90A8);
            return;
        }
        NbtList l = s.getList("offers", NbtElement.COMPOUND_TYPE);
        if (l.isEmpty()) {
            wrap(ctx, "Рыбак пока молчит. Партии приходят СМС.", x + 14, y + 10, w - 28, 0xFFB8A8B8);
            button(ctx, x + w / 2 - 60, y + 34, 120, 14, "Спросить Рыбака", Ui.CYAN, true, () -> act("uw_sm", "refresh", 0));
            return;
        }
        int cy = y + 4;
        for (int i = 0; i < l.size(); i++) {
            final int k = i;
            int top = cy;
            cy = cargo(ctx, x + 8, cy, w - 16, l.getCompound(i));
            button(ctx, x + w - 70, top + 2, 60, 14, "Взять", Ui.CYAN, true, () -> {
                act("uw_sm", "take", k);
                close();
            });
            cy += 4;
        }
    }

    private int cargo(DrawContext ctx, int x, int y, int w, NbtCompound c) {
        int risk = c.getInt("riskN");
        int rc = risk == 0 ? 0xFF62E57E : risk == 1 ? Ui.GOLD : Ui.RED;
        ctx.fill(x, y, x + w, y + 40, 0x30FFFFFF);
        ctx.fill(x, y, x + 2, y + 40, rc);
        ctx.drawText(textRenderer, (c.getBoolean("plane") ? "✈ " : "⚓ ") + c.getString("cargo"), x + 6, y + 3, 0xFFFFFFFF, false);
        Ui.small(ctx, textRenderer, c.getString("from") + " → " + c.getString("to") + " (" + c.getString("who") + ")", x + 6, y + 14, 0.6f, 0xFFD0C0C8, 0);
        Ui.small(ctx, textRenderer, "Риск: " + c.getString("risk"), x + 6, y + 22, 0.6f, rc, 0);
        Ui.small(ctx, textRenderer, "$" + c.getInt("pay") + " · " + c.getInt("min") + " мин", x + 6, y + 30, 0.65f, Ui.MONEY, 0);
        return y + 44;
    }
}
