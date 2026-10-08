package com.bitsson.gensokyou.ritual.behavior;

import com.bitsson.gensokyou.ritual.SpiritPowerAccess;

import com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity;
import com.bitsson.gensokyou.network.InfoLine;
import com.bitsson.gensokyou.ritual.RitualBehavior;
import com.bitsson.gensokyou.ritual.RitualMatch;
import com.bitsson.gensokyou.spirit.SpiritCoreItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 昼夜发电机仪式（日轮天台 / 月影水镜）共同行为。
 *
 * <p>产灵按所在维度的线性三角时间线：0 阶峰值 {@code base × 4^L}/s，从起点 0 线性升到中点峰值、
 * 再线性降回终点 0，其余时段为 0；每结算秒取"峰值 × 比例"后四舍五入为整数发放（MUST NOT 小数累积）。
 * 日轮以日出(0)为起点、正午(6000)为峰、日落(12000)归零；月影整体平移 12000。
 *
 * <p>产灵走 {@code serverTick}（enabled 门控）：先不限速注满槽内灵力核心、溢出进缓存、缓存满则作废；
 * 缓存在 {@code serverPassiveTick}（无门控）按核心注灵速率回流未满核心（复用 {@code tickBatteryAutoFill}）。
 * 计时只依赖 {@code dayTime}，不判天空/天气/月相；任意维度通用。
 *
 * <p>取时用 {@code DimensionType.fixedTime().orElse(dayTime)}——MUST NOT 用 {@code getTimeOfDay}（那是
 * 天空着色用的平滑曲线，非线性三角）。
 */
public abstract class DayCycleGeneratorBehavior implements RitualBehavior {

    private static final long DAY_TICKS = 24000L;
    private static final long HALF_DAY_TICKS = 6000L;
    private static final long LUNAR_SHIFT_TICKS = 12000L;

    /** true = 日轮（白天段），false = 月影（夜晚段）。 */
    protected abstract boolean solar();

    /** 0 阶峰值产灵/秒（每阶 ×4）。 */
    protected abstract double baseRatePerSecond();

    /** 对外供灵上限/秒（固定，不随阶级与时刻变化）。 */
    protected abstract long outRatePerSecond();

    /** lang 前缀：{@code gui.gensokyou.ritual.<prefix>.lore_N}。 */
    protected abstract String langPrefix();

    /** 状态行强调色（ARGB）。 */
    protected abstract int accentColor();

    // ---- 世界无关纯内核（单测 / 调试命令可直调）----

    /** 归一化三角时间线（输入已归一到 [0,24000)）：[0,6000) 升、[6000,12000) 降、其余 0。 */
    static double triangle(long t) {
        if (t < 0L) {
            return 0.0D;
        }
        if (t < HALF_DAY_TICKS) {
            return (double) t / HALF_DAY_TICKS;
        }
        if (t < 2L * HALF_DAY_TICKS) {
            return (double) (2L * HALF_DAY_TICKS - t) / HALF_DAY_TICKS;
        }
        return 0.0D;
    }

    /** 时刻比例 [0,1]：solar 取白天段，lunar 平移 12000 后取同一三角。 */
    public static double fraction(long dayTime, boolean solar) {
        long shifted = Math.floorMod(dayTime + (solar ? 0L : LUNAR_SHIFT_TICKS), DAY_TICKS);
        return triangle(shifted);
    }

    /** 该秒实际产灵 = round(峰值 × 比例)；峰值 = base × 4^level。 */
    public static long producedPerSecond(long dayTime, int level, double baseRate, boolean solar) {
        if (baseRate <= 0.0D) {
            return 0L;
        }
        double peak = baseRate * (double) pow4(level);
        return Math.round(peak * fraction(dayTime, solar));
    }

    /** 计数用的归一化时刻（已平移，与 {@link #fraction} 同相位）。 */
    public static long shiftedDayTime(long dayTime, boolean solar) {
        return Math.floorMod(dayTime + (solar ? 0L : LUNAR_SHIFT_TICKS), DAY_TICKS);
    }

    static long pow4(int exponent) {
        long value = 1L;
        for (int i = 0; i < exponent; i++) {
            value *= 4L;
        }
        return value;
    }

