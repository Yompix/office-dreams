package com.yompix.novograd.client;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.Novograd;
import com.yompix.novograd.client.anim.PlayerAnim;
import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.life.Looks;
import com.yompix.novograd.story.Story;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Только разработка, 0.8 — ролики для проверки «на глаз» (запись кадр за кадром, -Dnovograd.rec=путь.mp4):
 * <ul>
 *   <li>-Dnovograd.autotest=animshow — анимации игрока от третьего лица: стоит/дышит, шаг, бег, спринт, присед, прыжки,
 *       разворот на месте, телефон, прицел, жесты V (помахать, два танца, показать, сесть, фейспалм, честь), падение,
 *       вода, за рулём;</li>
 *   <li>-Dnovograd.autotest=lookshow — герои сюжета в ряд и перед ними все обычные скины прохожих (40 случайных +
 *       ролевые): облёт камерой; в лог — ближайший к герою прохожий (как в lookalikeTest).</li>
 * </ul>
 */
public final class ShowAutotest {
    private ShowAutotest() {}

    private record Step(int delay, Consumer<MinecraftClient> action) {}

    private static final List<Step> STEPS = new ArrayList<>();
    private static int index = -1, timer;
    private static final String P = "Dev";
    private static Vec3d base;
    private static Runnable perTick;

    public static void init() {
        String mode = System.getProperty("novograd.autotest");
        if (mode == null || !(mode.equals("animshow") || mode.equals("lookshow") || mode.equals("gridshow"))) return;
        com.yompix.novograd.client.story.YompixSplash.markShown();
        com.yompix.novograd.client.story.CineRec.armed = false;
        run(60, ShowAutotest::openWorld);
        run(160, c -> { if (c.currentScreen != null) c.setScreen(null); });
        cmd(5, "gamemode creative " + P);
        cmd(5, "time set 6000");
        cmd(5, "weather clear");
        cmd(5, "gamerule doDaylightCycle false");
        cmd(5, "gamerule doMobSpawning false");
        cmd(5, "clear " + P);
        cmd(5, "effect clear " + P);
        if (mode.equals("animshow")) animshow();
        else if (mode.equals("gridshow")) cityshow();
        else lookshow();
        ClientTickEvents.END_CLIENT_TICK.register(ShowAutotest::tick);
    }

    // ===================================================================== анимации игрока
    private static void key(MinecraftClient c, net.minecraft.client.option.KeyBinding k, boolean on) {
        k.setPressed(on);
    }

