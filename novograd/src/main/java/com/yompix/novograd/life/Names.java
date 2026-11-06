package com.yompix.novograd.life;

import net.minecraft.util.math.random.Random;

import java.util.Arrays;
import java.util.List;

/** Имена жителей — всегда по полу: девушке женское имя и фамилия (Иванова), парню — мужские. */
public final class Names {
    private Names() {}

    private static final String[] MALE = {"Майк", "Джон", "Дэвид", "Крис", "Джейсон", "Райан", "Тайлер", "Брэндон", "Кевин", "Эрик", "Ник", "Алекс", "Сэм", "Дэнни", "Тони", "Билли", "Лиам", "Джонатан"};
    private static final String[] FEMALE = {"Эмили", "Джессика", "Эшли", "Сара", "Хлоя", "Меган", "Кейт", "Лора", "Эмма", "Оливия", "Мия", "Эбби", "Холли", "Рэйчел", "Джулия", "Мэри", "Кэти", "Грейс"};
    private static final String[] LAST = {"Смит", "Джонсон", "Браун", "Миллер", "Дэвис", "Уилсон", "Тейлор", "Андерсон", "Мур", "Кларк"};
    private static final List<String> MALE_L = Arrays.asList(MALE), FEMALE_L = Arrays.asList(FEMALE), LAST_L = Arrays.asList(LAST);

    public static String random(Random r) {
        return random(r, r.nextBoolean());
    }

    public static String random(Random r, boolean female) {
        String[] a = female ? FEMALE : MALE;
        return a[r.nextInt(a.length)];
    }

    public static String full(Random r) {
        return full(r, r.nextBoolean());
    }

    public static String full(Random r, boolean female) {
        return random(r, female) + " " + LAST[r.nextInt(LAST.length)]; // фамилии американские: «Эмили Смит», без «-а»
    }

    private static final List<String> FEMALE_EXTRA = Arrays.asList("Зина", "Анна", "Валя", "Мария", "Марья", "Ирина", "Нина", "Галина", "Ольга", "Татьяна", "Баба", "Сьюзи", "Грейс", "Сара", "Бетти", "Нэнси");
    private static final List<String> MALE_EXTRA = Arrays.asList("Олег", "Гена", "Аркадий", "Сергей", "Стас", "Луиджи", "Пётр", "Петров", "Дед", "Семёныч", "Стив", "Боб", "Джо", "Дэн", "Рэй", "Фрэнк", "Гарри", "Питерсон");

    /** Пол по имени (для жителей с заданными именами): true — женский, false — мужской, null — непонятно. */
    public static Boolean genderOf(String name) {
        for (String p : name.split(" ")) {
            if (FEMALE_L.contains(p) || FEMALE_EXTRA.contains(p) || p.endsWith("овна") || p.endsWith("евна")) return true;
            if (MALE_L.contains(p) || MALE_EXTRA.contains(p) || p.endsWith("ович") || p.endsWith("евич")) return false;
        }
        return null;
    }

    /** Исправляет имя под пол: «Егор Иванов» у девушки станет, например, «Аня Иванова». Прочие слова (профессии) не трогает. */
    public static String fixGender(String name, boolean female, long seed) {
        String[] parts = name.split(" ");
        boolean changed = false;
        int k = (int) Math.floorMod(seed, 1000);
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            if (female && MALE_L.contains(p)) { parts[i] = FEMALE[k % FEMALE.length]; changed = true; }
            else if (!female && FEMALE_L.contains(p)) { parts[i] = MALE[k % MALE.length]; changed = true; }
        }
        return changed ? String.join(" ", parts) : name;
    }
}
