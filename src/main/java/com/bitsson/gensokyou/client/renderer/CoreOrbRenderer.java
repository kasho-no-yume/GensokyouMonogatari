package com.bitsson.gensokyou.client.renderer;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.item.weapon.BulletCoreItem;
import com.bitsson.gensokyou.item.weapon.CoreShellProfile;
import com.bitsson.gensokyou.item.weapon.WeaponSlots;
import com.bitsson.gensokyou.item.weapon.WeaponSlotsHelper;
import com.bitsson.gensokyou.registry.ModDataComponents;
import com.bitsson.gensokyou.registry.TierPalette;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 千机核心（弹幕主武器）的物品渲染器。
 *
 * <p><b>两种互斥形态</b>（由 slot1 是否有核决定）：
 * <ol>
 *   <li><b>沉睡态</b>（未装核）——全彩流光球 + 火冠气焰 + 球心暗腔。它<b>没有品阶归属</b>
 *       （品阶随装入的核而来），故走全光谱流光，不受品阶配色环约束。</li>
 *   <li><b>显形态</b>（已装核）——外壳几何<b>即该核对应的武器本身</b>（三把飞刀 / 激光枪 / 符纸…），
 *       基色取该核的品阶色（{@link TierPalette}，品阶色单一色源）。</li>
 * </ol>
 *
 * <p><b>层次（由内到外）</b>
 * <ol>
 *   <li><b>本体</b>：alpha 混合，持有品阶色（显形态）或全光谱（沉睡态）；</li>
 *   <li><b>腔体</b>：沉睡态球心的暗色涡旋（正对相机）；</li>
 *   <li><b>外晕</b>：放大一圈的加法辉光（品阶色的互补色）；</li>
 *   <li><b>能量</b>：武器自带的拖尾 / 能量线 / 符文流 / 光轴；</li>
 *   <li><b>火冠</b>：仅沉睡态的火焰舌（暖色，根部取局部流光色）；</li>
 *   <li><b>环绕环</b>：1~3 圈反向旋转的细环，<b>圈数 = 武器等级</b>（槽 2 的可视化）。</li>
 * </ol>
 *
 * <p><b>配色纪律</b>：全部色彩口径与<b>层预算</b>见 {@link CoreOrbPalette}。
 * 加法层贡献之和 MUST ≤ {@link CoreOrbPalette#ADDITIVE_BUDGET_TOTAL}，
 * 本体层峰值 MUST ≤ {@link CoreOrbPalette#BODY_PEAK_BRI} —— 二者相加 &lt; 255，
 * <b>结构上</b>不会发生通道截顶（上一版"整体发白"正是截顶造成的）。
 *
 * <p><b>几何在 {@link CoreOrbGeometry}</b>：本类只负责姿态、着色与提交；
 * 几何单独成类是为了让轮廓形状能被离线预览与断言（见 {@code CoreOrbPreviewTest}）。
 *
 * <p><b>纯客户端表现</b>：全部相位由本地单调时钟推导，MUST NOT 参与任何玩法判定，
 * MUST NOT 引入网络包。
 */
public class CoreOrbRenderer extends net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer {

    /** NeoForge 的自定义物品渲染器走 {@code BlockEntityWithoutLevelRenderer} 这一支
     *  （{@code IClientItemExtensions#getCustomRenderer()} 的返回类型）。 */
    public CoreOrbRenderer(net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher dispatcher,
                           net.minecraft.client.model.geom.EntityModelSet modelSet) {
        super(dispatcher, modelSet);
    }

    private static final ResourceLocation SHELL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Gensokyou.MODID, "textures/item/core_orb_shell.png");

    /** 自发光层满亮度。 */
    private static final int FULL_BRIGHT = 0xF000F0;

    // ---------------- 姿态 ----------------

    /** 模型长轴转到水平（尖端朝侧面，而不是戳向相机）。 */
    private static final float MODEL_YAW = 90.0F;
    /** 轻微俯仰，给一点立体感。 */
    private static final float MODEL_PITCH = 16.0F;

    /** 呼吸缩放幅度与频率。 */
    private static final float BREATHE_AMPLITUDE = 0.03F;
    private static final float BREATHE_SPEED = 1.6F;

    /** 外壳整体自转（度/秒）。 */
    private static final float SHELL_SPIN_DEG_PER_SEC = 24.0F;

    /** 外晕放大倍率。 */
    private static final float HALO_SCALE = 1.12F;

    /** 沉睡态本体透明度（半透明，让气焰与腔体透出层次）。 */
    private static final int DREAM_BODY_ALPHA = 225;

    /** 武器轮廓本体透明度（实心——武器就该是实的）。 */
    private static final int WEAPON_BODY_ALPHA = 255;

    /** 腔体透明度。 */
    private static final int CAVITY_ALPHA = 240;

    /** 外晕 / 环绕环的目标透明度（最终仍会被预算压住）。 */
    private static final int GLOW_ALPHA = 255;

    /** 武器自带能量件的透明度（实体层，不占加法预算）。 */
    private static final int ENERGY_ALPHA = 235;

    // ---------------- 主渲染 ----------------

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose,
                              MultiBufferSource bufferSource, int combinedLight, int combinedOverlay) {
        WeaponSlots slots = stack.getOrDefault(ModDataComponents.WEAPON_SLOTS.get(), WeaponSlots.DEFAULT);
        ItemStack coreStack = slots.slot1();
        BulletCoreItem core = coreStack.getItem() instanceof BulletCoreItem bullet ? bullet : null;
        CoreShellProfile profile = core != null ? core.shell() : CoreShellProfile.DREAM;
        boolean dormant = core == null;
        int tier = core != null ? core.stats().requiredTier().getAsInt() : 0;
        int baseColor = dormant ? 0 : TierPalette.rgb(tier);
        int level = WeaponSlotsHelper.weaponLevel(stack);

        float time = localTime();
        CoreOrbPalette.Palette pal = dormant ? CoreOrbPalette.Palette.dormant() : CoreOrbPalette.Palette.tier(baseColor);

        pose.pushPose();
        // 物品模型空间的原点落在槽位的**左下角**：ItemRenderer 在调用自定义渲染器前会做
        // `pose.translate(-0.5, -0.5, -0.5)`，而物品模型的几何是从原点向 +X/+Y 铺开的
        // （`item/generated` 的贴图也是 0..1 方向）。故几何若以原点为中心构建，
        // 就会贴到格子的左下角——必须先平移半格回到中心。
        pose.translate(0.5D, 0.5D, 0.0D);
        float breathe = 1.0F + BREATHE_AMPLITUDE * Mth.sin(time * BREATHE_SPEED);
        pose.scale(breathe, breathe, breathe);

        List<CoreOrbGeometry.Quad> silhouette = dormant
                ? CoreOrbGeometry.DREAM_BODY
                : CoreOrbGeometry.body(profile);

        // ① 本体：alpha 混合，持有品阶色 / 全光谱
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(MODEL_YAW));
        pose.mulPose(Axis.XP.rotationDegrees(MODEL_PITCH));
        pose.mulPose(Axis.ZP.rotationDegrees(time * SHELL_SPIN_DEG_PER_SEC));
        this.emitTo(RenderType.entityTranslucent(SHELL_TEXTURE), silhouette, pose, pal,
                CoreOrbPalette.Layer.BODY, time, 1.0F,
                dormant ? DREAM_BODY_ALPHA : WEAPON_BODY_ALPHA, bufferSource);
        pose.popPose();

        // ② 腔体：沉睡态球心的暗色涡旋，正对相机（须在本体之后画，才能压出"洞"）
        if (dormant) {
            pose.pushPose();
            pose.mulPose(Axis.XP.rotationDegrees(-MODEL_PITCH));
            pose.mulPose(Axis.YP.rotationDegrees(-MODEL_YAW));
            pose.mulPose(Axis.ZP.rotationDegrees(-time * 26.0F));
            this.emitTo(RenderType.entityTranslucent(SHELL_TEXTURE), CoreOrbGeometry.DREAM_CAVITY,
                    pose, pal, CoreOrbPalette.Layer.BODY, time, 0.35F, CAVITY_ALPHA, bufferSource);
            pose.popPose();
        }

        // ③ 外晕：轮廓外一圈加法辉光（品阶色的互补色），绕包围盒中心放大
        Vec3 center = dormant ? Vec3.ZERO : CoreOrbGeometry.center(profile);
        pose.pushPose();
        pose.translate(center.x, center.y, center.z);
        pose.scale(HALO_SCALE, HALO_SCALE, HALO_SCALE);
        pose.translate(-center.x, -center.y, -center.z);
        this.emitTo(DanmakuRenderTypes.additiveGlow(SHELL_TEXTURE), silhouette, pose, pal,
                CoreOrbPalette.Layer.HALO, time, 1.0F, GLOW_ALPHA, bufferSource);
        pose.popPose();

        // ④ 能量：武器自带的拖尾 / 能量线 / 符文流 / 光轴
        if (!dormant) {
            List<CoreOrbGeometry.Quad> energy = CoreOrbGeometry.energy(profile);
            if (!energy.isEmpty()) {
                pose.pushPose();
                pose.mulPose(Axis.YP.rotationDegrees(MODEL_YAW));
                pose.mulPose(Axis.XP.rotationDegrees(MODEL_PITCH));
                pose.mulPose(Axis.ZP.rotationDegrees(time * 14.0F));
                this.emitTo(RenderType.entityTranslucent(SHELL_TEXTURE), energy, pose, pal,
                        CoreOrbPalette.Layer.ENERGY, time, 1.0F, ENERGY_ALPHA, bufferSource);
                pose.popPose();
            }
        }

        // ⑤ 火冠：仅沉睡态
        if (dormant) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(MODEL_YAW));
            pose.mulPose(Axis.XP.rotationDegrees(MODEL_PITCH));
            this.emitFlameTo(RenderType.entityTranslucent(SHELL_TEXTURE),
                    CoreOrbGeometry.flame(time), pose, time, bufferSource);
            pose.popPose();
        }

        // ⑥ 环绕环：圈数 = 武器等级，反向旋转
        if (level > 0) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(MODEL_YAW));
            pose.mulPose(Axis.XP.rotationDegrees(MODEL_PITCH));
            pose.mulPose(Axis.YP.rotationDegrees(-time * 46.0F));
            // 同类型连续取缓冲：一次取够，几圈共用（期间没有别的 getBuffer 调用，缓冲不会被顶掉）
            VertexConsumer rings = bufferSource.getBuffer(DanmakuRenderTypes.additiveGlow(SHELL_TEXTURE));
            for (int i = 0; i < level; i++) {
                float tilt = 22.0F * i;
                pose.pushPose();
                pose.mulPose(Axis.XP.rotationDegrees(tilt));
                pose.mulPose(Axis.ZP.rotationDegrees(tilt * 0.6F));
                this.emit(CoreOrbGeometry.ring(i), rings, pose, pal, CoreOrbPalette.Layer.RING,
                        time, 0.6F, GLOW_ALPHA);
                pose.popPose();
            }
            pose.popPose();
        }

        pose.popPose();
    }

    /** 本地单调时钟（秒）。纯视觉用——MUST NOT 依赖服务端同步。 */
    private static float localTime() {
        // 注意：浮点字面量不能带 L 后缀（`0.0L` 非法——L 只属于整数字面量），
        // 分母写成 `1_000_000_000.0`（double）而不是 `1_000_000_000.0L`。
        return (float) ((System.nanoTime() % 100_000_000_000L) / 1_000_000_000.0);
    }

    // ---------------- 绘制 ----------------

    /**
     * 取缓冲并<b>立即</b>写入一组面片。
     *
     * <p><b>MUST NOT 把 {@code VertexConsumer} 存起来跨 {@code getBuffer} 调用使用。</b>
     * 1.21.1 的 {@code BufferSource.getBuffer} 只有在某个 RenderType <b>首次</b>占用共享缓冲时
     * 才会把上一个占用者 {@code endBatch} 掉（{@code BufferBuilder.building = false}）；
     * 此后往那个已被结束的 consumer 写顶点就会抛
     * {@code IllegalStateException: Not building!}。
     *
     * <p>换句话说：<b>取缓冲与写入 MUST 绑在一起</b>（取 → 写 → 取 → 写）。
     * 本方法把这个约束做成结构性的——调用方拿不到 {@code VertexConsumer}，
     * 也就无从把它跨 {@code getBuffer} 传下去。
     *
     * <p>（回归测试见 {@code CoreOrbBufferContractTest}。）
     */
    private void emitTo(RenderType type, List<CoreOrbGeometry.Quad> quads, PoseStack pose,
                        CoreOrbPalette.Palette pal, CoreOrbPalette.Layer layer, float time,
                        float flowScale, int desiredAlpha, MultiBufferSource bufferSource) {
        this.emit(quads, bufferSource.getBuffer(type), pose, pal, layer, time, flowScale, desiredAlpha);
    }

    /**
     * 输出一组面片。
     *
     * @param layer      该层（决定混合方式、预算与色相槽）
     * @param flowScale  光带对比度缩放（0 = 不做明暗调制）
     */
    private void emit(List<CoreOrbGeometry.Quad> quads, VertexConsumer consumer, PoseStack poseStack,
                      CoreOrbPalette.Palette pal, CoreOrbPalette.Layer layer, float time, float flowScale,
                      int desiredAlpha) {
        if (quads.isEmpty()) {
            return;
        }
        PoseStack.Pose pose = poseStack.last();
        for (CoreOrbGeometry.Quad q : quads) {
            // 逐顶点着色：色相与亮度带在面上连续，才读作"能量在流"
            int ca = CoreOrbPalette.shade(layer, pal, q.a(), q.face(), q.fixed(), time, flowScale);
            int cb = CoreOrbPalette.shade(layer, pal, q.b(), q.face(), q.fixed(), time, flowScale);
            int cc = CoreOrbPalette.shade(layer, pal, q.c(), q.face(), q.fixed(), time, flowScale);
            int cd = CoreOrbPalette.shade(layer, pal, q.d(), q.face(), q.fixed(), time, flowScale);
            if (layer.isAdditive()) {
                // 加法层：把颜色压进该层的预算——预算不是约定，是被算出来的。
                int budget = layer.budget();
                int aa = CoreOrbPalette.alphaWithin(ca, budget, desiredAlpha);
                int ab = CoreOrbPalette.alphaWithin(cb, budget, desiredAlpha);
                int ac = CoreOrbPalette.alphaWithin(cc, budget, desiredAlpha);
                int ad = CoreOrbPalette.alphaWithin(cd, budget, desiredAlpha);
                this.vertex(consumer, pose, q.a(), ca, aa);
                this.vertex(consumer, pose, q.b(), cb, ab);
                this.vertex(consumer, pose, q.c(), cc, ac);
                this.vertex(consumer, pose, q.d(), cd, ad);
            } else {
                this.vertex(consumer, pose, q.a(), ca, desiredAlpha);
                this.vertex(consumer, pose, q.b(), cb, desiredAlpha);
                this.vertex(consumer, pose, q.c(), cc, desiredAlpha);
                this.vertex(consumer, pose, q.d(), cd, desiredAlpha);
            }
        }
    }

    /**
     * 计算一个面片的颜色（转发到 {@link CoreOrbPalette#shade}）。
     *
     * <p>着色口径放在 {@code CoreOrbPalette} 里，是为了让它<b>不依赖客户端类</b>——
     * 离线预览与断言因此可以在没有 GL 上下文、也没有原版注册表引导的环境里跑。
     */
    static int shade(CoreOrbPalette.Layer layer, CoreOrbPalette.Palette pal, CoreOrbGeometry.Quad q,
                     float time, float flowScale) {
        return CoreOrbPalette.shade(layer, pal, q, time, flowScale);
    }

    /** 全光谱流光的色相：沿流动坐标滚动 + 整体缓慢摆动。 */
    private static float rainbowHue(float s, float time) {
        return CoreOrbPalette.rainbowHue(s, time);
    }

    /**
     * 输出一个顶点。
     *
     * <p>亮度调制<b>不在这里做</b>——它已经在 {@link #shade} 里于 HSV 侧完成。
     * 旧实现在这里做 RGB 乘法 + 逐通道截顶，是发白的直接成因。
     */
    private void vertex(VertexConsumer consumer, PoseStack.Pose pose, CoreOrbGeometry.Vtx v,
                        int color, int alpha) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        consumer.addVertex(pose.pose(), (float) v.p().x, (float) v.p().y, (float) v.p().z)
                .setColor(r, g, b, alpha)
                .setUv(v.u(), v.v())
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    /**
     * 火冠着色：根部取该舌下方的流光色，尖端过渡到暖白。
     *
     * <p>根部色由火舌序号推出的方位角决定——与球面上的流光色同源，故气焰与球体永远同色系。
     */
    private void emitFlameTo(RenderType type, List<CoreOrbGeometry.Quad> quads, PoseStack pose,
                               float time, MultiBufferSource bufferSource) {
        this.emitFlame(quads, bufferSource.getBuffer(type), pose, time);
    }

    /**
     * 火冠着色：根部取该舌下方的流光色，尖端过渡到暖白。
     *
     * <p>根部色由火舌序号推出的方位角决定——与球面上的流光色同源，故气焰与球体永远同色系。
     */
    private void emitFlame(List<CoreOrbGeometry.Quad> quads, VertexConsumer consumer, PoseStack poseStack,
                           float time) {
        if (quads.isEmpty()) {
            return;
        }
        PoseStack.Pose pose = poseStack.last();
        for (CoreOrbGeometry.Quad q : quads) {
            int tongue = q.face();
            double az = 2.0 * Math.PI * tongue / CoreOrbGeometry.FLAME_TONGUES
                    + time * Math.toRadians(CoreOrbGeometry.FLAME_SPIN);
            float rootFlow = 0.76F * 0.72F + 0.24F * (float) (az / (2.0 * Math.PI));
            float rootHue = rainbowHue(rootFlow, time);
            float t = q.a().s();
            // 根部 = 球面该处的流光色（同色源，故气焰与球体永远同色系）；
            // 尖端 = 暖白（色相插值到暖色 + 饱和度趋零 = 近白的火苗）
            float hue = CoreOrbPalette.mixHue(rootHue, CoreOrbPalette.FLAME_WARM_DEG / 360.0F, t);
            float sat = CoreOrbPalette.lerp(CoreOrbPalette.FLAME_SATURATION, 0.18F, t);
            float bri = CoreOrbPalette.lerp(0.88F, 1.0F, t);
            int color = CoreOrbPalette.compose(hue, sat, bri);
            // 尖端更实、根部更透：alpha 沿火舌高度渐变，火才"烧得起来"
            int alpha = (int) (CoreOrbPalette.lerp(120.0F, 245.0F, t));
            this.vertex(consumer, pose, q.a(), color, alpha);
            this.vertex(consumer, pose, q.b(), color, alpha);
            this.vertex(consumer, pose, q.c(), color, alpha);
            this.vertex(consumer, pose, q.d(), color, alpha);
        }
    }
}
