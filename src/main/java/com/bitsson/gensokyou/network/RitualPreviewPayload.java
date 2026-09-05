package com.bitsson.gensokyou.network;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.ritual.RitualPreviewState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.Optional;

/** S2C：构建器预览态同步。有值 = 置入/替换投影，空 = 清除（建造后下发）。 */
public record RitualPreviewPayload(Optional<RitualPreviewState> preview)
        implements CustomPacketPayload {

    public static final Type<RitualPreviewPayload> TYPE =
            new Type<>(Gensokyou.id("ritual_preview"));

    /** 手写 codec：Optional 无现成编解码（bool 哨兵 + 逐字段），composite 上限 6 参装不下。 */
    public static final StreamCodec<RegistryFriendlyByteBuf, RitualPreviewPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public RitualPreviewPayload decode(RegistryFriendlyByteBuf buf) {
                    if (!buf.readBoolean()) {
                        return new RitualPreviewPayload(Optional.empty());
                    }
                    ResourceLocation patternId = ResourceLocation.STREAM_CODEC.decode(buf);
                    int tier = buf.readVarInt();
                    BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
                    ResourceKey<Level> dimension = ResourceKey.create(
                            Registries.DIMENSION, ResourceLocation.STREAM_CODEC.decode(buf));
                    return new RitualPreviewPayload(
                            Optional.of(new RitualPreviewState(patternId, tier, pos, dimension)));
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, RitualPreviewPayload payload) {
                    if (payload.preview().isEmpty()) {
                        buf.writeBoolean(false);
                        return;
                    }
                    RitualPreviewState state = payload.preview().get();
                    buf.writeBoolean(true);
                    ResourceLocation.STREAM_CODEC.encode(buf, state.patternId());
                    buf.writeVarInt(state.tier());
                    BlockPos.STREAM_CODEC.encode(buf, state.corePos());
                    ResourceLocation.STREAM_CODEC.encode(buf, state.dimension().location());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
