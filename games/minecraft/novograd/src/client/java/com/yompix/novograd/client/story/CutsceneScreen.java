package com.yompix.novograd.client.story;

import com.yompix.novograd.Novograd;
import com.yompix.novograd.client.NovogradClient;
import com.yompix.novograd.client.screen.LifeScreen;
import com.yompix.novograd.story.CamPath;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Кат-сцена: камера летит между точками (как кран или дрон в кино) или ведёт героя, внизу субтитры под голос.
 * Работает на невидимом «операторе», игрок в это время стоит в режиме наблюдателя.
 *
 * <p><b>0.6.5 — «не догружается» и «камера в стене»:</b>
 * <ul>
 *   <li>Сцена начинается только когда клиент готов: участки вокруг первого кадра загружены и отрисованы, а актёры,
 *       которые есть на старте (список cast от сервера), уже пришли на клиент. До этого — чёрный кадр с кинополосами;
 *       сервер всё это время держит таймлайн на паузе (Director: hold), так что актёры не «убегают» вперёд.
 *       Не дождались за 8 с — сцена всё равно идёт (и всегда доигрывается до конца).</li>
 *   <li>Время сцены — по игровым тикам плюс доля тика в каждом кадре (как у сервера), пауза на загрузку — без
 *       рывков назад.</li>
 *   <li>Пролёт «А → Б» заранее проверяется по блокам (CamPath): если дуга задевает дом — другая сторона, прямая или
 *       подъём над крышами. Камера при герое: луч от героя к камере, упёрлись в стену — камера сразу перед стеной,
 *       а отходит обратно плавно. Повороты и погоня — критически демпфированными пружинами.
 *       Склейка (мгновенный переход) — только на смене плана.</li>
 *   <li>Проверка: -Dnovograd.camcheck (или запись видео) пишет позицию камеры в каждом кадре и считает кадры
 *       «внутри блока», «сквозь стену» и скачки.</li>
 * </ul>
 */
public class CutsceneScreen extends LifeScreen {
    private record Shot(double[] from, double[] to, float sec, String track, String follow, double[] off, float shake, float fov, float fov2, float roll) {}

    /** Актёры кат-сцены: имя в сценарии → id сущности на клиенте (сервер присылает «cut_actor»). */
    public static final java.util.Map<String, Integer> ACTORS = new java.util.HashMap<>();
    /** Открытая кат-сцена: камера ставится до отрисовки мира (StudioCameraMixin), а не после — без отставания и дрожи. */
    @org.jetbrains.annotations.Nullable public static CutsceneScreen current;

    private static final boolean CAMCHECK = Boolean.getBoolean("novograd.camcheck") || System.getProperty("novograd.rec") != null;

    /** Каждый кадр, до отрисовки мира. */
    public static void preFrame(float delta) {
        CutsceneScreen c = current;
        if (c != null && c.cam != null && c.mc().currentScreen == c) c.frame(delta);
    }

    private record Sub(String who, String name, int color, String text, String voice) {}

    private final List<Shot> shots = new ArrayList<>();
    private final List<Sub> subs = new ArrayList<>();
    private final List<String> cast = new ArrayList<>();
    private final float total;
    private ArmorStandEntity cam;
    private boolean hudWas, done;
    private int subIndex = -1;
    private double subAt;

    // ---- время сцены: base — на границе тика, в кадре + доля тика; на паузе (загрузка) стоит
    private double base, shown;
    private boolean running;
    // ---- готовность
    private boolean ready;
    private int prerollTicks;

    private double elapsed() {
        return shown;
    }

    public CutsceneScreen(NbtCompound n) {
        super("Кат-сцена");
        float t = 0;
        for (NbtElement e : n.getList("shots", NbtElement.COMPOUND_TYPE)) {
            NbtCompound s = (NbtCompound) e;
            double[] off = s.contains("off") ? arr(s.getList("off", NbtElement.DOUBLE_TYPE)) : new double[]{7, 3, 0, 0, 0};
            Shot sh = new Shot(s.contains("from") ? arr(s.getList("from", NbtElement.DOUBLE_TYPE)) : null,
                    s.contains("to") ? arr(s.getList("to", NbtElement.DOUBLE_TYPE)) : null, s.getFloat("sec"),
                    s.getString("track"), s.getString("follow"), off, s.getFloat("shake"),
                    s.contains("fov") ? s.getFloat("fov") : Float.NaN, s.contains("fov2") ? s.getFloat("fov2") : Float.NaN,
                    s.contains("roll") ? s.getFloat("roll") : Float.NaN);
            shots.add(sh);
            t += sh.sec();
        }
        for (NbtElement e : n.getList("lines", NbtElement.COMPOUND_TYPE)) {
            NbtCompound c = (NbtCompound) e;
            subs.add(new Sub(c.getString("who"), c.getString("name"), c.getInt("color"), c.getString("text"), c.getString("voice")));
        }
        for (NbtElement e : n.getList("cast", NbtElement.STRING_TYPE)) cast.add(e.asString());
        total = Math.max(t, 2);
        grade = n.contains("grade") ? n.getString("grade") : "film_cine";
        title = n.getString("title");
        chapter = n.getString("chapter");
        variant = new int[shots.size()];
        java.util.Arrays.fill(variant, -1);
        fromL = new double[shots.size()][];
        craneY = new double[shots.size()];
        toL = new double[shots.size()][];
    }

