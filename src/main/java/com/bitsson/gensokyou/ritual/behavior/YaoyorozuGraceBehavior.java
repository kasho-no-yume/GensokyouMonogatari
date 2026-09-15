package com.bitsson.gensokyou.ritual.behavior;

import com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.network.InfoLine;
import com.bitsson.gensokyou.ritual.RitualBehavior;
import com.bitsson.gensokyou.ritual.RitualMatch;
import com.bitsson.gensokyou.spirit.ModAttachments;
import com.bitsson.gensokyou.spirit.SpiritPowerData;
import com.bitsson.gensokyou.spirit.attr.AttributeKey;
import com.bitsson.gensokyou.spirit.attr.PlayerAttributes;
import com.bitsson.gensokyou.spirit.grace.GraceFlight;
import com.bitsson.gensokyou.spirit.grace.GraceNumbers;
import com.bitsson.gensokyou.spirit.grace.GraceService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 八百万神恩之仪（1~5 阶）：玩家超人类进阶与洗练。
 *
 * <p>会话推进汇聚在 {@link YaoyorozuGraceService}；本类做框架钩子转接、受灵汇声明
 * 与按查看者组装的自主信息区——固定呈现查看者本人属性面板（阶级/池/15 键），
 * 会话期叠加进度行，REVIEW 期对 initiator 呈现洗练预览（新 vs 当前 + 采纳/保留），
 * 阶级≥1 提供飞行惯性切换按钮。技能配装/切换不属于本界面职责（spec 红线）。
 */
public class YaoyorozuGraceBehavior implements RitualBehavior {

    /** 受灵汇速率：固定高配置（"inrate 相当大"——校验后蓄满即执行，不设等待曲线）。 */
    @Override
    public long spiritInRatePerSecond(ServerLevel level, BlockPos corePos, RitualMatch match,
                                      RitualCoreBlockEntity core) {
        return GensokyouConfig.GRACE_SPIRIT_IN_RATE.get();
    }

    @Override
    public boolean handlesStartViaUiAction() {
        return true; // 会话是唯一启动路径；红石通道整体不响应（不覆写 onRedstonePulse）
    }

    // ---- 按钮（按查看者注入） ----

    @Override
    public List<UiAction> uiActions(ServerLevel level, BlockPos corePos, RitualMatch match,
                                    RitualCoreBlockEntity core) {
        return uiActions(level, corePos, match, core, null);
    }

    @Override
    public List<UiAction> uiActions(ServerLevel level, BlockPos corePos, RitualMatch match,
                                    RitualCoreBlockEntity core, @Nullable ServerPlayer viewer) {
        List<UiAction> actions = new ArrayList<>();
        RitualCoreBlockEntity.GracePhase phase = core.gracePhase();
        boolean initiator = viewer != null && viewer.getUUID().equals(core.graceSession().initiator());
        actions.add(new UiAction(YaoyorozuGraceService.ACTION_TRIGGER,
                phase == RitualCoreBlockEntity.GracePhase.PAYING
                        ? "gui.gensokyou.ritual.grace.cancel"
                        : "gui.gensokyou.ritual.grace.start",
                phase != RitualCoreBlockEntity.GracePhase.PERFORM
                        && (phase != RitualCoreBlockEntity.GracePhase.PAYING || initiator)));
        if (viewer != null && GraceService.tierOf(viewer) >= 1) {
            actions.add(new UiAction(YaoyorozuGraceService.ACTION_INERTIA,
                    ModAttachments.get(viewer).flightInertia()
                            ? "gui.gensokyou.ritual.grace.inertia_on"
                            : "gui.gensokyou.ritual.grace.inertia_off"));
        }
        return List.copyOf(actions);
    }

