package com.bitsson.gensokyou.registry;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.effect.DanmakuProtectEffect;
import com.bitsson.gensokyou.effect.HiganbanaPoisonEffect;
import com.bitsson.gensokyou.effect.MuPowerEffect;
import com.bitsson.gensokyou.effect.ReikiRecoveryEffect;
import com.bitsson.gensokyou.effect.SpiritTouchEffect;
import com.bitsson.gensokyou.effect.SpiritualSightEffect;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModMobEffects {
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, Gensokyou.MODID);

    public static final DeferredHolder<MobEffect, DanmakuProtectEffect> DANMAKU_PROTECT =
            EFFECTS.register("danmaku_protect", DanmakuProtectEffect::new);

    public static final DeferredHolder<MobEffect, MuPowerEffect> MU_POWER =
            EFFECTS.register("mu_power", MuPowerEffect::new);

    // ---- mod 专属药水线（add-gensokyou-material-uses）----

    /** 回灵汤：持续期间额外回复灵力。 */
    public static final DeferredHolder<MobEffect, ReikiRecoveryEffect> REIKI_RECOVERY =
            EFFECTS.register("reiki_recovery", ReikiRecoveryEffect::new);

    /** 灵视：范围内实体以绿色发光轮廓穿墙显形。 */
    public static final DeferredHolder<MobEffect, SpiritualSightEffect> SPIRITUAL_SIGHT =
            EFFECTS.register("spiritual_sight", SpiritualSightEffect::new);

    /** 灵触：提升方块与实体的交互距离。 */
    public static final DeferredHolder<MobEffect, SpiritTouchEffect> SPIRIT_TOUCH =
            EFFECTS.register("spirit_touch", SpiritTouchEffect::new);

    /** 彼岸花毒：80% 免伤契约，到期即死。 */
    public static final DeferredHolder<MobEffect, HiganbanaPoisonEffect> HIGANBANA_POISON =
            EFFECTS.register("higanbana_poison", HiganbanaPoisonEffect::new);
}