    /** Кинофильтр сцены, титр миссии (как в начале миссии GTA). */
    private final String grade, title, chapter;

    private static double[] arr(NbtList l) {
        double[] v = new double[5];
        for (int i = 0; i < Math.min(5, l.size()); i++) v[i] = l.getDouble(i);
        return v;
    }

    @Override
    protected void init() {
        if (cam == null && mc().world != null) {
            subIndex = -1;
            base = shown = 0;
            running = false;
            ready = false;
            prerollTicks = 0;
            dataReadyAt = -1; // окно открылось заново (после чужой загрузки) — готовность проверяем с нуля
            waitTicks = 0;
            waitingShot = -1;
            cam = new ArmorStandEntity(EntityType.ARMOR_STAND, mc().world);
            cam.setInvisible(true);
            hudWas = mc().options.hudHidden;
            mc().options.hudHidden = true;
            current = this;
            if (!shots.isEmpty() && shots.get(0).from() != null) {
                double[] f = shots.get(0).from();
                cam.setPosition(f[0], f[1] - cam.getStandingEyeHeight(), f[2]);
            }
            mc().setCameraEntity(cam);
            sendHold(true);
            if (CAMCHECK && CineRec.armed) openCamLog();
        }
    }

    @org.jetbrains.annotations.Nullable
    private net.minecraft.entity.Entity actor(String name) {
        Integer id = name.isEmpty() ? null : ACTORS.get(name);
        return id == null || mc().world == null ? null : mc().world.getEntityById(id);
    }

    /** Кто говорит текущую реплику: актёр сцены или герой рядом с тем же именем. */
    @org.jetbrains.annotations.Nullable
    private net.minecraft.entity.Entity speaker() {
        if (subIndex < 0 || subIndex >= subs.size() || mc().world == null || cam == null) return null;
        Sub sub = subs.get(subIndex);
        var e = actor(sub.who());
        if (e != null) return e;
        if (sub.name().isEmpty()) return null;
        // поиск по имени — раз на реплику, а не каждый кадр
        if (speakerCacheIdx == subIndex && (speakerCache == null || !speakerCache.isRemoved())) return speakerCache;
        speakerCacheIdx = subIndex;
        speakerCache = null;
        for (var en : mc().world.getEntitiesByClass(net.minecraft.entity.LivingEntity.class, cam.getBoundingBox().expand(48),
                x -> x.hasCustomName() && x.getCustomName().getString().startsWith(sub.name()))) {
            speakerCache = en;
            break;
        }
        return speakerCache;
    }

    private int speakerCacheIdx = -2;
    private net.minecraft.entity.Entity speakerCache;

    // ======================================================================= камера
    /** Пути пролётов: вариант (CamPath) и концы, поднятые из стен; считаются, когда участок загружен. */
    private final int[] variant;
    private final double[][] fromL, toL;
    private final double[] craneY;
    // состояние «оператора»
    private int lastShot = -1;
    private double camX = Double.NaN, camY, camZ, velX, velY, velZ;
    private float camYaw, camPitch, yawV, pitchV;
    private double arm = -1, lastFrameT = -1, clock;
    /** Тики, пока открыт экран (часы пружин камеры; в отличие от времени сцены не стоят на паузе загрузки). */
    private int screenTicks;
    private Vec3d lastFinal, lastFocus, lastPos;
    private boolean closeShot, closeDecided;
    private double gvx, gvy, gvz;
    private int lastGuard;

    private void planShot(int si) {
        if (si < 0 || si >= shots.size() || variant[si] >= 0 || mc().world == null) return;
        Shot s = shots.get(si);
        if (s.from() == null) {
            variant[si] = 0;
            return;
        }
        fromL[si] = CamPath.lift(mc().world, s.from());
        toL[si] = CamPath.lift(mc().world, s.to() != null ? s.to() : s.from());
        int[] p = CamPath.plan(mc().world, fromL[si], toL[si], si);
        variant[si] = p[0];
        craneY[si] = p[2];
        if (p[0] != 0 || p[1] > 0) Novograd.LOG.info("[cam] план {}: вариант пути {}, задето {}", si, p[0], p[1]);
    }

    private int shotIndex(double time) {
        double t = time;
        for (int i = 0; i < shots.size(); i++) {
            if (t <= shots.get(i).sec()) return i;
            t -= shots.get(i).sec();
        }
        return shots.size() - 1;
    }

    private double shotStart(int si) {
        double t = 0;
        for (int i = 0; i < si; i++) t += shots.get(i).sec();
        return t;
    }

