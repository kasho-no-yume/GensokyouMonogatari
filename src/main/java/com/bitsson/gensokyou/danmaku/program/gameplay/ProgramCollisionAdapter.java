package com.bitsson.gensokyou.danmaku.program.gameplay;

import com.bitsson.gensokyou.danmaku.program.ops.Path;
import com.bitsson.gensokyou.danmaku.program.ops.Pattern;
import com.bitsson.gensokyou.danmaku.program.ops.CollisionProfile;
import com.bitsson.gensokyou.danmaku.program.server.DanmakuWorldRuntime;
import com.bitsson.gensokyou.registry.ModDamageTypes;
import com.bitsson.gensokyou.registry.ModAttributes;
import com.bitsson.gensokyou.config.GensokyouConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Objects;
import java.util.ArrayList;

/** Server adapter from runtime members to Minecraft collision and damage. */
public final class ProgramCollisionAdapter implements DanmakuWorldRuntime.CollisionSink {
    private final ServerLevel level;
    private final ProgramGameplayStats stats;
    private final java.util.Map<DanmakuWorldRuntime.MemberId,Double> beamLengths=new java.util.HashMap<>();

    public ProgramCollisionAdapter(ServerLevel level) {
        this(level,new ProgramGameplayStats());
    }
    public ProgramCollisionAdapter(ServerLevel level,ProgramGameplayStats stats) {
        this.level = Objects.requireNonNull(level); this.stats=Objects.requireNonNull(stats);
    }
    public ProgramGameplayStats stats() { return stats; }
    public com.bitsson.gensokyou.danmaku.program.network.ReplicaState.Projection project(
            com.bitsson.gensokyou.danmaku.program.network.ReplicaState.Projection projection) {
        var image=projection.image();
        beamLengths.keySet().removeIf(id->!com.bitsson.gensokyou.danmaku.program.network.ReplicaState.hasMember(projection.image(),id));
        if (!beamLengths.isEmpty()) {
            var entries=new java.util.HashMap<String,Record>(image.entries());
            for (var entry:beamLengths.entrySet()) entries.put("l/"+com.bitsson.gensokyou.danmaku.program.network.ReplicaState.motionKey(entry.getKey()),
                    new com.bitsson.gensokyou.danmaku.program.network.ReplicaState.Beam(entry.getKey(),entry.getValue()));
            image=new com.bitsson.gensokyou.danmaku.program.network.ReplicaState.Image(image.dimension(),image.tick(),image.revision(),entries);
        }
        return new com.bitsson.gensokyou.danmaku.program.network.ReplicaState.Projection(image,projection.descriptions(),projection.definitions());
    }

