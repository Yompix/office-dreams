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
        System.exit(bad + badName + badSkin + noSig == 0 ? 0 : 1);
    }
}
