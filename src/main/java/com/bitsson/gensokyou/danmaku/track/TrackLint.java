package com.bitsson.gensokyou.danmaku.track;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 轨道表静态校验器——把「三维可读性契约」R1/R2/R3 变成可断言的规则。
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
 * <p>另有一条本 mod 特有的硬约束：
 * <ul>
 *   <li><b>轨道视觉独占</b>——同符卡内任两轨在（色相/速度/尺寸/形状）四项中至少一项不同。
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
     * 单张符卡可声明的密度豁免上限（相对预算的倍数）。
     *
     * <p>豁免是给「估值模型结构性失真」的情形开的口子，不是给人随手放宽预算的。
     * 封顶 8 倍意味着：一张卡最多能声明到 960 颗/玩家，再高 MUST 走「改估值模型
     * 或改设计」这条路——而那正是应该发生的事。
     */
    public static final double MAX_DENSITY_WAIVER_FACTOR = 8.0D;

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
            for (Track track : card.tracks()) {
                // 声明色现在真的被读取了（TrackRunner 用 track.color()），所以它必须是
                // 本 BOSS 的签名色之一，否则「每轨一种独占视觉标识」就退化成作者自觉。
                if (!palette.contains(track.color())) {
                    violations.add(String.format("%s: 符卡「%s」轨「%s」的颜色 #%06X 不在签名色盘内",
                            owner, card.name(), track.name(), track.color() & 0xFFFFFF));
                }
            }
        }
        if (!palette.fits(maxTracks)) {
            violations.add(String.format("%s: 色盘容量 %d < 最大并发轨道数 %d",
                    owner, palette.size(), maxTracks));
        }
        Set<Integer> used = new LinkedHashSet<>();
        for (SpellCard card : cards) {
            for (Track track : card.tracks()) {
                used.add(track.color() & 0xFFFFFF);
            }
        }
        if (used.size() > palette.size()) {
            violations.add(String.format("%s: 符卡表用到 %d 个色号，色盘只有 %d 个",
                    owner, used.size(), palette.size()));
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
        violations.addAll(lintTimelineReplays(tag, card));
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
            violations.addAll(lintFormation(tag, track));
            for (Track.Beat beat : track.beats()) {
                violations.addAll(lintBeat(tag, track, beat));
            }
        }
        // R3：稳态并发密度（逐轨道判定；一个符卡至多 3 条并发轨道，故按轨累加仍是个位数倍）。
        double steady = 0.0D;
        for (Track track : card.tracks()) {
            steady += steadyStateEstimate(track);
        }
        double budget = card.hasDensityWaiver() ? card.densityWaiver() : STEADY_STATE_BUDGET;
        if (card.hasDensityWaiver() && budget > STEADY_STATE_BUDGET * MAX_DENSITY_WAIVER_FACTOR) {
            violations.add(String.format("%s: 密度豁免 %.0f 超过预算的 %d 倍（%.0f）"
                            + "——豁免是有界例外，不是空白支票",
                    tag, budget, (int) MAX_DENSITY_WAIVER_FACTOR,
                    STEADY_STATE_BUDGET * MAX_DENSITY_WAIVER_FACTOR));
        }
        if (steady > budget) {
            violations.add(String.format("%s: 违反 R3 层限——稳态并发密度约 %.0f 颗/玩家 > 预算 %.0f"
                    + "（判据为稳态并发数，不是每拍发数%s）",
                    tag, steady, budget,
                    card.hasDensityWaiver() ? "；本卡声明了密度豁免，理由见 SpellCard#densityWaiver" : ""));
        }
        return violations;
    }

    /**
     * 多拍显式时间线 MUST 声明循环长度，否则它只响一次。
     *
     * <p>节拍判据跑在<b>循环内的步号</b>上：{@code step = cycleTicks > 0 ? tick % cycle : tick}。
     * 于是一张「显式枚举拍（{@code repeatEvery == 0}）且未声明循环长度」的符卡，
     * 其节拍判据退化为「绝对 tick 等于拍号」——每个拍各响一次，之后永远不再发射。
     *
     * <p>这类符卡<b>不报错、不崩、lint 全绿</b>，症状只是「进卡几秒后彻底静默」。
     * 而它恰恰最容易被写成「持续型」：作者以为「不声明循环」=「一直放」，
     * 实际那正是保证它停掉的设置。踩过两次（阶段 1 的环、阶段 3 的雨），
     * 故在此静态拒绝。
     *
     * <p><b>「一次性过场」用单拍表达</b>：{@code repeatEvery(0)} + 一个拍就是「响一次就停」，
     * 它不违反本规则（本判据只管多拍）。刻意不为此再开一个结构豁免——
     * 「多拍但不想重放」与「忘了声明循环」在结构上无法区分，
     * 而前者几乎没有使用场景，后者是反复发生的错误。
     */
    private static List<String> lintTimelineReplays(String tag, SpellCard card) {
        List<String> violations = new ArrayList<>();
        if (card.hasCycle()) {
            return violations;
        }
        for (Track track : card.tracks()) {
            if (track.repeatEvery() != 0 || track.beats().size() <= 1) {
                continue;
            }
            violations.add(String.format("%s: 轨「%s」是多拍显式时间线（%d 拍、repeatEvery=0）"
                            + "但本卡未声明循环长度——节拍判据会退化成「绝对 tick 等于拍号」，"
                            + "于是这些拍各响一次后永不重放，现象是「进卡几秒后彻底静默」。"
                            + "若本卡本就该持续，请声明 cycleTicks；"
                            + "若本该只响一次，请压成单拍",
                    tag, track.name(), track.beats().size()));
        }
        return violations;
    }

    /**
     * 编队帧的静态判据（需求②③④）。
     *
     * <p>编队帧把弹位「写死」成一个 {@code (t, p₀)} 的函数，因此与三类既有运动
     * 语义互斥，MUST 在静态期就拒掉，而不是等到实机里看到「弹莫名停住」或
     * 「弹一卡一卡地抽搐」：
     * <ul>
     *   <li>{@code MINE} / {@code HOVER}——两者都靠「把速度清零」表达语义，而编队帧
     *       每 tick 都会重新给出一个非零位移，语义被直接覆盖；</li>
     *   <li>{@code CURVE}——曲射每 tick 改写速度向量，而编队帧已经把位置写死。
     *       两者同时开时曲射<b>静默失效</b>：弹看起来在动但轨迹没变，
     *       这种「配了等于没配」的故障比直接报错难查得多。</li>
     * </ul>
     *
     * <p>注意 {@code SPEED_PROFILE} <b>不</b>在互斥之列：它是编队弹「沿弹道推进」
     * 那一项的来源，与编队帧是<b>相加</b>关系。
     */
    private static List<String> lintFormation(String tag, Track track) {
        List<String> violations = new ArrayList<>();
        Behaviour.Formation formation = track.formation();
        for (Track.Beat beat : track.beats()) {
            if (formation.active() && formation.conflictsWith(beat.behaviour().motion())) {
                violations.add(String.format("%s: 轨「%s」t=%d 同时用了编队帧与运动「%s」"
                                + "——编队帧已接管弹位，该运动会与之争夺位置权威",
                        tag, track.name(), beat.tick(),
                        beat.behaviour().motion().kind()));
            }
            // 径向爆散的方向是「弹自身位置 → 参考点」的连线，参考点即编队帧的中心。
            // 没有帧就没有参考点，弹会退化为沿原方向飞——不报错，只是「花没有散开」。
            if (!formation.providesBurstReference()
                    && beat.behaviour().motion().kind() == Behaviour.Motion.Kind.BURST) {
                violations.add(String.format("%s: 轨「%s」t=%d 用了径向爆散但未挂编队帧"
                        + "——爆散方向需要参考点，而参考点就是编队帧的中心",
                        tag, track.name(), beat.tick()));
            }
        }
        return violations;
    }

    /**
     * 一条轨道的<b>稳态并发密度</b>估值（颗/玩家）。
     *
     * <p>模型：每拍发出的弹各在玩家 6 格包络内停留约 {@code 包络直径 ÷ 弹速} 个 tick。
     *
     * <p><b>估值口径按编排形态分两种</b>——这是本方法正确性的关键：
     * <ul>
     *   <li><b>无限重复轨</b>（声明了正重复周期）：每周期发一次，贡献按周期摊薄，
     *       {@code Σ count × 停留 / period}。</li>
     *   <li><b>显式时间线</b>（{@code repeatEvery == 0}，节拍逐个枚举）：该轨在整条
     *       时间线跑完前<b>不会</b>重复发射，故贡献 MUST 按「全部节拍的<b>累计在场数</b>」
     *       计，MUST NOT 把每个节拍当作每 tick 发射。</li>
     * </ul>
     *
     * <p>后者若按前者算，会把「48 拍逐发、跑完即止」这种编排高估一个数量级——
     * 正确值远低于预算，错误值超预算十倍以上，于是任何<b>会停的编排</b>都 lint 不通过。
     *
     * <p>本估值是<b>保守的静态近似</b>：它只取弹速与包络直径，不涉及地形遮挡、
     * 弹与弹互斥、玩家走位等运行时因素。MUST NOT 被当作精确值使用，只用于
     * 「一数量级的错判」——即每拍糊几百发那种。
     */
    public static double steadyStateEstimate(Track track) {
        return track.repeatEvery() > 0 ? repeatingEstimate(track) : timelinePeakEstimate(track);
    }

    /** 无限重复轨：每周期贡献按周期摊薄。 */
    private static double repeatingEstimate(Track track) {
        int period = track.repeatEvery();
        double perTick = 0.0D;
        for (Track.Beat beat : track.beats()) {
            perTick += perBeatLoad(beat) / period;
        }
        return perTick;
    }

    /**
     * 显式时间线：取「全部节拍的在场区间」的最大重叠数。
     *
     * <p>每拍在包络内的停留视为一个区间 {@code [出生 tick, 出生 tick + 停留)}，
     * 区间权重为该拍的发数。最大重叠数即该轨的稳态并发峰值。
     *
     * <p>用扫线而非「逐拍求和」：求和会把「同一时刻先后到达」误算成并发，
     * 对 16 朵花每朵 3 秒寿命这种密集时间线会高估数倍。
     */
    private static double timelinePeakEstimate(Track track) {
        List<double[]> events = new ArrayList<>();
        for (Track.Beat beat : track.beats()) {
            double load = perBeatCount(beat);
            double residence = residenceOf(beat);
            if (load <= 0.0D) {
                continue;
            }
            int birth = beat.tick();
            events.add(new double[]{birth, load});
            events.add(new double[]{birth + Math.max(1.0D, residence), -load});
        }
        if (events.isEmpty()) {
            return 0.0D;
        }
        // 同 tick 先出后进：先减后加，否则「上一批恰好离场、下一批恰好出生」
        // 会被算成重叠，双批时间线的峰值凭空翻倍。
        events.sort((a, b) -> a[0] != b[0]
                ? Double.compare(a[0], b[0])
                : Double.compare(a[1], b[1]));
        double live = 0.0D;
        double peak = 0.0D;
        for (double[] event : events) {
            live += event[1];
            peak = Math.max(peak, live);
        }
        return peak;
    }

    /** 该拍一次发多少颗。权重 MUST 是「颗数」而非「颗数 × 停留」——
     *  峰值重叠数统计的是<b>同时在场多少颗</b>，把停留时长乘进去等于把并发数放大数十倍。 */
    private static double perBeatCount(Track.Beat beat) {
        return beat.params().count() * emissionMultiplier(beat.shape()) + extraShotCount(beat.shape());
    }

    /**
     * 形状实际多发出的弹数（不含 {@code count} 本身）。
     *
     * <p>{@link Shape#CAGE} 是 {@code count} 的三倍（三个正交面各转一圈），
     * 由 {@link #emissionMultiplier} 覆盖；{@link Shape#FLOWER} 则是
     * {@code count + 1}——多出来的那颗是花心，{@code count} 只数花瓣。
     */
    private static int extraShotCount(Shape shape) {
        return shape == Shape.FLOWER ? 1 : 0;
    }

    /** 单拍在其停留时长内贡献的「弹数 × tick」量（仅供无限重复轨摊薄用）。 */
    private static double perBeatLoad(Track.Beat beat) {
        return perBeatCount(beat) * residenceOf(beat);
    }

    /** 该拍发出的弹在玩家包络内的停留时长（tick）。 */
    private static double residenceOf(Track.Beat beat) {
        double speed = beat.params().speed();
        // 零速弹不飞越包络；只按每拍发数计其常驻量（溜め环带即此类）。
        return speed <= 1.0E-4D ? 1.0D : PLAYER_ENVELOPE_DIAMETER / speed;
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

    /** 弹幕的默认寿命上限。与 {@code AbstractDanmakuProjectile#MAX_LIFETIME_TICKS} 同值。 */
    private static int danmakuMaxLifetimeTicks() {
        return com.bitsson.gensokyou.entity.AbstractDanmakuProjectile.MAX_LIFETIME_TICKS;
    }

    static List<String> lintBeat(String tag, Track track, Track.Beat beat) {
        List<String> violations = new ArrayList<>();
        Shape shape = beat.shape();
        Shape.Params params = beat.params();
        String where = String.format("%s 轨「%s」t=%d %s", tag, track.name(), beat.tick(), shape);
        // 显影/可见性：激光有自己的生命周期，隐藏态对它另有语义时由下面单独判。

        // 段式运动：末段零速率 ⇒ 弹在段表走完后<b>永久悬停</b>。
        //
        // <p><b>为什么必须拦</b>：{@code segmentAt(age)} 越过段表末尾时会夹紧到最后一段，
        // 于是末段速率为 0 的弹会在段表结束那一刻定住，<b>一直停到寿命结束</b>
        // —— 现象是「弹突然定住不动却不消失」，无报错、无日志、且完全看不出是声明的问题。
        // 而交替式段表（「飞 1 秒 → 悬停 1 秒」循环）在<b>偶数段数</b>下末段恰是悬停段，
        // 于是「段数取偶数」这样一个看似无害的选择就会稳定复现该现象。
        //
        // <p>豁免：寿命短于段表总时长的弹不受影响 —— 它在段表走完前就消失了。
        if (beat.hasLegSpec()) {
            var legSpec = beat.legSpec();
            int legs = legSpec.packedLegs().length;
            if (legs > 0) {
                int lastSpeed = legSpec.packedLegs()[legs - 1] & 0xFFFF;
                int lifetime = beat.lifetimeTicks() > 0
                        ? beat.lifetimeTicks() : danmakuMaxLifetimeTicks();
                int scheduleTicks = 0;
                for (int packed : legSpec.packedLegs()) {
                    scheduleTicks += (packed >>> 16) & 0xFFFF;
                }
                if (lastSpeed == 0 && lifetime > scheduleTicks) {
                    violations.add(where + ": 段式运动的末段速率为 0，而寿命(" + lifetime
                            + " tick)长于段表总时长(" + scheduleTicks
                            + " tick) ⇒ 弹会在段表走完后永久悬停。"
                            + "请让末段有速度，或把寿命压到段表总时长以内");
                }
            }
        }

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
        //
        // 两种「随机」的上限语义不同，故分开判：CONE_RANDOM 的 spreadDeg 是锥张角（半角 = 一半），
        // AROUND_TARGET 的是「方向与原点→目标连线的夹角上限」。
        //
        // AROUND_TARGET 的上限是 180°（完全自由），**刻意不收紧**：激光靠
        // `Phase.DELAY` 预警，公平性来自预警而非方向。收紧只会让「背后交叉火网」
        // 这类设计写不出来，换来的好处是零。
        if (shape == Shape.CONE_RANDOM
                && (params.spreadDeg() <= 0.0D || params.spreadDeg() > CONE_RANDOM_MAX_SPREAD)) {
            violations.add(where + ": 违反 R1——随机锥张角必须在 (0, "
                    + CONE_RANDOM_MAX_SPREAD + "] 度内");
        }
        if (shape == Shape.AROUND_TARGET
                && (params.spreadDeg() < 0.0D
                    || params.spreadDeg() > Geometry.AROUND_TARGET_MAX_AIM_DEG)) {
            violations.add(where + ": 违反 R1——目标周围发射的瞄准夹角须在 [0, "
                    + Geometry.AROUND_TARGET_MAX_AIM_DEG + "] 度内，实际 "
                    + params.spreadDeg());
        }
        // LATTICE：瞄准夹角上限 90°（网的语义是「从四周朝内收拢」，允许射线指向背离目标的
        // 方向就成了一团没有方向的线），且直瞄比例须在 (0,1] —— 全散射没有必须躲的，
        // 全直瞄没有夹缝可找。
        if (shape == Shape.LATTICE) {
            if (params.radius() <= 0.0D) {
                violations.add(where + ": LATTICE 的区域外半径须为正，实际 " + params.radius());
            }
            if (params.spreadDeg() < 0.0D || params.spreadDeg() > Geometry.LATTICE_MAX_AIM_DEG) {
                violations.add(where + ": 违反 R1——激光网的瞄准夹角须在 [0, "
                        + Geometry.LATTICE_MAX_AIM_DEG + "] 度内，实际 " + params.spreadDeg());
            }
            if (params.aimBias() <= 0.0D || params.aimBias() > 1.0D) {
                violations.add(where + ": 激光网的直瞄比例须在 (0, 1] 内，实际 "
                        + params.aimBias() + "（0 = 没有必须躲的；1 = 没有夹缝）");
            }
        }
        if (shape == Shape.AROUND_TARGET && params.radius() <= 0.0D) {
            violations.add(where + ": AROUND_TARGET 的目标周围区域半径须为正，实际 " + params.radius());
        }
        // 侧挂圆盘：整圈排满且不留缺口即是一面幕墙，与其它环形同理须留缺口。
        //
        // <p><b>例外：先停住、之后才启动</b>（{@link Behaviour.Motion.Kind#RECLAIM}）。
        // R1 针对的「幕墙」是<b>不可读</b>的墙，而可读性来自时间窗，不只来自有没有缝：
        // 一面<b>静止</b>三秒的闭合圆盘不是墙，是一道<b>预告</b> —— 玩家有三秒看清它、
        // 判断它、走到它的三维包围之外（竖直圆盘本就可以从上下与两侧绕过）。
        // 而「瞬间铺满并立刻开始流动」才是没有观察余量的那种（既有测试
        // {@code discRingNeedsGapWhenItFillsTheCircle} 用的正是后者）。
        //
        // <p><b>为什么用 motion kind 而不是 harmlessTicks</b>：无害只说明「不掉血」，
        // 不说明「不动」。一批<b>无害期内在飞向你</b>的闭合圆盘仍然是墙。
        // {@code RECLAIM} 精确地表达「这批弹先静止停留，到 atAge 才启动」。
        //
        // <p><b>与 R2 的关系</b>：R2 通过 {@link Shape#guaranteesGap} 判定，而它对
        // {@code DISC_RING} 返回 {@code true}（竖直圆盘在三维里有解），故 R2 从不拦它；
        // R1 此前硬编码 {@code gapDeg <= 0} 而不查该方法 —— 两条规则对<b>同一形状</b>
        // 的可解性判断互相矛盾。本例外把 R1 对齐到 R2 的立场，但只对「先停住」这一类放行，
        // 不整体豁免 {@code DISC_RING}（否则既有那条判据会变成空断言）。
        if (shape == Shape.DISC_RING
                && params.count() >= R2_SEAL_COUNT
                && params.gapDeg() <= 0.0D
                && beat.behaviour().motion().kind() != Behaviour.Motion.Kind.RECLAIM) {
            violations.add(where + ": 违反 R1 前向威胁——侧挂圆盘单拍排满且未留缺口（gapDeg=0）");
        }
        // 二维栅格的角间隔为 0 会退化成「全部重叠在一处」，与「格点阵」的语义不符。
        if (shape == Shape.GRID_FACING && params.count() > 1 && params.spreadDeg() <= 0.0D) {
            violations.add(where + ": 二维角度栅格的每轴角间隔须为正（spreadDeg=" + params.spreadDeg()
                    + "）——为 0 时所有发重叠在同一条射线上");
        }
        // 需要解析地形的形状必须声明锚定，否则生成点就停在半空。
        if (shape.needsWorldAnchor() && beat.anchor() == Track.Beat.SpawnAnchor.NONE) {
            violations.add(where + ": " + shape + " 的生成点需向下解析地面/空气，"
                    + "但本拍未声明 SpawnAnchor");
        }
        // R1 的另一种兑现：无约束全向随机 MUST 以「该发在固定年龄前不生效」兑现公平性。
        //
        // 反向亦不成立：若弹的有效寿命远大于无害期，声明就是一个晃眼的空话——
        // 玩家在第 3 秒躲开的那发，第 4 秒仍然在朝他飞。故要求无害期不得短于寿命的一半。
        if (shape.isOmniRandom()) {
            if (!beat.hasHarmlessWindow()) {
                violations.add(where + ": " + shape + " 是全向无约束随机，"
                        + "MUST 声明无害窗口（harmlessTicks > 0）作为可读性的兑现，"
                        + "否则生成方位完全在玩家视野外");
            } else if (beat.lifetimeTicks() > 0
                    && beat.harmlessTicks() * 2 > beat.lifetimeTicks()) {
                violations.add(String.format("%s: 无害窗口 %d tick 超过寿命 %d tick 的一半"
                                + "——声明形同虚设，弹在玩家躲开后仍持续朝他生效",
                        where, beat.harmlessTicks(), beat.lifetimeTicks()));
            }
        }

        // 激光 + 速度语义：激光是静止的射线，速率曲线对它是空转。
        // 不静默忽略——「配了却没反应」正是本项目反复踩的那类故障。
        if (beat.projectile().isLaser()
                && beat.behaviour().motion().kind() == Behaviour.Motion.Kind.SPEED_PROFILE) {
            violations.add(where + ": 激光是静止射线，配速率曲线无效（位置由激光自身生命周期决定）");
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

        // 弹速必须为正——除非这批弹本来就不动。
        //
        // <p>静止散布类（溜め）由几何给出零方向，「弹速」对它没有意义；
        // <b>激光更是如此</b>：它是一条静止的射线，位置由自身生命周期决定，
        // 「弹速为 0」是它的正确取值而非漏配。此前把激光按球弹判，
        // 于是任何地射激光都 lint 不通过。
        boolean inherentlyStatic = shape == Shape.SCATTER_STATIC || beat.projectile().isLaser();
        if (!inherentlyStatic && params.speed() <= 0.0D) {
            violations.add(where + ": 弹速必须为正（静止散布类与激光除外）");
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
}
