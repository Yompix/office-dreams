package com.yompix.glukhoman.client;

import com.yompix.glukhoman.GContent;
import com.yompix.glukhoman.client.anim.Gestures;
import com.yompix.glukhoman.entity.TrainEntity;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import static com.yompix.glukhoman.client.Timeline.v;

/**
 * Сюжетные кат-сцены на режиссёре {@link Timeline} (0.5.1). Длины и смысл — как раньше (Scenes.LEN, сервер ждёт
 * столько же), но с постановкой: актёры на аниматоре (взгляд, моргание, жесты), а не статуи.
 * Плюс «Сдача смены» в сюжете (c1_handover) и три демонстрационные сцены для роликов.
 */
final class StoryScenes {
    private StoryScenes() {}

    static void nightFx(double fs, double fe) {
        Cine.night();
        Cine.cone = 0;
        Cine.crush = 0.4f;
        Cine.fog((float) fs, (float) fe, 0.05f, 0.06f, 0.07f);
    }

    // ======================= пролог: поезд из тумана (12 с) =======================
    static Timeline prTrain() {
        return new Timeline(12, tl -> {
            final Entity[] tr = new Entity[1];
            tl.at(0, () -> {
                Cine.vf = true;
                tr[0] = Cine.puppet(tl.w, GContent.TRAIN, 160, TrainEntity.Y, 16.5, 90);
                Viewfinder.ghostRails(tl.w, true);
            });
            tl.each.add(t -> {
                nightFx(1.5, 34);
                double tx = trainX(t);
                Cine.glitch = (float) (0.08 + 0.5 * Math.max(0, 1 - Math.abs(tx - 60.5) / 25));
                Cine.crossing(tl.w, t, 0);
                Cine.motor(t);
                Cine.act(tr[0], "idle", t);
                Cine.place(tr[0], tx, TrainEntity.Y, 16.5, 90);
                if (Math.abs(tx - 60.5) < 30) Cine.key(tl.w, (int) Math.floor(tx), 68, 18, 7);
            });
            // оператор держит камеру сам: ждёт в темноту, ловит голову состава, ведёт его мимо, потом — вслед
            tl.shot(0, 12, (u, t) -> {
                Vec3d p = v(60.5, 67.5, 20.6).add(Cine.hand(t, 0).multiply(1.4));
                double tx = trainX(t);
                Vec3d far = v(160, 67.2, 16.5), front = v(tx - 4, 67.6, 16.5);
                double k = Cine.ease(Cine.seg(t, 2.4, 4.2));
                Vec3d look = far.lerp(front, k);
                if (t > 7) look = look.lerp(p.add(Cine.dirOf(20, 6).multiply(10)), Cine.ease(Cine.seg(t, 7, 11)));
                return new Vec3d[]{p, look};
            }).fov(40, 58).shake(0.25);
        });
    }

    static double trainX(double t) {
        return t < 2.5 ? 160 : 160 - 12.5 * (t - 2.5);
    }

