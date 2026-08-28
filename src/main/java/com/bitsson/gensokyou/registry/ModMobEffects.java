package com.bitsson.gensokyou.registry;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.effect.DanmakuProtectEffect;
import com.bitsson.gensokyou.effect.MuPowerEffect;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMobEffects {
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, Gensokyou.MODID);

    public static final DeferredHolder<MobEffect, DanmakuProtectEffect> DANMAKU_PROTECT =
            EFFECTS.register("danmaku_protect", DanmakuProtectEffect::new);

    public static final DeferredHolder<MobEffect, MuPowerEffect> MU_POWER =
            EFFECTS.register("mu_power", MuPowerEffect::new);
}
