package com.bitsson.gensokyou.ritual.behavior;

import com.bitsson.gensokyou.ritual.CraftSession;
import com.bitsson.gensokyou.ritual.CraftPhase;
import com.bitsson.gensokyou.ritual.RitualBehaviorState;

import com.bitsson.gensokyou.ritual.SpiritPowerAccess;

import com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.network.InfoLine;
import com.bitsson.gensokyou.ritual.RitualBehavior;
import com.bitsson.gensokyou.ritual.RitualMatch;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;

import java.util.ArrayList;
import java.util.List;

/**
 * 源初造化之仪（0~5 阶）：无序合成仪式——祭品台原料按最大匹配合成为产物。
 * 会话推进与触发汇聚在 {@link ZaohuaCraftingService}；本类只做框架钩子转接、
 * 受灵汇声明（供万象共鸣供能）与 GUI 状态行。
 */
public class ZaohuaCraftingBehavior implements RitualBehavior {

    private static CraftSession craft(SpiritPowerAccess core) {
        return (CraftSession) core.behaviorState();
    }

    @Override
    public long capacity(int level, SpiritPowerAccess core) {
        return craft(core).phase() == CraftPhase.IDLE ? 0L : craft(core).cost();
    }

    @Override
    public RitualBehaviorState newState() {
        return new CraftSession();
    }

    @Override
    public boolean handlesStartViaUiAction() {
        return true; // 会话是唯一启动路径，通用启停通道让位
    }

    /** 受灵汇速率：基项 × 倍率^等级（量级取大，担当合成供能主干）。 */
    public static long spiritInRatePerSecond(int level) {
        long rate = GensokyouConfig.ZAOHUA_SPIRIT_IN_RATE_BASE.get();
        long mult = GensokyouConfig.ZAOHUA_SPIRIT_IN_RATE_MULT.get();
        for (int i = 0; i < level; i++) {
            rate *= mult;
        }
        return rate;
    }

    @Override
    public long spiritInRatePerSecond(ServerLevel level, BlockPos corePos, RitualMatch match,
                                      SpiritPowerAccess core) {
        return spiritInRatePerSecond(match.level());
    }

    @Override
    public List<UiAction> uiActions(ServerLevel level, BlockPos corePos, RitualMatch match,
                                    SpiritPowerAccess core) {
        // FLIGHT 中置灰（服务端 trigger 仍独立拒绝，双保险）
        return List.of(new UiAction(ZaohuaCraftingService.ACTION_CRAFT,
                "gui.gensokyou.ritual.zaohua.start",
                craft(core).phase() != CraftPhase.FLIGHT));
    }

    @Override
    public InteractionResult onUiAction(ServerLevel level, BlockPos corePos, RitualMatch match,
                                        SpiritPowerAccess core, ServerPlayer player, int actionId) {
        if (actionId == ZaohuaCraftingService.ACTION_CRAFT) {
            return ZaohuaCraftingService.trigger(level, corePos,
                    ZaohuaCraftingService.TriggerSource.UI_BUTTON, player)
                    ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        }
        return InteractionResult.PASS;
    }

    @Override
    public void onRedstonePulse(ServerLevel level, BlockPos corePos, RitualMatch match,
                                SpiritPowerAccess core) {
        ZaohuaCraftingService.trigger(level, corePos,
                ZaohuaCraftingService.TriggerSource.REDSTONE_PULSE, null);
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos corePos, RitualMatch match,
                           SpiritPowerAccess core) {
        ZaohuaCraftingService.advanceSession(level, corePos, core);
    }

    @Override
    public void onStructureLost(ServerLevel level, BlockPos corePos) {
        if (level.getBlockEntity(corePos) instanceof SpiritPowerAccess core) {
            // 在飞实体经会话判据自行就地掉落（防吞件），此处仅清态
            craft(core).clear(); core.setActiveRecipeId(null); core.markDirty();
        }
    }

    @Override
    public List<InfoLine> uiInfo(ServerLevel level, BlockPos corePos, RitualMatch match,
                                 SpiritPowerAccess core) {
        List<InfoLine> lines = new ArrayList<>();
        switch (craft(core).phase()) {
            case PAYING -> {
                long cost = craft(core).cost();
                float ratio = cost <= 0L ? 1F
                        : Math.min(1F, (float) craft(core).collected() / (float) cost);
                lines.add(new InfoLine("gui.gensokyou.ritual.zaohua.paying",
                        new String[]{InfoLine.compact(craft(core).collected()),
                                InfoLine.compact(cost)},
                        "", 0xFFB39DDB, ratio, null, 0, InfoLine.CONTROL_NONE, InfoLine.LINK_NONE,
                        "gui.gensokyou.ritual.zaohua.paying_tip",
                        new String[]{String.valueOf(craft(core).collected()), String.valueOf(cost)}));
            }
            case FLIGHT -> lines.add(new InfoLine("gui.gensokyou.ritual.zaohua.flight",
                    new String[0], "", 0xFFCE93D8,
                    Math.min(1F, (float) craft(core).ticks()
                            / (float) Math.max(1, GensokyouConfig.ZAOHUA_CRAFT_DURATION_TICKS.get())),
                    null));
            default -> {
            }
        }
        // 配方清单不进仪式 GUI（归 JEI）；空闲态不追加通用行（造化无祭品门槛）
        return lines;
    }
}
