package com.bitsson.gensokyou.danmaku.program.content;

import com.bitsson.gensokyou.danmaku.program.TailPolicy;
import com.bitsson.gensokyou.danmaku.program.TimeDomain;
import com.bitsson.gensokyou.danmaku.program.author.CardProgram;
import com.bitsson.gensokyou.danmaku.program.author.MemberProgram;
import com.bitsson.gensokyou.danmaku.program.math.Vec3;
import com.bitsson.gensokyou.danmaku.program.ops.Paths;
import com.bitsson.gensokyou.danmaku.program.ops.Patterns;

import java.util.List;

/**
 * 露米娅正式程序内容。
 *
 * <p>五张卡对应她在原作里的全部符卡：红魔乡的月符 / 夜符 / 暗符，加上文花帖登场的
 * 暗符「月之暗面」与夜符「子夜之鸟」。血量阈值 1.00 / 0.80 / 0.60 / 0.40 / 0.20。
 *
 * <p>设计语法（见 docs 与本轮考据）：她的压迫从来不是弹量，而是
 * <b>「信息与节奏」</b>——走廊约束、交替扇动、交错缺口、半透明幽灵弹。
 * 黑暗在家用机上不能真的把屏幕变黑（违反 R1），所以改为
 * 「可见的暗体积 + 半透明周期」。
 *
 * <p>每一拍都朝玩家所在的朝向帧发射（{@code aimed} + {@code fixedDirection} 的墙，
 * 或 {@code templateDirections} 的锥/扇），不做垂直于 BOSS→玩家连线的横向环。
 */
public final class RumiaPrograms {
    private RumiaPrograms() {}

    /** 夜鸟两片翅膀的环半径：足够宽，玩家能从中间的缺口穿过。 */
    private static final double WING_RADIUS = 5.2D;
    /** 卡 1 激光走廊的半宽；两束平行激光中间留出 6.4 格。 */
    private static final double BEAM_OFFSET = 3.2D;

    private static BossProgramCards.Card card(int index, String nameKey, double hp, int cycle,
                                              CardProgram.Compiled program, List<String> notes) {
        return new BossProgramCards.Card(index, nameKey, hp, cycle, 0, program, notes);
    }

    // ------------------------------------------------------------------
    // 1 · 月符「月光射线」：两道激光夹出走廊 + 正面放射
    // ------------------------------------------------------------------
    private static BossProgramCards.Card moonlightRay() {
        var card = new CardProgram("gensokyou:rumia_moonlight_ray");
        var targets = card.targets("players", 64, 5);
        var frame = card.aimed("moonFrame", targets, 0, new Vec3(0, 1, 0));

        var beam = MemberProgram.attack("moonBeam",
                        MemberProgram.styled(new Patterns.Union(List.of(
                                        new Patterns.Point(new Vec3(-BEAM_OFFSET, 1, 0), Vec3.Z),
                                        new Patterns.Point(new Vec3(BEAM_OFFSET, 1, 0), Vec3.Z))),
                                "laser", "#BFD8FF", 0.22, 1, 0.22, "beam"),
                        new Paths.Hold(), 18)
                .fixedDirection().laser(70, false, 10).build();

        var sunburst = MemberProgram.attack("sunburst",
                        MemberProgram.styled(new Patterns.ConeScatter(14, Math.toRadians(55), 1),
                                "sphere", "#FFE9A8", 0.2, 1, 0.4, "ray"),
                        new Paths.Linear(Vec3.Z, 0.22), 90)
                .templateDirections().build();

        var aimed = MemberProgram.attack("aimedRay",
                        MemberProgram.styled(new Patterns.Fan(3, Math.toRadians(20), 0),
                                "sphere", "#6E8FB8", 0.18, 1, 0.36, "aim"),
                        new Paths.Linear(Vec3.Z, 0.30), 70)
                .templateDirections().build();

        for (int tick : new int[]{0, 100}) {
            // 两束激光必须是「同一方向 + 侧向偏移」的平行光，才会夹出走廊；
            // 用 offset/独立 aimed 组会让两束各自指向玩家、在玩家身上交叉。
            card.at(tick, c -> c.fire(frame, beam, 25, 401 + tick));
        }
        for (int beat = 0; beat < 5; beat++) {
            int tick = 15 + beat * 40;
            card.at(tick, c -> c.fire(frame, sunburst, 15, 211 + tick));
        }
        for (int tick : new int[]{35, 75, 155}) {
            card.at(tick, c -> {
                var gun = c.aimed("aimFrame" + tick, targets, 0, new Vec3(0, 1, 0));
                c.fire(gun, aimed, 15, 231 + tick);
            });
        }
        return card(1, "spellcard.gensokyou.rumia.1", 1.00D, 200,
                card.loop(200, TailPolicy.RETAIN_UNTIL_LIFETIME),
                List.of("激光径 70、半径 .22，左右各偏 3.2 格 ⇒ 中央净宽 6.4；预警 25 tick。",
                        "放射锥 55°×14 发，5 拍/轮；瞄准 3 扇 3 拍/轮，单拍最大 14 发（远低于 48）。",
                        "原作读法：激光不是伤害主体，而是把她正前方的一条通道固定下来。"));
    }

