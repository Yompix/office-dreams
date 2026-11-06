package com.yompix.novograd.life;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.Novograd;
import com.yompix.novograd.entity.CitizenEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Стиль в духе SA на стороне сервера: магазины одежды, цирюльня, гардероб и спортзал.
 * Свой канал в обе стороны («style_s2c» / «style_c2s»); внешность каждого игрока рассылается всем,
 * чтобы друзья видели твою одежду и причёску. Всё проверяется здесь: деньги, где стоишь, сколько уже накачал.
 */
public final class Style {
    private Style() {}

    public record S2C(String type, NbtCompound data) implements CustomPayload {
        public static final Id<S2C> ID = new Id<>(Novograd.id("style_s2c"));
        public static final PacketCodec<RegistryByteBuf, S2C> CODEC = PacketCodec.tuple(
                PacketCodecs.STRING, S2C::type, PacketCodecs.NBT_COMPOUND, S2C::data, S2C::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record C2S(String type, NbtCompound data) implements CustomPayload {
        public static final Id<C2S> ID = new Id<>(Novograd.id("style_c2s"));
        public static final PacketCodec<RegistryByteBuf, C2S> CODEC = PacketCodec.tuple(
                PacketCodecs.STRING, C2S::type, PacketCodecs.NBT_COMPOUND, C2S::data, C2S::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Где игрок открыл магазин/зал: вид, место, время. Покупки — только рядом и недолго после. */
    private record Session(String kind, Vec3d pos, long time) {}

    private static final Map<UUID, Session> SESSION = new HashMap<>();
    private static final Map<UUID, Long> GYM_START = new HashMap<>(), GYM_LAST = new HashMap<>();
    private static final Map<UUID, Boolean> SPRINT = new HashMap<>();
    private static final Identifier STR_ID = Novograd.id("gym_strength"), STA_ID = Novograd.id("gym_stamina"), FALL_ID = Novograd.id("gym_fall");

    public static void init() {
        PayloadTypeRegistry.playS2C().register(S2C.ID, S2C.CODEC);
        PayloadTypeRegistry.playC2S().register(C2S.ID, C2S.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(C2S.ID, (payload, ctx) -> {
            try {
                handle(ctx.player(), payload.type(), payload.data());
            } catch (RuntimeException e) {
                Novograd.LOG.warn("Стиль: ошибка {} от {}", payload.type(), ctx.player().getName().getString(), e);
            }
        });
        ServerPlayConnectionEvents.JOIN.register((h, s, server) -> server.execute(() -> onJoin(h.player)));
        ServerPlayConnectionEvents.DISCONNECT.register((h, server) -> {
            SESSION.remove(h.player.getUuid());
            SPRINT.remove(h.player.getUuid());
        });
        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register((o, n, alive) -> {
            SPRINT.remove(n.getUuid());
            apply(n, true);
        });
        ServerTickEvents.END_SERVER_TICK.register(Style::tick);
    }

    public static void send(ServerPlayerEntity p, String type, NbtCompound data) {
        if (ServerPlayNetworking.canSend(p, S2C.ID)) ServerPlayNetworking.send(p, new S2C(type, data));
    }

    // ------------------------------------------------------------------ внешность для всех
    private static void onJoin(ServerPlayerEntity p) {
        LifeData d = Life.get(p);
        if (d.wardrobe.isEmpty()) d.wardrobe.addAll(StyleCatalog.STARTER);
        NbtList list = new NbtList();
        for (ServerPlayerEntity o : p.getServer().getPlayerManager().getPlayerList()) list.add(entry(o));
        NbtCompound n = new NbtCompound();
        n.put("list", list);
        send(p, "looks", n);
        broadcast(p);
        apply(p, true);
    }

    private static NbtCompound entry(ServerPlayerEntity p) {
        LifeData d = Life.get(p);
        NbtCompound e = new NbtCompound();
        e.putUuid("id", p.getUuid());
        e.put("look", d.look.toNbt());
        e.putInt("str", d.strength);
        return e;
    }

    /** Разослать внешность игрока всем (и ему самому). */
    public static void broadcast(ServerPlayerEntity p) {
        NbtCompound e = entry(p);
        for (ServerPlayerEntity o : p.getServer().getPlayerManager().getPlayerList()) send(o, "look", e);
    }

    private static void changed(ServerPlayerEntity p) {
        Life.get(p).look.custom = true;
        Life.sync(p);
        broadcast(p);
    }

    // ------------------------------------------------------------------ магазины
    /**
     * Открыть экран: "cheap" — «Шмот-Базар», "lux" — бутик «Бархат», "barber" — цирюльня, "gym" — спортзал,
     * "wardrobe" — гардероб (бесплатно переодеться: в убежище или в магазине одежды).
     */
    public static void open(ServerPlayerEntity p, String kind) {
        if (kind.equals("wardrobe") && !wardrobeHere(p)) {
            NbtCompound n = new NbtCompound();
            n.putString("kind", "wardrobe");
            n.putBoolean("locked", true);
            send(p, "open", n);
            return;
        }
        LifeData d = Life.get(p);
        if (d.wardrobe.isEmpty()) d.wardrobe.addAll(StyleCatalog.STARTER);
        SESSION.put(p.getUuid(), new Session(kind, p.getPos(), p.getServerWorld().getTime()));
        if (kind.equals("gym")) newDay(p, d);
        Life.sync(p);
        NbtCompound n = new NbtCompound();
        n.putString("kind", kind);
        send(p, "open", n);
    }

    private static boolean wardrobeHere(ServerPlayerEntity p) {
        if (com.yompix.novograd.gta.Activities.nearOwnedSafehouse(p, 40)) return true;
        Session s = SESSION.get(p.getUuid());
        return s != null && (s.kind().equals("cheap") || s.kind().equals("lux")) && valid(p, s);
    }

    private static boolean valid(ServerPlayerEntity p, Session s) {
        return p.getPos().squaredDistanceTo(s.pos()) < 24 * 24 && p.getServerWorld().getTime() - s.time() < 20 * 60 * 15;
    }

    /** Открыт ли сейчас у игрока экран такого вида (и он всё ещё рядом). */
    private static boolean at(ServerPlayerEntity p, String... kinds) {
        Session s = SESSION.get(p.getUuid());
        if (s == null || !valid(p, s)) return false;
        for (String k : kinds) if (s.kind().equals(k)) return true;
        return false;
    }

    private static void handle(ServerPlayerEntity p, String type, NbtCompound d) {
        switch (type) {
            case "buy" -> buy(p, d.getString("ref"));
            case "wear" -> wear(p, d.getString("slot"), d.getString("ref"));
            case "barber" -> barber(p, d.getString("what"), d.getString("value"), d.getInt("tone"), d.getBoolean("slim"));
            case "wardrobe" -> open(p, "wardrobe");
            case "vanilla_skin" -> {
                if (!at(p, "wardrobe", "cheap", "lux") && !wardrobeHere(p)) return;
                Life.get(p).look.custom = d.getBoolean("custom");
                Life.sync(p);
                broadcast(p);
            }
            case "gym_start" -> gymStart(p, d.getString("ex"));
            case "gym" -> gymResult(p, d.getString("ex"), d.getInt("score"));
            default -> {}
        }
    }

    private static void buy(ServerPlayerEntity p, String ref) {
        StyleCatalog.Wear w = StyleCatalog.wear(Appearance.id(ref));
        if (w == null) return;
        int c = Math.floorMod(Appearance.colorIndex(ref), w.colors().length);
        ref = w.id() + "#" + c;
        if (!at(p, w.tier() == 0 ? "cheap" : "lux")) {
            p.sendMessage(Text.literal("Эту вещь продают в «" + (w.tier() == 0 ? StyleCatalog.CHEAP_NAME : StyleCatalog.LUX_NAME) + "»").formatted(Formatting.RED), true);
            return;
        }
        LifeData d = Life.get(p);
        if (!d.wardrobe.contains(ref)) {
            if (!Life.pay(p, w.price(), w.name())) return;
            d.wardrobe.add(ref);
            Life.sound(p, ModContent.CASH, 1.2f);
            if (d.wardrobe.size() >= 12) Achievements.give(p, "fashion");
        }
        d.look.set(w.slot(), ref);
        changed(p);
    }

    private static void wear(ServerPlayerEntity p, String slotName, String ref) {
        StyleCatalog.Slot slot;
        try {
            slot = StyleCatalog.Slot.valueOf(slotName);
        } catch (IllegalArgumentException e) {
            return;
        }
        if (!at(p, "wardrobe", "cheap", "lux") && !wardrobeHere(p)) {
            p.sendMessage(Text.literal("Переодеться можно в своём убежище или в магазине одежды").formatted(Formatting.YELLOW), true);
            return;
        }
        LifeData d = Life.get(p);
        if (ref.isEmpty()) {
            if (slot != StyleCatalog.Slot.HAT && slot != StyleCatalog.Slot.GLASSES) return; // без штанов по Новограду не ходят
        } else if (!d.wardrobe.contains(ref)) return;
        d.look.set(slot, ref);
        p.getServerWorld().playSound(null, p.getBlockPos(), SoundEvents.ITEM_ARMOR_EQUIP_LEATHER.value(), SoundCategory.PLAYERS, 0.7f, 1f);
        changed(p);
    }

    private static void barber(ServerPlayerEntity p, String what, String value, int tone, boolean slim) {
        if (!at(p, "barber")) return;
        LifeData d = Life.get(p);
        Appearance a = d.look;
        switch (what) {
            case "hair" -> {
                var o = StyleCatalog.find(StyleCatalog.HAIR, value);
                if (o == null || value.equals(a.hair) || !Life.pay(p, o.price(), "стрижка «" + o.name() + "»")) return;
                a.hair = value;
            }
            case "color" -> {
                int i = -1;
                for (int k = 0; k < StyleCatalog.HAIR_COLORS.size(); k++) if (StyleCatalog.HAIR_COLORS.get(k).id().equals(value)) i = k;
                if (i < 0 || i == a.hairColor) return;
                var o = StyleCatalog.HAIR_COLORS.get(i);
                if (!Life.pay(p, o.price(), "окрашивание: " + o.name().toLowerCase())) return;
                a.hairColor = i;
            }
            case "beard" -> {
                var o = StyleCatalog.find(StyleCatalog.BEARDS, value);
                if (o == null || value.equals(a.beard) || !Life.pay(p, o.price(), o.name())) return;
                a.beard = value;
            }
            case "look" -> {
                tone = Math.floorMod(tone, StyleCatalog.SKIN_TONES.length);
                if (a.skinChosen && tone == a.skin && slim == a.slim) return;
                if (a.skinChosen && !Life.pay(p, StyleCatalog.LOOK_PRICE, "новая внешность")) return;
                a.skin = tone;
                a.slim = slim;
                a.skinChosen = true;
            }
            default -> {
                return;
            }
        }
        p.getServerWorld().playSound(null, p.getBlockPos(), SoundEvents.ENTITY_SHEEP_SHEAR, SoundCategory.PLAYERS, 0.6f, 1.2f);
        changed(p);
    }

    // ------------------------------------------------------------------ спортзал
    private static void newDay(ServerPlayerEntity p, LifeData d) {
        long day = Life.day(p);
        if (d.gymDay != day) {
            d.gymDay = day;
            d.gymStrToday = 0;
            d.gymStaToday = 0;
        }
    }

    private static void gymStart(ServerPlayerEntity p, String ex) {
        if (!at(p, "gym")) return;
        LifeData d = Life.get(p);
        newDay(p, d);
        long now = p.getServerWorld().getTime();
        NbtCompound n = new NbtCompound();
        n.putString("ex", ex);
        boolean str = ex.equals("bench");
        int left = StyleCatalog.GYM_DAILY - (str ? d.gymStrToday : d.gymStaToday);
        int stat = str ? d.strength : d.stamina;
        String why = stat >= StyleCatalog.STAT_MAX ? "Ты на пике формы — больше не накачать"
                : left <= 0 ? "На сегодня хватит: мышцам нужен отдых. Приходи завтра"
                : now - GYM_LAST.getOrDefault(p.getUuid(), -10000L) < 100 ? "Передохни полминуты между подходами" : "";
        n.putString("deny", why);
        if (why.isEmpty()) GYM_START.put(p.getUuid(), now);
        send(p, "gym_start", n);
    }

    private static void gymResult(ServerPlayerEntity p, String ex, int score) {
        Long start = GYM_START.remove(p.getUuid());
        if (start == null || !at(p, "gym")) return;
        long now = p.getServerWorld().getTime();
        if (now - start < 20 * 6) return; // подход не бывает короче шести секунд
        GYM_LAST.put(p.getUuid(), now);
        LifeData d = Life.get(p);
        newDay(p, d);
        boolean str = ex.equals("bench");
        int gain;
        if (str) {
            score = Math.max(0, Math.min(15, score)); // повторы
            gain = score < 3 ? 0 : 1 + score / 4;
            gain = Math.min(gain, Math.min(StyleCatalog.GYM_DAILY - d.gymStrToday, StyleCatalog.STAT_MAX - d.strength));
            d.strength += Math.max(0, gain);
            d.gymStrToday += Math.max(0, gain);
        } else {
            score = Math.max(0, Math.min(100, score)); // % времени в нужном темпе
            gain = score < 30 ? 0 : 1 + score / 30;
            gain = Math.min(gain, Math.min(StyleCatalog.GYM_DAILY - d.gymStaToday, StyleCatalog.STAT_MAX - d.stamina));
            d.stamina += Math.max(0, gain);
            d.gymStaToday += Math.max(0, gain);
        }
        d.energy = Math.max(0, d.energy - 6);
        d.mood = Math.min(100, d.mood + 3);
        NbtCompound n = new NbtCompound();
        n.putString("ex", ex);
        n.putInt("gain", Math.max(0, gain));
        n.putInt("value", str ? d.strength : d.stamina);
        send(p, "gym_result", n);
        if (gain > 0) {
            com.yompix.novograd.gta.Activities.title(p, str ? "СИЛА +" + gain : "ВЫНОСЛИВОСТЬ +" + gain,
                    (str ? "Сила " + d.strength : "Выносливость " + d.stamina) + " / " + StyleCatalog.STAT_MAX);
            p.getServerWorld().playSound(null, p.getBlockPos(), ModContent.LEVEL_UP, SoundCategory.PLAYERS, 0.6f, 1.1f);
        }
        if (d.strength >= 50 && d.stamina >= 50) Achievements.give(p, "athlete");
        apply(p, true);
        Life.sync(p);
        broadcast(p);
    }

    // ------------------------------------------------------------------ польза от зала
    private static void tick(MinecraftServer server) {
        if (server.getTicks() % 4 != 0) return;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) apply(p, server.getTicks() % 100 == 0);
    }

    /**
     * Сила — сильнее удар в ближнем бою (до +3 урона). Выносливость — быстрее бег (до +12% при спринте)
     * и мягче приземление (до +3 блоков без урона). Веса и «жира» нет.
     */
    public static void apply(ServerPlayerEntity p, boolean force) {
        LifeData d = Life.get(p);
        boolean sprint = p.isSprinting() && !p.hasVehicle();
        Boolean was = SPRINT.put(p.getUuid(), sprint);
        if (!force && was != null && was == sprint) return;
        modifier(p, EntityAttributes.GENERIC_ATTACK_DAMAGE, STR_ID, d.strength * 0.03, EntityAttributeModifier.Operation.ADD_VALUE);
        modifier(p, EntityAttributes.GENERIC_MOVEMENT_SPEED, STA_ID, sprint ? d.stamina * 0.0012 : 0, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        modifier(p, EntityAttributes.GENERIC_SAFE_FALL_DISTANCE, FALL_ID, d.stamina * 0.03, EntityAttributeModifier.Operation.ADD_VALUE);
    }

    private static void modifier(ServerPlayerEntity p, RegistryEntry<EntityAttribute> attr, Identifier id, double v, EntityAttributeModifier.Operation op) {
        var inst = p.getAttributeInstance(attr);
        if (inst == null) return;
        var cur = inst.getModifier(id);
        if (cur != null && Math.abs(cur.value() - v) < 1e-6) return;
        inst.removeModifier(id);
        if (Math.abs(v) > 1e-6) inst.addTemporaryModifier(new EntityAttributeModifier(id, v, op));
    }

    /** Ближайший работник стиля нужной роли (для проверок и автотестов). */
    public static boolean near(ServerPlayerEntity p, CitizenEntity.Role role, double r) {
        return !p.getServerWorld().getEntitiesByClass(CitizenEntity.class, p.getBoundingBox().expand(r), c -> c.getRole() == role).isEmpty();
    }
}
