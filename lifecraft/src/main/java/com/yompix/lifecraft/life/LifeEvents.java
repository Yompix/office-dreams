package com.yompix.lifecraft.life;

import com.yompix.lifecraft.ModContent;
import com.yompix.lifecraft.world.TownBuilder;
import com.yompix.lifecraft.world.TownState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

/** Ход жизни: первый вход, школьные звонки, карманные деньги, проверка такси. */
public final class LifeEvents {
    private LifeEvents() {}

    private static long lastTimeOfDay = -1;

    public static void init() {
        Life.DATA.identifier(); // регистрируем данные игрока при старте
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> onJoin(handler.player)));
        ServerLifecycleEvents.SERVER_STARTED.register(TownBuilder::autoBuildIfCityWorld);
        ServerTickEvents.END_SERVER_TICK.register(LifeEvents::tick);
    }

    private static void onJoin(ServerPlayerEntity p) {
        LifeData d = Life.get(p);
        if (!d.started) {
            d.started = true;
            School.give(p, new ItemStack(ModContent.PHONE));
            Life.message(p, "Мама", "Доброе утро! Не опаздывай в школу: уроки с 8:00. Я положила тебе $50 на карту ❤");
            Life.message(p, "Школа", "Добро пожаловать в 1 класс! Подойди к доске в классе или к учителю, чтобы начать урок.");
            p.sendMessage(Text.literal("📱 Нажми G (или ПКМ телефоном), чтобы открыть телефон").formatted(Formatting.AQUA), false);
        }
        Life.sync(p);
    }

    private static void tick(MinecraftServer server) {
        if (server.getTicks() % 20 != 0) return;
        ServerWorld world = server.getOverworld();
        long tod = world.getTimeOfDay() % 24000;
        long day = world.getTimeOfDay() / 24000 + 1;
        long step = Math.floorMod(tod - lastTimeOfDay, 24000);
        // звоним только при обычном ходе времени: не при /time set и не после сна
        if (lastTimeOfDay >= 0 && step > 0 && step < 200) {
            for (long bell = School.DAY_START; bell <= School.DAY_END; bell += 1000) {
                if (crossed(lastTimeOfDay, tod, bell)) ringBell(world, bell == School.DAY_END);
            }
        }
        lastTimeOfDay = tod;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            Jobs.tickTaxi(p);
            LifeData d = Life.get(p);
            if (tod >= 1000 && d.lastAllowanceDay != day) {
                d.lastAllowanceDay = day;
                if (!d.adult()) {
                    d.money += 10;
                    Life.message(p, "Мама", "День " + day + ". Карманные $10 на обед. Учись хорошо!");
                } else if (Jobs.job(p) == Jobs.Job.NONE) {
                    Life.message(p, "Центр занятости", "Ты без работы. Загляни в приложение «Работа» — там вакансии.");
                } else {
                    d.money = Math.max(0, d.money - 5);
                    Life.message(p, "ЖКХ", "Списано $5 за коммунальные услуги. День " + day + ".");
                }
            }
        }
    }

    private static boolean crossed(long from, long to, long mark) {
        return from < to ? from < mark && to >= mark : from < mark || to >= mark;
    }

    private static void ringBell(ServerWorld world, boolean last) {
        BlockPos school = TownState.get(world).school;
        for (ServerPlayerEntity p : world.getPlayers()) {
            if (school != null && p.getBlockPos().getSquaredDistance(school) > 160 * 160) continue;
            if (school == null && Life.get(p).adult()) continue;
            p.playSoundToPlayer(ModContent.SCHOOL_BELL, SoundCategory.BLOCKS, school == null ? 0.4f : 0.9f, 1f);
            p.sendMessage(Text.literal(last ? "🔔 Звонок! Уроки на сегодня закончились" : "🔔 Звонок на урок!").formatted(Formatting.GOLD), true);
        }
    }
}
