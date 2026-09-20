package com.bitsson.gensokyou.entity.goal;

import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.entity.DanmakuWhitelists;
import com.bitsson.gensokyou.entity.FairyEntity;
import com.bitsson.gensokyou.entity.FairyVariant;
import com.bitsson.gensokyou.entity.LaserDanmaku;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * LASER 变体：在妖精脸部平面半径 0.5 格的圆环上随机取点发出激光，
 * 3D 指向施放瞬间玩家坐标；预警 1 秒 + 持续 2 秒；随机调色板颜色。
 */
public class FairyLaserGoal extends Goal {
    private static final int[] PALETTE = {
            0xFF3B30, 0xFF9500, 0xFFCC00, 0x34C759,
            0x00C7BE, 0x007AFF, 0xAF52DE, 0xFF2D55
    };

    private final FairyEntity fairy;
    private int cooldown;

    public FairyLaserGoal(FairyEntity fairy) {
        this.fairy = fairy;
        this.setFlags(EnumSet.of(Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return this.fairy.getVariant() == FairyVariant.LASER
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
        this.cooldown = GensokyouConfig.FAIRY_LASER_INTERVAL.get();
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
        this.cooldown = GensokyouConfig.FAIRY_LASER_INTERVAL.get();
        this.fire(target);
    }

    private void fire(LivingEntity target) {
        Vec3 center = this.fairy.getEyePosition();
        // 以"指向玩家"作为脸部朝向基（妖精正看向玩家，与脸平面一致且确定性强）。
        Vec3 forward = target.getEyePosition().subtract(center).normalize();
        Vec3 right = forward.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (right.lengthSqr() < 1.0E-6D) {
            right = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            right = right.normalize();
        }
        Vec3 up = right.cross(forward).normalize();

        double ring = GensokyouConfig.FAIRY_LASER_RING_RADIUS.get();
        double theta = this.fairy.getRandom().nextDouble() * Math.PI * 2.0D;
        Vec3 start = center
                .add(right.scale(Math.cos(theta) * ring))
                .add(up.scale(Math.sin(theta) * ring));

        Vec3 aim = target.getEyePosition();
        Vec3 dir = aim.subtract(start);
        if (dir.lengthSqr() < 1.0E-6D) {
            dir = forward;
        }
        dir = dir.normalize();
        int color = PALETTE[this.fairy.getRandom().nextInt(PALETTE.length)];

        LaserDanmaku laser = new LaserDanmaku(
                this.fairy.level(), start, dir,
                GensokyouConfig.FAIRY_LASER_DAMAGE.get().floatValue(),
                color,
                GensokyouConfig.FAIRY_LASER_LENGTH.get(),
                GensokyouConfig.FAIRY_LASER_RADIUS.get(),
                GensokyouConfig.FAIRY_LASER_DELAY_SECONDS.get(),
                GensokyouConfig.FAIRY_LASER_DURATION_SECONDS.get(),
                this.fairy,
                DanmakuWhitelists.FAIRY);
        this.fairy.level().addFreshEntity(laser);

        this.fairy.triggerCast();
    }
}
