package com.yompix.glukhoman.story;

import com.mojang.authlib.GameProfile;
import com.yompix.glukhoman.GContent;
import com.yompix.glukhoman.Glukhoman;
import com.yompix.glukhoman.entity.AnnaEntity;
import com.yompix.glukhoman.entity.ChoirEntity;
import com.yompix.glukhoman.entity.ListenerEntity;
import com.yompix.glukhoman.entity.PoleEntity;
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

    static void add(String name, Runnable act, BooleanSupplier until) {
        STEPS.add(new Step(name, act, until, 2400));
    }

    static void build() {
        add("карта построена", () -> {}, () -> st().built && st().interact.size() > 40);
        add("выбор сложности → глава 1", () -> Story.chooseDifficulty(fake, 1), () -> st().chapter == 1 && step("c1_road"));
        add("дошёл до посёлка", () -> { tp("cp2"); Story.area(fake, st()); }, () -> step("c1_house"));
        add("вошёл в дом Анны", () -> { tp("cp2"); Story.area(fake, st()); }, () -> step("c1_radio"));
        add("рация Анны", () -> use("radio_anna"), () -> step("c1_battery"));
        add("ящик Анны: фонарик", () -> use("crate_anna"), () -> fake.getInventory().count(GContent.FLASHLIGHT) == 1);
        add("батарейки → ночь", () -> use("crate_shop"), () -> step("c1_night"));
        add("печь → стук", () -> { tp("cp2"); use("stove_anna"); }, () -> step("c1_knock"));
        add("ночь прошла → глава 2", () -> {}, () -> st().chapter == 2 && step("c2_fuses") && count(ListenerEntity.class) >= 1);
        add("три предохранителя", () -> { use("crate_council_fuse"); use("crate_school_fuse"); use("crate_garage_fuse"); }, () -> step("c2_power"));
        add("щит → свет", () -> use("fusebox"), () -> step("c2_call") && count(ListenerEntity.class) == 0);
        add("связь из сельсовета", () -> use("radio_council"), () -> step("c2_key"));
        add("сейф → ключ", () -> use("crate_council_safe"), () -> step("c2_gate"));
        add("ворота → глава 3", () -> use("lock_gate"), () -> st().chapter == 3 && step("c3_path"));
        add("Жердь вышла на охоту", () -> {}, () -> count(PoleEntity.class) >= 1);
        add("пришёл на станцию", () -> { tp("cp5"); Story.area(fake, st()); }, () -> step("c3_power"));
        add("канистра и генератор", () -> { use("crate_hunter_can"); use("generator"); }, () -> step("c3_log") && st().flag("station_power"));
        add("журнал Анны", () -> use("note:anna_log"), () -> step("c3_key"));
        add("сейф Шумова → погоня", () -> use("crate_shumov"), () -> step("c3_run") && count(ListenerEntity.class) >= 2);
        add("добежал до шахты → глава 4", () -> { tp("cp4top"); Story.area(fake, st()); }, () -> st().chapter == 4 && step("c4_lift"));
        add("лифт вниз", () -> use("lift_top"), () -> step("c4_marks") && count(ListenerEntity.class) >= 3);
        add("три метки → убежище", () -> { use("mark_1"); use("mark_2"); use("mark_3"); }, () -> step("c4_anna") && count(AnnaEntity.class) == 1);
        add("Анна → камертон", () -> { tp("anna_mine"); Entity a = srv.getOverworld().getEntitiesByClass(AnnaEntity.class, new Box(BlockPos.ORIGIN).expand(700), e -> true).get(0); Story.interact(fake, a.getBlockPos(), "anna"); },
                () -> step("c4_escape") && fake.getInventory().count(GContent.TUNING_FORK) == 1 && count(ChoirEntity.class) == 1);
        add("лифт вверх → глава 5", () -> use("lift_bottom"), () -> st().chapter == 5 && step("c5_bunker"));
        add("бункер → Хор", () -> { tp("bunker_in"); Story.area(fake, st()); }, () -> step("c5_amps") && count(ChoirEntity.class) == 1);
        add("усилитель 1", () -> use("amp_1"), () -> st().flag("amp_1"));
        add("усилитель 2", () -> use("amp_2"), () -> st().flag("amp_2"));
        add("усилитель 3", () -> use("amp_3"), () -> step("c5_tx"));
        add("передатчик", () -> use("transmitter"), () -> st().flag("choosing"));
        add("выбор «Тишина» → концовка", () -> Story.choose(fake, "A"), () -> st().ending.equals("A") && step("end") && count(ChoirEntity.class) == 0);
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
            Story.reset();
            // у «призрака» нет клиента, который грузит чанки, — держим сюжетные места загруженными сами
            int[][] areas = {{-64, 16, 32, 176}, {-40, -288, 40, -200}, {-160, -240, -64, -128}};
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
            Glukhoman.LOG.info("SERVERTEST PASS: весь сюжет пройден, концовка {}", st().ending);
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
