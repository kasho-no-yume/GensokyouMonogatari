package com.bitsson.gensokyou.danmaku.program.content;

import com.bitsson.gensokyou.danmaku.program.author.*;
import com.bitsson.gensokyou.danmaku.program.math.*;
import com.bitsson.gensokyou.danmaku.program.ops.*;
import com.bitsson.gensokyou.danmaku.program.server.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real-runtime geometric probes, not claims of playability or Minecraft damage simulation. */
class RumiaCoverageTest {
    private static final UUID OWNER = new UUID(0, 1);
    private static class World implements DecisionProvider {
        Vec3 point;
        World(Vec3 point) { this.point = point; }
        public List<Player> players() { return List.of(new Player(new UUID(0, 2), point,
                point, 1.8, true, false, false, .3)); }
        public Optional<Actions.Pose> entity(UUID id) { return id.equals(OWNER)
                ? Optional.of(new Actions.Pose(Frame.IDENTITY, Vec3.ZERO)) : Optional.empty(); }
        public boolean loaded(Vec3 p) { return true; }
        public Block block(int x, int y, int z) { return new Block(true, true, false, 0); }
    }

    private static double segmentDistance(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 d = b.subtract(a);
        double length = d.dot(d);
        double u = length == 0 ? 0 : Math.max(0, Math.min(1, p.subtract(a).dot(d) / length));
        return p.subtract(a.add(d.multiply(u))).length();
    }

    private int[] probe(int card, java.util.function.IntFunction<Vec3> route, int until) {
        var world = new World(route.apply(0));
        var runtime = new DanmakuWorldRuntime("test:rumia-coverage",
                new DanmakuWorldRuntime.Limits(8192, 512, 512, 512, 40000), world);
        var session = new ProgramSession(runtime, StandardProgramOperations.execution());
        var controller = new BossProgramController(session, OWNER);
        controller.card(RumiaPrograms.deck().card(card).program(), 0);
        int field = 0, drive = 0;
        for (int t = 0; t <= until; t++) {
            world.point = route.apply(t);
            session.advance(t, 40000);
            for (var event : runtime.drainEvents())
                assertFalse(event.kind().name().contains("REJECT"), card + ": " + event.reason());
            for (var group : session.checkpoint().runtime().groups()) for (var member : group.members()) {
                if (!runtime.damaging(member.id(), t)) continue;
                Vec3 a = runtime.state(member.id(), t).position();
                Vec3 b = member.plan().program().collisionProfile() instanceof CollisionProfile.Laser laser
                        ? a.add(runtime.direction(member.id(), t).multiply(laser.maxLength()))
                        : runtime.state(member.id(), t + 1).position();
                if (segmentDistance(world.point, a, b) <= member.template().style().collisionRadius() * .5 + .3) {
                    if (member.template().tags().contains("field")) field++;
                    if (member.template().tags().contains("drive")) drive++;
                }
            }
        }
        controller.close();
        return new int[]{field, drive};
    }

    @Test void stationaryPlayersAreDrivenFromAllSixDirectionsAtInnerAndOuterRange() {
        for (int card = 1; card <= 5; card++) for (double radius : new double[]{8, 32})
            for (Vec3 axis : List.of(Vec3.X, Vec3.Y, Vec3.Z, Vec3.X.multiply(-1), Vec3.Y.multiply(-1), Vec3.Z.multiply(-1))) {
                Vec3 p = axis.multiply(radius);
                assertTrue(probe(card, t -> p, 110)[1] > 0, "no personal threat card=" + card + " position=" + p);
            }
    }

    @Test void orbitAndVerticalShuttlesEncounterPublicCurtains() {
        for (int card = 1; card <= 5; card++) {
            int[] orbit = probe(card, t -> new Vec3(24 * Math.cos(t * .0125), 3,
                    24 * Math.sin(t * .0125)), 639);
            int[] vertical = probe(card, t -> new Vec3(8, 26 * Math.sin(t * .0125), 6), 639);
            assertTrue(orbit[0] > 0, "unopposed fixed orbit: " + card);
            assertTrue(vertical[0] > 0, "unopposed vertical shuttle: " + card);
        }
    }

    @Test void fivePlayersReceiveFivePersonalBatchesButOnlyOnePublicField() {
        for (int card = 1; card <= 5; card++) {
            var world = new World(new Vec3(0, 0, 24)) {
                @Override public List<Player> players() {
                    List<Player> result = new ArrayList<>();
                    for (int i = 0; i < 5; i++) {
                        Vec3 p = new Vec3(24 * Math.cos(i * Math.PI * 2 / 5), 3,
                                24 * Math.sin(i * Math.PI * 2 / 5));
                        result.add(new Player(new UUID(0, 2 + i), p, p, 1.8, true, false, false, .3));
                    }
                    return result;
                }
            };
            var runtime = new DanmakuWorldRuntime("test:rumia-multiplayer",
                    new DanmakuWorldRuntime.Limits(8192, 512, 512, 512, 40000), world);
            var session = new ProgramSession(runtime, StandardProgramOperations.execution());
            var controller = new BossProgramController(session, OWNER);
            controller.card(RumiaPrograms.deck().card(card).program(), 0);
            for (int t = 0; t <= 40; t++) {
                session.advance(t, 40000);
                for (var e : runtime.drainEvents()) assertFalse(e.kind().name().contains("REJECT"), e.reason());
            }
            int personal = 0, publicMembers = 0;
            for (var g : session.checkpoint().runtime().groups()) for (var m : g.members()) {
                if (m.template().tags().contains("drive")) personal++;
                if (m.template().tags().contains("field")) publicMembers++;
            }
            assertEquals((card == 2 || card == 5) ? 45 : 5, personal, "five personal batches card=" + card);
            assertEquals(47 * (card == 3 ? 2 : card == 4 ? 3 : 1), publicMembers, "shared field card=" + card);
            controller.close();
        }
    }
}
