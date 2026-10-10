package com.bitsson.gensokyou.item.spellcard;

import com.bitsson.gensokyou.spirit.SpellCardEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class IcicleFallCardItem extends SpellCardItem {

    public IcicleFallCardItem(Properties properties) {
        super(properties, SpellCardEffects.ICICLE_FALL);
    }

    @Override
    protected void performEffect(Level level, Player player, int quality) {
        SpellCardEffects.performItem(SpellCardEffects.ICICLE_FALL, quality, level, player);
    }
}
