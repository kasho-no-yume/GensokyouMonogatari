package com.bitsson.gensokyou.item.spellcard;

import com.bitsson.gensokyou.spirit.SpellCardEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class MusouFuuinCardItem extends SpellCardItem {

    public MusouFuuinCardItem(Properties properties) {
        super(properties, SpellCardEffects.MUSOU_FUUIN);
    }

    @Override
    protected void performEffect(Level level, Player player) {
        SpellCardEffects.perform(SpellCardEffects.MUSOU_FUUIN, level, player);
    }
}
