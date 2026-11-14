package com.yompix.glukhoman.client;

import com.yompix.glukhoman.GContent;
import com.yompix.glukhoman.entity.ResidentEntity;
import com.yompix.glukhoman.entity.TrainEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import static com.yompix.glukhoman.client.Cine.*;

/**
 * Сцены трейлера 0.4 «Порожняк» (снимаются автотестом кадр за кадром, монтаж — tools/make_trailer3.py).
 * Всё — «чужая кассета»: ручная камера, REC, ночь, туман. Монстра почти не видно: поезд — только в видоискателе,
 * Обходчик — только глазами или на пересмотре плёнки.
 */
final class Trailer3 {
    private Trailer3() {}

    static Scene make(String id) {
        return switch (id) {
            case "t3_rails" -> new Rails();
            case "t3_cut" -> new Cut();
            case "t3_bell" -> new Bell();
            case "t3_train" -> new Train();
            case "t3_updown" -> new UpDown();
            case "t3_vanish" -> new Vanish();
            case "t3_house" -> new House();
            case "t3_board" -> new Board();
            case "t3_rewind" -> new Rewind();
            case "t3_rollcall" -> new RollCall();
            case "t3_draisine" -> new Draisine();
            case "t3_look" -> new Look();
            case "t3_stop" -> new Stop();
            default -> null;
        };
    }

    static void vhsNight() {
        night();
        cone = 0;
        crush = 0.32f;
        fog(2f, 34f, 0.06f, 0.068f, 0.08f);
    }

    /** 0:05 Видоискатель: перрон под фонарём, в окне дома напротив машет старушка. */
    static class Rails implements Scene {
        ResidentEntity polya;

        public void start(ClientWorld w) {
            vf = true;
            polya = resident(w, "polya", 18.5, 65, 37.4, 180);
            key(w, 18, 67, 38, 14);
            key(w, 17, 66, 38, 12);
        }

        public void frame(double t) {
            vhsNight();
            Vec3d p = v(8.6 + t * 0.2, 67.6, 19.6).add(hand(t, 0.4));
            float yw = (float) MathHelper.lerp(ease(seg(t, 0.6, 4.2)), -100, -27);
            float pt = (float) MathHelper.lerp(ease(seg(t, 0.6, 4.2)), 10, 2.5);
            cam(p, p.add(dirOf(yw, pt)));
            fov = (float) MathHelper.lerp(ease(seg(t, 3.6, 6.3)), 58, 22);
            glitch = 0.06f;
            act(polya, "slowwave", t);
            place(polya, 18.5, 65, 37.6, 180);
            motor(t);
        }
    }

    /** 0:13 Рельсы обрываются. Голые шпалы в лес, на них — босые следы. Фонарь. */
    static class Cut implements Scene {
        ClientWorld w;

        public void start(ClientWorld w) {
            this.w = w;
            vf = true;
        }

        public void frame(double t) {
            vhsNight();
            fog(1.2f, 24f, 0.06f, 0.068f, 0.08f);
            double x = MathHelper.lerp(ease(seg(t, 0, 5)), 57.5, 66.5);
            Vec3d p = v(x, 67.4, 16.8).add(hand(t, 1));
            float pt = (float) MathHelper.lerp(ease(seg(t, 0, 5)), 38, 14);
            cam(p, p.add(dirOf(-90, pt)));
            fov = 56;
            glitch = 0.08f;
            beam(w, p, dirOf(-90 + (float) Math.sin(t * 0.8) * 3, pt + 6), true);
            motor(t);
        }
    }

    /** 0:19 На мёртвой линии звонит переезд. Красные огни. */
    static class Bell implements Scene {
        ClientWorld w;

        public void start(ClientWorld w) {
            this.w = w;
            vf = true;
            key(w, 28, 66, 20, 7);
            key(w, 34, 66, 12, 7);
        }

