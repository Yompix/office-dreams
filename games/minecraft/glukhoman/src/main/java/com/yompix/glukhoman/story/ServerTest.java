package com.yompix.glukhoman.story;

import com.mojang.authlib.GameProfile;
import com.yompix.glukhoman.GContent;
import com.yompix.glukhoman.Glukhoman;
import com.yompix.glukhoman.entity.ResidentEntity;
import com.yompix.glukhoman.entity.TrainEntity;
import com.yompix.glukhoman.entity.WatcherEntity;
import com.yompix.glukhoman.horror.Sched;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Автотест сервера (-Dglukhoman.servertest=1): «игрок-призрак» проходит весь сюжет от выбора сложности до концовки,
 * проверяя каждый шаг, появление существ и чекпоинты. В лог — SERVERTEST PASS/FAIL, потом сервер останавливается.
 */
public final class ServerTest {
    private ServerTest() {}

    record Step(String name, Runnable act, BooleanSupplier until, int timeout) {}

    static final List<Step> STEPS = new ArrayList<>();
    static int idx = -1, wait, started;
    static ServerPlayerEntity fake;
    /** Вариант «никто не будит»: кассету не сдаём, ночью не вмешиваемся — проверяем посадку и уход поезда. */
    static final boolean BOARD = Boolean.getBoolean("glukhoman.stboard");
    static MinecraftServer srv;

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(ServerTest::tick);
    }

    static StoryState st() {
        return StoryState.get(srv.getOverworld());
    }

    static void tp(String point) {
        BlockPos q = st().point(point);
        fake.refreshPositionAndAngles(q.getX() + 0.5, q.getY(), q.getZ() + 0.5, 0, 0);
    }

    static void tpAt(double x, double y, double z) {
        fake.refreshPositionAndAngles(x, y, z, fake.getYaw(), fake.getPitch());
    }

    /** Повернуть «призрака» лицом к точке. */
    static void look(net.minecraft.util.math.Vec3d at) {
        net.minecraft.util.math.Vec3d d = at.subtract(fake.getEyePos());
        fake.setYaw((float) Math.toDegrees(Math.atan2(-d.x, d.z)));
        fake.setPitch((float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z))));
        fake.setHeadYaw(fake.getYaw());
    }

    static void film(String point, double fromX, double fromZ) {
        BlockPos q = st().point(point);
        tpAt(fromX, 66, fromZ);
        look(net.minecraft.util.math.Vec3d.ofCenter(q));
        if (!Cam.up(fake)) Cam.raise(fake);
    }

    static void use(String key) {
        BlockPos q = st().find(key);
        if (q == null) throw new IllegalStateException("нет вещи " + key);
        fake.refreshPositionAndAngles(q.getX() + 0.5, q.getY() + 1, q.getZ() + 0.5, 0, 0);
        Story.interact(fake, q, key);
    }

    static boolean step(String s) {
        return st().step.equals(s);
    }

    static int count(Class<? extends Entity> c) {
        return srv.getOverworld().getEntitiesByClass(c, new Box(BlockPos.ORIGIN).expand(700), e -> true).size();
    }

    static ResidentEntity resident(String who) {
        for (ResidentEntity r : srv.getOverworld().getEntitiesByClass(ResidentEntity.class, new Box(BlockPos.ORIGIN).expand(700), e -> true))
            if (r.who().equals(who)) return r;
        throw new IllegalStateException("нет жителя " + who);
    }

    static void add(String name, Runnable act, BooleanSupplier until) {
        STEPS.add(new Step(name, act, until, 2400));
    }

    static int slotOf(String kind) {
        for (var e : Tapes.list(st())) {
            var t = (net.minecraft.nbt.NbtCompound) e;
            var sl = Tapes.slots(t);
            for (int i = 0; i < sl.size(); i++) if (sl.getCompound(i).getString("kind").equals(kind) && !t.getBoolean("dep")) return t.getInt("n") * 100 + i;
        }
        return -1;
    }

    static void build() {
        add("карта построена", () -> {}, () -> st().built && st().flag("v2") && st().find("box") != null && st().lamps.get("board").size() == 10);
        add("выбор сложности → пролог", () -> Story.chooseDifficulty(fake, 1), () -> st().chapter == 1 && step("p_rec") && fake.getInventory().count(GContent.CAMCORDER) == 1);
        add("снял перрон", () -> film("t_platform", 0.5, 19.5), () -> st().flag("rec_t_platform"));
        add("снял стрелку", () -> film("t_switch", -12.5, 19.5), () -> st().flag("rec_t_switch"));
        add("снял переезд → на восток", () -> film("t_crossing", 22.5, 19.5), () -> step("p_east"));
        add("конец рельсов → звонит переезд", () -> { Cam.lower(fake, true); tpAt(66.5, 66, 16.5); }, () -> step("p_bell"));
        add("камеру вверх → поезд, посадка, утро", () -> Cam.raise(fake), () -> st().chapter == 2 && step("c1_house") && st().flag("taken:polya") && count(ResidentEntity.class) == 4);
        add("пролог на плёнке", () -> {}, () -> Tapes.has(st(), "prologue", null));
        add("дом бабы Поли → «Какая Поля?» → ящик", () -> tp("zina_day"), () -> step("c1_box"));
        add("интервью с Викой", () -> {
            ResidentEntity v = resident("vika");
            tpAt(v.getX() + 2.5, v.getY(), v.getZ());
            look(v.getPos().add(0, 1.4, 0));
            Cam.raise(fake);
            Story.resident(fake, v);
        }, () -> Tapes.has(st(), "interview", "int_vika") && Tapes.anchored(st(), "vika"));
        if (BOARD) add("оставил кассету себе → Гена злится", () -> { Cam.lower(fake, true); use("box"); Story.answer(fake, "box", 1); },
                () -> step("c1_zakhar") && st().flag("kept1") && Tapes.list(st()).size() == 1);
        else add("сдал кассету → мотовоз, новая кассета", () -> { Cam.lower(fake, true); use("box"); Story.answer(fake, "box", 0); },
                () -> step("c1_zakhar") && Tapes.list(st()).size() == 2 && fake.getInventory().count(GContent.BATTERY) >= 3);
        add("Захар → вечер", () -> { ResidentEntity z = resident("zakhar"); tpAt(z.getX() + 1, z.getY(), z.getZ()); Story.resident(fake, z); }, () -> step("c1_evening"));
        add("стул → объявление правил (снимаю)", () -> { use("wait"); tp("t_platform"); look(net.minecraft.util.math.Vec3d.ofCenter(st().point("loudspeaker"))); Sched.after(100, () -> Cam.raise(fake)); },
                () -> step("c1_wait"));
        add("правила на плёнке, пересмотр", () -> { Cam.lower(fake, true); int s = slotOf("announce"); if (s >= 0) Tapes.action(fake, s / 100, s % 100, "play"); },
                () -> Tapes.has(st(), "announce", null));
        add("стул → ночь 2, Обходчик, лунатик", () -> use("wait"), () -> Night.running && count(WatcherEntity.class) == 1 && !Night.walkersList().isEmpty());
        add("03:14 → звонок, поезд", () -> Night.skipTo(190), () -> step("n2_night") && count(TrainEntity.class) == 1);
        if (BOARD) {
            STEPS.add(new Step("никто не будит: лунатик дошёл и сел в поезд", () -> tp("cp_dispatch"), () -> st().count("taken") >= 1, 6000));
            STEPS.add(new Step("поезд ушёл → утро", () -> {}, () -> step("c1_replay") && !Night.running, 4000));
            add("пересмотр ночной записи → конец части 1", () -> { int s = slotOf("night"); if (s >= 0) Tapes.action(fake, s / 100, s % 100, "play"); },
                    () -> st().ending.equals("1") && step("end1"));
            return;
        }
        add("колокол и стрелка", () -> { use("bell"); use("switch_e"); }, () -> st().flag("bell_used") && Night.phase() == 4);
        add("будить лунатика", () -> {
            ResidentEntity r = Night.walkersList().get(0);
            tpAt(r.getX() + 1, r.getY(), r.getZ());
            Story.resident(fake, r);
        }, () -> st().flags.stream().anyMatch(f -> f.startsWith("saved:")));
        add("Обходчик вырвал плёнку", () -> Night.grab(fake, srv.getOverworld().getEntitiesByClass(WatcherEntity.class, new Box(BlockPos.ORIGIN).expand(700), e -> true).get(0)),
                () -> true);
        add("ночь кончилась → утро", Night::finish, () -> step("c1_replay") && !Night.running && count(TrainEntity.class) == 0 && Tapes.has(st(), "night", null));
        add("пересмотр ночной записи → конец части 1", () -> { int s = slotOf("night"); if (s >= 0) Tapes.action(fake, s / 100, s % 100, "play"); },
                () -> st().ending.equals("1") && step("end1"));
    }

    static void tick(MinecraftServer s) {
        srv = s;
        if (++started < 100) return;
        if (fake == null) {
            if (!Story.isOurWorld()) {
                Glukhoman.LOG.error("SERVERTEST FAIL: мир не «Глухомань» (level-type=glukhoman:taiga?)");
                s.stop(false);
                return;
            }
            fake = FakePlayer.get(s.getOverworld(), new GameProfile(UUID.fromString("00000000-0000-0000-0000-00000000d1d1"), "GlkTester"));
            Story.testPlayer = fake;
            Story.reset();
            // у «призрака» нет клиента, который грузит чанки, — держим сюжетные места загруженными сами
            int[][] areas = {{-80, -8, 120, 140}, {120, 0, 340, 32}};
            for (int[] a : areas)
                for (int x = a[0] >> 4; x <= a[2] >> 4; x++)
                    for (int z = a[1] >> 4; z <= a[3] >> 4; z++) s.getOverworld().setChunkForced(x, z, true);
            build();
        }
        if (idx >= STEPS.size()) return;
        if (idx >= 0) {
            Step cur = STEPS.get(idx);
            if (!cur.until().getAsBoolean()) {
                if (++wait > cur.timeout()) {
                    Glukhoman.LOG.error("SERVERTEST FAIL на шаге «{}»: {}", cur.name(), Story.status());
                    idx = STEPS.size();
                    s.stop(false);
                }
                return;
            }
            Glukhoman.LOG.info("SERVERTEST ok {}/{} {} ({} тиков) — {}", idx + 1, STEPS.size(), cur.name(), wait, Story.status());
        }
        idx++;
        wait = 0;
        if (idx >= STEPS.size()) {
            Glukhoman.LOG.info("SERVERTEST PASS: пролог и ночи 1–2 пройдены{}, конец части {}", BOARD ? " (вариант: никто не будит, забрали " + st().count("taken") + ")" : "", st().ending);
            s.stop(false);
            return;
        }
        try {
            STEPS.get(idx).act().run();
        } catch (Exception e) {
            Glukhoman.LOG.error("SERVERTEST FAIL: исключение на шаге «" + STEPS.get(idx).name() + "»", e);
            idx = STEPS.size();
            s.stop(false);
        }
    }
}
