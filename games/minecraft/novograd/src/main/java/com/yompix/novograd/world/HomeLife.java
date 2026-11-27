package com.yompix.novograd.world;

import com.yompix.novograd.Novograd;
import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.gta.FunUtil;
import com.yompix.novograd.life.Achievements;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.life.LifeData;
import com.yompix.novograd.life.LifeNet;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtLong;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.PersistentState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 0.9.9: своя квартира — «Уют» в телефоне.
 * <ul>
 *   <li><b>Мебель</b>: каталог (диван, кресло, столы, лампы, растения, ковры, полки, телевизор, аквариум…) — покупаешь,
 *       ставишь сам только у себя дома (в остальном городе строить нельзя, NoGrief), убираешь — ломаешь ударом,
 *       предмет возвращается. Что поставлено — в мире ({@code novograd_homelife}), за хозяином.</li>
 *   <li><b>Вечеринка</b>: дома, $300 — гости приходят и танцуют 5 минут, музыка, приглашения игрокам в сети (СМС с
 *       адресом), настроение и слава.</li>
 *   <li><b>Питомец</b>: собака ($400) или кошка ($250) — ручные, ходят за тобой, «Домой» — сидят дома; по одному.</li>
 * </ul>
 * Гараж на несколько машин — уже был (world/Garage, место в жилье: {@code Listing.cars}).
 */
public final class HomeLife {
    private HomeLife() {}

    /** Каталог: id блока, название, цена. */
    public static final Object[][] FURNITURE = {
            {"minecraft:dark_oak_stairs", "Кресло", 60}, {"minecraft:white_wool", "Диванная подушка", 25}, {"minecraft:spruce_stairs", "Стул", 40},
            {"minecraft:smooth_quartz_slab", "Столешница", 35}, {"minecraft:spruce_fence", "Ножка стола", 10}, {"minecraft:bookshelf", "Книжная полка", 80},
            {"minecraft:lantern", "Лампа", 30}, {"minecraft:sea_lantern", "Светильник", 50}, {"minecraft:flowering_azalea", "Цветущий куст", 20},
            {"minecraft:flowering_azalea_leaves", "Большое растение", 45}, {"minecraft:gray_carpet", "Ковёр (серый)", 8}, {"minecraft:red_carpet", "Ковёр (красный)", 8},
            {"minecraft:light_blue_stained_glass", "Аквариум", 120}, {"novograd:tv", "Телевизор", 350}, {"minecraft:jukebox", "Музыкальный центр", 200},
            {"minecraft:chest", "Комод", 60}, {"minecraft:candle", "Свеча", 6}, {"minecraft:white_concrete", "Белая тумба", 20},
            {"minecraft:loom", "Рабочий стол", 70}};
    public static final int PARTY = 300, DOG = 400, CAT = 250;

    static final class State extends PersistentState {
        final Map<UUID, Set<Long>> placed = new HashMap<>();

        @Override
        public NbtCompound writeNbt(NbtCompound n, RegistryWrapper.WrapperLookup reg) {
            NbtList l = new NbtList();
            for (var e : placed.entrySet()) {
                NbtCompound c = new NbtCompound();
                c.putUuid("who", e.getKey());
                NbtList ps = new NbtList();
                for (long v : e.getValue()) ps.add(NbtLong.of(v));
                c.put("pos", ps);
                l.add(c);
            }
            n.put("placed", l);
            return n;
        }

        static State read(NbtCompound n, RegistryWrapper.WrapperLookup reg) {
            State s = new State();
            for (var e : n.getList("placed", 10)) {
                NbtCompound c = (NbtCompound) e;
                Set<Long> set = new HashSet<>();
                for (var v : c.getList("pos", 4)) set.add(((NbtLong) v).longValue());
                s.placed.put(c.getUuid("who"), set);
            }
            return s;
        }
    }

    private static final PersistentState.Type<State> TYPE = new PersistentState.Type<>(State::new, State::read, null);

    static State state(MinecraftServer s) {
        return s.getOverworld().getPersistentStateManager().getOrCreate(TYPE, "novograd_homelife");
    }

