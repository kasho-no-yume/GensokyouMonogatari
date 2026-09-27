package com.bitsson.gensokyou.client.renderer;

import com.bitsson.gensokyou.block.entity.SukimaBlockEntity;
import com.bitsson.gensokyou.client.GensokyouTextures;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 隙间传送门渲染器：眼形「窗」——billboard 眼睑框（上下两片可开合）+ 末地门式虚空内景。
 *
 * <p><b>视觉结构（框与内景分离）：</b>
 * <ol>
 *   <li>内景 —— 眼形透镜面（竖条带扇面几何，尖端在中线），填
 *       {@link SukimaPortalRenderTypes#voidPortal}：克隆原版末地门渲染状态、
 *       双贴图槽位换成自有眼睛虚空贴图——着色器从<b>裁剪空间投影采样</b>，
 *       效果锚定屏幕而非几何表面，再叠加分层视差漂移，因此放在 billboard 上依然呈现
 *       「锚定的层叠虚空」而非糊在框上的静态图。0 厚度（仅前后两个透镜面，无侧壁）</li>
 *   <li>眼睑框 —— 描边贴图 cutout，<b>沿眼中线切成上下两片</b>，各取一段 UV 区间并各自
 *       沿中线方向压扁/复原，形成眼睑开合（见 {@link #renderEyelids}）</li>
 * </ol>
 *
 * <p><b>朝向：</b>整体 billboard（相机四元数 + Y 轴 180° 翻转）每帧正对玩家，
 * 再绕视线轴倾 10°。
 *
 * <p><b>几何约定（1× 基准，与 tools/textures/sukima.py 一致）：</b>
 * 尖端在眼的中线（世界高度 {@link #CENTER_Y}），上盖 {@link #UPPER_LID}=0.85、
 * 下弧 {@link #LOWER_LID}=1.15（格），半宽 {@link #HALF_W}=0.5，总高恰 2 格。
 * 全部尺寸常量乘以 {@link SukimaBlockEntity#scale()}；标量 1.0 时与变更前逐项一致。
 *
 * <p><b>爆炸与粒子的零包原则：</b>启动爆发与环境粒子全部由本渲染器依门体自身的动画
 * 计时器（{@code openTicks}）本地生成，服务端除一次音效广播外不发送任何相关数据包。
 * 迟到才进入渲染范围的客户端看不到爆发——与本模紫「结界崩解」的一致性，MUST NOT 重播。
 */
public class SukimaPortalRenderer implements BlockEntityRenderer<SukimaBlockEntity> {

    /** 眼睑整体倾角（绕 billboard 视线轴，度）。 */
    private static final float TILT_DEGREES = 10.0F;

    // ---- 1× 基准几何（单位：格） ----
    /** 透镜半宽。 */
    private static final float HALF_W = 0.5F;
    /** 上盖相对中线高度。 */
    private static final float UPPER_LID = 0.85F;
    /** 下弧相对中线高度（负向）。 */
    private static final float LOWER_LID = 1.15F;
    /** 眼中线世界高度：下弧最低点贴方块底。 */
    private static final float CENTER_Y = LOWER_LID;
    /** 描边外接矩形半高（格）：总高 2 格。 */
    private static final float RECT_HALF_H = (UPPER_LID + LOWER_LID) / 2.0F;

    /**
     * 描边贴图中眼尖所在的行（贴图共 {@link #TEX_ROWS} 行）——与 {@code sukima.py} 的
     * {@code _TIP_ROW = (UPPER/(UPPER+LOWER)) * 32 = 13.6} 同源。UV 在此切分即得上下两片。
     */
    private static final float TEX_ROWS = 32.0F;
    private static final float V_TIP = ((UPPER_LID / (UPPER_LID + LOWER_LID)) * TEX_ROWS) / TEX_ROWS;

    /** 眼睑框外偏移（防 z-fighting）。 */
    private static final float OUTLINE_OFFSET = 0.001F;

    /** 透镜竖向条带数（条带越少轮廓越棱角，16 与 16px 描边贴图精度匹配）。 */
    private static final int STRIPS = 16;

    private static final float SQRT_LIMIT = 1.0F - 1.0E-6F;


    /** 爆炸球壳的最大半径（格）——比眼本身大数倍，才有「极其夸张」的体量感。 */
    /** 爆炸冲击环的圈数与起始 tick 占比。 */

    private final BlockEntityRenderDispatcher dispatcher;

    public SukimaPortalRenderer(BlockEntityRendererProvider.Context context) {
        this.dispatcher = context.getBlockEntityRenderDispatcher();
    }

    @Override
    public void render(SukimaBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        // 注意：BER 的 render() 本身只在客户端调用，getLevel() 恒为 ClientLevel。
        // 不可加 level.isClientSide 判断——那会让整个方法成为永不执行的死代码。
        Level level = blockEntity.getLevel();
        if (level == null) {
            return;
        }
        int light = LevelRenderer.getLightColor(level, blockEntity.getBlockPos());

        float scale = blockEntity.scale();
        // 内景虚空与眼睑外框**必须共用同一个开合系数**：
        // 两者在几何上是同一个边界（虚空的左右极值 == 眼睑两片的左右极值），
        // 若给两条曲线，虚空会溢出眼纲外或小于眼纲——看起来像「开闭轴歪了」，
        // 实际是内景与眼纲脱钩。
        float extent = lensExtent(openProgress01(blockEntity));
        int openTicks = blockEntity.openTicks();
        int openDuration = Math.max(1, GensokyouConfig.SUKIMA_PORTAL_OPEN_TICKS.get());

        // 爆发与环境粒子在世界空间发射（不随 billboard 旋转），故先做。

        poseStack.pushPose();
        // 平移到眼中心后 billboard 正对玩家（+Y 180° 修正贴图正立），再绕视线轴倾 10°
        poseStack.translate(0.5D, CENTER_Y * scale, 0.5D);
        poseStack.mulPose(this.dispatcher.camera.rotation());
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(TILT_DEGREES));

        this.renderVoidInterior(poseStack, bufferSource, scale, extent);
        this.renderEyelids(poseStack, bufferSource, scale, extent, light);
        // 光球与光束在同一个 billboard 姿态内绘制，因此面向相机自然成立。

        poseStack.popPose();

        if (openTicks >= openDuration) {
            blockEntity.markBurstDone();
        }
    }

    /**
     * 开合原始进度 0..1（<b>未</b>过冲）。两条派生曲线都从这里出发。
     *
     * <p><b>关门进度必须取自 {@code closingTicks} 而非 {@code openTicks}</b>：关门期间
     * {@code openTicks} 已饱和在满值、并不递减，若据此插值会算出进度恒为 1，
     * 眼睛便一帧瞬闭、完全没有过渡动画。{@code closingTicks} 是同步下发的倒计时，
     * 由满向零递减，正是闭眼进度。
     */
    private static float openProgress01(SukimaBlockEntity portal) {
        int duration = Math.max(1, GensokyouConfig.SUKIMA_PORTAL_OPEN_TICKS.get());
        if (portal.isClosing()) {
            return 1.0F - Math.min(1.0F, (float) portal.closingTicks() / duration);
        }
        return portal.openProgress(duration);
    }

    /**
     * 眼形纵向半开度系数——<b>开合轴</b>的唯一来源。
     *
     * <p>眼形为 1 格宽 × 2 格高的<b>竖立杏仁</b>，尖端在左右两侧的眼中线高度，因此
     * <b>长轴是竖直的那条</b>。开合即以长轴为对称轴向<b>左右两侧</b>张开：唯一的开合量是
     * 横向半宽，纵向跨度（上盖 0.85 / 下弧 1.15）恒定不变。闭眼态（{@code extent=0}）是一条
     * 贯穿 2 格全高、宽度趋零的竖直细缝。
     *
     * <p>曲线为无过冲的 {@link #easeOutCubic}：旧的 {@code easeOutBack} 过冲回弹观感很差，
     * 已连同 {@code sukimaPortalLidTravel} 配置键一并退役。
     */
    public static float lensExtent(float t) {
        return easeOutCubic(t);
    }

    /**
     * 内景透镜的横向半宽（格，已乘 scale）——随开合进度缩放。
     */
    public static float voidHalfW(float extent, float scale) {
        return HALF_W * scale * extent;
    }

    /**
     * 单片眼睑 quad 的横向半宽（格，已乘 scale）。
     *
     * <p>故意委托给 {@link #voidHalfW} 并取半：左右两片各自覆盖半个杏仁，合起来恰为内景的
     * 全宽。这个 2:1 关系是<b>结构上</b>保证的，不依赖两处常量同时正确——否则内景会溢出眼纲
     * 或小于眼纲，表现为开闭进度错位（实机读作「开闭轴歪了」）。
     */
    public static float lidPieceHalfW(float extent, float scale) {
        return 0.5F * voidHalfW(extent, scale);
    }

    /**
     * 眼形的纵向半跨度（格，已乘 scale）——<b>不随开合进度变化</b>。
     *
     * <p>内景与眼睑两侧共用同一对常量（{@link #UPPER_LID} / {@link #LOWER_LID}），故纵向天然相等，
     * 无需再套一层派生。
     */
    public static float voidHalfH(float scale) {
        return RECT_HALF_H * scale;
    }

    /** 无过冲的 easeOutCubic：起步快、末段稳，终值精确为 1。 */
    public static float easeOutCubic(float t) {
        if (t >= 1.0F) {
            return 1.0F;
        }
        if (t <= 0.0F) {
            return 0.0F;
        }
        float u = 1.0F - t;
        return 1.0F - u * u * u;
    }

    // ------------------------------------------------------------------ 内景

    /**
     * 眼形虚空内景：前后两个透镜面（0 厚度，无侧壁），走 {@code voidPortal}
     * 渲染类型——原版 end portal 着色器采样自有眼睛虚空贴图（POSITION 顶点格式、
     * 无 UV——效果由着色器从屏幕投影生成，与顶点 UV 无关）。
     *
     * <p>透镜按竖条带切成纯四顶点 quad（正面外向绕序、背面反向，着色器渲染类型
     * 默认背面剔除），条带两端在眼尖处 up==lo，退化为带重复顶点的合法 quad。
     *
     * <p><b>开合轴 = 竖直长轴</b>：只有<b>横向半宽</b>随开合进度缩放，纵向跨度恒定。
     * {@code s→0} 时 {@code halfW→0}，形状退化为一条贯穿全高的<b>竖直细缝</b>（旧的实现是
     * 缩放纵向、横向恒满宽，闭眼态读作一条 1 格宽的水平细缝——与这只竖立杏仁的长轴矛盾）。
     */
    private void renderVoidInterior(PoseStack poseStack, MultiBufferSource bufferSource,
                                    float scale, float extent) {
        float s = Math.min(1.0F, Math.max(0.0F, extent));
        float halfW = voidHalfW(s, scale);
        float up = UPPER_LID * scale;
        float lo = LOWER_LID * scale;
        if (halfW <= 1.0E-4F) {
            return;
        }
        VertexConsumer consumer = bufferSource.getBuffer(
                SukimaPortalRenderTypes.voidPortal(GensokyouTextures.SUKIMA_PORTAL));
        PoseStack.Pose pose = poseStack.last();

        for (int i = 0; i < STRIPS; i++) {
            float t0 = -1.0F + 2.0F * i / STRIPS;
            float t1 = -1.0F + 2.0F * (i + 1) / STRIPS;
            float x0 = halfW * t0;
            float x1 = halfW * t1;
            float up0 = up * sqrtHalf(t0);
            float up1 = up * sqrtHalf(t1);
            float lo0 = -lo * sqrtHalf(t0);
            float lo1 = -lo * sqrtHalf(t1);

            // 正面（+Z 外向 CCW）
            this.lensQuad(consumer, pose, x0, lo0, x1, lo1, x1, up1, x0, up0);
            // 背面（-Z 外向，反绕序）
            this.lensQuad(consumer, pose, x0, lo0, x0, up0, x1, up1, x1, lo1);
        }
    }

    private static float sqrtHalf(float t) {
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

    // ------------------------------------------------------------------ 眼睑

    /**
     * 眼睑框：描边贴图沿<b>水平中线（U = 0.5）</b>切成左、右两片，各取半个杏仁
     * （外侧尖端在 {@code x = ±W}，内侧的直切口边在 {@code x = 0}），两片沿水平方向
     * 向中线收拢 / 复原。纵向两片都占全高，恒定不随开合进度变化。
     *
     * <p><b>为什么是「压扁」而不是「平移」</b>：两片的自然位置本就以中线相接（合起来是完整的
     * 杏仁），所以开合量只需要「把左片压向中线、把右片压向中线 / 反向复原」这一个动作；
     * 若改成向外平移，全开时眼形会宽于贴图本身，读作"眼框被撑大"而不是"眼睛张开"。
     * {@code s=0} 时两片精确塌成中线上的一条竖缝，正是闭眼。
     *
     * <p><b>纵向锚点</b>：贴图里的眼中线在 {@link #V_TIP}（不是 0.5），而几何的中线在
     * {@code y = 0}。因此每片按「半高 = 全高/2、y 平移 = (上盖−下弧)/2」放置，
     * 使 {@code v = V_TIP} 恰好落在 {@code y = 0}。若按对称居中放置，贴图里的眼中线会被
     * 映射到几何的 {@code v = 0.5}，与内景的零高度点错开约 0.05 格。
     *
     * <p>UV 区间与被压扁的 quad 宽度成正比，故压扁是<b>等比</b>的（不产生形变错觉），
     * 且不需任何新贴图——切分由 UV 子区间完成。
     */
    private void renderEyelids(PoseStack poseStack, MultiBufferSource bufferSource,
                               float scale, float s, int light) {
        if (s <= 1.0E-4F) {
            return; // 完全闭合
        }
        // 切勿改用 entitySmoothCutout——其片元着色器同样是硬 discard(alpha<0.1)，
        // 名字里的 "smooth" 指 mipmap 距离淡出，与边缘抗锯齿无关。眼形边缘的阶梯感来自
        // 低分辨率贴图的硬 alpha 轮廓被放大，只能从贴图侧解决（见 tools/textures/sukima.py）。
        RenderType cutout = RenderType.entityCutoutNoCull(GensokyouTextures.SUKIMA);
        float pieceW = lidPieceHalfW(s, scale);
        float fullW = voidHalfW(s, scale);
        float halfH = voidHalfH(scale);
        // 眼中线锚定：由 V_TIP 反解平移量，使贴图里的眼中线恰好落在几何的 y = 0。
        // （等价于 (上盖−下弧)/2，此处写成 V_TIP 形式是为了让该比例常量保持 load-bearing，
        //   且"眼中线在贴图里不是正中"这件事在代码里可见。）
        float anchorY = (V_TIP - 0.5F) * 2.0F * halfH;
        int alpha = (int) (255 * Math.min(1.0F, s * 1.6F));
        if (alpha <= 0) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0.0D, 0.0D, OUTLINE_OFFSET);
        poseStack.translate(0.0D, anchorY, 0.0D);
        // 左片：占 [-fullW, 0]，取贴图 u ∈ [0, 0.5]（外侧尖端在 x = -fullW）
        poseStack.pushPose();
        poseStack.translate(-fullW * 0.5D, 0.0D, 0.0D);
        SukimaPortalQuads.drawUvRange(poseStack, bufferSource, cutout,
                pieceW, halfH, 0.0F, 0.0F, 0.5F, 1.0F, 0xFFFFFF, alpha, light);
        poseStack.popPose();
        // 右片：占 [0, +fullW]，取贴图 u ∈ [0.5, 1]
        poseStack.pushPose();
        poseStack.translate(fullW * 0.5D, 0.0D, 0.0D);
        SukimaPortalQuads.drawUvRange(poseStack, bufferSource, cutout,
                pieceW, halfH, 0.5F, 0.0F, 1.0F, 1.0F, 0xFFFFFF, alpha, light);
        poseStack.popPose();
        poseStack.popPose();
    }

    // ------------------------------------------------------------------ 「结界崩解」编排（客户端）

    /** 演出总长度，与 {@code SukimaBlockEntity.SHATTER_FX_TICKS} 保持一致。 */
    private static Vec3 eyeCenter(SukimaBlockEntity portal, float scale) {
        return new Vec3(portal.getBlockPos().getX() + 0.5D,
                portal.getBlockPos().getY() + CENTER_Y * scale,
                portal.getBlockPos().getZ() + 0.5D);
    }

    // ------------------------------------------------------------------ 包围盒

    /**
     * 渲染包围盒覆盖放大后的眼体：竖向 2×scale 格，横向各外扩半宽（billboard 任意朝向）。
     */
    @Override
    public AABB getRenderBoundingBox(SukimaBlockEntity blockEntity) {
        float scale = blockEntity.scale();
        return new AABB(blockEntity.getBlockPos())
                .inflate(HALF_W * scale, 0.0D, HALF_W * scale)
                .expandTowards(0.0D, (UPPER_LID + LOWER_LID) * scale, 0.0D);
    }
}
