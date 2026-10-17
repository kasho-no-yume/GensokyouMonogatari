package com.bitsson.gensokyou.client;

import com.bitsson.gensokyou.danmaku.program.network.ProgramRenderView;

import java.util.HashSet;
import java.util.Set;

/** Bounded client-side sound deduplication for program presentation events. */
public final class ProgramSoundLedger {
    private final Set<String> played=new HashSet<>();
    private long epoch=-1;

    public boolean accept(long encounterEpoch,ProgramRenderView.Sound sound) {
        if (encounterEpoch!=epoch) { epoch=encounterEpoch; played.clear(); }
        return played.add(sound.group()+"/"+sound.batch()+"/"+sound.deduplicationKey());
    }

    public int size() { return played.size(); }
    public void clear() { played.clear(); epoch=-1; }
}
