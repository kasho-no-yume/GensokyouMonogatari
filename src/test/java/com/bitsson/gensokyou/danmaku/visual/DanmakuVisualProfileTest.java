package com.bitsson.gensokyou.danmaku.visual;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 视觉档案与几何的纯数学断言。
 *
 * <p>这些是本变更里唯一不依赖世界与渲染上下文的部分，也是「默认档案与改前逐位相同」
 * 这条兼容性承诺唯一能被自动验证的地方——观感本身只能靠实机眼睛。
 */
class DanmakuVisualProfileTest {

    private static final double EPS = 1.0E-6D;

    // ------------------------------------------------------------------
    // 兼容性：默认档案 MUST 与改前逐位相同
    // ------------------------------------------------------------------

    /**
     * 引入档案的唯一目的是「让这些值可按弹调整」，<b>不是</b>改掉它们。
     *
     * <p>四个数字逐位取自改前 {@code SphereDanmakuRenderer.CORE_SCALE / CORE_ALPHA} 与
     * {@code AbstractDanmakuRenderer.GLOW_SCALE / GLOW_ALPHA}。若这条挂了，说明有人
     * 「顺手调了一下」，四只 BOSS 的观感会在无人察觉的情况下改变。
     */
    @Test
    void defaultProfileMatchesPreChangeConstants() {
        DanmakuVisualProfile.Profile d = DanmakuVisualProfile.DEFAULT;
        assertEquals(0.55F, d.coreScale(), EPS, "亮核缩放 MUST 仍为改前的 0.55");
        assertEquals(235, d.coreAlpha(), "亮核 alpha MUST 仍为改前的 235");
        assertEquals(1.35F, d.glowScale(), EPS, "发光倍率 MUST 仍为改前的 1.35");
        assertEquals(110, d.glowAlpha(), "发光 alpha MUST 仍为改前的 110");
    }

    /**
     * 「碰撞箱 = 视觉直径」这条刚修好的不变量，在默认档案下 MUST 继续成立。
     *
     * <p>档案把两者拆成独立字段后，该等式从「恒等」降级为「默认相等」——正因如此
     * MUST 有断言守着，否则一次「给某档案设 hitboxScale=1.2」就会悄悄改掉全部弹的判定。
     */
    @Test
    void defaultProfileKeepsHitboxEqualToVisual() {
        assertEquals(DanmakuVisualProfile.DEFAULT.visualScale(),
                DanmakuVisualProfile.DEFAULT.hitboxScale(), EPS,
                "默认档案的碰撞缩放 MUST 等于视觉缩放");
    }

    @Test
    void defaultProfileHasCoreEnabled() {
        assertTrue(DanmakuVisualProfile.DEFAULT.hasCore());
    }

    /**
     * 星弹采用沿弹道朝向的薄型模型，宽白芯与彩色星角由同一网格提供。
     */
    @Test
    void starPrismUsesDedicatedHighResolutionTextureWithoutExtraLayers() {
        DanmakuVisualProfile.Profile star = DanmakuVisualProfile.STAR_PRISM;
        assertEquals(DanmakuGeometry.STAR_PRISM, star.geometry());
        assertFalse(star.hasCore(), "星形宽白芯由同一模型的内圈绘制，不另叠亮核副本");
        assertEquals(0, star.glowAlpha(), "单通星棱柱不应增加额外的加法绘制层");
        assertEquals("textures/entity/star_prism_danmaku.png", star.texture().getPath());
        assertTrue(star.hasTumble(), "3D 星弹应有小幅摇摆以显露棱边");
    }

    /** 星弹使用内嵌星芒，避免额外叠层。 */
    @Test
    void starPrismDoesNotAddASeparateGlowPass() {
        DanmakuVisualProfile.Profile s = DanmakuVisualProfile.STAR_PRISM;
        assertEquals(1.0F, s.glowScale(), EPS);
        assertEquals(0, s.glowAlpha());
    }

    // ------------------------------------------------------------------
    // 档案查表
    // ------------------------------------------------------------------

    @Test
    void defaultIdResolvesToDefault() {
        assertEquals(DanmakuVisualProfile.DEFAULT,
                DanmakuVisualProfile.byId(DanmakuVisualProfile.defaultId()));
    }

    /** 越界 id MUST 回落到默认档，绝不抛——一颗弹的坏档案不该让整场弹幕炸掉。 */
    @Test
    void outOfRangeIdFallsBackToDefault() {
        assertEquals(DanmakuVisualProfile.DEFAULT, DanmakuVisualProfile.byId(-1));
        assertEquals(DanmakuVisualProfile.DEFAULT, DanmakuVisualProfile.byId(9999));
    }

