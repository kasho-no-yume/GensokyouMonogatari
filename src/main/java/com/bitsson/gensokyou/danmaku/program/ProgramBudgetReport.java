package com.bitsson.gensokyou.danmaku.program;

import java.util.*;

/** Exact nominal emissions in a finite window, with conservative derived/live bounds.
 * A derived lifetime bound includes the delay to the last descendant's death,
 * measured from this operation. World rejection and early collisions lower counts. */
public record ProgramBudgetReport(long from, long until, long emittedMembers, long derivedMembers,
                                  long peakBirthsPerTick, long peakLiveMembers, List<Batch> batches) {
    public ProgramBudgetReport { batches = List.copyOf(batches); }
    public record Batch(NodeId node, long birthTick, List<Long> occurrence, long members,
                         long derivedBound, long endExclusive) {
        public Batch { occurrence = List.copyOf(occurrence); }
    }

    public static ProgramBudgetReport inspect(ProgramDefinition program, OperationRegistry registry,
                                              ClockAnchors clocks, long from, long until, int maxEvents) {
        Map<NodeId, OperationType.OperationContract> contracts = new HashMap<>();
        for (ProgramNode n : program.nodes()) if (n instanceof OperationNode op) contracts.put(n.id(), registry.validate(op));
        long historyStart = Math.max(clocks.card(), Math.subtractExact(from, program.bounds().maxLifetimeTicks()));
        List<Batch> batches = new ArrayList<>();
        TreeMap<Long, Long> changes = new TreeMap<>(), births = new TreeMap<>();
        long emitted = 0, derived = 0;
        for (var event : ProgramSchedule.between(program, clocks, historyStart, until, maxEvents)) {
            var c = contracts.get(event.node());
            long members = Math.addExact(c.members(), c.derivedMembers());
            if (members == 0) continue;
            long end = Math.min(event.ownershipEnd(),Math.addExact(event.tick(), c.lifetimeTicks()));
            if (program.playback() instanceof PlaybackPolicy.Loop loop
                    && loop.tailPolicy() == TailPolicy.END_AT_BOUNDARY) {
                long cycle = Math.floorDiv(event.tick()-clocks.card(),loop.length().ticks());
                end = Math.min(end,Math.addExact(clocks.card(),Math.multiplyExact(cycle+1,loop.length().ticks())));
            }
            if (program.playback() instanceof PlaybackPolicy.Once once
                    && once.tailPolicy() == TailPolicy.END_AT_BOUNDARY) {
                end = Math.min(end,ProgramTime.end(program.root(),clocks.card(),clocks));
            }
            if (end <= from) continue;
            batches.add(new Batch(event.node(), event.tick(), event.occurrence(), c.members(), c.derivedMembers(), end));
            changes.merge(Math.max(from, event.tick()), members, Math::addExact);
            changes.merge(end, -members, Math::addExact);
            if (event.tick() >= from) {
                emitted = Math.addExact(emitted, c.members()); derived = Math.addExact(derived, c.derivedMembers());
                births.merge(event.tick(), members, Math::addExact);
            }
        }
        long live = 0, peak = 0;
        for (var e : changes.entrySet()) {
            if (e.getKey() >= until) break;
            live = Math.addExact(live, e.getValue()); peak = Math.max(peak, live);
        }
        return new ProgramBudgetReport(from, until, emitted, derived,
                births.values().stream().mapToLong(Long::longValue).max().orElse(0), peak, batches);
    }

}
