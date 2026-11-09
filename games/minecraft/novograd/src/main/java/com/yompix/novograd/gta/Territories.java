package com.yompix.novograd.gta;

import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.life.Achievements;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.life.LifeData;
import com.yompix.novograd.life.LifeNet;
import com.yompix.novograd.life.Names;
import com.yompix.novograd.story.Story;
import com.yompix.novograd.world.Island;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.PersistentState;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Война банд за районы (с акта III — когда ты уже свой в криминальном мире):
 *  • остров поделен на районы «Чёрных Ящеров», «Синих» и твоей банды — цветные зоны на карте (M);
 *  • зайди в чужой район и убей их бойца — начнётся война: отбей три волны, и район твой;
 *  • свои районы каждый день приносят дань (грязные наличные) и реже устраивают засады (StreetEvents);
 *  • соперники иногда пытаются отбить район — приезжай защищать, иначе потеряешь.
 */
public final class Territories {
    private Territories() {}

    public static final int LIZARDS = 0, BLUES = 1, MINE = 2;
    public static final String[] GANGS = {"Чёрные Ящеры", "Синие", "Твоя банда"};
    private static final String[] MEMBER = {"Ящер", "Синий", "Свой"};

    /** Название, x0, z0, x1, z1, хозяин по умолчанию. */
    public static final Object[][] TURF = {
            {"Промзона-Север", 156, 156, 428, 428, LIZARDS},
            {"Склады Ящеров", 428, 156, 700, 428, LIZARDS},
            {"Свалка", 156, 428, 428, 700, LIZARDS},
            {"Логово Ящеров", 428, 428, 700, 700, LIZARDS},
            {"Нижний город", 156, -428, 428, -156, LIZARDS},
            {"Порт", -800, -420, -560, 0, BLUES},
            {"Доки", -800, 0, -560, 420, BLUES},
            {"Пригород-Запад", -560, 156, -156, 560, BLUES},
            {"Пригород-Север", -560, -560, -156, -156, BLUES},
            {"Задворки Ривьеры", -1560, 100, -1320, 420, BLUES},
    };

    public static final int ACT_NEEDED = 2;

    // ------------------------------------------------------------------ сохранение (общее для мира — играть с друзьями)
    public static final class State extends PersistentState {
        final int[] owner = new int[TURF.length];

        static final Type<State> TYPE = new Type<>(State::new, (n, reg) -> load(n), null);

        State() {
            for (int i = 0; i < TURF.length; i++) owner[i] = (int) TURF[i][5];
        }

        static State load(NbtCompound n) {
            State s = new State();
            int[] o = n.getIntArray("owner");
            for (int i = 0; i < Math.min(o.length, s.owner.length); i++) s.owner[i] = o[i];
            return s;
        }

        @Override
        public NbtCompound writeNbt(NbtCompound n, net.minecraft.registry.RegistryWrapper.WrapperLookup reg) {
            n.putIntArray("owner", owner);
            return n;
        }
    }

    public static State state(MinecraftServer s) {
        return s.getOverworld().getPersistentStateManager().getOrCreate(State.TYPE, "novograd_turf");
    }

    public static int ownedCount(MinecraftServer s) {
        int n = 0;
        for (int o : state(s).owner) if (o == MINE) n++;
        return n;
    }

    /** Район по координатам (−1 — ничей). */
    public static int at(double x, double z) {
        for (int i = 0; i < TURF.length; i++) {
            Object[] t = TURF[i];
            if (x >= (int) t[1] && x < (int) t[3] && z >= (int) t[2] && z < (int) t[4]) return i;
        }
        return -1;
    }

    private static Vec3d center(ServerWorld w, int i) {
        Object[] t = TURF[i];
        int x = FunUtil.snap(((int) t[1] + (int) t[3]) / 2.0) + 5, z = FunUtil.snap(((int) t[2] + (int) t[4]) / 2.0) + 16;
        return new Vec3d(x + 0.5, Story.topY(w, x, z), z + 0.5);
    }

