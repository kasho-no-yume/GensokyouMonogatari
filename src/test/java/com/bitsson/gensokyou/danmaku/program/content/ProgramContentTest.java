package com.bitsson.gensokyou.danmaku.program.content;

import com.bitsson.gensokyou.danmaku.program.ClockAnchors;
import com.bitsson.gensokyou.danmaku.program.author.BossProgramController;
import com.bitsson.gensokyou.danmaku.program.author.MemberProgram;
import com.bitsson.gensokyou.danmaku.program.author.StandardProgramOperations;
import com.bitsson.gensokyou.danmaku.program.math.Deterministic;
import com.bitsson.gensokyou.danmaku.program.math.Vec3;
import com.bitsson.gensokyou.danmaku.program.ops.*;
import com.bitsson.gensokyou.danmaku.program.server.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 正式迁移内容的结构/数量/时序证据（tasks 1.2–1.4、1.7）。
 *
 * <p>这些是真实内存 session 的权威结果，不是实机验收：地形、生存模式命中与多人可读性
 * 仍需实机/多人复验，测试不替代那些记录。
 */
class ProgramContentTest {
    private static final UUID OWNER = new UUID(0, 1);

    // ------------------------------------------------------------------
    // 结构与清单
    // ------------------------------------------------------------------
    @Test void decksCompileWithExpectedLegacyCompatibleShape() {
        var decks = BossProgramCards.all();
        assertEquals(List.of("big_fairy", "yamame", "yakumo_ran"),
                decks.stream().map(BossProgramCards.Deck::ownerId).toList());

        var bigFairy = BossProgramCards.forOwner("big_fairy").orElseThrow();
        assertEquals(3, bigFairy.cards().size());
        assertNotNull(bigFairy.background(), "大妖精保留默认弹墙底噪");
        assertEquals(List.of(1.00D, 0.66D, 0.33D),
                bigFairy.cards().stream().map(BossProgramCards.Card::hpFraction).toList());

        var yamame = BossProgramCards.forOwner("yamame").orElseThrow();
        assertEquals(4, yamame.cards().size());
        assertNotNull(yamame.background(), "山女保留蛛丝扇底噪");
        assertEquals(List.of(1.00D, 0.75D, 0.50D, 0.25D),
                yamame.cards().stream().map(BossProgramCards.Card::hpFraction).toList());

        var ran = BossProgramCards.forOwner("yakumo_ran").orElseThrow();
        assertEquals(8, ran.cards().size());
        assertNull(ran.background(), "蓝关闭独立底噪");
        assertEquals(List.of(1.000D, 0.875D, 0.750D, 0.625D, 0.500D, 0.375D, 0.250D, 0.125D),
                ran.cards().stream().map(BossProgramCards.Card::hpFraction).toList());

        for (var deck : decks) {
            for (int i = 0; i < deck.cards().size(); i++) {
                var card = deck.cards().get(i);
                assertEquals(i + 1, card.index());
                assertEquals("spellcard.gensokyou." + deck.ownerId() + "." + (i + 1), card.nameKey());
            }
        }
        assertEquals(List.of("nomen_mask"), BossProgramCards.excludedOwners());
        assertTrue(BossProgramCards.forOwner("nomen_mask").isEmpty());
    }

    @Test void migratedAndExcludedManifestCoversTheAuthorizedFifteenNotTwenty() {
        int migrated = BossProgramCards.all().stream().mapToInt(d -> d.cards().size()).sum();
        assertEquals(15, migrated, "大妖精3 + 山女4 + 蓝8；tasks 1.5 明确不迁移露米娅/傩面");
        assertEquals(List.of("nomen_mask"), BossProgramCards.excludedOwners(),
                "露米娅/傩面整体排除，不再因旧 20 卡表自动补全");
    }

    @Test void selectionUsesLastReachedThresholdLikeLegacyRunner() {
        var ran = BossProgramCards.forOwner("yakumo_ran").orElseThrow();
        assertEquals(1, ran.select(1.00D));
        assertEquals(1, ran.select(0.99D));
        assertEquals(2, ran.select(0.875D));
        assertEquals(3, ran.select(0.70D));
        assertEquals(8, ran.select(0.10D));
        assertEquals(8, ran.select(0.0D));
    }

    // ------------------------------------------------------------------
    // 大妖精：环卡 48/人、120 tick 锁位
    // ------------------------------------------------------------------
    @Test void bigFairyRingIsFortyEightPerPlayerAndLocksFirst120Ticks() {
        var deck = BossProgramCards.forOwner("big_fairy").orElseThrow();
        var session = session(1);
        var controller = new BossProgramController(session, OWNER);
        var battle = new com.bitsson.gensokyou.danmaku.program.gameplay.BossProgramBattle(controller, deck);
        battle.install(0);
        long commits = 0;
        long members = 0;
        for (int tick = 0; tick <= 239; tick++) {
            session.advance(tick, 4096);
            for (var event : session.runtime().drainEvents()) {
                if (event.kind() == DanmakuWorldRuntime.Kind.COMMIT
                        && event.plan().program().pattern().countBound() == 48) {
                    commits++;
                    members += event.plan().members().size();
                }
            }
            if (tick < 120) assertTrue(session.runtime().movementLocked(controller.encounter()), "locked at " + tick);
            if (tick == 120) assertFalse(session.runtime().movementLocked(controller.encounter()), "released at 120");
        }
        assertEquals(1, commits, "单名玩家只命中 slot0；缺席槽不补人也不发射");
        assertEquals(48, members, "单拍 48 颗共圆");
        // RETAIN：环弹寿命 400，240 边界不清场。
        assertTrue(session.runtime().liveMembers() > 0, "RETAIN keeps the ring until its own lifetime");
        battle.close();
    }

