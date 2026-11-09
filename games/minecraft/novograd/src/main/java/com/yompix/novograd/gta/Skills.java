package com.yompix.novograd.gta;

import com.yompix.novograd.Novograd;
import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.life.LifeData;
import com.yompix.novograd.life.LifeNet;
import com.yompix.novograd.life.StyleCatalog;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Навыки как в GTA San Andreas: растут от практики и правда меняют игру.
 * Очки 0..1000 (на экране — проценты). Сила и выносливость — те же, что качает зал «Железо» (LifeData.strength/stamina, 0..100):
 * практика добавляет к ним по десятым долям.
 * <ul>
 *   <li>Вождение / мотоцикл — сцепление и руль (CarEntity.drive), меньше заносит;</li>
 *   <li>Полёт — вертолёт и самолёт послушнее;</li>
 *   <li>Стрельба по классам — меньше разброс (Weapons.fire), отдача (GunFx) и быстрее перезарядка (GunItem.reload);</li>
 *   <li>Выносливость — дольше бег (меньше голод), быстрее бег (Style); сила — удар (Style);</li>
 *   <li>Плавание — быстрее в воде и дольше без воздуха; скрытность — тише стрельба с глушителем, звери подпускают ближе.</li>
 * </ul>
 */
public final class Skills {
    private Skills() {}

    public enum Skill {
        DRIVE("drive", "Вождение", "вождения", 0xFF28E6FF),
        BIKE("bike", "Мотоцикл", "езды на мотоцикле", 0xFFFF9F1C),
        FLY("fly", "Полёт", "пилотирования", 0xFF8EC5FF),
        PISTOL("pistol", "Пистолеты", "стрельбы из пистолета", 0xFFFF3B4A),
        SMG("smg", "Пистолеты-пулемёты", "стрельбы из ПП", 0xFFFF5A7A),
        SHOTGUN("shotgun", "Дробовики", "стрельбы из дробовика", 0xFFFF7A3B),
        RIFLE("rifle", "Винтовки", "стрельбы из винтовки", 0xFFE0463B),
        STAMINA("stamina", "Выносливость", "выносливости", 0xFF62E57E),
        STRENGTH("strength", "Сила", "силы", 0xFFFFC428),
        SWIM("swim", "Плавание", "плавания", 0xFF26C6DA),
        STEALTH("stealth", "Скрытность", "скрытности", 0xFFB04DFF);

        public final String id, title, genitive;
        public final int color;

        Skill(String id, String title, String genitive, int color) {
            this.id = id;
            this.title = title;
            this.genitive = genitive;
            this.color = color;
        }

        public static Skill of(String id) {
            for (Skill s : values()) if (s.id.equals(id)) return s;
            return null;
        }
    }

    public static final int MAX = 1000;
    /** Клиент подставляет сюда свои данные (физику машины игрока считает его клиент). */
    public static Supplier<LifeData> CLIENT = () -> null;

    // ------------------------------------------------------------------ уровни
    public static int points(LifeData d, Skill s) {
        if (d == null) return 0;
        return switch (s) {
            case STRENGTH -> MathHelper.clamp(d.strength * 10 + d.skills.getOrDefault("strength_f", 0), 0, MAX);
            case STAMINA -> MathHelper.clamp(d.stamina * 10 + d.skills.getOrDefault("stamina_f", 0), 0, MAX);
            default -> MathHelper.clamp(d.skills.getOrDefault(s.id, 0), 0, MAX);
        };
    }

    public static int percent(LifeData d, Skill s) {
        return points(d, s) / 10;
    }

    private static LifeData data(PlayerEntity p) {
        if (p == null) return null;
        if (p instanceof ServerPlayerEntity sp) return Life.get(sp);
        return p.getWorld().isClient ? CLIENT.get() : null;
    }

    /** 0..1 — насколько освоен навык у этого игрока (на сервере и на его клиенте). */
    public static float k(PlayerEntity p, Skill s) {
        return points(data(p), s) / (float) MAX;
    }

