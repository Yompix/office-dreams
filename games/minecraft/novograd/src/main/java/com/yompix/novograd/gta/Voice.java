package com.yompix.novograd.gta;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.Novograd;
import com.yompix.novograd.life.Admin;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.life.LifeNet;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 0.9.10: голос между игроками через Simple Voice Chat (мягкая зависимость: без него всё работает, только без голоса).
 * Рядом — обычный голос по расстоянию (сам мод голоса). Сверху — каналы Новограда:
 * <ul>
 *   <li>звонок по телефону — закрытая голосовая группа на двоих (телефон → «Рация»: позвонить, ответить, положить трубку);</li>
 *   <li>рация службы — для всех на смене полиции, пожарных и скорой (gta/Duty), включается сама на смене;</li>
 *   <li>рация банды — для членов одной банды (gta/CrimeCareer);</li>
 *   <li>в тюрьме микрофон выключен (config/novograd-server.json: voiceJailMute).</li>
 * </ul>
 * Группы создаёт voice/NovogradVoicePlugin (единственное место, где есть классы Simple Voice Chat) — скрытые, с паролем,
 * типа OPEN: рядом стоящие слышат, что ты говоришь в рацию, как в жизни. Значок над головой говорящего рисует сам мод голоса.
 */
public final class Voice {
    private Voice() {}

    /** Реализация на API Simple Voice Chat; null — мода голоса нет. */
    public interface Backend {
        boolean installed(UUID player);

        /** Поставить игрока в группу key ("" — без группы, голос рядом). */
        void group(UUID player, String key, String name);
    }

    public static volatile Backend backend;
    /** Кому нельзя говорить (тюрьма). Читается из потока голоса. */
    public static final Set<UUID> MUTED = ConcurrentHashMap.newKeySet();

