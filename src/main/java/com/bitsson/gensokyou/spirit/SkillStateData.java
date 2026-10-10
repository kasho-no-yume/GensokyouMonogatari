package com.bitsson.gensokyou.spirit;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 符卡技能状态（skill-slots-hud v2）：抽象槽模型——槽位与卡牌解绑。
 *
 * <p>槽位数量 = 玩家超人类阶级（0-5），本记录只存 5 格容量；
 * learned=已学卡池（正式途径受阶级上限，调试命令豁免），
 * equipped[i]=槽 i 配装的卡 id（空串=未配装），cooldownUntil[i]=槽冷却。
 *
 * <p>旧档（cd0/cd1/cd2 + 槽位=固定卡）迁移：equipped 按旧 {@link SpellCardEffects#SLOT_ORDER}
 * 过滤已学卡落位，冷却从旧槽位号搬运到该卡所在新槽位。
 */
public record SkillStateData(List<String> learned, List<Long> cooldownUntil, List<String> equipped) {

    /** 槽位容量上限（阶级 5 满配）。 */
    public static final int MAX_SLOTS = 5;
    /** 槽未配装占位符。 */
    public static final String EMPTY = "";

    /**
     * 阶级 → 可解锁槽数（单一事实来源）：{@code max(0, 阶级 − 2)}。
     * 阶级 0/1/2/3/4/5 → 0/0/0/1/2/3 槽。HUD 显示槽数、学卡上限、施放校验三处共用。
     */
    public static int slotCountForTier(int tier) {
        return Math.max(0, Math.min(MAX_SLOTS, tier - 2));
    }

    private static final List<Long> NO_CD = List.of(0L, 0L, 0L, 0L, 0L);
    private static final List<String> NO_EQUIP =
            List.of(EMPTY, EMPTY, EMPTY, EMPTY, EMPTY);

    /** 持久化形态：新字段 + 旧三槽冷却（仅读，用于迁移）。 */
    private record Raw(List<String> learned, List<Long> cds, List<String> equipped,
                       long cd0, long cd1, long cd2) {

        static final Codec<Raw> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        Codec.STRING.listOf().optionalFieldOf("learned", List.of())
                                .forGetter(Raw::learned),
                        Codec.LONG.listOf().optionalFieldOf("cds", List.of())
                                .forGetter(Raw::cds),
                        Codec.STRING.listOf().optionalFieldOf("equipped", List.of())
                                .forGetter(Raw::equipped),
                        Codec.LONG.optionalFieldOf("cd0", 0L).forGetter(Raw::cd0),
                        Codec.LONG.optionalFieldOf("cd1", 0L).forGetter(Raw::cd1),
                        Codec.LONG.optionalFieldOf("cd2", 0L).forGetter(Raw::cd2)
                ).apply(instance, Raw::new));
    }

    public static final Codec<SkillStateData> CODEC =
            Raw.CODEC.xmap(SkillStateData::fromRaw,
                    state -> new Raw(state.learned(), state.cooldownUntil(), state.equipped(),
                            0L, 0L, 0L));

    private static SkillStateData fromRaw(Raw raw) {
        List<Long> cds = padCds(raw.cds());
        List<String> equipped = padEquipped(raw.equipped());
        boolean hasLegacy = raw.cd0() != 0L || raw.cd1() != 0L || raw.cd2() != 0L;
        if (equipped.stream().allMatch(EMPTY::equals) && !raw.learned().isEmpty()) {
            // 旧档（或 v1 无 equipped 字段）：按旧固定槽序配装，冷却搬运到对应新槽位
            List<String> newEquipped = new ArrayList<>(NO_EQUIP);
            List<Long> newCds = new ArrayList<>(NO_CD);
            int slot = 0;
            for (String legacyCard : SpellCardEffects.SLOT_ORDER) {
                if (!raw.learned().contains(legacyCard) || slot >= MAX_SLOTS) {
                    continue;
                }
                newEquipped.set(slot, legacyCard);
                newCds.set(slot, cdsForLegacyCard(raw, legacyCard));
                slot++;
            }
            // 迁移期 v1 已学但未入旧三槽的卡：按学习顺序补位
            for (String card : raw.learned()) {
                if (slot >= MAX_SLOTS || newEquipped.contains(card)) {
                    continue;
                }
                newEquipped.set(slot++, card);
            }
            return new SkillStateData(List.copyOf(raw.learned()), List.copyOf(newCds),
                    List.copyOf(newEquipped));
        }
        return new SkillStateData(List.copyOf(raw.learned()), List.copyOf(cds),
                List.copyOf(equipped));
    }

    private static long cdsForLegacyCard(Raw raw, String legacyCard) {
        int legacySlot = legacyIndexOf(legacyCard);
        if (legacySlot < 0) {
            return 0L;
        }
        return switch (legacySlot) {
            case 0 -> raw.cd0();
            case 1 -> raw.cd1();
            default -> raw.cd2();
        };
    }

    private static int legacyIndexOf(String cardId) {
        for (int i = 0; i < SpellCardEffects.SLOT_ORDER.length; i++) {
            if (SpellCardEffects.SLOT_ORDER[i].equals(cardId)) {
                return i;
            }
        }
        return -1;
    }

    private static List<Long> padCds(List<Long> cds) {
        List<Long> padded = new ArrayList<>(cds);
        while (padded.size() < MAX_SLOTS) {
            padded.add(0L);
        }
        return padded;
    }

    private static List<String> padEquipped(List<String> equipped) {
        List<String> padded = new ArrayList<>(equipped);
        while (padded.size() < MAX_SLOTS) {
            padded.add(EMPTY);
        }
        return padded;
    }

    public static SkillStateData initial() {
        return new SkillStateData(List.of(), List.copyOf(NO_CD), List.copyOf(NO_EQUIP));
    }

    public boolean hasLearned(String cardId) {
        return learned.contains(cardId);
    }

    /** 正式学卡判据：已学数受可解锁槽数上限（调试命令走 {@link #withLearned} 豁免）。 */
    public boolean canLearn(String cardId, int tier) {
        return !hasLearned(cardId) && slotCountForTier(tier) > 0
                && learned.size() < slotCountForTier(tier)
                && SpellCardEffects.get(cardId) != null;
    }

    public long cooldownUntil(int slot) {
        return slot >= 0 && slot < cooldownUntil.size() ? cooldownUntil.get(slot) : 0L;
    }

    /** 槽位当前配装的卡 id；越界/未配装返回 null。 */
    @Nullable
    public String equippedCard(int slot) {
        if (slot < 0 || slot >= equipped.size()) {
            return null;
        }
        String cardId = equipped.get(slot);
        return cardId == null || cardId.isEmpty() ? null : cardId;
    }

    /** 调试/初始化用：以学习顺序自动配装并清空冷却。 */
    public SkillStateData withLearned(String cardId) {
        if (hasLearned(cardId)) {
            return this;
        }
        List<String> copy = new ArrayList<>(learned);
        copy.add(cardId);
        List<String> newEquipped = new ArrayList<>(equipped);
        while (newEquipped.size() < MAX_SLOTS) {
            newEquipped.add(EMPTY);
        }
        if (!newEquipped.contains(cardId)) {
            for (int i = 0; i < MAX_SLOTS; i++) {
                if (newEquipped.get(i).isEmpty()) {
                    newEquipped.set(i, cardId);
                    break;
                }
            }
        }
        return new SkillStateData(List.copyOf(copy), List.copyOf(cooldownUntil),
                List.copyOf(newEquipped));
    }

    /** 配装（同卡换槽/替换目标槽内容——两槽互换语义由调用方编排）。 */
    public SkillStateData withEquipped(int slot, String cardId) {
        if (slot < 0 || slot >= MAX_SLOTS || !hasLearned(cardId)) {
            return this;
        }
        List<String> newEquipped = new ArrayList<>(padEquipped(equipped));
        int existing = newEquipped.indexOf(cardId);
        if (existing >= 0) {
            // 目标卡原槽腾出后与当前槽内容互换（当前槽空则只腾位）
            newEquipped.set(existing, newEquipped.get(slot));
        }
        newEquipped.set(slot, cardId);
        return new SkillStateData(List.copyOf(learned), List.copyOf(cooldownUntil),
                List.copyOf(newEquipped));
    }

    public SkillStateData withCooldown(int slot, long until) {
        if (slot < 0 || slot >= MAX_SLOTS) {
            return this;
        }
        List<Long> cds = new ArrayList<>(padCds(cooldownUntil));
        cds.set(slot, until);
        return new SkillStateData(List.copyOf(learned), List.copyOf(cds), List.copyOf(equipped));
    }

    /** 清全部冷却（调试命令用）。 */
    public SkillStateData cleared() {
        return new SkillStateData(List.copyOf(learned), List.copyOf(NO_CD), List.copyOf(equipped));
    }
}