    // ======================= пролог: баба Поля входит в вагон (14 с) =======================
    static Timeline prBoard() {
        Vec3d a = v(13.5, 65, 32), b = v(12.8, 66, 21.2), c = v(12.6, 66, 18.7), d = v(12.6, TrainEntity.Y + 0.15, 17.4);
        Vec3d camP = v(25.5, 67.7, 21.8);
        return new Timeline(14, tl -> {
            var polya = tl.actor("polya", "Баба Поля", a, 180);
            final Entity[] tr = new Entity[1];
            tl.at(0, () -> {
                Cine.vf = true;
                tr[0] = Cine.puppet(tl.w, GContent.TRAIN, 8, TrainEntity.Y, 16.5, 90);
                Viewfinder.ghostRails(tl.w, true);
                Cine.key(tl.w, 11, 68, 18, 9);
            });
            tl.each.add(t -> {
                nightFx(2, 40);
                Cine.place(tr[0], 8, TrainEntity.Y, 16.5, 90);
                Cine.act(tr[0], "idle", t);
                Cine.crossing(tl.w, t, 0);
                Cine.motor(t);
                Cine.glitch = (float) (0.1 + (t > 10 && t < 11.6 ? 0.25 * Cine.seg(t, 10, 11.2) : 0) + (t > 12.6 ? 0.3 : 0));
                if (t > 13.4) {   // обрыв плёнки
                    Cine.glitch = 1f;
                    Cine.tintR = Cine.tintG = Cine.tintB = 0.05f;
                }
            });
            // идёт во сне к краю перрона, мешкает (шаг назад), медленно оборачивается — смотрит в объектив, моргает; потом в вагон
            polya.walk(0, 6.2, v(13.2, 65, 26), b, c);
            polya.look = () -> v(12.6, 67.2, 15);   // на вагон
            tl.at(6.2, () -> polya.an.base("hesitate", 1.4));
            tl.at(7.6, () -> {
                polya.face = () -> camP;
                polya.look = Timeline::camPos;
            });
            tl.at(9.4, () -> polya.an.play(Gestures.GLANCE));
            tl.at(11.2, () -> {
                polya.face = () -> d;
                polya.look = () -> d.add(0, 1.4, 0);
            });
            polya.walk(12.0, 13.0, d);
            tl.shot(0, 14, (u, t) -> {
                Vec3d face = polya.head();
                Vec3d track = polya.pos.add(0, 1.3, 0);
                Vec3d look = t < 8.6 ? track : track.lerp(face, Cine.ease(Cine.seg(t, 8.6, 9.3)));
                if (t >= 11.6) look = face.lerp(d.add(0, 1.4, 0), Cine.ease(Cine.seg(t, 11.6, 12.8)));
                return new Vec3d[]{camP, look};
            }).shake(0.5).fovFn(t -> t < 9.0 ? 46 : t < 11.6 ? MathHelper.lerp(Cine.ease(Cine.seg(t, 9.0, 11.2)), 46, 15)
                    : MathHelper.lerp(Cine.ease(Cine.seg(t, 11.6, 12.4)), 15, 30));   // наезд на лицо в объективе
        });
    }

    // ======================= пересмотр ночной записи (15 с) =======================
    static Timeline rpNight() {
        return new Timeline(15, tl -> {
            var args = Cine.args;
            Vec3d at0 = args.contains("x") ? v(args.getDouble("x"), args.getDouble("y"), args.getDouble("z")) : v(4.5, 66, 19.5);
            final Vec3d at = (at0.z > 20.2 || at0.z < 17.8 || at0.x < -10 || at0.x > 21) ? v(MathHelper.clamp(at0.x, -8, 19), 66, 19.3) : at0;
            String who = args.getString("who");
            var me = tl.creature(GContent.DOPPEL, Gestures.DOPPEL, args.getString("me"), at, who.isEmpty() ? 180 : -90);
            var res = who.isEmpty() ? null : tl.actor(who, "", at.add(1.1, 0, -0.2), 90);
            var wt = tl.creature(GContent.WATCHER, Gestures.WATCHER, "", at.add(-1.0, 0, -0.55), -90);
            Vec3d camP = v(at.x - 3.2, at.y + 0.28, at.z + 1.25);
            tl.at(0, () -> {
                Cine.vf = true;
                Cine.replay = true;
                Cine.key(tl.w, (int) Math.floor(at.x - 1.6), (int) at.y + 1, (int) Math.floor(at.z), 5);
                wt.an.base("w_observe", 10.5);
                if (res != null) {
                    me.an.play(Gestures.REACH);   // держит за плечо
                    res.an.play(Gestures.SLEEP_JERK);
                }
            });
            if (res != null) {
                me.look = () -> res.head();
                res.look = () -> me.head();
                tl.at(3.0, () -> me.an.play(Gestures.REACH));
                tl.at(5.5, () -> res.an.play(Gestures.FLINCH));
            }
            // он стоит за спиной; на 10.5 — голова поворачивается в объектив (перелёт за 90°) и наклон к камере
            wt.look = () -> res != null ? res.head() : me.head();
            tl.at(10.5, () -> {
                wt.look = Timeline::camPos;
                wt.face = () -> camP;
                wt.an.base("w_lean", 4.5);
            });
            tl.each.add(t -> {
                Cine.night();
                Cine.cone = 0;
                Cine.crush = 0.42f;
                Cine.fog(1.2f, 26f, 0.04f, 0.05f, 0.055f);
                Cine.glitch = (float) (0.1 + 0.15 * Cine.seg(t, 9, 14));
                if (t > 14.3) {
                    Cine.glitch = 1f;
                    Cine.tintR = Cine.tintG = Cine.tintB = 0.05f;
                }
            });
            // камера лежит на перроне — неподвижна; автофокус «ищет»
            tl.shot(0, 15, (u, t) -> new Vec3d[]{camP, v(at.x + 0.2, at.y + 1.05, at.z - 0.3)}).shake(0)
                    .fovFn(t -> 52 + (t > 10.5 && t < 12 ? Math.sin(t * 40) * 1.5 : 0));
        });
    }

