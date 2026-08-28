package com.bitsson.gensokyou.registry;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.block.RitualCoreBlock;
import com.bitsson.gensokyou.block.RitualPedestalBlock;
import com.bitsson.gensokyou.block.SukimaBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(Gensokyou.MODID);

    public static final DeferredBlock<Block> RITUAL_STONE =
            BLOCKS.registerSimpleBlock("ritual_stone",
                    BlockBehaviour.Properties.of().mapColor(MapColor.DIAMOND).strength(1.5F, 6F));

    /** 全 mod 唯一的主仪式方块：不同仪式由多方块结构区分。 */
    public static final DeferredBlock<RitualCoreBlock> RITUAL_CORE =
            BLOCKS.registerBlock("ritual_core", RitualCoreBlock::new,
                    BlockBehaviour.Properties.of().mapColor(MapColor.DIAMOND).strength(3F, 1200F));

    /** 隙间传送门：由结界仪式行为管理，不可摧毁、可穿行（接触即传送）、无物品形态。 */
    public static final DeferredBlock<SukimaBlock> SUKIMA =
            BLOCKS.registerBlock("sukima", SukimaBlock::new,
                    BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE)
                            .strength(-1.0F, 3600000.0F).noLootTable()
                            .noCollission());

    public static final DeferredBlock<RitualPedestalBlock> RITUAL_PEDESTAL =
            BLOCKS.registerBlock("ritual_pedestal", RitualPedestalBlock::new,
                    BlockBehaviour.Properties.of().mapColor(MapColor.DIAMOND).strength(1.5F, 6F));
}
