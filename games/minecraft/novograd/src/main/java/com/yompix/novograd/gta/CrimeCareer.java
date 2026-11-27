package com.yompix.novograd.gta;

import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.life.Achievements;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.life.LifeData;
import com.yompix.novograd.life.LifeNet;
import com.yompix.novograd.story.Story;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.PersistentState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 0.9.10: криминальная карьера (телефон → «Криминал»).
 * <ul>
 *   <li><b>Авторитет</b>: любые грязные деньги (налёты, контрабанда, заказы, угоны) — опыт; 5 ступеней (шпана → вор в
 *       законе), каждая — +5 % к грязной добыче.</li>
 *   <li><b>Налёт вдвоём</b>: второй игрок рядом (из твоей банды или со стволом) — касса больше на 60 %, делится поровну,
 *       розыск — обоим.</li>
 *   <li><b>Угон на заказ</b>: в «Криминале» заказ на модель (суперкар, маслкар, лимузин…) — угони и пригони на разборку
 *       («chop_shop») за 10 минут, плата грязными — от состояния машины.</li>
 *   <li><b>Своя банда</b>: название ($2000), приглашения игрокам, авторитет банды из добычи участников; участники вместе —
 *       бонус +20 % на совместных налётах. Контрабанда на катере — «Дела» (была).</li>
 * </ul>
 */
public final class CrimeCareer {
    private CrimeCareer() {}

    public static final String[] RANKS = {"шпана", "налётчик", "бригадир", "авторитет", "вор в законе"};
    public static final int[] XP = {0, 150, 500, 1200, 3000};
    static final int[] ORDER_VARIANTS = {12, 9, 10, 13, 14, 7, 8, 16};
    static final String[] ORDER_NAMES = {"суперкар", "маслкар", "лимузин", "классику", "лоурайдер", "внедорожник", "пикап", "компакт"};
    static final int[] ORDER_PAY = {2500, 1800, 2200, 2000, 1600, 1400, 900, 600};

    public static int rank(int xp) {
        int r = 0;
        for (int i = 0; i < XP.length; i++) if (xp >= XP[i]) r = i;
        return r;
    }

    // ------------------------------------------------------------------ банды (в мире)
    static final class Gang {
        String name;
        UUID leader;
        final Set<UUID> members = new LinkedHashSet<>();
        int rep;
    }

    static final class State extends PersistentState {
        final Map<String, Gang> gangs = new HashMap<>();

        @Override
        public NbtCompound writeNbt(NbtCompound n, RegistryWrapper.WrapperLookup reg) {
            NbtList l = new NbtList();
            for (Gang g : gangs.values()) {
                NbtCompound c = new NbtCompound();
                c.putString("name", g.name);
                c.putUuid("leader", g.leader);
                c.putInt("rep", g.rep);
                NbtList m = new NbtList();
                for (UUID u : g.members) m.add(NbtString.of(u.toString()));
                c.put("m", m);
                l.add(c);
            }
            n.put("gangs", l);
            return n;
        }

        static State read(NbtCompound n, RegistryWrapper.WrapperLookup reg) {
            State s = new State();
            for (var e : n.getList("gangs", 10)) {
                NbtCompound c = (NbtCompound) e;
                Gang g = new Gang();
                g.name = c.getString("name");
                g.leader = c.getUuid("leader");
                g.rep = c.getInt("rep");
                for (var m : c.getList("m", 8)) g.members.add(UUID.fromString(m.asString()));
                s.gangs.put(g.name, g);
            }
            return s;
        }
    }

    private static final PersistentState.Type<State> TYPE = new PersistentState.Type<>(State::new, State::read, null);

    static State state(MinecraftServer s) {
        return s.getOverworld().getPersistentStateManager().getOrCreate(TYPE, "novograd_gangs");
    }

    static Gang gangOf(MinecraftServer s, UUID u) {
        for (Gang g : state(s).gangs.values()) if (g.members.contains(u)) return g;
        return null;
    }

    /** 0.9.10: название банды игрока (или null) — для рации банды (gta/Voice). */
    public static String gangName(MinecraftServer s, UUID u) {
        Gang g = gangOf(s, u);
        return g == null ? null : g.name;
    }

    private static final Map<UUID, String> INVITES = new HashMap<>();

    // ------------------------------------------------------------------ опыт
    /** Economy.dirty: опыт и надбавка за авторитет (возвращает итоговую сумму). */
    public static int onDirty(ServerPlayerEntity p, int amount) {
        if (amount <= 0) return amount;
        LifeData d = Life.get(p);
        int before = d.city.getInt("crimeXp");
        int r = rank(before);
        int gain = Math.min(120, 5 + amount / 25);
        d.city.putInt("crimeXp", before + gain);
        int nr = rank(before + gain);
        if (nr > r) {
            FunUtil.title(p, "АВТОРИТЕТ", RANKS[nr] + " · добыча +" + nr * 5 + "%");
            if (nr == 4) Achievements.give(p, "crime_top");
        }
        Gang g = gangOf(p.getServer(), p.getUuid());
        if (g != null) {
            g.rep += amount / 50;
            state(p.getServer()).markDirty();
        }
        return Math.round(amount * (1 + r * 0.05f));
    }

