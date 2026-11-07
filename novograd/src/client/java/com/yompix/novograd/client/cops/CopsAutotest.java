package com.yompix.novograd.client.cops;

import com.yompix.novograd.Novograd;
import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.gta.*;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.world.Island;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Только для разработки: -Dnovograd.autotest=cops (или cops:chase,army,search,mask,prison,cnr) — погоня с PIT и шипами,
 * армия на 5★, зона поиска, ларёк масок и смена образа, колония и побег, «Полицейские и воры» (одному: -Dnovograd.cnrsolo=true).
 * Мир Fresh2 (или -Dnovograd.copsworld=...); снимки run/screenshots/cops_*.png, в логе — [cops].
 */
public final class CopsAutotest {
    private CopsAutotest() {}

    private record Step(int delay, Consumer<MinecraftClient> action) {}

    private static final List<Step> PRE = new ArrayList<>(), STEPS = new ArrayList<>();
    private static int preIndex = -1, preTimer, index = -1, timer;
    private static final String P = "Dev";

    public static void init() {
        String mode = System.getProperty("novograd.autotest");
        if (mode == null || !mode.startsWith("cops")) return;
        String only = mode.contains(":") ? mode.split(":")[1] : "";
        com.yompix.novograd.client.story.YompixSplash.markShown();
        PRE.add(new Step(60, c -> c.createIntegratedServerLoader().start(System.getProperty("novograd.copsworld", "Fresh2"),
                () -> c.setScreen(new com.yompix.novograd.client.menu.LifeMenuScreen()))));
        server(1, s -> {
            var d = Life.get(pl(s));
            d.storyStarted = true;
            d.storyDone = true;
            d.money = 20000;
        });
        run(100, c -> { if (c.player != null && c.player.isDead()) c.player.requestRespawn(); });
        run(60, c -> { if (c.currentScreen != null) c.setScreen(null); });
        cmd(5, "gamemode survival " + P);
        cmd(5, "gamerule doDaylightCycle false");
        cmd(5, "gamerule doMobSpawning false");
        cmd(5, "weather clear 99999");
        cmd(5, "time set 6000");
        cmd(5, "effect give " + P + " minecraft:resistance 99999 4 true");
        cmd(5, "effect give " + P + " minecraft:saturation 99999 4 true");
        int G = Island.G;
        if (only.isEmpty() || only.contains("chase")) chase(G);
        if (only.isEmpty() || only.contains("army")) army(G);
        if (only.isEmpty() || only.contains("search")) search(G);
        if (only.isEmpty() || only.contains("mask")) mask(G);
        if (only.isEmpty() || only.contains("prison")) prison(G);
        if (only.isEmpty() || only.contains("cnr")) cnr(G);
        run(20, c -> Novograd.LOG.info("[cops] done"));
        run(20, MinecraftClient::scheduleStop);
        ClientTickEvents.END_CLIENT_TICK.register(CopsAutotest::tick);
    }

