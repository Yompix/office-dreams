package com.yompix.novograd.client.screen;

import com.yompix.novograd.client.HousingClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Телефон → «Дом»: риелтор «Новоград-Недвижимость». Каталог (квартиры, дома, виллы, люксы, пентхаус) с ценами
 * покупки и аренды, и «Мой дом»: сейф для грязных денег, гардероб, сон, гараж с машинами и штрафстоянка.
 */
public class RealEstateScreen extends LifeScreen {
    private static final String[] FILTERS = {"Все", "Квартиры", "Дома", "Виллы", "Люксы", "Убежища"};
    private boolean mine;
    private int filter, scroll;
    private long seen = -1;

    public RealEstateScreen() {
        this(false);
    }

    /** mine — сразу вкладка «Мой дом». */
    public RealEstateScreen(boolean mine) {
        super("Недвижимость");
        this.mine = mine;
    }

    @Override
    protected void init() {
        super.init();
        HousingClient.send("list");
    }

    private static NbtCompound d() {
        return HousingClient.data;
    }

    private static void act(String type, String id) {
        NbtCompound n = new NbtCompound();
        n.putString("id", id);
        HousingClient.send(type, n);
    }

    private static void car(String type, int id) {
        NbtCompound n = new NbtCompound();
        n.putInt("car", id);
        HousingClient.send(type, n);
    }

    private boolean match(NbtCompound l) {
        String k = l.getString("kind");
        return switch (filter) {
            case 1 -> k.equals("Квартира") || k.equals("Пентхаус");
            case 2 -> k.equals("Дом");
            case 3 -> k.equals("Вилла");
            case 4 -> k.equals("Люкс в отеле");
            case 5 -> k.equals("Убежище");
            default -> true;
        };
    }

    @Override
    protected void draw(DrawContext ctx, float delta) {
        if (seen != HousingClient.version) seen = HousingClient.version;
        int w = Math.min(400, width - 20), h = Math.min(260, height - 16);
        int x = width / 2 - w / 2, y = height / 2 - h / 2 + (int) ((1 - openAnim()) * 30);
        panel(ctx, x, y, w, h, 0xFF2B1E12, 0xFF120C07);
        ctx.fillGradient(x, y, x + w, y + 22, 0xFFA0785A, 0xFF6D4C35);
        NbtCompound d = d();
        ctx.drawText(textRenderer, "🏠 НОВОГРАД-НЕДВИЖИМОСТЬ", x + 8, y + 7, 0xFFFFF3E0, true);
        String bal = "$" + d.getInt("money") + (d.getInt("dirty") > 0 ? "  · грязных $" + d.getInt("dirty") : "");
        ctx.drawText(textRenderer, bal, x + w - 8 - textRenderer.getWidth(bal), y + 7, 0xFFB9F6CA, true);
        button(ctx, x + 8, y + 27, 90, 14, "Каталог", mine ? 0xFF5D4037 : 0xFFFFB300, true, () -> { mine = false; scroll = 0; });
        button(ctx, x + 102, y + 27, 90, 14, "Мой дом", mine ? 0xFFFFB300 : 0xFF5D4037, true, () -> { mine = true; scroll = 0; });
        button(ctx, x + w - 60, y + 27, 52, 14, "Закрыть", 0xFF795548, true, this::close);
        int top = y + 46, bottom = y + h - 6;
        ctx.enableScissor(x + 2, top, x + w - 2, bottom);
        int before = hits.size();
        int yy = top - scroll;
        if (!d.contains("list")) center(ctx, "Загрузка…", x + w / 2, top + 30, 0xFFD7CCC8);
        else if (mine) yy = mine(ctx, x, yy, w, d);
        else yy = catalog(ctx, x, yy, w, d);
        ctx.disableScissor();
        maxScroll = Math.max(0, yy + scroll - bottom + 4);
        if (maxScroll > 0) {
            int bar = Math.max(16, (bottom - top) * (bottom - top) / (bottom - top + maxScroll));
            int by = top + (int) ((bottom - top - bar) * (scroll / (float) maxScroll));
            ctx.fill(x + w - 4, by, x + w - 2, by + bar, 0x90FFFFFF);
        }
        // кнопки списка, уехавшие за край прокрутки, не нажимаются
        for (int i = hits.size() - 1; i >= before; i--) {
            Hit hh = hits.get(i);
            if (hh.y() < top || hh.y() + hh.h() > bottom) hits.remove(i);
        }
    }

    private int maxScroll;