    // ------------------------------------------------------------------
    // 2 · 夜符「夜鸟」：两片弹幕翅膀交替扇动 + 鸟鸣点射
    // ------------------------------------------------------------------
    private static BossProgramCards.Card nightBird() {
        var card = new CardProgram("gensokyou:rumia_night_bird");
        var targets = card.targets("players", 64, 5);
        var frame = card.aimed("birdFrame", targets, 0, new Vec3(0, 1, 0));

        var cry = MemberProgram.attack("birdCry",
                        MemberProgram.styled(new Patterns.Fan(5, Math.toRadians(26), 0),
                                "sphere", "#FFD9F0", 0.18, 1, 0.34, "cry"),
                        new Paths.Linear(Vec3.Z, 0.30), 80)
                .templateDirections().build();

        int[] beats = {0, 30, 60, 90, 120, 150};
        for (int beat = 0; beat < beats.length; beat++) {
            int tick = beats[beat];
            // 两片翅用同一段圆弧、相位相差 180°，偶数拍/奇数拍交替 → 扇动节拍。
            double phase = (beat % 2 == 0) ? 0 : Math.PI;
            var wing = MemberProgram.attack("wing" + beat,
                            MemberProgram.styled(new Patterns.Ring(9, WING_RADIUS, Math.toRadians(110), phase),
                                    "sphere", "#9C7BFF", 0.2, 1, 0.42, "wing"),
                            new Paths.Linear(Vec3.Z, 0.14), 110)
                    .fixedDirection().build();
            card.at(tick, c -> c.fire(frame, wing, 15, 500 + tick));
        }
        for (int tick : new int[]{20, 65, 110, 155}) {
            card.at(tick, c -> c.fire(frame, cry, 12, 520 + tick));
        }
        return card(2, "spellcard.gensokyou.rumia.2", 0.80D, 180,
                card.loop(180, TailPolicy.RETAIN_UNTIL_LIFETIME),
                List.of("翅 = 半径 5.2、缺口 110° 的圆弧，整组沿朝向轴推进 .14；相位 0/π 交替 ⇒ 交替扇动。",
                        "单拍 9 发；鸟鸣 5 扇 4 拍/轮。缺口即解法，节拍可数。",
                        "原作 Night Bird 是「弹幕第一号」，定位教学卡：读节拍、穿缺口。"));
    }

