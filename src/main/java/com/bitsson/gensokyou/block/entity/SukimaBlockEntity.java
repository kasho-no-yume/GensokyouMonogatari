package com.bitsson.gensokyou.block.entity;

import com.bitsson.gensokyou.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 隙间方块实体：无数据字段、无 ticker，仅为客户端 BER 渲染眼形传送门提供挂载点。
 */
public class SukimaBlockEntity extends BlockEntity {

    public SukimaBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SUKIMA.get(), pos, state);
    }
}
