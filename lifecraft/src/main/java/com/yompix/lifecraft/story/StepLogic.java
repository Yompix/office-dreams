package com.yompix.lifecraft.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.yompix.lifecraft.ModContent;
import com.yompix.lifecraft.entity.CarEntity;
import com.yompix.lifecraft.entity.CitizenEntity;
import com.yompix.lifecraft.life.Life;
import com.yompix.lifecraft.life.LifeData;
import com.yompix.lifecraft.life.LifeNet;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/** Что делает каждый тип шага: при входе, каждый тик, куда ставить маркер и что писать в подсказке. */
final class StepLogic {
    private StepLogic() {}

    static String type(JsonObject s) {
        return Story.str(s, "type", "");
    }

    static void enter(ServerPlayerEntity p, LifeData d, Story.Run r, JsonObject s) {
        ServerWorld w = p.getServerWorld();
        switch (type(s)) {
            case "dialog" -> {
                Story.sendDialog(p, s.getAsJsonArray("lines"), s.has("choices") ? s.getAsJsonArray("choices") : null);
                r.waitingDialog = true;
                r.dialogSent = true;
            }
            case "party" -> {
                Vec3d at = Story.place(w, Story.str(s, "place", ""));
                if (at != null) {
                    for (CitizenEntity c : w.getEntitiesByClass(CitizenEntity.class, new Box(BlockPos.ofFloored(at)).expand(14), c -> true))
                        if (!c.getStoryId().equals(Story.str(s, "npc", ""))) c.lockActivity(CitizenEntity.Activity.DANCE);
                    // гости: ровесники героя
                    for (int i = 0; i < 4; i++) {
                        CitizenEntity g = new CitizenEntity(ModContent.CITIZEN, w);
                        g.setup(CitizenEntity.randomSkin(p.getRandom()), CitizenEntity.Role.CITIZEN, true, com.yompix.lifecraft.life.Names.random(p.getRandom()));
                        BlockPos gp = Story.ground(w, at.add(-3 + i * 2, 0, 3 + (i % 2)));
                        g.refreshPositionAndAngles(gp.getX() + 0.5, gp.getY(), gp.getZ() + 0.5, p.getRandom().nextFloat() * 360, 0);
                        g.setScale(LifeData.scaleForAge(Math.max(d.age, 7)));
                        g.lockActivity(CitizenEntity.Activity.DANCE);
                        g.removeLater(20 * 60 * 4);
                        w.spawnEntity(g);
                    }
                }
                if (s.has("music")) Story.music(p);
            }
            case "goto" -> r.timer = Story.num(s, "time", 0) * 20;
            case "collect" -> {
                d.stepCount = 0;
                for (JsonElement e : s.getAsJsonArray("clues")) {
                    String place = Story.str(e.getAsJsonObject(), "place", "");
                    if (d.flags.contains("clue:" + place)) {
                        d.stepCount++;
                        continue;
                    }
                    Vec3d v = Story.place(w, place);
                    if (v != null) w.setBlockState(Story.ground(w, v), ModContent.CLUE.getDefaultState());
                }
                if (d.stepCount >= s.getAsJsonArray("clues").size()) Story.advance(p);
            }
            case "race" -> startRace(p, r, s);
            case "cutscene" -> Story.startCutscene(p, d, s);
            case "title" -> {
                NbtCompound n = new NbtCompound();
                n.putString("title", Story.str(s, "title", ""));
                n.putString("subtitle", Story.str(s, "subtitle", ""));
                LifeNet.send(p, "title", n);
                r.timer = 150;
            }
            case "credits" -> LifeNet.send(p, "credits", new NbtCompound());
            default -> {}
        }
    }

    static void tick(ServerPlayerEntity p, LifeData d, Story.Run r, JsonObject s) {
        ServerWorld w = p.getServerWorld();
        if (r.ticks % 20 == 0 && !type(s).equals("title")) Story.sendObjective(p, d);
        switch (type(s)) {
            case "goto" -> {
                Vec3d v = Story.place(w, Story.str(s, "place", ""));
                if (v == null) {
                    Story.advance(p);
                    return;
                }
                boolean inCar = p.getVehicle() instanceof CarEntity;
                if (s.has("vehicle") && !inCar) {
                    if (r.ticks % 60 == 0) Story.say(p, "Сядь в машину!", Formatting.YELLOW);
                } else if (flat(p.getPos(), v) < Story.num(s, "radius", 4) + (inCar ? 3 : 0)) {
                    Story.advance(p);
                    return;
                }
                if (r.timer > 0 && --r.timer == 0) {
                    Story.say(p, "Не успел! Попробуй ещё раз", Formatting.RED);
                    p.playSoundToPlayer(ModContent.ANSWER_WRONG, SoundCategory.MASTER, 1f, 1f);
                    r.timer = Story.num(s, "time", 0) * 20;
                }
            }
            case "money" -> {
                if (d.money >= Story.num(s, "amount", 0)) Story.advance(p);
            }
            case "buy" -> {
                int have = Story.count(p, Story.item(Story.str(s, "item", ""))) + (s.has("alt") ? Story.count(p, Story.item(Story.str(s, "alt", ""))) : 0);
                if (have >= Story.num(s, "count", 1)) Story.advance(p);
            }
            case "sleep" -> {
                if (p.isSleeping()) Story.advance(p);
            }
            case "race" -> tickRace(p, d, r, s);
            case "drivetest" -> tickDrive(p, d, r, s);
            case "title" -> {
                if (--r.timer <= 0) Story.advance(p);
            }
            default -> {}
        }
    }

