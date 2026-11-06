package com.yompix.novograd.client.style;

import com.google.gson.JsonObject;
import com.yompix.novograd.client.NovogradClient;
import com.yompix.novograd.life.Appearance;
import com.yompix.novograd.life.Style;
import com.yompix.novograd.life.StyleCatalog;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Клиент стиля SA: внешность всех игроков (рассылает сервер), примерка в магазине (preview — только для себя),
 * подмена скина (см. mixin PlayerSkinMixin) и причёски/шапки/очки поверх модели игрока.
 */
public final class StyleClient {
    private StyleClient() {}

    public static final Map<UUID, Appearance> LOOKS = new HashMap<>();
    public static final Map<UUID, Integer> STRENGTH = new HashMap<>();
    /** Примерка: пока открыт магазин, свой герой рисуется так. */
    @Nullable public static Appearance preview;

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(Style.S2C.ID, (payload, ctx) -> receive(ctx.client(), payload.type(), payload.data()));
        ClientPlayConnectionEvents.JOIN.register((h, s, c) -> {
            LOOKS.clear();
            STRENGTH.clear();
            preview = null;
        });
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, ctx) -> {
            if (renderer instanceof PlayerEntityRenderer pr) helper.register(new PlayerLookFeature(pr));
        });
    }

    public static void send(String type, NbtCompound data) {
        if (MinecraftClient.getInstance().getNetworkHandler() != null) ClientPlayNetworking.send(new Style.C2S(type, data));
    }

    private static void receive(MinecraftClient client, String type, NbtCompound n) {
        switch (type) {
            case "looks" -> {
                for (NbtElement e : n.getList("list", NbtElement.COMPOUND_TYPE)) put((NbtCompound) e);
            }
            case "look" -> put(n);
            case "open" -> {
                String kind = n.getString("kind");
                if (kind.equals("gym")) client.setScreen(new GymScreen());
                else if (n.getBoolean("locked")) {
                    if (client.currentScreen instanceof StyleAppScreen s) s.locked();
                    else client.setScreen(new StyleAppScreen(true));
                } else client.setScreen(new StyleShopScreen(kind));
            }
            case "gym_start", "gym_result" -> {
                if (client.currentScreen instanceof GymScreen g) g.server(type, n);
            }
            default -> {}
        }
    }

    private static void put(NbtCompound e) {
        if (!e.containsUuid("id")) return;
        UUID id = e.getUuid("id");
        LOOKS.put(id, Appearance.fromNbt(e.getCompound("look")));
        STRENGTH.put(id, e.getInt("str"));
    }

    /** Внешность игрока на этом клиенте (своя — с учётом примерки); null — неизвестна. */
    @Nullable
    public static Appearance look(PlayerEntity p) {
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean me = mc.player != null && p.getUuid().equals(mc.player.getUuid());
        if (me && preview != null) return preview;
        Appearance a = LOOKS.get(p.getUuid());
        if (a == null && me) a = NovogradClient.data.look;
        return a;
    }

    /** Подмена скина: null — оставить скин Minecraft. */
    @Nullable
    public static SkinTextures skin(AbstractClientPlayerEntity p, SkinTextures orig) {
        Appearance a = look(p);
        if (a == null || !a.custom) return null;
        var id = SkinComposer.texture(a);
        if (id == null) return null;
        return new SkinTextures(id, null, orig.capeTexture(), orig.elytraTexture(), a.slim ? SkinTextures.Model.SLIM : SkinTextures.Model.WIDE, orig.secure());
    }

    /** Сила 0–100: модель чуть шире в плечах (до +8%). */
    public static float width(PlayerEntity p) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int s = mc.player != null && p.getUuid().equals(mc.player.getUuid()) ? NovogradClient.data.strength : STRENGTH.getOrDefault(p.getUuid(), 0);
        Appearance a = look(p);
        if (a == null || !a.custom) return 1f;
        return 1f + Math.max(0, Math.min(100, s)) * 0.0008f;
    }

    private static String hex(int rgb) {
        return String.format("%06x", rgb & 0xFFFFFF);
    }

    /** Описание головы для {@link com.yompix.novograd.client.render.HairFeature#drawLook}. */
    public static JsonObject headJson(Appearance a) {
        JsonObject o = new JsonObject();
        int hair = StyleCatalog.hairColor(a.hairColor);
        o.addProperty("style", a.hair);
        o.addProperty("hair", hex(hair));
        o.addProperty("skin", hex(StyleCatalog.SKIN_TONES[Math.floorMod(a.skin, StyleCatalog.SKIN_TONES.length)]));
        if (a.slim) o.addProperty("girl", true);
        switch (a.beard) {
            case "mustache" -> o.addProperty("mustache", hex(hair));
            case "goatee" -> o.addProperty("goatee", hex(hair));
            case "beard" -> o.addProperty("beard", hex(hair));
            case "full" -> {
                o.addProperty("beard", hex(hair));
                o.addProperty("mustache", hex(hair));
            }
            default -> {}
        }
        if (!a.hat.isEmpty()) {
            var w = StyleCatalog.wear(Appearance.id(a.hat));
            if (w != null) {
                String key = switch (w.id()) {
                    case "gold_cap" -> "cap";
                    default -> w.id();
                };
                o.addProperty(key, hex(w.color(Appearance.colorIndex(a.hat))));
            }
        }
        if (!a.glasses.isEmpty()) {
            var w = StyleCatalog.wear(Appearance.id(a.glasses));
            if (w != null) o.addProperty("glasses", w.id());
        }
        return o;
    }

    /** Телефон → «Стиль». */
    public static void phone() {
        MinecraftClient.getInstance().setScreen(new StyleAppScreen(false));
    }
}
