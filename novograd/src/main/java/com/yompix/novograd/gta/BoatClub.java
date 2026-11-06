package com.yompix.novograd.gta;

import com.yompix.novograd.life.Achievements;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.life.LifeNet;
import com.yompix.novograd.story.Story;
import com.yompix.novograd.vehicle.SpeedboatEntity;
import com.yompix.novograd.vehicle.Vehicles;
import com.yompix.novograd.world.Island;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.particle.ParticleTypes;
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
 * Яхт-клуб «Ривьера» на пристани: гонки на катерах по контрольным точкам вдоль западного берега, прокат и покупка катера.
 *  • Взнос $200, катер дают в клубе. Пройди все буйки (столбы света) и вернись к пристани за 4 минуты.
 *  • Таблица рекордов: капитаны клуба и твоё лучшее время. Обогнал третьего — $300, второго — $700, первого — $1500.
 */
public final class BoatClub {
    private BoatClub() {}

    public static final String CLUB = "riv_marina";
    public static final int FEE = 200, RENT = 120, BUY = 3800, LIMIT = 20 * 240;
    static final String[] RIVALS = {"Капитан Марко", "Лиза «Волна»", "Дед Михалыч"};
    static final float[] RIVAL_K = {1.12f, 1.24f, 1.42f};
    static final int[] PRIZES = {1500, 700, 300};

    private static final class Race {
        List<Vec3d> cps;
        int next, ticks = -60;
        UUID boat;
    }

    private static final Map<UUID, Race> RACES = new HashMap<>();
    private static final Map<UUID, Long> RENTS = new HashMap<>(); // катер → когда вернуть

    /** Берег у z: первая точка суши при движении с запада (по карте острова — без загрузки чанков). */
    static int coast(int z) {
        for (int x = -2000; x < -1700; x += 2) if (Island.zone(x, z) != Island.Zone.SEA) return x;
        return -1880;
    }

    /** Маршрут: вдоль берега на север, мористее на юг, обратно к пристани. */
    static List<Vec3d> route() {
        int[][] pts = {{22, 150}, {24, -150}, {26, -420}, {70, -260}, {80, 60}, {80, 420}, {70, 720}, {30, 620}, {16, 330}};
        List<Vec3d> l = new ArrayList<>();
        for (int[] p : pts) l.add(new Vec3d(coast(p[1]) - p[0] + 0.5, Island.G, p[1] + 0.5));
        return l;
    }

    static double length(List<Vec3d> r) {
        double len = 0;
        Vec3d prev = start();
        for (Vec3d v : r) {
            len += Math.hypot(v.x - prev.x, v.z - prev.z);
            prev = v;
        }
        return len;
    }

    static Vec3d start() {
        return new Vec3d(coast(300) - 8 + 0.5, Island.G, 300.5);
    }

    /** Время капитана i в тиках (по длине трассы: катер идёт ~1.15 блока за тик). */
    static int rivalTicks(int i) {
        return (int) (length(route()) / 1.1 * RIVAL_K[i]);
    }

    static void tickPlayer(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        Vec3d at = Story.place(w, CLUB);
        if (at != null && p.squaredDistanceTo(at) < 48 * 48) {
            int y = Story.topY(w, (int) Math.floor(at.x), (int) Math.floor(at.z));
            Vec3d m = new Vec3d(at.x, y, at.z);
            if (p.age % 40 < 10)
                FunUtil.marker(w, m, "nv_boatclub", new ItemStack(Items.OAK_BOAT), Text.literal("⚓ Яхт-клуб «Ривьера»").formatted(Formatting.AQUA), true);
            DriveHooks.walkIn(p, "boat", m, () -> {
                NbtCompound n = DriveHooks.state(p);
                n.putString("open", "boat");
                LifeNet.send(p, "drv_open", n);
            });
        }
        Race r = RACES.get(p.getUuid());
        if (r != null) tickRace(p, r);
    }

    static void putBoard(ServerPlayerEntity p, NbtCompound n) {
        NbtList board = new NbtList();
        for (int i = 0; i < RIVALS.length; i++) {
            NbtCompound c = new NbtCompound();
            c.putString("name", RIVALS[i]);
            c.putInt("t", rivalTicks(i));
            board.add(c);
        }
        DriveState st = DriveState.get(p.getServer());
        for (String k : st.players.getKeys()) {
            int best = st.players.getCompound(k).getInt("boat");
            if (best <= 0) continue;
            NbtCompound c = new NbtCompound();
            String nm = st.players.getCompound(k).getString("name");
            c.putString("name", nm.isEmpty() ? "Игрок" : nm);
            c.putInt("t", best);
            c.putBoolean("me", k.equals(p.getUuidAsString()));
            board.add(c);
        }
        n.put("board", board);
        n.putInt("fee", FEE);
        n.putInt("rent", RENT);
        n.putInt("buy", BUY);
    }

