package com.bitsson.gensokyou.item.weapon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * 主武器三槽快照：slot1 弹幕核 / slot2 武器等级核 / slot3 增幅核。
 * 槽位可空（EMPTY），序列化为武器的 weapon_slots 数据组件。
 */
public record WeaponSlots(ItemStack slot1, ItemStack slot2, ItemStack slot3) {

    public static final WeaponSlots DEFAULT =
            new WeaponSlots(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY);

    public static final Codec<WeaponSlots> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    ItemStack.OPTIONAL_CODEC.optionalFieldOf("slot1", ItemStack.EMPTY).forGetter(WeaponSlots::slot1),
                    ItemStack.OPTIONAL_CODEC.optionalFieldOf("slot2", ItemStack.EMPTY).forGetter(WeaponSlots::slot2),
                    ItemStack.OPTIONAL_CODEC.optionalFieldOf("slot3", ItemStack.EMPTY).forGetter(WeaponSlots::slot3)
            ).apply(instance, WeaponSlots::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, WeaponSlots> STREAM_CODEC = StreamCodec.composite(
            ItemStack.OPTIONAL_STREAM_CODEC, WeaponSlots::slot1,
            ItemStack.OPTIONAL_STREAM_CODEC, WeaponSlots::slot2,
            ItemStack.OPTIONAL_STREAM_CODEC, WeaponSlots::slot3,
            WeaponSlots::new);

    @Nullable
    public ItemStack slot(int index) {
        return switch (index) {
            case 0 -> slot1;
            case 1 -> slot2;
            case 2 -> slot3;
            default -> null;
        };
    }

    public WeaponSlots with(int index, ItemStack stack) {
        return switch (index) {
            case 0 -> new WeaponSlots(stack, slot2, slot3);
            case 1 -> new WeaponSlots(slot1, stack, slot3);
            case 2 -> new WeaponSlots(slot1, slot2, stack);
            default -> this;
        };
    }

    /**
     * 内容相等：嵌套 {@link ItemStack} 无值语义（{@code ItemStack} 未覆写 equals），
     * record 默认的引用比较会让"装了核的武器"在 {@code ItemStack.isSameItemSameComponents} 判据下
     * 永远不相等（无尽藏存取、同类合并、网络往返后比对均失效）。此处按槽位内容比较。
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WeaponSlots other)) {
            return false;
        }
        return ItemStack.matches(slot1, other.slot1)
                && ItemStack.matches(slot2, other.slot2)
                && ItemStack.matches(slot3, other.slot3);
    }

    @Override
    public int hashCode() {
        int h = stackHash(slot1);
        h = 31 * h + stackHash(slot2);
        h = 31 * h + stackHash(slot3);
        return h;
    }

    /** 嵌套栈的内容哈希（不能借用 ItemStack.hashCode —— 同样是引用语义）。 */
    private static int stackHash(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        int h = stack.getItem().hashCode();
        h = 31 * h + stack.getCount();
        h = 31 * h + stack.getComponents().hashCode();
        return h;
    }
}
