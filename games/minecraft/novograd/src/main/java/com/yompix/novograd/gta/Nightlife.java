package com.yompix.novograd.gta;

import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.life.Achievements;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.life.LifeData;
import com.yompix.novograd.life.LifeNet;
import com.yompix.novograd.story.Story;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 0.9.9: ночная жизнь. Каждый вечер с 19:00 до 22:00 — концерт под открытым небом (чётные дни — деловая площадь,
 * нечётные — Стрип Ривьеры): группа играет (гитара, саксофон, брейк), толпа танцует и хлопает, музыка слышна на
 * квартал. Был на концерте минуту — настроение, пост в «Фотограме», немного славы. Анонс — в «Новоград-24».
 * Остальное ночью: бильярд и боулинг (gta/Games), дартс и автоматы (Arcade), диджей в «Неоне» (Fitness), бар Розы.
 * Телефон → «Ночь» — что где сегодня (пакет «night»).
 */
public final class Nightlife {
    private Nightlife() {}

    static final String[] BANDS = {"«Порт-Сити Бэнд»", "«Неоновые Кошки»", "DJ Волна", "«Ржавый Трамвай»", "«Белые Ночи»", "«Сирены Ривьеры»"};
    static final String[] VENUES = {"downtown_plaza", "riv_strip"};
    static final String[] VENUE_NAMES = {"Деловая площадь", "Стрип Ривьеры"};
    public static final int FROM = 13000, TO = 16000; // 19:00–22:00

    public static boolean concertNow(ServerWorld w) {
        long tod = w.getTimeOfDay() % 24000;
        return tod >= FROM && tod < TO;
    }

    static int venue(long day) {
        return (int) Math.floorMod(day, 2);
    }

    static String band(long day) {
        return BANDS[(int) Math.floorMod(day * 7 + 3, BANDS.length)];
    }

    private static final List<UUID> SPAWNED = new ArrayList<>();
    private static final Map<UUID, Integer> WATCH = new HashMap<>();
    private static long announced = -1;

    public static void init() {
        if (!Boolean.getBoolean("novograd.no099")) ServerTickEvents.END_SERVER_TICK.register(Nightlife::tick); // замер «до/после» (MSPT)
        Achievements.ALL.putIfAbsent("concert", new String[]{"Живой звук", "Побывать на концерте"});
    }

    private static void tick(MinecraftServer s) {
        if (s.getTicks() % 20 != 13) return;
        ServerWorld w = s.getOverworld();
        if (!(w.getChunkManager().getChunkGenerator() instanceof com.yompix.novograd.world.IslandChunkGenerator)) return;
        long day = w.getTimeOfDay() / 24000L + 1;
        long tod = w.getTimeOfDay() % 24000;
        if (tod >= FROM - 1500 && tod < FROM && announced != day) {
            announced = day;
            News.post(s, "Сегодня в 19:00: концерт " + band(day) + " — " + VENUE_NAMES[venue(day)] + ". Вход свободный", "ГОРОД");
        }
        if (!concertNow(w)) {
            clear(w);
            ConcertStage.clear(w);
            WATCH.clear();
            return;
        }
        Vec3d at = Story.place(w, VENUES[venue(day)]);
        if (at == null) return;
        boolean near = false;
        for (ServerPlayerEntity p : w.getPlayers()) near |= p.squaredDistanceTo(at) < 120 * 120;
        if (!near) {
            clear(w);
            return;
        }
        if (SPAWNED.isEmpty()) {
            ConcertStage.build(w, at); // 0.9.10: настоящая сцена — подиум, свет, колонки, пульт
            spawn(w, at, day);
            com.yompix.novograd.Novograd.LOG.info("[ночь] концерт {}: сцена {} блоков, людей {}", VENUES[venue(day)], ConcertStage.size(s), SPAWNED.size());
        }
        // музыка (каждые 4 с — свой инструмент; клиент слышит позиционно)
        if (s.getTicks() % 80 == 13) {
            var ev = switch ((int) ((s.getTicks() / 80) % 3)) {
                case 0 -> StreetPerformers.GUITAR;
                case 1 -> StreetPerformers.SAX;
                default -> StreetPerformers.BEAT;
            };
            w.playSound(null, at.x, at.y + 2, at.z, ev, SoundCategory.RECORDS, 3.5f, 1f);
        }
        for (ServerPlayerEntity p : w.getPlayers()) {
            if (p.squaredDistanceTo(at) > 20 * 20) continue;
            int sec = WATCH.merge(p.getUuid(), 1, Integer::sum);
            if (sec == 60) {
                LifeData d = Life.get(p);
                d.mood = Math.min(100, d.mood + 20);
                Achievements.give(p, "concert");
                FunUtil.title(p, "КОНЦЕРТ", band(day) + " · настроение +20");
                Social.post(s, "@" + p.getName().getString(), "на концерте " + band(day) + " 🎸 #новограднощью", p, 15 + p.getRandom().nextInt(30), 3);
                Life.sync(p);
            }
        }
    }

