package com.yompix.novograd.gta;

import com.yompix.novograd.entity.CarBody;
import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.life.LifeNet;
import com.yompix.novograd.world.Island;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Полицейская рация и диспетчер. Слышно, когда сидишь в полицейской машине или тебя ищут (★):
 * короткие сводки в строке на экране («Всем постам: угон на Портовой, серый седан…») с треском рации.
 * Сводки идут от настоящих событий (розыск, проезд на красный, ДТП, стрельба, вызовы скорой и пожарных),
 * а в тишине — фоновый эфир. Названия районов — по зонам острова, улиц — по сетке (у каждой линии своё имя).
 */
public final class Dispatch {
    private Dispatch() {}

    // ------------------------------------------------------------------ имена улиц и районов
    /** Улицы вдоль оси Z (линии x): {именительный, «на …»}. */
    private static final String[][] STREETS_X = {
            {"Портовая улица", "на Портовой"}, {"Садовая улица", "на Садовой"}, {"улица Гагарина", "на улице Гагарина"},
            {"Кленовая улица", "на Кленовой"}, {"Заводская улица", "на Заводской"}, {"улица Пушкина", "на улице Пушкина"},
            {"Морская улица", "на Морской"}, {"Лесная улица", "на Лесной"}, {"Вокзальная улица", "на Вокзальной"},
            {"Речная улица", "на Речной"}, {"улица Строителей", "на улице Строителей"}, {"Солнечная улица", "на Солнечной"},
            {"Каштановая улица", "на Каштановой"}, {"Почтовая улица", "на Почтовой"}, {"Рыбацкая улица", "на Рыбацкой"},
            {"улица Чехова", "на улице Чехова"}, {"Липовая улица", "на Липовой"}};
    /** Улицы вдоль оси X (линии z). */
    private static final String[][] STREETS_Z = {
            {"проспект Мира", "на проспекте Мира"}, {"Центральный проспект", "на Центральном проспекте"}, {"бульвар Огней", "на бульваре Огней"},
            {"проспект Победы", "на проспекте Победы"}, {"Набережный проспект", "на Набережном проспекте"}, {"проспект Свободы", "на проспекте Свободы"},
            {"Неоновый бульвар", "на Неоновом бульваре"}, {"проспект Космонавтов", "на проспекте Космонавтов"}, {"Северный проспект", "на Северном проспекте"},
            {"Банковский переулок", "в Банковском переулке"}, {"Театральный проспект", "на Театральном проспекте"}, {"Южный бульвар", "на Южном бульваре"},
            {"проспект Ветеранов", "на проспекте Ветеранов"}};

    private static String[] streetName(int lineCoord, boolean alongZ) {
        String[][] pool = alongZ ? STREETS_X : STREETS_Z;
        int i = Math.floorMod((lineCoord - 24) / 48 * 7 + (alongZ ? 3 : 11), pool.length);
        return pool[i];
    }

    /** Улица у точки: {имя, «на …»} или null (не на улице). */
    @Nullable
    public static String[] street(double x, double z) {
        Island.Zone zn = Island.zone((int) x, (int) z);
        if (!Island.urban(zn) && zn != Island.Zone.CORE) return null;
        int lx = TrafficLights.line(x), lz = TrafficLights.line(z);
        double dx = Math.abs(x - lx), dz = Math.abs(z - lz);
        if (Math.min(dx, dz) > 8) return null;
        return dx <= dz ? streetName(lx, true) : streetName(lz, false);
    }

    /** Район: «в Деловом центре», «в Порту»… */
    public static String district(double x, double z) {
        return switch (Island.zone((int) x, (int) z)) {
            case DOWNTOWN -> "в Деловом центре";
            case INDUSTRIAL -> "в Промзоне";
            case SUBURB -> x < -840 ? "в пригороде Ривьеры" : "в Пригороде";
            case RIVIERA -> "на Ривьере";
            case PORT -> "в Порту";
            case BEACH -> "на пляже";
            case CORE -> "в Старом городе";
            case AIRFIELD -> "у аэродрома";
            case VILLAGE -> "в Сосновке";
            case SEA -> "на воде";
            default -> {
                String best = "за городом";
                double bd = 400;
                for (String[] l : Island.LABELS) {
                    double d = Math.hypot(x - Double.parseDouble(l[1]), z - Double.parseDouble(l[2]));
                    if (d < bd) { bd = d; best = "в районе «" + l[0] + "»"; }
                }
                yield best;
            }
        };
    }

