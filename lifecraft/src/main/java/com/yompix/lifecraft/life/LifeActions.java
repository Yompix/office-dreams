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

    private static final String[][] PHRASES = {
            {"Не опаздывай на урок!", "Домашнее задание сделал?", "Кто выучит таблицу умножения — получит пятёрку."},
            {"Свежие бургеры, всего $8!", "Заходи ещё!", "Скидок сегодня нет, но кола холодная."},
            {"Ты из какого класса?", "После уроков — в футбол?", "Завтра контрольная по математике…"},
            {"Слышал? В городе открыли казино.", "Я хочу стать программистом.", "Пошли в магазин за колой?"},
            {"Тяжёлая смена сегодня.", "Курьерам платят неплохо!", "Осторожно, тут дорога."},
            {"Опять дедлайн…", "Без диплома в офис не берут.", "Кофе — лучший друг программиста."},
            {"Соблюдайте правила дорожного движения!", "На красный не ходи.", "В городе спокойно."},
            {"Внучек, учись хорошо!", "Раньше хлеб стоил копейку…", "Шапку надень, холодно!"}};

    public static void talk(CitizenEntity c, ServerPlayerEntity p) {
        switch (c.getRole()) {
            case TEACHER -> School.start(p);
            case SHOPKEEPER -> Shop.open(p, false);
            case CROUPIER -> openCasino(p, "slots");
            case DEALER -> openCasino(p, "roulette");
            case BANKER -> LifeNet.send(p, "open_phone", Jobs.app("bank"));
            default -> {
                String[] lines = PHRASES[c.getSkin() % PHRASES.length];
                String name = c.hasCustomName() ? c.getCustomName().getString() : "Житель";
                p.sendMessage(Text.literal(name + ": ").formatted(Formatting.YELLOW).append(Text.literal(lines[p.getRandom().nextInt(lines.length)]).formatted(Formatting.WHITE)), false);
            }
        }
    }
}
