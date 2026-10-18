package com.bitsson.gensokyou.danmaku.program.content;

import com.bitsson.gensokyou.danmaku.program.TailPolicy;
import com.bitsson.gensokyou.danmaku.program.TimeDomain;
import com.bitsson.gensokyou.danmaku.program.author.CardProgram;
import com.bitsson.gensokyou.danmaku.program.author.MemberProgram;
import com.bitsson.gensokyou.danmaku.program.math.ScalarCurve;
import com.bitsson.gensokyou.danmaku.program.math.Vec3;
import com.bitsson.gensokyou.danmaku.program.ops.*;
import com.bitsson.gensokyou.danmaku.track.BossCards;
import java.util.*;

/**
 * 八云蓝正式程序内容（tasks 1.4）。
 *
 * <p>八张卡，全部 240 tick 一轮，与旧 {@code BossCards.yakumoRan()} 同下标/同语言键；
 * <b>不叠加独立默认攻击</b>（{@code background=null}）。
 *
 * <p><b>时刻口径</b>：content-mapping 与已验收预览（{@code ProgramBlue1Test}）的拍点一律是
 * <b>实际出生 tick</b>。因此发射调用写作 {@code at(birth - warning)}；{@code fire} 的
 * {@code warningTicks} 才是预警/延迟本身。把映射拍点直接当发射时刻会整体延后 20 tick——
 * 本类用 {@link #atBirth} 统一避免。
 *
 * <p>收束：本轮 220 停全部判伤/未来发射，10 tick 淡出，230..239 无攻击，240 新轮重新采样。
 * 共同：普通弹 life120 / 弹径 .40；最多 5 个个人目标槽；净距 6；预警 20。
 *
 * <p>已补齐的通用能力（本轮扩展通用作者入口/op，非按卡特判）：逐弹朝真实采样目标点
 * （{@code MemberProgram.aimAtTarget} + 运行期采样，随批次下发）、倾斜面板
 * （{@code Patterns.TiltedGrid}）、走廊方块净空（{@code CardProgram.corridor}）、
 * 声音（{@code Presentation.SoundCue}）；面板预警由每批逐成员 age-zero 姿态表达。
 * 卡 6 转向轴取本地 Y（= 锁定帧 U）。
 */
public final class YakumoRanPrograms {
    private YakumoRanPrograms() {}

    private static final int CYCLE = 240;
    private static final int WIND_DOWN = 220;
    private static final int LIFE = 120;
    private static final int WARNING = 20;
    private static final double MIN_CLEARANCE = 6;

    private static String hex(int index) { return BossProgramCards.hex(BossCards.RAN_PALETTE.at(index)); }

    // --- 共用构件 --------------------------------------------------------

    private static Actions.ChildProgram grid9(double speed, double damage, String color) {
        // 个人主轨：3×3 格，逐弹朝本拍真实采样到的目标点飞（源→Ti 的真实距离与倾斜，运行期解析）。
        return MemberProgram.attack("grid9", MemberProgram.styled(new Patterns.ParallelGrid(3, 3, 1.5, 1.5, Set.of(), Vec3.ZERO),
                        "sphere", color, 0.2, damage, 0.2, "grid9"), new Paths.Linear(Vec3.Z, speed), LIFE)
                .aimAtTarget().build();
    }

    /** 倾斜面板：{@code normalize(F ± kR)}（{@code columnTilt} 按列分左右倾角）。 */
    private static Actions.ChildProgram tiltedGrid(int rows, int columns, double spacingX, double spacingY,
                                                   Set<Integer> omitted, Vec3 forward, double columnTilt,
                                                   double speed, double damage, String color, int life) {
        return MemberProgram.attack("tilted", MemberProgram.styled(new Patterns.TiltedGrid(rows, columns, spacingX, spacingY, omitted, Vec3.ZERO, forward, columnTilt),
                        "sphere", color, 0.2, damage, 0.2, "tilted"), new Paths.Linear(Vec3.Z, speed), life)
                .templateDirections().build();
    }

