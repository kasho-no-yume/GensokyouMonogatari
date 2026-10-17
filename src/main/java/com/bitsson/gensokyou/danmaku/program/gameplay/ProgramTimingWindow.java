package com.bitsson.gensokyou.danmaku.program.gameplay;

import java.util.Arrays;

/** Fixed-memory recent latency window; sorting happens only when diagnostics are requested. */
public final class ProgramTimingWindow {
    private final long[] samples;
    private int next,size;
    public record Snapshot(int samples,long p50Nanos,long p95Nanos,long maxNanos) {}
    public ProgramTimingWindow(int capacity) {
        if (capacity<1) throw new IllegalArgumentException("positive timing capacity required");
        samples=new long[capacity];
    }
    public void record(long nanos) {
        if (nanos<0) throw new IllegalArgumentException("negative duration");
        samples[next]=nanos; next=(next+1)%samples.length; size=Math.min(size+1,samples.length);
    }
    public Snapshot snapshot() {
        if (size==0) return new Snapshot(0,0,0,0);
        long[] ordered=Arrays.copyOf(samples,size); Arrays.sort(ordered);
        return new Snapshot(size,ordered[(int)Math.ceil(size*.50)-1],ordered[(int)Math.ceil(size*.95)-1],ordered[size-1]);
    }
    public void clear() { next=0; size=0; }
}
