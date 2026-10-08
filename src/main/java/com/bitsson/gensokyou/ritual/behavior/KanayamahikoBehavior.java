package com.bitsson.gensokyou.ritual.behavior;

import com.bitsson.gensokyou.ritual.KanayamahikoSmeltSession;
import com.bitsson.gensokyou.ritual.RitualBehaviorState;

import com.bitsson.gensokyou.ritual.SpiritPowerAccess;

import com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity;
import com.bitsson.gensokyou.network.InfoLine;
import com.bitsson.gensokyou.ritual.RitualBehavior;
import com.bitsson.gensokyou.ritual.RitualMatch;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

public final class KanayamahikoBehavior implements RitualBehavior {

    @Override
    public com.bitsson.gensokyou.ritual.RitualRenderState buildRenderState(RitualMatch match, SpiritPowerAccess core) {
        java.util.List<net.minecraft.core.BlockPos> peds = core.pedestalPositions();
        int cap = com.bitsson.gensokyou.ritual.RitualRenderState.MAX_CHANNELS;
        long[] anchors = new long[Math.min(peds.size(), cap)];
        for (int i = 0; i < anchors.length; i++) anchors[i] = peds.get(i).asLong();
        return new com.bitsson.gensokyou.ritual.RitualRenderState(com.bitsson.gensokyou.ritual.RitualRenderState.KIND_KANAYAMAHIKO, core.isEnabled(), match.level(),
                core.structureMinY(), core.structureRadiusXZ(), 0, anchors, 0,
                com.bitsson.gensokyou.ritual.behavior.KanayamahikoSmelting.renderBurnMask(
                        peds, (com.bitsson.gensokyou.ritual.KanayamahikoSmeltSession) core.behaviorState()));
    }

    @Override
    public long capacity(int level, SpiritPowerAccess core) {
        return KanayamahikoSmelting.capacity(level);
    }

    @Override
    public RitualBehaviorState newState() {
        return new KanayamahikoSmeltSession();
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos corePos, RitualMatch match,
                           SpiritPowerAccess core) {
        KanayamahikoSmelting.tick(level, corePos, match, core);
    }

    @Override
    public void onStructureLost(ServerLevel level, BlockPos corePos) {
        KanayamahikoSmelting.clear(level, corePos);
    }

    @Override
    public long spiritInRatePerSecond(ServerLevel level, BlockPos corePos, RitualMatch match,
                                      SpiritPowerAccess core) {
        return KanayamahikoSmelting.inRate(match.level());
    }

    @Override
    public List<InfoLine> uiInfo(ServerLevel level, BlockPos corePos, RitualMatch match,
                                 SpiritPowerAccess core) {
        return KanayamahikoSmelting.uiInfo(level, corePos, match, core);
    }

    public static String debugSummary(ServerLevel level, BlockPos corePos, RitualMatch match,
                                      SpiritPowerAccess core) {
        return KanayamahikoSmelting.debugSummary(level, corePos, match, core);
    }
}
