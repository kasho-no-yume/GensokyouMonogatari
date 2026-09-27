package com.bitsson.gensokyou.ritual.behavior;

import com.bitsson.gensokyou.item.weapon.RuneAffix;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 星移之仪会话态持有者（BE 内，存储/逻辑在 {@code SeiiService} 之外无副作用）。
 *
 * <p>阶段：IDLE →（校验+换代锁配方）PAYING →（spCost 蓄满）扣催化剂+暂存新词条 →
 * PERFORM（纯演出，核组件零写入）→ REVIEW（待决，永久存续、决策权绑定 initiator）。
 *
 * <p><b>暂存语义</b>：洗练结果在 REVIEW 落定前只存在本对象里，核组件一个字节都没动。
 * 任一中止路径（结构失效 / 配方丢失 / initiator 离线）都保持核原样。
 */
public final class SeiiSession {

    public enum Phase { IDLE, PAYING, PERFORM, REVIEW }

    /**
     * 暂存的一次洗练结果（未落库）。
     *
     * <p>核在核心 GUI 的专用目标槽里，故无需记台位坐标；采纳时按"槽内仍是同一枚核且
     * {@code rune_affixes} 仍等于 {@link #before()} 快照"复验。
     */
    public record Pending(int coreTier, int ritualLevel,
                          List<RuneAffix> before, List<RuneAffix> after, int pityBefore) {
    }

    private Phase phase = Phase.IDLE;
    private long sessionId;
    private long cost;
    private long collected;
    private int ticks;
    private int coreTier;
    @Nullable
    private ResourceLocation recipeId;
    @Nullable
    private UUID initiator;
    @Nullable
    private Pending pending;

    public Phase phase() {
        return phase;
    }

    public long sessionId() {
        return sessionId;
    }

    public long cost() {
        return cost;
    }

    public long collected() {
        return collected;
    }

    public int ticks() {
        return ticks;
    }

    public int coreTier() {
        return coreTier;
    }

    @Nullable
    public ResourceLocation recipeId() {
        return recipeId;
    }

    @Nullable
    public UUID initiator() {
        return initiator;
    }

    @Nullable
    public Pending pending() {
        return pending;
    }

    public long begin(ResourceLocation recipe, long spCost, int tier, UUID who) {
        this.sessionId++;
        this.recipeId = recipe;
        this.cost = spCost;
        this.collected = 0L;
        this.ticks = 0;
        this.coreTier = tier;
        this.initiator = who;
        this.pending = null;
        this.phase = Phase.PAYING;
        return this.sessionId;
    }

    public void addCollected(long amount) {
        if (amount > 0L) {
            this.collected += amount;
        }
    }

    public void stage(Pending staged) {
        this.pending = staged;
        this.phase = Phase.PERFORM;
        this.ticks = 0;
    }

    public void tickPerform() {
        this.ticks++;
    }

    public void promoteReview() {
        this.phase = Phase.REVIEW;
        this.ticks = 0;
    }

    public void clear() {
        this.phase = Phase.IDLE;
        this.recipeId = null;
        this.initiator = null;
        this.pending = null;
        this.cost = 0L;
        this.collected = 0L;
        this.ticks = 0;
        this.coreTier = 0;
    }

    // ---- 持久化 ----

    public void save(CompoundTag tag) {
        if (phase == Phase.IDLE) {
            return;
        }
        tag.putString("SeiiPhase", phase.name());
        tag.putLong("SeiiSession", sessionId);
        tag.putLong("SeiiCost", cost);
        tag.putLong("SeiiCollected", collected);
        tag.putInt("SeiiTicks", ticks);
        tag.putInt("SeiiCoreTier", coreTier);
        if (recipeId != null) {
            tag.putString("SeiiRecipe", recipeId.toString());
        }
        if (initiator != null) {
            tag.putUUID("SeiiInitiator", initiator);
        }
        // REVIEW 态持久化暂存结果：待决永久存续，跨区块卸载/重启不丢
        if (phase == Phase.REVIEW && pending != null) {
            CompoundTag p = new CompoundTag();
            p.putInt("coreTier", pending.coreTier());
            p.putInt("ritualLevel", pending.ritualLevel());
            p.putInt("pityBefore", pending.pityBefore());
            p.put("before", writeAffixes(pending.before()));
            p.put("after", writeAffixes(pending.after()));
            tag.put("SeiiPending", p);
        }
    }

    /**
     * 读档：PAYING / REVIEW 复活；PERFORM 不可续作（效果未落库、暂存已在 NBT 里，
     * 为避免"演出中断=白扣材料"的不一致，直接清退退还）；IDLE 空转。
     */
    public void load(CompoundTag tag) {
        if (!tag.contains("SeiiPhase")) {
            return;
        }
        try {
            phase = Phase.valueOf(tag.getString("SeiiPhase"));
        } catch (IllegalArgumentException exception) {
            phase = Phase.IDLE;
            return;
        }
        if (phase == Phase.IDLE) {
            return;
        }
        if (phase == Phase.PERFORM) {
            clear();
            return;
        }
        sessionId = tag.getLong("SeiiSession");
        cost = tag.getLong("SeiiCost");
        collected = tag.getLong("SeiiCollected");
        ticks = tag.getInt("SeiiTicks");
        coreTier = tag.getInt("SeiiCoreTier");
        recipeId = tag.contains("SeiiRecipe")
                ? ResourceLocation.tryParse(tag.getString("SeiiRecipe")) : null;
        initiator = tag.hasUUID("SeiiInitiator") ? tag.getUUID("SeiiInitiator") : null;
        pending = null;
        if (phase == Phase.REVIEW && tag.contains("SeiiPending", Tag.TAG_COMPOUND)) {
            CompoundTag p = tag.getCompound("SeiiPending");
            pending = new Pending(p.getInt("coreTier"), p.getInt("ritualLevel"),
                    readAffixes(p.getList("before", Tag.TAG_COMPOUND)),
                    readAffixes(p.getList("after", Tag.TAG_COMPOUND)),
                    p.getInt("pityBefore"));
        } else if (phase == Phase.REVIEW) {
            // 旧档/数据损坏：无法复现暂存 → 清退（材料已沉没，核心原样）
            clear();
        }
    }

    private static ListTag writeAffixes(List<RuneAffix> affixes) {
        ListTag list = new ListTag();
        for (RuneAffix a : affixes) {
            CompoundTag t = new CompoundTag();
            t.putString("id", a.affixId());
            t.putFloat("v", a.value());
            list.add(t);
        }
        return list;
    }

    private static List<RuneAffix> readAffixes(ListTag list) {
        List<RuneAffix> out = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            out.add(new RuneAffix(t.getString("id"), t.getFloat("v")));
        }
        return List.copyOf(out);
    }
}
