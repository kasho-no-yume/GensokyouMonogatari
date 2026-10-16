package com.bitsson.gensokyou.danmaku.program.server;

import com.bitsson.gensokyou.danmaku.program.*;
import com.bitsson.gensokyou.danmaku.program.math.*;
import com.bitsson.gensokyou.danmaku.program.ops.*;
import java.util.*;

/** Standard executable emission operation. Runtime ownership is supplied by an installed binding. */
public final class ServerOperations {
    private ServerOperations() {}
    public record Emission(String scope,Actions.ChildProgram members,Actions.Binding binding,
                           Actions.Pose source,Deterministic.Seed seed,AuthorityDescriptions.Commit policy,
                           long warningTicks) {
        public Emission {
            if (scope==null || scope.isBlank() || warningTicks<0) throw new IllegalArgumentException("emission scope/lead required");
            Objects.requireNonNull(members); Objects.requireNonNull(binding); Objects.requireNonNull(source); Objects.requireNonNull(seed); Objects.requireNonNull(policy);
        }
    }
    public record Binding(DanmakuWorldRuntime.Encounter encounter,long card,boolean background) {}
    public static OperationType<Emission> emissionType(OperationDataCodec codec) {
        return OperationLibrary.descriptionType("gensokyou:server_emission",Emission.class,codec,p->
                new OperationType.OperationContract(p.scope(),Set.of(Channel.EMISSION),Set.of(),List.of(),
                        p.members().pattern().countBound(),Math.addExact(p.warningTicks(),p.members().treeLifetimeBound()),
                        p.members().descendantBound(),p.members().maxGenerations()));
    }
    public static OperationDataCodec dataRegistry() {
        return OperationLibrary.dataRegistry().register("server:emission",Emission.class);
    }
    public static void install(ProgramExecution execution,OperationType<Emission> type,Binding binding) {
        execution.register(type,new ProgramExecution.Handler<>() {
            public DanmakuWorldRuntime.Phase phase() { return DanmakuWorldRuntime.Phase.PREPARE; }
            public void execute(DanmakuWorldRuntime runtime,ProgramSchedule.Event event,Emission parameters) {
                if (binding==null) throw new IllegalStateException("installed program context required");
                emit(runtime,event,parameters,binding,0);
            }
            @Override public void execute(DanmakuWorldRuntime runtime,ProgramExecution.Context context,ProgramSchedule.Event event,Emission parameters) {
                long cycleOrigin=context.anchors().cycle();
                if (context.playback() instanceof PlaybackPolicy.Loop loop && !event.occurrence().isEmpty())
                    cycleOrigin=Math.addExact(context.anchors().card(),Math.multiplyExact(event.occurrence().getFirst(),loop.length().ticks()));
                emit(runtime,event,parameters,new Binding(context.encounter(),context.card(),context.background()),context.instance(),context.anchors().card(),cycleOrigin);
            }
            private void emit(DanmakuWorldRuntime runtime,ProgramSchedule.Event event,Emission parameters,Binding binding,long instance) {
                emit(runtime,event,parameters,binding,instance,event.tick(),event.tick());
            }
            private void emit(DanmakuWorldRuntime runtime,ProgramSchedule.Event event,Emission parameters,Binding binding,long instance,long cardOrigin,long cycleOrigin) {
                if (!runtime.active(binding.encounter())) return;
                long cycle=event.occurrence().isEmpty()?0:event.occurrence().getFirst();
                long group=runtime.createGroup(binding.encounter(),binding.card(),cycle,null,-1,binding.background(),
                        event.ownershipEnd()==Long.MAX_VALUE,parameters.source().frame(),List.of());
                runtime.clocks(group,new DanmakuWorldRuntime.Clocks(cardOrigin,cycleOrigin,event.tick()));
                var seed=parameters.seed().child(Math.toIntExact(instance)).child((int)event.node().value());
                for (long occurrence:event.occurrence()) seed=seed.child(Math.toIntExact(occurrence));
                long birth=Math.addExact(event.tick(),parameters.warningTicks());
                EmissionPlan plan=runtime.prepare(group,birth,parameters.source(),parameters.members(),parameters.binding(),seed);
                runtime.commitAtBirth(plan,parameters.policy(),event.ownershipEnd(),true);
            }
        });
    }
    public static void install(ProgramExecution execution,OperationType<Emission> type) { install(execution,type,null); }
}