        public void frame(double t) {
            vhsNight();
            Vec3d p = v(23.6, 67.5, 23.8).add(hand(t, 0.2));
            Vec3d look = v(MathHelper.lerp(seg(t, 0, 6), 28, 30.5), 67.6, MathHelper.lerp(seg(t, 0, 6), 18.5, 16.5));
            cam(p, look);
            fov = (float) MathHelper.lerp(ease(seg(t, 0, 6)), 52, 40);
            glitch = 0.08f + 0.12f * (float) Math.max(0, Math.sin(t * 2));
            crossing(w, t, 0.4);
        }
    }

    /** 0:24 В видоискателе бесшумно подходит состав. Окна светятся. Лица. */
    static class Train implements Scene {
        ClientWorld w;
        TrainEntity tr;

        public void start(ClientWorld w) {
            this.w = w;
            vf = true;
            tr = puppet(w, GContent.TRAIN, 130, 66, 16.5, 90);
            Viewfinder.ghostRails(w, true);
        }

        public void frame(double t) {
            vhsNight();
            double tx = 112 - 11 * t;
            Vec3d p = v(40.5, 67.4, 20.2).add(hand(t, 0));
            cam(p, v(tx - 6, 68.2, 16.5));
            fov = (float) MathHelper.lerp(ease(seg(t, 0, 8)), 34, 50);
            glitch = (float) (0.08 + 0.4 * Math.max(0, 1 - Math.abs(tx - 40) / 30));
            act(tr, "idle", t);
            place(tr, tx, 66, 16.5, 90);
            crossing(w, t, 0);
            motor(t);
        }
    }

    /** 0:32 Камеру опускают — пусто (а на краю перрона кто-то стоит). Поднимают — поезд здесь. */
    static class UpDown implements Scene {
        TrainEntity tr;
        Entity watcher;

        public void start(ClientWorld w) {
            vf = true;
            tr = puppet(w, GContent.TRAIN, 8, 66, 16.5, 90);
            watcher = puppet(w, GContent.WATCHER, -6.6, 66, 19.5, -90);
            Viewfinder.ghostRails(w, true);
            key(w, 6, 68, 19, 8);
            key(w, -7, 68, 19, 11);
        }

        public void frame(double t) {
            vhsNight();
            vf = t < 1.8 || t >= 3.5;   // опустил — нет REC, нет поезда; Обходчик виден только глазами
            Vec3d p = v(16.5, 67.6, 20.4).add(hand(t, vf ? 0 : 0.5));
            float yw = (float) (100 + Math.sin(t * 0.7) * 3), pt = vf ? 4 : 9;
            cam(p, p.add(dirOf(yw, pt)));
            fov = vf ? (t >= 3.5 ? (float) MathHelper.lerp(ease(seg(t, 3.5, 5.5)), 46, 30) : 46) : 62;
            glitch = vf ? (t >= 3.5 && t < 3.8 ? 0.8f : 0.1f) : 0.02f;
            act(tr, "idle", t);
            place(tr, 8, 66, 16.5, 90);
            act(watcher, "still", t);
            place(watcher, -6.6, 66, 19.5, -90);
            motor(t);
        }
    }

    /** Short: глазами — старушка идёт к краю перрона. Шаг… и её нет между двумя шагами. */
    static class Vanish implements Scene {
        ResidentEntity polya;

        public void start(ClientWorld w) {
            vf = false;
            polya = resident(w, "polya", 21.5, 66, 19.6, 90);
            key(w, 14, 68, 19, 7);
        }

        public void frame(double t) {
            vhsNight();
            Vec3d p = v(6.5, 67.6, 20.2).add(hand(t, 0));
            Vec3d at = v(MathHelper.lerp(seg(t, 0, 3.1), 21.5, 13.6), 66, MathHelper.lerp(seg(t, 0, 3.1), 19.6, 18.9));
            cam(p, at.add(0, 1.4, 0));
            fov = 52;
            glitch = 0.03f;
            act(polya, "walk", t);
            if (t < 3.1) place(polya, at.x, at.y, at.z, 90);
            else place(polya, 0, -100, 0, 90);   // между двумя шагами
        }
    }