    private static Actions.ChildProgram grid18(double speed, double damage, String color, Path path) {
        return MemberProgram.attack("grid18", MemberProgram.styled(new Patterns.ParallelGrid(3, 7, 2.5, 2.5, Set.of(3), Vec3.ZERO),
                        "sphere", color, 0.2, damage, 0.2, "grid18"), path, LIFE)
                .fixedDirection().build();
    }

    /** 出生 tick 到发射调用的换算：{@code fire} 的 warning 才是预警延迟。 */
    private static void atBirth(CardProgram card, int birthTick, java.util.function.Consumer<CardProgram> body) {
        card.at(birthTick - WARNING, body);
    }

    private static void windDown(CardProgram card, List<CardProgram.Group> groups) {
        card.at(WIND_DOWN, c -> { for (var group : groups) c.collapse(group, 10); });
    }

    /**
     * 个人主轨：每名被捕获玩家一槽、左右两个源（±offsetX）。{@code birthOffsets} 是相对基点的
     * 出生 tick，左右交替取不同侧。
     */
    private static void personalLanes(CardProgram card, List<CardProgram.Group> groups, String prefix,
                                      int base, CardProgram.Targets targets, double offsetX,
                                      double speed, double damage, String color, int[] birthOffsets) {
        for (int slot = 0; slot < 5; slot++) {
            int s = slot;
            for (int side : new int[]{-1, 1}) {
                var group = card.aimed(prefix + "_" + base + "_" + s + "_" + side, targets, s,
                        new Vec3(side * offsetX, 1, 0));
                groups.add(group);
                var bullets = grid9(speed, damage, color);
                for (int k = 0; k < birthOffsets.length; k++) {
                    if ((k % 2 == 0 ? -1 : 1) == side) {
                        int birth = base + birthOffsets[k];
                        atBirth(card, birth, cc -> cc.fire(group, bullets, WARNING, 200 + birth + s));
                    }
                }
            }
        }
    }

    // --- 卡 1 · 式神「仙狐思念」----------------------------------------

    private static BossProgramCards.Card card1() {
        var card = new CardProgram("gensokyou:ran_1").clearance(MIN_CLEARANCE);
        List<CardProgram.Group> groups = new ArrayList<>();
        int[] births = {20, 28, 36, 44};
        for (int base : new int[]{0, 60, 120}) {
            card.at(base, c -> {
                var targets = c.targets("t" + base, 64, 5);
                personalLanes(c, groups, "g", base, targets, 2, 0.30, 1, hex(1), births);
                c.lockMovement(groups.get(0), 164);
            });
        }
        windDown(card, groups);
        return new BossProgramCards.Card(1, "spellcard.gensokyou.yakumo_ran.1", 1.000D, CYCLE, 0,
                card.loop(CYCLE, TailPolicy.END_AT_BOUNDARY),
                List.of("出生 0/60/120 基点 + 20/28/36/44 交替左右；108/人/轮；锁至 164。",
                        "逐弹朝本拍真实采样目标点飞（aimAtTarget：源→Ti 真实距离与倾斜；客户端随批次收到同一点）。"));
    }

    // --- 卡 2 · 式神「前鬼後鬼の守護」------------------------------------

    private static BossProgramCards.Card card2() {
        var card = new CardProgram("gensokyou:ran_2").clearance(MIN_CLEARANCE);
        List<CardProgram.Group> groups = new ArrayList<>();
        card.at(0, c -> {
            var targets = c.targets("panelPlayers", 64, 5);
            var frame = c.arena("panelFrame", targets);
            for (int side : new int[]{-1, 1}) {
                var group = c.offset("panel" + side, frame, new Vec3(side, 0, 0));
                groups.add(group);
                var bullets = grid18(0.25, 1, side < 0 ? hex(2) : hex(5), new Paths.Linear(Vec3.Z, 0.25));
                int[] births = side < 0 ? new int[]{20, 60, 100, 140} : new int[]{40, 80, 120, 160};
                for (int birth : births) atBirth(c, birth, cc -> cc.fire(group, bullets, WARNING, 220 + birth));
            }
            c.lockMovement(frame, 160);
        });
        windDown(card, groups);
        return new BossProgramCards.Card(2, "spellcard.gensokyou.yakumo_ran.2", 0.875D, CYCLE, 0,
                card.loop(CYCLE, TailPolicy.END_AT_BOUNDARY),
                List.of("出生：左 20/60/100/140、右 40/80/120/160，共 8×18=144 公共；锁至 160。",
                        "面板预警以每批逐成员的 age-zero 姿态（render warning）表达，无需额外轮廓原语。"));
    }

