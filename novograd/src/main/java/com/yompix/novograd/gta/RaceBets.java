package com.yompix.novograd.gta;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.life.LifeNet;
import com.yompix.novograd.story.Story;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/**
 * Тотализатор у старта трассы (race_start): уличные гонщики устраивают заезды по большому кольцу, ты ставишь на одного.
 * Перед заездом — 4 гонщика с машинами и коэффициентами; результат решается по их «форме» и удаче, а сами машины
 * реально стартуют и едут по трассе (победитель — самый быстрый). Выигрыш — чистыми на карту.
 */
public final class RaceBets {
    private RaceBets() {}

    public static final String DESK = "race_start+0,16";
    static final String[] DRIVERS = {"Вадим «Турбо»", "Кира Скорость", "Гоша Дрифт", "Ахмед «Ночь»", "Сэм Ракета", "Нина Нитро", "Боря Шумахер", "Лёха Север"};
    static final int[] CARS = {CarEntity.SUPERCAR, 9, 2, CarEntity.CLASSIC, CarEntity.BUGGY, 3, CarEntity.COMPACT, 7};
    static final int[] BETS = {100, 500, 1000, 5000};

    /** Один заезд на сервер (общий для всех, кто смотрит). */
    private static final class Race {
        int[] who = new int[4];
        float[] form = new float[4];
        double[] odds = new double[4];
        int[] order;             // итоговые места (индексы 0..3), решено заранее
        int stage, timer, run;   // 0 — приём ставок, 1 — отсчёт, 2 — едут, 3 — итоги
        final List<UUID> cars = new ArrayList<>();
        final Map<UUID, int[]> bets = new HashMap<>(); // игрок → {гонщик, сумма}
        long next;
    }

    private static Race race;
    private static long nextRace;

    static List<Vec3d> route(ServerWorld w, int lane) {
        // по кольцу: на восток по z=−744, на юг по x=744, на запад по z=24 до финиша
        double o = lane * 3.2 - 4.8;
        double[][] pts = {{420, -744 + o}, {744 - o, -744 + o}, {744 - o, -200}, {744 - o, 24 + o}, {400, 24 + o}, {168, 24 + o}};
        List<Vec3d> l = new ArrayList<>();
        for (double[] p : pts) l.add(new Vec3d(p[0] + 0.5, Story.topY(w, (int) p[0], (int) p[1]), p[1] + 0.5));
        return l;
    }

    private static Race newRace(ServerWorld w) {
        Race r = new Race();
        var rnd = w.getRandom();
        List<Integer> pool = new ArrayList<>();
        for (int i = 0; i < DRIVERS.length; i++) pool.add(i);
        Collections.shuffle(pool, new Random(rnd.nextLong()));
        float sum = 0;
        for (int i = 0; i < 4; i++) {
            r.who[i] = pool.get(i);
            r.form[i] = 0.6f + rnd.nextFloat() * 1.4f + (CARS[r.who[i]] == CarEntity.SUPERCAR ? 0.4f : 0);
            sum += r.form[i];
        }
        for (int i = 0; i < 4; i++) r.odds[i] = Math.max(1.2, Math.round(0.88 * sum / r.form[i] * 10) / 10.0);
        r.stage = 0;
        r.timer = 20 * 45; // ставки принимаются 45 секунд после того, как кто-то подошёл
        return r;
    }

    static void tickPlayer(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        Vec3d at = Story.place(w, DESK);
        if (at == null || p.squaredDistanceTo(at) > 48 * 48) return;
        int y = Story.topY(w, (int) Math.floor(at.x), (int) Math.floor(at.z));
        Vec3d m = new Vec3d(at.x, y, at.z);
        if (p.age % 40 < 10)
            FunUtil.marker(w, m, "nv_racebets", new ItemStack(Items.GOLD_INGOT), Text.literal("⚑ Тотализатор · ставки на гонщиков").formatted(Formatting.GOLD), true);
        DriveHooks.walkIn(p, "bets", m, () -> open(p));
    }

    static void open(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        if (race == null || race.stage == 3 && race.timer <= 0) race = newRace(w);
        NbtCompound n = DriveHooks.state(p);
        n.putString("open", "bets");
        putRace(n, p);
        LifeNet.send(p, "drv_open", n);
    }

