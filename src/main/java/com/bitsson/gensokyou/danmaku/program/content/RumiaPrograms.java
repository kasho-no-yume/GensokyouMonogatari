package com.bitsson.gensokyou.danmaku.program.content;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.danmaku.program.TailPolicy;
import com.bitsson.gensokyou.danmaku.program.TimeDomain;
import com.bitsson.gensokyou.danmaku.program.author.CardProgram;
import com.bitsson.gensokyou.danmaku.program.author.MemberProgram;
import com.bitsson.gensokyou.danmaku.program.math.ScalarCurve;
import com.bitsson.gensokyou.danmaku.program.math.Vec3;
import com.bitsson.gensokyou.danmaku.program.ops.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 露米娅正式符卡程序（v4.1 · T2 约 35% 难度 · 双轨配合版）。
 *
 * <p>每张卡统一采用「轨 A：3D 广域空间限制（公共 arena，防绕圈/升降/贴远边逃课）」+
 * 「轨 B：点名逼迫移动（个人 aimed，防停在安全缝站桩）」的双轨配合。
 * 保持稀疏可读与小判定大视觉（collisionRadius ≤ visualRadius * 0.5）。
 */
public final class RumiaPrograms {
    private RumiaPrograms() {}

    public static final int CYCLE = 240;

    private static final String SILVER = "#C8DEFF";
    private static final String VIOLET = "#9A6BFF";
    private static final String CRIMSON = "#E05A7A";
    private static final String GOLD = "#FFE49E";
    private static final String INDIGO = "#5C7AEA";

    private static ScalarCurve ramp(double from, double to, int ticks) {
        return new ScalarCurve(List.of(new ScalarCurve.Key(0, from), new ScalarCurve.Key(ticks, to)));
    }

    private static Presentation.Profile sound(int lifetime, String soundId, String key) {
        return new Presentation.Profile(
                List.of(new Presentation.Window(TimeDomain.MEMBER_AGE, 0, lifetime)),
                null, null, List.of(),
                List.of(new Presentation.SoundCue(TimeDomain.MEMBER_AGE, 0,
                        Gensokyou.id(soundId).toString(), key)));
    }

    private static BossProgramCards.Card finish(int index, CardProgram c, String... notes) {
        return new BossProgramCards.Card(index, "spellcard.gensokyou.rumia." + index,
                new double[]{1.00, 0.80, 0.60, 0.40, 0.20}[index - 1], CYCLE, 0,
                c.loop(CYCLE, TailPolicy.RETAIN_UNTIL_LIFETIME), List.of(notes));
    }

