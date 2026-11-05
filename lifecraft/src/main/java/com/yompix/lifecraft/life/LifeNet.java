package com.yompix.lifecraft.life;

import com.yompix.lifecraft.LifeCraft;
import com.yompix.lifecraft.ModContent;
import com.yompix.lifecraft.entity.CarEntity;
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
        public static final Id<S2C> ID = new Id<>(LifeCraft.id("s2c"));
        public static final PacketCodec<RegistryByteBuf, S2C> CODEC = PacketCodec.tuple(
                PacketCodecs.STRING, S2C::type, PacketCodecs.NBT_COMPOUND, S2C::data, S2C::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record C2S(String type, NbtCompound data) implements CustomPayload {
        public static final Id<C2S> ID = new Id<>(LifeCraft.id("c2s"));
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
            try {
                handle(p, payload.type(), payload.data());
            } catch (RuntimeException e) {
                LifeCraft.LOG.warn("Ошибка обработки {} от {}", payload.type(), p.getName().getString(), e);
            }
        });
    }

    public static void send(ServerPlayerEntity p, String type, NbtCompound data) {
        ServerPlayNetworking.send(p, new S2C(type, data));
    }

    private static void handle(ServerPlayerEntity p, String type, NbtCompound d) {
        switch (type) {
            case "hello" -> Life.sync(p);
            case "ready" -> com.yompix.lifecraft.story.Story.onClientReady(p);
            case "dialog_done" -> com.yompix.lifecraft.story.Story.onDialogDone(p, d.getInt("choice"));
            case "cutscene_done" -> com.yompix.lifecraft.story.Story.onCutsceneDone(p);
            case "credits_done" -> com.yompix.lifecraft.story.Story.onCreditsDone(p);
            case "phone_opened" -> com.yompix.lifecraft.story.Story.onPhoneOpened(p);
            case "objective_please" -> com.yompix.lifecraft.story.Story.sendObjective(p, Life.get(p));
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
            case "atm" -> Bank.atm(p, d.getString("action"), d.getInt("amount"));
            case "bj" -> OnlineCasino.blackjack(p, d.getString("action"), d.getInt("bet"));
            case "crash_start" -> OnlineCasino.crashStart(p, d.getInt("bet"));
            case "crash_cashout" -> OnlineCasino.crashCashout(p);
            case "coin" -> OnlineCasino.coin(p, d.getInt("bet"), d.getBoolean("heads"));
            case "buy_house" -> CityLife.buyHouse(p, d.getString("id"));
            case "photo" -> Achievements.give(p, "photo");
            case "slots" -> Casino.spin(p, d.getInt("bet"));
            case "roulette" -> Casino.roulette(p, d.getInt("bet"), d.getString("kind"), d.getInt("number"));
            case "horn" -> {
                long now = p.getServerWorld().getTime();
                if (p.getVehicle() instanceof CarEntity car && car.getControllingPassenger() == p && now - HORN.getOrDefault(p.getUuid(), 0L) >= 12) {
                    HORN.put(p.getUuid(), now);
                    p.getWorld().playSound(null, car.getBlockPos(), ModContent.CAR_HORN, SoundCategory.NEUTRAL, 1.2f, car.isTaxi() ? 1.1f : 1f);
                }
            }
            default -> LifeCraft.LOG.debug("Неизвестный пакет {}", type);
        }
    }

    private static void transfer(ServerPlayerEntity from, String toName, int amount) {
        if (amount <= 0 || amount > 1_000_000) return;
        ServerPlayerEntity to = from.getServer().getPlayerManager().getPlayer(toName);
        if (to == null || to == from) {
            from.sendMessage(Text.literal("Игрок «" + toName + "» не в сети").formatted(Formatting.RED), true);
            return;
        }
        if (!Life.pay(from, amount, "перевод " + to.getName().getString())) return;
        LifeData d = Life.get(to);
        d.money += amount;
        Life.message(to, "Банк", "Перевод $" + amount + " от " + from.getName().getString());
        Life.sound(to, ModContent.CASH, 1.1f);
        Life.message(from, "Банк", "Вы перевели $" + amount + " игроку " + to.getName().getString());
    }
}
