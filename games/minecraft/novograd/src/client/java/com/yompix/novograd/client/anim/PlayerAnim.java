package com.yompix.novograd.client.anim;

import com.yompix.novograd.client.NovogradClient;
import com.yompix.novograd.client.NovogradConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 0.8: живые анимации игроков (у себя от третьего лица и у друзей в кооперативе) — слоями поверх ванильной модели
 * (миксин {@code PlayerAnimMixin} в конце {@code PlayerEntityModel.setAngles}: броня, предметы в руках, причёски,
 * Sodium и Iris берут уже изменённые части модели).
 *
 * <p>Слои по порядку:
 * <ol>
 *   <li>ходьба/бег/спринт: покачивание тела вниз-вверх по шагу, разворот плеч против бёдер, наклон вперёд на спринте,
 *       руки шире на бегу, голова держит горизонт; присед — мягче;</li>
 *   <li>стоит: дыхание, перенос веса; после 8 с — паузы (оглядывается, потягивается, смотрит на часы, переминается);</li>
 *   <li>разворот на месте — переступает ногами; прыжок — руки в стороны, ноги поджаты; приземление — присед;
 *       долгое падение — машет руками; в воде — гребёт;</li>
 *   <li>телефон в руке/открыт — смотрит в экран; за рулём — руки на руле (руль поворачивает); сидя — руки на коленях;</li>
 *   <li>жесты (V): помахать, два танца, показать, сесть, фейспалм, отдать честь — видят все игроки рядом.</li>
 * </ol>
 * С оружием в руке руки не трогаем (позу прицела ставит {@code GunFx}). Выключается: Настройки → «Живые анимации».
 */
public final class PlayerAnim {
    private PlayerAnim() {}

    public static final String[] EMOTES = {"wave", "dance", "dance2", "point", "sit", "facepalm", "salute"};
    public static final String[] EMOTE_NAMES = {"Помахать", "Танец", "Диско", "Показать", "Сесть", "Фейспалм", "Честь"};

    private static final class State {
        float lastBodyYaw = Float.NaN, lastT = -1;
        float stillSince, airSince = -1, landAt = -100, turnUntil = -1, turnSign = 1;
        double lastY = Double.NaN, vy;
        String emote = "";
        float emoteAt;
        boolean wasAir;
    }

    private static final Map<UUID, State> S = new HashMap<>();
    public static KeyBinding emoteKey;
    private static String sentPhone = "";

    public static void init() {
        emoteKey = net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.novograd.emote", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, "category.novograd"));
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(PlayerAnim::tick);
    }

    private static void tick(MinecraftClient c) {
        if (c.player == null || c.world == null) return;
        while (emoteKey.wasPressed())
            if (c.currentScreen == null && NovogradConfig.get().liveAnims) c.setScreen(new EmoteWheelScreen());
        // телефон открыт — остальные видят, что ты в телефоне
        boolean phone = c.currentScreen instanceof com.yompix.novograd.client.screen.PhoneScreen;
        String want = phone ? "phone" : "";
        if (!want.equals(sentPhone)) {
            if (phone || "phone".equals(emoteOf(c.player))) play(want.isEmpty() ? "none" : want);
            sentPhone = want;
        }
        if (c.world.getTime() % 400 == 0) S.keySet().removeIf(u -> c.world.getPlayerByUuid(u) == null);
    }

    /** Свой жест: сразу у себя и пакет серверу (он разошлёт игрокам рядом). */
    public static void play(String id) {
        var p = MinecraftClient.getInstance().player;
        if (p == null) return;
        set(p.getUuid(), id);
        NbtCompound n = new NbtCompound();
        n.putString("id", id);
        NovogradClient.send("emote", n);
    }

    /** Пакет «emote» от сервера: жест другого игрока. */
    public static void receive(NbtCompound n) {
        if (n.containsUuid("who")) set(n.getUuid("who"), n.getString("id"));
    }

    private static void set(UUID who, String id) {
        State st = S.computeIfAbsent(who, k -> new State());
        st.emote = "none".equals(id) ? "" : id;
        st.emoteAt = -1; // время начала поставим в первом кадре (часы модели — возраст игрока)
    }

