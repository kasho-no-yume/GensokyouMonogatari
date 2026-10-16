package com.bitsson.gensokyou.danmaku.program.server;

import java.util.*;
import java.util.function.Supplier;

/** Explicit host for dimension ownership; removing a dimension destroys all temporary encounters. */
public final class DimensionRuntimes {
    private final Map<String,DanmakuWorldRuntime> runtimes=new LinkedHashMap<>();
    public DanmakuWorldRuntime getOrCreate(String dimension,Supplier<DanmakuWorldRuntime> create) {
        return runtimes.computeIfAbsent(dimension,key->Objects.requireNonNull(create.get()));
    }
    public void unload(String dimension) {
        DanmakuWorldRuntime runtime=runtimes.remove(dimension);
        if (runtime!=null) runtime.close("dimension-unload");
    }
    public void close() {
        for (String dimension:List.copyOf(runtimes.keySet())) unload(dimension);
    }
}
