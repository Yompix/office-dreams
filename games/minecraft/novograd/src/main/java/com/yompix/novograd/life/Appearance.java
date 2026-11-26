package com.yompix.novograd.life;

import net.minecraft.nbt.NbtCompound;

/**
 * Внешность героя: пол и тело, лицо, причёска, что надето (верх, куртка, низ, обувь, головной убор, очки, шея, сумка,
 * часы), тату и мелочи (помада, лак, пирсинг, веснушки). Вещь записывается как «id#цвет» (см. {@link StyleCatalog}).
 * Пока {@link #custom} = false, у игрока обычный скин Minecraft.
 *
 * <p>0.9.6: формат v2. Сохранения 0.9.5 читаются: 6 тонов кожи переводятся в 10, куртки из «верха» уходят в слот
 * «Куртки», пол — по прежнему «стройному» телосложению ({@link #fromNbt}).
 */
public class Appearance {
    public static final int VERSION = 2;

    public boolean custom;
    /** Женский персонаж: тонкие руки (модель «slim»), чуть уже корпус. Имя поля — с 0.4 (было «стройное»). */
    public boolean slim;
    public int skin = 2;
    /** Цвет кожи выбран (первый раз в парикмахерской — бесплатно). */
    public boolean skinChosen;
    /** Телосложение 0 худое, 1 среднее, 2 спортивное, 3 плотное; рост −5…+5 % (хитбокс не меняется). */
    public int build = 1, height;
    // ---- лицо
    public int eyes = 1, eyeColor = 1, brows = 1, mouth;
    public int freckles, mole, scar, bags, lashes, blush, lipstick, liner, earrings, nose, faceTat;
    // ---- причёска
    public String hair = "short", beard = "none";
    public int hairColor = 1, highlight = -1, hairLen = 1;
    public boolean fringe = true;
    // ---- одежда
    public String top = "tee#0", outer = "", pants = "jeans#0", shoes = "sneakers#0", hat = "", glasses = "", neck = "", bag = "", wrist = "";
    public boolean tuck, rolled, open = true;
    public int pattern, grime;
    // ---- тату и ногти
    public int[] tats = new int[4];
    public int nails;

    public boolean female() {
        return slim;
    }

    public static String id(String ref) {
        int i = ref.indexOf('#');
        return i < 0 ? ref : ref.substring(0, i);
    }

