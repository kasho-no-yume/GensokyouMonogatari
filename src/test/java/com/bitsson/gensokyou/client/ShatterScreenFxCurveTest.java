package com.bitsson.gensokyou.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 屏幕级演出强度曲线的离线守卫。
 *
 * <p>这些是<b>纯算术</b>：距离衰减、蓄能爬升、爆炸包络。三者共同决定"玩家离门越近效果越强、
 * 100 格外完全无效"这条用户明确提出的要求。屏幕效果本身无法离线断言，但曲线可以——
 * 而曲线一旦写错（例如衰减指数写成 0.5F，近处几乎没效果、远处却还是满的），
 * 实机只会读作"效果很怪"，不会有任何异常或日志。
 */
class ShatterScreenFxCurveTest {

    @Test
    void zeroBeyondRange() {
        assertEquals(0.0F, ShatterScreenFx.falloff(100.0D), 1.0E-6F);
        assertEquals(0.0F, ShatterScreenFx.falloff(250.0D), 1.0E-6F);
    }

    @Test
    void fullStrengthAtContact() {
        assertEquals(1.0F, ShatterScreenFx.falloff(0.0D), 1.0E-6F);
    }

    @Test
    void monotonicallyDecreasingWithDistance() {
        float prev = Float.MAX_VALUE;
        for (int d = 0; d <= 100; d += 2) {
            float f = ShatterScreenFx.falloff(d);
            assertTrue(f <= prev, "d=" + d + " 的强度 " + f + " 大于更近处的 " + prev);
            prev = f;
        }
    }

    @Test
    void fullStrengthInsideThePlateau() {
        // 贴着门必须就是满效果。没有平台的话"离门越近越强"会退化成"离门越近越弱"——
        // 观众读不出 1.0 与 0.97 的差别，只会觉得"怎么这么淡"。
        assertEquals(1.0F, ShatterScreenFx.falloff(0.0D), 1.0E-6F);
        assertEquals(1.0F, ShatterScreenFx.falloff(2.0D), 1.0E-6F);
        assertEquals(1.0F, ShatterScreenFx.falloff(6.0D), 1.0E-6F, "平台边缘应仍是满值");
        assertTrue(ShatterScreenFx.falloff(6.5D) < 1.0F, "出平台后必须开始衰减");
    }

    @Test
    void chargeMidpointIsStrongEnoughToSee() {
        // 蓄能中段（p=0.5）必须已经有相当强度。上一版用 p^1.8 只有 0.287，
        // 再乘距离衰减后整段蓄能实际只有标称的 1/4，实机读作"完全没感觉"。
        int end = 160;
        float mid = ShatterScreenFx.chargeIntensity(0.0D, end / 2, end);
        assertTrue(mid > 0.40F, "蓄能中段强度过低：" + mid + "（会读作'没感觉'）");
        assertTrue(mid < 0.95F, "蓄能中段不该已达满值（爆炸包络才是 1）");
    }

    @Test
    void distantPortalsStillRegisterButWeakly() {
        // 用户要求"对所有玩家生效"，所以远处不能是 0；但必须明显弱于近处
        assertTrue(ShatterScreenFx.falloff(80.0D) > 0.0F, "80 格处不该完全没效果");
        assertTrue(ShatterScreenFx.falloff(80.0D) < 0.2F, "80 格处效果过强：" + ShatterScreenFx.falloff(80.0D));
        assertTrue(ShatterScreenFx.falloff(20.0D) > 0.6F, "20 格处效果过弱：" + ShatterScreenFx.falloff(20.0D));
    }

    @Test
    void chargeRampsUpAndIsZeroOutsideTheChargeWindow() {
        int end = 160;
        assertEquals(0.0F, ShatterScreenFx.chargeIntensity(0.0D, -1, end), 1.0E-6F, "无锚点时不该有蓄能强度");
        assertEquals(0.0F, ShatterScreenFx.chargeIntensity(0.0D, end, end), 1.0E-6F, "爆炸当刻已不是蓄能段");
        assertEquals(0.0F, ShatterScreenFx.chargeIntensity(0.0D, end + 1, end), 1.0E-6F);
        assertEquals(0.0F, ShatterScreenFx.chargeIntensity(0.0D, 0, 0), 1.0E-6F, "蓄能长度为 0 时不能除零");
        // 爬升：越接近爆炸越强
        float early = ShatterScreenFx.chargeIntensity(0.0D, 16, end);
        float late = ShatterScreenFx.chargeIntensity(0.0D, 144, end);
        assertTrue(early < late, "蓄能强度必须随时间上升");
        assertTrue(late < 1.0F, "t=chargeEnd 前不该达到满值（爆炸包络才是 1）");
    }

    @Test
    void burstSpikesAtTheInstantThenDecays() {
        int end = 160;
        assertEquals(1.0F, ShatterScreenFx.burstIntensity(0.0D, end, end), 1.0E-6F, "爆炸当刻应为满值");
        float a = ShatterScreenFx.burstIntensity(0.0D, end + 3, end);
        float b = ShatterScreenFx.burstIntensity(0.0D, end + 20, end);
        assertTrue(a > b, "爆炸包络必须随时间衰减");
        assertTrue(b > 0.0F, "20 tick 后仍应有可见余韵");
        assertEquals(0.0F, ShatterScreenFx.burstIntensity(0.0D, end - 1, end), 1.0E-6F, "爆炸前不该有包络");
        assertEquals(0.0F, ShatterScreenFx.burstIntensity(0.0D, end + 999, end), 1.0E-6F, "包络必须会结束");
    }

    @Test
    void everyIntensityIsZeroAtMaxRange() {
        int end = 160;
        for (int t = 0; t <= 200; t += 7) {
            assertEquals(0.0F, ShatterScreenFx.chargeIntensity(100.0D, t, end), 1.0E-6F);
            assertEquals(0.0F, ShatterScreenFx.burstIntensity(100.0D, t, end), 1.0E-6F);
            assertEquals(0.0F, ShatterScreenFx.chargeIntensity(140.0D, t, end), 1.0E-6F);
            assertEquals(0.0F, ShatterScreenFx.burstIntensity(140.0D, t, end), 1.0E-6F);
        }
    }

    @Test
    void intensityNeverLeavesZeroToOne() {
        int end = 160;
        for (int d = 0; d <= 100; d += 3) {
            for (int t = 0; t <= 200; t += 3) {
                float c = ShatterScreenFx.chargeIntensity(d, t, end);
                float b = ShatterScreenFx.burstIntensity(d, t, end);
                assertTrue(c >= 0.0F && c <= 1.0F, "charge 越界：" + c);
                assertTrue(b >= 0.0F && b <= 1.0F, "burst 越界：" + b);
            }
        }
    }
}