    private static void spawn(ServerWorld w, Vec3d at, long day) {
        // на сцене: гитара и саксофон впереди, танцор в центре, диджей за пультом
        Vec3d o = Vec3d.ofBottomCenter(net.minecraft.util.math.BlockPos.ofFloored(at));
        Vec3d[] spots = {o.add(-3, ConcertStage.RISE, -4), o.add(3, ConcertStage.RISE, -4), o.add(0, ConcertStage.RISE, -3), o.add(-0.5, ConcertStage.RISE, -7)};
        CitizenEntity.Activity[] band = {CitizenEntity.Activity.GUITAR, CitizenEntity.Activity.SAX, CitizenEntity.Activity.BREAKDANCE, CitizenEntity.Activity.DANCE2};
        for (int i = 0; i < spots.length; i++) {
            CitizenEntity c = FunUtil.citizen(w, spots[i], CitizenEntity.Role.CITIZEN, i == 0 ? band(day) : i == 3 ? "DJ" : null);
            c.setStationary(true);
            c.setYaw(0);
            c.setBodyYaw(0);
            c.setHeadYaw(0);
            c.busy(band[i], 20 * 160, null, ItemStack.EMPTY);
            c.removeLater(20 * 170);
            SPAWNED.add(c.getUuid());
        }
        CitizenEntity.Activity[] crowd = {CitizenEntity.Activity.DANCE, CitizenEntity.Activity.CHEER, CitizenEntity.Activity.DANCE2, CitizenEntity.Activity.CLAP,
                CitizenEntity.Activity.DANCE3};
        for (int i = 0; i < 12; i++) {
            double a = i * 0.9, r = 3.5 + (i % 3);
            CitizenEntity c = FunUtil.citizen(w, at.add(Math.sin(a) * r, 0, 1 + Math.abs(Math.cos(a)) * r), CitizenEntity.Role.CITIZEN, null);
            c.setStationary(true);
            c.busy(crowd[i % crowd.length], 20 * 160, null, ItemStack.EMPTY);
            c.removeLater(20 * 170);
            SPAWNED.add(c.getUuid());
        }
    }

    private static void clear(ServerWorld w) {
        if (SPAWNED.isEmpty()) return;
        for (UUID u : SPAWNED) if (w.getEntity(u) != null) w.getEntity(u).discard();
        SPAWNED.clear();
    }

    /** Телефон → «Ночь». */
    public static boolean handle(ServerPlayerEntity p, String type, NbtCompound n) {
        if (!type.equals("night_state")) return false;
        ServerWorld w = p.getServerWorld();
        long day = w.getTimeOfDay() / 24000L + 1;
        NbtCompound out = new NbtCompound();
        out.putString("band", band(day));
        out.putString("venue", VENUE_NAMES[venue(day)]);
        Vec3d at = Story.place(w, VENUES[venue(day)]);
        if (at != null) {
            out.putDouble("x", at.x);
            out.putDouble("z", at.z);
        }
        out.putBoolean("now", concertNow(w));
        out.putLong("tod", w.getTimeOfDay() % 24000);
        NbtList places = new NbtList();
        String[][] pl = {{"club_neon", "Клуб «Неон»", "диджей, бильярд, боулинг, бар"}, {"beach_bar", "Пляжный бар", "бильярд, дартс, коктейли"},
                {"riv_strip", "Стрип Ривьеры", "боулинг, бильярд, дартс, танцы"}, {"casino_dt", "Казино центра", "18+"}};
        for (String[] e : pl) {
            Vec3d v = Story.place(w, e[0]);
            if (v == null) continue;
            NbtCompound c = new NbtCompound();
            c.putString("name", e[1]);
            c.putString("what", e[2]);
            c.putDouble("x", v.x);
            c.putDouble("z", v.z);
            places.add(c);
        }
        out.put("places", places);
        Games.fill(out, w);
        LifeNet.send(p, "night", out);
        return true;
    }

    /** Где концерт сегодня (место из Story.place). */
    public static String todayVenue(ServerWorld w) {
        return VENUES[venue(w.getTimeOfDay() / 24000L + 1)];
    }

    /** Для автотеста: что поставлено. */
    public static int spawned() {
        return SPAWNED.size();
    }

    static Box box(Vec3d at) {
        return new Box(at, at).expand(25);
    }
}
