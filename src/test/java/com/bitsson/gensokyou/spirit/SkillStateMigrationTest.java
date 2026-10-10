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
 * 抽象槽配装轮换语义。对应 spec "抽象槽：已学卡可配装 / 槽数=max(0,阶级-2)"。
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
    void learnedCapFollowsSlotCount() {
        assertEquals(0, SkillStateData.slotCountForTier(0));
        assertEquals(0, SkillStateData.slotCountForTier(1));
        assertEquals(0, SkillStateData.slotCountForTier(2));
        assertEquals(1, SkillStateData.slotCountForTier(3));
        assertEquals(2, SkillStateData.slotCountForTier(4));
        assertEquals(3, SkillStateData.slotCountForTier(5));

        SkillStateData empty = SkillStateData.initial();
        assertFalse(empty.canLearn(SpellCardEffects.MUSOU_FUUIN, 2), "2 阶可解锁 0 槽，不可学");
        assertTrue(empty.canLearn(SpellCardEffects.MUSOU_FUUIN, 3), "3 阶上限 1 张");

        SkillStateData one = empty.withLearned(SpellCardEffects.MUSOU_FUUIN);
        assertFalse(one.canLearn(SpellCardEffects.ICICLE_FALL, 3), "3 阶上限 1 张已满");
        assertTrue(one.canLearn(SpellCardEffects.ICICLE_FALL, 4), "4 阶上限 2 张");
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
    void unlearnedEquipRejected() {
        SkillStateData state = SkillStateData.initial()
                .withLearned(SpellCardEffects.MUSOU_FUUIN);
        assertEquals(state, state.withEquipped(2, "ghost_card"), "未学卡不可配装");
    }
}
