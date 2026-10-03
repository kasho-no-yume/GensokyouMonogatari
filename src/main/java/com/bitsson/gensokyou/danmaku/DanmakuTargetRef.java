package com.bitsson.gensokyou.danmaku;

import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * 「弹体 → 目标」的引用与解析。
 *
 * <p><b>为什么抽出来</b>：目标身份用 UUID 而非网络 id 这件事，值钱的地方不在编码，
 * 而在<b>解析时不得回退</b>。一旦解析函数里出现「UUID 查不到就按 id 找」的兜底，
 * id 复用导致的静默错追就会从这条路径复活 —— 而那正是本类要消灭的东西。
 * 把解析收敛成这一个方法、并让它对任意值类型可测，这条规则才有可执行的断言。
 *
 * <p><b>为什么用 {@link Optional} 而不是「空 UUID」</b>：全零 UUID 是一个合法的
 * {@link UUID}（可以真的被随机分配到），拿它当哨兵值需要一条额外的不变式来维持，
 * 而不变式一旦被破坏就是一次静默错追。{@code Optional.empty()} 与任何 UUID 都可区分，
 * 不需要额外约定。
 *
 * @see com.bitsson.gensokyou.client.ClientEntityUuidIndex 客户端的 UUID 查询实现
 */
public final class DanmakuTargetRef {

    private DanmakuTargetRef() {
    }

    /** 实体 → 同步值。{@code null}（无目标）映射为 {@link Optional#empty()}。 */
    public static Optional<UUID> of(@Nullable Entity target) {
        return target == null ? Optional.empty() : Optional.of(target.getUUID());
    }

    /**
     * 同步值 → 实体。
     *
     * <p><b>不提供任何回退</b>：{@code lookup} 查不到就是查不到，返回 {@code null}
     * 让调用方按「无目标」表现。这与「按网络 id 找同名实体」是两种不同的行为，
     * 前者可诊断，后者静默。
     *
     * <p>泛型化的唯一理由是可测：把「id 被复用后是否会追到新实体」这条规则
     * 写成不依赖 Minecraft 世界的单元测试。
     *
     * @return 目标实体，或 {@code null} 表示当前无目标（未持有 / 已丢失 / 查不到）
     */
    @Nullable
    public static <T> T resolve(@Nullable Optional<UUID> targetId, Function<UUID, T> lookup) {
        if (targetId == null || targetId.isEmpty()) {
            return null;
        }
        return lookup.apply(targetId.get());
    }
}