package com.yompix.novograd.client;

import com.yompix.novograd.Novograd;
import com.yompix.novograd.entity.CarEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.AbstractSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.TickableSoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.random.Random;

/**
 * Радио в машине: свои станции Новограда. Садишься — играет последняя станция, вышел — замолкает.
 * R — следующая станция, «-»/«=» — тише/громче (и в «Настройках игры»). Громкость меняется на лету.
 */
public final class CarRadio {
    private CarRadio() {}

    /** Название, звук, жанр, цвет. */
    public static final String[][] STATIONS = {
            {"НЕОН 88 FM", "radio.neon88", "синтвейв ночных улиц", "FF4FD8"},
            {"БИТ УЛИЦ", "radio.beat_ulic", "хип-хоп и трэп", "FFD54F"},
            {"НОЧНОЙ ПОРТ", "radio.nochnoy_port", "лоу-фай для долгой дороги", "7FD3FF"},
            {"ТРАССА 128", "radio.trassa128", "электро для погонь", "7CFF8A"},
            {"РОК-ВОЛНА", "radio.rock_volna", "гитарный рок", "FF5252"},
            {"ДЖАЗ «ПОЛНОЧЬ»", "radio.jazz_polnoch", "свинг и саксофон", "FFB74D"},
            {"ПАЛЬМА FM", "radio.palma_fm", "регги Ривьеры", "B2FF59"},
            {"КЛАССИКА", "radio.klassika", "струнные и рояль", "E1BEE7"},
            {"КАНТРИ-РОУД", "radio.country_road", "банджо и дорога", "D7A86E"},
            {"ТЕХНО-ПОДВАЛ", "radio.tekhno_podval", "кислотное техно", "18FFFF"},
    };
    /** Треков на станции (radio.<станция>_0..2) — играют по очереди без повтора подряд. */
    private static final int TRACKS = 3;
    private static final int[] lastTrack = new int[16];

    private static final class Track extends AbstractSoundInstance implements TickableSoundInstance {
        boolean done;

        Track(String event) {
            super(Novograd.id(event), SoundCategory.RECORDS, Random.create());
            repeat = false;
            relative = true;
            attenuationType = SoundInstance.AttenuationType.NONE;
            volume = NovogradConfig.get().radioVolume;
        }

        @Override
        public boolean isDone() {
            return done;
        }

        @Override
        public void tick() {
            volume = Math.max(0.0001f, NovogradConfig.get().radioVolume);
        }

        @Override
        public boolean shouldAlwaysPlay() {
            return true;
        }
    }

    private static Track playing;
    /** Радио в наушниках (телефон → «Радио»): играет и пешком. */
    public static boolean pocket;
    private static int playingStation = -2;
    private static long shownAt = -100000;
    private static String toast = "";

