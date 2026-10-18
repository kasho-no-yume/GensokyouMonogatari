package com.bitsson.gensokyou.danmaku.program.content;

import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.danmaku.program.TailPolicy;
import com.bitsson.gensokyou.danmaku.program.TimeDomain;
import com.bitsson.gensokyou.danmaku.program.author.CardProgram;
import com.bitsson.gensokyou.danmaku.program.author.MemberProgram;
import com.bitsson.gensokyou.danmaku.program.math.Deterministic;
import com.bitsson.gensokyou.danmaku.program.math.ScalarCurve;
import com.bitsson.gensokyou.danmaku.program.math.Vec3;
import com.bitsson.gensokyou.danmaku.program.ops.*;
import com.bitsson.gensokyou.danmaku.track.BossCards;
import net.neoforged.neoforge.common.ModConfigSpec;
import java.util.*;

/**
 * 大妖精正式程序内容（tasks 1.2）。
 *
 * <p>三张卡 + 5×5 默认弹墙，全部前向可读，与旧 {@code BossCards.bigFairy()} 同下标/同语言键。
 *
 * <p>雨季使用逐拍世界水平圆盘随机采样、向下首空气与地表锚定，不复用固定发射点。
 */
public final class BigFairyPrograms {
    private BigFairyPrograms() {}

    private static final int RING_CYCLE = 240;
    private static final int RING_HOLD = 60;
    private static final int FLOWER_CYCLE = 240;
    private static final int FLOWER_COUNT = 16;
    private static final int FLOWER_STEP = 4;
    private static final int FLOWER_RAMP = 60;
    private static final int FLOWER_BURST_AGE = 100;
    private static final int FLOWER_LIFETIME = 400;
    private static final int RAIN_CYCLE = 200;
    private static final double RAIN_HEIGHT = 60.0D;

    private static String hex(int index) { return BossProgramCards.hex(BossCards.BIG_FAIRY_PALETTE.at(index)); }

    /** 内容在首个 encounter 才构建（config 已加载）；单元测试未加载 config 时退回默认值。 */
    private static int cfg(ModConfigSpec.IntValue value) {
        try { return value.get(); } catch (IllegalStateException notLoaded) { return value.getDefault(); }
    }
    private static double cfg(ModConfigSpec.DoubleValue value) {
        try { return value.get(); } catch (IllegalStateException notLoaded) { return value.getDefault(); }
    }

    // ------------------------------------------------------------------
    // 默认攻击：5×5 角度网格弹墙（独立底噪，全程叠加在符卡之下）
    // ------------------------------------------------------------------
    public static CardProgram.Compiled wall() {
        var card = new CardProgram("gensokyou:big_fairy_wall");
        var targets = card.targets("players", 64, 5);
        var gun = card.aimed("wall", targets, 0, new Vec3(0, 1, 3));
        double step = Math.toRadians(cfg(GensokyouConfig.BIG_FAIRY_WALL_STEP_DEG));
        double size = cfg(GensokyouConfig.BIG_FAIRY_WALL_SIZE);
        var pattern = MemberProgram.styled(new Patterns.AngularGrid(5, 5, step, step),
                "sphere", hex(0), size * 0.5, 1, size, "wall");
        var bullets = MemberProgram.attack("wall", pattern,
                        new Paths.Linear(Vec3.Z, cfg(GensokyouConfig.BIG_FAIRY_WALL_SPEED)), 1200)
                .templateDirections().build();
        int interval = Math.max(5, cfg(GensokyouConfig.BIG_FAIRY_WALL_INTERVAL));
        card.at(0, c -> c.fire(gun, bullets, 0, 31));
        // 底噪永不结束；循环边界清旧轮顶层计划，新轮重新采样。
        return card.loop(interval, TailPolicy.RETAIN_UNTIL_LIFETIME);
    }

    public static BossProgramCards.Deck deck() {
        return new BossProgramCards.Deck("big_fairy", wall(),
                List.of(ring(), flower(), rain()),
                List.of("默认墙为中心朝主目标的角度网格（公共一份）；环卡按人复制；花海/雨卡公共。"));
    }

