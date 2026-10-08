package com.bitsson.gensokyou.client.renderer;

import com.bitsson.gensokyou.block.entity.SukimaBlockEntity;
import com.bitsson.gensokyou.client.GensokyouTextures;
import com.bitsson.gensokyou.client.ShatterScreenFx;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * 隙间传送门渲染器：眼形「窗」——billboard 眼睑框（上下两片可开合）+ 末地门式虚空内景。
 *
 * <p><b>视觉结构（框与内景分离）：</b>
 * <ol>
 *   <li>内景 —— 眼形透镜面（竖条带扇面几何，尖端在左右两侧的眼中线高度），填
 *       {@link SukimaPortalRenderTypes#voidPortal}：克隆原版末地门渲染状态、
 *       双贴图槽位换成自有眼睛虚空贴图——着色器从<b>裁剪空间投影采样</b>，
 *       效果锚定屏幕而非几何表面，再叠加分层视差漂移，因此放在 billboard 上依然呈现
 *       「锚定的层叠虚空」而非糊在框上的静态图。0 厚度（仅前后两个透镜面，无侧壁）</li>
 *   <li>眼睑框 —— 描边贴图 cutout，<b>沿水平中线切成左右两片</b>，各取半个杏仁的 UV 区间
 *       并沿水平方向压扁/复原，形成开合（见 {@link #renderEyelids}）</li>
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
 * <p><b>姿态分段（本文件最要紧的一条结构约束）：</b>
 * <ol>
 *   <li><b>世界姿态</b> —— 「结界崩解」演出（蓝白光球 + 径向光柱 + 水平烟环）与常驻粒子。
 *       烟环 MUST 画在世界姿态：billboard 姿态的局部水平面正对相机，在它里面画"水平环"
 *       会得到一个跟着视线转的椭圆。</li>
 *   <li><b>billboard 姿态</b> —— 眼形内景与眼睑框。</li>
 * </ol>
 *
 * <p><b>零包原则：</b>整段演出与常驻粒子全部由本渲染器依门体自身的
 * {@link SukimaBlockEntity#fxStartGameTime() 绝对锚点}本地生成，服务端只负责音效与击退。
 * 迟到才进入渲染范围的客户端落在正确相位上而不是从头播，且<b>永不重播</b>——
 * 锚点存在持久化 NBT 里，活得过区块卸载与重连。
 */
public class SukimaPortalRenderer implements BlockEntityRenderer<SukimaBlockEntity> {

    /** 眼睑整体倾角（绕 billboard 视线轴，度）。 */
    private static final float TILT_DEGREES = 10.0F;

    // ---- 演出配色与资源 ----

    /** 球壳亮芯 / 光柱亮芯：近白。 */
    private static final int CORE_R = 226, CORE_G = 240, CORE_B = 255;
    /** 径向光柱外晕。 */
    private static final int BEAM_R = 170, BEAM_G = 214, BEAM_B = 255;
    /** 烟环：冷灰偏蓝，读作"被撕开的空气"而非暖烟。 */
    private static final int SMOKE_R = 188, SMOKE_G = 202, SMOKE_B = 226;

    private static final ResourceLocation BOLT_GLOW_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(com.bitsson.gensokyou.Gensokyou.MODID, "textures/fx/bolt_glow.png");
    private static final ResourceLocation BOLT_CORE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(com.bitsson.gensokyou.Gensokyou.MODID, "textures/fx/bolt_core.png");
    /**
     * 蓄能球用的<b>径向</b>光斑（中心实、边缘平滑归零）。
     *
     * <p>不能用 {@code fx/bolt_*}：那些是<b>光束</b>贴图，梯度沿条带方向（U 跨光束宽度），
     * 拿来贴一个球只会得到一块方斑。而 {@code textures/fx/spirit_mist.png} 同样是为
     * 平铺噪声准备的，接缝可见。
     */
    private static final ResourceLocation RADIAL_BLOB_TEXTURE = GensokyouTextures.SHATTER_GLOW;

    /**
     * 烟环用的原版软边烟帧 {@code particle/generic_0..7}——正是 {@code large_smoke} 粒子的
     * 8 帧贴图。零新资源、零新粒子类型，而且**是真的烟**：软边、不规则、带内部明暗，
     * 用加法混合只会把它读成"白色光球"（实测），必须配常规 alpha 混合才读得出烟。
     */
    private static final ResourceLocation[] SMOKE_FRAMES = {
            ResourceLocation.withDefaultNamespace("textures/particle/generic_0.png"),
            ResourceLocation.withDefaultNamespace("textures/particle/generic_1.png"),
            ResourceLocation.withDefaultNamespace("textures/particle/generic_2.png"),
            ResourceLocation.withDefaultNamespace("textures/particle/generic_3.png"),
            ResourceLocation.withDefaultNamespace("textures/particle/generic_4.png"),
            ResourceLocation.withDefaultNamespace("textures/particle/generic_5.png"),
            ResourceLocation.withDefaultNamespace("textures/particle/generic_6.png"),
            ResourceLocation.withDefaultNamespace("textures/particle/generic_7.png")};

    /** 黄金角（度）——在球面/圆周上取均匀点。 */
    private static final double GOLDEN_ANGLE = 137.50776405003785D;

    /** 常驻粒子的最大发射距离（格）；超出即完全不产生开销。 */
    /**
     * 常驻粒子的<b>玩家距离门控</b>（格）。
     *
     * <p>MUST 明显小于渲染包围盒（30 格见方）：包围盒只管"渲不渲染"，
     * 门控管"发不发粒子"。若门控放得太宽，几十格外<b>别的门</b>也在往你这边撒绿星，
     * 看起来就像自己这扇门在别处冒绿星。
     */
    private static final double MOTE_MAX_DISTANCE = 24.0D;

    /** 公转角速度（转/秒）。约 6 秒一圈——再快就读作一圈均匀亮环，看不出在转。 */
    private static final double MOTE_ORBIT_RATE = 0.17D;

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

    private final BlockEntityRenderDispatcher dispatcher;

    /**
     * 本帧的摄像机旋转，缓存下来给 {@link FxGeometry#emitBillboard} 摆正面片。
     *
     * <p>每帧在 {@link #render()} 开头重取。渲染器全在渲染线程调用，故不需要同步；
     * 缓存只是为了让深层方法不必一路传参。
     */
    private Quaternionf camRot = new Quaternionf();

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
        // billboard 面片要正对摄像机，故先把本帧的摄像机旋转取出来（摆正面片用）
        this.camRot.set(this.dispatcher.camera.rotation());

        float scale = blockEntity.scale();
        // 内景虚空与眼睑外框**必须共用同一个开合系数**：
        // 两者在几何上是同一个边界（虚空的左右极值 == 眼睑两片的左右极值），
        // 若给两条曲线，虚空会溢出眼纲外或小于眼纲——看起来像「开闭轴歪了」，
        // 实际是内景与眼纲脱钩。
        float extent = lensExtent(openProgress01(blockEntity));
        // 浮点时钟：可见性/强度调制 MUST 由它驱动，绝不能用按整数 tick 取模的开关。
        // 60fps 渲染 20tick/s 时钟时，"t % 8" 会变成画一帧空十一帧，光柱 11/12 的时间
        // 根本不存在（见 docs/barrier-shatter-fx-postmortem.md §10.3）。
        double now = level.getGameTime() + partialTick;
        // 粒子用绝对世界坐标（level.addParticle 不吃姿态），故这里备一份世界坐标的眼中心
        Vec3 center = eyeCenter(blockEntity, scale);

        // ⚠️ 传入的姿态**已经**被 LevelRenderer 平移到方块位置了
        //     （`posestack.translate(blockPos - sectionOrigin)`，见 LevelRenderer 的
        //      renderBlockEntity 段），所以这里的偏移 MUST 是**方块局部**的。
        //     写世界坐标会叠加两次，把整扇门画到两倍远处 —— 屏幕上什么都看不见，
        //     而粒子（走 level.addParticle 的绝对坐标）却仍在正确位置。
        //     换句话说：「粒子在、眼不在」就是踩中了这个 bug 的指纹。
        poseStack.pushPose();
        poseStack.translate(0.5D, CENTER_Y * scale, 0.5D);

        // ---- ① 世界朝向的演出：光球 / 径向光柱 / 水平烟环 / 地面冲击波 ----
        // 此刻姿态**只有平移、没有旋转**，所以在它里面画"水平"的东西就是世界水平的。
        // 烟环 MUST 留在这里：billboard 姿态的局部水平面正对相机，在那里面画环会得到
        // 一个跟着视线转的椭圆。
        if (blockEntity.fxStartGameTime() >= 0) {
            int elapsed = blockEntity.fxElapsed();
            int chargeEnd = blockEntity.fxChargeEnd();
            // 上报给屏幕级导演（渐晕 / FOV / 晃动）。它自己按"到最近一扇门的距离"衰减，
            // 与是谁触发的无关，故联机时队友放的也会影响你。
            ShatterScreenFx.report(blockEntity, center, elapsed, chargeEnd, now);
            this.renderShatterFx(blockEntity, elapsed, chargeEnd, now, scale, center, level,
                    poseStack, bufferSource);
        }

        // ---- ② billboard 姿态：眼形内景 + 眼睑框 ----
        // 旋转叠加在上面这一步；此前的一切都在世界坐标系里。
        poseStack.mulPose(this.dispatcher.camera.rotation());
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(TILT_DEGREES));

        this.renderVoidInterior(poseStack, bufferSource, scale, extent);
        this.renderEyelids(poseStack, bufferSource, scale, extent, light);
        poseStack.popPose();

        // 常驻粒子用绝对世界坐标，与姿态无关，放在这里最清楚。
        this.emitAmbientMotes(blockEntity, level, center, extent, now);
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

    /** 眼中心的<b>世界</b>坐标（演出与粒子都在世界姿态下以它为原点）。 */
    private static Vec3 eyeCenter(SukimaBlockEntity portal, float scale) {
        return new Vec3(portal.getBlockPos().getX() + 0.5D,
                portal.getBlockPos().getY() + CENTER_Y * scale,
                portal.getBlockPos().getZ() + 0.5D);
    }

    /**
     * 「结界崩解」演出：8 秒蓄能（蓝白光球 0→3 格 + 四周径向光柱）→ 爆炸（水平烟环 0→15 格）。
     *
     * <p><b>无状态</b>：所有量都由 {@code elapsed = now − fxStartGameTime} 现算，没有
     * 「上一帧到哪了」这类累积状态。帧率无关因此是<b>结构性保证</b>，而不是靠小心编码——
     * 这正是本文件曾经栽过的地方。
     *
     * <p>已在 {@link #render} 里置于世界姿态（原点即眼中心），故此处直接用局部坐标。
     */
    private void renderShatterFx(SukimaBlockEntity portal, int elapsed, int chargeEnd, double now,
                                 float scale, Vec3 center, Level level,
                                 PoseStack poseStack, MultiBufferSource buffers) {
        if (elapsed < chargeEnd) {
            this.renderCharge(elapsed, chargeEnd, now, scale, center, level, portal, poseStack, buffers);
        } else {
            int since = elapsed - chargeEnd;
            // 地面冲击波：比烟环快得多、薄得多，只活 20 tick。烟环是"余韵"，它是"那一下"
            this.renderGroundShockwave(since, scale, poseStack, buffers);
            int burst = portal.fxBurstTicks();
            if (since < burst) {
                this.renderSmokeRing(since, burst, scale, poseStack, buffers);
            }
        }
    }

    // ------------------------------------------------------------------ 蓄能：球面扫描环

    /**
     * 蓄能段的<b>球面扫描环</b>：一条亮细环贴着光球表面，环面法线缓慢进动。
     *
     * <p>作用是给"一个正在长大的光球"加一个<b>可读的尺度参照</b>——
     * 纯径向渐变的球在膨胀时很难判断"大了多少"，而一条绕球扫过的亮环会让尺寸变化
     * 一眼可见。3 条环、反向进动，读作约束场在扫描而不是装饰。
     *
     * <p>环用 camera-facing 小面片排成：环本身在 3D 空间是斜的，用 billboard 才能保证
     * 每一片都是正圆（用 {@code emitCrossGlow} 会被透视压成椭圆，见 §12）。
     */
    private void renderScanRings(float progress, double now, float radius,
                                 PoseStack poseStack, MultiBufferSource buffers) {
        int rings = GensokyouConfig.FX_SHATTER_SCAN_RINGS.get();
        if (rings <= 0 || radius <= 1.0E-3F) {
            return;
        }
        int puffs = GensokyouConfig.FX_SHATTER_SCAN_PUFFS.get();
        VertexConsumer glow = buffers.getBuffer(DanmakuRenderTypes.additiveGlow(RADIAL_BLOB_TEXTURE));
        PoseStack.Pose pose = poseStack.last();
        for (int ring = 0; ring < rings; ring++) {
            // 环面法线：极角均分 + 随时间进动，相邻环反向 ⇒ 读作对扫
            double polar = Math.PI * (ring + 0.5D) / rings;
            double spin = now * (ring % 2 == 0 ? 0.55D : -0.42D);
            double nx = Math.sin(polar) * Math.cos(spin);
            double ny = Math.cos(polar);
            double nz = Math.sin(polar) * Math.sin(spin);
            // 环内的一个正交基（u, v），两者都与法线垂直
            double ux = -nz;
            double uy = 0.0D;
            double uz = nx;
            double ul = Math.sqrt(ux * ux + uz * uz);
            if (ul < 1.0E-4D) {
                ux = 1.0D;
                uz = 0.0D;
                ul = 1.0D;
            }
            ux /= ul;
            uz /= ul;
            double vx = ny * uz - nz * uy;
            double vy = nz * ux - nx * uz;
            double vz = nx * uy - ny * ux;
            // 环只在蓄能中后段出现，且随蓄能推进变亮
            float gate = Mth.clamp((progress - 0.12F) / 0.5F, 0.0F, 1.0F);
            int alpha = (int) (gate * 165.0F);
            if (alpha <= 2) {
                continue;
            }
            float r = radius * 1.02F;
            float half = Math.max(0.06F, radius * 0.09F);
            for (int i = 0; i < puffs; i++) {
                double a = i * 2.0D * Math.PI / puffs;
                double ca = Math.cos(a);
                double sa = Math.sin(a);
                FxGeometry.emitBillboard(glow, pose, this.camRot,
                        (float) ((ux * ca + vx * sa) * r),
                        (float) ((uy * ca + vy * sa) * r),
                        (float) ((uz * ca + vz * sa) * r),
                        half, half, BEAM_R, BEAM_G, BEAM_B, alpha);
            }
        }
    }

    // ------------------------------------------------------------------ 爆炸：地面冲击波

    /**
     * 贴地冲击波环：爆炸当刻从球心下方炸开，20 tick 内扩到 {@code fxShatterShockwaveRadius}。
     *
     * <p>与烟环刻意区分：烟环慢（40 tick）、厚、alpha 混合、留有余韵；
     * 冲击波快（20 tick）、薄、加法混合、一闪即逝。两者叠在一起才有"炸开 → 然后散成烟"
     * 的层次，只有烟环会读作"缓缓扩大的雾圈"。
     *
     * <p>环贴在水平面上，故用 camera-facing 面片而非平面圆环：站在环上平视时，
     * 平面圆环被透视压成一条看不见的线，而 billboard 保证每一片都是正圆。
     */
    private void renderGroundShockwave(int since, float scale,
                                       PoseStack poseStack, MultiBufferSource buffers) {
        int len = GensokyouConfig.FX_SHATTER_SHOCKWAVE_TICKS.get();
        if (len <= 0 || since < 0 || since >= len) {
            return;
        }
        float t = (float) since / len;
        float radius = GensokyouConfig.FX_SHATTER_SHOCKWAVE_RADIUS.get().floatValue() * easeOutCubic(t);
        if (radius <= 1.0E-3F) {
            return;
        }
        int puffs = GensokyouConfig.FX_SHATTER_SHOCKWAVE_PUFFS.get();
        // 一闪即逝：比烟环的包络陡得多
        float alpha = (1.0F - t) * (1.0F - t) * 210.0F;
        if (alpha < 3.0F) {
            return;
        }
        float half = Math.max(0.10F, radius * 0.055F);
        // 贴地：眼中心在 1.15*scale 高，环落到 blockPos 的 y 上方一点点
        float groundY = -CENTER_Y * scale + 0.08F * scale;
        VertexConsumer glow = buffers.getBuffer(DanmakuRenderTypes.additiveGlow(RADIAL_BLOB_TEXTURE));
        PoseStack.Pose pose = poseStack.last();
        for (int i = 0; i < puffs; i++) {
            double a = i * 2.0D * Math.PI / puffs;
            FxGeometry.emitBillboard(glow, pose, this.camRot,
                    (float) (Math.cos(a) * radius), groundY, (float) (Math.sin(a) * radius),
                    half, half, CORE_R, CORE_G, CORE_B, (int) alpha);
        }
    }

    // ------------------------------------------------------------------ 蓄能：光球 + 径向光柱

    /**
     * 蓄能段。球<b>单调</b>膨胀到 {@code fxShatterBallRadius}（初版用过"先胀后缩"的曲线，
     * 实机读作"没有膨胀感"）；光柱自球心向四周射出。
     *
     * <p><b>球 MUST 是"同心分层 billboard"，绝不能是"一堆小球"。</b>
     * 上一版用 40 个斐波那球点各带 3 张轴对齐方片（{@link FxGeometry#emitCrossGlow}），
     * 屏幕上就是 <b>120 个各自带完整径向渐变的亮心</b>——每一片自己就已经是一个球，
     * 加法叠加只会让 120 个球心一起更亮，<b>永远不会合并成一个球</b>。实机读作"一群光球"。
     *
     * <p>能合并的结构是<b>同心分层</b>：N 层面片，半径递增、alpha 递减。
     * 单层的径向渐变由贴图给出，层与层的贡献沿半径错开，累加出一条
     * <b>平滑单调的径向衰减</b>——屏幕上只有一个球。层数越多衰减越平滑，
     * 这就是 {@code fxShatterBallLayers} 的含义。
     *
     * <p>光柱复用万象共鸣塔的折线生成器（{@link FxGeometry#buildBoltPoints}）与同一套
     * 芯/晕贴图，只是染成蓝白。
     */
    private void renderCharge(int elapsed, int chargeEnd, double now, float scale, Vec3 center,
                              Level level, SukimaBlockEntity portal,
                              PoseStack poseStack, MultiBufferSource buffers) {
        float progress = Mth.clamp((float) elapsed / chargeEnd, 0F, 1F);
        float eased = easeOutCubic(progress);
        float finalRadius = GensokyouConfig.FX_SHATTER_BALL_RADIUS.get().floatValue();
        float radius = finalRadius * eased;
        if (radius <= 1.0E-3F) {
            return;
        }
        // 呼吸<b>只调制 alpha、绝不乘进半径</b>：用户明确要"半径必须单调"，
        // 而把 breath 乘进 scale 会让最外层轮廓 ±10% 摆动，那就不单调了。
        // （顺带一条纪律：别让注释和代码分家——上一版这里就写着"不动几何"却动了。）
        float breath = 1.0F + 0.16F * Mth.sin((float) (now * 2.1D));
        int layers = Math.max(2, GensokyouConfig.FX_SHATTER_BALL_LAYERS.get());
        VertexConsumer glow = buffers.getBuffer(DanmakuRenderTypes.additiveGlow(RADIAL_BLOB_TEXTURE));
        PoseStack.Pose pose = poseStack.last();
        for (int i = 0; i < layers; i++) {
            // i=0 最内（亮芯）→ i=layers-1 最外（柔边）；半径用 f² 偏外层，中心更聚拢
            float f = (float) i / (layers - 1);
            float layerScale = 0.26F + 0.80F * f * f;
            float a = (1.0F - f * 0.90F) * (0.30F + 0.70F * progress) * breath;
            FxGeometry.emitBillboard(glow, pose, this.camRot, 0.0F, 0.0F, 0.0F,
                    radius * layerScale, radius * layerScale,
                    CORE_R, CORE_G, CORE_B, Mth.clamp((int) (a * 210.0F), 0, 255));
        }
        this.renderScanRings(progress, now, radius, poseStack, buffers);
        this.renderChargeBeams(elapsed, now, radius, poseStack, buffers);
        // 吸入流用世界坐标（level.addParticle 不吃姿态），与几何帧互不干扰
        this.emitInflowMotes(portal, level, center, radius, now);
    }

    /**
     * 蓄能段的<b>向内吸入粒子流</b>：粒子在球外圈生成、以螺旋向内运动、被膨胀中的球吃掉。
     *
     * <p>刻意用原版粒子而不是自建粒子类型：原版 {@code END_ROD} 是白色高亮短棒，
     * 速度可控，而"被球吃掉"靠<b>生成半径跟着球一起长</b>实现——粒子总在球面外一点，
     * 球一涨就把它们吞掉，不需要任何碰撞或寿命控制（GLOW 的寿命是内部随机的，
     * 想让它"恰好在球心消失"根本做不到）。
     *
     * <p>速度给的是"向球心 + 一点切向"，两者叠加读作螺旋而不是直线对穿。
     */
    private void emitInflowMotes(SukimaBlockEntity portal, Level level, Vec3 center,
                                 float radius, double now) {
        int perSec = GensokyouConfig.FX_SHATTER_INFLOW_PER_SEC.get();
        if (perSec <= 0 || radius <= 0.15F) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        double dx = player.getX() - center.x;
        double dy = player.getY() - center.y;
        double dz = player.getZ() - center.z;
        if (dx * dx + dy * dy + dz * dz > MOTE_MAX_DISTANCE * MOTE_MAX_DISTANCE) {
            return;
        }
        int budget = portal.takeInflowBudget(perSec, now);
        // 生成半径：球外圈一点，螺旋参数逐颗错开
        float spawn = radius * 1.55F;
        for (int i = 0; i < budget; i++) {
            double seed = now * 0.31D + i * 0.618D;
            double y = 1.0D - 2.0D * ((i + 0.5D) / budget);
            double ring = Math.sqrt(Math.max(0.0D, 1.0D - y * y));
            double theta = seed * 2.0D * Math.PI;
            double px = Math.cos(theta) * ring * spawn;
            double py = y * spawn;
            double pz = Math.sin(theta) * ring * spawn;
            double len = Math.max(1.0E-3D, Math.sqrt(px * px + py * py + pz * pz));
            // 向内 = -p̂；切向绕 Y 轴，取 40% 强度构成螺旋
            double k = 0.085D;
            level.addParticle(ParticleTypes.END_ROD,
                    center.x + px, center.y + py, center.z + pz,
                    -px / len * k - pz / len * k * 0.4D,
                    -py / len * k,
                    -pz / len * k + px / len * k * 0.4D);
        }
    }

    /**
     * 蓄能段的径向光柱。
     *
     * <p><b>形状</b>每 tick 换一个确定性 seed 重掷（roll 是形状切换，<b>不是</b>可见性门控，
     * 故允许）；<b>强度</b>由浮点时钟的两路异频正弦叠加驱动——绝不用 {@code elapsed % n}：
     * 60fps 渲染 20tick/s 时钟下那会变成画一帧空十一帧（见
     * {@code docs/barrier-shatter-fx-postmortem.md} §10.3）。
     *
     * <p><b>两趟提交</b>（外晕一遍、亮芯一遍）：{@code getBuffer} 换类型会<b>立即结算上一批</b>，
     * 故 MUST NOT 先把两个 consumer 都取出来再交叉写——那会让第一个 builder 已被 build 掉，
     * 写入即抛 "Not building!"。两趟的折线由同一 seed 决定，逐位一致。
     */
    private void renderChargeBeams(int elapsed, double now, float radius,
                                   PoseStack poseStack, MultiBufferSource buffers) {
        int count = GensokyouConfig.FX_SHATTER_BEAM_COUNT.get();
        if (count <= 0 || radius <= 1.0E-3F) {
            return;
        }
        float reach = GensokyouConfig.FX_SHATTER_BEAM_REACH.get().floatValue();
        float jitter = GensokyouConfig.FX_SHATTER_BEAM_JITTER.get().floatValue();
        float scroll = (float) (now * 0.9D);
        long rollSeed = (long) elapsed * 0x9E3779B97F4A7C15L;
        int segments = Math.max(4, Math.min(24, (int) (radius * 2.0F)));
        float length = radius * (1.0F + reach);

        VertexConsumer glow = buffers.getBuffer(DanmakuRenderTypes.additiveGlow(BOLT_GLOW_TEXTURE));
        for (int i = 0; i < count; i++) {
            float[] pts = this.beamPolyline(i, count, elapsed, length, segments, jitter, rollSeed);
            float flicker = beamFlicker(now, i);
            emitBeamSegments(poseStack, glow, pts, radius * 0.16F * flicker,
                    scroll + i * 0.7F, flicker, 95, BEAM_R, BEAM_G, BEAM_B);
        }
        VertexConsumer core = buffers.getBuffer(DanmakuRenderTypes.additiveGlow(BOLT_CORE_TEXTURE));
        for (int i = 0; i < count; i++) {
            float[] pts = this.beamPolyline(i, count, elapsed, length, segments, jitter, rollSeed);
            float flicker = beamFlicker(now, i);
            emitBeamSegments(poseStack, core, pts, radius * 0.06F * flicker,
                    scroll + i * 0.7F, flicker, 215, CORE_R, CORE_G, CORE_B);
        }
    }

    /** 第 i 根光柱的抖动折线（确定性：同 {@code (i, elapsed)} 恒给出同一条）。 */
    private float[] beamPolyline(int i, int count, int elapsed, float length, int segments,
                                 float jitter, long rollSeed) {
        // 斐波那球方向：均匀铺满四周，而不是只朝几个水平方向
        double y = 1.0D - 2.0D * (i + 0.5D) / count;
        double r = Math.sqrt(Math.max(0.0D, 1.0D - y * y));
        double theta = i * GOLDEN_ANGLE + elapsed * 0.11D;
        return FxGeometry.buildBoltPoints(0F, 0F, 0F,
                (float) (Math.cos(theta) * r * length), (float) (y * length),
                (float) (Math.sin(theta) * r * length),
                length / segments, jitter, segments, rollSeed + i * 0x9E3779B97F4A7C15L);
    }

    /** 光柱强度：两路异频正弦 + 每柱独立相位，连续、与帧率无关。 */
    private static float beamFlicker(double now, int i) {
        return 0.55F + 0.45F * (Mth.sin((float) (now * 7.3D + i * 1.7D)) * 0.6F
                + Mth.sin((float) (now * 17.1D + i * 4.3D)) * 0.4F);
    }

    /** 把一条折线逐段发射成米字面片。 */
    private static void emitBeamSegments(PoseStack poseStack, VertexConsumer consumer, float[] pts,
                                         float halfWidth, float v0, float flicker,
                                         int maxAlpha, int r, int g, int b) {
        for (int s = 0; s + 1 < pts.length / 3; s++) {
            int i0 = s * 3;
            int i1 = i0 + 3;
            FxGeometry.emitAlignedBeam(poseStack, consumer,
                    pts[i0], pts[i0 + 1], pts[i0 + 2], pts[i1], pts[i1 + 1], pts[i1 + 2],
                    halfWidth, 3, v0 + s * 0.7F, r, g, b, (int) (maxAlpha * flicker));
        }
    }

    // ------------------------------------------------------------------ 爆炸：水平烟环

    /**
     * 水平烟环：从 0 扩散到 {@code fxShatterRingRadius}。
     *
     * <p><b>刻意是纯几何而不是粒子。</b>原版烟雾粒子做不到：{@code SMOKE}/{@code LARGE_SMOKE}
     * 的 provider 只带位置与速度，寿命与尺寸在粒子类内部固定，无法按需设定；更要命的是粒子
     * 会<b>累积</b>——一个窗口内连续撒，t=200 时 t=160 生的那些仍在出生点附近，于是看到的是
     * 从 0 到 15 全填满的<b>实心盘</b>而不是环。想让每团都落在前沿就得给出生速度
     * {@code (R−r)/T}，而无阻力的烟会一路冲到几百格外。
     *
     * <p>形态是<b>水平环</b>（向四周震开），中央留空：球形炸开会把整座仪式连同玩家视野一起
     * 吞掉，环不会。
     *
     * <p><b>面片 MUST 是 billboard（正对摄像机），且 MUST 用 alpha 混合。</b>
     * 上一版用 {@link FxGeometry#emitCrossGlow} 的三张<b>轴对齐</b>方片 + 加法混合，
     * 撞了三个错：① 在无旋转的世界系里，XZ 面片被透视压成<b>扁椭圆</b>、XY/YZ 面片立成板，
     * 实机读作"被拉伸成椭圆"；② 加法混合<b>只能加亮</b>，无论贴图多灰都读作"白色光球"，
     * 永远读不出烟；③ 轴对齐方片在该换角度看时消失。
     * 现在改用原版 {@code generic_0..7}（即 {@code large_smoke} 的 8 帧软边真烟）
     * + {@code translucent} 常规 alpha 混合 + billboard，puff 在任何角度都是正圆，
     * 且能靠 alpha 混合压暗背景、读作烟。
     */
    private void renderSmokeRing(int since, int burstTicks, float scale,
                                 PoseStack poseStack, MultiBufferSource buffers) {
        float t = Mth.clamp((float) since / burstTicks, 0F, 1F);
        float radius = GensokyouConfig.FX_SHATTER_RING_RADIUS.get().floatValue() * easeOutCubic(t);
        if (radius <= 1.0E-3F) {
            return;
        }
        int layers = GensokyouConfig.FX_SHATTER_RING_LAYERS.get();
        int perLayer = GensokyouConfig.FX_SHATTER_RING_PUFFS_PER_LAYER.get();
        // 中段达峰、两端归零
        float alpha = (float) (4.0D * t * (1.0D - t) * 96.0D);
        if (alpha < 2.0F) {
            return;
        }
        // 厚度：多层水平堆叠读作一圈烟，而不是一条 2D 圆线
        float puff = radius * 0.20F + 0.5F;
        // 每层用不同帧：8 帧真烟轮换，避免整圈读成同一团贴图重复
        for (int layer = 0; layer < layers; layer++) {
            float ly = (layer - (layers - 1) * 0.5F) * 0.55F * scale;
            // 贴图在"帧之间"切换而不是在帧之间渐变，故按层选帧即可；层数少时也要错开
            ResourceLocation tex = SMOKE_FRAMES[(layer + since / 5) & 7];
            VertexConsumer smoke = buffers.getBuffer(DanmakuRenderTypes.translucent(tex));
            PoseStack.Pose pose = poseStack.last();
            for (int i = 0; i < perLayer; i++) {
                // 每层错开半步，避免各层 puff 上下对齐读成"辐条"
                double theta = (i + layer * 0.5D) * (2.0D * Math.PI / perLayer);
                float jitterR = radius * (0.90F + 0.18F * hash(i, layer));
                FxGeometry.emitBillboard(smoke, pose, this.camRot,
                        (float) (Math.cos(theta) * jitterR), ly,
                        (float) (Math.sin(theta) * jitterR),
                        puff, puff * 0.86F,   // 略扁：水平环上的烟团读作被压过
                        SMOKE_R, SMOKE_G, SMOKE_B, (int) alpha);
            }
        }
    }

    /** 确定性 [0,1) 抖动值：同一 (i, layer) 每帧恒定，故烟环不会逐帧乱跳。 */
    private static float hash(int i, int layer) {
        int h = i * 0x27D4EB2D + layer * 0x165667B1;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        return (h & 0x7FFF) / 32768.0F;
    }

    // ------------------------------------------------------------------ 常驻绿十字星

    /**
     * 门体周围的绿色十字星：沿眼形椭圆轨道<b>缓慢公转</b> + 轻微上浮。
     *
     * <p>用**原版 {@code ParticleTypes.GLOW}**：它的贴图本身就是一个 8×8 的十字，provider 随机
     * 给亮绿/暗青两色、半透明混合、亮度随年龄升到近全亮、尺寸约 0.56 格、寿命 8~40 tick。
     * 零新贴图、零新粒子类型——这正是玩家记忆里那个"绿色十字粒子"。
     *
     * <p>密度随开合进度上升、完全闭合时为零；超出渲染距离 MUST NOT 产生开销
     * （包围盒为了覆盖 15 格烟环涨到了 30 格见方，不门控就是每帧白跑）。
     *
     * <p><b>轨道 MUST 贴着眼形。</b>原实现把轨道半轴设成眼半宽/半高 × 1.6，再叠 0.8 格上浮，
     * 而 GLOW 粒子自己还会以 0.02/tick 再飘 0.8 格——四层叠加后绿十字星能跑到
     * 眼顶上方两格、侧向外扩一格多，实机读作"传送门几步路远的别处也有绿星"。
     * 现在轨道只外扩一点点（{@code fxShatterMoteOrbitScale}，默认 1.15）、上浮减半。
     *
     * <p><b>公转速度 MUST 是肉眼可见的。</b>原实现 {@code theta = now * 0.37}，
     * 即每 2.7 tick 转一整圈——注释写着"沿椭圆轨道公转"，实际读作一圈均匀亮环，
     * 根本看不出在转。
     */
    private void emitAmbientMotes(SukimaBlockEntity portal, Level level, Vec3 center,
                                  float extent, double now) {
        int perSec = GensokyouConfig.SUKIMA_PORTAL_MOTES_PER_SEC.get();
        if (perSec <= 0 || extent <= 1.0E-3F) {
            return;
        }
        // 距离门控：包围盒为了覆盖 15 格烟环涨到 30 格见方，不门控就是每帧白跑。
        // ⚠️ 门控距离 MUST 明显小于包围盒：否则几十格外别的门也在往你这边撒绿星，
        //    看着就像"自己这扇门在别处冒绿星"。
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        double dx = player.getX() - center.x;
        double dy = player.getY() - center.y;
        double dz = player.getZ() - center.z;
        if (dx * dx + dy * dy + dz * dz > MOTE_MAX_DISTANCE * MOTE_MAX_DISTANCE) {
            return;
        }
        float scale = portal.scale();
        float orbit = GensokyouConfig.FX_SHATTER_MOTE_ORBIT_SCALE.get().floatValue();
        float rx = HALF_W * scale * orbit;
        float ry = RECT_HALF_H * scale * orbit;
        // 帧率无关的发射预算，状态存在 BE 上（见 SukimaBlockEntity#takeMoteBudget）
        int budget = portal.takeMoteBudget((int) (perSec * extent), now);
        for (int i = 0; i < budget; i++) {
            // 相位：缓慢公转（约 6 秒一圈）+ 每颗错开黄金角，整圈铺满而不是挤成一点
            double seed = now * MOTE_ORBIT_RATE + i * 0.618D;
            double theta = seed * 2.0D * Math.PI;
            // 上浮：慢，且幅度只有 0.45 格（叠加 GLOW 自身的 0.8 格漂移也够看了）
            double rise = (now * 0.05D + i * 0.13D) % 1.0D;
            float wob = (float) Math.sin(seed * 2.3D) * 0.10F;
            level.addParticle(ParticleTypes.GLOW,
                    center.x + (float) Math.cos(theta) * (rx + wob),
                    center.y + (float) (Math.sin(theta) * ry) + rise * 0.45F,
                    center.z + (float) Math.sin(theta * 1.31D) * (rx + wob),
                    0D, 0.012D, 0D);
        }
    }

    // ------------------------------------------------------------------ 包围盒

    /**
     * 渲染包围盒：<b>眼体 ∪ 结界崩解演出</b>。
     *
     * <p>演出必须算进来，否则整段特效会被<b>静默视锥剔除</b>——球半径 3 格、烟环半径 15 格
     * 在任意斜视角下都在"只包住眼体"的盒子之外，而 {@code shouldRenderOffScreen} 只豁免
     * 区块可见性、<b>不豁免视锥</b>。历史上"特效位置不对 / 什么都看不到"的真正病因就在这里，
     * 却被误诊成 billboard Z 方向反了。
     *
     * <p>代价：盒子涨到 30 格见方后 BER 在视距内几乎恒可见，故常驻粒子 MUST 按距离门控。
     */
    @Override
    public AABB getRenderBoundingBox(SukimaBlockEntity blockEntity) {
        float scale = blockEntity.scale();
        float ring = GensokyouConfig.FX_SHATTER_RING_RADIUS.get().floatValue();
        float reach = Math.max(HALF_W * scale, ring);
        float up = Math.max((UPPER_LID + LOWER_LID) * scale, reach);
        BlockPos p = blockEntity.getBlockPos();
        return new AABB(p.getX() + 0.5D - reach, p.getY() + CENTER_Y * scale - reach,
                p.getZ() + 0.5D - reach,
                p.getX() + 0.5D + reach, p.getY() + CENTER_Y * scale + up,
                p.getZ() + 0.5D + reach);
    }
}
