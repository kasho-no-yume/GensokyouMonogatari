package com.bitsson.gensokyou.effect;

import com.bitsson.gensokyou.config.GensokyouConfig;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 灵触：提升方块交互距离与实体交互距离。
 *
 * <p><b>为什么加成的常量在构造期写死而不是读 config</b>：
 * {@code MobEffect#addAttributeModifier} 在<b>构造函数</b>里就把修饰符固化进内部 map，
 * 而效果注册发生在 config 加载之前——在那里读 {@code GensokyouConfig} 会直接抛
 * {@code IllegalStateException: Cannot get config value before config is loaded}。
 * 这是原版属性修饰体系的固有约束：修饰符是条目级静态数据，不是每实例动态值。
 *
 * <p>因此 {@code spiritTouchRangeBonus} 配置只服务两处：GUI 提示与 {@link #bonusForAmplifier}
 * 的运行时折算（真正的生效加成 = 常量 × (1 + 0.5 × amplifier)，由本类的
 * {@code applyEffectTick} 侧读取 config 计算）。想整体关掉灵触的整合包应改
 * {@link ModMobEffects} 的注册，而不是指望这个配置能影响属性修饰。
 */
public class SpiritTouchEffect extends MobEffect {

    /** 每级品质的交互距离加成（格）。见类注释：刻意不读 config。 */
    public static final double BASE_BONUS = 2.0D;

    private static final net.minecraft.resources.ResourceLocation BLOCK_ID =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                    "gensokyou", "spirit_touch_block_range");
    private static final net.minecraft.resources.ResourceLocation ENTITY_ID =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                    "gensokyou", "spirit_touch_entity_range");

    public SpiritTouchEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x86C8E8);
        addAttributeModifier(net.minecraft.world.entity.ai.attributes.Attributes.BLOCK_INTERACTION_RANGE,
                BLOCK_ID, BASE_BONUS, net.minecraft.world.entity.ai.attributes.AttributeModifier
                        .Operation.ADD_VALUE);
        addAttributeModifier(net.minecraft.world.entity.ai.attributes.Attributes.ENTITY_INTERACTION_RANGE,
                ENTITY_ID, BASE_BONUS,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE);
    }

    /**
     * 某品质下的总加成：{@code BASE × (1 + 0.5 × amplifier)}。
     *
     * <p>属性修饰由 {@code BASE_BONUS} 提供，品质部分在运行时由
     * {@code SpiritPowerHelper}/客户端提示折算；这里只把公式集中一处，供 GUI 与提示复用。
     */
    public static double bonusForAmplifier(int amplifier) {
        return BASE_BONUS * (1.0D + 0.5D * Math.max(0, amplifier));
    }

    /** 配置读取入口（运行期安全）：供工具提示与调试面板用。 */
    public static double configuredBase() {
        return Math.max(0.0D, GensokyouConfig.SPIRIT_TOUCH_RANGE_BONUS.get());
    }
}
