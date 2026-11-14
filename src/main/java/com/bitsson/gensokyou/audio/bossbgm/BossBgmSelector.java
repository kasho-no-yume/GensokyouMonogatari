package com.bitsson.gensokyou.audio.bossbgm;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 选曲器：把「场上这些 BOSS」映射成「现在该放哪首」。<b>纯函数，可离线断言。</b>
 *
 * <h2>排序</h2>
 * <ol>
 *   <li>正在以本玩家为目标的优先——那是"正在打我的那只"；</li>
 *   <li>距离近的优先；</li>
 *   <li>UUID 字典序兜底，保证同分时结果稳定（不随实体列表顺序抖动）。</li>
 * </ol>
 *
 * <h2>sticky</h2>
 * 当前提供者仍在场时，只有出现<b>显著更近</b>（距离优于 {@link #SWITCH_IMPROVEMENT}）
 * 的候选才换曲。否则两只同阶 BOSS 交叉走位会每 10 tick 切一次歌。
 *
 * <p>换曲的键是<b>事件 id</b>：选取结果的事件 id 与当前相同即视为「不用换」，
 * 所以同一只 BOSS 切符卡、同阶换 BOSS 都不会重开音乐。
 */
public final class BossBgmSelector {

    /** 新候选必须比当前提供者近这么多倍才值得换（1 - 25%）。 */
    public static final double SWITCH_IMPROVEMENT = 0.75D;

    /** 一只候选 BOSS 的不可变快照。 */
    public record Candidate(UUID id, int tier, double distanceSqr, boolean targetingMe,
                            ResourceLocation eventId) {
    }

    /** 当前选曲结果。 */
    public record Selection(UUID provider, double distanceSqr, ResourceLocation eventId) {
    }

    private static final Comparator<Candidate> ORDER =
            Comparator.comparing(Candidate::targetingMe).reversed()
                    .thenComparingDouble(Candidate::distanceSqr)
                    .thenComparing(candidate -> candidate.id().toString());

    private BossBgmSelector() {
    }

    /**
     * @param candidates 已经按退出半径过滤过的候选（空 = 场上没有 BOSS）
     * @param current    当前正在放的那条（{@code null} = 没在放）
     */
    public static Optional<Selection> select(List<Candidate> candidates, @Nullable Selection current) {
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }
        Candidate best = candidates.stream().min(ORDER).orElseThrow();

        if (current == null) {
            return Optional.of(selection(best));
        }

        // 同一个事件 id：不用换（哪怕提供者换了人或走了几步）。
        if (best.eventId().equals(current.eventId())) {
            Candidate sameProvider = byId(candidates, current.provider());
            return Optional.of(sameProvider == null ? selection(best) : selection(sameProvider));
        }

        // 当前提供者已不在场上：立刻换到最优候选。
        Candidate currentCandidate = byId(candidates, current.provider());
        if (currentCandidate == null) {
            return Optional.of(selection(best));
        }

        // 显著更近才换；否则维持当前曲目（sticky）。
        // 阈值按<b>距离</b>比较，而这里存的是平方距离，故乘两次。
        double threshold = currentCandidate.distanceSqr() * SWITCH_IMPROVEMENT * SWITCH_IMPROVEMENT;
        if (best.distanceSqr() < threshold) {
            return Optional.of(selection(best));
        }
        return Optional.of(selection(currentCandidate));
    }

    @Nullable
    private static Candidate byId(List<Candidate> candidates, UUID id) {
        if (id == null) {
            return null;
        }
        for (Candidate candidate : candidates) {
            if (candidate.id().equals(id)) {
                return candidate;
            }
        }
        return null;
    }

    private static Selection selection(Candidate candidate) {
        return new Selection(candidate.id(), candidate.distanceSqr(), candidate.eventId());
    }
}