    /** 档案 id 用 int 逐弹同步，故 id 到档案的映射 MUST 无歧义。 */
    @Test
    void everyIdMapsToADistinctProfile() {
        for (int i = 0; i < DanmakuVisualProfile.size(); i++) {
            assertEquals(DanmakuVisualProfile.byId(i), DanmakuVisualProfile.byId(i));
            for (int j = i + 1; j < DanmakuVisualProfile.size(); j++) {
                assertFalse(DanmakuVisualProfile.byId(i).equals(DanmakuVisualProfile.byId(j)),
                        "档案 " + i + " 与 " + j + " 重复，注册表失去意义");
            }
        }
    }

    // ------------------------------------------------------------------
    // 档案校验
    // ------------------------------------------------------------------

    /** 亮核内嵌于本体，故缩放 MUST ≤ 1；否则会盖住本体成为主要观感。 */
    @Test
    void coreScaleAboveOneRejected() {
        assertThrows(IllegalArgumentException.class, () -> new DanmakuVisualProfile.Profile(
                DanmakuVisualProfile.DEFAULT.texture(), 1.0F, 1.0F, 1.2F, 235, 1.35F, 110,
                127, DanmakuGeometry.QUAD, DanmakuColorMode.FIXED, 0, 0.0F, 0));
    }

    /** 外发光是本体的放大，故倍率 MUST ≥ 1。 */
    @Test
    void glowScaleBelowOneRejected() {
        assertThrows(IllegalArgumentException.class, () -> new DanmakuVisualProfile.Profile(
                DanmakuVisualProfile.DEFAULT.texture(), 1.0F, 1.0F, 0.55F, 235, 0.8F, 110,
                127, DanmakuGeometry.QUAD, DanmakuColorMode.FIXED, 0, 0.0F, 0));
    }

    /**
     * 俯仰摆幅 MUST ≤ 90°。
     *
     * <p>超过 90° 时五角星在某相位会完全侧对相机，投影退化成一条线，玩家读作
     * 「弹消失了」——那比不摆动更糟。
     */
    @Test
    void tumbleAmplitudeAboveNinetyRejected() {
        assertThrows(IllegalArgumentException.class, () -> new DanmakuVisualProfile.Profile(
                DanmakuVisualProfile.DEFAULT.texture(), 1.0F, 1.0F, 0.55F, 235, 1.35F, 110,
                127, DanmakuGeometry.STAR_PRISM, DanmakuColorMode.FIXED, 0, 120.0F, 40));
    }

    @Test
    void nonPositiveScalesRejected() {
        assertThrows(IllegalArgumentException.class, () -> new DanmakuVisualProfile.Profile(
                DanmakuVisualProfile.DEFAULT.texture(), 0.0F, 1.0F, 0.55F, 235, 1.35F, 110,
                127, DanmakuGeometry.QUAD, DanmakuColorMode.FIXED, 0, 0.0F, 0));
        assertThrows(IllegalArgumentException.class, () -> new DanmakuVisualProfile.Profile(
                DanmakuVisualProfile.DEFAULT.texture(), 1.0F, -1.0F, 0.55F, 235, 1.35F, 110,
                127, DanmakuGeometry.QUAD, DanmakuColorMode.FIXED, 0, 0.0F, 0));
    }

    // ------------------------------------------------------------------
    // 五角星柱几何
    // ------------------------------------------------------------------

    /** 轮廓 MUST 是 10 点凸凹交替：偶数下标凸（=R），奇数下标凹（=r）。 */
    @Test
    void starPrismOutlineAlternatesFiveTimes() {
        double radius = 1.0D;
        double[] o = DanmakuGeometry.starPrismOutline(radius);
        assertEquals(20, o.length, "10 个轮廓点 → 20 个分量");
        int convex = 0;
        int concave = 0;
        for (int i = 0; i < 10; i++) {
            double r = Math.hypot(o[i * 2], o[i * 2 + 1]);
            if (i % 2 == 0) {
                assertEquals(radius, r, EPS, "凸点半径 MUST 等于 " + radius);
                convex++;
            } else {
                assertEquals(radius * DanmakuGeometry.STAR_INNER_RATIO, r, EPS,
                        "游戏星弹使用较浅的凹角，不是刻纹法阵的正五芒星");
                concave++;
            }
        }
        assertEquals(5, convex);
        assertEquals(5, concave);
    }

