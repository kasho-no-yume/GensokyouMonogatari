package com.bitsson.gensokyou.entity;

import com.bitsson.gensokyou.danmaku.track.SignaturePalette;
import com.bitsson.gensokyou.danmaku.track.SpellCard;
import com.bitsson.gensokyou.danmaku.track.TrackRunner;
import com.bitsson.gensokyou.network.ModNetworking;
import com.bitsson.gensokyou.netprof.NetProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/** Explicit legacy island for excluded placeholder content; never a fallback for program BOSSes. */
public abstract class UnmigratedTouhouBoss extends AbstractTouhouBoss {
    private TrackRunner runner;
    private int announced = -1;

    protected UnmigratedTouhouBoss(EntityType<? extends UnmigratedTouhouBoss> type, Level level) {
        super(type, level);
    }

    protected abstract List<SpellCard> spellCards();
    protected abstract SignaturePalette palette();

    @Override protected final void tickUnmigratedContent() {
        if (runner == null) runner = new TrackRunner(spellCards(), palette());
        double fraction = getMaxHealth() <= 0 ? 0 : getHealth() / getMaxHealth();
        if (runner.selectCard(fraction) && !lockedTargets().isEmpty()) faceTowards(lockedTargets().getFirst());
        int index = runner.currentIndex();
        if (index >= 0 && index != announced) {
            announced = index;
            ModNetworking.broadcastSpellCardName(this, index);
            NetProfile.onCardChanged(this, index);
        }
        runner.tick(this, lockedTargets(), danmakuDamage());
    }

    @Override public OptionalInt activeCardIndex() {
        return runner == null || runner.currentIndex() < 0 ? OptionalInt.empty() : OptionalInt.of(runner.currentIndex());
    }

    @Override public Optional<Component> spellCardName(int index) {
        var cards = spellCards();
        return index >= 0 && index < cards.size() ? Optional.of(cards.get(index).name()) : Optional.empty();
    }
}