    /** «на Садовой, в Деловом центре». */
    public static String where(Vec3d at) {
        String[] st = street(at.x, at.z);
        String d = district(at.x, at.z);
        return st != null ? st[1] + ", " + d : d;
    }

    private static final String[][] COLOR_NAMES = {
            {"D7262C", "красный"}, {"A32025", "красный"}, {"5A1E24", "бордовый"}, {"2C6ECB", "синий"}, {"1F3A68", "тёмно-синий"}, {"7FA7C9", "голубой"},
            {"F2C032", "жёлтый"}, {"F7C600", "жёлтый"}, {"F07A12", "оранжевый"}, {"E8E8EA", "белый"}, {"F5F7FA", "белый"}, {"B8BCC2", "серебристый"},
            {"6B6F76", "серый"}, {"2A2C31", "чёрный"}, {"15161A", "чёрный"}, {"2F4A35", "зелёный"}, {"7BC639", "салатовый"}, {"CDBB95", "бежевый"},
            {"9B3FD1", "фиолетовый"}, {"5B2A86", "фиолетовый"}, {"6A2C1E", "коричневый"}, {"8C8F5A", "оливковый"}, {"34506B", "серо-синий"}};

    private static String colorName(int rgb) {
        String best = "серый";
        long bd = Long.MAX_VALUE;
        for (String[] c : COLOR_NAMES) {
            int v = Integer.parseInt(c[0], 16);
            long dr = ((v >> 16) & 255) - ((rgb >> 16) & 255), dg = ((v >> 8) & 255) - ((rgb >> 8) & 255), db = (v & 255) - (rgb & 255);
            long d = dr * dr * 3 + dg * dg * 4 + db * db * 2;
            if (d < bd) { bd = d; best = c[1]; }
        }
        return best;
    }

    /** Приметы машины для сводки: «серый седан», «жёлтое такси». */
    public static String describe(CarEntity car) {
        if (car.isBike()) return "мотоцикл";
        if (car.isScooter()) return "скутер";
        String body = switch (car.body()) {
            case SEDAN, LUXURY -> "седан";
            case HATCH, COMPACT -> "хэтчбек";
            case SPORTS, SUPERCAR -> "спорткар";
            case VAN -> "фургон";
            case TAXI -> "такси";
            case POLICE -> "патрульная машина";
            case SUV -> "внедорожник";
            case PICKUP -> "пикап";
            case MUSCLE, CLASSIC -> "купе";
            case LIMO -> "лимузин";
            case TRUCK -> "грузовик";
            case LOWRIDER -> "лоурайдер";
            case BUGGY -> "багги";
            case SWAT -> "броневик спецназа";
            case AMBULANCE -> "машина скорой";
            case FIRETRUCK -> "пожарная машина";
            case BUS -> "автобус";
            default -> "машина";
        };
        String col = colorName(car.color());
        // род: «жёлтое такси», «серая патрульная машина»
        boolean neuter = body.equals("такси") || body.equals("купе");
        boolean fem = body.startsWith("патрульная") || body.startsWith("машина") || body.startsWith("пожарная");
        if (car.body() == CarBody.TAXI || car.body() == CarBody.AMBULANCE || car.body() == CarBody.FIRETRUCK || car.body() == CarBody.SWAT) return body;
        if (neuter) col = col.replaceAll("(ый|ой|ий)$", col.endsWith("ий") ? "ее" : "ое");
        else if (fem) col = col.replaceAll("(ый|ой|ий)$", col.endsWith("ий") ? "яя" : "ая");
        return col + " " + body;
    }

    // ------------------------------------------------------------------ кто слышит
    public static boolean listening(ServerPlayerEntity p) {
        if (Wanted.stars(p) > 0) return true;
        return p.getVehicle() instanceof CarEntity car && (car.isPolice() || car.body() == CarBody.SWAT);
    }

    private static final Map<UUID, Long> LAST = new HashMap<>();
    private static final Map<UUID, Deque<String>> QUEUE = new HashMap<>();

