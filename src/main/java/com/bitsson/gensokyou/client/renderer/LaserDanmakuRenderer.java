package com.bitsson.gensokyou.client.renderer;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.entity.LaserDanmaku;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import com.bitsson.gensokyou.danmaku.render.DanmakuRenderProbe;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * 激光渲染器。
 *
 * <p><b>视觉结构（由内到外）：</b>
 * <ol>
 *   <li>亮核 —— near-white，细，制造「过曝」观感</li>
 *   <li>主体 —— 弹幕指定颜色，标准粗细</li>
 *   <li>外发光 —— 半透明自发光，放大包裹</li>
 *   <li>端盖 —— 面向摄像机的圆片，模拟半球末端</li>
 * </ol>
 *
 * <p><b>如何替换资源：</b>
 * <ul>
 *   <li>光束材质：{@code assets/gensokyou/textures/entity/laser_danmaku.png}<br>
 *       UV 约定：U 横跨光束宽度(0→1)，V 沿光束长度平铺（每格贴图重复 {@link #V_TILES_PER_BLOCK} 次）。
 *       建议做成纵向条纹/能量流纹理，中心亮两侧渐隐。</li>
 *   <li>端盖材质：{@code assets/gensokyou/textures/entity/laser_cap.png}<br>
 *       建议做成径向渐变的圆形光斑（中心白，边缘透明）。</li>
 *   <li>只想改颜色/粗细/层数：调下面的常量即可，无需动几何代码。</li>
 * </ul>
 */
public class LaserDanmakuRenderer extends AbstractDanmakuRenderer<LaserDanmaku> {

    // ===============================================================
    // 可调参数：换资源/调观感主要动这里
    // ===============================================================

    private static final ResourceLocation BEAM_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Gensokyou.MODID, "textures/entity/laser_danmaku.png");

    private static final ResourceLocation CAP_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Gensokyou.MODID, "textures/entity/laser_cap.png");

    private static final ResourceLocation MAGIC_CIRCLE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Gensokyou.MODID, "textures/entity/laser_magic_circle.png");

    /** 法阵相对半径（基于激光半径）：阵面撑得比光束大一圈。 */
    private static final float MAGIC_CIRCLE_RADIUS_RATIO = 2.2F;

    /** 法阵自旋速度（度/tick），约 28°/s。 */
    private static final float MAGIC_CIRCLE_SPIN_DEG_PER_TICK = 1.4F;

    /** 法阵透明度（随包络再缩放）。 */
    private static final int MAGIC_CIRCLE_ALPHA = 200;

    /** 构成圆柱的平面数量。越多越圆，6 个（间隔 30°）在观感与开销间较平衡。 */
    private static final int BEAM_PLANES = 6;

    /** 每格长度上贴图重复次数，控制能量流纹理的疏密。 */
    private static final float V_TILES_PER_BLOCK = 1.0F;

    /** 亮核相对半径与颜色混合比（0=纯本色，1=纯白）。 */
    private static final float CORE_RADIUS_RATIO = 0.45F;
    private static final float CORE_WHITE_MIX = 0.75F;

    /** 外发光相对半径。 */
    private static final float OUTER_GLOW_RADIUS_RATIO = 1.9F;
    private static final int OUTER_GLOW_ALPHA = 70;

    /** 延迟指示线相对半径与颜色。 */
    private static final float INDICATOR_RADIUS_RATIO = 0.22F;
    private static final int INDICATOR_COLOR = 0xFF2020;
    private static final int INDICATOR_ALPHA_MIN = 60;
    private static final int INDICATOR_ALPHA_MAX = 190;

    /** 指示线闪烁频率（弧度/tick）。 */
    private static final float INDICATOR_PULSE_SPEED = 0.45F;

    // ===============================================================

    public LaserDanmakuRenderer(EntityRendererProvider.Context context) {
        super(context, BEAM_TEXTURE);
    }

    /**
     * 激光的主体类层走「加法混合 + 写深度」：实体缓冲先于半透明地形（水）冲刷，
     * 写深度后身后的水被深度剔除，修复"激光在水面之前却被水覆盖"的问题。
     * 外发光与法阵同样走写深度（见 renderActiveBeam / renderMagicCircle），
     * 否则其光晕仍会被后画的水/云覆盖。
     */
    @Override
    protected RenderType glowRenderType() {
        return DanmakuRenderTypes.additiveSolid(this.texture);
    }

    @Override
    public void render(LaserDanmaku entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        LaserDanmaku.Phase phase = entity.getPhase();
        if (phase == LaserDanmaku.Phase.DONE) {
            return;
        }

        // MUST 用视觉长度而不是 getActualLength()：后者的裁剪起点是模拟位置，
        // 而世界平移已经带着纠偏量把整条光束挪走了——起点不同，裁到的方块就不同。
        // 症状是「光束末端穿进墙里一截」或「光束够不到墙角」，且不报任何错。
        //
        // 方向与阶段仍取自模拟状态：纠偏是<b>平移</b>，不改朝向；阶段由年龄推导，
        // 与位置同源，二者 MUST 来自同一份状态，否则会出现「位置在旧时刻、阶段在新时刻」。
        double length = entity.getRenderLength(partialTick);
        if (length < 1.0E-3D) {
            return;
        }

        Vec3 direction = entity.getLaserDirection();

        poseStack.pushPose();
        this.alignToDirection(poseStack, direction);

        if (phase == LaserDanmaku.Phase.DELAY) {
            this.renderDelayIndicator(entity, poseStack, bufferSource, (float) length);
        } else {
            this.renderActiveBeam(entity, poseStack, bufferSource, (float) length, partialTick);
        }

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    /**
     * 延迟期：细的半透明红色指示线，随时间脉冲闪烁提示即将开火。
     */
    private void renderDelayIndicator(LaserDanmaku entity, PoseStack poseStack,
                                      MultiBufferSource bufferSource, float length) {
        float radius = (float) entity.getRadius() * INDICATOR_RADIUS_RATIO;

        // 越接近开火，闪烁越快越亮
        float urgency = entity.getDelayProgress();
        float speed = INDICATOR_PULSE_SPEED * (1.0F + urgency * 2.0F);
        float pulse = (Mth.sin(entity.age() * speed) + 1.0F) * 0.5F;
        int alpha = (int) Mth.lerp(pulse * (0.4F + 0.6F * urgency),
                INDICATOR_ALPHA_MIN, INDICATOR_ALPHA_MAX);

        VertexConsumer consumer = this.getBuffer(bufferSource, this.glowRenderType());
        if (DanmakuRenderProbe.effectiveGlow()) {
            DanmakuRenderProbe.countGlow();
            DanmakuRenderProbe.pushGlow();
            try {
                this.emitBeam(poseStack, consumer, length, radius,
                        red(INDICATOR_COLOR), green(INDICATOR_COLOR), blue(INDICATOR_COLOR),
                        alpha, FULL_BRIGHT);
            } finally {
                DanmakuRenderProbe.pop();
            }
        }
    }

    /**
     * 激活期：亮核 + 主体 + 外发光 + 端盖。
     */
    private void renderActiveBeam(LaserDanmaku entity, PoseStack poseStack,
                                  MultiBufferSource bufferSource, float length, float partialTick) {
        float radius = (float) entity.getRadius();
        int color = entity.getColor();
        int r = red(color);
        int g = green(color);
        int b = blue(color);

        // 刚开火与即将结束时做一个快速的粗细收放，避免生硬的出现/消失
        float envelope = this.computeEnvelope(entity, partialTick);
        if (envelope <= 0.0F) {
            return;
        }
        int coreR = (int) Mth.lerp(CORE_WHITE_MIX, r, 255);
        int coreG = (int) Mth.lerp(CORE_WHITE_MIX, g, 255);
        int coreB = (int) Mth.lerp(CORE_WHITE_MIX, b, 255);

        // 外发光同样写深度：不被身后（更远）的水/云覆盖。shader discard alpha<0.1，
        // 只按发光可见轮廓写深度，不会凿出整块方形洞。
        // 注意 immediate 缓冲的别名规则：请求不同 RenderType 会立刻结束上一批，
        // 因此必须按「外发光 → 法阵 → 主体/亮核 → 端盖」整层连续写入，禁止交叉。
        if (DanmakuRenderProbe.effectiveGlow()) {
            VertexConsumer glow = this.getBuffer(bufferSource, DanmakuRenderTypes.additiveSolid(BEAM_TEXTURE));
            DanmakuRenderProbe.countGlow();
            DanmakuRenderProbe.pushGlow();
            try {
                this.emitBeam(poseStack, glow, length, radius * OUTER_GLOW_RADIUS_RATIO * envelope,
                        r, g, b, (int) (OUTER_GLOW_ALPHA * envelope), FULL_BRIGHT);
            } finally {
                DanmakuRenderProbe.pop();
            }
        }

        // 法阵：发射端五芒星，取激光色的反色（与光束形成对比又同源），随包络展开/收起并自旋
        this.renderMagicCircle(entity, poseStack, bufferSource,
                radius * envelope, 255 - r, 255 - g, 255 - b,
                (int) (MAGIC_CIRCLE_ALPHA * envelope), partialTick);

        if (DanmakuRenderProbe.effectiveBody()) {
            VertexConsumer emissive = this.getBuffer(bufferSource, this.glowRenderType());
            DanmakuRenderProbe.countBody();
            DanmakuRenderProbe.pushBody();
            try {
                // 主体
                this.emitBeam(poseStack, emissive, length, radius * envelope,
                        r, g, b, (int) (235 * envelope), FULL_BRIGHT);

                if (DanmakuRenderProbe.effectiveCore()) {
                    // 亮核：向白色混合，制造过曝感
                    this.emitBeam(poseStack, emissive, length, radius * CORE_RADIUS_RATIO * envelope,
                            coreR, coreG, coreB, (int) (255 * envelope), FULL_BRIGHT);
                }
            } finally {
                DanmakuRenderProbe.pop();
            }
        }

        // 端盖：面向摄像机的圆片，模拟半球末端
        this.renderCaps(poseStack, bufferSource, length, radius * envelope,
                coreR, coreG, coreB, (int) (235 * envelope));
    }

    /**
     * 发射端五芒星法阵。
     *
     * <p>局部坐标系 Z+ 沿光束，XY 平面天然垂直于光束——在 z≈0 处画一个
     * 面向 Z 轴的 quad 即得「光束从阵中喷出」的构图，无需额外朝向计算。
     * 缩放基于激光半径 × 粗细包络（开火展开、收束收起），自旋绕 Z 轴恒速。
     */
    private void renderMagicCircle(LaserDanmaku entity, PoseStack poseStack, MultiBufferSource bufferSource,
                                   float beamRadius, int r, int g, int b, int a, float partialTick) {
        if (beamRadius <= 0.0F || a <= 0) {
            return;
        }
        float radius = beamRadius * MAGIC_CIRCLE_RADIUS_RATIO;
        float angle = (entity.age() + partialTick) * MAGIC_CIRCLE_SPIN_DEG_PER_TICK;

        poseStack.pushPose();
        poseStack.mulPose(Axis.ZP.rotationDegrees(angle));

        PoseStack.Pose pose = poseStack.last();
        if (DanmakuRenderProbe.effectiveGlow()) {
            VertexConsumer consumer = this.getBuffer(bufferSource, DanmakuRenderTypes.additiveSolid(MAGIC_CIRCLE_TEXTURE));
            DanmakuRenderProbe.countGlow();
            DanmakuRenderProbe.pushGlow();
            try {
                this.vertex(consumer, pose, -radius, -radius, 0.0F, 0.0F, 1.0F, r, g, b, a, FULL_BRIGHT);
                this.vertex(consumer, pose, radius, -radius, 0.0F, 1.0F, 1.0F, r, g, b, a, FULL_BRIGHT);
                this.vertex(consumer, pose, radius, radius, 0.0F, 1.0F, 0.0F, r, g, b, a, FULL_BRIGHT);
                this.vertex(consumer, pose, -radius, radius, 0.0F, 0.0F, 0.0F, r, g, b, a, FULL_BRIGHT);
            } finally {
                DanmakuRenderProbe.pop();
            }
        }

        poseStack.popPose();
    }

    /**
     * 开火瞬间与收束瞬间的粗细包络，各占 3 tick。
     */
    private float computeEnvelope(LaserDanmaku entity, float partialTick) {
        float age = (entity.age() - entity.getDelayTicks()) + partialTick;
        float duration = entity.getDurationTicks();
        if (age < 0.0F || age > duration) {
            return 0.0F;
        }

        final float ramp = 3.0F;
        float in = Math.min(1.0F, age / ramp);
        float out = Math.min(1.0F, (duration - age) / ramp);
        return Math.min(in, out);
    }

    /**
     * 发射构成圆柱的所有平面。绕 Z 轴均匀分布，形成米字截面。
     */
    private void emitBeam(PoseStack poseStack, VertexConsumer consumer,
                          float length, float radius,
                          int r, int g, int b, int a, int light) {
        if (radius <= 0.0F || a <= 0) {
            return;
        }
        float step = 180.0F / BEAM_PLANES;
        for (int i = 0; i < BEAM_PLANES; i++) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.ZP.rotationDegrees(i * step));
            this.emitBeamPlane(poseStack, consumer, length, radius, r, g, b, a, light);
            poseStack.popPose();
        }
    }

    /**
     * 单个矩形平面：沿 Z+ 从 0 延伸到 length，宽度 2*radius。
     * 双面渲染由 noCull 材质保证，无需重复输出反面。
     */
    private void emitBeamPlane(PoseStack poseStack, VertexConsumer consumer,
                               float length, float radius,
                               int r, int g, int b, int a, int light) {
        PoseStack.Pose pose = poseStack.last();
        float vEnd = length * V_TILES_PER_BLOCK;

        this.vertex(consumer, pose, -radius, 0.0F, 0.0F, 0.0F, 0.0F, r, g, b, a, light);
        this.vertex(consumer, pose, radius, 0.0F, 0.0F, 1.0F, 0.0F, r, g, b, a, light);
        this.vertex(consumer, pose, radius, 0.0F, length, 1.0F, vEnd, r, g, b, a, light);
        this.vertex(consumer, pose, -radius, 0.0F, length, 0.0F, vEnd, r, g, b, a, light);
    }

    /**
     * 两端各画一个面向摄像机的圆片，视觉上等价于半球端盖但开销低得多。
     */
    private void renderCaps(PoseStack poseStack, MultiBufferSource bufferSource,
                            float length, float radius,
                            int r, int g, int b, int a) {
        if (radius <= 0.0F || a <= 0) {
            return;
        }
        if (DanmakuRenderProbe.effectiveGlow()) {
            VertexConsumer consumer = this.getBuffer(bufferSource, DanmakuRenderTypes.additiveSolid(CAP_TEXTURE));
            DanmakuRenderProbe.countGlow();
            DanmakuRenderProbe.pushGlow();
            try {
                this.emitCap(poseStack, consumer, 0.0F, radius, r, g, b, a);
                this.emitCap(poseStack, consumer, length, radius, r, g, b, a);
            } finally {
                DanmakuRenderProbe.pop();
            }
        }
    }

    private void emitCap(PoseStack poseStack, VertexConsumer consumer,
                         float zOffset, float radius,
                         int r, int g, int b, int a) {
        poseStack.pushPose();
        poseStack.translate(0.0F, 0.0F, zOffset);

        // 抵消当前的方向对齐，重新面向摄像机
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());

        PoseStack.Pose pose = poseStack.last();
        this.vertex(consumer, pose, -radius, -radius, 0.0F, 0.0F, 1.0F, r, g, b, a, FULL_BRIGHT);
        this.vertex(consumer, pose, radius, -radius, 0.0F, 1.0F, 1.0F, r, g, b, a, FULL_BRIGHT);
        this.vertex(consumer, pose, radius, radius, 0.0F, 1.0F, 0.0F, r, g, b, a, FULL_BRIGHT);
        this.vertex(consumer, pose, -radius, radius, 0.0F, 0.0F, 0.0F, r, g, b, a, FULL_BRIGHT);

        poseStack.popPose();
    }

    /**
     * 将坐标系 Z+ 对齐到给定方向。
     *
     * <p>Minecraft 约定：{@code yRot = atan2(dx, dz)}，绕 Y+ 正向旋转 θ 时
     * Z+ 会变成 {@code (sin θ, 0, cos θ)}，因此这里用 <b>正</b> yaw。
     */
    private void alignToDirection(PoseStack poseStack, Vec3 direction) {
        double horizontal = direction.horizontalDistance();

        if (horizontal < 1.0E-6D) {
            poseStack.mulPose(Axis.XP.rotationDegrees(direction.y > 0.0D ? -90.0F : 90.0F));
            return;
        }

        float yaw = (float) (Mth.atan2(direction.x, direction.z) * (180D / Math.PI));
        float pitch = (float) (Mth.atan2(-direction.y, horizontal) * (180D / Math.PI));
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
    }

    @Override
    protected void renderShape(LaserDanmaku entity, PoseStack poseStack, VertexConsumer consumer,
                               int r, int g, int b, int a, int light) {
        // 激光有多层结构，不走基类的单层通道，直接在 render() 中组装
    }
}
