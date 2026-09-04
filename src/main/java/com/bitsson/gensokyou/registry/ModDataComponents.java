package com.bitsson.gensokyou.registry;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.item.BuilderSelection;
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

    /** 增幅核晶石随机色（ARGB，首次获取时掷定，随物品持久化并同步客户端供 tint 使用）。 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> CRYSTAL_COLOR =
            DATA_COMPONENTS.register("crystal_color", () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    /** 仪式构建器当前选择（图案 id + 品阶），随物品持久化并同步客户端。 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BuilderSelection>> RITUAL_BUILDER_SELECTION =
            DATA_COMPONENTS.register("ritual_builder_selection", () -> DataComponentType.<BuilderSelection>builder()
                    .persistent(BuilderSelection.CODEC)
                    .networkSynchronized(BuilderSelection.STREAM_CODEC)
                    .build());

    private ModDataComponents() {
    }
}
