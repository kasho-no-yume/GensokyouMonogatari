package com.bitsson.gensokyou.entity.goal;

import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.entity.DanmakuWhitelists;
import com.bitsson.gensokyou.entity.FairyEntity;
import com.bitsson.gensokyou.entity.FairyVariant;
import com.bitsson.gensokyou.entity.SphereDanmaku;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * NET 变体：周期性发射 3×3 角度网格（相邻夹角 10°，中心精确瞄玩家）。
 * 9 发同源发散，在空间上呈一张曲平面。
 */
public class FairyNetGoal extends Goal {
    private final FairyEntity fairy;
    private int cooldown;

    public FairyNetGoal(FairyEntity fairy) {
        this.fairy = fairy;
        this.setFlags(EnumSet.of(Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return this.fairy.getVariant() == FairyVariant.NET
                && this.fairy.getTarget() != null
                && this.fairy.getTarget().isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        this.cooldown = GensokyouConfig.FAIRY_NET_INTERVAL.get();
    }

    @Override
    public void tick() {
        LivingEntity target = this.fairy.getTarget();
        if (target == null) {
            return;
        }
        this.fairy.getLookControl().setLookAt(target, 30.0F, 30.0F);
        this.fairy.faceTowards(target.getX(), target.getZ());
        if (--this.cooldown > 0) {
            return;
        }
        this.cooldown = GensokyouConfig.FAIRY_NET_INTERVAL.get();
        this.fire(target);
    }

    private void fire(LivingEntity target) {
        Vec3 origin = this.fairy.getEyePosition();
        Vec3 forward = target.getEyePosition().subtract(origin).normalize();
        Vec3 right = forward.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (right.lengthSqr() < 1.0E-6D) {
            right = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            right = right.normalize();
        }
        Vec3 up = right.cross(forward).normalize();

        double angle = Math.toRadians(GensokyouConfig.FAIRY_NET_ANGLE_DEG.get());
        float damage = GensokyouConfig.FAIRY_NET_DAMAGE.get().floatValue();
        float speed = this.fairy.bulletSpeed();

        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                Vec3 dir = rotate(forward, right, up, i * angle, j * angle);
                SphereDanmaku bullet = new SphereDanmaku(
                        this.fairy.level(), this.fairy, damage, 0, 0.4F, DanmakuWhitelists.FAIRY);
                bullet.moveTo(origin.x, origin.y, origin.z, this.fairy.getYRot(), this.fairy.getXRot());
                bullet.shoot(dir.x, dir.y, dir.z, speed, 0.0F);
                this.fairy.level().addFreshEntity(bullet);
            }
        }

        this.fairy.triggerCast();
    }

    /** 先绕 up 水平偏转 h，再绕 right 竖直偏转 v。 */
    private static Vec3 rotate(Vec3 forward, Vec3 right, Vec3 up, double h, double v) {
        Vec3 horizontal = forward.scale(Math.cos(h)).add(right.scale(Math.sin(h))).normalize();
        return horizontal.scale(Math.cos(v)).add(up.scale(Math.sin(v))).normalize();
    }
}