    /** 维度时刻：fixed_time 维度取其固定值（与视觉一致），否则取推进中的 dayTime。 */
    public static long dayTimeOf(ServerLevel level) {
        return level.dimensionType().fixedTime().orElse(level.dayTime());
    }

    // ---- RitualBehavior ----

    @Override
    public void serverTick(ServerLevel level, BlockPos corePos, RitualMatch match,
                           SpiritPowerAccess core) {
        if (core.ageTicks() % 20L != 0L) {
            return;
        }
        long produced = producedPerSecond(dayTimeOf(level), match.level(), baseRatePerSecond(), solar());
        if (produced > 0L) {
            deposit(core, produced);
        }
    }

    /** 发电仪式（日轮天台 / 月影水镜共用）：能量方向是缓存 → 电池，显式豁免默认方向。 */
    @Override
    public boolean refillsCacheFromSocket() {
        return false;
    }

    /** 缓存回流（无门控）：成型即每秒把缓存搬入未满核心，停机也继续。 */
    @Override
    public void serverPassiveTick(ServerLevel level, BlockPos corePos, RitualMatch match,
                                  SpiritPowerAccess core) {
        if (core.ageTicks() % 20L == 0L) {
            core.tickBatteryAutoFill();
        }
    }

    /** 核优先（不限速直注）→ 溢出进缓存（截断到上限，余量作废）。 */
    private static void deposit(SpiritPowerAccess core, long amount) {
        long rest = amount;
        ItemStack battery = core.batteryStack();
        if (battery.getItem() instanceof SpiritCoreItem) {
            long toCore = SpiritCoreItem.receive(battery, rest);
            if (toCore > 0L) {
                core.setBatteryStack(battery);
                rest -= toCore;
            }
        }
        if (rest > 0L) {
            core.receive(rest);
        }
    }

    @Override
    public long spiritOutRatePerSecond(ServerLevel level, BlockPos corePos, RitualMatch match,
                                       SpiritPowerAccess core) {
        return outRatePerSecond();
    }

    /** 结构失效清理（当前无跨 tick 内存态；预置以便未来加 FX/表）。 */
    @Override
    public void onStructureLost(ServerLevel level, BlockPos corePos) {
    }

    @Override
    public List<InfoLine> uiInfo(ServerLevel level, BlockPos corePos, RitualMatch match,
                                 SpiritPowerAccess core) {
        long dayTime = dayTimeOf(level);
        long produced = producedPerSecond(dayTime, match.level(), baseRatePerSecond(), solar());
        long peak = Math.round(baseRatePerSecond() * (double) pow4(match.level()));
        long phase = shiftedDayTime(dayTime, solar());
        String phaseKey;
        if (phase < HALF_DAY_TICKS) {
            phaseKey = "gui.gensokyou.ritual.daycycle.rising";
        } else if (phase < 2L * HALF_DAY_TICKS) {
            phaseKey = "gui.gensokyou.ritual.daycycle.falling";
        } else {
            phaseKey = "gui.gensokyou.ritual.daycycle.dormant";
        }
        ItemStack battery = core.batteryStack();
        String batteryStored = "—";
        String batteryCap = "—";
        if (battery.getItem() instanceof SpiritCoreItem coreItem) {
            batteryStored = InfoLine.compact(SpiritCoreItem.getStored(battery));
            batteryCap = InfoLine.compact(coreItem.capacity());
        }

        List<InfoLine> lines = new ArrayList<>();
        lines.add(InfoLine.tipped(phaseKey, new String[]{InfoLine.compact(produced)}, accentColor(),
                "gui.gensokyou.ritual.daycycle.tip",
                new String[]{
                        InfoLine.compact(produced),
                        InfoLine.compact(peak),
                        InfoLine.compact(core.getStored()),
                        InfoLine.compact(core.getCapacity()),
                        batteryStored,
                        batteryCap,
                        String.valueOf(Math.floorMod(dayTime, DAY_TICKS))}));
        for (int i = 1; i <= 5; i++) {
            lines.add(new InfoLine("gui.gensokyou.ritual." + langPrefix() + ".lore_" + i,
                    new String[0], "", accentColor(), -1F, null));
        }
        return lines;
    }
}
