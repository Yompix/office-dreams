package com.yompix.novograd.client;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.gta.Pulse;
import com.yompix.novograd.vehicle.MotorVehicle;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

/**
 * Клиент «Пульса города»: коллекции (точки рядом, радар, трамплины), уличные экраны «Новоград-24»,
 * эфир с вертолёта, ночные прожекторы и сирены, подпольные бои. Свой сетевой канал Pulse.S2C/C2S.
 */
public final class PulseClient {
    private PulseClient() {}

    public static NbtCompound colState = new NbtCompound();
    public static NbtList near = new NbtList(), radar = new NbtList();
    public static NbtList tvScreens = new NbtList();
    public static NbtCompound tvFeed = new NbtCompound();
    public static NbtCompound live = new NbtCompound();
    public static NbtCompound fight = new NbtCompound(), fightHud = new NbtCompound();
    public static boolean chaos, chaosUnlocked;
    private static NbtList places = new NbtList();
    private static long launchCd;

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(Pulse.S2C.ID, (payload, ctx) -> ctx.client().execute(() -> receive(ctx.client(), payload.type(), payload.data())));
        ClientTickEvents.END_CLIENT_TICK.register(PulseClient::tick);
        HudRenderCallback.EVENT.register((ctx, t) -> {
            LiveHud.render(ctx);
            fightHud(ctx);
            hints(ctx);
        });
        WorldRenderEvents.AFTER_TRANSLUCENT.register(PulseWorld::render);
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> {
            near = new NbtList();
            radar = new NbtList();
            tvScreens = new NbtList();
            live = new NbtCompound();
            fightHud = new NbtCompound();
        });
    }

    public static void send(String type, NbtCompound n) {
        if (ClientPlayNetworking.canSend(Pulse.C2S.ID)) ClientPlayNetworking.send(new Pulse.C2S(type, n));
    }

    public static void send(String type) {
        send(type, new NbtCompound());
    }

    private static void receive(MinecraftClient c, String type, NbtCompound n) {
        switch (type) {
            case "col_state" -> {
                colState = n;
                chaos = n.getBoolean("chaos");
                chaosUnlocked = n.getBoolean("chaosUnlocked");
                if (n.getBoolean("open") && c.currentScreen == null) c.setScreen(new com.yompix.novograd.client.screen.CollectionsScreen());
            }
            case "col_near" -> {
                near = n.getList("near", NbtElement.COMPOUND_TYPE);
                radar = n.getList("radar", NbtElement.COMPOUND_TYPE);
            }
            case "places" -> places = n.getList("list", NbtElement.COMPOUND_TYPE);
            case "chaos" -> {
                chaos = n.getBoolean("on");
                chaosUnlocked = n.getBoolean("unlocked");
            }
            case "tv_list" -> tvScreens = n.getList("list", NbtElement.COMPOUND_TYPE);
            case "tv" -> tvFeed = n;
            case "live" -> live = n;
            case "fight" -> {
                fight = n;
                if (n.getBoolean("open") && (c.currentScreen == null || c.currentScreen instanceof com.yompix.novograd.client.screen.FightScreen))
                    c.setScreen(new com.yompix.novograd.client.screen.FightScreen());
            }
            case "fight_hud" -> fightHud = n;
            default -> {}
        }
    }

    // ------------------------------------------------------------------ тик
    private static void tick(MinecraftClient c) {
        if (c.player == null || c.world == null) return;
        if (c.player.age % 20 == 0) injectPlaces();
        launch(c);
        NightFx.tick(c);
        PulseWorld.tick(c);
        LiveHud.tick(c);
    }

    /** Ларьки «Карты» и вход в «Подвал» — значками на радаре и карте. */
    private static void injectPlaces() {
        NbtCompound pls = com.yompix.novograd.client.story.ClientStory.places;
        for (int i = 0; i < places.size(); i++) {
            NbtCompound e = places.getCompound(i);
            if (!pls.contains(e.getString("id"))) {
                NbtCompound p = new NbtCompound();
                p.putInt("x", e.getInt("x"));
                p.putInt("z", e.getInt("z"));
                pls.put(e.getString("id"), p);
            }
        }
        if (!pls.contains("pulse_fight")) {
            NbtCompound p = new NbtCompound();
            p.putInt("x", com.yompix.novograd.gta.FightClub.EX);
            p.putInt("z", com.yompix.novograd.gta.FightClub.EZ);
            pls.put("pulse_fight", p);
        }
    }

    private static final int[][] DIRS = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};

    /** Трамплин: машина сходит с края на скорости — подбрасываем (физика машины считается у водителя). */
    private static void launch(MinecraftClient c) {
        if (!(c.player.getVehicle() instanceof CarEntity car) || car instanceof MotorVehicle || car.getControllingPassenger() != c.player) return;
        if (car.isOnGround() || c.world.getTime() < launchCd) return;
        Vec3d v = car.getVelocity();
        for (int i = 0; i < near.size(); i++) {
            NbtCompound e = near.getCompound(i);
            if (!e.getString("k").equals("jump")) continue;
            int[] d = DIRS[e.getInt("dir")];
            double ox = e.getInt("x") + 0.5, oz = e.getInt("z") + 0.5;
            double rx = car.getX() - ox, rz = car.getZ() - oz;
            double t = rx * d[0] + rz * d[1], lat = Math.abs(rx * -d[1] + rz * d[0]);
            double along = v.x * d[0] + v.z * d[1];
            if (t < 5.2 || t > 9.5 || lat > 2.4 || car.getY() < e.getInt("y") + 2.0 || along < 0.3) continue;
            car.setVelocity(v.x, 0.3 + along * 0.6, v.z);
            launchCd = c.world.getTime() + 40;
            if (System.getProperty("novograd.autotest") != null) com.yompix.novograd.Novograd.LOG.info("[pulse] трамплин: взлёт, скорость {}", along);
            c.world.playSound(c.player, car.getBlockPos(), SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, SoundCategory.NEUTRAL, 0.6f, 0.6f);
            return;
        }
    }

    // ------------------------------------------------------------------ радар (зовёт Minimap в координатах мира)
    public static void radar(DrawContext ctx, double px, double pz, float zoom) {
        for (int i = 0; i < radar.size(); i++) {
            NbtCompound e = radar.getCompound(i);
            double x = e.getInt("x") + 0.5, z = e.getInt("z") + 0.5;
            if (Math.abs(x - px) > 110 / zoom || Math.abs(z - pz) > 110 / zoom) continue;
            int col = switch (e.getString("k")) {
                case "tag" -> 0xFFC2405A;
                case "pkg" -> 0xFF6BFF6B;
                case "photo" -> 0xFF4FB8C0;
                default -> 0xFFFFA726;
            };
            float s = 2.0f / zoom;
            fill(ctx, x - s * 1.3, z - s * 1.3, x + s * 1.3, z + s * 1.3, 0xFF000000);
            fill(ctx, x - s, z - s, x + s, z + s, col);
        }
    }

    private static void fill(DrawContext ctx, double x1, double y1, double x2, double y2, int col) {
        var m = ctx.getMatrices();
        m.push();
        m.translate(x1, y1, 0);
        m.scale((float) (x2 - x1), (float) (y2 - y1), 1);
        ctx.fill(0, 0, 1, 1, col);
        m.pop();
    }

    // ------------------------------------------------------------------ подсказки рядом с точками
    private static void hints(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden || mc.currentScreen != null) return;
        String hint = null;
        for (int i = 0; i < near.size(); i++) {
            NbtCompound e = near.getCompound(i);
            if (e.getBoolean("done") || !e.getString("k").equals("photo")) continue;
            double dx = e.getDouble("x") - mc.player.getX(), dz = e.getDouble("z") - mc.player.getZ();
            if (dx * dx + dz * dz > 7 * 7) continue;
            double want = Math.toDegrees(Math.atan2(-(e.getDouble("sx") - mc.player.getX()), e.getDouble("sz") - mc.player.getZ()));
            float diff = MathHelper.wrapDegrees((float) (want - mc.player.getYaw()));
            String arrow = Math.abs(diff) < 30 ? "✔ в кадре" : diff > 0 ? "→ поверни вправо" : "← поверни влево";
            hint = "Фото-точка «" + e.getString("name") + "»: G → «Камера» · " + arrow;
        }
        if (hint == null) return;
        var tr = mc.textRenderer;
        int W = ctx.getScaledWindowWidth(), H = ctx.getScaledWindowHeight();
        int w = tr.getWidth(hint) + 12, y = H - 78;
        ctx.fill(W / 2 - w / 2, y - 3, W / 2 + w / 2, y + 11, 0xB0000000);
        ctx.drawText(tr, hint, W / 2 - w / 2 + 6, y, 0xFF4FB8C0, false);
    }

    // ------------------------------------------------------------------ бой: полоски здоровья сверху
    private static void fightHud(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden || !fightHud.contains("phase")) return;
        var tr = mc.textRenderer;
        int W = ctx.getScaledWindowWidth();
        String phase = fightHud.getString("phase");
        int bw = 120, y = 30;
        int cx = W / 2;
        ctx.fill(cx - bw - 50, y - 4, cx + bw + 50, y + 30, 0xB0000000);
        String a = fightHud.getString("a"), b = fightHud.getString("b");
        ctx.drawText(tr, a, cx - 30 - tr.getWidth(a), y, 0xFFFF6B6B, true);
        ctx.drawText(tr, b, cx + 30, y, 0xFF6BB8FF, true);
        float ha = fightHud.getFloat("ha"), hb = fightHud.getFloat("hb");
        ctx.fill(cx - 30 - bw, y + 12, cx - 30, y + 18, 0xFF3A1010);
        ctx.fill(cx - 30 - (int) (bw * ha), y + 12, cx - 30, y + 18, 0xFFE53935);
        ctx.fill(cx + 30, y + 12, cx + 30 + bw, y + 18, 0xFF10203A);
        ctx.fill(cx + 30, y + 12, cx + 30 + (int) (bw * hb), y + 18, 0xFF1E88E5);
        String mid = switch (phase) {
            case "BETS" -> "СТАВКИ " + fightHud.getInt("timer");
            case "RESULT" -> fightHud.getInt("winner") == 1 ? "◀ ПОБЕДА" : "ПОБЕДА ▶";
            default -> "Р" + fightHud.getInt("round") + " " + fightHud.getInt("timer");
        };
        ctx.drawText(tr, mid, cx - tr.getWidth(mid) / 2, y + 6, 0xFFFFD54F, true);
        int my = fightHud.getInt("mySide");
        if (my > 0) {
            String s = "твоя ставка: " + (my == 1 ? a : b);
            ctx.drawText(tr, s, cx - tr.getWidth(s) / 2, y + 21, 0xFFB0BEC5, false);
        }
    }

    /** Подсказка для автотеста и отладки: открыто ли окно эфира. */
    public static boolean liveOn() {
        return live.getBoolean("on");
    }

    static float yawTo(Vec3d from, Vec3d to) {
        return (float) Math.toDegrees(Math.atan2(-(to.x - from.x), to.z - from.z));
    }

    static void rotY(net.minecraft.client.util.math.MatrixStack m, float deg) {
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(deg));
    }

    static boolean night(MinecraftClient c) {
        long t = c.world.getTimeOfDay() % 24000;
        return t > 12800 && t < 23200;
    }

    static int stars() {
        return NovogradClient.wantedStars;
    }

    static net.minecraft.sound.SoundEvent siren() {
        return ModContent.SIREN;
    }
}
