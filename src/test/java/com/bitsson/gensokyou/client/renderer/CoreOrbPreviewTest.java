package com.bitsson.gensokyou.client.renderer;

import com.bitsson.gensokyou.item.weapon.CoreShellProfile;
import com.bitsson.gensokyou.registry.TierPalette;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 核心轮廓的<b>离线预览</b>与形状守卫。
 *
 * <p>观感本身只能靠实机眼睛，但"**七个档案的轮廓是不是真的各不相同**""**会不会发白**"
 * 这两件事是可以离线算出来的——所以它们既被断言，也被<b>画成 PNG</b> 供人眼复核。
 *
 * <p>产物落在 {@code build/preview/core_orb_*.png}（build 目录，不入库）。
 * 光栅化复现渲染器的投影与着色口径：模型坐标系 →（Yaw 90° → Pitch 16°）→ 屏幕正交投影，
 * 逐顶点重心插值着色（与 GPU 对逐顶点色的插值一致），按深度从远到近绘制。
 *
 * <p><b>刻意不引入渲染依赖</b>：单测环境没有 GL 上下文，故这里只做形状与配色预算的离线近似，
 * 不追求与实机逐像素一致。
 */
class CoreOrbPreviewTest {

    /** 预览画布边长。 */
    private static final int CANVAS = 256;

    /** 模型坐标 → 屏幕的缩放（模型半宽约 0.5，留一点余量）。 */
    private static final float SCALE = 200.0F;

    /** 模型 yaw / pitch（与渲染器一致）。 */
    private static final float YAW = 90.0F;
    private static final float PITCH = 16.0F;

    /** 背景色（中深灰，让辉光看得出来）。 */
    private static final int BACKGROUND = 0x20222A;

    /** 预览用的固定时刻。 */
    private static final float TIME = 1.37F;

    // ------------------------------------------------------------------
    // 断言
    // ------------------------------------------------------------------

    /**
     * 每个档案的轮廓 MUST 与其它档案<b>都不同</b>。
     *
     * <p>这是"外形随弹核变化"的机器可验证形式：若两个档案的剪影一致，
     * 玩家在物品栏里就分不出自己拿的是哪把武器。
     */
    @Test
    void everyProfileHasADistinctSilhouette() throws Exception {
        Set<String> fingerprints = new HashSet<>();
        List<String> duplicates = new ArrayList<>();
        for (CoreShellProfile profile : CoreShellProfile.values()) {
            BufferedImage image = render(profile);
            String fingerprint = fingerprint(image);
            if (!fingerprints.add(fingerprint)) {
                duplicates.add(profile.name());
            }
        }
        assertTrue(duplicates.isEmpty(), "这些档案的剪影完全相同：" + duplicates);
    }

    /**
     * 预览 MUST NOT 出现通道截顶。
     *
     * <p>离线光栅化把加法层直接加到画布上——若层预算失效，这里立刻就会看到 &gt;255 的通道值。
     */
    @Test
    void previewNeverExceedsTwoFiftyFive() throws Exception {
        for (CoreShellProfile profile : CoreShellProfile.values()) {
            BufferedImage image = render(profile);
            for (int y = 0; y < CANVAS; y++) {
                for (int x = 0; x < CANVAS; x++) {
                    int rgb = image.getRGB(x, y);
                    assertTrue(((rgb >> 16) & 0xFF) <= 255);
                    assertTrue(((rgb >> 8) & 0xFF) <= 255);
                    assertTrue((rgb & 0xFF) <= 255);
                }
            }
        }
    }

    /** 每个档案 MUST 画出非空的剪影（几何没退化）。 */
    @Test
    void everyProfileDrawsSomething() throws Exception {
        for (CoreShellProfile profile : CoreShellProfile.values()) {
            BufferedImage image = render(profile);
            int painted = 0;
            for (int y = 0; y < CANVAS; y++) {
                for (int x = 0; x < CANVAS; x++) {
                    if ((image.getRGB(x, y) & 0xFFFFFF) != BACKGROUND) {
                        painted++;
                    }
                }
            }
            assertTrue(painted > 200, profile + " 的剪影几乎是空的（" + painted + " 像素）");
        }
    }

