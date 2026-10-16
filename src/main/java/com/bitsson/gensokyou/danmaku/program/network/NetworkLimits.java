package com.bitsson.gensokyou.danmaku.program.network;

/** Explicit protocol resource budgets. Hosts may supply tighter limits. */
public record NetworkLimits(int messageBytes, int snapshotBytes, int entries, int members,
                            int chunks, int pendingTransfers, int cachedDefinitions,
                            int history, int staleTicks, int requestInterval) {
    public NetworkLimits {
        if (messageBytes<1024 || snapshotBytes<messageBytes || entries<1 || members<1 || chunks<1
                || pendingTransfers<1 || cachedDefinitions<1 || history<1 || staleTicks<1 || requestInterval<1)
            throw new IllegalArgumentException("positive bounded network limits required");
    }
    public static NetworkLimits defaults() {
        return new NetworkLimits(32768,4194304,16384,16384,256,4,1024,128,200,20);
    }
}