    /** Сообщение в эфир всем, кто слушает (в пределах острова). */
    public static void say(MinecraftServer server, String text) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (!listening(p)) continue;
            Deque<String> q = QUEUE.computeIfAbsent(p.getUuid(), u -> new ArrayDeque<>());
            if (q.size() < 3 && !q.contains(text)) q.add(text);
        }
    }

    /** Сообщение одному игроку (ответ диспетчера на его вызов). */
    public static void tell(ServerPlayerEntity p, String text) {
        QUEUE.computeIfAbsent(p.getUuid(), u -> new ArrayDeque<>()).add(text);
    }

    /**
     * Сводка о преступлении: все посты слышат, где и что. Если игрока не ищут — патруль может выехать на место
     * (Emergency.policeTo), а не только за игроком.
     */
    public static void report(ServerWorld w, Vec3d at, String what, @Nullable String desc, @Nullable ServerPlayerEntity suspect) {
        String s = "Всем постам: " + what + " " + where(at) + (desc != null && !desc.isEmpty() ? ", " + desc : "") + ".";
        say(w.getServer(), s);
        if (suspect != null && listening(suspect)) tell(suspect, "Диспетчер: подозреваемый " + (suspect.hasVehicle() ? "на машине" : "пешком") + ", будьте осторожны.");
    }

    /** Звезда розыска выросла (из Wanted.add). */
    public static void wanted(ServerPlayerEntity p, String why, int stars) {
        String desc = p.getVehicle() instanceof CarEntity car ? "подозреваемый на машине: " + describe(car) : "подозреваемый пешком";
        String head = stars >= 4 ? "Код красный! " : stars >= 3 ? "Всем экипажам, срочно: " : "Внимание, ";
        String tail = stars >= 4 ? " Вертолёт — в воздух." : stars >= 3 ? " Разрешено применять оружие." : "";
        say(p.getServer(), head + why + " " + where(p.getPos()) + ", " + desc + "." + tail);
    }

    // ------------------------------------------------------------------ фоновый эфир
    private static final String[] CHATTER = {
            "Восьмой, доложите обстановку. — Восьмой: всё тихо, пончики свежие.",
            "Всем постам: на %s сработала сигнализация. Проверить.",
            "Экипаж 12, ДТП %s, без пострадавших. Регулируйте движение.",
            "Диспетчер: шумная вечеринка %s, соседи жалуются.",
            "Внимание: %s замечена машина без номеров. Остановить и проверить.",
            "Пост 3 — диспетчеру: обед. — Принято, третий, не задерживайтесь.",
            "Всем постам: в Порту видели «Чёрных Ящеров». Повышенная готовность.",
            "Экипаж 21: подозрительная личность %s, предлагает часы со скидкой.",
            "Диспетчер: кот на дереве %s. Пожарные заняты — кто свободен?",
            "Всем постам: камера зафиксировала превышение %s. Номер передаём ГИБДД.",
            "Седьмой, ответьте диспетчеру… Седьмой? — Седьмой на связи, был в туннеле.",
            "Внимание: на Ривьере крупный выигрыш в казино. Усилить патруль у «Золотой короны».",
            "Экипаж 5: драка у бара %s. Разнимать без стрельбы."};

    private static final String[] SPOTS = {"на Портовой", "на проспекте Мира", "на Садовой", "на бульваре Огней", "у Сити-Банка", "в Промзоне",
            "на Набережном проспекте", "в Пригороде", "у пляжа", "на Неоновом бульваре", "на Ривьере"};

    private static long nextChatter;

    /** Раз в 5 тиков: отправка очереди (не чаще раза в 6 с на игрока) и фоновый эфир. */
    public static void tick(MinecraftServer server) {
        long t = server.getTicks();
        if (t % 5 != 0) return;
        var r = server.getOverworld().getRandom();
        if (t >= nextChatter) {
            nextChatter = t + 20L * (50 + r.nextInt(60));
            String line = CHATTER[r.nextInt(CHATTER.length)];
            if (line.contains("%s")) line = String.format(line, SPOTS[r.nextInt(SPOTS.length)]);
            say(server, line);
        }
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            Deque<String> q = QUEUE.get(p.getUuid());
            if (q == null || q.isEmpty()) continue;
            if (!listening(p) && !q.peek().startsWith("Диспетчер:")) {
                q.clear();
                continue;
            }
            if (t - LAST.getOrDefault(p.getUuid(), -1000L) < 20 * 6) continue;
            LAST.put(p.getUuid(), t);
            NbtCompound n = new NbtCompound();
            n.putString("text", q.poll());
            LifeNet.send(p, "city_radio", n);
        }
    }

    public static void onLeave(ServerPlayerEntity p) {
        QUEUE.remove(p.getUuid());
        LAST.remove(p.getUuid());
    }
}