    @Override
    public void collide(DanmakuWorldRuntime runtime, DanmakuWorldRuntime.MemberId id,
                        Path.State from, Path.State until) {
        DanmakuWorldRuntime.MemberSnapshot member = runtime.member(id).orElse(null);
        if (member == null) return;
        DanmakuWorldRuntime.Encounter encounter = runtime.encounter(id).orElse(null);
        if (encounter == null || !runtime.active(encounter)) return;
        Entity resolvedAttacker = level.getServer().getPlayerList().getPlayer(encounter.owner());
        if (resolvedAttacker == null) resolvedAttacker = level.getEntity(encounter.owner());
        Entity attacker = resolvedAttacker;
        if (attacker == null || !attacker.isAlive() || attacker.level() != level) return;

        Pattern.Style style = member.template().style();
        CollisionProfile collisionProfile = member.plan().program().collisionProfile();
        boolean laser = collisionProfile instanceof CollisionProfile.Laser;
        CollisionProfile.Laser laserProfile = laser ? (CollisionProfile.Laser) collisionProfile : null;
        ProgramCollisionSample sample = new ProgramCollisionSample(encounter, id, runtime.tick(), runtime.tick() + 1,
                from, until, style.collisionRadius(), style.damageScale(), true, true,
                laser ? ProgramCollisionSample.Profile.LASER : ProgramCollisionSample.Profile.PROJECTILE,
                laser ? laserProfile.maxLength() : 0,
                laser && laserProfile.piercesBlocks(),
                laser ? laserProfile.damageIntervalTicks() : 1);
        stats.collisionSample();
        Vec3 fromWorld = world(from.position());
        Vec3 untilWorld = world(until.position());
        if (laser) {
            com.bitsson.gensokyou.danmaku.program.math.Vec3 direction = runtime.direction(id, runtime.tick());
            Vec3 laserDirection = world(direction);
            if (laserDirection.lengthSqr() < 1.0E-12D) return;
            untilWorld = fromWorld.add(laserDirection.normalize().scale(laserProfile.maxLength()));
            if (!laserProfile.piercesBlocks()) {
                var block = level.clip(new net.minecraft.world.level.ClipContext(fromWorld, untilWorld,
                        net.minecraft.world.level.ClipContext.Block.COLLIDER,
                        net.minecraft.world.level.ClipContext.Fluid.NONE, attacker));
                if (block.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) { untilWorld = block.getLocation(); stats.laserClip(); }
            }
            beamLengths.put(id,fromWorld.distanceTo(untilWorld));
        }
        var sampled=laser?java.util.Optional.of(List.of(new ProgramCollisionSweep.Segment(fromWorld,untilWorld)))
                :ProgramCollisionSweep.sample(t->world(runtime.state(id,t).position()),runtime.tick(),runtime.tick()+1D);
        if (sampled.isEmpty()) { runtime.remove(id,"collision-curve-budget"); return; }
        List<ProgramCollisionSweep.Segment> segments=sampled.orElseThrow();
        boolean blocked=false;
        if (!laser) {
            var clipped=new ArrayList<ProgramCollisionSweep.Segment>();
            for (var segment:segments) {
                var boxes=new ArrayList<AABB>();
                for (var shape:level.getBlockCollisions(attacker,ProgramCollisionSweep.bounds(List.of(segment),sample.collisionRadius())))
                    boxes.addAll(shape.toAabbs());
                var block=ProgramCollisionSweep.nearest(List.of(segment),boxes,box->box,sample.collisionRadius(),false);
                if (block.isPresent()) {
                    clipped.add(new ProgramCollisionSweep.Segment(segment.from(),block.orElseThrow().point())); blocked=true; break;
                }
                clipped.add(segment);
            }
            segments=clipped;
        }
        long memberAge=runtime.tick()-member.plan().birthTick();
        if (laser && (memberAge<0 || memberAge%sample.damageIntervalTicks()!=0)) { stats.laserSkipped(); return; }
        AABB query=ProgramCollisionSweep.bounds(segments,sample.collisionRadius());
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, query,
                e -> e.isAlive() && e.canBeHitByProjectile() && e != attacker);
        var nearest=ProgramCollisionSweep.nearest(segments,targets,LivingEntity::getBoundingBox,sample.collisionRadius(),laser);
        if (nearest.isPresent()) {
            LivingEntity target=nearest.orElseThrow().target();
            double base = attacker instanceof LivingEntity living && living.getAttribute(ModAttributes.DANMAKU_DAMAGE)!=null
                    ? living.getAttributeValue(ModAttributes.DANMAKU_DAMAGE)
                    : GensokyouConfig.DANMAKU_BASE_DAMAGE.get();
            if (!Double.isFinite(base) || base < 0) base = 0;
            base *= style.damageScale();
            ProgramHitContext context = new ProgramHitContext(encounter, id, attacker, target, runtime.tick(),
                    base, style.damageScale(), nearest.orElseThrow().distance(), member.template().targetSlot(), sample.profile());
            if (context.damage() > 0) { stats.hit(); if (!target.hurt(ModDamageTypes.programDanmaku(target, attacker), (float) context.damage())) stats.damageRejected(); }
            if (sample.profile() == ProgramCollisionSample.Profile.PROJECTILE) runtime.remove(id, "entity-hit");
        }
        if (blocked) runtime.remove(id,"block-hit");
    }

    private static Vec3 world(com.bitsson.gensokyou.danmaku.program.math.Vec3 value) {
        return new Vec3(value.x(), value.y(), value.z());
    }
}