    /** Звание по проценту, как в SA: новичок → мастер (у стрельбы — «хитман»). */
    public static String rank(Skill s, int pct) {
        boolean gun = s == Skill.PISTOL || s == Skill.SMG || s == Skill.SHOTGUN || s == Skill.RIFLE;
        if (pct >= 100) return gun ? "Хитман" : "Мастер";
        if (pct >= 75) return "Профи";
        if (pct >= 50) return "Опытный";
        if (pct >= 25) return "Любитель";
        return "Новичок";
    }

    public static Skill gunSkill(int kind) {
        return switch (kind & 15) {
            case 1 -> Skill.SMG;
            case 2 -> Skill.SHOTGUN;
            case 3, 4 -> Skill.RIFLE;
            default -> Skill.PISTOL;
        };
    }

    // ------------------------------------------------------------------ польза (вызывают CarEntity, Heli/Plane, Weapons, GunItem, GunFx)
    /** Сцепление в заносе: новичок — как раньше, мастер — на треть цепче. */
    public static float gripMul(PlayerEntity p, boolean bike) {
        return 1 + 0.35f * k(p, bike ? Skill.BIKE : Skill.DRIVE);
    }

    /** Руль: мастер поворачивает на 12% резче. */
    public static float steerMul(PlayerEntity p, boolean bike) {
        return 1 + (bike ? 0.18f : 0.12f) * k(p, bike ? Skill.BIKE : Skill.DRIVE);
    }

    /** Наибольший угол заноса: у мастера машину не разворачивает (65° → 45°). */
    public static float maxSlide(PlayerEntity p) {
        return 65 - 20 * k(p, Skill.DRIVE);
    }

    /** Вертолёт и самолёт: поворот и разгон. */
    public static float flyMul(PlayerEntity p) {
        return 1 + 0.3f * k(p, Skill.FLY);
    }

    /** Разброс пуль: у хитмана вдвое кучнее (у пистолета ещё и на бегу без штрафа). */
    public static double spreadMul(PlayerEntity p, int kind) {
        float k = k(p, gunSkill(kind));
        double m = 1 - 0.45 * k;
        if ((kind & 15) == 0 && k >= 0.999f && p.getVelocity().horizontalLengthSquared() > 0.01) m *= 0.77;
        return m;
    }

    /** Отдача камеры и оружия (клиент). */
    public static float recoilMul(PlayerEntity p, int kind) {
        return 1 - 0.5f * k(p, gunSkill(kind));
    }

    /** Перезарядка магазином: до −30% (дробовик заряжается по патрону — без изменений). */
    public static float reloadMul(PlayerEntity p, int kind) {
        return 1 - 0.3f * k(p, gunSkill(kind));
    }

    /** Радиус, в котором свидетели замечают тихий выстрел: у мастера скрытности вдвое меньше. */
    public static double stealthMul(PlayerEntity p) {
        return 1 - 0.5 * k(p, Skill.STEALTH);
    }

    // ------------------------------------------------------------------ рост
    private static final class Track {
        Vec3d last;
        int cleanTicks;
        float lastExhaustion = -1;
        final Map<Skill, Float> acc = new HashMap<>();
    }

    private static final Map<UUID, Track> TRACK = new HashMap<>();

    public static void add(ServerPlayerEntity p, Skill s, float pts) {
        if (pts <= 0) return;
        Track t = TRACK.computeIfAbsent(p.getUuid(), u -> new Track());
        float a = t.acc.getOrDefault(s, 0f) + pts;
        int whole = (int) a;
        t.acc.put(s, a - whole);
        if (whole > 0) addPoints(p, s, whole);
    }

