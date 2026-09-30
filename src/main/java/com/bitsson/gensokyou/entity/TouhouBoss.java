package com.bitsson.gensokyou.entity;

import net.minecraft.network.chat.Component;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * 东方 BOSS 标记（touhou-boss-bar）：该实体在场时，其原版 BOSS 血条被改绘为咒符条。
 *
 * <p><b>刻意放在 common 侧</b>。BOSS 实体本身是 common 类（服务端与客户端
 * 都要加载），若把标记嵌在客户端渲染器里，`FlandreEntity` 为了 implements 它就得让服务端
 * 去加载那个客户端类——而它引用了 {@code LerpingBossEvent} / {@code CustomizeGuiOverlayEvent}
 * 等纯客户端类型，专用服务器会直接崩在类加载阶段。空接口则没有这个风险。
 *
 * <p>做成接口而非"逐个 instanceof 列出具体类"：新增 BOSS 只要 implements 这一个标记就自动
 * 套用咒符条，不会因为漏改判定而悄悄退回原版血条。判定方见
 * {@code TouhouBossBarRenderer#isTouhouBoss}。
 */
public interface TouhouBoss {

    /**
     * 本 BOSS 的<b>自身</b>品阶（1~5），决定咒符条用哪一套边框造型。
     *
     * <p>与 {@code AbstractTouhouBoss#referenceTier()} 是<b>两个不同的东西</b>：
     * 后者问"按几阶玩家做数值基准"（算生命/伤害预算），本方法问"这只 BOSS 是几阶"。
     * 两者刻意不互相推导——一只 1 阶 BOSS 完全可能按 3 阶玩家做预算。
     *
     * <p>本方法 SHALL 为编译期常数：MUST NOT 随召唤它的仪式等级、玩家探索进度或战斗进程
     * 变化，否则血条外观会在一场战斗中途改形状。
     *
     * <p>本方法 MUST NOT 影响任何数值平衡，只驱动外观。
     *
     * <p>越界值由消费方（客户端取贴图时）夹取，此处不抛异常——一个装饰性数值
     * 不该有能力把游戏打崩。
     */
    default int bossTier() {
        return 1;
    }

    /**
     * 当前生效符卡在符卡表中的下标，供 HUD 在血条下方显示符卡名。
     *
     * <p>返回 {@link OptionalInt#empty()} 表示"本实体没有可显示的符卡"，HUD 据此
     * 不绘制该行且行高回落。
     *
     * <p><b>本方法必须带缺省实现</b>（而不是抽成 {@code AbstractTouhouBoss} 的抽象方法）：
     * {@code FlandreEntity} 继承了 {@code Monster} 而非 {@code AbstractTouhouBoss}，
     * 它没有符卡表也没有 {@code TrackRunner}。四重存在的"血量比 ⇒ 选阶段"对它根本不成立，
     * 把它绑进同一套机制是错的。
     */
    default OptionalInt activeCardIndex() {
        return OptionalInt.empty();
    }

    /**
     * 本实体符卡表中第 {@code index} 张卡的显示名，供 HUD 在血条下方那一行使用。
     *
     * <p>网络包只传<b>下标</b>，名字由客户端在本地表里反查——于是 Component 永不上线，
     * 也顺带避开了「服务端语言环境与客户端不同导致显示不一致」。
     *
     * <p>本方法与 {@link #activeCardIndex()} 分开，是因为读写两侧拿不到彼此的时机：
     * 服务端在切卡时只有 {@code TrackRunner}，客户端只有下标。
     *
     * <p>返回 {@link Optional#empty()} 表示无此卡（无符卡表 / 下标越界），
     * HUD 据此不绘制该行。
     */
    default Optional<Component> spellCardName(int index) {
        return Optional.empty();
    }
}
