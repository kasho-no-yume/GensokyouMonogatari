package com.bitsson.gensokyou.client.renderer;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 弹幕专用渲染类型。
 *
 * <p>提供<b>加法混合</b>（SRC_ALPHA, ONE）的自发光层：重叠区域亮度相加而非相互覆盖，
 * 这是东方弹幕辉光的标准做法。原版实体渲染类型均为 alpha 混合，在弹幕密集重叠时
 * 会发灰糊成一团，因此这里自建。
 *
 * <p>继承 RenderStateShard 仅为了访问其 protected 的状态分片（本类不实例化）。
 */
public final class DanmakuRenderTypes extends RenderStateShard {

    /** 按纹理缓存渲染类型，避免同帧内重复构建，也保证 BufferSource 的缓冲复用稳定。 */
    private static final Map<ResourceLocation, RenderType> GLOW_CACHE = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, RenderType> SOLID_CACHE = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, RenderType> TRANSLUCENT_CACHE = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, RenderType> TRANSLUCENT_DEPTH_CACHE = new ConcurrentHashMap<>();

    private DanmakuRenderTypes() {
        super("gensokyou_shard_stub", () -> { }, () -> { });
    }

    /**
     * 加法混合自发光层：满亮度、双面、只写颜色不写深度。
     * 深度测试仍开启，因此发光不会穿透墙体。
     */
    public static RenderType additiveGlow(ResourceLocation texture) {
        return GLOW_CACHE.computeIfAbsent(texture, loc -> RenderType.create(
                "gensokyou_danmaku_additive_glow",
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                1024,
                false,  // affectsCrumbling
                false,  // sortOnUpload：加法混合与绘制顺序无关，无需排序
                RenderType.CompositeState.builder()
                        .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                        .setTextureState(new TextureStateShard(loc, false, false))
                        .setTransparencyState(ADDITIVE_TRANSPARENCY)
                        .setWriteMaskState(COLOR_WRITE)
                        .setCullState(NO_CULL)
                        .setLightmapState(LIGHTMAP)
                        .setOverlayState(NO_OVERLAY)
                        .createCompositeState(false)
        ));
    }

    /**
     * 加法混合 + 写深度：供需要「正确遮挡后画半透明方块（如水）」的光束使用。
     *
     * <p>与 {@link #additiveGlow} 的差异仅在 {@code COLOR_DEPTH_WRITE}：实体缓冲先于半透明地形冲刷，写深度后，
      * 身后的水会被深度剔除（光束在水面之前的观感正确）；水面在光束之前的场景
      * 不受影响（水仍后画并正常染色）。sortOnUpload=false：加法混合是 can-be-overlaid，
      * 透明颜色叠加顺序不重要，免去每帧的大量 MeshData#sortQuads 排序开销。
     */
     public static RenderType additiveSolid(ResourceLocation texture) {
         return SOLID_CACHE.computeIfAbsent(texture, loc -> RenderType.create(
                 "gensokyou_danmaku_additive_solid",
                 DefaultVertexFormat.NEW_ENTITY,
                 VertexFormat.Mode.QUADS,
                 1024,
                 false,  // affectsCrumbling
                 false,  // sortOnUpload：加法混合可以覆盖，无需逐帧排序
                RenderType.CompositeState.builder()
                        .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                        .setTextureState(new TextureStateShard(loc, false, false))
                        .setTransparencyState(ADDITIVE_TRANSPARENCY)
                        .setWriteMaskState(COLOR_DEPTH_WRITE)
                        .setCullState(NO_CULL)
                        .setLightmapState(LIGHTMAP)
                        .setOverlayState(NO_OVERLAY)
                        .createCompositeState(false)
        ));
    }

    /**
     * 常规 alpha 混合层：供需要平滑渐变边缘的弹幕本体使用（cutout 无法表现
     * 半透明衰减，会把抗锯齿柔边切成锯齿）。不写深度：重叠弹幕的柔边不会
     * 相互凿洞；深度测试仍开启，地形照常遮挡。
     */
    public static RenderType translucent(ResourceLocation texture) {
        return TRANSLUCENT_CACHE.computeIfAbsent(texture, loc -> RenderType.create(
                "gensokyou_danmaku_translucent",
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                1024,
                false,  // affectsCrumbling
                false,  // sortOnUpload
                RenderType.CompositeState.builder()
                        .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                        .setTextureState(new TextureStateShard(loc, false, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setWriteMaskState(COLOR_WRITE)
                        .setCullState(NO_CULL)
                        .setLightmapState(LIGHTMAP)
                        .setOverlayState(NO_OVERLAY)
                        .createCompositeState(false)
        ));
    }

    /**
     * 常规 alpha 混合 + 写深度：供需要被后画的半透明地形（水）与云层正确遮挡的弹幕本体使用。
     *
     * <p>与 {@link #translucent} 的差异仅在 {@code COLOR_DEPTH_WRITE} 与
     * {@code sortOnUpload(true)}：实体缓冲先于半透明地形冲刷，本体写深度后，
     * 位于其身后的水/云会被深度剔除（本体在水云之前不再被覆盖）；排序用于避免
     * 自身重叠 quad 互相深度剔除。渐变柔边（alpha 衰减）仍由 alpha 混合表现。
     */
    public static RenderType translucentDepth(ResourceLocation texture) {
        return TRANSLUCENT_DEPTH_CACHE.computeIfAbsent(texture, loc -> RenderType.create(
                "gensokyou_danmaku_translucent_depth",
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                1024,
                false,  // affectsCrumbling
                true,   // sortOnUpload：写深度的半透明需按距离远→近排序
                RenderType.CompositeState.builder()
                        .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                        .setTextureState(new TextureStateShard(loc, false, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setWriteMaskState(COLOR_DEPTH_WRITE)
                        .setCullState(NO_CULL)
                        .setLightmapState(LIGHTMAP)
                        .setOverlayState(NO_OVERLAY)
                        .createCompositeState(false)
        ));
    }
}
