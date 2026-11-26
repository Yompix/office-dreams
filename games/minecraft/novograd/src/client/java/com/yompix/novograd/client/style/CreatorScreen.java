package com.yompix.novograd.client.style;

import com.yompix.novograd.client.LifeHud;
import com.yompix.novograd.client.NovogradClient;
import com.yompix.novograd.client.screen.LifeScreen;
import com.yompix.novograd.client.ui.Ui;
import com.yompix.novograd.life.Appearance;
import com.yompix.novograd.life.LifeData;
import com.yompix.novograd.life.LookGen;
import com.yompix.novograd.life.Style;
import com.yompix.novograd.life.StyleCatalog;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * 0.9.6: редактор внешности — при первом входе в новый мир, в цирюльне и магазинах одежды, телефон → «Внешность».
 * Слева — герой в полный рост (мышь — повернуть, колесо — приблизить, «шаг» — идёт на месте), справа — вкладки
 * Тело · Лицо · Волосы · Одежда · Аксессуары · Тату · Образы: образцы цвета и переключатели, минимум текста.
 * Всё сразу видно на герое; «Готово» отправляет внешность серверу (он проверяет цену и место).
 */
public class CreatorScreen extends LifeScreen {
    public enum Mode { FIRST, PHONE, BARBER, CHEAP, LUX, WARDROBE }

    public static final String[] TABS = {"ТЕЛО", "ЛИЦО", "ВОЛОСЫ", "ОДЕЖДА", "АКСЕССУАРЫ", "ТАТУ", "ОБРАЗЫ"};

    private final Mode mode;
    private Appearance look;
    private final Appearance start;
    private int tab, scroll, contentH;
    private float yaw = 200, zoom = 1f, lastMx;
    private boolean dragging, walk = true, allGenders;
    private long idleSince = System.currentTimeMillis();
    private final float[] tabAnim = new float[TABS.length];
    private int panelX, panelY, panelW, panelH, stageX, stageY, stageW, stageH;
    private long seed = System.nanoTime();

    public CreatorScreen(Mode mode) {
        super("Внешность");
        this.mode = mode;
        LifeData d = NovogradClient.data;
        Appearance cur = d.look.copy();
        start = cur.copy();
        if (!cur.custom) {
            // скин Minecraft: пол — по модели скина (тонкие руки — женский персонаж)
            boolean slim = mc().player != null && mc().player.getSkinTextures().model() == net.minecraft.client.util.SkinTextures.Model.SLIM;
            cur = mode == Mode.FIRST ? LookGen.generate(seed, slim, LookGen.CASUAL, false) : cur;
            cur.slim = slim;
            cur.custom = true;
        }
        if (mode == Mode.BARBER) tab = 2;
        if (mode == Mode.CHEAP || mode == Mode.LUX || mode == Mode.WARDROBE) tab = 3;
        look = cur;
    }

    /** Для автотестов: вкладка и внешность. */
    public void autotest(int t, Appearance a) {
        tab = Math.floorMod(t, TABS.length);
        scroll = 0;
        if (a != null) look = a.copy();
    }

    public Appearance look() {
        return look;
    }

    /** Для ролика-автотеста: одно действие как у игрока. */
    public void demo(String act) {
        idleSince = System.currentTimeMillis();
        switch (act) {
            case "gender" -> setGender(!look.slim);
            case "tone" -> look.skin = (look.skin + 3) % StyleCatalog.SKIN_TONES.length;
            case "hair" -> {
                int i = StyleCatalog.indexOf(StyleCatalog.HAIR, look.hair);
                look.hair = StyleCatalog.HAIR.get((i + 5) % StyleCatalog.HAIR.size()).id();
            }
            case "haircolor" -> look.hairColor = (look.hairColor + 4) % StyleCatalog.HAIR_COLORS.size();
            case "eyes" -> look.eyeColor = (look.eyeColor + 3) % StyleCatalog.EYE_COLORS.size();
            case "lips" -> look.lipstick = look.slim ? 3 : 0;
            case "top", "outer", "hat", "glasses", "bag" -> {
                StyleCatalog.Slot sl = switch (act) {
                    case "top" -> StyleCatalog.Slot.TOP;
                    case "outer" -> StyleCatalog.Slot.OUTER;
                    case "hat" -> StyleCatalog.Slot.HAT;
                    case "glasses" -> StyleCatalog.Slot.GLASSES;
                    default -> StyleCatalog.Slot.BAG;
                };
                var l = StyleCatalog.of(sl, -1, look.slim);
                int i = 0;
                for (int k = 0; k < l.size(); k++) if (l.get(k).id().equals(Appearance.id(look.get(sl)))) i = k + 1;
                var w = l.get((i + 2) % l.size());
                look.set(sl, w.id() + "#" + (look.skin % w.colors().length));
            }
            case "tat" -> look.tats[1] = 4;
            case "build" -> look.build = (look.build + 1) % 4;
            case "zoom" -> zoom = zoom > 1.3f ? 1f : 2.4f;
            case "rot" -> rot(90);
            case "random" -> randomize();
            default -> {
                if (act.startsWith("tab")) {
                    tab = Integer.parseInt(act.substring(3));
                    scroll = 0;
                }
            }
        }
    }

