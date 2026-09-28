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
 *   <li><b>R3 层限</b>——稳态并发密度（每名玩家 6 格内同时在场的弹数）不得超过预算。
 *       刻意<b>不</b>用每拍发数度量：持续型图案每拍少发而稳态多，瞬发型图案每拍多发
 *       而稳态少，两种量各错一个方向。
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

    /**
     * R3 密度预算（颗/玩家·稳态并发）。
     *
     * <p><b>度量的不是每拍发数，而是稳态并发数。</b>两者是不同的量：持续型图案
     * （弹幕墙）每拍发数少而稳态并发高，只按每拍发数判定会低估一个数量级；
     * 瞬发型图案每拍发数高而弹迅速离场，只按每拍发数判定又会高估。
     *
     * <p>估值模型（{@link #steadyStateEstimate}）：每拍发出的弹在「玩家 6 格包络」内的
     * 停留时长约为包络直径 ÷ 弹速，乘以每拍发数即为该拍的稳态并发贡献。
     *
     * <p>取值 120：够容纳高密度弹幕墙这类持续型图案而不误杀，同时能拦住
     * 「一拍糊几百发且弹速低」的真糊屏。
     */
    public static final double STEADY_STATE_BUDGET = 120.0D;

    /**
     * 密度判定用的玩家包络直径（格）。spec 以「6 格内」表述，故取直径 12。
     */
    private static final double PLAYER_ENVELOPE_DIAMETER = 12.0D;

    /** R1 允许的锥内随机最大张角（度）。超过即读作全向无约束随机。 */
    public static final double CONE_RANDOM_MAX_SPREAD = 180.0D;
    /** R2 封死阈值：单拍达到这个数量且自认无缺口，才算「封死了解法」。 */
    public static final int R2_SEAL_COUNT = 8;
    /** 轨轨道数下限。 */
    public static final int MIN_TRACKS = 1;
    /** 轨道数上限。 */
    public static final int MAX_TRACKS = 3;

    /** R4：单个可见段的最短时长（tick）。低于此玩家来不及反应，隐藏会读作「突然无敌」。 */
    public static final double MIN_VISIBLE_TICKS = 20.0D;

    /** R4：单个隐藏段的最长时长（tick）。高于此弹幕墙读作「长时间无敌」而非「读节奏」。 */
    public static final double MAX_BLIND_TICKS = 60.0D;

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
            violations.addAll(lintRig(tag, track));
            for (Track.Beat beat : track.beats()) {
                violations.addAll(lintBeat(tag, track, beat));
            }
        }
        // R3：稳态并发密度（逐轨道判定；一个符卡至多 3 条并发轨道，故按轨累加仍是个位数倍）。
        double steady = 0.0D;
        for (Track track : card.tracks()) {
            steady += steadyStateEstimate(track);
        }
        if (steady > STEADY_STATE_BUDGET) {
            violations.add(String.format("%s: 违反 R3 层限——稳态并发密度约 %.0f 颗/玩家 > 预算 %.0f"
                    + "（判据为稳态并发数，不是每拍发数）", tag, steady, STEADY_STATE_BUDGET));
        }
        return violations;
    }

    /**
     * 编队装置的静态判据（需求②③④⑤）。
     *
     * <p>装置会把弹位「写死」成 {@code (装置 tick, 自身相位)} 的函数，因此与三类
     * 既有行为语义互斥，MUST 在静态期就拒掉，而不是等到实机里看到「弹不动」或
     * 「弹突然消失」：
     * <ul>
     *   <li>{@code MINE}——溜め弹的语义是「原地埋着等人踩」，而位置由装置决定；</li>
     *   <li>{@code CURVE} / {@code SPEED_PROFILE}——两者都在改速度，而装置已经
     *       用「解析终点 − 当前坐标」把速度占满了；叠上去等于两个权威同时写位置，
     *       表现为弹一卡一卡地抽搐。</li>
     * </ul>
     */
    private static List<String> lintRig(String tag, Track track) {
        List<String> violations = new ArrayList<>();
        Behaviour.Rig rig = track.rig();
        if (!rig.active()) {
            return violations;
        }
        if (rig.lifetimeTicks() <= 0) {
            violations.add(String.format("%s: 轨「%s」的装置寿命为 %d tick，弹将永远挂在它身上",
                    tag, track.name(), rig.lifetimeTicks()));
        }
        for (Track.Beat beat : track.beats()) {
            if (rig.conflictsWith(beat.behaviour().motion())) {
                violations.add(String.format("%s: 轨「%s」t=%d 同时用了编队与运动「%s」"
                                + "——装置已接管弹位，该运动会与之争夺位置权威",
                        tag, track.name(), beat.tick(),
                        beat.behaviour().motion().kind()));
            }
        }
        return violations;
    }

    /**
     * 一条轨道的<b>稳态并发密度</b>估值（颗/玩家）。
     *
     * <p>模型：每拍发出的 {@code count} 颗弹，各自在玩家 6 格包络内停留约
     * {@code 包络直径 ÷ 弹速} 个 tick；按重复周期摊薄即得稳态并发数。
     *
     * <pre>
     *   contribution = count × (包络直径 / speed) / max(1, repeatEvery)
     * </pre>
     *
     * <p>零速弹（溜め）没有「飞过包络」的过程，只按每拍发数计入其常驻量。
     *
     * <p>本估值是<b>保守的静态近似</b>：它只取弹速与包络直径，不涉及地形遮挡、
     * 弹与弹互斥、玩家走位等运行时因素。MUST NOT 被当作精确值使用，只用于
     * 「一数量级的错判」——即每拍糊几百发那种。
     */
    public static double steadyStateEstimate(Track track) {
        int period = Math.max(1, track.repeatEvery());
        double perTick = 0.0D;
        for (Track.Beat beat : track.beats()) {
            Shape.Params params = beat.params();
            double speed = params.speed();
            double residence;
            if (speed <= 1.0E-4D) {
                // 静止弹不飞越包络；只按每拍发数计其常驻量（溜め环带即此类）。
                residence = 1.0D;
            } else {
                residence = PLAYER_ENVELOPE_DIAMETER / speed;
            }
            perTick += params.count() * emissionMultiplier(beat.shape()) * residence / period;
        }
        return perTick;
    }

    /**
     * 某形状单拍的<b>实际</b>发射倍率。
     *
     * <p>{@link Shape#CAGE} 在三个正交竖直面各转一圈，故实际发数是
     * {@code count} 的三倍；其余形状为一比一。漏掉这个倍率会让笼类图案的
     * 密度被低估三倍。
     */
    private static int emissionMultiplier(Shape shape) {
        return shape == Shape.CAGE ? 3 : 1;
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

        if (shape != Shape.SCATTER_STATIC && params.speed() <= 0.0D) {
            violations.add(where + ": 弹速必须为正（静止散布类除外）");
        }
        violations.addAll(lintBehaviour(where, shape, beat.behaviour()));
        return violations;
    }

    // ------------------------------------------------------------------
    // 行为判据
    //
    // 刻意与几何判据分开：行为是「时间上的事」，判据也 MUST 是时间上的。
    // 改前行为焊在几何里，故 lint 只能看形状——「一个每拍 count=8 的平面」它判不出
    // 任何东西，而这个平面若带相位隐藏，恰恰是最需要被判定的图案。
    // ------------------------------------------------------------------

    /** 行为本身的自洽性（与可读性无关的硬错误）。 */
    private static List<String> lintBehaviour(String where, Shape shape, Behaviour behaviour) {
        List<String> violations = new ArrayList<>();
        Behaviour.Motion motion = behaviour.motion();
        if (!motion.effective()) {
            violations.add(where + ": 运动 " + motion.kind() + " 的参数无效"
                    + "（曲射角速度须非零 / 悬停 tick 须为正 / 溜め半径须为正）");
        }
        if (motion.kind() == Behaviour.Motion.Kind.MINE && !shape.isStaticSpawn()) {
            // 溜め要求弹真的静止；配了给出方向的几何，弹会一边飞一边「埋」，
            // 玩家既追不上也躲不掉——语义自相矛盾。
            violations.add(where + ": 溜め应配静止散布几何（当前 "
                    + shape + " 会给出方向，弹将一边飞一边待发）");
        }
        if (motion.kind() == Behaviour.Motion.Kind.GROUND_HUG && paramsZeroSpeedHere(shape)) {
            violations.add(where + ": 贴地运动配零弹速没有意义（本就静止）");
        }
        Behaviour.Split split = behaviour.split();
        if (split.active() && split.count() < 2) {
            violations.add(where + ": 分裂发数须 ≥ 2，当前 " + split.count());
        }
        violations.addAll(lintVisibility(where, behaviour.visibility()));
        return violations;
    }

    /** 该形状是否应当以零弹速使用。 */
    private static boolean paramsZeroSpeedHere(Shape shape) {
        return shape == Shape.SCATTER_STATIC;
    }

    /**
     * R4 节奏：<b>时间维度</b>的可读性判据。
     *
     * <p>存在的理由：R1/R2/R3 全是<b>几何</b>判据，而一类图案的可读性来自时间——
     * 「缓慢飞行的高密度弹幕墙以 2 秒为间隔变隐藏」在几何上就是「一个 count=8 的平面」，
     * 几何判据对它<b>一个字都说不出来</b>。但若隐藏段太长，它就不是「读节奏」而是
     * 「长时间无敌」，玩家无从准备。
     *
     * <p>本判据完全静态可计算：周期与占空比都是节拍上的标量。
     */
    private static List<String> lintVisibility(String where, Behaviour.Visibility visibility) {
        List<String> violations = new ArrayList<>();
        int period = visibility.periodTicks();
        double duty = visibility.duty();
        if (duty <= 0.0D) {
            violations.add(where + ": 违反 R4——相位隐藏的可见期占空比为 0，"
                    + "弹幕将全程不可见且不可碰");
            return violations;
        }
        if (duty >= 1.0D) {
            return violations;
        }
        double visibleTicks = duty * period;
        double hiddenTicks = period - visibleTicks;
        if (visibleTicks < MIN_VISIBLE_TICKS) {
            violations.add(String.format("%s: 违反 R4——可见段仅 %.0f tick，"
                    + "短于玩家反应下限 %d tick（相位隐藏会退化成「突然无敌」）",
                    where, visibleTicks, (int) MIN_VISIBLE_TICKS));
        }
        if (hiddenTicks > MAX_BLIND_TICKS) {
            violations.add(String.format("%s: 违反 R4——隐藏段 %.0f tick，"
                    + "超过致盲上限 %d tick（弹幕墙会读作长时间无敌而非读节奏）",
                    where, hiddenTicks, (int) MAX_BLIND_TICKS));
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