    /** Каждый кадр: время сцены и камера. */
    private void frame(float delta) {
        if (running) shown = Math.max(shown, Math.min(total + 0.5, base + delta * 0.05));
        clock = Math.max(clock, (screenTicks + delta) / 20.0);
        place(Math.min(shown, total), delta);
    }

    /** Критически демпфированная пружина (неявный Эйлер — устойчива при любом шаге кадра). Возвращает {x, v}. */
    private static double[] spring(double x, double v, double target, double omega, double dt) {
        double f = 1 + 2 * dt * omega, oo = omega * omega, hoo = dt * oo, hhoo = dt * hoo, inv = 1 / (f + hhoo);
        double nx = (f * x + dt * v + hhoo * target) * inv, nv = (v + hoo * (target - x)) * inv;
        return new double[]{nx, nv};
    }

    private void place(double time, float delta) {
        if (shots.isEmpty() || mc().world == null) return;
        int si = shotIndex(time);
        Shot s = shots.get(si);
        // путь выбирается по блокам — только когда сцена готова (участки пришли); до этого кадр ещё не показан,
        // а план по пустому миру выбрал бы «чистый» путь сквозь дома, которые пришли позже
        if (ready) planShot(si);
        double local = time - shotStart(si);
        double k = CamPath.ease(local / Math.max(0.05, s.sec()));
        var fol = actor(s.follow());
        var trk = actor(s.track());
        // как в кино: на крупном плане камера «переводит взгляд» на того, кто сейчас говорит
        var sp = speaker();
        if (trk != null && sp != null && sp != trk && sp.squaredDistanceTo(trk) < 20 * 20) trk = sp;
        boolean cut = Double.isNaN(camX) || si != lastShot;
        // шаг пружин — по часам экрана (идут и на паузе загрузки): время сцены на паузе стоит, а герой, за которым едет
        // камера, может ещё двигаться — по времени сцены пружины замирали и потом камера прыгала к нему (до 27 блоков)
        double dt = cut || lastFrameT < 0 ? 0 : MathHelper.clamp(clock - lastFrameT, 0, 0.1);
        lastFrameT = clock;
        double x, y, z;
        float yaw, pitch;
        Vec3d focusEye = null;
        if (fol != null) {
            // камера «за плечом» / за машиной: держится сзади и сбоку и мягко догоняет (пружина, не рывками)
            Vec3d at = fol.getLerpedPos(delta);
            float fy = fol instanceof net.minecraft.entity.LivingEntity le ? MathHelper.lerpAngleDegrees(delta, le.prevBodyYaw, le.bodyYaw) : fol.getYaw(delta);
            double rad = Math.toRadians(fy), back = s.off()[0], up = s.off()[1], side = s.off()[2];
            double fx = -Math.sin(rad), fz = Math.cos(rad);
            double tx = at.x - fx * back + fz * side, ty = at.y + up, tz = at.z - fz * back - fx * side;
            if (cut) {
                x = tx;
                y = ty;
                z = tz;
                velX = velY = velZ = 0;
            } else {
                double[] a = spring(camX, velX, tx, 7, dt), b = spring(camY, velY, ty, 7, dt), c = spring(camZ, velZ, tz, 7, dt);
                x = a[0];
                velX = a[1];
                y = b[0];
                velY = b[1];
                z = c[0];
                velZ = c[1];
            }
            focusEye = at.add(0, fol.getHeight() * 0.8, 0);
            yaw = yawTo(x, z, focusEye);
            pitch = pitchTo(x, y, z, focusEye);
        } else if (s.from() != null) {
            double[] to0 = s.to() != null ? s.to() : s.from();
            double[] f = fromL[si] != null ? fromL[si] : s.from(), to = toL[si] != null ? toL[si] : to0;
            Vec3d p = CamPath.at(f, to, k, si, Math.max(0, variant[si]), craneY[si]);
            x = p.x;
            y = p.y;
            z = p.z;
            yaw = (float) (s.from()[3] + MathHelper.wrapDegrees(to0[3] - s.from()[3]) * k);
            pitch = (float) MathHelper.lerp(k, s.from()[4], to0[4]);
        } else return;
        if (trk != null) {
            focusEye = trk.getLerpedPos(delta).add(0, trk.getHeight() * 0.75, 0);
            lastFocus = focusEye;
        } else if (!cut && lastFocus != null && !s.track().isEmpty()) {
            focusEye = lastFocus; // герой плана ушёл из зоны видимости клиента — смотрим туда, где он был, без рывка
        } else if (cut) lastFocus = null;
        if (focusEye != null && fol == null) {
            yaw = yawTo(x, z, focusEye);
            pitch = pitchTo(x, y, z, focusEye);
        }
        // ---- не сквозь стены: луч от героя к камере; упёрлись — камера сразу перед стеной, отходит плавно
        Vec3d want = new Vec3d(x, y, z);
        int guard = 0;
        // упираемся в стену только на планах «за героем» и крупных (герой ближе 14 блоков); общий план с дальним
        // героем идёт по своему проверенному пути (CamPath), иначе камеру рвало бы к герою через полгорода
        // решение «крупный план или общий» принимается один раз на план (на склейке), иначе камера прыгала бы,
        // когда герой пересекает границу 14 блоков
        if (cut) closeDecided = false;
        if (!closeDecided && (fol != null || focusEye != null)) {
            closeShot = fol != null || focusEye.squaredDistanceTo(want) < 14 * 14;
            closeDecided = true;
        }
        boolean close = focusEye != null && closeDecided && closeShot;
        if (close) {
            Vec3d dir = want.subtract(focusEye);
            double full = dir.length();
            if (full > 0.05) {
                Vec3d hit = CamPath.hit(mc().world, focusEye, want);
                double allowed = hit == null ? full : Math.max(0.35, hit.distanceTo(focusEye) - 0.45);
                if (cut || arm < 0) arm = allowed;
                else if (allowed < arm) arm += (allowed - arm) * (1 - Math.exp(-dt * 18)); // ближе — быстро, но за 3–4 кадра, а не прыжком
                else arm += (allowed - arm) * (1 - Math.exp(-dt * 3.5));     // дальше — мягко
                double d = Math.min(arm, full);
                if (d < full - 1e-3) {
                    want = focusEye.add(dir.multiply(d / full));
                    guard = 1;
                }
            }
        } else arm = -1;
        // последняя страховка: точка камеры задевает блок (тонкие заборы, края крыш) — ближайшая свободная точка рядом,
        // а из кандидатов — та, что ближе к прошлому кадру (без скачков); ничего нет — камера стоит, где была
        if (!CamPath.clear(mc().world, want)) {
            Vec3d anchor = !cut && lastPos != null ? lastPos : want, best = null;
            double bestD = Double.MAX_VALUE;
            Vec3d toFocus = focusEye == null ? null : focusEye.subtract(want).normalize();
            for (double r = 0.25; r <= 4.0 && best == null; r += 0.25) {
                Vec3d[] dirs = {new Vec3d(0, 1, 0), new Vec3d(0, -1, 0), new Vec3d(1, 0, 0), new Vec3d(-1, 0, 0), new Vec3d(0, 0, 1), new Vec3d(0, 0, -1), toFocus};
                for (Vec3d d : dirs) {
                    if (d == null) continue;
                    Vec3d q = want.add(d.multiply(r));
                    if (!CamPath.clear(mc().world, q)) continue;
                    if (!cut && lastPos != null && !CamPath.free(mc().world, lastPos, q)) continue; // не перескакивать сквозь стенку
                    double dd = q.squaredDistanceTo(anchor);
                    if (dd < bestD) {
                        bestD = dd;
                        best = q;
                    }
                }
            }
            if (best != null) {
                want = best;
                guard = 2;
            } else if (!cut && lastPos != null && CamPath.clear(mc().world, lastPos)) {
                want = lastPos; // свободной точки рядом нет (путь ушёл в дом) — камера стоит, где была, а не в стене
                guard = 2;
            }
        }
        // у стены (поправки на этом и прошлом кадре) — сглаживаем пружиной, чтобы поиск свободной точки не дрожал;
        // сглаженная точка берётся, только если она сама свободна и путь к ней от прошлого кадра не сквозь стену
        if (!cut && lastPos != null && (guard > 0 || lastGuard > 0)) {
            double[] a = spring(lastPos.x, gvx, want.x, 14, dt), b = spring(lastPos.y, gvy, want.y, 14, dt), c = spring(lastPos.z, gvz, want.z, 14, dt);
            Vec3d sm = new Vec3d(a[0], b[0], c[0]);
            if (CamPath.clear(mc().world, sm) && CamPath.free(mc().world, lastPos, sm)) {
                want = sm;
                gvx = a[1];
                gvy = b[1];
                gvz = c[1];
            } else gvx = gvy = gvz = 0;
        } else gvx = gvy = gvz = 0;
        lastGuard = guard;
        if (focusEye != null && guard > 0) {
            yaw = yawTo(want.x, want.z, focusEye);
            pitch = pitchTo(want.x, want.y, want.z, focusEye);
        }
        x = want.x;
        y = want.y;
        z = want.z;
        // ---- поворот: на склейке — сразу; внутри плана с героем — пружиной (смена говорящего, обход стены — без рывка)
        if (cut) {
            camYaw = yaw;
            camPitch = pitch;
            yawV = pitchV = 0;
        } else {
            double target = camYaw + MathHelper.wrapDegrees(yaw - camYaw);
            double[] a = spring(camYaw, yawV, target, 9, dt), b = spring(camPitch, pitchV, pitch, 9, dt);
            camYaw = (float) a[0];
            yawV = (float) a[1];
            camPitch = (float) b[0];
            pitchV = (float) b[1];
        }
        camX = x;
        camY = y;
        camZ = z;
        lastPos = new Vec3d(x, y, z);
        lastShot = si;
        float shake = s.shake();
        cine(s, (float) k, fol != null, trk != null, shake, time);
        double sx = 0, sy = 0;
        if (shake > 0) {
            sx = (Math.sin(time * 23) + Math.sin(time * 37) * 0.5) * shake * 0.6;
            sy = (Math.cos(time * 29) + Math.sin(time * 41) * 0.5) * shake * 0.4;
        }
        cam.prevX = cam.lastRenderX = camX;
        cam.prevY = cam.lastRenderY = camY - cam.getStandingEyeHeight();
        cam.prevZ = cam.lastRenderZ = camZ;
        cam.setPosition(camX, camY - cam.getStandingEyeHeight(), camZ);
        cam.setYaw(camYaw + (float) sx);
        cam.setPitch(camPitch + (float) sy);
        cam.prevYaw = cam.getYaw();
        cam.prevPitch = cam.getPitch();
        cam.setHeadYaw(cam.getYaw());
        cam.prevHeadYaw = cam.getYaw();
        logFrame(time, si, cut, guard);
    }