    private static LifeData d() {
        return NovogradClient.data;
    }

    private int accent() {
        return switch (mode) {
            case BARBER -> Ui.CYAN;
            case LUX -> 0xFFB04DFF;
            case CHEAP -> 0xFF5BE36A;
            case WARDROBE -> Ui.GOLD;
            default -> Ui.PINK;
        };
    }

    // ================================================================== цена
    private boolean owned(String ref) {
        return ref.isEmpty() || mode == Mode.FIRST || d().wardrobe.contains(ref) || ref.equals(start.get(slotOf(ref)));
    }

    private static StyleCatalog.Slot slotOf(String ref) {
        var w = StyleCatalog.wear(Appearance.id(ref));
        return w == null ? StyleCatalog.Slot.TOP : w.slot();
    }

    /** Можно ли купить здесь. */
    private boolean buyable(StyleCatalog.Wear w) {
        return mode == Mode.FIRST || mode == Mode.CHEAP && w.tier() == 0 || mode == Mode.LUX && w.tier() == 1;
    }

    private int clothesCost() {
        if (mode == Mode.FIRST) return 0;
        int c = 0;
        for (StyleCatalog.Slot s : StyleCatalog.Slot.values()) {
            String r = look.get(s);
            if (owned(r)) continue;
            var w = StyleCatalog.wear(Appearance.id(r));
            if (w != null) c += w.price();
        }
        return c;
    }

    /** Вещь, которую здесь не купить (или null). */
    private StyleCatalog.Wear blocked() {
        for (StyleCatalog.Slot s : StyleCatalog.Slot.values()) {
            String r = look.get(s);
            if (owned(r)) continue;
            var w = StyleCatalog.wear(Appearance.id(r));
            if (w != null && !buyable(w)) return w;
        }
        return null;
    }

    private int cost() {
        if (mode == Mode.FIRST) return 0;
        return clothesCost() + Style.bodyCost(start, look, mode == Mode.BARBER);
    }

    // ================================================================== строки панели
    private interface RowDraw {
        int draw(DrawContext ctx, int x, int y, int w, boolean visible);
    }

    private final List<RowDraw> rows = new ArrayList<>();

    private void label(DrawContext ctx, String s, int x, int y) {
        Ui.small(ctx, textRenderer, s, x, y + 2, 0.85f, 0xFFC9C7C1, 0);
    }

    private int labelW() {
        return Math.min(78, panelW / 3);
    }

    /** ‹ значение › — щелчок слева/справа листает. */
    private void stepper(DrawContext ctx, int x, int y, int w, String value, boolean visible, Runnable prev, Runnable next, int tag) {
        int h = 10;
        boolean hl = visible && hover(x, y, w, h);
        ctx.fill(x, y, x + w, y + h, hl ? 0x40FFFFFF : 0x24FFFFFF);
        ctx.fill(x, y + h - 1, x + w, y + h, Ui.alpha(accent(), 0.7f));
        ctx.drawText(textRenderer, "‹", x + 3, y + 1, 0xFFFFFFFF, false);
        ctx.drawText(textRenderer, "›", x + w - 7, y + 1, 0xFFFFFFFF, false);
        int maxW = w - 22 - (tag != 0 ? 20 : 0);
        String v = value;
        float sc = 0.8f;
        if (textRenderer.getWidth(v) * sc > maxW) v = textRenderer.trimToWidth(v, (int) (maxW / sc) - 4) + "…";
        Ui.small(ctx, textRenderer, v, x + w / 2f - (tag != 0 ? 8 : 0), y + 2, sc, 0xFFFFFFFF, 1);
        if (tag > 0) Ui.small(ctx, textRenderer, "$" + tag, x + w - 10, y + 2, 0.7f, Ui.GOLD, 2);
        else if (tag < 0) Ui.small(ctx, textRenderer, "$" + (-tag), x + w - 10, y + 2, 0.7f, 0xFF8A8985, 2);
        if (visible) {
            area(x, y, w / 2, h, prev);
            area(x + w / 2, y, w - w / 2, h, next);
        }
    }

