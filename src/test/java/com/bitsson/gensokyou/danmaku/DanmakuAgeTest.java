package com.bitsson.gensokyou.danmaku;

import com.bitsson.gensokyou.danmaku.motion.DanmakuAge;
import com.bitsson.gensokyou.danmaku.motion.FormationFrame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 弹体「年龄」的求值规则与存档判据。
 *
 * <p>本文件守的是 {@code danmaku-age-continuity} 修的那个缺陷的<b>核心不变量</b>：
 * 双端必须推进到<b>同一年龄</b>。原版 {@code Entity.tickCount} 既不写入存档、也不随生成包
 * 下发，两端只由 {@code Level.tickNonPassenger} 各自自增——于是客户端每「丢掉又重新拿到」
 * 这个实体就自 0 重数，而服务端那份一直在 tick，位置存了、年龄没存。
 *
 * <p>这些断言全都不需要世界：{@code ageAt} 与 {@code axisNeedsPersistence} 是抽出来的
 * 纯静态入口，与 {@code age()} 读同一个实现。
 */
class DanmakuAgeTest {

    /** 年龄钳位上限，与实现同步。 */
    private static final int MAX_AGE_TICKS = DanmakuAge.MAX_AGE_TICKS;

    // ------------------------------------------------------------------
    // 正常发射：MUST 与本进程内 tick 计数逐位相等
    // ------------------------------------------------------------------

    /**
     * 基准为 0 时年龄 MUST 逐位等于 tick 计数。
     *
     * <p>这条守的是「正常发射路径逐位不变」——绝大多数弹一生都不经存档，若这条不成立，
     * 本变更就会在<b>每一发</b>新弹幕上引入偏差，而不是只在读档后。
     */
    @Test
    void freshBulletAgeEqualsLocalTickCount() {
        for (int t = 0; t <= 1200; t++) {
            assertEquals(t, DanmakuAge.at(0, t),
                    "新发射弹在 tick=" + t + " 时的年龄必须逐位等于 tick 计数");
        }
    }

    // ------------------------------------------------------------------
    // 重新获取：基准把两端拉回同一年龄
    // ------------------------------------------------------------------

    /**
     * 重建弹的年龄 MUST 连续跨过「本地计数归零」那一刻。
     *
     * <p>模拟真实读档：实体在年龄 200 处被存下，读档后本地 tick 计数从 0 重新开始。
     * 若不补基准，读档后第一 tick 的年龄就是 1 而非 201，双端自变量从此差 200。
     */
    @Test
    void rebuiltBulletAgeContinuesAcrossLocalTickReset() {
        int savedAge = 200;
        assertEquals(201, DanmakuAge.at(savedAge, 1),
                "读档后首个 tick 的年龄必须是 201（200 + 1），而不是 1");
        assertEquals(1200, DanmakuAge.at(savedAge, 1000),
                "读档后第 1000 tick 的年龄必须是 1200");
    }

    /**
     * 两名客户端配对于不同时刻时，各自与服务端逐 tick 相等。
     *
     * <p>这是「基准每客户端一份」的根据：共享单一值必然弄坏其中一方。
     */
    @Test
    void twoClientsPairingAtDifferentAgesStayAligned() {
        int serverAgeWhenAPaired = 50;
        int serverAgeWhenBPaired = 120;

        // 服务端自其年龄 120 起再走 30 tick
        for (int k = 0; k <= 30; k++) {
            int serverAge = serverAgeWhenBPaired + k;

            // A 的基准是 50，本地计数已走过 70 + k
            int aAge = DanmakuAge.at(serverAgeWhenAPaired, 70 + k);
            // B 的基准是 120，本地计数刚走 k
            int bAge = DanmakuAge.at(serverAgeWhenBPaired, k);

            assertEquals(serverAge, aAge, "A 在服务端年龄 " + serverAge + " 时必须同相");
            assertEquals(serverAge, bAge, "B 在服务端年龄 " + serverAge + " 时必须同相");
        }
    }

    // ------------------------------------------------------------------
    // 钳位与缺省
    // ------------------------------------------------------------------

