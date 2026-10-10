package com.bitsson.gensokyou.registry;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.block.entity.CrystalBlockEntity;
import com.bitsson.gensokyou.block.entity.DanmakuAssemblyBenchBlockEntity;
import com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity;
import com.bitsson.gensokyou.block.entity.RitualPedestalBlockEntity;
import com.bitsson.gensokyou.block.entity.SukimaBlockEntity;
import com.bitsson.gensokyou.block.entity.SpiritBombBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Set;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Gensokyou.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RitualCoreBlockEntity>> RITUAL_CORE =
            BLOCK_ENTITIES.register("ritual_core", () -> new BlockEntityType<>(
                    RitualCoreBlockEntity::new, Set.of(ModBlocks.RITUAL_CORE.get()), null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RitualPedestalBlockEntity>> RITUAL_PEDESTAL =
            BLOCK_ENTITIES.register("ritual_pedestal", () -> new BlockEntityType<>(
                    RitualPedestalBlockEntity::new,
                    Set.of(ModBlocks.RITUAL_PEDESTAL.get()),
                    null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SukimaBlockEntity>> SUKIMA =
            BLOCK_ENTITIES.register("sukima", () -> new BlockEntityType<>(
                    SukimaBlockEntity::new, Set.of(ModBlocks.SUKIMA.get()), null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CrystalBlockEntity>> CRYSTAL =
            BLOCK_ENTITIES.register("crystal", () -> new BlockEntityType<>(
                    CrystalBlockEntity::new, Set.of(ModBlocks.CRYSTAL.get()), null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DanmakuAssemblyBenchBlockEntity>> DANMAKU_ASSEMBLY_BENCH =
            BLOCK_ENTITIES.register("danmaku_assembly_bench", () -> new BlockEntityType<>(
                    DanmakuAssemblyBenchBlockEntity::new, Set.of(ModBlocks.DANMAKU_ASSEMBLY_BENCH.get()), null));

    /** 灵力引爆器方块实体：保存参数并在启动后自行倒计时起爆。 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SpiritBombBlockEntity>> SPIRIT_BOMB =
            BLOCK_ENTITIES.register("spirit_bomb", () -> new BlockEntityType<>(
                    SpiritBombBlockEntity::new, Set.of(ModBlocks.SPIRIT_BOMB.get()), null));

    /** 整地器方块实体：保存长宽高与冷却，执行清除/整平。 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.bitsson.gensokyou.block.entity.LandscapingBlockEntity>> LANDSCAPING =
            BLOCK_ENTITIES.register("landscaping", () -> new BlockEntityType<>(
                    com.bitsson.gensokyou.block.entity.LandscapingBlockEntity::new,
                    Set.of(ModBlocks.LANDSCAPING.get()), null));
}
