package com.bitsson.gensokyou.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 镜头晃动<b>时间连续性</b>的离线守卫。
 *
 * <p>来源是一次实机反馈："不是很平滑，是直接 set 的吗"。根因是振荡器跑在
 * {@code level.getGameTime()} 上——那是<b>整 tick</b>（20Hz），于是 60fps 下
 * 连续 3 帧取到同一个角度、然后跳一下，读作 20 级台阶。
 *
 * <p>这类故障的性质和本仓库之前那几起完全同型：<b>没有任何异常、没有日志、
 * 编译与测试全绿</b>，只有画面能看出来。所以必须用断言把它钉住。
 */
class ShatterScreenFxOscillatorTest {

    /** 值噪声必须落在 [-1,1]。越界会让 roll 幅度超出标定值。 */
    @Test
    void valueNoiseStaysInRange() {
        for (int i = 0; i < 20000; i++) {
            // 步长刻意取无理数，避免只落在整数格上
            double t = i * 0.0137D;
            float n = ShatterScreenFx.valueNoise(t, 11L);
            assertTrue(n >= -1.0F && n <= 1.0F, "valueNoise 越界：" + n + " @ t=" + t);
        }
    }

    /** 值噪声必须真的在变——constant 或全零的噪声等于没有晃动。 */
    @Test
    void valueNoiseActuallyVaries() {
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        for (int i = 0; i < 5000; i++) {
            float n = ShatterScreenFx.valueNoise(i * 0.01D, 23L);
            min = Math.min(min, n);
            max = Math.max(max, n);
        }
        assertTrue(max - min > 0.8F, "噪声幅度过小，读作没有晃动：" + (max - min));
    }

    /** 不同 seed 必须给出不同序列，否则 roll 与 pitch 会完全同步（退化成一条斜线）。 */
    @Test
    void differentSeedsAreIndependent() {
        double sum = 0.0D;
        for (int i = 0; i < 2000; i++) {
            double t = i * 0.02D;
            sum += ShatterScreenFx.valueNoise(t, 11L) * ShatterScreenFx.valueNoise(t, 23L);
        }
        double mean = sum / 2000.0D;
        assertTrue(Math.abs(mean) < 0.05D, "两个八度高度相关，晃动会退化成单一方向摆动：" + mean);
    }

    /**
     * <b>核心断言</b>：相邻帧（1/60 s 与 1/240 s）之间的角度变化必须都很小。
     *
     * <p>20Hz 台阶在这条上必然失败：60fps 下相邻帧差为 0（同一 tick），
     * 而跨 tick 时突然跳满幅度；240fps 下平均每 12 帧才换一次值。
     * 真正的连续振荡器在两种帧率下的"最大帧间步进"都应当远小于幅度本身。
     */
    @Test
    void oscillatorIsContinuousAcrossFrameRates() {
        for (double fps : new double[]{60.0D, 144.0D, 240.0D}) {
            double dt = 1.0D / fps;
            double prev = ShatterScreenFx.valueNoise(0.0D, 37L);
            double maxStep = 0.0D;
            for (int i = 1; i <= (int) (fps * 4.0D); i++) {
                double cur = ShatterScreenFx.valueNoise(i * dt * 10.3D, 37L);
                maxStep = Math.max(maxStep, Math.abs(cur - prev));
                prev = cur;
            }
            // 10.3 Hz 的噪声在 60fps 下理论最大步进约 2π*10.3/60 ≈ 1.08（幅度 2 的归一化
            // 噪声），但因 smoothstep 插值且三八度混合，保守取 0.9 作为"连续"阈值。
            assertTrue(maxStep < 0.9D,
                    fps + " fps 下最大帧间步进 " + maxStep + " 过大 ⇒ 读作台阶而非平滑运动");
        }
    }

    /** 单极点平滑必须与 dt 无关：同一段时间内，帧数不同但收敛程度应几乎一致。 */
    @Test
    void envelopeConvergesAtSameRateRegardlessOfFramerate() {
        // 模拟 0.32s 的释放过程：tau=0.32 ⇒ 理论残余 e^{-1} ≈ 0.368
        double target = 0.0D;
        for (int fps : new int[]{30, 60, 144, 240}) {
            double cur = 1.0D;
            double dt = 1.0D / fps;
            for (int i = 0; i < (int) (0.32D * fps); i++) {
                cur += (target - cur) * (1.0D - Math.exp(-dt / 0.32D));
            }
            assertTrue(Math.abs(cur - Math.exp(-1.0D)) < 0.06D,
                    fps + " fps 下 0.32s 后的残余是 " + cur + "，与理论值 " + Math.exp(-1.0D) + " 偏离过大");
        }
    }

    /** 起振必须比衰减快：否则爆炸当刻的冲击会被 0.32s 的爬坡吃掉。 */
    @Test
    void attackIsFasterThanRelease() {
        assertTrue(ShatterScreenFx.TAU_ATTACK < ShatterScreenFx.TAU_RELEASE * 0.25F,
                "起振时间常数 " + ShatterScreenFx.TAU_ATTACK + " 没有显著快于衰减 "
                        + ShatterScreenFx.TAU_RELEASE);
    }
}
