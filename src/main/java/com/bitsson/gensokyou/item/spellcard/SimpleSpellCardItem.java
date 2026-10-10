package com.bitsson.gensokyou.item.spellcard;

import com.bitsson.gensokyou.spirit.SpellCardEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * 通用道具符卡：只把 cardId 转发给 {@link SpellCardEffects#performItem}。
 * 新卡（add-player-spellcards）无需各自建类。
 */
public class SimpleSpellCardItem extends SpellCardItem {

    public SimpleSpellCardItem(Properties properties, String cardId) {
        super(properties, cardId);
    }

    @Override
    protected void performEffect(Level level, Player player, int quality) {
        SpellCardEffects.performItem(cardId(), quality, level, player);
    }
}
