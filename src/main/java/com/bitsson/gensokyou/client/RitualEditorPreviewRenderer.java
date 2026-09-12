package com.bitsson.gensokyou.client;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.client.renderer.RitualGhostRenderTypes;
import com.bitsson.gensokyou.item.RitualWandItem;
import com.bitsson.gensokyou.registry.ModBlocks;
import com.bitsson.gensokyou.ritual.editor.RitualEditorPlacement;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * 编辑杖力建三色投影渲染：绿=新放置 / 橙=覆盖 / 品红=sweep 清除线框。
 * 预览期间每帧基于本地世界经共用 {@link RitualEditorPlacement#plan} 重算（零逐格同步）；
 * 持杖/同维度/锚点核心在场任一不满足即不画（服务端态随实体消亡清空，客户端残留由门控兜底）。
 */
@EventBusSubscriber(modid = Gensokyou.MODID, value = Dist.CLIENT)
public final class RitualEditorPreviewRenderer {

    private static final float[] TINT_GREEN = {0.35F, 1.0F, 0.35F, 0.5F};
    private static final float[] TINT_ORANGE = {1.0F, 0.6F, 0.1F, 0.6F};

    private RitualEditorPreviewRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        ClientRitualEditorState.Active active = ClientRitualEditorState.active();
        if (active == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null
                || RitualGhostRenderTypes.ritualGhostShader == null) {
            return;
        }
        if (!isHoldingWand(minecraft.player)
                || !minecraft.level.dimension().equals(active.state().dimension())
                || !minecraft.level.getBlockState(active.state().anchor()).is(ModBlocks.RITUAL_CORE.get())) {
            return;
        }
        RitualEditorPlacement.Plan plan = RitualEditorPlacement.plan(minecraft.level,
                active.state().anchor(), active.pattern(), active.state().level(),
                active.state().workspace());

        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        BlockRenderDispatcher dispatcher = minecraft.getBlockRenderer();

        setTint(TINT_GREEN);
        for (RitualEditorPlacement.Op op : plan.ops().values()) {
            if (op.kind() == RitualEditorPlacement.OpKind.PLACE) {
                renderGhost(dispatcher, pose, buffers, op, RitualGhostRenderTypes.GHOST);
            }
        }
        buffers.endBatch(RitualGhostRenderTypes.GHOST);
        setTint(TINT_ORANGE);
        for (RitualEditorPlacement.Op op : plan.ops().values()) {
            if (op.kind() == RitualEditorPlacement.OpKind.OVERWRITE) {
                renderGhost(dispatcher, pose, buffers, op, RitualGhostRenderTypes.GHOST_CONFLICT);
            }
        }
        buffers.endBatch(RitualGhostRenderTypes.GHOST_CONFLICT);
        VertexConsumer lines = buffers.getBuffer(RitualConflictRenderer.OVERLAY_LINES);
        for (RitualEditorPlacement.Op op : plan.ops().values()) {
            if (op.kind() == RitualEditorPlacement.OpKind.SWEEP) {
                LevelRenderer.renderLineBox(pose, lines,
                        new AABB(op.pos()).inflate(0.002D), 1.0F, 0.15F, 1.0F, 1.0F);
            }
        }
        buffers.endBatch(RitualConflictRenderer.OVERLAY_LINES);
        pose.popPose();
    }

    private static void renderGhost(BlockRenderDispatcher dispatcher, PoseStack pose,
                                    MultiBufferSource buffers, RitualEditorPlacement.Op op,
                                    RenderType type) {
        BlockState desired = op.desired();
        if (desired == null) {
            return;
        }
        pose.pushPose();
        pose.translate(op.pos().getX(), op.pos().getY(), op.pos().getZ());
        dispatcher.renderSingleBlock(desired, pose, buffers, LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, ModelData.EMPTY, type);
        pose.popPose();
    }

    private static void setTint(float[] tint) {
        if (RitualGhostRenderTypes.tintUniform != null) {
            RitualGhostRenderTypes.tintUniform.set(tint[0], tint[1], tint[2], tint[3]);
        }
    }

    private static boolean isHoldingWand(Player player) {
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        return main.getItem() instanceof RitualWandItem
                || off.getItem() instanceof RitualWandItem;
    }
}
