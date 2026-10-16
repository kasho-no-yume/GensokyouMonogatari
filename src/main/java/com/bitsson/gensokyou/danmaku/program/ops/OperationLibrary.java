package com.bitsson.gensokyou.danmaku.program.ops;

import com.bitsson.gensokyou.danmaku.program.*;
import com.bitsson.gensokyou.danmaku.program.math.*;
import java.util.*;

/** Installed pure extension types. Applications freeze the data/type registries together. */
public final class OperationLibrary {
    private OperationLibrary() {}
    public record PathParameters(String scope,long lifetimeTicks,PathSpec path) {
        public PathParameters {
            OperationLibrary.scope(scope); lifetime(lifetimeTicks); java.util.Objects.requireNonNull(path);
            Deterministic.nonnegative(path.evaluator().radiusBound(lifetimeTicks));
        }
    }
    /** Explicit representation of finite segments (the evaluated Segments class caches prefixes). */
    public record PathSpec(Path simple,List<Paths.Segment> segments,Paths.Tail tail) {
        public PathSpec {
            segments=List.copyOf(segments); java.util.Objects.requireNonNull(tail);
            if ((simple==null)==segments.isEmpty()) throw new IllegalArgumentException("choose simple path or finite segments");
            if (!segments.isEmpty()) new Paths.Segments(segments,tail);
        }
        public static PathSpec of(Path path) { return new PathSpec(path,List.of(),Paths.Tail.END); }
        public static PathSpec segments(List<Paths.Segment> segments,Paths.Tail tail) { return new PathSpec(null,segments,tail); }
        public Path evaluator() { return simple==null?new Paths.Segments(segments,tail):simple; }
    }
    public record PatternParameters(String scope,long lifetimeTicks,Pattern pattern,Deterministic.Seed seed) {
        public PatternParameters { OperationLibrary.scope(scope); lifetime(lifetimeTicks); java.util.Objects.requireNonNull(pattern); java.util.Objects.requireNonNull(seed); }
    }
    private static void scope(String scope) { if (scope==null || scope.isBlank()) throw new IllegalArgumentException("scope required"); }
    private static void lifetime(long ticks) { if (ticks<1) throw new IllegalArgumentException("finite positive lifetime required"); }
    public static OperationDataCodec dataRegistry() {
        OperationDataCodec codec=new OperationDataCodec();
        codec.register("math:vec3",Vec3.class).register("math:frame",Frame.class).register("math:curve",ScalarCurve.class)
                .register("math:key",ScalarCurve.Key.class).register("math:seed",Deterministic.Seed.class);
        codec.register("path:hold",Paths.Hold.class).register("path:linear",Paths.Linear.class).register("path:speed_curve",Paths.SpeedCurve.class)
                .register("path:arc",Paths.Arc.class).register("path:segment",Paths.Segment.class).register("path:segments",PathSpec.class)
                .register("path:bezier",Paths.Bezier.class).register("path:wave",Paths.WaveOffset.class).register("path:rebase",Paths.Rebase.class);
        codec.register("transform:rotate",Transforms.Rotate.class).register("transform:orbit",Transforms.Orbit.class)
                .register("transform:scale",Transforms.Scale.class).register("transform:breathe",Transforms.Breathe.class)
                .register("transform:mirror",Transforms.Mirror.class).register("transform:compose",Transforms.Compose.class).register("transform:translate",Transforms.Translate.class);
        codec.register("pattern:style",Pattern.Style.class).register("pattern:point",Patterns.Point.class).register("pattern:tagged",Patterns.Tagged.class)
                .register("pattern:union",Patterns.Union.class).register("pattern:ring",Patterns.Ring.class).register("pattern:fan",Patterns.Fan.class)
                .register("pattern:angular_grid",Patterns.AngularGrid.class).register("pattern:parallel_grid",Patterns.ParallelGrid.class)
                .register("pattern:axis_nodes",Patterns.AxisNodes.class).register("pattern:surface_rays",Patterns.SurfaceRays.class)
                .register("pattern:rose",Patterns.Rose.class).register("pattern:spiral",Patterns.Spiral.class)
                .register("pattern:cone_scatter",Patterns.ConeScatter.class).register("pattern:volume_scatter",Patterns.VolumeScatter.class)
                .register("pattern:sphere_surface",Patterns.SphereSurface.class).register("pattern:sphere_directions",Patterns.SphereDirections.class)
                .register("pattern:helix_column",Patterns.HelixColumn.class).register("pattern:radial",Patterns.Radial.class)
                .register("pattern:shell",Patterns.Shell.class).register("pattern:cage",Patterns.Cage.class)
                .register("pattern:around_point",Patterns.AroundPoint.class).register("pattern:lattice",Patterns.Lattice.class).register("pattern:rain",Patterns.Rain.class);
        codec.register("selection:range",Selection.IndexRange.class).register("selection:modulo",Selection.IndexModulo.class)
                .register("selection:tag",Selection.Tag.class).register("selection:target_slot",Selection.TargetSlot.class).register("selection:all_of",Selection.AllOf.class);
        codec.register("program:node_id",NodeId.class).register("program:group_ref",ProgramRef.Group.class).register("program:frame_ref",ProgramRef.Frame.class)
                .register("pattern:member",Pattern.Member.class).register("motion:layer",MemberMotion.Layer.class)
                .register("program:target_set_ref",ProgramRef.TargetSet.class).register("program:plan_ref",ProgramRef.Plan.class)
                .register("motion:ownership",MemberMotion.Ownership.class).register("motion:attached",MemberMotion.Attached.class).register("motion:detached",MemberMotion.Detached.class);
        codec.register("presentation:window",Presentation.Window.class).register("presentation:visibility_cycle",Presentation.VisibilityCycle.class)
                .register("presentation:flash",Presentation.Flash.class).register("presentation:sound_cue",Presentation.SoundCue.class)
                .register("presentation:fade",Presentation.Fade.class).register("presentation:billboard",Presentation.Billboard.class)
                .register("presentation:telegraph",Presentation.Telegraph.class).register("presentation:profile",Presentation.Profile.class).register("presentation:laser_window",Presentation.LaserWindow.class);
        codec.register("action:pose",Actions.Pose.class).register("action:member_pose",Actions.MemberPose.class).register("action:group_pose",Actions.GroupPose.class)
                .register("action:external_entity_source",Actions.ExternalEntitySource.class).register("action:binding",Actions.Binding.class)
                .register("action:child_program",Actions.ChildProgram.class).register("action:spawn_child",Actions.SpawnChild.class)
                .register("action:child_action",Actions.ChildAction.class).register("action:at_age",Actions.AtAge.class)
                .register("action:at_path_segment_end",Actions.AtPathSegmentEnd.class)
                .register("action:emit_along_path",Actions.EmitAlongPath.class).register("action:replace_members",Actions.ReplaceMembers.class)
                .register("action:split_at",Actions.SplitAt.class).register("action:create_emitter",Actions.CreateEmitter.class)
                .register("action:segment_end",Actions.SegmentEndTrigger.class).register("action:stagger",Actions.Stagger.class)
                .register("action:phase_offset",Actions.PhaseOffset.class).register("action:seed_direction",Actions.SeedDirection.class)
                .register("action:lifecycle",Actions.Lifecycle.class).register("action:decision",Actions.Decision.class)
                .register("action:create_group",Actions.CreateGroup.class).register("action:prepare_emission",Actions.PrepareEmission.class)
                .register("action:for_each_target",Actions.ForEachTarget.class).register("action:emit",Actions.Emit.class);
        codec.register("authority:capture_targets",AuthorityDescriptions.CaptureTargets.class)
                .register("authority:capture_frame",AuthorityDescriptions.CaptureFrame.class)
                .register("authority:sample_target",AuthorityDescriptions.SampleTarget.class)
                .register("authority:resolve_surface",AuthorityDescriptions.ResolveSurface.class)
                .register("authority:proximity",AuthorityDescriptions.Proximity.class)
                .register("authority:commit",AuthorityDescriptions.Commit.class)
                .register("authority:cancel",AuthorityDescriptions.Cancel.class)
                .register("authority:end_on_return",AuthorityDescriptions.EndOnReturn.class);
        return codec.register("operation:path_parameters",PathParameters.class).register("operation:pattern_parameters",PatternParameters.class);
    }
    public static OperationType<PathParameters> pathType(OperationDataCodec codec) {
        return new RegisteredType<>("gensokyou:path",PathParameters.class,codec,p->new OperationType.OperationContract(p.scope(),Set.of(Channel.POSITION),Set.of(),List.of(),0,p.lifetimeTicks(),0,0));
    }
    /** Child tree bounds are validated by ChildProgram rather than supplied by a caller callback. */
    public static OperationType<Actions.ChildProgram> childProgramType(OperationDataCodec codec) {
        return new RegisteredType<>("gensokyou:child_program",Actions.ChildProgram.class,codec,
                p->new OperationType.OperationContract("child_program",Set.of(Channel.EMISSION),Set.of(),List.of(),
                        p.pattern().countBound(),p.treeLifetimeBound(),p.descendantBound(),p.maxGenerations()));
    }
    /** Descriptions participate in the same validation/hash envelope. Authority execution is stage 3. */
    public static <P extends Record> OperationType<P> descriptionType(String id,Class<P> parameters,
            OperationDataCodec codec,java.util.function.Function<P,OperationType.OperationContract> contract) {
        return new RegisteredType<>(id,parameters,codec,contract);
    }
    public static OperationType<PatternParameters> patternType(OperationDataCodec codec) {
        return new RegisteredType<>("gensokyou:pattern",PatternParameters.class,codec,p->{
            List<Pattern.Member> members=p.pattern().generate(p.seed());
            if (members.size()>p.pattern().countBound() || members.size()>512 || members.stream().map(Pattern.Member::index).distinct().count()!=members.size()) throw new IllegalArgumentException("invalid pattern expansion");
            for (Pattern.Member m:members) if (m.position().length()>p.pattern().radiusBound()+1e-8) throw new IllegalArgumentException("pattern exceeds bounds");
            return new OperationType.OperationContract(p.scope(),Set.of(Channel.EMISSION),Set.of(),List.of(),members.size(),p.lifetimeTicks(),0,0);
        });
    }
    public record RegisteredType<P extends Record>(String id,Class<P> parameters,OperationDataCodec codec,
                java.util.function.Function<P,OperationType.OperationContract> contracts) implements OperationType<P> {
        public int schemaVersion() { return 1; }
        public P decode(Map<String,String> fields) { return codec.decode(fields,parameters); }
        public Map<String,String> encode(P value) { return codec.encode(value); }
        public OperationContract contract(P value) { return contracts.apply(value); }
        public OperationNode node(NodeId id,TickSpan duration,P value) { return new OperationNode(id,this.id,1,duration,contract(value).writes(),encode(value)); }
    }
}