    // ======================================================================= проверка камеры по кадрам
    private PrintWriter camLog;
    private static int sceneNo;
    private int frames, inside, crossed, jumps, guards;
    private double maxStep, maxTurn;
    private float lastYawLog;
    private double lastStep, lastTurn;

    private void openCamLog() {
        try {
            String b = System.getProperty("novograd.rec", System.getProperty("java.io.tmpdir") + "/novograd_cam.mp4");
            if (sceneNo++ > 0) b = b.replace(".mp4", "_" + sceneNo + ".mp4");
            camLog = new PrintWriter(new FileWriter(b.replace(".mp4", ".cam.tsv")), true);
            camLog.println("t\tshot\tcut\tx\ty\tz\tyaw\tpitch\tinside\tcrossed\tstep\tguard");
        } catch (Exception e) {
            camLog = null;
        }
    }

    private void logFrame(double t, int si, boolean cut, int guard) {
        if (!CAMCHECK || camLog == null || !ready || CineRec.on && !CineRec.capturing()) return;
        Vec3d now = new Vec3d(camX, camY, camZ);
        boolean in = !CamPath.clear(mc().world, now, 0.1);
        boolean cross = !cut && lastFinal != null && !CamPath.free(mc().world, lastFinal, now);
        double step = cut || lastFinal == null ? 0 : now.distanceTo(lastFinal);
        float turn = cut || lastFinal == null ? 0 : Math.abs(MathHelper.wrapDegrees(camYaw - lastYawLog)); // первый записанный кадр — как склейка
        frames++;
        if (in) inside++;
        if (cross) crossed++;
        if (guard > 0) guards++;
        // скачок: больше 3 блоков за кадр (≈90 блоков/с при 30 к/с) или разворот больше 25° за кадр — и это не склейка
        if (step > 3 && step > 2.5 * Math.max(0.3, lastStep) || turn > 25 && turn > 3 * Math.max(2, lastTurn)) jumps++;
        lastStep = step;
        lastTurn = turn;
        maxStep = Math.max(maxStep, step);
        maxTurn = Math.max(maxTurn, turn);
        lastFinal = now;
        lastYawLog = camYaw;
        if (camLog != null)
            camLog.println(String.format(Locale.ROOT, "%.3f\t%d\t%d\t%.3f\t%.3f\t%.3f\t%.2f\t%.2f\t%d\t%d\t%.3f\t%d", t, si, cut ? 1 : 0, camX, camY, camZ, camYaw, camPitch, in ? 1 : 0, cross ? 1 : 0, step, guard));
    }