    /** Банда, что охотится за игроком (для засад): та, чей район ближе. */
    public static String rivalName(ServerPlayerEntity p) {
        int i = at(p.getX(), p.getZ());
        State s = state(p.getServer());
        if (i >= 0 && s.owner[i] != MINE) return MEMBER[s.owner[i]].equals("Ящер") ? "Ящеры" : "Синие";
        return p.getX() < -150 ? "Синие" : "Ящеры";
    }

    // ------------------------------------------------------------------ война
    private static final class War {
        int turf, wave, timer, attacker, outside;
        boolean defense, arrived;
        final List<UUID> gang = new ArrayList<>();
    }

    private static final Map<UUID, War> WARS = new HashMap<>();
    private static final Map<UUID, Integer> WHERE = new HashMap<>();

    public static boolean busy(ServerPlayerEntity p) {
        return WARS.containsKey(p.getUuid());
    }

    private static int waveSize(int wave, MinecraftServer s) {
        int lvl = ownedCount(s) / 3;
        return new int[]{3, 5, 7}[Math.min(2, wave)] + lvl;
    }

    /** Боец банды убит игроком: в чужом районе это объявление войны. */
    public static void onGangKill(ServerPlayerEntity p, CitizenEntity victim) {
        War war = WARS.get(p.getUuid());
        if (war != null) return;
        int i = at(victim.getX(), victim.getZ());
        if (i < 0) return;
        State s = state(p.getServer());
        if (s.owner[i] == MINE || !FunUtil.freeRoam(p) || FunUtil.busy(p)) return;
        LifeData d = Life.get(p);
        if (Economy.act(d) < ACT_NEEDED) {
            if (d.flags.add("turf_early")) FunUtil.hint(p, "Войну за районы начнёшь, когда станешь своим в криминальном мире (акт III)", Formatting.GRAY);
            return;
        }
        startWar(p, i, s.owner[i], false);
    }

    private static void startWar(ServerPlayerEntity p, int i, int enemy, boolean defense) {
        War war = new War();
        war.turf = i;
        war.attacker = enemy;
        war.defense = defense;
        war.timer = defense ? 20 * 180 : 20 * 100;
        war.arrived = !defense;
        WARS.put(p.getUuid(), war);
        if (!defense) {
            FunUtil.title(p, "ВОЙНА ЗА РАЙОН", "«" + TURF[i][0] + "» · отбейся от трёх волн «" + GANGS[enemy] + "»");
            Economy.shock(p.getServer(), "CITY", -0.02, "перестрелки на улицах пугают вкладчиков");
            spawnWave(p, war);
        }
        broadcast(p.getServer());
    }

