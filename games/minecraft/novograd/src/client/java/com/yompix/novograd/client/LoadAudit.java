package com.yompix.novograd.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.yompix.novograd.Novograd;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.life.LifeData;
import com.yompix.novograd.story.Story;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Только разработка: -Dnovograd.autotest=loadaudit[:гл:мис:шаг,…] — время «триггер → первый хороший кадр» у кат-сцен сюжета.
 *
 * <p>Для каждой сцены — как в игре: игрок подходит (стоит в 40 блоках от места старта 3 с — столько обычно идёт подход
 * к метке), потом шагает на метку / к цели шага «доехать» (если сцена первая в миссии или после «доехать»), иначе сцена
 * запускается прямо (после боя, погони — подход не смоделировать). Замер — от прихода пакета сцены до готового кадра
 * (строка «[loadaudit]»), итог — «[loadaudit] итого». Сравнение: -Dnovograd.stage=false — как в 0.6.6.
 */
public final class LoadAudit {
    private LoadAudit() {}

    private record Scene(int c, int m, int s, String how) {}

    private static final List<Scene> LIST = new ArrayList<>();
    private static final List<long[]> RES = new ArrayList<>();
    private static int idx = -1, phase, timer, waitTicks;
    private static boolean active;

    static void begin(String mode) {
        String[] parts = mode.split(":", 2);
        JsonArray chs = Story.root().getAsJsonArray("chapters");
        for (int ci = 0; ci < chs.size(); ci++) {
            JsonArray ms = chs.get(ci).getAsJsonObject().getAsJsonArray("missions");
            for (int mi = 0; mi < ms.size(); mi++) {
                JsonArray st = ms.get(mi).getAsJsonObject().getAsJsonArray("steps");
                for (int si = 0; si < st.size(); si++) {
                    if (!"cutscene".equals(type(st.get(si).getAsJsonObject()))) continue;
                    String key = ci + ":" + mi + ":" + si;
                    if (parts.length > 1 && !("," + parts[1] + ",").contains("," + key + ",")) continue;
                    String how = "direct";
                    boolean onlyTalk = true;
                    for (int k = 0; k < si; k++) {
                        String t = type(st.get(k).getAsJsonObject());
                        if (!t.equals("title") && !t.equals("dialog")) onlyTalk = false;
                    }
                    if (onlyTalk && !(ci == 0 && mi == 0)) how = "marker";
                    else if (si > 0 && type(st.get(si - 1).getAsJsonObject()).equals("goto")) how = "goto";
                    LIST.add(new Scene(ci, mi, si, how));
                }
            }
        }
        Novograd.LOG.info("[loadaudit] сцен: {}, этап (Stage) {}", LIST.size(), com.yompix.novograd.story.Stage.ON);
        active = true;
        next();
    }

    private static String type(JsonObject o) {
        return o.has("type") ? o.get("type").getAsString() : "";
    }

    private static void next() {
        idx++;
        phase = 0;
        timer = 0;
    }

    private static void server(MinecraftClient c, java.util.function.Consumer<ServerPlayerEntity> f) {
        var s = c.getServer();
        if (s != null) s.execute(() -> {
            ServerPlayerEntity p = CineAutotest.player(s);
            if (p != null) f.accept(p);
        });
    }