    @Override
    public InteractionResult onUiAction(ServerLevel level, BlockPos corePos, RitualMatch match,
                                        RitualCoreBlockEntity core, ServerPlayer player, int actionId) {
        switch (actionId) {
            case YaoyorozuGraceService.ACTION_TRIGGER -> {
                return YaoyorozuGraceService.trigger(level, corePos, player)
                        ? InteractionResult.SUCCESS : InteractionResult.FAIL;
            }
            case YaoyorozuGraceService.ACTION_INERTIA -> {
                if (GraceService.tierOf(player) < 1) {
                    return InteractionResult.FAIL; // 服务端拒绝为他人/凡人切换（按钮本就按人隐藏）
                }
                GraceFlight.toggleInertia(player);
                return InteractionResult.SUCCESS;
            }
            case YaoyorozuGraceService.ACTION_REFINE_ACCEPT -> {
                return YaoyorozuGraceService.decideRefine(level, corePos, core, player, true)
                        ? InteractionResult.SUCCESS : InteractionResult.FAIL;
            }
            case YaoyorozuGraceService.ACTION_REFINE_KEEP -> {
                return YaoyorozuGraceService.decideRefine(level, corePos, core, player, false)
                        ? InteractionResult.SUCCESS : InteractionResult.FAIL;
            }
            default -> {
            }
        }
        return InteractionResult.PASS;
    }

    // ---- 会话推进与失效 ----

    @Override
    public void serverTick(ServerLevel level, BlockPos corePos, RitualMatch match,
                           RitualCoreBlockEntity core) {
        YaoyorozuGraceService.advanceSession(level, corePos, core);
    }

    @Override
    public void onStructureLost(ServerLevel level, BlockPos corePos) {
        if (level.getBlockEntity(corePos) instanceof RitualCoreBlockEntity core) {
            YaoyorozuGraceService.onStructureLost(level, corePos, core);
        }
    }

    // ---- 自主信息区：查看者属性面板 + 会话状态 + 预览 + 配装 ----

    @Override
    public List<InfoLine> uiInfo(ServerLevel level, BlockPos corePos, RitualMatch match,
                                 RitualCoreBlockEntity core, @Nullable ServerPlayer viewer) {
        List<InfoLine> lines = new ArrayList<>();
        RitualCoreBlockEntity.GracePhase phase = core.gracePhase();
        boolean initiator = viewer != null
                && viewer.getUUID().equals(core.graceSession().initiator());
        switch (phase) {
            case PAYING -> {
                long cost = core.graceSession().cost();
                long got = core.graceSession().collected();
                lines.add(new InfoLine("gui.gensokyou.ritual.grace.paying",
                        new String[]{InfoLine.compact(got), InfoLine.compact(cost)},
                        "", 0xFFB39DDB, cost <= 0L ? 1F : Math.min(1F, (float) got / (float) cost),
                        null, 0, InfoLine.CONTROL_NONE, InfoLine.LINK_NONE,
                        "gui.gensokyou.ritual.grace.paying_tip",
                        new String[]{String.valueOf(got), String.valueOf(cost)}));
            }
            case PERFORM -> lines.add(new InfoLine("gui.gensokyou.ritual.grace.perform",
                    new String[0], "", 0xFFCE93D8,
                    Math.min(1F, (float) core.graceSession().ticks()
                            / (float) Math.max(1, GensokyouConfig.GRACE_PERFORM_TICKS.get())), null));
            case REVIEW -> {
                if (initiator) {
                    appendReviewLines(lines, core, viewer);
                } else {
                    lines.add(new InfoLine("gui.gensokyou.ritual.grace.review_waiting",
                            new String[0], "", 0xFFCE93D8, -1F, null));
                }
            }
            default -> {
            }
        }
        if (viewer != null) {
            appendAttributePanel(lines, viewer);
        }
        return lines;
    }

    /** 会话信息行入口（无查看者场景兜底：不含面板）。 */
    @Override
    public List<InfoLine> uiInfo(ServerLevel level, BlockPos corePos, RitualMatch match,
                                 RitualCoreBlockEntity core) {
        return uiInfo(level, corePos, match, core, null);
    }

