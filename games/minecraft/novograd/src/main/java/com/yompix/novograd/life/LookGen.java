package com.yompix.novograd.life;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 0.9.6: генератор внешности — один и тот же у сервера и клиента (житель передаёт только число-зерно).
 *
 * <p>Зерно жителя: бит 0 — женщина, биты 1–4 — профиль (район/работа), остальное — случайность. Сервер подбирает
 * зерно так, чтобы житель не был похож на героя сюжета ({@link #citizenSeed}); клиент просто разворачивает его
 * ({@link #citizen}). Жителям не достаются «подписи» героев (берет, шарф, цепь, плащ, федора, бандана, серьги-подвески,
 * кепка назад) — их носит только игрок и герои.
 */
public final class LookGen {
    private LookGen() {}

    public static final int CASUAL = 0, BUSINESS = 1, BEACH = 2, WORKER = 3, STREET = 4, SPORT = 5, RESORT = 6, WINTER = 7, BIKER = 8, SUBURB = 9;
    public static final String[] PROFILE_NAMES = {"повседневно", "деловой", "пляж", "рабочий", "улица", "спорт", "курорт", "зима", "байкер", "пригород"};

    /** Стили одежды профиля. */
    static int tags(int profile) {
        return switch (profile) {
            case BUSINESS -> StyleCatalog.BU | StyleCatalog.FO;
            case BEACH -> StyleCatalog.BE;
            case WORKER -> StyleCatalog.WO;
            case STREET -> StyleCatalog.ST;
            case SPORT -> StyleCatalog.SP;
            case RESORT -> StyleCatalog.BE | StyleCatalog.FO | StyleCatalog.CA;
            case WINTER -> StyleCatalog.WI;
            case BIKER -> StyleCatalog.BI;
            default -> StyleCatalog.CA | StyleCatalog.ST;
        };
    }

    // ------------------------------------------------------------------ «подписи» героев: жителям нельзя
    private static final java.util.Set<String> HERO_ONLY = java.util.Set.of("fedora", "bandana", "beret", "cap_back", "coat", "trench",
            "chain_gold", "chain_silver", "scarf", "neck_bandana");

    private static <T> T pick(Random r, List<T> l) {
        return l.get(r.nextInt(l.size()));
    }

    private static String ref(Random r, StyleCatalog.Wear w) {
        return w.id() + "#" + r.nextInt(w.colors().length);
    }

    /** Вещь слота под пол и стили (если подходящих нет — любая вещь пола). */
    private static StyleCatalog.Wear wear(Random r, StyleCatalog.Slot slot, boolean female, int tags, boolean citizen) {
        List<StyleCatalog.Wear> ok = new ArrayList<>(), any = new ArrayList<>();
        for (StyleCatalog.Wear w : StyleCatalog.of(slot, -1, female)) {
            if (citizen && HERO_ONLY.contains(w.id())) continue;
            if (citizen && w.id().equals("swimsuit") && (tags & StyleCatalog.BE) == 0) continue;
            any.add(w);
            if ((w.tags() & tags) != 0) ok.add(w);
        }
        if (ok.isEmpty()) ok = any;
        return ok.isEmpty() ? null : pick(r, ok);
    }

    /**
     * Случайная внешность (и для кнопки «Случайно» в редакторе: citizen = false — без запретов).
     * Одинаковое зерно — одинаковый результат на любой машине.
     */
    public static Appearance generate(long seed, boolean female, int profile, boolean citizen) {
        Random r = new Random(seed * 0x5DEECE66DL + 11);
        Appearance a = new Appearance();
        a.custom = true;
        a.skinChosen = true;
        a.slim = female;
        int tags = tags(profile);
        // ---- тело
        a.skin = r.nextInt(StyleCatalog.SKIN_TONES.length);
        int b = r.nextInt(100);
        a.build = b < 22 ? 0 : b < 66 ? 1 : b < 84 ? 2 : 3;
        if (profile == SPORT && a.build == 3) a.build = 2;
        a.height = r.nextInt(9) - 4;
        // ---- лицо
        a.eyes = r.nextInt(StyleCatalog.EYE_SHAPES.size());
        a.eyeColor = a.skin >= 6 ? r.nextInt(3) == 0 ? 7 : r.nextInt(2) : r.nextInt(StyleCatalog.EYE_COLORS.size());
        a.brows = female ? (r.nextBoolean() ? 0 : 3) : 1 + r.nextInt(4);
        a.mouth = r.nextInt(StyleCatalog.MOUTHS.size());
        a.freckles = a.skin <= 3 && r.nextInt(4) == 0 ? 1 : 0;
        a.mole = r.nextInt(6) == 0 ? 1 + r.nextInt(2) : 0;
        a.scar = !female && r.nextInt(10) == 0 ? 1 + r.nextInt(3) : 0;
        a.bags = r.nextInt(5) == 0 ? 1 : 0;
        if (female) {
            a.lashes = 1;
            a.lipstick = r.nextInt(10) < 6 ? 1 + r.nextInt(StyleCatalog.LIP_COLORS.size() - 1) : 0;
            a.liner = r.nextInt(4) == 0 ? 1 : 0;
            a.blush = r.nextInt(3) == 0 ? 1 : 0;
            a.earrings = r.nextInt(2) == 0 ? 1 : citizen ? 0 : r.nextInt(StyleCatalog.EARRINGS.size());
            a.nails = r.nextInt(3) == 0 ? 1 + r.nextInt(StyleCatalog.NAIL_COLORS.size() - 1) : 0;
        } else if (!citizen && r.nextInt(8) == 0) a.earrings = 1;
        a.nose = r.nextInt(14) == 0 ? 1 + r.nextInt(2) : 0;
        a.faceTat = profile == STREET && r.nextInt(8) == 0 ? 1 + r.nextInt(3) : 0;
        // ---- причёска
        List<StyleCatalog.Option> hair = new ArrayList<>();
        for (StyleCatalog.Option o : StyleCatalog.HAIR) if (o.gender() == 0 || o.gender() == (female ? 2 : 1)) hair.add(o);
        a.hair = pick(r, hair).id();
        a.hairLen = r.nextInt(3);
        a.fringe = r.nextInt(4) != 0;
        int hc = r.nextInt(100);
        a.hairColor = hc < (citizen ? 5 : 12) ? 7 + r.nextInt(4) : StyleCatalog.NATURAL_HAIR[r.nextInt(StyleCatalog.NATURAL_HAIR.length)];
        if (a.skin >= 6 && hc >= 12 && r.nextInt(3) != 0) a.hairColor = r.nextBoolean() ? 0 : 1;
        a.highlight = r.nextInt(12) == 0 ? StyleCatalog.NATURAL_HAIR[r.nextInt(StyleCatalog.NATURAL_HAIR.length)] : -1;
        if (!female) {
            int bd = r.nextInt(100);
            a.beard = bd < 45 ? "none" : StyleCatalog.BEARDS.get(1 + r.nextInt(StyleCatalog.BEARDS.size() - 1)).id();
        }
        // ---- одежда
        StyleCatalog.Wear top = wear(r, StyleCatalog.Slot.TOP, female, tags, citizen);
        a.top = top == null ? "tee#0" : ref(r, top);
        boolean dress = StyleCatalog.dress(Appearance.id(a.top)) || Appearance.id(a.top).equals("swimsuit");
        StyleCatalog.Wear pants = wear(r, StyleCatalog.Slot.PANTS, female, tags, citizen);
        a.pants = pants == null ? "jeans#0" : ref(r, pants);
        if (Appearance.id(a.top).equals("swimsuit")) a.pants = "denim_shorts#" + r.nextInt(3);
        if (Appearance.id(a.top).equals("suit")) a.pants = "slacks#" + Appearance.colorIndex(a.top) % 4;
        StyleCatalog.Wear shoes = wear(r, StyleCatalog.Slot.SHOES, female, tags, citizen);
        a.shoes = shoes == null ? "sneakers#0" : ref(r, shoes);
        int outerChance = switch (profile) {
            case WINTER -> 100;
            case BUSINESS -> 55;
            case BIKER -> 80;
            case BEACH, RESORT, SPORT -> 8;
            default -> 30;
        };
        if (!dress && !Appearance.id(a.top).equals("suit") && r.nextInt(100) < outerChance) {
            StyleCatalog.Wear o = wear(r, StyleCatalog.Slot.OUTER, female, tags, citizen);
            if (o != null) a.outer = ref(r, o);
        }
        if (r.nextInt(100) < (profile == BEACH || profile == WORKER || profile == WINTER ? 55 : 22)) {
            StyleCatalog.Wear h = wear(r, StyleCatalog.Slot.HAT, female, tags, citizen);
            if (h != null) a.hat = ref(r, h);
        }
        if (r.nextInt(100) < (profile == BEACH || profile == RESORT ? 45 : 18)) {
            StyleCatalog.Wear g = wear(r, StyleCatalog.Slot.GLASSES, female, tags, citizen);
            if (g != null) a.glasses = ref(r, g);
        }
        if (r.nextInt(100) < 24) {
            StyleCatalog.Wear g = wear(r, StyleCatalog.Slot.BAG, female, tags, citizen);
            if (g != null) a.bag = ref(r, g);
        }
        if (r.nextInt(100) < 30) {
            StyleCatalog.Wear g = wear(r, StyleCatalog.Slot.WRIST, female, tags, citizen);
            if (g != null) a.wrist = ref(r, g);
        }
        if (!citizen && r.nextInt(100) < 15) {
            StyleCatalog.Wear g = wear(r, StyleCatalog.Slot.NECK, female, tags, false);
            if (g != null) a.neck = ref(r, g);
        } else if (citizen && r.nextInt(100) < 6) a.neck = "pendant#" + r.nextInt(2);
        a.tuck = r.nextInt(3) == 0;
        a.rolled = r.nextInt(4) == 0;
        a.open = r.nextInt(3) != 0;
        a.pattern = r.nextInt(100) < 18 ? 1 + r.nextInt(StyleCatalog.PATTERNS.size() - 1) : 0;
        a.grime = profile == WORKER ? 1 + r.nextInt(2) : r.nextInt(6) == 0 ? 1 : 0;
        if (r.nextInt(100) < (profile == BIKER || profile == STREET ? 40 : 12)) {
            int n = 1 + r.nextInt(2);
            for (int i = 0; i < n; i++) a.tats[r.nextInt(4)] = 1 + r.nextInt(StyleCatalog.TATS.size() - 1);
        }
        a.sanitize();
        return a;
    }

    // ------------------------------------------------------------------ жители
    public static boolean female(long seed) {
        return (seed & 1) != 0;
    }

    public static int profile(long seed) {
        return (int) ((seed >>> 1) & 15) % PROFILE_NAMES.length;
    }

    public static long pack(long random, boolean female, int profile) {
        return (random << 5) | ((long) (profile & 15) << 1) | (female ? 1 : 0) | (1L << 62);
    }

    /** Внешность жителя по зерну (0 — нет, у жителя обычный скин). */
    public static Appearance citizen(long seed) {
        return generate(seed >>> 5, female(seed), profile(seed), true);
    }

    /** Зерно жителя: первое, при котором он не похож ни на одного героя сюжета (одинаково при каждом вызове). */
    public static long citizenSeed(long random, boolean female, int profile) {
        random &= (1L << 56) - 1;
        for (int k = 0; k < 64; k++) {
            long s = pack(random + k * 7919L, female, profile);
            if (!heroLike(citizen(s))) return s;
        }
        return pack(random, female, profile);
    }

    /** Похож ли на героя (та же мера, что в {@link Looks#distance}: порог 2.5 или та же причёска того же цвета). */
    public static boolean heroLike(Appearance a) {
        JsonObject me = compareJson(a);
        var all = Looks.all();
        for (int i = 0; i < Looks.STORY_IDS.size() && Looks.STORY_FROM + i < all.size(); i++) {
            JsonObject s = Looks.base(all.get(Looks.STORY_FROM + i).getAsJsonObject());
            if (Looks.distance(me, s) < Looks.MIN_DISTANCE || Looks.sameCombo(me, s)) return true;
        }
        return false;
    }

    public static double nearestHero(Appearance a, String[] who) {
        JsonObject me = compareJson(a);
        var all = Looks.all();
        double best = Double.MAX_VALUE;
        for (int i = 0; i < Looks.STORY_IDS.size() && Looks.STORY_FROM + i < all.size(); i++) {
            double d = Looks.distance(me, Looks.base(all.get(Looks.STORY_FROM + i).getAsJsonObject()));
            if (d < best) {
                best = d;
                if (who != null) who[0] = Looks.STORY_IDS.get(i);
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ описания для сравнения и для отрисовки
    static String hex(int rgb) {
        return String.format("%06x", rgb & 0xFFFFFF);
    }

    private static int color(String ref) {
        var w = StyleCatalog.wear(Appearance.id(ref));
        return w == null ? 0 : w.color(Appearance.colorIndex(ref));
    }

    /** Причёска «на глаз» в словаре героев (похожие стили сводятся к старым, чтобы мера оставалась честной). */
    static String compareStyle(String s) {
        return switch (s) {
            case "crew", "cornrows" -> "buzz";
            case "side_part", "pixie" -> "short";
            case "quiff", "undercut", "man_bun" -> "slick";
            case "curly_short" -> "curly";
            case "wavy" -> "long";
            case "high_pony" -> "ponytail";
            default -> s;
        };
    }

    /** Образ в форме citizen_looks.json — для {@link Looks#distance}. */
    public static JsonObject compareJson(Appearance a) {
        JsonObject o = new JsonObject();
        o.addProperty("style", compareStyle(a.hair));
        o.addProperty("hair", hex(StyleCatalog.hairColor(a.hairColor)));
        o.addProperty("skin", hex(StyleCatalog.tone(a.skin)));
        if (a.slim) o.addProperty("girl", true);
        if (!a.glasses.isEmpty()) {
            String g = Appearance.id(a.glasses);
            o.addProperty("glasses", switch (g) {
                case "round", "cateye" -> "frame";
                case "sport" -> "shades";
                default -> g;
            });
        }
        switch (a.beard) {
            case "beard", "full", "chin" -> o.addProperty("beard", hex(StyleCatalog.hairColor(a.hairColor)));
            case "mustache" -> o.addProperty("mustache", hex(StyleCatalog.hairColor(a.hairColor)));
            case "goatee" -> o.addProperty("goatee", hex(StyleCatalog.hairColor(a.hairColor)));
            default -> {}
        }
        if (!a.hat.isEmpty()) {
            String h = Appearance.id(a.hat);
            String k = switch (h) {
                case "gold_cap" -> "cap";
                case "hardhat" -> "helmet";
                default -> h;
            };
            o.addProperty(k, hex(color(a.hat)));
        }
        String top = Appearance.id(a.top), outer = Appearance.id(a.outer);
        String cat;
        if (!outer.isEmpty()) cat = switch (outer) {
            case "blazer", "coat", "trench" -> "suit";
            default -> "jacket";
        };
        else cat = switch (top) {
            case "suit", "vest_knit", "shirt", "blouse" -> "suit";
            case "hoodie", "tracksuit", "sweatshirt" -> "hoodie";
            case "sweater", "turtleneck", "cardigan", "longsleeve" -> "sweater";
            case "sundress", "dress", "office_dress", "tunic", "swimsuit" -> "dress";
            case "coverall", "work_vest", "flannel" -> "jacket";
            default -> "tshirt";
        };
        o.addProperty("top", cat);
        o.addProperty("shirt", hex(color(outer.isEmpty() ? a.top : a.outer)));
        o.addProperty("pants", hex(color(a.pants)));
        if (StyleCatalog.skirt(Appearance.id(a.pants)) || StyleCatalog.dress(top)) o.addProperty("skirt", hex(color(StyleCatalog.dress(top) ? a.top : a.pants)));
        return o;
    }

    /**
     * Объёмные части для client/render/HairFeature: причёска, борода, головной убор, очки, серьги, шея, сумка, юбка.
     * Ключи старых образов (citizen_looks.json) те же, новые — дополнительно.
     */
    public static JsonObject renderJson(Appearance a) {
        JsonObject o = new JsonObject();
        int hair = StyleCatalog.hairColor(a.hairColor);
        o.addProperty("style", a.hair);
        o.addProperty("hair", hex(hair));
        o.addProperty("skin", hex(StyleCatalog.tone(a.skin)));
        if (a.slim) o.addProperty("girl", true);
        o.addProperty("len", a.hairLen);
        if (!a.fringe) o.addProperty("nofringe", true);
        if (a.highlight >= 0) o.addProperty("hl", hex(StyleCatalog.hairColor(a.highlight)));
        switch (a.beard) {
            case "mustache" -> o.addProperty("mustache", hex(hair));
            case "goatee" -> o.addProperty("goatee", hex(hair));
            case "chin", "beard" -> o.addProperty("beard", hex(hair));
            case "full" -> {
                o.addProperty("beard", hex(hair));
                o.addProperty("mustache", hex(hair));
            }
            default -> {}
        }
        if (!a.hat.isEmpty()) {
            String h = Appearance.id(a.hat);
            String key = switch (h) {
                case "gold_cap" -> "cap";
                case "hardhat" -> "helmet";
                default -> h;
            };
            o.addProperty(key, hex(color(a.hat)));
        }
        if (!a.glasses.isEmpty()) {
            o.addProperty("glasses", Appearance.id(a.glasses));
            o.addProperty("gcol", hex(color(a.glasses)));
        }
        if (a.earrings >= 2) {
            o.addProperty("earrings", hex(StyleCatalog.EARRINGS.get(a.earrings).color()));
            if (a.earrings == 2) o.addProperty("hoops", true);
        }
        if (!a.neck.isEmpty()) {
            String n = Appearance.id(a.neck);
            String key = switch (n) {
                case "chain_gold", "chain_silver" -> "chain";
                case "neck_bandana" -> "nbandana";
                default -> n;
            };
            o.addProperty(key, hex(color(a.neck)));
        }
        if (!a.bag.isEmpty()) {
            o.addProperty("bag", Appearance.id(a.bag));
            o.addProperty("bagc", hex(color(a.bag)));
        }
        String top = Appearance.id(a.top), pants = Appearance.id(a.pants), outer = Appearance.id(a.outer);
        if (StyleCatalog.dress(top)) {
            o.addProperty("skirt", hex(color(a.top)));
            o.addProperty("skirtLen", top.equals("dress") ? 2 : 1);
        } else if (StyleCatalog.skirt(pants) && !top.equals("swimsuit")) {
            o.addProperty("skirt", hex(color(a.pants)));
            o.addProperty("skirtLen", pants.equals("skirt_long") ? 2 : pants.equals("skirt") ? 1 : 0);
        }
        if (outer.equals("coat") || outer.equals("trench")) o.addProperty("coat", hex(color(a.outer)));
        return o;
    }
}
