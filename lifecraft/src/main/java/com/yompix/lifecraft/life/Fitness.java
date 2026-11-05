package com.yompix.lifecraft.life;

import com.yompix.lifecraft.LifeCraft;
import com.yompix.lifecraft.entity.CitizenEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Вес и форма. Фастфуд прибавляет килограммы, салат и движение — убавляют.
 * Тренажёры (беговая дорожка, штанга) и пульт диджея открывают ритм-игру; от результата зависит,
 * сколько сгорело и сколько заплатили. Полного героя видно: модель шире, бегает медленнее.
 */
public final class Fitness {
    private Fitness() {}

    private static final Identifier SPEED_ID = LifeCraft.id("weight_speed");
    private static final Map<UUID, Vec3d> LAST = new HashMap<>();
    private static final Map<UUID, Double> WALKED = new HashMap<>();
    private static final Map<UUID, Long> LAST_WORKOUT = new HashMap<>();

    public static String describe(float kg) {
        return kg < 52 ? "худой" : kg < 75 ? "в норме" : kg < 90 ? "полноват" : kg < 105 ? "толстый" : "очень толстый";
    }

    public static void ate(ServerPlayerEntity p, float kg) {
        LifeData d = Life.get(p);
        float before = d.weight;
        d.weight = Math.max(45, Math.min(130, d.weight + kg));
        if ((int) (before / 5) != (int) (d.weight / 5) && kg > 0)
            p.sendMessage(Text.literal("⚖ Вес " + Math.round(d.weight) + " кг — " + describe(d.weight) + ". Фастфуд — в меру!").formatted(Formatting.GOLD), true);
        apply(p, d);
        Life.sync(p);
    }

    /** Раз в секунду: пройденные блоки сжигают вес (бег — вдвое больше). */
    public static void tick(ServerPlayerEntity p) {
        LifeData d = Life.get(p);
        Vec3d now = p.getPos(), was = LAST.put(p.getUuid(), now);
        if (was == null || p.hasVehicle()) return;
        double dist = Math.min(12, Math.sqrt(Math.pow(now.x - was.x, 2) + Math.pow(now.z - was.z, 2)));
        double w = WALKED.merge(p.getUuid(), dist * (p.isSprinting() ? 2 : 1), Double::sum);
        if (w >= 400) {
            WALKED.put(p.getUuid(), 0.0);
            if (d.weight > 55) {
                d.weight -= 0.2f;
                if (d.fitness < 40) d.fitness++;
                apply(p, d);
                Life.sync(p);
            }
        }
    }

    /** Скорость от веса: лишние килограммы замедляют, хорошая форма — чуть ускоряет. */
    public static void apply(ServerPlayerEntity p, LifeData d) {
        var speed = p.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speed == null) return;
        speed.removeModifier(SPEED_ID);
        double k = d.weight > 80 ? -(d.weight - 80) * 0.006 : 0;
        k += d.fitness * 0.0008;
        if (Math.abs(k) > 0.001)
            speed.addTemporaryModifier(new EntityAttributeModifier(SPEED_ID, Math.max(-0.3, k), EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }

    public static void weigh(ServerPlayerEntity p) {
        LifeData d = Life.get(p);
        p.sendMessage(Text.literal("⚖ Весы: " + String.format("%.1f", d.weight) + " кг (" + describe(d.weight) + "). Форма: " + d.fitness + "/100. Тренировок: " + d.workouts)
                .formatted(Formatting.AQUA), false);
        Achievements.give(p, "scales");
    }

    /** Тренажёр или пульт: открыть ритм-игру. */
    public static void openWorkout(ServerPlayerEntity p, String mode) {
        LifeData d = Life.get(p);
        if (mode.equals("dj")) {
            long t = p.getServerWorld().getTimeOfDay() % 24000;
            boolean story = com.yompix.lifecraft.story.Story.wantsJob(p, "dj");
            if (!story && Jobs.job(p) != Jobs.Job.DJ) {
                p.sendMessage(Text.literal("🎧 Пульт только для диджея клуба. Работа «Диджей» — в телефоне").formatted(Formatting.LIGHT_PURPLE), false);
                return;
            }
            if (!story && (t < 12500 || t > 22500)) {
                p.sendMessage(Text.literal("🎧 Клуб открывается вечером — приходи после 18:30").formatted(Formatting.LIGHT_PURPLE), false);
                return;
            }
        } else {
            if (d.energy < 15) {
                p.sendMessage(Text.literal("Сил нет… Поешь или поспи, потом тренировка").formatted(Formatting.RED), true);
                return;
            }
            long now = p.getServerWorld().getTime();
            if (now - LAST_WORKOUT.getOrDefault(p.getUuid(), -10000L) < 200) {
                p.sendMessage(Text.literal("Передохни немного между подходами").formatted(Formatting.YELLOW), true);
                return;
            }
        }
        NbtCompound n = new NbtCompound();
        n.putString("mode", mode);
        LifeNet.send(p, "rhythm", n);
    }

    /** Результат ритм-игры: score 0–100. */
    static void result(ServerPlayerEntity p, String mode, int score) {
        LifeData d = Life.get(p);
        score = Math.max(0, Math.min(100, score));
        if (mode.equals("dj")) {
            int pay = 20 + score;
            Life.earn(p, pay, "сет в «Неоне»: " + score + "%");
            // танцпол оживает: чем лучше сет, тем больше людей танцует
            for (CitizenEntity c : p.getServerWorld().getEntitiesByClass(CitizenEntity.class, p.getBoundingBox().expand(16), c -> c.getStoryId().isEmpty()))
                if (p.getRandom().nextInt(100) < 30 + score) c.busy(CitizenEntity.Activity.DANCE, 400 + score * 4, null, net.minecraft.item.ItemStack.EMPTY);
            d.mood = Math.min(100, d.mood + 10);
            com.yompix.lifecraft.story.Story.onJobDone(p, "dj");
            if (score >= 90) Achievements.give(p, "dj");
        } else {
            LAST_WORKOUT.put(p.getUuid(), p.getServerWorld().getTime());
            float burn = (mode.equals("run") ? 0.9f : 0.6f) * (0.4f + score / 100f);
            d.weight = Math.max(50, d.weight - burn);
            d.fitness = Math.min(100, d.fitness + 1 + score / 30);
            d.workouts++;
            d.energy = Math.max(0, d.energy - 12);
            d.mood = Math.min(100, d.mood + 4);
            p.sendMessage(Text.literal("💪 Тренировка: " + score + "%. Сгорело " + String.format("%.1f", burn) + " кг, вес " + String.format("%.1f", d.weight) + " кг, форма " + d.fitness + "/100")
                    .formatted(Formatting.GREEN), false);
            com.yompix.lifecraft.story.Story.onJobDone(p, "workout");
            if (d.workouts >= 10) Achievements.give(p, "athlete");
        }
        apply(p, d);
        Life.sync(p);
    }
}