    public static void tick(MinecraftClient mc) {
        boolean inCar = mc.player != null && mc.player.getVehicle() instanceof CarEntity car && !car.isScooter();
        // главное меню: вместо музыки Minecraft — наше радио
        boolean menu = mc.world == null && mc.currentScreen instanceof com.yompix.novograd.client.menu.LifeMenuScreen;
        boolean cut = mc.currentScreen instanceof com.yompix.novograd.client.story.CutsceneScreen;
        int want = cut ? -1 : inCar || pocket && mc.player != null ? NovogradConfig.get().radioStation : menu ? 0 : -1;
        if (want >= STATIONS.length) want = 0;
        boolean ended = playing != null && !mc.getSoundManager().isPlaying(playing) && System.currentTimeMillis() / 50 % 20 == 0;
        if (want != playingStation || ended && want >= 0) {
            stop(mc);
            if (want >= 0) {
                // следующий трек станции: не тот, что играл последним
                int tr = (lastTrack[want] + 1 + (int) (Math.random() * (TRACKS - 1))) % TRACKS;
                lastTrack[want] = tr;
                playing = new Track(STATIONS[want][1] + "_" + tr);
                if (ended && mc.world != null && mc.world.random.nextInt(3) == 0) { // иногда ведущий между песнями
                    String id = "voice.radio_" + STATIONS[want][1].substring("radio.".length());
                    mc.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(
                            net.minecraft.sound.SoundEvent.of(Novograd.id(id)), 1f, Math.min(1f, NovogradConfig.get().radioVolume + 0.3f)));
                }
                mc.getSoundManager().play(playing);
                if (mc.player != null && (playingStation == -2 || playingStation != want)) {
                    show(STATIONS[want][0]);
                    // голос ведущего: «Неон 88 FM…»
                    String id = "voice.radio_" + STATIONS[want][1].substring("radio.".length());
                    mc.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(
                            net.minecraft.sound.SoundEvent.of(Novograd.id(id)), 1f, Math.min(1f, NovogradConfig.get().radioVolume + 0.3f)));
                }
            }
            playingStation = want;
        }
    }

    private static void stop(MinecraftClient mc) {
        if (playing != null) {
            playing.done = true;
            mc.getSoundManager().stop(playing);
        }
        playing = null;
    }

    public static void next(MinecraftClient mc) {
        if (mc.player == null || !(mc.player.getVehicle() instanceof CarEntity)) return; // пешком R — перезарядка, станции — в телефоне
        var c = NovogradConfig.get();
        boolean back = net.minecraft.client.gui.screen.Screen.hasShiftDown(); // Shift+R — предыдущая станция
        int n = STATIONS.length;
        c.radioStation = back ? (c.radioStation < 0 ? n - 1 : c.radioStation - 1) : c.radioStation + 1 >= n ? -1 : c.radioStation + 1;
        if (c.radioStation < 0) show("РАДИО ВЫКЛ");
        NovogradConfig.save();
    }

    public static void volume(MinecraftClient mc, float delta) {
        var c = NovogradConfig.get();
        c.radioVolume = Math.max(0, Math.min(1, Math.round((c.radioVolume + delta) * 10) / 10f));
        NovogradConfig.save();
        show("ГРОМКОСТЬ " + Math.round(c.radioVolume * 100) + "%");
    }

    /** Телефон → «Радио»: включить станцию в наушниках (i < 0 — выключить). */
    public static void pocket(int i) {
        var c = NovogradConfig.get();
        if (i < 0) {
            pocket = false;
            show("РАДИО ВЫКЛ");
            return;
        }
        pocket = true;
        c.radioStation = i;
        NovogradConfig.save();
    }

    public static boolean isPlaying(int i) {
        return playing != null && playingStation == i;
    }

    private static void show(String s) {
        toast = s;
        shownAt = System.currentTimeMillis();
    }

    /** Название станции сверху по центру, как в машине в криминальных боевиках. */
    public static void render(DrawContext ctx) {
        long age = System.currentTimeMillis() - shownAt;
        if (age > 3200) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;
        float a = age < 200 ? age / 200f : age > 2600 ? (3200 - age) / 600f : 1;
        int alpha = (int) (a * 255) << 24;
        int cx = ctx.getScaledWindowWidth() / 2, y = 44; // ниже компаса
        float K = NovogradConfig.hud();
        ctx.getMatrices().push();
        ctx.getMatrices().translate(cx, y, 0);
        ctx.getMatrices().scale(K * 0.8f, K * 0.8f, 1);
        ctx.getMatrices().translate(-cx, -y, 0);
        int st = NovogradConfig.get().radioStation;
        int col = 0xFFFFFF;
        String genre = "";
        if (toast.equals(st >= 0 && st < STATIONS.length ? STATIONS[st][0] : "")) {
            col = Integer.parseInt(STATIONS[st][3], 16);
            genre = STATIONS[st][2];
        }
        ctx.getMatrices().push();
        ctx.getMatrices().translate(cx, y, 0);
        ctx.getMatrices().scale(2f, 2f, 1);
        int w = tr.getWidth(toast);
        ctx.drawText(tr, toast, -w / 2 + 1, 1, alpha, false);
        ctx.drawText(tr, toast, -w / 2, 0, alpha | col, false);
        ctx.getMatrices().pop();
        if (!genre.isEmpty()) {
            int gw = tr.getWidth(genre);
            ctx.drawText(tr, genre, cx - gw / 2, y + 22, alpha | 0xD0D0D0, true);
        }
        // эквалайзер-полоски
        if (playing != null && !genre.isEmpty()) {
            long t = System.currentTimeMillis();
            for (int i = 0; i < 9; i++) {
                int h = 2 + (int) (Math.abs(Math.sin(t / 140.0 + i * 1.7) * Math.cos(t / 330.0 + i)) * 8);
                ctx.fill(cx - 27 + i * 6, y + 44 - h, cx - 23 + i * 6, y + 44, alpha | col);
            }
        }
        ctx.getMatrices().pop();
    }
}
