package com.yompix.novograd.world;

import com.yompix.novograd.Novograd;
import com.yompix.novograd.block.BillboardBlock;
import com.yompix.novograd.block.ElevatorFloorBlock;
import com.yompix.novograd.block.ElevatorPanelBlock;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.math.Direction;

/** Блоки, которые ставит генератор города: лифт (панель и пол кабины) и рекламные щиты. Регистрируются из {@link Elevators#init}. */
public final class CityBlocks {
    private CityBlocks() {}

    public static final Block ELEVATOR_PANEL = block("elevator_panel", new ElevatorPanelBlock(AbstractBlock.Settings.create().mapColor(MapColor.IRON_GRAY)
            .strength(-1f, 3600000f).sounds(BlockSoundGroup.METAL).luminance(s -> 7).dropsNothing()));
    public static final Block ELEVATOR_FLOOR = block("elevator_floor", new ElevatorFloorBlock(AbstractBlock.Settings.create().mapColor(MapColor.IRON_GRAY)
            .strength(-1f, 3600000f).sounds(BlockSoundGroup.METAL).luminance(s -> 9).dropsNothing()));
    public static final Block BILLBOARD = block("billboard", new BillboardBlock(AbstractBlock.Settings.create().mapColor(MapColor.BLACK)
            .strength(2f, 6f).sounds(BlockSoundGroup.METAL).luminance(s -> 11).dropsNothing()));

    static void init() {}

    private static Block block(String name, Block b) {
        Registry.register(Registries.BLOCK, Novograd.id(name), b);
        Registry.register(Registries.ITEM, Novograd.id(name), new BlockItem(b, new Item.Settings()));
        return b;
    }

    /** Кусок щита: реклама ad, столбец col (0 — слева для зрителя), ряд row (0 — верх). */
    public static BlockState billboard(int ad, Direction face, int col, int row) {
        return BILLBOARD.getDefaultState().with(BillboardBlock.FACING, face).with(BillboardBlock.AD, ad).with(BillboardBlock.TILE, row * BillboardBlock.W + col);
    }
}
