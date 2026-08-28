package com.bitsson.gensokyou.ritual.behavior;

import com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.ritual.RitualBehavior;
import com.bitsson.gensokyou.ritual.RitualBehaviors;
import com.bitsson.gensokyou.ritual.RitualMatch;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

public class GeneratorBehavior implements RitualBehavior {

    @Override
    public void serverTick(ServerLevel level, BlockPos corePos, RitualMatch match,
                           RitualCoreBlockEntity core) {
        int interval = GensokyouConfig.GENERATOR_PUSH_INTERVAL_TICKS.get();
        if (core.ageTicks() % interval != 0) {
            return;
        }
        int generated = (int) Math.round(GensokyouConfig.GENERATOR_SP_PER_SECOND.get() * interval / 20D);
        int remaining = generated;
        for (Direction direction : Direction.values()) {
            if (remaining <= 0) {
                break;
            }
            if (level.getBlockEntity(corePos.relative(direction)) instanceof RitualCoreBlockEntity neighbor
                    && neighbor.isPattern(RitualBehaviors.CAPACITOR)) {
                remaining -= neighbor.receive(remaining);
            }
        }
    }
}
