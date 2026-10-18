package com.bitsson.gensokyou.client;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.client.renderer.DanmakuRenderTypes;
import com.bitsson.gensokyou.client.renderer.DanmakuMeshes;
import com.bitsson.gensokyou.client.renderer.LaserDanmakuRenderer;
import com.bitsson.gensokyou.danmaku.program.gameplay.ProgramVisuals;
import com.bitsson.gensokyou.danmaku.visual.DanmakuGeometry;
import com.bitsson.gensokyou.danmaku.program.math.Vec3;
import com.bitsson.gensokyou.danmaku.program.network.ProgramRenderView;
import com.bitsson.gensokyou.danmaku.program.ops.CollisionProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.List;

/** Entity-free client renderer for the program replica projection. */
@EventBusSubscriber(modid=Gensokyou.MODID,value=Dist.CLIENT)
public final class ProgramDanmakuRenderer {
    private static final ResourceLocation SPHERE=ResourceLocation.fromNamespaceAndPath(
            Gensokyou.MODID,"textures/entity/sphere_danmaku_lod.png");
    private static final ResourceLocation LASER=ResourceLocation.fromNamespaceAndPath(
            Gensokyou.MODID,"textures/entity/laser_danmaku.png");
    private static final int LIGHT=0xF000F0;
    private static final ResourceLocation CAP=ResourceLocation.fromNamespaceAndPath(Gensokyou.MODID,"textures/entity/laser_cap.png");
    private static final ResourceLocation CIRCLE=ResourceLocation.fromNamespaceAndPath(Gensokyou.MODID,"textures/entity/laser_magic_circle.png");
    private static final ProgramSoundLedger SOUNDS=new ProgramSoundLedger();
    private static final com.bitsson.gensokyou.danmaku.program.gameplay.ProgramTimingWindow PROJECTION_TIMING=new com.bitsson.gensokyou.danmaku.program.gameplay.ProgramTimingWindow(2048);
    private static final com.bitsson.gensokyou.danmaku.program.gameplay.ProgramTimingWindow RENDER_TIMING=new com.bitsson.gensokyou.danmaku.program.gameplay.ProgramTimingWindow(2048);
    public static com.bitsson.gensokyou.danmaku.program.gameplay.ProgramTimingWindow.Snapshot projectionTiming() { return PROJECTION_TIMING.snapshot(); }
    public static com.bitsson.gensokyou.danmaku.program.gameplay.ProgramTimingWindow.Snapshot renderTiming() { return RENDER_TIMING.snapshot(); }
    private ProgramDanmakuRenderer() {}
    public static void clearSounds() { SOUNDS.clear(); PROJECTION_TIMING.clear(); RENDER_TIMING.clear(); }

