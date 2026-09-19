package com.bitsson.gensokyou.client.renderer;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.block.entity.CrystalBlockEntity;
import com.bitsson.gensokyou.client.GensokyouTextures;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 无尽藏晶渲染器（BER）：1.5 格高、0.75 格宽的正菱形（四棱双锥 / octahedron）水晶，
 * 中心悬于方块上方，持续绕纵轴自转并轻微上下浮动。
 *
 * <p><b>几何：</b>上下两尖端 + 赤道正方形四顶点共 8 个三角棱面；每个棱面以退化四边形
 * （末顶点重复）输出。UV 按「上尖端→纹理上半、下尖端→纹理下半」映射，贴图纵向虹彩
 * 渐变天然对齐水晶纵轴。
 *
 * <p><b>表现层（顺序无关通道，规避半透明排序闪烁）：</b>
 * <ol>
 *   <li>外壳 {@link RenderType#entityTranslucent}（凸体单面裁剪：前向面互不重叠 → 排序
 *       无关），逐棱面混入缓慢轮转的虹彩；</li>
 *   <li>{@link DanmakuRenderTypes#additiveGlow} 略放大 1.08 倍的加法外晕，脉动呼吸；</li>
 *   <li>脚下灵焰：自建 ribbon 发射器——每个平面沿高分段，段宽/横向摆幅/透明度按
 *       {@code gameTime} 与平面相位扰动，配纵向无缝的灰度火焰条带向上滚动，
 *       呈燃烧、摇曳、上窜的火苗（逐平面 tint 不同虹彩色）；</li>
 *   <li>客户端本地彩色尘粒 + 偶发 END_ROD（MUST NOT 发服务端粒子包）。</li>
 * </ol>
 * 全部相位由 {@code gameTime} 本地推导（方块实体零数据、零网络包）；满亮度自发光。
 */
public class CrystalRenderer implements BlockEntityRenderer<CrystalBlockEntity> {

    /** 水晶总高（格）。底尖端贴方块底，上尖端抬到 1.5。 */
    private static final float TOTAL_HEIGHT = 1.5F;
    /** 赤道半径（格）：宽 0.75。 */
    private static final float RADIUS = 0.375F;
    /** 水晶几何中心高度（=总高一半，使底尖端落在 y=0）。 */
    private static final float CENTER_Y = TOTAL_HEIGHT * 0.5F;

    private static final float SPIN_DEGREES_PER_TICK = 0.7F;
    private static final float BOB_AMPLITUDE = 0.035F;
    private static final float BOB_SPEED = 0.08F;

    private static final int FULL_BRIGHT = 0xF000F0;

    // ---- 灵焰参数 ----
    /** 火焰高度（格）：明显高过 1.5 的水晶。 */
    private static final float FLAME_HEIGHT = 3.4F;
    /** 火焰底部半宽（格）。 */
    private static final float FLAME_BASE_HALF = 0.30F;
    /** 交叉平面数（越多越有体积感，避免"几面板"）。 */
    private static final int FLAME_PLANES = 7;
    /** 每平面沿高分段数（弯曲/摆动的平滑度）。 */
    private static final int FLAME_SEGMENTS = 12;
    /** 火焰底部基准亮度（顶点 alpha，向上渐隐到 0）。 */
    private static final int FLAME_ALPHA = 175;
    /** 火舌纹理向上滚动速度（纹理单位/刻）。 */
    private static final float FLAME_SCROLL_PER_TICK = 0.55F;

    /** 虹彩棱镜四色，逐棱面/逐火舌轮转混合（青→紫→品红→蓝绿）。 */
    private static final int[] IRIS = { 0x6CD8FF, 0xAA80FF, 0xFF80E0, 0x80FFF0 };

    private static final ResourceLocation FLAME_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Gensokyou.MODID, "textures/fx/aura_flame.png");

    /** pos → 上次发射粒子的 gameTime：BER 逐帧回调，按 tick 去重（稳态每 tick 至多一次）。 */
    private static final Map<BlockPos, Long> LAST_PARTICLE_TICK = new WeakHashMap<>();

    public CrystalRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(CrystalBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Level level = blockEntity.getLevel();
        float time = (level == null ? 0.0F : level.getGameTime()) + partialTick;
        if (level != null) {
            emitAmbientParticles(level, blockEntity.getBlockPos(), level.getGameTime());
        }

        // ③ 脚下灵焰：先画（不写深度），水晶再叠上去，晶底周围留有火舌
        renderFlameColumn(poseStack, bufferSource, time);

        float bob = Mth.sin(time * BOB_SPEED) * BOB_AMPLITUDE;
        float spin = time * SPIN_DEGREES_PER_TICK;
        poseStack.pushPose();
        poseStack.translate(0.5D, CENTER_Y + bob, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(spin));

        // ① 半透明外壳（凸体单面裁剪：前向面互不重叠 → 排序无关，无棱边闪烁）
        VertexConsumer shell = bufferSource.getBuffer(RenderType.entityTranslucent(GensokyouTextures.CRYSTAL));
        emitOctahedron(poseStack, shell, 1.0F, time, 235, 0.5F);

        // ② 加法外晕：略放大 + 脉动，逐棱面满虹彩
        VertexConsumer halo = bufferSource.getBuffer(DanmakuRenderTypes.additiveGlow(GensokyouTextures.CRYSTAL));
        int pulse = (int) (80.0F + 45.0F * Mth.sin(time * 0.09F));
        emitOctahedron(poseStack, halo, 1.08F, time, pulse, 1.0F);

        poseStack.popPose();
    }

    /**
     * 脚下灵焰柱：7 个交叉平面，每面沿高分成 {@link #FLAME_SEGMENTS} 段，
     * 逐段计算宽度/横向摆动/透明度（带相位扰动），配纵向滚动的火焰条带。
     * 每面 tint 不同虹彩色 → 彩色火苗。
     */
    private void renderFlameColumn(PoseStack poseStack, MultiBufferSource bufferSource, float time) {
        VertexConsumer buf = bufferSource.getBuffer(DanmakuRenderTypes.additiveGlow(FLAME_TEXTURE));
        int colorBase = Mth.floor(time * 0.02F);
        poseStack.pushPose();
        poseStack.translate(0.5D, 0.0D, 0.5D);
        for (int i = 0; i < FLAME_PLANES; i++) {
            int color = IRIS[Math.floorMod(colorBase + i, IRIS.length)];
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(i * 180.0F / FLAME_PLANES));
            emitFlamePlane(poseStack.last(), buf, time, i, color);
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    /** 单个火焰平面：分段 ribbon，宽度收束 + 横向摆动 + 顶部渐隐，出火舌扰动。 */
    private void emitFlamePlane(PoseStack.Pose pose, VertexConsumer c, float time, int plane, int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        float phase = plane * 1.9F;
        float scroll = time * FLAME_SCROLL_PER_TICK;
        float prevLx = 0.0F, prevRx = 0.0F, prevY = 0.0F, prevV = 0.0F, prevA = 0.0F;
        for (int s = 0; s <= FLAME_SEGMENTS; s++) {
            float t = (float) s / FLAME_SEGMENTS;
            float y = t * FLAME_HEIGHT;
            // 宽度：向上收束但保留火苗头部（非尖点），叠加脉动
            float taper = 1.0F - 0.62F * t * t;
            float wobble = 0.78F + 0.22F * Mth.sin(t * 7.5F + phase + time * 0.15F);
            float half = FLAME_BASE_HALF * taper * wobble;
            // 横向摆动：越高摆幅越大，逐面相位不同 → 火焰整体扭动
            float sway = 0.13F * t * Mth.sin(t * 5.0F + phase * 1.7F + time * 0.11F);
            // 亮度：底部最亮，向上渐隐，叠加火苗闪烁
            float flicker = 0.80F + 0.20F * Mth.sin(t * 11.0F + phase + time * 0.23F);
            float alpha = FLAME_ALPHA * (1.0F - t) * flicker;
            float v = scroll + t * FLAME_HEIGHT;
            float lx = sway - half;
            float rx = sway + half;
            if (s > 0) {
                vertex(c, pose, prevLx, prevY, 0.0F, 0.0F, prevV, r, g, b, (int) prevA);
                vertex(c, pose, prevRx, prevY, 0.0F, 1.0F, prevV, r, g, b, (int) prevA);
                vertex(c, pose, rx, y, 0.0F, 1.0F, v, r, g, b, (int) Mth.clamp(alpha, 0.0F, 255.0F));
                vertex(c, pose, lx, y, 0.0F, 0.0F, v, r, g, b, (int) Mth.clamp(alpha, 0.0F, 255.0F));
            }
            prevLx = lx;
            prevRx = rx;
            prevY = y;
            prevV = v;
            prevA = Mth.clamp(alpha, 0.0F, 255.0F);
        }
    }

    /**
     * 客户端本地环境尘粒：绕水晶/火焰缓慢上升的彩色光点 + 少量 END_ROD。
     * 按 gameTime 每 tick 去重（BER 逐帧调用），MUST NOT 走服务端粒子包。
     */
    private void emitAmbientParticles(Level level, BlockPos pos, long gameTime) {
        Long last = LAST_PARTICLE_TICK.put(pos, gameTime);
        if (last != null && last == gameTime) {
            return;
        }
        RandomSource random = level.getRandom();
        int color = IRIS[random.nextInt(IRIS.length)];
        double angle = random.nextDouble() * Math.PI * 2.0D;
        double radius = FLAME_BASE_HALF * (0.3D + random.nextDouble() * 0.9D);
        level.addParticle(
                new DustParticleOptions(new Vector3f(
                        ((color >> 16) & 0xFF) / 255.0F,
                        ((color >> 8) & 0xFF) / 255.0F,
                        (color & 0xFF) / 255.0F), 0.7F + random.nextFloat() * 0.5F),
                pos.getX() + 0.5D + Math.cos(angle) * radius,
                pos.getY() + 0.1D + random.nextDouble() * (FLAME_HEIGHT * 0.7D),
                pos.getZ() + 0.5D + Math.sin(angle) * radius,
                Math.cos(angle) * 0.01D, 0.03D + random.nextDouble() * 0.03D,
                Math.sin(angle) * 0.01D);
        if (random.nextFloat() < 0.28F) {
            double sparkAngle = random.nextDouble() * Math.PI * 2.0D;
            level.addParticle(ParticleTypes.END_ROD,
                    pos.getX() + 0.5D + Math.cos(sparkAngle) * FLAME_BASE_HALF * 0.6D,
                    pos.getY() + 0.2D + random.nextDouble() * TOTAL_HEIGHT,
                    pos.getZ() + 0.5D + Math.sin(sparkAngle) * FLAME_BASE_HALF * 0.6D,
                    0.0D, 0.03D, 0.0D);
        }
    }

    /**
     * 输出四棱双锥的 8 个棱面。{@code scale} 用于外晕的放大遍；
     * {@code alpha} 为该遍统一透明度，{@code irisMix} 为逐棱面虹彩色偏强度（0=纯白）。
     */
    private void emitOctahedron(PoseStack poseStack, VertexConsumer consumer, float scale,
                                float time, int alpha, float irisMix) {
        float half = CENTER_Y * scale;
        float radius = RADIUS * scale;
        float[] ex = { radius, 0.0F, -radius, 0.0F };
        float[] ez = { 0.0F, radius, 0.0F, -radius };
        PoseStack.Pose pose = poseStack.last();
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) & 3;
            int color = faceColor(i, time, irisMix);
            // 上半棱面：上尖端 → 赤道 E_j → E_i（外向 CCW）
            tri(consumer, pose,
                    0.0F, half, 0.0F, 0.5F, 0.0F,
                    ex[j], 0.0F, ez[j], 1.0F, 0.5F,
                    ex[i], 0.0F, ez[i], 0.0F, 0.5F,
                    color, alpha);
            // 下半棱面：下尖端 → 赤道 E_i → E_j（外向 CCW）
            tri(consumer, pose,
                    0.0F, -half, 0.0F, 0.5F, 1.0F,
                    ex[i], 0.0F, ez[i], 0.0F, 0.5F,
                    ex[j], 0.0F, ez[j], 1.0F, 0.5F,
                    color, alpha);
        }
    }

    /** 逐棱面颜色：虹彩四色随 gameTime 缓慢轮转，再按 {@code irisMix} 向白色靠拢。 */
    private static int faceColor(int face, float time, float irisMix) {
        int n = IRIS.length;
        float cycle = time * 0.02F;
        int base = Mth.floor(cycle);
        int c0 = IRIS[Math.floorMod(face + base, n)];
        int c1 = IRIS[Math.floorMod(face + base + 1, n)];
        int color = mix(c0, c1, cycle - base);
        return irisMix >= 1.0F ? color : mix(0xFFFFFF, color, irisMix);
    }

    private static void tri(VertexConsumer consumer, PoseStack.Pose pose,
                            float x0, float y0, float z0, float u0, float v0,
                            float x1, float y1, float z1, float u1, float v1,
                            float x2, float y2, float z2, float u2, float v2,
                            int color, int alpha) {
        float ax = x1 - x0, ay = y1 - y0, az = z1 - z0;
        float bx = x2 - x0, by = y2 - y0, bz = z2 - z0;
        float nx = ay * bz - az * by;
        float ny = az * bx - ax * bz;
        float nz = ax * by - ay * bx;
        float len = Mth.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 1.0E-5F) {
            nx /= len;
            ny /= len;
            nz /= len;
        } else {
            nx = 0.0F;
            ny = 1.0F;
            nz = 0.0F;
        }
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        // QUADS 模式：末顶点重复成退化四边形，等效一个三角面
        vertex(consumer, pose, x0, y0, z0, u0, v0, r, g, b, alpha);
        vertex(consumer, pose, x1, y1, z1, u1, v1, r, g, b, alpha);
        vertex(consumer, pose, x2, y2, z2, u2, v2, r, g, b, alpha);
        vertex(consumer, pose, x2, y2, z2, u2, v2, r, g, b, alpha);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose,
                               float x, float y, float z, float u, float v,
                               int r, int g, int b, int a) {
        consumer.addVertex(pose.pose(), x, y, z)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    private static int mix(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = (int) (ar + (br - ar) * t);
        int g = (int) (ag + (bg - ag) * t);
        int bl = (int) (ab + (bb - ab) * t);
        return (r << 16) | (g << 8) | bl;
    }

    /** 渲染包围盒覆盖水晶（1.5 格）与灵焰（3.4 格）全高，避免视角偏移时被裁剪。 */
    @Override
    public AABB getRenderBoundingBox(CrystalBlockEntity blockEntity) {
        return new AABB(blockEntity.getBlockPos()).expandTowards(0.0D, 3.0D, 0.0D).inflate(0.6D);
    }
}
