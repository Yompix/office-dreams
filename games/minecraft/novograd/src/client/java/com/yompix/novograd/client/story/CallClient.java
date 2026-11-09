package com.yompix.novograd.client.story;

import com.yompix.novograd.Novograd;
import com.yompix.novograd.client.NovogradClient;
import com.yompix.novograd.client.ui.Ui;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.InputUtil;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Звонок героя на экране, как в GTA: карточка телефона справа («☎ Ники «Нитро» звонит — Y принять / N сбросить»),
 * мелодия звонка, потом разговор субтитрами внизу (и голосом, если реплика озвучена). Если герой что-то предлагает —
 * вопрос и Y/N, ответ героя, и сервер запускает дело (story/Calls). Игрок при этом может ехать и бежать — экран не открывается.
 */
public final class CallClient {
    private CallClient() {}

    private enum Phase { NONE, RING, TALK, ASK, REPLY, END }

    private static Phase phase = Phase.NONE;
    private static NbtCompound call = new NbtCompound();
    private static final List<NbtCompound> queue = new ArrayList<>();
    private static int line = -1, choice = -1;
    private static long phaseAt, lineAt, lineMs, lastRing;
    private static boolean voiced, prevY, prevN;

    public static void init() {
        ClientTickEvents.START_CLIENT_TICK.register(CallClient::keys);
        ClientTickEvents.END_CLIENT_TICK.register(CallClient::tick);
        HudRenderCallback.EVENT.register((ctx, t) -> render(ctx));
        com.yompix.novograd.client.CallsAutotest.init(); // -Dnovograd.autotest=calls
    }

    /** Пакеты call_* с сервера. */
    public static boolean receive(MinecraftClient mc, String type, NbtCompound n) {
        switch (type) {
            case "call_ring" -> {
                // сюжетный разговор или кат-сцена на экране — сейчас не время, сервер позвонит позже
                if (mc.currentScreen != null && mc.currentScreen.getClass().getPackageName().endsWith(".story") && !n.getBoolean("outgoing")) {
                    NbtCompound b = new NbtCompound();
                    b.putString("id", n.getString("id"));
                    NovogradClient.send("call_busy", b);
                    return true;
                }
                call = n;
                choice = -1;
                if (n.getBoolean("outgoing")) {
                    if (mc.currentScreen != null) mc.setScreen(null);
                    start(Phase.TALK, n.getList("lines", NbtElement.COMPOUND_TYPE));
                    play("phone_pickup", 1f);
                } else {
                    phase = Phase.RING;
                    phaseAt = System.currentTimeMillis();
                    lastRing = 0;
                }
            }
            case "call_stop" -> {
                if (phase == Phase.RING) end();
            }
            case "call_contacts" -> ContactsScreen.data = n;
            default -> {
                return false;
            }
        }
        return true;
    }

    /** Для автотеста (client/CallsAutotest): ответить Y/N и узнать фазу. */
    public static void debugAnswer(boolean yes) {
        answer(yes);
    }

    public static String debugPhase() {
        return phase.name() + " line " + line + "/" + queue.size();
    }

    public static boolean active() {
        return phase != Phase.NONE && phase != Phase.END;
    }

    // ---------------------------------------------------------------- клавиши Y / N (только пока звонит или ждёт ответа)
    private static void keys(MinecraftClient mc) {
        if (mc.player == null || phase != Phase.RING && phase != Phase.ASK) {
            prevY = prevN = false;
            return;
        }
        long win = mc.getWindow().getHandle();
        boolean y = InputUtil.isKeyPressed(win, GLFW.GLFW_KEY_Y), n = InputUtil.isKeyPressed(win, GLFW.GLFW_KEY_N);
        boolean yes = y && !prevY, no = n && !prevN;
        prevY = y;
        prevN = n;
        if (mc.currentScreen != null) return;
        // нажатие ушло в ответ: не даём ему же включить нитро или осмотр оружия
        for (KeyBinding kb : mc.options.allKeys) {
            String k = kb.getBoundKeyTranslationKey();
            if (k.equals("key.keyboard.y") || k.equals("key.keyboard.n")) while (kb.wasPressed()) {}
        }
        if (yes) answer(true);
        else if (no) answer(false);
    }