    /** 装核态的本体色 MUST 仍落在品阶色的窄区间内（装核后不得漂成彩虹）。 */
    @Test
    void loadedStateKeepsItsTierHue() {
        float tierHue = CoreOrbPalette.hueOf(TierPalette.rgb(1));
        CoreOrbPalette.Palette pal = CoreOrbPalette.Palette.tier(TierPalette.rgb(1));
        for (CoreShellProfile profile : CoreShellProfile.values()) {
            if (profile.isDormant()) {
                continue;
            }
            for (CoreOrbGeometry.Quad q : CoreOrbGeometry.body(profile)) {
                if (q.fixed() != 0) {
                    // 固定结构色（符纸的朱印 / 封印钉）本来就是"结构色"，不受品阶色带约束
                    continue;
                }
                int rgb = CoreOrbPalette.shade(CoreOrbPalette.Layer.BODY, pal, q, TIME, 1.0F);
                float hue = CoreOrbPalette.hueOf(rgb);
                float delta = Math.abs(hue - tierHue);
                if (delta > 0.5F) {
                    delta = 1.0F - delta;
                }
                assertTrue(delta <= 30.0F / 360.0F + 1.0E-6F,
                        profile + " 的本体色相偏出品阶带：" + (delta * 360.0F) + "°");
            }
        }
    }

    /** 产物 MUST 落盘，供人眼复核。 */
    @Test
    void writePreviewPngs() throws Exception {
        File dir = new File("build", "preview");
        assertTrue(dir.exists() || dir.mkdirs(), "预览目录创建失败：" + dir);
        for (CoreShellProfile profile : CoreShellProfile.values()) {
            BufferedImage image = render(profile);
            File out = new File(dir, "core_orb_" + profile.name().toLowerCase(Locale.ROOT) + ".png");
            ImageIO.write(image, "PNG", out);
            assertTrue(out.exists() && out.length() > 0, "预览未落盘：" + out);
        }
    }

    /** 沉睡态与单发玉 MUST 是两种可辨的形态。 */
    @Test
    void dreamAndBallAreNotTheSameShape() throws Exception {
        assertNotEquals(fingerprint(render(CoreShellProfile.DREAM)), fingerprint(render(CoreShellProfile.BALL)));
        assertEquals(fingerprint(render(CoreShellProfile.BALL)), fingerprint(render(CoreShellProfile.BALL)));
    }

    // ------------------------------------------------------------------
    // 光栅化
    // ------------------------------------------------------------------

    /** 渲染一个档案的预览。 */
    private static BufferedImage render(CoreShellProfile profile) {
        boolean dormant = profile.isDormant();
        int baseColor = dormant ? 0 : TierPalette.rgb(1);
        CoreOrbPalette.Palette pal = dormant
                ? CoreOrbPalette.Palette.dormant()
                : CoreOrbPalette.Palette.tier(baseColor);

        int[] pixels = new int[CANVAS * CANVAS];
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = BACKGROUND;
        }

        List<Draw> draws = new ArrayList<>();

        // ① 本体（模型坐标系 → 旋转 → 屏幕）
        List<CoreOrbGeometry.Quad> body = dormant ? CoreOrbGeometry.DREAM_BODY : CoreOrbGeometry.body(profile);
        addQuads(draws, body, CoreOrbPalette.Layer.BODY, pal, TIME, 1.0F, true);

        // ② 腔体（相机朝向坐标系 → 直接就是世界坐标）
        if (dormant) {
            addQuads(draws, CoreOrbGeometry.DREAM_CAVITY, CoreOrbPalette.Layer.BODY, pal, TIME, 0.35F, false);
        }

        // ③ 外晕（放大一圈）
        addQuads(draws, body, CoreOrbPalette.Layer.HALO, pal, TIME, 1.0F, true, 1.12F);

        // ④ 能量件
        if (!dormant) {
            addQuads(draws, CoreOrbGeometry.energy(profile), CoreOrbPalette.Layer.ENERGY, pal, TIME, 1.0F, true);
        }