    private void closeCamLog() {
        if (!CAMCHECK || camLog == null) return;
        Novograd.LOG.info(String.format(Locale.ROOT, "[cam] кадров=%d внутри_блока=%d сквозь_стену=%d скачков=%d макс_шаг=%.2f макс_поворот=%.1f°/кадр поправок_у_стены=%d",
                frames, inside, crossed, jumps, maxStep, maxTurn, guards));
        if (camLog != null) camLog.close();
        camLog = null;
    }

    /** Операторская работа: наезд зумом, голландский угол, «ручная» камера в погонях. */
    private void cine(Shot s, float k, boolean follow, boolean track, float shake, double tt) {
        float idx = shots.indexOf(s);
        float a, b, r;
        if (follow) { // погоня: широкий угол, камера раскачивается
            a = 80;
            b = 76;
            r = (float) (Math.sin(tt * 0.9) * 2.2 + Math.sin(tt * 2.3) * 0.6);
        } else if (track) { // крупный план героя: медленный наезд
            a = 66;
            b = 58;
            r = shake > 0 ? (idx % 2 == 0 ? 5 : -5) : 0;
        } else { // общий план: лёгкий наезд, ровный горизонт
            a = 72;
            b = 62;
            r = 0;
        }
        if (!Float.isNaN(s.fov())) a = s.fov();
        b = !Float.isNaN(s.fov2()) ? s.fov2() : !Float.isNaN(s.fov()) ? s.fov() : b;
        if (!Float.isNaN(s.roll())) r = s.roll();
        if (shake > 0) r += (float) (Math.sin(tt * 31) * shake * 1.5);
        com.yompix.novograd.client.studio.CineCam.cutscene = true;
        com.yompix.novograd.client.studio.CineCam.fov = MathHelper.lerp(k, a, b);
        com.yompix.novograd.client.studio.CineCam.roll = r;
    }

