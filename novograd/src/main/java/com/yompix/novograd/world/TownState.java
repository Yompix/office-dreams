package com.yompix.novograd.world;

import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.PersistentState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Что знает город: построен ли он, где школа, и все почтовые ящики (это адреса для курьеров и такси). */
public class TownState extends PersistentState {
    private static final Type<TownState> TYPE = new Type<>(TownState::new, TownState::fromNbt, DataFixTypes.LEVEL);
    private static final String[] STREETS_X = {"ул. Портовая", "ул. Центральная", "ул. Садовая", "ул. Лесная"};
    private static final String[] STREETS_Z = {"пр. Мира", "ул. Пушкина", "ул. Гагарина", "ул. Победы"};

    public boolean built;
    @Nullable public BlockPos origin, school;
    public final List<BlockPos> mailboxes = new ArrayList<>();
    /** Именованные места для сюжета: x, y, z, поворот, наклон (для камер). */
    public final java.util.Map<String, double[]> places = new java.util.HashMap<>();
    /** Герои сюжета: id → UUID жителя. */
    public final java.util.Map<String, java.util.UUID> storyNpcs = new java.util.HashMap<>();
    /** Где по сюжету стоит каждый герой (имя места). */
    public final java.util.Map<String, String> storyPlaces = new java.util.HashMap<>();

    public void place(String name, double x, double y, double z, float yaw, float pitch) {
        places.put(name, new double[]{x, y, z, yaw, pitch});
        markDirty();
    }

    @Nullable
    public double[] place(String name) {
        return places.get(name);
    }

    public static TownState get(ServerWorld world) {
        return world.getServer().getOverworld().getPersistentStateManager().getOrCreate(TYPE, "novograd_town");
    }

    public void addMailbox(BlockPos pos) {
        if (!mailboxes.contains(pos)) {
            mailboxes.add(pos.toImmutable());
            markDirty();
        }
    }

    public void removeMailbox(BlockPos pos) {
        if (mailboxes.remove(pos)) markDirty();
    }

    @Nullable
    public BlockPos randomMailbox(Random r, BlockPos from, int minDist) {
        List<BlockPos> far = new ArrayList<>();
        for (BlockPos b : mailboxes) if (b.getSquaredDistance(from) >= minDist * minDist) far.add(b);
        return far.isEmpty() ? null : far.get(r.nextInt(far.size()));
    }

    /** Человеческий адрес: улица по ближайшей дороге и номер дома. */
    public String address(@Nullable BlockPos pos) {
        if (pos == null) return "?";
        int num = mailboxes.indexOf(pos) + 1;
        String street;
        if (origin != null) {
            int dx = pos.getX() - origin.getX(), dz = pos.getZ() - origin.getZ();
            int ix = nearest(dx), iz = nearest(dz);
            int distX = Math.abs(dx - TownBuilder.ROADS[ix]), distZ = Math.abs(dz - TownBuilder.ROADS[iz]);
            street = distZ <= distX ? STREETS_X[iz] : STREETS_Z[ix];
        } else street = "ул. Новая";
        return street + ", д. " + (num > 0 ? num : 1);
    }

    private static int nearest(int v) {
        int best = 0;
        for (int i = 1; i < TownBuilder.ROADS.length; i++)
            if (Math.abs(v - TownBuilder.ROADS[i]) < Math.abs(v - TownBuilder.ROADS[best])) best = i;
        return best;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        nbt.putBoolean("built", built);
        if (origin != null) nbt.putLong("origin", origin.asLong());
        if (school != null) nbt.putLong("school", school.asLong());
        nbt.putLongArray("mailboxes", mailboxes.stream().mapToLong(BlockPos::asLong).toArray());
        NbtCompound pl = new NbtCompound();
        places.forEach((k, v) -> {
            net.minecraft.nbt.NbtList l = new net.minecraft.nbt.NbtList();
            for (double d : v) l.add(net.minecraft.nbt.NbtDouble.of(d));
            pl.put(k, l);
        });
        nbt.put("places", pl);
        NbtCompound npcs = new NbtCompound();
        storyNpcs.forEach(npcs::putUuid);
        nbt.put("storyNpcs", npcs);
        NbtCompound sp = new NbtCompound();
        storyPlaces.forEach(sp::putString);
        nbt.put("storyPlaces", sp);
        return nbt;
    }

    private static TownState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        TownState s = new TownState();
        s.built = nbt.getBoolean("built");
        if (nbt.contains("origin")) s.origin = BlockPos.fromLong(nbt.getLong("origin"));
        if (nbt.contains("school")) s.school = BlockPos.fromLong(nbt.getLong("school"));
        for (long l : nbt.getLongArray("mailboxes")) s.mailboxes.add(BlockPos.fromLong(l));
        NbtCompound pl = nbt.getCompound("places");
        for (String k : pl.getKeys()) {
            net.minecraft.nbt.NbtList l = pl.getList(k, net.minecraft.nbt.NbtElement.DOUBLE_TYPE);
            double[] v = new double[5];
            for (int i = 0; i < Math.min(5, l.size()); i++) v[i] = l.getDouble(i);
            s.places.put(k, v);
        }
        NbtCompound npcs = nbt.getCompound("storyNpcs");
        for (String k : npcs.getKeys()) if (npcs.containsUuid(k)) s.storyNpcs.put(k, npcs.getUuid(k));
        NbtCompound sp = nbt.getCompound("storyPlaces");
        for (String k : sp.getKeys()) s.storyPlaces.put(k, sp.getString(k));
        return s;
    }
}
