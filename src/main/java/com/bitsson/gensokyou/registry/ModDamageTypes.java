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
    /** 神恩演出的脚本伤害类型：不入弹幕减免管线、不触发灵汲（均为按类型排除的天然语义）。 */
    public static final ResourceKey<DamageType> GRACE_PERFORM =
            ResourceKey.create(Registries.DAMAGE_TYPE, Gensokyou.id("grace_perform"));

    private ModDamageTypes() {
    }

    public static DamageSource danmaku(Entity source, Entity attacker) {
        Holder<DamageType> holder = source.level()
                .registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(DANMAKU);
        return new DamageSource(holder, source, attacker);
    }

    /** 演出脚本伤害源（无直接实体/攻击者——减免管线与汲取均不认领此类型）。 */
    public static DamageSource gracePerform(Entity victim) {
        Holder<DamageType> holder = victim.level()
                .registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(GRACE_PERFORM);
        return new DamageSource(holder);
    }

    public static boolean isGracePerform(DamageSource source) {
        return source.is(GRACE_PERFORM);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Gensokyou.MODID, path);
    }
}
