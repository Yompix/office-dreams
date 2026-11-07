package com.yompix.novograd.client;

import com.yompix.novograd.Novograd;
import com.yompix.novograd.client.screen.DarkJobsScreen;
import com.yompix.novograd.client.screen.PhoneScreen;
import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.gta.BigHeists;
import com.yompix.novograd.gta.Contracts;
import com.yompix.novograd.gta.Smuggling;
import com.yompix.novograd.gta.Wanted;
import com.yompix.novograd.life.Life;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.Entity;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Только для разработки: -Dnovograd.autotest=heist (или heist:board, heist:stealth, heist:loud, heist:contract, heist:smuggle) —
 * «Тёмные дела»: доска ограблений и экраны телефона, тихий и громкий финал, заказ, контрабанда катером и самолётом.
 * Мир — копия Fresh2 (-Dnovograd.lifeworld=…); снимки run/screenshots/heist_*.png, в логе — [heist] и mspt.
 */
public final class HeistAutotest {
    private HeistAutotest() {}

    private record Step(int delay, Consumer<MinecraftClient> action) {}

    private static final List<Step> PRE = new ArrayList<>(), STEPS = new ArrayList<>();
    private static int preIndex = -1, preTimer, index = -1, timer;
    private static final String P = "Dev";

    public static void init() {
        String mode = System.getProperty("novograd.autotest");
        if (mode == null || !mode.startsWith("heist")) return;
        String only = mode.contains(":") ? mode.split(":")[1] : "";
        com.yompix.novograd.client.story.YompixSplash.markShown();
        PRE.add(new Step(60, c -> c.createIntegratedServerLoader().start(System.getProperty("novograd.lifeworld", "Fresh2"),
                () -> c.setScreen(new com.yompix.novograd.client.menu.LifeMenuScreen()))));
        server(1, s -> {
            var d = Life.get(pl(s));
            d.storyStarted = true;
            d.storyDone = true;
        });
        run(100, c -> { if (c.player != null && c.player.isDead()) c.player.requestRespawn(); });
        run(100, c -> { if (c.currentScreen != null) c.setScreen(null); });
        cmd(5, "gamemode survival " + P);
        cmd(5, "gamerule doDaylightCycle false");
        cmd(5, "gamerule keepInventory true");
        cmd(5, "weather clear 99999");
        cmd(5, "time set 14000");
        cmd(5, "effect give " + P + " minecraft:resistance 99999 4 true");
        cmd(5, "effect give " + P + " minecraft:regeneration 99999 3 true");
        server(5, s -> {
            var d = Life.get(pl(s));
            d.storyStarted = true;
            d.storyDone = true;
            d.money += 50000;
            Life.sync(pl(s));
        });
        if (only.isEmpty() || only.contains("board")) board();
        if (only.isEmpty() || only.contains("stealth")) stealth();
        if (only.isEmpty() || only.contains("loud")) loud();
        if (only.isEmpty() || only.contains("contract")) contract();
        if (only.isEmpty() || only.contains("smuggle")) smuggle();
        server(20, s -> log(s, "итог"));
        run(20, c -> Novograd.LOG.info("[heist] done"));
        run(20, MinecraftClient::scheduleStop);
        ClientTickEvents.END_CLIENT_TICK.register(HeistAutotest::tick);
    }

    // ------------------------------------------------------------------ сцены
    private static void board() {
        cmd(5, "uw heist bank prep");
        cmd(5, "uw contract");
        cmd(5, "uw smuggle");
        run(30, c -> c.setScreen(new PhoneScreen(null)));
        shot(40, "heist_phone");
        run(5, c -> c.setScreen(new DarkJobsScreen("heist")));
        shot(40, "heist_board");
        run(5, c -> c.setScreen(new DarkJobsScreen("contract")));
        shot(30, "heist_contracts");
        run(5, c -> c.setScreen(new DarkJobsScreen("smuggle")));
        shot(30, "heist_smuggle_list");
        run(5, c -> c.setScreen(null));
    }

