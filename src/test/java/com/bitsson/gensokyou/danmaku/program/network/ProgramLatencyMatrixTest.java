package com.bitsson.gensokyou.danmaku.program.network;

import com.bitsson.gensokyou.danmaku.program.*;
import com.bitsson.gensokyou.danmaku.program.math.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Reproducible transport queue, not a claim about real Minecraft multiplayer rendering. */
class ProgramLatencyMatrixTest {
    @Test void clockBacklogDoesNotRequestAnUnneededFactCheckpoint() {
        var fixture=new ProgramNetworkTest.Fixture(); fixture.birth(48);
        var wire=new ProgramWire(NetworkLimits.defaults());
        var client=new ProgramReplica(new ProgramWire(NetworkLimits.defaults()),new OperationRegistry().freeze(),ProgramLimits.defaults());
        var server=new ProgramSyncServer(wire,bytes->client.receive(bytes,0),20);
        server.publish(fixture.projection(),Map.of(fixture.group,new ProgramInterest.Box(new Vec3(-100,-100,-100),new Vec3(100,100,100))),Vec3.ZERO,0);
        assertTrue(client.active("test:world"));
        var clock=client.clock("test:world");
        for (int tick=1;tick<=50;tick++) clock.observe(1000+tick,1);
        client.renderTime("test:world",1.5);
        assertTrue(clock.renderable(),"severe backlog preserves the frozen pose");
        assertTrue(client.recoveryRequests(2).isEmpty(),"intact facts must not be retransmitted for clock uncertainty");
        clock.observe(1051,2);
        client.renderTime("test:world",2.5);
        assertTrue(clock.renderable(),"next independent clock sample restores rendering without a checkpoint");
        // Even an uncertain/frozen display must obey terminal facts immediately.
        for (int tick=1052;tick<=1100;tick++) clock.observe(tick,2);
        assertFalse(clock.trusted());
        server.end("test:world",fixture.encounter,1100,fixture.runtime.checkpoint().revision());
        assertFalse(client.active("test:world"));
        assertTrue(client.renderViews("test:world",client.renderTime("test:world",3)).isEmpty());
    }
    @Test void sparseHeartbeatsAtFiveHundredMsUseActualRenderTimeAndVisibility() {
        var fixture=new ProgramNetworkTest.Fixture(); fixture.birth(48);
        var wire=new ProgramWire(NetworkLimits.defaults());
        var client=new ProgramReplica(new ProgramWire(NetworkLimits.defaults()),new OperationRegistry().freeze(),ProgramLimits.defaults());
        var queue=new ArrayDeque<Packet>();
        int[] now={0}; long[] order={0};
        var server=new ProgramSyncServer(wire,bytes->{
            // 500 ms RTT, plus a deterministic +/- one-client-tick arrival phase.
            int jitter=now[0]%60==0?1:0;
            double due=Math.max(queue.isEmpty()?0:queue.peekLast().due(),now[0]+5+jitter);
            queue.add(new Packet(due,order[0]++,bytes));
        },20);
        int visible=0;
        for (int local=0;local<=90;local++) {
            now[0]=local;
            if (local>0) fixture.runtime.advance(local);
            server.publish(fixture.projection(),Map.of(fixture.group,new ProgramInterest.Box(new Vec3(-100,-100,-100),new Vec3(100,100,100))),Vec3.ZERO,0);
            // A render frame precedes enqueueWork within the same integer client tick.
            if (client.active("test:world")) client.renderTime("test:world",local+.99);
            while (!queue.isEmpty() && queue.peek().due()<=local) client.receive(queue.remove().bytes(),local);
            if (!client.active("test:world")) continue;
            double time=client.renderTime("test:world",local+.99);
            assertTrue(client.clock("test:world").renderable(),"blank at "+local+" "+client.clock("test:world").diagnostic());
            assertFalse(client.renderViews("test:world",time).isEmpty());
            assertTrue(client.recoveryRequests(local).isEmpty(),"clock noise must not request full state");
            visible++;
        }
        assertTrue(visible>60);
    }
    private record Packet(double due,long order,byte[] bytes) {}
    private record Request(double due,int viewer,ProgramWire.Envelope envelope) {}
    @Test void delayedOrderedStreamsRecoverAfterClientStallsRetrackingAndLowTps() throws Exception {
        var rows=new ArrayList<String>();
        rows.add("viewers,rttMs,jitterMs,stallMs,tps,failures,pending,logicalBytes");
        for (int viewers:new int[]{1,2,5}) for (int rtt:new int[]{0,100,250})
            for (int jitter:new int[]{0,30}) for (int stall:new int[]{500,2000}) for (int tps:new int[]{20,10}) {
                var f=new ProgramNetworkTest.Fixture(); f.birth(48);
                var clients=new ArrayList<ProgramReplica>(); var servers=new ArrayList<ProgramSyncServer>();
                var queues=new ArrayList<ArrayDeque<Packet>>(); var lastDue=new double[viewers];
                var requests=new PriorityQueue<Request>(Comparator.comparingDouble(Request::due));
                var random=new Random(20261017L+viewers*10000+rtt*10+jitter+stall+tps);
                double[] wall={0}; long[] order={0};
                for (int v=0;v<viewers;v++) {
                    var queue=new ArrayDeque<Packet>(); queues.add(queue); final int index=v;
                    clients.add(new ProgramReplica(new ProgramWire(NetworkLimits.defaults()),new OperationRegistry().freeze(),ProgramLimits.defaults()));
                    servers.add(new ProgramSyncServer(new ProgramWire(NetworkLimits.defaults()),bytes->{
                        double delay=Math.max(0,rtt/2D+(random.nextDouble()*2-1)*jitter);
                        double due=Math.max(lastDue[index],wall[0]+delay); lastDue[index]=due;
                        queue.add(new Packet(due,order[0]++,bytes));
                    },20));
                }
                int nextServer=0;
                for (int local=0;local<=300;local++) {
                    wall[0]=local*50D;
                    while (!requests.isEmpty() && requests.peek().due()<=wall[0]) {
                        var request=requests.remove(); servers.get(request.viewer()).recover(request.envelope(),local);
                    }
                    if (local%(20/tps)==0 && nextServer<=100) {
                        if (nextServer>0) f.runtime.advance(nextServer);
                        for (int v=0;v<viewers;v++) {
                            var bounds=Map.of(f.group,new ProgramInterest.Box(new Vec3(-100,-100,-100),new Vec3(100,100,100)));
                            servers.get(v).publish(f.projection(),bounds,nextServer>=30 && nextServer<40?new Vec3(1000,0,0):Vec3.ZERO,0);
                            if (nextServer==90) servers.get(v).end("test:world",f.encounter,nextServer,f.runtime.checkpoint().revision());
                        }
                        nextServer++;
                    }
                    boolean stalled=local>=45 && local<45+stall/50;
                    if (stalled) continue;
                    for (int v=0;v<viewers;v++) {
                        var client=clients.get(v); var queue=queues.get(v);
                        while (!queue.isEmpty() && queue.peek().due()<=wall[0]) client.receive(queue.remove().bytes(),local);
                        if (nextServer<90 && client.image(f.scope).isPresent()) {
                            var image=client.image(f.scope).orElseThrow();
                            for (var view:client.renderViews("test:world",image.tick())) for (var member:view.members())
                                assertEquals(f.runtime.state(member.id(),image.tick()).position(),member.position());
                        }
                        client.noteTick(local);
                        // Recovery traverses the upstream half RTT before the next authority publish.
                        for (var scope:client.recoveryRequests(local)) {
                            var request=client.request(scope,local).orElseThrow();
                            double delay=Math.max(0,rtt/2D+(random.nextDouble()*2-1)*jitter);
                            requests.add(new Request(wall[0]+delay,v,request));
                        }
                    }
                }
                long bytes=0,failures=0; int pending=0;
                for (int v=0;v<viewers;v++) {
                    assertTrue(clients.get(v).image(f.scope).isEmpty(),"End must dominate queued data");
                    assertEquals(0,clients.get(v).pendingTransfers()); assertTrue(queues.get(v).isEmpty());
                    assertEquals(0,clients.get(v).failures());
                    failures+=clients.get(v).failures(); pending+=clients.get(v).pendingTransfers();
                    bytes+=servers.get(v).stats().logicalBytes().values().stream().mapToLong(Long::longValue).sum();
                }
                rows.add(viewers+","+rtt+","+jitter+","+stall+","+tps+","+failures+","+pending+","+bytes);
            }
        var report=java.nio.file.Path.of("build/reports/program-latency-matrix.csv");
        java.nio.file.Files.createDirectories(report.getParent()); java.nio.file.Files.write(report,rows);
        assertEquals(73,rows.size());
    }
}
