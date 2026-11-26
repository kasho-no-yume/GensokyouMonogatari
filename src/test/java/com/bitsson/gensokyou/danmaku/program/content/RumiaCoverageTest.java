package com.bitsson.gensokyou.danmaku.program.content;

import com.bitsson.gensokyou.danmaku.program.author.*;
import com.bitsson.gensokyou.danmaku.program.math.*;
import com.bitsson.gensokyou.danmaku.program.ops.*;
import com.bitsson.gensokyou.danmaku.program.server.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real-runtime geometric & budget probes for Rumia v4 (T2 / ~35% difficulty). */
class RumiaCoverageTest {
    private static final UUID OWNER = new UUID(0, 1);

    private static class World implements DecisionProvider {
        Vec3 point;
        World(Vec3 point) { this.point = point; }
        public List<Player> players() {
            return List.of(new Player(new UUID(0, 2), point, point, 1.8, true, false, false, .3));
        }
        public Optional<Actions.Pose> entity(UUID id) {
            return id.equals(OWNER) ? Optional.of(new Actions.Pose(Frame.IDENTITY, Vec3.ZERO)) : Optional.empty();
        }
        public boolean loaded(Vec3 p) { return true; }
        public Block block(int x, int y, int z) { return new Block(true, true, false, 0); }
    }

    private static double segmentDistance(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 d = b.subtract(a);
        double length = d.dot(d);
        double u = length == 0 ? 0 : Math.max(0, Math.min(1, p.subtract(a).dot(d) / length));
        return p.subtract(a.add(d.multiply(u))).length();
    }

    private int[] runCard(int card, java.util.function.IntFunction<Vec3> route, int until) {
        var world = new World(route.apply(0));
        var runtime = new DanmakuWorldRuntime("test:rumia-v4",
                new DanmakuWorldRuntime.Limits(8192, 512, 512, 512, 40000), world);
        var session = new ProgramSession(runtime, StandardProgramOperations.execution());
        var controller = new BossProgramController(session, OWNER);
        controller.card(RumiaPrograms.deck().card(card).program(), 0);
        int hits = 0;
        int peakAlive = 0;
        for (int t = 0; t <= until; t++) {
            world.point = route.apply(t);
            session.advance(t, 40000);
            for (var event : runtime.drainEvents()) {
                assertFalse(event.kind().name().contains("REJECT"), card + ": " + event.reason());
            }
            int alive = 0;
            for (var group : session.checkpoint().runtime().groups()) {
                for (var member : group.members()) {
                    alive++;
                    if (!runtime.damaging(member.id(), t)) continue;
                    Vec3 a = runtime.state(member.id(), t).position();
                    Vec3 b = member.plan().program().collisionProfile() instanceof CollisionProfile.Laser laser
                            ? a.add(runtime.direction(member.id(), t).multiply(laser.maxLength()))
                            : runtime.state(member.id(), t + 1).position();
                    if (segmentDistance(world.point, a, b) <= member.template().style().effectiveCollisionRadius() + .3) {
                        hits++;
                    }
                }
            }
            peakAlive = Math.max(peakAlive, alive);
        }
        controller.close();
        return new int[]{hits, peakAlive};
    }

    @Test
    void allFiveCardsRunCleanlyWithinT2DensityBudget() {
        for (int card = 1; card <= 5; card++) {
            int[] res = runCard(card, t -> new Vec3(0, 0, 14), RumiaPrograms.CYCLE * 2);
            assertTrue(res[1] > 0 && res[1] <= 160,
                    "card " + card + " peak alive out of T2 range (1..160): " + res[1]);
        }
    }

    @Test
    void stationaryPlayerIsThreatenedAcrossCards() {
        for (int card = 1; card <= 5; card++) {
            int[] res = runCard(card, t -> new Vec3(0, 0, 14), RumiaPrograms.CYCLE);
            assertTrue(res[0] > 0, "stationary player at (0,0,14) never threatened on card " + card);
        }
    }

    @Test
    void card4PublicShellsDoNotDuplicateForFivePlayers() {
        var world = new World(new Vec3(0, 0, 18)) {
            @Override
            public List<Player> players() {
                List<Player> result = new ArrayList<>();
                for (int i = 0; i < 5; i++) {
                    Vec3 p = new Vec3(18 * Math.cos(i * Math.PI * 2 / 5), 2,
                            18 * Math.sin(i * Math.PI * 2 / 5));
                    result.add(new Player(new UUID(0, 2 + i), p, p, 1.8, true, false, false, .3));
                }
                return result;
            }
        };
        var runtime = new DanmakuWorldRuntime("test:rumia-v4-mp",
                new DanmakuWorldRuntime.Limits(8192, 512, 512, 512, 40000), world);
        var session = new ProgramSession(runtime, StandardProgramOperations.execution());
        var controller = new BossProgramController(session, OWNER);
        controller.card(RumiaPrograms.deck().card(4).program(), 0);
        for (int t = 0; t <= 40; t++) {
            session.advance(t, 40000);
            for (var e : runtime.drainEvents()) {
                assertFalse(e.kind().name().contains("REJECT"), e.reason());
            }
        }
        int shells = 0;
        int seeds = 0;
        for (var g : session.checkpoint().runtime().groups()) {
            for (var m : g.members()) {
                if (m.template().tags().contains("innerMoon") || m.template().tags().contains("midMoon") || m.template().tags().contains("outerMoon")) shells++;
                if (m.template().tags().contains("seed")) seeds++;
            }
        }
        assertEquals(63, shells, "three 21-member public shells must stay shared across 5 players");
        assertEquals(15, seeds, "five players x 3 eclipse seeds");
        controller.close();
    }
}
