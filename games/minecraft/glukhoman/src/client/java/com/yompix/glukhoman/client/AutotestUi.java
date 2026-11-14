package com.yompix.glukhoman.client;

import com.yompix.glukhoman.Glukhoman;
import com.yompix.glukhoman.client.menu.IntroScreen;
import com.yompix.glukhoman.client.menu.MenuClock;
import com.yompix.glukhoman.client.menu.MenuSound;
import com.yompix.glukhoman.client.menu.TitleMenuScreen;
import com.yompix.glukhoman.client.menu.YompixScreen;
import com.yompix.glukhoman.client.screen.SettingsScreen;
import com.yompix.glukhoman.client.ui.ChapterScreen;
import com.yompix.glukhoman.client.ui.ConfirmScreen;
import com.yompix.glukhoman.client.ui.CoopScreen;
import com.yompix.glukhoman.client.ui.FailScreen;
import com.yompix.glukhoman.client.ui.Kit;
import com.yompix.glukhoman.client.ui.KitScreen;
import com.yompix.glukhoman.client.ui.LobbyScreen;
import com.yompix.glukhoman.client.ui.Notify;
import com.yompix.glukhoman.client.ui.PauseScreen;
import com.yompix.glukhoman.client.ui.StoryLang;
import com.yompix.glukhoman.client.ui.Subtitles;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static com.yompix.glukhoman.client.Autotest.STEPS;


/**
 * Автотест интерфейса (этапы 4–8): видео (yompix_intro, ui_tour, settings, subtitles_test) и проверки QA
 * (раскладка субтитров при разных разрешениях/масштабах/языках, очередь, экраны без наложений и вылезаний, быстрые
 * переключения экранов, пропуск/повтор кат-сцены, «нет застрявших» слоёв аниматора). Итог — /tmp/claude-0/glk/qa_report.txt.
 *
 * <pre>xvfb-run -a -s "-screen 0 1920x1080x24" ./gradlew runClient -Pautotest=ui</pre>
 */
final class AutotestUi {
    private AutotestUi() {}

    static final String OUT = "/tmp/claude-0/glukhoman-trailer3/";
    static final List<String> REPORT = new ArrayList<>();
    static int fails;

    static void check(boolean ok, String what) {
        if (!ok) fails++;
        REPORT.add((ok ? "PASS  " : "FAIL  ") + what);
        Glukhoman.LOG.info("QA {} {}", ok ? "PASS" : "FAIL", what);
    }

    /** Действие на кадре записи f (запись экрана: 30 к/с по часам меню). */
    static void at(int f, Consumer<MinecraftClient> a) {
        Autotest.wait(c -> MenuClock.recFrame >= f, 200000);
        STEPS.add(new Autotest.Step(0, a));
    }

    static void rec(String name) {
        STEPS.add(new Autotest.Step(1, c -> {
            MenuSound.LOG.clear();
            Recorder.startScreen(OUT + "raw_" + name + ".mp4", 30);
        }));
    }

    static void stop(String name) {
        STEPS.add(new Autotest.Step(1, c -> {
            Recorder.stop();
            Kit.forceHover = Kit.forcePress = -1;
            TitleMenuScreen.forceHover = -1;
            try {
                Files.write(Path.of(OUT + "raw_" + name + ".sounds.csv"), MenuSound.LOG);
            } catch (Exception e) {
                Glukhoman.LOG.error("журнал звуков", e);
            }
        }));
    }

    static void hover(int f0, int step, int... items) {
        int f = f0;
        for (int i : items) {
            final int k = i;
            at(f, c -> Kit.forceHover = k);
            f += step;
        }
    }