    static final String[] CHANNELS = {"near", "duty", "gang"};
    private static final Map<UUID, String> CHANNEL = new HashMap<>();
    private static final Map<UUID, UUID> CALL = new HashMap<>(), RING = new HashMap<>(); // кому звонят → кто
    private static final Map<UUID, Long> RING_AT = new HashMap<>();
    private static final Map<UUID, String> LAST = new HashMap<>();

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(Voice::tick);
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((d, reg, env) -> d.register(
                net.minecraft.server.command.CommandManager.literal("call")
                        .then(net.minecraft.server.command.CommandManager.literal("accept").executes(c -> {
                            answer(c.getSource().getPlayerOrThrow());
                            return 1;
                        }))
                        .then(net.minecraft.server.command.CommandManager.literal("decline").executes(c -> {
                            hangup(c.getSource().getPlayerOrThrow());
                            return 1;
                        }))));
    }

    public static boolean handle(ServerPlayerEntity p, String type, NbtCompound n) {
        switch (type) {
            case "voice_state" -> send(p);
            case "voice_channel" -> {
                String ch = n.getString("ch");
                if (java.util.Arrays.asList(CHANNELS).contains(ch)) CHANNEL.put(p.getUuid(), ch);
                update(p);
                send(p);
            }
            case "voice_call" -> call(p, n.getString("to"));
            case "voice_answer" -> answer(p);
            case "voice_hangup" -> hangup(p);
            default -> {
                return false;
            }
        }
        return true;
    }

    static void call(ServerPlayerEntity p, String toName) {
        ServerPlayerEntity t = p.getServer().getPlayerManager().getPlayer(toName);
        if (t == null || t == p || CALL.containsKey(p.getUuid())) return;
        if (Prison.inside(p)) {
            FunUtil.hint(p, "Телефон изъят", Formatting.GRAY);
            return;
        }
        if (CALL.containsKey(t.getUuid()) || RING.containsKey(t.getUuid())) {
            FunUtil.hint(p, toName + ": занято", Formatting.GRAY);
            return;
        }
        RING.put(t.getUuid(), p.getUuid());
        RING_AT.put(t.getUuid(), (long) p.getServer().getTicks());
        FunUtil.hint(p, "Звоним: " + toName + "…", Formatting.AQUA);
        t.sendMessage(Text.literal("☎ Звонит " + p.getName().getString() + "  ").formatted(Formatting.AQUA)
                .append(Text.literal("[ОТВЕТИТЬ]").setStyle(Style.EMPTY.withColor(Formatting.GREEN).withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/call accept"))))
                .append(Text.literal("  "))
                .append(Text.literal("[СБРОС]").setStyle(Style.EMPTY.withColor(Formatting.GRAY)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/call decline")))), false);
        Life.sound(t, ModContent.PHONE_NOTIFY, 1f);
        send(t);
        send(p);
    }

    static void answer(ServerPlayerEntity p) {
        UUID from = RING.remove(p.getUuid());
        RING_AT.remove(p.getUuid());
        ServerPlayerEntity f = from == null ? null : p.getServer().getPlayerManager().getPlayer(from);
        if (f == null) {
            FunUtil.hint(p, "Звонков нет", Formatting.GRAY);
            return;
        }
        CALL.put(p.getUuid(), from);
        CALL.put(from, p.getUuid());
        for (ServerPlayerEntity x : new ServerPlayerEntity[]{p, f}) {
            ServerPlayerEntity o = x == p ? f : p;
            FunUtil.hint(x, "☎ На связи: " + o.getName().getString() + (backend != null && !backend.installed(o.getUuid()) ? " (у собеседника нет мода голоса)" : ""), Formatting.GREEN);
            update(x);
            send(x);
        }
        Novograd.LOG.info("[голос] звонок {} ↔ {}", f.getName().getString(), p.getName().getString());
    }

    static void hangup(ServerPlayerEntity p) {
        UUID from = RING.remove(p.getUuid());
        RING_AT.remove(p.getUuid());
        MinecraftServer s = p.getServer();
        if (from != null) {
            ServerPlayerEntity f = s.getPlayerManager().getPlayer(from);
            if (f != null) {
                FunUtil.hint(f, p.getName().getString() + " сбросил(а)", Formatting.GRAY);
                send(f);
            }
        }
        // свой исходящий, на который ещё не ответили
        RING.entrySet().removeIf(e -> e.getValue().equals(p.getUuid()));
        UUID o = CALL.remove(p.getUuid());
        if (o != null) {
            CALL.remove(o);
            ServerPlayerEntity op = s.getPlayerManager().getPlayer(o);
            if (op != null) {
                FunUtil.hint(op, "☎ Разговор окончен", Formatting.GRAY);
                update(op);
                send(op);
            }
            FunUtil.hint(p, "☎ Разговор окончен", Formatting.GRAY);
        }
        update(p);
        send(p);
    }

    /** Нужная группа игрока: звонок важнее рации; в тюрьме — ничего. */
    static String[] want(ServerPlayerEntity p) {
        if (Prison.inside(p)) return new String[]{"", ""};
        UUID o = CALL.get(p.getUuid());
        if (o != null) {
            String a = p.getUuid().toString(), b = o.toString();
            return new String[]{"call:" + (a.compareTo(b) < 0 ? a + ":" + b : b + ":" + a), "Звонок"};
        }
        String ch = CHANNEL.getOrDefault(p.getUuid(), "near");
        if ((ch.equals("duty") || ch.equals("near") && Duty.on(p) && !CHANNEL.containsKey(p.getUuid())) && Duty.on(p))
            return new String[]{"duty", "Рация службы"};
        if (ch.equals("gang")) {
            String g = CrimeCareer.gangName(p.getServer(), p.getUuid());
            if (g != null) return new String[]{"gang:" + g, "Банда " + g};
        }
        return new String[]{"", ""};
    }

    static void update(ServerPlayerEntity p) {
        Backend b = backend;
        String[] w = want(p);
        if (w[0].equals(LAST.get(p.getUuid()))) return;
        LAST.put(p.getUuid(), w[0]);
        if (b != null) b.group(p.getUuid(), w[0], w[1]);
    }

    private static void tick(MinecraftServer s) {
        if (s.getTicks() % 20 != 11) return;
        long now = s.getTicks();
        RING_AT.entrySet().removeIf(e -> {
            if (now - e.getValue() < 20 * 30) return false;
            UUID from = RING.remove(e.getKey());
            ServerPlayerEntity f = from == null ? null : s.getPlayerManager().getPlayer(from);
            if (f != null) FunUtil.hint(f, "Не отвечает", Formatting.GRAY);
            return true;
        });
        boolean jailMute = Admin.cfg.voiceJailMute;
        for (ServerPlayerEntity p : s.getPlayerManager().getPlayerList()) {
            if (jailMute && Prison.inside(p)) MUTED.add(p.getUuid());
            else MUTED.remove(p.getUuid());
            if (Prison.inside(p) && CALL.containsKey(p.getUuid())) hangup(p);
            update(p);
        }
    }

    public static void onLeave(ServerPlayerEntity p) {
        if (CALL.containsKey(p.getUuid()) || RING.containsKey(p.getUuid()) || RING.containsValue(p.getUuid())) hangup(p);
        CHANNEL.remove(p.getUuid());
        LAST.remove(p.getUuid());
        MUTED.remove(p.getUuid());
    }

    public static void send(ServerPlayerEntity p) {
        NbtCompound n = new NbtCompound();
        n.putBoolean("server", backend != null);
        n.putString("ch", CHANNEL.getOrDefault(p.getUuid(), "near"));
        n.putString("now", want(p)[1]);
        n.putBoolean("duty", Duty.on(p));
        String g = CrimeCareer.gangName(p.getServer(), p.getUuid());
        n.putString("gang", g == null ? "" : g);
        UUID o = CALL.get(p.getUuid()), r = RING.get(p.getUuid());
        ServerPlayerEntity op = o == null ? null : p.getServer().getPlayerManager().getPlayer(o), rp = r == null ? null : p.getServer().getPlayerManager().getPlayer(r);
        n.putString("call", op == null ? "" : op.getName().getString());
        n.putString("ring", rp == null ? "" : rp.getName().getString());
        n.putBoolean("dialing", RING.containsValue(p.getUuid()));
        n.putBoolean("jail", Prison.inside(p));
        NbtList l = new NbtList();
        for (ServerPlayerEntity x : p.getServer().getPlayerManager().getPlayerList()) if (x != p) l.add(NbtString.of(x.getName().getString()));
        n.put("players", l);
        LifeNet.send(p, "voice", n);
    }

    /** Для автотеста: в звонке ли игрок. */
    public static boolean inCall(UUID id) {
        return CALL.containsKey(id);
    }
}