    /** Образцы цвета; none — первый образец «нет». Возвращает высоту. */
    private int swatches(DrawContext ctx, int x, int y, int w, int[] colors, int sel, boolean none, boolean visible, IntConsumer set) {
        int s = 9, gap = 2, n = colors.length + (none ? 1 : 0);
        int perRow = Math.max(1, (w + gap) / (s + gap));
        for (int i = 0; i < n; i++) {
            int cx = x + (i % perRow) * (s + gap), cy = y + (i / perRow) * (s + gap);
            int idx = none ? i - 1 : i;
            boolean on = idx == sel;
            ctx.fill(cx - 1, cy - 1, cx + s + 1, cy + s + 1, on ? 0xFFFFFFFF : visible && hover(cx, cy, s, s) ? 0xFFB9B7B1 : 0xFF000000);
            if (idx < 0) {
                ctx.fill(cx, cy, cx + s, cy + s, 0xFF2A2A2A);
                for (int k = 0; k < s; k++) ctx.fill(cx + k, cy + s - 1 - k, cx + k + 1, cy + s - k, 0xFFD42A3A);
            } else ctx.fill(cx, cy, cx + s, cy + s, 0xFF000000 | colors[idx]);
            if (visible) area(cx - 1, cy - 1, s + 2, s + 2, () -> set.accept(idx));
        }
        return ((n + perRow - 1) / perRow) * (s + gap);
    }

    private void toggle(DrawContext ctx, int x, int y, boolean on, boolean visible, Runnable flip) {
        int w = 22, h = 10;
        ctx.fill(x, y, x + w, y + h, on ? Ui.alpha(accent(), 0.85f) : 0x30FFFFFF);
        int kx = on ? x + w - 9 : x + 1;
        ctx.fill(kx, y + 1, kx + 8, y + h - 1, 0xFFFFFFFF);
        if (visible) area(x, y, w + 40, h, flip);
    }

    private static int cycle(int v, int d, int n) {
        return Math.floorMod(v + d, n);
    }

    private static String[] names(List<StyleCatalog.Option> l) {
        String[] r = new String[l.size()];
        for (int i = 0; i < r.length; i++) r[i] = l.get(i).name();
        return r;
    }

    private static int[] colors(List<StyleCatalog.Option> l, int from) {
        int[] r = new int[l.size() - from];
        for (int i = from; i < l.size(); i++) r[i - from] = l.get(i).color();
        return r;
    }

    private void addStep(String label, String[] values, IntSupplier get, IntConsumer set) {
        rows.add((ctx, x, y, w, vis) -> {
            label(ctx, label, x, y);
            int lw = labelW();
            int v = get.getAsInt();
            stepper(ctx, x + lw, y, w - lw, values[Math.floorMod(v, values.length)], vis,
                    () -> set.accept(cycle(v, -1, values.length)), () -> set.accept(cycle(v, 1, values.length)), 0);
            return 13;
        });
    }

    private void addSwatch(String label, int[] colors, boolean none, IntSupplier get, IntConsumer set) {
        rows.add((ctx, x, y, w, vis) -> {
            label(ctx, label, x, y);
            int lw = labelW();
            return Math.max(13, swatches(ctx, x + lw, y + 1, w - lw, colors, get.getAsInt(), none, vis, set) + 2);
        });
    }

    private void addToggle(String label, java.util.function.BooleanSupplier get, Runnable flip) {
        rows.add((ctx, x, y, w, vis) -> {
            label(ctx, label, x, y);
            toggle(ctx, x + labelW(), y, get.getAsBoolean(), vis, flip);
            return 13;
        });
    }

