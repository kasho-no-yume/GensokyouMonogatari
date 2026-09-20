package com.bitsson.gensokyou.ritual.behavior;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.network.InfoLine;
import com.bitsson.gensokyou.ritual.RitualBehavior;
import com.bitsson.gensokyou.ritual.RitualMatch;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;

import java.util.ArrayList;
import java.util.List;

/**
 * 无尽藏之仪：托管 128 块无尽藏晶为一个统一仓储。
 *
 * <p>晶块随成型生成、停机隐藏、不成型移除（内容经核心孤儿段保全）；玩家与自动化只经核心读写；
 * 运行时按阶级每秒从缓存扣电，缓存由「电池核心→缓存」补料或万象路由注入维持，扣空自动停机。
 * 具体存储与生命周期逻辑见 {@link WujinzangStorage}。
 */
public class WujinzangBehavior implements RitualBehavior {

    @Override
    public ResourceLocation screenMenuId() {
        return Gensokyou.id("wujinzang_terminal");
    }

    @Override
    public boolean refillsCacheFromSocket() {
        return true;
    }

    @Override
    public void onFormed(ServerLevel level, BlockPos corePos, RitualMatch match) {
        RitualCoreBlockEntity core = core(level, corePos);
        if (core != null) {
            WujinzangStorage.ensureCrystals(core, level, match);
        }
    }

    @Override
    public void onStructureLost(ServerLevel level, BlockPos corePos) {
        RitualCoreBlockEntity core = core(level, corePos);
        if (core != null) {
            WujinzangStorage.onStructureLost(core, level);
        }
    }

    @Override
    public void serverPassiveTick(ServerLevel level, BlockPos corePos, RitualMatch match,
                                  RitualCoreBlockEntity core) {
        WujinzangStorage.passiveTick(core, level, match);
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos corePos, RitualMatch match,
                           RitualCoreBlockEntity core) {
        if (core.ageTicks() % 20 != 0L) {
            return;
        }
        long drain = RitualCoreBlockEntity.wujinzangDrain(match.level());
        long got = core.extract(drain);
        if (got < drain) {
            core.setEnabled(false);
            WujinzangStorage.concealAll(level, match, true);
        }
    }

    @Override
    public InteractionResult onStart(ServerLevel level, BlockPos corePos, RitualMatch match,
                                     RitualCoreBlockEntity core, ServerPlayer player) {
        int blocked = WujinzangStorage.blockedCount(level, match);
        if (blocked > 0) {
            player.displayClientMessage(Component.translatable(
                    "msg.gensokyou.wujinzang_blocked", blocked), true);
            return InteractionResult.FAIL;
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public long spiritInRatePerSecond(ServerLevel level, BlockPos corePos, RitualMatch match,
                                      RitualCoreBlockEntity core) {
        return GensokyouConfig.WUJINZANG_IN_RATE.get();
    }

    @Override
    public List<InfoLine> uiInfo(ServerLevel level, BlockPos corePos, RitualMatch match,
                                 RitualCoreBlockEntity core) {
        List<InfoLine> lines = new ArrayList<>();
        long stored = core.getStored();
        long cap = core.getCapacity();
        long drain = RitualCoreBlockEntity.wujinzangDrain(match.level());
        lines.add(InfoLine.tipped("gui.gensokyou.ritual.wujinzang.buffer",
                new String[]{InfoLine.compact(stored), InfoLine.compact(cap), InfoLine.compact(drain)},
                0xFF4FC3F7,
                "gui.gensokyou.ritual.wujinzang.buffer_tip",
                new String[]{String.valueOf(stored), String.valueOf(cap), String.valueOf(drain)}));
        int[] counts = WujinzangStorage.counts(level, match, core.getWujinzangVault());
        lines.add(InfoLine.tipped("gui.gensokyou.ritual.wujinzang.crystals",
                new String[]{String.valueOf(counts[0]), String.valueOf(counts[1])},
                0xFF4FC3F7, "gui.gensokyou.ritual.wujinzang.crystals_tip",
                new String[]{String.valueOf(counts[0]), String.valueOf(counts[1])}));
        lines.add(InfoLine.tipped("gui.gensokyou.ritual.wujinzang.types",
                new String[]{String.valueOf(counts[2]), String.valueOf(counts[3])},
                0xFFFFFFFF, "gui.gensokyou.ritual.wujinzang.types_tip",
                new String[]{String.valueOf(counts[2]), String.valueOf(counts[3])}));
        int blocked = WujinzangStorage.blockedCount(level, match);
        if (blocked > 0) {
            lines.add(new InfoLine("gui.gensokyou.ritual.wujinzang.blocked",
                    new String[]{String.valueOf(blocked)}, "", 0xFFB22222, -1F, null));
        }
        return lines;
    }

    /** 现场探针摘要（gs_debug wujinzang）：分区/容量/耗电/被占。 */
    public static String debugSummary(ServerLevel level, BlockPos corePos, RitualMatch match) {
        RitualCoreBlockEntity core = level.getBlockEntity(corePos) instanceof RitualCoreBlockEntity c
                ? c : null;
        long stored = core == null ? 0L : core.getStored();
        long cap = core == null ? 0L : core.getCapacity();
        int segs = com.bitsson.gensokyou.ritual.RitualPedestals.positions(match).size();
        return "stored=" + stored + " cap=" + cap
                + " drain=" + RitualCoreBlockEntity.wujinzangDrain(match.level())
                + " inRate=" + GensokyouConfig.WUJINZANG_IN_RATE.get()
                + " segments=" + segs
                + " blocked=" + WujinzangStorage.blockedCount(level, match)
                + " level=" + match.level();
    }

    private static RitualCoreBlockEntity core(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof RitualCoreBlockEntity c ? c : null;
    }
}
