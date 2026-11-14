package com.yompix.glukhoman.story;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtLong;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Общий для всех игроков сюжет мира: глава, шаг, флаги, карта интерактивных точек, рассудок игроков. */
public class StoryState extends PersistentState {
    public boolean built;
    public int chapter = 0;          // 0 — ещё не начали (выбор сложности)
    public String step = "";
    public int difficulty = 1;       // 0 жутко, 1 страшно, 2 кошмар
    public String ending = "";       // "", "A", "B"
    public final Set<String> flags = new HashSet<>();
    public final Map<String, Integer> counters = new HashMap<>();
    /** Позиция блока → ключ действия (radio_anna, crate_shop, fusebox…). */
    public final Map<Long, String> interact = new HashMap<>();
    /** Именованные точки: чекпоинты, двери, места появления существ. */
    public final Map<String, BlockPos> points = new LinkedHashMap<>();
    /** Группы ламп: village, station, bunker, house… */
    public final Map<String, List<Long>> lamps = new HashMap<>();
    public final Map<UUID, Float> sanity = new HashMap<>();
    /** 0.4: кассеты (Tapes), тепловая карта Обходчика и прочее, что удобнее хранить деревом NBT. */
    public NbtCompound data = new NbtCompound();

    public static final PersistentState.Type<StoryState> TYPE = new PersistentState.Type<>(StoryState::new, StoryState::fromNbt, null);

    public static StoryState get(ServerWorld world) {
        return world.getServer().getOverworld().getPersistentStateManager().getOrCreate(TYPE, "glukhoman_story");
    }

    public BlockPos point(String name) {
        return points.get(name);
    }

    public void point(String name, BlockPos p) {
        points.put(name, p.toImmutable());
        markDirty();
    }

    public void bind(BlockPos pos, String key) {
        interact.put(pos.asLong(), key);
        markDirty();
    }

    public BlockPos find(String key) {
        for (var e : interact.entrySet()) if (e.getValue().equals(key)) return BlockPos.fromLong(e.getKey());
        return null;
    }

    public void lamp(String group, BlockPos pos) {
        lamps.computeIfAbsent(group, k -> new ArrayList<>()).add(pos.asLong());
    }

    public boolean flag(String f) {
        return flags.contains(f);
    }

    public boolean set(String f) {
        boolean added = flags.add(f);
        if (added) markDirty();
        return added;
    }

    public int count(String c) {
        return counters.getOrDefault(c, 0);
    }

    public int inc(String c) {
        int v = count(c) + 1;
        counters.put(c, v);
        markDirty();
        return v;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup reg) {
        nbt.putBoolean("built", built);
        nbt.putInt("chapter", chapter);
        nbt.putString("step", step);
        nbt.putInt("difficulty", difficulty);
        nbt.putString("ending", ending);
        NbtList fl = new NbtList();
        flags.forEach(f -> fl.add(NbtString.of(f)));
        nbt.put("flags", fl);
        NbtCompound c = new NbtCompound();
        counters.forEach(c::putInt);
        nbt.put("counters", c);
        NbtCompound in = new NbtCompound();
        interact.forEach((k, v) -> in.putString(Long.toString(k), v));
        nbt.put("interact", in);
        NbtCompound pt = new NbtCompound();
        points.forEach((k, v) -> pt.putLong(k, v.asLong()));
        nbt.put("points", pt);
        NbtCompound lp = new NbtCompound();
        lamps.forEach((k, v) -> {
            NbtList l = new NbtList();
            v.forEach(x -> l.add(NbtLong.of(x)));
            lp.put(k, l);
        });
        nbt.put("lamps", lp);
        NbtCompound sn = new NbtCompound();
        sanity.forEach((k, v) -> sn.putFloat(k.toString(), v));
        nbt.put("sanity", sn);
        nbt.put("data", data);
        return nbt;
    }

    public static StoryState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup reg) {
        StoryState s = new StoryState();
        s.built = nbt.getBoolean("built");
        s.chapter = nbt.getInt("chapter");
        s.step = nbt.getString("step");
        s.difficulty = nbt.contains("difficulty") ? nbt.getInt("difficulty") : 1;
        s.ending = nbt.getString("ending");
        NbtList fl = nbt.getList("flags", 8);
        for (int i = 0; i < fl.size(); i++) s.flags.add(fl.getString(i));
        NbtCompound c = nbt.getCompound("counters");
        for (String k : c.getKeys()) s.counters.put(k, c.getInt(k));
        NbtCompound in = nbt.getCompound("interact");
        for (String k : in.getKeys()) s.interact.put(Long.parseLong(k), in.getString(k));
        NbtCompound pt = nbt.getCompound("points");
        for (String k : pt.getKeys()) s.points.put(k, BlockPos.fromLong(pt.getLong(k)));
        NbtCompound lp = nbt.getCompound("lamps");
        for (String k : lp.getKeys()) {
            List<Long> l = new ArrayList<>();
            NbtList nl = lp.getList(k, 4);
            for (int i = 0; i < nl.size(); i++) l.add(((NbtLong) nl.get(i)).longValue());
            s.lamps.put(k, l);
        }
        NbtCompound sn = nbt.getCompound("sanity");
        for (String k : sn.getKeys()) s.sanity.put(UUID.fromString(k), sn.getFloat(k));
        s.data = nbt.getCompound("data");
        return s;
    }
}