    // ------------------------------------------------------------------ налёт вдвоём
    /** Heists: касса взята — делим с напарником, если он рядом. */
    public static void robbery(ServerPlayerEntity p, int pay) {
        List<ServerPlayerEntity> crew = new ArrayList<>();
        crew.add(p);
        Gang g = gangOf(p.getServer(), p.getUuid());
        for (ServerPlayerEntity o : p.getServerWorld().getPlayers())
            if (o != p && o.squaredDistanceTo(p) < 15 * 15 && !Duty.on(o)
                    && (Weapons.isGun(o.getMainHandStack()) || g != null && g.members.contains(o.getUuid()))) crew.add(o);
        if (crew.size() == 1) {
            Economy.dirty(p, pay, "налёт на магазин");
            return;
        }
        boolean gang = g != null && crew.stream().allMatch(o -> g.members.contains(o.getUuid()));
        int total = (int) (pay * (1.6 + (gang ? 0.2 : 0))), share = total / crew.size();
        for (ServerPlayerEntity o : crew) {
            Economy.dirty(o, share, "налёт вдвоём");
            Activities.title(o, "НАЛЁТ ВМЕСТЕ", "доля $" + share + (gang ? " · банда «" + g.name + "»" : ""));
            if (o != p) Wanted.add(o, 0, "соучастие в налёте", 2);
            Achievements.give(o, "crew_holdup");
        }
    }

