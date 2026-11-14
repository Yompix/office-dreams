package com.yompix.glukhoman.client;

import com.yompix.glukhoman.GContent;
import com.yompix.glukhoman.Glukhoman;
import com.yompix.glukhoman.client.screen.ChoiceScreen;
import com.yompix.glukhoman.client.screen.DifficultyScreen;
import com.yompix.glukhoman.client.screen.SettingsScreen;
import com.yompix.glukhoman.entity.PoleEntity;
import com.yompix.glukhoman.net.Net;
import com.yompix.glukhoman.world.Terrain;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.ingame.BookScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.util.InputUtil;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Клиент Глухомани: приём сообщений сервера, клавиши, туман и цвет неба, звуковой фон, сердцебиение, галлюцинации. */
public class GlukhomanClient implements ClientModInitializer {
    // ---- состояние, присланное сервером ----
    public static boolean inWorld;
    public static int chapter, difficulty = 1;
    public static String title = "", objective = "", step = "", ending = "";
    public static float sanity = 100, shownSanity = 100;
    public static boolean lightOn, chase;
    public static long objectiveChanged;
    // ---- эффекты ----
    public static String cardA = "", cardB = "", cardC = "";
    public static int cardT;
    public static final List<String[]> SUBS = new ArrayList<>(); // who, text, ticksLeft
    public static int fadeT, fadeTotal, fadeColor;
    public static float shake;
    public static int joltT, lostT, creditsT = -1, hintT, hushT, crashT = -1;
    public static Vec3d hint;
    // ---- туман (читает FogMixin) ----
    public static float fogStart = 30, fogEnd = 90, fogR = 0.3f, fogG = 0.32f, fogB = 0.38f;

    static KeyBinding FLASH, HINT;
    static int stepsArmed, stepsToPlay, stepsDelay, heartT, halluCd = 600, halluLife;
    static PoleEntity hallu;
    static boolean wasMoving, askedChoose;
    static int chooseT;
    static double savedGamma = -1;
    static final Random RND = Random.create();
    static final Map<String, Loop> LOOPS = new HashMap<>();