    /** 十点 MUST 等分 36°，否则五角星不对称。 */
    @Test
    void starPrismOutlineEvenlySpaced() {
        double[] o = DanmakuGeometry.starPrismOutline(1.0D);
        for (int i = 0; i < 10; i++) {
            double deg = Math.toDegrees(Math.atan2(o[i * 2 + 1], o[i * 2]));
            double expected = 90.0D - i * 36.0D;
            // 归一化到 [-180,180) 再比
            double diff = Math.abs(((deg - expected + 540.0D) % 360.0D) - 180.0D);
            assertEquals(0.0D, diff, 1.0E-6D, "第 " + i + " 点极角 MUST 为 " + expected);
        }
    }

    /** 五点 MUST 等分 72°（凸点间隔两格）。 */
    @Test
    void starPrismHasFiveSymmetricPoints() {
        double[] o = DanmakuGeometry.starPrismOutline(1.0D);
        double first = Math.toDegrees(Math.atan2(o[1], o[0]));
        for (int k = 0; k < 5; k++) {
            double deg = Math.toDegrees(Math.atan2(o[k * 4 + 1], o[k * 4]));
            double diff = Math.abs(((deg - (first - k * 72.0D) + 540.0D) % 360.0D) - 180.0D);
            assertEquals(0.0D, diff, 1.0E-6D, "第 " + k + " 个凸点 MUST 相差 72°");
        }
    }

    /** 星弹保留实体厚度，避免从侧面观察时退化成薄纸。 */
    @Test
    void starPrismHasVisibleSolidDepth() {
        assertEquals(0.20D, DanmakuGeometry.starPrismHalfThickness(1.0D), EPS);
        assertEquals(0.80D, DanmakuGeometry.starPrismHalfThickness(4.0D), EPS);
        assertTrue(DanmakuGeometry.STAR_WHITE_CORE_RADIUS >= 0.40D,
                "原作星弹以大白芯为主，不能缩成一个金属高光点");
        assertTrue(DanmakuGeometry.STAR_WHITE_CORE_RADIUS < DanmakuGeometry.STAR_INNER_RATIO,
                "白芯必须完整包含在星形轮廓内");
    }

    /** 透明凹角对应较小的碰撞缩放，避免按完整外接圆判中。 */
    @Test
    void starPrismHitboxIsTighterThanVisual() {
        DanmakuVisualProfile.Profile s = DanmakuVisualProfile.STAR_PRISM;
        assertTrue(s.hitboxScale() < s.visualScale(),
                "星形弹幕的碰撞缩放 MUST 小于视觉缩放");
        assertTrue(Math.abs(s.hitboxScale() - 0.60F) < 0.02F,
                "星形碰撞缩放应接近当前设定值 0.60: " + s.hitboxScale());
    }

    @Test
    void curvedConeHasForwardTipAndTranslucentRimWithoutExcessiveSubdivision() {
        DanmakuGeometry.ArrowheadSection[] sections = DanmakuGeometry.arrowheadSections();
        assertTrue(sections.length <= 4, "弧面短锥体最多四个截面，避免高面数曲面");
        double previousSlope = 0.0D;
        for (int i = 1; i < sections.length; i++) {
            assertTrue(sections[i].z() > sections[i - 1].z(), "截面应从尾端连续排向尖端");
            assertTrue(sections[i].radius() < sections[i - 1].radius(), "锥体应从宽底向前尖收束");
            double slope = (sections[i].radius() - sections[i - 1].radius())
                    / (sections[i].z() - sections[i - 1].z());
            assertTrue(slope < previousSlope, "弧线应从宽底平缓过渡到前尖，不能重新变成直锥");
            previousSlope = slope;
        }
        var tail = sections[0];
        var tip = sections[sections.length - 1];
        assertEquals(-1.0D, tail.z(), EPS);
        assertEquals(1.0D, tip.z(), EPS);
        assertEquals(1.0D, tail.radius(), EPS, "后缘应为宽底，不能出现杆状尾柄");
        assertEquals(0.0D, tip.radius(), EPS,
                "局部 +Z 前端必须收束为尖端");
        assertTrue(tail.alpha() > 0.0D && tail.alpha() < 1.0D, "外缘应略微半透明");
        assertEquals(1.0D, tip.alpha(), EPS, "前尖应保留实体感");
        for (int i = 0; i < sections.length; i++) {
            var section = sections[i];
            if (i > 0 && i < sections.length - 1) {
                assertTrue(section.radius() > (1.0D - section.z()) * 0.5D,
                        "中段应较直锥略微外鼓，以保留原作的弧线");
            }
            assertTrue(section.alpha() > 0.0D && section.alpha() <= 1.0D);
        }
    }

    // ------------------------------------------------------------------
    // 变色模式（全部由 tickCount 推导，零同步）
    // ------------------------------------------------------------------

    @Test
    void fixedColorModeReturnsBaseColor() {
        int base = 0x3366FF;
        assertEquals(base, DanmakuColorMode.FIXED.resolve(base, 0, 40));
        assertEquals(base, DanmakuColorMode.FIXED.resolve(base, 12345, 40));
    }

