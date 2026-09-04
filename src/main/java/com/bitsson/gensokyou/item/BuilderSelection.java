package com.bitsson.gensokyou.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * 仪式构建器的当前选择：目标图案 id + 标签谓词实例化所用品阶（0-5）。
 * 存于构建器物品的数据组件，随物品持久化并同步客户端供 tooltip/菜单展示。
 */
public record BuilderSelection(ResourceLocation patternId, int tier) {

    public static final Codec<BuilderSelection> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    ResourceLocation.CODEC.fieldOf("pattern").forGetter(BuilderSelection::patternId),
                    Codec.intRange(0, 5).fieldOf("tier").forGetter(BuilderSelection::tier)
            ).apply(instance, BuilderSelection::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, BuilderSelection> STREAM_CODEC =
            StreamCodec.composite(
                    ResourceLocation.STREAM_CODEC, BuilderSelection::patternId,
                    ByteBufCodecs.VAR_INT, BuilderSelection::tier,
                    BuilderSelection::new);
}
