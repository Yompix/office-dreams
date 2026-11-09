package com.yompix.novograd.gta;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.life.Appearance;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.life.LifeData;
import com.yompix.novograd.life.StyleCatalog;
import com.yompix.novograd.world.Island;
import com.yompix.novograd.world.IslandChunkGenerator;
import com.yompix.novograd.world.TownState;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtFloat;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/**
 * Маски и смена образа. Ларёк «Маски и шмот» у «Шмот-Базара»: маски (балаклава, хоккейная, клоун, свинья, череп, платок)
 * и «сменный комплект» одежды. В маске прохожие не узнают тебя при преступлении (если рядом нет полиции).
 * Сменил одежду и/или маску, пока полиция тебя не видит, — минус 1–2 звезды (как перекраска машины в «Нитро-Гараже»).
 * K — надеть/снять маску. Маски видят все игроки (сервер рассылает «mask»).
 */
public final class Disguise {
    private Disguise() {}

    public record Mask(String id, String name, int price, int color, String desc) {}

    public static final List<Mask> MASKS = List.of(
            new Mask("balaclava", "Балаклава", 120, 0x1A1A1C, "Классика налётчика: видны только глаза."),
            new Mask("hockey", "Хоккейная маска", 90, 0xEDEDE6, "Белая, с дырочками. Пугает прохожих."),
            new Mask("clown", "Маска клоуна", 150, 0xF2F0EC, "Красный нос, весёлая улыбка — смешно, пока не страшно."),
            new Mask("pig", "Маска свиньи", 110, 0xF2A6B4, "Розовая, с пятачком. Для дерзких дел."),
            new Mask("skull", "Маска «Череп»", 200, 0xE8E4D8, "Кость и тень. Для ночных дел."),
            new Mask("scarf", "Платок на лицо", 40, 0xB71C1C, "Закрывает нос и рот — дёшево и сердито."));

    public static final String SHOP = "Маски и шмот";
    public static final int OUTFIT_PRICE = 150;
    /** Ларёк: на углу участка справа от «Шмот-Базара» (вход у x=−240, z=125), прилавком к тротуару (z=125..127). */
    public static final int SHOP_X = -226, SHOP_Z = 128;

    @org.jetbrains.annotations.Nullable
    public static Mask mask(String id) {
        for (Mask m : MASKS) if (m.id().equals(id)) return m;
        return null;
    }

    /** Надета ли маска (id или ""). */
    public static String wearing(ServerPlayerEntity p) {
        for (String f : Life.get(p).flags) if (f.startsWith("mask=")) return f.substring(5);
        return "";
    }

    private static boolean owns(LifeData d, String id) {
        return d.flags.contains("maskown:" + id);
    }

    /** Преступление в маске: прохожие не запомнили лицо (полиция рядом — всё равно звёзды). */
    public static boolean unnoticed(ServerPlayerEntity p, boolean policeNear) {
        if (wearing(p).isEmpty() || policeNear) return false;
        boolean ok = p.getRandom().nextFloat() < 0.65f;
        if (ok && p.getRandom().nextInt(3) == 0) p.sendMessage(Text.literal("В маске тебя не узнали").formatted(Formatting.GRAY), true);
        return ok;
    }

    // ------------------------------------------------------------------ маска на лице
    private static void setMask(ServerPlayerEntity p, String id) {
        LifeData d = Life.get(p);
        d.flags.removeIf(f -> f.startsWith("mask="));
        if (!id.isEmpty()) {
            d.flags.add("mask=" + id);
            d.flags.removeIf(f -> f.startsWith("masklast="));
            d.flags.add("masklast=" + id);
        }
        p.getServerWorld().playSound(null, p.getBlockPos(), SoundEvents.ITEM_ARMOR_EQUIP_LEATHER.value(), SoundCategory.PLAYERS, 0.8f, id.isEmpty() ? 1.3f : 0.9f);
        broadcast(p);
    }

