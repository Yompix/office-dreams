package com.yompix.novograd.client;

import com.yompix.novograd.Novograd;
import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.gta.Outdoors;
import com.yompix.novograd.gta.Skills;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.world.Island;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Только для разработки: -Dnovograd.autotest=outdoor (или outdoor:skills,hunt,fish,season,holiday) — навыки SA, дичь и охотничий домик,
 * рыбалка и турнир, осень/зима, Новый год, Хэллоуин, День города. Мир — копия Fresh2; снимки run/screenshots/outdoor_*.png, в логе — [outdoor].
 */
public final class OutdoorAutotest {
    private OutdoorAutotest() {}

    private record Step(int delay, Consumer<MinecraftClient> action) {}

    private static final List<Step> PRE = new ArrayList<>(), STEPS = new ArrayList<>();
    private static int preIndex = -1, preTimer, index = -1, timer;
    private static final String P = "Dev";

    public static void init() {
        String mode = System.getProperty("novograd.autotest");
        if (mode == null || !mode.startsWith("outdoor")) return;
        String only = mode.contains(":") ? mode.split(":")[1] : "";
        System.setProperty("novograd.skilltest", "true");
        com.yompix.novograd.client.story.YompixSplash.markShown();
        PRE.add(new Step(60, c -> c.createIntegratedServerLoader().start(System.getProperty("novograd.lifeworld", "Fresh2"),
                () -> c.setScreen(new com.yompix.novograd.client.menu.LifeMenuScreen()))));
        run(100, c -> { if (c.player != null && c.player.isDead()) c.player.requestRespawn(); });
        run(60, c -> { if (c.currentScreen != null) c.setScreen(null); });
        server(1, s -> {
            var d = Life.get(pl(s));
            d.storyStarted = true;
            d.storyDone = true;
        });
        cmd(5, "gamemode creative " + P);
        cmd(5, "gamerule doDaylightCycle false");
        cmd(5, "gamerule doMobSpawning false");
        cmd(5, "weather clear 99999");
        cmd(5, "outdoor season weather off");
        cmd(5, "effect give " + P + " minecraft:resistance 99999 4 true");
        int G = Island.G;
        if (only.isEmpty() || only.contains("skills")) skills();
        if (only.isEmpty() || only.contains("hunt")) hunt(G);
        if (only.isEmpty() || only.contains("fish")) fish(G);
        if (only.isEmpty() || only.contains("season")) season(G);
        if (only.isEmpty() || only.contains("holiday")) holiday(G);
        cmd(5, "outdoor holiday auto");
        cmd(5, "outdoor season game");
        run(20, c -> Novograd.LOG.info("[outdoor] done"));
        run(20, MinecraftClient::scheduleStop);
        ClientTickEvents.END_CLIENT_TICK.register(OutdoorAutotest::tick);
    }

