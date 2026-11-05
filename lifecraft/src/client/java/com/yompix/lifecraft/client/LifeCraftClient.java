package com.yompix.lifecraft.client;

import com.yompix.lifecraft.ClientHooks;
import com.yompix.lifecraft.LifeCraft;
import com.yompix.lifecraft.ModContent;
import com.yompix.lifecraft.client.render.CarRenderer;
import com.yompix.lifecraft.client.render.CitizenRenderer;
import com.yompix.lifecraft.client.screen.CashierScreen;
import com.yompix.lifecraft.client.screen.LessonScreen;
import com.yompix.lifecraft.client.screen.PhoneScreen;
import com.yompix.lifecraft.client.screen.RouletteScreen;
import com.yompix.lifecraft.client.screen.ShopScreen;
import com.yompix.lifecraft.client.screen.SlotsScreen;
import com.yompix.lifecraft.client.screen.TypingScreen;
import com.yompix.lifecraft.entity.CarEntity;
import com.yompix.lifecraft.life.LifeData;
import com.yompix.lifecraft.life.LifeNet;
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

public class LifeCraftClient implements ClientModInitializer {
    public static LifeData data = new LifeData();
    public static long dataVersion;

    private static KeyBinding phoneKey, hornKey;
    private static final Map<Integer, CarEngineSound> ENGINES = new HashMap<>();
    private static int screenshotIn = -1;
    private static boolean hudWasHidden;

