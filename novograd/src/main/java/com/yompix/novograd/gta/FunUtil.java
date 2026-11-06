package com.yompix.novograd.gta;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.life.LifeData;
import com.yompix.novograd.life.LifeNet;
import com.yompix.novograd.life.Names;
import com.yompix.novograd.story.Story;
import com.yompix.novograd.world.Island;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Общие помощники для уличных событий, подработок, войны за районы, парашюта и автоматов. */
public final class FunUtil {
    private FunUtil() {}

    // ------------------------------------------------------------------ числа во флагах игрока («ключ=число»)
    public static int getInt(LifeData d, String key) {
        String pre = key + "=";
        for (String f : d.flags) if (f.startsWith(pre)) {
            try {
                return Integer.parseInt(f.substring(pre.length()));
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    public static void setInt(LifeData d, String key, int v) {
        String pre = key + "=";
        d.flags.removeIf(f -> f.startsWith(pre));
        d.flags.add(pre + v);
    }

    public static long getLong(LifeData d, String key) {
        String pre = key + "=";
        for (String f : d.flags) if (f.startsWith(pre)) {
            try {
                return Long.parseLong(f.substring(pre.length()));
            } catch (NumberFormatException ignored) {
            }
        }
        return Long.MIN_VALUE;
    }

    public static void setLong(LifeData d, String key, long v) {
        String pre = key + "=";
        d.flags.removeIf(f -> f.startsWith(pre));
        d.flags.add(pre + v);
    }

    // ------------------------------------------------------------------ состояние игрока
    /** Свободная игра: нет активной миссии (сюжетной или побочной). */
    public static boolean freeRoam(ServerPlayerEntity p) {
        LifeData d = Life.get(p);
        if (!d.side.isEmpty()) return false;
        return !Story.active(p) || Story.idle(d) || d.storyDone;
    }

    /** Игрок уже чем-то занят: подработка, ограбление, событие, война банд, прыжок — у радара одна голубая метка. */
    public static boolean busy(ServerPlayerEntity p) {
        return Activities.busy(p) || Heists.busy(p) || StreetEvents.busy(p) || VehicleJobs.busy(p) || Territories.busy(p) || Skydive.busy(p);
    }

    // ------------------------------------------------------------------ интерфейс
    public static void title(ServerPlayerEntity p, String title, String sub) {
        Activities.title(p, title, sub);
    }

    public static void hint(ServerPlayerEntity p, String s, Formatting f) {
        p.sendMessage(Text.literal(s).formatted(f), true);
    }

    /** Голубая метка на радаре (null — убрать). */
    public static void target(ServerPlayerEntity p, @Nullable Vec3d at, String label) {
        NbtCompound n = new NbtCompound();
        if (at != null) {
            n.putDouble("x", at.x);
            n.putDouble("z", at.z);
            n.putString("label", label);
        }
        LifeNet.send(p, "act_target", n);
    }

    /** Таймер в углу радара (−1 — убрать). */
    public static void timer(ServerPlayerEntity p, int sec) {
        NbtCompound n = new NbtCompound();
        n.putInt("sec", sec);
        LifeNet.send(p, "act_timer", n);
    }

    public static void clearHud(ServerPlayerEntity p) {
        target(p, null, "");
        timer(p, -1);
    }

    // ------------------------------------------------------------------ улицы
    public static int snap(double v) {
        return (int) (Math.round((v - 24) / 48.0) * 48 + 24);
    }

    /** Точка на тротуаре у ближайшего квартала. */
    public static Vec3d curb(ServerWorld w, double x, double z) {
        int sx = snap(x) + 5, sz = snap(z) + 16;
        return new Vec3d(sx + 0.5, Story.topY(w, sx, sz), sz + 0.5);
    }

    /** Тротуар в городе на расстоянии min..max от игрока (или null). */
    @Nullable
    public static Vec3d curbAround(ServerPlayerEntity p, int min, int max) {
        var r = p.getRandom();
        for (int i = 0; i < 40; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = min + r.nextInt(Math.max(1, max - min));
            int x = (int) (p.getX() + Math.cos(a) * d), z = (int) (p.getZ() + Math.sin(a) * d);
            if (Island.urban(Island.zone(x, z)) && Island.urban(Island.zone(snap(x) + 5, snap(z) + 16))) return curb(p.getServerWorld(), x, z);
        }
        return null;
    }

    /** Точка на ближайшей улице (полоса движения, у перекрёстка с отступом). */
    public static Vec3d road(ServerWorld w, double x, double z, boolean alongX) {
        int gx = snap(x), gz = snap(z);
        double px = alongX ? x : gx + 2.5, pz = alongX ? gz + 2.5 : z;
        return new Vec3d(px, Story.topY(w, (int) Math.floor(px), (int) Math.floor(pz)), pz);
    }

    /** Прямой маршрут по полосе на n кварталов вперёд от перекрёстка (gx, gz) по направлению dir (0 +x, 1 +z, 2 −x, 3 −z). */
    public static List<Vec3d> straight(ServerWorld w, int gx, int gz, int dir, int blocks) {
        int[][] d = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
        double ox = dir == 1 ? -2 : dir == 3 ? 2 : 0, oz = dir == 0 ? 2 : dir == 2 ? -2 : 0;
        List<Vec3d> l = new ArrayList<>();
        for (int i = 1; i <= blocks; i++) {
            int x = gx + d[dir][0] * 48 * i, z = gz + d[dir][1] * 48 * i;
            Island.Zone zn = Island.zone(x, z);
            if (!Island.urban(zn) && zn != Island.Zone.CORE) break;
            l.add(new Vec3d(x + 0.5 + ox, Story.topY(w, x, z), z + 0.5 + oz));
        }
        return l;
    }

    /** Петляющий маршрут по сетке улиц: от перекрёстка случайно поворачивает (для погонь). */
    public static List<Vec3d> wander(ServerWorld w, double fx, double fz, int steps, net.minecraft.util.math.random.Random r) {
        int x = snap(fx), z = snap(fz);
        int dir = r.nextInt(4);
        int[][] d = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
        List<Vec3d> l = new ArrayList<>();
        for (int i = 0; i < steps; i++) {
            int nd = dir;
            int k = r.nextInt(10);
            if (k < 3) nd = (dir + 1) % 4;
            else if (k < 6) nd = (dir + 3) % 4;
            int nx = x + d[nd][0] * 48, nz = z + d[nd][1] * 48;
            Island.Zone zn = Island.zone(nx, nz);
            if (!Island.urban(zn) && zn != Island.Zone.CORE) {
                nd = (nd + 2) % 4;
                nx = x + d[nd][0] * 48;
                nz = z + d[nd][1] * 48;
            }
            dir = nd;
            x = nx;
            z = nz;
            double ox = dir == 1 ? -2 : dir == 3 ? 2 : 0, oz = dir == 0 ? 2 : dir == 2 ? -2 : 0;
            l.add(new Vec3d(x + 0.5 + ox, Story.topY(w, x, z), z + 0.5 + oz));
        }
        return l;
    }

    // ------------------------------------------------------------------ существа
    public static CitizenEntity citizen(ServerWorld w, Vec3d at, CitizenEntity.Role role, @Nullable String name) {
        CitizenEntity c = new CitizenEntity(ModContent.CITIZEN, w);
        var r = w.getRandom();
        int skin = role == CitizenEntity.Role.GANG ? CitizenEntity.RANDOM_FROM + 1 + 2 * r.nextInt(CitizenEntity.RANDOM_COUNT / 2) : CitizenEntity.randomSkin(r);
        c.setup(skin, role, false, name != null ? name : Names.random(r, CitizenEntity.isSlim(skin)));
        c.refreshPositionAndAngles(at.x, at.y, at.z, r.nextFloat() * 360, 0);
        w.spawnEntity(c);
        return c;
    }

    public static CitizenEntity gangster(ServerWorld w, Vec3d at, String name, boolean gun) {
        CitizenEntity g = citizen(w, at, CitizenEntity.Role.GANG, name);
        g.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, new ItemStack(gun ? ModContent.PISTOL : ModContent.BAT));
        g.setCustomNameVisible(true);
        g.removeWhenIdle();
        return g;
    }

    public static CarEntity car(ServerWorld w, Vec3d at, float yaw, int variant) {
        w.getChunk((int) Math.floor(at.x) >> 4, (int) Math.floor(at.z) >> 4);
        CarEntity car = new CarEntity(ModContent.CAR, w);
        car.refreshPositionAndAngles(at.x, at.y, at.z, yaw, 0);
        car.setVariant(variant);
        w.spawnEntity(car);
        return car;
    }

    public static float yawTo(Vec3d from, Vec3d to) {
        return (float) Math.toDegrees(Math.atan2(-(to.x - from.x), to.z - from.z));
    }

    public static void discard(ServerWorld w, @Nullable java.util.UUID id) {
        if (id == null) return;
        var e = w.getEntity(id);
        if (e == null) return;
        for (var ps : new ArrayList<>(e.getPassengerList())) if (!(ps instanceof ServerPlayerEntity)) ps.discard();
        if (e instanceof CitizenEntity c) c.removeLater(60);
        else e.discard();
    }

    /** Висящий в воздухе предмет-метка с подписью (как бизнесы и убежища): находим или создаём. */
    public static ItemEntity marker(ServerWorld w, Vec3d at, String tag, ItemStack stack, Text name, boolean glow) {
        List<ItemEntity> have = w.getEntitiesByClass(ItemEntity.class, new Box(at.x - 3, at.y - 4, at.z - 3, at.x + 3, at.y + 4, at.z + 3),
                e -> e.getCommandTags().contains(tag));
        ItemEntity it;
        if (have.isEmpty()) {
            it = new ItemEntity(w, at.x, at.y + 0.3, at.z, stack);
            it.setVelocity(0, 0, 0);
            it.setPickupDelayInfinite();
            it.setNeverDespawn();
            it.addCommandTag(tag);
            it.setCustomNameVisible(true);
            it.setGlowing(glow);
            w.spawnEntity(it);
        } else {
            it = have.get(0);
            for (int i = 1; i < have.size(); i++) have.get(i).discard();
        }
        if (it.getCustomName() == null || !it.getCustomName().getString().equals(name.getString())) it.setCustomName(name);
        return it;
    }

    public static String pick(ServerWorld w, String... s) {
        return s[w.getRandom().nextInt(s.length)];
    }
}
