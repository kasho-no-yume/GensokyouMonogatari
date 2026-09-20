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

/** SINGLE 变体：周期性向玩家发射一枚弹幕。 */
public class FairySingleShotGoal extends Goal {
    private final FairyEntity fairy;
    private int cooldown;

    public FairySingleShotGoal(FairyEntity fairy) {
        this.fairy = fairy;
        this.setFlags(EnumSet.of(Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return this.fairy.getVariant() == FairyVariant.SINGLE
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
        this.cooldown = GensokyouConfig.FAIRY_SINGLE_INTERVAL.get();
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
        this.cooldown = GensokyouConfig.FAIRY_SINGLE_INTERVAL.get();
        this.fire(target);
    }

    private void fire(LivingEntity target) {
        Vec3 origin = this.fairy.getEyePosition();
        Vec3 dir = target.getEyePosition().subtract(origin).normalize();
        float damage = GensokyouConfig.FAIRY_SINGLE_DAMAGE.get().floatValue();

        SphereDanmaku bullet = new SphereDanmaku(
                this.fairy.level(), this.fairy, damage, 0, 0.4F, DanmakuWhitelists.FAIRY);
        bullet.moveTo(origin.x, origin.y, origin.z, this.fairy.getYRot(), this.fairy.getXRot());
        bullet.shoot(dir.x, dir.y, dir.z, this.fairy.bulletSpeed(), 0.0F);
        this.fairy.level().addFreshEntity(bullet);

        this.fairy.triggerCast();
    }
}
