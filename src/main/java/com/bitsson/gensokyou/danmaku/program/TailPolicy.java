package com.bitsson.gensokyou.danmaku.program;

/** Describes ownership at a normal playback boundary; cancellation is separate. */
public enum TailPolicy {
    RETAIN_UNTIL_LIFETIME,
    END_AT_BOUNDARY
}
