package com.yompix.novograd.client;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.Novograd;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.story.Story;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Только разработка: -Dnovograd.autotest=uiaudit — проверка «ничего не вылезает»: все экраны мода (меню, пауза, настройки
 * по вкладкам, телефон и каждое приложение, карта, инвентарь, магазины, сундук, смерть, чат с длинными сообщениями,
 * разговор, субтитры кат-сцены, уведомления, цель миссии) при каждом масштабе интерфейса из -Dnovograd.uiscales
 * (по умолчанию «0,2»; 0 — авто). Снимки — run/screenshots/uiaudit/&lt;-Dnovograd.uitag&gt;/имя_sМАСШТАБ.jpg (не шире 1280).
 */
public final class UiAudit {
    private UiAudit() {}

    private record Step(int delay, Consumer<MinecraftClient> action) {}

    private static final List<Step> PRE = new ArrayList<>(), STEPS = new ArrayList<>();
    private static int preIndex = -1, preTimer, index = -1, timer;
    private static final String P = "Dev";
    private static int[] scales;
    private static String tag;

    public static void init() {
        String mode = System.getProperty("novograd.autotest");
        if (mode == null || !mode.equals("uiaudit")) return;
        com.yompix.novograd.client.story.YompixSplash.markShown();
        tag = System.getProperty("novograd.uitag", "ui");
        String[] sc = System.getProperty("novograd.uiscales", "0,2").split(",");
        scales = new int[sc.length];
        for (int i = 0; i < sc.length; i++) scales[i] = Integer.parseInt(sc[i].trim());
        // ---- до входа в мир: главное меню и настройки (все вкладки)
        pre(40, c -> { if (!(c.currentScreen instanceof com.yompix.novograd.client.menu.LifeMenuScreen)) c.setScreen(new com.yompix.novograd.client.menu.LifeMenuScreen()); });
        for (int s : scales) {
            pre(2, c -> scale(c, s));
            pre(30, c -> shot(c, "menu_main", s));
        }
        for (int t = 0; t < 4; t++) {
            final int tt = t;
            for (int s : scales) {
                pre(2, c -> {
                    scale(c, s);
                    c.setScreen(new com.yompix.novograd.client.menu.SettingsScreen(new com.yompix.novograd.client.menu.LifeMenuScreen()));
                    for (int k = 0; k < tt; k++) c.currentScreen.keyPressed(GLFW.GLFW_KEY_TAB, 0, 0);
                });
                pre(20, c -> shot(c, "menu_settings_tab" + tt, s));
            }
        }
        pre(5, c -> scale(c, 0));
        pre(5, c -> c.createIntegratedServerLoader().start("Island", () -> c.setScreen(new com.yompix.novograd.client.menu.LifeMenuScreen())));
        // ---- в мире
        run(100, c -> { if (c.player != null && c.player.isDead()) c.player.requestRespawn(); });
        for (int i = 0; i < 4; i++) run(10, c -> { if (c.currentScreen != null) c.currentScreen.keyPressed(GLFW.GLFW_KEY_SPACE, 0, 0); });
        run(5, c -> { if (c.currentScreen != null) c.setScreen(null); });
        cmd(5, "gamemode survival " + P);
        cmd(5, "effect give " + P + " minecraft:resistance 99999 4 true");
        cmd(5, "effect give " + P + " minecraft:regeneration 99999 4 true");
        cmd(5, "time set 6000");
        cmd(5, "weather clear");
        cmd(5, "gamerule doDaylightCycle false");
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            if (com.yompix.novograd.gta.Prison.inside(p)) com.yompix.novograd.gta.Prison.release(p, "", ""); // старый мир: игрок мог остаться в колонии
            var d = Life.get(p);
            d.storyStarted = true;
            d.storyDone = true;
            d.money = 1_284_350;
            d.dirty = 93_400;
            Life.sync(p);
            var inv = p.getInventory();
            inv.setStack(0, new ItemStack(ModContent.PISTOL));
            inv.setStack(1, new ItemStack(ModContent.PHONE));
            inv.setStack(2, new ItemStack(ModContent.BURGER, 64));
            inv.setStack(9, new ItemStack(ModContent.AMMO, 64));
            p.playerScreenHandler.sendContentUpdates();
            var w = p.getServerWorld();
            var at = p.getBlockPos().offset(p.getHorizontalFacing(), 2);
            w.setBlockState(at, net.minecraft.block.Blocks.CHEST.getDefaultState());
            if (w.getBlockEntity(at) instanceof net.minecraft.block.entity.ChestBlockEntity ch) {
                ch.setStack(0, new ItemStack(ModContent.SMG));
                ch.setStack(13, new ItemStack(ModContent.PIZZA, 2));
            }
            chestPos = at;
        });
        run(120, c -> { if (c.currentScreen != null) c.setScreen(null); });
        // экраны: имя → как открыть
        screen("pause", c -> new com.yompix.novograd.client.menu.LifePauseScreen());
        for (int t = 0; t < 4; t++) {
            final int tt = t;
            screen("settings_tab" + t, c -> {
                var s = new com.yompix.novograd.client.menu.SettingsScreen(null);
                c.setScreen(s);
                for (int k = 0; k < tt; k++) s.keyPressed(GLFW.GLFW_KEY_TAB, 0, 0);
                return s;
            });
        }
        screen("phone_home", c -> new com.yompix.novograd.client.screen.PhoneScreen(null));
        for (String app : new String[]{"bank", "jobs", "messages", "map", "casino", "music", "weather", "story", "trophy", "home", "taxi", "friends"})
            screen("phone_app_" + app, c -> new com.yompix.novograd.client.screen.PhoneScreen(app));
        screen("map", c -> new com.yompix.novograd.client.screen.MapScreen());
        screen("inventory", c -> new net.minecraft.client.gui.screen.ingame.InventoryScreen(c.player));
        screen("shop", c -> new com.yompix.novograd.client.screen.ShopScreen(true));
        screen("shop_guns", c -> new com.yompix.novograd.client.screen.ShopScreen(true, true));
        screen("atm", c -> new com.yompix.novograd.client.screen.AtmScreen(1_284_350, 93_400));
        // 0.9.9 «Город»: приложения и игры
        screen("app_night", c -> new com.yompix.novograd.client.city.CityApps.Night());
        screen("app_dating", c -> new com.yompix.novograd.client.city.CityApps.Dating());
        screen("app_sport", c -> new com.yompix.novograd.client.city.CityApps.Sport());
        screen("app_social", c -> new com.yompix.novograd.client.city.CityApps.Social());
        screen("app_cozy", c -> new com.yompix.novograd.client.city.CityApps.Home());
        for (String g : new String[]{"billiards", "bowling", "hoops", "golf"}) screen("game_" + g, c -> {
            var n = new net.minecraft.nbt.NbtCompound();
            n.putString("game", g); n.putInt("bet", 100); n.putInt("foe", 7); n.putString("foeName", "Кий-Саныч"); n.putLong("seed", 5);
            com.yompix.novograd.client.games.MiniGames.open(n);
            return c.currentScreen;
        });
        screen("atm_physical", c -> new com.yompix.novograd.client.screen.AtmScreen(1_284_350, 93_400).physical(true)); // 0.9.8: у банкомата, мир виден
        screen("loan", c -> { // 0.9.8: кредит — предложение
            var n = new net.minecraft.nbt.NbtCompound();
            n.putInt("score", 684); n.putString("band", "хороший"); n.putInt("income", 1240); n.putInt("limit", 18_500);
            n.putDouble("rateMonth", 4.9); n.putDouble("periodRate", 0.0049); n.putInt("period", 3); n.putLong("day", 12);
            com.yompix.novograd.client.screen.LoanScreen.state = n;
            return new com.yompix.novograd.client.screen.LoanScreen();
        });
        screen("finance", c -> new com.yompix.novograd.client.screen.FinanceScreen());
        screen("news", c -> new com.yompix.novograd.client.screen.NewsScreen());
        screen("collections", c -> new com.yompix.novograd.client.screen.CollectionsScreen());
        screen("contacts", c -> new com.yompix.novograd.client.story.ContactsScreen());
        screen("realestate", c -> new com.yompix.novograd.client.screen.RealEstateScreen());
        screen("guard", c -> new com.yompix.novograd.client.screen.BodyguardScreen());
        screen("dark", c -> new com.yompix.novograd.client.screen.DarkJobsScreen());
        screen("progress", c -> new com.yompix.novograd.client.city.ProgressScreen()); // 0.9
        screen("empire", c -> new com.yompix.novograd.client.city.EmpireScreen());
        screen("grid", c -> new com.yompix.novograd.client.city.GridScreen());
        screen("drive", c -> new com.yompix.novograd.client.screen.DriveScreen());
        screen("skills", c -> new com.yompix.novograd.client.screen.SkillsScreen());
        screen("outdoor", c -> new com.yompix.novograd.client.screen.OutdoorScreen(""));
        screen("style", c -> new com.yompix.novograd.client.style.StyleAppScreen(false));
        // 0.9.6: редактор внешности — все вкладки (и первый вход)
        for (int t = 0; t < com.yompix.novograd.client.style.CreatorScreen.TABS.length; t++) {
            final int tt = t;
            screen("creator_tab" + t, c -> {
                var s = new com.yompix.novograd.client.style.CreatorScreen(com.yompix.novograd.client.style.CreatorScreen.Mode.PHONE);
                s.autotest(tt, null);
                return s;
            });
        }
        screen("creator_first", c -> new com.yompix.novograd.client.style.CreatorScreen(com.yompix.novograd.client.style.CreatorScreen.Mode.FIRST));
        screen("creator_shop", c -> new com.yompix.novograd.client.style.CreatorScreen(com.yompix.novograd.client.style.CreatorScreen.Mode.CHEAP));
        screen("guide", c -> new com.yompix.novograd.client.guide.GuideScreen());
        screen("mycars", c -> new com.yompix.novograd.client.city.MyCarsScreen());
        screen("stash", c -> new com.yompix.novograd.client.city.StashScreen());
        screen("age_gate", c -> new com.yompix.novograd.client.menu.AgeGateScreen(null));
        screen("age_gate_typed", c -> new com.yompix.novograd.client.menu.AgeGateScreen(null).typed("2403199"));
        screen("casino_lobby", c -> new com.yompix.novograd.client.screen.casino.CasinoLobbyScreen());
        screen("online_casino", c -> new com.yompix.novograd.client.screen.OnlineCasinoScreen());
        screen("gun_tune", c -> new com.yompix.novograd.client.screen.GunTuneScreen());
        screen("gym", c -> new com.yompix.novograd.client.style.GymScreen());
        screen("weapon_wheel", c -> new com.yompix.novograd.client.screen.WeaponWheelScreen());
        screen("dialog", c -> dialog());
        screen("death", c -> new com.yompix.novograd.client.screen.GtaDeathScreen(Text.literal("Dev был застрелен полицейским Сержантом Петренко Олегом Викторовичем у входа в казино «Золотая корона»"), false));
        // сундук (контейнер)
        for (int s : scales) {
            run(2, c -> { c.setScreen(null); scale(c, s); });
            run(4, c -> {
                if (chestPos != null && c.interactionManager != null)
                    c.interactionManager.interactBlock(c.player, net.minecraft.util.Hand.MAIN_HAND,
                            new net.minecraft.util.hit.BlockHitResult(net.minecraft.util.math.Vec3d.ofCenter(chestPos), net.minecraft.util.math.Direction.UP, chestPos, false));
            });
            run(14, c -> shot(c, "chest", s));
        }
        run(2, c -> c.setScreen(null));
        // чат: длинные сообщения мода и игрока, открытый чат
        server(2, s -> {
            var p = pl(s);
            p.sendMessage(Text.literal("Новоград: в «Неоне» сегодня вечеринка — вход свободный до полуночи, приходи с друзьями и не забудь маску, будет весело и громко!").formatted(Formatting.AQUA), false);
            p.sendMessage(Text.literal("Полиция объявила план «Перехват»: все выезды с острова перекрыты, вертолёт над портом, награда за информацию $5000.").formatted(Formatting.RED), false);
            p.sendMessage(Text.literal("Оченьдлинноесловобезпробеловкотороедолжнопереноситьсяанеуходитьзакрайэкранаоченьдлинноесловобезпробелов").formatted(Formatting.YELLOW), false);
        });
        run(5, c -> c.player.networkHandler.sendChatMessage("Где встречаемся? Я у казино, жду вас всех, приезжайте быстрее, пока полиция не приехала, тут уже три звезды!"));
        for (int s : scales) {
            run(2, c -> { c.setScreen(null); scale(c, s); });
            run(3, c -> c.setScreen(new net.minecraft.client.gui.screen.ChatScreen("")));
            run(8, c -> shot(c, "chat_open", s));
        }
        run(2, c -> c.setScreen(null));
        // HUD: цель миссии, звёзды, деньги, уведомление, подсказка, строка над хотбаром
        for (int s : scales) {
            run(2, c -> {
                scale(c, s);
                NovogradClient.wantedStars = 4;
                NovogradClient.wantedSeen = true;
                var o = new net.minecraft.nbt.NbtCompound();
                o.putString("chapter", "Акт IV · Ривьера");
                o.putString("mission", "Последний рейс в Ривьеру через мост, пока не перекрыли выезд");
                o.putString("hint", "Доберись до мастерской Глеба на Портовой улице и забери оттуда бронированный фургон до заката, не привлекая внимания полиции");
                o.putString("where", "Портовая улица, 12, мастерская");
                o.putDouble("x", c.player.getX() + 60);
                o.putDouble("y", c.player.getY());
                o.putDouble("z", c.player.getZ() + 25);
                com.yompix.novograd.client.story.ClientStory.objective = o;
                com.yompix.novograd.client.ui.Quiet.notify("Достижение: Король дороги на скоростной трассе", "Проехать всю кольцевую трассу острова быстрее 2 минут без единой царапины", 0xFFFFC107);
                com.yompix.novograd.client.ui.Quiet.hint("", "Телефон — G. Карта — M. Инвентарь — E. Колесо оружия — Tab. Цель миссии — O. Пауза — Esc.");
            });
            server(1, sv -> pl(sv).sendMessage(Text.literal("Ты слишком далеко от машины — вернись к ней за 30 секунд, иначе миссия будет провалена и придётся начинать заново").formatted(Formatting.RED), true));
            run(14, c -> shot(c, "hud", s));
            run(2, c -> {
                if (c.options != null) KeyHelper.press(c, "key.novograd.objective");
            });
            run(14, c -> shot(c, "hud_objective_o", s));
        }
        // кат-сцена: субтитры (длинная реплика, двое говорящих), полосы кадра
        for (int s : scales) {
            run(2, c -> { c.setScreen(null); scale(c, s); });
            server(2, sv -> {
                var d = Life.get(pl(sv));
                d.storyDone = false;
                d.side = "";
                d.chapter = 3;
                d.mission = 1;
                d.step = 0;
                d.flags.add("started:3:1");
                d.flags.removeIf(f -> f.startsWith("entered:"));
                Story.reenter(pl(sv));
            });
            run(150, c -> shot(c, "cutscene_crow_long", s)); // ≈7 с: длинная реплика Кроу
            run(120, c -> shot(c, "cutscene_sofi", s));
            run(2, c -> { if (c.currentScreen instanceof com.yompix.novograd.client.story.CutsceneScreen cs) cs.keyPressed(GLFW.GLFW_KEY_SPACE, 0, 0); });
            run(30, c -> { if (c.currentScreen != null) c.setScreen(null); });
        }
        run(5, c -> scale(c, 0));
        run(5, c -> Novograd.LOG.info("[uiaudit] готово: {} снимков, ошибок {}", shots, errors));
        run(10, MinecraftClient::scheduleStop);
        ClientTickEvents.END_CLIENT_TICK.register(UiAudit::tick);
    }

    private static int shots, errors;
    private static net.minecraft.util.math.BlockPos chestPos;

    /** Открыть экран при каждом масштабе и снять. */
    private static void screen(String name, Function<MinecraftClient, Screen> open) {
        for (int s : scales) {
            run(2, c -> {
                c.setScreen(null);
                scale(c, s);
                try {
                    Screen sc = open.apply(c);
                    if (c.currentScreen != sc) c.setScreen(sc);
                } catch (Throwable t) {
                    errors++;
                    Novograd.LOG.warn("[uiaudit] {} не открылся: {}", name, t.toString());
                }
            });
            run(16, c -> shot(c, name, s));
        }
        run(2, c -> c.setScreen(null));
    }

    private static Screen dialog() {
        var n = new net.minecraft.nbt.NbtCompound();
        var lines = new net.minecraft.nbt.NbtList();
        var l = new net.minecraft.nbt.NbtCompound();
        l.putString("who", "tema");
        l.putString("name", "Тёма Ворошиловградский");
        l.putInt("color", 0xFF7A3D);
        l.putString("text", "Слушай, если хочешь в этом городе выжить — держись подальше от Портовой после заката. Там свои правила, свои люди и свои счёты, и никто не будет спрашивать, кто ты такой и откуда приехал.");
        l.putString("voice", "");
        lines.add(l);
        n.put("lines", lines);
        var choices = new net.minecraft.nbt.NbtList();
        for (String t : new String[]{"Я сам разберусь, без твоих советов и без твоих людей.", "Кто там главный и где его найти сегодня вечером?", "Поехали вместе."}) {
            var ch = new net.minecraft.nbt.NbtCompound();
            ch.putString("text", t);
            ch.put("reply", new net.minecraft.nbt.NbtList());
            choices.add(ch);
        }
        n.put("choices", choices);
        return new com.yompix.novograd.client.story.DialogScreen(n, true);
    }

    private static void scale(MinecraftClient c, int s) {
        c.options.getGuiScale().setValue(s);
        c.onResolutionChanged();
    }

    private static void shot(MinecraftClient c, String name, int s) {
        try {
            var fb = c.getFramebuffer();
            try (var img = net.minecraft.client.util.ScreenshotRecorder.takeScreenshot(fb)) {
                int w = img.getWidth(), h = img.getHeight();
                int k = Math.max(1, (w + 1279) / 1280);
                int ow = w / k, oh = h / k;
                var bi = new java.awt.image.BufferedImage(ow, oh, java.awt.image.BufferedImage.TYPE_INT_RGB);
                for (int y = 0; y < oh; y++)
                    for (int x = 0; x < ow; x++) {
                        int abgr = img.getColor(x * k, y * k);
                        bi.setRGB(x, y, (abgr & 0xFF) << 16 | (abgr & 0xFF00) | (abgr >> 16 & 0xFF));
                    }
                File dir = new File(c.runDirectory, "screenshots/uiaudit/" + tag);
                dir.mkdirs();
                String sf = s == 0 ? "auto" + (int) c.getWindow().getScaleFactor() : String.valueOf((int) c.getWindow().getScaleFactor());
                javax.imageio.ImageIO.write(bi, "jpg", new File(dir, name + "_s" + sf + ".jpg"));
                shots++;
                Novograd.LOG.info("[uiaudit] {} масштаб {} ({}x{} gui) экран={}", name, sf, c.getWindow().getScaledWidth(), c.getWindow().getScaledHeight(),
                        c.currentScreen == null ? null : c.currentScreen.getClass().getSimpleName());
            }
        } catch (Throwable t) {
            errors++;
            Novograd.LOG.warn("[uiaudit] снимок {} не вышел: {}", name, t.toString());
        }
    }

    /** Нажать клавишу привязки по её id (для «O» — цель миссии). */
    static final class KeyHelper {
        static void press(MinecraftClient c, String id) {
            for (var kb : c.options.allKeys)
                if (kb.getTranslationKey().equals(id)) {
                    net.minecraft.client.option.KeyBinding.onKeyPressed(net.minecraft.client.util.InputUtil.fromTranslationKey(kb.getBoundKeyTranslationKey()));
                    return;
                }
            Novograd.LOG.warn("[uiaudit] нет клавиши {}", id);
        }
    }

    private static ServerPlayerEntity pl(IntegratedServer s) {
        return s.getPlayerManager().getPlayer(P);
    }

    private static void cmd(int delay, String command) {
        server(delay, s -> s.getCommandManager().executeWithPrefix(s.getCommandSource(), command));
    }

    private static void server(int delay, Consumer<IntegratedServer> action) {
        STEPS.add(new Step(delay, c -> {
            IntegratedServer s = c.getServer();
            if (s != null) s.execute(() -> action.accept(s));
        }));
    }

    private static void pre(int delay, Consumer<MinecraftClient> action) {
        PRE.add(new Step(delay, action));
    }

    private static void run(int delay, Consumer<MinecraftClient> action) {
        STEPS.add(new Step(delay, action));
    }

    private static void tick(MinecraftClient c) {
        if (c.player == null && preIndex < PRE.size() && c.getOverlay() == null) {
            if (preIndex < 0) {
                if (!(c.currentScreen instanceof com.yompix.novograd.client.story.YompixSplash) && !(c.currentScreen instanceof com.yompix.novograd.client.menu.LifeMenuScreen)) return;
                preIndex = 0;
                preTimer = PRE.get(0).delay();
            }
            if (--preTimer > 0) return;
            try {
                PRE.get(preIndex).action().accept(c);
            } catch (Throwable t) {
                errors++;
                Novograd.LOG.warn("[uiaudit] шаг меню: {}", t.toString());
            }
            preIndex++;
            if (preIndex < PRE.size()) preTimer = PRE.get(preIndex).delay();
            return;
        }
        if (c.world == null || c.player == null) return;
        if (index < 0) {
            index = 0;
            timer = STEPS.get(0).delay();
        }
        if (index >= STEPS.size()) return;
        if (--timer > 0) return;
        try {
            STEPS.get(index).action().accept(c);
        } catch (Throwable t) {
            errors++;
            Novograd.LOG.warn("[uiaudit] шаг {}: {}", index, t.toString());
        }
        index++;
        if (index < STEPS.size()) timer = STEPS.get(index).delay();
    }
}