    // ======================= глава I: «Сдача смены» в дежурке (34 с) =======================
    static Timeline handover() {
        BlockPos lamp = new BlockPos(-6, 68, 25), board = new BlockPos(-9, 68, 24);
        Vec3d wall = v(-10.2, 67.2, 25.0), radio = v(-7.5, 67.2, 28.0), fig0 = v(-8.9, 66, 16.4);
        return new Timeline(34, tl -> {
            var z = tl.actor("zakhar", "Захар Ильич", v(-7.6, 66, 24.5), 90);
            var vika = tl.actor("vika", "Вика", v(-7.4, 66, 27.2), 0);
            var me = tl.actor("@me", "Сторож", v(-5.3, 66, 23.4), 120);
            final Entity[] fig = new Entity[1];
            tl.at(0, () -> {
                tl.hideReal();
                int gy = Cine.ground(tl.w, fig0.x, fig0.z);
                fig[0] = Cine.puppet(tl.w, GContent.WATCHER, fig0.x, gy, fig0.z, Cine.yawTo(fig0, v(-7.5, 66, 21)));
                Cine.key(tl.w, -5, 68, 23, 9);
                vika.e.setCustomName(null);
                ((com.yompix.glukhoman.entity.ResidentEntity) vika.e).act("rummage");
            });
            tl.each.add(t -> {
                Cine.fog(4f, 48f, 0.62f, 0.64f, 0.66f);
                Cine.tintR = 0.97f;
                if (fig[0] != null) Cine.act(fig[0], "still", t);
            });
            // 0–6: Захар у табло; на сторожа — глаза, голова, потом корпус
            z.look = () -> v(-9.5, 67.8, 24.5);
            tl.at(1.0, () -> z.look = () -> me.head());
            tl.at(1.7, () -> z.face = () -> me.pos);
            me.look = () -> z.head();
            me.face = () -> z.pos;
            vika.look = () -> radio;
            vika.face = () -> radio;
            tl.at(1.6, () -> vika.look = () -> me.head());
            tl.at(3.6, () -> vika.look = () -> radio);
            tl.line(2.4, z, "c1_zakhar_1", "Сторож… Ты снимал ночью? Потом покажешь.");
            // 6–12: одёрнул форму, правила; фоном — табло загорается само
            tl.play(6.2, z, Gestures.UNIFORM);
            tl.line(7.8, z, "c1_zakhar_2", "В десять вечера я объявляю правила. По громкоговорителю. Слушай всё до конца.");
            tl.lamp(10.4, board, true);
            tl.sound(10.4, "relay", Vec3d.ofCenter(board), 0.9f);
            tl.at(10.55, () -> z.look = () -> Vec3d.ofCenter(board));
            tl.at(11.4, () -> z.look = () -> me.head());
            // 12–17.5: крупно; показывает на табло — «табло скажет, где он»
            tl.line(12.4, z, "c1_zakhar_3", "И сиди в дежурке, пока не позвонит переезд. Табло скажет, где он.");
            tl.at(14.6, () -> {
                z.an.pointAt = Vec3d.ofCenter(board);
                z.look = () -> Vec3d.ofCenter(board);
            });
            tl.play(14.6, z, Gestures.point(1.4));
            tl.at(16.4, () -> z.look = () -> me.head());
            // 17.5–21.5: передаёт чистую кассету из рук в руки; кашель
            tl.at(17.3, () -> z.an.held = new ItemStack(GContent.CASSETTE));
            tl.play(17.5, z, Gestures.GIVE);
            tl.play(18.7, me, Gestures.TAKE);
            tl.at(19.5, () -> {
                z.an.held = ItemStack.EMPTY;
                me.an.held = new ItemStack(GContent.CASSETTE);
            });
            tl.stop(20.0, z, "give");
            tl.play(20.3, z, Gestures.COUGH);
            tl.sound(19.0, "radio_static", radio, 0.3f);
            // 21.8: стук в глухую стену — все вздрагивают и оборачиваются (резкий разворот)
            for (double k : new double[]{21.8, 22.2, 22.65}) tl.sound(k, "knock", wall, 1.0f);
            tl.at(21.85, () -> {
                z.an.startle(wall);
                z.look = () -> wall;
                vika.an.startle(wall);
                vika.look = () -> wall;
                ((com.yompix.glukhoman.entity.ResidentEntity) vika.e).act("");
                me.an.startle(wall);
                me.look = () -> wall;
                me.face = () -> wall;
            });
            tl.lamp(23.0, lamp, false);
            tl.lamp(23.2, lamp, true);
            tl.line(24.9, z, "proto_c5", "Не открывай. Это не к нам.");
            // 27–32: Вика у окна — на путях кто-то стоит; сбой плёнки — его нет
            vika.walk(26.6, 28.8, v(-6.6, 66, 26.2), v(-6.9, 66, 23.6), v(-8.55, 66, 22.45));
            tl.at(26.7, () -> vika.look = () -> fig0.add(0, 1.6, 0));
            tl.at(28.8, () -> vika.face = () -> fig0);
            tl.play(29.0, vika, Gestures.LEAN);
            tl.line(29.6, vika, "proto_c6", "Захар Ильич… там на путях кто-то стоит.");
            tl.at(31.5, () -> {
                Cine.place(fig[0], -60, 40, -60, 0);
                Cine.glitch = 0.9f;
            });
            tl.at(31.0, () -> {
                z.look = () -> me.head();
                z.face = () -> me.pos;
                me.look = () -> fig0.add(0, 1.6, 0);
            });
            tl.stop(31.8, vika, "lean");
            tl.line(32.0, z, "proto_c7", "Не смотри на него. Снимай.");
            tl.play(32.6, me, Gestures.RAISE);
            // планы
            tl.dolly(0, 6, () -> z.head().add(me.head()).multiply(0.5), v(-4.3, 67.55, 27.6), v(-4.35, 67.5, 27.2)).fov(64, 60).shake(0.5);
            tl.ots(6, 12.2, me, z, 1.8, 1.55, 0.15).fov(50, 47).shake(0.5);
            tl.close(12.2, 17.5, z, 22, 1.5, 1.1, 0.02).fov(48, 44).shake(0.35);
            tl.dolly(17.5, 21.6, () -> z.head().add(me.head()).multiply(0.5).add(0, -0.25, 0), v(-6.35, 67.4, 27.1), v(-6.25, 67.35, 26.8)).fov(58, 56).shake(0.45);
            tl.dolly(21.6, 24.6, () -> v(-8.0, 67.0, 24.6), v(-4.25, 68.1, 28.35), v(-4.3, 68.0, 28.2)).fov(66, 66).shake(0.9);
            tl.close(24.6, 26.6, z, -38, 1.25, 1.05, 0.0).fov(46, 44).shake(0.3);
            tl.dolly(26.6, 31.9, () -> v(-8.0, 67.35, 16.4), v(-6.85, 67.7, 24.6), v(-6.9, 67.66, 24.4)).fov(46, 26).shake(0.35);
            tl.ots(31.9, 34, z, me, -1.9, 1.3, 0.1).fov(50, 46).shake(0.45);
        });
    }

