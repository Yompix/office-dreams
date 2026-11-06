package com.yompix.novograd.client.screen;

import com.yompix.novograd.Novograd;
import com.yompix.novograd.client.NovogradClient;
import com.yompix.novograd.client.LifeHud;
import com.yompix.novograd.client.ui.Ui;
import com.yompix.novograd.life.Jobs;
import com.yompix.novograd.life.LifeData;
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
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Телефон: выезжает снизу, на домашнем экране часы, баланс и иконки приложений.
 * Приложения: Банк, Школа, Работа, Сообщения, Карта, Магазин, Казино, Музыка, Камера, Погода.
 */
public class PhoneScreen extends LifeScreen {
    private static final Identifier WALLPAPER = Novograd.id("textures/gui/wallpaper.png");
    private record App(String id, String title, int color) {}

    private static final List<App> APPS = List.of(
            new App("bank", "Банк", 0xFF2FA84F), new App("jobs", "Работа", 0xFF5A6ACF),
            new App("messages", "СМС", 0xFF34C759), new App("map", "Карта", 0xFF3AA0E6), new App("shop", "Магазин", 0xFFE05A47),
            new App("casino", "Казино", 0xFF8E1B3A), new App("music", "Радио", 0xFFFF375F), new App("camera", "Камера", 0xFF4B4F58),
            new App("weather", "Погода", 0xFF64B5F6), new App("story", "Сюжет", 0xFFFFB300), new App("trophy", "Награды", 0xFFC99A2E),
            new App("home", "Дом", 0xFFA0785A),
            new App("taxi", "Такси", 0xFFF7C600), new App("friends", "Друзья", 0xFF7E57C2), new App("missions", "Задания", 0xFFD32F2F),
            new App("style", "Стиль", 0xFFFF2E88), new App("finance", "Финансы", 0xFF2E7D32), new App("news", "Новости", 0xFFD32F2F), new App("guard", "Охрана", 0xFF28E6FF),
            new App("auto", "Авто", 0xFF00897B),
            new App("collect", "Коллекции", 0xFFFF4FB8));

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
        pw = 148;
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
            NovogradClient.send("phone_opened");
        }
    }

    private boolean opened;

    private void openApp(@Nullable String id) {
        if ("missions".equals(id)) {
            // список побочных миссий — отдельный экран, данные приходят с сервера
            NovogradClient.send("side_list");
            return;
        }
        app = id;
        appOpenedAt = System.currentTimeMillis();
        scroll = 0;
        clearChildren();
        if ("bank".equals(id)) {
            addDrawableChild(toField);
            addDrawableChild(amountField);
        }
        if ("messages".equals(id)) NovogradClient.send("read_messages");
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
        // корпус: чёрный, с неоновой кромкой (розовый сверху → бирюзовый снизу), боковые кнопки
        ctx.fill(px + 5, py + 6, px + pw + 5, py + ph + 6, 0x70000000);
        ctx.fill(px - 1, py + 20, px, py + 34, 0xFF2A2433);
        ctx.fill(px + pw, py + 26, px + pw + 1, py + 50, 0xFF2A2433);
        roundRect(ctx, px, py, pw, ph, 0xFF000000);
        ctx.fillGradient(px + 1, py + 1, px + pw - 1, py + ph - 1, 0xFFFF2E88, 0xFF28E6FF);
        roundRect(ctx, px + 2, py + 2, pw - 4, ph - 4, 0xFF0A080E);
        ctx.fill(px + 3, py + 3, px + pw - 3, py + 4, 0x30FFFFFF);
        ctx.enableScissor(sx, sy, sx + sw, sy + sh);
        if (app == null) {
            // обои: ночное небо, закатное свечение и силуэт города
            ctx.fillGradient(sx, sy, sx + sw, sy + sh, 0xFF1A0B2E, 0xFF07040C);
            ctx.fillGradient(sx, sy + sh - 90, sx + sw, sy + sh - 30, 0x00FF2E88, 0x60FF2E88);
            Ui.tex(ctx, Ui.SKYLINE_FG, sx - 20, sy + sh - 52, sw + 40, (sw + 40) / 4f, Ui.FG_SIZE[0], Ui.FG_SIZE[1], 1f);
            ctx.fill(sx, sy + sh - 10, sx + sw, sy + sh, 0xFF07040C);
        } else ctx.fill(sx, sy, sx + sw, sy + sh, 0xFF0E0A14);
        // камера-«капля» поверх экрана
        ctx.fill(sx + sw / 2 - 2, sy + 2, sx + sw / 2 + 2, sy + 6, 0xFF000000);
        ctx.fill(sx + sw / 2 - 3, sy + 3, sx + sw / 2 + 3, sy + 5, 0xFF000000);
        statusBar(ctx);
        float slide = MathHelper.clamp((System.currentTimeMillis() - appOpenedAt) / 200f, 0, 1);
        slide = 1 - (1 - slide) * (1 - slide);
        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, (1 - slide) * 20, 0);
        if (app == null) home(ctx);
        else {
            App info = APPS.stream().filter(x -> x.id().equals(app)).findFirst().orElse(APPS.get(0));
            // шапка приложения: косая плашка цвета приложения, крупное название
            ctx.fill(sx, sy + 10, sx + sw, sy + 28, 0xFF15101D);
            Ui.slant(ctx, sx + 18, sy + 12, textRenderer.getWidth(info.title().toUpperCase()) + 12, 14, 4, info.color() | 0xFF000000);
            ctx.fill(sx, sy + 27, sx + sw, sy + 28, info.color() | 0xFF000000);
            boolean backHov = hover(sx, sy + 10, 18, 18);
            ctx.drawText(textRenderer, "‹", sx + 7 - (backHov ? 1 : 0), sy + 15, backHov ? Ui.PINK : 0xFFFFFFFF, false);
            area(sx, sy + 10, 18, 18, () -> openApp(null));
            ctx.drawText(textRenderer, info.title().toUpperCase(), sx + 24, sy + 15, 0xFFFFFFFF, true);
            int cy = sy + 32;
            ctx.enableScissor(sx, sy + 28, sx + sw, sy + sh); // содержимое приложения не налезает на шапку при прокрутке
            switch (app) {
                case "bank" -> bank(ctx, cy);
                case "jobs" -> jobs(ctx, cy);
                case "messages" -> messages(ctx, cy);
                case "map" -> map(ctx, cy);
                case "casino" -> casino(ctx, cy);
                case "music" -> music(ctx, cy);
                case "weather" -> weather(ctx, cy);
                case "story" -> story(ctx, cy);
                case "trophy" -> trophies(ctx, cy);
                case "home" -> homes(ctx, cy);
                case "taxi" -> taxi(ctx, cy);
                case "friends" -> friends(ctx, cy);
                default -> {}
            }
            ctx.disableScissor();
        }
        ctx.getMatrices().pop();
        ctx.disableScissor();
        // текстовые поля банка следуют за телефоном
        toField.setX(sx + 8);
        toField.setY(sy + 132);
        toField.setWidth(sw - 16);
        amountField.setX(sx + 8);
        amountField.setY(sy + 149);
        amountField.setWidth(sw - 16);
        toField.visible = amountField.visible = "bank".equals(app) && a > 0.95f;
    }

    private void statusBar(DrawContext ctx) {
        int c = 0xFFFFFFFF;
        String time = LifeHud.clock(mc().world.getTimeOfDay() % 24000);
        ctx.drawText(textRenderer, time, sx + 5, sy + 1, c, false);
        for (int i = 0; i < 4; i++) ctx.fill(sx + sw - 31 + i * 3, sy + 7 - i * 2, sx + sw - 29 + i * 3, sy + 8, c);
        ctx.fill(sx + sw - 16, sy + 2, sx + sw - 5, sy + 8, 0x90FFFFFF);
        ctx.fill(sx + sw - 15, sy + 3, sx + sw - 8, sy + 7, Ui.MONEY);
        ctx.fill(sx + sw - 5, sy + 4, sx + sw - 4, sy + 6, 0x90FFFFFF);
    }

    // ---------------------------------------------------------------- домашний экран
    private void home(DrawContext ctx) {
        LifeData d = NovogradClient.data;
        World w = mc().world;
        // часы крупно, дата
        Ui.big(ctx, textRenderer, LifeHud.clock(w.getTimeOfDay() % 24000), sx + sw / 2f, sy + 14, 2.6f, 0xFFFFFFFF, 1);
        Ui.small(ctx, textRenderer, ("День " + (w.getTimeOfDay() / 24000 + 1) + " · Новоград").toUpperCase(), sx + sw / 2f, sy + 37, 0.75f, 0xFFD8D0F0, 1);
        // виджет баланса: стекло, деньги зелёным
        int by = sy + 47;
        ctx.fill(sx + 7, by, sx + sw - 7, by + 22, 0x900B0812);
        ctx.fill(sx + 7, by, sx + 9, by + 22, Ui.MONEY);
        Ui.small(ctx, textRenderer, "БАЛАНС", sx + 13, by + 3, 0.65f, 0xFFB8B2CC, 0);
        Ui.big(ctx, textRenderer, "$" + LifeHud.money(d.money), sx + 13, by + 10, 1f, Ui.MONEY, 0);
        String job = Jobs.Job.of(d.job).title;
        Ui.small(ctx, textRenderer, job, sx + sw - 11, by + 12, 0.7f, 0xFFFFFFFF, 2);
        // сетка подстраивается под экран: все приложения помещаются в телефон (5 колонок и иконки меньше, если тесно)
        int top = 76, avail = sh - top - 18;
        int cols = 4, rows = (APPS.size() + cols - 1) / cols;
        while (rows * 31 > avail && cols < 6) { cols++; rows = (APPS.size() + cols - 1) / cols; } // все приложения влезают в экран
        int rowH = Math.min(31, avail / Math.max(1, rows));
        int iw = Math.max(14, Math.min(24, Math.min(rowH - 8, (sw - 8) / cols - 6))), gapX = (sw - cols * iw) / (cols + 1);
        float t = seconds();
        for (int i = 0; i < APPS.size(); i++) {
            App ap = APPS.get(i);
            int col = i % cols, row = i / cols;
            int x = sx + gapX + col * (iw + gapX), y = sy + top + row * rowH;
            // иконки появляются волной
            float appear = MathHelper.clamp((t - i * 0.025f) / 0.25f, 0, 1);
            boolean hov = hover(x, y, iw, iw);
            ctx.getMatrices().push();
            float s = (0.6f + 0.4f * Ui.easeOut(appear)) * (hov ? 1.1f : 1f);
            ctx.getMatrices().translate(x + iw / 2f, y + iw / 2f - (hov ? 1 : 0), 0);
            ctx.getMatrices().scale(s * iw / 64f, s * iw / 64f, 1);
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            ctx.setShaderColor(1, 1, 1, appear);
            ctx.drawTexture(Novograd.id("textures/gui/ui/app/" + ap.id() + ".png"), -32, -32, 0, 0, 64, 64, 64, 64);
            ctx.setShaderColor(1, 1, 1, 1);
            ctx.getMatrices().pop();
            String label = ap.title();
            float ls = Math.min(0.6f, (iw + gapX - 2) / (float) Math.max(1, textRenderer.getWidth(label))); // подпись не налезает на соседнюю
            Ui.small(ctx, textRenderer, label, x + iw / 2f, y + iw + 1, ls, hov ? Ui.PINK : 0xFFFFFFFF, 1);
            if (ap.id().equals("messages") && d.unread > 0) {
                ctx.fill(x + iw - 5, y - 3, x + iw + 4, y + 6, 0xFFFF2E4A);
                center(ctx, String.valueOf(Math.min(9, d.unread)), x + iw, y - 2, 0xFFFFFFFF);
            }
            area(x, y, iw, iw + 7, () -> launch(ap.id()));
        }
    }

    private void launch(String id) {
        switch (id) {
            case "shop" -> mc().setScreen(new ShopScreen(true));
            case "camera" -> NovogradClient.takePhoto();
            case "style" -> com.yompix.novograd.client.style.StyleClient.phone();
            case "finance" -> mc().setScreen(new FinanceScreen());
            case "news" -> mc().setScreen(new NewsScreen());
            case "collect" -> mc().setScreen(new CollectionsScreen());
            case "home" -> mc().setScreen(new RealEstateScreen()); // риелтор: квартиры, дома, виллы, пентхаус (world/Housing)
            case "guard" -> mc().setScreen(new BodyguardScreen()); // агентство «Щит» — телохранители
            case "auto" -> mc().setScreen(new DriveScreen()); // штрафы, штрафстоянка, бензин
            case "casino" -> {
                if (NovogradClient.data.adult()) mc().setScreen(new com.yompix.novograd.client.screen.casino.CasinoLobbyScreen());
                else openApp("casino");
            }
            default -> openApp(id);
        }
    }

    // ---------------------------------------------------------------- приложения
    private void bank(DrawContext ctx, int y) {
        LifeData d = NovogradClient.data;
        roundRect(ctx, sx + 6, y, sw - 12, 34, 0xFF1F7A3A);
        ctx.drawText(textRenderer, "НОВОГРАД БАНК •••• 2024", sx + 11, y + 4, 0xFFB8F0C8, false);
        bigText(ctx, "$" + d.money, sx + 11 + textRenderer.getWidth("$" + d.money), y + 16, 1.5f, 0xFFFFFFFF);
        int ly = y + 40;
        ctx.drawText(textRenderer, "Заработано: $" + d.earned, sx + 8, ly, 0xFFE6E2F0, false);
        ctx.drawText(textRenderer, "Потрачено: $" + d.spent, sx + 8, ly + 10, 0xFFE6E2F0, false);
        if (d.casinoWon + d.casinoLost > 0) ctx.drawText(textRenderer, "Казино: +" + d.casinoWon + " / −" + d.casinoLost, sx + 8, ly + 20, 0xFFFF5A7A, false);
        button(ctx, sx + 8, y + 72, (sw - 20) / 2, 14, "Снять $50", 0xFF3A7BD5, d.money >= 50, () -> atm("withdraw", 50));
        button(ctx, sx + 12 + (sw - 20) / 2, y + 72, (sw - 20) / 2, 14, "Внести", 0xFF3A7BD5, true, () -> atm("deposit", 0));
        ctx.drawText(textRenderer, "Перевод другу", sx + 8, y + 89, 0xFFB8B2CC, false);
        button(ctx, sx + 8, y + 150, sw - 16, 14, "Перевести", 0xFF2FA84F, !toField.getText().isEmpty() && !amountField.getText().isEmpty(), () -> {
            NbtCompound n = new NbtCompound();
            n.putString("to", toField.getText());
            n.putInt("amount", parse(amountField.getText()));
            NovogradClient.send("transfer", n);
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
        NovogradClient.send("atm", n);
    }

    private void jobs(DrawContext ctx, int y) {
        LifeData d = NovogradClient.data;
        Jobs.Job cur = Jobs.Job.of(d.job);
        ctx.drawText(textRenderer, "Сейчас: " + cur.title, sx + 8, y, 0xFFFFFFFF, false);
        int yy = y + 12;
        if (d.taskPos != null && !d.taskKind.isEmpty()) {
            BlockPos p = d.taskPos;
            int dist = (int) Math.sqrt(p.getSquaredDistance(mc().player.getBlockPos()));
            roundRect(ctx, sx + 6, yy, sw - 12, 22, 0xFF3A2E10);
            ctx.drawText(textRenderer, (d.taskKind.equals("taxi") ? "Везёшь клиента" : "Доставка") + " · $" + d.taskReward, sx + 10, yy + 3, 0xFFFFD36E, false);
            ctx.drawText(textRenderer, "До цели " + dist + " м (см. Карту)", sx + 10, yy + 12, 0xFFFFD36E, false);
            yy += 26;
        }
        if (cur == Jobs.Job.TAXI) {
            button(ctx, sx + 8, yy, sw - 16, 14, "Взять заказ такси", 0xFFF7C600, true, () -> NovogradClient.send("taxi_order"));
            yy += 18;
        }
        yy -= scroll;
        for (Jobs.Job j : Jobs.Job.values()) {
            if (j == Jobs.Job.NONE) continue;
            boolean can = true; // учёбы больше нет — работа открыта всем
            boolean mine = j == cur;
            roundRect(ctx, sx + 6, yy, sw - 12, 54, mine ? 0xFF22305A : 0xFF1C1626);
            ctx.drawText(textRenderer, j.title, sx + 10, yy + 3, can ? 0xFFFFFFFF : 0xFF7A748C, false);
            ctx.getMatrices().push();
            ctx.getMatrices().translate(sx + 10, yy + 13, 0);
            ctx.getMatrices().scale(0.75f, 0.75f, 1);
            wrap(ctx, j.desc, 0, 0, (int) ((sw - 20) / 0.75f), 0xFFA9A3BC);
            ctx.getMatrices().pop();
            if (mine) button(ctx, sx + sw - 54, yy + 2, 46, 11, "Уволиться", 0xFFE53935, true, () -> NovogradClient.send("job_quit"));
            else if (can) button(ctx, sx + sw - 54, yy + 2, 46, 11, "Устроиться", 0xFF2FA84F, true, () -> {
                NbtCompound n = new NbtCompound();
                n.putString("job", j.id);
                NovogradClient.send("job_select", n);
            });
            else {
                String req = "пока недоступно";
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
        List<String> msgs = NovogradClient.data.messages;
        if (msgs.isEmpty()) center(ctx, "Сообщений нет", sx + sw / 2, y + 20, 0xFF8F89A3);
        int yy = y - scroll;
        for (String m : msgs) {
            String[] parts = m.split("\t");
            String from = parts[0], text = parts.length > 1 ? parts[1] : "", day = parts.length > 2 ? "день " + parts[2] : "";
            int th = textRenderer.wrapLines(Text.literal(text), sw - 22).size() * 10;
            roundRect(ctx, sx + 6, yy, sw - 12, th + 16, 0xFF1C1626);
            ctx.drawText(textRenderer, from, sx + 10, yy + 3, 0xFF62E57E, false);
            ctx.drawText(textRenderer, day, sx + sw - 10 - textRenderer.getWidth(day), yy + 3, 0xFF8A8598, false);
            wrap(ctx, text, sx + 10, yy + 13, sw - 22, 0xFFE6E2F0);
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
        ctx.drawBorder(mx - 1, my - 1, size + 2, size + 2, 0xFFE6E2F0);
        // цель задания
        LifeData d = NovogradClient.data;
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
        ctx.fill(0, -2, 1, 2, 0xFF6EC8FF);
        ctx.getMatrices().pop();
        int ly = my + size + 4;
        ctx.drawText(textRenderer, "X " + cx + "  Z " + cz, sx + 8, ly, 0xFFE6E2F0, false);
        if (d.taskPos != null && !d.taskKind.isEmpty())
            ctx.drawText(textRenderer, "● цель: " + d.taskPos.getX() + ", " + d.taskPos.getZ(), sx + 8, ly + 10, 0xFFE53935, false);
    }

    private void casino(DrawContext ctx, int y) {
        center(ctx, "18+", sx + sw / 2, y + 10, 0xFFE53935);
        wrap(ctx, "Казино «Фортуна» сейчас закрыто — загляни позже.", sx + 8, y + 26, sw - 16, 0xFFD0CBE0);
    }

    /** «Радио»: станции Новограда в наушниках — слушай пешком; в машине играет та же станция. */
    private void music(DrawContext ctx, int y) {
        int yy = y - scroll;
        var st = com.yompix.novograd.client.CarRadio.STATIONS;
        int cur = com.yompix.novograd.client.NovogradConfig.get().radioStation;
        for (int i = 0; i < st.length; i++) {
            boolean on = com.yompix.novograd.client.CarRadio.pocket && cur == i;
            int col = 0xFF000000 | Integer.parseInt(st[i][3], 16);
            roundRect(ctx, sx + 6, yy, sw - 12, 20, on ? 0xFF4A1830 : 0xFF1C1626);
            ctx.fill(sx + 8, yy + 3, sx + 10, yy + 17, col);
            ctx.drawText(textRenderer, (on ? "♪ " : "") + st[i][0], sx + 14, yy + 2, on ? col : 0xFFE6E2F0, false);
            Ui.small(ctx, textRenderer, st[i][2], sx + 14, yy + 12, 0.7f, 0xFF9A93AE, 0);
            final int idx = i;
            area(sx + 6, yy, sw - 12, 20, () -> com.yompix.novograd.client.CarRadio.pocket(idx));
            yy += 22;
        }
        button(ctx, sx + 8, yy + 2, sw - 16, 14, "Выключить", 0xFF8E8E93, com.yompix.novograd.client.CarRadio.pocket, () -> com.yompix.novograd.client.CarRadio.pocket(-1));
        Ui.small(ctx, textRenderer, "В машине: колесо мыши или R — станция", sx + sw / 2f, yy + 22, 0.7f, 0xFF9A93AE, 1);
    }

    public static void play(String id) {
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

    public static void stopMusic() {
        if (music != null) mc().getSoundManager().stop(music);
        music = null;
        playing = "";
    }

    private void trophies(DrawContext ctx, int y) {
        LifeData d = NovogradClient.data;
        int got = 0;
        for (String id : com.yompix.novograd.life.Achievements.ALL.keySet()) if (d.achievements.contains(id)) got++;
        ctx.drawText(textRenderer, "Получено " + got + " из " + com.yompix.novograd.life.Achievements.ALL.size(), sx + 8, y, 0xFFFFC428, false);
        int yy = y + 12 - scroll;
        for (var e : com.yompix.novograd.life.Achievements.ALL.entrySet()) {
            boolean has = d.achievements.contains(e.getKey());
            roundRect(ctx, sx + 6, yy, sw - 12, 22, has ? 0xFF3A2E10 : 0xFF1C1626);
            ctx.drawText(textRenderer, (has ? "★ " : "☆ ") + e.getValue()[0], sx + 10, yy + 3, has ? 0xFFFFC428 : 0xFF7A748C, false);
            ctx.getMatrices().push();
            ctx.getMatrices().translate(sx + 10, yy + 13, 0);
            ctx.getMatrices().scale(0.7f, 0.7f, 1);
            ctx.drawText(textRenderer, e.getValue()[1], 0, 0, 0xFF9A94AE, false);
            ctx.getMatrices().pop();
            yy += 24;
        }
    }

    /** Вызов такси: куда ехать и сколько стоит. Список — ближние сверху, листается колесом, не налезает на шапку. */
    private void taxi(DrawContext ctx, int y) {
        var places = com.yompix.novograd.client.story.ClientStory.places;
        ctx.drawText(textRenderer, "Куда поедем?", sx + 8, y, 0xFFFFFFFF, false);
        int top = y + 12, bottom = sy + sh - 6;
        boolean taxiJob = com.yompix.novograd.life.Jobs.Job.of(NovogradClient.data.job) == com.yompix.novograd.life.Jobs.Job.TAXI;
        if (taxiJob) bottom -= 20;
        // места: только с названием, без повторов, ближние первыми
        java.util.List<Object[]> list = new java.util.ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        var me = mc().player.getPos();
        // только места Новограда и Ривьеры из списка такси (без старых адресов)
        for (var en : com.yompix.novograd.life.Taxi.PLACES.entrySet()) {
            String id = en.getKey(), name = en.getValue();
            Vec3d to = null;
            if (places.get(id) instanceof net.minecraft.nbt.NbtCompound pl) to = new Vec3d(pl.getInt("x"), me.y, pl.getInt("z"));
            else if (com.yompix.novograd.world.Island.PLACES.get(id) instanceof double[] v) to = new Vec3d(v[0], me.y, v[2]);
            if (to == null || !seen.add(name)) continue;
            list.add(new Object[]{id, name, to, to.squaredDistanceTo(me)});
        }
        list.sort(java.util.Comparator.comparingDouble(o -> (double) o[3]));
        var wp = com.yompix.novograd.client.story.ClientStory.waypoint;
        int rows = list.size() + (wp != null ? 1 : 0);
        int maxScroll = Math.max(0, rows * 18 - (bottom - top));
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        ctx.enableScissor(sx, top, sx + sw, bottom);
        int yy = top - scroll;
        int bw = 40, nameW = sw - 12 - bw - 10;
        if (wp != null) {
            int price = com.yompix.novograd.life.Taxi.price(me, wp);
            roundRect(ctx, sx + 6, yy, sw - 12, 16, 0xFF3A2E10);
            ctx.drawText(textRenderer, textRenderer.trimToWidth("★ Метка на карте", nameW), sx + 10, yy + 4, 0xFFFFC428, false);
            final Vec3d to = wp;
            if (yy >= top - 2 && yy + 16 <= bottom + 2) button(ctx, sx + sw - bw - 8, yy + 2, bw, 12, "$" + price, 0xFFF7C600, NovogradClient.data.money >= price,
                    () -> callTaxi((int) Math.floor(to.x) + "," + (int) Math.floor(to.z)));
            yy += 18;
        }
        for (Object[] o : list) {
            if (yy + 16 >= top && yy <= bottom) {
                String id = (String) o[0], name = (String) o[1];
                int price = com.yompix.novograd.life.Taxi.price(me, (Vec3d) o[2]);
                roundRect(ctx, sx + 6, yy, sw - 12, 16, 0xFF1C1626);
                String shown = textRenderer.getWidth(name) > nameW ? textRenderer.trimToWidth(name, nameW - 6) + "…" : name;
                ctx.drawText(textRenderer, shown, sx + 10, yy + 4, 0xFFE6E2F0, false);
                if (yy >= top - 2 && yy + 16 <= bottom + 2) button(ctx, sx + sw - bw - 8, yy + 2, bw, 12, "$" + price, 0xFFF7C600, NovogradClient.data.money >= price, () -> callTaxi(id));
            }
            yy += 18;
        }
        ctx.disableScissor();
        if (maxScroll > 0) { // полоса прокрутки
            int barH = Math.max(12, (bottom - top) * (bottom - top) / (rows * 18));
            int barY = top + (bottom - top - barH) * scroll / maxScroll;
            ctx.fill(sx + sw - 3, barY, sx + sw - 1, barY + barH, 0x80FFFFFF);
        }
        if (taxiJob) button(ctx, sx + 8, bottom + 4, sw - 16, 14, "Принять вызов игрока", 0xFF2FA84F, true, () -> NovogradClient.send("taxi_accept"));
    }

    private void callTaxi(String dest) {
        net.minecraft.nbt.NbtCompound n = new net.minecraft.nbt.NbtCompound();
        n.putString("dest", dest);
        NovogradClient.send("taxi_call", n);
        close();
    }

    /** Друзья онлайн: где они и что у них по жизни. */
    private void friends(DrawContext ctx, int y) {
        var list = mc().getNetworkHandler() == null ? java.util.List.<net.minecraft.client.network.PlayerListEntry>of() : new java.util.ArrayList<>(mc().getNetworkHandler().getPlayerList());
        ctx.drawText(textRenderer, "В городе сейчас: " + list.size(), sx + 8, y, 0xFFFFFFFF, false);
        int yy = y + 12 - scroll;
        for (var e : list) {
            String name = e.getProfile().getName();
            var pl = mc().world.getPlayers().stream().filter(p -> p.getGameProfile().getName().equals(name)).findFirst().orElse(null);
            roundRect(ctx, sx + 6, yy, sw - 12, 26, 0xFF1C1626);
            ctx.drawText(textRenderer, name + (name.equals(mc().player.getGameProfile().getName()) ? " (ты)" : ""), sx + 10, yy + 3, 0xFFB79CFF, false);
            String where = pl == null ? "далеко" : (int) pl.distanceTo(mc().player) + " м от тебя";
            for (var el : com.yompix.novograd.client.story.ClientStory.players.getList("list", net.minecraft.nbt.NbtElement.COMPOUND_TYPE)) {
                var f = (net.minecraft.nbt.NbtCompound) el;
                if (!f.getString("name").equals(name)) continue;
                double dx = f.getDouble("x") - mc().player.getX(), dz = f.getDouble("z") - mc().player.getZ();
                where = (pl == null ? (int) Math.sqrt(dx * dx + dz * dz) + " м · " : where + " · ") + f.getString("job");
                if (pl == null) {
                    final Vec3d at = new Vec3d(f.getDouble("x"), mc().player.getY(), f.getDouble("z"));
                    button(ctx, sx + sw - 46, yy + 6, 38, 13, "Метка", 0xFF7E57C2, true, () -> com.yompix.novograd.client.story.ClientStory.waypoint = at);
                }
            }
            ctx.drawText(textRenderer, where, sx + 10, yy + 14, 0xFF9A94AE, false);
            if (pl != null && pl != mc().player) {
                final Vec3d at = pl.getPos();
                button(ctx, sx + sw - 46, yy + 6, 38, 13, "Метка", 0xFF7E57C2, true, () -> com.yompix.novograd.client.story.ClientStory.waypoint = at);
            }
            yy += 28;
        }
        wrap(ctx, "Сюжет общий: если друг ушёл дальше, ты подхватишь его историю при входе. Деньги можно перевести в «Банке».", sx + 8, yy + 2, sw - 16, 0xFF8F89A3);
    }

    private void homes(DrawContext ctx, int y) {
        LifeData d = NovogradClient.data;
        wrap(ctx, "Купи свой дом — после сна будешь просыпаться там.", sx + 8, y, sw - 16, 0xFFB8B2CC);
        int yy = y + 24;
        if (d.flags.contains("own_home")) {
            roundRect(ctx, sx + 6, yy, sw - 12, 18, 0xFF143A20);
            ctx.drawText(textRenderer, "Свой дом — твой", sx + 10, yy + 5, 0xFF62E57E, false);
            yy += 22;
        }
        for (String[] h : com.yompix.novograd.life.CityLife.HOUSES) {
            boolean own = d.flags.contains("house:" + h[0]);
            roundRect(ctx, sx + 6, yy, sw - 12, 34, own ? 0xFF143A20 : 0xFF1C1626);
            ctx.drawText(textRenderer, h[1], sx + 10, yy + 4, 0xFFE6E2F0, false);
            if (own) ctx.drawText(textRenderer, "Куплен ✔", sx + 10, yy + 18, 0xFF62E57E, false);
            else button(ctx, sx + 10, yy + 16, sw - 28, 13, "Купить $" + h[2], 0xFFA0785A, d.adult() && d.money >= Integer.parseInt(h[2]), () -> {
                net.minecraft.nbt.NbtCompound n = new net.minecraft.nbt.NbtCompound();
                n.putString("id", h[0]);
                NovogradClient.send("buy_house", n);
            });
            yy += 38;
        }
    }

    /** Дневник сюжета: глава, миссия и что делать дальше. */
    private void story(DrawContext ctx, int y) {
        var o = com.yompix.novograd.client.story.ClientStory.objective;
        LifeData d = NovogradClient.data;
        if (d.storyDone) {
            center(ctx, "Сюжет пройден!", sx + sw / 2, y + 10, 0xFF2FA84F);
            wrap(ctx, "Город твой: гоняй, грабь банки, покупай бизнесы и захватывай районы. Ники, Брик и Роза всегда на связи.", sx + 8, y + 26, sw - 16, 0xFFD0CBE0);
            return;
        }
        if (!o.contains("mission")) {
            wrap(ctx, "Сюжет начнётся в мире «Новоград: Город».", sx + 8, y + 10, sw - 16, 0xFFD0CBE0);
            return;
        }
        ctx.drawText(textRenderer, o.getString("chapter"), sx + 8, y, 0xFFFFC428, false);
        roundRect(ctx, sx + 6, y + 12, sw - 12, 16, 0xFF3A2E10);
        ctx.drawText(textRenderer, o.getString("mission"), sx + 10, y + 16, 0xFFFFE6A8, false);
        ctx.drawText(textRenderer, "Что делать:", sx + 8, y + 34, 0xFFFFFFFF, false);
        int h = wrap(ctx, o.getString("hint"), sx + 8, y + 45, sw - 16, 0xFFD0CBE0);
        if (o.contains("extra")) wrap(ctx, o.getString("extra"), sx + 8, y + 49 + h, sw - 16, 0xFF6EC8FF);
        wrap(ctx, "Золотой столб света показывает, куда идти. Карта в телефоне тоже.", sx + 8, y + 90 + h, sw - 16, 0xFF8F89A3);
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
        String[] hours = {"Восход 6:00", "Закат 18:30", "Ночью — неон и облавы"};
        for (int i = 0; i < hours.length; i++) ctx.drawText(textRenderer, hours[i], sx + 8, y + 68 + i * 11, 0xFFD0CBE0, false);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        scroll = Math.max(0, scroll - (int) (v * 12));
        return true;
    }

    @Override
    protected void background(DrawContext ctx) {
        ctx.fillGradient(0, 0, width, height, 0x40050210, 0x90050210);
    }
}
