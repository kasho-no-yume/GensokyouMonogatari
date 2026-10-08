package com.bitsson.gensokyou.ritual.behavior;

import com.bitsson.gensokyou.ritual.SpiritPowerAccess;

import com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity;
import com.bitsson.gensokyou.network.InfoLine;
import com.bitsson.gensokyou.ritual.RitualBehavior;
import com.bitsson.gensokyou.ritual.RitualExtraSlots;
import com.bitsson.gensokyou.ritual.RitualMatch;
import com.bitsson.gensokyou.ritual.brew.BrewReagentIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 少名（`gensokyou:sunako_circle`，1/2/3 阶）：自定义炼药仪式。
 *
 * <p><b>玩法</b>：祭品台上摆瓶装三途川水（每台 1 瓶，产出 1 瓶药水，原位替换），
 * 核心 GUI 的额外槽放"决定炼哪种药水"的试剂，点一次「开始炼药」跑一个批次。
 * 批次产量 = {@code min(有效台数, 缓存 ÷ 单价)}——<b>不做足额预检</b>，灵力不足时按可支付
 * 数量部分产出。数值口径全在 {@link SunakoScaling}。
 *
 * <p><b>一次性而非启停</b>：{@code handlesStartViaUiAction} 为真，通用 start/stop 让位；
 * pattern {@code toggleable:false}（已就位）。红石上升沿由框架代管触发（见
 * {@link #redstoneTriggersUiAction()}），故玩家不在场时也能起批。
 *
 * <p><b>缓存常驻</b>：空闲态容量不为 0，本仪式因此可被万象共鸣选为下游目标；
 * 若按会话型口径做成"空闲 0"，它会在蓄力阶段对路由完全隐身。
 */
public class SunakoBehavior implements RitualBehavior, RitualExtraSlots {

    @Override
    public com.bitsson.gensokyou.ritual.RitualRenderState buildRenderState(RitualMatch match, SpiritPowerAccess core) {
        return new com.bitsson.gensokyou.ritual.RitualRenderState(com.bitsson.gensokyou.ritual.RitualRenderState.KIND_SUNAKO, true, match.level(),
                (int) Math.round(com.bitsson.gensokyou.config.GensokyouConfig.FX_PILLAR_HEIGHT.get()), core.sacrificeFxTicks(),
                com.bitsson.gensokyou.ritual.RitualBehaviors.sacrificeColorIndex(match.patternId()), new long[0], 0, 0L);
    }

    @Override
    public long capacity(int level, SpiritPowerAccess core) {
        return SunakoScaling.capacityOf(level);
    }

    /** 注入按钮：唯一操作「开始炼药」。 */
    public static final int ACTION_BREW = 0;

    private static final String KEY_BUTTON = "gui.gensokyou.ritual.sunako.start";

    @Override
    public boolean handlesStartViaUiAction() {
        return true; // 批次型：通用启停通道让位，走一次性按钮
    }

    @Override
    public List<UiAction> uiActions(ServerLevel level, BlockPos corePos, RitualMatch match,
                                    SpiritPowerAccess core) {
        return List.of(new UiAction(ACTION_BREW, KEY_BUTTON));
    }

    @Override
    public InteractionResult onUiAction(ServerLevel level, BlockPos corePos, RitualMatch match,
                                        SpiritPowerAccess core, ServerPlayer player,
                                        int actionId) {
        if (actionId != ACTION_BREW) {
            return InteractionResult.PASS;
        }
        // player 可能为 null（红石代管触发），本操作不依赖玩家身份
        boolean produced = SunakoBrewing.brew(level, corePos, match, core).producedAnything();
        if (produced && match.level() >= 3 && player != null) {
            com.bitsson.gensokyou.event.AchievementAwards.award(player, "spirit_wine");
        }
        return produced
                ? InteractionResult.SUCCESS
                : InteractionResult.FAIL;
    }

    // ---- 灵力 ----

    @Override
    public long spiritInRatePerSecond(ServerLevel level, BlockPos corePos, RitualMatch match,
                                      SpiritPowerAccess core) {
        return SunakoScaling.inRateOf(match.level());
    }

    /**
     * 非发电仪式：走默认的「电池 → 缓存」，每 tick 把槽内灵力核心按其
     * {@code fillRatePerSecond} 补入缓存。
     *
     * <p>这一步<b>不可省</b>：非托管核心的 {@code getStored()} 只反映缓存、不含槽内核心，
     * 若不灌注，插核心的玩家与不插核心的玩家看到的可产出瓶数完全一样——本仪式就成了
     * 只能靠万象共鸣路由充能的死仪式。逐 tick 补电不会让"缓存读数"与"可产出瓶数"脱节：
     * 两者都读同一个 {@code getStored()}。
     *
     * <p>扣费只发生在点击那一刻（一次性批次），故不存在"同 tick 先扣后补"的顺序问题。
     */
    @Override
    public void serverPassiveTick(ServerLevel level, BlockPos corePos, RitualMatch match,
                                  SpiritPowerAccess core) {
        core.tickBatteryToCacheFill();
    }

    // ---- 额外槽（炼药试剂） ----

    @Override
    public int slotCount() {
        return 1;
    }

    @Override
    public boolean isSlotValid(int slot, ItemStack stack) {
        if (slot != SunakoBrewing.REAGENT_SLOT || stack == null || stack.isEmpty()) {
            return false;
        }
        // 只收"确实是炼药试剂"的物品：显式声明的优先，其次是原版 startMix 试剂集
        return BrewReagentIndex.isKnownReagent(stack.getItem());
    }

    @Override
    public String labelKey() {
        return "gui.gensokyou.ritual.sunako.reagent_slot";
    }

    // ---- GUI ----

    @Override
    public List<InfoLine> uiInfo(ServerLevel level, BlockPos corePos, RitualMatch match,
                                 SpiritPowerAccess core) {
        return SunakoBrewing.uiInfo(level, corePos, match, core);
    }
}