    // --- 卡 3 · 式輝「狐狸妖怪レーザー」+ 白点名 --------------------------

    private static BossProgramCards.Card card3() {
        var card = new CardProgram("gensokyou:ran_3").clearance(MIN_CLEARANCE);
        List<CardProgram.Group> groups = new ArrayList<>();
        card.at(0, c -> {
            var targets = c.targets("crossPlayers", 64, 5);
            var frame = c.arena("crossFrame", targets);
            for (int side : new int[]{-1, 1}) {
                var group = c.offset("cross" + side, frame, new Vec3(side * 5, 0, 0));
                groups.add(group);
                // 方向 normalize(F ± .22R)：显式逐弹方向，不再是平行 F。
                Vec3 tilt = Vec3.Z.add(Vec3.X.multiply(side * 0.22D)).unit();
                var bullets = tiltedGrid(3, 3, 2, 2, Set.of(), tilt, 0, 0.25, 1, hex(0), LIFE);
                int start = side < 0 ? 20 : 30;
                for (int i = 0; i < 6; i++) {
                    int birth = start + i * 20;
                    atBirth(c, birth, cc -> cc.fire(group, bullets, WARNING, 240 + birth));
                }
            }
            // 白点名：独立目标快照，逐槽 3 平行弹；出生 90/150（发射 70/130）。
            var whiteTargets = c.targets("whitePlayers", 64, 5);
            for (int slot = 0; slot < 5; slot++) {
                int s = slot;
                var white = c.aimed("whiteShot" + s, whiteTargets, s, new Vec3(0, 1, 0));
                groups.add(white);
                var whiteBullets = MemberProgram.attack("white", MemberProgram.styled(new Patterns.ParallelGrid(1, 3, 0.7, 0, Set.of(), Vec3.ZERO),
                                "sphere", hex(2), 0.2, 1, 0.2, "white"), new Paths.Linear(Vec3.Z, 0.25), LIFE)
                        .fixedDirection().build();
                for (int birth : new int[]{90, 150}) namedShot(c, birth, white, whiteBullets, s);
            }
            c.lockMovement(frame, 130);
            c.at(50, cc -> cc.lockMovement(frame, 60));
            c.at(110, cc -> cc.lockMovement(frame, 20));
        });
        windDown(card, groups);
        return new BossProgramCards.Card(3, "spellcard.gensokyou.yakumo_ran.3", 0.750D, CYCLE, 0,
                card.loop(CYCLE, TailPolicy.END_AT_BOUNDARY),
                List.of("出生：左 20..120 步 20、右 30..130 步 20，公共 108；白点名出生 90/150、逐槽 3 发 = 每人 6。",
                        "方向 normalize(F±.22R) 用显式逐弹方向表达（左 F−.22R、右 F+.22R）。"));
    }

    private static void namedShot(CardProgram card, int birth, CardProgram.Group group, Actions.ChildProgram bullets, int slot) {
        atBirth(card, birth, c -> c.fire(group, bullets, WARNING, 260 + birth + slot));
    }

    // --- 卡 4 · 狐狸妖怪激光（物理断口）---------------------------------

