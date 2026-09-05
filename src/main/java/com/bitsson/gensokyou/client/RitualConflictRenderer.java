package com.bitsson.gensokyou.client;

import com.bitsson.gensokyou.Gensokyou;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
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
 * 构建器冲突红框渲染：在关卡粒子阶段后，对 {@link ClientRitualConflictState} 中的
 * 每个冲突方块绘制贴合单格轮廓的红色线框（{@link LevelRenderer#renderLineBox}）。
 * 用无深度测试的自定义 {@link RenderType}，使线框不被前方方块遮挡（始终可见）。
 */
@EventBusSubscriber(modid = Gensokyou.MODID, value = Dist.CLIENT)
public final class RitualConflictRenderer {

    /** 冲突红框线渲染类型（ RitualPreviewRenderer 的 AIR 冲突红框复用同一实例）。 */
    static final RenderType OVERLAY_LINES = RenderType.create(
            "ritual_conflict_overlay_lines",
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.LINES,
            256,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                    .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.empty()))
                    .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setOutputState(RenderStateShard.ITEM_ENTITY_TARGET)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .createCompositeState(false));

    private RitualConflictRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        var conflicts = ClientRitualConflictState.active();
        if (conflicts.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        VertexConsumer buffer = minecraft.renderBuffers().bufferSource()
                .getBuffer(OVERLAY_LINES);
        for (BlockPos pos : conflicts.keySet()) {
            AABB box = new AABB(pos).inflate(0.002D);
            LevelRenderer.renderLineBox(pose, buffer, box, 1.0F, 0.15F, 0.15F, 1.0F);
        }
        pose.popPose();
    }
}
