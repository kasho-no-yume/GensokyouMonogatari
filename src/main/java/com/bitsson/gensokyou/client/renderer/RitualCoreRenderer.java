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
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
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
import org.joml.Quaternionf;

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
    /** 百鬼夜行：充能球与爆散冲击环共用的径向渐变团（同心分层靠多次缩放叠出平滑衰减）。 */
    private static final ResourceLocation SUMMON_BLOB_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Gensokyou.MODID, "textures/fx/summon_blob.png");
    /** 百鬼夜行：爆散碎片的软边烟团（alpha 混合用）。 */
    private static final ResourceLocation SUMMON_DEBRIS_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Gensokyou.MODID, "textures/fx/summon_debris.png");
    /** 百鬼夜行：降临光柱的竖向渐变（UV 随 gameTime 上滚）。 */
    private static final ResourceLocation SUMMON_PILLAR_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Gensokyou.MODID, "textures/fx/summon_pillar.png");
    /** 灵浴：水面涟漪（U/V 无缝平铺，UV 取世界坐标使相邻格连续，V 方向滚动即"流动"）。 */
    private static final ResourceLocation BATH_WATER_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Gensokyou.MODID, "textures/fx/bath_water.png");

    // ---- 百鬼夜行配色（黑红充能 → 黑红爆散 → 淡金降临）----
    // 刻意不复用 CORE_R/G/B 与 BEAM_R/G/B：那两组是蓝白（结界破碎的调子），语义不同。
    /** 球体本色：暗血红 {@code #8C0A0A}。加法混合下靠外层堆叠到可见亮度。 */
    private static final int SUMMON_R = 140, SUMMON_G = 10, SUMMON_B = 10;
    /** 闪电本体：赤红 {@code #D8232B}。 */
    private static final int SUMMON_BEAM_R = 216, SUMMON_BEAM_G = 35, SUMMON_BEAM_B = 43;
    /** 闪电亮芯：近白粉 {@code #FFD8D0}——亮芯走加法，色相必须由外层承担。 */
    private static final int SUMMON_BEAM_CORE_R = 255, SUMMON_BEAM_CORE_G = 216, SUMMON_BEAM_CORE_B = 208;
    /** 冲击环：赤 {@code #FF3020}。 */
    private static final int SUMMON_RING_R = 255, SUMMON_RING_G = 48, SUMMON_RING_B = 32;
    /** 碎片：暗红棕 {@code #4A0C10}（alpha 混合，故取值远低于加法组）。 */
    private static final int SUMMON_DEBRIS_R = 74, SUMMON_DEBRIS_G = 12, SUMMON_DEBRIS_B = 16;
    /** 光柱外层：淡金 {@code #E8C87A}。 */
    private static final int SUMMON_PILLAR_R = 232, SUMMON_PILLAR_G = 200, SUMMON_PILLAR_B = 122;
    /** 光柱内芯：亮金 {@code #FFF0C0}。 */
    private static final int SUMMON_PILLAR_CORE_R = 255, SUMMON_PILLAR_CORE_G = 240, SUMMON_PILLAR_CORE_B = 192;

    // 注：此处曾有 GOLDEN_ANGLE（斐波那球方位角）供百鬼夜行闪电排布使用。它随
    // summonBeam 的方向采样改成「(柱序, 周期序) 哈希 → 球面均匀采样」而下线——那个常量
    // 配合共享的角度偏移会让整把扇形刚体旋转（读作转盘而非闪电），留着会被下一个人走回去。

    /** 冲击环/碎片的扩散缓动：起步快、收尾慢，读作"炸开"而非"缓缓变大"。 */
    private static float easeOutCubic(float t) {
        float inv = 1.0F - Mth.clamp(t, 0.0F, 1.0F);
        return 1.0F - inv * inv * inv;
    }

    /** camera-facing 面片所需的相机朝向（每帧从 dispatcher 刷新，避免取到已过期的旋转）。 */
    private final Quaternionf camRot = new Quaternionf();

    /**
     * {@code BlockEntityRenderer} 在 1.21.1 是<b>接口</b>而非基类，故 {@code dispatcher}
     * 不会自动可用，必须自己从构造入参里取（与 {@code SukimaPortalRenderer} 同款做法）。
     * camera-facing 面片（球 / 冲击环 / 碎片）全靠它，故不可省。
     */
    private final BlockEntityRenderDispatcher dispatcher;

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
    /**
     * 灵浴水面布局缓存：结构等级 → 该阶底层格位（相对核心）。与 {@link #BAFANG_PEDESTALS} 同理，
     * 是 {@code (patternId, level)} 的纯函数（pattern JSON 加载期已完成四重展开与规范排序）。
     *
     * <p>key 是小整数 boxed，用 ConcurrentHashMap 而非 WeakHashMap：无需也不应回收。
     */
    private static final Map<Integer, RitualFxLayout.BathSurface> REIYOKU_SURFACE =
            new ConcurrentHashMap<>();

    public RitualCoreRenderer(BlockEntityRendererProvider.Context context) {
        this.dispatcher = context.getBlockEntityRenderDispatcher();
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
        // ⚠️ camRot MUST 在**分发之前**每帧设一次。camera-facing billboard（灵浴灵气、
        // 召唤降柱、召唤烟圈）要靠它把面片转向相机；早前它只在 renderOrb / renderSummon
        // **内部**设，于是本 kind 拿到的是上一帧残留值或单位四元数 —— billboard 恒朝 +Z，
        // 斜看时读作"特效没了"，且现象随 BER 渲染顺序变化，极难定位。
        this.camRot.set(dispatcher.camera.rotation());
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
            case RitualRenderState.KIND_SUMMON ->
                    renderSummon(blockEntity, state, now, poseStack, bufferSource);
            case RitualRenderState.KIND_REIYOKU ->
                    renderReiyoku(blockEntity, state, now, poseStack, bufferSource);
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
        if (kind == RitualRenderState.KIND_SUMMON) {
            // 降临光柱冲至世界最高处、半径最大 15 —— MUST 单独开一支。
            // 漏掉的后果是"柱子只到 48 格就断了"：柱顶远在默认的 16 格/48 格包围盒之外，
            // 视锥剔除会把整个 BER 连同高柱一起剔掉。shouldRenderOffScreen=true 不豁免视锥
            // （见本方法 javadoc）。水平半径取爆散环半径与光柱半径的较大者再加余量。
            double pillar = summonPillarRadius(blockEntity.renderState().tier());
            double burst = summonBurstRadius(blockEntity.renderState().tier());
            int worldTop = blockEntity.getLevel() == null
                    ? 320 : blockEntity.getLevel().getMaxBuildHeight();
            r = Math.max(24.0D, Math.max(pillar, burst) + 4.0D);
            up = Math.max(64.0D, worldTop - p.getY() + 1.0D);
        }
        if (kind == RitualRenderState.KIND_REIYOKU) {
            // 水面占地：主池 + 外圈院子（4 阶起 ±11 格，共 332 格），走道不铺水。
            // 水平范围取水面半径与灵气雾团张开半径的较大者再加余量。
            //
            // ⚠️ 垂直方向 MUST 到**世界建筑上限**：灵气升到天上，而渲染态的 maxY 只是结构
            //    最高点（偏移 ≤16）。只按 maxOffset 收盒子的话柱顶远在盒外，视锥剔除会把
            //    整个 BER 连同整柱灵气一起剔掉——现象与"灵气完全不显示"无法区分。
            //    （KIND_SUMMON 的高柱同理，两者都曾因此踩坑。）
            //
            // ⚠️ 渲染态的 minY/maxY 是**绝对世界 Y**（见 RitualCoreBlockEntity.structureMinY），
            //    BER 局部系原点在核心方块，故一律先减去 coreY 换成偏移再用。
            int level1 = blockEntity.renderState().tier();
            // 灵气横截半径已绑到充灵半径（见 renderBathQi），此处按张开后的最大值留余量
            double qi = GensokyouConfig.REIYOKU_BATH_RADIUS.get()
                    * GensokyouConfig.FX_REIYOKU_QI_WIDTH_RATIO.get()
                    * (1.0F + GensokyouConfig.FX_REIYOKU_QI_GROW.get());
            double reach = Math.max(r, Math.max(bathSurface(level1).radiusXZ() + 2.0D, qi + 2.0D));
            double minOffset = blockEntity.renderState().minY() - p.getY();
            double worldTop = blockEntity.getLevel() == null
                    ? 320.0D : blockEntity.getLevel().getMaxBuildHeight();
            double qiTop = worldTop - p.getY();
            double drop = Math.max(16.0D, -minOffset + 2.0D);
            return new AABB(p.getX() - reach, p.getY() - drop, p.getZ() - reach,
                    p.getX() + reach, p.getY() + Math.max(8.0D, qiTop),
                    p.getZ() + reach);
        }
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

    /** 折线顶点：等长分段 + 垂直双向随机抖动，正弦包络两端归零。见 {@link FxGeometry#buildBoltPoints}。 */
    private static float[] buildBoltPoints(float ax, float ay, float az, float bx, float by, float bz,
                                           float segLen, float jitter, int maxSegments, long seed) {
        return FxGeometry.buildBoltPoints(ax, ay, az, bx, by, bz, segLen, jitter, maxSegments, seed);
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

    /**
     * 灵浴该阶的水面布局，纯客户端推导。
     *
     * <p>数据源与 {@link #bafangPedestalOffsets} 同源：已全量下发的 pattern JSON
     * （{@code RitualDataSyncPayload} → {@code ClientRitualData}）。kind 唯一定位 patternId，
     * 故读的是<b>同一份</b> pattern 切片——不是第二套几何语义，也无需任何新增 payload。
     * 占地 = 主池（浴区半径内的未声明格）+ 外圈院子（最低层被结构包围的空块，见
     * {@link RitualFxLayout#bathSurface}），<b>走道不铺水</b>。空布局表示"该阶无水面格位"，
     * 调用方 MUST 视为不绘制。
     */
    private static RitualFxLayout.BathSurface bathSurface(int level) {
        return REIYOKU_SURFACE.computeIfAbsent(level, lv -> ClientRitualData
                .pattern(RitualBehaviors.REIYOKU)
                .map(pattern -> RitualFxLayout.bathSurface(pattern, lv,
                        GensokyouConfig.REIYOKU_BATH_RADIUS.get()))
                .orElseGet(() -> new RitualFxLayout.BathSurface(List.of(), 0)));
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

    // ============================================================ 灵浴

    /**
     * 灵浴运行态：①铺满结构底层的流动蓝色灵力水面 + ②自水面上方升腾至建筑高度上限的浅绿灵气。
     *
     * <p><b>门控只有 {@code enabled}</b>：停机即无特效，运行中即有特效，与缓存有无、
     * 玩家在场与否<b>无关</b>（用户明确要求不引入子状态）。
     *
     * <p>水面基准面 = 底层的<b>顶面</b>（相对核心 Y = {@code (minY - coreY) + 1}，灵浴恒为 0，
     * 即室内地板面）。刻意不是"底面放在 minY 层"——那一层全是实心台基，按字面读法水体
     * 完全埋在方块内、渲染上不可见。自基准面向上取 {@code waterHeight}（默认 0.8 格）作水面，
     * 读作浅水。
     *
     * <p>UV 取<b>世界坐标</b>（未经取模，交给 GL_REPEAT）：相邻格的边界 UV 严格相等，
     * 涟漪跨格连续、平铺接缝不可见；V 方向随 gameTime 滚动即"流动"。
     */
    private void renderReiyoku(RitualCoreBlockEntity be, RitualRenderState state, double now,
                               PoseStack poseStack, MultiBufferSource buffers) {
        float env = advanceEnvelope(be.getBlockPos(), 6, state.enabled() ? 1F : 0F, now);
        if (env <= 0F) {
            return;
        }
        BlockPos core = be.getBlockPos();
        RitualFxLayout.BathSurface surface = bathSurface(state.tier());
        // ⚠️ 渲染态的 minY/maxY 是**绝对世界 Y**，BER 局部系原点在核心方块 → 必须先减 coreY。
        //    （漏这一步会把水面与柱底画到核心上方几十格处，看上去就是"完全没有特效"。）
        //    maxY 此处不再用于灵气（顶面改取世界建筑上限），保留读取以便调试对照。
        float minOffset = state.minY() - core.getY();

        // 呼吸：整片水面同步起伏，幅度极小（读作"水面在动"而非"整块在缩放"）。
        float period = Math.max(1, GensokyouConfig.FX_REIYOKU_WATER_BREATH_PERIOD_TICKS.get());
        float breath = 1.0F + GensokyouConfig.FX_REIYOKU_WATER_BREATH_AMP.get().floatValue()
                * (float) Mth.sin((float) (now * (Math.PI * 2.0D / period)));

        if (!surface.cells().isEmpty()) {
            float waterH = GensokyouConfig.FX_REIYOKU_WATER_HEIGHT.get().floatValue() * breath;
            int r = GensokyouConfig.FX_REIYOKU_WATER_R.get();
            int g = GensokyouConfig.FX_REIYOKU_WATER_G.get();
            int b = GensokyouConfig.FX_REIYOKU_WATER_B.get();
            // ⚠️ 配置项是 0..1 不透明度，vertex alpha 要 0..255 —— 必须先乘 255 再截断。
            //    直接 (int)(0.55f * env) 会截成 0，整片水面全透明（"只有一小块"）。
            float opacity = GensokyouConfig.FX_REIYOKU_WATER_ALPHA.get().floatValue() * env;
            float scroll = (float) (now * GensokyouConfig.FX_REIYOKU_WATER_SCROLL_SPEED.get());
            // 远距降采样：主池 + 4 院子共 328 格，逐帧全画在多人观察时无谓。
            int stride = waterStride(core, surface.cells().size());
            // 底面 Y 由 WaterCell 自带：主池 = minY+1，院子低一格（见 RitualFxLayout.bathSurface）
            emitBathWater(poseStack, buffers, core, surface, stride, waterH,
                    scroll, r, g, b, opacity);
            // 池底辉光：紧贴各自底面的一层暗面，给 0.8 格水深以体积感（不滚动）。
            emitBathWater(poseStack, buffers, core, surface, stride, 0.02F,
                    0F, r, g, b, opacity * 0.35F);
        }

        renderBathQi(be, poseStack, buffers, minOffset, env, now);
    }

    /** 远距 LOD：返回抽样步长（1 = 全画）。 */
    private static int waterStride(BlockPos core, int cells) {
        net.minecraft.client.player.LocalPlayer player =
                net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) {
            return 1;
        }
        double limit = GensokyouConfig.FX_REIYOKU_WATER_LOD_DISTANCE.get();
        double distSq = player.distanceToSqr(core.getX() + 0.5D, core.getY() + 0.5D,
                core.getZ() + 0.5D);
        if (distSq <= limit * limit) {
            return 1;
        }
        double keep = GensokyouConfig.FX_REIYOKU_WATER_LOD_RATIO.get();
        if (keep >= 1.0D || cells <= 0) {
            return 1;
        }
        return Math.max(1, (int) Math.round(1.0D / Math.max(0.01D, keep)));
    }

    /**
     * 铺一层水面：每格一张水平面片，UV 取世界坐标（跨格连续）。
     *
     * <p>底面 Y <b>逐格自带</b>（{@link RitualFxLayout.WaterCell#y()}）：主池坐在最低层顶面，
     * 院子低一格。{@code yLift} 是对<b>各自底面</b>的额外抬升 —— 传水深得水面，传 0.02
     * 得紧贴底面的池底辉光。切勿在这里再加整体基准 Y：{@code cell.y()} 已是完整局部偏移，
     * 叠加 {@code minOffset} 会重复计数，把水面顶到结构上方去。
     *
     * @param opacity 0..1 基础不透明度（调用方须已乘包络）；内部换算到 0..255
     * @param yLift   水面相对各自底面的抬升高度（块）
     */
    private void emitBathWater(PoseStack poseStack, MultiBufferSource buffers, BlockPos core,
                               RitualFxLayout.BathSurface surface, int stride,
                               float yLift, float scroll,
                               int r, int g, int b, float opacity) {
        if (opacity <= 0.001F) {
            return;
        }
        float rim = GensokyouConfig.FX_REIYOKU_WATER_RIM_FADE.get().floatValue();
        VertexConsumer c = buffers.getBuffer(DanmakuRenderTypes.additiveGlow(BATH_WATER_TEXTURE));
        PoseStack.Pose pose = poseStack.last();
        List<RitualFxLayout.WaterCell> cells = surface.cells();
        for (int i = 0; i < cells.size(); i += stride) {
            RitualFxLayout.WaterCell cell = cells.get(i);
            // edge 只标记最外 1~2 圈（见 RitualFxLayout.bathSurface），故这里只在贴边处减光，
            // 池内保持均匀——渐隐读作"水边薄"，不是"水少"。
            int cellAlpha = (int) (255.0F * opacity * (1.0F - cell.edge() * rim));
            if (cellAlpha <= 0) {
                continue;
            }
            // 世界坐标（BER 局部系已平移到核心方块原点）→ UV；GL_REPEAT 负责取模，
            // 保留未取模值才能让相邻格边界 UV 严格相等（涟漪不断缝）。
            float cx = (float) cell.x();
            float cz = (float) cell.z();
            float wx = (float) core.getX() + cx;
            float wz = (float) core.getZ() + cz - scroll;
            float uL = wx - 0.5F, uR = wx + 0.5F;
            float vN = wz - 0.5F, vF = wz + 0.5F;
            float y = (float) cell.y() + yLift;
            FxGeometry.vertex(c, pose, cx - 0.5F, y, cz - 0.5F, uL, vN, r, g, b, cellAlpha);
            FxGeometry.vertex(c, pose, cx + 0.5F, y, cz - 0.5F, uR, vN, r, g, b, cellAlpha);
            FxGeometry.vertex(c, pose, cx + 0.5F, y, cz + 0.5F, uR, vF, r, g, b, cellAlpha);
            FxGeometry.vertex(c, pose, cx - 0.5F, y, cz + 0.5F, uL, vF, r, g, b, cellAlpha);
        }
    }

    /**
     * 浅绿<b>灵气</b>：自浴池水面上方<b>升腾至世界顶部</b>的软雾团柱。
     *
     * <p><b>为什么是堆叠 billboard 而不是多面片棱柱</b>：早先用
     * {@code emitCrossPlanes}（3 面片相交）做柱体，相机斜看时三个面片各自成面，读出来是
     * <b>八角柱</b>而非雾气——面片是硬的，棱是硬的。改为 N 片 <b>camera-facing</b> billboard
     * 沿高度堆叠：每片都正对相机，任意视角都只有一个软边轮廓，叠加后自然读作"一柱上升的雾"。
     * 每片仍走 {@code additiveGlow}（<b>深度测试开启</b>），故屋内主要从屋顶 1×1 烟孔看到，
     * 屋外看到的是从亭顶穿出、继续升到天上的那段。
     *
     * <p><b>片数 MUST 按柱高推导，且间距 MUST 按雾团自身尺寸成比例</b>（这是「一股一股、
     * 像烟囱」的根因）。顶面取世界建筑上限后，核心在 y≈64 时柱高约 <b>256 格</b>。绝对间距
     * 会与雾团尺寸脱钩：半宽 1（2 格高）配 4 格间距，横向只有 2 格、垂直却隔 4 格，必然露缝，
     * 叠起来就是一串独立烟团。故本实现把间距定义为<b>雾团自身高度的比例</b>
     * （{@code 间距 = 雾团高 × FX_REIYOKU_QI_SPACING_RATIO}，默认 0.25 → 每处约 4 片叠加），
     * 雾团变大时间距自动同比变大，光滑度恒定。
     *
     * <p><b>横截面积 MUST 等于充灵半径</b>：柱身取 {@code REIYOKU_BATH_RADIUS ×
     * FX_REIYOKU_QI_WIDTH_RATIO}。绝对半径 1.0（2 格宽）在 6 格净空的亭子里正是一根细管 ——
     * 这才是"烟囱"的另一半成因。
     *
     * <p><b>竖向拉伸把两件事解耦</b>：雾团只拉高、不拉宽（{@code FX_REIYOKU_QI_TALL}，默认 1.8）。
     * 垂直重叠因此显著变密，而横截面仍严格等于充灵半径 —— 否则"要更光滑"只能靠加宽，
     * 那会破坏用户要求的横截面积。
     *
     * <p><b>摆动 MUST 沿高度连续</b>，MUST NOT 按片序号取相位。片数上百时按序号取相
     * 会让相邻片反向摆动，读作杂乱喷溅而非一根连贯的柱；按 {@code u}（0..1 的高度比例）
     * 取相则整柱同相位移动，像一股上升的气。
     *
     * <p>整柱 SHALL 循环上升（{@code (t + 漂移) mod 1}）并在顶端回绕；回绕处 MUST 两端
     * 淡入淡出（{@code edgeFade}），否则高 alpha 的片瞬间跳到柱底会读作"闪一下"。
     *
     * <p><b>单片 alpha MUST 压得很低</b>：片数上百且相互重叠，观感由<b>累积</b>而成，
     * 任何一片都不该自己就看得见。
     *
     * <p>{@link #camRot} MUST 已由 {@code render()} 在分发前设好 —— 早前只在
     * {@code renderOrb} / {@code renderSummon} 内部设，本 kind 拿到的是上一帧残留或单位四元数，
     * billboard 恒朝 +Z，斜看时同样"像消失了一样"。
     */
    private void renderBathQi(RitualCoreBlockEntity be, PoseStack poseStack,
                              MultiBufferSource buffers, float minOffset, float env,
                              double now) {
        // 横截半径绑到充灵半径：MUST NOT 单设绝对值，否则两者迟早漂移
        float radius = (float) (GensokyouConfig.REIYOKU_BATH_RADIUS.get()
                * GensokyouConfig.FX_REIYOKU_QI_WIDTH_RATIO.get());
        if (radius <= 0F) {
            return;
        }
        int r = GensokyouConfig.FX_REIYOKU_QI_R.get();
        int g = GensokyouConfig.FX_REIYOKU_QI_G.get();
        int b = GensokyouConfig.FX_REIYOKU_QI_B.get();
        float opacity = GensokyouConfig.FX_REIYOKU_QI_ALPHA.get().floatValue() * env;
        if (opacity <= 0.001F) {
            return;
        }

        BlockPos core = be.getBlockPos();
        // ⚠️ 底面用水面基准面（最低层顶面），顶面取世界建筑上限。
        float baseY = minOffset + 1.0F;
        float worldTop = (float) (be.getLevel() == null
                ? 320.0D : be.getLevel().getMaxBuildHeight());
        float ratio = Math.max(0.05F,
                GensokyouConfig.FX_REIYOKU_QI_HEIGHT_RATIO.get().floatValue());
        float height = Math.max(1.0F, (worldTop - core.getY() - baseY) * ratio);

        float tall = Mth.clamp(GensokyouConfig.FX_REIYOKU_QI_TALL.get().floatValue(), 0.2F, 8F);
        float grow = Mth.clamp(GensokyouConfig.FX_REIYOKU_QI_GROW.get().floatValue(), 0F, 8F);
        float spacingRatio = Mth.clamp(
                GensokyouConfig.FX_REIYOKU_QI_SPACING_RATIO.get().floatValue(), 0.02F, 1.0F);
        // 间距按"柱底处雾团的高度"成比例：底处最窄，用它定间距才能保证全程重叠不露缝
        float baseSprite = 2.0F * radius * tall;
        float spacing = Math.max(0.05F, baseSprite * spacingRatio);
        int cap = Math.max(1, GensokyouConfig.FX_REIYOKU_QI_MAX_SPRITES.get());
        int sprites = Mth.clamp((int) Math.ceil(height / spacing), 1, cap);
        // 封顶会把实际间距拉大于上面的比例（默认参数下不会触发：256 格 ÷ 2.7 ≈ 95 < 192），
        // 拉大即重叠变少、读作"一股一股"。调小 maxSprites 省帧时要留意这条。

        float topAlphaRatio = Mth.clamp(
                GensokyouConfig.FX_REIYOKU_QI_TOP_ALPHA.get().floatValue(), 0.0F, 1.0F);
        float spread = Mth.clamp(GensokyouConfig.FX_REIYOKU_QI_SPREAD.get().floatValue(), 0.05F, 8F);
        float drift = GensokyouConfig.FX_REIYOKU_QI_DRIFT.get().floatValue();
        float edgeBand = 1.0F / Math.max(2, sprites);
        float pulse = 0.9F + 0.1F * (float) Mth.sin((float) (now * 0.18D));

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.0D, 0.5D);
        VertexConsumer mist = buffers.getBuffer(DanmakuRenderTypes.additiveGlow(MIST_TEXTURE));
        PoseStack.Pose pose = poseStack.last();
        for (int i = 0; i < sprites; i++) {
            // 确定性基础相位：让各片的初值均匀铺满整柱，而不是全挤在起点
            float t = (i + 0.5F) / sprites;
            float u = fract(t + (float) (now * drift / Math.max(1.0F, height)));
            float y = baseY + height * (float) Math.pow(u, spread);
            // 两端淡入淡出，消掉回绕跳变
            float fade = Mth.clamp(u / edgeBand, 0F, 1F)
                    * Mth.clamp((1.0F - u) / edgeBand, 0F, 1F);
            float taper = 1.0F - u * (1.0F - topAlphaRatio);
            int alpha = (int) (255.0F * opacity * pulse * taper * fade);
            if (alpha <= 0) {
                continue;
            }
            float hw = radius * (1.0F + grow * u);
            // 摆动按 u（高度）取相 → 整柱同相位移动，读作一股气；按片序号取相则上百片
            // 各摆各的，读作杂乱喷溅（实机反馈：像烟囱 / 一股一股）
            float wave = (float) Mth.sin((float) (now * 0.09D + u * 4.0D));
            float bob = (float) Mth.cos((float) (now * 0.13D + u * 5.5D));
            float sway = wave * hw * 0.30F;
            FxGeometry.emitBillboard(mist, pose, this.camRot, sway, y, bob * hw * 0.30F,
                    hw, hw * tall, r, g, b, alpha);
        }
        poseStack.popPose();
    }

    /** 0..1 循环相位（负值也能正确回绕）。 */
    private static float fract(float v) {
        float f = v - (float) Math.floor(v);
        return f < 0F ? f + 1F : f;
    }

    // ================================================================= 公用

    /**
     * 包络推进：slot 0=雾带 1=火柱 2=灵气场 3=无尽藏雾带/煅炉 6=灵浴；按 ramp ticks 线性淡入淡出
     * （跳帧钳 2 tick 步长）。<b>slot 4/5 固定保留给时钟</b>（上次时间、步长）——后者供
     * <b>逐台</b>包络（祭品台激光）复用，使两者共用一条时钟，避免出现两套"上一次时间"导致步长不一致。
     * 新增特效 MUST 取 6 或更大的槽位，MUST NOT 覆盖 4/5。
     */
    private static float advanceEnvelope(BlockPos pos, int slot, float target, double now) {
        float[] st = ENVELOPES.computeIfAbsent(pos, k -> new float[]{0F, 0F, 0F, 0F, -1F, 0F, 0F});
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

    // ================================================================= 百鬼夜行召唤演出

    /**
     * 百鬼夜行：黑红充能球 + 径向闪电 → 黑红爆散（环 + 碎片）→ 淡金降临光柱。
     *
     * <p><b>充能段由「相位」驱动，不看时间轴</b>：充能时长完全取决于玩家供灵（可以几十秒，
     * 也可以永远不满），若拿时间当进度，零供灵时整段演出会在启动后一秒内全部播完。
     * 故 {@code CHARGING} 相位无条件播球与闪电，直到服务端落下爆散锚点。
     *
     * <p>爆散与光柱由 {@code gameTime − 爆散锚点} 自算，MUST NOT 逐 tick 从服务端推。
     * 阶段互斥：球只在充能段在场，光柱只在降临段在场。
     */
    private void renderSummon(RitualCoreBlockEntity be, RitualRenderState state, double now,
                              PoseStack poseStack, MultiBufferSource buffers) {
        if (!state.enabled()) {
            return;
        }
        if (state.summonCharging()) {
            this.renderSummonBall(state, now, 0, Integer.MAX_VALUE, poseStack, buffers);
            // 闪电折线的 roll 用 gameTime 而非 elapsed：充能时长无上限，若传 elapsed=0
            // 会把形状**冻结**成一条不动的弧，只剩强度在闪——读作贴图而非闪电。
            this.renderSummonBeams(state, now, (int) now, poseStack, buffers);
            return;
        }
        int burstTicks = state.summonBurstTicks();
        int holdTicks = state.summonPillarHoldTicks();
        int retractTicks = Math.max(1, GensokyouConfig.FX_SUMMON_PILLAR_RETRACT_TICKS.get());
        int elapsed = state.summonElapsed((int) now);
        if (elapsed < burstTicks) {
            // 球在爆散前 3 tick 闪白作预告，故这里仍要画球
            this.renderSummonBall(state, now, elapsed, burstTicks, poseStack, buffers);
            this.renderSummonBurst(state, now, elapsed, burstTicks, poseStack, buffers);
            return;
        }
        int since = elapsed - burstTicks;
        if (since < holdTicks + retractTicks) {
            float hold = (float) since / (float) Math.max(1, holdTicks);
            float fade = 1.0F - Mth.clamp(
                    (float) (since - holdTicks) / (float) retractTicks, 0.0F, 1.0F);
            this.renderSummonPillar(be, state, now, Math.min(1.0F, hold), fade, poseStack, buffers);
        }
    }

    // ---------------------------------------------------------------- 充能球

    /** 该阶的充能球半径（格）。1 阶与结界破碎的最大球同规格，后两阶各为其 2 倍 / 3 倍。 */
    private static double summonBallRadius(int tier) {
        return GensokyouConfig.FX_SUMMON_BALL_RADIUS_BASE.get()
                + GensokyouConfig.FX_SUMMON_BALL_RADIUS_PER_TIER.get() * Math.max(0, tier - 1);
    }

    private static double summonBurstRadius(int tier) {
        return GensokyouConfig.FX_SUMMON_BURST_RADIUS_BASE.get()
                + GensokyouConfig.FX_SUMMON_BURST_RADIUS_PER_TIER.get() * Math.max(0, tier - 1);
    }

    private static double summonPillarRadius(int tier) {
        return GensokyouConfig.FX_SUMMON_PILLAR_RADIUS_BASE.get()
                + GensokyouConfig.FX_SUMMON_PILLAR_RADIUS_PER_TIER.get() * Math.max(0, tier - 1);
    }

    /**
     * 球心的<b>局部</b> Y：核心方块顶面（局部 y=1）之上「半径」格，故球底与核心顶面相切。
     */
    private static float summonBallLocalY(int tier) {
        return (float) (1.0D + summonBallRadius(tier));
    }

    /**
     * 充能球：同心分层 billboard，半径<b>恒定</b>。
     *
     * <p><b>刻意不乘任何进度因子</b>（设计 D5）：需求要"无论供灵是否在流入都一直播"，若让球
     * 随 {@code stored/capacity} 长大，断供时球会停在半途、而进度条也停住，两者叠在一起
     * 读作"卡了"。恒定球 + 独立进度条让"没在动"只有一个归因对象。
     *
     * <p>必须是<b>同心分层</b>而不是"一堆小球"：每片自己带完整径向渐变时，加法叠加只会
     * 让 N 个球心一起更亮，永不合并不成一个球（{@code SukimaPortalRenderer#renderCharge} 的
     * 同款教训）。
     *
     * <p>爆散前 3 tick 全层拉满作"要炸了"的预告，随后<b>随爆散进度一起淡出</b>——
     * 球是"碎"成碎片的来源，两者必须同窗共存，否则读作"球还挂着、碎片在旁边炸"。
     *
     * @param elapsed     演出段已进行 tick（充能段传 0）
     * @param burstTicks  爆散段长度；传 {@link Integer#MAX_VALUE} 表示"尚未进入演出段"，
     *                    此时 {@code elapsed=0} 故预告与淡出均不触发
     */
    private void renderSummonBall(RitualRenderState state, double now, int elapsed, int burstTicks,
                                  PoseStack poseStack, MultiBufferSource buffers) {
        float radius = (float) summonBallRadius(state.tier());
        if (radius <= 1.0E-3F) {
            return;
        }
        int layers = Math.max(2, GensokyouConfig.FX_SUMMON_BALL_LAYERS.get());
        float cy = summonBallLocalY(state.tier());
        float breathAmp = GensokyouConfig.FX_SUMMON_BALL_BREATH_AMP.get().floatValue();
        float glowAmp = GensokyouConfig.FX_SUMMON_BALL_GLOW_AMP.get().floatValue();
        // 闲置呼吸：半径整体小幅起伏。**只随时间，不随充能进度**——进度绑定被明令禁止
        // （断供时球停在半途会与进度条一起读作"卡住"），而闲置呼吸与之无关。
        float scale = 1.0F + breathAmp * (float) Math.sin(now * 1.9D);
        boolean performing = burstTicks != Integer.MAX_VALUE;
        // 预告：爆散前 3 tick 起 alpha 迅速拉满并向白提亮
        float tell = performing
                ? Mth.clamp((float) (elapsed - (burstTicks - 3)) / 3.0F, 0.0F, 1.0F) : 0.0F;
        // 淡出：爆散一开始就把球整体压下去，让碎片接管读感
        float fade = performing ? 1.0F - (float) elapsed / (float) burstTicks : 1.0F;
        fade = Mth.clamp(fade, 0.0F, 1.0F);
        if (fade <= 0.01F) {
            return;
        }
        int cr = tell > 0.0F ? (int) Mth.lerp(tell, SUMMON_R, 255) : SUMMON_R;
        int cg = tell > 0.0F ? (int) Mth.lerp(tell, SUMMON_G, 235) : SUMMON_G;
        int cb = tell > 0.0F ? (int) Mth.lerp(tell, SUMMON_B, 225) : SUMMON_B;

        VertexConsumer glow = buffers.getBuffer(DanmakuRenderTypes.additiveGlow(SUMMON_BLOB_TEXTURE));
        PoseStack.Pose pose = poseStack.last();
        for (int i = 0; i < layers; i++) {
            float f = (float) i / (layers - 1);
            // 每层<b>各自</b>的相位：所有层共用一个 breath 标量时，整颗球是刚体明暗，
            // 形状分毫不动 → 读作一张贴图。加法混合下核心早已饱和，单纯调 alpha 更看不见。
            // 真正让球"活"的是让各层<b>径向错位</b>：层与层分离又重新叠合，亮区位置随之游走。
            float ph = (float) (now * 2.6D + i * 0.85D);
            float wobble = (float) Math.sin(ph);
            float layerScale = (0.26F + 0.80F * f * f) * scale
                    * (1.0F + wobble * 0.045F * (0.35F + 0.65F * f));
            // 亮区游走：外层位移大于内层（内核稳、外壳翻），读作"能量壳绕着核转"
            float drift = radius * 0.055F * (float) Math.sin(now * 1.15D + i * 1.35D) * f;
            float dx = radius * 0.030F * (float) Math.sin(now * 0.83D + i * 0.71D);
            float dz = radius * 0.030F * (float) Math.cos(now * 1.07D + i * 0.53D);
            // 表面细闪：每层每 ~5 tick 一次独立微跳，模拟等离子翻滚
            float shimmer = 1.0F + 0.12F
                    * (unit01(mix32(i * 7919 + (int) (now * 20.0D))) - 0.5F);
            // 名字不叫 glow：会遮蔽同名的 VertexConsumer glow
            float glowMul = (1.0F - glowAmp * 0.5F) + glowAmp * 0.5F * (0.5F + 0.5F * wobble);
            float a = (1.0F - f * 0.90F) * glowMul * shimmer
                    * (1.0F + tell * 1.4F) * fade;
            FxGeometry.emitBillboard(glow, pose, this.camRot, dx, cy, dz,
                    radius * layerScale + drift, radius * layerScale,
                    cr, cg, cb, Mth.clamp((int) (a * 210.0F), 0, 255));
        }
    }

    /**
     * 充能段径向闪电：黑红，宽/长随阶次标量同步放大。
     *
     * <p><b>两趟提交</b>（外晕一遍、亮芯一遍）：{@code getBuffer} 换类型会立即结算上一批，
     * 故 MUST NOT 先把两个 consumer 取出来再交叉写（会抛 "Not building!"）。
     *
     * <p>强度用<b>双频正弦</b>驱动，MUST NOT 用 {@code elapsed % n}：60fps 渲染 20tick/s
     * 的时钟时，那会退化成画一帧空十一帧（barrier-shatter postmortem §10.3）。
     *
     * @param elapsed 折线重掷种子。充能段必须传<b>随时间递增</b>的值（gameTime），
     *                传常量会把弧形冻结；爆散段不再调用本方法。
     */
    /**
     * 充能段径向闪电：黑红，宽/长随阶次标量同步放大。
     *
     * <p><b>两趟提交</b>（外晕一遍、亮芯一遍）：{@code getBuffer} 换类型会立即结算上一批，
     * 故 MUST NOT 先把两个 consumer 取出来再交叉写（会抛 "Not building!"）。两趟折线与
     * 发射段数 MUST 逐位一致，否则亮芯会与外晕错位。
     *
     * <p><b>传播动画</b>：每根柱子只发射折线的<b>前 k 段</b>，k 随该柱年龄从 1 涨到全长，
     * 于是电弧自球心向外"长"出来而不是整条凭空出现。整条到位后保持若干 tick 再被下一周期
     * 的新方向替换。头部最亮、尾部渐隐，是闪电的经典读法。
     *
     * @param nowTick 单调递增的 tick 计数（充能段传 gameTime）。充能时长无上限，
     *                MUST NOT 传 elapsed=0 之类���常量——那会把折线与传播都冻结。
     */
    private void renderSummonBeams(RitualRenderState state, double now, int nowTick,
                                   PoseStack poseStack, MultiBufferSource buffers) {
        int count = GensokyouConfig.FX_SUMMON_BEAM_COUNT.get();
        float radius = (float) summonBallRadius(state.tier());
        if (count <= 0 || radius <= 1.0E-3F) {
            return;
        }
        float cy = summonBallLocalY(state.tier());
        float reach = GensokyouConfig.FX_SUMMON_BEAM_REACH.get().floatValue();
        float jitter = GensokyouConfig.FX_SUMMON_BEAM_JITTER.get().floatValue();
        float scroll = (float) (now * 0.9D);
        int segments = Math.max(4, Math.min(24, (int) (radius * 2.0F)));
        float length = radius * (1.0F + reach);
        int growTicks = Math.max(1, GensokyouConfig.FX_SUMMON_BEAM_GROW_TICKS.get());

        for (int pass = 0; pass < 2; pass++) {
            boolean inner = pass == 1;
            VertexConsumer buf = buffers.getBuffer(inner
                    ? DanmakuRenderTypes.additiveGlow(BOLT_CORE_TEXTURE)
                    : DanmakuRenderTypes.additiveGlow(BOLT_GLOW_TEXTURE));
            for (int i = 0; i < count; i++) {
                Beam beam = summonBeam(i, nowTick, cy, length, segments, jitter, growTicks);
                if (beam.segments() < 1) {
                    continue;
                }
                float flicker = summonBeamFlicker(now, i);
                emitSummonSegments(poseStack, buf, beam.points(), beam.segments(),
                        radius * (inner ? 0.06F : 0.16F) * flicker,
                        scroll + i * 0.7F, flicker, inner ? 215 : 95,
                        inner ? SUMMON_BEAM_CORE_R : SUMMON_BEAM_R,
                        inner ? SUMMON_BEAM_CORE_G : SUMMON_BEAM_G,
                        inner ? SUMMON_BEAM_CORE_B : SUMMON_BEAM_B);
            }
        }
    }

    /** 一次闪电采样的结果：折线 + 本帧该发射多少段（传播进度）。 */
    private record Beam(float[] points, int segments) {
    }

    /**
     * 第 i 根电弧在本帧的形态。
     *
     * <p><b>每根柱一个完整生命循环</b>（周期 {@link #BEAM_PERIODS}）：前
     * {@code growTicks} tick 自球心向外长到全长，其后保持到周期末，再被下一周期的新方向替换。
     * 周期互不相同 + 每根再错开相位，于是既不出现齐射、也不出现整齐的"呼吸"。
     *
     * <p>方向由 {@code (柱序, 周期序)} 哈希决定，<b>整周期内恒定</b>——否则弧会在生长的同时
     * 抖动方向，读作抽搐而不是传导。
     */
    private static Beam summonBeam(int i, int nowTick, float cy, float length, int maxSegments,
                                   float jitter, int growTicks) {
        int period = BEAM_PERIODS[Math.floorMod(i, BEAM_PERIODS.length)];
        // 每柱错开相位：否则所有柱子都在 t=0 起步，一起点就齐射
        int shifted = nowTick + i * 3;
        int cycle = Math.floorDiv(shifted, period);
        int age = Math.floorMod(shifted, period);
        int h = mix32(i * 0x9E3779B9 + cycle * 0x85EBCA6B);
        double y = 1.0D - 2.0D * (0.08D + unit01(h) * 0.84D);
        double r = Math.sqrt(Math.max(0.0D, 1.0D - y * y));
        double theta = unit01(mix32(h)) * 2.0D * Math.PI;
        double len = length * (0.75D + 0.5D * unit01(mix32(h ^ 0x5F356495)));
        float[] pts = FxGeometry.buildBoltPoints(0.0F, cy, 0.0F,
                (float) (Math.cos(theta) * r * len),
                (float) (cy + y * len),
                (float) (Math.sin(theta) * r * len),
                (float) (len / maxSegments), jitter, maxSegments,
                mix32(h ^ 0x27D4EB2F));
        int total = Math.max(1, pts.length / 3 - 1);
        // 传播：头部推进略快于线性（easeOut），模拟"先窜出去再稳住"
        float g = Mth.clamp((float) age / (float) growTicks, 0.0F, 1.0F);
        float eased = 1.0F - (1.0F - g) * (1.0F - g);
        int emit = Mth.clamp((int) Math.ceil(eased * total), 0, total);
        return new Beam(pts, emit);
    }

    /** 各柱生命周期（tick），互不整除；均 MUST 大于 growTicks 以留出保持段。 */
    private static final int[] BEAM_PERIODS = {7, 9, 11, 13};

    /** splitmix32 末两步：把 (柱序, 时隙) 打散成互不相关的 32-bit 值。 */
    private static int mix32(int z) {
        z ^= z >>> 16;
        z *= 0x7FEB352D;
        z ^= z >>> 15;
        z *= 0x846CA68B;
        z ^= z >>> 16;
        return z;
    }

    /** 取 [0,1) 浮点。取高 24 位避开低位偏斜。 */
    private static float unit01(int h) {
        return (h >>> 8) * (1.0F / 16777216.0F);
    }

    /**
     * 闪电强度：两路异频正弦 + 每柱独立相位，连续、与帧率无关。
     *
     * <p>额外做了<b>锐化</b>（三次方）：正弦本身有相当比例的时间停在中高位，读作"持续发光"
     * 而非"放电"。三次方把大部分时间压到接近 0，只留少数尖峰，读作"噼啪炸一下"。
     */
    private static float summonBeamFlicker(double now, int i) {
        float base = 0.55F + 0.45F * (Mth.sin((float) (now * 7.3D + i * 1.7D)) * 0.6F
                + Mth.sin((float) (now * 17.1D + i * 4.3D)) * 0.4F);
        float sharp = base * base * base;
        return Mth.clamp(0.15F + 0.85F * sharp, 0.0F, 1.0F);
    }

    /**
     * 把折线的<b>前 {@code emit} 段</b>逐段发射成米字面片。
     *
     * <p><b>头亮尾暗</b>：alpha 沿段序从尾部 {@code TAIL_ALPHA} 递增到头部满值。传播动画里
     * 头部是"正在窜出去的那一端"，让它最亮才读得出方向；整条等亮则像一根发光的管子。
     *
     * @param emit 本帧发射的段数（= 传播进度），MUST NOT 超过折线总段数
     */
    private static void emitSummonSegments(PoseStack poseStack, VertexConsumer consumer, float[] pts,
                                           int emit, float halfWidth, float v0, float flicker,
                                           int maxAlpha, int r, int g, int b) {
        int total = pts.length / 3 - 1;
        if (total <= 0) {
            return;
        }
        int n = Math.min(emit, total);
        for (int s = 0; s < n; s++) {
            int i0 = s * 3;
            int i1 = i0 + 3;
            // 头部在最后一段，故 alpha 随 s 递增
            float ramp = n <= 1 ? 1.0F : (float) s / (float) (n - 1);
            float a = Mth.lerp(TAIL_ALPHA, 1.0F, ramp * ramp) * flicker;
            FxGeometry.emitAlignedBeam(poseStack, consumer,
                    pts[i0], pts[i0 + 1], pts[i0 + 2], pts[i1], pts[i1 + 1], pts[i1 + 2],
                    halfWidth, 3, v0 + s * 0.7F, r, g, b, (int) (maxAlpha * a));
        }
    }

    /** 电弧尾部 alpha 系数（头部为 1.0）。 */
    private static final float TAIL_ALPHA = 0.30F;

    // ---------------------------------------------------------------- 爆散

    /**
     * 爆散段：<b>同时</b>出冲击环与碎片，两者都从 0 扩到该阶半径并在 1 秒内淡出。
     *
     * <p><b>冲击环贴在核心顶面高度（局部 y=1），刻意不跟球心</b>：球悬在 {@code 1+r} 高处，
     * 环若跟随球心就成了"半空中无缘无故震出一圈"；贴地才读作"头顶炸开、脚底震出一圈"。
     *
     * <p><b>碎片走 alpha 混合</b>（{@code translucent}），而冲击环走加法：加法只能加亮，
     * 无论贴图多灰都读作"白色光球"，永远读不出"碎"的质感。
     */
    private void renderSummonBurst(RitualRenderState state, double now, int since, int burstTicks,
                                   PoseStack poseStack, MultiBufferSource buffers) {
        float t = Mth.clamp((float) since / (float) Math.max(1, burstTicks), 0.0F, 1.0F);
        float radius = (float) (summonBurstRadius(state.tier()) * easeOutCubic(t));
        if (radius <= 1.0E-3F) {
            return;
        }

        // ---- 冲击环：贴核心顶面、加法、一闪即逝 ----
        int puffs = GensokyouConfig.FX_SUMMON_BURST_RING_PUFFS.get();
        float ringAlpha = (1.0F - t) * (1.0F - t) * 220.0F;
        if (ringAlpha >= 3.0F) {
            VertexConsumer glow = buffers.getBuffer(DanmakuRenderTypes.additiveGlow(SUMMON_BLOB_TEXTURE));
            PoseStack.Pose pose = poseStack.last();
            float half = Math.max(0.10F, radius * 0.05F);
            for (int i = 0; i < puffs; i++) {
                double a = i * 2.0D * Math.PI / puffs;
                FxGeometry.emitBillboard(glow, pose, this.camRot,
                        (float) (Math.cos(a) * radius), 1.0F, (float) (Math.sin(a) * radius),
                        half, half, SUMMON_RING_R, SUMMON_RING_G, SUMMON_RING_B, (int) ringAlpha);
            }
        }

        // ---- 碎片：自球心四散，alpha 混合（读作"碎"而非"光"）----
        int layers = Math.max(1, GensokyouConfig.FX_SUMMON_BURST_DEBRIS_LAYERS.get());
        int perLayer = GensokyouConfig.FX_SUMMON_BURST_DEBRIS_PER_LAYER.get();
        float debrisAlpha = (1.0F - t) * (1.0F - t) * 200.0F;
        if (debrisAlpha < 3.0F) {
            return;
        }
        float cy = summonBallLocalY(state.tier());
        VertexConsumer smoke = buffers.getBuffer(
                DanmakuRenderTypes.translucent(SUMMON_DEBRIS_TEXTURE));
        PoseStack.Pose pose = poseStack.last();
        for (int layer = 0; layer < layers; layer++) {
            float lf = layers <= 1 ? 0F : (float) layer / (layers - 1);
            float lr = radius * (0.30F + 0.70F * lf);
            float lhalf = Math.max(0.08F, lr * 0.10F);
            for (int i = 0; i < perLayer; i++) {
                double seed = now * 0.31D + i * 0.618D + layer * 1.37D;
                double yy = 1.0D - 2.0D * ((i + 0.5D) / perLayer);
                double ring = Math.sqrt(Math.max(0.0D, 1.0D - yy * yy));
                double theta = seed * 2.0D * Math.PI;
                FxGeometry.emitBillboard(smoke, pose, this.camRot,
                        (float) (Math.cos(theta) * ring * lr),
                        (float) (cy + yy * lr * 0.55D),
                        (float) (Math.sin(theta) * ring * lr),
                        lhalf, lhalf, SUMMON_DEBRIS_R, SUMMON_DEBRIS_G, SUMMON_DEBRIS_B,
                        (int) (debrisAlpha * (1.0F - lf * 0.5F)));
            }
        }
    }

    // ---------------------------------------------------------------- 降临光柱

    /**
     * 降临光柱：淡金、非常粗，自核心顶面冲至世界最高处。
     *
     * <p><b>alpha 混合</b>而非加法：柱体半径 5~15 格，玩家站进去时加法必然过曝成一片白。
     * 用 alpha 混合 + 沿柱轴的 alpha 梯度，视线穿过柱身时仍能看清后方。
     *
     * <p><b>不随观察距离淡出</b>（需求）：alpha 只由 {@code grow}（升起）与 {@code fade}
     * （收束）两个时间因子决定，MUST NOT 引入任何距离项。
     */
    private void renderSummonPillar(RitualCoreBlockEntity be, RitualRenderState state, double now,
                                    float grow, float fade,
                                    PoseStack poseStack, MultiBufferSource buffers) {
        double radius = summonPillarRadius(state.tier());
        if (radius <= 1.0E-3D || grow <= 0.0F || fade <= 0.0F) {
            return;
        }
        Level level = be.getLevel();
        double top = level == null ? 320.0D : level.getMaxBuildHeight();
        // 局部坐标：柱底 = 核心顶面（局部 y=1），柱顶按世界高度折算成相对核心的局部量
        float topLocal = (float) (top - be.getBlockPos().getY());
        float height = topLocal - 1.0F;
        if (height <= 1.0F) {
            return;
        }
        float alphaMin = GensokyouConfig.FX_SUMMON_PILLAR_ALPHA_MIN.get().floatValue();
        float alphaMax = GensokyouConfig.FX_SUMMON_PILLAR_ALPHA_MAX.get().floatValue();
        int alpha = (int) (255.0F * Mth.lerp(alphaMin, alphaMax, Mth.clamp(grow, 0.0F, 1.0F)) * fade);
        if (alpha <= 2) {
            return;
        }
        float scroll = (float) (now * 0.5D);
        // 12 面棱柱（正多边形近似圆），侧壁两趟：外层柔边 + 内芯亮柱
        int sides = 12;
        for (int pass = 0; pass < 2; pass++) {
            boolean inner = pass == 1;
            float rr = (float) (radius * (inner ? 0.55D : 1.0D));
            int a = inner ? (int) (alpha * 0.85F) : alpha;
            if (a <= 2) {
                continue;
            }
            VertexConsumer c = buffers.getBuffer(
                    inner ? DanmakuRenderTypes.additiveGlow(SUMMON_PILLAR_TEXTURE)
                            : DanmakuRenderTypes.translucent(SUMMON_PILLAR_TEXTURE));
            int cr = inner ? SUMMON_PILLAR_CORE_R : SUMMON_PILLAR_R;
            int cg = inner ? SUMMON_PILLAR_CORE_G : SUMMON_PILLAR_G;
            int cb = inner ? SUMMON_PILLAR_CORE_B : SUMMON_PILLAR_B;
            PoseStack.Pose pose = poseStack.last();
            for (int s = 0; s < sides; s++) {
                double a0 = s * 2.0D * Math.PI / sides;
                double a1 = (s + 1) * 2.0D * Math.PI / sides;
                float x0 = (float) (Math.cos(a0) * rr);
                float z0 = (float) (Math.sin(a0) * rr);
                float x1 = (float) (Math.cos(a1) * rr);
                float z1 = (float) (Math.sin(a1) * rr);
                float y0 = 1.0F;
                float y1 = topLocal;
                float v0 = scroll;
                float v1 = scroll + height * 0.08F;
                // 侧壁两片一四边形：底边左右两角 + 顶边左右两角，逆时针保证正面朝外。
                FxGeometry.vertex(c, pose, x0, y0, z0, 0.0F, v0, cr, cg, cb, a);
                FxGeometry.vertex(c, pose, x1, y0, z1, 1.0F, v0, cr, cg, cb, a);
                FxGeometry.vertex(c, pose, x1, y1, z1, 1.0F, v1, cr, cg, cb, a);
                FxGeometry.vertex(c, pose, x0, y0, z0, 0.0F, v0, cr, cg, cb, a);
                FxGeometry.vertex(c, pose, x1, y1, z1, 1.0F, v1, cr, cg, cb, a);
                FxGeometry.vertex(c, pose, x0, y1, z0, 0.0F, v1, cr, cg, cb, a);
            }
        }
    }
}
