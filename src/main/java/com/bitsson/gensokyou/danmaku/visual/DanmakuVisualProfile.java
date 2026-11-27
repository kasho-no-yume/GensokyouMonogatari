package com.bitsson.gensokyou.danmaku.visual;

import com.bitsson.gensokyou.Gensokyou;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * 弹幕视觉档案：把「一颗弹长什么样」从渲染器的编译期常量变成可查表的数据。
 *
 * <p><b>为什么需要它</b>——改前核心层缩放、核心 alpha、发光倍率、发光 alpha、贴图
 * 全部是 {@code static final}，全模组唯一。于是「换贴图 / 换模型 / 调发光半径」三条
 * 需求一条都做不到：它们不是「参数不对」，而是<b>没有参数</b>。
 *
 * <p><b>为什么碰撞缩放 MUST 独立于视觉缩放</b>——改前 {@code SphereDanmaku.setSize}
 * 用同一个 {@code size} 同时驱动渲染四边形与碰撞箱（{@code getDimensions} 覆写 +
 * {@code refreshDimensions}）。那个「碰撞箱 = 视觉直径」的不变量是踩坑后修好的
 * （此前 {@code DATA_SIZE} 只用于渲染、实体尺寸写死 0.4×0.4，导致 BOSS 的大慢球
 * 看起来穿过玩家却没伤害）。一旦把球弹换成<b>模型</b>，模型的视觉尺寸不等于四边形
 * 的尺寸，这��刚修好的不变量立刻被打破。故本档案把两者显式拆成
 * {@link #visualScale} 与 {@link #hitboxScale}。
 *
 * <p><b>为什么 MUST 放公共代码</b>——{@code getDimensions} 两端都要跑（客户端的
 * {@code DanmakuHitScan} 与定住弹接触判定都读 AABB），只客户端可读的表会让双端
 * 碰撞箱不一致，表现为「客户端看着撞到了、服务端判定没撞到」。
 *
 * <p><b>带宽</b>——每颗弹只同步一个 int（档案 id）。贴图 / 尺寸 / alpha 全不逐弹下发。
 * 资源包换贴图只改外观，不影响任何玩法判定。
 */
public final class DanmakuVisualProfile {

    /** 正五角星的内外半径比（(3−√5)/2 ≈ 0.381966）。 */
    public static final double PENTAGRAM_INNER_RATIO = 0.3819660112501051D;

    /**
     * 一份视觉档案。全部字段不可变。
     *
     * @param texture          贴图
     * @param visualScale      视觉整体缩放（相对 {@code SphereDanmaku.getSize()}）
     * @param hitboxScale      碰撞箱缩放（相对 {@code getSize()}）——与 {@code visualScale} <b>解耦</b>。
     *                         默认档案二者相等，故「碰撞 = 视觉」的不变量仍成立
     * @param coreScale        亮核层缩放（相对本体）
     * @param coreAlpha        亮核层 alpha；<b>0 即关闭该层</b>
     * @param glowScale        外发光层放大倍率（相对本体）
     * @param glowAlpha        外发光层 alpha
     * @param hiddenAlpha      隐藏态下本体层的 alpha，同时作为<b>暗态系数</b>（÷255）供
     *                         加法层按 {@code dim²} 压暗用。加法层的压暗 MUST 同时降
     *                         alpha 与 RGB——只降 alpha 对辉光几乎无效，见
     *                         {@code AbstractDanmakuRenderer#renderGlow}
     * @param geometry         几何形状
     * @param colorMode        变色模式
     * @param colorCycleTicks  变色周期（tick）；{@code colorMode != FIXED} 时生效
     * @param tumbleAmpDeg     俯仰摆动幅度（度）；0 = 不摆动
     * @param tumblePeriod     俯仰摆动周期（tick）
     */
    public record Profile(ResourceLocation texture,
                           float visualScale,
                           float hitboxScale,
                           float coreScale,
                           int coreAlpha,
                           float glowScale,
                           int glowAlpha,
                           int hiddenAlpha,
                           DanmakuGeometry geometry,
                           DanmakuColorMode colorMode,
                           int colorCycleTicks,
                           float tumbleAmpDeg,
                           int tumblePeriod) {

        /** 校验档案自洽。越界即抛，避免一个坏档案静默毁掉整批弹的观感。 */
        public Profile {
            if (visualScale <= 0.0F) {
                throw new IllegalArgumentException("visualScale 必须为正: " + visualScale);
            }
            if (hitboxScale <= 0.0F) {
                throw new IllegalArgumentException("hitboxScale 必须为正: " + hitboxScale);
            }
            if (coreScale <= 0.0F || coreScale > 1.0F) {
                throw new IllegalArgumentException("coreScale 应在 (0,1]：亮核内嵌于本体 " + coreScale);
            }
            if (glowScale < 1.0F) {
                throw new IllegalArgumentException("glowScale 应 ≥ 1：外发光是本体的放大 " + glowScale);
            }
            if (tumbleAmpDeg < 0.0F || tumbleAmpDeg > 90.0F) {
                // 上限 90°：再大五角星投影会退化成线，玩家读作「弹消失」
                throw new IllegalArgumentException("tumbleAmpDeg 应在 [0,90] " + tumbleAmpDeg);
            }
        }

        /** 亮核层是否启用。 */
        public boolean hasCore() {
            return coreAlpha > 0;
        }

        /** 该档案是否需要俯仰摆动。 */
        public boolean hasTumble() {
            return tumbleAmpDeg > 0.0F && tumblePeriod > 0;
        }

        /** 复制并覆盖几何形状。 */
        public Profile withGeometry(DanmakuGeometry newGeometry) {
            return new Profile(texture, visualScale, hitboxScale, coreScale, coreAlpha,
                    glowScale, glowAlpha, hiddenAlpha, newGeometry, colorMode, colorCycleTicks,
                    tumbleAmpDeg, tumblePeriod);
        }
    }

    // ------------------------------------------------------------------
    // 档案表
    // ------------------------------------------------------------------

    /**
     * 默认档案——与引入档案<b>之前</b>逐位相同的观感。
     *
     * <p>0.55 / 235 / 1.35 / 110 四项是原 {@code SphereDanmakuRenderer} 与
     * {@code AbstractDanmakuRenderer} 里的硬编码常量，此处原样搬入。视觉缩放与碰撞缩放
     * 同为 1.0，故既有四只 BOSS 的符卡表无需改动，观感 MUST NOT 有任何变化。
     */
    public static final Profile DEFAULT = new Profile(
            texture("sphere_danmaku"),
            1.0F, 1.0F,          // visualScale / hitboxScale —— 二者相等即旧不变量
            0.55F, 235,           // 亮核
            1.35F, 110,           // 外发光
            96,                    // 隐藏态 alpha ≈ 38%（暗态系数）；加法层按 dim² 压暗
            DanmakuGeometry.QUAD,
            DanmakuColorMode.FIXED,
            0,                    // colorCycleTicks
            0.0F, 0);             // 不摆动

    /**
     * 五角星弹幕。沿弹道朝向的实体模型，采用原作的大白芯、彩色星角和柔和外缘。
     *
     * <p>视觉和碰撞尺寸分开：五角星有透明的凹角，碰撞尺寸继续使用圆形半径的约 60%。
     *
     * <p>高清灰阶贴图仅提供平滑明暗；白芯和彩色外缘由同一网格的渐变顶点色提供。
     * 整体只绘制一次，不使用相机朝向的 billboard，也不叠加独立发光层。
     */
    public static final Profile STAR_PRISM = new Profile(
            texture("star_prism_danmaku"),
            1.0F, 0.60F,
            0.55F, 0,               // 宽白芯由本体网格的内圈提供
            1.0F, 0,                // 单通五角星模型不另画辉光层
            96,
            DanmakuGeometry.STAR_PRISM,
            DanmakuColorMode.FIXED,
            0,
            18.0F, 40);

    /** 全部档案，顺序即 id。id 逐弹同步，故<b>顺序 MUST NOT 变动</b>。 */
    private static final List<Profile> ALL = List.of(DEFAULT, STAR_PRISM);

    /** 档案总数上限——id 用 int 同步，越界即回落到默认档。 */
    public static int size() {
        return ALL.size();
    }

    /** 按 id 取档案。越界或负数回落到 {@link #DEFAULT}，绝不抛——一颗弹的坏档案
     *  不该让整场弹幕炸掉。 */
    public static Profile byId(int id) {
        if (id < 0 || id >= ALL.size()) {
            return DEFAULT;
        }
        return ALL.get(id);
    }

    /** 默认档案的 id。 */
    public static int defaultId() {
        return 0;
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(
                Gensokyou.MODID, "textures/entity/" + name + ".png");
    }

    private DanmakuVisualProfile() {
    }
}
