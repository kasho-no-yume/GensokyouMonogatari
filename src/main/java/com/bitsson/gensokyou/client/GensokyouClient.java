package com.bitsson.gensokyou.client;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.client.renderer.BillboardRenderer;
import com.bitsson.gensokyou.client.renderer.CrystalRenderer;
import com.bitsson.gensokyou.client.renderer.FairyGeoRenderer;
import com.bitsson.gensokyou.client.renderer.BigFairyGeoRenderer;
import com.bitsson.gensokyou.client.renderer.YamameGeoRenderer;
import com.bitsson.gensokyou.client.renderer.YakumoRanGeoRenderer;
import com.bitsson.gensokyou.client.renderer.RemnantBossRenderer;
import com.bitsson.gensokyou.client.renderer.SkinMobRenderer;
import com.bitsson.gensokyou.client.renderer.RitualPedestalRenderer;
import com.bitsson.gensokyou.client.renderer.RitualCoreRenderer;
import com.bitsson.gensokyou.client.renderer.SphereDanmakuRenderer;
import com.bitsson.gensokyou.client.renderer.DanmakuBulletTextures;
import com.bitsson.gensokyou.client.renderer.KnifeDanmakuRenderer;
import com.bitsson.gensokyou.client.renderer.ConeDanmakuRenderer;
import com.bitsson.gensokyou.client.renderer.TalismanDanmakuRenderer;
import com.bitsson.gensokyou.client.renderer.LaserDanmakuRenderer;
import com.bitsson.gensokyou.client.renderer.SukimaPortalRenderer;
import com.bitsson.gensokyou.client.renderer.SukimaPortalRenderTypes;
import com.bitsson.gensokyou.client.renderer.SpiritOrbRenderTypes;
import com.bitsson.gensokyou.client.renderer.RitualGhostRenderTypes;
import com.bitsson.gensokyou.client.screen.RitualBuilderScreen;
import com.bitsson.gensokyou.client.screen.RitualBuilderScreen;
import com.bitsson.gensokyou.client.screen.RitualCoreScreen;
import com.bitsson.gensokyou.client.screen.DanmakuAssemblyBenchScreen;
import com.bitsson.gensokyou.registry.ModBlockEntities;
import com.bitsson.gensokyou.registry.ModEntityTypes;
import com.bitsson.gensokyou.registry.ModMenus;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.minecraft.client.renderer.ShaderInstance;

import java.io.IOException;

@EventBusSubscriber(modid = Gensokyou.MODID, value = Dist.CLIENT)
public final class GensokyouClient {

    private GensokyouClient() {
    }

