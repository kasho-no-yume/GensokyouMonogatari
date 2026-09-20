package com.bitsson.gensokyou.network;

import com.bitsson.gensokyou.Gensokyou;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * C2S：无尽藏终端的 JEI 配方填充请求。客户端只发送"配方想要哪些输入"，
 * 服务端按 containerId 定位终端菜单并权威执行取料（仓储优先、背包兜底），
 * MUST NOT 信任客户端做任何取出/存入承诺。
 */
public record WujinzangRecipeFillPayload(int containerId, List<ItemStack> ingredients)
        implements CustomPacketPayload {

    public static final Type<WujinzangRecipeFillPayload> TYPE =
            new Type<>(Gensokyou.id("wujinzang_recipe_fill"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WujinzangRecipeFillPayload> STREAM_CODEC =
            StreamCodec.ofMember(WujinzangRecipeFillPayload::write, WujinzangRecipeFillPayload::read);

    private static void write(WujinzangRecipeFillPayload payload, RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(payload.containerId);
        buf.writeVarInt(payload.ingredients.size());
        for (ItemStack stack : payload.ingredients) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, stack);
        }
    }

    private static WujinzangRecipeFillPayload read(RegistryFriendlyByteBuf buf) {
        int containerId = buf.readVarInt();
        int size = buf.readVarInt();
        List<ItemStack> ingredients = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ingredients.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
        }
        return new WujinzangRecipeFillPayload(containerId, List.copyOf(ingredients));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