    // ------------------------------------------------------------------
    // 1 · 月符「月光射线」
    //     轨 A（全场限位）：外缘 27~31 格向 32 格球内斜切 10 道稀疏长月光，切碎绕圈与高空
    //     轨 B（点名逼位）：2 道交叉冷金射线 + 5 发慢速银珠弧扇
    // ------------------------------------------------------------------
    private static BossProgramCards.Card moonlightRay() {
        var c = new CardProgram("gensokyou:rumia_moonlight_ray");

        // 轨 A：3 波全场稀疏斜穿月光网（公共一份，多人不复制）
        for (int wave = 0; wave < 3; wave++) {
            int t = wave * 80;
            int w = wave;
            var webPattern = MemberProgram.styled(
                    new Patterns.AroundPoint(10, Vec3.ZERO, 27.0, 31.0, 0.30, Math.toRadians(46), 11 + w * 5L),
                    "laser", SILVER, 0.25, 1.0, 0.35, "moonWeb");
            var webBeams = MemberProgram.attack("moonWeb" + w, webPattern, new Paths.Hold(), 48)
                    .templateDirections().laser(58, true, 10)
                    .presentation(sound(48, "warn_shift", "moon-web-" + w))
                    .build();
            c.at(t, q -> {
                var targets = q.targets("moonWebTargets" + w, 64, 5);
                var arena = q.arena("moonWebArena" + w, targets);
                if (w == 0) q.lockMovement(arena, 40);
                q.fire(arena, webBeams, 36, 1000 + t);
            });
        }

        // 轨 B：个人点名交叉冷金射线 + 激活同拍 5 发深靛银珠弧扇
        var crossPattern = MemberProgram.styled(
                new Patterns.Union(List.of(
                        new Patterns.Point(new Vec3(-3.2, 0.4, 0), Vec3.Z),
                        new Patterns.Point(new Vec3(3.2, -0.4, 0), Vec3.Z))),
                "laser", GOLD, 0.25, 1.0, 0.35, "cross");
        var crossBeams = MemberProgram.attack("moonCross", crossPattern, new Paths.Hold(), 22)
                .aimAtTarget().laser(44, true, 10)
                .presentation(sound(22, "warn_shift", "moon-cross"))
                .build();
        var fanBullets = MemberProgram.attack("moonFan",
                        MemberProgram.styled(new Patterns.Fan(5, Math.toRadians(52), 0),
                                "sphere", INDIGO, 0.25, 1.0, 0.50, "motes"),
                        new Paths.Linear(Vec3.Z, 0.46), 110)
                .templateDirections()
                .presentation(sound(110, "shot_normal", "moon-fan"))
                .build();

        for (int wave = 0; wave < 4; wave++) {
            int t = 20 + wave * 60;
            int w = wave;
            c.at(t, q -> {
                var targets = q.targets("moonAimTargets" + w, 48, 5);
                for (int slot = 0; slot < 5; slot++) {
                    var crossGroup = q.aimed("moonCross" + w + "_" + slot, targets, slot, new Vec3(0, 0.6, 0));
                    q.fire(crossGroup, crossBeams, 30, 1200 + t + slot);
                }
            });
            c.at(t + 30, q -> {
                var targets = q.targets("moonFanTargets" + w, 48, 5);
                for (int slot = 0; slot < 5; slot++) {
                    var fanGroup = q.aimed("moonFan" + w + "_" + slot, targets, slot, new Vec3(0, 0.4, 0));
                    q.fire(fanGroup, fanBullets, 0, 1300 + t + slot);
                }
            });
        }

        return finish(1, c,
                "定位：T2 入门双轨卡——外缘斜切光网限位 + 正面点名逼位。",
                "轨A（限位）：每 4 秒从外缘向 32 格球内斜射 10 道稀疏月银长激光（预警 1.8 秒、持续 2.4 秒），切碎绕圈与高空。",
                "轨B（逼位）：每 3 秒对各玩家打 2 道交叉冷金射线并附 5 发慢速银珠弧扇，专打停在光格内站桩的人。",
                "解法：在立体月光格之间短距切缝，见冷金交叉预警立即侧移 2~3 格并顺势绕开弧扇。",
                "节奏：12 秒循环，全场光网多人不复制，峰值轻盈清晰。");
    }