    // ======================= ролики 0.5.1 =======================
    /** Обходчик: появление из присяда, походка, замер, неправильный поворот головы, подкрадывание, наклон к камере, уход в темноту. */
    static Timeline demoWatcher() {
        return new Timeline(30, tl -> {
            Vec3d s0 = v(30.5, 66.06, 16.5);
            var w = tl.creature(GContent.WATCHER, Gestures.WATCHER, "", s0, 90);
            tl.at(0, () -> {
                w.an.base("w_unfold", 2.4);
                Cine.key(tl.w, 22, 68, 18, 10);
                Cine.key(tl.w, 12, 68, 18, 10);
            });
            tl.each.add(t -> {
                Cine.night();
                Cine.cone = 0.3f;
                Cine.crush = 0.3f;
                Cine.fog(3f, 40f, 0.14f, 0.15f, 0.18f);
            });
            w.walk(2.6, 9.0, v(26, 66.06, 16.5), v(20.5, 66.06, 16.6));    // неровная походка с запинкой
            tl.at(9.0, () -> w.an.frozen = true);                           // замер намертво
            tl.at(11.0, () -> {
                w.an.frozen = false;
                w.look = () -> v(20.5, 67.6, 26);                           // голова — за 90°, перелёт и медленный возврат, крен
            });
            tl.at(13.6, () -> w.look = Timeline::camPos);
            tl.at(15.0, () -> w.an.base("w_creep", 6.0));
            w.walk(15.0, 21.0, v(18.8, 66, 18.0), v(17.3, 66, 19.0));      // медленно подходит
            tl.at(21.0, () -> {
                w.face = Timeline::camPos;
                w.an.base("w_lean", 4.0);                                   // наклон в объектив
            });
            tl.at(25.4, () -> w.an.base("w_back", 3.0));
            tl.each.add(t -> {
                if (t > 25.4) {   // уходит назад кадрами — 4 шага в секунду
                    double k = Math.floor((t - 25.4) * 4) / 4;
                    Vec3d back = Vec3d.fromPolar(0, w.yaw).multiply(-0.3 * k);
                    w.pos = v(17.3, 66, 19.0).add(back);
                    Cine.place(w.e, w.pos.x, w.pos.y, w.pos.z, w.yaw);
                    Cine.tintR = Cine.tintG = Cine.tintB = (float) (1 - 0.8 * Cine.seg(t, 27.5, 30));
                }
            });
            tl.dolly(0, 9.0, () -> w.pos.add(0, 1.6, 0), v(25.0, 67.2, 21.2), v(22.0, 67.2, 21.5)).fov(55, 50).shake(0.5);
            tl.dolly(9.0, 11.0, () -> w.pos.add(0, 2.2, 0), v(23.2, 67.9, 21.4), v(23.0, 67.9, 21.2)).fov(50, 46).shake(0.15);
            tl.dolly(11.0, 15.0, () -> w.pos.add(0, 2.6, 0), v(16.9, 68.1, 17.9), v(17.1, 68.1, 17.8)).fov(52, 50).shake(0.3);
            tl.dolly(15.0, 21.0, () -> w.pos.add(0, 2.0, 0), v(13.6, 67.6, 20.6), v(14.0, 67.6, 20.5)).fov(58, 50).shake(0.6);
            tl.dolly(21.0, 30.0, () -> w.pos.add(0, 2.1, 0), v(15.0, 68.0, 20.5), v(15.1, 68.0, 20.5)).fov(58, 58).shake(0.7);
        });
    }

