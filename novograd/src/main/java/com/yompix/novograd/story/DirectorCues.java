package com.yompix.novograd.story;

import com.google.gson.JsonArray;
import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.life.LifeNet;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * Дополнительные реплики режиссёра кат-сцены (Director отдаёт сюда всё, что сам не знает):
 *
 *   sfx    кто|место звук [громкость] [тон]   — звук в точке: «minecraft:block.wooden_door.open», «novograd:car_horn»
 *   music  звук|stop [громкость]              — музыка сцены (radio.jazz_polnoch_1 …), играет у клиента и стихает в конце сцены
 *   hold   кто предмет|none                    — взять вещь в руку (конверт, телефон, ствол)
 *   give   кто кому предмет                    — передать вещь: у первого рука пустеет, у второго — вещь, оба смотрят друг на друга
 *   door   место open|close                    — открыть/закрыть ближайшую дверь (до 4 блоков)
 *   anim   кто kick|hit|highfive|drag          — короткое движение: удар по столу/двери, отшатнуться, «дай пять»
 *   turn   кто место                           — повернуться к точке
 *   tp     кто место                           — переставить актёра (смена места действия между планами)
 *   glass  место                               — звон и осколки стекла
 *   flash  место                               — вспышка фотоаппарата/выстрела (без урона)
 */
final class DirectorCues {
    private DirectorCues() {}

    /** true — реплика наша и выполнена. */
    static boolean handle(ServerPlayerEntity p, JsonArray a, Function<String, Entity> actor) {
        ServerWorld w = p.getServerWorld();
        String kind = a.get(0).getAsString();
        switch (kind) {
            case "sfx" -> {
                Vec3d at = where(w, a.get(1).getAsString(), actor);
                Identifier id = Identifier.tryParse(a.get(2).getAsString());
                if (at == null || id == null) return true;
                float vol = a.size() > 3 ? a.get(3).getAsFloat() : 1.5f, pitch = a.size() > 4 ? a.get(4).getAsFloat() : 1f;
                SoundEvent ev = Registries.SOUND_EVENT.containsId(id) ? Registries.SOUND_EVENT.get(id) : SoundEvent.of(id);
                w.playSound(null, at.x, at.y + 1, at.z, ev, SoundCategory.NEUTRAL, vol, pitch);
            }
            case "music" -> {
                NbtCompound n = new NbtCompound();
                n.putString("id", a.get(1).getAsString());
                n.putFloat("vol", a.size() > 2 ? a.get(2).getAsFloat() : 0.35f);
                LifeNet.send(p, "cut_music", n);
            }
            case "hold" -> {
                if (actor.apply(a.get(1).getAsString()) instanceof CitizenEntity c) c.equipStack(EquipmentSlot.MAINHAND, stack(a.get(2).getAsString()));
            }
            case "give" -> {
                Entity from = actor.apply(a.get(1).getAsString()), to = actor.apply(a.get(2).getAsString());
                ItemStack s = stack(a.size() > 3 ? a.get(3).getAsString() : "minecraft:paper");
                if (from instanceof CitizenEntity c) {
                    c.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                    if (to != null) c.face(to);
                    c.triggerAnim("action", "highfive");
                }
                if (to instanceof CitizenEntity c) {
                    c.equipStack(EquipmentSlot.MAINHAND, s);
                    if (from != null) c.face(from);
                }
                Entity e = to != null ? to : from;
                if (e != null) w.playSound(null, e.getBlockPos(), net.minecraft.sound.SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.NEUTRAL, 0.8f, 0.8f);
            }
            case "door" -> {
                Vec3d at = where(w, a.get(1).getAsString(), actor);
                if (at == null) return true;
                boolean open = a.size() < 3 || a.get(2).getAsString().equals("open");
                BlockPos c = BlockPos.ofFloored(at);
                BlockPos best = null;
                double bd = 1e9;
                for (BlockPos q : BlockPos.iterate(c.add(-4, -2, -4), c.add(4, 2, 4))) {
                    BlockState st = w.getBlockState(q);
                    if (st.getBlock() instanceof DoorBlock && q.getSquaredDistance(at) < bd) {
                        bd = q.getSquaredDistance(at);
                        best = q.toImmutable();
                    }
                }
                if (best != null && w.getBlockState(best).getBlock() instanceof DoorBlock d) d.setOpen(null, w, w.getBlockState(best), best, open);
                else w.playSound(null, c, open ? net.minecraft.sound.SoundEvents.BLOCK_WOODEN_DOOR_OPEN : net.minecraft.sound.SoundEvents.BLOCK_WOODEN_DOOR_CLOSE, SoundCategory.BLOCKS, 1.2f, 1f);
            }
            case "anim" -> {
                if (actor.apply(a.get(1).getAsString()) instanceof CitizenEntity c) c.triggerAnim("action", a.get(2).getAsString());
            }
            case "turn" -> {
                Vec3d at = where(w, a.get(2).getAsString(), actor);
                if (at != null && actor.apply(a.get(1).getAsString()) instanceof CitizenEntity c) c.faceTo(at);
            }
            case "tp" -> {
                Entity e = actor.apply(a.get(1).getAsString());
                Vec3d at = Director.pos(w, a.get(2).getAsString());
                if (e == null || at == null) return true;
                w.getChunk(BlockPos.ofFloored(at));
                if (e instanceof CitizenEntity c) {
                    c.directTo = null;
                    if (c.hasVehicle()) c.stopRiding();
                    c.refreshPositionAndAngles(at.x, Story.ground(w, at).getY(), at.z, c.getYaw(), 0);
                    c.setStationary(true);
                } else e.refreshPositionAndAngles(at.x, Story.ground(w, at).getY(), at.z, e.getYaw(), 0);
            }
            case "glass" -> {
                Vec3d at = where(w, a.get(1).getAsString(), actor);
                if (at == null) return true;
                w.spawnParticles(new net.minecraft.particle.BlockStateParticleEffect(ParticleTypes.BLOCK, net.minecraft.block.Blocks.GLASS.getDefaultState()),
                        at.x, at.y + 1.2, at.z, 40, 0.5, 0.5, 0.5, 0.2);
                w.playSound(null, at.x, at.y + 1, at.z, net.minecraft.sound.SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.BLOCKS, 1.6f, 0.9f);
            }
            case "flash" -> {
                Vec3d at = where(w, a.get(1).getAsString(), actor);
                if (at == null) return true;
                w.spawnParticles(ParticleTypes.FLASH, at.x, at.y + 1.4, at.z, 1, 0, 0, 0, 0);
                w.spawnParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 1.4, at.z, 8, 0.2, 0.2, 0.2, 0.1);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    /** Актёр сцены (его позиция) или место. */
    @Nullable
    private static Vec3d where(ServerWorld w, String spec, Function<String, Entity> actor) {
        Entity e = actor.apply(spec);
        return e != null ? e.getPos() : Director.pos(w, spec);
    }

    private static ItemStack stack(String id) {
        if (id.equals("none")) return ItemStack.EMPTY;
        Identifier i = Identifier.tryParse(id.contains(":") ? id : "minecraft:" + id);
        if (i == null || !Registries.ITEM.containsId(i)) return ItemStack.EMPTY;
        Item it = Registries.ITEM.get(i);
        return new ItemStack(it);
    }
}
