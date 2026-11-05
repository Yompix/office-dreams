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

/** Герои сюжета — обычные жители с меткой storyId. Один герой — один житель на весь мир. */
public final class Npcs {
    private Npcs() {}

    /** Где герой «живёт», когда сюжету не нужен: учитель — в классе, Ирина — в офисе, Петров — в парке. */
    private static final Map<String, String> HOME = Map.of("teacher", "classroom1", "irina", "office_desk", "petrov", "park_center");
    private static final java.util.Set<String> KIDS = java.util.Set.of("tema", "lisa", "gleb");

    @Nullable
    public static CitizenEntity find(ServerWorld w, String id) {
        UUID u = TownState.get(w).storyNpcs.get(id);
        if (u == null) return null;
        return w.getServer().getOverworld().getEntity(u) instanceof CitizenEntity c && c.isAlive() ? c : null;
    }

    public static CitizenEntity spawn(ServerPlayerEntity p, String id, String place) {
        ServerWorld w = p.getServerWorld();
        Vec3d at = Story.place(w, place);
        if (at == null) at = p.getPos();
        CitizenEntity c = find(w, id);
        if (c == null) {
            JsonObject ch = Story.root().getAsJsonObject("characters").getAsJsonObject(id);
            c = new CitizenEntity(ModContent.CITIZEN, w);
            String skin = ch != null && ch.has("skin") && !ch.get("skin").isJsonNull() ? ch.get("skin").getAsString() : "student";
            c.setup(CitizenEntity.skinIndex(skin), CitizenEntity.Role.CITIZEN, true, ch != null ? ch.get("name").getAsString() : id);
            c.setStoryId(id);
            BlockPos g = Story.ground(w, at);
            c.refreshPositionAndAngles(at.x, g.getY(), at.z, Story.placeYaw(w, place), 0);
            w.spawnEntity(c);
            TownState t = TownState.get(w);
            t.storyNpcs.put(id, c.getUuid());
            t.markDirty();
        } else {
            BlockPos g = Story.ground(w, at);
            c.getNavigation().stop();
            c.refreshPositionAndAngles(at.x, g.getY(), at.z, Story.placeYaw(w, place), 0);
            c.setHeadYaw(Story.placeYaw(w, place));
        }
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
