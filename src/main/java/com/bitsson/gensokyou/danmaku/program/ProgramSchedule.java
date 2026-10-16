package com.bitsson.gensokyou.danmaku.program;

import java.util.*;

/** Bounded, side-effect-free interval query. Intervals are half open. */
public final class ProgramSchedule {
    private ProgramSchedule() {}

    public record Event(NodeId node, long tick, List<Long> occurrence, long ownershipEnd) {
        public Event { occurrence = List.copyOf(occurrence); }
    }

    public static List<Event> between(ProgramDefinition definition, long from, long until, int maxEvents) {
        return between(definition, new ClockAnchors(0, 0, null, null), from, until, maxEvents);
    }

    public static List<Event> between(ProgramDefinition definition, ClockAnchors clocks,
                                      long from, long until, int maxEvents) {
        if (until < from || maxEvents < 1) throw new IllegalArgumentException("invalid query window");
        Query query = new Query(from, until, maxEvents, definition.nodes());
        if (from == until) return List.of();
        if (definition.playback() instanceof PlaybackPolicy.Once) {
            long ownershipEnd = ((PlaybackPolicy.Once) definition.playback()).tailPolicy()==TailPolicy.END_AT_BOUNDARY
                    ? ProgramTime.end(definition.root(),clocks.card(),clocks) : Long.MAX_VALUE;
            query.expand(definition.root(), clocks.card(), clocks.card(), Long.MAX_VALUE, clocks, List.of(),ownershipEnd);
        } else {
            long period, phase;
            boolean cycle = definition.playback() instanceof PlaybackPolicy.Loop;
            if (definition.playback() instanceof PlaybackPolicy.Loop loop) {
                period = loop.length().ticks(); phase = 0;
            } else {
                var stream = (PlaybackPolicy.Stream) definition.playback();
                period = stream.period().ticks(); phase = stream.phase().ticks();
            }
            query.repeat(definition.root(), clocks.card(), phase, period, Long.MAX_VALUE,
                    Long.MAX_VALUE, clocks, List.of(), cycle,
                    cycle && ((PlaybackPolicy.Loop) definition.playback()).tailPolicy()==TailPolicy.END_AT_BOUNDARY,Long.MAX_VALUE);
        }
        query.events.sort(Comparator.comparingLong(Event::tick)
                .thenComparingInt(e -> query.order.get(e.node())));
        return List.copyOf(query.events);
    }

    private static final class Query {
        private final long from, until;
        private final int max;
        private long visits;
        private final long visitLimit;
        private final Map<NodeId, Integer> order = new HashMap<>();
        private final List<Event> events = new ArrayList<>();

        private Query(long from, long until, int max, List<ProgramNode> nodes) {
            this.from = from; this.until = until; this.max = max;
            visitLimit = Math.multiplyExact((long) max + 1, Math.max(1, nodes.size()) * 4L);
            for (int i = 0; i < nodes.size(); i++) order.put(nodes.get(i).id(), i);
        }

        private void expand(ProgramNode node, long base, long scopeStart, long end,
                            ClockAnchors clocks, List<Long> occurrence,long ownershipEnd) {
            if (++visits > visitLimit) throw new IllegalArgumentException("schedule work limit");
            switch (node) {
                case OperationNode op -> {
                    if (base >= scopeStart && base < end && base >= from && base < until) {
                        if (events.size() >= max) throw new IllegalArgumentException("schedule event limit");
                        events.add(new Event(op.id(), base, occurrence,ownershipEnd));
                    }
                }
                case AtNode at -> expand(at.child(), Math.addExact(clocks.origin(at.domain()), at.offset().ticks()),
                        scopeStart, end, clocks, occurrence,ownershipEnd);
                case OffsetNode offset -> expand(offset.child(), Math.addExact(base, offset.offset().ticks()),
                        scopeStart, end, clocks, occurrence,ownershipEnd);
                case SequenceNode sequence -> {
                    long cursor = base;
                    for (ProgramNode child : sequence.children()) {
                        expand(child, cursor, scopeStart, end, clocks, occurrence,ownershipEnd);
                        cursor = ProgramTime.end(child,cursor,clocks);
                    }
                }
                case RepeatNode r -> repeat(r.child(), base, 0, r.period().ticks(), r.count(), end, clocks, occurrence, false,false,ownershipEnd);
                case EveryNode e -> repeat(e.child(), base, e.phase().ticks(), e.period().ticks(), Long.MAX_VALUE,
                        Math.min(end, Math.addExact(base, e.window().ticks())), clocks, occurrence, false,false,ownershipEnd);
                case CycleNode c -> expand(c.child(), base, base, Math.min(end, Math.addExact(base, c.length().ticks())),
                        clocks.cycleAt(base), occurrence,c.tailPolicy()==TailPolicy.END_AT_BOUNDARY
                                ? Math.min(ownershipEnd,Math.addExact(base,c.length().ticks())) : ownershipEnd);
                case WaitNode ignored -> { }
                default -> {
                    for (ProgramNode child : node.children()) expand(child, base, scopeStart, end, clocks, occurrence,ownershipEnd);
                }
            }
        }

        private void repeat(ProgramNode child, long base, long phase, long period, long count, long end,
                            ClockAnchors clocks, List<Long> occurrence, boolean cycle,boolean endAtBoundary,long ownershipEnd) {
            if (count == 0) return;
            long first = Math.addExact(base, phase);
            // Each occurrence owns one [start,start+period) interval. Absolute At
            // retains its anchor, and is emitted only by the interval containing it.
            long index = from > first ? (from - first) / period : 0;
            while (index < count) {
                long tick = Math.addExact(first, Math.multiplyExact(index, period));
                if (tick >= end || tick >= until) break;
                long next = Math.addExact(tick, period);
                List<Long> path = new ArrayList<>(occurrence); path.add(index);
                expand(child, tick, tick, Math.min(end, next), cycle ? clocks.cycleAt(tick) : clocks, path,
                        endAtBoundary ? Math.min(ownershipEnd,next) : ownershipEnd);
                index++;
            }
        }
    }
}