    public static int colorIndex(String ref) {
        int i = ref.indexOf('#');
        if (i < 0) return 0;
        try {
            return Integer.parseInt(ref.substring(i + 1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public String get(StyleCatalog.Slot slot) {
        return switch (slot) {
            case TOP -> top;
            case OUTER -> outer;
            case PANTS -> pants;
            case SHOES -> shoes;
            case HAT -> hat;
            case GLASSES -> glasses;
            case NECK -> neck;
            case BAG -> bag;
            case WRIST -> wrist;
        };
    }

    public void set(StyleCatalog.Slot slot, String ref) {
        switch (slot) {
            case TOP -> top = ref;
            case OUTER -> outer = ref;
            case PANTS -> pants = ref;
            case SHOES -> shoes = ref;
            case HAT -> hat = ref;
            case GLASSES -> glasses = ref;
            case NECK -> neck = ref;
            case BAG -> bag = ref;
            case WRIST -> wrist = ref;
        }
    }

    /**
     * Ключ для кэша текстур: всё, что рисуется на скине (головной убор, очки, шея и сумка — объёмные, их тут нет).
     * Одинаковая внешность — одна текстура.
     */
    public String key() {
        return (slim ? "f" : "m") + skin + "|" + eyes + eyeColor + brows + mouth + "|" + freckles + mole + scar + bags + lashes + blush + lipstick + liner
                + earrings + nose + faceTat + "|" + hair + "|" + hairColor + "|" + highlight + "|" + (fringe ? 1 : 0) + "|" + beard
                + "|" + top + "|" + outer + "|" + pants + "|" + shoes + "|" + wrist + "|" + (tuck ? 1 : 0) + (rolled ? 1 : 0) + (open ? 1 : 0) + pattern + grime
                + "|" + tats[0] + tats[1] + tats[2] + tats[3] + nails;
    }

    public Appearance copy() {
        return fromNbt(toNbt());
    }

    public NbtCompound toNbt() {
        NbtCompound n = new NbtCompound();
        n.putInt("v", VERSION);
        n.putBoolean("custom", custom);
        n.putBoolean("slim", slim);
        n.putInt("skin", skin);
        n.putBoolean("skinChosen", skinChosen);
        n.putByte("build", (byte) build);
        n.putByte("height", (byte) height);
        n.putByteArray("face", new byte[]{(byte) eyes, (byte) eyeColor, (byte) brows, (byte) mouth, (byte) freckles, (byte) mole, (byte) scar, (byte) bags,
                (byte) lashes, (byte) blush, (byte) lipstick, (byte) liner, (byte) earrings, (byte) nose, (byte) faceTat});
        n.putString("top", top);
        n.putString("outer", outer);
        n.putString("pants", pants);
        n.putString("shoes", shoes);
        n.putString("hat", hat);
        n.putString("glasses", glasses);
        n.putString("neck", neck);
        n.putString("bag", bag);
        n.putString("wrist", wrist);
        n.putString("hair", hair);
        n.putString("beard", beard);
        n.putInt("hairColor", hairColor);
        n.putByte("highlight", (byte) highlight);
        n.putByte("hairLen", (byte) hairLen);
        n.putBoolean("fringe", fringe);
        n.putByteArray("wear", new byte[]{(byte) (tuck ? 1 : 0), (byte) (rolled ? 1 : 0), (byte) (open ? 1 : 0), (byte) pattern, (byte) grime});
        n.putByteArray("tats", new byte[]{(byte) tats[0], (byte) tats[1], (byte) tats[2], (byte) tats[3], (byte) nails});
        return n;
    }

    private static int b(byte[] a, int i, int def) {
        return a != null && i < a.length ? a[i] : def;
    }

    public static Appearance fromNbt(NbtCompound n) {
        Appearance a = new Appearance();
        if (n == null || n.isEmpty()) return a;
        int v = n.getInt("v");
        a.custom = n.getBoolean("custom");
        a.slim = n.getBoolean("slim");
        if (n.contains("skin")) a.skin = n.getInt("skin");
        if (v < 2) a.skin = StyleCatalog.OLD_TONE[Math.floorMod(a.skin, StyleCatalog.OLD_TONE.length)];
        a.skin = Math.floorMod(a.skin, StyleCatalog.SKIN_TONES.length);
        a.skinChosen = n.getBoolean("skinChosen");
        if (n.contains("build")) a.build = clamp(n.getByte("build"), 0, 3);
        a.height = clamp(n.getByte("height"), -5, 5);
        byte[] f = n.getByteArray("face");
        a.eyes = b(f, 0, 1);
        a.eyeColor = b(f, 1, a.skin >= 6 ? 0 : 1);
        a.brows = b(f, 2, 1);
        a.mouth = b(f, 3, 0);
        a.freckles = b(f, 4, 0);
        a.mole = b(f, 5, 0);
        a.scar = b(f, 6, 0);
        a.bags = b(f, 7, 0);
        a.lashes = b(f, 8, a.slim ? 1 : 0);
        a.blush = b(f, 9, 0);
        a.lipstick = b(f, 10, v < 2 && a.slim ? 2 : 0);
        a.liner = b(f, 11, 0);
        a.earrings = b(f, 12, 0);
        a.nose = b(f, 13, 0);
        a.faceTat = b(f, 14, 0);
        if (n.contains("top")) a.top = n.getString("top");
        a.outer = n.getString("outer");
        if (n.contains("pants")) a.pants = n.getString("pants");
        if (n.contains("shoes")) a.shoes = n.getString("shoes");
        a.hat = n.getString("hat");
        a.glasses = n.getString("glasses");
        a.neck = n.getString("neck");
        a.bag = n.getString("bag");
        a.wrist = n.getString("wrist");
        if (n.contains("hair")) a.hair = n.getString("hair");
        if (n.contains("beard")) a.beard = n.getString("beard");
        if (n.contains("hairColor")) a.hairColor = n.getInt("hairColor");
        a.highlight = n.contains("highlight") ? n.getByte("highlight") : -1;
        if (n.contains("hairLen")) a.hairLen = clamp(n.getByte("hairLen"), 0, 2);
        if (n.contains("fringe")) a.fringe = n.getBoolean("fringe");
        byte[] w = n.getByteArray("wear");
        a.tuck = b(w, 0, 0) == 1;
        a.rolled = b(w, 1, 0) == 1;
        a.open = b(w, 2, 1) == 1;
        a.pattern = b(w, 3, 0);
        a.grime = b(w, 4, 0);
        byte[] t = n.getByteArray("tats");
        for (int i = 0; i < 4; i++) a.tats[i] = b(t, i, 0);
        a.nails = b(t, 4, 0);
        if (v < 2) {
            // 0.9.5: куртки были «верхом» — теперь это слой поверх футболки
            var wr = StyleCatalog.wear(id(a.top));
            if (wr != null && wr.slot() == StyleCatalog.Slot.OUTER) {
                a.outer = a.top;
                a.top = "tee#0";
            }
        }
        a.sanitize();
        return a;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** Всё в допустимых пределах (данные от клиента и из старых миров). */
    public void sanitize() {
        skin = Math.floorMod(skin, StyleCatalog.SKIN_TONES.length);
        build = clamp(build, 0, 3);
        height = clamp(height, -5, 5);
        eyes = clamp(eyes, 0, StyleCatalog.EYE_SHAPES.size() - 1);
        eyeColor = clamp(eyeColor, 0, StyleCatalog.EYE_COLORS.size() - 1);
        brows = clamp(brows, 0, StyleCatalog.BROWS.size() - 1);
        mouth = clamp(mouth, 0, StyleCatalog.MOUTHS.size() - 1);
        freckles = clamp(freckles, 0, 1);
        mole = clamp(mole, 0, StyleCatalog.MOLES.size() - 1);
        scar = clamp(scar, 0, StyleCatalog.SCARS.size() - 1);
        bags = clamp(bags, 0, 1);
        lashes = clamp(lashes, 0, 1);
        blush = clamp(blush, 0, 1);
        lipstick = clamp(lipstick, 0, StyleCatalog.LIP_COLORS.size() - 1);
        liner = clamp(liner, 0, 1);
        earrings = clamp(earrings, 0, StyleCatalog.EARRINGS.size() - 1);
        nose = clamp(nose, 0, StyleCatalog.NOSE.size() - 1);
        faceTat = clamp(faceTat, 0, StyleCatalog.FACE_TATS.size() - 1);
        if (StyleCatalog.find(StyleCatalog.HAIR, hair) == null) hair = "short";
        if (StyleCatalog.find(StyleCatalog.BEARDS, beard) == null) beard = "none";
        hairColor = Math.floorMod(hairColor, StyleCatalog.HAIR_COLORS.size());
        if (highlight >= StyleCatalog.HAIR_COLORS.size() || highlight < -1) highlight = -1;
        hairLen = clamp(hairLen, 0, 2);
        pattern = clamp(pattern, 0, StyleCatalog.PATTERNS.size() - 1);
        grime = clamp(grime, 0, StyleCatalog.GRIME.length - 1);
        for (int i = 0; i < 4; i++) tats[i] = clamp(tats[i], 0, StyleCatalog.TATS.size() - 1);
        nails = clamp(nails, 0, StyleCatalog.NAIL_COLORS.size() - 1);
        for (StyleCatalog.Slot s : StyleCatalog.Slot.values()) {
            String ref = get(s);
            if (ref == null) ref = "";
            var w = ref.isEmpty() ? null : StyleCatalog.wear(id(ref));
            if (w == null || w.slot() != s) {
                if (!ref.isEmpty() || !s.optional()) set(s, s.optional() ? "" : switch (s) {
                    case TOP -> "tee#0";
                    case PANTS -> "jeans#0";
                    default -> "sneakers#0";
                });
            }
        }
    }

    /** Отличается ли тело/лицо/причёска (то, за что платят в цирюльне), без одежды. */
    public boolean sameBody(Appearance o) {
        Appearance x = copy(), y = o.copy();
        for (StyleCatalog.Slot s : StyleCatalog.Slot.values()) {
            x.set(s, "");
            y.set(s, "");
        }
        x.tuck = y.tuck = x.rolled = y.rolled = x.open = y.open = false;
        x.pattern = y.pattern = x.grime = y.grime = 0;
        x.custom = y.custom = x.skinChosen = y.skinChosen = false;
        return x.toNbt().equals(y.toNbt());
    }
}