    /** Сразу целые очки (команды, автотест). Тост «навык повышен» — на каждые 5%. */
    public static void addPoints(ServerPlayerEntity p, Skill s, int pts) {
        LifeData d = Life.get(p);
        int before = points(d, s);
        if (before >= MAX) return;
        set(d, s, Math.min(MAX, before + pts));
        int after = points(d, s);
        if (after / 50 > before / 50) {
            NbtCompound n = new NbtCompound();
            n.putString("id", s.id);
            n.putInt("pct", after / 10);
            LifeNet.send(p, "out_skill", n);
            Life.sync(p);
            if (s == Skill.STRENGTH || s == Skill.STAMINA) com.yompix.novograd.life.Style.apply(p, true);
            if (after >= MAX) com.yompix.novograd.life.Achievements.give(p, "skill_" + s.id);
        }
    }

    public static void set(LifeData d, Skill s, int pts) {
        pts = MathHelper.clamp(pts, 0, MAX);
        switch (s) {
            case STRENGTH -> {
                d.strength = Math.min(StyleCatalog.STAT_MAX, pts / 10);
                d.skills.put("strength_f", pts % 10);
            }
            case STAMINA -> {
                d.stamina = Math.min(StyleCatalog.STAT_MAX, pts / 10);
                d.skills.put("stamina_f", pts % 10);
            }
            default -> d.skills.put(s.id, pts);
        }
    }

    /** Выстрел игрока (Weapons.fire): hit 1 — попал, 2 — убил. */
    public static void onShot(ServerPlayerEntity p, int kind, int hit) {
        if (p.isCreative() && !Boolean.getBoolean("novograd.skilltest")) return;
        Skill s = gunSkill(kind);
        add(p, s, 0.15f + (hit >= 1 ? 1.5f : 0) + (hit >= 2 ? 5f : 0));
    }

    /** Удар в ближнем бою: сила растёт по десятым. */
    public static void onMelee(ServerPlayerEntity p, boolean kill) {
        add(p, Skill.STRENGTH, kill ? 6 : 2);
    }

    /** Машина игрока врезалась (CarEntity.crashCheck): «чистая» езда начинается заново. */
    public static void onCrash(ServerPlayerEntity p) {
        Track t = TRACK.get(p.getUuid());
        if (t != null) t.cleanTicks = 0;
    }

