package com.bitsson.gensokyou.client.renderer;

import com.bitsson.gensokyou.danmaku.visual.DanmakuGeometry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;

/** Entity-independent geometry shared by legacy and program renderers. */
public final class DanmakuMeshes {
    private DanmakuMeshes() {}
    public static void star(PoseStack.Pose pose,VertexConsumer out,double radius,int r,int g,int b,int a,int light) {
        double h=DanmakuGeometry.starPrismHalfThickness(radius);
        double[] outline=DanmakuGeometry.starPrismOutline(radius);
        for (int i=0;i<10;i++) {
            int j=(i+1)%10;
            double x=outline[2*i],y=outline[2*i+1],xx=outline[2*j],yy=outline[2*j+1];
            vertex(pose,out,0,0,h,.5F,.5F,r,g,b,a,light);
            vertex(pose,out,x,y,h,0,0,r,g,b,a,light);
            vertex(pose,out,xx,yy,h,1,0,r,g,b,a,light);
            vertex(pose,out,xx,yy,h,1,0,r,g,b,a,light);
            vertex(pose,out,0,0,-h,.5F,.5F,r,g,b,a,light);
            vertex(pose,out,xx,yy,-h,1,0,r,g,b,a,light);
            vertex(pose,out,x,y,-h,0,0,r,g,b,a,light);
            vertex(pose,out,x,y,-h,0,0,r,g,b,a,light);
            vertex(pose,out,x,y,h,0,1,r,g,b,a,light);
            vertex(pose,out,xx,yy,h,1,1,r,g,b,a,light);
            vertex(pose,out,xx,yy,-h,1,0,r,g,b,a,light);
            vertex(pose,out,x,y,-h,0,0,r,g,b,a,light);
        }
    }

    /** Draw a single-pass star sprite with a vertex-tinted white core. */
    public static void starSprite(PoseStack.Pose pose, VertexConsumer out, float halfSize,
                                  int r, int g, int b, float whiteBrightness, int a, int light) {
        for (int y = 0; y < 2; y++) {
            float y0 = (y - 1.0F) * halfSize;
            float y1 = y0 + halfSize;
            float v0 = 1.0F - y * 0.5F;
            float v1 = v0 - 0.5F;
            for (int x = 0; x < 2; x++) {
                float x0 = (x - 1.0F) * halfSize;
                float x1 = x0 + halfSize;
                float u0 = x * 0.5F;
                float u1 = u0 + 0.5F;
                vertex(pose, out, x0, y0, 0.0F, u0, v0, starChannel(r, u0, v0, whiteBrightness),
                        starChannel(g, u0, v0, whiteBrightness), starChannel(b, u0, v0, whiteBrightness), a, light);
                vertex(pose, out, x1, y0, 0.0F, u1, v0, starChannel(r, u1, v0, whiteBrightness),
                        starChannel(g, u1, v0, whiteBrightness), starChannel(b, u1, v0, whiteBrightness), a, light);
                vertex(pose, out, x1, y1, 0.0F, u1, v1, starChannel(r, u1, v1, whiteBrightness),
                        starChannel(g, u1, v1, whiteBrightness), starChannel(b, u1, v1, whiteBrightness), a, light);
                vertex(pose, out, x0, y1, 0.0F, u0, v1, starChannel(r, u0, v1, whiteBrightness),
                        starChannel(g, u0, v1, whiteBrightness), starChannel(b, u0, v1, whiteBrightness), a, light);
            }
        }
    }

    private static int starChannel(int tint, float u, float v, float whiteBrightness) {
        float dx = (u - 0.5F) * 2.0F;
        float dy = (v - 0.5F) * 2.0F;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        float whiteCore = Math.max(0.0F, 1.0F - distance / 0.72F);
        int white = Math.max(0, Math.min(255, Math.round(255.0F * whiteBrightness)));
        return Math.max(0, Math.min(255, Math.round(tint + (white - tint) * whiteCore)));
    }

    private static void vertex(PoseStack.Pose pose,VertexConsumer out,double x,double y,double z,float u,float v,
                               int r,int g,int b,int a,int light) {
        out.addVertex(pose.pose(),(float)x,(float)y,(float)z).setColor(r,g,b,a).setUv(u,v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0,1,0);
        com.bitsson.gensokyou.danmaku.render.DanmakuRenderProbe.countVertex();
    }
}
