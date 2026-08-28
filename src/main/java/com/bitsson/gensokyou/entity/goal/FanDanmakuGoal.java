package com.bitsson.gensokyou.entity.goal;

import com.bitsson.gensokyou.entity.DanmakuProjectile;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

public class FanDanmakuGoal extends Goal {
    private final Mob mob;
    private final int intervalTicks;
    private final int shots;
    private final double spreadDegrees;
    private final double speed;
    private final float damage;

    public FanDanmakuGoal(Mob mob, int intervalTicks, int shots, double spreadDegrees,
                          double speed, float damage) {
        this.mob = mob;
        this.intervalTicks = intervalTicks;
        this.shots = shots;
        this.spreadDegrees = spreadDegrees;
        this.speed = speed;
        this.damage = damage;
    }

    @Override
    public boolean canUse() {
        return mob.getTarget() != null && mob.tickCount % intervalTicks == 0;
    }

    @Override
    public void start() {
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) {
            return;
        }
        Vec3 origin = mob.getEyePosition();
        Vec3 aim = target.getEyePosition().subtract(origin).normalize();
        for (int i = 0; i < shots; i++) {
            double offset = Math.toRadians((i - (shots - 1) / 2.0D) * spreadDegrees);
            Vec3 dir = rotateAroundY(aim, offset);
            DanmakuProjectile projectile = new DanmakuProjectile(mob.level(), mob, damage);
            projectile.moveTo(origin.x, origin.y, origin.z, mob.getYRot(), mob.getXRot());
            projectile.shoot(dir.x, dir.y, dir.z, (float) speed, 0F);
            mob.level().addFreshEntity(projectile);
        }
    }

    public static Vec3 rotateAroundY(Vec3 vec, double radians) {
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return new Vec3(vec.x * cos + vec.z * sin, vec.y, -vec.x * sin + vec.z * cos);
    }
}
