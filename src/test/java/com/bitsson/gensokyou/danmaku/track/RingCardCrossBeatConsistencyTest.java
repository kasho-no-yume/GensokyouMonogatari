package com.bitsson.gensokyou.danmaku.track;

import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 环卡的<b>跨拍一致性</b>：48 颗弹 MUST 落在同一个圆上。
 *
 * <p><b>为什么既有测试没抓到</b>：{@code BigFairyCardReworkTest#discRingSitsBehindTheEmitterAndSweepsWithPhase}
 * 把 {@code EYE} 与 {@code FORWARD} 固定成常量，只调 {@code Geometry.build} 两次 ——
 * 它测的是<b>几何函数</b>，而 bug 恰好在「48 拍串起来」的地方。本类补的就是这一层。
 *
 * <p><b>本类如何复现发射器行为而不启动世界</b>：严格照 {@code TrackRunner#emitTrack}
 * 的采样方式建模。它在每次调用开头采样一次 {@code boss.getEyePosition()}、
 * {@code forward = toAnchor.normalize()} 与 {@code gapPhase}，然后遍历该 tick 到期的 beat。
 * 环卡声明 {@code repeatEvery(0)}（{@code isDue} 退化为 {@code beat.tick() == tick}），
 * 于是 48 个 beat 分属 48 个不同 tick —— <b>发射器被调用 48 次，每次重新采样</b>。
 *
 * <h2>BOSS 在环卡期间是锁位的</h2>
 *
 * <p>{@code BigFairyEntity#movementLocked} 在环卡前 {@code RING_CARD_RING_TICKS = 120} 拍内
 * 把 {@code setDeltaMovement(Vec3.ZERO)} 并把期望位置钉在原地，所以发射窗口内
 * <b>BOSS 位置是常量</b>。
 * 因此实机上的漂移源<b>只有</b> {@code forward}：锚点是玩家的当前位置，玩家一直在走，
 * 于是瞄准方向逐拍旋转，而圆心在它前方 6 格 —— 方向误差被放大 6 倍。
 *
 * <p>这与本变更 design 初稿的描述不同（初稿写「BOSS 持续游走，2.4 秒内位移可观」）。
 * 那句话对 2019 年的旧代码成立（当时 {@code movementLocked} 因比名字而恒不成立，
 * 见 {@code BigFairyEntity:199-202} 的注释），修复后不再成立。
 * 结论不变（环不成环），但<b>归因</b>变了 —— 而归因决定测试该建模什么。
 */
class RingCardCrossBeatConsistencyTest {

    private static final Vec3 EYE = new Vec3(0.0D, 64.0D, 0.0D);
    private static final Vec3 PLAYER_START = new Vec3(0.0D, 64.0D, 15.0D);
    private static final Vec3 WORLD_UP = new Vec3(0.0D, 1.0D, 0.0D);

    /** 实机情形：BOSS 被 movementLocked 钉住，只有玩家在走。 */
    private static final Vec3 LOCKED_BOSS_VELOCITY = Vec3.ZERO;
    /** 玩家闪避时的横移，0.15 格/tick ⇒ 47 拍横移约 7 格，在 15 格外看是 ~25°。 */
    private static final Vec3 PLAYER_VELOCITY = new Vec3(0.15D, 0.0D, 0.0D);
    /** 守护场景：若 {@code movementLocked} 再次失效（比名字、判错下标…），BOSS 会游走。 */
    private static final Vec3 WANDERING_BOSS_VELOCITY = new Vec3(0.25D, 0.0D, 0.05D);

    private static final double RING_RADIUS = 4.0D;
    private static final double OFFSET_FORWARD = -6.0D;
    private static final int BEATS = 48;
    private static final double EPS = 1.0E-9D;

    /** 一次「发射器调用」的产物：这一拍生成的弹，以及这一拍自己的圆心。 */
    private record Emission(List<Geometry.Shot> shots, Vec3 discCentre) {
    }

    /** 阶段 1 的环卡轨道。 */
    private static Track ringTrack() {
        return BossCards.bigFairy().stream()
                .map(SpellCard::tracks)
                .filter(tracks -> !tracks.isEmpty())
                .findFirst()
                .orElseThrow(() -> new AssertionError("bigFairy 没有符卡"))
                .get(0);
    }

    /**
     * 按 {@code TrackRunner#emitTrack} 的方式逐拍推进，取前 {@code ticks} 拍的发射。
     *
     * <p>刻意<b>不</b>复用发射器代码本身：那需要实体与 {@code Level}。这里复制的是它的采样规则，
     * 而规则本身只有几行 —— 若哪天它变了，本类的注释就会与代码不符，那正是需要人看一眼的时候。
     */
    private static List<Emission> simulate(Track track, int ticks,
                                            Vec3 bossVelocity, Vec3 playerVelocity) {
        List<Emission> out = new ArrayList<>();
        RandomSource rng = RandomSource.create(20261002L);
        for (int tick = 0; tick < ticks; tick++) {
            // ① 发射原点逐 tick 重采样
            Vec3 eye = EYE.add(bossVelocity.scale(tick));
            // 锚点是「该玩家当前位置」
            Vec3 anchor = PLAYER_START.add(playerVelocity.scale(tick));
            // ② forward 逐 tick 重采样
            Vec3 forward = anchor.subtract(eye).normalize();
            double phase = track.phaseAt(tick);

            for (Track.Beat beat : track.beats()) {
                if (beat.tick() != tick) {
                    continue;
                }
                List<Geometry.Shot> shots = Geometry.build(beat, eye, forward, anchor, WORLD_UP,
                        0.0D, phase, rng);
                // 这一拍自己的圆心（DISC_RING 的 offsetUp 未设，故只有 forward 一项）
                out.add(new Emission(shots, eye.add(forward.scale(OFFSET_FORWARD))));
            }
        }
        return out;
    }

    /** 实机情形下的发射序列。 */
    private static List<Emission> simulateLockedBoss() {
        return simulate(ringTrack(), BEATS, LOCKED_BOSS_VELOCITY, PLAYER_VELOCITY);
    }

    private static List<Geometry.Shot> flatten(List<Emission> emissions) {
        List<Geometry.Shot> all = new ArrayList<>();
        for (Emission emission : emissions) {
            all.addAll(emission.shots());
        }
        return all;
    }

    /** 任意两个圆心之间的最大距离。 */
    private static double centreSpread(List<Emission> emissions) {
        Vec3 reference = EYE.add(PLAYER_START.subtract(EYE).normalize().scale(OFFSET_FORWARD));
        double worst = 0.0D;
        for (Emission emission : emissions) {
            worst = Math.max(worst, emission.discCentre().distanceTo(reference));
        }
        return worst;
    }

    @Test
    @DisplayName("48 个圆心必须重合（实机情形：BOSS 锁位，只有玩家在动）")
    void ringSharesOneCentreWhenBossIsLocked() {
        List<Emission> emissions = simulateLockedBoss();
        assertEquals(BEATS, flatten(emissions).size(),
                "前 " + BEATS + " 拍应产出 " + BEATS + " 颗（当前形态：每拍 1 颗）");

        double spread = centreSpread(emissions);
        double aimSwing = Math.toDegrees(Math.asin(Math.min(1.0D,
                PLAYER_VELOCITY.scale(BEATS - 1).length()
                        / PLAYER_START.subtract(EYE).length())));

        assertEquals(0.0D, spread, EPS,
                "环的 48 个圆心必须重合，实测最大偏离 " + String.format("%.4f", spread)
                        + " 格。BOSS 被 movementLocked 钉住，唯一漂移源是 forward 随玩家旋转 "
                        + String.format("%.2f", aimSwing) + "°，而圆心在它前方 6 格 ⇒ "
                        + "方向误差被放大 6 倍。成因：发射原点与瞄准方向逐 tick 重采样，"
                        + "而 48 个 beat 分属 48 个 tick。");
    }

    @Test
    @DisplayName("48 个圆心必须重合（守护：BOSS 若在游走，漂移会更大）")
    void ringSharesOneCentreEvenIfBossMoves() {
        List<Emission> emissions = simulate(ringTrack(), BEATS,
                WANDERING_BOSS_VELOCITY, PLAYER_VELOCITY);
        double spread = centreSpread(emissions);
        double bossTravel = WANDERING_BOSS_VELOCITY.scale(BEATS - 1).length();

        assertEquals(0.0D, spread, EPS,
                "即使 BOSS 在游走，48 个圆心也必须重合，实测偏离 "
                        + String.format("%.4f", spread) + " 格（BOSS 位移 "
                        + String.format("%.4f", bossTravel) + " 格）。"
                        + "这条守护的是 movementLocked 再次失效的情形 —— 届时环会更明显地散开。");
    }

    @Test
    @DisplayName("48 颗弹两两相邻距离符合 2R·sin(Δ角/2)")
    void adjacentShotsAreEvenlySpacedOnOneCircle() {
        List<Geometry.Shot> all = flatten(simulateLockedBoss());

        // 只断言「都在半径 4 上」是不够的 —— 螺线上每颗到「自己那一拍的圆心」也正好是 4。
        // 必须查两两距离：它对圆心漂移敏感。
        double expectedStep = 2.0D * RING_RADIUS * Math.sin(Math.toRadians(360.0D / BEATS) / 2.0D);
        for (int i = 1; i < all.size(); i++) {
            double actual = all.get(i - 1).origin().distanceTo(all.get(i).origin());
            assertEquals(expectedStep, actual, 1.0E-6D,
                    "第 " + (i - 1) + " 与第 " + i + " 颗的间距应为 2R·sin(7.5°/2) = "
                            + String.format("%.6f", expectedStep)
                            + "，实际 " + String.format("%.6f", actual) + "。偏差即圆心漂移");
        }

        // 半径一致性：真圆时 48 颗的质心即圆心
        Vec3 centroid = new Vec3(0.0D, 0.0D, 0.0D);
        for (Geometry.Shot shot : all) {
            centroid = centroid.add(shot.origin());
        }
        centroid = centroid.scale(1.0D / all.size());
        for (int i = 0; i < all.size(); i++) {
            double radius = all.get(i).origin().distanceTo(centroid);
            assertEquals(RING_RADIUS, radius, 1.0E-6D,
                    "第 " + i + " 颗到质心的距离应为 " + RING_RADIUS
                            + "，实际 " + String.format("%.6f", radius));
        }
    }

    @Test
    @DisplayName("环是闭合的：首尾两颗的间距等于相邻间距（螺线的首尾间距会偏大）")
    void ringIsClosedNotSpiral() {
        List<Geometry.Shot> all = flatten(simulateLockedBoss());
        assertEquals(BEATS, all.size());

        double adjacent = all.get(0).origin().distanceTo(all.get(1).origin());
        double closing = all.get(BEATS - 1).origin().distanceTo(all.get(0).origin());
        assertEquals(adjacent, closing, 1.0E-6D,
                "闭合圆上首尾两颗的间距应与相邻间距相同（都是 2R·sin(7.5°/2)），"
                        + "实际首尾 " + String.format("%.6f", closing)
                        + " vs 相邻 " + String.format("%.6f", adjacent));
    }

    @Test
    @DisplayName("取证：圆心漂移中「瞄准旋转」与「BOSS 位移」各自的贡献")
    void attributesDriftToAimRotationAndTranslation() {
        // 这不是门禁，是**取证**：圆心 = eye + forward × offsetForward，两项都逐 tick 变。
        // 只报总数无法判断修复是否彻底 —— 若只压住一项，另一项仍会贡献可观漂移。
        Vec3 forward0 = PLAYER_START.subtract(EYE).normalize();
        Vec3 reference = EYE.add(forward0.scale(OFFSET_FORWARD));

        for (Vec3 bossVelocity : List.of(LOCKED_BOSS_VELOCITY, WANDERING_BOSS_VELOCITY)) {
            double total = 0.0D;
            double translationOnly = 0.0D;
            for (int tick = 0; tick < BEATS; tick++) {
                Vec3 eye = EYE.add(bossVelocity.scale(tick));
                Vec3 forward = PLAYER_START.add(PLAYER_VELOCITY.scale(tick))
                        .subtract(eye).normalize();
                total = Math.max(total, eye.add(forward.scale(OFFSET_FORWARD))
                        .distanceTo(reference));
                translationOnly = Math.max(translationOnly,
                        eye.add(forward0.scale(OFFSET_FORWARD)).distanceTo(reference));
            }
            System.out.printf(
                    "BOSS 速度 %s：圆心漂移总计 %.4f 格 = 位移贡献 %.4f + 瞄准旋转贡献 %.4f%n",
                    bossVelocity, total, translationOnly, total - translationOnly);
            assertTrue(total > 0.0D, "漂移 MUST 为正（否则复现不出螺线）");
            assertTrue(total - translationOnly > 0.0D,
                    "瞄准旋转对漂移的贡献 MUST 为正");
        }
    }
}