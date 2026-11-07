package com.yompix.novograd.gta;

import com.yompix.novograd.life.Life;
import com.yompix.novograd.life.LifeData;
import com.yompix.novograd.life.LifeNet;
import com.yompix.novograd.world.IslandChunkGenerator;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Рыбалка: обычная удочка у пирсов, на озере и в море. Что клюёт, решает таблица добычи
 * (data/minecraft/loot_table/gameplay/fishing/fish.json): в озере Ясное — карась, окунь, щука, сом; в море — скумбрия, лосось, тунец;
 * везде изредка — золотая рыбка. Вес взвешивается при поимке (в подписи), рыбу по весу берут рыбные лавки.
 * Турнир рыбаков на озере: каждый третий день с 6:00 до 11:00, считается общий вес улова, призы трём лучшим.
 */
public final class Fishing {
    private Fishing() {}

    /** Лавки: место, название. */
    static final String[][] SHOPS = {
            {"port_dock", "Рыбный рынок порта"}, {"lake_dock", "Клуб рыбаков «Ясное» · турнир"}, {"riv_marina", "Рыбная лавка марины"}};

    record Species(Item item, String name, float minKg, float maxKg, int perKg) {}

    static final List<Species> SPECIES = List.of(
            new Species(Outdoors.CRUCIAN, "Карась", 0.2f, 1.6f, 14),
            new Species(Outdoors.PERCH, "Окунь", 0.15f, 2.2f, 20),
            new Species(Outdoors.PIKE, "Щука", 1.0f, 9f, 24),
            new Species(Outdoors.CATFISH, "Сом", 4f, 40f, 16),
            new Species(Outdoors.MACKEREL, "Скумбрия", 0.3f, 1.8f, 22),
            new Species(Outdoors.SALMON, "Лосось", 1.5f, 12f, 30),
            new Species(Outdoors.TUNA, "Тунец", 8f, 90f, 18),
            new Species(Outdoors.GOLDFISH, "Золотая рыбка", 0.05f, 0.3f, 0));

    static Species species(Item it) {
        for (Species s : SPECIES) if (s.item == it) return s;
        return null;
    }

    public static int grams(ItemStack s) {
        NbtComponent c = s.get(DataComponentTypes.CUSTOM_DATA);
        return c == null ? 0 : c.copyNbt().getInt("nv_w");
    }

    /** Цена улова в лавке (0 — не рыба). */
    public static int price(ItemStack s) {
        Species sp = species(s.getItem());
        if (sp == null) return 0;
        if (sp.item == Outdoors.GOLDFISH) return 1500 * s.getCount();
        int g = grams(s);
        float kg = g > 0 ? g / 1000f : (sp.minKg + sp.maxKg) / 3;
        return Math.max(5, Math.round(kg * sp.perKg)) * s.getCount();
    }

    // ------------------------------------------------------------------ турнир
    static final String[] RIVALS = {"Дед Михалыч", "Серёга «Карась»", "Тётя Валя", "Профессор Лещёв", "Витёк с моста"};
    private static long tourDay = -1;
    private static boolean tourOn;
    private static final Map<UUID, Integer> TOUR = new HashMap<>();
    private static final Map<UUID, String> NAMES = new HashMap<>();
    private static final int[] RIVAL = new int[RIVALS.length];
    private static boolean forced;

    static boolean tourDayNow(ServerWorld w) {
        long day = w.getTimeOfDay() / 24000L + 1, tod = w.getTimeOfDay() % 24000;
        return day % 3 == 0 && tod < 5000;
    }

    static boolean atLake(ServerPlayerEntity p) {
        return Math.hypot((p.getX() - 1320) / 1.4, p.getZ() - 760) < 300;
    }

