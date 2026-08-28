package com.bitsson.gensokyou.spirit;

import com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity;
import com.bitsson.gensokyou.ritual.RitualBehaviors;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;

public final class SpiritPowerHelper {

    private SpiritPowerHelper() {
    }

    /** 半径内处于电容仪式状态的仪式核心。 */
    public static List<RitualCoreBlockEntity> capacitorsAround(ServerLevel level, BlockPos center, int radius) {
        List<RitualCoreBlockEntity> list = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius),
                center.offset(radius, radius, radius))) {
            if (level.getBlockEntity(pos) instanceof RitualCoreBlockEntity core
                    && core.isPattern(RitualBehaviors.CAPACITOR)) {
                list.add(core);
            }
        }
        return list;
    }

    public static float drainCapacitorsAround(ServerLevel level, BlockPos center, int radius, float want) {
        float remaining = want;
        for (RitualCoreBlockEntity capacitor : capacitorsAround(level, center, radius)) {
            remaining -= capacitor.extract((int) Math.ceil(remaining));
            if (remaining <= 0F) {
                break;
            }
        }
        return want - Math.max(0F, remaining);
    }

    public static boolean hasCapacitorAround(ServerLevel level, BlockPos center, int radius) {
        return !capacitorsAround(level, center, radius).isEmpty();
    }
}
