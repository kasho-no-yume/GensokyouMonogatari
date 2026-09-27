package com.bitsson.gensokyou.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * BOSS 的游走选点（{@code add-remnant-touhou-bosses} 的移动策略实现）。
 *
 * <p><b>为什么不能直接「朝远离玩家的方向推」</b>：那样只要玩家把 BOSS 逼到墙边、
 * 墙角、树丛或任何狭窄处，远离向量就永远指着墙，BOSS 会顶着墙抖动——观测到的
 * 「一直贴墙」正是这个。所以距离带在这里<b>不是位移方向，而是候选点的评分项</b>：
 * 先按「这个点能不能站得住、能不能走得到」筛，再按「离玩家多远」排。
 *
 * <p><b>可达性优先于距离</b>：候选点必须满足
 * <ol>
 *   <li>落点及头顶一格是空气
 *   <li>起点方向的前几步是通的（否则一出发就卡）
 *   <li>整条路径的空气比例达标
 * </ol>
 * 三个条件都过不了就<b>向上脱困</b>，绝不原地顶墙。
 *
 * <p>无祭坛特判：任何造成卡死的地形（墙、树、屋、洞穴、窄道）走同一条逻辑。
 */
public final class BossSteering {

    /** 选点参数。可由单只 BOSS 覆写。 */
    public record Policy(
            double minDist,
            double maxDist,
            double hoverFloor,
            double hoverCeiling,
            double avoidPlayer,
            double avoidAnchor,
            /** 每次重选的候选数；越多越不容易挑不出好点，但越费。 */
            int candidates,
            /** 路径空气比例的最低要求，低于此值直接淘汰。 */
            double minPathClearance,
            /** 候选点生成的重选周期（tick）。 */
            int repickTicks) {

        public static Policy of(double minDist, double maxDist, double hoverFloor, double hoverCeiling,
                                double avoidPlayer, double avoidAnchor) {
            return new Policy(minDist, maxDist, hoverFloor, hoverCeiling,
                    avoidPlayer, avoidAnchor, 14, 0.55D, 20);
        }
    }

    /** 选点结果：目标点 + 该路径的空气比例（供上层做日志与调试）。 */
    public record Choice(Vec3 point, double clearance) {
    }

    private static final int PATH_SAMPLES = 24;
    /** 起点方向必须通出的步数——短于这个距离撞上东西就是「一出发就卡」。 */
    private static final int MUST_CLEAR_LEAD = 3;
    private static final double SAMPLE_STEP = 0.5D;

    private BossSteering() {
    }

