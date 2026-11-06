package com.yompix.novograd.life;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.world.TownBuilder;
import com.yompix.novograd.world.TownState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
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
        // GTA: кровь при ранении, больница после гибели
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
            if (taken > 0 && entity.getWorld() instanceof net.minecraft.server.world.ServerWorld w
                    && (entity instanceof net.minecraft.entity.player.PlayerEntity || entity instanceof com.yompix.novograd.entity.CitizenEntity))
                com.yompix.novograd.gta.Weapons.blood(w, entity, taken, entity.isDead());
        });
        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register((oldP, newP, alive) -> {
            if (!alive) newP.getServer().execute(() -> com.yompix.novograd.gta.Wanted.respawned(newP));
        });
        net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register((player, world, hand, entity, hit) ->
                !world.isClient && hand == net.minecraft.util.Hand.MAIN_HAND && player instanceof ServerPlayerEntity sp && SideQuests.catFound(sp, entity)
                        ? net.minecraft.util.ActionResult.SUCCESS : net.minecraft.util.ActionResult.PASS);
        ServerTickEvents.END_SERVER_TICK.register(LifeEvents::tick);
        ServerTickEvents.END_SERVER_TICK.register(com.yompix.novograd.gta.Activities::tick);
        ServerTickEvents.END_SERVER_TICK.register(s -> s.getPlayerManager().getPlayerList().forEach(p -> {
            com.yompix.novograd.story.Story.tick(p);
            OnlineCasino.tick(p);
        }));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> com.yompix.novograd.story.Story.onLeave(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> com.yompix.novograd.gta.Activities.onLeave(handler.player));
    }

    private static void onJoin(ServerPlayerEntity p) {
        LifeData d = Life.get(p);
        d.weight = 75; // веса в Новограде нет
        d.fitness = 0;
        Fitness.apply(p, d);
        boolean storyWorld = !TownState.get(p.getServerWorld()).places.isEmpty();
        if (!d.started && storyWorld) {
            d.started = true;
        } else if (!d.started) {
            d.started = true;
            School.give(p, new ItemStack(ModContent.PHONE));
            Life.message(p, "Мама", "Доброе утро! Не опаздывай в школу: уроки с 8:00. Я положила тебе $50 на карту ❤");
            Life.message(p, "Школа", "Добро пожаловать в 1 класс! Подойди к доске в классе или к учителю, чтобы начать урок.");
            p.sendMessage(Text.literal("📱 Нажми G (или ПКМ телефоном), чтобы открыть телефон").formatted(Formatting.AQUA), false);
        }
        com.yompix.novograd.story.Story.onJoin(p);
        NbtCompound places = Taxi.placesNbt(p.getServerWorld());
        if (places != null) LifeNet.send(p, "places", places);
        Life.sync(p);
    }

    private static void tick(MinecraftServer server) {
        if (server.getTicks() % 20 != 0) return;
        ServerWorld world = server.getOverworld();
        long tod = world.getTimeOfDay() % 24000;
        long day = world.getTimeOfDay() / 24000 + 1;
        long step = Math.floorMod(tod - lastTimeOfDay, 24000);
        // звоним только при обычном ходе времени: не при /time set и не после сна
        if (false && lastTimeOfDay >= 0 && step > 0 && step < 200) { // школьного звонка в Новограде нет
            for (long bell = School.DAY_START; bell <= School.DAY_END; bell += 1000) {
                if (crossed(lastTimeOfDay, tod, bell)) ringBell(world, bell == School.DAY_END);
            }
        }
        lastTimeOfDay = tod;
        CityLife.worldTick(server);
        // игра по сети: раз в 2 секунды все видят друзей на карте, даже далеко
        if (server.getTicks() % 40 == 0 && server.getPlayerManager().getCurrentPlayerCount() > 1) {
            NbtCompound pl = new NbtCompound();
            net.minecraft.nbt.NbtList list = new net.minecraft.nbt.NbtList();
            for (ServerPlayerEntity q : server.getPlayerManager().getPlayerList()) {
                NbtCompound e = new NbtCompound();
                e.putString("name", q.getName().getString());
                e.putDouble("x", q.getX());
                e.putDouble("z", q.getZ());
                e.putFloat("yaw", q.getYaw());
                e.putString("job", Jobs.job(q).title);
                e.putInt("age", Life.get(q).age);
                e.putFloat("kg", Life.get(q).weight);
                list.add(e);
            }
            pl.put("list", list);
            for (ServerPlayerEntity q : server.getPlayerManager().getPlayerList()) LifeNet.send(q, "players", pl);
        }
        SideQuests.tick(server, server.getTicks() / 20);
        com.yompix.novograd.gta.Wanted.tick(server);
        com.yompix.novograd.gta.Population.tick(server);
        com.yompix.novograd.gta.Streets.tick(server);
        Police.tick(server);
        int second = server.getTicks() / 20;
        String today = CityLife.event(world);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            CityLife.playerTick(p, second);
            Taxi.tick(p);
            Achievements.check(p);
            if (!Life.get(p).today.equals(today)) {
                Life.get(p).today = today;
                Life.sync(p);
            }
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
