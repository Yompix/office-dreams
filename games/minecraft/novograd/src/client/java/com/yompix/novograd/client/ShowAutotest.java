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
    static Vec3d base;
    static Runnable perTick;

    public static void init() {
        String mode = System.getProperty("novograd.autotest");
        if (mode == null || !(mode.equals("animshow") || mode.equals("lookshow") || mode.equals("gridshow") || mode.equals("ailife")
                || mode.equals("lookgrid") || mode.equals("crowd") || mode.equals("creatorshow") || mode.equals("mpload") || mode.equals("gymshow")
                || mode.equals("bodyshow") || mode.equals("agetest") || mode.equals("spawnmap"))) return;
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
        if (mode.equals("lookgrid") || mode.equals("crowd") || mode.equals("creatorshow") || mode.equals("mpload") || mode.equals("gymshow")
                || mode.equals("bodyshow") || mode.equals("agetest") || mode.equals("spawnmap")) server(5, s -> {
            var d = com.yompix.novograd.life.Life.get(pl(s));
            d.storyStarted = true;
            d.storyDone = true;
            com.yompix.novograd.life.Life.sync(pl(s));
        });
        if (mode.equals("animshow")) animshow();
        else if (mode.equals("gridshow")) cityshow();
        else if (mode.equals("ailife")) ailife();
        else if (mode.equals("lookgrid")) lookgrid();
        else if (mode.equals("crowd")) crowd();
        else if (mode.equals("creatorshow")) creatorshow();
        else if (mode.equals("mpload")) mpload();
        else if (mode.equals("gymshow")) gymshow();
        else if (mode.equals("bodyshow")) BodyShow.bodyshow();
        else if (mode.equals("agetest")) BodyShow.agetest();
        else if (mode.equals("spawnmap")) BodyShow.spawnmap();
        else lookshow();
        ClientTickEvents.END_CLIENT_TICK.register(ShowAutotest::tick);
    }

    // ===================================================================== анимации игрока
    static void key(MinecraftClient c, net.minecraft.client.option.KeyBinding k, boolean on) {
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

    static void tp(String place, double dx, double dz, float yaw, float pitch) {
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

    // ===================================================================== 0.9.5: «Живой город» — день вокруг игрока
    private static final java.util.Map<Integer, int[]> AI_SAMPLES = new java.util.HashMap<>(); // id → {всего, без дела}
    private static final java.util.Map<Integer, java.util.Set<String>> AI_ACTS = new java.util.HashMap<>();
    private static final java.util.Set<String> AI_ALL = new java.util.TreeSet<>();
    private static final java.util.Map<java.util.UUID, Object[]> AI_CARS = new java.util.HashMap<>(); // машина → {позиция, с какого тика стоит}
    private static int aiStuck;
    private static final java.util.Map<String, Integer> AI_IDLE_KIND = new java.util.TreeMap<>(), AI_ALL_KIND = new java.util.TreeMap<>();
    private static double aiMspt;
    private static int aiMsptN;

    private static void aiSample(IntegratedServer s) {
        ServerPlayerEntity p = pl(s);
        var w = p.getServerWorld();
        for (CitizenEntity c : w.getEntitiesByClass(CitizenEntity.class, p.getBoundingBox().expand(48, 20, 48), CitizenEntity::isAlive)) {
            String a = c.hasVehicle() ? "DRIVE" : c.getActivity().name();
            boolean idle = c.getActivity() == CitizenEntity.Activity.IDLE && c.directTo == null && !c.hasVehicle() && c.getNavigation().isIdle();
            int[] k = AI_SAMPLES.computeIfAbsent(c.getId(), x -> new int[2]);
            k[0]++;
            if (idle) {
                k[1]++;
                // 0.9.7: кто именно «без дела» — чтобы понять, откуда процент
                String kind = c.getRole() != CitizenEntity.Role.CITIZEN ? c.getRole().name() : !c.getStoryId().isEmpty() ? "герой"
                        : c.getCommandTags().contains(com.yompix.novograd.world.Residents.TAG) ? "жилец" : c.isStationary() ? "стоит_на_месте(персонал)"
                        : c.isAmbient() ? "прохожий" : "прочие";
                AI_IDLE_KIND.merge(kind, 1, Integer::sum);
            }
            AI_ALL_KIND.merge(c.getRole() != CitizenEntity.Role.CITIZEN ? c.getRole().name() : !c.getStoryId().isEmpty() ? "герой"
                    : c.getCommandTags().contains(com.yompix.novograd.world.Residents.TAG) ? "жилец" : c.isStationary() ? "стоит_на_месте(персонал)"
                    : c.isAmbient() ? "прохожий" : "прочие", 1, Integer::sum);
            if (!idle) {
                AI_ACTS.computeIfAbsent(c.getId(), x -> new java.util.HashSet<>()).add(a);
                AI_ALL.add(a);
            }
        }
        long now = w.getTime();
        for (var car : w.getEntitiesByClass(com.yompix.novograd.entity.CarEntity.class, p.getBoundingBox().expand(64, 20, 64), x -> x.onRoute() && x.hasNpcDriver())) {
            Object[] o = AI_CARS.get(car.getUuid());
            if (o == null || ((Vec3d) o[0]).squaredDistanceTo(car.getPos()) > 1.5 * 1.5 || car.lightWait > 0) AI_CARS.put(car.getUuid(), new Object[]{car.getPos(), now});
            else if (now - (long) o[1] > 20 * 30) {
                aiStuck++;
                Novograd.LOG.info("[ailife] машина стоит >30 с: {} у {}", car.getId(), car.getBlockPos());
                AI_CARS.put(car.getUuid(), new Object[]{car.getPos(), now});
            }
        }
        aiMspt += s.getAverageNanosPerTick() / 1e6;
        aiMsptN++;
    }

    private static void ailife() {
        server(10, s -> {
            ServerPlayerEntity p = pl(s);
            var d = com.yompix.novograd.life.Life.get(p);
            d.storyStarted = true;
            d.storyDone = true;
        });
        cmd(5, "effect give " + P + " minecraft:invisibility 99999 0 true");
        tp(System.getProperty("novograd.aiplace", "dt_2"), 0, 0, 0, 20);
        boolean shortRun = Boolean.getBoolean("novograd.aishort"); // ролик: короче и с камерой сверху-сбоку
        String[] times = shortRun ? new String[]{"6800", "12500"} : new String[]{"500", "2200", "4000", "6800", "9000", "11800", "13500", "16500"}; // 6:30, 8:12, 10:00, 12:48, 15:00, 17:48, 19:30, 22:30
        if (shortRun) {
            cmd(5, "gamemode spectator " + P);
            server(5, s -> {
                ServerPlayerEntity p = pl(s);
                p.teleport(p.getServerWorld(), p.getX() - 6, p.getY() + 5, p.getZ() - 6, -45, 30);
            });
            cmd(5, "tick rate " + System.getProperty("novograd.rectps", "3"));
            run(40, c -> {
                c.options.hudHidden = false;
                rec(c);
            });
        }
        // утро без скачков времени: 7:20 → 8:40 — выходят из дома, идут к своим машинам, едут
        cmd(5, "time set 1350");
        cmd(5, "gamerule doDaylightCycle true");
        label(1, "утро подряд");
        for (int i = 0; i < (shortRun ? 15 : 40); i++) server(40, ShowAutotest::aiSample);
        cmd(5, "gamerule doDaylightCycle false");
        for (String t : times) {
            cmd(5, "time set " + t);
            label(1, "время " + t);
            for (int i = 0; i < (shortRun ? 8 : 15); i++) server(40, ShowAutotest::aiSample);
        }
        server(5, s -> {
            int n = 0, worst = 0;
            double sum = 0;
            for (var e : AI_SAMPLES.entrySet()) {
                int[] k = e.getValue();
                if (k[0] < 6) continue; // мелькнул мимо
                n++;
                int pct = k[1] * 100 / k[0];
                sum += pct;
                worst = Math.max(worst, pct);
            }
            int active = (int) AI_ACTS.values().stream().filter(x -> x.size() >= 2).count();
            Novograd.LOG.info("[ailife] горожан {} (долго в кадре), занятий всего {}: {}", n, AI_ALL.size(), AI_ALL);
            Novograd.LOG.info("[ailife] без дела: в среднем {}%, худший {}%; с ≥2 занятиями {}", n == 0 ? 0 : (int) (sum / n), worst, active);
            Novograd.LOG.info("[ailife] без дела по видам (замеров): {} из {}; отскоков {}, засмотрелись {}", AI_IDLE_KIND, AI_ALL_KIND,
                    com.yompix.novograd.world.Attention.dodges, com.yompix.novograd.world.Attention.gazes);
            Novograd.LOG.info("[ailife] поездки на своей машине: начато {} доехало {}; машин стояло >30 с: {}", com.yompix.novograd.world.Residents.tripsStarted,
                    com.yompix.novograd.world.Residents.tripsArrived, aiStuck);
            Novograd.LOG.info("[ailife] полиция без вызова: штрафов {} разговоров {} кофе {} обходов {}; mspt в среднем {}", com.yompix.novograd.gta.PoliceLife.tickets,
                    com.yompix.novograd.gta.PoliceLife.chats, com.yompix.novograd.gta.PoliceLife.coffees, com.yompix.novograd.gta.PoliceLife.walks,
                    String.format("%.2f", aiMspt / Math.max(1, aiMsptN)));
            boolean ok = AI_ALL.size() >= 8 && (n == 0 || sum / n < 50) && aiStuck == 0;
            Novograd.LOG.info("[ailife] {}", ok ? "PASS" : "FAIL (занятий ≥8, без дела <50%, застрявших 0)");
        });
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

    static final List<java.util.UUID> SPAWNED = new ArrayList<>();

    // ===================================================================== 0.9.6: редактор внешности и разнообразие жителей
    /** Зерно жителя из генератора с нужным полом и тоном кожи (для витрины «все тона»). */
    static long seedFor(boolean female, int tone, int profile, long from) {
        for (long k = from; k < from + 20000; k++) {
            long sd = com.yompix.novograd.life.LookGen.citizenSeed(k * 1_000_003L, female, profile);
            var a = com.yompix.novograd.life.LookGen.citizen(sd);
            if (a.skin == tone && !com.yompix.novograd.life.LookGen.heroLike(a)) return sd;
        }
        return com.yompix.novograd.life.LookGen.citizenSeed(from, female, profile);
    }

    static CitizenEntity genCitizen(net.minecraft.server.world.ServerWorld w, long seed) {
        CitizenEntity c = new CitizenEntity(ModContent.CITIZEN, w);
        boolean f = com.yompix.novograd.life.LookGen.female(seed);
        int skin = Looks.RANDOM_FROM + (f ? 0 : 1) + 2 * w.getRandom().nextInt(Looks.RANDOM_COUNT / 2 - 4);
        c.setup(skin, CitizenEntity.Role.CITIZEN, true, com.yompix.novograd.life.Names.random(w.getRandom(), f));
        return c;
    }

    /** Лист: 3 снимка по 8 жителей (24: мужчины и женщины, все 10 тонов кожи, разные профили) — склеивает tools/contact_sheet.py. */
    /** Студия над городом: белый пол и фон (без мусора улиц на снимках). */
    static Vec3d studio(ServerPlayerEntity p) {
        var w = p.getServerWorld();
        int x0 = (int) Math.floor(p.getX()) & ~15, z0 = (int) Math.floor(p.getZ()) & ~15, y0 = 200;
        var cs = w.getServer().getCommandManager();
        var src = w.getServer().getCommandSource().withSilent();
        cs.executeWithPrefix(src, String.format("fill %d %d %d %d %d %d minecraft:smooth_quartz", x0 - 16, y0, z0 - 6, x0 + 16, y0, z0 + 14));
        cs.executeWithPrefix(src, String.format("fill %d %d %d %d %d %d minecraft:light_gray_concrete", x0 - 16, y0 + 1, z0 - 6, x0 + 16, y0 + 8, z0 - 6));
        cs.executeWithPrefix(src, String.format("fill %d %d %d %d %d %d minecraft:air", x0 - 16, y0 + 1, z0 - 5, x0 + 16, y0 + 10, z0 + 14));
        return new Vec3d(x0 + 0.5, y0 + 1, z0 + 0.5);
    }

    private static void lookgrid() {
        cmd(5, "time set 6000");
        cmd(5, "weather clear");
        cmd(5, "gamerule doDaylightCycle false");
        run(120, c -> { if (c.currentScreen != null) c.setScreen(null); });
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            base = studio(p);
            p.teleport(p.getServerWorld(), base.x, base.y + 0.3, base.z + 9.2, 180, 4);
            p.getAbilities().flying = true;
            p.sendAbilitiesUpdate();
        });
        run(100, c -> { if (c.currentScreen != null) c.setScreen(null); });
        server(5, s -> pl(s).teleport(pl(s).getServerWorld(), base.x, base.y - 0.45, base.z + 7.4, 180, 3));
        run(100, c -> { if (c.currentScreen != null) c.setScreen(null); });
        for (int g = 0; g < 3; g++) {
            final int gg = g;
            server(gg == 0 ? 10 : 2, s -> {
                ServerPlayerEntity p = pl(s);
                var w = p.getServerWorld();
                Vec3d at = base;
                for (var u : SPAWNED) if (w.getEntity(u) != null) w.getEntity(u).discard();
                SPAWNED.clear();
            for (int i = 0; i < 8; i++) {
                    int k = gg * 8 + i;
                    boolean f = k % 2 == 1;
                    int tone = (k * 10) / 24;
                    long sd = seedFor(f, tone, k % com.yompix.novograd.life.LookGen.PROFILE_NAMES.length, 1000L * k + 7);
                    CitizenEntity c = genCitizen(w, sd);
                    place(w, c, at.add(-5.25 + i * 1.5, 0, 0));
                    c.forceLook(sd);
                    c.lockActivity(CitizenEntity.Activity.IDLE);
                    c.setCustomNameVisible(false);
                    var a = com.yompix.novograd.life.LookGen.citizen(sd);
                    Novograd.LOG.info("[lookgrid] {} {} тон {} {} профиль {} верх {} низ {} причёска {}", k, f ? "Ж" : "М", a.skin,
                            com.yompix.novograd.life.LookGen.PROFILE_NAMES[com.yompix.novograd.life.LookGen.profile(sd)], "", a.top, a.pants, a.hair);
                }
                p.teleport(w, at.x, at.y - 0.45, at.z + 7.4, 180, 3);
                Novograd.LOG.info("[lookgrid] студия {} игрок {}", at, p.getPos());
                p.getAbilities().flying = true;
                p.sendAbilitiesUpdate();
            });
            run(gg == 0 ? 60 : 5, c -> {
                if (c.currentScreen != null) c.setScreen(null);
                c.options.setPerspective(Perspective.FIRST_PERSON);
                c.options.hudHidden = true;
                c.options.getFov().setValue(52);
            });
            run(70, c -> shot(c, "lookgrid_" + gg));
            // крупно лица: средние четверо
            server(1, s -> {
                ServerPlayerEntity p = pl(s);
                p.teleport(p.getServerWorld(), base.x - 0.75, base.y + 0.05, base.z + 3.4, 180, 2);
            });
            run(25, c -> c.options.getFov().setValue(40));
            run(20, c -> shot(c, "lookface_" + gg));
        }
        // сами скины (128×128) — для листа и проверки пикселей
        run(1, c -> {
            java.io.File dir = new java.io.File(c.runDirectory, "screenshots/show/skins");
            dir.mkdirs();
            for (int k = 0; k < 24; k++) {
                long sd = seedFor(k % 2 == 1, (k * 10) / 24, k % com.yompix.novograd.life.LookGen.PROFILE_NAMES.length, 1000L * k + 7);
                try (var img = com.yompix.novograd.client.style.SkinComposer.compose(com.yompix.novograd.life.LookGen.citizen(sd))) {
                    img.writeTo(new java.io.File(dir, String.format("skin_%02d.png", k)));
                } catch (Exception e) {
                    Novograd.LOG.warn("[lookgrid] скин {}: {}", k, e.toString());
                }
            }
        });
        finish();
    }

    /** Толпа: 40 жителей из генератора гуляют по площади; FPS с новыми скинами и с прежними (те же люди), потом ролик. */
    private static final List<Integer> FPS = new ArrayList<>();

    private static void crowd() {
        String place = System.getProperty("novograd.lookplace", "park_center");
        int n = Integer.getInteger("novograd.crowd", 40);
        cmd(5, "time set 6000");
        cmd(5, "weather clear");
        cmd(5, "gamerule doDaylightCycle false");
        cmd(5, "effect give " + P + " minecraft:invisibility 99999 0 true");
        // телепорт дважды: первый до загрузки клиента иногда не доходит
        for (int k = 0; k < 2; k++) server(k == 0 ? 5 : 100, s -> {
            ServerPlayerEntity p = pl(s);
            var w = p.getServerWorld();
            Vec3d at = Story.place(w, place);
            if (at == null) at = p.getPos();
            base = at;
            BlockPos gnd = Story.ground(w, at);
            p.teleport(w, at.x - 9, gnd.getY() + 4.5, at.z - 9, -45, 22);
            p.getAbilities().flying = true;
            p.sendAbilitiesUpdate();
        });
        run(80, c -> {
            if (c.currentScreen != null) c.setScreen(null);
            c.options.setPerspective(Perspective.FIRST_PERSON);
            c.options.hudHidden = true;
        });
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            var w = p.getServerWorld();
            SPAWNED.clear();
            var r = new java.util.Random(96);
            for (int i = 0; i < n; i++) {
                boolean f = r.nextBoolean();
                long sd = com.yompix.novograd.life.LookGen.citizenSeed(r.nextLong(), f, r.nextInt(com.yompix.novograd.life.LookGen.PROFILE_NAMES.length));
                CitizenEntity c = genCitizen(w, sd);
                Vec3d q = base.add(r.nextGaussian() * 5, 0, r.nextGaussian() * 5);
                BlockPos g = Story.ground(w, q);
                c.refreshPositionAndAngles(q.x, g.getY(), q.z, r.nextFloat() * 360, 0);
                w.spawnEntity(c);
                c.setStationary(false);
                c.forceLook(sd);
                SPAWNED.add(c.getUuid());
            }
            Novograd.LOG.info("[crowd] поставлено {} жителей у {}", n, base);
        });
        run(120, c -> FPS.clear());
        String[] phase = {"новые скины", "старые скины", "новые скины (снова)"};
        for (int ph = 0; ph < 3; ph++) {
            final int fph = ph;
            if (ph > 0) server(1, s -> {
                var w = pl(s).getServerWorld();
                for (var u : SPAWNED) if (w.getEntity(u) instanceof CitizenEntity c) {
                    if (fph == 1) {
                        c.getCommandTags().add("seed:" + c.getLookSeed());
                        c.forceLook(0);
                    } else for (String t : c.getCommandTags()) if (t.startsWith("seed:")) c.forceLook(Long.parseLong(t.substring(5)));
                }
            });
            run(60, c -> FPS.clear());
            for (int i = 0; i < 12; i++) run(20, c -> FPS.add(c.getCurrentFps()));
            run(1, c -> Novograd.LOG.info("[crowd] FPS {}: {} среднее {} (жителей в кадре {}, скинов собрано {}, {} мс на скин)", phase[fph], FPS, avg(FPS),
                    c.world == null ? 0 : c.world.getEntitiesByClass(CitizenEntity.class, c.player.getBoundingBox().expand(40), x -> true).size(),
                    com.yompix.novograd.client.style.SkinComposer.built,
                    String.format(java.util.Locale.ROOT, "%.2f", com.yompix.novograd.client.style.SkinComposer.built == 0 ? 0 : com.yompix.novograd.client.style.SkinComposer.builtNanos / 1e6 / com.yompix.novograd.client.style.SkinComposer.built)));
        }
        run(40, c -> shot(c, "crowd_wide"));
        // ролик: камера медленно идёт вокруг толпы
        cmd(5, "tick rate " + System.getProperty("novograd.rectps", "5"));
        run(5, c -> rec(c));
        final int[] t = {0};
        run(1, c -> perTick = () -> {
            var pl = c.player;
            if (pl == null || base == null) return;
            t[0]++;
            double k = Math.min(1, t[0] / 260.0);
            double ang = Math.toRadians(-135 + 100 * k);
            double x = base.x + Math.cos(ang) * 9, z = base.z + Math.sin(ang) * 9, y = base.y + 2.6;
            pl.setPosition(x, y, z);
            pl.setVelocity(Vec3d.ZERO);
            float yaw = (float) Math.toDegrees(Math.atan2(-(base.x - x), base.z - z));
            pl.setYaw(yaw);
            pl.prevYaw = yaw;
            pl.setPitch(14);
            pl.prevPitch = 14;
        });
        label(270, "толпа");
        run(1, c -> perTick = null);
        run(5, c -> shot(c, "crowd_end"));
        finish();
    }

    private static int avg(List<Integer> l) {
        return l.isEmpty() ? 0 : (int) Math.round(l.stream().mapToInt(Integer::intValue).average().orElse(0));
    }

    /** Ролик редактора: вкладки, «случайно», пол, приближение. */
    private static void creatorshow() {
        server(10, s -> {
            ServerPlayerEntity p = pl(s);
            var w = p.getServerWorld();
            Vec3d at = Story.place(w, "park_center");
            if (at != null) p.teleport(w, at.x, Story.ground(w, at).getY(), at.z, 0, 0);
            p.getInventory().selectedSlot = 8;
        });
        cmd(5, "gamemode survival " + P);
        run(40, c -> {
            c.options.hudHidden = false;
            c.setScreen(new com.yompix.novograd.client.style.CreatorScreen(com.yompix.novograd.client.style.CreatorScreen.Mode.FIRST));
        });
        run(20, c -> rec(c));
        String[] steps = {"tab0", "wait", "gender", "wait", "tone", "tone", "tone", "tab2", "hair", "hair", "hair", "haircolor", "wait", "tab1", "eyes", "lips",
                "tab3", "top", "top", "outer", "wait", "tab4", "hat", "glasses", "bag", "zoom", "wait", "rot", "zoom", "random", "random", "random", "random",
                "gender", "random", "random", "tab5", "tat", "wait", "tab0", "build", "build", "wait"};
        for (String st : steps) run(st.equals("wait") ? 30 : 22, c -> {
            if (c.currentScreen instanceof com.yompix.novograd.client.style.CreatorScreen cs) cs.demo(st);
        });
        run(40, c -> c.setScreen(null));
        finish();
    }

    // ===================================================================== 0.9.6: много игроков (FakePlayer) — состояние у каждого своё, MSPT
    private static final List<net.fabricmc.fabric.api.entity.FakePlayer> BOTS = new ArrayList<>();
    private static final List<Double> MSPT = new ArrayList<>();

    private static void mpload() {
        String[] places = {"dt_1", "dt_3", "port_dock", "beach_bar", "suburb_street", "industry_street", "riv_strip", "riv_marina", "airfield", "village",
                "mirage_strip", "club_neon"};
        cmd(5, "gamerule doDaylightCycle false");
        server(10, s -> {
            var w = s.getOverworld();
            for (int i = 0; i < places.length; i++) {
                var fp = net.fabricmc.fabric.api.entity.FakePlayer.get(w, new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes(("bot" + i).getBytes()), "Bot" + i));
                Vec3d at = Story.place(w, places[i]);
                if (at == null) continue;
                fp.refreshPositionAndAngles(at.x, at.y, at.z, 0, 0);
                var d = com.yompix.novograd.life.Life.get(fp);
                d.money = 1000 * (i + 1);
                d.cash = 100 * (i + 1);
                d.dirty = 0;
                BOTS.add(fp);
                // «игрок» в районе: держим район загруженным, как будто там кто-то есть
                w.getChunkManager().addTicket(net.minecraft.server.world.ChunkTicketType.FORCED, new net.minecraft.util.math.ChunkPos(BlockPos.ofFloored(at)), 3,
                        new net.minecraft.util.math.ChunkPos(BlockPos.ofFloored(at)));
            }
            Novograd.LOG.info("[mpload] ботов {}", BOTS.size());
        });
        run(200, c -> MSPT.clear());
        for (int i = 0; i < 30; i++) server(20, s -> MSPT.add(s.getAverageNanosPerTick() / 1e6));
        // действия «каждый сам за себя»: платежи, розыск, машина — у одного бота не должно меняться у другого
        server(1, s -> {
            int leaks = 0;
            var w = s.getOverworld();
            for (int i = 0; i < BOTS.size(); i++) {
                var fp = BOTS.get(i);
                var d = com.yompix.novograd.life.Life.get(fp);
                if (i % 2 == 0) com.yompix.novograd.gta.Money.pay(d, 500);
                if (i == 3) com.yompix.novograd.gta.Wanted.add(fp, 3, "тест", 3);
                if (i == 5) {
                    var car = new com.yompix.novograd.entity.CarEntity(ModContent.CAR, w);
                    car.refreshPositionAndAngles(fp.getX() + 3, fp.getY(), fp.getZ(), 0, 0);
                    car.setOwner(fp.getUuid());
                    w.spawnEntity(car);
                    SPAWNED.add(car.getUuid());
                }
            }
            for (int i = 0; i < BOTS.size(); i++) {
                var fp = BOTS.get(i);
                var d = com.yompix.novograd.life.Life.get(fp);
                long expect = 1000L * (i + 1) + 100L * (i + 1) - (i % 2 == 0 ? 500 : 0);
                if (d.money + d.cash != expect) {
                    leaks++;
                    Novograd.LOG.info("[mpload] УТЕЧКА денег у бота {}: {} вместо {}", i, d.money + d.cash, expect);
                }
                int st = com.yompix.novograd.gta.Wanted.stars(fp);
                if (i == 3 ? st < 3 : st != 0) {
                    leaks++;
                    Novograd.LOG.info("[mpload] УТЕЧКА розыска у бота {}: {}", i, st);
                }
            }
            Novograd.LOG.info("[mpload] утечек состояния между игроками: {}", leaks);
            com.yompix.novograd.gta.Wanted.clear(BOTS.get(Math.min(3, BOTS.size() - 1)));
        });
        run(120, c -> {});
        server(1, s -> {
            int cars = 0;
            var st = com.yompix.novograd.gta.MyCars.state(s);
            for (int i = 0; i < BOTS.size(); i++) cars += st.cars.getOrDefault(BOTS.get(i).getUuid(), java.util.Map.of()).size();
            int cit = 0, allCars = 0;
            for (var e : s.getOverworld().iterateEntities()) {
                if (e instanceof CitizenEntity) cit++;
                else if (e instanceof com.yompix.novograd.entity.CarEntity) allCars++;
            }
            double avg = MSPT.stream().mapToDouble(Double::doubleValue).average().orElse(0), max = MSPT.stream().mapToDouble(Double::doubleValue).max().orElse(0);
            Novograd.LOG.info("[mpload] {} районов загружено, MSPT среднее {} макс {}, жителей {}, машин {}, машин ботов в «Мои машины» {}",
                    BOTS.size(), String.format(java.util.Locale.ROOT, "%.1f", avg), String.format(java.util.Locale.ROOT, "%.1f", max), cit, allCars, cars);
            var w = s.getOverworld();
            for (var fp : BOTS) w.getChunkManager().removeTicket(net.minecraft.server.world.ChunkTicketType.FORCED, new net.minecraft.util.math.ChunkPos(fp.getBlockPos()), 3,
                    new net.minecraft.util.math.ChunkPos(fp.getBlockPos()));
        });
        finish();
    }

    /** Спортзал: все 8 упражнений по очереди от третьего лица (снимки) — у себя, как у других игроков. */
    private static void gymshow() {
        cmd(5, "time set 6000");
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            base = studio(p);
            p.teleport(p.getServerWorld(), base.x, base.y, base.z, 150, 10);
            p.getAbilities().flying = false;
            p.sendAbilitiesUpdate();
            var d = com.yompix.novograd.life.Life.get(p);
            d.look = com.yompix.novograd.life.LookGen.generate(42, false, com.yompix.novograd.life.LookGen.SPORT, false);
            com.yompix.novograd.life.Life.sync(p);
            com.yompix.novograd.life.Style.broadcast(p);
        });
        run(100, c -> {
            if (c.currentScreen != null) c.setScreen(null);
            c.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
            c.options.hudHidden = true;
        });
        server(5, s -> pl(s).teleport(pl(s).getServerWorld(), base.x, base.y, base.z, 150, 10));
        run(60, c -> rec(c));
        for (String ex : new String[]{"curl", "bench", "pullup", "bag", "pushup", "run", "squat", "yoga"}) {
            run(5, c -> com.yompix.novograd.client.anim.PlayerAnim.play("gym_" + ex));
            run(50, c -> shot(c, "gym_" + ex));
            run(30, c -> {});
        }
        run(5, c -> com.yompix.novograd.client.anim.PlayerAnim.play("none"));
        // экран зала
        run(5, c -> c.setScreen(new com.yompix.novograd.client.style.GymScreen()));
        run(40, c -> shot(c, "gym_menu"));
        run(5, c -> c.setScreen(null));
        finish();
    }

    static void shot(MinecraftClient c, String name) {
        try (var img = net.minecraft.client.util.ScreenshotRecorder.takeScreenshot(c.getFramebuffer())) {
            java.io.File dir = new java.io.File(c.runDirectory, "screenshots/show");
            dir.mkdirs();
            img.writeTo(new java.io.File(dir, name + ".png"));
            Novograd.LOG.info("[show] снимок {} игрок {} камера {} экран {} пауза {}", name, c.player == null ? null : c.player.getPos(), c.gameRenderer.getCamera().getPos(), c.currentScreen, c.isPaused());
        } catch (Exception e) {
            Novograd.LOG.warn("[show] снимок {}: {}", name, e.toString());
        }
    }

    // ===================================================================== служебное
    static void rec(MinecraftClient c) {
        String rec = System.getProperty("novograd.rec");
        if (rec != null) com.yompix.novograd.client.story.CineRec.start(rec, 30);
    }

    static void label(int delay, String what) {
        run(delay, c -> Novograd.LOG.info("[show] {}", what));
    }

    static void finish() {
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

    static ServerPlayerEntity pl(IntegratedServer s) {
        return s.getPlayerManager().getPlayer(P);
    }

    static void run(int delay, Consumer<MinecraftClient> a) {
        STEPS.add(new Step(delay, a));
    }

    static void cmd(int delay, String command) {
        server(delay, s -> s.getCommandManager().executeWithPrefix(s.getCommandSource(), command));
    }

    static void server(int delay, Consumer<IntegratedServer> action) {
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
