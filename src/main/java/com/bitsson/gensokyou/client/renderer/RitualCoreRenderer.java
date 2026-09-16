package com.bitsson.gensokyou.client.renderer;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.ritual.RitualFxLayout;
import com.bitsson.gensokyou.ritual.RitualRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 仪式核心运行态特效渲染器：据只读渲染态按 {@code kind} 分发——
 * 共鸣塔=宽幅紫雾带 + 持续闪电弧、迦具土=贴地烈火场、八方归元=fresnel 灵气球。
 *
 * <p>几何统一走激光弹幕已实机验证的"方向对齐 + 米字交叉面片"路线（{@link FxGeometry
 * #emitAlignedBeam}）——单面摄像机朝向条带在掠射/平行视线时会棱边消失（正是旧版"紫气
 * 薄束"与首测"闪电横流"的成因），多面交叉从任意角度都有正对分量。
 * 启停带淡入淡出包络（ramp ticks 配置），MUST NOT 单帧硬切。
 */
public class RitualCoreRenderer implements BlockEntityRenderer<RitualCoreBlockEntity> {

    // ---- fx 贴图 ----
    private static final ResourceLocation FIRE_TONGUE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Gensokyou.MODID, "textures/fx/fire_tongue.png");
    private static final ResourceLocation FIRE_BED_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Gensokyou.MODID, "textures/fx/fire_bed.png");
    private static final ResourceLocation MIST_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Gensokyou.MODID, "textures/fx/spirit_mist.png");
    private static final ResourceLocation BOLT_CORE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Gensokyou.MODID, "textures/fx/bolt_core.png");
    private static final ResourceLocation BOLT_GLOW_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Gensokyou.MODID, "textures/fx/bolt_glow.png");
    private static final ResourceLocation CAP_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Gensokyou.MODID, "textures/entity/laser_cap.png");

    // 闪电弧通道色沿用原语义：入=青蓝、出=绿；雾带紫、灵气球绿。
    private static final int MIST_R = 199, MIST_G = 92, MIST_B = 250;
    private static final int MIST_DIM_R = 120, MIST_DIM_G = 52, MIST_DIM_B = 160;
    private static final int IN_R = 76, IN_G = 191, IN_B = 242;
    private static final int OUT_R = 76, OUT_G = 217, OUT_B = 89;

    /** 雾带螺旋角速度（rad/tick，沿旧 age=now*0.05 口径）。 */
    private static final float MIST_ROT_SPEED = 0.05F;

    /** pos → 包络槽（0=雾带 1=火柱 2=灵气球）+ 上次时间；WeakHashMap 键为核心 BE 常驻 BlockPos。 */
    private static final Map<BlockPos, float[]> ENVELOPES = new WeakHashMap<>();
    /** 闪电弧逐通道包络：pos → float[channelCount]。 */
    private static final Map<BlockPos, float[]> BOLT_ENVELOPES = new WeakHashMap<>();
    /** 闪电弧折线缓存：pos → 路径（按 roll tick 重掷）。 */
    private static final Map<BlockPos, BoltPaths> BOLT_PATHS = new WeakHashMap<>();

    public RitualCoreRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(RitualCoreBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Level level = blockEntity.getLevel();
        if (level == null) {
            return;
        }
        RitualRenderState state = blockEntity.renderState();
        double now = level.getGameTime() + partialTick;
        switch (state.kind()) {
            case RitualRenderState.KIND_RELAY -> {
                renderMist(blockEntity, state, now, poseStack, bufferSource);
                renderBolts(blockEntity, state, now, poseStack, bufferSource);
            }
            case RitualRenderState.KIND_KAGUTSUICHI ->
                    renderFlame(blockEntity, state, now, poseStack, bufferSource);
            case RitualRenderState.KIND_BAFANG ->
                    renderOrb(blockEntity, state, now, poseStack, bufferSource);
            case RitualRenderState.KIND_SACRIFICE ->
                    renderPillar(blockEntity, state, now, poseStack, bufferSource);
            default -> {
            }
        }
    }

    /**
     * 特效几何远超核心单格（雾带半径数格、闪电弧飞向数十格外的目标、火柱铺满台位），
     * 默认按方块包围盒的视锥剔除会造成"位置可见但特效消失"（抬头/视角边缘）。
     */
    @Override
    public boolean shouldRenderOffScreen(RitualCoreBlockEntity blockEntity) {
        return true;
    }

    /**
     * 特效几何远超核心单格（雾带半径数格、闪电弧飞向数十格外的目标、火柱铺满台位），
     * 默认按方块包围盒的视锥剔除会造成"位置可见但特效消失"（抬头/视角边缘）。
     * 注意：NeoForge 对 global BE 的可见性判定是
     * {@code frustum.isVisible(renderer.getRenderBoundingBox(be))}——{@code shouldRenderOffScreen}
     * 只让它摆脱区块可见性、**不豁免视锥**，所以必须在此声明真实作用域。
     */
    @Override
    public AABB getRenderBoundingBox(RitualCoreBlockEntity blockEntity) {
        BlockPos p = blockEntity.getBlockPos();
        double r = blockEntity.renderState().kind() == RitualRenderState.KIND_RELAY ? 96.0D : 16.0D;
        return new AABB(p.getX() - r, p.getY() - 16.0D, p.getZ() - r,
                p.getX() + r, p.getY() + 48.0D, p.getZ() + r);
    }

    /** 距离上限放宽：共鸣塔链接半径最高 ±80，且特效本体远大于核心，取宽松值。 */
    @Override
    public int getViewDistance() {
        return 192;
    }

    // ================================================================= 雾带

    /** 宽幅紫雾带：螺旋中心线逐段以双面交叉面片发射（任何视角有正对分量），双层错位、阶级加层。 */
    private void renderMist(RitualCoreBlockEntity be, RitualRenderState state, double now,
                            PoseStack poseStack, MultiBufferSource buffers) {
        float env = advanceEnvelope(be.getBlockPos(), 0, state.enabled() ? 1F : 0F, now);
        if (env <= 0F) {
            return;
        }
        BlockPos core = be.getBlockPos();
        double yBottom = state.minY() - core.getY();
        double yTop = Math.max(yBottom + 1.0D, state.maxY() - core.getY() + 1.0D);
        double height = yTop - yBottom;
        int turns = Math.max(2, Mth.ceil(height / 1.6D));
        int segments = Math.min(160, turns * 20);
        int layers = Mth.clamp(1 + state.tier() / 2, 1, GensokyouConfig.FX_MIST_LAYERS_MAX.get());
        float scroll = (float) (now * GensokyouConfig.FX_MIST_SCROLL_SPEED.get());
        double radiusBase = GensokyouConfig.FX_MIST_RADIUS.get();
        float bandHalf = GensokyouConfig.FX_MIST_BAND_WIDTH.get().floatValue() * 0.5F;
        double wobble = GensokyouConfig.FX_MIST_WOBBLE.get();
        double rot = now * MIST_ROT_SPEED;

        VertexConsumer glow = buffers.getBuffer(DanmakuRenderTypes.additiveGlow(MIST_TEXTURE));
        for (int layer = 0; layer < layers; layer++) {
            float layerFade = env * (layer == 0 ? 1.0F : 0.6F / layer);
            float halfW = bandHalf * (layer == 0 ? 1.0F : 1.35F);
            double radiusOff = layer * 0.45D;
            double phase = layer * Math.PI * 0.6667D;
            int r = layer == 0 ? MIST_R : MIST_DIM_R;
            int g = layer == 0 ? MIST_G : MIST_DIM_G;
            int b = layer == 0 ? MIST_B : MIST_DIM_B;
            float px = 0F, py = 0F, pz = 0F;
            for (int i = 0; i <= segments; i++) {
                float t = i / (float) segments;
                double angle = t * turns * Math.PI * 2.0D + phase + rot;
                double radius = radiusBase + radiusOff
                        + wobble * Mth.sin(t * 6.0F * (float) Math.PI + (float) (now * 0.07D));
                float x = 0.5F + Mth.cos((float) angle) * (float) radius;
                float z = 0.5F + Mth.sin((float) angle) * (float) radius;
                float y = (float) Mth.lerp(t, yBottom, yTop);
                if (i > 0) {
                    FxGeometry.emitAlignedBeam(poseStack, glow, px, py, pz, x, y, z,
                            halfW, 2, scroll + (i - 1) * 0.18F,
                            r, g, b, (int) (85F * layerFade));
                }
                px = x;
                py = y;
                pz = z;
            }
        }
    }

    // ================================================================ 闪电弧

    /** 持续放电闪电弧：抖动折线逐段米字面片（晕 3 面 + 芯 2 面，均加法），两端十字光斑。 */
    private void renderBolts(RitualCoreBlockEntity be, RitualRenderState state, double now,
                             PoseStack poseStack, MultiBufferSource buffers) {
        int channels = state.channelCount();
        BlockPos core = be.getBlockPos();
        if (channels == 0) {
            BOLT_ENVELOPES.remove(core);
            BOLT_PATHS.remove(core);
            return;
        }
        float[] envs = boltEnvelopes(core, channels);
        long roll = (long) (now / Math.max(1, GensokyouConfig.FX_BOLT_ROLL_TICKS.get()));
        BoltPaths paths = BOLT_PATHS.computeIfAbsent(core, k -> new BoltPaths());
        if (paths.rollTick != roll || paths.paths.length != channels) {
            paths.reset(channels, roll);
        }
        int ramp = Math.max(1, GensokyouConfig.FX_RAMP_TICKS.get());
        float step = (float) Mth.clamp(now - paths.lastTime, 0.0D, 2.0D) / ramp;
        paths.lastTime = now;

        float segLen = GensokyouConfig.FX_BOLT_SEGMENT_LEN.get().floatValue();
        float jitter = GensokyouConfig.FX_BOLT_JITTER.get().floatValue();
        float coreHalf = GensokyouConfig.FX_BOLT_CORE_WIDTH.get().floatValue();
        float glowHalf = GensokyouConfig.FX_BOLT_GLOW_WIDTH.get().floatValue();
        int maxSegments = GensokyouConfig.FX_BOLT_MAX_SEGMENTS.get();
        double startY = state.maxY() - core.getY() + 1.2D;
        float scroll = (float) (now * 0.35D);

        boolean any = false;
        for (int i = 0; i < channels; i++) {
            float target = state.enabled() && state.channelMoving(i) ? 1F : 0F;
            envs[i] = Mth.clamp(envs[i] + (target > envs[i] ? step : -step), 0F, 1F);
            if (envs[i] > 0F) {
                any = true;
            }
        }
        if (!any) {
            return;
        }

        // 折线物化（一次 roll 内稳定）
        float[][] ptsOf = new float[channels][];
        for (int i = 0; i < channels; i++) {
            if (envs[i] <= 0F) {
                continue;
            }
            BlockPos target = state.linkAt(i);
            float ax = 0.5F;
            float ay = (float) startY;
            float az = 0.5F;
            float bx = target.getX() - core.getX() + 0.5F;
            float by = target.getY() - core.getY() + 1.2F;
            float bz = target.getZ() - core.getZ() + 0.5F;
            float[] pts = paths.paths[i];
            if (pts == null) {
                pts = paths.paths[i] = buildBoltPoints(ax, ay, az, bx, by, bz,
                        segLen, jitter, maxSegments,
                        core.asLong() ^ Long.rotateLeft(target.asLong(), 21)
                                ^ (roll * 0x9E3779B97F4A7C15L));
            }
            ptsOf[i] = pts;
        }

        // 三遍严格顺序提交：请求不同 RenderType 会**立即结算上一批**（BufferSource 别名规则），
        // 因此绝不可先把多个 consumer 全取出来再交叉写（首测崩溃 "Not building!" 即此）。
        // 每遍开始前才 getBuffer，遍内只写同一种 RenderType。
        VertexConsumer caps = buffers.getBuffer(DanmakuRenderTypes.additiveGlow(CAP_TEXTURE));
        for (int i = 0; i < channels; i++) {
            float[] pts = ptsOf[i];
            if (pts == null) {
                continue;
            }
            float env = envs[i];
            boolean out = i >= state.inCount();
            int cr = out ? OUT_R : IN_R;
            int cg = out ? OUT_G : IN_G;
            int cb = out ? OUT_B : IN_B;
            int segments = pts.length / 3 - 1;
            FxGeometry.emitCrossGlow(poseStack, caps, pts[0], pts[1], pts[2],
                    glowHalf * 1.5F * env, cr, cg, cb, (int) (160F * env));
            int last = segments * 3;
            FxGeometry.emitCrossGlow(poseStack, caps, pts[last], pts[last + 1], pts[last + 2],
                    glowHalf * 1.7F * env, cr, cg, cb, (int) (190F * env));
        }
        VertexConsumer glowBuf = buffers.getBuffer(DanmakuRenderTypes.additiveGlow(BOLT_GLOW_TEXTURE));
        for (int i = 0; i < channels; i++) {
            float[] pts = ptsOf[i];
            if (pts == null) {
                continue;
            }
            float env = envs[i];
            boolean out = i >= state.inCount();
            int segments = pts.length / 3 - 1;
            for (int s = 0; s < segments; s++) {
                int i0 = s * 3;
                int i1 = i0 + 3;
                FxGeometry.emitAlignedBeam(poseStack, glowBuf,
                        pts[i0], pts[i0 + 1], pts[i0 + 2], pts[i1], pts[i1 + 1], pts[i1 + 2],
                        glowHalf * env, 3, scroll + s * 0.7F,
                        out ? OUT_R : IN_R, out ? OUT_G : IN_G, out ? OUT_B : IN_B,
                        (int) (85F * env));
            }
        }
        // 亮核走"加法 + 写深度"（激光弹幕主体的同一策略）：否则 BE 阶段先于半透明地形绘制，
        // 更近的弧会被后画的玻璃/悬浮物盖住。外晕保持不写深度的发光层，避免凿出硬边洞。
        VertexConsumer coreBuf = buffers.getBuffer(DanmakuRenderTypes.additiveSolid(BOLT_CORE_TEXTURE));
        for (int i = 0; i < channels; i++) {
            float[] pts = ptsOf[i];
            if (pts == null) {
                continue;
            }
            float env = envs[i];
            boolean out = i >= state.inCount();
            int segments = pts.length / 3 - 1;
            for (int s = 0; s < segments; s++) {
                int i0 = s * 3;
                int i1 = i0 + 3;
                FxGeometry.emitAlignedBeam(poseStack, coreBuf,
                        pts[i0], pts[i0 + 1], pts[i0 + 2], pts[i1], pts[i1 + 1], pts[i1 + 2],
                        coreHalf * env, 2, scroll + s * 0.7F,
                        (int) Mth.lerp(0.72F, out ? OUT_R : IN_R, 255),
                        (int) Mth.lerp(0.72F, out ? OUT_G : IN_G, 255),
                        (int) Mth.lerp(0.72F, out ? OUT_B : IN_B, 255),
                        (int) (235F * env));
            }
        }
    }

    /** 折线顶点：等长分段 + 垂直双向随机抖动，正弦包络两端归零。返回 [x,y,z]*。 */
    private static float[] buildBoltPoints(float ax, float ay, float az, float bx, float by, float bz,
                                           float segLen, float jitter, int maxSegments, long seed) {
        float dx = bx - ax, dy = by - ay, dz = bz - az;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int segments = Mth.clamp((int) (dist / Math.max(0.1F, segLen)), 4, maxSegments);
        // 垂直正交基：u 取水平垂向，v = dir×u，均需归一（v 长度含 |d| 因子，勿遗漏）
        float ux, uy, uz;
        if (Math.sqrt(dx * dx + dz * dz) > 1.0E-4F) {
            ux = -dz;
            uy = 0.0F;
            uz = dx;
        } else {
            ux = 1.0F;
            uy = 0.0F;
            uz = 0.0F;
        }
        float ul = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        ux /= ul;
        uy /= ul;
        uz /= ul;
        float vx = dy * uz - dz * uy;
        float vy = dz * ux - dx * uz;
        float vz = dx * uy - dy * ux;
        float vl = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (vl < 1.0E-4F) {
            vx = 0.0F;
            vy = 1.0F;
            vz = 0.0F;
        } else {
            vx /= vl;
            vy /= vl;
            vz /= vl;
        }
        RandomSource random = RandomSource.create(seed);
        float[] pts = new float[(segments + 1) * 3];
        for (int i = 0; i <= segments; i++) {
            float t = i / (float) segments;
            float amp = i == 0 || i == segments ? 0.0F
                    : jitter * Mth.sin(t * (float) Math.PI);
            float o1 = random.nextFloat() * 2.0F - 1.0F;
            float o2 = random.nextFloat() * 2.0F - 1.0F;
            pts[i * 3] = Mth.lerp(t, ax, bx) + (ux * o1 + vx * o2) * amp;
            pts[i * 3 + 1] = Mth.lerp(t, ay, by) + (uy * o1 + vy * o2) * amp;
            pts[i * 3 + 2] = Mth.lerp(t, az, bz) + (uz * o1 + vz * o2) * amp;
        }
        return pts;
    }

    private static float[] boltEnvelopes(BlockPos pos, int channels) {
        float[] envs = BOLT_ENVELOPES.get(pos);
        if (envs == null || envs.length != channels) {
            float[] rebuilt = new float[channels];
            if (envs != null) {
                System.arraycopy(envs, 0, rebuilt, 0, Math.min(envs.length, channels));
            }
            BOLT_ENVELOPES.put(pos, rebuilt);
            envs = rebuilt;
        }
        return envs;
    }

    // ============================================================ 贴地烈火场

    /**
     * 迦具土贴地烈火场（ritual-presentation-polish D6）：结构半径内均匀铺地火（布局纯函数），
     * 贴地火舌 + 脉动地面辉光 + 客户端本地余烬。整体观感为"仪式被烈火炙烤"，
     * MUST NOT 使用离散炎柱复制体；采样点半径硬钳于结构半径内（含抖动）。
     */
    private void renderFlame(RitualCoreBlockEntity be, RitualRenderState state, double now,
                             PoseStack poseStack, MultiBufferSource buffers) {
        BlockPos core = be.getBlockPos();
        int tier = state.tier();
        float env = advanceEnvelope(core, 1, state.enabled() && state.burning() ? 1F : 0F, now);
        if (env <= 0F) {
            return;
        }
        List<RitualFxLayout.FirePoint> points = RitualFxLayout.fireBed(
                core, state.linkPos(), tier,
                GensokyouConfig.FX_FIRE_DENSITY_BASE.get(),
                GensokyouConfig.FX_FIRE_DENSITY_PER_TIER.get(),
                state.maxY(),   // kind=KAGUTSUCHI 时 maxY 语义为结构水平半径
                GensokyouConfig.FX_FIRE_RADIUS_RATIO.get());
        int planes = GensokyouConfig.FX_FIRE_TONGUE_PLANES.get();
        float halfWidth = GensokyouConfig.FX_FIRE_TONGUE_WIDTH.get().floatValue();
        float height = GensokyouConfig.FX_FIRE_TONGUE_HEIGHT_BASE.get().floatValue()
                + GensokyouConfig.FX_FIRE_TONGUE_HEIGHT_PER_TIER.get().floatValue() * tier;
        float glow = GensokyouConfig.FX_FIRE_GLOW_RADIUS.get().floatValue() + 0.25F * tier;
        float glowAlpha = GensokyouConfig.FX_FIRE_GLOW_INTENSITY.get().floatValue();
        double pulseSpeed = GensokyouConfig.FX_FIRE_GLOW_PULSE_SPEED.get();
        float scroll = (float) (now * GensokyouConfig.FX_FIRE_SCROLL_SPEED.get());

        // 两遍严格顺序提交（不同 RenderType 会立即结算上一批）：先地面辉光，再火舌
        VertexConsumer ground = buffers.getBuffer(DanmakuRenderTypes.additiveGlow(FIRE_BED_TEXTURE));
        for (RitualFxLayout.FirePoint point : points) {
            float edgeFade = 1.0F - 0.7F * point.edge();
            if (edgeFade <= 0.01F) {
                continue;
            }
            RandomSource phase = RandomSource.create(point.seed());
            float wobble = 0.8F + 0.35F * Mth.sin((float) (now * 0.22D + phase.nextDouble() * 12D));
            float pulse = 0.75F + 0.25F * Mth.sin((float) (now * pulseSpeed + phase.nextDouble() * 6.28D));
            FxGeometry.emitGroundGlow(poseStack, ground, (float) point.x(),
                    (float) point.y() + 1.02F, (float) point.z(),
                    glow * edgeFade * wobble,
                    255, 150, 60, (int) (110F * env * glowAlpha * pulse * edgeFade));
        }
        VertexConsumer tongue = buffers.getBuffer(DanmakuRenderTypes.additiveGlow(FIRE_TONGUE_TEXTURE));
        for (RitualFxLayout.FirePoint point : points) {
            float edgeFade = 1.0F - 0.7F * point.edge();
            if (edgeFade <= 0.01F) {
                continue;
            }
            RandomSource phase = RandomSource.create(point.seed());
            float wobble = 0.8F + 0.35F * Mth.sin((float) (now * 0.22D + phase.nextDouble() * 12D));
            float pulse = 0.75F + 0.25F * Mth.sin((float) (now * pulseSpeed + phase.nextDouble() * 6.28D));
            poseStack.pushPose();
            poseStack.translate(point.x(), point.y() + 1.0D, point.z());
            FxGeometry.emitCrossPlanes(poseStack, tongue, planes,
                    halfWidth * edgeFade * wobble, height * (0.9F + 0.2F * pulse),
                    scroll, scroll + height,
                    255, 205, 130, (int) (185F * env * edgeFade * wobble));
            poseStack.popPose();
        }
        emitEmbers(be, points);
    }

    /** 客户端本地余烬/火星（低频点缀）：MUST NOT 引入服务端粒子包。 */
    private static void emitEmbers(RitualCoreBlockEntity be, List<RitualFxLayout.FirePoint> points) {
        Level level = be.getLevel();
        if (level == null || points.isEmpty() || level.getRandom().nextFloat() > 0.45F) {
            return;
        }
        RitualFxLayout.FirePoint point = points.get(level.getRandom().nextInt(points.size()));
        level.addParticle(level.getRandom().nextFloat() < 0.5F
                        ? ParticleTypes.SMALL_FLAME : ParticleTypes.FLAME,
                be.getBlockPos().getX() + point.x(),
                be.getBlockPos().getY() + 1.05D,
                be.getBlockPos().getZ() + point.z(),
                0.0D, 0.02D, 0.0D);
    }

    // ================================================================ 灵气球

    /** 八方归元灵气球：fresnel shader 球 + 阶级缩放 + 呼吸（含轻微上下浮动）。 */
    private void renderOrb(RitualCoreBlockEntity be, RitualRenderState state, double now,
                           PoseStack poseStack, MultiBufferSource buffers) {
        BlockPos core = be.getBlockPos();
        float env = advanceEnvelope(core, 2, state.enabled() ? 1F : 0F, now);
        if (env <= 0F) {
            return;
        }
        // D6 单点装配：阶级 → 半径/高度（未来水位表现仅改此处取值源）
        float radius = GensokyouConfig.FX_ORB_RADIUS_BASE.get().floatValue()
                + GensokyouConfig.FX_ORB_RADIUS_PER_TIER.get().floatValue() * state.tier();
        // 悬浮高度与半径联动：大球抬得更高、底缘将触未触核心，"凝于塔上"
        float hover = 1.0F + GensokyouConfig.FX_ORB_HOVER_BASE.get().floatValue()
                + GensokyouConfig.FX_ORB_HOVER_PER_TIER.get().floatValue() * state.tier()
                + radius * 0.6F;
        double period = Math.max(2.0D, GensokyouConfig.FX_ORB_BREATH_PERIOD_TICKS.get());
        double breath = Math.sin(now * Math.PI * 2.0D / period);
        float scale = radius * (1.0F + (float) (GensokyouConfig.FX_ORB_BREATH_AMP.get() * breath));
        float bob = hover + 0.1F * (float) breath;

        poseStack.pushPose();
        poseStack.translate(0.5D, bob, 0.5D);
        if (SpiritOrbRenderTypes.timeUniform != null) {
            SpiritOrbRenderTypes.timeUniform.set((float) (now % 1000000.0D));
        }
        VertexConsumer orb = buffers.getBuffer(SpiritOrbRenderTypes.ORB);
        FxGeometry.emitUnitSphere(orb, poseStack.last(), scale,
                255, 255, 255, (int) (220F * env));
        poseStack.popPose();
    }

    // ============================================================ 献祭光柱

    /**
     * 献祭产出瞬间的巨大光柱（tool-sacrifice-rituals）：核心处竖直米字面片，按剩余刻淡入淡出，
     * 结束即消失。复用激光弹幕几何（{@link FxGeometry#emitCrossPlanes}）与既有光束贴图，
     * 不新增贴图资源；服务端只在产出那刻下发起始态，无持续粒子包。
     */
    private void renderPillar(RitualCoreBlockEntity be, RitualRenderState state, double now,
                              PoseStack poseStack, MultiBufferSource buffers) {
        int total = Math.max(1, GensokyouConfig.FX_PILLAR_TICKS.get());
        int remain = Math.max(0, state.maxY());
        float ramp = Math.max(1, GensokyouConfig.FX_RAMP_TICKS.get());
        float fadeIn = Mth.clamp((total - remain) / ramp, 0F, 1F);
        float fadeOut = Mth.clamp(remain / ramp, 0F, 1F);
        float env = Math.min(fadeIn, fadeOut);
        if (env <= 0F) {
            return;
        }
        float height = Math.max(1, state.minY());
        float half = GensokyouConfig.FX_PILLAR_WIDTH.get().floatValue();
        float scroll = (float) (now * 0.9D);
        int[] rgb = pillarColor(state.period());
        float pulse = 0.85F + 0.15F * Mth.sin((float) (now * 0.6D));

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.0D, 0.5D);
        VertexConsumer glow = buffers.getBuffer(DanmakuRenderTypes.additiveGlow(BOLT_GLOW_TEXTURE));
        FxGeometry.emitCrossPlanes(poseStack, glow, 4, half * env, height,
                scroll, scroll + height, rgb[0], rgb[1], rgb[2], (int) (150F * env * pulse));
        VertexConsumer coreBuf = buffers.getBuffer(DanmakuRenderTypes.additiveGlow(BOLT_CORE_TEXTURE));
        FxGeometry.emitCrossPlanes(poseStack, coreBuf, 3, half * 0.45F * env, height,
                scroll * 1.3F, scroll + height,
                (int) Mth.lerp(0.5F, rgb[0], 255), (int) Mth.lerp(0.5F, rgb[1], 255),
                (int) Mth.lerp(0.5F, rgb[2], 255), (int) (220F * env));
        poseStack.popPose();
    }

    /** 献祭光柱色（0=石 1=木 2=土 3=草）。 */
    private static int[] pillarColor(int index) {
        return switch (index) {
            case 1 -> new int[]{141, 110, 99};
            case 2 -> new int[]{188, 170, 164};
            case 3 -> new int[]{129, 199, 132};
            default -> new int[]{176, 190, 197};
        };
    }

    // ================================================================= 公用

    /** 包络推进：slot 0=雾带 1=火柱 2=灵气球；按 ramp ticks 线性淡入淡出（跳帧钳 2 tick 步长）。 */
    private static float advanceEnvelope(BlockPos pos, int slot, float target, double now) {
        float[] st = ENVELOPES.computeIfAbsent(pos, k -> new float[]{0F, 0F, 0F, -1F});
        float last = st[3];
        double elapsed = last < 0F ? 0F : Mth.clamp(now - last, 0F, 2.0D);
        st[3] = (float) now;
        float ramp = Math.max(1, GensokyouConfig.FX_RAMP_TICKS.get());
        float step = (float) elapsed / ramp;
        float value = st[slot];
        value = target > value ? Math.min(target, value + step) : Math.max(target, value - step);
        st[slot] = value;
        return value;
    }

    /** 闪电弧路径缓存：一次 roll tick 内各通道折线稳定，跨 roll 重掷。 */
    private static final class BoltPaths {
        long rollTick = Long.MIN_VALUE;
        double lastTime;
        float[][] paths = new float[0][];

        void reset(int channels, long roll) {
            if (paths.length != channels) {
                paths = new float[channels][];
            } else {
                java.util.Arrays.fill(paths, null);
            }
            rollTick = roll;
        }
    }
}