    private static float yawTo(double x, double z, Vec3d look) {
        return (float) Math.toDegrees(Math.atan2(-(look.x - x), look.z - z));
    }

    private static float pitchTo(double x, double y, double z, Vec3d look) {
        double h = Math.sqrt((look.x - x) * (look.x - x) + (look.z - z) * (look.z - z));
        return (float) -Math.toDegrees(Math.atan2(look.y - y, h));
    }

    // ======================================================================= загрузка и время
    private int waitingShot = -1, waitTicks, dataReadyAt = -1;
    private float black = 1;
    private boolean sentHold, holdSentOnce;
    private int sentShot = -2;

    /** Участки вокруг точки загружены. */
    private boolean chunksAround(double x, double z, int r) {
        var w = mc().world;
        int cx = MathHelper.floor(x) >> 4, cz = MathHelper.floor(z) >> 4;
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++)
                if (!w.getChunkManager().isChunkLoaded(cx + dx, cz + dz)) return false;
        return true;
    }

    /** Точка, где будет камера в начале плана si (для проверки загрузки). */
    @org.jetbrains.annotations.Nullable
    private Vec3d shotPoint(int si) {
        Shot s = shots.get(si);
        if (s.from() != null) return new Vec3d(s.from()[0], s.from()[1], s.from()[2]);
        var a = actor(!s.follow().isEmpty() ? s.follow() : s.track());
        return a != null ? a.getPos() : cam == null ? null : cam.getPos();
    }

    /** Местность и актёры плана si на клиенте и отрисованы? */
    private boolean shotReady(int si) {
        var w = mc().world;
        if (w == null || cam == null || shots.isEmpty()) return true;
        Shot s = shots.get(si);
        // актёр плана объявлен сервером, но ещё не пришёл на клиент — ждём (иначе камера смотрит «в никуда»)
        for (String n : new String[]{s.track(), s.follow()})
            if (!n.isEmpty() && ACTORS.containsKey(n) && actor(n) == null) return false;
        Vec3d p = shotPoint(si);
        if (p != null && !chunksAround(p.x, p.z, 2)) return false;
        // пролёт А → Б: участки у конца пути тоже нужны — по ним выбирается путь без домов (CamPath.plan)
        if (s.to() == null || s.from() == null) return true;
        for (double k = 0.25; k <= 1.0; k += 0.25) {
            double qx = MathHelper.lerp(k, s.from()[0], s.to()[0]), qz = MathHelper.lerp(k, s.from()[2], s.to()[2]);
            // дальше ~7 участков от начала сервер может их и не прислать (дальность прорисовки) — их не ждём
            if (Math.hypot(qx - s.from()[0], qz - s.from()[2]) > 112) break;
            if (!chunksAround(qx, qz, 1)) return false;
        }
        return true;
    }

    /** Участки вокруг точки уже отрисованы (а не весь мир — на слабой видеокарте он дорисовывается долго). */
    private boolean rendered(Vec3d p) {
        // проверяем секции на уровне земли: пустая секция воздуха (камера высоко) «готовой» не бывает никогда
        var w = mc().world;
        for (int dx = -16; dx <= 16; dx += 16)
            for (int dz = -16; dz <= 16; dz += 16) {
                int x = MathHelper.floor(p.x) + dx, z = MathHelper.floor(p.z) + dz;
                int y = w.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, x, z) - 1;
                if (y <= w.getBottomY()) continue;
                if (!mc().worldRenderer.isRenderingReady(new net.minecraft.util.math.BlockPos(x, y, z))) return false;
            }
        return true;
    }

    /** Для лога: чего не хватает плану. */
    private String whyNot(int si) {
        Shot s = shots.get(si);
        StringBuilder b = new StringBuilder();
        for (String n : new String[]{s.track(), s.follow()})
            if (!n.isEmpty() && ACTORS.containsKey(n) && actor(n) == null) b.append("нет актёра ").append(n).append("; ");
        Vec3d p = shotPoint(si);
        if (p != null && !chunksAround(p.x, p.z, 2)) b.append("участки не загружены; ");
        if (p != null && !rendered(p)) b.append("отрисовка не готова");
        return b.toString();
    }

    private String missingCast() {
        StringBuilder b = new StringBuilder();
        for (String n : cast) if (!ACTORS.containsKey(n) || actor(n) == null) b.append(n).append(' ');
        return b.toString();
    }

    /** Все актёры, что есть на старте сцены, пришли на клиент. */
    private boolean castReady() {
        for (String n : cast) if (!ACTORS.containsKey(n) || actor(n) == null) return false;
        return true;
    }

    private void sendHold(boolean on) {
        int shot = on ? (ready ? waitingShot : 0) : -1;
        if (on == sentHold && holdSentOnce && shot == sentShot) return;
        holdSentOnce = true;
        sentHold = on;
        sentShot = shot;
        NbtCompound n = new NbtCompound();
        n.putBoolean("on", on);
        n.putInt("shot", shot);
        NovogradClient.send("cut_hold", n);
    }

    @Override
    public void tick() {
        if (cam == null) return;
        screenTicks++;
        com.yompix.novograd.client.studio.CineCam.filter(mc(), grade);
        // ---- старт: ждём участки, отрисовку и актёров (до 8 с), сервер тем временем держит таймлайн
        if (!ready) {
            prerollTicks++;
            // участки и актёры — обязательно; отрисовку вокруг первого кадра ждём ещё до 3 с (на слабой видеокарте дольше не держим)
            boolean base0 = castReady() && shotReady(0);
            if (base0 && dataReadyAt < 0) dataReadyAt = prerollTicks;
            Vec3d p0 = shotPoint(0);
            boolean drawn = p0 == null || rendered(p0) || dataReadyAt >= 0 && prerollTicks - dataReadyAt > 60;
            boolean ok = prerollTicks > 6 && base0 && drawn;
            if (ok || prerollTicks > 160) {
                if (!ok) Novograd.LOG.warn("[cut] старт по таймауту: актёры {} ({}), план 0: {}", castReady(), missingCast(), whyNot(0));
                else Novograd.LOG.info("[cut] готово к старту за {} тиков", prerollTicks);
                String rec = System.getProperty("novograd.rec");
                if (rec != null && !CineRec.on && !CineRec.used && CineRec.armed) CineRec.start(rec, Integer.getInteger("novograd.recfps", 30));
                planShot(0);
                ready = true;
                running = true;
                base = shown = 0;
                waitingShot = 0;
                sendHold(false);
            } else {
                sendHold(true);
                return;
            }
        }
        // ---- новый план: ждём, пока местность и актёры кадра на клиенте (до 4 с), время сцены стоит
        // смотрим на 0.3 с вперёд: следующий план не готов (актёр не пришёл) — придержим конец текущего, а не склейку
        int si = shotIndex(Math.min(shown + 0.3, total));
        if (si != waitingShot && waitTicks == 0) {
            waitingShot = si;
            waitTicks = 1;
        }
        if (waitTicks > 0) {
            if (shotReady(si) || waitTicks > 80) {
                if (waitTicks > 80) Novograd.LOG.warn("[cut] план {}: не догрузился за 4 с ({}) — играем дальше", si, whyNot(si));
                waitTicks = 0;
                planShot(si);
            } else waitTicks++;
        }
        boolean holding = waitTicks > 2;
        if (holding && running) {
            running = false;
            base = shown;          // время замирает на том, что уже показано (без шага назад)
        } else if (!holding && !running) running = true;
        if (running) base += 0.05;
        sendHold(holding);
        black += ((waitTicks > 10 ? 1 : 0) - black) * 0.12f;
        bodyLanguage();
        // субтитры идут по очереди: следующая — когда голос закончился (длина — по файлу, если звук не играет)
        double time = shown;
        double need = subIndex < 0 ? 0 : Math.max(1.2, Voice.length(subs.get(subIndex).voice()) + 0.35);
        if (subIndex < 0 || (!Voice.playing() && time - subAt > need)) {
            if (subIndex + 1 < subs.size()) {
                if (running) {
                    subIndex++;
                    subAt = time;
                    Voice.play(subs.get(subIndex).voice());
                    NbtCompound ln = new NbtCompound();
                    ln.putString("who", subs.get(subIndex).who());
                    NovogradClient.send("cut_line", ln);
                }
            } else if (time >= total) {
                end();
            }
        }
        // страховка: сцена никогда не висит
        if (time > total + 180) end();
    }

    /**
     * Язык тела в диалоге (как в GTA): говорящий жестикулирует мокап-жестом — вариант по реплике, чтобы
     * каждая фраза шла своими руками; остальные актёры слушают (переминаются, кивают). Поворот к говорящему
     * делает сервер (Director.speak). Метка живёт полсекунды — после сцены жители сами вернутся к обычным позам.
     */
    private void bodyLanguage() {
        var w = mc().world;
        if (w == null || subIndex < 0 || subIndex >= subs.size()) return;
        var sp = speaker();
        Sub sub = subs.get(subIndex);
        int line = (sub.text().hashCode() ^ subIndex * 31) & 0x7fffffff;
        java.util.Set<net.minecraft.entity.Entity> cast = new java.util.HashSet<>();
        for (Integer id : ACTORS.values()) {
            var e = w.getEntityById(id);
            if (e != null) cast.add(e);
        }
        if (sp != null) cast.add(sp);
        for (var e : cast) {
            if (!(e instanceof com.yompix.novograd.entity.CitizenEntity c)) continue;
            c.cineUntil = c.age + 10;
            if (e == sp) {
                c.cineTalk = line % 4;
                c.cineListen = -1;
            } else {
                c.cineTalk = -1;
                c.cineListen = Math.floorMod(c.getUuid().hashCode(), 3);
            }
        }
    }

    private void end() {
        if (done) return;
        done = true;
        Voice.stop();
        closeCamLog();
        if (CineRec.on && System.getProperty("novograd.rec") != null) CineRec.stop();
        com.yompix.novograd.client.studio.CineCam.reset(mc());
        mc().options.hudHidden = hudWas;
        if (mc().player != null) mc().setCameraEntity(mc().player);
        ACTORS.clear();
        current = null;
        NovogradClient.send("cutscene_done");
        super.close();
    }

    @Override
    public void close() {
    }

    /** Если окно закрыла чужая загрузка — вернуть камеру игроку и показать кат-сцену позже. */
    @Override
    public void removed() {
        if (done || cam == null) return;
        com.yompix.novograd.client.studio.CineCam.reset(mc());
        mc().options.hudHidden = hudWas;
        if (mc().player != null) mc().setCameraEntity(mc().player);
        Voice.stop();
        cam = null;
        camX = Double.NaN;
        lastShot = -1;
        lastFrameT = -1;
        com.yompix.novograd.client.NovogradClient.requeue(this);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ESCAPE) {
            end();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    /** Титр миссии внизу справа: название крупно, акт — мелко, плавно появляется и уходит. */
    private void titleCard(DrawContext ctx, int bar) {
        if (title == null || title.isEmpty() || !ready) return;
        float t = (float) elapsed() - 1.4f;
        if (t < 0 || t > 5.5f) return;
        float a = Math.min(1, Math.min(t / 0.6f, (5.5f - t) / 0.8f));
        int al = (int) (a * 255) << 24;
        float slide = (1 - Math.min(1, t / 0.6f)) * 20;
        int x = width - 24 + (int) slide, y = height - bar - 46;
        var m = ctx.getMatrices();
        m.push();
        m.translate(x, y, 0);
        m.scale(2.4f, 2.4f, 1);
        String T = title.toUpperCase();
        int w = textRenderer.getWidth(T);
        ctx.drawText(textRenderer, T, -w + 1, 1, al, false);
        ctx.drawText(textRenderer, T, -w, 0, al | 0xFFC83D, false);
        m.pop();
        if (chapter != null && !chapter.isEmpty()) {
            int cw = textRenderer.getWidth(chapter);
            ctx.drawText(textRenderer, chapter, x - cw, y + 24, al | 0xE0E0E0, true);
        }
        ctx.fill(x - (int) (w * 2.4f * Math.min(1, t / 0.9f)), y - 5, x, y - 4, al | 0xD42A3A);
    }

    @Override
    protected void background(DrawContext ctx) {
    }

    @Override
    protected void draw(DrawContext ctx, float delta) {
        current = this;
        // кинополосы: пока сцена грузится — стоят полностью (чёрный кадр), в конце уезжают
        float e = (float) elapsed(), out = MathHelper.clamp((total - e) / 0.6f, 0, 1);
        float kb = ready ? out : 1;
        kb = kb * kb * (3 - 2 * kb);
        int bar = (int) (height * 0.12f * kb);
        ctx.fill(0, 0, width, bar, 0xFF000000);
        ctx.fill(0, height - bar, width, height, 0xFF000000);
        for (int i = 0; i < 10; i++) { // виньетка
            int a = (int) (Math.pow((10 - i) / 10.0, 2.2) * 60) << 24;
            int m = i * width / 50, wd = Math.max(1, width / 50);
            ctx.fill(m, bar, m + wd, height - bar, a);
            ctx.fill(width - m - wd, bar, width - m, height - bar, a);
        }
        titleCard(ctx, bar);
        // вход из чёрного — только когда сцена готова (раньше проявлялся недогруженный кадр)
        float fade = ready ? Math.min(1, e / 0.9f) : 0;
        float dark = Math.max(1 - fade, black);
        if (dark > 0.01f) ctx.fill(0, 0, width, height, (int) (Math.min(1, dark) * 255) << 24);
        if (!ready && prerollTicks > 20) {
            String s = "Загрузка сцены…";
            int a = (int) (MathHelper.clamp((prerollTicks - 20) / 10f, 0, 1) * 160) << 24;
            ctx.drawText(textRenderer, s, width / 2 - textRenderer.getWidth(s) / 2, height / 2 - 4, a | 0xD0D0D0, false);
        }

        if (subIndex >= 0 && subIndex < subs.size() && com.yompix.novograd.client.NovogradConfig.get().subtitles) {
            Sub s = subs.get(subIndex);
            var lines = textRenderer.wrapLines(Text.literal(s.text()), Math.min(width - 40, 380));
            int y = height - bar + (bar - lines.size() * 11) / 2;
            for (int i = 0; i < lines.size(); i++) {
                int w = textRenderer.getWidth(lines.get(i));
                ctx.drawText(textRenderer, lines.get(i), width / 2 - w / 2, y + i * 11, 0xFFF0F0F0, true);
            }
        }
        String skip = "Пробел — пропустить";
        ctx.drawText(textRenderer, skip, width - textRenderer.getWidth(skip) - 6, 6, 0x66FFFFFF, false);
    }
}
