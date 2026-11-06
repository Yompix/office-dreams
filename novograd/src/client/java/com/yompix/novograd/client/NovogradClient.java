package com.yompix.novograd.client;

import com.yompix.novograd.ClientHooks;
import com.yompix.novograd.Novograd;
import com.yompix.novograd.ModContent;
import com.yompix.novograd.client.render.CarRenderer;
import com.yompix.novograd.client.render.CitizenRenderer;
import com.yompix.novograd.client.screen.CashierScreen;
import com.yompix.novograd.client.screen.LessonScreen;
import com.yompix.novograd.client.screen.PhoneScreen;
import com.yompix.novograd.client.screen.RouletteScreen;
import com.yompix.novograd.client.screen.ShopScreen;
import com.yompix.novograd.client.screen.SlotsScreen;
import com.yompix.novograd.client.screen.TypingScreen;
import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.life.LifeData;
import com.yompix.novograd.life.LifeNet;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.EmptyEntityRenderer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.nbt.NbtCompound;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

public class NovogradClient implements ClientModInitializer {
    private static net.minecraft.client.sound.SoundInstance ambientVoice;
    private static double lastCamX = Double.NaN;

    /** Отладка плавности: где камера в каждом кадре (-Dnovograd.camlog=true). */
    private static double lastTickX = Double.NaN;

    /** Отладка: шаг машины под игроком за каждый клиентский тик. */
    public static void tickLog() {
        var mc = MinecraftClient.getInstance();
        if (!Boolean.getBoolean("novograd.camlog") || mc.player == null || mc.player.getVehicle() == null) return;
        double x = mc.player.getVehicle().getX();
        if (!Double.isNaN(lastTickX)) Novograd.LOG.info("[tick] {}", String.format("%.4f", x - lastTickX));
        lastTickX = x;
    }

    public static void camLog() {
        var mc = MinecraftClient.getInstance();
        if (!Boolean.getBoolean("novograd.camlog") || mc.player == null || !mc.player.hasVehicle()) return;
        var cam = mc.gameRenderer.getCamera().getPos();
        lastCamX = cam.x;
    }
    public static LifeData data = new LifeData();
    public static long dataVersion;

    private static KeyBinding phoneKey, hornKey, mapKey, radioKey, radioDownKey, radioUpKey, jobKey, cinemaKey, gradeKey, lightsKey, leftKey, rightKey, hazardKey, nitroKey;
    public static int wantedStars;
    public static boolean wantedSeen;
    private static final Map<Integer, CarEngineSound> ENGINES = new HashMap<>();
    private static int screenshotIn = -1;
    private static boolean hudWasHidden;

