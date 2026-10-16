package com.bitsson.gensokyou.danmaku.program;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Trusted installed extension. Parameters on the wire are canonical strings;
 * the registered codec converts them into a validated, typed value before use.
 * No evaluator receives a world or connection. */
public interface OperationType<P> {
    String id();
    int schemaVersion();
    P decode(Map<String, String> fields);
    Map<String, String> encode(P parameters);
    OperationContract contract(P parameters);

    record Reference(NodeId producer, Kind kind) {
        public enum Kind { FRAME, TARGET_SET, GROUP, PLAN, SELECTION }
        public Reference {
            java.util.Objects.requireNonNull(producer);
            java.util.Objects.requireNonNull(kind);
        }
    }

    /** scope identifies the object whose channels this operation writes, not its visual lane.
     * Zero members is valid for a transform or a decision description. Lifetimes and
     * derived totals are conservative bounds supplied by the extension, never clamped.
     * lifetimeTicks covers the entire descendant tree from this operation's tick,
     * including delayed births. derivedMembers counts all generations, not only children. */
    record OperationContract(String scope, Set<Channel> writes, Set<Reference.Kind> produces,
                             List<Reference> reads, long members, long lifetimeTicks,
                             long derivedMembers, int generations) {
        public OperationContract {
            if (scope == null || scope.isBlank()) throw new IllegalArgumentException("scope required");
            writes = Set.copyOf(writes);
            produces = Set.copyOf(produces);
            reads = List.copyOf(reads);
            if (members < 0 || lifetimeTicks < 0 || derivedMembers < 0 || generations < 0
                    || (members > 0 || derivedMembers > 0) && lifetimeTicks == 0) {
                throw new IllegalArgumentException("finite count, generation and lifetime bounds required");
            }
        }
    }
}