    // ------------------------------------------------------------------
    // 3 · 暗符「境界线」：交错缺口的编织环 + 越拖越多的瞄准波
    // ------------------------------------------------------------------
    private static BossProgramCards.Card demarcation() {
        var card = new CardProgram("gensokyou:rumia_demarcation");
        var targets = card.targets("players", 64, 5);
        // 编织环与递增瞄准波在 tick 0 同时出现，必须分属两条发射通道。
        var weaveFrame = card.aimed("weaveFrame", targets, 0, new Vec3(0, 1, 0));
        var edgeFrame = card.aimed("edgeFrame", targets, 0, new Vec3(0.8, 1, 0));
        var lineFrame = card.aimed("lineFrame", targets, 0, new Vec3(-0.8, 1, 0));

        for (int beat = 0; beat < 9; beat++) {
            int tick = beat * 28;
            double phase = beat * 0.55D;
            var weave = MemberProgram.attack("weave" + beat,
                            MemberProgram.styled(new Patterns.Ring(20, 4.2, Math.toRadians(70), phase),
                                    "sphere", "#C94B4B", 0.22, 1, 0.44, "weave"),
                            new Paths.Linear(Vec3.Z, 0.12), 100)
                    .fixedDirection().build();
            card.at(tick, c -> c.fire(weaveFrame, weave, 15, 600 + tick));
        }

        // 越拖越疼：同一轮内 3 → 5 → 7 → 9 发，瞄准扇逐段变宽。
        int[][] waves = {{0, 3}, {60, 5}, {120, 7}, {180, 9}};
        for (int[] wave : waves) {
            int count = wave[1];
            var escalation = MemberProgram.attack("edge" + wave[0],
                            MemberProgram.styled(new Patterns.Fan(count, Math.toRadians(10) * count, 0),
                                    "sphere", "#FFB27A", 0.18, 1, 0.36, "edge"),
                            new Paths.Linear(Vec3.Z, 0.26), 80)
                    .templateDirections().build();
            card.at(wave[0], c -> c.fire(edgeFrame, escalation, 14, 640 + wave[0]));
        }

        var line = MemberProgram.attack("boundary",
                        MemberProgram.styled(new Patterns.Point(Vec3.ZERO, Vec3.Z),
                                "laser", "#FFE2D0", 0.2, 1, 0.2, "line"),
                        new Paths.Hold(), 24)
                .fixedDirection().laser(60, false, 10).build();
        card.at(100, c -> c.fire(lineFrame, line, 30, 680));

        return card(3, "spellcard.gensokyou.rumia.3", 0.60D, 240,
                card.loop(240, TailPolicy.RETAIN_UNTIL_LIFETIME),
                List.of("编织环 20 发、缺口 70°，相位每拍 +0.55 rad ⇒ 缺口绕圈走，玩家要跟着缝穿。",
                        "瞄准波 3/5/7/9 按 0/60/120/180 递增：同一张卡内压力自行上升，惩罚站桩。",
                        "tick 100 一道 60 格激光作为「分界线」标点，预警 30 tick。"));
    }

    // ------------------------------------------------------------------
    // 4 · 暗符「月之暗面」：半透明幽灵弹 + 缓慢月晕
    // ------------------------------------------------------------------
    private static BossProgramCards.Card darkSideOfTheMoon() {
        var card = new CardProgram("gensokyou:rumia_dark_side");
        var targets = card.targets("players", 64, 5);
        var frame = card.aimed("darkFrame", targets, 0, new Vec3(0, 1, 0));
        // 碎月场单独一条通道：它和慢速月扫在 tick 0/130 不撞拍，但仍分渠道更清晰。
        var shardField = card.aimed("shardFrame", targets, 0, new Vec3(0, 1, 12));

        // 暗体积里的碎月：缓慢前漂 + 左右摇摆，40 tick 可见有伤 / 40 tick 半透明无碰撞。
        var shard = MemberProgram.attack("moonShard",
                        MemberProgram.styled(new Patterns.VolumeScatter(12, new Vec3(5, 4, 5), 7),
                                "star_prism", "#6B5AA0", 0.3, 1, 0.6, "shard"),
                        new Paths.WaveOffset(new Paths.Linear(Vec3.Z, 0.02), Vec3.X, 0.6, 90, 0), 200)
                .fixedDirection()
                .hiddenCycle(TimeDomain.MEMBER_AGE, 80, 40, 0)
                .build();

        var sweep = MemberProgram.attack("moonSweep",
                        MemberProgram.styled(new Patterns.Fan(3, Math.toRadians(18), 0),
                                "sphere", "#A8C4FF", 0.24, 1, 0.5, "sweep"),
                        new Paths.Linear(Vec3.Z, 0.10), 200)
                .templateDirections().build();

        var halo = MemberProgram.attack("moonHalo",
                        MemberProgram.styled(new Patterns.Ring(16, 6.5, Math.toRadians(80), 0),
                                "sphere", "#5A4A78", 0.26, 1, 0.52, "halo"),
                        new Paths.Linear(Vec3.Z, 0.09), 150)
                .fixedDirection().build();

        for (int tick : new int[]{0, 130}) {
            card.at(tick, c -> c.fire(shardField, shard, 20, 740 + tick));
        }
        for (int tick : new int[]{30, 95, 160, 225}) {
            card.at(tick, c -> c.fire(frame, sweep, 15, 760 + tick));
        }
        for (int tick : new int[]{60, 190}) {
            card.at(tick, c -> c.fire(frame, halo, 20, 780 + tick));
        }
        return card(4, "spellcard.gensokyou.rumia.4", 0.40D, 260,
                card.loop(260, TailPolicy.RETAIN_UNTIL_LIFETIME),
                List.of("12 颗三维星形「碎月」撒在 BOSS 前方 12 格、5×4×5 盒内，寿命 200。",
                        "hiddenCycle：周期 80，前 40 tick 正常有伤，后 40 tick 半透明且跳过碰撞 ⇒ 记忆/节拍题。",
                        "月晕 = 半径 6.5、缺口 80° 的慢墙（.09）；月扫 = 3 发慢速大玉。单拍最大 16。"));
    }

