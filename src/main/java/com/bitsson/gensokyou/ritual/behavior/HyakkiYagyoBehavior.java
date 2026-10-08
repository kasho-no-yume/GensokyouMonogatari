package com.bitsson.gensokyou.ritual.behavior;

import com.bitsson.gensokyou.ritual.SummonSession;
import com.bitsson.gensokyou.ritual.SummonPhase;
import com.bitsson.gensokyou.ritual.RitualBehaviorState;

import com.bitsson.gensokyou.ritual.SpiritPowerAccess;

import com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.network.InfoLine;
import com.bitsson.gensokyou.network.ModNetworking;
import com.bitsson.gensokyou.ritual.RitualBehavior;
import com.bitsson.gensokyou.ritual.RitualBehaviors;
import com.bitsson.gensokyou.ritual.RitualMatch;
import com.bitsson.gensokyou.ritual.RitualPatternLoader;
import com.bitsson.gensokyou.ritual.RitualRecipe;
import com.bitsson.gensokyou.ritual.RitualRecipeLoader;
import com.bitsson.gensokyou.ritual.RitualRecipeMatcher;
import com.bitsson.gensokyou.spirit.SpiritCoreItem;
import com.bitsson.gensokyou.spirit.SpiritPowerHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 百鬼夜行（1/2/3 阶）：祭品化召唤仪式——摆齐配方 → 点「召唤」→ 吞祭品 → 无门票充能 →
 * 爆散 → 降临光柱。
 *
 * <p><b>无门票，是本仪式全部设计的起点。</b>{@code spCost} 在此语义下<b>不是费用而是会话容量</b>：
 * 启动当刻不向玩家扣任何灵力，而是把核心缓存上限从 0 抬到该配方消耗值，随后由供灵网络
 * 慢慢把它灌满。这决定了本仪式 MUST NOT 走通用 {@code RitualCoreBlockEntity#start()}——
 * 那条通道无条件 {@code payCost(spCost)}。故本类 {@link #handlesStartViaUiAction()} 返回 true、
 * pattern 写 {@code toggleable: false}，由 {@link #uiActions} 注入的自定义按钮独占启动。
 * 骨架照抄源初造化（{@code ZaohuaCraftingService}），差别见 design.md D2。
 *
 * <p><b>缓存本身即进度</b>：触发判据就是 {@code getStored() >= getCapacity()}，MUST NOT 引入
 * 与缓存解耦的累计计数器（造化的 {@code craftCollected} 存在是因为它要把原料"发射"出去，
 * 本仪式没有产物飞行）。
 *
 * <p><b>祭品在启动时即吞</b>（需求明确），故本类<b>不</b>复查台面——配方已锁进 NBT，复查只会
 * 制造一条"祭品都扣了还能中止"的分支。代价是点错一下白搭 4~8 组材料，唯一的缓解手段是
 * 「取消」按钮的提示文案（{@code gui.gensokyou.ritual.hyakki.cancel_tip}）。
 *
 * <p><b>供灵双路且 MUST NOT 合并上报</b>：槽内灵力核心走电池→缓存的本地直注（不经路由器，
 * 速率由核心自己的 {@code fillRatePerSecond} 决定）；万象共鸣走 {@code receiveRouted}。
 * 槽核那半 MIGHT NOT 算进 {@link #spiritInRatePerSecond}——那会虚增汇端预算，让别的受灵汇
 * 抢不到额度。空闲时 {@link #spiritInRatePerSecond} 刻意返回 0：这正是"仪式平时无缓存"的实现，
 * 也让本核心对路由器<b>结构性隐身</b>（汇端筛选要求受灵上限 &gt; 0）。
 *
 * <p><b>演出进度零网络包</b>：三段演出全部由客户端依持久化的绝对 gameTime 锚点自算
 * （{@code elapsed(level, core)}），服务端只推一个渲染态。
 */
public class HyakkiYagyoBehavior implements RitualBehavior {

    /** 停机即放弃本次召唤：清会话、抽干缓存、清激活配方。 */
    @Override
    public void onDisabled(ServerLevel level, BlockPos corePos, RitualMatch match,
                           SpiritPowerAccess core) {
        SummonSession s = summon(core);
        if (s.phase() != SummonPhase.IDLE) {
            s.clear();
            core.extract(core.getStored());
            core.setActiveRecipeId(null);
            core.markDirty();
        }
    }

    @Override
    public com.bitsson.gensokyou.ritual.RitualRenderState buildRenderState(RitualMatch match, SpiritPowerAccess core) {
        com.bitsson.gensokyou.ritual.SummonSession s = summon(core);
        boolean active = s.phase() != SummonPhase.IDLE;
        return new com.bitsson.gensokyou.ritual.RitualRenderState(com.bitsson.gensokyou.ritual.RitualRenderState.KIND_SUMMON, active, match.level(), s.fxStart(),
                com.bitsson.gensokyou.config.GensokyouConfig.FX_SUMMON_BURST_TICKS.get(), com.bitsson.gensokyou.config.GensokyouConfig.FX_SUMMON_PILLAR_HOLD_TICKS.get(),
                new long[0], 0, s.phase().ordinal());
    }

    private static SummonSession summon(SpiritPowerAccess core) {
        return (SummonSession) core.behaviorState();
    }

    private static int elapsed(ServerLevel level, SpiritPowerAccess core) {
        SummonSession s = summon(core);
        return s.fxStart() < 0 || level == null ? 0 : Math.max(0, (int) level.getGameTime() - s.fxStart());
    }

    @Override
    public long capacity(int level, SpiritPowerAccess core) {
        return summon(core).phase() == SummonPhase.IDLE ? 0L : summon(core).cost();
    }

    @Override
    public RitualBehaviorState newState() {
        return new SummonSession();
    }

    /**
     * UI 动作 id。
     *
     * <p><b>这是 {@code uiActions} 返回列表里的下标</b>，由服务端按下式回传：
     * <pre>
     *   客户端发 RitualCoreMenu.BUTTON_ACTION_BASE + i  →  服务端 onUiAction(..., id - BUTTON_ACTION_BASE)
     * </pre>
     * 而 {@code BUTTON_ACTION_BASE = 100}，且屏幕侧只有 {@code actionButtons[3]} 三个槽位、
     * 以 <b>下标</b> i 取 payload 里的第 i 个 action。故实际可用的 id 只有 0/1/2。
     *
     * <p>⚠️ <b>不要与 {@code InfoLine#actionId} 混淆</b>：那条是信息行点击通道，
     * 确实要求 &ge; 10（0/1 被框架启停按钮占用，故 {@code SeiiService.ACTION_ACCEPT = 10}、
     * {@code YaoyorozuGraceService.ACTION_REFINE_KEEP = 11} 那些 10/11 属于它）。
     * 本仪式只用按钮通道，故取 <b>0</b>。写成 10 会让服务端回传 0 与之不等，
     * 点击被 {@link #onUiAction} 的门禁静默吞掉——表现为"按钮能按但什么也不发生"。
     */
    public static final int ACTION_SUMMON = 0;

    private static final String KEY_BUTTON = "gui.gensokyou.ritual.hyakki.summon";
    private static final String KEY_CANCEL = "gui.gensokyou.ritual.hyakki.cancel";
    private static final String KEY_PROGRESS = "gui.gensokyou.ritual.hyakki.progress";
    private static final String KEY_PROGRESS_TIP = "gui.gensokyou.ritual.hyakki.progress_tip";
    private static final String KEY_SUPPLY = "gui.gensokyou.ritual.hyakki.supply";
    private static final String KEY_SUPPLY_TIP = "gui.gensokyou.ritual.hyakki.supply_tip";
    /** 空闲引导：必须把"启动即吞祭品"讲在前面，否则玩家会以为是先充能再扣料。 */
    private static final String KEY_IDLE = "gui.gensokyou.ritual.hyakki.idle";
    /** 会话期的祭品已耗警告：{@code UiAction} 没有 tooltip 通道，故只能占一行信息位。 */
    private static final String KEY_SPENT = "gui.gensokyou.ritual.hyakki.spent";

    private static final int COLOR_WARN = 0xFFE8912A;
    private static final int COLOR_PURPLE = 0xFFB39DDB;
    private static final int COLOR_BAD = 0xFFB22222;
    private static final int COLOR_DIM = 0xFF8A7A6D;

    // ---------------------------------------------------------------- 启动通道

    /**
     * 会话是唯一启动路径，通用启停通道整体让位。
     *
     * <p>不这样做就必然踩到 {@code payCost}——而"无门票"是硬需求。
     */
    @Override
    public boolean handlesStartViaUiAction() {
        return true;
    }

    /**
     * 退出红石代管触发：召唤是"起手式"，让红石脉冲在玩家不在场时悄悄烧掉祭品与十秒供灵，
     * 等于凭空蒸发玩家的存货。故既不覆写 {@code onRedstonePulse}，也退出默认的代管触发。
     */
    @Override
    public boolean redstoneTriggersUiAction() {
        return false;
    }

    @Override
    public List<UiAction> uiActions(ServerLevel level, BlockPos corePos, RitualMatch match,
                                     SpiritPowerAccess core) {
        // 会话进行中只给「取消」，且**不置灰**：它必须可点，否则玩家一旦断供就只能干等。
        return !summon(core).isIdle()
                ? List.of(new UiAction(ACTION_SUMMON, KEY_CANCEL))
                : List.of(new UiAction(ACTION_SUMMON, KEY_BUTTON));
    }

    @Override
    public InteractionResult onUiAction(ServerLevel level, BlockPos corePos, RitualMatch match,
                                        SpiritPowerAccess core, ServerPlayer player,
                                        int actionId) {
        if (actionId != ACTION_SUMMON) {
            return InteractionResult.PASS;
        }
        return trigger(level, corePos, player) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }

    /**
     * 按钮/红石的唯一入口。返回 false = 本次触发无任何效果。
     *
     * <p>刻意不实现 {@link #onRedstonePulse}：召唤是"起手式"，让红石脉冲在玩家不在场时
     * 悄悄烧掉祭品与十秒供灵，等于凭空蒸发玩家的存货。
     */
    public static boolean trigger(ServerLevel level, BlockPos corePos, @Nullable ServerPlayer player) {
        if (!(level.getBlockEntity(corePos) instanceof SpiritPowerAccess core)
                || !core.isPattern(RitualBehaviors.HYAKKI_YAGYO) || core.activeMatch() == null) {
            return false;
        }
        if (!summon(core).isIdle()) {
            cancel(level, corePos, core, player);
            return true;
        }
        return startSession(level, corePos, core, player);
    }

    /**
     * 开启会话：匹配配方 → <b>立即吞祭品</b> → 落容量与演出锚点 → 置 {@code enabled}。
     *
     * <p>顺序里最容易错的一条：{@code beginSummonSession} MIGHT 早于
     * {@code RitualRecipeMatcher.apply}——那样一旦扣料抛异常，玩家就白丢材料却没开成仪式。
     * 故先算好账本、扣完料，再开会话。
     */
    private static boolean startSession(ServerLevel level, BlockPos corePos,
                                        SpiritPowerAccess core, @Nullable ServerPlayer player) {
        RitualMatch match = core.activeMatch();
        List<RitualRecipe> candidates = RitualRecipeLoader.forPattern(match.patternId()).stream()
                .filter(RitualRecipe::activation)
                .filter(r -> r.minTier() <= match.level())
                .toList();
        if (candidates.isEmpty()) {
            RitualRecipeLoader.warnIfPatternMissing(match.patternId(), true);
            feedback(player, "msg.gensokyou.ritual_no_matching_recipe");
            return false;
        }
        var pools = RitualRecipeMatcher.collectPools(match, level);
        // 全部配方均为 match:"max"（子集命中），故走 matchMax 取消耗总量最大者。
        Optional<RitualRecipeMatcher.Match> matched = RitualRecipeMatcher.matchMax(candidates, pools);
        if (matched.isEmpty()) {
            feedback(player, "msg.gensokyou.ritual_no_matching_recipe");
            return false;
        }
        RitualRecipe recipe = matched.get().recipe();
        // 祭品在此刻吞掉（需求：启动即吞），此后不退还、不复查台面。
        RitualRecipeMatcher.apply(level, matched.get().takes());
        // 不 payCost：spCost 即容量。演出锚点**不在此落**——由缓存填满那一刻的 markSummonBurst 落。
        summon(core).begin(recipe.id(), recipe.spCost(), match.level()); core.setActiveRecipeId(recipe.id()); core.markDirty();
        core.setEnabled(true);
        core.broadcastPedestalsActive(true);
        ModNetworking.sendRitualInfoToViewers(level, corePos);
        feedback(player, "msg.gensokyou.hyakki_started");
        return true;
    }

    /** 取消会话：抽干缓存、清会话、归零。祭品与已投入灵力均不退（需求）。 */
    private static void cancel(ServerLevel level, BlockPos corePos, SpiritPowerAccess core,
                               @Nullable ServerPlayer player) {
        // 先关 enabled 再清：setEnabled(false) 内部会 clearSummonSession（覆盖全部停机路径），
        // 这里再显式清一次是幂等的，但保证即便将来那条挂钩改了也一定清干净。
        core.setEnabled(false);
        summon(core).clear(); core.extract(core.getStored()); core.setActiveRecipeId(null); core.markDirty();
        core.broadcastPedestalsActive(false);
        ModNetworking.sendRitualInfoToViewers(level, corePos);
        feedback(player, "msg.gensokyou.hyakki_cancelled");
    }

    // ---------------------------------------------------------------- 推进

    @Override
    public void serverTick(ServerLevel level, BlockPos corePos, RitualMatch match,
                           SpiritPowerAccess core) {
        switch (summon(core).phase()) {
            case CHARGING -> tickCharging(level, corePos, core);
            case BURST, PILLAR -> tickPerformance(level, corePos, core);
            case IDLE -> core.setEnabled(false);
        }
    }

    /** 吸灵结算周期（tick）：1 秒一次，故按「每秒速率」成批抽取，无需定点进位。 */
    private static final int PULL_PERIOD_TICKS = 20;

    /**
     * 充能段：<b>吸灵</b>（主动抽取）→ 判满则转入爆散段。
     *
     * <p><b>启动当刻不校验供灵</b>（需求明确）：配方满足即吞祭品、进本状态，之后能否吸满
     * 由玩家自己的供灵布局决定。扣料与开状态之间不设任何"够不够"闸门。
     *
     * <p><b>走 {@code SpiritPowerHelper.collect} 三段式主动抽取</b>（槽内灵力核心不限速率 →
     * 核心自身储灵 → 半径 3 格内其他成型核心），与源初造化 / 八百万神恩 / 星移同款。
     * 刻意<b>不</b>只靠 {@code tickBatteryToCacheFill()}：那是「电池→缓存」的<b>推</b>路径，
     * 受 {@code fillRatePerSecond} 限速且只能吃掉槽核自身存量——一颗 T0 核（存量 50,000、
     * 速率 1,000/s）面对 100,000 的容量会先被限速拖 50 秒、再在 50% 处<b>永久卡死</b>，
     * 且周围核心的储灵完全参与不进来。
     *
     * <p>抽取量按 {@link #spiritInRatePerSecond} 的同一口径（配方消耗 ÷ divisor）成批给，
     * 使「开一次门约十秒」这个节拍对推（路由注入）与拉（本方法）两条路同时成立；
     * 供灵不足时只是更慢，MUST NOT 变成拒绝或回退。
     */
    private static void tickCharging(ServerLevel level, BlockPos corePos,
                                     SpiritPowerAccess core) {
        long capacity = core.getCapacity();
        // 先补料再判满：否则玩家"最后一刻断供"时会差一档卡住，而料其实还在。
        if (capacity > 0L && core.ageTicks() % PULL_PERIOD_TICKS == 0L) {
            long want = Math.min(capacity - core.getStored(), pullPerSecond(core));
            if (want > 0L) {
                core.receive(SpiritPowerHelper.collect(level, corePos, core, want));
            }
        }
        if (capacity > 0L && core.getStored() >= capacity) {
            // 判满这一刻才落演出锚点：球/闪电在充能段由「相位」驱动（时长随供灵而变），
            // 爆散与光柱才从这一刻起算。锚点若打在启动瞬间，零供灵时整段演出会在 1 秒内播完。
            summon(core).markBurst((int) level.getGameTime()); core.markDirty();
            // 爆散当刻：这是"判满"这一事件唯一干净的一次性落点，故音效就播在这里。
            burstSound(level, corePos);
            return;
        }
        if (core.ageTicks() % PULL_PERIOD_TICKS == 0L) {
            ModNetworking.sendRitualInfoToViewers(level, corePos);
        }
    }

    /** 每秒主动抽取量（= 受灵汇速率同口径），下限 1 杜绝配置写坏时彻底不吸。 */
    private static long pullPerSecond(SpiritPowerAccess core) {
        if (!!summon(core).isIdle()) {
            return 0L;
        }
        int divisor = Math.max(1, GensokyouConfig.SUMMON_IN_RATE_DIVISOR.get());
        return Math.max(1L, summon(core).cost() / divisor);
    }

    /**
     * 爆散段与降临段：纯时间推进，MUST NOT 逐 tick 推渲染态。
     *
     * <p>相位的唯一事实源是 {@code summonElapsed()}（= gameTime − 持久化锚点），服务端与
     * 客户端各自算，故两者天然同步。收束时把缓存抽干并回到 IDLE，核心即可再次召唤。
     */
    private static void tickPerformance(ServerLevel level, BlockPos corePos,
                                        SpiritPowerAccess core) {
        int elapsed = elapsed(level, core);
        if (summon(core).phase() == SummonPhase.BURST) {
            if (elapsed >= GensokyouConfig.FX_SUMMON_BURST_TICKS.get()) {
                summon(core).setPhase(SummonPhase.PILLAR); core.markDirty();
                spawnBoss(level, corePos, core);
            }
            return;
        }
        int total = GensokyouConfig.FX_SUMMON_BURST_TICKS.get()
                + GensokyouConfig.FX_SUMMON_PILLAR_HOLD_TICKS.get()
                + GensokyouConfig.FX_SUMMON_PILLAR_RETRACT_TICKS.get();
        if (elapsed < total) {
            return;
        }
        // 收束：抽干缓存并回到 IDLE，核心即可再次召唤。
        core.setEnabled(false);
        summon(core).clear(); core.extract(core.getStored()); core.setActiveRecipeId(null); core.markDirty();
        core.broadcastPedestalsActive(false);
        ModNetworking.sendRitualInfoToViewers(level, corePos);
    }

    /**
     * 降临光柱转相那一帧落地 BOSS（{@code boss-summon-effect}）。
     *
     * <p>落点 = 核心正上方，与光柱落点一致。effect 为空或未注册时静默跳过——
     * 本仪式本身不要求必须召出东西（探针配方可只验演出）。
     */
    private static void spawnBoss(ServerLevel level, BlockPos corePos, SpiritPowerAccess core) {
        String effect = effectOf(core);
        if (effect == null || effect.isEmpty() || "-".equals(effect)) {
            return;
        }
        SummonBossEffects.spawnFromEffect(level, corePos, effect);
    }

    /**
     * 结构失效：清会话。不清的话核心会带着非零容量继续供灵，且演出锚点悬在半空。
     */
    @Override
    public void onStructureLost(ServerLevel level, BlockPos corePos) {
        if (level.getBlockEntity(corePos) instanceof SpiritPowerAccess core) {
            summon(core).clear(); core.extract(core.getStored()); core.setActiveRecipeId(null); core.markDirty();
        }
    }

    /**
     * 爆散当刻的三层音效，在 {@link #tickCharging} 判满转相时播一次。
     *
     * <p>低频轰鸣 + 碎裂 + 一记低频震荡，三层叠出体量。刻意<b>不加</b>镜头晃动、画面压暗
     * 与击退——前两者是屏幕级后处理（需求明令不要），击退是玩法而非表现，而本仪式不设击退。
     */
    public static void burstSound(ServerLevel level, BlockPos corePos) {
        level.playSound(null, corePos, SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.BLOCKS, 1.6F, 0.7F);
        level.playSound(null, corePos, SoundEvents.AMETHYST_CLUSTER_BREAK,
                SoundSource.BLOCKS, 1.0F, 0.70F);
        level.playSound(null, corePos, SoundEvents.WARDEN_SONIC_BOOM,
                SoundSource.BLOCKS, 1.0F, 0.85F);
    }

    // ---------------------------------------------------------------- 灵力端点

    /**
     * 受灵汇速率上限 = <b>锁定配方消耗 ÷ {@code summonInRateDivisor}</b>（默认 10 秒）。
     *
     * <p>配方在会话开始时锁进 NBT，故该值<b>在会话期间恒定</b>——不像结界破碎那样随
     * 附近归元的实际输出抖动。稳定的好处是"开一次门 10 秒"成为一个可预期的仪式节拍。
     *
     * <p><b>空闲返回 0</b>：这是本仪式"平时无缓存"的实现，也是对路由器隐身的机制
     * （{@code ResonanceRelayBehavior} 的汇端筛选要求受灵上限 &gt; 0）。启动瞬间 0 → cost/10
     * 的跳变落在 in 方向，是允许的（红线只约束 out，源闪断会断链）。
     *
     * <p>整除：配方 {@code spCost} 恒为 10 的倍数（灵力量级指数增长），故不丢量；仍取
     * {@code max(1, ·)} 下限，杜绝配置写坏时本核心永远进不了 {@code sinks}。
     */
    @Override
    public long spiritInRatePerSecond(ServerLevel level, BlockPos corePos, RitualMatch match,
                                      SpiritPowerAccess core) {
        if (!!summon(core).isIdle()) {
            return 0L;
        }
        long cost = summon(core).cost();
        int divisor = Math.max(1, GensokyouConfig.SUMMON_IN_RATE_DIVISOR.get());
        return cost <= 0L ? 0L : Math.max(1L, cost / divisor);
    }

    /**
     * 开放灵力核心槽：槽内核是<b>吸灵三段式的第一段</b>（不限速率抽取，见
     * {@code SpiritPowerHelper#collect}），方向与默认口径一致，故返回 true。
     *
     * <p>本仪式 MIGHT NOT 再调 {@code tickBatteryToCacheFill()}：那是「电池→缓存」的推路径、
     * 受 {@code fillRatePerSecond} 限速且只吃槽核自身存量，与主动抽取重复且更慢。
     * 两者只应取一，本仪式取主动抽取 —— <b>方向是同一条，机制不同</b>。
     */
    @Override
    public boolean refillsCacheFromSocket() {
        return false;
    }

    /**
     * 三档<b>可用存量</b>快照（槽核 / 自身+半径内其他核心 / 合计），供界面与调试判定
     * "这一波到底还够不够吸满"。
     *
     * <p>刻意报<b>存量</b>而非速率：本仪式是主动抽取，槽核不限速率，
     * {@code fillRatePerSecond} 已不参与充能——继续报它会给出"慢"的错觉，而真实约束是
     * "够不够"。玩家真正需要判断的是"我这三档加起来能不能凑满容量"。
     */
    public record Supply(long socket, long stored, long total) {

        /** 相对当前容量的缺口；&le; 0 即"存量已足够吸满"。 */
        public long deficit(long capacity) {
            return Math.max(0L, capacity - total);
        }
    }

    public static Supply supply(ServerLevel level, BlockPos corePos, SpiritPowerAccess core) {
        long socket = 0L;
        if (core.batteryStack().getItem() instanceof SpiritCoreItem item) {
            socket = SpiritCoreItem.getStored(core.batteryStack());
        }
        // available() 已含槽核，故自身+周围 = available − socket
        long total = Math.max(0L, SpiritPowerHelper.available(level, corePos, core));
        return new Supply(socket, Math.max(0L, total - socket), total);
    }

    // ---------------------------------------------------------------- 界面

    @Override
    public List<InfoLine> uiInfo(ServerLevel level, BlockPos corePos, RitualMatch match,
                                 SpiritPowerAccess core) {
        List<InfoLine> lines = new ArrayList<>();
        if (!!summon(core).isIdle()) {
            // 空闲：只补一行「本次召唤需先把灵力灌进核心」的引导，不列容量/供灵。
            lines.add(new InfoLine(KEY_IDLE, new String[0], "", COLOR_DIM, -1F, null,
                    0, InfoLine.CONTROL_NONE, InfoLine.LINK_NONE, "", new String[0]));
            return lines;
        }
        long stored = core.getStored();
        long capacity = core.getCapacity();
        long declared = spiritInRatePerSecond(level, corePos, match, core);
        Supply supply = supply(level, corePos, core);
        float progress = capacity > 0L ? Math.min(1F, (float) stored / (float) capacity) : 0F;

        // 容量进度行：可见行只放 compact 短值，原始值只进 tipped（信息区实得宽度约 116px）。
        lines.add(new InfoLine(KEY_PROGRESS,
                new String[]{InfoLine.compact(stored), InfoLine.compact(capacity)},
                "", COLOR_PURPLE, progress, null,
                0, InfoLine.CONTROL_NONE, InfoLine.LINK_NONE, KEY_PROGRESS_TIP,
                new String[]{String.valueOf(stored), String.valueOf(capacity),
                        String.valueOf(declared)}));

        // 供灵存量行：槽核 / 自身+周围 两分列。存量不足以吸满容量时显式转红并给出缺口——
        // 否则玩家只会盯着一个停住的进度条，而界面上没有任何数字告诉他"你其实不够"。
        long deficit = supply.deficit(capacity);
        lines.add(new InfoLine(KEY_SUPPLY,
                new String[]{InfoLine.compact(supply.socket()),
                        InfoLine.compact(supply.stored()),
                        InfoLine.compact(deficit)},
                "", deficit > 0L ? COLOR_BAD : COLOR_WARN, -1F, null,
                0, InfoLine.CONTROL_NONE, InfoLine.LINK_NONE, KEY_SUPPLY_TIP,
                new String[]{String.valueOf(supply.socket()), String.valueOf(supply.stored()),
                        String.valueOf(supply.total()), String.valueOf(deficit),
                        String.valueOf(declared)}));
        // 祭品已耗警告：UiAction 没有 tooltip 通道，只能占一行信息位。
        lines.add(new InfoLine(KEY_SPENT, new String[0], "", COLOR_DIM, -1F, null,
                0, InfoLine.CONTROL_NONE, InfoLine.LINK_NONE, "", new String[0]));
        return lines;
    }

    private static void feedback(@Nullable ServerPlayer player, String key) {
        if (player != null) {
            player.displayClientMessage(Component.translatable(key), true);
        }
    }

    /** 会话自检单行（/gs_debug summon 用）。 */
    public static String debugSummary(ServerLevel level, BlockPos corePos, RitualMatch match,
                                      SpiritPowerAccess core) {
        Supply s = supply(level, corePos, core);
        return "pattern=" + match.patternId()
                + " level=" + match.level()
                + " phase=" + summon(core).phase()
                + " stored=" + core.getStored()
                + " cap=" + core.getCapacity()
                + " pullPerSec=" + pullPerSecond(core)
                + " socketStored=" + s.socket()
                + " storedAround=" + s.stored()
                + " availTotal=" + s.total()
                + " deficit=" + s.deficit(core.getCapacity())
                + " fxStart=" + summon(core).fxStart()
                + " elapsed=" + elapsed(level, core)
                + " tier=" + summon(core).tier()
                + " recipe=" + summon(core).recipeId()
                + " effect=" + effectOf(core);
    }

    /** 锁定配方的 effect 串（后续 BOSS 生成的挂钩点，本变更只读不解释）。 */
    public static String effectOf(SpiritPowerAccess core) {
        return Optional.ofNullable(summon(core).recipeId())
                .flatMap(RitualRecipeLoader::byId)
                .map(RitualRecipe::effect)
                .orElse("-");
    }

    /** pattern 是否已加载（调试与自检用）。 */
    public static boolean patternLoaded() {
        return RitualPatternLoader.byId(RitualBehaviors.HYAKKI_YAGYO).isPresent();
    }
}