    private void addSlider(String label, int min, int max, IntSupplier get, IntConsumer set) {
        rows.add((ctx, x, y, w, vis) -> {
            label(ctx, label, x, y);
            int lw = labelW(), sx = x + lw + 4, sw = w - lw - 30;
            int v = get.getAsInt();
            ctx.fill(sx, y + 4, sx + sw, y + 6, 0x40FFFFFF);
            float k = (v - min) / (float) (max - min);
            ctx.fill(sx, y + 4, sx + (int) (sw * k), y + 6, accent());
            int kx = sx + (int) (sw * k);
            ctx.fill(kx - 2, y + 1, kx + 3, y + 9, 0xFFFFFFFF);
            Ui.small(ctx, textRenderer, (v > 0 ? "+" : "") + v + "%", x + w - 2, y + 2, 0.75f, 0xFFFFFFFF, 2);
            if (vis) for (int i = min; i <= max; i++) {
                final int val = i;
                int px = sx + (int) (sw * (i - min) / (float) (max - min));
                area(px - sw / (2 * (max - min)) - 1, y, sw / (max - min) + 2, 10, () -> set.accept(val));
            }
            return 13;
        });
    }

    private void addHeader(String s) {
        rows.add((ctx, x, y, w, vis) -> {
            Ui.small(ctx, textRenderer, s, x, y + 3, 0.8f, accent(), 0);
            ctx.fill(x, y + 11, x + w, y + 12, Ui.alpha(accent(), 0.35f));
            return 15;
        });
    }

    /** Вещь слота: ‹ название › с ценой, под ней — цвета. */
    private void addWear(StyleCatalog.Slot slot) {
        rows.add((ctx, x, y, w, vis) -> {
            label(ctx, slot.title, x, y);
            int lw = labelW();
            List<StyleCatalog.Wear> list = allGenders ? StyleCatalog.of(slot, -1) : StyleCatalog.of(slot, -1, look.slim);
            String cur = look.get(slot);
            int idx = -1;
            for (int i = 0; i < list.size(); i++) if (list.get(i).id().equals(Appearance.id(cur))) idx = i;
            boolean opt = slot.optional();
            int n = list.size() + (opt ? 1 : 0);
            int pos = opt ? idx + 1 : Math.max(0, idx);
            StyleCatalog.Wear w0 = idx >= 0 ? list.get(idx) : null;
            String name = w0 == null ? "Нет" : w0.name();
            int tag = 0;
            if (w0 != null && !owned(cur)) tag = buyable(w0) ? w0.price() : -w0.price();
            final int p = pos;
            stepper(ctx, x + lw, y, w - lw, name, vis, () -> pickWear(slot, list, opt, cycle(p, -1, n)), () -> pickWear(slot, list, opt, cycle(p, 1, n)), tag);
            int h = 13;
            if (w0 != null && w0.colors().length > 1) {
                final StyleCatalog.Wear ww = w0;
                h += swatches(ctx, x + lw, y + 12, w - lw, w0.colors(), Appearance.colorIndex(cur), false, vis, c -> look.set(slot, ww.id() + "#" + c)) + 1;
            }
            return h;
        });
    }

    private void pickWear(StyleCatalog.Slot slot, List<StyleCatalog.Wear> list, boolean opt, int pos) {
        if (opt && pos == 0) {
            look.set(slot, "");
            return;
        }
        StyleCatalog.Wear w = list.get(opt ? pos - 1 : pos);
        // своя вещь этого вида — в её цвете, иначе первый цвет
        int c = 0;
        for (int k = 0; k < w.colors().length; k++) if (d().wardrobe.contains(w.id() + "#" + k)) {
            c = k;
            break;
        }
        look.set(slot, w.id() + "#" + c);
    }