    @SubscribeEvent
    public static void render(net.neoforged.neoforge.client.event.RenderLevelStageEvent event) {
        if (event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        long projected=System.nanoTime();
        List<ProgramRenderView.Snapshot> views=ProgramClientNetwork.renderViews(event.getPartialTick().getGameTimeDeltaPartialTick(false));
        if (views.isEmpty()) return;
        PROJECTION_TIMING.record(System.nanoTime()-projected);
        long started=System.nanoTime();
        Minecraft minecraft=Minecraft.getInstance();
        PoseStack pose=event.getPoseStack();
        net.minecraft.world.phys.Vec3 camera=event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers=minecraft.renderBuffers().bufferSource();
        RenderType bodyType=DanmakuRenderTypes.translucentDepth(SPHERE);
        RenderType laserType=DanmakuRenderTypes.additiveSolid(LASER);
        java.util.Set<RenderType> usedTypes=new java.util.HashSet<>();
        pose.pushPose(); pose.translate(-camera.x,-camera.y,-camera.z);
        for (ProgramRenderView.Snapshot snapshot:views) {
            for (ProgramRenderView.Billboard billboard:snapshot.billboards()) {
                var presentation=billboard.presentation();
                RenderType type=DanmakuRenderTypes.additiveSolid(ResourceLocation.parse(presentation.texture())); usedTypes.add(type);
                Vec3 p=billboard.position();
                pose.pushPose(); pose.translate(p.x(),p.y(),p.z());
                pose.mulPose(event.getCamera().rotation()); pose.mulPose(Axis.YP.rotationDegrees(180));
                pose.mulPose(Axis.ZP.rotation((float)presentation.visualAngleRadians().at(billboard.age())));
                int tint=presentation.tint();
                quad(pose.last(),buffers.getBuffer(type),(float)presentation.size(),(tint>>16)&255,(tint>>8)&255,tint&255,200);
                pose.popPose();
            }
            for (ProgramRenderView.Guide guide:snapshot.guides()) {
                for (Vec3 point:guide.points()) {
                    pose.pushPose(); pose.translate(point.x(),point.y(),point.z());
                    pose.mulPose(minecraft.gameRenderer.getMainCamera().rotation());
                    pose.mulPose(Axis.YP.rotationDegrees(180));
                    quad(pose.last(),buffers.getBuffer(bodyType),.035F,80,160,255,100);
                    pose.popPose();
                }
            }
            for (ProgramRenderView.Sound sound:snapshot.sounds()) {
                if (!SOUNDS.accept(snapshot.epoch(),sound)) continue;
                try {
                    BuiltInRegistries.SOUND_EVENT.getOptional(ResourceLocation.parse(sound.sound()))
                            .ifPresent(soundEvent -> minecraft.getSoundManager().play(SimpleSoundInstance.forUI(soundEvent,1.0F)));
                } catch (RuntimeException ignored) {
                    // Optional presentation sounds must not break the render loop.
                }
            }
            for (ProgramRenderView.Member member:snapshot.members()) {
                if (!member.visible()) continue;
                Vec3 p=member.position();
                pose.pushPose();
                pose.translate(p.x(),p.y(),p.z());
                int brightness=(int)Math.max(0,Math.min(255,255*member.brightness()));
                int alpha=(int)Math.max(0,Math.min(255,255*member.alpha()));
                if (member.collisionProfile() instanceof CollisionProfile.Laser laser) {
                    renderLaser(pose,buffers,member,usedTypes);
                } else {
                    var profile=ProgramVisuals.profile(member.style());
                    boolean star=profile.geometry()==DanmakuGeometry.STAR_PRISM;
                    RenderType type=profile==com.bitsson.gensokyou.danmaku.visual.DanmakuVisualProfile.DEFAULT ? bodyType : DanmakuRenderTypes.translucentDepth(profile.texture());
                    usedTypes.add(type);
                    VertexConsumer body=buffers.getBuffer(type);
                    pose.mulPose(minecraft.gameRenderer.getMainCamera().rotation());
                    pose.mulPose(Axis.YP.rotationDegrees(180));
                    float half=(float)(member.style().visualRadius()*profile.visualScale()*member.sizeMultiplier());
                    // Existing dense sphere texture contains the core and halo in one alpha layer.
                    // Keep a saturated body color; a second additive copy washes it out.
                    int tint=ProgramVisuals.tint(member.style());
                    int r=channel(tint,16,member.brightness()),g=channel(tint,8,member.brightness()),b=channel(tint,0,member.brightness());
                    if (star) {
                        pose.mulPose(Axis.XP.rotationDegrees((float)(profile.tumbleAmpDeg()*Math.sin(2*Math.PI*member.age()/profile.tumblePeriod()))));
                        DanmakuMeshes.star(pose.last(),body,half,r,g,b,alpha,LIGHT);
                    } else quad(pose.last(),body,half,r,g,b,alpha);
                }
                pose.popPose();
            }
            for (ProgramRenderView.Warning plan:snapshot.warnings()) {
                Vec3 start=plan.source(); Vec3 direction=plan.direction();
                pose.pushPose(); pose.translate(start.x(),start.y(),start.z());
                align(pose,direction);
                RenderType warningType=DanmakuRenderTypes.additiveSolid(
                        plan.collisionProfile() instanceof CollisionProfile.Laser ? LASER : SPHERE);
                usedTypes.add(warningType);
                VertexConsumer warning=buffers.getBuffer(warningType);
                if (plan.collisionProfile() instanceof CollisionProfile.Laser laser) {
                    LaserDanmakuRenderer.emitBeam(pose,warning,(float)laser.maxLength(),(float)plan.style().visualRadius()*.22F,255,0,0,100,LIGHT);
                    RenderType circle=DanmakuRenderTypes.additiveSolid(CIRCLE); usedTypes.add(circle);
                    pose.pushPose(); pose.mulPose(Axis.ZP.rotationDegrees((float)(snapshot.tick()*1.4)));
                    quad(pose.last(),buffers.getBuffer(circle),(float)plan.style().visualRadius()*2.2F,255,0,0,140);
                    pose.popPose();
                } else {
                    quad(pose.last(),warning,.08F,255,64,64,170);
                }
                pose.popPose();
            }
        }
        pose.popPose();
        // AFTER_PARTICLES is outside the entity pass: submit our custom types explicitly.
        buffers.endBatch(bodyType);
        for (RenderType type:usedTypes) if (type!=bodyType) buffers.endBatch(type);
        buffers.endBatch(laserType);
        buffers.endBatch(DanmakuRenderTypes.additiveGlow(SPHERE));
        buffers.endBatch(DanmakuRenderTypes.additiveGlow(LASER));
        RENDER_TIMING.record(System.nanoTime()-started);
    }

    private static void renderLaser(PoseStack pose,MultiBufferSource.BufferSource buffers,ProgramRenderView.Member member,java.util.Set<RenderType> used) {
        float length=(float)member.beamLength();
        float envelope=(float)Math.max(0,Math.min(1,Math.min(member.age()/3,(member.lifetime()-member.age())/3)));
        float radius=(float)(member.style().visualRadius()*member.sizeMultiplier()*envelope);
        if (radius<=0) return;
        int tint=ProgramVisuals.tint(member.style());
        // RGB has its own hidden-state dim; alpha already carries opacity/fade.
        double dim=member.brightness();
        int r=channel(tint,16,dim),g=channel(tint,8,dim),b=channel(tint,0,dim);
        int cr=(int)((((tint>>16)&255)*.25+255*.75)*dim);
        int cg=(int)((((tint>>8)&255)*.25+255*.75)*dim);
        int cb=(int)(((tint&255)*.25+255*.75)*dim);
        pose.pushPose(); align(pose,member.direction());
        RenderType beamType=DanmakuRenderTypes.additiveSolid(LASER); used.add(beamType);
        RenderType circleType=DanmakuRenderTypes.additiveSolid(CIRCLE); used.add(circleType);
        pose.pushPose(); pose.mulPose(Axis.ZP.rotationDegrees((float)member.age()*1.4F));
        quad(pose.last(),buffers.getBuffer(circleType),radius*2.2F,channel(~tint,16,dim),channel(~tint,8,dim),channel(~tint,0,dim),(int)(200*envelope*member.alpha()));
        pose.popPose();
        if (length>=.001F) {
            VertexConsumer beam=buffers.getBuffer(beamType);
            LaserDanmakuRenderer.emitBeam(pose,beam,length,radius*1.9F,r,g,b,(int)(70*envelope*member.alpha()),LIGHT);
            LaserDanmakuRenderer.emitBeam(pose,beam,length,radius,r,g,b,(int)(235*envelope*member.alpha()),LIGHT);
            LaserDanmakuRenderer.emitBeam(pose,beam,length,radius*.45F,cr,cg,cb,(int)(255*envelope*member.alpha()),LIGHT);
        }
        pose.popPose();
        if (length<.001F) return;
        RenderType capType=DanmakuRenderTypes.additiveSolid(CAP); used.add(capType);
        Vec3 direction=member.direction().unit();
        for (float distance:new float[]{0,length}) {
            pose.pushPose(); pose.translate(direction.x()*distance,direction.y()*distance,direction.z()*distance);
            pose.mulPose(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
            pose.mulPose(Axis.YP.rotationDegrees(180));
            quad(pose.last(),buffers.getBuffer(capType),radius,cr,cg,cb,(int)(235*envelope*member.alpha())); pose.popPose();
        }
    }

    private static int channel(int tint,int shift,double brightness) {
        return (int)Math.max(0,Math.min(255,((tint>>shift)&255)*brightness));
    }

    private static void quad(PoseStack.Pose pose,VertexConsumer out,float half,int r,int g,int b,int a) {
        out.addVertex(pose.pose(),-half,-half,0).setColor(r,g,b,a).setUv(0,1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LIGHT).setNormal(0,1,0);
        out.addVertex(pose.pose(), half,-half,0).setColor(r,g,b,a).setUv(1,1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LIGHT).setNormal(0,1,0);
        out.addVertex(pose.pose(), half, half,0).setColor(r,g,b,a).setUv(1,0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LIGHT).setNormal(0,1,0);
        out.addVertex(pose.pose(),-half, half,0).setColor(r,g,b,a).setUv(0,0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LIGHT).setNormal(0,1,0);
    }

    private static void beam(PoseStack.Pose pose,VertexConsumer out,float length,float radius,int r,int g,int b,int a) {
        out.addVertex(pose.pose(),-radius,0,0).setColor(r,g,b,a).setUv(0,0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LIGHT).setNormal(0,1,0);
        out.addVertex(pose.pose(), radius,0,0).setColor(r,g,b,a).setUv(1,0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LIGHT).setNormal(0,1,0);
        out.addVertex(pose.pose(), radius,0,length).setColor(r,g,b,a).setUv(1,length).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LIGHT).setNormal(0,1,0);
        out.addVertex(pose.pose(),-radius,0,length).setColor(r,g,b,a).setUv(0,length).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LIGHT).setNormal(0,1,0);
    }

    private static void align(PoseStack pose,Vec3 direction) {
        net.minecraft.world.phys.Vec3 d=new net.minecraft.world.phys.Vec3(direction.x(),direction.y(),direction.z());
        if (d.lengthSqr()<1.0E-12) return;
        d=d.normalize();
        double horizontal=d.horizontalDistance();
        if (horizontal<1.0E-6) { pose.mulPose(Axis.XP.rotationDegrees(d.y>0?-90:90)); return; }
        pose.mulPose(Axis.YP.rotationDegrees((float)(Math.atan2(d.x,d.z)*180/Math.PI)));
        pose.mulPose(Axis.XP.rotationDegrees((float)(Math.atan2(-d.y,horizontal)*180/Math.PI)));
    }
}
