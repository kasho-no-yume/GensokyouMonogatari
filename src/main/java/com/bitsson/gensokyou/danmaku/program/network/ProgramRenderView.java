package com.bitsson.gensokyou.danmaku.program.network;

import com.bitsson.gensokyou.danmaku.program.math.Vec3;
import com.bitsson.gensokyou.danmaku.program.ops.Actions;
import com.bitsson.gensokyou.danmaku.program.ops.CollisionProfile;
import com.bitsson.gensokyou.danmaku.program.ops.Path;
import com.bitsson.gensokyou.danmaku.program.ops.Pattern;
import com.bitsson.gensokyou.danmaku.program.ops.Presentation;
import com.bitsson.gensokyou.danmaku.program.server.DanmakuWorldRuntime;

import java.util.List;
import java.util.Objects;

/** Immutable client render projection; it has no entity or world dependency. */
public final class ProgramRenderView {
    /**
     * 客户端渲染投影里的一个成员。
     *
     * <p><b>刻意不携带碰撞半径</b>：判定半径由服务端按碰撞形态分流（激光不乘倍率），
     * 渲染一律读 {@link Pattern.Style#visualRadius()}。把半径放进渲染投影只会制造第二份
     * 可能与服务端口径不一致的副本。
     */
    public record Member(DanmakuWorldRuntime.MemberId id, Vec3 position, Vec3 direction,
                         Pattern.Style style, boolean visible, boolean damaging,
                         double alpha, double sizeMultiplier, double brightness,
                          CollisionProfile collisionProfile, double age,double beamLength,long lifetime) {}
    public record Warning(long group, long batch, Vec3 source, Vec3 direction,
                          long birthTick, Pattern.Style style, CollisionProfile collisionProfile) {}
    public record Sound(long group, long batch, String sound, String deduplicationKey) {}
    public record Guide(List<Vec3> points) { public Guide { points=List.copyOf(points); } }
    public record Billboard(Vec3 position,Vec3 worldDirection,Presentation.Billboard presentation,double age) {}
    public record Snapshot(long tick, long revision, long epoch, List<Member> members, List<Warning> warnings,List<Sound> sounds,List<Guide> guides,List<Billboard> billboards) {
        public Snapshot(long tick,long revision,long epoch,List<Member> members,List<Warning> warnings,List<Sound> sounds,List<Guide> guides) {
            this(tick,revision,epoch,members,warnings,sounds,guides,List.of());
        }
        public Snapshot { members=List.copyOf(members); warnings=List.copyOf(warnings); sounds=List.copyOf(sounds); guides=List.copyOf(guides); billboards=List.copyOf(billboards); }
    }

    private ProgramRenderView() {}

