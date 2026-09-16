package com.bitsson.gensokyou.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 物品在 FIXED 上下文下的**实际模型包围盒**（块，已含原版 display.fixed 变换与 -0.5 归中）。
 *
 * <p>原版 {@code ItemRenderer.render} 的真实顺序是
 * {@code handleCameraTransforms(套 display.fixed) → translate(-0.5,-0.5,-0.5) → 画 [0,1]³ 几何}。
 * 因此仅凭「方块=半高」「2D=贴面」的经验常数无法覆盖三类物品——本类按模型烘焙几何实测包围盒，
 * 用于把祭品静置底缘精确对齐台面（MUST NOT 因 FIXED 平移/枢轴差异被抬升）。
 *
 * <p>按 {@link Item} 缓存（模型资产在会话内稳定）；动态模型/自定义渲染器回落到单位立方体近似。
 */
final class ItemFixedBounds {

    /** fallback：以原点为中心的单位立方体（旧经验常数的落点）。 */
    private static final ItemFixedBounds FALLBACK =
            new ItemFixedBounds(-0.5F, 0.5F, -0.5F, 0.5F);

    private static final Map<Item, ItemFixedBounds> CACHE = new ConcurrentHashMap<>();

    /** inner 空间（FIXED·T(-0.5) 之后）的 Y/Z 范围，单位：块。 */
    final float minY;
    final float maxY;
    final float minZ;
    final float maxZ;

    private ItemFixedBounds(float minY, float maxY, float minZ, float maxZ) {
        this.minY = minY;
        this.maxY = maxY;
        this.minZ = minZ;
        this.maxZ = maxZ;
    }

    static ItemFixedBounds of(ItemStack stack, Level level) {
        if (stack.isEmpty()) {
            return FALLBACK;
        }
        try {
            return CACHE.computeIfAbsent(stack.getItem(), item -> compute(stack, level));
        } catch (Throwable t) {
            return FALLBACK;
        }
    }

    private static ItemFixedBounds compute(ItemStack stack, Level level) {
        BakedModel model = Minecraft.getInstance().getItemRenderer()
                .getModel(stack, level, null, 42);
        if (model == null || model.isCustomRenderer()) {
            return FALLBACK;
        }
        List<BakedQuad> quads = model.getQuads(null, null, RandomSource.create(42L));
        if (quads.isEmpty()) {
            return FALLBACK;
        }
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (BakedQuad quad : quads) {
            int[] vertices = quad.getVertices();
            int stride = vertices.length / 4;
            for (int i = 0; i < 4; i++) {
                int o = i * stride;
                float x = Float.intBitsToFloat(vertices[o]);
                float y = Float.intBitsToFloat(vertices[o + 1]);
                float z = Float.intBitsToFloat(vertices[o + 2]);
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                minZ = Math.min(minZ, z);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
                maxZ = Math.max(maxZ, z);
            }
        }
        // 复刻原版 inner 变换：display.fixed → translate(-0.5)
        PoseStack pose = new PoseStack();
        ItemTransform fixed = model.getTransforms().getTransform(ItemDisplayContext.FIXED);
        fixed.apply(false, pose);
        pose.translate(-0.5F, -0.5F, -0.5F);

        float bMinY = Float.MAX_VALUE, bMaxY = -Float.MAX_VALUE;
        float bMinZ = Float.MAX_VALUE, bMaxZ = -Float.MAX_VALUE;
        Vector3f corner = new Vector3f();
        for (int i = 0; i < 8; i++) {
            corner.set((i & 1) == 0 ? minX : maxX,
                    (i & 2) == 0 ? minY : maxY,
                    (i & 4) == 0 ? minZ : maxZ);
            pose.last().pose().transformPosition(corner);
            bMinY = Math.min(bMinY, corner.y);
            bMaxY = Math.max(bMaxY, corner.y);
            bMinZ = Math.min(bMinZ, corner.z);
            bMaxZ = Math.max(bMaxZ, corner.z);
        }
        return new ItemFixedBounds(bMinY, bMaxY, bMinZ, bMaxZ);
    }
}
