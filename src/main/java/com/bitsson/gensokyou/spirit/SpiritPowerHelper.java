package com.bitsson.gensokyou.spirit;

import com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity;
import com.bitsson.gensokyou.ritual.RitualBehaviors;
import com.bitsson.gensokyou.ritual.RitualMatch;
import com.bitsson.gensokyou.ritual.behavior.SpiritBank;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class SpiritPowerHelper {

    /** 仪式扣费的周围兜底半径（既有口径）。 */
    public static final int PAYMENT_RADIUS = 3;

    private SpiritPowerHelper() {
    }

    // ---- 三段式扣费来源（core-socket-powering）：槽内核 → 核心自身储灵 → 周围兜底 ----

    /** 灵力来源合计（模拟口径，零副作用）；long 饱和加。 */
    public static long available(ServerLevel level, BlockPos center, RitualCoreBlockEntity core) {
        long sum = 0L;
        ItemStack battery = core.batteryStack();
        if (battery.getItem() instanceof SpiritCoreItem) {
            sum = saturatingAdd(sum, SpiritCoreItem.getStored(battery));
        }
        sum = saturatingAdd(sum, extractable(level, core));
        for (RitualCoreBlockEntity storage : storagesAround(level, center, PAYMENT_RADIUS)) {
            sum = saturatingAdd(sum, extractable(level, storage));
        }
        return sum;
    }

    /** 来源核验：全仪式扣费执行前 MUST 先行（全有全无，杜绝先抽后比不回滚）。 */
    public static boolean canCover(ServerLevel level, BlockPos center,
                                   RitualCoreBlockEntity core, long want) {
        return want <= 0L || available(level, center, core) >= want;
    }

    /**
     * 尽力抽取至多 want（不保证足额——调用方需原子性时先 {@link #canCover}）。
     * 优先级：槽内灵力核心（不限速率）→ 核心自身储灵（万象共鸣注入处）→ 周围核心兜底。
     * 返回实抽量。
     */
    public static long collect(ServerLevel level, BlockPos center,
                               RitualCoreBlockEntity core, long want) {
        if (want <= 0L) {
            return 0L;
        }
        long[] plan = drainPlan(batteryStored(core), extractable(level, core),
                surroundAvailable(level, center), want);
        long remaining = want;
        long taken = Math.min(remaining, plan[0]);
        if (taken > 0L) {
            ItemStack battery = core.batteryStack();
            long got = SpiritCoreItem.extract(battery, taken);
            if (got > 0L) {
                core.setBatteryStack(battery);
                remaining -= got;
            }
        }
        if (remaining > 0L) {
            remaining -= core.extract(Math.min(remaining, plan[1]));
        }
        if (remaining > 0L) {
            for (RitualCoreBlockEntity storage : storagesAround(level, center, PAYMENT_RADIUS)) {
                if (remaining <= 0L) {
                    break;
                }
                remaining -= storage.extract(remaining);
            }
        }
        return want - Math.max(0L, remaining);
    }

    /** 槽内核当前存量（非核心物品=0）。 */
    private static long batteryStored(RitualCoreBlockEntity core) {
        ItemStack battery = core.batteryStack();
        return battery.getItem() instanceof SpiritCoreItem ? SpiritCoreItem.getStored(battery) : 0L;
    }

    private static long surroundAvailable(ServerLevel level, BlockPos center) {
        long sum = 0L;
        for (RitualCoreBlockEntity storage : storagesAround(level, center, PAYMENT_RADIUS)) {
            sum = saturatingAdd(sum, extractable(level, storage));
        }
        return sum;
    }

    private static long extractable(ServerLevel level, RitualCoreBlockEntity storage) {
        RitualMatch match = storage.activeMatch();
        if (match != null) {
            return RitualBehaviors.get(match.patternId())
                    .filter(SpiritBank.class::isInstance)
                    .map(SpiritBank.class::cast)
                    .map(bank -> bank.extractable(level, storage.getBlockPos(), match))
                    .orElseGet(storage::getStored);
        }
        return storage.getStored();
    }

    /**
     * 世界无关抽灵计划内核：给定三段来源存量，按 槽核→自身→周围 优先级分配各段抽取量。
     * 返回 {@code long[3]} = 各段应抽（合计 ≤ want，且各 ≤ 该段存量）。
     */
    public static long[] drainPlan(long battery, long self, long surround, long want) {
        long[] plan = new long[3];
        if (want <= 0L) {
            return plan;
        }
        long remaining = want;
        plan[0] = Math.min(remaining, Math.max(0L, battery));
        remaining -= plan[0];
        plan[1] = Math.min(remaining, Math.max(0L, self));
        remaining -= plan[1];
        plan[2] = Math.min(remaining, Math.max(0L, surround));
        return plan;
    }

    /** 原子扣费：核验足额后一次抽满；不足则零消耗返回 false。 */
    public static boolean payCost(ServerLevel level, BlockPos center,
                                  RitualCoreBlockEntity core, long cost) {
        if (cost <= 0L) {
            return true;
        }
        return canCover(level, center, core, cost)
                && collect(level, center, core, cost) >= cost;
    }

    private static long saturatingAdd(long a, long b) {
        long sum = a + b;
        if ((a ^ sum) < 0L && (a ^ b) >= 0L) {
            return Long.MAX_VALUE;
        }
        return sum;
    }

    /** 半径内成型且有余灵的仪式核心（周围兜底来源；不含中心自身）。
     *  托管型（八方归元）经 extract 自动落到台上核心，普通缓存型抽自身字段。
     *  扣费请走 {@link #collect} 三段式（槽内核→自身储→本集合兜底）。 */
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
