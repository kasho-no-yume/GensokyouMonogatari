package com.bitsson.gensokyou.danmaku.track;

import com.bitsson.gensokyou.danmaku.motion.DanmakuSpeedProfile;
import com.bitsson.gensokyou.danmaku.motion.FormationFrame;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import static com.bitsson.gensokyou.danmaku.track.Track.of;

import java.util.List;

/**
 * 四只召唤 BOSS 的符卡表。
 *
 * <p>集中在一处而非散进实体类，因为符卡表是<b>内容</b>：改它不该碰实体代码。
 *
 * <p>每拍写成 {@code 几何 × 行为} 两段：{@code .at(tick, Shape.X, 几何参数, 行为, 目标模式)}。
 * 行为一律经 {@link Behaviour} 的工厂显式给出，MUST NOT 靠形状名隐含——改前
 * {@code HOVER_BURST} 这类名字把行为编码进了几何，导致「悬停的环」无法表达。
 *
 * <p>每只 BOSS 以其<b>角色自身的签名弹幕主题</b>区分，MUST NOT 依赖任何形式化的
 * 段式分类装置。符卡表 MUST 通过 {@link TrackLint} 的三维可读性契约（R1/R2/R3）、
 * 轨道视觉独占与签名色盘容量校验。
 */
public final class BossCards {

    /**
     * 符卡名的 lang 键。
     *
     * <p>键名按 <b>boss_id + 卡序</b> 排，MUST NOT 从中文名推导——中文名会改
     * （早期版本里同一张卡就叫过「散」和「乱焔散」），键名跟着改等于把存档里
     * 已有的语言条目一起作废。
     *
     * @param bossId 注册名（与 {@code ModEntityTypes} / lang 的 {@code entity.gensokyou.*} 同源）
     * @param index  该 BOSS 符卡表中的序号（从 1 起，与玩家看到的顺序一致）
     */
    private static Component card(String bossId, int index) {
        return Component.translatable("spellcard.gensokyou." + bossId + "." + index);
    }

    private BossCards() {
    }

    // ==================================================================
    // 大妖精 —— 三段有演出节奏的内容。
    //
    // 每张卡都声明了循环长度，于是血量跨阈值时切换被挂起到循环边界（不叠加半程）。
    // 时间轴一律用 repeatEvery(0) + 显式枚举每一拍：这三张卡都是「会停的编排」，
    // 用重复周期表达不出来。
    //
    // 阶段 2 的花是唯一的「全向无约束随机」轨道，它以「爆散前完全不生效」兑现可读性
    // （harmlessTicks=100，lint 强制该声明与它不得超过寿命的一半）。
    //
    // 颜色：at(0) 淡青（默认攻击、雨）／at(1) 粉白（环）／at(2) 粉红（花海）
    //      ／at(5) 淡蓝（激光）。运行时取的是各轨<b>声明色</b>，不是「卡内轨序」。
    //      激光必须给足饱和度——过淡的色（如 #DCE9FF）叠上激光的加法发光会洗成白灰。
    // ==================================================================

    public static final SignaturePalette BIG_FAIRY_PALETTE =
            SignaturePalette.of(0x9BE7FF, 0xFFD9F0, 0xFF8AD4, 0xFFE9A8, 0xC9C4FF, 0x7FB4FF);

