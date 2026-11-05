package com.yompix.lifecraft.story;

import com.google.gson.JsonArray;
import com.yompix.lifecraft.life.Life;
import com.yompix.lifecraft.life.LifeData;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** Действия в начале шага: деньги, предметы, возраст, время суток, телепорт, герои, машины, сообщения. */
final class Actions {
    private Actions() {}

    static void run(ServerPlayerEntity p, LifeData d, JsonArray a) {
        String kind = a.get(0).getAsString();
        ServerWorld w = p.getServerWorld();
        switch (kind) {
            case "money" -> {
                int n = a.get(1).getAsInt();
                if (n > 0) Life.earn(p, n, "");
            }
            case "give" -> {
                var item = Story.item(a.get(1).getAsString());
                if (item != null) Story.give(p, new ItemStack(item, a.size() > 2 ? a.get(2).getAsInt() : 1));
            }
            case "take" -> {
                Item item = Story.item(a.get(1).getAsString());
                int left = a.size() > 2 ? a.get(2).getAsInt() : 1;
                var inv = p.getInventory();
                for (int i = 0; i < inv.size() && left > 0 && item != null; i++) {
                    ItemStack s = inv.getStack(i);
                    if (s.isOf(item)) {
                        int t = Math.min(left, s.getCount());
                        s.decrement(t);
                        left -= t;
                    }
                }
            }
            case "flag" -> d.flags.add(a.get(1).getAsString());
            case "age" -> {
                d.age = a.get(1).getAsInt();
                Story.applyAge(p, d.age);
                Npcs.rescaleKids(p, d.age);
            }
            case "edu" -> {
                d.edu = a.get(1).getAsInt();
                d.lessons = 0;
            }
            case "job" -> {
                d.job = a.get(1).getAsString();
                d.taskKind = "";
                d.taskPos = null;
            }
            case "time" -> {
                long tod = w.getTimeOfDay() % 24000, day = w.getTimeOfDay() / 24000;
                long t = a.get(1).getAsLong();
                w.setTimeOfDay((t < tod ? day + 1 : day) * 24000 + t);
            }
            case "tp" -> {
                Vec3d v = Story.place(w, a.get(1).getAsString());
                if (v != null) {
                    BlockPos g = Story.ground(w, v);
                    p.teleport(w, v.x, g.getY(), v.z, Story.placeYaw(w, a.get(1).getAsString()), 0);
                }
            }
            case "spawn" -> Npcs.spawn(p, a.get(1).getAsString(), a.get(2).getAsString());
            case "tp_npc" -> Npcs.spawn(p, a.get(1).getAsString(), a.get(2).getAsString());
            case "despawn" -> Npcs.despawn(p, a.get(1).getAsString());
            case "car" -> {
                Vec3d v = Story.place(w, a.get(1).getAsString());
                if (v != null) Story.spawnCar(p, v, Story.placeYaw(w, a.get(1).getAsString()), a.get(2).getAsInt(), false);
            }
            case "ball" -> {
                Vec3d v = Story.place(w, a.get(1).getAsString());
                if (v != null && w.getEntitiesByClass(com.yompix.lifecraft.entity.BallEntity.class, new net.minecraft.util.math.Box(BlockPos.ofFloored(v)).expand(20), e -> true).isEmpty())
                    com.yompix.lifecraft.world.TownBuilder.ball(w, Story.ground(w, v));
            }
            case "msg" -> Life.message(p, a.get(1).getAsString(), a.get(2).getAsString());
            default -> {}
        }
    }
}
