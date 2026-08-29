package com.bitsson.gensokyou.registry;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.item.weapon.RuneAffix;
import com.bitsson.gensokyou.item.weapon.WeaponSlots;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import com.mojang.serialization.Codec;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

public final class ModDataComponents {

    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Gensokyou.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<WeaponSlots>> WEAPON_SLOTS =
            DATA_COMPONENTS.register("weapon_slots", () -> DataComponentType.<WeaponSlots>builder()
                    .persistent(WeaponSlots.CODEC)
                    .networkSynchronized(WeaponSlots.STREAM_CODEC)
                    .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<RuneAffix>>> RUNE_AFFIXES =
            DATA_COMPONENTS.register("rune_affixes", () -> DataComponentType.<List<RuneAffix>>builder()
                    .persistent(RuneAffix.CODEC.listOf())
                    .networkSynchronized(RuneAffix.STREAM_CODEC.apply(ByteBufCodecs.list()))
                    .build());

    private ModDataComponents() {
    }
}