    @Override
    public void onInitializeClient() {
        EntityModelLayerRegistry.registerModelLayer(CarRenderer.LAYER, CarRenderer::modelData);
        EntityRendererRegistry.register(ModContent.CAR, CarRenderer::new);
        EntityRendererRegistry.register(ModContent.CITIZEN, CitizenRenderer::new);
        EntityRendererRegistry.register(ModContent.SEAT, EmptyEntityRenderer::new);
        BlockRenderLayerMap.INSTANCE.putBlocks(RenderLayer.getCutout(), ModContent.TV, ModContent.TRAFFIC_LIGHT, ModContent.STREET_LAMP,
                ModContent.LAMP_POST, ModContent.COMPUTER, ModContent.SCHOOL_DESK, ModContent.MAILBOX, ModContent.SLOT_MACHINE, ModContent.ROULETTE, ModContent.ATM);

        phoneKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.lifecraft.phone", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "category.lifecraft"));
        hornKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.lifecraft.horn", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H, "category.lifecraft"));

        LifeCraft.CLIENT = new ClientHooks() {
            @Override
            public void openPhone() {
                MinecraftClient.getInstance().setScreen(new PhoneScreen(null));
            }

            @Override
            public void carTick(CarEntity car) {
                engine(car);
            }
        };

        ClientPlayNetworking.registerGlobalReceiver(LifeNet.S2C.ID, (payload, ctx) -> receive(ctx.client(), payload.type(), payload.data()));
        ClientPlayConnectionEvents.JOIN.register((h, s, c) -> {
            readySent = false;
            data = new LifeData();
            com.yompix.lifecraft.client.story.ClientStory.objective = new NbtCompound();
            send("hello", new NbtCompound());
        });
        ClientTickEvents.END_CLIENT_TICK.register(LifeCraftClient::tick);
        // заставка студии при запуске, своё главное меню и пауза вместо ванильных
        ClientTickEvents.END_CLIENT_TICK.register(c -> {
            if (c.currentScreen instanceof net.minecraft.client.gui.screen.TitleScreen) {
                c.setScreen(com.yompix.lifecraft.client.story.YompixSplash.shown() ? new com.yompix.lifecraft.client.menu.LifeMenuScreen()
                        : new com.yompix.lifecraft.client.story.YompixSplash(new com.yompix.lifecraft.client.menu.LifeMenuScreen()));
            } else if (c.currentScreen instanceof net.minecraft.client.gui.screen.GameMenuScreen g && g.shouldShowMenu()) {
                c.setScreen(new com.yompix.lifecraft.client.menu.LifePauseScreen());
            }
        });
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((c, screen, w, h) -> {
            String what = screen instanceof net.minecraft.client.gui.screen.world.LevelLoadingScreen ? "Строим Новоград"
                    : screen instanceof net.minecraft.client.gui.screen.DownloadingTerrainScreen ? "Входим в город"
                    : screen instanceof net.minecraft.client.gui.screen.MessageScreen ? "Минутку" : null;
            if (what != null)
                net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.afterRender(screen).register((sc, ctx, mx, my, d) ->
                        com.yompix.lifecraft.client.menu.LoadingOverlay.draw(ctx, w, h, what));
        });
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((c, screen, w, h) -> {
            if (screen instanceof net.minecraft.client.gui.screen.TitleScreen)
                net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.afterRender(screen).register((sc, ctx, mx, my, d) -> {
                    var tr = c.textRenderer;
                    ctx.drawText(tr, "LifeCraft 0.2", 4, h - 30, 0xFFFFC107, true);
                    ctx.drawText(tr, "© Yompix Games", 4, h - 20, 0xFF8A5CFF, true);
                });
        });
        HudRenderCallback.EVENT.register(LifeHud::render);
        HudRenderCallback.EVENT.register((ctx, t) -> com.yompix.lifecraft.client.story.StoryHud.render(ctx));
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.AFTER_TRANSLUCENT.register(com.yompix.lifecraft.client.story.StoryHud::renderWorld);
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
            case "dialog" -> show(client, new com.yompix.lifecraft.client.story.DialogScreen(n));
            case "cutscene" -> show(client, new com.yompix.lifecraft.client.story.CutsceneScreen(n));
            case "credits" -> show(client, new com.yompix.lifecraft.client.story.CreditsScreen());
            case "objective" -> com.yompix.lifecraft.client.story.ClientStory.objective = n;
            case "title" -> {
                com.yompix.lifecraft.client.story.ClientStory.title(n.getString("title"), n.getString("subtitle"));
                if (client.player != null) client.player.playSound(ModContent.LEVEL_UP, 0.7f, 0.8f);
            }
            case "mission_done" -> com.yompix.lifecraft.client.story.ClientStory.missionDone(n.getString("title"));
            case "countdown" -> com.yompix.lifecraft.client.story.ClientStory.countdown(n.getString("text"));
            case "bj_state" -> {
                if (client.currentScreen instanceof com.yompix.lifecraft.client.screen.OnlineCasinoScreen s) s.bj(n);
            }
            case "crash_start", "crash_cashout", "crash_end" -> {
                if (client.currentScreen instanceof com.yompix.lifecraft.client.screen.OnlineCasinoScreen s) s.crash(type, n);
            }
            case "coin_result" -> {
                if (client.currentScreen instanceof com.yompix.lifecraft.client.screen.OnlineCasinoScreen s) s.coin(n);
            }
            case "achievement" -> com.yompix.lifecraft.client.story.ClientStory.achievement(n.getString("name"), n.getString("desc"));
            case "ambient" -> {
                var e = client.world == null ? null : client.world.getEntityById(n.getInt("entity"));
                if (e != null) client.getSoundManager().play(new net.minecraft.client.sound.PositionedSoundInstance(LifeCraft.id("voice." + n.getString("voice")),
                        net.minecraft.sound.SoundCategory.VOICE, 1f, 1f, net.minecraft.util.math.random.Random.create(), false, 0,
                        net.minecraft.client.sound.SoundInstance.AttenuationType.LINEAR, e.getX(), e.getEyeY(), e.getZ(), false));
            }
            case "open_phone" -> client.setScreen(new PhoneScreen(n.getString("app")));
            case "open_lesson" -> client.setScreen(new LessonScreen(n));
            case "lesson_result" -> {
                if (client.currentScreen instanceof LessonScreen s) s.result(n);
            }
            case "open_shop" -> client.setScreen(new ShopScreen(n.getBoolean("delivery")));
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
        if (client.currentScreen == null || client.currentScreen instanceof com.yompix.lifecraft.client.screen.LifeScreen) client.setScreen(s);
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
