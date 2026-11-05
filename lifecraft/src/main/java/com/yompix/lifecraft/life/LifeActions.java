package com.yompix.lifecraft.life;

import com.yompix.lifecraft.block.LifeBlock;
import com.yompix.lifecraft.block.SeatBlockHelper;
import com.yompix.lifecraft.entity.CitizenEntity;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

/** Что происходит при нажатии на блоки города и на жителей. */
public final class LifeActions {
    private LifeActions() {}

    public static void useBlock(LifeBlock.Kind kind, BlockState state, ServerWorld world, BlockPos pos, ServerPlayerEntity p, ItemStack stack) {
        switch (kind) {
            case CHALKBOARD -> School.start(p);
            case DESK -> SeatBlockHelper.sit(world, pos, p, 0.3);
            case ATM -> Bank.useAtm(p, stack);
            case JOB_BOARD -> Jobs.jobBoard(p);
            case MAILBOX -> Jobs.mailbox(p, pos, stack);
            case SHOP_COUNTER -> Jobs.cashier(p);
            case COMPUTER -> Jobs.computer(p);
            case SLOT_MACHINE -> openCasino(p, "slots");
            case ROULETTE -> openCasino(p, "roulette");
        }
    }

    private static void openCasino(ServerPlayerEntity p, String game) {
        if (!Life.get(p).adult()) {
            p.sendMessage(Text.literal("🔞 Охранник: «Сначала школу закончи, потом приходи»").formatted(Formatting.RED), false);
            return;
        }
        NbtCompound n = new NbtCompound();
        n.putString("game", game);
        LifeNet.send(p, "open_casino", n);
    }


    /** Те же фразы, что озвучены в tools/story.py (AMBIENT). */
    private static final java.util.Map<String, String[]> AMBIENT = java.util.Map.of(
            "kid", new String[]{"Привет!", "Пошли играть в футбол!", "У меня завтра контрольная…", "Ты видел новую приставку?", "Мама зовёт обедать!"},
            "adult", new String[]{"Добрый день!", "Хорошая погода сегодня.", "Опять пробки на Центральной…", "Не подскажете, где банк?", "Удачи!"},
            "old", new String[]{"Здравствуй, внучек!", "Шапку надень, простудишься!", "Раньше хлеб был вкуснее.", "Ох, молодёжь…"});

    public static void talk(CitizenEntity c, ServerPlayerEntity p) {
        switch (c.getRole()) {
            case TEACHER -> School.start(p);
            case SHOPKEEPER -> Shop.open(p, false);
            case CROUPIER -> openCasino(p, "slots");
            case DEALER -> openCasino(p, "roulette");
            case BANKER -> LifeNet.send(p, "open_phone", Jobs.app("bank"));
            default -> {
                LifeData ld = Life.get(p);
                if (++ld.talks >= 20) Achievements.give(p, "friend");
                ld.mood = Math.min(100, ld.mood + 1);
                // голос прохожего: детский, взрослый или пожилой
                String group = c.getScale() < 0.85f ? "kid" : (c.getSkin() == 7 || c.getSkin() == 13) ? "old" : "adult";
                String[] lines = AMBIENT.get(group);
                int i = p.getRandom().nextInt(lines.length);
                net.minecraft.nbt.NbtCompound vn = new net.minecraft.nbt.NbtCompound();
                vn.putString("voice", "amb_" + group + "_" + String.format("%02d", i + 1));
                vn.putInt("entity", c.getId());
                LifeNet.send(p, "ambient", vn);
                c.lockActivity(null);
                String name = c.hasCustomName() ? c.getCustomName().getString() : "Житель";
                p.sendMessage(Text.literal(name + ": ").formatted(Formatting.YELLOW).append(Text.literal(lines[i]).formatted(Formatting.WHITE)), false);
            }
        }
    }
}
