package com.bitsson.gensokyou.client;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.entity.NpcQuestMarker;
import com.bitsson.gensokyou.entity.TouhouNpcEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** 共用世界渲染入口，兼容原版人形与 GeckoLib NPC，标记始终朝向观察者。 */
@EventBusSubscriber(modid = Gensokyou.MODID, value = Dist.CLIENT)
public final class NpcQuestMarkerRenderer {
    private static final int YELLOW = 0xFFFFD83D;
    private static final double MAX_DISTANCE_SQUARED = 64.0D * 64.0D;

    private NpcQuestMarkerRenderer() {}

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.options.hideGui) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        PoseStack pose = event.getPoseStack();
        var buffers = minecraft.renderBuffers().bufferSource();
        boolean rendered = false;
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof TouhouNpcEntity npc) || !npc.isAlive() || npc.isInvisible()
                    || ClientQuests.marker(npc.questRole()) == NpcQuestMarker.NONE
                    || npc.distanceToSqr(camera) > MAX_DISTANCE_SQUARED
                    || !event.getFrustum().isVisible(npc.getBoundingBox().inflate(0.5D, 1.0D, 0.5D))) {
                continue;
            }
            Vec3 position = npc.getPosition(partialTick);
            var text = Component.literal(ClientQuests.marker(npc.questRole()).symbol()).withStyle(style -> style.withBold(true));
            pose.pushPose();
            pose.translate(position.x - camera.x, position.y - camera.y + npc.getBbHeight() + 0.65D,
                    position.z - camera.z);
            pose.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
            pose.scale(-0.055F, -0.055F, 0.055F);
            minecraft.font.drawInBatch(text, -minecraft.font.width(text) / 2.0F, -minecraft.font.lineHeight,
                    YELLOW, true, pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
            pose.popPose();
            rendered = true;
        }
        if (rendered) {
            // 此阶段位于实体提交之后，显式提交文字，避免留到下一帧或被 GUI 的矩阵消费。
            buffers.endBatch();
        }
    }
}