    @Override
    public void onInitializeClient() {
        com.yompix.glukhoman.client.render.GeoRenderers.init();
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.END.register(ctx -> FearFx.render(ctx.tickCounter().getTickDelta(false)));
        net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlocks(net.minecraft.client.render.RenderLayer.getCutout(), GContent.NOTE, GContent.CHALK_MARK);
        FLASH = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.glukhoman.flashlight", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.glukhoman"));
        HINT = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.glukhoman.hint", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H, "key.categories.glukhoman"));
        ClientPlayNetworking.registerGlobalReceiver(Net.S2C.ID, (p, ctx) -> ctx.client().execute(() -> onMessage(p.data())));
        HudRenderCallback.EVENT.register(Hud::render);
        ClientTickEvents.END_CLIENT_TICK.register(GlukhomanClient::tick);
        ClientPlayConnectionEvents.JOIN.register((h, s, c) -> sendPrefs());
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> leave(c));
        ScreenEvents.AFTER_INIT.register((c, screen, w, h) -> {
            if (screen instanceof GameMenuScreen && inWorld)
                Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal("Глухомань"), b -> c.setScreen(new SettingsScreen(screen)))
                        .dimensions(4, 4, 96, 20).build());
        });
        Autotest.init();
    }

    static void leave(MinecraftClient c) {
        c.execute(() -> {
            Cine.stop();
            FearFx.reset();
        });
        inWorld = false;
        askedChoose = false;
        chapter = 0;
        chase = false;
        creditsT = -1;
        SUBS.clear();
        for (Loop l : LOOPS.values()) c.getSoundManager().stop(l);
        LOOPS.clear();
        if (savedGamma >= 0) {
            c.options.getGamma().setValue(savedGamma);
            savedGamma = -1;
        }
    }

    public static void sendPrefs() {
        NbtCompound m = Net.msg("prefs");
        m.putBoolean("noScares", GConfig.get().fewerScares);
        send(m);
    }

    public static void send(NbtCompound m) {
        if (ClientPlayNetworking.canSend(Net.C2S.ID)) ClientPlayNetworking.send(new Net.C2S(m));
    }

    static SoundEvent sound(String id) {
        return Registries.SOUND_EVENT.get(Glukhoman.id(id));
    }

    // ===================== сообщения сервера =====================
    static void onMessage(NbtCompound m) {
        MinecraftClient c = MinecraftClient.getInstance();
        inWorld = true;
        switch (m.getString("t")) {
            case "hud" -> {
                chapter = m.getInt("ch");
                title = m.getString("title");
                String obj = m.getString("obj");
                if (!obj.equals(objective)) objectiveChanged = c.world == null ? 0 : c.world.getTime();
                objective = obj;
                step = m.getString("step");
                ending = m.getString("ending");
            }
            case "sanity" -> sanity = m.getFloat("v");
            case "difficulty" -> difficulty = m.getInt("d");
            case "light" -> lightOn = m.getBoolean("on");
            case "title" -> {
                cardA = m.getString("a");
                cardB = m.getString("b");
                cardC = m.getString("c");
                cardT = 160;
            }
            case "voice" -> {
                SoundEvent e = m.getString("id").isEmpty() ? null : sound("voice." + m.getString("id"));
                if (e != null) {
                    if (m.getBoolean("global") || !m.contains("x"))
                        c.getSoundManager().play(PositionedSoundInstance.master(e, 1f, 1f));
                    else
                        c.getSoundManager().play(new PositionedSoundInstance(e, SoundCategory.VOICE, 1.6f, 1f, RND, m.getInt("x") + 0.5, m.getInt("y") + 0.5, m.getInt("z") + 0.5));
                }
                if (GConfig.get().subtitles) {
                    SUBS.add(new String[]{m.getString("who"), m.getString("text"), Integer.toString(m.getInt("dur"))});
                    while (SUBS.size() > 3) SUBS.remove(0);
                }
            }
            case "snd" -> {
                String id = m.getString("id");
                SoundEvent e = sound(id);
                if (e != null) c.getSoundManager().play(new PositionedSoundInstance(e, SoundCategory.AMBIENT, m.getFloat("v") * 1.5f, m.getFloat("p"), RND,
                        m.getDouble("x"), m.getDouble("y"), m.getDouble("z")));
            }
            case "steps" -> stepsArmed = 240;
            case "note" -> {
                List<Text> pages = new ArrayList<>();
                NbtList l = m.getList("pages", 8);
                for (int i = 0; i < l.size(); i++) pages.add(Text.literal((i == 0 ? "§l" + m.getString("title") + "§r\n\n" : "") + l.getString(i)));
                c.setScreen(new BookScreen(new BookScreen.Contents(pages)));
            }
            case "choose" -> {
                askedChoose = true;
                c.setScreen(new DifficultyScreen());
            }
            case "choice" -> c.setScreen(new ChoiceScreen());
            case "credits" -> creditsT = 0;
            case "hint" -> {
                hint = new Vec3d(m.getInt("x") + 0.5, m.getInt("y"), m.getInt("z") + 0.5);
                hintT = 100;
            }
            case "fx" -> fx(m.getString("k"), m.getInt("t"));
            case "cut" -> Cine.play(m.getString("id"));
            case "cutend" -> Cine.stop();
            case "hush" -> hushT = m.getInt("t");
            case "jumpscare" -> Jumpscare.start(m.getString("k"));
            case "fakecrash" -> {
                if (GConfig.get().fakeCrash && !GConfig.get().fewerScares) crashT = 0;
            }
            default -> {
            }
        }
    }

    static void fx(String k, int t) {
        boolean calm = GConfig.get().noFlashing;
        switch (k) {
            case "fade" -> fade(t, 0xFF000000);
            case "white" -> fade(t, calm ? 0xFFB8C0C8 : 0xFFFFFFFF);
            case "black" -> fade(t, 0xFF000000);
            case "shake" -> shake = Math.max(shake, 1.2f);
            case "jolt" -> {
                shake = Math.max(shake, calm ? 0.8f : 2.4f);
                if (!calm) joltT = 8;
            }
            case "panic" -> shake = Math.max(shake, 1.5f);
            case "lost" -> lostT = t;
            case "chase" -> chase = true;
            case "chase_end" -> chase = false;
            default -> {
            }
        }
    }

    static void fade(int t, int color) {
        fadeTotal = fadeT = Math.max(20, t);
        fadeColor = color;
    }

    // ===================== такт клиента =====================
    static void tick(MinecraftClient c) {
        if (c.player == null || c.world == null) return;
        while (FLASH.wasPressed()) if (inWorld) send(Net.msg("light"));
        while (HINT.wasPressed()) {
            if (inWorld && GConfig.get().hints) send(Net.msg("hint"));
            else if (inWorld) c.player.sendMessage(Text.literal("Подсказки выключены в настройках"), true);
        }
        if (Cine.active) Cine.clockTicks++;
        if (!inWorld) return;
        // закрыл выбор сложности, а история не началась — через 5 с спросим снова
        if (askedChoose && chapter == 0 && c.currentScreen == null && ++chooseT > 100) {
            chooseT = 0;
            c.setScreen(new DifficultyScreen());
        }
        if (chapter > 0) askedChoose = false;
        // тёмные ночи: своя яркость на время игры в Глухомани
        double g = new double[]{0.0, 0.35, 0.7}[Math.max(0, Math.min(2, GConfig.get().brightness))];
        if (Cine.active) g = Math.max(g, 0.8);   // «экспозиция» кино: в кадре видно, но темнота остаётся
        if (savedGamma < 0) savedGamma = c.options.getGamma().getValue();
        if (Math.abs(c.options.getGamma().getValue() - g) > 0.001) c.options.getGamma().setValue(g);

        shownSanity += (sanity - shownSanity) * 0.08f;
        if (cardT > 0) cardT--;
        if (fadeT > 0) fadeT--;
        if (joltT > 0) joltT--;
        if (lostT > 0) lostT--;
        if (hintT > 0) hintT--;
        if (hushT > 0) hushT--;
        if (crashT >= 0 && ++crashT > 130) crashT = -1;
        shake *= 0.9f;
        if (creditsT >= 0 && ++creditsT > 900) creditsT = -1;
        for (String[] s : SUBS) s[2] = Integer.toString(Integer.parseInt(s[2]) - 1);
        SUBS.removeIf(s -> Integer.parseInt(s[2]) <= 0);
        fog(c);
        ambience(c);
        heartbeat(c);
        mimicSteps(c);
        hallucinate(c);
    }

    static void fog(MinecraftClient c) {
        float s, e, r, gg, b;
        double y = c.player.getY();
        boolean night = c.world.getTimeOfDay() % 24000 > 12800 && c.world.getTimeOfDay() % 24000 < 23200;
        if (ending.equals("A")) { s = 40; e = 180; r = 0.62f; gg = 0.66f; b = 0.72f; }
        else if (ending.equals("B")) { s = 8; e = 48; r = 0.12f; gg = 0.05f; b = 0.05f; }
        else if (y < 52) { s = 2; e = 22; r = 0.01f; gg = 0.01f; b = 0.012f; }
        else switch (chapter) {
            case 0, 1 -> { s = night ? 10 : 22; e = night ? 52 : 80; r = night ? 0.06f : 0.30f; gg = night ? 0.07f : 0.30f; b = night ? 0.10f : 0.36f; }
            case 2 -> { s = 8; e = 46; r = 0.04f; gg = 0.05f; b = 0.08f; }
            case 3 -> { boolean st = Terrain.zone((int) c.player.getX(), (int) c.player.getZ()).equals("Метеостанция"); s = st ? 6 : 2; e = st ? 48 : 28; r = 0.16f; gg = 0.17f; b = 0.19f; }
            case 5 -> { s = 6; e = 40; r = 0.07f; gg = 0.03f; b = 0.09f; }
            default -> { s = 6; e = 38; r = 0.05f; gg = 0.05f; b = 0.06f; }
        }
        if (chase) { s *= 0.7f; e *= 0.8f; }
        if (lostT > 0) { s = 0.5f; e = 10; r = 0.55f; gg = 0.57f; b = 0.6f; }
        if (sanity < 30) e *= 0.6f + sanity / 75f;
        float k = 0.04f;
        fogStart += (s - fogStart) * k;
        fogEnd += (e - fogEnd) * k;
        fogR += (r - fogR) * k;
        fogG += (gg - fogG) * k;
        fogB += (b - fogB) * k;
    }

    // ---------- звуковой фон: петли с плавным переходом ----------
    static class Loop extends MovingSoundInstance {
        float target;

        Loop(SoundEvent e) {
            super(e, SoundCategory.AMBIENT, SoundInstance.createRandom());
            repeat = true;
            repeatDelay = 0;
            volume = 0.01f;
            relative = true;
            attenuationType = SoundInstance.AttenuationType.NONE;
        }

        @Override
        public void tick() {
            volume += (target - volume) * 0.04f;
            if (target <= 0.001f && volume < 0.01f) setDone();
        }
    }

    static void ambience(MinecraftClient c) {
        if (c.world.getTime() % 10 != 0) return;
        Map<String, Float> want = new HashMap<>();
        double x = c.player.getX(), y = c.player.getY(), z = c.player.getZ();
        if (ending.equals("A")) want.put("amb.relief", 0.5f);
        else {
            if (y < 52) want.put(x > -2 && x < 30 && z < -240 && z > -282 ? "amb.bunker" : "amb.mine", 0.8f);
            else {
                String zone = Terrain.zone((int) x, (int) z);
                want.put(zone.equals("Посёлок") ? "amb.village" : zone.equals("Метеостанция") ? "amb.station" : "amb.forest", 0.7f);
            }
            if (chase) want.put("amb.chase", 0.75f);
            if (sanity < 45) want.put("amb.dread", (45 - sanity) / 45f * 0.8f);
        }
        if (hushT > 0 || crashT >= 0) want.replaceAll((k, v) -> 0.001f);   // тишина перед скримером
        for (var e : want.entrySet()) {
            Loop l = LOOPS.get(e.getKey());
            if (l == null || l.isDone()) {
                SoundEvent ev = sound(e.getKey());
                if (ev == null) continue;
                l = new Loop(ev);
                LOOPS.put(e.getKey(), l);
                c.getSoundManager().play(l);
            }
            l.target = e.getValue();
        }
        for (var e : LOOPS.entrySet()) if (!want.containsKey(e.getKey())) e.getValue().target = 0;
        LOOPS.values().removeIf(MovingSoundInstance::isDone);
    }

    static void heartbeat(MinecraftClient c) {
        if (!GConfig.get().heartbeat || sanity >= 40 || !ending.isEmpty()) return;
        int period = 12 + (int) (sanity * 0.6f);
        if (++heartT >= period) {
            heartT = 0;
            SoundEvent e = sound("heartbeat");
            if (e != null) c.getSoundManager().play(PositionedSoundInstance.master(e, 1f, 0.5f + (40 - sanity) / 80f));
        }
    }

    /** Шаги, которые повторяют твои: остановился — а сзади ещё два шага. */
    static void mimicSteps(MinecraftClient c) {
        boolean moving = c.player.getVelocity().horizontalLengthSquared() > 0.002;
        if (stepsArmed > 0) {
            stepsArmed--;
            if (wasMoving && !moving) {
                stepsToPlay = 2 + RND.nextInt(2);
                stepsDelay = 6;
                stepsArmed = 0;
            }
        }
        wasMoving = moving;
        if (stepsToPlay > 0 && --stepsDelay <= 0) {
            stepsToPlay--;
            stepsDelay = 8;
            Vec3d back = c.player.getPos().subtract(c.player.getRotationVec(1f).multiply(3.5, 0, 3.5));
            SoundEvent e = sound("steps_near");
            if (e != null) c.getSoundManager().play(new PositionedSoundInstance(e, SoundCategory.AMBIENT, 0.9f, 0.9f, RND, back.x, back.y, back.z));
        }
    }

    /** Галлюцинация на низком рассудке: фигура краем глаза, видна только тебе. */
    static void hallucinate(MinecraftClient c) {
        if (hallu != null) {
            Vec3d to = hallu.getPos().add(0, 2, 0).subtract(c.player.getEyePos());
            if (--halluLife <= 0 || to.normalize().dotProduct(c.player.getRotationVec(1f)) > 0.8) {
                hallu.discard();
                c.world.removeEntity(hallu.getId(), net.minecraft.entity.Entity.RemovalReason.DISCARDED);
                hallu = null;
            }
            return;
        }
        if (GConfig.get().fewerScares || sanity > 25 || !ending.isEmpty() || --halluCd > 0) return;
        halluCd = 500 + RND.nextInt(700);
        PoleEntity e = GContent.POLE.create(c.world);
        if (e == null) return;
        Vec3d look = c.player.getRotationVec(1f).multiply(1, 0, 1).normalize();
        double ang = Math.toRadians(70) * (RND.nextBoolean() ? 1 : -1);
        Vec3d dir = new Vec3d(look.x * Math.cos(ang) - look.z * Math.sin(ang), 0, look.x * Math.sin(ang) + look.z * Math.cos(ang));
        Vec3d at = c.player.getPos().add(dir.multiply(14));
        int top = c.world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, MathHelper.floor(at.x), MathHelper.floor(at.z));
        e.setId(-777000 - RND.nextInt(1000));
        e.refreshPositionAndAngles(at.x, top, at.z, (float) Math.toDegrees(Math.atan2(-dir.x, dir.z)) + 180, 0);
        c.world.addEntity(e);
        hallu = e;
        halluLife = 120;
    }
}
