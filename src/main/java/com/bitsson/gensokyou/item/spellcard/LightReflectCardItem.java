package com.bitsson.gensokyou.item.spellcard;

import com.bitsson.gensokyou.spirit.SpellCardEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class LightReflectCardItem extends SpellCardItem {

    public LightReflectCardItem(Properties properties) {
        super(properties, SpellCardEffects.LIGHT_REFLECT);
    }

    @Override
    protected void performEffect(Level level, Player player, int quality) {
        SpellCardEffects.performItem(SpellCardEffects.LIGHT_REFLECT, quality, level, player);
    }
}
