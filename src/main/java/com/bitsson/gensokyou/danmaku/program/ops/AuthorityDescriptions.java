package com.bitsson.gensokyou.danmaku.program.ops;

import com.bitsson.gensokyou.danmaku.program.*;
import com.bitsson.gensokyou.danmaku.program.math.*;
import java.util.Objects;

/** Complete input policies; world queries and transaction execution belong to the server stage. */
public final class AuthorityDescriptions {
    private AuthorityDescriptions() {}
    public enum TargetPoint { EYES, CHEST, FEET }
    public enum SurfaceMode { DOWNWARD_SOLID_TOP, FIRST_AIR_ABOVE }
    public enum Failure { REJECT_BATCH, CANCEL_GROUP }
    public record CaptureTargets(double radius,int maximum,boolean excludeCreative,boolean excludeSpectator) {
        public CaptureTargets { Deterministic.positive(radius); if (maximum<1 || maximum>5) throw new IllegalArgumentException("1..5 targets required"); }
    }
    public record CaptureFrame(ProgramRef.TargetSet targets,int slot,TargetPoint point,Vec3 sourceOffset,
                               Vec3 preferredUp,Vec3 previousRight,Vec3 fallbackRight) {
        public CaptureFrame { Objects.requireNonNull(targets); AuthorityDescriptions.slot(slot); Objects.requireNonNull(point); Objects.requireNonNull(sourceOffset); preferredUp=preferredUp.unit(); if (previousRight!=null) previousRight=previousRight.unit(); fallbackRight=fallbackRight.unit(); }
    }
    public record SampleTarget(ProgramRef.TargetSet targets,int slot,TargetPoint point,TimeDomain clock,double tick) {
        public SampleTarget { Objects.requireNonNull(targets); AuthorityDescriptions.slot(slot); Objects.requireNonNull(point); Objects.requireNonNull(clock); Deterministic.nonnegative(tick); }
    }
    public record ResolveSurface(ProgramRef.Plan plan,SurfaceMode mode,int maxSteps,int maxQueries) {
        public ResolveSurface { Objects.requireNonNull(plan); Objects.requireNonNull(mode); if (maxSteps<1 || maxSteps>4096 || maxQueries<1 || maxQueries>512) throw new IllegalArgumentException("bounded surface query required"); }
    }
    public record Proximity(ProgramRef.Group group,Selection selection,double radius,boolean once,boolean damageBeforeTrigger) {
        public Proximity { Objects.requireNonNull(group); Objects.requireNonNull(selection); Deterministic.positive(radius); }
    }
    public record Commit(ProgramRef.Plan plan,double minimumClearance,int memberQuota,Failure onFailure,boolean allCombatPlayers) {
        public Commit { Objects.requireNonNull(plan); Deterministic.nonnegative(minimumClearance); if (memberQuota<1 || memberQuota>4096) throw new IllegalArgumentException("bounded quota required"); Objects.requireNonNull(onFailure); }
    }
    public record Cancel(String scope,String reason,boolean descendants,boolean stopEmission,boolean revokeWarnings,double fadeTicks) {
        public Cancel { if (scope==null || scope.isBlank() || reason==null || reason.isBlank()) throw new IllegalArgumentException("scope/reason required"); Deterministic.nonnegative(fadeTicks); }
    }
    public record EndOnReturn(Path path,Vec3 progressAxis,double farthestTick,double originProgress) {
        public EndOnReturn { Objects.requireNonNull(path); progressAxis=progressAxis.unit(); Deterministic.nonnegative(farthestTick); Deterministic.finite(originProgress); }
        public boolean ended(double tick) { return tick>farthestTick && path.at(tick).position().dot(progressAxis)<=originProgress; }
    }
    private static void slot(int slot) { if (slot<0 || slot>4) throw new IllegalArgumentException("slot in 0..4 required"); }
}
