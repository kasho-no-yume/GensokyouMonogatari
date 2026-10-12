package com.bitsson.gensokyou.item.codex;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * 寄宿残魂的众生典籍所携带的数据：被寄宿 BOSS 的实体类型 id 与其自身品阶
 * （{@code TouhouBoss#bossTier()}，合成时定格）。
 *
 * <p>与 {@link CodexData} 刻意分开：残魂典籍不是「众生典籍的收容态变体」，而是一件
 * 独立物品——众生余录据 {@code instanceof CodexOfBeingsItem} 识别供品，独立物品天然被排除，
 * 无需在众生余录里加特判。
 *
 * <p>存 {@link ResourceLocation} 而非实体类：跨数据包重载稳定，未知 id 可降级显示。
 */
public record BossResidualData(ResourceLocation boss, int tier) {

    public static final Codec<BossResidualData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    ResourceLocation.CODEC.fieldOf("boss").forGetter(BossResidualData::boss),
                    Codec.INT.optionalFieldOf("tier", 1).forGetter(BossResidualData::tier)
            ).apply(instance, BossResidualData::new));

    public static final StreamCodec<ByteBuf, BossResidualData> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, BossResidualData::boss,
            ByteBufCodecs.VAR_INT, BossResidualData::tier,
            BossResidualData::new);
}
