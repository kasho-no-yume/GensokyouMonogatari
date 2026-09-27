package com.bitsson.gensokyou.ritual.behavior;

import com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity;
import com.bitsson.gensokyou.network.InfoLine;
import com.bitsson.gensokyou.ritual.RitualBehavior;
import com.bitsson.gensokyou.ritual.RitualMatch;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

public final class KanayamahikoBehavior implements RitualBehavior {

    @Override
    public void serverTick(ServerLevel level, BlockPos corePos, RitualMatch match,
                           RitualCoreBlockEntity core) {
        KanayamahikoSmelting.tick(level, corePos, match, core);
    }

    @Override
    public void onStructureLost(ServerLevel level, BlockPos corePos) {
        KanayamahikoSmelting.clear(level, corePos);
    }

    @Override
    public long spiritInRatePerSecond(ServerLevel level, BlockPos corePos, RitualMatch match,
                                      RitualCoreBlockEntity core) {
        return KanayamahikoSmelting.inRate(match.level());
    }

    @Override
    public List<InfoLine> uiInfo(ServerLevel level, BlockPos corePos, RitualMatch match,
                                 RitualCoreBlockEntity core) {
        return KanayamahikoSmelting.uiInfo(level, corePos, match, core);
    }

    public static String debugSummary(ServerLevel level, BlockPos corePos, RitualMatch match,
                                      RitualCoreBlockEntity core) {
        return KanayamahikoSmelting.debugSummary(level, corePos, match, core);
    }
}