    // ------------------------------------------------------------------
    // 1 · 花符[弹幕花环]：单拍 48 颗共圆 → 静止 3 秒 → 重新瞄准所属玩家
    // ------------------------------------------------------------------
    private static BossProgramCards.Card ring() {
        var card = new CardProgram("gensokyou:big_fairy_ring");
        var targets = card.targets("players", 64, 5);
        List<CardProgram.Group> guns = new ArrayList<>();
        for (int slot = 0; slot < 5; slot++) {
            int s = slot;
            var group = card.aimed("ring" + s, targets, s, new Vec3(0, 1, -6));
            var pattern = MemberProgram.styled(new Patterns.Ring(48, 4, 0, 0),
                    "sphere", hex(1), 0.35, 1, 0.7, "ring");
            var members = MemberProgram.attack("ring", pattern, new Paths.Hold(), 400)
                    .fixedDirection()
                    .presentation(new Presentation.Profile(
                            List.of(new Presentation.Window(TimeDomain.MEMBER_AGE, RING_HOLD, 400)), null, null, List.of()))
                    .build();
            card.at(0, c -> c.fire(group, members, 0, 71 + s));
            card.at(RING_HOLD, c -> c.reaim(group, s, new Selection.Tag("ring"), 0.6));
            guns.add(group);
        }
        // 前 120 tick 锁位：48 颗共圆的圆心 MUST 在采样那一 tick 锁定。
        card.at(0, c -> c.lockMovement(guns.get(0), 120));
        // RETAIN：环弹寿命 400，跨轮保留直到自身寿命结束。
        return new BossProgramCards.Card(1, "spellcard.gensokyou.big_fairy.1", 1.00D,
                RING_CYCLE, 0, card.loop(RING_CYCLE, TailPolicy.RETAIN_UNTIL_LIFETIME),
                List.of("环弹寿命 400 跨轮保留（RETAIN）；伤害窗口 [60,400) 与内容映射一致。"));
    }

    // ------------------------------------------------------------------
    // 2 · 花符【花之海洋】：16 朵随机方向/随机花平面，飞 3 秒→停 2 秒→第 100 tick 爆散
    // ------------------------------------------------------------------
    private static BossProgramCards.Card flower() {
        var card = new CardProgram("gensokyou:big_fairy_flower");
        var seed = new Deterministic.Seed(73, List.of());
        List<CardProgram.Group> groups = new ArrayList<>();
        for (int i = 0; i < FLOWER_COUNT; i++) {
            int index = i;
            int birth = i * FLOWER_STEP;
            Vec3 axis = Deterministic.seedDirection(seed, 3, index, Deterministic.Distribution.UNIFORM_SPHERE, Vec3.Z, Math.PI);
            Vec3 plane = Deterministic.seedDirection(seed, 4, index, Deterministic.Distribution.UNIFORM_SPHERE, Vec3.Z, Math.PI);
            double planeAngle = seed.unit(5, index) * Deterministic.TAU;
            var spin = new Transforms.Rotate(plane, ScalarCurve.constant(planeAngle), Vec3.ZERO);
            var speed = new ScalarCurve(List.of(new ScalarCurve.Key(0, 0.24D), new ScalarCurve.Key(FLOWER_RAMP, 0.0D)));
            var motion = new Transforms.Compose(List.of(spin, new Transforms.Translate(new Paths.SpeedCurve(axis, speed))));
            var pattern = new Patterns.Union(List.of(
                    MemberProgram.styled(new Patterns.Point(Vec3.ZERO, Vec3.Z), "star_prism", hex(2), 0.55, 4, 1.1, "core"),
                    MemberProgram.styled(new Patterns.Rose(5, 9, 2, 1, 0), "sphere", hex(2), 0.275, 1, 0.55, "petals")));
            var members = MemberProgram.attack("flower" + index, pattern, new Paths.Hold(), FLOWER_LIFETIME)
                    .fixedDirection().transform(motion)
                    .presentation(new Presentation.Profile(
                            List.of(new Presentation.Window(TimeDomain.MEMBER_AGE, FLOWER_BURST_AGE, FLOWER_LIFETIME)), null, null, List.of()))
                    .build();
            var targets = card.targets("flowerT" + index, 64, 5);
            var group = card.arena("flower" + index, targets);
            groups.add(group);
            card.at(birth, c -> c.fire(group, members, 0, 47 + index));
            card.at(birth + FLOWER_BURST_AGE, c -> {
                c.release(group, new Selection.Tag("core"), new Selection.Tag("petals"), 0.2);
                c.reaim(group, -1, new Selection.Tag("core"), 0.3);
            });
        }
        return new BossProgramCards.Card(2, "spellcard.gensokyou.big_fairy.2", 0.66D,
                FLOWER_CYCLE, 0, card.loop(FLOWER_CYCLE, TailPolicy.RETAIN_UNTIL_LIFETIME),
                List.of("每轮 16 朵 × 46 成员；核心 4 倍伤害/直径 1.1，花瓣直径 .55。",
                        "爆散：花瓣沿径向 .2 脱离，核心采样随机有效目标 .3（reaim slot=-1）。",
                        "随机轴/随机花平面用 build 期确定性种子烘焙，契约与旧实现一致。"));
    }

