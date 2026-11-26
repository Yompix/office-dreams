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

    public static final String[] EMOTES = {"wave", "dance", "dance2", "point", "sit", "facepalm", "salute", "drink", "smoke"};
    public static final String[] EMOTE_NAMES = {"Помахать", "Танец", "Диско", "Показать", "Сесть", "Фейспалм", "Честь", "Выпить", "Закурить"};

    private static final class State {
        float lastBodyYaw = Float.NaN, lastT = -1;
        float stillSince, airSince = -1, landAt = -100, turnUntil = -1, turnSign = 1;
        double lastY = Double.NaN, vy;
        // 0.9.8: скорость по земле (блок/тик, сглаженная), наклон в поворот и при разгоне, ступенька
        double lastX = Double.NaN, lastZ;
        float speed, turnLean, accelLean, stepUntil = -1, stopAt = -100;
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
        // 0.9.8: звонок — телефон у уха; набирает текст (поле ввода в телефоне) — большие пальцы по экрану
        boolean call = com.yompix.novograd.client.story.CallClient.active();
        boolean typing = phone && c.currentScreen.getFocused() instanceof net.minecraft.client.gui.widget.TextFieldWidget;
        String want = call ? "phone_ear" : typing ? "phone_text" : phone ? "phone" : "";
        if (!want.equals(sentPhone)) {
            if (!want.isEmpty() || emoteOf(c.player).startsWith("phone")) play(want.isEmpty() ? "none" : want);
            sentPhone = want;
        }
        if (c.world.getTime() % 400 == 0) S.keySet().removeIf(u -> c.world.getPlayerByUuid(u) == null);
    }

    /** Свой жест: сразу у себя и пакет серверу (он разошлёт игрокам рядом). */
    public static void play(String id) {
        var p = MinecraftClient.getInstance().player;
        if (p == null) return;
        if (id.equals("drink") || id.equals("smoke")) { // 0.9.8: с предметом в руке — через сервер («Руки»), курить — только 18+
            NbtCompound n = new NbtCompound();
            n.putString("k", id);
            NovogradClient.send("hand_emote", n);
            return;
        }
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

    /** 0.9.8: «до» для роликов сравнения: -Dnovograd.oldgait=true — ванильные ноги. */
    static final boolean OLD_GAIT = Boolean.getBoolean("novograd.oldgait");
    static final float STANCE = 0.62f, LEG = 0.75f;

    /** Размах ноги: опорная стопа за 62 % цикла проходит назад ровно столько, сколько тело вперёд (tools/gait_metrics.py). */
    static float gaitAmp(float v, float s) {
        float w = 0.6662f * Math.max(s, 1e-3f);
        float T = MathHelper.TAU / w;
        return Math.min(1.2f, v * STANCE * T / (2 * LEG));
    }

    /** Угол ноги и подъём (пикс.) в фазе ph: опора — равномерно назад, перенос — плавно вперёд с подъёмом стопы. */
    static float[] gait(float ph, float A) {
        float u = (ph / MathHelper.TAU) % 1f;
        if (u < 0) u += 1;
        if (u < STANCE) return new float[]{A - 2 * A * (u / STANCE), 0};
        float k = (u - STANCE) / (1 - STANCE);
        return new float[]{-A + 2 * A * (0.5f - 0.5f * MathHelper.cos(MathHelper.PI * k)), 1.1f * MathHelper.sin(MathHelper.PI * k) * Math.min(1, A * 2)};
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
            // ступенька: на земле и на ходу поднялся — колено выше (6 тиков)
            if (p.isOnGround() && p.getY() - st.lastY > 0.3 && limbDistance > 0.1f) st.stepUntil = t + 6;
            st.lastY = p.getY();
            if (Double.isNaN(st.lastX)) {
                st.lastX = p.getX();
                st.lastZ = p.getZ();
            }
            float sp = (float) Math.sqrt((p.getX() - st.lastX) * (p.getX() - st.lastX) + (p.getZ() - st.lastZ) * (p.getZ() - st.lastZ)) / dtTick;
            st.lastX = p.getX();
            st.lastZ = p.getZ();
            float acc = (sp - st.speed);
            if (st.speed > 0.12f && sp < 0.03f) st.stopAt = t; // резко остановился — шаг-торможение
            st.speed += (sp - st.speed) * Math.min(1, 0.5f * dtTick);
            st.accelLean += (MathHelper.clamp(acc * 1.6f, -0.12f, 0.12f) - st.accelLean) * Math.min(1, 0.3f * dtTick);
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
        boolean gym = emote.startsWith("gym_");
        if (!emote.isEmpty() && !emote.startsWith("phone") && (moving && !emote.startsWith("dance") && !gym || riding || !ground && !gym || gun || et > 20 * (gym ? 90 : 30))) {
            st.emote = emote = ""; // пошёл, сел в машину, прыгнул, достал оружие — жест кончился (у всех одинаково)
        }
        if (emote.equals("wave") && et > 50) st.emote = emote = "";
        if ((emote.equals("point") || emote.equals("facepalm") || emote.equals("salute")) && et > 44) st.emote = emote = "";
        // ---------------------------------------------------------------- ходьба, бег, спринт
        if (ground && !riding && !p.isSwimming() && !flying) {
            float a = MathHelper.clamp(limbDistance, 0, 1);
            boolean sprint = p.isSprinting();
            float ph = limbAngle * 0.6662f;
            if (moving && !OLD_GAIT) {
                // 0.9.8: шаг без скольжения — опора 62 % цикла, нога идёт назад равномерно со скоростью тела
                float v = Math.max(0.02f, st.speed);
                float A = gaitAmp(v, limbDistance);
                float[] r = gait(ph, A), l = gait(ph + MathHelper.PI, A);
                m.rightLeg.pitch = r[0];
                m.leftLeg.pitch = l[0];
                m.rightLeg.pivotY -= r[1];
                m.leftLeg.pivotY -= l[1];
                if (armsFree) {
                    float armAmp = MathHelper.clamp(0.18f + v * 3.1f, 0.18f, 1.05f);
                    m.rightArm.pitch = MathHelper.cos(ph + MathHelper.PI) * armAmp;
                    m.leftArm.pitch = MathHelper.cos(ph) * armAmp;
                }
                // наклон в поворот (по скорости поворота и шага) и при разгоне
                float rate = Float.isNaN(st.lastBodyYaw) || dtTick <= 0 ? 0 : MathHelper.wrapDegrees(p.bodyYaw - st.lastBodyYaw) / dtTick;
                st.turnLean += (MathHelper.clamp(rate * v * 0.9f, -0.16f, 0.16f) - st.turnLean) * Math.min(1, 0.25f * Math.max(dtTick, 0.2f));
                m.body.roll -= st.turnLean;
                m.head.roll += st.turnLean * 0.5f; // голова держит горизонт
                m.body.pitch += st.accelLean;
            }
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
                // ---- шаг-торможение: корпус по инерции вперёд и обратно
                float sa = t - st.stopAt;
                if (sa >= 0 && sa < 10 && !OLD_GAIT) {
                    float k2 = MathHelper.sin(sa / 10f * MathHelper.PI) * (1 - sa / 10f);
                    m.body.pitch += 0.16f * k2;
                    m.rightLeg.pitch -= 0.3f * k2;
                    dy += 0.8f * k2;
                }
                // ---- мелкие движения головы (не каменная)
                if (!OLD_GAIT) {
                    float seed = (p.getUuid().hashCode() & 255) * 0.1f;
                    m.head.yaw += 0.025f * MathHelper.sin(t * 0.033f + seed) + 0.015f * MathHelper.sin(t * 0.091f + seed * 2);
                    m.head.pitch += 0.015f * MathHelper.sin(t * 0.047f + seed);
                }
                // ---- паузы после 8 с без дела
                float idle = t - st.stillSince;
                if (idle > 160 && armsFree && emote.isEmpty()) fidget(m, p, idle - 160);
            }
            st.lastBodyYaw = p.bodyYaw;
        } else st.lastBodyYaw = p.bodyYaw;
        // ---- ступенька: ведущая нога выше
        if (t < st.stepUntil && !OLD_GAIT) {
            float k = MathHelper.sin((1 - (st.stepUntil - t) / 6f) * MathHelper.PI);
            if (m.rightLeg.pitch < m.leftLeg.pitch) {
                m.rightLeg.pitch -= 0.5f * k;
                m.rightLeg.pivotY -= 1.2f * k;
            } else {
                m.leftLeg.pitch -= 0.5f * k;
                m.leftLeg.pivotY -= 1.2f * k;
            }
        }
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
        if (emote.equals("phone_ear") && !gun) {
            // 0.9.8: разговор: телефон у уха, локоть вниз-вперёд, свободная рука жестикулирует
            float in = smooth(et / 6f);
            m.rightArm.pitch = MathHelper.lerp(in, m.rightArm.pitch, -2.35f);
            m.rightArm.yaw = MathHelper.lerp(in, m.rightArm.yaw, -0.62f);
            m.rightArm.roll = MathHelper.lerp(in, m.rightArm.roll, 0.15f);
            m.head.roll += 0.1f * in;
            if (armsFree || moving) m.leftArm.pitch += -0.25f * in * Math.max(0, MathHelper.sin(et * 0.11f));
        } else if ((emote.equals("phone") || emote.equals("phone_text") || phoneItem) && !gun && !swinging) {
            m.rightArm.pitch = -1.05f;
            m.rightArm.yaw = -0.38f;
            m.rightArm.roll = 0;
            m.leftArm.pitch = -0.55f;
            m.leftArm.yaw = 0.42f;
            if (emote.equals("phone_text")) { // большие пальцы бегают по экрану
                m.rightArm.pitch += 0.035f * MathHelper.sin(et * 1.9f);
                m.leftArm.pitch += 0.035f * MathHelper.sin(et * 2.3f + 1);
                m.leftArm.pitch = -1.0f + 0.03f * MathHelper.sin(et * 2.3f + 1);
                m.leftArm.yaw = 0.3f;
            }
            m.head.pitch = MathHelper.clamp(m.head.pitch + 0.4f, -0.6f, 0.9f);
        }
        if (riding) ride(m, p, t, armsFree);
        // ---------------------------------------------------------------- жесты (V)
        if (!emote.isEmpty() && !emote.startsWith("phone")) dy += emote(m, emote, et);
        GunThird.handsPose(m, p, t); // 0.9.8: кобура, удары битой/ножом/кулаком
        // ---------------------------------------------------------------- голова: в пределах
        m.head.yaw = MathHelper.clamp(m.head.yaw, -1.3f, 1.3f);
        m.head.pitch = MathHelper.clamp(m.head.pitch, -1.2f, 1.2f);
        if (dy != 0) for (ModelPart part : new ModelPart[]{m.head, m.body, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg}) part.pivotY += dy;
        HandsClient.applyThird(m, p, t); // 0.9.8: «Руки» — банкомат, автомат, колонка… (руки тянутся к точкам)
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
            // ---- 0.9.6: спортзал (снаряды рисует client/style/GymProps)
            case "gym_curl" -> {
                float r = Math.max(0, MathHelper.sin(et * 0.22f)), l = Math.max(0, MathHelper.sin(et * 0.22f + MathHelper.PI));
                m.rightArm.pitch = MathHelper.lerp(in, m.rightArm.pitch, -0.15f - 1.75f * r);
                m.leftArm.pitch = MathHelper.lerp(in, m.leftArm.pitch, -0.15f - 1.75f * l);
                m.rightArm.roll = MathHelper.lerp(in, m.rightArm.roll, 0.08f);
                m.leftArm.roll = MathHelper.lerp(in, m.leftArm.roll, -0.08f);
                m.rightArm.yaw = m.leftArm.yaw = 0;
                m.body.pitch += 0.04f * (r + l) * in;
            }
            case "gym_bench", "gym_pushup" -> {
                // лёжа (корпус кладёт PlayerStyleScaleMixin): руки к грифу/полу, жим — плечи «ходят» вдоль тела
                float k = 0.5f + 0.5f * MathHelper.sin(et * 0.2f);
                m.rightArm.pitch = MathHelper.lerp(in, m.rightArm.pitch, -1.57f);
                m.leftArm.pitch = MathHelper.lerp(in, m.leftArm.pitch, -1.57f);
                m.rightArm.yaw = m.leftArm.yaw = 0;
                m.rightArm.roll = MathHelper.lerp(in, m.rightArm.roll, 0.12f);
                m.leftArm.roll = MathHelper.lerp(in, m.leftArm.roll, -0.12f);
                m.rightArm.pivotZ += 4.5f * (1 - k) * in;
                m.leftArm.pivotZ += 4.5f * (1 - k) * in;
                m.rightLeg.pitch = m.leftLeg.pitch = 0;
                m.head.pitch = 0;
            }
            case "gym_pullup" -> {
                float k = 0.5f + 0.5f * MathHelper.sin(et * 0.16f);
                m.rightArm.pitch = MathHelper.lerp(in, m.rightArm.pitch, -3.05f + 0.9f * k);
                m.leftArm.pitch = MathHelper.lerp(in, m.leftArm.pitch, -3.05f + 0.9f * k);
                m.rightArm.roll = MathHelper.lerp(in, m.rightArm.roll, -0.25f);
                m.leftArm.roll = MathHelper.lerp(in, m.leftArm.roll, 0.25f);
                m.rightLeg.pitch = 0.15f;
                m.leftLeg.pitch = 0.15f;
                return -6f * k * in;
            }
            case "gym_bag" -> {
                float t2 = et % 16f;
                boolean right = ((int) (et / 16f)) % 2 == 0;
                float punch = t2 < 4 ? t2 / 4f : t2 < 8 ? 1 - (t2 - 4) / 4f : 0;
                ModelPart hit = right ? m.rightArm : m.leftArm, guard = right ? m.leftArm : m.rightArm;
                hit.pitch = MathHelper.lerp(in, hit.pitch, -1.1f - 0.5f * punch);
                hit.yaw = (right ? 0.25f : -0.25f) * (1 - punch);
                guard.pitch = MathHelper.lerp(in, guard.pitch, -1.9f);
                guard.yaw = right ? -0.45f : 0.45f;
                m.body.yaw += (right ? -0.18f : 0.18f) * punch * in;
                m.rightLeg.pitch = 0.2f;
                m.leftLeg.pitch = -0.25f;
            }
            case "gym_run" -> {
                float s = MathHelper.sin(et * 0.62f);
                m.rightLeg.pitch = 0.95f * s * in;
                m.leftLeg.pitch = -0.95f * s * in;
                m.rightArm.pitch = -0.9f * s * in;
                m.leftArm.pitch = 0.9f * s * in;
                m.rightArm.roll = 0.12f;
                m.leftArm.roll = -0.12f;
                m.body.pitch += 0.12f * in;
                return 1.2f * Math.abs(s) * in;
            }
            case "gym_squat" -> {
                float k = 0.5f + 0.5f * MathHelper.sin(et * 0.14f - MathHelper.HALF_PI);
                m.rightArm.pitch = MathHelper.lerp(in, m.rightArm.pitch, -1.5f);
                m.leftArm.pitch = MathHelper.lerp(in, m.leftArm.pitch, -1.5f);
                m.rightArm.yaw = m.leftArm.yaw = 0;
                m.rightLeg.pitch = -1.1f * k * in;
                m.leftLeg.pitch = -1.1f * k * in;
                m.rightLeg.roll = 0.12f * k;
                m.leftLeg.roll = -0.12f * k;
                m.body.pitch += 0.35f * k * in;
                return 6.5f * k * in;
            }
            case "gym_yoga" -> {
                float b = MathHelper.sin(et * 0.05f);
                m.rightArm.pitch = MathHelper.lerp(in, m.rightArm.pitch, -3.0f);
                m.leftArm.pitch = MathHelper.lerp(in, m.leftArm.pitch, -3.0f);
                m.rightArm.roll = MathHelper.lerp(in, m.rightArm.roll, 0.18f);
                m.leftArm.roll = MathHelper.lerp(in, m.leftArm.roll, -0.18f);
                m.rightArm.yaw = m.leftArm.yaw = 0;
                m.leftLeg.pitch = MathHelper.lerp(in, 0, -0.35f);
                m.leftLeg.roll = MathHelper.lerp(in, 0, -0.75f);
                m.body.roll += 0.03f * b * in;
                m.head.pitch = 0.05f;
            }
            default -> {
            }
        }
        return 0;
    }
}