    public static void toggle(ServerPlayerEntity p) {
        if (Prison.inside(p)) return;
        if (!wearing(p).isEmpty()) {
            setMask(p, "");
            p.sendMessage(Text.literal("Маска снята").formatted(Formatting.GRAY), true);
            return;
        }
        LifeData d = Life.get(p);
        String last = "";
        for (String f : d.flags) if (f.startsWith("masklast=")) last = f.substring(9);
        if (last.isEmpty() || !owns(d, last)) for (Mask m : MASKS) if (owns(d, m.id())) last = m.id();
        if (last.isEmpty() || !owns(d, last)) {
            p.sendMessage(Text.literal("Масок нет — купи в ларьке «" + SHOP + "» у «Шмот-Базара»").formatted(Formatting.YELLOW), true);
            return;
        }
        setMask(p, last);
        p.sendMessage(Text.literal("Надета: " + mask(last).name() + " (K — снять)").formatted(Formatting.GRAY), true);
    }

    private static NbtCompound entry(ServerPlayerEntity p) {
        NbtCompound n = new NbtCompound();
        n.putUuid("id", p.getUuid());
        n.putString("mask", wearing(p));
        return n;
    }

    public static void broadcast(ServerPlayerEntity p) {
        NbtCompound e = entry(p);
        for (ServerPlayerEntity o : p.getServer().getPlayerManager().getPlayerList()) CopsNet.send(o, "mask", e);
    }

    public static void onJoin(ServerPlayerEntity p) {
        for (ServerPlayerEntity o : p.getServer().getPlayerManager().getPlayerList()) if (o != p) CopsNet.send(p, "mask", entry(o));
        broadcast(p);
    }

    // ------------------------------------------------------------------ смена образа сбрасывает звёзды
    private static final class Snap {
        String look;
        int reduced;
    }

    private static final Map<UUID, Snap> SNAP = new HashMap<>();

    private static String lookKey(ServerPlayerEntity p) {
        Appearance a = Life.get(p).look;
        return a.top + "|" + a.pants + "|" + a.shoes + "|" + a.hat + "|" + a.glasses + "|" + a.hair + "|" + a.hairColor + "|" + wearing(p);
    }

    private static final Map<UUID, Boolean> INSIDE = new HashMap<>();

