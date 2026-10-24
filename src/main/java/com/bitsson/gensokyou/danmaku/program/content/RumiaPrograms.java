package com.bitsson.gensokyou.danmaku.program.content;

import com.bitsson.gensokyou.danmaku.program.TailPolicy;
import com.bitsson.gensokyou.danmaku.program.author.CardProgram;
import com.bitsson.gensokyou.danmaku.program.author.MemberProgram;
import com.bitsson.gensokyou.danmaku.program.math.Vec3;
import com.bitsson.gensokyou.danmaku.program.ops.Patterns;
import com.bitsson.gensokyou.danmaku.program.ops.Paths;

import java.util.List;

/**
 * 露米娅正式程序内容（add-rumia-tier1-final-boss）。
 *
 * <p>四张卡使用官方卡名与血量阈值 1.00 / 0.75 / 0.50 / 0.25：
 * 月符「月光射线」/ 夜符「夜鸟」/ 暗符「境界线」/ 暗符「月之暗面」。
 *
 * <p><b>TODO: user-supplied performance.</b> 本类当前只提供四张最小占位卡：
 * 每张一条最慢、最稀疏的直瞄弹，用来验证「BOSS 会切卡、会发弹、不会结束 encounter」。
 * 四张卡的正式分轨、参数、循环与演出由用户另行设计；占位弹不得被当作最终内容验收。
 */
public final class RumiaPrograms {
    private RumiaPrograms() {}

    /** 占位卡循环；用户正式设计后可逐卡改写。 */
    private static final int PLACEHOLDER_CYCLE = 240;

    /**
     * 最小占位卡：一条直瞄慢弹。
     *
     * @param index   1 起卡序
     * @param hp      起始血量占比
     * @param id      程序定义 id
     * @param nameKey 语言键
     * @param color   占位弹颜色；只用于区分四张卡，不承担正式视觉身份
     */
    private static BossProgramCards.Card placeholder(int index, double hp, String id,
                                                     String nameKey, String color) {
        var card = new CardProgram(id);
        var targets = card.targets("players", 64, 5);
        var gun = card.aimed("placeholder" + index, targets, 0, new Vec3(0, 1, 0));
        var bullet = MemberProgram.attack("placeholder" + index,
                        MemberProgram.styled(new Patterns.Point(Vec3.ZERO, Vec3.Z),
                                "sphere", color, 0.2, 1, 0.4, "placeholder"),
                        new Paths.Linear(Vec3.Z, 0.18), 120)
                .aimAtTarget()
                .build();
        card.at(0, c -> c.fire(gun, bullet, 0, 211 + index));
        return new BossProgramCards.Card(index, nameKey, hp, PLACEHOLDER_CYCLE, 0,
                card.loop(PLACEHOLDER_CYCLE, TailPolicy.RETAIN_UNTIL_LIFETIME),
                List.of("TODO: user-supplied performance — minimal placeholder only."));
    }

    public static BossProgramCards.Deck deck() {
        return new BossProgramCards.Deck("rumia", null,
                List.of(
                        placeholder(1, 1.00D, "gensokyou:rumia_moonlight_ray",
                                "spellcard.gensokyou.rumia.1", "#BFD8FF"),
                        placeholder(2, 0.75D, "gensokyou:rumia_night_bird",
                                "spellcard.gensokyou.rumia.2", "#9C7BFF"),
                        placeholder(3, 0.50D, "gensokyou:rumia_demarcation",
                                "spellcard.gensokyou.rumia.3", "#D0433C"),
                        placeholder(4, 0.25D, "gensokyou:rumia_dark_side_of_the_moon",
                                "spellcard.gensokyou.rumia.4", "#7A5CFF")),
                List.of("4 张官方卡；表现待用户设计，占位程序非最终。",
                        "v1 不设独立底噪；四张卡自己承担连续性。",
                        "T1 最终：bossTier/referenceTier 均为 1，240s / 7 hits。"));
    }
}
