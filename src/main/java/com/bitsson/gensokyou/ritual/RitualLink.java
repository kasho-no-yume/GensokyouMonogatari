package com.bitsson.gensokyou.ritual;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * 共鸣塔的一条灵力链接：目标仪式核心坐标 + 登记时的图案 id。
 * 图案一致性是链接存活判据——同坐标换成别的仪式即视为死链（静默剔除）。
 */
public record RitualLink(BlockPos corePos, ResourceLocation patternId) {

    public RitualLink {
        corePos = corePos.immutable();
    }
}
