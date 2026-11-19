package com.bitsson.gensokyou.entity.goal;

import com.bitsson.gensokyou.balance.BalanceNumbers;
import com.bitsson.gensokyou.entity.FairyEntity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * 妖精悬停 AI：目标点 = 玩家 + 水平随机偏移（1~3 格，带滞回）+ 头顶约 3 格
 * （每只妖精带固定小幅高度偏移）；受天花板限高、受地板防穿模约束，可跟随玩家升降。
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

        double min = BalanceNumbers.FAIRY_HOVER_MIN_DIST;
        double max = Math.max(min, BalanceNumbers.FAIRY_HOVER_MAX_DIST);

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
        double hoverOffset = this.mob instanceof FairyEntity fairy ? fairy.getHoverOffsetY() : 0.0D;
        double desiredY = target.getY() + BalanceNumbers.FAIRY_HOVER_HEIGHT + hoverOffset;

        // 天花板限高、地板防穿模；极端低矮空间两者冲突时优先抬到地板之上。
        double hoverY = Math.min(desiredY, this.findCeilingY(targetX, targetZ, desiredY));
        hoverY = Math.max(hoverY, this.findFloorY(targetX, targetZ, desiredY));

        this.mob.getMoveControl().setWantedPosition(targetX, hoverY, targetZ,
                BalanceNumbers.FAIRY_FLY_SPEED);
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

    /** 从 desiredY 上方向下射线，返回首个实心面顶之上的安全悬停高度；无地面返回 -∞。 */
    private double findFloorY(double x, double z, double desiredY) {
        Vec3 from = new Vec3(x, desiredY + 1.0D, z);
        Vec3 to = new Vec3(x, this.mob.level().getMinBuildHeight(), z);
        BlockHitResult hit = this.mob.level().clip(new ClipContext(
                from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this.mob));
        if (hit.getType() == HitResult.Type.BLOCK) {
            return hit.getLocation().y + this.mob.getBbHeight() + 0.1D;
        }
        return Double.NEGATIVE_INFINITY;
    }
}
