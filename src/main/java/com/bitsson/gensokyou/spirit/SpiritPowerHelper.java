package com.bitsson.gensokyou.spirit;

import com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;

public final class SpiritPowerHelper {

    private SpiritPowerHelper() {
    }

    /** 半径内成型且有余灵的仪式核心（就近扣费来源；不含中心自身）。
     *  托管型（八方归元）经 extract 自动落到台上核心，普通缓存型抽自身字段。 */
    public static List<RitualCoreBlockEntity> storagesAround(ServerLevel level, BlockPos center, int radius) {
        List<RitualCoreBlockEntity> list = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius),
                center.offset(radius, radius, radius))) {
            if (pos.equals(center)) {
                continue;
            }
            if (level.getBlockEntity(pos) instanceof RitualCoreBlockEntity core
                    && core.activeMatch() != null && core.getStored() > 0L) {
                list.add(core);
            }
        }
        return list;
    }

    public static float drainStoragesAround(ServerLevel level, BlockPos center, int radius, float want) {
        float remaining = want;
        for (RitualCoreBlockEntity storage : storagesAround(level, center, radius)) {
            remaining -= storage.extract((int) Math.ceil(remaining));
            if (remaining <= 0F) {
                break;
            }
        }
        return want - Math.max(0F, remaining);
    }

    public static boolean hasStorageAround(ServerLevel level, BlockPos center, int radius) {
        return !storagesAround(level, center, radius).isEmpty();
    }
}