    // ------------------------------------------------------------------ угон на заказ
    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(CrimeCareer::tick);
        Achievements.ALL.putIfAbsent("crime_top", new String[]{"Вор в законе", "Высший криминальный авторитет"});
        Achievements.ALL.putIfAbsent("crew_holdup", new String[]{"Напарники", "Налёт на магазин вдвоём"});
        Achievements.ALL.putIfAbsent("theft_order", new String[]{"Под заказ", "Пригнать машину на разборку"});
        Achievements.ALL.putIfAbsent("gang_boss", new String[]{"Своя банда", "Основать банду"});
    }

    private static void tick(MinecraftServer s) {
        if (s.getTicks() % 20 != 11) return;
        ServerWorld w = s.getOverworld();
        Vec3d chop = Story.place(w, "chop_shop");
        if (chop == null) return;
        long now = w.getTime();
        for (ServerPlayerEntity p : s.getPlayerManager().getPlayerList()) {
            LifeData d = Life.get(p);
            if (!d.city.contains("order")) continue;
            long dl = d.city.getLong("orderEnd");
            if (now > dl) {
                d.city.remove("order");
                Life.message(p, "Заказчик", "Время вышло. Заказ отдали другому.");
                continue;
            }
            if (!(p.getVehicle() instanceof CarEntity car) || car.getControllingPassenger() != p) continue;
            int i = d.city.getInt("order");
            if (car.getVariant() != ORDER_VARIANTS[i] || p.getUuid().equals(car.getOwner()) || car.squaredDistanceTo(chop) > 14 * 14 || !car.slow()) continue;
            int pay = (int) (ORDER_PAY[i] * Math.max(0.3, car.health() / 100.0));
            p.stopRiding();
            car.discard();
            d.city.remove("order");
            Economy.dirty(p, pay, "угон на заказ: " + ORDER_NAMES[i]);
            Activities.title(p, "ЗАКАЗ ВЫПОЛНЕН", ORDER_NAMES[i] + " · $" + pay + " грязными");
            Achievements.give(p, "theft_order");
        }
    }

    private static void order(ServerPlayerEntity p) {
        LifeData d = Life.get(p);
        if (d.city.contains("order")) return;
        int r = rank(d.city.getInt("crimeXp"));
        int i = p.getRandom().nextInt(Math.min(ORDER_VARIANTS.length, 3 + r * 2));
        i = ORDER_VARIANTS.length - 1 - i; // новичкам — попроще
        if (r >= 3) i = p.getRandom().nextInt(ORDER_VARIANTS.length);
        d.city.putInt("order", i);
        d.city.putLong("orderEnd", p.getServerWorld().getTime() + 20 * 600);
        FunUtil.target(p, Story.place(p.getServerWorld(), "chop_shop"), "РАЗБОРКА");
        Life.message(p, "Заказчик", "Нужен " + ORDER_NAMES[i] + ". Пригони на разборку за 10 минут. Плачу до $" + ORDER_PAY[i] + ".");
    }

    // ------------------------------------------------------------------ банда
    private static void create(ServerPlayerEntity p, String name) {
        name = name.replaceAll("[^\\p{L}\\p{N} \\-]", "").trim();
        if (name.length() < 3 || name.length() > 16) {
            FunUtil.hint(p, "Название банды — 3–16 букв", Formatting.GRAY);
            return;
        }
        State st = state(p.getServer());
        if (gangOf(p.getServer(), p.getUuid()) != null || st.gangs.containsKey(name)) return;
        if (!Life.pay(p, 2000, "своя банда")) return;
        Gang g = new Gang();
        g.name = name;
        g.leader = p.getUuid();
        g.members.add(p.getUuid());
        st.gangs.put(name, g);
        st.markDirty();
        Achievements.give(p, "gang_boss");
        News.post(p.getServer(), "В городе новая банда — «" + name + "». Полиция наблюдает", "КРИМИНАЛ");
    }

    private static void invite(ServerPlayerEntity p, String who) {
        Gang g = gangOf(p.getServer(), p.getUuid());
        ServerPlayerEntity o = p.getServer().getPlayerManager().getPlayer(who);
        if (g == null || !g.leader.equals(p.getUuid()) || o == null || o == p || gangOf(p.getServer(), o.getUuid()) != null || g.members.size() >= 8) return;
        INVITES.put(o.getUuid(), g.name);
        Life.message(o, p.getName().getString(), "Зову в банду «" + g.name + "». Телефон → «Криминал» → «Вступить».");
    }

    private static void accept(ServerPlayerEntity p) {
        String name = INVITES.remove(p.getUuid());
        Gang g = name == null ? null : state(p.getServer()).gangs.get(name);
        if (g == null || gangOf(p.getServer(), p.getUuid()) != null) return;
        g.members.add(p.getUuid());
        state(p.getServer()).markDirty();
        ServerPlayerEntity l = p.getServer().getPlayerManager().getPlayer(g.leader);
        if (l != null) Life.message(l, "Банда", p.getName().getString() + " теперь в «" + g.name + "».");
    }

    private static void leave(ServerPlayerEntity p) {
        State st = state(p.getServer());
        Gang g = gangOf(p.getServer(), p.getUuid());
        if (g == null) return;
        g.members.remove(p.getUuid());
        if (g.members.isEmpty()) st.gangs.remove(g.name);
        else if (g.leader.equals(p.getUuid())) g.leader = g.members.iterator().next();
        st.markDirty();
    }

    public static boolean handle(ServerPlayerEntity p, String type, NbtCompound n) {
        switch (type) {
            case "crime_state" -> {}
            case "crime_order" -> order(p);
            case "crime_gang" -> create(p, n.getString("name"));
            case "crime_invite" -> invite(p, n.getString("who"));
            case "crime_accept" -> accept(p);
            case "crime_leave" -> leave(p);
            default -> {
                return false;
            }
        }
        send(p);
        return true;
    }

    public static void send(ServerPlayerEntity p) {
        LifeData d = Life.get(p);
        NbtCompound n = new NbtCompound();
        int xp = d.city.getInt("crimeXp"), r = rank(xp);
        n.putInt("xp", xp);
        n.putInt("r", r);
        n.putString("rank", RANKS[r]);
        n.putInt("from", XP[r]);
        n.putInt("next", r < 4 ? XP[r + 1] : xp);
        if (d.city.contains("order")) {
            n.putString("order", ORDER_NAMES[d.city.getInt("order")]);
            n.putInt("orderPay", ORDER_PAY[d.city.getInt("order")]);
            n.putLong("orderLeft", Math.max(0, d.city.getLong("orderEnd") - p.getServerWorld().getTime()) / 20);
        }
        Gang g = gangOf(p.getServer(), p.getUuid());
        if (g != null) {
            n.putString("gang", g.name);
            n.putInt("gangRep", g.rep);
            n.putBoolean("leader", g.leader.equals(p.getUuid()));
            NbtList m = new NbtList();
            for (UUID u : g.members) {
                ServerPlayerEntity o = p.getServer().getPlayerManager().getPlayer(u);
                m.add(NbtString.of(o != null ? o.getName().getString() : "(не в сети)"));
            }
            n.put("members", m);
        }
        if (INVITES.containsKey(p.getUuid())) n.putString("invite", INVITES.get(p.getUuid()));
        NbtList online = new NbtList();
        for (ServerPlayerEntity o : p.getServer().getPlayerManager().getPlayerList()) if (o != p) online.add(NbtString.of(o.getName().getString()));
        n.put("online", online);
        // топ банд
        NbtList top = new NbtList();
        state(p.getServer()).gangs.values().stream().sorted((a, b) -> b.rep - a.rep).limit(5).forEach(x -> {
            NbtCompound c = new NbtCompound();
            c.putString("name", x.name);
            c.putInt("rep", x.rep);
            c.putInt("n", x.members.size());
            top.add(c);
        });
        n.put("top", top);
        LifeNet.send(p, "crime", n);
    }
}
