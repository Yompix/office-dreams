package com.yompix.novograd.life;

import com.yompix.novograd.block.LifeBlock;
import com.yompix.novograd.block.SeatBlockHelper;
import com.yompix.novograd.entity.CitizenEntity;
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
            case CHALKBOARD -> {}
            case DESK -> SeatBlockHelper.sit(world, pos, p, 0.3);
            case ATM -> Bank.useAtm(p, stack);
            case JOB_BOARD -> Jobs.jobBoard(p);
            case MAILBOX -> Jobs.mailbox(p, pos, stack);
            case SHOP_COUNTER -> Jobs.cashier(p);
            case COMPUTER -> Jobs.computer(p);
            case SLOT_MACHINE -> openCasino(p, "slots");
            case ROULETTE -> openCasino(p, "roulette");
            case TREADMILL, BARBELL -> { if (Style.near(p, CitizenEntity.Role.COACH, 32)) Style.open(p, "gym"); }
            case SCALES, DJ_DECK -> {}
            case PIZZA_OVEN -> Jobs.pizzaOrder(p);
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


    /** Фразы прохожих с тегами (озвучены tools/voice.py, список — assets/novograd/ambient.json). */
    private record Phrase(String key, String text, java.util.Set<String> tags) {}

    private static java.util.Map<String, java.util.List<Phrase>> ambient;
    /** Что недавно звучало рядом (чтобы соседи не повторяли друг за другом) и что каждый житель уже говорил. */
    private static final java.util.ArrayDeque<String> RECENT = new java.util.ArrayDeque<>();
    private static final java.util.Map<java.util.UUID, java.util.ArrayDeque<String>> SAID = new java.util.HashMap<>();
    /** Кто с кем уже здоровался сегодня: житель+игрок → день. */
    private static final java.util.Map<String, Long> GREETED = new java.util.HashMap<>();

    private static java.util.Map<String, java.util.List<Phrase>> ambient() {
        if (ambient != null) return ambient;
        ambient = new java.util.HashMap<>();
        try (var in = LifeActions.class.getResourceAsStream("/assets/novograd/ambient.json")) {
            var root = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            for (var e : root.entrySet()) {
                java.util.List<Phrase> list = new java.util.ArrayList<>();
                for (var el : e.getValue().getAsJsonArray()) {
                    var o = el.getAsJsonObject();
                    java.util.Set<String> tags = new java.util.HashSet<>();
                    o.getAsJsonArray("tags").forEach(t -> tags.add(t.getAsString()));
                    list.add(new Phrase(o.get("key").getAsString(), o.get("text").getAsString(), tags));
                }
                ambient.put(e.getKey(), list);
            }
        } catch (Exception ex) {
            com.yompix.novograd.Novograd.LOG.warn("ambient.json: {}", ex.toString());
        }
        return ambient;
    }

    /**
     * Фраза по ситуации: при первой встрече за день — приветствие по времени суток, дальше — темы,
     * уместные сейчас (дождь, вечер, ребёнку или взрослому), без повторов своих и чужих недавних фраз.
     */
    private static Phrase pick(CitizenEntity c, ServerPlayerEntity p, String group) {
        var list = ambient().getOrDefault(group, java.util.List.of());
        var w = p.getServerWorld();
        long t = w.getTimeOfDay() % 24000;
        int hour = (int) ((t / 1000 + 6) % 24);
        String time = hour >= 5 && hour < 11 ? "morning" : hour < 17 ? "day" : hour < 22 ? "evening" : "night";
        LifeData d = Life.get(p);
        boolean kid = d.age > 0 ? d.age < 18 : d.edu <= LifeData.SCHOOL_END;
        String key = c.getUuid() + "/" + p.getUuid();
        long day = w.getTimeOfDay() / 24000;
        boolean met = GREETED.getOrDefault(key, -1L) == day;
        GREETED.put(key, day);
        var said = SAID.computeIfAbsent(c.getUuid(), u -> new java.util.ArrayDeque<>());
        java.util.function.Predicate<Phrase> fits = ph -> {
            var tg = ph.tags();
            if (tg.contains("to_kid") && !kid) return false;
            if (tg.contains("to_adult") && kid) return false;
            if (tg.contains("rain")) return w.isRaining();
            boolean timed = tg.contains("morning") || tg.contains("day") || tg.contains("evening") || tg.contains("night");
            return !timed || tg.contains(time);
        };
        java.util.List<Phrase> pool;
        if (!met) pool = list.stream().filter(ph -> ph.tags().contains("greet") && fits.test(ph)).toList();
        else if (said.size() >= 3 && p.getRandom().nextInt(3) == 0) pool = list.stream().filter(ph -> ph.tags().contains("again")).toList();
        else {
            pool = list.stream().filter(ph -> !ph.tags().contains("greet") && !ph.tags().contains("again") && fits.test(ph)
                    && !said.contains(ph.key()) && !RECENT.contains(group + ph.key())).toList();
            if (pool.isEmpty()) pool = list.stream().filter(ph -> !ph.tags().contains("greet") && !ph.tags().contains("again") && fits.test(ph) && !said.contains(ph.key())).toList();
            if (pool.isEmpty()) pool = list.stream().filter(ph -> ph.tags().contains("again")).toList();
        }
        if (pool.isEmpty()) pool = list;
        Phrase ph = pool.get(p.getRandom().nextInt(pool.size()));
        said.addLast(ph.key());
        while (said.size() > 5) said.removeFirst();
        RECENT.addLast(group + ph.key());
        while (RECENT.size() > 6) RECENT.removeFirst();
        return ph;
    }

    public static void talk(CitizenEntity c, ServerPlayerEntity p) {
        switch (c.getRole()) {
            case TEACHER -> {}
            case SHOPKEEPER -> Shop.open(p, false);
            case CROUPIER, DEALER -> openCasino(p, "hall"); // игровой стол: выбор игры
            case BANKER -> LifeNet.send(p, "open_phone", Jobs.app("bank"));
            case GUNSMITH -> Shop.openGuns(p);
            case MECHANIC -> com.yompix.novograd.gta.Garage.open(p);
            case TAILOR -> Style.open(p, "cheap");
            case BOUTIQUE -> Style.open(p, "lux");
            case BARBER -> Style.open(p, "barber");
            case COACH -> Style.open(p, "gym");
            case POLICE -> p.sendMessage(Text.literal(c.getName().getString() + ": «Проходите, гражданин. Не нарушайте.»").formatted(Formatting.BLUE), false);
            case GANG -> p.sendMessage(Text.literal(c.getName().getString() + ": «Чего уставился? Это наш район.»").formatted(Formatting.DARK_GREEN), false);
            default -> {
                LifeData ld = Life.get(p);
                if (++ld.talks >= 20) Achievements.give(p, "friend");
                ld.mood = Math.min(100, ld.mood + 1);
                if (c.asleep()) {
                    p.sendMessage(Text.literal("Тсс… житель спит.").formatted(Formatting.GRAY), true);
                    return;
                }
                // голос прохожего: детский, взрослый или пожилой; пол и тембр — свои у каждого жителя
                String group = c.getScale() < 0.85f ? "kid" : (c.getSkin() == 7 || c.getSkin() == 13) ? "old" : "adult";
                Phrase ph = pick(c, p, group);
                String sex = CitizenEntity.isSlim(c.getSkin()) ? "f" : "m";
                int v = 1 + (c.getUuid().hashCode() & 1);
                net.minecraft.nbt.NbtCompound vn = new net.minecraft.nbt.NbtCompound();
                vn.putString("voice", ""); // прохожие говорят только текстом (озвучены лишь сюжетные герои)
                vn.putInt("entity", c.getId());
                LifeNet.send(p, "ambient", vn);
                c.getLookControl().lookAt(p, 30, 30);
                c.lockActivity(null);
                String name = c.hasCustomName() ? c.getCustomName().getString() : "Житель";
                p.sendMessage(Text.literal(name + ": ").formatted(Formatting.YELLOW).append(Text.literal(ph.text()).formatted(Formatting.WHITE)), false);
            }
        }
    }
}
