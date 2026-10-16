package com.bitsson.gensokyou.danmaku.program;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Length-prefixed canonical binary data. Limits apply before collection allocation
 * and before recursive descent. Decoded definitions always pass through the compiler. */
public final class ProgramCodec {
    public static final int SCHEMA = 1;
    private ProgramCodec() {}

    public static byte[] encode(String id, PlaybackPolicy playback, ProgramNode root, ProgramLimits limits) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(new FilterOutputStream(bytes) {
                private int written;
                @Override public void write(int value) throws IOException {
                    if (++written > limits.maxPayloadBytes()) throw new IOException("payload limit");
                    this.out.write(value);
                }
                @Override public void write(byte[] data, int offset, int length) throws IOException {
                    if (length > limits.maxPayloadBytes() - written) throw new IOException("payload limit");
                    written += length;
                    this.out.write(data, offset, length);
                }
            });
            out.writeInt(SCHEMA);
            string(out, id, limits);
            if (playback instanceof PlaybackPolicy.Once once) { out.writeByte(0); out.writeByte(once.tailPolicy().ordinal()); }
            else if (playback instanceof PlaybackPolicy.Loop loop) { out.writeByte(1); out.writeLong(loop.length().ticks()); out.writeByte(loop.tailPolicy().ordinal()); }
            else if (playback instanceof PlaybackPolicy.Stream stream) {
                out.writeByte(2); out.writeLong(stream.period().ticks()); out.writeLong(stream.phase().ticks());
            } else throw new IllegalArgumentException("playback required");
            writeNode(out, root, limits, 1, new int[1]);
            out.flush();
            return bytes.toByteArray();
        } catch (IOException e) { throw new IllegalArgumentException(e.getMessage(), e); }
    }

    public static CompileResult decode(byte[] bytes, ProgramLimits limits, OperationRegistry registry) {
        if (bytes.length > limits.maxPayloadBytes()) throw new IllegalArgumentException("payload limit");
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
            if (in.readInt() != SCHEMA) throw new IOException("unsupported definition schema");
            String id = string(in, limits);
            PlaybackPolicy playback = switch (in.readUnsignedByte()) {
                case 0 -> new PlaybackPolicy.Once(value(TailPolicy.values(),in.readUnsignedByte()));
                case 1 -> new PlaybackPolicy.Loop(span(in),value(TailPolicy.values(),in.readUnsignedByte()));
                case 2 -> new PlaybackPolicy.Stream(span(in), span(in));
                default -> throw new IOException("unknown playback");
            };
            ProgramNode root = readNode(in, limits, 1, new int[1]);
            if (in.available() != 0) throw new IOException("trailing definition bytes");
            if (!Arrays.equals(bytes, encode(id, playback, root, limits))) throw new IOException("noncanonical definition");
            return ProgramCompiler.compile(id, SCHEMA, playback, root, limits, registry);
        } catch (IOException e) { throw new IllegalArgumentException(e.getMessage(), e); }
    }

    private static void writeNode(DataOutputStream out, ProgramNode n, ProgramLimits l, int depth, int[] count) throws IOException {
        guard(l, depth, count);
        int tag = n instanceof OperationNode ? 0 : n instanceof AtNode ? 1 : n instanceof SequenceNode ? 2
                : n instanceof ParallelNode ? 3 : n instanceof WaitNode ? 4 : n instanceof RepeatNode ? 5
                : n instanceof CycleNode ? 6 : n instanceof EveryNode ? 7 : n instanceof ComposeNode ? 8 : 9;
        out.writeByte(tag); out.writeLong(n.id().value());
        switch (n) {
            case OperationNode op -> {
                string(out, op.operationId(), l); out.writeInt(op.schemaVersion()); out.writeLong(op.duration().ticks());
                int mask = 0; for (Channel c : op.writes()) mask |= 1 << c.ordinal(); out.writeInt(mask);
                if (op.parameters().size() > l.maxNodes()) throw new IOException("parameter count limit");
                out.writeInt(op.parameters().size());
                for (var e : new TreeMap<>(op.parameters()).entrySet()) { string(out, e.getKey(), l); string(out, e.getValue(), l); }
            }
            case AtNode at -> { out.writeByte(at.domain().ordinal()); out.writeLong(at.offset().ticks()); }
            case OffsetNode offset -> out.writeLong(offset.offset().ticks());
            case WaitNode wait -> out.writeLong(wait.duration().ticks());
            case RepeatNode repeat -> { out.writeInt(repeat.count()); out.writeLong(repeat.period().ticks()); }
            case CycleNode cycle -> { out.writeLong(cycle.length().ticks()); out.writeByte(cycle.tailPolicy().ordinal()); }
            case EveryNode every -> { out.writeLong(every.period().ticks()); out.writeLong(every.phase().ticks()); out.writeLong(every.window().ticks()); }
            case ComposeNode compose -> { out.writeByte(compose.channel().ordinal()); out.writeByte(compose.mode().ordinal()); }
            default -> { }
        }
        out.writeInt(n.children().size());
        for (ProgramNode child : n.children()) writeNode(out, child, l, depth + 1, count);
    }

    private static ProgramNode readNode(DataInputStream in, ProgramLimits l, int depth, int[] count) throws IOException {
        guard(l, depth, count);
        int tag = in.readUnsignedByte(); NodeId id = new NodeId(in.readLong());
        String type = null; int version = 0; TickSpan duration = null, period = null, phase = null;
        int repetitions = 0; TimeDomain domain = null; Channel channel = null; ComposeNode.Mode mode = null;
        TailPolicy tail = null;
        Set<Channel> writes = EnumSet.noneOf(Channel.class); Map<String,String> params = new TreeMap<>();
        switch (tag) {
            case 0 -> {
                type = string(in,l); version = in.readInt(); duration = span(in);
                int mask = in.readInt(); if ((mask >>> Channel.values().length) != 0) throw new IOException("unknown channel bits");
                for (Channel c : Channel.values()) if ((mask & 1 << c.ordinal()) != 0) writes.add(c);
                int size = size(in, l.maxNodes());
                for (int i=0;i<size;i++) if (params.put(string(in,l),string(in,l)) != null) throw new IOException("duplicate parameter");
            }
            case 1 -> { domain = value(TimeDomain.values(), in.readUnsignedByte()); duration = span(in); }
            case 2, 3 -> { }
            case 4, 9 -> duration = span(in);
            case 6 -> { duration = span(in); tail = value(TailPolicy.values(),in.readUnsignedByte()); }
            case 5 -> { repetitions = in.readInt(); period = span(in); }
            case 7 -> { period = span(in); phase = span(in); duration = span(in); }
            case 8 -> { channel = value(Channel.values(), in.readUnsignedByte()); mode = value(ComposeNode.Mode.values(), in.readUnsignedByte()); }
            default -> throw new IOException("unknown node tag");
        }
        int size = size(in, l.maxNodes() - count[0]);
        if ((tag==0 || tag==4) && size!=0 || (tag==1 || tag==5 || tag==6 || tag==7 || tag==9) && size!=1) throw new IOException("invalid node arity");
        List<ProgramNode> children = new ArrayList<>();
        for(int i=0;i<size;i++) children.add(readNode(in,l,depth+1,count));
        return switch(tag) {
            case 0 -> new OperationNode(id,type,version,duration,writes,params);
            case 1 -> new AtNode(id,domain,duration,children.getFirst());
            case 2 -> new SequenceNode(id,children);
            case 3 -> new ParallelNode(id,children);
            case 4 -> new WaitNode(id,duration);
            case 5 -> new RepeatNode(id,repetitions,period,children.getFirst());
            case 6 -> new CycleNode(id,duration,tail,children.getFirst());
            case 7 -> new EveryNode(id,period,phase,duration,children.getFirst());
            case 8 -> new ComposeNode(id,channel,mode,children);
            case 9 -> new OffsetNode(id,duration,children.getFirst());
            default -> throw new IOException("unknown node tag");
        };
    }

    private static void guard(ProgramLimits l,int depth,int[] count) throws IOException {
        if(depth>l.maxDepth() || ++count[0]>l.maxNodes()) throw new IOException("node/depth limit");
    }
    private static TickSpan span(DataInputStream in) throws IOException { return new TickSpan(in.readLong()); }
    private static <T> T value(T[] values,int index) throws IOException {
        if(index>=values.length) throw new IOException("unknown enum value"); return values[index];
    }
    private static int size(DataInputStream in,int max) throws IOException {
        int n=in.readInt(); if(n<0 || n>max) throw new IOException("length limit"); return n;
    }
    private static void string(DataOutputStream out,String s,ProgramLimits l) throws IOException {
        if(s.length()>l.maxStringBytes()) throw new IOException("string limit");
        byte[] bytes=s.getBytes(StandardCharsets.UTF_8);
        if(bytes.length>l.maxStringBytes()) throw new IOException("string limit");
        out.writeInt(bytes.length); out.write(bytes);
    }
    private static String string(DataInputStream in,ProgramLimits l) throws IOException {
        int n=size(in,l.maxStringBytes()); byte[] data=in.readNBytes(n);
        if(data.length!=n) throw new EOFException(); return new String(data,StandardCharsets.UTF_8);
    }
}
