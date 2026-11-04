package com.bitsson.gensokyou.spirit;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 符卡槽 v2（skill-slots-hud 改造）：旧三固定槽档迁移映射、学习阶级上限、
 * 抽象槽配装轮换语义。对应 spec "抽象槽：已学卡可配装 / 槽数=clamp(阶级,0,5)；学习资格走旧遗留上限"。
 */
class SkillStateMigrationTest {

    @Test
    void emptyIsDefault() {
        SkillStateData state = SkillStateData.initial();
        assertTrue(state.learned().isEmpty());
        for (int i = 0; i < SkillStateData.MAX_SLOTS; i++) {
            assertNull(state.equippedCard(i));
            assertEquals(0L, state.cooldownUntil(i));
        }
    }

    @Test
    void legacySaveMapsFixedSlotsAndCooldowns() {
        CompoundTag tag = new CompoundTag();
        ListTag learned = new ListTag();
        learned.add(StringTag.valueOf(SpellCardEffects.MUSOU_FUUIN));
        learned.add(StringTag.valueOf(SpellCardEffects.ICICLE_FALL));
        tag.put("learned", learned);
        tag.putLong("cd0", 1234L);   // 旧语义：槽 0 = musou
        tag.putLong("cd2", 5678L);   // 旧语义：槽 2 = light_reflect（未学，无对应新槽）
        Optional<SkillStateData> decoded = SkillStateData.CODEC.decode(
                net.minecraft.nbt.NbtOps.INSTANCE, tag).result().map(e -> e.getFirst());
        assertTrue(decoded.isPresent());
        SkillStateData state = decoded.get();
        // 按旧 SLOT_ORDER 过滤已学卡入组：musou→槽0、icicle→槽1；cd0 跟 musou、cd1 未设=0
        assertEquals(SpellCardEffects.MUSOU_FUUIN, state.equippedCard(0));
        assertEquals(SpellCardEffects.ICICLE_FALL, state.equippedCard(1));
        assertEquals(1234L, state.cooldownUntil(0));
        assertEquals(0L, state.cooldownUntil(1));
    }

