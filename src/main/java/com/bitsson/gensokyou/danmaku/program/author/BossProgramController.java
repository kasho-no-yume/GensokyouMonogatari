package com.bitsson.gensokyou.danmaku.program.author;

import com.bitsson.gensokyou.danmaku.program.ClockAnchors;
import com.bitsson.gensokyou.danmaku.program.server.*;
import java.util.*;

/** Battle-local control over a shared session. Does not opt any existing BOSS into programs. */
public final class BossProgramController implements AutoCloseable {
    private final ProgramSession session;
    private final DanmakuWorldRuntime.Encounter encounter;
    private final boolean resumed;
    private long card;
    private boolean closed;
    private boolean backgroundInstalled;
    public BossProgramController(ProgramSession session,UUID owner) {
        this.session=Objects.requireNonNull(session);
        encounter=session.runtime().startEncounter(Objects.requireNonNull(owner));
        resumed=false;
    }
    private BossProgramController(ProgramSession session,DanmakuWorldRuntime.Encounter encounter) {
        this.session=Objects.requireNonNull(session);
        this.encounter=Objects.requireNonNull(encounter);
        this.resumed=true;
        // Recover the lifecycle identity so a later card() ends the real previous card (not card 0)
        // and background() does not install a second default layer.
        this.card=session.issuedCard(encounter);
        this.backgroundInstalled=session.backgroundInstalled(encounter);
    }
    /** Wrap an encounter restored from disk: it already owns live instances, so no re-install occurs. */
    public static BossProgramController resumed(ProgramSession session,DanmakuWorldRuntime.Encounter encounter) {
        if (!session.runtime().active(encounter)) throw new IllegalArgumentException("encounter is not active");
        return new BossProgramController(session,encounter);
    }
    public boolean resumed() { return resumed; }
    public String currentDefinitionHash() { return session.currentDefinitionHash(encounter); }
    public long runtimeTick() { return session.runtime().tick(); }
    public long card(CardProgram.Compiled program,long firstTick) {
        requireActive();
        if (firstTick<=session.runtime().tick()) throw new IllegalArgumentException("card must start after completed boundary");
        StandardProgramOperations.execution().validate(program.definition());
        session.endCard(encounter,card);
        card=Math.addExact(card,1);
        return install(program,card,false,firstTick);
    }
    public long background(CardProgram.Compiled program,long firstTick) {
        requireActive();
        if (backgroundInstalled) throw new IllegalStateException("background already installed");
        long instance=install(program,0,true,firstTick); backgroundInstalled=true; return instance;
    }
    private long install(CardProgram.Compiled program,long card,boolean background,long firstTick) {
        return session.install(encounter,card,background,program.definition(),new ClockAnchors(firstTick,firstTick,null,null),firstTick);
    }
    public boolean movementLocked() { return active() && session.runtime().movementLocked(encounter); }
    public boolean active() { return !closed && session.runtime().active(encounter); }
    public DanmakuWorldRuntime.Encounter encounter() { return encounter; }
    private void requireActive() { if (!active()) throw new IllegalStateException("battle is ended"); }
    @Override public void close() {
        if (closed) return;
        if (session.runtime().active(encounter)) session.runtime().endEncounter(encounter,"boss-controller-close"); closed=true;
    }
}