    static void tick(MinecraftServer s) {
        long t = s.getTicks();
        if (t % 10 != 1) return;
        ServerWorld w = s.getOverworld();
        for (ServerPlayerEntity p : s.getPlayerManager().getPlayerList()) weigh(p);
        if (t % 20 != 1 || !(w.getChunkManager().getChunkGenerator() instanceof IslandChunkGenerator)) return;
        boolean want = forced || tourDayNow(w);
        if (want && !tourOn) start(s, w);
        else if (!want && tourOn) finish(s);
        if (tourOn) {
            var r = w.getRandom();
            for (int i = 0; i < RIVAL.length; i++) if (r.nextFloat() < 0.035f) RIVAL[i] += 200 + r.nextInt(1800 + i * 300);
            if (t % 100 == 1) for (ServerPlayerEntity p : s.getPlayerManager().getPlayerList()) if (atLake(p)) LifeNet.send(p, "out_tour", board(p));
        }
    }

    private static void start(MinecraftServer s, ServerWorld w) {
        tourOn = true;
        tourDay = w.getTimeOfDay() / 24000L + 1;
        TOUR.clear();
        NAMES.clear();
        java.util.Arrays.fill(RIVAL, 0);
        for (ServerPlayerEntity p : s.getPlayerManager().getPlayerList())
            Life.message(p, "Клуб рыбаков", "Турнир на озере Ясное начался! До 11:00 считаем общий вес улова. Призы: $1500, $800, $400. Удочка — в клубе у пирса.");
    }

    private static void finish(MinecraftServer s) {
        tourOn = false;
        forced = false;
        List<Object[]> rows = ranking();
        int[] prize = {1500, 800, 400};
        StringBuilder top = new StringBuilder();
        for (int i = 0; i < Math.min(3, rows.size()); i++) top.append(i + 1).append(". ").append(rows.get(i)[0]).append(" — ").append(kg((Integer) rows.get(i)[1])).append("  ");
        for (ServerPlayerEntity p : s.getPlayerManager().getPlayerList()) {
            int place = -1;
            for (int i = 0; i < rows.size(); i++) if (p.getUuid().equals(rows.get(i)[2])) place = i;
            if (place >= 0) {
                if (place < 3 && (Integer) rows.get(place)[1] > 0) {
                    Life.earn(p, prize[place], "турнир рыбаков: " + (place + 1) + " место");
                    Life.get(p).skills.merge("x_tour_wins", place == 0 ? 1 : 0, Integer::sum);
                }
                Life.message(p, "Клуб рыбаков", "Турнир окончен. Ты — " + (place + 1) + " место. " + top);
            } else if (atLake(p)) Life.message(p, "Клуб рыбаков", "Турнир окончен. " + top);
        }
        TOUR.clear();
    }

