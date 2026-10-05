package com.bitsson.gensokyou.ritual.behavior;

import com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.network.InfoLine;
import com.bitsson.gensokyou.ritual.RitualBehavior;
import com.bitsson.gensokyou.ritual.RitualMatch;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * 赛尔能源（{@code gensokyou:sair_energy_circle}）：创造模式调试用的无限供灵源。
 *
 * <p>被动型仪式，成型即生效，无启动态、无配方、无激活费（pattern 不写 {@code toggleable}）。
 * 结构仅「核心 + 同层八邻 {@code minecraft:bedrock}」共 9 格——基岩无法在生存合法获取，天然构成
 * 创造门槛。行为每 1 秒（20 tick）在 {@code serverPassiveTick}（不受 enabled 门控）把核心缓存
 * 补满至 {@link GensokyouConfig#SAIR_ENERGY_BASE_CAPACITY}（默认 100 亿），使本仪式对外表现为
 * 永不枯竭的源：缓存被路由抽取后于下个补满周期恢复满额。补满走普通 {@code receive} 通道，
 * MUST NOT 走 {@code extractRouted/receiveRouted}（否则被自身 in=0 账本误截）。
 * 补满后同周期把缓存回流槽内灵力核心（{@code tickBatteryAutoFill}，按核心注灵速率），
 * 槽核亦表现为永不枯竭。
 *
 * <p>对外供灵速率 {@link #spiritOutRatePerSecond} 恒为 {@link GensokyouConfig#SAIR_ENERGY_OUT_RATE_PER_SECOND}
 * （默认 10 亿/s），静态不随阶级/时刻/结构状态变化（路由端点速率按周期 memo，动态速率会致源闪断）。
 * in 速率保持默认 0，本仪式不可作为受灵汇。
 */
public final class SairEnergyBehavior implements RitualBehavior {

    /** 恒定满缓存的补满周期（20 tick = 1 秒）。 */
    private static final long REFILL_PERIOD_TICKS = 20L;

    /** 状态行强调色（ARGB，电光青）。 */
    private static final int ACCENT = 0xFF00E5FF;

    /** 由来诗行；键显式列举——拼接前缀会被 lang_audit 当字面量误报。 */
    private static final String[] LORE_KEYS = {
            "gui.gensokyou.ritual.sair_energy.lore_1",
            "gui.gensokyou.ritual.sair_energy.lore_2",
            "gui.gensokyou.ritual.sair_energy.lore_3",
            "gui.gensokyou.ritual.sair_energy.lore_4",
            "gui.gensokyou.ritual.sair_energy.lore_5",
    };

    @Override
    public long spiritOutRatePerSecond(ServerLevel level, BlockPos corePos, RitualMatch match,
                                       RitualCoreBlockEntity core) {
        return GensokyouConfig.SAIR_ENERGY_OUT_RATE_PER_SECOND.get().longValue();
    }

    /** 供灵源仪式：能量方向是缓存 → 电池，显式豁免默认的"电池 → 缓存"。 */
    @Override
    public boolean refillsCacheFromSocket() {
        return false;
    }

    /** 成型即每秒把缓存补满至上限（无限源语义）；缓存已满时零写入。 */
    @Override
    public void serverPassiveTick(ServerLevel level, BlockPos corePos, RitualMatch match,
                                  RitualCoreBlockEntity core) {
        if (core.ageTicks() % REFILL_PERIOD_TICKS != 0L) {
            return;
        }
        long gap = core.getCapacity() - core.getStored();
        if (gap > 0L) {
            core.receive(gap);
        }
        core.tickBatteryAutoFill();
    }

    /** 可见行只放供灵速率短值，缓存/上限明细进 tooltip；下方固定放由来诗。 */
    @Override
    public List<InfoLine> uiInfo(ServerLevel level, BlockPos corePos, RitualMatch match,
                                 RitualCoreBlockEntity core) {
        long stored = core.getStored();
        long capacity = core.getCapacity();
        long out = spiritOutRatePerSecond(level, corePos, match, core);
        List<InfoLine> lines = new ArrayList<>();
        lines.add(InfoLine.tipped("gui.gensokyou.ritual.sair_energy.status",
                new String[]{InfoLine.compact(out)}, ACCENT,
                "gui.gensokyou.ritual.sair_energy.status.tip",
                new String[]{
                        InfoLine.compact(stored),
                        InfoLine.compact(capacity),
                        InfoLine.compact(out)}));
        for (String loreKey : LORE_KEYS) {
            lines.add(new InfoLine(loreKey, new String[0], "", ACCENT, -1F, null));
        }
        return lines;
    }
}
