package com.bitsson.gensokyou.client.resident;

import com.bitsson.gensokyou.Gensokyou;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** A single shared texture/model batch in the normal depth-tested, lit world pass. */
@EventBusSubscriber(modid = Gensokyou.MODID, value = Dist.CLIENT)
public final class ResidentRenderer {
    private static final ResourceLocation TEXTURE = Gensokyou.id("textures/entity/village_resident.png");
    private static final RenderType TYPE = RenderType.entityCutoutNoCull(TEXTURE);
    private static ResidentModel model;

    private ResidentRenderer() {}
    static void invalidateModel() { model = null; }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        ClientResidents.drawn(0);
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || ClientResidents.visible().isEmpty()) return;
        if (model == null) model = new ResidentModel();
        var camera = event.getCamera().getPosition();
        float partial = mc.isPaused() ? 1 : event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double age = mc.level.getGameTime() + (mc.isPaused() ? 0 : partial);
        var pose = event.getPoseStack();
        var buffers = mc.renderBuffers().bufferSource();
        int rendered = 0;
        for (ResidentWalker resident : ClientResidents.visible()) {
            var frame = resident.frame(partial);
            if (frame == null) continue;
            var p = frame.position();
            double distance = camera.distanceToSqr(p.x(), p.y(), p.z());
            if (distance > ResidentScene.VIEW_DISTANCE * ResidentScene.VIEW_DISTANCE) continue;
            float scale = resident.appearance().scale();
            AABB bounds = new AABB(p.x() - 0.55, p.y(), p.z() - 0.55,
                    p.x() + 0.55, p.y() + 2.4 * scale, p.z() + 0.55);
            if (!event.getFrustum().isVisible(bounds) || !loaded(mc.level, bounds)) continue;
            int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(p.x(), p.y() + 1, p.z()));
            pose.pushPose();
            pose.translate(p.x() - camera.x, p.y() - camera.y, p.z() - camera.z);
            pose.mulPose(Axis.YP.rotationDegrees(180 - frame.yaw()));
            pose.scale(-scale, -scale, scale);
            pose.translate(0, -1.5, 0);
            model.render(pose, buffers.getBuffer(TYPE), light, resident.appearance(), frame, age, distance < 40 * 40);
            pose.popPose();
            rendered++;
        }
        if (rendered > 0) buffers.endBatch(TYPE);
        ClientResidents.drawn(rendered);
    }

    private static boolean loaded(ClientLevel level, AABB bounds) {
        // Geometry was validated at tick rate. Re-check only cache availability per frame,
        // so interpolating a half-block step does not make the resident flicker/disappear.
        for (int x = (int) Math.floor(bounds.minX) >> 4; x <= (int) Math.floor(bounds.maxX) >> 4; x++) {
            for (int z = (int) Math.floor(bounds.minZ) >> 4; z <= (int) Math.floor(bounds.maxZ) >> 4; z++) {
                if (!level.hasChunk(x, z)) return false;
            }
        }
        return true;
    }
}