    public static void init() {
        net.minecraft.util.Identifier early = Novograd.id("homelife_early");
        UseBlockCallback.EVENT.addPhaseOrdering(early, Event.DEFAULT_PHASE);
        UseBlockCallback.EVENT.register(early, (player, world, hand, hit) -> {
            if (world.isClient || !(player instanceof ServerPlayerEntity p) || p.isCreative()) return ActionResult.PASS;
            ItemStack st = p.getStackInHand(hand);
            if (!isFurniture(st) || !(st.getItem() instanceof BlockItem bi)) return ActionResult.PASS;
            ItemPlacementContext ctx = new ItemPlacementContext(p, hand, st, hit);
            BlockPos at = ctx.getBlockPos();
            Housing.Listing l = Housing.here(p);
            if (l == null || !inside(l, at)) {
                p.sendMessage(Text.literal("Мебель — только у себя дома").formatted(Formatting.GRAY), true);
                return ActionResult.FAIL;
            }
            ActionResult r = bi.place(ctx);
            if (r.isAccepted()) {
                state(p.getServer()).placed.computeIfAbsent(p.getUuid(), k -> new HashSet<>()).add(at.asLong());
                state(p.getServer()).markDirty();
            }
            return r.isAccepted() ? ActionResult.SUCCESS : ActionResult.FAIL;
        });
        PlayerBlockBreakEvents.BEFORE.addPhaseOrdering(early, Event.DEFAULT_PHASE);
        PlayerBlockBreakEvents.BEFORE.register(early, (world, player, pos, st, be) -> {
            if (world.isClient || !(player instanceof ServerPlayerEntity p) || p.isCreative()) return true;
            Set<Long> mine = state(p.getServer()).placed.get(p.getUuid());
            if (mine == null || !mine.remove(pos.asLong())) return true; // не своя мебель — решает NoGrief
            state(p.getServer()).markDirty();
            Block b = st.getBlock();
            world.breakBlock(pos, false);
            ItemStack back = furniture(net.minecraft.registry.Registries.BLOCK.getId(b).toString(), null);
            if (!back.isEmpty() && !p.getInventory().insertStack(back)) p.dropItem(back, false);
            return false;
        });
        net.fabricmc.fabric.api.event.player.AttackBlockCallback.EVENT.addPhaseOrdering(early, Event.DEFAULT_PHASE);
        net.fabricmc.fabric.api.event.player.AttackBlockCallback.EVENT.register(early, (player, world, hand, pos, dir) -> {
            if (world.isClient || !(player instanceof ServerPlayerEntity p) || p.isCreative()) return ActionResult.PASS;
            Set<Long> mine = state(p.getServer()).placed.get(p.getUuid());
            if (mine == null || !mine.contains(pos.asLong())) return ActionResult.PASS;
            // своя мебель — убрать одним ударом
            mine.remove(pos.asLong());
            state(p.getServer()).markDirty();
            var st = world.getBlockState(pos);
            world.breakBlock(pos, false);
            ItemStack back = furniture(net.minecraft.registry.Registries.BLOCK.getId(st.getBlock()).toString(), null);
            if (!back.isEmpty() && !p.getInventory().insertStack(back)) p.dropItem(back, false);
            return ActionResult.SUCCESS;
        });
        if (!Boolean.getBoolean("novograd.no099")) ServerTickEvents.END_SERVER_TICK.register(HomeLife::tick); // замер «до/после» (MSPT)
        Achievements.ALL.putIfAbsent("party", new String[]{"Хозяин вечера", "Устроить вечеринку дома"});
        Achievements.ALL.putIfAbsent("pet", new String[]{"Лучший друг", "Завести питомца"});
        Achievements.ALL.putIfAbsent("decor", new String[]{"Уют", "Поставить 10 предметов мебели"});
    }

    /** Для автотеста: сколько мебели поставил игрок. */
    public static int placed(ServerPlayerEntity p) {
        Set<Long> s = state(p.getServer()).placed.get(p.getUuid());
        return s == null ? 0 : s.size();
    }

    static boolean inside(Housing.Listing l, BlockPos at) {
        double dx = at.getX() + 0.5 - l.inside.x, dz = at.getZ() + 0.5 - l.inside.z, dy = at.getY() - l.inside.y;
        double r = l.radius();
        return dx * dx + dz * dz <= r * r && dy > -1.5 && dy < 4;
    }

    public static boolean isFurniture(ItemStack st) {
        NbtComponent c = st.get(DataComponentTypes.CUSTOM_DATA);
        return c != null && c.copyNbt().getBoolean("nv_furn");
    }

