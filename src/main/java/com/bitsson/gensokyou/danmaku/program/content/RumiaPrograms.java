package com.bitsson.gensokyou.danmaku.program.content;

import com.bitsson.gensokyou.danmaku.program.TailPolicy;
import com.bitsson.gensokyou.danmaku.program.TimeDomain;
import com.bitsson.gensokyou.danmaku.program.author.CardProgram;
import com.bitsson.gensokyou.danmaku.program.author.MemberProgram;
import com.bitsson.gensokyou.danmaku.program.math.ScalarCurve;
import com.bitsson.gensokyou.danmaku.program.math.Vec3;
import com.bitsson.gensokyou.danmaku.program.ops.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Score v3: public volume constraints plus independently refreshed personal displacement. */
public final class RumiaPrograms {
    private RumiaPrograms() {}

    public static final int CYCLE = 320;
    private static final int WARNING = 40;

    private static ScalarCurve ramp(double from, double to, int ticks) {
        return new ScalarCurve(List.of(new ScalarCurve.Key(0, from), new ScalarCurve.Key(ticks, to)));
    }

    /** Parallel rays span 84 x 78.2 blocks; origins remain outside the 32-block combat sphere. */
    private static Pattern curtain(String color, String tag) {
        List<Pattern> rays = new ArrayList<>();
        for (int row = -23; row <= 23; row++) {
            rays.add(new Patterns.Point(new Vec3(0, row * 1.7, -42), Vec3.Z));
        }
        return MemberProgram.styled(new Patterns.Union(rays), "laser", color, .80, 1, .40, tag);
    }

    private static Transforms.Transform rotation(Vec3 axis, double start, double end, int duration) {
        return new Transforms.Rotate(axis, ramp(Math.toRadians(start), Math.toRadians(end), duration), Vec3.ZERO);
    }

    private static Actions.ChildProgram sheet(String color, Transforms.Transform motion, int life,
                                               boolean dark, int phase) {
        var member = MemberProgram.attack("public curtain", curtain(color, "field"), new Paths.Hold(), life)
                .fixedDirection().laser(84, true, 10).transform(motion);
        if (dark) member.hiddenCycle(TimeDomain.MEMBER_AGE, 120, 60, phase);
        return member.build();
    }

    private static CardProgram.Group arena(CardProgram c, String name) {
        var targets = c.targets(name + "Targets", 64, 5);
        return c.arena(name, targets);
    }

    private static void field(CardProgram c, int tick, String name, Actions.ChildProgram members) {
        c.at(tick, q -> {
            var group = arena(q, name);
            // Keep the arena centred on the boss throughout this score; no drifting-away exploit.
            if (tick == 0) q.lockMovement(group, CYCLE);
            q.fire(group, members, WARNING, 2000 + tick);
        });
    }

    /** Five explicit target slots, refreshed on each beat; never replicate public curtains. */
    private static void drive(CardProgram c, int period, boolean laser, String color) {
        for (int tick = 0; tick < CYCLE; tick += period) {
            int t = tick;
            c.at(t, q -> {
                var targets = q.targets("driveTargets" + t, 40, 5);
                for (int slot = 0; slot < 5; slot++) {
                    var group = q.aimed("drive" + t + "_" + slot, targets, slot, Vec3.ZERO);
                    var pattern = MemberProgram.styled(laser ? new Patterns.Point(Vec3.ZERO, Vec3.Z)
                                    : new Patterns.AngularGrid(3, 3, Math.toRadians(5), Math.toRadians(5)),
                            laser ? "laser" : "sphere", color, laser ? .65 : .40, 1,
                            laser ? .38 : .48, "drive");
                    var member = MemberProgram.attack("personal displacement", pattern,
                            laser ? new Paths.Hold() : new Paths.Linear(Vec3.Z, .65), laser ? 18 : 70);
                    if (laser) member.fixedDirection().laser(40, true, 10);
                    else member.templateDirections().piercing();
                    q.fire(group, member.build(), 30, 3000 + t + slot);
                }
            });
        }
    }

    private static BossProgramCards.Card finish(int index, CardProgram c, String... notes) {
        return new BossProgramCards.Card(index, "spellcard.gensokyou.rumia." + index,
                new double[]{1, .8, .6, .4, .2}[index - 1], CYCLE, 0,
                c.loop(CYCLE, TailPolicy.RETAIN_UNTIL_LIFETIME), List.of(notes));
    }