    public static String emoteOf(PlayerEntity p) {
        State st = S.get(p.getUuid());
        return st == null ? "" : st.emote;
    }

    // ===================================================================== слои
    private static float smooth(float x) {
        x = MathHelper.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    /** Окно «вход–держит–выход» по времени t (тики) в отрезке [0, len], вход/выход по `ramp` тиков. */
    private static float env(float t, float len, float ramp) {
        if (t < 0 || t > len) return 0;
        return Math.min(smooth(t / ramp), smooth((len - t) / ramp));
    }

    public static void apply(PlayerEntityModel<?> m, LivingEntity le, float limbAngle, float limbDistance, float age, float headYaw, float headPitch) {
        // крен головы и корпуса ваниль не сбрасывает (модель общая на всех игроков) — сбрасываем сами
        if (!(le.getMainHandStack().getItem() instanceof com.yompix.novograd.item.GunItem)) m.head.roll = 0; // с оружием крен головы ставит GunFx
        m.body.roll = 0;
        if (!(le instanceof PlayerEntity p) || !NovogradConfig.get().liveAnims) {
            m.hat.copyTransform(m.head);
            m.jacket.copyTransform(m.body);
            return;
        }
        // рука от первого лица (PlayerEntityRenderer.renderArm) зовёт setAngles с нулями — её не трогаем
        if (age == 0 && limbAngle == 0 && limbDistance == 0 && headYaw == 0 && headPitch == 0) return;
        State st = S.computeIfAbsent(p.getUuid(), k -> new State());
        float t = age;
        float dtTick = st.lastT < 0 ? 0 : MathHelper.clamp(t - st.lastT, 0, 4);
        st.lastT = t;
        // вертикальная скорость — по позиции (у чужих игроков скорость с сервера не приходит)
        if (Double.isNaN(st.lastY)) st.lastY = p.getY();
        if (dtTick > 0) {
            st.vy = (p.getY() - st.lastY) / dtTick;
            st.lastY = p.getY();
        }
        boolean gun = p.getMainHandStack().getItem() instanceof com.yompix.novograd.item.GunItem;
        boolean swinging = m.handSwingProgress > 0;
        boolean riding = p.hasVehicle();
        boolean flying = p.getAbilities().flying || p.isFallFlying();
        boolean water = p.isTouchingWater() && !p.isOnGround();
        boolean ground = p.isOnGround() || riding;
        boolean moving = limbDistance > 0.08f;
        boolean armsFree = !gun && !swinging && !p.isUsingItem() && !p.isSpectator();
        if (moving || riding || !ground) st.stillSince = t;
        float dy = 0;
        // ---------------------------------------------------------------- жест (синхронный)
        String emote = st.emote;
        if (!emote.isEmpty() && st.emoteAt < 0) st.emoteAt = t;
        float et = t - st.emoteAt;
        if (!emote.isEmpty() && !emote.equals("phone") && (moving && !emote.startsWith("dance") || riding || !ground || gun || et > 20 * 30)) {
            st.emote = emote = ""; // пошёл, сел в машину, прыгнул, достал оружие — жест кончился (у всех одинаково)
        }
        if (emote.equals("wave") && et > 50) st.emote = emote = "";
        if ((emote.equals("point") || emote.equals("facepalm") || emote.equals("salute")) && et > 44) st.emote = emote = "";
        // ---------------------------------------------------------------- ходьба, бег, спринт
        if (ground && !riding && !p.isSwimming() && !flying) {
            float a = MathHelper.clamp(limbDistance, 0, 1);
            boolean sprint = p.isSprinting();
            float ph = limbAngle * 0.6662f;
            if (moving) {
                float k = m.sneaking ? 0.5f : 1f;
                // тело ниже, когда ноги разведены (двойная опора), — шаг «садится», а не плывёт
                float legA = Math.abs(m.rightLeg.pitch);
                dy += 12 * (1 - MathHelper.cos(Math.min(legA, 1.2f))) * 0.7f * k;
                float twist = MathHelper.sin(ph) * (sprint ? 0.16f : 0.09f) * a * k;
                float lean = (sprint ? 0.2f : 0.045f) * a * (m.sneaking ? 0 : 1);
                if (!gun) twistBody(m, twist);
                m.body.pitch += lean;
                m.rightLeg.pivotZ += 12 * MathHelper.sin(lean);
                m.leftLeg.pivotZ += 12 * MathHelper.sin(lean);
                m.head.pitch -= lean * 0.6f;
                if (armsFree) {
                    float boost = sprint ? 1.3f : 1.0f;
                    m.rightArm.pitch = m.rightArm.pitch * boost + lean * 0.6f;
                    m.leftArm.pitch = m.leftArm.pitch * boost + lean * 0.6f;
                    m.rightArm.roll += (sprint ? 0.1f : 0.04f) * a;
                    m.leftArm.roll -= (sprint ? 0.1f : 0.04f) * a;
                    if (m.sneaking) {
                        m.rightArm.pitch -= 0.2f;
                        m.leftArm.pitch -= 0.2f;
                    }
                }
            } else if (!m.sneaking) {
                // ---- стоит: дыхание и перенос веса
                float br = MathHelper.sin(t * MathHelper.TAU / 76f);
                float sh = MathHelper.sin(t * MathHelper.TAU / 190f);
                if (armsFree) {
                    m.rightArm.roll += 0.03f + 0.025f * br;
                    m.leftArm.roll -= 0.03f + 0.025f * br;
                }
                m.head.pitch += 0.018f * br;
                dy += 0.18f * (br + 1);
                m.body.roll += 0.02f * sh;
                m.rightLeg.roll += 0.025f * sh;
                m.leftLeg.roll += 0.025f * sh;
                // ---- разворот на месте: переступает ногами
                float by = p.bodyYaw;
                if (!Float.isNaN(st.lastBodyYaw) && dtTick > 0) {
                    float dYaw = MathHelper.wrapDegrees(by - st.lastBodyYaw) / dtTick;
                    if (Math.abs(dYaw) > 2.5f) {
                        st.turnUntil = t + 9;
                        st.turnSign = Math.signum(dYaw);
                    }
                }
                if (t < st.turnUntil) {
                    float k = (st.turnUntil - t) / 9f, s = MathHelper.sin((9 - (st.turnUntil - t)) / 9f * MathHelper.PI * 2);
                    m.rightLeg.pitch += 0.32f * s * k;
                    m.leftLeg.pitch -= 0.32f * s * k;
                    m.rightLeg.yaw += 0.15f * st.turnSign * k;
                    m.leftLeg.yaw += 0.15f * st.turnSign * k;
                }
                // ---- паузы после 8 с без дела
                float idle = t - st.stillSince;
                if (idle > 160 && armsFree && emote.isEmpty()) fidget(m, p, idle - 160);
            }
            st.lastBodyYaw = p.bodyYaw;
        } else st.lastBodyYaw = p.bodyYaw;
        // ---------------------------------------------------------------- прыжок, приземление, падение, вода
        boolean air = !ground && !water && !flying && !p.isClimbing();
        if (air && !st.wasAir) st.airSince = t;
        if (!air && st.wasAir && t - st.airSince > 5 && !water) st.landAt = t;
        st.wasAir = air;
        if (air) {
            float up = (float) MathHelper.clamp(st.vy * 4, -1, 1);
            float fall = (float) MathHelper.clamp((-st.vy - 0.5) * 1.5, 0, 1) * smooth((p.fallDistance - 2) / 3);
            if (armsFree) {
                m.rightArm.roll += 0.28f * Math.max(up, 0.3f) + fall * (1.0f + 0.35f * MathHelper.sin(t * 0.9f));
                m.leftArm.roll -= 0.28f * Math.max(up, 0.3f) + fall * (1.0f + 0.35f * MathHelper.sin(t * 0.9f + 1.7f));
                m.rightArm.pitch += -0.3f * Math.max(up, 0) - fall * (0.5f + 0.4f * MathHelper.sin(t * 1.3f));
                m.leftArm.pitch += -0.3f * Math.max(up, 0) - fall * (0.5f + 0.4f * MathHelper.sin(t * 1.3f + 2f));
            }
            m.rightLeg.pitch = MathHelper.lerp(0.7f, m.rightLeg.pitch, -0.45f + fall * 0.5f * MathHelper.sin(t * 0.8f));
            m.leftLeg.pitch = MathHelper.lerp(0.7f, m.leftLeg.pitch, 0.2f - fall * 0.5f * MathHelper.sin(t * 0.8f));
        }
        float lt = t - st.landAt;
        if (lt >= 0 && lt < 8) {
            float k = 1 - lt / 8f;
            dy += 2.2f * k * k;
            m.body.pitch += 0.12f * k;
            if (armsFree) {
                m.rightArm.pitch -= 0.35f * k;
                m.leftArm.pitch -= 0.35f * k;
            }
        }
        if (water && !p.isSwimming()) { // держится на воде: гребёт руками, ноги работают
            if (armsFree) {
                m.rightArm.roll += 0.9f + 0.4f * MathHelper.sin(t * 0.25f);
                m.leftArm.roll -= 0.9f + 0.4f * MathHelper.sin(t * 0.25f);
                m.rightArm.pitch += -0.25f * MathHelper.sin(t * 0.25f + 1);
                m.leftArm.pitch += -0.25f * MathHelper.sin(t * 0.25f + 1);
            }
            m.rightLeg.pitch = 0.4f * MathHelper.sin(t * 0.5f);
            m.leftLeg.pitch = -0.4f * MathHelper.sin(t * 0.5f);
        }
        // ---------------------------------------------------------------- телефон, руль, сидя
        boolean phoneItem = p.getMainHandStack().getItem() instanceof com.yompix.novograd.item.PhoneItem;
        if ((emote.equals("phone") || phoneItem) && !gun && !swinging) {
            m.rightArm.pitch = -1.05f;
            m.rightArm.yaw = -0.38f;
            m.rightArm.roll = 0;
            m.leftArm.pitch = -0.55f;
            m.leftArm.yaw = 0.42f;
            m.head.pitch = MathHelper.clamp(m.head.pitch + 0.4f, -0.6f, 0.9f);
        }
        if (riding) ride(m, p, t, armsFree);
        // ---------------------------------------------------------------- жесты (V)
        if (!emote.isEmpty() && !emote.equals("phone")) dy += emote(m, emote, et);
        // ---------------------------------------------------------------- голова: в пределах
        m.head.yaw = MathHelper.clamp(m.head.yaw, -1.3f, 1.3f);
        m.head.pitch = MathHelper.clamp(m.head.pitch, -1.2f, 1.2f);
        if (dy != 0) for (ModelPart part : new ModelPart[]{m.head, m.body, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg}) part.pivotY += dy;
        // оверлеи скина (рукава, штанины, куртка, шапка) — за частями
        m.hat.copyTransform(m.head);
        m.leftPants.copyTransform(m.leftLeg);
        m.rightPants.copyTransform(m.rightLeg);
        m.leftSleeve.copyTransform(m.leftArm);
        m.rightSleeve.copyTransform(m.rightArm);
        m.jacket.copyTransform(m.body);
    }

    /** Разворот плеч: корпус и руки (их точки крепления едут по дуге, как в ванильном замахе). */
    private static void twistBody(PlayerEntityModel<?> m, float yaw) {
        m.body.yaw += yaw;
        m.rightArm.pivotZ = MathHelper.sin(m.body.yaw) * 5;
        m.rightArm.pivotX = -MathHelper.cos(m.body.yaw) * 5;
        m.leftArm.pivotZ = -MathHelper.sin(m.body.yaw) * 5;
        m.leftArm.pivotX = MathHelper.cos(m.body.yaw) * 5;
        m.rightArm.yaw += yaw;
        m.leftArm.yaw += yaw;
    }

    /** Паузы стоя (детерминированы временем — у всех игроков в кадре одинаково). */
    private static void fidget(PlayerEntityModel<?> m, PlayerEntity p, float idle) {
        float cycle = 260; // 13 с: пауза — 3 с, дальше просто стоит
        int n = (int) (idle / cycle);
        float lt = idle - n * cycle;
        int kind = Math.floorMod(n * 7 + p.getUuid().hashCode(), 4);
        float e = env(lt, 60, 12);
        if (e <= 0) return;
        switch (kind) {
            case 0 -> m.head.yaw += 0.6f * MathHelper.sin(lt / 60f * MathHelper.TAU) * e; // оглядывается
            case 1 -> { // потягивается
                m.rightArm.pitch = MathHelper.lerp(e, m.rightArm.pitch, -2.9f);
                m.leftArm.pitch = MathHelper.lerp(e, m.leftArm.pitch, -2.9f);
                m.rightArm.roll += 0.25f * e;
                m.leftArm.roll -= 0.25f * e;
                m.head.pitch -= 0.3f * e;
            }
            case 2 -> { // смотрит на часы
                m.leftArm.pitch = MathHelper.lerp(e, m.leftArm.pitch, -1.25f);
                m.leftArm.yaw = MathHelper.lerp(e, m.leftArm.yaw, 0.65f);
                m.head.pitch += 0.45f * e;
                m.head.yaw += 0.3f * e;
            }
            default -> { // переминается
                float s = MathHelper.sin(lt / 30f * MathHelper.TAU);
                m.rightLeg.pitch += 0.18f * s * e;
                m.leftLeg.pitch -= 0.18f * s * e;
                m.body.roll += 0.04f * s * e;
            }
        }
    }

    private static void ride(PlayerEntityModel<?> m, PlayerEntity p, float t, boolean armsFree) {
        var v = p.getVehicle();
        if (v instanceof com.yompix.novograd.entity.CarEntity car) {
            boolean driver = car.getFirstPassenger() == p;
            if (!armsFree) return;
            if (driver) {
                // руки на руле; руль поворачивает за машиной
                float steer = MathHelper.clamp(MathHelper.wrapDegrees(car.getYaw() - car.prevYaw) / 6f, -1, 1);
                m.rightArm.pitch = -1.15f + 0.18f * steer;
                m.leftArm.pitch = -1.15f - 0.18f * steer;
                m.rightArm.yaw = -0.28f;
                m.leftArm.yaw = 0.28f;
                m.rightArm.roll = 0.05f * steer;
                m.leftArm.roll = 0.05f * steer;
                m.head.roll += 0.06f * steer;
            } else {
                m.rightArm.pitch = -0.45f;
                m.leftArm.pitch = -0.45f;
                m.rightArm.roll = -0.12f;
                m.leftArm.roll = 0.12f;
            }
        } else if (v instanceof com.yompix.novograd.entity.SeatEntity && armsFree) { // сидит: руки на коленях, дышит
            float br = MathHelper.sin(t * MathHelper.TAU / 80f);
            m.rightArm.pitch = -0.55f + 0.02f * br;
            m.leftArm.pitch = -0.55f + 0.02f * br;
            m.rightArm.roll = -0.12f;
            m.leftArm.roll = 0.12f;
        }
    }

    /** Жест V: возвращает сдвиг тела по вертикали (пиксели модели). */
    private static float emote(PlayerEntityModel<?> m, String id, float et) {
        float in = smooth(et / 6f);
        switch (id) {
            case "wave" -> {
                float e = env(et, 50, 7);
                m.rightArm.pitch = MathHelper.lerp(e, m.rightArm.pitch, -2.85f);
                m.rightArm.roll = MathHelper.lerp(e, m.rightArm.roll, 0.15f + 0.35f * MathHelper.sin(et * 0.55f));
                m.rightArm.yaw = MathHelper.lerp(e, m.rightArm.yaw, 0);
                m.head.roll += 0.06f * e;
            }
            case "dance" -> {
                float s = MathHelper.sin(et * 0.42f), c = MathHelper.cos(et * 0.42f);
                m.rightArm.pitch = MathHelper.lerp(in, m.rightArm.pitch, -1.1f + 0.6f * s);
                m.leftArm.pitch = MathHelper.lerp(in, m.leftArm.pitch, -1.1f - 0.6f * s);
                m.rightArm.roll = MathHelper.lerp(in, m.rightArm.roll, 0.3f);
                m.leftArm.roll = MathHelper.lerp(in, m.leftArm.roll, -0.3f);
                m.rightLeg.pitch = 0.35f * s * in;
                m.leftLeg.pitch = -0.35f * s * in;
                m.body.yaw += 0.22f * MathHelper.sin(et * 0.21f) * in;
                m.head.roll += 0.14f * c * in;
                return 1.4f * Math.abs(s) * in;
            }
            case "dance2" -> {
                boolean up = ((int) (et / 10)) % 2 == 0;
                float k = smooth((et % 10) / 4f);
                float tp = up ? -2.6f : 0.25f, tr = up ? -0.55f : 0.35f;
                m.rightArm.pitch = MathHelper.lerp(in, m.rightArm.pitch, MathHelper.lerp(k, up ? 0.25f : -2.6f, tp));
                m.rightArm.roll = MathHelper.lerp(in, m.rightArm.roll, MathHelper.lerp(k, up ? 0.35f : -0.55f, tr));
                m.leftArm.roll = MathHelper.lerp(in, m.leftArm.roll, -0.35f);
                m.leftArm.pitch = MathHelper.lerp(in, m.leftArm.pitch, -0.2f);
                float hip = MathHelper.sin(et * 0.31f);
                m.body.roll += 0.1f * hip * in;
                m.rightLeg.roll += 0.12f * hip * in;
                m.leftLeg.roll += 0.12f * hip * in;
                m.rightLeg.pitch = -0.25f * Math.max(0, hip) * in;
                return 0.8f * Math.abs(hip) * in;
            }
            case "point" -> {
                float e = env(et, 44, 6);
                m.rightArm.pitch = MathHelper.lerp(e, m.rightArm.pitch, -1.5f + m.head.pitch * 0.8f);
                m.rightArm.yaw = MathHelper.lerp(e, m.rightArm.yaw, m.head.yaw * 0.8f);
                m.rightArm.roll = MathHelper.lerp(e, m.rightArm.roll, 0);
            }
            case "sit" -> {
                m.rightLeg.pitch = MathHelper.lerp(in, m.rightLeg.pitch, -1.45f);
                m.leftLeg.pitch = MathHelper.lerp(in, m.leftLeg.pitch, -1.45f);
                m.rightLeg.yaw = MathHelper.lerp(in, m.rightLeg.yaw, 0.15f);
                m.leftLeg.yaw = MathHelper.lerp(in, m.leftLeg.yaw, -0.15f);
                m.rightArm.pitch = MathHelper.lerp(in, m.rightArm.pitch, 0.35f);
                m.leftArm.pitch = MathHelper.lerp(in, m.leftArm.pitch, 0.35f);
                m.rightArm.roll = MathHelper.lerp(in, m.rightArm.roll, 0.25f);
                m.leftArm.roll = MathHelper.lerp(in, m.leftArm.roll, -0.25f);
                m.body.pitch -= 0.12f * in;
                return 10.5f * in;
            }
            case "facepalm" -> {
                float e = env(et, 44, 7);
                m.rightArm.pitch = MathHelper.lerp(e, m.rightArm.pitch, -2.05f);
                m.rightArm.yaw = MathHelper.lerp(e, m.rightArm.yaw, -0.55f);
                m.head.pitch += 0.38f * e;
            }
            case "salute" -> {
                float e = env(et, 44, 6);
                m.rightArm.pitch = MathHelper.lerp(e, m.rightArm.pitch, -2.25f);
                m.rightArm.yaw = MathHelper.lerp(e, m.rightArm.yaw, -0.85f);
                m.rightArm.roll = MathHelper.lerp(e, m.rightArm.roll, 0.1f);
                m.head.pitch -= 0.05f * e;
            }
            default -> {
            }
        }
        return 0;
    }
}
