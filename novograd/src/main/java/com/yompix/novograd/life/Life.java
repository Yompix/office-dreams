package com.yompix.novograd.life;

import com.yompix.novograd.Novograd;
import com.yompix.novograd.ModContent;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Удобные действия с жизнью игрока: деньги, сообщения, синхронизация с телефоном. */
public final class Life {
    private Life() {}

    @SuppressWarnings("UnstableApiUsage")
    public static final AttachmentType<LifeData> DATA = AttachmentRegistry.<LifeData>builder()
            .persistent(LifeData.CODEC)
            .copyOnDeath()
            .initializer(LifeData::new)
            .buildAndRegister(Novograd.id("life"));

    @SuppressWarnings("UnstableApiUsage")
    public static LifeData get(ServerPlayerEntity p) {
        return p.getAttachedOrCreate(DATA);
    }

    public static void sync(ServerPlayerEntity p) {
        LifeNet.send(p, "data", get(p).toNbt());
    }

    public static long day(ServerPlayerEntity p) {
        return p.getServerWorld().getTimeOfDay() / 24000L + 1;
    }

    /** Заработок. Показывает зелёную сумму над хотбаром и звенит кассой. */
    public static void earn(ServerPlayerEntity p, int amount, String why) {
        LifeData d = get(p);
        d.money += amount;
        d.earned += amount;
        p.sendMessage(Text.literal("+$" + amount + "  " + why).formatted(Formatting.GREEN), true);
        sound(p, ModContent.CASH, 1f);
        sync(p);
    }

    /** Списать деньги. false — не хватает. */
    public static boolean pay(ServerPlayerEntity p, int amount, String why) {
        LifeData d = get(p);
        if (d.money < amount) {
            p.sendMessage(Text.literal("Не хватает денег: нужно $" + amount + ", у тебя $" + d.money).formatted(Formatting.RED), true);
            sound(p, ModContent.ANSWER_WRONG, 1f);
            return false;
        }
        d.money -= amount;
        d.spent += amount;
        if (!why.isEmpty()) p.sendMessage(Text.literal("−$" + amount + "  " + why).formatted(Formatting.GOLD), true);
        sync(p);
        return true;
    }

    public static void message(ServerPlayerEntity p, String from, String text) {
        get(p).addMessage(from, text, day(p));
        p.sendMessage(Text.literal("📱 ").append(Text.literal(from + ": ").formatted(Formatting.AQUA)).append(Text.literal(text)), false);
        sound(p, ModContent.PHONE_NOTIFY, 1f);
        sync(p);
    }

    public static void sound(ServerPlayerEntity p, SoundEvent s, float pitch) {
        p.playSoundToPlayer(s, SoundCategory.PLAYERS, 0.8f, pitch);
    }
}
