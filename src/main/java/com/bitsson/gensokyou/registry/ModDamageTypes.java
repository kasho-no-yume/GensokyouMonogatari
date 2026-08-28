package com.bitsson.gensokyou.registry;

import com.bitsson.gensokyou.Gensokyou;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;

public final class ModDamageTypes {
    public static final ResourceKey<DamageType> DANMAKU =
            ResourceKey.create(Registries.DAMAGE_TYPE, Gensokyou.id("danmaku"));

    private ModDamageTypes() {
    }

    public static DamageSource danmaku(Entity source, Entity attacker) {
        Holder<DamageType> holder = source.level()
                .registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(DANMAKU);
        return new DamageSource(holder, source, attacker);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Gensokyou.MODID, path);
    }
}
