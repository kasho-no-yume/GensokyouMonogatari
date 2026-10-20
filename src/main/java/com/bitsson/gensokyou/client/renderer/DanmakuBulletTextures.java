package com.bitsson.gensokyou.client.renderer;

import com.bitsson.gensokyou.Gensokyou;
import net.minecraft.resources.ResourceLocation;

/**
 * 球弹贴图变体与当前选择。
 *
 * <p><b>为什么需要这一层</b>：球弹此前有<b>两处</b>各自 hardcode 贴图——
 * {@code SphereDanmakuRenderer}（实体路径）与 {@code ProgramDanmakuRenderer}（程序路径），
 * 各自持有自己的常量。两处独立 ⇒ 改一处忘一处就出现「实体弹换了、程序弹没换」的观感分叉。
 * 本类把<b>贴图名</b>与<b>当前变体</b>收敛到唯一出处，两条路径只读这里。
 *
 * <p><b>变体是可热切换的</b>（{@code /gs_bullet texture lod|glow}，客户端命令）：
 * 目的是让新旧观感能在<b>同一构建、同一二进制</b>内做对照压测——
 * 换构建会把「贴图差异」与「代码差异」混在一起，热切换则两臂只有贴图不同。
 *
 * <p><b>LOD 变体 MUST 保留</b>：它既是对照臂，也是回滚点。
 */
public final class DanmakuBulletTextures {

    /** 球弹贴图变体。 */
    public enum Variant {

        /**
         * 现役 LOD 贴图：实心小核（宽 43.75%）+ 透明环缝 + 三档硬边环。
         *
         * <p>读作一个「环」而不是一颗球；且硬边台阶在缩小时走样明显。
         * 仅作对照臂与回滚点。
         */
        LOD("sphere_danmaku_lod"),

        /**
         * 新贴图：实心本体（宽 58%）+ 连续外晕，单张贴图承载两层观感。
         *
         * <p>本体的实心区与外晕之间<b>没有透明环缝</b>（alpha 剖面取 body 与 glow 的 max），
         * 故读作一颗球；外缘靠原版着色器的 {@code discard}（alpha &lt; 0.1）自然收口。
         */
        GLOW("sphere_danmaku_glow");

        private final String fileName;

        Variant(String fileName) {
            this.fileName = fileName;
        }

        public ResourceLocation location() {
            return ResourceLocation.fromNamespaceAndPath(
                    Gensokyou.MODID, "textures/entity/" + fileName + ".png");
        }
    }

    /** 当前变体。默认 {@link Variant#GLOW}（新观感）。 */
    private static volatile Variant current = Variant.GLOW;

    private DanmakuBulletTextures() {
    }

    public static Variant current() {
        return current;
    }

    /** 切换当前变体。热生效，下一帧渲染即改。 */
    public static void setCurrent(Variant variant) {
        current = variant == null ? Variant.GLOW : variant;
    }

    /** 当前变体的贴图。两条渲染路径 MUST 只经由本方法取球弹贴图。 */
    public static ResourceLocation currentLocation() {
        return current.location();
    }
}
