package com.bitsson.gensokyou.danmaku.program.ops;

import java.util.List;

/** Stable template identity selection; never enumerates a live, compacted list. */
public interface Selection {
    boolean includes(Pattern.Member member);
    record IndexRange(int fromInclusive,int untilExclusive) implements Selection {
        public IndexRange { if (fromInclusive<0 || untilExclusive<fromInclusive) throw new IllegalArgumentException("invalid range"); }
        public boolean includes(Pattern.Member m) { return m.index()>=fromInclusive && m.index()<untilExclusive; }
    }
    record IndexModulo(int modulus,int remainder) implements Selection {
        public IndexModulo { if (modulus<1 || remainder<0 || remainder>=modulus) throw new IllegalArgumentException("invalid modulo"); }
        public boolean includes(Pattern.Member m) { return m.index()%modulus==remainder; }
    }
    record Tag(String tag) implements Selection {
        public Tag { if (tag==null || tag.isBlank()) throw new IllegalArgumentException("tag required"); }
        public boolean includes(Pattern.Member m) { return m.tags().contains(tag); }
    }
    record TargetSlot(int slot) implements Selection {
        public TargetSlot { if (slot<0 || slot>4) throw new IllegalArgumentException("slot in 0..4"); }
        public boolean includes(Pattern.Member m) { return m.targetSlot()==slot; }
    }
    record AllOf(List<Selection> selections) implements Selection {
        public AllOf { selections=List.copyOf(selections); if (selections.size()>64) throw new IllegalArgumentException("selection limit"); }
        public boolean includes(Pattern.Member m) { return selections.stream().allMatch(s->s.includes(m)); }
    }
}