    private static SpeedboatEntity spawnBoat(ServerPlayerEntity p, int color) {
        ServerWorld w = p.getServerWorld();
        Vec3d s = start();
        w.getChunk((int) Math.floor(s.x) >> 4, (int) Math.floor(s.z) >> 4);
        double y = w.getSeaLevel() - 0.3;
        SpeedboatEntity b = new SpeedboatEntity(Vehicles.SPEEDBOAT, w);
        b.refreshPositionAndAngles(s.x, y, s.z, 180, 0);
        b.paint(color);
        w.spawnEntity(b);
        return b;
    }

    private static boolean nearClub(ServerPlayerEntity p) {
        Vec3d at = Story.place(p.getServerWorld(), CLUB);
        return at != null && p.squaredDistanceTo(at) < 16 * 16;
    }

    public static void action(ServerPlayerEntity p, String what) {
        if (!nearClub(p)) {
            p.sendMessage(Text.literal("Это делается в яхт-клубе на пристани Ривьеры").formatted(Formatting.RED), true);
            return;
        }
        switch (what) {
            case "race" -> {
                if (RACES.containsKey(p.getUuid()) || FunUtil.busy(p)) return;
                if (!Life.pay(p, FEE, "взнос за гонку катеров")) return;
                SpeedboatEntity b = spawnBoat(p, 0xF2C032);
                b.addCommandTag("nv_boatrace");
                p.teleport(p.getServerWorld(), b.getX(), b.getY() + 0.5, b.getZ(), 180, 10);
                p.startRiding(b, true);
                Race r = new Race();
                r.cps = route();
                r.boat = b.getUuid();
                RACES.put(p.getUuid(), r);
                FunUtil.title(p, "ГОНКА КАТЕРОВ", "Пройди " + r.cps.size() + " буйков · рекорд клуба " + time(rivalTicks(0)));
            }
            case "rent" -> {
                if (!Life.pay(p, RENT, "прокат катера на 10 минут")) return;
                SpeedboatEntity b = spawnBoat(p, 0x1E5AA8);
                b.addCommandTag("nv_rent");
                b.setOwner(p.getUuid());
                RENTS.put(b.getUuid(), p.getServerWorld().getTime() + 20 * 600);
                p.teleport(p.getServerWorld(), b.getX(), b.getY() + 0.5, b.getZ(), 180, 10);
                p.startRiding(b, true);
                FunUtil.title(p, "КАТЕР НАПРОКАТ", "10 минут · потом его заберёт клуб");
            }
            case "buy" -> {
                if (Economy.act(Life.get(p)) < 1) {
                    p.sendMessage(Text.literal("Клуб: «Членство — только для известных в городе людей»").formatted(Formatting.GOLD), false);
                    return;
                }
                if (!Life.pay(p, BUY, "свой катер")) return;
                DriveHooks.give(p, new ItemStack(Vehicles.SPEEDBOAT_ITEM));
                Economy.shock(p.getServer(), "RIVH", 0.01, "");
                FunUtil.title(p, "КАТЕР ТВОЙ", "Спусти на воду: ПКМ по воде");
            }
            default -> {}
        }
    }

