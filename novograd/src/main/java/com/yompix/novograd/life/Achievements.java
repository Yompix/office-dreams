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
        add("first_job", "Первая зарплата", "Заработать на работе");
        add("courier10", "Почтальон", "Доставить 10 посылок");
        add("taxi10", "Король дорог", "Сделать 10 поездок на такси");
        add("rich1000", "Копилка", "Накопить $1000");
        add("rich10000", "Богач", "Накопить $10 000");
        add("car", "Свои колёса", "Купить машину");
        add("house", "Свой угол", "Купить дом");
        add("jackpot", "Везунчик", "Выиграть ×10 в казино");
        add("photo", "Фотограф", "Сделать фото на телефон");
        add("friend", "Душа компании", "Поговорить с 20 жителями");
        add("event", "Городская жизнь", "Побывать на городском празднике");
        add("taxi_ride", "Шеф, гони!", "Прокатиться на вызванном такси");
        add("quest5", "Добрый сосед", "Помочь жителям 5 раз");
        add("speeding", "Гонщик", "Получить штраф за превышение");
        add("coop", "Вместе веселее", "Пройти миссию с другом");
        add("story", "Легенда Новограда", "Пройти сюжет");
        add("fashion", "Модник", "Собрать 12 вещей в гардеробе");
        // новые — про жизнь криминального острова
        add("heist", "Большой куш", "Ограбить банк");
        add("holdup", "Руки вверх!", "Взять кассу в магазине");
        add("armored", "Инкассатор", "Разбить бронефургон");
        add("tycoon", "Бизнесмен", "Купить бизнес");
        add("packages50", "Кладоискатель", "Найти все 50 тайников");
        add("rampage", "Мясорубка", "Пройти бойню");
        add("stars5", "Враг государства", "Получить 5 звёзд розыска");
        add("stunt", "Каскадёр", "Сделать трюковой прыжок");
        // уличные события, подработки, районы, парашют, бары (gta/FunHooks)
        add("bag_return", "Честный человек", "Вернуть украденную сумку хозяйке");
        add("samaritan", "Добрый самаритянин", "Помочь пострадавшему в аварии");
        add("street_race", "Король светофора", "Выиграть уличную гонку");
        add("hero_store", "Герой района", "Сорвать ограбление магазина");
        add("drunk", "Трезвый взгляд", "Остановить пьяного водителя");
        add("hitchhiker", "Попутчик", "Довезти автостопщика");
        add("ambush", "Не на того напали", "Отбиться от засады банды");
        add("ambulance12", "Ангел-хранитель", "Скорая: пройти 12 уровней");
        add("fire12", "Огнеборец", "Пожарные: пройти 12 уровней");
        add("pizza12", "Пицца-экспресс", "Пицца: пройти 12 уровней");
        add("turf1", "Мой район", "Захватить район у банды");
        add("turf_all", "Хозяин города", "Захватить все районы");
        add("skydive", "Свободное падение", "Прыгнуть с парашютом");
        add("bullseye", "В яблочко", "Приземлиться в центр мишени");
        add("arcade", "Неоновый рекорд", "Набрать 500 очков в автомате");
        add("darts", "Меткий глаз", "Выиграть партию в дартс");
        add("bowling", "Боулинг", "Сбить машиной 5 пешеходов за раз");
        // коллекции, бои (gta/Collectibles, gta/FightClub)
        add("tags100", "Уличный художник", "Закрасить все 100 тегов банд");
        add("photos30", "Фотограф Новограда", "Снять все 30 фото-точек");
        add("jumps25", "Каскадёр-призрак", "Пройти все 25 уникальных прыжков");
        add("collector", "Коллекционер", "Собрать все коллекции острова");
        add("fight_win", "Кулаки решают", "Победить на ринге «Подвала»");
        add("fight_champ", "Чемпион «Подвала»", "Победить чемпиона подпольных боёв");
        add("fight_bet", "Букмекер плачет", "Выиграть на ставке $5000 и больше");
        // тёмные дела: большие ограбления, заказы, контрабанда (gta/Underworld)
        add("bh_port", "Портовый куш", "Ограбить «Портовую компанию»");
        add("bh_bank", "Сейф Сити-Банка", "Ограбить хранилище Сити-Банка");
        add("bh_casino", "Корона снята", "Ограбить казино «Корона»");
        add("bh_ghost", "Призрак", "Пройти большое ограбление тихо, без тревоги");
        add("bh_all", "Ограбление века", "Взять все три больших дела");
        add("contract1", "Чистильщик", "Выполнить заказ");
        add("contract10", "Профессионал", "Выполнить 10 заказов");
        add("contract_clean", "Без следов", "Заказ нужным способом, без свидетелей и розыска");
        add("smuggle1", "Рыбак доволен", "Доставить контрабанду");
        add("smuggle10", "Король контрабанды", "Доставить 10 партий контрабанды");
        add("smuggle_air", "Воздушный мост", "Сбросить груз с самолёта");
        add("skill_drive", "Король дорог", "Навык «Вождение» на 100%");
        add("skill_bike", "Байкер", "Навык «Мотоцикл» на 100%");
        add("skill_fly", "Ас", "Навык «Полёт» на 100%");
        add("skill_pistol", "Ковбой", "Навык «Пистолеты» на 100%");
        add("skill_smg", "Шквал", "Навык «Пистолеты-пулемёты» на 100%");
        add("skill_shotgun", "Дробь", "Навык «Дробовики» на 100%");
        add("skill_rifle", "Снайпер", "Навык «Винтовки» на 100%");
        add("skill_stamina", "Марафонец", "Навык «Выносливость» на 100%");
        add("skill_strength", "Силач", "Навык «Сила» на 100%");
        add("skill_swim", "Дельфин", "Навык «Плавание» на 100%");
        add("skill_stealth", "Тень", "Навык «Скрытность» на 100%");
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
        if (d.earned > 0 && !"none".equals(d.job)) give(p, "first_job");
    }
}