    private static BossProgramCards.Card card4() {
        var card = new CardProgram("gensokyou:ran_4").clearance(MIN_CLEARANCE);
        List<CardProgram.Group> groups = new ArrayList<>();
        for (int base : new int[]{0, 100}) {
            card.at(base, c -> {
                var targets = c.targets("laserPlayers" + base, 64, 5);
                var frame = c.arena("laserFrame" + base, targets);
                var balls = c.offset("balls" + base, frame, Vec3.ZERO);
                var lasers = c.offset("lasers" + base, frame, Vec3.ZERO);
                groups.add(frame);
                var ballProgram = MemberProgram.attack("balls", MemberProgram.styled(new Patterns.ParallelGrid(3, 3, 1.5, 1.5, Set.of(), Vec3.ZERO),
                                "sphere", hex(1), 0.2, 1, 0.2, "ball"), new Paths.Linear(Vec3.Z, 0.3), 70)
                        .fixedDirection().build();
                for (int birth : new int[]{base + 20, base + 28, base + 36})
                    atBirth(c, birth, cc -> cc.fire(balls, ballProgram, WARNING, 280 + birth));
                // 激光：映射明确给的是预警时刻 b+40 → 激活 b+70 → 结束 b+90（寿命 20）。
                var beam = MemberProgram.attack("laser", MemberProgram.styled(new Patterns.AxisNodes(Vec3.Z,
                                    List.of(6D, 10D), List.of(Vec3.X, Vec3.X.multiply(-1)), 1.5),
                                "laser", hex(5), 0.18, 1, 0.18, "laser"), new Paths.Hold(), 20)
                        .templateDirections().laser(24, false, 10).build();
                c.at(base + 40, cc -> cc.fire(lasers, beam, 30, 320 + base));
                // 激活时检查中心走廊方块净空与声明净宽；失败撤该激光批（本地坐标）。
                c.at(base + 70, cc -> cc.corridor(lasers, new Vec3(0, 0, 4), new Vec3(0, 0, 12), 1.32, 4096, true));
                c.lockMovement(frame, 189 - base);
            });
        }
        windDown(card, groups);
        return new BossProgramCards.Card(4, "spellcard.gensokyou.yakumo_ran.4", 0.625D, CYCLE, 0,
                card.loop(CYCLE, TailPolicy.END_AT_BOUNDARY),
                List.of("球出生 b+20/28/36（54）；激光预警 b+40、激活 b+70、结束 b+90（8 道，AxisNodes 6/10 ×±X、内缩 1.5 ⇒ 净宽 2.64）。",
                        "激活时用 corridor 检查中心走廊方块净空与净宽，失败撤该激光批（本地坐标，通用 op）。"));
    }

    // --- 卡 5 · 留影之符 -------------------------------------------------

    private static BossProgramCards.Card card5() {
        var card = new CardProgram("gensokyou:ran_5").clearance(MIN_CLEARANCE);
        List<CardProgram.Group> groups = new ArrayList<>();
        var motion = new Paths.SpeedCurve(Vec3.Z, new ScalarCurve(List.of(
                new ScalarCurve.Key(0, 0.20), new ScalarCurve.Key(20, 0.0),
                new ScalarCurve.Key(40, 0.0), new ScalarCurve.Key(60, 0.30))));
        var profile = new Presentation.Profile(List.of(new Presentation.Window(TimeDomain.MEMBER_AGE, 0, LIFE)),
                null, null,
                List.of(new Presentation.Flash(new Presentation.Window(TimeDomain.MEMBER_AGE, 30, 31), 1.0, 1.35)),
                List.of(new Presentation.SoundCue(TimeDomain.MEMBER_AGE, 30, "minecraft:entity.blaze.shoot", "ran5-flash")));
        for (int base : new int[]{0, 90}) {
            card.at(base, c -> {
                var targets = c.targets("mementoPlayers" + base, 64, 5);
                var group = c.arena("memento" + base, targets);
                groups.add(group);
                var bullets = MemberProgram.attack("memento", MemberProgram.styled(new Patterns.ParallelGrid(3, 7, 2.5, 2.5, Set.of(3), Vec3.ZERO),
                                "sphere", hex(2), 0.2, 1, 0.2, "memento"), motion, LIFE)
                        .fixedDirection().presentation(profile).build();
                for (int birth : new int[]{base + 20, base + 32, base + 44})
                    atBirth(c, birth, cc -> cc.fire(group, bullets, WARNING, 360 + birth));
                c.lockMovement(group, 134);
            });
        }
        windDown(card, groups);
        return new BossProgramCards.Card(5, "spellcard.gensokyou.yakumo_ran.5", 0.500D, CYCLE, 0,
                card.loop(CYCLE, TailPolicy.END_AT_BOUNDARY),
                List.of("SpeedCurve 停批后再出发；出生 b+20/32/44，共 6×18=108 公共；锁至 134。",
                        "age30 闪亮用 Flash、声音用 SoundCue（须在预警结束后才派发，渲染投影已实现）。"));
    }

