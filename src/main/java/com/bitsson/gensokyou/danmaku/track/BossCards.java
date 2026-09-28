package com.bitsson.gensokyou.danmaku.track;

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
 * <p>「缺段」是对轨道构成的硬约束，由 {@link TrackLint} 的
 * {@link TrackLint#hasNoAimedTrack} / {@link TrackLint#allTracksEndless} 断言：
 * <ul>
 *   <li>鬼蛛缺「破」——整副表 MUST NOT 含任何瞄准型节拍（它不主动攻击）。
 *   <li>傩神楽面缺「結」——全部轨道 MUST 无终止条件。
 * </ul>
 */
public final class BossCards {

    private BossCards() {
    }

    // ==================================================================
    // 大妖精 —— 教学档。无符名、无缺陷。
    // 全部大、全慢、全朝前：玩家第一眼要读到的是「大而慢 = 可读 = 可以靠近」。
    // 刻意<b>不发环</b>：二维里环是万能母题，三维里环等于半盲。
    // ==================================================================

    public static final SignaturePalette BIG_FAIRY_PALETTE =
            SignaturePalette.of(0x9BE7FF, 0xFFD9F0, 0xB8FFC9, 0xFFE9A8, 0xC9C4FF);

    public static List<SpellCard> bigFairy() {
        return List.of(
                new SpellCard("散華", 1.00D, List.of(
                        of("散華扇", BIG_FAIRY_PALETTE.at(0))
                                .identity(0, 0, 0)
                                .repeatEvery(50)
                                .phaseStep(4)
                                .at(0, Shape.FAN, Shape.Params.defaults()
                                        .count(5).spread(60.0D).speed(0.30D).size(0.95D),
                                        Behaviour.NONE, TargetMode.SELF_AXIS)
                                .build())),
                new SpellCard("旋風", 0.66D, List.of(
                        of("旋風環", BIG_FAIRY_PALETTE.at(1))
                                .damageScale(1.0D)
                                .identity(1, 1, 0)
                                .repeatEvery(45)
                                .phaseStep(14)
                                .at(0, Shape.RING_FACING, Shape.Params.defaults()
                                        .count(12).gap(70.0D).speed(0.28D).size(0.85D),
                                        Behaviour.NONE, TargetMode.SELF_AXIS)
                                .build())),
                new SpellCard("落華", 0.33D, List.of(
                        of("落下", BIG_FAIRY_PALETTE.at(2))
                                .damageScale(1.25D)
                                .identity(2, 0, 1)
                                .repeatEvery(70)
                                .at(0, Shape.FALL_FROM_ABOVE, Shape.Params.defaults()
                                        .count(9).radius(7.0D).speed(0.22D).size(1.05D),
                                        Behaviour.NONE, TargetMode.SELF_AXIS)
                                .build())));
    }

    // ==================================================================
    // 鬼蛛「堅牢」 —— 缺「破」。它不主动攻击，只会封锁。
    // 玩家必须主动破局：拆掉定幕的静止弹幕、躲开潜溜、或者从交差的空格里穿过去。
    // ==================================================================

    public static final SignaturePalette KUZUMONO_PALETTE =
            SignaturePalette.of(0x8C6BD8, 0x4CE0B0, 0xD84C8C, 0xF0E24C);

    public static List<SpellCard> kuzumono() {
        return List.of(
                // 経糸：地滑帯 ‖ 定幕。两轨都是封锁，不打你。
                new SpellCard("経糸", 1.00D, List.of(
                        of("地滑帯", KUZUMONO_PALETTE.at(0))
                                .identity(0, 0, 0)
                                .repeatEvery(60)
                                .at(0, Shape.SHELL, Shape.Params.defaults()
                                                .count(14).radius(4.0D, 0.08D).rise(0.15D)
                                                .speed(0.34D).size(0.7D),
                                        Behaviour.NONE.withMotion(Behaviour.Motion.groundHug()),
                                        TargetMode.ARENA)
                                .build(),
                        of("定幕", KUZUMONO_PALETTE.at(1))
                                .damageScale(1.0D)
                                .identity(1, 1, 0)
                                .repeatEvery(90)
                                .phaseStep(20)
                                .at(0, Shape.RADIAL_BURST, Shape.Params.defaults()
                                        .count(8).speed(0.26D).size(0.6D),
                                        Behaviour.NONE.withMotion(Behaviour.Motion.hover(26)),
                                        TargetMode.SELF_AXIS)
                                .build())),
                // 結界：包囲 ‖ 潜溜。包囲会闭合，逼迫玩家在合拢前找到出口。
                new SpellCard("結界", 0.66D, List.of(
                        of("包囲", KUZUMONO_PALETTE.at(2))
                                .damageScale(1.15D)
                                .identity(2, 2, 0)
                                .repeatEvery(50)
                                .at(0, Shape.SHELL, Shape.Params.defaults()
                                                .count(16).radius(11.0D, -0.06D).rise(1.0D)
                                                .speed(0.24D).size(0.75D),
                                        Behaviour.NONE, TargetMode.SELF_AXIS)
                                .build(),
                        of("潜溜", KUZUMONO_PALETTE.at(3))
                                .damageScale(1.15D)
                                .identity(3, 0, 1)
                                .repeatEvery(120)
                                .phaseStep(30)
                                .at(0, Shape.SCATTER_STATIC, Shape.Params.defaults()
                                                .count(6).radius(5.0D).speed(0.0D).size(0.55D),
                                        Behaviour.NONE.withMotion(Behaviour.Motion.mine(1.6D)),
                                        TargetMode.ARENA)
                                .build())),
                // 硬化：三正交面交差 ‖ 从缺口方位补发的分裂弹。必须自己找空格。
                new SpellCard("硬化", 0.33D, List.of(
                        of("交差", KUZUMONO_PALETTE.at(0))
                                .damageScale(1.3D)
                                .identity(0, 1, 1)
                                .repeatEvery(70)
                                .phaseStep(11)
                                .at(0, Shape.CAGE, Shape.Params.defaults()
                                        .count(8).gap(45.0D).speed(0.30D).size(0.65D),
                                        Behaviour.NONE, TargetMode.SELF_AXIS)
                                .build(),
                        of("補発", KUZUMONO_PALETTE.at(1))
                                .damageScale(1.3D)
                                .identity(1, 2, 1)
                                .repeatEvery(140)
                                .at(0, Shape.GAP_FAN, Shape.Params.defaults()
                                                .count(3).gap(45.0D).speed(0.28D).size(0.55D),
                                        Behaviour.NONE.withSplit(Behaviour.Split.at(18, 4)),
                                        TargetMode.SELF_AXIS)
                                .build())));
    }

    // ==================================================================
    // 狐火「無序」 —— 缺「序」。没有预备拍，起手即峰值。
    //
    // 关键约束：缺「序」MUST NOT 变成「在你背后凭空刷弹」。读不出是因为没时间，
    // 不是因为看不见，故全部轨道仍锁在玩家朝向的包络里。
    // ==================================================================

    public static final SignaturePalette KITSUNEBI_PALETTE =
            SignaturePalette.of(0x66E8FF, 0xFF7A3C, 0xC6FF5E, 0xFF4FA0, 0x9C6BFF);

    public static List<SpellCard> kitsuneBi() {
        return List.of(
                // 乱焔：绕竖轴与绕横轴两路反向曲射，交叉处密度峰值。
                // 注意几何是同一个 RING——两轨的差别全在曲射轴与角速度，属行为层。
                new SpellCard("乱焔", 1.00D, List.of(
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
                new SpellCard("泡", 0.66D, List.of(
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
                new SpellCard("散", 0.33D, List.of(
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
    // 傩神楽面「無終」 —— 缺「結」。节拍无终止条件，到时不收束。
    // 五张符卡全是「同一招的变奏」：連射、乱連、急連、互不同步。
    // ==================================================================

    public static final SignaturePalette NOMEN_PALETTE =
            SignaturePalette.of(0xF2D9A0, 0xD0433C, 0x2A2E3C, 0xE8E0C8, 0x7FA0C8);

    public static List<SpellCard> nomenMask() {
        return List.of(
                new SpellCard("拍", 1.00D, List.of(
                        of("無終連", NOMEN_PALETTE.at(0))
                                .endless()
                                .identity(0, 0, 0)
                                .repeatEvery(40)
                                .phaseStep(9)
                                .at(0, Shape.RING_FACING, Shape.Params.defaults()
                                        .count(10).gap(60.0D).speed(0.30D).size(0.7D),
                                        Behaviour.NONE, TargetMode.SELF_AXIS)
                                .build())),
                new SpellCard("連拍", 0.80D, List.of(
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
                new SpellCard("乱拍", 0.60D, List.of(
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
                new SpellCard("急拍", 0.40D, List.of(
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
                new SpellCard("無終", 0.20D, List.of(
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
        return java.util.stream.Stream.of(bigFairy(), kuzumono(), kitsuneBi(), nomenMask())
                .flatMap(List::stream).toList();
    }
}
