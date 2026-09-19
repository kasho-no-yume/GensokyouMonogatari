package com.bitsson.gensokyou.item.codex;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * 众生典籍收容状态：单槽记录一种 mob 类型（species）与其累计收容数（count），
 * 以及"驯服动物警告已弹过"标记（tamedWarned，每本书一次）。
 * 存 {@link ResourceLocation} 而非 EntityType 实例：跨数据包重载稳定，未知 id 可降级显示。
 */
public record CodexData(Optional<ResourceLocation> species, int count, boolean tamedWarned) {

    /** 收容上限：达到即进入不可逆附魔形态。 */
    public static final int MAX_CAPTURE = 20;

    public static final CodexData EMPTY = new CodexData(Optional.empty(), 0, false);

    public static final Codec<CodexData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    ResourceLocation.CODEC.optionalFieldOf("species").forGetter(CodexData::species),
                    Codec.INT.optionalFieldOf("count", 0).forGetter(CodexData::count),
                    Codec.BOOL.optionalFieldOf("tamed_warned", false).forGetter(CodexData::tamedWarned)
            ).apply(instance, CodexData::new));

    public static final StreamCodec<ByteBuf, CodexData> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs::optional), CodexData::species,
            ByteBufCodecs.VAR_INT, CodexData::count,
            ByteBufCodecs.BOOL, CodexData::tamedWarned,
            CodexData::new);

    public boolean isEmpty() {
        return species.isEmpty() || count <= 0;
    }

    public boolean isFull() {
        return count >= MAX_CAPTURE;
    }

    /** 收容一只指定类型：同种累加（封顶），异种改写类型并重置为 1。 */
    public CodexData capture(ResourceLocation type) {
        if (species.isPresent() && species.get().equals(type)) {
            return new CodexData(species, Math.min(count + 1, MAX_CAPTURE), tamedWarned);
        }
        return new CodexData(Optional.of(type), 1, tamedWarned);
    }

    public CodexData withTamedWarned() {
        return tamedWarned ? this : new CodexData(species, count, true);
    }
}
