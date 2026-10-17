package com.bitsson.gensokyou.danmaku.program.network;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Records the M2 hard gate even while implementation is below the required threshold. */
class ProgramScalingGateTest {
    @Test void semanticDigestDoesNotDependOnCompactTemplateCacheOrSpatialSubset() {
        var f=new ProgramNetworkTest.Fixture(); var plan=f.birth(48);
        var projection=f.projection();
        var fresh=new ProgramWire(NetworkLimits.defaults());
        String expected=fresh.imageDigest(projection.image());
        assertEquals(expected,f.wire.imageDigest(projection.image()));
        var decoded=fresh.image(f.wire.image(projection.image()));
        assertEquals(projection.image(),decoded);
        assertEquals(expected,new ProgramWire(NetworkLimits.defaults()).imageDigest(decoded));
        f.publish(com.bitsson.gensokyou.danmaku.program.math.Vec3.ZERO); f.deliver(0);
        assertEquals(expected,f.client.image(f.scope).map(fresh::imageDigest).orElseThrow());
        var batch=(ReplicaState.Batch)projection.image().entries().get(ReplicaState.batchKey(f.group,plan.ordinal()));
        var template=batch.templates().get(7);
        var subset=new ReplicaState.Batch(batch.group(),batch.ordinal(),batch.birth(),batch.source(),batch.description(),batch.binding(),batch.seed(),
                List.of(template),List.of(1L),List.of(1L),false,batch.motion());
        assertEquals(subset,fresh.decodeData(f.wire.encodeData(subset),ReplicaState.Batch.class));
    }
    @Test void reportInitialLogicalScalingWithoutHidingAnUnmetGate() throws Exception {
        long[] sizes=new long[2]; int i=0;
        for (int count:new int[]{48,480}) {
            var f=new ProgramNetworkTest.Fixture(); f.birth(count); f.publish(com.bitsson.gensokyou.danmaku.program.math.Vec3.ZERO);
            sizes[i++]=f.server.stats().logicalBytes().values().stream().mapToLong(Long::longValue).sum();
            f.deliver(0); assertEquals(count,f.client.renderViews("test:world",0).getFirst().members().size());
        }
        var path=java.nio.file.Path.of("build/reports/program-scaling-gate.json"); java.nio.file.Files.createDirectories(path.getParent());
        java.nio.file.Files.writeString(path,"{\"members48\":"+sizes[0]+",\"members480\":"+sizes[1]+",\"ratio\":"+(sizes[1]/(double)sizes[0])+",\"requiredMaximum\":2,\"passed\":"+(sizes[1]<=2*sizes[0])+"}\n");
        assertTrue(sizes[0]>0 && sizes[1]>0);
        assertTrue(sizes[1]<=2*sizes[0],"M2 shared description initial logical bytes must scale <= 2x");
    }
}
