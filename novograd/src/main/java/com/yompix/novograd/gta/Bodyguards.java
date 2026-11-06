package com.yompix.novograd.gta;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.Novograd;
import com.yompix.novograd.entity.BodyguardEntity;
import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.life.Life;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.PersistentState;

import java.util.*;

/**
 * Телохранители — охранное агентство «Щит» (телефон → «Охрана»), с акта III.
 * До трёх человек: охранник (пистолет), боец (ПП), ветеран спецназа (карабин, крепче всех).
 * Плата — каждые игровые сутки, как у оружейника: сначала грязными наличными, потом с карты; не хватило — охрана уходит.
 * Наём хранится в мире (novograd_guards); сами охранники в мир не сохраняются — появляются возле нанимателя, когда он в игре,
 * и догоняют его, если сильно отстали. Погиб — место освобождается, можно нанять снова.
 */
public final class Bodyguards {
    private Bodyguards() {}

    public static final String[] TIER_NAMES = {"Охранник", "Боец", "Ветеран спецназа"};
    public static final String[] TIER_INFO = {"Пистолет, 30 HP", "Пистолет-пулемёт, 40 HP, очередями", "Карабин, 60 HP, бронежилет"};
    public static final int[] TIER_FEE = {500, 1000, 1800};
    public static final int[] TIER_HP = {30, 40, 60};
    public static final float[] TIER_DMG = {3f, 2.6f, 4.2f};
    public static final int MAX = 3, ACT = 2;

    public static Item tierGun(int tier) {
        return tier == 0 ? ModContent.PISTOL : tier == 1 ? ModContent.SMG : Armory.CARBINE;
    }

    // ---------------------------------------------------------------- хранение
    static final class Hire {
        int tier, skin;
        String name;
        long paidDay;
        boolean stay;
        transient UUID entity;
    }

    public static final class State extends PersistentState {
        final Map<UUID, List<Hire>> hires = new HashMap<>();
        static final Type<State> TYPE = new Type<>(State::new, (n, reg) -> load(n), null);

        static State load(NbtCompound n) {
            State s = new State();
            for (String k : n.getKeys()) {
                List<Hire> l = new ArrayList<>();
                NbtList list = n.getList(k, NbtElement.COMPOUND_TYPE);
                for (int i = 0; i < list.size(); i++) {
                    NbtCompound h = list.getCompound(i);
                    Hire x = new Hire();
                    x.tier = Math.max(0, Math.min(2, h.getInt("tier")));
                    x.skin = h.getInt("skin");
                    x.name = h.getString("name");
                    x.paidDay = h.getLong("paid");
                    x.stay = h.getBoolean("stay");
                    l.add(x);
                }
                try {
                    if (!l.isEmpty()) s.hires.put(UUID.fromString(k), l);
                } catch (IllegalArgumentException ignored) {}
            }
            return s;
        }

        @Override
        public NbtCompound writeNbt(NbtCompound n, net.minecraft.registry.RegistryWrapper.WrapperLookup reg) {
            hires.forEach((u, l) -> {
                NbtList list = new NbtList();
                for (Hire x : l) {
                    NbtCompound h = new NbtCompound();
                    h.putInt("tier", x.tier);
                    h.putInt("skin", x.skin);
                    h.putString("name", x.name);
                    h.putLong("paid", x.paidDay);
                    h.putBoolean("stay", x.stay);
                    list.add(h);
                }
                if (!list.isEmpty()) n.put(u.toString(), list);
            });
            return n;
        }
    }

    static State state(MinecraftServer s) {
        return s.getOverworld().getPersistentStateManager().getOrCreate(State.TYPE, "novograd_guards");
    }

    static List<Hire> list(ServerPlayerEntity p) {
        return state(p.getServer()).hires.computeIfAbsent(p.getUuid(), u -> new ArrayList<>());
    }

