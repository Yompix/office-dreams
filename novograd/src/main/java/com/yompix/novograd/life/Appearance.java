package com.yompix.novograd.life;

import net.minecraft.nbt.NbtCompound;

/**
 * Внешность героя в духе SA: что надето (верх, низ, обувь, головной убор, очки), причёска, цвет волос, борода,
 * цвет кожи и телосложение. Вещь записывается как «id#цвет» (см. {@link StyleCatalog}).
 * Пока {@link #custom} = false, у игрока обычный скин Minecraft.
 */
public class Appearance {
    public boolean custom;
    /** Стройное телосложение — тонкие руки (модель «slim»). */
    public boolean slim;
    public int skin = 1;
    /** Цвет кожи выбран (первый раз в парикмахерской — бесплатно). */
    public boolean skinChosen;
    public String top = "tee#0", pants = "jeans#0", shoes = "sneakers#0", hat = "", glasses = "";
    public String hair = "short", beard = "none";
    public int hairColor = 1;

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
            case PANTS -> pants;
            case SHOES -> shoes;
            case HAT -> hat;
            case GLASSES -> glasses;
        };
    }

    public void set(StyleCatalog.Slot slot, String ref) {
        switch (slot) {
            case TOP -> top = ref;
            case PANTS -> pants = ref;
            case SHOES -> shoes = ref;
            case HAT -> hat = ref;
            case GLASSES -> glasses = ref;
        }
    }

    /** Ключ для кэша текстур: одинаковая внешность — одна текстура. */
    public String key() {
        return (slim ? "s" : "w") + skin + "|" + top + "|" + pants + "|" + shoes + "|" + hair + "|" + hairColor + "|" + beard;
    }

    public Appearance copy() {
        return fromNbt(toNbt());
    }

    public NbtCompound toNbt() {
        NbtCompound n = new NbtCompound();
        n.putBoolean("custom", custom);
        n.putBoolean("slim", slim);
        n.putInt("skin", skin);
        n.putBoolean("skinChosen", skinChosen);
        n.putString("top", top);
        n.putString("pants", pants);
        n.putString("shoes", shoes);
        n.putString("hat", hat);
        n.putString("glasses", glasses);
        n.putString("hair", hair);
        n.putString("beard", beard);
        n.putInt("hairColor", hairColor);
        return n;
    }

    public static Appearance fromNbt(NbtCompound n) {
        Appearance a = new Appearance();
        if (n == null || n.isEmpty()) return a;
        a.custom = n.getBoolean("custom");
        a.slim = n.getBoolean("slim");
        if (n.contains("skin")) a.skin = n.getInt("skin");
        a.skinChosen = n.getBoolean("skinChosen");
        if (n.contains("top")) a.top = n.getString("top");
        if (n.contains("pants")) a.pants = n.getString("pants");
        if (n.contains("shoes")) a.shoes = n.getString("shoes");
        a.hat = n.getString("hat");
        a.glasses = n.getString("glasses");
        if (n.contains("hair")) a.hair = n.getString("hair");
        if (n.contains("beard")) a.beard = n.getString("beard");
        if (n.contains("hairColor")) a.hairColor = n.getInt("hairColor");
        return a;
    }
}
