package com.bitsson.gensokyou.ritual.behavior;

import com.bitsson.gensokyou.balance.BalanceNumbers;
import com.bitsson.gensokyou.config.GensokyouServerConfig;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.block.entity.RitualPedestalBlockEntity;
import com.bitsson.gensokyou.entity.TouhouBoss;
import com.bitsson.gensokyou.item.BossResidualCodexItem;
import com.bitsson.gensokyou.network.InfoLine;
import com.bitsson.gensokyou.network.ModNetworking;
import com.bitsson.gensokyou.registry.ModItems;
import com.bitsson.gensokyou.ritual.RitualBehavior;
import com.bitsson.gensokyou.ritual.RitualMatch;
import com.bitsson.gensokyou.ritual.RitualPedestals;
import com.bitsson.gensokyou.ritual.SpiritPowerAccess;
import com.bitsson.gensokyou.spirit.SpiritPowerHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 镇魂返照之仪（{@code chinkon_hanshou_circle}，1~5 阶）：以大量灵力把寄宿了强敌残魂的
 * 众生典籍炼回碎符卡星与主题信物的<b>激活型一次性</b>仪式。
 *
 * <p><b>供品</b>：祭品台上一件 {@link BossResidualCodexItem}（独立物品类，故众生余录
 * {@code instanceof CodexOfBeingsItem} 判据天然不识别它）。成功镇魂消耗 1 件。
 *
 * <p><b>阶级门槛</b>：结构层号 {@link RitualMatch#level()} 必须 ≥ 该典籍寄宿 BOSS 的自身品阶
 * （{@code TouhouBoss#bossTier()}，合成时定格于组件）。低阶结构不得产出高阶 BOSS 产物。
 *
 * <p><b>扣费</b>：单次成本 = {@code chinkonBaseSpCost × chinkonSpCostMult^(L-1)}，
 * 走三段式（槽内核 → 缓存 → 周围），{@link SpiritPowerHelper#payCost} 原子全有全无。
 *
 * <p><b>产出</b>：{@code 0~1} 碎符卡星（添头）+ {@code 1~2} 件该 BOSS 的主题信物
 * （{@code TouhouBoss#signatureItem()}，须属 {@code #gensokyou:boss_signature}）；
 * 无信物 BOSS 只出碎星。产物聚合空投于核心半径 3 圆盘内，并触发一次冷色光柱。
 */
public class ChinkonHanshouBehavior implements RitualBehavior {

    /** 唯一操作按钮「镇魂」。 */
    public static final int ACTION_EXECUTE = 0;

    private static final int ACCENT = 0xFF8FD8FF;

    /** 主题信物标签（须为「新签名素材」，复用既有物的信物同样在内）。 */
    private static final TagKey<Item> BOSS_SIGNATURE =
            TagKey.create(Registries.ITEM, Gensokyou.id("boss_signature"));

    // ---- 世界无关纯内核 ----

    /** 单次灵力成本 = 基值 × 倍率^(BOSS 品阶-1)（品阶起点为 1）。 */
    public static long spiritCost(int bossTier) {
        long value = GensokyouServerConfig.CHINKON_BASE_SP_COST.get();
        long mult = GensokyouServerConfig.CHINKON_SP_COST_MULT.get();
        for (int i = 1; i < bossTier; i++) {
            value *= mult;
        }
        return Math.max(0L, value);
    }

    /** 缓存容量 = 基值 × 倍率^(等级-1)。 */
    public static long capacity(int level) {
        long cap = GensokyouServerConfig.CHINKON_BASE_CAPACITY.get();
        long mult = GensokyouServerConfig.CHINKON_CAPACITY_MULT.get();
        for (int i = 1; i < level; i++) {
            cap *= mult;
        }
        return Math.max(0L, cap);
    }

    /** 阶级门槛：结构层号 ≥ BOSS 品阶。 */
    public static boolean tierAllows(int ritualLevel, int bossTier) {
        return bossTier <= ritualLevel;
    }

    // ---- 世界侧 ----

    /** 有效供品：位置 + 寄宿 BOSS 身份/品阶。 */
    public record Offering(BlockPos pos, ResourceLocation boss, int tier) {
    }

    /** 第一个携带有效残魂典籍的祭品台（规范序）；无则 null。 */
    public static Offering findOffering(ServerLevel level, RitualMatch match) {
        for (BlockPos pos : RitualPedestals.positions(match)) {
            if (level.getBlockEntity(pos) instanceof RitualPedestalBlockEntity pedestal) {
                ItemStack held = pedestal.getHeld();
                if (BossResidualCodexItem.isValid(held)) {
                    ResourceLocation boss = BossResidualCodexItem.getBoss(held).orElse(null);
                    if (boss != null) {
                        return new Offering(pos, boss, BossResidualCodexItem.getTier(held));
                    }
                }
            }
        }
        return null;
    }

    private static void consume(ServerLevel level, BlockPos podium) {
        if (level.getBlockEntity(podium) instanceof RitualPedestalBlockEntity pedestal) {
            ItemStack held = pedestal.getHeld();
            int remaining = Math.max(0, held.getCount() - 1);
            pedestal.setHeld(remaining == 0 ? ItemStack.EMPTY : held.copyWithCount(remaining));
        }
    }

    /** 该 BOSS 的主题信物（须属 #gensokyou:boss_signature）；无归属返回 empty。 */
    public static Optional<Item> signatureOf(ServerLevel level, ResourceLocation boss) {
        Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(boss);
        if (type.isEmpty()) {
            return Optional.empty();
        }
        Entity probe = type.get().create(level);
        if (probe instanceof TouhouBoss touhou) {
            Optional<Item> item = touhou.signatureItem();
            if (item.isPresent() && item.get().builtInRegistryHolder().is(BOSS_SIGNATURE)) {
                return item;
            }
        }
        return Optional.empty();
    }

    private static int rollRange(ServerLevel level, int min, int max) {
        int lo = Math.max(0, Math.min(min, max));
        int hi = Math.max(lo, Math.max(min, max));
        return hi <= lo ? lo : lo + level.random.nextInt(hi - lo + 1);
    }

    /** 产出掷骰（0~1 碎星 + 1~2 主题信物）。 */
    public static List<ItemStack> rollOutputs(ServerLevel level, ResourceLocation boss) {
        List<ItemStack> out = new ArrayList<>();
        int star = rollRange(level, GensokyouServerConfig.CHINKON_STAR_MIN.get(),
                GensokyouServerConfig.CHINKON_STAR_MAX.get());
        if (star > 0) {
            out.add(new ItemStack(ModItems.BROKEN_SPELL_CARD_STAR.get(), star));
        }
        Optional<Item> signature = signatureOf(level, boss);
        if (signature.isPresent()) {
            int count = rollRange(level, GensokyouServerConfig.CHINKON_SIGNATURE_MIN.get(),
                    GensokyouServerConfig.CHINKON_SIGNATURE_MAX.get());
            if (count > 0) {
                out.add(new ItemStack(signature.get(), count));
            }
        }
        return out;
    }

    // ---- RitualBehavior ----

    @Override
    public com.bitsson.gensokyou.ritual.RitualRenderState buildRenderState(RitualMatch match,
                                                                          SpiritPowerAccess core) {
        if (core.sacrificeFxTicks() <= 0) {
            return null;
        }
        return new com.bitsson.gensokyou.ritual.RitualRenderState(
                com.bitsson.gensokyou.ritual.RitualRenderState.KIND_SACRIFICE, core.isEnabled(),
                match.level(),
                (int) Math.round(BalanceNumbers.FX_PILLAR_HEIGHT),
                core.sacrificeFxTicks(),
                com.bitsson.gensokyou.ritual.RitualBehaviors.sacrificeColorIndex(match.patternId()),
                new long[0], 0, 0L);
    }

    @Override
    public long capacity(int level, SpiritPowerAccess core) {
        return capacity(level);
    }

    @Override
    public boolean handlesStartViaUiAction() {
        return true; // 激活型：通用启停通道让位，走一次性「镇魂」按钮
    }

    @Override
    public List<UiAction> uiActions(ServerLevel level, BlockPos corePos, RitualMatch match,
                                    SpiritPowerAccess core) {
        return List.of(new UiAction(ACTION_EXECUTE, "gui.gensokyou.ritual.chinkon.start"));
    }

    @Override
    public long spiritInRatePerSecond(ServerLevel level, BlockPos corePos, RitualMatch match,
                                      SpiritPowerAccess core) {
        return GensokyouServerConfig.CHINKON_SPIRIT_IN_RATE.get().longValue();
    }

    @Override
    public void serverPassiveTick(ServerLevel level, BlockPos corePos, RitualMatch match,
                                  SpiritPowerAccess core) {
        core.tickBatteryToCacheFill();
    }

    @Override
    public InteractionResult onUiAction(ServerLevel level, BlockPos corePos, RitualMatch match,
                                        SpiritPowerAccess core, ServerPlayer player, int actionId) {
        if (actionId != ACTION_EXECUTE) {
            return InteractionResult.PASS;
        }
        int ritualLevel = match.level();
        Offering offering = findOffering(level, match);
        if (offering == null) {
            return InteractionResult.FAIL;
        }
        if (!tierAllows(ritualLevel, offering.tier())) {
            return InteractionResult.FAIL;
        }
        long cost = spiritCost(offering.tier());
        if (!SpiritPowerHelper.payCost(level, corePos, core, cost)) {
            return InteractionResult.FAIL;
        }
        consume(level, offering.pos());
        List<ItemStack> outputs = rollOutputs(level, offering.boss());
        WatatsumiBehavior.dropStacks(level, corePos, outputs);
        core.triggerSacrificeFx(BalanceNumbers.FX_PILLAR_TICKS);
        ModNetworking.sendRitualInfoToViewers(level, corePos);
        return InteractionResult.SUCCESS;
    }

    // ---- UI ----

    @Override
    public List<InfoLine> uiInfo(ServerLevel level, BlockPos corePos, RitualMatch match,
                                 SpiritPowerAccess core) {
        List<InfoLine> lines = new ArrayList<>();
        int ritualLevel = match.level();
        Offering offering = findOffering(level, match);
        int costTier = offering != null ? offering.tier() : 1;
        long cost = spiritCost(costTier);

        String stateKey;
        String[] stateArgs = new String[0];
        if (offering == null) {
            stateKey = "gui.gensokyou.ritual.chinkon.no_codex";
        } else if (!tierAllows(ritualLevel, offering.tier())) {
            stateKey = "gui.gensokyou.ritual.chinkon.tier_low";
            stateArgs = new String[]{String.valueOf(offering.tier())};
        } else if (!SpiritPowerHelper.canCover(level, corePos, core, cost)) {
            stateKey = "gui.gensokyou.ritual.chinkon.no_power";
        } else {
            stateKey = "gui.gensokyou.ritual.chinkon.ready";
        }
        lines.add(new InfoLine(stateKey, stateArgs, "", ACCENT, -1F, null));
        if (offering != null) {
            lines.add(new InfoLine("gui.gensokyou.ritual.chinkon.boss",
                    new String[]{BossResidualCodexItem.bossDisplayName(offering.boss()).getString(),
                            String.valueOf(offering.tier())},
                    "", ACCENT, -1F, null));
        }
        lines.add(new InfoLine("gui.gensokyou.ritual.chinkon.level",
                new String[]{String.valueOf(ritualLevel)}, "", ACCENT, -1F, null));
        lines.add(InfoLine.tipped("gui.gensokyou.ritual.chinkon.cost",
                new String[]{InfoLine.compact(cost)}, ACCENT,
                "gui.gensokyou.ritual.chinkon.cost.tip", new String[]{String.valueOf(cost)}));
        return lines;
    }

    // ---- 调试 ----

    /** 世界无关 + 世界读的单行摘要（供 /gs_debug chinkon 探针）。 */
    public static String debugSummary(ServerLevel level, RitualMatch match) {
        int ritualLevel = match.level();
        Offering offering = findOffering(level, match);
        String bossId = offering == null ? "<none>" : offering.boss().toString();
        int bossTier = offering == null ? -1 : offering.tier();
        boolean allowed = offering != null && tierAllows(ritualLevel, bossTier);
        String signature = offering == null ? "<none>"
                : signatureOf(level, offering.boss())
                        .map(i -> BuiltInRegistries.ITEM.getKey(i).toString()).orElse("<none>");
        return "level=" + ritualLevel
                + " ped=" + RitualPedestals.positions(match).size()
                + " hasOffering=" + (offering != null)
                + " boss=" + bossId
                + " bossTier=" + bossTier
                + " tierOk=" + allowed
                + " signature=" + signature
                + " costTier=" + (offering == null ? "-" : String.valueOf(offering.tier()))
                + " cost=" + spiritCost(offering == null ? 1 : offering.tier())
                + " costBase=" + GensokyouServerConfig.CHINKON_BASE_SP_COST.get()
                + " capacity=" + capacity(ritualLevel)
                + " star=[" + GensokyouServerConfig.CHINKON_STAR_MIN.get()
                + "," + GensokyouServerConfig.CHINKON_STAR_MAX.get() + "]"
                + " sig=[" + GensokyouServerConfig.CHINKON_SIGNATURE_MIN.get()
                + "," + GensokyouServerConfig.CHINKON_SIGNATURE_MAX.get() + "]";
    }

    /** 试掷摘要（探针用；无 world 副作用，仅消耗随机数）。 */
    public static String debugRoll(ServerLevel level, RitualMatch match) {
        Offering offering = findOffering(level, match);
        if (offering == null) {
            return "roll=<no offering>";
        }
        Map<String, Integer> agg = new java.util.LinkedHashMap<>();
        for (ItemStack stack : rollOutputs(level, offering.boss())) {
            if (!stack.isEmpty()) {
                agg.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                        stack.getCount(), Integer::sum);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : agg.entrySet()) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(e.getKey()).append('x').append(e.getValue());
        }
        return "roll(" + offering.boss() + ")=" + (sb.length() == 0 ? "<empty>" : sb.toString());
    }
}