    /** Жители: у каждого своя походка, стойка, моргание и взгляд. */
    static Timeline demoResidents() {
        return new Timeline(26, tl -> {
            var pet = tl.actor("petrovich", "Петрович", v(12, 66, 18.7), 90);
            var gena = tl.actor("gena", "Гена", v(7.5, 66, 19.6), 180);
            var polya = tl.actor("polya", "Баба Поля", v(3.5, 66, 18.6), 90);
            var zina = tl.actor("zina", "Зина", v(-0.5, 66, 19.6), 180);
            var vika = tl.actor("vika", "Вика", v(-4.0, 66, 19.4), 180);
            var z = tl.actor("zakhar", "Захар Ильич", v(-8, 66, 19.0), -90);
            for (var a : new Timeline.Actor[]{pet, gena, polya, zina, vika, z}) {
                a.look = Timeline::camPos;
                a.face = Timeline::camPos;
            }
            pet.face = null;
            pet.walk(0.5, 6.5, v(10, 66, 19.0), v(8.8, 66, 18.6));
            polya.walk(9.5, 14.5, v(2.2, 66, 18.8));
            z.walk(19.5, 24.5, v(-6.5, 66, 19.0));
            tl.at(4.5, () -> gena.an.play(Gestures.WATCH));
            tl.sound(7.6, "relay", v(14, 67, 20), 0.9f);
            tl.at(7.65, () -> {
                gena.an.startle(v(14, 67, 20));
                gena.look = () -> v(14, 67, 20);
                pet.look = () -> v(14, 67, 20);
            });
            tl.at(8.6, () -> gena.look = Timeline::camPos);
            tl.play(7.0, pet, Gestures.WAVE_OFF);
            tl.play(15.5, vika, Gestures.GLANCE);
            tl.at(21, () -> z.an.play(Gestures.UNIFORM));
            tl.line(1.5, pet, "talk_petrovich", "Поезд-призрак? Ха. Гена тебе байки травит, чтоб не спал.");
            tl.line(13.0, zina, "talk_zina", "Петрович опять у переезда сидит. Ты его не слушай.");
            tl.line(16.5, vika, "talk_vika", "Снимешь меня? Для мамы. Она в Норильске.");
            tl.line(22.5, z, "talk_zakhar", "Табло горит без тока. Не спрашивай меня, сторож.");
            tl.dolly(0, 26, () -> v(MathHelper.lerp(Cine.ease(Cine.seg(tl.t, 0, 25)), 10.5, -7.5), 67.4, 19.1),
                    v(11.5, 67.4, 15.4), v(4.5, 67.4, 15.6), v(-3, 67.4, 15.6), v(-8.5, 67.4, 15.4)).fov(52, 52).shake(0.5);
        });
    }

