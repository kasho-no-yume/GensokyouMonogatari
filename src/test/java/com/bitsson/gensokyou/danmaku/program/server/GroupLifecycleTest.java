package com.bitsson.gensokyou.danmaku.program.server;

import com.bitsson.gensokyou.danmaku.program.*;
import com.bitsson.gensokyou.danmaku.program.math.*;
import com.bitsson.gensokyou.danmaku.program.ops.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 组的生命周期：退休、回收、批次各自寿命。
 *
 * <p>这些是「打着打着崩服」的根因所在：循环型符卡每个循环边界都重新声明组，
 * 而组在旧实现里永远回收不掉，组表单调增长撞上 512 上限后 {@code createGroup} 抛异常。
 */
class GroupLifecycleTest {
    static final UUID OWNER=new UUID(0,1);
    static final Deterministic.Seed SEED=new Deterministic.Seed(23,List.of());
    static final class World implements DecisionProvider {
        List<Player> players=new ArrayList<>();
        Map<UUID,Actions.Pose> entities=new HashMap<>();
        World() { entities.put(OWNER,new Actions.Pose(Frame.IDENTITY,Vec3.ZERO)); }
        public List<Player> players() { return List.copyOf(players); }
        public Optional<Actions.Pose> entity(UUID id) { return Optional.ofNullable(entities.get(id)); }
        public boolean loaded(Vec3 p) { return p.x()<100; }
        public Block block(int x,int y,int z) { return new Block(x<100,y>0,y<=0,1); }
    }
    /** 寿命 100 tick 的普通弹批次。 */
    static Actions.ChildProgram program(int n) {
        return new Actions.ChildProgram(new Patterns.SphereDirections(n,Patterns.SphereDistribution.FIBONACCI,1),new Paths.Linear(Vec3.Z,.2),100,0,0,List.of(),
                new Presentation.Profile(List.of(new Presentation.Window(TimeDomain.MEMBER_AGE,0,100)),null,null,List.of()),new Transforms.Compose(List.of()));
    }
    static AuthorityDescriptions.Commit policy(int quota,double clearance) {
        return new AuthorityDescriptions.Commit(new ProgramRef.Plan(new NodeId(0)),clearance,quota,AuthorityDescriptions.Failure.CANCEL_GROUP,true);
    }
    static DanmakuWorldRuntime runtime(World world,int cap) {
        return new DanmakuWorldRuntime("test:lifecycle",new DanmakuWorldRuntime.Limits(cap,1000,100,100,10000),world);
    }
    static long group(DanmakuWorldRuntime runtime,DanmakuWorldRuntime.Encounter encounter,long cycle,boolean retain) {
        return runtime.createGroup(encounter,1,cycle,null,-1,false,retain,Frame.IDENTITY,List.of());
    }
    static EmissionPlan birth(DanmakuWorldRuntime r,long group,int n) {
        var p=r.prepare(group,r.tick(),new Actions.Pose(Frame.IDENTITY,Vec3.ZERO),program(n),Actions.Binding.snapshot(),SEED);
        assertTrue(r.commit(p,policy(4096,0))); return p;
    }

    /** 退休只影响回收：成员按各自寿命活着，成员消亡后组被回收。 */
    @Test void retirementKeepsTheBatchAndTheGroupIsReclaimedOnceItIsEmpty() {
        World w=new World(); var r=runtime(w,100); var encounter=r.startEncounter(OWNER);
        long g=group(r,encounter,0,true);
        r.advance(0); birth(r,g,2);
        assertEquals(2,r.liveMembers());

        r.endCycle(encounter,1,0,false);       // 循环边界：上一轮的组退休
        assertEquals(2,r.liveMembers(),"retirement must not kill the batch");
        assertEquals(1,r.checkpoint().groups().size(),"a retired group with live members is still present");
        assertTrue(r.checkpoint().groups().getFirst().members().stream().allMatch(DanmakuWorldRuntime.MemberSnapshot::harmful),
                "the surviving batch keeps its own damage state");

        for (long t=1;t<=120;t++) r.advance(t);   // 寿命 100 → 成员在 tick 100 结束
        assertEquals(0,r.liveMembers());
        assertTrue(r.checkpoint().groups().isEmpty(),"a retired group is reclaimed once it is empty");
    }

    /** 退休不取消：跨过循环边界才出生的批次照常出生。 */
    @Test void retirementDoesNotDropABatchThatIsBornAfterTheBoundary() {
        World w=new World(); var r=runtime(w,100); var encounter=r.startEncounter(OWNER);
        long g=group(r,encounter,0,true);
        r.advance(0);
        var pending=r.prepare(g,25,new Actions.Pose(Frame.IDENTITY,Vec3.ZERO),program(1),Actions.Binding.snapshot(),SEED);
        for (long t=1;t<=20;t++) r.advance(t);
        r.endCycle(encounter,1,0,false);       // 边界落在该批次出生之前
        for (long t=21;t<=25;t++) r.advance(t);
        assertTrue(r.commit(pending,policy(4096,0)),"the unborn batch still lands after the boundary");
        assertEquals(1,r.liveMembers());
    }

    /** 同一张符卡的两轮是两份独立组：一份的批次级操作不波及另一份。 */
    @Test void twoCyclesOfTheSameCardAreIndependentGroups() {
        World w=new World(); var r=runtime(w,100); var encounter=r.startEncounter(OWNER);
        long first=group(r,encounter,0,true);
        r.advance(0); birth(r,first,1);
        long second=group(r,encounter,1,true);
        r.advance(1); birth(r,second,1);
        assertEquals(2,r.liveMembers());
        assertEquals(2,r.checkpoint().groups().size());

        var members=r.checkpoint().groups().stream()
                .flatMap(g->g.members().stream()).map(DanmakuWorldRuntime.MemberSnapshot::id).toList();
        var ofFirst=r.checkpoint().groups().stream().filter(g->g.id()==first).findFirst().orElseThrow().members().getFirst().id();
        var ofSecond=r.checkpoint().groups().stream().filter(g->g.id()==second).findFirst().orElseThrow().members().getFirst().id();
        assertEquals(2,members.size());

        // 只对第二轮的成员做脱离（等价于 release / reaim 的运行时动作）
        r.rebase(ofSecond,new Paths.Linear(Vec3.X,1),true);
        assertTrue(r.checkpoint().detached().containsKey(ofSecond));
        assertFalse(r.checkpoint().detached().containsKey(ofFirst),"the other cycle's batch stays untouched");
    }

    /** 切牌：旧弹幕淡出离场，离场期间不再判伤。 */
    @Test void endingACardFadesTheOldDanmakuOutAndStopsThemDamaging() {
        World w=new World(); var r=runtime(w,100); var encounter=r.startEncounter(OWNER);
        long g=group(r,encounter,0,false);
        r.advance(0); birth(r,g,2);
        assertEquals(2,r.liveMembers());

        r.endCard(encounter,1);
        assertEquals(2,r.liveMembers(),"the old batch stays on screen while it fades");
        assertTrue(r.checkpoint().groups().getFirst().members().stream().noneMatch(DanmakuWorldRuntime.MemberSnapshot::harmful),
                "the fading batch stops damaging immediately");
        assertEquals(DanmakuWorldRuntime.CARD_END_FADE_TICKS,r.checkpoint().groups().getFirst().fadeEnd()-r.tick());
        assertTrue(DanmakuWorldRuntime.CARD_END_FADE_TICKS>=20,"the fade window must be readable");

        for (long t=1;t<=40;t++) r.advance(t);
        assertEquals(0,r.liveMembers(),"the faded batch is gone once the window closes");
    }
}
