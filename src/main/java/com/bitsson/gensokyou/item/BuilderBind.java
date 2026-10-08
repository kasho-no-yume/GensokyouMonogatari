package com.bitsson.gensokyou.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * 构建器绑定的无尽藏核心：维度 + 核心坐标。
 * 存于 {@code RitualBuilderItem} 的 Data Component，缺失即未绑定。
 */
public record BuilderBind(ResourceLocation dimension, BlockPos pos) {

    public static final Codec<BuilderBind> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    ResourceLocation.CODEC.fieldOf("dimension").forGetter(BuilderBind::dimension),
                    BlockPos.CODEC.fieldOf("pos").forGetter(BuilderBind::pos)
            ).apply(instance, BuilderBind::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, BuilderBind> STREAM_CODEC =
            StreamCodec.composite(
                    ResourceLocation.STREAM_CODEC, BuilderBind::dimension,
                    BlockPos.STREAM_CODEC, BuilderBind::pos,
                    BuilderBind::new);
}