    static double flat(Vec3d a, Vec3d b) {
        double dx = a.x - b.x, dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    // ---------------------------------------------------------------- гонка
    private static void startRace(ServerPlayerEntity p, Story.Run r, JsonObject s) {
        ServerWorld w = p.getServerWorld();
        CitizenEntity rival = Npcs.find(w, Story.str(s, "rival", ""));
        if (rival != null) {
            rival.getNavigation().stop();
            rival.refreshPositionAndAngles(p.getX() + 1.2, p.getY(), p.getZ(), p.getYaw(), 0);
            rival.setStationary(true);
            var sp = rival.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
            if (sp != null) sp.setBaseValue(0.3);
        }
        r.countdown = 80;
        r.rivalIndex = 0;
        Life.get(p).stepCount = 0;
    }

    private static void tickRace(ServerPlayerEntity p, LifeData d, Story.Run r, JsonObject s) {
        ServerWorld w = p.getServerWorld();
        JsonArray pts = s.getAsJsonArray("points");
        CitizenEntity rival = Npcs.find(w, Story.str(s, "rival", ""));
        if (r.countdown > 0) {
            if (r.countdown % 20 == 0) {
                int n = r.countdown / 20 - 1;
                LifeNet.send(p, "countdown", Story.text("text", n == 0 ? "МАРШ!" : String.valueOf(n)));
                p.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.MASTER, 1f, n == 0 ? 2f : 1f);
            }
            r.countdown--;
            return;
        }
        // соперник бежит по тем же отметкам
        if (rival != null && r.rivalIndex < pts.size()) {
            Vec3d t = Story.place(w, pts.get(r.rivalIndex).getAsString());
            if (t != null) {
                if (flat(rival.getPos(), t) < 2.2) r.rivalIndex++;
                else if (r.ticks % 10 == 0) rival.getNavigation().startMovingTo(t.x, t.y, t.z, 1.25);
            }
        }
        if (d.stepCount < pts.size()) {
            Vec3d t = Story.place(w, pts.get(d.stepCount).getAsString());
            if (t != null && flat(p.getPos(), t) < 3.5) {
                d.stepCount++;
                p.playSoundToPlayer(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.MASTER, 1f, 1.2f);
            }
        }
        if (d.stepCount >= pts.size()) {
            if (rival != null) rival.getNavigation().stop();
            Story.say(p, "Ты первый!", Formatting.GREEN);
            Story.advance(p);
        } else if (r.rivalIndex >= pts.size()) {
            Story.say(p, "Соперник прибежал первым! Ещё раз!", Formatting.RED);
            p.playSoundToPlayer(ModContent.ANSWER_WRONG, SoundCategory.MASTER, 1f, 1f);
            Vec3d start = Story.place(w, "lisa_yard3");
            if (start != null) {
                BlockPos g = Story.ground(w, start);
                p.teleport(w, start.x, g.getY(), start.z, p.getYaw(), 0);
            }
            startRace(p, r, s);
        }
    }

    // ---------------------------------------------------------------- экзамен на права
    private static void tickDrive(ServerPlayerEntity p, LifeData d, Story.Run r, JsonObject s) {
        ServerWorld w = p.getServerWorld();
        JsonArray pts = s.getAsJsonArray("points");
        if (!(p.getVehicle() instanceof CarEntity car)) {
            r.lastCarPos = null;
            return;
        }
        if (r.ticks % 20 == 0) {
            if (r.lastCarPos != null) {
                double kmh = flat(car.getPos(), r.lastCarPos) * 3.6;
                int limit = Story.num(s, "limit", 50);
                if (kmh > limit + 4) {
                    r.violations++;
                    Story.say(p, "Инструктор: «Куда гонишь?! " + (int) kmh + " км/ч!» Ошибка " + r.violations + "/3", Formatting.RED);
                    p.playSoundToPlayer(ModContent.CAR_HORN, SoundCategory.MASTER, 0.6f, 0.7f);
                }
            }
            r.lastCarPos = car.getPos();
        }
        if (r.violations >= 3) {
            Story.say(p, "Экзамен провален. Начнём заново!", Formatting.RED);
            r.violations = 0;
            d.stepCount = 0;
            Vec3d start = Story.place(w, "drive_start");
            if (start != null) {
                BlockPos g = Story.ground(w, start);
                car.refreshPositionAndAngles(start.x, g.getY(), start.z, Story.placeYaw(w, "drive_start"), 0);
            }
            return;
        }
        if (d.stepCount < pts.size()) {
            Vec3d t = Story.place(w, pts.get(d.stepCount).getAsString());
            if (t != null && flat(car.getPos(), t) < 7) {
                d.stepCount++;
                p.playSoundToPlayer(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.MASTER, 1f, 1.2f);
            }
        } else {
            Story.advance(p);
        }
    }

