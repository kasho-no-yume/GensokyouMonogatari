package com.bitsson.gensokyou.ritual.behavior;

import com.bitsson.gensokyou.ritual.RitualBehaviorState;
import com.bitsson.gensokyou.ritual.SeiiSession;

import com.bitsson.gensokyou.ritual.SpiritPowerAccess;

import com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.item.weapon.AmpCoreItem;
import com.bitsson.gensokyou.item.weapon.RuneAffix;
import com.bitsson.gensokyou.item.weapon.RuneSummary;
import com.bitsson.gensokyou.item.weapon.SeiiNumbers;
import com.bitsson.gensokyou.network.InfoLine;
import com.bitsson.gensokyou.ritual.RitualBehavior;
import com.bitsson.gensokyou.ritual.RitualExtraSlots;
import com.bitsson.gensokyou.ritual.RitualMatch;
import com.bitsson.gensokyou.spirit.attr.AttributeKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 星移之仪（1/3/5 阶）：增幅核词条洗练。
 *
 * <p>会话推进汇聚在 {@link SeiiService}；本类做框架钩子转接、受灵汇声明，
 * 与按查看者组装的信息区——REVIEW 期把两个决策行<b>置于信息行首位</b>（信息区视口只有
 * 68px ≈ 6 行，按钮排在逐条对比之后会落在折线以下），且只对绑定 initiator 可见。
 */
/**
 * 星移之仪的增幅核占**核心 GUI 的泛化额外槽第 0 格**，祭品台全部留给催化剂
 * （1 阶只有 4 台，核若占一台就只剩 3 个催化剂位）。
 */
public class SeiiBehavior implements RitualBehavior, RitualExtraSlots {

    @Override
    public com.bitsson.gensokyou.ritual.RitualRenderState buildRenderState(RitualMatch match, SpiritPowerAccess core) {
        com.bitsson.gensokyou.ritual.SeiiSession session = seii(core);
        net.minecraft.world.level.Level lv = core.getLevel();
        boolean performing = lv != null && !lv.isClientSide
                && session.phase() == com.bitsson.gensokyou.ritual.SeiiSession.Phase.PERFORM;
        int duration = com.bitsson.gensokyou.config.GensokyouConfig.SEII_PERFORM_TICKS.get();
        int startTick = performing ? (int) (lv.getGameTime() - session.ticks()) : 0;
        return new com.bitsson.gensokyou.ritual.RitualRenderState(com.bitsson.gensokyou.ritual.RitualRenderState.KIND_SEII, performing, match.level(), startTick, duration, 0, new long[0], 0, 0L);
    }

    private static SeiiSession seii(SpiritPowerAccess core) {
        return (SeiiSession) core.behaviorState();
    }

    @Override
    public long capacity(int level, SpiritPowerAccess core) {
        return com.bitsson.gensokyou.item.weapon.SeiiNumbers.capacity(level);
    }

    @Override
    public RitualBehaviorState newState() {
        return new SeiiSession();
    }

    private static final int COLOR_OK_ACTION = 0xFF66BB6A;
    private static final int COLOR_KEEP_ACTION = 0xFFFFB74D;
    private static final int COLOR_UP = 0xFFA5D6A7;
    private static final int COLOR_TITLE = 0xFFE0B0FF;

    @Override
    public long spiritInRatePerSecond(ServerLevel level, BlockPos corePos, RitualMatch match,
                                      SpiritPowerAccess core) {
        return SeiiNumbers.inRate(match.level());
    }

    @Override
    public boolean handlesStartViaUiAction() {
        return true; // 会话是唯一启动路径；红石通道整体不响应
    }

    @Override
    public int slotCount() {
        return 1; // 增幅核走核心 GUI 专用槽，祭品台全部留给催化剂
    }

    @Override
    public boolean isSlotValid(int slot, ItemStack stack) {
        return slot == 0 && stack.getItem() instanceof AmpCoreItem;
    }

    @Override
    public String labelKey() {
        return "gui.gensokyou.ritual.target_slot";
    }

    // ---- 按钮 ----

    @Override
    public List<UiAction> uiActions(ServerLevel level, BlockPos corePos, RitualMatch match,
                                     SpiritPowerAccess core) {
        return uiActions(level, corePos, match, core, null);
    }

    @Override
    public List<UiAction> uiActions(ServerLevel level, BlockPos corePos, RitualMatch match,
                                    SpiritPowerAccess core, @Nullable ServerPlayer viewer) {
        SeiiSession.Phase phase = seii(core).phase();
        boolean initiator = viewer == null || viewer.getUUID().equals(seii(core).initiator());
        return List.of(new UiAction(SeiiService.ACTION_TRIGGER,
                phase == SeiiSession.Phase.PAYING
                        ? "gui.gensokyou.ritual.seii.cancel"
                        : "gui.gensokyou.ritual.seii.start",
                phase != SeiiSession.Phase.PERFORM
                        && (phase != SeiiSession.Phase.PAYING || initiator)));
    }

