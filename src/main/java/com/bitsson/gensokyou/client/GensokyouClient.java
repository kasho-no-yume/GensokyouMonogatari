package com.bitsson.gensokyou.client;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.client.renderer.BillboardRenderer;
import com.bitsson.gensokyou.client.renderer.CrystalRenderer;
import com.bitsson.gensokyou.client.renderer.FairyGeoRenderer;
import com.bitsson.gensokyou.client.renderer.SkinMobRenderer;
import com.bitsson.gensokyou.client.renderer.RitualPedestalRenderer;
import com.bitsson.gensokyou.client.renderer.RitualCoreRenderer;
import com.bitsson.gensokyou.client.renderer.SphereDanmakuRenderer;
import com.bitsson.gensokyou.client.renderer.KnifeDanmakuRenderer;
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

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntityTypes.DANMAKU.get(),
                context -> new BillboardRenderer<>(context, 0.4F, GensokyouTextures.DANMAKU));
        event.registerEntityRenderer(ModEntityTypes.SPHERE_DANMAKU.get(),
                SphereDanmakuRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.KNIFE_DANMAKU.get(),
                KnifeDanmakuRenderer::new);
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
                context -> new SkinMobRenderer<>(context, 0.5F, 1.1F, GensokyouTextures.BIG_FAIRY));
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
        event.registerBlockEntityRenderer(ModBlockEntities.RITUAL_PEDESTAL.get(), RitualPedestalRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.RITUAL_CORE.get(), RitualCoreRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.SUKIMA.get(), SukimaPortalRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.CRYSTAL.get(), CrystalRenderer::new);
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
        // 八方归元灵气球 fresnel 着色器（POSITION_COLOR_TEX_LIGHTMAP，采样雾噪声，Tint 默认绿）
        event.registerShader(
                new ShaderInstance(event.getResourceProvider(),
                        ResourceLocation.fromNamespaceAndPath(Gensokyou.MODID, "spirit_orb"),
                        DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP),
                shader -> {
                    SpiritOrbRenderTypes.spiritOrbShader = shader;
                    SpiritOrbRenderTypes.timeUniform = shader.getUniform("Time");
                });
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
    }
}
