package com.yompix.glukhoman.client;

import com.yompix.glukhoman.GContent;
import com.yompix.glukhoman.client.anim.Gestures;
import com.yompix.glukhoman.entity.ResidentEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import static com.yompix.glukhoman.client.Timeline.v;

/** Кат-сцены части третьей (0.7): глава IV, глава V (ролик), три концовки. */
final class Part3Scenes {
    private Part3Scenes() {}

    static final int CX = 150, CZ = -40, CY = 89;   // вагоны изнутри (Part3Map), пол + 1

    static void act(Timeline.Actor a, String anim) {
        if (a.e instanceof ResidentEntity r) r.act(anim);
    }

    // ======================= «Пустые стулья» (18 с): длинный план, на каждом проходе — на одного меньше =======================
    static Timeline chairs() {
        return new Timeline(18, tl -> {
            String[] who = {"zina", "petrovich", "vika", "polya"};
            Timeline.Actor[] a = new Timeline.Actor[4];
            for (int i = 0; i < 4; i++) a[i] = tl.actor(who[i], "", v(-0.5 + i * 1.7, 66.5, 27.6), 180);
            tl.at(0, () -> {
                tl.hideReal();
                for (var x : a) act(x, "sit");
                Cine.key(tl.w, 2, 68, 25, 11);
            });
            tl.each.add(t -> {
                Cine.tintR = 1.05f;
                Cine.tintB = 0.92f;
                int pass = (int) (t / 6);
                for (int i = 0; i < 4; i++) a[i].pos = i >= 4 - pass ? v(-60, 40, -60) : v(-0.5 + i * 1.7, 66.5, 27.6);
            });
            for (int p = 0; p < 3; p++) {
                double t0 = p * 6;
                tl.dolly(t0, t0 + 6, () -> v(2, 67.0, 28), v(-1.6, 67.5, 24.2), v(5.2, 67.5, 24.4)).fov(52, 52).shake(0.3);
            }
            tl.sound(6.0, "tape_click", null, 0.5f);
            tl.sound(12.0, "tape_click", null, 0.5f);
        });
    }

    // ======================= «Перекличка» (18 с) =======================
    static Timeline rollcall() {
        return new Timeline(18, tl -> {
            String[] who = {"zakhar", "zina", "vika", "gena", "kid_a", "kid_b", "@me", "zina"};
            String[] nm = {"Захар Ильич", "Зина", "Вика", "Гена", "", "", "Сторож", "Зина"};
            Timeline.Actor[] a = new Timeline.Actor[who.length];
            for (int i = 0; i < who.length; i++) a[i] = tl.actor(who[i], nm[i], v(-1.5 + i * 0.95, 66, 25.0), 180);
            tl.at(0, () -> {
                tl.hideReal();
                Cine.key(tl.w, 2, 68, 24, 12);
            });
            Vec3d cam0 = v(1.8, 67.5, 21.8);
            for (var x : a) x.look = () -> cam0.add(0, 0, 0);
            tl.line(0.6, a[0], "c4_rollcall_1", "Перекличка. Все — громко: «я».");
            for (int i = 0; i < who.length; i++) {
                final int k = i;
                tl.at(3.6 + i * 1.0, () -> a[k].an.say("Я."));
                tl.at(3.6 + i * 1.0, () -> a[k].look = Timeline::camPos);
            }
            tl.at(11.6, () -> a[7].look = () -> a[1].head());   // лишняя смотрит на «себя»
            tl.line(12.6, a[0], "c4_rollcall_2", "…Двенадцать. А нас одиннадцать. Кто лишний?");
            tl.at(13.4, () -> {
                for (int i = 1; i < who.length; i++) {
                    final int k = i;
                    a[k].look = () -> a[(k + 3) % who.length].head();
                }
            });
            tl.shot(0, 18, (u, t) -> {
                double x = MathHelper.lerp(Cine.ease(Cine.seg(t, 3.0, 11.6)), -1.5, 5.2);
                return new Vec3d[]{cam0.add(t < 12.6 ? 0 : (t - 12.6) * 0.1, 0, 0), v(t < 3 ? 1.8 : x, 67.4, 25.0)};
            }).fov(50, 50).shake(0.35);
        });
    }

    // ======================= «Лицо» (12 с): перемотка — в объектив наклоняется он, а лицо — твоё =======================
    static Timeline face() {
        return new Timeline(12, tl -> {
            var mc = net.minecraft.client.MinecraftClient.getInstance();
            String me = mc.player != null ? mc.player.getName().getString() : "";
            var d = tl.creature(GContent.DOPPEL, Gestures.DOPPEL, me, v(-6.5, 66, 23.4), 0);
            tl.at(0, () -> {
                Cine.vf = true;
                Cine.replay = true;
                Cine.key(tl.w, -5, 67, 26, 4);
            });
            d.walk(5.0, 9.2, v(-5.6, 66, 25.6), v(-5.0, 66, 26.6));
            tl.at(8.6, () -> d.an.play(Gestures.LEAN));
            d.look = Timeline::camPos;
            d.face = Timeline::camPos;
            tl.each.add(t -> {
                StoryScenes.nightFx(1.0, 10);
                Cine.glitch = (float) (0.12 + 0.5 * Cine.seg(t, 8.5, 11.3));
                Cine.tintR = Cine.tintG = Cine.tintB = t > 11.4 ? 0.04f : 1f;
            });
            tl.sound(11.4, "tape_click", null, 1f);
            tl.shot(0, 12, (u, t) -> new Vec3d[]{v(-4.6, 67.6, 27.5), t < 5 ? v(-6.2, 66.8, 25.6) : d.head()}).fov(58, 50).shake(0.1);
        });
    }