    /** 阶段 1 的循环长度：6 秒放环（120 tick）+ 6 秒游走（120 tick）。 */
    private static final int RING_CARD_CYCLE = 240;
    /** 阶段 1 放环段的结束 tick：过此刻起 BOSS 恢复游走。 */
    public static final int RING_CARD_RING_TICKS = 120;
    /** 环弹的飞行与起飞速度（格/tick）：12 格/秒。 */
    private static final double RING_LAUNCH_SPEED = 0.6D;
    /** 阶段 2 的循环长度（12 秒）。 */
    private static final int FLOWER_CARD_CYCLE = 240;
    /**
     * 阶段 2 每朵花的间隔（0.2 秒 = 4 tick），16 朵占 64 tick。
     * 最后一朵在第 60 tick 抛出。
     *
     * <p>间隔 MUST NOT 调稀来「填满周期」——见 {@link #FLOWER_LIFETIME}，那会让同时开花的
     * 朵数从 16 掉到 4，压迫密度只剩四分之一（{@code TrackLint} 按 50 tick 的危险窗口
     * 重叠数计密度，峰值从 736 掉到 184）。
     */
    private static final int FLOWER_STEP = 4;
    private static final int FLOWER_COUNT = 16;
    /**
     * 阶段 2 单朵花的编排时长：飞 3 秒（60 tick）→ 悬停 2 秒（40 tick）→ 第 100 tick 爆散。
     *
     * <p>原为「飞 1 秒 → 悬停 2 秒」。1 秒的抛射行程只有 0.5×0.24×20 = 2.4 格，
     * 于是 16 朵全部停在 BOSS 身周 2.4 格的球内，实测读作「一团糊在一起」而不是一片海。
     * 拉长到 3 秒后行程 7.2 ���，悬停时花朵沿各自分布方向散成环状。
     */
    private static final int FLOWER_RAMP = 60;
    private static final int FLOWER_HOLD = 40;
    private static final int FLOWER_BURST_AGE = FLOWER_RAMP + FLOWER_HOLD;
    /**
     * 花弹从出生起存活 20 秒（400 tick），寿命独立于符卡的 12 秒发射循环。
     * 第 100 tick 爆散后仍有 300 tick 的飞行时间。
     */
    private static final int FLOWER_LIFETIME = 400;
    /**
     * 阶段 3 的循环长度：<b>MUST 等于内容跨度</b>（10 拍 × 20 tick = 200），不得留余量。
     *
     * <p>这卡曾是「持续型」却复用了 {@link #FLOWER_CARD_CYCLE}。当时两个值恰好都是 200，
     * 看不出问题；一旦花卡的循环被调长，雨卡就跟着变成 240 —— 于是每轮最后 40 tick
     * 完全静默，正是本文件顶部注释描述的那类「放一段然后哑掉」。
     *
     * <p>为什么雨卡必须等于跨度、花卡却可以重叠：雨卡是「一拍一滴」的稀疏型，
     * 循环一旦长过跨度就会在末尾留一段静默；花卡是多代重叠型——寿命 200 与循环 240 同量级，
     * 一代还没死完下一代已经开始，本来就不该追求「末朵编排先结束」。它要的是稳态密度恒定，
     * 也就是 {@link #FLOWER_COUNT} × 单朵寿命 / 循环 ≈ 13~14 朵在场，不多不少。
     * 雨卡每秒一拍、拍到第 9 秒就满了，循环等于跨度才是「每秒一滴、从不间断」。
     */
    private static final int RAIN_CARD_CYCLE = 200;

    public static List<SpellCard> bigFairy() {
        return List.of(
                ringCard(), flowerCard(), rainCard());
    }

/**
 * 1 阶段 · 花符[弹幕花环]（66%~100%）
 *
 * <p><b>单拍放环</b>：第 0 拍一次生成 48 颗，相位由 {@code Geometry} 的
 * {@code i × 360/48} 在这一次调用内展开，于是 48 颗落在<b>同一个</b>圆上。
 * 环挂在 BOSS <b>身后</b> 6 格处、半径 4 格的竖直圆盘上，平面垂直于
 * 「BOSS → 该玩家」—— 该方向在生成那一 tick 锁定，此后 BOSS 转向不影响已生成的环。
 *
 * <p><b>为什么必须单拍而不是「48 拍各 1 颗」</b>：后者听上去等价，其实不然 ——
 * {@code TrackRunner#emitTrack} <b>每个 tick 重新采样</b>发射原点与瞄准方向，
 * 而 48 个 beat 分属 48 个 tick，于是 48 个圆心沿 BOSS 与玩家的相对运动排开，
 * 相位却仍按 7.5°/拍递增 ⇒ 结果是一条<b>螺线</b>而不是圆。
 * 实测（{@code RingCardCrossBeatConsistencyTest}）：BOSS 被
 * {@code movementLocked} 钉住时圆心仍偏离 <b>2.615 格</b>（环半径只有 4 格），
 * 首尾两颗相距 3.154 格而相邻两颗只隔 0.523 格 —— 环根本没有闭合。
 * 单拍生成让 48 颗共享同一次采样的圆心，这是唯一能保证共圆的方式。
 *
 * <p>每颗弹生成后静止 3 秒（速率先压到 0），第 3 秒<b>重新瞄准</b>它所属的那名玩家
 * 并以 6 格/秒射出。等待期能伤玩家：环在那 3 秒里是一道实体墙。
 *
 * <p>按人复制（{@link TargetMode#AIMED}）：每名被锁定玩家身后各一个环，
 * 且各环的同一序号在同一 tick 出现。5 人 = 240 颗/轮。
 */
private static SpellCard ringCard() {
    Track.Builder ring = of("花环", BIG_FAIRY_PALETTE.at(1))
            .identity(1, 0, 1)
            .repeatEvery(0);
    ring.at(0, Shape.DISC_RING, Shape.Params.defaults()
                    .count(48)
                    .radius(4.0D)
                    .offsetForward(-6.0D)
                    .size(0.7D)
                    .speed(RING_LAUNCH_SPEED),
// 静止 3 秒 → 重新瞄准 → 12 格/秒。曲线只管前半段的「停住」。
            Behaviour.NONE.withMotion(Behaviour.Motion.reclaim(
                    DanmakuSpeedProfile.decelerateAndHold(RING_LAUNCH_SPEED, 1),
                    60, RING_LAUNCH_SPEED, Behaviour.TARGET_AUTO)),
            TargetMode.AIMED, 0, 60);
    return new SpellCard(card("big_fairy", 1), 1.00D, List.of(ring.build()), RING_CARD_CYCLE);
}

