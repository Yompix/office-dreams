package com.yompix.lifecraft.client.screen;

import com.yompix.lifecraft.LifeCraft;
import com.yompix.lifecraft.client.LifeCraftClient;
import com.yompix.lifecraft.client.LifeHud;
import com.yompix.lifecraft.life.Jobs;
import com.yompix.lifecraft.life.LifeData;
import com.yompix.lifecraft.life.School;
import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Телефон: выезжает снизу, на домашнем экране часы, баланс и иконки приложений.
 * Приложения: Банк, Школа, Работа, Сообщения, Карта, Магазин, Казино, Музыка, Камера, Погода.
 */
public class PhoneScreen extends LifeScreen {
    private static final Identifier WALLPAPER = LifeCraft.id("textures/gui/wallpaper.png");
    private record App(String id, String title, int color) {}

    private static final List<App> APPS = List.of(
            new App("bank", "Банк", 0xFF2FA84F), new App("school", "Школа", 0xFFF29B2B), new App("jobs", "Работа", 0xFF5A6ACF),
            new App("messages", "СМС", 0xFF34C759), new App("map", "Карта", 0xFF3AA0E6), new App("shop", "Магазин", 0xFFE05A47),
            new App("casino", "Казино", 0xFF8E1B3A), new App("music", "Музыка", 0xFFFF375F), new App("camera", "Камера", 0xFF4B4F58),
            new App("weather", "Погода", 0xFF64B5F6), new App("story", "Сюжет", 0xFFFFB300));

    private static final String[][] DISCS = {{"cat", "C418 — cat"}, {"chirp", "C418 — chirp"}, {"mall", "C418 — mall"}, {"mellohi", "C418 — mellohi"},
            {"stal", "C418 — stal"}, {"strad", "C418 — strad"}, {"wait", "C418 — wait"}, {"pigstep", "Lena Raine — Pigstep"},
            {"otherside", "Lena Raine — otherside"}, {"relic", "Aaron Cherof — Relic"}, {"creator", "Lena Raine — Creator"}, {"precipice", "Aaron Cherof — Precipice"}};
    @Nullable private static SoundInstance music;
    private static String playing = "";

    @Nullable private String app;
    private long appOpenedAt = System.currentTimeMillis();
    private int scroll;
    private TextFieldWidget toField, amountField;
    private int[] mapColors;
    private int mapFrame;
    private boolean closing;
    private long closeAt;

    // геометрия телефона
    private int px, py, pw, ph, sx, sy, sw, sh;

    public PhoneScreen(@Nullable String app) {
        super("Телефон");
        this.app = app == null || app.isEmpty() ? null : app;
    }

    @Override
    protected void init() {
        pw = 132;
        ph = Math.min(236, height - 12);
        toField = new TextFieldWidget(textRenderer, 0, 0, 100, 14, Text.literal("Ник"));
        toField.setMaxLength(16);
        toField.setPlaceholder(Text.literal("ник игрока"));
        amountField = new TextFieldWidget(textRenderer, 0, 0, 100, 14, Text.literal("Сумма"));
        amountField.setMaxLength(7);
        amountField.setPlaceholder(Text.literal("сумма $"));
        amountField.setTextPredicate(s -> s.matches("\\d*"));
        openApp(app);
        if (!opened) {
            opened = true;
            LifeCraftClient.send("phone_opened");
        }
    }

    private boolean opened;

    private void openApp(@Nullable String id) {
        app = id;
        appOpenedAt = System.currentTimeMillis();
        scroll = 0;
        clearChildren();
        if ("bank".equals(id)) {
            addDrawableChild(toField);
            addDrawableChild(amountField);
        }
        if ("messages".equals(id)) LifeCraftClient.send("read_messages");
        if ("map".equals(id)) mapColors = null;
    }

    @Override
    public void close() {
        if (closing) {
            super.close();
            return;
        }
        closing = true;
        closeAt = System.currentTimeMillis();
    }