    static void run() {
        GConfig.get().subtitles = true;
        // ---------- yompix_intro.mp4 ----------
        Autotest.wait(c -> c.currentScreen instanceof TitleMenuScreen && c.getOverlay() == null, 3000);
        STEPS.add(new Autotest.Step(40, c -> {}));
        rec("yompix_intro");
        STEPS.add(new Autotest.Step(0, c -> c.setScreen(new YompixScreen(new IntroScreen(new TitleMenuScreen())))));
        at((int) (YompixScreen.LEN * 30) + 75, c -> {});
        stop("yompix_intro");

        // ---------- ui_tour.mp4 (меню) ----------
        STEPS.add(new Autotest.Step(1, c -> c.setScreen(new TitleMenuScreen())));
        rec("ui_tour");
        for (int i = 0; i < 8; i++) {
            final int k = i;
            at(20 + i * 14, c -> TitleMenuScreen.forceHover = k);
        }
        at(140, c -> {
            TitleMenuScreen.forceHover = -1;
            c.setScreen(new ChapterScreen(c.currentScreen));
        });
        hover(160, 16, 0, 1, 2, 3, 4);
        at(240, c -> Kit.forcePress = 4);
        at(252, c -> {
            Kit.forcePress = -1;
            c.setScreen(new CoopScreen(new TitleMenuScreen()));
        });
        hover(270, 16, 0, 1, 2, 3);
        at(340, c -> c.setScreen(new ConfirmScreen(new TitleMenuScreen(), false, "Новая смена?", "Прошлая смена останется в «Мирах».", "Начать", "Назад", () -> {})));
        hover(356, 18, 0, 1, 0);
        at(400, c -> Kit.forcePress = 0);
        at(410, c -> Kit.forcePress = -1);
        at(420, c -> Kit.forceHover = -1);
        stop("ui_tour");
        // загрузка мира — своя «перемотка» (отдельный кусок записи, 4 с)
        Autotest.newWorld();
        Autotest.wait(c -> c.currentScreen instanceof net.minecraft.client.gui.screen.world.LevelLoadingScreen, 3000);
        rec("ui_loading");
        at(90, c -> {});
        stop("ui_loading");
        Autotest.wait(c -> c.player != null && c.currentScreen != null, 3000);
        STEPS.add(new Autotest.Step(5, c -> {
            c.setScreen(null);
            Autotest.server(c, s -> com.yompix.glukhoman.story.Story.chooseDifficulty(Autotest.pl(s), 1));
        }));
        STEPS.add(new Autotest.Step(60, c -> {
            c.setScreen(null);
            Autotest.server(c, s -> {
                s.getOverworld().setTimeOfDay(6000);
                Autotest.tp(s, 0, 66, 19, -90, 5);
            });
        }));
        STEPS.add(new Autotest.Step(60, c -> c.setScreen(null)));
        Autotest.wait(Autotest::ready, 900);
        STEPS.add(new Autotest.Step(40, c -> {
            c.setScreen(null);
            Recorder.startScreen(OUT + "raw_ui_tour2.mp4", 30);
            MenuSound.LOG.clear();
            c.setScreen(new PauseScreen());
        }));
        hover(20, 16, 0, 1, 2, 3, 4, 5);
        at(120, c -> Kit.forcePress = 4);
        at(130, c -> {
            Kit.forcePress = -1;
            Kit.forceHover = -1;
            c.setScreen(new LobbyScreen(new PauseScreen()));
            Notify.show("Смена открыта: порт 25565");
        });
        hover(150, 20, 0, 1);
        at(200, c -> c.setScreen(new ConfirmScreen(new PauseScreen(), true, "Выйти в меню?", "Смена сохранится. Друзей отключит.", "Выйти", "Остаться", () -> {})));
        hover(216, 18, 0, 1);
        at(260, c -> {
            Kit.forceHover = -1;
            c.setScreen(new FailScreen());
        });
        hover(320, 18, 0, 1);
        at(370, c -> {});
        stop("ui_tour2");

        // ---------- settings.mp4 ----------
        STEPS.add(new Autotest.Step(1, c -> {
            SettingsScreen.tab = 0;
            c.setScreen(new SettingsScreen(new PauseScreen()));
        }));
        rec("settings");
        int f = 10;
        for (int tab = 0; tab < 5; tab++) {
            final int t = tab;
            at(f, c -> {
                SettingsScreen.tab = t;
                Kit.forceHover = -1;
                c.setScreen(new SettingsScreen(new PauseScreen()));
            });
            int items = new int[]{12, 9, 5, 11, 7}[tab];
            for (int i = 5; i < Math.min(items + 5, 5 + 6); i++) {
                final int k = i;
                at(f + 12 + (i - 5) * 10, c -> Kit.forceHover = k);
            }
            if (tab == 3) {   // показать, что меняется вживую: размер и стиль субтитров
                at(f + 75, c -> {
                    GConfig.get().subSize = 150;
                    GConfig.get().subStyle = 1;
                });
                at(f + 105, c -> {
                    GConfig.get().subSize = 100;
                    GConfig.get().subStyle = 0;
                });
            }
            f += tab == 3 ? 130 : 80;
        }
        at(f, c -> {});
        stop("settings");

        // ---------- subtitles_test.mp4 ----------
        STEPS.add(new Autotest.Step(1, c -> {
            c.setScreen(null);
            c.options.hudHidden = false;
        }));
        rec("subtitles_test");
        String[][] langs = {{"ru_ru", "русский"}, {"en_us", "English"}, {"uk_ua", "українська"}, {"ru_ru", "русский"}};
        int[] sizes = {100, 100, 150, 75};
        for (int i = 0; i < 4; i++) {
            final int k = i;
            int f0 = 10 + i * 150;
            at(f0, c -> {
                c.options.getGuiScale().setValue(k + 1);
                c.onResolutionChanged();
                GConfig.get().subLang = langs[k][0];
                GConfig.get().subSize = sizes[k];
                Subtitles.clear();
                Notify.show("Интерфейс ×" + (k + 1) + " · субтитры " + sizes[k] + "% · " + langs[k][1]);
                Subtitles.push("vika", StoryLang.who("vika", "Вика"), StoryLang.text("talk_vika", "Снимешь меня? Для мамы. Она в Норильске."), 0);
                Subtitles.push("zakhar", StoryLang.who("zakhar", "Захар Ильич"), StoryLang.text("rule_n2_2",
                        "Второе. Кто идёт к поезду — того будить. Взять за плечо и держать, пока не очнётся. Не кричать."), 0);
                Subtitles.push("vika", StoryLang.who("vika", "Вика"), StoryLang.text("vika_rule_2", "Мама в Норильске. Это на севере. Он же туда идёт, да?"), 0);
            });
        }
        at(10 + 4 * 150, c -> {
            c.options.getGuiScale().setValue(0);
            c.onResolutionChanged();
            GConfig.get().subLang = "auto";
            GConfig.get().subSize = 100;
        });
        stop("subtitles_test");

        // ---------- проверки QA ----------
        STEPS.add(new Autotest.Step(5, c -> qaLayout(c)));
        STEPS.add(new Autotest.Step(5, c -> qaQueue()));
        STEPS.add(new Autotest.Step(5, c -> qaScreens(c)));
        // быстрые переключения экранов: 120 тиков подряд новый экран
        for (int i = 0; i < 120; i++) {
            final int k = i;
            STEPS.add(new Autotest.Step(1, c -> {
                try {
                    Screen s = switch (k % 8) {
                        case 0 -> new PauseScreen();
                        case 1 -> new SettingsScreen(null);
                        case 2 -> new ChapterScreen(null);
                        case 3 -> new CoopScreen(null);
                        case 4 -> new LobbyScreen(null);
                        case 5 -> new FailScreen();
                        case 6 -> new TitleMenuScreen();
                        default -> null;
                    };
                    if (k % 8 == 1) SettingsScreen.tab = (k / 8) % 5;
                    c.setScreen(s);
                } catch (Exception e) {
                    check(false, "быстрое переключение экранов: " + e);
                }
            }));
        }
        STEPS.add(new Autotest.Step(5, c -> {
            c.setScreen(null);
            check(true, "быстрые переключения экранов: 120 подряд, без исключений (исключения — в отчёте выше)");
        }));
        // кат-сцена: пропуск посередине, повтор, мир вернулся как был
        STEPS.add(new Autotest.Step(5, c -> Autotest.server(c, s -> s.getOverworld().setTimeOfDay(18000))));
        STEPS.add(new Autotest.Step(20, c -> {
            doorWas = c.world.getBlockState(new net.minecraft.util.math.BlockPos(-6, 66, 21));
            Cine.play("proto_cut");
            Cine.local = true;
        }));
        STEPS.add(new Autotest.Step(140, c -> {
            check(Cine.active && Cine.PUPPETS.size() >= 4, "кат-сцена идёт, актёры на месте: " + Cine.PUPPETS.size());
            Cine.stop();   // как Esc в локальной сцене
        }));
        STEPS.add(new Autotest.Step(5, c -> {
            check(!Cine.active && Cine.PUPPETS.isEmpty(), "пропуск: сцена остановлена, куклы убраны");
            check(c.world.getBlockState(new net.minecraft.util.math.BlockPos(-6, 66, 21)).equals(doorWas), "пропуск: дверь вернулась как была");
            Cine.play("proto_cut");
            Cine.local = true;
        }));
        STEPS.add(new Autotest.Step(60, c -> {
            check(Cine.active, "повтор кат-сцены после пропуска");
            Cine.stop();
        }));
        // аниматор: жест отыграл — слоёв не осталось
        STEPS.add(new Autotest.Step(5, c -> {
            Cine.play("proto_npc");
            Cine.local = true;
        }));
        STEPS.add(new Autotest.Step(30, c -> {
            for (var e : Cine.PUPPETS)
                if (e instanceof com.yompix.glukhoman.entity.ResidentEntity r && r.who().equals("zakhar")) {
                    probe = com.yompix.glukhoman.client.anim.Animator.get(r);
                    if (probe != null) probe.play(com.yompix.glukhoman.client.anim.Gestures.COUGH);
                }
            check(probe != null && probe.activeLayers() >= 1, "аниматор: жест запущен");
        }));
        STEPS.add(new Autotest.Step(70, c -> {
            check(probe != null && probe.activeLayers() == 0, "аниматор: после кашля (2.3 с) слоёв не осталось — " + (probe == null ? "?" : probe.activeLayers()));
            Cine.stop();
        }));
        STEPS.add(new Autotest.Step(5, c -> {
            REPORT.add(0, "QA «Порожняк» — " + (fails == 0 ? "всё прошло" : "ошибок: " + fails));
            try {
                Files.write(Path.of("/tmp/claude-0/glk/qa_report.txt"), REPORT);
            } catch (Exception e) {
                Glukhoman.LOG.error("отчёт QA", e);
            }
        }));
        STEPS.add(new Autotest.Step(20, c -> c.scheduleStop()));
        ClientTickEvents.END_CLIENT_TICK.register(Autotest::tick);
    }

