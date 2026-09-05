package com.bitsson.gensokyou.ritual;

import com.bitsson.gensokyou.item.BuilderSelection;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * 服务端 per-player 仪式构建预览态（transient attachment，不序列化）。
 * 生命周期：置入 = 第一击；清除 = 第二击建造后；随玩家对象消亡自动清（登出/换维度/死亡）。
 * {@code dimension} 兜底"服务端态随实体重建清空而客户端静态态残留"的同连接跨维不同步。
 */
public record RitualPreviewState(ResourceLocation patternId, int tier,
                                 BlockPos corePos, ResourceKey<Level> dimension) {

    /** 与"点击的核心 + 手上选择 + 当前维度"全等比对（第二击建造判定，服务端权威）。 */
    public boolean matches(BlockPos clickedPos, BuilderSelection selection,
                           ResourceKey<Level> currentDimension) {
        return corePos.equals(clickedPos)
                && patternId.equals(selection.patternId())
                && tier == selection.tier()
                && dimension.equals(currentDimension);
    }
}