    @Override
    public void onInitializeClient() {
        EntityModelLayerRegistry.registerModelLayer(CarRenderer.LAYER, CarRenderer::modelData);
        EntityRendererRegistry.register(ModContent.CAR, CarRenderer::new);
        EntityModelLayerRegistry.registerModelLayer(com.yompix.novograd.client.render.BallRenderer.LAYER, com.yompix.novograd.client.render.BallRenderer::modelData);
        EntityRendererRegistry.register(ModContent.BALL, com.yompix.novograd.client.render.BallRenderer::new);
        EntityModelLayerRegistry.registerModelLayer(com.yompix.novograd.client.render.ScooterRenderer.LAYER, com.yompix.novograd.client.render.ScooterRenderer::modelData);
        EntityRendererRegistry.register(ModContent.SCOOTER, com.yompix.novograd.client.render.ScooterRenderer::new);
        EntityRendererRegistry.register(ModContent.CITIZEN, com.yompix.novograd.client.render.CitizenGeoRenderer::new);
        EntityRendererRegistry.register(ModContent.SEAT, EmptyEntityRenderer::new);
        BlockRenderLayerMap.INSTANCE.putBlocks(RenderLayer.getCutout(), ModContent.TV, ModContent.TRAFFIC_LIGHT, ModContent.STREET_LAMP,
                ModContent.LAMP_POST, ModContent.COMPUTER, ModContent.SCHOOL_DESK, ModContent.MAILBOX, ModContent.SLOT_MACHINE, ModContent.ROULETTE, ModContent.ATM);

        phoneKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.novograd.phone", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "category.novograd"));
        mapKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.novograd.map", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_M, "category.novograd"));
        gradeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.novograd.grade", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, "category.novograd"));
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.END.register(c -> com.yompix.novograd.client.render.ColorGrade.render(c.tickCounter().getTickDelta(false)));
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.END.register(c -> camLog());
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(c -> tickLog());
        radioKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.novograd.radio", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, "category.novograd"));
        jobKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.novograd.job", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J, "category.novograd"));
        cinemaKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.novograd.cinema", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_B, "category.novograd"));
        radioDownKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.novograd.radio_down", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_MINUS, "category.novograd"));
        radioUpKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.novograd.radio_up", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_EQUAL, "category.novograd"));
        NovogradConfig.load();
        NovogradConfig.registerPacks();
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STARTED.register(c -> NovogradConfig.applyPacks());
        hornKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.novograd.horn", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H, "category.novograd"));
        lightsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.novograd.lights", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_L, "category.novograd"));
        leftKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.novograd.left", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_Z, "category.novograd"));
        rightKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.novograd.right", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_X, "category.novograd"));
        nitroKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.novograd.nitro", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_N, "category.novograd"));
        hazardKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.novograd.hazard", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_C, "category.novograd"));

        Novograd.CLIENT = new ClientHooks() {
            @Override
            public void openPhone() {
                MinecraftClient.getInstance().setScreen(new PhoneScreen(null));
            }

            @Override
            public void carTick(CarEntity car) {
                engine(car);
            }

            @Override
            public net.minecraft.entity.Entity player() {
                return MinecraftClient.getInstance().player;
            }
        };

        ClientPlayNetworking.registerGlobalReceiver(LifeNet.S2C.ID, (payload, ctx) -> receive(ctx.client(), payload.type(), payload.data()));
        ClientPlayConnectionEvents.JOIN.register((h, s, c) -> {
            readySent = false;
            data = new LifeData();
            com.yompix.novograd.client.story.ClientStory.objective = new NbtCompound();
            send("hello", new NbtCompound());
        });
        ClientTickEvents.END_CLIENT_TICK.register(NovogradClient::tick);
        // заставка студии при запуске, своё главное меню и пауза вместо ванильных
        ClientTickEvents.END_CLIENT_TICK.register(c -> {
            if (c.currentScreen instanceof net.minecraft.client.gui.screen.TitleScreen) {
                c.setScreen(com.yompix.novograd.client.story.YompixSplash.shown() ? new com.yompix.novograd.client.menu.LifeMenuScreen()
                        : new com.yompix.novograd.client.story.YompixSplash(new com.yompix.novograd.client.menu.LifeMenuScreen()));
            } else if (c.currentScreen instanceof net.minecraft.client.gui.screen.GameMenuScreen g && g.shouldShowMenu()) {
                c.setScreen(new com.yompix.novograd.client.menu.LifePauseScreen());
            }
        });
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((c, screen, w, h) -> {
            String what = screen instanceof net.minecraft.client.gui.screen.world.LevelLoadingScreen ? "Строим Новоград"
                    : screen instanceof net.minecraft.client.gui.screen.DownloadingTerrainScreen ? "Входим в город"
                    : screen instanceof net.minecraft.client.gui.screen.MessageScreen ? "Минутку" : null;
            if (what != null)
                net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.afterRender(screen).register((sc, ctx, mx, my, d) ->
                        com.yompix.novograd.client.menu.LoadingOverlay.draw(ctx, w, h, what));
        });
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((c, screen, w, h) -> {
            if (screen instanceof net.minecraft.client.gui.screen.TitleScreen)
                net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.afterRender(screen).register((sc, ctx, mx, my, d) -> {
                    var tr = c.textRenderer;
                    ctx.drawText(tr, "Новоград", 4, h - 30, 0xFFFFC107, true);
                    ctx.drawText(tr, "© Yompix Games", 4, h - 20, 0xFF8A5CFF, true);
                });
        });
        HudRenderCallback.EVENT.register(LifeHud::render);
        HudRenderCallback.EVENT.register((ctx, t) -> CarRadio.render(ctx));
        HudRenderCallback.EVENT.register((ctx, t) -> { if (!CinemaCam.active()) Minimap.render(ctx); });
        HudRenderCallback.EVENT.register((ctx, t) -> CinemaCam.render(ctx));
        HudRenderCallback.EVENT.register((ctx, t) -> com.yompix.novograd.client.story.StoryHud.render(ctx));
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.AFTER_TRANSLUCENT.register(com.yompix.novograd.client.story.StoryHud::renderWorld);
        DevAutotest.init();
    }