    /** Раз в секунду: расстояние за рулём, время в воздухе, бег, плавание, скрытность; польза плавания и выносливости. */
    static void tick(MinecraftServer server) {
        if (server.getTicks() % 20 != 3) return;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p.isSpectator() || p.isDead()) continue;
            Track t = TRACK.computeIfAbsent(p.getUuid(), u -> new Track());
            Vec3d pos = p.getPos();
            double dist = t.last == null ? 0 : Math.hypot(pos.x - t.last.x, pos.z - t.last.z);
            if (dist > 60) dist = 0; // телепорт
            t.last = pos;
            boolean practice = !p.isCreative() || Boolean.getBoolean("novograd.skilltest");
            if (practice) grow(p, t, dist);
            apply(p, t);
        }
    }

    private static void grow(ServerPlayerEntity p, Track t, double dist) {
        if (p.getVehicle() instanceof CarEntity car && car.getControllingPassenger() == p) {
            if (car instanceof com.yompix.novograd.vehicle.HeliEntity || car instanceof com.yompix.novograd.vehicle.PlaneEntity) {
                if (!car.isOnGround() && !car.isTouchingWater()) add(p, Skill.FLY, 1.5f + (float) Math.min(dist, 40) * 0.02f);
            } else if (car instanceof com.yompix.novograd.vehicle.SpeedboatEntity) {
                // катер — без навыка
            } else if (car.isBike() || car.isScooter()) {
                if (dist > 3) add(p, Skill.BIKE, (float) dist / 22f);
                if (!car.isOnGround() && !car.isTouchingWater()) add(p, Skill.BIKE, 2.5f); // прыжки и «вилли»
            } else if (dist > 3) {
                t.cleanTicks += 20;
                float clean = t.cleanTicks > 600 ? 1.5f : 1f; // полминуты без аварий — быстрее
                add(p, Skill.DRIVE, (float) dist / 25f * clean);
            }
            return;
        }
        t.cleanTicks = 0;
        if (p.hasVehicle()) return;
        if (p.isTouchingWater() || p.isSwimming()) {
            add(p, Skill.SWIM, p.isSubmergedInWater() ? 1.5f : 1f);
            add(p, Skill.STAMINA, 0.3f);
            return;
        }
        if (p.isSprinting() && dist > 2) add(p, Skill.STAMINA, (float) dist / 10f);
        if (p.isSneaking() && !p.getServerWorld().getEntitiesByClass(CitizenEntity.class, p.getBoundingBox().expand(12, 4, 12),
                c -> c.isAlive() && c.getRole() != CitizenEntity.Role.GANG).isEmpty()) add(p, Skill.STEALTH, 0.8f);
    }

    private static final Identifier SWIM_ID = Novograd.id("skill_swim"), AIR_ID = Novograd.id("skill_air");

    private static void apply(ServerPlayerEntity p, Track t) {
        float swim = k(p, Skill.SWIM);
        modifier(p, EntityAttributes.GENERIC_WATER_MOVEMENT_EFFICIENCY, SWIM_ID, swim * 0.55);
        modifier(p, EntityAttributes.GENERIC_OXYGEN_BONUS, AIR_ID, swim * 3);
        // выносливость: голод от бега и прыжков копится медленнее — бегаешь дольше
        var hm = p.getHungerManager();
        float ex = hm.getExhaustion();
        if (t.lastExhaustion >= 0 && ex > t.lastExhaustion) {
            float cut = (ex - t.lastExhaustion) * 0.6f * k(p, Skill.STAMINA);
            hm.addExhaustion(-Math.min(cut, ex));
        }
        t.lastExhaustion = hm.getExhaustion();
    }

    private static void modifier(ServerPlayerEntity p, RegistryEntry<EntityAttribute> attr, Identifier id, double v) {
        var inst = p.getAttributeInstance(attr);
        if (inst == null) return;
        var cur = inst.getModifier(id);
        if (cur != null && Math.abs(cur.value() - v) < 1e-4) return;
        inst.removeModifier(id);
        if (Math.abs(v) > 1e-4) inst.addTemporaryModifier(new EntityAttributeModifier(id, v, EntityAttributeModifier.Operation.ADD_VALUE));
    }

    static void onLeave(ServerPlayerEntity p) {
        TRACK.remove(p.getUuid());
    }

    /** Урон в ближнем бою (без ствола в руке) — сила; вызывается из Outdoors. */
    static void afterDamage(LivingEntity victim, net.minecraft.entity.damage.DamageSource src, float taken) {
        if (taken <= 0 || !(src.getAttacker() instanceof ServerPlayerEntity p) || src.getSource() != p) return;
        if (Weapons.isGun(p.getMainHandStack()) || p.isCreative() && !Boolean.getBoolean("novograd.skilltest")) return;
        onMelee(p, victim.isDead());
    }

    // ------------------------------------------------------------------ для телефона и отладки
    public static NbtCompound describe(LifeData d) {
        NbtCompound n = new NbtCompound();
        for (Skill s : Skill.values()) n.putInt(s.id, points(d, s));
        return n;
    }

    public static String debug(ServerPlayerEntity p, String[] a) {
        LifeData d = Life.get(p);
        if (a.length >= 3 && a[1].equals("all")) {
            int v = Integer.parseInt(a[2]) * 10;
            for (Skill s : Skill.values()) set(d, s, v);
            Life.sync(p);
            com.yompix.novograd.life.Style.apply(p, true);
            return "Все навыки: " + a[2] + "%";
        }
        if (a.length >= 3) {
            Skill s = Skill.of(a[1]);
            if (s == null) return "Нет навыка " + a[1];
            int before = points(d, s), want = Integer.parseInt(a[2]) * 10;
            if (want > before) addPoints(p, s, want - before);
            else set(d, s, want);
            Life.sync(p);
            return s.title + ": " + percent(d, s) + "%";
        }
        StringBuilder sb = new StringBuilder("Навыки:");
        for (Skill s : Skill.values()) sb.append(' ').append(s.id).append('=').append(percent(d, s)).append('%');
        return sb.toString();
    }
}
