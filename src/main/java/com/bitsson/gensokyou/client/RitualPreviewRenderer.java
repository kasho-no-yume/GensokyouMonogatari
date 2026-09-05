package com.bitsson.gensokyou.client;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.client.renderer.RitualGhostRenderTypes;
import com.bitsson.gensokyou.item.RitualBuilderItem;
import com.bitsson.gensokyou.registry.ModBlocks;
import com.bitsson.gensokyou.ritual.RitualBuilderPlacement;
import com.bitsson.gensokyou.ritual.RitualPattern;
import com.bitsson.gensokyou.ritual.RitualPatternLoader;
import com.bitsson.gensokyou.ritual.RitualPreviewState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * 仪式构建器投影渲染：预览态存在时，把全部待放置格以正确位置、正确朝向的目标
 * BlockState 渲染为半透明幽灵（白 = 待放置，红 = 被占冲突），AIR 谓词格被占画红线框，
 * 已满足/锚点格不画。三态分类每帧基于本地世界状态经共用 {@code classify} 重算——
 * 预览期间拆/塞方块当帧变色，零额外网络同步。
 */
@EventBusSubscriber(modid = Gensokyou.MODID, value = Dist.CLIENT)
public final class RitualPreviewRenderer {

    private static final float[] TINT_WHITE = {1.0F, 1.0F, 1.0F, 0.45F};
    private static final float[] TINT_RED = {1.0F, 0.2F, 0.2F, 0.55F};

    private RitualPreviewRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        RitualPreviewState preview = ClientRitualPreviewState.active();
        if (preview == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null
                || RitualGhostRenderTypes.ritualGhostShader == null) {
            return;
        }
        // 门控（每帧）：不校验组件内选择（零槽菜单关闭后组件回同步时序不可靠，选择权威在服务端）
        if (!isHoldingBuilder(minecraft.player)
                || !minecraft.level.dimension().equals(preview.dimension())
                || !minecraft.level.getBlockState(preview.corePos()).is(ModBlocks.RITUAL_CORE.get())) {
            return;
        }
        Optional<RitualPattern> patternOpt = RitualPatternLoader.byId(preview.patternId());
        if (patternOpt.isEmpty()) {
            return; // 热重载宽限：图案被移除则不画
        }
        RitualPattern pattern = patternOpt.get();

        RitualBuilderPlacement.Classification classification = RitualBuilderPlacement
                .classify(minecraft.level, preview.corePos(), pattern, preview.tier());

        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        BlockRenderDispatcher dispatcher = minecraft.getBlockRenderer();

        // 待放置 = 白幽灵（正确位置、正确朝向的目标 BlockState）
        setTint(TINT_WHITE);
        for (RitualPattern.BlockEntry entry : classification.pending()) {
            renderGhostEntry(dispatcher, pose, buffers, preview.corePos(), pattern, entry,
                    preview.tier(), RitualGhostRenderTypes.GHOST);
        }
        buffers.endBatch(RitualGhostRenderTypes.GHOST);
        // 被占冲突 = 红幽灵（同一目标态、红色调）
        setTint(TINT_RED);
        for (RitualBuilderPlacement.Conflict conflict : classification.conflicts()) {
            renderGhostEntry(dispatcher, pose, buffers, preview.corePos(), pattern, conflict.entry(),
                    preview.tier(), RitualGhostRenderTypes.GHOST_CONFLICT);
        }
        buffers.endBatch(RitualGhostRenderTypes.GHOST_CONFLICT);
        // AIR 谓词格被占 = 红框（复用冲突渲染器线画法）
        if (!classification.airConflicts().isEmpty()) {
            VertexConsumer lines = buffers.getBuffer(RitualConflictRenderer.OVERLAY_LINES);
            for (RitualBuilderPlacement.Conflict conflict : classification.airConflicts()) {
                LevelRenderer.renderLineBox(pose, lines,
                        new AABB(conflict.pos()).inflate(0.002D), 1.0F, 0.15F, 0.15F, 1.0F);
            }
            buffers.endBatch(RitualConflictRenderer.OVERLAY_LINES);
        }
        pose.popPose();
    }

    private static void renderGhostEntry(BlockRenderDispatcher dispatcher, PoseStack pose,
                                         MultiBufferSource buffers, BlockPos anchor,
                                         RitualPattern pattern, RitualPattern.BlockEntry entry,
                                         int tier, RenderType type) {
        BlockState desired = RitualBuilderPlacement.resolveState(pattern, entry, tier);
        if (desired == null) {
            return; // 无法实例化（同搭建跳过规则），不画
        }
        BlockPos target = anchor.offset(entry.x(), entry.y(), entry.z());
        pose.pushPose();
        pose.translate(target.getX(), target.getY(), target.getZ());
        dispatcher.renderSingleBlock(desired, pose, buffers,
                LightTexture.FULL_BRIGHT, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                ModelData.EMPTY, type);
        pose.popPose();
    }

    /** 各批次绘制前写入本批 Tint；Uniform.set 脏标记在 apply 时上传。 */
    private static void setTint(float[] tint) {
        if (RitualGhostRenderTypes.tintUniform != null) {
            RitualGhostRenderTypes.tintUniform.set(tint[0], tint[1], tint[2], tint[3]);
        }
    }

    private static boolean isHoldingBuilder(Player player) {
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        return main.getItem() instanceof RitualBuilderItem
                || off.getItem() instanceof RitualBuilderItem;
    }
}
