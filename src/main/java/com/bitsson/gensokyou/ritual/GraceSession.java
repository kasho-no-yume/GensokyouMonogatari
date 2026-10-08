package com.bitsson.gensokyou.ritual;

import com.bitsson.gensokyou.ritual.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GraceSession extends RitualBehaviorState {

    private static final String TAG_GRACE_PHASE = "GracePhase";
    private static final String TAG_GRACE_SESSION = "GraceSession";
    private static final String TAG_GRACE_COLLECTED = "GraceCollected";
    private static final String TAG_GRACE_COST = "GraceCost";
    private static final String TAG_GRACE_TICKS = "GraceTicks";
    private static final String TAG_GRACE_TIER = "GraceTier";
    private static final String TAG_GRACE_REFINE = "GraceRefine";
    private static final String TAG_GRACE_RECIPE = "GraceRecipe";
    private static final String TAG_GRACE_INITIATOR = "GraceInitiator";
    private static final String TAG_GRACE_PENDING = "GracePending";

    private GracePhase phase = GracePhase.IDLE;
    private long sessionId;
    private @Nullable ResourceLocation recipeId;
    private @Nullable java.util.UUID initiator;
    /** 配方阶级（effect 的 N；1..5）。 */
    private int tier;
    /** true=洗练配方；false=进阶配方。 */
    private boolean refine;
    private long collected;
    /** 会话容量口径：锁定配方的 spCost；空闲为 0（不启动不缓存灵力）。 */
    private long cost;
    private int ticks;
    /** 洗练预览（当场制：不入 NBT，重启/清退即作废）。 */
    private @Nullable com.bitsson.gensokyou.spirit.grace.GraceNumbers.GraceRoll pendingRefine;

    public GracePhase phase() {
        return phase;
    }

    public long sessionId() {
        return sessionId;
    }

    public @Nullable ResourceLocation recipeId() {
        return recipeId;
    }

    public @Nullable java.util.UUID initiator() {
        return initiator;
    }

    public int tier() {
        return tier;
    }

    public boolean refine() {
        return refine;
    }

    public long collected() {
        return collected;
    }

    public long cost() {
        return cost;
    }

    public int ticks() {
        return ticks;
    }

    public @Nullable com.bitsson.gensokyou.spirit.grace.GraceNumbers.GraceRoll pendingRefine() {
        return pendingRefine;
    }

    /** 启动新会话：换代、锁配方/阶级/类型与 initiator，进聚灵；返回新会话 id。 */
    public long begin(ResourceLocation recipe, long spCost, java.util.UUID who,
                      int recipeTier, boolean isRefine) {
        sessionId++;
        recipeId = recipe;
        cost = spCost;
        initiator = who;
        tier = recipeTier;
        refine = isRefine;
        collected = 0L;
        ticks = 0;
        phase = GracePhase.PAYING;
        pendingRefine = null;
        return sessionId;
    }

    public void addCollected(long amount) {
        collected += amount;
    }

    /** 聚灵足额 → 效果已 apply，进入演出。 */
    public void enterPerform() {
        phase = GracePhase.PERFORM;
        ticks = 0;
    }

    public void advancePerform() {
        ticks++;
    }

    /** 演出结束（洗练线）：预览挂会话等待当场决策。 */
    public void enterReview(com.bitsson.gensokyou.spirit.grace.GraceNumbers.GraceRoll roll) {
        pendingRefine = roll;
        phase = GracePhase.REVIEW;
        ticks = 0;
    }

    /** 洗练 roll 在 apply 瞬间挂上（跨演出期携带；REVIEW 提升时转正）。 */
    public void stageRefine(com.bitsson.gensokyou.spirit.grace.GraceNumbers.GraceRoll roll) {
        pendingRefine = roll;
    }

    /** 演出结束升为待决策态（保留 staged 预览）。 */
    public void promoteReview() {
        phase = GracePhase.REVIEW;
        ticks = 0;
    }

    public void clearPendingRefine() {
        pendingRefine = null;
        if (phase == GracePhase.REVIEW) {
            phase = GracePhase.IDLE;
        }
    }

    /** 清退回空闲（正常收尾/中止/取消共用）。 */
    public void clear() {
        phase = GracePhase.IDLE;
        recipeId = null;
        initiator = null;
        tier = 0;
        refine = false;
        collected = 0L;
        cost = 0L;
        ticks = 0;
        pendingRefine = null;
    }

    @Override
    public boolean isEmpty() {
        return phase == GracePhase.IDLE;
    }

    public void save(CompoundTag tag) {
        if (phase == GracePhase.IDLE) {
            return;
        }
        // REVIEW 也写盘：待决预览永久存续（跨区块卸载 / 服务器重启），
        // 决策权绑定 initiator，不会被他人关闭界面或掉线吞掉。
        tag.putString(TAG_GRACE_PHASE, phase.name());
        tag.putLong(TAG_GRACE_SESSION, sessionId);
        tag.putLong(TAG_GRACE_COLLECTED, collected);
        tag.putLong(TAG_GRACE_COST, cost);
        tag.putInt(TAG_GRACE_TICKS, ticks);
        tag.putInt(TAG_GRACE_TIER, tier);
        tag.putBoolean(TAG_GRACE_REFINE, refine);
        if (recipeId != null) {
            tag.putString(TAG_GRACE_RECIPE, recipeId.toString());
        }
        if (initiator != null) {
            tag.putUUID(TAG_GRACE_INITIATOR, initiator);
        }
        // REVIEW 的待决 roll 必须落盘：否则重启后 REVIEW 回来了但没有可决策的 roll，
        // 玩家既看不到结果也无法"全收 / 保留"，等于待决预览丢失。
        if (phase == GracePhase.REVIEW && pendingRefine != null) {
            tag.put(TAG_GRACE_PENDING, writeGraceRoll(pendingRefine));
        }
    }

    private static CompoundTag writeGraceRoll(
            com.bitsson.gensokyou.spirit.grace.GraceNumbers.GraceRoll roll) {
        CompoundTag t = new CompoundTag();
        t.putInt("tier", roll.tier());
        t.putFloat("maxGain", roll.maxGain());
        t.putFloat("powerGain", roll.powerGain());
        ListTag list = new ListTag();
        for (var entry : roll.contributions().entrySet()) {
            CompoundTag one = new CompoundTag();
            one.putString("k", entry.getKey().id());
            one.putFloat("v", entry.getValue());
            list.add(one);
        }
        t.put("contrib", list);
        return t;
    }

    private static @Nullable com.bitsson.gensokyou.spirit.grace.GraceNumbers.GraceRoll
    readGraceRoll(CompoundTag t) {
        if (!t.contains("contrib", Tag.TAG_LIST)) {
            return null;
        }
        Map<com.bitsson.gensokyou.spirit.attr.AttributeKey, Float> contrib = new LinkedHashMap<>();
        ListTag list = t.getList("contrib", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag one = list.getCompound(i);
            var key = com.bitsson.gensokyou.spirit.attr.AttributeKey.byId(one.getString("k"));
            if (key != null) {
                contrib.put(key, one.getFloat("v"));
            }
        }
        return new com.bitsson.gensokyou.spirit.grace.GraceNumbers.GraceRoll(
                t.getInt("tier"), t.getFloat("maxGain"), t.getFloat("powerGain"),
                Map.copyOf(contrib));
    }

    /**
     * 读档：PAYING 复活（initiator 离线时由服务侧首 tick 取消退还）；REVIEW 复活为待决态
     * （暂存的 roll 不写盘而是按 tier 重新 roll —— 精确复现需要额外持久化，收益不匹配）；
     * PERFORM 视为不可续作，清退。
     */
    public void load(CompoundTag tag) {
        if (!tag.contains(TAG_GRACE_PHASE)) {
            return;
        }
        try {
            phase = GracePhase.valueOf(tag.getString(TAG_GRACE_PHASE));
        } catch (IllegalArgumentException exception) {
            phase = GracePhase.IDLE;
        }
        if (phase == GracePhase.IDLE) {
            return;
        }
        if (phase == GracePhase.PERFORM) {
            clear(); // 演出不可续作：效果已入账，清态不重演
            return;
        }
        sessionId = tag.getLong(TAG_GRACE_SESSION);
        collected = tag.getLong(TAG_GRACE_COLLECTED);
        cost = tag.getLong(TAG_GRACE_COST);
        ticks = tag.getInt(TAG_GRACE_TICKS);
        tier = tag.getInt(TAG_GRACE_TIER);
        refine = tag.getBoolean(TAG_GRACE_REFINE);
        recipeId = tag.contains(TAG_GRACE_RECIPE)
                ? ResourceLocation.tryParse(tag.getString(TAG_GRACE_RECIPE)) : null;
        initiator = tag.hasUUID(TAG_GRACE_INITIATOR) ? tag.getUUID(TAG_GRACE_INITIATOR) : null;
        pendingRefine = null;
        if (phase == GracePhase.REVIEW && tag.contains(TAG_GRACE_PENDING, Tag.TAG_COMPOUND)) {
            pendingRefine = readGraceRoll(tag.getCompound(TAG_GRACE_PENDING));
        }
        // REVIEW 却读不出待决 roll（老存档 / 词条键已退役）→ 退回 IDLE，
        // 绝不停在一个"有 REVIEW 相位、无可决策内容"的死状态。
        if (phase == GracePhase.REVIEW && pendingRefine == null) {
            phase = GracePhase.IDLE;
        }
    }
}