    @Test
    void ageIsClampedToMax() {
        assertEquals(MAX_AGE_TICKS,
                DanmakuAge.at(MAX_AGE_TICKS, 1000),
                "年龄 MUST 钳在上限，不得 int 溢出翻负");
        assertEquals(MAX_AGE_TICKS, DanmakuAge.at(Integer.MAX_VALUE, Integer.MAX_VALUE),
                "两个极大值相加 MUST 仍是上限，而不是溢出后的负数");
    }

    @Test
    void negativeInputIsFlooredAtZero() {
        assertEquals(0, DanmakuAge.at(-5, 0),
                "负基准 MUST 归零，不得产生负年龄");
        assertEquals(0, DanmakuAge.at(0, -5),
                "负 tick 计数 MUST 归零");
    }

    /**
     * 旧存档缺「Age」键时 MUST 退化为基准 0，而不是报错。
     *
     * <p>退化的后果是「读档得到的飞行中弹行为与本变更之前相同」——不比迁移前更差，
     * 故这是可接受的缺省，而不是待修的缺陷。
     */
    @Test
    void missingAgeKeyDegradesToZeroBasis() {
        // CompoundTag.getInt 对不存在的键返回 0，与「键存在且值为 0」不可区分——这正是想要的缺省。
        assertEquals(0, DanmakuAge.at(0, 0));
        assertEquals(1200, DanmakuAge.at(0, 1200));
    }

    // ------------------------------------------------------------------
    // 方向轴的存档条件
    // ------------------------------------------------------------------

    /**
     * 「有帧、无曲线」的方向轴 MUST 也入存档。
     *
     * <p><b>本条守的是已发生过的 bug</b>：写盘条件曾窄到只有 {@code hasSpeedProfile()}，
     * 而 {@code bindToFrame} 同样写方向轴。于是这类弹读档后回落默认 {@code (0,0,1)}，
     * 沿弹道推进项指向世界 +Z——现象是「整队弹读档后朝一个方向平移」，日志干净无报错。
     */
    @Test
    void formationFrameWithoutSpeedProfileStillPersistsAxis() {
        assertTrue(DanmakuAge.axisNeedsPersistence(false, true),
                "挂编队帧的弹 MUST 存方向轴，哪怕它没挂速率曲线");
    }

    @Test
    void speedProfileWithoutFrameAlsoPersistsAxis() {
        assertTrue(DanmakuAge.axisNeedsPersistence(true, false),
                "挂速率曲线的弹 MUST 存方向轴（返程弹靠它恢复反向）");
    }

    @Test
    void plainBulletNeedsNoAxis() {
        assertFalse(DanmakuAge.axisNeedsPersistence(false, false),
                "两者都没挂的弹不需要方向轴，写不写都无差别");
    }

    // ------------------------------------------------------------------
    // 编队帧求值用 t=0（出生当 tick）
    // ------------------------------------------------------------------

    /**
     * 出生当 tick MUST 取 {@code framePositionAt(0)}，不是 {@code (1)}。
     *
     * <p>这条守的是 1.21.1 的 tick 自增位置：{@code Entity.tick()} 与 {@code baseTick()} 都不
     * 自增 {@code tickCount}，自增发生在 {@code Level.tickNonPassenger} 调
     * {@code entity.tick()} <b>之前</b>。故体内 {@code tickCount} 已是本 tick 编号，
     * 再加一会让整条编队轨迹偏一 tick，且「出生即收拢」这个既定语义失效。
     */
    @Test
    void birthTickEvaluatesFrameAtZero() {
        // 缩放在 t=0 取最小值（收拢），t=1 已张开一点
        FormationFrame breathing = new FormationFrame(0, 0, 0,
                2, 0, 0,
                0, FormationFrame.HORIZONTAL_PITCH_DEG, 0.0D,
                1.0D, 0.5D, 40.0D);
        double atZero = breathing.scaleAt(0);
        double atOne = breathing.scaleAt(1);

        assertTrue(atZero < 1.0D, "t=0 MUST 处于收拢端（scaleAt 约定 t=0 取最小值）");
        assertTrue(atOne > atZero, "t=1 MUST 已开始张开，故与 t=0 MUST 有可测差异");
    }
}