    private static void animshow() {
        server(10, s -> {
            ServerPlayerEntity p = pl(s);
            var w = p.getServerWorld();
            Vec3d at = Story.place(w, "park_center");
            if (at == null) at = p.getPos();
            BlockPos g = Story.ground(w, at.add(6, 0, 0));
            base = new Vec3d(at.x + 6, g.getY(), at.z);
            p.teleport(w, base.x, base.y, base.z, 90, 5);
            p.getAbilities().flying = false;
            p.sendAbilitiesUpdate();
        });
        run(40, c -> {
            c.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
            c.options.hudHidden = true;
            c.options.getFov().setValue(42); // крупнее: камера от третьего лица в 4 блоках
        });
        cmd(5, "tick rate " + System.getProperty("novograd.rectps", "4"));
        run(5, c -> rec(c));
        label(70, "стоит: дыхание");
        run(1, c -> key(c, c.options.forwardKey, true));
        label(80, "шаг");
        run(1, c -> key(c, c.options.sprintKey, true));
        label(80, "спринт");
        run(1, c -> { key(c, c.options.sprintKey, false); key(c, c.options.sneakKey, true); });
        label(70, "присед");
        run(1, c -> { key(c, c.options.sneakKey, false); key(c, c.options.forwardKey, false); });
        label(30, "остановка");
        for (int i = 0; i < 2; i++) {
            run(1, c -> key(c, c.options.jumpKey, true));
            run(4, c -> key(c, c.options.jumpKey, false));
            label(26, "прыжок");
        }
        // разворот на месте — 120° за секунду
        for (int i = 0; i < 20; i++) run(1, c -> { if (c.player != null) c.player.setYaw(c.player.getYaw() + 6); });
        label(30, "разворот");
        run(1, c -> PlayerAnim.play("phone"));
        label(70, "телефон");
        run(1, c -> PlayerAnim.play("none"));
        server(1, s -> pl(s).getInventory().setStack(pl(s).getInventory().selectedSlot, new net.minecraft.item.ItemStack(ModContent.PISTOL)));
        label(60, "оружие");
        server(1, s -> pl(s).getInventory().setStack(pl(s).getInventory().selectedSlot, net.minecraft.item.ItemStack.EMPTY));
        label(10, "");
        String[] em = {"wave", "dance", "dance2", "point", "sit", "facepalm", "salute"};
        int[] len = {55, 90, 90, 50, 70, 50, 50};
        for (int i = 0; i < em.length; i++) {
            String e = em[i];
            run(1, c -> PlayerAnim.play(e));
            label(len[i], "жест " + e);
        }
        run(1, c -> PlayerAnim.play("none"));
        // падение с высоты
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            p.teleport(p.getServerWorld(), p.getX(), p.getY() + 14, p.getZ(), p.getYaw(), 10);
        });
        label(60, "падение");
        // вода: ближайшая вода от пляжа
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            var w = p.getServerWorld();
            Vec3d b = Story.place(w, "beach_bar");
            if (b == null) return;
            for (int r = 8; r <= 160; r += 4)
                for (int[] d : new int[][]{{-1, 0}, {0, -1}, {1, 0}, {0, 1}}) {
                    BlockPos q = BlockPos.ofFloored(b.x + d[0] * r, 62, b.z + d[1] * r);
                    w.getChunk(q);
                    int top = w.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, q.getX(), q.getZ());
                    BlockPos sfc = new BlockPos(q.getX(), top - 1, q.getZ());
                    if (w.getFluidState(sfc).isIn(net.minecraft.registry.tag.FluidTags.WATER) && w.getFluidState(sfc.down(2)).isIn(net.minecraft.registry.tag.FluidTags.WATER)) {
                        p.teleport(w, sfc.getX() + 0.5, sfc.getY() - 0.4, sfc.getZ() + 0.5, p.getYaw(), 10);
                        return;
                    }
                }
        });
        run(1, c -> key(c, c.options.jumpKey, true)); // держится на воде
        label(80, "в воде");
        run(1, c -> key(c, c.options.jumpKey, false));
        // за рулём
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            var w = p.getServerWorld();
            Vec3d at = base;
            p.teleport(w, at.x, at.y, at.z, 90, 5);
            CitizenEntity none = null;
            var car = new com.yompix.novograd.entity.CarEntity(ModContent.CAR, w);
            car.refreshPositionAndAngles(at.x, at.y, at.z, 90, 0);
            w.spawnEntity(car);
            p.startRiding(car, true);
        });
        run(20, c -> c.options.setPerspective(Perspective.THIRD_PERSON_FRONT));
        run(1, c -> key(c, c.options.forwardKey, true));
        run(30, c -> key(c, c.options.leftKey, true));
        run(25, c -> { key(c, c.options.leftKey, false); key(c, c.options.rightKey, true); });
        run(25, c -> key(c, c.options.rightKey, false));
        label(20, "за рулём");
        run(1, c -> key(c, c.options.forwardKey, false));
        label(20, "конец");
        run(1, c -> c.options.getFov().setValue(70));
        finish();
    }

    // ===================================================================== 0.9: прогресс, «Империя», «Сеть», события
    private static void screen(int delay, java.util.function.Supplier<net.minecraft.client.gui.screen.Screen> sc, String what) {
        run(delay, c -> c.setScreen(sc.get()));
        label(1, what);
    }

    private static void tp(String place, double dx, double dz, float yaw, float pitch) {
        server(1, s -> {
            ServerPlayerEntity p = pl(s);
            var w = p.getServerWorld();
            Vec3d at = Story.place(w, place);
            if (at == null) return;
            BlockPos g = Story.ground(w, at.add(dx, 0, dz));
            p.teleport(w, at.x + dx, g.getY(), at.z + dz, yaw, pitch);
        });
    }

    private static void cityshow() {
        server(10, s -> {
            ServerPlayerEntity p = pl(s);
            var d = com.yompix.novograd.life.Life.get(p);
            d.money = Math.max(d.money, 400_000);
            d.flags.add("biz:0");
            d.flags.add("biz:1");
            d.flags.add("bizmgr:0");
            com.yompix.novograd.life.Life.sync(p);
            p.getAbilities().flying = false;
            p.sendAbilitiesUpdate();
        });
        cmd(5, "gamemode survival " + P);
        cmd(5, "effect give " + P + " minecraft:resistance 99999 4 true");
        cmd(5, "time set 14500");
        tp("dt_1", 0, 0, 0, 10);
        run(60, c -> {
            c.options.setPerspective(Perspective.FIRST_PERSON);
            c.options.hudHidden = false;
        });
        cmd(5, "tick rate " + System.getProperty("novograd.rectps", "4"));
        run(5, c -> rec(c));
        screen(10, com.yompix.novograd.client.city.ProgressScreen::new, "прогресс");
        screen(80, com.yompix.novograd.client.city.EmpireScreen::new, "империя");
        run(80, c -> c.setScreen(new com.yompix.novograd.client.screen.MapScreen()));
        label(1, "карта");
        run(40, c -> { if (c.currentScreen instanceof com.yompix.novograd.client.screen.MapScreen m) m.showActs(); });
        run(70, c -> c.setScreen(null));
        // «Сеть»: светофор перекрёстка
        server(1, s -> {
            ServerPlayerEntity p = pl(s);
            int[] sig = com.yompix.novograd.gta.TrafficLights.nearestSignal(p.getServerWorld(), p.getX(), p.getZ());
            Novograd.LOG.info("[show] перекрёсток рядом: {}", sig == null ? "нет" : sig[0] + "," + sig[1]);
            if (sig != null) p.teleport(p.getServerWorld(), sig[0] + 9.5, p.getY(), sig[1] + 9.5, 135, 15);
        });
        run(20, c -> {
            com.yompix.novograd.client.city.GridScreen.autoWin = true;
            c.setScreen(new com.yompix.novograd.client.city.GridScreen());
        });
        label(1, "сеть");
        run(30, c -> { if (c.currentScreen instanceof com.yompix.novograd.client.city.GridScreen g) Novograd.LOG.info("[show] взлом светофора: {}", g.autoHack("lights")); });
        label(90, "светофоры красные");
        // щиток района — блэкаут
        run(5, c -> c.setScreen(new com.yompix.novograd.client.city.GridScreen()));
        run(30, c -> { if (c.currentScreen instanceof com.yompix.novograd.client.city.GridScreen g) Novograd.LOG.info("[show] взлом щитка: {}", g.autoHack("district")); });
        label(110, "блэкаут");
        server(1, s -> Novograd.LOG.info("[show] сеть: уровень {} опыт {}", com.yompix.novograd.gta.Grid.level(com.yompix.novograd.life.Life.get(pl(s))),
                com.yompix.novograd.gta.Grid.xp(com.yompix.novograd.life.Life.get(pl(s)))));
        // «Ночь музыки»
        tp("park_center", 0, 6, 180, 10);
        cmd(20, "event festival");
        label(110, "ночь музыки");
        cmd(5, "event stop");
        // шторм
        cmd(5, "time set 6000");
        tp("dt_2", 0, 0, 0, -10);
        cmd(20, "event storm");
        label(120, "шторм");
        cmd(5, "event stop");
        // рейд на свой бизнес
        server(5, s -> com.yompix.novograd.gta.Empire.forceRaid(pl(s), 0));
        tp("bar_door", 6, 6, 135, 10);
        label(120, "рейд");
        server(1, s -> {
            var p = pl(s);
            java.util.List<String> names = new java.util.ArrayList<>();
            int[][] parts = com.yompix.novograd.gta.Progress.parts(p, names);
            Novograd.LOG.info("[show] прогресс {}% {}", com.yompix.novograd.gta.Progress.percent(parts), names);
            Novograd.LOG.info("[show] рейдеров рядом: {}", p.getServerWorld().getEntitiesByClass(CitizenEntity.class, p.getBoundingBox().expand(30),
                    c -> c.hasCustomName() && c.getCustomName().getString().equals("Рейдер")).size());
        });
        cmd(1, "effect clear " + P);
        finish();
    }

    // ===================================================================== герои и прохожие
    private static void lookshow() {
        server(10, s -> {
            ServerPlayerEntity p = pl(s);
            var w = p.getServerWorld();
            Vec3d at = Story.place(w, System.getProperty("novograd.lookplace", "beach_lot"));
            if (at == null) at = p.getPos();
            base = at;
            int n = 0;
            // площадка пустая: прохожих рядом (не героев) убираем — только в тестовом мире
            for (CitizenEntity o : w.getEntitiesByClass(CitizenEntity.class, new net.minecraft.util.math.Box(at.add(-30, -6, -20), at.add(30, 8, 30)),
                    o -> o.getStoryId().isEmpty())) o.discard();
            // ряд героев (z = 0) и ряды прохожих за ними (z = +3, +6, +9)
            for (int i = 0; i < Looks.STORY_IDS.size(); i++) {
                String id = Looks.STORY_IDS.get(i);
                CitizenEntity c = new CitizenEntity(ModContent.CITIZEN, w);
                String name = com.yompix.novograd.story.Story.root().getAsJsonObject("characters").getAsJsonObject(id).get("name").getAsString();
                c.setupAsHero(id, CitizenEntity.Role.CITIZEN, name);
                place(w, c, at.add(-16 + i * 2.1, 0, 0));
                n++;
            }
            int[] skins = new int[Looks.RANDOM_COUNT + 8];
            for (int i = 0; i < Looks.RANDOM_COUNT; i++) skins[i] = Looks.RANDOM_FROM + i;
            int[] roles = {1, 2, 3, 4, 5, 6, 7, 15};
            System.arraycopy(roles, 0, skins, Looks.RANDOM_COUNT, roles.length);
            for (int i = 0; i < skins.length; i++) {
                CitizenEntity c = new CitizenEntity(ModContent.CITIZEN, w);
                c.setup(skins[i], CitizenEntity.Role.CITIZEN, true, com.yompix.novograd.life.Names.random(w.getRandom(), Looks.isSlim(skins[i])));
                int row = i / 16, col = i % 16;
                place(w, c, at.add(-16 + col * 2.1, 0, 3.2 + row * 3.0));
                int[] ns = Looks.nearestStory(skins[i], Looks.STORY_FROM);
                Novograd.LOG.info("[lookshow] {} ближайший герой {} — {}{}", skins[i], Looks.STORY_IDS.get(Math.max(0, ns[0])), ns[1] / 1000.0, ns[2] == 1 ? " (та же причёска!)" : "");
                n++;
            }
            Novograd.LOG.info("[lookshow] поставлено {} жителей", n);
            p.teleport(w, at.x - 18, at.y + 2.2, at.z - 5, -60, 12);
            p.getAbilities().flying = true;
            p.sendAbilitiesUpdate();
        });
        run(60, c -> {
            c.options.setPerspective(Perspective.FIRST_PERSON);
            c.options.hudHidden = true;
        });
        cmd(5, "tick rate " + System.getProperty("novograd.rectps", "4"));
        run(5, c -> rec(c));
        // облёт: вдоль ряда героев, потом над рядами прохожих
        final int[] t = {0};
        run(1, c -> perTick = () -> {
            var pl = c.player;
            if (pl == null || base == null) return;
            t[0]++;
            double k = Math.min(1, t[0] / 560.0);
            // первая половина — вдоль героев (имена видны), вторая — над рядами прохожих
            double x = base.x - 18 + 36 * (k < 0.55 ? k / 0.55 : (k - 0.55) / 0.45), y = base.y + 1.7 + (k > 0.55 ? 3.5 : 0), z = base.z - (k > 0.55 ? 2.5 : 3.6);
            pl.setPosition(x, y, z);
            pl.setVelocity(Vec3d.ZERO);
            double tx = x + 1.5, tz = base.z + (k > 0.55 ? 7 : 0), ty = base.y + (k > 0.55 ? 0.5 : 1.3);
            float yaw = (float) Math.toDegrees(Math.atan2(-(tx - x), tz - z));
            float pitch = (float) -Math.toDegrees(Math.atan2(ty - (y + 1.6), Math.hypot(tx - x, tz - z)));
            pl.setYaw(yaw);
            pl.setPitch(pitch);
            pl.prevYaw = yaw;
            pl.prevPitch = pitch;
        });
        label(580, "облёт");
        run(1, c -> perTick = null);
        finish();
    }

    private static void place(net.minecraft.server.world.ServerWorld w, CitizenEntity c, Vec3d at) {
        BlockPos g = Story.ground(w, at);
        c.refreshPositionAndAngles(at.x, g.getY(), at.z, 0, 0);
        c.setHeadYaw(0);
        c.setBodyYaw(0);
        c.setAiDisabled(true); // стоят ровно в ряд (анимация стойки идёт — она на клиенте)
        w.spawnEntity(c);
        c.setStationary(true);
        c.setCustomNameVisible(true);
        SPAWNED.add(c.getUuid());
    }

    private static final List<java.util.UUID> SPAWNED = new ArrayList<>();

    // ===================================================================== служебное
    private static void rec(MinecraftClient c) {
        String rec = System.getProperty("novograd.rec");
        if (rec != null) com.yompix.novograd.client.story.CineRec.start(rec, 30);
    }

    private static void label(int delay, String what) {
        run(delay, c -> Novograd.LOG.info("[show] {}", what));
    }

    private static void finish() {
        server(1, s -> { // поставленных для ролика — убрать из мира
            var w = s.getOverworld();
            for (var u : SPAWNED) if (w.getEntity(u) != null) w.getEntity(u).discard();
        });
        run(5, c -> {
            if (com.yompix.novograd.client.story.CineRec.on) com.yompix.novograd.client.story.CineRec.stop();
            var s = c.getServer();
            if (s != null) s.execute(() -> s.getCommandManager().executeWithPrefix(s.getCommandSource(), "tick rate 20"));
            Novograd.LOG.info("[show] done");
        });
        run(20, MinecraftClient::scheduleStop);
    }

    private static boolean pendingOpen;

    private static void openWorld(MinecraftClient c) {
        // пока висит оверлей (загрузка ресурсов), клиент ждёт его в цикле старта сервера и не дождётся — сначала пусть уйдёт
        if (c.getOverlay() != null) {
            pendingOpen = true;
            return;
        }
        pendingOpen = false;
        c.createIntegratedServerLoader().start(System.getProperty("novograd.cineworld", "Island"),
                () -> c.setScreen(new com.yompix.novograd.client.menu.LifeMenuScreen()));
    }

    private static void tick(MinecraftClient c) {
        if (pendingOpen) {
            openWorld(c);
            return;
        }
        if (perTick != null) perTick.run();
        if (index >= STEPS.size()) return;
        if (index < 0) {
            index = 0;
            timer = STEPS.get(0).delay();
        }
        if (--timer > 0) return;
        try {
            STEPS.get(index).action().accept(c);
        } catch (RuntimeException e) {
            Novograd.LOG.warn("[show] шаг {}: {}", index, e.toString());
        }
        index++;
        if (index < STEPS.size()) timer = STEPS.get(index).delay();
    }

    private static ServerPlayerEntity pl(IntegratedServer s) {
        return s.getPlayerManager().getPlayer(P);
    }

    private static void run(int delay, Consumer<MinecraftClient> a) {
        STEPS.add(new Step(delay, a));
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
                    Novograd.LOG.warn("[show] сервер: {}", e.toString());
                }
            });
        }));
    }
}
