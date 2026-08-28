package com.bitsson.gensokyou.entity.goal;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

public class FlashToNearestPlayerGoal extends Goal {
    private static final int ATTEMPTS = 8;
    private static final double MIN_DISTANCE = 4D;
    private static final double MAX_DISTANCE = 9D;

    private final Mob mob;
    private final int intervalTicks;

    public FlashToNearestPlayerGoal(Mob mob, int intervalTicks) {
        this.mob = mob;
        this.intervalTicks = intervalTicks;
    }

    @Override
    public boolean canUse() {
        return mob.getTarget() instanceof Player && mob.tickCount % intervalTicks == 0;
    }

    @Override
    public void start() {
        if (!(mob.level() instanceof ServerLevel level)) {
            return;
        }
        LivingEntity target = mob.getTarget();
        for (int i = 0; i < ATTEMPTS; i++) {
            double angle = mob.getRandom().nextDouble() * Math.PI * 2D;
            double dist = MIN_DISTANCE + mob.getRandom().nextDouble() * (MAX_DISTANCE - MIN_DISTANCE);
            int x = (int) Math.round(target.getX() + Math.cos(angle) * dist);
            int z = (int) Math.round(target.getZ() + Math.sin(angle) * dist);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            AABB box = mob.getBoundingBox().move(x + 0.5D - mob.getX(), y - mob.getY(), z + 0.5D - mob.getZ());
            if (!level.noCollision(mob, box)) {
                continue;
            }
            spawnFlash(level, mob.position());
            mob.teleportTo(x + 0.5D, y, z + 0.5D);
            spawnFlash(level, mob.position());
            mob.getNavigation().stop();
            return;
        }
    }

    private void spawnFlash(ServerLevel level, net.minecraft.world.phys.Vec3 pos) {
        level.sendParticles(ParticleTypes.POOF, pos.x, pos.y + 1D, pos.z, 8, 0.3D, 0.6D, 0.3D, 0.01D);
    }
}
