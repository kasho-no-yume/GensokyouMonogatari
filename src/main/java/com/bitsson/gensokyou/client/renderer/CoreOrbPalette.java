package com.bitsson.gensokyou.client.renderer;

import java.awt.Color;

/**
 * 核心渲染的<b>配色与层预算</b>。
 *
 * <p>本类抽出全部"纯函数"色彩口径，渲染器只负责调用与组装几何。
 * 抽出为独立类有两个理由：① 渲染器里塞满魔法数会让预算规则无法被审阅；
 * ② 预算规则需要被<b>单元测试</b>覆盖（见 {@code CoreOrbPaletteTest}）。
 *
 * <p><b>为什么会有"层预算"这回事</b>——上一版渲染器发白的成因是算术溢出，不是审美：
 *
 * <pre>
 * 品阶色 #4CAF50 (76,175,80) × band 1.567 → (119, 255, 125)   ← 主通道被逐通道截顶，色相自己漂了
 * ①内层壳 + ②焦点核 + ④外晕 + ⑤环绕环 加法求和 → 主通道再次截顶 → (160, 255, 168) 发白薄荷绿
 * </pre>
 *
 * <p>根因有三条：<b>RGB 侧乘法 + 逐通道 clamp</b>、<b>多个加法层叠同一个饱和色</b>、
 * <b>贴图零色相信息</b>（灰度乘法遮罩）。三条各有对策：
 * <ul>
 *   <li>亮度调制搬到 <b>HSV 空间</b>（{@link #compose}）→ 色相不再随亮度漂移；</li>
 *   <li>每层一个<b>加法预算</b>（{@link Layer#budget()}）→ 总量有界，结构上不可能截顶；</li>
 *   <li>加法层<b>色相分槽</b>（{@link Layer#hueSlot()}）→ 同一色相不被两层同时持有，
 *       "全彩"由层间色相分离达成，而不是全色相循环。</li>
 * </ul>
 *
 * <p><b>纯客户端表现</b>：本类不参与任何玩法判定。
 */
public final class CoreOrbPalette {

    // ==================== 层预算（R1） ====================

    /**
     * 本体层峰值亮度（HSV 的 V 分量）。
     *
     * <p>取值 0.62（158/255）是刻意留的余量：本体峰值 + 加法总和 ≤ 158 + 85 = 243 &lt; 255，
     * <b>结构上</b>不可能发生通道截顶。
     */
    public static final float BODY_PEAK_BRI = 0.62F;

    /** 加法层贡献总和上限（每通道）。 */
    public static final int ADDITIVE_BUDGET_TOTAL = 85;

    /**
     * 加法层预算。
     *
     * <p>预算是"该层颜色峰值 × alpha"折算出的<b>单通道贡献上限</b>，
     * 由 {@link #alphaWithin(int, int, int)} 在运行时强制。
     */
    public enum Layer {
        /**
         * 本体：武器轮廓 / 流光球。alpha 混合，持有<b>品阶色纯本色</b>（装核态）或全光谱（沉睡态）。
         *
         * <p>alpha 而非加法：本体是"实体"，加法叠出来的实体在通道预算下必然发暗。
         */
        BODY(false, 0, 0),
        /**
         * 外晕：轮廓外一圈加法辉光，品阶色的<b>互补色</b>。
         *
         * <p>唯一的"纯加法"轮廓层：辉光本来就该是薄的一层光，加法是对的。
         */
        HALO(true, 46, 1),
        /**
         * 武器自带能量：拖尾 / 能量线 / 符文流 / 光轴。品阶色的<b>高亮变体</b>（同色相、饱和度更低）。
         *
         * <p>alpha：它们是武器上"发亮的零件"，不是光晕——实体画法在小尺寸下才读得出来。
         */
        ENERGY(false, 0, 2),
        /**
         * 火冠气焰：仅沉睡态。根部取局部流光色，尖端过渡到暖白。
         *
         * <p>alpha：火焰要"看得见"，加法在通道预算下只有 10% 出头，等于看不见。
         */
        FLAME(false, 0, 3),
        /** 环绕环：武器等级的标记。白金。 */
        RING(true, 22, 4);

