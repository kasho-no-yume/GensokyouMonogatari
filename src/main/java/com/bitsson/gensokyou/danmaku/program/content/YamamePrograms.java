package com.bitsson.gensokyou.danmaku.program.content;

import com.bitsson.gensokyou.danmaku.program.TailPolicy;
import com.bitsson.gensokyou.danmaku.program.author.CardProgram;
import com.bitsson.gensokyou.danmaku.program.author.MemberProgram;
import com.bitsson.gensokyou.danmaku.program.math.Vec3;
import com.bitsson.gensokyou.danmaku.program.ops.*;
import com.bitsson.gensokyou.danmaku.track.BossCards;
import java.util.*;

/**
 * 黑谷山女正式程序内容（tasks 1.3）。
 *
 * <p>四张卡 + 蛛丝扇底噪，与旧 {@code BossCards.yamame()} 同下标/同语言键。
 *
 * <p><b>子拍修正</b>：旧外层调度只放周期 0 相位，导致 0/20/40 子拍丢失（content-mapping
 * §3）。新程序用「周期内多拍显式展开」表达，卡 1 每 200 tick 一轮共 10 个织丝拍
 * （0..180 步 20，60 根），卡 3 每 260 tick 共 13 拍（0..240 步 20，91 根）。
 *
 * <p>尾珠：母弹具名路径 hold40 → 随机方向缓慢漂移，寿命 400；母弹提前死亡即停发后续。
 */
public final class YamamePrograms {
    private YamamePrograms() {}

    private static final int SPINNER_LIFETIME = 100;
    // The latest pearl is emitted at carrier age 96. Keeping the pearl for
    // Keep the authored 400-tick tail lifetime while measuring the real source
    // of the reported early hitch. A shorter lifetime changes the authored
    // density and cannot distinguish startup cost from accumulated load.
    private static final int NODE_LIFETIME = 400;
    private static final int NODE_HOLD = 40;
    private static final double NODE_DRIFT = 0.06D;
    private static final int TRAIL_PERIOD = 4;
    private static final int TRAIL_COUNT = 24;

    private static String hex(int index) { return BossProgramCards.hex(BossCards.YAMAME_PALETTE.at(index)); }

    private static Actions.ChildProgram pearl() {
        var pattern = MemberProgram.styled(new Patterns.Point(Vec3.ZERO, Vec3.Z),
                "sphere", hex(0), 0.275, 1, 0.275, "pearl");
        var drift = new Paths.Segments(List.of(
                new Paths.Segment("hold", NODE_HOLD, new Paths.Hold()),
                new Paths.Segment("drift", NODE_LIFETIME - NODE_HOLD, new Paths.Linear(Vec3.Z, NODE_DRIFT))),
                Paths.Tail.HOLD);
        return MemberProgram.attack("pearl", pattern, drift, NODE_LIFETIME).randomDirections(17).build();
    }

    /** 引线母弹：FAN/CONE 散射的丝头，挂尾珠派生。 */
    private static Actions.ChildProgram spinner(String name, int count, double coneRadians, double speed, double size) {
        var styled = MemberProgram.styled(new Patterns.ConeScatter(count, coneRadians, 1),
                "sphere", name.equals("nest") ? hex(1) : hex(0), size * 0.5, 1, size, "carrier");
        return MemberProgram.attack(name, styled, new Paths.Linear(Vec3.Z, speed), SPINNER_LIFETIME)
                .templateDirections().piercing()
                .emit(new Selection.Tag("carrier"), TRAIL_PERIOD, TRAIL_PERIOD, TRAIL_COUNT, pearl())
                .build();
    }

    public static CardProgram.Compiled background() {
        var card = new CardProgram("gensokyou:yamame_web_shot");
        var targets = card.targets("players", 64, 5);
        var gun = card.arena("web", targets);
        var pattern = MemberProgram.styled(new Patterns.Fan(3, Math.toRadians(55), 0),
                "sphere", hex(0), 0.275, 1, 0.55, "web");
        var bullets = MemberProgram.attack("web", pattern, new Paths.Linear(Vec3.Z, 0.35), 1200)
                .templateDirections().build();
        card.at(0, c -> c.fire(gun, bullets, 0, 43));
        return card.loop(80, TailPolicy.END_AT_BOUNDARY);
    }

    public static BossProgramCards.Deck deck() {
        return new BossProgramCards.Deck("yamame", background(),
                List.of(web(), miasma(), nest(), fever()),
                List.of("子拍按周期内显式展开；尾珠派生 24 次/母弹（age 4..96），母弹死则停。",
                        "内容映射 §3 的子拍修复在此落实；旧实现只放周期 0 相位导致子拍丢失。"));
    }

