package com.yompix.novograd.gta;

import com.yompix.novograd.life.LifeNet;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;

/**
 * «Новоград-24» — городские новости. Город замечает, что ты делаешь: налёты, войны банд, гонки, подвиги,
 * сделки — всё попадает в экстренный выпуск (бегущая строка на экране) и в приложение «Новости» в телефоне.
 * Между делами канал рассказывает о жизни острова, и часть новостей двигает биржу.
 */
public final class News {
    private News() {}

    private static final Deque<String[]> FEED = new ArrayDeque<>(); // {заголовок, время, рубрика}
    private static final Random R = new Random();

    /** Из большого титра события — заголовок новости (null — не новость). */
    private static String headline(String title, String sub) {
        String place = sub.contains("·") ? sub.substring(0, sub.indexOf('·')).trim() : sub.trim();
        return switch (title) {
            case "ОГРАБЛЕНИЕ БАНКА" -> "Прямо сейчас: вооружённый налёт на «" + place + "». Полиция стягивает силы";
            case "ОГРАБЛЕНИЕ УДАЛОСЬ" -> "«" + place + "» ограблен средь бела дня. Налётчик в маске ушёл от погони";
            case "ОГРАБЛЕНИЕ ПРОВАЛЕНО" -> "Полиция сорвала ограбление банка. Мэр благодарит патрульных";
            case "КАССА ВЗЯТА", "КАССА НАША" -> "Ограблен магазин. Продавец: «Он был спокоен, будто делал это сто раз»";
            case "ИНКАССАТОР ВЗЯТ" -> "Нападение на инкассаторов: бронемашина брошена пустой";
            case "БОЙНЯ ПРОЙДЕНА" -> "Перестрелка с бандой: полиция насчитала десять тел. Свидетели молчат";
            case "БИЗНЕС КУПЛЕН" -> "У «" + place + "» новый владелец. Откуда у него деньги — не уточняется";
            case "НЕДВИЖИМОСТЬ КУПЛЕНА" -> "Рынок недвижимости оживает: на острове снова покупают жильё";
            case "ВОЙНА ЗА РАЙОН" -> "Война банд в районе " + place + ". Жителей просят не выходить из домов";
            case "ГРАБИТЕЛИ ОСТАНОВЛЕНЫ" -> "Прохожий в одиночку остановил грабителей магазина. Кто он?";
            case "СУМКА ВОЗВРАЩЕНА" -> "Незнакомец догнал вора и вернул сумку хозяйке";
            case "ПОМОЩЬ ОКАЗАНА" -> "Случайный водитель спас пострадавшего в аварии до приезда скорой";
            case "ПОБЕДА!" -> "Ночные гонки вернулись: полиция получает жалобы со всего центра";
            case "ПРЕСТУПНИКИ ОСТАНОВЛЕНЫ" -> "Таинственный мститель задерживает преступников. Полиция просит не помогать";
            case "ТРЮКОВОЙ ПРЫЖОК!" -> R.nextInt(3) == 0 ? "Видео дня: машина пролетела над улицей. Водителя ищут" : null;
            case "САЛОН ОБЧИЩЕН" -> "Мародёр обчистил машину после аварии. Пострадавший в больнице";
            case "СУМКА ТВОЯ" -> "Ещё одна кража сумки в центре: полиция советует быть внимательнее";
            default -> null;
        };
    }

