package com.bitsson.gensokyou.spirit;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * 玩家符卡即时状态（add-player-spellcards）：花瓣护盾计数与疫符免控窗口。
 * 持久化 + 死亡保留（与既有 Attachment 语义对齐）。
 */
public record SpellBuffData(int flowerArmorPetals, long flowerArmorUntil, long plagueRepayUntil) {

    public static final Codec<SpellBuffData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.optionalFieldOf("flower_armor_petals", 0)
                            .forGetter(SpellBuffData::flowerArmorPetals),
                    Codec.LONG.optionalFieldOf("flower_armor_until", 0L)
                            .forGetter(SpellBuffData::flowerArmorUntil),
                    Codec.LONG.optionalFieldOf("plague_repay_until", 0L)
                            .forGetter(SpellBuffData::plagueRepayUntil)
            ).apply(instance, SpellBuffData::new));

    public static SpellBuffData initial() {
        return new SpellBuffData(0, 0L, 0L);
    }

    public boolean hasFlowerArmor(long now) {
        return flowerArmorPetals > 0 && now < flowerArmorUntil;
    }

    public SpellBuffData withFlowerArmor(int petals, long until) {
        return new SpellBuffData(Math.max(0, petals), until, plagueRepayUntil);
    }

    /** 挡下一发：花瓣减一（归零即失效）。 */
    public SpellBuffData consumePetal(long now) {
        if (!hasFlowerArmor(now)) {
            return this;
        }
        int left = flowerArmorPetals - 1;
        return new SpellBuffData(left, left > 0 ? flowerArmorUntil : 0L, plagueRepayUntil);
    }

    /** 清空护盾（不碰疫符）。 */
    public SpellBuffData clearedFlowerArmor() {
        return new SpellBuffData(0, 0L, plagueRepayUntil);
    }

    public boolean hasPlagueRepay(long now) {
        return now < plagueRepayUntil;
    }

    public SpellBuffData withPlagueRepay(long until) {
        return new SpellBuffData(flowerArmorPetals, flowerArmorUntil, until);
    }
}
