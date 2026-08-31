package com.bitsson.gensokyou.client;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.item.spellcard.SpellCardItem;
import com.bitsson.gensokyou.item.weapon.AmpCoreItem;
import com.bitsson.gensokyou.registry.ModDataComponents;
import com.bitsson.gensokyou.registry.ModItems;
import com.bitsson.gensokyou.registry.TierPalette;
import com.bitsson.gensokyou.spirit.SpellCardEffects;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/**
 * 程序化滤镜染色：灰度底图 + 运行期 tint。
 *
 * <p>tintIndex 约定（物品模型的层号即 tintIndex）：
 * <ul>
 *   <li>增幅核 —— layer0 灰度托座（品阶色）、layer1 灰度晶石（实例随机色，
 *       首次获取时掷定存入 crystal_color 组件，无组件时回退品阶色）</li>
 *   <li>符卡 —— layer0 白纸卡框（不染，-1）、layer1 灰度纹章（卡主题色）</li>
 * </ul>
 * 返回 -1 表示不染色（原样输出）。assets 只保留未染色底图，染色结果不落盘。
 * 注意：所有返回色必须带 0xFF alpha——1.21.1 ItemRenderer 会提取 tint alpha 乘入顶点色。
 */
@EventBusSubscriber(modid = Gensokyou.MODID, value = Dist.CLIENT)
public final class GensokyouItemColors {

    private static final int NO_TINT = -1;

    @SubscribeEvent
    public static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> {
            if (stack.getItem() instanceof AmpCoreItem ampCore) {
                if (tintIndex == 0) {
                    return TierPalette.rgb(ampCore.tier());
                }
                if (tintIndex == 1) {
                    Integer crystal = stack.get(ModDataComponents.CRYSTAL_COLOR.get());
                    return crystal != null ? crystal : TierPalette.rgb(ampCore.tier());
                }
                return NO_TINT;
            }
            if (stack.getItem() instanceof SpellCardItem card) {
                if (tintIndex != 1) {
                    return NO_TINT;
                }
                SpellCardEffects.Entry entry = SpellCardEffects.get(card.cardId());
                return entry != null ? entry.themeColor() : NO_TINT;
            }
            return NO_TINT;
        }, ModItems.AMP_CORE_T1.get(), ModItems.AMP_CORE_T2.get(), ModItems.AMP_CORE_T3.get(),
                ModItems.MUSOU_FUUIN.get(), ModItems.LIGHT_REFLECT.get(), ModItems.ICICLE_FALL.get());
    }

    private GensokyouItemColors() {
    }
}
