package com.yompix.novograd.gta;

import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.life.LifeData;
import com.yompix.novograd.life.LifeNet;
import com.yompix.novograd.story.Story;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 0.9: «Империя» — бизнесы, районы и жильё одним экраном (телефон → «Империя»), с угрозами, наймом и целями.
 * <ul>
 *   <li><b>Рейд</b>: раз в игровые сутки на свой бизнес может наехать банда (25 %, с охраной — 10 %). Метка на карте,
 *       3 минуты: приехал и отбился — бизнес цел, не успел — банда забрала сейф.</li>
 *   <li><b>Найм</b>: управляющий (+15 % дохода, цена — 20 % стоимости бизнеса), охрана (рейды реже, 15 %).</li>
 *   <li><b>Цели</b>: первый миллион, все бизнесы, все районы, 5 домов — денежные награды и достижения.</li>
 * </ul>
 * Всё — флаги LifeData ({@code bizmgr:i}, {@code bizgrd:i}, {@code raidday=N}, {@code goal:id}) — старые сохранения работают.
 */
public final class Empire {
    private Empire() {}

    private static final class Raid {
        int biz;
        long until;
        final List<UUID> gang = new ArrayList<>();
        boolean spawned;
    }

    private static final Map<UUID, Raid> RAIDS = new HashMap<>();