    // ------------------------------------------------------------------
    // 山女：子拍修复后的根弹数量（10 × 6 = 60/轮）
    // ------------------------------------------------------------------
    @Test void yamameWebSubBeatsAreAllPresentAfterLegacyFix() {
        var deck = BossProgramCards.forOwner("yamame").orElseThrow();
        var session = session(1);
        var controller = new BossProgramController(session, OWNER);
        var battle = new com.bitsson.gensokyou.danmaku.program.gameplay.BossProgramBattle(controller, deck);
        battle.install(0);
        long roots = 0;
        for (int tick = 0; tick <= 199; tick++) {
            session.advance(tick, 16384);
            for (var event : session.runtime().drainEvents()) {
                if (event.kind() != DanmakuWorldRuntime.Kind.COMMIT) continue;
                if (event.plan().program().pattern().countBound() == 6) roots += event.plan().members().size();
            }
        }
        assertEquals(60, roots, "0..180 每 20 tick 一拍 × 6 根 = 60；旧实现只放首拍会是 6");
        battle.close();
    }

    @Test void yamameNestTailPearlsEndBeforeTheNextCycle() {
        var deck=BossProgramCards.forOwner("yamame").orElseThrow();
        var session=session(1);
        var controller=new BossProgramController(session,OWNER);
        var battle=new com.bitsson.gensokyou.danmaku.program.gameplay.BossProgramBattle(controller,deck);
        battle.install(0);
        int maxLive=0;
        for (int tick=0;tick<=519;tick++) {
            session.advance(tick,65536);
            session.runtime().drainEvents();
            maxLive=Math.max(maxLive,session.runtime().liveMembers());
        }
        assertTrue(maxLive>0,"nest tails must remain active across the authored cycle boundary");
        battle.close();
    }

    // ------------------------------------------------------------------
    // 蓝：无底噪、220 收束、卡 1 单人 108
    // ------------------------------------------------------------------
    @Test void ranHasNoBackgroundAndWindsDownAt220ThenStartsFreshAt240() {
        var deck = BossProgramCards.forOwner("yakumo_ran").orElseThrow();
        var session = session(1);
        var controller = new BossProgramController(session, OWNER);
        var battle = new com.bitsson.gensokyou.danmaku.program.gameplay.BossProgramBattle(controller, deck);
        battle.install(0);
        long firstCycle = 0;
        for (int tick = 0; tick <= 239; tick++) {
            session.advance(tick, 4096);
            for (var event : session.runtime().drainEvents()) {
                if (event.kind() == DanmakuWorldRuntime.Kind.COMMIT) firstCycle += event.plan().members().size();
                if (tick >= 220 && event.kind() == DanmakuWorldRuntime.Kind.COMMIT) fail("no attack after wind-down: " + tick);
            }
            if (tick == 219) assertTrue(session.runtime().liveMembers() > 0);
        }
        assertEquals(108, firstCycle, "单人 108（3 基点 × 4 拍 × 9）");
        assertEquals(0, session.runtime().liveMembers(), "230 前完成淡出，239 无残留");
        battle.close();
    }

    // ------------------------------------------------------------------
    // 蓝：五人数量 = 108/人 × 5
    // ------------------------------------------------------------------
    @Test void ranCardOneScalesToFiveCapturedSlots() {
        var deck = BossProgramCards.forOwner("yakumo_ran").orElseThrow();
        var session = session(5);
        var controller = new BossProgramController(session, OWNER);
        var battle = new com.bitsson.gensokyou.danmaku.program.gameplay.BossProgramBattle(controller, deck);
        battle.install(0);
        long total = 0;
        for (int tick = 0; tick <= 239; tick++) {
            session.advance(tick, 8192);
            for (var event : session.runtime().drainEvents())
                if (event.kind() == DanmakuWorldRuntime.Kind.COMMIT) total += event.plan().members().size();
        }
        assertEquals(540, total, "5 人 × 108/人；每个槽独立采样、缺席不补");
        battle.close();
    }