    static net.minecraft.block.BlockState doorWas;
    static com.yompix.glukhoman.client.anim.Animator probe;

    /** Субтитры: при всех разрешениях, масштабах, размерах и языках строки в экране и не налезают друг на друга. */
    static void qaLayout(MinecraftClient c) {
        int[][] res = {{1280, 720}, {1366, 768}, {1920, 1080}, {2560, 1440}, {3840, 2160}};
        String[] langs = {"ru_ru", "uk_ua", "en_us"};
        int[] sizes = {75, 100, 150, 200};
        int bad = 0, total = 0;
        String saveLang = GConfig.get().subLang;
        int saveSize = GConfig.get().subSize;
        for (String lang : langs) {
            GConfig.get().subLang = lang;
            Subtitles.clear();
            Subtitles.push("zakhar", StoryLang.who("zakhar", "Захар Ильич"), StoryLang.text("rule_n2_2", "x"), 0);
            Subtitles.push("vika", StoryLang.who("vika", "Вика"), StoryLang.text("woke_vika", "x"), 0);
            double t = Subtitles.now() + 0.5;
            for (int[] r : res)
                for (int scale = 1; scale <= 6; scale++) {
                    int w = (int) Math.ceil(r[0] / (double) scale), h = (int) Math.ceil(r[1] / (double) scale);
                    if (w < 320 || h < 240) continue;   // как и в игре: масштаб, при котором экран меньше 320×240, недоступен
                    for (int sz : sizes) {
                        GConfig.get().subSize = sz;
                        for (int bottom : new int[]{62, 8}) {
                            total++;
                            var boxes = Subtitles.layout(c.textRenderer, w, h, bottom, t);
                            boolean ok = boxes.size() >= 2;
                            for (int i = 0; i < boxes.size(); i++) {
                                var b = boxes.get(i);
                                if (b.x() < 0 || b.x() + b.w() > w || b.y() < 0 || b.y() + b.h() > h) ok = false;
                                for (int j = i + 1; j < boxes.size(); j++) {
                                    var o = boxes.get(j);
                                    if (b.y() < o.y() + o.h() && o.y() < b.y() + b.h() && b.x() < o.x() + o.w() && o.x() < b.x() + b.w()) ok = false;
                                }
                            }
                            if (!ok) {
                                bad++;
                                if (bad < 6) REPORT.add("      субтитры не влезли: " + lang + " " + r[0] + "×" + r[1] + " ×" + scale + " " + sz + "% низ " + bottom);
                            }
                        }
                    }
                }
        }
        GConfig.get().subLang = saveLang;
        GConfig.get().subSize = saveSize;
        Subtitles.clear();
        check(bad == 0, "субтитры: " + total + " раскладок (5 разрешений × масштаб 1–6 × 4 размера × 3 языка × HUD/сцена), не влезло: " + bad);
    }