    private static void answer(boolean yes) {
        if (phase == Phase.RING) {
            NbtCompound n = new NbtCompound();
            n.putString("id", call.getString("id"));
            n.putBoolean("yes", yes);
            NovogradClient.send("call_answer", n);
            if (yes) {
                play("phone_pickup", 1f);
                start(Phase.TALK, call.getList("lines", NbtElement.COMPOUND_TYPE));
            } else end();
        } else if (phase == Phase.ASK) {
            choice = yes ? 0 : 1;
            NbtCompound o = call.getCompound("offer");
            start(Phase.REPLY, o.getList(yes ? "yes_lines" : "no_lines", NbtElement.COMPOUND_TYPE));
        }
    }

    private static void start(Phase p, NbtList lines) {
        phase = p;
        phaseAt = System.currentTimeMillis();
        queue.clear();
        for (int i = 0; i < lines.size(); i++) queue.add(lines.getCompound(i));
        line = -1;
        next();
    }

    private static void next() {
        line++;
        Voice.stop();
        if (line >= queue.size()) {
            if (phase == Phase.TALK && call.contains("offer")) {
                phase = Phase.ASK;
                phaseAt = System.currentTimeMillis();
                return;
            }
            hangUp();
            return;
        }
        NbtCompound l = queue.get(line);
        String v = l.getString("voice");
        MinecraftClient mc = MinecraftClient.getInstance();
        voiced = !v.isEmpty() && mc.getSoundManager().get(Novograd.id("voice." + v)) != null;
        if (voiced) Voice.play(v);
        lineAt = System.currentTimeMillis();
        lineMs = MathHelper.clamp(1400 + l.getString("text").length() * 55L, 2200, 7500);
    }

    private static void hangUp() {
        NbtCompound n = new NbtCompound();
        n.putString("id", call.getString("id"));
        n.putInt("choice", choice);
        NovogradClient.send("call_end", n);
        play("phone_pickup", 0.7f);
        end();
    }

    private static void end() {
        Voice.stop();
        phase = Phase.END;
        phaseAt = System.currentTimeMillis();
        queue.clear();
    }

    private static void tick(MinecraftClient mc) {
        if (mc.player == null) {
            phase = Phase.NONE;
            return;
        }
        long now = System.currentTimeMillis();
        switch (phase) {
            case RING -> {
                if (now - lastRing > 2200) {
                    lastRing = now;
                    play("phone_ring", 1f);
                }
            }
            case TALK, REPLY -> {
                long el = now - lineAt;
                boolean done = voiced ? el > Math.min(1500, lineMs) && !Voice.playing() : el > lineMs; // файла нет — хотя бы успеть прочитать
                if (done) next();
            }
            case ASK -> {
                if (now - phaseAt > 15000) answer(false); // не ответил — «в другой раз»
            }
            case END -> {
                if (now - phaseAt > 1200) phase = Phase.NONE;
            }
            default -> {}
        }
    }

