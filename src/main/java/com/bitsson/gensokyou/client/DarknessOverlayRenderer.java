package com.bitsson.gensokyou.client;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.entity.spell.DarknessFieldEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.util.List;

/**
 * 闇符『划界』客户端压暗（add-player-spellcards 5.1 反馈版）：本地玩家处于结界内时**恒定**压暗
 * 画面四周（vignette，中央留清晰区），无脉冲、无闪烁；进出结界时平滑淡入淡出。
 * 纯客户端渲染，结界实体本身已同步，无额外包。
 */
@EventBusSubscriber(modid = Gensokyou.MODID, value = Dist.CLIENT)
public final class DarknessOverlayRenderer {

    /** 最大压暗强度（alpha/255）。 */
    private static final float MAX_DARKNESS = 165F / 255F;
    /** 每秒向目标强度逼近的速度。 */
    private static final float EASE = 0.12F;

    private static float current;

    private DarknessOverlayRenderer() {
    }

    @SubscribeEvent
    public static void onRenderGuiLayer(RenderGuiLayerEvent.Post event) {
        if (event.getName() != VanillaGuiLayers.CROSSHAIR) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) {
            current = 0F;
            return;
        }
        float target = targetDarkness(mc);
        current += (target - current) * EASE;
        if (current < 0.003F) {
            return;
        }
        drawVignette(event.getGuiGraphics(), current);
    }

    private static float targetDarkness(Minecraft mc) {
        List<DarknessFieldEntity> fields = mc.level.getEntitiesOfClass(
                DarknessFieldEntity.class, mc.player.getBoundingBox().inflate(64D));
        for (DarknessFieldEntity field : fields) {
            if (mc.player.position().distanceTo(field.position()) <= field.radius()) {
                return MAX_DARKNESS;
            }
        }
        return 0F;
    }

    /** 四周压暗、中央留清晰：多层内缩矩形叠加出柔和梯度。 */
    private static void drawVignette(GuiGraphics graphics, float darkness) {
        int w = graphics.guiWidth();
        int h = graphics.guiHeight();
        int bands = 20;
        int maxInsetX = Math.max(4, w / 4);
        int maxInsetY = Math.max(4, h / 4);
        for (int i = 0; i < bands; i++) {
            float t = i / (float) bands;
            int alpha = (int) (darkness * 255F * (1F - t) / bands * 2.2F);
            alpha = Mth.clamp(alpha, 0, 255);
            int color = alpha << 24;
            int x = i * maxInsetX / bands;
            int y = i * maxInsetY / bands;
            graphics.fill(0, 0, x + 2, h, color);
            graphics.fill(w - x - 2, 0, w, h, color);
            graphics.fill(0, 0, w, y + 2, color);
            graphics.fill(0, h - y - 2, w, h, color);
        }
    }
}
