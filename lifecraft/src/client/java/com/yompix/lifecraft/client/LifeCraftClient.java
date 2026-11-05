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
            data = new LifeData();
            send("hello", new NbtCompound());
        });
        ClientTickEvents.END_CLIENT_TICK.register(LifeCraftClient::tick);
        HudRenderCallback.EVENT.register(LifeHud::render);
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

    private static void tick(MinecraftClient client) {
        if (client.player == null) return;
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