    // ------------------------------------------------------------------
    // 3 · 夏符【雨季喷泉】：每秒一拍，雨（首空气面） + 光柱（地面/玩家脚下）
    // ------------------------------------------------------------------
    private static BossProgramCards.Card rain() {
        var card = new CardProgram("gensokyou:big_fairy_rain");
        var targets = card.targets("rainPlayers", 64, 5);
        var rainGun = card.arena("rain", targets);
        var fountain = card.arena("fountain", targets);
        var drops = MemberProgram.attack("rain", MemberProgram.styled(new Patterns.Point(Vec3.ZERO, Vec3.Z),
                        "sphere", hex(0), 0.4, 1, 0.8, "rain"), new Paths.Linear(Vec3.Z, 0.3), 400)
                .templateDirections().build();
        var beams = MemberProgram.attack("fountain", MemberProgram.styled(new Patterns.Point(Vec3.ZERO, Vec3.Z),
                         "laser", hex(5), 0.4, 1.1, 0.4, "beam"), new Paths.Hold(), 120)
                .templateDirections().laser(80, false, 10).build();
        for (int beat = 0; beat < 10; beat++) {
            int tick = beat * 20;
            final boolean playerGround = (beat % 5 == 4);
            card.at(tick, c -> {
                c.surfaceFireRandom(rainGun, "rainSurface" + tick, 24, 30, RAIN_HEIGHT,
                        com.bitsson.gensokyou.danmaku.program.ops.AuthorityDescriptions.SurfaceMode.FIRST_AIR_BELOW,
                         80, 512, drops, 0, 61 + tick, Vec3.Y.multiply(-1));
                if (playerGround) {
                    c.surfaceFireAtTargets(fountain, "pillarPlayer" + tick,
                            com.bitsson.gensokyou.danmaku.program.ops.AuthorityDescriptions.TargetPoint.FEET,
                            com.bitsson.gensokyou.danmaku.program.ops.AuthorityDescriptions.SurfaceMode.DOWNWARD_SOLID_TOP,
                            80, 256, beams, 40, 63 + tick, Vec3.Y, 5);
                } else {
                    c.surfaceFireRandom(fountain, "pillarGround" + tick, 1, 30, 0,
                            com.bitsson.gensokyou.danmaku.program.ops.AuthorityDescriptions.SurfaceMode.DOWNWARD_SOLID_TOP,
                             80, 80, beams, 40, 63 + tick, Vec3.Y);
                }
            });
        }
        return new BossProgramCards.Card(3, "spellcard.gensokyou.big_fairy.3", 0.33D,
                RAIN_CYCLE, 800, card.loop(RAIN_CYCLE, TailPolicy.RETAIN_UNTIL_LIFETIME),
                List.of("雨：每拍在半径30、BOSS上方60格重新随机取24点，每点一弹，FIRST_AIR_BELOW解析后竖直下落。",
                        "光柱：DOWNWARD_SOLID_TOP 解析地面点后竖直向上；每第 5 拍（index 4/9）改为逐玩家脚下（PLAYER_GROUND 等价）。",
                        "每次 surfaceFire 只建 1 个普通组（解析点作为显式成员表），与 legacy 一样跨轮保留到自身寿命。"));
    }
}
