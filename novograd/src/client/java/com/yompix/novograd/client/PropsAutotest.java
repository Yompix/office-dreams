package com.yompix.novograd.client;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.Novograd;
import com.yompix.novograd.block.PropBlock;
import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.story.Story;
import com.yompix.novograd.world.Destruction;
import com.yompix.novograd.world.Fires;
import com.yompix.novograd.world.Props;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Только для разработки: -Dnovograd.autotest=props — живая улица: расстановка предметов, машина сносит фонари и гидрант,
 * стрельба по стёклам и баллону, пожар, автомат с газировкой, починка. Снимки — run/screenshots/props_*.png, итоги — [props] в логе.
 * Улица: -Dnovograd.propsx=221 -Dnovograd.propsz=-694 (тротуар у бордюра, едем на юг).
 */
public final class PropsAutotest {
    private PropsAutotest() {}

    private record Step(int delay, Consumer<MinecraftClient> action) {}

    private static final List<Step> PRE = new ArrayList<>(), STEPS = new ArrayList<>();
    private static int preIndex = -1, preTimer, index = -1, timer;
    private static final String P = "Dev";
    private static final int X = Integer.getInteger("novograd.propsx", 221), Z = Integer.getInteger("novograd.propsz", -686);
    private static final long[] TICK = new long[1];
    private static int glassY;

