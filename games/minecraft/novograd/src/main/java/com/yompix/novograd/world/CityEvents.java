package com.yompix.novograd.world;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.life.LifeData;
import com.yompix.novograd.life.LifeNet;
import com.yompix.novograd.story.Story;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 0.9: город-событие — то, что случается со всем городом сразу и видно всем игрокам.
 * <ul>
 *   <li><b>Блэкаут района</b> — гаснет свет (клиент: блочный свет района почти выключен), светофоры мигают аварийным
 *       и не регулируют, потом приезжают ремонтники. Его же устраивает игрок через «Сеть» (щиток района).</li>
 *   <li><b>Шторм</b> — гроза с молниями вокруг игроков (не по самим игрокам), ветер и ливень.</li>
 *   <li><b>«Ночь музыки»</b> — ночью на площади в парке: диджей и танцующая толпа; пришёл — событие засчитано.</li>
 * </ul>
 * Раз в 30–45 минут игры одно случайное событие (не во время кат-сцены). Отладка: {@code /event blackout|storm|festival|stop}.
 * Участие (был рядом) — флаги {@code evt:<id>} в LifeData (прогресс 100 %).
 */
public final class CityEvents {
    private CityEvents() {}

    public static final String[] IDS = {"blackout", "storm", "festival"};
    public static final String[] NAMES = {"Блэкаут", "Шторм", "Ночь музыки"};

