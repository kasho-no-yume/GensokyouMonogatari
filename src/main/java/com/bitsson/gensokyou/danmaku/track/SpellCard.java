package com.bitsson.gensokyou.danmaku.track;

import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 一张符卡 = 一个战斗阶段 = 1~3 条并发轨道。
 *
 * <p>与玩家符卡<b>语义无关</b>：玩家符卡是一次性技能；BOSS 符卡是按血量阈值切出的阶段。
 * 血条读整体生命占比，故符卡不设独立生命池。
 *
 * @param name 符卡名（如「経糸」）。
 *        <p><b>是 {@link Component} 而非 String</b>：符卡名要显示在 HUD 上（血条下方），
 *        而 HUD 必须能显示英文名，故 MUST 走 lang 键 {@code spellcard.gensokyou.<boss_id>.<序号>}。
 *        <p>网络包只传<b>下标</b>，名字由客户端在本地表里反查，所以 Component 永不上线。
 *        <p>调试输出须用 {@code name().getString()}——直接 {@code + name} 走
 *        {@code Object.toString()}，缺键时吐的是原始键名而不是名字。
 * @param hpFraction 该阶段的<b>起始</b>生命占比（1.0 = 满血进本卡；0.5 = 半血进本卡）
 * @param cycleTicks 本符卡的<b>循环长度</b>（tick）。
 *        <p>声明它即声明「本卡有自己的编排单元」：血量跨到下一张卡的门槛时，
 *        切换被<b>挂起</b>，直到本卡的循环走完才发生——于是符卡之间不叠加半程。
 *        <p>{@code <= 0} 表示未声明：此时保持既有的即时切换。该豁免让
 *        「有循环的符卡」与「纯重复的符卡」得以共存，而不必强制后者定义一个
 *        它自身没有的循环边界。
 * @param tracks 并发轨道，1~3 条
 * @param densityWaiver 本卡的<b>密度豁免</b>（颗/玩家）。{@code 0} = 不豁免，按
 *        {@link TrackLint#STEADY_STATE_BUDGET} 判定。
 *        <p>它是一条<b>有界</b>的例外，不是空白支票：{@code TrackLint} 会拒掉超过
 *        预算 8 倍的豁免，且本字段 MUST 附注释写明理由。
 *        <p><b>现存唯一用例</b>：大妖精「花符【花之海洋】」。该卡的 598 颗/玩家估值
 *        假设玩家站在爆散中心，而花是在 BOSS 身边爆散的、BOSS 距离带下界 10 格——
 *        玩家物理上到不了花心。真实局面是 598 颗摊在一层层外扩的球壳上
 *        （15 格半径处 0.21 颗/格²），玩家身边 6 格内约 25 颗。
 *        静态估值看不出这一点，因为「玩家离爆散中心多远」是 BOSS 级参数、轨道表里没有。
 *        <b>本条待实测复核</b>：中弹率与画面密度测过之后，要么把估值模型补上距离项
 *        （正解），要么撤掉豁免并降低该卡密度。
 */
public record SpellCard(Component name, double hpFraction, List<Track> tracks, int cycleTicks,
                         double densityWaiver) {

    public SpellCard {
        tracks = List.copyOf(tracks);
    }

    /** 未声明循环长度、未豁免密度的构造（保持既有行为）。 */
    public SpellCard(Component name, double hpFraction, List<Track> tracks) {
        this(name, hpFraction, tracks, 0, 0.0D);
    }

    /**
     * 字面量名字的便捷构造。
     *
     * <p>在役符卡一律用 {@link BossCards#card} 走 lang 键（要显示英文名就得走键），
     * 但<b>测试与离线校验</b>用字面量更合适：断言时不必先查键名。
     */
    public SpellCard(String name, double hpFraction, List<Track> tracks) {
        this(Component.literal(name == null ? "" : name), hpFraction, tracks, 0, 0.0D);
    }

    /** 声明了循环长度（不豁免密度）。 */
    public SpellCard(Component name, double hpFraction, List<Track> tracks, int cycleTicks) {
        this(name, hpFraction, tracks, cycleTicks, 0.0D);
    }


    /** 本符卡是否声明了循环长度。 */
    public boolean hasCycle() {
        return cycleTicks > 0;
    }

    /** 本符卡是否声明了密度豁免。 */
    public boolean hasDensityWaiver() {
        return densityWaiver > 0.0D;
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