    // ------------------------------------------------------------------
    // 5 · 夜符「子夜之鸟」：高速俯冲 + 四面收束
    // ------------------------------------------------------------------
    private static BossProgramCards.Card midnightBird() {
        var card = new CardProgram("gensokyou:rumia_midnight_bird");
        var targets = card.targets("players", 64, 5);
        var flockFrame = card.aimed("flockFrame", targets, 0, new Vec3(0, 1, 0));
        var leftSwoop = card.aimed("swoopLeft", targets, 0, new Vec3(-4, 2.5, 0));
        var rightSwoop = card.aimed("swoopRight", targets, 0, new Vec3(4, 2.5, 0));

        var swoop = MemberProgram.attack("swoop",
                        MemberProgram.styled(new Patterns.ConeScatter(8, Math.toRadians(22), 11),
                                "sphere", "#7FD3FF", 0.16, 1, 0.32, "swoop"),
                        new Paths.Linear(Vec3.Z, 0.45), 70)
                .templateDirections().build();

        var flock = MemberProgram.attack("flock",
                        MemberProgram.styled(new Patterns.AroundPoint(16, new Vec3(0, 0, 10), 4, 8, 0.4,
                                        Math.toRadians(40), 13),
                                "sphere", "#B18CFF", 0.16, 1, 0.32, "flock"),
                        new Paths.Linear(Vec3.Z, 0.38), 70)
                .templateDirections().build();

        var nightWall = MemberProgram.attack("nightWall",
                        MemberProgram.styled(new Patterns.Ring(18, 7, Math.toRadians(90), 0),
                                "sphere", "#FFF1B8", 0.22, 1, 0.44, "wall"),
                        new Paths.Linear(Vec3.Z, 0.30), 80)
                .fixedDirection().build();

        for (int beat = 0; beat < 10; beat++) {
            int tick = beat * 20;
            var side = (beat % 2 == 0) ? leftSwoop : rightSwoop;
            card.at(tick, c -> c.fire(side, swoop, 10, 800 + tick));
        }
        for (int tick : new int[]{0, 55, 110, 165}) {
            card.at(tick, c -> c.fire(flockFrame, flock, 15, 820 + tick));
        }
        for (int tick : new int[]{30, 140}) {
            card.at(tick, c -> {
                var gun = c.aimed("wallFrame" + tick, targets, 0, new Vec3(0, 2, 0));
                c.fire(gun, nightWall, 18, 840 + tick);
            });
        }
        return card(5, "spellcard.gensokyou.rumia.5", 0.20D, 220,
                card.loop(220, TailPolicy.RETAIN_UNTIL_LIFETIME),
                List.of("俯冲 = 左右两点各 8 发 22° 锥、速度 .45，左右交替 10 拍/轮。",
                        "鸟群 = 玩家前方 10 格、半径 4~8 壳内 16 发，40% 精确瞄准（aimBias .4）。",
                        "夜墙 = 半径 7、缺口 90°、速度 .30 的快墙；单拍最大 18。"));
    }

    public static BossProgramCards.Deck deck() {
        return new BossProgramCards.Deck("rumia", null,
                List.of(
                        moonlightRay(),
                        nightBird(),
                        demarcation(),
                        darkSideOfTheMoon(),
                        midnightBird()),
                List.of("5 张官方卡：月符 / 夜符 / 暗符 / 暗符·月之暗面 / 夜符·子夜之鸟。",
                        "不设独立底噪：五张卡自己承担连续性（原作一面 BOSS 也没有常驻底噪）。",
                        "T1 最终：bossTier/referenceTier 均为 1；240s / 7 hits 不变。",
                        "黑暗按「可见暗体积 + 半透明周期」实现，不做全屏遮蔽（R1）。"));
    }
}