        private final boolean additive;
        private final int budget;
        private final int hueSlot;

        Layer(boolean additive, int budget, int hueSlot) {
            this.additive = additive;
            this.budget = budget;
            this.hueSlot = hueSlot;
        }

        /** 该层是否走加法混合（true = 占加法预算）。 */
        public boolean isAdditive() {
            return additive;
        }

        /** 该层的加法预算（单通道贡献上限）；本体层为 0（走 alpha 混合，不占加法预算）。 */
        public int budget() {
            return this.budget;
        }

        /**
         * 该层占用的<b>色相槽</b>。
         *
         * <p>同一槽位的层 MUST NOT 同时出现——这是"同一色相不被两个加法层同时持有"的实现形式。
         * 槽位互斥保证任意同像素叠加的加法层色相彼此错开。
         */
        public int hueSlot() {
            return hueSlot;
        }
    }

    /**
     * 任意同像素上"可能同时出现"的加法层贡献总和上限。
     *
     * <p>由 {@link Layer#hueSlot()} 的互斥关系推出：同像素上每槽至多一层，
     * 故总和 ≤ 各槽预算之和。实测值 MUST ≤ {@link #ADDITIVE_BUDGET_TOTAL}。
     */
    public static int worstCaseAdditive() {
        int[] perSlot = new int[5];
        for (Layer layer : Layer.values()) {
            perSlot[layer.hueSlot()] = Math.max(perSlot[layer.hueSlot()], layer.budget());
        }
        int sum = 0;
        for (int v : perSlot) {
            sum += v;
        }
        return sum;
    }

    // ==================== 色彩口径 ====================

    /** 本体层饱和度（与 `DanmakuColorMode` 同口径：0.85）。 */
    public static final float BODY_SATURATION = 0.85F;

    /** 沉睡态（未装核）的全光谱滚动速度（周/秒）。 */
    public static final float RAINBOW_SPEED = 0.06F;

    /** 沉睡态的色相摆动幅度（度）——全光谱下不再受限，只控制"整颗球同时偏转"的呼吸感。 */
    public static final float RAINBOW_SWING_DEG = 18.0F;

    /** 装核态的色相摆动幅度（度）。**必须窄**——全色相循环会让球色不再是品阶色。 */
    public static final float TIER_HUE_SWING_DEG = 24.0F;

    /**
     * 逐面虹彩微偏（度/格）：相邻面之间的色相差，读作金属光泽。
     *
     * <p>乘的是「扇区序号<b>相对中心</b>的偏移」，故总偏移 ≈ 扇区数 ÷ 2 × 本值。
     * 想让总偏移落在 ±6° 内，本值应取 0.5° 量级。
     */
    public static final float HUE_FACE_SPREAD_DEG = 0.5F;

    /** 沉睡态轮廓处的色散偏移（度）。做进本体层的色相，**不做成加法层**（三通道同加即白边）。 */
    public static final float DISPERSION_DEG = 40.0F;

    /** 行进光带的深度（相对亮度起伏幅度）。 */
    public static final float BAND_DEPTH = 0.34F;

    /** 行进光带的条数。 */
    public static final float FLOW_BANDS = 2.5F;

    /** 行进光带的速度（周/秒）。 */
    public static final float FLOW_SPEED = 0.22F;

    /** 互补色槽（外晕 / 内部翻涌）的饱和度。 */
    public static final float AURA_SATURATION = 0.80F;

    /** 互补色槽的亮度。 */
    public static final float AURA_BRIGHTNESS = 1.0F;

    /** 武器自带能量层的饱和度——比本体低，读作"高亮变体"而非第二份品阶色。 */
    public static final float ENERGY_SATURATION = 0.55F;

