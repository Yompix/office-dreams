package com.yompix.novograd.client;

import net.minecraft.client.util.InputUtil;

import net.minecraft.client.option.KeyBinding;

import com.yompix.novograd.client.screen.OnlineCasinoScreen;
import com.yompix.novograd.client.screen.PhoneScreen;
import com.yompix.novograd.client.story.YompixSplash;
import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.story.Npcs;
import com.yompix.novograd.story.Story;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Только для разработки: запускается с -Dnovograd.autotest=true, сам проходит начало сюжета,
 * открывает экраны и делает скриншоты, чтобы проверить мод без человека за клавиатурой.
 */
public final class DevAutotest {
    private DevAutotest() {}

    private record Step(int delay, Consumer<MinecraftClient> action) {}

    private static final List<Step> STEPS = new ArrayList<>();
    private static int index = -1, timer;
    private static final String P = "Dev";

    public static void init() {
        if (System.getProperty("novograd.autotest") == null) return;
        String mode = System.getProperty("novograd.autotest");
        if (mode.startsWith("fun")) return; // свой сценарий — FunAutotest (FunClient.init)
        if (mode.startsWith("housing")) return; // недвижимость, жильцы, музыканты — HousingAutotest
        if (mode.startsWith("drv")) return; // свой сценарий — DriveAutotest (DriveClient.init)
        if (mode.startsWith("city")) return; // свой сценарий — CityAutotest (CityClient.init)
        if (mode.startsWith("props")) return; // своё — PropsAutotest (PropsClient.init)
        if (mode.startsWith("life")) return; // повседневная жизнь горожан — LifeAutotest
        if (mode.startsWith("transit")) return; // кафе, метро, «Мираж» — TransitAutotest
        if (mode.startsWith("outdoor")) return; // навыки, охота, рыбалка, сезоны, праздники — OutdoorAutotest
        if (mode.startsWith("calls")) return; // звонки героев — CallsAutotest (CallClient.init)
        if (mode.startsWith("cops")) return; // погоня, маски, колония, «Полицейские и воры» — client/cops/CopsAutotest
        if (mode.startsWith("heist")) return; // тёмные дела — HeistAutotest
        if (mode.startsWith("mp")) {
            // игра по сети: A проходит разговоры пробелом, B просто смотрит; оба делают снимки
            YompixSplash.markShown();
            boolean a = mode.equals("mpA");
            for (int i = 0; i < 14; i++) {
                if (a) for (int k = 0; k < 6; k++) key(25, GLFW.GLFW_KEY_SPACE);
                else run(150, c -> {});
                shot(10, mode + "_" + i);
            }
            run(20, MinecraftClient::scheduleStop);
            return;
        }
        if (mode.equals("story")) {
            YompixSplash.markShown();
            for (int i = 0; i < 6; i++) shot(100, "s_" + i);
            for (int i = 0; i < 10; i++) { key(40, GLFW.GLFW_KEY_SPACE); shot(10, "s_k" + i); }
            server(5, s -> {
                var d = Life.get(s.getPlayerManager().getPlayer(P));
                com.yompix.novograd.Novograd.LOG.info("[story] chapter {} mission {} step {} started {}", d.chapter, d.mission, d.step, d.storyStarted);
            });
            run(5, c -> c.setScreen(null));
            run(5, c -> NovogradClient.send("side_list"));
            shot(30, "s_missions");
            run(20, MinecraftClient::scheduleStop);
            return;
        }
        if (mode.equals("side")) {
            YompixSplash.markShown();
            server(150, s -> {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                var d = Life.get(p);
                d.storyStarted = true;
                d.storyDone = true;
                d.age = 22;
                d.edu = 16;
                Story.applyAge(p, 22);
                com.yompix.novograd.story.SideMissions.start(p, "race01");
            });
            for (int i = 0; i < 3; i++) key(30, GLFW.GLFW_KEY_SPACE);
            run(5, c -> { if (c.currentScreen != null) c.setScreen(null); });
            shot(40, "side_race");
            server(5, s -> {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                var d = Life.get(p);
                com.yompix.novograd.Novograd.LOG.info("[side] active {} step {}", d.side, d.sideStep);
                com.yompix.novograd.story.SideMissions.cancel(p);
                com.yompix.novograd.story.SideMissions.start(p, "turf01");
            });
            for (int i = 0; i < 3; i++) key(30, GLFW.GLFW_KEY_SPACE);
            run(5, c -> { if (c.currentScreen != null) c.setScreen(null); });
            server(5, s -> {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                var d = Life.get(p);
                var t = Story.place(p.getServerWorld(), ((com.google.gson.JsonObject) com.yompix.novograd.story.SideMissions.get("turf01").getAsJsonArray("steps").get(1)).get("place").getAsString());
                p.teleport(p.getServerWorld(), t.x, t.y, t.z, 0, 10);
                com.yompix.novograd.Novograd.LOG.info("[side] turf at {} step {}", t, d.sideStep);
            });
            shot(60, "side_turf");
            server(5, s -> {
                var d = Life.get(s.getPlayerManager().getPlayer(P));
                com.yompix.novograd.Novograd.LOG.info("[side] after goto: {} step {} count {}", d.side, d.sideStep, d.stepCount);
            });
            shot(80, "side_turf2");
            run(20, MinecraftClient::scheduleStop);
            return;
        }
        if (mode.equals("tour")) {
            YompixSplash.markShown();
            server(150, s -> {
                var d = Life.get(s.getPlayerManager().getPlayer(P));
                d.storyStarted = true;
                d.storyDone = true;
                d.chapter = 99;
            });
            for (int i = 0; i < 4; i++) key(10, GLFW.GLFW_KEY_SPACE);
            run(5, c -> { if (c.currentScreen != null) c.setScreen(null); c.options.hudHidden = true; });
            cmd(10, "gamemode spectator " + P);
            cmd(5, "time set 6000");
            // точка: x, z, высота над землёй, yaw, pitch, имя
            String[][] spots = {{"456", "-120", "90", "180", "28", "t_downtown"}, {"456", "-408", "3", "180", "-12", "t_downtown_street"},
                    {"-560", "40", "30", "90", "25", "t_port"}, {"-600", "-520", "12", "60", "15", "t_beach"}, {"430", "330", "40", "160", "30", "t_industry"},
                    {"-300", "260", "25", "200", "30", "t_suburb"}, {"-200", "-700", "50", "180", "20", "t_hills"}, {"560", "-820", "40", "120", "25", "t_airfield"},
                    {"24", "300", "6", "0", "5", "t_highway"}, {"200", "200", "120", "135", "45", "t_overview"}};
            for (String[] v : spots) {
                server(5, s -> {
                    ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                    int x = Integer.parseInt(v[0]), z = Integer.parseInt(v[1]);
                    int y = com.yompix.novograd.world.Island.surface(x, z) + Integer.parseInt(v[2]);
                    p.teleport(p.getServerWorld(), x + 0.5, y, z + 0.5, Float.parseFloat(v[3]), Float.parseFloat(v[4]));
                });
                shot(200, v[5]);
            }
            run(20, MinecraftClient::scheduleStop);
            return;
        }
        if (mode.equals("reel")) {
            // короткий ролик: оружие от первого лица и со стороны, маслкар с дрифтом, вертолёт
            YompixSplash.markShown();
            pre(60, c -> c.createIntegratedServerLoader().start("Fresh2", () -> c.setScreen(new com.yompix.novograd.client.menu.LifeMenuScreen())));
            run(100, c -> { if (c.player != null && c.player.isDead()) c.player.requestRespawn(); });
            run(5, c -> { if (c.currentScreen != null) c.setScreen(null); });
            cmd(5, "gamemode creative " + P);
            cmd(5, "time set 6500");
            cmd(5, "gamerule doDaylightCycle false");
            cmd(5, "effect give " + P + " minecraft:resistance 99999 4 true");
            // --- оружие
            server(5, s -> {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                var w = p.getServerWorld();
                w.getChunk(504 >> 4, -190 >> 4);
                p.teleport(w, 504.5, Story.topY(w, 504, -190) + 0.1, -190.5, 180, 4);
                p.getInventory().clear();
                p.getInventory().setStack(0, new net.minecraft.item.ItemStack(com.yompix.novograd.gta.Arsenal.RIFLE));
                p.getInventory().setStack(1, new net.minecraft.item.ItemStack(com.yompix.novograd.gta.Arsenal.SHOTGUN));
                p.getInventory().setStack(2, new net.minecraft.item.ItemStack(com.yompix.novograd.ModContent.PISTOL));
                p.getInventory().setStack(8, new net.minecraft.item.ItemStack(com.yompix.novograd.ModContent.AMMO, 64));
                p.getInventory().selectedSlot = 0;
                for (int i = 0; i < 4; i++) {
                    CitizenEntity g = new CitizenEntity(com.yompix.novograd.ModContent.CITIZEN, w);
                    g.setup(CitizenEntity.RANDOM_FROM + 39 - i * 2, CitizenEntity.Role.GANG, true, "Бандит");
                    g.refreshPositionAndAngles(502 + i * 1.5, Story.topY(w, 503, -205), -205.5, 0, 0);
                    w.spawnEntity(g);
                }
            });
            run(60, c -> { c.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON); c.player.getInventory().selectedSlot = 0; });
            run(1, c -> com.yompix.novograd.Novograd.LOG.info("[reel] guns {}", System.currentTimeMillis()));
            // управление как в шутере: ЛКМ (attackKey) — огонь, ПКМ (useKey) — прицел, Y — осмотр
            hold(30, c -> c.options.attackKey, 30);                       // очередь от бедра
            run(15, c -> KeyBinding.setKeyPressed(InputUtil.fromTranslationKey(c.options.useKey.getBoundKeyTranslationKey()), true));
            hold(15, c -> c.options.attackKey, 40);                       // очередь в прицеле
            run(10, c -> KeyBinding.setKeyPressed(InputUtil.fromTranslationKey(c.options.useKey.getBoundKeyTranslationKey()), false));
            run(10, c -> ArsenalClient.send("reload", 0));                 // перезарядка
            run(60, c -> GunFx.inspect());                                      // осмотр
            run(65, c -> c.player.getInventory().selectedSlot = 1);
            hold(25, c -> c.options.attackKey, 3);                        // дробовик: выстрел, помпа
            run(25, c -> {});
            hold(5, c -> c.options.attackKey, 3);
            run(25, c -> ArsenalClient.send("reload", 0));                 // патроны по одному
            run(60, c -> { c.player.getInventory().selectedSlot = 2; c.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_FRONT); });
            run(10, c -> KeyBinding.setKeyPressed(InputUtil.fromTranslationKey(c.options.useKey.getBoundKeyTranslationKey()), true));
            for (int i = 0; i < 4; i++) hold(10, c -> c.options.attackKey, 2); // пистолет в прицеле от третьего лица
            run(15, c -> KeyBinding.setKeyPressed(InputUtil.fromTranslationKey(c.options.useKey.getBoundKeyTranslationKey()), false));
            run(20, c -> { c.player.getInventory().selectedSlot = 0; c.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON); });
            hold(10, c -> c.options.attackKey, 40);
            // --- маслкар
            server(10, s -> {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                var w = p.getServerWorld();
                w.getChunk(200 >> 4, -744 >> 4);
                var car = new com.yompix.novograd.entity.CarEntity(com.yompix.novograd.ModContent.CAR, w);
                car.refreshPositionAndAngles(200.5, Story.topY(w, 200, -742), -742.5, -90, 0);
                car.setVariant(9);
                car.setTune(true, 2, 0);
                w.spawnEntity(car);
                p.getInventory().selectedSlot = 7;
                p.teleport(w, 200.5, car.getY() + 1, -742.5, -90, 10);
                p.startRiding(car, true);
            });
            run(40, c -> { c.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_BACK); com.yompix.novograd.Novograd.LOG.info("[reel] car {}", System.currentTimeMillis()); });
            hold(5, c -> c.options.forwardKey, 140);
            run(1, c -> { KeyBinding.setKeyPressed(InputUtil.fromTranslationKey(c.options.forwardKey.getBoundKeyTranslationKey()), true); KeyBinding.setKeyPressed(InputUtil.fromTranslationKey(c.options.leftKey.getBoundKeyTranslationKey()), true); KeyBinding.setKeyPressed(InputUtil.fromTranslationKey(c.options.jumpKey.getBoundKeyTranslationKey()), true); });
            run(14, c -> { KeyBinding.setKeyPressed(InputUtil.fromTranslationKey(c.options.leftKey.getBoundKeyTranslationKey()), false); KeyBinding.setKeyPressed(InputUtil.fromTranslationKey(c.options.jumpKey.getBoundKeyTranslationKey()), false); });
            run(60, c -> KeyBinding.setKeyPressed(InputUtil.fromTranslationKey(c.options.forwardKey.getBoundKeyTranslationKey()), false));
            run(10, c -> pressKey(c, GLFW.GLFW_KEY_B)); // кино-камера
            hold(5, c -> c.options.forwardKey, 120);
            run(5, c -> pressKey(c, GLFW.GLFW_KEY_B));
            // --- вертолёт
            server(10, s -> {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                p.stopRiding();
                var w = p.getServerWorld();
                w.getChunk(460 >> 4, -890 >> 4);
                var heli = new com.yompix.novograd.vehicle.HeliEntity(com.yompix.novograd.vehicle.Vehicles.HELI, w);
                heli.refreshPositionAndAngles(460.5, Story.topY(w, 460, -895), -895.5, 90, 0);
                w.spawnEntity(heli);
                p.teleport(w, 460.5, heli.getY() + 1, -895.5, 90, 10);
                p.startRiding(heli, true);
            });
            run(30, c -> { c.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_BACK); com.yompix.novograd.Novograd.LOG.info("[reel] heli {}", System.currentTimeMillis()); });
            hold(5, c -> c.options.jumpKey, 140);
            run(1, c -> { KeyBinding.setKeyPressed(InputUtil.fromTranslationKey(c.options.forwardKey.getBoundKeyTranslationKey()), true); KeyBinding.setKeyPressed(InputUtil.fromTranslationKey(c.options.jumpKey.getBoundKeyTranslationKey()), true); });
            run(100, c -> { KeyBinding.setKeyPressed(InputUtil.fromTranslationKey(c.options.jumpKey.getBoundKeyTranslationKey()), false); });
            run(60, c -> KeyBinding.setKeyPressed(InputUtil.fromTranslationKey(c.options.forwardKey.getBoundKeyTranslationKey()), false));
            run(5, c -> com.yompix.novograd.Novograd.LOG.info("[reel] end {}", System.currentTimeMillis()));
            run(20, MinecraftClient::scheduleStop);
            return;
        }
        if (mode.equals("runover")) {
            runoverMode();
            return;
        }
        if (mode.startsWith("pulse")) { // коллекции, «Хаос», экраны и вертолёт «Новоград-24», прожекторы, «Подвал»
            pulseMode(mode);
            return;
        }
        if (mode.equals("taxi")) {
            // такси по острову: вызвать к гаражу, сесть, доехать до Сити-Банка; лог расстояния до цели
            YompixSplash.markShown();
            pre(60, c -> c.createIntegratedServerLoader().start("Fresh2", () -> c.setScreen(new com.yompix.novograd.client.menu.LifeMenuScreen())));
            run(100, c -> { if (c.player != null && c.player.isDead()) c.player.requestRespawn(); });
            run(5, c -> { if (c.currentScreen != null) c.setScreen(null); });
            cmd(5, "time set 6000");
            server(5, s -> {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                var w = p.getServerWorld();
                String[] fr = System.getProperty("novograd.taxifrom", "120,-720").split(",");
                int fx = Integer.parseInt(fr[0]), fz = Integer.parseInt(fr[1]);
                w.getChunk(fx >> 4, fz >> 4);
                p.teleport(w, fx, Story.topY(w, fx, fz) + 1, fz, 0, 0);
                Life.get(p).money += 2000;
            });
            server(40, s -> com.yompix.novograd.life.Taxi.call(s.getPlayerManager().getPlayer(P), "bank_tower"));
            for (int i = 0; i < 40; i++) {
                final int n = i;
                server(100, s -> {
                    ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                    var cid = com.yompix.novograd.life.Taxi.carOf(p);
                    var car = cid == null ? null : p.getServerWorld().getEntity(cid);
                    if (car != null && p.getVehicle() == null && n > 2 && Life.get(p).taskKind.equals("taxi_wait")) { // «дошёл» до такси
                        if (car.squaredDistanceTo(p) > 64 * 64) p.teleport(p.getServerWorld(), car.getX() + 2, car.getY(), car.getZ(), 0, 0);
                        p.startRiding(car, true);
                    }
                    var to = Story.place(p.getServerWorld(), "bank_tower");
                    com.yompix.novograd.Novograd.LOG.info("[taxitest] t={} riding={} task={} pos={} car={} distToBank={}", n * 5, p.getVehicle() != null, Life.get(p).taskKind,
                            p.getBlockPos().toShortString(), car == null ? "-" : car.getBlockPos().toShortString(), to == null ? -1 : (int) Math.sqrt(p.squaredDistanceTo(to)));
                });
                if (i % 8 == 4) shot(1, "taxi_" + i);
            }
            run(20, MinecraftClient::scheduleStop);
            return;
        }
        if (mode.equals("phone")) {
            // телефон целиком, такси, новости, карта, титры (проверка после чистки старой игры)
            YompixSplash.markShown();
            pre(60, c -> c.createIntegratedServerLoader().start("Fresh2", () -> c.setScreen(new com.yompix.novograd.client.menu.LifeMenuScreen())));
            run(100, c -> { if (c.player != null && c.player.isDead()) c.player.requestRespawn(); });
            run(5, c -> { if (c.currentScreen != null) c.setScreen(null); });
            server(5, s -> {
                com.yompix.novograd.gta.News.post(s, "Прямо сейчас: вооружённый налёт на «Сити-Банк». Полиция стягивает силы", "ЭКСТРЕННО");
                com.yompix.novograd.gta.Economy.shock(s, "PORT", 0.06, "Порт подписал контракт с крупной судоходной компанией");
            });
            shot(40, "phone_news_banner");
            run(5, c -> c.setScreen(new com.yompix.novograd.client.screen.PhoneScreen(null)));
            shot(30, "phone_home");
            run(5, c -> c.setScreen(new com.yompix.novograd.client.screen.PhoneScreen("taxi")));
            shot(30, "phone_taxi");
            run(5, c -> c.setScreen(new com.yompix.novograd.client.screen.PhoneScreen("music")));
            run(5, c -> com.yompix.novograd.client.CarRadio.pocket(2));
            shot(30, "phone_radio");
            run(5, c -> { c.setScreen(null); for (char ch : "HESOYAM".toCharArray()) { var n = new net.minecraft.nbt.NbtCompound(); } });
            server(5, sv -> com.yompix.novograd.gta.Cheats.apply(sv.getPlayerManager().getPlayer(P), "HESOYAM"));
            shot(10, "phone_cheat");
            run(5, c -> c.setScreen(new com.yompix.novograd.client.screen.NewsScreen()));
            shot(30, "phone_news");
            run(5, c -> c.setScreen(new com.yompix.novograd.client.screen.FinanceScreen()));
            shot(30, "phone_finance");
            run(5, c -> c.setScreen(new com.yompix.novograd.client.screen.MapScreen()));
            shot(30, "phone_map");
            run(5, c -> c.setScreen(new com.yompix.novograd.client.story.CreditsScreen()));
            shot(140, "phone_credits");
            run(20, MinecraftClient::scheduleStop);
            return;
        }
        if (mode.equals("studio")) {
            // Киностудия: два дубля-актёра, облёт, фильтры, кинополосы
            YompixSplash.markShown();
            pre(60, c -> c.createIntegratedServerLoader().start("Fresh2", () -> c.setScreen(new com.yompix.novograd.client.menu.LifeMenuScreen())));
            run(100, c -> { if (c.player != null && c.player.isDead()) c.player.requestRespawn(); });
            run(5, c -> { if (c.currentScreen != null) c.setScreen(null); });
            cmd(5, "time set 13000");
            server(5, s -> {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                var w = p.getServerWorld();
                p.teleport(w, 40, Story.topY(w, 40, 20) + 1, 20, 0, 0);
            });
            run(80, c -> com.yompix.novograd.client.studio.Studio.devDemo(c, 0));
            shot(30, "studio_free");
            run(5, c -> com.yompix.novograd.client.studio.Studio.devDemo(c, 1));
            shot(10, "studio_orbit_set");
            run(5, c -> com.yompix.novograd.client.studio.Studio.devDemo(c, 2));
            for (int i = 0; i < 6; i++) shot(i == 0 ? 20 : 45, "studio_play_" + i);
            run(5, c -> com.yompix.novograd.client.studio.Studio.devDemo(c, 3));
            shot(30, "studio_noir");
            run(20, MinecraftClient::scheduleStop);
            return;
        }
        if (mode.equals("gps")) {
            // навигатор: от гаража до старта гонок, от центра до Ривьеры — по дорогам
            YompixSplash.markShown();
            pre(60, c -> c.createIntegratedServerLoader().start("Fresh2", () -> c.setScreen(new com.yompix.novograd.client.menu.LifeMenuScreen())));
            run(100, c -> { if (c.player != null && c.player.isDead()) c.player.requestRespawn(); });
            run(5, c -> { if (c.currentScreen != null) c.setScreen(null); });
            cmd(5, "gamemode creative " + P);
            cmd(5, "time set 6000");
            double[][] trips = {{60, 60, 120, -744}, {430, -430, -1320, 24}, {-300, 300, 600, -905}};
            for (int i = 0; i < trips.length; i++) {
                double[] tr = trips[i];
                final int n = i;
                server(5, s -> {
                    ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                    var w = p.getServerWorld();
                    w.getChunk((int) tr[0] >> 4, (int) tr[1] >> 4);
                    p.teleport(w, tr[0], Story.topY(w, (int) tr[0], (int) tr[1]) + 1, tr[1], 0, 0);
                });
                run(5, c -> {
                    var o = new net.minecraft.nbt.NbtCompound();
                    o.putString("hint", "Тест навигатора " + n);
                    o.putString("mission", "GPS");
                    o.putDouble("x", tr[2]); o.putDouble("y", -56); o.putDouble("z", tr[3]);
                    com.yompix.novograd.client.story.ClientStory.objective = o;
                    NovogradConfig.get().hudScale = 1.6f;
                });
                shot(60, "gps_" + i);
            }
            run(20, MinecraftClient::scheduleStop);
            return;
        }
        if (mode.equals("splash")) {
            pre(1, c -> {});
            for (int i = 0; i < 16; i++) preShot(i == 0 ? 60 : 22, "splash_" + i);
            pre(40, MinecraftClient::scheduleStop);
            return;
        }
        if (mode.equals("keyart")) {
            // кадры для заставки: без интерфейса, красивое время суток, машины в кадре
            YompixSplash.markShown();
            pre(60, c -> c.createIntegratedServerLoader().start("Fresh2", () -> c.setScreen(new com.yompix.novograd.client.menu.LifeMenuScreen())));
            run(100, c -> { if (c.player != null && c.player.isDead()) c.player.requestRespawn(); });
            run(5, c -> { if (c.currentScreen != null) c.setScreen(null); c.options.hudHidden = true; });
            cmd(5, "gamemode spectator " + P);
            cmd(5, "gamerule doDaylightCycle false");
            // {имя кадра, место камеры, на что смотреть, время суток, машины: «вариант@место»}
            String[][] shots = {
                    {"strip", "riv_strip+0,140,4", "riv_strip+0,40", "12600", "2@riv_strip+-3,60;9@riv_strip+3,80;10@riv_strip+-3,100"},
                    {"bridge", "bridge_n+60,30,14", "bridge_n+-120,0", "12300", "6@bridge_n+20,-2;9@bridge_n+0,2"},
                    {"night", "dt_2+3,30,2", "dt_2+0,-40,12", "17000", "5@dt_2+-3,10;0@dt_2+3,20"},
                    {"chase", "race_start+30,-5,2", "race_start+-20,0", "13000", "9@race_start+10,-2;6@race_start+22,-2;6@race_start+30,2"},
                    {"desert", "desert_road+40,-30,6", "desert_mesa", "11800", "8@desert_road+0,-20"},
                    {"mountains", "mtn_road+40,60,30", "mtn_top", "1500", "7@mtn_road+0,40"},
                    {"port", "port_dock+60,40,18", "port_dock", "12800", ""},
                    {"casino", "riv_casino+0,4,2", "riv_casino+0,-14,3", "18000", ""},
            };
            for (String[] sh : shots) {
                server(5, s -> {
                    ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                    var w = p.getServerWorld();
                    w.setTimeOfDay(Long.parseLong(sh[3]));
                    for (String carSpec : sh[4].split(";")) {
                        if (carSpec.isEmpty()) continue;
                        String[] cs = carSpec.split("@");
                        var at = com.yompix.novograd.story.Director.pos(w, cs[1]);
                        if (at == null) continue;
                        w.getChunk(net.minecraft.util.math.BlockPos.ofFloored(at));
                        var car = new com.yompix.novograd.entity.CarEntity(com.yompix.novograd.ModContent.CAR, w);
                        car.refreshPositionAndAngles(at.x, Story.topY(w, (int) at.x, (int) at.z), at.z, (float) (Math.random() * 360), 0);
                        car.setVariant(Integer.parseInt(cs[0]));
                        if (car.isPolice()) car.toggleLight(com.yompix.novograd.entity.CarEntity.HAZARD);
                        w.spawnEntity(car);
                    }
                    double[] v = com.yompix.novograd.story.Director.camera(w, sh[1]);
                    var look = com.yompix.novograd.story.Director.pos(w, sh[2]);
                    if (v == null || look == null) return;
                    float yaw = (float) Math.toDegrees(Math.atan2(-(look.x - v[0]), look.z - v[2]));
                    float pitch = (float) -Math.toDegrees(Math.atan2(look.y - v[1], Math.hypot(look.x - v[0], look.z - v[2])));
                    p.teleport(w, v[0], v[1], v[2], yaw, pitch);
                });
                shot(260, "keyart_" + sh[0]);
            }
            run(20, MinecraftClient::scheduleStop);
            return;
        }
        if (mode.startsWith("city")) { // город изнутри: лифт, офисы, квартиры, отель, дома, склад, заправка (новый мир)
            city();
            return;
        }
        if (mode.equals("fresh")) {
            // новая игра как у игрока: вступление без пропуска, потом несколько минут жизни
            YompixSplash.markShown();
            pre(60, c -> c.createIntegratedServerLoader().start("Fresh2", () -> c.setScreen(new com.yompix.novograd.client.menu.LifeMenuScreen())));
            for (int i = 0; i < 20; i++) shot(50, "fresh_c" + i);
            for (int i = 0; i < 8; i++) shot(200, "fresh_" + i);
            run(20, MinecraftClient::scheduleStop);
            return;
        }
        if (mode.equals("island3")) {
            YompixSplash.markShown();
            pre(60, c -> c.createIntegratedServerLoader().start("Island", () -> c.setScreen(new com.yompix.novograd.client.menu.LifeMenuScreen())));
            run(100, c -> { if (c.player != null && c.player.isDead()) c.player.requestRespawn(); });
            run(5, c -> { if (c.currentScreen != null) c.setScreen(null); });
            cmd(5, "gamemode spectator " + P);
            cmd(5, "time set 6000");
            String[] cams = {"cam_riv_far", "cam_riv_strip", "cam_bridge", "cam_mtn", "cam_desert", "lake_dock+0,-60,20", "village+30,0,15", "shop_clothes+0,-25,6", "riv_casino+0,25,8", "cam_island_far"};
            for (String cn : cams) {
                server(5, s -> {
                    ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                    var w = p.getServerWorld();
                    double[] v = com.yompix.novograd.story.Director.camera(w, cn);
                    if (v == null) return;
                    w.getChunk((int) v[0] >> 4, (int) v[2] >> 4);
                    float yaw = (float) v[3], pitch = (float) v[4];
                    if (cn.contains("+")) { // смотрим на базовое место
                        var b = Story.place(w, cn.substring(0, cn.indexOf('+')));
                        yaw = (float) Math.toDegrees(Math.atan2(-(b.x - v[0]), b.z - v[2]));
                        pitch = 20;
                    }
                    p.teleport(w, v[0], v[1], v[2], yaw, pitch);
                });
                shot(140, "isl3_" + cn.replaceAll("[^a-z_]", ""));
            }
            run(20, MinecraftClient::scheduleStop);
            return;
        }
        if (mode.equals("nogrief")) {
            // город — не стройка: копать/ставить в выживании нельзя; фразы девушек и парней
            YompixSplash.markShown();
            pre(60, c -> c.createIntegratedServerLoader().start("Fresh2", () -> c.setScreen(new com.yompix.novograd.client.menu.LifeMenuScreen())));
            cmd(80, "gamemode survival " + P);
            cmd(5, "give " + P + " minecraft:dirt 16");
            run(20, c -> {
                var hit = c.crosshairTarget;
                c.player.setPitch(80);
            });
            run(5, c -> {
                if (c.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult bh) {
                    var pos = bh.getBlockPos();
                    var before = c.world.getBlockState(pos).getBlock();
                    for (int i = 0; i < 40; i++) c.interactionManager.updateBlockBreakingProgress(pos, bh.getSide());
                    com.yompix.novograd.Novograd.LOG.info("[nogrief] copать {} -> client {} flag={}", before, c.world.getBlockState(pos).getBlock(), NovogradClient.noGrief);
                    c.player.getInventory().selectedSlot = 0;
                    c.interactionManager.interactBlock(c.player, net.minecraft.util.Hand.MAIN_HAND, bh);
                }
            });
            server(40, s -> {
                var p = s.getPlayerManager().getPlayer(P);
                var pos = p.getBlockPos().down();
                com.yompix.novograd.Novograd.LOG.info("[nogrief] сервер: под ногами {} над {} dirt в руке {}", p.getServerWorld().getBlockState(pos).getBlock(), p.getServerWorld().getBlockState(pos.up()).getBlock(), p.getMainHandStack().getCount());
                // фразы жителей: ближайшие девушка и парень
                var w = p.getServerWorld();
                var list = w.getEntitiesByClass(com.yompix.novograd.entity.CitizenEntity.class, p.getBoundingBox().expand(64), e -> e.getRole() == com.yompix.novograd.entity.CitizenEntity.Role.CITIZEN && e.getStoryId().isEmpty() && !e.isKid());
                int f = 0, m = 0;
                for (var e : list) {
                    boolean fem = com.yompix.novograd.entity.CitizenEntity.isSlim(e.getSkin());
                    if ((fem && f < 4) || (!fem && m < 4)) {
                        for (int i = 0; i < 2; i++) com.yompix.novograd.life.LifeActions.talk(e, p);
                        if (fem) f++; else m++;
                    }
                }
                com.yompix.novograd.Novograd.LOG.info("[nogrief] поговорили: девушек {} парней {}", f, m);
            });
            shot(20, "nogrief");
            run(20, MinecraftClient::scheduleStop);
            return;
        }
        if (mode.equals("first")) {
            // как новый игрок: новый мир «Новоград: Город», выживание, сюжет с начала — где Джейк, идёт ли первая миссия
            YompixSplash.markShown();
            pre(60, c -> {
                var data = new net.minecraft.resource.DataConfiguration(new net.minecraft.resource.DataPackSettings(List.of("vanilla", "fabric"), List.of()),
                        net.minecraft.resource.featuretoggle.FeatureFlags.DEFAULT_ENABLED_FEATURES);
                var info = new net.minecraft.world.level.LevelInfo("FirstTest" + System.currentTimeMillis() % 100000, net.minecraft.world.GameMode.SURVIVAL, false,
                        net.minecraft.world.Difficulty.NORMAL, false, new net.minecraft.world.GameRules(), data);
                var city = net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD_PRESET, com.yompix.novograd.Novograd.id("city"));
                c.createIntegratedServerLoader().createAndStart(info.getLevelName(), info, net.minecraft.world.gen.GeneratorOptions.createRandom(),
                        drm -> drm.get(net.minecraft.registry.RegistryKeys.WORLD_PRESET).getOrThrow(city).createDimensionsRegistryHolder(), new com.yompix.novograd.client.menu.LifeMenuScreen());
            });
            for (int i = 0; i < 60; i++) {
                final int k = i;
                if (i % 3 == 0) key(10, GLFW.GLFW_KEY_SPACE);
                server(30, s -> {
                    var pl = s.getPlayerManager().getPlayer(P);
                    if (pl == null) return;
                    var d = Life.get(pl);
                    var g = com.yompix.novograd.story.Npcs.find(pl.getServerWorld(), "gleb");
                    com.yompix.novograd.Novograd.LOG.info("[first] t={} ch {} m {} st {} started {} player {} gleb {}", k, d.chapter, d.mission, d.step, d.storyStarted,
                            pl.getBlockPos().toShortString(), g == null ? "НЕТ" : g.getBlockPos().toShortString() + (g.isAlive() ? "" : " мёртв") + " dist " + (int) g.distanceTo(pl));
                });
                if (i % 5 == 4) shot(1, "first_" + i);
                if (i == 20) {
                    // подойти к Джейку и нажать ПКМ, как игрок
                    server(2, s -> {
                        var pl = s.getPlayerManager().getPlayer(P);
                        var g = com.yompix.novograd.story.Npcs.find(pl.getServerWorld(), "gleb");
                        if (g != null) {
                            var dir = pl.getPos().subtract(g.getPos()).normalize();
                            pl.networkHandler.requestTeleport(g.getX() + dir.x * 1.8, g.getY(), g.getZ() + dir.z * 1.8, 0, 0);
                        }
                    });
                    run(10, c -> {
                        var g = c.world.getEntitiesByClass(com.yompix.novograd.entity.CitizenEntity.class, c.player.getBoundingBox().expand(6), e -> e.hasCustomName() && e.getCustomName().getString().startsWith("Джейк")).stream().findFirst().orElse(null);
                        com.yompix.novograd.Novograd.LOG.info("[first] client sees gleb: {}", g);
                        if (g != null) {
                            c.player.lookAt(net.minecraft.command.argument.EntityAnchorArgumentType.EntityAnchor.EYES, g.getEyePos());
                            c.interactionManager.interactEntity(c.player, g, net.minecraft.util.Hand.MAIN_HAND);
                        }
                    });
                    shot(20, "first_talk");
                }
                if (i > 20 && i < 30) { key(5, GLFW.GLFW_KEY_1); key(5, GLFW.GLFW_KEY_SPACE); }
                if (i == 30) {
                    // сесть в машину Джейка у ворот и доехать до старта гонок
                    server(5, s -> {
                        var pl = s.getPlayerManager().getPlayer(P);
                        var w = pl.getServerWorld();
                        var cars = w.getEntitiesByClass(com.yompix.novograd.entity.CarEntity.class, pl.getBoundingBox().expand(40), e -> true);
                        com.yompix.novograd.Novograd.LOG.info("[first] машин рядом: {}", cars.size());
                        if (!cars.isEmpty()) {
                            var car = cars.get(0);
                            pl.startRiding(car, true);
                            var to = com.yompix.novograd.story.Story.place(w, "race_start");
                            com.yompix.novograd.Novograd.LOG.info("[first] race_start {}", to);
                            if (to != null) car.teleport(w, to.x + 3, to.y + 1, to.z, java.util.Set.of(), car.getYaw(), 0);
                        }
                    });
                    shot(60, "first_race_start");
                }
                if (i > 30) { key(3, GLFW.GLFW_KEY_SPACE); }
            }
            run(20, MinecraftClient::scheduleStop);
            return;
        }
        if (mode.equals("missions")) {
            // проходим все миссии сюжета по шагам: шаг запускается без ошибок, цель есть, герой разговора на месте
            // -Dnovograd.newworld=true — в только что созданном мире (проверка новой генерации города)
            YompixSplash.markShown();
            if (Boolean.getBoolean("novograd.newworld")) pre(60, c -> {
                var data = new net.minecraft.resource.DataConfiguration(new net.minecraft.resource.DataPackSettings(List.of("vanilla", "fabric"), List.of()),
                        net.minecraft.resource.featuretoggle.FeatureFlags.DEFAULT_ENABLED_FEATURES);
                var info = new net.minecraft.world.level.LevelInfo("MissionTest" + System.currentTimeMillis() % 100000, net.minecraft.world.GameMode.CREATIVE, false,
                        net.minecraft.world.Difficulty.NORMAL, true, new net.minecraft.world.GameRules(), data);
                var city = net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD_PRESET, com.yompix.novograd.Novograd.id("city"));
                c.createIntegratedServerLoader().createAndStart(info.getLevelName(), info, net.minecraft.world.gen.GeneratorOptions.createRandom(),
                        drm -> drm.get(net.minecraft.registry.RegistryKeys.WORLD_PRESET).getOrThrow(city).createDimensionsRegistryHolder(), new com.yompix.novograd.client.menu.LifeMenuScreen());
            });
            else pre(60, c -> c.createIntegratedServerLoader().start("Island", () -> c.setScreen(new com.yompix.novograd.client.menu.LifeMenuScreen())));
            run(100, c -> { if (c.player != null && c.player.isDead()) c.player.requestRespawn(); });
            cmd(5, "gamemode creative " + P);
            cmd(5, "effect give " + P + " minecraft:resistance 99999 4 true");
            var chs = com.yompix.novograd.story.Story.root().getAsJsonArray("chapters");
            for (int ci = Integer.getInteger("novograd.missions.from", 0); ci < chs.size(); ci++) { // -Dnovograd.missions.from=5 — только акт VI
                var ms = chs.get(ci).getAsJsonObject().getAsJsonArray("missions");
                for (int mi = 0; mi < ms.size(); mi++) {
                    var steps = ms.get(mi).getAsJsonObject().getAsJsonArray("steps");
                    for (int si = 0; si < steps.size(); si++) {
                        final int fc = ci, fm = mi, fs = si;
                        String type = steps.get(si).getAsJsonObject().get("type").getAsString();
                        String npc = steps.get(si).getAsJsonObject().has("npc") ? steps.get(si).getAsJsonObject().get("npc").getAsString() : "";
                        String mid = ms.get(mi).getAsJsonObject().get("id").getAsString();
                        run(2, c -> { if (c.currentScreen != null) c.setScreen(null); });
                        server(3, s -> {
                            ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                            var d = Life.get(p);
                            d.storyStarted = true;
                            d.storyDone = false;
                            d.flags.add("end_cop"); // акт VI «Под присягой» открыт только копу
                            d.chapter = fc;
                            d.mission = fm;
                            d.step = fs;
                            try {
                                Story.reenter(p);
                            } catch (Throwable e) {
                                com.yompix.novograd.Novograd.LOG.error("[mcheck] {} {} {} ОШИБКА входа: {}", mid, fs, type, e.toString());
                            }
                        });
                        server(25, s -> {
                            ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                            if (p.hasVehicle()) p.stopRiding();
                            var t = Story.debugTarget(p);
                            if (t != null) {
                                var w = p.getServerWorld();
                                w.getChunk((int) t.x >> 4, (int) t.z >> 4);
                                p.teleport(w, t.x, Story.topY(w, (int) t.x, (int) t.z) + 1, t.z, p.getYaw(), 0);
                            }
                        });
                        server(30, s -> {
                            ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                            var d = Life.get(p);
                            var t = Story.debugTarget(p);
                            String note = "";
                            if (!npc.isEmpty()) {
                                var n = com.yompix.novograd.story.Npcs.find(p.getServerWorld(), npc);
                                note = n == null ? " НЕТ ГЕРОЯ " + npc : " герой " + npc + " в " + (int) n.distanceTo(p) + " м";
                            }
                            boolean needT = java.util.Set.of("goto", "talk", "collect", "carrace", "steal", "clear", "chase", "tune").contains(type);
                            String status = needT && t == null ? "НЕТ ЦЕЛИ" : "ок";
                            if (!npc.isEmpty() && (note.contains("НЕТ") || note.matches(".* в ([3-9][0-9]|[0-9]{3,}) м"))) status = "ГЕРОЙ ДАЛЕКО/НЕТ";
                            com.yompix.novograd.Novograd.LOG.info("[mcheck] {} шаг {} {} — {}{} (сейчас {}/{}/{})", mid, fs, type, status,
                                    t == null ? "" : " цель " + (int) t.x + "," + (int) t.y + "," + (int) t.z, note, d.chapter, d.mission, d.step);
                        });
                    }
                }
            }
            run(20, MinecraftClient::scheduleStop);
            return;
        }
        if (mode.equals("style")) {
            style();
            return;
        }
        if (mode.equals("casino")) {
            casino();
            return;
        }
        if (mode.equals("cars")) {
            cars();
            return;
        }
        if (mode.equals("guns")) {
            guns();
            return;
        }
        if (mode.equals("armory")) {
            armory(); // новые стволы, Мастерская, телохранители
            return;
        }
        if (mode.startsWith("scene")) {
            // «scene:глава:миссия:шаг» — прыгнуть прямо в кат-сцену и снимать её; потом радар и вода
            String[] a = mode.split(":");
            int ch = Integer.parseInt(a[1]), mi = Integer.parseInt(a[2]), st = Integer.parseInt(a[3]);
            YompixSplash.markShown();
            pre(60, c -> c.createIntegratedServerLoader().start("Island", () -> c.setScreen(new com.yompix.novograd.client.menu.LifeMenuScreen())));
            run(100, c -> { if (c.player != null && c.player.isDead()) c.player.requestRespawn(); });
            server(60, s -> {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                var d = Life.get(p);
                d.storyStarted = true;
                d.storyDone = false;
                if (ch >= 5) d.flags.add("end_cop"); // акт VI — ветка копа
                d.chapter = ch;
                d.mission = mi;
                d.step = st;
                d.age = 22;
                Story.applyAge(p, 22);
                Life.sync(p);
            });
            run(5, c -> { if (c.currentScreen != null) c.setScreen(null); });
            cmd(5, "time set 13500");
            server(5, s -> Story.reenter(s.getPlayerManager().getPlayer(P)));
            for (int i = 0; i < 14; i++) shot(36, "scene_" + i);
            run(20, c -> { if (c.currentScreen != null) c.setScreen(null); });
            // радар в машине по деловому центру
            cmd(10, "gamemode survival " + P);
            cmd(5, "time set 6000");
            server(5, s -> {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                var w = p.getServerWorld();
                w.getChunk(216 >> 4, -200 >> 4);
                p.teleport(w, 216.5, Story.topY(w, 216, -200) + 1, -200.5, 0, 10);
            });
            run(40, c -> {});
            server(5, s -> {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                var car = new com.yompix.novograd.entity.CarEntity(com.yompix.novograd.ModContent.CAR, p.getServerWorld());
                car.refreshPositionAndAngles(p.getX(), p.getY(), p.getZ(), 180, 0);
                p.getServerWorld().spawnEntity(car);
                p.startRiding(car, true);
            });
            shot(60, "radar_car");
            key(5, GLFW.GLFW_KEY_R);
            shot(15, "radar_radio");
            // вода и деревья
            server(5, s -> {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                p.stopRiding();
                p.changeGameMode(net.minecraft.world.GameMode.SPECTATOR);
                var w = p.getServerWorld();
                w.getChunk(-640 >> 4, -600 >> 4);
                p.teleport(w, -640.5, Story.topY(w, -640, -600) + 5, -600.5, 90, 20);
            });
            shot(80, "water_1");
            shot(30, "water_2");
            server(5, s -> {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                var w = p.getServerWorld();
                w.getChunk(-300 >> 4, -850 >> 4);
                p.teleport(w, -300.5, Story.topY(w, -300, -850) + 3, -850.5, 0, 10);
            });
            shot(80, "trees_1");
            run(20, MinecraftClient::scheduleStop);
            return;
        }
        if (mode.equals("gta")) {
            YompixSplash.markShown();
            server(150, s -> {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                var d = Life.get(p);
                d.storyStarted = true;
                d.storyDone = true;
                d.chapter = 99;
                d.age = 22;
                d.edu = 16;
                d.money = 3000;
                Story.applyAge(p, 22);
                Life.sync(p);
            });
            for (int i = 0; i < 4; i++) key(10, GLFW.GLFW_KEY_SPACE);
            run(5, c -> { if (c.currentScreen != null) c.setScreen(null); });
            cmd(10, "gamemode survival " + P);
            cmd(5, "time set 6000");
            cmd(5, "effect give " + P + " minecraft:resistance 600 4 true");
            server(5, s -> {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                var w = p.getServerWorld();
                int y = Story.topY(w, 0, 16);
                p.teleport(w, 0.5, y, 16.5, 180, 5);
                p.getInventory().insertStack(new net.minecraft.item.ItemStack(com.yompix.novograd.ModContent.PISTOL));
                p.getInventory().insertStack(new net.minecraft.item.ItemStack(com.yompix.novograd.ModContent.AMMO, 40));
                CitizenEntity v = new CitizenEntity(com.yompix.novograd.ModContent.CITIZEN, w);
                v.setup(CitizenEntity.RANDOM_FROM + 5, CitizenEntity.Role.GANG, false, "Ящер Тест");
                v.refreshPositionAndAngles(0.5, y, 10.5, 0, 0);
                w.spawnEntity(v);
            });
            for (int i = 0; i < 3; i++) {
                server(i == 0 ? 20 : 12, s -> {
                    ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                    var w = p.getServerWorld();
                    var v = w.getEntitiesByClass(CitizenEntity.class, p.getBoundingBox().expand(12), c -> c.getName().getString().equals("Ящер Тест")).stream().findFirst().orElse(null);
                    if (v != null) com.yompix.novograd.gta.Weapons.shoot(w, p, p.getEyePos(), v.getEyePos().subtract(p.getEyePos()), 6, 0, 48, false);
                });
                shot(2, "g_shot" + i);
            }
            server(5, s -> {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                var w = p.getServerWorld();
                int y = Story.topY(w, 0, 16);
                // заставим прохожего «увидеть» убийство: ударим обычного жителя
                CitizenEntity v = new CitizenEntity(com.yompix.novograd.ModContent.CITIZEN, w);
                v.setup(CitizenEntity.RANDOM_FROM + 7, CitizenEntity.Role.CITIZEN, false, "Прохожий");
                v.refreshPositionAndAngles(3.5, y, 12.5, 0, 0);
                w.spawnEntity(v);
                com.yompix.novograd.gta.Weapons.shoot(w, p, p.getEyePos(), v.getEyePos().subtract(p.getEyePos()), 30, 0, 48, false);
                com.yompix.novograd.Novograd.LOG.info("[gta] stars after crimes {}", com.yompix.novograd.gta.Wanted.stars(p));
                for (var e : w.getOtherEntities(p, p.getBoundingBox().expand(12), e -> true))
                    com.yompix.novograd.Novograd.LOG.info("[gta] near {} fire {} alive {}", e.getType(), e.getFireTicks(), e.isAlive());
                int fires = 0;
                for (var bp : net.minecraft.util.math.BlockPos.iterate(p.getBlockPos().add(-12, -2, -12), p.getBlockPos().add(12, 4, 12)))
                    if (w.getBlockState(bp).isOf(net.minecraft.block.Blocks.FIRE)) fires++;
                com.yompix.novograd.Novograd.LOG.info("[gta] fire blocks {}", fires);
            });
            shot(10, "g_after");
            run(5, c -> c.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_BACK));
            for (int i = 0; i < 6; i++) {
                shot(60, "g_police" + i);
                server(1, s -> {
                    ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                    var w = p.getServerWorld();
                    long cops = w.getEntitiesByClass(CitizenEntity.class, p.getBoundingBox().expand(60), c -> c.getRole() == CitizenEntity.Role.POLICE).size();
                    long cars = w.getEntitiesByClass(com.yompix.novograd.entity.CarEntity.class, p.getBoundingBox().expand(60), c -> c.isPolice()).size();
                    com.yompix.novograd.Novograd.LOG.info("[gta] stars {} cops {} policeCars {} hp {}", com.yompix.novograd.gta.Wanted.stars(p), cops, cars, p.getHealth());
                });
            }
            run(5, c -> c.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON));
            server(5, s -> {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                double[] c = com.yompix.novograd.world.TownState.get(p.getServerWorld()).place("cam_south_a");
                if (c != null) p.teleport(p.getServerWorld(), c[0], c[1], c[2], (float) c[3], (float) c[4]);
                com.yompix.novograd.Novograd.LOG.info("[gta] south built: {}", c != null);
            });
            cmd(3, "gamemode spectator " + P);
            shot(60, "g_south");
            cmd(3, "gamemode survival " + P);
            server(5, s -> {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                var w = p.getServerWorld();
                double[] c = com.yompix.novograd.world.TownState.get(w).place("garage_bay");
                if (c == null) return;
                p.teleport(w, c[0], c[1] + 0.2, c[2] + 3, (float) c[3], 10);
                var car = new com.yompix.novograd.entity.CarEntity(com.yompix.novograd.ModContent.CAR, w);
                car.refreshPositionAndAngles(c[0], c[1] + 0.2, c[2], (float) c[3], 0);
                w.spawnEntity(car);
                com.yompix.novograd.gta.Garage.open(p);
            });
            shot(30, "g_garage");
            server(5, s -> com.yompix.novograd.gta.Garage.action(s.getPlayerManager().getPlayer(P), "nitro", 0));
            server(5, s -> com.yompix.novograd.gta.Garage.action(s.getPlayerManager().getPlayer(P), "paint", 6));
            server(5, s -> com.yompix.novograd.gta.Garage.action(s.getPlayerManager().getPlayer(P), "hide", 0));
            shot(20, "g_garage2");
            server(5, s -> com.yompix.novograd.Novograd.LOG.info("[gta] stars after garage {}", com.yompix.novograd.gta.Wanted.stars(s.getPlayerManager().getPlayer(P))));
            run(5, c -> c.setScreen(null));
            server(5, s -> {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                com.yompix.novograd.gta.Wanted.add(p, 3, "тест", 3);
                com.yompix.novograd.gta.Wanted.busted(p);
            });
            shot(30, "g_busted");
            run(20, MinecraftClient::scheduleStop);
            return;
        }
        if (mode.equals("ui")) {
            // оформление «Тёмные улицы»: до входа в мир — настоящая заставка, главное меню, настройки, ванильные настройки
            // с панорамой и экран загрузки; в мире — пауза, телефон, HUD, «миссия пройдена», разговор
            // транспорт (параллельная работа) ещё не регистрируется при запуске — подгружаем класс заранее, пока реестры открыты
            try {
                Class.forName("com.yompix.novograd.vehicle.Vehicles");
            } catch (Throwable ignored) {
            }
            pre(1, c -> {});
            int[] at = {30, 22, 22, 20, 14, 12, 12, 10, 12, 16, 20, 26};
            for (int i = 0; i < at.length; i++) preShot(at[i], "ui_splash_" + i);
            pre(60, c -> { if (!(c.currentScreen instanceof com.yompix.novograd.client.menu.LifeMenuScreen)) c.setScreen(new com.yompix.novograd.client.menu.LifeMenuScreen()); });
            preShot(50, "ui_menu");
            pre(5, c -> c.setScreen(new com.yompix.novograd.client.menu.SettingsScreen(c.currentScreen)));
            preShot(25, "ui_settings_menu");
            pre(5, c -> c.setScreen(new net.minecraft.client.gui.screen.option.OptionsScreen(new com.yompix.novograd.client.menu.LifeMenuScreen(), c.options)));
            preShot(25, "ui_options_vanilla");
            pre(5, c -> c.createIntegratedServerLoader().start("Island", () -> c.setScreen(new com.yompix.novograd.client.menu.LifeMenuScreen())));
            preShot(2, "ui_loading");
            for (int i = 0; i < 4; i++) key(40, GLFW.GLFW_KEY_SPACE);
            run(10, c -> c.setScreen(null));
            cmd(5, "gamemode creative " + P);
            cmd(5, "kill @e[type=novograd:citizen,distance=..40]");
            cmd(5, "time set 13000");
            run(5, c -> { if (c.player != null && c.player.isDead()) c.player.requestRespawn(); c.setScreen(null); });
            run(5, c -> c.setScreen(new com.yompix.novograd.client.menu.LifePauseScreen()));
            shot(25, "ui_pause");
            run(5, c -> c.setScreen(new com.yompix.novograd.client.menu.SettingsScreen(c.currentScreen)));
            shot(20, "ui_settings");
            key(2, GLFW.GLFW_KEY_TAB);
            key(2, GLFW.GLFW_KEY_DOWN);
            shot(15, "ui_settings_gfx");
            run(5, c -> c.setScreen(new net.minecraft.client.gui.screen.option.OptionsScreen(c.currentScreen, c.options)));
            shot(20, "ui_options_ingame");
            run(5, c -> c.setScreen(new PhoneScreen(null)));
            shot(25, "ui_phone");
            run(5, c -> c.setScreen(new PhoneScreen("jobs")));
            shot(20, "ui_phone_jobs");
            run(5, c -> c.setScreen(new PhoneScreen("bank")));
            shot(20, "ui_phone_bank");
            run(5, c -> c.setScreen(new com.yompix.novograd.client.screen.ShopScreen(true)));
            shot(20, "ui_shop");
            run(5, c -> c.setScreen(new com.yompix.novograd.client.screen.AtmScreen(1200, 340)));
            shot(20, "ui_atm");
            run(5, c -> c.setScreen(null));
            cmd(5, "novograd money " + P + " 12450");
            cmd(5, "give " + P + " novograd:pistol");
            cmd(5, "give " + P + " novograd:ammo 24");
            run(5, c -> {
                c.player.getInventory().selectedSlot = 0;
                for (int i = 0; i < 9; i++) if (c.player.getInventory().getStack(i).isOf(com.yompix.novograd.ModContent.PISTOL)) c.player.getInventory().selectedSlot = i;
                NovogradClient.wantedStars = 3;
                NovogradClient.wantedSeen = true;
                var o = new net.minecraft.nbt.NbtCompound();
                o.putString("chapter", "Глава 1");
                o.putString("mission", "Новый дом");
                o.putString("hint", "Доберись до мастерской Глеба на Портовой");
                o.putString("where", "Портовая улица, 12");
                o.putDouble("x", c.player.getX() + 60);
                o.putDouble("y", c.player.getY());
                o.putDouble("z", c.player.getZ() + 25);
                com.yompix.novograd.client.story.ClientStory.objective = o;
            });
            for (int i = 0; i < 6; i++) key(4, GLFW.GLFW_KEY_SPACE);
            run(2, c -> { if (c.currentScreen != null) c.setScreen(null); com.yompix.novograd.client.story.ClientStory.titleAt = -1; com.yompix.novograd.client.story.ClientStory.missionDoneAt = -1; });
            shot(16, "ui_hud_banner");
            run(40, c -> { if (c.currentScreen != null) c.setScreen(null); });
            shot(4, "ui_hud");
            run(2, c -> com.yompix.novograd.client.story.ClientStory.missionDone("Новый дом"));
            shot(14, "ui_passed");
            run(80, c -> { if (c.currentScreen != null) c.setScreen(null); });
            run(2, c -> com.yompix.novograd.client.story.StoryHud.gtaTitle("ЗАДЕРЖАН", "Штраф $250 · оружие изъято"));
            shot(20, "ui_busted");
            run(90, c -> com.yompix.novograd.client.story.ClientStory.title("Глава 2", "Тёмные улицы"));
            shot(40, "ui_chapter");
            run(110, c -> {
                var n = new net.minecraft.nbt.NbtCompound();
                var lines = new net.minecraft.nbt.NbtList();
                var l = new net.minecraft.nbt.NbtCompound();
                l.putString("who", "tema");
                l.putString("name", "Тёма");
                l.putInt("color", 0xFF7A3D);
                l.putString("text", "Слушай, если хочешь в этом городе выжить — держись подальше от Портовой после заката. Там свои правила.");
                l.putString("voice", "");
                lines.add(l);
                n.put("lines", lines);
                var choices = new net.minecraft.nbt.NbtList();
                for (String t : new String[]{"Я сам разберусь.", "Кто там главный?", "Поехали вместе."}) {
                    var ch = new net.minecraft.nbt.NbtCompound();
                    ch.putString("text", t);
                    ch.put("reply", new net.minecraft.nbt.NbtList());
                    choices.add(ch);
                }
                n.put("choices", choices);
                c.setScreen(new com.yompix.novograd.client.story.DialogScreen(n, true));
            });
            shot(30, "ui_dialog_typing");
            shot(60, "ui_dialog");
            key(5, GLFW.GLFW_KEY_SPACE);
            key(15, GLFW.GLFW_KEY_DOWN);
            shot(15, "ui_dialog_choice");
            run(20, MinecraftClient::scheduleStop);
            return;
        }
        if (mode.equals("pause")) {
            YompixSplash.markShown();
            for (int i = 0; i < 4; i++) key(40, GLFW.GLFW_KEY_SPACE);
            run(10, c -> c.setScreen(new com.yompix.novograd.client.menu.LifePauseScreen()));
            shot(20, "pause");
            run(5, c -> c.setScreen(new net.minecraft.client.gui.screen.OpenToLanScreen(c.currentScreen)));
            shot(20, "lan");
            run(20, MinecraftClient::scheduleStop);
            return;
        }
        if (mode.equals("gfx")) {
            YompixSplash.markShown();
            server(150, s -> {
                var d = Life.get(s.getPlayerManager().getPlayer(P));
                d.storyStarted = true;
                d.storyDone = true;
                d.chapter = 99;
            });
            for (int i = 0; i < 4; i++) key(10, GLFW.GLFW_KEY_SPACE);
            run(5, c -> { if (c.currentScreen != null) c.setScreen(null); c.options.hudHidden = true; });
            cmd(10, "gamemode spectator " + P);
            cmd(5, "tp " + P + " 30 -45 30 135 12");
            String[][] shots = {{"12600", "1", "g_sunset"}, {"12600", "0", "g_sunset_lite"}, {"17000", "1", "g_night"}, {"17000", "0", "g_night_lite"}, {"6000", "1", "g_day"}};
            for (String[] v : shots) {
                cmd(5, "time set " + v[0]);
                run(2, c -> { com.yompix.novograd.client.render.ColorGrade.enabled = true; com.yompix.novograd.client.render.ColorGrade.quality = Float.parseFloat(v[1]); });
                shot(40, v[2]);
            }
            run(2, c -> com.yompix.novograd.client.render.ColorGrade.enabled = false);
            shot(20, "g_off");
            run(20, MinecraftClient::scheduleStop);
            return;
        }
        if (mode.equals("atm")) {
            YompixSplash.markShown();
            server(150, s -> {
                var d = Life.get(s.getPlayerManager().getPlayer(P));
                d.storyStarted = true;
                d.storyDone = true;
                d.chapter = 99;
                d.money = 800;
                Life.sync(s.getPlayerManager().getPlayer(P));
            });
            for (int i = 0; i < 4; i++) key(10, GLFW.GLFW_KEY_SPACE);
            run(5, c -> c.setScreen(new com.yompix.novograd.client.screen.AtmScreen(800, 0)));
            shot(8, "atm_card");
            shot(30, "atm_menu");
            run(2, c -> { if (c.currentScreen instanceof com.yompix.novograd.client.screen.AtmScreen a) a.pressWithdraw(250); });
            shot(10, "atm_proc");
            shot(22, "atm_cash1");
            shot(10, "atm_cash2");
            run(30, c -> c.setScreen(null));
            server(5, s -> {
                var inv = s.getPlayerManager().getPlayer(P).getInventory();
                for (int i = 0; i < inv.size(); i++) if (!inv.getStack(i).isEmpty())
                    com.yompix.novograd.Novograd.LOG.info("[atm] {} x{}", inv.getStack(i).getName().getString(), inv.getStack(i).getCount());
                com.yompix.novograd.Novograd.LOG.info("[atm] balance {}", Life.get(s.getPlayerManager().getPlayer(P)).money);
            });
            run(20, MinecraftClient::scheduleStop);
            return;
        }
        if (mode.equals("wheel")) {
            YompixSplash.markShown();
            server(150, s -> {
                var d = Life.get(s.getPlayerManager().getPlayer(P));
                d.storyStarted = true;
                d.storyDone = true;
                d.chapter = 99;
                d.age = 20;
                Story.applyAge(s.getPlayerManager().getPlayer(P), 20);
            });
            for (int i = 0; i < 4; i++) key(10, GLFW.GLFW_KEY_SPACE);
            run(5, c -> { if (c.currentScreen != null) c.setScreen(null); });
            cmd(10, "gamemode creative " + P);
            cmd(5, "time set 6000");
            server(5, s -> {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                var w = p.getServerWorld();
                int y = Story.topY(w, -40, 24);
                var car = new com.yompix.novograd.entity.CarEntity(com.yompix.novograd.ModContent.CAR, w);
                car.refreshPositionAndAngles(-45.5, y, 24.5, -90, 0);
                car.setVariant(1);
                w.spawnEntity(car);
                p.startRiding(car, true);
            });
            cmd(5, "time set 15000");
            server(5, s -> {
                if (s.getPlayerManager().getPlayer(P).getVehicle() instanceof com.yompix.novograd.entity.CarEntity car) {
                    car.toggleLight(com.yompix.novograd.entity.CarEntity.HEADLIGHTS);
                    car.toggleLight(com.yompix.novograd.entity.CarEntity.LEFT);
                }
            });
            run(10, c -> { c.player.setYaw(-90); c.player.setPitch(30); });
            shot(20, "wh1");
            shot(4, "wh1b");
            run(5, c -> { c.player.setPitch(10); c.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_FRONT); });
            shot(20, "wh2");
            run(5, c -> c.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_BACK));
            shot(20, "wh3");
            shot(4, "wh3b");
            shot(4, "wh3c");
            run(20, MinecraftClient::scheduleStop);
            return;
        }
        if (mode.equals("stuck")) {
            YompixSplash.markShown();
            server(150, s -> {
                var d = Life.get(s.getPlayerManager().getPlayer(P));
                d.storyStarted = true;
                d.storyDone = true;
                d.chapter = 99;
            });
            for (int i = 0; i < 4; i++) key(10, GLFW.GLFW_KEY_SPACE);
            run(5, c -> { if (c.currentScreen != null) c.setScreen(null); });
            cmd(10, "gamemode spectator " + P);
            cmd(5, "gamerule doDaylightCycle true");
            cmd(5, "time set 0");
            // держим все чанки города загруженными: игрок летает по кругу над городом
            for (int k = 0; k < 40; k++) {
                int kk = k;
                server(60, s -> {
                    ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                    double a = kk * 0.6;
                    p.teleport(p.getServerWorld(), Math.cos(a) * 40 + 20, -30, Math.sin(a) * 40, 0, 90);
                    if (kk % 8 == 0) s.getCommandManager().executeWithPrefix(s.getCommandSource(), "time add 2500");
                });
            }
            run(20, MinecraftClient::scheduleStop);
            return;
        }
        if (mode.equals("traffic")) {
            YompixSplash.markShown();
            server(150, s -> {
                var d = Life.get(s.getPlayerManager().getPlayer(P));
                d.storyStarted = true;
                d.storyDone = true;
                d.chapter = 99;
            });
            for (int i = 0; i < 4; i++) key(10, GLFW.GLFW_KEY_SPACE);
            run(5, c -> { if (c.currentScreen != null) c.setScreen(null); });
            cmd(10, "gamemode spectator " + P);
            cmd(5, "time set 6000");
            server(5, s -> {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                var w = p.getServerWorld();
                int y = Story.topY(w, -40, 24);
                var taxi = new com.yompix.novograd.entity.CarEntity(com.yompix.novograd.ModContent.CAR, w);
                taxi.refreshPositionAndAngles(-45.5, y, 24.5, -90, 0);
                taxi.setVariant(5);
                w.spawnEntity(taxi);
                CitizenEntity drv = new CitizenEntity(com.yompix.novograd.ModContent.CITIZEN, w);
                drv.setup(20, CitizenEntity.Role.CITIZEN, true, "Таксист");
                drv.refreshPositionAndAngles(-45.5, y, 24.5, -90, 0);
                w.spawnEntity(drv);
                drv.startRiding(taxi, true);
                var parked = new com.yompix.novograd.entity.CarEntity(com.yompix.novograd.ModContent.CAR, w);
                parked.refreshPositionAndAngles(-30.5, y, 24.5, -90, 0);
                w.spawnEntity(parked);
                CitizenEntity walker = new CitizenEntity(com.yompix.novograd.ModContent.CITIZEN, w);
                walker.setup(CitizenEntity.RANDOM_FROM + 3, CitizenEntity.Role.CITIZEN, true, "Зевака");
                walker.refreshPositionAndAngles(-12.5, y, 24.5, 90, 0);
                w.spawnEntity(walker);
                taxi.setRoute(java.util.List.of(new net.minecraft.util.math.Vec3d(12.5, y, 24.5)), 0.5f,
                        () -> com.yompix.novograd.Novograd.LOG.info("[traffic] arrived"));
                if (Boolean.getBoolean("novograd.ride")) p.startRiding(taxi, true);
                else p.teleport(w, -28.5, y + 22, 24.5, -90, 70);
            });
            server(5, s -> {
                var w = s.getPlayerManager().getPlayer(P).getServerWorld();
                for (var c : w.getEntitiesByClass(CitizenEntity.class, s.getPlayerManager().getPlayer(P).getBoundingBox().expand(200), c -> c.getStoryId().isEmpty()))
                    com.yompix.novograd.Novograd.LOG.info("[names] {} {}", CitizenEntity.isSlim(c.getSkin()) ? "Ж" : "М", c.getName().getString());
            });
            for (int i = 0; i < 12; i++) {
                shot(40, "tr" + i);
                server(1, s -> {
                    var w = s.getPlayerManager().getPlayer(P).getServerWorld();
                    for (var car : w.getEntitiesByClass(com.yompix.novograd.entity.CarEntity.class, s.getPlayerManager().getPlayer(P).getBoundingBox().expand(80), c -> c.isTaxi()))
                        com.yompix.novograd.Novograd.LOG.info("[traffic] taxi at {} {} route {}", String.format("%.1f", car.getX()), String.format("%.1f", car.getZ()), car.onRoute());
                });
            }
            run(20, MinecraftClient::scheduleStop);
            return;
        }
        if (mode.equals("scooter")) {
            YompixSplash.markShown();
            server(150, s -> {
                var d = Life.get(s.getPlayerManager().getPlayer(P));
                d.storyStarted = true;
                d.storyDone = true;
                d.chapter = 99;
            });
            for (int i = 0; i < 4; i++) key(10, GLFW.GLFW_KEY_SPACE);
            run(5, c -> { if (c.currentScreen != null) c.setScreen(null); });
            cmd(10, "gamemode survival " + P);
            server(5, s -> {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                var w = p.getServerWorld();
                int y = Story.topY(w, -40, 24);
                var sc = new com.yompix.novograd.entity.CarEntity(com.yompix.novograd.ModContent.SCOOTER, w);
                sc.refreshPositionAndAngles(-50.5, y, 22.5, -90, 0);
                w.spawnEntity(sc);
                p.teleport(w, -50.5, y, 22.5, -90, 0);
                p.startRiding(sc, true);
            });
            run(20, c -> c.options.forwardKey.setPressed(true));
            for (int i = 0; i < 6; i++) shot(20, "sc" + i);
            run(5, c -> c.options.forwardKey.setPressed(false));
            run(20, MinecraftClient::scheduleStop);
            return;
        }
        if (mode.equals("c6")) {
            YompixSplash.markShown();
            server(150, s -> {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                var d = Life.get(p);
                d.storyStarted = true;
                d.storyDone = true;
                d.age = 18;
                d.chapter = 5;
                d.mission = 0;
                d.step = 0;
                d.prevMode = -1;
                Story.onJoin(p);
                Story.onClientReady(p);
            });
            for (int i = 0; i < 4; i++) shot(80, "c6_" + i);
            for (int i = 0; i < 8; i++) { key(60, GLFW.GLFW_KEY_SPACE); shot(20, "c6_k" + i); }
            server(5, s -> {
                var d = Life.get(s.getPlayerManager().getPlayer(P));
                com.yompix.novograd.Novograd.LOG.info("[c6] chapter {} mission {} step {} done {}", d.chapter, d.mission, d.step, d.storyDone);
            });
            run(20, MinecraftClient::scheduleStop);
            return;
        }
        if (mode.equals("east")) {
            YompixSplash.markShown();
            server(150, s -> {
                var d = Life.get(s.getPlayerManager().getPlayer(P));
                d.storyStarted = true;
                d.storyDone = true;
                d.chapter = 99;
            });
            for (int i = 0; i < 4; i++) key(10, GLFW.GLFW_KEY_SPACE);
            run(5, c -> { if (c.currentScreen != null) c.setScreen(null); c.options.hudHidden = false; });
            cmd(10, "gamemode spectator " + P);
            cmd(5, "time set 6000");
            boolean quick = Boolean.getBoolean("novograd.quick");
            String[][] views = quick ? new String[0][] : new String[][]{{"cam_east_a", "e_aerial"}, {"cam_east_b", "e_aerial2"}, {"cam_pizza_a", "e_pizza"}, {"cam_club_a", "e_club_out"}};
            for (String[] v : views) {
                server(5, s -> {
                    ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                    double[] c = com.yompix.novograd.world.TownState.get(p.getServerWorld()).place(v[0]);
                    if (c != null) p.teleport(p.getServerWorld(), c[0], c[1], c[2], (float) c[3], (float) c[4]);
                });
                shot(60, v[1]);
            }
            String[][] inside = quick ? new String[0][] : new String[][]{{"pizza_counter", "0", "e_pizza_in", "20"}, {"gym_center", "180", "e_gym_in", "15"}, {"club_floor", "180", "e_club_in", "10"}};
            for (String[] v : inside) {
                if (v[2].equals("e_club_in")) cmd(5, "time set 16000");
                server(5, s -> {
                    ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                    double[] c = com.yompix.novograd.world.TownState.get(p.getServerWorld()).place(v[0]);
                    if (c != null) p.teleport(p.getServerWorld(), c[0], c[1] + 1.2, c[2], (float) c[3] + Float.parseFloat(v[1]), Float.parseFloat(v[3]));
                });
                shot(60, v[2]);
            }
            cmd(5, "gamemode creative " + P);
            run(5, c -> c.setScreen(new com.yompix.novograd.client.screen.RhythmScreen("run")));
            shot(60, "e_rhythm");
            run(5, c -> c.setScreen(null));
            server(5, s -> {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                Life.get(p).weight = 105;
                Life.sync(p);
                com.yompix.novograd.life.Fitness.apply(p, Life.get(p));
                double[] c = com.yompix.novograd.world.TownState.get(p.getServerWorld()).place("gym_door");
                p.teleport(p.getServerWorld(), c[0], c[1], c[2] + 3, 0, 10);
            });
            run(5, c -> c.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_FRONT));
            shot(30, "e_fat");
            server(5, s -> {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                Life.get(p).weight = 55;
                Life.sync(p);
            });
            shot(20, "e_slim");
            run(20, MinecraftClient::scheduleStop);
            return;
        }
        if (mode.equals("home")) {
            YompixSplash.markShown();
            server(150, s -> {
                var d = Life.get(s.getPlayerManager().getPlayer(P));
                d.storyStarted = true;
                d.storyDone = true;
            });
            for (int i = 0; i < 4; i++) key(10, GLFW.GLFW_KEY_SPACE);
            run(5, c -> { if (c.currentScreen != null) c.setScreen(null); c.options.hudHidden = false; });
            cmd(10, "gamemode creative " + P);
            cmd(5, "gamerule doDaylightCycle true");
            cmd(5, "time set 12950");
            server(5, s -> {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                var w = p.getServerWorld();
                double[] door = com.yompix.novograd.world.TownState.get(w).place("gleb_door");
                double rad = Math.toRadians(door[3]);
                for (int i = 0; i < 4; i++) {
                    CitizenEntity c = new CitizenEntity(com.yompix.novograd.ModContent.CITIZEN, w);
                    c.setup(CitizenEntity.RANDOM_FROM + i * 3, CitizenEntity.Role.CITIZEN, false, "Сосед " + i);
                    c.setHome("gleb");
                    c.refreshPositionAndAngles(door[0] - Math.sin(rad) * -6 + (i - 1.5) * 2, door[1], door[2] + Math.cos(rad) * -6, 0, 0);
                    w.spawnEntity(c);
                }
                // камера на крыльце, смотрит на дверь
                p.teleport(w, door[0] + Math.sin(rad) * 5 + Math.cos(rad) * 3, door[1] + 1.5, door[2] - Math.cos(rad) * 5 + Math.sin(rad) * 3, (float) door[3] - 30, 10);
            });
            shot(60, "h_walk");
            shot(100, "h_door");
            shot(100, "h_door2");
            cmd(5, "time set 18500");
            server(5, s -> {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                var w = p.getServerWorld();
                double[] bed = com.yompix.novograd.world.TownState.get(w).place("gleb_bed");
                double[] liv = com.yompix.novograd.world.TownState.get(w).place("gleb_living");
                p.teleport(w, liv[0], liv[1] + 0.2, liv[2], 0, 20);
                p.lookAt(net.minecraft.command.argument.EntityAnchorArgumentType.EntityAnchor.EYES, new net.minecraft.util.math.Vec3d(bed[0], bed[1], bed[2]));
                for (var c : w.getEntitiesByClass(CitizenEntity.class, p.getBoundingBox().expand(20), c -> true))
                    com.yompix.novograd.Novograd.LOG.info("[home] {} act {} inv {} at {}", c.getName().getString(), c.getActivity(), c.isInvisible(), c.getPos());
            });
            shot(500, "h_night");
            server(5, s -> {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                double[] liv = com.yompix.novograd.world.TownState.get(p.getServerWorld()).place("gleb_living");
                p.teleport(p.getServerWorld(), liv[0] + 3, liv[1] + 0.2, liv[2] + 1, 0, 15);
                p.lookAt(net.minecraft.command.argument.EntityAnchorArgumentType.EntityAnchor.EYES, new net.minecraft.util.math.Vec3d(liv[0] - 2, liv[1], liv[2] - 1));
            });
            shot(20, "h_night2");
            cmd(5, "time set 23700");
            shot(200, "h_morning");
            shot(200, "h_morning2");
            server(5, s -> {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                for (var c : p.getServerWorld().getEntitiesByClass(CitizenEntity.class, p.getBoundingBox().expand(30), c -> c.getName().getString().startsWith("Сосед")))
                    com.yompix.novograd.Novograd.LOG.info("[home-am] {} act {} inv {} at {}", c.getName().getString(), c.getActivity(), c.isInvisible(), c.getPos());
            });
            // встречи на улице: ставим пары лицом к лицу
            server(5, s -> {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                var w = p.getServerWorld();
                int y = Story.topY(w, 0, 10);
                CitizenEntity.Activity[] acts = {CitizenEntity.Activity.HANDSHAKE, CitizenEntity.Activity.HUG, CitizenEntity.Activity.ARGUE, CitizenEntity.Activity.FIGHT, CitizenEntity.Activity.LAUGH, CitizenEntity.Activity.UPSET};
                for (int i = 0; i < acts.length; i++) {
                    CitizenEntity a1 = new CitizenEntity(com.yompix.novograd.ModContent.CITIZEN, w), a2 = new CitizenEntity(com.yompix.novograd.ModContent.CITIZEN, w);
                    a1.setup(CitizenEntity.RANDOM_FROM + 2 * i, CitizenEntity.Role.CITIZEN, true, "A" + i);
                    a2.setup(CitizenEntity.RANDOM_FROM + 2 * i + 5, CitizenEntity.Role.CITIZEN, true, "B" + i);
                    double x = -12.5 + i * 5;
                    double gap = acts[i] == CitizenEntity.Activity.HUG ? 0.65 : acts[i] == CitizenEntity.Activity.HANDSHAKE ? 0.9 : 1.4;
                    a1.refreshPositionAndAngles(x - gap / 2, y, 10.5, -90, 0);
                    a2.refreshPositionAndAngles(x + gap / 2, y, 10.5, 90, 0);
                    w.spawnEntity(a1);
                    w.spawnEntity(a2);
                    a1.sceneWith(a2, acts[i], 2000);
                    a2.sceneWith(a1, acts[i] == CitizenEntity.Activity.LAUGH || acts[i] == CitizenEntity.Activity.UPSET ? CitizenEntity.Activity.TALK : acts[i], 2000);
                }
                p.teleport(w, 0.5, y + 1.2, 18.5, 180, 10);
            });
            cmd(5, "time set 5000");
            shot(40, "i1");
            shot(9, "i2");
            shot(9, "i3");
            run(20, MinecraftClient::scheduleStop);
            return;
        }
        if (mode.equals("geo")) {
            YompixSplash.markShown();
            server(150, s -> {
                var d = Life.get(s.getPlayerManager().getPlayer(P));
                d.storyStarted = true;
                d.storyDone = true;
            });
            for (int i = 0; i < 4; i++) key(10, GLFW.GLFW_KEY_SPACE);
            run(5, c -> {
                if (c.currentScreen != null) c.setScreen(null);
                c.setCameraEntity(c.player);
                c.options.hudHidden = false;
            });
            cmd(20, "time set 5000");
            cmd(5, "gamemode creative " + P);
            server(5, s -> {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                var w = p.getServerWorld();
                double bx = 0.5, bz = 10.5;
                int y = Story.topY(w, 0, 10);
                CitizenEntity.Activity[] acts = {CitizenEntity.Activity.IDLE, CitizenEntity.Activity.TALK, CitizenEntity.Activity.WAVE, CitizenEntity.Activity.PHONE,
                        CitizenEntity.Activity.THINK, CitizenEntity.Activity.DANCE, CitizenEntity.Activity.CHEER, CitizenEntity.Activity.SIT};
                int[] skins = {8, 11, 9, 10, 14, 17, 12, 16};
                for (int i = 0; i < acts.length; i++) {
                    CitizenEntity c = new CitizenEntity(com.yompix.novograd.ModContent.CITIZEN, w);
                    c.setup(skins[i], CitizenEntity.Role.CITIZEN, true, acts[i].name());
                    c.refreshPositionAndAngles(bx - 10.5 + i * 3, y, bz, 0, 0);
                    c.setHeadYaw(0);
                    c.setBodyYaw(0);
                    w.spawnEntity(c);
                    c.lockActivity(acts[i]);
                }
                // двое идут и бегут
                for (int i = 0; i < 2; i++) {
                    CitizenEntity c = new CitizenEntity(com.yompix.novograd.ModContent.CITIZEN, w);
                    c.setup(20 + i * 5, CitizenEntity.Role.CITIZEN, false, i == 0 ? "WALK" : "RUN");
                    c.refreshPositionAndAngles(bx - 12, y, bz + 5 + i * 2, -90, 0);
                    w.spawnEntity(c);
                    c.getNavigation().startMovingTo(bx + 14, y, bz + 5 + i * 2, i == 0 ? 0.6 : 1.3);
                }
                p.teleport(w, bx, y + 1.2, bz + 10, 180, 8);
            });
            server(5, s -> {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                var w = p.getServerWorld();
                int y = Story.topY(w, 0, 18);
                // такси с водителем-жителем и игроком на пассажирском — проверить посадку
                var car = new com.yompix.novograd.entity.CarEntity(com.yompix.novograd.ModContent.CAR, w);
                car.refreshPositionAndAngles(-20.5, y, 18.5, 90, 0);
                car.setVariant(5);
                w.spawnEntity(car);
                CitizenEntity drv = new CitizenEntity(com.yompix.novograd.ModContent.CITIZEN, w);
                drv.setup(20, CitizenEntity.Role.CITIZEN, true, "Таксист");
                drv.refreshPositionAndAngles(-20.5, y, 18.5, 90, 0);
                w.spawnEntity(drv);
                drv.startRiding(car, true);
            });
            shot(30, "g1");
            for (int i = 0; i < 8; i++) shot(3, "w" + i);
            server(5, s -> {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                var w = p.getServerWorld();
                for (CitizenEntity c : w.getEntitiesByClass(CitizenEntity.class, p.getBoundingBox().expand(30), c -> c.getVehicle() == null && c.getNavigation().isIdle())) {
                    c.setYaw(90); c.setBodyYaw(90); c.setHeadYaw(90);
                }
                p.teleport(w, -4.5, Story.topY(w, -4, 15) + 0.1, 15.5, 180, 5);
            });
            run(3, c -> {
                for (var e : c.world.getEntities()) if (e instanceof CitizenEntity ce && ce.getVehicle() == null) { ce.bodyYaw = 90; ce.prevBodyYaw = 90; ce.headYaw = 90; ce.prevHeadYaw = 90; }
            });
            shot(10, "side1");
            server(3, s -> {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                p.teleport(p.getServerWorld(), 4.5, Story.topY(p.getServerWorld(), 4, 15) + 0.1, 15.5, 180, 5);
            });
            shot(10, "side2");
            shot(7, "g2");
            shot(7, "g3");
            server(5, s -> {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                p.teleport(p.getServerWorld(), -7.5, p.getY(), 13.2, 180, 12);
            });
            shot(30, "g4");
            server(5, s -> {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                p.teleport(p.getServerWorld(), 5.5, p.getY(), 13.2, 180, 12);
            });
            shot(30, "g5");
            server(5, s -> {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                p.teleport(p.getServerWorld(), -14, p.getY(), 12, -100, 5);
            });
            shot(30, "g6");
            server(5, s -> {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                p.teleport(p.getServerWorld(), -20.8, Story.topY(p.getServerWorld(), -20, 23) + 0.4, 21.0, 180, 18);
            });
            shot(30, "t_side");
            server(5, s -> {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                p.getAbilities().flying = true; p.sendAbilitiesUpdate();
                p.teleport(p.getServerWorld(), -20.5, Story.topY(p.getServerWorld(), -20, 18) + 3.3, 20.3, 180, 60);
            });
            shot(30, "t_top");
            server(5, s -> {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                var cars = p.getServerWorld().getEntitiesByClass(com.yompix.novograd.entity.CarEntity.class, p.getBoundingBox().expand(10), c -> true);
                if (!cars.isEmpty()) p.startRiding(cars.get(0), true);
            });
            run(5, c -> { c.player.setYaw(90); c.player.setPitch(12); });
            shot(20, "t_inside");
            run(5, c -> { c.player.setYaw(-60); c.player.setPitch(25); });
            shot(20, "t_back");
            run(5, c -> { c.player.setYaw(90); c.player.setPitch(20); c.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_BACK); });
            shot(20, "t_3rd");
            // такси само везёт: игрок пассажир, руль у таксиста
            server(2, s -> {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                if (p.getVehicle() instanceof com.yompix.novograd.entity.CarEntity car) {
                    car.setRoute(java.util.List.of(car.getPos().add(-25, 0, 0)), 0.5f, () -> com.yompix.novograd.Novograd.LOG.info("[ride] arrived, controller={}", car.getControllingPassenger()));
                    com.yompix.novograd.Novograd.LOG.info("[ride] start at {} controller={}", car.getPos(), car.getControllingPassenger());
                }
            });
            shot(40, "t_ride1");
            server(2, s -> com.yompix.novograd.Novograd.LOG.info("[ride] player now at {} vehicle={}", s.getPlayerManager().getPlayer(P).getPos(), s.getPlayerManager().getPlayer(P).getVehicle()));
            shot(60, "t_ride2");
            server(2, s -> {
                for (var car : s.getPlayerManager().getPlayer(P).getServerWorld().getEntitiesByClass(com.yompix.novograd.entity.CarEntity.class, s.getPlayerManager().getPlayer(P).getBoundingBox().expand(10), c -> true))
                    for (var e : car.getPassengerList())
                        com.yompix.novograd.Novograd.LOG.info("[seat] car {} {} {} yaw {} -> {} seat {} at {} {} {}", car.getX(), car.getY(), car.getZ(), car.getYaw(), e.getType(), car.seatOf(e), e.getX(), e.getY(), e.getZ());
            });
            run(2, c -> {
                for (var e : c.world.getEntities()) if (e.getVehicle() != null)
                    com.yompix.novograd.Novograd.LOG.info("[seat-client] {} at {} {} {} pose {} vis {}", e.getType(), e.getX(), e.getY(), e.getZ(), e.getPose(), !e.isInvisible());
            });
            run(5, c -> c.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_FRONT));
            shot(30, "t_front");
            run(20, MinecraftClient::scheduleStop);
            return;
        }
        if (System.getProperty("novograd.autotest").equals("taxi")) {
            taxiOnly();
            return;
        }
        YompixSplash.markShown();
        run(80, c -> c.setScreen(new YompixSplash(null)));
        shot(30, "00_splash_logo");
        shot(70, "00b_splash_title");
        run(2, c -> c.setScreen(null));
        // сюжет стартует сам: кат-сцена облёта
        shot(100, "01_intro_cutscene");
        shot(120, "02_intro_cutscene2");
        key(5, GLFW.GLFW_KEY_SPACE);            // пропустить кат-сцену
        shot(40, "03_chapter_title");
        shot(140, "04_mom_dialog");
        spaces(12);
        run(5, c -> pressKey(c, GLFW.GLFW_KEY_1));
        spaces(4);
        shot(30, "05_objective_dad");
        talk(5, "dad");
        shot(30, "06_dad_dialog");
        spaces(10);
        run(10, c -> c.setScreen(new PhoneScreen("story")));
        shot(30, "07_phone_story");
        run(5, c -> c.setScreen(null));
        shot(20, "08_hud_go_school");
        // дальше по сюжету — к школе, знакомство с Тёмой
        server(5, s -> {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
            var v = Story.place(p.getServerWorld(), "school_front");
            p.teleport(p.getServerWorld(), v.x, v.y, v.z + 3, 180, 0);
        });
        shot(80, "09_tema_dialog");
        spaces(8);
        run(5, c -> pressKey(c, GLFW.GLFW_KEY_2));
        spaces(4);
        shot(40, "10_gleb_target");
        // ряд жителей — проверить 3D-причёски, бороды, рост и движения
        server(5, s -> {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
            var w = p.getServerWorld();
            double bx = 60, bz = 60;
            int y = Story.topY(w, (int) bx, (int) bz);
            for (int i = 0; i < 18; i++) {
                CitizenEntity c = new CitizenEntity(com.yompix.novograd.ModContent.CITIZEN, w);
                int skin = i < 8 ? 8 + i : CitizenEntity.RANDOM_FROM + (i - 8) * 2;
                c.setup(skin, CitizenEntity.Role.CITIZEN, true, CitizenEntity.SKINS[skin]);
                c.refreshPositionAndAngles(bx - 13 + i * 1.5, y, bz, 0, 0);
                c.setHeadYaw(0);
                c.lockActivity(CitizenEntity.Activity.values()[i % 8]);
                if (i % 5 == 3) c.setScale(0.7f);
                w.spawnEntity(c);
            }
            p.teleport(w, bx, y + 1, bz + 6, 180, 8);
        });
        shot(80, "11_lineup");
        server(5, s -> s.getPlayerManager().getPlayer(P).teleport(s.getOverworld(), 60, Story.topY(s.getOverworld(), 60, 60) + 1, 63, 180, 15));
        shot(40, "12_lineup_close");
        // взрослый — онлайн-казино и награды
        server(5, s -> {
            var d = Life.get(s.getPlayerManager().getPlayer(P));
            d.edu = 12;
            d.money = 5000;
            Life.sync(s.getPlayerManager().getPlayer(P));
        });
        run(10, c -> c.setScreen(new OnlineCasinoScreen()));
        run(10, c -> NovogradClient.send("bj", nbt("action", "deal", 10)));
        shot(30, "13_blackjack");
        run(5, c -> click(c, "crash"));
        run(5, c -> NovogradClient.send("crash_start", nbt(null, null, 10)));
        shot(70, "14_crash");
        run(30, c -> click(c, "coin"));
        run(5, c -> NovogradClient.send("coin", nbt(null, null, 10)));
        shot(15, "15_coin_spin");
        run(5, c -> c.setScreen(new PhoneScreen("trophy")));
        shot(30, "16_trophies");
        run(5, c -> c.setScreen(new PhoneScreen(null)));
        shot(30, "17_phone_home");
        // 0.3: карта, такси с автопилотом, вид из салона, «!» над жителем
        run(5, c -> c.setScreen(null));
        server(5, s -> s.getCommandManager().executeWithPrefix(s.getCommandSource(), "time set 4000"));
        run(5, c -> c.setScreen(new com.yompix.novograd.client.screen.MapScreen()));
        shot(40, "20_map");
        run(5, c -> c.setScreen(null));
        server(5, s -> {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
            var v = Story.place(p.getServerWorld(), "school_front");
            p.teleport(p.getServerWorld(), v.x, v.y, v.z + 4, 180, 5);
            com.yompix.novograd.life.Taxi.call(p, "shop_door");
        });
        shot(200, "21_taxi_coming");
        shot(300, "22_taxi_arrived");
        server(5, s -> {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
            var cars = p.getServerWorld().getEntitiesByClass(com.yompix.novograd.entity.CarEntity.class, p.getBoundingBox().expand(30), c -> c.isTaxi() && c.hasPassengers());
            if (!cars.isEmpty()) p.startRiding(cars.get(0), true);
        });
        shot(120, "23_inside_taxi");
        run(5, c -> c.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_BACK));
        shot(120, "24_taxi_driving");
        run(5, c -> c.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON));
        server(5, s -> {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
            p.stopRiding();
            var w = p.getServerWorld();
            var list = w.getEntitiesByClass(CitizenEntity.class, p.getBoundingBox().expand(60), c -> c.getStoryId().isEmpty() && c.getRole() == CitizenEntity.Role.CITIZEN);
            if (!list.isEmpty()) {
                var c = list.get(0);
                c.setQuest(true);
                p.teleport(w, c.getX(), c.getY(), c.getZ() + 4, 180, 0);
            }
        });
        shot(60, "25_quest_marker");
        // кадры для фона главного меню: ночной неон, казино, город сверху, школа днём
        run(5, c -> c.setScreen(null));
        cmd(5, "gamemode spectator " + P);
        cmd(5, "time set 14500");
        run(5, c -> c.options.hudHidden = true);
        cmd(5, "tp " + P + " -38 -53 20 -110 10");
        shot(260, "bg1");
        cmd(5, "tp " + P + " 4 -50 30 0 12");
        shot(80, "bg2");
        cmd(5, "tp " + P + " 30 -25 70 135 30");
        shot(100, "bg3");
        cmd(5, "time set 3000");
        cmd(5, "tp " + P + " 6 -52 24 160 6");
        shot(80, "bg4");
        run(5, c -> c.options.hudHidden = false);
        run(5, c -> c.setScreen(new com.yompix.novograd.client.menu.LifeMenuScreen()));
        shot(60, "18_main_menu");
        run(5, c -> c.setScreen(new com.yompix.novograd.client.menu.LifePauseScreen()));
        shot(30, "19_pause");
        run(20, MinecraftClient::scheduleStop);
    }

    private static void taxiOnly() {
        YompixSplash.markShown();
        cmd(200, "time set 4000");
        cmd(5, "novograd money " + P + " 5000");
        server(5, s -> {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
            var v = Story.place(p.getServerWorld(), "school_front");
            p.teleport(p.getServerWorld(), v.x, v.y, v.z + 6, 180, 5);
            com.yompix.novograd.life.Taxi.call(p, "shop_door");
        });
        for (int i = 0; i < 6; i++) shot(100, "t" + i);
        server(5, s -> {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
            var cars = p.getServerWorld().getEntitiesByClass(com.yompix.novograd.entity.CarEntity.class, p.getBoundingBox().expand(60), c -> c.isTaxi() && c.hasPassengers());
            if (!cars.isEmpty()) p.startRiding(cars.get(0), true);
        });
        for (int i = 6; i < 12; i++) shot(100, "t" + i);
        run(20, MinecraftClient::scheduleStop);
    }

    private static net.minecraft.nbt.NbtCompound nbt(String k, String v, int bet) {
        var n = new net.minecraft.nbt.NbtCompound();
        if (k != null) n.putString(k, v);
        n.putInt("bet", bet);
        return n;
    }

    private static void click(MinecraftClient c, String tab) {
        if (c.currentScreen instanceof OnlineCasinoScreen) {
            try {
                var f = OnlineCasinoScreen.class.getDeclaredField("tab");
                f.setAccessible(true);
                f.set(c.currentScreen, tab);
            } catch (ReflectiveOperationException ignored) {
            }
        }
    }

    /** «casino»: лобби, «Краш» с автовыводом, новые игры, затем зал «Золотая корона» на Ривьере и меню у дилера. */
    private static void casino() {
        YompixSplash.markShown();
        pre(60, c -> c.createIntegratedServerLoader().start("Island", () -> c.setScreen(new com.yompix.novograd.client.menu.LifeMenuScreen())));
        run(100, c -> { if (c.player != null && c.player.isDead()) c.player.requestRespawn(); });
        server(60, s -> {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
            var d = Life.get(p);
            d.storyStarted = true;
            d.storyDone = true;
            d.chapter = 99;
            d.age = 22;
            d.money = 50000;
            Story.applyAge(p, 22);
            Life.sync(p);
            p.getServerWorld().setTimeOfDay(6000);
            p.getServerWorld().setWeather(6000, 0, false, false);
        });
        run(5, c -> { if (c.currentScreen != null) c.setScreen(null); });
        cmd(5, "gamemode creative " + P);
        run(10, c -> c.setScreen(new com.yompix.novograd.client.screen.casino.CasinoLobbyScreen()));
        shot(30, "casino_lobby");
        // «Краш»: несколько раундов с автовыводом ×1.5 — набирается история, потом ручной раунд
        run(5, c -> c.setScreen(new OnlineCasinoScreen("crash")));
        for (int i = 0; i < 6; i++) run(i == 0 ? 10 : 115, c -> NovogradClient.send("crash_start", cg(null, null, 25, "auto", 150)));
        run(115, c -> NovogradClient.send("crash_start", cg(null, null, 50, "auto", 0)));
        shot(40, "casino_crash_running");
        shot(200, "casino_crash_after");
        // покер
        run(5, c -> c.setScreen(new com.yompix.novograd.client.screen.casino.PokerScreen()));
        run(10, c -> NovogradClient.send("cg", cg("poker", "deal", 25, null, 0)));
        run(20, c -> { if (c.currentScreen instanceof com.yompix.novograd.client.screen.casino.PokerScreen ps) ps.advise(); });
        shot(10, "casino_poker_hold");
        run(5, c -> { if (c.currentScreen instanceof com.yompix.novograd.client.screen.casino.PokerScreen ps) ps.draw(); });
        shot(30, "casino_poker_result");
        // кости
        run(5, c -> c.setScreen(new com.yompix.novograd.client.screen.casino.DiceScreen()));
        run(10, c -> NovogradClient.send("cg", cg("dice", "roll", 50, "pick", 0)));
        shot(8, "casino_dice_roll");
        shot(40, "casino_dice_result");
        // колесо
        run(5, c -> c.setScreen(new com.yompix.novograd.client.screen.casino.WheelScreen()));
        run(10, c -> NovogradClient.send("cg", cg("wheel", "spin", 50, "pick", 2)));
        shot(30, "casino_wheel_spin");
        shot(140, "casino_wheel_result");
        // тараканьи бега
        run(5, c -> c.setScreen(new com.yompix.novograd.client.screen.casino.RaceScreen()));
        run(20, c -> NovogradClient.send("cg", cg("race", "run", 50, "pick", 0)));
        shot(90, "casino_race_mid");
        shot(130, "casino_race_finish");
        // плинко
        run(5, c -> c.setScreen(new com.yompix.novograd.client.screen.casino.PlinkoScreen()));
        for (int i = 0; i < 4; i++) run(i == 0 ? 10 : 6, c -> NovogradClient.send("cg", cg("plinko", "drop", 25, "risk", 2)));
        shot(14, "casino_plinko_drop");
        shot(60, "casino_plinko_done");
        // мины
        run(5, c -> c.setScreen(new com.yompix.novograd.client.screen.casino.MinesScreen()));
        run(10, c -> NovogradClient.send("cg", cg("mines", "start", 50, "mines", 3)));
        for (int cell : new int[]{6, 12, 18}) run(8, c -> NovogradClient.send("cg", cg("mines", "open", 50, "cell", cell)));
        shot(15, "casino_mines_play");
        run(5, c -> NovogradClient.send("cg", cg("mines", "cash", 50, null, 0)));
        shot(20, "casino_mines_end");
        run(5, c -> c.setScreen(null));
        // зал на Ривьере: подходим к «riv_casino» — он строится, потом снимаем внутри
        cmd(5, "gamemode spectator " + P);
        server(5, s -> {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
            double[] pl = com.yompix.novograd.world.Island.place("riv_casino");
            p.teleport(p.getServerWorld(), pl[0], pl[1] + 2, pl[2] + 8, 180, 10);
        });
        shot(160, "casino_riv_outside");
        server(5, s -> {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
            var st = com.yompix.novograd.world.CasinoBuilder.state(p.getServerWorld());
            if (st.hall != null) p.teleport(p.getServerWorld(), st.hall.getX() + 0.5, st.hall.getY() + 3.5, st.hall.getZ() + 10.5, 180, 14);
        });
        shot(60, "casino_hall_north");
        server(5, s -> {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
            var st = com.yompix.novograd.world.CasinoBuilder.state(p.getServerWorld());
            if (st.hall != null) p.teleport(p.getServerWorld(), st.hall.getX() + 0.5, st.hall.getY() + 4.5, st.hall.getZ() - 9.5, 0, 18);
        });
        shot(40, "casino_hall_south");
        server(5, s -> {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
            var st = com.yompix.novograd.world.CasinoBuilder.state(p.getServerWorld());
            if (st.hall != null) p.teleport(p.getServerWorld(), st.hall.getX() - 9.5, st.hall.getY() + 5.5, st.hall.getZ() + 9.5, 225, 22);
        });
        shot(40, "casino_hall_corner");
        // разговор с дилером → «игровой стол»
        server(5, s -> {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
            var dealer = p.getServerWorld().getEntitiesByClass(CitizenEntity.class, p.getBoundingBox().expand(40),
                    e -> e.getRole() == CitizenEntity.Role.DEALER).stream().findFirst();
            dealer.ifPresent(e -> com.yompix.novograd.life.LifeActions.talk(e, p));
        });
        shot(30, "casino_hall_dealer_menu");
        run(20, MinecraftClient::scheduleStop);
    }

    private static net.minecraft.nbt.NbtCompound cg(String g, String a, int bet, String k, int v) {
        var n = new net.minecraft.nbt.NbtCompound();
        if (g != null) n.putString("g", g);
        if (a != null) n.putString("a", a);
        n.putInt("bet", bet);
        if (k != null) n.putInt(k, v);
        return n;
    }

    /** «style»: магазины одежды, цирюльня, зал и гардероб (стиль SA) — строит их у игрока и снимает экраны. */
    private static void style() {
        YompixSplash.markShown();
        pre(60, c -> c.createIntegratedServerLoader().start("Island", () -> c.setScreen(new com.yompix.novograd.client.menu.LifeMenuScreen())));
        run(100, c -> { if (c.player != null && c.player.isDead()) c.player.requestRespawn(); });
        final double[] o = new double[3];
        server(60, s -> {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
            var d = Life.get(p);
            d.storyStarted = true;
            d.storyDone = true;
            d.chapter = 99;
            d.age = 22;
            d.money = 25000;
            d.look = new com.yompix.novograd.life.Appearance();
            d.wardrobe.clear();
            d.wardrobe.addAll(com.yompix.novograd.life.StyleCatalog.STARTER);
            d.strength = 20;
            d.stamina = 10;
            d.gymDay = -1;
            Story.applyAge(p, 22);
            Life.sync(p);
            var w = p.getServerWorld();
            w.setTimeOfDay(6000);
            w.setWeather(6000, 0, false, false);
            // площадка далеко от города (за краем острова), чтобы не трогать улицы
            int bx = 2600, bz = 2600;
            w.getChunk(bx >> 4, bz >> 4);
            int by = Math.max(w.getSeaLevel() + 1, w.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, bx, bz));
            net.minecraft.util.math.BlockPos at = new net.minecraft.util.math.BlockPos(bx, by, bz);
            for (int x = -52; x <= 52; x++)
                for (int z = -42; z <= 18; z++) {
                    w.setBlockState(at.add(x, -1, z), (Math.abs(z - 6) <= 3 ? net.minecraft.block.Blocks.BLACK_CONCRETE : net.minecraft.block.Blocks.SMOOTH_STONE).getDefaultState(), 2);
                    w.setBlockState(at.add(x, -2, z), net.minecraft.block.Blocks.STONE.getDefaultState(), 2);
                    for (int y = 0; y < 12; y++) w.setBlockState(at.add(x, y, z), net.minecraft.block.Blocks.AIR.getDefaultState(), 2);
                }
            for (int x = -52; x <= 52; x += 4) w.setBlockState(at.add(x, -1, 6), net.minecraft.block.Blocks.YELLOW_CONCRETE.getDefaultState(), 2);
            p.teleport(w, bx + 0.5, by, bz + 10.5, 180, 0);
            o[0] = at.getX();
            o[1] = at.getY();
            o[2] = at.getZ();
            java.util.List<com.yompix.novograd.world.ShopBuilder.Spot> spots = new java.util.ArrayList<>();
            var kinds = com.yompix.novograd.world.ShopBuilder.Kind.values();
            for (int i = 0; i < kinds.length; i++)
                spots.add(new com.yompix.novograd.world.ShopBuilder.Spot(kinds[i], at.add((i - 1) * 24 - 12, 0, -22), net.minecraft.util.math.Direction.SOUTH));
            com.yompix.novograd.world.ShopBuilder.placeAll(w, spots);
            p.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
        });
        for (int i = 0; i < 6; i++) key(10, GLFW.GLFW_KEY_SPACE);
        run(5, c -> { if (c.currentScreen != null) c.setScreen(null); c.options.hudHidden = true; });
        run(40, c -> { if (c.currentScreen != null) c.setScreen(null); });
        // фасады снаружи
        styleGo(40, o, 0, 14, 180, 8, "style_street");
        // «Шмот-Базар»: внутри, потом экран магазина
        styleGo(20, o, -36, -25, 180, 10, "style_cheap_inside");
        server(5, s -> com.yompix.novograd.life.Style.open(s.getPlayerManager().getPlayer(P), "cheap"));
        run(5, c -> c.options.hudHidden = false);
        shot(30, "style_cheap_screen");
        styleScreen(5, 0, 6, 2);      // олимпийка с лампасами
        shot(20, "style_cheap_tracksuit");
        key(5, GLFW.GLFW_KEY_ENTER);
        styleScreen(15, 1, 1, 0);     // спортивные штаны
        key(5, GLFW.GLFW_KEY_ENTER);
        styleScreen(15, 2, 1, 0);     // кроссовки белые
        key(5, GLFW.GLFW_KEY_ENTER);
        styleScreen(15, 3, 0, 1);     // кепка чёрная
        key(5, GLFW.GLFW_KEY_ENTER);
        shot(20, "style_cheap_bought");
        // «Бархат»
        run(5, c -> c.setScreen(null));
        server(5, s -> {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
            p.teleport(p.getServerWorld(), o[0] - 12 + 0.5, o[1], o[2] - 25 + 0.5, 180, 10);
        });
        run(5, c -> c.options.hudHidden = true);
        shot(30, "style_lux_inside");
        server(5, s -> com.yompix.novograd.life.Style.open(s.getPlayerManager().getPlayer(P), "lux"));
        run(5, c -> c.options.hudHidden = false);
        styleScreen(20, 0, 4, 0);     // костюм-тройка
        shot(20, "style_lux_suit");
        key(5, GLFW.GLFW_KEY_ENTER);
        styleScreen(15, 1, 0, 0);     // брюки
        key(5, GLFW.GLFW_KEY_ENTER);
        styleScreen(15, 2, 0, 0);     // лаковые туфли
        key(5, GLFW.GLFW_KEY_ENTER);
        styleScreen(15, 3, 1, 0);     // федора
        key(5, GLFW.GLFW_KEY_ENTER);
        styleScreen(15, 4, 1, 0);     // авиаторы
        key(5, GLFW.GLFW_KEY_ENTER);
        shot(20, "style_lux_bought");
        // цирюльня
        run(5, c -> c.setScreen(null));
        server(5, s -> {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
            p.teleport(p.getServerWorld(), o[0] + 12 + 0.5, o[1], o[2] - 25 + 0.5, 180, 10);
        });
        run(5, c -> c.options.hudHidden = true);
        shot(30, "style_barber_inside");
        server(5, s -> com.yompix.novograd.life.Style.open(s.getPlayerManager().getPlayer(P), "barber"));
        run(5, c -> c.options.hudHidden = false);
        styleScreen(20, 3, 3, -1);    // внешность: кожа 4
        key(5, GLFW.GLFW_KEY_ENTER);
        styleScreen(15, 0, 4, -1);    // ирокез
        shot(20, "style_barber_mohawk");
        styleScreen(5, 0, 5, -1);     // афро
        shot(20, "style_barber_afro");
        key(5, GLFW.GLFW_KEY_ENTER);
        styleScreen(15, 1, 0, -1);    // чёрный
        key(5, GLFW.GLFW_KEY_ENTER);
        styleScreen(15, 2, 5, -1);    // борода с усами
        key(5, GLFW.GLFW_KEY_ENTER);
        shot(20, "style_barber_done");
        // гардероб: снять шляпу и посмотреть
        run(5, c -> c.setScreen(null));
        server(5, s -> com.yompix.novograd.life.Style.open(s.getPlayerManager().getPlayer(P), "cheap"));
        run(10, c -> c.setScreen(null));
        server(5, s -> com.yompix.novograd.life.Style.open(s.getPlayerManager().getPlayer(P), "wardrobe"));
        styleScreen(20, 0, 0, -1);
        shot(20, "style_wardrobe");
        // зал
        run(5, c -> c.setScreen(null));
        server(5, s -> {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
            p.teleport(p.getServerWorld(), o[0] + 36 + 0.5, o[1], o[2] - 25 + 0.5, 180, 10);
        });
        run(5, c -> c.options.hudHidden = true);
        shot(30, "style_gym_inside");
        server(5, s -> com.yompix.novograd.life.Style.open(s.getPlayerManager().getPlayer(P), "gym"));
        run(5, c -> c.options.hudHidden = false);
        shot(20, "style_gym_menu");
        run(5, c -> { if (c.currentScreen instanceof com.yompix.novograd.client.style.GymScreen g) g.autostart("bench"); });
        for (int i = 0; i < 260; i++) {
            final int k = i;
            run(1, c -> { if (c.currentScreen instanceof com.yompix.novograd.client.style.GymScreen g) g.autotest(k); });
            if (i == 70) shot(0, "style_gym_bench");
        }
        shot(40, "style_gym_result");
        run(5, c -> { if (c.currentScreen instanceof com.yompix.novograd.client.style.GymScreen g) g.close(); });
        server(140, s -> com.yompix.novograd.life.Style.open(s.getPlayerManager().getPlayer(P), "gym"));
        run(10, c -> { if (c.currentScreen instanceof com.yompix.novograd.client.style.GymScreen g) g.autostart("run"); });
        for (int i = 0; i < 440; i++) {
            final int k = i;
            run(1, c -> { if (c.currentScreen instanceof com.yompix.novograd.client.style.GymScreen g && k % 3 == 0) g.autotest(k / 3); });
            if (i == 200) shot(0, "style_gym_run");
        }
        shot(40, "style_gym_run_result");
        // телефон → «Стиль»
        run(5, c -> c.setScreen(new PhoneScreen(null)));
        shot(30, "style_phone");
        run(5, c -> com.yompix.novograd.client.style.StyleClient.phone());
        shot(30, "style_app");
        // герой на улице: вид спереди и сзади
        run(5, c -> c.setScreen(null));
        server(5, s -> {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
            p.teleport(p.getServerWorld(), o[0] - 12 + 0.5, o[1], o[2] - 18 + 0.5, 0, 0);
        });
        run(5, c -> { c.options.hudHidden = true; c.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_FRONT); });
        shot(40, "style_hero_front");
        run(5, c -> c.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_BACK));
        shot(20, "style_hero_back");
        server(5, s -> {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
            com.yompix.novograd.life.Style.open(p, "lux");
        });
        run(10, c -> c.setScreen(null));
        server(5, s -> com.yompix.novograd.life.Style.open(s.getPlayerManager().getPlayer(P), "wardrobe"));
        styleScreen(15, 0, 0, -1);
        key(5, GLFW.GLFW_KEY_DOWN);
        key(5, GLFW.GLFW_KEY_ENTER);
        styleScreen(10, 3, 0, -1);
        key(5, GLFW.GLFW_KEY_ENTER);
        run(10, c -> c.setScreen(null));
        run(5, c -> c.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_FRONT));
        shot(30, "style_hero_front2");
        server(5, s -> {
            var d = Life.get(s.getPlayerManager().getPlayer(P));
            com.yompix.novograd.Novograd.LOG.info("[style] money {} str {} sta {} look {} wardrobe {}", d.money, d.strength, d.stamina, d.look.toNbt(), d.wardrobe);
        });
        run(20, MinecraftClient::scheduleStop);
    }

    private static void styleGo(int delay, double[] o, int dx, int dz, float yaw, float pitch, String name) {
        server(delay, s -> {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
            p.teleport(p.getServerWorld(), o[0] + dx + 0.5, o[1], o[2] + dz + 0.5, yaw, pitch);
        });
        shot(40, name);
    }

    private static void styleScreen(int delay, int tab, int row, int color) {
        run(delay, c -> { if (c.currentScreen instanceof com.yompix.novograd.client.style.StyleShopScreen s) s.pick(tab, row, color); });
    }

    /** Оружие: от первого лица (покой, выстрел, прицел, перезарядка, осмотр), гильзы, хитмаркер, от третьего (и в прицеле), иконки, жители с оружием. */
    private static void guns() {
        YompixSplash.markShown();
        GunFx.testFlashNs = 700_000_000L;
        // стрельба на улице даёт розыск — в автотесте полиция не нужна
        ClientTickEvents.END_CLIENT_TICK.register(c -> {
            if (c.getServer() != null && c.world != null && c.world.getTime() % 10 == 0) c.getServer().execute(() -> {
                ServerPlayerEntity p = c.getServer().getPlayerManager().getPlayer(P);
                if (p != null && com.yompix.novograd.gta.Wanted.stars(p) > 0) com.yompix.novograd.gta.Wanted.clear(p);
            });
        });
        pre(60, c -> c.createIntegratedServerLoader().start("Island", () -> c.setScreen(new com.yompix.novograd.client.menu.LifeMenuScreen())));
        run(100, c -> { if (c.player != null && c.player.isDead()) c.player.requestRespawn(); });
        server(40, s -> {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
            var d = Life.get(p);
            d.storyStarted = true;
            d.storyDone = true;
            d.chapter = 99;
            d.age = 22;
            Story.applyAge(p, 22);
            Life.sync(p);
        });
        for (int i = 0; i < 4; i++) key(10, GLFW.GLFW_KEY_SPACE);
        run(5, c -> { if (c.currentScreen != null) c.setScreen(null); });
        cmd(5, "gamemode survival " + P);
        cmd(5, "effect give " + P + " minecraft:resistance 99999 4 true");
        cmd(5, "time set 6000");
        cmd(2, "clear " + P);
        String[] guns = {"pistol", "smg", "shotgun", "rifle", "sniper", "knife", "grenade", "molotov"};
        for (String g : guns) cmd(2, "give " + P + " novograd:" + g);
        cmd(2, "give " + P + " novograd:ammo 64");
        cmd(2, "give " + P + " novograd:ammo 64");
        cmd(2, "item replace entity " + P + " hotbar.8 with minecraft:air");
        // открытая площадка на пляже: видно оружие на фоне неба и моря
        server(5, s -> {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
            var w = p.getServerWorld();
            int x = -600, z = -520;
            w.getChunk(x >> 4, z >> 4);
            int y = com.yompix.novograd.world.Island.surface(x, z) + 1;
            p.teleport(w, x + 0.5, y, z + 0.5, 60, 0);
        });
        cmd(10, "execute at " + P + " run fill ~-12 ~-1 ~-12 ~12 ~-1 ~12 minecraft:smooth_stone");
        cmd(2, "execute at " + P + " run fill ~-12 ~ ~-12 ~12 ~8 ~12 minecraft:air");
        cmd(2, "kill @e[type=novograd:citizen,distance=..40]");
        cmd(2, "kill @e[type=minecraft:item,distance=..40]");
        run(60, c -> { if (c.currentScreen != null) c.setScreen(null); });
        // каждый ствол: покой, выстрел от бедра, прицел (ADS), выстрел в прицеле, перезарядка (магазин снаружи), осмотр
        for (int i = 0; i < 5; i++) {
            final int slot = i;
            String g = guns[i];
            run(5, c -> c.player.getInventory().selectedSlot = slot);
            shot(30, "gun_" + g + "_idle");
            run(2, c -> c.options.attackKey.setPressed(true));
            shot(6, "gun_" + g + "_fire");
            run(1, c -> c.options.attackKey.setPressed(false));
            run(25, c -> c.options.useKey.setPressed(true));
            run(19, c -> com.yompix.novograd.Novograd.LOG.info("[guns] ads {}", GunFx.debug()));
            shot(1, "gun_" + g + "_ads");
            run(2, c -> c.options.attackKey.setPressed(true));
            shot(6, "gun_" + g + "_ads_fire");
            run(1, c -> c.options.attackKey.setPressed(false));
            run(25, c -> c.options.useKey.setPressed(false));
            // перезарядка: замораживаем анимацию на моменте «магазин вынут» (у дробовика — патрон в руке)
            run(5, c -> ArsenalClient.send("reload", 0));
            run(3, c -> GunFx.testFreezeP = slot == 2 ? 0.55f : 0.36f);
            shot(3, "gun_" + g + "_reload");
            run(1, c -> GunFx.testFreezeP = slot == 2 ? 0.85f : 0.62f);
            shot(3, "gun_" + g + "_reload2");
            run(1, c -> GunFx.testFreezeP = -1);
            run(70, c -> GunFx.inspect());
            run(1, c -> GunFx.testFreezeP = 0.3f);
            shot(3, "gun_" + g + "_inspect");
            run(1, c -> GunFx.testFreezeP = 0.68f);
            shot(3, "gun_" + g + "_inspect2");
            run(1, c -> GunFx.testFreezeP = -1);
            run(60, c -> {});
        }
        // пустой магазин: пистолет стреляет до щелчка — затвор на задержке, магазин падает, новый, затвор
        run(5, c -> c.player.getInventory().selectedSlot = 0);
        server(5, s -> {
            var st = s.getPlayerManager().getPlayer(P).getMainHandStack();
            st.set(com.yompix.novograd.gta.Arsenal.MAG, 1);
        });
        run(10, c -> c.options.attackKey.setPressed(true));
        run(2, c -> c.options.attackKey.setPressed(false));
        run(14, c -> GunFx.testFreezeP = 0.2f);
        shot(3, "gun_pistol_empty_drop");
        run(1, c -> GunFx.testFreezeP = 0.74f);
        shot(3, "gun_pistol_empty_rack");
        run(1, c -> GunFx.testFreezeP = -1);
        run(60, c -> {});
        // гильзы на земле: очередь из «АК» и смотрим под ноги
        run(5, c -> c.player.getInventory().selectedSlot = 3);
        run(30, c -> c.options.attackKey.setPressed(true));
        run(24, c -> c.options.attackKey.setPressed(false));
        run(25, c -> { c.player.setPitch(62); c.player.setYaw(c.player.getYaw() + 40); });
        shot(10, "gun_casings");
        run(5, c -> { c.player.setPitch(5); c.player.setYaw(c.player.getYaw() - 40); });
        // хитмаркер: свинья без ИИ перед игроком
        server(5, s -> {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
            var w = p.getServerWorld();
            var pig = new net.minecraft.entity.passive.PigEntity(net.minecraft.entity.EntityType.PIG, w);
            var look = p.getRotationVec(1f).multiply(1, 0, 1).normalize();
            double px = p.getX() + look.x * 6, pz = p.getZ() + look.z * 6;
            pig.refreshPositionAndAngles(px, p.getY(), pz, 0, 0);
            pig.setAiDisabled(true);
            w.spawnEntity(pig);
            look(p, p.getX(), p.getY(), p.getZ(), px, p.getY() - 1.17, pz); // взгляд из глаз в середину свиньи
        });
        run(30, c -> c.player.getInventory().selectedSlot = 0);
        run(30, c -> c.options.useKey.setPressed(true));
        run(15, c -> c.options.attackKey.setPressed(true));
        shot(8, "gun_hitmarker");
        run(1, c -> c.options.attackKey.setPressed(false));
        run(5, c -> c.options.useKey.setPressed(false));
        cmd(5, "kill @e[type=minecraft:pig,distance=..20]");
        // от третьего лица: от бедра и в прицеле
        run(20, c -> c.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_FRONT));
        for (int i : new int[]{0, 3, 4}) {
            run(5, c -> c.player.getInventory().selectedSlot = i);
            shot(20, "gun_tp_front_" + guns[i]);
            run(2, c -> c.options.useKey.setPressed(true));
            shot(20, "gun_tp_ads_front_" + guns[i]);
            run(2, c -> c.options.useKey.setPressed(false));
        }
        run(5, c -> c.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_BACK));
        for (int i : new int[]{0, 2, 3}) {
            run(5, c -> c.player.getInventory().selectedSlot = i);
            shot(20, "gun_tp_back_" + guns[i]);
            run(2, c -> c.options.useKey.setPressed(true));
            shot(20, "gun_tp_ads_back_" + guns[i]);
            run(2, c -> c.options.useKey.setPressed(false));
        }
        for (int i = 5; i < 8; i++) {
            final int slot = i;
            run(5, c -> c.player.getInventory().selectedSlot = slot);
        }
        run(5, c -> c.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON));
        // вид сбоку: «манекены»-игроки (та же модель и поза) с каждым стволом, стоят боком
        server(5, s -> {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
            p.teleport(p.getServerWorld(), p.getX(), p.getY(), p.getZ(), 0, 10);
        });
        cmd(2, "kill @e[type=novograd:citizen,distance=..40]");
        cmd(2, "kill @e[type=minecraft:item,distance=..40]");
        run(10, c -> {
            for (int i = 0; i < 5; i++) {
                var d = new net.minecraft.client.network.OtherClientPlayerEntity(c.world, new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "Gun" + i));
                d.setId(-7000 - i);
                double x = c.player.getX() + 3.2 - i * 1.6, z = c.player.getZ() + 4.5;
                float yaw = i % 2 == 0 ? 90 : -90;
                d.refreshPositionAndAngles(x, c.player.getY(), z, yaw, i == 4 ? -10 : 0);
                d.setHeadYaw(yaw);
                d.setBodyYaw(yaw);
                d.prevBodyYaw = yaw;
                d.prevHeadYaw = yaw;
                d.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, new net.minecraft.item.ItemStack(net.minecraft.registry.Registries.ITEM.get(
                        net.minecraft.util.Identifier.of("novograd", guns[i]))));
                c.world.addEntity(d);
            }
            c.player.getInventory().selectedSlot = 8;
            c.options.hudHidden = true;
        });
        shot(20, "gun_side");
        run(5, c -> {
            for (int i = 0; i < 5; i++) c.world.removeEntity(-7000 - i, net.minecraft.entity.Entity.RemovalReason.DISCARDED);
            c.options.hudHidden = false;
        });
        // иконки
        run(10, c -> c.setScreen(new net.minecraft.client.gui.screen.ingame.InventoryScreen(c.player)));
        shot(20, "gun_inventory");
        run(5, c -> c.setScreen(null));
        // жители с оружием целятся друг в друга
        server(10, s -> {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
            var w = p.getServerWorld();
            CitizenEntity a1 = new CitizenEntity(com.yompix.novograd.ModContent.CITIZEN, w), a2 = new CitizenEntity(com.yompix.novograd.ModContent.CITIZEN, w);
            a1.setup(CitizenEntity.RANDOM_FROM + 3, CitizenEntity.Role.CITIZEN, true, "G1");
            a2.setup(CitizenEntity.RANDOM_FROM + 8, CitizenEntity.Role.CITIZEN, true, "G2");
            double y = p.getY();
            a1.refreshPositionAndAngles(p.getX() - 2, y, p.getZ() + 4, -90, 0);
            a2.refreshPositionAndAngles(p.getX() + 2, y, p.getZ() + 4, 90, 0);
            a1.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, new net.minecraft.item.ItemStack(com.yompix.novograd.ModContent.PISTOL));
            a2.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, new net.minecraft.item.ItemStack(com.yompix.novograd.gta.Arsenal.RIFLE));
            w.spawnEntity(a1);
            w.spawnEntity(a2);
            a1.sceneWith(a2, CitizenEntity.Activity.AIM, 2000);
            a2.sceneWith(a1, CitizenEntity.Activity.AIM, 2000);
            p.teleport(w, p.getX(), y, p.getZ(), 0, 15);
        });
        run(5, c -> c.player.getInventory().selectedSlot = 8);
        shot(40, "gun_npc");
        run(20, MinecraftClient::scheduleStop);
    }

    private static void pressKey(MinecraftClient c, int key) {
        if (c.currentScreen != null) c.currentScreen.keyPressed(key, 0, 0);
    }

    // ------------------------------------------------------------------ машины: все кузова, повреждения, ночь, езда
    private static void look(ServerPlayerEntity p, double cx, double cy, double cz, double tx, double ty, double tz) {
        double dx = tx - cx, dy = ty - cy, dz = tz - cz;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        p.teleport(p.getServerWorld(), cx, cy, cz, yaw, pitch);
    }

    private static final java.util.Map<Integer, com.yompix.novograd.entity.CarEntity> TEST_CARS = new java.util.HashMap<>();

    private static void cars() {
        YompixSplash.markShown();
        pre(60, c -> c.createIntegratedServerLoader().start("Island", () -> c.setScreen(new com.yompix.novograd.client.menu.LifeMenuScreen())));
        run(100, c -> { if (c.player != null && c.player.isDead()) c.player.requestRespawn(); });
        server(40, s -> {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
            var d = Life.get(p);
            d.storyStarted = true;
            d.storyDone = true;
            d.chapter = 99;
            d.age = 22;
            d.edu = 16;
            Story.applyAge(p, 22);
            Life.sync(p);
        });
        for (int i = 0; i < 4; i++) key(10, GLFW.GLFW_KEY_SPACE);
        run(5, c -> { if (c.currentScreen != null) c.setScreen(null); });
        cmd(5, "gamemode spectator " + P);
        cmd(5, "gamerule doDaylightCycle false");
        cmd(5, "gamerule doMobSpawning false");
        cmd(5, "weather clear");
        cmd(5, "time set 6000");
        final int X0 = 420, Z = -905, N = com.yompix.novograd.entity.CarEntity.VARIANTS.length;
        server(5, s -> {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
            var w = p.getServerWorld();
            for (int x = X0 - 32; x < X0 + N * 9 + 32; x += 16) w.getChunk(x >> 4, Z >> 4);
            look(p, X0 + N * 4.5, Story.topY(w, X0, Z) + 14, Z + 26, X0 + N * 4.5, Story.topY(w, X0, Z), Z);
        });
        run(80, c -> {});
        server(5, s -> {
            var w = s.getPlayerManager().getPlayer(P).getServerWorld();
            for (int i = 0; i < N; i++) {
                double x = X0 + i * 9 + 0.5;
                var car = new com.yompix.novograd.entity.CarEntity(com.yompix.novograd.ModContent.CAR, w);
                car.refreshPositionAndAngles(x, Story.topY(w, (int) x, Z), Z + 0.5, 0, 0);
                car.setVariant(i);
                w.spawnEntity(car);
                TEST_CARS.put(i, car);
            }
        });
        shot(60, "cars_all");
        for (int i = 0; i < N; i++) {
            int ii = i;
            server(5, s -> {
                var car = TEST_CARS.get(ii);
                look(s.getPlayerManager().getPlayer(P), car.getX() + 4.6, car.getY() + 2.1, car.getZ() + 6.2, car.getX(), car.getY() + 0.9, car.getZ() - 0.4);
            });
            shot(18, "car_" + i + "_" + com.yompix.novograd.entity.CarEntity.VARIANTS[i] + "_front");
            server(5, s -> {
                var car = TEST_CARS.get(ii);
                look(s.getPlayerManager().getPlayer(P), car.getX() - 4.8, car.getY() + 2.3, car.getZ() - 7.4, car.getX(), car.getY() + 0.9, car.getZ() - 1);
            });
            shot(18, "car_" + i + "_" + com.yompix.novograd.entity.CarEntity.VARIANTS[i] + "_rear");
        }
        // сбоку — силуэты
        for (int i : new int[]{0, 2, 8, 10, 11}) {
            int ii = i;
            server(5, s -> {
                var car = TEST_CARS.get(ii);
                look(s.getPlayerManager().getPlayer(P), car.getX() + 8.5, car.getY() + 1.2, car.getZ() - 0.8, car.getX(), car.getY() + 0.9, car.getZ() - 0.8);
            });
            shot(18, "car_" + i + "_side");
        }
        // повреждения: седан и полиция
        server(5, s -> {
            TEST_CARS.get(0).hitByBullet(27, null);
            TEST_CARS.get(6).hitByBullet(38, null);
            var car = TEST_CARS.get(0);
            look(s.getPlayerManager().getPlayer(P), car.getX() + 5.5, car.getY() + 2.2, car.getZ() + 7.5, car.getX() + 4, car.getY() + 0.8, car.getZ());
        });
        shot(25, "cars_damage");
        // ночь: фары, мигалка, такси
        server(5, s -> {
            for (int i : new int[]{0, 5, 6, 3}) TEST_CARS.get(i).toggleLight(com.yompix.novograd.entity.CarEntity.HEADLIGHTS);
            TEST_CARS.get(6).toggleLight(com.yompix.novograd.entity.CarEntity.HAZARD);
        });
        cmd(5, "time set 18000");
        server(5, s -> {
            var car = TEST_CARS.get(4);
            look(s.getPlayerManager().getPlayer(P), car.getX() + 2, car.getY() + 4, car.getZ() + 13, car.getX() + 2, car.getY() + 0.8, car.getZ());
        });
        shot(30, "cars_night");
        cmd(5, "time set 6000");
        // езда: спорткупе по взлётной полосе, спидометр, занос на ручнике и следы шин
        cmd(5, "gamemode creative " + P);
        server(5, s -> {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
            var w = p.getServerWorld();
            for (int x = 300; x < 720; x += 16) w.getChunk(x >> 4, Z >> 4);
            var car = new com.yompix.novograd.entity.CarEntity(com.yompix.novograd.ModContent.CAR, w);
            car.refreshPositionAndAngles(340.5, Story.topY(w, 340, Z - 8), Z - 7.5, -90, 0);
            car.setVariant(2);
            car.setTune(true, 2, 0);
            w.spawnEntity(car);
            p.teleport(w, 340.5, car.getY(), Z - 7.5, -90, 10);
            p.startRiding(car, true);
        });
        run(30, c -> {
            c.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_BACK);
            c.options.forwardKey.setPressed(true);
        });
        shot(25, "drive_launch");
        shot(55, "drive_speed");
        run(5, c -> {
            c.options.leftKey.setPressed(true);
            c.options.jumpKey.setPressed(true);
        });
        shot(10, "drive_drift_1");
        shot(10, "drive_drift_2");
        run(5, c -> {
            c.options.leftKey.setPressed(false);
            c.options.jumpKey.setPressed(false);
            c.options.forwardKey.setPressed(false);
            c.options.backKey.setPressed(true);
        });
        shot(20, "drive_brake");
        run(30, c -> {
            c.options.backKey.setPressed(false);
            c.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_FRONT);
        });
        shot(20, "drive_marks");
        run(5, c -> c.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON));
        shot(20, "drive_first_person");
        server(5, s -> {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
            var car = p.getVehicle();
            p.stopRiding();
            p.changeGameMode(net.minecraft.world.GameMode.SPECTATOR);
            if (car != null) look(p, car.getX() + 1, car.getY() + 9, car.getZ() + 3, car.getX() - 6, car.getY(), car.getZ());
        });
        shot(20, "drive_marks_top");
        run(20, MinecraftClient::scheduleStop);
    }

    /** Держать клавишу ticks тиков (имитация зажатой кнопки). */
    // ------------------------------------------------------------------ «city»: здания изнутри в новом мире
    private static int[] findLot(int x0, int x1, int z0, int z1, int type, int minFloors) {
        for (int x = x0; x <= x1; x += 48)
            for (int z = z0; z <= z1; z += 48) {
                int[] in = com.yompix.novograd.world.Island.lotInfo(x, z);
                if (in != null && in[0] == type && in[2] >= minFloors) return new int[]{Math.floorDiv(x + 24, 48) * 48, Math.floorDiv(z + 24, 48) * 48, in[1], in[2]};
            }
        return null;
    }

    private static void tp(int delay, double x, double y, double z, float yaw, float pitch) {
        server(delay, s -> {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
            if (p == null) return;
            var w = p.getServerWorld();
            w.getChunk((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4);
            p.teleport(w, x, y, z, yaw, pitch);
            p.getAbilities().flying = true; // зависнуть на месте для кадра (в кабине прыжок всё равно работает)
            p.sendAbilitiesUpdate();
        });
    }

    private static void liftMenu(int delay, int px, int py, int pz) {
        server(delay, s -> {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
            if (p != null) com.yompix.novograd.world.Elevators.openMenu(p, new net.minecraft.util.math.BlockPos(px, py, pz), net.minecraft.util.math.Direction.WEST);
        });
    }

    private static void liftGo(int delay, int x, int z, int floor) {
        run(delay, c -> {
            net.minecraft.nbt.NbtCompound d = new net.minecraft.nbt.NbtCompound();
            d.putInt("x", x);
            d.putInt("z", z);
            d.putInt("floor", floor);
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new com.yompix.novograd.world.Elevators.C2S(d));
            c.setScreen(null);
        });
    }

    private static void city() {
        YompixSplash.markShown();
        String only = System.getProperty("novograd.autotest").contains(":") ? System.getProperty("novograd.autotest").split(":")[1] : "";
        pre(60, c -> {
            var data = new net.minecraft.resource.DataConfiguration(new net.minecraft.resource.DataPackSettings(List.of("vanilla", "fabric"), List.of()),
                    net.minecraft.resource.featuretoggle.FeatureFlags.DEFAULT_ENABLED_FEATURES);
            var info = new net.minecraft.world.level.LevelInfo("CityTest" + System.currentTimeMillis() % 100000, net.minecraft.world.GameMode.CREATIVE, false,
                    net.minecraft.world.Difficulty.PEACEFUL, true, new net.minecraft.world.GameRules(), data);
            var city = net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD_PRESET, com.yompix.novograd.Novograd.id("city"));
            c.createIntegratedServerLoader().createAndStart(info.getLevelName(), info, net.minecraft.world.gen.GeneratorOptions.createRandom(),
                    drm -> drm.get(net.minecraft.registry.RegistryKeys.WORLD_PRESET).getOrThrow(city).createDimensionsRegistryHolder(), new com.yompix.novograd.client.menu.LifeMenuScreen());
        });
        server(1, sv -> { // без вступления и сюжета: только город
            ServerPlayerEntity p = sv.getPlayerManager().getPlayer(P);
            if (p == null) return;
            var d = Life.get(p);
            d.storyStarted = true;
            d.storyDone = true;
        });
        run(200, c -> { if (c.currentScreen != null) c.setScreen(null); });
        cmd(5, "gamemode creative " + P);
        cmd(5, "difficulty peaceful");
        cmd(5, "time set 6000");
        cmd(5, "gamerule doDaylightCycle false");
        cmd(5, "gamerule doMobSpawning false");
        int G = com.yompix.novograd.world.Island.G;
        int[] off = findLot(192, 672, -672, -192, 0, 12), homes = findLot(192, 672, -672, -192, 1, 8);
        int[] hotel = findLot(-1536, -1104, -384, 384, 2, 7), casino = null;
        for (int x = -1536; x <= -1104 && casino == null; x += 48)
            for (int z = -384; z <= 384; z += 48) {
                int[] in = com.yompix.novograd.world.Island.lotInfo(x, z);
                if (in != null && in[0] == 3 && !(Math.floorDiv(x + 24, 48) == -27 && Math.floorDiv(z + 24, 48) == -1)) { casino = new int[]{Math.floorDiv(x + 24, 48) * 48, Math.floorDiv(z + 24, 48) * 48, in[1]}; break; }
            }
        int[] store = findLot(192, 672, 192, 672, 6, 0);
        com.yompix.novograd.Novograd.LOG.info("[city] офисы {} жилая {} отель {} казино {} склад {}", java.util.Arrays.toString(off), java.util.Arrays.toString(homes),
                java.util.Arrays.toString(hotel), java.util.Arrays.toString(casino), java.util.Arrays.toString(store));
        int s = G;
        if (off != null && (only.isEmpty() || only.equals("lift"))) {
            int X = off[0], Z = off[1], h = off[2], n = off[3];
            tp(5, X + 0.5, s + 2, Z + h + 10, 180, -12);
            shot(200, "city_tower_ext");
            tp(5, X + 0.5, s + 1, Z + h - 2, 180, 8);
            shot(120, "city_lobby");
            tp(5, X - 2.5, s + 1, Z + 0.5, -90, 5);
            shot(80, "city_lobby_lifts");
            tp(5, X + 7.5, s + 1, Z - 0.5, -90, 0);
            liftMenu(40, X + 8, s + 2, Z - 1);
            shot(30, "city_lift_menu");
            liftGo(5, X + 7, Z - 1, 3);
            shot(8, "city_lift_fade");
            shot(70, "city_office_arrive");
            hold(10, c -> c.options.jumpKey, 6); // прыжок в кабине — этажом выше
            shot(60, "city_jump_up");
            server(5, sv -> com.yompix.novograd.Novograd.LOG.info("[city] после прыжка игрок на y={}", sv.getPlayerManager().getPlayer(P).getY()));
            int W = h - 1;
            tp(5, X - W + 1.5, s + 4 * 4 + 1, Z + W - 0.5, -135, 10);
            shot(80, "city_office_wide");
            tp(5, X + 5.5, s + 4 * 4 + 1, Z + 3.5, -45, 15);
            shot(60, "city_meeting");
            tp(5, X - 2.5, s + 4 * 2 + 1, Z - 7.5, -90, 25);
            shot(60, "city_stairs");
            tp(5, X + 7.5, s + 4 * 4 + 1, Z - 0.5, -90, 0);
            liftMenu(30, X + 8, s + 4 * 4 + 2, Z - 1);
            liftGo(20, X + 7, Z - 1, n);
            shot(80, "city_roof_arrive");
            tp(5, X - 7.5, s + 4 * n + 4, Z + 7.5, -135, 25);
            shot(80, "city_roof");
            tp(5, X - h - 12, s + 4 * n - 6, Z + h + 12, -135, 15);
            shot(120, "city_tower_top");
        }
        if (homes != null && (only.isEmpty() || only.equals("homes"))) {
            int X = homes[0], Z = homes[1], h = homes[2];
            tp(5, X - 3 + 0.5, s + 9, Z - 3.5, 0, 10);
            shot(200, "city_apt_corridor");
            tp(5, X - 1.5, s + 9, Z + 0.5, 45, 20);
            shot(80, "city_apartment");
            tp(5, X - h + 2.5, s + 9, Z + h - 2.5, -135, 20);
            shot(60, "city_apartment2");
        }
        if (hotel != null && (only.isEmpty() || only.equals("hotel"))) {
            int X = hotel[0], Z = hotel[1], h = hotel[2], n = hotel[3];
            tp(5, X + 0.5, s + 2, Z + h + 9, 180, -10);
            shot(220, "city_hotel_ext");
            tp(5, X + 0.5, s + 1, Z + h - 2, 180, 8);
            shot(100, "city_hotel_lobby");
            tp(5, X + 4.5, s + 5, Z + 5.5, -45, 20);
            shot(80, "city_hotel_room");
            tp(5, X - 6.5, s + 4 * n + 3, Z + 8.5, -150, 25);
            shot(80, "city_hotel_pool");
        }
        if (casino != null && (only.isEmpty() || only.equals("hotel"))) {
            int X = casino[0], Z = casino[1], h = casino[2];
            tp(5, X + 0.5, s + 3, Z + h - 2, 180, 12);
            shot(200, "city_casino");
        }
        if (only.isEmpty() || only.equals("house")) {
            int X = -240 + 9, Z = 240 + 9;
            tp(5, X + 0.5, s + 2, Z + 12, 180, 0);
            shot(220, "city_house_ext");
            tp(5, X + 0.5, s + 1, Z + 3.3, 180, 15);
            shot(80, "city_house_living");
            tp(5, X + 0.5, s + 1, Z - 0.5, 180, 25);
            shot(60, "city_house_bedroom");
        }
        if (store != null && (only.isEmpty() || only.equals("house"))) {
            tp(5, store[0] + 0.5, s + 2, store[1] + 7.5, 180, 10);
            shot(220, "city_warehouse");
        }
        if (only.isEmpty() || only.equals("extra")) {
            int[] site = findLot(192, 672, -672, -192, 8, 0);
            com.yompix.novograd.Novograd.LOG.info("[city] стройка {}", java.util.Arrays.toString(site));
            if (site != null) {
                tp(5, site[0] - 0.5, s + 6, site[1] + 26.5, 180, 0);
                shot(220, "city_construction");
            }
            int lx = com.yompix.novograd.world.Island.LIGHTHOUSE_X, lz = com.yompix.novograd.world.Island.LIGHTHOUSE_Z;
            tp(5, lx + 18.5, G + 8, lz + 18.5, 135, 5);
            shot(220, "city_lighthouse");
            tp(5, lx + 0.5, G + 1, lz + 4.5, 180, -10);
            shot(60, "city_lighthouse_door");
        }
        if (only.isEmpty() || only.equals("road")) {
            tp(5, -150.5, G + 3, 746.5, 0, 8);
            shot(220, "city_gas");
            tp(5, -149.5, G + 1, 765.5, 0, 10);
            shot(80, "city_gas_shop");
            tp(5, -349.5, G + 3, 744.5, 0, -8);
            shot(160, "city_billboard");
        }
        run(20, MinecraftClient::scheduleStop);
    }

    private static double topLoaded(net.minecraft.server.world.ServerWorld w, int x, int z) {
        w.getChunk(x >> 4, z >> 4);
        return Story.topY(w, x, z);
    }

    /** -Dnovograd.autotest=pulse — «Пульс города» (gta/Pulse): снимки run/screenshots/pulse_*.png, итоги в логе [pulse]. */
    private static void pulseMode(String mode) {
        YompixSplash.markShown();
        pre(60, c -> c.createIntegratedServerLoader().start("Fresh2", () -> c.setScreen(new com.yompix.novograd.client.menu.LifeMenuScreen())));
        run(100, c -> { if (c.player != null && c.player.isDead()) c.player.requestRespawn(); });
        run(5, c -> { if (c.currentScreen != null) c.setScreen(null); });
        cmd(5, "gamemode survival " + P);
        cmd(5, "time set 6000");
        cmd(5, "gamerule doDaylightCycle false");
        cmd(5, "weather clear");
        cmd(5, "effect give " + P + " minecraft:resistance 99999 4 true");
        server(5, s -> {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
            var d = Life.get(p);
            d.chapter = Math.max(d.chapter, 1);
            d.money += 30000;
            d.dirty += 5000;
            Life.sync(p);
            com.yompix.novograd.gta.News.post(s, "Прямо сейчас: вооружённый налёт на «Сити-Банк». Полиция стягивает силы", "ЭКСТРЕННО");
            com.yompix.novograd.gta.Economy.shock(s, "PORT", 0.06, "Порт подписал контракт с крупной судоходной компанией");
            com.yompix.novograd.gta.News.post(s, "Ночные гонки вернулись: полиция получает жалобы со всего центра", "ГОРОД");
        });
        java.util.function.BiConsumer<Integer, String> pc = (delay, what) -> server(delay, s -> {
            String r = com.yompix.novograd.gta.Pulse.debug(s.getPlayerManager().getPlayer(P), what);
            com.yompix.novograd.Novograd.LOG.info("[pulse] {} → {}", what, r);
        });
        run(5, c -> c.options.hudHidden = false);
        if (mode.equals("pulsenight")) { // ночная погоня: прожекторы, огни вертолётов, окно эфира
            pc.accept(5, "tv 1");
            pc.accept(400, "tv view 1");
            shot(60, "pulse_tv_riviera");
            pc.accept(5, "tv 0");
            cmd(100, "time set 18000");
            server(5, s -> com.yompix.novograd.gta.Wanted.add(s.getPlayerManager().getPlayer(P), 3, "проверка", 3));
            for (int i = 0; i < 4; i++) {
                final int k = i;
                run(i == 0 ? 200 : 5, c -> { c.player.setYaw(k * 90); c.player.setPitch(-22); });
                shot(30, "pulse_night_" + i);
            }
            run(20, MinecraftClient::scheduleStop);
            return;
        }
        if (mode.equals("pulsejump")) {
            pulseJump(pc);
            run(20, MinecraftClient::scheduleStop);
            return;
        }
        // 1. видеостена на деловой площади
        pc.accept(5, "tv 0");
        pc.accept(120, "tv view 0");
        shot(60, "pulse_tv");
        pc.accept(5, "tv 1");
        pc.accept(400, "tv view 1");
        shot(60, "pulse_tv_riviera");
        // 2. граффити: тег банды → держим Shift → свой тег
        pc.accept(5, "col tp tag 0");
        pc.accept(80, "col view 0");
        shot(40, "pulse_tag_rival");
        run(5, c -> c.options.sneakKey.setPressed(true));
        shot(30, "pulse_tag_spraying");
        run(40, c -> c.options.sneakKey.setPressed(false));
        shot(50, "pulse_tag_mine");
        // 3. фото-точка: встать, снять камерой
        pc.accept(5, "col tp photo 0");
        shot(200, "pulse_photo_spot");
        run(5, c -> NovogradClient.takePhoto());
        server(30, s -> com.yompix.novograd.Novograd.LOG.info("[pulse] photo0 done={}", Life.get(s.getPlayerManager().getPlayer(P)).flags.contains("col:photo:0")));
        pulseJump(pc);
        // 5. ларёк карт и приложение «Коллекции»
        pc.accept(5, "col tp stall 0");
        shot(200, "pulse_stall");
        pc.accept(5, "col give tag 49");
        pc.accept(5, "col give photo 29");
        run(20, c -> c.setScreen(new com.yompix.novograd.client.screen.CollectionsScreen()));
        shot(30, "pulse_collections");
        run(5, c -> c.setScreen(new com.yompix.novograd.client.screen.PhoneScreen(null)));
        shot(30, "pulse_phone");
        run(5, c -> c.setScreen(null));
        // 6. хаос
        pc.accept(5, "chaos");
        run(10, c -> c.setScreen(new com.yompix.novograd.client.screen.CollectionsScreen()));
        shot(30, "pulse_chaos");
        run(5, c -> c.setScreen(null));
        pc.accept(5, "chaos off");
        // 7. ночь, погоня ★★★★, вертолёт «Новоград-24»: окно эфира, прожекторы
        pc.accept(5, "tv 0");
        cmd(20, "time set 18000");
        pc.accept(5, "heli");
        shot(160, "pulse_live");
        run(5, c -> c.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_BACK));
        run(5, c -> c.player.setPitch(-30));
        shot(40, "pulse_searchlight");
        run(5, c -> c.player.setPitch(-60));
        shot(40, "pulse_heli_sky");
        run(5, c -> c.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON));
        server(5, s -> com.yompix.novograd.gta.Wanted.clear(s.getPlayerManager().getPlayer(P)));
        cmd(5, "time set 6000");
        // 8. «Подвал»: вход, бой NPC, ставка, свой бой
        pc.accept(30, "fight tp");
        shot(60, "pulse_fight_door");
        server(5, s -> {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
            p.teleport(p.getServerWorld(), com.yompix.novograd.gta.FightClub.EX + 0.5, p.getY(), com.yompix.novograd.gta.FightClub.EZ + 0.5, 0, 0);
        });
        shot(80, "pulse_fight_inside");
        server(5, s -> com.yompix.novograd.gta.FightClub.bet(s.getPlayerManager().getPlayer(P), 1, 500));
        pc.accept(5, "fight");
        shot(30, "pulse_fight_bookie");
        run(5, c -> c.setScreen(null));
        run(5, c -> { c.player.setYaw(-50); c.player.setPitch(10); });
        shot(500, "pulse_fight_npc");
        shot(200, "pulse_fight_npc2");
        pc.accept(250, "fight join 0");
        shot(140, "pulse_fight_me");
        server(5, s -> com.yompix.novograd.Novograd.LOG.info("[pulse] dirty after fights {}", Life.get(s.getPlayerManager().getPlayer(P)).dirty));
        // 9. телевизор в квартире на канале «Новости 24»
        server(5, s -> {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
            var w = p.getServerWorld();
            int x = 200, z = -742;
            w.getChunk(x >> 4, z >> 4);
            int y = com.yompix.novograd.story.Story.topY(w, x, z);
            p.teleport(w, x + 0.5, y, z + 0.5, 180, 15);
            w.setBlockState(new net.minecraft.util.math.BlockPos(x, y, z - 2), com.yompix.novograd.ModContent.TV.getDefaultState()
                    .with(com.yompix.novograd.block.TvBlock.CHANNEL, 1).with(net.minecraft.state.property.Properties.HORIZONTAL_FACING, net.minecraft.util.math.Direction.SOUTH));
        });
        shot(60, "pulse_tvblock");
        server(5, s -> {
            var d = Life.get(s.getPlayerManager().getPlayer(P));
            com.yompix.novograd.Novograd.LOG.info("[pulse] итог: tag={} photo={} jump={} pkg={}", com.yompix.novograd.gta.Collectibles.count(d, "tag"),
                    com.yompix.novograd.gta.Collectibles.count(d, "photo"), com.yompix.novograd.gta.Collectibles.count(d, "jump"), com.yompix.novograd.gta.Collectibles.count(d, "pkg"));
        });
        run(20, MinecraftClient::scheduleStop);
    }

    /** Уникальный прыжок: машина в 16 блоках до трамплина, газ в пол. */
    private static void pulseJump(java.util.function.BiConsumer<Integer, String> pc) {
        pc.accept(5, "col tp jump 0");
        shot(200, "pulse_ramp");
        server(5, s -> {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
            var w = p.getServerWorld();
            var car = new com.yompix.novograd.entity.CarEntity(com.yompix.novograd.ModContent.CAR, w);
            car.refreshPositionAndAngles(p.getX(), p.getY() + 0.2, p.getZ(), p.getYaw(), 0);
            car.setVariant(0);
            car.setTune(true, 2, 0);
            w.spawnEntity(car);
            p.startRiding(car, true);
        });
        run(30, c -> c.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_BACK));
        run(5, c -> c.options.forwardKey.setPressed(true));
        for (int i = 0; i < 6; i++) shot(12, "pulse_jump_" + i);
        run(30, c -> c.options.forwardKey.setPressed(false));
        server(40, s -> {
            var d = Life.get(s.getPlayerManager().getPlayer(P));
            com.yompix.novograd.Novograd.LOG.info("[pulse] jump0 done={} jumps={}", d.flags.contains("col:jump:0"), com.yompix.novograd.gta.Collectibles.count(d, "jump"));
        });
        run(5, c -> c.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON));
        server(5, s -> s.getPlayerManager().getPlayer(P).stopRiding());
    }

    private static void runoverMode() {
        YompixSplash.markShown();
        pre(60, c -> c.createIntegratedServerLoader().start("Fresh2", () -> c.setScreen(new com.yompix.novograd.client.menu.LifeMenuScreen())));
        run(100, c -> { if (c.player != null && c.player.isDead()) c.player.requestRespawn(); });
        run(5, c -> { if (c.currentScreen != null) c.setScreen(null); });
        cmd(5, "gamemode survival " + P);
        cmd(5, "time set 6500");
        cmd(5, "gamerule doDaylightCycle false");
        cmd(5, "effect give " + P + " minecraft:resistance 99999 2 true");
        cmd(5, "effect give " + P + " minecraft:regeneration 99999 2 true");
        int[] ids = new int[8];
        // --- 1. витрина: трое прохожих перед камерой — сбит и лежит, сбит насмерть
        server(5, s -> {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
            var w = p.getServerWorld();
            w.getChunk(200 >> 4, -742 >> 4);
            double y = topLoaded(w, 210, -742);
            for (var e : w.getEntitiesByClass(CitizenEntity.class, p.getBoundingBox().expand(400), e -> true)) if (e.squaredDistanceTo(210, y, -742) < 60 * 60) e.discard();
            p.getInventory().clear();
            p.teleport(w, 210.5, y, -747.5, 0, 14);
            for (int i = 0; i < 2; i++) {
                CitizenEntity c = new CitizenEntity(com.yompix.novograd.ModContent.CITIZEN, w);
                c.setup(CitizenEntity.RANDOM_FROM + 3 + i * 7, CitizenEntity.Role.CITIZEN, false, "Прохожий");
                c.refreshPositionAndAngles(208.5 + i * 4, y, -743.5, 90, 0);
                w.spawnEntity(c);
                ids[i] = c.getId();
            }
        });
        run(30, c -> { c.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON); c.options.hudHidden = true; });
        server(5, s -> {
            var w = s.getPlayerManager().getPlayer(P).getServerWorld();
            if (w.getEntityById(ids[0]) instanceof CitizenEntity a) {
                // удар «машиной» слева: летит вправо, падает на спину
                a.setVelocity(0.55, 0.42, 0);
                a.velocityModified = true;
                a.knockDown(a.getPos().add(-2, 0, 0), null, 60);
            }
            if (w.getEntityById(ids[1]) instanceof CitizenEntity b) {
                CitizenEntity.runOverHit = true;
                b.damage(w.getDamageSources().flyIntoWall(), 40);
                CitizenEntity.runOverHit = false;
                b.setVelocity(0.6, 0.45, 0);
                b.velocityModified = true;
            }
        });
        for (int i = 0; i < 6; i++) shot(3, "ro_fall_" + i);
        shot(25, "ro_lying");
        shot(50, "ro_lying2");
        shot(12, "ro_getup_a");
        shot(10, "ro_getup_b");
        shot(25, "ro_after");
        run(5, c -> { com.yompix.novograd.Novograd.LOG.info("[runover] showcase done"); c.options.hudHidden = false; });
        // --- 2. за рулём: маслкар разгоняется и сбивает троих; полицейский у дороги видит
        server(10, s -> {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
            var w = p.getServerWorld();
            double y = topLoaded(w, 200, -742);
            var car = new com.yompix.novograd.entity.CarEntity(com.yompix.novograd.ModContent.CAR, w);
            car.refreshPositionAndAngles(170.5, topLoaded(w, 170, -742), -742.5, -90, 0);
            car.setVariant(9);
            w.spawnEntity(car);
            p.teleport(w, 170.5, car.getY() + 1, -742.5, -90, 10);
            p.startRiding(car, true);
            for (int i = 0; i < 3; i++) {
                CitizenEntity c = new CitizenEntity(com.yompix.novograd.ModContent.CITIZEN, w);
                c.setup(CitizenEntity.RANDOM_FROM + 10 + i * 5, i == 2 ? CitizenEntity.Role.GANG : CitizenEntity.Role.CITIZEN, false, i == 2 ? "Бандит" : "Прохожий");
                c.refreshPositionAndAngles(196.5 + i * 3, y, -742.5 + (i - 1) * 0.6, 90, 0);
                w.spawnEntity(c);
                ids[2 + i] = c.getId();
            }
            CitizenEntity cop = new CitizenEntity(com.yompix.novograd.ModContent.CITIZEN, w);
            cop.setup(CitizenEntity.skinIndex("police"), CitizenEntity.Role.POLICE, false, "Полицейский");
            cop.refreshPositionAndAngles(214.5, topLoaded(w, 214, -749), -749.5, 0, 0);
            w.spawnEntity(cop);
            ids[5] = cop.getId();
        });
        run(20, c -> c.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_BACK));
        run(1, c -> KeyBinding.setKeyPressed(InputUtil.fromTranslationKey(c.options.forwardKey.getBoundKeyTranslationKey()), true));
        for (int i = 0; i < 16; i++) shot(4, "ro_drive_" + i);
        run(1, c -> KeyBinding.setKeyPressed(InputUtil.fromTranslationKey(c.options.forwardKey.getBoundKeyTranslationKey()), false));
        run(1, c -> KeyBinding.setKeyPressed(InputUtil.fromTranslationKey(c.options.backKey.getBoundKeyTranslationKey()), true));
        run(25, c -> KeyBinding.setKeyPressed(InputUtil.fromTranslationKey(c.options.backKey.getBoundKeyTranslationKey()), false));
        run(1, c -> c.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_FRONT));
        shot(5, "ro_drive_look");
        server(5, s -> {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
            var w = p.getServerWorld();
            StringBuilder b = new StringBuilder();
            for (int i = 2; i < 6; i++) b.append(w.getEntityById(ids[i]) instanceof CitizenEntity c ? c.getRole() + " hp=" + c.getHealth() + " ko=" + c.knockPhase() + " at " + c.getBlockPos() + "; " : "gone; ");
            com.yompix.novograd.Novograd.LOG.info("[runover] after drive: stars={} car={} | {}", com.yompix.novograd.gta.Wanted.stars(p),
                    p.getVehicle() instanceof com.yompix.novograd.entity.CarEntity car ? car.health() : -1, b);
        });
        shot(40, "ro_drive_after");
        // --- 3. угон: водитель-бандит выкинут, бежит назад, вытаскивает игрока и бьёт
        server(5, s -> {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
            var w = p.getServerWorld();
            com.yompix.novograd.gta.Wanted.clear(p);
            p.stopRiding();
            for (var e : w.getEntitiesByClass(CitizenEntity.class, p.getBoundingBox().expand(80), e -> true)) e.discard();
            double y = topLoaded(w, 260, -742);
            var car = new com.yompix.novograd.entity.CarEntity(com.yompix.novograd.ModContent.CAR, w);
            car.refreshPositionAndAngles(260.5, y, -742.5, -90, 0);
            car.setVariant(1);
            w.spawnEntity(car);
            CitizenEntity d = new CitizenEntity(com.yompix.novograd.ModContent.CITIZEN, w);
            d.setup(CitizenEntity.RANDOM_FROM + 21, CitizenEntity.Role.GANG, false, "Водила");
            d.refreshPositionAndAngles(260.5, y, -742.5, -90, 0);
            w.spawnEntity(d);
            d.startRiding(car, true);
            ids[6] = d.getId();
            ids[7] = car.getId();
            p.teleport(w, 260.5, y, -745.5, 0, 15);
        });
        run(20, c -> c.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_BACK));
        server(5, s -> {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
            if (p.getServerWorld().getEntityById(ids[7]) instanceof com.yompix.novograd.entity.CarEntity car) car.interact(p, net.minecraft.util.Hand.MAIN_HAND);
            if (p.getServerWorld().getEntityById(ids[7]) instanceof com.yompix.novograd.entity.CarEntity car && !p.hasVehicle()) car.interact(p, net.minecraft.util.Hand.MAIN_HAND);
        });
        for (int i = 0; i < 26; i++) {
            shot(8, "ro_jack_" + i);
            if (i % 4 == 0) server(1, s -> {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                var w = p.getServerWorld();
                com.yompix.novograd.Novograd.LOG.info("[runover] jack: player in car={} hp={} driver={}", p.hasVehicle(), p.getHealth(),
                        w.getEntityById(ids[6]) instanceof CitizenEntity c ? c.getBlockPos() + " act=" + c.getActivity() + " veh=" + c.hasVehicle() : "gone");
            });
        }
        run(1, c -> c.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_FRONT));
        shot(10, "ro_jack_front");
        shot(40, "ro_jack_fight");
        server(5, s -> {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
            var w = p.getServerWorld();
            com.yompix.novograd.Novograd.LOG.info("[runover] jack end: player in car={} hp={} driver={}", p.hasVehicle(), p.getHealth(),
                    w.getEntityById(ids[6]) instanceof CitizenEntity c ? c.getBlockPos() + " act=" + c.getActivity() + " veh=" + c.hasVehicle() : "gone");
        });
        run(20, MinecraftClient::scheduleStop);
    }

    /** Автотест «armory»: новые стволы (огонь, прицел, перезарядка), обвесы и окраска, Мастерская, гранатомёт, телохранители. */
    private static void armory() {
        YompixSplash.markShown();
        GunFx.testFlashNs = 700_000_000L;
        ClientTickEvents.END_CLIENT_TICK.register(c -> {
            if (c.getServer() != null && c.world != null && c.world.getTime() % 10 == 0) c.getServer().execute(() -> {
                ServerPlayerEntity p = c.getServer().getPlayerManager().getPlayer(P);
                if (p != null && com.yompix.novograd.gta.Wanted.stars(p) > 0) com.yompix.novograd.gta.Wanted.clear(p);
            });
        });
        pre(60, c -> c.createIntegratedServerLoader().start("Fresh2", () -> c.setScreen(new com.yompix.novograd.client.menu.LifeMenuScreen())));
        run(100, c -> { if (c.player != null && c.player.isDead()) c.player.requestRespawn(); });
        server(40, s -> {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
            var d = Life.get(p);
            d.storyStarted = true;
            d.storyDone = true;
            d.chapter = 99;
            d.age = 22;
            d.money = 200000;
            d.dirty = 30000;
            Story.applyAge(p, 22);
            Life.sync(p);
        });
        for (int i = 0; i < 4; i++) key(10, GLFW.GLFW_KEY_SPACE);
        run(5, c -> { if (c.currentScreen != null) c.setScreen(null); });
        cmd(5, "gamemode survival " + P);
        cmd(5, "effect give " + P + " minecraft:resistance 99999 4 true");
        cmd(5, "time set 6000");
        cmd(2, "clear " + P);
        String[] guns = {"revolver", "eagle", "micro", "saiga", "carbine", "lmg", "rpg", "machete"};
        for (String g : guns) cmd(2, "give " + P + " novograd:" + g);
        cmd(2, "give " + P + " novograd:ammo 64");
        cmd(2, "give " + P + " novograd:ammo 64");
        cmd(2, "give " + P + " novograd:rocket 8");
        server(5, s -> {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
            var w = p.getServerWorld();
            int x = -600, z = -520;
            w.getChunk(x >> 4, z >> 4);
            int y = com.yompix.novograd.world.Island.surface(x, z) + 1;
            p.teleport(w, x + 0.5, y, z + 0.5, 60, 0);
        });
        cmd(10, "execute at " + P + " run fill ~-14 ~-1 ~-14 ~14 ~-1 ~14 minecraft:smooth_stone");
        cmd(2, "execute at " + P + " run fill ~-14 ~ ~-14 ~14 ~8 ~14 minecraft:air");
        cmd(2, "kill @e[type=novograd:citizen,distance=..40]");
        cmd(2, "kill @e[type=minecraft:item,distance=..40]");
        run(60, c -> { if (c.currentScreen != null) c.setScreen(null); });
        for (int i = 0; i < 7; i++) {
            final int slot = i;
            String g = guns[i];
            run(5, c -> c.player.getInventory().selectedSlot = slot);
            shot(30, "arm_" + g + "_idle");
            run(2, c -> c.options.attackKey.setPressed(true));
            shot(6, "arm_" + g + "_fire");
            run(1, c -> c.options.attackKey.setPressed(false));
            run(25, c -> c.options.useKey.setPressed(true));
            shot(20, "arm_" + g + "_ads");
            run(2, c -> c.options.useKey.setPressed(false));
            run(25, c -> ArsenalClient.send("reload", 0));
            run(3, c -> GunFx.testFreezeP = 0.4f);
            shot(3, "arm_" + g + "_reload");
            run(1, c -> GunFx.testFreezeP = -1);
            run(100, c -> {});
        }
        // обвесы и окраска
        server(5, s -> {
            var inv = s.getPlayerManager().getPlayer(P).getInventory();
            inv.getStack(4).set(com.yompix.novograd.gta.GunMods.MODS, 1 | 2 | 4 | 8 | (1 << 8));   // карабин: всё + камуфляж
            inv.getStack(1).set(com.yompix.novograd.gta.GunMods.MODS, 2 | 4 | (3 << 8));           // «Орёл»: точка, магазин, золото
            inv.getStack(2).set(com.yompix.novograd.gta.GunMods.MODS, 1 | 4 | (2 << 8));           // «Микро»: глушитель, неон
        });
        for (int slot : new int[]{4, 1, 2}) {
            run(5, c -> c.player.getInventory().selectedSlot = slot);
            shot(30, "arm_mod" + slot + "_idle");
            run(2, c -> c.options.useKey.setPressed(true));
            shot(25, "arm_mod" + slot + "_ads");
            run(2, c -> c.options.attackKey.setPressed(true));
            shot(5, "arm_mod" + slot + "_ads_fire");
            run(1, c -> { c.options.attackKey.setPressed(false); c.options.useKey.setPressed(false); });
            run(1, c -> c.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_FRONT));
            shot(20, "arm_mod" + slot + "_3p");
            run(1, c -> c.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON));
            run(40, c -> {});
        }
        // «Калибр» и Мастерская
        server(5, s -> com.yompix.novograd.life.Shop.openGuns(s.getPlayerManager().getPlayer(P)));
        shot(30, "arm_shop");
        run(5, c -> c.setScreen(new com.yompix.novograd.client.screen.GunTuneScreen()));
        shot(30, "arm_workshop");
        run(5, c -> ArsenalClient.send("mod", 3 * 256 + 2)); // «Сайга»: удлинённый магазин за деньги
        shot(20, "arm_workshop_bought");
        run(5, c -> c.setScreen(null));
        run(5, c -> c.player.getInventory().selectedSlot = 3);
        shot(20, "arm_saiga_extmag");
        // гранатомёт по машине
        server(5, s -> {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
            var car = new com.yompix.novograd.entity.CarEntity(com.yompix.novograd.ModContent.CAR, p.getServerWorld());
            var look = p.getRotationVector().multiply(1, 0, 1).normalize().multiply(12);
            car.refreshPositionAndAngles(p.getX() + look.x, p.getY(), p.getZ() + look.z, 0, 0);
            p.getServerWorld().spawnEntity(car);
        });
        run(20, c -> { c.player.getInventory().selectedSlot = 6; c.player.setPitch(4); });
        run(20, c -> c.options.attackKey.setPressed(true));
        run(2, c -> c.options.attackKey.setPressed(false));
        shot(4, "arm_rpg_launch");
        shot(6, "arm_rpg_boom");
        shot(30, "arm_rpg_after");
        // телохранители
        run(5, c -> c.setScreen(new com.yompix.novograd.client.screen.BodyguardScreen()));
        server(10, s -> {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
            for (int t = 0; t < 3; t++) com.yompix.novograd.gta.Bodyguards.hire(p, t);
            com.yompix.novograd.gta.Bodyguards.sendInfo(p);
        });
        shot(30, "arm_guards_phone");
        run(5, c -> c.setScreen(null));
        run(120, c -> c.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_BACK));
        shot(5, "arm_guards_arrive");
        // бандиты нападают
        server(20, s -> {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
            var w = p.getServerWorld();
            for (int i = 0; i < 3; i++) {
                var gang = new com.yompix.novograd.entity.CitizenEntity(com.yompix.novograd.ModContent.CITIZEN, w);
                gang.refreshPositionAndAngles(p.getX() + 12 + i * 2, p.getY(), p.getZ() + 6 - i * 3, 0, 0);
                gang.setup(CitizenEntity.randomSkin(w.getRandom()), CitizenEntity.Role.GANG, false, "Ящер");
                w.spawnEntity(gang);
                gang.attack(p);
            }
        });
        shot(30, "arm_guards_fight1");
        shot(40, "arm_guards_fight2");
        shot(60, "arm_guards_fight3");
        server(5, s -> {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
            var w = p.getServerWorld();
            for (var g : w.getEntitiesByClass(com.yompix.novograd.entity.BodyguardEntity.class, p.getBoundingBox().expand(60), e -> true))
                com.yompix.novograd.Novograd.LOG.info("[armory] guard {} hp={} dist={} target={}", g.getName().getString(), g.getHealth(), g.distanceTo(p), g.target());
            for (var c : w.getEntitiesByClass(CitizenEntity.class, p.getBoundingBox().expand(60), e -> e.getRole() == CitizenEntity.Role.GANG))
                com.yompix.novograd.Novograd.LOG.info("[armory] gang hp={} alive={}", c.getHealth(), c.isAlive());
            // в машину: охрана садится пассажирами
            var car = new com.yompix.novograd.entity.CarEntity(com.yompix.novograd.ModContent.CAR, w);
            car.refreshPositionAndAngles(p.getX() + 2, p.getY(), p.getZ(), 0, 0);
            w.spawnEntity(car);
            p.startRiding(car, true);
        });
        shot(80, "arm_guards_car");
        server(5, s -> {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
            var car = p.getVehicle();
            com.yompix.novograd.Novograd.LOG.info("[armory] car passengers={} controller={}", car == null ? -1 : car.getPassengerList().size(),
                    car instanceof com.yompix.novograd.entity.CarEntity ce ? ce.getControllingPassenger() : null);
        });
        run(40, MinecraftClient::scheduleStop);
    }

    private static void hold(int delay, java.util.function.Function<MinecraftClient, KeyBinding> kb, int ticks) {
        run(delay, c -> KeyBinding.setKeyPressed(InputUtil.fromTranslationKey(kb.apply(c).getBoundKeyTranslationKey()), true));
        run(ticks, c -> KeyBinding.setKeyPressed(InputUtil.fromTranslationKey(kb.apply(c).getBoundKeyTranslationKey()), false));
    }

    private static void key(int delay, int key) {
        run(delay, c -> pressKey(c, key));
    }

    private static void spaces(int n) {
        for (int i = 0; i < n; i++) key(14, GLFW.GLFW_KEY_SPACE);
    }

    private static void talk(int delay, String npc) {
        server(delay, s -> {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
            CitizenEntity c = Npcs.find(p.getServerWorld(), npc);
            if (c != null) Story.onTalk(p, c);
        });
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

    private static final List<Step> PRE = new ArrayList<>();
    private static int preIndex = -1, preTimer;

    /** Шаг до входа в мир (меню, заставка): идёт, пока игрока ещё нет. */
    private static void pre(int delay, Consumer<MinecraftClient> action) {
        PRE.add(new Step(delay, action));
    }

    private static void preShot(int delay, String name) {
        pre(delay, c -> ScreenshotRecorder.saveScreenshot(c.runDirectory, name + ".png", c.getFramebuffer(), t -> {}));
    }

    private static void shot(int delay, String name) {
        STEPS.add(new Step(delay, c -> ScreenshotRecorder.saveScreenshot(c.runDirectory, name + ".png", c.getFramebuffer(), t -> {})));
    }

    static {
        ClientTickEvents.END_CLIENT_TICK.register(c -> {
            if (PRE.isEmpty() || c.player != null || c.getOverlay() != null || preIndex >= PRE.size()) return;
            if (preIndex < 0) {
                // ждём, пока появится заставка или меню
                if (!(c.currentScreen instanceof YompixSplash) && !(c.currentScreen instanceof com.yompix.novograd.client.menu.LifeMenuScreen)) return;
                preIndex = 0;
                preTimer = PRE.get(0).delay();
            }
            if (--preTimer > 0) return;
            PRE.get(preIndex).action().accept(c);
            preIndex++;
            if (preIndex < PRE.size()) preTimer = PRE.get(preIndex).delay();
        });
        ClientTickEvents.END_CLIENT_TICK.register(c -> {
            if (STEPS.isEmpty() || c.player == null || c.world == null) return;
            if (index < 0) {
                index = 0;
                timer = STEPS.get(0).delay();
            }
            if (index >= STEPS.size()) return;
            if (--timer > 0) return;
            STEPS.get(index).action().accept(c);
            index++;
            if (index < STEPS.size()) timer = STEPS.get(index).delay();
        });
    }
}
