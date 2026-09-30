package com.bitsson.gensokyou.danmaku.track;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * <b>读档后符卡阶段正确落位</b>的回归测试。
 *
 * <p><b>这个 bug 的形状</b>：读档时 {@code TrackRunner} 被重建。若重建代码预设
 * {@code selectCard(1.0D)}，{@code current} 就落在首卡且 {@code tick == 0}；随后
 * {@code syncCard()} 按实际血量选卡，若血量已低于首卡门槛，本该落的那张会走进
 * <b>挂起切卡</b>分支（当前卡声明了循环长度、循环尚未走完一个周期）——被记进
 * {@code pending}，要等整个循环走完（big_fairy 是 240 tick = 12 秒）才切。
 *
 * <p>玩家看到的是：读档后符卡名回到最初状态、且<b>真的在放首卡的弹幕</b>，
 * 十几秒后才跳到正确阶段。
 *
 * <p>正解是<b>不预设</b>：{@link TrackRunner#selectCard} 在 {@code current == null}
 * 时无条件切换（不走挂起分支），于是紧随其后的 {@code syncCard()} 一次选对。
 * 本测试钉住的就是这一条——它<b>不需要世界或实体</b>，纯查表逻辑。
 */
class RunnerReloadCardPlacementTest {

    /**
     * 一张<b>声明了循环长度</b>的卡——挂起切卡分支的前提，缺了它测不出这个 bug。
     *
     * <p>{@code cycleTicks > 0} + {@code terminates()}（即每周期有终止），两者同时满足
     * 才会让 {@code cycleFinished()} 在 tick=0 时返回 false。
     */
    private static SpellCard cycling(String name, double start) {
        return new SpellCard(Component.literal(name), start,
                List.of(Track.of("t", 0xFFFFFF)
                        .terminates().repeatEvery(40)
                        .at(0, Shape.RING_FACING, Shape.Params.defaults()
                                .count(10).gap(60.0D).speed(0.3D), TargetMode.SELF_AXIS)
                        .build()),
                240, 0.0D);
    }

    private static List<SpellCard> table() {
        return List.of(cycling("A", 1.00D), cycling("B", 0.66D), cycling("C", 0.33D));
    }

    @Test
    void freshRunnerLandsOnTheCardMatchingHealthImmediately() {
        // 模拟读档：runner 全新（current == null），BOSS 血量只剩 40%
        TrackRunner runner = new TrackRunner(table(), SignaturePalette.of(0xFFFFFF));
        assertEquals(-1, runner.currentIndex(), "新 runner 不应已有当前卡");

        runner.selectCard(0.40D);

        // 0.40 落在 B 的区间 [0.33, 0.66)
        assertEquals(1, runner.currentIndex(),
                "血量 40% 必须直接落到 B；预设首卡会让它挂起 12 秒");
        assertEquals("B", runner.current().name().getString());
    }

    @Test
    void preselectingFirstCardWouldHaveHung() {
        // 反证：证明「预设首卡」这条老路确实会被挂起，否则上面的测试是假阳性
        TrackRunner runner = new TrackRunner(table(), SignaturePalette.of(0xFFFFFF));
        runner.selectCard(1.0D);
        assertEquals(0, runner.currentIndex());

        boolean switched = runner.selectCard(0.40D);
        assertFalse(switched, "预设首卡后 40% 血量 MUST NOT 立即切换");
        assertEquals(0, runner.currentIndex(), "此时仍停在首卡——这就是那个 bug");
        assertNotEquals(1, runner.currentIndex());
    }

    // 「挂起会在循环边界后自愈」这条<b>不测</b>：让 tick 前进只能靠
    // TrackRunner#tick(boss, targets, damage)，而它要一个 LivingEntity + ServerLevel，
    // 无世界无实体的单测里推不动 tick（tick 恒为 0 ⇒ cycleFinished() 恒 false）。
    // 为一条「读代码即可确认」的事实硬凑一个跑不动的测试，只会让它变成假阳性来源。
}
