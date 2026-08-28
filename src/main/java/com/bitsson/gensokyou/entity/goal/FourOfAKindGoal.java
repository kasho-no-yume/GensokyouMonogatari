package com.bitsson.gensokyou.entity.goal;

import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.entity.FlandreEntity;
import com.bitsson.gensokyou.entity.FakeFlandreEntity;
import com.bitsson.gensokyou.registry.ModEntityTypes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.levelgen.Heightmap;

public class FourOfAKindGoal extends Goal {
    private static final double SPAWN_RADIUS = 5D;
    private static final double COUNT_RANGE = 48D;

    private final FlandreEntity boss;

    public FourOfAKindGoal(FlandreEntity boss) {
        this.boss = boss;
    }

    @Override
    public boolean canUse() {
        return !boss.isFake()
                && boss.getTarget() != null
                && boss.tickCount % GensokyouConfig.FOUR_OF_A_KIND_INTERVAL.get() == 0;
    }

    @Override
    public void start() {
        if (!(boss.level() instanceof ServerLevel level)) {
            return;
        }
        int alive = level.getEntitiesOfClass(FakeFlandreEntity.class,
                boss.getBoundingBox().inflate(COUNT_RANGE)).size();
        int toSpawn = Math.min(GensokyouConfig.FAKE_FLANDRE_COUNT.get(),
                GensokyouConfig.FAKE_FLANDRE_CAP.get() - alive);
        LivingEntity target = boss.getTarget();
        for (int i = 0; i < toSpawn; i++) {
            double angle = boss.getRandom().nextDouble() * Math.PI * 2D;
            double dist = 2D + boss.getRandom().nextDouble() * (SPAWN_RADIUS - 2D);
            int x = (int) Math.round(boss.getX() + Math.cos(angle) * dist);
            int z = (int) Math.round(boss.getZ() + Math.sin(angle) * dist);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            FakeFlandreEntity fake = ModEntityTypes.FAKE_FLANDRE.get().create(level);
            if (fake == null) {
                continue;
            }
            fake.moveTo(x + 0.5D, y, z + 0.5D, boss.getYRot(), 0F);
            fake.setTarget(target);
            level.addFreshEntity(fake);
            level.sendParticles(ParticleTypes.POOF, fake.getX(), fake.getY() + 1D, fake.getZ(),
                    10, 0.3D, 0.6D, 0.3D, 0.01D);
        }
    }
}