    /** Предмет мебели (или пусто, если блока нет в каталоге). */
    static ItemStack furniture(String blockId, String name) {
        for (Object[] f : FURNITURE)
            if (f[0].equals(blockId)) {
                var item = net.minecraft.registry.Registries.ITEM.get(net.minecraft.util.Identifier.of(blockId));
                if (item == net.minecraft.item.Items.AIR) return ItemStack.EMPTY;
                ItemStack st = new ItemStack(item);
                NbtCompound n = new NbtCompound();
                n.putBoolean("nv_furn", true);
                st.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(n));
                st.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name != null ? name : (String) f[1]).formatted(Formatting.GOLD));
                return st;
            }
        return ItemStack.EMPTY;
    }

    // ------------------------------------------------------------------ телефон → «Уют»
    public static boolean handle(ServerPlayerEntity p, String type, NbtCompound n) {
        switch (type) {
            case "home_state" -> send(p);
            case "home_buy" -> buy(p, n.getInt("i"));
            case "home_party" -> party(p);
            case "home_pet" -> pet(p, n.getString("kind"));
            case "home_petcall" -> petCall(p, n.getBoolean("home"));
            default -> {
                return false;
            }
        }
        return true;
    }

    private static void buy(ServerPlayerEntity p, int i) {
        if (i < 0 || i >= FURNITURE.length) return;
        if (Housing.homes(p).isEmpty()) {
            p.sendMessage(Text.literal("Сначала жильё: телефон → «Дом»").formatted(Formatting.GRAY), true);
            return;
        }
        Object[] f = FURNITURE[i];
        if (!Life.pay(p, (int) f[2], "мебель: " + f[1])) return;
        ItemStack st = furniture((String) f[0], null);
        if (!p.getInventory().insertStack(st)) p.dropItem(st, false);
        int n = Life.get(p).city.getInt("furnBought") + 1;
        Life.get(p).city.putInt("furnBought", n);
        if (n >= 10) Achievements.give(p, "decor");
        send(p);
    }

    public static void send(ServerPlayerEntity p) {
        NbtCompound out = new NbtCompound();
        NbtList l = new NbtList();
        for (Object[] f : FURNITURE) {
            NbtCompound c = new NbtCompound();
            c.putString("id", (String) f[0]);
            c.putString("name", (String) f[1]);
            c.putInt("price", com.yompix.novograd.gta.Money.price((int) f[2]));
            l.add(c);
        }
        out.put("catalog", l);
        Housing.Listing here = Housing.here(p);
        out.putString("here", here == null ? "" : here.name);
        out.putInt("homes", Housing.homes(p).size());
        Set<Long> placed = state(p.getServer()).placed.get(p.getUuid());
        out.putInt("placed", placed == null ? 0 : placed.size());
        LifeData d = Life.get(p);
        out.putString("dog", d.city.getString("dogName"));
        out.putString("cat", d.city.getString("catName"));
        out.putBoolean("party", PARTIES.containsKey(p.getUuid()));
        out.putIntArray("prices", new int[]{com.yompix.novograd.gta.Money.price(PARTY), com.yompix.novograd.gta.Money.price(DOG), com.yompix.novograd.gta.Money.price(CAT)});
        LifeNet.send(p, "home_life", out);
    }

    // ------------------------------------------------------------------ вечеринка
    private static final Map<UUID, Object[]> PARTIES = new HashMap<>(); // {конец, гости, место}

    private static void party(ServerPlayerEntity p) {
        Housing.Listing l = Housing.here(p);
        if (l == null) {
            p.sendMessage(Text.literal("Вечеринка — у себя дома").formatted(Formatting.GRAY), true);
            return;
        }
        if (PARTIES.containsKey(p.getUuid()) || !Life.pay(p, PARTY, "вечеринка")) return;
        ServerWorld w = p.getServerWorld();
        List<UUID> guests = new ArrayList<>();
        CitizenEntity.Activity[] acts = {CitizenEntity.Activity.DANCE, CitizenEntity.Activity.DANCE2, CitizenEntity.Activity.LAUGH, CitizenEntity.Activity.TALK,
                CitizenEntity.Activity.DANCE3, CitizenEntity.Activity.CHEER};
        for (int i = 0; i < 7; i++) {
            double a = i * 0.9;
            Vec3d at = l.inside.add(Math.cos(a) * 2.2, 0, Math.sin(a) * 2.2);
            CitizenEntity c = FunUtil.citizen(w, at, CitizenEntity.Role.CITIZEN, null);
            c.setStationary(true);
            c.busy(acts[i % acts.length], 20 * 300, null, ItemStack.EMPTY);
            c.removeLater(20 * 310);
            guests.add(c.getUuid());
        }
        PARTIES.put(p.getUuid(), new Object[]{w.getTime() + 20 * 300, guests, l.inside});
        for (ServerPlayerEntity o : p.getServer().getPlayerManager().getPlayerList())
            if (o != p) {
                Life.message(o, p.getName().getString(), "Вечеринка у меня: «" + l.name + "». Приходи! (метка на радаре)");
                FunUtil.target(o, l.inside, "ВЕЧЕРИНКА");
            }
        Achievements.give(p, "party");
        FunUtil.title(p, "ВЕЧЕРИНКА", l.name + " · 5 минут");
        com.yompix.novograd.gta.Social.post(p.getServer(), "@" + p.getName().getString(), "у меня дома вечеринка 🎉 #новограднощью", p, 25, 6);
    }

    private static void tick(MinecraftServer s) {
        if (PARTIES.isEmpty() || s.getTicks() % 20 != 9) return;
        ServerWorld w = s.getOverworld();
        long now = w.getTime();
        for (var it = PARTIES.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            Object[] v = e.getValue();
            Vec3d at = (Vec3d) v[2];
            if (now >= (long) v[0]) {
                ServerPlayerEntity host = s.getPlayerManager().getPlayer(e.getKey());
                if (host != null) {
                    LifeData d = Life.get(host);
                    d.mood = Math.min(100, d.mood + 25);
                    Life.message(host, "Гости", "Было круто! Спасибо за вечер");
                    Life.sync(host);
                }
                it.remove();
                continue;
            }
            if (now % 80 < 20) w.playSound(null, at.x, at.y + 1, at.z, com.yompix.novograd.gta.StreetPerformers.BEAT, SoundCategory.RECORDS, 1.6f, 1f);
            // гости танцуют с тобой: игроки рядом — настроение
            for (ServerPlayerEntity pl : w.getPlayers())
                if (pl.squaredDistanceTo(at) < 10 * 10 && now % 200 < 20) Life.get(pl).mood = Math.min(100, Life.get(pl).mood + 2);
        }
    }

    // ------------------------------------------------------------------ питомцы
    private static void pet(ServerPlayerEntity p, String kind) {
        LifeData d = Life.get(p);
        boolean dog = kind.equals("dog");
        String key = dog ? "dogName" : "catName";
        if (!d.city.getString(key).isEmpty() && petEntity(p, dog) != null) {
            p.sendMessage(Text.literal(dog ? "Собака уже есть" : "Кошка уже есть").formatted(Formatting.GRAY), true);
            return;
        }
        if (!Life.pay(p, dog ? DOG : CAT, dog ? "собака" : "кошка")) return;
        ServerWorld w = p.getServerWorld();
        TameableEntity pet = dog ? EntityType.WOLF.create(w) : EntityType.CAT.create(w);
        if (pet == null) return;
        String[] names = dog ? new String[]{"Бакс", "Рекс", "Лайка", "Тайсон", "Белка"} : new String[]{"Мурка", "Барсик", "Рыжик", "Багира", "Кекс"};
        String name = names[p.getRandom().nextInt(names.length)];
        pet.refreshPositionAndAngles(p.getX() + 1, p.getY(), p.getZ(), p.getYaw(), 0);
        pet.setOwner(p);
        pet.setTamed(true, true);
        pet.setCustomName(Text.literal(name));
        pet.setPersistent();
        w.spawnEntity(pet);
        d.city.putString(key, name);
        d.city.putUuid(dog ? "dog" : "cat", pet.getUuid());
        Achievements.give(p, "pet");
        Life.message(p, "Зоомагазин", (dog ? "Собака" : "Кошка") + " «" + name + "» теперь с тобой.");
        send(p);
    }

    static TameableEntity petEntity(ServerPlayerEntity p, boolean dog) {
        LifeData d = Life.get(p);
        String key = dog ? "dog" : "cat";
        if (!d.city.containsUuid(key)) return null;
        for (ServerWorld w : p.getServer().getWorlds())
            if (w.getEntity(d.city.getUuid(key)) instanceof TameableEntity t && t.isAlive()) return t;
        return null;
    }

    /** «Ко мне» (телепорт к хозяину) или «Домой» (сидит дома). */
    private static void petCall(ServerPlayerEntity p, boolean home) {
        for (boolean dog : new boolean[]{true, false}) {
            TameableEntity t = petEntity(p, dog);
            if (t == null) continue;
            if (home) {
                var homes = Housing.homes(p);
                if (homes.isEmpty()) continue;
                Vec3d at = homes.get(0).inside;
                t.teleport(p.getServerWorld(), at.x, at.y, at.z, java.util.Set.of(), t.getYaw(), 0);
                t.setSitting(true);
                if (t instanceof WolfEntity || t instanceof CatEntity) t.setInSittingPose(true);
            } else {
                t.teleport(p.getServerWorld(), p.getX() + 1, p.getY(), p.getZ(), java.util.Set.of(), t.getYaw(), 0);
                t.setSitting(false);
                t.setInSittingPose(false);
            }
        }
        send(p);
    }
}