    @Override
    public InteractionResult onUiAction(ServerLevel level, BlockPos corePos, RitualMatch match,
                                        SpiritPowerAccess core, ServerPlayer player, int actionId) {
        return switch (actionId) {
            case SeiiService.ACTION_TRIGGER -> SeiiService.trigger(level, corePos, player)
                    ? InteractionResult.SUCCESS : InteractionResult.FAIL;
            case SeiiService.ACTION_ACCEPT -> SeiiService.decideReroll(level, corePos, core, player, true)
                    ? InteractionResult.SUCCESS : InteractionResult.FAIL;
            case SeiiService.ACTION_KEEP -> SeiiService.decideReroll(level, corePos, core, player, false)
                    ? InteractionResult.SUCCESS : InteractionResult.FAIL;
            default -> InteractionResult.PASS;
        };
    }

    // ---- 会话推进与失效 ----

    @Override
    public void serverTick(ServerLevel level, BlockPos corePos, RitualMatch match,
                           SpiritPowerAccess core) {
        SeiiService.advanceSession(level, corePos, core);
    }

    @Override
    public void onStructureLost(ServerLevel level, BlockPos corePos) {
        if (level.getBlockEntity(corePos) instanceof SpiritPowerAccess core) {
            SeiiService.onStructureLost(level, corePos, core);
        }
    }

    // ---- 信息区 ----

    @Override
    public List<InfoLine> uiInfo(ServerLevel level, BlockPos corePos, RitualMatch match,
                                 SpiritPowerAccess core) {
        return uiInfo(level, corePos, match, core, null);
    }

    @Override
    public List<InfoLine> uiInfo(ServerLevel level, BlockPos corePos, RitualMatch match,
                                 SpiritPowerAccess core, @Nullable ServerPlayer viewer) {
        List<InfoLine> lines = new ArrayList<>();
        SeiiSession session = seii(core);
        boolean initiator = viewer != null && viewer.getUUID().equals(session.initiator());
        switch (session.phase()) {
            case PAYING -> {
                long cost = session.cost();
                long got = session.collected();
                lines.add(new InfoLine("gui.gensokyou.ritual.seii.paying",
                        new String[]{InfoLine.compact(got), InfoLine.compact(cost)},
                        "", 0xFFB39DDB, cost <= 0L ? 1F : Math.min(1F, (float) got / (float) cost),
                        null, 0, InfoLine.CONTROL_NONE, InfoLine.LINK_NONE,
                        "gui.gensokyou.ritual.seii.paying_tip",
                        new String[]{String.valueOf(got), String.valueOf(cost)}));
            }
            case PERFORM -> lines.add(new InfoLine("gui.gensokyou.ritual.seii.perform",
                    new String[0], "", 0xFFCE93D8,
                    Math.min(1F, (float) session.ticks()
                            / (float) Math.max(1, GensokyouConfig.SEII_PERFORM_TICKS.get())), null));
            case REVIEW -> {
                if (initiator) {
                    appendReview(lines, session.pending());
                } else {
                    lines.add(new InfoLine("gui.gensokyou.ritual.seii.review_waiting",
                            new String[0], "", 0xFFCE93D8, -1F, null));
                }
            }
            default -> {
            }
        }
        if (viewer != null && session.phase() != SeiiSession.Phase.REVIEW) {
            appendOfferingChecklist(lines, match, core);
            appendLadderInfo(lines, match.level());
        }
        return lines;
    }

    /**
     * 祭品核对清单（带凹槽边框的物品格）。
     *
     * <p>本类覆写了 {@code uiInfo}，故不继承基类的 {@code defaultUiInfo}，需自行产出 ——
     * 复制其语义（图标 + ✓/✗ 满足标记）并补上 {@code CONTROL_ITEM} 边框。
     */
    private static void appendOfferingChecklist(List<InfoLine> lines, RitualMatch match,
                                                SpiritPowerAccess core) {
        var patternOpt = com.bitsson.gensokyou.ritual.RitualPatternLoader.byId(match.patternId());
        patternOpt.ifPresent(pattern -> {
            var result = com.bitsson.gensokyou.ritual.RitualOfferings.check(pattern, match, core.getLevel());
            for (var status : result.slots()) {
                ItemStack rep = status.requirement().item().representative();
                String itemId = rep.isEmpty() ? "" : net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .getKey(rep.getItem()).toString();
                lines.add(new InfoLine("", new String[0], itemId, 0, -1F, status.satisfied(),
                        0, InfoLine.CONTROL_ITEM, InfoLine.LINK_NONE, "", new String[0]));
            }
        });
    }

