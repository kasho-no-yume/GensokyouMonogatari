package com.bitsson.gensokyou.client;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.client.renderer.BillboardRenderer;
import com.bitsson.gensokyou.client.renderer.SkinMobRenderer;
import com.bitsson.gensokyou.client.renderer.RitualPedestalRenderer;
import com.bitsson.gensokyou.client.renderer.SphereDanmakuRenderer;
import com.bitsson.gensokyou.client.renderer.KnifeDanmakuRenderer;
import com.bitsson.gensokyou.client.renderer.TalismanDanmakuRenderer;
import com.bitsson.gensokyou.client.renderer.LaserDanmakuRenderer;
import com.bitsson.gensokyou.client.screen.RitualCoreScreen;
import com.bitsson.gensokyou.registry.ModBlockEntities;
import com.bitsson.gensokyou.registry.ModEntityTypes;
import com.bitsson.gensokyou.registry.ModMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

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
        event.registerEntityRenderer(ModEntityTypes.FAIRY.get(),
                context -> new SkinMobRenderer<>(context, 0.3F, 0.6F, GensokyouTextures.FAIRY));
        event.registerEntityRenderer(ModEntityTypes.BIG_FAIRY.get(),
                context -> new SkinMobRenderer<>(context, 0.5F, 1.1F, GensokyouTextures.BIG_FAIRY));
        event.registerEntityRenderer(ModEntityTypes.FLANDRE.get(),
                context -> new SkinMobRenderer<>(context, 0.5F, 1.0F, GensokyouTextures.FLANDRE));
        event.registerEntityRenderer(ModEntityTypes.FAKE_FLANDRE.get(),
                context -> new SkinMobRenderer<>(context, 0.5F, 0.9F, GensokyouTextures.FLANDRE));
        event.registerEntityRenderer(ModEntityTypes.CIRNO.get(),
                context -> new SkinMobRenderer<>(context, 0.45F, 1.15F, GensokyouTextures.FAIRY));
        event.registerBlockEntityRenderer(ModBlockEntities.RITUAL_PEDESTAL.get(), RitualPedestalRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.RITUAL_CORE.get(), RitualCoreScreen::new);
    }
}