    private static void tickRace(ServerPlayerEntity p, Race r) {
        ServerWorld w = p.getServerWorld();
        r.ticks++;
        if (r.ticks < 0) {
            int t = -r.ticks;
            if (t % 20 == 0) {
                String[] cd = {"ВПЕРЁД!", "1", "2", "3"};
                int s = t / 20;
                if (s < 4) FunUtil.title(p, cd[s], "");
                p.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), SoundCategory.MASTER, 1f, s == 0 ? 2f : 1f);
            }
            if (p.getVehicle() instanceof SpeedboatEntity b) b.speed = 0;
            return;
        }
        if (r.ticks == 0) FunUtil.title(p, "ВПЕРЁД!", "");
        Vec3d cp = r.cps.get(r.next);
        FunUtil.target(p, cp, r.next == r.cps.size() - 1 ? "Финиш" : "Буй " + (r.next + 1) + "/" + r.cps.size());
        if (r.ticks % 20 == 0) FunUtil.timer(p, (LIMIT - r.ticks) / 20);
        {
            double y = w.getSeaLevel();
            for (int i = 0; i < 10; i++) w.spawnParticles(p, ParticleTypes.END_ROD, true, cp.x, y + i * 1.6, cp.z, 2, 0.4, 0.4, 0.4, 0.01);
            if (r.next + 1 < r.cps.size()) {
                Vec3d nx = r.cps.get(r.next + 1);
                w.spawnParticles(p, ParticleTypes.GLOW, true, nx.x, y + 3, nx.z, 3, 0.5, 2, 0.5, 0.01);
            }
        }
        boolean onBoat = p.getVehicle() instanceof SpeedboatEntity b && b.getUuid().equals(r.boat);
        if (!onBoat || r.ticks > LIMIT || p.isDead()) {
            end(p, r);
            FunUtil.title(p, "ГОНКА ПРОВАЛЕНА", r.ticks > LIMIT ? "Время вышло" : "Ты покинул катер");
            return;
        }
        if (Math.hypot(p.getX() - cp.x, p.getZ() - cp.z) < 13) {
            r.next++;
            p.playSoundToPlayer(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.MASTER, 1f, 1.2f);
            if (r.next >= r.cps.size()) finish(p, r);
        }
    }

    private static void finish(ServerPlayerEntity p, Race r) {
        int t = r.ticks;
        end(p, r);
        DriveState st = DriveState.get(p.getServer());
        NbtCompound me = st.of(p);
        int best = me.getInt("boat");
        boolean record = best <= 0 || t < best;
        if (record) me.putInt("boat", t);
        me.putString("name", p.getName().getString());
        st.markDirty();
        int place = RIVALS.length;
        for (int i = RIVALS.length - 1; i >= 0; i--) if (t < rivalTicks(i)) place = i;
        if (place < RIVALS.length) {
            Life.earn(p, PRIZES[place], "приз яхт-клуба");
            FunUtil.title(p, place == 0 ? "РЕКОРД КЛУБА!" : (place + 1) + "-Е МЕСТО", time(t) + " · обогнал: " + RIVALS[place] + " · +$" + PRIZES[place]);
            Achievements.give(p, "street_race");
            if (place == 0) News.post(p.getServer(), "Яхт-клуб Ривьеры: новый рекорд гонки катеров — " + time(t), "СПОРТ");
        } else FunUtil.title(p, "ФИНИШ", time(t) + (record ? " · личный рекорд" : "") + " · капитаны быстрее — попробуй ещё");
    }

    private static void end(ServerPlayerEntity p, Race r) {
        RACES.remove(p.getUuid());
        FunUtil.clearHud(p);
        ServerWorld w = p.getServerWorld();
        if (w.getEntity(r.boat) instanceof SpeedboatEntity b) {
            RENTS.put(b.getUuid(), w.getTime() + 20 * 60); // ещё минута — доплыть до берега
        }
    }

    static void tick(MinecraftServer s) {
        if (s.getTicks() % 100 != 17 || RENTS.isEmpty()) return;
        ServerWorld w = s.getOverworld();
        long now = w.getTime();
        for (var it = RENTS.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (now < e.getValue()) continue;
            var ent = w.getEntity(e.getKey());
            if (ent == null) {
                it.remove();
                continue;
            }
            if (ent.hasPassengers()) {
                if (ent.getFirstPassenger() instanceof ServerPlayerEntity pl) pl.sendMessage(Text.literal("⚓ Прокат закончился — сойди на берег, клуб заберёт катер").formatted(Formatting.GOLD), true);
                e.setValue(now + 20 * 30);
                continue;
            }
            it.remove();
            ent.discard();
        }
    }

    public static void onLeave(ServerPlayerEntity p) {
        Race r = RACES.remove(p.getUuid());
        if (r != null && p.getServerWorld().getEntity(r.boat) != null) p.getServerWorld().getEntity(r.boat).discard();
    }

    public static boolean busy(ServerPlayerEntity p) {
        return RACES.containsKey(p.getUuid());
    }

    public static String time(int ticks) {
        int s = ticks / 20;
        return s / 60 + ":" + String.format("%02d", s % 60) + "." + (ticks % 20) / 2;
    }
}
