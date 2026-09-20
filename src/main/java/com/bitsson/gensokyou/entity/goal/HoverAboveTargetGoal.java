package com.bitsson.gensokyou.entity.goal;

import com.bitsson.gensokyou.config.GensokyouConfig;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * 妖精悬停 AI：目标点 = 玩家 + 水平随机偏移（1~3 格，带滞回）+ 头顶 3 格；
 * 受天花板封顶；天花板低于当前高度时不下降（玩家钻洞不追）。
 */
public class HoverAboveTargetGoal extends Goal {
    private static final double HYSTERESIS = 0.5D;

    private final Mob mob;
    private double offsetX;
    private double offsetZ;
    private boolean hasOffset;

    public HoverAboveTargetGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.mob.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse();
    }

    @Override
    public void stop() {
        this.hasOffset = false;
    }

    @Override
    public void tick() {
        LivingEntity target = this.mob.getTarget();
        if (target == null) {
            return;
        }

        double min = GensokyouConfig.FAIRY_HOVER_MIN_DIST.get();
        double max = Math.max(min, GensokyouConfig.FAIRY_HOVER_MAX_DIST.get());

        double dx = this.mob.getX() - target.getX();
        double dz = this.mob.getZ() - target.getZ();
        double current = Math.sqrt(dx * dx + dz * dz);

        if (!this.hasOffset || current < min - HYSTERESIS || current > max + HYSTERESIS) {
            double angle = this.mob.getRandom().nextDouble() * Math.PI * 2.0D;
            double dist = min + this.mob.getRandom().nextDouble() * (max - min);
            this.offsetX = Math.cos(angle) * dist;
            this.offsetZ = Math.sin(angle) * dist;
            this.hasOffset = true;
        }

        double targetX = target.getX() + this.offsetX;
        double targetZ = target.getZ() + this.offsetZ;
        double desiredY = target.getY() + GensokyouConfig.FAIRY_HOVER_HEIGHT.get();

        double hoverY = Math.min(desiredY, this.findCeilingY(targetX, targetZ, desiredY));
        if (hoverY < this.mob.getY()) {
            hoverY = this.mob.getY();
        }

        this.mob.getMoveControl().setWantedPosition(targetX, hoverY, targetZ,
                GensokyouConfig.FAIRY_FLY_SPEED.get());
    }

    /** 从妖精头顶向 desiredY 上方做竖直射线，返回首个实心方块下的可站高度。 */
    private double findCeilingY(double x, double z, double desiredY) {
        Vec3 from = new Vec3(x, this.mob.getY() + this.mob.getBbHeight(), z);
        Vec3 to = new Vec3(x, desiredY + 2.0D, z);
        if (to.y <= from.y) {
            return desiredY;
        }
        BlockHitResult hit = this.mob.level().clip(new ClipContext(
                from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this.mob));
        if (hit.getType() == HitResult.Type.BLOCK) {
            return hit.getLocation().y - this.mob.getBbHeight();
        }
        return desiredY;
    }
}