    /** 0:37 Утро. Заколоченный дом. Соседка: «Какая Поля?» */
    static class House implements Scene {
        ResidentEntity zina;

        public void start(ClientWorld w) {
            vf = true;
            zina = resident(w, "zina", 11.8, 65, 37.2, -60);
        }

        public void frame(double t) {
            fog(8f, 60f, 0.30f, 0.32f, 0.35f);
            crush = 0.25f;
            cone = 0;
            Vec3d p = v(7.6, 66.6, 41.6).add(hand(t, 0.2));
            Vec3d a = v(14, 66.5, 40), b = v(11.8, 66.6, 37.4);
            cam(p, a.lerp(b, ease(seg(t, 2.2, 3.2))));
            fov = 50;
            glitch = 0.05f;
            if (zina != null) zina.talking = t > 2.8;
            act(zina, "idle", t);
            place(zina, 11.8, 65, 37.2, -60);
        }
    }

    /** 0:42 Табло в дежурке: лампочки участков загораются по одной — к станции. Щелчки реле. */
    static class Board implements Scene {
        ClientWorld w;
        final java.util.List<BlockPos> lamps = new java.util.ArrayList<>();

        public void start(ClientWorld w) {
            this.w = w;
            vf = true;
            for (int row = 1; row >= 0; row--) for (int z = 23; z <= 27; z++) lamps.add(new BlockPos(-9, 67 + row, z));
            for (BlockPos q : lamps) lamp(w, q, false);
        }

        public void frame(double t) {
            night();
            cone = 0;
            crush = 0.3f;
            Vec3d p = v(-5.6, 67.6, 25.2).add(hand(t, 0).multiply(0.6));
            cam(p, v(-9, 67.6, 25));
            fov = (float) MathHelper.lerp(ease(seg(t, 0, 5)), 60, 48);
            glitch = 0.05f;
            int[] order = {7, 6, 5, 4, 3, 2};
            for (int i = 0; i < order.length; i++) {
                double at = 0.5 + i * 0.7;
                boolean on = i == order.length - 1 ? t >= at : t >= at && t < at + 1.0;
                lamp(w, lamps.get(order[i]), on);
                if (once("relay" + i, t, at)) sound("relay", Vec3d.ofCenter(lamps.get(order[i])), 1f, 1f);
            }
        }
    }

    /** 0:47 Перемотка: на старой записи за спиной оператора — высокий силуэт. Вплотную. */
    static class Rewind implements Scene {
        Entity me, watcher;

        public void start(ClientWorld w) {
            vf = true;
            replay = true;
            me = resident(w, "kolya", 6.2, 66, 19.5, -90);
            watcher = puppet(w, GContent.WATCHER, 5.1, 66, 18.95, -90);
            key(w, 4, 67, 19, 6);
            sound("rewind", null, 0.9f, 1f);
        }

        public void frame(double t) {
            vhsNight();
            Vec3d p = v(1.6, 66.3, 18.4);
            double up = ease(seg(t, 4.25, 4.75));   // рывок камеры вверх — к его лицу
            cam(p, v(MathHelper.lerp(up, 6.0, 5.0), MathHelper.lerp(up, 67.2, 68.15), MathHelper.lerp(up, 19.4, 19.0)));
            fov = (float) MathHelper.lerp(up, 54, 38);
            glitch = t < 1.0 ? 0.9f : t > 4.3 ? 0.45f : 0.1f;
            act(me, "idle", t);
            place(me, 6.2, 66, 19.5, -90);
            act(watcher, t < 4.3 ? "still" : "lean", t < 4.3 ? t : (t - 4.3) * 2.2);
            place(watcher, 5.1, 66, 18.95, -90 + (float) (t > 4.3 ? 185 * ease(seg(t, 4.3, 5.2)) : 0));
            if (t > 5.6) {
                glitch = 1f;
                tintR = tintG = tintB = 0.05f;
            }
        }
    }

