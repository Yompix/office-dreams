package com.yompix.novograd.client;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.Novograd;
import com.yompix.novograd.life.HandKind;
import com.yompix.novograd.life.Hands;
import com.yompix.novograd.life.Life;
import net.minecraft.block.Blocks;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

import static com.yompix.novograd.client.ShowAutotest.*;

/**
 * 0.9.8, только разработка: {@code -Dnovograd.autotest=handshow} — витрина «Рук»: банкомат (карта, PIN, деньги),
 * автомат (банка и чипсы), ларёк, касса, дверь, колонка и ремонт, сейф, сигарета и бутылка, перезарядки пяти стволов,
 * бита/нож/кулак — от третьего лица (камера сбоку) и от первого. Снимки — screenshots/show/hand_*.png, ролик —
 * -Dnovograd.rec=файл.mp4. В конце в логе: «[hands] done/cancel» (сколько действий кончилось и прервалось).
 */
public final class HandShow {
    private HandShow() {}

    private static BlockPos atm, atmWall, vend, cart, door;

    static void handshow() {
        cmd(5, "time set 6000");
        cmd(5, "weather clear");
        cmd(5, "gamerule doDaylightCycle false");
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            base = studio(p);
            var w = p.getServerWorld();
            BlockPos b = BlockPos.ofFloored(base);
            atm = b.add(0, 0, -3);
            atmWall = b.add(-7, 0, -3);
            vend = b.add(4, 0, -3);
            cart = b.add(8, 0, -3);
            door = b.add(-4, 0, -3);
            w.setBlockState(atm, ModContent.ATM.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.SOUTH));
            for (int dx = -1; dx <= 1; dx++) for (int y = 0; y < 3; y++) w.setBlockState(atmWall.add(dx, y, -1 + (dx == 0 ? 0 : 1)), Blocks.POLISHED_ANDESITE.getDefaultState());
            w.setBlockState(atmWall, ModContent.ATM_WALL.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.SOUTH));
            w.setBlockState(atmWall.up(2), Blocks.POLISHED_ANDESITE.getDefaultState());
            w.setBlockState(vend, com.yompix.novograd.world.Props.VENDING.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.SOUTH));
            w.setBlockState(cart, com.yompix.novograd.world.Props.HOTDOG_CART.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.SOUTH));
            w.setBlockState(door, Blocks.OAK_DOOR.getDefaultState());
            w.setBlockState(door.up(), Blocks.OAK_DOOR.getDefaultState().with(net.minecraft.block.DoorBlock.HALF, net.minecraft.block.enums.DoubleBlockHalf.UPPER));
            var d = Life.get(p);
            d.money = 5000;
            d.cash = 300;
            d.storyStarted = true;
            d.storyDone = true;
            d.look = com.yompix.novograd.life.LookGen.generate(31, true, com.yompix.novograd.life.LookGen.CASUAL, false);
            d.look.hair = "ponytail";
            d.look.hat = "";
            d.look.glasses = "";
            d.look.bag = "";
            d.look.sanitize();
            Life.sync(p);
            com.yompix.novograd.life.Style.broadcast(p);
            p.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
            p.getAbilities().flying = false;
            p.sendAbilitiesUpdate();
        });
        run(100, c -> {
            if (c.currentScreen != null) c.setScreen(null);
            c.options.hudHidden = true;
        });
        run(40, ShowAutotest::rec);
        // ---------------- банкомат: третье лицо, потом первое
        station(() -> atm, 0.5, 2.3, "atm");
        click(10, () -> atm);
        shotAt(14, "hand_atm_card");
        shotAt(14, "hand_atm_pin");
        run(26, c -> { if (c.currentScreen != null) shot(c, "hand_atm_menu"); });
        run(6, c -> {
            if (c.currentScreen instanceof com.yompix.novograd.client.screen.AtmScreen a) a.pressWithdraw(250);
        });
        shotAt(24, "hand_atm_cash");
        shotAt(18, "hand_atm_count");
        run(30, c -> {});
        firstPerson(() -> atm, "atm_fp");
        click(8, () -> atm);
        shotAt(18, "hand_fp_atm_card");
        shotAt(14, "hand_fp_atm_pin");
        run(20, c -> { if (c.currentScreen != null) c.currentScreen.close(); });
        shotAt(10, "hand_fp_atm_takecard");
        run(30, c -> thirdPerson(c));
        // настенный банкомат
        station(() -> atmWall, 0.6, -2.3, "atm_wall");
        click(10, () -> atmWall);
        shotAt(30, "hand_atm_wall");
        run(30, c -> { if (c.currentScreen != null) c.currentScreen.close(); });
        run(30, c -> {});
        // ---------------- автомат: банка; потом чипсы (Shift)
        station(() -> vend, 0.5, 2.3, "vend");
        click(10, () -> vend);
        shotAt(16, "hand_vend_coin");
        shotAt(30, "hand_vend_pick");
        shotAt(30, "hand_vend_open");
        shotAt(30, "hand_vend_drink");
        run(50, c -> {});
        firstPerson(() -> vend, "vend_fp");
        run(2, c -> c.player.setSneaking(true));
        server(1, s -> pl(s).setSneaking(true));
        click(4, () -> vend);
        server(2, s -> pl(s).setSneaking(false));
        run(1, c -> c.player.setSneaking(false));
        shotAt(48, "hand_fp_vend_tray");
        shotAt(40, "hand_fp_chips");
        run(70, c -> thirdPerson(c));
        // ---------------- ларёк
        station(() -> cart, 0.6, 2.4, "stall");
        click(10, () -> cart);
        shotAt(24, "hand_stall_pay");
        shotAt(26, "hand_stall_take");
        shotAt(24, "hand_stall_eat");
        run(60, c -> {});
        // ---------------- дверь, касса, колонка, ремонт, сейф, сигарета, бутылка (на месте)
        station(() -> door, 0.6, 2.2, "door");
        click(8, () -> door);
        shotAt(5, "hand_door");
        run(20, c -> {});
        for (Object[] k : new Object[][]{{HandKind.CHECKOUT, "card", "hand_checkout", 30}, {HandKind.CHECKOUT, "cash", "hand_checkout_cash", 30},
                {HandKind.FUEL, "", "hand_fuel", 60}, {HandKind.REPAIR, "", "hand_repair", 60}, {HandKind.SAFE, "1", "hand_safe", 30},
                {HandKind.SMOKE, "", "hand_smoke", 64}, {HandKind.DRINK, "beer", "hand_drink", 30}}) {
            HandKind kind = (HandKind) k[0];
            server(4, s -> {
                ServerPlayerEntity p = pl(s);
                p.teleport(p.getServerWorld(), base.x + 0.5, base.y, base.z + 3.5, 180, 10);
                Hands.start(p, kind, (String) k[1], null, null);
            });
            run(3, c -> aim(c, new Vec3d(base.x + 0.5, base.y, base.z + 3.5), -2.4, 1.1));
            shotAt((Integer) k[3], (String) k[2]);
            run(kind.len - (Integer) k[3] + 6, c -> {});
        }
        // ---------------- оружие: перезарядки (третье лицо), бита, нож, кулак
        for (String gun : new String[]{"pistol", "smg", "rifle", "shotgun", "sniper"}) {
            server(4, s -> {
                ServerPlayerEntity p = pl(s);
                p.getInventory().clear();
                var item = net.minecraft.registry.Registries.ITEM.get(Novograd.id(gun));
                ItemStack st = new ItemStack(item);
                st.set(com.yompix.novograd.gta.Arsenal.MAG, 0);
                p.getInventory().setStack(0, st);
                p.getInventory().setStack(1, new ItemStack(ModContent.AMMO, 64));
                p.getInventory().selectedSlot = 0;
                p.teleport(p.getServerWorld(), base.x + 0.5, base.y, base.z + 3.5, 160, 0);
            });
            run(14, c -> aim(c, new Vec3d(base.x + 0.5, base.y, base.z + 3.5), -2.2, 1.0));
            server(2, s -> {
                ServerPlayerEntity p = pl(s);
                if (p.getMainHandStack().getItem() instanceof com.yompix.novograd.item.GunItem g) g.reload(p.getServerWorld(), p, p.getMainHandStack(), true);
            });
            shotAt(gun.equals("shotgun") ? 24 : 14, "hand_reload_" + gun);
            run(gun.equals("shotgun") ? 70 : 40, c -> {});
        }
        for (String m : new String[]{"bat", "knife", ""}) {
            server(4, s -> {
                ServerPlayerEntity p = pl(s);
                p.getInventory().clear();
                if (!m.isEmpty()) p.getInventory().setStack(0, new ItemStack(net.minecraft.registry.Registries.ITEM.get(Novograd.id(m))));
            });
            run(8, c -> c.player.swingHand(Hand.MAIN_HAND));
            shotAt(3, "hand_melee_" + (m.isEmpty() ? "fist" : m));
            run(12, c -> c.player.swingHand(Hand.MAIN_HAND));
            run(14, c -> {});
        }
        // первое лицо: пистолет и дробовик
        for (String gun : new String[]{"pistol", "shotgun"}) {
            server(4, s -> {
                ServerPlayerEntity p = pl(s);
                var st = new ItemStack(net.minecraft.registry.Registries.ITEM.get(Novograd.id(gun)));
                st.set(com.yompix.novograd.gta.Arsenal.MAG, 0);
                p.getInventory().setStack(0, st);
                p.getInventory().setStack(1, new ItemStack(ModContent.AMMO, 64));
            });
            run(4, c -> {
                CAM = null;
                c.options.setPerspective(Perspective.FIRST_PERSON);
                c.options.hudHidden = false;
            });
            server(10, s -> {
                ServerPlayerEntity p = pl(s);
                if (p.getMainHandStack().getItem() instanceof com.yompix.novograd.item.GunItem g) g.reload(p.getServerWorld(), p, p.getMainHandStack(), true);
            });
            shotAt(12, "hand_fp_reload_" + gun);
            run(60, c -> {});
        }
        run(10, c -> {
            CAM = null;
            Novograd.LOG.info("[hands] done {} cancel {}", Hands.doneCount, Hands.cancelCount);
        });
        finish();
    }

    // ================================================================== походка: игрок и жители (до/после — -Dnovograd.oldgait)
    static void gaitshow() {
        cmd(5, "time set 6000");
        cmd(5, "weather clear");
        cmd(5, "gamerule doDaylightCycle false");
        java.util.List<UUID> crowd = new java.util.ArrayList<>();
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            base = studio(p);
            var w = p.getServerWorld();
            BlockPos b = BlockPos.ofFloored(base);
            // лестница: 4 ступени вверх по +x на дорожке z = b.z+2
            for (int i = 0; i < 4; i++)
                for (int j = 0; j <= i; j++) w.setBlockState(b.add(8 + i, j, 2), j == i ? Blocks.SMOOTH_QUARTZ_STAIRS.getDefaultState()
                        .with(net.minecraft.block.StairsBlock.FACING, Direction.EAST) : Blocks.SMOOTH_QUARTZ.getDefaultState());
            for (int i = 0; i < 6; i++) w.setBlockState(b.add(12 + i, 3, 2), Blocks.SMOOTH_QUARTZ.getDefaultState());
            var d = Life.get(p);
            d.look = com.yompix.novograd.life.LookGen.generate(31, true, com.yompix.novograd.life.LookGen.CASUAL, false);
            d.look.hair = "ponytail";
            d.look.hat = "";
            d.look.sanitize();
            Life.sync(p);
            com.yompix.novograd.life.Style.broadcast(p);
            p.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
            p.getAbilities().flying = false;
            p.sendAbilitiesUpdate();
            // толпа: 8 жителей стоят (дыхание, вес, взгляд)
            for (int i = 0; i < 8; i++) {
                var c = com.yompix.novograd.gta.FunUtil.citizen(w, base.add(-6 + (i % 4) * 1.6, 0, -3 - (i / 4) * 1.6), com.yompix.novograd.entity.CitizenEntity.Role.CITIZEN, null);
                c.setYaw(180);
                c.setHeadYaw(180);
                c.setBodyYaw(180);
                c.setStationary(true);
                crowd.add(c.getUuid());
                SPAWNED.add(c.getUuid());
            }
        });
        run(100, c -> {
            if (c.currentScreen != null) c.setScreen(null);
            c.options.hudHidden = true;
        });
        run(20, ShowAutotest::rec);
        // ---- игрок: шаг, бег, стоп, лестница (камера сбоку, едет рядом)
        server(2, s -> pl(s).teleport(pl(s).getServerWorld(), base.x - 9, base.y, base.z + 2.5, -90, 0));
        run(4, c -> {
            c.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            perTick = () -> {
                var pl = MinecraftClient.getInstance().player;
                if (pl != null && CAM != null && CAM.length > 5) {
                    CAM[0] = (float) pl.getX() + 0.6f;
                    CAM[1] = (float) pl.getY() + 1.1f;
                    CAM[2] = (float) pl.getZ() + 4.2f;
                }
            };
            CAM = new float[]{0, 0, 0, 175, 4, 1};
        });
        label(1, "gait walk");
        run(10, c -> { c.player.setYaw(-90); key(c, c.options.forwardKey, true); });
        run(30, c -> shot(c, "gait_walk"));
        run(5, c -> c.player.setSprinting(true));
        run(14, c -> { c.player.setSprinting(true); shot(c, "gait_run"); });
        run(6, c -> key(c, c.options.forwardKey, false));
        run(4, c -> shot(c, "gait_stop"));
        run(30, c -> {});
        // лестница: от подножия вверх
        server(2, s -> pl(s).teleport(pl(s).getServerWorld(), base.x + 5, base.y, base.z + 2.5, -90, 0));
        run(4, c -> CAM = new float[]{0, 0, 0, 175, 6, 1});
        run(10, c -> { c.player.setYaw(-90); key(c, c.options.forwardKey, true); });
        run(12, c -> shot(c, "gait_stairs"));
        run(26, c -> key(c, c.options.forwardKey, false));
        run(20, c -> {});
        // ---- толпа стоит; потом идёт
        run(4, c -> CAM = new float[]{(float) base.x - 3.6f, (float) base.y + 1.7f, (float) base.z + 1.2f, 180, 10});
        server(2, s -> pl(s).teleport(pl(s).getServerWorld(), base.x - 3.6, base.y, base.z + 8, 180, 0));
        run(80, c -> shot(c, "gait_crowd_idle"));
        server(2, s -> {
            var w = s.getOverworld();
            for (UUID u : crowd)
                if (w.getEntity(u) instanceof com.yompix.novograd.entity.CitizenEntity ce)
                {
                    ce.setStationary(false);
                    ce.getNavigation().startMovingTo(ce.getX() + 10, ce.getY(), ce.getZ() + 1, 0.9);
                }
        });
        run(30, c -> shot(c, "gait_crowd_walk"));
        run(40, c -> {});
        run(5, c -> {
            CAM = null;
            Novograd.LOG.info("[gait] citizen layer: {} calls, {} ns/call", com.yompix.novograd.client.anim.CitizenLife.calls,
                    com.yompix.novograd.client.anim.CitizenLife.calls == 0 ? 0 : com.yompix.novograd.client.anim.CitizenLife.nanos / com.yompix.novograd.client.anim.CitizenLife.calls);
        });
        finish();
    }

    // ================================================================== «Сити-Банк»: снимки снаружи и внутри
    static void bankshow() {
        cmd(5, "time set 6000");
        cmd(5, "weather clear");
        cmd(5, "gamerule doDaylightCycle false");
        int X = 480, Z = -336;
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            p.changeGameMode(net.minecraft.world.GameMode.SPECTATOR);
            p.teleport(s.getOverworld(), X + 0.5, com.yompix.novograd.world.Island.G + 30, Z + 40, 180, 10);
        });
        run(100, c -> {
            if (c.currentScreen != null) c.setScreen(null);
            c.options.hudHidden = true;
            c.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        });
        // ждём загрузку участка и (в старом мире) перестройку
        for (int i = 0; i < 90; i++) server(40, s -> Novograd.LOG.info("[bank] {}", com.yompix.novograd.world.BankBuilding.status(s)));
        String tag = Boolean.getBoolean("novograd.nobank") ? "before" : "after";
        int F = com.yompix.novograd.world.Island.G + 2;
        float[][] views = {
                {X + 0.5f, F + 4, Z + 38, 180, -8}, {X + 24, F + 7, Z + 34, 145, -6}, {X + 0.5f, F + 14, Z + 75, 180, -18},
                {X + 0.5f, F + 5, Z + 11.5f, 180, 12}, {X + 9.5f, F + 3.2f, Z + 10.5f, 210, 18}, {X - 11.5f, F + 2.6f, Z + 18.5f, 180, 8},
                {X - 4, F + 4, Z + 4, 90, 18}, {X + 0.5f, F + 3, Z - 1.5f, 180, 0}};
        String[] names = {"front", "corner", "tower", "lobby", "office", "atm_bay", "waiting", "vault"};
        for (int i = 0; i < views.length; i++) {
            float[] v = views[i];
            String n = names[i];
            server(2, s -> pl(s).teleport(s.getOverworld(), v[0], v[1] - 1.6, v[2] + 3, v[3], v[4]));
            run(30, c -> CAM = new float[]{v[0], v[1], v[2], v[3], v[4]});
            run(30, c -> shot(c, "bank_" + tag + "_" + n));
        }
        run(5, c -> CAM = null);
        finish();
    }

    /** Подойти к месту у блока и поставить камеру сбоку (side — вправо/влево, dist — от игрока). */
    private static void station(java.util.function.Supplier<BlockPos> at, double dist, double side, String name) {
        server(4, s -> {
            BlockPos pos = at.get();
            ServerPlayerEntity p = pl(s);
            p.teleport(p.getServerWorld(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 2.4, 180, 10);
        });
        run(4, c -> aim(c, new Vec3d(at.get().getX() + 0.5, at.get().getY(), at.get().getZ() + 1.1), dist, side));
        label(1, "станция " + name);
    }

    /** Камера показа (ставит ShowCameraMixin в виде от 3-го лица): x, y, z, yaw, pitch; null — обычная. */
    public static float[] CAM;

    /** Камера сбоку и чуть спереди от точки, смотрит на неё. */
    private static void aim(MinecraftClient c, Vec3d at, double dist, double side) {
        Vec3d eye = at.add(side, 1.45, dist);
        Vec3d d = at.add(0, 1.0, 0).subtract(eye);
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z)));
        CAM = new float[]{(float) eye.x, (float) eye.y, (float) eye.z, yaw, pitch};
        thirdPerson(c);
    }

    private static void thirdPerson(MinecraftClient c) {
        c.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        c.options.hudHidden = true;
    }

    private static void firstPerson(java.util.function.Supplier<BlockPos> at, String name) {
        server(4, s -> {
            BlockPos pos = at.get();
            ServerPlayerEntity p = pl(s);
            p.teleport(p.getServerWorld(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 2.0, 180, 25);
        });
        run(4, c -> {
            CAM = null;
            c.options.setPerspective(Perspective.FIRST_PERSON);
            c.options.hudHidden = false;
        });
        label(1, "первое лицо " + name);
    }

    private static void click(int delay, java.util.function.Supplier<BlockPos> at) {
        run(delay, c -> {
            BlockPos p = at.get();
            BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(p).add(0, 0.2, 0.5), Direction.SOUTH, p, false);
            c.interactionManager.interactBlock(c.player, Hand.MAIN_HAND, hit);
        });
    }

    private static void shotAt(int delay, String name) {
        run(delay, c -> shot(c, name));
    }
}