    private static void stealth() {
        cmd(5, "time set 18000");
        cmd(5, "uw heist bank go");
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            Vec3d e = BigHeists.debugEntry(p), v = BigHeists.debugVault(p);
            if (e == null) return;
            ground(p, e.add(e.subtract(v).normalize().multiply(30)), v); // издалека — охрана появится
        });
        server(60, s -> {
            ServerPlayerEntity p = pl(s);
            Vec3d e = BigHeists.debugEntry(p), v = BigHeists.debugVault(p);
            if (e != null) ground(p, e.add(e.subtract(v).normalize().multiply(6)), v);
            p.setSneaking(true);
        });
        server(60, s -> log(s, "у входа"));
        shot(10, "heist_stealth_entry");
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            Vec3d v = BigHeists.debugVault(p);
            if (v != null) look(p, v.add(14, 12, 14), v);
        });
        shot(40, "heist_stealth_overview");
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            Vec3d v = BigHeists.debugVault(p), e = BigHeists.debugEntry(p);
            if (v != null) ground(p, v.add(e.subtract(v).normalize().multiply(1.5)), v);
        });
        server(80, s -> log(s, "у сейфа"));
        shot(10, "heist_stealth_vault");
        server(5, s -> {
            pl(s).setSneaking(false);
            BigHeists.debugStage(pl(s), 3);
        });
        server(140, s -> log(s, "мешки"));
        shot(5, "heist_stealth_bags");
        server(5, s -> BigHeists.debugStage(pl(s), 4));
        server(10, s -> {
            ServerPlayerEntity p = pl(s);
            Vec3d g = com.yompix.novograd.story.Story.place(p.getServerWorld(), "downtown_plaza+0,30");
            if (g != null) ground(p, g.add(6, 0, 6), g);
        });
        shot(40, "heist_stealth_getaway");
        server(5, s -> log(s, "машина"));
        server(5, s -> BigHeists.debugStage(pl(s), 5));
        server(30, s -> log(s, "выплата"));
        shot(10, "heist_stealth_paid");
        server(5, s -> Wanted.clear(pl(s)));
    }

    private static void loud() {
        cmd(5, "time set 6000");
        cmd(5, "uw heist casino go loud");
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            Vec3d e = BigHeists.debugEntry(p), v = BigHeists.debugVault(p);
            if (e != null) ground(p, e.add(e.subtract(v).normalize().multiply(4)), v);
        });
        server(100, s -> log(s, "громко вход"));
        shot(5, "heist_loud_entry");
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            Vec3d v = BigHeists.debugVault(p), e = BigHeists.debugEntry(p);
            if (v != null) ground(p, v.add(e.subtract(v).normalize().multiply(1.5)), v);
        });
        server(40, s -> {
            ServerPlayerEntity p = pl(s);
            Vec3d v = BigHeists.debugVault(p), e = BigHeists.debugEntry(p);
            if (v != null) ground(p, v.add(e.subtract(v).normalize().multiply(7)), v); // отойти от заряда
        });
        shot(50, "heist_loud_boom");
        server(60, s -> log(s, "громко сейф"));
        shot(5, "heist_loud_vault");
        cmd(5, "uw cancel");
        server(5, s -> Wanted.clear(pl(s)));
    }

    private static void contract() {
        cmd(20, "uw contract 0");
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            Vec3d t = Contracts.debugTarget(p);
            if (t != null) ground(p, t.add(30, 0, 0), t);
        });
        server(60, s -> {
            ServerPlayerEntity p = pl(s);
            Vec3d t = Contracts.debugTarget(p);
            if (t != null) ground(p, t.add(7, 0, 3), t);
            log(s, "заказ");
        });
        shot(40, "heist_contract_target");
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            Vec3d t = Contracts.debugTarget(p);
            if (t == null) return;
            for (CitizenEntity c : p.getServerWorld().getEntitiesByClass(CitizenEntity.class, p.getBoundingBox().expand(40), c -> c.getPos().squaredDistanceTo(t) < 0.5))
                c.damage(p.getDamageSources().playerAttack(p), 200);
        });
        server(80, s -> log(s, "заказ выполнен"));
        shot(5, "heist_contract_done");
        server(5, s -> Wanted.clear(pl(s)));
    }

    private static void smuggle() {
        server(20, s -> Novograd.LOG.info("[heist] {}", Smuggling.debugBoat(pl(s), false, true)));
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            Vec3d at = com.yompix.novograd.story.Story.place(p.getServerWorld(), "port_dock");
            if (at != null) ground(p, at, at.add(-20, 0, 0));
        });
        server(40, s -> {
            ServerPlayerEntity p = pl(s);
            Entity v = Smuggling.debugVehicle(p);
            if (v != null) p.startRiding(v, true);
        });
        server(200, s -> log(s, "катер"));
        shot(5, "heist_smuggle_boat");
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            Entity v = Smuggling.debugVehicle(p);
            if (v != null) look(p, v.getPos().add(10, 8, 10), v.getPos());
        });
        shot(40, "heist_smuggle_chase");
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            Entity v = Smuggling.debugVehicle(p);
            Vec3d d = Smuggling.debugDrop(p);
            if (v == null || d == null) return;
            p.stopRiding();
            v.refreshPositionAndAngles(d.x + 4, v.getY(), d.z + 4, v.getYaw(), 0);
            p.teleport(p.getServerWorld(), d.x + 4, v.getY() + 1, d.z + 4, p.getYaw(), 20);
            p.startRiding(v, true);
        });
        server(40, s -> log(s, "катер доставка"));
        shot(5, "heist_smuggle_delivered");
        server(5, s -> {
            pl(s).stopRiding();
            Wanted.clear(pl(s));
        });
        cmd(5, "uw cancel");
        server(40, s -> Novograd.LOG.info("[heist] {}", Smuggling.debugBoat(pl(s), true, false)));
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            Vec3d at = com.yompix.novograd.story.Story.place(p.getServerWorld(), "airfield");
            if (at != null) ground(p, at, at.add(-30, 0, 0));
        });
        server(40, s -> {
            ServerPlayerEntity p = pl(s);
            Entity v = Smuggling.debugVehicle(p);
            if (v != null) p.startRiding(v, true);
        });
        shot(40, "heist_smuggle_plane");
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            Entity v = Smuggling.debugVehicle(p);
            Vec3d d = Smuggling.debugDrop(p);
            if (v == null || d == null) return;
            ServerWorld w = p.getServerWorld();
            w.getChunk((int) d.x >> 4, (int) d.z >> 4);
            int top = w.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, (int) d.x, (int) d.z);
            p.stopRiding();
            v.refreshPositionAndAngles(d.x + 6, top + 25, d.z, v.getYaw(), 0);
            p.teleport(w, d.x + 6, top + 26, d.z, p.getYaw(), 20);
            p.startRiding(v, true);
        });
        server(30, s -> log(s, "самолёт сброс"));
        shot(5, "heist_smuggle_drop");
        server(5, s -> pl(s).stopRiding());
    }

    // ------------------------------------------------------------------ помощники
    private static ServerPlayerEntity pl(IntegratedServer s) {
        return s.getPlayerManager().getPlayer(P);
    }

    private static void log(IntegratedServer s, String what) {
        ServerPlayerEntity p = pl(s);
        var d = Life.get(p);
        Novograd.LOG.info("[heist] {}: {} | звёзд {} грязных ${} | mspt {} | pos {}", what,
                com.yompix.novograd.gta.Underworld.debug(p, "state"), Wanted.stars(p), d.dirty,
                String.format("%.2f", s.getAverageTickTime()), p.getBlockPos().toShortString());
    }

    /** Поставить игрока на землю в точке at лицом к look. */
    private static void ground(ServerPlayerEntity p, Vec3d at, Vec3d look) {
        ServerWorld w = p.getServerWorld();
        w.getChunk((int) Math.floor(at.x) >> 4, (int) Math.floor(at.z) >> 4);
        double y = at.y;
        var bp = net.minecraft.util.math.BlockPos.ofFloored(at.x, y, at.z);
        for (int i = 0; i < 6 && !(w.isAir(bp) && w.isAir(bp.up())); i++) bp = bp.up();
        double dx = look.x - at.x, dz = look.z - at.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        p.stopRiding();
        p.getAbilities().flying = false;
        p.sendAbilitiesUpdate();
        p.teleport(w, at.x, bp.getY(), at.z, yaw, 8);
    }

    private static void look(ServerPlayerEntity p, Vec3d from, Vec3d to) {
        ServerWorld w = p.getServerWorld();
        w.getChunk((int) Math.floor(from.x) >> 4, (int) Math.floor(from.z) >> 4);
        double dx = to.x - from.x, dy = to.y - (from.y + 1.62), dz = to.z - from.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        p.stopRiding();
        p.getAbilities().allowFlying = true;
        p.getAbilities().flying = true;
        p.sendAbilitiesUpdate();
        p.teleport(w, from.x, from.y, from.z, yaw, pitch);
    }

    private static void cmd(int delay, String command) {
        server(delay, s -> s.getCommandManager().executeWithPrefix(s.getCommandSource().withEntity(pl(s)).withPosition(pl(s).getPos()), command));
    }

    private static void server(int delay, Consumer<IntegratedServer> action) {
        STEPS.add(new Step(delay, c -> {
            IntegratedServer s = c.getServer();
            if (s != null) s.execute(() -> {
                try {
                    action.accept(s);
                } catch (RuntimeException e) {
                    Novograd.LOG.warn("[heist] server step failed", e);
                }
            });
        }));
    }

    private static void run(int delay, Consumer<MinecraftClient> action) {
        STEPS.add(new Step(delay, action));
    }

    private static void shot(int delay, String name) {
        STEPS.add(new Step(delay, c -> ScreenshotRecorder.saveScreenshot(c.runDirectory, name + ".png", c.getFramebuffer(), t -> {})));
    }

    private static void tick(MinecraftClient c) {
        if (c.player == null && !PRE.isEmpty() && c.getOverlay() == null && preIndex < PRE.size()) {
            if (preIndex < 0) {
                if (!(c.currentScreen instanceof com.yompix.novograd.client.story.YompixSplash) && !(c.currentScreen instanceof com.yompix.novograd.client.menu.LifeMenuScreen)) return;
                preIndex = 0;
                preTimer = PRE.get(0).delay();
            }
            if (--preTimer > 0) return;
            PRE.get(preIndex).action().accept(c);
            preIndex++;
            if (preIndex < PRE.size()) preTimer = PRE.get(preIndex).delay();
            return;
        }
        if (STEPS.isEmpty() || c.player == null || c.world == null) return;
        if (index < 0) {
            index = 0;
            timer = STEPS.get(0).delay();
        }
        if (index >= STEPS.size()) return;
        if (--timer > 0) return;
        try {
            STEPS.get(index).action().accept(c);
        } catch (RuntimeException e) {
            Novograd.LOG.warn("[heist] client step failed", e);
        }
        index++;
        if (index < STEPS.size()) timer = STEPS.get(index).delay();
    }
}