    // --- 卡 6 · 双使回转 -------------------------------------------------

    private static BossProgramCards.Card card6() {
        var card = new CardProgram("gensokyou:ran_6").clearance(MIN_CLEARANCE);
        List<CardProgram.Group> groups = new ArrayList<>();
        card.at(0, c -> {
            var targets = c.targets("turnPlayers", 64, 5);
            var frame = c.arena("turnFrame", targets);
            for (int side : new int[]{-1, 1}) {
                var group = c.offset("turn" + side, frame, new Vec3(side * 4, 0, 0));
                groups.add(group);
                double angle = side * Math.toRadians(25);
                Vec3 initial = Vec3.Z.rotate(Vec3.Y, angle);
                var path = new Paths.Segments(List.of(
                        new Paths.Segment("out", 30, new Paths.Linear(initial, 0.2)),
                        new Paths.Segment("turn", 30, new Paths.Arc(initial, Vec3.Y, -angle / 30, 0.2)),
                        new Paths.Segment("return", 1, new Paths.Linear(Vec3.Z, 0.2))), Paths.Tail.CONTINUE);
                var bullets = MemberProgram.attack("turn", MemberProgram.styled(new Patterns.ParallelGrid(4, 3, 1.5, 2, Set.of(), Vec3.ZERO),
                                "sphere", side < 0 ? hex(1) : hex(0), 0.2, 1, 0.2, "turn"), path, LIFE)
                        .fixedDirection().build();
                int[] births = side < 0 ? new int[]{20, 44, 68, 92} : new int[]{32, 56, 80, 104};
                for (int birth : births) atBirth(c, birth, cc -> cc.fire(group, bullets, WARNING, 380 + birth));
            }
            c.lockMovement(frame, 104);
        });
        windDown(card, groups);
        return new BossProgramCards.Card(6, "spellcard.gensokyou.yakumo_ran.6", 0.375D, CYCLE, 0,
                card.loop(CYCLE, TailPolicy.END_AT_BOUNDARY),
                List.of("出生：左 20/44/68/92、右 32/56/80/104，共 8×12=96；锁至 104。",
                        "age20 弧线预告由路径含 Arc 段触发（渲染投影已实现）。",
                        "转向轴取本地 Y（= 锁定帧 U，F/R 平面），保证末端回 F；Paths 的 Vec3.Y 是局部 Y 而非世界 Y。"));
    }

    // --- 卡 7 · 双使合奏 -------------------------------------------------

    private static BossProgramCards.Card card7() {
        var card = new CardProgram("gensokyou:ran_7").clearance(MIN_CLEARANCE);
        List<CardProgram.Group> groups = new ArrayList<>();
        card.at(0, c -> {
            var targets = c.targets("ensemblePlayers", 64, 5);
            var frame = c.arena("ensembleFrame", targets);
            for (int side : new int[]{-1, 1}) {
                var group = c.offset("ensemble" + side, frame, new Vec3(side, 0, 0));
                groups.add(group);
                var bullets = grid18(0.25, 1, side < 0 ? hex(2) : hex(5), new Paths.Linear(Vec3.Z, 0.25));
                // 8 拍合计 144 公共：左右各 4 拍（复用卡 2 的左右交替）。
                int[] births = side < 0 ? new int[]{20, 60, 100, 140} : new int[]{40, 80, 120, 160};
                for (int birth : births) atBirth(c, birth, cc -> cc.fire(group, bullets, WARNING, 420 + birth + side));
            }
            // 白点名：逐槽 3 平行弹；出生 80/140（发射 60/120）。
            var whiteTargets = c.targets("ensembleWhitePlayers", 64, 5);
            for (int slot = 0; slot < 5; slot++) {
                int s = slot;
                var white = c.aimed("ensembleWhite" + s, whiteTargets, s, new Vec3(0, 1, 0));
                groups.add(white);
                var whiteBullets = MemberProgram.attack("ensembleWhite", MemberProgram.styled(new Patterns.ParallelGrid(1, 3, 0.7, 0, Set.of(), Vec3.ZERO),
                                "sphere", hex(2), 0.2, 1, 0.2, "ensembleWhite"), new Paths.Linear(Vec3.Z, 0.25), 70)
                        .fixedDirection().build();
                for (int birth : new int[]{80, 140}) namedShot(c, birth, white, whiteBullets, s);
            }
            c.lockMovement(frame, 160);
            c.at(40, cc -> cc.lockMovement(frame, 60));
            c.at(100, cc -> cc.lockMovement(frame, 20));
        });
        windDown(card, groups);
        return new BossProgramCards.Card(7, "spellcard.gensokyou.yakumo_ran.7", 0.250D, CYCLE, 0,
                card.loop(CYCLE, TailPolicy.END_AT_BOUNDARY),
                List.of("面板出生 20..160 步 20，8×18=144；白点名出生 80/140、逐槽 3 发 = 每人 6。",
                        "复用卡 2 的共享帧面板几何与卡 5 的点名节奏。"));
    }

