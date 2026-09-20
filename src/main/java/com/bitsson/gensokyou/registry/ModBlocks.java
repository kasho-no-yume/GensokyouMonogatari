package com.bitsson.gensokyou.registry;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.block.CrystalBlock;
import com.bitsson.gensokyou.block.RitualCoreBlock;
import com.bitsson.gensokyou.block.RitualPedestalBlock;
import com.bitsson.gensokyou.block.SukimaBlock;
import com.bitsson.gensokyou.block.TieredBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.WallBlock;
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
    /** 祭品台：单一方块，tier BlockState 属性驱动变色（品阶视觉由核心重扫写入）。 */
    public static final DeferredBlock<RitualPedestalBlock> RITUAL_PEDESTAL =
            BLOCKS.registerBlock("ritual_pedestal", RitualPedestalBlock::new, stoneProperties());
    /** 仪式石台阶：装饰变种，按品阶独立注册 ritual_stone_slab_0..5。 */
    public static final List<DeferredBlock<SlabBlock>> RITUAL_STONE_SLABS = new ArrayList<>();
    /** 仪式石楼梯：装饰变种，按品阶独立注册 ritual_stone_stairs_0..5。 */
    public static final List<DeferredBlock<StairBlock>> RITUAL_STONE_STAIRS = new ArrayList<>();
    /** 仪式石墙：装饰变种，按品阶独立注册 ritual_stone_wall_0..5。 */
    public static final List<DeferredBlock<WallBlock>> RITUAL_STONE_WALLS = new ArrayList<>();

    static {
        for (int i = 0; i < TIER_COUNT; i++) {
            final int tier = i;
            RITUAL_STONES.add(BLOCKS.registerBlock("ritual_stone_" + i,
                    properties -> new TieredBlock(properties, tier),
                    stoneProperties()));
        }
        // 装饰变种：直接用原版方块类，品阶只体现在注册名与贴图；tierOf() 不感知（不参与仪式匹配）
        for (int i = 0; i < TIER_COUNT; i++) {
            final int tier = i;
            RITUAL_STONE_SLABS.add(BLOCKS.registerBlock("ritual_stone_slab_" + tier,
                    SlabBlock::new, stoneProperties()));
            RITUAL_STONE_STAIRS.add(BLOCKS.registerBlock("ritual_stone_stairs_" + tier,
                    properties -> new StairBlock(RITUAL_STONES.get(tier).get().defaultBlockState(), properties),
                    stoneProperties()));
            RITUAL_STONE_WALLS.add(BLOCKS.registerBlock("ritual_stone_wall_" + tier,
                    properties -> new WallBlock(properties.forceSolidOn()), stoneProperties()));
        }
    }

    /** 仪式石族的共用属性：与仪式石本体保持一致。 */
    private static BlockBehaviour.Properties stoneProperties() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.DIAMOND).strength(1.5F, 6F);
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

    /**
     * 无尽藏晶：仪式产物与托管载体。本体空模型，1.5 格高正菱形水晶由 BER 绘制、可穿行。
     * 不可破坏、无掉落（玩家/爆炸均无法摧毁）；仪式经 setBlock/removeBlock 仍可移除。
     */
    public static final DeferredBlock<CrystalBlock> CRYSTAL =
            BLOCKS.registerBlock("crystal", CrystalBlock::new,
                    BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN)
                            .strength(-1.0F, 3600000.0F).noLootTable()
                            .sound(SoundType.AMETHYST_CLUSTER)
                            .lightLevel(state -> 12).noOcclusion());

    /** 查询方块的仪式品阶；非品阶方块返回 -1（如仪式核心、单方块化后的祭品台）。 */
    public static int tierOf(Block block) {
        if (block instanceof TieredBlock tiered) {
            return tiered.tier();
        }
        return -1;
    }
}