    private void buildRows() {
        rows.clear();
        Appearance a = look;
        switch (tab) {
            case 0 -> {
                addStep("Пол", new String[]{"Мужской", "Женский"}, () -> a.slim ? 1 : 0, v -> setGender(v == 1));
                addSwatch("Кожа", StyleCatalog.SKIN_TONES, false, () -> a.skin, v -> a.skin = v);
                addStep("Телосложение", StyleCatalog.BUILDS, () -> a.build, v -> a.build = v);
                addSlider("Рост", -5, 5, () -> a.height, v -> a.height = v);
                addSwatch("Ногти", colors(StyleCatalog.NAIL_COLORS, 1), true, () -> a.nails - 1, v -> a.nails = v + 1);
            }
            case 1 -> {
                addStep("Глаза", names(StyleCatalog.EYE_SHAPES), () -> a.eyes, v -> a.eyes = v);
                addSwatch("Цвет глаз", colors(StyleCatalog.EYE_COLORS, 0), false, () -> a.eyeColor, v -> a.eyeColor = v);
                addStep("Брови", names(StyleCatalog.BROWS), () -> a.brows, v -> a.brows = v);
                addStep("Губы", names(StyleCatalog.MOUTHS), () -> a.mouth, v -> a.mouth = v);
                addSwatch("Помада", colors(StyleCatalog.LIP_COLORS, 1), true, () -> a.lipstick - 1, v -> a.lipstick = v + 1);
                addToggle("Ресницы", () -> a.lashes > 0, () -> a.lashes ^= 1);
                addToggle("Подводка", () -> a.liner > 0, () -> a.liner ^= 1);
                addToggle("Румянец", () -> a.blush > 0, () -> a.blush ^= 1);
                addToggle("Веснушки", () -> a.freckles > 0, () -> a.freckles ^= 1);
                addToggle("Мешки", () -> a.bags > 0, () -> a.bags ^= 1);
                addStep("Родинка", names(StyleCatalog.MOLES), () -> a.mole, v -> a.mole = v);
                addStep("Шрам", names(StyleCatalog.SCARS), () -> a.scar, v -> a.scar = v);
                addStep("Серьги", names(StyleCatalog.EARRINGS), () -> a.earrings, v -> a.earrings = v);
                addStep("Нос", names(StyleCatalog.NOSE), () -> a.nose, v -> a.nose = v);
                addStep("Тату на лице", names(StyleCatalog.FACE_TATS), () -> a.faceTat, v -> a.faceTat = v);
            }
            case 2 -> {
                List<StyleCatalog.Option> hair = new ArrayList<>();
                for (var o : StyleCatalog.HAIR) if (o.gender() == 0 || o.gender() == (a.slim ? 2 : 1)) hair.add(o);
                for (var o : StyleCatalog.HAIR) if (!hair.contains(o)) hair.add(o);
                String[] hn = names(hair);
                addStep("Причёска", hn, () -> {
                    for (int i = 0; i < hair.size(); i++) if (hair.get(i).id().equals(a.hair)) return i;
                    return 0;
                }, v -> a.hair = hair.get(v).id());
                if (StyleCatalog.hasLength(a.hair)) addStep("Длина", new String[]{"Короче", "Средняя", "Длиннее"}, () -> a.hairLen, v -> a.hairLen = v);
                if (StyleCatalog.hasFringe(a.hair)) addToggle("Чёлка", () -> a.fringe, () -> a.fringe = !a.fringe);
                addSwatch("Цвет", colors(StyleCatalog.HAIR_COLORS, 0), false, () -> a.hairColor, v -> a.hairColor = v);
                addSwatch("Пряди", colors(StyleCatalog.HAIR_COLORS, 0), true, () -> a.highlight, v -> a.highlight = v);
                if (!a.slim || !a.beard.equals("none")) addStep("Борода", names(StyleCatalog.BEARDS), () -> StyleCatalog.indexOf(StyleCatalog.BEARDS, a.beard),
                        v -> a.beard = StyleCatalog.BEARDS.get(v).id());
            }
            case 3 -> {
                addToggle("Все вещи", () -> allGenders, () -> allGenders = !allGenders);
                for (StyleCatalog.Slot s : new StyleCatalog.Slot[]{StyleCatalog.Slot.TOP, StyleCatalog.Slot.OUTER, StyleCatalog.Slot.PANTS, StyleCatalog.Slot.SHOES}) addWear(s);
                addHeader("КАК НОСИТЬ");
                addStep("Узор", names(StyleCatalog.PATTERNS), () -> a.pattern, v -> a.pattern = v);
                addToggle("Заправить", () -> a.tuck, () -> a.tuck = !a.tuck);
                addToggle("Рукава", () -> a.rolled, () -> a.rolled = !a.rolled);
                addToggle("Нараспашку", () -> a.open, () -> a.open = !a.open);
                addStep("Износ", StyleCatalog.GRIME, () -> a.grime, v -> a.grime = v);
            }
            case 4 -> {
                addToggle("Все вещи", () -> allGenders, () -> allGenders = !allGenders);
                for (StyleCatalog.Slot s : new StyleCatalog.Slot[]{StyleCatalog.Slot.HAT, StyleCatalog.Slot.GLASSES, StyleCatalog.Slot.NECK, StyleCatalog.Slot.BAG,
                        StyleCatalog.Slot.WRIST}) addWear(s);
            }
            case 5 -> {
                for (int i = 0; i < 4; i++) {
                    final int k = i;
                    addStep(StyleCatalog.TAT_SLOTS[i], names(StyleCatalog.TATS), () -> a.tats[k], v -> a.tats[k] = v);
                }
            }
            default -> {
                for (int i = 0; i < Style.PRESETS; i++) {
                    final int k = i;
                    rows.add((ctx, x, y, w, vis) -> {
                        boolean has = k < d().presets.size();
                        Ui.small(ctx, textRenderer, "ОБРАЗ " + (k + 1), x, y + 4, 0.8f, has ? 0xFFFFFFFF : 0xFF8A8985, 0);
                        int bx = x + labelW(), bw = (w - labelW() - 4) / 3;
                        if (has) {
                            Appearance p = d().presets.get(k);
                            ctx.fill(x + 46, y + 2, x + 52, y + 8, 0xFF000000 | StyleCatalog.tone(p.skin));
                            ctx.fill(x + 54, y + 2, x + 60, y + 8, 0xFF000000 | StyleCatalog.hairColor(p.hairColor));
                            var tw = StyleCatalog.wear(Appearance.id(p.top));
                            if (tw != null) ctx.fill(x + 62, y + 2, x + 68, y + 8, 0xFF000000 | tw.color(Appearance.colorIndex(p.top)));
                        }
                        if (vis) {
                            button(ctx, bx, y, bw, 12, "НАДЕТЬ", accent(), has, () -> look = d().presets.get(k).copy());
                            button(ctx, bx + bw + 2, y, bw, 12, "СОХРАНИТЬ", Ui.GOLD, true, () -> {
                                NbtCompound n = new NbtCompound();
                                n.putInt("slot", Math.min(k, d().presets.size()));
                                n.put("look", look.toNbt());
                                StyleClient.send("preset_save", n);
                            });
                            button(ctx, bx + 2 * (bw + 2), y, bw, 12, "×", 0xFF8A8985, has, () -> {
                                NbtCompound n = new NbtCompound();
                                n.putInt("slot", k);
                                StyleClient.send("preset_del", n);
                            });
                        }
                        return 16;
                    });
                }
            }
        }
    }

