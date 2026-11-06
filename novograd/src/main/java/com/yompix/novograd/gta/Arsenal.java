package com.yompix.novograd.gta;

import com.mojang.serialization.Codec;
import com.yompix.novograd.ModContent;
import com.yompix.novograd.Novograd;
import com.yompix.novograd.entity.GrenadeEntity;
import com.yompix.novograd.item.GunItem;
import com.yompix.novograd.item.MedkitItem;
import com.yompix.novograd.item.ThrowableItem;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.component.ComponentType;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterials;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Rarity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.EnumMap;
import java.util.List;

/**
 * Арсенал: дробовик, «АК», снайперка, гранаты, молотов, нож, бронежилет, аптечка.
 * Магазин патронов хранится в самом оружии (компонент novograd:mag), перезарядка — R пешком или сама.
 */
public final class Arsenal {
    private Arsenal() {}

    // ---------- звуки ----------
    public static final SoundEvent SHOTGUN_SHOT = sound("shotgun_shot");
    public static final SoundEvent RIFLE_SHOT = sound("rifle_shot");
    public static final SoundEvent SNIPER_SHOT = sound("sniper_shot");
    public static final SoundEvent RELOAD = sound("reload");
    public static final SoundEvent GUN_DRY = sound("gun_dry");
    // детали перезарядки и отклик — играются у самого стрелка по кадрам анимации (client/gun)
    public static final SoundEvent MAG_OUT = sound("gun_mag_out");
    public static final SoundEvent MAG_IN = sound("gun_mag_in");
    public static final SoundEvent SLIDE_BACK = sound("gun_slide_back");
    public static final SoundEvent SLIDE_RELEASE = sound("gun_slide_release");
    public static final SoundEvent BOLT_BACK = sound("gun_bolt_back");
    public static final SoundEvent BOLT_FWD = sound("gun_bolt_fwd");
    public static final SoundEvent SHELL_IN = sound("gun_shell_in");
    public static final SoundEvent PUMP = sound("gun_pump");
    public static final SoundEvent HANDLING = sound("gun_handling");
    public static final SoundEvent HIT_MARKER = sound("hit_marker");
    public static final SoundEvent HIT_KILL = sound("hit_kill");
    public static final SoundEvent CASING = sound("casing");
    public static final SoundEvent CASING_SHELL = sound("casing_shell");
    private static final java.util.Map<String, SoundEvent> RELOADS = new java.util.HashMap<>();

    static {
        for (String g : List.of("pistol", "smg", "shotgun", "rifle", "sniper")) RELOADS.put(g, sound(g + "_reload"));
    }

    /** Звук перезарядки своего ствола (магазин, затвор; у дробовика — патроны по одному). */
    public static SoundEvent reloadSound(Item gun) {
        return RELOADS.getOrDefault(Registries.ITEM.getId(gun).getPath(), RELOAD);
    }
    public static final RegistryEntry.Reference<SoundEvent> EXPLOSION = Registry.registerReference(Registries.SOUND_EVENT, Novograd.id("explosion"), SoundEvent.of(Novograd.id("explosion")));

    // ---------- патроны в магазине ----------
    public static final ComponentType<Integer> MAG = Registry.register(Registries.DATA_COMPONENT_TYPE, Novograd.id("mag"),
            ComponentType.<Integer>builder().codec(Codec.INT).packetCodec(PacketCodecs.VAR_INT).build());

    // ---------- бронежилет ----------
    public static final RegistryEntry<ArmorMaterial> KEVLAR = Registry.registerReference(Registries.ARMOR_MATERIAL, Novograd.id("kevlar"),
            new ArmorMaterial(net.minecraft.util.Util.make(new EnumMap<>(ArmorItem.Type.class), m -> {
                m.put(ArmorItem.Type.BOOTS, 0);
                m.put(ArmorItem.Type.LEGGINGS, 0);
                m.put(ArmorItem.Type.CHESTPLATE, 8);
                m.put(ArmorItem.Type.HELMET, 0);
                m.put(ArmorItem.Type.BODY, 0);
            }), 1, SoundEvents.ITEM_ARMOR_EQUIP_LEATHER, () -> Ingredient.EMPTY,
                    List.of(new ArmorMaterial.Layer(Novograd.id("kevlar"))), 2.0f, 0.1f));

