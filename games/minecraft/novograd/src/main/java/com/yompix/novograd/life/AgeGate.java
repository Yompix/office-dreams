package com.yompix.novograd.life;

import com.yompix.novograd.Novograd;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 0.9.7: проверка возраста. Клиент сам спрашивает дату рождения при первом запуске (хранит только «18+ да/нет» и дату
 * проверки) и сразу после входа присылает пакет {@code age}: взрослый ли, и какие взрослые вещи он включил (казино,
 * алкоголь). Это честное слово клиента — подделать можно, поэтому это не защита, а уважение к настройке игрока.
 *
 * <ul>
 *   <li>Казино (автоматы, рулетка, онлайн-игры) и выпивка у Розы — только если клиент сказал «18+» и не выключил их сам.</li>
 *   <li>Не 18+: в редакторе остаётся средний размер ({@link Appearance#MID_CHEST}).</li>
 *   <li>{@code requireAdult} в {@code novograd-server.json}: не 18+ (или клиент не прислал ответ за 15 с) — вежливо
 *       отключаем.</li>
 * </ul>
 */
public final class AgeGate {
    private AgeGate() {}

    /** Что сказал клиент. */
    public record Report(boolean adult, boolean casino, boolean alcohol) {}

    private static final Map<UUID, Report> REPORTS = new HashMap<>();
    /** Вошёл и ещё не ответил: время входа (тик сервера). */
    private static final Map<UUID, Integer> WAITING = new HashMap<>();
    private static final int WAIT_TICKS = 20 * 15;

    public static final String KICK_MINOR = "Этот сервер — только для взрослых (18+). Спасибо, что зашёл!";
    public static final String KICK_SILENT = "Этому серверу нужен Новоград 0.9.7 или новее (проверка возраста).";

    public static void init() {
        ServerPlayConnectionEvents.JOIN.register((h, s, server) -> WAITING.put(h.player.getUuid(), server.getTicks()));
        ServerPlayConnectionEvents.DISCONNECT.register((h, s) -> {
            REPORTS.remove(h.player.getUuid());
            WAITING.remove(h.player.getUuid());
        });
        ServerTickEvents.END_SERVER_TICK.register(AgeGate::tick);
    }

    private static void tick(MinecraftServer s) {
        if (WAITING.isEmpty() || s.getTicks() % 20 != 0) return;
        var it = WAITING.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            if (s.getTicks() - e.getValue() < WAIT_TICKS) continue;
            it.remove();
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(e.getKey());
            if (p == null || p instanceof net.fabricmc.fabric.api.entity.FakePlayer) continue;
            if (Admin.cfg.requireAdult && !REPORTS.containsKey(e.getKey())) {
                Novograd.LOG.info("[возраст] {}: клиент не ответил — отключён (requireAdult)", p.getName().getString());
                p.networkHandler.disconnect(Text.literal(KICK_SILENT));
            }
        }
    }

    /** Пакет {@code age} от клиента. */
    public static void report(ServerPlayerEntity p, NbtCompound d) {
        boolean adult = d.getBoolean("adult");
        Report r = new Report(adult, adult && d.getBoolean("casino"), adult && d.getBoolean("alcohol"));
        REPORTS.put(p.getUuid(), r);
        WAITING.remove(p.getUuid());
        if (Admin.cfg.requireAdult && !adult) {
            Novograd.LOG.info("[возраст] {}: не 18+ — отключён (requireAdult)", p.getName().getString());
            p.networkHandler.disconnect(Text.literal(KICK_MINOR));
            return;
        }
        // не 18+: средний размер в своей внешности (выбор размера у таких игроков скрыт)
        if (!adult) {
            LifeData ld = Life.get(p);
            if (ld.look != null && ld.look.chest != Appearance.MID_CHEST) {
                ld.look.chest = Appearance.MID_CHEST;
                Style.broadcast(p);
            }
        }
    }

    /** Ответ клиента (null — ещё не пришёл; боты и консоль — тоже null). */
    public static Report of(ServerPlayerEntity p) {
        return REPORTS.get(p.getUuid());
    }

    public static boolean adult(ServerPlayerEntity p) {
        Report r = of(p);
        return r != null && r.adult();
    }

    /** Азартные игры разрешены: 18+ и казино не выключено в настройках. Пока клиент не ответил — нет. */
    public static boolean casino(ServerPlayerEntity p) {
        Report r = of(p);
        return r != null && r.casino();
    }

    public static boolean alcohol(ServerPlayerEntity p) {
        Report r = of(p);
        return r != null && r.alcohol();
    }

    /** Отказ в казино (одна строка на экране). */
    public static void casinoClosed(ServerPlayerEntity p) {
        p.sendMessage(Text.literal("Казино недоступно: взрослый контент выключен").formatted(net.minecraft.util.Formatting.GRAY), true);
    }

    /** Для автотеста: подставить ответ клиента. */
    public static void testSet(UUID id, Report r) {
        if (r == null) REPORTS.remove(id);
        else REPORTS.put(id, r);
    }
}
