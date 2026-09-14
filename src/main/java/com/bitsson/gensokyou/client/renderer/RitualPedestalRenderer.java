package com.bitsson.gensokyou.client.renderer;

import com.bitsson.gensokyou.block.entity.RitualPedestalBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 祭品台渲染：默认祭品平躺静置于台面；所属仪式激活时悬浮、立起并绕纵轴旋转
 * （倾角随过渡进度从 90° 平躺渐变到 0° 竖直，无跳变）。
 * 使用 FIXED 上下文手动控制姿态（不受模型 ground 变换影响），
 * 光照采样台面上方一格（避免采到方块内部导致纯黑）。
 */
public class RitualPedestalRenderer implements BlockEntityRenderer<RitualPedestalBlockEntity> {

    private static final double REST_Y = 1.01D;
    /** 立姿半高 ~0.28 + 浮动 ±0.04：中心 1.34 保证立起物品底缘不切台面（顶面 y=1）。 */
    private static final double FLOAT_Y = 1.34D;
    private static final float SPIN_DEGREES_PER_TICK = 1.2F;
    /** 每游戏刻的过渡速率（约 0.7 秒完成切换）。 */
    private static final float BLEND_PER_TICK = 0.06F;
    private static final float SCALE = 0.55F;
    /** FIXED 上下文以中心为原点：方块类物品需抬高半高，避免沉入台面。 */
    private static final double BLOCK_HALF_HEIGHT = 0.5D * SCALE;

    /** 客户端动画状态缓存：pos → {进度 0..1, 上次时间}。 */
    private static final Map<BlockPos, float[]> ANIM = new WeakHashMap<>();

    public RitualPedestalRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(RitualPedestalBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        ItemStack held = blockEntity.getHeld();
        if (held.isEmpty()) {
            return;
        }
        Level level = blockEntity.getLevel();
        double time = (level == null ? 0L : level.getGameTime()) + partialTick;

        float blend = advanceBlend(blockEntity.getBlockPos(), blockEntity.isRituallyActive(), time);
        float eased = blend * blend * (3F - 2F * blend);

        boolean blockShaped = held.getItem() instanceof net.minecraft.world.item.BlockItem;
        double restY = REST_Y + (blockShaped ? BLOCK_HALF_HEIGHT : 0D);
        double floatBase = FLOAT_Y + (blockShaped ? BLOCK_HALF_HEIGHT : 0D);

        int light = level == null ? packedLight
                : LevelRenderer.getLightColor(level, blockEntity.getBlockPos().above());

        poseStack.pushPose();
        double y = restY + (floatBase - restY) * eased
                + Math.sin(time * 0.08D) * 0.04D * eased;
        poseStack.translate(0.5D, y, 0.5D);
        // 先绕世界 Y 轴慢旋，倾角随进度立起：eased=0 平躺（FIXED 原始立牌放倒 90°）、eased=1 竖直
        poseStack.mulPose(Axis.YP.rotationDegrees((float) time * SPIN_DEGREES_PER_TICK * eased));
        poseStack.mulPose(Axis.XP.rotationDegrees(90F * (1F - eased)));
        poseStack.scale(SCALE, SCALE, SCALE);
        Minecraft.getInstance().getItemRenderer().renderStatic(held, ItemDisplayContext.FIXED,
                light, OverlayTexture.NO_OVERLAY, poseStack, bufferSource, level,
                (int) blockEntity.getBlockPos().asLong());
        poseStack.popPose();
    }

    /** 帧推进过渡进度（smoothstep 前的线性值）。 */
    private static float advanceBlend(BlockPos pos, boolean target, double time) {
        float[] state = ANIM.computeIfAbsent(pos, k -> new float[]{target ? 1F : 0F, (float) time});
        float elapsed = Math.max(0F, Math.min(4F, (float) time - state[1]));
        state[1] = (float) time;
        float progress = state[0] + elapsed * BLEND_PER_TICK * (target ? 1F : -1F);
        progress = Math.max(0F, Math.min(1F, progress));
        state[0] = progress;
        return progress;
    }
}
