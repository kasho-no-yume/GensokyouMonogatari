package com.bitsson.gensokyou.entity.spell;

import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.registry.ModEntityTypes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * 花符『癒しの花園』：施放点绽放的花圃场。每 {@code interval} tick 对半径内玩家回血。
 * 不回灵、不奶敌对生物（spec early-boss-player-spellcards）。
 */
public class FlowerGardenEntity extends AbstractSpellFieldEntity {

    private static final String TAG_HEAL_PER_SECOND = "HealPerSecond";
    private static final String TAG_INTERVAL = "Interval";

    private double healPerSecond;
    private int intervalTicks = 20;

    public FlowerGardenEntity(EntityType<? extends FlowerGardenEntity> type, Level level) {
        super(type, level);
    }

    public FlowerGardenEntity(Level level, double x, double y, double z,
                              float radius, int durationTicks, double healPerSecond) {
        this(ModEntityTypes.FLOWER_GARDEN.get(), level);
        setPos(x, y, z);
        setRadius(radius);
        this.ticksLeft = durationTicks;
        this.healPerSecond = healPerSecond;
        this.intervalTicks = GensokyouConfig.HEALING_GARDEN_INTERVAL_TICKS.get();
    }

    @Override
    protected void serverTick(ServerLevel level) {
        if (intervalTicks <= 0 || tickCount % intervalTicks != 0) {
            return;
        }
        double amount = healPerSecond * intervalTicks / 20.0D;
        if (amount <= 0D) {
            return;
        }
        float r = radius();
        AABB box = new AABB(getX() - r, getY() - r, getZ() - r, getX() + r, getY() + r, getZ() + r);
        List<Player> players = level.getEntitiesOfClass(Player.class, box,
                p -> p.isAlive() && p.distanceToSqr(this) <= (double) r * r);
        for (Player player : players) {
            player.heal((float) amount);
        }
    }

    @Override
    protected void clientTick() {
        float r = radius();
        if (tickCount % 4 == 0) {
            double angle = random.nextDouble() * Math.PI * 2D;
            double rr = r * (0.6D + random.nextDouble() * 0.4D);
            level().addParticle(ParticleTypes.SPORE_BLOSSOM_AIR,
                    getX() + Math.cos(angle) * rr, getY() + 0.2D, getZ() + Math.sin(angle) * rr,
                    0D, 0.01D, 0D);
            level().addParticle(ParticleTypes.HAPPY_VILLAGER,
                    getX() + (random.nextDouble() - 0.5D) * r, getY() + 0.1D,
                    getZ() + (random.nextDouble() - 0.5D) * r, 0D, 0.02D, 0D);
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putDouble(TAG_HEAL_PER_SECOND, healPerSecond);
        tag.putInt(TAG_INTERVAL, intervalTicks);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.healPerSecond = tag.getDouble(TAG_HEAL_PER_SECOND);
        this.intervalTicks = Math.max(1, tag.getInt(TAG_INTERVAL));
    }
}
