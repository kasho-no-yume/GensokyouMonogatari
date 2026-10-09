package com.bitsson.gensokyou.danmaku.track;

import com.bitsson.gensokyou.danmaku.motion.DanmakuSpeedProfile;
import net.minecraft.network.chat.Component;

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
     * 阶段 2 单朵花的寿命：<b>MUST 不小于 {@link #FLOWER_CARD_CYCLE}</b>。
     *
     * <p>原为 {@code 2 * FLOWER_BURST_AGE = 200}，比循环 240 短 40 tick。这个差值就是
     * 「画面陆续减少、逐渐变空」的<b>全部</b>原因，而且它与发射密度无关：在场花朵数
     * {@code = 每周期朵数 × 寿命 / 周期}，{@link #FLOWER_STEP} 改多少都动不了它。
     *
     * <p>寿命 200 / 周期 240 时，每轮第 201~261 tick 没有任何新发射、而上一批正在陆续到期，
     * 画面从 736 枚排空到 276 枚（峰值的 37%），然后再涨回来。取 240 后，
     * 下一轮的第 0 朵恰好在上一轮第 0 朵到期的那一 tick 补上，在场数稳定在 16~17 朵。
     *
     * <p><b>为什么不能用「把间隔调稀」来代替</b>——那样会把同时开花的朵数从 16 压到 4：
     * {@code TrackLint} 的密度按 50 tick 危险窗口的重叠数计，峰值会从 736 掉到 184。
     * 补间隔治的是症状，掉的是这张卡本来就该有的压迫感。
     *
     * <p>本文件对「循环留余量」已有既定态度（见 {@link #RAIN_CARD_CYCLE}：循环 MUST 等于
     * 内容跨度，余量就是「放一段然后哑掉」）。花卡的 80 tick 余量本该一并去掉，但去掉会把
     * 12 秒压成 10 秒——那是节奏选择，不是 bug，所以留在这里，只把寿命补到与之匹配。
     *
     * <p>代价：爆散后的滑行余量由 100 tick 变成 140 tick（花瓣滑行 20 格 → 28 格）。
     * {@code harmlessTicks} 仍是 100，即爬升与悬停全程无害这一点没有被改动。
     */
    private static final int FLOWER_LIFETIME = FLOWER_CARD_CYCLE;
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
     * <p>寿命 10 秒（5 秒成形 + 5 秒散开）。整朵共享一条编队帧作参考点，
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
    // 母题来自原作地霊殿 1 面：罠符「キャプチャーウェブ」（网）与
    // 瘴符「フィルドミアズマ」（瘴气）。
    //
    // **红线：每一发都得是朝着玩家去的。** 只用「朝着玩家/绕着玩家/压在玩家脚下」
    // 的几何——AIMED 瞄准扇、AROUND_TARGET 绕玩家收拢、LATTICE 绕玩家的激光网、
    // DISC_RING 悬停后朝玩家压来的墙、PLAYER_GROUND 从玩家脚下炸起的光柱。
    // 绝不用往 BOSS 侧边放的 RING / RADIAL_BURST / SHELL / CAGE——那打不到人、
    // 也不逼走位，是纯粹的花瓶。
    //
    // 颜色：at(0) 金（蛛丝）／at(1) 陶土（地蜘蛛）／at(2) 赤・at(3) 紫（奇偶波的瘴）
    //      ／at(4) 病黄绿（毒/热病）。容量 6 ≥ 最大并发 3。
    // ==================================================================

    public static final SignaturePalette YAMAME_PALETTE =
            SignaturePalette.of(0xE0A24B, 0xD9683A, 0xC8383E, 0x8E5FD8, 0xB8D24A, 0x5FBF7A);

    /** 各卡循环长度（tick）。显式时间轴靠它重放，符卡切换也挂起到它的边界。 */
    private static final int YAMAME_WEB_CYCLE = 160;
    private static final int YAMAME_MIASMA_CYCLE = 240;
    private static final int YAMAME_NEST_CYCLE = 220;
    private static final int YAMAME_FEVER_CYCLE = 240;

    public static List<SpellCard> yamame() {
        return List.of(webCard(), miasmaCard(), nestCard(), feverCard());
    }

    /**
     * 1 阶段 · 罠符「キャプチャーウェブ」（100%~75%）
     *
     * <p>意念：蛛丝朝着你收拢。一道瞄准扇直取当前站位；同时一圈蛛丝绕着你生成、
     * 从四面八方朝你合拢——站在原点必死，必须踩着缝走位。
     */
    private static SpellCard webCard() {
        Track.Builder net = of("張網", YAMAME_PALETTE.at(0))
                .identity(0, 0, 0)
                .repeatEvery(20)
                .phaseStep(9.0D);
        net.at(0, Shape.AROUND_TARGET, Shape.Params.defaults()
                        .count(18).radius(7.0D).spread(14.0D).speed(0.30D).size(0.6D),
                Behaviour.NONE, TargetMode.AIMED);

        Track.Builder thread = of("追い糸", YAMAME_PALETTE.at(1))
                .identity(1, 1, 1)
                .repeatEvery(20);
        thread.at(0, Shape.FAN, Shape.Params.defaults()
                        .count(7).spread(22.0D).speed(0.45D).size(0.55D),
                Behaviour.NONE, TargetMode.AIMED);

        return new SpellCard(card("yamame", 1), 1.00D,
                List.of(net.build(), thread.build()), YAMAME_WEB_CYCLE);
    }

    /**
     * 2 阶段 · 瘴符「フィルドミアズマ」（75%~50%）
     *
     * <p>意念：瘴气充满你所在的洞窟。绕着你自转的瘴气涡旋逐拍推进、不断收拢；
     * 同时一圈**凌乱激光网**在你四周亮起——有的直指你（不动就中），有的只封路，
     * 必须在一堆交叉线里找活口。
     */
    private static SpellCard miasmaCard() {
        Track.Builder vortex = of("瘴気の渦", YAMAME_PALETTE.at(2))
                .identity(0, 0, 0)
                .repeatEvery(16)
                .phaseStep(12.0D);
        vortex.at(0, Shape.AROUND_TARGET, Shape.Params.defaults()
                        .count(20).radius(7.0D).spread(16.0D).speed(0.32D).size(0.6D),
                Behaviour.NONE, TargetMode.AIMED);

        Track.Builder web = of("瘴気の網", YAMAME_PALETTE.at(4))
                .identity(1, 1, 0)
                .repeatEvery(46);
        web.at(0, Shape.LATTICE, Shape.Params.defaults()
                        .count(14).radius(9.0D).spread(40.0D).aimBias(0.4D).speed(0.0D),
                Behaviour.NONE, TargetMode.AIMED, Projectile.laser(36.0D, 0.3D, 1.4D, 2.4D));

        return new SpellCard(card("yamame", 2), 0.75D,
                List.of(vortex.build(), web.build()), YAMAME_MIASMA_CYCLE);
    }

    /**
     * 3 阶段 · 蜘蛛「石窟の蜘蛛の巣」（50%~25%）
     *
     * <p>意念：蜘蛛收网。她在身后立起一面蛛丝墙、悬停两秒后整面朝你压来；
     * 你的脚底炸起垂丝光柱；同时一圈络丝持续绕你收拢。三种威胁叠在一起，
     * 逼着你不停换位。
     */
    private static SpellCard nestCard() {
        Track.Builder wall = of("結界網", YAMAME_PALETTE.at(1))
                .identity(0, 0, 0)
                .repeatEvery(50);
        wall.at(0, Shape.DISC_RING, Shape.Params.defaults()
                        .count(30).radius(5.0D).offsetForward(-8.0D).speed(0.4D).size(0.65D),
                Behaviour.NONE.withMotion(Behaviour.Motion.reclaim(
                        DanmakuSpeedProfile.decelerateAndHold(0.4D, 1.0D),
                        46, 0.4D, Behaviour.TARGET_AUTO)),
                TargetMode.AIMED);

        Track.Builder pillar = of("垂れ糸", YAMAME_PALETTE.at(0))
                .identity(1, 1, 0)
                .repeatEvery(44);
        pillar.at(0, Shape.PILLAR_UP, Shape.Params.defaults()
                        .count(2).radius(2.0D).speed(0.0D),
                Behaviour.NONE, TargetMode.SELF_AXIS, Projectile.laser(44.0D, 0.35D, 1.6D, 3.0D),
                0, 0, Track.Beat.SpawnAnchor.PLAYER_GROUND, 1.0D, 1.0D);

        Track.Builder tangle = of("絡み糸", YAMAME_PALETTE.at(3))
                .identity(2, 2, 0)
                .repeatEvery(22)
                .phaseStep(45.0D);
        tangle.at(0, Shape.AROUND_TARGET, Shape.Params.defaults()
                        .count(16).radius(6.0D).spread(16.0D).speed(0.30D).size(0.55D),
                Behaviour.NONE, TargetMode.AIMED);

        return new SpellCard(card("yamame", 3), 0.50D,
                List.of(wall.build(), pillar.build(), tangle.build()), YAMAME_NEST_CYCLE);
    }

    /**
     * 4 阶段 · 瘴気「原因不明の熱病」（25%~0%）
     *
     * <p>意念：高热之涡。更密更快的收拢涡旋、更密的激光网、脚下毒柱连发——
     * 三种威胁同时压上来。靠后的卡以 damageScale 加压，而非无限加弹数。
     */
    private static SpellCard feverCard() {
        Track.Builder heat = of("熱病", YAMAME_PALETTE.at(2))
                .damageScale(1.3D)
                .identity(0, 0, 0)
                .repeatEvery(16)
                .phaseStep(14.0D);
        heat.at(0, Shape.AROUND_TARGET, Shape.Params.defaults()
                        .count(20).radius(8.0D).spread(14.0D).speed(0.32D).size(0.55D),
                Behaviour.NONE, TargetMode.AIMED);

        Track.Builder web = of("瘴気の網", YAMAME_PALETTE.at(3))
                .damageScale(1.3D)
                .identity(1, 1, 0)
                .repeatEvery(38);
        web.at(0, Shape.LATTICE, Shape.Params.defaults()
                        .count(18).radius(10.0D).spread(45.0D).aimBias(0.45D).speed(0.0D),
                Behaviour.NONE, TargetMode.AIMED, Projectile.laser(40.0D, 0.32D, 1.2D, 2.4D));

        Track.Builder rain = of("毒雨", YAMAME_PALETTE.at(4))
                .damageScale(1.35D)
                .identity(2, 2, 1)
                .repeatEvery(30);
        rain.at(0, Shape.PILLAR_UP, Shape.Params.defaults()
                        .count(3).radius(3.0D).speed(0.0D),
                Behaviour.NONE, TargetMode.SELF_AXIS, Projectile.laser(40.0D, 0.4D, 1.0D, 2.6D),
                0, 0, Track.Beat.SpawnAnchor.PLAYER_GROUND, 1.0D, 1.0D);

        return new SpellCard(card("yamame", 4), 0.25D,
                List.of(heat.build(), web.build(), rain.build()), YAMAME_FEVER_CYCLE);
    }

    // ==================================================================
    // 狐火「無序」 —— 主题：没有预备拍，起手即峰值。
    //
    // 可读性：起手即峰值 MUST NOT 变成「在你背后凭空刷弹」。读不出是因为没时间，
    // 不是因为看不见，故全部轨道仍锁在玩家朝向的包络里。
    // ==================================================================

    public static final SignaturePalette KITSUNEBI_PALETTE =
            SignaturePalette.of(0x66E8FF, 0xFF7A3C, 0xC6FF5E, 0xFF4FA0, 0x9C6BFF);

    public static List<SpellCard> kitsuneBi() {
        return List.of(
                // 乱焔：绕竖轴与绕横轴两路反向曲射，交叉处密度峰值。
                // 注意几何是同一个 RING——两轨的差别全在曲射轴与角速度，属行为层。
                new SpellCard(card("kitsunebi", 1), 1.00D, List.of(
                        of("縦曲", KITSUNEBI_PALETTE.at(0))
                                .identity(0, 0, 0)
                                .repeatEvery(40)
                                .at(0, Shape.RING, Shape.Params.defaults()
                                                .count(10).speed(0.26D).size(0.6D),
                                        Behaviour.NONE.withMotion(Behaviour.Motion.curve(0.0D, 90.0D, 95.0D)),
                                        TargetMode.SELF_AXIS)
                                .build(),
                        of("横曲", KITSUNEBI_PALETTE.at(1))
                                .identity(1, 1, 0)
                                .repeatEvery(40)
                                .at(0, Shape.RING, Shape.Params.defaults()
                                                .count(10).speed(0.26D).size(0.6D),
                                        Behaviour.NONE.withMotion(Behaviour.Motion.curve(90.0D, 0.0D, -80.0D)),
                                        TargetMode.SELF_AXIS)
                                .build())),
                // 泡：悬停泡缓慢逼近 ‖ 泡破裂时的分裂环。没有预备拍 = 逼近即峰值。
                new SpellCard(card("kitsunebi", 2), 0.66D, List.of(
                        of("浮泡", KITSUNEBI_PALETTE.at(2))
                                .identity(2, 2, 0)
                                .repeatEvery(55)
                                .at(0, Shape.RADIAL_BURST, Shape.Params.defaults()
                                                .count(9).speed(0.20D).size(0.8D),
                                        Behaviour.NONE.withMotion(Behaviour.Motion.hover(34)),
                                        TargetMode.SELF_AXIS)
                                .build(),
                        of("破裂環", KITSUNEBI_PALETTE.at(3))
                                .damageScale(1.1D)
                                .identity(3, 0, 1)
                                .repeatEvery(110)
                                .at(0, Shape.RING_FACING, Shape.Params.defaults()
                                                .count(10).gap(60.0D).speed(0.22D).size(0.6D),
                                        Behaviour.NONE.withSplit(Behaviour.Split.at(24, 3)),
                                        TargetMode.SELF_AXIS)
                                .build())),
                // 散：随机，但锁在一个随 BOSS 旋转的锥里。三轨并发。
                new SpellCard(card("kitsunebi", 3), 0.33D, List.of(
                        of("乱焔散", KITSUNEBI_PALETTE.at(4))
                                .identity(0, 0, 0)
                                .repeatEvery(30)
                                .at(0, Shape.CONE_RANDOM, Shape.Params.defaults()
                                                .count(4).spread(110.0D).speed(0.30D).size(0.55D),
                                        Behaviour.NONE, TargetMode.SELF_AXIS)
                                .build(),
                        of("泡散", KITSUNEBI_PALETTE.at(2))
                                .damageScale(1.2D)
                                .identity(1, 1, 0)
                                .repeatEvery(75)
                                .at(0, Shape.CONE_RANDOM, Shape.Params.defaults()
                                                .count(3).spread(70.0D).speed(0.18D).size(0.9D),
                                        Behaviour.NONE.withMotion(Behaviour.Motion.hover(30)),
                                        TargetMode.SELF_AXIS)
                                .build(),
                        of("斜散", KITSUNEBI_PALETTE.at(0))
                                .damageScale(1.35D)
                                .identity(2, 2, 1)
                                .repeatEvery(45)
                                .at(0, Shape.AXIAL_STAR, Shape.Params.defaults()
                                                .count(1).speed(0.24D).size(0.6D),
                                        Behaviour.NONE, TargetMode.SELF_AXIS)
                                .build())));
    }

    // ==================================================================
    // 傩神楽面「無終」 —— 主题：节拍无终止条件，到时不收束。
    // 五张符卡全是「同一招的变奏」：連射、乱連、急連、互不同步。
    // ==================================================================

    public static final SignaturePalette NOMEN_PALETTE =
            SignaturePalette.of(0xF2D9A0, 0xD0433C, 0x2A2E3C, 0xE8E0C8, 0x7FA0C8);

    public static List<SpellCard> nomenMask() {
        return List.of(
                new SpellCard(card("nomen_mask", 1), 1.00D, List.of(
                        of("無終連", NOMEN_PALETTE.at(0))
                                .endless()
                                .identity(0, 0, 0)
                                .repeatEvery(40)
                                .phaseStep(9)
                                .at(0, Shape.RING_FACING, Shape.Params.defaults()
                                        .count(10).gap(60.0D).speed(0.30D).size(0.7D),
                                        Behaviour.NONE, TargetMode.SELF_AXIS)
                                .build())),
                new SpellCard(card("nomen_mask", 2), 0.80D, List.of(
                        of("連射", NOMEN_PALETTE.at(0))
                                .damageScale(1.15D)
                                .endless()
                                .identity(0, 1, 0)
                                .repeatEvery(24)
                                .phaseStep(13)
                                .at(0, Shape.RING_FACING, Shape.Params.defaults()
                                        .count(10).gap(60.0D).speed(0.30D).size(0.7D),
                                        Behaviour.NONE, TargetMode.SELF_AXIS)
                                .build(),
                        of("地連", NOMEN_PALETTE.at(1))
                                .damageScale(1.15D)
                                .endless()
                                .identity(1, 0, 1)
                                .repeatEvery(60)
                                .at(0, Shape.SHELL, Shape.Params.defaults()
                                                .count(12).radius(5.0D, 0.06D).rise(0.15D)
                                                .speed(0.32D).size(0.6D),
                                        Behaviour.NONE.withMotion(Behaviour.Motion.groundHug()),
                                        TargetMode.ARENA)
                                .build())),
                new SpellCard(card("nomen_mask", 3), 0.60D, List.of(
                        of("乱連", NOMEN_PALETTE.at(0))
                                .damageScale(1.3D)
                                .endless()
                                .identity(0, 0, 0)
                                .repeatEvery(24)
                                .phaseStep(-13)
                                .at(0, Shape.RING_FACING, Shape.Params.defaults()
                                        .count(10).gap(60.0D).speed(0.30D).size(0.7D),
                                        Behaviour.NONE, TargetMode.SELF_AXIS)
                                .build(),
                        of("天蓋", NOMEN_PALETTE.at(2))
                                .damageScale(1.3D)
                                .endless()
                                .identity(1, 1, 1)
                                .repeatEvery(48)
                                .at(0, Shape.FALL_FROM_ABOVE, Shape.Params.defaults()
                                        .count(9).radius(7.0D).speed(0.26D).size(0.7D),
                                        Behaviour.NONE, TargetMode.SELF_AXIS)
                                .build())),
                new SpellCard(card("nomen_mask", 4), 0.40D, List.of(
                        of("急連", NOMEN_PALETTE.at(0))
                                .damageScale(1.45D)
                                .endless()
                                .identity(0, 2, 0)
                                .repeatEvery(12)
                                .phaseStep(18)
                                .at(0, Shape.RING_FACING, Shape.Params.defaults()
                                        .count(10).gap(54.0D).speed(0.34D).size(0.6D),
                                        Behaviour.NONE, TargetMode.SELF_AXIS)
                                .build(),
                        of("地溜", NOMEN_PALETTE.at(4))
                                .damageScale(1.45D)
                                .endless()
                                .identity(1, 0, 1)
                                .repeatEvery(36)
                                .at(0, Shape.SCATTER_STATIC, Shape.Params.defaults()
                                                .count(5).radius(4.5D).speed(0.0D).size(0.55D),
                                        Behaviour.NONE.withMotion(Behaviour.Motion.mine(1.4D)),
                                        TargetMode.ARENA)
                                .build())),
                // 无終：三轨同时重复但节拍互不同步——玩家找不到共同节奏。
                new SpellCard(card("nomen_mask", 5), 0.20D, List.of(
                        of("終連甲", NOMEN_PALETTE.at(0))
                                .damageScale(1.6D)
                                .endless()
                                .identity(0, 0, 0)
                                .repeatEvery(18)
                                .phaseStep(16)
                                .at(0, Shape.RING_FACING, Shape.Params.defaults()
                                        .count(10).gap(54.0D).speed(0.34D).size(0.6D),
                                        Behaviour.NONE, TargetMode.SELF_AXIS)
                                .build(),
                        of("終連乙", NOMEN_PALETTE.at(1))
                                .damageScale(1.6D)
                                .endless()
                                .identity(1, 1, 0)
                                .repeatEvery(27)
                                .phaseStep(-16)
                                .at(0, Shape.CAGE, Shape.Params.defaults()
                                        .count(7).gap(50.0D).speed(0.30D).size(0.6D),
                                        Behaviour.NONE, TargetMode.SELF_AXIS)
                                .build(),
                        of("定幕", NOMEN_PALETTE.at(4))
                                .damageScale(1.6D)
                                .endless()
                                .identity(2, 2, 1)
                                .repeatEvery(41)
                                .at(0, Shape.RADIAL_BURST, Shape.Params.defaults()
                                                .count(7).speed(0.28D).size(0.55D),
                                        Behaviour.NONE.withMotion(Behaviour.Motion.hover(22)),
                                        TargetMode.SELF_AXIS)
                                .build())));
    }

    /** 全部 BOSS 的符卡表（lint / 测试遍历用）。 */
    public static List<SpellCard> all() {
        return java.util.stream.Stream.of(bigFairy(), yamame(), kitsuneBi(), nomenMask())
                .flatMap(List::stream).toList();
    }
}