    /**
     * 2 阶段 · 花符【花之海洋】（33%~66%）
     *
     * <p>每 4 tick 抛 1 朵，共 16 朵（占 64 tick），方向<b>全向随机</b>，
     * 花平面<b>独立随机</b>且不必垂直于飞行方向——于是 16 朵读作一片海而不是一堵共面的墙。
     *
     * <p>每朵 = 1 颗中心弹（4 倍伤害、2 倍大）+ 45 颗花瓣弹（5 花瓣 × 9）。
     * 整朵以 12 格/秒飞 3 秒、悬停 2 秒，然后<b>径向爆散</b>：花瓣朝各自到花心的连线
     * 反方向以 4 格/秒射出，中心弹因恰好落在参考点上（连线退化）改朝随机目标玩家
     * 以 6 格/秒射出。
     *
     * <p>寿命 60 秒（5 秒成形 + 55 秒散开）。整朵共享一条编队帧作参考点，
     * 该帧在爆散时被解除——否则弹会被拽回队形里继续转，现象是「炸开了又缩回去」。
     */
    private static SpellCard flowerCard() {
        Track.Builder flowers = of("花海", BIG_FAIRY_PALETTE.at(2))
                .identity(0, 1, 0)
                .repeatEvery(0)
                // 只要参考点：爆散方向是「弹自身位置 → 花心」的连线。
                // 刻意<b>不</b>自旋（花瓣长短已编码在出生点里），也不呼吸。
                .formation(Behaviour.Formation.reference());
        for (int i = 0; i < FLOWER_COUNT; i++) {
            flowers.at(i * FLOWER_STEP, Shape.FLOWER, Shape.Params.defaults()
                            .count(45)
                            .rose(5, 1.2D)
                            .radius(1.8D)
                            .size(0.55D)
                            .speed(0.24D),
                    Behaviour.NONE.withMotion(Behaviour.Motion.burst(
                            // 飞 3 秒（12 格/秒）→ 停 2 秒 → 爆散
                            new DanmakuSpeedProfile(0.24D, FLOWER_RAMP, 0.0D, FLOWER_HOLD, 0.0D, 0, 0.0D),
                            FLOWER_BURST_AGE, 0.2D, 0.3D, Behaviour.TARGET_RANDOM)),
                    TargetMode.SELF_AXIS, Projectile.SPHERE, FLOWER_LIFETIME, FLOWER_BURST_AGE,
                    Track.Beat.SpawnAnchor.NONE, 4.0D, 2.0D);
        }
        return new SpellCard(card("big_fairy", 2), 0.66D, List.of(flowers.build()),
                FLOWER_CARD_CYCLE, 800.0D);
    }

    /**
     * 3 阶段 · 夏符【雨季喷泉】（0~33%）
     *
     * <p>持续型，循环长度 = 10 秒：雨轨每秒一拍（24 颗随机撒点，从 BOSS 上方 60 格往下找
     * 第一个空气再生成，竖直向下 6 格/秒）；激光轨同样每秒一拍（地面锚定、竖直向上，
     * 预警 2 秒、持续 6 秒、长 80）。10 拍 × 20 tick 正好占满一个 200 tick 循环，
     * 于是它每 10 秒重放一轮、永不停止。
     *
     * <p><b>「持续型」必须给循环长度</b>：本卡的拍是显式枚举的（{@code repeatEvery(0)}），
     * 而显式时间线只会在<b>有循环可依</b>时重放——循环长度为 0 时节拍判据退化成
     * 「绝对 tick 等于拍号」，于是这 10 拍各响一次后永远不再发射。
     * 症状是「进卡 9 秒后彻底静默」，而雨从 60 格高空落下还要 10 秒才到人眼前，
     * 第一批落地时生成早已停过——于是观感是「完全没看见 BOSS 放」。
     *
     * <p>「每 5 次至少一次压在玩家头上」写成<b>数据</b>：每第 5 拍用
     * {@link Track.Beat.SpawnAnchor#PLAYER_GROUND}，其余拍随机。规则不进代码计数器。
     *
     * <p>两轨并发，颜色/尺寸/行为三项各自不同，满足视觉独占。
     */
    private static SpellCard rainCard() {
        Track.Builder rain = of("骤雨", BIG_FAIRY_PALETTE.at(0))
                .identity(0, 0, 0)
                .repeatEvery(0);
        Track.Builder pillar = of("喷泉", BIG_FAIRY_PALETTE.at(5))
                .damageScale(1.1D)
                .identity(1, 1, 1)
                .repeatEvery(0);
        for (int second = 0; second < 10; second++) {
            int tick = second * 20;
            rain.at(tick, Shape.SCATTER_FALL, Shape.Params.defaults()
                            .count(24)
                            .radius(30.0D)
                            .offsetUp(60.0D)
                            .size(0.8D)
                            .speed(0.3D),
                    Behaviour.NONE, TargetMode.SELF_AXIS, Projectile.SPHERE, 0, 0,
                    Track.Beat.SpawnAnchor.FIRST_AIR_BELOW, 1.0D, 1.0D);
            pillar.at(tick, Shape.PILLAR_UP, Shape.Params.defaults()
                            .count(1)
                            .radius(30.0D)
                            .size(0.6D)
                            .speed(0.0D),
                    Behaviour.NONE, TargetMode.SELF_AXIS,
                    Projectile.laser(80.0D, 0.8D, 2.0D, 6.0D), 0, 0,
                    (second % 5 == 4)
                            ? Track.Beat.SpawnAnchor.PLAYER_GROUND
                            : Track.Beat.SpawnAnchor.GROUND_BELOW,
                    1.0D, 1.0D);
        }
        // 循环长度 MUST 给出：10 拍 × 20 tick 正好占满 200 tick，循环即「每秒一发、永不停止」。
        return new SpellCard(card("big_fairy", 3), 0.33D, List.of(rain.build(), pillar.build()),
                RAIN_CARD_CYCLE);
    }