    private void setGender(boolean female) {
        if (look.slim == female) return;
        look.slim = female;
        if (female) {
            look.lashes = 1;
            look.beard = "none";
            if (look.brows == 4) look.brows = 0;
        } else {
            look.lashes = 0;
            look.lipstick = 0;
        }
        // вещи другого пола — на похожие своего
        for (StyleCatalog.Slot s : StyleCatalog.Slot.values()) {
            var w = StyleCatalog.wear(Appearance.id(look.get(s)));
            if (w != null && !w.fits(female)) look.set(s, s.optional() ? "" : switch (s) {
                case TOP -> "tee#0";
                case PANTS -> "jeans#0";
                default -> "sneakers#0";
            });
        }
        var h = StyleCatalog.find(StyleCatalog.HAIR, look.hair);
        if (h != null && h.gender() != 0 && h.gender() != (female ? 2 : 1)) look.hair = female ? "long" : "short";
    }

    // ================================================================== отрисовка
    @Override
    protected void draw(DrawContext ctx, float delta) {
        float an = openAnim();
        int acc = accent();
        StyleClient.preview = look;
        // ---- шапка
        int hy = 4 + (int) ((1 - an) * -14);
        String title = switch (mode) {
            case FIRST -> "НОВЫЙ ПЕРСОНАЖ";
            case BARBER -> StyleCatalog.BARBER_NAME.toUpperCase();
            case CHEAP -> StyleCatalog.CHEAP_NAME.toUpperCase();
            case LUX -> StyleCatalog.LUX_NAME.toUpperCase();
            default -> "ВНЕШНОСТЬ";
        };
        float hs = width < 460 ? 1.0f : 1.3f;
        int tw = (int) (textRenderer.getWidth(title) * hs) + 16;
        Ui.slant(ctx, 6, hy, tw, (int) (10 * hs) + 4, 5, acc);
        Ui.big(ctx, textRenderer, title, 13, hy + 3, hs, Ui.WHITE, 0);
        if (mode != Mode.FIRST) Ui.big(ctx, textRenderer, "$" + LifeHud.money(d().money), width - 8, hy + 3, hs, Ui.MONEY, 2);
        // ---- сцена
        int top = hy + (int) (10 * hs) + 10;
        stageX = 6;
        stageY = top;
        stageW = (int) (width * 0.36f);
        stageH = height - top - 24;
        drawStage(ctx, acc, an);
        // ---- вкладки
        panelX = stageX + stageW + 6;
        panelW = width - panelX - 6;
        int tx = panelX, ty = top;
        for (int i = 0; i < TABS.length; i++) {
            float sc = width < 520 ? 0.7f : 0.8f;
            int w = (int) (textRenderer.getWidth(TABS[i]) * sc) + 8;
            if (tx + w > panelX + panelW) {
                tx = panelX;
                ty += 13;
            }
            boolean hov = hover(tx, ty, w, 11);
            tabAnim[i] = Ui.approach(tabAnim[i], i == tab ? 1 : hov ? 0.4f : 0, 0.3f);
            Ui.slant(ctx, tx, ty, w, 11, 3, 0xC0161615);
            if (tabAnim[i] > 0.02f) Ui.slant(ctx, tx, ty, (int) (w * tabAnim[i]), 11, 3, i == tab ? acc : Ui.alpha(acc, 0.5f));
            Ui.small(ctx, textRenderer, TABS[i], tx + 4, ty + 2.5f, sc, i == tab ? Ui.WHITE : 0xFFB9B7B1, 0);
            final int t = i;
            area(tx, ty, w, 11, () -> {
                tab = t;
                scroll = 0;
            });
            tx += w + 2;
        }
        // ---- строки
        panelY = ty + 15;
        panelH = height - panelY - 24;
        Ui.panel(ctx, panelX - 2, panelY - 2, panelW + 4, panelH + 4, Ui.alpha(acc, an * 0.6f));
        buildRows();
        int y = panelY + 2 - scroll;
        int px = panelX + 4, pw = panelW - 10;
        ctx.enableScissor(panelX, panelY, panelX + panelW, panelY + panelH);
        int total = 0;
        for (RowDraw r : rows) {
            boolean vis = y >= panelY - 2 && y + 10 <= panelY + panelH;
            int h = r.draw(ctx, px, y, pw, vis);
            y += h;
            total += h;
        }
        ctx.disableScissor();
        contentH = total + 4;
        if (contentH > panelH) {
            int bh = Math.max(10, panelH * panelH / contentH);
            int by = panelY + (panelH - bh) * scroll / Math.max(1, contentH - panelH);
            ctx.fill(panelX + panelW - 2, panelY, panelX + panelW, panelY + panelH, 0x30FFFFFF);
            ctx.fill(panelX + panelW - 2, by, panelX + panelW, by + bh, acc);
        }
        // ---- низ
        ctx.fill(0, height - 20, width, height, 0xE0040404);
        ctx.fill(0, height - 21, width, height - 20, Ui.alpha(acc, 0.8f));
        int bw = Math.min(90, (width - 24) / 4);
        button(ctx, 6, height - 17, bw, 14, "СЛУЧАЙНО", 0xFF7E57C2, true, this::randomize);
        button(ctx, 10 + bw, height - 17, bw, 14, mode == Mode.FIRST ? "ПРОПУСТИТЬ" : "ОТМЕНА", 0xFF8A8985, true, this::cancel);
        StyleCatalog.Wear blocked = blocked();
        int cost = cost();
        String done = blocked != null ? (blocked.tier() == 0 ? StyleCatalog.CHEAP_NAME : StyleCatalog.LUX_NAME) : cost > 0 ? "ГОТОВО · $" + cost : "ГОТОВО";
        boolean can = blocked == null && (mode == Mode.FIRST || cost <= d().money);
        int dw = Math.min(150, width / 3);
        button(ctx, width - dw - 6, height - 17, dw, 14, done, acc, can, this::apply);
    }

