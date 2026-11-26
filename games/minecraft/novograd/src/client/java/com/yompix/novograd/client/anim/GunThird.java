package com.yompix.novograd.client.anim;

import com.yompix.novograd.client.GunFx;
import com.yompix.novograd.client.gun.GunAnim;
import com.yompix.novograd.item.GunItem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;

import java.util.HashMap;
import java.util.Map;

/**
 * 0.9.8: оружие от третьего лица — у каждого типа своя перезарядка (пистолет: магазин и затвор; «АК»: магазин и
 * рукоятка взвода; дробовик: патроны по одному и помпа; снайперка: магазин и затвор; ПП; револьвер: барабан и
 * ускоритель; гранатомёт — выстрел из-за спины), достать/убрать в кобуру, отдача в руки, удары битой, ножом и кулаком.
 * Другие игроки видят перезарядку по пакету «hand_gunrl» (сервер шлёт длину), себя — по GunFx; звуки — позиционно.
 */
public final class GunThird {
    private GunThird() {}

    private record Reload(String gun, long t0, int ticks, boolean empty, int shells) {}

    private static final Map<Integer, Reload> RELOAD = new HashMap<>();
    private static final Map<Integer, float[]> CUE_DONE = new HashMap<>();
    /** Смена предмета в руке: id сущности → {предмет, тик смены, был ли ствол}. */
    private static final Map<Integer, Object[]> HELD = new HashMap<>();
    private static final Map<Integer, Boolean> SWING = new HashMap<>();

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(GunThird::tick);
    }

    static void receive(MinecraftClient c, NbtCompound n) {
        if (c.world == null) return;
        RELOAD.put(n.getInt("id"), new Reload(n.getString("gun"), c.world.getTime(), Math.max(6, n.getInt("ticks")), n.getBoolean("empty"), n.getInt("shells")));
        CUE_DONE.put(n.getInt("id"), new float[]{-1});
    }

    private static void tick(MinecraftClient c) {
        if (c.world == null) {
            RELOAD.clear();
            HELD.clear();
            return;
        }
        long now = c.world.getTime();
        // звуки чужой перезарядки — по меткам GunAnim (как у себя), в мире
        for (var it = RELOAD.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            Reload r = e.getValue();
            float p = (now - r.t0()) / (float) r.ticks();
            if (p > 1.05f) {
                it.remove();
                continue;
            }
            var ent = c.world.getEntityById(e.getKey());
            if (ent == null) continue;
            float[] done = CUE_DONE.computeIfAbsent(e.getKey(), k -> new float[]{-1});
            for (GunAnim.Cue cue : GunAnim.reloadCues(r.gun(), r.empty(), r.shells(), r.ticks() / 20f))
                if (cue.at() > done[0] && cue.at() <= p && !cue.sound().equals("drop"))
                    c.world.playSound(ent.getX(), ent.getY() + 1.2, ent.getZ(), sound(cue.sound()), SoundCategory.PLAYERS, 0.7f, 1f, false);
            done[0] = p;
        }
        // смена предмета: достал/убрал (звук кобуры), взмах битой/ножом/кулаком — свист
        for (PlayerEntity p : c.world.getPlayers()) {
            if (p.squaredDistanceTo(c.player) > 64 * 64) continue;
            Item item = p.getMainHandStack().getItem();
            Object[] h = HELD.get(p.getId());
            if (h == null || h[0] != item) {
                boolean wasGun = h != null && h[0] instanceof GunItem;
                if (h != null && (item instanceof GunItem || wasGun))
                    c.world.playSound(p.getX(), p.getY() + 1, p.getZ(), hands("holster"), SoundCategory.PLAYERS, 0.5f, item instanceof GunItem ? 1.1f : 0.9f, false);
                HELD.put(p.getId(), new Object[]{item, now, wasGun});
            }
            boolean sw = p.handSwinging;
            if (sw && !SWING.getOrDefault(p.getId(), false)) {
                String m = melee(p.getMainHandStack());
                if (m != null)
                    c.world.playSound(p.getX(), p.getY() + 1.2, p.getZ(), hands(m.equals("bat") ? "whoosh_bat" : m.equals("knife") ? "whoosh_knife" : "whoosh_fist"),
                            SoundCategory.PLAYERS, 0.6f, 0.9f + p.getRandom().nextFloat() * 0.2f, false);
            }
            SWING.put(p.getId(), sw);
        }
        if (now % 200 == 0) {
            HELD.keySet().removeIf(id -> c.world.getEntityById(id) == null);
            SWING.keySet().removeIf(id -> c.world.getEntityById(id) == null);
        }
    }

    private static net.minecraft.sound.SoundEvent hands(String s) {
        return net.minecraft.sound.SoundEvent.of(com.yompix.novograd.Novograd.id("hands." + s));
    }

    private static net.minecraft.sound.SoundEvent sound(String cue) {
        return switch (cue) {
            case "mag_out" -> com.yompix.novograd.gta.Arsenal.MAG_OUT;
            case "mag_in" -> com.yompix.novograd.gta.Arsenal.MAG_IN;
            case "slide_back" -> com.yompix.novograd.gta.Arsenal.SLIDE_BACK;
            case "slide_release" -> com.yompix.novograd.gta.Arsenal.SLIDE_RELEASE;
            case "bolt_back" -> com.yompix.novograd.gta.Arsenal.BOLT_BACK;
            case "bolt_fwd" -> com.yompix.novograd.gta.Arsenal.BOLT_FWD;
            case "shell_in" -> com.yompix.novograd.gta.Arsenal.SHELL_IN;
            case "pump" -> com.yompix.novograd.gta.Arsenal.PUMP;
            default -> com.yompix.novograd.gta.Arsenal.HANDLING;
        };
    }

    /** bat / knife / fist — или null. */
    static String melee(ItemStack st) {
        if (st.isEmpty()) return "fist";
        String id = Registries.ITEM.getId(st.getItem()).getPath();
        return id.equals("bat") ? "bat" : id.equals("knife") || id.equals("machete") ? "knife" : null;
    }

    static float s(float a, float b, float t) {
        float x = MathHelper.clamp((t - a) / (b - a), 0, 1);
        return x * x * (3 - 2 * x);
    }

    static float w(float t, float a, float b, float c, float d) {
        return s(a, b, t) * (1 - s(c, d, t));
    }

    // ------------------------------------------------------------------ поза со стволом (после GunFx.pose)
    /** Перезарядка, достать, отдача — поверх позы «целится». */
    public static void gunPose(BipedEntityModel<?> m, LivingEntity e) {
        if (!(e instanceof PlayerEntity p) || !(p.getMainHandStack().getItem() instanceof GunItem g)) return;
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.world == null) return;
        float td = c.getRenderTickCounter().getTickDelta(false);
        boolean right = p.getMainArm() == Arm.RIGHT;
        ModelPart main = right ? m.rightArm : m.leftArm, off = right ? m.leftArm : m.rightArm;
        float sd = right ? 1 : -1;
        // достать: из кобуры у бедра — вверх к позе (7 тиков)
        Object[] h = HELD.get(p.getId());
        if (h != null) {
            float dt = c.world.getTime() - (long) h[1] + td;
            if (dt < 7) {
                float k = s(0, 7, dt);
                main.pitch = MathHelper.lerp(k, 0.25f, main.pitch);
                main.yaw = MathHelper.lerp(k, 0.15f * sd, main.yaw);
                off.pitch = MathHelper.lerp(k, 0.05f, off.pitch);
                off.yaw = MathHelper.lerp(k, 0, off.yaw);
            }
        }
        // перезарядка
        float prog;
        boolean empty;
        int shells;
        float sec;
        if (p == c.player) {
            prog = GunFx.reloadProgress(g);
            empty = GunFx.reloadEmpty();
            shells = GunFx.reloadShells();
            sec = GunFx.reloadSeconds();
        } else {
            Reload r = RELOAD.get(p.getId());
            prog = r == null ? -1 : (c.world.getTime() - r.t0() + td) / r.ticks();
            if (prog > 1) prog = -1;
            empty = r != null && r.empty();
            shells = r == null ? 0 : r.shells();
            sec = r == null ? 1 : r.ticks() / 20f;
        }
        if (prog >= 0) reload(m, main, off, sd, g.animBase(), prog, empty, shells, sec, p.age + td);
        // отдача: чужой выстрел — руки подбрасывает (сила по типу)
        Long shot = GunFx.OTHERS_SHOT.get(p.getId());
        if (p != c.player && shot != null) {
            float ms = (System.nanoTime() - shot) / 1e6f;
            if (ms < 220) {
                float kick = (1 - ms / 220f) * (1 - ms / 220f) * switch (g.animBase()) {
                    case "shotgun", "sniper", "rpg" -> 0.45f;
                    case "pistol", "revolver" -> 0.32f;
                    default -> 0.14f;
                };
                main.pitch -= kick;
                off.pitch -= kick * 0.8f;
                m.head.pitch -= kick * 0.2f;
            }
        }
        // дыхание в стойке: ствол чуть «плывёт»
        float br = MathHelper.sin((p.age + td) * 0.07f + p.getId());
        main.pitch += 0.012f * br;
        off.pitch += 0.012f * br;
    }

    private static void reload(BipedEntityModel<?> m, ModelPart main, ModelPart off, float sd, String base, float t, boolean empty, int shells, float sec, float age) {
        // общий наклон: ствол к груди и вниз — видно, что руки заняты
        float tilt = w(t, 0, 0.12f, 0.88f, 1);
        float basePitch = -0.95f;
        main.pitch = MathHelper.lerp(tilt, main.pitch, basePitch);
        main.yaw = MathHelper.lerp(tilt, main.yaw, 0.35f * sd);
        off.pitch = MathHelper.lerp(tilt, off.pitch, -0.9f);
        off.yaw = MathHelper.lerp(tilt, off.yaw, 0.55f * sd);
        m.head.pitch = MathHelper.lerp(tilt * 0.8f, m.head.pitch, 0.55f);
        m.head.yaw = MathHelper.lerp(tilt * 0.8f, m.head.yaw, 0.18f * sd);
        switch (base) {
            case "pistol" -> {
                // магазин: вниз к поясу (подсумок) → вставить; с пустого — затвор
                float pouch = w(t, 0.12f, 0.28f, 0.42f, 0.56f);
                off.pitch += 0.75f * pouch;
                off.yaw -= 0.35f * sd * pouch;
                float slap = w(t, 0.56f, 0.6f, 0.62f, 0.66f);
                off.pitch -= 0.12f * slap;
                if (empty) {
                    float rack = w(t, 0.66f, 0.71f, 0.78f, 0.84f);
                    off.pitch -= 0.35f * rack;
                    off.yaw -= 0.25f * sd * rack;
                    off.pivotZ += 2.2f * s(0.71f, 0.77f, t) * rack;
                }
            }
            case "smg", "rifle" -> {
                main.yaw += 0.15f * sd * tilt;
                float pouch = w(t, 0.12f, 0.3f, 0.42f, 0.58f);
                off.pitch += 0.8f * pouch;
                off.yaw -= 0.2f * sd * pouch;
                off.pitch -= 0.1f * w(t, 0.6f, 0.64f, 0.66f, 0.7f);
                if (empty) {
                    float rack = w(t, 0.66f, 0.71f, 0.8f, 0.88f);
                    if (base.equals("rifle")) { // рукоятка затвора справа — левая тянется через ствол
                        off.yaw -= 0.55f * sd * rack;
                        off.pitch -= 0.25f * rack;
                    } else { // ПП: рукоятка спереди слева
                        off.pitch -= 0.35f * rack;
                        off.yaw += 0.15f * sd * rack;
                    }
                    off.pivotZ += 1.8f * s(0.71f, 0.77f, t) * rack;
                }
            }
            case "shotgun" -> {
                // патроны по одному: подсумок → окно снизу (0.4 с + 0.35 с на патрон)
                float T = Math.max(0.5f, sec), ts = t * T;
                int n = Math.max(1, shells);
                for (int i = 0; i < n; i++) {
                    float a = 0.4f + 0.35f * i;
                    float down = w(ts, a, a + 0.1f, a + 0.14f, a + 0.24f);
                    off.pitch += 0.7f * down;
                    off.yaw -= 0.25f * sd * down;
                    float push = w(ts, a + 0.22f, a + 0.28f, a + 0.3f, a + 0.35f);
                    off.pitch -= 0.15f * push;
                }
                if (empty) {
                    float u = ts - (T - 0.55f);
                    float pump = w(u, 0, 0.18f, 0.24f, 0.45f);
                    off.pivotZ += 3f * pump;
                }
            }
            case "sniper" -> {
                float pouch = w(t, 0.1f, 0.25f, 0.4f, 0.56f);
                off.pitch += 0.75f * pouch;
                // затвор: правая вверх-назад-вперёд-вниз
                float bolt = w(t, 0.64f, 0.7f, 0.9f, 0.96f);
                main.pitch -= 0.3f * bolt;
                main.yaw += 0.25f * sd * bolt;
                main.pivotZ += 2.2f * w(t, 0.7f, 0.76f, 0.82f, 0.88f);
            }
            case "revolver" -> {
                main.pitch = MathHelper.lerp(tilt, main.pitch, -1.5f); // стволом вверх — гильзы высыпаются
                float pouch = w(t, 0.16f, 0.26f, 0.3f, 0.36f);
                off.pitch += 0.8f * pouch;
                off.yaw -= 0.3f * sd * pouch;
                off.pitch -= 0.15f * w(t, 0.8f, 0.84f, 0.86f, 0.9f); // барабан на место
            }
            case "rpg" -> {
                float back = w(t, 0.05f, 0.22f, 0.3f, 0.52f);
                off.pitch = MathHelper.lerp(back, off.pitch, -2.7f);
                off.yaw = MathHelper.lerp(back, off.yaw, -0.3f * sd);
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ без ствола: убрал в кобуру, удары
    /** PlayerAnim: после смены ствола на «пусто» — рука к бедру (кобура); удары битой, ножом, кулаком. */
    public static void handsPose(BipedEntityModel<?> m, PlayerEntity p, float age) {
        if (p.getMainHandStack().getItem() instanceof GunItem) return;
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.world == null) return;
        float td = c.getRenderTickCounter().getTickDelta(false);
        Object[] h = HELD.get(p.getId());
        if (h != null && Boolean.TRUE.equals(h[2])) {
            float dt = c.world.getTime() - (long) h[1] + td;
            if (dt < 8) {
                float k = w(dt, 0, 3, 5, 8);
                m.rightArm.pitch = MathHelper.lerp(k, m.rightArm.pitch, 0.35f);
                m.rightArm.yaw = MathHelper.lerp(k, m.rightArm.yaw, 0.1f);
                m.rightArm.roll = MathHelper.lerp(k, m.rightArm.roll, 0.25f);
            }
        }
        float sw = m.handSwingProgress;
        if (sw <= 0) return;
        String kind = melee(p.getMainHandStack());
        if (kind == null) return;
        boolean right = p.getMainArm() == Arm.RIGHT;
        ModelPart main = right ? m.rightArm : m.leftArm, off = right ? m.leftArm : m.rightArm;
        float sd = right ? 1 : -1;
        switch (kind) {
            case "bat" -> {
                // двуручный удар: замах за плечо → широкая дуга → доворот корпуса
                float wind = w(sw, 0, 0.25f, 0.25f, 0.45f), hit = s(0.25f, 0.6f, sw), follow = w(sw, 0.5f, 0.7f, 0.8f, 1);
                float yaw = MathHelper.lerp(hit, 0.9f, -0.9f) * sd;
                float pitch = -1.35f - 0.5f * wind;
                main.pitch = pitch;
                off.pitch = pitch;
                main.yaw = yaw;
                off.yaw = yaw + 0.45f * sd;
                main.roll = off.roll = 0;
                m.body.yaw += (0.5f * wind - 0.6f * hit + 0.2f * follow) * sd;
                m.head.yaw -= 0.2f * hit * sd;
            }
            case "knife" -> {
                // быстрый выпад и полоснуть: рука вперёд, кисть по диагонали
                float stab = w(sw, 0, 0.2f, 0.4f, 0.8f), slash = s(0.3f, 0.75f, sw);
                main.pitch = -0.6f - 1.0f * stab - 0.3f * slash;
                main.yaw = (0.35f - 0.8f * slash) * sd;
                main.roll = 0.3f * slash * sd;
                off.pitch = -1.0f;
                off.yaw = 0.5f * sd;
                m.body.yaw += (-0.25f * stab + 0.2f * slash) * sd;
            }
            default -> {
                // кулак: джеб «от плеча», вторая рука — защита у лица
                float jab = w(sw, 0, 0.25f, 0.45f, 1);
                main.pitch = MathHelper.lerp(jab, -1.25f, -1.6f);
                main.yaw = (0.25f - 0.3f * jab) * sd;
                main.pivotZ -= 2.5f * jab;
                off.pitch = -1.7f;
                off.yaw = 0.45f * sd;
                m.body.yaw += -0.3f * jab * sd;
            }
        }
    }
}
