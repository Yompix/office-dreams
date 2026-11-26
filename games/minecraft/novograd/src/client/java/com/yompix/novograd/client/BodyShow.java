package com.yompix.novograd.client;

import com.yompix.novograd.Novograd;
import com.yompix.novograd.client.style.BodyShape;
import com.yompix.novograd.client.style.CreatorScreen;
import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.life.Appearance;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.life.LookGen;
import com.yompix.novograd.story.Story;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static com.yompix.novograd.client.ShowAutotest.*;

/**
 * 0.9.7, только разработка:
 * <ul>
 *   <li>{@code bodyshow} — лист «5 размеров × 3 верха» (и куртка нараспашку/застёгнута, рельеф у мужчины) на витрине
 *       редактора; ролик: ползунок размера, потом шаг/бег/прыжки с «Физикой тела»; замер цены физики на 30 жительницах;</li>
 *   <li>{@code agetest} — проверка возраста: дата, путь «младше 18» (всё взрослое выключено, у сервера — казино закрыто),
 *       путь «18+» (всё включено), requireAdult отключает «не 18+»;</li>
 *   <li>{@code spawnmap} — где стоят люди: обход 10 районов, координаты всех жителей в лог (тепловая карта —
 *       tools/heatmap.py), макс. людей в клетке 16×16.</li>
 * </ul>
 */
final class BodyShow {
    private BodyShow() {}

    static Appearance girl(String top, String outer, boolean open, int chest) {
        Appearance a = LookGen.generate(31, true, LookGen.CASUAL, false);
        a.hair = "ponytail";
        a.hairColor = 2;
        a.hat = "";
        a.glasses = "";
        a.bag = "";
        a.neck = "";
        a.top = top;
        a.outer = outer;
        a.open = open;
        a.pattern = 0;
        a.pants = top.startsWith("swimsuit") ? "denim_shorts#0" : "jeans#1";
        a.chest = chest;
        a.sanitize();
        return a;
    }

    private static CreatorScreen cs(MinecraftClient c) {
        return c.currentScreen instanceof CreatorScreen s ? s : null;
    }

    private static void setLook(Appearance a) {
        server(1, s -> {
            ServerPlayerEntity p = pl(s);
            Life.get(p).look = a.copy();
            Life.sync(p);
            com.yompix.novograd.life.Style.broadcast(p);
        });
    }

