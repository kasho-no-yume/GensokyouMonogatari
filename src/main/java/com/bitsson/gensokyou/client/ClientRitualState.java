package com.bitsson.gensokyou.client;

import com.bitsson.gensokyou.network.RitualInfoPayload;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/** 客户端仪式界面数据：最近一次服务端下发的信息快照（单界面假设）。 */
public final class ClientRitualState {

    @Nullable
    private static volatile RitualInfoPayload latest;
    private static final Map<String, ItemStack> ITEM_CACHE = new HashMap<>();

    private ClientRitualState() {
    }

    public static void update(RitualInfoPayload payload) {
        latest = payload;
    }

    @Nullable
    public static RitualInfoPayload latest() {
        return latest;
    }

    /** itemId → ItemStack（客户端展示用，空 id 返回空堆栈）。 */
    public static ItemStack stackFor(String itemId) {
        if (itemId.isEmpty()) {
            return ItemStack.EMPTY;
        }
        synchronized (ITEM_CACHE) {
            return ITEM_CACHE.computeIfAbsent(itemId, id -> {
                var item = net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .getOptional(net.minecraft.resources.ResourceLocation.parse(id));
                return item.map(ItemStack::new).orElse(ItemStack.EMPTY);
            });
        }
    }
}
