package com.yompix.novograd.life;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

/**
 * 0.8: внешность героев сюжета — только у них.
 *
 * <p>У каждого героя свой скин {@code story_<id>} (рисует tools/gen_story_looks.py) с «подписью» силуэта (берет, шарф,
 * плащ, цепь, гогглы, серьги, федора, бандана, кепка назад); у прохожих этих скинов и подписей нет. Скины прохожих,
 * совпадавшие с героями, перерисованы так, что «расстояние на глаз» ({@link #distance}) до любого героя ≥ 2.5, а
 * причёска того же цвета не совпадает. Имена героев прохожим не даются ({@link #cleanName}).
 *
 * <p>Без зависимостей от Minecraft — проверяется отдельно: {@code ./gradlew lookalikeTest} (5000 жителей).
 */
public final class Looks {
    private Looks() {}

    /** Порядок = порядок скинов story_* в CitizenEntity.SKINS и в citizen_looks.json (после 56 обычных). */
    public static final List<String> STORY_IDS = List.of("gleb", "nika", "shtorm", "kirpich", "petrov", "tema", "rosa", "vince", "marco",
            "harris", "lena", "crow", "sofi", "ray", "shakal", "diaz");
    public static final Set<String> STORY_SLIM = Set.of("nika", "rosa", "lena", "sofi", "diaz");
    /** Имена/фамилии героев: у прохожих не встречаются. */
    public static final Set<String> STORY_NAMES = Set.of("Джейк", "Ники", "Нитро", "Сторм", "Брик", "Питерсон", "Томми", "Роза", "Винс", "Марко",
            "Харрис", "Лена", "Вуд", "Виктор", "Кроу", "Софи", "Рэй", "Шакал", "Диас");
    private static final String[] SAFE_MALE = {"Лео", "Гас", "Оскар", "Нил", "Хэнк", "Карл"};
    private static final String[] SAFE_FEMALE = {"Зои", "Нора", "Белла", "Ида", "Тина", "Ева"};
    public static final String[] SIGNATURE = {"beret", "scarf", "chain", "coat", "goggles", "earrings", "fedora", "bandana", "cap_back"};
    /** Ниже — «двойник» героя. */
    public static final double MIN_DISTANCE = 2.5;

    private static JsonArray looks;

    // ---------------------------------------------------------------- скины (те же числа, что в CitizenEntity)
    public static final int RANDOM_FROM = 16, RANDOM_COUNT = 40, STORY_FROM = RANDOM_FROM + RANDOM_COUNT;
    private static final boolean[] BASE_SLIM = {true, false, false, true, false, false, false, true, true, false, false, true, false, false, false, false};

    /** Случайный прохожий — только из 40 обычных скинов (скины героев — дальше, сюда не попадают). */
    public static int randomSkin(net.minecraft.util.math.random.Random r) {
        return RANDOM_FROM + r.nextInt(RANDOM_COUNT);
    }

    public static boolean isStorySkin(int skin) {
        return skin >= STORY_FROM && skin < STORY_FROM + STORY_IDS.size();
    }

    public static boolean isSlim(int skin) {
        if (isStorySkin(skin)) return STORY_SLIM.contains(STORY_IDS.get(skin - STORY_FROM));
        return skin < BASE_SLIM.length ? BASE_SLIM[skin] : (skin - RANDOM_FROM) % 2 == 0;
    }

    public static synchronized JsonArray all() {
        if (looks == null) {
            try (var in = Looks.class.getResourceAsStream("/assets/novograd/citizen_looks.json")) {
                looks = in == null ? new JsonArray() : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray();
            } catch (Exception e) {
                looks = new JsonArray();
            }
        }
        return looks;
    }

    /** Имя без имён героев: слово-совпадение заменяется нейтральным (по seed, стабильно). */
    public static String cleanName(String name, long seed, boolean female) {
        if (name == null || name.isEmpty()) return name;
        String[] w = name.split(" ");
        boolean ch = false;
        for (int i = 0; i < w.length; i++) {
            String bare = w[i].replace("«", "").replace("»", "");
            if (STORY_NAMES.contains(bare)) {
                String[] pool = female ? SAFE_FEMALE : SAFE_MALE;
                w[i] = pool[(int) Math.floorMod(seed + i * 31L, pool.length)];
                ch = true;
            }
        }
        return ch ? String.join(" ", w) : name;
    }

