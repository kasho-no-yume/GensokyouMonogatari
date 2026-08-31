package com.bitsson.gensokyou.registry;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.block.RitualCoreBlock;
import com.bitsson.gensokyou.block.RitualPedestalBlock;
import com.bitsson.gensokyou.block.SukimaBlock;
import com.bitsson.gensokyou.block.TieredBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(Gensokyou.MODID);

    /** 品阶数（0-5 灰/绿/蓝/金/红/紫）。 */
    public static final int TIER_COUNT = 6;

    /** 仪式石：按品阶独立注册 ritual_stone_0..5。 */
    public static final List<DeferredBlock<TieredBlock>> RITUAL_STONES = new ArrayList<>();
    /** 祭品台：按品阶独立注册 ritual_pedestal_0..5。 */
    public static final List<DeferredBlock<RitualPedestalBlock>> RITUAL_PEDESTALS = new ArrayList<>();

    static {
        for (int i = 0; i < TIER_COUNT; i++) {
            final int tier = i;
            RITUAL_STONES.add(BLOCKS.registerBlock("ritual_stone_" + i,
                    properties -> new TieredBlock(properties, tier),
                    BlockBehaviour.Properties.of().mapColor(MapColor.DIAMOND).strength(1.5F, 6F)));
            RITUAL_PEDESTALS.add(BLOCKS.registerBlock("ritual_pedestal_" + i,
                    properties -> new RitualPedestalBlock(properties, tier),
                    BlockBehaviour.Properties.of().mapColor(MapColor.DIAMOND).strength(1.5F, 6F)));
        }
    }

    /** 全 mod 唯一的主仪式方块：不同仪式由多方块结构区分，品阶视觉由 tier 属性驱动。 */
    public static final DeferredBlock<RitualCoreBlock> RITUAL_CORE =
            BLOCKS.registerBlock("ritual_core", RitualCoreBlock::new,
                    BlockBehaviour.Properties.of().mapColor(MapColor.DIAMOND).strength(3F, 1200F));

    /** 隙间传送门：由结界仪式行为管理，不可摧毁、可穿行（接触即传送）、无物品形态。 */
    public static final DeferredBlock<SukimaBlock> SUKIMA =
            BLOCKS.registerBlock("sukima", SukimaBlock::new,
                    BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE)
                            .strength(-1.0F, 3600000.0F).noLootTable()
                            .noCollission());

    /** 查询方块的仪式品阶；非品阶方块返回 -1（如仪式核心自身）。 */
    public static int tierOf(Block block) {
        if (block instanceof TieredBlock tiered) {
            return tiered.tier();
        }
        if (block instanceof RitualPedestalBlock pedestal) {
            return pedestal.tier();
        }
        return -1;
    }
}