    public static void tick(MinecraftServer server) {
        ServerWorld w = server.getOverworld();
        boolean island = w.getChunkManager().getChunkGenerator() instanceof IslandChunkGenerator;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            int stars = Wanted.stars(p);
            if (stars == 0) SNAP.remove(p.getUuid());
            else {
                String key = lookKey(p);
                Snap s = SNAP.get(p.getUuid());
                if (s == null) {
                    s = new Snap();
                    s.look = key;
                    SNAP.put(p.getUuid(), s);
                } else if (Wanted.seen(p)) s.look = key; // полиция видит, как ты выглядишь сейчас
                else if (!key.equals(s.look) && s.reduced < 2) {
                    String[] was = s.look.split("\\|", -1), now = key.split("\\|", -1);
                    int clothes = 0;
                    for (int i = 0; i < 7 && i < was.length && i < now.length; i++) if (!was[i].equals(now[i])) clothes++;
                    boolean maskChanged = !was[was.length - 1].equals(now[now.length - 1]);
                    int n = (maskChanged ? 1 : 0) + (clothes >= 2 ? 1 : 0);
                    n = Math.min(n, 2 - s.reduced);
                    if (n > 0) {
                        s.reduced += n;
                        s.look = key;
                        p.sendMessage(Text.literal("Новый образ — полиция ищет другого человека: −" + n + "★").formatted(Formatting.GREEN), false);
                        Wanted.reduce(p, n);
                    }
                }
            }
            // ларёк: подошёл к прилавку — меню
            if (island && p.getServerWorld() == w) {
                boolean in = Math.abs(p.getX() - (SHOP_X + 0.5)) < 1.8 && Math.abs(p.getZ() - (SHOP_Z - 1 + 0.5)) < 1.6 && !p.hasVehicle();
                boolean was = INSIDE.getOrDefault(p.getUuid(), false);
                INSIDE.put(p.getUuid(), in);
                if (in && !was) menu(p);
                if (p.squaredDistanceTo(SHOP_X, p.getY(), SHOP_Z) < 96 * 96) ensureBuilt(w);
            }
        }
    }

    // ------------------------------------------------------------------ ларёк
    public static void menu(ServerPlayerEntity p) {
        LifeData d = Life.get(p);
        String on = wearing(p);
        CopsNet.Menu m = new CopsNet.Menu("masks", SHOP, 0xFFFFC428)
                .line("Маска прячет лицо: в ней прохожие не узнают тебя (полицию не обманешь).")
                .line("Сменил одежду или маску вне поля зрения полиции — минус 1–2 звезды.")
                .line("K — надеть/снять маску где угодно.  У тебя: $" + d.money + (d.dirty > 0 ? " · грязными $" + d.dirty : ""));
        for (Mask mk : MASKS) {
            boolean own = owns(d, mk.id());
            if (on.equals(mk.id())) m.button("off", "Снять: " + mk.name(), 0xFF8E8E93, true);
            else if (own) m.button("wear:" + mk.id(), "Надеть: " + mk.name(), 0xFF2FA84F, true);
            else m.button("buy:" + mk.id(), mk.name() + " — $" + mk.price(), 0xFFFF2E88, d.money + d.dirty >= mk.price());
        }
        m.button("outfit", "Сменный комплект одежды — $" + OUTFIT_PRICE, 0xFF28E6FF, d.money + d.dirty >= OUTFIT_PRICE);
        m.open(p);
    }

    public static void click(ServerPlayerEntity p, String id) {
        if (p.squaredDistanceTo(SHOP_X + 0.5, p.getY(), SHOP_Z - 0.5) > 8 * 8 && !id.equals("off") && !id.startsWith("wear:")) {
            p.sendMessage(Text.literal("Подойди к ларьку «" + SHOP + "»").formatted(Formatting.YELLOW), true);
            return;
        }
        LifeData d = Life.get(p);
        if (id.equals("off")) setMask(p, "");
        else if (id.startsWith("wear:")) {
            String m = id.substring(5);
            if (owns(d, m)) setMask(p, m);
        } else if (id.startsWith("buy:")) {
            Mask m = mask(id.substring(4));
            if (m == null || owns(d, m.id())) return;
            if (!Economy.payBlack(p, m.price(), m.name())) return; // наличными, грязные — в первую очередь (продавец вопросов не задаёт)
            d.flags.add("maskown:" + m.id());
            Life.sound(p, ModContent.CASH, 1.2f);
            setMask(p, m.id());
        } else if (id.equals("outfit")) {
            if (!Economy.payBlack(p, OUTFIT_PRICE, "сменный комплект")) return;
            Appearance a = d.look;
            var r = p.getRandom();
            for (StyleCatalog.Slot slot : new StyleCatalog.Slot[]{StyleCatalog.Slot.TOP, StyleCatalog.Slot.PANTS, StyleCatalog.Slot.HAT}) {
                List<StyleCatalog.Wear> l = StyleCatalog.of(slot, 0);
                String cur = a.get(slot), ref = cur;
                for (int k = 0; k < 8 && ref.equals(cur); k++) {
                    StyleCatalog.Wear w = l.get(r.nextInt(l.size()));
                    ref = w.id() + "#" + r.nextInt(w.colors().length);
                }
                a.set(slot, ref);
                if (!d.wardrobe.contains(ref)) d.wardrobe.add(ref);
            }
            a.custom = true;
            Life.sound(p, ModContent.CASH, 1.2f);
            com.yompix.novograd.life.Style.broadcast(p);
            p.getServerWorld().playSound(null, p.getBlockPos(), SoundEvents.ITEM_ARMOR_EQUIP_LEATHER.value(), SoundCategory.PLAYERS, 0.8f, 1f);
            p.sendMessage(Text.literal("Переоделся в новый комплект").formatted(Formatting.AQUA), true);
        }
        Life.sync(p);
        menu(p);
    }

    /** Ларёк строится при первом приближении: прилавок, навес, вывеска, манекен-голова в маске. */
    private static void ensureBuilt(ServerWorld w) {
        TownState t = TownState.get(w);
        if (t.storyPlaces.containsKey("built:mask_shop")) return;
        int x = SHOP_X, z = SHOP_Z;
        if (!w.isChunkLoaded(x >> 4, z >> 4)) return;
        int y = Island.surface(x, z) + 1;
        // прилавок 3 блока вдоль тротуара, за ним продавец, навес
        BlockState counter = Blocks.STRIPPED_DARK_OAK_LOG.getDefaultState().with(Properties.AXIS, Direction.Axis.X);
        for (int dx = -1; dx <= 1; dx++) {
            w.setBlockState(new BlockPos(x + dx, y, z), counter);
            for (int h = 0; h <= 3; h++) w.setBlockState(new BlockPos(x + dx, y + h, z + 1), Blocks.AIR.getDefaultState());
            w.setBlockState(new BlockPos(x + dx, y - 1, z + 1), Blocks.SPRUCE_PLANKS.getDefaultState());
            w.setBlockState(new BlockPos(x + dx, y - 1, z), Blocks.SPRUCE_PLANKS.getDefaultState());
            w.setBlockState(new BlockPos(x + dx, y + 3, z), Blocks.BLACK_WOOL.getDefaultState());
            w.setBlockState(new BlockPos(x + dx, y + 3, z - 1), (dx & 1) == 0 ? Blocks.YELLOW_CARPET.getDefaultState() : Blocks.BLACK_CARPET.getDefaultState());
            w.setBlockState(new BlockPos(x + dx, y + 3, z + 1), Blocks.BLACK_WOOL.getDefaultState());
        }
        for (int dx : new int[]{-2, 2}) for (int h = 0; h <= 3; h++) w.setBlockState(new BlockPos(x + dx, y + h, z + 1), Blocks.DARK_OAK_FENCE.getDefaultState());
        w.setBlockState(new BlockPos(x - 2, y, z), Blocks.DARK_OAK_FENCE.getDefaultState());
        w.setBlockState(new BlockPos(x + 2, y, z), Blocks.DARK_OAK_FENCE.getDefaultState());
        w.setBlockState(new BlockPos(x - 1, y + 1, z), Blocks.CARVED_PUMPKIN.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH));
        w.setBlockState(new BlockPos(x + 1, y + 1, z), Blocks.WITHER_SKELETON_SKULL.getDefaultState().with(Properties.ROTATION, 8));
        w.setBlockState(new BlockPos(x, y + 1, z), Blocks.LANTERN.getDefaultState());
        label(w, x + 0.5, y + 4.2, z + 0.5, "МАСКИ И ШМОТ");
        com.yompix.novograd.entity.CitizenEntity seller = new com.yompix.novograd.entity.CitizenEntity(ModContent.CITIZEN, w);
        seller.refreshPositionAndAngles(x + 0.5, y, z + 1.5, 180, 0);
        seller.setHeadYaw(180);
        seller.setBodyYaw(180);
        seller.setup(com.yompix.novograd.entity.CitizenEntity.RANDOM_FROM + 7, com.yompix.novograd.entity.CitizenEntity.Role.SHOPKEEPER, true, "Продавец масок Хэллоуин");
        w.spawnEntity(seller);
        t.storyPlaces.put("built:mask_shop", "1");
        t.markDirty();
    }

    static void label(ServerWorld w, double x, double y, double z, String text) {
        DisplayEntity.TextDisplayEntity d = EntityType.TEXT_DISPLAY.create(w);
        if (d == null) return;
        NbtCompound n = new NbtCompound();
        n.putString("text", Text.Serialization.toJsonString(Text.literal(text).formatted(Formatting.GOLD, Formatting.BOLD), w.getRegistryManager()));
        n.putString("billboard", "center");
        n.putInt("background", 0x99000000);
        NbtCompound tr = new NbtCompound();
        tr.put("translation", floats(0, 0, 0));
        tr.put("left_rotation", floats(0, 0, 0, 1));
        tr.put("scale", floats(1.6f, 1.6f, 1.6f));
        tr.put("right_rotation", floats(0, 0, 0, 1));
        n.put("transformation", tr);
        NbtCompound br = new NbtCompound();
        br.putInt("block", 15);
        br.putInt("sky", 15);
        n.put("brightness", br);
        d.readNbt(n);
        d.refreshPositionAndAngles(x, y, z, 0, 0);
        d.addCommandTag("novograd_cops");
        w.spawnEntity(d);
    }

    private static NbtList floats(float... v) {
        NbtList l = new NbtList();
        for (float f : v) l.add(NbtFloat.of(f));
        return l;
    }

    static Vec3d shopPos() {
        return new Vec3d(SHOP_X + 0.5, Island.surface(SHOP_X, SHOP_Z) + 1, SHOP_Z - 0.5);
    }
}
