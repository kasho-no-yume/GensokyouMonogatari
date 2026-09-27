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
 * 八方归元两类球体的渲染类型（均为 POSITION_COLOR_TEX_LIGHTMAP + 单位球几何，见
 * {@link FxGeometry#emitUnitSphere}）：
 *
 * <ul>
 *   <li>{@link #ORB} —— <b>灵气场</b>。自定义 fresnel × 雾噪声着色器
 *       {@code gensokyou:spirit_orb}：边缘极缓、体内带微弱填充的"场"，无硬轮廓。
 *       加法混合（重叠处亮度相加，读作弥漫的雾）。</li>
 *   <li>{@link #CORE} —— <b>焦点核</b>。着色器 {@code gensokyou:spirit_core}：
 *       体内密度恒为 {@code Density}（不透明），fresnel 只在其上叠加边缘高光。
 *       常规 alpha 混合，故**真的遮挡**其后景物。</li>
 * </ul>
 *
 * <p><b>深度策略</b>：只有焦点核写深度——它是画面里唯一的实体，MUST 正确遮挡其后的半透明
 * 地形（玻璃/水）与祭品台激光。灵气场与激光都是加法外层，按
 * {@code ritual-runtime-fx} 的「加法混合外层 MUST NOT 写深度」一律
 * {@link RenderStateShard#COLOR_WRITE}：写深度会在半透明地形上凿出硬边洞。
 *
 * <p>Time uniform 由渲染器每帧设入（{@link #timeUniform}），Fill / Density 同理，批次冲刷时上传。
 */
public final class SpiritOrbRenderTypes extends RenderStateShard {

    public static final ResourceLocation MIST_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Gensokyou.MODID, "textures/fx/spirit_mist.png");

    /** 灵气场着色器实例，由 {@code GensokyouClient.onRegisterShaders} 装入。 */
    public static ShaderInstance spiritOrbShader;
    /** 焦点核着色器实例，由 {@code GensokyouClient.onRegisterShaders} 装入。 */
    public static ShaderInstance spiritCoreShader;
    /** Time uniform（雾噪声滚动相位）缓存，资源重载时随实例换新。 */
    public static Uniform timeUniform;
    /** Fill uniform：灵气场体内基础密度（0=全透壳，1=实心球）。 */
    public static Uniform fillUniform;
    /** Density uniform：焦点核体内基础不透明度。 */
    public static Uniform densityUniform;

    private static final ShaderStateShard SPIRIT_ORB_SHADER = new ShaderStateShard(() -> spiritOrbShader);
    private static final ShaderStateShard SPIRIT_CORE_SHADER = new ShaderStateShard(() -> spiritCoreShader);

    /** 灵气场：加法、不写深度、NO_CULL（要能从球内外两侧都看见雾）。 */
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
                    .setWriteMaskState(COLOR_WRITE)      // 加法外层不写深度
                    .setCullState(NO_CULL)
                    .createCompositeState(false));

    /**
     * 焦点核：常规 alpha 混合、**写深度**、NO_CULL。
     *
     * <p>写深度使它成为画面中唯一被正确排序的实体；NO_CULL 是必需的——alpha 混合的球若只画
     * 正面，从背面看会"消失"，读作一张贴片而非一颗球。
     */
    public static final RenderType CORE = RenderType.create(
            "gensokyou_spirit_core",
            DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
            VertexFormat.Mode.QUADS,
            4096,
            false,   // affectsCrumbling
            false,   // sortOnUpload
            RenderType.CompositeState.builder()
                    .setShaderState(SPIRIT_CORE_SHADER)
                    .setTextureState(new TextureStateShard(MIST_TEXTURE, false, true))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(COLOR_DEPTH_WRITE)  // 画面中唯一写深度的实体
                    .setCullState(NO_CULL)
                    .createCompositeState(false));

    private SpiritOrbRenderTypes() {
        super("gensokyou_orb_shard_stub", () -> { }, () -> { });
    }
}
