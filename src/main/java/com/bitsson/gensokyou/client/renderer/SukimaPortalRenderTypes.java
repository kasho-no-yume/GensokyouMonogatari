package com.bitsson.gensokyou.client.renderer;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 隙间传送门专用渲染类型。
 *
 * <p>{@link #voidPortal(ResourceLocation)}：末地门虚空内景——克隆原版 END_PORTAL
 * 组合状态，仅把双贴图（底色槽位 + 16 层视差槽位）换成指定贴图：原版
 * {@code rendertype_end_portal} 着色器从裁剪空间投影采样（效果锚定屏幕、与几何
 * 朝向无关），以 COLORS[0] 底色叠加 16 层缩放/旋转/GameTime 漂移的视差星层——
 * 换掉贴图即得「以自有素材呈现的锚定虚空」，槽位同贴图即纯虚空（无星空）。
 *
 * <p>{@link #portal(ResourceLocation)}：半透明贴图 billboard quad（translucent +
 * REPEAT wrap），当前渲染未引用，为未来传送门类技能预留。
 *
 * <p>继承 RenderStateShard 仅为了访问其 protected 的状态分片（本类不实例化）。
 */
public final class SukimaPortalRenderTypes extends RenderStateShard {

    private static final Map<ResourceLocation, RenderType> VOID_CACHE = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, RenderType> PORTAL_CACHE = new ConcurrentHashMap<>();

    /** 隙间虚空着色器实例，由 {@code GensokyouClient.onRegisterShaders} 在资源重载时装入。 */
    public static ShaderInstance sukimaVoidShader;

    /** 着色器分片：引用上方字段（注册完成前为 null，渲染期必已就绪）。 */
    private static final ShaderStateShard SUKIMA_VOID_SHADER = new ShaderStateShard(() -> sukimaVoidShader);

    private SukimaPortalRenderTypes() {
        super("gensokyou_shard_stub", () -> { }, () -> { });
    }

    /**
     * 末地门虚空内景：{@code gensokyou:sukima_portal} 自定义着色器（克隆原版 end portal
     * 机理：裁剪空间投影采样 + 分层视差，黑红调色板 + 低层密度）+ 自有眼睛虚空贴图。
     * 眼形剪影由几何承载（着色器输出不透明、不吃环境光）；贴图须可平铺
     * （层矩阵缩放 UV，REPEAT wrap 下无缝拼接——烘焙脚本已做无缝化）。
     */
    public static RenderType voidPortal(ResourceLocation texture) {
        return VOID_CACHE.computeIfAbsent(texture, loc -> RenderType.create(
                "gensokyou_sukima_portal_void",
                DefaultVertexFormat.POSITION,
                VertexFormat.Mode.QUADS,
                1536,
                false,  // affectsCrumbling
                false,  // sortOnUpload
                RenderType.CompositeState.builder()
                        .setShaderState(SUKIMA_VOID_SHADER)
                        .setTextureState(MultiTextureStateShard.builder()
                                .add(loc, false, false)   // Sampler0：底色（屏幕锚定）
                                .add(loc, false, false)   // Sampler1：层叠视差
                                .build())
                        .createCompositeState(false)
        ));
    }

    /**
     * 传送门贴图层：translucent 混合、双面、REPEAT wrap、lightmap 生效
     * （全亮度由顶点写 {@code LightTexture.FULL_BRIGHT} 实现）。预留能力，暂未被引用。
     */
    public static RenderType portal(ResourceLocation texture) {
        return PORTAL_CACHE.computeIfAbsent(texture, loc -> RenderType.create(
                "gensokyou_sukima_portal",
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                256,
                false,  // affectsCrumbling
                false,  // sortOnUpload
                RenderType.CompositeState.builder()
                        .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                        .setTextureState(new TextureStateShard(loc, false, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setLightmapState(LIGHTMAP)
                        .setOverlayState(OVERLAY)
                        .createCompositeState(false)
        ));
    }
}
