package com.bitsson.gensokyou.danmaku.track;

import java.util.ArrayList;
import java.util.List;

/**
 * 轨道表静态校验器——把「三维可读性契约」R1/R2/R3 与缺段约束变成可断言的规则。
 *
 * <p>契约（{@code danmaku-track-composition}）：
 * <ul>
 *   <li><b>R1 前向威胁</b>——生成点须在玩家视野锥内或有预警。落成静态规则：任何绕玩家
 *       铺满 360° 的形状必须留缺口；随机必须被锥包络约束。
 *   <li><b>R2 解法全向</b>——横向堵死时上下/前后有解。落成：自轴型形状 MUST 自带缺口或间隙。
 *   <li><b>R3 层限</b>——单拍生成量不得超过密度预算。
 * </ul>
 *
 * <p>另有两条本 mod 特有的硬约束：
 * <ul>
 *   <li><b>轨道视觉独占</b>——同符卡内任两轨在（色相/速度/尺寸/形状）四项中至少一项不同。
 *   <li><b>缺段约束</b>——缺「破」的符卡 MUST NOT 含 {@link TargetMode#AIMED}；
 *       缺「結」的符卡全部轨道 MUST 无终止条件。
 * </ul>
 *
 * <p>纯静态、可离线运行，故 lint 与单测都无需世界。
 */
public final class TrackLint {

    /** R3 单拍密度预算（发/拍）。 */
    public static final int DENSITY_BUDGET = 24;
    /** R1 允许的锥内随机最大张角（度）。超过即读作全向无约束随机。 */
    public static final double CONE_RANDOM_MAX_SPREAD = 180.0D;
    /** R2 封死阈值：单拍达到这个数量且自认无缺口，才算「封死了解法」。 */
    public static final int R2_SEAL_COUNT = 8;
    /** 轨轨道数下限。 */
    public static final int MIN_TRACKS = 1;
    /** 轨道数上限。 */
    public static final int MAX_TRACKS = 3;

    private TrackLint() {
    }

    /** 校验一整副符卡表。返回空列表即通过。 */
    public static List<String> lint(String owner, List<SpellCard> cards, SignaturePalette palette) {
        List<String> violations = new ArrayList<>();
        if (cards.isEmpty()) {
            violations.add(owner + ": 符卡表为空");
            return violations;
        }
        int maxTracks = 0;
        for (SpellCard card : cards) {
            maxTracks = Math.max(maxTracks, card.tracks().size());
            violations.addAll(lintCard(owner, card));
        }
        if (!palette.fits(maxTracks)) {
            violations.add(String.format("%s: 色盘容量 %d < 最大并发轨道数 %d",
                    owner, palette.size(), maxTracks));
        }
        double previous = Double.MAX_VALUE;
        for (SpellCard card : cards) {
            if (card.hpFraction() > previous) {
                violations.add(String.format("%s: 符卡「%s」起始占比 %.3f 未随血量递减",
                        owner, card.name(), card.hpFraction()));
            }
            previous = card.hpFraction();
        }
        return violations;
    }

    /** 校验单张符卡。 */
    public static List<String> lintCard(String owner, SpellCard card) {
        List<String> violations = new ArrayList<>();
        String tag = owner + "/「" + card.name() + "」";
        int tracks = card.tracks().size();
        if (tracks < MIN_TRACKS || tracks > MAX_TRACKS) {
            violations.add(String.format("%s: 轨道数 %d 越界 [%d, %d]",
                    tag, tracks, MIN_TRACKS, MAX_TRACKS));
        }
        for (int i = 0; i < tracks; i++) {
            for (int j = i + 1; j < tracks; j++) {
                if (card.tracks().get(i).identity().equals(card.tracks().get(j).identity())) {
                    violations.add(String.format("%s: 轨「%s」与「%s」视觉标识完全相同",
                            tag, card.tracks().get(i).name(), card.tracks().get(j).name()));
                }
            }
        }
        for (Track track : card.tracks()) {
            if (track.beats().isEmpty()) {
                violations.add(String.format("%s: 轨「%s」没有任何节拍", tag, track.name()));
            }
            for (Track.Beat beat : track.beats()) {
                violations.addAll(lintBeat(tag, track, beat));
            }
        }
        return violations;
    }

