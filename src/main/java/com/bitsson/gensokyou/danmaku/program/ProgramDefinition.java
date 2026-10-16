package com.bitsson.gensokyou.danmaku.program;

import java.util.List;

/** A compiled immutable definition. Only the compiler in this package constructs it. */
public final class ProgramDefinition {
    private final String id;
    private final int schemaVersion;
    private final PlaybackPolicy playback;
    private final ProgramNode root;
    private final List<ProgramNode> nodes;
    private final ProgramBounds bounds;
    private final String hash;

    ProgramDefinition(String id, int schemaVersion, PlaybackPolicy playback, ProgramNode root,
                      List<ProgramNode> nodes, ProgramBounds bounds, String hash) {
        this.id=id; this.schemaVersion=schemaVersion; this.playback=playback; this.root=root;
        this.nodes=List.copyOf(nodes); this.bounds=bounds; this.hash=hash;
    }
    public String id() { return id; }
    public int schemaVersion() { return schemaVersion; }
    public PlaybackPolicy playback() { return playback; }
    public ProgramNode root() { return root; }
    public List<ProgramNode> nodes() { return nodes; }
    public ProgramBounds bounds() { return bounds; }
    public String hash() { return hash; }
}