    // ==================================================================
    // 黑谷山女（土蜘蛛） —— T1，接在大妖精之后。
    //
    // 设计基准见 change redesign-yamame-spellcards 的 design.md D4~D8。
    // 两组母题：第 1、3 张「蛛丝编织」，第 2、4 张「瘴气螺旋」。
    //
    //  - 蛛丝 = FAN 射出的引线弹挂 Trail，沿线每 N tick 留一颗静止尾珠；
    //  - 螺旋 = Shape.SPIRAL，编队呼吸表现膨胀、SPEED_PROFILE 表现「停批」凝结。
    //
    // 每张卡都有独立循环长度；织网 / 展开阶段由 YamameEntity#movementLocked 锁位，
    // 使多批共享同一中心、图案不随 BOSS 游走漂移。
    //
    // 颜色：at(0) 金（丝/压网）／at(1) 陶土（石窟丝）／at(2) 赤・at(3) 紫（瘴的走/停两批）
    //      ／at(4) 病黄绿（热病副螺旋）。容量 6 ≥ 最大并发 3。
    // ==================================================================

    public static final SignaturePalette YAMAME_PALETTE =
            SignaturePalette.of(0xE0A24B, 0xD9683A, 0xC8383E, 0x8E5FD8, 0xB8D24A, 0x5FBF7A);

    /** 各卡循环长度（tick）。显式时间轴靠它重放，符卡切换也挂起到它的边界。 */
    public static final int YAMAME_WEB_CYCLE = 200;
    public static final int YAMAME_MIASMA_CYCLE = 260;
    public static final int YAMAME_NEST_CYCLE = 260;
    public static final int YAMAME_FEVER_CYCLE = 260;

    /** 引线弹保留 5 秒；尾珠使用 BOSS 基础弹幕的 20 秒寿命。 */
    private static final int YAMAME_SPINNER_LIFETIME = 100;
    private static final int YAMAME_NODE_LIFETIME = 400;
    /** 尾珠生成间隔（tick）。 */
    private static final int YAMAME_TRAIL_EVERY = 4;
    /** 尾珠静止多少 tick 后开始漂移（约 2 秒）。 */
    private static final int YAMAME_NODE_HOLD = 40;
    /** 尾珠漂移速率（格/tick）——缓慢飘散。 */
    private static final double YAMAME_NODE_DRIFT = 0.06D;

    public static List<SpellCard> yamame() {
        return List.of(webCard(), miasmaCard(), nestCard(), feverCard());
    }

    /**
     * 1 阶段 · 罠符「キャプチャーウェブ」（100%~75%）
     *
     * <p>稀疏蛛丝：从她手中散出一把引线弹（{@code FAN}），每颗沿线每 N tick 留下静止尾珠，
     * 尾珠连成一张宽缝的网。缺口相位逐轮推进，玩家踩着缝换位即可，不必撤离。
     */
    private static SpellCard webCard() {
        Track.Builder weave = of("织丝", YAMAME_PALETTE.at(0))
                .identity(0, 0, 0)
                .repeatEvery(60)
                .phaseStep(24.0D);
        // 半球随机散射：3 条子拍各 6 根，合计 18 根/轮；单拍 6 发低于 R2 封死阈值，
        // 且方向被限制在「BOSS → 玩家」的半球包络内，故 SELF_AXIS 单份即可，不按人复制。
        for (int sub = 0; sub < 3; sub++) {
            weave.at(sub * 20, Shape.CONE_RANDOM, Shape.Params.defaults()
                            .count(6).spread(180.0D).speed(0.70D).size(0.75D),
                    Behaviour.NONE.withTrail(Behaviour.Trail.drifting(
                            YAMAME_TRAIL_EVERY, YAMAME_NODE_LIFETIME,
                            YAMAME_NODE_HOLD, YAMAME_NODE_DRIFT)),
                    TargetMode.SELF_AXIS, Projectile.piercingSphere(),
                    YAMAME_SPINNER_LIFETIME, 0, Track.Beat.SpawnAnchor.NONE, 1.0D, 1.0D);
        }

        Track.Builder chase = of("追丝", YAMAME_PALETTE.at(1))
                .identity(1, 1, 1)
                .repeatEvery(70)
                .phaseStep(30.0D);
        chase.at(0, Shape.FAN, Shape.Params.defaults()
                        .count(3).spread(24.0D).speed(0.5D).size(0.4D),
                Behaviour.NONE, TargetMode.AIMED);

        return new SpellCard(card("yamame", 1), 1.00D,
                List.of(weave.build(), chase.build()), YAMAME_WEB_CYCLE);
    }

