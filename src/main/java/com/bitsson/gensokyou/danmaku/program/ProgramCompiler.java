package com.bitsson.gensokyou.danmaku.program;

import java.security.MessageDigest;
import java.util.*;

/** World-free compilation. Structural limits are checked before duration/bounds recursion. */
public final class ProgramCompiler {
    private ProgramCompiler() {}

    public static CompileResult compile(String id, int schemaVersion, PlaybackPolicy playback,
                                        ProgramNode root, ProgramLimits limits, OperationRegistry registry) {
        List<CompileDiagnostic> errors = new ArrayList<>();
        try {
            if (schemaVersion != ProgramCodec.SCHEMA) throw new IllegalArgumentException("unsupported definition schema");
            if (id == null || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IllegalArgumentException("invalid program id");
            byte[] bytes = ProgramCodec.encode(id, playback, root, limits);
            List<ProgramNode> nodes = new ArrayList<>();
            Map<NodeId, OperationType.OperationContract> contracts = new LinkedHashMap<>();
            collect(root, nodes, new HashSet<>(), contracts, registry);
            validateTime(root);
            operationOccurrences(root);
            if (playback instanceof PlaybackPolicy.Loop loop && root.duration().ticks() > loop.length().ticks()) {
                throw new IllegalArgumentException("program exceeds loop length");
            }
            validateReferences(root, contracts);
            validateChannels(root, contracts);
            nodes = topological(nodes, contracts);
            long batch = contracts.values().stream().mapToLong(OperationType.OperationContract::members).max().orElse(0);
            long derived = contracts.values().stream().mapToLong(OperationType.OperationContract::derivedMembers).max().orElse(0);
            if (batch > limits.maxBatchMembers() || derived > limits.maxDerivedMembers()
                    || contracts.values().stream().anyMatch(c -> c.generations() > limits.maxDepth())) {
                throw new IllegalArgumentException("member/derivation limit");
            }
            long life = contracts.values().stream().mapToLong(OperationType.OperationContract::lifetimeTicks).max().orElse(0);
            ProgramBounds bounds = new ProgramBounds(nodes.size(), batch, derived, life, depth(root), root.duration().ticks());
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            return new CompileResult(Optional.of(new ProgramDefinition(id, schemaVersion, playback, root, nodes, bounds, hash)), errors);
        } catch (IllegalArgumentException | IllegalStateException | ArithmeticException e) {
            errors.add(new CompileDiagnostic(CompileDiagnostic.Severity.ERROR, CompileDiagnostic.Code.INVALID_PROGRAM,
                    root == null ? null : root.id(), e.getMessage()));
            return new CompileResult(Optional.empty(), errors);
        } catch (java.security.NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }

    /** Checked finite expansion bound, useful before allocating offline query results. */
    public static long operationOccurrences(ProgramNode node) {
        if (node instanceof OperationNode) return 1;
        if (node instanceof RepeatNode r) return Math.multiplyExact(r.count(),operationOccurrences(r.child()));
        if (node instanceof EveryNode e) {
            long count=e.window().ticks()<=e.phase().ticks() ? 0 : 1+(e.window().ticks()-1-e.phase().ticks())/e.period().ticks();
            return Math.multiplyExact(count,operationOccurrences(e.child()));
        }
        long count=0;
        for(ProgramNode child:node.children()) count=Math.addExact(count,operationOccurrences(child));
        return count;
    }

    private static void collect(ProgramNode node,List<ProgramNode> nodes,Set<NodeId> seen,
                                Map<NodeId,OperationType.OperationContract> contracts,OperationRegistry registry) {
        if (!seen.add(node.id())) throw new IllegalArgumentException("duplicate node " + node.id());
        nodes.add(node);
        if (node instanceof OperationNode op) contracts.put(op.id(), registry.validate(op));
        for (ProgramNode child:node.children()) collect(child,nodes,seen,contracts,registry);
    }

    private static void validateTime(ProgramNode node) {
        if (node instanceof RepeatNode r && r.child().duration().ticks() > r.period().ticks()) throw new IllegalArgumentException("repeat body exceeds period " + r.id());
        if (node instanceof CycleNode c && c.child().duration().ticks() > c.length().ticks()) throw new IllegalArgumentException("cycle body exceeds length " + c.id());
        if (node instanceof EveryNode e && e.child().duration().ticks() > e.period().ticks()) throw new IllegalArgumentException("every body exceeds period " + e.id());
        if (node instanceof RepeatNode r && r.count() > 0) {
            Math.addExact(Math.multiplyExact(r.count()-1L,r.period().ticks()),r.child().duration().ticks());
        }
        for (ProgramNode child:node.children()) validateTime(child);
    }

    private static void validateReferences(ProgramNode root,Map<NodeId,OperationType.OperationContract> contracts) {
        Map<NodeId,List<NodeId>> scopes = new HashMap<>(); scopes(root,List.of(),scopes);
        Map<NodeId, Timing> timing = new HashMap<>();
        timing(root, new Timing(TimeDomain.CARD, 0, 0), timing);
        for(var entry:contracts.entrySet()) for(var ref:entry.getValue().reads()) {
            var producer=contracts.get(ref.producer());
            if(producer==null || !producer.produces().contains(ref.kind())) throw new IllegalArgumentException("invalid typed reference " + ref + " at " + entry.getKey());
            List<NodeId> from=scopes.get(ref.producer()), to=scopes.get(entry.getKey());
            if(from.size()>to.size() || !to.subList(0,from.size()).equals(from)) throw new IllegalArgumentException("reference escapes repeated scope " + ref);
            Timing made = timing.get(ref.producer()), used = timing.get(entry.getKey());
            boolean sameOccurrence = from.equals(to);
            if (made.domain() != used.domain() || (sameOccurrence ? made.earliest() : made.latest()) > used.earliest()) {
                throw new IllegalArgumentException("reference is not available before use " + ref + " at " + entry.getKey());
            }
            if (producer.lifetimeTicks() > 0
                    && (sameOccurrence ? used.earliest() : used.latest()) >= Math.addExact(made.earliest(), producer.lifetimeTicks())) {
                throw new IllegalArgumentException("reference may expire before use " + ref);
            }
        }
        Set<NodeId> visited=new HashSet<>(), visiting=new HashSet<>();
        for(NodeId id:contracts.keySet()) visit(id,contracts,visited,visiting);
    }

    private record Timing(TimeDomain domain, long earliest, long latest) {
        Timing plus(long value) { return new Timing(domain,Math.addExact(earliest,value),Math.addExact(latest,value)); }
    }
    private static Timing timing(ProgramNode node, Timing start, Map<NodeId,Timing> out) {
        out.put(node.id(), start);
        switch (node) {
            case AtNode at -> {
                return timing(at.child(),new Timing(at.domain(),at.offset().ticks(),at.offset().ticks()),out);
            }
            case OffsetNode offset -> { return timing(offset.child(),start.plus(offset.offset().ticks()),out); }
            case SequenceNode sequence -> {
                Timing cursor=start;
                for(ProgramNode child:sequence.children()) cursor=timing(child,cursor,out);
                return cursor;
            }
            case RepeatNode repeat -> {
                long shift=repeat.count()==0 ? 0 : Math.multiplyExact(repeat.count()-1L,repeat.period().ticks());
                timing(repeat.child(),new Timing(start.domain(),start.earliest(),Math.addExact(start.latest(),shift)),out);
            }
            case EveryNode every -> {
                long count=every.window().ticks()<=every.phase().ticks() ? 0
                        : 1+(every.window().ticks()-1-every.phase().ticks())/every.period().ticks();
                Timing first=start.plus(every.phase().ticks());
                timing(every.child(),new Timing(first.domain(),first.earliest(),Math.addExact(first.latest(),
                        count==0 ? 0 : Math.multiplyExact(count-1,every.period().ticks()))),out);
            }
            case CycleNode cycle -> {
                timing(cycle.child(),new Timing(TimeDomain.CYCLE,0,0),out);
            }
            default -> { for(ProgramNode child:node.children()) timing(child,start,out); }
        }
        return start.plus(node.duration().ticks());
    }

    private static void scopes(ProgramNode n,List<NodeId> path,Map<NodeId,List<NodeId>> out) {
        out.put(n.id(),path);
        List<NodeId> next=path;
        if(n instanceof RepeatNode || n instanceof EveryNode || n instanceof CycleNode) {
            next=new ArrayList<>(path); next.add(n.id()); next=List.copyOf(next);
        }
        for(ProgramNode child:n.children()) scopes(child,next,out);
    }
    private static void visit(NodeId id,Map<NodeId,OperationType.OperationContract> contracts,Set<NodeId> visited,Set<NodeId> visiting) {
        if(visited.contains(id)) return;
        if(!visiting.add(id)) throw new IllegalArgumentException("dependency cycle at " + id);
        for(var ref:contracts.get(id).reads()) visit(ref.producer(),contracts,visited,visiting);
        visiting.remove(id); visited.add(id);
    }

    private static void validateChannels(ProgramNode node,Map<NodeId,OperationType.OperationContract> contracts) {
        if(node instanceof ParallelNode) {
            List<WriteWindow> used=new ArrayList<>();
            for(ProgramNode child:node.children()) {
                List<WriteWindow> writes=new ArrayList<>(); windows(child,0,TimeDomain.CARD,contracts,writes);
                for(WriteWindow write:writes) for(WriteWindow prior:used) {
                    if(write.key().equals(prior.key()) && (write.domain()!=prior.domain()
                            || write.start()<prior.end() && prior.start()<write.end())) {
                        throw new IllegalArgumentException("parallel channel conflict " + write.key() + " at " + child.id());
                    }
                }
                used.addAll(writes);
            }
        }
        if(node instanceof ComposeNode) {
            Set<String> scopes=new HashSet<>(); gatherScopes(node,contracts,scopes);
            if(scopes.size()!=1) throw new IllegalArgumentException("composition must address one scope");
        }
        for(ProgramNode child:node.children()) validateChannels(child,contracts);
    }
    private record WriteWindow(String key,TimeDomain domain,long start,long end) {}
    private static void windows(ProgramNode n,long start,TimeDomain domain,
                                Map<NodeId,OperationType.OperationContract> contracts,List<WriteWindow> out) {
        if(n instanceof OperationNode op) {
            var c=contracts.get(n.id());
            for(Channel channel:c.writes()) out.add(new WriteWindow(c.scope()+":"+channel,domain,start,
                    Math.addExact(start,Math.max(1,op.duration().ticks()))));
        } else if(n instanceof AtNode at) windows(at.child(),at.offset().ticks(),at.domain(),contracts,out);
        else if(n instanceof OffsetNode offset) windows(offset.child(),Math.addExact(start,offset.offset().ticks()),domain,contracts,out);
        else if(n instanceof SequenceNode sequence) {
            long cursor=start;
            for(ProgramNode child:sequence.children()) {
                windows(child,cursor,domain,contracts,out);
                cursor=child instanceof AtNode at ? Math.addExact(at.offset().ticks(),at.child().duration().ticks())
                        : Math.addExact(cursor,child.duration().ticks());
            }
        } else if(n instanceof RepeatNode || n instanceof EveryNode) {
            // Conservative interval envelope avoids unbounded LCM expansion.
            Set<String> keys=new HashSet<>(); gatherWrites(n,contracts,keys);
            for(String key:keys) out.add(new WriteWindow(key,domain,start,Math.addExact(start,n.duration().ticks())));
        } else for(ProgramNode child:n.children()) windows(child,start,domain,contracts,out);
    }
    private static void gatherWrites(ProgramNode n,Map<NodeId,OperationType.OperationContract> c,Set<String> out) {
        if(n instanceof OperationNode) for(Channel channel:c.get(n.id()).writes()) out.add(c.get(n.id()).scope()+":"+channel);
        for(ProgramNode child:n.children()) gatherWrites(child,c,out);
    }
    private static void gatherScopes(ProgramNode n,Map<NodeId,OperationType.OperationContract> c,Set<String> out) {
        if(n instanceof OperationNode) out.add(c.get(n.id()).scope());
        for(ProgramNode child:n.children()) gatherScopes(child,c,out);
    }
    private static int depth(ProgramNode n) { return 1+n.children().stream().mapToInt(ProgramCompiler::depth).max().orElse(0); }

    private static List<ProgramNode> topological(List<ProgramNode> nodes, Map<NodeId,OperationType.OperationContract> contracts) {
        Map<NodeId,ProgramNode> byId = new HashMap<>();
        for (ProgramNode node : nodes) byId.put(node.id(), node);
        List<ProgramNode> ordered = new ArrayList<>(); Set<NodeId> added = new HashSet<>();
        for (ProgramNode node : nodes) order(node, byId, contracts, added, ordered);
        return ordered;
    }
    private static void order(ProgramNode node, Map<NodeId,ProgramNode> byId,
                              Map<NodeId,OperationType.OperationContract> contracts,
                              Set<NodeId> added, List<ProgramNode> ordered) {
        if (added.contains(node.id())) return;
        var contract = contracts.get(node.id());
        if (contract != null) for (var ref : contract.reads()) order(byId.get(ref.producer()),byId,contracts,added,ordered);
        added.add(node.id()); ordered.add(node);
    }
}