    // ---------- предметы ----------
    public static final Item SHOTGUN = item("shotgun", new GunItem(new Item.Settings().maxCount(1),
            new GunItem.Spec(3.2f, 18, false, 8, 0.16, 16, 6, 55, 5f, SHOTGUN_SHOT, 1.4f, false)));
    public static final Item RIFLE = item("rifle", new GunItem(new Item.Settings().maxCount(1),
            new GunItem.Spec(5f, 4, true, 1, 0.03, 64, 30, 45, 0.9f, RIFLE_SHOT, 1.1f, false)));
    public static final Item SNIPER = item("sniper", new GunItem(new Item.Settings().maxCount(1).rarity(Rarity.UNCOMMON),
            new GunItem.Spec(28f, 32, false, 1, 0.06, 180, 5, 60, 6f, SNIPER_SHOT, 1.6f, true)));
    public static final Item GRENADE = item("grenade", new ThrowableItem(new Item.Settings().maxCount(8), false));
    public static final Item MOLOTOV = item("molotov", new ThrowableItem(new Item.Settings().maxCount(8), true));
    public static final Item KNIFE = item("knife", new SwordItem(ToolMaterials.IRON,
            new Item.Settings().attributeModifiers(SwordItem.createAttributeModifiers(ToolMaterials.IRON, 3, -1.6f))));
    public static final Item ARMOR = item("body_armor", new ArmorItem(KEVLAR, ArmorItem.Type.CHESTPLATE,
            new Item.Settings().maxCount(1).maxDamage(ArmorItem.Type.CHESTPLATE.getMaxDamage(4))));
    public static final Item MEDKIT = item("medkit", new MedkitItem(new Item.Settings().maxCount(8)));

    // ---------- брошенные гранаты ----------
    public static final EntityType<GrenadeEntity> GRENADE_ENTITY = Registry.register(Registries.ENTITY_TYPE, Novograd.id("grenade"),
            EntityType.Builder.<GrenadeEntity>create(GrenadeEntity::new, SpawnGroup.MISC).dimensions(0.25f, 0.25f).maxTrackingRange(6).trackingTickInterval(2).build("grenade"));
    public static final EntityType<GrenadeEntity> MOLOTOV_ENTITY = Registry.register(Registries.ENTITY_TYPE, Novograd.id("molotov"),
            EntityType.Builder.<GrenadeEntity>create(GrenadeEntity::new, SpawnGroup.MISC).dimensions(0.25f, 0.25f).maxTrackingRange(6).trackingTickInterval(2).build("molotov"));

    /** Все виды оружия для колеса и проверок (в порядке колеса). */
    public static List<Item> weapons() {
        return List.of(ModContent.BAT, KNIFE, ModContent.PISTOL, ModContent.SMG, SHOTGUN, RIFLE, SNIPER, GRENADE, MOLOTOV);
    }

    public static boolean isWeapon(ItemStack s) {
        return !s.isEmpty() && (s.getItem() instanceof GunItem || s.getItem() instanceof ThrowableItem || s.isOf(KNIFE) || s.isOf(ModContent.BAT));
    }