    /** 0:52 Перекличка: «…семь, восемь, девять». Камера по лицам — их восемь. */
    static class RollCall implements Scene {
        final String[] who = {"zakhar", "nyura", "petrovich", "zina", "kolya", "lyuba", "vika", "mitya"};
        final ResidentEntity[] r = new ResidentEntity[8];

        public void start(ClientWorld w) {
            vf = true;
            for (int i = 0; i < 8; i++) r[i] = resident(w, who[i], -5.5 + i * 1.6, 66, 18.7, 0);
            key(w, 0, 68, 19, 9);
            key(w, 7, 68, 19, 9);
        }

        public void frame(double t) {
            night();
            cone = 0;
            crush = 0.3f;
            fog(4f, 40f, 0.06f, 0.068f, 0.08f);
            double x = MathHelper.lerp(ease(seg(t, 0.2, 5)), -6.5, 7.5);
            Vec3d p = v(x, 67.7, 20.6).add(hand(t, 0.5));
            cam(p, v(x + 1.4, 67.6, 18.7));
            fov = 44;
            glitch = 0.06f;
            for (int i = 0; i < 8; i++) {
                act(r[i], "idle", t + i);
                place(r[i], -5.5 + i * 1.6, 66, 18.7, 0);
            }
        }
    }

    /** 0:57 Дрезина в тумане. Пикет «58». Стук на стыках — позади, где рельсов нет. */
    static class Draisine implements Scene {
        ClientWorld w;

        public void start(ClientWorld w) {
            this.w = w;
            vf = true;
        }

        public void frame(double t) {
            vhsNight();
            fog(0.6f, 16f, 0.07f, 0.078f, 0.09f);
            double x = 112 + t * 2.2;
            Vec3d p = v(x, 67.3 + Math.sin(t * 9) * 0.015, 16.5);
            cam(p, p.add(dirOf(-90 + (float) Math.sin(t * 0.5) * 6, 6)));
            fov = 60;
            glitch = 0.1f;
            beam(w, p, dirOf(-84, 10), true);
            if (once("j1", t, 1.0)) sound("joints", p.add(-4, 0, 0), 1.6f, 1f);
            if (once("j2", t, 3.0)) sound("joints", p.add(-6, 0, 0), 1.6f, 0.9f);
        }
    }

    /** 1:01 Пересмотр пролога: старушка в двери вагона оборачивается и смотрит в объектив (наезд). */
    static class Look implements Scene {
        final Scene inner = new Cine.PrBoard();

        public void start(ClientWorld w) {
            inner.start(w);
        }

        public void frame(double t) {
            inner.frame(Math.min(13.3, t + 8.9));
        }
    }

    /** 1:08 Видоискатель на тёмный перрон; пусто. На краю кадра кто-то поворачивает голову в объектив. STOP. */
    static class Stop implements Scene {
        Entity watcher;

        public void start(ClientWorld w) {
            vf = true;
            replay = true;
            watcher = puppet(w, GContent.WATCHER, 7.2, 66, 20.3, 90);
        }

        public void frame(double t) {
            vhsNight();
            Vec3d p = v(4.6, 67.55, 19.6).add(hand(t, 0).multiply(0.5));
            cam(p, p.add(dirOf(-120, 6)));
            fov = 58;
            glitch = t > 1.8 ? 0.4f : 0.08f;
            act(watcher, t < 1.4 ? "still" : "lean", t < 1.4 ? t : (t - 1.4) * 1.8);
            place(watcher, 7.2, 66, 20.3, 90 - (float) (t > 1.4 ? 80 * ease(seg(t, 1.4, 2.3)) : 0));
            if (t > 2.6) {
                glitch = 1f;
                tintR = tintG = tintB = 0.03f;
            }
        }
    }
}