    // ------------------------------------------------------------------
    // 2 · 夜符「夜鸟」
    //     轨 A（全场限位）：全向慢速巡游夜鸟群 + 沿途稀疏外飘飞羽，封死无脑绕圈
    //     轨 B（点名逼位）：左右贝塞尔弧翼掠过 + 翼尖 3 连发瞄准金珠
    // ------------------------------------------------------------------
    private static Pattern wingArc(int side, String color) {
        List<Pattern> feathers = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            double u = i / 6.0;
            double x = side * (1.2 + u * 5.4);
            double y = Math.sin(u * Math.PI) * 2.2 - 0.4;
            double z = -u * 2.2;
            String tag = (i == 6) ? "tip" : "wing";
            feathers.add(MemberProgram.styled(
                    new Patterns.Point(new Vec3(x, y, z), Vec3.Z),
                    i % 2 == 0 ? "star_prism" : "sphere",
                    color, 0.25, 1.0, 0.50, tag));
        }
        return new Patterns.Union(feathers);
    }

    private static BossProgramCards.Card nightBird() {
        var c = new CardProgram("gensokyou:rumia_night_bird");

        // 轨 A：全向慢速巡游夜鸟，沿途留下稀疏缓飘飞羽，在 32 格球内形成立体飞羽空域
        var driftingFeather = MemberProgram.attack("driftFeather",
                        MemberProgram.styled(new Patterns.SphereDirections(2, Patterns.SphereDistribution.SEEDED, 29),
                                "sphere", INDIGO, 0.20, 1.0, 0.40, "feather"),
                        new Paths.Linear(Vec3.Z, 0.08), 110)
                .templateDirections().piercing()
                .build();
        for (int wave = 0; wave < 4; wave++) {
            int t = wave * 60;
            int w = wave;
            var roamingBirds = MemberProgram.attack("roamBird" + w,
                            MemberProgram.styled(new Patterns.SphereDirections(6, Patterns.SphereDistribution.SEEDED, 21 + w * 3L),
                                    "star_prism", VIOLET, 0.28, 1.0, 0.56, "roam"),
                            new Paths.Linear(Vec3.Z, 0.24), 130)
                    .templateDirections().piercing()
                    .emit(new Selection.Tag("roam"), 20, 24, 4, driftingFeather)
                    .build();
            c.at(t, q -> {
                var targets = q.targets("roamTargets" + w, 64, 5);
                var arena = q.arena("roamArena" + w, targets);
                q.fire(arena, roamingBirds, 0, 2000 + t);
            });
        }

        // 轨 B：左右对称贝塞尔弧翼掠过 + 翼尖 3 连发冷金点射
        var tipVolley = MemberProgram.attack("wingTipShot",
                        MemberProgram.styled(new Patterns.Point(Vec3.ZERO, Vec3.Z),
                                "star_prism", GOLD, 0.24, 1.0, 0.48, "tipShot"),
                        new Paths.Linear(Vec3.Z, 0.56), 80)
                .aimAtTarget()
                .presentation(sound(80, "shot_normal", "wing-tip"))
                .build();

        for (int wave = 0; wave < 3; wave++) {
            int baseTick = wave * 80;
            int w = wave;
            for (int side : new int[]{-1, 1}) {
                int s = side;
                int fireTick = baseTick + (side < 0 ? 0 : 28);
                String color = side < 0 ? VIOLET : CRIMSON;
                var sweep = new Paths.Bezier(List.of(
                        new Vec3(0, 0, 0),
                        new Vec3(side * 6.5, 1.4, 12.0),
                        new Vec3(-side * 5.0, -0.6, 28.0)),
                        75, Paths.TimeMap.SMOOTHSTEP, Paths.Tail.CONTINUE);
                var wing = MemberProgram.attack("nightWing" + w + "_" + (s < 0 ? "L" : "R"),
                                wingArc(side, color), sweep, 96)
                        .fixedDirection()
                        .emit(new Selection.Tag("tip"), 24, 6, 3, tipVolley)
                        .presentation(sound(96, "boss_burst", "wing-" + w + "-" + s))
                        .build();

                c.at(fireTick, q -> {
                    var targets = q.targets("birdTargets" + w + "_" + (s < 0 ? "L" : "R"), 48, 5);
                    for (int slot = 0; slot < 5; slot++) {
                        var group = q.aimed("bird" + w + "_" + (s < 0 ? "L" : "R") + "_" + slot,
                                targets, slot, new Vec3(0, 0.8, 0));
                        q.fire(group, wing, 12, 2100 + fireTick + slot * 7 + (s < 0 ? 0 : 1));
                    }
                });
            }
        }

        return finish(2, c,
                "定位：立体飞羽空域 + 左右弧翼包抄。",
                "轨A（限位）：每 3 秒向全空间放出 6 只慢速巡游夜鸟（飞满 32 格），沿途留缓飘稀疏飞羽，封死无脑绕圈。",
                "轨B（逼位）：每 4 秒一对对称贝塞尔弧翼交错掠过，翼尖连续 3 发冷金星棱瞄准点名。",
                "解法：穿飞羽空档时预留左右换位空间，左紫翼扫来右偏、右绯翼跟进切回。",
                "节奏：巡游鸟与飞羽为全场公共一份，稀疏好穿但不允许闭眼绕圈。");
    }

    // ------------------------------------------------------------------
    // 3 · 暗符「境界线」
    //     轨 A（全场限位）：每 4 秒全向缓慢膨胀的稀疏 3D 境界球壳（28 颗），覆盖 32 格全空域
    //     轨 B（点名逼位）：95° 大缺口微旋环 + 中心暗金星核正面推进
    // ------------------------------------------------------------------
    private static BossProgramCards.Card demarcation() {
        var c = new CardProgram("gensokyou:rumia_demarcation");

        // 轨 A：全向膨胀稀疏 3D 境界球壳（公共一份）
        for (int wave = 0; wave < 3; wave++) {
            int t = wave * 80;
            int w = wave;
            String color = (wave % 2 == 0) ? VIOLET : INDIGO;
            var expandShell = MemberProgram.attack("borderSphere" + w,
                            MemberProgram.styled(
                                    new Patterns.SphereSurface(28, 2.0, Patterns.SphereDistribution.FIBONACCI,
                                            Patterns.Direction.OUTWARD, Vec3.Z, 31 + w),
                                    "sphere", color, 0.26, 1.0, 0.52, "borderSphere"),
                            new Paths.Linear(Vec3.Z, 0.20), 160)
                    .templateDirections().piercing()
                    .transform(new Transforms.Rotate(Vec3.Y, ramp(0, Math.toRadians(w % 2 == 0 ? 28 : -28), 160), Vec3.ZERO))
                    .presentation(sound(160, "warn_shift", "border-sphere-" + w))
                    .build();
            c.at(t, q -> {
                var targets = q.targets("sphereTargets" + w, 64, 5);
                var arena = q.arena("sphereArena" + w, targets);
                q.fire(arena, expandShell, 15, 3000 + t);
            });
        }

        // 轨 B：95° 大缺口旋转环 + 中心暗金星核
        for (int wave = 0; wave < 4; wave++) {
            int t = wave * 60;
            int w = wave;
            double gapPhase = wave * Math.PI * 0.5;
            double spinDeg = (wave % 2 == 0) ? 32.0 : -32.0;
            String ringColor = (wave % 2 == 0) ? CRIMSON : SILVER;

            var ringPattern = new Patterns.Union(List.of(
                    MemberProgram.styled(
                            new Patterns.Ring(16, 5.2, Math.toRadians(95), gapPhase),
                            "sphere", ringColor, 0.28, 1.0, 0.56, "borderRing"),
                    MemberProgram.styled(
                            new Patterns.Point(Vec3.ZERO, Vec3.Z),
                            "star_prism", GOLD, 0.45, 1.0, 0.90, "borderCore")));
            var ringMotion = new Transforms.Compose(List.of(
                    new Transforms.Rotate(Vec3.Z, ramp(0, Math.toRadians(spinDeg), 110), Vec3.ZERO),
                    new Transforms.Translate(new Paths.Linear(Vec3.Z, 0.36))));
            var ring = MemberProgram.attack("borderRing" + w, ringPattern, new Paths.Hold(), 110)
                    .fixedDirection().piercing()
                    .transform(ringMotion)
                    .presentation(sound(110, "boss_burst", "border-ring-" + w))
                    .build();

            c.at(t, q -> {
                var targets = q.targets("borderTargets" + w, 48, 5);
                for (int slot = 0; slot < 5; slot++) {
                    var group = q.aimed("border" + w + "_" + slot, targets, slot, new Vec3(0, 0.5, 0));
                    q.fire(group, ring, 15, 3100 + t + slot);
                    if (w == 0 && slot == 0) q.lockMovement(group, 30);
                }
            });
        }

        return finish(3, c,
                "定位：全向膨胀球壳 + 正面穿环的双层空间边界。",
                "轨A（限位）：每 4 秒从 BOSS 向外膨胀一层 28 颗稀疏 3D 境界球壳（飞至 34 格），任何高度和外圈都必须正面穿壳。",
                "轨B（逼位）：每 3 秒正面推出带 95° 大缺口与中心暗金星核的微旋环，迫使侧向穿门而非站环心不动。",
                "解法：迎着稀疏球壳顺势前穿，同时对准四向轮换的 95° 大缺口避开中心星核。",
                "节奏：球壳在 14 格处弹距 > 5.5 格、24 格处 > 9 格，穿行宽敞但绕圈无效。");
    }

    // ------------------------------------------------------------------
    // 4 · 暗符「月之暗面」
    //     轨 A（全场限位）：三层覆盖 9 / 17 / 25 格的反相明暗公转月壳（公共一份）
    //     轨 B（点名逼位）：3 颗暗金星棱飞停后 1 分 6 裂珠（含直推弹）
    // ------------------------------------------------------------------
    private static BossProgramCards.Card darkSideOfTheMoon() {
        var c = new CardProgram("gensokyou:rumia_dark_side");

        // 轨 A：三层反相 hiddenCycle 公转月壳——自 BOSS 周围 2 格绽放展开至 9 / 17 / 25 格，永不撞出生净距
        var innerExpand = new ScalarCurve(List.of(
                new ScalarCurve.Key(0, 1.0), new ScalarCurve.Key(25, 4.5), new ScalarCurve.Key(215, 4.5)));
        var midExpand = new ScalarCurve(List.of(
                new ScalarCurve.Key(0, 1.0), new ScalarCurve.Key(25, 8.5), new ScalarCurve.Key(215, 8.5)));
        var outerExpand = new ScalarCurve(List.of(
                new ScalarCurve.Key(0, 1.0), new ScalarCurve.Key(25, 12.5), new ScalarCurve.Key(215, 12.5)));
        var innerShell = MemberProgram.attack("innerMoon",
                        MemberProgram.styled(new Patterns.Shell(3, 7, 2.0, Math.toRadians(75), 0),
                                "sphere", VIOLET, 0.28, 1.0, 0.56, "innerMoon"),
                        new Paths.Hold(), 215)
                .fixedDirection().piercing()
                .transform(new Transforms.Compose(List.of(
                        new Transforms.Scale(innerExpand),
                        new Transforms.Rotate(Vec3.Y, ramp(0, Math.toRadians(120), 215), Vec3.ZERO))))
                .presentation(sound(215, "warn_shift", "dark-moon-shell"))
                .hiddenCycle(TimeDomain.MEMBER_AGE, 100, 50, 0)
                .build();
        var midShell = MemberProgram.attack("midMoon",
                        MemberProgram.styled(new Patterns.Shell(3, 7, 2.0, Math.toRadians(75), Math.PI * 2 / 3),
                                "sphere", INDIGO, 0.28, 1.0, 0.56, "midMoon"),
                        new Paths.Hold(), 215)
                .fixedDirection().piercing()
                .transform(new Transforms.Compose(List.of(
                        new Transforms.Scale(midExpand),
                        new Transforms.Rotate(Vec3.Y, ramp(0, Math.toRadians(-100), 215), Vec3.ZERO))))
                .hiddenCycle(TimeDomain.MEMBER_AGE, 100, 50, 50)
                .build();
        var outerShell = MemberProgram.attack("outerMoon",
                        MemberProgram.styled(new Patterns.Shell(3, 7, 2.0, Math.toRadians(75), Math.PI * 4 / 3),
                                "sphere", SILVER, 0.28, 1.0, 0.56, "outerMoon"),
                        new Paths.Hold(), 215)
                .fixedDirection().piercing()
                .transform(new Transforms.Compose(List.of(
                        new Transforms.Scale(outerExpand),
                        new Transforms.Rotate(Vec3.Y, ramp(0, Math.toRadians(80), 215), Vec3.ZERO))))
                .hiddenCycle(TimeDomain.MEMBER_AGE, 100, 50, 0)
                .build();

        c.at(0, q -> {
            var targets = q.targets("moonShellTargets", 64, 5);
            var innerArena = q.arena("innerMoonArena", targets);
            var midArena = q.arena("midMoonArena", targets);
            var outerArena = q.arena("outerMoonArena", targets);
            q.lockMovement(innerArena, 220);
            q.fire(innerArena, innerShell, 20, 4100);
            q.fire(midArena, midShell, 20, 4101);
            q.fire(outerArena, outerShell, 20, 4102);
        });

        // 轨 B：3 颗暗金星棱减速悬停于月壳之间，各分裂为 6 颗慢速珠（含 1 颗直推金珠）
        var splitPattern = new Patterns.Union(List.of(
                MemberProgram.styled(new Patterns.SphereDirections(5, Patterns.SphereDistribution.FIBONACCI, 41),
                        "sphere", SILVER, 0.22, 1.0, 0.44, "shard"),
                MemberProgram.styled(new Patterns.Point(Vec3.ZERO, Vec3.Z),
                        "sphere", GOLD, 0.24, 1.0, 0.48, "shardAim")));
        var splitChild = MemberProgram.attack("eclipseShard", splitPattern, new Paths.Linear(Vec3.Z, 0.32), 95)
                .templateDirections()
                .build();
        var seedPath = new Paths.Segments(List.of(
                new Paths.Segment("fly", 35, new Paths.SpeedCurve(Vec3.Z, ramp(0.62, 0.0, 35))),
                new Paths.Segment("pause", 15, new Paths.Hold())),
                Paths.Tail.HOLD);
        var eclipseSeed = MemberProgram.attack("eclipseSeed",
                        MemberProgram.styled(new Patterns.Fan(3, Math.toRadians(44), 0),
                                "star_prism", GOLD, 0.35, 1.1, 0.70, "seed"),
                        seedPath, 60)
                .templateDirections()
                .replace(new Selection.Tag("seed"), new Actions.AtAge(50), splitChild, Actions.OnReject.END_PARENT)
                .presentation(sound(60, "boss_burst", "eclipse-seed"))
                .build();

        for (int wave = 0; wave < 3; wave++) {
            int t = 15 + wave * 75;
            int w = wave;
            c.at(t, q -> {
                var targets = q.targets("eclipseTargets" + w, 48, 5);
                for (int slot = 0; slot < 5; slot++) {
                    var group = q.aimed("eclipse" + w + "_" + slot, targets, slot, new Vec3(0, 0.6, 0));
                    q.fire(group, eclipseSeed, 0, 4200 + t + slot);
                }
            });
        }

        return finish(4, c,
                "定位：露米娅招牌明暗相位卡，三层月壳覆盖近/中/远全距。",
                "轨A（限位）：半径 9/17/25 格三层稀疏月壳反向公转，每 2.5 秒反相明暗交替（半透明无碰撞 ↔ 实体有判定）。",
                "轨B（逼位）：每 3.75 秒抛出 3 颗暗金星棱，飞停后各裂成 6 颗慢速珠，专打停在壳缝里等翻转的人。",
                "解法：借半透明无伤期直接穿壳换区，拉远圈同样需读外层 25 格月壳的明暗节拍。",
                "节奏：公共三壳仅 63 颗且多人不复制，辨识度与空间层次拉满。");
    }

    // ------------------------------------------------------------------
    // 5 · 夜符「子夜之鸟」
    //     轨 A（全场限位）：三倾角交替全向外扩星环（12 颗 × 5 波，飞满 33 格），封死绕圈与升降
    //     轨 B（点名逼位）：双弧 20 颗夜鸟群悬停蓄势 → reaim 整群扑杀 + 2 道交叉月光
    // ------------------------------------------------------------------
    private static Pattern flockArc(int side, String color) {
        List<Pattern> birds = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            double u = i / 9.0;
            double angle = Math.toRadians(15 + u * 110);
            double x = side * (1.4 + Math.sin(angle) * 4.2);
            double y = 0.6 + Math.cos(angle) * 2.8;
            double z = -0.8 * Math.sin(u * Math.PI);
            Vec3 spreadDir = new Vec3(side * (0.65 + 0.35 * Math.sin(angle)), 0.35 * Math.cos(angle), 0.25).unit();
            birds.add(MemberProgram.styled(
                    new Patterns.Point(new Vec3(x, y, z), spreadDir),
                    i % 3 == 0 ? "star_prism" : "sphere",
                    color, 0.25, 1.15, 0.50, "flock"));
        }
        return new Patterns.Union(birds);
    }

    private static BossProgramCards.Card midnightBird() {
        var c = new CardProgram("gensokyou:rumia_midnight_bird");

        // 轨 A：三倾角交替全向外扩星环（公共一份，封死水平绕圈与垂直升降）
        double[] tilts = {0, 36, -36, 22, -22};
        for (int wave = 0; wave < 5; wave++) {
            int t = wave * 48;
            int w = wave;
            var starRing = MemberProgram.attack("midnightStarRing" + w,
                            MemberProgram.styled(new Patterns.Radial(12, 0, w * 0.26),
                                    "sphere", INDIGO, 0.24, 1.0, 0.48, "starRing"),
                            new Paths.Linear(Vec3.Z, 0.25), 135)
                    .templateDirections().piercing()
                    .transform(new Transforms.Rotate(Vec3.X, ScalarCurve.constant(Math.toRadians(tilts[w])), Vec3.ZERO))
                    .build();
            c.at(t, q -> {
                var targets = q.targets("starRingTargets" + w, 64, 5);
                var arena = q.arena("starRingArena" + w, targets);
                q.fire(arena, starRing, 0, 5000 + t);
            });
        }

        // 轨 B：双弧夜鸟群展开悬停蓄势 → reaim 整群锁点扑杀 + 2 道交叉月光
        var flockPath = new Paths.Segments(List.of(
                new Paths.Segment("spread", 35, new Paths.SpeedCurve(Vec3.Z, ramp(0.22, 0.0, 35))),
                new Paths.Segment("hover", 115, new Paths.Hold())),
                Paths.Tail.HOLD);
        var leftFlock = MemberProgram.attack("midnightLeft", flockArc(-1, VIOLET), flockPath, 150)
                .templateDirections()
                .presentation(sound(150, "boss_burst", "midnight-spread"))
                .build();
        var rightFlock = MemberProgram.attack("midnightRight", flockArc(1, CRIMSON), flockPath, 150)
                .templateDirections()
                .build();

        var crossPattern = MemberProgram.styled(
                new Patterns.Union(List.of(
                        new Patterns.Point(new Vec3(-4.5, 1.2, 0), Vec3.Z),
                        new Patterns.Point(new Vec3(4.5, -0.6, 0), Vec3.Z))),
                "laser", SILVER, 0.25, 1.15, 0.35, "midnightCross");
        var crossBeams = MemberProgram.attack("midnightCross", crossPattern, new Paths.Hold(), 22)
                .aimAtTarget().laser(42, true, 10)
                .presentation(sound(22, "warn_shift", "midnight-cross"))
                .build();

        for (int wave = 0; wave < 2; wave++) {
            int t = wave * 120;
            int w = wave;
            List<CardProgram.Group> leftGroups = new ArrayList<>();
            List<CardProgram.Group> rightGroups = new ArrayList<>();

            c.at(t, q -> {
                var targets = q.targets("midnightTargets" + w, 48, 5);
                for (int slot = 0; slot < 5; slot++) {
                    var left = q.aimed("midnightL" + w + "_" + slot, targets, slot, new Vec3(0, 1.0, 0));
                    var right = q.aimed("midnightR" + w + "_" + slot, targets, slot, new Vec3(0, 1.0, 0));
                    leftGroups.add(left);
                    rightGroups.add(right);
                    q.fire(left, leftFlock, 0, 5100 + t + slot);
                    q.fire(right, rightFlock, 0, 5200 + t + slot);
                    if (slot == 0) q.lockMovement(left, 65);
                }
            });

            c.at(t + 25, q -> {
                var targets = q.targets("midnightCrossTargets" + w, 48, 5);
                for (int slot = 0; slot < 5; slot++) {
                    var crossGroup = q.aimed("midnightCross" + w + "_" + slot, targets, slot, new Vec3(0, 0.6, 0));
                    q.fire(crossGroup, crossBeams, 33, 5300 + t + slot);
                }
            });

            c.at(t + 58, q -> {
                for (int slot = 0; slot < 5; slot++) {
                    q.reaim(leftGroups.get(slot), slot, new Selection.Tag("flock"), 0.56);
                    q.reaim(rightGroups.get(slot), slot, new Selection.Tag("flock"), 0.56);
                }
            });
        }

        return finish(5, c,
                "定位：终符双轨收束——三倾角星环持续压场 + 蓄势群翼扑杀高潮。",
                "轨A（限位）：每 2.4 秒按 0°/±36°/±22° 倾角交替推出 12 颗全向外扩星环（飞满 33 格），封死绕圈与升降。",
                "轨B（逼位）：每 6 秒展开 20 颗对称夜鸟群悬停蓄势并同步预亮 2 道交叉月光，随后整群锁点扑射。",
                "解法：日常随倾角星环微调高度，见群翼悬停后先引诱锁点，交叉光亮起瞬间大步侧切。",
                "节奏：12 秒循环 2 大波高潮，公共星环稀疏稳态，整体保持 T2 清晰可读。");
    }

    public static BossProgramCards.Deck deck() {
        return new BossProgramCards.Deck("rumia", null,
                List.of(moonlightRay(), nightBird(), demarcation(), darkSideOfTheMoon(), midnightBird()),
                List.of("谱面v4.1（T2 · 约 35% 难度）：轨A广域3D限位 + 轨B点名逼位，小判定大视觉。"));
    }

    public static Map<Integer, String> checkpoints(int card) {
        return switch (card) {
            case 1 -> ordered(36, "首波立体月光网激活", 50, "交叉冷金点名 + 弧扇", 116, "二波光网与点名叠拍", 196, "三波光网收束");
            case 2 -> ordered(24, "全向巡游鸟 + 左紫翼", 52, "飞羽滞留 + 右绯翼点射", 112, "二对弧翼穿羽空域", 192, "三对双翼收尾");
            case 3 -> ordered(20, "首层膨胀球壳 + 大缺口环", 80, "二层球壳与右缺口环", 140, "三环（下缺口）穿壳", 195, "末环（左缺口）");
            case 4 -> ordered(25, "内/外实 · 中壳虚", 75, "明暗翻转 + 星棱分裂", 125, "二次翻转", 175, "末波裂珠");
            case 5 -> ordered(25, "倾角星环 + 夜鸟悬停", 58, "交叉光激活 + 整群扑杀", 145, "二波夜鸟展开", 178, "二波扑杀收口");
            default -> throw new IllegalArgumentException("Rumia card 1..5 required");
        };
    }

    private static Map<Integer, String> ordered(int t1, String l1, int t2, String l2,
                                                int t3, String l3, int t4, String l4) {
        var map = new LinkedHashMap<Integer, String>();
        map.put(t1, l1);
        map.put(t2, l2);
        map.put(t3, l3);
        map.put(t4, l4);
        return map;
    }
}
