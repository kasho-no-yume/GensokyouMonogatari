package com.bitsson.gensokyou.client.renderer;

import com.bitsson.gensokyou.danmaku.visual.DanmakuGeometry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;

/** Entity-independent projectile meshes shared by legacy and program renderers. */
public final class DanmakuMeshes {
    private static final int STAR_SEGMENTS = 10;
    private static final double[] STAR_OUTLINE = DanmakuGeometry.starPrismOutline(1.0D);
    private static final double[] STAR_FACE_DEPTH = {1.0D, 1.0D, 0.98D, 0.90D, 0.82D};
    private static final float[] STAR_FACE_WHITE = {1.0F, 1.0F, 0.0F, 0.08F, 0.30F};

    private DanmakuMeshes() {}
    /** White core and colored points on a solid star with an opaque sidewall; one body pass. */
    public static void star(PoseStack.Pose pose, VertexConsumer out, double radius,
                            int r, int g, int b, int a, int light) {
        double halfDepth = DanmakuGeometry.starPrismHalfThickness(radius);
        for (int face = -1; face <= 1; face += 2) {
            for (int ring = 0; ring < STAR_FACE_DEPTH.length - 1; ring++) {
                for (int i = 0; i < STAR_SEGMENTS; i++) {
                    int next = (i + 1) % STAR_SEGMENTS;
                    int first = face > 0 ? next : i;
                    int second = face > 0 ? i : next;
                    starSurfaceVertex(pose, out, first, ring, face, radius, halfDepth, r, g, b, a, light);
                    starSurfaceVertex(pose, out, first, ring + 1, face, radius, halfDepth, r, g, b, a, light);
                    starSurfaceVertex(pose, out, second, ring + 1, face, radius, halfDepth, r, g, b, a, light);
                    starSurfaceVertex(pose, out, second, ring, face, radius, halfDepth, r, g, b, a, light);
                }
            }
        }
        // Close the opaque body at ring 3. Ring 4 is only its soft face border, not the sidewall.
        int sideR = shaded(r, 0.72F);
        int sideG = shaded(g, 0.72F);
        int sideB = shaded(b, 0.72F);
        for (int i = 0; i < STAR_SEGMENTS; i++) {
            int next = (i + 1) % STAR_SEGMENTS;
            starSurfaceVertex(pose, out, i, 3, -1, radius, halfDepth, sideR, sideG, sideB, a, light);
            starSurfaceVertex(pose, out, i, 3, 1, radius, halfDepth, sideR, sideG, sideB, a, light);
            starSurfaceVertex(pose, out, next, 3, 1, radius, halfDepth, sideR, sideG, sideB, a, light);
            starSurfaceVertex(pose, out, next, 3, -1, radius, halfDepth, sideR, sideG, sideB, a, light);
        }
    }

    private static void starSurfaceVertex(PoseStack.Pose pose, VertexConsumer out, int index, int ring, int face,
                                          double radius, double halfDepth,
                                          int r, int g, int b, int a, int light) {
        double outlineRadius = index % 2 == 0 ? 1.0D : DanmakuGeometry.STAR_INNER_RATIO;
        double ringRadius = switch (ring) {
            case 0 -> 0.0D;
            case 1 -> DanmakuGeometry.STAR_WHITE_CORE_RADIUS;
            case 2 -> 0.47D + 0.21D * (outlineRadius - DanmakuGeometry.STAR_INNER_RATIO)
                    / (1.0D - DanmakuGeometry.STAR_INNER_RATIO);
            case 3 -> outlineRadius * 0.94D;
            default -> outlineRadius;
        };
        double x = STAR_OUTLINE[index * 2] / outlineRadius * ringRadius;
        double y = STAR_OUTLINE[index * 2 + 1] / outlineRadius * ringRadius;
        float white = STAR_FACE_WHITE[ring];
        int brightness = Math.max(r, Math.max(g, b));
        float shade = face > 0 ? 1.0F : 0.96F;
        int alpha = ring == 4 ? Math.round(a * 0.35F) : a;
        vertex(pose, out, x * radius, y * radius, face * halfDepth * STAR_FACE_DEPTH[ring],
                (float) (0.5D + x * 0.5D), (float) (0.5D - y * 0.5D),
                shaded(r + (brightness - r) * white, shade),
                shaded(g + (brightness - g) * white, shade),
                shaded(b + (brightness - b) * white, shade), alpha, light);
    }

    private static int shaded(float channel, float shade) {
        return Math.max(0, Math.min(255, Math.round(channel * shade)));
    }

    private static void vertex(PoseStack.Pose pose,VertexConsumer out,double x,double y,double z,float u,float v,
                               int r,int g,int b,int a,int light) {
        out.addVertex(pose.pose(),(float)x,(float)y,(float)z).setColor(r,g,b,a).setUv(u,v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0,1,0);
        com.bitsson.gensokyou.danmaku.render.DanmakuRenderProbe.countVertex();
    }
}