    // ======================= Глава V: вагоны изнутри (ролик, 36 с) =======================
    static Timeline cars() {
        return new Timeline(36, tl -> {
            Timeline.Actor[] pass = new Timeline.Actor[4];
            for (int i = 0; i < 4; i++) pass[i] = tl.actor("nameless", "Пассажир", v(CX + 2.5 + i * 2, CY + 0.5, CZ + 4.3), 180);
            var gena = tl.actor("gena", "Гена", v(CX + 12 + 5.5, CY, CZ + 2.6), -90);
            var vika = tl.actor("vika", "Вика", v(CX + 24 + 5.5, CY + 0.5, CZ + 1.6), 0);
            var wt = tl.creature(GContent.WATCHER, Gestures.WATCHER, "", v(CX + 36 + 5.5, CY, CZ + 4.2), 180);
            var me = tl.actor("@me", "Сторож", v(-60, 40, -60), 0);
            tl.at(0, () -> {
                for (var p : pass) act(p, "sit");
                act(vika, "sit");
                for (int k = 0; k < 4; k++) Cine.key(tl.w, CX + k * 12 + 5, CY + 2, CZ + 2, 10);
                wt.an.base("w_observe", 40);
            });
            for (var p : pass) p.look = Timeline::camPos;
            gena.look = Timeline::camPos;
            vika.look = () -> v(CX + 29.5, CY + 2, CZ - 2);
            wt.look = Timeline::camPos;
            tl.each.add(t -> {
                Cine.fog(2f, 20f, 0.05f, 0.04f, 0.04f);
                Cine.tintR = 1.05f;
                if (t > 5.0) pass[0].pos = v(-60, 40, -60);
            });
            tl.line(1.0, me, "c5_car1", "Теплушка. Без лиц. Их сорок мест. Надо называть.");
            tl.at(3.6, () -> pass[0].an.play(Gestures.UNIFORM));
            tl.line(3.8, pass[0], "", "38. Гринюк Семён Петрович.");
            tl.line(11.5, gena, "c5_gena", "Последний раз, Лёха. Твоя кассета — и мы уходим. Или запиши меня вместо себя. Мне всё равно уже.");
            tl.at(20.0, () -> vika.look = Timeline::camPos);
            tl.line(20.4, vika, "c5_vika_out", "Ты мне включишь? Ещё раз? …Пойдём.");
            tl.line(27.2, me, "c5_car4", "Место сорок. Он напротив. Снимает меня.");
            tl.at(31.0, () -> Cine.vf = true);
            tl.shot(0, 9, (u, t) -> new Vec3d[]{v(CX + 1.6 + t * 0.6, CY + 1.62, CZ + 2.0), v(CX + 4 + t * 0.4, CY + 1.2, CZ + 4.2)}).fov(70, 64).shake(0.6);
            tl.shot(9, 18, (u, t) -> new Vec3d[]{v(CX + 13.6 + (t - 9) * 0.25, CY + 1.62, CZ + 2.2), gena.head()}).fov(64, 56).shake(0.5);
            tl.shot(18, 26, (u, t) -> new Vec3d[]{v(CX + 26 + (t - 18) * 0.2, CY + 1.62, CZ + 3.2), vika.head()}).fov(60, 50).shake(0.4);
            tl.shot(26, 31, (u, t) -> new Vec3d[]{v(CX + 39 + (t - 26) * 0.3, CY + 1.62, CZ + 1.6), wt.head()}).fov(58, 46).shake(0.4);
            tl.shot(31, 36, (u, t) -> {   // повернул видоискатель на себя: в кадре вы рядом
                double k = Cine.ease(Cine.seg(t, 31, 33));
                return new Vec3d[]{v(CX + 41.5, CY + 1.62, CZ + 3.2), v(CX + 41.5, CY + 1.4, CZ + 1.0).lerp(v(CX + 43, CY + 1.6, CZ + 4.5), k)};
            }).fov(70, 70).shake(0.5);
        });
    }

