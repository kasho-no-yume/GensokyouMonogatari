package com.bitsson.gensokyou.ritual;

import com.bitsson.gensokyou.ritual.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import javax.annotation.Nullable;

public final class SummonSession extends RitualBehaviorState {

    private static final String TAG_SUMMON_PHASE = "SummonPhase";
    private static final String TAG_SUMMON_COST = "SummonCost";
    private static final String TAG_SUMMON_RECIPE = "SummonRecipe";
    private static final String TAG_SUMMON_TIER = "SummonTier";
    /**
     * 召唤演出的<b>绝对</b> gameTime 锚点（充能段起点）。
     *
     * <p>持久化是必须的：它让「充能 → 爆散 → 降临」三段演出在区块卸载 / 重连之后仍能由
     * 服务端与客户端各自自算出同一相位，而不必逐 tick 同步。参考
     * {@code SukimaBlockEntity#fxStartGameTime} 的同款教训——用 transient 标记判重播会在方块
     * 实体重建后失效，导致整段演出重播；缺锚点则会让演出永远停在第 0 tick。
     */
    private static final String TAG_SUMMON_FX_START = "SummonFxStart";

    private SummonPhase phase = SummonPhase.IDLE;
    /** 会话容量口径：锁定配方的 spCost；空闲为 0（不启动不缓存灵力）。 */
    private long cost;
    private @Nullable ResourceLocation recipeId;
    private int tier;
    /** 演出起始 gameTime（绝对锚点，持久化）；{@code < 0} = 从未播放过演出。 */
    private int fxStart = -1;

    public boolean isIdle() {
        return phase == SummonPhase.IDLE;
    }

    public SummonPhase phase() {
        return phase;
    }

    public long cost() {
        return cost;
    }

    public @Nullable ResourceLocation recipeId() {
        return recipeId;
    }

    public int tier() {
        return tier;
    }

    public int fxStart() {
        return fxStart;
    }

    /** 启动召唤会话：锁配方（spCost 即容量）、记结构层号，不落演出锚点。 */
    public void begin(ResourceLocation recipe, long spCost, int tier) {
        this.phase = SummonPhase.CHARGING;
        this.cost = Math.max(0L, spCost);
        this.recipeId = recipe;
        this.tier = Math.max(1, tier);
        this.fxStart = -1;
    }

    /** 缓存填满 → 落下演出锚点并转入爆散段。 */
    public void markBurst(int gameTime) {
        this.fxStart = gameTime;
        this.phase = SummonPhase.BURST;
    }

    /** 清掉演出锚点（退回演出段未开始）。 */
    public void clearFxStart() {
        if (fxStart < 0) {
            return;
        }
        fxStart = -1;
    }

    /** 会话阶段推进（行为侧按锚点自算后调用）。 */
    public void setPhase(SummonPhase phase) {
        this.phase = phase;
    }

    /** 会话清退至 IDLE：容量归零、演出锚点作废。 */
    public void clear() {
        phase = SummonPhase.IDLE;
        cost = 0L;
        recipeId = null;
        tier = 0;
        fxStart = -1;
    }

    @Override
    public boolean isEmpty() {
        return isIdle() && cost == 0L && recipeId == null && tier == 0 && fxStart < 0;
    }

    public void save(CompoundTag tag) {
        if (isIdle() && cost == 0L && recipeId == null && fxStart < 0) {
            return;
        }
        tag.putString(TAG_SUMMON_PHASE, phase.name());
        tag.putLong(TAG_SUMMON_COST, cost);
        tag.putByte(TAG_SUMMON_TIER, (byte) Math.min(127, Math.max(0, tier)));
        tag.putInt(TAG_SUMMON_FX_START, fxStart);
        if (recipeId != null) {
            tag.putString(TAG_SUMMON_RECIPE, recipeId.toString());
        }
    }

    public void load(CompoundTag tag) {
        if (!tag.contains(TAG_SUMMON_PHASE)) {
            return;
        }
        try {
            phase = SummonPhase.valueOf(tag.getString(TAG_SUMMON_PHASE));
        } catch (IllegalArgumentException exception) {
            phase = SummonPhase.IDLE;
        }
        cost = Math.max(0L, tag.getLong(TAG_SUMMON_COST));
        recipeId = tag.contains(TAG_SUMMON_RECIPE)
                ? ResourceLocation.tryParse(tag.getString(TAG_SUMMON_RECIPE)) : null;
        tier = tag.getByte(TAG_SUMMON_TIER) & 0xFF;
        fxStart = tag.contains(TAG_SUMMON_FX_START) ? tag.getInt(TAG_SUMMON_FX_START) : -1;
        // 旧档迁移：非 IDLE 却缺锚点有两种成因，处置不同。
        //  ① 旧格式（锚点曾打在启动瞬间）：值 < 0 ⇒ 演出段根本没开始过 → 退回充能态，
        //     重新吸满时由 markSummonBurst 落新锚点。
        //  ② 荒谬的负 gameTime（时钟回拨/数据损坏）：同样退回充能态，绝不拿它当演出计时。
        // 无论哪种，MUST NOT 保留一个无锚点的 BURST/PILLAR 态——那会让客户端拿到
        // elapsed=0 反复重播爆散段。
        if (!isIdle() && fxStart < 0) {
            phase = SummonPhase.CHARGING;
        }
    }
}

