package com.bitsson.gensokyou.registry;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.item.BuilderSelection;
import com.bitsson.gensokyou.item.codex.CodexData;
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

    /**
     * 洗练度（星移之仪软保底）：保留 +1、采纳减半，驱动 {@code SeiiNumbers.pityBand}
     * 把采样带向好的一侧平移。刻意挂在核上而非 BE 会话上 —— 跟物走，换仪式/拆结构/换地图都不丢。
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> RUNE_REROLLS =
            DATA_COMPONENTS.register("rune_rerolls", () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    /** 仪式构建器当前选择（图案 id + 品阶），随物品持久化并同步客户端。 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BuilderSelection>> RITUAL_BUILDER_SELECTION =
            DATA_COMPONENTS.register("ritual_builder_selection", () -> DataComponentType.<BuilderSelection>builder()
                    .persistent(BuilderSelection.CODEC)
                    .networkSynchronized(BuilderSelection.STREAM_CODEC)
                    .build());

    /** 仪式构建器绑定的无尽藏核心（维度 + 坐标），缺失即未绑定；随物品持久化并同步客户端。 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.bitsson.gensokyou.item.BuilderBind>> RITUAL_BUILDER_BIND =
            DATA_COMPONENTS.register("ritual_builder_bind", () -> DataComponentType.<com.bitsson.gensokyou.item.BuilderBind>builder()
                    .persistent(com.bitsson.gensokyou.item.BuilderBind.CODEC)
                    .networkSynchronized(com.bitsson.gensokyou.item.BuilderBind.STREAM_CODEC)
                    .build());

    /** 编辑杖会话态（锚点/维度/选择/按阶级工作区），随物品持久化并同步客户端。 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.bitsson.gensokyou.ritual.editor.EditorState>> RITUAL_EDITOR_STATE =
            DATA_COMPONENTS.register("ritual_editor_state",
                    () -> DataComponentType.<com.bitsson.gensokyou.ritual.editor.EditorState>builder()
                            .persistent(com.bitsson.gensokyou.ritual.editor.EditorState.CODEC)
                            .networkSynchronized(com.bitsson.gensokyou.ritual.editor.EditorState.STREAM_CODEC)
                            .build());

    /** 灵力核心已存灵力（long；容量/速率是物品定值，不入组件）。 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.bitsson.gensokyou.spirit.SpiritCoreData>> SPIRIT_CORE_POWER =
            DATA_COMPONENTS.register("spirit_core_power",
                    () -> DataComponentType.<com.bitsson.gensokyou.spirit.SpiritCoreData>builder()
                            .persistent(com.bitsson.gensokyou.spirit.SpiritCoreData.CODEC)
                            .networkSynchronized(com.bitsson.gensokyou.spirit.SpiritCoreData.STREAM_CODEC)
                            .build());

    /** 众生典籍收容状态（mob 类型 + 数量 + 驯服警告标记）。 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CodexData>> CODEX_DATA =
            DATA_COMPONENTS.register("codex_data", () -> DataComponentType.<CodexData>builder()
                    .persistent(CodexData.CODEC)
                    .networkSynchronized(CodexData.STREAM_CODEC)
                    .build());

    private ModDataComponents() {
    }
}