    /**
     * 选下一个游走目标点。
     *
     * <p>候选以<b>玩家</b>为圆心、按距离带取点，但评分顺序是「先能不能去、再好不好去」：
     * 不满足可达性的候选直接淘汰，不参与距离评分。
     */
    public static Choice pick(Level level, Vec3 self, Vec3 velocity, Vec3 primaryPos,
                              List<Player> others, Vec3 anchor, Policy policy) {
        Vec3 best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        double bestClearance = 0.0D;
        int fallbackY = Integer.MIN_VALUE;

        for (int attempt = 0; attempt < policy.candidates(); attempt++) {
            double yaw = (level.getRandom().nextDouble() - 0.5D) * Math.PI * 2.0D;
            double pitch = (level.getRandom().nextDouble() - 0.5D) * 1.2D;
            double dist = policy.minDist()
                    + level.getRandom().nextDouble() * (policy.maxDist() - policy.minDist());
            double horiz = Math.cos(pitch) * dist;

            double baseY = anchor != null ? anchor.y : self.y;
            double y = baseY + policy.hoverFloor()
                    + level.getRandom().nextDouble() * (policy.hoverCeiling() - policy.hoverFloor());

            Vec3 candidate = new Vec3(
                    primaryPos.x + Math.cos(yaw) * horiz,
                    y,
                    primaryPos.z + Math.sin(yaw) * horiz);

            if (!isStandable(level, candidate)) {
                // 顺手记一下「往上多少格能站住」，作为无解时的脱困方向
                int up = upwardClearance(level, candidate);
                if (up > fallbackY) {
                    fallbackY = up;
                }
                continue;
            }
            double clearance = pathClearance(level, self, candidate);
            if (clearance < policy.minPathClearance()) {
                continue;
            }

            double score = clearance * 100.0D;
            // 距离带内的候选按「越靠中带越好」给分，不做硬性方向约束
            double mid = (policy.minDist() + policy.maxDist()) * 0.5D;
            score -= Math.abs(dist - mid);
            for (Player other : others) {
                double d = candidate.distanceToSqr(other.position());
                if (d < policy.avoidPlayer() * policy.avoidPlayer()) {
                    score -= 50.0D;
                }
            }
            if (anchor != null && candidate.distanceToSqr(anchor)
                    < policy.avoidAnchor() * policy.avoidAnchor()) {
                score -= 30.0D;
            }
            // 朝向保持：轻微偏好继续朝当前前进方向，避免原地来回摆
            Vec3 delta = candidate.subtract(self);
            if (delta.lengthSqr() > 1.0E-6D) {
                Vec3 want = delta.normalize();
                Vec3 have = velocity.lengthSqr() > 1.0E-4D ? velocity.normalize() : want;
                score += want.dot(have) * 6.0D;
            }

            if (score > bestScore) {
                bestScore = score;
                best = candidate;
                bestClearance = clearance;
            }
        }
        if (best != null) {
            return new Choice(best, bestClearance);
        }
        return new Choice(upwardEscape(level, self, fallbackY), 1.0D);
    }

    /** 该点能否站住：脚下那一格与头顶那一格都得是空气。 */
    public static boolean isStandable(Level level, Vec3 at) {
        BlockPos body = BlockPos.containing(at);
        if (!level.isLoaded(body) || !level.getBlockState(body).isAir()) {
            return false;
        }
        BlockPos head = body.above();
        return level.isLoaded(head) && level.getBlockState(head).isAir();
    }

    /** 从某点往上数，连续几格是空气。 */
    public static int upwardClearance(Level level, Vec3 at) {
        for (int step = 0; step <= 24; step++) {
            if (!isStandable(level, at.add(0.0D, step, 0.0D))) {
                return step;
            }
        }
        return 24;
    }

    /**
     * 路径空气比例。
     *
     * <p>前 {@link #MUST_CLEAR_LEAD} 格必须全通——否则这个点再「好」也没用，
     * 走过去的第一步就撞墙。之后的比例只影响评分，不做硬淘汰。
     */
    public static double pathClearance(Level level, Vec3 from, Vec3 to) {
        Vec3 delta = to.subtract(from);
        double length = delta.length();
        if (length < 1.0E-3D) {
            return 1.0D;
        }
        int samples = Math.max(MUST_CLEAR_LEAD + 1, (int) Math.ceil(length / SAMPLE_STEP));
        int clear = 0;
        for (int i = 0; i <= samples; i++) {
            double t = (double) i / samples;
            Vec3 p = from.add(delta.scale(t));
            boolean open = level.isLoaded(BlockPos.containing(p))
                    && level.getBlockState(BlockPos.containing(p)).isAir();
            if (open) {
                clear++;
            } else if (i < MUST_CLEAR_LEAD) {
                return 0.0D;
            }
        }
        return (double) clear / (samples + 1);
    }

    /**
     * 无解时的脱困方向：向上找开阔处。
     *
     * <p>这是<b>通用兜底</b>而非祭坛特判——任何把 BOSS 围死的地方都靠它脱身。
     * 找不到就原地抬起 1 格，比顶着墙原地抖动好。
     */
    public static Vec3 upwardEscape(Level level, Vec3 self, int hint) {
        for (int step = 1; step <= 12; step++) {
            Vec3 candidate = self.add(0.0D, step, 0.0D);
            if (isStandable(level, candidate)) {
                return candidate;
            }
        }
        double nudge = Mth.clamp(1.0D + hint * 0.5D, 1.0D, 8.0D);
        return self.add(0.0D, nudge, 0.0D);
    }
}
