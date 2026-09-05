package com.bitsson.gensokyou.client.renderer;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureAtlas;

/**
 * 仪式投影幽灵方块专用渲染类型：BLOCK 顶点格式 + 方块图集 + 自定义核心着色器
 * {@code gensokyou:ritual_ghost}（克隆原版 rendertype_translucent，fsh 额外乘
 * Tint uniform——BLOCK 顶点色 alpha 恒 255，原版 shader 下不透明贴图无法半透明）。
 * 混合 translucent、深度测试开、深度写入关（COLOR_WRITE）。
 *
 * <p>Tint 在渲染器逐批设置（{@code RitualPreviewRenderer} 在各 {@code endBatch} 前
 * 写入白/红值，{@code Uniform.set} 的脏标记延迟上传保证各批次取值正确）；
 * 两实例共用同一 ShaderInstance，仅状态一致、颜色分批。
 */
public final class RitualGhostRenderTypes extends RenderStateShard {

    /** 幽灵着色器实例，由 {@code GensokyouClient.onRegisterShaders} 在资源重载时装入。 */
    public static ShaderInstance ritualGhostShader;
    /** Tint uniform（rgba）缓存，资源重载时随实例一起换新。 */
    public static Uniform tintUniform;

    public static final RenderType GHOST = create("gensokyou_ritual_ghost");
    public static final RenderType GHOST_CONFLICT = create("gensokyou_ritual_ghost_conflict");

    private RitualGhostRenderTypes() {
        super("gensokyou_ghost_shard_stub", () -> { }, () -> { });
    }

    private static RenderType create(String name) {
        return RenderType.create(name,
                DefaultVertexFormat.BLOCK,
                VertexFormat.Mode.QUADS,
                131072,
                false,  // affectsCrumbling
                true,   // sortOnUpload（translucent 语义，buffer 内按距离排序）
                RenderType.CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> ritualGhostShader))
                        .setTextureState(new TextureStateShard(TextureAtlas.LOCATION_BLOCKS, false, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setDepthTestState(LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(COLOR_WRITE) // 只写颜色 = 深度写入关
                        .setCullState(NO_CULL)
                        .setLightmapState(LIGHTMAP)
                        .createCompositeState(false));
    }
}
