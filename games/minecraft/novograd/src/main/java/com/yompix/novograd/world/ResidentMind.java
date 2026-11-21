package com.yompix.novograd.world;

import com.yompix.novograd.entity.CitizenEntity;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;

/**
 * 0.9.5 «Живой город»: характер, потребности и память жильца (world/Residents).
 * <ul>
 *   <li><b>Характер</b> — из зерна жильца: лентяй, энергичный, общительный, застенчивый, вспыльчивый, осторожный.
 *       Влияет на досуг (болтовня, пробежка, скамейка), на реакцию на игрока (дать сдачи / убежать / снимать на телефон).</li>
 *   <li><b>Потребности</b> (голод, бодрость, общение, отдых-веселье, деньги) — чистая арифметика от часа и распорядка:
 *       одинаково считаются и рядом с игроком, и «за кадром» (LOD без поиска пути) — город остаётся согласованным.</li>
 *   <li><b>Память</b>: обидел — в следующий раз боится или злится; помог — здоровается. Хранится в мире
 *       (PersistentState «novograd_minds»), старые сохранения работают — пусто, значит все нейтральны.</li>
 * </ul>
 */
public final class ResidentMind {
    private ResidentMind() {}

    public enum Trait {
        LAZY("лентяй"), ENERGETIC("энергичный"), SOCIABLE("общительный"), SHY("застенчивый"), AGGRESSIVE("вспыльчивый"), CAUTIOUS("осторожный");
        public final String title;

        Trait(String t) {
            title = t;
        }
    }

    public static final String[] NEEDS = {"Голод", "Бодрость", "Общение", "Отдых", "Деньги"};

    static Trait trait(long seed) {
        return Trait.values()[(int) Math.floorMod(seed >>> 9, 6)];
    }

    /** Есть своя машина (примерно половина жильцов). */
    static boolean hasCar(long seed) {
        return Math.floorMod(seed >>> 21, 10) < 5;
    }

    static int carVariant(long seed) {
        int[] v = {0, 1, 2, 3, 4, 7, 8, 9, 13, 16};
        return v[(int) Math.floorMod(seed >>> 27, v.length)];
    }

    private static float clamp(double v) {
        return (float) Math.max(0, Math.min(1, v));
    }

    /**
     * Потребности (1 — всё хорошо, 0 — очень нужно) в час h по распорядку. Без состояния: «за кадром» и рядом — одно и то же.
     * Еда: завтрак 7:00, обед 13:00, ужин 19:30. Сон: 23:00–6:30.
     */
    static float[] needs(long seed, double h, boolean night) {
        Trait t = trait(seed);
        double hh = night ? (h + 12) % 24 : h;
        double since = hh >= 19.5 ? hh - 19.5 : hh >= 13 ? hh - 13 : hh >= 7 ? hh - 7 : hh + 4.5;
        float hunger = clamp(1 - since / 6.5);
        double awake = hh >= 6.5 && hh < 23.2 ? hh - 6.5 : -1;
        float energy = awake < 0 ? clamp(0.3 + ((hh + 24 - 23.2) % 24) / 7.3 * 0.7)
                : clamp(1 - awake / 17 * (t == Trait.LAZY ? 1.25 : t == Trait.ENERGETIC ? 0.75 : 1));
        boolean work = hh >= 8.4 && hh < 17.5 && !(hh >= 12.5 && hh < 13.5);
        double workH = Math.max(0, Math.min(hh, 17.5) - 8.4);
        float social = work ? clamp(0.85 - workH * (t == Trait.SOCIABLE ? 0.09 : t == Trait.SHY ? 0.03 : 0.06))
                : hh >= 18 && hh < 21.7 ? clamp(0.3 + (hh - 18) * 0.2) : 0.8f;
        float fun = work ? clamp(0.8 - workH * 0.08) : hh >= 18 && hh < 21.7 ? clamp(0.35 + (hh - 18) * 0.18) : 0.7f;
        float money = clamp(0.35 + Math.min(workH, 8) * 0.07 - (hh > 18 ? (hh - 18) * 0.03 : 0) + Math.floorMod(seed >>> 33, 20) / 100.0);
        return new float[]{hunger, energy, social, fun, money};
    }

    // ------------------------------------------------------------------ память
    public static final class State extends PersistentState {
        static final Type<State> TYPE = new Type<>(State::new, State::read, DataFixTypes.LEVEL);
        /** Жилец (индекс) → отношение к игрокам: −5 (враг) … +5 (друг). */
        final Map<Integer, Integer> opinion = new HashMap<>();

        @Override
        public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
            NbtCompound o = new NbtCompound();
            for (var e : opinion.entrySet()) o.putInt(String.valueOf(e.getKey()), e.getValue());
            nbt.put("opinion", o);
            return nbt;
        }

        static State read(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
            State s = new State();
            NbtCompound o = nbt.getCompound("opinion");
            for (String k : o.getKeys()) {
                try {
                    s.opinion.put(Integer.parseInt(k), o.getInt(k));
                } catch (NumberFormatException ignored) {}
            }
            return s;
        }
    }

    static State state(MinecraftServer s) {
        return s.getOverworld().getPersistentStateManager().getOrCreate(State.TYPE, "novograd_minds");
    }

    public static int opinion(MinecraftServer s, int idx) {
        return state(s).opinion.getOrDefault(idx, 0);
    }

    static void change(MinecraftServer s, int idx, int d) {
        if (idx < 0) return;
        State st = state(s);
        int v = Math.max(-5, Math.min(5, st.opinion.getOrDefault(idx, 0) + d));
        if (v == 0) st.opinion.remove(idx);
        else st.opinion.put(idx, v);
        st.markDirty();
    }

    /** Игрок ударил жильца (Crime.hurt через Residents.onHurt): −2, свидетели-жильцы рядом −1. */
    public static void wronged(ServerPlayerEntity p, CitizenEntity victim, boolean killed) {
        if (Residents.is(victim)) change(p.getServer(), Residents.idx(victim), killed ? -5 : -2);
        for (CitizenEntity c : p.getServerWorld().getEntitiesByClass(CitizenEntity.class, victim.getBoundingBox().expand(16), c -> c != victim && Residents.is(c) && c.canSee(p)))
            change(p.getServer(), Residents.idx(c), -1);
    }

    /** Игрок заступился: ударил того, кто напал на жильца, — жилец запомнит добро. */
    public static void helped(ServerPlayerEntity p, CitizenEntity attacker) {
        if (attacker.getFoe() instanceof CitizenEntity v && Residents.is(v)) {
            change(p.getServer(), Residents.idx(v), 2);
            v.say("Спасибо!", 50);
        }
    }

    /** Поговорил по-доброму — +1, но не выше +2 (дружба — делом). */
    public static void talked(ServerPlayerEntity p, CitizenEntity c) {
        if (!Residents.is(c)) return;
        int idx = Residents.idx(c);
        if (opinion(p.getServer(), idx) < 2) change(p.getServer(), idx, 1);
    }

    /** Даст сдачи, если его ударили: по характеру (вспыльчивый — да, осторожный и застенчивый — бегут). */
    public static boolean fightsBack(CitizenEntity c) {
        if (!Residents.is(c)) return !c.isKid() && !CitizenEntity.isSlim(c.getSkin()) && c.getRandom().nextInt(3) == 0;
        Trait t = Residents.traitOf(c);
        return t == Trait.AGGRESSIVE || t == Trait.ENERGETIC && c.getRandom().nextBoolean();
    }
}
