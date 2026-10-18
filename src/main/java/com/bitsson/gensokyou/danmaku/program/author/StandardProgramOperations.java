package com.bitsson.gensokyou.danmaku.program.author;

import com.bitsson.gensokyou.danmaku.program.*;
import com.bitsson.gensokyou.danmaku.program.ops.OperationDataCodec;
import com.bitsson.gensokyou.danmaku.program.server.*;
import java.util.*;

/** One shared vocabulary for content compilation, authority execution and replicas. */
public final class StandardProgramOperations {
    private static final OperationDataCodec CODEC=ServerControlOperations.dataRegistry().freeze();
    private static final Map<Class<?>,OperationType<? extends Record>> TYPES=new LinkedHashMap<>();
    private static final OperationRegistry REGISTRY=new OperationRegistry();
    static {
        add("capture",ServerControlOperations.Capture.class); add("group",ServerControlOperations.Group.class);
        add("fire",ServerControlOperations.Fire.class); add("control",ServerControlOperations.Control.class);
        add("offset_group",ServerControlOperations.OffsetGroup.class); add("corridor",ServerControlOperations.Corridor.class);
        add("rebase_target",ServerControlOperations.RebaseTarget.class); add("radial_release",ServerControlOperations.RadialRelease.class);
        add("emitter",ServerControlOperations.Emitter.class); add("bind_source",ServerControlOperations.BindSource.class);
        add("sample",ServerControlOperations.Sample.class); add("surface",ServerControlOperations.Surface.class);
        add("replace",ServerControlOperations.Replace.class); REGISTRY.freeze();
    }
    private StandardProgramOperations() {}
    private static <P extends Record> void add(String name,Class<P> type) {
        var operation=ServerControlOperations.type(name,type,CODEC); TYPES.put(type,operation); REGISTRY.register(operation);
    }
    public static OperationRegistry registry() { return REGISTRY; }
    public static Optional<Object> decode(OperationNode node) {
        return TYPES.values().stream().filter(type->type.id().equals(node.operationId())).findFirst().map(type->type.decode(node.parameters()));
    }
    public static ProgramLimits limits() { return new ProgramLimits(4096,8,512,4096,256,262144); }
    @SuppressWarnings("unchecked")
    public static <P extends Record> OperationType<P> type(Class<P> type) {
        var result=TYPES.get(type);
        if (result==null) throw new IllegalArgumentException("not a standard operation: "+type.getSimpleName());
        return (OperationType<P>)result;
    }
    public static ProgramExecution execution() {
        var result=new ProgramExecution();
        for (var type:TYPES.values()) install(result,type);
        return result.freeze();
    }
    private static <P extends Record> void install(ProgramExecution result,OperationType<P> type) { ServerControlOperations.install(result,type); }
}