    private int catalog(DrawContext ctx, int x, int y, int w, NbtCompound d) {
        int fx = x + 8;
        for (int i = 0; i < FILTERS.length; i++) {
            int fw = textRenderer.getWidth(FILTERS[i]) + 10;
            int idx = i;
            button(ctx, fx, y, fw, 12, FILTERS[i], filter == i ? 0xFFFF8F00 : 0xFF4E342E, true, () -> { filter = idx; scroll = 0; });
            fx += fw + 3;
        }
        y += 17;
        NbtList list = d.getList("list", NbtElement.COMPOUND_TYPE);
        for (NbtElement e : list) {
            NbtCompound l = (NbtCompound) e;
            if (!match(l)) continue;
            String st = l.getString("state");
            boolean own = st.equals("own") || st.equals("rent");
            roundRect(ctx, x + 6, y, w - 14, 34, own ? 0xFF1B3A22 : st.equals("taken") ? 0xFF2A2A2A : 0xFF2A1D14);
            int tw = w - 14 - 84;
            ctx.drawText(textRenderer, l.getString("name"), x + 11, y + 4, 0xFFFFF3E0, false);
            String info = l.getString("kind") + " · " + l.getString("area") + " · гараж на " + l.getInt("cars") + (l.getBoolean("heli") ? " · вертолёт" : "");
            ctx.drawText(textRenderer, textRenderer.trimToWidth(info, tw), x + 11, y + 15, 0xFFBCAAA4, false);
            String price = (l.getInt("price") > 0 ? "Купить $" + l.getInt("price") : "") + (l.getInt("price") > 0 && l.getInt("rent") > 0 ? " · " : "")
                    + (l.getInt("rent") > 0 ? "аренда $" + l.getInt("rent") + "/сут" : "");
            ctx.drawText(textRenderer, price, x + 11, y + 25, 0xFFFFD54F, false);
            int bx = x + w - 14 - 4;
            String id = l.getString("id");
            switch (st) {
                case "own", "rent" -> {
                    String t = st.equals("rent") ? "Снято ✔" : "Твоё ✔";
                    ctx.drawText(textRenderer, t, bx - textRenderer.getWidth(t), y + 5, 0xFF69F0AE, false);
                }
                case "taken" -> ctx.drawText(textRenderer, "Занято", bx - textRenderer.getWidth("Занято"), y + 5, 0xFF9E9E9E, false);
                case "locked" -> {
                    String t = "Откроется в акте " + l.getInt("act");
                    ctx.drawText(textRenderer, t, bx - textRenderer.getWidth(t), y + 13, 0xFFFF8A65, false);
                }
                default -> {
                    int money = d.getInt("money");
                    int by = l.getInt("price") > 0 && l.getInt("rent") > 0 ? y + 3 : y + 10;
                    if (l.getInt("price") > 0) {
                        button(ctx, bx - 72, by, 72, 13, "Купить", 0xFF2E7D32, money >= l.getInt("price"), () -> act("buy", id));
                        by += 15;
                    }
                    if (l.getInt("rent") > 0) button(ctx, bx - 72, by, 72, 13, "Снять", 0xFF0288D1, money >= l.getInt("rent"), () -> act("rent", id));
                }
            }
            y += 38;
        }
        return y;
    }