    // ------------------------------------------------------------------
    // 隐藏态（实机两次反馈所加）
    // ------------------------------------------------------------------

    /**
     * 隐藏态 MUST 对<b>每一层</b>等比压暗。
     *
     * <p>实机反馈①：隐藏态看起来<b>比常态更亮</b>。根因是只压暗了本体层与发光层，
     * <b>亮核层保留 235 的纯白加法</b>——它是三层里视觉最强的一层，压过被压暗的本体层。
     */
    @Test
    void hiddenStateMustDimEveryLayer() {
        DanmakuVisualProfile.Profile d = DanmakuVisualProfile.DEFAULT;
        double dim = d.hiddenAlpha() / 255.0D;
        assertTrue(dim < 1.0D, "隐藏态本体 alpha MUST 小于常态");
        assertTrue(d.glowAlpha() * dim < d.glowAlpha() * 0.6D,
                "发光层压暗后应低于常态的 60%");
        assertTrue(d.coreAlpha() * dim < d.coreAlpha() * 0.6D,
                "亮核层 MUST 同步压暗——它是最强的一层，漏掉它会导致隐藏态更亮");
    }

    /**
     * 加法层的压暗 MUST 同时降 alpha 与 RGB，即贡献按 {@code dim²} 下降。
     *
     * <p>实机反馈③：只压暗 alpha 时「外发光那一圈完全没有变暗的效果」。原因是辉光是
     * 软渐变的<b>外圈</b>，其贴图自身 alpha 在峰值外缘已掉到 ~0.2，故层 alpha
     * 110→55 换算到实际贡献只是 22→11，差 11/255，肉眼不可见。
     *
     * <p>本条锁的是「加法层必须按 dim² 压暗」这条性质。
     */
    @Test
    void additiveLayersDimQuadratically() {
        DanmakuVisualProfile.Profile d = DanmakuVisualProfile.DEFAULT;
        float dim = d.hiddenAlpha() / 255.0F;
        double glowAlphaOnly = d.glowAlpha() * dim;                 // 只降 alpha
        double glowAlsoRgb = d.glowAlpha() * dim * dim;             // 同时降 RGB
        assertTrue(glowAlsoRgb < glowAlphaOnly * 0.5D,
                "加法层的贡献应按 dim² 而非 dim 下降");
    }

    /** 隐藏态的 {@code hiddenAlpha} MUST 明显低于常态，且不至于低到读作「消失」。 */
    @Test
    void hiddenAlphaIsInTheReadableRange() {
        int hidden = DanmakuVisualProfile.DEFAULT.hiddenAlpha();
        assertTrue(hidden <= 110,
                "隐藏态 alpha 应 ≤ 110（约 43%），实测 " + hidden + "——实机反馈力度不够");
        assertTrue(hidden >= 70,
                "隐藏态 alpha 不宜低于 70（约 27%）：再低时密集墙会读作「消失」而非「变暗」");
    }

    /** 零周期时 MUST 退回固定色，否则除零。 */    @Test
    void zeroCycleFallsBackToBaseColor() {
        int base = 0xFF8800;
        assertEquals(base, DanmakuColorMode.CYCLE_HUE.resolve(base, 100, 0));
        assertEquals(base, DanmakuColorMode.PULSE.resolve(base, 100, 0));
    }

    /** 色相循环 MUST 随 tick 变化，且一个周期后回到起点。 */
    @Test
    void cycleHueVariesAndRepeats() {
        int at0 = DanmakuColorMode.CYCLE_HUE.resolve(0, 0, 40);
        int at20 = DanmakuColorMode.CYCLE_HUE.resolve(0, 20, 40);
        int at40 = DanmakuColorMode.CYCLE_HUE.resolve(0, 40, 40);
        assertFalse(at0 == at20, "半个周期后色相 MUST 已变化");
        assertEquals(at0, at40, "整整一个周期后 MUST 回到起点");
    }

    /** 亮度脉动 MUST 保持在 [0.6, 1.0] 内，不会暗到看不见或亮到溢出。 */
    @Test
    void pulseStaysInBrightnessRange() {
        for (int t = 0; t < 200; t++) {
            int c = DanmakuColorMode.PULSE.resolve(0xFF0000, t, 40);
            int value = Math.max(c & 0xFF, Math.max((c >> 8) & 0xFF, (c >> 16) & 0xFF));
            assertTrue(value >= 0.6 * 255 - 1 && value <= 255,
                    "亮度须在 [0.6,1.0]，tick=" + t + " 实测 " + (value / 255.0D));
        }
    }
}