    static void putRace(NbtCompound n, ServerPlayerEntity p) {
        if (race == null) return;
        NbtList l = new NbtList();
        for (int i = 0; i < 4; i++) {
            NbtCompound c = new NbtCompound();
            c.putString("name", DRIVERS[race.who[i]]);
            c.putString("car", com.yompix.novograd.gta.Impound.name(CARS[race.who[i]]));
            c.putDouble("odds", race.odds[i]);
            l.add(c);
        }
        n.put("drivers", l);
        n.putInt("stage", race.stage);
        n.putInt("sec", race.timer / 20);
        int[] b = race.bets.get(p.getUuid());
        if (b != null) {
            n.putInt("myDriver", b[0]);
            n.putInt("myBet", b[1]);
        }
        NbtList bets = new NbtList();
        for (int v : BETS) {
            NbtCompound c = new NbtCompound();
            c.putInt("v", v);
            bets.add(c);
        }
        n.put("bets", bets);
    }

    public static void bet(ServerPlayerEntity p, int driver, int amount) {
        ServerWorld w = p.getServerWorld();
        Vec3d at = Story.place(w, DESK);
        if (race == null || at == null || p.squaredDistanceTo(at) > 12 * 12) return;
        if (race.stage != 0) {
            p.sendMessage(Text.literal("Ставки уже не принимаются — заезд идёт").formatted(Formatting.RED), true);
            return;
        }
        if (driver < 0 || driver > 3 || Arrays.stream(BETS).noneMatch(b -> b == amount) || race.bets.containsKey(p.getUuid())) return;
        if (!Life.pay(p, amount, "ставка на " + DRIVERS[race.who[driver]])) return;
        race.bets.put(p.getUuid(), new int[]{driver, amount});
        race.timer = Math.min(race.timer, 20 * 15);
        FunUtil.title(p, "СТАВКА ПРИНЯТА", "$" + amount + " на " + DRIVERS[race.who[driver]] + " · ×" + race.odds[driver] + " · старт через " + race.timer / 20 + " с");
    }

    static void tick(MinecraftServer s) {
        if (race == null) return;
        ServerWorld w = s.getOverworld();
        Race r = race;
        if (r.stage == 2) {
            r.run++;
            // лидер реально доехал — или прошло 95 секунд (машины вдали от игроков не загружены)
            boolean done = r.run > 20 * 95;
            if (!done && w.getEntity(r.cars.get(r.order[0])) instanceof CarEntity lead && !lead.onRoute() && r.run > 200) done = true;
            if (r.run % 20 == 0) live(s, r);
            if (done) results(s, w, r);
            return;
        }
        if (--r.timer > 0) {
            if (r.stage == 1 && r.timer % 20 == 0) countdown(s, r);
            return;
        }
        switch (r.stage) {
            case 0 -> {
                if (r.bets.isEmpty()) {
                    race = null; // никто не поставил — заезд отменён
                    return;
                }
                decide(w, r);
                spawn(w, r);
                r.stage = 1;
                r.timer = 20 * 4;
            }
            case 1 -> {
                for (int i = 0; i < r.cars.size(); i++)
                    if (w.getEntity(r.cars.get(i)) instanceof CarEntity car) {
                        int place = indexOf(r.order, i);
                        car.setRoute(route(w, i), 0.98f - place * 0.045f + w.getRandom().nextFloat() * 0.01f, null);
                    }
                for (UUID id : r.bets.keySet()) {
                    ServerPlayerEntity p = s.getPlayerManager().getPlayer(id);
                    if (p != null) FunUtil.title(p, "СТАРТ!", "Гонка по кольцу · итоги через ~1,5 мин (СМС)");
                }
                r.stage = 2;
                r.run = 0;
            }
            default -> {
                for (UUID id : r.cars) FunUtil.discard(w, id);
                race = null;
            }
        }
    }

    private static int indexOf(int[] a, int v) {
        for (int i = 0; i < a.length; i++) if (a[i] == v) return i;
        return 0;
    }

    /** Итоговые места: по очереди тянем «жребий» с весом формы гонщика. */
    private static void decide(ServerWorld w, Race r) {
        List<Integer> left = new ArrayList<>(List.of(0, 1, 2, 3));
        r.order = new int[4];
        for (int k = 0; k < 4; k++) {
            float total = 0;
            for (int i : left) total += r.form[i];
            float x = w.getRandom().nextFloat() * total;
            int pick = left.get(left.size() - 1);
            for (int i : left) if ((x -= r.form[i]) <= 0) {
                pick = i;
                break;
            }
            r.order[k] = pick;
            left.remove((Integer) pick);
        }
    }