    /** Таблица: имя, граммы, uuid (у соперников — null). */
    static List<Object[]> ranking() {
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < RIVALS.length; i++) rows.add(new Object[]{RIVALS[i], RIVAL[i], null});
        for (var e : TOUR.entrySet()) rows.add(new Object[]{NAMES.getOrDefault(e.getKey(), "?"), e.getValue(), e.getKey()});
        rows.sort((a, b) -> Integer.compare((Integer) b[1], (Integer) a[1]));
        return rows;
    }

    static NbtCompound board(ServerPlayerEntity p) {
        NbtCompound n = new NbtCompound();
        n.putBoolean("on", tourOn);
        n.putLong("day", tourDay);
        NbtList l = new NbtList();
        for (Object[] r : ranking()) {
            NbtCompound c = new NbtCompound();
            c.putString("name", (String) r[0]);
            c.putInt("g", (Integer) r[1]);
            c.putBoolean("me", p.getUuid().equals(r[2]));
            l.add(c);
        }
        n.put("rows", l);
        return n;
    }

    static String kg(int g) {
        return String.format(Locale.ROOT, "%.1f кг", g / 1000f).replace('.', ',');
    }

    // ------------------------------------------------------------------ улов
    /** Новая рыба в инвентаре (без веса) — взвесить, подписать, засчитать в рекорды и турнир. */
    private static void weigh(ServerPlayerEntity p) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            Species sp = species(s.getItem());
            if (sp == null || s.contains(DataComponentTypes.CUSTOM_DATA)) continue;
            var r = p.getRandom();
            float k = r.nextFloat();
            float kg = sp.minKg + (sp.maxKg - sp.minKg) * k * k * k; // крупная — редкость
            int g = Math.max(10, Math.round(kg * 1000));
            NbtCompound tag = new NbtCompound();
            tag.putInt("nv_w", g);
            s.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(tag));
            s.set(DataComponentTypes.LORE, new LoreComponent(List.of(Text.literal("Вес: " + kg(g)).formatted(Formatting.AQUA))));
            LifeData d = Life.get(p);
            String key = "x_rec_" + net.minecraft.registry.Registries.ITEM.getId(sp.item).getPath();
            boolean rec = g > d.skills.getOrDefault(key, 0);
            if (rec) d.skills.put(key, g);
            d.skills.merge("x_fish", 1, Integer::sum);
            String msg = "🎣 " + sp.name + " — " + kg(g) + (rec ? "  · личный рекорд!" : "");
            if (tourOn && atLake(p)) {
                TOUR.merge(p.getUuid(), g, Integer::sum);
                NAMES.put(p.getUuid(), p.getName().getString());
                msg += "  · в зачёт турнира";
                LifeNet.send(p, "out_tour", board(p));
            }
            p.sendMessage(Text.literal(msg).formatted(sp.item == Outdoors.GOLDFISH ? Formatting.GOLD : Formatting.AQUA), true);
            if (sp.item == Outdoors.GOLDFISH) {
                p.getServerWorld().playSound(null, p.getBlockPos(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.6f, 1.4f);
                Life.message(p, "Клуб рыбаков", "Золотая рыбка! Говорят, исполняет желания… а мы просто купим её за $1500.");
            }
        }
    }

    static NbtCompound state(ServerPlayerEntity p) {
        LifeData d = Life.get(p);
        NbtCompound n = board(p);
        n.putInt("caught", d.skills.getOrDefault("x_fish", 0));
        n.putInt("wins", d.skills.getOrDefault("x_tour_wins", 0));
        NbtList rec = new NbtList();
        for (Species sp : SPECIES) {
            NbtCompound c = new NbtCompound();
            c.putString("name", sp.name);
            c.putInt("g", d.skills.getOrDefault("x_rec_" + net.minecraft.registry.Registries.ITEM.getId(sp.item).getPath(), 0));
            c.putInt("perKg", sp.perKg);
            rec.add(c);
        }
        n.put("records", rec);
        long day = p.getServerWorld().getTimeOfDay() / 24000L + 1;
        n.putLong("nextTour", tourOn ? day : day + (3 - day % 3) % 3 + (day % 3 == 0 && p.getServerWorld().getTimeOfDay() % 24000 >= 5000 ? 3 : 0));
        return n;
    }

    static String debug(ServerPlayerEntity p, String[] a) {
        if (a[0].equals("tour")) {
            if (a.length > 1 && a[1].equals("stop")) {
                forced = false;
                if (tourOn) finish(p.getServer());
                return "Турнир окончен";
            }
            forced = true;
            if (!tourOn) start(p.getServer(), p.getServerWorld());
            return "Турнир идёт";
        }
        if (a.length > 1) {
            for (Species sp : SPECIES)
                if (net.minecraft.registry.Registries.ITEM.getId(sp.item).getPath().endsWith(a[1])) {
                    Hunting.give(p, new ItemStack(sp.item));
                    return "Поймал: " + sp.name;
                }
        }
        return "Рыбы поймано: " + Life.get(p).skills.getOrDefault("x_fish", 0) + (tourOn ? ", турнир идёт" : "");
    }
}