    // ---------------------------------------------------------------- сеть: окно агентства
    /** Сервер → клиент: состояние агентства для окна «Охрана». */
    public record Info(NbtCompound data) implements CustomPayload {
        public static final Id<Info> ID = new Id<>(Novograd.id("guards"));
        public static final PacketCodec<RegistryByteBuf, Info> CODEC = PacketCodec.tuple(PacketCodecs.NBT_COMPOUND, Info::data, Info::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(Info.ID, Info.CODEC);
        ServerTickEvents.END_SERVER_TICK.register(Bodyguards::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((h, s) -> s.execute(() -> despawn(h.player)));
    }

    /** Arsenal.C2S: guard_info, guard_hire (value — класс), guard_fire (value — номер), guard_stay. */
    public static void handle(ServerPlayerEntity p, String action, int value) {
        switch (action) {
            case "guard_hire" -> hire(p, value);
            case "guard_fire" -> fire(p, value, "Ты отпустил охранника");
            case "guard_stay" -> toggleStay(p);
            default -> {}
        }
        sendInfo(p);
    }

    public static void sendInfo(ServerPlayerEntity p) {
        NbtCompound n = new NbtCompound();
        n.putInt("act", Economy.act(Life.get(p)));
        NbtList l = new NbtList();
        for (Hire h : list(p)) {
            NbtCompound x = new NbtCompound();
            x.putInt("tier", h.tier);
            x.putString("name", h.name);
            x.putBoolean("stay", h.stay);
            var e = entity(p, h);
            x.putFloat("hp", e == null ? 1f : e.getHealth() / e.getMaxHealth());
            l.add(x);
        }
        n.put("list", l);
        ServerPlayNetworking.send(p, new Info(n));
    }

    static long day(ServerWorld w) {
        return w.getTimeOfDay() / 24000L;
    }

    public static void hire(ServerPlayerEntity p, int tier) {
        if (tier < 0 || tier > 2) return;
        List<Hire> l = list(p);
        if (Economy.act(Life.get(p)) < ACT) {
            Life.message(p, "Агентство «Щит»", "Мы работаем только с теми, у кого есть имя в городе. Позвоните, когда о вас заговорят (акт III).");
            return;
        }
        if (l.size() >= MAX) {
            p.sendMessage(Text.literal("Больше трёх телохранителей агентство не даёт").formatted(Formatting.RED), true);
            return;
        }
        if (!Economy.payBlack(p, TIER_FEE[tier], "охрана «Щит»")) return;
        Hire h = new Hire();
        h.tier = tier;
        var r = p.getRandom();
        h.skin = CitizenEntity.randomSkin(r);
        h.name = TIER_NAMES[tier].split(" ")[0] + " " + com.yompix.novograd.life.Names.full(r);
        h.paidDay = day(p.getServerWorld());
        l.add(h);
        state(p.getServer()).markDirty();
        spawn(p, h, l.size() - 1, true);
        Activities.title(p, "ОХРАНА НАНЯТА", h.name + " · $" + TIER_FEE[tier] + " в сутки");
    }

    static void fire(ServerPlayerEntity p, int index, String why) {
        List<Hire> l = list(p);
        if (index < 0 || index >= l.size()) return;
        Hire h = l.remove(index);
        var e = entity(p, h);
        if (e != null) e.discard();
        state(p.getServer()).markDirty();
        p.sendMessage(Text.literal(why + ": " + h.name).formatted(Formatting.GRAY), true);
        renumber(p);
    }

    public static void toggleStay(ServerPlayerEntity p) {
        List<Hire> l = list(p);
        if (l.isEmpty()) return;
        boolean stay = !l.get(0).stay;
        for (Hire h : l) {
            h.stay = stay;
            var e = entity(p, h);
            if (e != null) e.setStay(stay);
        }
        state(p.getServer()).markDirty();
        p.sendMessage(Text.literal(stay ? "Охрана: «Ждём здесь, босс»" : "Охрана: «Идём за вами»").formatted(Formatting.AQUA), true);
    }

    // ---------------------------------------------------------------- охранники в мире
    static BodyguardEntity entity(ServerPlayerEntity p, Hire h) {
        if (h.entity == null) return null;
        var e = p.getServerWorld().getEntity(h.entity);
        return e instanceof BodyguardEntity b && b.isAlive() ? b : null;
    }

    /** Охранник ещё нанят (иначе исчезает сам). */
    public static boolean isActive(BodyguardEntity b) {
        if (b.owner() == null || !(b.getWorld() instanceof ServerWorld w)) return false;
        ServerPlayerEntity p = w.getServer().getPlayerManager().getPlayer(b.owner());
        if (p == null || p.getServerWorld() != w) return false;
        for (Hire h : list(p)) if (b.getUuid().equals(h.entity)) return true;
        return false;
    }

    static void spawn(ServerPlayerEntity p, Hire h, int slot, boolean arrive) {
        ServerWorld w = p.getServerWorld();
        BodyguardEntity b = Armory.BODYGUARD.create(w);
        if (b == null) return;
        Vec3d at = spot(w, p, arrive ? 18 : 3);
        b.refreshPositionAndAngles(at.x, at.y, at.z, p.getYaw(), 0);
        b.setup(h.skin, CitizenEntity.Role.CITIZEN, false, h.name);
        b.hire(p.getUuid(), h.tier, slot);
        b.setStay(h.stay);
        w.spawnEntity(b);
        h.entity = b.getUuid();
        if (p.getVehicle() != null) b.startRiding(p.getVehicle(), true);
    }

    /** Точка на земле рядом с игроком (сзади, на distance блоков), не в воде. */
    static Vec3d spot(ServerWorld w, ServerPlayerEntity p, double dist) {
        Vec3d back = p.getRotationVector().multiply(-1, 0, -1);
        if (back.lengthSquared() < 1e-4) back = new Vec3d(1, 0, 0);
        back = back.normalize();
        for (int i = 0; i < 8; i++) {
            double a = (i - 4) * 0.35;
            Vec3d d = back.rotateY((float) a).multiply(dist);
            int x = (int) Math.floor(p.getX() + d.x), z = (int) Math.floor(p.getZ() + d.z);
            if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
            int y = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
            // под крышей (игрок в здании) — ставим рядом на его уровне
            if (y > p.getY() + 4) y = (int) Math.floor(p.getY());
            BlockPos bp = new BlockPos(x, y, z);
            if (w.getFluidState(bp.down()).isEmpty() && w.isSpaceEmpty(Armory.BODYGUARD.getDimensions().getBoxAt(x + 0.5, y, z + 0.5)))
                return new Vec3d(x + 0.5, y, z + 0.5);
        }
        return p.getPos();
    }

    /** Охранник совсем отстал (хозяин уехал/телепорт) — подходит «из-за угла» рядом. */
    public static void catchUp(BodyguardEntity b, ServerPlayerEntity p) {
        Vec3d at = spot(p.getServerWorld(), p, 6);
        b.getNavigation().stop();
        b.refreshPositionAndAngles(at.x, at.y, at.z, b.getYaw(), 0);
    }

    public static void onDeath(BodyguardEntity b, DamageSource src) {
        if (b.owner() == null || !(b.getWorld() instanceof ServerWorld w)) return;
        ServerPlayerEntity p = w.getServer().getPlayerManager().getPlayer(b.owner());
        if (p == null) return;
        List<Hire> l = list(p);
        for (int i = 0; i < l.size(); i++)
            if (b.getUuid().equals(l.get(i).entity)) {
                Hire h = l.remove(i);
                state(p.getServer()).markDirty();
                Activities.title(p, "ТЕЛОХРАНИТЕЛЬ ПОГИБ", h.name + " — агентство «Щит» пришлёт замену за плату");
                renumber(p);
                break;
            }
    }

    static void renumber(ServerPlayerEntity p) {
        List<Hire> l = list(p);
        for (int i = 0; i < l.size(); i++) {
            var e = entity(p, l.get(i));
            if (e != null) e.hire(p.getUuid(), l.get(i).tier, i);
        }
    }

    static void despawn(ServerPlayerEntity p) {
        if (p.getServer() == null) return;
        for (Hire h : list(p)) {
            var e = entity(p, h);
            if (e != null) e.discard();
            h.entity = null;
        }
    }

    /** Раз в секунду: охранники при нанимателе, плата за сутки. */
    private static void tick(MinecraftServer s) {
        if (s.getTicks() % 20 != 0) return;
        State st = state(s);
        for (ServerPlayerEntity p : s.getPlayerManager().getPlayerList()) {
            List<Hire> l = st.hires.get(p.getUuid());
            if (l == null || l.isEmpty()) continue;
            if (p.isSpectator() || !p.isAlive()) continue;
            ServerWorld w = p.getServerWorld();
            long today = day(w);
            for (int i = l.size() - 1; i >= 0; i--) {
                Hire h = l.get(i);
                if (h.paidDay < today) {
                    if (Economy.payBlack(p, TIER_FEE[h.tier], "охрана «Щит» за сутки")) {
                        h.paidDay = today;
                        p.sendMessage(Text.literal("Агентство «Щит»: −$" + TIER_FEE[h.tier] + " за сутки охраны (" + h.name + ")").formatted(Formatting.GRAY), false);
                    } else {
                        fire(p, i, "Нечем платить — охрана ушла");
                        continue;
                    }
                    st.markDirty();
                }
                BodyguardEntity e = entity(p, h);
                if (e == null) spawn(p, h, i, false);
                else if (e.squaredDistanceTo(p) > 96 * 96) catchUp(e, p);
            }
        }
    }
}
