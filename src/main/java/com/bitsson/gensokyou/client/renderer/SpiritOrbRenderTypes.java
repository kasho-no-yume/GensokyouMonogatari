package com.bitsson.gensokyou.client.renderer;

import com.bitsson.gensokyou.Gensokyou;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;

/**
 * 八方归元灵气球渲染类型（ritual-fx-overhaul D6）：POSITION_COLOR_TEX_LIGHTMAP 顶点格式 +
 * 自定义 fresnel×雾噪声着色器 {@code gensokyou:spirit_orb}（边缘亮、内部丝缕流动、
 * 整体偏透的"气"球壳）。贴图为共享紫雾软团（blur + REPEAT 滚动采样）。
 *
 * <p><b>写深度</b>（COLOR_DEPTH_WRITE）：BE 阶段整体先于半透明地形（玻璃/水）绘制，
 * 不写深度会让更近的球被后画的玻璃/悬浮物盖住；写深度后 DepthTest 正确剔除其后对象。
 * 加法混合下顺序无关，无需 sortOnUpload。
 *
 * <p>Time uniform 由渲染器每帧设入（{@link #timeUniform}），批次冲刷时上传。
 */
public final class SpiritOrbRenderTypes extends RenderStateShard {

    public static final ResourceLocation MIST_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Gensokyou.MODID, "textures/fx/spirit_mist.png");

    /** 灵气球着色器实例，由 {@code GensokyouClient.onRegisterShaders} 装入。 */
    public static ShaderInstance spiritOrbShader;
    /** Time uniform（雾噪声滚动相位）缓存，资源重载时随实例换新。 */
    public static Uniform timeUniform;

    private static final ShaderStateShard SPIRIT_ORB_SHADER = new ShaderStateShard(() -> spiritOrbShader);

    public static final RenderType ORB = RenderType.create(
            "gensokyou_spirit_orb",
            DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
            VertexFormat.Mode.QUADS,
            4096,
            false,   // affectsCrumbling
            false,   // sortOnUpload（加法与顺序无关）
            RenderType.CompositeState.builder()
                    .setShaderState(SPIRIT_ORB_SHADER)
                    .setTextureState(new TextureStateShard(MIST_TEXTURE, false, true))
                    .setTransparencyState(ADDITIVE_TRANSPARENCY)
                    .setWriteMaskState(COLOR_DEPTH_WRITE)   // 写深度：与玻璃/悬浮物正确排序
                    .setCullState(NO_CULL)
                    .createCompositeState(false));

    private SpiritOrbRenderTypes() {
        super("gensokyou_orb_shard_stub", () -> { }, () -> { });
    }
}