    @Override
    protected void draw(DrawContext ctx, float delta) {
        float a = openAnim();
        if (closing) {
            float t = MathHelper.clamp((System.currentTimeMillis() - closeAt) / 180f, 0, 1);
            a = 1 - t * t;
            if (t >= 1) {
                super.close();
                return;
            }
        }
        px = width / 2 - pw / 2;
        py = (int) (height / 2 - ph / 2 + (1 - a) * (height / 2f + ph / 2f));
        sx = px + 6;
        sy = py + 16;
        sw = pw - 12;
        sh = ph - 30;
        // корпус
        panel(ctx, px, py, pw, ph, 0xFF2A2C33, 0xFF15161A);
        roundRect(ctx, px + 3, py + 3, pw - 6, ph - 6, 0xFF0B0B0E);
        roundRect(ctx, px + pw / 2 - 16, py + 6, 32, 5, 0xFF1E1F25);
        ctx.fill(px + pw / 2 + 9, py + 7, px + pw / 2 + 12, py + 10, 0xFF2D4A7A);
        roundRect(ctx, px + pw / 2 - 12, py + ph - 9, 24, 3, 0xFF3A3C44);
        ctx.enableScissor(sx, sy, sx + sw, sy + sh);
        ctx.drawTexture(WALLPAPER, sx, sy, 0, 0, sw, sh, 96, 176);
        if (app != null) ctx.fill(sx, sy, sx + sw, sy + sh, 0xF0F2F3F7);
        statusBar(ctx);
        float slide = MathHelper.clamp((System.currentTimeMillis() - appOpenedAt) / 200f, 0, 1);
        slide = 1 - (1 - slide) * (1 - slide);
        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, (1 - slide) * 20, 0);
        if (app == null) home(ctx);
        else {
            App info = APPS.stream().filter(x -> x.id().equals(app)).findFirst().orElse(APPS.get(0));
            ctx.fill(sx, sy + 10, sx + sw, sy + 28, info.color());
            ctx.drawText(textRenderer, "‹", sx + 4, sy + 15, 0xFFFFFFFF, false);
            area(sx, sy + 10, 24, 18, () -> openApp(null));
            center(ctx, info.title(), sx + sw / 2, sy + 15, 0xFFFFFFFF);
            int cy = sy + 32;
            switch (app) {
                case "bank" -> bank(ctx, cy);
                case "school" -> school(ctx, cy);
                case "jobs" -> jobs(ctx, cy);
                case "messages" -> messages(ctx, cy);
                case "map" -> map(ctx, cy);
                case "casino" -> casino(ctx, cy);
                case "music" -> music(ctx, cy);
                case "weather" -> weather(ctx, cy);
                case "story" -> story(ctx, cy);
                default -> {}
            }
        }
        ctx.getMatrices().pop();
        ctx.disableScissor();
        // текстовые поля банка следуют за телефоном
        toField.setX(sx + 8);
        toField.setY(sy + 128);
        toField.setWidth(sw - 16);
        amountField.setX(sx + 8);
        amountField.setY(sy + 146);
        amountField.setWidth(sw - 16);
        toField.visible = amountField.visible = "bank".equals(app) && a > 0.95f;
    }

    private void statusBar(DrawContext ctx) {
        int c = app == null ? 0xFFFFFFFF : 0xFF222222;
        String time = LifeHud.clock(mc().world.getTimeOfDay() % 24000);
        ctx.drawText(textRenderer, time, sx + 4, sy + 1, c, false);
        for (int i = 0; i < 4; i++) ctx.fill(sx + sw - 30 + i * 3, sy + 7 - i * 2, sx + sw - 28 + i * 3, sy + 8, c);
        ctx.fill(sx + sw - 15, sy + 2, sx + sw - 4, sy + 8, c);
        ctx.fill(sx + sw - 14, sy + 3, sx + sw - 5, sy + 7, app == null ? 0xFF5BE37D : 0xFF34A853);
        ctx.fill(sx + sw - 4, sy + 4, sx + sw - 3, sy + 6, c);
    }

    // ---------------------------------------------------------------- домашний экран
    private void home(DrawContext ctx) {
        LifeData d = LifeCraftClient.data;
        World w = mc().world;
        bigText(ctx, LifeHud.clock(w.getTimeOfDay() % 24000), sx + sw / 2, sy + 16, 2.2f, 0xFFFFFFFF);
        center(ctx, "День " + (w.getTimeOfDay() / 24000 + 1) + " · " + d.eduName(), sx + sw / 2, sy + 37, 0xFFD8DEFF);
        // виджет баланса
        roundRect(ctx, sx + 8, sy + 50, sw - 16, 22, 0x66000000);
        ctx.drawText(textRenderer, "Баланс", sx + 13, sy + 53, 0xFFB8C4E0, false);
        ctx.drawText(textRenderer, "$" + d.money, sx + 13, sy + 62, 0xFF7CFF8A, true);
        String job = Jobs.Job.of(d.job).title;
        ctx.drawText(textRenderer, job, sx + sw - 12 - textRenderer.getWidth(job), sy + 62, 0xFFFFFFFF, false);
        int cols = 3, iw = 22, gapX = (sw - cols * iw) / (cols + 1);
        float t = seconds();
        for (int i = 0; i < APPS.size(); i++) {
            App ap = APPS.get(i);
            int col = i % cols, row = i / cols;
            int x = sx + gapX + col * (iw + gapX), y = sy + 80 + row * 34;
            // иконки появляются волной
            float appear = MathHelper.clamp((t - i * 0.03f) / 0.25f, 0, 1);
            boolean hov = hover(x, y, iw, iw);
            ctx.getMatrices().push();
            float s = (0.6f + 0.4f * appear) * (hov ? 1.08f : 1f);
            ctx.getMatrices().translate(x + iw / 2f, y + iw / 2f, 0);
            ctx.getMatrices().scale(s * iw / 16f, s * iw / 16f, 1);
            ctx.drawTexture(LifeCraft.id("textures/gui/app/" + ap.id() + ".png"), -8, -8, 0, 0, 16, 16, 16, 16);
            ctx.getMatrices().pop();
            String label = ap.title();
            ctx.getMatrices().push();
            ctx.getMatrices().translate(x + iw / 2f, y + iw + 2, 0);
            ctx.getMatrices().scale(0.7f, 0.7f, 1);
            ctx.drawText(textRenderer, label, -textRenderer.getWidth(label) / 2, 0, 0xFFFFFFFF, true);
            ctx.getMatrices().pop();
            if (ap.id().equals("messages") && d.unread > 0) {
                ctx.fill(x + iw - 6, y - 3, x + iw + 3, y + 6, 0xFFFF3B30);
                center(ctx, String.valueOf(Math.min(9, d.unread)), x + iw - 1, y - 2, 0xFFFFFFFF);
            }
            area(x, y, iw, iw + 8, () -> launch(ap.id()));
        }
    }

    private void launch(String id) {
        switch (id) {
            case "shop" -> mc().setScreen(new ShopScreen(true));
            case "camera" -> LifeCraftClient.takePhoto();
            case "casino" -> {
                if (LifeCraftClient.data.adult()) mc().setScreen(new SlotsScreen());
                else openApp("casino");
            }
            default -> openApp(id);
        }
    }

    // ---------------------------------------------------------------- приложения
    private void bank(DrawContext ctx, int y) {
        LifeData d = LifeCraftClient.data;
        roundRect(ctx, sx + 6, y, sw - 12, 34, 0xFF1F7A3A);
        ctx.drawText(textRenderer, "LifeBank •••• 2024", sx + 11, y + 4, 0xFFB8F0C8, false);
        bigText(ctx, "$" + d.money, sx + 11 + textRenderer.getWidth("$" + d.money), y + 16, 1.5f, 0xFFFFFFFF);
        int ly = y + 40;
        ctx.drawText(textRenderer, "Заработано: $" + d.earned, sx + 8, ly, 0xFF333333, false);
        ctx.drawText(textRenderer, "Потрачено: $" + d.spent, sx + 8, ly + 10, 0xFF333333, false);
        if (d.casinoWon + d.casinoLost > 0) ctx.drawText(textRenderer, "Казино: +" + d.casinoWon + " / −" + d.casinoLost, sx + 8, ly + 20, 0xFF8E1B3A, false);
        button(ctx, sx + 8, y + 72, (sw - 20) / 2, 14, "Снять $50", 0xFF3A7BD5, d.money >= 50, () -> atm("withdraw", 50));
        button(ctx, sx + 12 + (sw - 20) / 2, y + 72, (sw - 20) / 2, 14, "Внести", 0xFF3A7BD5, true, () -> atm("deposit", 0));
        ctx.drawText(textRenderer, "Перевод другу", sx + 8, y + 92, 0xFF555555, false);
        button(ctx, sx + 8, y + 150, sw - 16, 14, "Перевести", 0xFF2FA84F, !toField.getText().isEmpty() && !amountField.getText().isEmpty(), () -> {
            NbtCompound n = new NbtCompound();
            n.putString("to", toField.getText());
            n.putInt("amount", parse(amountField.getText()));
            LifeCraftClient.send("transfer", n);
            amountField.setText("");
        });
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void atm(String action, int amount) {
        NbtCompound n = new NbtCompound();
        n.putString("action", action);
        n.putInt("amount", amount);
        LifeCraftClient.send("atm", n);
    }

    private void school(DrawContext ctx, int y) {
        LifeData d = LifeCraftClient.data;
        ctx.drawText(textRenderer, d.eduName(), sx + 8, y, 0xFF222222, false);
        int need = LifeData.lessonsNeeded(d.edu);
        int bw = sw - 16;
        roundRect(ctx, sx + 8, y + 12, bw, 8, 0xFFDDDDDD);
        float prog = d.edu > LifeData.UNI_END ? 1 : (float) d.lessons / need;
        roundRect(ctx, sx + 8, y + 12, Math.max(2, (int) (bw * prog)), 8, 0xFFF29B2B);
        ctx.drawText(textRenderer, d.edu > LifeData.UNI_END ? "Учёба окончена!" : "Уроков сдано: " + d.lessons + " из " + need, sx + 8, y + 23, 0xFF666666, false);
        ctx.drawText(textRenderer, "Оценки:", sx + 8, y + 37, 0xFF222222, false);
        for (int i = 0; i < d.grades.size() && i < 10; i++) {
            int g = d.grades.get(i);
            int c = g == 5 ? 0xFF2FA84F : g == 4 ? 0xFF7BC043 : g == 3 ? 0xFFF2B02B : 0xFFE53935;
            int gx = sx + 8 + (i % 5) * 21, gy = y + 48 + (i / 5) * 18;
            roundRect(ctx, gx, gy, 18, 15, c);
            center(ctx, String.valueOf(g), gx + 9, gy + 4, 0xFFFFFFFF);
        }
        long tod = mc().world.getTimeOfDay() % 24000;
        boolean lessons = tod >= School.DAY_START && tod < School.DAY_END;
        int ty = y + 90;
        ctx.drawText(textRenderer, "Расписание", sx + 8, ty, 0xFF222222, false);
        wrap(ctx, "Уроки с 8:00 до 18:00, звонок каждый час. Подойди к доске или учителю.", sx + 8, ty + 11, sw - 16, 0xFF555555);
        long wait = d.lastLesson + School.LESSON_COOLDOWN - mc().world.getTime();
        String status = !lessons ? "Сейчас уроков нет" : wait > 0 ? "Перемена: " + (wait / 20 + 1) + " с" : "Можно идти на урок!";
        roundRect(ctx, sx + 6, ty + 56, sw - 12, 14, lessons && wait <= 0 ? 0xFF2FA84F : 0xFF8E8E93);
        center(ctx, status, sx + sw / 2, ty + 59, 0xFFFFFFFF);
        if (d.edu <= LifeData.SCHOOL_END)
            wrap(ctx, "Учебник из магазина убирает один неверный ответ.", sx + 8, ty + 76, sw - 16, 0xFF888888);
    }

    private void jobs(DrawContext ctx, int y) {
        LifeData d = LifeCraftClient.data;
        Jobs.Job cur = Jobs.Job.of(d.job);
        ctx.drawText(textRenderer, "Сейчас: " + cur.title, sx + 8, y, 0xFF222222, false);
        int yy = y + 12;
        if (d.taskPos != null && !d.taskKind.isEmpty()) {
            BlockPos p = d.taskPos;
            int dist = (int) Math.sqrt(p.getSquaredDistance(mc().player.getBlockPos()));
            roundRect(ctx, sx + 6, yy, sw - 12, 22, 0xFFFFF1C2);
            ctx.drawText(textRenderer, (d.taskKind.equals("taxi") ? "Везёшь клиента" : "Доставка") + " · $" + d.taskReward, sx + 10, yy + 3, 0xFF6B4A00, false);
            ctx.drawText(textRenderer, "До цели " + dist + " м (см. Карту)", sx + 10, yy + 12, 0xFF6B4A00, false);
            yy += 26;
        }
        if (cur == Jobs.Job.TAXI) {
            button(ctx, sx + 8, yy, sw - 16, 14, "Взять заказ такси", 0xFFF7C600, true, () -> LifeCraftClient.send("taxi_order"));
            yy += 18;
        }
        yy -= scroll;
        for (Jobs.Job j : Jobs.Job.values()) {
            if (j == Jobs.Job.NONE) continue;
            boolean can = d.edu >= j.minEdu;
            boolean mine = j == cur;
            roundRect(ctx, sx + 6, yy, sw - 12, 54, mine ? 0xFFDDE8FF : 0xFFFFFFFF);
            ctx.drawText(textRenderer, j.title, sx + 10, yy + 3, can ? 0xFF222222 : 0xFF999999, false);
            ctx.getMatrices().push();
            ctx.getMatrices().translate(sx + 10, yy + 13, 0);
            ctx.getMatrices().scale(0.75f, 0.75f, 1);
            wrap(ctx, j.desc, 0, 0, (int) ((sw - 20) / 0.75f), 0xFF666666);
            ctx.getMatrices().pop();
            if (mine) button(ctx, sx + sw - 54, yy + 2, 46, 11, "Уволиться", 0xFFE53935, true, () -> LifeCraftClient.send("job_quit"));
            else if (can) button(ctx, sx + sw - 54, yy + 2, 46, 11, "Устроиться", 0xFF2FA84F, true, () -> {
                NbtCompound n = new NbtCompound();
                n.putString("job", j.id);
                LifeCraftClient.send("job_select", n);
            });
            else {
                String req = "нужно: " + Jobs.eduName(j.minEdu);
                ctx.getMatrices().push();
                ctx.getMatrices().translate(sx + sw - 10, yy + 46, 0);
                ctx.getMatrices().scale(0.7f, 0.7f, 1);
                ctx.drawText(textRenderer, req, -textRenderer.getWidth(req), 0, 0xFFE53935, false);
                ctx.getMatrices().pop();
            }
            yy += 58;
        }
    }

    private void messages(DrawContext ctx, int y) {
        List<String> msgs = LifeCraftClient.data.messages;
        if (msgs.isEmpty()) center(ctx, "Сообщений нет", sx + sw / 2, y + 20, 0xFF888888);
        int yy = y - scroll;
        for (String m : msgs) {
            String[] parts = m.split("\t");
            String from = parts[0], text = parts.length > 1 ? parts[1] : "", day = parts.length > 2 ? "день " + parts[2] : "";
            int th = textRenderer.wrapLines(Text.literal(text), sw - 22).size() * 10;
            roundRect(ctx, sx + 6, yy, sw - 12, th + 16, 0xFFFFFFFF);
            ctx.drawText(textRenderer, from, sx + 10, yy + 3, 0xFF1C7C3C, false);
            ctx.drawText(textRenderer, day, sx + sw - 10 - textRenderer.getWidth(day), yy + 3, 0xFFAAAAAA, false);
            wrap(ctx, text, sx + 10, yy + 13, sw - 22, 0xFF333333);
            yy += th + 20;
        }
    }

    /** Карта сверху: каждый пиксель — блок, цвета как у карты Майнкрафта, стрелка — ты, красная точка — цель. */
    private void map(DrawContext ctx, int y) {
        int size = sw - 12, half = size / 2;
        int mx = sx + 6, my = y;
        World w = mc().world;
        int cx = mc().player.getBlockX(), cz = mc().player.getBlockZ();
        if (mapColors == null || mapFrame++ % 40 == 0) {
            mapColors = new int[size * size];
            BlockPos.Mutable pos = new BlockPos.Mutable();
            for (int dz = 0; dz < size; dz++)
                for (int dx = 0; dx < size; dx++) {
                    int x = cx - half + dx, z = cz - half + dz;
                    int top = w.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1;
                    pos.set(x, top, z);
                    BlockState s = w.getBlockState(pos);
                    MapColor col = s.getMapColor(w, pos);
                    int north = w.getTopY(Heightmap.Type.WORLD_SURFACE, x, z - 1) - 1;
                    MapColor.Brightness br = top > north ? MapColor.Brightness.HIGH : top < north ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
                    int abgr = col == MapColor.CLEAR ? 0xFF203020 : col.getRenderColor(br);
                    mapColors[dz * size + dx] = 0xFF000000 | (abgr & 0xFF) << 16 | (abgr & 0xFF00) | (abgr >> 16 & 0xFF);
                }
        }
        for (int dz = 0; dz < size; dz++) {
            int run = 0;
            for (int dx = 1; dx <= size; dx++) {
                if (dx == size || mapColors[dz * size + dx] != mapColors[dz * size + run]) {
                    ctx.fill(mx + run, my + dz, mx + dx, my + dz + 1, mapColors[dz * size + run]);
                    run = dx;
                }
            }
        }
        ctx.drawBorder(mx - 1, my - 1, size + 2, size + 2, 0xFF333333);
        // цель задания
        LifeData d = LifeCraftClient.data;
        if (d.taskPos != null && !d.taskKind.isEmpty()) {
            int tx = MathHelper.clamp(d.taskPos.getX() - cx + half, 1, size - 2), tz = MathHelper.clamp(d.taskPos.getZ() - cz + half, 1, size - 2);
            int pulse = (System.currentTimeMillis() / 300) % 2 == 0 ? 2 : 3;
            ctx.fill(mx + tx - pulse, my + tz - pulse, mx + tx + pulse, my + tz + pulse, 0xFFFF3B30);
        }
        // игрок — повёрнутая стрелка
        ctx.getMatrices().push();
        ctx.getMatrices().translate(mx + half, my + half, 0);
        ctx.getMatrices().multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(mc().player.getYaw() + 180));
        for (int i = 0; i < 4; i++) ctx.fill(-i, -3 + i, i + 1, -2 + i, 0xFFFFFFFF);
        ctx.fill(-1, -3, 2, 3, 0xFFFFFFFF);
        ctx.fill(0, -2, 1, 2, 0xFF1E88E5);
        ctx.getMatrices().pop();
        int ly = my + size + 4;
        ctx.drawText(textRenderer, "X " + cx + "  Z " + cz, sx + 8, ly, 0xFF333333, false);
        if (d.taskPos != null && !d.taskKind.isEmpty())
            ctx.drawText(textRenderer, "● цель: " + d.taskPos.getX() + ", " + d.taskPos.getZ(), sx + 8, ly + 10, 0xFFE53935, false);
    }

    private void casino(DrawContext ctx, int y) {
        center(ctx, "18+", sx + sw / 2, y + 10, 0xFFE53935);
        wrap(ctx, "Казино «Фортуна» пускает только взрослых. Закончи школу — и приходи: автоматы и рулетка в здании казино, а онлайн-слоты — прямо здесь.", sx + 8, y + 26, sw - 16, 0xFF444444);
    }

    private void music(DrawContext ctx, int y) {
        int yy = y - scroll;
        for (String[] disc : DISCS) {
            boolean on = playing.equals(disc[0]);
            roundRect(ctx, sx + 6, yy, sw - 12, 16, on ? 0xFFFFD6E0 : 0xFFFFFFFF);
            ctx.drawText(textRenderer, (on ? "♪ " : "") + disc[1], sx + 10, yy + 4, on ? 0xFFFF375F : 0xFF333333, false);
            area(sx + 6, yy, sw - 12, 16, () -> play(disc[0]));
            yy += 18;
        }
        button(ctx, sx + 8, yy + 2, sw - 16, 14, "Стоп", 0xFF8E8E93, !playing.isEmpty(), PhoneScreen::stopMusic);
    }

    private static void play(String id) {
        stopMusic();
        SoundEvent ev = discSound(id);
        if (ev == null) return;
        music = new PositionedSoundInstance(ev.getId(), SoundCategory.RECORDS, 1f, 1f, net.minecraft.util.math.random.Random.create(), false, 0,
                SoundInstance.AttenuationType.NONE, 0, 0, 0, true);
        mc().getSoundManager().play(music);
        playing = id;
    }

    @Nullable
    private static SoundEvent discSound(String id) {
        RegistryEntry<SoundEvent> e = switch (id) {
            case "cat" -> SoundEvents.MUSIC_DISC_CAT;
            case "chirp" -> SoundEvents.MUSIC_DISC_CHIRP;
            case "mall" -> SoundEvents.MUSIC_DISC_MALL;
            case "mellohi" -> SoundEvents.MUSIC_DISC_MELLOHI;
            case "stal" -> SoundEvents.MUSIC_DISC_STAL;
            case "strad" -> SoundEvents.MUSIC_DISC_STRAD;
            case "wait" -> SoundEvents.MUSIC_DISC_WAIT;
            case "pigstep" -> SoundEvents.MUSIC_DISC_PIGSTEP;
            case "otherside" -> SoundEvents.MUSIC_DISC_OTHERSIDE;
            case "relic" -> SoundEvents.MUSIC_DISC_RELIC;
            case "creator" -> SoundEvents.MUSIC_DISC_CREATOR;
            case "precipice" -> SoundEvents.MUSIC_DISC_PRECIPICE;
            default -> null;
        };
        return e == null ? null : e.value();
    }

    private static void stopMusic() {
        if (music != null) mc().getSoundManager().stop(music);
        music = null;
        playing = "";
    }

    /** Дневник сюжета: глава, миссия и что делать дальше. */
    private void story(DrawContext ctx, int y) {
        var o = com.yompix.lifecraft.client.story.ClientStory.objective;
        LifeData d = LifeCraftClient.data;
        if (d.storyDone) {
            center(ctx, "Сюжет пройден!", sx + sw / 2, y + 10, 0xFF2FA84F);
            wrap(ctx, "Город твой: работай, учись, покупай машины и дома. Тёма, Лиза и Глеб всегда рады тебя видеть.", sx + 8, y + 26, sw - 16, 0xFF444444);
            return;
        }
        if (!o.contains("mission")) {
            wrap(ctx, "Сюжет начнётся в мире «LifeCraft: Город».", sx + 8, y + 10, sw - 16, 0xFF444444);
            return;
        }
        ctx.drawText(textRenderer, o.getString("chapter"), sx + 8, y, 0xFF8A6D00, false);
        roundRect(ctx, sx + 6, y + 12, sw - 12, 16, 0xFFFFE9A8);
        ctx.drawText(textRenderer, o.getString("mission"), sx + 10, y + 16, 0xFF5A4300, false);
        ctx.drawText(textRenderer, "Что делать:", sx + 8, y + 34, 0xFF222222, false);
        int h = wrap(ctx, o.getString("hint"), sx + 8, y + 45, sw - 16, 0xFF444444);
        if (o.contains("extra")) wrap(ctx, o.getString("extra"), sx + 8, y + 49 + h, sw - 16, 0xFF1E88E5);
        ctx.drawText(textRenderer, "Возраст: " + (d.age == 0 ? 18 : d.age) + " · " + d.eduName(), sx + 8, y + 74 + h, 0xFF888888, false);
        wrap(ctx, "Золотой столб света показывает, куда идти. Карта в телефоне тоже.", sx + 8, y + 90 + h, sw - 16, 0xFF888888);
    }

    private void weather(DrawContext ctx, int y) {
        World w = mc().world;
        boolean rain = w.isRaining(), thunder = w.isThundering();
        long tod = w.getTimeOfDay() % 24000;
        boolean night = tod > 12500 && tod < 23500;
        String sky = thunder ? "Гроза" : rain ? "Дождь" : night ? "Ясная ночь" : "Солнечно";
        float temp = w.getBiome(mc().player.getBlockPos()).value().getTemperature() * 25 + (night ? -6 : 3) - (rain ? 4 : 0);
        roundRect(ctx, sx + 6, y, sw - 12, 60, rain ? 0xFF5C6F87 : night ? 0xFF1B2347 : 0xFF4FA3F7);
        bigText(ctx, Math.round(temp) + "°", sx + 34, y + 14, 3f, 0xFFFFFFFF);
        ctx.drawText(textRenderer, sky, sx + 62, y + 16, 0xFFFFFFFF, true);
        ctx.drawText(textRenderer, LifeHud.clock(tod), sx + 62, y + 28, 0xFFE0E8FF, false);
        String[] hours = {"Восход 6:00", "Уроки 8:00–18:00", "Закат 18:30", "Спать — ночью"};
        for (int i = 0; i < hours.length; i++) ctx.drawText(textRenderer, hours[i], sx + 8, y + 68 + i * 11, 0xFF444444, false);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        scroll = Math.max(0, scroll - (int) (v * 12));
        return true;
    }

    @Override
    protected void background(DrawContext ctx) {
        ctx.fillGradient(0, 0, width, height, 0x30000000, 0x70000000);
    }
}
