package com.yompix.novograd.life;

import com.yompix.novograd.Novograd;
import com.yompix.novograd.ModContent;
import com.yompix.novograd.entity.CarEntity;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Один канал в каждую сторону: тип сообщения + NBT. Сервер ничего не принимает на веру:
 * ответы на уроки, работу и ставки проверяются здесь.
 */
public final class LifeNet {
    private LifeNet() {}

    private static final java.util.Map<java.util.UUID, Long> HORN = new java.util.HashMap<>();

    public record S2C(String type, NbtCompound data) implements CustomPayload {
        public static final Id<S2C> ID = new Id<>(Novograd.id("s2c"));
        public static final PacketCodec<RegistryByteBuf, S2C> CODEC = PacketCodec.tuple(
                PacketCodecs.STRING, S2C::type, PacketCodecs.NBT_COMPOUND, S2C::data, S2C::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record C2S(String type, NbtCompound data) implements CustomPayload {
        public static final Id<C2S> ID = new Id<>(Novograd.id("c2s"));
        public static final PacketCodec<RegistryByteBuf, C2S> CODEC = PacketCodec.tuple(
                PacketCodecs.STRING, C2S::type, PacketCodecs.NBT_COMPOUND, C2S::data, C2S::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public static void init() {
        PayloadTypeRegistry.playS2C().register(S2C.ID, S2C.CODEC);
        PayloadTypeRegistry.playC2S().register(C2S.ID, C2S.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(C2S.ID, (payload, ctx) -> {
            ServerPlayerEntity p = ctx.player();
            if (!Admin.allow(p)) return; // 0.9.6: лимит пакетов от одного игрока
            try {
                handle(p, payload.type(), payload.data());
            } catch (RuntimeException e) {
                Novograd.LOG.warn("Ошибка обработки {} от {}", payload.type(), p.getName().getString(), e);
            }
        });
    }

    public static void send(ServerPlayerEntity p, String type, NbtCompound data) {
        ServerPlayNetworking.send(p, new S2C(type, data));
    }

    private static final java.util.Set<String> EMOTES = java.util.Set.of("wave", "dance", "dance2", "point", "sit", "facepalm", "salute", "phone", "none",
            "gym_curl", "gym_bench", "gym_pullup", "gym_bag", "gym_pushup", "gym_run", "gym_squat", "gym_yoga", "phone_ear", "phone_text");

    /** 0.8: разослать жест игрокам в том же мире ближе 160 блоков (только внешний вид, без механики). */
    private static void emote(ServerPlayerEntity p, String id) {
        if (!EMOTES.contains(id)) return;
        NbtCompound n = new NbtCompound();
        n.putUuid("who", p.getUuid());
        n.putString("id", id);
        for (ServerPlayerEntity q : p.getServerWorld().getPlayers())
            if (q != p && q.squaredDistanceTo(p) < 160 * 160) send(q, "emote", n.copy());
    }

    private static void handle(ServerPlayerEntity p, String type, NbtCompound d) {
        String scope = com.yompix.novograd.story.Npcs.push(p); // 0.9.7: герои сюжета — этого игрока
        try {
            handleScoped(p, type, d);
        } finally {
            com.yompix.novograd.story.Npcs.pop(scope);
        }
    }

    private static void handleScoped(ServerPlayerEntity p, String type, NbtCompound d) {
        switch (type) {
            case "hello" -> Life.sync(p);
            case "age" -> AgeGate.report(p, d); // 0.9.7: проверка возраста (ответ клиента)
            case "emote" -> emote(p, d.getString("id"));
            // 0.9: прогресс, «Империя», «Сеть», значки карты
            case "progress_state" -> com.yompix.novograd.gta.Progress.send(p);
            case "map_acts" -> com.yompix.novograd.gta.Progress.mapActs(p);
            case "empire_state" -> com.yompix.novograd.gta.Empire.send(p);
            case "empire_hire" -> com.yompix.novograd.gta.Empire.hire(p, d.getInt("i"), d.getString("who"));
            case "grid_scan" -> com.yompix.novograd.gta.Grid.scan(p);
            case "grid_hack" -> com.yompix.novograd.gta.Grid.hack(p, d.getString("id"));
            case "grid_fail" -> com.yompix.novograd.gta.Grid.fail(p);
            case "observe" -> com.yompix.novograd.gta.Observe.set(p, d.getBoolean("on")); // 0.9.5: «Наблюдение» // 0.8: жест игрока (V) — видят игроки рядом
            case "ready" -> com.yompix.novograd.story.Story.onClientReady(p);
            case "perf_level" -> com.yompix.novograd.perf.PerfLevel.level = Math.max(0, Math.min(2, d.getInt("level"))); // настройка «Производительность»
            case "taxi_call" -> Taxi.call(p, d.getString("dest"));
            case "taxi_accept" -> Taxi.accept(p);
            case "dialog_done" -> com.yompix.novograd.story.Story.onDialogDone(p, d.getInt("choice"));
            case "eco_state" -> com.yompix.novograd.gta.Economy.sendState(p);
            case "stock_trade" -> com.yompix.novograd.gta.Economy.trade(p, d.getString("t"), d.getInt("qty"));
            case "biz_upgrade" -> com.yompix.novograd.gta.Heists.upgrade(p, d.getInt("i"));
            case "taxi_skip" -> Taxi.skip(p);
            case "cheat_code" -> com.yompix.novograd.gta.Cheats.apply(p, d.getString("code"));
            case "news_feed" -> com.yompix.novograd.gta.News.sendFeed(p);
            case "cut_line" -> com.yompix.novograd.story.Director.speak(p, d.getString("who"));
            case "cut_hold" -> com.yompix.novograd.story.Director.hold(p, d.getBoolean("on"), d.contains("shot") ? d.getInt("shot") : -1);
            case "activity" -> com.yompix.novograd.gta.Activities.toggle(p);
            case "cutscene_done" -> com.yompix.novograd.story.Story.onCutsceneDone(p);
            case "credits_done" -> com.yompix.novograd.story.Story.onCreditsDone(p);
            case "phone_opened" -> com.yompix.novograd.story.Story.onPhoneOpened(p);
            case "objective_please" -> com.yompix.novograd.story.Story.sendObjective(p, Life.get(p));
            case "read_messages" -> {
                Life.get(p).unread = 0;
                Life.sync(p);
            }
            case "lesson_answer" -> School.answer(p, d.getIntArray("answers"), d.getBoolean("hint"));
            case "shop_buy" -> Shop.buy(p, d.getInt("item"), d.getBoolean("delivery"));
            case "job_select" -> Jobs.select(p, d.getString("job"));
            case "job_quit" -> Jobs.select(p, "none");
            case "taxi_order" -> Jobs.takeTaxiOrder(p);
            case "work_answer" -> Jobs.answer(p, d);
            case "transfer" -> transfer(p, d.getString("to"), d.getInt("amount"));
            case "atm" -> {
                String a = d.getString("action");
                if (a.startsWith("cash_")) com.yompix.novograd.gta.Money.atm(p, a, d.getInt("amount"));
                else Bank.atm(p, a, d.getInt("amount"));
            }
            case "mycars" -> com.yompix.novograd.gta.MyCars.handle(p, d.getString("act"), d);
            case "stash" -> com.yompix.novograd.gta.Money.stashAction(p, d.getString("act"), d.getInt("amount"));
            case "stash_open" -> HandUses.stashOpen(p); // 0.9.8: дома — к сейфу руками, потом экран
            case "stash_state" -> com.yompix.novograd.gta.Money.sendStash(p);
            case "bj" -> { if (!AgeGate.casino(p)) AgeGate.casinoClosed(p); else OnlineCasino.blackjack(p, d.getString("action"), d.getInt("bet")); }
            case "crash_start" -> { if (!AgeGate.casino(p)) AgeGate.casinoClosed(p); else OnlineCasino.crashStart(p, d.getInt("bet"), d.getInt("auto")); }
            case "cg" -> { if (!AgeGate.casino(p)) AgeGate.casinoClosed(p); else CasinoGames.handle(p, d); } // покер, кости, колесо, бега, плинко, мины
            case "crash_cashout" -> { if (!AgeGate.casino(p)) AgeGate.casinoClosed(p); else OnlineCasino.crashCashout(p); }
            case "coin" -> { if (!AgeGate.casino(p)) AgeGate.casinoClosed(p); else OnlineCasino.coin(p, d.getInt("bet"), d.getBoolean("heads")); }
            case "rhythm_done" -> Fitness.result(p, d.getString("mode"), d.getInt("score"));
            case "buy_house" -> CityLife.buyHouse(p, d.getString("id"));
            case "photo" -> {
                Achievements.give(p, "photo");
                com.yompix.novograd.gta.Collectibles.onPhoto(p); // фото-точки коллекции
                com.yompix.novograd.gta.Underworld.onPhoto(p); // разведка большого ограбления
                com.yompix.novograd.gta.Social.onPhoto(p); // 0.9.9: пост в «Фотограм»
            }
            case "slots" -> { if (!AgeGate.casino(p)) AgeGate.casinoClosed(p); else Casino.spin(p, d.getInt("bet")); }
            case "roulette" -> { if (!AgeGate.casino(p)) AgeGate.casinoClosed(p); else Casino.roulette(p, d.getInt("bet"), d.getString("kind"), d.getInt("number")); }
            case "side_list" -> com.yompix.novograd.story.SideMissions.sendList(p);
            case "side_start" -> com.yompix.novograd.story.SideMissions.start(p, d.getString("id"));
            case "side_cancel" -> com.yompix.novograd.story.SideMissions.cancel(p);
            case "garage" -> com.yompix.novograd.gta.Garage.action(p, d.getString("action"), d.getInt("value"));
            case "nitro" -> {
                if (p.getVehicle() instanceof CarEntity car && car.getControllingPassenger() == p && !car.fireNitro())
                    p.sendMessage(net.minecraft.text.Text.literal(car.hasNitro() ? "Нитро перезаряжается…" : "Нитро нет — поставь в «Нитро-Гараже»"), true);
            }
            case "car_light" -> {
                if (p.getVehicle() instanceof CarEntity car && car.getControllingPassenger() == p) {
                    int bit = d.getInt("bit");
                    car.toggleLight(bit);
                    p.getWorld().playSound(null, car.getBlockPos(), net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK.value(), SoundCategory.NEUTRAL, 0.25f, 1.8f);
                }
            }
            case "horn" -> {
                long now = p.getServerWorld().getTime();
                if (p.getVehicle() instanceof CarEntity car && car.getControllingPassenger() == p && now - HORN.getOrDefault(p.getUuid(), 0L) >= 12) {
                    HORN.put(p.getUuid(), now);
                    p.getWorld().playSound(null, car.getBlockPos(), ModContent.CAR_HORN, SoundCategory.NEUTRAL, 1.2f, car.isTaxi() ? 1.1f : 1f);
                    com.yompix.novograd.world.LifeAi.honk(p, car); // прохожие впереди отскакивают
                }
            }
            default -> {
                if (type.startsWith("hand_") && Hands.handle(p, type, d)) return; // 0.9.8: руки — прервать, закурить, выпить
                if (type.startsWith("loan_") && com.yompix.novograd.gta.Loans.handle(p, type, d)) return; // 0.9.8: кредит
                // 0.9.9 «Город»: соцсеть, свидания, спорт, ночь, квартира
                if (type.startsWith("social_") && com.yompix.novograd.gta.Social.handle(p, type, d)) return;
                if (type.startsWith("date_") && Dating.handle(p, type, d)) return;
                if (type.startsWith("sport_") && com.yompix.novograd.gta.Sports.handle(p, type, d)) return;
                if (type.startsWith("night_") && com.yompix.novograd.gta.Nightlife.handle(p, type, d)) return;
                if (type.startsWith("home_") && com.yompix.novograd.world.HomeLife.handle(p, type, d)) return;
                if (type.startsWith("drv_") && com.yompix.novograd.gta.DriveHooks.handle(p, type, d)) return; // дороги и транспорт 0.4
                if (type.startsWith("city_") && com.yompix.novograd.gta.CityServices.handle(p, type, d)) return; // автобусы, службы города
                if (type.startsWith("call_") && com.yompix.novograd.story.Calls.handle(p, type, d)) return; // звонки героев, «Контакты»
                if (type.startsWith("out_") && com.yompix.novograd.gta.Outdoors.handle(p, type, d)) return; // навыки, охота, рыбалка, праздники
                if (type.startsWith("uw_") && com.yompix.novograd.gta.Underworld.handle(p, type, d)) return; // тёмные дела
                if (!com.yompix.novograd.gta.FunHooks.handle(p, type, d)) Novograd.LOG.debug("Неизвестный пакет {}", type);
            }
        }
    }

    private static void transfer(ServerPlayerEntity from, String toName, int amount) {
        if (amount <= 0 || amount > 1_000_000) return;
        ServerPlayerEntity to = from.getServer().getPlayerManager().getPlayer(toName);
        if (to == null || to == from) {
            from.sendMessage(Text.literal("Игрок «" + toName + "» не в сети").formatted(Formatting.RED), true);
            return;
        }
        if (!Life.payRaw(from, amount, "перевод " + to.getName().getString())) return;
        LifeData d = Life.get(to);
        d.money += amount;
        Life.message(to, "Банк", "Перевод $" + amount + " от " + from.getName().getString());
        Life.sound(to, ModContent.CASH, 1.1f);
        Life.message(from, "Банк", "Вы перевели $" + amount + " игроку " + to.getName().getString());
    }
}
