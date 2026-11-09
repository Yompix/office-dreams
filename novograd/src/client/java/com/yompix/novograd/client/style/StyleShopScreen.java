package com.yompix.novograd.client.style;

import com.yompix.novograd.client.LifeHud;
import com.yompix.novograd.client.NovogradClient;
import com.yompix.novograd.client.screen.LifeScreen;
import com.yompix.novograd.client.ui.Ui;
import com.yompix.novograd.life.Appearance;
import com.yompix.novograd.life.LifeData;
import com.yompix.novograd.life.StyleCatalog;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.MathHelper;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Магазин одежды / цирюльня / гардероб в стиле SA: слева — твой герой в полный рост (крутится, можно повернуть мышью)
 * сразу в выбранной вещи, справа — разделы и список с ценами, цвета и большая кнопка «Купить» / «Надеть».
 */
public class StyleShopScreen extends LifeScreen {
    private final String kind; // cheap, lux, barber, wardrobe
    private int tab, sel, scroll, color;
    private int lookTone = -1;
    private Boolean lookSlim;
    private float yaw = 205, lastMx;
    private boolean dragging;
    private long idleSince = System.currentTimeMillis();
    private long flashAt;
    private String flash = "";
    private final float[] rowAnim = new float[64];
    private final float[] tabAnim = new float[8];
    private int listX, listY, listW, listH;
    private static final int ROW_H = 15;

    /** Строка списка: название, справа — цена/статус, образец цвета, как выглядит, и действие для кнопки. */
    private record Row(String name, String right, int rightColor, int swatch, Appearance look, String action, boolean enabled, Runnable run, String desc) {}

    public StyleShopScreen(String kind) {
        super(title(kind));
        this.kind = kind;
        selectWorn();
    }

    private static String title(String kind) {
        return switch (kind) {
            case "lux" -> StyleCatalog.LUX_NAME;
            case "barber" -> StyleCatalog.BARBER_NAME;
            case "wardrobe" -> "Гардероб";
            default -> StyleCatalog.CHEAP_NAME;
        };
    }

    private boolean barber() {
        return kind.equals("barber");
    }

    private int tier() {
        return kind.equals("lux") ? 1 : kind.equals("cheap") ? 0 : -1;
    }

    private int accent() {
        return switch (kind) {
            case "lux" -> 0xFFB04DFF;
            case "barber" -> Ui.CYAN;
            case "wardrobe" -> Ui.GOLD;
            default -> 0xFF5BE36A;
        };
    }

    private String[] tabs() {
        if (barber()) return new String[]{"СТРИЖКА", "ЦВЕТ", "БОРОДА", "ВНЕШНОСТЬ"};
        StyleCatalog.Slot[] s = StyleCatalog.Slot.values();
        String[] t = new String[s.length];
        for (int i = 0; i < s.length; i++) t[i] = s[i].title.toUpperCase();
        return t;
    }

    private static LifeData d() {
        return NovogradClient.data;
    }

    /** Текущая настоящая внешность (то, что надето), с видимым стилем для примерки. */
    private static Appearance real() {
        Appearance a = d().look.copy();
        a.custom = true;
        if (mc().currentScreen instanceof StyleShopScreen s && s.barber()) a.hat = ""; // у цирюльника шапку снимают
        return a;
    }

    private StyleCatalog.Slot slot() {
        return StyleCatalog.Slot.values()[Math.min(tab, StyleCatalog.Slot.values().length - 1)];
    }

