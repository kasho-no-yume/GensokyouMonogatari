package com.bitsson.gensokyou.ritual.editor;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * 服务端 per-player 编辑杖力建预览态（transient attachment，不序列化）。
 * 生命周期：置入 = 力建第一击；清除 = 第二击执行/选择变更/登出。
 * 客户端凭此每帧本地重算三色分类渲染（零额外同步，同构建杖投影范式）。
 */
public record EditorPreviewState(ResourceLocation patternId, int level, BlockPos anchor,
                                 Workspace workspace, ResourceKey<Level> dimension) {

    /** 与"此核心 + 手上选择/工作区 + 当前维度"全等比对（第二击执行判定，服务端权威）。 */
    public boolean matches(BlockPos clickedPos, ResourceLocation selPattern, int selLevel,
                           Workspace ws, ResourceKey<Level> currentDimension) {
        return anchor.equals(clickedPos)
                && patternId.equals(selPattern)
                && level == selLevel
                && workspace.equals(ws)
                && dimension.equals(currentDimension);
    }
}
