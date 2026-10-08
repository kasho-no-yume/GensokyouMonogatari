package com.bitsson.gensokyou.ritual;

import com.bitsson.gensokyou.ritual.RitualMatch;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * 托管型储灵池：仪式灵力物理住在结构内物品（如祭品台上的灵力核心）里，
 * 核心 BE 的灵力四件套（getStored/getCapacity/receive/extract）在图案命中时
 * 整体转发到本接口，MUST NOT 经过核心自身字段。实现者负责守恒与逐载体限速。
 */
public interface SpiritBank {

    /** 聚合已存 = Σ 被托管载体的存量。 */
    long stored(ServerLevel level, BlockPos corePos, RitualMatch match);

    /** 聚合容量 = Σ 被托管载体的容量（0 = 空池，路由侧自动不选为汇）。 */
    long capacity(ServerLevel level, BlockPos corePos, RitualMatch match);

    /** 注入至多 maxAmount，逐载体按各自速率限速并行写入，返回实际注入量。 */
    long receive(ServerLevel level, BlockPos corePos, RitualMatch match, long maxAmount);

    default long extractable(ServerLevel level, BlockPos corePos, RitualMatch match) {
        return 0L;
    }

    /** 取出至多 maxAmount，逐载体按各自速率限速并行抽出，返回实际取出量。 */
    long extract(ServerLevel level, BlockPos corePos, RitualMatch match, long maxAmount);
}