    /** Фон: жизнь острова. {тикер, изменение} — новость двигает биржу. */
    private static final String[][] FLAVOR = {
            {"В порту забастовка грузчиков: суда стоят у причала", "PORT", "-0.05"},
            {"Порт подписал контракт с крупной судоходной компанией", "PORT", "0.06"},
            {"«Корона» объявила турнир по покеру с призом в миллион", "KRON", "0.05"},
            {"Проверка налоговой в казино «Корона»: залы закрыты на день", "KRON", "-0.06"},
            {"Туристический сезон на Ривьере побил рекорд", "RIVH", "0.05"},
            {"Шторм у побережья: отели Ривьеры отменяют брони", "RIVH", "-0.05"},
            {"Такси «Жёлтый» обновляет парк машин", "TAXI", "0.06"},
            {"Водители такси требуют повысить тарифы", "TAXI", "-0.04"},
            {"«Нитро-Гараж» выпустил новый комплект тюнинга", "NITR", "0.05"},
            {"Сити-Банк повысил ставки по вкладам", "CITY", "0.03"},
            {"Мэр обещает новые фонари на всех трассах острова", "", ""},
            {"На мосту к Ривьере пробка: ремонт продлится до выходных", "", ""},
            {"Синоптики: ночью над Новоградом пройдёт гроза", "", ""},
            {"В Седых горах снова видели снежного барса", "", ""},
            {"Радио НЕОН 88 FM — самая слушаемая станция города", "", ""},
            {"Полиция Новограда получила новые вертолёты", "", ""},
            {"Жители пригорода жалуются на ночные гонки", "", ""},
            {"В пустоши нашли брошенный самолёт. Хозяин не объявился", "", ""},
            {"Лучший бар месяца — на пляже Ривьеры", "", ""},
            {"Город ищет водителей скорой и пожарных — платят хорошо", "", ""},
    };

    /** Активности сообщают о событии — может стать новостью. */
    public static void onTitle(ServerPlayerEntity p, String title, String sub) {
        if (p.getServer() == null) return;
        String h = headline(title, sub == null ? "" : sub);
        if (h != null) post(p.getServer(), h, "ЭКСТРЕННО");
    }

    /** Биржевая новость (из Economy.shock). */
    public static void market(MinecraftServer s, String name, double pct, String news) {
        post(s, name + (pct >= 0 ? " растёт" : " падает") + " на бирже: " + news, "БИРЖА");
    }

    public static void post(MinecraftServer s, String headline, String cat) {
        long tod = s.getOverworld().getTimeOfDay() % 24000;
        int hh = (int) ((tod / 1000 + 6) % 24), mm = (int) (tod % 1000 * 60 / 1000);
        String time = String.format("%02d:%02d", hh, mm);
        synchronized (FEED) {
            if (!FEED.isEmpty() && FEED.peekFirst()[0].equals(headline)) return;
            FEED.addFirst(new String[]{headline, time, cat});
            while (FEED.size() > 30) FEED.removeLast();
        }
        NbtCompound n = new NbtCompound();
        n.putString("h", headline);
        n.putString("t", time);
        n.putString("c", cat);
        for (ServerPlayerEntity pl : s.getPlayerManager().getPlayerList()) LifeNet.send(pl, "news", n);
    }

    private static long lastFlavor = -1;

    /** Раз в ~3 игровых часа — новость о жизни острова (часть двигает биржу). */
    public static void tick(MinecraftServer s) {
        long t = s.getOverworld().getTimeOfDay() / 3000;
        if (t == lastFlavor) return;
        boolean first = lastFlavor < 0;
        lastFlavor = t;
        if (first || s.getPlayerManager().getPlayerList().isEmpty()) return;
        String[] f = FLAVOR[R.nextInt(FLAVOR.length)];
        if (!f[1].isEmpty()) Economy.shock(s, f[1], Double.parseDouble(f[2]), f[0]);
        else post(s, f[0], "ГОРОД");
    }

    /** Лента для приложения «Новости». */
    public static void sendFeed(ServerPlayerEntity p) {
        NbtList l = new NbtList();
        synchronized (FEED) {
            for (String[] f : FEED) {
                NbtCompound c = new NbtCompound();
                c.putString("h", f[0]);
                c.putString("t", f[1]);
                c.putString("c", f[2]);
                l.add(c);
            }
        }
        NbtCompound n = new NbtCompound();
        n.put("feed", l);
        LifeNet.send(p, "news_feed", n);
    }
}
