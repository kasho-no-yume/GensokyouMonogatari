package com.bitsson.gensokyou.danmaku.program.ops;

import com.bitsson.gensokyou.danmaku.program.TimeDomain;
import com.bitsson.gensokyou.danmaku.program.math.*;
import java.util.List;

/** Analytic local presentation/collision gates. No sound playback or rendering side effects. */
public final class Presentation {
    private Presentation() {}
    public record Window(TimeDomain clock,double fromInclusive,double untilExclusive) {
        public Window { java.util.Objects.requireNonNull(clock); Deterministic.nonnegative(fromInclusive); Deterministic.finite(untilExclusive); if (untilExclusive<=fromInclusive) throw new IllegalArgumentException("nonempty half-open window required"); }
        public boolean contains(double t) { Deterministic.finite(t); return t>=fromInclusive && t<untilExclusive; }
    }
    public record Clocks(double card,double cycle,double group,double member) {
        public Clocks { Deterministic.finite(card,cycle,group,member); }
        public double time(TimeDomain domain) { return switch(domain) { case CARD->card; case CYCLE->cycle; case GROUP->group; case MEMBER_AGE->member; }; }
    }
    public record VisibilityCycle(TimeDomain clock,double period,double visibleTicks,double phase) {
        public VisibilityCycle { java.util.Objects.requireNonNull(clock); Deterministic.positive(period); Deterministic.nonnegative(visibleTicks); Deterministic.finite(phase); if (visibleTicks>period) throw new IllegalArgumentException("visible duration exceeds period"); }
        public boolean visible(Clocks clocks) { double t=clocks.time(clock)-phase; return t-StrictMath.floor(t/period)*period<visibleTicks; }
    }
    public record Flash(Window window,double sizeMultiplier,double brightness) {
        public Flash { java.util.Objects.requireNonNull(window); Deterministic.nonnegative(sizeMultiplier); Deterministic.nonnegative(brightness); }
    }
    public record SoundCue(TimeDomain clock,double tick,String sound,String deduplicationKey) {
        public SoundCue { java.util.Objects.requireNonNull(clock); Deterministic.nonnegative(tick); if (sound==null || sound.isBlank() || deduplicationKey==null || deduplicationKey.isBlank()) throw new IllegalArgumentException("sound and stable deduplication key required"); }
        public boolean due(double fromInclusive,double untilExclusive) { return tick>=fromInclusive && tick<untilExclusive; }
    }
    public record Fade(TimeDomain clock,ScalarCurve alpha) {
        public Fade { java.util.Objects.requireNonNull(clock); java.util.Objects.requireNonNull(alpha); if (alpha.keys().stream().anyMatch(k->k.value()<0 || k.value()>1)) throw new IllegalArgumentException("alpha in [0,1]"); }
    }
    public record Billboard(String texture,double size,int tint,ScalarCurve visualAngleRadians) {
        public Billboard { if (texture==null || texture.isBlank()) throw new IllegalArgumentException("texture required"); Deterministic.positive(size); java.util.Objects.requireNonNull(visualAngleRadians); }
    }
    public record Telegraph(String planId,double leadTicks,String display,int tint) {
        public Telegraph { if (planId==null || planId.isBlank() || display==null || display.isBlank()) throw new IllegalArgumentException("plan/display required"); Deterministic.nonnegative(leadTicks); }
    }
    public record State(boolean visible,boolean damaging,double alpha,double sizeMultiplier,double brightness,boolean warning) {}
    public record Profile(List<Window> damageWindows,VisibilityCycle visibility,Fade fade,List<Flash> flashes) {
        public Profile { damageWindows=List.copyOf(damageWindows); flashes=List.copyOf(flashes); if (damageWindows.size()>64 || flashes.size()>64) throw new IllegalArgumentException("presentation limit"); }
        public State at(Clocks clocks) {
            boolean visible=visibility==null || visibility.visible(clocks);
            boolean damage=damageWindows.stream().anyMatch(w->w.contains(clocks.time(w.clock())));
            double alpha=fade==null?1:fade.alpha().at(StrictMath.max(0,clocks.time(fade.clock()))),size=1,brightness=1;
            for (Flash flash:flashes) if (flash.window().contains(clocks.time(flash.window().clock()))) { size*=flash.sizeMultiplier(); brightness*=flash.brightness(); }
            // Fade alone does not close damage. VisibilityCycle explicitly does.
            return new State(visible,visible&&damage,visible?alpha:0,size,brightness,false);
        }
    }
    public record LaserWindow(TimeDomain clock,double warningStart,double activeStart,double end) {
        public LaserWindow { java.util.Objects.requireNonNull(clock); Deterministic.nonnegative(warningStart); Deterministic.finite(activeStart,end); if (activeStart<warningStart || end<=activeStart) throw new IllegalArgumentException("ordered laser phases required"); }
        public State at(Clocks clocks) {
            double t=clocks.time(clock); boolean warning=t>=warningStart && t<activeStart, active=t>=activeStart && t<end;
            return new State(warning||active,active,warning||active?1:0,1,1,warning);
        }
    }
}
