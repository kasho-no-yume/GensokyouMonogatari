package com.bitsson.gensokyou.client.renderer;

import com.bitsson.gensokyou.block.entity.SukimaBlockEntity;
import com.bitsson.gensokyou.client.GensokyouTextures;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * 隙间传送门渲染器：2 格高、斜 10° 的眼形封闭棱壳。
 *
 * <p><b>视觉结构：</b>
 * <ol>
 *   <li>棱壳 —— 前后透镜形端面 + 环形侧壁，全部用 {@link RenderType#endPortal()}
 *       （原版末地门渲染类型）填充：任何视角往里看都是静止不动、无视差的星空内景，
 *       与末地门同源效果；侧壁保证侧视时不退化成细线</li>
 *   <li>眼睑轮廓 —— 16×32 眼形外描边贴图，以 cutout 覆盖于前后端面外侧
 *       （外偏移 0.001 防 z-fighting，先壳后轮廓）</li>
 * </ol>
 *
 * <p><b>几何约定（与 tools/textures/sukima.py 一致）：</b>
 * 尖端在眼的中线（世界高度 {@link #CENTER_Y}），上盖 {@link #UPPER_LID}=0.85、
 * 下弧 {@link #LOWER_LID}=1.15（单位：格），总高恰 2 格；贴图 V=0 对应上盖顶点。
 *
 * <p><b>未来换肤：</b>「充满眼睛的紫黑色空间」只需把 {@link #INTERIOR_RENDER_TYPE}
 * 换成自定义核心着色器的 RenderType（RegisterShadersEvent 注册），几何无需改动。
 */
public class SukimaPortalRenderer implements BlockEntityRenderer<SukimaBlockEntity> {

    /** 内景渲染类型 —— 单一换肤点。 */
    private static final RenderType INTERIOR_RENDER_TYPE = RenderType.endPortal();

    /** 眼睑整体倾角（绕面法线，度）。 */
    private static final float TILT_DEGREES = 10.0F;

    /** 棱壳半进深（格）。 */
    private static final float HALF_DEPTH = 0.125F;

    /** 上盖/下弧相对中线高度（格）；总高 = 和 = 2。 */
    private static final float UPPER_LID = 0.85F;
    private static final float LOWER_LID = 1.15F;

    /** 眼中线世界高度：下弧最低点贴方块底。 */
    private static final float CENTER_Y = LOWER_LID;

    /** 透镜竖向条带数（条带越少轮廓越棱角，16 与 16px 贴图精度匹配）。 */
    private static final int STRIPS = 16;

    /** 眼睑轮廓外偏移（防 z-fighting）。 */
    private static final float OUTLINE_OFFSET = 0.001F;

    private static final float SQRT_LIMIT = 1.0F - 1.0E-6F;

    public SukimaPortalRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(SukimaBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Level level = blockEntity.getLevel();
        int light = level == null ? packedLight
                : LevelRenderer.getLightColor(level, blockEntity.getBlockPos());

        poseStack.pushPose();
        // 平移到眼中心后整体绕面法线（Z）倾斜 10°，网格与贴图恒对齐
        poseStack.translate(0.5D, CENTER_Y, 0.5D);
        poseStack.mulPose(Axis.ZP.rotationDegrees(TILT_DEGREES));

        this.renderShell(poseStack, bufferSource);
        this.renderOutline(poseStack, bufferSource, light);

        poseStack.popPose();
    }

    /**
     * 眼形封闭棱壳：前后透镜端面 + 环形侧壁，全部走末地门渲染类型（POSITION 顶点格式）。
     *
     * <p>透镜按竖条带切成纯四顶点 quad（正面外向绕序、背面反向），侧壁正反两份绕序——
     * 不依赖任何顶点重排/退化行为，杜绝因扇形三角塞入 QUADS 模式导致的反面破碎。
     */
    private void renderShell(PoseStack poseStack, MultiBufferSource bufferSource) {
        VertexConsumer consumer = bufferSource.getBuffer(INTERIOR_RENDER_TYPE);
        PoseStack.Pose pose = poseStack.last();

        for (int i = 0; i < STRIPS; i++) {
            float t0 = -1.0F + 2.0F * i / STRIPS;
            float t1 = -1.0F + 2.0F * (i + 1) / STRIPS;
            float x0 = 0.5F * t0;
            float x1 = 0.5F * t1;
            float up0 = UPPER_LID * sqrtHalf(t0);
            float up1 = UPPER_LID * sqrtHalf(t1);
            float lo0 = -LOWER_LID * sqrtHalf(t0);
            float lo1 = -LOWER_LID * sqrtHalf(t1);

            // 前端面（+Z 外向 CCW）；条带两端在眼尖处 up==lo，退化为带重复顶点的合法 quad
            this.shellQuad(consumer, pose,
                    x0, lo0, HALF_DEPTH, x1, lo1, HALF_DEPTH, x1, up1, HALF_DEPTH, x0, up0, HALF_DEPTH);
            // 后端面（-Z 外向，反绕序）
            this.shellQuad(consumer, pose,
                    x0, lo0, -HALF_DEPTH, x0, up0, -HALF_DEPTH, x1, up1, -HALF_DEPTH, x1, lo1, -HALF_DEPTH);

            // 上弧侧壁 + 下弧侧壁（正反两份绕序，任意侧视均可见）
            this.shellQuad(consumer, pose,
                    x0, up0, HALF_DEPTH, x1, up1, HALF_DEPTH, x1, up1, -HALF_DEPTH, x0, up0, -HALF_DEPTH);
            this.shellQuad(consumer, pose,
                    x0, up0, -HALF_DEPTH, x1, up1, -HALF_DEPTH, x1, up1, HALF_DEPTH, x0, up0, HALF_DEPTH);
            this.shellQuad(consumer, pose,
                    x0, lo0, HALF_DEPTH, x1, lo1, HALF_DEPTH, x1, lo1, -HALF_DEPTH, x0, lo0, -HALF_DEPTH);
            this.shellQuad(consumer, pose,
                    x0, lo0, -HALF_DEPTH, x1, lo1, -HALF_DEPTH, x1, lo1, HALF_DEPTH, x0, lo0, HALF_DEPTH);
        }
    }

    private float sqrtHalf(float t) {
        return (float) Math.sqrt(Math.max(0.0F, SQRT_LIMIT - t * t));
    }

    /** 内景顶点：POSITION 格式，仅位置。 */
    private void shellVertex(VertexConsumer consumer, PoseStack.Pose pose,
                             float x, float y, float z) {
        consumer.addVertex(pose.pose(), x, y, z);
    }

    private void shellQuad(VertexConsumer consumer, PoseStack.Pose pose,
                           float x0, float y0, float z0,
                           float x1, float y1, float z1,
                           float x2, float y2, float z2,
                           float x3, float y3, float z3) {
        this.shellVertex(consumer, pose, x0, y0, z0);
        this.shellVertex(consumer, pose, x1, y1, z1);
        this.shellVertex(consumer, pose, x2, y2, z2);
        this.shellVertex(consumer, pose, x3, y3, z3);
    }

    /**
     * 渲染包围盒覆盖上下两格：传送门为 2 格高，避免斜视角下上半天被视锥裁剪。
     */
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(SukimaBlockEntity blockEntity) {
        return new net.minecraft.world.phys.AABB(blockEntity.getBlockPos()).expandTowards(0.0D, 1.0D, 0.0D);
    }

    /**
     * 眼睑轮廓：全幅 16×32 quad 贴于前后端面外侧，cutout 挖去眼形以外区域。
     * UV 与棱壳几何对齐：U=0..1 ↔ x=-0.5..0.5，V=0..1 ↔ 上盖顶..下弧底。
     */
    private void renderOutline(PoseStack poseStack, MultiBufferSource bufferSource, int light) {
        VertexConsumer consumer = bufferSource.getBuffer(
                RenderType.entityCutoutNoCull(GensokyouTextures.SUKIMA));
        PoseStack.Pose pose = poseStack.last();

        float halfW = 0.5F;
        float top = UPPER_LID;
        float bottom = -LOWER_LID;
        float zFront = HALF_DEPTH + OUTLINE_OFFSET;
        float zBack = -HALF_DEPTH - OUTLINE_OFFSET;

        this.outlineQuad(consumer, pose, halfW, top, bottom, zFront, light);
        this.outlineQuad(consumer, pose, halfW, top, bottom, zBack, light);
    }

    private void outlineQuad(VertexConsumer consumer, PoseStack.Pose pose,
                             float halfW, float top, float bottom, float z, int light) {
        this.outlineVertex(consumer, pose, -halfW, bottom, z, 0.0F, 1.0F, light);
        this.outlineVertex(consumer, pose, halfW, bottom, z, 1.0F, 1.0F, light);
        this.outlineVertex(consumer, pose, halfW, top, z, 1.0F, 0.0F, light);
        this.outlineVertex(consumer, pose, -halfW, top, z, 0.0F, 0.0F, light);
    }

    /** 轮廓顶点：NEW_ENTITY 格式（位置/颜色/UV/overlay/光照/法线）。 */
    private void outlineVertex(VertexConsumer consumer, PoseStack.Pose pose,
                               float x, float y, float z, float u, float v, int light) {
        consumer.addVertex(pose.pose(), x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, 0.0F, 0.0F, 1.0F);
    }
}
