package com.yompix.lifecraft.life;

import net.minecraft.util.math.random.Random;

public final class Names {
    private Names() {}

    private static final String[] FIRST = {"Саша", "Маша", "Дима", "Аня", "Ваня", "Катя", "Миша", "Оля", "Петя", "Лена", "Артём", "Соня",
            "Егор", "Даша", "Никита", "Вика", "Илья", "Полина", "Максим", "Алина"};
    private static final String[] LAST = {"Иванов", "Смирнов", "Кузнецов", "Попов", "Волков", "Соколов", "Лебедев", "Козлов", "Новиков", "Морозов"};

    public static String random(Random r) {
        return FIRST[r.nextInt(FIRST.length)];
    }

    public static String full(Random r) {
        return FIRST[r.nextInt(FIRST.length)] + " " + LAST[r.nextInt(LAST.length)];
    }
}