    static Snapshot capture(ReplicaState.Image image, java.util.Map<String, Actions.ChildProgram> descriptions,
                                   double tick) {
        Objects.requireNonNull(image);
        java.util.ArrayList<Member> members=new java.util.ArrayList<>();
        java.util.ArrayList<Warning> warnings=new java.util.ArrayList<>();
        java.util.ArrayList<Sound> sounds=new java.util.ArrayList<>();
        java.util.ArrayList<Guide> guides=new java.util.ArrayList<>();
        java.util.ArrayList<Billboard> billboards=new java.util.ArrayList<>();
        for (Record record:image.entries().values()) {
            if (record instanceof ReplicaState.Emitter emitter && emitter.description().billboard()!=null && tick>=emitter.origin()) {
                var group=emitter.sourceGroup()==null?null:(ReplicaState.Group)image.entries().get(ReplicaState.groupKey(emitter.sourceGroup()));
                // Group-source emitter FX use the same deterministic source closure as authority shots.
                // Member/external sources require explicit source pose projection before drawing FX.
                if (emitter.description().source() instanceof Actions.GroupPose && group!=null && tick<group.fadeEnd()
                        && tick<emitter.origin()+emitter.description().shotTick(emitter.description().count()-1)+1) {
                    var source=(group.motion()==null?com.bitsson.gensokyou.danmaku.program.server.GroupMotion.stationary(group.frame(),0):group.motion()).pose(tick);
                    var nozzle=emitter.description().nozzle(source,tick-emitter.origin());
                    billboards.add(new Billboard(nozzle.frame().origin(),nozzle.frame().forward(),emitter.description().billboard(),tick-emitter.origin()));
                }
            }
            if (!(record instanceof ReplicaState.Batch batch)) continue;
            Actions.ChildProgram program=Objects.requireNonNull(descriptions.get(batch.description()));
            var group=(ReplicaState.Group)image.entries().get(ReplicaState.groupKey(batch.group()));
            var clocks=group.clocks();
            boolean guided=false;
            for (int templatePosition=0;templatePosition<batch.templates().size();templatePosition++) {
                Pattern.Member template=batch.templates().get(templatePosition);
                // A committed snapshot can arrive before the smoothed render clock reaches birth.
                // Paths accept member age >= 0 only; do not evaluate future members backwards.
                if (!batch.alive(templatePosition) || tick<batch.birth() || tick-batch.birth()>=program.lifetimeTicks()) continue;
                DanmakuWorldRuntime.MemberId id=new DanmakuWorldRuntime.MemberId(batch.group(),batch.ordinal(),template.index());
                Path.State state=ReplicaState.state(image,descriptions,id,tick);
                if (state.ended() || tick>=group.fadeEnd()) continue;
                double age=tick-batch.birth();
                if (!guided && age>=20 && age<30 && program.path() instanceof com.bitsson.gensokyou.danmaku.program.ops.Paths.Segments segments) {
                    double start=0;
                    for (var segment:segments.segments()) {
                        if (segment.path() instanceof com.bitsson.gensokyou.danmaku.program.ops.Paths.Arc) {
                            java.util.ArrayList<Vec3> points=new java.util.ArrayList<>();
                            for (int step=0;step<=12;step++) points.add(ReplicaState.state(image,descriptions,id,batch.birth()+start+segment.duration()*step/12).position());
                            guides.add(new Guide(points)); guided=true; break;
                        }
                        start+=segment.duration();
                    }
                }
                var presentation=program.presentation().at(new com.bitsson.gensokyou.danmaku.program.ops.Presentation.Clocks(
                        tick-clocks.card(),tick-clocks.cycle(),tick-clocks.group(),tick-batch.birth()));
                Vec3 direction=state.velocity();
                if (direction.length()<1e-9) direction=batch.source().frame().direction(template.direction());
                CollisionProfile profile=program.collisionProfile();
                if (profile instanceof CollisionProfile.Laser) direction=ReplicaState.direction(image,descriptions,id,tick);
                double beamLength=profile instanceof CollisionProfile.Laser laser?laser.maxLength():0;
                if (image.entries().get("l/"+ReplicaState.motionKey(id)) instanceof ReplicaState.Beam beam) beamLength=beam.length();
                // 淡出按组自己的淡出窗口等比推进（切牌退场 30 tick，内容收束 10 tick）。
                // 固定除数会把长窗口的前段留成满亮 —— 旧弹与新弹同亮，玩家分不出谁在退场。
                double fade=group.fadeEnd()==Long.MAX_VALUE?1
                        :Math.max(0,Math.min(1,(group.fadeEnd()-tick)/(double)Math.max(1,group.fadeTicks())));
                boolean hidden=program.presentation().visibility()!=null && !program.presentation().visibility().visible(
                        new Presentation.Clocks(tick-clocks.card(),tick-clocks.cycle(),tick-clocks.group(),tick-batch.birth()));
                double alpha=presentation.alpha(),brightness=presentation.brightness();
                if (hidden) {
                    double dim=profile instanceof CollisionProfile.Laser
                            ?com.bitsson.gensokyou.danmaku.visual.DanmakuHiddenStyle.LASER_DIM
                            :com.bitsson.gensokyou.danmaku.visual.DanmakuHiddenStyle.bodyAlpha(com.bitsson.gensokyou.danmaku.program.gameplay.ProgramVisuals.profile(template.style()));
                    alpha=alpha/Presentation.HIDDEN_ALPHA*dim;
                    if (profile instanceof CollisionProfile.Laser) brightness*=dim;
                }
                members.add(new Member(id,state.position(),direction,template.style(),
                         presentation.visible(),batch.harmful(templatePosition) && presentation.damaging(),
                          alpha*fade,presentation.sizeMultiplier(),brightness,profile,tick-batch.birth(),beamLength,program.lifetimeTicks()));
            }
            double previous=tick-1.0D;
            Presentation.Clocks previousClocks=new Presentation.Clocks(previous-clocks.card(),previous-clocks.cycle(),previous-clocks.group(),previous-batch.birth());
            Presentation.Clocks currentClocks=new Presentation.Clocks(tick-clocks.card(),tick-clocks.cycle(),tick-clocks.group(),tick-batch.birth());
            for (Presentation.SoundCue cue:batch.warning() || tick<batch.birth() ? List.<Presentation.SoundCue>of() : program.presentation().soundCues()) {
                if (cue.due(previousClocks.time(cue.clock()),currentClocks.time(cue.clock()))) {
                    sounds.add(new Sound(batch.group(),batch.ordinal(),cue.sound(),cue.deduplicationKey()));
                }
            }
            if (batch.warning()) for (Pattern.Member template:batch.templates()) {
                // Before birth hold the exact age-zero pose that will be committed, including transforms.
                var warningState=com.bitsson.gensokyou.danmaku.program.server.EmissionPlan.state(program,batch.binding(),batch.source(),batch.seed(),template,0,batch.aimLocal());
                Vec3 warningDirection=com.bitsson.gensokyou.danmaku.program.server.EmissionPlan.beamDirection(program,batch.source(),template,0,batch.aimLocal());
                if (batch.motion()!=null) {
                    warningState=batch.motion().apply(program.memberState(template,batch.seed(),0,batch.aimLocal()),tick);
                    warningDirection=batch.motion().direction(program.transform().direction(com.bitsson.gensokyou.danmaku.program.ops.Actions.ChildProgram.aimDirection(template,batch.aimLocal()),0),tick);
                }
                warnings.add(new Warning(batch.group(),batch.ordinal(),warningState.position(),
                         warningDirection,
                        batch.birth(),template.style(),program.collisionProfile()));
            }
        }
        long epoch=image.entries().values().stream().filter(r->r instanceof ReplicaState.Group)
                .map(r->((ReplicaState.Group)r).encounter().epoch()).findFirst().orElse(0L);
        return new Snapshot(image.tick(),image.revision(),epoch,members,warnings,sounds,guides,billboards);
    }
}
