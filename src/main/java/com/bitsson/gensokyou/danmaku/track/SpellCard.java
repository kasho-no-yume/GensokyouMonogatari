package com.bitsson.gensokyou.danmaku.track;

import java.util.List;

/**
 * 一张符卡 = 一个战斗阶段 = 1~3 条并发轨道。
 *
 * <p>与玩家符卡<b>语义无关</b>：玩家符卡是一次性技能；BOSS 符卡是按血量阈值切出的阶段。
 * 血条读整体生命占比，故符卡不设独立生命池。
 *
 * @param name 符卡名（如「経糸」）
 * @param hpFraction 该阶段的<b>起始</b>生命占比（1.0 = 满血进本卡；0.5 = 半血进本卡）
 * @param tracks 并发轨道，1~3 条
 */
public record SpellCard(String name, double hpFraction, List<Track> tracks) {

    public SpellCard {
        tracks = List.copyOf(tracks);
    }

    /**
     * 本符卡覆盖的生命区间为 {@code [nextHpFraction, hpFraction]}。
     *
     * <p>{@code nextHpFraction} 是<b>下一张</b>符卡的起始占比；最后一张的下界为 0。
     * 用它做区间判定，而不是「占比是否低于本卡阈值」——后者对首卡恒真，会导致符卡永不切换。
     */
    public boolean covers(double nextHpFraction, double currentFraction) {
        return currentFraction >= nextHpFraction && currentFraction <= hpFraction + 1.0E-6D;
    }

    public boolean activeAt(double currentFraction) {
        return covers(0.0D, currentFraction);
    }

    /** 本符卡内所有轨道是否都无终止条件（缺結型符卡的判据）。 */
    public boolean allEndless() {
        return tracks.stream().noneMatch(Track::terminates);
    }
}