    public static void init() {
        String mode = System.getProperty("novograd.autotest");
        if (mode == null || !mode.startsWith("props")) return;
        com.yompix.novograd.client.story.YompixSplash.markShown();
        PRE.add(new Step(60, c -> c.createIntegratedServerLoader().start(System.getProperty("novograd.propsworld", "Fresh2"),
                () -> c.setScreen(new com.yompix.novograd.client.menu.LifeMenuScreen()))));
        server(1, s -> {
            var d = Life.get(pl(s));
            d.storyStarted = true;
            d.storyDone = true;
        });
        run(100, c -> { if (c.player != null && c.player.isDead()) c.player.requestRespawn(); });
        run(60, c -> { if (c.currentScreen != null) c.setScreen(null); });
        cmd(5, "gamemode survival " + P);
        cmd(5, "time set 6000");
        cmd(5, "gamerule doDaylightCycle false");
        cmd(5, "weather clear");
        cmd(5, "effect give " + P + " minecraft:resistance 99999 4 true");
        cmd(5, "effect give " + P + " minecraft:regeneration 99999 2 true");
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            Life.get(p).money = 5000;
            Life.get(p).dirty = 0;
            ServerWorld w = p.getServerWorld();
            w.getChunk(X >> 4, Z >> 4);
            p.teleport(w, X + 0.5, Story.topY(w, X, Z) + 0.2, Z + 0.5, 0, 10);
        });
        // ждём, пока загрузятся и обставятся чанки вокруг
        for (int i = 0; i < 5; i++) server(60, s -> Novograd.LOG.info("[props] queue {} props near {}", Props.queued(), countProps(pl(s))));
        server(5, s -> { // без помех: прохожих и бандитов вокруг убираем
            ServerPlayerEntity p = pl(s);
            for (var e : p.getServerWorld().getEntitiesByClass(com.yompix.novograd.entity.CitizenEntity.class, p.getBoundingBox().expand(80), e -> true)) e.discard();
        });
        run(5, c -> c.options.setPerspective(Perspective.THIRD_PERSON_BACK));
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            ServerWorld w = p.getServerWorld();
            StringBuilder sb = new StringBuilder();
            for (int z = Z; z < Z + 34; z++)
                for (int x = X - 1; x <= X + 1; x++) {
                    BlockPos b = new BlockPos(x, Story.topY(w, x, z), z);
                    var st = w.getBlockState(b.down());
                    if (!st.isOf(ModContent.SIDEWALK) && !st.isOf(ModContent.ASPHALT)) sb.append(x).append(',').append(z).append('=').append(net.minecraft.registry.Registries.BLOCK.getId(st.getBlock()).getPath()).append(' ');
                }
            Novograd.LOG.info("[props] street: {}", sb);
            p.teleport(w, X - 5.5, Story.topY(w, X, Z) + 3.5, Z - 1.5, -25, 22);
        });
        shot(20, "props_street");
        // --- 0. витрина всех предметов на асфальте
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            ServerWorld w = p.getServerWorld();
            int gx = X - 8, gz = Z + 4;
            int y = Story.topY(w, gx, gz);
            var list = List.of(Props.VENDING, Props.PAYPHONE, Props.NEWS_STAND, Props.HOTDOG_CART, Props.ICECREAM_CART, Props.PROPANE, Props.CONE);
            for (int i = 0; i < list.size(); i++)
                w.setBlockState(new BlockPos(gx, y, gz + i * 2), list.get(i).getDefaultState().with(net.minecraft.block.HorizontalFacingBlock.FACING, net.minecraft.util.math.Direction.WEST));
            p.teleport(w, gx - 6.5, y, gz + 6.5, -90, 8);
        });
        run(5, c -> c.options.setPerspective(Perspective.FIRST_PERSON));
        shot(25, "props_gallery");
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            ServerWorld w = p.getServerWorld();
            int gx = X - 8, gz = Z + 4, y = Story.topY(w, gx, gz) - 1;
            for (int i = 0; i < 7; i++) w.setBlockState(new BlockPos(gx, y, gz + i * 2), Blocks.AIR.getDefaultState());
        });
        run(5, c -> c.options.setPerspective(Perspective.THIRD_PERSON_BACK));
        // --- 1. машина игрока по тротуару: фонари, гидрант, урны, скамейки
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            ServerWorld w = p.getServerWorld();
            Vec3d at = new Vec3d(X + 0.5, Story.topY(w, X, Z), Z + 0.5);
            CarEntity car = com.yompix.novograd.gta.FunUtil.car(w, at, 0, 9);
            car.setOwner(p.getUuid());
            p.teleport(w, at.x, at.y, at.z, 0, 15);
            p.startRiding(car, true);
        });
        run(10, c -> KeyBinding.setKeyPressed(InputUtil.fromTranslationKey(c.options.forwardKey.getBoundKeyTranslationKey()), true));
        for (int i = 0; i < 10; i++) {
            final int n = i;
            shot(5, "props_drive_" + n);
            server(1, s -> {
                ServerPlayerEntity p = pl(s);
                Novograd.LOG.info("[props] drive t={} pos {} speed {} broken {} fires {} debris {}", n, p.getBlockPos().toShortString(),
                        p.getVehicle() instanceof CarEntity car ? String.format("%.2f", car.hitSpeed()) : "-", Destruction.brokenCount(p.getServerWorld()), Fires.count(),
                        p.getServerWorld().getEntitiesByType(Props.DEBRIS, p.getBoundingBox().expand(60), e -> true).size());
            });
        }
        run(1, c -> KeyBinding.setKeyPressed(InputUtil.fromTranslationKey(c.options.forwardKey.getBoundKeyTranslationKey()), false));
        run(1, c -> KeyBinding.setKeyPressed(InputUtil.fromTranslationKey(c.options.backKey.getBoundKeyTranslationKey()), true));
        run(25, c -> KeyBinding.setKeyPressed(InputUtil.fromTranslationKey(c.options.backKey.getBoundKeyTranslationKey()), false));
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            ServerWorld w = p.getServerWorld();
            Novograd.LOG.info("[props] after drive: {}", Destruction.debugBroken(w));
            p.stopRiding();
            p.teleport(w, X - 4.5, Story.topY(w, X - 5, Z + 32) + 3, Z + 32.5, 155, 20); // оглянуться на поваленное
        });
        run(5, c -> c.options.setPerspective(Perspective.FIRST_PERSON));
        shot(10, "props_aftermath");
        shot(30, "props_aftermath2");
        // --- 2. стекло и выстрелы: стена из стекла на дороге, рядом баллоны и урна
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            ServerWorld w = p.getServerWorld();
            int gx = X - 10, gz = Z + 20;
            int y = Story.topY(w, gx, gz);
            glassY = y;
            for (int dx = -2; dx <= 2; dx++) for (int dy = 0; dy < 3; dy++) w.setBlockState(new BlockPos(gx + dx, y + dy, gz), Blocks.GLASS_PANE.getDefaultState());
            w.setBlockState(new BlockPos(gx + 5, y, gz), Props.PROPANE.getDefaultState());
            w.setBlockState(new BlockPos(gx + 6, y, gz + 1), Props.PROPANE.getDefaultState());
            w.setBlockState(new BlockPos(gx + 4, y, gz + 2), Blocks.COMPOSTER.getDefaultState());
            w.setBlockState(new BlockPos(gx + 7, y, gz - 1), Blocks.OAK_FENCE.getDefaultState());
            p.teleport(w, gx + 1.5, y, gz - 9.5, 0, 0);
            p.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, ItemStack(ModContent.PISTOL));
        });
        shot(20, "props_glass_before");
        for (int i = 0; i < 5; i++) {
            final int n = i;
            server(3, s -> {
                ServerPlayerEntity p = pl(s);
                Vec3d t = new Vec3d(X - 10 - 2 + n + 0.5, glassY + 1.5 - (n % 2), Z + 20 + 0.5);
                com.yompix.novograd.gta.Weapons.fire(p.getServerWorld(), p, p.getEyePos(), t.subtract(p.getEyePos()).normalize(), 6, 0, 60, 1, ModContent.GUNSHOT, 1f);
            });
        }
        shot(4, "props_glass_after");
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            Novograd.LOG.info("[props] after glass: broken {}", Destruction.brokenCount(p.getServerWorld()));
            Vec3d t = new Vec3d(X - 10 + 5.5, glassY + 0.5, Z + 20 + 0.5);
            com.yompix.novograd.gta.Weapons.fire(p.getServerWorld(), p, p.getEyePos(), t.subtract(p.getEyePos()).normalize(), 6, 0, 60, 1, ModContent.GUNSHOT, 1f);
        });
        shot(3, "props_boom");
        server(1, s -> Novograd.LOG.info("[props] boom1 fires {} broken {}", Fires.count(), Destruction.brokenCount(pl(s).getServerWorld())));
        shot(15, "props_boom2");
        shot(50, "props_fire");
        server(5, s -> Novograd.LOG.info("[props] after boom fires {} broken {} wanted {}: {}", Fires.count(), Destruction.brokenCount(pl(s).getServerWorld()),
                com.yompix.novograd.gta.Wanted.stars(pl(s)), Destruction.debugBroken(pl(s).getServerWorld())));
        // --- 3. автомат с газировкой, газета
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            ServerWorld w = p.getServerWorld();
            p.setHealth(10);
            Props.use(PropBlock.Kind.VENDING, w.getBlockState(p.getBlockPos()), w, p.getBlockPos(), p);
            Props.use(PropBlock.Kind.NEWS_STAND, w.getBlockState(p.getBlockPos()), w, p.getBlockPos(), p);
            Novograd.LOG.info("[props] vending health {} money {}", p.getHealth(), Life.get(p).money);
        });
        shot(10, "props_chat");
        // --- 4. починка: все сроки вышли, отходим подальше
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            ServerWorld w = p.getServerWorld();
            Destruction.expireAll(w);
            p.teleport(w, X + 40.5, Story.topY(w, X + 40, Z) + 1, Z + 20.5, 90, 10);
        });
        server(80, s -> Novograd.LOG.info("[props] after restore broken {} fires {}: {}", Destruction.brokenCount(pl(s).getServerWorld()), Fires.count(),
                Destruction.debugBroken(pl(s).getServerWorld())));
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            ServerWorld w = p.getServerWorld();
            p.teleport(w, X - 5.5, Story.topY(w, X, Z) + 4, Z + 2.5, -30, 22);
        });
        shot(30, "props_restored");
        server(5, s -> Novograd.LOG.info("[props] mspt {} ms", String.format("%.2f", s.getAverageTickTime())));
        run(20, MinecraftClient::scheduleStop);
        ClientTickEvents.END_CLIENT_TICK.register(PropsAutotest::tick);
    }

    private static net.minecraft.item.ItemStack ItemStack(net.minecraft.item.Item i) {
        return new net.minecraft.item.ItemStack(i);
    }

    private static int countProps(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        int n = 0;
        BlockPos o = p.getBlockPos();
        for (int dx = -48; dx <= 48; dx++)
            for (int dz = -48; dz <= 48; dz++) {
                int x = o.getX() + dx, z = o.getZ() + dz;
                var st = w.getBlockState(new BlockPos(x, Story.topY(w, x, z) - 1, z));
                if (st.getBlock() instanceof PropBlock || st.isOf(ModContent.STREET_LAMP) || st.isOf(Blocks.COMPOSTER) || st.isOf(Blocks.RED_NETHER_BRICK_WALL)) n++;
            }
        return n;
    }

    // ------------------------------------------------------------------ помощники
    private static ServerPlayerEntity pl(IntegratedServer s) {
        return s.getPlayerManager().getPlayer(P);
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
                    Novograd.LOG.warn("[props] server step failed", e);
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
            Novograd.LOG.warn("[props] client step failed", e);
        }
        index++;
        if (index < STEPS.size()) timer = STEPS.get(index).delay();
    }
}
