package com.bitsson.gensokyou.block;

import com.mojang.serialization.MapCodec;
import com.bitsson.gensokyou.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;

/**
 * 灵土：普通土色方块（可被锄成 {@link SpiritSoilFarmlandBlock}）。
 *
 * <p><b>为什么用 {@code getToolModifiedState} 而不是 {@code BlockToolModificationEvent}</b>：
 * 原版 {@code HoeItem#useOn} 已经改成走 {@code state.getToolModifiedState(...)} 扩展点，
 * 覆写本方块的这个方法是 NeoForge 官方推荐的路径（{@code HoeItem} 的注释原文），
 * 无需注册事件、不依赖加载顺序、单机与服务端一致。
 */
public class SpiritSoilBlock extends Block {

    public static final MapCodec<SpiritSoilBlock> CODEC =
            BlockBehaviour.simpleCodec(SpiritSoilBlock::new);

    public SpiritSoilBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public BlockState getToolModifiedState(BlockState state, UseOnContext context,
                                           ItemAbility itemAbility, boolean simulate) {
        if (itemAbility == ItemAbilities.HOE_TILL) {
            // 两条约束与 HoeItem#onlyIfAirAbove 对齐（NeoForge 把 TILLABLES 表打了补丁，
            // onlyIfAirAbove 不再由原版执行，得自己判）：
            // ① 点的不能是下表面（否则 "头顶的土" 也能被耕，玩家会莫名其妙挖穿地板）；
            // ② 上方必须是空气（耕地不能压在任何方块下，否则方块会因 canSurvive 失败立刻退化）。
            if (context.getClickedFace() == Direction.DOWN) {
                return null;
            }
            if (!context.getLevel().getBlockState(context.getClickedPos().above()).isAir()) {
                return null;
            }
            return ModBlocks.SPIRIT_SOIL_FARMLAND.get()
                    .defaultBlockState()
                    .setValue(SpiritSoilFarmlandBlock.MOISTURE, 0);
        }
        return null;
    }
}