    private static void spawnWave(ServerPlayerEntity p, War war) {
        ServerWorld w = p.getServerWorld();
        var r = w.getRandom();
        int n = waveSize(war.wave, p.getServer());
        Object[] t = TURF[war.turf];
        for (int k = 0; k < n; k++) {
            Vec3d at = null;
            for (int tries = 0; tries < 12 && at == null; tries++) {
                double a = r.nextDouble() * Math.PI * 2, d = 18 + r.nextInt(14);
                int x = (int) (p.getX() + Math.cos(a) * d), z = (int) (p.getZ() + Math.sin(a) * d);
                if (x < (int) t[1] - 20 || x > (int) t[3] + 20 || z < (int) t[2] - 20 || z > (int) t[4] + 20) continue;
                if (Island.zone(x, z) == Island.Zone.SEA) continue;
                int y = Story.topY(w, x, z);
                if (!w.isAir(new net.minecraft.util.math.BlockPos(x, y, z)) || !w.isAir(new net.minecraft.util.math.BlockPos(x, y + 1, z))) continue;
                at = new Vec3d(x + 0.5, y, z + 0.5);
            }
            if (at == null) at = p.getPos().add(r.nextInt(20) - 10, 0, 18);
            CitizenEntity g = FunUtil.gangster(w, at, MEMBER[war.attacker] + " " + Names.random(r, false).split(" ")[0], war.wave > 0 || r.nextInt(3) > 0);
            if (war.wave == 2 && k == 0) {
                g.setCustomName(net.minecraft.text.Text.literal("Главарь «" + GANGS[war.attacker] + "»"));
                g.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, new net.minecraft.item.ItemStack(com.yompix.novograd.ModContent.SMG));
                g.equipStack(net.minecraft.entity.EquipmentSlot.CHEST, new net.minecraft.item.ItemStack(net.minecraft.item.Items.IRON_CHESTPLATE));
            }
            g.attack(p);
            war.gang.add(g.getUuid());
        }
        w.playSound(null, p.getBlockPos(), SoundEvents.EVENT_RAID_HORN.value(), SoundCategory.HOSTILE, 0.6f, 1.2f);
        FunUtil.hint(p, "Волна " + (war.wave + 1) + "/3: " + n + " бойцов «" + GANGS[war.attacker] + "»", Formatting.RED);
    }

    private static void tickWar(ServerPlayerEntity p, War war) {
        ServerWorld w = p.getServerWorld();
        State s = state(p.getServer());
        Object[] t = TURF[war.turf];
        war.timer -= 10;
        Vec3d c = center(w, war.turf);
        boolean inside = at(p.getX(), p.getZ()) == war.turf;
        if (war.defense && !war.arrived) { // защита: успей приехать
            FunUtil.target(p, c, "Защити: " + t[0]);
            FunUtil.timer(p, war.timer / 20);
            if (inside) {
                war.arrived = true;
                war.timer = 20 * 120;
                FunUtil.title(p, "ОТБЕЙ РАЙОН", "«" + t[0] + "» · «" + GANGS[war.attacker] + "» уже здесь");
                war.wave = 2;
                spawnWave(p, war);
            } else if (war.timer <= 0) lose(p, war, "Ты не успел — «" + GANGS[war.attacker] + "» забрали район");
            return;
        }
        int alive = 0;
        CitizenEntity any = null;
        for (UUID u : war.gang) if (w.getEntity(u) instanceof CitizenEntity g && g.isAlive()) {
            alive++;
            any = g;
            if (!g.chasing(p)) g.attack(p);
        }
        FunUtil.target(p, any != null ? any.getPos() : c, "Банда (" + alive + ")");
        FunUtil.timer(p, war.timer / 20);
        war.outside = inside ? 0 : war.outside + 10;
        if (war.outside > 20 * 15) {
            lose(p, war, "Ты покинул район — война проиграна");
            return;
        }
        if (war.outside == 20) FunUtil.hint(p, "Вернись в район «" + t[0] + "», иначе война проиграна!", Formatting.RED);
        if (alive == 0) {
            war.gang.clear();
            if (war.wave >= 2) {
                win(p, war, s);
                return;
            }
            war.wave++;
            war.timer = 20 * 100;
            spawnWave(p, war);
        } else if (war.timer <= 0) lose(p, war, "Время вышло — банда удержала район");
    }

    private static void win(ServerPlayerEntity p, War war, State s) {
        WARS.remove(p.getUuid());
        int i = war.turf;
        s.owner[i] = MINE;
        s.markDirty();
        FunUtil.clearHud(p);
        LifeData d = Life.get(p);
        int loot = 600 + 300 * ownedCount(p.getServer());
        Economy.dirty(p, loot, war.defense ? "район удержан" : "касса банды");
        FunUtil.title(p, war.defense ? "РАЙОН УДЕРЖАН" : "РАЙОН ЗАХВАЧЕН", "«" + TURF[i][0] + "» — твой · каждый день дань $" + tribute() + " · +$" + loot);
        Achievements.give(p, "turf1");
        if (ownedCount(p.getServer()) >= TURF.length) Achievements.give(p, "turf_all");
        Island.Zone zn = Island.zone(((int) TURF[i][1] + (int) TURF[i][3]) / 2, ((int) TURF[i][2] + (int) TURF[i][4]) / 2);
        if (!war.defense) {
            if (zn == Island.Zone.PORT) Economy.shock(p.getServer(), "PORT", 0.05, "в порту больше не воруют — там новая власть");
            else if (zn == Island.Zone.RIVIERA) Economy.shock(p.getServer(), "RIVH", -0.03, "бандитские разборки отпугивают туристов");
            else if (zn == Island.Zone.INDUSTRIAL) Economy.shock(p.getServer(), "NITR", 0.02, "промзона под новой «крышей»");
        }
        if (d.flags.add("turf_first")) Life.message(p, "Свои", "Район наш! Пацаны будут собирать дань каждый день — грязными, отмывай через бизнесы. И меньше будут лезть на тебя с засадами.");
        p.getServerWorld().playSound(null, p.getBlockPos(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.8f, 1f);
        broadcast(p.getServer());
    }

    private static void lose(ServerPlayerEntity p, War war, String why) {
        WARS.remove(p.getUuid());
        FunUtil.clearHud(p);
        ServerWorld w = p.getServerWorld();
        for (UUID u : war.gang) if (w.getEntity(u) instanceof CitizenEntity g) g.removeLater(300);
        if (war.defense) {
            State s = state(p.getServer());
            s.owner[war.turf] = war.attacker;
            s.markDirty();
        }
        FunUtil.title(p, war.defense ? "РАЙОН ПОТЕРЯН" : "ВОЙНА ПРОИГРАНА", why);
        broadcast(p.getServer());
    }

    public static void onDownfall(ServerPlayerEntity p) {
        War war = WARS.get(p.getUuid());
        if (war != null) lose(p, war, war.defense ? "Без тебя пацаны не удержали район" : "Банда удержала район");
    }

    public static void onLeave(ServerPlayerEntity p) {
        War war = WARS.remove(p.getUuid());
        if (war != null) for (UUID u : war.gang) if (p.getServerWorld().getEntity(u) instanceof CitizenEntity g) g.discard();
        WHERE.remove(p.getUuid());
    }

    public static int tribute() {
        return 250;
    }

    // ------------------------------------------------------------------ тик: подсказки, бойцы в районах, дань, набеги
    public static void tick(MinecraftServer server) {
        long t = server.getTicks();
        if (t % 10 != 6) return;
        State s = state(server);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p.isSpectator() || p.isDead()) continue;
            War war = WARS.get(p.getUuid());
            if (war != null) {
                tickWar(p, war);
                continue;
            }
            LifeData d = Life.get(p);
            int i = at(p.getX(), p.getZ());
            Integer was = WHERE.put(p.getUuid(), i);
            if (i >= 0 && (was == null || was != i)) {
                int o = s.owner[i];
                String sub = o == MINE ? "твой район · дань $" + tribute() + "/день"
                        : Economy.act(d) >= ACT_NEEDED ? "район «" + GANGS[o] + "» · убей их бойца — начнётся война" : "район «" + GANGS[o] + "»";
                p.sendMessage(net.minecraft.text.Text.literal("⚑ " + TURF[i][0] + " — " + sub).formatted(o == MINE ? Formatting.GREEN : o == LIZARDS ? Formatting.RED : Formatting.BLUE), true);
            }
            if (t % 100 == 6 && i >= 0) populate(p, i, s.owner[i], d);
            if (t % 200 == 6) daily(p, d, s);
        }
        if (t % 600 == 6) broadcast(server);
    }

    /** Бойцы банды на улицах района (свои — просто тусуются). */
    private static void populate(ServerPlayerEntity p, int i, int owner, LifeData d) {
        if (!FunUtil.freeRoam(p) || Economy.act(d) < 1 || Economy.act(d) < ACT_NEEDED && owner == MINE) return;
        ServerWorld w = p.getServerWorld();
        String pre = MEMBER[owner] + " ";
        long have = w.getEntitiesByClass(CitizenEntity.class, p.getBoundingBox().expand(48, 16, 48),
                c -> c.isAlive() && c.hasCustomName() && c.getCustomName().getString().startsWith(pre)).size();
        if (have >= (owner == MINE ? 3 : 4)) return;
        Vec3d at = FunUtil.curbAround(p, 28, 46);
        if (at == null || at(at.x, at.z) != i) return;
        var r = w.getRandom();
        CitizenEntity c;
        if (owner == MINE) {
            c = FunUtil.citizen(w, at, CitizenEntity.Role.CITIZEN, pre + Names.random(r, false).split(" ")[0]);
            c.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, new net.minecraft.item.ItemStack(com.yompix.novograd.ModContent.BAT));
            c.setCustomNameVisible(true);
            c.setAmbient(true);
        } else {
            c = FunUtil.gangster(w, at, pre + Names.random(r, false).split(" ")[0], r.nextBoolean());
            c.setAmbient(true);
        }
        c.setStationary(true);
        c.lockActivity(r.nextBoolean() ? CitizenEntity.Activity.TALK : CitizenEntity.Activity.PHONE);
    }

    /** Раз в игровой день: дань со своих районов и, может быть, набег соперников. */
    private static void daily(ServerPlayerEntity p, LifeData d, State s) {
        long day = p.getServerWorld().getTimeOfDay() / 24000;
        long last = FunUtil.getLong(d, "turf_day");
        if (last == Long.MIN_VALUE) {
            FunUtil.setLong(d, "turf_day", day);
            return;
        }
        if (day <= last) return;
        FunUtil.setLong(d, "turf_day", day);
        int own = 0;
        List<Integer> mine = new ArrayList<>();
        for (int i = 0; i < TURF.length; i++) if (s.owner[i] == MINE) { own++; mine.add(i); }
        if (own == 0) {
            if (Economy.act(d) >= ACT_NEEDED && d.flags.add("tips_turf"))
                Life.message(p, "Свои", "Ящеры и Синие делят город. Посмотри карту (M): цветные зоны — их районы. Зайди в чужой район и убей их бойца — начнётся война. Отобьёшься — район твой, и он платит дань.");
            return;
        }
        int pay = own * tribute();
        Economy.dirty(p, pay, "дань с районов (" + own + ")");
        Life.message(p, "Свои", "Собрали дань с районов: $" + pay + " наличными. Районов у нас: " + own + "/" + TURF.length + ".");
        // набег соперников
        if (FunUtil.freeRoam(p) && !FunUtil.busy(p) && p.getRandom().nextInt(100) < 25 + own * 3) {
            int i = mine.get(p.getRandom().nextInt(mine.size()));
            int enemy = (int) TURF[i][5] == MINE ? LIZARDS : (int) TURF[i][5];
            Life.message(p, "Свои", "«" + GANGS[enemy] + "» наехали на «" + TURF[i][0] + "»! Приезжай быстрее, без тебя не отобьёмся!");
            startWar(p, i, enemy, true);
        }
    }

    // ------------------------------------------------------------------ карта
    public static void broadcast(MinecraftServer s) {
        NbtCompound n = stateNbt(s);
        for (ServerPlayerEntity p : s.getPlayerManager().getPlayerList()) LifeNet.send(p, "turf", n);
    }

    public static void send(ServerPlayerEntity p) {
        LifeNet.send(p, "turf", stateNbt(p.getServer()));
    }

    private static NbtCompound stateNbt(MinecraftServer s) {
        State st = state(s);
        Set<Integer> fighting = new HashSet<>();
        for (War w : WARS.values()) fighting.add(w.turf);
        NbtList l = new NbtList();
        for (int i = 0; i < TURF.length; i++) {
            NbtCompound c = new NbtCompound();
            c.putString("name", (String) TURF[i][0]);
            c.putIntArray("box", new int[]{(int) TURF[i][1], (int) TURF[i][2], (int) TURF[i][3], (int) TURF[i][4]});
            c.putInt("owner", st.owner[i]);
            c.putBoolean("war", fighting.contains(i));
            l.add(c);
        }
        NbtCompound n = new NbtCompound();
        n.put("list", l);
        return n;
    }

    /** Для теста: начать войну в районе, где стоит игрок. */
    public static boolean debugWar(ServerPlayerEntity p) {
        int i = at(p.getX(), p.getZ());
        if (i < 0) return false;
        State s = state(p.getServer());
        if (s.owner[i] == MINE) s.owner[i] = (int) TURF[i][5] == MINE ? LIZARDS : (int) TURF[i][5];
        startWar(p, i, s.owner[i], false);
        return true;
    }

    @Nullable
    public static String turfName(int i) {
        return i < 0 || i >= TURF.length ? null : (String) TURF[i][0];
    }
}
