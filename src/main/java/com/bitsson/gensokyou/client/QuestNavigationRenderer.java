package com.bitsson.gensokyou.client;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.quest.QuestProjection;
import com.bitsson.gensokyou.quest.QuestSnapshot;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.joml.Vector4f;

@EventBusSubscriber(modid = Gensokyou.MODID, value = Dist.CLIENT)
public final class QuestNavigationRenderer {
    private static QuestSnapshot.Destination projectedFor;
    private static QuestProjection.Point point;
    private static int distance;
    @SubscribeEvent public static void project(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        projectedFor = null; point = null;
        var mc = Minecraft.getInstance();
        var target = ClientQuests.snapshot().navigation().orElse(null);
        if (target == null || mc.level == null || !mc.level.dimension().location().equals(target.dimension())) return;
        Vec3 camera = event.getCamera().getPosition();
        Vec3 delta = new Vec3(target.x(), target.y(), target.z()).subtract(camera);
        var clip = new Vector4f((float) delta.x, (float) delta.y, (float) delta.z, 1F);
        event.getModelViewMatrix().transform(clip); event.getProjectionMatrix().transform(clip);
        if (!Float.isFinite(clip.x) || !Float.isFinite(clip.y) || !Float.isFinite(clip.w)) return;
        point = QuestProjection.project(clip.x, clip.y, clip.w, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), 18);
        distance = (int) Math.min(Integer.MAX_VALUE, Math.round(delta.length()));
        projectedFor = target;
    }
    @SubscribeEvent public static void render(RenderGuiLayerEvent.Post event) {
        var mc = Minecraft.getInstance();
        var target = ClientQuests.snapshot().navigation().orElse(null);
        if (!event.getName().equals(VanillaGuiLayers.CROSSHAIR) || mc.options.hideGui || mc.screen != null
                || mc.level == null || target == null || !target.equals(projectedFor) || point == null
                || !mc.level.dimension().location().equals(target.dimension())) return;
        GuiGraphics g = event.getGuiGraphics();
        int x = (int) Math.round(point.x()), y = (int) Math.round(point.y());
        g.flush();
        RenderSystem.disableDepthTest();
        // A fixed 9px diamond and, at the edge, a 7px arrow; no world-space geometry or depth sampling.
        for (int n = -4; n <= 4; n++) {
            int side = 4 - Math.abs(n);
            g.fill(x - side, y + n, x - side + 1, y + n + 1, SakuraPalette.GOLD);
            g.fill(x + side, y + n, x + side + 1, y + n + 1, SakuraPalette.GOLD);
        }
        if (point.edge()) {
            double angle = point.angle();
            for (int n = 0; n <= 5; n++) {
                int ax = x + (int) Math.round(Math.cos(angle) * (8 - n));
                int ay = y + (int) Math.round(Math.sin(angle) * (8 - n));
                g.fill(ax, ay, ax + 2, ay + 2, SakuraPalette.CREAM);
                if (n < 4) for (int side : new int[]{-1, 1}) {
                    int bx = ax + (int) Math.round(-Math.sin(angle) * n * side);
                    int by = ay + (int) Math.round(Math.cos(angle) * n * side);
                    g.fill(bx, by, bx + 1, by + 1, SakuraPalette.CREAM);
                }
            }
        }
        var text = Component.translatable("hud.gensokyou.quest.distance", distance);
        int textW = mc.font.width(text);
        int tx = Math.clamp(x - textW / 2, 4, Math.max(4, mc.getWindow().getGuiScaledWidth() - textW - 4));
        int ty = y + 10;
        if (ty + 12 >= mc.getWindow().getGuiScaledHeight()) ty = y - 18;
        g.fill(tx - 2, ty - 2, tx + textW + 2, ty + 10, SakuraPalette.DARK);
        g.drawString(mc.font, text, tx, ty, SakuraPalette.CREAM, false);
        g.flush();
        RenderSystem.enableDepthTest();
    }
    private QuestNavigationRenderer() {}
}
