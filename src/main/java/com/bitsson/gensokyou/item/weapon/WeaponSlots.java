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
}