    static void tick(MinecraftClient c) {
        if (!active || c.player == null) return;
        if (idx >= LIST.size()) {
            active = false;
            summary();
            c.scheduleStop();
            return;
        }
        Scene sc = LIST.get(idx);
        timer++;
        switch (phase) {
            case 0 -> { // состояние сюжета «перед сценой», игрок в 40 блоках от места старта
                if (c.currentScreen != null && !(c.currentScreen instanceof com.yompix.novograd.client.story.CutsceneScreen)) c.setScreen(null);
                server(c, p -> {
                    LifeData d = Life.get(p);
                    d.storyStarted = true;
                    d.storyDone = false;
                    if (sc.c >= 5) d.flags.add("end_cop");
                    d.side = "";
                    d.chapter = sc.c;
                    d.mission = sc.m;
                    d.flags.removeIf(f -> f.startsWith("started:") || f.startsWith("entered:"));
                    d.step = sc.how.equals("marker") ? 0 : sc.how.equals("goto") ? sc.s - 1 : sc.s;
                    Vec3d at = trigger(p, d, sc);
                    if (at != null) {
                        double ax = at.x + 40, az = at.z;
                        p.teleport(p.getServerWorld(), ax, ground(p, ax, az, at.y), az, 0, 0);
                    }
                    if (!sc.how.equals("direct")) Story.reenter(p); // метка или шаг «доехать» — ждут игрока
                    Life.sync(p);
                });
                phase = 1;
                timer = 0;
            }
            case 1 -> { // «подход» 3 с, затем шаг на метку / цель (или прямой старт)
                if (timer < 60) return;
                com.yompix.novograd.client.story.CutsceneScreen.lastLoadMs = -1;
                server(c, p -> {
                    LifeData d = Life.get(p);
                    Vec3d at = trigger(p, d, sc);
                    if (sc.how.equals("direct")) {
                        if (at != null) p.teleport(p.getServerWorld(), at.x, ground(p, at.x, at.z, at.y), at.z, 0, 0);
                        Story.reenter(p);
                    } else if (at != null) {
                        p.getServerWorld().getChunk(net.minecraft.util.math.ChunkSectionPos.getSectionCoord(at.x), net.minecraft.util.math.ChunkSectionPos.getSectionCoord(at.z)); // загрузить: иначе высота −64
                        int top = Story.topY(p.getServerWorld(), (int) Math.floor(at.x), (int) Math.floor(at.z));
                        Novograd.LOG.info(String.format(Locale.ROOT, "[loadaudit] %d:%d:%d метка %.1f %.1f %.1f, поверхность %d, блок %s", sc.c, sc.m, sc.s, at.x, at.y, at.z, top,
                                p.getServerWorld().getBlockState(net.minecraft.util.math.BlockPos.ofFloored(at)).getBlock().getTranslationKey()));
                        p.teleport(p.getServerWorld(), at.x, ground(p, at.x, at.z, at.y), at.z, 0, 0); // как игрок: по земле (на мосту — на настил)
                        if (sc.how.equals("goto")) Story.advance(p); // «доехал» (шаг может требовать машину — не моделируем)
                    }
                });
                phase = 2;
                timer = 0;
                waitTicks = 0;
            }
            case 2 -> { // ждём готовый кадр
                if (c.currentScreen instanceof com.yompix.novograd.client.story.DialogScreen) { // диалог перед сценой — «прочитан»
                    c.setScreen(null);
                    server(c, p -> Story.onDialogDone(p, 0));
                }
                boolean open = c.currentScreen instanceof com.yompix.novograd.client.story.CutsceneScreen;
                long ms = com.yompix.novograd.client.story.CutsceneScreen.lastLoadMs;
                if (open) waitTicks++;
                if (open && ms >= 0) {
                    Novograd.LOG.info(String.format(Locale.ROOT, "[loadaudit] %d:%d:%d (%s) готово за %d мс", sc.c, sc.m, sc.s, sc.how, ms));
                    RES.add(new long[]{sc.c, sc.m, sc.s, ms, sc.how.equals("direct") ? 0 : 1});
                    phase = 3;
                    timer = 0;
                } else if (timer > 400) {
                    Novograd.LOG.warn("[loadaudit] {}:{}:{} ({}) сцена не открылась/не готова за 20 с (открыта={})", sc.c, sc.m, sc.s, sc.how, open);
                    RES.add(new long[]{sc.c, sc.m, sc.s, -1, sc.how.equals("direct") ? 0 : 1});
                    if (open) c.currentScreen.keyPressed(GLFW.GLFW_KEY_SPACE, 0, 0);
                    phase = 4;
                    timer = 0;
                }
            }
            case 3 -> { // посмотреть вход (0.5–1 с) и пропустить
                if (timer < 16) return;
                if (c.currentScreen instanceof com.yompix.novograd.client.story.CutsceneScreen cs) cs.keyPressed(GLFW.GLFW_KEY_SPACE, 0, 0);
                phase = 4;
                timer = 0;
            }
            case 4 -> {
                if (timer < 30) return;
                if (c.currentScreen != null) c.setScreen(null);
                next();
            }
        }
    }

    /** Высота, где стоял бы игрок: верх земли в загруженном участке (не ниже метки −1). */
    private static double ground(ServerPlayerEntity p, double x, double z, double markY) {
        var w = p.getServerWorld();
        w.getChunk(net.minecraft.util.math.ChunkSectionPos.getSectionCoord(x), net.minecraft.util.math.ChunkSectionPos.getSectionCoord(z));
        int top = Story.topY(w, (int) Math.floor(x), (int) Math.floor(z));
        var at = net.minecraft.util.math.BlockPos.ofFloored(x, markY, z);
        // метка на улице под навесом/козырьком (участок полиции): игрок подходит по земле, а не по крыше
        if (w.getBlockState(at).isAir() && w.getBlockState(at.up()).isAir() && !w.getBlockState(at.down()).isAir()) return markY;
        return top <= w.getBottomY() ? markY : top + 1;
    }

    /** Где сцена начинается: метка миссии, цель шага «доехать» или (сцена после боя/погони) — метка миссии. */
    static Vec3d trigger(ServerPlayerEntity p, LifeData d, Scene sc) {
        JsonObject m = Story.root().getAsJsonArray("chapters").get(sc.c).getAsJsonObject().getAsJsonArray("missions").get(sc.m).getAsJsonObject();
        if (sc.how.equals("goto")) {
            JsonObject g = m.getAsJsonArray("steps").get(sc.s - 1).getAsJsonObject();
            Vec3d v = Story.place(p.getServerWorld(), g.get("place").getAsString());
            if (v != null) return v;
        }
        return Story.place(p.getServerWorld(), m.has("start") ? m.get("start").getAsString() : "garage_door");
    }

    private static void summary() {
        long sum = 0, n = 0, worst = 0, fails = 0, sumA = 0, nA = 0;
        StringBuilder b = new StringBuilder();
        for (long[] r : RES) {
            if (r[3] < 0) {
                fails++;
                continue;
            }
            sum += r[3];
            n++;
            worst = Math.max(worst, r[3]);
            if (r[4] == 1) {
                sumA += r[3];
                nA++;
            }
            b.append(r[0]).append(':').append(r[1]).append(':').append(r[2]).append('=').append(r[3]).append(' ');
        }
        Novograd.LOG.info("[loadaudit] итого: сцен {}, среднее {} мс, худшее {} мс, с подходом (метка/доехать) {} сцен — среднее {} мс, не готовы за 20 с: {}, этап={}",
                n, n == 0 ? 0 : sum / n, worst, nA, nA == 0 ? 0 : sumA / nA, fails, com.yompix.novograd.story.Stage.ON);
        Novograd.LOG.info("[loadaudit] по сценам: {}", b);
    }
}