    public static boolean hasStoryName(String name) {
        if (name == null) return false;
        for (String w : name.split(" ")) if (STORY_NAMES.contains(w.replace("«", "").replace("»", ""))) return true;
        return false;
    }

    // ---------------------------------------------------------------- похожесть (та же формула — в tools/gen_story_looks.py)
    private static int[] rgb(JsonObject o, String k) {
        if (o == null || !o.has(k)) return null;
        int c = Integer.parseInt(o.get(k).getAsString(), 16);
        return new int[]{(c >> 16) & 255, (c >> 8) & 255, c & 255};
    }

    private static double hexd(int[] a, int[] b) {
        if (a == null || b == null) return (a == null) != (b == null) ? 255 : 0;
        return Math.sqrt(Math.pow(a[0] - b[0], 2) + Math.pow(a[1] - b[1], 2) + Math.pow(a[2] - b[2], 2));
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) ? o.get(k).getAsString() : null;
    }

    private static final String[] HEADWEAR = {"helmet", "cap", "beanie", "fedora", "bandana", "beret", "cap_back"};

    private static String headKind(JsonObject o) {
        for (String k : HEADWEAR) if (o.has(k)) return k;
        return null;
    }

    private static String facial(JsonObject o) {
        return o.has("beard") ? "beard" : o.has("mustache") ? "mustache" : o.has("goatee") ? "goatee" : "";
    }

    /** Образ без «подписей» героя. */
    public static JsonObject base(JsonObject o) {
        JsonObject r = o.deepCopy();
        for (String k : SIGNATURE) r.remove(k);
        return r;
    }

    /** Расстояние «на глаз»: причёска, цвет волос и кожи, головной убор, очки, борода, одежда, пол. */
    public static double distance(JsonObject a, JsonObject b) {
        double d = 0;
        if (!java.util.Objects.equals(str(a, "style"), str(b, "style"))) d += 1.5;
        d += Math.min(1, hexd(rgb(a, "hair"), rgb(b, "hair")) / 120);
        d += 0.5 * Math.min(1, hexd(rgb(a, "skin"), rgb(b, "skin")) / 80);
        String ha = headKind(a), hb = headKind(b);
        if (!java.util.Objects.equals(ha, hb)) d += 1;
        else if (ha != null) d += 0.5 * Math.min(1, hexd(rgb(a, ha), rgb(b, hb)) / 150);
        if (!java.util.Objects.equals(str(a, "glasses"), str(b, "glasses"))) d += 0.5;
        if (!facial(a).equals(facial(b))) d += 0.5;
        if (!java.util.Objects.equals(str(a, "top"), str(b, "top"))) d += 0.5;
        d += Math.min(1, hexd(rgb(a, "shirt"), rgb(b, "shirt")) / 150);
        d += 0.5 * Math.min(1, hexd(rgb(a, "pants"), rgb(b, "pants")) / 150);
        if (a.has("girl") != b.has("girl")) d += 2;
        return d;
    }

    /** Та же причёска того же цвета. */
    public static boolean sameCombo(JsonObject a, JsonObject b) {
        return java.util.Objects.equals(str(a, "style"), str(b, "style")) && hexd(rgb(a, "hair"), rgb(b, "hair")) < 40;
    }

    /** Ближайший герой к образу скина: {индекс героя, расстояние·1000, 1 если та же причёска}. */
    public static int[] nearestStory(int skin, int storyFrom) {
        JsonArray all = all();
        if (skin < 0 || skin >= all.size()) return new int[]{-1, Integer.MAX_VALUE, 0};
        JsonObject a = all.get(skin).getAsJsonObject();
        int best = -1, combo = 0;
        double bd = Double.MAX_VALUE;
        for (int i = 0; i < STORY_IDS.size() && storyFrom + i < all.size(); i++) {
            JsonObject s = base(all.get(storyFrom + i).getAsJsonObject());
            double d = distance(a, s);
            if (sameCombo(a, s)) combo = 1;
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        return new int[]{best, (int) Math.round(bd * 1000), combo};
    }
}