    private static List<String> lintBeat(String tag, Track track, Track.Beat beat) {
        List<String> violations = new ArrayList<>();
        Shape shape = beat.shape();
        Shape.Params params = beat.params();
        String where = String.format("%s 轨「%s」t=%d %s", tag, track.name(), beat.tick(), shape);

        // R1：绕玩家铺满 360° 的形状必须留缺口，否则等于有一半弹在背后。
        boolean fullCircle = shape == Shape.RING_FACING || shape == Shape.RING_HORIZONTAL
                || shape == Shape.AXIAL_STAR;
        if (fullCircle && shape != Shape.AXIAL_STAR && params.gapDeg() <= 0.0D) {
            violations.add(where + ": 违反 R1 前向威胁——环形未留缺口（gapDeg=0）");
        }
        if (shape == Shape.CAGE && params.gapDeg() <= 0.0D && params.count() > 12) {
            violations.add(where + ": 违反 R1——高密度笼未留缺口");
        }

        // R1：随机必须被包络约束。
        if (shape.isRandom()
                && (params.spreadDeg() <= 0.0D || params.spreadDeg() > CONE_RANDOM_MAX_SPREAD)) {
            violations.add(where + ": 违反 R1——随机锥张角必须在 (0, "
                    + CONE_RANDOM_MAX_SPREAD + "] 度内");
        }

        // R2：自轴型 MUST 留有可穿过的解。
        //
        // 判据刻意是<b>密度</b>而非形状身份：5 发稀疏扇（教学档的大妖精散華）堵不死任何
        // 方向，垂直与前后都还有解；只有单拍数量足够多、又自认不留缺口时才算封死。
        // 阈值 R2_SEAL_COUNT 低于它即视为「稀疏，不构成封死」。
        if (beat.targetMode() != TargetMode.AIMED
                && shape != Shape.AIMED_SINGLE
                && !shape.guaranteesGap(params)
                && params.count() >= R2_SEAL_COUNT) {
            violations.add(where + ": 违反 R2 解法全向——自轴型单拍 " + params.count()
                    + " 发且未留任何可穿过的间隙");
        }

        // R3：单拍密度预算。
        if (params.count() > DENSITY_BUDGET) {
            violations.add(where + ": 违反 R3 层限——单拍 " + params.count()
                    + " 发 > 预算 " + DENSITY_BUDGET);
        }

        if (shape != Shape.MINE_RING && params.speed() <= 0.0D) {
            violations.add(where + ": 弹速必须为正（溜め除外）");
        }
        if (shape != Shape.MINE_RING && params.hoverTicks() < 0) {
            violations.add(where + ": 悬停 tick 不得为负");
        }
        if (shape == Shape.MINE_RING && params.mineRadius() <= 0.0D) {
            violations.add(where + ": 溜め必须给出正的触发半径");
        }
        if (shape == Shape.GAP_SPLIT && params.splitCount() < 2) {
            violations.add(where + ": 补位分裂的发数必须 ≥ 2");
        }
        if (shape == Shape.CURVE_RING && Math.abs(params.curveRateDegPerSec()) < 1.0E-3D) {
            violations.add(where + ": 曲射必须给出非零角速度");
        }
        return violations;
    }

    // ------------------------------------------------------------------
    // 缺段约束（残影 BOSS 的机制载体）
    // ------------------------------------------------------------------

    /** 缺「破」：整副符卡表 MUST NOT 含任何瞄准型节拍。 */
    public static boolean hasNoAimedTrack(String owner, List<SpellCard> cards) {
        return cards.stream().flatMap(c -> c.tracks().stream())
                .flatMap(t -> t.beats().stream())
                .noneMatch(b -> b.targetMode() == TargetMode.AIMED);
    }

    /** 缺「結」：全部符卡的全部轨道 MUST 无终止条件。 */
    public static boolean allTracksEndless(List<SpellCard> cards) {
        return cards.stream().flatMap(c -> c.tracks().stream()).noneMatch(Track::terminates);
    }
}
