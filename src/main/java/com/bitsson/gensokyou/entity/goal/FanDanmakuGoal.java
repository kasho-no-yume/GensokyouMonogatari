package com.bitsson.gensokyou.entity.goal;

import com.bitsson.gensokyou.entity.SphereDanmaku;
import com.bitsson.gensokyou.registry.ModEntityTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

public class FanDanmakuGoal extends Goal {
    private static final Set<EntityType<?>> FAIRY_WHITELIST = Set.of(
            ModEntityTypes.FAIRY.get(),
            ModEntityTypes.BIG_FAIRY.get()
    );
    
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
            
            // Use new SphereDanmaku with whitelist and random color
            SphereDanmaku projectile = new SphereDanmaku(
                    mob.level(), 
                    mob, 
                    damage, 
                    0,  // color=0 means random color
                    0.4F,  // size
                    FAIRY_WHITELIST
            );
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