    /** 空闲态的阶梯说明（各阶可洗核阶与花费），供玩家判断"我该建几阶"。 */
    private static void appendLadderInfo(List<InfoLine> lines, int ritualLevel) {
        int maxTier = SeiiNumbers.maxCoreTier(ritualLevel);
        lines.add(new InfoLine("gui.gensokyou.ritual.seii.ladder_title",
                new String[0], "", COLOR_TITLE, -1F, null));
        for (int t = 1; t <= SeiiNumbers.CORE_TIERS; t++) {
            boolean unlocked = t <= maxTier;
            lines.add(new InfoLine("gui.gensokyou.ritual.seii.ladder_row",
                    new String[]{String.valueOf(t), InfoLine.compact(SeiiNumbers.spCost(t)),
                            String.valueOf(SeiiNumbers.affixCount(t))},
                    "", unlocked ? COLOR_UP : 0xFF9E9E9E, -1F, unlocked ? Boolean.TRUE : Boolean.FALSE));
        }
    }

    /**
     * 待决面板：<b>决策行置顶</b> + 逐条"新 vs 当前"。
     *
     * <p>对比按 id 对齐两侧；只在一侧出现的 id 显示为"新增/移除"。玩家属性 id 复用
     * {@code CONTROL_ATTR}（客户端本地化属性名），武器专有 id 用 {@code affix.gensokyou.*}。
     */
    private static void appendReview(List<InfoLine> lines, @Nullable SeiiSession.Pending pending) {
        if (pending == null) {
            return;
        }
        lines.add(new InfoLine("gui.gensokyou.ritual.seii.review_accept",
                new String[0], "", COLOR_OK_ACTION, -1F, null,
                SeiiService.ACTION_ACCEPT, InfoLine.CONTROL_BUTTON, InfoLine.LINK_NONE,
                "gui.gensokyou.ritual.seii.review_accept_tip", new String[0]));
        lines.add(new InfoLine("gui.gensokyou.ritual.seii.review_keep",
                new String[0], "", COLOR_KEEP_ACTION, -1F, null,
                SeiiService.ACTION_KEEP, InfoLine.CONTROL_BUTTON, InfoLine.LINK_NONE,
                "gui.gensokyou.ritual.seii.review_keep_tip", new String[0]));
        lines.add(new InfoLine("gui.gensokyou.ritual.seii.review_title",
                new String[]{String.valueOf(pending.coreTier())}, "", COLOR_TITLE, -1F, null));
        // 洗练度刻意不外显：保底是暗的，不在预览面板上 advertize。

        Map<String, Float> before = sum(pending.before());
        Map<String, Float> after = sum(pending.after());
        List<String> ids = new ArrayList<>(before.keySet());
        for (String id : after.keySet()) {
            if (!ids.contains(id)) {
                ids.add(id);
            }
        }
        for (String id : ids) {
            Float oldValue = before.get(id);
            Float newValue = after.get(id);
            AttributeKey key = AttributeKey.byId(id);
            String label = key != null ? key.langKey() : "affix.gensokyou." + id;
            String text = format(key, oldValue == null ? 0F : oldValue) + " → "
                    + format(key, newValue == null ? 0F : newValue);
            int color = newValue == null ? 0xFF9E9E9E
                    : oldValue == null ? COLOR_UP
                    : newValue >= oldValue ? COLOR_UP : 0xFFE57373;
            lines.add(new InfoLine(label, new String[]{text}, "", color, -1F, null,
                    0, InfoLine.CONTROL_ATTR, InfoLine.LINK_NONE, "", new String[0]));
        }
        RuneSummary oldSum = RuneSummary.of(pending.before());
        RuneSummary newSum = RuneSummary.of(pending.after());
        lines.add(new InfoLine("gui.gensokyou.ritual.seii.review_dps",
                new String[]{dpsHint(oldSum), dpsHint(newSum)}, "", 0xFFB0BEC5, -1F, null,
                0, InfoLine.CONTROL_ATTR, InfoLine.LINK_NONE, "", new String[0]));
    }

    private static Map<String, Float> sum(List<RuneAffix> affixes) {
        Map<String, Float> out = new LinkedHashMap<>();
        for (RuneAffix a : affixes) {
            out.merge(a.affixId(), a.value(), Float::sum);
        }
        return out;
    }

    /** 武器专有四键的有效 DPS 粗估（玩家属性域不进此式，其收益在属性面板可见）。 */
    private static String dpsHint(RuneSummary summary) {
        float damage = 1F + summary.damagePct();
        float rate = 1F / Math.max(0.2F, 1F - Math.min(0.8F, summary.attackRatePct()));
        return String.format(java.util.Locale.ROOT, "x%.2f", damage * rate);
    }

    /** 值格式化：护壁为无量纲指数的等效倍数、flat 键一位小数、其余百分比。 */
    private static String format(@Nullable AttributeKey key, float value) {
        if (key == AttributeKey.DANMAKU_REDUCE) {
            return "x" + String.format(java.util.Locale.ROOT, "%.2f",
                    Math.pow(2D, Math.max(0F, value)));
        }
        if (key == null) {
            return String.format(java.util.Locale.ROOT, "%+.1f%%", value * 100F);
        }
        if (key.isFlat()) {
            return String.format(java.util.Locale.ROOT, "%+.1f", value);
        }
        return String.format(java.util.Locale.ROOT, "%+.1f%%", value * 100F);
    }
}
