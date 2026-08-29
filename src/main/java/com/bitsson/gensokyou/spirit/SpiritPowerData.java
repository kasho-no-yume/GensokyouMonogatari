package com.bitsson.gensokyou.spirit;

import com.bitsson.gensokyou.config.GensokyouConfig;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Mth;

public record SpiritPowerData(float current, float max, int temperLevel, float regenBuffer, float spiritDamage) {

    public static final Codec<SpiritPowerData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.FLOAT.fieldOf("current").forGetter(SpiritPowerData::current),
                    Codec.FLOAT.fieldOf("max").forGetter(SpiritPowerData::max),
                    Codec.INT.fieldOf("temper_level").forGetter(SpiritPowerData::temperLevel),
                    Codec.FLOAT.optionalFieldOf("regen_buffer", 0F).forGetter(SpiritPowerData::regenBuffer),
                    Codec.FLOAT.optionalFieldOf("spirit_damage", 0F).forGetter(SpiritPowerData::spiritDamage)
            ).apply(instance, SpiritPowerData::new));

    public static SpiritPowerData initial() {
        float baseMax = GensokyouConfig.BASE_MAX_SP.get().floatValue();
        float baseDamage = GensokyouConfig.BASE_SPIRIT_DAMAGE.get().floatValue();
        return new SpiritPowerData(baseMax, baseMax, 0, 0F, baseDamage);
    }

    public SpiritPowerData withCurrent(float newCurrent) {
        return new SpiritPowerData(Mth.clamp(newCurrent, 0F, max), max, temperLevel, regenBuffer, spiritDamage);
    }

    public SpiritPowerData withAddedCurrent(float amount) {
        return withCurrent(current + amount);
    }

    public SpiritPowerData withTemperUp(float gainPerLevel, float regenBuffer) {
        float newMax = Math.min(max + gainPerLevel, 1000000F);
        return new SpiritPowerData(Math.min(current, newMax), newMax, temperLevel + 1, regenBuffer, spiritDamage);
    }

    /** 淬炼同时提升灵力伤害。 */
    public SpiritPowerData withTemperUp(float gainPerLevel, float spiritDamageGain, float regenBuffer) {
        float newMax = Math.min(max + gainPerLevel, 1000000F);
        return new SpiritPowerData(Math.min(current, newMax), newMax, temperLevel + 1, regenBuffer,
                spiritDamage + spiritDamageGain);
    }

    public SpiritPowerData withRegenBuffer(float buffer) {
        return new SpiritPowerData(current, max, temperLevel, buffer, spiritDamage);
    }
}