    public static void send(String type, NbtCompound data) {
        if (MinecraftClient.getInstance().getNetworkHandler() != null) ClientPlayNetworking.send(new LifeNet.C2S(type, data));
    }

    public static void send(String type) {
        send(type, new NbtCompound());
    }

    private static void receive(MinecraftClient client, String type, NbtCompound n) {
        switch (type) {
            case "data" -> {
                data = LifeData.fromNbt(n);
                dataVersion++;
            }
            case "dialog" -> show(client, new com.yompix.novograd.client.story.DialogScreen(n));
            case "act_target" -> {
                Minimap.actLabel = n.contains("x") ? n.getString("label") : "";
                Minimap.actX = n.getDouble("x");
                Minimap.actZ = n.getDouble("z");
                if (!n.contains("x")) Minimap.actTimer = -1;
            }
            case "act_timer" -> Minimap.actTimer = n.getInt("sec");
            case "cut_actor" -> com.yompix.novograd.client.story.CutsceneScreen.ACTORS.put(n.getString("name"), n.getInt("id"));
            case "cutscene" -> show(client, new com.yompix.novograd.client.story.CutsceneScreen(n));
            case "credits" -> show(client, new com.yompix.novograd.client.story.CreditsScreen());
            case "objective" -> com.yompix.novograd.client.story.ClientStory.objective = n;
            case "dialog_view" -> show(client, new com.yompix.novograd.client.story.DialogScreen(n, true));
            case "siren" -> {
                com.yompix.novograd.client.story.ClientStory.sirenAt = System.currentTimeMillis();
                if (client.player != null) client.player.playSound(net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), 1f, 1.6f);
            }
            case "players" -> com.yompix.novograd.client.story.ClientStory.players = n;
            case "places" -> com.yompix.novograd.client.story.ClientStory.places = n;
            case "dialog_close" -> {
                if (client.currentScreen instanceof com.yompix.novograd.client.story.DialogScreen ds) ds.forceClose();
            }
            case "title" -> {
                com.yompix.novograd.client.story.ClientStory.title(n.getString("title"), n.getString("subtitle"));
                if (client.player != null) client.player.playSound(ModContent.LEVEL_UP, 0.7f, 0.8f);
            }
            case "mission_done" -> com.yompix.novograd.client.story.ClientStory.missionDone(n.getString("title"));
            case "countdown" -> com.yompix.novograd.client.story.ClientStory.countdown(n.getString("text"));
            case "bj_state" -> {
                if (client.currentScreen instanceof com.yompix.novograd.client.screen.OnlineCasinoScreen s) s.bj(n);
            }
            case "crash_start", "crash_cashout", "crash_end" -> {
                if (client.currentScreen instanceof com.yompix.novograd.client.screen.OnlineCasinoScreen s) s.crash(type, n);
            }
            case "coin_result" -> {
                if (client.currentScreen instanceof com.yompix.novograd.client.screen.OnlineCasinoScreen s) s.coin(n);
            }
            case "achievement" -> com.yompix.novograd.client.story.ClientStory.achievement(n.getString("name"), n.getString("desc"));
            case "ambient" -> {
                var e = client.world == null ? null : client.world.getEntityById(n.getInt("entity"));
                if (e != null) com.yompix.novograd.client.story.ClientStory.talk(e.getId(), 1800);
                if (e != null && !n.getString("voice").isEmpty()) {
                    if (ambientVoice != null) client.getSoundManager().stop(ambientVoice);
                    ambientVoice = new net.minecraft.client.sound.PositionedSoundInstance(Novograd.id("voice." + n.getString("voice")),
                            net.minecraft.sound.SoundCategory.VOICE, 1f, 1f, net.minecraft.util.math.random.Random.create(), false, 0,
                            net.minecraft.client.sound.SoundInstance.AttenuationType.LINEAR, e.getX(), e.getEyeY(), e.getZ(), false);
                    client.getSoundManager().play(ambientVoice);
                }
            }
            case "open_phone" -> client.setScreen(new PhoneScreen(n.getString("app")));
            case "side_list" -> client.setScreen(new com.yompix.novograd.client.screen.MissionsScreen(n));
            case "open_garage" -> client.setScreen(new com.yompix.novograd.client.screen.GarageScreen(n));
            case "garage_info" -> {
                if (client.currentScreen instanceof com.yompix.novograd.client.screen.GarageScreen g) g.update(n);
            }
            case "wanted" -> {
                wantedStars = n.getInt("stars");
                wantedSeen = n.getBoolean("seen");
            }
            case "gta_title" -> com.yompix.novograd.client.story.StoryHud.gtaTitle(n.getString("title"), n.getString("sub"));
            case "open_atm" -> client.setScreen(new com.yompix.novograd.client.screen.AtmScreen(n.getInt("balance"), n.getInt("cash")));
            case "atm_result" -> {
                if (client.currentScreen instanceof com.yompix.novograd.client.screen.AtmScreen s) s.result(n);
            }
            case "rhythm" -> client.setScreen(new com.yompix.novograd.client.screen.RhythmScreen(n.getString("mode")));
            case "open_lesson" -> client.setScreen(new LessonScreen(n));
            case "lesson_result" -> {
                if (client.currentScreen instanceof LessonScreen s) s.result(n);
            }
            case "open_shop" -> client.setScreen(new ShopScreen(n.getBoolean("delivery"), n.getBoolean("guns")));
            case "open_cashier" -> client.setScreen(new CashierScreen(n));
            case "open_typing" -> client.setScreen(new TypingScreen(n));
            case "open_casino" -> client.setScreen(n.getString("game").equals("roulette") ? new RouletteScreen() : new SlotsScreen());
            case "slots_result" -> {
                if (client.currentScreen instanceof SlotsScreen s) s.result(n);
            }
            case "roulette_result" -> {
                if (client.currentScreen instanceof RouletteScreen s) s.result(n);
            }
            default -> {}
        }
    }

    private static boolean readySent;
    private static int calmTicks;
    @org.jetbrains.annotations.Nullable private static net.minecraft.client.gui.screen.Screen pending;

    /** Сюжетный экран откроется, только когда игра не занята загрузкой. */
    public static void show(MinecraftClient client, net.minecraft.client.gui.screen.Screen s) {
        if (client.currentScreen == null || client.currentScreen instanceof com.yompix.novograd.client.screen.LifeScreen) client.setScreen(s);
        else pending = s;
    }

    public static void requeue(net.minecraft.client.gui.screen.Screen s) {
        pending = s;
    }

    private static void tick(MinecraftClient client) {
        if (client.player == null) return;
        calmTicks = client.currentScreen == null ? calmTicks + 1 : 0;
        if (!readySent && calmTicks > 20 && client.world != null) {
            readySent = true;
            send("ready");
        }
        if (pending != null && client.currentScreen == null && calmTicks > 5) {
            var s = pending;
            pending = null;
            client.setScreen(s);
        }
        while (phoneKey.wasPressed()) {
            if (client.currentScreen == null && client.player.getInventory().count(ModContent.PHONE) > 0) {
                client.setScreen(new PhoneScreen(null));
                client.player.playSound(ModContent.PHONE_OPEN, 0.5f, 1f);
            } else if (client.currentScreen == null) {
                client.player.sendMessage(net.minecraft.text.Text.literal("У тебя нет телефона — купи в магазине"), true);
            }
        }
        while (hornKey.wasPressed()) send("horn");
        while (nitroKey.wasPressed()) send("nitro");
        int[][] carKeys = {{0, com.yompix.novograd.entity.CarEntity.HEADLIGHTS}, {1, com.yompix.novograd.entity.CarEntity.LEFT},
                {2, com.yompix.novograd.entity.CarEntity.RIGHT}, {3, com.yompix.novograd.entity.CarEntity.HAZARD}};
        KeyBinding[] ck = {lightsKey, leftKey, rightKey, hazardKey};
        for (int[] k : carKeys)
            while (ck[k[0]].wasPressed()) {
                if (client.player != null && client.player.getVehicle() instanceof com.yompix.novograd.entity.CarEntity car && car.getControllingPassenger() == client.player) {
                    NbtCompound n = new NbtCompound();
                    n.putInt("bit", k[1]);
                    send("car_light", n);
                }
            }
        while (gradeKey.wasPressed()) {
            client.player.sendMessage(net.minecraft.text.Text.literal("Графика Новограда: " + com.yompix.novograd.client.render.ColorGrade.cycle()), true);
            var g = com.yompix.novograd.client.render.ColorGrade.class;
            NovogradConfig.get().graphics = !com.yompix.novograd.client.render.ColorGrade.enabled ? 0 : com.yompix.novograd.client.render.ColorGrade.quality > 0.5f ? 2 : 1;
            NovogradConfig.save();
        }
        while (radioKey.wasPressed()) CarRadio.next(client);
        while (jobKey.wasPressed()) send("activity");
        while (cinemaKey.wasPressed()) CinemaCam.toggle(client);
        CinemaCam.tick(client);
        while (radioDownKey.wasPressed()) CarRadio.volume(client, -0.1f);
        while (radioUpKey.wasPressed()) CarRadio.volume(client, 0.1f);
        CarRadio.tick(client);
        while (mapKey.wasPressed()) if (client.currentScreen == null) client.setScreen(new com.yompix.novograd.client.screen.MapScreen());
        ENGINES.values().removeIf(s -> s.isDone());
        if (screenshotIn >= 0 && --screenshotIn == 0) {
            ScreenshotRecorder.saveScreenshot(client.runDirectory, client.getFramebuffer(),
                    t -> client.execute(() -> client.player.sendMessage(net.minecraft.text.Text.literal("☺ Фото сохранено в папку screenshots"), false)));
            client.options.hudHidden = hudWasHidden;
            screenshotIn = -1;
        }
    }

    /** Камера телефона: прячем интерфейс на пару кадров и делаем снимок. */
    public static void takePhoto() {
        MinecraftClient c = MinecraftClient.getInstance();
        c.setScreen(null);
        hudWasHidden = c.options.hudHidden;
        c.options.hudHidden = true;
        screenshotIn = 3;
        send("photo");
        if (c.player != null) c.player.playSound(ModContent.PHONE_CLICK, 1f, 0.6f);
    }

    private static void engine(CarEntity car) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.player == null || car.getControllingPassenger() == null) return;
        if (car.squaredDistanceTo(c.player) > 40 * 40) return;
        if (ENGINES.containsKey(car.getId())) return;
        CarEngineSound s = new CarEngineSound(car);
        ENGINES.put(car.getId(), s);
        c.getSoundManager().play(s);
    }
}