    private static BossProgramCards.Card moonlightRay() {
        var c = new CardProgram("gensokyou:rumia_moonlight_ray");
        field(c, 0, "meridian", sheet("#BFD8FF", rotation(Vec3.Y, 0, 110, 280), 280, false, 0));
        field(c, 20, "inclinedMoon", sheet("#7E9EDB", new Transforms.Compose(List.of(
                rotation(Vec3.Z, 65, 65, 280), rotation(Vec3.Y, 90, -20, 280))), 280, false, 0));
        drive(c, 50, true, "#FFE6A2");
        return finish(1, c,
                "限制：两张84格长的月光栅幕绕BOSS反向旋转，一张竖直、一张倾斜65°；背面和高低处也会经过幕面。",
                "驱赶：每2.5秒对每名玩家重新锁定眼位，预警1.5秒后照射0.9秒；40格射程，已锁定光束不追踪。",
                "解法：金色点名亮起时离开原站位；接近蓝幕时从相邻光条之间穿过，或等幕扫过后换区。",
                "时间：0/1秒开始两幕预警，2/3秒激活，分别持续14秒；16秒循环，第二幕尾部跨轮1秒。每幕47条、条距1.7格，实际伤害半径0.4格。",
                "重点检查：保持固定半径绕圈、飞到头顶或身后是否仍需处理幕面/点名；没有实体笼子或隐形边界。近轴低速、外缘扫速较快，32格外缘手感待试玩。");
    }

    private static BossProgramCards.Card nightBird() {
        var c = new CardProgram("gensokyou:rumia_night_bird");
        for (int wave = 0; wave < 4; wave++) {
            int t = wave * 80;
            double angle = wave * 45;
            for (int side : new int[]{-1, 1}) {
                var motion = new Transforms.Compose(List.of(
                        rotation(Vec3.Z, side * 25, side * 25, 120),
                        new Transforms.Translate(new Paths.Bezier(List.of(new Vec3(side * 28, 0, 0),
                                new Vec3(0, side * 5, 0), new Vec3(-side * 28, 0, 0)),
                                120, Paths.TimeMap.SMOOTHSTEP, Paths.Tail.HOLD)),
                        rotation(Vec3.Y, angle, angle, 120)));
                field(c, t + (side < 0 ? 0 : 24), "wing" + wave + "_" + side,
                        sheet(side < 0 ? "#9C7BFF" : "#F0A8DB", motion, 120, false, 0));
            }
        }
        drive(c, 32, false, "#FFF0B8");
        return finish(2, c,
                "限制：紫粉两翼是倾斜光条幕，从BOSS两侧±28格交错扫到对侧，切断直飞路线；下一对换45°方向。",
                "驱赶：每1.6秒向每名玩家当前眼位打3×3羽弹，预警1.5秒，13格/秒、寿命3.5秒；中央弹精确指向锁定站位。",
                "解法：紫翼先行、粉翼晚1.2秒；观察翼幕移动方向，穿光条间隙换到刚扫过的一侧，同时偏离金色羽弹的锁定位置。",
                "时间：每4秒一对，各翼预警2秒、飞行6秒；跨轮保留已出生翼。每翼47条，整轮8翼，最大四翼叠加；16秒循环。",
                "重点检查：绕圈会遇到后续变向翼幕，升降会穿过倾斜栅格；条间穿越是主动解法。羽弹穿地形，预览不证明障碍物后的可读性。");
    }

    private static BossProgramCards.Card demarcation() {
        var c = new CardProgram("gensokyou:rumia_demarcation");
        for (int axis = 0; axis < 3; axis++) {
            int a = axis;
            for (int side : new int[]{-1, 1}) {
                var offset = new Paths.Segments(List.of(
                        new Paths.Segment("in", 120, new Paths.Linear(Vec3.X, -side * .16)),
                        new Paths.Segment("out", 120, new Paths.Linear(Vec3.X, side * .16))), Paths.Tail.HOLD);
                var motion = new Transforms.Compose(List.of(
                        new Transforms.Translate(new Paths.Linear(Vec3.X, 0)),
                        new Transforms.Translate(new Paths.Bezier(List.of(new Vec3(side * 28, 0, 0),
                                new Vec3(side * 28, 0, 0), new Vec3(side * 28, 0, 0)), 1, Paths.TimeMap.LINEAR, Paths.Tail.HOLD)),
                        new Transforms.Translate(offset),
                        rotation(a == 2 ? Vec3.Z : Vec3.Y, a == 0 ? 0 : 90, a == 0 ? 0 : 90, 240)));
                field(c, axis * 20, "border" + axis + "_" + side,
                        sheet(new String[]{"#DA536A", "#FFBA80", "#A58BE8"}[axis], motion, 240, false, 0));
            }
        }
        drive(c, 40, true, "#FFF1CF");
        return finish(3, c,
                "限制：三对正交境界幕从±28格缓慢内收至±8.8格，再展开；六个面依次开始，使32格球内各方向都有移动边界。",
                "驱赶：每2秒点名每名玩家，1.5秒预警后40格直射光束持续0.9秒；留在收缩中心也不能永久站桩。",
                "解法：决定留在正在缩小的区域，还是穿过条间空隙换到外侧；外侧不免疫点名，下一轮幕面重新占据外区。",
                "时间：三对幕在0/1/2秒预警，2/3/4秒激活；各6秒收缩、6秒展开，3.2格/秒；16秒循环。每面47条，公共282条。",
                "重点检查：这是可穿栅幕，不是六面绝对封死的箱体；单纯到球壳外侧不是整卡解法。需要实测六面同时可见时的光束遮挡。");
    }