    /** 武器自带能量层的亮度。 */
    public static final float ENERGY_BRIGHTNESS = 1.0F;

    /** 火冠的饱和度。 */
    public static final float FLAME_SATURATION = 0.90F;

    /** 火冠根部色（暖色相，度）——尖端由此过渡到近白。 */
    public static final float FLAME_WARM_DEG = 28.0F;

    /** 环绕环（等级标记）的色相（度）——白金，与任何品阶色都错开。 */
    public static final float RING_HUE_DEG = 45.0F;

    /** 环绕环的饱和度（近白）。 */
    public static final float RING_SATURATION = 0.12F;

    // ==================== 纯函数 ====================

    /**
     * HSV → RGB。<b>亮度分量 MUST 在此侧被限制</b>，MUST NOT 在 RGB 侧做乘法后再截顶
     * ——后者会在主通道触顶时同时造成色相漂移与饱和度塌陷（发白的直接成因）。
     */
    public static int compose(float hue, float saturation, float brightness) {
        float h = hue - (float) Math.floor(hue);
        float s = Math.min(1.0F, Math.max(0.0F, saturation));
        float b = Math.min(1.0F, Math.max(0.0F, brightness));
        return Color.HSBtoRGB(h, s, b) & 0xFFFFFF;
    }

    /** 取颜色的色相（0..1）。 */
    public static float hueOf(int rgb) {
        return Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null)[0];
    }

    /** 取颜色的饱和度（0..1）。 */
    public static float saturationOf(int rgb) {
        return Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null)[1];
    }

    /** 色相平移（度），结果仍在 0..1。 */
    public static float shiftHue(float hue, float degrees) {
        float h = hue + degrees / 360.0F;
        return h - (float) Math.floor(h);
    }

    /** 互补色（色相 + 180°）。 */
    public static float complement(float hue) {
        return shiftHue(hue, 180.0F);
    }

    /** 沿最短弧把色相 a 插值到 b（t=0 取 a，t=1 取 b）。 */
    public static float mixHue(float a, float b, float t) {
        float delta = b - a;
        if (delta > 0.5F) {
            delta -= 1.0F;
        } else if (delta < -0.5F) {
            delta += 1.0F;
        }
        return shiftHue(a, delta * t * 360.0F);
    }

    /** 线性插值。 */
    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /**
     * 行进光带的亮度系数（HSV 的 V 侧作用）。
     *
     * @param flow 流动坐标（沿表面的行进方向参数，0..1）
     */
    public static float band(float flow, float time) {
        return 1.0F + BAND_DEPTH * (float) Math.sin((float) (Math.PI * 2.0
                * (flow * FLOW_BANDS - time * FLOW_SPEED)));
    }

    /**
     * 把一层的颜色压进它的预算。
     *
     * <p>返回该层应使用的 alpha，使 {@code max(r,g,b) * alpha / 255 ≤ budget}。
     * 这是 R1 的<b>运行时强制点</b>——预算不是一个需要自觉遵守的约定，而是被算出来的。
     */
    public static int alphaWithin(int rgb, int budget, int desiredAlpha) {
        if (budget <= 0) {
            return desiredAlpha;
        }
        int peak = Math.max((rgb >> 16) & 0xFF, Math.max((rgb >> 8) & 0xFF, rgb & 0xFF));
        if (peak <= 0) {
            return desiredAlpha;
        }
        int maxAlpha = Math.min(255, budget * 255 / peak);
        return Math.min(desiredAlpha, maxAlpha);
    }

    /** 一层颜色在给定 alpha 下的单通道峰值贡献。 */
    public static int contribution(int rgb, int alpha) {
        int peak = Math.max((rgb >> 16) & 0xFF, Math.max((rgb >> 8) & 0xFF, rgb & 0xFF));
        return peak * alpha / 255;
    }

    // ==================== 配色方案与着色 ====================

    /**
     * 一帧的配色方案。
     *
     * @param baseHue 本体基色相（装核态 = 品阶色的色相；沉睡态为 0，由流光函数接管）
     * @param baseSat 本体饱和度
     * @param rainbow 是否走全光谱流光（沉睡态）
     */
    public record Palette(float baseHue, float baseSat, boolean rainbow) {

        public static Palette dormant() {
            return new Palette(0.0F, BODY_SATURATION, true);
        }

        public static Palette tier(int baseColor) {
            return new Palette(hueOf(baseColor), BODY_SATURATION, false);
        }
    }

    /**
     * 计算一个面片的颜色。
     *
     * <p><b>色相与亮度都在 HSV 侧合成</b>——这是"整体发白"的根治点。
     * 旧做法是 RGB 乘法 + 逐通道截顶，主通道一触顶，色相自己就漂了、饱和度同步塌掉。
     */
    public static int shade(Layer layer, Palette pal, CoreOrbGeometry.Quad q, float time, float flowScale) {
        return shade(layer, pal, q.a(), q.face(), q.fixed(), time, flowScale);
    }

    /**
     * 按<b>单个顶点</b>着色。
     *
     * <p>逐面着色会把流光球画成"迪斯科球"——每个面片一整块颜色，接缝处色相硬跳。
     * 逐顶点着色才有"能量在表面流动"的连续感。
     */
    public static int shade(Layer layer, Palette pal, CoreOrbGeometry.Vtx vtx, int face, int fixed,
                            float time, float flowScale) {
        if (fixed != 0) {
            // 固定结构色（符纸朱印等）：只让亮度随光带走，色相与饱和度保持不变。
            float[] hsb = Color.RGBtoHSB((fixed >> 16) & 0xFF,
                    (fixed >> 8) & 0xFF, fixed & 0xFF, null);
            float bri = hsb[2] * band(vtx.s(), time);
            return compose(hsb[0], hsb[1], Math.min(1.0F, bri));
        }
        float s = vtx.s();
        float e = vtx.e();
        float hue;
        float sat;
        float bri;
        switch (layer) {
            case BODY -> {
                if (pal.rainbow()) {
                    hue = rainbowHue(s, time);
                    // 色散：轮廓处把色相拆开（MUST 做进本体层，做成加法层就是白边）
                    hue = shiftHue(hue, DISPERSION_DEG * e);
                } else {
                    hue = shiftHue(pal.baseHue(),
                            TIER_HUE_SWING_DEG * (float) Math.sin(time * 0.9F)
                                    + HUE_FACE_SPREAD_DEG * face);
                }
                sat = pal.baseSat();
                bri = BODY_PEAK_BRI * band(s, time) * flowScale;
            }
            case HALO -> {
                hue = complement(pal.rainbow() ? rainbowHue(s, time) : pal.baseHue());
                sat = AURA_SATURATION;
                bri = AURA_BRIGHTNESS * band(s, time) * flowScale;
            }
            case ENERGY -> {
                hue = pal.rainbow() ? rainbowHue(s, time) : pal.baseHue();
                sat = ENERGY_SATURATION;
                bri = ENERGY_BRIGHTNESS;
            }
            case FLAME -> {
                hue = shiftHue(0.0F, FLAME_WARM_DEG);
                sat = FLAME_SATURATION;
                bri = 1.0F;
            }
            case RING -> {
                hue = shiftHue(0.0F, RING_HUE_DEG);
                sat = RING_SATURATION;
                bri = 1.0F;
            }
            default -> {
                hue = 0.0F;
                sat = 0.0F;
                bri = 0.0F;
            }
        }
        return compose(hue, sat, bri);
    }

    /** 全光谱流光的色相：沿流动坐标滚动 + 整体缓慢摆动。 */
    public static float rainbowHue(float s, float time) {
        float swing = RAINBOW_SWING_DEG * (float) Math.sin(time * 0.9F) / 360.0F;
        return shiftHue(s + time * RAINBOW_SPEED + swing, 0.0F);
    }

    private CoreOrbPalette() {
    }
}