    // ------------------------------------------------------------------ навыки
    private static void skills() {
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            var d = Life.get(p);
            Skills.set(d, Skills.Skill.DRIVE, 345);
            Skills.set(d, Skills.Skill.PISTOL, 620);
            Skills.set(d, Skills.Skill.RIFLE, 180);
            Skills.set(d, Skills.Skill.SWIM, 900);
            Skills.set(d, Skills.Skill.FLY, 60);
            Skills.set(d, Skills.Skill.STEALTH, 410);
            Skills.set(d, Skills.Skill.STAMINA, 270);
            Skills.set(d, Skills.Skill.STRENGTH, 530);
            Life.sync(p);
            Skills.addPoints(p, Skills.Skill.DRIVE, 10); // 34,5% → 35,5% — тост «навык вождения повышен»
            Novograd.LOG.info("[outdoor] drive grip {} steer {} slide {} | pistol spread {} recoil {} reload {} | swim {} | {}",
                    Skills.gripMul(p, false), Skills.steerMul(p, false), Skills.maxSlide(p), Skills.spreadMul(p, 0), Skills.recoilMul(p, 0), Skills.reloadMul(p, 0),
                    Skills.k(p, Skills.Skill.SWIM), Skills.debug(p, new String[]{"skills"}));
            int before = Skills.points(d, Skills.Skill.SMG);
            for (int i = 0; i < 40; i++) Skills.onShot(p, 1, i % 4 == 0 ? 2 : 1);
            Novograd.LOG.info("[outdoor] ПП после 40 попаданий: {} → {} очков", before, Skills.points(d, Skills.Skill.SMG));
        });
        server(5, s -> look(pl(s), new Vec3d(398, Island.G + 6, -397), new Vec3d(445, Island.G + 1, -404)));
        shot(25, "outdoor_skill_toast");
        run(40, c -> c.setScreen(new com.yompix.novograd.client.screen.SkillsScreen()));
        shot(30, "outdoor_skills_app");
        run(5, c -> c.setScreen(new com.yompix.novograd.client.screen.PhoneScreen(null)));
        shot(30, "outdoor_phone");
        run(5, c -> c.setScreen(null));
        // клиент видит тот же навык (физика машины — на клиенте)
        run(5, c -> Novograd.LOG.info("[outdoor] клиент: grip {} recoil {}", Skills.gripMul(c.player, false), Skills.recoilMul(c.player, 0)));
    }

    // ------------------------------------------------------------------ охота
    private static void hunt(int G) {
        cmd(5, "time set 3000");
        server(5, s -> look(pl(s), new Vec3d(1000, G + 30, -300), new Vec3d(1000, G + 20, -290)));
        server(40, s -> {
            ServerPlayerEntity p = pl(s);
            ServerWorld w = p.getServerWorld();
            int gy = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, 1000, -300);
            look(p, new Vec3d(1000.5, gy + 1.6, -300.5), new Vec3d(1000.5, gy + 0.8, -294));
            spawn(w, "deer", 999.5, -294);
            spawn(w, "boar", 1003.5, -295);
            spawn(w, "deer", 996.5, -293);
            Novograd.LOG.info("[outdoor] {}", com.yompix.novograd.gta.Hunting.region(1000, -300));
        });
        shot(60, "outdoor_hunt_animals");
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            ServerWorld w = p.getServerWorld();
            List<com.yompix.novograd.entity.WildAnimalEntity> l = w.getEntitiesByClass(com.yompix.novograd.entity.WildAnimalEntity.class, p.getBoundingBox().expand(20), e -> true);
            if (!l.isEmpty()) {
                var e = l.get(0);
                look(p, e.getPos().add(2.6, 1.6, 2.6), e.getPos().add(0, 0.8, 0));
            }
        });
        shot(30, "outdoor_hunt_close");
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            ServerWorld w = p.getServerWorld();
            for (var e : w.getEntitiesByClass(com.yompix.novograd.entity.WildAnimalEntity.class, p.getBoundingBox().expand(20), e -> e.kind() == com.yompix.novograd.entity.WildAnimalEntity.Kind.BOAR)) {
                look(p, e.getPos().add(2.2, 1.2, 2.2), e.getPos().add(0, 0.5, 0));
                break;
            }
        });
        shot(30, "outdoor_hunt_boar");
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            ServerWorld w = p.getServerWorld();
            p.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
            var list = w.getEntitiesByClass(com.yompix.novograd.entity.WildAnimalEntity.class, p.getBoundingBox().expand(30), e -> true);
            if (!list.isEmpty()) list.get(0).damage(w.getDamageSources().playerAttack(p), 200); // без лицензии — браконьерство
            Life.get(p).flags.add("hunt_license");
            if (list.size() > 1) list.get(1).damage(w.getDamageSources().playerAttack(p), 200); // с лицензией
            Novograd.LOG.info("[outdoor] добыто {}, браконьерство {}, звёзд {}", Life.get(p).skills.get("x_hunted"), Life.get(p).skills.get("x_poached"),
                    com.yompix.novograd.gta.Wanted.stars(p));
            com.yompix.novograd.gta.Wanted.clear(p);
            p.changeGameMode(net.minecraft.world.GameMode.CREATIVE);
        });
        server(300, s -> Novograd.LOG.info("[outdoor] дичь вокруг через 15 с: {}", com.yompix.novograd.gta.Hunting.class.getSimpleName() + " "
                + pl(s).getServerWorld().getEntitiesByClass(net.minecraft.entity.LivingEntity.class, pl(s).getBoundingBox().expand(100),
                e -> e.getCommandTags().contains("nv_wild")).stream().map(e -> e.getType().getUntranslatedName()).toList()));
        // охотничий домик у Сосновки
        server(5, s -> look(pl(s), new Vec3d(1196, G + 25, -10), new Vec3d(1196, G + 5, -36)));
        server(80, s -> {
            ServerPlayerEntity p = pl(s);
            Vec3d door = com.yompix.novograd.gta.Hunting.lodge(p.getServerWorld(), p);
            Novograd.LOG.info("[outdoor] домик: {}", door);
            if (door != null) look(p, door.add(7, 3.5, 9), door.add(0, 1, -3));
        });
        shot(80, "outdoor_lodge");
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            Vec3d door = com.yompix.novograd.gta.Hunting.lodge(p.getServerWorld(), p);
            if (door == null) return;
            p.getAbilities().flying = false;
            p.sendAbilitiesUpdate();
            p.teleport(p.getServerWorld(), door.x, door.y, door.z, 180, 10);
            p.giveItemStack(new ItemStack(Outdoors.PELT_DEER, 2));
            p.giveItemStack(new ItemStack(Outdoors.VENISON, 3));
        });
        shot(60, "outdoor_lodge_shop");
        run(5, c -> c.setScreen(null));
    }

    private static void spawn(ServerWorld w, String kind, double x, double z) {
        Entity e = kind.equals("deer") ? Outdoors.DEER.create(w) : Outdoors.BOAR.create(w);
        if (e == null) return;
        e.refreshPositionAndAngles(x, w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z)), z, 150, 0);
        e.addCommandTag("nv_wild");
        w.spawnEntity(e);
    }

    // ------------------------------------------------------------------ рыбалка
    private static void fish(int G) {
        cmd(5, "time set 1500");
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            ServerWorld w = p.getServerWorld();
            // что клюёт: таблица улова в озере и в море
            var table = s.getReloadableRegistries().getLootTable(net.minecraft.loot.LootTables.FISHING_FISH_GAMEPLAY);
            for (Vec3d at : new Vec3d[]{new Vec3d(1320, G, 760), new Vec3d(-700, G, 0)}) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < 12; i++) {
                    var set = new LootContextParameterSet.Builder(w).add(LootContextParameters.ORIGIN, at).add(LootContextParameters.TOOL, new ItemStack(Items.FISHING_ROD))
                            .build(LootContextTypes.FISHING);
                    for (ItemStack it : table.generateLoot(set)) sb.append(it.getItem().getName().getString()).append(", ");
                }
                Novograd.LOG.info("[outdoor] клюёт у {}: {}", at, sb);
            }
            Vec3d dock = com.yompix.novograd.story.Story.place(w, "lake_dock");
            look(p, dock.add(6, 4, 8), dock);
            p.getInventory().clear();
        });
        cmd(5, "outdoor tour");
        cmd(20, "outdoor fish pike");
        cmd(5, "outdoor fish perch");
        cmd(5, "outdoor fish catfish");
        cmd(5, "outdoor fish gold");
        shot(40, "outdoor_fish_tour");
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            Vec3d dock = com.yompix.novograd.story.Story.place(p.getServerWorld(), "lake_dock");
            p.getAbilities().flying = false;
            p.sendAbilitiesUpdate();
            p.teleport(p.getServerWorld(), dock.x, dock.y, dock.z, 0, 10);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < p.getInventory().size(); i++) {
                ItemStack it = p.getInventory().getStack(i);
                if (!it.isEmpty()) sb.append(it.getName().getString()).append(" ").append(com.yompix.novograd.gta.Fishing.grams(it)).append("г $")
                        .append(com.yompix.novograd.gta.Fishing.price(it)).append("; ");
            }
            Novograd.LOG.info("[outdoor] улов: {}", sb);
        });
        shot(60, "outdoor_fish_shop");
        run(5, c -> c.setScreen(null));
        cmd(5, "outdoor tour stop");
    }

    // ------------------------------------------------------------------ времена года
    private static void season(int G) {
        cmd(5, "time set 5000");
        cmd(5, "outdoor season autumn");
        server(5, s -> look(pl(s), new Vec3d(-230, G + 30, -760), new Vec3d(-200, G + 18, -830)));
        shot(160, "outdoor_autumn_forest");
        server(5, s -> look(pl(s), new Vec3d(-330, G + 30, 330), new Vec3d(-270, G + 2, 270)));
        shot(120, "outdoor_autumn_suburb");
        cmd(5, "outdoor season winter");
        cmd(5, "weather rain 99999");
        server(5, s -> look(pl(s), new Vec3d(398, G + 6, -397), new Vec3d(445, G + 1, -404)));
        shot(160, "outdoor_winter_snow");
        cmd(5, "weather clear 99999");
        server(5, s -> look(pl(s), new Vec3d(-330, G + 30, 330), new Vec3d(-270, G + 2, 270)));
        shot(140, "outdoor_winter_suburb");
        cmd(5, "outdoor season spring");
        shot(120, "outdoor_spring_suburb");
        cmd(5, "outdoor season game");
    }

    // ------------------------------------------------------------------ праздники
    private static void holiday(int G) {
        cmd(5, "outdoor holiday newyear");
        cmd(5, "time set 18010");
        server(5, s -> look(pl(s), new Vec3d(432, G + 14, -380), new Vec3d(432, G + 8, -419)));
        server(100, s -> {
            String t = com.yompix.novograd.world.TownState.get(pl(s).getServerWorld()).storyPlaces.get("hol_tree");
            Novograd.LOG.info("[outdoor] ёлка: {}", t);
            if (t == null) return;
            String[] a = t.split(",");
            double x = Double.parseDouble(a[0]), y = Double.parseDouble(a[1]), z = Double.parseDouble(a[2]);
            look(pl(s), new Vec3d(x + 14, y + 6, z + 14), new Vec3d(x, y + 8, z));
        });
        shot(100, "outdoor_newyear_tree");
        server(5, s -> look(pl(s), new Vec3d(470, G + 22, -360), new Vec3d(430, G + 10, -420)));
        shot(200, "outdoor_newyear_fireworks");
        server(1, s -> Novograd.LOG.info("[outdoor] ракет в небе: {}", pl(s).getServerWorld().getEntitiesByClass(net.minecraft.entity.projectile.FireworkRocketEntity.class,
                pl(s).getBoundingBox().expand(90), e -> true).size()));
        closeup("outdoor_newyear_hat");
        server(5, s -> Novograd.LOG.info("[outdoor] {}", com.yompix.novograd.world.Holidays.debug(pl(s), new String[]{"holiday"})));
        cmd(5, "outdoor holiday halloween");
        cmd(5, "time set 14500");
        server(5, s -> look(pl(s), new Vec3d(398, G + 6, -397), new Vec3d(445, G + 1, -404)));
        shot(260, "outdoor_halloween_street");
        closeup("outdoor_halloween_costume");
        server(5, s -> {
            double[] v = Island.place("hill_top");
            look(pl(s), new Vec3d(v[0] + 14, v[1] + 12, v[2] + 22), new Vec3d(v[0] + 14, v[1], v[2]));
        });
        shot(160, "outdoor_halloween_ruin");
        cmd(5, "outdoor holiday fear");
        cmd(5, "difficulty normal");
        server(120, s -> Novograd.LOG.info("[outdoor] мертвецов вокруг: {}",
                pl(s).getServerWorld().getEntitiesByClass(net.minecraft.entity.mob.ZombieEntity.class, pl(s).getBoundingBox().expand(40), e -> e.getCommandTags().contains("nv_fear")).size()));
        shot(5, "outdoor_halloween_fear");
        cmd(5, "outdoor holiday cityday");
        cmd(5, "time set 15100");
        server(5, s -> look(pl(s), new Vec3d(445, G + 10, -395), new Vec3d(432, G + 3, -413)));
        shot(200, "outdoor_cityday");
        cmd(5, "outdoor holiday off");
        server(200, s -> Novograd.LOG.info("[outdoor] после праздников: {}", com.yompix.novograd.world.Holidays.debug(pl(s), new String[]{"holiday"})));
    }

    private static void closeup(String name) {
        server(10, s -> {
            ServerPlayerEntity p = pl(s);
            ServerWorld w = p.getServerWorld();
            List<CitizenEntity> list = w.getEntitiesByClass(CitizenEntity.class, p.getBoundingBox().expand(60, 30, 60),
                    c -> c.isAlive() && !c.hasVehicle() && Math.floorMod(c.getId() * 7919 + 13, 100) < 40);
            if (list.isEmpty()) return;
            CitizenEntity c = list.get(0);
            look(p, c.getPos().add(1.6, 2.2, 1.6), c.getPos().add(0, 1.6, 0));
        });
        shot(40, name);
    }

    // ------------------------------------------------------------------ помощники
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
        p.getAbilities().flying = true;
        p.sendAbilitiesUpdate();
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
                    Novograd.LOG.warn("[outdoor] server step failed", e);
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
            Novograd.LOG.warn("[outdoor] client step failed", e);
        }
        index++;
        if (index < STEPS.size()) timer = STEPS.get(index).delay();
    }
}
