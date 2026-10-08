package com.bitsson.gensokyou.ritual.behavior;

import com.bitsson.gensokyou.ritual.WujinzangState;
import com.bitsson.gensokyou.ritual.RitualBehaviorState;

import com.bitsson.gensokyou.ritual.SpiritPowerAccess;

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
    public com.bitsson.gensokyou.ritual.RitualRenderState buildRenderState(RitualMatch match, SpiritPowerAccess core) {
        return new com.bitsson.gensokyou.ritual.RitualRenderState(com.bitsson.gensokyou.ritual.RitualRenderState.KIND_WUJINZANG, core.isEnabled(), match.level(),
                core.structureMinY(), core.structureMaxY(), 0,
                com.bitsson.gensokyou.ritual.behavior.WujinzangStorage.laserAnchors(core, match), 0, 0L);
    }

    private static WujinzangState wujinzang(SpiritPowerAccess core) {
        return (WujinzangState) core.behaviorState();
    }

    @Override
    public long capacity(int level, SpiritPowerAccess core) {
        return RitualCoreBlockEntity.wujinzangCapacity(level);
    }

    @Override
    public RitualBehaviorState newState() {
        return new WujinzangState();
    }

    @Override
    public String startAchievement(ServerLevel level, BlockPos corePos, RitualMatch match,
                                   SpiritPowerAccess core) {
        return "wujinzang";
    }

    @Override
    public net.neoforged.neoforge.items.IItemHandler itemHandler(SpiritPowerAccess core) {
        return new WujinzangStorage.ProxyHandler(core);
    }

    @Override
    public void onCoreRemoved(ServerLevel level, BlockPos corePos, RitualMatch match,
                              SpiritPowerAccess core) {
        WujinzangStorage.releaseForceLoads(core, level);
    }

    @Override
    public ResourceLocation screenMenuId() {
        return Gensokyou.id("wujinzang_terminal");
    }

    // 无尽藏是物品储物（祭品台上的晶块），灵力缓存只是给电池核当料仓，
    // 方向为「槽核 → 缓存」——即 RitualBehavior 的默认口径，无需覆写。

    @Override
    public void onFormed(ServerLevel level, BlockPos corePos, RitualMatch match) {
        SpiritPowerAccess core = core(level, corePos);
        if (core != null) {
            WujinzangStorage.ensureCrystals(core, level, match);
        }
    }

    @Override
    public void onStructureLost(ServerLevel level, BlockPos corePos) {
        SpiritPowerAccess core = core(level, corePos);
        if (core != null) {
            WujinzangStorage.onStructureLost(core, level);
        }
    }

    @Override
    public void serverPassiveTick(ServerLevel level, BlockPos corePos, RitualMatch match,
                                  SpiritPowerAccess core) {
        WujinzangStorage.passiveTick(core, level, match);
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos corePos, RitualMatch match,
                           SpiritPowerAccess core) {
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
                                     SpiritPowerAccess core, ServerPlayer player) {
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
                                      SpiritPowerAccess core) {
        return GensokyouConfig.WUJINZANG_IN_RATE.get();
    }

    @Override
    public List<InfoLine> uiInfo(ServerLevel level, BlockPos corePos, RitualMatch match,
                                 SpiritPowerAccess core) {
        List<InfoLine> lines = new ArrayList<>();
        long stored = core.getStored();
        long cap = core.getCapacity();
        long drain = RitualCoreBlockEntity.wujinzangDrain(match.level());
        lines.add(InfoLine.tipped("gui.gensokyou.ritual.wujinzang.buffer",
                new String[]{InfoLine.compact(stored), InfoLine.compact(cap), InfoLine.compact(drain)},
                0xFF4FC3F7,
                "gui.gensokyou.ritual.wujinzang.buffer_tip",
                new String[]{String.valueOf(stored), String.valueOf(cap), String.valueOf(drain)}));
        int[] counts = WujinzangStorage.counts(level, match, wujinzang(core).rawVault());
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
        SpiritPowerAccess core = level.getBlockEntity(corePos) instanceof RitualCoreBlockEntity c
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

    private static SpiritPowerAccess core(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof RitualCoreBlockEntity c ? c : null;
    }
}
