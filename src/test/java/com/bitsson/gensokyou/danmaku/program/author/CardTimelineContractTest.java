package com.bitsson.gensokyou.danmaku.program.author;

import com.bitsson.gensokyou.danmaku.program.*;
import com.bitsson.gensokyou.danmaku.program.math.*;
import com.bitsson.gensokyou.danmaku.program.ops.*;
import com.bitsson.gensokyou.danmaku.program.server.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 作者时间轴的顺序契约与循环收束契约。
 *
 * <p>覆盖观察者指出的两条：(a) 校验必须按<b>真实时间</b>而非 AST 列表顺序；(b)
 * {@code END_AT_BOUNDARY} 必须真的清掉旧轮成员，不能只凭 {@code Group.retain} 推断。
 */
class CardTimelineContractTest {
    private static final UUID OWNER = new UUID(0, 1);

    @Test void outOfOrderDeclaredAtNodesStillRunByTickOrder() {
        var card = new CardProgram("test:out_of_order");
        // 先声明 tick=100 的块，再声明 tick=0 的块；执行必须按 tick。
        var late = newCardBlock(card, "late", 100);
        var early = newCardBlock(card, "early", 0);
        var session = session();
        var controller = new BossProgramController(session, OWNER);
        controller.card(card.once(), 0);
        var birthTicks = new TreeSet<Long>();
        for (int tick = 0; tick <= 120; tick++) {
            session.advance(tick, 1024);
            for (var event : session.runtime().drainEvents())
                if (event.kind() == DanmakuWorldRuntime.Kind.COMMIT) birthTicks.add(event.tick());
            if (tick == 0) assertEquals(1, live(session), "early block born at tick 0");
            if (tick == 99) assertEquals(1, live(session), "late block not yet born");
            if (tick == 100) assertEquals(2, live(session), "late block born at tick 100");
        }
        assertEquals(new TreeSet<>(List.of(0L, 100L)), birthTicks);
        controller.close();
        assertNotNull(early); assertNotNull(late);
    }

    @Test void referenceBeforeCreateIsRejectedAtBuildTime() {
        var card = new CardProgram("test:late_ref");
        var targets = card.targets("players", 64, 1);
        var gun = card.aimed("gun", targets, 0, Vec3.ZERO);
        var bullet = bullet();
        assertThrows(IllegalArgumentException.class,
                () -> card.fire(new CardProgram.Group(card, "missing"), bullet, 10, 1));
    }

    @Test void endAtBoundaryClearsTheOldRoundEvenWithLongLifetime() {
        var session = session();
        var controller = new BossProgramController(session, OWNER);
        controller.card(longLifeCard("test:boundary", TailPolicy.END_AT_BOUNDARY), 0);
        for (int tick = 0; tick <= 150; tick++) {
            session.advance(tick, 1024);
            session.runtime().drainEvents();
        }
        // 成员寿命 300 > 周期 100：END_AT_BOUNDARY 必须在 100 清掉第 0 轮。
        assertEquals(1, live(session), "END_AT_BOUNDARY clears the previous round's members");
        controller.close();
    }

    @Test void retainUntilLifetimeKeepsTheOldRound() {
        var session = session();
        var controller = new BossProgramController(session, OWNER);
        controller.card(longLifeCard("test:retain", TailPolicy.RETAIN_UNTIL_LIFETIME), 0);
        for (int tick = 0; tick <= 150; tick++) {
            session.advance(tick, 1024);
            session.runtime().drainEvents();
        }
        assertEquals(2, live(session), "RETAIN lets the previous round's members live out their lifetime");
        controller.close();
    }

    // --- harness -------------------------------------------------------

    private static String newCardBlock(CardProgram card, String prefix, int tick) {
        var targets = card.targets("players_" + prefix, 64, 1);
        var gun = card.aimed("gun_" + prefix, targets, 0, Vec3.ZERO);
        card.at(tick, c -> c.fire(gun, bullet(), 0, 1));
        return prefix;
    }

    private static CardProgram.Compiled longLifeCard(String id, TailPolicy tail) {
        var card = new CardProgram(id);
        var targets = card.targets("players", 64, 1);
        var gun = card.aimed("gun", targets, 0, Vec3.ZERO);
        card.at(0, c -> c.fire(gun, bullet(), 0, 1));
        return card.loop(100, tail);
    }

    private static Actions.ChildProgram bullet() {
        return MemberProgram.attack("bullet", MemberProgram.styled(new Patterns.Point(Vec3.ZERO, Vec3.Z),
                        "sphere", "blue", .1, 1, .2), new Paths.Linear(Vec3.Z, .1), 300)
                .fixedDirection().build();
    }

    private static int live(ProgramSession session) { return session.runtime().liveMembers(); }

    private static ProgramSession session() {
        var world = new World();
        var runtime = new DanmakuWorldRuntime("test:timeline",
                new DanmakuWorldRuntime.Limits(4096, 512, 256, 256, 65536), world);
        return new ProgramSession(runtime, StandardProgramOperations.execution());
    }

    private static final class World implements DecisionProvider {
        public List<Player> players() {
            return List.of(new Player(new UUID(0, 2), new Vec3(0, 0, 20), new Vec3(0, 1, 20), 2, true, false, false, .3));
        }
        public Optional<Actions.Pose> entity(UUID uuid) { return Optional.of(new Actions.Pose(Frame.IDENTITY, Vec3.ZERO)); }
        public boolean loaded(Vec3 point) { return true; }
        public Block block(int x, int y, int z) { return new Block(true, true, false, 0); }
    }
}
