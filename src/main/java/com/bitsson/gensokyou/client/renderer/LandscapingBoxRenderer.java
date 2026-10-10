package com.bitsson.gensokyou.client.renderer;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.block.entity.LandscapingBlockEntity;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.OptionalDouble;

/**
 * 灵力筑基器的范围线框渲染。
 *
 * <p><b>为什么走 {@code RenderLevelStageEvent} 而不是 BER</b>：BER 的渲染阶段早于半透明地形
 * （水），线框会被水盖住。这里在 {@code AFTER_TRANSLUCENT_BLOCKS} 阶段绘制（水之后），
 * 遍历 {@link LandscapingBlockEntity#clientActive()} 里已加载的实例。
 *
 * <p><b>两趟绘制</b>：先按深度测试画不透明（被方块挡住处不画），再关深度测试以 alpha 0.5 画一遍，
 * 于是可见处实色、被遮挡处半透明，一眼看出立体走向。
 */
@EventBusSubscriber(modid = Gensokyou.MODID, value = Dist.CLIENT)
public final class LandscapingBoxRenderer {

    private static final RenderType LINES_DEPTH = RenderType.create(
            "landscaping_box_depth",
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.LINES,
            256,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                    .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.empty()))
                    .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .createCompositeState(false));

    private static final RenderType LINES_OVERLAY = RenderType.create(
            "landscaping_box_overlay",
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.LINES,
            256,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                    .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.empty()))
                    .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .createCompositeState(false));

    /** 线框色：明亮的灵异绿。 */
    private static final float R = 0.25F;
    private static final float G = 1.0F;
    private static final float B = 0.45F;
    /** 被方块遮挡部分的透明度。 */
    private static final float OCCLUDED_ALPHA = 0.5F;

    private LandscapingBoxRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();

        boolean drew = false;
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        for (LandscapingBlockEntity device : LandscapingBlockEntity.clientActive()) {
            if (device.getLevel() != minecraft.level) {
                continue;
            }
            int sizeX = device.getSizeX();
            int sizeZ = device.getSizeZ();
            int height = device.getHeight();
            if (sizeX <= 0 || sizeZ <= 0 || height <= 0) {
                continue;
            }
            int halfX = (sizeX - 1) / 2;
            int halfZ = (sizeZ - 1) / 2;
            BlockPos p = device.getBlockPos();
            // 长/宽以方块为中心；高以方块下面一格为底向上
            AABB box = new AABB(p.getX() - halfX, p.getY() - 1.0D, p.getZ() - halfZ,
                    p.getX() - halfX + sizeX, p.getY() - 1.0D + height,
                    p.getZ() - halfZ + sizeZ);
            VertexConsumer depth = buffers.getBuffer(LINES_DEPTH);
            LevelRenderer.renderLineBox(pose, depth, box, R, G, B, 1.0F);
            VertexConsumer overlay = buffers.getBuffer(LINES_OVERLAY);
            LevelRenderer.renderLineBox(pose, overlay, box, R, G, B, OCCLUDED_ALPHA);
            drew = true;
        }
        pose.popPose();
        if (drew) {
            buffers.endBatch(LINES_DEPTH);
            buffers.endBatch(LINES_OVERLAY);
        }
    }
}