    private static void spawn(ServerWorld w, Race r) {
        Vec3d st = Story.place(w, "race_start");
        if (st == null) return;
        int[] bright = {0xF2C032, 0xF07A12, 0x7BC639, 0xD7262C, 0x9B3FD1, 0x28E6FF, 0xE8E8EA, 0x15161A};
        for (int i = 0; i < 4; i++) {
            double lane = i * 3.2 - 4.8, back = (i % 2) * 7;
            Vec3d at = new Vec3d(st.x - back, Story.topY(w, (int) st.x, (int) (st.z + lane)), st.z + lane);
            CarEntity car = FunUtil.car(w, at, -90, CARS[r.who[i]]);
            car.paint(bright[r.who[i] % bright.length]);
            car.setTune(true, 2, 0);
            car.toggleLight(CarEntity.HEADLIGHTS);
            car.addCommandTag("nv_betrace");
            CitizenEntity drv = FunUtil.citizen(w, at, CitizenEntity.Role.CITIZEN, DRIVERS[r.who[i]]);
            drv.setCustomNameVisible(true);
            drv.startRiding(car, true);
            r.cars.add(car.getUuid());
        }
        w.playSound(null, net.minecraft.util.math.BlockPos.ofFloored(st), ModContent.CAR_HORN, SoundCategory.NEUTRAL, 2f, 0.8f);
    }

    private static void countdown(MinecraftServer s, Race r) {
        int sec = r.timer / 20;
        for (UUID id : r.bets.keySet()) {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(id);
            if (p == null) continue;
            FunUtil.title(p, String.valueOf(sec), "Гонщики на старте");
            p.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), SoundCategory.MASTER, 1f, 1f);
        }
    }

    /** Живой счёт для тех, кто поставил: позиции по реальным машинам (если видно) — в полоске над радаром. */
    private static void live(MinecraftServer s, Race r) {
        NbtCompound n = new NbtCompound();
        n.putString("kind", "bets");
        StringBuilder sb = new StringBuilder("⚑ Заезд: ");
        int shown = Math.min(4, 1 + r.timer / (20 * 20));
        for (int k = 0; k < shown; k++) sb.append(k + 1).append(". ").append(DRIVERS[r.who[r.order[k]]].split(" ")[0]).append("  ");
        if (shown < 4) sb.append("…");
        n.putString("text", sb.toString());
        for (UUID id : r.bets.keySet()) {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(id);
            if (p != null) LifeNet.send(p, "vjob", n);
        }
    }

    private static void results(MinecraftServer s, ServerWorld w, Race r) {
        r.stage = 3;
        r.timer = 20 * 20;
        String winner = DRIVERS[r.who[r.order[0]]];
        StringBuilder top = new StringBuilder();
        for (int k = 0; k < 4; k++) top.append(k + 1).append(". ").append(DRIVERS[r.who[r.order[k]]]).append(k < 3 ? ", " : "");
        for (var e : r.bets.entrySet()) {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(e.getKey());
            if (p == null) continue;
            LifeNet.send(p, "vjob", new NbtCompound());
            int[] b = e.getValue();
            if (b[0] == r.order[0]) {
                int win = (int) Math.round(b[1] * r.odds[b[0]]);
                Life.earn(p, win, "выигрыш на тотализаторе");
                Life.message(p, "Тотализатор", "Победил " + winner + "! Твоя ставка сыграла: +$" + win + ". Итог: " + top);
                FunUtil.title(p, "СТАВКА СЫГРАЛА!", winner + " первый · +$" + win);
            } else {
                Life.message(p, "Тотализатор", "Победил " + winner + ". Ставка на " + DRIVERS[r.who[b[0]]] + " не сыграла. Итог: " + top);
                FunUtil.title(p, "СТАВКА ПРОИГРАНА", "Победил " + winner);
            }
        }
        if (w.getRandom().nextInt(3) == 0) News.post(s, "Уличные гонки на кольце: победу снова взял " + winner, "СПОРТ");
    }

    public static boolean busy(ServerPlayerEntity p) {
        return false;
    }
}
