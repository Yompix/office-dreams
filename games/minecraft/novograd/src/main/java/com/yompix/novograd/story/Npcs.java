package com.yompix.novograd.story;

import com.google.gson.JsonObject;
import com.yompix.novograd.ModContent;
import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.life.LifeData;
import com.yompix.novograd.world.TownState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

/**
 * Герои сюжета — обычные жители с меткой storyId. Один герой — один житель на игрока: 0.9.7 — в мультиплеере у каждого
 * игрока свои герои (ключ «id@uuid» в TownState.storyNpcs, у жителя — владелец {@link CitizenEntity#getStoryOwner}, чужие
 * его не видят и не могут заговорить). У хозяина одиночной/LAN-игры ключ прежний («id») — старые сохранения не ломаются.
 * Чей сюжет сейчас выполняется — {@link #push}/{@link #pop} вокруг входов сюжета.
 */
public final class Npcs {
    private Npcs() {}

    private static String scope = "";
    @Nullable private static net.minecraft.server.MinecraftServer server;
    private static final java.util.Set<String> HOSTS = new java.util.HashSet<>();

    /** Сюжет игрока p: герои ищутся и ставятся его. Вернуть прежний — {@link #pop}. */
    public static String push(@Nullable ServerPlayerEntity p) {
        String prev = scope;
        if (p == null) scope = "";
        else {
            scope = p.getUuidAsString();
            server = p.getServer();
            if (server != null && !server.isDedicated() && server.isHost(p.getGameProfile())) HOSTS.add(scope);
        }
        return prev;
    }

    public static void pop(String prev) {
        scope = prev == null ? "" : prev;
    }

    public static String scope() {
        return scope;
    }

    /** Ключ героя в TownState.storyNpcs: у хозяина и вне сюжета — «id», у остальных — «id@uuid». */
    public static String keyOf(String id, @Nullable String owner, @Nullable net.minecraft.server.MinecraftServer s) {
        if (owner == null || owner.isEmpty() || HOSTS.contains(owner)) return id;
        return id + "@" + owner;
    }

    static String key(String id) {
        return keyOf(id, scope, server);
    }

    /** Где герой «живёт», когда сюжету не нужен: учитель — в классе, Ирина — в офисе, Петров — в парке. */
    private static final Map<String, String> HOME = Map.of("teacher", "classroom1", "irina", "office_desk", "petrov", "park_center");
    private static final java.util.Set<String> KIDS = java.util.Set.<String>of(); // детей в Новограде нет: все сюжетные герои взрослые;

    @Nullable
    public static CitizenEntity find(ServerWorld w, String id) {
        UUID u = TownState.get(w).storyNpcs.get(key(id));
        if (u == null) return null;
        return w.getServer().getOverworld().getEntity(u) instanceof CitizenEntity c && c.isAlive() ? c : null;
    }


    /** Герой для разговора: если его район не загружен или он пропал — поставить снова на его место. */
    @Nullable
    public static CitizenEntity ensure(ServerPlayerEntity p, String id) {
        ServerWorld w = p.getServerWorld();
        CitizenEntity c = find(w, id);
        if (c != null) return c;
        String place = TownState.get(w).storyPlaces.get(key(id));
        Vec3d at = place == null ? null : Story.place(w, place);
        if (at == null || p.squaredDistanceTo(at) > 80 * 80) return null; // далеко — метка ведёт на место, герой появится, когда подъедешь
        w.getChunk(BlockPos.ofFloored(at));
        c = find(w, id); // после загрузки района он мог найтись
        return c != null ? c : spawn(p, id, place);
    }

    public static CitizenEntity spawn(ServerPlayerEntity p, String id, String place) {
        ServerWorld w = p.getServerWorld();
        TownState.get(w).storyPlaces.put(key(id), place);
        TownState.get(w).markDirty();
        Vec3d at = Story.place(w, place);
        if (at == null) at = p.getPos();
        CitizenEntity c = find(w, id);
        if (c == null) {
            JsonObject ch = Story.root().getAsJsonObject("characters").getAsJsonObject(id);
            c = new CitizenEntity(ModContent.CITIZEN, w);
            String skin = ch != null && ch.has("skin") && !ch.get("skin").isJsonNull() ? ch.get("skin").getAsString() : "student";
            int own = CitizenEntity.storySkin(id); // 0.8: свой скин героя (у прохожих такого нет)
            c.setStoryId(id);
            c.setup(own >= 0 ? own : CitizenEntity.skinIndex(skin), CitizenEntity.Role.CITIZEN, true, ch != null ? ch.get("name").getAsString() : id);
            BlockPos g = Story.ground(w, at);
            c.refreshPositionAndAngles(at.x, g.getY(), at.z, Story.placeYaw(w, place), 0);
            w.spawnEntity(c);
            TownState t = TownState.get(w);
            t.storyNpcs.put(key(id), c.getUuid());
            t.markDirty();
        } else {
            BlockPos g = Story.ground(w, at);
            c.getNavigation().stop();
            c.refreshPositionAndAngles(at.x, g.getY(), at.z, Story.placeYaw(w, place), 0);
            c.setHeadYaw(Story.placeYaw(w, place));
        }
        c.setStoryOwner(scope);
        c.setStationary(true);
        if (KIDS.contains(id)) {
            LifeData d = Life.get(p);
            c.setScale(LifeData.scaleForAge(Math.max(d.age, 7)));
        }
        return c;
    }

    public static void despawn(ServerPlayerEntity p, String id) {
        if (HOME.containsKey(id)) {
            spawn(p, id, HOME.get(id));
            return;
        }
        CitizenEntity c = find(p.getServerWorld(), id);
        if (c != null) c.discard();
    }

    /** Подрос игрок — подросли и друзья детства. */
    public static void rescaleKids(ServerPlayerEntity p, int age) {
        for (String id : KIDS) {
            CitizenEntity c = find(p.getServerWorld(), id);
            if (c != null) c.setScale(LifeData.scaleForAge(Math.max(age, 7)));
        }
    }
}