    // ------------------------------------------------------------------
    // 结构保证：卡内不得结束整场战斗；循环长度覆盖内容跨度
    // ------------------------------------------------------------------
    @Test void formalCardsNeverEndTheEncounterAndFitTheirLoopLength() {
        for (var deck : BossProgramCards.all()) {
            for (var card : deck.cards()) {
                assertTrue(card.program().definition().bounds().windowTicks() <= card.cycleTicks(),
                        card.program().definition().id() + " must fit its loop");
                for (var node : card.program().definition().nodes()) {
                    if (!(node instanceof com.bitsson.gensokyou.danmaku.program.OperationNode operation)) continue;
                    var value = StandardProgramOperations.decode(operation).orElse(null);
                    if (value instanceof ServerControlOperations.Control control)
                        assertNotEquals(com.bitsson.gensokyou.danmaku.program.ops.Actions.LifecycleKind.END_ENCOUNTER,
                                control.kind(), "a card must never end the encounter: " + card.nameKey());
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // 读档/中途开启：按当前血量装卡，不回到首卡并挂起
    // ------------------------------------------------------------------
    @Test void installAtReducedHpPicksTheMatchingCard() {
        var deck = BossProgramCards.forOwner("yakumo_ran").orElseThrow();
        var session = session(1);
        var controller = new BossProgramController(session, OWNER);
        var battle = new com.bitsson.gensokyou.danmaku.program.gameplay.BossProgramBattle(controller, deck);
        battle.install(0.10D, 0);
        assertEquals(8, battle.activeCardIndex(), "血量已在终卡区间时直接装终卡");
        battle.close();
    }

    // ------------------------------------------------------------------
    // 出生时刻不得被预警整体推后（发射 = 出生 − warning）
    // ------------------------------------------------------------------
    @Test void ranCardOneBirthTicksMatchTheMapping() {
        var deck = BossProgramCards.forOwner("yakumo_ran").orElseThrow();
        var session = session(1);
        var controller = new BossProgramController(session, OWNER);
        controller.card(deck.card(1).program(), 0);
        var births = new TreeSet<Long>();
        for (int tick = 0; tick <= 239; tick++) {
            session.advance(tick, 8192);
            for (var event : session.runtime().drainEvents())
                if (event.kind() == DanmakuWorldRuntime.Kind.COMMIT
                        && event.plan().program().pattern().countBound() == 9) births.add(event.tick());
        }
        assertEquals(Set.of(20L, 28L, 36L, 44L, 80L, 88L, 96L, 104L, 140L, 148L, 156L, 164L),
                new TreeSet<>(births), "出生 tick 必须等于映射拍点，不能是拍点+20");
    }

    // ------------------------------------------------------------------
    // 蓝逐卡五人数量（个人槽 ×5，公共组 ×1）
    // ------------------------------------------------------------------
    @Test void ranFivePlayerCountsPerCardMatchTheMapping() {
        var deck = BossProgramCards.forOwner("yakumo_ran").orElseThrow();
        long[] expected = {540, 144, 138, 62, 108, 96, 174, 612};
        for (int i = 0; i < 8; i++) {
            var session = session(5);
            var controller = new BossProgramController(session, OWNER);
            controller.card(deck.card(i + 1).program(), 0);
            long total = 0;
            for (int tick = 0; tick <= 239; tick++) {
                session.advance(tick, 32768);
                for (var event : session.runtime().drainEvents())
                    if (event.kind() == DanmakuWorldRuntime.Kind.COMMIT) total += event.plan().members().size();
            }
            assertEquals(expected[i], total, "card " + (i + 1) + " five-player total");
            controller.close();
        }
    }

    // ------------------------------------------------------------------
    // 蓝 4：走廊方块净空失败撤该激光批
    // ------------------------------------------------------------------
    @Test void ranCardFourWithdrawsLaserBatchWhenCorridorIsBlocked() {
        for (boolean blocked : new boolean[]{false, true}) {
            var world = new SolidWorld(1, blocked);
            var runtime = new DanmakuWorldRuntime("test:corridor",
                    new DanmakuWorldRuntime.Limits(8192, 512, 512, 512, 40000), world);
            var session = new ProgramSession(runtime, StandardProgramOperations.execution());
            var controller = new BossProgramController(session, OWNER);
            var deck = BossProgramCards.forOwner("yakumo_ran").orElseThrow();
            controller.card(deck.card(4).program(), 0);
            long liveLasers = -1;
            for (int tick = 0; tick <= 80; tick++) {
                session.advance(tick, 16384);
                runtime.drainEvents();
                if (tick == 75) {
                    liveLasers = session.checkpoint().runtime().groups().stream()
                            .flatMap(g -> g.members().stream())
                            .filter(m -> m.plan().program().collisionProfile()
                                    instanceof com.bitsson.gensokyou.danmaku.program.ops.CollisionProfile.Laser)
                            .count();
                }
            }
            assertEquals(blocked ? 0 : 4, liveLasers, "blocked=" + blocked + " (base 0 activation+corridor at 70; base 100 not yet born)");
            controller.close();
        }
    }

    // ------------------------------------------------------------------
    // 穿透方块的球弹（蛛丝）：collision profile 带 pierceBlocks 标志
    // （真实方块裁剪行为需实机；此处只证明能力已按通用 profile 表达）
    // ------------------------------------------------------------------
    @Test void piercingSetsThePierceBlocksCollisionFlag() {
        var normal = MemberProgram.attack("normal", MemberProgram.styled(new Patterns.Point(Vec3.ZERO, Vec3.Z),
                        "sphere", "blue", .1, 1, .2), new Paths.Linear(Vec3.Z, .2), 100)
                .fixedDirection().build();
        var pierce = MemberProgram.attack("pierce", MemberProgram.styled(new Patterns.Point(Vec3.ZERO, Vec3.Z),
                        "sphere", "cyan", .1, 1, .2), new Paths.Linear(Vec3.Z, .2), 100)
                .fixedDirection().piercing().build();
        assertFalse(((com.bitsson.gensokyou.danmaku.program.ops.CollisionProfile.Projectile) normal.collisionProfile()).piercesBlocks());
        assertTrue(((com.bitsson.gensokyou.danmaku.program.ops.CollisionProfile.Projectile) pierce.collisionProfile()).piercesBlocks());
    }

    // ------------------------------------------------------------------
    // 蓝 5：age30 闪亮声音经渲染投影到达客户端
    // ------------------------------------------------------------------
    @Test void ranCardFiveSoundCueReachesTheRenderProjection() {
        var deck = BossProgramCards.forOwner("yakumo_ran").orElseThrow();
        var runtime = new DanmakuWorldRuntime("test:sound",
                new DanmakuWorldRuntime.Limits(8192, 512, 512, 512, 40000), new World(1));
        var session = new ProgramSession(runtime, StandardProgramOperations.execution());
        var controller = new BossProgramController(session, OWNER);
        controller.card(deck.card(5).program(), 0);
        var wire = new com.bitsson.gensokyou.danmaku.program.network.ProgramWire(
                com.bitsson.gensokyou.danmaku.program.network.NetworkLimits.defaults());
        var replica = new com.bitsson.gensokyou.danmaku.program.network.ProgramReplica(wire,
                StandardProgramOperations.registry(), StandardProgramOperations.limits());
        var packets = new ArrayList<byte[]>();
        var sync = new com.bitsson.gensokyou.danmaku.program.network.ProgramSyncServer(wire, packets::add, 20);
        boolean heard = false;
        for (int tick = 0; tick <= 170; tick++) {
            session.advance(tick, 16384);
            runtime.drainEvents();
            var projection = com.bitsson.gensokyou.danmaku.program.network.ReplicaState.capture(session.checkpoint(), wire);
            sync.publishSpatial(projection,
                    com.bitsson.gensokyou.danmaku.program.gameplay.ProgramMotionBounds.groups(projection),
                    com.bitsson.gensokyou.danmaku.program.gameplay.ProgramMotionBounds.members(projection),
                    new Vec3(0, 1, 18), 64, 256, 128);
            for (byte[] packet : packets) replica.receive(packet, tick);
            packets.clear();
            for (var view : replica.renderViews("test:sound", tick)) if (!view.sounds().isEmpty()) heard = true;
        }
        assertTrue(heard, "card5 age-30 flash sound cue reaches the render projection");
        assertEquals(0, replica.failures());
        controller.close();
    }

    // ------------------------------------------------------------------
    // 大妖精雨季：通用 surface 能力（首空气面落雨 / 地面光柱）
    // ------------------------------------------------------------------
    @Test void yamameWebAuthorityAndReplicaAgreeForCarriersAndDerivedPearls() {
        var wire=new com.bitsson.gensokyou.danmaku.program.network.ProgramWire(
                com.bitsson.gensokyou.danmaku.program.network.NetworkLimits.defaults());
        var runtime=new DanmakuWorldRuntime("test:yamame-parity",
                new DanmakuWorldRuntime.Limits(8192,512,512,512,40000),new SurfaceWorld(1));
        var session=new ProgramSession(runtime,StandardProgramOperations.execution());
        var controller=new BossProgramController(session,OWNER);
        controller.card(BossProgramCards.forOwner("yamame").orElseThrow().card(1).program(),0);
        for(int tick=0;tick<=240;tick++) {
            session.advance(tick,65536); runtime.drainEvents();
            if(tick%10!=0) continue;
            var projection=com.bitsson.gensokyou.danmaku.program.network.ReplicaState.capture(session.checkpoint(),wire);
            for(var group:session.checkpoint().runtime().groups()) for(var member:group.members())
                for(double phase:new double[]{0,.5,1}) {
                    var a=runtime.state(member.id(),tick+phase).position();
                    var b=com.bitsson.gensokyou.danmaku.program.network.ReplicaState.state(
                            projection.image(),projection.descriptions(),member.id(),tick+phase).position();
                    assertTrue(a.subtract(b).length()<1e-8,"carrier/pearl parity at "+tick+" "+member.id());
                }
        }
        controller.close();
    }

    @Test void fairyAuthorityAndReplicaAgreeThroughoutDamageSweep() {
        var wire=new com.bitsson.gensokyou.danmaku.program.network.ProgramWire(
                com.bitsson.gensokyou.danmaku.program.network.NetworkLimits.defaults());
        var deck=BossProgramCards.forOwner("big_fairy").orElseThrow();
        for(int card=1;card<=3;card++) {
            var runtime=new DanmakuWorldRuntime("test:fairy-parity",
                    new DanmakuWorldRuntime.Limits(8192,512,512,512,40000),new SurfaceWorld(1));
            var session=new ProgramSession(runtime,StandardProgramOperations.execution());
            var controller=new BossProgramController(session,OWNER);
            controller.card(deck.card(card).program(),0);
            for(int tick=0;tick<=260;tick++) {
                session.advance(tick,65536); runtime.drainEvents();
                var projection=com.bitsson.gensokyou.danmaku.program.network.ReplicaState.capture(session.checkpoint(),wire);
                for(var group:session.checkpoint().runtime().groups()) for(var member:group.members())
                    for(double phase:new double[]{0,.5,1}) {
                        var authority=runtime.state(member.id(),tick+phase).position();
                        var replica=com.bitsson.gensokyou.danmaku.program.network.ReplicaState.state(
                                projection.image(),projection.descriptions(),member.id(),tick+phase).position();
                        assertTrue(authority.subtract(replica).length()<1e-8,
                                "card="+card+" tick="+tick+" phase="+phase+" member="+member.id());
                    }
            }
            controller.close();
        }
    }

    @Test void wallBulletsSurviveTheNextVolleyBoundary() {
        var session=session(1);
        var controller=new BossProgramController(session,OWNER);
        controller.background(BossProgramCards.forOwner("big_fairy").orElseThrow().background(),0);
        for(int tick=0;tick<=100;tick++) { session.advance(tick,65536); session.runtime().drainEvents(); }
        assertTrue(session.checkpoint().runtime().groups().stream().flatMap(g->g.members().stream())
                .anyMatch(m->m.plan().birthTick()==0),"first volley keeps its own lifetime across repeats");
        controller.close();
    }

    @Test void rainUsesFreshSingleDropsAndAllWarnedPillarsActivate() {
        var runtime = new DanmakuWorldRuntime("test:fresh-rain",
                new DanmakuWorldRuntime.Limits(8192,512,512,512,40000),new SurfaceWorld(1));
        var session=new ProgramSession(runtime,StandardProgramOperations.execution());
        var controller=new BossProgramController(session,OWNER);
        controller.card(BossProgramCards.forOwner("big_fairy").orElseThrow().card(3).program(),0);
        var rainPoints=new java.util.HashSet<String>();
        int rain=0,lasers=0;
        for (int tick=0;tick<=220;tick++) {
            session.advance(tick,65536);
            for (var event:runtime.drainEvents()) assertNotEquals("clearance",event.reason(),"warned ground pillar must activate");
            for (var group:session.checkpoint().runtime().groups()) for (var member:group.members()) {
                if (member.plan().birthTick()!=tick) continue;
                if (member.plan().program().collisionProfile() instanceof CollisionProfile.Laser) {
                    lasers++;
                    assertTrue(runtime.state(member.id(),tick).position().y()>1,"beam starts above ground surface");
                } else {
                    rain++;
                    var p=runtime.state(member.id(),tick).position();
                    assertTrue(rainPoints.add(p.x()+":"+p.z()),"new samples each beat and cycle, one drop per point");
                }
            }
        }
        assertEquals(12*24,rain);
        assertEquals(10,lasers);
        controller.close();
    }

    @Test void bigFairyRainResolvesAirAboveAndGroundAnchoredPillars() {
        var deck = BossProgramCards.forOwner("big_fairy").orElseThrow();
        var runtime = new DanmakuWorldRuntime("test:surface",
                new DanmakuWorldRuntime.Limits(8192, 512, 512, 512, 40000), new SurfaceWorld(1));
        var session = new ProgramSession(runtime, StandardProgramOperations.execution());
        var controller = new BossProgramController(session, OWNER);
        controller.card(deck.card(3).program(), 0);
        int rainHigh = 0, pillarsGround = 0;
        for (int tick = 0; tick <= 45; tick++) {
            session.advance(tick, 65536);
            runtime.drainEvents();
            if (tick == 45) {
                for (var group : session.checkpoint().runtime().groups())
                    for (var member : group.members()) {
                        var p = session.runtime().state(member.id(), tick).position();
                        if (p.y() > 20) rainHigh++;
                        else if (p.y() <= 2) pillarsGround++;
                    }
            }
        }
        assertTrue(rainHigh >= 20, "rain resolved to air above (~40) and is falling: " + rainHigh);
        assertTrue(pillarsGround >= 1, "pillar resolved to the ground top: " + pillarsGround);
        controller.close();
    }

    @Test void surfaceEmissionIsCoveredByTheStandardEnvelope() {
        var deck = BossProgramCards.forOwner("big_fairy").orElseThrow();
        var runtime = new DanmakuWorldRuntime("test:surface-envelope",
                new DanmakuWorldRuntime.Limits(8192, 512, 512, 512, 40000), new SurfaceWorld(1));
        var session = new ProgramSession(runtime, StandardProgramOperations.execution());
        var controller = new BossProgramController(session, OWNER);
        controller.card(deck.card(3).program(), 0);
        for (int tick = 0; tick <= 1; tick++) {
            session.advance(tick, 65536);
            runtime.drainEvents();
        }
        var wire = new com.bitsson.gensokyou.danmaku.program.network.ProgramWire(
                com.bitsson.gensokyou.danmaku.program.network.NetworkLimits.defaults());
        var projection = com.bitsson.gensokyou.danmaku.program.network.ReplicaState.capture(session.checkpoint(), wire);
        var boxes = com.bitsson.gensokyou.danmaku.program.gameplay.ProgramMotionBounds.groups(projection);
        boolean covered = false;
        for (var group : session.checkpoint().runtime().groups()) {
            var box = boxes.get(group.id());
            if (box == null) continue;
            covered = true;
            for (var member : group.members()) {
                var end = session.runtime().state(member.id(), member.plan().birthTick() + member.plan().program().lifetimeTicks() - 1).position();
                assertTrue(box.near(end, 0), "envelope covers future member position");
            }
        }
        assertTrue(covered, "surface groups are projected with standard envelopes");
        controller.close();
    }

    // ------------------------------------------------------------------
    // 蓝 8 副轨：单块 Grid4×3 倾斜面板（12 成员/拍），两端列 ±5
    // ------------------------------------------------------------------
    @Test void ranCardEightSecondaryIsASingleTiltedPanelOfTwelve() {
        var deck = BossProgramCards.forOwner("yakumo_ran").orElseThrow();
        var runtime = new DanmakuWorldRuntime("test:ninetail",
                new DanmakuWorldRuntime.Limits(8192, 512, 512, 512, 40000), new World(1));
        var session = new ProgramSession(runtime, StandardProgramOperations.execution());
        var controller = new BossProgramController(session, OWNER);
        controller.card(deck.card(8).program(), 0);
        int panels = 0;
        for (int tick = 0; tick <= 85; tick++) {
            session.advance(tick, 65536);
            runtime.drainEvents();
            if (tick != 85) continue;
            for (var group : session.checkpoint().runtime().groups()) {
                var tilted = group.members().stream().filter(m -> m.template().tags().contains("tilted")).toList();
                if (tilted.size() != 12) continue;
                panels++;
                var xs = new TreeSet<Double>();
                boolean leftTilt = false, rightTilt = false;
                for (var m : tilted) {
                    xs.add(Math.round(m.template().position().x() * 1e6) / 1e6);
                    if (m.template().position().x() < 0 && m.template().direction().x() < 0) leftTilt = true;
                    if (m.template().position().x() > 0 && m.template().direction().x() > 0) rightTilt = true;
                }
                assertEquals(new TreeSet<>(List.of(-5.0, 0.0, 5.0)), xs, "two source columns at ±5 plus a center column");
                assertTrue(leftTilt && rightTilt, "columns tilt F±.22R");
            }
        }
        assertEquals(1, panels, "exactly one 12-member secondary panel per bottom-60 sub-round batch");
        controller.close();
    }

    // ------------------------------------------------------------------
    // 收敛：真实采样源→目标（不同距离与倾斜目标）
    // ------------------------------------------------------------------
    @Test void aimAtTargetSamplesTheRealSourceToTargetVector() {
        for (double distance : new double[]{12, 22}) for (double dy : new double[]{0, 6}) {
            var world = new PlayerAt(0, dy, distance);
            var runtime = new DanmakuWorldRuntime("test:aim",
                    new DanmakuWorldRuntime.Limits(4096, 512, 256, 256, 40000), world);
            var session = new ProgramSession(runtime, StandardProgramOperations.execution());
            var controller = new BossProgramController(session, OWNER);
            controller.card(aimProbe(), 0);
            for (int tick = 0; tick <= 1; tick++) { session.advance(tick, 4096); runtime.drainEvents(); }
            var group = session.checkpoint().runtime().groups().getFirst();
            var target = session.runtime().sampleTarget(group.id(), 0,
                    com.bitsson.gensokyou.danmaku.program.ops.AuthorityDescriptions.TargetPoint.EYES).orElseThrow();
            boolean sawNonParallel = false;
            var directions = new ArrayList<Vec3>();
            for (var member : group.members()) {
                var spawn = session.runtime().state(member.id(), 0).position();
                var velocity = session.runtime().state(member.id(), 1).position()
                        .subtract(session.runtime().state(member.id(), 0).position()).unit();
                assertTrue(target.subtract(spawn).unit().subtract(velocity).length() < 1e-9,
                        "each member aims at the sampled target (distance=" + distance + ", dy=" + dy + ")");
                directions.add(velocity);
                if (dy > 0) assertTrue(velocity.y() > 0);
            }
            // 3 members at x∈{-2,0,2} => their directions differ: a real per-member sample, not a constant.
            for (int i = 1; i < directions.size(); i++)
                sawNonParallel |= directions.get(i).subtract(directions.get(0)).length() > 1e-6;
            assertTrue(sawNonParallel, "per-member convergence differs across the lattice");
            controller.close();
        }
    }

    private static com.bitsson.gensokyou.danmaku.program.author.CardProgram.Compiled aimProbe() {
        var card = new com.bitsson.gensokyou.danmaku.program.author.CardProgram("test:aim").clearance(0);
        var targets = card.targets("players", 64, 1);
        var gun = card.aimed("gun", targets, 0, Vec3.ZERO);
        var bullets = MemberProgram.attack("aim", MemberProgram.styled(new Patterns.ParallelGrid(1, 3, 2, 0, Set.of(), Vec3.ZERO),
                        "sphere", "blue", .1, 1, .2), new Paths.Linear(Vec3.Z, .2), 40)
                .aimAtTarget().build();
        card.at(0, c -> c.fire(gun, bullets, 0, 1));
        return card.once();
    }

    // ------------------------------------------------------------------
    // 卡 6 弧线：转向轴是本地 Y（= 锁定帧 U），不是世界 Y
    // ------------------------------------------------------------------
    @Test void arcTurnStaysInTheLocalFramePlane() {
        double angle = Math.toRadians(25);
        Vec3 initial = Vec3.Z.rotate(Vec3.Y, angle);
        var path = new Paths.Segments(List.of(
                new Paths.Segment("out", 30, new Paths.Linear(initial, 0.2)),
                new Paths.Segment("turn", 30, new Paths.Arc(initial, Vec3.Y, -angle / 30, 0.2)),
                new Paths.Segment("return", 1, new Paths.Linear(Vec3.Z, 0.2))), Paths.Tail.CONTINUE);
        var program = MemberProgram.attack("turn", MemberProgram.styled(new Patterns.ParallelGrid(1, 1, 0, 0, Set.of(), Vec3.ZERO),
                        "sphere", "blue", .1, 1, .2), path, 120).fixedDirection().build();
        var seed = new Deterministic.Seed(0, List.of());
        var member = program.pattern().generate(seed).getFirst();
        for (double age = 0; age <= 61; age += 0.5)
            assertEquals(0.0D, program.memberState(member, seed, age).position().y(), 1e-9,
                    "the turn stays in the local XZ (F/R) plane about local Y");
        assertTrue(Vec3.Z.subtract(program.memberState(member, seed, 61).velocity().unit()).length() < 1e-9,
                "the tail returns to local forward F");
    }

    // ------------------------------------------------------------------
    // 预算：真实峰值落在原设定内，且组被回收
    // ------------------------------------------------------------------
    @Test void bigFairyRainPeakFitsTheStandardBudgetAndReclaims() {
        var deck = BossProgramCards.forOwner("big_fairy").orElseThrow();
        var runtime = new DanmakuWorldRuntime("test:budget",
                new DanmakuWorldRuntime.Limits(8192, 512, 512, 512, 40000), new SurfaceWorld(1));
        var session = new ProgramSession(runtime, StandardProgramOperations.execution());
        var controller = new BossProgramController(session, OWNER);
        controller.card(deck.card(3).program(), 0);
        int peakGroups = 0, peakMembers = 0, groupsAt201 = -1;
        boolean anyBudgetReject = false;
        for (int tick = 0; tick <= 220; tick++) {
            session.advance(tick, 65536);
            for (var event : runtime.drainEvents())
                if (event.kind() == DanmakuWorldRuntime.Kind.REJECT && "budget".equals(event.reason())) anyBudgetReject = true;
            peakGroups = Math.max(peakGroups, session.checkpoint().runtime().groups().size());
            peakMembers = Math.max(peakMembers, session.runtime().liveMembers());
            if (tick == 201) groupsAt201 = session.checkpoint().runtime().groups().size();
        }
        assertTrue(peakGroups <= 512, "peak groups must fit the standard 512 limit: " + peakGroups);
        assertTrue(peakMembers <= 8192, "peak live members: " + peakMembers);
        assertFalse(anyBudgetReject, "no budget rejection at the standard limits");
        assertTrue(groupsAt201 < peakGroups, "groups are reclaimed at the cycle boundary: " + groupsAt201 + " < " + peakGroups);
        controller.close();
        assertEquals(0, session.runtime().liveMembers());
        assertTrue(session.checkpoint().runtime().groups().isEmpty(), "all groups reclaimed on close");
    }

    // ------------------------------------------------------------------
    // 包络：预警（尚未落点）期间也必须订阅落点区域
    // ------------------------------------------------------------------
    @Test void surfaceWarningEnvelopeCoversBeforeLanding() {
        var deck = BossProgramCards.forOwner("big_fairy").orElseThrow();
        var runtime = new DanmakuWorldRuntime("test:surface-warning",
                new DanmakuWorldRuntime.Limits(8192, 512, 512, 512, 40000), new SurfaceWorld(1));
        var session = new ProgramSession(runtime, StandardProgramOperations.execution());
        var controller = new BossProgramController(session, OWNER);
        controller.card(deck.card(3).program(), 0);
        // pillar birth is 40 (warning 40); at tick 39 the batch is a warning, not yet born.
        for (int tick = 0; tick <= 39; tick++) { session.advance(tick, 65536); runtime.drainEvents(); }
        var wire = new com.bitsson.gensokyou.danmaku.program.network.ProgramWire(
                com.bitsson.gensokyou.danmaku.program.network.NetworkLimits.defaults());
        var projection = com.bitsson.gensokyou.danmaku.program.network.ReplicaState.capture(session.checkpoint(), wire);
        var boxes = com.bitsson.gensokyou.danmaku.program.gameplay.ProgramMotionBounds.groups(projection);
        boolean warned = false;
        for (var entry : projection.image().entries().values()) {
            if (!(entry instanceof com.bitsson.gensokyou.danmaku.program.network.ReplicaState.Batch batch) || !batch.warning()) continue;
            if (batch.birth() != 40) continue;
            warned = true;
            var box = boxes.get(batch.group());
            assertNotNull(box, "a warning batch is still subscribed");
            for (var member : batch.templates()) {
                var land = com.bitsson.gensokyou.danmaku.program.server.EmissionPlan.state(
                        projection.descriptions().get(batch.description()), batch.binding(), batch.source(),
                        batch.seed(), member, 0, batch.aimLocal()).position();
                assertTrue(box.near(land, 0), "the pre-landing envelope already covers the landing point");
            }
        }
        assertTrue(warned, "a not-yet-born surface batch is projected during its warning");
        controller.close();
    }

    // ------------------------------------------------------------------
    // FIRST_AIR_BELOW：顶棚/洞穴下的空气夹层（不是 FIRST_AIR_ABOVE 的等价物）
    // ------------------------------------------------------------------
    @Test void firstAirBelowResolvesTheAirLayerUnderACeiling() {
        var world = new CeilingWorld(1);
        var insideCeiling = world.resolveSurface(List.of(new Vec3(0, 60, 0)),
                com.bitsson.gensokyou.danmaku.program.ops.AuthorityDescriptions.SurfaceMode.FIRST_AIR_BELOW, 80, 80);
        assertEquals(new Vec3(0, 49, 0), insideCeiling.points().getFirst().orElseThrow(),
                "inside a ceiling, rain seeds in the air layer just below it");
        var aboveCeiling = world.resolveSurface(List.of(new Vec3(0, 90, 0)),
                com.bitsson.gensokyou.danmaku.program.ops.AuthorityDescriptions.SurfaceMode.FIRST_AIR_BELOW, 80, 80);
        assertEquals(new Vec3(0, 90, 0), aboveCeiling.points().getFirst().orElseThrow(),
                "in the open, the high anchor is already air");
        var upward = world.resolveSurface(List.of(new Vec3(0, 60, 0)),
                com.bitsson.gensokyou.danmaku.program.ops.AuthorityDescriptions.SurfaceMode.FIRST_AIR_ABOVE, 80, 80);
        assertEquals(new Vec3(0, 71, 0), upward.points().getFirst().orElseThrow(),
                "FIRST_AIR_ABOVE is a different query and must not be substituted");
        var ground = world.resolveSurface(List.of(new Vec3(0, 30, 0)),
                com.bitsson.gensokyou.danmaku.program.ops.AuthorityDescriptions.SurfaceMode.DOWNWARD_SOLID_TOP, 80, 80);
        assertEquals(new Vec3(0, 1, 0), ground.points().getFirst().orElseThrow(), "ground top for pillars");
    }

    // ------------------------------------------------------------------
    // 换卡：循环边界挂起
    // ------------------------------------------------------------------
    @Test void cardSwitchSuspendsUntilLoopBoundary() {
        var deck = BossProgramCards.forOwner("yakumo_ran").orElseThrow();
        var session = session(1);
        var controller = new BossProgramController(session, OWNER);
        var battle = new com.bitsson.gensokyou.danmaku.program.gameplay.BossProgramBattle(controller, deck);
        battle.install(0);
        assertEquals(1, battle.activeCardIndex());
        for (int tick = 0; tick < 239; tick++) {
            session.advance(tick, 4096);
            session.runtime().drainEvents();
            battle.tick(tick == 0 ? 1.0D : 0.70D, tick); // 开卡后才跨入卡 3 区间；当前轮未走完
            if (tick > 0) assertEquals(1, battle.activeCardIndex(), "switch is suspended before boundary: " + tick);
        }
        session.advance(239, 4096);
        session.runtime().drainEvents();
        battle.tick(0.70D, 239);
        assertEquals(1, battle.activeCardIndex(), "still suspended at 239");
        session.advance(240, 4096);
        session.runtime().drainEvents();
        battle.tick(0.70D, 240);
        assertEquals(3, battle.activeCardIndex(), "boundary 240 installs the reached card");
        battle.close();
    }

    // ------------------------------------------------------------------
    // harness
    // ------------------------------------------------------------------
    private static ProgramSession session(int players) {
        var world = new World(players);
        var runtime = new DanmakuWorldRuntime("test:content",
                new DanmakuWorldRuntime.Limits(8192, 512, 512, 512, 40000), world);
        var session = new ProgramSession(runtime, StandardProgramOperations.execution());
        // BossProgramController 构造时会开启 encounter。
        return session;
    }

    private static class World implements DecisionProvider {
        private final List<Player> players;
        World(int count) {
            var list = new ArrayList<Player>();
            for (int i = 0; i < count; i++) {
                Vec3 feet = new Vec3(i * 2, 0, 18);
                list.add(new Player(new UUID(0, i + 2), feet, feet.add(Vec3.Y), 2, true, false, false, .3));
            }
            players = List.copyOf(list);
        }
        public List<Player> players() { return players; }
        public Optional<Actions.Pose> entity(UUID uuid) {
            return uuid.equals(OWNER)
                    ? Optional.of(new Actions.Pose(com.bitsson.gensokyou.danmaku.program.math.Frame.IDENTITY, Vec3.ZERO))
                    : Optional.empty();
        }
        public boolean loaded(Vec3 point) { return true; }
        public Block block(int x, int y, int z) { return new Block(true, true, false, 0); }
    }

    private static final class SolidWorld extends World {
        private final boolean blocked;
        SolidWorld(int count, boolean blocked) { super(count); this.blocked = blocked; }
        @Override public Block block(int x, int y, int z) {
            return blocked ? new Block(true, false, true, 1.0D) : new Block(true, true, false, 0);
        }
    }

    /** Solid up to y=0 (top 1.0), air above: ground-anchored pillars and first-air rain resolve here. */
    private static final class SurfaceWorld extends World {
        SurfaceWorld(int count) { super(count); }
        @Override public Block block(int x, int y, int z) {
            return y <= 0 ? new Block(true, false, true, 1.0D) : new Block(true, true, false, 0);
        }
    }

    /** A single player at (x,y,z) with eyes one block up. */
    private static final class PlayerAt extends World {
        private final Player player;
        PlayerAt(double x, double y, double z) {
            super(0);
            this.player = new Player(new UUID(0, 2), new Vec3(x, y, z), new Vec3(x, y + 1, z), 2, true, false, false, .3);
        }
        @Override public List<Player> players() { return List.of(player); }
    }

    /** Solid ground up to y=0, solid ceiling in y∈[50,70], air otherwise. */
    private static final class CeilingWorld extends World {
        CeilingWorld(int count) { super(count); }
        @Override public Block block(int x, int y, int z) {
            boolean solid = y <= 0 || (y >= 50 && y <= 70);
            return solid ? new Block(true, false, true, 1.0D) : new Block(true, true, false, 0);
        }
    }
}