    private void drawStage(DrawContext ctx, int acc, float a) {
        int x = stageX, y = stageY, w = stageW, h = stageH;
        ctx.fill(x, y, x + w, y + h, 0x90060606);
        int cx = x + w / 2;
        for (int i = 0; i < 16; i++) {
            float k = i / 16f;
            int half = (int) (w * (0.12f + k * 0.38f));
            int al = (int) (36 * (1 - k) * a);
            ctx.fill(cx - half, y + (int) (k * h * 0.86f), cx + half, y + (int) ((k + 1 / 16f) * h * 0.86f), al << 24 | (acc & 0xFFFFFF));
        }
        int floorY = y + h - 14;
        for (int i = 0; i < 5; i++) {
            int rw = (int) (w * 0.34f) - i * 5, rh = 6 - i;
            ctx.fill(cx - rw, floorY - rh / 2, cx + rw, floorY + rh / 2 + 1, Ui.alpha(acc, 0.06f + i * 0.04f));
        }
        ctx.fill(x, y, x + w, y + 1, Ui.alpha(acc, 0.6f));
        if (!dragging && System.currentTimeMillis() - idleSince > 3000) yaw += 0.3f;
        float base = Math.min(h * 0.36f, w * 0.62f);
        float size = base * zoom;
        // приближение — к лицу: опускаем героя, чтобы голова оставалась в кадре
        float feet = floorY + (zoom - 1f) * base * 1.55f;
        ctx.enableScissor(x, y, x + w, y + h);
        StyleClient.walkPreview = walk;
        StyleClient.walkPhase = (System.currentTimeMillis() % 100000L) / 1000f * 4.2f;
        try {
            StyleShopScreen.hero(ctx, cx, feet, size, yaw);
        } finally {
            StyleClient.walkPreview = false;
        }
        ctx.disableScissor();
        // кнопки сцены
        int bs = 13;
        button(ctx, x + 3, y + h - bs - 3, bs + 2, bs, "‹", acc, true, () -> rot(-45));
        button(ctx, x + w - bs - 5, y + h - bs - 3, bs + 2, bs, "›", acc, true, () -> rot(45));
        button(ctx, x + 3, y + 3, 30, 11, walk ? "ШАГ" : "СТОП", walk ? acc : 0xFF8A8985, true, () -> walk = !walk);
        button(ctx, x + w - 33, y + 3, 30, 11, zoom > 1.3f ? "−" : "+", acc, true, () -> zoom = zoom > 1.3f ? 1f : 2.4f);
    }