    @Test
    void roundTripKeepsV2Fields() {
        SkillStateData state = SkillStateData.initial()
                .withLearned(SpellCardEffects.MUSOU_FUUIN)
                .withLearned(SpellCardEffects.ICICLE_FALL)
                .withEquipped(3, SpellCardEffects.ICICLE_FALL)
                .withCooldown(3, 99L);
        CompoundTag tag = new CompoundTag();
        SkillStateData.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, state)
                .result().ifPresent(t -> tag.put("s", t));
        SkillStateData back = SkillStateData.CODEC.decode(
                net.minecraft.nbt.NbtOps.INSTANCE, tag.get("s")).result().orElseThrow().getFirst();
        assertEquals(SpellCardEffects.ICICLE_FALL, back.equippedCard(3));
        assertEquals(99L, back.cooldownUntil(3));
        assertNull(back.equippedCard(1), "icicle 被换到槽 3 后原槽腾出");
    }

    @Test
    void slotCountFollowsTierClamp() {
        assertEquals(0, SkillStateData.slotCountForTier(0));
        assertEquals(1, SkillStateData.slotCountForTier(1));
        assertEquals(2, SkillStateData.slotCountForTier(2));
        assertEquals(3, SkillStateData.slotCountForTier(3));
        assertEquals(4, SkillStateData.slotCountForTier(4));
        assertEquals(5, SkillStateData.slotCountForTier(5));

        // 超界钳制：负阶级→0，超阶→5，不抛异常
        assertEquals(0, SkillStateData.slotCountForTier(-3));
        assertEquals(5, SkillStateData.slotCountForTier(9));
    }

    @Test
    void learnLimitStaysLegacyAndDecoupled() {
        // 旧遗留学习上限：0/0/0/1/2/3，不随槽数扩容扩大
        assertEquals(0, SkillStateData.legacyLearnLimit(0));
        assertEquals(0, SkillStateData.legacyLearnLimit(1));
        assertEquals(0, SkillStateData.legacyLearnLimit(2));
        assertEquals(1, SkillStateData.legacyLearnLimit(3));
        assertEquals(2, SkillStateData.legacyLearnLimit(4));
        assertEquals(3, SkillStateData.legacyLearnLimit(5));

        SkillStateData empty = SkillStateData.initial();
        assertFalse(empty.canLearn(SpellCardEffects.MUSOU_FUUIN, 2), "2 阶旧遗留上限 0，不可学");
        assertTrue(empty.canLearn(SpellCardEffects.MUSOU_FUUIN, 3), "3 阶遗留上限 1 张");

        SkillStateData one = empty.withLearned(SpellCardEffects.MUSOU_FUUIN);
        assertFalse(one.canLearn(SpellCardEffects.ICICLE_FALL, 3), "3 阶遗留上限 1 张已满");
        assertTrue(one.canLearn(SpellCardEffects.ICICLE_FALL, 4), "4 阶遗留上限 2 张");
        assertTrue(one.canLearn(SpellCardEffects.ICICLE_FALL, 5), "5 阶槽数 5 但遗留学习上限 3，第 2 张可学");
        assertFalse(one.canLearn(SpellCardEffects.MUSOU_FUUIN, 5), "重复学习拒绝");
    }

    @Test
    void equipRotationKeepsUniqueness() {
        SkillStateData state = SkillStateData.initial()
                .withLearned(SpellCardEffects.MUSOU_FUUIN)
                .withLearned(SpellCardEffects.ICICLE_FALL);
        // 槽0=musou（自动）、槽1=icicle；把 icicle 换入槽 0 → 原槽腾出互换
        SkillStateData rotated = state.withEquipped(0, SpellCardEffects.ICICLE_FALL);
        assertEquals(SpellCardEffects.ICICLE_FALL, rotated.equippedCard(0));
        assertEquals(SpellCardEffects.MUSOU_FUUIN, rotated.equippedCard(1), "被顶出的卡落到 icicle 原槽");
        assertFalse(rotated.equippedCard(0).equals(rotated.equippedCard(1)));
    }

    @Test
    void ungrantedSlotsStayEmptyAndCooldownSurvivesSwap() {
        // 未获授卡的槽保持空：学习只填入学习顺序、不凭空发卡
        SkillStateData state = SkillStateData.initial().withLearned(SpellCardEffects.MUSOU_FUUIN);
        assertEquals(SpellCardEffects.MUSOU_FUUIN, state.equippedCard(0), "学习自动配装首个空槽");
        for (int i = 1; i < SkillStateData.MAX_SLOTS; i++) {
            assertNull(state.equippedCard(i), "其余槽不自动授卡");
        }

        // 换槽/顶出不重置冷却：冷却挂在槽位上，withEquipped 不触碰 cooldownUntil
        SkillStateData withCd = state.withCooldown(2, 4242L);
        SkillStateData swapped = withCd.withEquipped(2, SpellCardEffects.MUSOU_FUUIN);
        assertEquals(4242L, swapped.cooldownUntil(2), "换槽不重置既有冷却");

        // 零阶拒绝学习（旧遗留上限 0）；一阶有 1 个可用槽但无卡可学（上限仍为 0）
        assertFalse(SkillStateData.initial().canLearn(SpellCardEffects.MUSOU_FUUIN, 0));
        assertFalse(SkillStateData.initial().canLearn(SpellCardEffects.MUSOU_FUUIN, 1));

        // 旧角色迁移后学习卡不丢、新槽保持空、冷却全部保留
        CompoundTag tag = new CompoundTag();
        ListTag learned = new ListTag();
        learned.add(StringTag.valueOf(SpellCardEffects.MUSOU_FUUIN));
        learned.add(StringTag.valueOf(SpellCardEffects.ICICLE_FALL));
        tag.put("learned", learned);
        tag.putLong("cd0", 777L);
        Optional<SkillStateData> decoded = SkillStateData.CODEC.decode(
                net.minecraft.nbt.NbtOps.INSTANCE, tag).result().map(e -> e.getFirst());
        assertTrue(decoded.isPresent());
        assertEquals(2, decoded.get().learned().size(), "迁移保留已学卡，不因新槽扩容增删");
        assertEquals(777L, decoded.get().cooldownUntil(0), "旧冷却随卡搬运保留");
    }

    @Test
    void unlearnedEquipRejected() {
        SkillStateData state = SkillStateData.initial()
                .withLearned(SpellCardEffects.MUSOU_FUUIN);
        assertEquals(state, state.withEquipped(2, "ghost_card"), "未学卡不可配装");
    }
}