    /**
     * 整地器模型引用的是带透明像素的贴图（工具素材），默认 solid 渲染层会把透明处涂黑。
     * 设为 cutout：透明像素直接丢弃，不再有黑色块。
     */
    @SubscribeEvent
    public static void onClientSetup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        event.enqueueWork(() -> net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(
                com.bitsson.gensokyou.registry.ModBlocks.LANDSCAPING.get(),
                net.minecraft.client.renderer.RenderType.cutout()));
    }

    /**
     * 千机核心的动态物品渲染器。
     *
     * <p>走 {@link RegisterClientExtensionsEvent} 而不是覆写 {@code Item#initializeClient}
     * ——后者在 1.21 起已标记为待移除（{@code @Deprecated(forRemoval = true)}）。
     */
    @SubscribeEvent
    public static void onRegisterClientExtensions(
            net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent event) {
        event.registerItem(com.bitsson.gensokyou.client.renderer.CoreOrbItemExtensions.INSTANCE,
                com.bitsson.gensokyou.registry.ModItems.DANMAKU_WEAPON.get());
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {        event.registerEntityRenderer(ModEntityTypes.DANMAKU.get(),
                context -> new BillboardRenderer<>(context, 0.4F, GensokyouTextures.DANMAKU));
        event.registerEntityRenderer(ModEntityTypes.SPHERE_DANMAKU.get(),
                SphereDanmakuRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.KNIFE_DANMAKU.get(),
                KnifeDanmakuRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.CONE_DANMAKU.get(),
                ConeDanmakuRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.TALISMAN_DANMAKU.get(),
                TalismanDanmakuRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.LASER_DANMAKU.get(),
                LaserDanmakuRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.ORBIT_YIN_YANG_ORB.get(),
                context -> new BillboardRenderer<>(context, 0.7F, GensokyouTextures.ORBIT_ORB));
        event.registerEntityRenderer(ModEntityTypes.ZAOHUA_FLIGHT_ITEM.get(),
                net.minecraft.client.renderer.entity.ItemEntityRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.FAIRY.get(), FairyGeoRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.BIG_FAIRY.get(),
                BigFairyGeoRenderer::new);
        // 黑谷山女：正式 GeckoLib 模型（不走残影占位通路）。
        event.registerEntityRenderer(ModEntityTypes.YAMAME.get(), YamameGeoRenderer::new);
        // 八云蓝：正式 GeckoLib 残影模型（不走残影占位通路）。
        event.registerEntityRenderer(ModEntityTypes.YAKUMO_RAN.get(), YakumoRanGeoRenderer::new);
        // 残影 BOSS：渲染由注册点按实体 id 解析，实体类内不硬编（替换零成本）。
        event.registerEntityRenderer(ModEntityTypes.NOMEN_MASK.get(),
                context -> RemnantBossRenderer.create(ModEntityTypes.NOMEN_MASK.get(), context));
        event.registerEntityRenderer(ModEntityTypes.FLANDRE.get(),
                context -> new SkinMobRenderer<>(context, 0.5F, 1.0F, GensokyouTextures.FLANDRE));
        event.registerEntityRenderer(ModEntityTypes.FAKE_FLANDRE.get(),
                context -> new SkinMobRenderer<>(context, 0.5F, 0.9F, GensokyouTextures.FLANDRE));
        event.registerEntityRenderer(ModEntityTypes.CIRNO.get(),
                context -> new SkinMobRenderer<>(context, 0.45F, 1.15F, GensokyouTextures.FAIRY));
        event.registerEntityRenderer(ModEntityTypes.RINNOSUKE.get(),
                context -> new SkinMobRenderer<>(context, 0.5F, 1.0F, GensokyouTextures.RINNOSUKE));
        event.registerEntityRenderer(ModEntityTypes.BALANCE_TEST_BOSS.get(),
                com.bitsson.gensokyou.client.renderer.TestBossGeoRenderer::new);
        // 咒符条外观试作（临时）：只验证 frame_3/4 边框，复用妖精皮肤，无专属美术。
        event.registerEntityRenderer(ModEntityTypes.BLOOD_BAR_PROBE_T3.get(),
                context -> new SkinMobRenderer<>(context, 0.6F, 1.15F, GensokyouTextures.FAIRY));
        event.registerEntityRenderer(ModEntityTypes.BLOOD_BAR_PROBE_T4.get(),
                context -> new SkinMobRenderer<>(context, 0.6F, 1.15F, GensokyouTextures.FAIRY));
        // 玩家符卡宿主：范围场画贴地光盘，狐火/丝弹画发光billboard
        event.registerEntityRenderer(ModEntityTypes.FLOWER_GARDEN.get(),
                com.bitsson.gensokyou.client.renderer.FlowerGardenRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.DARKNESS_FIELD.get(),
                context -> new com.bitsson.gensokyou.client.renderer.SpellFieldRenderer<>(context, 0x66101018));
        event.registerEntityRenderer(ModEntityTypes.WEB_FIELD.get(),
                com.bitsson.gensokyou.client.renderer.SpiderWebRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.FOX_SERVANT.get(),
                com.bitsson.gensokyou.client.renderer.FoxServantRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.SILK_PROJECTILE.get(),
                context -> new BillboardRenderer<>(context, 0.3F, GensokyouTextures.FOX_FIRE));
        event.registerEntityRenderer(ModEntityTypes.DANMAKU_TELEGRAPH.get(),
                context -> new BillboardRenderer<>(context, 1.1F, GensokyouTextures.ORBIT_ORB));
        event.registerBlockEntityRenderer(ModBlockEntities.RITUAL_PEDESTAL.get(), RitualPedestalRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.RITUAL_CORE.get(), RitualCoreRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.SUKIMA.get(), SukimaPortalRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.CRYSTAL.get(), CrystalRenderer::new);
        // 灵力筑基器的范围线框不用 BER（BER 阶段早于水，会被水盖住），改在 LandscapingBoxRenderer
        // 的 RenderLevelStageEvent(AFTER_TRANSLUCENT_BLOCKS) 里绘制。
    }


    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
        // 隙间虚空着色器（克隆原版 end portal 机理，黑红调色板 + 低层密度）
        event.registerShader(
                new ShaderInstance(event.getResourceProvider(),
                        ResourceLocation.fromNamespaceAndPath(Gensokyou.MODID, "sukima_portal"),
                        DefaultVertexFormat.POSITION),
                shader -> SukimaPortalRenderTypes.sukimaVoidShader = shader);
        // 仪式投影幽灵着色器（克隆原版 rendertype_translucent，fsh 乘 Tint uniform）
        event.registerShader(
                new ShaderInstance(event.getResourceProvider(),
                        ResourceLocation.fromNamespaceAndPath(Gensokyou.MODID, "ritual_ghost"),
                        DefaultVertexFormat.BLOCK),
                shader -> {
                    RitualGhostRenderTypes.ritualGhostShader = shader;
                    RitualGhostRenderTypes.tintUniform = shader.getUniform("Tint");
                });
        // 八方归元灵气场 fresnel 着色器（POSITION_COLOR_TEX_LIGHTMAP，采样雾噪声，Tint 默认绿）
        event.registerShader(
                new ShaderInstance(event.getResourceProvider(),
                        ResourceLocation.fromNamespaceAndPath(Gensokyou.MODID, "spirit_orb"),
                        DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP),
                shader -> {
                    SpiritOrbRenderTypes.spiritOrbShader = shader;
                    SpiritOrbRenderTypes.timeUniform = shader.getUniform("Time");
                    SpiritOrbRenderTypes.fillUniform = shader.getUniform("Fill");
                });
        // 八方归元焦点核着色器（同顶点格式；常规 alpha 混合的**不透明**核，Density 为体内基础不透明度）
        event.registerShader(
                new ShaderInstance(event.getResourceProvider(),
                        ResourceLocation.fromNamespaceAndPath(Gensokyou.MODID, "spirit_core"),
                        DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP),
                shader -> {
                    SpiritOrbRenderTypes.spiritCoreShader = shader;
                    SpiritOrbRenderTypes.densityUniform = shader.getUniform("Density");
                });
    }

    @SubscribeEvent
    public static void onLoggingOut(
            net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        ClientBoundSupplyState.clear();
    }

    /**
     * 客户端命令：{@code /gs_bullet texture lod|glow}——热切换球弹贴图变体。
     *
     * <p><b>为什么是客户端命令</b>：贴图变体是纯客户端表现状态，服务端不知道也不该知道。
     * 走 {@link net.neoforged.neoforge.client.event.RegisterClientCommandsEvent} 才能在
     * 多人下正确地只改本地。
     *
     * <p><b>为什么要有这个命令</b>：新旧观感的对照压测必须在<b>同一构建、同一二进制</b>内进行
     * ——换构建会把「贴图差异」与「代码差异」混在一起，热切换则两臂只有贴图不同。
     *
     * <p>用独立根 {@code gs_bullet} 而不是 {@code danmaku}：{@code /danmaku} 是服务端命令树，
     * 客户端再注册一棵同名树会遮蔽服务端子命令（单人游戏里服务端树也在本地可用）。
     */
    @SubscribeEvent
    public static void onRegisterClientCommands(
            net.neoforged.neoforge.client.event.RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
                net.minecraft.commands.Commands.literal("gs_bullet")
                        .then(net.minecraft.commands.Commands.literal("texture")
                                .then(net.minecraft.commands.Commands.literal("lod")
                                        .executes(ctx -> setBulletTexture(ctx.getSource(),
                                                DanmakuBulletTextures.Variant.LOD)))
                                .then(net.minecraft.commands.Commands.literal("glow")
                                        .executes(ctx -> setBulletTexture(ctx.getSource(),
                                                DanmakuBulletTextures.Variant.GLOW)))));
    }

    /** 切换球弹贴图变体并回报当前选择。 */
    private static int setBulletTexture(net.minecraft.commands.CommandSourceStack source,
                                        DanmakuBulletTextures.Variant variant) {
        DanmakuBulletTextures.setCurrent(variant);
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                "§7球弹贴图变体 = §f" + variant.name()
                        + "§7（实体路径与程序路径同时生效）"), false);
        return 1;
    }

    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.RITUAL_CORE.get(), RitualCoreScreen::new);
        event.register(ModMenus.DANMAKU_ASSEMBLY_BENCH.get(), DanmakuAssemblyBenchScreen::new);
        event.register(ModMenus.RITUAL_BUILDER.get(), RitualBuilderScreen::new);
        event.register(ModMenus.RITUAL_EDITOR.get(), com.bitsson.gensokyou.client.screen.RitualEditorScreen::new);
        event.register(ModMenus.CRYSTAL_STORAGE.get(),
                com.bitsson.gensokyou.client.screen.CrystalStorageScreen::new);
        event.register(ModMenus.WUJINZANG_TERMINAL.get(),
                com.bitsson.gensokyou.client.screen.WujinzangTerminalScreen::new);
        event.register(ModMenus.SPIRIT_BOMB.get(),
                com.bitsson.gensokyou.client.screen.SpiritBombScreen::new);
        event.register(ModMenus.LANDSCAPING.get(),
                com.bitsson.gensokyou.client.screen.LandscapingScreen::new);
    }
}
