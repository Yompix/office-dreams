package com.yompix.novograd.client.story;

import com.yompix.novograd.client.NovogradClient;
import com.yompix.novograd.client.screen.LifeScreen;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Кат-сцена: камера плавно летит между точками (как облёт в кино), внизу субтитры под голос рассказчика.
 * Работает на невидимом «операторе», игрок в это время стоит в режиме наблюдателя.
 */
public class CutsceneScreen extends LifeScreen {
    private record Shot(double[] from, double[] to, float sec, String track, String follow, double[] off, float shake, float fov, float fov2, float roll) {}

    /** Актёры кат-сцены: имя в сценарии → id сущности на клиенте (сервер присылает «cut_actor»). */
    public static final java.util.Map<String, Integer> ACTORS = new java.util.HashMap<>();
    private double camX = Double.NaN, camY, camZ;
    private float camYaw, camPitch;
    private long lastFrame;

    private record Sub(String who, String name, int color, String text, String voice) {}

    private final List<Shot> shots = new ArrayList<>();
    private final List<Sub> subs = new ArrayList<>();
    private final float total;
    private ArmorStandEntity cam;
    private boolean hudWas, done;
    private int subIndex = -1;
    private long subAt, startAt = System.currentTimeMillis();

    private float elapsed() {
        return (System.currentTimeMillis() - startAt) / 1000f;
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
        total = Math.max(t, 2);
        grade = n.contains("grade") ? n.getString("grade") : "film_cine";
        title = n.getString("title");
        chapter = n.getString("chapter");
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
            startAt = System.currentTimeMillis();
            cam = new ArmorStandEntity(EntityType.ARMOR_STAND, mc().world);
            cam.setInvisible(true);
            hudWas = mc().options.hudHidden;
            mc().options.hudHidden = true;
            place(0, 0);
            mc().setCameraEntity(cam);
        }
    }

    @org.jetbrains.annotations.Nullable
    private net.minecraft.entity.Entity actor(String name) {
        Integer id = name.isEmpty() ? null : ACTORS.get(name);
        return id == null || mc().world == null ? null : mc().world.getEntityById(id);
    }

    private void place(float time, float delta) {
        float t = time;
        for (Shot s : shots) {
            if (t <= s.sec() || s == shots.get(shots.size() - 1)) {
                float k = MathHelper.clamp(t / s.sec(), 0, 1);
                k = k * k * (3 - 2 * k); // плавный старт и остановка
                double[] f = s.from(), to = s.to() != null ? s.to() : s.from();
                var fol = actor(s.follow());
                var trk = actor(s.track());
                double x, y, z;
                float yaw, pitch;
                if (fol != null) {
                    // камера «за плечом» / за машиной: держится сзади и сбоку, мягко догоняет
                    Vec3d at = fol.getLerpedPos(delta);
                    float fy = fol instanceof net.minecraft.entity.LivingEntity le ? MathHelper.lerpAngleDegrees(delta, le.prevBodyYaw, le.bodyYaw) : fol.getYaw(delta);
                    double rad = Math.toRadians(fy), back = s.off()[0], up = s.off()[1], side = s.off()[2];
                    double fx = -Math.sin(rad), fz = Math.cos(rad); // вперёд по взгляду
                    x = at.x - fx * back + fz * side;
                    y = at.y + up;
                    z = at.z - fz * back - fx * side;
                    Vec3d look = at.add(0, fol.getHeight() * 0.8, 0);
                    yaw = yawTo(x, z, look);
                    pitch = pitchTo(x, y, z, look);
                } else if (f != null) {
                    x = MathHelper.lerp(k, f[0], to[0]);
                    y = MathHelper.lerp(k, f[1], to[1]);
                    z = MathHelper.lerp(k, f[2], to[2]);
                    // камера идёт не по линейке, а дугой — как кран/дрон у оператора
                    double mx = to[0] - f[0], mz = to[2] - f[2], dist = Math.sqrt(mx * mx + mz * mz);
                    if (dist > 2) {
                        double arc = Math.min(6, dist * 0.09) * Math.sin(Math.PI * k) * (shots.indexOf(s) % 2 == 0 ? 1 : -1);
                        x += -mz / dist * arc;
                        z += mx / dist * arc;
                        y += Math.min(3, dist * 0.03) * Math.sin(Math.PI * k);
                    }
                    yaw = (float) (f[3] + MathHelper.wrapDegrees(to[3] - f[3]) * k);
                    pitch = (float) MathHelper.lerp(k, f[4], to[4]);
                } else return;
                if (trk != null) {
                    Vec3d look = trk.getLerpedPos(delta).add(0, trk.getHeight() * 0.75, 0);
                    yaw = yawTo(x, z, look);
                    pitch = pitchTo(x, y, z, look);
                }
                // сглаживание: камера-оператор не дёргается, а «ведёт» героя
                long now = System.nanoTime();
                float dt = lastFrame == 0 ? 1 : Math.min(1, (now - lastFrame) / 1e9f * (fol != null ? 6f : trk != null ? 10f : 60f));
                lastFrame = now;
                boolean cut = Double.isNaN(camX) || Math.abs(camX - x) + Math.abs(camZ - z) > 30;
                if (cut || (fol == null && trk == null)) {
                    camX = x; camY = y; camZ = z; camYaw = yaw; camPitch = pitch;
                } else {
                    camX += (x - camX) * dt;
                    camY += (y - camY) * dt;
                    camZ += (z - camZ) * dt;
                    camYaw += MathHelper.wrapDegrees(yaw - camYaw) * Math.min(1, dt * 1.5f);
                    camPitch += (pitch - camPitch) * Math.min(1, dt * 1.5f);
                }
                float shake = s.shake();
                cine(s, k, fol != null, trk != null, shake);
                double sx = 0, sy = 0;
                if (shake > 0) {
                    double tt = System.currentTimeMillis() / 1000.0;
                    sx = (Math.sin(tt * 23) + Math.sin(tt * 37) * 0.5) * shake * 0.6;
                    sy = (Math.cos(tt * 29) + Math.sin(tt * 41) * 0.5) * shake * 0.4;
                }
                cam.prevX = cam.getX();
                cam.prevY = cam.getY();
                cam.prevZ = cam.getZ();
                cam.lastRenderX = cam.getX();
                cam.lastRenderY = cam.getY();
                cam.lastRenderZ = cam.getZ();
                cam.setPosition(camX, camY - cam.getStandingEyeHeight(), camZ);
                cam.setYaw(camYaw + (float) sx);
                cam.setPitch(camPitch + (float) sy);
                cam.prevYaw = cam.getYaw();
                cam.prevPitch = cam.getPitch();
                cam.setHeadYaw(cam.getYaw());
                cam.prevHeadYaw = cam.getYaw();
                return;
            }
            t -= s.sec();
        }
    }

    /** Операторская работа: наезд зумом, голландский угол, «ручная» камера в погонях. */
    private void cine(Shot s, float k, boolean follow, boolean track, float shake) {
        float idx = shots.indexOf(s);
        float a, b, r;
        double tt = System.currentTimeMillis() / 1000.0;
        if (follow) { // погоня: широкий угол, камера раскачивается
            a = 80; b = 76;
            r = (float) (Math.sin(tt * 0.9) * 2.2 + Math.sin(tt * 2.3) * 0.6);
        } else if (track) { // крупный план героя: медленный наезд
            a = 66; b = 58;
            r = shake > 0 ? (idx % 2 == 0 ? 5 : -5) : 0;
        } else { // общий план: лёгкий наезд, ровный горизонт
            a = 72; b = 62;
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

    /** Какой кадр сейчас по времени. */
    private int shotIndex(float time) {
        float t = time;
        for (int i = 0; i < shots.size(); i++) {
            if (t <= shots.get(i).sec()) return i;
            t -= shots.get(i).sec();
        }
        return shots.size() - 1;
    }

    private int waitingShot = -1, waitTicks;
    private float black = 1;
    private boolean sentHold;

    /** Местность вокруг камеры прогрузилась и отрисована? */
    private boolean areaReady() {
        var w = mc().world;
        if (w == null || cam == null) return true;
        int cx = (int) Math.floor(cam.getX()) >> 4, cz = (int) Math.floor(cam.getZ()) >> 4;
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++)
                if (!w.getChunkManager().isChunkLoaded(cx + dx, cz + dz)) return false;
        return mc().worldRenderer.isTerrainRenderComplete();
    }

    @Override
    public void tick() {
        if (cam == null) return;
        // новый кадр: ждём, пока местность вокруг камеры прогрузится (до 4 с), время сцены стоит, экран тёмный
        int si = shotIndex(Math.min(elapsed(), total));
        if (si != waitingShot && waitTicks == 0) {
            waitingShot = si;
            waitTicks = 1;
        }
        if (waitTicks > 0) {
            if (areaReady() && waitTicks > 3 || waitTicks > 80) waitTicks = 0;
            else {
                waitTicks++;
                startAt += 50; // остановили время сцены
                if (subIndex >= 0) subAt += 50;
            }
        }
        boolean holding = waitTicks > 0;
        if (holding != sentHold) {
            sentHold = holding;
            net.minecraft.nbt.NbtCompound n = new net.minecraft.nbt.NbtCompound();
            n.putBoolean("on", holding);
            NovogradClient.send("cut_hold", n);
        }
        black += ((waitTicks > 10 ? 1 : 0) - black) * 0.12f;
        float time = elapsed();
        com.yompix.novograd.client.studio.CineCam.filter(mc(), grade);
        place(Math.min(time, total), 0);
        // субтитры идут по очереди: следующая — когда голос закончился
        if (subIndex < 0 || (!Voice.playing() && System.currentTimeMillis() - subAt > 1200)) {
            if (subIndex + 1 < subs.size()) {
                subIndex++;
                subAt = System.currentTimeMillis();
                Voice.play(subs.get(subIndex).voice());
                net.minecraft.nbt.NbtCompound ln = new net.minecraft.nbt.NbtCompound();
                ln.putString("who", subs.get(subIndex).who());
                NovogradClient.send("cut_line", ln);
            } else if (time >= total) {
                end();
            }
        }
    }

    private void end() {
        if (done) return;
        done = true;
        Voice.stop();
        com.yompix.novograd.client.studio.CineCam.reset(mc());
        mc().options.hudHidden = hudWas;
        if (mc().player != null) mc().setCameraEntity(mc().player);
        ACTORS.clear();
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
        if (title == null || title.isEmpty()) return;
        float t = elapsed() - 1.4f;
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
        ctx.fill(x - (int) (w * 2.4f * Math.min(1, t / 0.9f)), y - 5, x, y - 4, al | 0xFF4FD8);
    }

    @Override
    protected void background(DrawContext ctx) {
    }

    @Override
    protected void draw(DrawContext ctx, float delta) {
        if (cam != null) place(Math.min(elapsed(), total), delta);
        int bar = (int) (height * 0.12f);
        ctx.fill(0, 0, width, bar, 0xFF000000);
        ctx.fill(0, height - bar, width, height, 0xFF000000);
        for (int i = 0; i < 10; i++) { // виньетка
            int a = (int) (Math.pow((10 - i) / 10.0, 2.2) * 60) << 24;
            int m = i * width / 50, wd = Math.max(1, width / 50);
            ctx.fill(m, bar, m + wd, height - bar, a);
            ctx.fill(width - m - wd, bar, width - m, height - bar, a);
        }
        titleCard(ctx, bar);
        float fade = Math.min(1, elapsed() / 1.2f);
        float dark = Math.max(1 - fade, black);
        if (dark > 0.01f) ctx.fill(0, 0, width, height, (int) (Math.min(1, dark) * 255) << 24);

        if (subIndex >= 0 && subIndex < subs.size()) {
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
