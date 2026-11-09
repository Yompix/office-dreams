package com.yompix.novograd.client;

import com.yompix.novograd.Novograd;
import com.yompix.novograd.client.story.CallClient;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.story.Calls;
import com.yompix.novograd.story.Story;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Только для разработки: -Dnovograd.autotest=calls — звонки героев (story/Calls, client/story/CallClient):
 * звонок Ники с приглашением на гонку (Y → разговор → Y → миссия), присяга по звонку Питерсона (акт VI открывается),
 * звонок «сам по себе» в быстром режиме, «Контакты», бонусы дружбы (Питерсон −1★, бойцы Брика), СМС.
 * Мир — копия Island; снимки run/screenshots/calls_*.png, в логе — [calls].
 */
public final class CallsAutotest {
    private CallsAutotest() {}

    private record Step(int delay, Consumer<MinecraftClient> action) {}

    private static final List<Step> PRE = new ArrayList<>(), STEPS = new ArrayList<>();
    private static int preIndex = -1, preTimer, index = -1, timer;
    private static final String P = "Dev";

    public static void init() {
        String mode = System.getProperty("novograd.autotest");
        if (mode == null || !mode.startsWith("calls")) return;
        System.setProperty("novograd.calls.fast", "true");
        com.yompix.novograd.client.story.YompixSplash.markShown();
        PRE.add(new Step(60, c -> c.createIntegratedServerLoader().start(System.getProperty("novograd.callsworld", "Island"),
                () -> c.setScreen(new com.yompix.novograd.client.menu.LifeMenuScreen()))));
        run(100, c -> { if (c.player != null && c.player.isDead()) c.player.requestRespawn(); });
        run(60, c -> { if (c.currentScreen != null) c.setScreen(null); });
        cmd(5, "gamemode creative " + P);
        cmd(5, "time set 13000");
        cmd(5, "weather clear 99999");
        cmd(5, "effect give " + P + " minecraft:resistance 99999 4 true");
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            var d = Life.get(p);
            d.storyStarted = true;
            d.storyDone = true;
            d.chapter = 5;
            d.mission = 0;
            d.step = 0;
            d.side = "";
            d.flags.remove("end_cop");
            d.flags.remove("oath");
            d.flags.add("end_racer");
            d.age = 22;
            Story.applyAge(p, 22);
            var at = Story.place(p.getServerWorld(), "dt_2");
            if (at != null) {
                p.getServerWorld().getChunk((int) at.x >> 4, (int) at.z >> 4);
                p.teleport(p.getServerWorld(), at.x, Story.topY(p.getServerWorld(), (int) at.x, (int) at.z) + 1, at.z, 90, 5);
            }
            Calls.debugFriend(p, "nika", 5);
            Life.sync(p);
            log("start " + Calls.debugState(p));
        });
        run(80, c -> { if (c.currentScreen != null) c.setScreen(null); });
        // 1. Ники звонит: карточка, Y — разговор, вопрос, Y — гонка
        server(5, s -> log("ring nika_ring -> " + Calls.debugRing(pl(s), "nika_ring")));
        shot(40, "calls_1_ring");
        run(5, c -> CallClient.debugAnswer(true));
        shot(40, "calls_2_talk");
        waitPhase("ASK", 600);
        shot(5, "calls_3_offer");
        run(5, c -> CallClient.debugAnswer(true));
        waitPhase("NONE", 600);
        server(40, s -> log("after nika: side=" + Life.get(pl(s)).side + " " + Calls.debugState(pl(s))));
        run(5, c -> { if (c.currentScreen != null) c.setScreen(null); });
        shot(40, "calls_4_race");
        server(5, s -> com.yompix.novograd.story.SideMissions.cancel(pl(s)));
        // 2. СМС от Брика (из тюрьмы — в сюжете раньше, здесь просто проверка вида)
        server(20, s -> log("sms -> " + Calls.debugRing(pl(s), "brick_rat")));
        // 3. Присяга: Питерсон предлагает акт VI
        server(40, s -> log("ring oath -> " + Calls.debugRing(pl(s), "petrov_oath")));
        shot(30, "calls_5_petrov_ring");
        run(5, c -> CallClient.debugAnswer(true));
        waitPhase("ASK", 600);
        shot(5, "calls_6_oath_offer");
        run(5, c -> CallClient.debugAnswer(true));
        waitPhase("NONE", 600);
        server(40, s -> {
            var d = Life.get(pl(s));
            log("after oath: storyDone=" + d.storyDone + " chapter=" + d.chapter + " mission=" + d.mission + " idle=" + Story.idle(d) + " flags.oath=" + d.flags.contains("oath"));
        });
        run(5, c -> { if (c.currentScreen != null) c.setScreen(null); });
        shot(60, "calls_7_cop_marker");
        // 4. Звонок «сам по себе» (быстрый режим): событие «авария» → реакция
        server(5, s -> {
            Calls.event(pl(s), "crash");
            log("event crash; " + Calls.debugState(pl(s)));
        });
        waitPhase("RING", 1200);
        server(5, s -> log("auto ring: " + Calls.debugState(pl(s))));
        shot(10, "calls_8_auto_ring");
        run(5, c -> CallClient.debugAnswer(false));
        // 5. Контакты и бонусы
        server(20, s -> {
            ServerPlayerEntity p = pl(s);
            Calls.debugFriend(p, "petrov", 4);
            Calls.debugFriend(p, "kirpich", 4);
            Calls.debugFriend(p, "rosa", 3);
            com.yompix.novograd.gta.Wanted.add(p, 0, "тест", 2);
        });
        run(20, c -> c.setScreen(new com.yompix.novograd.client.story.ContactsScreen()));
        shot(40, "calls_9_contacts");
        run(5, c -> {
            var n = new net.minecraft.nbt.NbtCompound();
            n.putString("who", "petrov");
            NovogradClient.send("call_perk", n);
            var k = new net.minecraft.nbt.NbtCompound();
            k.putString("who", "kirpich");
            NovogradClient.send("call_perk", k);
        });
        server(40, s -> log("perks: stars=" + com.yompix.novograd.gta.Wanted.stars(pl(s)) + " guards=" + pl(s).getServerWorld().getEntitiesByClass(
                com.yompix.novograd.entity.BodyguardEntity.class, pl(s).getBoundingBox().expand(40), e -> true).size()));
        shot(10, "calls_10_contacts_after");
        run(5, c -> c.setScreen(null));
        shot(60, "calls_11_guards");
        // 6. Тюнинг со скидкой Ники
        server(5, s -> log("tune price 2000 -> " + Calls.tunePrice(pl(s), 2000)));
        run(40, MinecraftClient::scheduleStop);
        ClientTickEvents.END_CLIENT_TICK.register(CallsAutotest::tick);
    }

    /** Ждать, пока карточка звонка дойдёт до фазы (не дольше max тиков). */
    private static void waitPhase(String phase, int max) {
        final int[] left = {max};
        STEPS.add(new Step(1, c -> {
            if (!CallClient.debugPhase().startsWith(phase) && --left[0] > 0) index--; // повторить этот шаг
            else log("phase " + CallClient.debugPhase() + " (ждали " + phase + ")");
        }));
    }

    private static void log(String s) {
        Novograd.LOG.info("[calls] {}", s);
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
            if (s != null) s.execute(() -> {
                try {
                    action.accept(s);
                } catch (RuntimeException e) {
                    Novograd.LOG.warn("[calls] server step failed", e);
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
            Novograd.LOG.warn("[calls] client step failed", e);
        }
        index++;
        if (index < STEPS.size()) timer = STEPS.get(index).delay();
    }
}