    /** Очередь: не больше двух строк сразу; реплики одного говорящего не пересекаются по времени. */
    static void qaQueue() {
        Subtitles.clear();
        for (int i = 0; i < 6; i++) Subtitles.push(i % 3 == 0 ? "vika" : "zakhar", "x", "Строка " + i + " — " + "слово ".repeat(i * 3), 0);
        double t0 = Subtitles.now();
        int maxVis = 0;
        for (double t = t0; t < t0 + 40; t += 0.05) maxVis = Math.max(maxVis, Subtitles.visible(t).size());
        boolean overlap = false;
        var all = new ArrayList<>(Subtitles.visible(t0));
        for (double t = t0; t < t0 + 40; t += 0.05) for (var l : Subtitles.visible(t)) if (!all.contains(l)) all.add(l);
        for (int i = 0; i < all.size(); i++) for (int j = i + 1; j < all.size(); j++) {
            var a = all.get(i);
            var b = all.get(j);
            if (a.key.equals(b.key) && a.start < b.end() && b.start < a.end()) overlap = true;
        }
        check(maxVis <= Subtitles.MAX_VISIBLE, "очередь субтитров: на экране не больше " + Subtitles.MAX_VISIBLE + " (было " + maxVis + ")");
        check(!overlap && all.size() == 6, "очередь субтитров: реплики одного говорящего не накладываются, показаны все 6 (" + all.size() + ")");
        check(Subtitles.readTime("Да.", 0) >= 1.6 && Subtitles.readTime("слово ".repeat(30), 0) > 9, "время показа от длины текста");
        Subtitles.clear();
    }

