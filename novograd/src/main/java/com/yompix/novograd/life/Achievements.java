package com.yompix.novograd.life;

import com.yompix.novograd.ModContent;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;

import java.util.LinkedHashMap;
import java.util.Map;

/** Достижения: всплывают как в больших играх и копятся в телефоне («Награды»). */
public final class Achievements {
    private Achievements() {}

    public static final Map<String, String[]> ALL = new LinkedHashMap<>();

    static {
        add("first_lesson", "Первый звонок", "Сдать первый урок");
        add("excellent", "Отличник", "Получить пятёрку");
        add("graduate", "Выпускник", "Окончить школу");
        add("diploma", "Высшее образование", "Окончить университет");
        add("first_job", "Первая зарплата", "Заработать на работе");
        add("courier10", "Почтальон", "Доставить 10 посылок");
        add("scales", "Взвешенный", "Встать на весы в спортзале");
        add("athlete", "Атлет", "10 тренировок в спортзале");
        add("dj", "Король танцпола", "Отыграть сет в «Неоне» на 90%+");
        add("slim", "Стройность", "Сбросить вес до нормы после 85+ кг");
        add("taxi10", "Король дорог", "Сделать 10 поездок на такси");
        add("rich1000", "Копилка", "Накопить $1000");
        add("rich10000", "Богач", "Накопить $10 000");
        add("car", "Свои колёса", "Купить машину");
        add("house", "Свой угол", "Купить дом");
        add("jackpot", "Везунчик", "Выиграть ×10 в казино");
        add("tv", "Телеман", "Включить телевизор");
        add("photo", "Фотограф", "Сделать фото на телефон");
        add("friend", "Душа компании", "Поговорить с 20 жителями");
        add("event", "Городская жизнь", "Побывать на городском празднике");
        add("taxi_ride", "Шеф, гони!", "Прокатиться на вызванном такси");
        add("pet", "Лучший друг", "Завести питомца");
        add("quest5", "Добрый сосед", "Помочь жителям 5 раз");
        add("speeding", "Гонщик", "Получить штраф за превышение");
        add("coop", "Вместе веселее", "Пройти миссию с другом");
        add("story", "Легенда Новограда", "Пройти сюжет");
        add("fashion", "Модник", "Собрать 12 вещей в гардеробе");
    }

    private static void add(String id, String name, String desc) {
        ALL.put(id, new String[]{name, desc});
    }

    public static void give(ServerPlayerEntity p, String id) {
        LifeData d = Life.get(p);
        if (!ALL.containsKey(id) || !d.achievements.add(id)) return;
        NbtCompound n = new NbtCompound();
        n.putString("name", ALL.get(id)[0]);
        n.putString("desc", ALL.get(id)[1]);
        LifeNet.send(p, "achievement", n);
        p.playSoundToPlayer(ModContent.LEVEL_UP, SoundCategory.MASTER, 0.6f, 1.5f);
        Life.sync(p);
    }

    /** Проверки, которые удобно делать раз в секунду. */
    public static void check(ServerPlayerEntity p) {
        LifeData d = Life.get(p);
        if (d.money >= 1000) give(p, "rich1000");
        if (d.money >= 10000) give(p, "rich10000");
        if (d.edu > LifeData.SCHOOL_END) give(p, "graduate");
        if (d.edu > LifeData.UNI_END) give(p, "diploma");
        if (d.earned > 0 && !"none".equals(d.job)) give(p, "first_job");
    }
}