    // ------------------------------------------------------------------ строки
    private List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        LifeData d = d();
        Appearance cur = real();
        if (barber()) {
            switch (tab) {
                case 0 -> {
                    for (var o : StyleCatalog.HAIR) {
                        Appearance a = cur.copy();
                        a.hair = o.id();
                        boolean now = cur.hair.equals(o.id());
                        rows.add(new Row(o.name(), now ? "СЕЙЧАС" : "$" + o.price(), now ? Ui.MONEY : Ui.GOLD, 0, a,
                                now ? "ТАК И ЕСТЬ" : "ПОСТРИЧЬ — $" + o.price(), !now && d.money >= o.price(), () -> barberSend("hair", o.id()), "Стрижка «" + o.name() + "»."));
                    }
                }
                case 1 -> {
                    for (int i = 0; i < StyleCatalog.HAIR_COLORS.size(); i++) {
                        var o = StyleCatalog.HAIR_COLORS.get(i);
                        Appearance a = cur.copy();
                        a.hairColor = i;
                        boolean now = cur.hairColor == i;
                        rows.add(new Row(o.name(), now ? "СЕЙЧАС" : "$" + o.price(), now ? Ui.MONEY : Ui.GOLD, 0xFF000000 | o.color(), a,
                                now ? "ТАК И ЕСТЬ" : "ПОКРАСИТЬ — $" + o.price(), !now && d.money >= o.price(), () -> barberSend("color", o.id()),
                                o.price() > 50 ? "Неоновый цвет — чтобы тебя замечали за квартал." : "Краска держится, пока не перекрасишь."));
                    }
                }
                case 2 -> {
                    for (var o : StyleCatalog.BEARDS) {
                        Appearance a = cur.copy();
                        a.beard = o.id();
                        boolean now = cur.beard.equals(o.id());
                        rows.add(new Row(o.name(), now ? "СЕЙЧАС" : "$" + o.price(), now ? Ui.MONEY : Ui.GOLD, 0, a,
                                now ? "ТАК И ЕСТЬ" : "ГОТОВО — $" + o.price(), !now && d.money >= o.price(), () -> barberSend("beard", o.id()), "Цирюльник Арсен поправит бороду бритвой."));
                    }
                }
                default -> {
                    int tone = lookTone < 0 ? cur.skin : lookTone;
                    boolean slim = lookSlim == null ? cur.slim : lookSlim;
                    int price = cur.skinChosen ? StyleCatalog.LOOK_PRICE : 0;
                    boolean same = cur.skinChosen && tone == cur.skin && slim == cur.slim;
                    String act = same ? "ТАК И ЕСТЬ" : price == 0 ? "ВЫБРАТЬ — БЕСПЛАТНО" : "ПРИМЕНИТЬ — $" + price;
                    Runnable send = () -> {
                        NbtCompound n = new NbtCompound();
                        n.putString("what", "look");
                        n.putInt("tone", lookTone < 0 ? cur.skin : lookTone);
                        n.putBoolean("slim", lookSlim == null ? cur.slim : lookSlim);
                        StyleClient.send("barber", n);
                        done("Новая внешность");
                    };
                    String desc = price == 0 ? "Первый раз — бесплатно. Потом смена внешности стоит $" + StyleCatalog.LOOK_PRICE + "." : "Смена внешности — $" + price + ".";
                    for (int i = 0; i < StyleCatalog.SKIN_TONES.length; i++) {
                        Appearance a = cur.copy();
                        a.skin = i;
                        a.slim = slim;
                        rows.add(new Row("Цвет кожи " + (i + 1), i == tone ? "✔" : "", Ui.MONEY, 0xFF000000 | StyleCatalog.SKIN_TONES[i], a, act,
                                !same && d.money >= price, send, desc));
                    }
                    for (int k = 0; k < 2; k++) {
                        Appearance a = cur.copy();
                        a.skin = tone;
                        a.slim = k == 1;
                        rows.add(new Row(k == 0 ? "Телосложение: крепкое" : "Телосложение: стройное", (k == 1) == slim ? "✔" : "", Ui.MONEY, 0, a, act,
                                !same && d.money >= price, send, desc));
                    }
                }
            }
            return rows;
        }
        StyleCatalog.Slot slot = slot();
        String worn = cur.get(slot);
        if (kind.equals("wardrobe")) {
            if (slot == StyleCatalog.Slot.HAT || slot == StyleCatalog.Slot.GLASSES) {
                Appearance a = cur.copy();
                a.set(slot, "");
                boolean now = worn.isEmpty();
                rows.add(new Row(slot == StyleCatalog.Slot.HAT ? "Без головного убора" : "Без очков", now ? "НАДЕТО" : "", Ui.MONEY, 0, a,
                        now ? "НАДЕТО" : "СНЯТЬ", !now, () -> wear(slot, ""), "Голова открыта."));
            }
            List<String> owned = new ArrayList<>(d.wardrobe);
            owned.sort(String::compareTo);
            for (String ref : owned) {
                StyleCatalog.Wear w = StyleCatalog.wear(Appearance.id(ref));
                if (w == null || w.slot() != slot) continue;
                Appearance a = cur.copy();
                a.set(slot, ref);
                boolean now = worn.equals(ref);
                rows.add(new Row(w.name(), now ? "НАДЕТО" : "", Ui.MONEY, 0xFF000000 | w.color(Appearance.colorIndex(ref)), a,
                        now ? "НАДЕТО" : "НАДЕТЬ", !now, () -> wear(slot, ref), w.desc()));
            }
            return rows;
        }
        List<StyleCatalog.Wear> list = StyleCatalog.of(slot, tier());
        for (int i = 0; i < list.size(); i++) {
            StyleCatalog.Wear w = list.get(i);
            int c = i == sel ? color : wornOrOwnedColor(w, cur);
            String ref = w.id() + "#" + c;
            Appearance a = cur.copy();
            a.set(slot, ref);
            boolean wornAny = Appearance.id(worn).equals(w.id());
            boolean ownedAny = d.wardrobe.stream().anyMatch(r -> Appearance.id(r).equals(w.id()));
            boolean wornThis = worn.equals(ref), ownedThis = d.wardrobe.contains(ref);
            String right = wornAny ? "НАДЕТО" : ownedAny ? "ЕСТЬ" : "$" + w.price();
            int rc = wornAny ? Ui.MONEY : ownedAny ? Ui.CYAN : Ui.GOLD;
            String act = wornThis ? "НАДЕТО" : ownedThis ? "НАДЕТЬ" : "КУПИТЬ — $" + w.price();
            boolean en = !wornThis && (ownedThis || d.money >= w.price());
            Runnable run = ownedThis ? () -> wear(slot, ref) : () -> {
                NbtCompound n = new NbtCompound();
                n.putString("ref", ref);
                StyleClient.send("buy", n);
                done("Куплено: " + w.name());
            };
            rows.add(new Row(w.name(), right, rc, 0, a, act, en, run, w.desc()));
        }
        return rows;
    }

    private int wornOrOwnedColor(StyleCatalog.Wear w, Appearance cur) {
        String worn = cur.get(w.slot());
        if (Appearance.id(worn).equals(w.id())) return Appearance.colorIndex(worn);
        for (int c = 0; c < w.colors().length; c++) if (d().wardrobe.contains(w.id() + "#" + c)) return c;
        return 0;
    }

    private void wear(StyleCatalog.Slot slot, String ref) {
        NbtCompound n = new NbtCompound();
        n.putString("slot", slot.name());
        n.putString("ref", ref);
        StyleClient.send("wear", n);
        done(ref.isEmpty() ? "Снято" : "Надето");
    }

    private void barberSend(String what, String value) {
        NbtCompound n = new NbtCompound();
        n.putString("what", what);
        n.putString("value", value);
        StyleClient.send("barber", n);
        done("Готово!");
    }

    private void done(String text) {
        flash = text;
        flashAt = System.currentTimeMillis();
    }

    /** Выбрать в списке то, что сейчас надето. */
    private void selectWorn() {
        sel = 0;
        color = 0;
        lookTone = -1;
        lookSlim = null;
        Appearance cur = real();
        if (barber()) {
            if (tab == 0) for (int i = 0; i < StyleCatalog.HAIR.size(); i++) if (StyleCatalog.HAIR.get(i).id().equals(cur.hair)) sel = i;
            if (tab == 1) sel = Math.floorMod(cur.hairColor, StyleCatalog.HAIR_COLORS.size());
            if (tab == 2) for (int i = 0; i < StyleCatalog.BEARDS.size(); i++) if (StyleCatalog.BEARDS.get(i).id().equals(cur.beard)) sel = i;
            if (tab == 3) sel = cur.skin;
        } else if (!kind.equals("wardrobe")) {
            List<StyleCatalog.Wear> list = StyleCatalog.of(slot(), tier());
            for (int i = 0; i < list.size(); i++)
                if (Appearance.id(cur.get(slot())).equals(list.get(i).id())) {
                    sel = i;
                    color = Appearance.colorIndex(cur.get(slot()));
                }
        } else {
            List<Row> rows = rows();
            for (int i = 0; i < rows.size(); i++) if (rows.get(i).right().equals("НАДЕТО")) sel = i;
        }
        scroll = 0;
    }

    private void select(int i, int size) {
        if (size == 0) return;
        int n = Math.floorMod(i, size);
        if (n == sel) return;
        sel = n;
        if (!barber() && !kind.equals("wardrobe")) color = wornOrOwnedColor(StyleCatalog.of(slot(), tier()).get(sel), real());
        if (barber() && tab == 3) {
            if (sel < StyleCatalog.SKIN_TONES.length) lookTone = sel;
            else lookSlim = sel - StyleCatalog.SKIN_TONES.length == 1;
        }
        int visible = Math.max(1, listH / ROW_H);
        if (sel < scroll) scroll = sel;
        if (sel >= scroll + visible) scroll = sel - visible + 1;
    }

    private void setTab(int t) {
        tab = Math.floorMod(t, tabs().length);
        selectWorn();
    }

    // ------------------------------------------------------------------ отрисовка
    @Override
    protected void draw(DrawContext ctx, float delta) {
        float a = openAnim();
        int acc = accent();
        List<Row> rows = rows();
        if (sel >= rows.size()) sel = Math.max(0, rows.size() - 1);
        Row cur = rows.isEmpty() ? null : rows.get(sel);
        StyleClient.preview = cur != null ? cur.look() : real();

        // ---- шапка: название магазина и деньги
        String name = getTitle().getString().toUpperCase();
        float hs = width < 380 ? 1.2f : 1.6f;
        int hw = (int) (textRenderer.getWidth(name) * hs) + 22;
        int hy = 8 + (int) ((1 - a) * -20);
        Ui.slant(ctx, 10, hy, hw, (int) (12 * hs) + 6, 6, acc);
        Ui.slant(ctx, 10 + hw + 4, hy, 5, (int) (12 * hs) + 6, 6, Ui.alpha(acc, 0.55f));
        Ui.big(ctx, textRenderer, name, 20, hy + 4, hs, Ui.WHITE, 0);
        String sub = switch (kind) {
            case "cheap" -> "уличная одежда · цены для района";
            case "lux" -> "одежда класса люкс · для тех, кто поднялся";
            case "barber" -> "стрижки · цвет · бороды · внешность";
            default -> "переодеться бесплатно";
        };
        Ui.small(ctx, textRenderer, sub, 14, hy + 12 * hs + 10, 0.8f, 0xFFD9D7D0, 0);
        Ui.big(ctx, textRenderer, "$" + LifeHud.money(d().money), width - 12, hy + 4, 1.5f, Ui.MONEY, 2);

        // ---- сцена с героем
        int stageW = (int) (width * 0.40f), stageX = 10, stageY = hy + 40, stageH = height - stageY - 26;
        drawStage(ctx, stageX, stageY, stageW, stageH, acc, a);

        // ---- правая панель
        int px = stageX + stageW + 10, py = stageY - 6, pw = width - px - 10, ph = height - py - 26;
        Ui.panel(ctx, px, py, pw, ph, Ui.alpha(acc, a));
        String[] tabs = tabs();
        int tx = px + 6, ty = py + 7;
        for (int i = 0; i < tabs.length; i++) {
            int tw = textRenderer.getWidth(tabs[i]) + 12;
            if (tx + tw > px + pw - 4) {
                tx = px + 6;
                ty += 17;
            }
            boolean hov = hover(tx, ty, tw, 14);
            tabAnim[i] = Ui.approach(tabAnim[i], i == tab ? 1 : hov ? 0.4f : 0, 0.3f);
            Ui.slant(ctx, tx, ty, tw, 14, 4, 0xC0161615);
            if (tabAnim[i] > 0.02f) Ui.slant(ctx, tx, ty, (int) (tw * tabAnim[i]), 14, 4, i == tab ? acc : Ui.alpha(acc, 0.5f));
            ctx.drawText(textRenderer, tabs[i], tx + 6, ty + 3, i == tab ? (Ui.WHITE) : 0xFFB9B7B1, i == tab);
            final int t = i;
            area(tx, ty, tw, 14, () -> setTab(t));
            tx += tw + 3;
        }
        // список
        listX = px + 4;
        listY = ty + 20;
        listW = pw - 8;
        int bottomBlock = 58;
        listH = Math.max(ROW_H * 3, py + ph - bottomBlock - listY);
        int visible = listH / ROW_H;
        scroll = MathHelper.clamp(scroll, 0, Math.max(0, rows.size() - visible));
        ctx.enableScissor(listX, listY, listX + listW, listY + visible * ROW_H);
        for (int i = scroll; i < rows.size() && i < scroll + visible; i++) {
            Row r = rows.get(i);
            int ry = listY + (i - scroll) * ROW_H;
            boolean s = i == sel;
            if (i < rowAnim.length) rowAnim[i] = Ui.approach(rowAnim[i], s ? 1 : 0, 0.35f);
            float k = i < rowAnim.length ? rowAnim[i] : (s ? 1 : 0);
            if (k > 0.02f) Ui.slant(ctx, listX, ry, (int) (listW * Ui.easeOut(k)), ROW_H - 1, 4, Ui.alpha(0xE0D42A3A, k));
            else if (i % 2 == 1) ctx.fill(listX, ry, listX + listW, ry + ROW_H - 1, 0x12FFFFFF);
            int nx = listX + 6 + (int) (k * 3);
            if (r.swatch() != 0) {
                ctx.fill(nx - 1, ry + 3, nx + 9, ry + 12, 0xFF000000);
                ctx.fill(nx, ry + 4, nx + 8, ry + 11, r.swatch());
                nx += 13;
            }
            String label = r.name();
            int maxW = listW - (nx - listX) - textRenderer.getWidth(r.right()) - 12;
            if (textRenderer.getWidth(label) > maxW) label = textRenderer.trimToWidth(label, maxW - 6) + "…";
            ctx.drawText(textRenderer, label, nx, ry + 4, s ? Ui.WHITE : 0xFFDDDBD4, s);
            ctx.drawText(textRenderer, r.right(), listX + listW - 6 - textRenderer.getWidth(r.right()), ry + 4, s ? Ui.WHITE : r.rightColor(), s);
            final int idx = i;
            area(listX, ry, listW, ROW_H - 1, () -> select(idx, rows.size()));
        }
        ctx.disableScissor();
        if (rows.size() > visible) {
            int bh = Math.max(10, visible * ROW_H * visible / rows.size());
            int by = listY + (visible * ROW_H - bh) * scroll / Math.max(1, rows.size() - visible);
            ctx.fill(listX + listW, listY, listX + listW + 2, listY + visible * ROW_H, 0x30FFFFFF);
            ctx.fill(listX + listW, by, listX + listW + 2, by + bh, acc);
        }
        if (rows.isEmpty()) center(ctx, kind.equals("wardrobe") ? "Здесь пока пусто — купи что-нибудь в магазине" : "Пусто", px + pw / 2, listY + 20, 0xFFB9B7B1);

        // ---- цвета, описание и кнопка
        int by = py + ph - bottomBlock + 4;
        if (cur != null) {
            int cx = listX + 2;
            if (!barber() && !kind.equals("wardrobe")) {
                StyleCatalog.Wear w = StyleCatalog.of(slot(), tier()).get(sel);
                if (w.colors().length > 1) {
                    Ui.small(ctx, textRenderer, "ЦВЕТ", cx, by + 3, 0.75f, 0xFFB9B7B1, 0);
                    cx += 24;
                    for (int c = 0; c < w.colors().length; c++) {
                        boolean on = c == color;
                        boolean own = d().wardrobe.contains(w.id() + "#" + c);
                        ctx.fill(cx - 1, by - 1, cx + 12, by + 12, on ? Ui.WHITE : 0xFF000000);
                        ctx.fill(cx, by, cx + 11, by + 11, 0xFF000000 | w.color(c));
                        if (own) ctx.fill(cx + 8, by, cx + 11, by + 3, Ui.CYAN);
                        final int cc = c;
                        area(cx - 1, by - 1, 13, 13, () -> color = cc);
                        cx += 15;
                    }
                }
            }
            if (cx == listX + 2) {
                var lines = textRenderer.wrapLines(net.minecraft.text.Text.literal(cur.desc()), (int) ((listW - 6) / 0.8f));
                for (int i = 0; i < Math.min(2, lines.size()); i++) {
                    ctx.getMatrices().push();
                    ctx.getMatrices().translate(cx, by + i * 8, 0);
                    ctx.getMatrices().scale(0.8f, 0.8f, 1);
                    ctx.drawText(textRenderer, lines.get(i), 0, 0, 0xFFC9C7C1, false);
                    ctx.getMatrices().pop();
                }
            } else {
                String desc = textRenderer.trimToWidth(cur.desc(), (int) ((listW - (cx - listX) - 6) / 0.75f));
                Ui.small(ctx, textRenderer, desc, cx + 4, by + 3, 0.75f, 0xFFC9C7C1, 0);
            }
            int bw = listW - 4, bh = 20, bx = listX + 2, byy = py + ph - 28;
            button(ctx, bx, byy, bw, bh, cur.action(), acc, cur.enabled(), cur.run());
        }
        // вспышка «Куплено!»
        float fl = (System.currentTimeMillis() - flashAt) / 1400f;
        if (fl < 1 && !flash.isEmpty()) {
            float al = fl < 0.15f ? fl / 0.15f : 1 - Math.max(0, (fl - 0.6f) / 0.4f);
            Ui.big(ctx, textRenderer, flash.toUpperCase(), stageX + stageW / 2f, stageY + 6 - fl * 8, 1.4f, Ui.alpha(Ui.GOLD, al), 1);
        }

        // ---- нижняя полоса
        ctx.fill(0, height - 18, width, height, 0xE0040404);
        ctx.fill(0, height - 19, width, height - 18, Ui.alpha(acc, 0.8f));
        String hint = barber() ? "↑↓ выбор   Tab раздел   Enter оплатить   мышь — повернуть героя" : "↑↓ выбор   ← → цвет   Tab раздел   Enter купить/надеть   мышь — повернуть";
        Ui.small(ctx, textRenderer, hint, 10, height - 13, width < 420 ? 0.7f : 0.85f, 0xB0FFFFFF, 0);
        String back = "‹ ВЫЙТИ";
        int bw = textRenderer.getWidth(back) + 16;
        boolean bh = hover(width - bw - 8, height - 17, bw, 16);
        Ui.slant(ctx, width - bw - 8, height - 16, bw, 14, 4, bh ? Ui.PINK : 0x60FFFFFF);
        ctx.drawText(textRenderer, back, width - bw, height - 13, 0xFFFFFFFF, false);
        area(width - bw - 8, height - 17, bw, 17, this::close);
    }

    private void drawStage(DrawContext ctx, int x, int y, int w, int h, int acc, float a) {
        // подиум: прожектор сверху, пол, отражение
        ctx.fill(x, y, x + w, y + h, 0x90060606);
        int cx = x + w / 2;
        for (int i = 0; i < 18; i++) {
            float k = i / 18f;
            int half = (int) (w * (0.12f + k * 0.38f));
            int al = (int) (40 * (1 - k) * a);
            ctx.fill(cx - half, y + (int) (k * h * 0.86f), cx + half, y + (int) ((k + 1 / 18f) * h * 0.86f), al << 24 | (acc & 0xFFFFFF));
        }
        int floorY = y + h - 18;
        for (int i = 0; i < 6; i++) {
            int rw = (int) (w * 0.36f) - i * 5, rh = 7 - i;
            ctx.fill(cx - rw, floorY - rh / 2, cx + rw, floorY + rh / 2 + 1, Ui.alpha(acc, 0.06f + i * 0.04f));
        }
        ctx.fill(x, y, x + w, y + 1, Ui.alpha(acc, 0.6f));
        ctx.fill(x, y + h - 1, x + w, y + h, Ui.alpha(acc, 0.6f));
        // герой
        if (!dragging && System.currentTimeMillis() - idleSince > 2500) yaw += 0.35f;
        float size = Math.min(h * 0.34f, w * 0.6f);
        ctx.enableScissor(x, y, x + w, y + h);
        hero(ctx, cx, floorY, size, yaw);
        ctx.disableScissor();
        if (kind.equals("wardrobe")) {
            boolean mcSkin = !d().look.custom;
            button(ctx, x + 6, y + 6, w - 12, 13, mcSkin ? "Свой скин Minecraft: ДА" : "Свой скин Minecraft: НЕТ", 0xFF7E57C2, true, () -> {
                NbtCompound n = new NbtCompound();
                n.putBoolean("custom", mcSkin);
                StyleClient.send("vanilla_skin", n);
            });
        }
        // стрелки поворота
        button(ctx, x + 6, y + h - 22, 18, 14, "‹", acc, true, () -> {
            yaw -= 45;
            idleSince = System.currentTimeMillis();
        });
        button(ctx, x + w - 24, y + h - 22, 18, 14, "›", acc, true, () -> {
            yaw += 45;
            idleSince = System.currentTimeMillis();
        });
    }

    /** Герой в полный рост: ноги на floorY, повернут на yaw (180 — лицом к зрителю). */
    public static void hero(DrawContext ctx, float cx, float floorY, float size, float yaw) {
        LivingEntity e = mc().player;
        if (e == null) return;
        float saveBody = e.bodyYaw, savePrevBody = e.prevBodyYaw, saveYaw = e.getYaw(), savePitch = e.getPitch(), saveHead = e.headYaw, savePrevHead = e.prevHeadYaw;
        e.bodyYaw = yaw;
        e.prevBodyYaw = yaw;
        e.setYaw(yaw);
        e.setPitch(-4);
        e.headYaw = yaw;
        e.prevHeadYaw = yaw;
        Quaternionf q = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf q2 = new Quaternionf().rotateX(6 * MathHelper.RADIANS_PER_DEGREE);
        q.mul(q2);
        float scale = e.getScale();
        // на витрине — без оружия в руках: на кадр выбираем пустую ячейку хотбара
        int slot = -1;
        if (e instanceof net.minecraft.entity.player.PlayerEntity pl) {
            slot = pl.getInventory().selectedSlot;
            for (int i = 0; i < 9; i++) if (pl.getInventory().getStack(i).isEmpty()) {
                pl.getInventory().selectedSlot = i;
                break;
            }
        }
        InventoryScreen.drawEntity(ctx, cx, floorY - size * 0.9f, size / scale, new Vector3f(0, e.getHeight() / 2f, 0), q, q2, e);
        if (slot >= 0) ((net.minecraft.entity.player.PlayerEntity) e).getInventory().selectedSlot = slot;
        e.bodyYaw = saveBody;
        e.prevBodyYaw = savePrevBody;
        e.setYaw(saveYaw);
        e.setPitch(savePitch);
        e.headYaw = saveHead;
        e.prevHeadYaw = savePrevHead;
    }

    // ------------------------------------------------------------------ управление
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int stageW = (int) (width * 0.40f);
        if (button == 0 && mx >= 10 && mx < 10 + stageW && my > 48 && my < height - 48) {
            if (super.mouseClicked(mx, my, button)) return true;
            dragging = true;
            lastMx = (float) mx;
            idleSince = System.currentTimeMillis();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) {
            yaw -= (float) (mx - lastMx) * 2.2f;
            lastMx = (float) mx;
            idleSince = System.currentTimeMillis();
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        int size = rows().size();
        int visible = Math.max(1, listH / ROW_H);
        scroll = MathHelper.clamp(scroll - (int) Math.signum(v), 0, Math.max(0, size - visible));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        List<Row> rows = rows();
        switch (key) {
            case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_S -> {
                select(sel + 1, rows.size());
                click();
                return true;
            }
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_W -> {
                select(sel - 1, rows.size());
                click();
                return true;
            }
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_D -> {
                int dir = key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_A ? -1 : 1;
                if (!barber() && !kind.equals("wardrobe") && !rows.isEmpty()) {
                    color = Math.floorMod(color + dir, StyleCatalog.of(slot(), tier()).get(sel).colors().length);
                } else {
                    yaw += dir * 45;
                    idleSince = System.currentTimeMillis();
                }
                click();
                return true;
            }
            case GLFW.GLFW_KEY_TAB, GLFW.GLFW_KEY_E, GLFW.GLFW_KEY_Q -> {
                setTab(tab + (key == GLFW.GLFW_KEY_Q || (mods & GLFW.GLFW_MOD_SHIFT) != 0 ? -1 : 1));
                click();
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_SPACE -> {
                if (!rows.isEmpty() && rows.get(sel).enabled()) {
                    rows.get(sel).run().run();
                    click();
                }
                return true;
            }
            default -> {
                return super.keyPressed(key, scan, mods);
            }
        }
    }

    @Override
    public void removed() {
        StyleClient.preview = null;
        super.removed();
    }

    /** Для автотеста: выбрать раздел и строку. */
    public void pick(int t, int row, int c) {
        setTab(t);
        select(row, rows().size());
        if (c >= 0) color = c;
    }
}
