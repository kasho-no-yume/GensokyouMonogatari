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
    private static void vertex(PoseStack.Pose pose,VertexConsumer out,double x,double y,double z,float u,float v,
                               int r,int g,int b,int a,int light) {
        out.addVertex(pose.pose(),(float)x,(float)y,(float)z).setColor(r,g,b,a).setUv(u,v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0,1,0);
        com.bitsson.gensokyou.danmaku.render.DanmakuRenderProbe.countVertex();
    }
}