    /**
     * 2 阶段 · 瘴符「フィルドミアズマ」（75%~50%）
     *
     * <p>中央瘴气螺旋：以 BOSS 为编队中心的确定性螺旋（{@code SPIRAL}）向外膨胀；
     * 另加一轨「毒雾」——半球内随机方向的缓速分散弹，飘向玩家。
     */
    private static SpellCard miasmaCard() {
        Track.Builder flow = of("瘴涡", YAMAME_PALETTE.at(2))
                .identity(0, 0, 0)
                .formation(Behaviour.Formation.breathing(0.0D, FormationFrame.HORIZONTAL_PITCH_DEG,
                        0.0D, 1.0D, 0.4D, 80.0D))
                .repeatEvery(60)
                .phaseStep(9.0D);
        flow.at(0, Shape.SPIRAL, Shape.Params.defaults()
                        .count(20).radius(8.0D).rose(1, 3.0D).speed(0.18D).size(0.55D),
                Behaviour.NONE, TargetMode.SELF_AXIS, Projectile.piercingSphere());

        // 毒雾：半球内随机方向的缓速分散弹——螺旋绕在 BOSS 身边，毒雾则飘向玩家。
        // （原「凝瘴」是减速定住的螺旋，实测完全不动、无威胁，改为会飘的毒雾。）
        Track.Builder mist = of("毒雾", YAMAME_PALETTE.at(3))
                .identity(1, 1, 1)
                .repeatEvery(30)
                .phaseStep(41.0D);
        mist.at(0, Shape.CONE_RANDOM, Shape.Params.defaults()
                        .count(7).spread(180.0D).speed(0.12D).size(0.5D),
                Behaviour.NONE, TargetMode.SELF_AXIS, Projectile.piercingSphere());

        return new SpellCard(card("yamame", 2), 0.75D,
                List.of(flow.build(), mist.build()), YAMAME_MIASMA_CYCLE);
    }

    /**
     * 3 阶段 · 蜘蛛「石窟の蜘蛛の巣」（50%~25%）
     *
     * <p>密集蛛丝：同第 1 张的语法，蛛丝更密、速度更快；辅以「石窟吐丝」——
     * 朝玩家方向 60° 内的随机散射，逼走位。不使用脚下光柱与随机激光网。
     */
    private static SpellCard nestCard() {
        Track.Builder weave = of("石窟织网", YAMAME_PALETTE.at(1))
                .identity(0, 0, 0)
                .repeatEvery(60)
                .phaseStep(28.0D);
        // 同第 1 张的半球随机散射，蛛丝更密：3 条子拍各 7 根，合计 21 根/轮。
        for (int sub = 0; sub < 3; sub++) {
            weave.at(sub * 20, Shape.CONE_RANDOM, Shape.Params.defaults()
                            .count(7).spread(180.0D).speed(0.64D).size(0.75D),
                    Behaviour.NONE.withTrail(Behaviour.Trail.drifting(
                            YAMAME_TRAIL_EVERY, YAMAME_NODE_LIFETIME,
                            YAMAME_NODE_HOLD, YAMAME_NODE_DRIFT)),
                    TargetMode.SELF_AXIS, Projectile.piercingSphere(),
                    YAMAME_SPINNER_LIFETIME, 0, Track.Beat.SpawnAnchor.NONE, 1.0D, 1.0D);
        }

        // 石窟吐丝：朝玩家方向 60° 内的随机散射（spread=120 ⇒ 半角 60°）。
        // 取代原「压网」的环形追踪——那圈弹打不到人也不逼走位。
        Track.Builder spit = of("石窟吐丝", YAMAME_PALETTE.at(0))
                .identity(1, 1, 1)
                .repeatEvery(50)
                .phaseStep(33.0D);
        spit.at(0, Shape.CONE_RANDOM, Shape.Params.defaults()
                        .count(6).spread(120.0D).speed(0.45D).size(0.55D),
                Behaviour.NONE, TargetMode.SELF_AXIS, Projectile.piercingSphere());

        return new SpellCard(card("yamame", 3), 0.50D,
                List.of(weave.build(), spit.build()), YAMAME_NEST_CYCLE);
    }

