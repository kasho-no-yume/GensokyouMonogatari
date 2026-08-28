package com.bitsson.gensokyou.item.spellcard;

import com.bitsson.gensokyou.spirit.SpellCardEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class IcicleFallCardItem extends SpellCardItem {

    public IcicleFallCardItem(Properties properties) {
        super(properties);
    }

    @Override
    protected void performEffect(Level level, Player player) {
        SpellCardEffects.perform(SpellCardEffects.ICICLE_FALL, level, player);
    }
}
