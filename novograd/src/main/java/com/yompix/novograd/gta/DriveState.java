package com.yompix.novograd.gta;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.PersistentState;

import java.util.UUID;

/**
 * Сохранённое «водительское» состояние мира: штрафы игроков, машины на штрафстоянке, рекорды гонок катеров
 * и камеры скорости, уже поставленные у трасс. Хранится в мире (работает и в старых сохранениях).
 */
public final class DriveState extends PersistentState {
    /** По игроку: fines (список {amt, day, where, rep}), impound (список машин), boat (лучшее время в тиках). */
    final NbtCompound players = new NbtCompound();
    /** Общее: какие камеры уже построены, рекорды катеров. */
    final NbtCompound global = new NbtCompound();

    static final Type<DriveState> TYPE = new Type<>(DriveState::new, (n, reg) -> load(n), null);

    static DriveState load(NbtCompound n) {
        DriveState s = new DriveState();
        s.players.copyFrom(n.getCompound("players"));
        s.global.copyFrom(n.getCompound("global"));
        return s;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound n, net.minecraft.registry.RegistryWrapper.WrapperLookup reg) {
        n.put("players", players);
        n.put("global", global);
        return n;
    }

    public static DriveState get(MinecraftServer s) {
        return s.getOverworld().getPersistentStateManager().getOrCreate(TYPE, "novograd_drive");
    }

    /** Данные игрока (создаются при первом обращении). Меняешь — зови markDirty(). */
    public NbtCompound of(UUID id) {
        String k = id.toString();
        if (!players.contains(k)) players.put(k, new NbtCompound());
        return players.getCompound(k);
    }

    public NbtCompound of(ServerPlayerEntity p) {
        return of(p.getUuid());
    }

    public static NbtList list(NbtCompound n, String key) {
        if (!n.contains(key)) n.put(key, new NbtList());
        return n.getList(key, NbtElement.COMPOUND_TYPE);
    }
}