    /**
     * 4 阶段 · 瘴気「原因不明の熱病」（25%~0%）
     *
     * <p>三重瘴气螺旋：中央大螺旋 + 左右两个小螺旋（以横向偏移并排）同步展开。
     * 难度来自安全区变化速度与三组图案的空间关系，而非弹数——靠后的卡用
     * {@code damageScale} 加压。左右小螺旋一正一反旋向，读起来是一对。
     */
    private static SpellCard feverCard() {
        Track.Builder centre = of("热病中心", YAMAME_PALETTE.at(2))
                .damageScale(1.25D)
                .identity(0, 0, 0)
                .formation(Behaviour.Formation.breathing(0.0D, FormationFrame.HORIZONTAL_PITCH_DEG,
                        0.0D, 1.0D, 0.45D, 70.0D))
                .repeatEvery(50)
                .phaseStep(11.0D);
        centre.at(0, Shape.SPIRAL, Shape.Params.defaults()
                        .count(14).radius(9.0D).rose(1, 3.0D).speed(0.20D).size(0.5D),
                Behaviour.NONE, TargetMode.SELF_AXIS, Projectile.piercingSphere());

        Track.Builder left = of("热病左", YAMAME_PALETTE.at(4))
                .damageScale(1.25D)
                .identity(1, 1, 1)
                .formation(Behaviour.Formation.breathing(0.0D, FormationFrame.HORIZONTAL_PITCH_DEG,
                        0.0D, 1.0D, 0.4D, 60.0D))
                .repeatEvery(50);
        left.at(0, Shape.SPIRAL, Shape.Params.defaults()
                        .count(6).radius(4.0D).rose(1, 1.5D).offsetRight(6.0D)
                        .speed(0.22D).size(0.45D),
                Behaviour.NONE, TargetMode.SELF_AXIS, Projectile.piercingSphere());

        Track.Builder right = of("热病右", YAMAME_PALETTE.at(3))
                .damageScale(1.25D)
                .identity(2, 2, 2)
                .formation(Behaviour.Formation.breathing(0.0D, FormationFrame.HORIZONTAL_PITCH_DEG,
                        0.0D, 1.0D, 0.4D, 60.0D))
                .repeatEvery(50);
        right.at(0, Shape.SPIRAL, Shape.Params.defaults()
                        .count(6).radius(4.0D).rose(-1, 1.5D).offsetRight(-6.0D)
                        .speed(0.22D).size(0.45D),
                Behaviour.NONE, TargetMode.SELF_AXIS, Projectile.piercingSphere());

        // 毒雾（更浓）：半球内随机方向的缓速分散弹。三重螺旋都在 BOSS 身边，
        // 只靠它们玩家绕圈即可规避；这轨把威胁铺向玩家所在的方向，逼其不能一味绕场。
        Track.Builder mist = of("毒雾", YAMAME_PALETTE.at(5))
                .damageScale(1.25D)
                .identity(3, 3, 3)
                .repeatEvery(26)
                .phaseStep(53.0D);
        mist.at(0, Shape.CONE_RANDOM, Shape.Params.defaults()
                        .count(7).spread(180.0D).speed(0.12D).size(0.5D),
                Behaviour.NONE, TargetMode.SELF_AXIS, Projectile.piercingSphere());

        return new SpellCard(card("yamame", 4), 0.25D,
                List.of(centre.build(), left.build(), right.build(), mist.build()),
                YAMAME_FEVER_CYCLE);
    }

    // ==================================================================
    // 八云蓝 —— T2 正主（妖妖梦 EX 面 BOSS）。正式 GeckoLib 残影模型 + 8 张考据符卡。
    //
    // 签名母题（角色自身的，不是人造装置）：九尾 / 式神 / 狐火 / 狐狸妖怪激光 / 阴阳术。
    // 八张卡按血量阈值等分递减：1.00 / 0.875 / … / 0.125。
    //
    // 主威胁几何一律朝玩家（AIMED 扇 / AROUND_TARGET / LATTICE / CAGE / PILLAR_UP /
    // DISC_RING），MUST NOT 用「朝没人的地方放烟花」的自轴环形。激光的旋转/扫射由
    // Projectile.LaserMotion 表达（轨道层接线见 capability danmaku-track-composition）。
    //
    // 颜色：金=九尾 / 狐火青=狐火（默认攻击）/ 白=尾尖符纸 / 靛蓝=长襟阴阳术 /
    //      符纸黄=式弹 / 紫=隙间与终局。
    // ==================================================================

    public static final SignaturePalette RAN_PALETTE =
            SignaturePalette.of(0xF2C14E, 0x66E8FF, 0xF5F0E6, 0x2E3A8C, 0xFFE066, 0x9C6BFF);