    private static void play(String id, float pitch) {
        MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvent.of(Novograd.id(id)), pitch, 0.9f));
    }

    // ---------------------------------------------------------------- отрисовка
    private static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (phase == Phase.NONE || mc.player == null || mc.options.hudHidden) return;
        TextRenderer tr = mc.textRenderer;
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        long now = System.currentTimeMillis();
        float in = Ui.easeOut(MathHelper.clamp((now - phaseAt) / 260f, 0, 1));
        float out = phase == Phase.END ? 1 - MathHelper.clamp((now - phaseAt) / 600f, 0, 1) : 1;
        int color = 0xFF000000 | call.getInt("color");
        String name = call.getString("name");
        // карточка телефона справа
        int w = 178, h = phase == Phase.ASK ? 64 : 46;
        int x = (int) (sw - 8 - w + (1 - (phase == Phase.RING ? in : 1)) * (w + 12)), y = (int) (sh * 0.30f);
        int a = (int) (out * 255);
        if (a < 8) return;
        ctx.fill(x + 3, y + 3, x + w + 3, y + h + 3, Ui.alpha(0x60000000, out));
        ctx.fill(x, y, x + w, y + h, Ui.alpha(0xEE0C0C0C, out));
        ctx.fill(x, y, x + 2, y + h, Ui.alpha(color, out));
        ctx.fillGradient(x + 2, y, x + w, y + 1, Ui.alpha(Ui.PINK, out), Ui.alpha(Ui.CYAN, out));
        // аватар: круг цвета героя с первой буквой, пока звонит — пульсирует
        float pulse = phase == Phase.RING ? 0.5f + 0.5f * MathHelper.sin(now / 140f) : 0;
        int ax = x + 8, ay = y + 8, as = 30;
        int ring = (int) (pulse * 3);
        ctx.fill(ax - ring, ay - ring, ax + as + ring, ay + as + ring, Ui.alpha(color & 0x00FFFFFF | 0x50000000, out));
        ctx.fill(ax, ay, ax + as, ay + as, Ui.alpha(color, out));
        String initial = name.isEmpty() ? "?" : name.substring(0, 1);
        letter(ctx, tr, initial, ax + as / 2f, ay + 8, Ui.alpha(0xFFFFFFFF, out));
        int tx = ax + as + 8;
        ctx.drawText(tr, name, tx, y + 8, Ui.alpha(0xFFFFFFFF, out), true);
        String status = switch (phase) {
            case RING -> "☎ звонит" + ".".repeat((int) (now / 400 % 4));
            case TALK, REPLY -> "☎ на линии " + clock(now - phaseAt);
            case ASK -> "☎ ждёт ответа";
            case END -> "звонок завершён";
            default -> "";
        };
        ctx.drawText(tr, status, tx, y + 19, Ui.alpha(phase == Phase.RING ? Ui.MONEY : 0xFFB9B7B1, out), false);
        if (phase == Phase.RING) {
            int bx = tx, by = y + 31;
            bx = key(ctx, tr, "Y", "принять", bx, by, Ui.MONEY, out);
            key(ctx, tr, "N", "сбросить", bx + 6, by, Ui.RED, out);
        } else if (phase == Phase.ASK) {
            NbtCompound o = call.getCompound("offer");
            List<OrderedText> q = tr.wrapLines(Text.literal(o.getString("q")), w - 16);
            int qy = y + 31;
            for (int i = 0; i < Math.min(2, q.size()); i++) ctx.drawText(tr, q.get(i), x + 8, qy + i * 9, Ui.alpha(Ui.GOLD, out), true);
            int by = y + h - 13;
            int bx = key(ctx, tr, "Y", o.getString("yes"), x + 8, by, Ui.MONEY, out);
            key(ctx, tr, "N", o.getString("no"), bx + 6, by, Ui.RED, out);
            float left = 1 - MathHelper.clamp((now - phaseAt) / 15000f, 0, 1);
            ctx.fill(x + 2, y + h - 1, x + 2 + (int) ((w - 2) * left), y + h, Ui.alpha(Ui.GOLD, out));
        }
        // субтитры разговора внизу экрана
        if ((phase == Phase.TALK || phase == Phase.REPLY) && line >= 0 && line < queue.size()) {
            NbtCompound l = queue.get(line);
            String who = l.getString("name") + ": ";
            int maxW = Math.min(420, sw - 40);
            List<OrderedText> rows = tr.wrapLines(Text.literal(l.getString("text")), maxW - tr.getWidth(who));
            int rowsH = rows.size() * 10;
            int by = (int) (sh * 0.64f) - rowsH; // выше чата и подсказок внизу экрана
            int bw = Math.min(maxW, tr.getWidth(who) + rows.stream().mapToInt(tr::getWidth).max().orElse(0)) + 12;
            int bx = sw / 2 - bw / 2;
            ctx.fill(bx, by - 4, bx + bw, by + rowsH + 2, 0x90000000);
            ctx.drawText(tr, who, bx + 6, by, 0xFF000000 | l.getInt("color"), true);
            for (int i = 0; i < rows.size(); i++) ctx.drawText(tr, rows.get(i), bx + 6 + tr.getWidth(who), by + i * 10, 0xFFFFFFFF, true);
            ctx.drawText(tr, "☎", bx - 10, by, 0xFF62E57E, true);
        }
    }

    /** Крупная буква на аватаре (белая, с тенью). */
    public static void letter(DrawContext ctx, TextRenderer tr, String s, float cx, float y, int color) {
        ctx.getMatrices().push();
        ctx.getMatrices().translate(cx, y, 0);
        ctx.getMatrices().scale(2f, 2f, 1);
        ctx.drawText(tr, s, -tr.getWidth(s) / 2, 0, color, true);
        ctx.getMatrices().pop();
    }

    private static int key(DrawContext ctx, TextRenderer tr, String k, String label, int x, int y, int color, float out) {
        int kw = tr.getWidth(k) + 6;
        ctx.fill(x, y - 1, x + kw, y + 10, Ui.alpha(color, out));
        ctx.drawText(tr, k, x + 3, y + 1, Ui.alpha(0xFF0C0C0C, out), false);
        ctx.drawText(tr, label, x + kw + 3, y + 1, Ui.alpha(0xFFFFFFFF, out), true);
        return x + kw + 3 + tr.getWidth(label);
    }

    private static String clock(long ms) {
        long s = ms / 1000;
        return String.format("%d:%02d", s / 60, s % 60);
    }
}
