package com.yompix.novograd.client;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.Novograd;
import com.yompix.novograd.client.screen.GtaInventoryScreen;
import com.yompix.novograd.client.story.CineRec;
import com.yompix.novograd.gta.Arsenal;
import com.yompix.novograd.life.Life;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Только разработка: -Dnovograd.autotest=uiinv — проход по инвентарю «как в GTA» (E): вкладки, наведение, перенос
 * предмета, крафт, затем сундук и экран смерти. С -Dnovograd.rec=путь.mp4 — запись видео. Лог «[uiinv]».
 */
public final class UiInvAutotest {
    private UiInvAutotest() {}

    private record Step(int delay, Consumer<MinecraftClient> action) {}

    private static final List<Step> PRE = new ArrayList<>(), STEPS = new ArrayList<>();
    private static int preIndex = -1, preTimer, index = -1, timer;
    private static final String P = "Dev";
    /** Плавное движение мыши к цели (в координатах интерфейса). */
    private static double mx = 200, my = 150, tx = 200, ty = 150;

    public static void init() {
        String mode = System.getProperty("novograd.autotest");
        if (mode == null || !mode.equals("uiinv")) return;
        com.yompix.novograd.client.story.YompixSplash.markShown();
        PRE.add(new Step(60, c -> c.createIntegratedServerLoader().start(System.getProperty("novograd.uiworld", "Island"), () -> c.setScreen(new com.yompix.novograd.client.menu.LifeMenuScreen()))));
        run(100, c -> { if (c.player != null && c.player.isDead()) c.player.requestRespawn(); });
        run(5, c -> { if (c.currentScreen != null) c.setScreen(null); });
        cmd(5, "gamemode survival " + P);
        cmd(5, "time set 6000");
        cmd(5, "weather clear");
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            var d = Life.get(p);
            d.storyStarted = true;
            d.storyDone = true;
            d.money = 12850;
            d.dirty = 3400;
            Life.sync(p);
            var inv = p.getInventory();
            inv.clear();
            inv.setStack(0, new ItemStack(ModContent.PISTOL));
            inv.setStack(1, new ItemStack(Arsenal.KNIFE));
            inv.setStack(2, new ItemStack(Arsenal.MEDKIT, 3));
            inv.setStack(3, new ItemStack(ModContent.BURGER, 4));
            inv.setStack(4, new ItemStack(ModContent.PHONE));
            inv.setStack(9, new ItemStack(ModContent.AMMO, 48));
            inv.setStack(10, new ItemStack(Arsenal.GRENADE, 3));
            inv.setStack(11, new ItemStack(ModContent.COLA, 6));
            inv.setStack(12, new ItemStack(ModContent.SUIT));
            inv.setStack(13, new ItemStack(Items.LEATHER_CHESTPLATE));
            inv.setStack(14, new ItemStack(Items.OAK_PLANKS, 16));
            inv.setStack(15, new ItemStack(ModContent.BANK_CARD));
            inv.setStack(20, new ItemStack(ModContent.SHOPPING_BAG, 2));
            inv.setStack(22, new ItemStack(Arsenal.ARMOR));
            inv.armor.set(3, new ItemStack(Items.LEATHER_HELMET));
            p.playerScreenHandler.sendContentUpdates();
            // сундук перед игроком — для второй части
            var w = p.getServerWorld();
            var at = p.getBlockPos().offset(p.getHorizontalFacing(), 2);
            w.setBlockState(at, net.minecraft.block.Blocks.CHEST.getDefaultState());
            if (w.getBlockEntity(at) instanceof net.minecraft.block.entity.ChestBlockEntity ch) {
                ch.setStack(0, new ItemStack(ModContent.SMG));
                ch.setStack(4, new ItemStack(Items.GOLD_INGOT, 12));
                ch.setStack(13, new ItemStack(ModContent.PIZZA, 2));
            }
            chestPos = at;
        });
        if (System.getProperty("novograd.rec") != null) cmd(5, "tick rate " + System.getProperty("novograd.rectps", "8"));
        run(20, c -> {
            if (System.getProperty("novograd.rec") != null) CineRec.start(System.getProperty("novograd.rec"), 30);
        });
        run(10, c -> c.setScreen(new InventoryScreen(c.player)));
        run(5, c -> Novograd.LOG.info("[uiinv] screen={}", c.currentScreen == null ? null : c.currentScreen.getClass().getSimpleName()));
        // наведение на пистолет (быстрый слот 1), нож, аптечку
        hoverSlot(24, 36);
        hoverSlot(22, 37);
        hoverSlot(22, 38);
        // вкладки
        for (int t = 1; t <= 5; t++) {
            final int tt = t;
            run(1, c -> move(c, tabX(c, tt) + 28, tabY(c) + 7));
            run(16, c -> click(c, tabX(c, tt) + 28, tabY(c) + 7));
            if (t == 1) hoverSlot(22, 9);
            if (t == 2) hoverSlot(22, 38);
            if (t == 3) hoverSlot(22, 21);
            if (t == 5) hoverSlot(20, 1);
        }
        run(1, c -> move(c, tabX(c, 0) + 28, tabY(c) + 7));
        run(14, c -> click(c, tabX(c, 0) + 28, tabY(c) + 7));
        // перенести бургер из быстрых слотов в рюкзак (клик — взять, клик — положить)
        hoverSlot(14, 39);
        run(4, c -> clickSlot(c, 39));
        moveSlot(16, 27);
        run(6, c -> clickSlot(c, 27));
        hoverSlot(20, 27);
        run(10, c -> Novograd.LOG.info("[uiinv] slot27={} slot39={}", c.player.playerScreenHandler.getSlot(27).getStack(), c.player.playerScreenHandler.getSlot(39).getStack()));
        // броня: кожаная куртка (рюкзак 13) — в слот груди (6); вторая рука: аптечка (быстрый слот 38) — в слот 45
        run(6, c -> clickSlot(c, 13));
        run(6, c -> clickSlot(c, 6));
        run(6, c -> clickSlot(c, 38));
        run(6, c -> clickSlot(c, 45));
        // крафт 2×2 на вкладке «Крафт»: 4 доски по одной правой кнопкой → верстак → в пустую ячейку рюкзака (30)
        run(6, c -> click(c, tabX(c, 5) + 28, tabY(c) + 7));
        run(6, c -> clickSlot(c, 14));
        for (int i = 1; i <= 4; i++) {
            final int k = i;
            run(4, c -> clickSlot(c, k, 1));
        }
        run(6, c -> clickSlot(c, 14));
        run(6, c -> Novograd.LOG.info("[uiinv] craft result={}", c.player.playerScreenHandler.getSlot(0).getStack()));
        run(6, c -> clickSlot(c, 0));
        run(6, c -> clickSlot(c, 30));
        run(10, c -> Novograd.LOG.info("[uiinv] armor6={} offhand45={} slot30={} slot14={} cursor={}", c.player.playerScreenHandler.getSlot(6).getStack(),
                c.player.playerScreenHandler.getSlot(45).getStack(), c.player.playerScreenHandler.getSlot(30).getStack(),
                c.player.playerScreenHandler.getSlot(14).getStack(), c.player.playerScreenHandler.getCursorStack()));
        run(6, c -> click(c, tabX(c, 0) + 28, tabY(c) + 7));
        run(10, c -> c.setScreen(null));
        // сундук
        run(10, c -> {
            if (chestPos != null && c.interactionManager != null)
                c.interactionManager.interactBlock(c.player, net.minecraft.util.Hand.MAIN_HAND,
                        new net.minecraft.util.hit.BlockHitResult(net.minecraft.util.math.Vec3d.ofCenter(chestPos), net.minecraft.util.math.Direction.UP, chestPos, false));
        });
        run(10, c -> Novograd.LOG.info("[uiinv] chest screen={}", c.currentScreen == null ? null : c.currentScreen.getClass().getSimpleName()));
        run(40, c -> {});
        run(5, c -> c.setScreen(null));
        // экран смерти
        cmd(10, "kill " + P);
        run(60, c -> Novograd.LOG.info("[uiinv] death screen={}", c.currentScreen == null ? null : c.currentScreen.getClass().getSimpleName()));
        run(10, c -> {
            if (CineRec.on) CineRec.stop();
            if (c.player != null) c.player.requestRespawn();
        });
        cmd(20, "tick rate 20");
        run(40, c -> Novograd.LOG.info("[uiinv] after respawn: alive={} hp={} screen={}", c.player != null && !c.player.isDead(), c.player == null ? -1 : c.player.getHealth(),
                c.currentScreen == null ? null : c.currentScreen.getClass().getSimpleName()));
        run(20, c -> Novograd.LOG.info("[uiinv] done"));
        run(10, MinecraftClient::scheduleStop);
        ClientTickEvents.END_CLIENT_TICK.register(UiInvAutotest::tick);
        ClientTickEvents.END_CLIENT_TICK.register(UiInvAutotest::mouse);
    }

    private static net.minecraft.util.math.BlockPos chestPos;

    private static int left(MinecraftClient c) {
        return (c.getWindow().getScaledWidth() - 368) / 2;
    }

    private static int top(MinecraftClient c) {
        int h = c.getWindow().getScaledHeight();
        return Math.max((h - 222) / 2, Math.min(14, h - 222)); // как GtaInventoryScreen.init
    }

    private static int tabX(MinecraftClient c, int i) {
        return left(c) + 8 + i * 59;
    }

    private static int tabY(MinecraftClient c) {
        return top(c) + 32;
    }

    private static void hoverSlot(int delay, int slot) {
        moveSlot(delay, slot);
    }

    private static void moveSlot(int delay, int slot) {
        run(1, c -> {
            var s = c.player.playerScreenHandler.getSlot(slot);
            move(c, left(c) + s.x + 8, top(c) + s.y + 8);
        });
        run(delay, c -> {});
    }

    private static void move(MinecraftClient c, double x, double y) {
        tx = x;
        ty = y;
    }

    /** Каждый тик мышь едет к цели (видно, как курсор ходит по окну). */
    private static void mouse(MinecraftClient c) {
        if (c.currentScreen == null) return;
        mx += (tx - mx) * 0.45;
        my += (ty - my) * 0.45;
        double k = c.getWindow().getScaleFactor();
        GLFW.glfwSetCursorPos(c.getWindow().getHandle(), mx * k, my * k);
    }

    private static void click(MinecraftClient c, double x, double y) {
        if (c.currentScreen == null) return;
        c.currentScreen.mouseClicked(x, y, 0);
        c.currentScreen.mouseReleased(x, y, 0);
    }

    private static void clickSlot(MinecraftClient c, int slot) {
        clickSlot(c, slot, 0);
    }

    private static void clickSlot(MinecraftClient c, int slot, int button) {
        var s = c.player.playerScreenHandler.getSlot(slot);
        if (c.currentScreen == null) return;
        double x = left(c) + s.x + 8, y = top(c) + s.y + 8;
        c.currentScreen.mouseClicked(x, y, button);
        c.currentScreen.mouseReleased(x, y, button);
    }

    private static ServerPlayerEntity pl(IntegratedServer s) {
        return s.getPlayerManager().getPlayer(P);
    }

    private static void cmd(int delay, String command) {
        server(delay, s -> s.getCommandManager().executeWithPrefix(s.getCommandSource(), command));
    }

    private static void server(int delay, Consumer<IntegratedServer> action) {
        STEPS.add(new Step(delay, c -> {
            IntegratedServer s = c.getServer();
            if (s != null) s.execute(() -> action.accept(s));
        }));
    }

    private static void run(int delay, Consumer<MinecraftClient> action) {
        STEPS.add(new Step(delay, action));
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
        if (STEPS.isEmpty() || c.world == null) return;
        if (c.player == null && index < 0) return;
        if (index < 0) {
            index = 0;
            timer = STEPS.get(0).delay();
        }
        if (index >= STEPS.size()) return;
        if (--timer > 0) return;
        try {
            STEPS.get(index).action().accept(c);
        } catch (RuntimeException e) {
            Novograd.LOG.warn("[uiinv] step failed", e);
        }
        index++;
        if (index < STEPS.size()) timer = STEPS.get(index).delay();
    }
}