    /** Улика найдена (нажали на блок улики). */
    static void clue(ServerPlayerEntity p, BlockPos pos) {
        LifeData d = Life.get(p);
        JsonObject s = Story.step(d);
        ServerWorld w = p.getServerWorld();
        if (s == null || !type(s).equals("collect")) return;
        for (JsonElement e : s.getAsJsonArray("clues")) {
            JsonObject c = e.getAsJsonObject();
            String place = Story.str(c, "place", "");
            Vec3d v = Story.place(w, place);
            if (v == null || d.flags.contains("clue:" + place) || flat(Vec3d.ofCenter(pos), v) > 2.5) continue;
            d.flags.add("clue:" + place);
            d.stepCount++;
            w.removeBlock(pos, false);
            Story.say(p, "Найдено: " + Story.str(c, "name", "улика") + " (" + d.stepCount + "/" + s.getAsJsonArray("clues").size() + ")", Formatting.GOLD);
            p.playSoundToPlayer(ModContent.LEVEL_UP, SoundCategory.MASTER, 0.7f, 1.4f);
            if (d.stepCount >= s.getAsJsonArray("clues").size()) Story.advance(p);
            else {
                Story.sendObjective(p, d);
                Life.sync(p);
            }
            return;
        }
    }

    // ---------------------------------------------------------------- маркер и подсказка
    @Nullable
    static Vec3d target(ServerPlayerEntity p, LifeData d, Story.Run r, JsonObject s) {
        ServerWorld w = p.getServerWorld();
        switch (type(s)) {
            case "goto":
                return Story.place(w, Story.str(s, "place", ""));
            case "talk":
            case "party": {
                CitizenEntity c = Npcs.find(w, Story.str(s, "npc", ""));
                return c != null ? c.getPos().add(0, c.getHeight() + 0.3, 0) : Story.place(w, Story.str(s, "place", ""));
            }
            case "lesson":
                return Story.place(w, "classroom1");
            case "sleep":
                return Story.place(w, "home_bed");
            case "buy":
                return Story.place(w, "shop_door");
            case "job": {
                if (d.taskPos != null && !d.taskKind.isEmpty()) return Vec3d.ofCenter(d.taskPos);
                return switch (Story.str(s, "job", "")) {
                    case "courier" -> Story.place(w, "office_desk");
                    case "cashier" -> Story.place(w, "shop_counter");
                    default -> null;
                };
            }
            case "collect":
                for (JsonElement e : s.getAsJsonArray("clues")) {
                    String place = Story.str(e.getAsJsonObject(), "place", "");
                    if (!d.flags.contains("clue:" + place)) return Story.place(w, place);
                }
                return null;
            case "race": {
                JsonArray pts = s.getAsJsonArray("points");
                return d.stepCount < pts.size() ? Story.place(w, pts.get(d.stepCount).getAsString()) : null;
            }
            case "drivetest": {
                if (!(p.getVehicle() instanceof CarEntity)) {
                    Vec3d st = Story.place(w, "drive_start");
                    if (st == null) return null;
                    var cars = w.getEntitiesByClass(CarEntity.class, new Box(BlockPos.ofFloored(st)).expand(12), c -> true);
                    return cars.isEmpty() ? st : cars.get(0).getPos().add(0, 1.6, 0);
                }
                JsonArray pts = s.getAsJsonArray("points");
                return d.stepCount < pts.size() ? Story.place(w, pts.get(d.stepCount).getAsString()) : null;
            }
            default:
                return null;
        }
    }

    static String extra(ServerPlayerEntity p, LifeData d, Story.Run r, JsonObject s) {
        return switch (type(s)) {
            case "collect" -> "Найдено " + d.stepCount + "/" + s.getAsJsonArray("clues").size();
            case "race" -> "Отметка " + Math.min(d.stepCount + 1, s.getAsJsonArray("points").size()) + "/" + s.getAsJsonArray("points").size();
            case "drivetest" -> !(p.getVehicle() instanceof CarEntity) ? "Сядь в учебную машину"
                    : "Отметка " + Math.min(d.stepCount + 1, s.getAsJsonArray("points").size()) + "/" + s.getAsJsonArray("points").size() + " · ошибки " + r.violations + "/3";
            case "goto" -> r.timer > 0 ? "Осталось " + (r.timer / 20) + " с" : "";
            case "lesson", "job" -> Story.num(s, "count", 1) > 1 ? "Сделано " + d.stepCount + "/" + Story.num(s, "count", 1) : "";
            case "money" -> "$" + d.money + " из $" + Story.num(s, "amount", 0);
            default -> "";
        };
    }
}
