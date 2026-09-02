package com.bitsson.gensokyou.client.renderer;

import com.bitsson.gensokyou.block.entity.SukimaBlockEntity;
import com.bitsson.gensokyou.client.GensokyouTextures;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * 隙间传送门渲染器：眼形「窗」——billboard 眼睑框 + 末地门式虚空内景。
 *
 * <p><b>视觉结构（框与内景分离）：</b>
 * <ol>
 *   <li>内景 —— 眼形透镜面（竖条带扇面几何，尖端在中线），填
 *       {@link SukimaPortalRenderTypes#voidPortal}：克隆原版末地门渲染状态、
 *       双贴图槽位换成自有眼睛虚空贴图——着色器从<b>裁剪空间投影采样</b>
 *       （rendertype_end_portal.vsh 的 {@code projection_from_position(gl_Position)}），
 *       效果锚定屏幕而非几何表面，再叠加 16 层缩放/旋转/GameTime 漂移的视差层——
 *       因此放在 billboard 上依然呈现「锚定的层叠虚空」而非糊在框上的静态图，
 *       且不透明输出、不吃环境光。内容为自有眼睛纹理，无原版星空。
 *       传送门 0 厚度（仅前后两个透镜面，无侧壁）</li>
 *   <li>眼睑框 —— 16×32 眼形描边贴图（{@code sukima.png}）cutout 单层，
 *       billboard 局部 +z 偏移 0.001（billboard 恒面向相机，+z 即朝观察者）</li>
 * </ol>
 *
 * <p><b>朝向：</b>整体 billboard（相机四元数 + Y 轴 180° 翻转，沿用
 * {@link BillboardRenderer} 验证过的绕法）每帧正对玩家，再绕视线轴倾 10°
 * （眼睑微斜的眼形神韵，在视平面内呈现）。
 *
 * <p><b>几何约定（与 tools/textures/sukima.py 一致）：</b>
 * 尖端在眼的中线（世界高度 {@link #CENTER_Y}），上盖 {@link #UPPER_LID}=0.85、
 * 下弧 {@link #LOWER_LID}=1.15（格），半宽 0.5，总高恰 2 格。
 *
 * <p>眼形内景剪影由几何承载（endPortal 着色器不采样贴图、输出不透明，
 * alpha 遮罩贴图无法参与）；购入的眼睛虚空素材保留于 tools/textures/ 供未来
 * 自定义着色器方案使用。
 */
public class SukimaPortalRenderer implements BlockEntityRenderer<SukimaBlockEntity> {

    /** 眼睑整体倾角（绕 billboard 视线轴，度）。 */
    private static final float TILT_DEGREES = 10.0F;

    /** 透镜半宽（格）。 */
    private static final float HALF_W = 0.5F;

    /** 上盖/下弧相对中线高度（格）；总高恰 2 格。 */
    private static final float UPPER_LID = 0.85F;
    private static final float LOWER_LID = 1.15F;

    /** 眼中线世界高度：下弧最低点贴方块底。 */
    private static final float CENTER_Y = LOWER_LID;

    /** 眼睑描边外接矩形中心相对眼中线的偏移：(上盖 - 下弧) / 2。 */
    private static final float RECT_CENTER_Y = (UPPER_LID - LOWER_LID) / 2.0F;

    /** 描边外接矩形半高（格）：总高 2 格。 */
    private static final float RECT_HALF_H = (UPPER_LID + LOWER_LID) / 2.0F;

    /** 眼睑框外偏移（防 z-fighting）。 */
    private static final float OUTLINE_OFFSET = 0.001F;

    /** 透镜竖向条带数（条带越少轮廓越棱角，16 与 16px 描边贴图精度匹配）。 */
    private static final int STRIPS = 16;

    private static final float SQRT_LIMIT = 1.0F - 1.0E-6F;

    private final BlockEntityRenderDispatcher dispatcher;

    public SukimaPortalRenderer(BlockEntityRendererProvider.Context context) {
        this.dispatcher = context.getBlockEntityRenderDispatcher();
    }

    @Override
    public void render(SukimaBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Level level = blockEntity.getLevel();
        int light = level == null ? packedLight
                : LevelRenderer.getLightColor(level, blockEntity.getBlockPos());

        poseStack.pushPose();
        // 平移到眼中心后 billboard 正对玩家（+Y 180° 修正贴图正立），再绕视线轴倾 10°
        poseStack.translate(0.5D, CENTER_Y, 0.5D);
        poseStack.mulPose(this.dispatcher.camera.rotation());
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(TILT_DEGREES));

        this.renderVoidInterior(poseStack, bufferSource);

        poseStack.translate(0.0D, RECT_CENTER_Y, OUTLINE_OFFSET);
        SukimaPortalQuads.draw(poseStack, bufferSource,
                RenderType.entityCutoutNoCull(GensokyouTextures.SUKIMA),
                HALF_W, RECT_HALF_H, 0.0F, 0.0F, 0xFFFFFF, 0xFF, light);

        poseStack.popPose();
    }

    /**
     * 眼形虚空内景：前后两个透镜面（0 厚度，无侧壁），走 {@code voidPortal}
     * 渲染类型——原版 end portal 着色器采样自有眼睛虚空贴图（POSITION 顶点格式、
     * 无 UV——效果由着色器从屏幕投影生成，与顶点 UV 无关）。
     *
     * <p>透镜按竖条带切成纯四顶点 quad（正面外向绕序、背面反向，着色器渲染类型
     * 默认背面剔除），条带两端在眼尖处 up==lo，退化为带重复顶点的合法 quad。
     */
    private void renderVoidInterior(PoseStack poseStack, MultiBufferSource bufferSource) {
        VertexConsumer consumer = bufferSource.getBuffer(
                SukimaPortalRenderTypes.voidPortal(GensokyouTextures.SUKIMA_PORTAL));
        PoseStack.Pose pose = poseStack.last();

        for (int i = 0; i < STRIPS; i++) {
            float t0 = -1.0F + 2.0F * i / STRIPS;
            float t1 = -1.0F + 2.0F * (i + 1) / STRIPS;
            float x0 = HALF_W * t0;
            float x1 = HALF_W * t1;
            float up0 = UPPER_LID * sqrtHalf(t0);
            float up1 = UPPER_LID * sqrtHalf(t1);
            float lo0 = -LOWER_LID * sqrtHalf(t0);
            float lo1 = -LOWER_LID * sqrtHalf(t1);

            // 正面（+Z 外向 CCW）
            this.lensQuad(consumer, pose, x0, lo0, x1, lo1, x1, up1, x0, up0);
            // 背面（-Z 外向，反绕序）
            this.lensQuad(consumer, pose, x0, lo0, x0, up0, x1, up1, x1, lo1);
        }
    }

    private float sqrtHalf(float t) {
        return (float) Math.sqrt(Math.max(0.0F, SQRT_LIMIT - t * t));
    }

    private void lensQuad(VertexConsumer consumer, PoseStack.Pose pose,
                          float x0, float y0, float x1, float y1,
                          float x2, float y2, float x3, float y3) {
        consumer.addVertex(pose.pose(), x0, y0, 0.0F);
        consumer.addVertex(pose.pose(), x1, y1, 0.0F);
        consumer.addVertex(pose.pose(), x2, y2, 0.0F);
        consumer.addVertex(pose.pose(), x3, y3, 0.0F);
    }

    /**
     * 渲染包围盒覆盖上下两格：传送门为 2 格高，避免斜视角下上半天被视锥裁剪。
     */
    @Override
    public AABB getRenderBoundingBox(SukimaBlockEntity blockEntity) {
        return new AABB(blockEntity.getBlockPos()).expandTowards(0.0D, 1.0D, 0.0D);
    }
}
