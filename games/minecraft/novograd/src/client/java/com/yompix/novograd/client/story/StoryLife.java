package com.yompix.novograd.client.story;

import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.life.Looks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 0.8: герои сюжета живут и вне кат-сцен (только клиент, только внешний вид — механика не меняется).
 * <ul>
 *   <li>свой набор «пауз»: курит, смотрит в телефон, на часы, поправляет воротник/волосы, скрещивает руки…;</li>
 *   <li>смотрят на игрока, когда он рядом (шея, мягко, с пределами);</li>
 *   <li>реагируют: машут, когда подходишь, кивают, если на них смотрят; с оружием в руках у игрока — отшатываются;</li>
 *   <li>своя походка (темп шага, раскачка корпуса);</li>
 *   <li>в кат-сценах и диалогах — поза по смыслу реплики ({@link #emotion}).</li>
 * </ul>
 * Выключается вместе с анимациями игроков (Настройки → «Живые анимации»).
 */
public final class StoryLife {
    private StoryLife() {}

    /** Профиль героя: паузы (жесты-слои), темп шага, раскачка корпуса в градусах, как здоровается. */
    private record Profile(List<String> idle, double stride, float sway, String hello, boolean timid) {}

    private static final Map<String, Profile> P = Map.ofEntries(
            Map.entry("gleb", new Profile(List.of("g_adjust", "g_hair", "g_phonecheck"), 1.06, 3.5f, "g_wave2", false)),
            Map.entry("nika", new Profile(List.of("g_stretch", "g_hair", "g_phonecheck"), 1.1, 2.5f, "g_wave2", false)),
            Map.entry("shtorm", new Profile(List.of("g_crossarms", "g_smoke"), 0.95, 4.5f, "g_nod", false)),
            Map.entry("kirpich", new Profile(List.of("g_crossarms", "g_stretch"), 0.88, 5.5f, "g_nod", false)),
            Map.entry("petrov", new Profile(List.of("g_watch", "g_adjust", "g_think"), 0.94, 1.5f, "g_nod", false)),
            Map.entry("tema", new Profile(List.of("g_phonecheck", "g_shrug", "g_hair"), 1.12, 2.0f, "g_wave2", true)),
            Map.entry("rosa", new Profile(List.of("g_hair", "g_crossarms", "g_watch"), 0.98, 3.0f, "g_wave2", false)),
            Map.entry("vince", new Profile(List.of("g_adjust", "g_smoke", "g_watch"), 0.92, 4.0f, "g_nod", false)),
            Map.entry("marco", new Profile(List.of("g_smoke", "g_crossarms"), 0.97, 5.0f, "g_nod", false)),
            Map.entry("harris", new Profile(List.of("g_watch", "g_crossarms"), 0.9, 1.5f, "g_salute", false)),
            Map.entry("lena", new Profile(List.of("g_phonecheck", "g_think", "g_hair"), 1.05, 2.0f, "g_wave2", true)),
            Map.entry("crow", new Profile(List.of("g_smoke", "g_adjust", "g_watch"), 0.86, 1.0f, "g_nod", false)),
            Map.entry("sofi", new Profile(List.of("g_hair", "g_phonecheck", "g_think"), 1.0, 2.5f, "g_wave2", true)),
            Map.entry("ray", new Profile(List.of("g_smoke", "g_phonecheck", "g_shrug"), 0.96, 3.0f, "g_wave2", false)),
            Map.entry("shakal", new Profile(List.of("g_smoke", "g_crossarms"), 0.9, 4.5f, "g_nod", false)),
            Map.entry("diaz", new Profile(List.of("g_watch", "g_crossarms", "g_adjust"), 1.02, 1.5f, "g_salute", false)));

    private static final java.util.Set<String> LOOPING = CitizenEntity.LOOP_GESTURES;

    private static final class State {
        int nextIdle = -1, helloAt = -100000, nodAt = -100000, scaredAt = -100000, lookedTicks;
        boolean near;
        float yaw, pitch;
        double lookW;
        long lastNs;
    }

    private static final Map<Integer, State> S = new HashMap<>();

    @org.jetbrains.annotations.Nullable
    public static String heroOf(CitizenEntity e) {
        int s = e.getSkin();
        return Looks.isStorySkin(s) ? Looks.STORY_IDS.get(s - Looks.STORY_FROM) : null;
    }

    public static boolean on() {
        return com.yompix.novograd.client.NovogradConfig.get().liveAnims;
    }

    /** Каждый клиентский тик. */
    public static void tick(MinecraftClient c) {
        if (c.world == null || c.player == null || !on()) return;
        if (c.world.getTime() % 200 == 0) S.keySet().removeIf(id -> c.world.getEntityById(id) == null);
        var p = c.player;
        boolean armed = p.getMainHandStack().getItem() instanceof com.yompix.novograd.item.GunItem;
        Vec3d eye = p.getEyePos(), look = p.getRotationVec(1f);
        for (CitizenEntity e : c.world.getEntitiesByClass(CitizenEntity.class, p.getBoundingBox().expand(40), x -> Looks.isStorySkin(x.getSkin()))) {
            String id = heroOf(e);
            Profile pr = id == null ? null : P.get(id);
            if (pr == null || e.isInvisible() || e.hasVehicle() || e.knockPhase() != CitizenEntity.KO_NONE) continue;
            State st = S.computeIfAbsent(e.getId(), k -> new State());
            boolean inScene = e.age < e.cineUntil || CutsceneScreen.ACTORS.containsValue(e.getId());
            boolean busy = e.cineGesture != null && e.age < e.cineGestUntil;
            if (inScene) continue;
            double d = e.squaredDistanceTo(p);
            Vec3d to = e.getEyePos().subtract(eye);
            double cos = to.lengthSquared() < 1e-6 ? 1 : to.normalize().dotProduct(look);
            boolean moving = e.getVelocity().horizontalLengthSquared() > 2.5e-4 || e.prevX != e.getX() || e.prevZ != e.getZ();
            // реакции на игрока
            if (armed && d < 7 * 7 && cos > 0.93 && e.age - st.scaredAt > 120) {
                gesture(e, pr.timid() ? "g_scared" : "g_stepback", pr.timid() ? 44 : 28);
                st.scaredAt = e.age;
                continue;
            }
            boolean nearNow = d < 5 * 5;
            if (nearNow && !st.near && !busy && e.age - st.helloAt > 20 * 120 && !moving) {
                gesture(e, pr.hello(), 32);
                st.helloAt = e.age;
            }
            st.near = nearNow;
            if (d < 3.5 * 3.5 && cos > 0.97) st.lookedTicks++;
            else st.lookedTicks = 0;
            if (st.lookedTicks > 30 && !busy && e.age - st.nodAt > 20 * 20) {
                gesture(e, "g_nod", 18);
                st.nodAt = e.age;
                st.lookedTicks = 0;
            }
            // паузы: стоит без дела — время от времени свой жест
            if (moving || busy || e.getActivity() != CitizenEntity.Activity.IDLE) {
                if (st.nextIdle < e.age + 40) st.nextIdle = e.age + 60 + Math.floorMod(e.getId() * 37 + e.age, 120);
                continue;
            }
            if (st.nextIdle < 0) st.nextIdle = e.age + 40 + Math.floorMod(e.getId() * 53, 160);
            if (e.age >= st.nextIdle) {
                int k = Math.floorMod(e.getId() * 31 + e.age / 7, pr.idle().size());
                String g = pr.idle().get(k);
                gesture(e, g, LOOPING.contains(g) ? 70 + Math.floorMod(e.age, 60) : 40);
                st.nextIdle = e.age + 160 + Math.floorMod(e.getId() * 17 + e.age, 200);
            }
        }
    }

    private static void gesture(CitizenEntity e, String g, int ticks) {
        e.cineGesture = g;
        e.cineGestUntil = e.age + ticks;
    }

    /** Темп шага героя (своя походка) — множитель к темпу анимации ходьбы; 1 — не герой. */
    public static double stride(CitizenEntity e) {
        String id = heroOf(e);
        Profile pr = id == null ? null : P.get(id);
        return pr == null || !on() ? 1 : pr.stride();
    }

    /** Раскачка корпуса героя на ходу, градусы (0 — не герой). */
    public static float sway(CitizenEntity e) {
        String id = heroOf(e);
        Profile pr = id == null ? null : P.get(id);
        return pr == null || !on() ? 0 : pr.sway();
    }

    /**
     * Взгляд героя на игрока, когда тот рядом (до 6 блоков): [курс, наклон] шеи относительно тела, или null.
     * Мягко (≈0.25 с), не больше 65° вбок и 30° вверх/вниз; отошёл — голова плавно возвращается.
     */
    @org.jetbrains.annotations.Nullable
    public static float[] look(LivingEntity le, float tickDelta) {
        if (!(le instanceof CitizenEntity e) || !on() || heroOf(e) == null) return null;
        var p = MinecraftClient.getInstance().player;
        if (p == null) return null;
        State st = S.computeIfAbsent(e.getId(), k -> new State());
        long now = System.nanoTime();
        float dt = st.lastNs == 0 ? 0 : MathHelper.clamp((now - st.lastNs) / 1e9f, 0, 0.1f);
        st.lastNs = now;
        double d = e.squaredDistanceTo(p);
        boolean want = d < 6 * 6 && !p.isSpectator() && e.knockPhase() == CitizenEntity.KO_NONE;
        float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, e.prevBodyYaw, e.bodyYaw);
        float ty = 0, tp = 0;
        if (want) {
            Vec3d a = e.getLerpedPos(tickDelta).add(0, e.getStandingEyeHeight(), 0), b = p.getLerpedPos(tickDelta).add(0, p.getStandingEyeHeight(), 0);
            float yaw = (float) Math.toDegrees(Math.atan2(-(b.x - a.x), b.z - a.z));
            float rel = MathHelper.wrapDegrees(yaw - bodyYaw);
            if (Math.abs(rel) > 110) want = false; // игрок за спиной — не выворачиваем шею
            ty = MathHelper.clamp(rel, -65, 65);
            tp = (float) MathHelper.clamp(-Math.toDegrees(Math.atan2(b.y - a.y, Math.hypot(b.x - a.x, b.z - a.z))), -30, 30);
        }
        float k = 1 - (float) Math.exp(-dt * 7);
        st.lookW += ((want ? 1 : 0) - st.lookW) * k;
        st.yaw += (ty - st.yaw) * k;
        st.pitch += (tp - st.pitch) * k;
        if (st.lookW < 0.01) return null;
        return new float[]{st.yaw, st.pitch, (float) st.lookW};
    }

    // ===================================================================== эмоция реплики
    /**
     * Поза по смыслу реплики (тег по словам и знакам): злость, грусть, смех, испуг, «не знаю», «да», «нет».
     * null — нейтрально (обычная жестикуляция).
     */
    @org.jetbrains.annotations.Nullable
    public static String emotion(String text) {
        if (text == null || text.isEmpty()) return null;
        String t = text.toLowerCase(java.util.Locale.ROOT);
        int bangs = 0;
        for (char ch : t.toCharArray()) if (ch == '!') bangs++;
        if (has(t, "боюсь", "страшно", "помогите", "не стреляй", "не надо", "осторожно", "бежим", "спасите", "о боже")) return "g_scared";
        if (has(t, "ха-ха", "хаха", "ахах", "ха!", "смешно", "шучу", "весело", "умора")) return "g_laugh";
        if (has(t, "убью", "заткнись", "чёрт", "черт", "проклят", "ненавиж", "идиот", "сволоч", "урод", "тварь", "сдохн", "достал") || bangs >= 2)
            return "g_angry";
        if (has(t, "жаль", "прости", "умер", "погиб", "потерял", "грустн", "больно", "одна ", "один ", "плакал", "похорон", "скучаю"))
            return "g_sad";
        if (has(t, "не знаю", "кто знает", "может быть", "без понятия", "как знать")) return "g_shrug";
        if (t.startsWith("да") || has(t, "договорились", "по рукам", "хорошо,", "ладно", "согласен", "согласна", "конечно")) return "g_nod";
        if (t.startsWith("нет") || has(t, "ни за что", "даже не думай", "забудь")) return "g_headshake";
        return null;
    }

    private static boolean has(String t, String... w) {
        for (String s : w) if (t.contains(s)) return true;
        return false;
    }
}
