package com.yompix.novograd.gta;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.life.LifeData;
import com.yompix.novograd.life.LifeNet;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;


/**
 * 0.9, фирменная механика «Сеть» — город на проводе. Телефон → «Сеть»: рядом видны узлы городской сети, и каждый
 * можно взломать короткой мини-игрой (клиент, ~10 с):
 * <ul>
 *   <li><b>светофор</b> — на перекрёстке все сигналы красные (поток встаёт, погоне не проехать), проезд на красный не штрафуют;</li>
 *   <li><b>камера скорости</b> — ослепла на игровые сутки;</li>
 *   <li><b>щиток района</b> — блэкаут вокруг (world/CityEvents): темно, светофоры не работают;</li>
 *   <li><b>банкомат</b> (в 6 блоках) — выдаёт наличные (грязные), раз в сутки с каждого;</li>
 *   <li><b>камеры ограбления</b> — во время большого дела охрана слепнет.</li>
 * </ul>
 * Уровень «Сети» 1–5 растёт от взломов (каждые 5): дольше эффект, шире окно мини-игры, больше денег. Провал — «шум»:
 * звезда розыска, если полиция рядом. Всё — флаги LifeData ({@code grid_xp=N}, {@code atm_day:x,y,z=день}).
 */
public final class Grid {
    private Grid() {}

    public static int xp(LifeData d) {
        return (int) d.flags.stream().filter(f -> f.startsWith("grid_xp=")).mapToLong(f -> Long.parseLong(f.substring(8))).max().orElse(0);
    }

    public static int level(LifeData d) {
        return Math.min(5, 1 + xp(d) / 5);
    }

    private static void addXp(LifeData d) {
        int x = xp(d) + 1;
        d.flags.removeIf(f -> f.startsWith("grid_xp="));
        d.flags.add("grid_xp=" + x);
    }

    /** Узлы рядом с игроком → клиенту («grid_nodes»). */
    public static void scan(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        LifeData d = Life.get(p);
        NbtList l = new NbtList();
        int[] sig = TrafficLights.nearestSignal(w, p.getX(), p.getZ());
        if (sig != null && p.squaredDistanceTo(sig[0], p.getY(), sig[1]) < 40 * 40) l.add(node("lights", "Светофор", sig[0], sig[1], 2));
        int cam = Fines.nearestCam(p.getX(), p.getZ(), 90);
        if (cam >= 0) {
            double[] c = Fines.camPos(cam);
            l.add(node("speedcam:" + cam, "Камера скорости: " + Fines.camName(cam), c[0], c[1], 2));
        }
        BlockPos atm = nearAtm(w, p.getBlockPos());
        if (atm != null) l.add(node("atm:" + atm.getX() + "," + atm.getY() + "," + atm.getZ(), "Банкомат", atm.getX(), atm.getZ(),
                atmUsed(w, d, atm) ? 0 : 3));
        l.add(node("district", "Щиток района", p.getX(), p.getZ(), 4));
        if (BigHeists.active(p)) l.add(node("heistcams", "Камеры охраны", p.getX(), p.getZ(), 3));
        NbtCompound n = new NbtCompound();
        n.put("nodes", l);
        n.putInt("level", level(d));
        n.putInt("xp", xp(d));
        LifeNet.send(p, "grid_nodes", n);
    }

    private static NbtCompound node(String id, String name, double x, double z, int diff) {
        NbtCompound c = new NbtCompound();
        c.putString("id", id);
        c.putString("name", name);
        c.putDouble("x", x);
        c.putDouble("z", z);
        c.putInt("diff", diff); // 0 — недоступен
        return c;
    }

    private static BlockPos nearAtm(ServerWorld w, BlockPos at) {
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int dx = -6; dx <= 6; dx++)
            for (int dy = -2; dy <= 3; dy++)
                for (int dz = -6; dz <= 6; dz++) {
                    m.set(at.getX() + dx, at.getY() + dy, at.getZ() + dz);
                    if (w.getBlockState(m).isOf(ModContent.ATM)) return m.toImmutable();
                }
        return null;
    }

    private static long day(ServerWorld w) {
        return w.getTimeOfDay() / 24000;
    }

    private static boolean atmUsed(ServerWorld w, LifeData d, BlockPos b) {
        return d.flags.contains("atm_day:" + b.getX() + "," + b.getY() + "," + b.getZ() + "=" + day(w));
    }

    /** Мини-игра пройдена: применить взлом узла. */
    public static void hack(ServerPlayerEntity p, String id) {
        ServerWorld w = p.getServerWorld();
        LifeData d = Life.get(p);
        int lvl = level(d);
        long now = w.getTime();
        String done;
        if (id.equals("lights")) {
            int[] sig = TrafficLights.nearestSignal(w, p.getX(), p.getZ());
            if (sig == null) return;
            TrafficLights.hack(sig[0], sig[1], now + 20L * (20 + 10 * lvl));
            done = "Светофоры перекрёстка — красные " + (20 + 10 * lvl) + " с";
        } else if (id.startsWith("speedcam:")) {
            Fines.blind(Integer.parseInt(id.substring(9)), now + 24000);
            done = "Камера скорости ослепла до завтра";
        } else if (id.startsWith("atm:")) {
            String[] c = id.substring(4).split(",");
            BlockPos b = new BlockPos(Integer.parseInt(c[0]), Integer.parseInt(c[1]), Integer.parseInt(c[2]));
            if (!w.getBlockState(b).isOf(ModContent.ATM) || p.squaredDistanceTo(b.toCenterPos()) > 8 * 8 || atmUsed(w, d, b)) return;
            d.flags.removeIf(f -> f.startsWith("atm_day:" + c[0] + "," + c[1] + "," + c[2] + "="));
            d.flags.add("atm_day:" + c[0] + "," + c[1] + "," + c[2] + "=" + day(w));
            int cash = 250 * lvl + p.getRandom().nextInt(300);
            Economy.dirty(p, cash, "банкомат («Сеть»)");
            done = "Банкомат выдал $" + cash;
        } else if (id.equals("district")) {
            int t = 20 * (60 + 30 * lvl);
            com.yompix.novograd.world.CityEvents.blackout(w, p.getX(), p.getZ(), 60 + 15 * lvl, t);
            done = "Район без света " + t / 20 + " с";
        } else if (id.equals("heistcams")) {
            if (!BigHeists.hackCams(p, 20 * (40 + 20 * lvl))) return;
            done = "Камеры охраны слепые " + (40 + 20 * lvl) + " с";
        } else return;
        int was = level(d);
        addXp(d);
        Activities.title(p, "СЕТЬ", done);
        if (level(d) > was) Life.message(p, "Сеть", "Уровень " + level(d) + ": дольше, тише, больше.");
        if (level(d) >= 5) com.yompix.novograd.life.Achievements.give(p, "grid_master");
        com.yompix.novograd.life.Achievements.give(p, "grid_first");
        Life.sync(p);
    }

    /** Мини-игра провалена: шум в сети — если полиция рядом, звезда. */
    public static void fail(ServerPlayerEntity p) {
        if (Wanted.policeSees(p.getServerWorld(), p, 60)) Wanted.add(p, 1, "взлом городской сети", 1);
        else p.sendMessage(net.minecraft.text.Text.literal("Сеть: сбой — попробуй ещё раз").formatted(net.minecraft.util.Formatting.GRAY), true);
    }
}
