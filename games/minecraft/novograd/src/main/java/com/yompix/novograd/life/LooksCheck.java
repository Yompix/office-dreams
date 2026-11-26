package com.yompix.novograd.life;

import com.google.gson.JsonObject;

/**
 * Проверка 0.8 «ни одного двойника героя»: 5000 жителей теми же функциями, что и в игре (случайный прохожий,
 * банда, мужчины/женщины по полу, жильцы, ролевые скины), — ни один не похож на героя (расстояние ≥ 2.5, другая
 * причёска того же цвета), не носит его скин и имя. Запуск: {@code ./gradlew lookalikeTest} (код выхода 1 — нашёлся двойник).
 */
public final class LooksCheck {
    private LooksCheck() {}

    private static String skinName(int skin) {
        String[] base = {"teacher", "shopkeeper", "student", "student2", "worker", "office", "police", "grandma", "mom", "dad", "tema", "lisa", "gleb", "instructor", "baron", "guard"};
        return skin < 16 ? base[skin] : skin < Looks.STORY_FROM ? String.format("c%02d", skin - 16) : "story_" + Looks.STORY_IDS.get(skin - Looks.STORY_FROM);
    }

    public static void main(String[] args) {
        int n = args.length > 0 ? Integer.parseInt(args[0]) : 5000;
        var r = net.minecraft.util.math.random.Random.create(808);
        int bad = 0, badName = 0, badSkin = 0;
        double minD = Double.MAX_VALUE;
        String minWho = "";
        int[] roleSkins = {1, 2, 3, 4, 5, 6, 7, 15};
        java.util.Map<String, Integer> kinds = new java.util.TreeMap<>();
        for (int i = 0; i < n; i++) {
            int skin;
            String kind;
            switch (i % 6) {
                case 0, 1 -> { skin = Looks.randomSkin(r); kind = "прохожий"; }
                case 2 -> { skin = Looks.RANDOM_FROM + 39 - r.nextInt(4) * 2; kind = "банда"; }
                case 3 -> { skin = Looks.RANDOM_FROM + 1 + 2 * r.nextInt(Looks.RANDOM_COUNT / 2); kind = "мужчина"; }
                case 4 -> { skin = Looks.RANDOM_FROM + 2 * r.nextInt(Looks.RANDOM_COUNT / 2); kind = "женщина"; }
                default -> { skin = roleSkins[r.nextInt(roleSkins.length)]; kind = "роль"; }
            }
            kinds.merge(kind, 1, Integer::sum);
            boolean slim = Looks.isSlim(skin);
            String name = Looks.cleanName(i % 3 == 0 ? Names.full(r, slim) : Names.random(r, slim), r.nextLong(), slim);
            if (Looks.isStorySkin(skin)) badSkin++;
            if (Looks.hasStoryName(name)) badName++;
            int[] ns = Looks.nearestStory(skin, Looks.STORY_FROM);
            double d = ns[1] / 1000.0;
            if (d < minD) {
                minD = d;
                minWho = skinName(skin) + " ↔ " + Looks.STORY_IDS.get(Math.max(0, ns[0]));
            }
            if (d < Looks.MIN_DISTANCE || ns[2] == 1) {
                if (bad < 10) System.out.println("ДВОЙНИК: " + skinName(skin) + " похож на " + Looks.STORY_IDS.get(ns[0]) + " (" + d + (ns[2] == 1 ? ", та же причёска" : "") + ")");
                bad++;
            }
        }
        // имена: все случайные имена генератора (оба пола) без имён героев
        for (int i = 0; i < 2000; i++) {
            boolean f = (i & 1) == 0;
            if (Looks.hasStoryName(Looks.cleanName(Names.full(r, f), i, f))) badName++;
        }
        // сами герои: каждый со своим скином, у каждого есть «подпись»
        int noSig = 0;
        var all = Looks.all();
        for (int i = 0; i < Looks.STORY_IDS.size(); i++) {
            JsonObject s = all.get(Looks.STORY_FROM + i).getAsJsonObject();
            boolean sig = false;
            for (String k : Looks.SIGNATURE) sig |= s.has(k);
            if (!sig || !Looks.STORY_IDS.get(i).equals(s.get("story").getAsString())) noSig++;
        }
        System.out.printf(java.util.Locale.ROOT, "[lookalike] жителей=%d %s двойников=%d скин_героя=%d имя_героя=%d героев_без_подписи=%d ближе_всех=%s (%.2f, порог %.1f)%n",
                n, kinds, bad, badSkin, badName, noSig, minWho, minD, Looks.MIN_DISTANCE);
        // ---- 0.9.6: жители из генератора внешности (те же функции, что CitizenEntity.assignLook и клиент)
        int genBad = 0, genSig = 0, female = 0;
        double genMin = Double.MAX_VALUE;
        String genWho = "";
        java.util.Set<String> distinct = new java.util.HashSet<>(), skins = new java.util.HashSet<>();
        java.util.Map<String, Integer> profiles = new java.util.TreeMap<>();
        int[] chestHist = new int[Appearance.CHEST_STEPS];
        java.util.Set<String> heroOnly = java.util.Set.of("beret", "scarf", "chain", "coat", "goggles", "earrings", "fedora", "bandana", "cap_back", "nbandana");
        for (int i = 0; i < n; i++) {
            boolean f = r.nextBoolean();
            int prof = r.nextInt(LookGen.PROFILE_NAMES.length);
            long seed = LookGen.citizenSeed(r.nextLong(), f, prof);
            Appearance a = LookGen.citizen(seed);
            if (LookGen.female(seed) != f || a.slim != f) genBad++;
            profiles.merge(LookGen.PROFILE_NAMES[LookGen.profile(seed)], 1, Integer::sum);
            if (f) female++;
            String[] who = new String[1];
            double d = LookGen.nearestHero(a, who);
            if (d < genMin) {
                genMin = d;
                genWho = who[0];
            }
            if (LookGen.heroLike(a)) {
                if (genBad < 10) System.out.println("ДВОЙНИК (генератор): зерно " + seed + " похож на " + who[0] + " (" + d + ")");
                genBad++;
            }
            if (a.slim) chestHist[a.chest]++;
            JsonObject rj = LookGen.renderJson(a);
            for (String k : heroOnly) if (rj.has(k)) genSig++;
            distinct.add(a.key() + "|" + a.hat + "|" + a.glasses + "|" + a.bag);
            skins.add(a.key());
        }
        System.out.printf(java.util.Locale.ROOT, "[lookalike] генератор: жителей=%d женщин=%d профили=%s двойников=%d подписей_героя=%d разных_образов=%d разных_скинов=%d ближе_всех=%s (%.2f)%n",
                n, female, profiles, genBad, genSig, distinct.size(), skins.size(), genWho, genMin);
        // 0.9.7: размеры у жительниц — естественный разброс, чаще середина, все в пределах 0…4
        int mid = chestHist[1] + chestHist[2] + chestHist[3];
        int shapeBad = female > 0 && (chestHist[2] < chestHist[0] || chestHist[2] < chestHist[4] || mid * 100 < female * 60) ? 1 : 0;
        System.out.printf(java.util.Locale.ROOT, "[lookalike] форма тела (женщины): размеры 0..4 = %s, в середине (1–3) %d%% → %s%n",
                java.util.Arrays.toString(chestHist), female == 0 ? 0 : mid * 100 / female, shapeBad == 0 ? "ок" : "ОШИБКА");
        System.out.printf(java.util.Locale.ROOT, "[catalog] верх=%d куртки=%d низ=%d обувь=%d аксессуары=%d (уборы %d, очки %d, шея %d, сумки %d, часы %d) причёсок=%d цветов_волос=%d тонов_кожи=%d цветов_глаз=%d%n",
                StyleCatalog.count(StyleCatalog.Slot.TOP), StyleCatalog.count(StyleCatalog.Slot.OUTER), StyleCatalog.count(StyleCatalog.Slot.PANTS),
                StyleCatalog.count(StyleCatalog.Slot.SHOES),
                StyleCatalog.count(StyleCatalog.Slot.HAT, StyleCatalog.Slot.GLASSES, StyleCatalog.Slot.NECK, StyleCatalog.Slot.BAG, StyleCatalog.Slot.WRIST),
                StyleCatalog.count(StyleCatalog.Slot.HAT), StyleCatalog.count(StyleCatalog.Slot.GLASSES), StyleCatalog.count(StyleCatalog.Slot.NECK),
                StyleCatalog.count(StyleCatalog.Slot.BAG), StyleCatalog.count(StyleCatalog.Slot.WRIST), StyleCatalog.HAIR.size(), StyleCatalog.HAIR_COLORS.size(),
                StyleCatalog.SKIN_TONES.length, StyleCatalog.EYE_COLORS.size());
        // старые сохранения: внешность 0.9.5 читается (6 тонов → 10, куртка из «верха» → слой поверх)
        net.minecraft.nbt.NbtCompound old = new net.minecraft.nbt.NbtCompound();
        old.putBoolean("custom", true);
        old.putInt("skin", 5);
        old.putString("top", "leather#1");
        old.putString("hair", "afro");
        Appearance oa = Appearance.fromNbt(old);
        int compat = oa.chest == Appearance.MID_CHEST && oa.pecs == 1 && oa.skin == 8 && oa.outer.equals("leather#1") && oa.top.equals("tee#0") && oa.hair.equals("afro") ? 0 : 1;
        System.out.println("[lookalike] старая внешность 0.9.5 → " + (compat == 0 ? "ок" : "ОШИБКА") + " (тон " + oa.skin + ", верх " + oa.top + ", куртка " + oa.outer + ")");
        System.exit(bad + badName + badSkin + noSig + genBad + genSig + compat + shapeBad == 0 ? 0 : 1);
    }
}
