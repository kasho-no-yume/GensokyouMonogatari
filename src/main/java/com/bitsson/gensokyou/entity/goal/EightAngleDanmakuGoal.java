package com.bitsson.gensokyou.entity.goal;

import com.bitsson.gensokyou.entity.DanmakuProjectile;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

public class EightAngleDanmakuGoal extends Goal {
    private static final int RING_COUNT = 8;

    private final Mob mob;
    private final int intervalTicks;
    private final double speed;
    private final float damage;

    public EightAngleDanmakuGoal(Mob mob, int intervalTicks, double speed, float damage) {
        this.mob = mob;
        this.intervalTicks = intervalTicks;
        this.speed = speed;
        this.damage = damage;
    }

    @Override
    public boolean canUse() {
        return mob.getTarget() != null && mob.tickCount % intervalTicks == 0;
    }

    @Override
    public void start() {
        Vec3 origin = mob.getEyePosition();
        for (int i = 0; i < RING_COUNT; i++) {
            double angle = Math.PI / 4D * i;
            Vec3 dir = new Vec3(Math.cos(angle), 0.1D, Math.sin(angle)).normalize();
            DanmakuProjectile projectile = new DanmakuProjectile(mob.level(), mob, damage);
            projectile.moveTo(origin.x, origin.y, origin.z, mob.getYRot(), 0F);
            projectile.shoot(dir.x, dir.y, dir.z, (float) speed, 0F);
            mob.level().addFreshEntity(projectile);
        }
    }
}