    // ------------------------------------------------------------------ сцены
    /** Северная трасса: игрок в машине жмёт газ, 3★ — патрули, PIT, шипы впереди. */
    private static void chase(int G) {
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            ServerWorld w = p.getServerWorld();
            Wanted.clear(p);
            for (int k = 0; k < 6; k++) w.getChunk(1, (-820 >> 4) - k);
            p.teleport(w, 22.5, G + 1, -790.5, 180, 0);
        });
        server(40, s -> {
            ServerPlayerEntity p = pl(s);
            ServerWorld w = p.getServerWorld();
            int y = com.yompix.novograd.story.Story.topY(w, 22, -800);
            CarEntity car = FunUtil.car(w, new Vec3d(22.5, y, -800.5), 180, 9);
            car.setOwner(p.getUuid());
            p.startRiding(car, true);
            Wanted.add(p, 0, "автотест: погоня", 3);
            // патруль сзади — сразу в погоню
            CarEntity cop = FunUtil.car(w, new Vec3d(26.5, y, -760.5), 180, CarEntity.POLICE);
            for (int i = 0; i < 2; i++) {
                CitizenEntity c = new CitizenEntity(com.yompix.novograd.ModContent.CITIZEN, w);
                c.setup(CitizenEntity.skinIndex("police"), CitizenEntity.Role.POLICE, false, "Полицейский");
                c.refreshPositionAndAngles(cop.getX(), cop.getY(), cop.getZ(), 0, 0);
                c.removeWhenIdle();
                w.spawnEntity(c);
                c.startRiding(cop, true);
                c.attack(p);
            }
            cop.chase(p);
        });
        run(10, c -> c.options.setPerspective(Perspective.THIRD_PERSON_BACK));
        run(5, c -> holdW = true);
        for (int k = 0; k < 6; k++) server(10, s -> log(s, "chase+"));
        shot(1, "cops_chase_start");
        server(1, s -> log(s, "chase 3s"));
        shot(80, "cops_chase_1");
        server(1, s -> log(s, "chase 7s"));
        shot(100, "cops_chase_2");
        server(1, s -> log(s, "chase 12s"));
        shot(100, "cops_chase_3");
        server(1, s -> log(s, "chase 17s"));
        shot(120, "cops_chase_4");
        server(1, s -> log(s, "chase 23s"));
        run(1, c -> {
            holdW = false;
            c.options.forwardKey.setPressed(false);
        });
        // шипы крупно: где лежат
        server(30, s -> {
            ServerPlayerEntity p = pl(s);
            log(s, "chase stop");
            var w = p.getServerWorld();
            var strips = w.getEntitiesByClass(net.minecraft.entity.decoration.DisplayEntity.BlockDisplayEntity.class, p.getBoundingBox().expand(160, 30, 160),
                    e -> e.getCommandTags().contains("nvg_spike"));
            Novograd.LOG.info("[cops] шипов рядом {}", strips.size());
            if (!strips.isEmpty()) {
                Vec3d at = strips.get(0).getPos();
                p.stopRiding();
                look(p, at.add(6, 4, 6), at);
            }
        });
        shot(40, "cops_spikes");
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            Wanted.clear(p);
            p.stopRiding();
        });
        run(5, c -> c.options.setPerspective(Perspective.FIRST_PERSON));
    }

    /** 5★ на трассе: броневики с пулемётчиком, солдаты. */
    private static void army(int G) {
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            ServerWorld w = p.getServerWorld();
            p.stopRiding();
            p.teleport(w, 24.5, G + 1, -1000.5, 180, 10);
            Wanted.add(p, 0, "автотест: армия", 5);
        });
        server(200, s -> log(s, "army 10s"));
        server(140, s -> {
            ServerPlayerEntity p = pl(s);
            CarEntity truck = null;
            for (CarEntity c : p.getServerWorld().getEntitiesByClass(CarEntity.class, p.getBoundingBox().expand(120), c -> c.getVariant() == CarEntity.SWAT))
                if (truck == null || c.squaredDistanceTo(p) < truck.squaredDistanceTo(p)) truck = c;
            if (truck != null) look(p, truck.getPos().add(7, 5, 7), truck.getPos().add(0, 1, 0));
            log(s, "army 17s");
        });
        shot(10, "cops_army_1");
        server(80, s -> log(s, "army 21s"));
        shot(1, "cops_army_2");
        server(5, s -> {
            Wanted.clear(pl(s));
            pl(s).getAbilities().flying = false;
            pl(s).sendAbilitiesUpdate();
        });
    }

    /** Потеряли из виду: круг поиска на радаре, вертолёт шарит прожектором. */
    private static void search(int G) {
        cmd(5, "time set 15000");
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            p.teleport(p.getServerWorld(), 430.5, G + 1, -390.5, 0, 0);
            Wanted.add(p, 0, "автотест: поиск", 4);
        });
        server(160, s -> log(s, "search seen"));
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            // ушли далеко и под крышу — полиция теряет
            p.teleport(p.getServerWorld(), 600.5, G + 1, -560.5, 0, 30);
        });
        server(100, s -> log(s, "search lost"));
        shot(10, "cops_search_radar");
        server(120, s -> log(s, "search later"));
        shot(10, "cops_search_radar2");
        server(5, s -> Wanted.clear(pl(s)));
        cmd(5, "time set 6000");
    }

    /** Ларёк «Маски и шмот», маска на лице, смена образа снимает звёзды. */
    private static void mask(int G) {
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            ServerWorld w = p.getServerWorld();
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) w.getChunk((Disguise.SHOP_X >> 4) + dx, (Disguise.SHOP_Z >> 4) + dz);
            look(p, new Vec3d(Disguise.SHOP_X + 0.5 - 6, Island.surface(Disguise.SHOP_X, Disguise.SHOP_Z) + 3.5, Disguise.SHOP_Z - 9.5),
                    new Vec3d(Disguise.SHOP_X + 0.5, Island.surface(Disguise.SHOP_X, Disguise.SHOP_Z) + 2, Disguise.SHOP_Z + 0.5));
        });
        server(80, s -> {
            ServerPlayerEntity p = pl(s);
            p.getAbilities().flying = false;
            p.sendAbilitiesUpdate();
            Novograd.LOG.info("[cops] ларёк: {}", com.yompix.novograd.world.TownState.get(p.getServerWorld()).storyPlaces.get("built:mask_shop"));
        });
        shot(20, "cops_mask_shop");
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            double y = Island.surface(Disguise.SHOP_X, Disguise.SHOP_Z) + 1;
            p.teleport(p.getServerWorld(), Disguise.SHOP_X + 0.5, y, Disguise.SHOP_Z - 0.6, 0, 10);
        });
        shot(50, "cops_mask_menu");
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            Disguise.click(p, "buy:balaclava");
            Disguise.click(p, "buy:hockey");
            Novograd.LOG.info("[cops] маска: {}", Disguise.wearing(p));
        });
        shot(20, "cops_mask_menu2");
        run(5, c -> { if (c.currentScreen != null) c.setScreen(null); });
        // лицом к камере: третье лицо спереди
        run(5, c -> c.options.setPerspective(Perspective.THIRD_PERSON_FRONT));
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            p.teleport(p.getServerWorld(), Disguise.SHOP_X - 3.5, p.getY(), Disguise.SHOP_Z - 3.5, 0, 0);
        });
        shot(30, "cops_mask_hockey");
        server(5, s -> Disguise.click(pl(s), "wear:balaclava"));
        shot(30, "cops_mask_balaclava");
        for (String m : new String[]{"clown", "pig", "skull", "scarf"}) {
            server(5, s -> {
                ServerPlayerEntity p = pl(s);
                p.teleport(p.getServerWorld(), Disguise.SHOP_X + 0.5, p.getY(), Disguise.SHOP_Z - 0.6, 0, 0);
                Disguise.click(p, "buy:" + m);
            });
            run(5, c -> { if (c.currentScreen != null) c.setScreen(null); });
            server(5, s -> pl(s).teleport(pl(s).getServerWorld(), Disguise.SHOP_X - 3.5, pl(s).getY(), Disguise.SHOP_Z - 3.5, 0, 0));
            shot(25, "cops_mask_" + m);
        }
        // смена образа: 2★, вне поля зрения полиции — новый комплект и маска → звёзды падают
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            Disguise.click(p, "off");
            Wanted.add(p, 0, "автотест: образ", 2);
            Novograd.LOG.info("[cops] образ: звёзд до {}", Wanted.stars(p));
        });
        server(60, s -> {
            ServerPlayerEntity p = pl(s);
            p.teleport(p.getServerWorld(), Disguise.SHOP_X + 0.5, p.getY(), Disguise.SHOP_Z - 0.6, 0, 0);
            Disguise.click(p, "outfit");
            Disguise.click(p, "wear:pig");
        });
        server(60, s -> Novograd.LOG.info("[cops] образ: звёзд после {} (seen {})", Wanted.stars(pl(s)), Wanted.seen(pl(s))));
        run(5, c -> { if (c.currentScreen != null) c.setScreen(null); });
        server(5, s -> pl(s).teleport(pl(s).getServerWorld(), Disguise.SHOP_X - 3.5, pl(s).getY(), Disguise.SHOP_Z - 3.5, 0, 0));
        shot(30, "cops_mask_newlook");
        server(5, s -> {
            Wanted.clear(pl(s));
            Disguise.click(pl(s), "off");
        });
        run(5, c -> c.options.setPerspective(Perspective.FIRST_PERSON));
    }

    /** Арест → колония: камера, меню, побег ночью (вентиляция, лестница, тревога, собаки), свобода. */
    private static void prison(int G) {
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            Wanted.add(p, 0, "автотест: арест", 3);
            Wanted.busted(p);
            Novograd.LOG.info("[cops] арест: в колонии {} y {}", Prison.inside(p), p.getY());
        });
        shot(60, "cops_prison_cell");
        run(30, c -> { if (c.currentScreen != null) c.setScreen(null); });
        // вид сверху на колонию (камера-зритель отдельно не нужна: снимок с вышки)
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            p.changeGameMode(net.minecraft.world.GameMode.SPECTATOR);
            look(p, new Vec3d(Prison.X0 - 14, p.getY() + 22, Prison.Z0 - 14), new Vec3d(Prison.X0 + 15, p.getY(), Prison.Z0 + 15));
        });
        shot(60, "cops_prison_aerial");
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            p.changeGameMode(net.minecraft.world.GameMode.ADVENTURE);
            p.getAbilities().flying = false;
            p.sendAbilitiesUpdate();
        });
        cmd(30, "time set 15000");
        server(30, s -> Prison.menu(pl(s)));
        shot(30, "cops_prison_menu");
        server(5, s -> Prison.click(pl(s), "escape"));
        run(10, c -> { if (c.currentScreen != null) c.setScreen(null); });
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            p.teleport(p.getServerWorld(), Prison.X0 + 5.5, p.getY(), Prison.Z0 + 27.4, 0, 0);
        });
        run(5, c -> c.options.sneakKey.setPressed(true));
        shot(100, "cops_prison_vent");
        run(120, c -> c.options.sneakKey.setPressed(false));
        server(5, s -> log(s, "vent"));
        shot(10, "cops_prison_vent_open");
        // через вентиляцию к лестнице и на стену
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            p.teleport(p.getServerWorld(), Prison.X0 + 8.5, p.getY() + 5.2, Prison.Z0 + 29.5, 180, 20);
        });
        server(40, s -> log(s, "wall"));
        run(5, c -> c.options.setPerspective(Perspective.THIRD_PERSON_BACK));
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            p.teleport(p.getServerWorld(), Prison.X0 + 8.5, p.getY() - 5, Prison.Z0 + 34.5, 0, 10);
        });
        shot(60, "cops_prison_alarm");
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            log(s, "alarm");
            p.teleport(p.getServerWorld(), Prison.X0 + 15.5, p.getY(), Prison.Z0 + 200.5, 0, 0);
        });
        server(60, s -> log(s, "free"));
        shot(10, "cops_prison_free");
        run(5, c -> c.options.setPerspective(Perspective.FIRST_PERSON));
        server(5, s -> {
            Wanted.clear(pl(s));
            pl(s).changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
        });
        cmd(5, "time set 6000");
        // ещё раз: адвокат
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            Wanted.add(p, 0, "автотест: арест 2", 2);
            Wanted.busted(p);
            Prison.click(p, "lawyer");
            Novograd.LOG.info("[cops] адвокат: в колонии {} режим {}", Prison.inside(p), p.interactionManager.getGameMode());
        });
        shot(40, "cops_prison_lawyer");
    }

    /** «Полицейские и воры» одному (cnrsolo): табло, точка сбора, бот-патруль. */
    private static void cnr(int G) {
        run(5, c -> { if (c.currentScreen != null) c.setScreen(null); });
        server(5, s -> CopsAndRobbers.menu(pl(s)));
        shot(30, "cops_cnr_menu");
        run(5, c -> { if (c.currentScreen != null) c.setScreen(null); });
        server(5, s -> CopsAndRobbers.start(s, pl(s)));
        shot(80, "cops_cnr_start");
        server(200, s -> Novograd.LOG.info("[cops] cnr running {}", CopsAndRobbers.running()));
        shot(200, "cops_cnr_later");
        server(5, s -> CopsAndRobbers.stop(s, "автотест"));
        shot(30, "cops_cnr_stop");
    }

    // ------------------------------------------------------------------ помощники
    private static void log(IntegratedServer s, String tag) {
        ServerPlayerEntity p = pl(s);
        ServerWorld w = p.getServerWorld();
        int cars = w.getEntitiesByClass(CarEntity.class, p.getBoundingBox().expand(120), c -> c.isPolice()).size();
        int swat = w.getEntitiesByClass(CarEntity.class, p.getBoundingBox().expand(120), c -> c.getVariant() == CarEntity.SWAT).size();
        int cops = w.getEntitiesByClass(CitizenEntity.class, p.getBoundingBox().expand(120), c -> c.getRole() == CitizenEntity.Role.POLICE).size();
        String car = p.getVehicle() instanceof CarEntity c ? String.format("car flat=%s hp=%d", c.flat(), c.health()) : "on foot";
        Novograd.LOG.info("[cops] {} pos {} {} | {} | police cars {} swat {} cops {} | {} | jail {} | mspt {}", tag, (int) p.getX(), (int) p.getZ(), car,
                cars, swat, cops, Pursuit.debug(p), Prison.inside(p), String.format("%.2f", s.getAverageTickTime()));
    }

    private static ServerPlayerEntity pl(IntegratedServer s) {
        return s.getPlayerManager().getPlayer(P);
    }

    private static void look(ServerPlayerEntity p, Vec3d from, Vec3d to) {
        ServerWorld w = p.getServerWorld();
        w.getChunk((int) Math.floor(from.x) >> 4, (int) Math.floor(from.z) >> 4);
        double dx = to.x - from.x, dy = to.y - (from.y + 1.62), dz = to.z - from.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        p.stopRiding();
        p.teleport(w, from.x, from.y, from.z, yaw, pitch);
        p.getAbilities().allowFlying = true;
        p.getAbilities().flying = true;
        p.sendAbilitiesUpdate();
    }

    private static void cmd(int delay, String command) {
        server(delay, s -> s.getCommandManager().executeWithPrefix(s.getCommandSource(), command));
    }

    private static void server(int delay, Consumer<IntegratedServer> action) {
        STEPS.add(new Step(delay, c -> {
            IntegratedServer s = c.getServer();
            if (s != null) s.execute(() -> {
                try {
                    action.accept(s);
                } catch (RuntimeException e) {
                    Novograd.LOG.warn("[cops] server step failed", e);
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

    private static boolean holdW;

    private static void tick(MinecraftClient c) {
        if (holdW && c.player != null) {
            if (c.currentScreen != null) c.setScreen(null); // сюжетные окна сбрасывают нажатые клавиши
            c.options.forwardKey.setPressed(true);
        }
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
            Novograd.LOG.warn("[cops] client step failed", e);
        }
        index++;
        if (index < STEPS.size()) timer = STEPS.get(index).delay();
    }
}
