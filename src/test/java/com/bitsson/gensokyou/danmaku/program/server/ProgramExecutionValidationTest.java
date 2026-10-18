package com.bitsson.gensokyou.danmaku.program.server;

import com.bitsson.gensokyou.danmaku.program.*;
import com.bitsson.gensokyou.danmaku.program.author.*;
import com.bitsson.gensokyou.danmaku.program.math.*;
import com.bitsson.gensokyou.danmaku.program.ops.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 通用句柄校验必须按<b>真实时间序</b>（tick, 节点序）判，而不是 AST 列表序。
 * 用故意乱序的嵌套 Parallel/At 直接编译原始节点，绕开 CardProgram 的排序。
 */
class ProgramExecutionValidationTest {
    private static OperationNode capture(ProgramBuilder b) {
        var codec = ServerControlOperations.dataRegistry().freeze();
        var type = ServerControlOperations.type("capture", ServerControlOperations.Capture.class, codec);
        return b.operation(type, new ServerControlOperations.Capture("players",
                new AuthorityDescriptions.CaptureTargets(64, 1, true, true)), new TickSpan(0));
    }
    private static OperationNode group(ProgramBuilder b) {
        var codec = ServerControlOperations.dataRegistry().freeze();
        var type = ServerControlOperations.type("group", ServerControlOperations.Group.class, codec);
        return b.operation(type, new ServerControlOperations.Group("gun", "players", 0,
                AuthorityDescriptions.TargetPoint.EYES, Vec3.ZERO, Vec3.Y, Vec3.Z, false, null), new TickSpan(0));
    }
    private static OperationNode fire(ProgramBuilder b) {
        var codec = ServerControlOperations.dataRegistry().freeze();
        var type = ServerControlOperations.type("fire", ServerControlOperations.Fire.class, codec);
        var member = MemberProgram.attack("b", MemberProgram.styled(new Patterns.Point(Vec3.ZERO, Vec3.Z),
                        "sphere", "blue", .1, 1, .2), new Paths.Linear(Vec3.Z, .1), 40)
                .fixedDirection().build();
        var policy = new AuthorityDescriptions.Commit(new ProgramRef.Plan(new NodeId(0)), 0, 512,
                AuthorityDescriptions.Failure.REJECT_BATCH, true);
        return b.operation(type, new ServerControlOperations.Fire("gun", member, Actions.Binding.snapshot(),
                new Deterministic.Seed(1, List.of()), policy, 0), new TickSpan(0));
    }

    private static ProgramDefinition compile(ProgramNode root) {
        var result = ProgramCompiler.compile("test:validation_order", 1,
                new PlaybackPolicy.Once(TailPolicy.RETAIN_UNTIL_LIFETIME), root, ProgramLimits.defaults(),
                StandardProgramOperations.registry());
        assertTrue(result.success(), result.diagnostics().toString());
        return result.definition().orElseThrow();
    }

    @Test void nestedParallelValidatesByTimeNotAstOrder() {
        var b = new ProgramBuilder();
        // AST order lists the tick-100 fire FIRST; capture/group at tick 0 come after.
        var root = new ParallelNode(b.nextId(), List.of(
                new AtNode(b.nextId(), TimeDomain.CARD, new TickSpan(100), fire(b)),
                new AtNode(b.nextId(), TimeDomain.CARD, new TickSpan(0), capture(b)),
                new AtNode(b.nextId(), TimeDomain.CARD, new TickSpan(0), group(b))));
        var definition = compile(root);
        assertDoesNotThrow(() -> StandardProgramOperations.execution().validate(definition),
                "real order is capture@0, group@0, fire@100");
    }

    @Test void referenceToALaterHandleIsRejected() {
        var b = new ProgramBuilder();
        // group@0 exists at the same tick but is listed after capture; swap so group precedes capture.
        var root = new ParallelNode(b.nextId(), List.of(
                new AtNode(b.nextId(), TimeDomain.CARD, new TickSpan(0), group(b)),
                new AtNode(b.nextId(), TimeDomain.CARD, new TickSpan(0), capture(b))));
        var definition = compile(root);
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> StandardProgramOperations.execution().validate(definition))
                .getMessage().contains("unavailable target handle"));
    }

    @Test void captureAfterUseIsRejectedEvenWhenAstLooksOrdered() {
        var b = new ProgramBuilder();
        var root = new ParallelNode(b.nextId(), List.of(
                new AtNode(b.nextId(), TimeDomain.CARD, new TickSpan(0), group(b)),
                new AtNode(b.nextId(), TimeDomain.CARD, new TickSpan(100), capture(b))));
        var definition = compile(root);
        assertThrows(IllegalArgumentException.class,
                () -> StandardProgramOperations.execution().validate(definition));
    }
}