    // --- 卡 8 · 九尾余光（终局）-----------------------------------------

    private static BossProgramCards.Card card8() {
        var card = new CardProgram("gensokyou:ran_8").clearance(MIN_CLEARANCE);
        List<CardProgram.Group> groups = new ArrayList<>();
        int[] bases = {0, 60, 120};
        double[] speeds = {0.25, 0.275, 0.30};
        int[] births = {20, 28, 36, 44};
        for (int bi = 0; bi < bases.length; bi++) {
            int base = bases[bi];
            double speed = speeds[bi];
            card.at(base, c -> {
                var targets = c.targets("ninePlayers" + base, 64, 5);
                personalLanes(c, groups, "nine", base, targets, 2, speed, 1.1, hex(0), births);
                c.lockMovement(groups.get(0), 168);
            });
        }
        // 副轨：单个公共面板（Grid 4×3，两源/两端在 ±5 列），固定方向 F±.22R 由列位决定。
        // 复用各子轮的主目标帧（在基点 60/120 采样，不在 84/144 另采）；6 拍 × 12 = 72 公共。
        card.at(60, c -> {
            var targets = c.targets("nineTailPlayers60", 64, 5);
            var group = c.arena("nineTail60", targets);
            groups.add(group);
            var bullets = tiltedGrid(4, 3, 5, 1.5, Set.of(), Vec3.Z, Math.toRadians(12.6), 0.20, 1.1, hex(2), LIFE);
            for (int birth : new int[]{84, 100}) atBirth(c, birth, cc -> cc.fire(group, bullets, WARNING, 500 + birth));
        });
        card.at(120, c -> {
            var targets = c.targets("nineTailPlayers120", 64, 5);
            var group = c.arena("nineTail120", targets);
            groups.add(group);
            var bullets = tiltedGrid(4, 3, 5, 1.5, Set.of(), Vec3.Z, Math.toRadians(12.6), 0.20, 1.1, hex(2), LIFE);
            for (int birth : new int[]{144, 152, 160, 168}) atBirth(c, birth, cc -> cc.fire(group, bullets, WARNING, 500 + birth));
        });
        windDown(card, groups);
        return new BossProgramCards.Card(8, "spellcard.gensokyou.yakumo_ran.8", 0.125D, CYCLE, 0,
                card.loop(CYCLE, TailPolicy.END_AT_BOUNDARY),
                List.of("主出生 0/60/120 基点 × 20/28/36/44，108/人（逐弹朝真实采样目标点）；副单面板 Grid4×3、两端列在 ±5、方向按列 F±.22R，6 拍 × 12 = 72（出生 84/100/144/152/160/168，在基点复用主目标帧）；单人 180、5 人 612。倍率 1.1。",
                        "尾部九道余辉为 FX，不在本程序范围。"));
    }

    public static BossProgramCards.Deck deck() {
        // 无独立底噪：蓝不叠加默认攻击。
        return new BossProgramCards.Deck("yakumo_ran", null,
                List.of(card1(), card2(), card3(), card4(), card5(), card6(), card7(), card8()),
                List.of("全部 240 tick 一轮；220 停判伤/未来发射、10 tick 淡出、230..239 无攻击；无底噪。",
                        "净距 6；个人组失败只取消该槽子轮；发射时刻 = 出生 − 预警。"));
    }
}