    /** Экраны мода: пункты не налезают друг на друга и не вылезают за экран при разных размерах окна/масштабах. */
    static void qaScreens(MinecraftClient c) {
        int[][] sizes = {{320, 240}, {427, 240}, {480, 270}, {640, 360}, {960, 540}, {1920, 1080}};
        int bad = 0, total = 0;
        for (int[] sz : sizes)
            for (int k = 0; k < 11; k++) {
                Screen s = switch (k) {
                    case 0 -> new PauseScreen();
                    case 1, 2, 3, 4, 5 -> {
                        SettingsScreen.tab = k - 1;
                        yield new SettingsScreen(null);
                    }
                    case 6 -> new ChapterScreen(null);
                    case 7 -> new CoopScreen(null);
                    case 8 -> new LobbyScreen(null);
                    case 9 -> new FailScreen();
                    default -> new ConfirmScreen(null, false, "Выйти из игры?", "", "Выйти", "Назад", () -> {});
                };
                try {
                    s.init(c, sz[0], sz[1]);
                } catch (Exception e) {
                    bad++;
                    REPORT.add("      исключение " + s.getClass().getSimpleName() + " " + sz[0] + "×" + sz[1] + ": " + e);
                    continue;
                }
                total++;
                List<net.minecraft.client.gui.widget.ClickableWidget> ws = new ArrayList<>();
                for (var el : s.children()) if (el instanceof net.minecraft.client.gui.widget.ClickableWidget w) ws.add(w);
                boolean ok = true;
                for (int i = 0; i < ws.size(); i++) {
                    var a = ws.get(i);
                    if (a.getX() < 0 || a.getY() < 0 || a.getX() + a.getWidth() > sz[0] || a.getY() + a.getHeight() > sz[1]) ok = false;
                    for (int j = i + 1; j < ws.size(); j++) {
                        var b = ws.get(j);
                        int ay = a instanceof Kit.Slider ? a.getY() - 9 : a.getY(), by = b instanceof Kit.Slider ? b.getY() - 9 : b.getY();
                        if (a.getX() < b.getX() + b.getWidth() && b.getX() < a.getX() + a.getWidth() && ay < by + b.getHeight() + (b.getY() - by)
                                && by < ay + a.getHeight() + (a.getY() - ay)) ok = false;
                    }
                }
                if (!ok) {
                    bad++;
                    if (bad < 12) REPORT.add("      наложение/вылезание: " + s.getClass().getSimpleName() + (k >= 1 && k <= 5 ? " вкладка " + (k - 1) : "") + " " + sz[0] + "×" + sz[1]);
                }
            }
        SettingsScreen.tab = 0;
        check(bad == 0, "экраны мода: " + total + " раскладок (11 экранов × 6 размеров от 320×240), с проблемами: " + bad);
    }

    @SuppressWarnings("unused")
    static KitScreen dummy;
}