    private int mine(DrawContext ctx, int x, int y, int w, NbtCompound d) {
        NbtList list = d.getList("list", NbtElement.COMPOUND_TYPE);
        NbtList cars = d.getList("cars", NbtElement.COMPOUND_TYPE);
        String here = d.getString("here");
        List<NbtCompound> homes = new ArrayList<>();
        for (NbtElement e : list) {
            NbtCompound l = (NbtCompound) e;
            if (l.getString("state").equals("own") || l.getString("state").equals("rent")) homes.add(l);
        }
        // сейф — один на все дома
        roundRect(ctx, x + 6, y, w - 14, 30, 0xFF33261A);
        ctx.drawText(textRenderer, "💰 Сейф: $" + d.getInt("safe") + " грязными · в кармане $" + d.getInt("dirty"), x + 11, y + 4, 0xFFFFE082, false);
        ctx.drawText(textRenderer, here.isEmpty() ? "Сейф, гардероб и сон — когда ты дома" : "Ты дома — деньги в сейфе не пропадут при гибели и аресте",
                x + 11, y + 16, 0xFFBCAAA4, false);
        y += 34;
        if (!here.isEmpty()) {
            int bw = (w - 22) / 4;
            button(ctx, x + 6, y, bw, 14, "В сейф всё", 0xFF6D4C41, d.getInt("dirty") > 0, () -> HousingClient.send("safe_in"));
            button(ctx, x + 8 + bw, y, bw, 14, "Из сейфа", 0xFF6D4C41, d.getInt("safe") > 0, () -> HousingClient.send("safe_out"));
            button(ctx, x + 10 + bw * 2, y, bw, 14, "Гардероб", 0xFFAD1457, true, () -> HousingClient.send("wardrobe"));
            button(ctx, x + 12 + bw * 3, y, bw, 14, "Спать", 0xFF283593, true, () -> { HousingClient.send("sleep"); close(); });
            y += 20;
        }
        if (homes.isEmpty()) {
            wrap(ctx, "У тебя пока нет своего жилья. Загляни в «Каталог»: квартиру можно снять посуточно, дом — купить. "
                    + "Дома — сейф, гардероб, сон и гараж, после больницы очнёшься у себя.", x + 10, y + 4, w - 24, 0xFFD7CCC8);
            y += 44;
        }
        for (NbtCompound l : homes) {
            String id = l.getString("id");
            List<NbtCompound> parked = new ArrayList<>();
            for (NbtElement e : cars) if (((NbtCompound) e).getString("where").equals("home:" + id)) parked.add((NbtCompound) e);
            int hh = 32 + parked.size() * 16;
            roundRect(ctx, x + 6, y, w - 14, hh, id.equals(here) ? 0xFF1B3A22 : 0xFF2A1D14);
            ctx.drawText(textRenderer, l.getString("name") + (id.equals(here) ? "  · ты здесь" : ""), x + 11, y + 4, 0xFFFFF3E0, false);
            ctx.drawText(textRenderer, l.getString("kind") + " · " + l.getString("area") + (l.getString("state").equals("rent") ? " · аренда $" + l.getInt("rent") + "/сут" : ""),
                    x + 11, y + 15, 0xFFBCAAA4, false);
            String g = "🅿 Гараж " + parked.size() + "/" + l.getInt("cars") + (l.getBoolean("heli") ? " · 🚁 на крыше" : "");
            ctx.drawText(textRenderer, g, x + 11, y + 24, 0xFF90CAF9, false);
            boolean rent = l.getString("state").equals("rent");
            if (!id.startsWith("P:") && !id.startsWith("C:"))
                button(ctx, x + w - 14 - 84, y + 4, 80, 13, rent ? "Съехать" : "Продать ½", 0xFF8D6E63, true, () -> act("leave", id));
            int cy = y + 34;
            for (NbtCompound c : parked) {
                ctx.fill(x + 12, cy + 2, x + 20, cy + 10, 0xFF000000 | c.getInt("color"));
                String nm = carName(c) + " · " + c.getInt("health") + "%" + (c.getBoolean("nitro") ? " · нитро" : "")
                        + (c.getInt("engine") > 0 ? " · мотор " + c.getInt("engine") : "");
                ctx.drawText(textRenderer, nm, x + 24, cy + 2, 0xFFFFFFFF, false);
                int cid = c.getInt("id");
                button(ctx, x + w - 14 - 84, cy, 80, 13, "Выкатить", 0xFF00897B, true, () -> car("car_out", cid));
                cy += 16;
            }
            y += hh + 4;
        }
        // штрафстоянка
        List<NbtCompound> lot = new ArrayList<>();
        for (NbtElement e : cars) if (((NbtCompound) e).getString("where").startsWith("impound:")) lot.add((NbtCompound) e);
        if (!lot.isEmpty()) {
            ctx.drawText(textRenderer, "🚨 Штрафстоянка", x + 10, y + 2, 0xFFFF8A80, false);
            y += 13;
            for (NbtCompound c : lot) {
                roundRect(ctx, x + 6, y, w - 14, 16, 0xFF3A1A1A);
                ctx.fill(x + 12, y + 4, x + 20, y + 12, 0xFF000000 | c.getInt("color"));
                String where = c.getString("where").substring(8);
                String nm = carName(c) + (where.equals("evict") ? " · после переезда" : where.equals("delivery") ? " · доставка" : " · «" + where + "»");
                ctx.drawText(textRenderer, nm, x + 24, y + 4, 0xFFFFFFFF, false);
                int cid = c.getInt("id");
                button(ctx, x + w - 14 - 84, y + 1, 80, 13, where.equals("delivery") ? "Забрать" : "Забрать $250", 0xFFC62828, true, () -> car("car_release", cid));
                y += 18;
            }
        }
        wrap(ctx, "Поставить машину в гараж: заедь на въезд (значок «гараж» на радаре) и остановись.", x + 10, y + 4, w - 24, 0xFF8D8D8D);
        return y + 24;
    }

    private static final String[][] CAR_NAMES = {{"suv", "Внедорожник"}, {"pickup", "Пикап"}, {"muscle", "Маслкар"}, {"limo", "Лимузин"},
            {"truck", "Грузовик"}, {"bike", "Мотоцикл"}, {"scooter", "Скутер"}};

    /** Название машины: перевод из языкового файла, а если его нет — по кузову. */
    private static String carName(NbtCompound c) {
        String key = c.getString("name");
        if (net.minecraft.client.resource.language.I18n.hasTranslation(key)) return Text.translatable(key).getString();
        for (String[] n : CAR_NAMES) if (key.endsWith(n[0])) return n[1];
        return "Машина";
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hx, double vy) {
        scroll = (int) Math.max(0, Math.min(maxScroll, scroll - vy * 18));
        return true;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