    /** 查看者本人属性面板（只读；凡人同样可见——"我还差什么"的入口）。 */
    private static void appendAttributePanel(List<InfoLine> lines, ServerPlayer viewer) {
        SpiritPowerData data = ModAttachments.get(viewer);
        lines.add(attrLine("gui.gensokyou.ritual.grace.tier",
                String.valueOf(data.temperLevel()), 0xFF7E57C2));
        lines.add(attrLine("gui.gensokyou.ritual.grace.pool",
                trim(data.current()) + " / " + trim(
                        PlayerAttributes.effectiveMaxSpirit(viewer)), 0xFF7E57C2));
        for (AttributeKey key : AttributeKey.values()) {
            if (key == AttributeKey.MAX_SPIRIT || key == AttributeKey.SPIRIT_POWER
                    || key == AttributeKey.SPIRIT_REGEN_RATE) {
                continue; // 池两键见 pool 行；恢复速率并入下方通用键
            }
            lines.add(attrLine(key.langKey(), format(key,
                    PlayerAttributes.finalValue(viewer, key)), 0));
        }
        lines.add(attrLine(AttributeKey.SPIRIT_REGEN_RATE.langKey(), format(
                AttributeKey.SPIRIT_REGEN_RATE,
                PlayerAttributes.finalValue(viewer, AttributeKey.SPIRIT_REGEN_RATE)), 0));
        lines.add(attrLine(AttributeKey.SPIRIT_POWER.langKey(),
                trim(data.spiritDamage()), 0));
    }

    /** 洗练预览：新 vs 当前逐键 + 采纳/保留按钮行（仅 initiator，REVIEW 态）。 */
    private static void appendReviewLines(List<InfoLine> lines, RitualCoreBlockEntity core,
                                          ServerPlayer viewer) {
        GraceNumbers.GraceRoll preview = core.graceSession().pendingRefine();
        if (preview == null) {
            return;
        }
        int tier = preview.tier();
        GraceNumbers.GraceRoll current = GraceService.currentRoll(viewer, tier);
        lines.add(new InfoLine("gui.gensokyou.ritual.grace.refine_title",
                new String[]{String.valueOf(tier)}, "", 0xFFE0B0FF, -1F, null));
        lines.add(attrLine(AttributeKey.MAX_SPIRIT.langKey(),
                trim(current.maxGain()) + " → " + trim(preview.maxGain()), 0xFFA5D6A7));
        lines.add(attrLine(AttributeKey.SPIRIT_POWER.langKey(),
                trim(current.powerGain()) + " → " + trim(preview.powerGain()), 0xFFA5D6A7));
        for (AttributeKey key : AttributeKey.values()) {
            if (key == AttributeKey.MAX_SPIRIT || key == AttributeKey.SPIRIT_POWER) {
                continue;
            }
            Float newValue = preview.contributions().get(key);
            Float oldValue = current.contributions().get(key);
            if (newValue == null && oldValue == null) {
                continue; // 表外键不显示
            }
            lines.add(attrLine(key.langKey(),
                    format(key, oldValue == null ? 0F : oldValue) + " → "
                            + format(key, newValue == null ? 0F : newValue), 0xFFA5D6A7));
        }
        lines.add(new InfoLine("gui.gensokyou.ritual.grace.refine_accept",
                new String[0], "", COLOR_OK_ACTION, -1F, null,
                YaoyorozuGraceService.ACTION_REFINE_ACCEPT, InfoLine.CONTROL_BUTTON,
                InfoLine.LINK_NONE, "", new String[0]));
        lines.add(new InfoLine("gui.gensokyou.ritual.grace.refine_keep",
                new String[0], "", COLOR_KEEP_ACTION, -1F, null,
                YaoyorozuGraceService.ACTION_REFINE_KEEP, InfoLine.CONTROL_BUTTON,
                InfoLine.LINK_NONE, "", new String[0]));
    }

    // ---- 格式化 ----

    private static final int COLOR_OK_ACTION = 0xFF66BB6A;
    private static final int COLOR_KEEP_ACTION = 0xFFFFB74D;

    private static InfoLine attrLine(String textKey, String value, int color) {
        return new InfoLine(textKey, new String[]{value}, "", color, -1F, null,
                0, InfoLine.CONTROL_ATTR, InfoLine.LINK_NONE, "", new String[0]);
    }

    /** flat 键（生命/抵抗/池值）定值展示；百分比键 % 后缀。 */
    private static String format(AttributeKey key, float value) {
        return key.isFlat() ? trim(value) : String.format(java.util.Locale.ROOT, "%.1f%%", value * 100F);
    }

    private static String trim(float value) {
        double rounded = Math.round(value * 10D) / 10D;
        return rounded == Math.rint(rounded)
                ? String.valueOf((long) rounded)
                : String.format(java.util.Locale.ROOT, "%.1f", rounded);
    }
}