    // 1 · 罠符「捕获之网」：每 200 tick 10 个织丝拍 + 每 70 tick 个人追丝扇
    private static BossProgramCards.Card web() {
        var card = new CardProgram("gensokyou:yamame_web_card");
        var targets = card.targets("players", 64, 5);
        var weave = card.arena("weave", targets);
        var chase = card.aimed("chase", targets, 0, new Vec3(0, 1, 0));
        var spinners = spinner("web", 6, Math.PI, 0.70D, 0.75D);
        var chasers = MemberProgram.attack("chase", MemberProgram.styled(new Patterns.Fan(3, Math.toRadians(24), 0),
                        "sphere", hex(1), 0.2, 1, 0.4, "chase"), new Paths.Linear(Vec3.Z, 0.5), 120)
                .templateDirections().build();
        for (int beat = 0; beat < 10; beat++) {
            int tick = beat * 20;
            card.at(tick, c -> c.fire(weave, spinners, 0, 51 + tick));
        }
        for (int i = 0; i < 3; i++) {
            int tick = i * 70;
            card.at(tick, c -> c.fire(chase, chasers, 0, 83 + tick));
        }
        card.at(0, c -> c.lockMovement(weave, 100));
        return new BossProgramCards.Card(1, "spellcard.gensokyou.yamame.1", 1.00D, 200, 0,
                card.loop(200, TailPolicy.RETAIN_UNTIL_LIFETIME),
                List.of("子拍 0/20/40 每 60 tick：10 拍/轮（0..180），60 根/轮，符合内容映射。"));
    }

    // 2 · 瘴符「瘴气充溢」：每名目标一份中央螺旋呼吸 + 毒雾缓速
    private static BossProgramCards.Card miasma() {
        var card = new CardProgram("gensokyou:yamame_miasma");
        var targets = card.targets("players", 64, 5);
        var mist = card.arena("mist", targets);
        var spiral = MemberProgram.attack("flow", MemberProgram.styled(new Patterns.Spiral(20, 1, 3, 8, 1, 0),
                        "sphere", hex(2), 0.275, 1, 0.55, "flow"), new Paths.Hold(), 400)
                .fixedDirection().piercing()
                .transform(new Transforms.Compose(List.of(new Transforms.Breathe(1, 0.4D, 80, 0),
                        new Transforms.Translate(new Paths.Linear(Vec3.Z, 0.36D)))))
                .build();
        var mists = MemberProgram.attack("mist", MemberProgram.styled(new Patterns.ConeScatter(7, Math.PI, 3),
                        "sphere", hex(3), 0.25, 1, 0.5, "mist"), new Paths.Linear(Vec3.Z, 0.12), 400)
                .templateDirections().piercing().build();
        for (int wave = 0; wave < 5; wave++) {
            int tick = wave * 60;
            int w = wave;
            card.at(tick, c -> {
                // 每波、每名被锁定玩家各建一个精确朝向该玩家的组；多人 = 每人一份，不是每人五份。
                for (int slot = 0; slot < 5; slot++) {
                    int s = slot;
                    var flow = c.aimed("flow" + w + "_" + s, targets, s, Vec3.ZERO);
                    c.fire(flow, spiral, 0, 91 + tick + s);
                    if (w == 0 && s == 0) c.lockMovement(flow, 130);
                }
            });
        }
        for (int i = 0; i < 9; i++) {
            int tick = i * 30;
            card.at(tick, c -> c.fire(mist, mists, 0, 97 + tick));
        }
        return new BossProgramCards.Card(2, "spellcard.gensokyou.yamame.2", 0.75D, 260, 0,
                card.loop(260, TailPolicy.RETAIN_UNTIL_LIFETIME),
                List.of("螺旋每 60 tick 按目标槽各建一个 AIMED 组：多人时每人一份，方向精确取 BOSS 眼位→该玩家。",
                        "推进 0.36 格/tick；每波重建组，已生成的波保持出生时快照，新波才按当时位置/方向采样。",
                        "毒雾仍为公共 arena 组（本次不改）；前 130 tick 锁位。"));
    }

