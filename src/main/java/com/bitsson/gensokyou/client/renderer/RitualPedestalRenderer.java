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
import net.minecraft.util.Mth;
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
 *
 * <p>姿态表现状态（过渡进度 / 自转相位）为纯客户端缓存，只在台面有物时存在：
 * 台面清空即清除，新物品从静置态起算（修复：旧实现状态冻结导致新物品暴旋）。
 * 自转由相位累积给出，MUST NOT 用「绝对游戏时间 × 进度」直接算角度。
 */
public class RitualPedestalRenderer implements BlockEntityRenderer<RitualPedestalBlockEntity> {

    /** 台面高度（祭品台为整砖，顶面 y=1）。 */
    private static final double SURFACE_Y = 1.0D;
    /** 静置态防 z-fighting 的极小离隙（不算悬浮）。 */
    private static final double REST_GAP = 0.001D;
    /** 激活立起时底缘离台间隙。 */
    private static final double ACTIVE_GAP = 0.06D;
    private static final float SPIN_DEGREES_PER_TICK = 1.2F;
    /** 每游戏刻的过渡速率（约 0.7 秒完成切换）。 */
    private static final float BLEND_PER_TICK = 0.06F;
    private static final float SCALE = 0.55F;

    /** 每台位的姿态表现状态：过渡进度（0..1）、上次时间、自转相位（度）。 */
    private static final class Pose {
        float progress;
        double lastTime;
        float spinDeg;
    }

    /** 客户端动画状态缓存：pos → 位姿状态（台面清空即移除）。 */
    private static final Map<BlockPos, Pose> ANIM = new WeakHashMap<>();

    public RitualPedestalRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(RitualPedestalBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        BlockPos pos = blockEntity.getBlockPos();
        ItemStack held = blockEntity.getHeld();
        if (held.isEmpty()) {
            // 台面清空（仪式吞食 / 玩家取走）：清除位姿状态，杜绝跨占位残留
            ANIM.remove(pos);
            return;
        }
        Level level = blockEntity.getLevel();
        double time = (level == null ? 0L : level.getGameTime()) + partialTick;

        boolean active = blockEntity.isRituallyActive();
        Pose pose = ANIM.computeIfAbsent(pos, k -> {
            Pose p = new Pose();
            // 载入即按现状收敛：首次渲染不播放插值，避免"凭空起旋"
            p.progress = active ? 1F : 0F;
            p.lastTime = time;
            return p;
        });
        float dt = advanceProgress(pose, active, time);
        float blend = pose.progress;
        float eased = blend * blend * (3F - 2F * blend);
        // 自转相位：仅随激活进度累积；进度归零后冻结（不重置，避免平躺瞬间偏航跳变）
        if (eased > 0F) {
            pose.spinDeg = (pose.spinDeg + SPIN_DEGREES_PER_TICK * eased * dt) % 360F;
        }

        // 模型感知底缘对齐：实测该物品在 FIXED 上下文下的包围盒（含 display.fixed 与 -0.5 归中）
        ItemFixedBounds bounds = ItemFixedBounds.of(held, level);
        // 静置：θ=90° 放倒，竖直向对应模型 -Z，底缘 = y - SCALE*maxZ，贴台 + REST_GAP 防 z-fighting
        double restY = SURFACE_Y + REST_GAP + SCALE * bounds.maxZ;
        // 激活：θ=0 竖直立起，底缘 = y + SCALE*minY，保持 ACTIVE_GAP 离台
        double activeY = SURFACE_Y + ACTIVE_GAP - SCALE * bounds.minY;

        int light = level == null ? packedLight
                : LevelRenderer.getLightColor(level, pos.above());

        poseStack.pushPose();
        double y = restY + (activeY - restY) * eased
                + Math.sin(time * 0.08D) * 0.04D * eased;
        poseStack.translate(0.5D, y, 0.5D);
        // 先绕世界 Y 轴慢旋，倾角随进度立起：eased=0 平躺（FIXED 原始立牌放倒 90°）、eased=1 竖直
        poseStack.mulPose(Axis.YP.rotationDegrees(pose.spinDeg));
        poseStack.mulPose(Axis.XP.rotationDegrees(90F * (1F - eased)));
        poseStack.scale(SCALE, SCALE, SCALE);
        Minecraft.getInstance().getItemRenderer().renderStatic(held, ItemDisplayContext.FIXED,
                light, OverlayTexture.NO_OVERLAY, poseStack, bufferSource, level,
                (int) pos.asLong());
        poseStack.popPose();
    }

    /** 推进过渡进度并写入 pose，返回本帧游戏刻增量（clamp 防跳帧）；相位累积复用该增量。 */
    private static float advanceProgress(Pose pose, boolean target, double time) {
        double elapsed = Math.max(0.0D, Math.min(4.0D, time - pose.lastTime));
        pose.lastTime = time;
        float progress = pose.progress + (float) elapsed * BLEND_PER_TICK * (target ? 1F : -1F);
        pose.progress = Mth.clamp(progress, 0F, 1F);
        return (float) elapsed;
    }
}