    private static BossProgramCards.Card darkSideOfTheMoon() {
        var c = new CardProgram("gensokyou:rumia_dark_side");
        for (int axis = 0; axis < 3; axis++) {
            var motion = new Transforms.Compose(List.of(
                    rotation(Vec3.Z, axis * 60, axis * 60, 280), rotation(Vec3.Y, 0, 100, 280)));
            field(c, 0, "dark" + axis, sheet(new String[]{"#9675D5", "#A7C9EE", "#D998D9"}[axis],
                    motion, 280, true, axis % 2 * 60));
        }
        drive(c, 40, true, "#FFE6AD");
        return finish(4, c,
                "限制：三张相差60°的月幕穿过BOSS并缓慢旋转，紫/粉两幕与蓝幕交替有害；不是只在玩家正面亮灭的一小块墙。",
                "驱赶：每2秒全方向重新点名，金光预警1.5秒后激活0.9秒；明暗安全窗不等于可以站着不动。",
                "解法：优先穿半透明幕面，避开金色点名；有害幕仍保留光条间隙，切换时不强迫瞬间穿过实体屏障。",
                "时间：2秒激活月幕；2–5秒紫粉实/蓝虚，5–8秒反转，每3秒交换；16秒循环，每轮月幕寿命14秒。公共141条。",
                "重点检查：半透明阶段全程可见且无碰撞；固定上升路线也会经过倾斜幕面。相位翻转附近的反应窗口及三幕交线处密度须逐卡试玩。");
    }

    private static BossProgramCards.Card midnightBird() {
        var c = new CardProgram("gensokyou:rumia_midnight_bird");
        for (int wave = 0; wave < 4; wave++) {
            int t = wave * 80;
            var motion = new Transforms.Compose(List.of(
                    rotation(Vec3.Z, wave % 2 == 0 ? 35 : -35, wave % 2 == 0 ? 35 : -35, 160),
                    rotation(Vec3.Y, wave * 45, wave * 45 + 70, 160)));
            field(c, t, "nightNet" + wave, sheet(wave % 2 == 0 ? "#B18CFF" : "#739DDD", motion, 160, false, 0));
        }
        drive(c, 24, false, "#9EDAFF");
        return finish(5, c,
                "限制：每4秒新生一张倾斜夜幕，8秒内旋转70°，上下倾斜交替；两张持续叠加，形成不断换向的交叉通路。",
                "驱赶：每1.2秒重新采样最多5名玩家，预警1.5秒后释放3×3羽弹；13格/秒可到32格，旧羽弹在新点名期间继续飞。",
                "解法：从交叉幕片的条间或正在展开的区域移动；羽弹连续压迫当前站位，不能沿同一安全缝无限停留。",
                "时间：夜幕在2/6/10/14秒激活，寿命8秒；16秒循环，尾幕跨轮。公共两幕通常94条，另有逐人9发羽弹。",
                "重点检查：这是前四卡的连续换区终题，未靠伤害倍率抬难度。需检查持续同向绕圈能否完全逃课，以及多人羽弹是否挤掉可用通路。");
    }

    public static BossProgramCards.Deck deck() {
        return new BossProgramCards.Deck("rumia", null,
                List.of(moonlightRay(), nightBird(), demarcation(), darkSideOfTheMoon(), midnightBird()),
                List.of("谱面v3：32格球内公共移动约束+逐人持续驱赶。数值为首轮候选，等待逐卡实机反馈。"));
    }

    public static Map<Integer, String> checkpoints(int card) {
        return switch (card) {
            case 1 -> Map.of(30, "首轮点名", 40, "竖直月幕", 60, "倾斜月幕", 160, "反向旋转中段");
            case 2 -> Map.of(40, "紫翼", 64, "粉翼", 100, "紫翼过中心", 180, "第二对交叉");
            case 3 -> Map.of(40, "左右边界", 80, "六面齐备", 160, "首对最内侧", 280, "首对展开完毕");
            case 4 -> Map.of(40, "紫粉实蓝虚", 100, "蓝实紫粉虚", 160, "再次换面", 220, "第三次换面");
            case 5 -> Map.of(40, "首夜幕", 120, "交叉形成", 200, "第二次换区", 280, "末幕跨轮");
            default -> throw new IllegalArgumentException("Rumia card 1..5 required");
        };
    }
}