    /** Интерактивные моменты (как в игре, от первого лица): схватили за плечо, шаг назад от двери, резкий разворот на звонок,
     *  Захар показывает дорогу, медленный подъём камеры, Обходчик тянется к объективу, житель в окне замечает фигуру. */
    static Timeline demoMoments() {
        return new Timeline(30, tl -> {
            Vec3d eye = v(4.5, 67.62, 19.3);
            var zina = tl.actor("zina", "Зина", v(6.6, 66, 18.8), 90);
            var z = tl.actor("zakhar", "Захар Ильич", v(1.6, 66, 20.2), 180);
            var w = tl.creature(GContent.WATCHER, Gestures.WATCHER, "", v(9.0, 66, 19.4), 90);
            var polya = tl.actor("polya", "Баба Поля", v(-1.5, 66, 20.4), 0);
            w.pos = v(-60, 40, -60);   // за кадром, пока не нужен
            tl.at(0, () -> Cine.key(tl.w, 4, 68, 19, 12));
            tl.each.add(t -> {
                Cine.fog(4f, 46f, 0.6f, 0.62f, 0.65f);
                Cine.vf = t >= 17.4 && t < 21.0;
            });
            // 1) Зина подходит и берёт за плечо — камеру дёргает
            zina.walk(0.2, 1.6, v(5.4, 66, 18.9));
            zina.look = () -> eye;
            zina.face = () -> eye;
            tl.play(1.4, zina, Gestures.REACH);
            tl.tug(1.75, 1);
            tl.line(1.8, zina, "c1_zina_1", "Ты чего у пустого дома встал, сторож?");
            // 2) отступает от двери зала
            tl.at(4.6, () -> zina.face = () -> v(2.5, 66, 21));
            zina.walk(4.6, 5.8, v(2.6, 66, 20.3));
            tl.play(6.6, zina, Gestures.RECOIL);
            // 3) звонок переезда за спиной — резкий разворот
            tl.sound(9.0, "crossing_bell", v(58, 67, 16), 1.4f);
            // 4) Захар показывает дорогу
            tl.at(12.0, () -> {
                z.an.pointAt = v(-6, 67, 26);
                z.look = () -> v(-6, 67, 26);
            });
            tl.play(12.0, z, Gestures.point(1.8));
            tl.at(13.8, () -> z.look = () -> eye);
            tl.line(12.2, z, "c1_zakhar_3", "И сиди в дежурке, пока не позвонит переезд. Табло скажет, где он.");
            // 6) Обходчик: пока камера у глаза — его нет; опустил — он наклонился к самому объективу
            tl.at(17.4, () -> w.pos = v(6.2, 66, 19.3));
            tl.at(18.0, () -> {
                w.face = () -> eye;
                w.look = () -> eye;
                w.an.base("w_lean", 6);
            });
            tl.at(24.6, () -> w.pos = v(-60, 40, -60));
            // 7) баба Поля у окна замечает фигуру за спиной у сторожа
            polya.look = () -> eye;
            tl.at(26.4, () -> {
                polya.an.startle(v(18, 67.5, 8));
                polya.look = () -> v(18, 67.5, 8);
            });
            // камера — от первого лица
            tl.shot(0, 30, (u, t) -> {
                Vec3d look;
                if (t < 4.4) look = zina.head();
                else if (t < 9.0) look = v(2.5, 67.2, 21);
                else if (t < 11.6) {   // резкий разворот на звонок: ease-out 0.38 с
                    double k = 1 - Math.pow(1 - Cine.seg(t, 9.05, 9.43), 3);
                    look = v(2.5, 67.2, 21).lerp(v(58, 67.5, 16), k);
                } else if (t < 16.4) look = z.head().lerp(v(-6, 67, 26), Cine.ease(Cine.seg(t, 12.4, 13.4)) * (1 - Cine.ease(Cine.seg(t, 14.4, 15.2))));
                else if (t < 21.0) look = v(12, 67.5, 19.3);
                else if (t < 25.0) look = v(6.2, 67.3, 19.3);
                else look = polya.head();
                Vec3d p = eye;
                if (t >= 16.4 && t < 17.9) {   // медленный подъём камеры к глазу: снизу, с «досадкой»
                    double r = 1 - Cine.ease(Cine.seg(t, 16.4, 17.9));
                    p = p.add(0, -0.25 * r, 0);
                    look = look.add(0, -2.2 * r, 0);
                }
                if (t >= 25.0) p = v(0.5, 67.62, 19.3);
                return new Vec3d[]{p, look};
            }).fov(70, 70).shake(0.6);
        });
    }
}