    private void rot(float d) {
        yaw += d;
        idleSince = System.currentTimeMillis();
    }

    private void randomize() {
        seed = seed * 6364136223846793005L + 1442695040888963407L;
        int profile = (int) Math.floorMod(seed >>> 33, LookGen.PROFILE_NAMES.length);
        look = LookGen.generate(seed, look.slim, profile, false);
    }

    private void apply() {
        NbtCompound n = new NbtCompound();
        n.put("look", look.toNbt());
        n.putString("mode", switch (mode) {
            case FIRST -> "first";
            case BARBER -> "barber";
            case CHEAP -> "cheap";
            case LUX -> "lux";
            case WARDROBE -> "wardrobe";
            default -> "phone";
        });
        StyleClient.send("creator_apply", n);
        close();
    }

    private void cancel() {
        if (mode == Mode.FIRST) StyleClient.send("creator_skip", new NbtCompound());
        close();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return mode != Mode.FIRST;
    }

    @Override
    public void removed() {
        StyleClient.preview = null;
        StyleClient.walkPreview = false;
        super.removed();
    }

    // ================================================================== управление
    private boolean onStage(double mx, double my) {
        return mx >= stageX && mx < stageX + stageW && my >= stageY + 16 && my < stageY + stageH - 18;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && onStage(mx, my)) {
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
        if (onStage(mx, my)) {
            zoom = MathHelper.clamp(zoom + (float) v * 0.2f, 0.8f, 2.6f);
            return true;
        }
        scroll = MathHelper.clamp(scroll - (int) (v * 14), 0, Math.max(0, contentH - panelH));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        switch (key) {
            case GLFW.GLFW_KEY_TAB -> {
                tab = Math.floorMod(tab + ((mods & GLFW.GLFW_MOD_SHIFT) != 0 ? -1 : 1), TABS.length);
                scroll = 0;
                click();
                return true;
            }
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_A -> {
                rot(-45);
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_D -> {
                rot(45);
                return true;
            }
            case GLFW.GLFW_KEY_R -> {
                randomize();
                click();
                return true;
            }
            default -> {
                return super.keyPressed(key, scan, mods);
            }
        }
    }
}