    // 3 · 蜘蛛「石窟蜘蛛巢」：更密织丝（13 拍/轮）+ 石窟吐丝
    private static BossProgramCards.Card nest() {
        var card = new CardProgram("gensokyou:yamame_nest");
        var targets = card.targets("players", 64, 5);
        var weave = card.arena("nestWeave", targets);
        var spit = card.arena("spit", targets);
        var spinners = spinner("nest", 7, Math.PI, 0.64D, 0.75D);
        var spits = MemberProgram.attack("spit", MemberProgram.styled(new Patterns.ConeScatter(6, Math.toRadians(60), 5),
                        "sphere", hex(0), 0.275, 1, 0.55, "spit"), new Paths.Linear(Vec3.Z, 0.45), 400)
                .templateDirections().piercing().build();
        for (int beat = 0; beat < 13; beat++) {
            int tick = beat * 20;
            card.at(tick, c -> c.fire(weave, spinners, 0, 101 + tick));
        }
        for (int i = 0; i < 6; i++) {
            int tick = i * 50;
            card.at(tick, c -> c.fire(spit, spits, 0, 131 + tick));
        }
        card.at(0, c -> c.lockMovement(weave, 130));
        return new BossProgramCards.Card(3, "spellcard.gensokyou.yamame.3", 0.50D, 260, 0,
                card.loop(260, TailPolicy.RETAIN_UNTIL_LIFETIME),
                List.of("子拍 13 拍/轮（0..240），91 根/轮；登记相较旧「只放周期 0 相位」的实际密度上升。",
                        "尾珠寿命 400 tick（保留原始内容）；性能归因通过开局/持续采样确认，不用寿命收束掩盖问题。",
                        "石窟吐丝用 120° 锥（spread=120 ⇒ 半角 60°）。"));
    }

    // 4 · 瘴气「原因不明之热病」：每名目标一份三重螺旋 + 更浓毒雾
    private static BossProgramCards.Card fever() {
        var card = new CardProgram("gensokyou:yamame_fever");
        var targets = card.targets("players", 64, 5);
        var mist = card.arena("feverMist", targets);
        var centre = MemberProgram.attack("feverCentre", MemberProgram.styled(new Patterns.Spiral(14, 1, 3, 9, 1, 0),
                        "sphere", hex(2), 0.25, 1.25, 0.5, "centre"), new Paths.Hold(), 400)
                .fixedDirection().piercing()
                .transform(new Transforms.Compose(List.of(new Transforms.Breathe(1, 0.45D, 70, 0),
                        new Transforms.Translate(new Paths.Linear(Vec3.Z, 0.40D)))))
                .build();
        var leftArm = MemberProgram.attack("feverLeft", MemberProgram.styled(new Patterns.Spiral(6, 1, 1.5, 4, 1, 0),
                        "sphere", hex(4), 0.225, 1.25, 0.45, "left"), new Paths.Hold(), 400)
                .fixedDirection().piercing()
                .transform(new Transforms.Compose(List.of(new Transforms.Breathe(1, 0.4D, 60, 0),
                        new Transforms.Translate(new Paths.Linear(Vec3.Z, 0.44D)))))
                .build();
        var rightArm = MemberProgram.attack("feverRight", MemberProgram.styled(new Patterns.Spiral(6, 1, 1.5, 4, -1, 0),
                        "sphere", hex(3), 0.225, 1.25, 0.45, "right"), new Paths.Hold(), 400)
                .fixedDirection().piercing()
                .transform(new Transforms.Compose(List.of(new Transforms.Breathe(1, 0.4D, 60, 0),
                        new Transforms.Translate(new Paths.Linear(Vec3.Z, 0.44D)))))
                .build();
        var mists = MemberProgram.attack("feverMist", MemberProgram.styled(new Patterns.ConeScatter(7, Math.PI, 7),
                        "sphere", hex(5), 0.25, 1.25, 0.5, "mist"), new Paths.Linear(Vec3.Z, 0.12), 400)
                .templateDirections().piercing().build();
        for (int wave = 0; wave < 6; wave++) {
            int tick = wave * 50;
            int w = wave;
            card.at(tick, c -> {
                // 每人一份三重螺旋：同一玩家的左/右副螺旋从该玩家的中心组横向偏移。
                for (int slot = 0; slot < 5; slot++) {
                    int s = slot;
                    var base = c.aimed("feverBase" + w + "_" + s, targets, s, Vec3.ZERO);
                    var left = c.offset("feverLeft" + w + "_" + s, base, new Vec3(6, 0, 0));
                    var right = c.offset("feverRight" + w + "_" + s, base, new Vec3(-6, 0, 0));
                    c.fire(base, centre, 0, 151 + tick + s);
                    c.fire(left, leftArm, 0, 157 + tick + s);
                    c.fire(right, rightArm, 0, 163 + tick + s);
                    if (w == 0 && s == 0) c.lockMovement(base, 130);
                }
            });
        }
        for (int i = 0; i < 10; i++) {
            int tick = i * 26;
            card.at(tick, c -> c.fire(mist, mists, 0, 167 + tick));
        }
        return new BossProgramCards.Card(4, "spellcard.gensokyou.yamame.4", 0.25D, 260, 0,
                card.loop(260, TailPolicy.RETAIN_UNTIL_LIFETIME),
                List.of("每波、每目标槽各建 base + 左右偏移组：多人时每人一份三重螺旋，不互相叠五份。",
                        "推进 0.40 / 左右 0.44 格/tick；四轨伤害 1.25 倍；每波重建组，旧波保持出生快照。"));
    }
}
