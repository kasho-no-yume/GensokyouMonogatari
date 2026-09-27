package com.bitsson.gensokyou.registry;

import com.bitsson.gensokyou.Gensokyou;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 模组自定义属性。
 *
 * <p><b>为什么弹幕伤害要单列一个属性</b>，而不是复用 {@code Attributes.ATTACK_DAMAGE}：
 * <ul>
 *   <li>语义不同——{@code ATTACK_DAMAGE} 是近战伤害，弹幕是 {@code gensokyou:danmaku}
 *       伤害类型、且要吃玩家的护壁指数与擦弹。两者混在一处，调弹幕时会被近战干扰，
 *       反之亦然。
 *   <li>可调性——独立的属性让 {@code /attribute} 能直接改，是<b>调试弹幕平衡的主旋钮</b>：
 *       改一次，一只 BOSS 全部符卡的伤害同步缩放，不用碰配置、不用重开、不用改代码。
 *   <li>可扩展——将来给符卡加「伤害 +N%」类词条时，有明确的挂载点。
 * </ul>
 */
public final class ModAttributes {

    public static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(Registries.ATTRIBUTE, Gensokyou.MODID);

    /**
     * 弹幕单发伤害。基准值由「参照玩家 EHP ÷ 参照挨弹数」播种。
     *
     * <p>实际造成的血量损耗还要再经过玩家的<b>擦弹</b>与<b>护壁指数 2^-P</b>，
     * 故本属性的值是<b>结算前的原始弹幕伤害</b>。
     */
    public static final DeferredHolder<Attribute, Attribute> DANMAKU_DAMAGE =
            ATTRIBUTES.register("danmaku_damage",
                    () -> new RangedAttribute("attribute.name.gensokyou.danmaku_damage", 6.0D, 0.0D, 1024.0D)
                            .setSyncable(true));

    private ModAttributes() {
    }
}
