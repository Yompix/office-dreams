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
            creatorPending = false;
            // 0.9.6: без ванильной обучалки «Move with WASD» — у Новограда свои подсказки
            if (c.getTutorialManager() != null) c.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
        });
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(c -> {
            if (creatorPending && c.player != null && c.player.age % 10 == 0) tryOpenCreator(c);
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
            case "creator" -> {
                creatorPending = true;
                tryOpenCreator(client);
            }
            case "gym_start", "gym_result" -> {
                if (client.currentScreen instanceof GymScreen g) g.server(type, n);
            }
            default -> {}
        }
    }

    /** Новичок: редактор после заставки Yompix и пока нет других экранов. */
    public static void tryOpenCreator(MinecraftClient c) {
        if (!creatorPending || c.player == null || c.world == null) return;
        if (c.currentScreen != null || com.yompix.novograd.client.story.CutsceneScreen.current != null) return;
        creatorPending = false;
        c.setScreen(new CreatorScreen(CreatorScreen.Mode.FIRST));
    }

    private static void put(NbtCompound e) {
        if (!e.containsUuid("id")) return;
        UUID id = e.getUuid("id");
        LOOKS.put(id, Appearance.fromNbt(e.getCompound("look")));
        STRENGTH.put(id, e.getInt("str"));
        if (e.contains("fat")) FAT.put(id, e.getFloat("fat"));
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

    /** Ширина модели по телосложению (и чуть уже у женского персонажа). */
    public static float buildWidth(Appearance a) {
        float w = switch (a.build) {
            case 0 -> 0.93f;
            case 2 -> 1.04f;
            case 3 -> 1.11f;
            default -> 1f;
        };
        return a.slim ? w * 0.97f : w;
    }

    /** Рост: ±5 % (только картинка, хитбокс прежний). */
    public static float buildHeight(Appearance a) {
        return 1f + a.height * 0.01f;
    }

    /** Сила 0–100: модель чуть шире в плечах (до +8%) — поверх телосложения. */
    public static float width(PlayerEntity p) {
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean me = mc.player != null && p.getUuid().equals(mc.player.getUuid());
        int s = me ? NovogradClient.data.strength : STRENGTH.getOrDefault(p.getUuid(), 0);
        Appearance a = look(p);
        if (a == null || !a.custom) return 1f;
        float target;
        if (me && preview != null) target = buildWidth(a); // в редакторе — как выбрано ползунком
        else {
            // 0.9.6: в игре ширина — от жира (еда/спорт), плавно
            float fat = me ? NovogradClient.data.fat : FAT.getOrDefault(p.getUuid(), 30f);
            target = (a.slim ? 0.97f : 1f) * (0.92f + Math.max(0, Math.min(100, fat)) * 0.0026f);
        }
        target *= 1f + Math.max(0, Math.min(100, s)) * 0.0008f;
        Float cur = SMOOTH.get(p.getUuid());
        if (cur == null || Math.abs(cur - target) > 0.25f || me && preview != null) cur = target;
        else cur += (target - cur) * 0.02f;
        SMOOTH.put(p.getUuid(), cur);
        return cur;
    }

    public static final Map<UUID, Float> FAT = new HashMap<>(), SMOOTH = new HashMap<>();

    public static float height(PlayerEntity p) {
        Appearance a = look(p);
        return a == null || !a.custom ? 1f : buildHeight(a);
    }

    private static final Map<String, JsonObject> HEAD = new java.util.LinkedHashMap<>(32, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, JsonObject> e) {
            return size() > 64;
        }
    };

    /** Описание объёмных частей (причёска, головной убор, очки, шея, сумка, юбка) для HairFeature — один объект на внешность. */
    public static JsonObject headJson(Appearance a) {
        String k = a.key() + "|" + a.hat + "|" + a.glasses + "|" + a.neck + "|" + a.bag + "|" + a.hairLen;
        return HEAD.computeIfAbsent(k, x -> com.yompix.novograd.life.LookGen.renderJson(a));
    }

    /** Телефон → «Стиль». */
    public static void phone() {
        MinecraftClient.getInstance().setScreen(new StyleAppScreen(false));
    }

    /** Телефон → «Внешность»: редактор (одежда — только своя, остальное — как в цирюльне). */
    public static void creator() {
        MinecraftClient.getInstance().setScreen(new CreatorScreen(CreatorScreen.Mode.PHONE));
    }

    /** Ждёт ли сервер, что новичок создаст персонажа (откроем, как только закроется заставка/экран). */
    public static boolean creatorPending;

    /** Примерка: идёт ли герой на витрине (подменяет шаг модели). */
    public static boolean walkPreview;
    /** 0.9.7: сейчас рисуется витрина (своя пружина «Физики тела», не как у героя в мире). */
    public static boolean inPreview;
    public static float walkPhase;
}