    /** Активное событие: тип, центр, радиус, конец (время мира). */
    private static String active = "";
    private static double cx, cz, radius;
    private static long until, next = -1;
    private static String district = "";
    private static final List<UUID> SPAWNED = new ArrayList<>();
    /** Районы без света: {x, z, r, до}. Блэкаут-событие и взломы «Сети». */
    private static final List<double[]> DARK = new ArrayList<>();

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(CityEvents::tick);
        CommandRegistrationCallback.EVENT.register((d, reg, env) -> d.register(net.minecraft.server.command.CommandManager.literal("event")
                .requires(s -> s.hasPermissionLevel(2))
                .then(net.minecraft.server.command.CommandManager.argument("id", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(c -> {
                    var src = c.getSource();
                    String id = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "id");
                    if (id.equals("stop")) stop(src.getServer());
                    else start(src.getServer(), id, src.getPlayer());
                    return 1;
                }))));
    }

    @Nullable
    public static String active() {
        return active.isEmpty() ? null : active;
    }

    public static double[] center() {
        return new double[]{cx, cz, radius};
    }

    /** Перекрёсток (gx, gz) в районе без света в момент time. */
    public static boolean dark(int gx, int gz, long time) {
        for (int i = DARK.size() - 1; i >= 0; i--) {
            double[] d = DARK.get(i);
            if (time >= d[3]) continue;
            if ((gx - d[0]) * (gx - d[0]) + (gz - d[1]) * (gz - d[1]) < d[2] * d[2]) return true;
        }
        return false;
    }

    /** Выключить свет в районе (событие или «Сеть»): всем игрокам мира — зона темноты. */
    public static void blackout(ServerWorld w, double x, double z, double r, int ticks) {
        long end = w.getTime() + ticks;
        DARK.removeIf(d -> w.getTime() >= d[3]);
        DARK.add(new double[]{x, z, r, end});
        NbtCompound n = new NbtCompound();
        n.putDouble("x", x);
        n.putDouble("z", z);
        n.putDouble("r", r);
        n.putInt("ticks", ticks);
        for (ServerPlayerEntity p : w.getPlayers()) LifeNet.send(p, "blackout", n.copy());
    }

    private static void tick(MinecraftServer s) {
        ServerWorld w = s.getOverworld();
        long now = w.getTime();
        if (now % 20 != 0) return;
        if (next < 0) next = now + 20 * 60 * (30 + w.getRandom().nextInt(15));
        if (!active.isEmpty()) {
            for (ServerPlayerEntity p : w.getPlayers()) {
                if (p.squaredDistanceTo(cx, p.getY(), cz) < radius * radius) {
                    LifeData d = Life.get(p);
                    if (d.flags.add("evt:" + active)) Life.message(p, "Новоград-24", NAMES[idx(active)] + ": ты был там, где всё случилось.");
                }
            }
            if (active.equals("storm") && now % 60 == 0) strikes(w);
            if (now >= until) stop(s);
            return;
        }
        if (now >= next && !w.getPlayers().isEmpty()) {
            boolean night = w.getTimeOfDay() % 24000 > 13000 && w.getTimeOfDay() % 24000 < 23000;
            String id = night && w.getRandom().nextBoolean() ? "festival" : IDS[w.getRandom().nextInt(2)];
            ServerPlayerEntity any = w.getPlayers().get(0);
            if (!com.yompix.novograd.story.Story.active(any) || com.yompix.novograd.story.Story.idle(Life.get(any))) start(s, id, null);
            next = now + 20 * 60 * (30 + w.getRandom().nextInt(15));
        }
    }

    private static int idx(String id) {
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return i;
        return 0;
    }

    public static void start(MinecraftServer s, String id, @Nullable ServerPlayerEntity near) {
        stop(s);
        ServerWorld w = s.getOverworld();
        var r = w.getRandom();
        switch (id) {
            case "blackout" -> {
                // район — одна из подписей карты в городе (не горы и не пустошь)
                String[][] towns = {{"Деловой центр", "430", "-430"}, {"Порт", "-640", "0"}, {"Пригород", "-300", "300"}, {"РИВЬЕРА", "-1320", "0"},
                        {"Промзона", "430", "430"}};
                String[] t = towns[r.nextInt(towns.length)];
                if (near != null) t = new String[]{"район рядом", String.valueOf((int) near.getX()), String.valueOf((int) near.getZ())};
                district = t[0];
                cx = Integer.parseInt(t[1]);
                cz = Integer.parseInt(t[2]);
                radius = 150;
                until = w.getTime() + 20 * 60 * 3;
                blackout(w, cx, cz, radius, 20 * 60 * 3);
            }
            case "storm" -> {
                ServerPlayerEntity p = near != null ? near : w.getPlayers().isEmpty() ? null : w.getPlayers().get(0);
                cx = p == null ? 0 : p.getX();
                cz = p == null ? 0 : p.getZ();
                radius = 2048;
                district = "весь остров";
                until = w.getTime() + 20 * 60 * 4;
                w.setWeather(0, 20 * 60 * 4, true, true);
            }
            case "festival" -> {
                Vec3d at = Story.place(w, "park_center");
                if (at == null) return;
                cx = at.x;
                cz = at.z;
                radius = 40;
                district = "парк";
                until = w.getTime() + 20 * 60 * 4;
                festival(w, at);
            }
            default -> {
                return;
            }
        }
        active = id;
        String head = switch (id) {
            case "blackout" -> "Блэкаут: " + district + " без света";
            case "storm" -> "Штормовое предупреждение";
            default -> "«Ночь музыки» в парке";
        };
        for (ServerPlayerEntity p : w.getPlayers()) {
            com.yompix.novograd.gta.Activities.title(p, NAMES[idx(id)].toUpperCase(), head);
            NbtCompound n = new NbtCompound();
            n.putString("id", id);
            n.putDouble("x", cx);
            n.putDouble("z", cz);
            n.putDouble("r", radius);
            LifeNet.send(p, "city_event", n);
        }
    }

    public static void stop(MinecraftServer s) {
        if (active.isEmpty()) return;
        ServerWorld w = s.getOverworld();
        if (active.equals("storm")) w.setWeather(20 * 60 * 10, 0, false, false);
        for (UUID u : SPAWNED) if (w.getEntity(u) != null) w.getEntity(u).discard();
        SPAWNED.clear();
        active = "";
        for (ServerPlayerEntity p : w.getPlayers()) LifeNet.send(p, "city_event", new NbtCompound());
    }

    /** Гроза: молнии в 20–60 блоках от игроков (без урона по ним самим — «только визуально»). */
    private static void strikes(ServerWorld w) {
        var r = w.getRandom();
        for (ServerPlayerEntity p : w.getPlayers()) {
            if (r.nextInt(3) != 0) continue;
            double a = r.nextDouble() * Math.PI * 2, d = 20 + r.nextInt(40);
            int x = (int) (p.getX() + Math.cos(a) * d), z = (int) (p.getZ() + Math.sin(a) * d);
            BlockPos top = w.getTopPosition(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, new BlockPos(x, 0, z));
            LightningEntity l = EntityType.LIGHTNING_BOLT.create(w);
            if (l == null) continue;
            l.refreshPositionAfterTeleport(Vec3d.ofBottomCenter(top));
            l.setCosmetic(true);
            w.spawnEntity(l);
        }
    }

    /** «Ночь музыки»: диджей и 14 танцующих вокруг. */
    private static void festival(ServerWorld w, Vec3d at) {
        var r = w.getRandom();
        CitizenEntity.Activity[] moves = {CitizenEntity.Activity.DANCE, CitizenEntity.Activity.DANCE2, CitizenEntity.Activity.DANCE3, CitizenEntity.Activity.CHEER,
                CitizenEntity.Activity.CLAP};
        for (int i = 0; i < 15; i++) {
            CitizenEntity c = new CitizenEntity(ModContent.CITIZEN, w);
            int skin = CitizenEntity.randomSkin(r);
            c.setup(skin, CitizenEntity.Role.CITIZEN, true, i == 0 ? "Диджей" : com.yompix.novograd.life.Names.random(r, CitizenEntity.isSlim(skin)));
            double a = i == 0 ? 0 : i * 0.42, d = i == 0 ? 0 : 3 + (i % 3) * 1.6;
            Vec3d p = at.add(Math.cos(a) * d, 0, Math.sin(a) * d - 6);
            BlockPos g = Story.ground(w, p);
            c.refreshPositionAndAngles(p.x, g.getY(), p.z, r.nextFloat() * 360, 0);
            c.setCustomNameVisible(i == 0);
            w.spawnEntity(c);
            c.setStationary(true);
            c.lockActivity(i == 0 ? CitizenEntity.Activity.DANCE2 : moves[r.nextInt(moves.length)]);
            SPAWNED.add(c.getUuid());
        }
        w.playSound(null, BlockPos.ofFloored(at), net.minecraft.sound.SoundEvents.MUSIC_DISC_PIGSTEP.value(), net.minecraft.sound.SoundCategory.RECORDS, 4f, 1f);
    }
}
