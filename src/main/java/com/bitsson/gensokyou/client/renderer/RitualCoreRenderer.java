package com.bitsson.gensokyou.client.renderer;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity;
import com.bitsson.gensokyou.block.entity.RitualPedestalBlockEntity;
import com.bitsson.gensokyou.client.ritual.ClientRitualData;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.ritual.RitualBehaviors;
import com.bitsson.gensokyou.ritual.RitualFxLayout;
import com.bitsson.gensokyou.ritual.RitualPedestals;
import com.bitsson.gensokyou.ritual.RitualRenderState;
import com.bitsson.gensokyou.spirit.SpiritCoreItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

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
    /** 煅炉火星场专用噪声火团：与火柱的 fire_tongue 刻意分开的第二张图。 */
    private static final ResourceLocation FIRE_EMBER_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Gensokyou.MODID, "textures/fx/fire_ember.png");
    private static final ResourceLocation MIST_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Gensokyou.MODID, "textures/fx/spirit_mist.png");
    private static final ResourceLocation BOLT_CORE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Gensokyou.MODID, "textures/fx/bolt_core.png");
    private static final ResourceLocation BOLT_GLOW_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Gensokyou.MODID, "textures/fx/bolt_glow.png");
    private static final ResourceLocation CAP_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Gensokyou.MODID, "textures/entity/laser_cap.png");

    private static final int MIST_R = 199, MIST_G = 92, MIST_B = 250;
    private static final int MIST_DIM_R = 120, MIST_DIM_G = 52, MIST_DIM_B = 160;

    /**
     * 闪电弧通道色：入（抽取侧）= <b>青绿</b> {@code #1FD0C8}、出（注入侧）= <b>橙红</b>
     * {@code #FF6040}。二者为互补色对——在加法混合的过曝区里，邻近色相（旧的青↔绿）会一起
     * 泛白而不可分，互补对则各自保持可辨。雾带紫、灵气球绿不在此列。
     */
    private static final int IN_R = 31, IN_G = 208, IN_B = 200;
    private static final int OUT_R = 255, OUT_G = 96, OUT_B = 64;

    /**
     * 亮芯层向白提亮比例。<b>MUST NOT 超过 0.58</b>——亮芯 alpha 高（235）且是远处主导画面的
     * 那一层，旧的 0.72 意味着 72% 白，加法混合下双向弧一起洗白，正是「特定情况下分不出
     * 输入输出」的主因。0.58 仍保证亮芯至少保留 42% 通道色相。
     */
    private static final float BOLT_CORE_WHITEN = 0.58F;

    /**
     * 落点端光斑向白提亮比例。**两端必须异色**，否则弧体本体洗白后读不出流向：
     * 塔端用通道本色（能量自此出发），落点端提亮 40%（能量在此汇聚）。
     */
    private static final float BOLT_TARGET_CAP_WHITEN = 0.4F;

    /**
     * 八方归元祭品台汇流激光色：<b>青白</b> {@code #C8F0FF}。
     *
     * <p>MUST NOT 与焦点核同族（核为绿）。24 条绿激光叠在绿核上会糊成一团、读不出条数。
     */
    private static final int BEAM_R = 200, BEAM_G = 240, BEAM_B = 255;

    /** 雾带螺旋角速度（rad/tick，沿旧 age=now*0.05 口径）。 */
    private static final float MIST_ROT_SPEED = 0.05F;

    /** pos → 包络槽（0=雾带 1=火柱 2=灵气球）+ 上次时间；WeakHashMap 键为核心 BE 常驻 BlockPos。 */
    private static final Map<BlockPos, float[]> ENVELOPES = new WeakHashMap<>();
    /** 闪电弧逐通道包络：pos → float[channelCount]。 */
    private static final Map<BlockPos, float[]> BOLT_ENVELOPES = new WeakHashMap<>();
    /** 闪电弧折线缓存：pos → 路径（按 roll tick 重掷）。 */
    private static final Map<BlockPos, BoltPaths> BOLT_PATHS = new WeakHashMap<>();
    /** 星移演出逐 tick 发射去重：pos → 上次发射的 gameTime（BER 逐帧回调，不去重会按帧率放大）。 */
    private static final Map<BlockPos, Long> SEII_LAST_TICK = new WeakHashMap<>();
    /** 八方归元祭品台激光逐台包络：pos → float[台数]。台数随阶级变，重建时保留已有分量。 */
    private static final Map<BlockPos, float[]> BAFANG_BEAM_ENVELOPES = new WeakHashMap<>();
    /**
     * 八方归元台位偏移缓存：阶级 → 该阶台位（相对核心，规范序）。
     *
     * <p>纯函数结果，故可长期缓存；数据源（pattern JSON）登录时一次性下发，之后不变。
     * 用 ConcurrentHashMap 而非 WeakHashMap：key 是小整数 boxed，无需也不应回收。
     */
    private static final Map<Integer, List<BlockPos>> BAFANG_PEDESTALS = new ConcurrentHashMap<>();

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
            case RitualRenderState.KIND_KANAYAMAHIKO ->
                    renderForge(blockEntity, state, now, poseStack, bufferSource);
            case RitualRenderState.KIND_BAFANG ->
                    renderOrb(blockEntity, state, now, poseStack, bufferSource);
            case RitualRenderState.KIND_SACRIFICE ->
                    renderPillar(blockEntity, state, now, poseStack, bufferSource);
            case RitualRenderState.KIND_WUJINZANG ->
                    renderWujinzang(blockEntity, state, now, poseStack, bufferSource);
            case RitualRenderState.KIND_SEII ->
                    renderSeii(blockEntity, state, now, poseStack, bufferSource);
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
        int kind = blockEntity.renderState().kind();
        double r = kind == RitualRenderState.KIND_RELAY ? 96.0D
                : kind == RitualRenderState.KIND_WUJINZANG ? 32.0D : 16.0D;
        // 煅炉火星场铺满结构水平半径（maxY 即半径），L2 约 20 格，向上抬到火柱顶
        double forgeLift = GensokyouConfig.FX_FORGE_EMBER_LIFT.get()
                + GensokyouConfig.FX_FORGE_PILLAR_HEIGHT.get();
        double up = kind == RitualRenderState.KIND_WUJINZANG
                ? Math.max(48.0D, GensokyouConfig.FX_WUJINZANG_LASER_HEIGHT.get())
                : kind == RitualRenderState.KIND_KANAYAMAHIKO
                        ? Math.max(16.0D, blockEntity.renderState().maxY() + forgeLift)
                        : 48.0D;
        double radius = kind == RitualRenderState.KIND_KANAYAMAHIKO
                ? Math.max(r, blockEntity.renderState().maxY() + 1.0D)
                : r;
        return new AABB(p.getX() - radius, p.getY() - 16.0D, p.getZ() - radius,
                p.getX() + radius, p.getY() + up, p.getZ() + radius);
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
        // 两端落点光斑：塔端取通道本色、落点端同色相向白提亮，使流向在弧体洗白时仍可读。
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
                    glowHalf * 1.7F * env,
                    (int) Mth.lerp(BOLT_TARGET_CAP_WHITEN, 255, cr),
                    (int) Mth.lerp(BOLT_TARGET_CAP_WHITEN, 255, cg),
                    (int) Mth.lerp(BOLT_TARGET_CAP_WHITEN, 255, cb),
                    (int) (190F * env));
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
                        (int) Mth.lerp(BOLT_CORE_WHITEN, out ? OUT_R : IN_R, 255),
                        (int) Mth.lerp(BOLT_CORE_WHITEN, out ? OUT_G : IN_G, 255),
                        (int) Mth.lerp(BOLT_CORE_WHITEN, out ? OUT_B : IN_B, 255),
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

    // ============================================================ 金山彦命煅炉

    /**
     * 煅炉燃烧表现（两段）：
     * <ol>
     *   <li><b>密集火星场</b>：结构水平半径内铺满小型火舌几何（{@link RitualFxLayout#emberField}），
     *       每点按自身 {@code cycle} 上升并渐隐，视觉等同"大量火焰粒子"——
     *       MUST NOT 走 {@link ParticleTypes}，全部是加法混合交叉面片。</li>
     *   <li><b>祭品台火柱</b>：仅对 {@code movingMask} 中点亮的台位绘制分段 ribbon 火柱，
     *       台面物品本身被火舌包裹；熄灭位立即收火（包络淡出，无硬切）。</li>
     * </ol>
     * 两种几何共用同一 RenderType，故可一次 {@code getBuffer} 连续写完，无需分批。
     */
    private void renderForge(RitualCoreBlockEntity be, RitualRenderState state, double now,
                             PoseStack poseStack, MultiBufferSource buffers) {
        BlockPos core = be.getBlockPos();
        float env = advanceEnvelope(core, 3, state.enabled() && state.forgeBurning() ? 1F : 0F, now);
        if (env <= 0F) {
            return;
        }
        int tier = state.tier();
        // 火星场与火柱用**两张不同纹理**，故是两种 RenderType；必须"取一批写完再取下一批"，
        // 绝不能交叉写（取新 RenderType 会立即结算上一批）。
        VertexConsumer embersOut = buffers.getBuffer(
                DanmakuRenderTypes.additiveGlow(FIRE_EMBER_TEXTURE));

        // ---- 1. 密集火星场 ----
        List<RitualFxLayout.EmberPoint> embers = RitualFxLayout.emberField(
                core, tier,
                GensokyouConfig.FX_FORGE_EMBER_COUNT_BASE.get(),
                GensokyouConfig.FX_FORGE_EMBER_COUNT_PER_TIER.get(),
                state.maxY(),   // kind=KANAYAMAHIKO 时 maxY 语义为结构水平半径
                GensokyouConfig.FX_FORGE_EMBER_RADIUS_RATIO.get());
        float emberHalf = GensokyouConfig.FX_FORGE_EMBER_WIDTH.get().floatValue();
        float emberHeight = GensokyouConfig.FX_FORGE_EMBER_HEIGHT.get().floatValue();
        float lift = GensokyouConfig.FX_FORGE_EMBER_LIFT.get().floatValue();
        // 上升周期（tick）：阶级越高火星越密，单点循环越快，铺满感来自"数量"而非"速度"
        double cycleTicks = Math.max(6.0D, 26.0D - 4.0D * tier);
        float scroll = (float) (now * GensokyouConfig.FX_FIRE_SCROLL_SPEED.get() * 1.6D);
        for (RitualFxLayout.EmberPoint ember : embers) {
            float edgeFade = 1.0F - 0.55F * ember.edge();
            if (edgeFade <= 0.01F) {
                continue;
            }
            RandomSource phase = RandomSource.create(ember.seed());
            float jitter = phase.nextFloat();
            // 每个点一个循环相位：t=0 生于台面，t=1 升到最高并熄灭
            float t = (float) (((now / cycleTicks) + ember.cycle()) % 1.0D);
            float rise = lift * t;
            // 淡入（前 18%）— 稳定 — 淡出（后 55%），端点都收 0，避免整片硬切
            float fadeOut = (float) Mth.smoothstep((t - 0.45D) / 0.55D);
            float envelope = Math.min(1.0F, t / 0.18F) * (1.0F - Mth.clamp(fadeOut, 0.0F, 1.0F));
            float flicker = 0.72F + 0.28F * Mth.sin((float) (now * 0.35D + jitter * 12.56D));
            float scale = ember.scale() * edgeFade * flicker * envelope * env;
            if (scale <= 0.02F) {
                continue;
            }
            // 越升越淡越宽（火焰受热膨胀的观感）
            float swell = 1.0F + 0.45F * t;
            poseStack.pushPose();
            poseStack.translate(ember.x(), ember.y() + 1.0D + rise, ember.z());
            FxGeometry.emitCrossPlanes(poseStack, embersOut, 2,
                    emberHalf * scale * swell, emberHeight * scale,
                    scroll + jitter, scroll + jitter + emberHeight,
                    255, 176, 92, (int) (200F * scale));
            poseStack.popPose();
        }

        // ---- 2. 祭品台火柱（火舌条带贴图，与火星场刻意区分）----
        VertexConsumer flames = buffers.getBuffer(
                DanmakuRenderTypes.additiveGlow(FIRE_TONGUE_TEXTURE));
        int planes = GensokyouConfig.FX_FORGE_PILLAR_PLANES.get();
        int segments = GensokyouConfig.FX_FORGE_PILLAR_SEGMENTS.get();
        float pillarHalf = GensokyouConfig.FX_FORGE_PILLAR_WIDTH.get().floatValue();
        float pillarHeight = GensokyouConfig.FX_FORGE_PILLAR_HEIGHT.get().floatValue();
        float pillarScroll = (float) (now * GensokyouConfig.FX_FIRE_SCROLL_SPEED.get() * 1.15D);
        for (int i = 0; i < state.channelCount(); i++) {
            if (!state.forgePedestalBurning(i)) {
                continue;
            }
            BlockPos ped = state.linkAt(i);
            poseStack.pushPose();
            poseStack.translate(ped.getX() - core.getX() + 0.5D, 1.0D,
                    ped.getZ() - core.getZ() + 0.5D);
            // 外层焰：宽而暗
            emitForgePillar(poseStack, flames, planes, segments, now, i,
                    pillarHalf * 1.9F, pillarHeight * 1.15F, pillarScroll, 0.42F * env);
            // 内芯：窄而亮
            emitForgePillar(poseStack, flames, planes, segments, now, i + 7,
                    pillarHalf * 0.85F, pillarHeight, pillarScroll + 0.37F, 0.85F * env);
            poseStack.popPose();
        }
    }

    /**
     * 单根分段火柱：每面沿高 {@code segments} 段 ribbon，逐段收束 + 摆动 + 渐隐。
     *
     * @param intensity 亮度/不透明度系数（外焰暗、内芯亮）
     */
    private void emitForgePillar(PoseStack poseStack, VertexConsumer c, int planes, int segments,
                                 double now, int phase, float halfWidth, float height,
                                 float scroll, float intensity) {
        float ph = phase * 2.3999632F;
        float prevLeft = 0.0F;
        float prevRight = 0.0F;
        float prevY = 0.0F;
        float prevV = 0.0F;
        float prevAlpha = 0.0F;
        for (int plane = 0; plane < planes; plane++) {
            float planePhase = ph + plane * 1.7F;
            prevLeft = 0.0F;
            prevRight = 0.0F;
            prevY = 0.0F;
            prevV = 0.0F;
            prevAlpha = 0.0F;
            for (int s = 0; s <= segments; s++) {
                float t = (float) s / segments;
                float y = t * height;
                float taper = 1.0F - 0.55F * t * t;
                float wobble = 0.80F + 0.20F * Mth.sin(t * 6.5F + planePhase + (float) now * 0.13F);
                float half = halfWidth * taper * wobble;
                float sway = 0.16F * t * Mth.sin(t * 4.2F + planePhase * 1.6F + (float) now * 0.10F);
                float flicker = 0.82F + 0.18F * Mth.sin(t * 9.5F + planePhase + (float) now * 0.21F);
                float alpha = intensity * (1.0F - t) * flicker;
                float v = scroll + t * height;
                float left = sway - half;
                float right = sway + half;
                poseStack.pushPose();
                poseStack.mulPose(Axis.YP.rotationDegrees(plane * 180.0F / planes));
                PoseStack.Pose p = poseStack.last();
                if (s > 0) {
                    FxGeometry.vertex(c, p, prevLeft, prevY, 0.0F, 0.0F, prevV,
                            255, 210, 140, (int) (prevAlpha * 255.0F));
                    FxGeometry.vertex(c, p, prevRight, prevY, 0.0F, 1.0F, prevV,
                            255, 210, 140, (int) (prevAlpha * 255.0F));
                    FxGeometry.vertex(c, p, right, y, 0.0F, 1.0F, v,
                            255, 226, 170, (int) Mth.clamp(alpha * 255.0F, 0.0F, 255.0F));
                    FxGeometry.vertex(c, p, left, y, 0.0F, 0.0F, v,
                            255, 226, 170, (int) Mth.clamp(alpha * 255.0F, 0.0F, 255.0F));
                }
                poseStack.popPose();
                prevLeft = left;
                prevRight = right;
                prevY = y;
                prevV = v;
                prevAlpha = alpha;
            }
        }
    }

    // ================================================================ 灵气场 + 焦点核

    /**
     * 八方归元：<b>灵气场</b>（大片淡雾，高空）+ <b>焦点核</b>（不透明绿球，核心顶面 +1.5）
     * + 每座合格祭品台汇向焦点核的青白激光。
     *
     * <p><b>绘制顺序即深度策略</b>（三者均为不同 RenderType，故 getBuffer 换类型会立即结算
     * 上一批，顺序确定）：
     * <ol>
     *   <li><b>焦点核</b>——画面中唯一写深度的实体，先画。于是其后的祭品台激光在球体轮廓内
     *       被正确剔除（读作"光束打进核里"），而近端那半截仍画在核之上。</li>
     *   <li><b>激光</b>——加法外层，不写深度。</li>
     *   <li><b>灵气场</b>——加法外层，不写深度，最后画，故它包裹住前两者（读作弥漫的雾）。</li>
     * </ol>
     * 反过来（场先、核后）会让场的前半球先写入深度、把核整颗剔掉。
     *
     * <p><b>零新增网络包</b>：祭品台坐标由已同步的 pattern JSON 本地推导（见
     * {@link RitualPedestals#offsets}），台内是否合格由祭品台方块实体已同步的 held 判定
     * （与服务端 {@code BafangGuiyuanBehavior} 同一判据）。
     */
    private void renderOrb(RitualCoreBlockEntity be, RitualRenderState state, double now,
                           PoseStack poseStack, MultiBufferSource buffers) {
        BlockPos core = be.getBlockPos();
        float env = advanceEnvelope(core, 2, state.enabled() ? 1F : 0F, now);
        if (env <= 0F) {
            return;
        }
        Level level = be.getLevel();
        List<BlockPos> pedestals = bafangPedestalOffsets(state.tier());
        // 逐台包络：放上合格核心即淡入亮起，取走即淡出熄灭（非整数硬切）
        float[] beamEnv = bafangBeamEnvelopes(core, pedestals.size());
        float step = lastEnvelopeStep(core);
        int active = 0;
        for (int i = 0; i < pedestals.size(); i++) {
            boolean fed = level != null && pedestalFed(level, core, pedestals.get(i), state.tier());
            beamEnv[i] = Mth.clamp(beamEnv[i] + (fed ? step : -step), 0F, 1F);
            if (beamEnv[i] > 0.02F) {
                active++;
            }
        }
        if (active == 0) {
            // 一台都没有：既无核也无光，只留高空那片场
            this.renderQiField(state, now, env, poseStack, buffers);
            return;
        }

        this.renderFocusCore(state, now, env, active, pedestals.size(), poseStack, buffers);
        this.renderPedestalBeams(core, pedestals, beamEnv, now, poseStack, buffers);
        this.renderQiField(state, now, env, poseStack, buffers);
    }

    /** 焦点核中心的<b>局部</b> Y（相对核心方块原点）：方块顶面以上 {@code fxFocusHeight} 格。 */
    private static float focusLocalY() {
        return (float) (1.0D + GensokyouConfig.FX_FOCUS_HEIGHT.get());
    }

    /** 焦点核半径（含呼吸）。 */
    private static float focusRadius(double now) {
        float radius = GensokyouConfig.FX_FOCUS_RADIUS.get().floatValue();
        double period = Math.max(2.0D, GensokyouConfig.FX_FOCUS_BREATH_PERIOD_TICKS.get());
        float breath = (float) Math.sin(now * Math.PI * 2.0D / period);
        return radius * (1.0F + GensokyouConfig.FX_FOCUS_BREATH_AMP.get().floatValue() * breath);
    }

    /** 有效台数占比 → 核的亮度系数（读作"在充能"）。 */
    private static float focusGlow(int active, int total) {
        float ratio = total <= 0 ? 0F : (float) active / total;
        return 0.45F + 0.55F * ratio;
    }

    private void renderFocusCore(RitualRenderState state, double now, float env,
                                 int active, int total, PoseStack poseStack,
                                 MultiBufferSource buffers) {
        float glow = focusGlow(active, total);
        if (SpiritOrbRenderTypes.densityUniform != null) {
            SpiritOrbRenderTypes.densityUniform.set(
                    GensokyouConfig.FX_FOCUS_DENSITY.get().floatValue());
        }
        poseStack.pushPose();
        poseStack.translate(0.5D, focusLocalY(), 0.5D);
        FxGeometry.emitUnitSphere(buffers.getBuffer(SpiritOrbRenderTypes.CORE), poseStack.last(),
                focusRadius(now), (int) (255 * glow), 255, (int) (255 * glow), (int) (255 * env));
        poseStack.popPose();
    }

    /**
     * 逐台激光：台面中心上方 → 焦点核中心，青白。
     *
     * <p>按 RenderType 分趟提交（见 {@link FxGeometry#emitAlignedBeam} 与
     * {@code MultiBufferSource} 的别名规则）：换 {@code getBuffer} 的类型会立即结算上一批，
     * 故 MUST 先写完全部光晕段再取亮芯缓冲，MUST NOT 把两个 consumer 交叉写。
     */
    private void renderPedestalBeams(BlockPos core, List<BlockPos> pedestals, float[] beamEnv,
                                     double now, PoseStack poseStack, MultiBufferSource buffers) {
        float half = GensokyouConfig.FX_FOCUS_BEAM_WIDTH.get().floatValue();
        float alpha = (int) (255.0F * GensokyouConfig.FX_FOCUS_BEAM_ALPHA.get().floatValue());
        float scroll = (float) (now * 0.45D);
        double sourceY = GensokyouConfig.FX_PEDESTAL_BEAM_SOURCE_HEIGHT.get();
        float targetY = focusLocalY();
        int beamR = BEAM_R, beamG = BEAM_G, beamB = BEAM_B;

        VertexConsumer glowBuf = buffers.getBuffer(DanmakuRenderTypes.additiveGlow(BOLT_GLOW_TEXTURE));
        for (int i = 0; i < pedestals.size(); i++) {
            float e = beamEnv[i];
            if (e <= 0.02F) {
                continue;
            }
            BlockPos p = pedestals.get(i);
            FxGeometry.emitAlignedBeam(poseStack, glowBuf,
                    p.getX() + 0.5F, (float) (p.getY() + sourceY), p.getZ() + 0.5F,
                    0.5F, targetY, 0.5F,
                    half * 2.2F * e, 3, scroll + i * 0.7F,
                    beamR, beamG, beamB, (int) (alpha * 0.45F * e));
        }
        VertexConsumer coreBuf = buffers.getBuffer(DanmakuRenderTypes.additiveGlow(BOLT_CORE_TEXTURE));
        for (int i = 0; i < pedestals.size(); i++) {
            float e = beamEnv[i];
            if (e <= 0.02F) {
                continue;
            }
            BlockPos p = pedestals.get(i);
            FxGeometry.emitAlignedBeam(poseStack, coreBuf,
                    p.getX() + 0.5F, (float) (p.getY() + sourceY), p.getZ() + 0.5F,
                    0.5F, targetY, 0.5F,
                    half * e, 2, scroll + i * 0.7F,
                    beamR, beamG, beamB, (int) (alpha * e));
        }
    }

    /** 灵气场：阶级缩放 + 呼吸 + 轻微浮动；淡到只作背景。 */
    private void renderQiField(RitualRenderState state, double now, float env,
                               PoseStack poseStack, MultiBufferSource buffers) {
        // 单点装配：阶级 → 半径/高度（未来水位表现仅改此处取值源）
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
        int alpha = (int) (255.0F * GensokyouConfig.FX_FIELD_ALPHA.get().floatValue() * env);

        poseStack.pushPose();
        poseStack.translate(0.5D, bob, 0.5D);
        if (SpiritOrbRenderTypes.timeUniform != null) {
            SpiritOrbRenderTypes.timeUniform.set((float) (now % 1000000.0D));
        }
        if (SpiritOrbRenderTypes.fillUniform != null) {
            SpiritOrbRenderTypes.fillUniform.set(GensokyouConfig.FX_FIELD_FILL.get().floatValue());
        }
        FxGeometry.emitUnitSphere(buffers.getBuffer(SpiritOrbRenderTypes.ORB), poseStack.last(),
                scale, 255, 255, 255, alpha);
        poseStack.popPose();
    }

    /**
     * 八方归元该阶的祭品台偏移（相对核心），纯客户端推导。
     *
     * <p>数据源全部是已同步内容：仪式 pattern JSON（{@code RitualDataSyncPayload} →
     * {@code ClientRitualData}）在<b>加载期</b>就完成了四重展开与 {@code (y,z,x)} 规范排序，
     * 故台位集合是 {@code (patternId, tier)} 的纯函数。缓存命中失败时返回空列表。
     */
    private static List<BlockPos> bafangPedestalOffsets(int tier) {
        return BAFANG_PEDESTALS.computeIfAbsent(tier, t -> ClientRitualData
                .pattern(RitualBehaviors.BAFANG_GUIYUAN)
                .map(pattern -> RitualPedestals.offsets(pattern, t))
                .orElse(List.of()));
    }

    private static float[] bafangBeamEnvelopes(BlockPos core, int channels) {
        float[] envs = BAFANG_BEAM_ENVELOPES.get(core);
        if (envs == null || envs.length != channels) {
            float[] rebuilt = new float[channels];
            if (envs != null) {
                System.arraycopy(envs, 0, rebuilt, 0, Math.min(envs.length, channels));
            }
            BAFANG_BEAM_ENVELOPES.put(core, rebuilt);
            envs = rebuilt;
        }
        return envs;
    }

    /**
     * 该祭品台是否放有<b>符合要求</b>的灵力核心。
     *
     * <p>判据与服务端 {@code BafangGuiyuanBehavior.hosted} <b>逐条对应</b>：物品类型谓词
     * + 阶级比较上限。改动服务端判据时 MUST 同步改这里——跨端一致性只能靠代码评审保证，
     * 没有任何编译期或运行期断言能覆盖它。
     *
     * <p>{@code markHeldChanged()} 不广播，但那只在<b>能量数值</b>变动时调用；槽内物品的有无
     * 走 {@code setHeld}/{@code takeHeld}，两者都广播，故本判定不会读到陈旧值。
     */
    private static boolean pedestalFed(Level level, BlockPos core, BlockPos offset, int tier) {
        if (!(level.getBlockEntity(core.offset(offset)) instanceof RitualPedestalBlockEntity pedestal)) {
            return false;
        }
        ItemStack held = pedestal.getHeld();
        return held.getItem() instanceof SpiritCoreItem item && item.tier() <= tier;
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

    /** 献祭光柱色（0=石 1=木 2=土 3=草 4=绵津见水蓝 5=众生余录灵魂紫 6=丰穰神金穗）。 */
    private static int[] pillarColor(int index) {
        return switch (index) {
            case 1 -> new int[]{141, 110, 99};
            case 2 -> new int[]{188, 170, 164};
            case 3 -> new int[]{129, 199, 132};
            case 4 -> new int[]{90, 180, 200};
            case 5 -> new int[]{156, 111, 214};
            case 6 -> new int[]{232, 190, 96};
            default -> new int[]{176, 190, 197};
        };
    }

    // ============================================================ 星移之仪洗练演出

    /**
     * 星移演出（<b>纯客户端本地生成</b>，服务端零持续包）。
     *
     * <p>服务端只下发「演出中 + 档位 + 起始 gameTime + 总时长」四个标量
     * （{@link RitualRenderState#KIND_SEII}），本方法用
     * {@code elapsed = now - startTick} 自行推进动画，因此客户端重新加载区块也能自行
     * 接上正确阶段，不需要补发任何包。
     *
     * <p>三档递进（与原服务端实现逐点对应）：
     * <ol>
     *   <li>底座星盘微光螺旋（一阶即有）</li>
     *   <li>铜环环转（仪式阶 ≥ 3）</li>
     *   <li>天极星光柱 + 顶端天极星（仪式阶 5）</li>
     * </ol>
     */
    private void renderSeii(RitualCoreBlockEntity be, RitualRenderState state, double now,
                            PoseStack poseStack, MultiBufferSource buffers) {
        if (!state.enabled()) {
            return;
        }
        int start = state.seiiStartTick();
        if (start == 0) {
            return;
        }
        int tier = Math.max(1, state.tier());
        int duration = state.seiiDurationTicks();
        double elapsed = now - start;
        // 服务端翻转 enabled 前的那几个 tick 里不再画，避免粒子拖尾
        if (elapsed < 0D || elapsed > duration + 4D) {
            return;
        }
        // 收尾淡出：最后 8 tick 线性收束，避免粒子硬切
        double fade = elapsed >= duration - 8D ? Math.max(0D, (duration - elapsed) / 8D) : 1D;
        if (fade <= 0D) {
            return;
        }
        emitSeiiTick(be, tier, elapsed, fade);
    }

    /**
     * 逐 tick 发射星移演出粒子。
     *
     * <p><b>per-tick 去重守卫</b>：BER 逐帧回调，而 {@code getGameTime()} 每 tick 只 +1，
     * 故同一 tick 内可能被调 2~3 次；不去重会按帧率放大粒子量。用
     * {@link #SEII_LAST_TICK}（键为常驻 BE 的 {@link BlockPos}，弱引用防泄漏）按
     * gameTime 去重，与本文件其他演出同一范式。
     */
    private static void emitSeiiTick(RitualCoreBlockEntity be, int tier, double elapsed, double fade) {
        Level level = be.getLevel();
        if (level == null) {
            return;
        }
        BlockPos core = be.getBlockPos();
        long tick = level.getGameTime();
        Long last = SEII_LAST_TICK.get(core);
        if (last != null && last == tick) {
            return;
        }
        SEII_LAST_TICK.put(core, tick);

        double x = core.getX() + 0.5D;
        double y = core.getY() + 1.0D;
        double z = core.getZ() + 0.5D;
        int glow = GensokyouConfig.FX_SEII_DIAL_GLOW_BASE.get()
                + GensokyouConfig.FX_SEII_DIAL_GLOW_PER_TIER.get() * tier;
        if (glow > 0) {
            for (int i = 0; i < glow; i++) {
                // 微光螺旋：角度随 tick 推进，固定半径 2.2 格
                double ang = elapsed * 0.35D + i * 2.399963D;
                level.addParticle(ParticleTypes.GLOW,
                        x + Math.cos(ang) * 2.2D,
                        y + 0.15D + Math.sin(elapsed * 0.5D + i) * 0.2D,
                        z + Math.sin(ang) * 2.2D,
                        0.0D, 0.01D, 0.0D);
            }
        }
        if (tier >= 3) {
            int nodes = GensokyouConfig.FX_SEII_RING_NODES.get();
            double radius = GensokyouConfig.FX_SEII_RING_RADIUS.get();
            double spin = elapsed * 0.35D;
            for (int i = 0; i < nodes; i++) {
                double ang = spin + i * (Math.PI * 2D / Math.max(1, nodes));
                level.addParticle(ParticleTypes.END_ROD,
                        x + Math.cos(ang) * radius, y + 0.2D, z + Math.sin(ang) * radius,
                        0.0D, 0.02D, 0.0D);
            }
        }
        if (tier >= 5) {
            int height = GensokyouConfig.FX_SEII_PILLAR_HEIGHT.get();
            // (0,0,1..h) 通道自下而上逐段点亮
            for (int h = 1; h <= height; h++) {
                double lit = Math.min(1D, Math.max(0D, (elapsed - h * 2D) / 6D));
                if (lit <= 0D) {
                    continue;
                }
                int count = (int) Math.ceil(2D * lit * fade);
                for (int c = 0; c < count; c++) {
                    level.addParticle(ParticleTypes.END_ROD,
                            x + level.getRandom().nextDouble() * 0.24D - 0.12D,
                            core.getY() + h + 0.5D,
                            z + level.getRandom().nextDouble() * 0.24D - 0.12D,
                            0.0D, 0.01D, 0.0D);
                }
            }
            int star = (int) Math.ceil(6D * fade);
            for (int i = 0; i < star; i++) {
                level.addParticle(ParticleTypes.FLAME,
                        x, core.getY() + height + 1.5D, z,
                        (level.getRandom().nextDouble() - 0.5D) * 0.3D,
                        0.02D,
                        (level.getRandom().nextDouble() - 0.5D) * 0.3D);
            }
        }
    }

    // ============================================================ 无尽藏
    /**
     * 无尽藏运行态：蓝色螺旋雾带（复用 spirit_mist，层数随阶级）+ 3 阶起底座 8 点信标激光。
     * 全部客户端本地绘制，带距离 LOD 降级。
     */
    private void renderWujinzang(RitualCoreBlockEntity be, RitualRenderState state, double now,
                                 PoseStack poseStack, MultiBufferSource buffers) {
        BlockPos core = be.getBlockPos();
        float env = advanceEnvelope(core, 3, state.enabled() ? 1F : 0F, now);
        if (env <= 0F) {
            return;
        }
        boolean lod = lowLod(core);
        double yBottom = state.minY() - core.getY();
        double yTop = Math.max(yBottom + 1.0D, state.maxY() - core.getY() + 1.0D);
        double height = yTop - yBottom;
        int turns = Math.max(2, Mth.ceil(height / 1.6D));
        int segments = Math.min(lod ? 80 : 160, turns * 20);
        int layers = Mth.clamp(1 + state.tier() / 2, 1, GensokyouConfig.FX_WUJINZANG_MIST_LAYERS_MAX.get());
        float scroll = (float) (now * GensokyouConfig.FX_MIST_SCROLL_SPEED.get());
        double radiusBase = GensokyouConfig.FX_WUJINZANG_MIST_RADIUS.get();
        float bandHalf = GensokyouConfig.FX_MIST_BAND_WIDTH.get().floatValue() * 0.5F;
        double wobble = lod ? 0.0D : GensokyouConfig.FX_MIST_WOBBLE.get();
        double rot = now * MIST_ROT_SPEED;
        int r = GensokyouConfig.FX_WUJINZANG_MIST_R.get();
        int g = GensokyouConfig.FX_WUJINZANG_MIST_G.get();
        int b = GensokyouConfig.FX_WUJINZANG_MIST_B.get();

        VertexConsumer glow = buffers.getBuffer(DanmakuRenderTypes.additiveGlow(MIST_TEXTURE));
        for (int layer = 0; layer < layers; layer++) {
            float layerFade = env * (layer == 0 ? 1.0F : 0.6F / layer);
            float halfW = bandHalf * (layer == 0 ? 1.0F : 1.35F);
            double radiusOff = layer * 0.45D;
            double phase = layer * Math.PI * 0.6667D;
            int lr = Math.max(0, r - layer * 40);
            int lg = Math.max(0, g - layer * 30);
            int lb = Math.max(0, b - layer * 20);
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
                            halfW, 2, scroll + (i - 1) * 0.18F, lr, lg, lb, (int) (85F * layerFade));
                }
                px = x;
                py = y;
                pz = z;
            }
        }

        int minTier = GensokyouConfig.FX_WUJINZANG_LASER_MIN_TIER.get();
        if (!lod && state.tier() >= minTier && state.channelCount() > 0) {
            renderWujinzangLasers(poseStack, buffers, core, state, env, now);
        }
    }

    /**
     * 底座 8 点竖直信标激光：米字面片，亮核写深度 + 外晕发光。
     * 锚点为绝对坐标，须减去核心坐标转为 BER 局部坐标（BER pose 已平移到核心方块原点）。
     */
    private void renderWujinzangLasers(PoseStack poseStack, MultiBufferSource buffers,
                                       BlockPos core, RitualRenderState state, float env, double now) {
        float laserH = Math.max(8.0F, GensokyouConfig.FX_WUJINZANG_LASER_HEIGHT.get().floatValue());
        float half = GensokyouConfig.FX_WUJINZANG_LASER_WIDTH.get().floatValue();
        int r = GensokyouConfig.FX_WUJINZANG_MIST_R.get();
        int g = GensokyouConfig.FX_WUJINZANG_MIST_G.get();
        int b = GensokyouConfig.FX_WUJINZANG_MIST_B.get();
        float scroll = (float) (now * 0.6D);
        float pulse = 0.82F + 0.18F * Mth.sin((float) (now * 0.25D));
        VertexConsumer glow = buffers.getBuffer(DanmakuRenderTypes.additiveGlow(BOLT_GLOW_TEXTURE));
        for (int i = 0; i < state.channelCount(); i++) {
            BlockPos anchor = state.linkAt(i);
            poseStack.pushPose();
            poseStack.translate(anchor.getX() - core.getX() + 0.5D,
                    anchor.getY() - core.getY() + 0.5D, anchor.getZ() - core.getZ() + 0.5D);
            FxGeometry.emitCrossPlanes(poseStack, glow, 4, half, laserH,
                    scroll, scroll + laserH, r, g, b, (int) (140F * env * pulse));
            poseStack.popPose();
        }
        VertexConsumer coreBuf = buffers.getBuffer(DanmakuRenderTypes.additiveSolid(BOLT_CORE_TEXTURE));
        for (int i = 0; i < state.channelCount(); i++) {
            BlockPos anchor = state.linkAt(i);
            poseStack.pushPose();
            poseStack.translate(anchor.getX() - core.getX() + 0.5D,
                    anchor.getY() - core.getY() + 0.5D, anchor.getZ() - core.getZ() + 0.5D);
            FxGeometry.emitCrossPlanes(poseStack, coreBuf, 3, half * 0.45F, laserH,
                    scroll * 1.3F, scroll + laserH,
                    (int) Mth.lerp(0.5F, r, 255), (int) Mth.lerp(0.5F, g, 255),
                    (int) Mth.lerp(0.5F, b, 255), (int) (210F * env * pulse));
            poseStack.popPose();
        }
    }

    /** 距离 LOD：超过配置距离则降级（减少雾带段数/摆动、跳过激光）。 */
    private static boolean lowLod(BlockPos core) {
        net.minecraft.client.player.LocalPlayer player =
                net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }
        double limit = GensokyouConfig.FX_WUJINZANG_LOD_DISTANCE.get();
        return player.distanceToSqr(core.getX() + 0.5D, core.getY() + 0.5D, core.getZ() + 0.5D)
                > limit * limit;
    }

    // ================================================================= 公用

    /**
     * 包络推进：slot 0=雾带 1=火柱 2=灵气场 3=无尽藏雾带；按 ramp ticks 线性淡入淡出（跳帧钳 2 tick 步长）。
     *
     * <p>槽位 4 存上次时间、槽位 5 存本帧步长——后者供<b>逐台</b>包络（祭品台激光）复用，
     * 使两者共用同一条时钟，避免出现两套"上一次时间"导致步长不一致。
     */
    private static float advanceEnvelope(BlockPos pos, int slot, float target, double now) {
        float[] st = ENVELOPES.computeIfAbsent(pos, k -> new float[]{0F, 0F, 0F, 0F, -1F, 0F});
        float last = st[4];
        double elapsed = last < 0F ? 0F : Mth.clamp(now - last, 0F, 2.0D);
        st[4] = (float) now;
        float ramp = Math.max(1, GensokyouConfig.FX_RAMP_TICKS.get());
        float step = (float) elapsed / ramp;
        st[5] = step;
        float value = st[slot];
        value = target > value ? Math.min(target, value + step) : Math.max(target, value - step);
        st[slot] = value;
        return value;
    }

    /** 本帧包络步长（由本帧先调用的 {@link #advanceEnvelope} 写入），供逐台包络复用。 */
    private static float lastEnvelopeStep(BlockPos pos) {
        float[] st = ENVELOPES.get(pos);
        return st == null ? 0F : st[5];
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