    /**
     * Сервер → клиенты: выстрел (вспышка, трассеры, гильзы, искры; стрелку — хитмаркер).
     * ends — концы лучей (x, y, z подряд), what — что задели: 0 мимо, 1 блок (биты 2..4 — грань), 2 существо; hit — 0/1 попал/2 убил.
     */
    public record ShotFx(int shooter, int kind, float fx, float fy, float fz, float[] ends, byte[] what, byte hit) implements CustomPayload {
        public static final Id<ShotFx> ID = new Id<>(Novograd.id("shot_fx"));
        public static final PacketCodec<RegistryByteBuf, ShotFx> CODEC = PacketCodec.of((v, buf) -> {
            buf.writeVarInt(v.shooter);
            buf.writeByte(v.kind);
            buf.writeFloat(v.fx);
            buf.writeFloat(v.fy);
            buf.writeFloat(v.fz);
            buf.writeVarInt(v.what.length);
            for (int i = 0; i < v.what.length; i++) {
                buf.writeFloat(v.ends[i * 3]);
                buf.writeFloat(v.ends[i * 3 + 1]);
                buf.writeFloat(v.ends[i * 3 + 2]);
                buf.writeByte(v.what[i]);
            }
            buf.writeByte(v.hit);
        }, buf -> {
            int shooter = buf.readVarInt(), kind = buf.readByte();
            float fx = buf.readFloat(), fy = buf.readFloat(), fz = buf.readFloat();
            int n = Math.min(64, buf.readVarInt());
            float[] ends = new float[n * 3];
            byte[] what = new byte[n];
            for (int i = 0; i < n; i++) {
                ends[i * 3] = buf.readFloat();
                ends[i * 3 + 1] = buf.readFloat();
                ends[i * 3 + 2] = buf.readFloat();
                what[i] = buf.readByte();
            }
            return new ShotFx(shooter, kind, fx, fy, fz, ends, what, buf.readByte());
        });

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Сервер → клиенты: игрок entity целится (ПКМ) или опустил оружие — поза от третьего лица. */
    public record AimS2C(int entity, boolean aiming) implements CustomPayload {
        public static final Id<AimS2C> ID = new Id<>(Novograd.id("aim"));
        public static final PacketCodec<RegistryByteBuf, AimS2C> CODEC = PacketCodec.tuple(
                PacketCodecs.VAR_INT, AimS2C::entity, PacketCodecs.BOOL, AimS2C::aiming, AimS2C::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Кто сейчас в прицеле (ПКМ) — кучнее стрельба. */
    private static final java.util.Set<java.util.UUID> AIMING = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static boolean aiming(PlayerEntity p) {
        return AIMING.contains(p.getUuid());
    }

    /** Разослать эффекты выстрела всем игрокам рядом (и самому стрелку). */
    public static void sendShotFx(ServerWorld w, net.minecraft.entity.LivingEntity shooter, net.minecraft.util.math.Vec3d from, int kind,
                                  float[] ends, byte[] what, byte hit) {
        ShotFx fx = new ShotFx(shooter.getId(), kind, (float) from.x, (float) from.y, (float) from.z, ends, what, hit);
        for (ServerPlayerEntity p : w.getPlayers())
            if (p == shooter || p.squaredDistanceTo(shooter) < 160 * 160) ServerPlayNetworking.send(p, fx);
    }

    /** Клиент → сервер: «reload» (R пешком), «wheel» (колесо оружия, value = слот), «fire» (ЛКМ, value = 1 в прицеле), «aim» (0/1). */
    public record C2S(String action, int value) implements CustomPayload {
        public static final Id<C2S> ID = new Id<>(Novograd.id("arsenal"));
        public static final PacketCodec<RegistryByteBuf, C2S> CODEC = PacketCodec.tuple(
                PacketCodecs.STRING, C2S::action, PacketCodecs.VAR_INT, C2S::value, C2S::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public static void register() {
        ItemGroupEvents.modifyEntriesEvent(RegistryKey.of(RegistryKeys.ITEM_GROUP, Novograd.id("main"))).register(e -> {
            for (Item i : List.of(KNIFE, SHOTGUN, RIFLE, SNIPER, GRENADE, MOLOTOV, ARMOR, MEDKIT)) e.add(i);
        });
        PayloadTypeRegistry.playC2S().register(C2S.ID, C2S.CODEC);
        PayloadTypeRegistry.playS2C().register(ShotFx.ID, ShotFx.CODEC);
        PayloadTypeRegistry.playS2C().register(AimS2C.ID, AimS2C.CODEC);
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((h, s) -> {
            AIMING.remove(h.player.getUuid());
            GunItem.forget(h.player.getUuid());
        });
        ServerPlayNetworking.registerGlobalReceiver(C2S.ID, (payload, ctx) -> handle(ctx.player(), payload.action(), payload.value()));
        // стрельба из машины: ПКМ (прицел) по «своей» машине не садит и не открывает её
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) ->
                player.hasVehicle() && player.isConnectedThroughVehicle(entity) && isWeapon(player.getStackInHand(hand)) ? ActionResult.FAIL : ActionResult.PASS);
        ServerTickEvents.END_WORLD_TICK.register(Arsenal::pickups);
    }

    private static void handle(ServerPlayerEntity p, String action, int value) {
        switch (action) {
            case "fire" -> {
                ItemStack s = p.getMainHandStack();
                if (s.getItem() instanceof GunItem g && p.isAlive() && !p.isSpectator()) g.trigger(p, s, (value & 1) != 0);
            }
            case "aim" -> {
                boolean on = value != 0 && p.getMainHandStack().getItem() instanceof GunItem;
                if (on ? AIMING.add(p.getUuid()) : AIMING.remove(p.getUuid())) {
                    AimS2C pkt = new AimS2C(p.getId(), on);
                    for (ServerPlayerEntity o : net.fabricmc.fabric.api.networking.v1.PlayerLookup.tracking(p)) ServerPlayNetworking.send(o, pkt);
                }
            }
            case "reload" -> {
                ItemStack s = p.getMainHandStack();
                if (s.getItem() instanceof GunItem g) g.reload(p.getServerWorld(), p, s, true);
            }
            case "wheel" -> {
                var inv = p.getInventory();
                if (value < 0) { // «кулаки»: пустой слот на панели
                    for (int i = 0; i < 9; i++)
                        if (inv.getStack(i).isEmpty()) {
                            select(p, i);
                            return;
                        }
                    return;
                }
                if (value >= inv.main.size() || !isWeapon(inv.getStack(value))) return;
                if (value < 9) {
                    select(p, value);
                } else {
                    int sel = inv.selectedSlot;
                    ItemStack a = inv.getStack(sel), b = inv.getStack(value);
                    inv.setStack(sel, b);
                    inv.setStack(value, a);
                    p.playerScreenHandler.sendContentUpdates();
                }
            }
            default -> {}
        }
    }

    private static void select(ServerPlayerEntity p, int slot) {
        p.getInventory().selectedSlot = slot;
        p.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket(slot));
    }