    /** 各卡循环长度（tick）：显式时间线靠它重放，符卡切换也挂起到它的边界。 */
    private static final int RAN_C1 = 120;
    private static final int RAN_C2 = 140;
    private static final int RAN_C3 = 160;
    private static final int RAN_C4 = 160;
    private static final int RAN_C5 = 200;
    private static final int RAN_C6 = 140;
    private static final int RAN_C7 = 160;
    private static final int RAN_C8 = 200;

    /** 八云蓝的八张签名符卡（考据见该 change 的 design.md）。 */
    public static List<SpellCard> yakumoRan() {
        return List.of(
                // 1 · 式神「仙狐思念」—— 狐灵自四周朝玩家收拢。
                new SpellCard(card("yakumo_ran", 1), 1.000D, List.of(
                        of("仙狐", RAN_PALETTE.at(1))
                                .identity(0, 0, 0)
                                .repeatEvery(40)
                                .at(0, Shape.AROUND_TARGET, Shape.Params.defaults()
                                                .count(10).radius(9.0D).spread(15.0D)
                                                .speed(0.30D).size(0.6D),
                                        Behaviour.NONE, TargetMode.AIMED)
                                .build(),
                        of("思念", RAN_PALETTE.at(2))
                                .identity(1, 1, 1)
                                .repeatEvery(60)
                                .at(0, Shape.FAN, Shape.Params.defaults()
                                                .count(5).spread(40.0D).speed(0.36D).size(0.5D),
                                        Behaviour.NONE, TargetMode.AIMED)
                                .build()), RAN_C1),
                // 2 · 式神「前鬼後鬼の守護」—— 前后护卫：相向扇 + 留缺口的笼。
                new SpellCard(card("yakumo_ran", 2), 0.875D, List.of(
                        of("前鬼", RAN_PALETTE.at(1))
                                .identity(0, 0, 0)
                                .repeatEvery(50)
                                .at(0, Shape.FAN, Shape.Params.defaults()
                                                .count(6).spread(60.0D).speed(0.30D).size(0.55D),
                                        Behaviour.NONE, TargetMode.AIMED)
                                .build(),
                        of("後鬼", RAN_PALETTE.at(5))
                                .identity(2, 1, 1)
                                .repeatEvery(70)
                                .at(0, Shape.CAGE, Shape.Params.defaults()
                                                .count(9).gap(70.0D).speed(0.26D).size(0.6D),
                                        Behaviour.NONE, TargetMode.SELF_AXIS)
                                .build()), RAN_C2),
                // 3 · 式輝「狐狸妖怪レーザー」—— 绕玩家旋转的激光星（招牌）。
                new SpellCard(card("yakumo_ran", 3), 0.750D, List.of(
                        of("狐妖激光", RAN_PALETTE.at(1))
                                .identity(0, 0, 0)
                                .repeatEvery(80)
                                .laserAt(0, Shape.AROUND_TARGET, Shape.Params.defaults()
                                                .count(8).radius(8.0D).spread(10.0D).size(0.5D),
                                        Projectile.laser(60.0D, 0.35D, 0.8D, 1.4D, false,
                                                Projectile.LaserMotion.spin(
                                                        new Vec3(0.0D, 1.0D, 0.0D), 1.5D)),
                                        TargetMode.AIMED)
                                .build(),
                        of("星芒", RAN_PALETTE.at(4))
                                .identity(3, 1, 0)
                                .repeatEvery(100)
                                .at(0, Shape.RING_FACING, Shape.Params.defaults()
                                                .count(10).gap(60.0D).speed(0.22D).size(0.55D),
                                        Behaviour.NONE, TargetMode.SELF_AXIS)
                                .build()), RAN_C3),
                // 4 · 式輝「四面楚歌チャーミング」—— 四面合围 + 补刀。
                new SpellCard(card("yakumo_ran", 4), 0.625D, List.of(
                        of("合围", RAN_PALETTE.at(5))
                                .identity(2, 0, 0)
                                .repeatEvery(90)
                                .at(0, Shape.CAGE, Shape.Params.defaults()
                                                .count(12).gap(60.0D).speed(0.24D).size(0.6D),
                                        Behaviour.NONE, TargetMode.SELF_AXIS)
                                .build(),
                        of("补刀", RAN_PALETTE.at(0))
                                .damageScale(1.1D)
                                .identity(0, 2, 1)
                                .repeatEvery(45)
                                .at(0, Shape.FAN, Shape.Params.defaults()
                                                .count(4).spread(24.0D).speed(0.40D).size(0.45D),
                                        Behaviour.NONE, TargetMode.AIMED)
                                .build()), RAN_C4),
                // 5 · 式神「十二神将の宴」—— 分批的地柱（脚下/地面）+ 齐射。
                new SpellCard(card("yakumo_ran", 5), 0.500D, List.of(
                        pillars(),
                        of("齐射", RAN_PALETTE.at(2))
                                .identity(1, 0, 0)
                                .repeatEvery(50)
                                .at(0, Shape.FAN, Shape.Params.defaults()
                                                .count(5).spread(30.0D).speed(0.34D).size(0.5D),
                                        Behaviour.NONE, TargetMode.AIMED)
                                .build()), RAN_C5),
                // 6 · 式弾「ユーニラタルコンタクト」—— 单侧压来的重扇，留反向通道。
                new SpellCard(card("yakumo_ran", 6), 0.375D, List.of(
                        of("片面接触", RAN_PALETTE.at(0))
                                .identity(0, 0, 0)
                                .repeatEvery(70)
                                .phaseStep(30.0D)
                                .at(0, Shape.FAN, Shape.Params.defaults()
                                                .count(7).spread(90.0D).speed(0.30D).size(0.6D),
                                        Behaviour.NONE, TargetMode.SELF_AXIS)
                                .build(),
                        of("逆流", RAN_PALETTE.at(2))
                                .identity(1, 1, 1)
                                .repeatEvery(70)
                                .phaseStep(210.0D)
                                .at(0, Shape.FAN, Shape.Params.defaults()
                                                .count(7).spread(90.0D).speed(0.30D).size(0.6D),
                                        Behaviour.NONE, TargetMode.SELF_AXIS)
                                .build()), RAN_C6),
                // 7 · 地妖符「式神大星」—— 十字魔方阵 + 逐颗点亮的盘环。
                new SpellCard(card("yakumo_ran", 7), 0.250D, List.of(
                        of("十字", RAN_PALETTE.at(1))
                                .identity(0, 0, 0)
                                .repeatEvery(60)
                                .phaseStep(22.5D)
                                .at(0, Shape.GRID_FACING, Shape.Params.defaults()
                                                .count(9).spread(12.0D).speed(0.28D).size(0.5D),
                                        Behaviour.NONE, TargetMode.SELF_AXIS)
                                .build(),
                        of("大星", RAN_PALETTE.at(5))
                                .identity(2, 1, 1)
                                .repeatEvery(80)
                                .phaseStep(9.0D)
                                .at(0, Shape.DISC_RING, Shape.Params.defaults()
                                                .count(6).speed(0.26D).size(0.6D),
                                        Behaviour.NONE, TargetMode.SELF_AXIS)
                                .build()), RAN_C7),
                // 8 · 幻神「飯綱権現降臨」—— 狐神降临：旋转激光网 + 狐火光晕。终局，最高倍率。
                new SpellCard(card("yakumo_ran", 8), 0.125D, List.of(
                        of("權現網", RAN_PALETTE.at(5))
                                .damageScale(1.4D)
                                .identity(2, 0, 0)
                                .repeatEvery(100)
                                .laserAt(0, Shape.LATTICE, Shape.Params.defaults()
                                                .count(10).radius(10.0D).spread(80.0D)
                                                .aimBias(0.3D).size(0.5D),
                                        Projectile.laser(70.0D, 0.4D, 1.0D, 1.6D, false,
                                                Projectile.LaserMotion.spin(
                                                        new Vec3(0.0D, 1.0D, 0.0D), 1.0D)),
                                        TargetMode.AIMED)
                                .build(),
                        of("狐火光晕", RAN_PALETTE.at(1))
                                .damageScale(1.4D)
                                .identity(1, 1, 1)
                                .repeatEvery(60)
                                .at(0, Shape.AROUND_TARGET, Shape.Params.defaults()
                                                .count(8).radius(12.0D).spread(25.0D)
                                                .speed(0.30D).size(0.5D),
                                        Behaviour.NONE, TargetMode.AIMED)
                                .build()), RAN_C8));
    }

    /** 卡 5「十二神将の宴」的地柱轨：8 根分批落点，每第 4 根压在玩家脚下。 */
    private static Track pillars() {
        Track.Builder b = of("神将柱", RAN_PALETTE.at(4))
                .identity(2, 2, 1)
                .repeatEvery(0);
        for (int i = 0; i < 8; i++) {
            b.at(i * 25, Shape.PILLAR_UP, Shape.Params.defaults()
                            .count(1).radius(18.0D).size(0.6D).speed(0.0D),
                    Behaviour.NONE, TargetMode.SELF_AXIS,
                    Projectile.laser(50.0D, 0.6D, 1.0D, 1.6D), 0, 0,
                    (i % 4 == 3) ? Track.Beat.SpawnAnchor.PLAYER_GROUND
                            : Track.Beat.SpawnAnchor.GROUND_BELOW,
                    1.0D, 1.0D);
        }
        return b.build();
    }

    /** 旧后端的在役符卡表（lint / 测试遍历用）；露米娅已走程序内容，不在此表。 */
    public static List<SpellCard> all() {
        return java.util.stream.Stream.of(bigFairy(), yamame(), yakumoRan())
                .flatMap(List::stream).toList();
    }
}