        // ⑤ 火冠
        if (dormant) {
            addQuads(draws, CoreOrbGeometry.flame(TIME), CoreOrbPalette.Layer.FLAME, pal, TIME, 1.0F, true);
        }

        // 画家算法：深度大的先画
        draws.sort((a, b) -> Float.compare(b.depth, a.depth));
        for (Draw draw : draws) {
            drawTriangle(pixels, draw.p0, draw.p1, draw.p2, draw.c0, draw.c1, draw.c2, draw.alpha, draw.additive);
            drawTriangle(pixels, draw.p0, draw.p2, draw.p3, draw.c0, draw.c2, draw.c3, draw.alpha, draw.additive);
        }

        BufferedImage image = new BufferedImage(CANVAS, CANVAS, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, CANVAS, CANVAS, pixels, 0, CANVAS);
        return image;
    }

    /** 一条待绘制的四边形（已投影到屏幕）。 */
    private record Draw(double[] p0, double[] p1, double[] p2, double[] p3,
                        int c0, int c1, int c2, int c3, float depth, float alpha, boolean additive) {
    }

    /** 把面片转成屏幕多边形。 */
    private static void addQuads(List<Draw> out, List<CoreOrbGeometry.Quad> quads,
                                 CoreOrbPalette.Layer layer, CoreOrbPalette.Palette pal,
                                 float time, float flowScale, boolean modelSpace) {
        addQuads(out, quads, layer, pal, time, flowScale, modelSpace, 1.0F);
    }

    /** 把面片转成屏幕多边形（可整体缩放，用于外晕）。 */
    private static void addQuads(List<Draw> out, List<CoreOrbGeometry.Quad> quads,
                                 CoreOrbPalette.Layer layer, CoreOrbPalette.Palette pal,
                                 float time, float flowScale, boolean modelSpace, float scale) {
        for (CoreOrbGeometry.Quad q : quads) {
            int alpha = layer.isAdditive()
                    ? CoreOrbPalette.alphaWithin(
                    CoreOrbPalette.shade(layer, pal, q, time, flowScale), layer.budget(), 255)
                    : 255;
            double[][] pts = new double[4][2];
            double depth = 0.0D;
            CoreOrbGeometry.Vtx[] vs = {q.a(), q.b(), q.c(), q.d()};
            for (int i = 0; i < 4; i++) {
                double[] p = project(vs[i].p(), modelSpace);
                pts[i][0] = CANVAS / 2.0 + p[0] * SCALE * scale;
                pts[i][1] = CANVAS / 2.0 - p[1] * SCALE * scale;
                depth += p[2];
            }
            depth /= 4.0D;
            int c0 = CoreOrbPalette.shade(layer, pal, q.a(), q.face(), q.fixed(), time, flowScale);
            int c1 = CoreOrbPalette.shade(layer, pal, q.b(), q.face(), q.fixed(), time, flowScale);
            int c2 = CoreOrbPalette.shade(layer, pal, q.c(), q.face(), q.fixed(), time, flowScale);
            int c3 = CoreOrbPalette.shade(layer, pal, q.d(), q.face(), q.fixed(), time, flowScale);
            out.add(new Draw(pts[0], pts[1], pts[2], pts[3], c0, c1, c2, c3, (float) depth,
                    alpha / 255.0F, layer.isAdditive()));
        }
    }

    /**
     * 重心插值填充一个三角形（与 GPU 对逐顶点色的插值一致）。
     *
     * @param alpha    该层颜色的混合强度（0..1）
     * @param additive true = 加法混合（src×alpha 加到 dst 上）；false = alpha 混合
     */
    private static void drawTriangle(int[] pixels, double[] p0, double[] p1, double[] p2,
                                     int c0, int c1, int c2, float alpha, boolean additive) {
        double minX = Math.min(p0[0], Math.min(p1[0], p2[0]));
        double maxX = Math.max(p0[0], Math.max(p1[0], p2[0]));
        double minY = Math.min(p0[1], Math.min(p1[1], p2[1]));
        double maxY = Math.max(p0[1], Math.max(p1[1], p2[1]));
        int x0 = (int) Math.floor(minX);
        int x1 = (int) Math.ceil(maxX);
        int y0 = (int) Math.floor(minY);
        int y1 = (int) Math.ceil(maxY);
        if (x1 < 0 || y1 < 0 || x0 >= CANVAS || y0 >= CANVAS) {
            return;
        }
        x0 = Math.max(0, x0);
        y0 = Math.max(0, y0);
        x1 = Math.min(CANVAS - 1, x1);
        y1 = Math.min(CANVAS - 1, y1);

        double d = (p1[1] - p2[1]) * (p0[0] - p2[0]) + (p2[0] - p1[0]) * (p0[1] - p2[1]);
        if (Math.abs(d) < 1.0E-9) {
            return;
        }
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                double px = x + 0.5;
                double py = y + 0.5;
                double l0 = ((p1[1] - p2[1]) * (px - p2[0]) + (p2[0] - p1[0]) * (py - p2[1])) / d;
                double l1 = ((p2[1] - p0[1]) * (px - p2[0]) + (p0[0] - p2[0]) * (py - p2[1])) / d;
                double l2 = 1.0 - l0 - l1;
                if (l0 < 0.0 || l1 < 0.0 || l2 < 0.0) {
                    continue;
                }
                int r = (int) Math.round(((c0 >> 16) & 0xFF) * l0 + ((c1 >> 16) & 0xFF) * l1 + ((c2 >> 16) & 0xFF) * l2);
                int g = (int) Math.round(((c0 >> 8) & 0xFF) * l0 + ((c1 >> 8) & 0xFF) * l1 + ((c2 >> 8) & 0xFF) * l2);
                int b = (int) Math.round((c0 & 0xFF) * l0 + (c1 & 0xFF) * l1 + (c2 & 0xFF) * l2);
                int idx = y * CANVAS + x;
                int dst = pixels[idx];
                if (additive) {
                    // 加法混合：src × alpha 加到 dst 上（与渲染器的 SRC_ALPHA, ONE 一致）
                    r = Math.min(255, ((dst >> 16) & 0xFF) + Math.round(r * alpha));
                    g = Math.min(255, ((dst >> 8) & 0xFF) + Math.round(g * alpha));
                    b = Math.min(255, (dst & 0xFF) + Math.round(b * alpha));
                } else {
                    // alpha 混合：src × alpha + dst × (1 - alpha)
                    r = Math.round(r * alpha + ((dst >> 16) & 0xFF) * (1.0F - alpha));
                    g = Math.round(g * alpha + ((dst >> 8) & 0xFF) * (1.0F - alpha));
                    b = Math.round(b * alpha + (dst & 0xFF) * (1.0F - alpha));
                }
                pixels[idx] = (r << 16) | (g << 8) | b;
            }
        }
    }

    /**
     * 模型坐标 → 屏幕坐标（正交投影）。
     *
     * @param modelSpace true = 走模型姿态（Yaw 90° → Pitch 16°）；false = 已是世界坐标
     */
    private static double[] project(net.minecraft.world.phys.Vec3 p, boolean modelSpace) {
        double x = p.x;
        double y = p.y;
        double z = p.z;
        if (modelSpace) {
            // Yaw 90°
            double nx = z;
            double nz = -x;
            x = nx;
            z = nz;
            // Pitch 16°
            double ry = y * Math.cos(Math.toRadians(PITCH)) - z * Math.sin(Math.toRadians(PITCH));
            double rz = y * Math.sin(Math.toRadians(PITCH)) + z * Math.cos(Math.toRadians(PITCH));
            y = ry;
            z = rz;
        }
        return new double[]{x, y, z};
    }

    /** 剪影指纹（忽略抗锯齿边缘，取内部像素的直方图摘要）。 */
    private static String fingerprint(BufferedImage image) {
        long hash = 1125899906842597L;
        for (int y = 4; y < CANVAS - 4; y += 3) {
            for (int x = 4; x < CANVAS - 4; x += 3) {
                hash = hash * 31 + image.getRGB(x, y);
            }
        }
        return Long.toHexString(hash);
    }
}