    /** Аптечки и бронежилеты иногда появляются на улицах (светятся, пропадают через 5 минут). */
    private static void pickups(ServerWorld w) {
        if (w.getRegistryKey() != World.OVERWORLD || w.getTime() % 1200 != 600) return;
        var r = w.getRandom();
        for (ServerPlayerEntity p : w.getPlayers()) {
            if (p.isSpectator() || p.isCreative() || r.nextFloat() > 0.6f) continue;
            var box = p.getBoundingBox().expand(80, 40, 80);
            if (w.getEntitiesByClass(ItemEntity.class, box, e -> e.getStack().isOf(MEDKIT) || e.getStack().isOf(ARMOR)).size() >= 3) continue;
            double ang = r.nextDouble() * Math.PI * 2, dist = 14 + r.nextDouble() * 30;
            int x = (int) Math.floor(p.getX() + Math.cos(ang) * dist), z = (int) Math.floor(p.getZ() + Math.sin(ang) * dist);
            if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
            int y = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (y <= w.getBottomY() + 1 || !w.getFluidState(new BlockPos(x, y - 1, z)).isEmpty()) continue;
            ItemEntity e = new ItemEntity(w, x + 0.5, y + 0.2, z + 0.5, new ItemStack(r.nextInt(3) == 0 ? ARMOR : MEDKIT));
            e.setVelocity(0, 0.1, 0);
            e.setGlowing(true);
            w.spawnEntity(e);
        }
    }

    /** Тратит патроны из инвентаря, сколько есть (до max). @return сколько взяли */
    public static int takeAmmo(PlayerEntity p, int max) {
        if (p.isCreative() || Cheats.infiniteAmmo(p)) return max;
        int got = 0;
        var inv = p.getInventory();
        for (int i = 0; i < inv.size() && got < max; i++) {
            var s = inv.getStack(i);
            if (s.isOf(ModContent.AMMO)) {
                int n = Math.min(max - got, s.getCount());
                s.decrement(n);
                got += n;
            }
        }
        return got;
    }

    private static SoundEvent sound(String name) {
        return Registry.register(Registries.SOUND_EVENT, Novograd.id(name), SoundEvent.of(Novograd.id(name)));
    }

    private static <I extends Item> I item(String name, I item) {
        return Registry.register(Registries.ITEM, Novograd.id(name), item);
    }
}