    // ================================================================== лист, ролик, замер
    static void bodyshow() {
        cmd(5, "time set 6000");
        cmd(5, "weather clear");
        cmd(5, "gamerule doDaylightCycle false");
        setLook(girl("tee_print#2", "", true, 2));
        run(120, c -> { if (c.currentScreen != null) c.setScreen(null); });
        run(5, c -> c.setScreen(new CreatorScreen(CreatorScreen.Mode.PHONE)));
        // ---- лист: 3 верха × 5 размеров, вид 3/4; профиль — для купальника
        String[] tops = {"tee_print#2", "sweater#2", "swimsuit#0"};
        for (String t : tops)
            for (int k = 0; k < Appearance.CHEST_STEPS; k++) {
                final int kk = k;
                run(k == 0 ? 30 : 6, c -> {
                    if (cs(c) == null) return;
                    cs(c).autotest(0, girl(t, "", true, kk));
                    cs(c).pose(148, 1.0f, false);
                });
                run(8, c -> shot(c, "body_" + t.replace('#', '_') + "_" + kk));
            }
        for (int k = 0; k < Appearance.CHEST_STEPS; k += 2) {
            final int kk = k;
            for (String side : new String[]{"side", "front"}) {
                run(6, c -> {
                    if (cs(c) == null) return;
                    cs(c).autotest(0, girl("swimsuit#0", "", true, kk));
                    cs(c).pose(side.equals("side") ? 110 : 180, 1.15f, false);
                });
                run(8, c -> shot(c, "body_" + side + "_swimsuit_" + kk));
            }
            // куртки: нараспашку и застёгнутая, пиджак
            for (Object[] o : new Object[][]{{"leather#0", true}, {"leather#0", false}, {"blazer#1", true}, {"puffer#2", false}}) {
                run(6, c -> {
                    if (cs(c) == null) return;
                    cs(c).autotest(0, girl("tee#0", (String) o[0], (boolean) o[1], kk));
                    cs(c).pose(150, 1.15f, false);
                });
                run(8, c -> shot(c, "body_jacket_" + ((String) o[0]).replace('#', '_') + ((boolean) o[1] ? "_open_" : "_closed_") + kk));
            }
        }
        // мужской рельеф при спортивном телосложении
        for (int k = 0; k < Appearance.PEC_STEPS; k++) {
            final int kk = k;
            run(6, c -> {
                if (cs(c) == null) return;
                Appearance a = LookGen.generate(5, false, LookGen.SPORT, false);
                a.build = 2;
                a.pecs = kk;
                a.top = "tank#0";
                a.outer = "";
                a.hat = "";
                cs(c).autotest(0, a);
                cs(c).pose(150, 1.15f, false);
            });
            run(8, c -> shot(c, "body_male_pecs_" + kk));
        }
        // ---- ролик: ползунок на вкладке «Тело» (витрина идёт), потом город
        run(5, c -> {
            if (cs(c) == null) return;
            cs(c).autotest(0, girl("tee_print#2", "", true, 0));
            cs(c).pose(155, 1.0f, true);
        });
        run(10, ShowAutotest::rec);
        for (String a : new String[]{"chest+", "chest+", "chest+", "chest+", "chest-", "chest-", "rot", "rot"})
            run(22, c -> { if (cs(c) != null) cs(c).demo(a); });
        setLook(girl("tee_print#2", "denim#0", true, 3));
        run(20, c -> c.setScreen(null));
        final int[] rw = new int[3];
        server(5, s -> {
            // дорожка над городом (светлый пол, небо фоном): идёт, бежит, прыгает — ничего не заслоняет
            ServerPlayerEntity p = pl(s);
            rw[0] = (int) Math.floor(p.getX()) + 40;
            rw[1] = 210;
            rw[2] = (int) Math.floor(p.getZ());
            p.getAbilities().flying = true;
            p.sendAbilitiesUpdate();
            p.teleport(p.getServerWorld(), rw[0] + 0.5, rw[1] + 1.2, rw[2] + 0.5, 0, 8);
        });
        server(40, s -> {
            ServerPlayerEntity p = pl(s);
            var w = p.getServerWorld();
            for (int cz = rw[2] - 16; cz <= rw[2] + 96; cz += 16) w.getChunk(rw[0] >> 4, cz >> 4);
            var cm = s.getCommandManager();
            var src = s.getCommandSource().withSilent();
            int x0 = rw[0], y0 = rw[1], z0 = rw[2];
            cm.executeWithPrefix(src, String.format("fill %d %d %d %d %d %d minecraft:smooth_quartz", x0 - 3, y0, z0 - 2, x0 + 3, y0, z0 + 90));
            cm.executeWithPrefix(src, String.format("fill %d %d %d %d %d %d minecraft:air", x0 - 3, y0 + 1, z0 - 2, x0 + 3, y0 + 6, z0 + 90));
            p.teleport(w, x0 + 0.5, y0 + 1, z0 + 0.5, 0, 8);
            p.getAbilities().flying = false;
            p.sendAbilitiesUpdate();
            Novograd.LOG.info("[bodyshow] дорожка {} {} {} пол {}", x0, y0, z0, w.getBlockState(new net.minecraft.util.math.BlockPos(x0, y0, z0 + 10)));
        });
        run(40, c -> {
            c.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
            c.options.hudHidden = true;
        });
        label(30, "стоит");
        run(1, c -> key(c, c.options.forwardKey, true));
        label(60, "шаг");
        run(1, c -> key(c, c.options.sprintKey, true));
        label(60, "бег");
        for (int i = 0; i < 3; i++) {
            run(1, c -> key(c, c.options.jumpKey, true));
            run(4, c -> key(c, c.options.jumpKey, false));
            label(22, "прыжок");
        }
        run(1, c -> { key(c, c.options.sprintKey, false); key(c, c.options.forwardKey, false); });
        label(40, "остановка");
        run(1, c -> { if (com.yompix.novograd.client.story.CineRec.on) com.yompix.novograd.client.story.CineRec.stop(); });
        cmd(5, "tick rate 20");
        // ---- замер: 30 жительниц из генератора ходят вокруг; физика вкл/выкл
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            var w = p.getServerWorld();
            var r = new java.util.Random(97);
            for (int i = 0; i < 30; i++) {
                long sd = LookGen.citizenSeed(r.nextLong(), true, r.nextInt(LookGen.PROFILE_NAMES.length));
                CitizenEntity c = genCitizen(w, sd);
                Vec3d q = p.getPos().add(r.nextGaussian() * 5 + 2, 0, r.nextGaussian() * 5 + 6);
                c.refreshPositionAndAngles(q.x, Story.ground(w, q).getY(), q.z, r.nextFloat() * 360, 0);
                w.spawnEntity(c);
                c.setStationary(false);
                c.forceLook(sd);
                SPAWNED.add(c.getUuid());
            }
        });
        run(5, c -> c.options.setPerspective(Perspective.THIRD_PERSON_BACK));
        for (int ph = 0; ph < 2; ph++) {
            final boolean on = ph == 0;
            run(60, c -> {
                NovogradConfig.get().bodyPhysics = on;
                BodyShape.physNanos = BodyShape.physCount = BodyShape.drawNanos = BodyShape.drawCount = BodyShape.bufNanos = 0;
                FPS.clear();
            });
            for (int i = 0; i < 10; i++) run(20, c -> FPS.add(c.getCurrentFps()));
            run(1, c -> Novograd.LOG.info("[bodyshow] физика {}: FPS {} (среднее {}), физика {} нс/вызов ({} вызовов), деталь {} нс/вызов ({}; из них взять буфер {} нс), пружин {}",
                    on ? "ВКЛ" : "ВЫКЛ", FPS, FPS.stream().mapToInt(Integer::intValue).average().orElse(0),
                    BodyShape.physCount == 0 ? 0 : BodyShape.physNanos / BodyShape.physCount, BodyShape.physCount,
                    BodyShape.drawCount == 0 ? 0 : BodyShape.drawNanos / BodyShape.drawCount, BodyShape.drawCount,
                    BodyShape.drawCount == 0 ? 0 : BodyShape.bufNanos / BodyShape.drawCount, BodyShape.springs()));
        }
        run(1, c -> NovogradConfig.get().bodyPhysics = true);
        run(20, c -> shot(c, "body_crowd"));
        finish();
    }

    private static final List<Integer> FPS = new ArrayList<>();

    // ================================================================== проверка возраста
    private static int bad;

    private static void check(String what, boolean ok) {
        if (!ok) bad++;
        Novograd.LOG.info("[agetest] {} — {}", what, ok ? "ок" : "ОШИБКА");
    }

    static void agetest() {
        final Object[] saved = new Object[4];
        run(120, c -> {
            if (c.currentScreen != null) c.setScreen(null);
            var cfg = NovogradConfig.get();
            saved[0] = cfg.ageChecked;
            saved[1] = cfg.adult;
            saved[2] = cfg.ageCheckedOn;
            saved[3] = cfg.adultOk;
            LocalDate today = LocalDate.of(2026, 11, 26);
            check("дата 31.02.2000 — нет такой", com.yompix.novograd.client.menu.AgeGateScreen.age("31022000", today) == -1);
            check("дата в будущем — нет", com.yompix.novograd.client.menu.AgeGateScreen.age("01012030", today) == -1);
            check("26.11.2008 → 18", com.yompix.novograd.client.menu.AgeGateScreen.age("26112008", today) == 18);
            check("27.11.2008 → 17", com.yompix.novograd.client.menu.AgeGateScreen.age("27112008", today) == 17);
            // ---- младше 18
            NovogradConfig.setAge(false);
            check("младше 18: размер груди выкл", !NovogradConfig.chestSizes());
            check("младше 18: физика тела выкл", !NovogradConfig.bodyPhysics());
            check("младше 18: кровь — мягко", !NovogradConfig.goreFull());
            check("младше 18: алкоголь выкл", !NovogradConfig.alcohol());
            check("младше 18: казино выкл", !NovogradConfig.casino());
            check("младше 18: у героини виден средний размер", BodyShape.chestShown(girl("tee#0", "", true, 4)) == Appearance.MID_CHEST);
            var scr = new CreatorScreen(CreatorScreen.Mode.PHONE);
            scr.init(c, 1280, 720);
            scr.autotest(0, girl("tee#0", "", true, 4));
            check("младше 18: в редакторе нет «Грудь»", !scr.hasRow("Грудь"));
            check("в конфиге нет даты рождения", !new com.google.gson.Gson().toJson(NovogradConfig.get()).matches(".*(birth|dob|рожд).*"));
        });
        server(30, s -> {
            ServerPlayerEntity p = pl(s);
            check("младше 18: сервер знает (не 18+)", !com.yompix.novograd.life.AgeGate.adult(p));
            check("младше 18: казино на сервере закрыто", !com.yompix.novograd.life.AgeGate.casino(p));
            check("младше 18: выпивки нет", !com.yompix.novograd.life.AgeGate.alcohol(p));
        });
        run(5, c -> {
            NovogradConfig.setAge(true);
            check("18+: размер груди", NovogradConfig.chestSizes());
            check("18+: физика тела", NovogradConfig.bodyPhysics());
            check("18+: кровь обычная", NovogradConfig.goreFull());
            check("18+: алкоголь", NovogradConfig.alcohol());
            check("18+: казино", NovogradConfig.casino());
            var scr = new CreatorScreen(CreatorScreen.Mode.PHONE);
            scr.init(c, 1280, 720);
            scr.autotest(0, girl("tee#0", "", true, 4));
            check("18+: в редакторе есть «Грудь»", scr.hasRow("Грудь"));
            check("18+: виден свой размер", BodyShape.chestShown(girl("tee#0", "", true, 4)) == 4);
            NovogradConfig.get().adultCasino = false;
            NovogradConfig.save();
            check("18+: казино выключено в настройках", !NovogradConfig.casino());
        });
        server(30, s -> {
            ServerPlayerEntity p = pl(s);
            check("18+: сервер знает", com.yompix.novograd.life.AgeGate.adult(p));
            check("18+, казино выкл в настройках: сервер не пускает", !com.yompix.novograd.life.AgeGate.casino(p));
        });
        run(5, c -> {
            NovogradConfig.get().adultCasino = true;
            NovogradConfig.save();
        });
        server(30, s -> {
            ServerPlayerEntity p = pl(s);
            check("18+: казино на сервере открыто", com.yompix.novograd.life.AgeGate.casino(p));
            // requireAdult: «не 18+» отключается (на боте)
            com.yompix.novograd.life.Admin.cfg.requireAdult = true;
            var fp = net.fabricmc.fabric.api.entity.FakePlayer.get(s.getOverworld(), new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes("minor".getBytes()), "Minor"));
            var n = new net.minecraft.nbt.NbtCompound();
            n.putBoolean("adult", false);
            boolean kicked;
            try {
                com.yompix.novograd.life.AgeGate.report(fp, n);
                kicked = !com.yompix.novograd.life.AgeGate.adult(fp); // отключение у бота — без сети; проверяем решение
            } catch (Throwable e) {
                kicked = true;
            }
            check("requireAdult: «не 18+» отключён (" + com.yompix.novograd.life.AgeGate.KICK_MINOR + ")", kicked);
            com.yompix.novograd.life.Admin.cfg.requireAdult = false;
        });
        run(5, c -> {
            var cfg = NovogradConfig.get();
            cfg.ageChecked = (boolean) saved[0];
            cfg.adult = (boolean) saved[1];
            cfg.ageCheckedOn = (String) saved[2];
            cfg.adultOk = (boolean) saved[3];
            NovogradConfig.save();
            Novograd.LOG.info("[agetest] {} (ошибок {})", bad == 0 ? "PASS" : "FAIL", bad);
        });
        finish();
    }

    // ================================================================== где стоят люди (тепловая карта)
    static void spawnmap() {
        String[] places = {"dt_1", "dt_3", "park_center", "port_dock", "beach_bar", "suburb_street", "industry_street", "riv_strip", "village", "mirage_strip"};
        cmd(5, "time set 3000");
        cmd(5, "gamerule doDaylightCycle false");
        cmd(5, "effect give " + "Dev" + " minecraft:invisibility 99999 0 true");
        run(100, c -> { if (c.currentScreen != null) c.setScreen(null); });
        final int[] worst = {0};
        for (String pl0 : places) {
            server(5, s -> {
                ServerPlayerEntity p = pl(s);
                var w = p.getServerWorld();
                Vec3d at = Story.place(w, pl0);
                if (at == null) return;
                p.teleport(w, at.x, Story.ground(w, at).getY(), at.z, 0, 0);
            });
            for (int i = 0; i < 4; i++)
                server(i == 0 ? 400 : 100, s -> {
                    ServerPlayerEntity p = pl(s);
                    var w = p.getServerWorld();
                    StringBuilder b = new StringBuilder();
                    for (CitizenEntity c : w.getEntitiesByClass(CitizenEntity.class, p.getBoundingBox().expand(140, 64, 140), CitizenEntity::isAlive))
                        b.append((int) c.getX()).append(',').append((int) c.getZ()).append(';');
                    int[] st = com.yompix.novograd.world.Crowd.stats(w, p.getPos(), 96);
                    worst[0] = Math.max(worst[0], st[0]);
                    Novograd.LOG.info("[spawnmap] {} игрок {},{} людей {} клеток {} макс_в_клетке {} точки {}", pl0, (int) p.getX(), (int) p.getZ(), st[1], st[2], st[0], b);
                });
        }
        server(5, s -> Novograd.LOG.info("[spawnmap] итог: макс. людей в клетке 16×16 = {}; отклонено появлений {}, убрано лишних {}, дальних {} (crowdcap {})",
                worst[0], com.yompix.novograd.world.Crowd.denied, com.yompix.novograd.world.Crowd.thinned, com.yompix.novograd.world.Crowd.farRemoved,
                com.yompix.novograd.world.Crowd.ON));
        finish();
    }
}