    // ======================= концовки =======================
    static Timeline poimenno() {
        return new Timeline(26, tl -> {
            var z = tl.actor("zakhar", "Захар Ильич", v(1.5, 66, 18.6), 0);
            var vika = tl.actor("vika", "Вика", v(4.6, 66, 19.6), 0);
            var zina = tl.actor("zina", "Зина", v(-1.5, 66, 19.2), 0);
            tl.at(0, () -> {
                tl.hideReal();
                for (int c = 0; c < 4; c++) {
                    StringBuilder s = new StringBuilder();
                    for (int r = 0; r < 10; r++) {
                        int n = c * 10 + r + 1;
                        s.append(n).append(". ").append(com.yompix.glukhoman.story.Names.surname(n)).append('\n');
                    }
                    Part2Scenes.label(s.toString(), v(-3.4 + c * 2.5, 66.2, 15.6), 0, 0.5f);
                }
            });
            tl.each.add(t -> {
                Cine.tintR = 1.06f;
                Cine.tintG = 1.03f;
                Cine.tintB = 0.92f;
                Cine.fog(10f, 90f, 0.7f, 0.72f, 0.74f);
            });
            z.look = () -> v(1, 67.2, 15.3);
            zina.look = () -> v(1, 67.2, 15.3);
            vika.look = () -> v(1, 67.2, 15.3);
            tl.line(2.0, z, "end_poimenno_1", "Гринюк Семён Петрович. Кравец Иосиф. Лещенко Михаил Андреевич…");
            tl.line(10.0, z, "end_poimenno_2", "Сам. Своим голосом. Все сорок.");
            tl.at(17.0, () -> {
                vika.look = Timeline::camPos;
                vika.face = Timeline::camPos;
                act(vika, "wave");
            });
            tl.line(18.4, vika, "end_poimenno_3", "Мам, это я.");
            tl.dolly(0, 9, () -> v(1, 67.2, 15.3), v(-1.0, 67.8, 21.0), v(3.0, 67.6, 20.6)).fov(56, 48).shake(0.4);
            tl.close(9, 16, z, 25, 1.6, 1.2, 0.0).fov(46, 44).shake(0.3);
            tl.close(16, 26, vika, 0, 2.2, 1.4, 0.0).fov(48, 42).shake(0.35);
        });
    }

    static Timeline naryad() {
        return new Timeline(22, tl -> {
            var g = tl.actor("gena", "Гена", v(2.0, 66, 19.2), 90);
            var me = tl.actor("@me", "Сторож", v(0.5, 66, 19.4), -90);
            var stranger = tl.actor("kolya", "", v(-60, 40, -60), 0);
            tl.at(0, () -> tl.hideReal());
            g.look = () -> me.head();
            me.look = () -> g.head();
            tl.each.add(t -> {
                Cine.tintR = Cine.tintG = 0.9f;
                Cine.tintB = 1.0f;
                Cine.fog(5f, 50f, 0.6f, 0.62f, 0.66f);
                Cine.vf = t > 11;
                Cine.replay = t > 11;
                stranger.pos = t > 11 ? v(1.2, 66, 22.6) : v(-60, 40, -60);
            });
            stranger.look = Timeline::camPos;
            stranger.face = Timeline::camPos;
            tl.line(1.5, g, "end_naryad_1", "Все на месте? Раз, два, три… Все.");
            tl.line(13.0, me, "end_naryad_2", "А это кто на кассете? Ты его знаешь? …Никто не знает.");
            tl.ots(0, 11, me, g, 1.8, 1.4, 0.15).fov(52, 48).shake(0.4);
            tl.close(11, 22, stranger, 0, 1.6, 0.9, 0.0).fov(46, 38).shake(0.3);
        });
    }

    static Timeline obkh() {
        return new Timeline(22, tl -> {
            var wt = tl.creature(GContent.WATCHER, Gestures.WATCHER, "", v(14.5, 66, 19.2), 90);
            var me = tl.actor("@me", "Сторож", v(-60, 40, -60), 0);
            tl.at(0, () -> {
                Cine.key(tl.w, 13, 68, 20, 8);
                wt.an.base("w_observe", 30);
            });
            wt.look = Timeline::camPos;
            tl.each.add(t -> {
                StoryScenes.nightFx(2, 30);
                if (t >= 10.5) {   // глазами: светлый туман — силуэт в твоей куртке читается
                    Cine.fog(3f, 40f, 0.16f, 0.17f, 0.2f);
                    Cine.crush = 0.2f;
                }
                Cine.vf = t < 10.5;
                wt.pos = t < 10.5 ? v(-60, 40, -60) : v(14.5, 66, 19.2);
                Cine.crossing(tl.w, t, 13);
            });
            tl.line(1.0, me, "end_obkh_1", "REC. Октябрь девяносто седьмого. Новая бригада.");
            tl.line(6.5, me, "end_obkh_2", "В видоискателе — никого. А глазами…");
            tl.sound(10.5, "breath_close", null, 0.4f);
            tl.shot(0, 22, (u, t) -> new Vec3d[]{v(-2, 67.6, 19.4), v(14.5, 67.6 + (t > 10.5 ? 0.8 : 0), 19.2)}).fov(48, 34).shake(0.45);
        });
    }
}