    public static final String[][] GOALS = {
            {"million", "Первый миллион", "100000"}, {"allbiz", "Все бизнесы", "200000"}, {"allturf", "Все районы", "150000"},
            {"homes5", "Пять домов", "100000"}};

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(Empire::tick);
    }

    private static long day(ServerWorld w) {
        return w.getTimeOfDay() / 24000;
    }

    public static boolean raidAt(ServerPlayerEntity p, int i) {
        Raid r = RAIDS.get(p.getUuid());
        return r != null && r.biz == i;
    }

    @org.jetbrains.annotations.Nullable
    public static Vec3d raidPos(ServerPlayerEntity p) {
        Raid r = RAIDS.get(p.getUuid());
        return r == null ? null : Story.place(p.getServerWorld(), Heists.businessPlace(r.biz));
    }

    private static void tick(MinecraftServer s) {
        if (s.getTicks() % 20 != 0) return;
        ServerWorld w = s.getOverworld();
        for (ServerPlayerEntity p : w.getPlayers()) {
            LifeData d = Life.get(p);
            Raid r = RAIDS.get(p.getUuid());
            if (r != null) {
                raidTick(p, d, r);
                continue;
            }
            goals(p, d);
            // раз в игровые сутки — бросок на рейд (только когда игрок свободен)
            long today = day(w);
            String key = "raidday=" + today;
            if (d.flags.contains(key) || s.getTicks() % 200 != 0) continue;
            d.flags.removeIf(f -> f.startsWith("raidday="));
            d.flags.add(key);
            if (Story.active(p) && !Story.idle(d) || BigHeists.active(p)) continue;
            List<Integer> own = new ArrayList<>();
            for (int i = 0; i < Heists.businessCount(); i++) if (d.flags.contains("biz:" + i)) own.add(i);
            if (own.isEmpty()) continue;
            int i = own.get(p.getRandom().nextInt(own.size()));
            int chance = d.flags.contains("bizgrd:" + i) ? 10 : 25;
            if (p.getRandom().nextInt(100) >= chance) continue;
            r = new Raid();
            r.biz = i;
            r.until = w.getTime() + 20 * 60 * 3;
            RAIDS.put(p.getUuid(), r);
            Activities.title(p, "РЕЙД", Heists.businessName(i) + " · 3 минуты");
            Life.message(p, "Управляющий", "На " + Heists.businessName(i) + " наехали! Приезжай, пока не вынесли сейф.");
        }
    }

    private static void raidTick(ServerPlayerEntity p, LifeData d, Raid r) {
        ServerWorld w = p.getServerWorld();
        Vec3d at = Story.place(w, Heists.businessPlace(r.biz));
        if (at == null) {
            RAIDS.remove(p.getUuid());
            return;
        }
        FunUtil.target(p, at, "Рейд");
        if (!r.spawned && p.squaredDistanceTo(at) < 60 * 60) {
            r.spawned = true;
            int n = 3 + p.getRandom().nextInt(2) + (int) (Life.day(p) % 2);
            for (int k = 0; k < n; k++) {
                double a = k * 2.1;
                CitizenEntity g = FunUtil.gangster(w, at.add(Math.cos(a) * 3, 0, Math.sin(a) * 3), "Рейдер", k % 2 == 0);
                r.gang.add(g.getUuid());
            }
        }
        if (r.spawned) {
            boolean alive = false;
            for (UUID u : r.gang) if (w.getEntity(u) instanceof CitizenEntity c && c.isAlive()) alive = true;
            if (!alive) {
                RAIDS.remove(p.getUuid());
                FunUtil.target(p, null, "");
                int bonus = 500 + 250 * r.gang.size();
                Life.earn(p, bonus, "рейд отбит");
                Activities.title(p, "РЕЙД ОТБИТ", Heists.businessName(r.biz) + " · +$" + bonus);
                com.yompix.novograd.life.Achievements.give(p, "raid_won");
                return;
            }
        }
        if (w.getTime() >= r.until) {
            RAIDS.remove(p.getUuid());
            FunUtil.target(p, null, "");
            int lost = Heists.safeOf(d, r.biz);
            Heists.setSafeOf(d, r.biz, 0);
            for (UUID u : r.gang) FunUtil.discard(w, u);
            Activities.title(p, "РЕЙД", Heists.businessName(r.biz) + ": сейф вынесли" + (lost > 0 ? " (−$" + lost + ")" : ""));
        }
    }

    private static void goals(ServerPlayerEntity p, LifeData d) {
        boolean[] ok = goalState(p, d);
        for (int i = 0; i < GOALS.length; i++) {
            if (!ok[i] || !d.flags.add("goal:" + GOALS[i][0])) continue;
            int prize = Integer.parseInt(GOALS[i][2]);
            Life.earn(p, prize, "цель «Империи»: " + GOALS[i][1]);
            Activities.title(p, "ЦЕЛЬ ИМПЕРИИ", GOALS[i][1] + " · +$" + prize);
            com.yompix.novograd.life.Achievements.give(p, "empire_" + GOALS[i][0]);
        }
    }

    private static boolean[] goalState(ServerPlayerEntity p, LifeData d) {
        int biz = 0;
        for (int i = 0; i < Heists.businessCount(); i++) if (d.flags.contains("biz:" + i)) biz++;
        int homes = com.yompix.novograd.world.Housing.homes(p).size();
        return new boolean[]{d.money >= 1_000_000, biz == Heists.businessCount(), Territories.ownedCount(p.getServer()) >= Territories.TURF.length, homes >= 5};
    }

    /** Для автотеста и ролика: рейд прямо сейчас на бизнес i. */
    public static void forceRaid(ServerPlayerEntity p, int i) {
        Raid r = new Raid();
        r.biz = i;
        r.until = p.getServerWorld().getTime() + 20 * 60 * 3;
        RAIDS.put(p.getUuid(), r);
        Activities.title(p, "РЕЙД", Heists.businessName(i) + " · 3 минуты");
    }

    /** Нанять управляющего или охрану (из телефона). */
    public static void hire(ServerPlayerEntity p, int i, String who) {
        LifeData d = Life.get(p);
        if (i < 0 || i >= Heists.businessCount() || !d.flags.contains("biz:" + i)) return;
        String flag = (who.equals("mgr") ? "bizmgr:" : "bizgrd:") + i;
        if (d.flags.contains(flag)) return;
        int cost = Heists.businessPrice(i) * (who.equals("mgr") ? 20 : 15) / 100;
        if (!Life.pay(p, cost, (who.equals("mgr") ? "управляющий: " : "охрана: ") + Heists.businessName(i))) return;
        d.flags.add(flag);
        Activities.title(p, "ИМПЕРИЯ", Heists.businessName(i) + (who.equals("mgr") ? ": управляющий +15%" : ": охрана, рейды реже"));
        Life.sync(p);
        send(p);
    }

    /** Состояние «Империи» клиенту. */
    public static void send(ServerPlayerEntity p) {
        LifeData d = Life.get(p);
        NbtCompound n = new NbtCompound();
        NbtList biz = Heists.businessState(p);
        int daily = 0;
        for (int i = 0; i < biz.size(); i++) {
            NbtCompound c = biz.getCompound(i);
            c.putBoolean("mgr", d.flags.contains("bizmgr:" + i));
            c.putBoolean("grd", d.flags.contains("bizgrd:" + i));
            c.putInt("mgrCost", Heists.businessPrice(i) * 20 / 100);
            c.putInt("grdCost", Heists.businessPrice(i) * 15 / 100);
            c.putBoolean("raid", raidAt(p, i));
            if (c.getBoolean("own")) daily += c.getInt("income");
        }
        n.put("biz", biz);
        n.putInt("daily", daily);
        n.putInt("turf", Territories.ownedCount(p.getServer()));
        n.putInt("turfAll", Territories.TURF.length);
        n.putInt("homes", com.yompix.novograd.world.Housing.homes(p).size());
        n.putInt("money", d.money);
        n.putInt("dirty", d.dirty);
        boolean[] ok = goalState(p, d);
        NbtList g = new NbtList();
        for (int i = 0; i < GOALS.length; i++) {
            NbtCompound c = new NbtCompound();
            c.putString("name", GOALS[i][1]);
            c.putInt("prize", Integer.parseInt(GOALS[i][2]));
            c.putBoolean("done", d.flags.contains("goal:" + GOALS[i][0]) || ok[i]);
            g.add(c);
        }
        n.put("goals", g);
        LifeNet.send(p, "empire_state", n);
    }
}
