package com.yompix.lifecraft.block;

import com.yompix.lifecraft.ModContent;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Телевизор: нажатие листает каналы, присесть + нажать — выключить. Картинка анимируется текстурой. */
public class TvBlock extends FacingBlock {
    public static final IntProperty CHANNEL = IntProperty.of("channel", 0, 5);
    private static final String[] NAMES = {"", "Новости 24", "Мультики", "Мир природы", "Музыка", "Спорт"};

    public TvBlock(Settings settings) {
        super(settings, box(0, 0, 6, 16, 14, 10));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder);
        builder.add(CHANNEL);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        int ch = player.isSneaking() ? 0 : state.get(CHANNEL) % 5 + 1;
        if (!world.isClient) {
            world.setBlockState(pos, state.with(CHANNEL, ch));
            if (ch > 0 && player instanceof net.minecraft.server.network.ServerPlayerEntity sp) com.yompix.lifecraft.life.Achievements.give(sp, "tv");
            world.playSound(null, pos, ModContent.TV_SWITCH, SoundCategory.BLOCKS, 0.6f, 1f);
            player.sendMessage(Text.literal(ch == 0 ? "📺 Телевизор выключен" : "📺 Канал " + ch + ": " + NAMES[ch]), true);
        }
        return ActionResult.success(world.isClient);
    }
}
